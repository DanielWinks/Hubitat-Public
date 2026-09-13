package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonOutput
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
    driver.scheduled[0][1] == 'emitGroupCommandRequest'
    driver.scheduled[0][2].data.payload.command == 'on'
    driver.scheduled[0][2].data.payload.groupDni == 'SONOS-GROUP-TEST-DNI'
    driver.scheduled[0][2].data.payload.args.groupingMode == 'EXPLICIT'
  }

  def "joinPlayersToCoordinator publishes an additive grouping request"() {
    when:
    driver.joinPlayersToCoordinator()

    then:
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.command == 'joinPlayersToCoordinator'
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.args?.groupingMode == 'ADDITIVE'
  }

  def "groupPlayers publishes an explicit request without the legacy ungroup delay"() {
    when:
    driver.groupPlayers()

    then:
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.command == 'groupPlayers'
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.args?.groupingMode == 'EXPLICIT'
    driver.scheduled.every { List call -> call[1] != 'regroupSafetyTimeout' }
  }

  def "evictUnlistedPlayers marks the request as removal-only"() {
    when:
    driver.evictUnlistedPlayers()

    then:
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.command == 'evictUnlistedPlayers'
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.args == [
      groupingMode: 'EXPLICIT', evictUnlistedOnly: true
    ]
  }

  def "group refresh is also deferred through the request boundary"() {
    when:
    driver.refresh()

    then:
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.command == 'refresh'
    driver.events.empty
  }

  def "deferred group commands use the parent queue without creating a current-state event"() {
    given:
    SonosGroupParentDouble parent = new SonosGroupParentDouble()
    driver.binding.setVariable('parent', parent)
    Map payload = [groupDni: 'SONOS-GROUP-TEST-DNI', requestId: 'request-1', command: 'refresh', args: [:]]

    when:
    driver.emitGroupCommandRequest([payload: payload])

    then:
    parent.requests == [payload]
    driver.events.empty
  }

  def "setVolumeZero publishes a zero-volume group command without an argument"() {
    when:
    driver.setVolumeZero()

    then:
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.command == 'setVolume'
    driver.scheduled.find { List call -> call[1] == 'emitGroupCommandRequest' }?.getAt(2)?.data?.payload?.args?.level == 0
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
