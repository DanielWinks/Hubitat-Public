package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.DuplicateFieldRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DuplicateFieldRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body, String name = 'T.groovy') {
    File f = tmp.resolve(name).toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "two @Field declarations with the same name in one file are flagged"() {
    given:
    ParseResult pr = parse('''
      import groovy.transform.Field
      definition(name: 'F', namespace: 'dwinks', author: 'X')
      preferences { page(name: 'p') }
      @Field static final String FOO = 'a'
      @Field static final String FOO = 'b'
    '''.stripIndent())

    when:
    List<Violation> v = new DuplicateFieldRule().check(pr)

    then:
    v.any { it.message.contains("Duplicate @Field 'FOO'") }
  }

  def "field colliding with one from #include'd library is flagged"() {
    given:
    File lib = tmp.resolve('Lib.groovy').toFile()
    lib.text = '''
      import groovy.transform.Field
      library(name: 'TheLib', namespace: 'dwinks', author: 'X')
      @Field static final String SHARED = 'lib'
    '''.stripIndent()
    ParseResult libPr = HubitatScriptParser.parse(lib)

    File app = tmp.resolve('App.groovy').toFile()
    app.text = '''
      import groovy.transform.Field
      #include dwinks.TheLib
      definition(name: 'F', namespace: 'dwinks', author: 'X')
      preferences { page(name: 'p') }
      @Field static final String SHARED = 'app'
    '''.stripIndent()
    ParseResult appPr = HubitatScriptParser.parse(app)

    ProjectIndex idx = new ProjectIndex()
    idx.register(libPr)

    when:
    List<Violation> v = new DuplicateFieldRule().check(appPr, idx)

    then:
    v.size() == 1
    v[0].message.contains("Duplicate @Field 'SHARED'")
  }

  def "fields without @Field are not part of the merge surface"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'X')
      preferences { page(name: 'p') }
      class Helper { String FOO; String FOO }
    '''.stripIndent())

    expect:
    new DuplicateFieldRule().check(pr).empty
  }
}
