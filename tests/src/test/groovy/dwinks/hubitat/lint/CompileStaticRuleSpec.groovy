package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.CompileStaticRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CompileStaticRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body) {
    File f = tmp.resolve('T.groovy').toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "untyped parameter in @CompileStatic method is flagged"() {
    given:
    ParseResult pr = parse('''
      import groovy.transform.CompileStatic
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      @CompileStatic
      void handler(evt) { }
    '''.stripIndent())

    when:
    List<Violation> v = new CompileStaticRule().check(pr)

    then:
    v.any { it.message.contains("parameter 'evt'") }
  }

  def "typed parameter in @CompileStatic method passes"() {
    given:
    ParseResult pr = parse('''
      import groovy.transform.CompileStatic
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      @CompileStatic
      void handler(String evt) { }
    '''.stripIndent())

    expect:
    new CompileStaticRule().check(pr)
      .findAll { it.message.contains("parameter 'evt'") }
      .empty
  }

  def "non-@CompileStatic methods are not checked"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void handler(evt) { }
    '''.stripIndent())

    expect:
    new CompileStaticRule().check(pr).empty
  }

  def "catch (e) in @CompileStatic method is flagged"() {
    given:
    ParseResult pr = parse('''
      import groovy.transform.CompileStatic
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      @CompileStatic
      void boom() {
        try { throw new RuntimeException() } catch (e) { }
      }
    '''.stripIndent())

    when:
    List<Violation> v = new CompileStaticRule().check(pr)

    then:
    v.any { it.message.contains("catches untyped 'e'") }
  }
}
