/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (danielwinks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.ForStatement

/**
 * Flags {@code value.length} (property form) on an expression known to be a
 * String. Groovy Strings have a {@code length()} method but no {@code length}
 * property, so this fails at runtime with
 * {@code MissingPropertyException: No such property: length for class java.lang.String}.
 * Arrays do have a {@code length} property and are never flagged.
 *
 * A receiver counts as a String only with positive evidence:
 *   - a string literal or GString;
 *   - {@code x.toString()};
 *   - a String-returning method ({@code substring}, {@code trim}, ...) called on
 *     a receiver that is itself known to be a String;
 *   - a variable or parameter declared as {@code String} in the lexical scope
 *     that is visible at the use (a sibling block's or an inner scope's
 *     declaration of the same name is not considered).
 */
@CompileStatic
class StringLengthPropertyRule implements Rule {

  /** String methods that return a String. Only trusted when the receiver is a known String. */
  static final Set<String> STRING_RETURNING_METHODS = ([
    'substring', 'trim', 'toLowerCase', 'toUpperCase', 'replace',
    'replaceAll', 'replaceFirst', 'stripIndent', 'capitalize'
  ] as Set)

  String getName() { 'StringLengthProperty' }

  List<Violation> check(ParseResult pr) {
    if (pr.module == null) { return [] }
    List<Violation> out = []
    Set<String> reported = []
    List<MethodNode> methods = []
    methods.addAll(pr.module.methods)
    pr.module.classes.each { ClassNode cls -> methods.addAll(cls.methods) }
    methods.each { MethodNode method ->
      if (method.code == null) { return }
      ScopeVisitor visitor = new ScopeVisitor()
      visitor.push()
      method.parameters.each { Parameter p -> visitor.declare(p.name, isStringType(p.type)) }
      method.code.visit(visitor)
      visitor.hits.each { PropertyExpression expression ->
        if (reported.add("${expression.lineNumber}:${expression.columnNumber}".toString())) {
          out << new Violation(pr.filePath, expression.lineNumber, expression.columnNumber, name, Severity.ERROR,
            "'.length' on a String fails at runtime (MissingPropertyException); use '.length()'.")
        }
      }
    }
    out
  }

  /** Walks one method body tracking which names are Strings in each lexical scope. */
  private static class ScopeVisitor extends CodeVisitorSupport {
    final List<Map<String, Boolean>> scopes = []
    final List<PropertyExpression> hits = []

    void push() { scopes.add(new HashMap<String, Boolean>()) }
    void pop() { scopes.remove(scopes.size() - 1) }
    void declare(String name, boolean string) { scopes.last().put(name, string) }

    boolean isStringName(String name) {
      for (int i = scopes.size() - 1; i >= 0; i--) {
        Boolean known = scopes.get(i).get(name)
        if (known != null) { return known.booleanValue() }
      }
      false
    }

    @Override
    void visitBlockStatement(BlockStatement block) {
      push()
      super.visitBlockStatement(block)
      pop()
    }

    @Override
    void visitForLoop(ForStatement forLoop) {
      push()
      Parameter variable = forLoop.variable
      if (variable != null) { declare(variable.name, isStringType(variable.type)) }
      super.visitForLoop(forLoop)
      pop()
    }

    @Override
    void visitCatchStatement(CatchStatement statement) {
      push()
      declare(statement.variable.name, isStringType(statement.variable.type))
      super.visitCatchStatement(statement)
      pop()
    }

    @Override
    void visitClosureExpression(ClosureExpression expression) {
      push()
      if (expression.parameters != null) {
        expression.parameters.each { Parameter p -> declare(p.name, isStringType(p.type)) }
      }
      super.visitClosureExpression(expression)
      pop()
    }

    @Override
    void visitDeclarationExpression(DeclarationExpression expression) {
      // Visit the initializer first so `String s = s.length` cannot see its own declaration.
      expression.rightExpression.visit(this)
      Expression left = expression.leftExpression
      if (left instanceof VariableExpression) {
        VariableExpression variable = (VariableExpression) left
        declare(variable.name, isStringType(variable.type))
      }
    }

    @Override
    void visitPropertyExpression(PropertyExpression expression) {
      if (expression.propertyAsString == 'length' && !expression.spreadSafe && isString(expression.objectExpression)) {
        hits << expression
      }
      super.visitPropertyExpression(expression)
    }

    boolean isString(Expression receiver) {
      if (receiver instanceof ConstantExpression) { return ((ConstantExpression) receiver).value instanceof String }
      if (receiver instanceof GStringExpression) { return true }
      if (receiver instanceof MethodCallExpression) {
        MethodCallExpression call = (MethodCallExpression) receiver
        if (!(call.method instanceof ConstantExpression)) { return false }
        String method = ((ConstantExpression) call.method).value?.toString()
        if (method == 'toString' && call.arguments instanceof TupleExpression && ((TupleExpression) call.arguments).expressions.isEmpty()) { return true }
        return STRING_RETURNING_METHODS.contains(method) && isString(call.objectExpression)
      }
      if (receiver instanceof VariableExpression) { return isStringName(((VariableExpression) receiver).name) }
      false
    }
  }

  /** Types are unresolved at the CONVERSION phase, so accept the simple and qualified name. */
  private static boolean isStringType(ClassNode type) {
    type.name == 'String' || type.name == 'java.lang.String'
  }
}
