package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class SonosGroupParentDouble {
  List<Map> requests = []
  List<Map> preferenceUpdates = []
  Boolean accepted = true

  Boolean groupAppRequest(Map request) {
    requests << request
    return accepted
  }

  void groupPreferencesUpdated(Map payload) { preferenceUpdates << payload }
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
    driver.PARENT_SEQUENCE_BY_DNI.clear()
    driver.device.deviceNetworkId = 'SONOS-GROUP-TEST-DNI'
    driver.device.dataValues.clear()
    driver.device.currentValues.clear()
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.playerIds = 'RINCON_FOLLOW'
    driver.parent = new SonosGroupParentDouble()
  }

  private List<Map> groupRequests() {
    return ((SonosGroupParentDouble)driver.parent).requests.findAll { Map request -> request.command }
  }

  private String householdTopology(String coordinatorId, List<String> playerIds, List<String> playerNames) {
    return JsonOutput.toJson([groups: [[
      groupId: 'GROUP-1', coordinatorId: coordinatorId,
      playerIds: playerIds, playerNames: playerNames
    ]]])
  }

  def "group preference save schedules the full refresh and sends the snapshot directly"() {
    given:
    driver.metaClass.initialize = { -> }
    driver.metaClass.configure = { -> }
    driver.settings.putAll([
      chimeBeforeStandardPriorityTTS: true,
      chimeBeforeHighPriorityTTS: true,
      disableAlbumArtEvents: false,
      enableTrackPositionUpdates: true
    ])

    when:
    driver.updated()

    then:
    driver.scheduled.any { List call -> call[0] == 1 && call[1] == 'refresh' }
    !driver.scheduled.any { List call -> call[1] == 'publishGroupPreferenceSnapshot' }
    driver.parent.preferenceUpdates.size() == 1
    driver.parent.preferenceUpdates[0].deviceDni == 'SONOS-GROUP-TEST-DNI'
    driver.parent.preferenceUpdates[0].preferences == [
      chimeBeforeStandardPriorityTTS: true,
      chimeBeforeHighPriorityTTS: true,
      disableAlbumArtEvents: false,
      enableTrackPositionUpdates: true,
      useProportionalVolume: true,
      controlUngroupedIndividually: false
    ]
  }

  def "group actions use app requests without publishing control attributes"() {
    when:
    driver.on()

    then:
    driver.events.isEmpty()
    groupRequests()[0].command == 'on'

    when:
    driver.joinPlayersToCoordinator()

    then:
    driver.events.isEmpty()
    groupRequests().last().command == 'joinPlayersToCoordinator'
  }

  def "group driver contains no attribute sendEvent calls"() {
    expect:
    !new File('../Drivers/Component/SonosAdvGroup.groovy').text.contains('sendEvent(')
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
    // direct parent call — poll retired
    driver.parent.requests.size() == 1
    driver.parent.requests[0].command == 'on'
    driver.parent.requests[0].groupDni == 'SONOS-GROUP-TEST-DNI'
    driver.parent.requests[0].args.groupingMode == 'EXPLICIT'
    driver.parent.requests[0].channel == 'groupAppRequest'
    UUID.fromString(driver.parent.requests[0].requestId as String)
    driver.parent.requests[0].sequence == 1
  }

  def "group commands reach the parent in call order"() {
    given:
    driver.parent = new SonosGroupParentDouble()

    when:
    driver.requestGroupCommand('refresh')
    driver.requestGroupCommand('play')

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
    !driver.scheduled.any { List item -> item[1] == 'purgeParentGroupCommandsAfterInitialization' }
    ((SonosGroupParentDouble)driver.parent).requests.count { Map request -> request.action == 'initializationPurge' } == 1
    driver.scheduled.any { List item -> item[1] == 'refresh' }
    driver.events.isEmpty()
  }

  def "group preference snapshot includes both volume settings with documented defaults"() {
    when:
    driver.sendGroupPreferencesToParent()
    Map update = ((SonosGroupParentDouble)driver.parent).preferenceUpdates.last()

    then:
    update.deviceDni == 'SONOS-GROUP-TEST-DNI'
    update.preferences.useProportionalVolume == true
    update.preferences.controlUngroupedIndividually == false
    groupRequests().isEmpty()
  }

  def "group preference snapshot reflects saved volume settings and does not queue a group command"() {
    given:
    driver.settings.useProportionalVolume = false
    driver.settings.controlUngroupedIndividually = true

    when:
    driver.sendGroupPreferencesToParent()
    Map update = ((SonosGroupParentDouble)driver.parent).preferenceUpdates.last()

    then:
    update.preferences.useProportionalVolume == false
    update.preferences.controlUngroupedIndividually == true
    groupRequests().isEmpty()
  }

  def "group volume commands still carry both settings to the app command path"() {
    given:
    driver.settings.useProportionalVolume = false

    when:
    driver.setLevel(40G)

    then:
    groupRequests()[0].command == 'setVolume'
    groupRequests()[0].args.level == 40
    groupRequests()[0].args.useProportionalVolume == false
    groupRequests()[0].args.controlUngroupedIndividually == false
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

  def "group command request IDs stay unique when the in-memory sequence resets with a new epoch"() {
    when:
    driver.requestGroupCommand('refresh')
    String firstRequestId = groupRequests()[0].requestId as String
    driver.PARENT_SEQUENCE_BY_DNI.clear()
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
    !driver.device.dataValues.containsKey('lastGroupingMode')
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

  def "group requests call the parent directly with no outbox or flush"() {
    given:
    List<Map> direct = []
    driver.parent = new SonosGroupParentDouble() {
      Boolean groupAppRequest(Map request) {
        direct << request
        return true
      }
    }

    when:
    driver.refresh()
    driver.refresh()

    then:
    direct*.command == ['refresh', 'refresh']
    direct*.epoch.unique() == [driver.DRIVER_EPOCH]
    direct*.sequence == [1L, 2L]
    !driver.scheduled.any { List call -> call[1] == 'flushPendingGroupRequests' }
  }

  def "group refresh calls the parent directly"() {
    when:
    driver.refresh()

    then:
    groupRequests()[0].command == 'refresh'
    driver.events.empty
  }

  def "group commands schedule no retry or flush job"() {
    given:
    List<Map> direct = []
    driver.parent = new SonosGroupParentDouble() {
      Boolean groupAppRequest(Map request) {
        direct << request
        return true
      }
    }

    when:
    driver.refresh()

    then:
    direct.size() == 1
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

  def "legacy grouping mode and track position preference data are pruned"() {
    given:
    driver.device.dataValues.lastGroupingMode = 'ADDITIVE'
    driver.device.dataValues.enableTrackPositionUpdates = 'false'

    when:
    driver.initialize()

    then:
    !driver.device.dataValues.containsKey('lastGroupingMode')
    !driver.device.dataValues.containsKey('enableTrackPositionUpdates')
    driver.settings.enableTrackPositionUpdates == false
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

  def "refresh migrates legacy group variables before sending its parent request"() {
    given:
    driver.state.groupCommandSequence = 8

    when:
    driver.refresh()

    then:
    !driver.state.containsKey('groupCommandSequence')
    groupRequests().last().sequence == 1L
    UUID.fromString(groupRequests().last().requestId as String).toString() ==
      groupRequests().last().requestId
    groupRequests().last().command == 'refresh'
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
