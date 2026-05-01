package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Static helpers for navigating the bits of the Groovy AST that Hubitat
 * idioms produce. Hubitat scripts compile to a Script subclass whose run()
 * method holds the top-level statements (definition/metadata/preferences/
 * mappings/imports etc.).
 */
@CompileStatic
class AstUtil {

  /** Method name for a MethodCallExpression, or null if the method ref isn't a constant. */
  static String methodName(MethodCallExpression mce) {
    Expression m = mce.method
    if (m instanceof ConstantExpression) {
      return ((ConstantExpression) m).value?.toString()
    }
    null
  }

  /** Check if the receiver of a MethodCallExpression is the implicit `this`. */
  static boolean isImplicitThis(MethodCallExpression mce) {
    if (mce.objectExpression instanceof VariableExpression) {
      String n = ((VariableExpression) mce.objectExpression).name
      return n == 'this'
    }
    false
  }

  /** Top-level expression statements in a script body. */
  static List<MethodCallExpression> topLevelCalls(ModuleNode module) {
    List<MethodCallExpression> out = []
    BlockStatement block = module.statementBlock
    if (block == null) return out
    block.statements.each { Statement st ->
      if (st instanceof ExpressionStatement) {
        Expression e = ((ExpressionStatement) st).expression
        if (e instanceof MethodCallExpression) {
          out << (MethodCallExpression) e
        }
      }
    }
    out
  }

  /** Find top-level call by name (definition, metadata, preferences, mappings, library). */
  static MethodCallExpression findTopLevelCall(ModuleNode module, String name) {
    topLevelCalls(module).find { isImplicitThis(it) && methodName(it) == name }
  }

  /** Argument list for a method call. */
  static List<Expression> argList(MethodCallExpression mce) {
    Expression args = mce.arguments
    if (args instanceof TupleExpression) {
      return ((TupleExpression) args).expressions
    }
    Collections.<Expression>emptyList()
  }

  /** Treat any ArgumentList containing a single MapExpression / NamedArgumentList as named args. */
  static Map<String, Expression> namedArgs(MethodCallExpression mce) {
    Map<String, Expression> result = [:]
    Expression args = mce.arguments
    if (args instanceof TupleExpression) {
      ((TupleExpression) args).expressions.each { Expression e ->
        addEntries(result, e)
      }
    } else if (args instanceof MapExpression) {
      addEntries(result, args)
    }
    result
  }

  private static void addEntries(Map<String, Expression> result, Expression e) {
    if (e instanceof NamedArgumentListExpression) {
      ((NamedArgumentListExpression) e).mapEntryExpressions.each { MapEntryExpression me ->
        addEntry(result, me)
      }
    } else if (e instanceof MapExpression) {
      ((MapExpression) e).mapEntryExpressions.each { MapEntryExpression me ->
        addEntry(result, me)
      }
    }
  }

  private static void addEntry(Map<String, Expression> result, MapEntryExpression me) {
    Expression key = me.keyExpression
    if (key instanceof ConstantExpression) {
      result[((ConstantExpression) key).value?.toString()] = me.valueExpression
    }
  }

  /** Return a string constant value or null. */
  static String constantString(Expression e) {
    if (e instanceof ConstantExpression) {
      Object v = ((ConstantExpression) e).value
      return v == null ? null : v.toString()
    }
    null
  }

  /** Closure body for the last argument of a call (Hubitat's metadata/preferences/section). */
  static ClosureExpression trailingClosure(MethodCallExpression mce) {
    List<Expression> args = argList(mce)
    if (args && args.last() instanceof ClosureExpression) {
      return (ClosureExpression) args.last()
    }
    null
  }

  /** Walk every MethodCallExpression in a closure body. */
  static List<MethodCallExpression> callsInClosure(ClosureExpression ce) {
    List<MethodCallExpression> out = []
    if (ce == null) return out
    Statement code = ce.code
    if (code instanceof BlockStatement) {
      ((BlockStatement) code).statements.each { Statement s ->
        if (s instanceof ExpressionStatement) {
          Expression e = ((ExpressionStatement) s).expression
          if (e instanceof MethodCallExpression) {
            out << (MethodCallExpression) e
          }
        }
      }
    }
    out
  }

  /** Has-annotation check by simple name (e.g. 'CompileStatic'). */
  static boolean hasAnnotation(AnnotatedNode node, String simpleName) {
    node.annotations.any { AnnotationNode a -> a.classNode.nameWithoutPackage == simpleName }
  }

  /** All non-script methods declared in the module. */
  static List<MethodNode> userMethods(ModuleNode module) {
    List<MethodNode> out = []
    module.classes.each { ClassNode cn ->
      cn.methods.each { MethodNode mn ->
        // Skip synthetic/script methods like main/run from Script base class
        if (!mn.synthetic && mn.name != 'main' && mn.name != 'run') {
          out << mn
        }
      }
    }
    out
  }
}
