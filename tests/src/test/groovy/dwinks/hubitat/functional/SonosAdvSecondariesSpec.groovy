package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Specification

class SonosAdvSecondariesSpec extends Specification {
  HubitatScriptHarness driver
  List<Map> connections = []
  List<String> sentMessages = []
  List<String> sendAttempts = []
  Integer sendFailuresRemaining = 0

  def setup() {
    driver = ScriptLoader.load(new File('../Drivers/Component/SonosAdvSecondaries.groovy'))
    driver.binding.setVariable('interfaces', [webSocket: new Expando(
      connect: { Map options, String url -> connections.add([url: url, options: options]) },
      sendMessage: { String message ->
        sendAttempts << message
        if(sendFailuresRemaining > 0) {
          sendFailuresRemaining--
          throw new RuntimeException('simulated send failure')
        }
        sentMessages << message
      },
      close: { }
    )])
  }

  def "connect uses the child IP when discovery did not supply a websocket URL"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'

    when:
    driver.initialize()

    then:
    connections*.url == ['wss://192.168.1.51:1443/websocket/api']
    driver.device.getDataValue('websocketStatus') == 'connecting'

    when: 'discovery updates the existing child IP'
    driver.device.dataValues.deviceIp = '192.168.1.52'
    driver.renewWebsocketConnection()

    then:
    connections.last().url == 'wss://192.168.1.52:1443/websocket/api'

    when:
    driver.webSocketStatus('status: open')

    then:
    driver.scheduled.any { List job -> job[1] == 'renewWebsocketConnection' }
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

  def "open status notifies the parent player directly"() {
    given:
    List<List> parentStatuses = []
    Expando mockParent = new Expando()
    mockParent.rightChannelWebsocketStatusChanged = { String status, String dni ->
      parentStatuses << [status, dni]
    }
    driver.binding.setVariable('parent', mockParent)
    driver.device.deviceNetworkId = 'parent-right-channel'

    when:
    driver.webSocketStatus('status: open')

    then:
    parentStatuses == [['open', 'parent-right-channel']]
    driver.logs.any { it.toString().contains('Notified parent player of secondary WebSocket status') }
  }

  def "missing connection data does not open a socket or schedule renewal"() {
    when:
    driver.wsConnect()

    then:
    connections.empty
    driver.scheduled.empty
  }

  def "secondary sends while disconnected do not start a local message queue"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'
    driver.device.dataValues.websocketStatus = 'closed'

    when:
    Boolean firstSent = driver.sendWsMessage('first')
    Boolean secondSent = driver.sendWsMessage('second')

    then:
    !firstSent
    !secondSent
    sentMessages.empty
    driver.state.websocketOutboundQueue == null
    connections.size() == 1
    driver.state.websocketConnectPending == true

    when:
    driver.webSocketStatus('status: open')

    then:
    sentMessages.empty
    driver.device.getDataValue('websocketStatus') == 'open'
  }

  def "secondary sends while opening wait for the parent coordinated release"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'
    driver.device.dataValues.websocketStatus = 'connecting'
    driver.state.websocketConnectPending = true

    when:
    Boolean firstSent = driver.sendWsMessage('one')
    Boolean secondSent = driver.sendWsMessage('two')

    then:
    !firstSent
    !secondSent
    sentMessages.empty
    connections.empty
    driver.state.websocketOutboundQueue == null

    when:
    driver.webSocketStatus('status: open')

    then:
    sentMessages.empty
  }

  def "an open secondary sends directly and reports send failure without retaining a child queue"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'
    driver.device.dataValues.websocketStatus = 'open'
    sendFailuresRemaining = 1

    when:
    Boolean sent = driver.sendWsMessage('retry-me')

    then:
    !sent
    driver.state.websocketOutboundQueue == null
    driver.device.getDataValue('websocketStatus') == 'error'
    driver.scheduled.any { List job -> job[1] == 'retryWebsocketConnection' }
  }

  def "synchronized release completion logs a send without introducing a secondary queue"() {
    given:
    driver.device.dataValues.websocketStatus = 'open'

    when:
    driver.playerLoadAudioClipForSynchronizedRelease('paired-clip', 'corr-1')
    driver.completeSynchronizedAudioClipRelease('corr-1')

    then:
    sentMessages == ['paired-clip']
    driver.state.websocketOutboundQueue == null
    driver.state.synchronizedAudioClipReleases == [:]
    driver.logs.any { it.toString().contains('sent during synchronized release pass') }
  }

  def "paired release while disconnected requests connection but does not retain a child message"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'
    driver.device.dataValues.websocketStatus = 'closed'

    when:
    driver.playerLoadAudioClipForSynchronizedRelease('paired-clip', 'corr-2')
    driver.completeSynchronizedAudioClipRelease('corr-2')

    then:
    driver.state.websocketOutboundQueue == null
    connections.size() == 1
    driver.logs.any { it.toString().contains('Secondary socket was not open during paired release') }
  }

  def "secondary reconnect attempts stop after three failures"() {
    given:
    driver.device.dataValues.deviceIp = '192.168.1.51'
    driver.device.dataValues.websocketStatus = 'closed'

    when:
    driver.initializeWebsocketConnection()
    (1..3).each {
      driver.webSocketStatus('failure: connect timed out')
      driver.retryWebsocketConnection()
    }
    driver.webSocketStatus('failure: connect timed out')

    then:
    driver.device.getDataValue('websocketStatus') == 'connect timed out'
    driver.state.websocketReconnectAttempts == 3
    driver.scheduled.findAll { List job -> job[1] == 'retryWebsocketConnection' }.collect { List job -> job[0] } == [5, 15, 60]
    connections.size() == 4
  }
}
