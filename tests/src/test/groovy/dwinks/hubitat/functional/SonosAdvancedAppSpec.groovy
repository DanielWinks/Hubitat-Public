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
  Long id = 101L
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
      String shuffleMode, String autoPlay, String crossfadeMode, String operationId, String groupId,
      String attemptId = null) {
    commands << [
      name: 'loadFavoriteForGroupOperation', favoriteId: favoriteId, repeatMode: repeatMode,
      queueMode: queueMode, shuffleMode: shuffleMode, autoPlay: autoPlay,
      crossfadeMode: crossfadeMode, operationId: operationId, groupId: groupId,
      attemptId: attemptId
    ]
  }

  void registerGroupFavoriteOperation(String operationId, String favoriteId) {
    commands << [name: 'registerGroupFavoriteOperation', operationId: operationId, favoriteId: favoriteId]
  }

  void setGroupFavoriteOperationAttempt(String operationId, String attemptId, String groupId) {
    commands << [name: 'setGroupFavoriteOperationAttempt', operationId: operationId,
                 attemptId: attemptId, groupId: groupId]
  }

  void clearGroupFavoriteOperation(String operationId = null) {
    commands << [name: 'clearGroupFavoriteOperation', operationId: operationId]
  }

  void getPlaybackStatus() { commands << [name: 'getPlaybackStatus'] }
  void getPlaybackMetadataStatus() { commands << [name: 'getPlaybackMetadataStatus'] }
  void playerPlay() { commands << [name: 'playerPlay'] }
}

class SonosAppGroupDouble extends ChildDeviceWrapper {
  Long id = 201L
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
    appScript.pageHrefs.clear()
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
    currentAttemptId == "${operation.operationId}:1"
    appScript.getActiveGroupOperation().loadAcknowledged == false
  }

  def "a post-load topology change is recovered instead of completing the Favorite"() {
    given:
    appScript.processGroupCommandRequest([request: request('on', [:], 1)])
    appScript.processGroupCommandRequest([request: request('loadFavorite', [favoriteId: '42'], 2)])
    Map operation = appScript.getActiveGroupOperation()
    emitRequiredTopology()
    appScript.advanceGroupFavoriteOperation([operationId: operation.operationId])
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
    groupTable.contains("class='new-ui-group-table-section-title'>Sonos Groups</div>")
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
