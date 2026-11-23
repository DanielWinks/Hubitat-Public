package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import java.io.ByteArrayInputStream
import java.time.Instant
import org.apache.commons.lang3.StringEscapeUtils
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvPlayerSpec extends Specification {

  @Shared HubitatScriptHarness driver
  @Shared List<String> websocketMessages = []
  @Shared List<Map> rawConnections = []
  @Shared List<Map> parentPlayerUpdates = []
  @Shared List<Map> parentPlayerRequests = []
  @Shared List<Map> parentPlayerPresence = []
  @Shared List<List> websocketConnections = []
  @Shared List<String> rawRequests = []
  @Shared Object rawSocket

  void seedUpnpFavorite(String favoriteId, String uri = "x-sonos-http:song%3a${favoriteId}.mp4?sid=204&flags=8232&sn=9") {
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.getFavoritesMap()["test-${favoriteId}"] = [id: favoriteId, name: "Favorite ${favoriteId}",
      service: 'Apple Music', upnpUri: uri,
      upnpMetadata: '<DIDL-Lite><item id="10032028song%3a' + favoriteId + '"><dc:title>Favorite ' + favoriteId + '</dc:title></item></DIDL-Lite>',
      resourceObjectId: '10032028song%3a' + favoriteId, matchObjectId: favoriteId]
  }

  void seedUpnpPlaylist(String playlistId) {
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.getPlaylistsMap()[playlistId] = [id: playlistId, objectId: "SQ:${playlistId}", name: "Playlist ${playlistId}",
      upnpUri: "x-rincon-cpcontainer:SQ:${playlistId}",
      upnpMetadata: '<DIDL-Lite><item id="SQ:' + playlistId + '"><dc:title>Playlist ' + playlistId + '</dc:title></item></DIDL-Lite>']
  }

  void queueTestGroupSocketCommand(Integer marker = 10) {
    driver.device.dataValues.websocketStatus = 'open'
    driver.sendWsMessage(JsonOutput.toJson([[namespace: 'audioClip', command: 'loadAudioClip', playerId: 'RINCON_TEST'],
      [name: "clip-${marker}", clipType: 'CUSTOM', appId: 'test', priority: 'HIGH', streamUrl: 'http://example.test/clip.mp3']]))
  }

  void seedGroupTopology(String coordinatorId, Map<String, String> hostByPlayer) {
    driver.zgtMemberHostByPlayerId.clear()
    hostByPlayer.each { String playerId, String host -> driver.zgtMemberHostByPlayerId[playerId] = "${host}:1400" }
    driver.device.dataValues.groupCoordinatorId = coordinatorId
  }

  def "local SOAP helpers build requests without mutating service defaults"() {
    given:
    java.lang.reflect.Field serviceField = driver.getClass().getDeclaredField('RenderingControl')
    serviceField.setAccessible(true)
    Map service = (Map)serviceField.get(driver)
    Map originalArguments = new LinkedHashMap(service.actions.SetVolume.arguments)

    when:
    Map first = driver.getSoapActionParams('192.168.1.50:1400', service, 'SetVolume', [DesiredVolume: 37, InstanceID: 0, Channel: 'Master'])
    Map second = driver.getSoapActionParams('192.168.1.50:1400', service, 'SetVolume', [DesiredVolume: 82, InstanceID: 0, Channel: 'Master'])

    then:
    first.uri == 'http://192.168.1.50:1400/MediaRenderer/RenderingControl/Control'
    first.headers.SOAPAction == 'urn:schemas-upnp-org:service:RenderingControl:1#SetVolume'
    first.contentType == 'text/xml'
    first.body.contains('<DesiredVolume>37</DesiredVolume>')
    second.body.contains('<DesiredVolume>82</DesiredVolume>')
    !second.body.contains('<DesiredVolume>37</DesiredVolume>')
    service.actions.SetVolume.arguments == originalArguments
    driver.parseSonosMessageXML('<Event><title>A &amp; B</title></Event>').getAt('title').text() == 'A & B'
    driver.parseSonosMessageXML('<Event><title>A &lt; B &gt; C &quot;D&quot; &apos;E&apos;</title></Event>').getAt('title').text() == 'A < B > C "D" \'E\''
  }

  def "favorites are browsed and correlated to AVTransport by their UPnP resource URI"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    String uri = 'x-sonos-http:song%3a933958518.mp4?sid=204&flags=8232&sn=9'
    String embedded = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" ' +
      'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" ' +
      'xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="10032028song%3a933958518">' +
      '<dc:title>Peepers On Crane Pond</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class>' +
      '</item></DIDL-Lite>'
    String didl = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" ' +
      'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" ' +
      'xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" ' +
      'xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="FV:2/14" parentID="FV:2">' +
      '<dc:title>Peepers On Crane Pond</dc:title><upnp:albumArtURI>https://example.test/cover.jpg</upnp:albumArtURI>' +
      '<res>' + uri.replace('&', '&amp;') + '</res><r:resMD>' + StringEscapeUtils.escapeXml10(embedded) +
      '</r:resMD></item></DIDL-Lite>'

    when:
    driver.getFavorites()
    Map request = driver.asyncCalls.last()
    Map playbackSnapshot = [
      currentTrackURI: 'x-sonos-http:song%3aother.mp4',
      currentTrackMetaData: [:],
      enqueuedTransportURIMetaData: driver.parseAvtMetaData(embedded)
    ]
    driver.runtimeState.lastTransportPlaybackSnapshot = playbackSnapshot
    driver.favoritesBrowseCallback(new hubitat.scheduling.AsyncResponse(status: 200,
      data: '<BrowseResponse><Result>' + StringEscapeUtils.escapeXml10(didl) +
        '</Result><TotalMatches>1</TotalMatches><UpdateID>5</UpdateID></BrowseResponse>'), request.data)
    driver.updateCurrentFavoriteFromUpnp(playbackSnapshot)
    driver.flushPendingLocalDeviceEvents()

    then:
    request.params.uri == 'http://192.168.1.50/MediaServer/ContentDirectory/Control'
    request.params.body.contains('<ObjectID>FV:2</ObjectID>')
    driver.findFavoriteById('14').name == 'Peepers On Crane Pond'
    driver.device.currentValue('currentFavorite').contains('Favorite #14 Peepers On Crane Pond')
  }

  def "ContentDirectory updates refresh only the changed active catalog over UPnP"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.children << new dwinks.hubitat.stubs.MockDevice(deviceNetworkId: driver.getFavoritesChildDNI())
    driver.children << new dwinks.hubitat.stubs.MockDevice(deviceNetworkId: driver.getPlaylistChildDNI())
    String favoritesEvent = '<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"><e:property><FavoritesUpdateID>RINCON_TEST,101</FavoritesUpdateID></e:property></e:propertyset>'
    String playlistsEvent = '<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"><e:property><SavedQueuesUpdateID>RINCON_TEST,33</SavedQueuesUpdateID></e:property></e:propertyset>'

    when:
    driver.handleContentDirectoryPayload(favoritesEvent)
    driver.handleContentDirectoryPayload(playlistsEvent)

    then:
    driver.asyncCalls*.cb == ['favoritesBrowseCallback', 'playlistsBrowseCallback']
    driver.asyncCalls[0].params.body.contains('<ObjectID>FV:2</ObjectID>')
    driver.asyncCalls[1].params.body.contains('<ObjectID>SQ:</ObjectID>')
  }

  def "ContentDirectory subscription is active when a favorite or playlist child exists"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.children << new dwinks.hubitat.stubs.MockDevice(deviceNetworkId: driver.getFavoritesChildDNI())

    when:
    driver.subscribeToContentDirectory()

    then:
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.sidKey == 'sid6'
    driver.upnpRequestQueues.get('SONOS-TEST-DNI').active.eventUrl == '/MediaServer/ContentDirectory/Event'
  }

  void drainParentBridge() {
    List<Map> updates = driver.flushPendingParentUpdates() ?: []
    updates.each { Map payload -> parentPlayerUpdates << new LinkedHashMap(payload) }
    List<Map> requests = driver.flushPendingParentRequests() ?: []
    requests.each { Map request ->
      parentPlayerRequests << new LinkedHashMap(request)
      if(request?.action == 'activeStreamTts' && request?.args?.requestId) {
        driver.completeParentAppActiveStreamTts(request.args.requestId.toString(), 'false')
      }
    }
  }

  List<Map> playerStateMessages() {
    drainParentBridge()
    parentPlayerUpdates.collect { Map event -> new LinkedHashMap(event) }
  }

  List<Map> playerRequestMessages() {
    drainParentBridge()
    parentPlayerRequests.collect { Map event -> new LinkedHashMap(event) }
  }

  String avtBody(String instanceProperties) {
    String event = "<Event><InstanceID val=\"0\">${instanceProperties}</InstanceID></Event>"
    return '<propertyset><property><LastChange>' + StringEscapeUtils.escapeXml10(event) +
      '</LastChange></property></propertyset>'
  }

  void appendAudioClipAdmissionSchedules(Integer regularRemaining, Integer highRemaining,
      List<String> prefix, List<List<String>> schedules) {
    if(regularRemaining == 0 && highRemaining == 0) {
      schedules.add(new ArrayList<String>(prefix))
      return
    }
    if(regularRemaining > 0) {
      prefix.add('R')
      appendAudioClipAdmissionSchedules(regularRemaining - 1, highRemaining, prefix, schedules)
      prefix.remove(prefix.size() - 1)
    }
    if(highRemaining > 0) {
      prefix.add('H')
      appendAudioClipAdmissionSchedules(regularRemaining, highRemaining - 1, prefix, schedules)
      prefix.remove(prefix.size() - 1)
    }
  }

  List<String> expectedAudioClipDispatches(List<Map> operations) {
    List<Map> regularQueue = []
    List<Map> highQueue = []
    List<String> dispatches = []
    Map active = null

    Closure dispatchNext = { ->
      if(!highQueue.isEmpty()) {
        active = highQueue.remove(0)
      } else if(!regularQueue.isEmpty()) {
        active = regularQueue[0]
      } else {
        active = null
      }
      if(active != null) { dispatches.add(active.uri as String) }
    }

    operations.each { Map operation ->
      if(operation.kind == 'regular') {
        regularQueue.add(operation)
        if(active == null) { dispatchNext.call() }
      } else if(operation.kind == 'high') {
        highQueue.add(operation)
        if(active == null || active.priority != 'HIGH') { dispatchNext.call() }
      } else if(operation.kind == 'complete' && active != null) {
        if(active.priority != 'HIGH' && !regularQueue.isEmpty()) { regularQueue.remove(0) }
        active = null
        dispatchNext.call()
      }
    }
    return dispatches
  }

  void resetAudioClipQueueModel() {
    driver.metaClass.getParent = { -> null }
    driver.runtimeState.clear()
    driver.state.clear()
    driver.binding.setVariable('atomicState', [:])
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.getDeviceAudioClipQueueTimers().clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    driver.asyncCalls.clear()
    driver.events.clear()
    driver.logs.clear()
    driver.settings.chimeBeforeTTS = false
    driver.settings.ttsBoostAmount = 0
    driver.device.currentValues.mute = 'unmuted'
    driver.device.currentValues.volume = 10
    driver.device.currentValues.level = 10
  }

  void captureActiveAudioClipDispatch(List<String> dispatches, List<String> lastCorrelationId) {
    Map active = driver.runtimeState.activeAudioClipMessage instanceof Map
        ? (Map)driver.runtimeState.activeAudioClipMessage : null
    String correlationId = active?.correlationId?.toString()
    if(!correlationId) {
      lastCorrelationId[0] = null
      return
    }
    if(correlationId != lastCorrelationId[0]) {
      dispatches.add(active.uri?.toString() ?: active.origin?.toString() ?: correlationId)
      lastCorrelationId[0] = correlationId
    }
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
      event.phase == 'PLAYBACK_RESTORED' && event.correlationId == 'restore-guid' && event.restored == true
    }
  }

  def "idle high priority TTS retries a rejected TuneIn restore without touching audio clips"() {
    given:
    driver.device.dataValues.capabilities = ''
    driver.metaClass.textToSpeech = { String text, String voice ->
      [uri: 'http://example.test/tts.mp3', duration: 2] as LinkedHashMap
    }
    driver.runtimeState.lastTransportPlaybackSnapshot = [
      currentUri: 'x-sonosapi-stream:s123?sid=254&flags=32',
      currentUriMetaData: '<DIDL-Lite><title>Jazz &amp; Blues</title></DIDL-Lite>',
      transportState: 'STOPPED', track: '1', positionMillis: 1000L, playMode: 'NORMAL'
    ]
    driver.runtimeState.audioClipPlaying = true
    driver.runtimeState.audioClipWatchdogRequestId = 'regular-watchdog'
    driver.getDeviceAudioClipQueue().add([correlationId: 'regular-head'])
    driver.playHighPriorityTTS('restore test', 10, null)
    Map timer = driver.scheduled.find { List job -> job[1] == 'restoreDirectStreamTts' }[2].data
    driver.restoreDirectStreamTts(timer)
    Map first = driver.asyncCalls.last()

    when:
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 500), first.data)
    Map retry = driver.scheduled.find { List job -> job[1] == 'retryAnnouncementPlaybackUri' }[2].data

    then:
    driver.runtimeState.announcementPlaybackSnapshot.currentUri == 'x-sonosapi-stream:s123?sid=254&flags=32'
    !playerStateMessages().any { it.phase == 'PLAYBACK_RESTORED' }

    when:
    driver.retryAnnouncementPlaybackUri(retry)
    Map second = driver.asyncCalls.last()
    Integer callCount = driver.asyncCalls.size()
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 200), first.data)
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 200), second.data)
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 200), second.data)

    then:
    first.params.body == second.params.body
    first.params.body.contains('<CurrentURI>x-sonosapi-stream:s123?sid=254&amp;flags=32</CurrentURI>')
    first.params.body.contains('<CurrentURIMetaData>&lt;DIDL-Lite&gt;&lt;title&gt;Jazz &amp;amp; Blues&lt;/title&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>')
    first.data.attempt == 1
    second.data.attempt == 2
    driver.asyncCalls.size() == callCount
    driver.getDeviceAudioClipQueue()*.correlationId == ['regular-head']
    driver.runtimeState.audioClipPlaying == true
    driver.runtimeState.audioClipWatchdogRequestId == 'regular-watchdog'
    !driver.unschedules.contains('audioClipWatchdog')
    driver.runtimeState.announcementPlaybackSnapshot == null
    playerStateMessages().findAll { it.phase == 'PLAYBACK_RESTORED' }*.restored == [true]
  }

  def "URI restore reports terminal HTTP failure once and keeps the original snapshot"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [correlationId: 'failure', currentUri: 'x-rincon-queue:RINCON_TEST#0']
    driver.restoreGroupAnnouncementPlayback('failure')

    when:
    attempts.times { Integer index ->
      Map request = driver.asyncCalls.last()
      driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: status), request.data)
      if(index + 1 < attempts) {
        Map retry = driver.scheduled.findAll { List job -> job[1] == 'retryAnnouncementPlaybackUri' }.last()[2].data
        driver.retryAnnouncementPlaybackUri(retry)
      }
    }

    then:
    driver.asyncCalls.count { it.cb == 'announcementPlaybackUriRestored' } == attempts
    driver.runtimeState.announcementPlaybackRestore == null
    driver.runtimeState.announcementPlaybackSnapshot == null
    driver.runtimeState.lastAnnouncementPlaybackRestoreFailure.snapshot.currentUri == 'x-rincon-queue:RINCON_TEST#0'
    driver.runtimeState.lastAnnouncementPlaybackRestoreFailure.attempt == attempts
    driver.runtimeState.lastAnnouncementPlaybackRestoreFailure.status == status
    playerStateMessages().findAll { it.phase == 'PLAYBACK_RESTORED' }*.restored == [false]
    playerStateMessages().find { it.phase == 'PLAYBACK_RESTORED' }.reason == "set-uri-failed-${status}"

    where:
    status | attempts
    500    | 3
    404    | 1
  }

  def "URI retry leaves an external takeover or newer snapshot alone"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [
      correlationId: 'old', currentUri: 'x-rincon-queue:RINCON_TEST#0', announcementUri: 'http://example.test/tts.mp3'
    ]
    driver.restoreGroupAnnouncementPlayback('old')
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 500), driver.asyncCalls.last().data)
    Map retry = driver.scheduled.find { List job -> job[1] == 'retryAnnouncementPlaybackUri' }[2].data
    Integer callCount = driver.asyncCalls.size()

    when:
    if(newAnnouncement) {
      driver.runtimeState.announcementPlaybackSnapshot = [correlationId: 'new', currentUri: 'new-source']
    } else {
      driver.device.currentValues.audioSource = 'new-source'
      driver.state.playbackState = 'PLAYBACK_STATE_PLAYING'
    }
    driver.retryAnnouncementPlaybackUri(retry)

    then:
    driver.asyncCalls.size() == callCount
    driver.runtimeState.announcementPlaybackSnapshot?.correlationId == (newAnnouncement ? 'new' : null)
    playerStateMessages().findAll { it.phase == 'PLAYBACK_RESTORED' }*.skipped ==
        (newAnnouncement ? [] : ['external-playback'])

    where:
    newAnnouncement << [true, false]
  }

  def "confirmed local stream replacement cancels pending restore before playback attributes change"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [
      correlationId: 'replaced', currentUri: 'radio', announcementUri: 'http://example.test/tts.mp3'
    ]
    driver.restoreGroupAnnouncementPlayback('replaced')
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 500), driver.asyncCalls.last().data)
    Map retry = driver.scheduled.find { List job -> job[1] == 'retryAnnouncementPlaybackUri' }[2].data

    when:
    driver.setAVTransportURIAndPlay('http://example.test/replacement.mp3')
    driver.setAVTransportURIAndPlayCallback(new hubitat.scheduling.AsyncResponse(status: 200), driver.asyncCalls.last().data)
    driver.retryAnnouncementPlaybackUri(retry)

    then:
    driver.asyncCalls.count { it.cb == 'announcementPlaybackUriRestored' } == 1
    playerStateMessages().findAll { it.phase == 'PLAYBACK_RESTORED' }*.skipped == ['external-playback']
  }

  def "synchronous URI restore dispatch failure completes the transaction"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [correlationId: 'dispatch', currentUri: 'radio']
    MetaClass originalMetaClass = driver.getMetaClass()
    driver.setMetaClass(new ExpandoMetaClass(driver.class, false, true))
    driver.getMetaClass().initialize()
    driver.metaClass.getParent = { -> new Expando(
      playerAttributeUpdate: { Map payload -> applyMockAppPlayerAttributes(payload) },
      playerStateUpdate: { Map payload -> parentPlayerUpdates << payload },
      groupAnnouncementProgress: { String corr, String phase, Map payload, String dni -> parentPlayerUpdates << ([kind:'groupAnnouncementProgress', correlationId:corr, phase:phase] + (payload ?: [:])) }) }
    driver.metaClass.postSonosSoapAsync = { String callback, Map params, Map data ->
      throw new IllegalStateException('HTTP dispatch failed')
    }

    when:
    driver.restoreGroupAnnouncementPlayback('dispatch')

    then:
    driver.runtimeState.announcementPlaybackRestore == null
    driver.runtimeState.lastAnnouncementPlaybackRestoreFailure.reason == 'uri-dispatch-failed'
    playerStateMessages().findAll { it.phase == 'PLAYBACK_RESTORED' }*.restored == [false]

    cleanup:
    driver.setMetaClass(originalMetaClass)
  }

  def "a failed seek leaves playback restore incomplete after successful resume"() {
    given:
    driver.runtimeState.announcementPlaybackSnapshot = [
      correlationId: 'seek-failure', currentUri: 'x-rincon-queue:RINCON_TEST#0', track: 3, transportState: 'PLAYING'
    ]

    when:
    driver.restoreGroupAnnouncementPlayback('seek-failure')
    driver.announcementPlaybackUriRestored(new hubitat.scheduling.AsyncResponse(status: 200), driver.asyncCalls.last().data)
    driver.announcementPlaybackSeekRestored(new hubitat.scheduling.AsyncResponse(status: 500), driver.asyncCalls.last().data)
    driver.announcementPlaybackTransportRestored(new hubitat.scheduling.AsyncResponse(status: 200), driver.asyncCalls.last().data)

    then:
    driver.runtimeState.lastAnnouncementPlaybackRestoreFailure.stage == 'track'
    playerStateMessages().findAll { it.phase == 'PLAYBACK_RESTORED' }*.restored == [false]
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
    driver.state.playbackStateObservedAt == null
    driver.pendingLocalDeviceEvents[dni]?.containsKey('playbackState') != true
    driver.pendingLocalDeviceEvents[dni]?.containsKey('playbackStateObservedAt') != true
    driver.flushPendingLocalDeviceEvents()
    parentPlayerUpdates.any { Map update -> update.kind == 'deviceAttributes' && update.attributes.lastUpdated != null }
    !playerStateMessages().any { Map update -> update.kind != 'deviceAttributes' }
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

  def "AVTransport and HTControl UPnP report home theater source and input connection"() {
    given:
    String dni = driver.device.getDeviceNetworkId()
    driver.pendingLocalDeviceEvents.remove(dni)
    String metadata = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item><dc:title>RINCON_TEST</dc:title><upnp:class>object.item.audioItem.linein.homeTheater</upnp:class></item></DIDL-Lite>'
    Map snapshot = [avTransportURI: 'x-sonos-htastream:RINCON_TEST:spdif',
      avTransportURIMetaData: driver.parseAvtMetaData(metadata), transportState: 'PLAYING']
    driver.runtimeState.lastTransportPlaybackSnapshot = snapshot

    when:
    driver.observeUpnpPlaybackSource(snapshot)
    driver.handleHtControlPayload('<propertyset><property><TOSLinkConnected>0</TOSLinkConnected></property></propertyset>')
    driver.flushPendingLocalDeviceEvents()

    then:
    driver.state.audioSourceMetadataType == 'linein.homeTheater'
    driver.state.audioSourceSignalStatus == 'no-signal'
    (driver.state.audioSourceSignalObservedAt as Long) > 0L
    driver.pendingLocalDeviceEvents[dni]?.containsKey('audioSourceSignalStatus') != true
    driver.pendingLocalDeviceEvents[dni]?.containsKey('audioSourceSignalObservedAt') != true
    playerStateMessages().isEmpty()
  }

  def "UPnP HTControl connection event updates source signal without WebSocket metadata"() {
    given:
    driver.state.audioSourceMetadataType = 'linein.homeTheater'
    driver.state.audioSourceSignalStatus = 'no-signal'
    driver.state.audioSourceSignalObservedAt = driver.now() - 1000L
    String metadata = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item><upnp:class>object.item.audioItem.linein.homeTheater</upnp:class></item></DIDL-Lite>'
    driver.observeUpnpPlaybackSource([avTransportURI: 'x-sonos-htastream:RINCON_TEST:spdif',
      avTransportURIMetaData: driver.parseAvtMetaData(metadata)])
    driver.runtimeState.lastTransportPlaybackSnapshot = [avTransportURI: 'x-sonos-htastream:RINCON_TEST:spdif',
      avTransportURIMetaData: driver.parseAvtMetaData(metadata)]

    when:
    driver.handleHtControlPayload('<propertyset><property><TOSLinkConnected>1</TOSLinkConnected></property></propertyset>')

    then:
    driver.state.audioSourceSignalStatus == 'signal'
  }

  def "WebSocket playback metadata no longer drives home theater signal observations"() {
    given:
    driver.state.audioSourceSignalStatus = 'no-signal'
    Long observedAt = driver.now() - 1000L
    driver.state.audioSourceSignalObservedAt = observedAt

    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playbackMetadata', type: 'metadataStatus'],
      [container: [type: 'linein.homeTheater.hdmi', htInputFormat: [streamDescription: 'Signal']]]
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
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    com.hubitat.app.DeviceWrapper child = new com.hubitat.app.DeviceWrapper(dataValues: [command: 'Mute'])

    when:
    driver.componentOn(child)
    driver.componentOff(child)

    then:
    driver.asyncCalls.collect { it.params.headers.SOAPAction } == [
      'urn:schemas-upnp-org:service:RenderingControl:1#SetMute',
      'urn:schemas-upnp-org:service:RenderingControl:1#SetMute']
    driver.asyncCalls.collect { it.params.body.contains('<DesiredMute>true</DesiredMute>') } == [true, false]
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
    Integer settingsUpdated = 0
    lifecycleDriver.metaClass.initialize = { -> initialized++ }
    lifecycleDriver.metaClass.handleSettingsUpdate = { -> settingsUpdated++ }

    when:
    lifecycleDriver.installed()
    lifecycleDriver.updated()

    then:
    initialized == 1
    settingsUpdated == 1
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

  void applyMockAppPlayerAttributes(Map payload) {
    parentPlayerUpdates << new LinkedHashMap(payload)
    Map attributes = payload?.attributes instanceof Map ? (Map)payload.attributes : [:]
    attributes.each { Object name, Object value ->
      if(name == 'currentFavorite' && value instanceof Map) {
        value = ((Map)value).label?.toString() ?: 'No favorite playing'
      }
      driver.device.sendEvent(name: name.toString(), value: value)
    }
  }

  def setup() {
    parentPlayerUpdates.clear()
    parentPlayerRequests.clear()
    parentPlayerPresence.clear()
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
    driver.zgtMemberHostByPlayerId.clear()
    driver.metaClass.getParent = { -> new Expando(
      playerStateUpdate: { Map payload -> parentPlayerUpdates << new LinkedHashMap(payload) },
      playerAppRequest: { Map payload -> parentPlayerRequests << new LinkedHashMap(payload) },
      putPlayerUpnpEvents: { Map payload -> parentPlayerUpdates << new LinkedHashMap(payload) },
      putAvtEvents: { String rincon, List<Map> events -> parentPlayerUpdates << [kind:'avtEvents', rincon:rincon, events:new ArrayList(events)] },
      putZgtTopology: { String sourceDni, Map eventsByGroupId -> parentPlayerUpdates << [kind:'zgtTopology', sourceDni:sourceDni, eventsByGroupId:new LinkedHashMap(eventsByGroupId)] },
      playerArrived: { String rincon, String dni, String name -> parentPlayerPresence << [kind:'arrived', rincon:rincon, dni:dni, name:name] },
      playerDeparted: { String rincon, String dni, String name -> parentPlayerPresence << [kind:'departed', rincon:rincon, dni:dni, name:name] },
      putGrcEvents: { String rincon, List<Map> events -> parentPlayerUpdates << [kind:'grcEvents', rincon:rincon, events:new ArrayList(events)] },
      groupAnnouncementProgress: { String corr, String phase, Map payload, String dni -> parentPlayerUpdates << ([kind:'groupAnnouncementProgress', correlationId:corr, phase:phase] + (payload ?: [:])) }
    ) }
    driver.device.dataValues.localUpnpHost = '192.168.1.50:1400'
    driver.device.dataValues.websocketUrl = 'wss://192.168.1.50:1443/websocket/api'
    driver.location.metaClass.getHub = { -> new Expando(localIP: '192.168.1.4', localSrvPortTCP: 39501) }
    driver.upnpRequestQueues.clear()
    driver.eventTimestamps.clear()
    driver.device.dataValues.groupId = 'GROUP_TEST'
    driver.device.dataValues.websocketStatus = 'open'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_TEST'
    driver.metaClass.getParent = { -> new Expando(
      playerAttributeUpdate: { Map payload -> applyMockAppPlayerAttributes(payload) },
      flushGroupDeviceState: { String coordinatorId, Map volumeAttrs, Map playbackAttrs ->
        parentPlayerUpdates << [kind: 'groupDeviceState', coordinatorId: coordinatorId,
          volumeAttrs: volumeAttrs ? new LinkedHashMap(volumeAttrs) : null,
          playbackAttrs: playbackAttrs ? new LinkedHashMap(playbackAttrs) : [:]]
      },
      playerCommandRequest: { Map payload -> parentPlayerRequests << new LinkedHashMap(payload); true },
      playerGroupFavoriteOperationUpdate: { Map payload -> parentPlayerUpdates << [kind: 'groupFavoriteOperation', payload: new LinkedHashMap(payload)] },
      putAvtEvents: { String rincon, List<Map> events -> parentPlayerUpdates << [kind:'avtEvents', rincon:rincon, events:new ArrayList(events)] },
      putGrcEvents: { String rincon, List<Map> events -> parentPlayerUpdates << [kind:'grcEvents', rincon:rincon, events:new ArrayList(events)] },
      putPlayerUpnpEvents: { Map payload -> parentPlayerUpdates << new LinkedHashMap(payload) },
      putZgtTopology: { String sourceDni, Map eventsByGroupId -> parentPlayerUpdates << [kind:'zgtTopology', sourceDni:sourceDni, eventsByGroupId:new LinkedHashMap(eventsByGroupId)] },
      playerArrived: { String rincon, String dni, String name -> parentPlayerPresence << [kind:'arrived', rincon:rincon, dni:dni, name:name] },
      playerDeparted: { String rincon, String dni, String name -> parentPlayerPresence << [kind:'departed', rincon:rincon, dni:dni, name:name] },
      groupAnnouncementProgress: { String corr, String phase, Map payload, String dni -> parentPlayerUpdates << ([kind:'groupAnnouncementProgress', correlationId:corr, phase:phase] + (payload ?: [:])) }
    ) }
    driver.clearFavoritesMap()
    driver.clearPlaylistsMap()
    driver.favoriteRetryState.clear()
    driver.playlistRetryState.clear()
    driver.pendingGroupedPlayerVolumes.clear()
    driver.pendingLocalDeviceEvents.clear()
    driver.lastLocalDeviceEvents.clear()
    driver.lastPlaybackState.clear()
    driver.lastMetadataContainerId.clear()
  }

  def "player escaping helpers handle HTML, XML, and layered XML entities consistently"() {
    expect:
    driver.escapeHiddenStateHtml("A&B <'\"") == 'A&amp;B &lt;&#39;&quot;'
    driver.escapeQueueHtml("A&B <'\"") == 'A&amp;B &lt;&#39;&quot;'
    driver.escapeSoapXmlText("A&B <'\"") == 'A&amp;B &lt;&apos;&quot;'
    driver.unEscapeMetaData('&amp;lt;title&amp;gt;A&amp;lt;/title&amp;gt;') == '<title>A</title>'
    driver.unescapeIfNeeded('&lt;x&gt;') == '<x>'
    driver.normalizeLogLevel('INFO') == 'info'
    driver.splitTokens('a,,b,', ',') == ['a', 'b']
    driver.splitTokens(null, ',').empty
    driver.parseSonosDurationSeconds('1:02:03') == 3723
    driver.parseSonosDurationSeconds('invalid') == null
  }


  def "every primary websocket open subscribes to audioClip"() {
    when:
    driver.setWebSocketStatus('open')

    then:
    websocketMessages.any { String message ->
      List parsed = (List)new JsonSlurper().parseText(message)
      ((Map)parsed[0]).namespace == 'audioClip' && ((Map)parsed[0]).command == 'subscribe'
    }
  }

  def "audioClip subscription is sent ahead of queued clips"() {
    given:
    driver.device.dataValues.websocketStatus = 'open'
    String clip = JsonOutput.toJson([[namespace: 'audioClip', command: 'loadAudioClip', playerId: 'RINCON_TEST'],
      [name: 'queued-clip', clipType: 'CUSTOM', appId: 'test', priority: 'HIGH', streamUrl: 'http://example.test/clip.mp3']])
    driver.queueWebsocketMessage(clip, 0, [kind: 'audioClip', correlationId: 'queued-clip'])

    when:
    driver.subscribeToAudioClip()

    then:
    websocketMessages.size() == 1
    ((List)new JsonSlurper().parseText(websocketMessages[0]))[0].command == 'subscribe'
    driver.runtimeState.websocketOutboundQueue*.correlationId == ['queued-clip']
  }

  def "player initialization starts subscriptions and websocket locally"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50:1400'
    driver.device.dataValues.websocketUrl = 'wss://192.168.1.50:1443/websocket/api'
    driver.device.dataValues.isGroupCoordinator = 'false'

    when:
    driver.initialize()

    then:
    websocketConnections.size() == 1
    websocketConnections[0].last() == 'wss://192.168.1.50:1443/websocket/api'
    !parentPlayerUpdates.any { Map event -> event.kind?.toString()?.startsWith('initialization') }
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

  def "parse enqueues parent updates without calling the parent directly"() {
    when:
    driver.processWebsocketMessage(JsonOutput.toJson([
      [namespace: 'playback', type: 'playbackStatus'],
      [playbackState: 'PLAYBACK_STATE_PLAYING', itemId: 'track-1']
    ]))

    then:
    parentPlayerUpdates.empty
    !driver.scheduled.any { List job -> job[1] == 'flushPendingParentUpdates' }
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].lastUpdated != null

    when:
    drainParentBridge()

    then:
    // playbackObservation now via lastUpdated + attribute subscription, not outbox
    parentPlayerUpdates.isEmpty()
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].lastUpdated != null
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


  def cleanup() {
    GroovySystem.metaClassRegistry.removeMetaClass(driver.getClass())
    driver.metaClass = null
  }

  def "local event enqueue re-arms a drain when its scheduled marker is stale"() {
    given:
    driver.state.localDeviceEventDrainScheduled = true

    when:
    driver.queuePlayerAttributeUpdate('isGrouped', 'on')

    then:
    driver.scheduled.any { List item -> item[1] == 'flushPendingLocalDeviceEvents' }
    driver.state.localDeviceEventDrainScheduled == true
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].isGrouped == 'on'
  }

  def "player state changes are handed to the app without driver attribute events"() {
    given:
    List<Map> updates = []
    driver.metaClass.getParent = { -> new Expando(playerAttributeUpdate: { Map payload -> updates << new LinkedHashMap(payload) }) }

    when:
    driver.queuePlayerAttributeUpdate('currentTrackName', 'App Owned Track')
    driver.flushPendingLocalDeviceEvents()

    then:
    updates.size() == 1
    updates[0].playerDni == 'SONOS-TEST-DNI'
    updates[0].attributes.currentTrackName == 'App Owned Track'
    driver.device.events.isEmpty()
    driver.device.currentValue('currentTrackName') == null
  }

  def "local event rescheduling preserves the original maximum-wait deadline"() {
    given:
    List<Long> clock = [1000L]
    driver.metaClass.now = { -> clock[0] }

    when:
    driver.queuePlayerAttributeUpdate('groupMemberCount', 2)
    Long firstDueAt = driver.state.localDeviceEventDrainDueAt as Long
    clock[0] = 1300L
    driver.queuePlayerAttributeUpdate('groupMemberCount', 1)

    then:
    firstDueAt == 1500L
    driver.state.localDeviceEventDrainDueAt == firstDueAt
    driver.scheduled.last()[0] == 200
    driver.scheduled.last()[1] == 'flushPendingLocalDeviceEvents'
  }

  def "local event drain schedules values enqueued while it sends a batch"() {
    given:
    driver.queuePlayerAttributeUpdate('groupName', 'Cora’s Room + 1')
    driver.device.metaClass.sendEvent = { Map event ->
      delegate.events << event
      delegate.currentValues[event.name as String] = event.value
      if(event.name == 'groupName') {
        driver.queuePlayerAttributeUpdate('groupMemberCount', 2)
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
    driver.queuePlayerAttributeUpdate('isGrouped', 'on')
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

  def "AVT parse relays sendEvent maps without publishing player events"() {
    given:
    String body = avtBody('<TransportState val="PLAYING"/>')
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /avt HTTP/1.1\r\n' +
      'X-SONOS-SERVICETYPE: AVTransport\r\nSEQ: 1\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['X-SONOS-SERVICETYPE': 'AVTransport', 'SEQ': '1'], body: body]
    }

    when:
    driver.parse(raw)

    then:
    driver.device.events.empty
    driver.scheduled.any { List row -> row[1] == 'relayParsedUpnpToParent' && row[2].data.service == 'AVT' &&
      row[2].data.events.any { Map event -> event.name == 'status' && event.value == 'playing' } }
  }

  def "ZGT notification logs header values with SEQ 0"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    String zoneState = '<ZoneGroups><ZoneGroup ID="G1:1" Coordinator="RINCON_TEST">' +
      '<ZoneGroupMember UUID="RINCON_TEST" ZoneName="Test Speaker" Location="http://192.168.1.50:1400/xml/device_description.xml"/>' +
      '<ZoneGroupMember UUID="RINCON_B" ZoneName="Bedroom" Location="http://192.168.1.51:1400/xml/device_description.xml"/>' +
      '</ZoneGroup></ZoneGroups>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String body = '<propertyset><property><ZoneGroupState>' + escapedState + '</ZoneGroupState></property></propertyset>'
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /zgt HTTP/1.1\r\n' +
      'X-SONOS-SERVICETYPE: ZoneGroupTopology\r\nSEQ: 0\r\nSID: uuid:zgt-test\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['X-SONOS-SERVICETYPE': 'ZoneGroupTopology', 'SEQ': '0', 'SID': 'uuid:zgt-test', 'NT': 'upnp:event', 'NTS': 'upnp:propchange'], body: body]
    }

    when:
    driver.parse(raw)

    then:
    driver.logs.any { String entry -> entry.contains('UPnP NOTIFY header values') && entry.contains('SEQ=0') && entry.contains('SID=uuid:zgt-test') && entry.contains('X-SONOS-SERVICETYPE=ZoneGroupTopology') }
    driver.logs.any { String entry -> entry.contains('UPnP NOTIFY parsed') && entry.contains('seq=0') }
    driver.zgtMemberHostByPlayerId['RINCON_TEST'] == '192.168.1.50:1400'
    driver.zgtMemberHostByPlayerId['RINCON_B'] == '192.168.1.51:1400'
  }

  def "ZGT notification logs header values with SEQ greater than 0"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    String zoneState = '<ZoneGroups><ZoneGroup ID="G1:1" Coordinator="RINCON_TEST">' +
      '<ZoneGroupMember UUID="RINCON_TEST" ZoneName="Test Speaker"/></ZoneGroup></ZoneGroups>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String body = '<propertyset><property><ZoneGroupState>' + escapedState + '</ZoneGroupState></property></propertyset>'
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /zgt HTTP/1.1\r\n' +
      'X-SONOS-SERVICETYPE: ZoneGroupTopology\r\nSEQ: 5\r\nSID: uuid:zgt-test\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['X-SONOS-SERVICETYPE': 'ZoneGroupTopology', 'SEQ': '5', 'SID': 'uuid:zgt-test', 'NT': 'upnp:event', 'NTS': 'upnp:propchange'], body: body]
    }

    when:
    driver.parse(raw)

    then:
    driver.logs.any { String entry -> entry.contains('UPnP NOTIFY header values') && entry.contains('SEQ=5') && entry.contains('SID=uuid:zgt-test') }
  }

  def "AVT notification logs header values with SEQ 0"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    String body = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;&lt;TransportState val="PLAYING"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /avt HTTP/1.1\r\n' +
      'X-SONOS-SERVICETYPE: AVTransport\r\nSEQ: 0\r\nSID: uuid:avt-test\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['X-SONOS-SERVICETYPE': 'AVTransport', 'SEQ': '0', 'SID': 'uuid:avt-test', 'NT': 'upnp:event', 'NTS': 'upnp:propchange'], body: body]
    }

    when:
    driver.parse(raw)

    then:
    driver.logs.any { String entry -> entry.contains('UPnP NOTIFY header values') && entry.contains('SEQ=0') && entry.contains('SID=uuid:avt-test') && entry.contains('X-SONOS-SERVICETYPE=AVTransport') }
    driver.logs.any { String entry -> entry.contains('UPnP NOTIFY parsed') && entry.contains('seq=0') }
  }

  def "AVT parser preserves nested DIDL and ampersand entities"() {
    given:
    String didl = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">' +
      '<item><dc:title>Flowers &amp; Flags</dc:title><upnp:artist>Artist &amp; Co</upnp:artist>' +
      '<upnp:album>Album &amp; Friends</upnp:album><res protocolInfo="http-get:*:audio/mpeg:*">' +
      'http://media.example.test/song.mp3</res></item></DIDL-Lite>'
    String encodedMetadata = StringEscapeUtils.escapeXml10(didl)
    String body = avtBody('<TransportState val="PLAYING"/>' +
      '<CurrentTrackURI val="http://media.example.test/song.mp3?one=1&amp;two=2"/>' +
      '<CurrentTrackMetaData val="' + encodedMetadata + '"/>')

    when:
    Map snapshot = driver.parseAvtFullSnapshot(body)

    then:
    snapshot.transportState == 'PLAYING'
    snapshot.currentTrackURI == 'http://media.example.test/song.mp3?one=1&two=2'
    snapshot.currentTrackMetaData.title == 'Flowers & Flags'
    snapshot.currentTrackMetaData.creator == 'Artist & Co'
    snapshot.currentTrackMetaData.album == 'Album & Friends'
  }

  def "AVT DIDL metadata lookup supports Sonos default and prefixed namespaces"() {
    given:
    String didl = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" ' +
      'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" ' +
      'xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" ' +
      'xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">' +
      '<item><res>sonos:track</res><r:streamContent></r:streamContent>' +
      '<upnp:albumArtURI>/getaa?s=1&amp;u=track</upnp:albumArtURI>' +
      '<dc:title>Peepers On Crane Pond</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class>' +
      '<dc:creator>Adirondack Environments</dc:creator><upnp:album>Peepers On Crane Pond</upnp:album>' +
      '</item></DIDL-Lite>'

    when:
    Map<String, Object> metadata = driver.parseAvtMetaData(didl)

    then:
    metadata.title == 'Peepers On Crane Pond'
    metadata.creator == 'Adirondack Environments'
    metadata.album == 'Peepers On Crane Pond'
    metadata.albumArtURI == '/getaa?s=1&u=track'
    metadata.class == 'object.item.audioItem.musicTrack'
  }

  def "AVT parser emits next track metadata from r-prefixed LastChange elements"() {
    given:
    String current = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item><dc:title>My Own Prison</dc:title><dc:creator>Creed</dc:creator><upnp:album>My Own Prison</upnp:album><upnp:albumArtURI>/current.jpg</upnp:albumArtURI></item></DIDL-Lite>'
    String next = '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item><dc:title>Man in the Box</dc:title><dc:creator>Alice In Chains</dc:creator><upnp:album>Facelift</upnp:album><upnp:albumArtURI>/getaa?s=1&amp;u=next</upnp:albumArtURI></item></DIDL-Lite>'
    String fragment = '<r:NextTrackURI val="x-sonos-http:song%3a274953022.mp4?sid=204&amp;flags=73768"/>' +
      '<r:NextTrackMetaData val="' + StringEscapeUtils.escapeXml10(next) + '"/>' +
      '<CurrentTrackMetaData val="' + StringEscapeUtils.escapeXml10(current) + '"/>'
    String xml = '<propertyset><property><LastChange>' + StringEscapeUtils.escapeXml10(
      '<Event xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/"><InstanceID val="0">' + fragment +
      '</InstanceID></Event>') + '</LastChange></property></propertyset>'

    when:
    Map snapshot = driver.parseAvtFullSnapshot(xml)
    List<Map> events = driver.buildAvtEvents(snapshot, [:])

    then:
    snapshot.hasNextTrackMetaData
    snapshot.nextTrackMetaData.title == 'Man in the Box'
    snapshot.nextTrackMetaData.creator == 'Alice In Chains'
    snapshot.nextTrackMetaData.album == 'Facelift'
    snapshot.nextTrackMetaData.albumArtURI == '/getaa?s=1&u=next'
    events.find { Map event -> event.name == 'nextTrackName' }?.value == 'Man in the Box'
    events.find { Map event -> event.name == 'nextArtistName' }?.value == 'Alice In Chains'
    events.find { Map event -> event.name == 'nextAlbumName' }?.value == 'Facelift'
    events.find { Map event -> event.name == 'nextTrackAlbumArtURI' }?.value == '/getaa?s=1&u=next'
  }

  def "AVT explicitly clears next-track fields when NextTrackMetaData is empty"() {
    given:
    String fragment = '<r:NextTrackMetaData val=""/>'
    String xml = '<propertyset><property><LastChange>' + StringEscapeUtils.escapeXml10(
      '<Event xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/"><InstanceID val="0">' + fragment +
      '</InstanceID></Event>') + '</LastChange></property></propertyset>'

    when:
    List<Map> events = driver.buildAvtEvents(driver.parseAvtFullSnapshot(xml), [:])

    then:
    events.find { Map event -> event.name == 'nextTrackName' }?.value == 'Not Available'
    events.find { Map event -> event.name == 'nextArtistName' }?.value == 'Not Available'
    events.find { Map event -> event.name == 'nextAlbumName' }?.value == 'Not Available'
    events.find { Map event -> event.name == 'nextTrackAlbumArtURI' }?.value == 'Not Available'
  }

  def "bounded XML lookups match namespace-prefixed sleep timer and queue fields"() {
    given:
    String didlXml = '<DIDL-Lite xmlns:dc="urn:dc"><item id="Q:0/1"><dc:title>Queue Title</dc:title>' +
      '<res protocolInfo="http-get:*:audio/mpeg:*">http://media.test/q</res></item></DIDL-Lite>'
    GPathResult sleepXml = new XmlSlurper().parseText(
      '<s:Envelope xmlns:s="urn:soap"><s:Body><u:Response xmlns:u="urn:sonos"><RemainingSleepTimerDuration>00:04:00</RemainingSleepTimerDuration>' +
      '<CurrentSleepTimerGeneration>12</CurrentSleepTimerGeneration></u:Response></s:Body></s:Envelope>')
    GPathResult queueXml = new XmlSlurper().parseText(
      '<s:Envelope xmlns:s="urn:soap"><s:Body><u:BrowseResponse xmlns:u="urn:content"><NumberReturned>1</NumberReturned>' +
      '<Result>' + StringEscapeUtils.escapeXml10(didlXml) + '</Result>' +
      '<UpdateID>27</UpdateID></u:BrowseResponse></s:Body></s:Envelope>')
    GPathResult browseResponse = driver.findDirectXmlChild(driver.findDirectXmlChild(queueXml, 'Body'), 'BrowseResponse')
    GPathResult didl = new XmlSlurper().parseText(driver.findQueueXmlText(browseResponse, 'Result'))
    GPathResult item = driver.findDirectXmlChild(didl, 'item')

    expect:
    driver.findSleepTimerXmlText(sleepXml, 'RemainingSleepTimerDuration') == '00:04:00'
    driver.findSleepTimerXmlText(sleepXml, 'CurrentSleepTimerGeneration') == '12'
    driver.findSoapBodyField(queueXml, 'UpdateID') == '27'
    driver.findQueueXmlText(item, 'title') == 'Queue Title'
    item['res']['@protocolInfo'].toString() == 'http-get:*:audio/mpeg:*'
  }

  def "base SOAP builder escapes control values"() {
    when:
    String body = driver.getBaseControlXML(driver.AVTransport, 'SetAVTransportURI', [CurrentURI: 'x?a=1&b=<two>'])

    then:
    body.contains('<CurrentURI>x?a=1&amp;b=&lt;two&gt;</CurrentURI>')
  }

  def "ZGT packet without service type header is not applied"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    String zoneState = '<ZoneGroups><ZoneGroup ID="G1:1" Coordinator="RINCON_TEST">' +
      '<ZoneGroupMember UUID="RINCON_TEST" ZoneName="Test Speaker"/></ZoneGroup></ZoneGroups>'
    String escapedState = zoneState.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    String body = '<propertyset><property><ZoneGroupState>' + escapedState + '</ZoneGroupState></property></propertyset>'
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /zgt HTTP/1.1\r\nSEQ: 3\r\nSID: uuid:zgt-legacy\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['SEQ': '3', 'SID': 'uuid:zgt-legacy', 'NOTIFY /zgt HTTP/1.1': ''], body: body]
    }

    when:
    driver.parse(raw)

    then:
    !driver.logs.any { String entry -> entry.contains('UPnP NOTIFY header values') }
    !parentPlayerUpdates.any { Map item -> item.kind == 'zgtTopology' }
    driver.logs.any { String entry -> entry.contains('Could not determine service type') || entry.contains('unrecognized service type') }
  }

  def "AVT packet without service type header is not applied"() {
    given:
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    String body = '<propertyset><property><LastChange>&lt;Event&gt;&lt;InstanceID val="0"&gt;&lt;TransportState val="PLAYING"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;</LastChange></property></propertyset>'
    String raw = 'mac:AA:BB:CC:DD:EE:FF\r\nNOTIFY /avt HTTP/1.1\r\nSEQ: 3\r\nSID: uuid:avt-legacy\r\n\r\n' + body
    driver.metaClass.getMapForRaw = { String rawMessage ->
      [headers: ['SEQ': '3', 'SID': 'uuid:avt-legacy', 'NOTIFY /avt HTTP/1.1': ''], body: body]
    }

    when:
    driver.parse(raw)

    then:
    !driver.logs.any { String entry -> entry.contains('UPnP NOTIFY header values') }
    !parentPlayerUpdates.any { Map item -> item.kind == 'avtEvents' }
    driver.logs.any { String entry -> entry.contains('Could not determine service type') || entry.contains('unrecognized service type') }
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

  def "becoming coordinator does not clear playback attributes in the player driver"() {
    given:
    driver.device.dataValues.id = 'RINCON_TEST'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_OLD_COORD'
    driver.device.dataValues.isGroupCoordinator = 'false'
    driver.device.currentValues.currentTrackName = 'Mama, I\'m Coming Home'
    driver.device.currentValues.currentArtistName = 'Ozzy Osbourne'

    when:
    driver.setGroupCoordinatorId('RINCON_TEST')

    then:
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].groupCoordinatorId == 'RINCON_TEST'
    !driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].containsKey('currentTrackName')
    !driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'].containsKey('currentArtistName')
    driver.device.currentValues.currentTrackName == 'Mama, I\'m Coming Home'
    driver.device.currentValues.currentArtistName == 'Ozzy Osbourne'
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

  def "player driver leaves its public player attributes to the parent app"() {
    expect:
    !new File('../Drivers/Component/SonosAdvPlayer.groovy').text.contains('device.sendEvent(')
  }

  def "publication watchdog repairs missing local drain jobs"() {
    given:
    driver.scheduled.clear()
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'] = [groupCoordinatorId: 'RINCON_COORD']
    driver.state.localDeviceEventDrainScheduled = true

    when:
    driver.verifyPendingPublicationDrains()

    then:
    driver.scheduled.any { List call -> call[1] == 'flushPendingLocalDeviceEvents' }
    driver.scheduled.any { List call -> call[1] == 'verifyPendingPublicationDrains' }
  }

  def "initialization reset clears only this player's pending publications"() {
    given:
    String otherDni = 'SONOS-OTHER-DNI'
    driver.state.pendingComponentChildEvents = ['child-dni': [switch: 'on']]
    driver.state.componentChildDrainScheduled = true
    driver.state.localDeviceEventDrainScheduled = true
    driver.state.localDeviceEventDrainDueAt = 123L
    driver.pendingLocalDeviceEvents['SONOS-TEST-DNI'] = [groupCoordinatorId: 'RINCON_OLD']
    driver.lastLocalDeviceEvents['SONOS-TEST-DNI'] = [isGroupCoordinator: 'on']
    driver.pendingLocalDeviceEvents[otherDni] = [status: 'playing']
    driver.scheduled << [1000, 'flushPendingLocalDeviceEvents']
    driver.scheduled << [1000, 'drainComponentChildEvents']

    when:
    driver.resetPendingStatePublicationForInitialization()

    then:
    !driver.state.containsKey('pendingComponentChildEvents')
    !driver.state.containsKey('componentChildDrainScheduled')
    !driver.state.containsKey('localDeviceEventDrainScheduled')
    !driver.pendingLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
    !driver.lastLocalDeviceEvents.containsKey('SONOS-TEST-DNI')
    driver.pendingLocalDeviceEvents[otherDni].status == 'playing'
    ['flushPendingLocalDeviceEvents', 'drainComponentChildEvents',
     'drainComponentChildEvents'].every { String method -> driver.unschedules.contains(method) }
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

  def "ZGT notification with unrelated service properties relays parsed topology"() {
    given:
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
      [headers: ['X-SONOS-SERVICETYPE': 'ZoneGroupTopology'], body: body]
    }

    when:
    driver.parse(raw)

    then:
    driver.scheduled.any { List row -> row[1] == 'relayParsedUpnpToParent' &&
      row[2].data.service == 'ZGT' && row[2].data.eventsByGroupId['G1'].find { Map e -> e.name == 'groupCoordinatorId' }.value == 'RINCON_TEST' }
    parentPlayerUpdates.isEmpty()  // hand-off is queued; parent is called after parse returns
  }

  def "every player subscribes to ZGT without app election state"() {
    given:
    List<String> zgtCalls = []
    driver.metaClass.subscribeToZgtEvents = { -> zgtCalls << 'subscribe' }
    driver.metaClass.subscribeToMrGrcEvents = { -> }

    when:
    driver.initialize()

    then:
    zgtCalls == ['subscribe']
    !driver.device.dataValues.containsKey('isZgtSubscriber')
    !driver.device.dataValues.containsKey('zgtLeaderGeneration')
  }

  def "group topology is queried through ZoneGroupTopology UPnP"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    String zones = '<ZoneGroups><ZoneGroup ID="GROUP-TEST" Coordinator="RINCON_TEST"><ZoneGroupMember UUID="RINCON_TEST" ZoneName="Test" Location="http://192.168.1.50:1400/xml/device_description.xml"/><ZoneGroupMember UUID="RINCON_B" ZoneName="Bedroom" Location="http://192.168.1.51:1400/xml/device_description.xml"/></ZoneGroup></ZoneGroups>'

    when:
    driver.playerGetGroupsFull()
    Map request = driver.asyncCalls.last()
    String soap = '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetZoneGroupStateResponse xmlns:u="urn:schemas-upnp-org:service:ZoneGroupTopology:1"><ZoneGroupState>' +
      StringEscapeUtils.escapeXml10(zones) + '</ZoneGroupState></u:GetZoneGroupStateResponse></s:Body></s:Envelope>'
    driver.zoneGroupTopologySoapCallback(new hubitat.scheduling.AsyncResponse(status: 200, data: soap), request.data)

    then:
    request.params.headers.SOAPAction == 'urn:schemas-upnp-org:service:ZoneGroupTopology:1#GetZoneGroupState'
    Map relay = driver.scheduled.find { List job -> job[1] == 'relayParsedUpnpToParent' }[2].data
    relay.service == 'ZGT'
    relay.eventsByGroupId['GROUP-TEST'].find { Map e -> e.name == 'groupCoordinatorId' }.value == 'RINCON_TEST'
    relay.eventsByGroupId['GROUP-TEST'].find { Map e -> e.name == 'groupPlayerIds' }.value == 'RINCON_TEST,RINCON_B'
    driver.zgtMemberHostByPlayerId['RINCON_B'] == '192.168.1.51:1400'
  }

  def "group membership retries after refreshing topology when a sibling host is not cached"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_TEST'
    driver.device.dataValues.id = 'RINCON_TEST'
    driver.zgtMemberHostByPlayerId.clear()

    when:
    driver.playerModifyGroupMembers(['RINCON_B'], [])

    then:
    driver.asyncCalls.any { Map call -> call.params.headers.SOAPAction.endsWith('#GetZoneGroupState') }
    driver.scheduled.any { List job -> job[1] == 'retryGroupMemberTopologyMutation' &&
      job[2].data.playerIdsToAdd == ['RINCON_B'] && job[2].data.retryAttempt == 1 }
  }

  def "group creation adds members with UPnP SetAVTransportURI"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.dataValues.groupPlayerIds = 'RINCON_TEST'
    seedGroupTopology('RINCON_TEST', [RINCON_TEST: '192.168.1.50', RINCON_B: '192.168.1.51'])

    when:
    driver.playerCreateGroupRequest(JsonOutput.toJson([args: [['RINCON_TEST', 'RINCON_B'], null]]))

    then:
    driver.asyncCalls.size() == 1
    driver.asyncCalls[0].params.uri == 'http://192.168.1.51:1400/MediaRenderer/AVTransport/Control'
    driver.asyncCalls[0].params.headers.SOAPAction.endsWith('#SetAVTransportURI')
    driver.asyncCalls[0].params.body.contains('<CurrentURI>x-rincon:RINCON_TEST</CurrentURI>')
  }

  def "group removal makes a follower a standalone coordinator over UPnP"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.dataValues.groupPlayerIds = 'RINCON_TEST,RINCON_B'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_TEST'
    seedGroupTopology('RINCON_TEST', [RINCON_TEST: '192.168.1.50', RINCON_B: '192.168.1.51'])

    when:
    driver.playerModifyGroupMembers([], ['RINCON_B'])

    then:
    driver.asyncCalls.size() == 1
    driver.asyncCalls[0].params.uri == 'http://192.168.1.51:1400/MediaRenderer/AVTransport/Control'
    driver.asyncCalls[0].params.headers.SOAPAction.endsWith('#BecomeCoordinatorOfStandaloneGroup')
  }

  def "groups websocket messages are ignored because topology arrives over UPnP"() {
    given:
    driver.device.currentValues.groupCoordinatorId = 'RINCON_OLD_COORD'
    driver.device.currentValues.isGrouped = 'on'
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
    !playerStateMessages().any { Map event -> event.kind == 'topology' }
    driver.device.events.every { Map event -> event.name != 'groupCoordinatorId' }
  }

  def "groups websocket messages do not update topology state"() {
    given:
    driver.device.dataValues.id = 'RINCON_ARC'
    driver.device.dataValues.groupId = 'GROUP_ARC'
    driver.device.dataValues.groupCoordinatorId = 'RINCON_ARC'
    driver.device.dataValues.groupPlayerIds = 'RINCON_ARC'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.pendingLocalDeviceEvents.remove(driver.device.deviceNetworkId)
    String message = JsonOutput.toJson([
      [type: 'groups', name: 'groups'],
      [groups: [[id: 'GROUP_ARC', name: 'Arc', coordinatorId: 'RINCON_ARC',
        playerIds: ['RINCON_ARC', 'RINCON_B']]],
       players: [[id: 'RINCON_ARC', name: 'Arc'], [id: 'RINCON_B', name: 'Bedroom']]]
    ])

    when:
    driver.processWebsocketMessage(message)

    then:
    driver.device.dataValues.groupPlayerIds == 'RINCON_ARC'
    driver.pendingLocalDeviceEvents[driver.device.deviceNetworkId]?.groupPlayerIds == null
    !playerStateMessages().any { Map event -> event.kind == 'topology' }
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

  def "second priority clip waits while unfinished regular clips stay in FIFO order"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.settings.ttsBoostAmount = 10
    driver.playerLoadAudioClip('http://example.test/a.mp3', 15G, 30, 'A')
    driver.playerLoadAudioClip('http://example.test/b.mp3', 15G, 10, 'B')
    driver.playerLoadAudioClipHighPriority('http://example.test/h1.mp3', 15G, 'H1', 20)
    List<Map> watchdogs = new ArrayList<Map>(driver.scheduled)

    when:
    driver.playerLoadAudioClipHighPriority('http://example.test/h2.mp3', 15G, 'H2', 10)
    driver.dequeueAudioClip()

    then:
    driver.runtimeState.activeAudioClipCorrelationId == 'H1'
    driver.runtimeState.audioClipPlaying == true
    driver.getDeviceAudioClipQueueHighPriority()*.correlationId == ['H2']
    driver.getDeviceAudioClipQueue()*.correlationId == ['A', 'B']
    driver.runtimeState.currentlyPlayingClip.correlationId == 'A'
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/h1.mp3'
    driver.logs.any { String entry -> entry.contains('Ignoring redundant audio clip dequeue while high-priority clip H1 is active') }
    driver.scheduled == watchdogs

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.runtimeState.activeAudioClipCorrelationId == 'H2'
    driver.getDeviceAudioClipQueue()*.correlationId == ['A', 'B']
    driver.runtimeState.currentlyPlayingClip.correlationId == 'A'

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/a.mp3'
    driver.runtimeState.activeAudioClipMessage.dispatchCount == 2
    driver.runtimeState.activeAudioClipCorrelationId != 'A'
    driver.getDeviceAudioClipQueue()*.uri == ['http://example.test/a.mp3', 'http://example.test/b.mp3']
    driver.getDeviceAudioClipQueue().peek().correlationId == driver.runtimeState.activeAudioClipCorrelationId

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/b.mp3'
    driver.getDeviceAudioClipQueue()*.uri == ['http://example.test/b.mp3']

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.getDeviceAudioClipQueue().empty
    driver.runtimeState.audioClipPlaying == null
  }

  def "regular clip interrupted by high priority resumes before regular clips enqueued during interruption"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.settings.ttsBoostAmount = 0
    driver.playerLoadAudioClip('http://example.test/a.mp3', 10G, 5, 'A')
    driver.playerLoadAudioClipHighPriority('http://example.test/h.mp3', 10G, 'H', 2)

    when:
    driver.playerLoadAudioClip('http://example.test/b.mp3', 10G, 5, 'B')

    then:
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/h.mp3'
    driver.getDeviceAudioClipQueue()*.uri == ['http://example.test/a.mp3', 'http://example.test/b.mp3']

    when: 'the high-priority clip finishes'
    driver.setAudioClipPlaying(false)

    then: 'the interrupted regular head resumes first'
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/a.mp3'
    driver.getDeviceAudioClipQueue()*.uri == ['http://example.test/a.mp3', 'http://example.test/b.mp3']

    when: 'the resumed regular clip finishes'
    driver.setAudioClipPlaying(false)

    then: 'the newer regular clip plays next'
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/b.mp3'
    driver.getDeviceAudioClipQueue()*.uri == ['http://example.test/b.mp3']

    when:
    driver.setAudioClipPlaying(false)

    then:
    driver.getDeviceAudioClipQueue().empty
  }

  def "final regular clip failure drops only the active queue head and leaves the tail queued"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.playerLoadAudioClip('http://example.test/a.mp3', 10G, 5, 'A')
    driver.playerLoadAudioClip('http://example.test/b.mp3', 10G, 5, 'B')

    when:
    driver.markAudioClipWatchdogFailure('A', 'FAILED_ACTIVE_TIMEOUT', 'regression test')

    then:
    driver.getDeviceAudioClipQueue()*.correlationId == ['B']
    driver.runtimeState.audioClipPlaying == false
    !driver.runtimeState.containsKey('activeAudioClipCorrelationId')
    !driver.runtimeState.containsKey('currentlyPlayingClip')
  }

  def "final high-priority failure preserves the unfinished regular queue head"() {
    given:
    driver.metaClass.getParent = { -> null }
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.playerLoadAudioClip('http://example.test/a.mp3', 10G, 5, 'A')
    driver.playerLoadAudioClipHighPriority('http://example.test/h1.mp3', 10G, 'H1', 2)
    driver.playerLoadAudioClipHighPriority('http://example.test/h2.mp3', 10G, 'H2', 2)

    when:
    driver.markAudioClipWatchdogFailure('H1', 'FAILED_ACTIVE_TIMEOUT', 'regression test')
    driver.dequeueAudioClip()

    then:
    driver.runtimeState.activeAudioClipMessage.uri == 'http://example.test/h2.mp3'
    driver.getDeviceAudioClipQueue()*.uri == ['http://example.test/a.mp3']
    driver.runtimeState.currentlyPlayingClip.correlationId == 'A'
  }

  def "bounded standard and high-priority admission schedules preserve dispatch order at varied completion times"() {
    given:
    List<String> timingModes = ['drain-after-admission', 'complete-each-admission', 'complete-at-midpoint']
    Integer scenarioCount = 0

    when:
    for(Integer regularCount = 0; regularCount <= 3; regularCount++) {
      for(Integer highCount = 0; highCount <= 3; highCount++) {
        if(regularCount + highCount == 0) { continue }
        List<List<String>> admissionSchedules = []
        appendAudioClipAdmissionSchedules(regularCount, highCount, [], admissionSchedules)
        admissionSchedules.each { List<String> admissionSchedule ->
          timingModes.each { String timingMode ->
            resetAudioClipQueueModel()
            List<Map> operations = []
            List<String> actualDispatches = []
            List<String> lastCorrelationId = [null]
            Integer regularIndex = 0
            Integer highIndex = 0

            admissionSchedule.eachWithIndex { String kind, Integer index ->
              String prefix = kind == 'R' ? 'R' : 'H'
              Integer messageIndex = kind == 'R' ? regularIndex++ : highIndex++
              String correlationId = "${prefix}${messageIndex}"
              String uri = "http://example.test/${correlationId}.mp3"
              Map operation = [kind: kind == 'R' ? 'regular' : 'high', priority: kind == 'H' ? 'HIGH' : 'REGULAR',
                correlationId: correlationId, uri: uri]
              operations.add(operation)

              if(kind == 'R') {
                driver.playerLoadAudioClip(uri, 1G, 1, correlationId)
              } else {
                driver.playerLoadAudioClipHighPriority(uri, 1G, correlationId, 1)
              }
              captureActiveAudioClipDispatch(actualDispatches, lastCorrelationId)

              Boolean completeNow = timingMode == 'complete-each-admission' ||
                (timingMode == 'complete-at-midpoint' && index == Math.floor(admissionSchedule.size() / 2.0d) as Integer)
              if(completeNow) {
                operations.add([kind: 'complete'])
                driver.setAudioClipPlaying(false)
                captureActiveAudioClipDispatch(actualDispatches, lastCorrelationId)
              }
            }

            Integer drainCount = 0
            Integer maxDrainCount = (regularCount + (highCount * 2) + 1) * 2
            while(driver.runtimeState.audioClipPlaying == true && drainCount < maxDrainCount) {
              operations.add([kind: 'complete'])
              driver.setAudioClipPlaying(false)
              captureActiveAudioClipDispatch(actualDispatches, lastCorrelationId)
              drainCount++
            }

            List<String> expectedDispatches = expectedAudioClipDispatches(operations)
            assert actualDispatches == expectedDispatches:
              "regularDepth=${regularCount}, highDepth=${highCount}, arrivals=${admissionSchedule}, timing=${timingMode}, expected=${expectedDispatches}, actual=${actualDispatches}"
            assert driver.getDeviceAudioClipQueue().isEmpty():
              "regular queue not drained: arrivals=${admissionSchedule}, timing=${timingMode}, queue=${driver.getDeviceAudioClipQueue()*.correlationId}"
            assert driver.getDeviceAudioClipQueueHighPriority().isEmpty():
              "high queue not drained: arrivals=${admissionSchedule}, timing=${timingMode}, queue=${driver.getDeviceAudioClipQueueHighPriority()*.correlationId}"
            scenarioCount++
          }
        }
      }
    }

    then:
    scenarioCount == 204
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
    seedUpnpFavorite('3')
    seedUpnpFavorite('9001')

    when:
    driver.loadFavorite()

    then:
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') &&
      (it.params.body.contains('song%3a3') || it.params.body.contains('song%3a9001')) }
  }

  def "loadPlaylist without an ID chooses an available sparse playlist ID"() {
    given:
    seedUpnpPlaylist('playlist-2')
    seedUpnpPlaylist('playlist-10004')

    when:
    driver.loadPlaylist()

    then:
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') &&
      (it.params.body.contains('SQ:playlist-2') || it.params.body.contains('SQ:playlist-10004')) }
  }

  def "random favorite load waits for the asynchronous favorites query when the cache is empty"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'

    when:
    driver.loadFavorite()

    then:
    driver.runtimeState.pendingRandomMediaLoads == ['favorite']
    driver.asyncCalls.any { it.params.body.contains('<ObjectID>FV:2</ObjectID>') }

    when:
    seedUpnpFavorite('9001')
    driver.completePendingRandomMediaLoads('favorite')

    then:
    driver.runtimeState.pendingRandomMediaLoads.isEmpty()
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') &&
      it.params.body.contains('song%3a9001') }
  }

  def "favorite autoplay follows the accepted UPnP load"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.getFavoritesMap()['object-service-account'] = [
      id: '353', name: 'Favorite 353', service: 'Amazon Music',
      upnpUri: 'x-sonos-http:song%3a353.mp4?sid=204&flags=8232&sn=9',
      upnpMetadata: '<DIDL-Lite><item id="10032028song%3a353"/></DIDL-Lite>'
    ]

    when:
    driver.loadFavoriteFull('353', 'repeat all', 'replace', 'off', 'true', 'on')

    then:
    driver.asyncCalls.last().params.headers.SOAPAction.endsWith('#SetAVTransportURI')
    !driver.scheduled.any { List call -> call[1] == 'playerPlay' }

    when:
    Map request = driver.asyncCalls.last()
    driver.favoriteLoadSoapCallback(new hubitat.scheduling.AsyncResponse(status: 200, data: ''), request.data)
    driver.drainTransportCommandQueue()

    then:
    driver.asyncCalls.last().params.headers.SOAPAction.endsWith('#Play')
  }

  def "favorite confirmation polls first and does not immediately reload"() {
    given:
    seedUpnpFavorite('42')
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    driver.asyncCalls.clear()
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.checkFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    driver.asyncCalls.collect { it.params.headers.SOAPAction }.containsAll([
      'urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo'])
    driver.asyncCalls.every { !it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') }
    driver.scheduled.last() == [
      2,
      'evaluateFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "favorite evaluation performs one bounded reload only after confirmation fails"() {
    given:
    seedUpnpFavorite('42')
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    driver.asyncCalls.clear()
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    driver.asyncCalls.count { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') } == 1
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
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.currentValues.transportStatus = 'stopped'

    when:
    driver.stop()
    driver.drainTransportCommandQueue()

    then:
    driver.asyncCalls.last().params.headers.SOAPAction.endsWith('#Stop')
    // Stop is intentionally sent even with stale reported state.
    driver.runtimeState.activeTransportCommand.command == 'stop'
  }

  def "stop sends a playback stop request when playback is active"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    driver.device.currentValues.transportStatus = 'playing'

    when:
    driver.stop()
    driver.drainTransportCommandQueue()

    then:
    driver.asyncCalls.last().params.headers.SOAPAction.endsWith('#Stop')
  }

  def "playlist confirmation also polls before performing a bounded reload"() {
    given:
    seedUpnpPlaylist('playlist-7')
    driver.loadPlaylistFull('playlist-7', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']
    driver.asyncCalls.clear()
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.checkPlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    driver.asyncCalls.collect { it.params.headers.SOAPAction }.containsAll([
      'urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo'])
    driver.asyncCalls.every { !it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') }

    when:
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    driver.asyncCalls.count { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') } == 1
    retryState.attemptNumber == 1
    driver.scheduled.last() == [
      5,
      'checkPlaylistPlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "short media load overloads enable crossfade in the outgoing command"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
    if(method.toLowerCase(Locale.ROOT).contains('favorite')) { seedUpnpFavorite('42') }
    else { seedUpnpPlaylist('42') }

    when:
    driver."${method}"(*arguments)

    then:
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#SetCrossfadeMode') &&
      it.params.body.contains('<CrossfadeMode>1</CrossfadeMode>') }
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') }

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
    given:
    seedUpnpPlaylist('playlist-963')

    when:
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'insert', 'off', 'true', 'on')

    then:
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#AddURIToQueue') &&
      it.params.body.contains('<EnqueuedURI>x-rincon-cpcontainer:SQ:playlist-963</EnqueuedURI>') &&
      it.params.body.contains('<EnqueueAsNext>true</EnqueueAsNext>') }
  }

  def "acknowledged playlist load issues play instead of duplicating the playlist request"() {
    given:
    seedUpnpPlaylist('playlist-963')
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'insert', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']
    Map request = driver.asyncCalls.find { it.params.headers.SOAPAction.endsWith('#AddURIToQueue') }
    driver.playlistLoadSoapCallback(new hubitat.scheduling.AsyncResponse(status: 200, data: ''), request.data)
    driver.asyncCalls.clear()
    websocketMessages.clear()
    rawConnections.clear()
    rawRequests.clear()
    driver.scheduled.clear()

    when:
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])
    driver.drainTransportCommandQueue()

    then:
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#Play') }
    driver.asyncCalls.every { !it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') }
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

  def "UPnP playback metadata confirms the exact saved playlist resource ID"() {
    given:
    driver.getPlaylistsMap()['playlist-963'] = [id: 'playlist-963', name: 'Morning Mix', upnpUri: 'x-rincon-cpcontainer:playlist-963']
    driver.loadPlaylistFull('playlist-963', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']

    when:
    driver.updateCurrentFavoriteFromUpnp([
      currentTrackURI: 'x-rincon-cpcontainer:playlist-963',
      enqueuedTransportURI: '', currentTrackMetaData: [:], enqueuedTransportURIMetaData: [:]
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
    driver.metaClass.getParent = { -> new Expando(playerCommandRequest: { Map payload -> parentPlayerRequests << new LinkedHashMap(payload); false }) }
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
    driver.device.dataValues.localUpnpHost = '192.168.1.50'
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
    driver.asyncCalls.size() == 1
    driver.asyncCalls[0].params.headers.SOAPAction.endsWith('#SetVolume')
    driver.asyncCalls[0].params.body.contains('<DesiredVolume>41</DesiredVolume>')
    !driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#Play') || it.params.headers.SOAPAction.endsWith('#Pause') }
  }

  def "setVolumeZero sends a direct player volume command with zero"() {
    given:
    driver.device.dataValues.localUpnpHost = '192.168.1.50'

    when:
    driver.setVolumeZero()

    then:
    driver.asyncCalls.size() == 1
    driver.asyncCalls[0].params.headers.SOAPAction.endsWith('#SetVolume')
    driver.asyncCalls[0].params.body.contains('<DesiredVolume>0</DesiredVolume>')
  }

  def "group Favorite loads use the verified group ID without the per-player Amazon workaround"() {
    given:
    seedUpnpFavorite('42')
    driver.device.dataValues.isGroupCoordinator = 'true'

    when:
    driver.loadFavoriteForGroupOperation('42', 'repeat all', 'replace', 'off', 'true', 'on', 'group-op-1', 'GROUP-VERIFIED', 'group-op-1:1')

    then:
    driver.asyncCalls.any { it.params.headers.SOAPAction.endsWith('#SetAVTransportURI') }
    driver.runtimeState.groupFavoriteOperationId == 'group-op-1'
    driver.runtimeState.groupFavoriteOperationAttemptId == 'group-op-1:1'
    driver.flushPendingGroupFavoriteOperationEvents()
    Map update = parentPlayerUpdates.find { Map item -> item.kind == 'groupFavoriteOperation' }.payload
    update.attemptId == 'group-op-1:1'
    driver.device.events.isEmpty()
    driver.scheduled.every { List call -> call[1] != 'playerPlay' }
  }

  def "group Favorite updates use a deferred app handoff without device events"() {
    given:
    driver.registerGroupFavoriteOperation('group-op-private', '42')

    when:
    driver.emitGroupFavoriteOperationEvent('registered', [favoriteId: '42'])
    driver.flushPendingGroupFavoriteOperationEvents()

    then:
    parentPlayerUpdates.any { Map item -> item.kind == 'groupFavoriteOperation' && item.payload.event == 'registered' }
    driver.device.events.isEmpty()
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

  def "non audioClip websocket commands are rejected"() {
    given:
    String firstMessage = JsonOutput.toJson([[namespace: 'favorites', command: 'loadFavorite', groupId: 'GROUP_TEST'], [favoriteId: '24']])
    String secondMessage = JsonOutput.toJson([[namespace: 'playlists', command: 'loadPlaylist', groupId: 'GROUP_TEST'], [playlistId: '963']])
    driver.device.dataValues.websocketStatus = 'open'

    when:
    driver.sendWsMessage(firstMessage)
    driver.sendWsMessage(secondMessage)

    then:
    websocketMessages.empty
    driver.runtimeState.websocketOutboundQueue == null
    driver.logs.any { String entry -> entry.contains('Refusing to send a WebSocket command without a known response fingerprint') }
  }

  def "matching websocket response clears the command ledger with trace success"() {
    given:
    queueTestGroupSocketCommand()
    List<Map> pending = driver.runtimeState.websocketCommandLedger as List<Map>
    Integer pendingCount = pending.size()

    when:
    driver.processWebsocketMessage('[{"namespace":"audioClip","response":"loadAudioClip","success":true,"type":"audioClip"},{"name":"clip-10"}]')

    then:
    pendingCount == 1
    driver.runtimeState.websocketCommandLedger == null
    driver.logs.any { it.toString().contains('WebSocket command ledger success') && it.toString().contains('audioClip.loadAudioClip') }
  }

  def "primary websocket ledger expiry cleans correlation without reconnecting"() {
    given:
    driver.device.dataValues.websocketStatus = 'open'

    when:
    queueTestGroupSocketCommand()
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
    queueTestGroupSocketCommand(10)
    Map original = (driver.runtimeState.websocketCommandLedger as List<Map>)[0]

    when:
    List<Map> claimed = driver.takeWebsocketCommandLedgerBatch()
    queueTestGroupSocketCommand(20)

    then:
    claimed.size() == 1
    claimed[0].entryId == original.entryId
    ((driver.runtimeState.websocketCommandLedger as List<Map>)[0]).entryId != original.entryId
    claimed[0].message.contains('clip-10')
    ((driver.runtimeState.websocketCommandLedger as List<Map>)[0]).message.contains('clip-20')
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
    queueTestGroupSocketCommand()
    Integer generation = (driver.runtimeState.websocketConnectionGeneration ?: 0) as Integer

    when:
    driver.processWebsocketMessage('[{"namespace":"audioClip","response":"loadAudioClip","success":false,"type":"globalError"},{"errorCode":"ERROR_INVALID_ARGUMENT","reason":"invalid request","name":"clip-10"}]')

    then:
    driver.runtimeState.websocketCommandLedger == null
    driver.device.getDataValue('websocketStatus') == 'open'
    (driver.runtimeState.websocketConnectionGeneration ?: 0) == generation
    driver.logs.any { it.toString().contains('WebSocket command ledger failure') && it.toString().contains('ERROR_INVALID_ARGUMENT') }
  }

  def "command response silence expires correlation without queueing or reconnecting"() {
    given:
    queueTestGroupSocketCommand()
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

    when:
    driver.wsConnect()

    then:
    websocketConnections.size() == 1
    websocketConnections[0].any { Object argument ->
      argument instanceof Map && (argument as Map).pingInterval == driver.WS_PING_INTERVAL_SECONDS
    }
    driver.WS_PING_INTERVAL_SECONDS > 0
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

  def "GroupRenderingControl expectation resubscribes after player becomes ungrouped"() {
    given:
    driver.device.currentValues.isGrouped = 'off'
    driver.armExpectedUpnpSubscriptionEvent('sid4', 'SetGroupVolume')
    List<Map> pending = driver.runtimeState.upnpExpectedSubscriptionEvents as List<Map>
    pending[0].deadlineAt = driver.now() - 1L

    when:
    driver.watchExpectedUpnpSubscriptionEvents()

    then:
    pending[0].attempts == 1
    rawRequests.size() == 1
    rawRequests[0].startsWith('SUBSCRIBE /MediaRenderer/GroupRenderingControl/Event HTTP/1.1')
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
    driver.getFavoritesMap()['match'] = [id: '42', name: 'Favorite 42', imageUrl: 'https://example.test/cover',
      resourceObjectId: 'urn:42']
    driver.updateCurrentFavoriteFromUpnp([
      currentTrackURI: 'x-sonos-http:song%3a42.mp4',
      currentTrackMetaData: [id: 'urn:42'], enqueuedTransportURIMetaData: [:]
    ])

    then:
    driver.flushPendingGroupFavoriteOperationEvents()
    List<Map> updates = parentPlayerUpdates.findAll { Map item -> item.kind == 'groupFavoriteOperation' }
    Map payload = updates.collect { Map item -> item.payload }.find { Map item -> item.event == 'metadataConfirmed' }
    payload.event == 'metadataConfirmed'
    payload.data.favoriteId == '42'
    payload.data.confirmed == true
  }

  def "empty groups responses emit a negative topology observation"() {
    given:
    driver.registerGroupFavoriteOperation('group-op-4', '42')
    driver.device.events.clear()

    when:
    driver.processWebsocketMessage('[{"type":"groups","name":"groups"}, {"groups":[]}]')

    then:
    driver.device.events.isEmpty()
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

  def "retained active regular head does not count as waiting work for synchronized chime readiness"() {
    given:
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()
    driver.getDeviceAudioClipQueue().add([correlationId: 'A'])
    driver.runtimeState.currentlyPlayingClip = [correlationId: 'A']
    driver.runtimeState.audioClipPlaying = true
    driver.runtimeState.activeAudioClipMessage = [correlationId: 'A', priority: 'REGULAR']

    expect:
    driver.getAudioClipQueueIsEmpty() == false
    driver.hasQueuedRegularAudioClip() == false
    driver.canPrepareSynchronizedGroupChime(false) == true

    when: 'another regular clip is waiting behind the retained active head'
    driver.getDeviceAudioClipQueue().add([correlationId: 'B'])

    then:
    driver.hasQueuedRegularAudioClip() == true
    driver.canPrepareSynchronizedGroupChime(false) == false

    when: 'the active head is interrupted by a high-priority clip'
    driver.runtimeState.activeAudioClipMessage = [correlationId: 'H', priority: 'HIGH']
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueue().add([correlationId: 'A'])

    then: 'the suspended head remains unfinished but does not count as newly waiting work'
    driver.getAudioClipQueueIsEmpty() == false
    driver.hasQueuedRegularAudioClip() == false
    driver.canPrepareSynchronizedGroupChime(false) == true
  }

  def "muted synchronized group member reports terminal progress"() {
    given:
    driver.events.clear()
    driver.device.currentValues.mute = 'muted'
    driver.getDeviceAudioClipQueue().clear()
    driver.getDeviceAudioClipQueueHighPriority().clear()

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

  def "volume scaling rounds fractional values half up"() {
    expect:
    driver.roundVolume(input) == expected
    driver.calculateAnnouncementVolume(input, 20, 0) == expected

    where:
    input | expected
    12.49G | 12
    12.5G  | 13
    12.51G | 13
  }

  def "AVT event translation contains only fields represented in the payload"() {
    when:
    List<Map> events = driver.buildAvtEvents([
      hasTransportState: true, transportState: 'STOPPED',
      hasTrack: true, track: '0', hasCurrentTrackURI: true, currentTrackURI: '',
      hasCurrentTrackDuration: true, currentTrackDuration: '0:00:00',
      hasCurrentTrackMetaData: true, currentTrackMetaDataRaw: '', currentTrackMetaData: [:]
    ], [:])

    then:
    events.collect { Map event -> [event.name, event.value] }.containsAll([
      ['status', 'stopped'], ['transportStatus', 'stopped'], ['currentTrackName', 'Not Available'],
      ['currentArtistName', 'Not Available'], ['currentAlbumName', 'Not Available'],
      ['albumArtURI', 'Not Available'], ['trackDescription', 'Not Available'],
      ['currentTrackNumber', 0], ['currentTrackDuration', '0:00:00']
    ])
    !events.any { Map event -> event.name == 'nextTrackName' }
  }

  def "GRC and RC event translation emits only present reported values"() {
    expect:
    driver.buildGrcEvents([groupVolume: 0, groupMute: 'unmuted']) == [
      [name: 'groupVolume', value: 0], [name: 'groupMute', value: 'unmuted']]
    driver.buildRcEvents([volumeMaster: 0, muteMaster: 'unmuted', bass: 2]) == [
      [name: 'volume', value: 0], [name: 'level', value: 0], [name: 'mute', value: 'unmuted'], [name: 'bass', value: 2]]
    driver.buildRcEvents([treble: 1]) == [[name: 'treble', value: 1]]
  }

}
