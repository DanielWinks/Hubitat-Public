package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ImportNode

/**
 * Verifies every import in a Hubitat file is on the curated allowlist. The
 * allowlist supports exact FQNs ("java.util.Random") and wildcard prefixes
 * ("hubitat.*", "java.util.concurrent.*").
 */
@CompileStatic
class ImportRule implements Rule {

  String getName() { 'Imports' }

  private final Set<String> exact
  private final List<String> prefixes

  ImportRule(Collection<String> entries) {
    Set<String> ex = []
    List<String> px = []
    entries.each { String raw ->
      String s = raw.trim()
      if (s.empty || s.startsWith('#')) return
      if (s.endsWith('.*')) {
        px << s.substring(0, s.length() - 2)
      } else if (s.endsWith('*')) {
        px << s.substring(0, s.length() - 1)
      } else {
        ex << s
      }
    }
    this.exact = ex
    this.prefixes = px
  }

  static ImportRule fromResource(String path = '/allowed-imports.txt') {
    InputStream is = ImportRule.classLoader.getResourceAsStream(path.replaceFirst('^/', ''))
    if (is == null) {
      throw new IllegalStateException("Cannot load allowed imports list from $path")
    }
    new ImportRule(is.text.split('\n').toList())
  }

  List<Violation> check(ParseResult pr) {
    if (pr.module == null) return []
    List<Violation> out = []

    pr.module.imports.each { ImportNode i ->
      String fqn = i.className
      if (!isAllowed(fqn)) {
        out << new Violation(pr.filePath, i.lineNumber, i.columnNumber, name, Severity.ERROR,
          "Import '${fqn}' is not on the Hubitat sandbox allowlist.")
      }
    }
    pr.module.starImports.each { ImportNode i ->
      // ImportNode.packageName for star imports (foo.bar.*)
      String pkg = i.packageName
      // packageName always ends with '.', strip trailing dot for matching
      String candidate = pkg.endsWith('.') ? pkg[0..-2] : pkg
      if (!isAllowed("${candidate}.*")) {
        out << new Violation(pr.filePath, i.lineNumber, i.columnNumber, name, Severity.ERROR,
          "Star import '${pkg}*' is not on the Hubitat sandbox allowlist.")
      }
    }
    pr.module.staticImports.each { String alias, ImportNode i ->
      String fqn = "${i.className}.${i.fieldName}"
      if (!isAllowed(i.className) && !isAllowed(fqn)) {
        out << new Violation(pr.filePath, i.lineNumber, i.columnNumber, name, Severity.ERROR,
          "Static import '${fqn}' is not on the Hubitat sandbox allowlist.")
      }
    }
    out
  }

  boolean isAllowed(String candidate) {
    if (exact.contains(candidate)) return true
    if (candidate.endsWith('.*')) {
      String stripped = candidate[0..-3]
      // wildcard request matches if any prefix is an ancestor or equal
      return prefixes.any { stripped == it || stripped.startsWith("${it}.") } ||
             prefixes.contains(stripped)
    }
    prefixes.any { candidate == it || candidate.startsWith("${it}.") }
  }
}
