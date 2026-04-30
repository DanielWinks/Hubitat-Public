package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.control.SourceUnit

/**
 * Pattern-based checks for code that would likely fail under @CompileStatic on
 * the Hubitat hub. We deliberately scope to high-confidence patterns; the next
 * step (when stubs are available) is to actually run the static type checker.
 *
 * Patterns flagged:
 *   1. @CompileStatic methods with untyped parameters (treated as Object/def)
 *   2. @CompileStatic methods with no declared return type
 *   3. `def` local variable declarations inside @CompileStatic methods
 *   4. catch (e) without a typed exception inside @CompileStatic methods
 *   5. closures used inside @CompileStatic where `it` is referenced and the
 *      closure parameter is untyped (this is a common Hubitat gotcha)
 */
@CompileStatic
class CompileStaticRule implements Rule {

  String getName() { 'CompileStatic' }

  List<Violation> check(ParseResult pr) {
    if (pr.module == null) return []
    List<Violation> out = []

    boolean classWide = pr.module.classes.any { AstUtil.hasAnnotation(it, 'CompileStatic') }

    AstUtil.userMethods(pr.module).each { MethodNode m ->
      boolean compileStatic = classWide || AstUtil.hasAnnotation(m, 'CompileStatic')
      if (!compileStatic) return
      checkMethod(pr.filePath, m, out)
    }
    out
  }

  private void checkMethod(String file, MethodNode m, List<Violation> out) {
    // 1. Untyped parameters
    m.parameters.each { Parameter p ->
      if (isObject(p.type) && !p.dynamicTyped) {
        // Parameter was declared as `def` -> ClassHelper.OBJECT_TYPE
      }
      if (p.dynamicTyped || isObject(p.type)) {
        out << new Violation(file, m.lineNumber, m.columnNumber, name, Severity.WARNING,
          "@CompileStatic method '${m.name}' has untyped parameter '${p.name}' (becomes Object - will fail static dispatch).")
      }
    }

    // 2. No return type for non-void method
    if (!m.voidMethod && isObject(m.returnType) && m.dynamicReturnType) {
      out << new Violation(file, m.lineNumber, m.columnNumber, name, Severity.WARNING,
        "@CompileStatic method '${m.name}' has no declared return type (defaults to Object).")
    }

    // 3,4,5: walk the method body for def declarations / catch(e) / it-closures
    BodyVisitor v = new BodyVisitor(file, m.name, name, out)
    if (m.code != null) {
      m.code.visit(v)
    }
  }

  private static boolean isObject(org.codehaus.groovy.ast.ClassNode cn) {
    cn != null && cn == ClassHelper.OBJECT_TYPE
  }

  @CompileStatic
  static class BodyVisitor extends ClassCodeVisitorSupport {
    final String file
    final String methodName
    final String ruleName
    final List<Violation> out

    BodyVisitor(String file, String methodName, String ruleName, List<Violation> out) {
      this.file = file
      this.methodName = methodName
      this.ruleName = ruleName
      this.out = out
    }

    @Override
    protected SourceUnit getSourceUnit() { null }

    @Override
    void visitDeclarationExpression(DeclarationExpression expr) {
      if (expr.leftExpression instanceof VariableExpression) {
        VariableExpression ve = (VariableExpression) expr.leftExpression
        if (ve.dynamicTyped) {
          out << new Violation(file, expr.lineNumber, expr.columnNumber, ruleName, Severity.WARNING,
            "@CompileStatic method '${methodName}' uses 'def' for local '${ve.name}' (prefer a concrete type).")
        }
      }
      super.visitDeclarationExpression(expr)
    }

    @Override
    void visitCatchStatement(CatchStatement statement) {
      Parameter p = statement.variable
      if (p != null && (p.dynamicTyped || (p.type != null && p.type == ClassHelper.OBJECT_TYPE))) {
        out << new Violation(file, statement.lineNumber, statement.columnNumber, ruleName, Severity.WARNING,
          "@CompileStatic method '${methodName}' catches untyped '${p.name}' (catch (e) -> use catch (Exception e) or similar).")
      }
      super.visitCatchStatement(statement)
    }

    @Override
    void visitClosureExpression(ClosureExpression expression) {
      // If a closure has untyped parameters AND is inside @CompileStatic, the receiver
      // type matters. We can't fully resolve here, but flag closures with explicit `it`
      // usage where parameter isn't typed.
      if (expression.parameters != null) {
        expression.parameters.each { Parameter p ->
          if (p.name == 'it') return // implicit `it` is fine for typed iteration
          if (p.dynamicTyped) {
            out << new Violation(file, expression.lineNumber, expression.columnNumber, ruleName, Severity.INFO,
              "@CompileStatic method '${methodName}' closure parameter '${p.name}' is untyped (consider declaring a type).")
          }
        }
      }
      super.visitClosureExpression(expression)
    }
  }
}
