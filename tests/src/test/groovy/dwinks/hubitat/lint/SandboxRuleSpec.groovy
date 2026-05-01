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
}
