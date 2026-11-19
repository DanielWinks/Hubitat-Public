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
  @Shared List<Map> parentPlayerUpdates = []
  @Shared List<Map> parentPlayerRequests = []
  @Shared List<List> websocketConnections = []
  @Shared List<String> rawRequests = []
  @Shared Object rawSocket

  List<Map> playerStateMessages() {
    parentPlayerUpdates.collect { Map event -> new LinkedHashMap(event) }
  }

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

  def "initialize clears all local pending command queues before requesting parent initialization"() {
    given:
    driver.audioClipQueueInitialization()
    driver.getDeviceAudioClipQueue().add([correlationId: 'regular'])
    driver.getDeviceAudioClipQueueHighPriority().add([correlationId: 'high'])
    driver.getDeviceAudioClipQueueSaved().add([correlationId: 'saved'])
    driver.getDeviceAudioClipQueueTimers().put('clip-uri', 123L)
    driver.state.pendingParentAppTtsRequests = [tts: [uri: 'http://example.test/tts.mp3']]
    driver.runtimeState.pendingParentCoordinatorCommands = [[command: 'play']]
    driver.runtimeState.activeParentCoordinatorCommand = [command: 'pause']
    driver.runtimeState.transportCommandQueue = [[command: 'stop']]
    driver.runtimeState.activeTransportCommand = [command: 'play']
    driver.runtimeState.websocketOutboundQueue = [[namespace: 'playback', wireCommand: 'play']]
    driver.runtimeState.websocketCommandLedger = [[namespace: 'playback', wireCommand: 'pause']]
    driver.runtimeState.activeAudioClipCorrelationId = 'active-clip'
    driver.atomicState.activeAudioClipMessage = [correlationId: 'active-clip']
    driver.state.pendingParentCoordinatorCommands = [[command: 'legacy-play']]
    driver.atomicState.transportCommandQueue = [[command: 'legacy-stop']]
    driver.atomicState.audioClipProgressByCorrelationId = [legacy: [phase: 'QUEUED']]

    when:
    driver.initialize()

    then:
    driver.getDeviceAudioClipQueue().isEmpty()
    driver.getDeviceAudioClipQueueHighPriority().isEmpty()
    driver.getDeviceAudioClipQueueSaved().isEmpty()
    driver.getDeviceAudioClipQueueTimers().isEmpty()
    !driver.state.containsKey('pendingParentAppTtsRequests')
    !driver.runtimeState.containsKey('pendingParentCoordinatorCommands')
    !driver.runtimeState.containsKey('activeParentCoordinatorCommand')
    !driver.runtimeState.containsKey('transportCommandQueue')
    !driver.runtimeState.containsKey('activeTransportCommand')
    !driver.runtimeState.containsKey('websocketOutboundQueue')
    !driver.runtimeState.containsKey('websocketCommandLedger')
    !driver.runtimeState.containsKey('activeAudioClipCorrelationId')
    !driver.state.containsKey('pendingParentCoordinatorCommands')
    !driver.atomicState.containsKey('transportCommandQueue')
    !driver.runtimeState.containsKey('audioClipProgressByCorrelationId')
    playerStateMessages().any { Map event -> event.kind == 'initializationRequested' }
  }

  def "app-mediated initialization uses a 180 second settle timeout"() {
    given:
    Long startedAt = 1000000L
    driver.runtimeState.appMediatedInitializationRequestId = 'init-180'
    driver.runtimeState.appMediatedInitializationStartedAt = startedAt
    driver.runtimeState.appMediatedInitializationUpnpStarted = true
    driver.runtimeState.appMediatedInitializationResubscriptionsStarted = true
    driver.metaClass.now = { -> startedAt + 180000L }

    when:
    driver.checkAppMediatedInitializationReady()

    then:
    driver.APP_INITIALIZATION_CHILD_TIMEOUT_SECONDS == 180
    playerStateMessages().any { Map event ->
      event.kind == 'initializationFailed' && event.requestId == 'init-180' &&
        event.detail == 'WebSocket or subscription setup did not settle within 180 seconds'
    }
    driver.logs.any { String log -> log.contains('App-mediated initialization timed out') }
  }

  def "right-channel initialization grants only that child a socket connection"() {
    given:
    List<List> grants = []
    dwinks.hubitat.stubs.MockDevice rightChannel = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getRightChannelChildDNI())
    rightChannel.metaClass.beginParentMediatedConnection = { String requestId, Boolean resetRetries ->
      grants << [requestId, resetRetries]
    }
    driver.children << rightChannel

    when:
    driver.requestSecondaryWebsocketInitialization('secondary device lifecycle initialize',
      driver.getRightChannelChildDNI())

    then:
    grants.size() == 1
    grants[0][1] == true
    !playerStateMessages().any { Map event -> event.kind == 'initializationRequested' }
    parentPlayerRequests.empty

    when: 'an unexpected child asks for access'
    driver.requestSecondaryWebsocketInitialization('secondary WebSocket connection requested', 'other-child')

    then:
    grants.size() == 1
    driver.logs.any { String message -> message.contains('Ignoring secondary WebSocket request from unexpected child') }
  }

  def "app-mediated initialization succeeds when only the best-effort right channel is unavailable"() {
    given:
    driver.runtimeState.appMediatedInitializationRequestId = 'init-right-channel-best-effort'
    driver.runtimeState.appMediatedInitializationStartedAt = driver.now()
    driver.runtimeState.appMediatedInitializationUpnpStarted = true
    driver.runtimeState.appMediatedInitializationResubscriptionsStarted = true
    driver.device.dataValues.websocketStatus = 'open'
    dwinks.hubitat.stubs.MockDevice rightChannel = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getRightChannelChildDNI())
    rightChannel.dataValues.websocketStatus = 'error'
    driver.device.dataValues.rightChannelId = 'RINCON_RIGHT'
    driver.children << rightChannel

    when:
    driver.checkAppMediatedInitializationReady()

    then:
    playerStateMessages().any { Map event ->
      event.kind == 'initializationComplete' && event.requestId == 'init-right-channel-best-effort'
    }
    !playerStateMessages().any { Map event ->
      event.kind == 'initializationFailed' && event.requestId == 'init-right-channel-best-effort'
    }
    driver.logs.any { String message -> message.contains('Right-channel WebSocket is not ready') }
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
    playerStateMessages().any { Map event ->
      event.kind == 'groupAnnouncementProgress' &&
        event.correlationId == 'announcement-guid' && event.phase == 'INTERRUPTED' &&
        event.externalPlayback == true &&
        event.replacementUri == 'https://music.example.test/track.mp3'
    }
    driver.runtimeState.groupAnnouncementStream == null
  }

  def "paused coordinator stream completes correlated group announcement immediately"() {
    given:
    driver.events.clear()
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
    playerStateMessages().any { Map event ->
      event.kind == 'groupAnnouncementProgress' &&
        event.correlationId == 'announcement-paused' && event.phase == 'DONE' &&
        event.playbackState == 'PLAYBACK_STATE_PAUSED' && event.termination == 'paused'
    }
    driver.runtimeState.groupAnnouncementStream == null
  }

  def "group announcement restores the captured queue track and playback position"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [
      correlationId: 'restore-guid', currentUri: 'x-rincon-queue:RINCON_TEST#0',
      currentUriMetaData: '<DIDL-Lite></DIDL-Lite>', track: '3', positionMillis: 65000L,
      playMode: 'REPEAT_ALL', transportState: 'PLAYING'
    ]

    when:
    driver.restoreGroupAnnouncementPlayback('restore-guid')
    Map uriRequest = driver.asyncCalls.last()
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 200), uriRequest.data)
    Map trackRequest = driver.asyncCalls.last()
    driver.announcementPlaybackSeekRestored(new hubitat.scheduling.AsyncResponse(status: 200), trackRequest.data)
    Map positionRequest = driver.asyncCalls.last()
    driver.announcementPlaybackSeekRestored(new hubitat.scheduling.AsyncResponse(status: 200), positionRequest.data)
    Map modeRequest = driver.asyncCalls.last()
    driver.announcementPlaybackPlayModeRestored(new hubitat.scheduling.AsyncResponse(status: 200), modeRequest.data)
    Map playRequest = driver.asyncCalls.last()
    driver.announcementPlaybackTransportRestored(new hubitat.scheduling.AsyncResponse(status: 200), playRequest.data)

    then:
    uriRequest.params.body.contains('<CurrentURI>x-rincon-queue:RINCON_TEST#0</CurrentURI>')
    trackRequest.params.body.contains('<Unit>TRACK_NR</Unit>')
    trackRequest.params.body.contains('<Target>3</Target>')
    positionRequest.params.body.contains('<Unit>REL_TIME</Unit>')
    positionRequest.params.body.contains('<Target>00:01:05</Target>')
    modeRequest.params.body.contains('<NewPlayMode>REPEAT_ALL</NewPlayMode>')
    playRequest.params.headers.SOAPAction.endsWith('#Play')
    driver.runtimeState.announcementPlaybackSnapshot == null
    playerStateMessages().any { Map event ->
      event.phase == 'PLAYBACK_RESTORED' && event.correlationId == 'restore-guid'
    }
  }

  def "group stream captures the pre-TTS transport and keeps it across stream replacement"() {
    given:
    driver.runtimeState.lastTransportPlaybackSnapshot = [
      currentUri: 'x-rincon-queue:RINCON_TEST#0', track: '2', positionMillis: 41000L,
      transportState: 'PLAYING'
    ]

    when:
    driver.playerPlayGroupAnnouncementStream('http://example.test/first.mp3', 20G, 'first-guid')
    driver.playerPlayGroupAnnouncementStream('http://example.test/second.mp3', 20G, 'second-guid')

    then:
    driver.runtimeState.announcementPlaybackSnapshot.correlationId == 'second-guid'
    driver.runtimeState.announcementPlaybackSnapshot.currentUri == 'x-rincon-queue:RINCON_TEST#0'
    driver.runtimeState.announcementPlaybackSnapshot.positionMillis == 41000L
  }

  def "group announcement restore leaves an external playback takeover alone"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [
      correlationId: 'external-guid', currentUri: 'x-rincon-queue:RINCON_TEST#0',
      announcementUri: 'http://example.test/tts.mp3', transportState: 'PLAYING'
    ]
    driver.device.currentValues.audioSource = 'https://music.example.test/new-track.mp3'
    driver.state.playbackState = 'PLAYBACK_STATE_PLAYING'

    when:
    driver.restoreGroupAnnouncementPlayback('external-guid')

    then:
    driver.asyncCalls.empty
    driver.runtimeState.announcementPlaybackSnapshot == null
    playerStateMessages().any { Map event ->
      event.phase == 'PLAYBACK_RESTORED' && event.skipped == 'external-playback'
    }
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
    driver.pendingLocalDeviceEvents[dni]?.containsKey('playbackState') != true
    driver.pendingLocalDeviceEvents[dni]?.containsKey('playbackStateObservedAt') != true
    playerStateMessages().any { Map event ->
      event.kind == 'playbackObservation' && event.playbackState == 'PLAYBACK_STATE_IDLE' &&
        event.playbackObservedAt == driver.state.playbackStateObservedAt
    }
  }

  def "track position publishes on playback changes and seek but ignores normal progress heartbeats"() {
    given:
    driver.settings.enableTrackPositionUpdates = true
    driver.device.dataValues.isGroupCoordinator = 'true'
    Long firstObservedAt = 100000L
    driver.metaClass.now = { -> firstObservedAt }

    when: 'the first playing status establishes the position'
    driver.setTrackPositionFromPlaybackStatus([playbackState: 'PLAYBACK_STATE_PLAYING',
      itemId: 'item-1', positionMillis: 12345L], 'PLAYBACK_STATE_PLAYING', firstObservedAt)

    then:
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].trackPosition == 12L
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].trackPositionUpdated == firstObservedAt
    driver.pendingGroupDeviceUpdates['SONOS-TEST-DNI'].trackPosition == 12L

    when: 'ordinary elapsed progress matches the elapsed wall time'
    driver.pendingLocalDeviceEvents.remove('SONOS-TEST-DNI')
    driver.pendingGroupDeviceUpdates.remove('SONOS-TEST-DNI')
    firstObservedAt = 105000L
    driver.setTrackPositionFromPlaybackStatus([playbackState: 'PLAYBACK_STATE_PLAYING',
      itemId: 'item-1', positionMillis: 17345L], 'PLAYBACK_STATE_PLAYING', firstObservedAt)

    then:
    !driver.pendingLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
    !driver.pendingGroupDeviceUpdates.containsKey('SONOS-TEST-DNI')

    when: 'a seek is reported while playback remains active'
    firstObservedAt = 106000L
    driver.setTrackPositionFromPlaybackStatus([playbackState: 'PLAYBACK_STATE_PLAYING',
      itemId: 'item-1', positionMillis: 45000L], 'PLAYBACK_STATE_PLAYING', firstObservedAt)

    then:
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].trackPosition == 45L
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].trackPositionUpdated == firstObservedAt

    when: 'the track changes'
    driver.pendingLocalDeviceEvents.remove('SONOS-TEST-DNI')
    firstObservedAt = 107000L
    driver.setTrackPositionFromPlaybackStatus([playbackState: 'PLAYBACK_STATE_PLAYING',
      itemId: 'item-2', positionMillis: 800L], 'PLAYBACK_STATE_PLAYING', firstObservedAt)

    then:
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].trackPosition == 0L
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].trackPositionUpdated == firstObservedAt

    cleanup:
    GroovySystem.metaClassRegistry.removeMetaClass(driver.getClass())
    driver.metaClass = null
  }

  def "track position updates default off when the preference is null"() {
    given:
    driver.settings.remove('enableTrackPositionUpdates')

    expect:
    driver.getEnableTrackPositionUpdates() == false

    when:
    driver.migrationCleanup()

    then:
    driver.settings.enableTrackPositionUpdates == false
  }

  def "disabling track position updates clears existing player attributes"() {
    given:
    driver.settings.enableTrackPositionUpdates = false
    driver.device.dataValues.enableTrackPositionUpdates = 'true'
    driver.device.currentValues.trackPosition = 84L
    driver.device.currentValues.trackPositionUpdated = 123456L

    when:
    driver.synchronizeTrackPositionPreference()
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.currentValues.trackPosition == 0L
    driver.device.currentValues.trackPositionUpdated == 0L
    driver.device.dataValues.enableTrackPositionUpdates == 'false'
  }

  def "disabled player position preference suppresses its events while retaining group updates"() {
    given:
    driver.settings.enableTrackPositionUpdates = false
    driver.device.dataValues.isGroupCoordinator = 'true'

    when:
    driver.setTrackPosition(64L, 123456L)

    then:
    !driver.pendingLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
    driver.pendingGroupDeviceUpdates['SONOS-TEST-DNI'].trackPosition == 64L
    driver.pendingGroupDeviceUpdates['SONOS-TEST-DNI'].trackPositionUpdated == 123456L
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
    driver.pendingLocalDeviceEvents[dni]?.containsKey('audioSourceSignalStatus') != true
    driver.pendingLocalDeviceEvents[dni]?.containsKey('audioSourceSignalObservedAt') != true
    playerStateMessages().any { Map event ->
      event.kind == 'playbackObservation' && event.signalStatus == 'no-signal'
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
    driver.flushPendingLocalDeviceEvents()

    then:
    requests.empty
    driver.device.events.findAll { Map event -> event.name == 'balance' }*.value == [10]
  }

  def "rendering control event updates master volume stereo balance mute and loudness"() {
    given:
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;' +
      '&lt;Volume channel="Master" val="42"/&gt;&lt;Volume channel="LF" val="70"/&gt;' +
      '&lt;Volume channel="RF" val="100"/&gt;&lt;Mute channel="Master" val="1"/&gt;' +
      '&lt;Loudness channel="Master" val="1"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange>' +
      '</property></propertyset>'

    when:
    driver.processRenderingControlMessages(event)
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.events.findAll { Map item -> item.name == 'volume' }*.value == [42]
    driver.device.events.findAll { Map item -> item.name == 'balance' }*.value == [6]
    driver.device.events.findAll { Map item -> item.name == 'mute' }*.value == ['muted']
    driver.device.events.findAll { Map item -> item.name == 'loudness' }*.value == ['on']
  }

  def "partial rendering control event preserves absent values and existing missing channel behavior"() {
    given:
    driver.device.currentValues.volume = 25
    driver.device.currentValues.level = 25
    driver.device.currentValues.mute = 'unmuted'
    driver.device.currentValues.loudness = 'off'
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;' +
      '&lt;Volume channel="LF" val="80"/&gt;&lt;Mute channel="Master" val="1"/&gt;' +
      '&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'

    when:
    driver.processRenderingControlMessages(event)
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.currentValue('volume') == 25
    driver.device.currentValue('mute') == 'muted'
    driver.device.currentValue('loudness') == 'off'
    driver.device.events.findAll { Map item -> item.name == 'volume' }.empty
    driver.device.events.findAll { Map item -> item.name == 'loudness' }.empty
    driver.device.events.findAll { Map item -> item.name == 'balance' }*.value == [4]
  }

  def "rendering control event with absent stereo channels keeps zero fallback balance"() {
    given:
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;' +
      '&lt;Mute channel="Master" val="0"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange>' +
      '</property></propertyset>'

    when:
    driver.processRenderingControlMessages(event)
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.events.findAll { Map item -> item.name == 'balance' }*.value == [20]
    driver.device.events.findAll { Map item -> item.name == 'mute' }*.value == ['unmuted']
  }

  def "unmute skips AirPlay volume restore when saved level is missing or invalid"() {
    given:
    driver.settings.enableAirPlayUnmuteVolumeFix = true
    driver.device.currentValues.mute = 'muted'
    if(restoreLevel != null) { driver.runtimeState.restoreLevelAfterUnmute = restoreLevel }

    when:
    driver.setMuteState('unmuted')
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.events.findAll { Map event -> event.name == 'mute' }*.value == ['unmuted']
    websocketMessages.empty
    driver.logs.any { it.toString().contains('saved restore level is unavailable or invalid') }

    where:
    restoreLevel << [null, 'invalid', 101, -1, 10.5G]
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
    driver.drainComponentChildEvents()

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
    driver.flushPendingLocalDeviceEvents()
    driver.drainComponentChildEvents()

    then:
    child.currentValue('switch') == switchState
    driver.device.events.findAll { Map event -> event.name == 'mute' }*.value ==
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

  def "missing component child retains latest value and drains promptly after recreation"() {
    given:
    String dni = driver.getMuteControlChildDNI()
    dwinks.hubitat.stubs.MockDevice eventSource = new dwinks.hubitat.stubs.MockDevice(deviceNetworkId: dni)
    dwinks.hubitat.stubs.MockDevice recreatedChild = new dwinks.hubitat.stubs.MockDevice(deviceNetworkId: dni)
    driver.children.clear()
    driver.sendChildEvent(eventSource, 'switch', 'off')
    driver.sendChildEvent(eventSource, 'switch', 'on')

    when: 'the child is temporarily absent'
    driver.drainComponentChildEvents()

    then:
    driver.state.pendingComponentChildEvents[dni] == [switch: 'on']
    driver.state.componentChildRetryAttempts[dni] == 1
    driver.scheduled.last()[0] == 200

    when: 'configuration recreates the child'
    driver.metaClass.addChildDevice = { String namespace, String type, String childDni, Map props ->
      driver.children.add(recreatedChild)
      return recreatedChild
    }
    driver.createRemoveMuteChildDevice(true)

    then:
    driver.state.pendingComponentChildEvents[dni] == [switch: 'on']
    driver.scheduled.last()[0] == 1

    when:
    driver.drainComponentChildEvents()

    then:
    recreatedChild.events*.value == ['on']
    driver.state.pendingComponentChildEvents == null
    driver.state.componentChildRetryAttempts == null

    cleanup:
    driver.children.clear()
  }

  def "transient child sendEvent failure backs off and resets after recovery"() {
    given:
    dwinks.hubitat.stubs.MockDevice child = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getMuteControlChildDNI())
    Integer failuresRemaining = 1
    child.metaClass.sendEvent = { Map event ->
      if(failuresRemaining > 0) {
        failuresRemaining--
        throw new RuntimeException('transient send failure')
      }
      child.events << event
      child.currentValues[event.name as String] = event.value
    }
    driver.children.clear()
    driver.children.add(child)
    driver.sendChildEvent(child, 'switch', 'on')

    when:
    driver.drainComponentChildEvents()

    then:
    driver.state.pendingComponentChildEvents[child.deviceNetworkId] == [switch: 'on']
    driver.state.componentChildRetryAttempts[child.deviceNetworkId] == 1
    driver.scheduled.last()[0] == 200

    when:
    driver.drainComponentChildEvents()

    then:
    child.events*.value == ['on']
    driver.state.pendingComponentChildEvents == null
    driver.state.componentChildRetryAttempts == null

    cleanup:
    driver.children.clear()
  }

  def "component child retry delay grows exponentially and stays capped"() {
    given:
    String dni = driver.getMuteControlChildDNI()
    dwinks.hubitat.stubs.MockDevice eventSource = new dwinks.hubitat.stubs.MockDevice(deviceNetworkId: dni)
    driver.children.clear()
    driver.sendChildEvent(eventSource, 'switch', 'on')

    when:
    10.times {
      driver.scheduled.clear()
      driver.drainComponentChildEvents()
    }

    then:
    driver.state.componentChildRetryAttempts[dni] == 10
    driver.scheduled.last()[0] == 30000

    when:
    driver.configure()
    driver.scheduled.clear()
    driver.drainComponentChildEvents()

    then:
    driver.state.componentChildRetryAttempts[dni] == 1
    driver.scheduled.last()[0] == 200
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

  def "sleep timer preference creates a stable player child and removes it when disabled"() {
    given:
    driver.children.clear()

    when:
    driver.createRemoveSleepTimerChildDevice(true)
    driver.createRemoveSleepTimerChildDevice(true)

    then:
    driver.children.size() == 1
    driver.children[0].deviceNetworkId == 'SONOS-TEST-DNI-SleepTimer'
    driver.children[0].dataValues.playerDni == 'SONOS-TEST-DNI'

    when:
    driver.createRemoveSleepTimerChildDevice(false)

    then:
    driver.children.isEmpty()
  }

  def "sleep timer commands use AVTransport and query response updates child state"() {
    given:
    driver.children.clear()
    driver.createRemoveSleepTimerChildDevice(true)
    driver.asyncCalls.clear()

    when:
    driver.setSleepTimer('01:25:30')
    Map setRequest = driver.asyncCalls.last()

    then:
    setRequest.cb == 'sleepTimerSetCallback'
    setRequest.params.headers.SOAPAction.endsWith('#ConfigureSleepTimer')
    setRequest.params.body.contains('<NewSleepTimerDuration>01:25:30</NewSleepTimerDuration>')

    when:
    driver.setSleepTimer('01:99:00')
    driver.cancelSleepTimer()
    Map cancelRequest = driver.asyncCalls.last()

    then:
    driver.asyncCalls.size() == 2
    cancelRequest.params.headers.SOAPAction.endsWith('#ConfigureSleepTimer')
    cancelRequest.params.body.contains('<NewSleepTimerDuration></NewSleepTimerDuration>')

    when:
    driver.asyncCalls.clear()
    driver.getSleepTimer()
    Map queryRequest = driver.asyncCalls.last()
    driver.sleepTimerGetCallback(new hubitat.scheduling.AsyncResponse(status: 200,
      data: '<GetRemainingSleepTimerDurationResponse><RemainingSleepTimerDuration>00:12:34</RemainingSleepTimerDuration><CurrentSleepTimerGeneration>7</CurrentSleepTimerGeneration></GetRemainingSleepTimerDurationResponse>'), queryRequest.data)

    then:
    queryRequest.params.headers.SOAPAction.endsWith('#GetRemainingSleepTimerDuration')
    driver.children[0].currentValues.remainingDuration == '00:12:34'
    driver.children[0].currentValues.sleepTimerState == 'active'
    driver.children[0].currentValues.generation == '7'

    when:
    driver.sleepTimerGetCallback(new hubitat.scheduling.AsyncResponse(status: 200,
      data: '<GetRemainingSleepTimerDurationResponse><RemainingSleepTimerDuration></RemainingSleepTimerDuration><CurrentSleepTimerGeneration>0</CurrentSleepTimerGeneration></GetRemainingSleepTimerDurationResponse>'), null)

    then:
    driver.children[0].currentValues.remainingDuration == '00:00:00'
    driver.children[0].currentValues.sleepTimerState == 'inactive'
    driver.children[0].currentValues.generation == '0'
  }

  def "sleep timer AVTransport events normalize running state and generation"() {
    given:
    driver.children.clear()
    driver.createRemoveSleepTimerChildDevice(true)
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;&lt;SleepTimerState val="SLEEP_TIMER_RUNNING"/&gt;&lt;SleepTimerGeneration val="9"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'

    when:
    driver.processAVTransportMessages(event, 'http://192.168.1.50:1400')

    then:
    driver.children[0].currentValues.sleepTimerState == 'active'
    driver.children[0].currentValues.generation == '9'
  }

  def "AVTransport states publish only supported normalized local and group values"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.pendingLocalDeviceEvents.remove(dni)
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;&lt;TransportState val="' +
      sonosState + '"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'

    when:
    driver.processAVTransportMessages(event, 'http://192.168.1.50:1400')

    then:
    driver.pendingLocalDeviceEvents[dni].status == expectedStatus
    driver.pendingLocalDeviceEvents[dni].transportStatus == expectedStatus
    driver.pendingGroupDeviceUpdates[dni].status == expectedStatus
    driver.pendingGroupDeviceUpdates[dni].transportStatus == expectedStatus

    where:
    sonosState       | expectedStatus
    'PLAYING'        | 'playing'
    'PAUSED_PLAYBACK'| 'paused'
    'STOPPED'        | 'stopped'
    'NO_MEDIA_PRESENT' | 'stopped'
  }

  def "TRANSITIONING preserves the last valid local and group transport state"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.device.currentValues.status = 'paused'
    driver.device.currentValues.transportStatus = 'paused'
    driver.pendingLocalDeviceEvents.remove(dni)
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;&lt;TransportState val="TRANSITIONING"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'

    when:
    driver.processAVTransportMessages(event, 'http://192.168.1.50:1400')

    then:
    driver.device.currentValue('status') == 'paused'
    driver.device.currentValue('transportStatus') == 'paused'
    driver.pendingLocalDeviceEvents[dni] == null
    driver.pendingGroupDeviceUpdates[dni] == null
  }

  def "unknown AVTransport state is logged and not sent to local or group devices"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.device.currentValues.status = 'playing'
    driver.device.currentValues.transportStatus = 'playing'
    driver.pendingLocalDeviceEvents.remove(dni)
    String event = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;&lt;TransportState val="BUFFERING_UNKNOWN"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'

    when:
    driver.processAVTransportMessages(event, 'http://192.168.1.50:1400')

    then:
    driver.pendingLocalDeviceEvents[dni]?.containsKey('status') != true
    driver.pendingLocalDeviceEvents[dni]?.containsKey('transportStatus') != true
    driver.pendingGroupDeviceUpdates[dni]?.containsKey('status') != true
    driver.pendingGroupDeviceUpdates[dni]?.containsKey('transportStatus') != true
    driver.logs.any { it.toString().contains("unsupported Sonos TransportState='BUFFERING_UNKNOWN'") }
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
        connect: { Object... args -> websocketConnections << args.toList() },
        close: { -> }
      )
    ])
    rawSocket = driver.binding.getVariable('interfaces').rawSocket
  }

  def setup() {
    parentPlayerUpdates.clear()
    parentPlayerRequests.clear()
    driver.binding.getVariable('interfaces').rawSocket = rawSocket
    driver.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: true]
    driver.logs.clear()
    driver.asyncCalls.clear()
    driver.events.clear()
    driver.locationEvents.clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    websocketMessages.clear()
    websocketConnections.clear()
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
    driver.metaClass.getParent = { -> new Expando(
      playerStateUpdate: { Map payload -> parentPlayerUpdates << new LinkedHashMap(payload) },
      playerAppRequest: { Map payload -> parentPlayerRequests << new LinkedHashMap(payload) }
    ) }
    driver.device.dataValues.localUpnpHost = '192.168.1.50:1400'
    driver.device.dataValues.websocketUrl = 'wss://192.168.1.50:1443/websocket/api'
    driver.location.metaClass.getHub = { -> new Expando(localIP: '192.168.1.4', localSrvPortTCP: 39501) }
    driver.upnpRequestQueues.clear()
    driver.eventTimestamps.clear()
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
    driver.pendingTopologySnapshotByDni.clear()
    driver.pendingTopologyWebsocketByDni.clear()
    driver.pendingTopologyKindByDni.clear()
    driver.pendingLocalDeviceEvents.clear()
    driver.lastLocalDeviceEvents.clear()
    driver.lastPlaybackState.clear()
    driver.lastMetadataContainerId.clear()
  }

  def "websocket connect requests the parent initialization turn when not granted"() {
    given:
    driver.device.dataValues.websocketStatus = 'closed'

    when:
    driver.wsConnect()

    then:
    websocketConnections.empty
    playerStateMessages().any { Map event ->
      event.kind == 'initializationRequested' && event.reason.contains('outside app initialization turn')
    }
  }

  def "duplicate open callback does not replay subscription setup"() {
    given:
    driver.device.dataValues.websocketStatus = 'open'
    driver.runtimeState.websocketConnectionGeneration = 4L

    when:
    driver.webSocketStatus('status: open')

    then:
    driver.runtimeState.websocketConnectionGeneration == 4L
    driver.runtimeState.websocketConnectPending == null
    driver.logs.any { it.toString().contains('Ignoring duplicate primary WebSocket open callback') }
  }

  def "closing callback from replaced socket does not cancel pending connect"() {
    given:
    driver.device.dataValues.websocketStatus = 'connecting'
    driver.runtimeState.websocketConnectPending = true

    when:
    driver.webSocketStatus('status: closing')

    then:
    driver.device.getDataValue('websocketStatus') == 'connecting'
    driver.runtimeState.websocketConnectPending == true
    !driver.scheduled.any { List job -> job[1] == 'retryWebSocketConnection' }
  }

  def "driver initialize only asks the parent app for an initialization turn"() {
    given:
    driver.device.currentValues.sonosPlayerStateUpdate = '{"kind":"old"}'
    driver.device.currentValues.sonosPlayerAppRequest = '{"action":"old"}'

    when:
    driver.initialize()

    then:
    playerStateMessages().size() == 1
    playerStateMessages()[0].kind == 'initializationRequested'
    playerStateMessages()[0].reason == 'driver initialize'
    playerStateMessages()[0].clearPendingCommands == true
    !driver.device.currentValues.containsKey('sonosPlayerStateUpdate')
    !driver.device.currentValues.containsKey('sonosPlayerAppRequest')
    !driver.events.any { Map event -> event.name in ['sonosPlayerStateUpdate', 'sonosPlayerAppRequest'] }
    driver.scheduled.empty
    websocketConnections.empty
  }

  def "subscription renewal requests do not purge pending player commands"() {
    given:
    driver.runtimeState.transportCommandQueue = [[command: 'play']]
    driver.runtimeState.pendingParentCoordinatorCommands = [[command: 'pause']]
    driver.state.pendingParentAppTtsRequests = [tts: [uri: 'http://example.test/tts.mp3']]

    when:
    driver.requestFullSubscriptionRenewal(0, 'full subscription renewal')

    then:
    driver.runtimeState.transportCommandQueue.size() == 1
    driver.runtimeState.pendingParentCoordinatorCommands.size() == 1
    driver.state.pendingParentAppTtsRequests.size() == 1
    playerStateMessages().last().clearPendingCommands == null
  }

  def "parent announcement purge removes only the matching clip correlation"() {
    given:
    driver.audioClipQueueInitialization()
    driver.getDeviceAudioClipQueue().addAll([
      [correlationId: 'announcement'], [correlationId: 'unrelated']
    ])
    driver.getDeviceAudioClipQueueHighPriority().add([correlationId: 'announcement'])
    driver.runtimeState.websocketOutboundQueue = [
      [kind: 'audioClip', correlationId: 'announcement'],
      [kind: 'audioClip', correlationId: 'unrelated']
    ]
    driver.runtimeState.activeAudioClipMessage = [correlationId: 'announcement']
    driver.runtimeState.activeAudioClipCorrelationId = 'announcement'
    driver.runtimeState.audioClipPlaying = true

    when:
    driver.clearPendingAudioClipCorrelation('announcement')

    then:
    driver.getDeviceAudioClipQueue()*.correlationId == ['unrelated']
    driver.getDeviceAudioClipQueueHighPriority().isEmpty()
    driver.runtimeState.websocketOutboundQueue*.correlationId == ['unrelated']
    !driver.runtimeState.activeAudioClipCorrelationId
    driver.runtimeState.audioClipPlaying == false
  }

  def "parent initialization reports start before scheduling recovery work"() {
    given:
    driver.device.dataValues.appInitializationRequestId = 'request-1'

    when:
    driver.beginAppMediatedInitialization('request-1')

    then:
    playerStateMessages()[0].kind == 'initializationStarted'
    playerStateMessages()[0].requestId == 'request-1'
    driver.logs.any { it.contains('Starting app-mediated initialization') }
    driver.scheduled.any { List job -> job[1] == 'initializeWebsocketConnection' }
  }

  def cleanup() {
    GroovySystem.metaClassRegistry.removeMetaClass(driver.getClass())
    driver.metaClass = null
  }

  def "local event enqueue re-arms a drain when its scheduled marker is stale"() {
    given:
    driver.state.localDeviceEventDrainScheduled = true

    when:
    driver.queueLocalDeviceEvent('isGrouped', 'on')

    then:
    driver.scheduled.any { List item -> item[1] == 'flushPendingLocalDeviceEvents' }
    driver.state.localDeviceEventDrainScheduled == true
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].isGrouped == 'on'
  }

  def "local event rescheduling preserves the original maximum-wait deadline"() {
    given:
    List<Long> clock = [1000L]
    driver.metaClass.now = { -> clock[0] }

    when:
    driver.queueLocalDeviceEvent('groupMemberCount', 2)
    Long firstDueAt = driver.state.localDeviceEventDrainDueAt as Long
    clock[0] = 1300L
    driver.queueLocalDeviceEvent('groupMemberCount', 1)

    then:
    firstDueAt == 1500L
    driver.state.localDeviceEventDrainDueAt == firstDueAt
    driver.scheduled.last()[0] == 200
    driver.scheduled.last()[1] == 'flushPendingLocalDeviceEvents'
  }

  def "local event drain schedules values enqueued while it sends a batch"() {
    given:
    driver.queueLocalDeviceEvent('groupName', 'Cora’s Room + 1')
    driver.device.metaClass.sendEvent = { Map event ->
      delegate.events << event
      delegate.currentValues[event.name as String] = event.value
      if(event.name == 'groupName') {
        driver.queueLocalDeviceEvent('groupMemberCount', 2)
      }
    }

    when:
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.currentValues.groupName == 'Cora’s Room + 1'
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].groupMemberCount == 2
    driver.scheduled.last()[1] == 'flushPendingLocalDeviceEvents'

    when:
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.currentValues.groupMemberCount == 2
    !driver.pendingLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
  }

  def "failed local event remains queued and is delivered on a later drain"() {
    given:
    Integer attempts = 0
    driver.queueLocalDeviceEvent('isGrouped', 'on')
    driver.device.metaClass.sendEvent = { Map event ->
      attempts++
      if(attempts == 1) { throw new IllegalStateException('temporary event failure') }
      delegate.events << event
      delegate.currentValues[event.name as String] = event.value
    }

    when:
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].isGrouped == 'on'
    driver.scheduled.last()[1] == 'flushPendingLocalDeviceEvents'

    when:
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.currentValues.isGrouped == 'on'
    !driver.pendingLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
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

  def "expired renewal accepts the replacement SID returned by a fresh subscribe"() {
    given:
    driver.setSid('sid1', 'uuid:expired')
    driver.eventTimestamps.put('SONOS-TEST-DNI-sid1-expires', Instant.now().getEpochSecond() - 60L)

    when:
    driver.upnpResubscribeGeneric('resubscribeToAVTransport', 'sid1', '/avt', 'callback',
      '/MediaRenderer/AVTransport/Event')

    then:
    rawRequests[0].startsWith('SUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1')
    rawRequests[0].contains('CALLBACK: <http://192.168.1.4:39501/avt>')
    !rawRequests[0].contains('SID: uuid:expired')
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.expectedCurrentSid == 'uuid:expired'
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.sid == null

    when:
    rawConnections[0].options.inputReader.call(
      'HTTP/1.1 200 OK\r\nSID: uuid:replacement\r\nTIMEOUT: Second-1800\r\n\r\n')

    then:
    driver.getSid('sid1') == 'uuid:replacement'
    driver.device.dataValues['sid1-expires'] != null
    driver.eventTimestamps.get('SONOS-TEST-DNI-sid1-expires') > Instant.now().getEpochSecond()
    driver.scheduled.any { List row -> row[1] == 'resubscribeToAVTransport' }
    !driver.logs.any { String line -> line.contains('Ignoring UPnP SID update') }
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active == null
  }

  def "late fresh subscribe retry cannot replace a newer SID owner"() {
    given:
    driver.setSid('sid1', 'uuid:expired')
    driver.eventTimestamps.put('SONOS-TEST-DNI-sid1-expires', Instant.now().getEpochSecond() - 60L)

    when:
    driver.upnpResubscribeGeneric('resubscribeToAVTransport', 'sid1', '/avt', 'callback',
      '/MediaRenderer/AVTransport/Event')
    rawConnections[0].options.inputReader.call('HTTP/1.1 500 Failure\r\n\r\n')
    driver.setSid('sid1', 'uuid:newer-owner')
    Map retryData = driver.scheduled.find { List row -> row[1] == 'retryUpnpSid1' }[2].data as Map
    driver.retryUpnpSid1(retryData)

    then:
    rawRequests.size() == 2
    rawRequests[1].startsWith('SUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1')
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.expectedCurrentSid == 'uuid:expired'

    when:
    rawConnections[1].options.inputReader.call(
      'HTTP/1.1 200 OK\r\nSID: uuid:stale-response\r\nTIMEOUT: Second-1800\r\n\r\n')

    then:
    driver.getSid('sid1') == 'uuid:newer-owner'
    driver.logs.any { String line -> line.contains('Ignoring UPnP SID update') }
    !driver.scheduled.any { List row -> row[1] == 'resubscribeToAVTransport' }
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

  def "changed topology payload is snapshotted once and applied from that snapshot"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    Integer parseCalls = 0
    driver.metaClass.parseZoneGroupTopologySnapshot = { String xml, String playerId ->
      parseCalls++
      [playerFound: true, coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST', 'RINCON_MEMBER'],
       visibleMemberIds: ['RINCON_TEST', 'RINCON_MEMBER'], memberNames: ['Living Room', 'Kitchen'],
       coordinatorName: 'Living Room', groupName: 'Downstairs', batteryData: [:]]
    }
    driver.metaClass.processZoneGroupTopologySnapshot = { Map topology, LinkedHashSet oldMembers ->
      driver.runtimeState.appliedTopologySnapshot = topology
      true
    }

    when:
    driver.deferOrProcessZoneGroupTopologyPayload('SONOS-TEST-DNI', '<topology>changed</topology>')

    then:
    parseCalls == 1
    driver.runtimeState.appliedTopologySnapshot.coordinatorId == 'RINCON_TEST'
    driver.runtimeState.appliedTopologySnapshot.memberNames == ['Living Room', 'Kitchen']
    driver.lastZgtXmlHash['SONOS-TEST-DNI'] == '<topology>changed</topology>'.hashCode()

    when:
    driver.deferOrProcessZoneGroupTopologyPayload('SONOS-TEST-DNI', '<topology>changed</topology>')

    then:
    parseCalls == 1
  }

  def "topology snapshot extracts visible roster names coordinator group and battery once"() {
    given:
    String zoneState = '<ZoneGroups><ZoneGroup Coordinator="RINCON_A">' +
      '<ZoneGroupMember UUID="RINCON_B" ZoneName="Kitchen" MoreInfo="BattTmp:23,BattPct:80,BattChg:NOT_CHARGING"/>' +
      '<ZoneGroupMember UUID="RINCON_A" ZoneName="Living Room"/>' +
      '<ZoneGroupMember UUID="RINCON_H" ZoneName="Hidden" Invisible="1"/>' +
      '</ZoneGroup></ZoneGroups>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String xml = '<propertyset><property><ZoneGroupName>Downstairs</ZoneGroupName><ZoneGroupState>' +
      escapedState + '</ZoneGroupState></property></propertyset>'

    when:
    Map topology = driver.parseZoneGroupTopologySnapshot(xml, 'RINCON_B')

    then:
    topology.playerFound == true
    topology.coordinatorId == 'RINCON_A'
    topology.coordinatorName == 'Living Room'
    topology.memberIds == ['RINCON_A', 'RINCON_B']
    topology.visibleMemberIds == ['RINCON_B', 'RINCON_A']
    topology.memberNames == ['Kitchen', 'Living Room']
    topology.groupName == 'Downstairs'
    topology.batteryData == [BattTmp: '23', BattPct: '80', BattChg: 'NOT_CHARGING']
  }

  def "unchanged coordinator ID repairs a stale coordinator role flag"() {
    given:
    driver.device.dataValues.id = 'RINCON_FOLLOW'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.isGroupCoordinator = 'true'

    when:
    driver.setGroupCoordinatorId('RINCON_COORD')

    then:
    driver.device.dataValues.groupCoordinatorId == 'RINCON_COORD'
    driver.device.dataValues.isGroupCoordinator == 'false'
  }

  def "unchanged internal coordinator values repair stale public attributes"() {
    given:
    driver.device.dataValues.id = 'RINCON_FOLLOW'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.isGroupCoordinator = 'false'
    driver.device.currentValues.groupCoordinatorId = 'RINCON_OLD'
    driver.device.currentValues.isGroupCoordinator = 'on'

    when:
    driver.setGroupCoordinatorId('RINCON_COORD')

    then:
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].groupCoordinatorId == 'RINCON_COORD'
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].isGroupCoordinator == 'off'
    driver.scheduled.any { List call -> call[1] == 'flushPendingLocalDeviceEvents' }
  }

  def "batch event enqueue re-arms a drain when the scheduled marker is stale"() {
    given:
    driver.state.pendingBatchEvents = [status: 'playing']
    driver.state.batchEventsScheduled = true

    when:
    driver.applyStateBatch('{"transportStatus":"playing"}')

    then:
    driver.state.batchEventsScheduled == true
    driver.scheduled.any { List call -> call[1] == 'drainBatchEvents' }
  }

  def "batch drain preserves values re-enqueued while sending the detached batch"() {
    given:
    driver.applyStateBatch('{"status":"playing"}')
    driver.device.metaClass.sendEvent = { Map event ->
      delegate.events << event
      delegate.currentValues[event.name as String] = event.value
      if(event.name == 'status') { driver.applyStateBatch('{"status":"stopped"}') }
    }

    when:
    driver.drainBatchEvents()

    then:
    driver.state.pendingBatchEvents.status == 'stopped'
    driver.state.batchEventsScheduled == true
    driver.scheduled.any { List call -> call[1] == 'drainBatchEvents' }
  }

  def "publication watchdog repairs missing batch and local drain jobs"() {
    given:
    driver.scheduled.clear()
    driver.state.pendingBatchEvents = [status: 'playing']
    driver.state.batchEventsScheduled = true
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'] = [groupCoordinatorId: 'RINCON_COORD']
    driver.state.localDeviceEventDrainScheduled = true

    when:
    driver.verifyPendingPublicationDrains()

    then:
    driver.scheduled.any { List call -> call[1] == 'drainBatchEvents' }
    driver.scheduled.any { List call -> call[1] == 'flushPendingLocalDeviceEvents' }
    driver.scheduled.any { List call -> call[1] == 'verifyPendingPublicationDrains' }
  }

  def "publication watchdog clears stale drain markers when their queues are empty"() {
    given:
    driver.scheduled.clear()
    driver.state.pendingBatchEvents = [:]
    driver.state.batchEventsScheduled = true
    driver.state.batchEventsDueAt = 1L
    driver.state.localDeviceEventDrainScheduled = true
    driver.state.localDeviceEventDrainDueAt = 1L
    driver.pendingLocalDeviceEvents.remove('SONOS-TEST-DNI')

    when:
    driver.verifyPendingPublicationDrains()

    then:
    !driver.state.containsKey('pendingBatchEvents')
    !driver.state.containsKey('batchEventsScheduled')
    !driver.state.containsKey('batchEventsDueAt')
    !driver.state.containsKey('localDeviceEventDrainScheduled')
    !driver.state.containsKey('localDeviceEventDrainDueAt')
    !driver.scheduled.any { List call -> call[1] == 'verifyPendingPublicationDrains' }
  }

  def "initialization reset clears only this player's stale publication queues"() {
    given:
    String otherDni = 'SONOS-OTHER-DNI'
    driver.state.pendingBatchEvents = [status: 'stopped']
    driver.state.batchEventsScheduled = true
    driver.state.pendingComponentChildEvents = ['child-dni': [switch: 'on']]
    driver.state.componentChildDrainScheduled = true
    driver.state.groupDeviceUpdateScheduled = true
    driver.state.localDeviceEventDrainScheduled = true
    driver.state.localDeviceEventDrainDueAt = 123L
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'] = [groupCoordinatorId: 'RINCON_OLD']
    driver.lastLocalDeviceEvents['SONOS-TEST-DNI'] = [isGroupCoordinator: 'on']
    driver.pendingLocalDeviceEvents[otherDni] = [status: 'playing']
    driver.pendingGroupDeviceUpdates['SONOS-TEST-DNI'] = [status: 'stopped']
    driver.scheduled << [1000, 'drainBatchEvents']
    driver.scheduled << [1000, 'flushPendingLocalDeviceEvents']
    driver.scheduled << [1000, 'drainComponentChildEvents']
    driver.scheduled << [1000, 'flushPendingGroupDeviceUpdates']

    when:
    driver.resetPendingStatePublicationForInitialization()

    then:
    !driver.state.containsKey('pendingBatchEvents')
    !driver.state.containsKey('batchEventsScheduled')
    !driver.state.containsKey('pendingComponentChildEvents')
    !driver.state.containsKey('componentChildDrainScheduled')
    !driver.state.containsKey('groupDeviceUpdateScheduled')
    !driver.state.containsKey('localDeviceEventDrainScheduled')
    !driver.pendingLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
    !driver.lastLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
    !driver.pendingGroupDeviceUpdates.containsKey('SONOS-TEST-DNI')
    driver.pendingLocalDeviceEvents[otherDni].status == 'playing'
    ['drainBatchEvents', 'flushPendingLocalDeviceEvents', 'drainComponentChildEvents',
     'flushPendingGroupDeviceUpdates'].every { String method -> driver.unschedules.contains(method) }
  }

  def "topology snapshot includes every household group for the app ZGT source"() {
    given:
    String zoneState = '<ZoneGroupState><ZoneGroups>' +
      '<ZoneGroup ID="G1" Coordinator="RINCON_A">' +
      '<ZoneGroupMember UUID="RINCON_A" ZoneName="Living Room"/>' +
      '<ZoneGroupMember UUID="RINCON_B" ZoneName="Kitchen"/>' +
      '</ZoneGroup>' +
      '<ZoneGroup ID="G2" Coordinator="RINCON_C" ZoneGroupName="Office">' +
      '<ZoneGroupMember UUID="RINCON_C" ZoneName="Office"/>' +
      '</ZoneGroup>' +
      '<ZoneGroup ID="G3" Coordinator="RINCON_D">' +
      '<ZoneGroupMember UUID="RINCON_D" ZoneName="Bedroom"/>' +
      '</ZoneGroup>' +
      '</ZoneGroups></ZoneGroupState>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String xml = '<propertyset><property><ZoneGroupName>Downstairs</ZoneGroupName><ZoneGroupState>' +
      escapedState + '</ZoneGroupState></property></propertyset>'

    when:
    Map topology = driver.parseZoneGroupTopologySnapshot(xml, 'RINCON_B')

    then:
    topology.playerFound == true
    topology.groups.size() == 3
    topology.groups*.groupId == ['G1', 'G2', 'G3']
    topology.groups[0].playerIds == ['RINCON_A', 'RINCON_B']
    topology.groups[1].coordinatorId == 'RINCON_C'
    topology.groups[1].playerIds == ['RINCON_C']
    topology.groups*.groupName == ['Downstairs', 'Office', 'Bedroom']
    topology.groupName == 'Downstairs'
  }

  def "household ZGT reporter name is not reused for other groups"() {
    given:
    String zoneState = '<ZoneGroups>' +
      '<ZoneGroup ID="G-CORA" Coordinator="RINCON_CORA">' +
      '<ZoneGroupMember UUID="RINCON_CORA" ZoneName="Cora’s Room"/>' +
      '</ZoneGroup>' +
      '<ZoneGroup ID="G-ARC" Coordinator="RINCON_ARC">' +
      '<ZoneGroupMember UUID="RINCON_ARC" ZoneName="Arc"/>' +
      '</ZoneGroup>' +
      '<ZoneGroup ID="G-KITCHEN" Coordinator="RINCON_KITCHEN">' +
      '<ZoneGroupMember UUID="RINCON_KITCHEN" ZoneName="Kitchen"/>' +
      '</ZoneGroup>' +
      '</ZoneGroups>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String xml = '<propertyset><property><ZoneGroupName>Cora’s Room</ZoneGroupName><ZoneGroupState>' +
      escapedState + '</ZoneGroupState></property></propertyset>'

    when:
    Map topology = driver.parseZoneGroupTopologySnapshot(xml, 'RINCON_CORA')

    then:
    topology.groupName == 'Cora’s Room'
    topology.groups.collectEntries { Map group -> [(group.groupId): group.groupName] } == [
      'G-CORA': 'Cora’s Room', 'G-ARC': 'Arc', 'G-KITCHEN': 'Kitchen'
    ]
  }

  def "ZGT notification with unrelated service properties still publishes household topology"() {
    given:
    driver.device.dataValues.appZgtSubscribe = 'true'
    driver.device.dataValues.appZgtLeaderGeneration = 'leader-test'
    String zoneState = '<ZoneGroups><ZoneGroup ID="G1" Coordinator="RINCON_TEST">' +
      '<ZoneGroupMember UUID="RINCON_TEST" ZoneName="Test Speaker"/>' +
      '</ZoneGroup></ZoneGroups>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String body = '<propertyset><property><ThirdPartyMediaServersX>1</ThirdPartyMediaServersX></property>' +
      '<property><AvailableSoftwareUpdate>0</AvailableSoftwareUpdate></property>' +
      '<property><ZoneGroupName>Household</ZoneGroupName><ZoneGroupState>' + escapedState +
      '</ZoneGroupState></property></propertyset>'
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /zgt HTTP/1.1\r\n' +
      'X-SONOS-SERVICETYPE: ZoneGroupTopology\r\n' +
      'SID: uuid:test-zgt\r\n' +
      'Content-Length: ' + body.getBytes('UTF-8').length + '\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['X-SONOS-SERVICETYPE': 'ZoneGroupTopology', 'NOTIFY /zgt HTTP/1.1': ''], body: body]
    }
    driver.metaClass.processZoneGroupTopologySnapshot = { Map topology, LinkedHashSet oldMembers -> true }

    when:
    driver.parse(raw)

    then:
    parentPlayerUpdates.any { Map event ->
      event.kind == 'zgtTopology' && event.leaderGeneration == 'leader-test' &&
        event.topology.groups.size() == 1 && event.topology.groups[0].groupId == 'G1'
    }
  }

  def "only app-elected ZGT player subscribes to ZoneGroupTopology"() {
    given:
    List<String> zgtCalls = []
    driver.metaClass.subscribeToZgtEvents = { -> zgtCalls << 'subscribe' }
    driver.metaClass.unsubscribeFromZgtEvents = { -> zgtCalls << 'unsubscribe' }
    driver.metaClass.subscribeToMrRcEvents = { -> }
    driver.metaClass.markAppMediatedUpnpSubscriptionsStarted = { -> }
    driver.device.dataValues.appZgtSubscribe = 'false'

    when:
    driver.subscribeToEvents()
    driver.device.dataValues.appZgtSubscribe = 'true'
    driver.device.dataValues.appZgtLeaderGeneration = 'leader-1'
    driver.subscribeToEvents()

    then:
    zgtCalls == ['unsubscribe', 'subscribe']
  }

  def "topology change holds only affected player until its expected topology arrives"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    // The parent app stamps expectations before dispatching the mutation.
    driver.device.dataValues.expectedGroupTopology = JsonOutput.toJson([
      coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST'], expiresAt: driver.now() + 30000L
    ])
    driver.metaClass.parseZoneGroupTopologySnapshot = { String xml, String playerId ->
      xml == '<topology>final</topology>'
        ? [playerFound: true, coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST'], visibleMemberIds: ['RINCON_TEST'], memberNames: []]
        : [playerFound: true, coordinatorId: 'RINCON_OTHER', memberIds: ['RINCON_OTHER'], visibleMemberIds: ['RINCON_OTHER'], memberNames: []]
    }
    driver.metaClass.processZoneGroupTopologySnapshot = { Map topology, LinkedHashSet oldMembers ->
      String xml = topology.coordinatorId == 'RINCON_TEST' ? '<topology>final</topology>' : '<topology>intermediate</topology>'
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
    driver.metaClass.parseZoneGroupTopologySnapshot = { String xml, String playerId ->
      [playerFound: true, coordinatorId: 'RINCON_UNRELATED', memberIds: ['RINCON_UNRELATED'],
       visibleMemberIds: ['RINCON_UNRELATED'], memberNames: []]
    }
    driver.metaClass.processZoneGroupTopologySnapshot = { Map topology, LinkedHashSet oldMembers ->
      String xml = '<topology>unrelated-current</topology>'
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
    driver.pendingTopologySnapshotByDni['SONOS-TEST-DNI'] = [playerFound: true,
      coordinatorId: 'RINCON_TEST', memberIds: ['RINCON_TEST'], visibleMemberIds: ['RINCON_TEST'], memberNames: []]
    driver.pendingTopologyKindByDni['SONOS-TEST-DNI'] = 'zgt'
    Integer parseCalls = 0
    driver.metaClass.parseZoneGroupTopologySnapshot = { String xml, String playerId ->
      parseCalls++
      null
    }
    driver.metaClass.processZoneGroupTopologySnapshot = { Map topology, LinkedHashSet oldMembers ->
      driver.runtimeState.appliedTopologyXml = '<topology>latest</topology>'
      true
    }

    when:
    driver.flushPendingTopologyUpdates()

    then:
    driver.runtimeState.appliedTopologyXml == '<topology>latest</topology>'
    !driver.pendingTopologyXmlByDni.containsKey('SONOS-TEST-DNI')
    !driver.pendingTopologySnapshotByDni.containsKey('SONOS-TEST-DNI')
    driver.device.dataValues.expectedGroupTopology == ''
    parseCalls == 0
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
    Map topologyReport = playerStateMessages().find { Map event -> event.kind == 'topology' }
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

  def "raw diagnostic payloads are logged only at trace level"() {
    given:
    String xml = '<ZoneGroupState>' + ('x' * 2400) + '</ZoneGroupState>'
    driver.logs.clear()
    driver.settings.logLevel = 'info'

    when:
    driver.logZgtXmlAtTrace('TEST-DNI', xml)

    then:
    driver.logs.empty

    when:
    driver.settings.logLevel = 'debug'
    driver.logZgtXmlAtTrace('TEST-DNI', xml)

    then:
    driver.logs.size() == 2
    driver.logs.every { String entry -> entry.contains('XML body omitted') && !entry.contains(xml) }

    when:
    driver.logs.clear()
    driver.settings.logLevel = 'trace'
    driver.logZgtXmlAtTrace('TEST-DNI', xml)
    List<Map> chunks = driver.logs.findAll { String entry ->
      entry.startsWith('TRACE Test Device: UPnP ZGT NOTIFY XML;')
    }.collect { String entry ->
      List<String> marker = (entry =~ /chunk=(\d+)\/(\d+)/)[0] as List<String>
      [index: marker[1] as Integer, data: entry.substring(entry.indexOf(' data=') + 6)]
    }.sort { Map left, Map right -> left.index <=> right.index }

    then:
    chunks.size() > 1
    chunks.every { Map chunk -> chunk.data.toString().length() <= 900 }
    chunks.collect { Map chunk -> chunk.data }.join() == xml
    driver.logs.findAll { String entry -> entry.contains('UPnP ZGT NOTIFY XML;') }
      .every { String entry -> entry.length() < 1400 }
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

  def "done watchdog schedules when queue start time is missing"() {
    given:
    driver.runtimeState.activeAudioClipCorrelationId = 'active-clip'
    driver.runtimeState.remove('audioClipQueueStartTime')

    when:
    driver.scheduleAudioClipDoneWatchdog('active-clip', 1000L, 10)

    then:
    driver.scheduled.size() == 1
    driver.scheduled[0][1] == 'audioClipWatchdog'
    driver.scheduled[0][2].data.correlationId == 'active-clip'
    driver.scheduled[0][2].data.phase == 'WAITING_FOR_DONE'
    !driver.runtimeState.containsKey('audioClipQueueStartTime')
    driver.runtimeState.audioClipQueueTotalDuration > 0
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

  def "random ID selection samples entries from sparse ID lists"() {
    given:
    List<String> favoriteIds = ['3', '40', '9001']
    List<String> playlistIds = ['playlist-2', 'playlist-98', 'playlist-10004']

    expect:
    (0..<100).every { driver.selectRandomAvailableId(favoriteIds) in favoriteIds }
    (0..<100).every { driver.selectRandomAvailableId(playlistIds) in playlistIds }
    driver.selectRandomAvailableId([]) == null
  }

  def "loadFavorite without an ID chooses an available sparse favorite ID"() {
    given:
    driver.getFavoritesMap()['object-1'] = [id: '3', name: 'First']
    driver.getFavoritesMap()['object-2'] = [id: '9001', name: 'Second']

    when:
    driver.loadFavorite()

    then:
    List payload = (List)new JsonSlurper().parseText(websocketMessages.find { String message -> message.contains('"command":"loadFavorite"') })
    payload[1].favoriteId in ['3', '9001']
  }

  def "loadPlaylist without an ID chooses an available sparse playlist ID"() {
    given:
    driver.getPlaylistsMap()['playlist-2'] = [id: 'playlist-2', name: 'First']
    driver.getPlaylistsMap()['playlist-10004'] = [id: 'playlist-10004', name: 'Second']

    when:
    driver.loadPlaylist()

    then:
    List payload = (List)new JsonSlurper().parseText(websocketMessages.find { String message -> message.contains('"command":"loadPlaylist"') })
    payload[1].playlistId in ['playlist-2', 'playlist-10004']
  }

  def "random favorite load waits for the asynchronous favorites query when the cache is empty"() {
    when:
    driver.loadFavorite()

    then:
    driver.runtimeState.pendingRandomMediaLoads == ['favorite']
    websocketMessages.any { String message -> message.contains('"command":"getFavorites"') }

    when:
    driver.getFavoritesMap()['object-1'] = [id: '9001', name: 'Second']
    driver.completePendingRandomMediaLoads('favorite')

    then:
    driver.runtimeState.pendingRandomMediaLoads.isEmpty()
    List payload = (List)new JsonSlurper().parseText(websocketMessages.find { String message -> message.contains('"command":"loadFavorite"') })
    payload[1].favoriteId == '9001'
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

  def "unsupported stop fails and queues a separately tracked pause fallback"() {
    given:
    driver.device.currentValues.transportStatus = 'playing'
    driver.stop()
    driver.drainTransportCommandQueue()
    Map stopCommand = driver.runtimeState.activeTransportCommand

    when:
    driver.failTransportCommand('stop', 'ERROR_UNSUPPORTED_COMMAND: stop command is not supported')

    then:
    driver.runtimeState.activeTransportCommand == null
    driver.runtimeState.transportCommandQueue*.command == ['pause']
    driver.logs.any { String entry -> entry.contains("Transport command stop (${stopCommand.id}) failed: ERROR_UNSUPPORTED_COMMAND") }
    !driver.logs.any { String entry -> entry.contains("Transport command stop (${stopCommand.id}) confirmed:") }

    when:
    driver.drainTransportCommandQueue()

    then:
    websocketMessages.size() == 2
    websocketMessages[0].contains('"command":"stop"')
    websocketMessages[1].contains('"command":"pause"')
    driver.runtimeState.activeTransportCommand.command == 'pause'
    driver.logs.any { String entry -> entry.contains('sending pause as the supported fallback') }
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

  def "playback status publishes play modes including crossfade"() {
    given:
    driver.device.events.clear()
    driver.device.currentValues.currentCrossfadeMode = 'off'
    driver.device.currentValues.currentShuffleMode = 'off'
    driver.device.currentValues.currentRepeatAllMode = 'off'
    driver.device.currentValues.currentRepeatOneMode = 'off'

    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playback', type: 'playbackStatus'],
      [playbackState: 'PLAYBACK_STATE_PAUSED', playModes: [repeat: false, repeatOne: false, shuffle: true, crossfade: true]]
    ]))
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.device.events.any { Map event -> event.name == 'currentCrossfadeMode' && event.value == 'on' }
    driver.device.events.any { Map event -> event.name == 'currentShuffleMode' && event.value == 'on' }
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

  def "app topology application suppresses redundant parent membership feedback"() {
    given:
    driver.device.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    driver.device.dataValues.isGroupCoordinator = 'false'
    driver.device.dataValues.groupPlayerIds = 'RINCON_TEST,RINCON_FOLLOW'
    String topology = JsonOutput.toJson([groups: [[groupId: 'GROUP_TEST',
      coordinatorId: 'RINCON_TEST', playerIds: ['RINCON_TEST', 'RINCON_FOLLOW'],
      playerNames: ['Test Player', 'Follower']]]])

    when:
    Boolean applied = driver.applyAppHouseholdTopology(topology, false)

    then:
    assert applied : driver.logs.join('\n')
    driver.device.dataValues.groupPlayerIds == 'RINCON_TEST,RINCON_FOLLOW'
    !playerStateMessages().any { Map event -> event.kind == 'membership' }
  }

  def "deferred membership updates publish a location event"() {
    when:
    driver.deferredParentUpdateGroupDevices([coordinatorId: 'RINCON_TEST', playersInGroup: ['RINCON_TEST']])

    then:
    playerStateMessages().size() == 1
    playerStateMessages()[0] == [kind: 'membership', coordinatorId: 'RINCON_TEST', playersInGroup: ['RINCON_TEST'], playerDni: 'SONOS-TEST-DNI']
  }

  def "group state flush publishes one batched location event"() {
    given:
    driver.queueGroupDeviceUpdate([status: 'playing', _groupVolume: 25, _groupMute: 'unmuted'])

    when:
    driver.flushPendingGroupDeviceUpdates()

    then:
    playerStateMessages().size() == 1
    playerStateMessages()[0].kind == 'state'
    playerStateMessages()[0].followerAttrs == [status: 'playing', groupVolume: 25, groupMute: 'unmuted']
    playerStateMessages()[0].volumeAttrs == [volume: 25, mute: 'unmuted']
    playerStateMessages()[0].playbackAttrs == [status: 'playing']
    driver.getPendingGroupDeviceUpdateSnapshot('SONOS-TEST-DNI').isEmpty()
  }

  def "unchanged coordinator metadata is still batched to followers and group devices"() {
    given:
    driver.device.dataValues.groupPlayerIds = 'RINCON_TEST,RINCON_FOLLOW'
    driver.device.currentValues.currentTrackName = 'Peepers On Crane Pond'
    driver.device.currentValues.currentArtistName = 'Adirondack Environments'
    driver.device.currentValues.currentAlbumName = 'Peepers On Crane Pond'
    driver.device.currentValues.currentTrackDuration = '0:41:03'
    driver.device.currentValues.currentTrackNumber = 1
    driver.device.currentValues.trackDescription = 'Peepers On Crane Pond by Adirondack Environments'
    driver.device.currentValues.trackData = JsonOutput.toJson([
      name: 'Peepers On Crane Pond', artist: 'Adirondack Environments', album: 'Peepers On Crane Pond'
    ])

    when:
    driver.setCurrentTrackName('Peepers On Crane Pond')
    driver.setCurrentArtistName('Adirondack Environments')
    driver.setCurrentAlbumName('Peepers On Crane Pond')
    driver.setCurrentTrackDuration('0:41:03')
    driver.setCurrentTrackNumber(1)
    driver.setTrackDescription('Peepers On Crane Pond by Adirondack Environments')
    driver.setTrackDataEvents([
      name: 'Peepers On Crane Pond', artist: 'Adirondack Environments', album: 'Peepers On Crane Pond'
    ])
    driver.flushPendingGroupDeviceUpdates()

    then:
    playerStateMessages().size() == 1
    playerStateMessages()[0].followerAttrs.currentTrackName == 'Peepers On Crane Pond'
    playerStateMessages()[0].followerAttrs.currentArtistName == 'Adirondack Environments'
    playerStateMessages()[0].followerAttrs.currentAlbumName == 'Peepers On Crane Pond'
    playerStateMessages()[0].followerAttrs.currentTrackDuration == '0:41:03'
    playerStateMessages()[0].followerAttrs.currentTrackNumber == 1
    playerStateMessages()[0].followerAttrs.trackDescription == 'Peepers On Crane Pond by Adirondack Environments'
    playerStateMessages()[0].playbackAttrs.currentTrackName == 'Peepers On Crane Pond'
    playerStateMessages()[0].playbackAttrs.trackData == driver.device.currentValues.trackData
  }

  def "new group member snapshot explicitly clears absent coordinator playback values"() {
    when:
    List<Map> snapshot = driver.getCurrentPlayingStatesForGroup()
    Map<String, Object> attributes = [:]
    snapshot.each { Map item -> attributes[item.name.toString()] = item.value }

    then:
    attributes.currentFavorite == 'No favorite playing'
    attributes.currentPlaylist == 'No playlist playing'
    attributes.currentTrackName == 'Not Available'
    attributes.currentArtistName == 'Not Available'
    attributes.audioSource == 'Not Available'
    attributes.status == 'stopped'
    attributes.transportStatus == 'stopped'
    attributes.currentTrackNumber == 0
    attributes.values().every { Object value -> value != null }
  }

  def "grouped follower ignores its local AVTransport playback stream"() {
    given:
    driver.device.dataValues.id = 'RINCON_TEST'
    driver.device.dataValues.groupPlayerIds = 'RINCON_TEST,RINCON_COORD'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.isGroupCoordinator = 'false'

    expect:
    driver.isGroupedFollowerForPlayback()
  }

  def "becoming coordinator invalidates cached playback metadata for restored queue"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.lastMetadataContainerId.put(dni, 'shared-group-container')
    driver.lastPlaybackState.put(dni, 'PLAYBACK_STATE_PAUSED')

    when:
    driver.clearCurrentPlayingStates()

    then:
    !driver.lastMetadataContainerId.containsKey(dni)
    !driver.lastPlaybackState.containsKey(dni)
  }

  def "group update enqueue re-arms a flush when its scheduled marker is stale"() {
    given:
    driver.scheduled.clear()
    driver.state.groupDeviceUpdateScheduled = true

    when:
    driver.queueGroupDeviceUpdate([currentTrackName: 'My Wave'])

    then:
    driver.scheduled.any { List item -> item[1] == 'flushPendingGroupDeviceUpdates' }
    driver.state.groupDeviceUpdateScheduled == true
    driver.getPendingGroupDeviceUpdateSnapshot('SONOS-TEST-DNI').currentTrackName == 'My Wave'
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

  def "parent command acknowledgement is not exposed as a player command"() {
    expect:
    !new File('../Drivers/Component/SonosAdvPlayer.groovy').text.contains("command 'acknowledgeParentCommand'")
    driver.metaClass.respondsTo(driver, 'acknowledgeParentCommand', String).size() == 1
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

  def "paired audio clip waits for both sockets and never replays secondary after primary dispatch"() {
    given:
    String leftMessage = JsonOutput.toJson([[namespace: 'audioClip', command: 'loadAudioClip', playerId: 'LEFT'], [name: 'pair-1']])
    String rightMessage = JsonOutput.toJson([[namespace: 'audioClip', command: 'loadAudioClip', playerId: 'RIGHT'], [name: 'pair-1']])
    driver.device.dataValues.websocketStatus = 'open'
    driver.setWsSubscriptionValue('audioClip', 'Subscribed')
    driver.children.clear()
    dwinks.hubitat.stubs.MockDevice rightChannel = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getRightChannelChildDNI())
    rightChannel.dataValues.websocketStatus = 'closed'
    Integer childConnects = 0
    Boolean failRightSendOnce = true
    rightChannel.metaClass.initializeWebsocketConnection = { -> childConnects++ }
    rightChannel.metaClass.playerLoadAudioClipForSynchronizedRelease = { String message, String correlationId ->
      if(failRightSendOnce) {
        failRightSendOnce = false
        rightChannel.updateDataValue('websocketStatus', 'error')
        return false
      }
      websocketMessages << "right:${message}"
      true
    }
    rightChannel.metaClass.completeSynchronizedAudioClipRelease = { String correlationId -> }
    driver.children << rightChannel
    driver.queueWebsocketMessage(leftMessage, 0, [kind: 'audioClip', correlationId: 'pair-1',
      pairedRightChannelMessage: rightMessage])

    when:
    driver.flushWebsocketOutboundQueue()

    then:
    websocketMessages.empty
    driver.runtimeState.websocketOutboundQueue.size() == 1
    driver.runtimeState.websocketOutboundQueue[0].message == leftMessage
    driver.runtimeState.websocketOutboundQueue[0].pairedRightChannelMessage == rightMessage
    childConnects == 1

    when:
    rightChannel.updateDataValue('websocketStatus', 'open')
    driver.flushWebsocketOutboundQueue()

    then:
    websocketMessages == [leftMessage]
    driver.runtimeState.websocketOutboundQueue == null
    driver.logs.any { it.toString().contains('Holding paired audioClip before either channel send') }
    driver.logs.any { it.toString().contains('AudioClip channel dispatch completed') }

    when:
    rightChannel.updateDataValue('websocketStatus', 'open')
    driver.flushWebsocketOutboundQueue()

    then:
    websocketMessages == [leftMessage]
    driver.runtimeState.websocketOutboundQueue == null

    cleanup:
    driver.children.clear()
    driver.subscriptions.clear()
  }

  def "paired clip falls back to primary only after secondary open timeout"() {
    given:
    String leftMessage = JsonOutput.toJson([[namespace: 'audioClip', command: 'loadAudioClip', playerId: 'LEFT'], [name: 'timeout-pair']])
    driver.device.dataValues.websocketStatus = 'open'
    driver.setWsSubscriptionValue('audioClip', 'Subscribed')
    driver.children.clear()
    dwinks.hubitat.stubs.MockDevice rightChannel = new dwinks.hubitat.stubs.MockDevice(
      deviceNetworkId: driver.getRightChannelChildDNI())
    rightChannel.dataValues.websocketStatus = 'closed'
    rightChannel.metaClass.initializeWebsocketConnection = { -> }
    rightChannel.metaClass.playerLoadAudioClipForSynchronizedRelease = { String message, String correlationId ->
      websocketMessages << "right:${message}"
      true
    }
    rightChannel.metaClass.completeSynchronizedAudioClipRelease = { String correlationId -> }
    driver.children << rightChannel
    Map pair = [kind: 'audioClip', correlationId: 'timeout-pair', pairedRightChannelMessage: JsonOutput.toJson([[namespace: 'audioClip', command: 'loadAudioClip', playerId: 'RIGHT'], [name: 'timeout-pair']]),
      pairedSecondaryDeadline: driver.now() + 30000L]
    driver.queueWebsocketMessage(leftMessage, 0, pair)
    driver.schedulePairedAudioClipSecondaryTimeout(driver.getWebsocketOutboundQueue()[0])

    when:
    driver.expirePairedAudioClipSecondary([correlationId: 'timeout-pair'])

    then:
    websocketMessages == [leftMessage]
    driver.runtimeState.websocketOutboundQueue == null
    driver.logs.any { it.toString().contains('did not report open within 30 seconds') }
    driver.logs.any { it.toString().contains('Secondary-open timeout expired') }
    driver.scheduled.any { List job -> job[1] == 'expirePairedAudioClipSecondary' && job[0] == 30 }

    cleanup:
    driver.children.clear()
    driver.subscriptions.clear()
  }

  def "websocket commands queue until the socket is open and flush in order"() {
    given:
    String firstMessage = JsonOutput.toJson([[namespace: 'favorites', command: 'getFavorites', householdId: 'HOUSE_TEST'], [:]])
    String secondMessage = JsonOutput.toJson([[namespace: 'playlists', command: 'getPlaylists', householdId: 'HOUSE_TEST'], [:]])
    driver.device.dataValues.websocketStatus = 'closed'
    driver.runtimeState.remove('websocketConnectPending')

    when:
    driver.sendWsMessage(firstMessage)
    driver.sendWsMessage(secondMessage)

    then:
    websocketMessages.empty
    driver.runtimeState.websocketOutboundQueue*.message == [firstMessage, secondMessage]

    when:
    driver.setWebSocketStatus('open')
    List<Map> subscriptions = (driver.runtimeState.websocketCommandLedger as List<Map>).findAll { Map entry ->
      entry.wireCommand == 'subscribe'
    }
    subscriptions.each { Map entry ->
      driver.processWebsocketMessage(JsonOutput.toJson([
        [namespace: entry.namespace, response: 'subscribe', success: true, type: 'none'], [:]
      ]))
    }

    then:
    Integer firstCommandIndex = websocketMessages.indexOf(firstMessage)
    Integer secondCommandIndex = websocketMessages.indexOf(secondMessage)
    Integer subscriptionIndex = websocketMessages.findIndexOf { String message -> message.contains('"command":"subscribe"') }
    subscriptionIndex >= 0
    firstCommandIndex > subscriptionIndex
    secondCommandIndex > firstCommandIndex
    driver.runtimeState.websocketOutboundQueue == null
  }

  def "matching websocket response clears the command ledger with trace success"() {
    given:
    driver.playerSetPlayerVolume(10)
    List<Map> pending = driver.runtimeState.websocketCommandLedger as List<Map>
    Integer pendingCount = pending.size()

    when:
    driver.processWebsocketMessage('[{"namespace":"playerVolume","response":"setVolume","success":true,"type":"none"},{ }]')

    then:
    pendingCount == 1
    driver.runtimeState.websocketCommandLedger == null
    driver.logs.any { it.toString().contains('WebSocket command ledger success') && it.toString().contains('playerVolume.setVolume') }
  }

  def "primary websocket ledger expiry cleans correlation without reconnecting"() {
    given:
    driver.device.dataValues.websocketStatus = 'open'

    when:
    driver.playerSetPlayerVolume(10)
    Map pending = (driver.runtimeState.websocketCommandLedger as List<Map>)[0]
    driver.expireWebsocketCommandLedgerEntry([entryId: pending.entryId,
      connectionGeneration: pending.connectionGeneration])

    then:
    driver.runtimeState.websocketCommandLedger == null
    driver.runtimeState.websocketOutboundQueue == null
    driver.device.getDataValue('websocketStatus') == 'open'
    !driver.scheduled.any { List job -> job[1] == 'retryWebSocketConnection' }
  }

  def "claimed websocket ledger batch is detached from commands registered afterward"() {
    given:
    driver.playerSetPlayerVolume(10)
    Map original = (driver.runtimeState.websocketCommandLedger as List<Map>)[0]

    when:
    List<Map> claimed = driver.takeWebsocketCommandLedgerBatch()
    driver.playerSetPlayerVolume(20)

    then:
    claimed.size() == 1
    claimed[0].entryId == original.entryId
    ((driver.runtimeState.websocketCommandLedger as List<Map>)[0]).entryId != original.entryId
    claimed[0].message.contains('"volume":10')
    ((driver.runtimeState.websocketCommandLedger as List<Map>)[0]).message.contains('"volume":20')
  }

  def "websocket ledger locks are stable per device and distinct across devices"() {
    given:
    Object firstDeviceLock = driver.getWebsocketCommandLedgerLock()

    when:
    driver.device.deviceNetworkId = 'SONOS-OTHER-DNI'
    Object secondDeviceLock = driver.getWebsocketCommandLedgerLock()
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'

    then:
    firstDeviceLock.is(driver.getWebsocketCommandLedgerLock())
    !firstDeviceLock.is(secondDeviceLock)
  }

  def "explicit websocket command rejection clears the ledger as failure without declaring socket stale"() {
    given:
    driver.playerSetPlayerVolume(10)
    Integer generation = (driver.runtimeState.websocketConnectionGeneration ?: 0) as Integer

    when:
    driver.processWebsocketMessage('[{"namespace":"playerVolume","response":"setVolume","success":false,"type":"globalError"},{"errorCode":"ERROR_INVALID_ARGUMENT","reason":"invalid volume"}]')

    then:
    driver.runtimeState.websocketCommandLedger == null
    driver.device.getDataValue('websocketStatus') == 'open'
    (driver.runtimeState.websocketConnectionGeneration ?: 0) == generation
    driver.logs.any { it.toString().contains('WebSocket command ledger failure') && it.toString().contains('ERROR_INVALID_ARGUMENT') }
  }

  def "command response silence expires correlation without queueing or reconnecting"() {
    given:
    driver.playerSetPlayerVolume(10)
    Map pending = (driver.runtimeState.websocketCommandLedger as List<Map>)[0]

    when:
    driver.expireWebsocketCommandLedgerEntry([entryId: pending.entryId,
      connectionGeneration: pending.connectionGeneration])

    then:
    driver.runtimeState.websocketCommandLedger == null
    driver.runtimeState.websocketOutboundQueue == null
    driver.device.getDataValue('websocketStatus') == 'open'
    !driver.scheduled.any { List job -> job[1] == 'retryWebSocketConnection' }
  }

  def "websocket connection enables native ping liveness checks"() {
    given:
    driver.device.dataValues.websocketStatus = 'closed'
    driver.runtimeState[driver.APP_INITIALIZATION_CONNECTION_GRANTED_STATE_KEY] = true

    when:
    driver.wsConnect()

    then:
    websocketConnections.size() == 1
    websocketConnections[0].any { Object argument ->
      argument instanceof Map && (argument as Map).pingInterval == driver.WS_PING_INTERVAL_SECONDS
    }
    driver.WS_PING_INTERVAL_SECONDS > 0
  }

  def "websocket send gate queues work while a connection attempt is pending"() {
    given:
    String message = JsonOutput.toJson([[namespace: 'playerVolume', command: 'setVolume',
      playerId: 'RINCON_TEST'], [volume: 30]])
    driver.device.dataValues.websocketStatus = 'open'
    driver.runtimeState[driver.WS_CONNECT_PENDING_STATE_KEY] = true

    when:
    Boolean sent = driver.sendWsMessage(message)

    then:
    !sent
    websocketMessages.empty
    websocketConnections.empty
    driver.runtimeState.websocketOutboundQueue*.message == [message]
  }

  def "outbound command expirations use one earliest-deadline scheduler job"() {
    given:
    driver.device.dataValues.websocketStatus = 'closed'

    when:
    (1..40).each { Integer sequence ->
      String message = JsonOutput.toJson([[namespace: 'playback', command: 'skipToNextTrack',
        groupId: "GROUP-${sequence}"], [:]])
      driver.queueWebsocketMessage(message)
    }

    then:
    (driver.runtimeState.websocketOutboundQueue as List<Map>).size() == 40
    driver.scheduled.count { List job -> job[1] == 'expireWebsocketOutboundQueueEntry' &&
      job[2]?.overwrite == true } == 1
    driver.runtimeState.websocketOutboundQueueExpiryAt ==
      (driver.runtimeState.websocketOutboundQueue as List<Map>).collect { Map entry -> entry.expiresAt as Long }.min()
  }

  def "disconnected command queue replaces absolute state but preserves relative commands"() {
    given:
    driver.device.dataValues.websocketStatus = 'closed'
    String volume20 = JsonOutput.toJson([[namespace: 'playerVolume', command: 'setVolume', playerId: 'RINCON_TEST'], [volume: 20]])
    String volume30 = JsonOutput.toJson([[namespace: 'playerVolume', command: 'setVolume', playerId: 'RINCON_TEST'], [volume: 30]])
    String volume40 = JsonOutput.toJson([[namespace: 'playerVolume', command: 'setVolume', playerId: 'RINCON_TEST'], [volume: 40]])
    String next = JsonOutput.toJson([[namespace: 'playback', command: 'skipToNextTrack', groupId: 'GROUP_TEST'], [:]])
    String previous = JsonOutput.toJson([[namespace: 'playback', command: 'skipToPreviousTrack', groupId: 'GROUP_TEST'], [:]])

    when:
    driver.queueWebsocketMessage(volume20)
    driver.queueWebsocketMessage(volume30)
    driver.queueWebsocketMessage(next)
    driver.queueWebsocketMessage(volume40)
    driver.queueWebsocketMessage(previous)

    then:
    List<Map> queued = driver.runtimeState.websocketOutboundQueue as List<Map>
    queued.size() == 4
    queued[0].wireCommand == 'setVolume'
    ((List)new JsonSlurper().parseText(queued[0].message))[1].volume == 30
    queued[1].wireCommand == 'skipToNextTrack'
    queued[2].wireCommand == 'setVolume'
    ((List)new JsonSlurper().parseText(queued[2].message))[1].volume == 40
    queued[3].wireCommand == 'skipToPreviousTrack'
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
    driver.device.dataValues.isGroupCoordinator = 'true'
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

  def "follower refuses delayed and watchdog group namespace subscriptions"() {
    given:
    driver.device.dataValues.isGroupCoordinator = 'false'
    driver.device.dataValues.groupId = 'GROUP_TEST'
    driver.device.dataValues.websocketStatus = 'open'

    when:
    driver.subscribeToPlaybackDebounce([groupId: 'GROUP_TEST'])
    driver.resubscribeWebsocketNamespace('playbackMetadata')

    then:
    websocketMessages.empty
    driver.runtimeState.websocketCommandLedger == null
    driver.logs.count { it.toString().contains('Skipping') } >= 2
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

  def "UPnP event expectations cover state-changing SOAP actions only"() {
    expect:
    driver.expectedUpnpSidForSoapAction([headers: [SOAPAction: soapAction]]) == expectedSid

    where:
    soapAction                                                                                         || expectedSid
    'urn:schemas-upnp-org:service:AVTransport:1#Play'                                                  || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#Pause'                                                 || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#Stop'                                                  || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#Next'                                                  || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#Previous'                                              || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#Seek'                                                  || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI'                                     || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#RemoveAllTracksFromQueue'                              || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#AddURIToQueue'                                         || 'sid1'
    'urn:schemas-upnp-org:service:AVTransport:1#ConfigureSleepTimer'                                 || 'sid1'
    'urn:schemas-upnp-org:service:RenderingControl:1#SetVolume'                                        || 'sid2'
    'urn:schemas-upnp-org:service:RenderingControl:1#SetMute'                                           || 'sid2'
    'urn:schemas-upnp-org:service:RenderingControl:1#SetBass'                                           || 'sid2'
    'urn:schemas-upnp-org:service:RenderingControl:1#SetTreble'                                         || 'sid2'
    'urn:schemas-upnp-org:service:RenderingControl:1#SetLoudness'                                       || 'sid2'
    'urn:schemas-upnp-org:service:RenderingControl:1#SetEQ'                                            || 'sid2'
    'urn:schemas-upnp-org:service:GroupRenderingControl:1#SetGroupVolume'                              || 'sid4'
    'urn:schemas-upnp-org:service:GroupRenderingControl:1#SetRelativeGroupVolume'                      || 'sid4'
    'urn:schemas-upnp-org:service:GroupRenderingControl:1#SetGroupMute'                                || 'sid4'
    'urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo'                                      || null
    'urn:schemas-upnp-org:service:RenderingControl:1#GetVolume'                                         || null
  }

  def "UPnP expectation retries once and then stops when no event arrives"() {
    given:
    driver.armExpectedUpnpSubscriptionEvent('sid1', 'Play')
    List<Map> pending = driver.runtimeState.upnpExpectedSubscriptionEvents as List<Map>
    pending[0].deadlineAt = driver.now() - 1L

    when:
    driver.watchExpectedUpnpSubscriptionEvents()

    then:
    pending[0].attempts == 1
    rawRequests.size() == 1
    rawRequests[0].startsWith('SUBSCRIBE /MediaRenderer/AVTransport/Event HTTP/1.1')

    when:
    pending[0].deadlineAt = driver.now() - 1L
    driver.watchExpectedUpnpSubscriptionEvents()

    then:
    driver.runtimeState.upnpExpectedSubscriptionEvents == null
    rawRequests.size() == 1
    driver.logs.any { it.toString().contains('No UPnP sid1 event arrived after resubscription for Play') }
  }

  def "GroupRenderingControl expectation does not resubscribe after player becomes ungrouped"() {
    given:
    driver.device.currentValues.isGrouped = 'off'
    driver.armExpectedUpnpSubscriptionEvent('sid4', 'SetGroupVolume')
    List<Map> pending = driver.runtimeState.upnpExpectedSubscriptionEvents as List<Map>
    pending[0].deadlineAt = driver.now() - 1L

    when:
    driver.watchExpectedUpnpSubscriptionEvents()

    then:
    driver.runtimeState.upnpExpectedSubscriptionEvents == null
    rawRequests.empty
    driver.logs.any { it.toString().contains('Skipping GroupRenderingControl resubscribe') }
  }

  def "AVTransport event receipt records a per-player diagnostic timestamp"() {
    when:
    driver.setLastInboundAVTransportEvent()

    then:
    driver.getLastInboundAVTransportEventEpoch() > 0L
    driver.EVENT_TIMESTAMP_KEYS.contains('lastAVTransportEvent')
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

  def "groups websocket refreshes descriptive metadata without republishing unchanged membership"() {
    given:
    driver.device.dataValues.id = 'RINCON_ARC'
    driver.device.dataValues.groupId = 'GROUP_ARC'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_ARC'
    driver.device.dataValues.groupPlayerIds = 'RINCON_ARC'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.device.currentValues.groupName = 'Cora’s Room'
    driver.device.currentValues.groupCoordinatorName = 'Old Name'
    driver.device.currentValues.groupMemberNames = '[Old Name]'
    List<Map> parentMembershipUpdates = []
    driver.metaClass.parentUpdateGroupDevices = { String coordinatorId, List<String> playerIds ->
      parentMembershipUpdates << [coordinatorId, playerIds]
    }
    String message = JsonOutput.toJson([
      [type: 'groups', name: 'groups'],
      [groups: [[id: 'GROUP_ARC', name: 'Arc', coordinatorId: 'RINCON_ARC', playerIds: ['RINCON_ARC']]],
       players: [[id: 'RINCON_ARC', name: 'Arc']]]
    ])

    when:
    driver.processWebsocketMessage(message)

    then:
    driver.pendingLocalDeviceEvents[driver.device.deviceNetworkId].groupName == 'Arc'
    driver.pendingLocalDeviceEvents[driver.device.deviceNetworkId].groupCoordinatorName == 'Arc'
    driver.pendingLocalDeviceEvents[driver.device.deviceNetworkId].groupMemberNames == '[Arc]'
    parentMembershipUpdates.empty
    driver.logs.any { String log -> log.contains('topology identity unchanged; descriptive metadata refreshed') }
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

  def "muted synchronized group member reports terminal progress"() {
    given:
    driver.events.clear()
    driver.device.currentValues.mute = 'muted'
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.getDeviceAudioClipQueueSaved().clear()

    when:
    driver.playerPrepareAudioClipForSynchronizedStart('http://example.test/muted.mp3', 10G,
      'muted-group-announcement', 2)

    then:
    driver.getDeviceAudioClipQueue().empty
    playerStateMessages().any { Map event ->
      event.kind == 'groupAnnouncementProgress' &&
        event.correlationId == 'muted-group-announcement' &&
        event.phase == 'DISMISSED' && event.reason == 'muted'
    }
  }

  def "public volume coercion preserves zero"() {
    expect:
    driver.coerceVolume(input) == 0G

    where:
    input << [0, 0L, 0G, '0', ' 0 ']
  }
  def "public restore resume and speak commands preserve zero volume"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.metaClass.shouldUseAnnouncementAudioClip = { Boolean highPriority -> true }
    driver.metaClass.textToSpeech = { String text, String voice -> [uri: 'http://example.test/zero.mp3', duration: 1] as LinkedHashMap }
    driver.device.dataValues.capabilities = 'AUDIO_CLIP'
    driver.device.currentValues.volume = 5
    driver.settings.ttsBoostAmount = 10
    driver.getDeviceAudioClipQueue().clear()
    driver.runtimeState.audioClipPlaying = true
    List<BigDecimal> streamVolumes = []
    driver.metaClass.componentPlayStreamWithRestoreLocal = { String uri, Integer duration, BigDecimal volume ->
      streamVolumes << volume
    }

    when:
    driver.invokeMethod(command, ['zero', input] as Object[])
    Map clip = driver.getDeviceAudioClipQueue().peek()

    then:
    if(command == 'speak') {
      streamVolumes == [0G]
    } else {
      ((List)new JsonSlurper().parseText(clip.leftChannel as String))[1].volume == 0
    }

    where:
    [command, input] << [['playTextAndRestore', 'playTextAndResume', 'speak'], [0, 0L, 0G, '0', ' 0 ']].combinations()
  }

  def "paused AirPlay metadata never routes speak through the stream holder"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.getDeviceAudioClipQueue().clear()
    Boolean routedToHolder = false
    com.hubitat.app.InstalledAppWrapper parent = new com.hubitat.app.InstalledAppWrapper()
    parent.metaClass.shouldUseAudioClipForAirPlay = { -> true }
    parent.metaClass.handlePlayerActiveStreamTts = { String dni, String uri, BigDecimal volume,
        Integer duration, String correlationId ->
      routedToHolder = dni == 'SONOS-TEST-DNI' && uri == 'http://example.test/airplay.mp3'
      return routedToHolder
    }
    driver.metaClass.getParentApp = { -> parent }
    driver.metaClass.shouldUseAnnouncementAudioClip = { Boolean highPriority -> true }
    driver.metaClass.textToSpeech = { String text, String voice ->
      [uri: 'http://example.test/airplay.mp3', duration: 2] as LinkedHashMap
    }
    driver.device.currentValues.audioSource = 'Not Available'
    driver.state.audioSourceMetadataType = 'linein.airplay'
    driver.state.audioSourceMetadataObservedAt = driver.now()
    driver.device.dataValues.capabilities = 'AUDIO_CLIP'
    driver.device.dataValues.useAudioClipForAirPlay = 'true'
    driver.runtimeState.audioClipPlaying = true

    when:
    driver.devicePlayText('AirPlay clip test', 1G, null)
    Map clip = driver.getDeviceAudioClipQueue().peek()

    then:
    !routedToHolder
    driver.asyncCalls.empty
    clip != null
    ((List)new JsonSlurper().parseText(clip.leftChannel as String))[1].streamUrl == 'http://example.test/airplay.mp3'
  }

  def "stale AirPlay metadata does not trigger holder routing"() {
    given:
    driver.state.audioSourceMetadataType = 'linein.airplay'
    driver.state.audioSourceMetadataObservedAt = driver.now() - 180001L

    expect:
    !driver.isCurrentAirPlayAudioSource()
  }

  def "fallback announcements treat zero as omitted and minus one as explicit zero"() {
    given:
    List<BigDecimal> volumes = []
    driver.metaClass.getParent = { -> null }
    driver.metaClass.textToSpeech = { String text, String voice -> [:] as LinkedHashMap }
    driver.metaClass.componentPlayTextNoRestoreLocal = { String text, BigDecimal level, String voice -> volumes.add(level) }
    driver.metaClass.componentPlayTextWithRestoreLocal = { String text, BigDecimal level, String voice -> volumes.add(level) }
    driver.device.currentValues.volume = 5
    driver.settings.ttsBoostAmount = 10

    when:
    driver.invokeMethod(command, ['zero', requestedVolume, null] as Object[])

    then:
    volumes == [expectedVolume]

    where:
    command                  | requestedVolume | expectedVolume
    'speak'                  | 0               | 15G
    'devicePlayTextNoRestore'| 0               | 15G
    'speak'                  | -1G             | 0G
    'devicePlayTextNoRestore'| -1G             | 0G
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

  def "public playback commands treat zero as unspecified and minus one as explicit zero"() {
    given:
    List<BigDecimal> appliedVolumes = []
    driver.metaClass.getParent = { -> null }
    driver.metaClass.componentSetGroupLevelLocal = { BigDecimal level -> appliedVolumes.add(level) }
    driver.metaClass.setAVTransportURIAndPlay = { String uri -> }
    driver.device.dataValues.capabilities = ''

    when:
    driver.playTrack('http://example.test/track.mp3', 0)
    driver.playHighPriorityTrack('http://example.test/announcement.mp3', 0)

    then:
    appliedVolumes.isEmpty()

    when:
    driver.playTrack('http://example.test/track.mp3', -1G)
    driver.playHighPriorityTrack('http://example.test/announcement.mp3', -1G)

    then:
    appliedVolumes == [0G, 0G]
  }

  def "public optional playback volume normalizer preserves the explicit zero sentinel"() {
    expect:
    driver.normalizePublicPlaybackVolume(input) == expected

    where:
    input | expected
    null  | null
    0     | null
    '0'   | null
    0G    | null
    -1G   | -1G
    12G   | 12G
  }

  def "announcement volume resolves minus one sentinel without changing omitted-volume boost"() {
    expect:
    driver.calculateAnnouncementVolume(requested, 20, 5) == expected

    where:
    requested | expected
    null      | 25
    0G        | 0
    -1G       | 0
    12G       | 17
  }

}
