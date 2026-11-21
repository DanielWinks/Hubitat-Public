package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class SonosGroupParentDouble {
  List<Map> requests = []
  Boolean accepted = true

  Boolean groupAppRequest(Map request) {
    requests << request
    return accepted
  }
}

class SonosAdvGroupSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvGroup.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file, null, true)
  }

  def setup() {
    driver.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: true]
    driver.logs.clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    driver.events.clear()
    driver.locationEvents.clear()
    driver.runtimeState.clear()
    driver.state.clear()
    driver.pendingGroupRequests.clear()
    driver.parentRequestLocks.clear()
    driver.device.deviceNetworkId = 'SONOS-GROUP-TEST-DNI'
    driver.device.dataValues.clear()
    driver.device.currentValues.clear()
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.playerIds = 'RINCON_FOLLOW'
    driver.parent = new SonosGroupParentDouble()
    driver.heldPlaybackState.remove('SONOS-GROUP-TEST-DNI')
  }

  private void drainBatchEvents() {
    while(driver.state.pendingBatchEvents instanceof Map && !((Map)driver.state.pendingBatchEvents).isEmpty()) {
      driver.drainBatchEvents()
    }
  }

  private void drainGroupRequests() {
    List<Map> batch = driver.flushPendingGroupRequests() ?: []
    batch.each { Map request -> ((SonosGroupParentDouble)driver.parent).requests << new LinkedHashMap(request) }
  }

  private List<Map> groupRequests() {
    drainGroupRequests()
    return ((SonosGroupParentDouble)driver.parent).requests.findAll { Map request -> request.command }
  }

  private String householdTopology(String coordinatorId, List<String> playerIds, List<String> playerNames) {
    return JsonOutput.toJson([groups: [[
      groupId: 'GROUP-1', coordinatorId: coordinatorId,
      playerIds: playerIds, playerNames: playerNames
    ]]])
  }

  def "on and join set zgtInProgress on at initiation"() {
    when:
    driver.on()

    then:
    driver.events.find { Map event -> event.name == 'zgtInProgress' }?.value == 'on'
    groupRequests()[0].command == 'on'

    when:
    driver.events.clear()
    driver.joinPlayersToCoordinator()

    then:
    driver.events.find { Map event -> event.name == 'zgtInProgress' }?.value == 'on'
  }

  def "playback commands do not set zgtInProgress and setZgtInProgress clears to off"() {
    when:
    driver.play()

    then:
    driver.events.find { Map event -> event.name == 'zgtInProgress' } == null

    when:
    driver.setZgtInProgress(false)

    then:
    driver.events.find { Map event -> event.name == 'zgtInProgress' }?.value == 'off'

    when:
    driver.setZgtInProgress('yes')

    then:
    driver.events.findAll { Map event -> event.name == 'zgtInProgress' }.last()?.value == 'on'
  }

  def "group batch enqueue re-arms a drain when the scheduled marker is stale"() {
    given:
    driver.state.pendingBatchEvents = [status: [value: 'playing']]
    driver.state.batchEventsScheduled = true

    when:
    driver.queueBatchEvents([transportStatus: 'playing'])

    then:
    driver.state.batchEventsScheduled == true
    driver.scheduled.any { List call -> call[1] == 'drainBatchEvents' }
  }

  def "group initialization clears stale publication events and drain jobs"() {
    given:
    driver.state.pendingBatchEvents = [status: [value: 'stopped']]
    driver.state.batchEventsScheduled = true
    driver.state.batchEventsDueAt = 100L
    driver.scheduled << [1000, 'drainBatchEvents']

    when:
    driver.resetPendingStatePublicationForInitialization()

    then:
    !driver.state.containsKey('pendingBatchEvents')
    !driver.state.containsKey('batchEventsScheduled')
    !driver.state.containsKey('batchEventsDueAt')
    driver.unschedules.contains('drainBatchEvents')
  }

  def "retargeting a group fade to the reported volume cancels queued steps"() {
    given:
    driver.groupDeviceVolumeFadeState.clear()
    driver.lastGroupDeviceVolumeFadeCallTime.clear()
    driver.device.currentValues.volume = 5
    driver.metaClass.now = { -> 10000L }
    driver.setLevel(15G, 20G)
    driver.groupDeviceVolumeFadeStep()
    driver.device.currentValues.volume = 6
    driver.metaClass.now = { -> 14000L }
    Integer existingRequestCount = groupRequests().size()
    driver.unschedules.clear()
    driver.scheduled.clear()
    driver.runtimeState.remove('pendingGroupCommands')

    when:
    driver.setLevel(6G, 10G)
    driver.groupDeviceVolumeFadeStep()

    then:
    driver.groupDeviceVolumeFadeState.isEmpty()
    driver.unschedules.contains('groupDeviceVolumeFadeStep')
    groupRequests().size() == existingRequestCount
    !driver.scheduled.any { List call -> call[1] == 'groupDeviceVolumeFadeStep' }

    cleanup:
    GroovySystem.metaClassRegistry.removeMetaClass(driver.getClass())
    driver.metaClass = null
  }

  def "group commands are queued for poll with no parent call until polled"() {
    when:
    driver.on()

    then:
    driver.parent.requests.empty
    !driver.scheduled.any { List call -> call[1] == 'flushPendingGroupRequests' }

    when:
    drainGroupRequests()

    then:
    driver.parent.requests.size() == 1
    driver.parent.requests[0].command == 'on'
    driver.parent.requests[0].groupDni == 'SONOS-GROUP-TEST-DNI'
    driver.parent.requests[0].args.groupingMode == 'EXPLICIT'
    driver.parent.requests[0].channel == 'groupAppRequest'
    UUID.fromString(driver.parent.requests[0].requestId as String)
    driver.parent.requests[0].sequence == 1
  }

  def "group commands survive concurrent enqueue without lost updates"() {
    given:
    driver.parent = new SonosGroupParentDouble()

    when:
    driver.requestGroupCommand('refresh')
    driver.requestGroupCommand('play')
    drainGroupRequests()

    then:
    groupRequests()*.command == ['refresh', 'play']
  }

  def "initialize clears pending work and defers parent purge until after the lifecycle callback"() {
    given:
    driver.runtimeState.pendingGroupCommands = [[command: 'on']]
    driver.runtimeState.activeGroupCommandRequest = [requestId: 'stale', command: 'off']
    driver.state.pendingGroupCommands = [[command: 'legacy-on']]
    driver.state.activeGroupCommandRequest = [requestId: 'legacy', command: 'legacy-off']
    driver.groupDeviceVolumeFadeState.put('SONOS-GROUP-TEST-DNI', [target: 20])
    driver.lastGroupDeviceVolumeFadeCallTime.put('SONOS-GROUP-TEST-DNI', 123L)

    when:
    driver.initialize()

    then:
    !driver.runtimeState.containsKey('pendingGroupCommands')
    !driver.runtimeState.containsKey('activeGroupCommandRequest')
    !driver.state.containsKey('pendingGroupCommands')
    !driver.state.containsKey('activeGroupCommandRequest')
    driver.groupDeviceVolumeFadeState.isEmpty()
    driver.lastGroupDeviceVolumeFadeCallTime.isEmpty()
    driver.unschedules.contains('drainGroupCommandQueue')
    driver.unschedules.contains('retryActiveGroupCommandRequest')
    driver.events.find { Map e -> e.name == 'parentAppRequest' } == null
    driver.scheduled.any { List item ->
      item[1] == 'purgeParentGroupCommandsAfterInitialization' &&
        item[2].data.deviceDni == 'SONOS-GROUP-TEST-DNI'
    }
    driver.scheduled.any { List item -> item[1] == 'refresh' }

    when:
    driver.events.clear()
    driver.purgeParentGroupCommandsAfterInitialization([deviceDni: 'SONOS-GROUP-TEST-DNI'])

    then:
    driver.events.count { Map e -> e.name == 'parentAppRequest' } == 1
    new JsonSlurper().parseText(driver.events.find { Map e -> e.name == 'parentAppRequest' }.value as String).action == 'initializationPurge'
  }

  def "track position preference controls updates received by the group device"() {
    given:
    driver.settings.enableTrackPositionUpdates = false
    driver.settings.onlyUpdateWhenActive = false
    String disabledBatch = JsonOutput.toJson([trackPosition: 42L, trackPositionUpdated: 123456L])

    when:
    driver.updateBatchPlaybackState(disabledBatch)

    then:
    !driver.events.any { Map event -> event.name in ['trackPosition', 'trackPositionUpdated'] }

    when:
    driver.settings.enableTrackPositionUpdates = true
    driver.updateBatchPlaybackState(disabledBatch)
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'trackPosition' }?.value == 42L
    driver.events.find { Map event -> event.name == 'trackPositionUpdated' }?.value == 123456L
  }

  def "track position updates default off when the preference is null"() {
    given:
    driver.settings.remove('enableTrackPositionUpdates')

    when:
    driver.initialize()

    then:
    driver.settings.enableTrackPositionUpdates == false
    driver.getEnableTrackPositionUpdates() == false
  }

  def "disabling track position updates clears existing group attributes"() {
    given:
    driver.settings.enableTrackPositionUpdates = false
    driver.device.dataValues.enableTrackPositionUpdates = 'true'
    driver.device.currentValues.trackPosition = 84L
    driver.device.currentValues.trackPositionUpdated = 123456L

    when:
    driver.synchronizeTrackPositionPreference()
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'trackPosition' }?.value == 0L
    driver.events.find { Map event -> event.name == 'trackPositionUpdated' }?.value == 0L
    driver.device.dataValues.enableTrackPositionUpdates == 'false'
  }

  def "optional playback volumes normalize webCoRE zero and preserve minus one sentinel"() {
    given:
    driver.metaClass.textToSpeech = { String text, String voice ->
      [uri: 'http://example.test/announcement.mp3', duration: 2] as LinkedHashMap
    }

    when:
    driver.playTrack('http://example.test/track.mp3', 0)
    driver.playHighPriorityTrack('http://example.test/announcement.mp3', 0)
    driver.enqueueLowPriorityTrack('http://example.test/queued.mp3', 0)
    driver.playText('test', 0)
    driver.playTextAndRestore('test', 0)
    driver.playTextAndResume('test', 0)
    driver.speak('test', 0)
    driver.speakNoRestore('test', 0)
    driver.playHighPriorityTTS('test', 0)
    driver.playTrackAndRestore('http://example.test/clip.mp3', 0)
    driver.playTrackAndResume('http://example.test/clip.mp3', 0)

    then:
    groupRequests().size() == 11
    groupRequests().every { Map request -> request.args.volume == null }

    when:
    driver.playTrack('http://example.test/track.mp3', -1G)
    driver.playHighPriorityTrack('http://example.test/announcement.mp3', -1G)
    driver.enqueueLowPriorityTrack('http://example.test/queued.mp3', -1G)
    driver.playText('test', -1G)
    driver.playTextAndRestore('test', -1G)
    driver.playTextAndResume('test', -1G)
    driver.speak('test', -1G)
    driver.speakNoRestore('test', -1G)
    driver.playHighPriorityTTS('test', -1G)
    driver.playTrackAndRestore('http://example.test/clip.mp3', -1G)
    driver.playTrackAndResume('http://example.test/clip.mp3', -1G)

    then:
    groupRequests().drop(11).size() == 11
    groupRequests().drop(11).every { Map request -> request.args.volume == '-1' }
  }

  def "group command request IDs stay unique when the in-memory sequence resets"() {
    when:
    driver.requestGroupCommand('refresh')
    String firstRequestId = groupRequests()[0].requestId as String
    driver.runtimeState.remove('groupCommandSequence')
    driver.requestGroupCommand('refresh')
    String secondRequestId = groupRequests()[1].requestId as String

    then:
    UUID.fromString(firstRequestId).toString() == firstRequestId
    UUID.fromString(secondRequestId).toString() == secondRequestId
    firstRequestId != secondRequestId
    groupRequests()*.sequence == [1, 1]
  }

  def "joinPlayersToCoordinator publishes an additive grouping request"() {
    when:
    driver.joinPlayersToCoordinator()

    then:
    groupRequests()[0].command == 'joinPlayersToCoordinator'
    groupRequests()[0].args.groupingMode == 'ADDITIVE'
  }

  def "addPlayerToGroup queues only the supplied player identifier"() {
    when:
    driver.addPlayerToGroup('Bedroom')

    then:
    groupRequests()[0].command == 'addPlayerToGroup'
    groupRequests()[0].args == [playerIdentifier: 'Bedroom']
  }

  def "groupPlayers publishes an explicit request"() {
    when:
    driver.groupPlayers()

    then:
    groupRequests()[0].command == 'groupPlayers'
    groupRequests()[0].args.groupingMode == 'EXPLICIT'
  }

  def "evictUnlistedPlayers marks the request as removal-only"() {
    when:
    driver.evictUnlistedPlayers()

    then:
    groupRequests()[0].command == 'evictUnlistedPlayers'
    groupRequests()[0].args == [
      groupingMode: 'EXPLICIT', evictUnlistedOnly: true
    ]
  }

  def "polled group requests never call the parent and only require the poll"() {
    given:
    driver.parent = new SonosGroupParentDouble() {
      Boolean groupAppRequest(Map request) {
        throw new AssertionError('poll must not call parent')
      }
    }

    when:
    driver.refresh()
    List<Map> batch = driver.flushPendingGroupRequests()

    then:
    batch.size() == 1
    batch[0].command == 'refresh'
    ((SonosGroupParentDouble)driver.parent).requests.empty
    !driver.scheduled.any { List call -> call[1] == 'flushPendingGroupRequests' }
  }

  def "group refresh is also deferred through the request boundary"() {
    when:
    driver.refresh()

    then:
    groupRequests()[0].command == 'refresh'
    driver.events.empty
  }

  def "polled group commands never call the parent synchronously"() {
    given:
    driver.parent = new SonosGroupParentDouble() {
      Boolean groupAppRequest(Map request) {
        throw new AssertionError('poll must not call parent')
      }
    }

    when:
    driver.refresh()

    then:
    groupRequests().size() == 1
    !driver.scheduled.any { List call -> call[1] == 'retryActiveGroupCommandRequest' }
    driver.events.empty
  }

  def "setVolumeZero publishes a zero-volume group command without an argument"() {
    when:
    driver.setVolumeZero()

    then:
    groupRequests()[0].command == 'setVolume'
    groupRequests()[0].args.level == 0
  }

  def "group operation status records the selected grouping mode"() {
    when:
    driver.updateGroupOperationStatus('{"status":"SUCCEEDED"}', 'ADDITIVE')

    then:
    driver.runtimeState.lastGroupingMode == 'ADDITIVE'
    driver.device.dataValues.lastGroupingMode == 'ADDITIVE'
    driver.events.find { Map event -> event.name == 'groupingMode' } == null
    driver.events.find { Map event -> event.name == 'groupOperationStatus' } == null
  }

  def "explicit topology requires an exact roster and still reports extra members"() {
    given:
    driver.device.dataValues.lastGroupingMode = 'EXPLICIT'
    String topology = householdTopology('RINCON_COORD',
      ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'],
      ['Living Room', 'Dining Room', 'Play'])

    when:
    driver.applyAppHouseholdTopology(topology)
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'switch' }?.value == 'off'
    driver.events.find { Map event -> event.name == 'currentlyJoinedPlayers' }?.value == 'Living Room, Dining Room, Play'
  }

  def "explicit topology remains active when the actual roster exactly matches"() {
    given:
    driver.device.dataValues.lastGroupingMode = 'EXPLICIT'
    String topology = householdTopology('RINCON_COORD',
      ['RINCON_COORD', 'RINCON_FOLLOW'], ['Living Room', 'Dining Room'])

    when:
    driver.applyAppHouseholdTopology(topology)
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'switch' }?.value == 'on'
    driver.events.find { Map event -> event.name == 'currentlyJoinedPlayers' }?.value == 'Living Room, Dining Room'
  }

  def "additive topology permits extra members while retaining configured players"() {
    given:
    driver.device.dataValues.lastGroupingMode = 'ADDITIVE'
    String topology = householdTopology('RINCON_COORD',
      ['RINCON_COORD', 'RINCON_FOLLOW', 'RINCON_EXTRA'],
      ['Living Room', 'Dining Room', 'Play'])

    when:
    driver.applyAppHouseholdTopology(topology)
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'switch' }?.value == 'on'
    driver.events.find { Map event -> event.name == 'currentlyJoinedPlayers' }?.value == 'Living Room, Dining Room, Play'
  }

  def "topology is inactive when the configured roster has a different coordinator"() {
    given:
    driver.device.dataValues.lastGroupingMode = 'EXPLICIT'
    String topology = householdTopology('RINCON_FOLLOW',
      ['RINCON_COORD', 'RINCON_FOLLOW'], ['Living Room', 'Dining Room'])

    when:
    driver.applyAppHouseholdTopology(topology)
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'switch' }?.value == 'off'
    driver.events.find { Map event -> event.name == 'currentlyJoinedPlayers' }?.value == 'Living Room, Dining Room'
  }

  def "legacy group state migrates to the DNI runtime map and is cleared from Hubitat state"() {
    given:
    driver.state.groupCommandSequence = 7

    when:
    driver.migrateLegacyRuntimeState()

    then:
    driver.runtimeState.groupCommandSequence == 7
    driver.state.isEmpty()
  }

  def "refresh migrates legacy group variables before queuing its parent request"() {
    given:
    driver.state.groupCommandSequence = 8

    when:
    driver.refresh()

    then:
    !driver.state.containsKey('groupCommandSequence')
    driver.runtimeState.groupCommandSequence == 9
    UUID.fromString(groupRequests().last().requestId as String).toString() ==
      groupRequests().last().requestId
    groupRequests().last().command == 'refresh'
  }

  def "refreshing an inactive shared-coordinator group does not copy playback and resets configured attributes"() {
    given:
    driver.settings.onlyUpdateWhenActive = true
    driver.settings.resetAttributesWhenInactive = true
    driver.device.currentValues.switch = 'off'

    when:
    driver.applyRefreshedPlaybackState(JsonOutput.toJson([
      status: 'playing', transportStatus: 'playing', currentTrackName: 'Only the Good Die Young',
      currentArtistName: 'Billy Joel', volume: 26, mute: 'unmuted', currentlyJoinedPlayers: 'Living Room, Kitchen, Dining Room'
    ]))
    drainBatchEvents()

    then:
    driver.device.currentValue('switch') == 'off'
    driver.events.find { Map event -> event.name == 'status' }?.value == 'inactive'
    driver.events.find { Map event -> event.name == 'transportStatus' }?.value == 'stopped'
    driver.events.find { Map event -> event.name == 'currentTrackName' }?.value == 'n/a'
    !driver.events.any { Map event -> event.name == 'status' && event.value == 'playing' }
    !driver.events.any { Map event -> event.name == 'transportStatus' && event.value == 'playing' }
    driver.events.find { Map event -> event.name == 'currentlyJoinedPlayers' }?.value == 'Living Room, Kitchen, Dining Room'
  }

  def "inactive refresh leaves playback attributes unchanged when inactive resets are disabled"() {
    given:
    driver.settings.onlyUpdateWhenActive = true
    driver.settings.resetAttributesWhenInactive = false
    driver.device.currentValues.switch = 'off'
    driver.device.currentValues.status = 'paused'
    driver.device.currentValues.transportStatus = 'paused'
    driver.device.currentValues.currentTrackName = 'Existing track'

    when:
    driver.applyRefreshedPlaybackState(JsonOutput.toJson([
      status: 'playing', transportStatus: 'playing', currentTrackName: 'Coordinator track'
    ]))

    then:
    driver.events.find { Map event -> event.name == 'status' } == null
    driver.events.find { Map event -> event.name == 'transportStatus' } == null
    driver.events.find { Map event -> event.name == 'currentTrackName' } == null
    driver.device.currentValue('status') == 'paused'
    driver.device.currentValue('transportStatus') == 'paused'
    driver.device.currentValue('currentTrackName') == 'Existing track'
  }

  def "explicit refresh still hydrates an inactive group when active-only updates are disabled"() {
    given:
    driver.settings.onlyUpdateWhenActive = false
    driver.settings.resetAttributesWhenInactive = true
    driver.device.currentValues.switch = 'off'

    when:
    driver.applyRefreshedPlaybackState(JsonOutput.toJson([
      status: 'playing', transportStatus: 'playing', currentTrackName: 'Coordinator track', volume: 26, mute: 'unmuted'
    ]))
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'status' }?.value == 'playing'
    driver.events.find { Map event -> event.name == 'transportStatus' }?.value == 'playing'
    driver.events.find { Map event -> event.name == 'currentTrackName' }?.value == 'Coordinator track'
    driver.events.find { Map event -> event.name == 'volume' }?.value == 26
    driver.events.find { Map event -> event.name == 'mute' }?.value == 'unmuted'
  }

  def "explicit refresh hydrates playback while the group is active"() {
    given:
    driver.settings.onlyUpdateWhenActive = true
    driver.settings.resetAttributesWhenInactive = true
    driver.device.currentValues.switch = 'on'

    when:
    driver.applyRefreshedPlaybackState(JsonOutput.toJson([
      status: 'playing', transportStatus: 'playing', currentTrackName: 'Active group track'
    ]))
    drainBatchEvents()

    then:
    driver.events.find { Map event -> event.name == 'status' }?.value == 'playing'
    driver.events.find { Map event -> event.name == 'transportStatus' }?.value == 'playing'
    driver.events.find { Map event -> event.name == 'currentTrackName' }?.value == 'Active group track'
  }

  def "deprecated diagnostic states are removed during initialization"() {
    given:
    driver.device.currentValues.groupCommandRequest = '{"command":"refresh"}'
    driver.device.currentValues.groupingMode = 'ADDITIVE'
    driver.device.currentValues.groupOperationStatus = '{"status":"FAILED"}'

    when:
    driver.initialize()

    then:
    !driver.device.currentValues.containsKey('groupCommandRequest')
    !driver.device.currentValues.containsKey('groupingMode')
    !driver.device.currentValues.containsKey('groupOperationStatus')
  }

  def "TTS voice cache update logs successfully"() {
    when:
    driver.updateTTSVoiceCache(['Matthew', 'Joanna'], 'Matthew')

    then:
    driver.logs.any { String entry -> entry.contains('DEBUG') && entry.contains('TTS voice cache updated: 2 voices') }
  }
}
