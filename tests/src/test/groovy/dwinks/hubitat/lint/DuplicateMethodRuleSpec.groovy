package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.DuplicateMethodRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DuplicateMethodRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body, String name = 'T.groovy') {
    File f = tmp.resolve(name).toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "two methods with the same signature in one file are flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void hello(String s) {}
      void hello(String s) {}
    '''.stripIndent())

    when:
    List<Violation> v = new DuplicateMethodRule().check(pr)

    then:
    v.any { it.message.contains("Duplicate method 'hello(String)'") }
  }

  def "two methods with different parameter types are NOT flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void hello(String s) {}
      void hello(Map m) {}
    '''.stripIndent())

    expect:
    new DuplicateMethodRule().check(pr).empty
  }

  def "method colliding with one from #include'd library is flagged"() {
    given:
    File lib = tmp.resolve('Lib.groovy').toFile()
    lib.text = '''
      library(name: 'TheLib', namespace: 'dwinks', author: 'X')
      void updated() { }
    '''.stripIndent()
    ParseResult libPr = HubitatScriptParser.parse(lib)

    File app = tmp.resolve('App.groovy').toFile()
    app.text = '''
      #include dwinks.TheLib
      definition(name: 'F', namespace: 'dwinks', author: 'X')
      preferences { page(name: 'p') }
      void updated() { }
    '''.stripIndent()
    ParseResult appPr = HubitatScriptParser.parse(app)

    ProjectIndex idx = new ProjectIndex()
    idx.register(libPr)

    when:
    List<Violation> v = new DuplicateMethodRule().check(appPr, idx)

    then:
    v.size() == 1
    v[0].message.contains("Duplicate method 'updated()'")
    v[0].message.contains('Lib.groovy')
  }

  def "the same library #include'd twice is flagged"() {
    given:
    ParseResult pr = parse('''
      #include dwinks.SomeLib
      #include dwinks.SomeLib
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
    '''.stripIndent())

    when:
    List<Violation> v = new DuplicateMethodRule().check(pr)

    then:
    v.any { it.message.contains("'dwinks.SomeLib' is #include'd more than once") }
  }
}
