package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvGroupSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvGroup.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file, null, true)
  }

  def setup() {
    driver.settings = [logEnable: true, debugLogEnable: true]
    driver.logs.clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    driver.events.clear()
    driver.state.clear()
    driver.device.deviceNetworkId = 'SONOS-GROUP-TEST-DNI'
    driver.device.dataValues.clear()
    driver.device.currentValues.clear()
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.playerIds = 'RINCON_FOLLOW'
  }

  def "group commands publish an asynchronous request instead of looking up the parent"() {
    when:
    driver.on()

    then:
    driver.scheduled.size() == 1
    driver.scheduled[0][0] == 1
    driver.scheduled[0][1] == 'emitGroupCommandRequest'
    driver.scheduled[0][2].data.payload.command == 'on'
    driver.scheduled[0][2].data.payload.groupDni == 'SONOS-GROUP-TEST-DNI'
  }

  def "group refresh is also deferred through the request boundary"() {
    when:
    driver.refresh()

    then:
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.command == 'refresh'
    driver.events.empty
  }
}
