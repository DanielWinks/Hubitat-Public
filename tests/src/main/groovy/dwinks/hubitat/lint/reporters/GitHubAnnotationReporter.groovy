package dwinks.hubitat.lint.reporters

import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic

/**
 * Emits annotations in the format that GitHub Actions recognises so that
 * lint findings show up inline on the Files Changed view of a pull request.
 *
 *   ::error file=path,line=N,col=M,title=Rule::message
 *   ::warning file=path,line=N,col=M,title=Rule::message
 *   ::notice  file=path,line=N,col=M,title=Rule::message
 */
@CompileStatic
class GitHubAnnotationReporter implements Reporter {

  void report(List<Violation> violations) {
    violations.each { Violation v ->
      String level
      switch (v.severity) {
        case Severity.ERROR:   level = 'error';   break
        case Severity.WARNING: level = 'warning'; break
        default:               level = 'notice';  break
      }
      String message = v.message.replace('\n', ' %0A ').replace('\r', '')
      println("::${level} file=${v.file},line=${v.line},col=${v.column},title=${v.rule}::${message}")
    }
  }
}
