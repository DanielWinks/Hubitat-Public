package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException

import java.security.CodeSource

/**
 * Wraps Groovy's CompilationUnit so we can parse Hubitat scripts to AST without
 * actually resolving classes or executing code. We stop at the CONVERSION phase
 * which is where the AST is fully built but type resolution hasn't happened.
 */
@CompileStatic
class HubitatScriptParser {

  /** Parse a single source file. Always returns a ParseResult; check isParseSuccessful(). */
  static ParseResult parse(File file) {
    String original = file.text
    Preprocessor.Result pp = Preprocessor.process(original)

    ParseResult result = new ParseResult(
      filePath: file.path,
      originalSource: original,
      preprocessed: pp,
    )

    CompilerConfiguration cc = new CompilerConfiguration()
    cc.tolerance = 0
    cc.sourceEncoding = 'UTF-8'
    // Disable @Grab/Grapes resolution - we report those as sandbox violations
    // ourselves and don't want the parser pulling dependencies from the network.
    cc.disabledGlobalASTTransformations = (['groovy.grape.GrabAnnotationTransformation'] as Set)

    GroovyClassLoader gcl = new GroovyClassLoader(HubitatScriptParser.classLoader, cc)
    CompilationUnit cu = new CompilationUnit(cc, (CodeSource) null, gcl)
    SourceUnit su = SourceUnit.create(file.path, pp.source)
    cu.addSource(su)

    try {
      cu.compile(Phases.CONVERSION)
      result.module = su.AST
    } catch (MultipleCompilationErrorsException ex) {
      ex.errorCollector.errors.each { Message m ->
        if (m instanceof SyntaxErrorMessage) {
          SyntaxException se = ((SyntaxErrorMessage) m).cause
          result.syntaxViolations << new Violation(
            file.path,
            se.startLine,
            se.startColumn,
            'SyntaxError',
            Severity.ERROR,
            se.originalMessage
          )
        } else {
          result.syntaxViolations << new Violation(
            file.path, 0, 0, 'SyntaxError', Severity.ERROR, m.toString()
          )
        }
      }
      // Parsing might have produced a partial AST - still try to use it.
      try { result.module = su.AST } catch (Exception ignored) { /* leave null */ }
    } catch (Exception ex) {
      result.syntaxViolations << new Violation(
        file.path, 0, 0, 'ParserError', Severity.ERROR,
        "Parser threw ${ex.class.simpleName}: ${ex.message}"
      )
    }

    if (result.module != null) {
      result.kind = detectKind(result.module)
    }
    result
  }

  /**
   * Determine whether the script is an App, Driver, or Library by looking for
   * the characteristic top-level method calls. The Groovy parser turns a Hubitat
   * script into a Script subclass with the top-level statements in run().
   */
  static SourceKind detectKind(ModuleNode module) {
    // Top-level statements live on module.statementBlock for scripts.
    BlockStatement block = module.statementBlock
    boolean hasLibrary = false
    boolean hasMetadata = false
    boolean hasDefinition = false

    block.statements.each { Statement st ->
      if (st instanceof ExpressionStatement) {
        def expr = ((ExpressionStatement) st).expression
        if (expr instanceof MethodCallExpression) {
          MethodCallExpression mce = (MethodCallExpression) expr
          if (mce.objectExpression instanceof VariableExpression
              && ((VariableExpression) mce.objectExpression).name == 'this') {
            String name = methodName(mce)
            if (name == 'library') hasLibrary = true
            if (name == 'metadata') hasMetadata = true
            if (name == 'definition') hasDefinition = true
          }
        }
      }
    }

    // App takes priority over Driver because Apps declare top-level definition()
    // and Drivers wrap it inside metadata { }. A file with both is ill-formed
    // and should be classified by its primary entry point (App).
    if (hasLibrary) return SourceKind.LIBRARY
    if (hasDefinition) return SourceKind.APP
    if (hasMetadata) return SourceKind.DRIVER
    SourceKind.UNKNOWN
  }

  static String methodName(MethodCallExpression mce) {
    if (mce.method instanceof ConstantExpression) {
      return ((ConstantExpression) mce.method).value?.toString()
    }
    null
  }
}
