package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.SourceUnit

/**
 * Catches things that are syntactically valid Groovy but rejected by the
 * Hubitat installation/runtime sandbox:
 *   - `package` declarations (Hubitat strips/rejects them)
 *   - direct `System.exit(...)`, `Runtime.getRuntime()`, `Thread.start { }` etc.
 *   - `new File(...)` or any `java.io.File` use - the sandbox blocks the file system
 *   - reflection (`Class.forName`, `getClass().getMethod` etc.)
 *   - `eval`/`Eval`
 *   - `@Grab`/`@Grapes` annotations (sandbox forbids dependency loading)
 */
@CompileStatic
class SandboxRule implements Rule {

  String getName() { 'Sandbox' }

  /** Disallowed receiver-method pairs. Receiver is matched by *simple* name or FQN. */
  static final Map<String, Set<String>> DISALLOWED_CALLS = [
    'System'      : (['exit', 'gc', 'load', 'loadLibrary'] as Set),
    'Runtime'     : (['getRuntime', 'exec'] as Set),
    'Class'       : (['forName'] as Set),
    'Thread'      : (['start', 'sleep'] as Set),
    'ClassLoader' : (['getSystemClassLoader'] as Set),
    'Eval'        : (['me', 'x', 'xy', 'xyz'] as Set),
  ]

  /** Receivers banned outright (any method on them is suspect). */
  static final Set<String> DISALLOWED_RECEIVERS = ([
    'java.io.File', 'java.lang.ProcessBuilder', 'ProcessBuilder',
    'java.lang.Runtime'
  ] as Set)

  List<Violation> check(ParseResult pr) {
    if (pr.module == null) return []
    List<Violation> out = []

    // package declaration
    if (pr.module.packageName != null && !pr.module.packageName.empty) {
      out << new Violation(pr.filePath,
        pr.module.package?.lineNumber ?: 1,
        pr.module.package?.columnNumber ?: 1,
        name, Severity.ERROR,
        "Hubitat scripts must not declare a 'package'. Found: package ${pr.module.packageName.replaceFirst(/\.$/, '')}")
    }

    // @Grab / @Grapes can land on classes, methods, fields, or imports.
    pr.module.classes.each { cn ->
      reportGrab(pr.filePath, cn.annotations, out)
      cn.fields.each { f -> reportGrab(pr.filePath, f.annotations, out) }
      cn.methods.each { m -> reportGrab(pr.filePath, m.annotations, out) }
    }
    pr.module.imports.each { i -> reportGrab(pr.filePath, i.annotations, out) }
    pr.module.starImports.each { i -> reportGrab(pr.filePath, i.annotations, out) }

    Visitor v = new Visitor(pr.filePath, name, out)
    // The Hubitat script class' run() method holds the top-level statements
    // and user methods, so visitContents covers both. Don't also visit
    // statementBlock - that would double-count.
    pr.module.classes.each { it.visitContents(v) }
    out
  }

  private void reportGrab(String file, List anns, List<Violation> out) {
    anns.each { ann ->
      org.codehaus.groovy.ast.AnnotationNode a = (org.codehaus.groovy.ast.AnnotationNode) ann
      String simpleName = a.classNode.nameWithoutPackage
      if (simpleName == 'Grab' || simpleName == 'Grapes' || simpleName == 'GrabResolver' || simpleName == 'GrabExclude' || simpleName == 'GrabConfig') {
        out << new Violation(file, a.lineNumber, a.columnNumber, name, Severity.ERROR,
          "@${simpleName} is not allowed - the Hubitat sandbox cannot fetch external dependencies.")
      }
    }
  }

  @CompileStatic
  static class Visitor extends ClassCodeVisitorSupport {
    final String file
    final String ruleName
    final List<Violation> out

    Visitor(String file, String ruleName, List<Violation> out) {
      this.file = file
      this.ruleName = ruleName
      this.out = out
    }

    @Override
    protected SourceUnit getSourceUnit() { null }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
      String mname = AstUtil.methodName(call)
      String receiver = receiverName(call)
      if (mname != null && receiver != null) {
        Set<String> blocked = DISALLOWED_CALLS[receiver]
        if (blocked != null && blocked.contains(mname)) {
          out << new Violation(file, call.lineNumber, call.columnNumber, ruleName, Severity.ERROR,
            "Disallowed call '${receiver}.${mname}(...)' - Hubitat sandbox will reject this.")
        }
        if (DISALLOWED_RECEIVERS.contains(receiver)) {
          out << new Violation(file, call.lineNumber, call.columnNumber, ruleName, Severity.ERROR,
            "Disallowed call on '${receiver}' - Hubitat sandbox will reject this.")
        }
      }
      super.visitMethodCallExpression(call)
    }

    @Override
    void visitConstructorCallExpression(ConstructorCallExpression call) {
      String fqn = call.type.name
      String simple = call.type.nameWithoutPackage
      if (DISALLOWED_RECEIVERS.contains(fqn) || DISALLOWED_RECEIVERS.contains(simple)) {
        out << new Violation(file, call.lineNumber, call.columnNumber, ruleName, Severity.ERROR,
          "Disallowed 'new ${simple}(...)' - Hubitat sandbox will reject this.")
      }
      super.visitConstructorCallExpression(call)
    }

    private static String receiverName(MethodCallExpression call) {
      def obj = call.objectExpression
      if (obj instanceof VariableExpression) {
        return ((VariableExpression) obj).name
      }
      if (obj instanceof PropertyExpression) {
        // chase property expressions to a name like "java.io.File" or "Runtime"
        return propertyChain((PropertyExpression) obj)
      }
      null
    }

    private static String propertyChain(PropertyExpression pe) {
      List<String> parts = []
      def cur = pe
      while (cur instanceof PropertyExpression) {
        parts.add(0, ((PropertyExpression) cur).propertyAsString)
        cur = ((PropertyExpression) cur).objectExpression
      }
      if (cur instanceof VariableExpression) {
        parts.add(0, ((VariableExpression) cur).name)
      }
      parts.join('.')
    }
  }
}
