package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

/**
 * Surfaces any syntax errors caught during parsing. The parser already collects
 * them on the ParseResult; this rule just exposes them through the same
 * pipeline as everything else so they get the same reporting treatment.
 */
@CompileStatic
class SyntaxRule implements Rule {
  String getName() { 'Syntax' }

  List<Violation> check(ParseResult parseResult) {
    parseResult.syntaxViolations
  }
}
