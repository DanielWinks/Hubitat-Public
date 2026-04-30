package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.ProjectIndex
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

/**
 * Catches collisions in the merged symbol table that Hubitat would build at
 * #include time. Flags:
 *   - Two methods with the same signature defined within a single file
 *     (already a Groovy compile error, but useful to catch in one place)
 *   - A host file's method colliding with one provided by an #included library
 *   - The same library #included twice (its methods would land in the merged
 *     class twice, so it's a fatal duplicate)
 *
 * Two methods collide when their (name, parameter type list) match exactly.
 * Overloads with different parameter lists are allowed and not flagged.
 */
@CompileStatic
class DuplicateMethodRule implements Rule {

  String getName() { 'DuplicateMethod' }

  List<Violation> check(ParseResult pr) {
    check(pr, new ProjectIndex())
  }

  @Override
  List<Violation> check(ParseResult pr, ProjectIndex index) {
    if (pr.module == null) return []
    List<Violation> out = []

    // The same library #include'd twice would merge twice. Flag that first.
    Set<String> seenIncludes = []
    pr.preprocessed.includes.each { String fqn ->
      if (!seenIncludes.add(fqn)) {
        out << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
          "Library '${fqn}' is #include'd more than once - its methods would merge into the host class twice.")
      }
    }

    // Build (signatureKey -> definitions). Includes only registered libraries.
    Map<String, List<ProjectIndex.MethodDef>> bySig = [:].withDefault {
      (List<ProjectIndex.MethodDef>) ([] as List<ProjectIndex.MethodDef>)
    }

    // Locally-declared methods.
    ProjectIndex.collectMethods(pr.filePath, pr).each { ProjectIndex.MethodDef d ->
      ((List<ProjectIndex.MethodDef>) bySig[d.signatureKey()]) << d
    }

    // Methods from each #include'd library (deduped against the seen set so
    // a double-include doesn't cause every method to appear as a duplicate).
    Set<String> processed = []
    pr.preprocessed.includes.each { String fqn ->
      if (!processed.add(fqn)) return
      List<ProjectIndex.MethodDef> defs = index.libraryMethodDefs[fqn]
      if (defs == null) return
      defs.each { ProjectIndex.MethodDef d ->
        ((List<ProjectIndex.MethodDef>) bySig[d.signatureKey()]) << d
      }
    }

    bySig.each { String sig, List<ProjectIndex.MethodDef> defs ->
      if (defs.size() < 2) return
      // Pick the host file's location to anchor the violation if the host is
      // one of the duplicates; otherwise anchor to the first occurrence.
      ProjectIndex.MethodDef anchor = defs.find { it.file == pr.filePath } ?: defs[0]
      String otherLocations = defs.findAll { it != anchor }.collect { ProjectIndex.MethodDef d ->
        "${niceFile(d.file)}:${d.line}"
      }.join(', ')
      out << new Violation(pr.filePath, anchor.line, 0, name, Severity.ERROR,
        "Duplicate method '${sig}' - also defined at: ${otherLocations}")
    }
    out
  }

  private static String niceFile(String path) {
    int slash = path.lastIndexOf('/')
    slash >= 0 ? path.substring(slash + 1) : path
  }
}
