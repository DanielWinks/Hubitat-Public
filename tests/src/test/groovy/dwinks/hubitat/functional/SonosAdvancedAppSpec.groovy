package dwinks.hubitat.functional

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.hub.domain.Event
import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class SonosAppPlayerDouble extends ChildDeviceWrapper {
  List<Map> commands = []

  void playerCreateGroup(List<String> playerIds) {
    commands << [name: 'playerCreateGroup', playerIds: playerIds]
  }

  void playerModifyGroupMembers(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = []) {
    commands << [name: 'playerModifyGroupMembers', add: playerIdsToAdd, remove: playerIdsToRemove]
  }

  void playerGetGroupsFull() {
    commands << [name: 'playerGetGroupsFull']
  }

  void loadFavoriteForGroupOperation(String favoriteId, String repeatMode, String queueMode,
      String shuffleMode, String autoPlay, String crossfadeMode, String operationId, String groupId) {
    commands << [
      name: 'loadFavoriteForGroupOperation', favoriteId: favoriteId, repeatMode: repeatMode,
      queueMode: queueMode, shuffleMode: shuffleMode, autoPlay: autoPlay,
      crossfadeMode: crossfadeMode, operationId: operationId, groupId: groupId
    ]
  }

  void registerGroupFavoriteOperation(String operationId, String favoriteId) {
    commands << [name: 'registerGroupFavoriteOperation', operationId: operationId, favoriteId: favoriteId]
  }

  void clearGroupFavoriteOperation(String operationId = null) {
    commands << [name: 'clearGroupFavoriteOperation', operationId: operationId]
  }

  void getPlaybackStatus() { commands << [name: 'getPlaybackStatus'] }
  void getPlaybackMetadataStatus() { commands << [name: 'getPlaybackMetadataStatus'] }
  void playerPlay() { commands << [name: 'playerPlay'] }
}

class SonosAppGroupDouble extends ChildDeviceWrapper {
  List<Map> statuses = []

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

  def setup() {
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    appScript = ScriptLoader.load(appFile, new File('../Libraries'), true)
    appScript.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: true]
    appScript.logs.clear()
    appScript.events.clear()
    appScript.scheduled.clear()
    appScript.state.clear()

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
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: appScript.getActiveGroupOperation().operationId,
        playerId: playerId,
        observedAt: observedAt ?: appScript.now(),
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

  void emitPlayback(String playerId, String playbackState, Long observedAt = null) {
    appScript.groupFavoriteOperationEventHandler([
      value: JsonOutput.toJson([
        operationId: appScript.getActiveGroupOperation().operationId,
        playerId: playerId,
        observedAt: observedAt ?: appScript.now(),
        event: 'playbackStatus',
        data: [playbackState: playbackState, groupId: 'GROUP-1']
      ])
    ] as Event)
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

  def "additive grouping adds missing configured followers and never removes extras"() {
    given:
    follower.dataValues.groupId = 'GROUP-2'
    follower.dataValues.groupCoordinatorId = 'RINCON_FOLLOW'
    follower.dataValues.groupPlayerIds = 'RINCON_FOLLOW'
    follower.dataValues.isGroupCoordinator = 'true'
    appScript.processGroupCommandRequest([request: request('joinPlayersToCoordinator')])
    emitTopologyObservation('RINCON_COORD', 'GROUP-2', 'RINCON_FOLLOW', 'RINCON_FOLLOW,RINCON_EXTRA')
    emitTopologyObservation('RINCON_FOLLOW', 'GROUP-2', 'RINCON_FOLLOW', 'RINCON_FOLLOW,RINCON_EXTRA')

    when:
    appScript.advanceGroupFavoriteOperation([operationId: appScript.getActiveGroupOperation().operationId])

    then:
    follower.commands.find { it.name == 'playerModifyGroupMembers' } == [
      name: 'playerModifyGroupMembers', add: ['RINCON_COORD'], remove: []
    ]
    follower.commands.every { it.name != 'playerCreateGroup' }
    follower.commands.every { it.name != 'playerModifyGroupMembers' || it.remove == [] }
    appScript.getActiveGroupOperation().groupingMode == 'ADDITIVE'
  }

  def "topology satisfaction uses exact equality for explicit mode and subset matching for additive mode"() {
    given:
    Map exactOperation = [
      groupingMode: 'EXPLICIT', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW'],
      desiredCoordinatorId: 'RINCON_COORD'
    ]
    Map additiveOperation = [
      groupingMode: 'ADDITIVE', requiredPlayerIds: ['RINCON_COORD', 'RINCON_FOLLOW']
    ]
    Map topology = [
      consistent: true, groupId: 'GROUP-1', coordinatorId: 'RINCON_COORD',
      playerIds: ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA']
    ]

    expect:
    !appScript.isGroupTopologySatisfied(exactOperation, topology)
    appScript.isGroupTopologySatisfied(additiveOperation, topology)
    appScript.isGroupTopologySatisfied(exactOperation, topology + [playerIds: ['RINCON_COORD', 'RINCON_FOLLOW']])
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
    topology.fresh == true
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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])

    then:
    coordinator.commands.find { it.name == 'loadFavoriteForGroupOperation' }.groupId == 'GROUP-1'
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

  def "Favorite completes only after coordinator acknowledgement, playback, and exact metadata"() {
    given:
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 1)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
    assert appScript.getActiveGroupOperation().phase == 'WAIT_FOR_FAVORITE'

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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
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
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
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
}
