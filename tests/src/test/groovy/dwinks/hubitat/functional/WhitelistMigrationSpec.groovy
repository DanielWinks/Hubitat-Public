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
    app.state['pendingPlayerStateUpdates'] = []
    app.state['favs'] = ['old': true]  // legacy, should be pruned

    when:
    Map result = app.pruneOrphanedAppPersistence()

    then:
    app.state.containsKey('userGroups')
    app.state.containsKey('pendingPlayerStateUpdates')
    !app.state.containsKey('activeOrphan')
    !app.state.containsKey('favs')
    (result.stateRemoved as List).contains('activeOrphan')
    (result.stateRemoved as List).contains('favs')
  }

  def "app retains Sonos satellite topology and bond reconciliation state"() {
    given:
    app.state['htSatChanMapSetByPlayer'] = ['RINCON_ARC': 'RINCON_ARC:LF,RF;RINCON_SUB:SW']
    app.state['htSatelliteNamesById'] = ['RINCON_SUB': 'Sub Mini']
    app.state['pendingGroupSatelliteReconciliations'] = [:]
    app.state['groupSatelliteReconciliationScheduled'] = true
    app.state['activeGroupSatelliteReconciliation'] = [requestId: 'active']
    app.state['groupSatelliteStatuses'] = ['Arc + Kitchen': [status: 'Pending']]
    app.state['pendingGroupSatelliteVerifications'] = ['active': [satelliteId: 'RINCON_SUB']]

    when:
    Map result = app.pruneOrphanedAppPersistence()

    then:
    app.state['htSatChanMapSetByPlayer']['RINCON_ARC'].contains('RINCON_SUB:SW')
    app.state['htSatelliteNamesById']['RINCON_SUB'] == 'Sub Mini'
    app.state.containsKey('pendingGroupSatelliteReconciliations')
    app.state['groupSatelliteReconciliationScheduled'] == true
    app.state['activeGroupSatelliteReconciliation'].requestId == 'active'
    app.state['groupSatelliteStatuses']['Arc + Kitchen'].status == 'Pending'
    app.state['pendingGroupSatelliteVerifications']['active'].satelliteId == 'RINCON_SUB'
    !((List)result.stateRemoved).intersect([
      'htSatChanMapSetByPlayer', 'htSatelliteNamesById', 'pendingGroupSatelliteReconciliations',
      'groupSatelliteReconciliationScheduled', 'activeGroupSatelliteReconciliation',
      'groupSatelliteStatuses', 'pendingGroupSatelliteVerifications'
    ])
  }

  def "app prunes orphan atomicState keys"() {
    given:
    app.atomicState['discoveryRunning'] = true
    app.atomicState['legacyAtomicOrphan'] = true

    when:
    app.pruneOrphanedAppPersistence()

    then:
    app.atomicState.containsKey('discoveryRunning')
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
    app.settings.containsKey('playerDevices')
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
     'hubSecurityUsername', 'hubSecurityPassword', 'libraryIdSMAPI', 'libraryIdUtilities',
     'playerDevices', 'groupDevices', 'skipOrphanRemoval', 'descriptionTextEnable',
     'newUiGroupName', 'newUiGroupCoordinator', 'newUiGroupFollowers', 'newUiGroupSatellite'].each { String k ->
      app.settings[k] = 'value'
    }

    when:
    app.pruneOrphanedAppPersistence()

    then:
    app.settings.size() == 27
  }

  def "app migrateLegacyAppStateOnce copies discovery keys to atomicState before prune"() {
    given:
    app.state['discoveryRunning'] = true
    app.state['discoveryEndTime'] = 12345L
    app.atomicState.clear()

    when:
    app.migrateLegacyAppStateOnce()
    Map result = app.pruneOrphanedAppPersistence()

    then:
    app.atomicState['discoveryRunning'] == true
    app.atomicState['discoveryEndTime'] == 12345L
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

  def "player prunes orphan device data but keeps sid keys"() {
    given:
    playerDriver.device.dataValues['id'] = 'RINCON_123'
    playerDriver.device.dataValues['sid3'] = 'sid-value'
    playerDriver.device.dataValues['sid3-expires'] = '2024-01-01'
    playerDriver.device.dataValues['orphanData'] = 'gone'
    playerDriver.device.dataValues['localUpnpHost'] = '192.168.1.50'

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    playerDriver.device.dataValues.containsKey('id')
    playerDriver.device.dataValues.containsKey('sid3')
    playerDriver.device.dataValues.containsKey('sid3-expires')
    playerDriver.device.dataValues.containsKey('localUpnpHost')
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
    playerDriver.device.dataValues['sid1-expiresFormatted'] = 'formatted'
    playerDriver.device.dataValues['sid1-expires'] = 'expires'

    when:
    playerDriver.pruneOrphanedDriverPersistence()

    then:
    !playerDriver.device.dataValues.containsKey('sid1-expiresFormatted')
    playerDriver.device.dataValues.containsKey('sid1-expires')
  }

  // ---------- Group Driver ----------

  def "group prunes orphan state and data"() {
    given:
    groupDriver.state['pendingBatchEvents'] = []
    groupDriver.state['orphanState'] = 'x'
    groupDriver.device.dataValues['groupCoordinatorId'] = 'RINCON_COORD'
    groupDriver.device.dataValues['satelliteId'] = 'RINCON_SUB'
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
    groupDriver.device.dataValues['satelliteId'] == 'RINCON_SUB'
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
    secondaryDriver.state.containsKey('websocketConnectPending')
    !secondaryDriver.state.containsKey('orphanState')
    secondaryDriver.device.dataValues.containsKey('id')
    !secondaryDriver.device.dataValues.containsKey('orphanData')
    secondaryDriver.device.currentValues.containsKey('websocketStatus')
    !secondaryDriver.device.currentValues.containsKey('orphanAttr')
    secondaryDriver.settings.containsKey('logEnable')
    !secondaryDriver.settings.containsKey('orphanSetting')
  }
}
