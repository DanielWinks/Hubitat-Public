package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import hubitat.scheduling.AsyncResponse
import spock.lang.Shared
import spock.lang.Specification

class ESPHomeRATGDOGarageDoorSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File driverFile = new File('../Drivers/HTTP/ESPHomeRATGDOGarageDoor.groovy')
    assert driverFile.exists(), "Could not find ${driverFile.absolutePath}"
    driver = ScriptLoader.load(driverFile, new File('../Libraries'))
  }

  def setup() {
    driver.settings.clear()
    driver.events.clear()
    driver.scheduled.clear()
    driver.asyncCalls.clear()
    driver.children.clear()
    driver.childEvents.clear()
    driver.device.currentValues.clear()
    driver.settings.ip = '192.168.1.214'
    driver.settings.port = 80
    driver.settings.coverEntityId = 'door'
    driver.settings.lightEntityId = 'light'
    driver.settings.remoteLockEntityId = 'remotes'
  }

  def "cover commands use ESPHome's direct RATGDO REST endpoints"() {
    when:
    driver.open()
    driver.close()
    driver.stop()
    driver.lock()
    driver.unlock()

    then:
    driver.asyncCalls == [
      [verb: 'POST', cb: 'commandCallback', params: [uri: 'http://192.168.1.214:80/cover/door/open'], data: [action: 'open']],
      [verb: 'POST', cb: 'commandCallback', params: [uri: 'http://192.168.1.214:80/cover/door/close'], data: [action: 'close']],
      [verb: 'POST', cb: 'commandCallback', params: [uri: 'http://192.168.1.214:80/cover/door/stop'], data: [action: 'stop']],
      [verb: 'POST', cb: 'commandCallback', params: [uri: 'http://192.168.1.214:80/lock/remotes/lock'], data: [action: 'lock']],
      [verb: 'POST', cb: 'commandCallback', params: [uri: 'http://192.168.1.214:80/lock/remotes/unlock'], data: [action: 'unlock']]
    ]
    driver.scheduled.empty
  }

  def "RATGDO POST payloads map to Hubitat attributes"() {
    when:
    driver.parse('{"id":"cover-door","value":true}')

    then:
    driver.events.find { Map event -> event.name == 'door' }?.value == 'open'
    driver.events.find { Map event -> event.name == 'position' }?.value == BigDecimal.ONE

    when:
    driver.events.clear()
    driver.parse('{"id":"cover-door","value":"opening"}')

    then:
    driver.events.find { Map event -> event.name == 'door' }?.value == 'opening'
    !driver.events.any { Map event -> event.name == 'position' }

    when:
    driver.events.clear()
    driver.parse('{"id":"cover-door","value":"closing"}')

    then:
    driver.events.find { Map event -> event.name == 'door' }?.value == 'closing'
    !driver.events.any { Map event -> event.name == 'position' }

    when:
    driver.events.clear()
    driver.parse('{"id":"light-light","value":false}')
    driver.parse('{"id":"lock-remotes","value":true}')
    driver.parse('{"id":"binary_sensor-motion","value":true}')
    driver.parse('{"id":"binary_sensor-obstruction","value":false}')

    then:
    driver.events.find { Map event -> event.name == 'switch' }?.value == 'off'
    driver.events.find { Map event -> event.name == 'lock' }?.value == 'locked'
    driver.events.find { Map event -> event.name == 'motion' }?.value == 'active'
    driver.events.find { Map event -> event.name == 'obstruction' }?.value == 'clear'
  }

  def "cover refresh payloads preserve position and movement state"() {
    when: 'the door is partially open and idle'
    driver.parse('{"id":"cover/door","state":"OPEN","value":0.4,"current_operation":"IDLE"}')

    then:
    driver.device.currentValue('position') == 0.4G
    driver.device.currentValue('door') == 'partially open'

    when: 'the door is moving while partially open'
    driver.parse('{"id":"cover/door","state":"OPEN","value":0.55,"current_operation":"OPENING"}')

    then:
    driver.device.currentValue('position') == 0.55G
    driver.device.currentValue('door') == 'opening'

    when: 'the door is moving closed while partially open'
    driver.parse('{"id":"cover/door","state":"OPEN","value":0.3,"current_operation":"CLOSING"}')

    then:
    driver.device.currentValue('position') == 0.3G
    driver.device.currentValue('door') == 'closing'

    when: 'the door is fully open'
    driver.parse('{"id":"cover/door","state":"OPEN","value":1.0,"current_operation":"IDLE"}')

    then:
    driver.device.currentValue('position') == 1.0G
    driver.device.currentValue('door') == 'open'

    when: 'the door is fully closed'
    driver.parse('{"id":"cover/door","state":"CLOSED","value":0.0,"current_operation":"IDLE"}')

    then:
    driver.device.currentValue('position') == 0.0G
    driver.device.currentValue('door') == 'closed'
  }

  def "BLE device-tracker POSTs create and update presence children"() {
    when: 'the tracker first reports home'
    driver.parse('{"id":"device-tracker-daniel_iphone","value":true}')

    then:
    driver.children.size() == 1
    driver.children[0].displayName == 'Test Device - daniel_iphone'
    driver.childEvents == [[device: driver.children[0], name: 'presence', value: 'present',
                            descriptionText: 'daniel_iphone is home']]

    when: 'the same tracker reports away'
    driver.parse('{"id":"device-tracker-daniel_iphone","value":false}')

    then:
    driver.children.size() == 1
    driver.childEvents.last() == [device: driver.children[0], name: 'presence', value: 'not present',
                                  descriptionText: 'daniel_iphone is away']
  }
}
