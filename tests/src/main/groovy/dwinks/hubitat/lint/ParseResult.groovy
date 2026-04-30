package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ModuleNode

/**
 * Result of parsing a single Hubitat source file. Carries the AST (when
 * parsing succeeded), any syntax violations encountered, the source kind, the
 * preprocessed source text and the include list extracted by {@link Preprocessor}.
 */
@CompileStatic
class ParseResult {
  String filePath
  String originalSource
  Preprocessor.Result preprocessed
  ModuleNode module                 // null if parsing failed catastrophically
  List<Violation> syntaxViolations = []
  SourceKind kind = SourceKind.UNKNOWN

  boolean isParseSuccessful() {
    module != null && syntaxViolations.every { it.severity != Severity.ERROR }
  }
}
