package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.ImportRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class ImportRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body) {
    File f = tmp.resolve('T.groovy').toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "imports from the bundled allowlist are accepted"() {
    given:
    ParseResult pr = parse('''
      import groovy.transform.CompileStatic
      import groovy.transform.Field
      import com.hubitat.app.DeviceWrapper
      import hubitat.scheduling.AsyncResponse
      import java.util.concurrent.ConcurrentHashMap

      library(name: 'X', namespace: 'dwinks', author: 'Daniel')
      void noop() { }
    '''.stripIndent())

    expect:
    ImportRule.fromResource().check(pr).empty
  }

  def "an import outside the allowlist is flagged"() {
    given:
    ParseResult pr = parse('''
      import sun.misc.Unsafe

      library(name: 'X', namespace: 'dwinks', author: 'Daniel')
    '''.stripIndent())

    when:
    List<Violation> v = ImportRule.fromResource().check(pr)

    then:
    v.size() == 1
    v[0].message.contains('sun.misc.Unsafe')
    v[0].severity == Severity.ERROR
  }

  def "wildcard prefix matching works"() {
    given:
    ImportRule rule = new ImportRule(['hubitat.*', 'java.util.Random'])

    expect:
    rule.isAllowed('hubitat.scheduling.AsyncResponse')
    rule.isAllowed('hubitat.matter.SomeNew')
    rule.isAllowed('java.util.Random')
    !rule.isAllowed('java.util.Date')
    !rule.isAllowed('com.example.Whatever')
  }

  def "star imports are checked against the allowlist"() {
    given:
    ParseResult pr = parse('''
      import java.util.concurrent.*

      library(name: 'X', namespace: 'dwinks', author: 'Daniel')
    '''.stripIndent())

    expect:
    ImportRule.fromResource().check(pr).empty
  }
}
