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
  Boolean provisioningAccepted = true
  Map submitPlayerProvisioningRequest(Map request) { provisioningRequest = request; return [accepted: provisioningAccepted] }
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
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyEnvelope(KITCHEN_GROUP)),
      [requestId: 'unknown-request', sourceIp: '192.168.1.33', sessionId: 'superseded-session'])

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

  def 'one topology request discovers every supported player and secondary with no per-speaker requests'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()
    String sessionId = app.atomicState.discoverySessionId.toString()

    when:
    app.processDiscoveredSonosDevice('192.168.1.35')
    Map topologyRequest = app.asyncCalls.find { Map call -> call.cb == 'quickTopologyCallback' }
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyEnvelope(FULL_TOPOLOGY)), topologyRequest.data as Map)

    then:
    app.asyncCalls.size() == 1
    topologyRequest.verb == 'POST'
    topologyRequest.params.uri == 'http://192.168.1.35:1400/ZoneGroupTopology/Control'
    app.discoveredSonoses.keySet() == ['48A6B805F0AF', '542A1B66F5B2', '542A1B671954', 'AABBCCDDEEFF'] as Set
    app.discoveredSonoses['48A6B805F0AF'].with {
      id == 'RINCON_48A6B805F0AF01400' && name == 'Arc' && deviceIp == '192.168.1.35' &&
        hostId == 'RINCON_48A6B805F0AF01400' && upnpPort == 1400 && zoneKind == 'primary' && eventTransport == 'upnp' &&
        groupId == 'RINCON_48A6B805F0AF01400:2727180044' && coordinatorId == 'RINCON_48A6B805F0AF01400'
    }
    app.discoveredSonosSecondaries.keySet() == ['F0F6C10B791A', '542A1B5EC37E'] as Set
    app.discoveredSonosSecondaries['F0F6C10B791A'].with {
      primaryDeviceId == 'RINCON_48A6B805F0AF01400' && zoneKind == 'satellite' && deviceIp == '192.168.1.149'
    }
    app.discoveredSonosSecondaries['542A1B5EC37E'].with {
      primaryDeviceId == 'RINCON_542A1B67195401400' && zoneKind == 'stereoPair' && deviceIp == '192.168.1.39'
    }
    app.state.quickTopologyRequests == null
    app.state.discoveredSonosSecondaries.keySet() == ['F0F6C10B791A', '542A1B5EC37E'] as Set
    app.atomicState.discoverySessionId == sessionId
  }

  def 'players on unsupported UPnP ports are skipped but their group and coordinator data is kept'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()
    app.requestQuickTopologyRefresh('192.168.1.50')
    Map topologyRequest = app.asyncCalls.find { Map call -> call.cb == 'quickTopologyCallback' }

    when:
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyEnvelope(AMP_GROUP)), topologyRequest.data as Map)

    then:
    app.discoveredSonoses.keySet() == ['AABBCCDDEEFF'] as Set
    app.discoveredSonoses['AABBCCDDEEFF'].coordinatorId == 'RINCON_AABBCCDDEEFF01500'
    app.discoveredSonoses['AABBCCDDEEFF'].groupId == 'RINCON_AABBCCDDEEFF01500:7'
    app.discoveredSonosSecondaries.isEmpty()
    app.logs.any { String line -> line.contains('RINCON_AABBCCDDEEFF01500') && line.contains('UPnP port 1500 is not supported yet') }
  }

  def 'a member with an unrecognized RINCON is ignored with a warning'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()
    app.requestQuickTopologyRefresh('192.0.2.2')
    Map topologyRequest = app.asyncCalls.find { Map call -> call.cb == 'quickTopologyCallback' }
    String group = '<ZoneGroup Coordinator="RINCON_MAIN" ID="RINCON_MAIN:1"><ZoneGroupMember UUID="RINCON_MAIN" Location="http://192.0.2.2:1400/xml/device_description.xml" ZoneName="Kitchen"/></ZoneGroup>'

    when:
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyEnvelope(group)), topologyRequest.data as Map)

    then:
    app.discoveredSonoses.isEmpty()
    app.logs.any { String line -> line.startsWith('WARN') && line.contains('unrecognized RINCON RINCON_MAIN') }
  }

  def 'parseRincon accepts the RINCON shape generally and rejects anything else'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)

    expect:
    app.parseRincon(rincon)?.subMap(['mac', 'upnpPort']) == expected

    where:
    rincon                       || expected
    'RINCON_542A1B5D6A7001400'   || [mac: '542A1B5D6A70', upnpPort: 1400]
    'RINCON_542a1b5d6a7001400'   || [mac: '542A1B5D6A70', upnpPort: 1400]
    'RINCON_48A6B8B0417001500'   || [mac: '48A6B8B04170', upnpPort: 1500]
    'RINCON_48A6B8B0417001700'   || [mac: '48A6B8B04170', upnpPort: 1700]
    ' RINCON_542A1B5D6A7001400 ' || [mac: '542A1B5D6A70', upnpPort: 1400]
    'RINCON_542A1B5D6A70014'     || null
    'RINCON_542A1B5D6A700140000' || null
    'RINCON_542A1B5D6A7Z01400'   || null
    'RINCON_542A1B5D6A7011400'   || null
    '542A1B5D6A7001400'          || null
    'RINCON_MAIN'                || null
    ''                           || null
    null                         || null
  }

  def 'only the 1400 host player is supported today and keys stay stable for existing installs'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)

    expect:
    app.isSupportedUpnpPort(1400)
    !app.isSupportedUpnpPort(1500)
    !app.isSupportedUpnpPort(null)
    app.buildPlayerKey('542A1B5D6A70', 1400) == '542A1B5D6A70'
    app.buildPlayerKey('542A1B5D6A70', 1500) == '542A1B5D6A70-1500'
    app.buildHostRincon('542A1B5D6A70') == 'RINCON_542A1B5D6A7001400'
  }

  def 'discovery candidates seed one topology request and are otherwise ignored'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()

    when: 'a non-host SSDP endpoint is seen first'
    app.processParsedSsdpEvent([networkAddress: 'C0A801D2', deviceAddress: '05DC'] as LinkedHashMap)

    then: 'it does not seed a topology request'
    app.asyncCalls.empty

    when: 'the host endpoint of the same IP follows, then repeats and other speakers arrive mid-request'
    app.processParsedSsdpEvent([networkAddress: 'C0A801D2', deviceAddress: '0578'] as LinkedHashMap)
    app.processParsedSsdpEvent([networkAddress: 'C0A801D2', deviceAddress: '0578'] as LinkedHashMap)
    app.processDiscoveredSonosDevice('192.168.1.211')
    app.processDiscoveredSonosDevice('192.168.1.212')

    then: 'exactly one request is in flight'
    app.asyncCalls.size() == 1
    app.asyncCalls[0].params.uri == 'http://192.168.1.210:1400/ZoneGroupTopology/Control'
  }

  def 'a known IP is not probed again and a failed topology request lets the next candidate try'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()
    app.processDiscoveredSonosDevice('192.168.1.200')
    Map first = app.asyncCalls.find { Map call -> call.cb == 'quickTopologyCallback' }

    when: 'the first seed is unreachable'
    app.quickTopologyCallback(new AsyncResponse(status: 500, errorMessage: 'timeout'), first.data as Map)
    app.processDiscoveredSonosDevice('192.168.1.200')
    app.processDiscoveredSonosDevice('192.168.1.35')

    then: 'the failed endpoint stays reserved and the next candidate seeds a new request'
    app.asyncCalls.size() == 2
    app.asyncCalls[1].params.uri == 'http://192.168.1.35:1400/ZoneGroupTopology/Control'
    app.logs.any { String line -> line.startsWith('WARN') && line.contains('topology query failed') }

    when: 'topology succeeds and a known IP is seen again'
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyEnvelope(FULL_TOPOLOGY)), app.asyncCalls[1].data as Map)
    app.processDiscoveredSonosDevice('192.168.1.33')

    then:
    app.asyncCalls.size() == 2
  }

  def 'a candidate seen while a topology request is in flight is tried when that request fails'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.startDiscoverySession()
    app.processDiscoveredSonosDevice('192.168.1.200')
    app.processDiscoveredSonosDevice('192.168.1.201')
    app.processDiscoveredSonosDevice('192.168.1.201')
    Map first = app.asyncCalls.find { Map call -> call.cb == 'quickTopologyCallback' }

    expect: 'only the first is requested while it is in flight'
    app.asyncCalls.size() == 1

    when: 'the first request times out'
    app.quickTopologyCallback(new AsyncResponse(status: 500, errorMessage: 'timeout'), first.data as Map)

    then: 'the queued speaker is requested next, once'
    app.asyncCalls.size() == 2
    app.asyncCalls[1].params.uri == 'http://192.168.1.201:1400/ZoneGroupTopology/Control'

    when: 'the second request lists the whole household, including a queued speaker that is now known'
    app.processDiscoveredSonosDevice('192.168.1.33')
    app.quickTopologyCallback(new AsyncResponse(status: 200, data: topologyEnvelope(FULL_TOPOLOGY)), app.asyncCalls[1].data as Map)

    then: 'the queue is empty and nothing else is requested'
    app.asyncCalls.size() == 2
  }

  def 'a failed snapshot read does not settle a removal'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.parent = new Expando(getProvisioningSnapshot: { -> throw new IllegalStateException('parent busy') })
    app.state.pendingPlayerTableRefresh = [present: [], absent: ['AABBCCDDEEFF'], attempts: 0]

    when:
    app.pollPlayerTableRefresh()

    then: 'the poll continues instead of reporting the speaker as gone'
    app.state.pendingPlayerTableRefresh.attempts == 1
    !app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }
    app.scheduled.any { List item -> item[1] == 'pollPlayerTableRefresh' }
  }

  def 'child imports legacy secondary cache and acknowledges migration'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble(
      legacyState: [secondaries: ['AABB': [id: 'RINCON_SUB', primaryDeviceId: 'RINCON_MAIN']],
        groupConfigurations: [:]], snapshot: [players: [:]])
    app.parent = parent

    when:
    app.importLegacyDiscoveryState()

    then:
    app.discoveredSonosSecondaries['AABB'].id == 'RINCON_SUB'
    app.state.discoveredSonosSecondaries['AABB'].id == 'RINCON_SUB'
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

  def 'the speaker table refreshes once the parent reports the created player'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.discoveredSonoses['AABBCCDDEEFF'] = discovered('RINCON_AABBCCDDEEFF01400', 'Kitchen', '192.0.2.2')
    app.queuePlayerCreation('AABBCCDDEEFF')

    expect: 'a poll is armed but nothing has refreshed yet'
    app.scheduled.any { List item -> item[0] == 2 && item[1] == 'pollPlayerTableRefresh' }
    !app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }

    when: 'the parent has not created the device yet'
    app.pollPlayerTableRefresh()

    then: 'it keeps waiting'
    app.state.pendingPlayerTableRefresh.attempts == 1
    !app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }

    when: 'the device appears in the parent snapshot'
    parent.snapshot = [players: [AABBCCDDEEFF: 'Kitchen']]
    app.pollPlayerTableRefresh()

    then:
    app.state.pendingPlayerTableRefresh == null
    app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }
  }

  def 'the speaker table refreshes anyway when the parent never reports the change'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    app.parent = new SonosDiscoveryParentDouble()
    app.state.pendingPlayerTableRefresh = [present: ['AABBCCDDEEFF'], absent: [], attempts: 14]

    when:
    app.pollPlayerTableRefresh()

    then:
    app.state.pendingPlayerTableRefresh == null
    app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }
    app.logs.any { String line -> line.startsWith('WARN') && line.contains('did not report the requested player changes') }
  }

  def 'confirming a delete also waits for the parent before refreshing the table'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    parent.snapshot = [players: [AABBCCDDEEFF: 'Kitchen'], playerRows: [], playerNames: [:], groups: []]
    app.parent = parent

    when:
    app.queuePlayerRemoval(['AABBCCDDEEFF'])
    app.pollPlayerTableRefresh()

    then: 'the device is still installed, so the poll continues'
    app.state.pendingPlayerTableRefresh.absent == ['AABBCCDDEEFF']
    !app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }

    when:
    parent.snapshot = [players: [:]]
    app.pollPlayerTableRefresh()

    then:
    app.app.events.any { Map event -> event.name == 'newUiSpeakerTable' }
  }

  def 'player creation submits one detached request for one player straight from topology data'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.discoveredSonoses['AABBCCDDEEFF'] = discovered('RINCON_AABBCCDDEEFF01400', 'Kitchen', '192.0.2.2')
    app.discoveredSonoses['112233445566'] = discovered('RINCON_11223344556601400', 'Garage', '192.0.2.3')
    app.discoveredSonosSecondaries['F0F6C10B791A'] = [id: 'RINCON_F0F6C10B791A01400', primaryDeviceId: 'RINCON_AABBCCDDEEFF01400',
      deviceIp: '192.0.2.9', zoneKind: 'satellite', upnpPort: 1400, hostId: 'RINCON_F0F6C10B791A01400']
    app.discoveredSonosSecondaries['0A0B0C0D0E0F'] = [id: 'RINCON_0A0B0C0D0E0F01400', primaryDeviceId: 'RINCON_11223344556601400',
      deviceIp: '192.0.2.10', zoneKind: 'satellite', upnpPort: 1400, hostId: 'RINCON_0A0B0C0D0E0F01400']

    when:
    app.queuePlayerCreation('AABBCCDDEEFF')

    then: 'nothing is read from the speaker and one request names one player and only its own secondaries'
    app.asyncCalls.isEmpty()
    parent.provisioningRequest.schemaVersion == 2
    parent.provisioningRequest.dni == 'AABBCCDDEEFF'
    parent.provisioningRequest.player.with {
      id == 'RINCON_AABBCCDDEEFF01400' && deviceIp == '192.0.2.2' && hostId == 'RINCON_AABBCCDDEEFF01400' && upnpPort == 1400
    }
    parent.provisioningRequest.secondaries.keySet() == ['F0F6C10B791A'] as Set
    !parent.provisioningRequest.containsKey('selected')
    app.state.playerRequestMessage == 'Speaker creation has been queued.'
    app.scheduled.any { List item -> item[1] == 'pollPlayerTableRefresh' }
  }

  def 'a speaker that is not in the discovery results is not queued'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent

    when:
    app.queuePlayerCreation('OFFLINE000000')

    then:
    app.asyncCalls.isEmpty()
    parent.provisioningRequest == null
    app.state.playerRequestMessage == 'Speaker creation could not be queued.'
  }

  def 'a request the parent rejects is reported and does not arm a table refresh'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    parent.provisioningAccepted = false
    app.parent = parent
    app.discoveredSonoses['AABBCCDDEEFF'] = discovered('RINCON_AABBCCDDEEFF01400', 'Kitchen', '192.0.2.2')

    when:
    app.queuePlayerCreation('AABBCCDDEEFF')

    then:
    app.state.playerRequestMessage == 'Speaker creation could not be queued.'
    !app.scheduled.any { List item -> item[1] == 'pollPlayerTableRefresh' }
  }

  def 'there is no way left to create or remove several players at once'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.discoveredSonoses['AABBCCDDEEFF'] = discovered('RINCON_AABBCCDDEEFF01400', 'Kitchen', '192.0.2.2')
    app.discoveredSonoses['112233445566'] = discovered('RINCON_11223344556601400', 'Garage', '192.0.2.3')
    app.settings.playerDevices = ['AABBCCDDEEFF', '112233445566']

    when:
    app.appButtonHandler('createSelectedPlayers')
    app.appButtonHandler('removeUnselectedPlayers')

    then:
    parent.provisioningRequest == null
    parent.removalRequest == null
    app.respondsTo('submitSelectedPlayers').isEmpty()
    app.respondsTo('submitPlayerRemoval').isEmpty()
  }

  def 'add button creates only the clicked speaker'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.discoveredSonoses['AABBCCDDEEFF'] = discovered('RINCON_AABBCCDDEEFF01400', 'Kitchen', '192.0.2.2')
    app.discoveredSonoses['112233445566'] = discovered('RINCON_11223344556601400', 'Garage', '192.0.2.3')

    when:
    app.appButtonHandler('newUiCreatePlayer|112233445566')

    then:
    parent.provisioningRequest.dni == '112233445566'
    parent.provisioningRequest.player.id == 'RINCON_11223344556601400'
    app.state.playerRequestMessage == 'Speaker creation has been queued.'
  }

  def 'mobile add button is normalized to the same handler'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.discoveredSonoses['112233445566'] = discovered('RINCON_11223344556601400', 'Garage', '192.0.2.3')

    when:
    app.appButtonHandler('newUiMobileCreatePlayer|112233445566')

    then:
    parent.provisioningRequest.dni == '112233445566'
  }

  def 'delete button only stages the speaker until removal is confirmed'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent

    when:
    app.appButtonHandler('newUiDeletePlayer|AABBCCDDEEFF')

    then:
    app.state.pendingNewUiDeletePlayer == 'AABBCCDDEEFF'
    !parent.removalRequest

    when:
    app.appButtonHandler('btnCancelNewUiDeletePlayer')

    then:
    !app.state.pendingNewUiDeletePlayer
    !parent.removalRequest
  }

  def 'confirming a staged delete removes only that speaker'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    parent.snapshot = [players: [AABBCCDDEEFF: 'Kitchen', '112233445566': 'Garage'], playerRows: [], playerNames: [:], groups: []]
    app.parent = parent
    app.settings.playerDevices = ['AABBCCDDEEFF', '112233445566']
    app.discoveredSonoses['AABBCCDDEEFF'] = [id: 'RINCON_MAIN', name: 'Kitchen', deviceIp: '192.0.2.2']
    app.discoveredSonoses['112233445566'] = [id: 'RINCON_NEW', name: 'Garage', deviceIp: '192.0.2.3']
    app.state.pendingNewUiDeletePlayer = '112233445566'

    when:
    app.appButtonHandler('btnConfirmNewUiDeletePlayer')

    then:
    parent.removalRequest.remove == ['112233445566']
    !app.state.pendingNewUiDeletePlayer
  }

  def 'group delete button deletes the clicked group even when another is already selected'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    app.parent = parent
    app.state.groupConfigurations = [Family: [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']],
      Party: [groupCoordinatorId: 'RINCON_MAIN', playerIds: ['RINCON_FOLLOW']]]
    app.settings.managedGroupSelection = 'Party'
    String token = app.newUiGroupToken('Family')

    when:
    app.appButtonHandler('newUiDeleteGroup|' + token)

    then:
    parent.groupManagementRequest.action == 'delete'
    parent.groupManagementRequest.name == 'Family'
    app.state.groupConfigurations.keySet() == ['Party'] as Set
  }

  def 'confirming a delete removes only that speaker even when others are absent from the saved selection'() {
    given:
    HubitatScriptHarness app = ScriptLoader.load(APP_FILE)
    SonosDiscoveryParentDouble parent = new SonosDiscoveryParentDouble()
    parent.snapshot = [players: [AABBCCDDEEFF: 'Kitchen', '112233445566': 'Garage'], playerRows: [], playerNames: [:], groups: []]
    app.parent = parent
    app.settings.playerDevices = ['AABBCCDDEEFF']
    app.discoveredSonoses['AABBCCDDEEFF'] = [id: 'RINCON_MAIN', name: 'Kitchen', deviceIp: '192.0.2.2']
    app.state.pendingNewUiDeletePlayer = 'AABBCCDDEEFF'

    when:
    app.appButtonHandler('btnConfirmNewUiDeletePlayer')

    then:
    parent.removalRequest.remove == ['AABBCCDDEEFF']
  }

  // ---------------------------------------------------------------------------
  // Fixtures and helpers
  // ---------------------------------------------------------------------------

  private static final String KITCHEN_GROUP =
    '<ZoneGroup Coordinator="RINCON_542A1B66F5B201400" ID="RINCON_542A1B66F5B201400:2026123718">' +
    '<ZoneGroupMember UUID="RINCON_542A1B66F5B201400" Location="http://192.168.1.33:1400/xml/device_description.xml" ZoneName="Kitchen" SWGen="2" SSLPort="1443"/>' +
    '</ZoneGroup>'

  // Modeled on a real household payload: an Arc with a Sub Mini satellite, a Kitchen speaker, an invisible
  // stereo-pair partner sharing a channel map, and a supported player grouped under another group's id.
  private static final String FULL_TOPOLOGY =
    '<ZoneGroup Coordinator="RINCON_48A6B805F0AF01400" ID="RINCON_48A6B805F0AF01400:2727180044">' +
    '<ZoneGroupMember UUID="RINCON_48A6B805F0AF01400" Location="http://192.168.1.35:1400/xml/device_description.xml" ZoneName="Arc" SWGen="2" HTSatChanMapSet="RINCON_48A6B805F0AF01400:LF,RF;RINCON_F0F6C10B791A01400:SW">' +
    '<Satellite UUID="RINCON_F0F6C10B791A01400" Location="http://192.168.1.149:1400/xml/device_description.xml" ZoneName="Sub Mini" Invisible="1" SWGen="2"/>' +
    '</ZoneGroupMember></ZoneGroup>' +
    KITCHEN_GROUP +
    '<ZoneGroup Coordinator="RINCON_542A1B67195401400" ID="RINCON_48A6B805F0AF01400:2727180043">' +
    '<ZoneGroupMember UUID="RINCON_542A1B5EC37E01400" Location="http://192.168.1.39:1400/xml/device_description.xml" ZoneName="Kids&apos; Room" Invisible="1" SWGen="2" ChannelMapSet="RINCON_542A1B67195401400:RF,RF;RINCON_542A1B5EC37E01400:LF,LF"/>' +
    '<ZoneGroupMember UUID="RINCON_542A1B67195401400" Location="http://192.168.1.34:1400/xml/device_description.xml" ZoneName="Kids&apos; Room" SWGen="2" ChannelMapSet="RINCON_542A1B67195401400:RF,RF;RINCON_542A1B5EC37E01400:LF,LF"/>' +
    '</ZoneGroup>' +
    '<ZoneGroup Coordinator="RINCON_AABBCCDDEEFF01400" ID="RINCON_AABBCCDDEEFF01400:5">' +
    '<ZoneGroupMember UUID="RINCON_AABBCCDDEEFF01400" Location="http://192.168.1.50:1400/xml/device_description.xml" ZoneName="Amp" SWGen="2"/>' +
    '</ZoneGroup>'

  // A supported 1400 zone grouped under a coordinator that is a 1500 zone of the same hardware.
  private static final String AMP_GROUP =
    '<ZoneGroup Coordinator="RINCON_AABBCCDDEEFF01500" ID="RINCON_AABBCCDDEEFF01500:7">' +
    '<ZoneGroupMember UUID="RINCON_AABBCCDDEEFF01400" Location="http://192.168.1.50:1400/xml/device_description.xml" ZoneName="Zone 1" SWGen="2"/>' +
    '<ZoneGroupMember UUID="RINCON_AABBCCDDEEFF01500" Location="http://192.168.1.50:1500/xml/device_description.xml" ZoneName="Zone 2" SWGen="2"/>' +
    '</ZoneGroup>'

  /** Real speakers return the topology as escaped XML text inside ZoneGroupState. */
  private static String topologyEnvelope(String zoneGroups) {
    String inner = "<ZoneGroupState><ZoneGroups>${zoneGroups}</ZoneGroups><VanishedDevices></VanishedDevices></ZoneGroupState>".toString()
    return '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>' +
      '<u:GetZoneGroupStateResponse xmlns:u="urn:schemas-upnp-org:service:ZoneGroupTopology:1"><ZoneGroupState>' +
      groovy.xml.XmlUtil.escapeXml(inner) + '</ZoneGroupState></u:GetZoneGroupStateResponse></s:Body></s:Envelope>'
  }

  private static Map discovered(String rincon, String name, String ip) {
    return [id: rincon, name: name, deviceIp: ip, swGen: '2', hostId: rincon, upnpPort: 1400, zoneKind: 'primary', eventTransport: 'upnp']
  }
}
