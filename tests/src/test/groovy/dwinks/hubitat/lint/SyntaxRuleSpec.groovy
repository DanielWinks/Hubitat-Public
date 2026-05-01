package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.SyntaxRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SyntaxRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body) {
    File f = tmp.resolve('T.groovy').toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "unbalanced braces are caught"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p')
      void busted() {
        if (true) {
      }
    '''.stripIndent())

    when:
    List<Violation> v = new SyntaxRule().check(pr)

    then:
    !v.empty
    v[0].rule == 'SyntaxError'
    v[0].severity == Severity.ERROR
  }

  def "valid script produces no syntax violations"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void hello() { return }
    '''.stripIndent())

    expect:
    new SyntaxRule().check(pr).empty
  }
}
