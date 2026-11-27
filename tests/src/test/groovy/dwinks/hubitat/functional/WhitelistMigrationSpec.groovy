package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

class WhitelistMigrationSpec extends Specification {

  @Shared HubitatScriptHarness app
  @Shared HubitatScriptHarness playerDriver
  @Shared HubitatScriptHarness groupDriver
  @Shared HubitatScriptHarness secondaryDriver

  def setupSpec() {
    File appFile = new File('../Apps/SonosAdvancedApp.groovy')
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    app = ScriptLoader.load(appFile, null, true)

    File playerFile = new File('../Drivers/Component/SonosAdvPlayer.groovy')
    assert playerFile.exists(), "Could not find ${playerFile.absolutePath}"
    playerDriver = ScriptLoader.load(playerFile, null, true)

    File groupFile = new File('../Drivers/Component/SonosAdvGroup.groovy')
    assert groupFile.exists(), "Could not find ${groupFile.absolutePath}"
    groupDriver = ScriptLoader.load(groupFile, null, true)

    File secondaryFile = new File('../Drivers/Component/SonosAdvSecondaries.groovy')
    assert secondaryFile.exists(), "Could not find ${secondaryFile.absolutePath}"
    secondaryDriver = ScriptLoader.load(secondaryFile, null, true)
  }

  def setup() {
    // App
    app.state.clear()
    app.atomicState.clear()
    app.settings.clear()
    app.logs.clear()

    // Player
    playerDriver.state.clear()
    playerDriver.atomicState.clear()
    playerDriver.settings.clear()
    playerDriver.device.dataValues.clear()
    playerDriver.device.currentValues.clear()
    playerDriver.logs.clear()

    // Group
    groupDriver.state.clear()
    groupDriver.settings.clear()
    groupDriver.device.dataValues.clear()
    groupDriver.device.currentValues.clear()
    groupDriver.logs.clear()

    // Secondary
    secondaryDriver.state.clear()
    secondaryDriver.settings.clear()
    secondaryDriver.device.dataValues.clear()
    secondaryDriver.device.currentValues.clear()
    secondaryDriver.logs.clear()
  }

  // ---------- App ----------

  def "app prunes orphan state keys not in whitelist"() {
    given:
    app.state['userGroups'] = [:]
    app.state['activeOrphan'] = 'to-be-removed'
    app.state['pendingPlayerStateUpdates'] = []  // retired child->app queue, pruned
    app.state['favs'] = ['old': true]  // legacy, should be pruned

    when:
    Map result = app.pruneOrphanedAppPersistence()

    then:
    app.state.containsKey('userGroups')
    !app.state.containsKey('pendingPlayerStateUpdates')
    !app.state.containsKey('activeOrphan')
    !app.state.containsKey('favs')
    (result.stateRemoved as List).contains('activeOrphan')
    (result.stateRemoved as List).contains('favs')
    (result.stateRemoved as List).contains('pendingPlayerStateUpdates')
  }

  def "app save migrates shared data to atomicState and prunes retired queue and memory keys from state"() {
    given:
    app.state['groupPreferencesByDni'] = [g: [useProportionalVolume: false]]
    app.state['activeGroupTopologyOperation'] = [operationId: 'op']
    app.state['playerReadingsByRincon'] = [A: [volume: 10]]
    app.state['pendingVolumeRecalculation'] = [players: ['A'], groups: [], all: false]
    app.state['groupAnnouncementQueues'] = [G: [pending: []]]

    when:
    app.migrateSharedDataToAtomicState()
    app.pruneOrphanedAppPersistence()

    then: 'shared read-modify-write data moved to atomicState with its value'
    app.atomicState.groupPreferencesByDni == [g: [useProportionalVolume: false]]
    app.atomicState.activeGroupTopologyOperation == [operationId: 'op']
    !app.state.containsKey('groupPreferencesByDni')
    !app.state.containsKey('activeGroupTopologyOperation')

    and: 'caches and queues that now live in app memory are pruned from state'
    !app.state.containsKey('playerReadingsByRincon')
    !app.state.containsKey('pendingVolumeRecalculation')
    !app.state.containsKey('groupAnnouncementQueues')
  }

  def "app prunes orphan atomicState keys"() {
    given:
    app.atomicState['readyForZgt'] = ['DNI-A']
    app.atomicState['legacyAtomicOrphan'] = true

    when:
    app.pruneOrphanedAppPersistence()

    then:
    app.atomicState.containsKey('readyForZgt')
    !app.atomicState.containsKey('legacyAtomicOrphan')
  }

  def "app prunes orphan settings keys"() {
    given:
    app.settings['logLevel'] = 'info'
    app.settings['playerDevices'] = []
    app.settings['orphanSettingFromOldVersion'] = true
    app.settings['chimeBeforeTTS'] = false  // legacy, not whitelisted

    when:
    app.pruneOrphanedAppPersistence()

    then:
    app.settings.containsKey('logLevel')
    !app.settings.containsKey('playerDevices')   // the retired player selection is pruned
    !app.settings.containsKey('orphanSettingFromOldVersion')
    !app.settings.containsKey('chimeBeforeTTS')
  }

  def "app whitelist retains all active settings"() {
    given:
    ['logLevel', 'useOldUi', 'maximumTtsRetention', 'groupAnnouncementMode',
     'highPriorityAnnouncementMode', 'queueChimeSeparatelyFromTTS', 'suppressChimeOnSuccessiveTTS',
     'enableAlarmManagement',
     'enableAlarmSwitches', 'autoCheckUpdates', 'autoInstallUpdates', 'updateCheckFrequency',
     'autoInstallTime', 'autoInstallNextOccurrence', 'autoInstallDayOfWeek',
     'hubSecurityUsername', 'hubSecurityPassword',
     'groupDevices', 'skipOrphanRemoval', 'descriptionTextEnable',
     'newUiGroupName', 'newUiGroupCoordinator', 'newUiGroupFollowers'].each { String k ->
      app.settings[k] = 'value'
    }

    when:
    app.pruneOrphanedAppPersistence()

    then:
    app.settings.size() == 23
  }

  def "app prunes retired discovery state keys"() {
    given:
    app.state['discoveryRunning'] = true
    app.state['discoveryEndTime'] = 12345L

    when:
    Map result = app.pruneOrphanedAppPersistence()

    then:
    !app.state.containsKey('discoveryRunning')
    !app.state.containsKey('discoveryEndTime')
    (result.stateRemoved as List).contains('discoveryRunning')
  }

  def "app prune is idempotent"() {
    given:
    app.state['userGroups'] = [:]
    app.state['orphan1'] = 1
    app.settings['logLevel'] = 'info'
    app.settings['orphanSetting'] = 1

    when:
    Map first = app.pruneOrphanedAppPersistence()
    Map second = app.pruneOrphanedAppPersistence()

    then:
    (first.stateRemoved as List).size() == 1
    (second.stateRemoved as List).size() == 0
    app.settings.containsKey('logLevel')
    !app.settings.containsKey('orphanSetting')
  }

  // ---------- Player Driver ----------

  def "player prunes orphan state"() {
    given:
    playerDriver.state['pendingParentAppTtsRequests'] = [:]
    playerDriver.state['orphanState'] = 'x'
    playerDriver.state['pendingBatchEvents'] = []
    playerDriver.state['lastError'] = 'msg'

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    !playerDriver.state.containsKey('pendingParentAppTtsRequests')
    !playerDriver.state.containsKey('pendingBatchEvents')
    playerDriver.state.containsKey('lastError')
    !playerDriver.state.containsKey('orphanState')
  }

  def "player prunes orphan device data but keeps ZGT subscription data"() {
    given:
    playerDriver.device.dataValues['id'] = 'RINCON_123'
    playerDriver.device.dataValues['zgtSub'] = 'uuid:zgt-subscription'
    playerDriver.device.dataValues['zgtSubExpires'] = '2024-01-01'
    playerDriver.device.dataValues['orphanData'] = 'gone'
    playerDriver.device.dataValues['deviceIp'] = '192.168.1.50'
    playerDriver.device.dataValues['localUpnpHost'] = 'stale endpoint'
    playerDriver.device.dataValues['localUpnpUrl'] = 'http://192.168.1.50:1400'
    playerDriver.device.dataValues['localApiUrl'] = 'https://192.168.1.50:1443/api/v1/'
    playerDriver.device.dataValues['websocketUrl'] = 'wss://192.168.1.50:1443/websocket/api'
    playerDriver.device.dataValues['WS-audioClip'] = 'Subscribed'

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    playerDriver.device.dataValues.containsKey('id')
    playerDriver.device.dataValues.containsKey('zgtSub')
    playerDriver.device.dataValues.containsKey('zgtSubExpires')
    !playerDriver.device.dataValues.containsKey('localUpnpHost')
    !playerDriver.device.dataValues.containsKey('localUpnpUrl')
    !playerDriver.device.dataValues.containsKey('localApiUrl')
    !playerDriver.device.dataValues.containsKey('websocketUrl')
    !playerDriver.device.dataValues.containsKey('WS-audioClip')
    !playerDriver.device.dataValues.containsKey('orphanData')
  }

  def "player prunes orphan current states"() {
    given:
    playerDriver.device.currentValues['status'] = 'playing'
    playerDriver.device.currentValues['trackPosition'] = 42
    playerDriver.device.currentValues['orphanAttribute'] = 'gone'
    playerDriver.device.currentValues['groupVolume'] = 50

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    playerDriver.device.currentValues.containsKey('status')
    playerDriver.device.currentValues.containsKey('trackPosition')
    playerDriver.device.currentValues.containsKey('groupVolume')
    !playerDriver.device.currentValues.containsKey('orphanAttribute')
  }

  def "player prunes orphan settings"() {
    given:
    playerDriver.settings['logLevel'] = 'info'
    playerDriver.settings['disableTrackDataEvents'] = true
    playerDriver.settings['orphanSetting'] = true
    playerDriver.settings['logEnable'] = false // legacy

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    playerDriver.settings.containsKey('logLevel')
    playerDriver.settings.containsKey('disableTrackDataEvents')
    !playerDriver.settings.containsKey('orphanSetting')
    !playerDriver.settings.containsKey('logEnable')
  }

  def "player prunes sid-expiresFormatted via whitelist"() {
    given:
    playerDriver.device.dataValues['avTransport-expiresFormatted'] = 'formatted'
    playerDriver.device.dataValues['avTransport-expires'] = 'expires'

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    !playerDriver.device.dataValues.containsKey('avTransport-expiresFormatted')
    playerDriver.device.dataValues.containsKey('avTransport-expires')
  }

  // ---------- Group Driver ----------

  def "group prunes orphan state and data"() {
    given:
    groupDriver.state['pendingBatchEvents'] = []
    groupDriver.state['orphanState'] = 'x'
    groupDriver.device.dataValues['groupCoordinatorId'] = 'RINCON_COORD'
    groupDriver.device.dataValues['orphanData'] = 'x'
    groupDriver.device.currentValues['status'] = 'playing'
    groupDriver.device.currentValues['orphanAttr'] = 'x'
    groupDriver.settings['logLevel'] = 'info'
    groupDriver.settings['orphanSetting'] = true

    when:
    groupDriver.pruneOrphanedGroupPersistence()

    then:
    !groupDriver.state.containsKey('pendingBatchEvents')
    !groupDriver.state.containsKey('orphanState')
    groupDriver.device.dataValues.containsKey('groupCoordinatorId')
    !groupDriver.device.dataValues.containsKey('orphanData')
    groupDriver.device.currentValues.containsKey('status')
    !groupDriver.device.currentValues.containsKey('orphanAttr')
    groupDriver.settings.containsKey('logLevel')
    !groupDriver.settings.containsKey('orphanSetting')
  }

  def "group prunes legacy group state attributes"() {
    given:
    groupDriver.device.currentValues['groupCommandRequest'] = 'x'
    groupDriver.device.currentValues['groupingMode'] = 'x'
    groupDriver.device.currentValues['status'] = 'playing'

    when:
    groupDriver.pruneOrphanedGroupPersistence()

    then:
    !groupDriver.device.currentValues.containsKey('groupCommandRequest')
    !groupDriver.device.currentValues.containsKey('groupingMode')
    groupDriver.device.currentValues.containsKey('status')
  }

  // ---------- Secondary Driver ----------

  def "secondary prunes orphan state and data"() {
    given:
    secondaryDriver.state['websocketConnectPending'] = true
    secondaryDriver.state['orphanState'] = 'x'
    secondaryDriver.device.dataValues['id'] = 'RINCON_SEC'
    secondaryDriver.device.dataValues['orphanData'] = 'x'
    secondaryDriver.device.currentValues['websocketStatus'] = 'open'
    secondaryDriver.device.currentValues['orphanAttr'] = 'x'
    secondaryDriver.settings['logEnable'] = true
    secondaryDriver.settings['orphanSetting'] = true

    when:
    secondaryDriver.pruneOrphanedSecondaryPersistence()

    then:
    !secondaryDriver.state.containsKey('websocketConnectPending')
    !secondaryDriver.state.containsKey('orphanState')
    secondaryDriver.device.dataValues.containsKey('id')
    !secondaryDriver.device.dataValues.containsKey('orphanData')
    !secondaryDriver.device.currentValues.containsKey('websocketStatus')
    !secondaryDriver.device.currentValues.containsKey('orphanAttr')
    secondaryDriver.settings.containsKey('logEnable')
    !secondaryDriver.settings.containsKey('orphanSetting')
  }
}
