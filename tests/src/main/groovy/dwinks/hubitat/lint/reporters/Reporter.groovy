package dwinks.hubitat.lint.reporters

import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

@CompileStatic
interface Reporter {
  void report(List<Violation> violations)
}
