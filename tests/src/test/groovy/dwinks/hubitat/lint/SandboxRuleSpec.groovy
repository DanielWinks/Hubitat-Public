package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.SandboxRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SandboxRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body) {
    File f = tmp.resolve('T.groovy').toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  def "package declaration is forbidden"() {
    given:
    ParseResult pr = parse('''
      package com.example

      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
    '''.stripIndent())

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    v.any { it.message.contains("must not declare a 'package'") }
  }

  def "System.exit is flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void doIt() { System.exit(0) }
    '''.stripIndent())

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    v.any { it.message.contains("'System.exit") }
  }

  def "System.#method is flagged because the hub rejects it at save time"() {
    given:
    ParseResult pr = parse("""
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void doIt() { Object x = System.${call} }
    """.stripIndent())

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    v.any { it.message.contains("'System.${method}") }

    where:
    method       | call
    'nanoTime'   | 'nanoTime()'
    'getenv'     | "getenv('HOME')"
    'getProperty'| "getProperty('user.dir')"
  }

  def "fully qualified java.lang.System.nanoTime is flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void doIt() { Object x = java.lang.System.nanoTime() }
    '''.stripIndent())

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    v.any { it.message.contains("'System.nanoTime") }
  }

  def "now() and UUID are not flagged"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void doIt() { Long a = now(); String b = UUID.randomUUID().toString() }
    '''.stripIndent())

    expect:
    new SandboxRule().check(pr).isEmpty()
  }

  def "@Grab is forbidden"() {
    given:
    ParseResult pr = parse('''
      @Grab('org.apache.groovy:groovy:4.0.0')
      import groovy.transform.Field

      library(name: 'X', namespace: 'dwinks', author: 'Foo')
    '''.stripIndent())

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    v.any { it.message.contains('@Grab') }
  }

  def "new java.io.File is forbidden"() {
    given:
    ParseResult pr = parse('''
      definition(name: 'F', namespace: 'dwinks', author: 'Foo')
      preferences { page(name: 'p') }
      void readit() {
        def f = new java.io.File('/etc/passwd')
        f.text
      }
    '''.stripIndent())

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    v.any { it.message.contains("'new File") || it.message.contains('java.io.File') }
  }

  def "real NWS driver can be checked without crashing on map lookups"() {
    given:
    File driver = new File('../Drivers/weather/NWSForecast.groovy')
    assert driver.isFile()
    ParseResult pr = HubitatScriptParser.parse(driver)

    when:
    List<Violation> v = new SandboxRule().check(pr)

    then:
    !v.any { it.message.startsWith('Rule threw') }
  }
}
