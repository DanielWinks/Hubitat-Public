package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.ProjectIndex
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.SourceKind
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.TupleExpression

/**
 * Driver-specific structural checks beyond MetadataRule:
 *  - capability '<Name>' values exist in the official capability list
 *  - command '<name>' has a matching public method declared in the file (or library)
 *  - attribute '<name>', '<type>' types are one of the accepted Hubitat types
 *  - input declarations have a recognized type
 */
@CompileStatic
class DriverDefinitionRule implements Rule {

  /** Officially documented attribute types per Hubitat developer docs. */
  static final Set<String> CANONICAL_ATTRIBUTE_TYPES = ([
    'STRING', 'NUMBER', 'ENUM', 'JSON_OBJECT', 'BOOL', 'BOOLEAN'
  ] as Set)

  /** Other attribute types observed in production Hubitat drivers. */
  static final Set<String> ACCEPTED_ATTRIBUTE_TYPES = ([
    'DATE', 'DATETIME', 'DYNAMIC_ENUM', 'VECTOR3'
  ] as Set)

  static final Set<String> ATTRIBUTE_TYPES = (
    CANONICAL_ATTRIBUTE_TYPES.collectMany { String t -> [t, t.toLowerCase()] } +
    ACCEPTED_ATTRIBUTE_TYPES.collectMany { String t -> [t, t.toLowerCase()] }
  ) as Set<String>

  static final Set<String> INPUT_TYPES = ([
    'bool', 'boolean', 'decimal', 'email', 'enum', 'hub', 'icon',
    'number', 'password', 'phone', 'time', 'text', 'string', 'textarea',
    'date', 'color'
  ] as Set)

  String getName() { 'DriverDefinition' }

  private final Set<String> capabilities

  DriverDefinitionRule(Collection<String> caps) {
    this.capabilities = caps as Set<String>
  }

  static DriverDefinitionRule fromResource(String path = '/hubitat-capabilities.txt') {
    InputStream is = DriverDefinitionRule.classLoader.getResourceAsStream(path.replaceFirst('^/', ''))
    Set<String> caps = []
    if (is != null) {
      is.text.split('\n').each { String l ->
        String s = l.trim()
        if (!s.empty && !s.startsWith('#')) caps << s
      }
    }
    new DriverDefinitionRule(caps)
  }

  List<Violation> check(ParseResult pr) {
    check(pr, new ProjectIndex())
  }

  @Override
  List<Violation> check(ParseResult pr, ProjectIndex index) {
    if (pr.module == null) return []
    if (pr.kind != SourceKind.DRIVER) return []

    List<Violation> out = []
    MethodCallExpression metadata = AstUtil.findTopLevelCall(pr.module, 'metadata')
    if (metadata == null) return out
    ClosureExpression metaBody = AstUtil.trailingClosure(metadata)
    if (metaBody == null) return out

    Set<String> definedMethods = AstUtil.userMethods(pr.module).collect { MethodNode m -> m.name } as Set<String>
    definedMethods.addAll(index.resolveMethods(pr.preprocessed.includes))
    boolean hasUnresolvedIncludes = pr.preprocessed.includes.any { String fqn -> !index.knowsLibrary(fqn) }
    boolean hasIncludes = hasUnresolvedIncludes

    // Walk metadata body. The first nested call is `definition(...) { ... }`.
    AstUtil.callsInClosure(metaBody).each { MethodCallExpression mce ->
      String n = AstUtil.methodName(mce)
      if (n == 'definition') {
        ClosureExpression defBody = AstUtil.trailingClosure(mce)
        if (defBody != null) {
          checkDefinitionBody(pr, defBody, definedMethods, hasIncludes, out)
        }
      } else if (n == 'preferences') {
        ClosureExpression prefsBody = AstUtil.trailingClosure(mce)
        if (prefsBody != null) {
          checkPreferences(pr, prefsBody, out)
        }
      }
    }
    out
  }

  private void checkDefinitionBody(ParseResult pr, ClosureExpression body,
                                   Set<String> definedMethods, boolean hasIncludes,
                                   List<Violation> out) {
    Severity methodSev = hasIncludes ? Severity.WARNING : Severity.ERROR

    AstUtil.callsInClosure(body).each { MethodCallExpression call ->
      String n = AstUtil.methodName(call)
      List<Expression> args = AstUtil.argList(call)
      switch (n) {
        case 'capability':
          if (args) {
            String cap = AstUtil.constantString(args[0])
            if (cap != null && !capabilities.contains(cap)) {
              out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, Severity.ERROR,
                "Unknown capability '${cap}'. If this is a new Hubitat capability, add it to hubitat-capabilities.txt.")
            }
          }
          break
        case 'command':
          if (args) {
            String cmdName = AstUtil.constantString(args[0])
            if (cmdName != null && !definedMethods.contains(cmdName)) {
              out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, methodSev,
                "command '${cmdName}' has no matching method declared in this file.")
            }
          }
          break
        case 'attribute':
          if (args.size() >= 2) {
            String type = AstUtil.constantString(args[1])
            if (type != null && !ATTRIBUTE_TYPES.contains(type)) {
              out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, Severity.ERROR,
                "attribute type '${type}' is not a recognized Hubitat type (expected one of: ${ATTRIBUTE_TYPES.findAll { it.toUpperCase() == it }.join(', ')}).")
            }
          }
          break
      }
    }
  }

  private void checkPreferences(ParseResult pr, ClosureExpression body, List<Violation> out) {
    // Walk recursively to find `input` calls. Section is also a method call.
    walkPreferences(pr, body, out)
  }

  private void walkPreferences(ParseResult pr, ClosureExpression body, List<Violation> out) {
    AstUtil.callsInClosure(body).each { MethodCallExpression call ->
      String n = AstUtil.methodName(call)
      if (n == 'section' || n == 'page') {
        ClosureExpression inner = AstUtil.trailingClosure(call)
        if (inner != null) walkPreferences(pr, inner, out)
      } else if (n == 'input') {
        checkInput(pr.filePath, call, out)
      }
    }
  }

  private void checkInput(String file, MethodCallExpression call, List<Violation> out) {
    // input can be called as: input(name, type, ...) or input(name: 'x', type: 'bool', ...)
    Map<String, Expression> named = AstUtil.namedArgs(call)
    String type
    if (named.containsKey('type')) {
      type = AstUtil.constantString(named['type'])
    } else {
      // Filter out the named-arg map (Groovy hoists it into the arg list).
      List<Expression> positional = AstUtil.argList(call).findAll { Expression e ->
        !(e instanceof NamedArgumentListExpression) && !(e instanceof MapExpression)
      }
      // input 'name', 'type', ... -> type is positional[1]
      if (positional.size() >= 2) type = AstUtil.constantString(positional[1])
    }
    if (type != null && !INPUT_TYPES.contains(type) && !type.startsWith('capability.')) {
      out << new Violation(file, call.lineNumber, call.columnNumber, name, Severity.WARNING,
        "input type '${type}' is unusual; expected one of ${INPUT_TYPES} or 'capability.*'.")
    }
  }
}
