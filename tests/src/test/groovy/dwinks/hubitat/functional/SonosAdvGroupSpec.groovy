package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class SonosGroupParentDouble {
  List<Map> requests = []

  void enqueueGroupCommandRequest(Map request) {
    requests << request
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
    driver.state.clear()
    driver.device.deviceNetworkId = 'SONOS-GROUP-TEST-DNI'
    driver.device.dataValues.clear()
    driver.device.currentValues.clear()
    driver.device.dataValues.groupCoordinatorId = 'RINCON_COORD'
    driver.device.dataValues.playerIds = 'RINCON_FOLLOW'
  }

  def "group commands publish an asynchronous request instead of looking up the parent"() {
    when:
    driver.on()

    then:
    driver.scheduled.size() == 1
    driver.scheduled[0][0] == 1
    driver.scheduled[0][1] == 'drainGroupCommandQueue'
    driver.state.pendingGroupCommands[0].command == 'on'
    driver.state.pendingGroupCommands[0].groupDni == 'SONOS-GROUP-TEST-DNI'
    driver.state.pendingGroupCommands[0].args.groupingMode == 'EXPLICIT'
  }

  def "joinPlayersToCoordinator publishes an additive grouping request"() {
    when:
    driver.joinPlayersToCoordinator()

    then:
    driver.state.pendingGroupCommands[0].command == 'joinPlayersToCoordinator'
    driver.state.pendingGroupCommands[0].args.groupingMode == 'ADDITIVE'
  }

  def "addPlayerToGroup queues only the supplied player identifier"() {
    when:
    driver.addPlayerToGroup('Bedroom')

    then:
    driver.state.pendingGroupCommands[0].command == 'addPlayerToGroup'
    driver.state.pendingGroupCommands[0].args == [playerIdentifier: 'Bedroom']
  }

  def "groupPlayers publishes an explicit request without the legacy ungroup delay"() {
    when:
    driver.groupPlayers()

    then:
    driver.state.pendingGroupCommands[0].command == 'groupPlayers'
    driver.state.pendingGroupCommands[0].args.groupingMode == 'EXPLICIT'
    driver.scheduled.every { List call -> call[1] != 'regroupSafetyTimeout' }
  }

  def "evictUnlistedPlayers marks the request as removal-only"() {
    when:
    driver.evictUnlistedPlayers()

    then:
    driver.state.pendingGroupCommands[0].command == 'evictUnlistedPlayers'
    driver.state.pendingGroupCommands[0].args == [
      groupingMode: 'EXPLICIT', evictUnlistedOnly: true
    ]
  }

  def "group refresh is also deferred through the request boundary"() {
    when:
    driver.refresh()

    then:
    driver.state.pendingGroupCommands[0].command == 'refresh'
    driver.events.empty
  }

  def "deferred group commands publish an event without entering the parent"() {
    given:
    SonosGroupParentDouble parent = new SonosGroupParentDouble()
    driver.binding.setVariable('parent', parent)
    Map payload = [groupDni: 'SONOS-GROUP-TEST-DNI', requestId: 'request-1', command: 'refresh', args: [:]]

    when:
    driver.emitGroupCommandRequest([payload: payload])

    then:
    parent.requests.empty
    driver.events.empty
    driver.locationEvents.size() == 1
    driver.locationEvents[0].name == 'dwinksSonosAdvancedGroupCommand'
    driver.locationEvents[0].isStateChange == true
    new JsonSlurper().parseText(driver.locationEvents[0].value as String) == payload
  }

  def "group command queue waits for parent acknowledgement before sending the next request"() {
    given:
    driver.requestGroupCommand('speak', [text: 'first'])
    driver.requestGroupCommand('speak', [text: 'second'])

    when:
    driver.drainGroupCommandQueue()
    Map first = (Map)driver.state.activeGroupCommandRequest
    driver.acknowledgeGroupCommand(first.requestId as String)

    then:
    first.command == 'speak'
    driver.state.pendingGroupCommands.size() == 1
    !driver.state.activeGroupCommandRequest
    driver.scheduled.any { List call -> call[1] == 'drainGroupCommandQueue' }
  }

  def "setVolumeZero publishes a zero-volume group command without an argument"() {
    when:
    driver.setVolumeZero()

    then:
    driver.state.pendingGroupCommands[0].command == 'setVolume'
    driver.state.pendingGroupCommands[0].args.level == 0
  }

  def "group operation status records the selected grouping mode"() {
    when:
    driver.updateGroupOperationStatus('{"status":"SUCCEEDED"}', 'ADDITIVE')

    then:
    driver.state.lastGroupingMode == 'ADDITIVE'
    driver.device.dataValues.lastGroupingMode == 'ADDITIVE'
    driver.events.find { Map event -> event.name == 'groupingMode' } == null
    driver.events.find { Map event -> event.name == 'groupOperationStatus' } == null
  }

  def "explicit refresh hydrates legacy playback states while group is inactive"() {
    given:
    driver.settings.onlyUpdateWhenActive = true
    driver.device.currentValues.switch = 'off'

    when:
    driver.applyRefreshedPlaybackState(JsonOutput.toJson([
      status: 'playing', currentTrackName: 'Only the Good Die Young',
      currentArtistName: 'Billy Joel', volume: 26, mute: 'unmuted'
    ]))

    then:
    driver.events.find { Map event -> event.name == 'status' }?.value == 'playing'
    driver.events.find { Map event -> event.name == 'currentTrackName' }?.value == 'Only the Good Die Young'
    driver.events.find { Map event -> event.name == 'currentArtistName' }?.value == 'Billy Joel'
    driver.events.find { Map event -> event.name == 'volume' }?.value == 26
    driver.events.find { Map event -> event.name == 'groupVolume' }?.value == 26
    driver.events.find { Map event -> event.name == 'mute' }?.value == 'unmuted'
    driver.events.find { Map event -> event.name == 'groupMute' }?.value == 'unmuted'
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
    driver.logs.any { String entry -> entry.contains('TRACE') && entry.contains('TTS voice cache updated: 2 voices') }
  }
}
