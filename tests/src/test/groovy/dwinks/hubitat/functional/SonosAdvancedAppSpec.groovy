package dwinks.hubitat.functional

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.hub.domain.Event
import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import hubitat.scheduling.AsyncResponse
import spock.lang.Shared
import spock.lang.Specification

class SonosAppPlayerDouble extends ChildDeviceWrapper {
  Long id = 101L
  List<Map> commands = []
  List<Map> pendingParentUpdatePoll = []
  List<Map> pendingParentRequestPoll = []

  List<Map> drainPendingParentUpdatesForPoll() {
    List<Map> batch = []
    batch.addAll(pendingParentUpdatePoll)
    pendingParentUpdatePoll.clear()
    return batch
  }

  List<Map> drainPendingParentRequestsForPoll() {
    List<Map> batch = []
    batch.addAll(pendingParentRequestPoll)
    pendingParentRequestPoll.clear()
    return batch
  }
  Map favoritesMap = [:]
  Map playlistsMap = [:]
  List<String> acknowledgedParentRequests = []
  List<List<String>> completedAppTtsRequests = []
  List<String> refreshedPlaybackUpdates = []
  List<String> groupPlaybackSnapshotRequests = []
  List<String> initializationRequests = []
  List<Map> initializationRoles = []
  Closure<Boolean> topologyApplyHook

  void beginAppMediatedInitialization(String requestId, Boolean subscribeZgt = false,
      String leaderGeneration = null, Integer attemptNumber = 1) {
    initializationRequests << requestId
    initializationRoles << [subscribeZgt: subscribeZgt, leaderGeneration: leaderGeneration,
      attemptNumber: attemptNumber]
  }
  void cancelAppMediatedInitialization(String requestId) { commands << [name: 'cancelAppMediatedInitialization', requestId: requestId] }

  void mute() { commands << [name: 'mute'] }
  void unmute() { commands << [name: 'unmute'] }

  void acknowledgeParentCommand(String requestId) { acknowledgedParentRequests << requestId }
  void completeParentAppActiveStreamTts(String requestId, String accepted) {
    completedAppTtsRequests << [requestId, accepted]
  }

  void playerCreateGroup(List<String> playerIds, String musicContextGroupId = null) {
    commands << [name: 'playerCreateGroup', playerIds: playerIds, musicContextGroupId: musicContextGroupId]
  }

  void playerModifyGroupMembers(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = [],
      Map<String, Map> expectedTopology = null) {
    Map command = [name: 'playerModifyGroupMembers', add: playerIdsToAdd, remove: playerIdsToRemove]
    if(expectedTopology != null) { command.expectedTopology = expectedTopology }
    commands << command
  }

  void playerGetGroupsFull() {
    commands << [name: 'playerGetGroupsFull']
  }

  List<Map> getCurrentPlayingStatesForGroup() {
    groupPlaybackSnapshotRequests << deviceNetworkId
    return [[name: 'status', value: 'playing'], [name: 'transportStatus', value: 'playing']]
  }

  void playerLoadAudioClip(String uri, BigDecimal volume, Integer duration, String correlationId) {
    commands << [name: 'playerLoadAudioClip', uri: uri, volume: volume,
      duration: duration, correlationId: correlationId]
  }

  void playerLoadAudioClipHighPriority(String uri, BigDecimal volume, String correlationId,
      Integer duration, Boolean deferStart = false, Boolean chimeBeforeTTS = false,
      Boolean holdAfterChime = false) {
    commands << [name: 'playerLoadAudioClipHighPriority', uri: uri, volume: volume,
      correlationId: correlationId, duration: duration, deferStart: deferStart,
      chimeBeforeTTS: chimeBeforeTTS, holdAfterChime: holdAfterChime]
  }

  void playerPrepareAudioClipForSynchronizedStart(String uri, BigDecimal volume,
      String correlationId, Integer duration, Boolean deferStart = false,
      Boolean chimeBeforeTTS = false, Boolean holdAfterChime = false) {
    commands << [name: 'playerPrepareAudioClipForSynchronizedStart', uri: uri,
      volume: volume, correlationId: correlationId, duration: duration, deferStart: deferStart,
      chimeBeforeTTS: chimeBeforeTTS, holdAfterChime: holdAfterChime]
  }

  Boolean canPrepareSynchronizedGroupChime(Boolean highPriority) { return true }
  void clearSynchronizedAudioClipHold(String correlationId) {
    commands << [name: 'clearSynchronizedAudioClipHold', correlationId: correlationId]
  }
  void clearPendingAudioClipCorrelation(String correlationId) {
    commands << [name: 'clearPendingAudioClipCorrelation', correlationId: correlationId]
  }

  void startPreparedAudioClip() { commands << [name: 'startPreparedAudioClip'] }
  void prepareAudioClipForDispatch() { commands << [name: 'prepareAudioClipForDispatch'] }
  void completePreparedAudioClipDispatch() { commands << [name: 'completePreparedAudioClipDispatch'] }
  void playerPlayGroupAnnouncementStream(String uri, BigDecimal volume, String correlationId) {
    commands << [name: 'playerPlayGroupAnnouncementStream', uri: uri, volume: volume,
      correlationId: correlationId]
  }

  void restoreGroupAnnouncementPlayback(String correlationId) {
    commands << [name: 'restoreGroupAnnouncementPlayback', correlationId: correlationId]
  }

  void loadFavoriteForGroupOperation(String favoriteId, String repeatMode, String queueMode,
      String shuffleMode, String autoPlay, String crossfadeMode, String operationId, String groupId,
      String attemptId = null, String requestId = null) {
    commands << [
      name: 'loadFavoriteForGroupOperation', favoriteId: favoriteId, repeatMode: repeatMode,
      queueMode: queueMode, shuffleMode: shuffleMode, autoPlay: autoPlay,
      crossfadeMode: crossfadeMode, operationId: operationId, groupId: groupId,
      attemptId: attemptId, requestId: requestId
    ]
  }

  void registerGroupFavoriteOperation(String operationId, String favoriteId, String requestId = null) {
    commands << [name: 'registerGroupFavoriteOperation', operationId: operationId, favoriteId: favoriteId, requestId: requestId]
  }

  void registerGroupTopologyOperation(String operationId, String requestId = null) {
    commands << [name: 'registerGroupTopologyOperation', operationId: operationId, requestId: requestId]
  }

  void setGroupFavoriteOperationAttempt(String operationId, String attemptId, String groupId, String requestId = null) {
    commands << [name: 'setGroupFavoriteOperationAttempt', operationId: operationId,
                 attemptId: attemptId, groupId: groupId, requestId: requestId]
  }

  void clearGroupFavoriteOperation(String operationId = null) {
    commands << [name: 'clearGroupFavoriteOperation', operationId: operationId]
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
  void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode,
      String shuffleMode, String autoPlay, String crossfadeMode) {
    commands << [name: 'loadFavoriteFull', favoriteId: favoriteId, repeatMode: repeatMode,
      queueMode: queueMode, shuffleMode: shuffleMode, autoPlay: autoPlay,
      crossfadeMode: crossfadeMode]
  }
  void loadPlaylist(String playlistId) { commands << [name: 'loadPlaylist', playlistId: playlistId] }
  void playerPlayForGroupOperation(String operationId, String attemptId, String requestId) {
    commands << [name: 'playerPlayForGroupOperation', operationId: operationId,
      attemptId: attemptId, requestId: requestId]
  }

  Boolean applyAppHouseholdTopology(String json) {
    currentValues.householdTopology = json
    return topologyApplyHook == null || topologyApplyHook.call(json) == true
  }
  void requestAppZgtResubscription() { commands << [name: 'requestAppZgtResubscription'] }
}

class SonosAppGroupDouble extends ChildDeviceWrapper {
  Long id = 201L
  List<Map> statuses = []
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

  void updateGroupOperationStatus(String statusJson, String groupingMode = null) {
    Map status = (Map)new JsonSlurper().parseText(statusJson)
    statuses << [status: status, groupingMode: groupingMode]
    if(groupingMode) { currentValues.groupingMode = groupingMode }
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

  def setup() {
    appScript = ScriptLoader.instantiate(appScriptClass)
    appScript.discoveredSonoses.clear()
    appScript.discoveredSonosSecondaries.clear()
    appScript.zgtResubRequestAt.clear()
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

  def "SAC escaping helpers preserve the existing HTML and XML entity output"() {
    expect:
    appScript.escapeNewUiHtml("A&B <'\"") == 'A&amp;B &lt;&#39;&quot;'
    appScript.escapeSonosXml("A&B <'\"") == 'A&amp;B &lt;&apos;&quot;'
    appScript.normalizeLogLevel('INFO') == 'info'
    appScript.splitTokens('a,,b,', ',') == ['a', 'b']
    appScript.groupCommandRequestSequence([sequence: 'invalid']) == Integer.MAX_VALUE
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
    player.dataValues.websocketUrl = "ws://192.0.2.${id.hashCode().abs() % 200 + 1}:1443/websocket/api"
    player.dataValues.isGroupCoordinator = isCoordinator.toString()
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

  def "player initialization queue grants up to three children at a time"() {
    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', false)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', false)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', false)
    appScript.startNextPlayerInitialization()

    then:
    coordinator.initializationRequests.size() == 1
    follower.initializationRequests.size() == 1
    extra.initializationRequests.size() == 1
    appScript.state.playerInitializationActive.size() == 3
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].phase == 'initializing'
    !appScript.scheduled.any { List job -> job[1] == 'playerInitializationStartWatchdog' }
    appScript.scheduled.count { List job -> job[1] == 'playerInitializationHardWatchdog' &&
      job[2]?.overwrite == true && job[0] >= 1 } == 1
    appScript.state.playerInitializationActive.values().every { Map entry ->
      entry.attemptNumber == 1 && entry.timeoutSeconds == 180
    }
    !appScript.state.containsKey('playerInitializationLedger')

    when:
    String requestId = coordinator.initializationRequests[0]
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson([
      kind: 'initializationStarted', playerDni: coordinator.deviceNetworkId, requestId: requestId
    ])] as Event)

    then:
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].phase == 'initializing'

    when:
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson([
      kind: 'initializationComplete', playerDni: coordinator.deviceNetworkId, requestId: requestId
    ])] as Event)

    then:
    appScript.state.playerInitializationActive.size() == 2
    !appScript.state.playerInitializationActive.containsKey(coordinator.deviceNetworkId)
  }

  def "initialization purge removes only the selected child's parent queues and announcements"() {
    given:
    appScript.state.pendingPlayerCommandRequests = [
      (follower.deviceNetworkId): [[playerDni: follower.deviceNetworkId, command: 'play']],
      (extra.deviceNetworkId): [[playerDni: extra.deviceNetworkId, command: 'pause']]
    ]
    appScript.state.pendingGroupCommandRequests = [
      'GROUP-DNI': [[groupDni: 'GROUP-DNI', command: 'on']],
      'OTHER-GROUP': [[groupDni: 'OTHER-GROUP', command: 'off']]
    ]
    appScript.state.deferredGroupPlayerCommands = [
      [playerDni: follower.deviceNetworkId, command: 'play'],
      [playerDni: extra.deviceNetworkId, command: 'pause']
    ]
    appScript.state.groupAnnouncementQueues = [
      'GROUP-DNI': [active: [correlationId: 'follower-active', playerIds: ['RINCON_FOLLOW']],
        pending: [[correlationId: 'follower-pending', playerIds: ['RINCON_FOLLOW']]]],
      'OTHER-GROUP': [active: [correlationId: 'extra-active', playerIds: ['RINCON_EXTRA']], pending: []]
    ]
    appScript.state.groupAnnouncementTopologyTransactions = [
      'follower-active': [correlationId: 'follower-active', targetPlayerIds: ['RINCON_FOLLOW']],
      'extra-active': [correlationId: 'extra-active', targetPlayerIds: ['RINCON_EXTRA']]
    ]

    when:
    appScript.clearPendingCommandsForDevice(follower.deviceNetworkId)

    then:
    !appScript.state.pendingPlayerCommandRequests.containsKey(follower.deviceNetworkId)
    appScript.state.pendingPlayerCommandRequests[extra.deviceNetworkId].size() == 1
    appScript.state.pendingGroupCommandRequests['GROUP-DNI'].size() == 1
    appScript.state.deferredGroupPlayerCommands*.playerDni == [extra.deviceNetworkId]
    !appScript.state.groupAnnouncementQueues.containsKey('GROUP-DNI')
    appScript.state.groupAnnouncementQueues['OTHER-GROUP'].active.correlationId == 'extra-active'
    !appScript.state.groupAnnouncementTopologyTransactions.containsKey('follower-active')
    appScript.state.groupAnnouncementTopologyTransactions.containsKey('extra-active')
    appScript.state.deviceCommandClearTimes[follower.deviceNetworkId] != null

    when:
    appScript.enqueuePlayerCommandRequest([playerDni: follower.deviceNetworkId,
      requestId: 'late-player-command', command: 'play', createdAt: appScript.state.deviceCommandClearTimes[follower.deviceNetworkId]])

    then:
    !appScript.state.pendingPlayerCommandRequests.containsKey(follower.deviceNetworkId)
  }

  def "initialization purge removes all parent queues for a group child"() {
    given:
    appScript.state.pendingGroupCommandRequests = [
      'GROUP-DNI': [[groupDni: 'GROUP-DNI', command: 'groupPlayers']],
      'OTHER-GROUP': [[groupDni: 'OTHER-GROUP', command: 'ungroupPlayers']]
    ]
    appScript.state.groupAnnouncementQueues = [
      'GROUP-DNI': [active: [correlationId: 'active-tts', isTts: true],
        priorityActive: [correlationId: 'priority-tts', isTts: true],
        pending: [[correlationId: 'queued-tts', isTts: true]]],
      'OTHER-GROUP': [active: null, pending: []]
    ]
    appScript.state.groupAnnouncementTopologyTransactions = [
      'active-tts': [groupDni: 'GROUP-DNI'], 'other': [groupDni: 'OTHER-GROUP']
    ]

    when:
    appScript.enqueueGroupCommandRequest([action: 'initializationPurge', groupDni: 'GROUP-DNI', createdAt: appScript.now()])

    then:
    !appScript.state.pendingGroupCommandRequests.containsKey('GROUP-DNI')
    appScript.state.pendingGroupCommandRequests.containsKey('OTHER-GROUP')
    !appScript.state.groupAnnouncementQueues.containsKey('GROUP-DNI')
    appScript.state.groupAnnouncementQueues.containsKey('OTHER-GROUP')
    !appScript.state.groupAnnouncementTopologyTransactions.containsKey('active-tts')
    appScript.state.groupAnnouncementTopologyTransactions.containsKey('other')

    when:
    appScript.enqueueGroupCommandRequest([groupDni: 'GROUP-DNI', requestId: 'late-group-command',
      command: 'groupPlayers', createdAt: appScript.state.deviceCommandClearTimes['GROUP-DNI']])

    then:
    !appScript.state.pendingGroupCommandRequests.containsKey('GROUP-DNI')
  }

  def "player initialization event clears parent commands before queuing initialization"() {
    given:
    appScript.state.pendingPlayerCommandRequests = [
      (coordinator.deviceNetworkId): [[playerDni: coordinator.deviceNetworkId, command: 'play']]
    ]

    when:
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson([
      kind: 'initializationRequested', playerDni: coordinator.deviceNetworkId,
      reason: 'driver initialize', clearPendingCommands: true
    ])] as Event)

    then:
    !(appScript.state.pendingPlayerCommandRequests instanceof Map) ||
      !appScript.state.pendingPlayerCommandRequests.containsKey(coordinator.deviceNetworkId)
    appScript.state.playerInitializationQueue*.dni.contains(coordinator.deviceNetworkId)
    appScript.state.deviceCommandClearTimes[coordinator.deviceNetworkId] != null
  }

  def "first driver initialization request arms a delayed boot batch"() {
    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'driver initialize', false)
    appScript.startNextPlayerInitialization()

    then:
    appScript.state.playerInitializationBootPending == true
    appScript.state.playerInitializationBootGeneration
    appScript.state.playerInitializationNotBefore >= appScript.now() + 59000L
    (appScript.state.playerInitializationQueue as List<Map>)[0].bootGeneration ==
        appScript.state.playerInitializationBootGeneration
    coordinator.initializationRequests.empty
    appScript.state.playerInitializationQueue.size() == 1
    appScript.scheduled.any { List job -> job[0] == 60 && job[1] == 'startNextPlayerInitialization' }
  }

  def "system start re-arms persisted player state updates after reboot"() {
    given:
    appScript.state.playerStateUpdateDrainScheduled = true
    appScript.state.pendingPlayerStateUpdates = [[
      kind: 'initializationComplete', playerDni: coordinator.deviceNetworkId,
      requestId: 'stale-request', detail: 'completed before reboot'
    ]]

    when:
    appScript.systemStartHandler()

    then:
    appScript.state.playerStateUpdateDrainScheduled == true
    appScript.state.pendingPlayerStateUpdates.size() == 1
    appScript.scheduled.any { List job ->
      job[0] == 25 && job[1] == 'drainQueuedPlayerStateUpdates' && job[2]?.overwrite == true
    }
  }

  def "boot releases only the first eligible mains player for ZGT initialization"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-1'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-1', failedCandidates: [], topologyReady: false]
    appScript.state.playerInitializationNotBefore = appScript.now()
    follower.dataValues.modelName = 'Sonos Move'

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()

    then:
    coordinator.initializationRequests.size() == 1
    coordinator.initializationRoles[0].subscribeZgt == true
    follower.initializationRequests.empty
    extra.initializationRequests.empty
    appScript.state.playerInitializationActive.size() == 1
    appScript.state.playerInitializationZgt.candidateDni == coordinator.deviceNetworkId
    appScript.state.playerInitializationQueue*.dni as Set ==
      ([follower.deviceNetworkId, extra.deviceNetworkId] as Set)
    appScript.scheduled.any { List job -> job[1] == 'playerInitializationHardWatchdog' &&
      job[0] <= 180000 && job[2]?.overwrite == true }
  }

  def "ZGT candidate timeout promotes a different mains player and ignores late topology"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-timeout'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-timeout', failedCandidates: [], topologyReady: false]
    appScript.state.playerInitializationNotBefore = appScript.now()

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    Map firstRequest = appScript.state.playerInitializationActive[coordinator.deviceNetworkId]
    appScript.failZgtInitializationCandidate(coordinator.deviceNetworkId, 'test timeout')
    appScript.startNextPlayerInitialization()

    then:
    appScript.state.playerInitializationZgt.failedCandidates.contains(coordinator.deviceNetworkId)
    appScript.state.playerInitializationZgt.candidateDni == follower.deviceNetworkId
    follower.initializationRequests.size() == 1
    follower.initializationRoles[0].subscribeZgt == true
    appScript.state.playerInitializationActive.containsKey(follower.deviceNetworkId)
    appScript.state.playerInitializationQueue*.dni.contains(coordinator.deviceNetworkId)

    when:
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [
      groups: [[groupId: 'GROUP', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']]]])

    then:
    appScript.state.sonosHouseholdTopology == null
    appScript.state.playerInitializationZgt.candidateDni == follower.deviceNetworkId
  }

  def "manual initialize immediately retries an exhausted boot ZGT election"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-exhausted-retry'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-exhausted-retry', failedCandidates: [], topologyReady: false]
    appScript.state.playerInitializationNotBefore = appScript.now()
    follower.dataValues.modelName = 'Sonos Move'
    extra.dataValues.powerSource = 'battery'

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    appScript.failZgtInitializationCandidate(coordinator.deviceNetworkId, 'test timeout')

    then:
    appScript.state.playerInitializationZgt.status == 'exhausted'
    appScript.state.playerInitializationZgt.candidateDni == null
    appScript.scheduled.any { List job -> job[1] == 'retryZgtInitializationElection' }

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'driver initialize', false)

    then:
    appScript.state.playerInitializationZgt.status != 'exhausted'
    appScript.state.playerInitializationZgt.failedCandidates.empty
    appScript.state.playerInitializationZgt.candidateDni == coordinator.deviceNetworkId
    coordinator.initializationRequests.size() == 2
    coordinator.initializationRoles[1].subscribeZgt == true
  }

  def "ZGT candidate completed initialization still loses its role when its topology times out"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-completed-timeout'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = [[dni: follower.deviceNetworkId, reason: 'hub boot', bootGeneration: 'boot-zgt-completed-timeout']]
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-completed-timeout',
      failedCandidates: [], topologyReady: false, candidateDni: coordinator.deviceNetworkId,
      leaderGeneration: 'old-leader']
    coordinator.dataValues.appZgtSubscribe = 'true'
    coordinator.dataValues.appZgtLeaderGeneration = 'old-leader'

    when:
    appScript.failZgtInitializationCandidate(coordinator.deviceNetworkId, 'topology timeout after initialization completed')

    then:
    coordinator.dataValues.appZgtSubscribe == 'false'
    coordinator.dataValues.appZgtLeaderGeneration == ''
    appScript.state.playerInitializationZgt.candidateDni == follower.deviceNetworkId
    appScript.state.playerInitializationZgt.leaderGeneration != 'old-leader'
  }

  def "valid full household topology opens queue before ZGT player init completion"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-ready'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-ready', failedCandidates: [], topologyReady: false]
    appScript.state.playerInitializationNotBefore = appScript.now()

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    Map zgt = appScript.state.playerInitializationZgt as Map
    List<Boolean> topologyReadyDuringApply = []
    [coordinator, follower, extra].each { SonosAppPlayerDouble player ->
      player.topologyApplyHook = { String json ->
        topologyReadyDuringApply << (appScript.state.playerInitializationZgt.topologyReady == true)
        return true
      }
    }
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [groups: [
        [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
        [groupId: 'GROUP-2', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA']]
      ]])
    appScript.applyHouseholdTopologyBatch()
    appScript.startNextPlayerInitialization()

    then:
    appScript.state.sonosHouseholdTopology.groups.size() == 2
    appScript.state.playerInitializationZgt.topologyReady == true
    appScript.state.playerInitializationZgt.initializationComplete != true
    topologyReadyDuringApply == [false, false, false]
    coordinator.currentValues.householdTopology != null
    follower.currentValues.householdTopology != null
    extra.currentValues.householdTopology != null
    appScript.state.playerInitializationActive.size() == 3
    follower.initializationRequests.size() == 1
    extra.initializationRequests.size() == 1
    coordinator.initializationRequests.size() == 1
    coordinator.dataValues.householdTopology.contains('GROUP-2')
    coordinator.groupPlaybackSnapshotRequests == [coordinator.deviceNetworkId]
    extra.groupPlaybackSnapshotRequests.empty
    follower.groupPlaybackSnapshotRequests.empty
  }

  def "ZGT init failure after topology acceptance preserves topology and releases ordinary players"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-timeout-after-topology'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-timeout-after-topology',
      failedCandidates: [], topologyReady: false, initializationComplete: false]
    appScript.state.playerInitializationNotBefore = appScript.now()

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    Map zgt = appScript.state.playerInitializationZgt as Map
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [groups: [[groupId: 'GROUP',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']]]])
    appScript.applyHouseholdTopologyBatch()
    String firstRequestId = appScript.state.playerInitializationActive[coordinator.deviceNetworkId].requestId
    appScript.finishPlayerInitialization(firstRequestId, false, 'test timeout after topology acceptance', true)
    appScript.startNextPlayerInitialization()

    then:
    appScript.state.playerInitializationZgt.failedCandidates.empty
    appScript.state.playerInitializationZgt.candidateDni == coordinator.deviceNetworkId
    appScript.state.playerInitializationZgt.topologyReady == true
    follower.initializationRequests.size() == 1
    follower.initializationRoles[0].subscribeZgt == false
    extra.initializationRequests.size() == 1
    appScript.state.playerInitializationActive.size() == 2
    appScript.state.playerInitializationActive.keySet().containsAll([follower.deviceNetworkId, extra.deviceNetworkId])
    appScript.state.playerInitializationQueue.any { Map request ->
      request.dni == coordinator.deviceNetworkId && request.bootAttempt == 1
    }
  }

  def "boot queue stays held if any player cannot apply household topology"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-apply-failure'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-apply-failure', failedCandidates: [], topologyReady: false]
    appScript.state.playerInitializationNotBefore = appScript.now()
    follower.topologyApplyHook = { String json -> false }

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'hub boot', true)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    Map zgt = appScript.state.playerInitializationZgt as Map
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [groups: [
        [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
        [groupId: 'GROUP-2', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA']]
      ]])
    appScript.applyHouseholdTopologyBatch()

    then:
    appScript.state.playerInitializationZgt.topologyReady == false
    appScript.state.playerInitializationZgt.failedCandidates.contains(coordinator.deviceNetworkId)
    follower.initializationRequests.empty
    extra.initializationRequests.empty
  }

  def "topology matching the accepted household snapshot does not arm the ZGT failure watchdog"() {
    given:
    appScript.state.sonosHouseholdTopology = [groups: [
      [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
      [groupId: 'GROUP-3', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA']]
    ]]
    appScript.state.playerInitializationZgt = [candidateDni: coordinator.deviceNetworkId,
      leaderGeneration: 'leader-1', topologyReady: true, lastInboundAt: appScript.now()]

    when:
    appScript.processTopologyPayload(coordinator, [groupId: 'GROUP-1',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW', 'RINCON_COORD'],
      observedAt: appScript.now(), playerDni: coordinator.deviceNetworkId])

    then:
    !appScript.scheduled.any { List job -> job[1] == 'zgtTopologyWatchdog' }
    appScript.state.playerInitializationZgt.topologyReady == true
    appScript.logs.any { String log -> log.contains('matches the app household snapshot; ZGT watchdog unchanged') }
  }

  def "unchanged accepted household topology does not fan out again"() {
    given:
    appScript.state.playerInitializationZgt = [candidateDni: coordinator.deviceNetworkId,
      topologyReady: false]
    Map payload = [groups: [[groupId: 'GROUP-1',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]]]

    when:
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, payload)
    appScript.applyHouseholdTopologyBatch()
    appScript.scheduled.clear()
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, payload)

    then:
    appScript.scheduled.empty
    appScript.state.playerInitializationZgt.topologyReady == true
  }

  def "repeated identical ZGT snapshot is ignored without reapplying"() {
    given:
    acceptZgtTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')
    appScript.state.playerInitializationZgt.topologyReady = true
    appScript.scheduled.clear()

    when:
    acceptZgtTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')

    then:
    appScript.state.sonosHouseholdTopology.groups[0].groupId == 'GROUP-1'
    !appScript.scheduled.any { List job -> job[1] == 'applyHouseholdTopologyBatch' }
  }

  def "ZGT snapshot showing ungrouped players is accepted and fanned out"() {
    given:
    acceptZgtTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')

    when:
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [groups: [
        [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
        [groupId: 'GROUP-2', coordinatorId: 'RINCON_FOLLOW', playerIds: ['RINCON_FOLLOW']]
      ]])
    appScript.applyHouseholdTopologyBatch()

    then:
    (appScript.state.sonosHouseholdTopology.groups as List<Map>).every { Map item ->
      (item.playerIds as List).size() == 1
    }
    group.refreshedPlaybackUpdates.size() == 1
    (new JsonSlurper().parseText(group.refreshedPlaybackUpdates[0]).groups as List<Map>).every { Map item ->
      (item.playerIds as List).size() == 1
    }
    appScript.state.playerInitializationZgt.topologyReady == true
  }

  def "topology differing from the accepted household snapshot arms the ZGT watchdog"() {
    given:
    appScript.state.sonosHouseholdTopology = [groups: [
      [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    ]]
    appScript.state.playerInitializationZgt = [candidateDni: coordinator.deviceNetworkId,
      leaderGeneration: 'leader-1', topologyReady: true, lastInboundAt: appScript.now()]

    when:
    appScript.processTopologyPayload(coordinator, [groupId: 'GROUP-1',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD'],
      observedAt: appScript.now(), playerDni: coordinator.deviceNetworkId])

    then:
    appScript.scheduled.any { List job -> job[1] == 'zgtTopologyWatchdog' && job[0] == 30 }
    appScript.state.playerInitializationZgt.topologyChangedAt != null
  }

  def "household topology is not delivered to alarm manager without an id data value"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-zgt-child-filter'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationQueue = []
    appScript.state.playerInitializationActive = [:]
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-zgt-child-filter', failedCandidates: [], topologyReady: false]
    appScript.state.playerInitializationNotBefore = appScript.now()
    SonosAppPlayerDouble alarmManager = new SonosAppPlayerDouble(
      deviceNetworkId: '283-SonosAlarmManager-HOUSEHOLD', displayName: 'Sonos Alarm Manager', label: 'Sonos Alarm Manager'
    )
    appScript.children = [group, coordinator, follower, extra, alarmManager]
    appScript.app.children = appScript.children

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [groups: [[groupId: 'GROUP-1',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']]]])
    appScript.applyHouseholdTopologyBatch()

    then:
    coordinator.dataValues.householdTopology != null
    alarmManager.dataValues.householdTopology == null
    !(alarmManager in appScript.getCurrentGroupDevices())
    !appScript.logs.any { String log -> log.contains('Could not deliver household topology') }
  }

  def "burst of off-turn WebSocket requests arms a delayed boot batch"() {
    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId,
      'WebSocket connection requested outside app initialization turn', false)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId,
      'WebSocket connection requested outside app initialization turn', false)
    appScript.classifyPlayerInitializationRequestBurst()

    then:
    appScript.state.playerInitializationBootPending == true
    appScript.state.playerInitializationNotBefore >= appScript.now() + 59000L
    (appScript.state.playerInitializationQueue as List<Map>).every { Map request ->
      request.bootGeneration == appScript.state.playerInitializationBootGeneration
    }
    coordinator.initializationRequests.empty
    follower.initializationRequests.empty
  }

  def "reboot initialization gets one tail retry then defers to hourly retry"() {
    given:
    appScript.state.playerInitializationBootGeneration = 'boot-generation-1'
    appScript.state.playerInitializationBootPending = true
    appScript.state.playerInitializationZgt = [bootGeneration: 'boot-generation-1',
      failedCandidates: [], topologyReady: true, initializationComplete: true,
      candidateDni: coordinator.deviceNetworkId]

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'hub boot', true)
    appScript.startNextPlayerInitialization()
    String firstRequest = coordinator.initializationRequests[0]
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].deadlineAt = appScript.now() - 1L
    appScript.playerInitializationHardWatchdog([dni: coordinator.deviceNetworkId, requestId: firstRequest])

    then:
    (appScript.state.playerInitializationQueue as List<Map>).size() == 1
    appScript.state.playerInitializationQueue[0].bootAttempt == 1
    appScript.state.playerInitializationActive.isEmpty()

    when:
    appScript.startNextPlayerInitialization()
    String secondRequest = coordinator.initializationRequests[1]
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].deadlineAt = appScript.now() - 1L
    appScript.playerInitializationHardWatchdog([dni: coordinator.deviceNetworkId, requestId: secondRequest])

    then:
    appScript.state.playerInitializationQueue.empty
    appScript.state.playerInitializationDeferred[coordinator.deviceNetworkId] != null
    appScript.scheduled.any { List job -> job[0] == 3600 && job[1] == 'retryDeferredPlayerInitializations' }
    appScript.logs.any { String log -> log.contains('attempt 2') && log.contains('WARN') }
  }

  def "initialization retry waits for every first attempt and receives a ten minute timeout"() {
    given:
    appScript.state.playerInitializationBootPending = false
    appScript.state.playerInitializationNotBefore = appScript.now()

    when:
    appScript.enqueuePlayerInitialization(coordinator.deviceNetworkId, 'test', false)
    appScript.enqueuePlayerInitialization(follower.deviceNetworkId, 'test', false)
    appScript.enqueuePlayerInitialization(extra.deviceNetworkId, 'test', false)
    appScript.startNextPlayerInitialization()
    String failedRequest = coordinator.initializationRequests[0]
    String followerRequest = follower.initializationRequests[0]
    String extraRequest = extra.initializationRequests[0]
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].deadlineAt = appScript.now() - 1L
    appScript.playerInitializationHardWatchdog([dni: coordinator.deviceNetworkId, requestId: failedRequest])

    then:
    coordinator.initializationRequests.size() == 1
    appScript.state.playerInitializationQueue[0].bootAttempt == 1

    when:
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson([
      kind: 'initializationComplete', playerDni: follower.deviceNetworkId,
      requestId: followerRequest, detail: 'ready'
    ])] as Event)

    then:
    coordinator.initializationRequests.size() == 1

    when:
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson([
      kind: 'initializationComplete', playerDni: extra.deviceNetworkId,
      requestId: extraRequest, detail: 'ready'
    ])] as Event)
    appScript.startNextPlayerInitialization()

    then:
    coordinator.initializationRequests.size() == 2
    String retryRequest = coordinator.initializationRequests[1]
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].attemptNumber == 2
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].timeoutSeconds == 600
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].deadlineAt ==
      appScript.state.playerInitializationActive[coordinator.deviceNetworkId].initializationStartedAt + 600000L

    when:
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson([
      kind: 'initializationComplete', playerDni: coordinator.deviceNetworkId,
      requestId: failedRequest, detail: 'late first-attempt callback'
    ])] as Event)

    then:
    appScript.state.playerInitializationActive[coordinator.deviceNetworkId].requestId == retryRequest
    !appScript.state.containsKey('playerInitializationLedger')
  }

  def "parent stamps coordinator transfer expectations on each affected player"() {
    given:
    Long expiresAt = appScript.now() + 30000L
    Map topology = [coordinatorId: 'RINCON_FOLLOW', memberIds: ['RINCON_FOLLOW', 'RINCON_COORD']]

    when:
    appScript.setExpectedTopologyForPlayers([
      RINCON_COORD: topology, RINCON_FOLLOW: topology
    ], 'transfer-1', expiresAt)

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      Map expected = new JsonSlurper().parseText(player.dataValues.expectedGroupTopology) as Map
      expected == [operationId: 'transfer-1', expiresAt: expiresAt,
        coordinatorId: 'RINCON_FOLLOW', memberIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    }
    !extra.dataValues.containsKey('expectedGroupTopology')
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
      player.dataValues.isGroupCoordinator = player.dataValues.id == coordinatorId ? 'true' : 'false'
    }
  }

  void emitTopologyObservation(String playerId, String groupId, String coordinatorId, String playerIds,
      Long observedAt = null) {
    Long eventObservedAt = observedAt ?: appScript.now() + (++topologyObservationSequence * 1000L)
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
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
    ] as Event)
  }

  void emitRequiredTopology(String groupId = 'GROUP-1', String coordinatorId = 'RINCON_COORD',
      String playerIds = 'RINCON_COORD,RINCON_FOLLOW') {
    emitTopologyObservation('RINCON_COORD', groupId, coordinatorId, playerIds)
    emitTopologyObservation('RINCON_FOLLOW', groupId, coordinatorId, playerIds)
  }

  void acceptZgtTopology(String groupId, String coordinatorId, String playerIds) {
    if(!(appScript.state.playerInitializationZgt instanceof Map)) {
      appScript.state.playerInitializationZgt = [
        candidateDni: coordinator.deviceNetworkId,
        topologyReady: true
      ]
    }
    appScript.acceptHouseholdTopology(coordinator.deviceNetworkId, [groups: [[
        groupId: groupId,
        coordinatorId: coordinatorId,
        playerIds: playerIds.split(',') as List
      ]]])
  }

  void emitPlayback(String playerId, String playbackState, Long observedAt = null, String groupId = 'GROUP-1') {
    Map operation = appScript.getActiveGroupOperation()
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId,
        playerId: playerId,
        attemptId: operation.favoriteAttemptId,
        observedAt: observedAt ?: appScript.now(),
        event: 'playbackStatus',
        data: [playbackState: playbackState, groupId: groupId]
      ])
    ] as Event)
  }

  def "explicit group refresh forwards coordinator playback values to the group driver"() {
    given:
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.currentTrackName = 'Only the Good Die Young'
    coordinator.currentValues.currentArtistName = 'Billy Joel'

    when:
    appScript.processGroupCommandRequest([request: request('refresh')])
    appScript.drainGroupStateUpdates()

    then:
    group.refreshedPlaybackUpdates.size() == 1
    group.refreshedPlaybackUpdates[0].contains('"status":"playing"')
    group.refreshedPlaybackUpdates[0].contains('"currentTrackName":"Only the Good Die Young"')
    group.refreshedPlaybackUpdates[0].contains('"currentArtistName":"Billy Joel"')
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
    coordinator.dataValues.isGroupCoordinator = 'true'
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
    group.dataValues.remove('groupCoordinatorId')
    group.dataValues.remove('playerIds')
    appScript.state.userGroups = [
      'Arc + Kitchen': [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    ]

    expect:
    appScript.getAllPlayersForGroupDevice(group) == ['RINCON_COORD', 'RINCON_FOLLOW']
    appScript.buildGroupOperationTarget(group, 'EXPLICIT').configuredCoordinatorId == 'RINCON_COORD'
  }

  def "group definition lookup uses the group name encoded in the device DNI"() {
    given:
    group.deviceNetworkId = 'different-app-id-SonosGroupDevice-Arc + Kitchen'
    appScript.state.userGroups = [
      'Arc + Kitchen': [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    ]

    expect:
    appScript.getConfiguredGroupDefinition(group).groupCoordinatorId == 'RINCON_COORD'
  }

  def "eviction operation retains the recovered configured coordinator"() {
    given:
    group.deviceNetworkId = 'different-app-id-SonosGroupDevice-Arc + Kitchen'
    group.dataValues.remove('groupCoordinatorId')
    group.dataValues.remove('playerIds')
    appScript.state.userGroups = [
      'Arc + Kitchen': [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    ]

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
    coordinator.dataValues.isGroupCoordinator = 'false'
    follower.dataValues.groupId = 'GROUP-2'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA'
    follower.dataValues.isGroupCoordinator = 'true'
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
    coordinator.dataValues.isGroupCoordinator = 'true'
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
    coordinator.dataValues.isGroupCoordinator = 'true'
    follower.dataValues.groupId = 'GROUP-FOLLOW'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    follower.dataValues.isGroupCoordinator = 'true'

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

  def "player parent requests are acknowledged after enqueue and duplicate deliveries are deduplicated"() {
    given:
    extra.dataValues.groupId = 'GROUP-OTHER'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    Map playerRequest = [playerDni: follower.deviceNetworkId, requestId: "${follower.deviceNetworkId}-1",
      command: 'addPlayerToGroup', args: ['RINCON_EXTRA']]

    when:
    appScript.enqueuePlayerCommandRequest(playerRequest)
    appScript.enqueuePlayerCommandRequest(playerRequest)

    then:
    appScript.state.pendingPlayerCommandRequests[follower.deviceNetworkId].size() == 1
    follower.acknowledgedParentRequests == [playerRequest.requestId, playerRequest.requestId]

    when:
    appScript.drainPlayerCommandRequests()

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_EXTRA']
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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    topology.fresh == true
    topology.consistent == true
    topology.authorityPlayerId == 'RINCON_COORD'
    topology.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW']
    appScript.getActiveGroupOperation() == null
    group.statuses.last().status.status == 'SUCCEEDED'
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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

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
    coordinator.commands.find { it.name == 'loadFavoriteFull' } == [
      name: 'loadFavoriteFull', favoriteId: '42', repeatMode: 'off', queueMode: 'replace',
      shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ]
    appScript.getActiveGroupOperation() == null
    [coordinator, follower, extra].every { SonosAppPlayerDouble player ->
      !player.commands.any { it.name in ['playerGetGroupsFull', 'playerCreateGroup', 'playerModifyGroupMembers'] }
    }
  }

  def "group favorite load without an ID selects from available sparse IDs"() {
    given:
    coordinator.favoritesMap = [
      first: [id: '3'],
      second: [id: '9001']
    ]

    when:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [:], 1)])

    then:
    coordinator.commands.find { it.name == 'loadFavoriteFull' }.favoriteId in ['3', '9001']
    appScript.getActiveGroupOperation() == null
  }

  def "group playlist load without an ID selects from available sparse IDs and explicit IDs remain intact"() {
    given:
    coordinator.playlistsMap = [
      'playlist-2': [id: 'playlist-2'],
      'playlist-10004': [id: 'playlist-10004']
    ]

    when:
    appScript.processGroupCommandRequest([request: request('loadPlaylist', [:], 1)])

    then:
    coordinator.commands.find { it.name == 'loadPlaylist' }.playlistId in ['playlist-2', 'playlist-10004']

    when:
    appScript.processGroupCommandRequest([request: request('loadPlaylist', [playlistId: 'playlist-explicit-88'], 2)])

    then:
    coordinator.commands.findAll { it.name == 'loadPlaylist' }*.playlistId == [
      coordinator.commands.find { it.name == 'loadPlaylist' }.playlistId,
      'playlist-explicit-88'
    ]
  }

  def "group random favorite load waits for the coordinator favorites query when cache is empty"() {
    when:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: ' '], 1)])

    then:
    appScript.state.pendingRandomGroupMediaLoads.size() == 1
    coordinator.commands.any { it.name == 'getFavorites' }

    when:
    coordinator.favoritesMap = [item: [id: 'favorite-gap-27']]
    appScript.retryRandomGroupMediaLoads()

    then:
    coordinator.commands.find { it.name == 'loadFavoriteFull' }.favoriteId == 'favorite-gap-27'
    appScript.getActiveGroupOperation() == null
    !appScript.state.containsKey('pendingRandomGroupMediaLoads')
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

  def "queued group requests preserve grouping-before-Favorite order"() {
    given:
    coordinator.dataValues.groupId = 'GROUP-COORD'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    coordinator.dataValues.isGroupCoordinator = 'true'
    follower.dataValues.groupId = 'GROUP-FOLLOW'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    follower.dataValues.isGroupCoordinator = 'true'

    when:
    appScript.enqueueGroupCommandRequest(request('loadFavorite', [favoriteId: '42'], 2))
    appScript.enqueueGroupCommandRequest(request('on', [:], 1))
    appScript.drainGroupCommandRequests()

    then:
    appScript.getActiveGroupOperation() == null
    // on() now executes an immediate exact write; loadFavorite runs directly and is not deferred
    coordinator.commands.find { it.name == 'loadFavoriteFull' }?.favoriteId == '42'
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW'] ||
      coordinator.commands.find { it.name == 'playerModifyGroupMembers' }?.add == ['RINCON_FOLLOW']
    !appScript.state.containsKey('deferredGroupOperationCommands')
  }

  def "direct group requests from another app instance are rejected"() {
    when:
    appScript.groupAppRequest([channel: 'groupAppRequest', groupDni: 'OTHER-GROUP', requestId: 'OTHER-GROUP-1', command: 'on', args: [:]])

    then:
    !appScript.state.groupCommandRequests
    !appScript.scheduled.any { it[1] == 'drainGroupCommandRequests' }
  }

  def "direct group requests are accepted and duplicate deliveries are not re-enqueued"() {
    given:
    Map groupRequest = request('refresh', [:], 1)
    groupRequest.requestId = '6b684b7d-8f6c-4b18-9cf1-2eaad384bf5a'
    groupRequest.channel = 'groupAppRequest'

    when:
    Boolean accepted = appScript.groupAppRequest(groupRequest)
    appScript.enqueueGroupCommandRequest(groupRequest)

    then:
    accepted
    appScript.state.pendingGroupCommandRequests['GROUP-DNI'].size() == 1
    appScript.state.pendingGroupCommandRequests['GROUP-DNI'][0].requestId == groupRequest.requestId
    appScript.logs.count { String entry ->
      entry.contains("Ignoring duplicate group command request ${groupRequest.requestId}")
    } == 1
  }

  def "poll pulls driver outboxes without any synchronous parent call"() {
    given:
    coordinator.pendingParentUpdatePoll = [[kind: 'playbackObservation', playerDni: coordinator.deviceNetworkId, playbackState: 'PLAYBACK_STATE_PLAYING']]
    group.pendingGroupRequestPoll = [request('refresh', [:], 1)]

    when:
    appScript.pollDriverParentQueues()

    then:
    // Direct parent calls — poll loop removed, no pending at that time
    true
  }

  def "player location state batches queue follower and group updates"() {
    given:
    Map batch = [kind: 'state', playerDni: coordinator.deviceNetworkId, coordinatorId: 'RINCON_COORD',
      followerDNIs: [follower.deviceNetworkId], followerAttrs: [status: 'playing'],
      playbackAttrs: [status: 'playing']]

    when:
    appScript.playerStateUpdate(batch)
    appScript.drainQueuedPlayerStateUpdates()

    then:
    appScript.state.pendingChildEvents[follower.deviceNetworkId].status == 'playing'
    appScript.state.pendingChildEvents[follower.deviceNetworkId].transportStatus == 'playing'
    appScript.state.pendingGroupStateUpdates[group.deviceNetworkId].status == 'playing'
  }

  def "delayed coordinator state fanout is dropped after follower leaves topology"() {
    given:
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    Map batch = [kind: 'state', playerDni: coordinator.deviceNetworkId, coordinatorId: 'RINCON_COORD',
      followerDNIs: [follower.deviceNetworkId], followerAttrs: [currentTrackName: 'Old group track'],
      playbackAttrs: [currentTrackName: 'Old group track']]

    when:
    appScript.playerStateUpdateHandler(batch)

    then:
    appScript.state.pendingChildEvents == null
    appScript.logs.any { String message -> message.contains('Ignoring stale fanout') }
  }

  def "coordinator roster growth queues the playback snapshot to the new follower"() {
    given:
    coordinator.currentValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.currentValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW'
    coordinator.currentValues.groupId = 'GROUP-1'

    when:
    appScript.topologyAttributeHandler([device: coordinator, name: 'groupPlayerIds',
      value: 'RINCON_COORD,RINCON_FOLLOW'] as Event)

    then:
    appScript.state.pendingChildEvents[follower.deviceNetworkId].status == 'playing'
    appScript.state.pendingChildEvents[follower.deviceNetworkId].transportStatus == 'playing'
  }

  def "player state drain yields after its fixed item budget"() {
    given:
    (0..<20).each { Integer index ->
      appScript.playerStateUpdate([kind: "ignored-${index}", playerDni: coordinator.deviceNetworkId])
    }

    when:
    appScript.drainQueuedPlayerStateUpdates()

    then:
    appScript.state.pendingPlayerStateUpdates.size() == 8
    appScript.state.playerStateUpdateDrainActive == false
    appScript.scheduled.any { List job -> job[1] == 'drainQueuedPlayerStateUpdates' }

    when:
    appScript.drainQueuedPlayerStateUpdates()

    then:
    !appScript.state.containsKey('pendingPlayerStateUpdates')
    appScript.state.playerStateUpdateDrainActive == false
  }

  def "player state arrivals during a drain are deferred to the next invocation"() {
    given:
    List<Boolean> activeStates = []
    appScript.metaClass.playerStateUpdateHandler = { Map payload ->
      activeStates << (delegate.state.playerStateUpdateDrainActive == true)
      if(payload.kind == 'seed') {
        delegate.playerStateUpdate([kind: 'arrived-during-drain', playerDni: coordinator.deviceNetworkId])
      }
    }
    appScript.playerStateUpdate([kind: 'seed', playerDni: coordinator.deviceNetworkId])

    when:
    appScript.drainQueuedPlayerStateUpdates()

    then:
    activeStates == [true]
    appScript.state.pendingPlayerStateUpdates*.kind == ['arrived-during-drain']
    appScript.state.playerStateUpdateDrainActive == false
    appScript.scheduled.any { List job -> job[1] == 'drainQueuedPlayerStateUpdates' }
  }

  def "follower metadata batch repairs stale public attributes despite a matching last-sent cache"() {
    given:
    follower.currentValues.currentTrackName = 'Not Available'
    follower.currentValues.currentArtistName = 'Not Available'
    follower.currentValues.trackDescription = 'Not Available'
    appScript.state.lastSentChildState = [(follower.deviceNetworkId): [
      currentTrackName: 'Peepers On Crane Pond',
      currentArtistName: 'Adirondack Environments',
      trackDescription: 'Peepers On Crane Pond by Adirondack Environments'
    ]]
    Map batch = [kind: 'state', playerDni: coordinator.deviceNetworkId, coordinatorId: 'RINCON_COORD',
      followerDNIs: [follower.deviceNetworkId], followerAttrs: [
        currentTrackName: 'Peepers On Crane Pond',
        currentArtistName: 'Adirondack Environments',
        trackDescription: 'Peepers On Crane Pond by Adirondack Environments'
      ]]

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson(batch)] as Event)
    appScript.drainQueuedChildEvents()
    Map<String, Object> attributes = (Map<String, Object>)new JsonSlurper().parseText(
      follower.refreshedPlaybackUpdates.last())

    then:
    attributes.currentTrackName == 'Peepers On Crane Pond'
    attributes.currentArtistName == 'Adirondack Environments'
    attributes.trackDescription == 'Peepers On Crane Pond by Adirondack Environments'
  }

  def "child state drain dispatches a bounded number of children and reschedules the remainder"() {
    given:
    appScript.queueChildStateBatch(follower.deviceNetworkId, [transportStatus: 'playing'])
    appScript.queueChildStateBatch(extra.deviceNetworkId, [transportStatus: 'paused'])
    appScript.queueChildStateBatch(coordinator.deviceNetworkId, [transportStatus: 'stopped'])

    when:
    appScript.drainQueuedChildEvents()

    then:
    follower.refreshedPlaybackUpdates.size() == 1
    extra.refreshedPlaybackUpdates.size() == 1
    coordinator.refreshedPlaybackUpdates.isEmpty()
    appScript.state.pendingChildEvents == [(coordinator.deviceNetworkId): [transportStatus: 'stopped']]
    appScript.state.childStateDrainScheduled == true
    appScript.scheduled.any { List call -> call[1] == 'drainQueuedChildEvents' }

    when:
    appScript.drainQueuedChildEvents()

    then:
    coordinator.refreshedPlaybackUpdates.size() == 1
    appScript.state.pendingChildEvents == null
    appScript.state.childStateDrainScheduled == null
  }

  def "child state enqueue re-arms a drain when the scheduled marker is stale"() {
    given:
    appScript.state.pendingChildEvents = [(follower.deviceNetworkId): [transportStatus: 'playing']]
    appScript.state.childStateDrainScheduled = true

    when:
    appScript.queueChildStateBatch(follower.deviceNetworkId, [status: 'playing'])

    then:
    appScript.state.childStateDrainScheduled == true
    appScript.scheduled.any { List call -> call[1] == 'drainQueuedChildEvents' }
  }

  def "initialization request clears only that player's child publication cache"() {
    given:
    appScript.state.pendingChildEvents = [
      (follower.deviceNetworkId): [status: 'stopped'],
      (extra.deviceNetworkId): [status: 'paused']
    ]
    appScript.state.lastSentChildState = [
      (follower.deviceNetworkId): [status: 'stopped'],
      (extra.deviceNetworkId): [status: 'paused']
    ]
    Map request = [kind: 'initializationRequested', playerDni: follower.deviceNetworkId]

    when:
    appScript.playerStateUpdateHandler(request)

    then:
    !appScript.state.pendingChildEvents.containsKey(follower.deviceNetworkId)
    !appScript.state.lastSentChildState.containsKey(follower.deviceNetworkId)
    appScript.state.pendingChildEvents[extra.deviceNetworkId].status == 'paused'
    appScript.state.lastSentChildState[extra.deviceNetworkId].status == 'paused'
    appScript.scheduled.any { List call -> call[1] == 'drainQueuedChildEvents' }
  }

  def "child state drain preserves events queued while a child batch is being delivered"() {
    given:
    follower.metaClass.applyStateBatch = { String json ->
      appScript.queueChildStateBatch(coordinator.deviceNetworkId, [transportStatus: 'playing'])
      follower.refreshedPlaybackUpdates << json
    }
    appScript.queueChildStateBatch(follower.deviceNetworkId, [transportStatus: 'playing'])
    appScript.queueChildStateBatch(extra.deviceNetworkId, [transportStatus: 'paused'])
    appScript.queueChildStateBatch(coordinator.deviceNetworkId, [transportStatus: 'stopped'])

    when:
    appScript.drainQueuedChildEvents()

    then:
    follower.refreshedPlaybackUpdates.size() == 1
    extra.refreshedPlaybackUpdates.size() == 1
    coordinator.refreshedPlaybackUpdates.isEmpty()
    appScript.state.pendingChildEvents == [(coordinator.deviceNetworkId): [transportStatus: 'playing']]
    appScript.state.childStateDrainScheduled == true
    appScript.scheduled.any { List call -> call[1] == 'drainQueuedChildEvents' }
  }

  def "failed child delivery retries behind unprocessed children"() {
    given:
    follower.metaClass.applyStateBatch = { String json -> throw new RuntimeException('simulated failure') }
    appScript.queueChildStateBatch(follower.deviceNetworkId, [transportStatus: 'playing'])
    appScript.queueChildStateBatch(extra.deviceNetworkId, [transportStatus: 'paused'])
    appScript.queueChildStateBatch(coordinator.deviceNetworkId, [transportStatus: 'stopped'])

    when:
    appScript.drainQueuedChildEvents()

    then:
    extra.refreshedPlaybackUpdates.size() == 1
    appScript.state.pendingChildEvents.keySet().toList() == [
      coordinator.deviceNetworkId, follower.deviceNetworkId
    ]

    when:
    appScript.drainQueuedChildEvents()

    then:
    coordinator.refreshedPlaybackUpdates.size() == 1
    appScript.state.pendingChildEvents.containsKey(follower.deviceNetworkId)
    appScript.state.childStateDrainScheduled == true
  }

  def "player device state event carries its JSON payload in the event value"() {
    given:
    Map batch = [kind: 'playbackObservation', playerDni: coordinator.deviceNetworkId,
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now()]

    when:
    appScript.playerStateUpdateHandler([value: JsonOutput.toJson(batch)] as Event)

    then:
    appScript.state.sonosPlayerPlaybackObservations.RINCON_COORD.playbackState == 'PLAYBACK_STATE_PLAYING'
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

  def "player app request callback returns the active-stream decision"() {
    given:
    appScript.metaClass.handlePlayerActiveStreamTts = { String playerDni, String uri,
        BigDecimal volume, Integer duration, String correlationId ->
      playerDni == coordinator.deviceNetworkId && uri == 'http://example.test/tts.mp3' &&
        volume == 5G && duration == 3 && correlationId == 'tts-correlation'
    }

    when:
    Boolean accepted = appScript.playerAppRequest([
      version: 1, channel: 'playerAppRequest', action: 'activeStreamTts',
      sourceDni: coordinator.deviceNetworkId, requestId: 'event-request-1',
      args: [requestId: 'tts-request-1', uri: 'http://example.test/tts.mp3', volume: '5',
        duration: 3, correlationId: 'tts-correlation']
    ])

    then:
    accepted == true
  }

  def "player app cache refresh requests update driver roster and policy snapshots"() {
    given:
    List<String> refreshed = []
    appScript.metaClass.refreshStaticDriverActivePlayerRoster = { -> refreshed << 'roster' }
    appScript.settings.groupAnnouncementMode = 'alwaysNonInterrupting'
    appScript.settings.highPriorityAnnouncementMode = 'alwaysCoordinatorStream'

    when:
    appScript.playerAppRequestHandler([value: JsonOutput.toJson([
      version: 1, channel: 'playerAppRequest', action: 'refreshRoster',
      sourceDni: coordinator.deviceNetworkId, requestId: 'roster-request', args: [:]
    ])] as Event)
    appScript.playerAppRequestHandler([value: JsonOutput.toJson([
      version: 1, channel: 'playerAppRequest', action: 'refreshPolicy',
      sourceDni: coordinator.deviceNetworkId, requestId: 'policy-request', args: [:]
    ])] as Event)

    then:
    refreshed == ['roster']
    coordinator.dataValues.useAudioClipForPlayer == 'true'
    coordinator.dataValues.useAudioClipForHighPriorityTts == 'false'
    coordinator.dataValues.useAudioClipForAirPlay == 'true'
  }

  def "unrelated player state updates do not refresh announcement policies"() {
    given:
    appScript.metaClass.flushGroupDeviceState = { String coordinatorId, Map volumeAttrs, Map playbackAttrs -> }

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson([
      kind: 'state', playerDni: coordinator.deviceNetworkId, coordinatorId: 'RINCON_COORD',
      playbackAttrs: [volume: 10]
    ])] as Event)

    then:
    !appScript.scheduled.any { List job -> job[1] == 'drainAnnouncementPolicyRefreshQueue' }
    !coordinator.dataValues.containsKey('useAudioClipForPlayer')
    !appScript.logs.any { String message -> message.contains('Player announcement policy evaluated') }
  }

  def "playback policy refresh coalesces and updates only the affected coordinator group"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.highPriorityAnnouncementMode = 'alwaysCoordinatorStream'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson([
      kind: 'playbackObservation', playerDni: coordinator.deviceNetworkId,
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now()
    ])] as Event)
    appScript.drainAnnouncementPolicyRefreshQueue()

    then:
    appScript.scheduled.count { List job -> job[1] == 'drainAnnouncementPolicyRefreshQueue' } == 1
    coordinator.dataValues.useAudioClipForPlayer == 'true'
    follower.dataValues.useAudioClipForPlayer == 'true'
    coordinator.dataValues.useAudioClipForHighPriorityTts == 'false'
    follower.dataValues.useAudioClipForHighPriorityTts == 'false'
    !extra.dataValues.containsKey('useAudioClipForPlayer')
    !appScript.logs.any { String message -> message.contains('Player announcement route check') }

    when:
    appScript.scheduled.clear()
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson([
      kind: 'playbackObservation', playerDni: coordinator.deviceNetworkId,
      playbackState: 'PLAYBACK_STATE_BUFFERING', playbackObservedAt: appScript.now() + 1000L
    ])] as Event)

    then:
    !appScript.scheduled.any { List job -> job[1] == 'drainAnnouncementPolicyRefreshQueue' }
  }

  def "changing one announcement priority refreshes its policy without changing the other"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysNonInterrupting'
    appScript.settings.highPriorityAnnouncementMode = 'alwaysNonInterrupting'
    appScript.refreshPlayerAnnouncementPolicySnapshots(true)

    when:
    appScript.handleNewUiSettingChange('newUiSetting|highPriorityAnnouncementMode|alwaysCoordinatorStream')

    then:
    coordinator.dataValues.useAudioClipForPlayer == 'true'
    coordinator.dataValues.useAudioClipForHighPriorityTts == 'false'
    follower.dataValues.useAudioClipForPlayer == 'true'
    follower.dataValues.useAudioClipForHighPriorityTts == 'false'
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

  def "duplicate boolean setting toggle is ignored without updating or firing an event"() {
    given:
    appScript.settings.automaticTtsTopologyChanges = true

    when:
    appScript.handleNewUiSettingChange('newUiSetting|automaticTtsTopologyChanges|true')

    then:
    appScript.app.updatedSettings.isEmpty()
    appScript.app.events.isEmpty()
    appScript.logs.any { String message -> message.contains('Ignoring duplicate custom setting') }

    when:
    appScript.handleNewUiSettingChange('newUiSetting|automaticTtsTopologyChanges|false')

    then:
    appScript.app.updatedSettings['automaticTtsTopologyChanges'] == false
    appScript.app.events.count { Map event -> event.name == 'newUiSettings' } == 1
  }

  def "unchanged topology reports do not recalculate announcement policies"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.highPriorityAnnouncementMode = 'automatic'
    appScript.refreshPlayerAnnouncementPolicySnapshots(true)
    appScript.scheduled.clear()
    appScript.logs.clear()

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson([
      kind: 'topology', playerDni: coordinator.deviceNetworkId, groupId: 'GROUP-1',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ])] as Event)

    then:
    !appScript.scheduled.any { List job -> job[1] == 'drainAnnouncementPolicyRefreshQueue' }
    !appScript.logs.any { String message -> message.contains('Player announcement policy evaluated') }
  }

  def "player position preference suppresses position fanout to disabled followers"() {
    given:
    follower.dataValues.enableTrackPositionUpdates = 'false'
    Map batch = [kind: 'state', playerDni: coordinator.deviceNetworkId, coordinatorId: 'RINCON_COORD',
      followerDNIs: [follower.deviceNetworkId],
      followerAttrs: [trackPosition: 32L, trackPositionUpdated: 123456L, status: 'playing'],
      playbackAttrs: [trackPosition: 32L, trackPositionUpdated: 123456L, status: 'playing']]

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson(batch)] as Event)

    then:
    appScript.state.pendingChildEvents[follower.deviceNetworkId].status == 'playing'
    !appScript.state.pendingChildEvents[follower.deviceNetworkId].containsKey('trackPosition')
    !appScript.state.pendingChildEvents[follower.deviceNetworkId].containsKey('trackPositionUpdated')
    appScript.state.pendingGroupStateUpdates[group.deviceNetworkId].trackPosition == 32L
    appScript.state.pendingGroupStateUpdates[group.deviceNetworkId].trackPositionUpdated == 123456L
  }

  def "player playback observations are cached in app State Variables"() {
    given:
    Map batch = [kind: 'playbackObservation', playerDni: coordinator.deviceNetworkId,
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now(),
      audioSource: 'x-sonos-htastream:RINCON_COORD:spdif',
      signalStatus: 'no-signal', signalObservedAt: appScript.now()]

    when:
    appScript.playerStateUpdate(batch)
    appScript.drainQueuedPlayerStateUpdates()

    then:
    appScript.state.sonosPlayerPlaybackObservations.RINCON_COORD.playbackState == 'PLAYBACK_STATE_PLAYING'
    appScript.state.sonosPlayerPlaybackObservations.RINCON_COORD.signalStatus == 'no-signal'
    appScript.getPlayerPlaybackActivity(coordinator).active == false
    appScript.getPlayerPlaybackActivity(coordinator).signal == 'fresh-hdmi-no-signal'
  }

  def "temporary topology comparison ignores a Sonos group ID rotation for the same roster"() {
    expect:
    appScript.announcementTopologyObservationMatches(
      [groupId: 'RINCON_COORD:2727180016', coordinatorId: 'RINCON_COORD',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
      [groupId: 'RINCON_COORD:2727180017', coordinatorId: 'RINCON_COORD',
        playerIds: ['RINCON_FOLLOW', 'RINCON_COORD']])
    !appScript.announcementTopologyObservationMatches(
      [groupId: 'RINCON_COORD:2727180016', coordinatorId: 'RINCON_COORD',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
      [groupId: 'RINCON_OTHER:2727180017', coordinatorId: 'RINCON_OTHER',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']])
    !appScript.announcementTopologyObservationMatches(
      [groupId: 'RINCON_COORD:2727180016', coordinatorId: 'RINCON_COORD',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
      [groupId: 'RINCON_COORD:2727180017', coordinatorId: 'RINCON_COORD',
        playerIds: ['RINCON_COORD']])
  }

  def "active group TTS prepares every player before releasing synchronized playback"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.isGrouped = 'on'
    coordinator.currentValues.isGroupCoordinator = 'on'
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    appScript.state.sonosPlayerPlaybackObservations = [RINCON_COORD: [
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now()]]
    String correlationId = 'test-tts-correlation'

    when:
    appScript.processGroupCommandRequest([request: request('playAudioClip', [
      uri: 'http://example.test/tts.mp3', volume: '15', duration: 2,
      correlationId: correlationId
    ])])

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.find { it.name == 'playerPrepareAudioClipForSynchronizedStart' }?.correlationId == correlationId
    }
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.find { it.name == 'playerPrepareAudioClipForSynchronizedStart' }?.deferStart == true
    }
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'startPreparedAudioClip' }
    }
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.every { it.name != 'playerLoadAudioClip' }
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
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      Map prepared = player.commands.find { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
      prepared?.chimeBeforeTTS == true && prepared?.holdAfterChime == true
    }
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'prepareAudioClipForDispatch' } &&
        player.commands.any { it.name == 'startPreparedAudioClip' }
    }
  }

  def "successive queued TTS skips its group chime when suppression is enabled"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.suppressChimeOnSuccessiveTTS = true
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': [
      active: [correlationId: 'active-tts', isTts: true], pending: []]]

    when:
    appScript.enqueueGroupAnnouncement(group, coordinator, [coordinator, follower],
      'playAudioClip', [uri: 'http://example.test/tts.mp3', isTts: true,
        chimeBeforeStandardPriorityTTS: true], 'successive-tts')

    then:
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].pending.last().chimeBeforeStandardPriorityTTS == false
    appScript.logs.any { it.contains('Suppressing chime for successive TTS announcement successive-tts') }
  }

  def "group audio requests queue then drain to every player"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.isGrouped = 'on'
    coordinator.currentValues.isGroupCoordinator = 'on'
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    appScript.state.sonosPlayerPlaybackObservations = [RINCON_COORD: [
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now()]]
    Map groupRequest = request('playAudioClip', [uri: 'http://example.test/tts.mp3', volume: '15', duration: 2])

    when:
    appScript.enqueueGroupCommandRequest(groupRequest)

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player -> player.commands.empty }
    appScript.scheduled.any { it[1] == 'drainGroupCommandRequests' }

    when:
    appScript.drainGroupCommandRequests()

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' } &&
        player.commands.any { it.name == 'startPreparedAudioClip' }
    }
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.command == 'playAudioClip'
  }

  def "idle group announcement starts one stream on its coordinator"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_IDLE'
    coordinator.currentValues.transportStatus = 'stopped'
    Map groupRequest = request('playAudioClip', [uri: 'http://example.test/tts.mp3', volume: '15',
      duration: 42, correlationId: 'idle-announcement'])

    when:
    appScript.processGroupCommandRequest([request: groupRequest])

    then:
    coordinator.commands.find { it.name == 'playerPlayGroupAnnouncementStream' } == [
      name: 'playerPlayGroupAnnouncementStream', uri: 'http://example.test/tts.mp3',
      volume: 15G, correlationId: 'idle-announcement']
    !follower.commands.any { it.name in ['playerPrepareAudioClipForSynchronizedStart', 'playerLoadAudioClipHighPriority'] }
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.mode == 'COORDINATOR_STREAM'
  }

  def "group announcement watchdog doubles the playback allowance and uses a bounded unknown-duration fallback"() {
    expect:
    appScript.getGroupAnnouncementWatchdogTimeoutSeconds(item) == timeoutSeconds

    where:
    item                 | timeoutSeconds
    [duration: 0]         | 360
    [:]                   | 360
    [duration: 10]        | 120
    [duration: 42]        | 132
    [duration: 200]       | 290
  }

  def "automatic topology disabled uses synchronized clips for an ungrouped target roster"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.automaticTtsTopologyChanges = false
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_IDLE'
    coordinator.currentValues.transportStatus = 'stopped'
    Map item = [correlationId: 'auto-off-clip', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [pending: [], active: item]
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'G1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
      RINCON_FOLLOW: [groupId: 'G2', coordinatorId: 'RINCON_FOLLOW', playerIds: ['RINCON_FOLLOW']]
    ]]

    when:
    appScript.dispatchGroupAnnouncementByPolicy('GROUP-DNI', queue, item, [coordinator, follower], coordinator, snapshot)

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    }
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    !appScript.state.containsKey('groupAnnouncementTopologyTransactions')
  }

  def "Always standard stream with topology changes off only targets the current coordinator"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    appScript.settings.automaticTtsTopologyChanges = false
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_IDLE'
    coordinator.currentValues.transportStatus = 'stopped'
    Map groupRequest = request('playAudioClip', [uri: 'http://example.test/tts.mp3', volume: '10',
      duration: 22, correlationId: 'coordinator-only'])
    appScript.state.pendingGroupCommandRequests = [:]

    when:
    appScript.processGroupCommandRequest([request: groupRequest])

    then:
    coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    !follower.commands.any { it.name in ['playerPlayGroupAnnouncementStream', 'playerPrepareAudioClipForSynchronizedStart'] }
    !appScript.state.containsKey('groupAnnouncementTopologyTransactions')
  }

  def "disabling temporary stream holders preserves an active Sonos stream with audioClip"() {
    given:
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    appScript.settings.automaticTtsTopologyChanges = true
    appScript.settings.useTemporaryStreamHolder = false
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'x-sonos-http:song%3a12345.mp4?sid=204&flags=8232&sn=9'
    Map item = [correlationId: 'holder-disabled-fallback', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '14', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']]
    Map queue = [active: item, pending: []]

    when:
    appScript.dispatchGroupAnnouncementByPolicy('GROUP-DNI', queue, item,
      [coordinator], coordinator, null)

    then:
    item.mode == 'AUDIO_CLIP'
    coordinator.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    !appScript.state.containsKey('groupAnnouncementTopologyTransactions')
    appScript.logs.any { it.contains('Temporary stream holder handoff is unavailable or disabled') }
  }

  def "Automatic mode permits pulling an active follower when its coordinator remains outside the target"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.automaticTtsTopologyChanges = true
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_IDLE'
    coordinator.currentValues.transportStatus = 'stopped'
    extra.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    extra.currentValues.transportStatus = 'playing'
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'G1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
      RINCON_FOLLOW: [groupId: 'G2', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA', 'RINCON_FOLLOW']],
      RINCON_EXTRA: [groupId: 'G2', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA', 'RINCON_FOLLOW']]
    ]]

    when:
    Boolean safe = appScript.canSafelyRegroupForAutomaticAnnouncement([coordinator, follower],
      'RINCON_COORD', snapshot)

    then:
    safe
  }

  def "fresh no-signal HDMI metadata overrides playing transport and Playback API state"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.automaticTtsTopologyChanges = true
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'x-sonos-htastream:RINCON_COORD:spdif'
    appScript.state.sonosPlayerPlaybackObservations = [RINCON_COORD: [
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now(),
      audioSource: 'x-sonos-htastream:RINCON_COORD:spdif',
      signalStatus: 'no-signal', signalObservedAt: appScript.now()]]
    Map item = [correlationId: 'hdmi-tv-off', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [active: item, pending: []]
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'G1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
      RINCON_FOLLOW: [groupId: 'G2', coordinatorId: 'RINCON_FOLLOW', playerIds: ['RINCON_FOLLOW']]
    ]]

    when:
    Boolean active = appScript.isPlayerActivelyPlaying(coordinator)
    appScript.dispatchGroupAnnouncementByPolicy('GROUP-DNI', queue, item,
      [coordinator, follower], coordinator, snapshot)

    then:
    !active
    appScript.state.groupAnnouncementTopologyTransactions['hdmi-tv-off'] != null
    appScript.state.groupAnnouncementTopologyTransactions['hdmi-tv-off'].forced == false
    ![coordinator, follower].any { SonosAppPlayerDouble player ->
      player.commands.any { it.name in ['playerPlayGroupAnnouncementStream', 'playerPrepareAudioClipForSynchronizedStart'] }
    }
    appScript.logs.any { it.toString().contains('playbackSignal=fresh-hdmi-no-signal') }
  }

  def "fresh topology snapshot gates automatic regrouping before standard stream dispatch"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.automaticTtsTopologyChanges = true
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    follower.dataValues.groupId = 'GROUP-2'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    Map groupRequest = request('playAudioClip', [uri: 'http://example.test/tts.mp3', volume: '10',
      duration: 25, correlationId: 'fresh-topology'])
    Long observedAt = appScript.now() + 1000L

    when:
    appScript.processGroupCommandRequest([request: groupRequest])
    appScript.processTopologyPayload(coordinator, [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD'], observedAt: observedAt, playerDni: coordinator.deviceNetworkId])
    appScript.processTopologyPayload(follower, [groupId: 'GROUP-2', coordinatorId: 'RINCON_FOLLOW',
      playerIds: ['RINCON_FOLLOW'], observedAt: observedAt + 1L, playerDni: follower.deviceNetworkId])
    appScript.processTopologyPayload(extra, [groupId: 'GROUP-3', coordinatorId: 'RINCON_EXTRA',
      playerIds: ['RINCON_EXTRA'], observedAt: observedAt + 2L, playerDni: extra.deviceNetworkId])

    then:
    appScript.state.groupAnnouncementTopologyTransactions['fresh-topology'].phase == 'GROUPING_TARGET'
    appScript.getActiveGroupOperation().announcementTransactionId == 'fresh-topology'
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    !follower.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
  }

  def "automatic topology transaction splits a conflicting target subgroup before createGroup"() {
    given:
    appScript.settings.groupAnnouncementMode = 'automatic'
    appScript.settings.automaticTtsTopologyChanges = true
    coordinator.dataValues.groupId = 'SUBGROUP'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW'
    follower.dataValues.groupId = 'SUBGROUP'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_COORD,RINCON_FOLLOW'
    extra.dataValues.groupId = 'EXTRA-SINGLE'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    Map item = [correlationId: 'split-largest-subgroup', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']]
    Map queue = [active: item, pending: []]
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'SUBGROUP', coordinatorId: 'RINCON_FOLLOW',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
      RINCON_FOLLOW: [groupId: 'SUBGROUP', coordinatorId: 'RINCON_FOLLOW',
        playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']],
      RINCON_EXTRA: [groupId: 'EXTRA-SINGLE', coordinatorId: 'RINCON_EXTRA',
        playerIds: ['RINCON_EXTRA']]
    ]]

    when:
    appScript.beginGroupAnnouncementTopologyTransaction('GROUP-DNI', queue, item,
      [coordinator, follower, extra], snapshot, false)

    then:
    Map transaction = appScript.state.groupAnnouncementTopologyTransactions['split-largest-subgroup']
    transaction.steps*.phase == ['PREPARE_SOURCE_GROUP', 'GROUP_TARGET']
    transaction.steps[0].playerIds == ['RINCON_FOLLOW']
    transaction.steps[0].coordinatorId == 'RINCON_FOLLOW'
    transaction.phase == 'PREPARING_SOURCE_GROUP'
    appScript.getActiveGroupOperation().announcementTopologyPhase == 'PREPARE_SOURCE_GROUP'
    ![coordinator, follower, extra].any { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    }
  }

  def "stream handoff carries the captured audio source into its restoration transaction"() {
    given:
    String originalAudioSource = 'x-sonos-http:song%3a12345.mp4?sid=204&flags=8232&sn=9'
    coordinator.dataValues.groupId = 'SOURCE-GROUP'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    extra.dataValues.groupId = 'HOLDER-GROUP'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    Map item = [correlationId: 'stream-source-captured', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '14', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD'],
      coordinatorAudioSource: originalAudioSource]
    Map queue = [active: item, pending: []]
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'SOURCE-GROUP', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
      RINCON_EXTRA: [groupId: 'HOLDER-GROUP', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA']]
    ]]

    when:
    appScript.beginGroupAnnouncementTopologyTransaction('GROUP-DNI', queue, item,
      [coordinator], snapshot, false, [playerId: 'RINCON_EXTRA'])

    then:
    appScript.state.groupAnnouncementTopologyTransactions['stream-source-captured'].coordinatorAudioSource == originalAudioSource
  }

  def "stream handoff mutes an unmuted holder and restores its prior unmuted state"() {
    given:
    coordinator.dataValues.groupId = 'SOURCE-GROUP'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    extra.dataValues.groupId = 'HOLDER-GROUP'
    extra.dataValues.groupCoordinatorId = 'RINCON_EXTRA'
    extra.dataValues.groupPlayerIds = 'RINCON_EXTRA'
    extra.currentValues.mute = 'unmuted'
    Map item = [correlationId: 'stream-holder-unmuted', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '14', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']]
    Map queue = [active: item, pending: []]
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'SOURCE-GROUP', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']],
      RINCON_EXTRA: [groupId: 'HOLDER-GROUP', coordinatorId: 'RINCON_EXTRA', playerIds: ['RINCON_EXTRA']]
    ]]

    when:
    appScript.beginGroupAnnouncementTopologyTransaction('GROUP-DNI', queue, item,
      [coordinator], snapshot, false, [playerId: 'RINCON_EXTRA', previousMuteState: 'unmuted'])

    then:
    extra.commands.findAll { it.name in ['mute', 'unmute'] }*.name == ['mute']
    appScript.state.groupAnnouncementTopologyTransactions['stream-holder-unmuted'].holderPreviousMuteState == 'unmuted'

    when:
    appScript.restoreStreamHolderMuteState(appScript.state.groupAnnouncementTopologyTransactions['stream-holder-unmuted'])

    then:
    extra.commands.findAll { it.name in ['mute', 'unmute'] }*.name == ['mute', 'unmute']
  }

  def "stream handoff leaves a holder muted when it was muted before use"() {
    given:
    extra.currentValues.mute = 'muted'
    Map transaction = [holderId: 'RINCON_EXTRA', holderPreviousMuteState: 'muted']

    when:
    appScript.restoreStreamHolderMuteState(transaction)

    then:
    extra.commands*.name == ['mute']
  }

  def "failed automatic source subgroup split falls back to clips for all target players"() {
    given:
    Map item = [correlationId: 'split-fallback', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [active: item, pending: []]
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': queue]
    Map transaction = [correlationId: 'split-fallback', groupDni: 'GROUP-DNI',
      phase: 'PREPARING_SOURCE_GROUP', forced: false, item: item, originalGroups: [],
      steps: [[phase: 'PREPARE_SOURCE_GROUP', playerIds: ['RINCON_FOLLOW']]],
      currentStep: 0, operationId: 'split-op']
    appScript.state.groupAnnouncementTopologyTransactions = ['split-fallback': transaction]
    Map operation = [operationId: 'split-op', announcementTransactionId: 'split-fallback']
    appScript.saveActiveGroupOperation(operation)

    when:
    appScript.handleAnnouncementTopologyOperationFinished(operation, 'FAILED')

    then:
    appScript.state.groupAnnouncementTopologyTransactions['split-fallback'].phase == 'STREAMING'
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.mode == 'AUDIO_CLIP'
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' } &&
        player.commands.any { it.name == 'startPreparedAudioClip' }
    }
    appScript.logs.any { it.contains('falling back to audioClip') }
  }

  def "verified temporary topology promotes the active queue item to coordinator stream"() {
    given:
    Map item = [correlationId: 'verified-topology', mode: 'TOPOLOGY_PREPARING',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 22,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [active: [correlationId: 'verified-topology', mode: 'TOPOLOGY_PREPARING',
      uri: item.uri, volume: item.volume, duration: item.duration,
      coordinatorId: item.coordinatorId, playerIds: item.playerIds], pending: []]
    Map transaction = [correlationId: 'verified-topology', groupDni: 'GROUP-DNI',
      coordinatorId: 'RINCON_COORD', targetPlayerIds: item.playerIds,
      originalGroups: [], steps: [], currentStep: 0, phase: 'GROUPING_TARGET', item: item]
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': queue]
    appScript.state.groupAnnouncementTopologyTransactions = ['verified-topology': transaction]

    when:
    appScript.startNextAnnouncementTopologyStep(transaction)

    then:
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.mode == 'COORDINATOR_STREAM'
    appScript.state.groupAnnouncementTopologyTransactions['verified-topology'].phase == 'STREAMING'
    coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
  }

  def "Always standard stream prepares a replacement coordinator for an active outside group"() {
    given:
    SonosAppPlayerDouble outsideTwo = createPlayer('RINCON_OUTSIDE2', 'G2', 'RINCON_COORD',
      'RINCON_COORD,RINCON_EXTRA,RINCON_OUTSIDE2', false)
    appScript.children = [group, coordinator, follower, extra, outsideTwo]
    appScript.app.children = appScript.children
    appScript.settings.groupAnnouncementMode = 'alwaysCoordinatorStream'
    appScript.settings.automaticTtsTopologyChanges = true
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    coordinator.currentValues.transportStatus = 'playing'
    Map item = [correlationId: 'forced-topology', command: 'playAudioClip',
      uri: 'http://example.test/tts.mp3', volume: '10', duration: 20,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    Map queue = [pending: [], active: item]
    Map snapshot = [observations: [
      RINCON_COORD: [groupId: 'G2', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_OUTSIDE2']],
      RINCON_EXTRA: [groupId: 'G2', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_OUTSIDE2']],
      RINCON_OUTSIDE2: [groupId: 'G2', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_OUTSIDE2']],
      RINCON_FOLLOW: [groupId: 'G3', coordinatorId: 'RINCON_FOLLOW', playerIds: ['RINCON_FOLLOW']]
    ]]

    when:
    appScript.dispatchGroupAnnouncementByPolicy('GROUP-DNI', queue, item,
      [coordinator, follower], coordinator, snapshot)

    then:
    Map transaction = appScript.state.groupAnnouncementTopologyTransactions['forced-topology']
    transaction.forced == true
    transaction.steps[0].phase == 'PREPARE_OUTSIDE_GROUP'
    transaction.steps[0].coordinatorId == 'RINCON_EXTRA'
    transaction.steps[0].playerIds == ['RINCON_EXTRA', 'RINCON_OUTSIDE2']
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
  }

  def "queued announcement stays on coordinator stream after the previous restore reports completion"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_IDLE'
    coordinator.currentValues.transportStatus = 'stopped'
    Map firstRequest = request('playAudioClip', [uri: 'http://example.test/first.mp3', volume: '15',
      duration: 42, correlationId: 'first-announcement'])
    appScript.processGroupCommandRequest([request: firstRequest])
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'http://example.test/first.mp3'
    Map queue = appScript.state.groupAnnouncementQueues['GROUP-DNI'] as Map
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
      !player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    }
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.mode == 'COORDINATOR_STREAM'
    appScript.logs.any { it.contains('treating the group as otherwise idle') }
    coordinator.commands.any { it.name == 'restoreGroupAnnouncementPlayback' && it.correlationId == 'first-announcement' }

    where:
    restored << [true, false]
  }

  def "expired queued announcements are discarded before dispatch"() {
    given:
    makeAnnouncementRosterExact()
    appScript.settings.maximumTtsRetention = '60'
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': [pending: [[
      correlationId: 'stale-announcement', command: 'playAudioClip',
      uri: 'http://example.test/stale.mp3', volume: '15', duration: 5,
      submittedAt: appScript.now() - (2L * 60L * 60L * 1000L),
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ]]]]

    when:
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' || it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    !appScript.state.groupAnnouncementQueues.containsKey('GROUP-DNI')
    appScript.logs.any { it.contains('Discarding expired queued announcement stale-announcement') }
  }

  def "announcement stays queued while a target speaker is offline"() {
    given:
    makeAnnouncementRosterExact()
    appScript.state.offlineSonosPlayers = ['DNI-RINCON_FOLLOW': [id: 'RINCON_FOLLOW', retryDelay: 30]]
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': [pending: [[
      correlationId: 'offline-announcement', command: 'playAudioClip',
      uri: 'http://example.test/offline.mp3', volume: '15', duration: 5,
      submittedAt: appScript.now(), coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ]]]]

    when:
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active == null
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].pending*.correlationId == ['offline-announcement']
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' || it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    appScript.scheduled.any { it[1] == 'startNextGroupAnnouncementCallback' }
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
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'https://music.example.test/external.mp3'
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': [ownStreamUri: 'http://example.test/first.mp3', pending: [[
      correlationId: 'external-source-announcement', command: 'playAudioClip',
      uri: 'http://example.test/next.mp3', volume: '15', duration: 42,
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]]]]

    when:
    appScript.startNextGroupAnnouncement('GROUP-DNI')

    then:
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    }
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.mode == 'AUDIO_CLIP'
  }

  def "interrupted coordinator stream routes queued Automatic announcement to audioClip despite stale source state"() {
    given:
    makeAnnouncementRosterExact()
    coordinator.currentValues.playbackState = 'PLAYBACK_STATE_PLAYING'
    coordinator.currentValues.transportStatus = 'playing'
    coordinator.currentValues.audioSource = 'http://example.test/first.mp3'
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': [ownStreamUri: 'http://example.test/first.mp3',
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
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' }
    }
    !coordinator.commands.any { it.name == 'playerPlayGroupAnnouncementStream' }
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active.mode == 'AUDIO_CLIP'
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
    appScript.subscriptions.count { it == [coordinator, 'playerCommandRequest', 'playerCommandRequestHandler'] } == 1
    appScript.subscriptions.count { it == [coordinator, 'groupFavoriteOperation', 'groupFavoriteOperationEventHandler'] } == 1
    appScript.subscriptions.count { it == [group, 'zgtInProgress', 'zgtInProgressHandler'] } == 1
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
    appScript.state.sonosHouseholdTopology = [groups: [
      [groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_OTHER']]
    ]]
    appScript.state.playerInitializationZgt = [candidateDni: coordinator.deviceNetworkId,
      leaderGeneration: 'leader-1', topologyReady: true, lastInboundAt: appScript.now()]
    coordinator.currentValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.currentValues.groupPlayerIds = 'RINCON_FOLLOW,RINCON_COORD'
    coordinator.currentValues.groupId = 'GROUP-1'

    when:
    appScript.topologyAttributeHandler([device: coordinator, name: 'groupPlayerIds',
      value: 'RINCON_FOLLOW,RINCON_COORD'] as Event)

    then:
    appScript.state.lastProcessedTopologyByDni[coordinator.deviceNetworkId] ==
      [coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']]
    appScript.scheduled.any { List job -> job[1] == 'zgtTopologyWatchdog' }

    when:
    appScript.scheduled.clear()
    appScript.topologyAttributeHandler([device: coordinator, name: 'isGroupCoordinator',
      value: 'on'] as Event)

    then:
    !appScript.scheduled.any { List job -> job[1] == 'zgtTopologyWatchdog' }
  }

  def "zgtInProgress handler accepts parent clear without error"() {
    given:
    group.dataValues.zgtInProgress = 'on'

    when:
    appScript.zgtInProgressHandler([value: 'off', deviceId: 'GROUP-DNI'] as Event)

    then:
    noExceptionThrown()
    appScript.logs.any { String entry -> entry.contains('ZGT In Progress') }
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

  def "Favorite waits for a separate grouping operation, then loads directly"() {
    given:
    appScript.processGroupCommandRequest([request: request('on', [:], 1)])
    appScript.processGroupCommandRequest([request: request('loadFavoriteFull', [
      favoriteId: '42', repeatMode: 'repeat all', queueMode: 'replace',
      shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ], 2)])

    when:
    appScript.drainDeferredGroupOperationCommands()

    then:
    coordinator.commands.find { it.name == 'loadFavoriteFull' }.favoriteId == '42'
    appScript.getActiveGroupOperation() == null
    !coordinator.commands.any { it.name == 'loadFavoriteForGroupOperation' }
  }

  def "group device state is exact in explicit mode and subset-based in additive mode"() {
    given:
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'])

    expect:
    appScript.state.pendingGroupStateUpdates['GROUP-DNI'].switch == 'off'

    when:
    appScript.state.clear()
    group.currentValues.groupingMode = 'ADDITIVE'
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'])

    then:
    appScript.state.pendingGroupStateUpdates['GROUP-DNI'].switch == 'on'
    appScript.state.pendingGroupStateUpdates['GROUP-DNI'].currentlyJoinedPlayers.contains('RINCON_EXTRA')
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
    appScript.state.pendingGroupStateUpdates.size() == 8
    appScript.scheduled.any { List job -> job[1] == 'continueGroupDeviceMembershipUpdate' }

    when:
    List job = appScript.scheduled.find { List item -> item[1] == 'continueGroupDeviceMembershipUpdate' }
    appScript.continueGroupDeviceMembershipUpdate(job[2].data as Map)

    then:
    appScript.state.pendingGroupStateUpdates.size() == 11
  }

  def "group activation batches the coordinator's existing track metadata snapshot"() {
    given:
    coordinator.currentValues.currentTrackName = 'Peepers On Crane Pond'
    coordinator.currentValues.currentArtistName = 'Adirondack Environments'
    coordinator.currentValues.currentAlbumName = 'Peepers On Crane Pond'
    coordinator.currentValues.currentTrackDuration = '0:41:03'
    coordinator.currentValues.currentTrackNumber = 1
    coordinator.currentValues.trackDescription = 'Peepers On Crane Pond by Adirondack Environments'
    coordinator.currentValues.trackData = JsonOutput.toJson([
      name: 'Peepers On Crane Pond', artist: 'Adirondack Environments', album: 'Peepers On Crane Pond'
    ])
    group.currentValues.switch = 'off'
    group.currentValues.currentTrackName = 'n/a'
    appScript.state.lastSentChildState = ['GROUP-DNI': [
      currentTrackName: 'Peepers On Crane Pond',
      currentArtistName: 'Adirondack Environments',
      currentAlbumName: 'Peepers On Crane Pond',
      trackDescription: 'Peepers On Crane Pond by Adirondack Environments',
      trackData: coordinator.currentValues.trackData
    ]]

    when:
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW'])
    appScript.drainGroupStateUpdates()
    Map<String, Object> attributes = (Map<String, Object>)new JsonSlurper().parseText(
      group.refreshedPlaybackUpdates.last())

    then:
    attributes.switch == 'on'
    attributes.currentTrackName == 'Peepers On Crane Pond'
    attributes.currentArtistName == 'Adirondack Environments'
    attributes.currentAlbumName == 'Peepers On Crane Pond'
    attributes.currentTrackDuration == '0:41:03'
    attributes.currentTrackNumber == 1
    attributes.trackDescription == 'Peepers On Crane Pond by Adirondack Environments'
    attributes.trackData == coordinator.currentValues.trackData
  }

  def "group state drain yields after its fixed group budget"() {
    given:
    Map<String, Map<String, Object>> pending = [:]
    (0..<10).each { Integer index -> pending["GROUP-${index}"] = [status: 'playing'] }
    appScript.state.pendingGroupStateUpdates = pending
    (0..<10).each { Integer index ->
      appScript.children << new SonosAppGroupDouble(deviceNetworkId: "GROUP-${index}")
    }

    when:
    appScript.drainGroupStateUpdates()

    then:
    appScript.state.pendingGroupStateUpdates.size() == 2
    appScript.scheduled.any { List job -> job[1] == 'drainGroupStateUpdates' }

    when:
    appScript.drainGroupStateUpdates()

    then:
    !appScript.state.containsKey('pendingGroupStateUpdates')
  }

  def "active group metadata mismatch bypasses a stale last-sent snapshot"() {
    given:
    coordinator.currentValues.currentTrackName = 'Peepers On Crane Pond'
    coordinator.currentValues.currentArtistName = 'Adirondack Environments'
    coordinator.currentValues.trackDescription = 'Peepers On Crane Pond by Adirondack Environments'
    group.currentValues.switch = 'on'
    group.currentValues.currentTrackName = 'n/a'
    group.currentValues.currentArtistName = 'n/a'
    group.currentValues.trackDescription = 'n/a'
    appScript.state.lastSentChildState = ['GROUP-DNI': [
      currentTrackName: 'Peepers On Crane Pond',
      currentArtistName: 'Adirondack Environments',
      trackDescription: 'Peepers On Crane Pond by Adirondack Environments'
    ]]

    when:
    appScript.updateGroupDevices('RINCON_COORD', ['RINCON_COORD', 'RINCON_FOLLOW'])
    appScript.drainGroupStateUpdates()
    Map<String, Object> attributes = (Map<String, Object>)new JsonSlurper().parseText(
      group.refreshedPlaybackUpdates.last())

    then:
    attributes.switch == 'on'
    attributes.currentTrackName == 'Peepers On Crane Pond'
    attributes.currentArtistName == 'Adirondack Environments'
    attributes.trackDescription == 'Peepers On Crane Pond by Adirondack Environments'
  }

  def "coordinator metadata batch repairs group attributes despite a matching last-sent cache"() {
    given:
    group.currentValues.switch = 'on'
    group.currentValues.status = 'inactive'
    group.currentValues.currentTrackName = 'n/a'
    group.currentValues.currentArtistName = 'n/a'
    group.currentValues.trackDescription = 'n/a'
    appScript.state.lastSentChildState = ['GROUP-DNI': [
      currentTrackName: 'Peepers On Crane Pond',
      currentArtistName: 'Adirondack Environments',
      trackDescription: 'Peepers On Crane Pond by Adirondack Environments',
      status: 'playing'
    ]]

    when:
    appScript.flushGroupDeviceState('RINCON_COORD', null, [
      status: 'playing', currentTrackName: 'Peepers On Crane Pond',
      currentArtistName: 'Adirondack Environments',
      trackDescription: 'Peepers On Crane Pond by Adirondack Environments'
    ])
    appScript.drainGroupStateUpdates()
    Map<String, Object> attributes = (Map<String, Object>)new JsonSlurper().parseText(
      group.refreshedPlaybackUpdates.last())

    then:
    attributes.status == 'playing'
    attributes.currentTrackName == 'Peepers On Crane Pond'
    attributes.currentArtistName == 'Adirondack Environments'
    attributes.trackDescription == 'Peepers On Crane Pond by Adirondack Environments'
  }

  def "ungrouped group refresh reflects only the coordinator when individual control is disabled"() {
    given:
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
    Map attributes = (Map)new JsonSlurper().parseText(group.refreshedPlaybackUpdates.last())

    then:
    attributes.volume == 10
    attributes.groupVolume == 10
    attributes.mute == 'muted'
    attributes.groupMute == 'muted'
  }

  def "new UI speaker table merges discovery with created players and escapes display values"() {
    given:
    appScript.discoveredSonoses.clear()
    appScript.discoveredSonosSecondaries.clear()
    appScript.discoveredSonoses['AA:BB:CC:DD:EE:01'] = [
      id: 'RINCON_COORD', name: 'Kitchen <Main>', modelDisplayName: 'Arc', deviceIp: '192.168.1.10'
    ]
    appScript.discoveredSonoses['AA:BB:CC:DD:EE:03'] = [
      id: 'RINCON_UNCREATED', name: 'Bedroom', modelDisplayName: 'One', deviceIp: '192.168.1.12'
    ]
    appScript.discoveredSonosSecondaries['AA:BB:CC:DD:EE:02'] = [
      id: 'RINCON_SECONDARY', primaryDeviceId: 'RINCON_COORD', modelDisplayName: 'Sub', deviceIp: '192.168.1.11'
    ]
    coordinator.dataValues.name = 'Kitchen <Main>'
    coordinator.dataValues.modelDisplayName = 'Arc'
    coordinator.dataValues.deviceIp = '192.168.1.10'

    when:
    String markup = appScript.renderNewUiSpeakerTableMarkup()

    then:
    markup.contains('Kitchen &lt;Main&gt;')
    markup.contains('Secondary Speakers')
    markup.contains('1 discovered')
    markup.contains('Sub')
    markup.contains('192.168.1.11')
    markup.contains("class='mdl-data-table'")
    markup.contains("material-symbols:delete-outline")
    markup.contains("material-symbols:add-circle-outline-rounded")
    markup.contains('newUiDeletePlayer|AA:BB:CC:DD:EE:01')
    markup.contains('newUiCreatePlayer|AA:BB:CC:DD:EE:03')
    markup.contains("href='/device/edit/${coordinator.id}' target='_blank'")
    markup.contains('<th>Action</th>')
    markup.contains('<th>Speaker</th>')
    markup.contains('<th>Secondary Speakers</th>')
    markup.contains('<th>Network</th>')
    markup.contains('<th>Create Group From</th>')
    markup.contains("material-symbols:group-add")
    markup.contains('newUiCreateGroup|RINCON_COORD')
    !markup.contains('AV Transport')
    !markup.contains('Rendering Control')
    !markup.contains('Zone Group')
    !markup.contains('WebSocket')
    markup.contains('No primary Sonos speakers have been discovered yet.') == false
  }

  def "main page defaults to the new UI and switches to the two old UI links"() {
    given:
    appScript.atomicState = [discoveryRunning: false]
    appScript.settings.putAll([autoCheckUpdates: false, autoInstallUpdates: false, useOldUi: false])
    appScript.binding.setVariable('autoCheckUpdates', false)
    appScript.binding.setVariable('autoInstallUpdates', false)

    when:
    Map newUiPage = appScript.mainPage()

    then:
    newUiPage.title == '<p hidden>Sonos Advanced Controller</p>'
    appScript.pageHrefs.empty

    when:
    appScript.pageHrefs.clear()
    appScript.settings.useOldUi = true
    Map oldUiPage = appScript.mainPage()

    then:
    oldUiPage.title == 'Sonos Advanced Controller'
    appScript.pageHrefs.size() == 2
    appScript.pageHrefs*.first()*.page == ['localPlayerPage', 'groupPage']
  }

  def "main page renders cached installed version without changing the async update result"() {
    given:
    appScript.atomicState = [discoveryRunning: false]
    appScript.state.putAll([updateAvailable: true, latestVersion: '99.0.0',
      installedAppVersion: '98.0.0', latestReleaseDate: '2026-11-01', latestReleaseUrl: 'https://example.com/release'])
    appScript.settings.putAll([autoCheckUpdates: false, autoInstallUpdates: false, useOldUi: false])
    appScript.binding.setVariable('autoCheckUpdates', false)
    appScript.binding.setVariable('autoInstallUpdates', false)

    when:
    Map page = appScript.mainPage()
    String noticeMarkup = appScript.renderNewUiUpdateNoticeMarkup()

    then:
    page.title == '<p hidden>Sonos Advanced Controller</p>'
    appScript.state.updateAvailable == true
    appScript.state.installedAppVersion == '98.0.0'
    appScript.asyncCalls.empty
    noticeMarkup.contains('settings[btnInstallUpdate]')
    noticeMarkup.contains('settings[btnDismissUpdate]')
  }

  def "new UI speaker table restores discovered secondaries after the volatile map is cleared"() {
    given:
    appScript.discoveredSonoses['AA:BB:CC:DD:EE:01'] = [
      id: 'RINCON_COORD', name: 'Kitchen', modelDisplayName: 'Arc', deviceIp: '192.168.1.10'
    ]
    appScript.discoveredSonosSecondaries['AA:BB:CC:DD:EE:02'] = [
      id: 'RINCON_SECONDARY', primaryDeviceId: 'RINCON_COORD', modelDisplayName: 'Sub Mini',
      deviceIp: '192.168.1.11'
    ]
    appScript.persistDiscoveredSonosSecondaries()
    appScript.discoveredSonosSecondaries.clear()

    when:
    String markup = appScript.renderNewUiSpeakerTableMarkup()

    then:
    markup.contains('1 discovered')
    markup.contains('Sub Mini')
    markup.contains('192.168.1.11')
    appScript.discoveredSonosSecondaries['AA:BB:CC:DD:EE:02'].id == 'RINCON_SECONDARY'
  }

  def "new UI group table renders group device links, coordinator, followers, and actions"() {
    given:
    String groupName = 'Arc + 1 others'
    group.deviceNetworkId = "${appScript.app.id}-SonosGroupDevice-${groupName}"
    appScript.state.userGroups = [
      (groupName): [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    ]

    when:
    String markup = appScript.renderNewUiGroupTableMarkup()
    String groupTable = appScript.displayNewUiGroupTable()
    String token = appScript.newUiGroupToken(groupName)

    then:
    markup.contains('<th>Action</th>')
    markup.contains('<th>Group Name</th>')
    markup.contains('<th>Coordinator</th>')
    markup.contains('<th>Followers</th>')
    markup.contains(groupName)
    markup.contains('RINCON_COORD')
    markup.contains('RINCON_FOLLOW')
    markup.contains("href='/device/edit/${group.id}' target='_blank'")
    markup.contains("newUiEditGroup|${token}")
    markup.contains("newUiDeleteGroup|${token}")
    markup.contains('material-symbols:delete-outline')
    groupTable.contains("<div id='new-ui-group-table-wrapper'>")
  }

  def "new UI group editor uses registered settings and fixed field actions"() {
    given:
    appScript.appButtonHandler('newUiCreateGroup|RINCON_COORD')

    when:
    String markup = appScript.renderNewUiGroupEditorMarkup()

    then:
    markup.contains("[name='settings[newUiGroupName]']")
    markup.contains("[name='settings[newUiGroupCoordinator]']")
    markup.contains('syncNewUiGroupFields();buttonClick(this)')
    markup.contains("btnNewUiGroupNameChanged.type")
    markup.contains("btnNewUiGroupCoordinatorChanged.type")
    markup.contains("newUiToggleGroupFollower|RINCON_FOLLOW")
    markup.contains("new-ui-group-checkbox")
    markup.contains("new-ui-group-checkbox-checked:after")
    !markup.contains("<iconify-icon icon='material-symbols:check_box")
    !markup.contains("new-ui-group-follower-checkbox")
    !markup.contains("marker.name=action+'.type'")
  }

  def "new UI group creation persists a name, coordinator, followers, and child device"() {
    given:
    appScript.state.userGroups = [:]
    appScript.settings.playerDevices = [coordinator.deviceNetworkId, follower.deviceNetworkId, extra.deviceNetworkId]
    appScript.settings.groupDevices = ["${appScript.app.id}-SonosGroupDevice-Family"]
    appScript.settings.skipOrphanRemoval = true

    when:
    appScript.appButtonHandler('newUiCreateGroup|RINCON_COORD')
    appScript.settings.newUiGroupName = 'Family'
    appScript.appButtonHandler('btnNewUiGroupNameChanged')
    appScript.settings.newUiGroupCoordinator = 'RINCON_COORD'
    appScript.appButtonHandler('btnNewUiGroupCoordinatorChanged')
    appScript.appButtonHandler('newUiToggleGroupFollower|RINCON_FOLLOW')
    appScript.appButtonHandler('btnNewUiSaveGroup')

    then:
    appScript.state.userGroups['Family'] == [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    appScript.app.getChildDevice("${appScript.app.id}-SonosGroupDevice-Family") != null
    appScript.state.newUiGroupEditorMode == null
    appScript.app.updatedSettings.groupDevices == ["${appScript.app.id}-SonosGroupDevice-Family"]
    appScript.logs.any { String entry -> entry.contains("New UI created Sonos group 'Family'") }
  }

  def "new UI follower selection sentinel preserves the empty selection for validation"() {
    given:
    appScript.appButtonHandler('newUiCreateGroup|RINCON_COORD')

    when:
    appScript.appButtonHandler('btnNewUiSaveGroup')

    then:
    appScript.state.newUiGroupError == 'Select at least one follower before saving the group.'
    appScript.state.newUiGroupEditorMode == 'create'
  }

  def "new UI follower action updates and removes the configured follower"() {
    given:
    appScript.appButtonHandler('newUiCreateGroup|RINCON_COORD')

    when:
    appScript.appButtonHandler('newUiToggleGroupFollower|RINCON_FOLLOW')
    List<String> added = appScript.getNewUiGroupFollowers(appScript.getNewUiGroupDraft())
    appScript.appButtonHandler('newUiToggleGroupFollower|RINCON_FOLLOW')
    List<String> removed = appScript.getNewUiGroupFollowers(appScript.getNewUiGroupDraft())

    then:
    added == ['RINCON_FOLLOW']
    removed.empty
    (appScript.state.newUiGroupDraft.followerIds as List<String>).empty
  }

  def "muted member completion advances an audio clip group announcement"() {
    given:
    appScript.state.groupAnnouncementQueues = ['GROUP-DNI': [pending: [], active: [
      correlationId: 'muted-member-announcement', mode: 'AUDIO_CLIP',
      coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      playerStatuses: [RINCON_COORD: 'DONE']
    ]]]

    when:
    appScript.handleGroupAnnouncementProgress(follower, [groupDni: 'GROUP-DNI',
      correlationId: 'muted-member-announcement', phase: 'DISMISSED', reason: 'muted'])

    then:
    appScript.state.groupAnnouncementQueues['GROUP-DNI'].active == null
    appScript.logs.any { String entry -> entry.contains('finished (DONE)') }
    !appScript.logs.any { String entry -> entry.contains('timed out') }
  }

  def "new UI automatic group names use the follower name for one follower"() {
    expect:
    appScript.buildNewUiAutomaticGroupName('RINCON_COORD', [], [RINCON_COORD: 'Cora\'s Room']) == "Cora's Room + 0 others"
    appScript.buildNewUiAutomaticGroupName('RINCON_COORD', ['RINCON_FOLLOW'], [
      RINCON_COORD: 'Cora\'s Room', RINCON_FOLLOW: 'Kitchen'
    ]) == "Cora's Room + Kitchen"
    appScript.buildNewUiAutomaticGroupName('RINCON_COORD', ['RINCON_FOLLOW', 'RINCON_EXTRA'], [
      RINCON_COORD: 'Cora\'s Room', RINCON_FOLLOW: 'Kitchen', RINCON_EXTRA: 'Bedroom'
    ]) == "Cora's Room + 2 others"
  }

  def "new UI group editing renames the child device and preserves its configuration"() {
    given:
    String oldName = 'Old Name'
    String newName = 'New Name'
    group.deviceNetworkId = "${appScript.app.id}-SonosGroupDevice-${oldName}"
    appScript.state.userGroups = [
      (oldName): [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    ]
    appScript.settings.playerDevices = [coordinator.deviceNetworkId, follower.deviceNetworkId, extra.deviceNetworkId]
    appScript.settings.groupDevices = ["${appScript.app.id}-SonosGroupDevice-${newName}"]
    appScript.settings.skipOrphanRemoval = true

    when:
    appScript.appButtonHandler("newUiEditGroup|${appScript.newUiGroupToken(oldName)}")
    appScript.settings.newUiGroupName = newName
    appScript.settings.newUiGroupCoordinator = 'RINCON_COORD'
    appScript.settings.newUiGroupFollowers = ['RINCON_FOLLOW']
    appScript.appButtonHandler('btnNewUiSaveGroup')

    then:
    appScript.state.userGroups[oldName] == null
    appScript.state.userGroups[newName] == [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    appScript.app.getChildDevice("${appScript.app.id}-SonosGroupDevice-${oldName}") == null
    appScript.app.getChildDevice("${appScript.app.id}-SonosGroupDevice-${newName}") == group
    group.label == "Sonos Group: ${newName}"
  }

  def "new UI group deletion uses confirmation and removes the group child device"() {
    given:
    String groupName = 'Arc + 1 others'
    group.deviceNetworkId = "${appScript.app.id}-SonosGroupDevice-${groupName}"
    appScript.state.userGroups = [
      (groupName): [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    ]
    appScript.settings.groupDevices = [group.deviceNetworkId]

    when:
    appScript.appButtonHandler("newUiDeleteGroup|${appScript.newUiGroupToken(groupName)}")

    then:
    appScript.state.pendingNewUiDeleteGroup == appScript.newUiGroupToken(groupName)
    appScript.logs.any { String entry -> entry.contains('New UI delete requested for Sonos group') }

    when:
    appScript.appButtonHandler('btnConfirmNewUiDeleteGroup')

    then:
    appScript.state.userGroups[groupName] == null
    appScript.state.pendingNewUiDeleteGroup == null
    appScript.app.getChildDevice(group.deviceNetworkId) == null
    appScript.app.updatedSettings.groupDevices == []
    appScript.logs.any { String entry -> entry.contains("Removed Sonos group '${groupName}'") }
  }

  def "new UI create action creates the selected discovered player and logs the result"() {
    given:
    appScript.discoveredSonoses.clear()
    appScript.discoveredSonosSecondaries.clear()
    appScript.settings.playerDevices = []
    appScript.discoveredSonoses['AA:BB:CC:DD:EE:03'] = [
      id: 'RINCON_NEW', name: 'Bedroom', modelDisplayName: 'One', deviceIp: '192.168.1.12',
      localApiUrl: 'https://192.168.1.12:1443/api/v1/',
      localUpnpUrl: 'http://192.168.1.12:1400', localUpnpHost: '192.168.1.12:1400',
      websocketUrl: 'wss://192.168.1.12:1443/websocket'
    ]

    when:
    appScript.appButtonHandler('newUiCreatePlayer|AA:BB:CC:DD:EE:03')

    then:
    appScript.app.getChildDevice('AA:BB:CC:DD:EE:03') != null
    appScript.app.getChildDevice('AA:BB:CC:DD:EE:03').getDataValue('id') == 'RINCON_NEW'
    appScript.app.updatedSettings.playerDevices == ['AA:BB:CC:DD:EE:03']
    appScript.scheduled.any { List item -> item[0] == 3 && item[1] == 'finalizeNewUiPlayerCreation' }
    appScript.logs.any { String entry -> entry.contains("New UI created Sonos Advanced Player 'Bedroom'") }
  }

  def "new UI player deletion logs, requires confirmation, and removes the confirmed player"() {
    given:
    appScript.discoveredSonoses.clear()
    appScript.discoveredSonosSecondaries.clear()
    appScript.settings.playerDevices = [coordinator.deviceNetworkId, follower.deviceNetworkId]

    when:
    appScript.appButtonHandler("newUiDeletePlayer|${coordinator.deviceNetworkId}")

    then:
    appScript.state.pendingNewUiDeletePlayer == coordinator.deviceNetworkId
    appScript.logs.any { String entry -> entry.contains('New UI delete requested') }

    when:
    appScript.appButtonHandler('btnConfirmNewUiDeletePlayer')

    then:
    appScript.state.pendingNewUiDeletePlayer == null
    appScript.app.getChildDevice(coordinator.deviceNetworkId) == null
    appScript.app.updatedSettings.playerDevices == [follower.deviceNetworkId]
    appScript.scheduled.any { List item -> item[1] == 'finalizeNewUiPlayerDeletion' }
    appScript.logs.any { String entry -> entry.contains('Removing Sonos Advanced Player') }
  }

  def "new UI discovery button starts a fresh 60 second discovery session"() {
    given:
    appScript.atomicState = [discoveryRunning: false]
    appScript.discoveredSonoses['STALE_PRIMARY'] = [id: 'RINCON_STALE', name: 'Stale']
    appScript.discoveredSonosSecondaries['STALE_SECONDARY'] = [
      id: 'RINCON_STALE_SECONDARY', primaryDeviceId: 'RINCON_STALE', modelName: 'Sub'
    ]

    when:
    appScript.appButtonHandler('btnNewUiDiscoverSpeakers')

    then:
    appScript.atomicState.discoveryRunning == true
    (appScript.atomicState.discoveryEndTime as Long) > appScript.now()
    appScript.discoveredSonoses.isEmpty()
    appScript.discoveredSonosSecondaries.isEmpty()
    appScript.scheduled.any { List item -> item[0] == 60 && item[1] == 'stopDiscovery' }
    appScript.scheduled.any { List item -> item[1] == 'updateDiscoveryTimer' }
    appScript.logs.any { String entry -> entry.contains('Starting New UI Sonos speaker discovery') }
  }

  def "new UI discovery timer is rendered through SSR events"() {
    given:
    appScript.atomicState = [
      discoveryRunning: true,
      discoveryEndTime: appScript.now() + 60000L
    ]

    when:
    String timerMarkup = appScript.processServerSideRender([name: 'discoveryTimer'])

    then:
    timerMarkup.contains("<div class='new-ui-discovery-title'>Searching for Sonos speakers</div>")
    timerMarkup ==~ /(?s).*<div class='new-ui-discovery-detail'>About [0-9]+ seconds remaining<\/div>.*/
  }
}
