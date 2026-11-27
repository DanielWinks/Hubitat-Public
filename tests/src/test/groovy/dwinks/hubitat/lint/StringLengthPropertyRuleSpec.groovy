/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (danielwinks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.StringLengthPropertyRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class StringLengthPropertyRuleSpec extends Specification {

  @TempDir Path tmp

  List<Violation> lint(String body) {
    File f = tmp.resolve('T.groovy').toFile()
    f.text = body.stripIndent()
    new StringLengthPropertyRule().check(HubitatScriptParser.parse(f))
  }

  def 'substring of a literal with .length is flagged'() {
    when:
    List<Violation> v = lint('''
      void handler(String buttonName) {
        String key = buttonName.substring('newUiCreatePlayer|'.length)
      }
    ''')

    then:
    v.size() == 1
    v[0].severity == Severity.ERROR
    v[0].message.contains('length()')
  }

  def 'typed String variables and parameters are flagged'() {
    expect:
    lint('void a(String s) { Integer n = s.length }').size() == 1
    lint('void a() { String s = "x"; Integer n = s.length }').size() == 1
    lint('void a(Object o) { Integer n = o.toString().length }').size() == 1
    lint('void a(Object o) { Integer n = "v${o}".length }').size() == 1
  }

  def 'sibling block declarations do not leak between scopes'() {
    expect:
    lint('''
      void a(boolean flag) {
        if (flag) {
          String data = 'x'
        } else {
          byte[] data = new byte[2]
          Integer n = data.length
        }
      }
    ''').empty
    lint('''
      void a(boolean flag) {
        if (flag) {
          String data = 'x'
          Integer n = data.length
        } else {
          byte[] data = new byte[2]
        }
      }
    ''').size() == 1
    lint('void a(String s) { [1].each { byte[] s -> Integer n = s.length } }').empty
  }

  def 'method names alone are not evidence of a String'() {
    expect:
    lint('void a(byte[] bytes) { Integer n = bytes.reverse().length }').empty
    lint('void a(def thing) { Integer n = thing.trim().length }').empty
    lint('void a(String s) { Integer n = s.trim().length }').size() == 1
  }

  def 'length() calls, arrays and untyped receivers are not flagged'() {
    expect:
    lint('void a(String s) { Integer n = s.length() }').empty
    lint('void a(byte[] hash) { Integer n = hash.length }').empty
    lint('void a(def thing) { Integer n = thing.length }').empty
    lint('void a(List<String> items) { Integer n = items.size() }').empty
  }
}
