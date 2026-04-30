package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.ProjectIndex
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

@CompileStatic
interface Rule {
  String getName()
  List<Violation> check(ParseResult parseResult)

  /**
   * Optional: rules that need to look across files (e.g. resolve #include
   * symbols) override this. The default just delegates to {@link #check}.
   */
  default List<Violation> check(ParseResult parseResult, ProjectIndex index) {
    check(parseResult)
  }
}
