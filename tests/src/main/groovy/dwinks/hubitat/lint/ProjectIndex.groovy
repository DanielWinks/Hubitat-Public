package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.MethodCallExpression

/**
 * Cross-file lookup table built once per lint run. Maps a library's fully
 * qualified Hubitat name (e.g. "dwinks.UtilitiesAndLoggingLibrary") to the
 * set of method names it declares. The MethodReferenceRule consults this so
 * a callback like {@code runIn(1800, 'logsOff')} doesn't report a false
 * positive when 'logsOff' is provided by an #included library.
 *
 * Library identity is determined by the library() top-level call's `namespace`
 * + `name` arguments - matching how Hubitat resolves #include directives.
 */
@CompileStatic
class ProjectIndex {

  /** namespace.LibraryName -> method names declared in that library. */
  final Map<String, Set<String>> libraryMethods = [:].withDefault { (Set<String>) ([] as Set<String>) }

  /** namespace.LibraryName -> file path it lives in (for diagnostics). */
  final Map<String, String> libraryFiles = [:]

  void register(ParseResult pr) {
    if (pr.module == null || pr.kind != SourceKind.LIBRARY) return
    MethodCallExpression lib = AstUtil.findTopLevelCall(pr.module, 'library')
    if (lib == null) return
    Map args = AstUtil.namedArgs(lib)
    String ns = AstUtil.constantString((org.codehaus.groovy.ast.expr.Expression) args['namespace'])
    String nm = AstUtil.constantString((org.codehaus.groovy.ast.expr.Expression) args['name'])
    if (ns == null || nm == null) return
    String fqn = "${ns}.${nm}"
    libraryFiles[fqn] = pr.filePath
    Set<String> ms = (Set<String>) libraryMethods[fqn]
    AstUtil.userMethods(pr.module).each { MethodNode m -> ms.add(m.name) }
  }

  /** Union of all method names from the listed includes. */
  Set<String> resolveMethods(List<String> includes) {
    Set<String> out = []
    includes.each { String fqn ->
      Set<String> m = libraryMethods[fqn]
      if (m != null) out.addAll(m)
    }
    out
  }

  boolean knowsLibrary(String fqn) {
    libraryMethods.containsKey(fqn)
  }
}
