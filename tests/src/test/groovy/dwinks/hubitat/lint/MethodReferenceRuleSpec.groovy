package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.MethodReferenceRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class MethodReferenceRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body, String name = 'T.groovy') {
    File f = tmp.resolve(name).toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "subscribe handler defined locally is OK"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void initialize() {
        subscribe(disableSwitches, 'switch', 'handler')
      }
      void handler(evt) { }
    '''.stripIndent())

    expect:
    new MethodReferenceRule().check(pr).empty
  }

  def "subscribe to undefined handler is flagged as ERROR when no includes"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void initialize() {
        subscribe(disableSwitches, 'switch', 'missingHandler')
      }
    '''.stripIndent())

    when:
    List<Violation> v = new MethodReferenceRule().check(pr)

    then:
    v.size() == 1
    v[0].message.contains('missingHandler')
    v[0].severity == Severity.ERROR
  }

  def "method from #include'd library is resolved via ProjectIndex"() {
    given:
    File libFile = tmp.resolve('Lib.groovy').toFile()
    libFile.text = '''
      library(name: 'TheLib', namespace: 'dwinks', author: 'Foo')
      void logsOff() { }
    '''.stripIndent()
    ParseResult libPr = HubitatScriptParser.parse(libFile)

    File appFile = tmp.resolve('App.groovy').toFile()
    appFile.text = '''
      #include dwinks.TheLib
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void installed() { runIn(1800, 'logsOff') }
    '''.stripIndent()
    ParseResult appPr = HubitatScriptParser.parse(appFile)

    ProjectIndex idx = new ProjectIndex()
    idx.register(libPr)

    expect:
    new MethodReferenceRule().check(appPr, idx).empty
  }

  def "mappings handler missing is flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      mappings {
        path('/x') { action: [GET: 'noSuchMethod'] }
      }
    '''.stripIndent())

    when:
    List<Violation> v = new MethodReferenceRule().check(pr)

    then:
    v.size() == 1
    v[0].message.contains('noSuchMethod')
  }
}
