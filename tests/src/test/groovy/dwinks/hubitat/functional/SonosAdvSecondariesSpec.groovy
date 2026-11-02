package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Specification

class SonosAdvSecondariesSpec extends Specification {
  HubitatScriptHarness driver
  List<Map> connections = []

  def setup() {
    driver = ScriptLoader.load(new File('../Drivers/Component/SonosAdvSecondaries.groovy'))
    driver.binding.setVariable('interfaces', [webSocket: new Expando(
      connect: { Map options, String url -> connections.add([url: url, options: options]) }
    )])
  }

  def "connect uses the child IP when discovery did not supply a websocket URL"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'

    when:
    driver.initialize()

    then:
    connections*.url == ['wss://192.168.1.51:1443/websocket/api']
    driver.scheduled.any { List job -> job[1] == 'renewWebsocketConnection' }

    when: 'discovery updates the existing child IP'
    driver.device.dataValues.deviceIp = '192.168.1.52'
    driver.renewWebsocketConnection()

    then:
    connections.last().url == 'wss://192.168.1.52:1443/websocket/api'
  }

  def "explicit websocket URL takes precedence over the IP fallback"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'
    driver.device.dataValues.websocketUrl = 'wss://192.168.1.60:1443/websocket/api'

    when:
    driver.wsConnect()

    then:
    connections*.url == ['wss://192.168.1.60:1443/websocket/api']
  }

  def "missing connection data does not open a socket or schedule renewal"() {
    when:
    driver.wsConnect()

    then:
    connections.empty
    driver.scheduled.empty
  }
}
