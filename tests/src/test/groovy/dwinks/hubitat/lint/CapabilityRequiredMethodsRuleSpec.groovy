package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.CapabilityRequiredMethodsRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CapabilityRequiredMethodsRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body, String name = 'T.groovy') {
    File f = tmp.resolve(name).toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "Switch capability without on()/off() is flagged"() {
    given:
    ParseResult pr = parse('''
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'X') {
          capability 'Switch'
        }
        preferences { input 'x', 'bool', title: '' }
      }
    '''.stripIndent())

    when:
    List<Violation> v = CapabilityRequiredMethodsRule.fromResource().check(pr)

    then:
    v.any { it.message.contains("'Switch' requires method 'on()'") }
    v.any { it.message.contains("'Switch' requires method 'off()'") }
  }

  def "Switch capability with on() and off() passes"() {
    given:
    ParseResult pr = parse('''
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'X') {
          capability 'Switch'
        }
        preferences { input 'x', 'bool', title: '' }
      }
      void on() { }
      void off() { }
    '''.stripIndent())

    expect:
    CapabilityRequiredMethodsRule.fromResource().check(pr).empty
  }

  def "marker capabilities (Sensor, Battery) require nothing"() {
    given:
    ParseResult pr = parse('''
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'X') {
          capability 'Sensor'
          capability 'Battery'
          capability 'TemperatureMeasurement'
        }
        preferences { input 'x', 'bool', title: '' }
      }
    '''.stripIndent())

    expect:
    CapabilityRequiredMethodsRule.fromResource().check(pr).empty
  }

  def "method provided by a #include'd library satisfies the requirement"() {
    given:
    File lib = tmp.resolve('Lib.groovy').toFile()
    lib.text = '''
      library(name: 'OnOff', namespace: 'dwinks', author: 'X')
      void on() { }
      void off() { }
    '''.stripIndent()
    ParseResult libPr = HubitatScriptParser.parse(lib)

    File drv = tmp.resolve('D.groovy').toFile()
    drv.text = '''
      #include dwinks.OnOff
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'X') {
          capability 'Switch'
        }
        preferences { input 'x', 'bool', title: '' }
      }
    '''.stripIndent()
    ParseResult drvPr = HubitatScriptParser.parse(drv)

    ProjectIndex idx = new ProjectIndex()
    idx.register(libPr)

    expect:
    CapabilityRequiredMethodsRule.fromResource().check(drvPr, idx).empty
  }
}
