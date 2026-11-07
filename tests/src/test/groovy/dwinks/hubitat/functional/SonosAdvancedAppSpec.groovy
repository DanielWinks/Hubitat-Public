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
  List<String> acknowledgedParentRequests = []

  void mute() { commands << [name: 'mute'] }
  void unmute() { commands << [name: 'unmute'] }

  void acknowledgeParentCommand(String requestId) { acknowledgedParentRequests << requestId }

  void playerCreateGroup(List<String> playerIds) {
    commands << [name: 'playerCreateGroup', playerIds: playerIds]
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

  void playerLoadAudioClip(String uri, BigDecimal volume, Integer duration, String correlationId) {
    commands << [name: 'playerLoadAudioClip', uri: uri, volume: volume,
      duration: duration, correlationId: correlationId]
  }

  void playerLoadAudioClipHighPriority(String uri, BigDecimal volume, String correlationId,
      Integer duration, Boolean deferStart = false) {
    commands << [name: 'playerLoadAudioClipHighPriority', uri: uri, volume: volume,
      correlationId: correlationId, duration: duration, deferStart: deferStart]
  }

  void playerPrepareAudioClipForSynchronizedStart(String uri, BigDecimal volume,
      String correlationId, Integer duration, Boolean deferStart = false) {
    commands << [name: 'playerPrepareAudioClipForSynchronizedStart', uri: uri,
      volume: volume, correlationId: correlationId, duration: duration, deferStart: deferStart]
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
  void playerPlay() { commands << [name: 'playerPlay'] }
  void playerPlayForGroupOperation(String operationId, String attemptId, String requestId) {
    commands << [name: 'playerPlayForGroupOperation', operationId: operationId,
      attemptId: attemptId, requestId: requestId]
  }
}

class SonosAppGroupDouble extends ChildDeviceWrapper {
  Long id = 201L
  List<Map> statuses = []
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

  void updateGroupOperationStatus(String statusJson, String groupingMode = null) {
    Map status = (Map)new JsonSlurper().parseText(statusJson)
    statuses << [status: status, groupingMode: groupingMode]
    if(groupingMode) { currentValues.groupingMode = groupingMode }
  }
}

class SonosAdvancedAppSpec extends Specification {

  @Shared File appFile = new File('../Apps/SonosAdvancedApp.groovy')

  HubitatScriptHarness appScript
  SonosAppGroupDouble group
  SonosAppPlayerDouble coordinator
  SonosAppPlayerDouble follower
  SonosAppPlayerDouble extra
  Long topologyObservationSequence = 0L

  def setup() {
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    appScript = ScriptLoader.load(appFile, new File('../Libraries'), true)
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

  SonosAppPlayerDouble createPlayer(String id, String groupId, String coordinatorId,
      String playerIds, Boolean isCoordinator) {
    SonosAppPlayerDouble player = new SonosAppPlayerDouble(
      deviceNetworkId: "DNI-${id}", displayName: id, label: id
    )
    player.dataValues.id = id
    player.dataValues.name = id
    player.dataValues.groupId = groupId
    player.dataValues.groupCoordinatorId = coordinatorId
    player.dataValues.groupPlayerIds = playerIds
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

  void stabilizePostFavoriteTopology(String groupId = 'GROUP-1', String coordinatorId = 'RINCON_COORD',
      String playerIds = 'RINCON_COORD,RINCON_FOLLOW') {
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology(groupId, coordinatorId, playerIds)
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology(groupId, coordinatorId, playerIds)
  }

  void emitPlayback(String playerId, String playbackState, Long observedAt = null, String groupId = 'GROUP-1') {
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: appScript.getActiveGroupOperation().operationId,
        playerId: playerId,
        observedAt: observedAt ?: appScript.now(),
        event: 'playbackStatus',
        data: [playbackState: playbackState, groupId: groupId]
      ])
    ] as Event)
  }

  def "explicit group refresh hydrates legacy playback states while inactive"() {
    given:
    coordinator.currentValues.status = 'playing'
    coordinator.currentValues.currentTrackName = 'Only the Good Die Young'
    coordinator.currentValues.currentArtistName = 'Billy Joel'

    when:
    appScript.processGroupCommandRequest([request: request('refresh')])

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
    appScript.processGroupCommandRequest([request: request('on')])
    emitRequiredTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')

    when:
    appScript.advanceGroupFavoriteOperation([operationId: appScript.getActiveGroupOperation().operationId])

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_EXTRA']
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' || it.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW'] }
    appScript.getActiveGroupOperation().groupingMode == 'EXPLICIT'
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

  def "additive grouping transfers coordination to the configured coordinator and preserves extras"() {
    given:
    follower.dataValues.groupId = 'GROUP-2'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    follower.dataValues.isGroupCoordinator = 'true'
    appScript.processGroupCommandRequest([request: request('joinPlayersToCoordinator')])
    emitTopologyObservation('RINCON_COORD', 'GROUP-2', 'RINCON_FOLLOW', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-2', 'RINCON_FOLLOW', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')
    Map operation = appScript.getActiveGroupOperation()

    when:
    appScript.advanceGroupFavoriteOperation([operationId: appScript.getActiveGroupOperation().operationId])

    then:
    follower.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: [], remove: ['RINCON_COORD'],
      expectedTopology: [
        RINCON_COORD: [coordinatorId: 'RINCON_COORD', memberIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']],
        RINCON_FOLLOW: [coordinatorId: 'RINCON_COORD', memberIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']],
        RINCON_EXTRA: [coordinatorId: 'RINCON_COORD', memberIds: ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']]
      ]
    ]
    operation.coordinatorTransferStarted == true
    operation.coordinatorTransferPlayerIds == ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']
    follower.commands.every { it.name != 'playerCreateGroup' }
    appScript.getActiveGroupOperation().groupingMode == 'ADDITIVE'
  }

  def "additive coordinator transfer recreates the preserved roster with configured coordinator first"() {
    given:
    appScript.processGroupCommandRequest([request: request('joinPlayersToCoordinator')])
    Map operation = appScript.getActiveGroupOperation()
    operation.coordinatorTransferStarted = true
    operation.coordinatorTransferGroupCreated = false
    operation.coordinatorTransferPlayerIds = ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']
    operation.coordinatorTransferPreviousCoordinatorId = 'RINCON_FOLLOW'
    Map<String, Map> topology = [
      fresh: true,
      observations: [[playerId: 'RINCON_COORD', fresh: true, coordinatorId: 'RINCON_COORD', playerIds: ['RINCON_COORD']]]
    ]

    when:
    appScript.sendGroupTopologyCommand(operation, topology)

    then:
    coordinator.commands.find { it.name == 'playerCreateGroup' }?.playerIds ==
        ['RINCON_COORD', 'RINCON_EXTRA', 'RINCON_FOLLOW']
    operation.coordinatorTransferGroupCreated == true
    operation.resolvedCoordinatorId == 'RINCON_COORD'
  }

  def "additive grouping sends the join when fresh topology responses are unavailable"() {
    given:
    coordinator.dataValues.groupId = 'GROUP-COORD'
    coordinator.dataValues.groupCoordinatorId = 'RINCON_COORD'
    coordinator.dataValues.groupPlayerIds = 'RINCON_COORD'
    follower.dataValues.groupId = 'GROUP-FOLLOW'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    appScript.processGroupCommandRequest([request: request('joinPlayersToCoordinator')])
    Map operation = appScript.getActiveGroupOperation()
    operation.lastCommandAt = appScript.now() - 4000L

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    coordinator.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: ['RINCON_FOLLOW'], remove: []
    ]
    coordinator.commands.every { it.name != 'playerCreateGroup' }
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
    appScript.getActiveGroupOperation().stableObservations == 1
    coordinator.commands.findAll { it.name == 'playerCreateGroup' }.empty

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation().stableObservations == 1
    coordinator.commands.find { it.name == 'playerCreateGroup' } == null

    when:
    emitTopologyObservation('RINCON_COORD', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation() == null
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

  def "cached topology is not accepted before a fresh group observation"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation() != null
    appScript.getActiveGroupOperation().stableObservations == 0
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' } == null
    coordinator.commands.find { it.name == 'playerGetGroupsFull' } != null
    follower.commands.find { it.name == 'playerGetGroupsFull' } != null
  }

  def "a negative group observation prevents stale cached topology from satisfying the operation"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitTopologyObservation('RINCON_COORD', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW')
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitTopologyObservation('RINCON_FOLLOW', null, null, '')

    when:
    Map topology = appScript.readGroupOperationTopology(operation)

    then:
    topology.fresh == false
    topology.consistent == false
    topology.observations.find { it.playerId == 'RINCON_FOLLOW' }.groupId == null
    appScript.getActiveGroupOperation() != null
  }

  def "queued group requests preserve grouping-before-Favorite order"() {
    when:
    appScript.groupCommandRequestHandler([
      value: JsonOutput.toJson(request('loadFavorite', [favoriteId: '42'], 2))
    ] as Event)
    appScript.groupCommandRequestHandler([
      value: JsonOutput.toJson(request('on', [:], 1))
    ] as Event)
    appScript.drainGroupCommandRequests()

    then:
    appScript.getActiveGroupOperation().groupingMode == 'EXPLICIT'
    appScript.getActiveGroupOperation().favoriteId == '42'
    appScript.getActiveGroupOperation().registeredPlayerIds.containsAll(['RINCON_COORD', 'RINCON_FOLLOW'])
  }

  def "location requests from another app instance are ignored"() {
    when:
    appScript.groupCommandRequestHandler([
      value: JsonOutput.toJson([groupDni: 'OTHER-GROUP', requestId: 'OTHER-GROUP-1', command: 'on', args: [:]])
    ] as Event)

    then:
    !appScript.state.groupCommandRequests
    !appScript.scheduled.any { it[1] == 'drainGroupCommandRequests' }
  }

  def "group requests are acknowledged once and duplicate deliveries are not re-enqueued"() {
    given:
    Map groupRequest = request('refresh', [:], 1)
    // Exercise the UUID format emitted by SonosAdvGroup, rather than the
    // legacy group-DNI/sequence request ID format.
    groupRequest.requestId = '6b684b7d-8f6c-4b18-9cf1-2eaad384bf5a'

    when:
    appScript.enqueueGroupCommandRequest(groupRequest)
    appScript.enqueueGroupCommandRequest(groupRequest)

    then:
    appScript.state.pendingGroupCommandRequests['GROUP-DNI'].size() == 1
    appScript.state.pendingGroupCommandRequests['GROUP-DNI'][0].requestId == groupRequest.requestId
    group.acknowledgedGroupRequests == [groupRequest.requestId, groupRequest.requestId]
    appScript.logs.count { String entry ->
      entry.contains("Ignoring duplicate group command request ${groupRequest.requestId}")
    } == 1
  }

  def "player location state batches queue follower and group updates"() {
    given:
    Map batch = [kind: 'state', playerDni: coordinator.deviceNetworkId, coordinatorId: 'RINCON_COORD',
      followerDNIs: [follower.deviceNetworkId], followerAttrs: [status: 'playing'],
      playbackAttrs: [status: 'playing']]

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson(batch)] as Event)

    then:
    appScript.state.pendingChildEvents[follower.deviceNetworkId].status == 'playing'
    appScript.state.pendingGroupStateUpdates[group.deviceNetworkId].status == 'playing'
  }

  def "player playback observations are cached in app State Variables"() {
    given:
    Map batch = [kind: 'playbackObservation', playerDni: coordinator.deviceNetworkId,
      playbackState: 'PLAYBACK_STATE_PLAYING', playbackObservedAt: appScript.now(),
      audioSource: 'x-sonos-htastream:RINCON_COORD:spdif',
      signalStatus: 'no-signal', signalObservedAt: appScript.now()]

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson(batch)] as Event)

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

  def "queued group audio clips dispatch immediately to every player"() {
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
    [coordinator, follower].every { SonosAppPlayerDouble player ->
      player.commands.any { it.name == 'playerPrepareAudioClipForSynchronizedStart' } &&
        player.commands.any { it.name == 'startPreparedAudioClip' }
    }
    !appScript.scheduled.any { it[1] == 'drainGroupCommandRequests' }
    group.acknowledgedGroupRequests == [groupRequest.requestId]
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
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson([kind: 'topology',
      playerDni: coordinator.deviceNetworkId, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD'], observedAt: observedAt])] as Event)
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson([kind: 'topology',
      playerDni: follower.deviceNetworkId, groupId: 'GROUP-2', coordinatorId: 'RINCON_FOLLOW',
      playerIds: ['RINCON_FOLLOW'], observedAt: observedAt + 1L])] as Event)

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
    extra.commands*.name == ['mute']
    appScript.state.groupAnnouncementTopologyTransactions['stream-holder-unmuted'].holderPreviousMuteState == 'unmuted'

    when:
    appScript.restoreStreamHolderMuteState(appScript.state.groupAnnouncementTopologyTransactions['stream-holder-unmuted'])

    then:
    extra.commands*.name == ['mute', 'unmute']
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

  def "queued announcement stays on coordinator stream while previous announcement is playing"() {
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
      correlationId: 'first-announcement', phase: 'PLAYBACK_RESTORED', restored: true])
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

  def "fresh topology reports from player location events satisfy group observations"() {
    given:
    appScript.startGroupTopologyOperation(group, 'EXPLICIT')
    Map operation = appScript.getActiveGroupOperation()
    Long observedAt = appScript.now() + 1000L
    Map report = [kind: 'topology', playerDni: coordinator.deviceNetworkId,
      groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW'], observedAt: observedAt]

    when:
    appScript.playerStateUpdateHandler([data: JsonOutput.toJson(report)] as Event)
    Map topology = appScript.readGroupOperationTopology(appScript.getActiveGroupOperation())

    then:
    topology.fresh == true
    topology.consistent == true
    topology.authorityPlayerId == 'RINCON_COORD'
    topology.playerIds == ['RINCON_COORD', 'RINCON_FOLLOW']
  }

  def "repeated subscription setup keeps one location command subscription"() {
    when:
    appScript.subscribeToGroupCommandRequests()
    appScript.subscribeToGroupCommandRequests()

    then:
    appScript.subscriptions.count { it == [appScript.location, 'dwinksSonosAdvancedGroupCommand', 'groupCommandRequestHandler'] } == 1
  }

  def "Favorite is not loaded until the requested topology is stable twice"() {
    given:
    appScript.processGroupCommandRequest([request: request('on', [:], 1)])
    appScript.processGroupCommandRequest([request: request('loadFavoriteFull', [
      favoriteId: '42', repeatMode: 'repeat all', queueMode: 'replace',
      shuffleMode: 'off', autoPlay: 'true', crossfadeMode: 'on'
    ], 2)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    coordinator.commands.clear()

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' } == null
    appScript.getActiveGroupOperation().stableObservations == 1

    when:
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' }.groupId == 'GROUP-1'
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' }.attemptId == operation.favoriteAttemptId
    appScript.getActiveGroupOperation().phase == 'WAIT_FOR_FAVORITE'
  }

  def "Favorite inherits the additive context and accepts the extra player"() {
    given:
    makeTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')
    group.currentValues.groupingMode = 'ADDITIVE'
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology('GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitTopologyObservation('RINCON_COORD', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-1', 'RINCON_COORD', 'RINCON_COORD,RINCON_FOLLOW,RINCON_EXTRA')
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    operation.groupingMode == 'ADDITIVE'
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' }.groupId == 'GROUP-1'
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' }.favoriteId == '42'
  }

  def "stale Favorite operation events cannot complete the current operation"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()

    when:
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: 'stale-operation', event: 'favoriteLoadAck',
        data: [success: true]
      ])
    ] as Event)

    then:
    appScript.getActiveGroupOperation().operationId == operation.operationId
    appScript.getActiveGroupOperation().loadAcknowledged == false
  }

  def "stale Favorite attempt events cannot acknowledge a later load attempt"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    String currentAttemptId = operation.favoriteAttemptId

    when:
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD',
        attemptId: "${operation.operationId}:old", event: 'favoriteLoadAck',
        data: [success: true, groupId: 'GROUP-1']
      ])
    ] as Event)

    then:
    UUID.fromString(currentAttemptId).toString() == currentAttemptId
    currentAttemptId != operation.operationId
    appScript.getActiveGroupOperation().loadAcknowledged == false
  }

  def "a post-load topology change is recovered instead of completing the Favorite"() {
    given:
    appScript.processGroupCommandRequest([request: request('on', [:], 1)])
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 2)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology('GROUP-2')

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation() != null
    appScript.getActiveGroupOperation().phase == 'ENSURE_GROUP'
    appScript.getActiveGroupOperation().favoriteAttempt == 1
    appScript.getActiveGroupOperation().favoriteTopologyVerified == false
    group.statuses.last().status.status != 'SUCCEEDED'
  }

  def "Favorite completes only after coordinator acknowledgement, playback, and exact metadata"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    assert appScript.getActiveGroupOperation().phase == 'WAIT_FOR_FAVORITE'
    stabilizePostFavoriteTopology()

    when:
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'favoriteLoadAck',
        data: [success: true]
      ])
    ] as Event)
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'playbackStatus',
        data: [playbackState: 'PLAYBACK_STATE_PLAYING']
      ])
    ] as Event)
    emitPlayback('RINCON_FOLLOW', 'PLAYBACK_STATE_PLAYING')
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'metadataConfirmed',
        data: [favoriteId: '42', confirmed: true]
      ])
    ] as Event)
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation() == null
    group.statuses.last().status.status == 'SUCCEEDED'
    coordinator.commands.find { it.name == 'clearGroupFavoriteOperation' } != null
  }

  def "a failed Favorite acknowledgement retries the media load without regrouping"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    stabilizePostFavoriteTopology()
    coordinator.commands.clear()

    when:
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'favoriteLoadAck',
        data: [success: false, errorCode: 'ERROR_PLAYBACK_FAILED', reason: 'temporary']
      ])
    ] as Event)
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    coordinator.commands.count { it.name == 'loadFavoriteForGroupOperation' } == 1
    coordinator.commands.every { it.name != 'playerCreateGroup' && it.name != 'playerModifyGroupMembers' }
    appScript.getActiveGroupOperation().favoriteAttempt == 2
  }

  def "buffering is treated as progress and does not trigger an unnecessary play or reload"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    stabilizePostFavoriteTopology()
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'favoriteLoadAck',
        data: [success: true]
      ])
    ] as Event)
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'playbackStatus',
        data: [playbackState: 'PLAYBACK_STATE_BUFFERING']
      ])
    ] as Event)
    coordinator.commands.clear()

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation() != null
    coordinator.commands.find { it.name == 'playerPlay' } == null
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' } == null
    follower.commands.find { it.name == 'getPlaybackStatus' } != null
  }

  def "Favorite does not complete when a requested follower is not playing"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    stabilizePostFavoriteTopology()
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'favoriteLoadAck',
        data: [success: true, groupId: 'GROUP-1']
      ])
    ] as Event)
    emitPlayback('RINCON_COORD', 'PLAYBACK_STATE_PLAYING')
    emitPlayback('RINCON_FOLLOW', 'PLAYBACK_STATE_PAUSED')
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: operation.operationId, playerId: 'RINCON_COORD', event: 'metadataConfirmed',
        data: [favoriteId: '42', confirmed: true, groupId: 'GROUP-1']
      ])
    ] as Event)

    when:
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    appScript.getActiveGroupOperation() != null
    appScript.getActiveGroupOperation().playerPlaybackStates['RINCON_FOLLOW'].playbackState == 'PLAYBACK_STATE_PAUSED'
    group.statuses.last().status.status != 'SUCCEEDED'
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
    newUiPage.title == 'Sonos Advanced Controller'
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

    then:
    page.title == 'Sonos Advanced Controller'
    appScript.state.updateAvailable == true
    appScript.state.installedAppVersion == '98.0.0'
    appScript.asyncCalls.empty
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

  def "new UI group creation persists a name, coordinator, followers, and child device"() {
    given:
    appScript.state.userGroups = [:]
    appScript.settings.playerDevices = [coordinator.deviceNetworkId, follower.deviceNetworkId, extra.deviceNetworkId]
    appScript.settings.groupDevices = ["${appScript.app.id}-SonosGroupDevice-Family"]
    appScript.settings.skipOrphanRemoval = true

    when:
    appScript.appButtonHandler('newUiCreateGroup|RINCON_COORD')
    appScript.settings.newUiGroupName = 'Family'
    appScript.settings.newUiGroupCoordinator = 'RINCON_COORD'
    appScript.settings.newUiGroupFollowers = ['RINCON_FOLLOW']
    appScript.appButtonHandler('btnNewUiSaveGroup')

    then:
    appScript.state.userGroups['Family'] == [groupCoordinatorId: 'RINCON_COORD', playerIds: ['RINCON_FOLLOW']]
    appScript.app.getChildDevice("${appScript.app.id}-SonosGroupDevice-Family") != null
    appScript.state.newUiGroupEditorMode == null
    appScript.app.updatedSettings.groupDevices == ["${appScript.app.id}-SonosGroupDevice-Family"]
    appScript.logs.any { String entry -> entry.contains("New UI created Sonos group 'Family'") }
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
    timerMarkup ==~ /<b>Discovery is running: [0-9]+ seconds remaining\.<\/b>/
  }
}
