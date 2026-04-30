package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.MetadataRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class MetadataRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body, String name = 'Test.groovy') {
    File f = tmp.resolve(name).toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "valid app passes metadata rule"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'Foo', namespace: 'dwinks', author: 'Daniel')
      preferences { page(name: 'main') }
    '''.stripIndent())

    expect:
    pr.kind == SourceKind.APP
    new MetadataRule().check(pr).empty
  }

  def "app missing definition is flagged"() {
    given:
    ParseResult pr = parse('''
      preferences { page(name: 'main') }
    '''.stripIndent())

    when:
    List<Violation> v = new MetadataRule().check(pr)

    then:
    pr.kind == SourceKind.UNKNOWN
    v.any { it.message.contains('definition()/metadata{}/library()') }
  }

  def "app missing preferences is flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'Foo', namespace: 'dwinks', author: 'Daniel')
    '''.stripIndent())

    when:
    List<Violation> v = new MetadataRule().check(pr)

    then:
    pr.kind == SourceKind.APP
    v.any { it.message.contains('preferences') }
  }

  def "definition missing required field is flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'Foo', namespace: 'dwinks')
      preferences { page(name: 'main') }
    '''.stripIndent())

    when:
    List<Violation> v = new MetadataRule().check(pr)

    then:
    v.any { it.message.contains("required field 'author'") }
  }

  def "valid driver passes metadata rule"() {
    given:
    ParseResult pr = parse('''
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'Daniel') {
          capability 'Switch'
        }
        preferences {
          input 'logEnable', 'bool', title: 'Logs'
        }
      }
    '''.stripIndent())

    expect:
    pr.kind == SourceKind.DRIVER
    new MetadataRule().check(pr).every { it.severity != Severity.ERROR }
  }

  def "valid library passes metadata rule"() {
    given:
    ParseResult pr = parse('''
      library(name: 'L', namespace: 'dwinks', author: 'Daniel')
      void hello() { }
    '''.stripIndent())

    expect:
    pr.kind == SourceKind.LIBRARY
    new MetadataRule().check(pr).every { it.severity != Severity.ERROR }
  }

  def "metadata block in app file is flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'Foo', namespace: 'dwinks', author: 'Daniel')
      preferences { page(name: 'main') }
      metadata {
        definition(name: 'X', namespace: 'dwinks', author: 'Daniel')
      }
    '''.stripIndent())

    when:
    List<Violation> v = new MetadataRule().check(pr)

    then:
    v.any { it.message.contains('belongs in Drivers') }
  }
}
