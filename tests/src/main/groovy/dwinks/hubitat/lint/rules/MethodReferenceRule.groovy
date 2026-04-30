package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.ProjectIndex
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.SourceKind
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.SourceUnit

/**
 * Catches the most common runtime "method not found" failure on Hubitat:
 * passing a method name as a string to one of the scheduling/subscription
 * helpers, but the method doesn't actually exist in the file (or any
 * #include'd library, which we approximate by looking at the include list).
 *
 * Methods checked:
 *   subscribe(device, attr, 'handlerName')
 *   runIn(secs, 'methodName' [, opts])
 *   runInMillis(ms, 'methodName' [, opts])
 *   schedule(cron, 'methodName')
 *   runEvery1Minute('methodName'), runEvery5Minutes(...), runEvery30Minutes,
 *   runEvery1Hour, runEvery3Hours, runEveryHour, runEveryDayAtHHMM,
 *   asynchttpGet/Post/Put/Patch/Delete/Head('callbackName', ...)
 *   mappings { path("/x") { action: [GET: "handlerMethodName"] } }
 *
 * The rule reports an ERROR if the referenced method is not defined locally
 * AND the file doesn't #include any libraries (because then we'd be guessing).
 * Otherwise, it reports a WARNING - the method might be defined in the
 * included library.
 */
@CompileStatic
class MethodReferenceRule implements Rule {

  static final Set<String> SCHEDULING_FUNCS = ([
    'runIn', 'runInMillis', 'schedule',
    'runEvery1Minute', 'runEvery5Minutes', 'runEvery10Minutes',
    'runEvery15Minutes', 'runEvery30Minutes',
    'runEvery1Hour', 'runEveryHour', 'runEvery3Hours',
    'asynchttpGet', 'asynchttpPost', 'asynchttpPut',
    'asynchttpPatch', 'asynchttpDelete', 'asynchttpHead'
  ] as Set)

  /** Method name string is at this index in the call argument list. */
  static final Map<String, Integer> METHOD_NAME_ARG_INDEX = [
    'runIn'           : 1,
    'runInMillis'     : 1,
    'schedule'        : 1,
    'runEvery1Minute' : 0,
    'runEvery5Minutes': 0,
    'runEvery10Minutes': 0,
    'runEvery15Minutes': 0,
    'runEvery30Minutes': 0,
    'runEvery1Hour'   : 0,
    'runEveryHour'    : 0,
    'runEvery3Hours'  : 0,
    'asynchttpGet'    : 0,
    'asynchttpPost'   : 0,
    'asynchttpPut'    : 0,
    'asynchttpPatch'  : 0,
    'asynchttpDelete' : 0,
    'asynchttpHead'   : 0,
    'subscribe'       : 2  // subscribe(thing, attr, 'handler')
  ] as Map<String, Integer>

  String getName() { 'MethodReference' }

  List<Violation> check(ParseResult pr) {
    check(pr, new ProjectIndex())
  }

  @Override
  List<Violation> check(ParseResult pr, ProjectIndex index) {
    if (pr.module == null) return []
    List<Violation> out = []

    // Library files are themselves intended to call into host-provided methods
    // (refresh, configure, etc.) - downgrade severity for them.
    boolean isLibrary = pr.kind == SourceKind.LIBRARY

    Set<String> defined = AstUtil.userMethods(pr.module).collect { MethodNode m -> m.name } as Set<String>
    Set<String> fromIncludes = index.resolveMethods(pr.preprocessed.includes)

    boolean hasUnresolvedIncludes = pr.preprocessed.includes.any { String fqn -> !index.knowsLibrary(fqn) }
    Severity severity
    if (isLibrary || hasUnresolvedIncludes) {
      severity = Severity.WARNING
    } else {
      severity = Severity.ERROR
    }

    Set<String> known = (defined + fromIncludes) as Set<String>
    Visitor v = new Visitor(pr.filePath, known, severity, name, out)
    // For Hubitat scripts, the top-level statements live in the synthetic
    // run() method on the Script subclass, and user methods are also attached
    // to that class. Visiting classes covers both - visiting the
    // statementBlock as well would double-count.
    pr.module.classes.each { it.visitContents(v) }
    out
  }

  @CompileStatic
  static class Visitor extends ClassCodeVisitorSupport {
    final String file
    final Set<String> defined
    final Severity severity
    final String ruleName
    final List<Violation> out

    Visitor(String file, Set<String> defined, Severity sev, String ruleName, List<Violation> out) {
      this.file = file
      this.defined = defined
      this.severity = sev
      this.ruleName = ruleName
      this.out = out
    }

    @Override
    protected SourceUnit getSourceUnit() { null }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
      String name = AstUtil.methodName(call)
      if (name != null) {
        Integer idx = METHOD_NAME_ARG_INDEX[name]
        if (idx != null) {
          List<Expression> args = AstUtil.argList(call)
          if (idx < args.size()) {
            String methodRef = AstUtil.constantString(args[idx])
            if (methodRef != null && !methodRef.empty && !defined.contains(methodRef)) {
              out << new Violation(file, call.lineNumber, call.columnNumber, ruleName, severity,
                "${name}(...) references method '${methodRef}' which is not defined in this file.")
            }
          }
        }
        if (name == 'mappings') {
          checkMappings(call)
        }
      }
      super.visitMethodCallExpression(call)
    }

    private void checkMappings(MethodCallExpression mappings) {
      ClosureExpression body = AstUtil.trailingClosure(mappings)
      if (body == null) return
      // The Hubitat idiom is `path("/x") { action: [GET: "..."] }`. Note that
      // `action:` is parsed by Groovy as a STATEMENT LABEL, not a named arg -
      // so the visitor walks every MapExpression inside path-closure bodies
      // and treats each value as a candidate method name reference.
      MappingsVisitor mv = new MappingsVisitor(this)
      body.code.visit(mv)
    }
  }

  @CompileStatic
  static class MappingsVisitor extends ClassCodeVisitorSupport {
    final Visitor parent

    MappingsVisitor(Visitor parent) { this.parent = parent }

    @Override
    protected SourceUnit getSourceUnit() { null }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
      String n = AstUtil.methodName(call)
      if (n == 'path') {
        ClosureExpression body = AstUtil.trailingClosure(call)
        if (body != null) {
          MapVisitor mapV = new MapVisitor(parent, call.lineNumber, call.columnNumber)
          body.code.visit(mapV)
        }
      }
      super.visitMethodCallExpression(call)
    }
  }

  @CompileStatic
  static class MapVisitor extends ClassCodeVisitorSupport {
    final Visitor parent
    final int line
    final int col

    MapVisitor(Visitor parent, int line, int col) {
      this.parent = parent
      this.line = line
      this.col = col
    }

    @Override
    protected SourceUnit getSourceUnit() { null }

    @Override
    void visitMapExpression(MapExpression expression) {
      for (MapEntryExpression me : expression.mapEntryExpressions) {
        String handler = AstUtil.constantString(me.valueExpression)
        if (handler != null && !parent.defined.contains(handler)) {
          parent.out << new Violation(parent.file, line, col, parent.ruleName, parent.severity,
            "mappings action references method '${handler}' which is not defined in this file.")
        }
      }
      super.visitMapExpression(expression)
    }
  }
}
