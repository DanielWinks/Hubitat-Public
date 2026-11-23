package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvSecondariesSpec extends Specification {
  @Shared Class<? extends HubitatScriptHarness> driverClass
  HubitatScriptHarness driver

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvSecondaries.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driverClass = ScriptLoader.compile(file)
  }

  def setup() {
    driver = ScriptLoader.instantiate(driverClass)
  }

  def "secondary initialization removes secondary websocket data without opening a socket"() {
    given:
    driver.device.dataValues = [id: 'RIGHT', deviceIp: '192.168.1.51', websocketUrl: 'wss://192.168.1.51:1443/websocket/api',
      websocketStatus: 'open', householdId: 'HOUSE']

    when:
    driver.initialize()

    then:
    driver.device.dataValues == [id: 'RIGHT', deviceIp: '192.168.1.51', householdId: 'HOUSE']
    driver.state.isEmpty()
    driver.events.isEmpty()
    driver.scheduled.isEmpty()
  }

  def "info logging helper accepts GString messages"() {
    given:
    Integer retryCount = 3

    when:
    driver.logInfo("Requesting secondary status; retryCount=${retryCount}")

    then:
    driver.logs.any { String entry -> entry.contains('INFO') && entry.contains('retryCount=3') }
  }
}
