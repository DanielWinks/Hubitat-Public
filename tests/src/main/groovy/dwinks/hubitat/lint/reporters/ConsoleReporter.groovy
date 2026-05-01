package dwinks.hubitat.lint.reporters

import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

@CompileStatic
class ConsoleReporter implements Reporter {

  final boolean colorized

  ConsoleReporter(boolean colorized = true) {
    this.colorized = colorized && System.console() != null
  }

  void report(List<Violation> violations) {
    if (violations.empty) {
      println(green('No violations found.'))
      return
    }

    Map<String, List<Violation>> byFile = violations.groupBy { Violation v -> v.file }
    byFile.keySet().sort().each { String file ->
      println()
      println(bold(file))
      byFile[file].sort { Violation v -> [v.line, v.column] }.each { Violation v ->
        String sev = colorize(v.severity)
        println("  ${sev}  ${pad(v.line, 4)}:${pad(v.column, 3)}  ${v.rule.padRight(20)} ${v.message}")
      }
    }

    int errors = (int) violations.count { Violation v -> v.severity == Severity.ERROR }
    int warns  = (int) violations.count { Violation v -> v.severity == Severity.WARNING }
    int infos  = (int) violations.count { Violation v -> v.severity == Severity.INFO }

    println()
    println("Summary: ${red(errors + ' errors')}, ${yellow(warns + ' warnings')}, ${blue(infos + ' info')}")
  }

  private String colorize(Severity s) {
    switch (s) {
      case Severity.ERROR:   return red('ERROR  ')
      case Severity.WARNING: return yellow('WARN   ')
      default:               return blue('INFO   ')
    }
  }

  private String pad(int v, int w) { String.valueOf(v).padLeft(w) }

  private String red(String s)    { colorized ? "\033[31m${s}\033[0m"    : s }
  private String green(String s)  { colorized ? "\033[32m${s}\033[0m"    : s }
  private String yellow(String s) { colorized ? "\033[33m${s}\033[0m"    : s }
  private String blue(String s)   { colorized ? "\033[34m${s}\033[0m"    : s }
  private String bold(String s)   { colorized ? "\033[1m${s}\033[0m"     : s }
}
