package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import hubitat.scheduling.AsyncResponse
import groovy.json.JsonOutput
import spock.lang.Specification

class SonosDiscoveryParentDouble {
  Map legacyState = [:]
  Map snapshot = [players: [:]]
  Map provisioningRequest
  Map removalRequest
  Map groupSyncRequest
  Map groupManagementRequest
  Map groupManagementResult = [status: 'pending']
  Integer acknowledgements = 0
  Boolean childOwner = true

  Map getLegacyDiscoveryState() { return legacyState }
  Map registerDiscoveryManagementChild(Object childId) { return [accepted: true, owner: childOwner, ownerId: '10'] }
  void unregisterDiscoveryManagementChild(Object childId) { }
  void acknowledgeLegacyDiscoveryState() { acknowledgements++ }
  Map getProvisioningSnapshot() { return snapshot }
  Map submitGroupConfigurationSync(Map request) { groupSyncRequest = request; return [accepted: true] }
  Map submitGroupManagementRequest(Map request) { groupManagementRequest = request; return [accepted: true, requestId: request.requestId] }
  Map getGroupManagementResult(String requestId) { return groupManagementResult }
  Map submitPlayerProvisioningRequest(Map request) { provisioningRequest = request; return [accepted: true] }
  Map submitPlayerRemovalRequest(Map request) { removalRequest = request; return [accepted: true] }
}

class SonosAdvancedDiscoveryAppSpec extends Specification {
  private static final File APP_FILE = new File('../Apps/SonosAdvancedDiscoveryApp.groovy')

  def 'management home page loads without calling into its parent'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)

    when:
    Map page = (Map)app.mainPage()

    then:
    page.name == 'mainPage'
    page.title == 'Sonos Discovery & Management'
  }

  def 'discovery scan state and callbacks are isolated in the child app'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.discoveredSonoses['STALE'] = [id: 'RINCON_STALE', name: 'Stale']
    app.atomicState.discoveryRunning = false

    when:
    app.startDiscoverySession()
    String sessionId = app.atomicState.discoverySessionId.toString()
    app.discoveryPlayerInfoCallback(new AsyncResponse(status: 200,
      data: '{"playerId":"RINCON_OLD","device":{"name":"Old"}}'),
      [ipAddress: '192.0.2.1', sessionId: 'superseded-session'])

    then:
    app.atomicState.discoveryRunning == true
    app.atomicState.discoveryEndTime > app.now()
    app.discoveredSonoses.isEmpty()
    app.atomicState.discoverySessionId == sessionId
    app.state.discoveredSonoses.isEmpty()
    app.scheduled.any { List item -> item[0] == 60 && item[1] == 'stopDiscovery' }
    app.asyncCalls.empty
  }

  def 'automatic updates renders a closed HTML disclosure with its settings cards'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)

    when:
    String markup = app.displayAutoUpdateSettingsMarkup()

    then:
    markup.contains("<details class='sonos-update-settings-disclosure'><summary>Automatic updates</summary>")
    markup.indexOf('Check for updates automatically') > markup.indexOf('</summary>')
    markup.endsWith('</div></details>')
    markup.contains(':not(:has(.sonos-update-settings-disclosure[open]))')
    !markup.contains('<script')
  }

  def 'announcements and alarms render separate HTML disclosures that start open'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)

    when:
    String announcements = app.displayAnnouncementSettingsMarkup()
    String alarms = app.displayAlarmSettingsMarkup()

    then:
    announcements.contains("<details class='sonos-managed-disclosure sonos-announcement-disclosure' open><summary>Announcements</summary>")
    announcements.count("<details class='sonos-managed-select'>") == 3
    announcements.endsWith('</div></details>')
    alarms.contains("<details class='sonos-managed-disclosure' open><summary>Alarm management</summary>")
    alarms.endsWith('</div></details>')
  }

  def 'topology fallback adds primary and secondary discovery results to child state'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()
    String sessionId = app.atomicState.discoverySessionId.toString()
    app.requestQuickTopologyRefresh('192.0.2.2')
    Map topologyRequest = app.asyncCalls.find { Map call -> call.cb == 'quickTopologyCallback' }
    String topologyXml = '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:u="urn:schemas-upnp-org:service:ZoneGroupTopology:1">' +
      '<s:Body><u:GetZoneGroupStateResponse><ZoneGroupState><ZoneGroups><ZoneGroup>' +
      '<ZoneGroupMember UUID="RINCON_MAIN" Location="http://192.0.2.2:1400" ZoneName="Kitchen">' +
      '<Satellite UUID="RINCON_SUB" Location="http://192.0.2.3:1400" ModelName="Sub Mini"/></ZoneGroupMember>' +
      '</ZoneGroup></ZoneGroups></ZoneGroupState></u:GetZoneGroupStateResponse></s:Body></s:Envelope>'

    when:
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyXml), topologyRequest.data as Map)
    List<Map> infoRequests = app.asyncCalls.findAll { Map call -> call.cb == 'quickTopologySpeakerInfoCallback' }
    infoRequests.each { Map call ->
      String ip = call.data.ipAddress.toString()
      Boolean secondary = ip.endsWith('.3')
      Map info = [playerId: secondary ? 'RINCON_SUB' : 'RINCON_MAIN', householdId: 'HOUSE', groupId: 'GROUP',
        device: [name: secondary ? 'Sub' : 'Kitchen', primaryDeviceId: secondary ? 'RINCON_MAIN' : null,
          swGen: '2', capabilities: []]]
      app.quickTopologySpeakerInfoCallback(new AsyncResponse(status: 200, data: JsonOutput.toJson(info)), call.data as Map)
    }
    List<Map> descriptions = app.asyncCalls.findAll { Map call -> call.cb == 'quickTopologyDeviceDescriptionCallback' }
    descriptions.eachWithIndex { Map call, Integer index ->
      String mac = index == 0 ? 'AA:BB:CC:DD:EE:01' : 'AA:BB:CC:DD:EE:02'
      String model = index == 0 ? 'Sonos Arc' : 'Sub Mini'
      app.quickTopologyDeviceDescriptionCallback(new AsyncResponse(status: 200,
        data: "<root><device><modelName>${model}</modelName><MACAddress>${mac}</MACAddress></device></root>"), call.data as Map)
    }

    then:
    infoRequests.size() == 2
    descriptions.size() == 2
    app.state.discoveredSonoses.values().any { Map player -> player.id == 'RINCON_MAIN' && player.name == 'Kitchen' }
    app.state.discoveredSonosSecondaries.values().any { Map player -> player.id == 'RINCON_SUB' && player.primaryDeviceId == 'RINCON_MAIN' }
    app.state.quickTopologyRequestId == null
    app.atomicState.discoverySessionId == sessionId
  }

  def 'child imports legacy secondary cache and acknowledges migration'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble(
      legacyState: [secondaries: ['AABB': [id: 'RINCON_SUB', primaryDeviceId: 'RINCON_MAIN']],
        playerDevices: ['AABBCC']], snapshot: [players: [:]])
    app.parent = parent

    when:
    app.importLegacyDiscoveryState()

    then:
    app.discoveredSonosSecondaries['AABB'].id == 'RINCON_SUB'
    app.state.discoveredSonosSecondaries['AABB'].id == 'RINCON_SUB'
    app.app.updatedSettings.playerDevices == ['AABBCC']
    parent.acknowledgements == 1
  }

  def 'duplicate child instance cannot start discovery or take over migration'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble(childOwner: false,
      legacyState: [groupConfigurations: [Family: [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']]]])
    app.parent = parent

    when:
    Map page = (Map)app.discoveryPage()

    then:
    page.title == 'Discovery App Already Active'
    app.atomicState.discoveryRunning != true
    app.state.groupConfigurations == null
    parent.acknowledgements == 0
  }

  def 'child migrates saved group table configuration and queues a parent sync'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble(legacyState: [
      secondaries: [:],
      groupConfigurations: [Family: [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']]]
    ])
    app.parent = parent

    when:
    app.importLegacyDiscoveryState()

    then:
    app.state.groupConfigurations.Family == [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']]
    parent.groupSyncRequest.groups == app.state.groupConfigurations
    parent.acknowledgements == 1
  }

  def 'player and group tables render from the child cache and parent device snapshot'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.state.groupConfigurations = [Family: [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']]]
    Map snapshot = [
      playerRows: [[name: 'Kitchen', model: 'Sonos Arc', id: 'RINCON_MAIN', ip: '192.0.2.2',
        secondaries: [[id: 'RINCON_SUB', deviceIp: '192.0.2.3']]]],
      playerNames: [RINCON_MAIN: 'Kitchen', RINCON_FOLLOW: 'Bedroom'],
      groups: []
    ]

    expect:
    app.renderManagementPlayerTable(snapshot).contains('RINCON_SUB')
    app.renderManagementPlayerTable(snapshot).contains('Sonos Arc')
    app.renderManagementGroupTable(snapshot).contains('Family')
    app.renderManagementGroupTable(snapshot).contains('Bedroom')
  }

  def 'discovery management uses the former single-app custom player and group layouts'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    parent.snapshot = [
      playerRows: [[deviceNetworkId: 'AA:BB:CC:DD:EE:01', name: 'Kitchen', model: 'Sonos Arc',
        id: 'RINCON_MAIN', ip: '192.0.2.2', secondaries: [[id: 'RINCON_SUB', deviceIp: '192.0.2.3']]],
        [deviceNetworkId: 'AA:BB:CC:DD:EE:02', name: 'Kitchen Sub', model: 'Sonos Sub',
          id: 'RINCON_SUB', ip: '192.0.2.3', secondaries: []]],
      playerNames: [RINCON_MAIN: 'Kitchen', RINCON_FOLLOW: 'Bedroom'],
      groups: [[name: 'Family', coordinatorId: 'RINCON_MAIN', followerIds: ['RINCON_FOLLOW']]]
    ]
    app.parent = parent
    app.state.groupConfigurations = [Family: [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']]]

    when:
    String speakerMarkup = app.displayNewUiSpeakerTable()
    String groupMarkup = app.displayNewUiGroupTable()

    then:
    speakerMarkup.contains("class='new-ui-device-card'")
    speakerMarkup.contains("id='new-ui-speaker-table'")
    speakerMarkup.contains('Discover speakers')
    speakerMarkup.contains('RINCON_SUB')
    !speakerMarkup.contains('Kitchen Sub')
    groupMarkup.contains("class='new-ui-groups-card'")
    groupMarkup.contains("id='new-ui-group-table'")
    groupMarkup.contains('Family')
  }

  def 'discovery page has no next-page navigation to the legacy speaker selection screen'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.parent = new SonosDiscoveryParentDouble()
    app.state.managementOwner = true

    when:
    Map page = (Map)app.discoveryPage()

    then:
    page.name == 'discoveryPage'
    page.title == ''
    !page.containsKey('nextPage')
  }

  def 'child group editor submits changes through the parent queue and stores table state locally'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.state.managementOwner = true
    app.settings.managedGroupEditorPayload = JsonOutput.toJson([
      name: 'Cora’s room + Kitchen', coordinator: 'RINCON_MAIN', followers: ['RINCON_FOLLOW']
    ])

    when:
    Map page = app.discoveryPage()

    then:
    page.name == 'discoveryPage'
    parent.groupManagementRequest.action == 'upsert'
    parent.groupManagementRequest.name == 'Cora’s room + Kitchen'
    parent.groupManagementRequest.playerIds == ['RINCON_FOLLOW']
    app.state.groupConfigurations['Cora’s room + Kitchen'].groupCoordinatorId == 'RINCON_MAIN'
    !app.state.groupEditorOpen
    app.state.pendingGroupDeviceAcknowledgement.requestId == parent.groupManagementRequest.requestId
  }

  def 'group creation acknowledgement refreshes the server-rendered group table after device creation'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.state.pendingGroupDeviceAcknowledgement = [requestId: 'request-1', name: 'Family', attempts: 0]
    parent.groupManagementResult = [status: 'created', name: 'Family', deviceId: '42']

    when:
    app.pollGroupDeviceAcknowledgement()

    then:
    app.state.pendingGroupDeviceAcknowledgement == null
    app.app.events.any { Map event -> event.name == 'newUiGroupsTable' && event.value == '42' }
  }

  def 'group editor preselects its coordinator and followers in native controls'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.state.groupEditorOriginalName = 'Family'
    app.settings.managedGroupName = "Family's group"
    app.settings.managedGroupCoordinator = 'RINCON_MAIN'
    app.settings.managedGroupFollowers = ['RINCON_FOLLOW']

    when:
    String markup = app.renderManagedGroupEditor([RINCON_MAIN: 'Kitchen', RINCON_FOLLOW: 'Living & dining'])

    then:
    markup.contains("<div class='new-ui-groups-card sonos-group-editor' data-original-name='Family'")
    markup.contains("<div class='new-ui-group-toolbar-container'><div class='new-ui-group-toolbar'>")
    markup.contains("<div class='new-ui-group-toolbar-title' role='heading' aria-level='3'>Edit Sonos group</div>")
    markup.contains("<div class='sonos-group-editor-fields'><label for='sonos-group-name'>")
    markup.contains("</select></label></div><fieldset onchange=")
    !markup.contains('\n')
    markup.contains("value='Family&#39;s group'")
    markup.contains("<option value='RINCON_MAIN' selected>")
    markup.contains("<label hidden><input type='checkbox' value='RINCON_MAIN'>")
    markup.contains("<input type='checkbox' value='RINCON_FOLLOW' checked>")
    markup.contains('Living &amp; dining')
    markup.contains('settings[managedGroupEditorPayload]')
    markup.contains('payload.dispatchEvent(new Event(')
    !markup.contains('buttonClick(this)')
    markup.contains("onclick=\"this.closest('.sonos-group-editor').hidden = true\">Cancel</button>")
    !markup.contains("name='settings[cancelManagedGroup]'")
  }

  def 'group creation opens a hidden editor without a Hubitat button submission'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.parent = new SonosDiscoveryParentDouble()

    when:
    String table = app.displayNewUiGroupTable()
    String editor = app.renderManagedGroupEditor([:], false)

    then:
    table.contains("class='sonos-local-action new-ui-group-create-button'")
    table.contains("document.getElementById('sonos-group-editor-open')")
    !table.contains("name='settings[btnNewUiCreateGroup]'")
    editor.contains("data-auto-name='' hidden onclick='event.stopPropagation()'")
    editor.contains("<span id='sonos-group-editor-open' hidden")
    table.contains('opener.onclick()')
    !table.contains('opener.click()')
    !editor.contains(".click()")
  }

  def 'invalid group submission keeps the editor open and skips the parent queue'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.state.managementOwner = true
    app.settings.managedGroupEditorPayload = JsonOutput.toJson([
      name: 'Family', coordinator: 'RINCON_MAIN', followers: []
    ])

    when:
    app.consumeManagedGroupSave()

    then:
    app.state.groupEditorOpen == true
    app.state.groupManagementMessage.contains('follower')
    !parent.groupManagementRequest
  }

  def 'player creation request crosses to the parent as a detached versioned DTO'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.settings.playerDevices = ['AABBCCDDEEFF']
    app.discoveredSonoses['AABBCCDDEEFF'] = [id: 'RINCON_MAIN', name: 'Kitchen',
      deviceIp: '192.0.2.2', localApiUrl: 'https://192.0.2.2:1443/api/v1/',
      localUpnpUrl: 'http://192.0.2.2:1400', localUpnpHost: '192.0.2.2:1400',
      websocketUrl: 'wss://192.0.2.2:1443/websocket']

    when:
    app.submitSelectedPlayers()

    then:
    parent.provisioningRequest.schemaVersion == 1
    parent.provisioningRequest.selected == ['AABBCCDDEEFF']
    parent.provisioningRequest.players['AABBCCDDEEFF'].id == 'RINCON_MAIN'
    app.state.playerRequestMessage == 'Speaker creation has been queued.'
  }
}
