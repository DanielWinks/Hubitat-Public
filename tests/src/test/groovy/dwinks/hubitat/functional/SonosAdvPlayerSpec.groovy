package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.ByteArrayInputStream
import java.time.Instant
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvPlayerSpec extends Specification {

  @Shared HubitatScriptHarness driver
  @Shared List<String> websocketMessages = []
  @Shared List<Map> rawConnections = []
  @Shared List<String> rawRequests = []
  @Shared Object rawSocket

  def "delayed stream callback cannot supersede a newer transport command"() {
    given:
    driver.asyncCalls.clear()
    driver.setAVTransportURIAndPlay('http://example.test/first.mp3')
    Map data = driver.asyncCalls.last().data
    String originalGuid = data.streamTransportIntentGuid

    when:
    driver.enqueueTransportCommand(command)
    driver.setAVTransportURIAndPlayCallback(new hubitat.scheduling.AsyncResponse(status: 200), data)

    then:
    originalGuid != driver.runtimeState.streamTransportIntentGuid
    driver.runtimeState.transportCommandQueue*.command == [command]
    driver.logs.any { it.toString().contains("Ignoring superseded stream load callback guid=${originalGuid}") }

    where:
    command << ['pause', 'stop', 'play', 'skipToNextTrack', 'skipToPreviousTrack']
  }

  def "external transport replacement reports correlated interruption of active group announcement"() {
    given:
    driver.device.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    driver.device.currentValues.audioSource = 'http://example.test/announcement.mp3'
    driver.runtimeState.groupAnnouncementStream = [correlationId: 'announcement-guid',
      phase: 'ACTIVE', activeObserved: true]

    when:
    driver.setAVTransportURIAndPlay('https://music.example.test/track.mp3')
    Map request = driver.asyncCalls.last().data
    driver.setAVTransportURIAndPlayCallback(new hubitat.scheduling.AsyncResponse(status: 200), request)

    then:
    driver.locationEvents.any { Map event ->
      event.data?.kind == 'groupAnnouncementProgress' &&
        event.data?.correlationId == 'announcement-guid' && event.data?.phase == 'INTERRUPTED' &&
        event.data?.externalPlayback == true &&
        event.data?.replacementUri == 'https://music.example.test/track.mp3'
    }
    driver.runtimeState.groupAnnouncementStream == null
  }

  def "paused coordinator stream completes correlated group announcement immediately"() {
    given:
    driver.locationEvents.clear()
    driver.device.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    driver.device.currentValues.audioSource = 'http://example.test/announcement.mp3'
    driver.runtimeState.groupAnnouncementStream = [correlationId: 'announcement-paused',
      phase: 'ACTIVE', activeObserved: true]

    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playback', type: 'playbackStatus'],
      [playbackState: 'PLAYBACK_STATE_PAUSED', itemId: 'announcement-item']
    ]))

    then:
    driver.locationEvents.any { Map event ->
      event.data?.kind == 'groupAnnouncementProgress' &&
        event.data?.correlationId == 'announcement-paused' && event.data?.phase == 'DONE' &&
        event.data?.playbackState == 'PLAYBACK_STATE_PAUSED' && event.data?.termination == 'paused'
    }
    driver.runtimeState.groupAnnouncementStream == null
  }

  def "playback status websocket events publish internal freshness state even when repeated"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.pendingLocalDeviceEvents.remove(dni)
    driver.state.playbackState = 'PLAYBACK_STATE_IDLE'

    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playback', type: 'playbackStatus'],
      [playbackState: 'PLAYBACK_STATE_IDLE', itemId: 'hdmi-input']
    ]))

    then:
    driver.state.playbackState == 'PLAYBACK_STATE_IDLE'
    (driver.state.playbackStateObservedAt as Long) > 0L
    !driver.pendingLocalDeviceEvents[dni].containsKey('playbackState')
    !driver.pendingLocalDeviceEvents[dni].containsKey('playbackStateObservedAt')
    driver.locationEvents.any { Map event ->
      event.data?.kind == 'playbackObservation' && event.data?.playbackState == 'PLAYBACK_STATE_IDLE' &&
        event.data?.playbackObservedAt == driver.state.playbackStateObservedAt
    }
  }

  def "HDMI metadata reports no signal separately from the playback state"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.pendingLocalDeviceEvents.remove(dni)

    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playbackMetadata', type: 'metadataStatus'],
      [container: [type: 'linein.homeTheater.hdmi', htInputFormat: [streamDescription: 'No Signal']]]
    ]))

    then:
    driver.state.audioSourceSignalStatus == 'no-signal'
    (driver.state.audioSourceSignalObservedAt as Long) > 0L
    !driver.pendingLocalDeviceEvents[dni].containsKey('audioSourceSignalStatus')
    !driver.pendingLocalDeviceEvents[dni].containsKey('audioSourceSignalObservedAt')
    driver.locationEvents.any { Map event ->
      event.data?.kind == 'playbackObservation' && event.data?.signalStatus == 'no-signal'
    }
  }

  def "empty metadata status preserves the last valid HDMI signal observation"() {
    given:
    driver.state.audioSourceSignalStatus = 'no-signal'
    Long observedAt = driver.now() - 1000L
    driver.state.audioSourceSignalObservedAt = observedAt

    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playbackMetadata', type: 'metadataStatus'],
      [container: [:]]
    ]))

    then:
    driver.state.audioSourceSignalStatus == 'no-signal'
    driver.state.audioSourceSignalObservedAt == observedAt
  }

  def "only the latest stream callback plays and duplicate callbacks are ignored"() {
    given:
    driver.asyncCalls.clear()
    driver.setAVTransportURIAndPlay('http://example.test/first.mp3')
    Map first = driver.asyncCalls.last().data
    driver.setAVTransportURIAndPlay('http://example.test/second.mp3')
    Map second = driver.asyncCalls.last().data
    hubitat.scheduling.AsyncResponse response = new hubitat.scheduling.AsyncResponse(status: 200)

    when:
    driver.setAVTransportURIAndPlayCallback(response, first)

    then:
    first.streamTransportIntentGuid != second.streamTransportIntentGuid
    driver.runtimeState.transportCommandQueue == null

    when:
    driver.setAVTransportURIAndPlayCallback(response, second)
    driver.setAVTransportURIAndPlayCallback(response, second)

    then:
    driver.runtimeState.transportCommandQueue*.command == ['play']
  }

  def "set-only media request supersedes pending stream autoplay"() {
    given:
    driver.asyncCalls.clear()
    driver.setAVTransportURIAndPlay('http://example.test/first.mp3')
    Map data = driver.asyncCalls.last().data

    when:
    driver.setAVTransportURI('http://example.test/second.mp3')
    driver.setAVTransportURIAndPlayCallback(new hubitat.scheduling.AsyncResponse(status: 200), data)

    then:
    driver.runtimeState.transportCommandQueue == null
  }

  def "transport command arriving during HTTP diagnostics supersedes stream autoplay"() {
    given:
    driver.setAVTransportURIAndPlay('http://example.test/first.mp3')
    Map data = driver.asyncCalls.last().data
    driver.metaClass.localControlCallback = { hubitat.scheduling.AsyncResponse response, Map callbackData ->
      driver.enqueueTransportCommand('pause')
    }

    when:
    driver.setAVTransportURIAndPlayCallback(new hubitat.scheduling.AsyncResponse(status: 200), data)

    then:
    driver.runtimeState.transportCommandQueue*.command == ['pause']
  }

  def "integer and decimal balance commands send identical stereo SOAP requests"() {
    given:
    driver.device.dataValues.rightChannelId = 'RINCON_RIGHT'
    driver.device.dataValues.secondaryIds = 'RINCON_RIGHT'
    List<Map> requests = []
    driver.metaClass.postSonosSoapAsync = { String callback, Map params -> requests.add(params) }

    when:
    driver.setBalance(level)

    then:
    requests.size() == 2
    requests[0].body.contains('<Channel>' + firstChannel + '</Channel>')
    ((requests[0].body =~ /<DesiredVolume>([^<]+)<\/DesiredVolume>/)[0][1] as BigDecimal) == firstVolume
    requests[1].body.contains('<DesiredVolume>100</DesiredVolume>')
    driver.events.findAll { Map event -> event.name == 'balance' }.empty

    where:
    level | firstChannel | firstVolume
    10    | 'LF'         | 50
    10.0G | 'LF'         | 50
    -10   | 'RF'         | 50
    -10.0G| 'RF'         | 50
    0     | 'LF'         | 100
    0.0G  | 'LF'         | 100
  }

  def "balance state updates deduplicate events without sending a speaker command"() {
    given:
    List<Map> requests = []
    driver.metaClass.postSonosSoapAsync = { String callback, Map params -> requests.add(params) }

    when:
    driver.setBalanceState(10)
    driver.device.currentValues.balance = 10
    driver.setBalanceState(10)

    then:
    requests.empty
    driver.events.findAll { Map event -> event.name == 'balance' }*.value == [10]
  }

  def "mute child commands send player mute requests"() {
    given:
    com.hubitat.app.DeviceWrapper child = new com.hubitat.app.DeviceWrapper(dataValues: [command: 'Mute'])

    when:
    driver.componentOn(child)
    driver.componentOff(child)

    then:
    List messages = websocketMessages.collect { String message -> new JsonSlurper().parseText(message) }
    messages.collect { it[0].command } == ['setMute', 'setMute']
    messages.collect { it[1].muted } == [true, false]
  }

  def "mute child refresh reports valid switch states"() {
    given:
    dwinks.hubitat.stubs.MockDevice child = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getMuteControlChildDNI(), dataValues: [command: 'Mute'])
    driver.children.clear()
    driver.children.add(child)
    driver.device.currentValues.mute = muteState

    when:
    driver.componentRefresh(child)

    then:
    child.currentValue('switch') == switchState

    cleanup:
    driver.children.clear()

    where:
    muteState | switchState
    'muted'   | 'on'
    'unmuted' | 'off'
    null      | 'off'
  }

  def "player mute reports synchronize the child even when parent state is unchanged"() {
    given:
    dwinks.hubitat.stubs.MockDevice child = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getMuteControlChildDNI())
    driver.children.clear()
    driver.children.add(child)
    driver.settings.enableAirPlayUnmuteVolumeFix = false
    driver.device.currentValues.mute = previousState
    child.currentValues.switch = 'stale'

    when:
    driver.setMuteState(muteState)

    then:
    child.currentValue('switch') == switchState
    driver.events.findAll { Map event -> event.name == 'mute' }*.value ==
      (previousState == muteState ? [] : [muteState])

    cleanup:
    driver.children.clear()

    where:
    previousState | muteState | switchState
    'unmuted' | 'muted'   | 'on'
    'muted'   | 'unmuted' | 'off'
    'muted'   | 'muted'   | 'on'
    'unmuted' | 'unmuted' | 'off'
  }

  def "stereo companion selection supports either front channel and excludes surrounds"() {
    expect:
    driver.selectStereoCompanionId(members, ownId) == companionId

    where:
    members | ownId | companionId
    [[id: 'left', channelMap: ['LF']], [id: 'right', channelMap: ['RF']]] | 'left' | 'right'
    [[id: 'right', channelMap: ['RF']], [id: 'left', channelMap: ['LF']]] | 'right' | 'left'
    [[id: 'bar', channelMap: ['LF', 'RF']], [id: 'rear', channelMap: ['LR']]] | 'bar' | null
    [[id: 'solo', channelMap: []]] | 'solo' | null
    null | 'solo' | null
  }

  def "legacy self-referencing stereo ID resolves to companion IP"() {
    given:
    driver.device.dataValues.secondaryIds = 'OTHER'
    driver.device.dataValues.secondaryDeviceIps = '192.168.1.51'

    when:
    driver.setRightChannelRincon('RINCON_TEST')

    then:
    driver.getRightChannelRincon() == 'OTHER'
    driver.getRightChannelDeviceIp() == '192.168.1.51'

    when: 'discovery no longer has a matching companion'
    driver.setRightChannelRincon('MISSING')

    then:
    driver.getRightChannelRincon() == null
    driver.getRightChannelDeviceIp() == null
  }

  def "saving preferences and installation invoke initialization"() {
    given:
    HubitatScriptHarness lifecycleDriver = ScriptLoader.load(
      new File('../Drivers/Component/SonosAdvPlayer.groovy'), new File('../Libraries'), true)
    Integer initialized = 0
    lifecycleDriver.metaClass.initialize = { -> initialized++ }

    when:
    lifecycleDriver.installed()
    lifecycleDriver.updated()

    then:
    initialized == 2
  }

  def "stereo child creation and repair populate connection data and initialize once"() {
    given:
    HubitatScriptHarness childDriver = ScriptLoader.load(
      new File('../Drivers/Component/SonosAdvPlayer.groovy'), new File('../Libraries'), true)
    childDriver.device.dataValues = [id: 'MAIN', rightChannelId: 'MAIN',
      secondaryIds: 'OTHER', secondaryDeviceIps: '192.168.1.51']
    dwinks.hubitat.stubs.MockDevice child = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: childDriver.getRightChannelChildDNI())
    Integer initialized = 0
    Integer created = 0
    child.metaClass.initialize = { -> initialized++ }
    childDriver.metaClass.addChildDevice = { String namespace, String type, String dni, Map props ->
      created++
      childDriver.children.add(child)
      return child
    }

    when:
    childDriver.createRemoveRightChannelChildDevice(true)
    childDriver.createRemoveRightChannelChildDevice(true)

    then:
    created == 1
    initialized == 1
    child.dataValues == [deviceIp: '192.168.1.51', id: 'OTHER', websocketUrl: 'wss://192.168.1.51:1443/websocket/api']

    when: 'an existing child needs its endpoint repaired'
    childDriver.device.dataValues.secondaryDeviceIps = '192.168.1.52'
    childDriver.createRemoveRightChannelChildDevice(true)

    then:
    initialized == 2
    child.getDataValue('deviceIp') == '192.168.1.52'
    child.getDataValue('websocketUrl') == 'wss://192.168.1.52:1443/websocket/api'

    when:
    childDriver.createRemoveRightChannelChildDevice(false)

    then:
    childDriver.children.empty
  }

  def "incomplete stereo discovery waits without creating a child or clearing preference"() {
    given:
    driver.settings.createRightChannelChildDevice = true
    driver.children.clear()

    when:
    driver.createRemoveRightChannelChildDevice(true)

    then:
    driver.children.empty
    !driver.device.updatedSettings.containsKey('createRightChannelChildDevice')
  }

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvPlayer.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file, new File('../Libraries'), true)
    driver.binding.setVariable('interfaces', [
      rawSocket: new Expando(
        connect: { Map options, String host, Integer port ->
          Closure inputReader = options.inputReader as Closure
          options.inputReader = { Object data ->
            byte[] bytes = data == null ? new byte[0] : data.toString().getBytes('UTF-8')
            inputReader.call(new ByteArrayInputStream(bytes), { String message -> driver.parse(message) })
          }
          rawConnections << [options: options, host: host, port: port]
        },
        sendMessage: { String message -> rawRequests << message },
        close: { -> }
      ),
      webSocket: new Expando(
        sendMessage: { String message -> websocketMessages << message },
        connect: { Object... ignored -> },
        close: { -> }
      )
    ])
    rawSocket = driver.binding.getVariable('interfaces').rawSocket
  }

  def setup() {
    driver.binding.getVariable('interfaces').rawSocket = rawSocket
    driver.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: true]
    driver.logs.clear()
    driver.asyncCalls.clear()
    driver.events.clear()
    driver.locationEvents.clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    driver.runtimeState.clear()
    driver.state.clear()
    driver.binding.setVariable('atomicState', [:])
    driver.device.dataValues.clear()
    driver.device.currentValues.clear()
    driver.device.events.clear()
    driver.device.dataValues.id = 'RINCON_TEST'
    driver.device.dataValues.localUpnpHost = '192.168.1.50:1400'
    driver.location.metaClass.getHub = { -> new Expando(localIP: '192.168.1.4', localSrvPortTCP: 39501) }
    driver.upnpRequestQueues.clear()
    driver.device.dataValues.groupId = 'GROUP_TEST'
    driver.device.dataValues.websocketStatus = 'open'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.clearFavoritesMap()
    driver.clearPlaylistsMap()
    driver.favoriteRetryState.clear()
    driver.playlistRetryState.clear()
    driver.pendingGroupedPlayerVolumes.clear()
    driver.groupDeviceUpdateRetryAttempts.clear()
    driver.pendingGroupDeviceUpdates.clear()
    driver.pendingTopologyXmlByDni.clear()
    driver.pendingTopologyWebsocketByDni.clear()
    driver.pendingTopologyKindByDni.clear()
    driver.lastPlaybackState.clear()
    driver.lastMetadataContainerId.clear()
  }

  def cleanup() {
    GroovySystem.metaClassRegistry.removeMetaClass(driver.getClass())
    driver.metaClass = null
  }

  def "retargeting a fade to the reported volume cancels queued steps"() {
    given:
    driver.volumeFadeState.clear()
    driver.groupVolumeFadeState.clear()
    driver.lastVolumeFadeCallTime.clear()
    driver.lastGroupVolumeFadeCallTime.clear()
    driver.device.currentValues.volume = 5
    driver.metaClass.now = { -> 10000L }
    driver."${command}"(15G, 20G)
    driver."${step}"()
    driver.device.currentValues.volume = 6
    driver.metaClass.now = { -> 14000L }
    driver.unschedules.clear()
    websocketMessages.clear()
    driver.scheduled.clear()

    when:
    driver."${command}"(6G, 10G)
    driver."${step}"()

    then:
    driver."${fadeMap}".isEmpty()
    driver.unschedules.contains(step)
    websocketMessages.isEmpty()
    driver.scheduled.isEmpty()

    where:
    command          | step                  | fadeMap
    'setLevel'       | 'volumeFadeStep'      | 'volumeFadeState'
    'setGroupVolume' | 'groupVolumeFadeStep' | 'groupVolumeFadeState'
  }

  def "raw UPnP subscribe formats request and records SID expiry and renewal"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')

    then:
    rawConnections.size() == 1
    rawConnections[0].host == '192.168.1.50'
    rawRequests[0].contains('HOST: 192.168.1.50:1400')
    rawConnections[0].port == 1400
    rawRequests[0].startsWith('SUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1\r\n')
    rawRequests[0].contains('CALLBACK: <http://192.168.1.4:39501/avt>')
    rawRequests[0].contains('NT: upnp:event')
    rawRequests[0].contains('TIMEOUT: Second-3600')
    rawRequests[0].contains('Connection: close')

    when:
    rawConnections[0].options.inputReader.call('HTTP/1.1 200 OK\r\nSID: uuid:test-subscription\r\nTIMEOUT: Second-1800\r\n\r\n')

    then:
    driver.getSid('sid1') == 'uuid:test-subscription'
    driver.device.dataValues['sid1-expires'] != null
    driver.scheduled.any { List row -> row[1] == 'resubscribeToAVTransport' }
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active == null
  }

  def "renewal uses captured SID and unsubscribe preserves a replacement SID"() {
    given:
    driver.setSid('sid1', 'uuid:old')
    driver.eventTimestamps.put('SONOS-TEST-DNI-sid1-expires', Instant.now().getEpochSecond() + 7200L)

    when:
    driver.upnpResubscribeGeneric('renewAVTransportSubscription', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')

    then:
    rawRequests[0].startsWith('SUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1')
    rawRequests[0].contains('SID: uuid:old')
    !rawRequests[0].contains('CALLBACK:')

    when:
    driver.upnpUnsubscribeGeneric('unsubscribeFromAVTransport', 'sid1', 'renewAVTransportSubscription', '/MediaRenderer/AVTransport/Event', 'callback')

    then:
    rawConnections.size() == 1
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').queue.size() == 1

    when:
    rawConnections[0].options.inputReader.call('HTTP/1.1 200 OK\r\nSID: uuid:new\r\nTIMEOUT: Second-3600\r\n\r\n')

    then:
    rawRequests.size() == 2
    rawRequests[1].startsWith('UNSUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1')
    rawRequests[1].contains('SID: uuid:new')

    when:
    driver.setSid('sid1', 'uuid:replacement')
    rawConnections[1].options.inputReader.call('HTTP/1.1 200 OK\r\n\r\n')

    then:
    driver.getSid('sid1') == 'uuid:replacement'
  }

  def "stale response and watchdog cannot complete the next request"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    Map firstQueue = driver.upnpRequestQueues.get('SONOS-TEST-DNI')
    String firstGuid = firstQueue.active.guid
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid2', '/mrc', 'callback', '/MediaRenderer/RenderingControl/Event')
    driver.upnpSubscriptionWatchdog([guid: firstGuid])

    then:
    rawConnections.size() == 2
    firstQueue.active.sidKey == 'sid2'

    when:
    rawConnections[0].options.inputReader.call('HTTP/1.1 200 OK\r\nSID: uuid:stale\r\n\r\n')
    driver.socketStatus('receive error after request B started')
    driver.upnpSubscriptionWatchdog([guid: firstGuid])

    then:
    firstQueue.active.sidKey == 'sid2'
    driver.getSid('sid1') == null
    driver.getSid('sid2') == null
  }

  def "missing SID and HTTP failures retry only within the configured bound"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    rawConnections[0].options.inputReader.call('HTTP/1.1 200 OK\r\nTIMEOUT: Second-3600\r\n\r\n')

    then:
    driver.getSid('sid1') == null
    driver.scheduled.any { List row -> row[1] == 'retryUpnpSid1' }

    when:
    Map retryOne = driver.scheduled.find { List row -> row[1] == 'retryUpnpSid1' }[2].data as Map
    driver.retryUpnpSid1(retryOne)
    rawConnections[1].options.inputReader.call('HTTP/1.1 500 Failure\r\n\r\n')
    Map retryTwo = driver.scheduled.findAll { List row -> row[1] == 'retryUpnpSid1' }.last()[2].data as Map
    driver.retryUpnpSid1(retryTwo)
    rawConnections[2].options.inputReader.call('HTTP/1.1 500 Failure\r\n\r\n')

    then:
    rawRequests.size() == 3
    driver.scheduled.count { List row -> row[1] == 'retryUpnpSid1' } == 2
    driver.logs.any { String line -> line.contains('retry limit reached') }
  }

  def "scheduled retry callback accepts Hubitat LazyMap payloads"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    rawConnections[0].options.inputReader.call('HTTP/1.1 500 Failure\r\n\r\n')
    Map scheduledRetry = driver.scheduled.find { List row -> row[1] == 'retryUpnpSid1' }[2].data as Map
    Map hubitatData = new JsonSlurper().parseText(JsonOutput.toJson(scheduledRetry)) as Map
    driver.retryUpnpSid1(hubitatData)

    then:
    rawRequests.size() == 2
    rawRequests[1].startsWith('SUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1')
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active != null
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.attempts == 2
  }

  def "oversized and malformed headers fail without recording a SID"() {
    given:
    String oversizedGuid = null

    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    oversizedGuid = driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.guid
    rawConnections[0].options.inputReader.call('X' * 17000)

    then:
    driver.getSid('sid1') == null
    driver.scheduled.any { List row -> row[1] == 'retryUpnpSid1' }

    when:
    driver.upnpSubscriptionWatchdog([guid: oversizedGuid])
    driver.retryUpnpSid1(driver.scheduled.find { List row -> row[1] == 'retryUpnpSid1' }[2].data as Map)
    rawConnections[1].options.inputReader.call('malformed status\r\n\r\n')

    then:
    driver.getSid('sid1') == null
    driver.scheduled.any { List row -> row[1] == 'retryUpnpSid1' }
  }

  def "EOF and connection errors are failures and never create a SID"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    rawConnections[0].options.inputReader.call(null)

    then:
    driver.getSid('sid1') == null
    driver.scheduled.any { List row -> row[1] == 'retryUpnpSid1' }

    when:
    driver.binding.getVariable('interfaces').rawSocket = new Expando(
      connect: { Map options, String host, Integer port -> throw new IOException('refused') },
      sendMessage: { String message -> rawRequests << message },
      close: { -> }
    )
    driver.retryUpnpSid1(driver.scheduled.findAll { List row -> row[1] == 'retryUpnpSid1' }.last()[2].data as Map)

    then:
    driver.getSid('sid1') == null
    rawRequests.size() == 1
    driver.scheduled.count { List row -> row[1] == 'retryUpnpSid1' } == 2
  }

  def "reinitialization rejects callbacks from the previous queue generation"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    String priorGuid = driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.guid
    driver.invalidateUpnpRequestsForDevice()
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid2', '/mrc', 'callback', '/MediaRenderer/RenderingControl/Event')
    rawConnections[0].options.inputReader.call('HTTP/1.1 200 OK\r\nSID: uuid:prior\r\n\r\n')
    driver.upnpSubscriptionWatchdog([guid: priorGuid])

    then:
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.sidKey == 'sid2'
    driver.getSid('sid1') == null
    driver.getSid('sid2') == null
  }

  def "legacy callbacks and old fallback methods cannot alter current queue ownership"() {
    when:
    driver.upnpSubscribeGeneric('subscribeToAVTransport', 'sid1', '/avt', 'callback', '/MediaRenderer/AVTransport/Event')
    String guid = driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.guid
    driver.subscribeResubscribeGenericCallback('subscribeToAVTransport', 'resubscribeToAVTransport', 'sid1', 'AVTransport', null)
    driver.upnpUnsubscribeCallbackGeneric('sid1', 'AVTransport', null)
    driver.unlockSid1SubscribeMutexAfterTimeoutCallback()
    driver.socketStatus('receive error')

    then:
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.guid == guid
    driver.getSid('sid1') == null
    rawConnections.size() == 1
  }

  def "ZGT XML guards reject blank outer and nested payloads"() {
    expect:
    !driver.isPlausibleXmlPayload(null)
    !driver.isPlausibleXmlPayload('   \n\t')
    !driver.isPlausibleXmlPayload('not xml')
    driver.isPlausibleXmlPayload('  <propertyset/>  ')

    and:
    !driver.processZoneGroupTopologyMessages('   ', new LinkedHashSet<String>())
    !driver.processZoneGroupTopologyMessages(
      '<propertyset><property><ZoneGroupState>   </ZoneGroupState></property></propertyset>',
      new LinkedHashSet<String>()
    )
  }

  def "topology change holds only affected player until its expected topology arrives"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    // The parent app stamps expectations before dispatching the mutation.
    driver.device.dataValues.expectedGroupTopology = JsonOutput.toJson([
      coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST'], expiresAt: driver.now() + 30000L
    ])
    driver.metaClass.getZoneGroupTopologyForPlayer = { String xml, String playerId ->
      xml == '<topology>final</topology>'
        ? [coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST']]
        : [coordinatorId: 'RINCON_OTHER', memberIds: ['RINCON_OTHER']]
    }
    driver.metaClass.processZoneGroupTopologyMessages = { String xml, LinkedHashSet oldMembers ->
      driver.runtimeState.appliedTopologyXml = xml
      true
    }

    when:
    driver.deferOrProcessZoneGroupTopologyPayload('SONOS-TEST-DNI', '<topology>intermediate</topology>')

    then:
    driver.runtimeState.appliedTopologyXml == null
    driver.pendingTopologyXmlByDni['SONOS-TEST-DNI'] == '<topology>intermediate</topology>'

    when:
    driver.deferOrProcessZoneGroupTopologyPayload('SONOS-TEST-DNI', '<topology>final</topology>')

    then:
    driver.runtimeState.appliedTopologyXml == '<topology>final</topology>'
    !driver.pendingTopologyXmlByDni.containsKey('SONOS-TEST-DNI')
    driver.device.dataValues.expectedGroupTopology == ''
  }

  def "coordinator transfer retains the topology expectation stamped by the parent"() {
    given:
    driver.device.dataValues.id = 'RINCON_OLD_COORD'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_OLD_COORD'
    driver.device.dataValues.groupPlayerIds = 'RINCON_OLD_COORD,RINCON_NEW_COORD'
    Map<String, Map> finalTopology = [
      'RINCON_OLD_COORD': [coordinatorId: 'RINCON_NEW_COORD', memberIds: ['RINCON_OLD_COORD', 'RINCON_NEW_COORD']],
      'RINCON_NEW_COORD': [coordinatorId: 'RINCON_NEW_COORD', memberIds: ['RINCON_OLD_COORD', 'RINCON_NEW_COORD']]
    ]

    driver.device.dataValues.expectedGroupTopology = JsonOutput.toJson(
      finalTopology.RINCON_OLD_COORD + [expiresAt: driver.now() + 30000L]
    )

    when:
    driver.playerModifyGroupMembers([], ['RINCON_NEW_COORD'], finalTopology)

    then:
    Map expected = new JsonSlurper().parseText(driver.device.dataValues.expectedGroupTopology) as Map
    expected.coordinatorId == 'RINCON_NEW_COORD'
    expected.memberIds == ['RINCON_OLD_COORD', 'RINCON_NEW_COORD']
    expected.expiresAt > driver.now()
    websocketMessages.any { String message -> message.contains('modifyGroupMembers') }
  }

  def "unrelated player's topology updates are not held"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    driver.device.dataValues.id = 'RINCON_UNRELATED'
    driver.metaClass.processZoneGroupTopologyMessages = { String xml, LinkedHashSet oldMembers ->
      driver.runtimeState.appliedTopologyXml = xml
      true
    }

    when:
    driver.deferOrProcessZoneGroupTopologyPayload('SONOS-TEST-DNI', '<topology>unrelated-current</topology>')

    then:
    driver.runtimeState.appliedTopologyXml == '<topology>unrelated-current</topology>'
    !driver.pendingTopologyXmlByDni.containsKey('SONOS-TEST-DNI')
  }

  def "expired expected topology releases the most recent pending snapshot"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    driver.device.dataValues.expectedGroupTopology = groovy.json.JsonOutput.toJson([
      coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST'], expiresAt: (driver.now() as Long) - 1L
    ])
    driver.pendingTopologyXmlByDni['SONOS-TEST-DNI'] = '<topology>latest</topology>'
    driver.pendingTopologyKindByDni['SONOS-TEST-DNI'] = 'zgt'
    driver.metaClass.processZoneGroupTopologyMessages = { String xml, LinkedHashSet oldMembers ->
      driver.runtimeState.appliedTopologyXml = xml
      true
    }

    when:
    driver.flushPendingTopologyUpdates()

    then:
    driver.runtimeState.appliedTopologyXml == '<topology>latest</topology>'
    !driver.pendingTopologyXmlByDni.containsKey('SONOS-TEST-DNI')
    driver.device.dataValues.expectedGroupTopology == ''
  }

  def "held groups websocket topology is reported to the parent without publishing player states"() {
    given:
    driver.device.currentValues.groupCoordinatorId = 'RINCON_OLD_COORD'
    driver.device.currentValues.isGrouped = 'on'
    driver.device.dataValues.expectedGroupTopology = JsonOutput.toJson([
      coordinatorId: 'RINCON_OTHER', memberIds: ['RINCON_OTHER', 'RINCON_TEST'],
      expiresAt: (driver.now() as Long) + 15000L
    ])
    String message = JsonOutput.toJson([
      [namespace: 'groups', name: 'groups', type: 'groups'],
      [groups: [[id: 'RINCON_TEST:100', coordinatorId: 'RINCON_TEST',
        playerIds: ['RINCON_TEST'], name: 'Test Player']], players: []]
    ])

    when:
    driver.processWebsocketMessage(message)

    then:
    driver.device.currentValues.groupCoordinatorId == 'RINCON_OLD_COORD'
    driver.device.currentValues.isGrouped == 'on'
    Map topologyReport = driver.locationEvents.find { Map event -> event.data?.kind == 'topology' }?.data
    topologyReport.groupId == 'RINCON_TEST:100'
    topologyReport.coordinatorId == 'RINCON_TEST'
    topologyReport.playerIds == ['RINCON_TEST']
    driver.device.events.every { Map event -> event.name != 'groupCoordinatorId' }
  }

  def "logging level controls player log output"() {
    given:
    driver.settings.logLevel = 'off'

    when:
    driver.logInfo('hidden')
    driver.logError('hidden')

    then:
    driver.logs.empty

    when:
    driver.settings.logLevel = 'error'
    driver.logInfo('hidden')
    driver.logError('visible')

    then:
    driver.logs.size() == 2
    driver.logs.every { String entry -> entry.contains('visible') }
  }

  def "second priority clip waits for active priority playback and preserves suspended regular clips"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.getDeviceAudioClipQueueSaved().clear()
    driver.settings.ttsBoostAmount = 10
    driver.playerLoadAudioClip('http://example.test/a.mp3', 15G, 30, 'A')
    driver.playerLoadAudioClip('http://example.test/b.mp3', 15G, 10, 'B')
    driver.playerLoadAudioClipHighPriority('http://example.test/h1.mp3', 15G, 'H1', 20)
    List<Map> suspended = new ArrayList<Map>(driver.getDeviceAudioClipQueueSaved())
    List<Map> watchdogs = new ArrayList<Map>(driver.scheduled)

    when:
    driver.playerLoadAudioClipHighPriority('http://example.test/h2.mp3', 15G, 'H2', 10)

    then:
    driver.runtimeState.activeAudioClipCorrelationId == 'H1'
    driver.runtimeState.audioClipPlaying == true
    driver.getDeviceAudioClipQueueHighPriority()*.correlationId == ['H2']
    driver.getDeviceAudioClipQueueSaved().toList() == suspended
    suspended*.correlationId == ['A', 'B']
    driver.scheduled == watchdogs

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.runtimeState.activeAudioClipCorrelationId == 'H2'
    driver.getDeviceAudioClipQueueSaved()*.correlationId == ['A', 'B']

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/a.mp3'
    driver.runtimeState.activeAudioClipMessage.dispatchCount == 2
    driver.runtimeState.activeAudioClipCorrelationId != 'A'
    driver.getDeviceAudioClipQueue()*.correlationId == ['B']
    driver.getDeviceAudioClipQueueSaved().empty

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.runtimeState.activeAudioClipCorrelationId == 'B'
  }

  def "stale audio clip watchdog does not reset active playback state"() {
    given:
    driver.runtimeState.audioClipPlaying = true
    driver.runtimeState.remove('audioClipQueueStartTime')
    driver.runtimeState.remove('audioClipQueueTotalDuration')

    when:
    driver.audioClipWatchdog()

    then:
    driver.runtimeState.audioClipPlaying == true
    driver.logs.any { String entry -> entry.contains('Ignoring stale audio clip watchdog guid=unknown watchdog=unknown') }
    driver.logs.every { String entry -> !entry.contains('Audio clip watchdog fired') }
  }

  def "legacy player state migrates to the DNI runtime map and is cleared from Hubitat state"() {
    given:
    driver.state.audioClipPlaying = true
    driver.state.parentCommandSequence = 4
    driver.binding.setVariable('atomicState', [wsRetryCount: 2])

    when:
    driver.migrateLegacyRuntimeState()

    then:
    driver.runtimeState.audioClipPlaying == true
    driver.runtimeState.parentCommandSequence == 4
    driver.runtimeState.wsRetryCount == 2
    driver.state.isEmpty()
    driver.binding.getVariable('atomicState').isEmpty()
  }

  def "favorite lookup finds an item by Sonos favorite ID instead of map key"() {
    given:
    driver.getFavoritesMap()['object-service-account'] = [
      id: '353', name: 'Amazon Favorite', service: 'Amazon Music'
    ]

    expect:
    driver.findFavoriteById('353').name == 'Amazon Favorite'
    driver.isAmazonMusicService(driver.findFavoriteById('353').service)
    driver.findFavoriteById('999') == null
  }

  def "Amazon favorite gets its play workaround before the first confirmation check"() {
    given:
    driver.getFavoritesMap()['object-service-account'] = [
      id: '353', name: 'Amazon Favorite', service: 'Amazon Music'
    ]

    when:
    driver.loadFavoriteFull('353', 'repeat all', 'replace', 'off', 'true', 'on')

    then:
    driver.scheduled.find { List call -> call[1] == 'playerPlay' }?.getAt(0) == 3
    driver.scheduled.find { List call -> call[1] == 'checkFavoritePlaybackAndRetry' }?.getAt(0) == 12
    driver.favoriteRetryState['SONOS-TEST-DNI'].isAmazon == true
  }

  def "favorite confirmation polls first and does not immediately reload"() {
    given:
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.checkFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.size() == 2
    websocketMessages.any { String message -> message.contains('getPlaybackStatus') }
    websocketMessages.any { String message -> message.contains('getMetadataStatus') }
    websocketMessages.every { String message -> !message.contains('loadFavorite') }
    driver.scheduled.last() == [
      2,
      'evaluateFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "favorite evaluation performs one bounded reload only after confirmation fails"() {
    given:
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.count { String message -> message.contains('loadFavorite') } == 1
    retryState.attemptNumber == 1
    driver.scheduled.last() == [
      5,
      'checkFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "active pre-existing playback is not interrupted while target confirmation is ambiguous"() {
    given:
    driver.device.currentValues.transportStatus = 'playing'
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    retryState.playbackObserved = true
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.every { String message -> !message.contains('loadFavorite') }
    retryState.attemptNumber == 0
    retryState.ambiguousConfirmationPasses == 1
    driver.scheduled.last() == [
      2,
      'checkFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "stop is queued even when the reported playback state is stopped"() {
    given:
    driver.device.currentValues.transportStatus = 'stopped'

    when:
    driver.stop()
    driver.drainTransportCommandQueue()

    then:
    websocketMessages.size() == 1
    websocketMessages[0].contains('"command":"stop"')
    // Stop is intentionally sent even with stale reported state.
    driver.runtimeState.activeTransportCommand.command == 'stop'
  }

  def "stop sends a playback stop request when playback is active"() {
    given:
    driver.device.currentValues.transportStatus = 'playing'

    when:
    driver.stop()
    driver.drainTransportCommandQueue()

    then:
    websocketMessages.size() == 1
    websocketMessages[0].contains('"namespace":"playback"')
    websocketMessages[0].contains('"command":"stop"')
  }

  def "playlist confirmation also polls before performing a bounded reload"() {
    given:
    driver.loadPlaylistFull('playlist-7', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.checkPlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.size() == 2
    websocketMessages.any { String message -> message.contains('getPlaybackStatus') }
    websocketMessages.any { String message -> message.contains('getMetadataStatus') }
    websocketMessages.every { String message -> !message.contains('loadPlaylist') }

    when:
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.count { String message -> message.contains('loadPlaylist') } == 1
    retryState.attemptNumber == 1
    driver.scheduled.last() == [
      5,
      'checkPlaylistPlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "short media load overloads enable crossfade in the outgoing command"() {
    when:
    driver."${method}"(*arguments)

    then:
    List payload = (List)new JsonSlurper().parseText(websocketMessages[0])
    payload[0].command == command
    payload[1].playModes.crossfade == true

    where:
    method | arguments | command
    'loadFavorite' | ['42'] | 'loadFavorite'
    'loadFavoriteFull' | ['42'] | 'loadFavorite'
    'loadFavoriteFull' | ['42', 'repeat all'] | 'loadFavorite'
    'loadFavoriteFull' | ['42', 'repeat all', 'replace'] | 'loadFavorite'
    'loadFavoriteFull' | ['42', 'repeat all', 'replace', 'off'] | 'loadFavorite'
    'loadFavoriteFull' | ['42', 'repeat all', 'replace', 'off', 'true'] | 'loadFavorite'
    'loadPlaylist' | ['42'] | 'loadPlaylist'
    'loadPlaylistFull' | ['42'] | 'loadPlaylist'
    'loadPlaylistFull' | ['42', 'repeat all'] | 'loadPlaylist'
    'loadPlaylistFull' | ['42', 'repeat all', 'replace'] | 'loadPlaylist'
    'loadPlaylistFull' | ['42', 'repeat all', 'replace', 'off'] | 'loadPlaylist'
    'loadPlaylistFull' | ['42', 'repeat all', 'replace', 'off', 'true'] | 'loadPlaylist'
  }

  def "append-and-play sends INSERT with the unchanged Sonos playlist ID"() {
    when:
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'insert', 'off', 'true', 'on')

    then:
    List payload = (List)new JsonSlurper().parseText(websocketMessages[0])
    payload[0].namespace == 'playlists'
    payload[0].command == 'loadPlaylist'
    payload[1].playlistId == 'playlist-963'
    payload[1].action == 'INSERT'
    payload[1].playOnCompletion == true
  }

  def "acknowledged playlist load issues play instead of duplicating the playlist request"() {
    given:
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'insert', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']
    driver.processWebsocketMessage('[{"namespace":"playlists","response":"loadPlaylist","success":true},{}]')
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])
    driver.drainTransportCommandQueue()

    then:
    websocketMessages.count { String message -> message.contains('"command":"play"') } == 1
    websocketMessages.every { String message -> !message.contains('loadPlaylist') }
    retryState.playCommandAttempts == 1
    driver.scheduled.contains([
      2,
      'checkPlaylistPlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ])

    when:
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.empty
    driver.playlistRetryState.isEmpty()
    driver.state.lastError?.contains('PLAYBACK_NOT_STARTED')
    !driver.events.any { Map event -> event.name == 'lastError' }
  }

  def "buffering counts as playlist playback progress and does not reload the playlist"() {
    given:
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'insert', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()

    when:
    driver.processWebsocketMessage('[{"type":"playbackStatus","namespace":"playback"},{"playbackState":"PLAYBACK_STATE_BUFFERING"}]')
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    driver.playlistRetryState.isEmpty()
    websocketMessages.every { String message -> !message.contains('loadPlaylist') && !message.contains('"command":"play"') }
  }

  def "deterministic playlist load errors are surfaced without retrying"() {
    given:
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'insert', 'off', 'true', 'on')

    when:
    driver.processWebsocketMessage('[{"type":"globalError","namespace":"playlists","response":"loadPlaylist","success":false},{"errorCode":"ERROR_INVALID_OBJECT_ID","reason":"Playlist not found"}]')

    then:
    driver.playlistRetryState.isEmpty()
    driver.state.lastError == "Playlist 'playlist-963' failed to load: ERROR_INVALID_OBJECT_ID"
    driver.state.lastErrorReason == 'Playlist not found'
    !driver.events.any { Map event -> event.name == 'lastError' }
  }

  def "playlist metadata matches the exact ID before using a unique name fallback"() {
    given:
    driver.getPlaylistsMap()['playlist-963'] = [id: 'playlist-963', name: 'Morning Mix']
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']

    when:
    driver.isPlaylistPlaying([
      container: [
        type: 'PLAYLIST',
        name: 'Different Name',
        id: [objectId: 'urn:sonos:playlist-963']
      ]
    ])

    then:
    retryState.metadataConfirmed == true
  }

  def "stale favorite callbacks cannot act on a newer load operation"() {
    given:
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    String staleOperationId = driver.favoriteRetryState['SONOS-TEST-DNI'].operationId
    driver.loadFavoriteFull('43', 'repeat all', 'replace', 'off', 'true', 'on')
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: staleOperationId])

    then:
    websocketMessages.empty
    driver.scheduled.empty
    driver.favoriteRetryState['SONOS-TEST-DNI'].favoriteId == '43'
  }

  def "topology membership updates are deferred until the player callback returns"() {
    when:
    driver.parentUpdateGroupDevices('RINCON_COORD', ['RINCON_FOLLOW'])

    then:
    driver.scheduled == [[
      1,
      'deferredParentUpdateGroupDevices',
      [overwrite: true, data: [coordinatorId: 'RINCON_COORD', playersInGroup: ['RINCON_FOLLOW']]]
    ]]
  }

  def "deferred membership updates publish a location event"() {
    when:
    driver.deferredParentUpdateGroupDevices([coordinatorId: 'RINCON_TEST', playersInGroup: ['RINCON_TEST']])

    then:
    driver.locationEvents.size() == 1
    driver.locationEvents[0].name == 'dwinksSonosAdvancedPlayerState'
    driver.locationEvents[0].data == [kind: 'membership', coordinatorId: 'RINCON_TEST', playersInGroup: ['RINCON_TEST'], playerDni: 'SONOS-TEST-DNI']
  }

  def "group state flush publishes one batched location event"() {
    given:
    driver.queueGroupDeviceUpdate([status: 'playing', _groupVolume: 25, _groupMute: 'unmuted'])

    when:
    driver.flushPendingGroupDeviceUpdates()

    then:
    driver.locationEvents.size() == 1
    driver.locationEvents[0].data.kind == 'state'
    driver.locationEvents[0].data.followerAttrs == [status: 'playing', groupVolume: 25, groupMute: 'unmuted']
    driver.locationEvents[0].data.volumeAttrs == [volume: 25, mute: 'unmuted']
    driver.locationEvents[0].data.playbackAttrs == [status: 'playing']
    driver.getPendingGroupDeviceUpdateSnapshot('SONOS-TEST-DNI').isEmpty()
  }

  def "follower group volume forwarding is deferred through the parent boundary"() {
    given:
    driver.device.dataValues.isGroupCoordinator = 'false'
    driver.device.currentValues.isGrouped = 'on'

    when:
    driver.setGroupVolume(40G)

    then:
    driver.scheduled.find { List call -> call[1] == 'drainParentCoordinatorCommandQueue' }?.getAt(0) == 1
    driver.runtimeState.pendingParentCoordinatorCommands[0].command == 'setGroupVolume'
    websocketMessages.empty
  }

  def "addPlayerToGroup forwards the requested identifier through the parent boundary"() {
    when:
    driver.addPlayerToGroup('  Bedroom  ')

    then:
    driver.runtimeState.pendingParentCoordinatorCommands[0].command == 'addPlayerToGroup'
    driver.runtimeState.pendingParentCoordinatorCommands[0].args == ['Bedroom']
    websocketMessages.empty
  }

  def "parent command queue waits for controller acknowledgement before sending the next request"() {
    given:
    driver.requestParentCoordinatorCommand('muteGroup')
    driver.requestParentCoordinatorCommand('unmuteGroup')

    when:
    driver.drainParentCoordinatorCommandQueue()
    Map first = (Map)driver.runtimeState.activeParentCoordinatorCommand
    driver.acknowledgeParentCommand(first.requestId as String)

    then:
    first.command == 'muteGroup'
    driver.runtimeState.pendingParentCoordinatorCommands.size() == 1
    !driver.runtimeState.activeParentCoordinatorCommand
    driver.scheduled.any { List call -> call[1] == 'drainParentCoordinatorCommandQueue' }
  }

  def "grouped player volume is staggered without issuing playback commands"() {
    given:
    driver.device.currentValues.isGrouped = 'on'
    driver.device.dataValues.groupPlayerIds = 'RINCON_FIRST,RINCON_TEST,RINCON_THIRD'

    when:
    driver.setLevel(40G)
    driver.setLevel(41G)

    then:
    websocketMessages.empty
    driver.pendingGroupedPlayerVolumes['RINCON_TEST'] == 41
    driver.scheduled.find { List call -> call[1] == 'flushPendingGroupedPlayerVolume' } == [
      500,
      'flushPendingGroupedPlayerVolume',
      [overwrite: true]
    ]

    when:
    driver.flushPendingGroupedPlayerVolume()

    then:
    websocketMessages.size() == 1
    websocketMessages[0].contains('"namespace":"playerVolume"')
    websocketMessages[0].contains('"command":"setVolume"')
    !websocketMessages[0].contains('"command":"play"')
    !websocketMessages[0].contains('"command":"pause"')
  }

  def "setVolumeZero sends a direct player volume command with zero"() {
    when:
    driver.setVolumeZero()

    then:
    websocketMessages.size() == 1
    List payload = (List)new JsonSlurper().parseText(websocketMessages[0])
    payload[0].namespace == 'playerVolume'
    payload[0].command == 'setVolume'
    payload[1].volume == 0
    websocketMessages[0].contains('"volume":0')
  }

  def "group Favorite loads use the verified group ID without the per-player Amazon workaround"() {
    when:
    driver.loadFavoriteForGroupOperation('42', 'repeat all', 'replace', 'off', 'true', 'on', 'group-op-1', 'GROUP-VERIFIED', 'group-op-1:1')

    then:
    List payload = (List)new JsonSlurper().parseText(websocketMessages.find { String message -> message.contains('"command":"loadFavorite"') })
    payload[0].namespace == 'favorites'
    payload[0].command == 'loadFavorite'
    payload[0].groupId == 'GROUP-VERIFIED'
    driver.runtimeState.groupFavoriteOperationId == 'group-op-1'
    driver.runtimeState.groupFavoriteOperationAttemptId == 'group-op-1:1'
    String bridgeValue = driver.device.events.find { Map event ->
      event.name == 'groupFavoriteOperation'
    }.value as String
    ((Map)new JsonSlurper().parseText(bridgeValue.replaceAll('(?is)<[^>]+>', ''))).attemptId == 'group-op-1:1'
    driver.device.currentValues.groupFavoriteOperation == bridgeValue
    bridgeValue.startsWith('<span style="display:none">')
    driver.scheduled.every { List call -> call[1] != 'playerPlay' }
  }

  def "stale group Favorite bridge state is removable"() {
    given:
    driver.device.currentValues.groupFavoriteOperation = '{"event":"stale"}'

    when:
    driver.clearGroupFavoriteOperationCurrentState()

    then:
    !driver.device.currentValues.containsKey('groupFavoriteOperation')
  }

  def "websocket commands queue until the socket is open and flush in order"() {
    given:
    driver.device.dataValues.websocketStatus = 'closed'
    driver.runtimeState.remove('websocketConnectPending')

    when:
    driver.sendWsMessage('first-command')
    driver.sendWsMessage('second-command')

    then:
    websocketMessages.empty
    driver.runtimeState.websocketOutboundQueue*.message == ['first-command', 'second-command']

    when:
    driver.setWebSocketStatus('open')

    then:
    Integer firstCommandIndex = websocketMessages.indexOf('first-command')
    Integer secondCommandIndex = websocketMessages.indexOf('second-command')
    Integer subscriptionIndex = websocketMessages.findIndexOf { String message -> message.contains('"command":"subscribe"') }
    subscriptionIndex >= 0
    firstCommandIndex > subscriptionIndex
    secondCommandIndex > firstCommandIndex
    driver.runtimeState.websocketOutboundQueue == null
  }

  def "loading a Favorite expects playback and metadata subscription events"() {
    given:
    String command = JsonOutput.toJson([[namespace: 'favorites', command: 'loadFavorite', groupId: 'GROUP_TEST'], [favoriteId: '24']])

    when:
    driver.sendWsMessage(command)

    then:
    List<Map> pending = driver.runtimeState.websocketExpectedSubscriptionEvents as List<Map>
    pending.size() == 1
    pending[0].expected == [
      [namespace: 'playbackMetadata', type: 'metadataStatus'],
      [namespace: 'playback', type: 'playbackStatus']
    ]

    when:
    driver.processWebsocketMessage('[{"namespace":"playbackMetadata","name":"playbackMetadataStatus","type":"metadataStatus"},{"currentItem":{"id":"24"}}]')

    then:
    (driver.runtimeState.websocketExpectedSubscriptionEvents as List<Map>)[0].received == ['playbackMetadata:metadataStatus']

    when:
    driver.observeExpectedSubscriptionEvent([namespace: 'playback', type: 'playbackStatus'])

    then:
    driver.runtimeState.websocketExpectedSubscriptionEvents == null
  }

  def "missing subscription events trigger one namespace resubscription"() {
    given:
    String command = JsonOutput.toJson([[namespace: 'favorites', command: 'loadFavorite', groupId: 'GROUP_TEST'], [favoriteId: '24']])
    driver.sendWsMessage(command)
    List<Map> pending = driver.runtimeState.websocketExpectedSubscriptionEvents as List<Map>
    pending[0].deadlineAt = driver.now() - 1001L

    when:
    driver.watchExpectedSubscriptionEvents()

    then:
    websocketMessages.any { String message -> message.contains('"namespace":"playbackMetadata"') && message.contains('"command":"subscribe"') }
    websocketMessages.any { String message -> message.contains('"namespace":"playback"') && message.contains('"command":"subscribe"') }
    (driver.runtimeState.websocketExpectedSubscriptionEvents as List<Map>)[0].attempts == 1
  }

  def "SOAP state changes expect a matching UPnP event and renew its subscription on timeout"() {
    given:
    Map params = [uri: 'http://192.168.1.50:1400/MediaRenderer/RenderingControl/Control',
        headers: [SOAPAction: 'urn:schemas-upnp-org:service:RenderingControl:1#SetVolume']]

    when:
    driver.postSonosSoapAsync('localControlCallback', params)
    List<Map> pending = driver.runtimeState.upnpExpectedSubscriptionEvents as List<Map>
    pending[0].deadlineAt = driver.now() - 1L
    driver.watchExpectedUpnpSubscriptionEvents()

    then:
    driver.asyncCalls.size() == 1
    pending[0].sidKey == 'sid2'
    pending[0].attempts == 1
    rawRequests[0].startsWith('SUBSCRIBE /MediaRenderer/RenderingControl/Event HTTP/1.1')

    when:
    driver.observeExpectedUpnpSubscriptionEvent('sid2')

    then:
    driver.runtimeState.upnpExpectedSubscriptionEvents == null
  }

  def "group Favorite metadata reports an exact Favorite ID to the parent operation"() {
    given:
    driver.getFavoritesMap()['42serviceaccount'] = [id: '42', name: 'Favorite 42', imageUrl: 'https://example.test/cover']
    driver.registerGroupFavoriteOperation('group-op-2', '42')
    driver.device.events.clear()

    when:
    driver.isFavoritePlaying([
      container: [imageUrl: 'https://example.test/cover', id: [objectId: 'urn:42', serviceId: 'service', accountId: 'account']]
    ])

    then:
    Map event = driver.device.events.find { Map item -> item.name == 'groupFavoriteOperation' }
    Map payload = (Map)new JsonSlurper().parseText((event.value as String).replaceAll('(?is)<[^>]+>', ''))
    payload.event == 'metadataConfirmed'
    payload.data.favoriteId == '42'
    payload.data.confirmed == true
  }

  def "group Favorite acknowledgements are emitted for the parent operation"() {
    given:
    driver.registerGroupFavoriteOperation('group-op-3', '42')
    driver.device.events.clear()

    when:
    driver.processWebsocketMessage('[{"namespace":"favorites","response":"loadFavorite","success":true},{}]')

    then:
    Map event = driver.device.events.find { Map item -> item.name == 'groupFavoriteOperation' }
    Map payload = (Map)new JsonSlurper().parseText((event.value as String).replaceAll('(?is)<[^>]+>', ''))
    payload.event == 'favoriteLoadAck'
    payload.data.success == true
  }

  def "empty groups responses emit a negative topology observation"() {
    given:
    driver.registerGroupFavoriteOperation('group-op-4', '42')
    driver.device.events.clear()

    when:
    driver.processWebsocketMessage('[{"type":"groups","name":"groups"}, {"groups":[]}]')

    then:
    Map event = driver.device.events.find { Map item -> item.name == 'groupFavoriteOperation' }
    Map payload = (Map)new JsonSlurper().parseText((event.value as String).replaceAll('(?is)<[^>]+>', ''))
    payload.event == 'groups'
    payload.observedAt instanceof Number
    payload.data.groupId == null
    payload.data.coordinatorId == null
    payload.data.playerIds == []
  }
  def "announcement volume preserves explicit zero across paths and boosts"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.device.currentValues.volume = 5
    driver.device.currentValues.mute = 'unmuted'
    driver.settings.ttsBoostAmount = boost
    driver.settings.chimeBeforeTTS = false
    driver.settings.createRightChannelChildDevice = true
    driver.device.dataValues.rightChannelId = 'RINCON_RIGHT'
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.getDeviceAudioClipQueueSaved().clear()
    driver.runtimeState.audioClipPlaying = true

    when:
    if (path == 'regular') {
      driver.playerLoadAudioClip('http://example.test/volume.mp3', volume, 2, 'V')
    } else if (path == 'priority') {
      driver.playerLoadAudioClipHighPriority('http://example.test/volume.mp3', volume, 'V', 2)
    } else {
      driver.playerPrepareAudioClipForSynchronizedStart('http://example.test/volume.mp3', volume, 'V', 2)
    }
    Map clip = path == 'priority' ? driver.runtimeState.activeAudioClipMessage : driver.getDeviceAudioClipQueue().peek()
    List payload = (List)new JsonSlurper().parseText(clip.leftChannel as String)

    then:
    payload[1].volume == (volume == 0 ? 0 : Math.max(0, Math.min((volume == null ? 5 : volume) + boost, 100)))
    ((List)new JsonSlurper().parseText(clip.rightChannel as String))[1].volume == payload[1].volume

    where:
    [path, volume, boost] << [['regular', 'priority', 'synchronized'], [null, 0G, 1G, 95G], [0, 10, -5]].combinations()
  }
  def "chimes preserve explicit zero and omit unspecified volume"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.device.currentValues.mute = 'unmuted'
    driver.settings.chimeBeforeTTS = true
    driver.settings.createRightChannelChildDevice = true
    driver.device.dataValues.rightChannelId = 'RINCON_RIGHT'
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.getDeviceAudioClipQueueSaved().clear()
    driver.runtimeState.audioClipPlaying = true

    when:
    if (synchronizedClip) {
      driver.playerPrepareAudioClipForSynchronizedStart('http://example.test/test.mp3', volume, 'C', 2)
    } else {
      driver.playerLoadAudioClipChime(volume)
    }
    Map clip = driver.getDeviceAudioClipQueue().peek()
    List payload = (List)new JsonSlurper().parseText(clip.leftChannel as String)

    then:
    payload[1].clipType == 'CHIME'
    payload[1].containsKey('volume') == (volume != null)
    ((List)new JsonSlurper().parseText(clip.rightChannel as String))[1] == payload[1]
    if (volume != null) { assert payload[1].volume == volume }

    where:
    [synchronizedClip, volume] << [[false, true], [null, 0G, 1G]].combinations()
  }

  def "public volume coercion preserves zero"() {
    expect:
    driver.coerceVolume(input) == 0G

    where:
    input << [0, 0L, 0G, '0', ' 0 ']
  }
  def "public restore resume and speak commands preserve zero in clip payloads"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.metaClass.textToSpeech = { String text, String voice -> [uri: 'http://example.test/zero.mp3', duration: 1] as LinkedHashMap }
    driver.device.dataValues.capabilities = 'AUDIO_CLIP'
    driver.device.currentValues.volume = 5
    driver.settings.ttsBoostAmount = 10
    driver.getDeviceAudioClipQueue().clear()
    driver.runtimeState.audioClipPlaying = true

    when:
    driver.invokeMethod(command, ['zero', input] as Object[])
    Map clip = driver.getDeviceAudioClipQueue().peek()

    then:
    ((List)new JsonSlurper().parseText(clip.leftChannel as String))[1].volume == 0

    where:
    [command, input] << [['playTextAndRestore', 'playTextAndResume', 'speak'], [0, 0L, 0G, '0', ' 0 ']].combinations()
  }

  def "fallback announcements preserve zero with boost"() {
    given:
    List<BigDecimal> volumes = []
    driver.metaClass.getParent = { -> null }
    driver.metaClass.textToSpeech = { String text, String voice -> [uri: 'http://example.test/zero.mp3'] as LinkedHashMap }
    driver.metaClass.componentPlayTextNoRestoreLocal = { String text, BigDecimal level, String voice -> volumes.add(level) }
    driver.device.currentValues.volume = 5
    driver.settings.ttsBoostAmount = 10

    when:
    driver.invokeMethod(command, ['zero', 0G, null] as Object[])

    then:
    volumes == [0G]

    where:
    command << ['devicePlayText', 'devicePlayTextNoRestore']
  }

  def "local stream playback applies explicit zero volume"() {
    given:
    List<BigDecimal> volumes = []
    driver.metaClass.componentSetGroupLevelLocal = { BigDecimal level -> volumes.add(level) }
    driver.metaClass.setAVTransportURIAndPlay = { String uri -> }
    driver.metaClass.setAVTransportURI = { String uri -> }
    driver.metaClass.textToSpeech = { String text, String voice -> [uri: 'http://example.test/zero.mp3'] as LinkedHashMap }

    when:
    driver.invokeMethod(command, ['http://example.test/zero.mp3', 0G] as Object[])

    then:
    volumes == [0G]

    where:
    command << ['componentPlayTextNoRestoreLocal', 'componentLoadStreamUrlLocal', 'componentSetStreamUrlLocal']
  }
}
