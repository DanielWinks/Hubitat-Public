package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import groovy.transform.ToString

@CompileStatic
@ToString(includeNames = true, includePackage = false)
class Violation {
  String file
  int line
  int column
  String rule
  Severity severity
  String message

  Violation(String file, int line, int column, String rule, Severity severity, String message) {
    this.file = file
    this.line = Math.max(line, 0)
    this.column = Math.max(column, 0)
    this.rule = rule
    this.severity = severity
    this.message = message
  }

  String render() {
    "[${severity}] ${file}:${line}:${column} (${rule}) ${message}"
  }
}
