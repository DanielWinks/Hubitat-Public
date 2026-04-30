package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.ProjectIndex
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.SourceKind
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression

/**
 * For each {@code capability 'X'} declared in a Driver's metadata, verifies
 * that the methods Hubitat expects that capability to expose are defined -
 * either locally in the driver or in an #include'd library.
 *
 *   capability 'Switch'    -> requires on(), off()
 *   capability 'Refresh'   -> requires refresh()
 *   capability 'Lock'      -> requires lock(), unlock()
 *
 * The catalog lives in {@code resources/hubitat-capability-methods.txt}.
 * Capabilities that are pure attribute markers (sensors) have an empty
 * required-method list and pass trivially.
 */
@CompileStatic
class CapabilityRequiredMethodsRule implements Rule {

  String getName() { 'CapabilityRequiredMethods' }

  /** capability name -> required method names. */
  private final Map<String, List<String>> required

  CapabilityRequiredMethodsRule(Map<String, List<String>> required) {
    this.required = required
  }

  static CapabilityRequiredMethodsRule fromResource(String path = '/hubitat-capability-methods.txt') {
    InputStream is = CapabilityRequiredMethodsRule.classLoader.getResourceAsStream(path.replaceFirst('^/', ''))
    if (is == null) {
      throw new IllegalStateException("Cannot load capability methods catalog from $path")
    }
    Map<String, List<String>> map = [:]
    is.text.split('\n').each { String line ->
      String s = line.trim()
      if (s.empty || s.startsWith('#')) return
      int colon = s.indexOf(':')
      if (colon < 0) return
      String cap = s.substring(0, colon).trim()
      String rest = s.substring(colon + 1).trim()
      List<String> methods = rest.empty ? [] : rest.split(',').collect { String m -> m.trim() }.findAll { String m -> !m.empty }
      map[cap] = methods
    }
    new CapabilityRequiredMethodsRule(map)
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

    AstUtil.callsInClosure(metaBody).each { MethodCallExpression mce ->
      if (AstUtil.methodName(mce) != 'definition') return
      ClosureExpression defBody = AstUtil.trailingClosure(mce)
      if (defBody == null) return
      AstUtil.callsInClosure(defBody).each { MethodCallExpression call ->
        if (AstUtil.methodName(call) != 'capability') return
        List<Expression> args = AstUtil.argList(call)
        if (args.empty) return
        String cap = AstUtil.constantString(args[0])
        if (cap == null) return
        List<String> need = required[cap]
        if (need == null) return  // unknown capability handled by DriverDefinitionRule
        need.each { String m ->
          if (!definedMethods.contains(m)) {
            Severity sev = hasUnresolvedIncludes ? Severity.WARNING : Severity.ERROR
            out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, sev,
              "capability '${cap}' requires method '${m}()' but it is not defined locally or in any #include'd library.")
          }
        }
      }
    }
    out
  }
}
