package dwinks.hubitat.lint

import dwinks.hubitat.lint.rules.ConcurrencyRule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class ConcurrencyRuleSpec extends Specification {

  @TempDir Path tmp

  ParseResult parse(String body, String name = 'T.groovy') {
    File f = tmp.resolve(name).toFile()
    f.text = body
    HubitatScriptParser.parse(f)
  }

  static final String APP_HEADER = '''
    import groovy.transform.Field
    definition(name: 'F', namespace: 'dwinks', author: 'Foo')
    preferences { page(name: 'p') }
  '''.stripIndent()

  def "a synchronized method modifier is flagged"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      synchronized void drain() { }
    '''.stripIndent())

    expect:
    new ConcurrencyRule().check(pr).any { it.message.contains('SynchronizedModifier') }
  }

  def "a synchronized block on a static lock is allowed"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final Object LOCK = new Object()
      void drain() { synchronized(LOCK) { } }
    '''.stripIndent())

    expect:
    new ConcurrencyRule().check(pr).empty
  }

  def "a child entry point that reaches a child write through helpers is flagged with its path"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final List<String> CHILD_ENTRY_POINTS = ['putThing']
      void putThing(String dni) { publish(dni) }
      void publish(String dni) { sendEvent(dni, [name: 'x', value: 1]) }
    '''.stripIndent())

    when:
    List<Violation> found = new ConcurrencyRule().check(pr)

    then:
    found.any { it.message.contains('ChildEntryPurity') && it.message.contains('putThing -> publish') }
  }

  def "child writes on a child wrapper are flagged but reads are not"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final List<String> CHILD_ENTRY_POINTS = ['readOnly', 'writes']
      void readOnly(String dni) { def player = getChildDevice(dni); player.getDataValue('id'); player.currentValue('volume') }
      void writes(String dni) { def player = getChildDevice(dni); player.playerPlay() }
    '''.stripIndent())

    when:
    List<Violation> found = new ConcurrencyRule().check(pr)

    then:
    found.size() == 1
    found[0].message.contains("'writes'")
  }

  def "a child write in a later overload is found even when a pure overload comes first"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final List<String> CHILD_ENTRY_POINTS = ['entry']
      void entry(String dni) { helper(dni, 1) }
      void helper(String dni) { getChildDevice(dni).getDataValue('id') }
      void helper(String dni, Integer level) { def player = getChildDevice(dni); player.playerPlay() }
    '''.stripIndent())

    when:
    List<Violation> found = new ConcurrencyRule().check(pr)

    then:
    found.size() == 1
    found[0].message.contains("'entry'")
  }

  def "a child write behind a runInMillis callback is not reachable from the entry point"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final List<String> CHILD_ENTRY_POINTS = ['putThing']
      void putThing(String dni) { runInMillis(1, 'publish', [data: [dni: dni], overwrite: false]) }
      void publish(Map data) { sendEvent(data.dni, [name: 'x', value: 1]) }
    '''.stripIndent())

    expect:
    new ConcurrencyRule().check(pr).empty
  }

  def "an unknown entry point name is an error"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final List<String> CHILD_ENTRY_POINTS = ['missing']
      void other() { }
    '''.stripIndent())

    expect:
    new ConcurrencyRule().check(pr).any { it.message.contains("'missing'") }
  }

  def "classified app data keys are never accessed through state"() {
    given:
    ParseResult pr = parse(APP_HEADER + '''
      @Field static final String QUEUE_KEY = 'queue'
      @Field static final String SHARED_KEY = 'shared'
      @Field static final String PLAIN_KEY = 'plain'
      @Field static final List<String> APP_MEMORY_KEYS = [QUEUE_KEY]
      @Field static final List<String> ATOMIC_MIGRATED_STATE_KEYS = [SHARED_KEY]
      void bad() { state[QUEUE_KEY] = 1; state.remove(SHARED_KEY); def x = state.queue }
      void good() { state[PLAIN_KEY] = 1; atomicState[SHARED_KEY] = 2 }
    '''.stripIndent())

    when:
    List<Violation> found = new ConcurrencyRule().check(pr)

    then:
    found.count { it.message.contains('AppDataAccess') } == 3
    found.every { it.line > 0 }
  }

  def "a driver hop whose only job is a parent call is flagged"() {
    given:
    ParseResult pr = parse('''
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'Foo') { capability 'Refresh'; command 'refresh' }
      }
      void refresh() { runInMillis(1, 'relay', [overwrite: false]) }
      void relay() {
        try { parent.putThing(1) } catch(Exception e) { logWarn('x') }
      }
      void logWarn(String m) { }
    '''.stripIndent())

    expect:
    new ConcurrencyRule().check(pr).any { it.message.contains('DriverParentHop') }
  }

  def "a driver callback that also reads device state is not a pure hop"() {
    given:
    ParseResult pr = parse('''
      metadata {
        definition(name: 'D', namespace: 'dwinks', author: 'Foo') { capability 'Refresh'; command 'refresh' }
      }
      void refresh() { runInMillis(1, 'relay', [overwrite: false]) }
      void relay() { Object v = device.currentValue('volume'); parent.putThing(v) }
    '''.stripIndent())

    expect:
    new ConcurrencyRule().check(pr).empty
  }

  def "the Sonos Advanced app and drivers satisfy every concurrency rule"() {
    expect:
    ['../Apps/SonosAdvancedApp.groovy', '../Drivers/Component/SonosAdvPlayer.groovy', '../Drivers/Component/SonosAdvGroup.groovy'].every { String path ->
      List<Violation> found = new ConcurrencyRule().check(HubitatScriptParser.parse(new File(path)))
      assert found.empty, found*.render().join('\n')
      true
    }
  }

  def "the Sonos Advanced app declares its entry points and is not single threaded"() {
    given:
    String app = new File('../Apps/SonosAdvancedApp.groovy').text
    ParseResult pr = HubitatScriptParser.parse(new File('../Apps/SonosAdvancedApp.groovy'))

    expect:
    ConcurrencyRule.listField(ConcurrencyRule.fieldInits(pr), 'CHILD_ENTRY_POINTS').size() >= 13
    app.contains('singleThreaded: false')
    !app.contains('singleThreaded: true')
  }

  def "the Sonos Advanced player and group drivers are not single threaded"() {
    expect:
    ['../Drivers/Component/SonosAdvPlayer.groovy', '../Drivers/Component/SonosAdvGroup.groovy'].every { String path ->
      String text = new File(path).text
      text.contains('singleThreaded: false') && !text.contains('singleThreaded: true')
    }
  }
}
