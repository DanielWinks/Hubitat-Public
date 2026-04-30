package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Cross-file lookup table built once per lint run. Maps a library's fully
 * qualified Hubitat name (e.g. "dwinks.UtilitiesAndLoggingLibrary") to the
 * methods and {@code @Field} declarations it contributes when that library is
 * #included. Rules consult this index so callbacks resolve correctly across
 * file boundaries, and so duplicate-method / duplicate-field rules can flag
 * collisions that Hubitat's runtime would reject when it tries to merge the
 * library into the host script.
 *
 * Library identity is determined by the library() top-level call's `namespace`
 * + `name` arguments - matching how Hubitat resolves #include directives.
 */
@CompileStatic
class ProjectIndex {

  @ToString(includeNames = true)
  static class MethodDef {
    String name
    String file
    int line
    /** Simple parameter type names, e.g. ["String", "Map"]. */
    List<String> paramTypes
    /** Methods with the same key collide when merged. */
    String signatureKey() { "${name}(${paramTypes.join(',')})" }
  }

  @ToString(includeNames = true)
  static class FieldDef {
    String name
    String file
    int line
    boolean isStatic
    boolean isFinal
  }

  /** namespace.LibraryName -> file path it lives in (for diagnostics). */
  final Map<String, String> libraryFiles = [:]

  /** namespace.LibraryName -> method definitions it contributes. */
  final Map<String, List<MethodDef>> libraryMethodDefs = [:]

  /** namespace.LibraryName -> @Field declarations it contributes. */
  final Map<String, List<FieldDef>> libraryFieldDefs = [:]

  void register(ParseResult pr) {
    if (pr.module == null || pr.kind != SourceKind.LIBRARY) return
    MethodCallExpression lib = AstUtil.findTopLevelCall(pr.module, 'library')
    if (lib == null) return
    Map args = AstUtil.namedArgs(lib)
    String ns = AstUtil.constantString((Expression) args['namespace'])
    String nm = AstUtil.constantString((Expression) args['name'])
    if (ns == null || nm == null) return
    String fqn = "${ns}.${nm}"
    libraryFiles[fqn] = pr.filePath
    libraryMethodDefs[fqn] = collectMethods(pr.filePath, pr)
    libraryFieldDefs[fqn] = collectFields(pr.filePath, pr)
  }

  /** Methods declared by host or library file - for duplicate-detection use. */
  static List<MethodDef> collectMethods(String file, ParseResult pr) {
    List<MethodDef> out = []
    AstUtil.userMethods(pr.module).each { MethodNode m ->
      out << new MethodDef(
        name: m.name,
        file: file,
        line: m.lineNumber,
        paramTypes: m.parameters.collect { Parameter p ->
          p.type?.nameWithoutPackage ?: 'Object'
        }
      )
    }
    out
  }

  /**
   * Top-level {@code @Field} declarations.
   *
   * IMPORTANT: at the CONVERSION compile phase we use, the @Field AST
   * transformation has not yet run, so these declarations live as
   * DeclarationExpressions inside the script's statementBlock - NOT as fields
   * on the script class. We pluck them out by walking the statementBlock for
   * ExpressionStatements whose expression is a DeclarationExpression carrying
   * a @Field annotation.
   */
  static List<FieldDef> collectFields(String file, ParseResult pr) {
    List<FieldDef> out = []
    if (pr.module?.statementBlock == null) return out

    pr.module.statementBlock.statements.each { Statement st ->
      if (!(st instanceof ExpressionStatement)) return
      Expression e = ((ExpressionStatement) st).expression
      if (!(e instanceof DeclarationExpression)) return
      DeclarationExpression de = (DeclarationExpression) e
      boolean hasFieldAnnotation = de.annotations.any { AnnotationNode a ->
        a.classNode.nameWithoutPackage == 'Field'
      }
      if (!hasFieldAnnotation) return
      Expression left = de.leftExpression
      if (!(left instanceof VariableExpression)) return
      VariableExpression ve = (VariableExpression) left
      out << new FieldDef(
        name: ve.name,
        file: file,
        line: de.lineNumber,
        isStatic: true,  // @Field at script level always becomes a static field
        isFinal: false
      )
    }
    out
  }

  /** Union of all method names from the listed includes. */
  Set<String> resolveMethods(List<String> includes) {
    Set<String> out = []
    includes.each { String fqn ->
      List<MethodDef> defs = libraryMethodDefs[fqn]
      if (defs != null) defs.each { out.add(it.name) }
    }
    out
  }

  /** Method definitions (with locations) from each include. */
  List<MethodDef> resolveMethodDefs(List<String> includes) {
    List<MethodDef> out = []
    includes.each { String fqn ->
      List<MethodDef> defs = libraryMethodDefs[fqn]
      if (defs != null) out.addAll(defs)
    }
    out
  }

  /** Field definitions (with locations) from each include. */
  List<FieldDef> resolveFieldDefs(List<String> includes) {
    List<FieldDef> out = []
    includes.each { String fqn ->
      List<FieldDef> defs = libraryFieldDefs[fqn]
      if (defs != null) out.addAll(defs)
    }
    out
  }

  boolean knowsLibrary(String fqn) {
    libraryMethodDefs.containsKey(fqn)
  }
}
