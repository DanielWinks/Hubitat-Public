package dwinks.hubitat.functional

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.hub.domain.Event
import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import hubitat.scheduling.AsyncResponse
import org.apache.commons.lang3.StringEscapeUtils
import spock.lang.Shared
import spock.lang.Specification

class SonosAppPlayerDouble extends ChildDeviceWrapper {
  Long id = 101L
  Map<String, Object> settings = [:]
  List<Map> commands = []

  List<String> reads = []
  List<String> zgtElectionResults = []

  void zgtElectionResult(String result) { zgtElectionResults << result }

  Object getSetting(String name) { reads << "getSetting:${name}".toString(); return settings[name] }
  Object currentValue(String n, boolean skipCache = false) { reads << "currentValue:${n}".toString(); return currentValues[n] }
  void setGroupVolume(BigDecimal level, BigDecimal duration = null) { commands << [name: 'setGroupVolume', level: level] }
  void setLevel(BigDecimal level) { commands << [name: 'setLevel', level: level] }

  Map favoritesMap = [:]
  Map playlistsMap = [:]
  List<String> acknowledgedParentRequests = []
  List<String> refreshedPlaybackUpdates = []
  Closure<Boolean> topologyApplyHook

  Map<String, Object> emittedAttributes() {
    return events.findAll { Map event -> event?.name != null }.collectEntries { Map event ->
      [(event.name.toString()): event.value]
    }
  }

  void mute() { commands << [name: 'mute'] }
  void unmute() { commands << [name: 'unmute'] }

  void acknowledgeParentCommand(String requestId) { acknowledgedParentRequests << requestId }
  void playerCreateGroup(List<String> playerIds, String musicContextGroupId = null) {
    commands << [name: 'playerCreateGroup', playerIds: playerIds, musicContextGroupId: musicContextGroupId]
  }

  void playerCreateGroupRequest(String requestJson) {
    Map request = (Map)new JsonSlurper().parseText(requestJson)
    List args = (List)request.args
    playerCreateGroup((List<String>)args[0], args.size() > 1 ? args[1]?.toString() : null)
  }

  void playerModifyGroupMembers(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = [],
      Map<String, Map> expectedTopology = null) {
    Map command = [name: 'playerModifyGroupMembers', add: playerIdsToAdd, remove: playerIdsToRemove]
    if(expectedTopology != null) { command.expectedTopology = expectedTopology }
    commands << command
  }

  void playerModifyGroupMembersRequest(String requestJson) {
    Map request = (Map)new JsonSlurper().parseText(requestJson)
    List args = (List)request.args
    playerModifyGroupMembers((List<String>)args[0], (List<String>)args[1],
        args.size() > 2 ? (Map<String, Map>)args[2] : null)
  }

  void playerGetGroupsFull() {
    commands << [name: 'playerGetGroupsFull']
  }

  void playerLoadAudioClip(String uri, BigDecimal volume, Integer duration, String correlationId) {
    commands << [name: 'playerLoadAudioClip', uri: uri, volume: volume,
      duration: duration, correlationId: correlationId]
  }

  void playerLoadAudioClipHighPriority(String uri, BigDecimal volume, String correlationId,
      Integer duration, Boolean chimeBeforeTTS = false) {
    commands << [name: 'playerLoadAudioClipHighPriority', uri: uri, volume: volume,
      correlationId: correlationId, duration: duration, chimeBeforeTTS: chimeBeforeTTS]
  }

  void playerPlayGroupAnnouncementStream(String uri, BigDecimal volume, String correlationId) {
    commands << [name: 'playerPlayGroupAnnouncementStream', uri: uri, volume: volume,
      correlationId: correlationId]
  }

  void restoreGroupAnnouncementPlayback(String correlationId) {
    commands << [name: 'restoreGroupAnnouncementPlayback', correlationId: correlationId]
  }

  void registerGroupTopologyOperation(String operationId, String requestId = null) {
    commands << [name: 'registerGroupTopologyOperation', operationId: operationId, requestId: requestId]
  }
  void clearGroupTopologyOperation(String operationId = null) {
    commands << [name: 'clearGroupTopologyOperation', operationId: operationId]
  }

  void getPlaybackStatus() { commands << [name: 'getPlaybackStatus'] }
  void getPlaybackMetadataStatus() { commands << [name: 'getPlaybackMetadataStatus'] }
  void applyRefreshedPlaybackState(String json) { refreshedPlaybackUpdates << json }
  void applyStateBatch(String json) { refreshedPlaybackUpdates << json }
  void updateBatchPlaybackState(String json) { refreshedPlaybackUpdates << json }
  void playerPlay() { commands << [name: 'playerPlay'] }
  void play() { commands << [name: 'play'] }
  Map getFavoritesMap() { return favoritesMap }
  Map getPlaylistsMap() { return playlistsMap }
  void getFavorites() { commands << [name: 'getFavorites'] }
  void getPlaylists() { commands << [name: 'getPlaylists'] }
  void loadFavorite(String favoriteId = null) { commands << [name: 'loadFavorite', favoriteId: favoriteId] }
  void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode,
      String shuffleMode, String autoPlay, String crossfadeMode) {
    commands << [name: 'loadFavoriteFull', favoriteId: favoriteId, repeatMode: repeatMode,
      queueMode: queueMode, shuffleMode: shuffleMode, autoPlay: autoPlay,
      crossfadeMode: crossfadeMode]
  }
  void loadPlaylist(String playlistId) { commands << [name: 'loadPlaylist', playlistId: playlistId] }
  void loadPlaylistFull(String playlistId, String repeatMode, String queueMode,
      String shuffleMode, String autoPlay, String crossfadeMode) {
    commands << [name: 'loadPlaylistFull', playlistId: playlistId, repeatMode: repeatMode,
      queueMode: queueMode, shuffleMode: shuffleMode, autoPlay: autoPlay, crossfadeMode: crossfadeMode]
  }
  Boolean applyAppHouseholdTopology(String json) {
    currentValues.householdTopology = json
    return topologyApplyHook == null || topologyApplyHook.call(json) == true
  }
}

class SonosAppGroupDouble extends ChildDeviceWrapper {
  Long id = 201L
  List<String> reads = []
  Object currentValue(String n, boolean skipCache = false) { reads << "currentValue:${n}".toString(); return currentValues[n] }
  Object getSetting(String name) { reads << "getSetting:${name}".toString(); return updatedSettings[name] }
  List<Map> pendingGroupRequestPoll = []

  List<Map> drainPendingGroupRequestsForPoll() {
    List<Map> batch = []
    batch.addAll(pendingGroupRequestPoll)
    pendingGroupRequestPoll.clear()
    return batch
  }
  List<String> refreshedPlaybackUpdates = []
  List<Map> voiceUpdates = []
  List<String> acknowledgedGroupRequests = []

  Map<String, Object> emittedAttributes() {
    return events.findAll { Map event -> event?.name != null }.collectEntries { Map event ->
      [(event.name.toString()): event.value]
    }
  }

  void acknowledgeGroupCommand(String requestId) { acknowledgedGroupRequests << requestId }

  void updateTTSVoiceCache(List<String> names, String defaultVoice) {
    voiceUpdates << [names: names, defaultVoice: defaultVoice]
  }

  void applyRefreshedPlaybackState(String json) {
    refreshedPlaybackUpdates << json
  }

  void applyStateBatch(String json) {
    refreshedPlaybackUpdates << json
  }

  void updateBatchPlaybackState(String json) {
    refreshedPlaybackUpdates << json
  }

  void applyAppHouseholdTopology(String json) { refreshedPlaybackUpdates << json }
}

class SonosAdvancedAppSpec extends Specification {

  @Shared File appFile = new File('../Apps/SonosAdvancedApp.groovy')
  @Shared Class<? extends HubitatScriptHarness> appScriptClass

  HubitatScriptHarness appScript
  SonosAppGroupDouble group
  SonosAppPlayerDouble coordinator
  SonosAppPlayerDouble follower
  SonosAppPlayerDouble extra
  Long topologyObservationSequence = 0L

  def setupSpec() {
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    appScriptClass = ScriptLoader.compile(appFile, new File('../Libraries'), true)
  }

  static final List<String> NEW_CALLBACKS = ['applyPlayerAttributeUpdateCallback', 'groupAnnouncementProgressCallback',
    'processUpnpNotificationCallback', 'groupTopologyOperationEventHandler', 'applyAvtCallback',
    'recalculateVolumeStateCallback', 'publishGroupRenderingCallback', 'deliverCatalogSnapshot',
    'processPlayerCommandRequest', 'processGroupCommandRequest', 'refreshPlayerPolicyCallback',
    'completePlayerAudioClipCallback']

  /** Run the recorded runInMillis callbacks (and any they schedule) in submission order, as the hub would. */
  void runScheduledCallbacks(Collection<String> only = NEW_CALLBACKS) {
    int guard = 0
    while(guard++ < 200) {
      List job = appScript.scheduled.find { List j -> only.contains(j[1]) }
      if(job == null) { return }
      appScript.scheduled.remove(job)
      Map opts = job[2] instanceof Map ? (Map)job[2] : [:]
      if(opts.data != null) { appScript."${job[1]}"(opts.data) } else { appScript."${job[1]}"() }
    }
  }

  def setup() {
    appScript = ScriptLoader.instantiate(appScriptClass)
    appScript.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: true]
    appScript.logs.clear()
    appScript.events.clear()
    appScript.scheduled.clear()
    appScript.pageHrefs.clear()
    appScript.state.clear()
    topologyObservationSequence = 0L

    group = new SonosAppGroupDouble(deviceNetworkId: 'GROUP-DNI', displayName: 'Test Group', label: 'Test Group')
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    group.dataValues.playerIds = 'RINCON_FOLLOW'
    coordinator = createPlayer('RINCON_COORD', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW', true)
    follower = createPlayer('RINCON_FOLLOW', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW', false)
    extra = createPlayer('RINCON_EXTRA', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA', false)

    appScript.children = [group, coordinator, follower, extra]
    appScript.app.children = appScript.children
  }

  def "orphan pruning removes the retired last sent child state cache"() {
    given:
    appScript.state.lastSentChildState = ['GROUP-DNI': [status: 'playing']]

    when:
    Map result = appScript.pruneOrphanedAppPersistence()

    then:
    result.stateRemoved.contains('lastSentChildState')
    !appScript.state.containsKey('lastSentChildState')
  }

  def "ZGT election request is answered from a scheduled delivery, never inline"() {
    when:
    appScript.playerAppRequestHandler([channel: 'playerAppRequest', action: 'requestZgtElection',
      sourceDni: coordinator.deviceNetworkId, requestId: 'zgt-1', args: [:]])

    then:
    coordinator.zgtElectionResults.isEmpty()
    appScript.scheduled.any { List job -> job[1] == 'deliverZgtElection' }

    when:
    Map data = appScript.scheduled.find { List job -> job[1] == 'deliverZgtElection' }[2].data
    appScript.deliverZgtElection(data)

    then:
    coordinator.zgtElectionResults == ['proceed']
  }

  def "first player reporting ready wins ZGT subscription and winner can retry"() {
    expect:
    appScript.readyForZgt('DNI-FIRST') == 'proceed'
    appScript.readyForZgt('DNI-SECOND') == 'wait'
    appScript.readyForZgt('DNI-FIRST') == 'proceed'
    appScript.atomicState.readyForZgt == ['DNI-FIRST', 'DNI-SECOND']
  }

  def "player and group preference snapshots are stored by child DNI and used by app operations"() {
    given:
    coordinator.settings.disableAlbumArtEvents = false
    appScript.playerPreferencesUpdated([deviceDni: coordinator.deviceNetworkId, preferences: [
      disableAlbumArtEvents: true,
      disableArtistAlbumTrackEvents: true,
      disableTrackDataEvents: true,
      enableTrackPositionUpdates: true,
      includeTrackDataMetaData: true,
      chimeBeforeHighPriorityTTS: true,
      chimeBeforeStandardPriorityTTS: false,
      ttsBoostAmount: 15
    ]])
    appScript.groupPreferencesUpdated([deviceDni: group.deviceNetworkId, preferences: [
      chimeBeforeStandardPriorityTTS: true,
      chimeBeforeHighPriorityTTS: false,
      disableAlbumArtEvents: false,
      enableTrackPositionUpdates: true
    ]])

    expect:
    appScript.atomicState.playerPreferencesByDni[coordinator.deviceNetworkId].ttsBoostAmount == 15
    appScript.atomicState.playerPreferencesByDni[coordinator.deviceNetworkId].includeTrackDataMetaData == true
    appScript.atomicState.groupPreferencesByDni[group.deviceNetworkId].chimeBeforeStandardPriorityTTS == true
    appScript.isAlbumArtEventsDisabled(coordinator)
    !appScript.isAlbumArtEventsDisabled(group)
    appScript.isPlayerTrackPositionUpdatesEnabled(coordinator)
    appScript.isGroupTrackPositionUpdatesEnabled(group)
    appScript.isPlayerAttributeDisabled(coordinator, 'trackData')
    appScript.isPlayerAttributeDisabled(coordinator, 'currentArtistName')
    !appScript.isPlayerAttributeDisabled(follower, 'currentTrackName')
  }

  // ---------------------------------------------------------------------------
  // Child -> app contract: direct calls, one deferred callback, order from [epoch, sequence]
  // ---------------------------------------------------------------------------

  def "accept gate drops an older sequence, accepts a new epoch, and starts empty after an app restart"() {
    expect: 'first arrival on a stream is accepted'
    appScript.acceptSequence('s:1', 100L, 5L)

    and: 'an equal or older sequence in the same epoch is dropped'
    !appScript.acceptSequence('s:1', 100L, 5L)
    !appScript.acceptSequence('s:1', 100L, 4L)
    appScript.acceptSequence('s:1', 100L, 6L)

    and: 'a new epoch (driver reload) resets the stream even with a lower sequence'
    appScript.acceptSequence('s:1', 101L, 1L)
    !appScript.acceptSequence('s:1', 101L, 1L)

    and: 'streams are independent and unstamped payloads always pass'
    appScript.acceptSequence('s:2', 100L, 1L)
    appScript.acceptSequence('s:1', null, null)

    when: 'the app restarts, so the marks are gone'
    appScript.resetAppRuntimeMemory()

    then:
    appScript.acceptSequence('s:1', 101L, 1L)
  }

  def "apply gate lets an equal sequence through but drops a callback older than the last applied one"() {
    expect:
    appScript.applySequence('a:1', 7L, 10L)
    appScript.applySequence('a:1', 7L, 10L)
    !appScript.applySequence('a:1', 7L, 9L)
    appScript.applySequence('a:1', 7L, 11L)
    appScript.applySequence('a:1', 8L, 1L)
  }

  def "partial attribute updates are ordered per attribute so a late delta cannot drop a name a newer one did not carry"() {
    given:
    appScript.playerAttributeUpdate([playerDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 1L,
      attributes: [customOne: 'Old']])
    appScript.playerAttributeUpdate([playerDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 2L,
      attributes: [customTwo: 'Newer']])
    List<Map> jobs = appScript.scheduled.findAll { List job -> job[1] == 'applyPlayerAttributeUpdateCallback' }

    expect: 'entry points only schedule; nothing was written to the child'
    jobs.size() == 2
    coordinator.events.isEmpty()

    when: 'the callbacks run out of order'
    appScript.applyPlayerAttributeUpdateCallback(jobs[1][2].data)
    appScript.applyPlayerAttributeUpdateCallback(jobs[0][2].data)

    then: 'both attributes are applied because they are different names'
    coordinator.emittedAttributes().customTwo == 'Newer'
    coordinator.emittedAttributes().customOne == 'Old'

    when: 'an older value for an already-applied name arrives late'
    coordinator.events.clear()
    appScript.applyPlayerAttributeUpdateCallback([playerDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 0L,
      attributes: [customOne: 'Stale']])

    then:
    coordinator.events.isEmpty()
  }

  def "a stale attribute report is dropped at the entry point before anything is scheduled"() {
    given:
    appScript.playerAttributeUpdate([playerDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 5L, attributes: [customOne: 'New']])
    appScript.scheduled.clear()

    when:
    appScript.playerAttributeUpdate([playerDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 4L, attributes: [customOne: 'Old']])

    then:
    appScript.scheduled.isEmpty()
  }

  def "announcement phases are accepted once per stream and handled from a callback"() {
    when:
    appScript.groupAnnouncementProgress('corr-1', 'PLAYING', [epoch: 9L, sequence: 2L, correlationId: 'corr-1'], coordinator.deviceNetworkId)
    appScript.groupAnnouncementProgress('corr-1', 'PLAYING', [epoch: 9L, sequence: 1L, correlationId: 'corr-1'], coordinator.deviceNetworkId)

    then: 'the older phase from the same driver is dropped'
    appScript.scheduled.count { List job -> job[1] == 'groupAnnouncementProgressCallback' } == 1
    appScript.scheduled.find { List job -> job[1] == 'groupAnnouncementProgressCallback' }[2].overwrite == false
  }

  def "a late callback with an older sequence cannot apply after a newer one"() {
    given:
    List<Map> handled = []
    appScript.metaClass.handleGroupAnnouncementProgress = { ChildDeviceWrapper player, Map payload -> handled << payload }
    Map newer = [correlationId: 'c', phase: 'B', dni: coordinator.deviceNetworkId, payload: [phase: 'B'], epoch: 3L, sequence: 2L]
    Map older = [correlationId: 'c', phase: 'A', dni: coordinator.deviceNetworkId, payload: [phase: 'A'], epoch: 3L, sequence: 1L]

    when:
    appScript.groupAnnouncementProgressCallback(newer)
    appScript.groupAnnouncementProgressCallback(older)

    then:
    handled*.phase == ['B']
  }

  def "AlarmClock notifications are accepted once and processed from a callback"() {
    given:
    appScript.metaClass.processUpnpNotification = { Map notification -> }

    when:
    appScript.putPlayerUpnpEvents([service: 'AlarmClock', sourceDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 3L, values: [TimeZone: 'x']])
    appScript.putPlayerUpnpEvents([service: 'AlarmClock', sourceDni: coordinator.deviceNetworkId, epoch: 1L, sequence: 2L, values: [TimeZone: 'y']])

    then:
    appScript.scheduled.count { List job -> job[1] == 'processUpnpNotificationCallback' } == 1
    !appScript.scheduled.any { List job -> job[1] == 'drainUpnpNotificationQueue' }
  }

  def "AVT deltas are cached and applied per event name with accept and apply gates"() {
    given:
    appScript.putAvtEvents('DNI-A', [[name: 'status', value: 'playing']], 1L, 1L)
    appScript.putAvtEvents('DNI-A', [[name: 'trackName', value: 'Song']], 1L, 2L)
    appScript.putAvtEvents('DNI-A', [[name: 'status', value: 'stale']], 1L, 0L)

    expect: 'the stale status delta never reached the cache or a callback'
    appScript.getStoredAvtEvents('DNI-A').collectEntries { [(it.name): it.value] } == [status: 'playing', trackName: 'Song']
    appScript.scheduled.count { List job -> job[1] == 'applyAvtCallback' } == 2
    appScript.state.avtEventsByDni == null
  }

  def "group topology operation updates are accepted once and processed from a callback"() {
    when:
    appScript.playerGroupTopologyOperationUpdate([operationId: 'op', playerId: 'RINCON_COORD', event: 'groups', epoch: 2L, sequence: 4L])
    appScript.playerGroupTopologyOperationUpdate([operationId: 'op', playerId: 'RINCON_COORD', event: 'groups', epoch: 2L, sequence: 3L])

    then:
    appScript.scheduled.count { List job -> job[1] == 'groupTopologyOperationEventHandler' } == 1
    !appScript.scheduled.any { List job -> job[1] == 'drainPlayerGroupTopologyEvents' }
  }

  def "ZGT payloads from a stale sequence are ignored without storing them"() {
    given:
    Closure payload = { Long seq, String name -> [sourceDni: coordinator.deviceNetworkId, epoch: 1L, sequence: seq,
      followersByCoordinator: ['RINCON_COORD': ['RINCON_FOLLOW']], groupNamesById: ['RINCON_COORD:1': name]] }

    when:
    appScript.setZgtPayload(payload(2L, 'Newer'))
    appScript.setZgtPayload(payload(1L, 'Older'))

    then:
    appScript.atomicState.zgtPayload.groupNamesById == ['RINCON_COORD:1': 'Newer']
  }

  def "player readings and group rendering are ordered per value and never touch a child inline"() {
    given:
    when:
    appScript.playerAppRequest([version: 1, channel: 'playerAppRequest', action: 'playerReading', sourceDni: coordinator.deviceNetworkId,
      requestId: 'r1', epoch: 4L, sequence: 2L, args: [volume: 30]])
    appScript.playerAppRequest([version: 1, channel: 'playerAppRequest', action: 'playerReading', sourceDni: coordinator.deviceNetworkId,
      requestId: 'r2', epoch: 4L, sequence: 1L, args: [volume: 10]])
    appScript.playerAppRequest([version: 1, channel: 'playerAppRequest', action: 'groupRendering', sourceDni: coordinator.deviceNetworkId,
      requestId: 'g1', epoch: 4L, sequence: 3L, args: [groupVolume: 44]])

    then:
    appScript.getPlayerReadings()['RINCON_COORD'].volume == 30
    appScript.scheduled.count { List job -> job[1] == 'publishGroupRenderingCallback' } == 1
    coordinator.events.isEmpty()
    appScript.childEvents.isEmpty()
  }

  def "readyForZgt keeps the first reporter under concurrent election requests"() {
    when:
    List<Thread> threads = (1..8).collect { Integer index ->
      Thread.start { appScript.readyForZgt("DNI-${index}".toString()) }
    }
    threads*.join()

    then: 'every reporter is recorded exactly once'
    (appScript.atomicState.readyForZgt as List).sort() == (1..8).collect { "DNI-${it}".toString() }.sort()
  }

  def "app lock helpers do not lose updates across threads"() {
    given:
    appScript.atomicState.counter = 0
    int threadCount = 8
    int perThread = 200

    when:
    List<Thread> threads = (1..threadCount).collect {
      Thread.start {
        perThread.times {
          appScript.mutateAppData('counter') { Object current -> ((current ?: 0) as Integer) + 1 }
          appScript.withMemoryMap('tally') { Map<String, Map> live ->
            live['n'] = [count: (((live['n'] ?: [count: 0]) as Map).count as Integer) + 1]
          }
        }
      }
    }
    threads*.join()

    then:
    appScript.atomicState.counter == threadCount * perThread
    appScript.memoryMapCopy('tally')['n'].count == threadCount * perThread
  }

  def "accept gate admits exactly one of many concurrent arrivals with the same sequence"() {
    given:
    List<Boolean> results = Collections.synchronizedList([])

    when:
    (1..16).collect { Thread.start { results << appScript.acceptSequence('race', 1L, 1L) } }*.join()

    then:
    results.count { it } == 1
  }

  def "the app has no child-to-app queue, drain or acknowledgement left"() {
    given:
    String app = new File('../Apps/SonosAdvancedApp.groovy').text

    expect:
    !app.contains('PENDING_PLAYER_ATTRIBUTE_UPDATES')
    !app.contains('drainUpnpNotificationQueue')
    !app.contains('drainVolumeRecalculation')
    !app.contains('drainPlayerCommandRequests')
    !app.contains('drainGroupCommandRequests')
    !app.contains('drainQueuedPlayerStateUpdates')
    !app.contains('acknowledgePlayerCommandRequest')
    !app.contains('receivedGroupCommandRequestIds')
    !app.contains('PLAYER_COMMAND_RECEIVED_IDS')
  }

  def "empty DNI is not registered as ready for ZGT"() {
    expect:
    appScript.readyForZgt(null) == 'wait'
    appScript.atomicState.readyForZgt == null
  }

  def "SAC escaping helpers preserve the existing HTML and XML entity output"() {
    expect:
    appScript.escapeNewUiHtml("A&B <'\"") == 'A&amp;B &lt;&#39;&quot;'
    appScript.escapeSonosXml("A&B <'\"") == 'A&amp;B &lt;&apos;&quot;'
    appScript.normalizeLogLevel('INFO') == 'info'
    appScript.splitTokens('a,,b,', ',') == ['a', 'b']
  }

  def "Sonos endpoint helpers derive URLs from device IP"() {
    expect:
    appScript.getSonosLocalUpnpHost(coordinator) == '192.0.2.10:1400'
    appScript.getSonosLocalUpnpUrl(coordinator) == 'http://192.0.2.10:1400'
    appScript.getSonosLocalApiUrl(coordinator) == 'https://192.0.2.10:1443/api/v1/'
    appScript.getSonosWebsocketUrl(coordinator) == 'wss://192.0.2.10:1443/websocket/api'
  }

  SonosAppPlayerDouble createPlayer(String id, String groupId, String coordinatorId,
      String playerIds, Boolean isCoordinator) {
    SonosAppPlayerDouble player = new SonosAppPlayerDouble(
      deviceNetworkId: "DNI-${id}", displayName: id, label: id
    )
    player.dataValues.id = id
    player.dataValues.modelName = 'Sonos One'
    player.dataValues.name = id
    player.dataValues.groupId = groupId
    player.dataValues.groupCoordinatorId = coordinatorId
    player.dataValues.groupPlayerIds = playerIds
    player.dataValues.deviceIp = id == 'RINCON_COORD' ? '192.0.2.10' : '192.0.2.11'
    player.currentValues.transportStatus = 'stopped'
    player
  }

  Map request(String command, Map args = [:], Integer sequence = 1) {
    [groupDni: 'GROUP-DNI', requestId: "GROUP-DNI-${sequence}", command: command, args: args]
  }

  void makeAnnouncementRosterExact() {
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW'
  }


  def "initialization purge removes only the selected child's deferred commands and announcements"() {
    given:
    extra.dataValues.groupId = 'GROUP-OTHER'
    appScript.atomicState.deferredGroupPlayerCommands = [
      [playerDni: follower.deviceNetworkId, command: 'play'],
      [playerDni: extra.deviceNetworkId, command: 'pause']
    ]
    appScript.appMemory()['groupAnnouncementQueues'] = [
      'GROUP-DNI': [active: [correlationId: 'follower-active', playerIds: ['RINCON_FOLLOW']],
        pending: [[correlationId: 'follower-pending', playerIds: ['RINCON_FOLLOW']]]],
      'OTHER-GROUP': [active: [correlationId: 'extra-active', playerIds: ['RINCON_EXTRA']], pending: []]
    ]

    when:
    appScript.clearPendingCommandsForDevice(follower.deviceNetworkId)

    then:
    appScript.atomicState.deferredGroupPlayerCommands*.playerDni == [extra.deviceNetworkId]
    !appScript.appMemoryMap('groupAnnouncementQueues').containsKey('GROUP-DNI')
    appScript.appMemoryMap('groupAnnouncementQueues')['OTHER-GROUP'].active.correlationId == 'extra-active'
    appScript.appMemoryMap('deviceCommandClearTimes')[follower.deviceNetworkId] != null

    when: 'a command created before the purge arrives late'
    Boolean accepted = appScript.playerCommandRequest([playerDni: follower.deviceNetworkId,
      requestId: 'late-player-command', command: 'play',
      createdAt: appScript.appMemoryMap('deviceCommandClearTimes')[follower.deviceNetworkId].at])

    then: 'it is rejected and nothing is scheduled'
    !accepted
    !appScript.scheduled.any { List job -> job[1] == 'processPlayerCommandRequest' }
  }

  def "initialization purge removes all announcements for a group child and rejects late commands"() {
    given:
    appScript.appMemory()['groupAnnouncementQueues'] = [
      'GROUP-DNI': [active: [correlationId: 'active-tts', isTts: true],
        priorityActive: [correlationId: 'priority-tts', isTts: true],
        pending: [[correlationId: 'queued-tts', isTts: true]]],
      'OTHER-GROUP': [active: null, pending: []]
    ]

    when:
    appScript.acceptGroupCommandRequest([action: 'initializationPurge', groupDni: 'GROUP-DNI', createdAt: appScript.now()])

    then:
    !appScript.appMemoryMap('groupAnnouncementQueues').containsKey('GROUP-DNI')
    appScript.appMemoryMap('groupAnnouncementQueues').containsKey('OTHER-GROUP')

    when:
    Boolean accepted = appScript.acceptGroupCommandRequest([groupDni: 'GROUP-DNI', requestId: 'late-group-command',
      command: 'groupPlayers', createdAt: appScript.appMemoryMap('deviceCommandClearTimes')['GROUP-DNI'].at])

    then:
    !accepted
    !appScript.scheduled.any { List job -> job[1] == 'processGroupCommandRequest' }
  }

  def "ZGT reports from every player replace the stored snapshot latest wins"() {
    given:
    List<Map> first = [[name: 'groupCoordinatorId', value: 'RINCON_COORD']]
    List<Map> latest = [[name: 'groupCoordinatorId', value: 'RINCON_FOLLOW']]

    when:
    appScript.putZgtTopology(coordinator.deviceNetworkId, ['G1': first])
    Map stored = appScript.putZgtTopology(follower.deviceNetworkId, ['G2': latest])

    then:
    stored.keySet() == ['G2'] as Set
    appScript.getStoredZgtEventsMap().keySet() == ['G2'] as Set
    appScript.state.zgtLastReport.sourcePlayerDni == follower.deviceNetworkId
  }




  def "update checks issue an asynchronous request and ignore stale responses"() {
    when:
    appScript.checkForUpdates(true)
    Map first = appScript.asyncCalls.last()
    appScript.checkForUpdates(true)
    Map second = appScript.asyncCalls.last()
    appScript.updateCheckCallback(new AsyncResponse(status: 503), first.data as Map)

    then:
    first.cb == 'updateCheckCallback'
    first.data.requestId != second.data.requestId
    appScript.state.updateCheckRequestId == second.data.requestId

    when:
    appScript.updateCheckCallback(new AsyncResponse(status: 503), second.data as Map)

    then:
    !appScript.state.containsKey('updateCheckRequestId')
    appScript.logs.any { it.contains('Update check failed') }
  }

  def "update check reads the installed version through asynchronous hub requests"() {
    given:
    appScript.checkForUpdates(true)
    Map manifestRequest = appScript.asyncCalls.last()

    when:
    appScript.updateCheckCallback(new AsyncResponse(data: '{"version":"99.0.0"}'), manifestRequest.data as Map)
    Map loginRequest = appScript.asyncCalls.last()
    appScript.updateCheckLoginCallback(new AsyncResponse(status: 302, headers: ['Set-Cookie': 'sid=abc; Path=/']), loginRequest.data as Map)
    Map listRequest = appScript.asyncCalls.last()
    appScript.updateCheckAppListCallback(new AsyncResponse(data: '[{"id":42,"name":"Sonos Advanced Controller","namespace":"dwinks"}]'), listRequest.data as Map)
    Map codeRequest = appScript.asyncCalls.last()
    appScript.updateCheckAppCodeCallback(new AsyncResponse(data: '{"source":"version: \'98.0.0\'"}'), codeRequest.data as Map)

    then:
    loginRequest.cb == 'updateCheckLoginCallback'
    listRequest.cb == 'updateCheckAppListCallback'
    listRequest.params.headers.Cookie == 'sid=abc'
    codeRequest.cb == 'updateCheckAppCodeCallback'
    appScript.state.updateAvailable == true
    appScript.state.latestVersion == '99.0.0'
    appScript.state.installedAppVersion == '98.0.0'
    !appScript.state.containsKey('updateCheckRequestId')
  }

  def "TTS voice refresh ignores stale responses and applies the current voice"() {
    given:
    appScript.state.ttsVoiceRefreshRequestId = 'new'

    when:
    appScript.refreshTTSVoiceCacheCallback(new AsyncResponse(data: '{"ttsCurrent":"Joanna"}'),
      [requestId: 'old', voiceNames: ['Joanna']])

    then:
    group.voiceUpdates.empty

    when:
    appScript.refreshTTSVoiceCacheCallback(new AsyncResponse(data: '{"ttsCurrent":"Joanna"}'),
      [requestId: 'new', voiceNames: ['Joanna']])

    then:
    group.voiceUpdates == [[names: ['Joanna'], defaultVoice: 'Joanna']]
    !appScript.state.containsKey('ttsVoiceRefreshRequestId')
  }

  def "local HTTP retries retain a request GUID and reject callbacks from an older attempt"() {
    when:
    appScript.sendLocalQueryAsync([params: [uri: 'http://sonos.local/test']])
    Map firstCall = appScript.asyncCalls.last()
    Map firstData = (Map)firstCall.data
    Map firstRetryData = (Map)firstData._localControlRetryRequest
    appScript.localControlCallback(new AsyncResponse(status: 503, error: true, errorMessage: 'unavailable'), firstData)
    appScript.executeLocalControlRetry()
    Map retryCall = appScript.asyncCalls.last()
    Map retryData = (Map)retryCall.data
    Map retryMetadata = (Map)retryData._localControlRetryRequest

    then:
    UUID.fromString(firstRetryData.requestId).toString() == firstRetryData.requestId
    UUID.fromString(firstRetryData.attemptId).toString() == firstRetryData.attemptId
    retryMetadata.requestId == firstRetryData.requestId
    retryMetadata.attemptId != firstRetryData.attemptId
    retryMetadata.attemptNumber == 2

    when:
    appScript.localControlCallback(new AsyncResponse(status: 200), firstData)

    then:
    appScript.state.lastLocalControlRetryRequest.activeAttemptId == retryMetadata.attemptId
    appScript.state.localControlRetryAttemptCount == 1

    when:
    appScript.localControlCallback(new AsyncResponse(status: 200), retryData)

    then:
    !appScript.state.containsKey('lastLocalControlRetryRequest')
    appScript.state.localControlRetryAttemptCount == 0
  }

  void makeTopology(String groupId, String coordinatorId, String playerIds) {
    [coordinator, follower, extra].each { SonosAppPlayerDouble player ->
      player.dataValues.groupId = groupId
      player.dataValues.groupCoordinatorId = coordinatorId
      player.dataValues.groupPlayerIds = playerIds
    }
  }

  void emitTopologyObservation(String playerId, String groupId, String coordinatorId, String playerIds,
      Long observedAt = null) {
    Long eventObservedAt = observedAt ?: appScript.now() + (++topologyObservationSequence * 1000L)
    // The driver hands the app a plain Map with a `data` key; the scheduled callback receives it as is.
    appScript.groupTopologyOperationEventHandler([
      operationId: appScript.getActiveGroupOperation().operationId,
      playerId: playerId,
      observedAt: eventObservedAt,
      event: 'groups',
      data: [
        groupId: groupId,
        coordinatorId: coordinatorId,
        playerIds: playerIds.split(',') as List
      ]
    ])
  }

  void emitRequiredTopology(String groupId = 'GROUP-1', String coordinatorId = 'RINCON_COORD',
      String playerIds = 'RINCON_COORD,RINCON_FOLLOW') {
    emitTopologyObservation('RINCON_COORD', groupId, coordinatorId, playerIds)
    emitTopologyObservation('RINCON_FOLLOW', groupId, coordinatorId, playerIds)
  }



  def "explicit group refresh forwards coordinator playback values to the group driver"() {
    given:
    group.currentValues.switch = 'on'
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.currentTrackName = 'Only the Good Die Young'
    coordinator.currentValues.currentArtistName = 'Billy Joel'

    when:
    appScript.processGroupCommandRequest([request: request('refresh')])
    appScript.drainGroupStateUpdates()

    then:
    group.emittedAttributes().status == 'playing'
    group.emittedAttributes().currentTrackName == 'Only the Good Die Young'
    group.emittedAttributes().currentArtistName == 'Billy Joel'
  }

  def "unlisted-player eviction removes extras without rebuilding or changing coordinator"() {
    given:
    makeTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')
    Map operation = [
      operationId: 'evict-op-1', evictUnlistedOnly: true,
      configuredCoordinatorId: 'RINCON_COORD', desiredCoordinatorId: 'RINCON_COORD',
      resolvedCoordinatorId: 'RINCON_COORD', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_EXTRA']
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    follower.commands.every { it.name != 'playerModifyGroupMembers' && it.name != 'playerCreateGroup' }
  }

  def "unlisted-player eviction waits for a stable snapshot instead of creating a group"() {
    given:
    Map operation = [
      operationId: 'evict-op-2', evictUnlistedOnly: true,
      configuredCoordinatorId: 'RINCON_COORD', desiredCoordinatorId: 'RINCON_COORD',
      resolvedCoordinatorId: 'RINCON_COORD', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      topologyAttempt: 0
    ]
    Map topology = [
      fresh: false, consistent: false, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.every { it.name != 'playerCreateGroup' && it.name != 'playerModifyGroupMembers' }
    coordinator.commands.find { it.name == 'playerGetGroupsFull' } != null
  }

  def "unlisted-player eviction read refresh does not reset the observation window"() {
    given:
    Map operation = [
      operationId: 'evict-op-read', evictUnlistedOnly: true,
      configuredCoordinatorId: 'RINCON_COORD', desiredCoordinatorId: 'RINCON_COORD',
      resolvedCoordinatorId: 'RINCON_COORD', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      topologyAttempt: 0
    ]
    Map topology = [
      fresh: false, consistent: false, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.find { it.name == 'playerGetGroupsFull' } != null
    operation.topologyMutationSent == null
    appScript.scheduled.find { List call -> call[1] == 'refreshGroupOperationTopology' } == null
  }

  def "unlisted-player eviction reads live membership from the configured coordinator"() {
    given:
    coordinator.dataValues.groupId = 'GROUP-1'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_EXTRA,RINCON_FOLLOW'
    Map operation = [
      configuredCoordinatorId: 'RINCON_COORD', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ]

    when:
    Map topology = appScript.readEvictionCoordinatorTopology(operation)

    then:
    topology.fresh == true
    topology.coordinatorId == 'RINCON_COORD'
    topology.playerIds == ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']
  }

  def "group operation target recovers missing group device data from its saved definition"() {
    given:
    group.deviceNetworkId = '42-SonosGroupDevice-Arc + Kitchen'
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    group.dataValues.playerIds = 'RINCON_FOLLOW'

    expect:
    appScript.getAllPlayersForGroupDevice(group) == ['RINCON_COORD', 'RINCON_FOLLOW']
    appScript.buildGroupOperationTarget(group, 'EXPLICIT').configuredCoordinatorId == 'RINCON_COORD'
  }

  def "group definition lookup uses the group name encoded in the device DNI"() {
    given:
    group.deviceNetworkId = 'different-app-id-SonosGroupDevice-Arc + Kitchen'
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    group.dataValues.playerIds = 'RINCON_FOLLOW'

    expect:
    appScript.getConfiguredGroupDefinition(group).groupCoordinatorId == 'RINCON_COORD'
  }

  def "eviction operation retains the recovered configured coordinator"() {
    given:
    group.deviceNetworkId = 'different-app-id-SonosGroupDevice-Arc + Kitchen'
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    group.dataValues.playerIds = 'RINCON_FOLLOW'

    when:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT', [evictUnlistedOnly: true])

    then:
    appScript.getActiveGroupOperation().configuredCoordinatorId == 'RINCON_COORD'
    appScript.getActiveGroupOperation().requiredPlayerIds == ['RINCON_COORD', 'RINCON_FOLLOW']
    appScript.getActiveGroupOperation().evictUnlistedOnly == true
  }

  def "explicit grouping evicts extras without using the additive join operation"() {
    given:
    makeTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')

    when:
    appScript.processGroupCommandRequest([request: request('on')])

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_EXTRA']
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    appScript.getActiveGroupOperation() == null
  }

  def "explicit grouping transfers coordination before rebuilding an already joined group"() {
    given:
    coordinator.dataValues.id = 'RINCON_Z_COORD'
    follower.dataValues.id = 'RINCON_A_FOLLOW'
    Map operation = [
      operationId: 'explicit-coordinator-order', groupingMode: 'EXPLICIT',
      desiredCoordinatorId: 'RINCON_Z_COORD', resolvedCoordinatorId: 'RINCON_Z_COORD',
      requiredPlayerIds: ['RINCON_A_FOLLOW', 'RINCON_Z_COORD'], topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: true, groupId: 'GROUP-OLD', coordinatorId: 'RINCON_A_FOLLOW',
      playerIds: ['RINCON_A_FOLLOW', 'RINCON_Z_COORD']
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    follower.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_Z_COORD'],
      expectedTopology: [
        'RINCON_A_FOLLOW': [coordinatorId: 'RINCON_Z_COORD', memberIds: ['RINCON_A_FOLLOW', 'RINCON_Z_COORD']],
        'RINCON_Z_COORD': [coordinatorId: 'RINCON_Z_COORD', memberIds: ['RINCON_A_FOLLOW', 'RINCON_Z_COORD']]
      ]
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    operation.coordinatorTransferStarted == true
  }

  def "explicit grouping dissolves an existing target subgroup before creating around a solo coordinator"() {
    given:
    coordinator.dataValues.id = 'RINCON_Z_COORD'
    follower.dataValues.id = 'RINCON_A_FOLLOW'
    extra.dataValues.id = 'RINCON_B_EXTRA'
    Map operation = [
      operationId: 'explicit-split-subgroup', groupingMode: 'EXPLICIT',
      desiredCoordinatorId: 'RINCON_Z_COORD', resolvedCoordinatorId: 'RINCON_Z_COORD',
      requiredPlayerIds: ['RINCON_Z_COORD', 'RINCON_A_FOLLOW', 'RINCON_B_EXTRA'], topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: true, groupId: 'GROUP-SOLO', coordinatorId: 'RINCON_Z_COORD',
      playerIds: ['RINCON_Z_COORD'], observations: [
        [playerId: 'RINCON_Z_COORD', fresh: true, groupId: 'GROUP-SOLO',
          coordinatorId: 'RINCON_Z_COORD', playerIds: ['RINCON_Z_COORD']],
        [playerId: 'RINCON_A_FOLLOW', fresh: true, groupId: 'GROUP-SUB',
          coordinatorId: 'RINCON_A_FOLLOW', playerIds: ['RINCON_A_FOLLOW', 'RINCON_B_EXTRA']],
        [playerId: 'RINCON_B_EXTRA', fresh: true, groupId: 'GROUP-SUB',
          coordinatorId: 'RINCON_A_FOLLOW', playerIds: ['RINCON_A_FOLLOW', 'RINCON_B_EXTRA']]
      ]
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    follower.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_B_EXTRA']
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    operation.lastCommand == 'splitConflictingTargetGroup'
    operation.topologyMutationSent == true
  }

  def "explicit grouping expands a target subgroup already led by the configured coordinator"() {
    given:
    SonosAppPlayerDouble bedroom = createPlayer('RINCON_BEDROOM', 'GROUP-BEDROOM',
        'RINCON_BEDROOM', 'RINCON_BEDROOM', true)
    appScript.children = [group, coordinator, follower, extra, bedroom]
    appScript.app.children = appScript.children
    coordinator.dataValues.id = 'RINCON_COORD'
    Map operation = [
      operationId: 'explicit-preserve-coordinator', groupingMode: 'EXPLICIT',
      desiredCoordinatorId: 'RINCON_COORD', resolvedCoordinatorId: 'RINCON_COORD',
      requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA', 'RINCON_BEDROOM'],
      topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'], observations: [
        [playerId: 'RINCON_COORD', fresh: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
          playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']],
        [playerId: 'RINCON_FOLLOW', fresh: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
          playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']],
        [playerId: 'RINCON_EXTRA', fresh: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
          playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']],
        [playerId: 'RINCON_BEDROOM', fresh: true, groupId: 'GROUP-BEDROOM', coordinatorId: 'RINCON_BEDROOM',
          playerIds: ['RINCON_BEDROOM']]
      ]
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: ['RINCON_BEDROOM'], remove: []
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    coordinator.commands.every { it.name != 'playerModifyGroupMembers' || it.add == ['RINCON_BEDROOM'] }
    follower.commands.every { it.name != 'playerModifyGroupMembers' }
    extra.commands.every { it.name != 'playerModifyGroupMembers' }
    operation.lastCommand == 'addMissingPlayers'
  }

  def "play after on() runs immediately without an operation"() {
    given:
    appScript.processGroupCommandRequest([request: request('on')])
    coordinator.commands.clear()

    when:
    appScript.processGroupCommandRequest([request: request('play', [:], 2)])

    then:
    appScript.getActiveGroupOperation() == null
    coordinator.commands.find { it.name == 'play' } != null
    !appScript.state.containsKey('deferredGroupOperationCommands')
  }

  def "explicit group creation puts the desired coordinator first after transfer"() {
    given:
    coordinator.dataValues.id = 'RINCON_Z_COORD'
    follower.dataValues.id = 'RINCON_A_FOLLOW'
    Map operation = [
      operationId: 'explicit-coordinator-order', groupingMode: 'EXPLICIT',
      desiredCoordinatorId: 'RINCON_Z_COORD', resolvedCoordinatorId: 'RINCON_Z_COORD',
      requiredPlayerIds: ['RINCON_A_FOLLOW', 'RINCON_Z_COORD'],
      coordinatorTransferStarted: true, topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: true, groupId: 'GROUP-SPLIT', coordinatorId: 'RINCON_Z_COORD',
      playerIds: ['RINCON_Z_COORD']
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds ==
        ['RINCON_Z_COORD', 'RINCON_A_FOLLOW']
    operation.coordinatorTransferStarted == false
  }

  def "explicit coordinator transfer creates group from fresh conflicting singleton reports"() {
    given:
    coordinator.dataValues.id = 'RINCON_Z_COORD'
    follower.dataValues.id = 'RINCON_A_FOLLOW'
    Map operation = [
      operationId: 'explicit-transfer-conflicting-singletons', groupingMode: 'EXPLICIT',
      desiredCoordinatorId: 'RINCON_Z_COORD', resolvedCoordinatorId: 'RINCON_Z_COORD',
      requiredPlayerIds: ['RINCON_A_FOLLOW', 'RINCON_Z_COORD'],
      coordinatorTransferStarted: true, topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: false, groupId: null, coordinatorId: null, playerIds: [],
      observations: [
        [playerId: 'RINCON_Z_COORD', fresh: true, groupId: 'GROUP-SOLO-Z',
          coordinatorId: 'RINCON_Z_COORD', playerIds: ['RINCON_Z_COORD']],
        [playerId: 'RINCON_A_FOLLOW', fresh: true, groupId: 'GROUP-SOLO-A',
          coordinatorId: 'RINCON_A_FOLLOW', playerIds: ['RINCON_A_FOLLOW']]
      ]
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds ==
        ['RINCON_Z_COORD', 'RINCON_A_FOLLOW']
    operation.coordinatorTransferStarted == false
    operation.lastCommand == 'createExactGroup'
  }

  def "explicit coordinator transfer detaches follower still grouped with outside speaker"() {
    given:
    coordinator.dataValues.id = 'RINCON_COORD'
    follower.dataValues.id = 'RINCON_A_FOLLOW'
    extra.dataValues.id = 'RINCON_BEDROOM'
    Map operation = [
      operationId: 'explicit-transfer-detach-follower', groupingMode: 'EXPLICIT',
      desiredCoordinatorId: 'RINCON_COORD', resolvedCoordinatorId: 'RINCON_COORD',
      requiredPlayerIds: ['RINCON_COORD', 'RINCON_A_FOLLOW'],
      coordinatorTransferStarted: true, topologyAttempt: 0,
      coordinatorTransferPreviousCoordinatorId: 'RINCON_BEDROOM'
    ]
    Map topology = [
      fresh: true, consistent: false, groupId: null, coordinatorId: null, playerIds: [],
      observations: [
        [playerId: 'RINCON_COORD', fresh: true, groupId: 'GROUP-SOLO-COORD',
          coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
        [playerId: 'RINCON_A_FOLLOW', fresh: true, groupId: 'GROUP-OLD',
          coordinatorId: 'RINCON_BEDROOM', playerIds: ['RINCON_BEDROOM', 'RINCON_A_FOLLOW']]
      ]
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    extra.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_A_FOLLOW']
    ] || extra.commands.find { it.name == 'playerModifyGroupMembers' }?.remove == ['RINCON_A_FOLLOW']
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    operation.coordinatorTransferStarted == true
    operation.lastCommand == 'detachFollowerFromOldGroup'
    operation.topologyMutationSent == true
  }

  def "sendGroupTopologyCommand does not mutate topology for legacy CURRENT operation"() {
    given:
    coordinator.dataValues.id = 'RINCON_COORD'
    follower.dataValues.id = 'RINCON_A_FOLLOW'
    Map operation = [
      operationId: 'legacy-current-no-mutate', groupingMode: 'CURRENT',
      desiredCoordinatorId: 'RINCON_COORD', resolvedCoordinatorId: 'RINCON_COORD',
      requiredPlayerIds: ['RINCON_COORD', 'RINCON_A_FOLLOW'],
      coordinatorTransferStarted: false, topologyAttempt: 0
    ]
    Map topology = [
      fresh: true, consistent: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_A_FOLLOW'], observations: [
        [playerId: 'RINCON_COORD', fresh: true, groupId: 'GROUP-1',
          coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_A_FOLLOW']],
        [playerId: 'RINCON_A_FOLLOW', fresh: true, groupId: 'GROUP-1',
          coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_A_FOLLOW']]
      ]
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.every { it.name != 'playerCreateGroup' && it.name != 'playerModifyGroupMembers' }
    follower.commands.every { it.name != 'playerCreateGroup' && it.name != 'playerModifyGroupMembers' }
    extra.commands.every { it.name != 'playerCreateGroup' && it.name != 'playerModifyGroupMembers' }
  }

  def "additive grouping transfers coordination to the configured coordinator and preserves extras"() {
    given:
    coordinator.dataValues.groupId = 'GROUP-2'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA'
    follower.dataValues.groupId = 'GROUP-2'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA'
    coordinator.commands.clear()
    follower.commands.clear()

    when:
    appScript.processGroupCommandRequest([request: request('joinPlayersToCoordinator')])

    then:
    follower.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_COORD'],
      expectedTopology: [
        RINCON_COORD: [coordinatorId: 'RINCON_COORD', memberIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']],
        RINCON_FOLLOW: [coordinatorId: 'RINCON_COORD', memberIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']],
        RINCON_EXTRA: [coordinatorId: 'RINCON_COORD', memberIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']]
      ]
    ]
    follower.commands.every { it.name != 'playerCreateGroup' }
    appScript.getActiveGroupOperation() == null
    appScript.scheduled.any { List job -> job[1] == 'joinAssemblyAfterDetach' }
  }

  def "additive coordinator transfer recreates the preserved roster with configured coordinator first"() {
    given:
    group.dataValues.playerIds = 'RINCON_FOLLOW,RINCON_EXTRA'
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupId = 'GROUP-COORD'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    coordinator.commands.clear()
    follower.commands.clear()
    extra.commands.clear()

    when:
    appScript.handleJoinAdditiveGroupImmediate(group)

    then:
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds ==
        ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']
  }

  def "additive grouping sends the join when fresh topology responses are unavailable"() {
    given:
    coordinator.dataValues.groupId = 'GROUP-COORD'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    follower.dataValues.groupId = 'GROUP-FOLLOW'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'

    when:
    appScript.processGroupCommandRequest([request: request('joinPlayersToCoordinator')])

    then:
    def joined = coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW'] ||
      coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_FOLLOW']
    joined
    appScript.getActiveGroupOperation() == null
  }

  def "addPlayerToGroup resolves a case-insensitive player name and adds only that player through the configured coordinator"() {
    given:
    extra.dataValues.name = 'Bedroom'
    extra.dataValues.deviceIp = '192.168.1.33'
    extra.deviceNetworkId = 'AABBCCDDEEFF'
    extra.dataValues.groupId = 'GROUP-EXTRA'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    appScript.processGroupCommandRequest([request: request('addPlayerToGroup', [playerIdentifier: 'bedroom'])])

    expect:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: ['RINCON_EXTRA'], remove: []
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
    follower.commands.empty
    extra.commands.empty
  }

  def "inactive group add targets only the requested player on its coordinator"() {
    given:
    extra.dataValues.groupId = 'GROUP-EXTRA'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    group.dataValues.playerIds = 'RINCON_FOLLOW,RINCON_EXTRA'
    group.currentValues.switch = 'off'

    when:
    appScript.processGroupCommandRequest([request: request('addPlayerToGroup', [playerIdentifier: 'RINCON_EXTRA'])])

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_EXTRA']
    coordinator.commands.every { it.name != 'playerCreateGroup' && it.name != 'playerPlay' }
    follower.commands.empty
    extra.commands.empty
  }

  def "player add request from a follower is dispatched through its current coordinator"() {
    given:
    extra.dataValues.groupId = 'GROUP-EXTRA'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'

    when:
    appScript.processPlayerCommandRequest([request: [
      playerDni: follower.deviceNetworkId, command: 'addPlayerToGroup', args: ['RINCON_EXTRA']
    ]])

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_EXTRA']
    follower.commands.empty
  }

  def "player parent requests are acknowledged by the return value and processed from a new execution"() {
    given:
    extra.dataValues.groupId = 'GROUP-OTHER'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    Map playerRequest = [playerDni: follower.deviceNetworkId, requestId: "${follower.deviceNetworkId}-1",
      command: 'addPlayerToGroup', args: ['RINCON_EXTRA']]

    when:
    Boolean accepted = appScript.playerCommandRequest(playerRequest)

    then: 'the entry point only validates and schedules; nothing is written to a child'
    accepted
    appScript.scheduled.count { List job -> job[1] == 'processPlayerCommandRequest' } == 1
    coordinator.commands.empty
    follower.commands.empty

    when:
    runScheduledCallbacks()

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_EXTRA']
  }

  def "absolute commands are latest-wins per target while additive commands keep arrival order"() {
    given:
    Map newer = [playerDni: follower.deviceNetworkId, command: 'setGroupVolume', args: [40, null], epoch: 1L, sequence: 5L]
    Map older = [playerDni: follower.deviceNetworkId, command: 'setGroupVolume', args: [10, null], epoch: 1L, sequence: 4L]
    Map addA = [playerDni: follower.deviceNetworkId, command: 'addPlayerToGroup', args: ['A'], epoch: 1L, sequence: 7L]
    Map addB = [playerDni: follower.deviceNetworkId, command: 'addPlayerToGroup', args: ['B'], epoch: 1L, sequence: 6L]

    expect: 'an older absolute command that arrives after a newer one is acknowledged but dropped'
    appScript.playerCommandRequest(newer)
    appScript.playerCommandRequest(older)
    appScript.scheduled.count { List job -> job[1] == 'processPlayerCommandRequest' } == 1

    and: 'additive commands are never dropped, even with a lower sequence than the previous one'
    appScript.playerCommandRequest(addA)
    appScript.playerCommandRequest(addB)
    appScript.scheduled.count { List job -> job[1] == 'processPlayerCommandRequest' } == 3

    and: 'a new driver epoch resets the stream'
    appScript.playerCommandRequest(older + [epoch: 2L, sequence: 1L])
    appScript.scheduled.count { List job -> job[1] == 'processPlayerCommandRequest' } == 4
  }

  def "a superseded absolute command is skipped when its callback runs out of order"() {
    given: 'the older command is accepted first, then the newer one; both schedule a callback'
    Map older = [playerDni: follower.deviceNetworkId, command: 'setGroupVolume', args: [10, null], epoch: 1L, sequence: 8L]
    Map newer = [playerDni: follower.deviceNetworkId, command: 'setGroupVolume', args: [40, null], epoch: 1L, sequence: 9L]
    appScript.playerCommandRequest(older)
    appScript.playerCommandRequest(newer)
    List<List> jobs = appScript.scheduled.findAll { List job -> job[1] == 'processPlayerCommandRequest' }
    assert jobs.size() == 2

    when: 'the newer callback runs first, then the older one'
    appScript.processPlayerCommandRequest(jobs[1][2].data)
    appScript.processPlayerCommandRequest(jobs[0][2].data)

    then: 'the coordinator receives only the newer command'
    coordinator.commands.findAll { it.name == 'setGroupVolume' }*.level == [40G]
  }

  def "a callback from an older driver epoch cannot apply after one from the new epoch"() {
    expect:
    appScript.applySequence('stream', 2L, 5L)
    !appScript.applySequence('stream', 1L, 99L)
    appScript.applySequence('stream', 2L, 6L)
    appScript.acceptSequence('other', 2L, 1L)
    !appScript.acceptSequence('other', 1L, 50L)
  }

  def "group commands are processed in arrival order even when callbacks run in reverse"() {
    given:
    Map first = request('on', [:], 1)
    Map second = request('play', [:], 2)
    appScript.acceptGroupCommandRequest(first)
    appScript.acceptGroupCommandRequest(second)
    List<List> jobs = appScript.scheduled.findAll { List job -> job[1] == 'processGroupCommandRequest' }
    assert jobs.size() == 2

    when: 'the second callback is delivered first'
    appScript.processGroupCommandRequest(jobs[1][2].data)
    appScript.processGroupCommandRequest(jobs[0][2].data)

    then: 'the activation is still processed before Play'
    List<String> processing = appScript.logs.findAll { String line -> line.contains('Processing group command') }
    processing.size() == 2
    processing[0].contains('command on ')
    processing[1].contains('command play ')
  }

  def "player identifier resolver accepts MAC DNI and IP forms"() {
    given:
    extra.dataValues.deviceIp = '192.168.1.33'
    extra.deviceNetworkId = 'AA:BB:CC:DD:EE:FF'

    expect:
    appScript.resolvePlayerIdentifier('rincon_extra') == extra
    appScript.resolvePlayerIdentifier('AA-BB-CC-DD-EE-FF') == extra
    appScript.resolvePlayerIdentifier('AABBCCDDEEFF') == extra
    appScript.resolvePlayerIdentifier('192.168.1.33:1400') == extra
  }

  def "ambiguous player names do not change group membership"() {
    given:
    follower.dataValues.name = 'Bedroom'
    extra.dataValues.name = 'Bedroom'

    when:
    appScript.processGroupCommandRequest([request: request('addPlayerToGroup', [playerIdentifier: 'bedroom'])])

    then:
    coordinator.commands.empty
    appScript.logs.any { String entry -> entry.contains('ambiguous') }
  }

  def "standalone coordinator creates a group with itself first when group ID is unavailable"() {
    given:
    coordinator.dataValues.groupId = ''
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    coordinator.currentValues.isGrouped = 'off'
    extra.dataValues.groupId = 'GROUP-EXTRA'

    when:
    appScript.processGroupCommandRequest([request: request('addPlayerToGroup', [playerIdentifier: 'RINCON_EXTRA'])])

    then:
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds == ['RINCON_COORD', 'RINCON_EXTRA']
    coordinator.commands.every { it.name != 'playerModifyGroupMembers' }
  }

  def "topology satisfaction uses exact equality for explicit mode and subset matching for additive mode"() {
    given:
    Map exactOperation = [
      groupingMode: 'EXPLICIT', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      desiredCoordinatorId: 'RINCON_COORD'
    ]
    Map additiveOperation = [
      groupingMode: 'ADDITIVE', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      desiredCoordinatorId: 'RINCON_COORD'
    ]
    Map topology = [
      consistent: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']
    ]

    expect:
    !appScript.isGroupTopologySatisfied(exactOperation, topology)
    appScript.isGroupTopologySatisfied(additiveOperation, topology)
    !appScript.isGroupTopologySatisfied(additiveOperation, topology + [coordinatorId: 'RINCON_FOLLOW'])
    appScript.isGroupTopologySatisfied(exactOperation, topology + [playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']])
  }

  def "explicit group accepts a fresh exact coordinator roster without a fresh follower report"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    operation.topologyMutationSent = true
    operation.lastCommand = 'createExactGroup'
    operation.lastCommandAt = appScript.now() - 11000L
    operation.topologyAttempt = 1
    appScript.saveActiveGroupOperation(operation)
    emitTopologyObservation('RINCON_COORD', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')

    when:
    Map topology = appScript.readGroupOperationTopology(operation)
    appScript.advanceGroupTopologyOperation([operationId: operation.operationId])

    then:
    topology.fresh == true
    topology.consistent == true
    topology.authorityPlayerId == 'RINCON_COORD'
    topology.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW']
    appScript.getActiveGroupOperation() == null
    appScript.getFreshGroupingContext('GROUP-DNI').mode == 'EXPLICIT'
    coordinator.commands.findAll { it.name == 'playerCreateGroup' }.empty
  }

  def "explicit group refreshes missing topology confirmation without repeating createGroup"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    operation.topologyMutationSent = true
    operation.lastCommand = 'createExactGroup'
    operation.lastCommandAt = appScript.now() - 11000L
    operation.topologyAttempt = 1
    appScript.saveActiveGroupOperation(operation)
    coordinator.commands.clear()

    when:
    appScript.advanceGroupTopologyOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation().lastCommand == 'refreshGroupTopology'
    coordinator.commands.find { it.name == 'playerGetGroupsFull' } != null
    coordinator.commands.find { it.name == 'playerCreateGroup' } == null
  }

  def "explicit group retries createGroup when every fresh follower still reports standalone"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    operation.topologyMutationSent = true
    operation.lastCommand = 'createExactGroup'
    operation.lastCommandAt = appScript.now() - 11000L
    operation.topologyAttempt = 1
    appScript.saveActiveGroupOperation(operation)
    coordinator.commands.clear()
    follower.commands.clear()
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-FOLLOW', 'RINCON_FOLLOW', 'RINCON_FOLLOW')

    when:
    appScript.advanceGroupTopologyOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation().lastCommand == 'createExactGroup'
    appScript.getActiveGroupOperation().topologyMutationSent == true
    appScript.getActiveGroupOperation().topologyAttempt == 2
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds ==
        ['RINCON_COORD', 'RINCON_FOLLOW']
  }

  def "explicit group accepts an exact fresh follower roster when coordinator report is missing"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')

    when:
    Map topology = appScript.readGroupOperationTopology(operation)

    then:
    topology.fresh == true
    topology.consistent == true
    topology.authorityPlayerId == 'RINCON_FOLLOW'
    topology.coordinatorId == 'RINCON_COORD'
    topology.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW']
  }

  def "Favorite ID loads directly without topology queries or group mutations"() {
    when:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])

    then:
    coordinator.commands.find { it.name == 'loadFavorite' } == [name: 'loadFavorite', favoriteId: '42']
    appScript.getActiveGroupOperation() == null
    [coordinator, follower, extra].every { SonosAppPlayerDouble player ->
      !player.commands.any { it.name in ['playerGetGroupsFull', 'playerCreateGroup', 'playerModifyGroupMembers'] }
    }
  }

  def "group favorite without an ID forwards null once to the current coordinator"() {
    when:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [:], 1)])

    then:
    coordinator.commands.findAll { it.name == 'loadFavorite' } == [[name: 'loadFavorite', favoriteId: null]]
    appScript.getActiveGroupOperation() == null
  }

  def "group playlist without an ID forwards null once to the current coordinator"() {
    when:
    appScript.processGroupCommandRequest([request: request('loadPlaylist', [:], 1)])

    then:
    coordinator.commands.findAll { it.name == 'loadPlaylist' } == [[name: 'loadPlaylist', playlistId: null]]
  }

  def "group Favorite relay uses the currently reported coordinator"() {
    given:
    coordinator.currentValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.currentValues.groupCoordinatorId = 'RINCON_FOLLOW'

    when:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])

    then:
    follower.commands.find { it.name == 'loadFavorite' } == [name: 'loadFavorite', favoriteId: '42']
    !coordinator.commands.any { it.name == 'loadFavorite' }
  }

  def "a negative group observation prevents stale cached topology from satisfying the operation"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    emitTopologyObservation('RINCON_COORD', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')
    emitTopologyObservation('RINCON_FOLLOW', null, null, '')

    when:
    Map topology = appScript.readGroupOperationTopology(operation)

    then:
    topology.fresh == true
    topology.consistent == false
    topology.observations.find { it.playerId == 'RINCON_FOLLOW' }.groupId == null
    appScript.getActiveGroupOperation() != null
  }

  def "queued group requests preserve order while Favorite loads stay one-shot"() {
    given:
    coordinator.dataValues.groupId = 'GROUP-COORD'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    follower.dataValues.groupId = 'GROUP-FOLLOW'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'

    when:
    appScript.acceptGroupCommandRequest(request('loadFavorite', [favoriteId: '42'], 2))
    appScript.acceptGroupCommandRequest(request('on', [:], 1))
    runScheduledCallbacks()

    then:
    appScript.getActiveGroupOperation() == null
    // on() starts the topology operation, but Favorite still goes directly to the current coordinator.
    coordinator.commands.find { it.name == 'loadFavorite' }?.favoriteId == '42'
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW'] ||
      coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_FOLLOW']
    !appScript.atomicState.containsKey('deferredGroupOperationCommands')
  }

  def "direct group requests from another app instance are rejected"() {
    when:
    appScript.groupAppRequest([channel: 'groupAppRequest', groupDni: 'OTHER-GROUP', requestId: 'OTHER-GROUP-1', command: 'on', args: [:]])

    then:
    !appScript.scheduled.any { it[1] == 'processGroupCommandRequest' }
  }

  def "direct group requests are accepted and each schedules its own processing callback"() {
    given:
    Map groupRequest = request('refresh', [:], 1)
    groupRequest.requestId = '6b684b7d-8f6c-4b18-9cf1-2eaad384bf5a'
    groupRequest.channel = 'groupAppRequest'

    when:
    Boolean accepted = appScript.groupAppRequest(groupRequest)

    then:
    accepted
    appScript.scheduled.count { List job -> job[1] == 'processGroupCommandRequest' && job[2].data.request.requestId == groupRequest.requestId } == 1
    group.events.empty
  }

  def "child attribute events are sent once without queueing or retrying"() {
    given:
    follower.metaClass.sendEvent = { Map event -> throw new RuntimeException('simulated failure') }

    when:
    appScript.sendChildAttributeEvents(follower, [transportStatus: 'playing'])
    appScript.sendChildAttributeEvents(extra, [transportStatus: 'paused'])

    then:
    extra.emittedAttributes().transportStatus == 'paused'
    !appScript.state.containsKey('pendingChildEvents')
    !appScript.state.containsKey('childStateDrainScheduled')
    !appScript.scheduled.any { List call -> call[1] == 'drainQueuedChildEvents' }
    appScript.logs.any { String line -> line.contains('Could not emit transportStatus') }
  }

  def "direct child event calls do not cache or suppress unchanged values"() {
    when:
    appScript.sendChildAttributeEvents(follower, [transportStatus: 'playing'])
    appScript.sendChildAttributeEvents(follower, [transportStatus: 'playing'])

    then:
    follower.events.count { Map event -> event.name == 'transportStatus' && event.value == 'playing' } == 2
    !appScript.state.containsKey('lastSentChildState')
  }

  def "alarm device request event dispatches through the parent app handler"() {
    given:
    SonosAppPlayerDouble alarmManager = new SonosAppPlayerDouble(
      deviceNetworkId: '259-SonosAlarmManager-HOUSE-1', displayName: 'Alarm Manager')
    appScript.children << alarmManager
    appScript.app.children = appScript.children
    List<String> refreshedHouseholds = []
    appScript.metaClass.refreshSonosAlarmsForHousehold = { String householdId ->
      refreshedHouseholds << householdId
    }

    when:
    appScript.alarmRequestHandler([value: JsonOutput.toJson([
      version: 1, channel: 'alarmManager', action: 'refresh', sourceDni: alarmManager.deviceNetworkId,
      householdId: 'HOUSE-1', requestId: 'alarm-request-1', args: [:]
    ])] as Event)

    then:
    refreshedHouseholds == ['HOUSE-1']
  }

  def "alarm manager and switch requests dispatch every action with decoded arguments"() {
    given:
    String managerDni = '259-SonosAlarmManager-HOUSE-1'
    String switchDni = '259-SonosAlarm-HOUSE-1-ALARM-1'
    SonosAppPlayerDouble alarmManager = new SonosAppPlayerDouble(deviceNetworkId: managerDni)
    SonosAppPlayerDouble alarmSwitch = new SonosAppPlayerDouble(deviceNetworkId: switchDni)
    appScript.children << alarmManager
    appScript.children << alarmSwitch
    appScript.app.children = appScript.children
    List<List> calls = []
    appScript.metaClass.refreshSonosAlarmsForHousehold = { String householdId -> calls << ['refresh', householdId] }
    appScript.metaClass.createSonosAlarm = { String householdId, Map args -> calls << ['create', householdId, args] }
    appScript.metaClass.createSonosAlarmFromCatalogSource = { String householdId, String type, String id, Map args -> calls << ['createFromCatalogSource', householdId, type, id, args] }
    appScript.metaClass.destroySonosAlarm = { String householdId, String id, String room -> calls << ['destroy', householdId, id, room] }
    appScript.metaClass.setSonosAlarmEnabled = { String dni, Boolean value -> calls << ['setEnabled', dni, value] }
    appScript.metaClass.setSonosAlarmTime = { String dni, String value -> calls << ['setTime', dni, value] }
    appScript.metaClass.setSonosAlarmVolume = { String dni, Integer value -> calls << ['setVolume', dni, value] }
    appScript.metaClass.setSonosAlarmRecurrence = { String dni, String value -> calls << ['setRecurrence', dni, value] }
    appScript.metaClass.setSonosAlarmDuration = { String dni, String value -> calls << ['setDuration', dni, value] }
    appScript.metaClass.setSonosAlarmRoom = { String dni, String value -> calls << ['setRoom', dni, value] }
    appScript.metaClass.setSonosAlarmSource = { String dni, String uri, String metadata -> calls << ['setSource', dni, uri, metadata] }
    appScript.metaClass.setSonosAlarmPlayMode = { String dni, String value -> calls << ['setPlayMode', dni, value] }
    appScript.metaClass.setSonosAlarmIncludeGroupedRooms = { String dni, Boolean value -> calls << ['setIncludeGroupedRooms', dni, value] }
    Closure sendRequest = { String channel, String action, String dni, Object value = null, Map args = [:] ->
      Map request = [version: 1, channel: channel, action: action, sourceDni: dni,
        requestId: "${channel}-${action}", args: args]
      if(channel == 'alarmManager') { request.householdId = 'HOUSE-1' }
      if(value != null) { request.value = value }
      appScript.alarmRequestHandler([value: JsonOutput.toJson(request)] as Event)
    }

    when:
    sendRequest('alarmManager', 'create', managerDni, null, [Volume: '5'])
    sendRequest('alarmManager', 'createFromCatalogSource', managerDni, null,
      [sourceType: 'favorite', sourceId: '42', Volume: '5'])
    sendRequest('alarmManager', 'destroy', managerDni, null, [alarmId: '42', roomUuid: 'RINCON-1'])
    sendRequest('alarmSwitch', 'setEnabled', switchDni, true)
    sendRequest('alarmSwitch', 'refresh', switchDni, 'HOUSE-1')
    sendRequest('alarmSwitch', 'setTime', switchDni, '07:30:00')
    sendRequest('alarmSwitch', 'setVolume', switchDni, 5)
    sendRequest('alarmSwitch', 'setRecurrence', switchDni, 'WEEKDAYS')
    sendRequest('alarmSwitch', 'setDuration', switchDni, '00:15:00')
    sendRequest('alarmSwitch', 'setRoom', switchDni, 'RINCON-1')
    sendRequest('alarmSwitch', 'setSource', switchDni, [programUri: 'uri', programMetadata: 'meta'])
    sendRequest('alarmSwitch', 'setPlayMode', switchDni, 'NORMAL')
    sendRequest('alarmSwitch', 'setIncludeGroupedRooms', switchDni, true)

    then:
    calls*.first() == ['create', 'createFromCatalogSource', 'destroy', 'setEnabled', 'refresh',
      'setTime', 'setVolume', 'setRecurrence', 'setDuration', 'setRoom', 'setSource',
      'setPlayMode', 'setIncludeGroupedRooms']
    calls[0][1..2] == ['HOUSE-1', [Volume: '5']]
    calls[1][1..3] == ['HOUSE-1', 'favorite', '42']
    calls[2] == ['destroy', 'HOUSE-1', '42', 'RINCON-1']
    calls[3..-1].every { List call -> call[1] == switchDni || call[0] == 'refresh' }
    calls.find { List call -> call[0] == 'setVolume' } == ['setVolume', switchDni, 5]
    calls.find { List call -> call[0] == 'setSource' } == ['setSource', switchDni, 'uri', 'meta']
  }

  def "Sonos alarm bounded XML parsing handles namespace prefixes in both XML layers"() {
    given:
    String alarmList = '<a:CurrentAlarmList xmlns:a="urn:sonos:alarms">' +
      '<a:Alarm ID="alarm-1" StartTime="07:15:00"/>' +
      '<a:Alarm ID="alarm-2" StartTime="08:30:00" StartLocalTime="08:30:00"/>' +
      '<a:Alarm StartTime="09:00:00"/></a:CurrentAlarmList>'
    String response = '<s:Envelope xmlns:s="urn:soap"><s:Body><u:GetZoneGroupAttributesResponse xmlns:u="urn:sonos:service">' +
      '<r:CurrentAlarmList xmlns:r="urn:sonos:response">' + StringEscapeUtils.escapeXml10(alarmList) +
      '</r:CurrentAlarmList></u:GetZoneGroupAttributesResponse></s:Body></s:Envelope>'

    when:
    List<Map> alarms = appScript.parseSonosAlarmList(response)

    then:
    alarms == [
      [ID: 'alarm-1', StartTime: '07:15:00', StartLocalTime: '07:15:00'],
      [ID: 'alarm-2', StartTime: '08:30:00', StartLocalTime: '08:30:00']
    ]
  }

  def "each AlarmClock notification is processed from its own execution and merges partial values"() {
    given:
    coordinator.dataValues.householdId = 'HOUSE-1'
    appScript.app.children = appScript.children
    appScript.settings.enableAlarmSwitches = true
    Closure refreshes = { -> appScript.asyncCalls.findAll { Map call -> call.cb == 'sonosAlarmListCallback' }*.data.householdId }

    when:
    appScript.putPlayerUpnpEvents([service: 'AlarmClock', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmListVersion: 'V1', TimeZone: 'tz-1']])
    appScript.putPlayerUpnpEvents([service: 'AlarmClock', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmListVersion: 'V2']])
    runScheduledCallbacks()

    then:
    refreshes() == ['HOUSE-1', 'HOUSE-1']
    appScript.getAlarmClockHouseholdState('HOUSE-1').values == [AlarmListVersion: 'V2', TimeZone: 'tz-1']
    appScript.getAlarmClockHouseholdState('HOUSE-1').requestedVersion == 'V2'

    when: 'the same version arrives again while its refresh is in flight'
    appScript.processAlarmClockNotification([sourceDni: coordinator.deviceNetworkId, values: [AlarmListVersion: 'V2']])

    then:
    refreshes() == ['HOUSE-1', 'HOUSE-1']

    when: 'the list response records the fetched version'
    appScript.updateAlarmClockHouseholdState('HOUSE-1', [lastFetchedVersion: 'V2', requestedVersion: null])
    appScript.processAlarmClockNotification([sourceDni: coordinator.deviceNetworkId, values: [AlarmListVersion: 'V2']])
    appScript.processAlarmClockNotification([sourceDni: coordinator.deviceNetworkId, values: [AlarmListVersion: 'V3']])

    then:
    refreshes() == ['HOUSE-1', 'HOUSE-1', 'HOUSE-1']
  }

  def "AlarmClock version change does not refresh switches when alarm switches are disabled"() {
    given:
    coordinator.dataValues.householdId = 'HOUSE-1'
    appScript.app.children = appScript.children
    appScript.settings.enableAlarmSwitches = false
    Closure refreshes = { -> appScript.asyncCalls.findAll { Map call -> call.cb == 'sonosAlarmListCallback' }*.data.householdId }

    when:
    appScript.processAlarmClockNotification([sourceDni: coordinator.deviceNetworkId, values: [AlarmListVersion: 'V1']])

    then:
    refreshes().isEmpty()
  }

  def "AlarmClock partial metadata updates the manager without clearing earlier values"() {
    given:
    coordinator.dataValues.householdId = 'HOUSE-1'
    appScript.settings.enableAlarmSwitches = false
    SonosAppPlayerDouble manager = new SonosAppPlayerDouble(deviceNetworkId: "${appScript.app.id}-SonosAlarmManager-HOUSE-1")
    appScript.children << manager
    appScript.app.children = appScript.children

    when:
    appScript.processAlarmClockNotification([sourceDni: coordinator.deviceNetworkId,
      values: [TimeZone: 'tz-1', TimeServer: 'time.example', Bogus: 'x']])
    appScript.processAlarmClockNotification([sourceDni: coordinator.deviceNetworkId, values: [DateFormat: 'DMY']])

    then:
    manager.emittedAttributes() == [sonosTimeZone: 'tz-1', sonosTimeServer: 'time.example', sonosDateFormat: 'DMY']
    manager.events.every { Map event -> event.name != 'sonosTimeGeneration' }
    appScript.getAlarmClockHouseholdState('HOUSE-1').values == [TimeZone: 'tz-1', TimeServer: 'time.example', DateFormat: 'DMY']
  }

  def "ListAlarms response updates switches, removes missing ones, and records the list version"() {
    given:
    coordinator.dataValues.householdId = 'HOUSE-1'
    appScript.settings.enableAlarmSwitches = true
    String keptDni = "${appScript.app.id}-SonosAlarm-HOUSE-1-A1"
    String goneDni = "${appScript.app.id}-SonosAlarm-HOUSE-1-A2"
    SonosAppPlayerDouble kept = new SonosAppPlayerDouble(deviceNetworkId: keptDni)
    SonosAppPlayerDouble gone = new SonosAppPlayerDouble(deviceNetworkId: goneDni)
    appScript.children << kept
    appScript.children << gone
    appScript.app.children = appScript.children
    appScript.state.sonosAlarmRefreshRequestIds = ['HOUSE-1': 'req-1']
    appScript.updateAlarmClockHouseholdState('HOUSE-1', [requestedVersion: 'V2'])
    String alarmList = '<Alarms><Alarm ID="A1" StartLocalTime="07:30:00" Enabled="1" Volume="12" RoomUUID="RINCON_COORD"/></Alarms>'
    String response = '<s:Envelope xmlns:s="urn:soap"><s:Body><u:ListAlarmsResponse xmlns:u="urn:sonos">' +
      '<CurrentAlarmList>' + StringEscapeUtils.escapeXml10(alarmList) + '</CurrentAlarmList>' +
      '<CurrentAlarmListVersion>V2</CurrentAlarmListVersion></u:ListAlarmsResponse></s:Body></s:Envelope>'

    expect:
    appScript.parseSonosAlarmListVersion(response) == 'V2'
    appScript.parseSonosAlarmListVersion('<broken') == null

    when:
    appScript.sonosAlarmListCallback(new hubitat.scheduling.AsyncResponse(status: 200, data: response),
      [householdId: 'HOUSE-1', requestId: 'req-1'])

    then:
    kept.emittedAttributes().alarmTime == '07:30:00'
    kept.emittedAttributes().alarmVolume == 12
    !appScript.children.any { it.deviceNetworkId == goneDni }
    appScript.getAlarmClockHouseholdState('HOUSE-1').lastFetchedVersion == 'V2'
    !appScript.getAlarmClockHouseholdState('HOUSE-1').containsKey('requestedVersion')
  }

  def "an alarm start time that arrives before running state is retained and cleared when the alarm ends"() {
    given:
    coordinator.dataValues.householdId = 'HOUSE-1'
    appScript.app.children = appScript.children
    SonosAppPlayerDouble alarmOne = new SonosAppPlayerDouble(deviceNetworkId: "${appScript.app.id}-SonosAlarm-HOUSE-1-1")
    appScript.children << alarmOne
    appScript.app.children = appScript.children

    when: 'only the start time arrives first'
    appScript.putPlayerUpnpEvents([service: 'AlarmRunning', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmLoggedStartTime: 'T9']])
    runScheduledCallbacks()

    then:
    alarmOne.events.isEmpty()

    when: 'running state follows'
    appScript.putPlayerUpnpEvents([service: 'AlarmRunning', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmRunning: '1', AlarmIDRunning: '1']])
    runScheduledCallbacks()

    then:
    alarmOne.emittedAttributes() == [alarmRunning: 'true', alarmLoggedStartTime: 'T9']

    when: 'the alarm ends'
    appScript.putPlayerUpnpEvents([service: 'AlarmRunning', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmRunning: '0', AlarmIDRunning: '']])
    runScheduledCallbacks()

    then:
    !(appScript.state.alarmRunningByHousehold ?: [:]).containsKey('HOUSE-1')
  }

  def "AVT alarm-running state updates only the matching alarm switch and keeps enabled state separate"() {
    given:
    coordinator.dataValues.householdId = 'HOUSE-1'
    appScript.app.children = appScript.children
    SonosAppPlayerDouble alarmOne = new SonosAppPlayerDouble(deviceNetworkId: "${appScript.app.id}-SonosAlarm-HOUSE-1-1")
    SonosAppPlayerDouble alarmTwo = new SonosAppPlayerDouble(deviceNetworkId: "${appScript.app.id}-SonosAlarm-HOUSE-1-2")
    appScript.children << alarmOne
    appScript.children << alarmTwo
    appScript.app.children = appScript.children

    when: 'alarm 1 starts'
    appScript.putPlayerUpnpEvents([service: 'AlarmRunning', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmRunning: '1', AlarmIDRunning: '1', AlarmLoggedStartTime: 'T1']])
    runScheduledCallbacks()

    then:
    alarmOne.emittedAttributes() == [alarmRunning: 'true', alarmLoggedStartTime: 'T1']
    alarmTwo.events.isEmpty()
    appScript.getRunningAlarmId('HOUSE-1') == '1'

    when: 'a partial event stops it, then alarm 2 starts'
    appScript.putPlayerUpnpEvents([service: 'AlarmRunning', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmRunning: '0', AlarmIDRunning: '']])
    appScript.putPlayerUpnpEvents([service: 'AlarmRunning', sourceDni: coordinator.deviceNetworkId,
      values: [AlarmRunning: '1', AlarmIDRunning: '2']])
    runScheduledCallbacks()

    then:
    alarmOne.emittedAttributes().alarmRunning == 'false'
    alarmTwo.emittedAttributes().alarmRunning == 'true'
    !alarmOne.events.any { Map event -> event.name == 'switch' }
    !alarmTwo.events.any { Map event -> event.name == 'switch' }
    appScript.getRunningAlarmId('HOUSE-1') == '2'

    when: 'the alarm stops with no alarm ID reported'
    appScript.processAlarmRunningNotification([sourceDni: coordinator.deviceNetworkId, values: [AlarmRunning: '0']])

    then:
    alarmTwo.emittedAttributes().alarmRunning == 'false'
    appScript.getRunningAlarmId('HOUSE-1') == null
  }

  def "AVT alarm-running data from a player without a household is ignored"() {
    when:
    appScript.processAlarmRunningNotification([sourceDni: coordinator.deviceNetworkId, values: [AlarmRunning: '1', AlarmIDRunning: '1']])

    then:
    appScript.getRunningAlarmId('HOUSE-1') == null
    !appScript.state.containsKey('alarmRunningByHousehold')
  }

  def "Sonos alarm SOAP builder escapes values as XML text"() {
    when:
    String body = appScript.sonosAlarmSoapBody('SetAlarm', [ProgramURI: 'x?a=1&b=<two>'])

    then:
    body.contains('<ProgramURI>x?a=1&amp;b=&lt;two&gt;</ProgramURI>')
  }

  def "player app policy refresh requests update policy snapshots from new executions"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysNonInterrupting'
    appScript.settings.highPriorityAnnouncementMode = 'alwaysCoordinatorStream'

    when:
    appScript.playerAppRequestHandler([value: JsonOutput.toJson([
      version: 1, channel: 'playerAppRequest', action: 'refreshPolicy',
      sourceDni: coordinator.deviceNetworkId, requestId: 'policy-request', args: [:]
    ])] as Event)

    then: 'the request is acknowledged and deferred; nothing is written to a child inline'
    coordinator.dataValues.normalAnnouncementMode == null

    when:
    runScheduledCallbacks(['refreshPlayerPolicyCallback'])

    then:
    coordinator.dataValues.normalAnnouncementMode == 'alwaysNonInterrupting'
    coordinator.dataValues.highPriorityAnnouncementMode == 'alwaysCoordinatorStream'
    !coordinator.dataValues.containsKey('useAudioClipForAirPlay')
  }

  def "changing one announcement priority refreshes its policy without changing the other"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysNonInterrupting'
    appScript.settings.highPriorityAnnouncementMode = 'alwaysNonInterrupting'
    appScript.refreshPlayerAnnouncementPolicySnapshots(true)

    when:
    appScript.handleNewUiSettingChange('newUiSetting|highPriorityAnnouncementMode|alwaysCoordinatorStream')

    then:
    coordinator.dataValues.normalAnnouncementMode == 'alwaysNonInterrupting'
    coordinator.dataValues.highPriorityAnnouncementMode == 'alwaysCoordinatorStream'
    follower.dataValues.normalAnnouncementMode == 'alwaysNonInterrupting'
    follower.dataValues.highPriorityAnnouncementMode == 'alwaysCoordinatorStream'
  }

  def "duplicate enum setting selection is ignored without updating or firing an event"() {
    given:
    appScript.settings.highPriorityAnnouncementMode = 'alwaysCoordinatorStream'

    when:
    appScript.handleNewUiSettingChange('newUiSetting|highPriorityAnnouncementMode|alwaysCoordinatorStream')

    then:
    appScript.app.updatedSettings.isEmpty()
    appScript.app.events.isEmpty()
    appScript.logs.any { String message -> message.contains('Ignoring duplicate custom setting') }

    when:
    appScript.handleNewUiSettingChange('newUiSetting|highPriorityAnnouncementMode|alwaysNonInterrupting')

    then:
    appScript.app.updatedSettings['highPriorityAnnouncementMode'] == 'alwaysNonInterrupting'
    appScript.app.events.count { Map event -> event.name == 'newUiSettings' } == 1
  }

  def "active group TTS sends POSTs to every player"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.isGrouped = 'on'
    coordinator.currentValues.isGroupCoordinator = 'on'
    coordinator.currentValues.status = 'playing'
    String correlationId = 'test-tts-correlation'

    when:
    appScript.processGroupCommandRequest([request: request('playAudioClip', [
      uri: 'http://example.test/tts.mp3', volume: '15', duration: 2,
      correlationId: correlationId
    ])])

    then:
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.every { Map call ->
      call.params.uri.endsWith('/api/v1/players/local/audioClip') &&
        new JsonSlurper().parseText(call.params.body as String).streamUrl == 'http://example.test/tts.mp3'
    }
    appScript.appMemoryMap('audioClipQueuesByPlayer').size() == 2
  }

  def "group TTS uses only the group chime setting"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.settings.chimeBeforeStandardPriorityTTS = true
    follower.settings.chimeBeforeStandardPriorityTTS = false
    coordinator.settings.chimeBeforeHighPriorityTTS = false
    follower.settings.chimeBeforeHighPriorityTTS = true
    Map item = [correlationId: 'group-chime-policy', uri: 'http://example.test/tts.mp3',
      volume: '10', duration: 5, isTts: true]

    when: 'no group preference is supplied'
    appScript.dispatchGroupAnnouncementClip(item, [coordinator, follower], false)

    then: 'individual player preferences are ignored'
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    appScript.asyncCalls.every { Map call ->
      (new JsonSlurper().parseText(call.params.body as String) as Map).clipType != 'CHIME'
    }

    when: 'the group preference enables a standard TTS chime'
    appScript.appMemory()['audioClipQueuesByPlayer'] = [:]
    appScript.asyncCalls.clear()
    item.chimeBeforeStandardPriorityTTS = true
    appScript.dispatchGroupAnnouncementClip(item, [coordinator, follower], false)

    then: 'both group members receive the group-selected chime'
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    appScript.asyncCalls.every { Map call ->
      (new JsonSlurper().parseText(call.params.body as String) as Map).clipType == 'CHIME'
    }
  }

  def "separate group chime phase holds each player's TTS until the chime release"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.queueChimeSeparatelyFromTTS = true
    Map item = [correlationId: 'separate-chime', uri: 'http://example.test/tts.mp3',
      volume: '10', duration: 5, isTts: true, chimeBeforeStandardPriorityTTS: true]

    when:
    appScript.dispatchGroupAnnouncementClip(item, [coordinator, follower], false)

    then:
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    appScript.asyncCalls.findAll { Map call ->
      (new JsonSlurper().parseText(call.params.body as String) as Map).clipType == 'CHIME'
    }.size() == 2
  }

  def "player requests POST to Sonos and correlate status events by app ID and name"() {
    given:
    coordinator.currentValues.volume = 20
    appScript.playerPreferencesUpdated([deviceDni: coordinator.deviceNetworkId, preferences: [ttsBoostAmount: 5]])
    Map request = [channel: 'playerAppRequest', action: 'enqueueAudioClip',
      sourceDni: coordinator.deviceNetworkId, requestId: 'request-1',
      args: [uri: 'http://example.test/clip.mp3', volume: 10, duration: 4,
        correlationId: 'clip-correlation', priority: 'HIGH']]

    when:
    appScript.playerAppRequestHandler(request)
    Map post = appScript.asyncCalls.find { Map call -> call.cb == 'audioClipPostCallback' }
    Map body = new JsonSlurper().parseText(post.params.body as String) as Map

    then:
    post.params.uri == 'https://192.0.2.10:1443/api/v1/players/local/audioClip'
    post.params.headers['X-Sonos-Api-Key'] == '123e4567-e89b-12d3-a456-426655440000'
    body.appId == 'com.dwinks.sonos.advanced'
    body.name
    body.streamUrl == 'http://example.test/clip.mp3'
    body.priority == 'HIGH'
    body.volume == 15

    when:
    appScript.audioClipStatusHandler([deviceNetworkId: coordinator.deviceNetworkId,
      value: JsonOutput.toJson([appId: body.appId, name: body.name, id: 'sonos-id-1',
        priority: 'HIGH', clipType: 'CUSTOM', status: 'ACTIVE'])])

    then:
    appScript.appMemoryMap('audioClipQueuesByPlayer').RINCON_COORD.active.phase == 'ACTIVE'
    appScript.appMemoryMap('audioClipQueuesByPlayer').RINCON_COORD.active.sonosId == 'sonos-id-1'

    when:
    appScript.audioClipPostCallback(new AsyncResponse(status: 200,
      data: '{"_objectType":"audioClip","id":"sonos-id-1"}'), post.data as Map)
    appScript.audioClipStatusHandler([deviceNetworkId: coordinator.deviceNetworkId,
      value: JsonOutput.toJson([appId: body.appId, name: body.name, id: 'sonos-id-1',
        priority: 'HIGH', clipType: 'CUSTOM', status: 'DONE'])])

    then:
    appScript.appMemoryMap('audioClipQueuesByPlayer').RINCON_COORD.active == null
  }

  def "failed group player POST counts complete while available players finish by status"() {
    given:
    makeAnnouncementRosterExact()
    String groupCorrelationId = 'offline-member-group-clip'
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [
      active: [correlationId: groupCorrelationId, mode: 'AUDIO_CLIP',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW'], coordinatorId: 'RINCON_COORD'],
      pending: []]]
    appScript.enqueuePlayerAudioClipItem([correlationId: 'available-member',
      groupCorrelationId: groupCorrelationId, playerId: 'RINCON_COORD',
      uri: 'http://example.test/group.mp3', duration: 3, priority: 'REGULAR'])
    appScript.enqueuePlayerAudioClipItem([correlationId: 'offline-member',
      groupCorrelationId: groupCorrelationId, playerId: 'RINCON_FOLLOW',
      uri: 'http://example.test/group.mp3', duration: 3, priority: 'REGULAR'])
    Map coordinatorPost = appScript.asyncCalls.find { Map call -> call.data.playerId == 'RINCON_COORD' }
    Map followerPost = appScript.asyncCalls.find { Map call -> call.data.playerId == 'RINCON_FOLLOW' }
    Map coordinatorBody = new JsonSlurper().parseText(coordinatorPost.params.body as String) as Map

    when: 'the unavailable member times out or rejects its POST'
    appScript.audioClipPostCallback(new AsyncResponse(status: 503, data: '{}'), followerPost.data as Map)

    then: 'that member is terminal, but the group waits for the available player'
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.correlationId == groupCorrelationId
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.playerStatuses.RINCON_FOLLOW == 'DONE'
    !appScript.scheduled.any { List job -> job[1] == 'audioClipPlayerWatchdog' }

    when: 'the available member reports completion'
    appScript.audioClipStatusHandler([deviceNetworkId: coordinator.deviceNetworkId,
      value: JsonOutput.toJson([appId: coordinatorBody.appId, name: coordinatorBody.name,
        id: 'available-sonos-id', priority: 'REGULAR', clipType: 'CUSTOM', status: 'DONE'])])

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.correlationId == groupCorrelationId
    appScript.scheduled.any { List job -> job[1] == 'completeGroupAnnouncementAfterAudioClipGrace' && job[0] == 1000 }

    when:
    appScript.completeGroupAnnouncementAfterAudioClipGrace([groupDni: 'GROUP-DNI',
      correlationId: groupCorrelationId])

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active == null
  }

  def "app HIGH priority clips preempt and then resume regular work with a fresh name"() {
    given:
    appScript.enqueuePlayerAudioClipItem([correlationId: 'regular-A', playerId: 'RINCON_COORD',
      uri: 'http://example.test/regular.mp3', volume: 10, duration: 5,
      priority: 'REGULAR', origin: 'test'])
    Map regularPost = appScript.asyncCalls.last()
    Map regularBody = new JsonSlurper().parseText(regularPost.params.body as String) as Map

    when:
    appScript.enqueuePlayerAudioClipItem([correlationId: 'high-H', playerId: 'RINCON_COORD',
      uri: 'http://example.test/high.mp3', volume: 20, duration: 2,
      priority: 'HIGH', origin: 'test'])
    Map highPost = appScript.asyncCalls.last()
    Map highBody = new JsonSlurper().parseText(highPost.params.body as String) as Map

    then:
    highBody.priority == 'HIGH'
    appScript.appMemoryMap('audioClipQueuesByPlayer').RINCON_COORD.suspended.correlationId == 'regular-A'
    appScript.appMemoryMap('audioClipQueuesByPlayer').RINCON_COORD.active.correlationId == 'high-H'

    when:
    appScript.audioClipStatusHandler([deviceNetworkId: coordinator.deviceNetworkId,
      value: JsonOutput.toJson([appId: highBody.appId, name: highBody.name, id: 'sonos-high',
        priority: 'HIGH', clipType: 'CUSTOM', status: 'DONE'])])
    appScript.dispatchNextPlayerAudioClip('RINCON_COORD')
    Map resumedPost = appScript.asyncCalls.last()
    Map resumedBody = new JsonSlurper().parseText(resumedPost.params.body as String) as Map

    then:
    resumedBody.streamUrl == 'http://example.test/regular.mp3'
    resumedBody.name != regularBody.name
    appScript.appMemoryMap('audioClipQueuesByPlayer').RINCON_COORD.active.correlationId == 'regular-A'
  }

  def "successive queued TTS skips its group chime when suppression is enabled"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.suppressChimeOnSuccessiveTTS = true
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [
      active: [correlationId: 'active-tts', isTts: true], pending: []]]

    when:
    appScript.enqueueGroupAnnouncement(group, coordinator, [coordinator, follower],
      'playAudioClip', [uri: 'http://example.test/tts.mp3', isTts: true,
        chimeBeforeStandardPriorityTTS: true], 'successive-tts')

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].pending.last().chimeBeforeStandardPriorityTTS == false
    appScript.logs.any { it.contains('Suppressing chime for successive TTS announcement successive-tts') }
  }

  def "group audio requests are accepted then processed to every player"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.isGrouped = 'on'
    coordinator.currentValues.isGroupCoordinator = 'on'
    coordinator.currentValues.status = 'playing'
    Map groupRequest = request('playAudioClip', [uri: 'http://example.test/tts.mp3', volume: '15', duration: 2])

    when:
    appScript.acceptGroupCommandRequest(groupRequest)

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player -> player.commands.empty }
    appScript.scheduled.any { it[1] == 'processGroupCommandRequest' }

    when:
    runScheduledCallbacks()

    then:
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.command == 'playAudioClip'
  }

  def "idle group announcement starts one stream on its coordinator"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    makeAnnouncementRosterExact()
    coordinator.currentValues.status = 'stopped'
    coordinator.currentValues.transportStatus = 'stopped'
    Map groupRequest = request('playAudioClip', [uri: 'http://example.test/tts.mp3', volume: '15',
      duration: 42, correlationId: 'idle-announcement'])

    when:
    appScript.processGroupCommandRequest([request: groupRequest])

    then:
    coordinator.commands.find { it.name == 'playerPlayGroupAnnouncementStream' } == [
      name: 'playerPlayGroupAnnouncementStream', uri: 'http://example.test/tts.mp3',
      volume: 15G, correlationId: 'idle-announcement']
    !follower.commands.any { it.name == 'playerLoadAudioClipHighPriority' }
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.mode == 'COORDINATOR_STREAM'
  }

  def "automatic announcements use current attributes and never regroup a mismatched target roster"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.groupAnnouncementMode = 'automatic'
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'x-sonos-http:active-track'
    follower.dataValues.groupId = 'OTHER-GROUP'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    Map item = [correlationId: 'no-regroup-auto', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [active: item, pending: []]

    when:
    appScript.dispatchGroupAnnouncementByPolicy('GROUP-DNI', queue, item, [coordinator, follower], coordinator)

    then:
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    ![coordinator, follower, extra].any { SonosAppPlayerDouble player ->
      player.commands.any { it.name in ['playerCreateGroup', 'playerModifyGroupMembers', 'playerGetGroupsFull'] }
    }
  }

  def "always standard stream does not automatically change group membership"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    coordinator.currentValues.status = 'stopped'
    coordinator.currentValues.transportStatus = 'stopped'
    follower.dataValues.groupId = 'OTHER-GROUP'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    Map item = [correlationId: 'no-regroup-standard', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [active: item, pending: []]

    when:
    appScript.dispatchGroupAnnouncementByPolicy('GROUP-DNI', queue, item, [coordinator, follower], coordinator)

    then:
    coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    !follower.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    ![coordinator, follower, extra].any { SonosAppPlayerDouble player ->
      player.commands.any { it.name in ['playerCreateGroup', 'playerModifyGroupMembers', 'playerGetGroupsFull'] }
    }
  }

  def "queued announcement stays on coordinator stream after the previous restore reports completion"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    makeAnnouncementRosterExact()
    coordinator.currentValues.status = 'stopped'
    coordinator.currentValues.transportStatus = 'stopped'
    Map firstRequest = request('playAudioClip', [uri: 'http://example.test/first.mp3', volume: '15',
      duration: 42, correlationId: 'first-announcement'])
    appScript.processGroupCommandRequest([request: firstRequest])
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'http://example.test/first.mp3'
    Map queue = appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'] as Map
    queue.pending = [[correlationId: 'second-announcement', command: 'playAudioClip',
      uri: 'http://example.test/second.mp3', volume: '15', duration: 42,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]]

    when:
    appScript.handleGroupAnnouncementProgress(coordinator, [groupDni: 'GROUP-DNI',
      correlationId: 'first-announcement', phase: 'DONE'])
    appScript.handleGroupAnnouncementProgress(coordinator, [groupDni: 'GROUP-DNI',
      correlationId: 'first-announcement', phase: 'PLAYBACK_RESTORED', restored: restored,
      reason: restored ? null : 'set-uri-failed-500'])
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    coordinator.commands.findAll { it.name == 'playerPlayGroupAnnouncementStream' }*.uri == [
      'http://example.test/first.mp3', 'http://example.test/second.mp3']
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      !player.commands.any { it.name == 'playerLoadAudioClipHighPriority' }
    }
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.mode == 'COORDINATOR_STREAM'
    coordinator.commands.any { it.name == 'restoreGroupAnnouncementPlayback' && it.correlationId == 'first-announcement' }

    where:
    restored << [true, false]
  }

  def "expired queued announcements are discarded before dispatch"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.maximumTtsRetention = '60'
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [pending: [[
      correlationId: 'stale-announcement', command: 'playAudioClip',
      uri: 'http://example.test/stale.mp3', volume: '15', duration: 5,
      submittedAt: appScript.now() - (2L * 60L * 60L * 1000L),
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ]]]]

    when:
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    !appScript.appMemoryMap('groupAnnouncementQueues').containsKey('GROUP-DNI')
    appScript.logs.any { it.contains('Discarding expired queued announcement stale-announcement') }
  }

  def "announcement dispatch does not depend on lastUpdated offline polling"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    makeAnnouncementRosterExact()
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [pending: [[
      correlationId: 'announcement-without-offline-poll', command: 'playAudioClip',
      uri: 'http://example.test/announcement.mp3', volume: '15', duration: 5,
      submittedAt: appScript.now(), coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ]]]]

    when:
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.correlationId == 'announcement-without-offline-poll'
    coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
  }

  def "unlimited TTS retention disables expiration"() {
    when:
    appScript.settings.maximumTtsRetention = 'unlimited'

    then:
    appScript.getMaximumTtsRetentionMs() == null

    when:
    appScript.settings.maximumTtsRetention = '60'

    then:
    appScript.getMaximumTtsRetentionMs() == 3600000L
  }

  def "queued announcement uses audioClip when an external source replaces prior TTS"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'https://music.example.test/external.mp3'
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [ownStreamUri: 'http://example.test/first.mp3', pending: [[
      correlationId: 'external-source-announcement', command: 'playAudioClip',
      uri: 'http://example.test/next.mp3', volume: '15', duration: 42,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]]]]

    when:
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.mode == 'AUDIO_CLIP'
  }

  def "interrupted coordinator stream routes queued Automatic announcement to audioClip despite stale source state"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'http://example.test/first.mp3'
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [ownStreamUri: 'http://example.test/first.mp3',
      active: [correlationId: 'first', mode: 'COORDINATOR_STREAM', coordinatorId: 'RINCON_COORD',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']], pending: [[
          correlationId: 'next', command: 'playAudioClip', uri: 'http://example.test/next.mp3',
          volume: '15', duration: 42, coordinatorId: 'RINCON_COORD',
          playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]]]]

    when:
    appScript.handleGroupAnnouncementProgress(coordinator, [groupDni: 'GROUP-DNI',
      correlationId: 'first', phase: 'INTERRUPTED', externalPlayback: true,
      replacementUri: 'https://music.example.test/track.mp3'])
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    appScript.asyncCalls.findAll { Map call -> call.cb == 'audioClipPostCallback' }.size() == 2
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.mode == 'AUDIO_CLIP'
  }

  def "fresh topology reports satisfy group observations"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    Long observedAt = appScript.now() + 1000L

    when:
    appScript.processTopologyPayload(coordinator, [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW'], observedAt: observedAt,
      playerDni: coordinator.deviceNetworkId])
    Map topology = appScript.readGroupOperationTopology(appScript.getActiveGroupOperation())

    then:
    topology.fresh == true
    topology.consistent == true
    topology.authorityPlayerId == 'RINCON_COORD'
    topology.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW']
  }

  def "repeated subscription setup keeps one device event subscription per player"() {
    when:
    appScript.subscribeToGroupCommandRequests()
    appScript.subscribeToGroupCommandRequests()

    then:
    !appScript.subscriptions.any { it[0] in [coordinator, follower] &&
      it[1] in ['playerCommandRequest', 'groupTopologyOperation'] }
    !appScript.subscriptions.any { it[0] == group && it[1] in ['zgtInProgress', 'parentAppRequest'] }
    !appScript.subscriptions.any { it[1] == 'sonosGroupCommandRequest' }
    !appScript.subscriptions.any { it[1] in ['sonosPlayerStateUpdate', 'sonosPlayerAppRequest'] }
  }

  def "player topology attributes subscribe once per player"() {
    when:
    appScript.subscribeToGroupCommandRequests()
    appScript.subscribeToGroupCommandRequests()

    then:
    ['groupCoordinatorId', 'groupMemberCount', 'isGroupCoordinator', 'groupPlayerIds'].every { String attr ->
      appScript.subscriptions.count { it == [coordinator, attr, 'topologyAttributeHandler'] } == 1 &&
      appScript.subscriptions.count { it == [follower, attr, 'topologyAttributeHandler'] } == 1
    }
  }

  def "topology attribute handler rebuilds topology from attributes once per change"() {
    given:
    coordinator.currentValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.currentValues.groupPlayerIds = 'RINCON_FOLLOW,RINCON_COORD'
    coordinator.currentValues.groupId = 'GROUP-1'

    when:
    appScript.topologyAttributeHandler([device: coordinator, name: 'groupPlayerIds',
      value: 'RINCON_FOLLOW,RINCON_COORD'] as Event)

    then:
    appScript.atomicState.lastProcessedTopologyByDni[coordinator.deviceNetworkId] ==
      [coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    !appScript.scheduled.any { List job -> job[1] == 'zgtTopologyWatchdog' }

    when:
    appScript.scheduled.clear()
    appScript.topologyAttributeHandler([device: coordinator, name: 'isGroupCoordinator',
      value: 'on'] as Event)

    then:
    !appScript.scheduled.any { List job -> job[1] == 'zgtTopologyWatchdog' }
  }

  def "Favorite with full options dispatches directly without topology checks"() {
    when:
    appScript.processGroupCommandRequest([request: request('loadFavoriteFull', [
      favoriteId: '42', repeatMode: 'repeat all', queueMode: 'replace',
      shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ], 1)])

    then:
    coordinator.commands.find { it.name == 'loadFavoriteFull' } == [
      name: 'loadFavoriteFull', favoriteId: '42', repeatMode: 'repeat all',
      queueMode: 'replace', shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ]
    appScript.getActiveGroupOperation() == null
    [coordinator, follower, extra].every { SonosAppPlayerDouble player ->
      !player.commands.any { it.name in ['playerGetGroupsFull', 'playerCreateGroup', 'playerModifyGroupMembers'] }
    }
  }

  def "Favorite and playlist loads go straight to the current coordinator during topology operations"() {
    given:
    appScript.processGroupCommandRequest([request: request('on', [:], 1)])

    when:
    appScript.processGroupCommandRequest([request: request('loadFavoriteFull', [
      favoriteId: '42', repeatMode: 'repeat all', queueMode: 'replace',
      shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ], 2)])

    appScript.processGroupCommandRequest([request: request('loadPlaylistFull', [
      playlistId: '88', repeatMode: 'off', queueMode: 'replace',
      shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ], 3)])

    then:
    coordinator.commands.find { it.name == 'loadFavoriteFull' }.favoriteId == '42'
    coordinator.commands.find { it.name == 'loadPlaylistFull' }.playlistId == '88'
    appScript.atomicState.deferredGroupOperationCommands == null
  }

  def "group device state is exact in explicit mode and subset-based in additive mode"() {
    given:
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'])

    expect:
    appScript.appMemoryMap('pendingGroupStateUpdates')['GROUP-DNI'].switch == 'off'

    when:
    appScript.state.clear()
    appScript.storeGroupingContext([groupDni: 'GROUP-DNI', groupingMode: 'ADDITIVE',
      requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'], resolvedCoordinatorId: 'RINCON_COORD'])
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'])

    then:
    appScript.appMemoryMap('pendingGroupStateUpdates')['GROUP-DNI'].switch == 'on'
    appScript.appMemoryMap('pendingGroupStateUpdates')['GROUP-DNI'].currentlyJoinedPlayers.contains('RINCON_EXTRA')
  }

  def "track position preference is read from the app preference snapshot instead of device data"() {
    given:
    appScript.playerPreferencesUpdated([deviceDni: coordinator.deviceNetworkId, preferences: [enableTrackPositionUpdates: true]])
    coordinator.dataValues.enableTrackPositionUpdates = 'false'

    expect:
    appScript.isPlayerTrackPositionUpdatesEnabled(coordinator)
  }

  def "group membership does not infer a missing coordinator from the callback"() {
    when:
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_FOLLOW'])

    then:
    appScript.appMemoryMap('pendingGroupStateUpdates')['GROUP-DNI'].switch == 'off'
  }

  def "group membership fanout yields after its fixed group budget"() {
    given:
    List<SonosAppGroupDouble> additional = (0..<10).collect { Integer index ->
      SonosAppGroupDouble child = new SonosAppGroupDouble(deviceNetworkId: "EXTRA-GROUP-${index}")
      child.dataValues.groupCoordinatorId = 'RINCON_COORD'
      child.dataValues.playerIds = 'RINCON_FOLLOW'
      child
    }
    appScript.children.addAll(additional)

    when:
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW'])

    then:
    appScript.appMemoryMap('pendingGroupStateUpdates').size() == 8
    appScript.scheduled.any { List job -> job[1] == 'continueGroupDeviceMembershipUpdate' }

    when:
    List job = appScript.scheduled.find { List item -> item[1] == 'continueGroupDeviceMembershipUpdate' }
    appScript.continueGroupDeviceMembershipUpdate(job[2].data as Map)

    then:
    appScript.appMemoryMap('pendingGroupStateUpdates').size() == 11
  }

  def "parent app emits player and group attributes with child sendEvent"() {
    when:
    appScript.sendChildAttributeEvents(coordinator, [transportStatus: 'playing'])
    appScript.queueGroupDeviceState('GROUP-DNI', [
      switch: 'on', currentlyJoinedPlayers: 'Kitchen, Kids Room',
      currentTrackName: 'B.Y.O.B.', currentArtistName: 'System Of A Down',
      currentAlbumName: 'Mezmerize', trackDescription: 'No More Tears by Ozzy Osbourne',
      currentTrackDuration: '0:04:16', currentTrackNumber: 3, trackData: '{}'
    ])
    appScript.drainGroupStateUpdates()

    then:
    coordinator.emittedAttributes().transportStatus == 'playing'
    group.emittedAttributes().switch == 'on'
    group.emittedAttributes().currentlyJoinedPlayers == 'Kitchen, Kids Room'
    group.emittedAttributes().currentTrackName == 'B.Y.O.B.'
    group.emittedAttributes().currentArtistName == 'System Of A Down'
    group.emittedAttributes().trackDescription == 'No More Tears by Ozzy Osbourne'
    group.emittedAttributes().currentTrackNumber == 3
    group.emittedAttributes().trackData == '{}'
    coordinator.refreshedPlaybackUpdates.isEmpty()
    group.refreshedPlaybackUpdates.isEmpty()
  }

  def "group state drain yields after its fixed group budget"() {
    given:
    Map<String, Map<String, Object>> pending = [:]
    (0..<10).each { Integer index -> pending["GROUP-${index}"] = [status: 'playing'] }
    appScript.appMemory()['pendingGroupStateUpdates'] = pending
    (0..<10).each { Integer index ->
      appScript.children << new SonosAppGroupDouble(deviceNetworkId: "GROUP-${index}")
    }

    when:
    appScript.drainGroupStateUpdates()

    then:
    appScript.appMemoryMap('pendingGroupStateUpdates').size() == 2
    appScript.scheduled.any { List job -> job[1] == 'drainGroupStateUpdates' }

    when:
    appScript.drainGroupStateUpdates()

    then:
    !appScript.state.containsKey('pendingGroupStateUpdates')
  }

  def "group state enqueue rearms drain when scheduled marker is stale"() {
    given:
    appScript.state.groupStateDrainScheduled = true
    appScript.appMemory()['pendingGroupStateUpdates'] = ['GROUP-DNI': [switch: 'on']]

    when:
    appScript.queueGroupDeviceState('GROUP-DNI', [currentlyJoinedPlayers: 'Kitchen, Kids Room'])

    then:
    appScript.scheduled.any { List job -> job[1] == 'drainGroupStateUpdates' }
  }

  def "ungrouped group refresh reflects only the coordinator when individual control is disabled"() {
    given:
    group.currentValues.switch = 'on'
    coordinator.currentValues.isGrouped = 'off'
    coordinator.currentValues.isGroupCoordinator = 'on'
    coordinator.currentValues.volume = 10
    coordinator.currentValues.mute = 'muted'
    follower.currentValues.volume = 30
    follower.currentValues.mute = 'unmuted'

    when:
    appScript.refreshGroupDeviceState(group, coordinator, [coordinator, follower], [
      useProportionalVolume: true, controlUngroupedIndividually: false
    ])
    appScript.drainGroupStateUpdates()
    Map attributes = group.emittedAttributes()

    then:
    attributes.volume == 10
    attributes.groupVolume == 10
    attributes.mute == 'muted'
    attributes.groupMute == 'muted'
  }

  // ---------------------------------------------------------------------------
  // Calculated group volume and mute
  // ---------------------------------------------------------------------------

  private void putTopology(Map<String, String> groups) {
    // groups: coordinatorId -> comma-separated member ids
    Map<String, List<Map>> byGroup = [:]
    groups.each { String coordinatorId, String members ->
      byGroup["GROUP-${coordinatorId}".toString()] = [
        [name: 'groupCoordinatorId', value: coordinatorId],
        [name: 'groupPlayerIds', value: members]
      ]
    }
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: byGroup])
    runScheduledCallbacks(['processUpnpNotificationCallback'])
  }

  private void groupedTopology() {
    putTopology(['RINCON_COORD': 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA'])
  }

  private void ungroupedTopology() {
    putTopology(['RINCON_COORD': 'RINCON_COORD', 'RINCON_FOLLOW': 'RINCON_FOLLOW', 'RINCON_EXTRA': 'RINCON_EXTRA'])
  }

  private void report(String rincon, Integer volume, String mute = null) {
    Map args = [:]
    if(volume != null) { args.volume = volume }
    if(mute != null) { args.mute = mute }
    appScript.playerAppRequest([version: 1, channel: 'playerAppRequest', action: 'playerReading',
      sourceDni: "DNI-${rincon}".toString(), requestId: UUID.randomUUID().toString(), args: args])
  }

  /** A coordinator's GroupRenderingControl report relayed by its player driver. */
  private void reportGrc(String rincon, Integer volume, String mute = null) {
    Map args = [:]
    if(volume != null) { args.groupVolume = volume }
    if(mute != null) { args.groupMute = mute }
    appScript.playerAppRequest([version: 1, channel: 'playerAppRequest', action: 'groupRendering',
      sourceDni: "DNI-${rincon}".toString(), requestId: UUID.randomUUID().toString(), args: args])
    runScheduledCallbacks(['publishGroupRenderingCallback'])
    appScript.drainGroupStateUpdates()
  }

  private void publishPreferences(Boolean proportional, Boolean individually) {
    appScript.groupPreferencesUpdated([deviceDni: 'GROUP-DNI',
      preferences: [useProportionalVolume: proportional, controlUngroupedIndividually: individually]])
  }

  /** Apply queued recalculation and batched group state, returning what the group device emitted. */
  private Map settle() {
    group.events.clear()
    runScheduledCallbacks()
    appScript.drainGroupStateUpdates()
    return group.emittedAttributes()
  }

  private void reportStandardVolumes() {
    report('RINCON_COORD', 10, 'muted')
    report('RINCON_FOLLOW', 21, 'muted')
    report('RINCON_EXTRA', 40, 'unmuted')
  }

  def "group device uses #expectedPlayers when #topology and proportional=#proportional individually=#individually"() {
    given:
    if(topology == 'grouped') { groupedTopology() } else { ungroupedTopology() }
    publishPreferences(proportional, individually)
    reportStandardVolumes()
    if(topology == 'grouped') { reportGrc('RINCON_COORD', 24, 'unmuted') }

    when:
    Map attributes = settle()

    then:
    attributes.volume == expectedVolume
    attributes.groupVolume == expectedVolume
    attributes.mute == expectedMute
    attributes.groupMute == expectedMute

    where:
    topology    | proportional | individually || expectedPlayers                    | expectedVolume | expectedMute
    'grouped'   | true         | false        || 'live Sonos group members'         | 24             | 'unmuted'
    'grouped'   | true         | true         || 'live Sonos group members'         | 24             | 'unmuted'
    'grouped'   | false        | false        || 'configured players'              | 16             | 'muted'
    'grouped'   | false        | true         || 'configured players'              | 16             | 'muted'
    'ungrouped' | true         | true         || 'configured players'              | 16             | 'muted'
    'ungrouped' | false        | true         || 'configured players'              | 16             | 'muted'
    'ungrouped' | true         | false        || 'the coordinator only'            | 10             | 'muted'
    'ungrouped' | false        | false        || 'the coordinator only'            | 10             | 'muted'
  }

  def "player group attributes come from the coordinator's GRC and reach every member"() {
    given:
    groupedTopology()
    publishPreferences(false, false)
    coordinator.events.clear(); follower.events.clear(); extra.events.clear()

    when:
    reportGrc('RINCON_COORD', 24, 'unmuted')

    then:
    ['COORD', 'FOLLOW', 'EXTRA'].every { String id ->
      childEventsFor("DNI-RINCON_${id}".toString()).any { Map e -> e.name == 'groupVolume' && e.value == 24 } &&
      childEventsFor("DNI-RINCON_${id}".toString()).any { Map e -> e.name == 'groupMute' && e.value == 'unmuted' } }

    when: 'a follower reports GRC it should not receive'
    appScript.childEvents.clear()
    reportGrc('RINCON_FOLLOW', 99, 'muted')

    then: 'the coordinator remains authoritative'
    appScript.childEvents.isEmpty()
    appScript.appMemoryMap('groupRenderingByCoordinator').keySet() == ['RINCON_COORD'] as Set
  }

  def "a player that joins a group receives the coordinator's cached GRC"() {
    given:
    ungroupedTopology()
    reportGrc('RINCON_COORD', 35, 'unmuted')
    appScript.childEvents.clear()

    when:
    groupedTopology()
    settle()

    then:
    childEventsFor('DNI-RINCON_FOLLOW').any { Map e -> e.name == 'groupVolume' && e.value == 35 }
    childEventsFor('DNI-RINCON_FOLLOW').any { Map e -> e.name == 'groupMute' && e.value == 'unmuted' }
  }

  def "group devices on a live group with unmanaged speakers publish the coordinator's GRC"() {
    given:
    putTopology(['RINCON_COORD': 'RINCON_COORD,RINCON_FOLLOW,RINCON_UNMANAGED'])
    publishPreferences(true, false)
    report('RINCON_COORD', 20, 'muted')
    report('RINCON_FOLLOW', 20, 'muted')

    when: 'the unmanaged speaker raised the group volume to 40'
    reportGrc('RINCON_COORD', 40, 'unmuted')
    runScheduledCallbacks()
    appScript.drainGroupStateUpdates()

    then:
    group.emittedAttributes().groupVolume == 40
    group.emittedAttributes().groupMute == 'unmuted'
  }

  def "group volume commands carry settings that seed the cache without calling the group device"() {
    given:
    group.reads.clear()

    when:
    appScript.groupAppRequest([version: 1, channel: 'groupAppRequest', groupDni: 'GROUP-DNI',
      requestId: 'seed-1', sequence: 1, command: 'volumeUp',
      args: [useProportionalVolume: false, controlUngroupedIndividually: true]])

    then:
    appScript.atomicState.groupPreferencesByDni['GROUP-DNI'].useProportionalVolume == false
    appScript.atomicState.groupPreferencesByDni['GROUP-DNI'].controlUngroupedIndividually == true
    appScript.scheduled.any { List job -> job[1] == 'recalculateVolumeStateCallback' && job[2].data.groups == ['GROUP-DNI'] }
    !appScript.metaClass.respondsTo(appScript, 'seedGroupPreferenceSnapshots')
    group.reads.isEmpty()
  }

  def "group volume rounds the member average half up"() {
    given:
    groupedTopology()
    publishPreferences(true, false)

    expect:
    appScript.calculateVolumeState(['A', 'B'], [A: [volume: 10], B: [volume: 11]]).volume == 11
    appScript.calculateVolumeState(['A', 'B'], [A: [volume: 10], B: [volume: 15]]).volume == 13
    appScript.calculateVolumeState(['A', 'B', 'C'], [A: [volume: 10], B: [volume: 10], C: [volume: 11]]).volume == 10
    appScript.calculateVolumeState(['A', 'B', 'C'], [A: [volume: 0], B: [volume: 0], C: [volume: 100]]).volume == 33
  }

  def "mute is reported only when every selected player is muted"() {
    expect:
    appScript.calculateVolumeState(['A', 'B'], [A: [mute: 'muted'], B: [mute: 'muted']]).mute == 'muted'
    appScript.calculateVolumeState(['A', 'B'], [A: [mute: 'muted'], B: [mute: 'unmuted']]).mute == 'unmuted'
    appScript.calculateVolumeState(['A', 'B'], [A: [mute: 'muted']]).mute == 'unmuted'
    appScript.calculateVolumeState(['A'], [A: [mute: 'unmuted']]).mute == 'unmuted'
  }

  def "players without valid readings are skipped and no volume is published without any"() {
    given:
    groupedTopology()
    publishPreferences(false, false)

    when: 'nothing has reported'
    Map attributes = settle()

    then:
    !attributes.containsKey('volume')
    !attributes.containsKey('groupVolume')
    !attributes.containsKey('mute')

    when: 'one player reports and the rest are missing or invalid'
    report('RINCON_COORD', 30, 'unmuted')
    report('RINCON_FOLLOW', 150)
    appScript.playerAppRequest([version: 1, channel: 'playerAppRequest', action: 'playerReading',
      sourceDni: 'DNI-RINCON_EXTRA', args: [volume: 'loud', mute: 'maybe']])
    attributes = settle()

    then:
    attributes.volume == 30
    attributes.groupVolume == 30
    attributes.mute == 'unmuted'
    !appScript.appMemoryMap('playerReadingsByRincon').containsKey('RINCON_EXTRA')
    appScript.appMemoryMap('playerReadingsByRincon').RINCON_FOLLOW == null
  }

  def "changing a group setting recalculates the group device from cached readings"() {
    given:
    groupedTopology()
    publishPreferences(true, false)
    reportStandardVolumes()
    settle()

    when:
    publishPreferences(false, false)

    then:
    appScript.atomicState.groupPreferencesByDni['GROUP-DNI'].useProportionalVolume == false
    appScript.atomicState.groupPreferencesByDni['GROUP-DNI'].controlUngroupedIndividually == false
    appScript.scheduled.any { List job -> job[1] == 'recalculateVolumeStateCallback' && job[2].data.groups == ['GROUP-DNI'] }

    when:
    Map attributes = settle()

    then:
    attributes.volume == 16
    attributes.mute == 'muted'
  }

  def "an unchanged group preference snapshot does not schedule a recalculation"() {
    given:
    publishPreferences(false, true)
    runScheduledCallbacks()
    appScript.scheduled.clear()

    when:
    publishPreferences(false, true)

    then:
    !appScript.scheduled.any { List job -> job[1] == 'recalculateVolumeStateCallback' }
  }

  def "group preference snapshots from unknown groups are ignored"() {
    when:
    appScript.groupPreferencesUpdated([deviceDni: 'OTHER-GROUP', preferences: [useProportionalVolume: false]])

    then:
    !(appScript.atomicState.groupPreferencesByDni ?: [:]).containsKey('OTHER-GROUP')
    !appScript.state.containsKey('pendingVolumeRecalculation')
  }

  def "a missing preference snapshot falls back to proportional grouped control"() {
    given:
    groupedTopology()
    reportStandardVolumes()
    reportGrc('RINCON_COORD', 24, 'unmuted')

    when:
    Map attributes = settle()

    then:
    !appScript.state.containsKey('groupPreferencesByDni')
    attributes.volume == 24
  }

  def "readings before topology are replaced once the group and its GRC are known"() {
    given:
    publishPreferences(true, false)

    when: 'readings arrive before topology'
    reportStandardVolumes()
    Map beforeTopology = settle()
    groupedTopology()
    reportGrc('RINCON_COORD', 24, 'unmuted')
    Map afterTopology = settle()

    then: 'ungrouped coordinator-only value is replaced by the live group value'
    beforeTopology.volume == 10
    afterTopology.volume == 24
    afterTopology.mute == 'unmuted'
  }

  def "GRC that arrives before topology is held and applied once the coordinator is known"() {
    given:
    publishPreferences(true, false)

    when: 'the initial snapshot beats topology'
    reportGrc('RINCON_COORD', 50, 'muted')

    then:
    !appScript.appMemoryMap('groupRenderingByCoordinator').containsKey('RINCON_COORD')
    appScript.appMemoryMap('pendingGroupRenderingBySource').RINCON_COORD == [groupVolume: 50, groupMute: 'muted']

    when:
    appScript.childEvents.clear()
    groupedTopology()
    settle()

    then:
    appScript.appMemoryMap('groupRenderingByCoordinator').RINCON_COORD == [groupVolume: 50, groupMute: 'muted']
    appScript.appMemoryMap('pendingGroupRenderingBySource').isEmpty()
    ['COORD', 'FOLLOW', 'EXTRA'].every { String id ->
      childEventsFor("DNI-RINCON_${id}".toString()).any { Map e -> e.name == 'groupVolume' && e.value == 50 } }
    group.emittedAttributes().groupVolume == 50
  }

  def "held GRC from a source that turns out to be a follower is dropped"() {
    when:
    reportGrc('RINCON_FOLLOW', 70, 'unmuted')
    groupedTopology()
    settle()

    then:
    appScript.appMemoryMap('pendingGroupRenderingBySource').isEmpty()
    appScript.appMemoryMap('groupRenderingByCoordinator').isEmpty()
  }

  def "regrouping replaces stale derived values and ZGT repeats with the same roster do nothing"() {
    given:
    publishPreferences(true, false)
    groupedTopology()
    reportStandardVolumes()
    settle()
    coordinator.events.clear()

    when: 'the same topology is reported again'
    groupedTopology()

    then:
    !appScript.scheduled.any { List job -> job[1] == 'recalculateVolumeStateCallback' }

    when: 'the extra player leaves the group'
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: [
      'GROUP-RINCON_COORD': [[name: 'groupCoordinatorId', value: 'RINCON_COORD'], [name: 'groupPlayerIds', value: 'RINCON_COORD,RINCON_FOLLOW']],
      'GROUP-RINCON_EXTRA': [[name: 'groupCoordinatorId', value: 'RINCON_EXTRA'], [name: 'groupPlayerIds', value: 'RINCON_EXTRA']]]])
    runScheduledCallbacks(['processUpnpNotificationCallback'])

    then:
    appScript.scheduled.any { List job -> job[1] == 'recalculateVolumeStateCallback' && job[2].data.all == true }

    when: 'each coordinator reports its new group state'
    settle()
    reportGrc('RINCON_COORD', 16, 'muted')
    reportGrc('RINCON_EXTRA', 40, 'unmuted')

    then:
    group.emittedAttributes().volume == 16
    group.emittedAttributes().mute == 'muted'
    childEventsFor('DNI-RINCON_COORD').any { Map e -> e.name == 'groupVolume' && e.value == 16 }
    childEventsFor('DNI-RINCON_EXTRA').any { Map e -> e.name == 'groupVolume' && e.value == 40 }
  }

  def "only affected group devices are republished when an unrelated player reports"() {
    given:
    publishPreferences(true, false)
    putTopology(['RINCON_COORD': 'RINCON_COORD,RINCON_FOLLOW', 'RINCON_EXTRA': 'RINCON_EXTRA'])
    report('RINCON_COORD', 10, 'unmuted')
    report('RINCON_FOLLOW', 20, 'unmuted')
    settle()

    when:
    reportGrc('RINCON_EXTRA', 80, 'muted')
    Map attributes = settle()

    then:
    attributes.isEmpty()
    childEventsFor('DNI-RINCON_EXTRA').any { Map e -> e.name == 'groupVolume' && e.value == 80 }
  }

  def "player volume reports do not read child devices or group settings on the event path"() {
    given:
    groupedTopology()
    publishPreferences(false, false)
    [coordinator, follower, extra].each { it.reads.clear() }
    group.reads.clear()

    when:
    report('RINCON_COORD', 10, 'unmuted')
    report('RINCON_FOLLOW', 20, 'unmuted')
    settle()

    then:
    [coordinator, follower, extra, group].every { it.reads.isEmpty() }
    group.emittedAttributes().volume == 15
  }

  def "refresh seeds missing readings and uses the settings carried by the request"() {
    given:
    groupedTopology()
    coordinator.currentValues.volume = 10
    coordinator.currentValues.mute = 'unmuted'
    follower.currentValues.volume = 30
    follower.currentValues.mute = 'unmuted'
    extra.currentValues.volume = 50
    extra.currentValues.mute = 'unmuted'
    group.currentValues.switch = 'on'
    group.events.clear()

    when:
    appScript.refreshGroupDeviceState(group, coordinator, [coordinator, follower], [
      useProportionalVolume: false, controlUngroupedIndividually: false
    ])
    appScript.drainGroupStateUpdates()

    then:
    appScript.atomicState.groupPreferencesByDni['GROUP-DNI'].useProportionalVolume == false
    group.emittedAttributes().volume == 20
    group.emittedAttributes().groupVolume == 20
  }

  def "group volume commands still use the existing coordinator group API"() {
    given:
    groupedTopology()
    coordinator.currentValues.isGrouped = 'on'
    coordinator.currentValues.isGroupCoordinator = 'on'

    when:
    appScript.executeGroupVolumeCommand(coordinator, [coordinator, follower], [level: 35,
      useProportionalVolume: true, controlUngroupedIndividually: false])

    then:
    coordinator.commands.any { Map command -> command.name == 'setGroupVolume' }
    !follower.commands.any { Map command -> command.name == 'setLevel' }
  }

  def "parent page is a coordination shell without player or group tables"() {
    given:
    appScript.settings.putAll([autoCheckUpdates: false, autoInstallUpdates: false])
    appScript.binding.setVariable('autoCheckUpdates', false)
    appScript.binding.setVariable('autoInstallUpdates', false)

    when:
    Map page = appScript.mainPage()

    then:
    page.title == 'Sonos Advanced Controller'
    appScript.app.childApps.size() == 3
    appScript.pageHrefs.any { List args ->
      args[0] instanceof Map && args[0].url == '/installedapp/configure/134/discoveryPage'
    }
    !appScript.metaClass.respondsTo(appScript, 'renderNewUiSpeakerTableMarkup')
    !appScript.metaClass.respondsTo(appScript, 'renderNewUiGroupTableMarkup')
  }

  def "main page creates one child on first open and opens it directly thereafter"() {
    given:
    appScript.settings.putAll([autoCheckUpdates: false, autoInstallUpdates: false])
    appScript.binding.setVariable('autoCheckUpdates', false)
    appScript.binding.setVariable('autoInstallUpdates', false)

    when:
    Map page = appScript.mainPage()
    Map reopenedPage = appScript.mainPage()

    then:
    page.title == 'Sonos Advanced Controller'
    reopenedPage.title == 'Sonos Advanced Controller'
    appScript.app.childApps.size() == 3
    appScript.pageHrefs.count { List args -> args[0] instanceof Map && args[0].url == '/installedapp/configure/134/discoveryPage' } == 2
  }

  def "catalog child apps are created unconditionally with their exact names and linked from the main page"() {
    given:
    appScript.settings.putAll([autoCheckUpdates: false, autoInstallUpdates: false])
    appScript.binding.setVariable('autoCheckUpdates', false)
    appScript.binding.setVariable('autoInstallUpdates', false)

    when:
    appScript.mainPage()
    appScript.mainPage()

    then:
    appScript.app.childApps*.name.sort() == ['Sonos Advanced Controller - Discovery & Management',
      'Sonos Advanced Controller - Favorites', 'Sonos Advanced Controller - Playlists']
    appScript.app.childApps.findAll { it.name.endsWith(' - Favorites') || it.name.endsWith(' - Playlists') }.every { it.label == it.name }
    appScript.pageHrefs.any { List args -> args[0] instanceof Map && args[0].url?.endsWith('/mainPage') && args[0].title == 'Sonos Favorites' }
    appScript.pageHrefs.any { List args -> args[0] instanceof Map && args[0].url?.endsWith('/mainPage') && args[0].title == 'Sonos Playlists' }
  }

  def "existing catalog children with the old short labels are relabeled to their full names"() {
    given:
    appScript.app.childApps << new dwinks.hubitat.stubs.MockInstalledApp(id: 960L, name: 'Sonos Advanced Controller - Favorites', label: 'Sonos Favorites')

    when:
    appScript.ensureCatalogChild('Sonos Advanced Controller - Favorites')

    then:
    appScript.app.childApps.find { it.id == '960' || it.name.endsWith('- Favorites') }.label == 'Sonos Advanced Controller - Favorites'
  }

  def "configure clears the retired daily refresh instead of scheduling it"() {
    given:
    appScript.app.metaClass.getName = { -> 'Sonos Advanced Controller' }

    when:
    appScript.configure(false)

    then:
    appScript.unschedules.contains('refreshFavoritesAndPlaylists')
    !appScript.scheduled.any { List job -> job[1] == 'refreshFavoritesAndPlaylists' }
    !appScript.metaClass.respondsTo(appScript, 'refreshFavoritesAndPlaylists')
    appScript.app.childApps*.name.containsAll(['Sonos Advanced Controller - Favorites', 'Sonos Advanced Controller - Playlists'])
  }

  def "catalog snapshots are delivered to the matching child app from a new execution"() {
    given:
    List<Map> delivered = []
    Map namedChildren = [:]
    ['Sonos Advanced Controller - Favorites', 'Sonos Advanced Controller - Playlists'].each { String childName ->
      def child = new dwinks.hubitat.stubs.MockInstalledApp(id: 900L + namedChildren.size(), name: childName)
      child.metaClass.storeCatalogSnapshot = { List<Map> records, String dni = null -> delivered << [child: childName, records: records, dni: dni] }
      namedChildren[childName] = child
      appScript.app.childApps << child
    }

    when:
    appScript.putCatalogSnapshot('PLAYER-1', 'playlists', [[id: '7', name: 'Mix', imageUrl: '']])

    then:
    delivered.isEmpty()
    appScript.scheduled.any { List job -> job[1] == 'deliverCatalogSnapshot' && job[2].data.catalogType == 'playlists' }

    when:
    runScheduledCallbacks(['deliverCatalogSnapshot'])

    then:
    delivered == [[child: 'Sonos Advanced Controller - Playlists', records: [[id: '7', name: 'Mix', imageUrl: '']], dni: 'PLAYER-1']]
  }

  def "a catalog snapshot stays in memory when the child app cannot accept it and is re-delivered on demand"() {
    given:
    List<Map> delivered = []
    def child = new dwinks.hubitat.stubs.MockInstalledApp(id: 950L, name: 'Sonos Advanced Controller - Playlists')
    int attempts = 0
    child.metaClass.storeCatalogSnapshot = { List<Map> records, String dni = null ->
      attempts++
      if(attempts == 1) { throw new IllegalStateException('child busy') }
      delivered << [records: records]
    }
    appScript.app.childApps << child
    appScript.putCatalogSnapshot('PLAYER-1', 'playlists', [[id: '7', name: 'Mix', imageUrl: '']])

    when:
    runScheduledCallbacks(['deliverCatalogSnapshot'])

    then:
    appScript.memoryMapCopy('catalogSnapshots').playlists.records*.id == ['7']
    !appScript.scheduled.any { List job -> job[1] == 'deliverCatalogSnapshot' }

    when:
    appScript.deliverRetainedCatalogSnapshots()
    runScheduledCallbacks(['deliverCatalogSnapshot'])

    then:
    delivered*.records == [[[id: '7', name: 'Mix', imageUrl: '']]]
  }

  def "management snapshot exposes detached player and group rows without a parent table cache"() {
    given:
    coordinator.dataValues.secondaryIds = 'RINCON_SUB'
    coordinator.dataValues.secondaryDeviceIps = '192.0.2.12'
    group.deviceNetworkId = "${appScript.app.id}-SonosGroupDevice-Family"
    group.dataValues.groupCoordinatorId = 'RINCON_COORD'
    group.dataValues.playerIds = 'RINCON_FOLLOW'

    when:
    Map snapshot = appScript.getProvisioningSnapshot()

    then:
    snapshot.playerRows.any { Map row -> row.id == 'RINCON_COORD' && row.secondaries[0].id == 'RINCON_SUB' }
    snapshot.groups.any { Map row -> row.name == 'Family' && row.followerIds == ['RINCON_FOLLOW'] }
    !appScript.state.containsKey('playerTableRows')
    !appScript.state.containsKey('groupTableRows')
  }

  def "legacy group configurations are exposed for child migration and pruned after acknowledgement"() {
    given:
    appScript.state.userGroups = [Family: [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]]

    when:
    Map migration = appScript.getLegacyDiscoveryState()
    appScript.acknowledgeLegacyDiscoveryState()

    then:
    migration.groupConfigurations.Family.groupCoordinatorId == 'RINCON_COORD'
    !appScript.state.containsKey('userGroups')
  }

  def "group configuration changes queue on the parent for deferred device coordination"() {
    when:
    Map result = appScript.submitGroupManagementRequest([schemaVersion: 1, requestId: 'group-1',
      action: 'upsert', name: 'Family', groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']])

    then:
    result.accepted == true
    appScript.app.getChildDevice("${appScript.app.id}-SonosGroupDevice-Family") == null
    appScript.state.pendingGroupConfigurationRequests['group-1'].name == 'Family'
    appScript.scheduled.any { List item -> item[1] == 'drainGroupConfigurationRequests' }
  }

  def "main page renders cached installed version without changing the async update result"() {
    given:
    appScript.state.putAll([updateAvailable: true, latestVersion: '99.0.0',
      installedAppVersion: '98.0.0', latestReleaseDate: '2026-11-01', latestReleaseUrl: 'https://example.com/release'])
    appScript.settings.putAll([autoCheckUpdates: false, autoInstallUpdates: false, useOldUi: false])
    appScript.binding.setVariable('autoCheckUpdates', false)
    appScript.binding.setVariable('autoInstallUpdates', false)

    when:
    Map page = appScript.mainPage()
    String noticeMarkup = appScript.renderNewUiUpdateNoticeMarkup()

    then:
    page.title == 'Sonos Advanced Controller'
    appScript.state.updateAvailable == true
    appScript.state.installedAppVersion == '98.0.0'
    appScript.asyncCalls.empty
    noticeMarkup.contains('settings[btnInstallUpdate]')
    noticeMarkup.contains('settings[btnDismissUpdate]')
  }

  def "runtime app does not retain discovery cache keys"() {
    expect:
    !appScript.state.containsKey('discoveredSonoses')
    !appScript.state.containsKey('discoveredSonosSecondaries')
    !appScript.atomicState.containsKey('discoveryRunning')
    !appScript.atomicState.containsKey('discoveryProbeAttempts')
  }

  def "audio clip group completion waits one second after the first terminal player callback"() {
    given:
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [pending: [], active: [
      correlationId: 'muted-member-announcement', mode: 'AUDIO_CLIP',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      playerStatuses: [RINCON_COORD: 'DONE']
    ]]]

    when:
    appScript.handleGroupAnnouncementProgress(follower, [groupDni: 'GROUP-DNI',
      correlationId: 'muted-member-announcement', phase: 'DISMISSED', reason: 'muted'])

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active.correlationId == 'muted-member-announcement'
    appScript.scheduled.any { List job -> job[1] == 'completeGroupAnnouncementAfterAudioClipGrace' && job[0] == 1000 }
    !appScript.logs.any { String entry -> entry.contains('finished (DONE)') }

    when:
    appScript.completeGroupAnnouncementAfterAudioClipGrace([groupDni: 'GROUP-DNI',
      correlationId: 'muted-member-announcement'])

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active == null
    appScript.logs.any { String entry -> entry.contains('finished (DONE)') }
    !appScript.logs.any { String entry -> entry.contains('timed out') }
  }

  def "audio clip group completion advances after grace when another player never reports done"() {
    given:
    appScript.appMemory()['groupAnnouncementQueues'] = ['GROUP-DNI': [pending: [], active: [
      correlationId: 'missing-callback-announcement', mode: 'AUDIO_CLIP',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      playerStatuses: [:]
    ]]]

    when:
    appScript.handleGroupAnnouncementProgress(coordinator, [groupDni: 'GROUP-DNI',
      correlationId: 'missing-callback-announcement', phase: 'DONE'])
    appScript.completeGroupAnnouncementAfterAudioClipGrace([groupDni: 'GROUP-DNI',
      correlationId: 'missing-callback-announcement'])

    then:
    appScript.appMemoryMap('groupAnnouncementQueues')['GROUP-DNI'].active == null
    appScript.logs.any { String entry -> entry.contains('completion grace expired') && entry.contains('RINCON_FOLLOW') }
    appScript.logs.any { String entry -> entry.contains('finished (DONE)') }
  }

  def "player provisioning request is validated, queued, and deferred by the parent"() {
    given:
    Map player = [id: 'RINCON_NEW', name: 'Bedroom', modelName: 'One', deviceIp: '192.168.1.12']

    when:
    Map result = appScript.submitPlayerProvisioningRequest([schemaVersion: 1, requestId: 'request-1',
      selected: ['AA:BB:CC:DD:EE:03'], players: ['AA:BB:CC:DD:EE:03': player], secondaries: [:]])

    then:
    result.accepted == true
    appScript.app.getChildDevice('AA:BB:CC:DD:EE:03') == null
    appScript.state.pendingPlayerProvisioningRequests['request-1'].selected == ['AA:BB:CC:DD:EE:03']
    appScript.scheduled.any { List item -> item[1] == 'drainPlayerProvisioningRequests' }
  }

  def "player arrived and departed stubs log without persisting state"() {
    given:
    Map stateBefore = new LinkedHashMap(appScript.state)

    when:
    appScript.playerArrived('RINCON_542A1B66F5B201400', '542A1B66F5B2', 'Kitchen')
    appScript.playerDeparted('RINCON_542A1B66F5B201400', '542A1B66F5B2', 'Kitchen')

    then:
    appScript.logs.any { String entry -> entry.contains('Sonos player arrived: Kitchen (RINCON_542A1B66F5B201400, 542A1B66F5B2)') }
    appScript.logs.any { String entry -> entry.contains('Sonos player departed: Kitchen (RINCON_542A1B66F5B201400, 542A1B66F5B2)') }
    appScript.state == stateBefore
    coordinator.events.isEmpty()
    follower.events.isEmpty()
  }

  def "pure ZGT setter stores every reporter latest wins without child calls"() {
    given:
    List<Map> coordinatorEvents = [[name: 'groupCoordinatorId', value: 'RINCON_COORD']]
    List<Map> followerEvents = [[name: 'groupCoordinatorId', value: 'RINCON_FOLLOW']]
    when:
    appScript.putZgtTopology(coordinator.deviceNetworkId, ['GROUP-1': coordinatorEvents])
    Map stored = appScript.putZgtTopology(follower.deviceNetworkId, ['GROUP-2': followerEvents])

    then:
    stored.keySet() == ['GROUP-2'] as Set
    appScript.getStoredZgtEventsMap().keySet() == ['GROUP-2'] as Set
    appScript.state.zgtLastReport.sourcePlayerDni == follower.deviceNetworkId
    appScript.state.zgtLastReport.observedAt > 0L
    coordinator.events.isEmpty()
    follower.events.isEmpty()
  }

  def "ZGT payload publishes all topology attributes to every player on the scheduled pass"() {
    given:
    coordinator.updateDataValue('name', 'Living Room')
    follower.updateDataValue('name', 'Kitchen')
    extra.updateDataValue('name', 'Office')

    when:
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_COORD': ['RINCON_FOLLOW']],
      groupNamesById: ['RINCON_COORD:123': 'Downstairs']
    ])

    then:
    appScript.atomicState.zgtPayload.followersByCoordinator.RINCON_COORD == ['RINCON_FOLLOW']
    appScript.scheduled.any { List job -> job[0] == 1 && job[1] == 'publishZgtPlayerAttributes' }
    coordinator.events.isEmpty()
    follower.events.isEmpty()
    extra.events.isEmpty()

    when:
    appScript.publishZgtPlayerAttributes()
    List<String> required = ['groupCoordinatorId', 'groupCoordinatorName', 'groupId',
      'groupMemberCount', 'groupMemberNames', 'groupPlayerIds', 'isGroupCoordinator', 'isGrouped']
    Map coordinatorAttrs = coordinator.emittedAttributes()
    Map followerAttrs = follower.emittedAttributes()
    Map extraAttrs = extra.emittedAttributes()

    then:
    required.every { String name -> coordinatorAttrs.containsKey(name) && followerAttrs.containsKey(name) && extraAttrs.containsKey(name) }
    coordinatorAttrs.groupCoordinatorId == 'RINCON_COORD'
    coordinatorAttrs.groupCoordinatorName == 'Living Room'
    coordinatorAttrs.groupId == 'RINCON_COORD:123'
    coordinatorAttrs.groupMemberCount == 2
    coordinatorAttrs.groupPlayerIds == 'RINCON_COORD,RINCON_FOLLOW'
    followerAttrs.isGroupCoordinator == 'off'
    followerAttrs.isGrouped == 'on'
    extraAttrs.groupCoordinatorId == 'RINCON_EXTRA'
    extraAttrs.groupCoordinatorName == 'Office'
    extraAttrs.groupId == ''
    extraAttrs.groupMemberCount == 1
    extraAttrs.groupPlayerIds == 'RINCON_EXTRA'
    extraAttrs.isGroupCoordinator == 'on'
    extraAttrs.isGrouped == 'off'
    group.emittedAttributes().currentlyJoinedPlayers == 'Living Room, Kitchen'
    group.emittedAttributes().switch == 'on'
    !appScript.state.containsKey('pendingChildEvents')
    !appScript.scheduled.any { List job -> job[1] == 'drainQueuedChildEvents' }
  }

  def "a later ZGT snapshot resets every player omitted from its groups"() {
    given:
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_COORD': ['RINCON_FOLLOW']],
      groupNamesById: ['RINCON_COORD:old': 'Old Group']
    ])
    appScript.publishZgtPlayerAttributes()
    coordinator.events.clear()
    follower.events.clear()
    extra.events.clear()

    when:
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_EXTRA': []],
      groupNamesById: ['RINCON_EXTRA:new': 'Office']
    ])
    appScript.publishZgtPlayerAttributes()

    then:
    coordinator.emittedAttributes().groupCoordinatorId == 'RINCON_COORD'
    coordinator.emittedAttributes().groupId == ''
    coordinator.emittedAttributes().isGrouped == 'off'
    follower.emittedAttributes().groupCoordinatorId == 'RINCON_FOLLOW'
    follower.emittedAttributes().groupId == ''
    follower.emittedAttributes().isGrouped == 'off'
    extra.emittedAttributes().groupId == 'RINCON_EXTRA:new'
    group.emittedAttributes().currentlyJoinedPlayers == ''
    group.emittedAttributes().switch == 'off'
  }

  def "ZGT group switch requires the configured coordinator and exact follower roster"() {
    given:
    coordinator.updateDataValue('name', 'Living Room')
    follower.updateDataValue('name', 'Kitchen')
    extra.updateDataValue('name', 'Office')

    when: 'the configured coordinator and all configured followers match exactly'
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_COORD': ['RINCON_FOLLOW']],
      groupNamesById: ['RINCON_COORD:exact': 'Exact']
    ])
    appScript.publishZgtPlayerAttributes()

    then:
    group.emittedAttributes().switch == 'on'
    group.emittedAttributes().currentlyJoinedPlayers == 'Living Room, Kitchen'

    when: 'a configured follower is missing'
    group.events.clear()
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_COORD': []],
      groupNamesById: ['RINCON_COORD:missing': 'Missing']
    ])
    appScript.publishZgtPlayerAttributes()

    then:
    group.emittedAttributes().switch == 'off'
    group.emittedAttributes().currentlyJoinedPlayers == 'Living Room'

    when: 'an unconfigured extra follower is present'
    group.events.clear()
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_COORD': ['RINCON_FOLLOW', 'RINCON_EXTRA']],
      groupNamesById: ['RINCON_COORD:extra': 'Extra']
    ])
    appScript.publishZgtPlayerAttributes()

    then:
    group.emittedAttributes().switch == 'off'
    group.emittedAttributes().currentlyJoinedPlayers == 'Living Room, Kitchen, Office'

    when: 'the configured coordinator is only a follower under a different active coordinator'
    group.events.clear()
    appScript.setZgtPayload([
      followersByCoordinator: ['RINCON_FOLLOW': ['RINCON_COORD']],
      groupNamesById: ['RINCON_FOLLOW:leader-changed': 'Leader Changed']
    ])
    appScript.publishZgtPlayerAttributes()

    then:
    group.emittedAttributes().switch == 'off'
    group.emittedAttributes().currentlyJoinedPlayers == 'Kitchen, Living Room'
  }

  def "every group child receives both ZGT states even when its coordinator is missing"() {
    given:
    SonosAppGroupDouble unconfigured = new SonosAppGroupDouble(
      deviceNetworkId: 'APP-ID-SonosGroupDevice-Unconfigured', displayName: 'Unconfigured', label: 'Unconfigured')
    appScript.children << unconfigured
    appScript.app.children = appScript.children

    when:
    appScript.setZgtPayload([followersByCoordinator: ['RINCON_COORD': []],
      groupNamesById: ['RINCON_COORD:solo': 'Solo']])
    appScript.publishZgtPlayerAttributes()

    then:
    unconfigured.emittedAttributes().containsKey('currentlyJoinedPlayers')
    unconfigured.emittedAttributes().currentlyJoinedPlayers == ''
    unconfigured.emittedAttributes().switch == 'off'
  }

  def "GRC and AVT are not accepted by the parent ZGT notification queue"() {
    given:
    Map<String, List<Map>> topology = [
      'GROUP-1': [
        [name: 'groupCoordinatorId', value: 'RINCON_COORD'],
        [name: 'groupPlayerIds', value: 'RINCON_COORD,RINCON_FOLLOW'],
        [name: 'groupName', value: 'Kitchen + Kids Room']
      ]
    ]
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: topology])
    runScheduledCallbacks()
    coordinator.events.clear()
    follower.events.clear()
    group.events.clear()

    when:
    appScript.putPlayerUpnpEvents([service: 'GRC', rincon: 'RINCON_COORD', events: [
      [name: 'groupVolume', value: 0], [name: 'groupMute', value: 'unmuted']
    ]])
    appScript.putPlayerUpnpEvents([service: 'AVT', rincon: 'RINCON_COORD', events: [[name: 'status', value: 'playing']]])

    then: 'only ZGT is accepted by the debounced notification queue'
    coordinator.events.isEmpty()
    follower.events.isEmpty()
    !appScript.state.containsKey('pendingUpnpNotifications') || appScript.state.pendingUpnpNotifications.isEmpty()

    when:
    runScheduledCallbacks()

    then:
    !group.events.any { Map event -> event.name in ['volume', 'mute', 'groupVolume', 'groupMute'] }
    !coordinator.events.any { Map event -> event.name in ['groupVolume', 'groupMute'] }
  }

  void seedAvtRegistry() {
    appScript.state.groupDeviceCoordinatorsByDni = ['GROUP-DNI': 'RINCON_COORD']
    appScript.state.zgtEventsByGroupId = ['GROUP-1': [
      [name: 'groupCoordinatorId', value: 'RINCON_COORD'],
      [name: 'groupPlayerIds', value: 'RINCON_COORD,RINCON_FOLLOW']]]
    // The AVT path must never obtain a child device handle (handles deadlock the app gate).
    appScript.metaClass.getChildDevice = { Object dni -> throw new IllegalStateException("child handle requested for ${dni}") }
  }

  List<Map> childEventsFor(String dni) {
    appScript.childEvents.findAll { Map event -> event.device == dni }
  }

  def "putAvtEvents stores by DNI, schedules a 1 ms callback, and touches no device"() {
    given:
    seedAvtRegistry()

    when:
    appScript.putAvtEvents('DNI-RINCON_COORD', [[name: 'currentTrackName', value: 'Track A'], [name: 'status', value: 'playing']], 1L, 1L)
    appScript.putAvtEvents('DNI-RINCON_COORD', [[name: 'currentTrackName', value: 'Track B']], 1L, 2L)

    then:
    appScript.appMemoryMap('avtEventsByDni')['DNI-RINCON_COORD'] == [
      [name: 'currentTrackName', value: 'Track B'], [name: 'status', value: 'playing']]
    appScript.scheduled.findAll { List job -> job[1] == 'applyAvtCallback' }.size() == 2
    appScript.scheduled.every { List job -> job[1] != 'applyAvtCallback' || job[0] == 1 }
    appScript.childEvents.isEmpty()
    coordinator.events.isEmpty()
  }

  def "the AVT callback applies the delta to the coordinator, every follower, and group devices by DNI"() {
    given:
    seedAvtRegistry()
    appScript.playerPreferencesUpdated([deviceDni: 'DNI-RINCON_COORD', preferences: [disableAlbumArtEvents: false]])

    when:
    appScript.putAvtEvents('DNI-RINCON_COORD', [[name: 'currentTrackName', value: 'Track A'],
      [name: 'albumArtURI', value: 'http://192.0.2.10:1400/getaa?s=1&u=x']], 1L, 1L)
    appScript.applyAvtCallback(appScript.scheduled.find { List job -> job[1] == 'applyAvtCallback' }[2].data)

    then:
    ['DNI-RINCON_COORD', 'DNI-RINCON_FOLLOW', 'GROUP-DNI'].every { String dni ->
      childEventsFor(dni).any { Map event -> event.name == 'currentTrackName' && event.value == 'Track A' } }
    childEventsFor('DNI-RINCON_EXTRA').isEmpty()
    childEventsFor('DNI-RINCON_COORD').find { Map event -> event.name == 'albumArtLarge' }.value.endsWith('&x=600&y=600')
    childEventsFor('DNI-RINCON_FOLLOW').find { Map event -> event.name == 'albumArtLarge' }.value == 'Disabled in Preferences'
    coordinator.events.isEmpty()
    follower.events.isEmpty()
  }

  def "requestCoordinatorEvents replays the cached coordinator events only to the requesting follower"() {
    given:
    seedAvtRegistry()
    appScript.putAvtEvents('DNI-RINCON_COORD', [[name: 'currentTrackName', value: 'Latest leader track']], 1L, 1L)
    appScript.scheduled.clear()
    appScript.childEvents.clear()

    when:
    appScript.requestCoordinatorEvents('RINCON_COORD', 'DNI-RINCON_EXTRA')

    then:
    appScript.scheduled.size() == 1
    appScript.scheduled[0][0] == 1
    appScript.childEvents.isEmpty()

    when:
    appScript.applyAvtCallback(appScript.scheduled[0][2].data)

    then:
    childEventsFor('DNI-RINCON_EXTRA').find { Map event -> event.name == 'currentTrackName' }.value == 'Latest leader track'
    childEventsFor('DNI-RINCON_COORD').isEmpty()
    childEventsFor('DNI-RINCON_FOLLOW').isEmpty()
    childEventsFor('GROUP-DNI').isEmpty()
  }

  def "requestCoordinatorEvents ignores unknown coordinators and coordinators with no cached events"() {
    given:
    seedAvtRegistry()

    when:
    appScript.requestCoordinatorEvents('RINCON_UNKNOWN', 'DNI-RINCON_FOLLOW')
    appScript.requestCoordinatorEvents('RINCON_COORD', 'DNI-RINCON_FOLLOW')

    then:
    appScript.scheduled.isEmpty()
  }

  def "ZGT topology changes never replay AVT and the AVT cache is cleared at hub start"() {
    given:
    seedAvtRegistry()
    appScript.putAvtEvents('DNI-RINCON_COORD', [[name: 'currentTrackName', value: 'Track A']], 1L, 1L)
    appScript.scheduled.clear()
    appScript.childEvents.clear()

    when:
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: [
      'GROUP-KITCHEN': [[name: 'groupCoordinatorId', value: 'RINCON_COORD'], [name: 'groupPlayerIds', value: 'RINCON_COORD']],
      'GROUP-KIDS': [[name: 'groupCoordinatorId', value: 'RINCON_FOLLOW'], [name: 'groupPlayerIds', value: 'RINCON_FOLLOW']]
    ]])
    runScheduledCallbacks()
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: [
      'GROUP-1': [[name: 'groupCoordinatorId', value: 'RINCON_COORD'], [name: 'groupPlayerIds', value: 'RINCON_COORD,RINCON_FOLLOW']]
    ]])
    runScheduledCallbacks()

    then:
    !follower.events.any { Map event -> event.name == 'currentTrackName' }
    !appScript.scheduled.any { List job -> job[1] in ['applyAvtCallback', 'applyCachedAvtForPromotedCoordinators', 'replayZgtFollowerAvt'] }

    when:
    appScript.systemStartHandler()

    then:
    !appScript.state.containsKey('avtEventsByDni')
  }

  def "group device registry follows each group device's configured coordinator"() {
    when:
    appScript.refreshGroupDeviceCoordinators()

    then:
    appScript.state.groupDeviceCoordinatorsByDni == ['GROUP-DNI': 'RINCON_COORD']
  }

  def "ZGT coordinator changes do not alter player AVT subscriptions"() {
    given:
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: [
      'GROUP-1': [
        [name: 'groupCoordinatorId', value: 'RINCON_COORD'],
        [name: 'groupPlayerIds', value: 'RINCON_COORD,RINCON_FOLLOW']
      ]
    ]])
    runScheduledCallbacks()

    when:
    appScript.putPlayerUpnpEvents([service: 'ZGT', sourceDni: coordinator.deviceNetworkId, eventsByGroupId: [
      'GROUP-COORD': [[name: 'groupCoordinatorId', value: 'RINCON_COORD'], [name: 'groupPlayerIds', value: 'RINCON_COORD']],
      'GROUP-FOLLOW': [[name: 'groupCoordinatorId', value: 'RINCON_FOLLOW'], [name: 'groupPlayerIds', value: 'RINCON_FOLLOW']]
    ]])
    runScheduledCallbacks()

    then:
    !appScript.scheduled.any { List job -> job[1] == 'drainPlayerAvtSubscriptionUpdates' }
  }

  def "AVT artwork is rendered and group preference controls only group size attributes"() {
    given:
    seedAvtRegistry()
    appScript.playerPreferencesUpdated([deviceDni: 'DNI-RINCON_COORD', preferences: [disableAlbumArtEvents: false]])
    appScript.groupPreferencesUpdated([deviceDni: 'GROUP-DNI', preferences: [disableAlbumArtEvents: true]])
    List<Map> events = [
      [name: 'albumArtURI', value: 'http://192.0.2.10:1400/getaa?s=1&u=x-sonos-http%3asong%253a123.mp4%3fsid%3d204%26flags%3d8232%26sn%3d9'],
      [name: 'nextTrackAlbumArtURI', value: 'http://192.0.2.10:1400/getaa?s=1&u=x-sonos-http%3asong%253a456.mp4%3fsid%3d204%26flags%3d73768%26sn%3d9'],
      [name: 'nextTrackName', value: 'Man in the Box']
    ]
    Closure attrs = { String dni -> childEventsFor(dni).collectEntries { Map event -> [(event.name): event.value] } }

    when:
    appScript.applyAvtEvents('DNI-RINCON_COORD', events)

    then:
    attrs('DNI-RINCON_COORD').albumArtURI.contains('<img')
    attrs('DNI-RINCON_COORD').albumArtURI.contains('http://192.0.2.10:1400/getaa?')
    attrs('DNI-RINCON_COORD').albumArtSmall.endsWith('&x=60&y=60')
    attrs('DNI-RINCON_COORD').albumArtLarge.endsWith('&x=600&y=600')
    attrs('GROUP-DNI').albumArtURI == attrs('DNI-RINCON_COORD').albumArtURI
    attrs('GROUP-DNI').albumArtSmall == 'Disabled in Preferences'
    attrs('GROUP-DNI').albumArtMedium == 'Disabled in Preferences'
    attrs('GROUP-DNI').albumArtLarge == 'Disabled in Preferences'
    attrs('GROUP-DNI').nextTrackAlbumArtURI.contains('<img')
    attrs('GROUP-DNI').nextTrackName == 'Man in the Box'
  }

}
