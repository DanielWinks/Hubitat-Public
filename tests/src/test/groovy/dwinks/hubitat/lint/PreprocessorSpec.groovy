package dwinks.hubitat.lint

import spock.lang.Specification

class PreprocessorSpec extends Specification {

  def "strips #include directive but keeps line numbering"() {
    given:
    String src = '''\
#include dwinks.UtilitiesAndLoggingLibrary

definition(name: 'X', namespace: 'dwinks', author: 'Foo')
'''
    when:
    Preprocessor.Result result = Preprocessor.process(src)

    then:
    result.includes == ['dwinks.UtilitiesAndLoggingLibrary']
    !result.source.contains('#include')
    // Line 3 (the definition) is still on line 3 in stripped source
    result.source.split('\n')[2].contains('definition')
  }

  def "captures multiple includes"() {
    given:
    String src = '''\
#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.SMAPILibrary

import groovy.transform.Field

library(name: 'X', namespace: 'dwinks', author: 'Foo')
'''
    when:
    Preprocessor.Result result = Preprocessor.process(src)

    then:
    result.includes == ['dwinks.UtilitiesAndLoggingLibrary', 'dwinks.SMAPILibrary']
  }

  def "ignores commented-out includes"() {
    given:
    String src = '''\
// #include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.RealLibrary
'''
    when:
    Preprocessor.Result result = Preprocessor.process(src)

    then:
    result.includes == ['dwinks.RealLibrary']
  }
}
