package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Specification

class ZoozZen72Spec extends Specification {

  HubitatScriptHarness driver

  def setup() {
    File file = new File('../Drivers/ZWave/ZoozZen72.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file)
    driver.state.clear()
    driver.settings.clear()
    driver.events.clear()
    driver.scheduled.clear()
    driver.hubCommands.clear()
    driver.updatedSettings.clear()
    driver.device.updatedSettings.clear()
    driver.device.dataValues.clear()
    driver.state.deviceModel = 'ZEN72'
    driver.device.dataValues.firmwareVersion = '3.10'
  }

  def "enableLocalControl enables paddle and Z-Wave load control with parameter 15 for #model"() {
    given:
    driver.state.deviceModel = model

    when:
    driver.enableLocalControl()

    then:
    driver.hubCommands.size() == 1
    driver.hubCommands.last().commands == [
      'secure:configurationSet:15:1:1',
      'secure:configurationGet:15'
    ]
    driver.hubCommands.last().protocol == hubitat.device.Protocol.ZWAVE
    driver.device.updatedSettings.configParam15 == '1'

    where:
    model << ['ZEN72', 'ZEN77']
  }

  def "disableLocalControl disables paddle while retaining Z-Wave load control for #model"() {
    given:
    driver.state.deviceModel = model

    when:
    driver.disableLocalControl()

    then:
    driver.hubCommands.size() == 1
    driver.hubCommands.last().commands == [
      'secure:configurationSet:15:1:0',
      'secure:configurationGet:15'
    ]
    driver.hubCommands.last().protocol == hubitat.device.Protocol.ZWAVE
    driver.device.updatedSettings.configParam15 == '0'

    where:
    model << ['ZEN72', 'ZEN77']
  }
}
