package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.ProjectIndex
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

/**
 * Flags duplicate {@code @Field static final} declarations that would clash
 * when libraries are merged into a host App or Driver via #include. Two
 * libraries that both declare {@code @Field static final String FOO = 'a'}
 * would result in a class with two identically-named static fields, which
 * the Hubitat compiler rejects.
 */
@CompileStatic
class DuplicateFieldRule implements Rule {

  String getName() { 'DuplicateField' }

  List<Violation> check(ParseResult pr) {
    check(pr, new ProjectIndex())
  }

  @Override
  List<Violation> check(ParseResult pr, ProjectIndex index) {
    if (pr.module == null) return []
    List<Violation> out = []

    Map<String, List<ProjectIndex.FieldDef>> byName = [:].withDefault {
      (List<ProjectIndex.FieldDef>) ([] as List<ProjectIndex.FieldDef>)
    }

    ProjectIndex.collectFields(pr.filePath, pr).each { ProjectIndex.FieldDef d ->
      ((List<ProjectIndex.FieldDef>) byName[d.name]) << d
    }

    Set<String> processed = []
    pr.preprocessed.includes.each { String fqn ->
      if (!processed.add(fqn)) return
      List<ProjectIndex.FieldDef> defs = index.libraryFieldDefs[fqn]
      if (defs == null) return
      defs.each { ProjectIndex.FieldDef d ->
        ((List<ProjectIndex.FieldDef>) byName[d.name]) << d
      }
    }

    byName.each { String fieldName, List<ProjectIndex.FieldDef> defs ->
      if (defs.size() < 2) return
      ProjectIndex.FieldDef anchor = defs.find { it.file == pr.filePath } ?: defs[0]
      String otherLocations = defs.findAll { it != anchor }.collect { ProjectIndex.FieldDef d ->
        "${niceFile(d.file)}:${d.line}"
      }.join(', ')
      out << new Violation(pr.filePath, anchor.line, 0, name, Severity.ERROR,
        "Duplicate @Field '${fieldName}' - also declared at: ${otherLocations}")
    }
    out
  }

  private static String niceFile(String path) {
    int slash = path.lastIndexOf('/')
    slash >= 0 ? path.substring(slash + 1) : path
  }
}
