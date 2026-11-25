/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (danielwinks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

import groovy.transform.Field
import groovy.transform.CompileStatic
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import hubitat.scheduling.AsyncResponse
import java.util.Locale
import java.util.UUID
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringEscapeUtils

definition(
  name: 'Sonos Advanced Controller - Discovery & Management',
  version: '0.18.9',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  parent: 'dwinks:Sonos Advanced Controller',
  category: 'Audio',
  description: 'Discovery and management for Sonos Advanced Controller',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/SonosAdvancedDiscoveryApp.groovy',
  singleThreaded: true
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
  page(name: 'discoveryPage')
}

@Field static final String DISCOVERED_SONOS_SECONDARIES_STATE_KEY = 'discoveredSonosSecondaries'
@Field static final String DISCOVERY_SESSION_ID_KEY = 'discoverySessionId'
@Field static final String DISCOVERY_RUNNING_KEY = 'discoveryRunning'
@Field static final String DISCOVERY_END_TIME_KEY = 'discoveryEndTime'
@Field static final String DISCOVERY_PROBE_ATTEMPTS_KEY = 'discoveryProbeAttempts'
@Field static final String QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY = 'quickTopologyRequestId'
@Field static final String NEW_UI_GROUP_TABLE_EVENT = 'newUiGroupsTable'
@Field static final String NEW_UI_GROUP_DELETE_KEY = 'pendingNewUiDeleteGroup'
@Field static final String QUICK_TOPOLOGY_BATCH_STATE_KEY = 'quickTopologyRefreshBatch'
@Field static final String QUICK_TOPOLOGY_QUERIED_DURING_DISCOVERY_STATE_KEY = 'quickTopologyQueriedDuringDiscovery'
@Field static final List<String> QUICK_TOPOLOGY_DEVICE_ATTRIBUTE_NAMES = [
  'UUID', 'Location', 'ZoneName', 'Icon', 'Configuration', 'SoftwareGeneration',
  'MinCompatibleVersion', 'LegacyCompatibleVersion', 'BootSeq', 'TVConfiguration',
  'WirelessMode', 'HasConfiguredSSID', 'ChannelFreq', 'BehindWifiExtender', 'WifiEnabled',
  'EthLink', 'VoiceConfigState', 'MicEnabled', 'Invisible', 'HdmiCecAvailable',
  'HdmiArcAvailable', 'ModelName', 'ModelDisplayName', 'SWGen'
]
Map getDiscoveredSonoses() {
  if(!(state.discoveredSonoses instanceof Map)) { state.discoveredSonoses = [:] }
  return (Map)state.discoveredSonoses
}

void setDiscoveredSonoses(Map devices) { state.discoveredSonoses = devices ?: [:] }

Map getDiscoveredSonosSecondaries() {
  if(!(state[DISCOVERED_SONOS_SECONDARIES_STATE_KEY] instanceof Map)) {
    state[DISCOVERED_SONOS_SECONDARIES_STATE_KEY] = [:]
  }
  return (Map)state[DISCOVERED_SONOS_SECONDARIES_STATE_KEY]
}

void setDiscoveredSonosSecondaries(Map devices) {
  state[DISCOVERED_SONOS_SECONDARIES_STATE_KEY] = devices ?: [:]
}

void installed() {
  runIn(1, 'initializeAfterInstall')
}

void updated() { initializeAfterInstall() }

void uninstalled() {
  stopDiscovery()
  try { parent.unregisterDiscoveryManagementChild(app.id) } catch(Exception ignored) { }
}

void initializeAfterInstall() {
  importLegacyDiscoveryState()
}

void importLegacyDiscoveryState() {
  try {
    Map ownership = parent.registerDiscoveryManagementChild(app.id) as Map
    state.managementOwner = ownership?.owner == true
    if(state.managementOwner != true) { return }
    Map legacy = parent.getLegacyDiscoveryState() as Map
    if(legacy?.secondaries instanceof Map) {
      Boolean imported = false
      ((Map)legacy.secondaries).each { Object key, Object value ->
        if(key != null && value instanceof Map && !discoveredSonosSecondaries.containsKey(key.toString())) {
          discoveredSonosSecondaries[key.toString()] = new LinkedHashMap((Map)value)
          imported = true
        }
      }
      if(imported) { persistDiscoveredSonosSecondaries() }
    }
    Boolean importedGroups = legacy?.groupConfigurations instanceof Map && !(state.groupConfigurations instanceof Map)
    if(importedGroups) {
      state.groupConfigurations = copyGroupConfigurations((Map)legacy.groupConfigurations)
    }
    if(legacy?.playerDevices != null && settings.playerDevices == null) {
      app.updateSetting('playerDevices', [type: 'enum', value: legacy.playerDevices])
    }
    if(importedGroups) {
      Map sync = parent.submitGroupConfigurationSync([
        schemaVersion: 1,
        requestId: UUID.randomUUID().toString(),
        groups: copyGroupConfigurations((Map)(state.groupConfigurations ?: [:]))
      ]) as Map
      if(sync?.accepted != true) { throw new IllegalStateException('Parent did not accept group configuration migration') }
    }
    parent.acknowledgeLegacyDiscoveryState()
  } catch(Exception e) {
    logWarn("Legacy discovery data migration will retry when the app is opened: ${e.message}")
  }
}

Map mainPage() {
  dynamicPage(name: 'mainPage', title: 'Sonos Discovery & Management', install: true, uninstall: true) {
    section() {
      href(page: 'discoveryPage', title: 'Discover Sonos Speakers', description: 'Search the local network and select speakers to add or remove')
    }
  }
}

Map discoveryPage() {
  importLegacyDiscoveryState()
  if(state.managementOwner != true) {
    return dynamicPage(name: 'discoveryPage', title: 'Discovery App Already Active', install: false, uninstall: false) {
      section('Duplicate child app') {
        paragraph 'Another Sonos Discovery & Management child app instance is active. Remove this duplicate app instance and open the active one.'
      }
    }
  }
  restoreDiscoveredSonosSecondaries()
  startDiscoverySession()
  Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
  Long endTime = atomicState[DISCOVERY_END_TIME_KEY] ? (atomicState[DISCOVERY_END_TIME_KEY] as Long) : now()
  Integer remainingSecs = Math.max(0, ((endTime - now()) / 1000L) as Integer)
  dynamicPage(name: 'discoveryPage', title: 'Sonos Discovery', install: true, uninstall: false) {
    renderNewUiPageContent()
  }
}

void renderNewUiPageContent() {
  section() { paragraph displayNewUiSpeakerTable() }
  if(state.pendingNewUiDeletePlayer) {
    String pendingKey = state.pendingNewUiDeletePlayer.toString()
    Map pending = buildNewUiSpeakerRows().find { Map row -> row.discoveryKey?.toString() == pendingKey || row.id?.toString() == pendingKey }
    section() {
      paragraph "<b style='color:#F44336'>Remove '${escapeNewUiHtml(pending?.name ?: pendingKey)}'?</b>"
      input 'btnConfirmNewUiDeletePlayer', 'button', title: 'Yes, Remove Speaker', submitOnChange: true
      input 'btnCancelNewUiDeletePlayer', 'button', title: 'Cancel', submitOnChange: true
    }
  }
  section() { paragraph displayNewUiGroupTable() }
  if(state.groupEditorOpen == true) {
    section('Create or edit group') {
      Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
      Map<String, String> players = snapshot.playerNames instanceof Map ? (Map<String, String>)snapshot.playerNames : [:]
      input(name: 'managedGroupName', type: 'text', title: 'Group name', required: true, submitOnChange: true)
      input(name: 'managedGroupCoordinator', type: 'enum', title: 'Coordinator', options: players, required: true, submitOnChange: true)
      input(name: 'managedGroupFollowers', type: 'enum', title: 'Followers', options: players, multiple: true, required: true, submitOnChange: true)
      input 'saveManagedGroup', 'button', title: 'Save Group', submitOnChange: true
      input 'cancelManagedGroup', 'button', title: 'Cancel', submitOnChange: true
    }
  }
}

Map<String, Map> copyGroupConfigurations(Map source) {
  Map<String, Map> copy = [:]
  source.each { Object rawName, Object rawDefinition ->
    String name = rawName?.toString()?.trim()
    if(name && rawDefinition instanceof Map) {
      Map definition = (Map)rawDefinition
      List<String> followers = normalizeGroupIds(definition.playerIds)
      String coordinatorId = definition.groupCoordinatorId?.toString()
      if(coordinatorId && !followers.isEmpty()) {
        copy[name] = [groupCoordinatorId: coordinatorId, playerIds: followers]
      }
    }
  }
  return copy
}

List<String> normalizeGroupIds(Object value) {
  if(value instanceof Collection) {
    return ((Collection)value).collect { Object id -> id?.toString()?.trim() }.findAll { String id -> id }.unique()
  }
  if(value) {
    return value.toString().split(',').collect { String id -> id.trim() }.findAll { String id -> id }.unique()
  }
  return []
}

String escapeDiscoveryHtml(Object rawValue) {
  String value = rawValue?.toString() ?: ''
  return value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
      .replace('"', '&quot;').replace("'", '&#39;')
}

String renderManagementPlayerTable(Map snapshot) {
  List<Map> rows = snapshot.playerRows instanceof Collection ? (List<Map>)snapshot.playerRows : []
  StringBuilder html = new StringBuilder("<table class='mdl-data-table'><thead><tr><th>Speaker</th><th>Secondary speakers</th><th>Network</th></tr></thead><tbody>")
  if(rows.isEmpty()) {
    html.append("<tr><td colspan='3'>No Sonos Advanced Player devices have been created yet.</td></tr>")
  } else {
    rows.each { Map row ->
      String secondaries = row.secondaries instanceof Collection
          ? ((Collection<Map>)row.secondaries).collect { Map item ->
              String name = item.modelDisplayName?.toString() ?: item.modelName?.toString() ?: 'Secondary speaker'
              String id = item.id?.toString()
              String ip = item.deviceIp?.toString()
              "${escapeDiscoveryHtml(name)}${ip || id ? "<br><small>${escapeDiscoveryHtml(ip ?: '')}${ip && id ? ' · ' : ''}${escapeDiscoveryHtml(id ?: '')}</small>" : ''}"
            }.join('<br>') : '—'
      html.append("<tr><td><strong>${escapeDiscoveryHtml(row.name)}</strong><br><small>${escapeDiscoveryHtml(row.model ?: '')} · ${escapeDiscoveryHtml(row.id)}</small></td>")
      html.append("<td>${secondaries}</td><td>${escapeDiscoveryHtml(row.ip ?: 'Unknown')}</td></tr>")
    }
  }
  html.append('</tbody></table>')
  return html.toString()
}

String renderManagementGroupTable(Map snapshot) {
  Map<String, Map> rowsByName = copyGroupConfigurations((Map)(state.groupConfigurations ?: [:]))
  List<Map> installed = snapshot.groups instanceof Collection ? (List<Map>)snapshot.groups : []
  installed.each { Map row ->
    String name = row.name?.toString()
    if(name) {
      Map stored = rowsByName[name] ?: [:]
      List<String> ids = normalizeGroupIds(row.followerIds ?: stored.playerIds)
      rowsByName[name] = [groupCoordinatorId: row.coordinatorId?.toString() ?: stored.groupCoordinatorId?.toString(), playerIds: ids]
    }
  }
  StringBuilder html = new StringBuilder("<table class='mdl-data-table'><thead><tr><th>Group</th><th>Coordinator</th><th>Followers</th><th>Device</th></tr></thead><tbody>")
  if(rowsByName.isEmpty()) {
    html.append("<tr><td colspan='4'>No Sonos groups have been created yet.</td></tr>")
  } else {
    rowsByName.each { String name, Map row ->
      String coordinatorId = row.groupCoordinatorId?.toString()
      List<String> followers = normalizeGroupIds(row.playerIds)
      String coordinatorName = snapshot.playerNames instanceof Map
          ? (((Map)snapshot.playerNames)[coordinatorId]?.toString() ?: coordinatorId) : coordinatorId
      List<String> followerNames = followers.collect { String id ->
        snapshot.playerNames instanceof Map ? (((Map)snapshot.playerNames)[id]?.toString() ?: id) : id
      }
      html.append("<tr><td>${escapeDiscoveryHtml(name)}</td><td>${escapeDiscoveryHtml(coordinatorName ?: 'Unknown')}</td>")
      html.append("<td>${followerNames.isEmpty() ? '—' : followerNames.collect { String value -> escapeDiscoveryHtml(value) }.join('<br>')}</td><td>${escapeDiscoveryHtml(name)}</td></tr>")
    }
  }
  html.append('</tbody></table>')
  return html.toString()
}

void appButtonHandler(String buttonName) {
  if(buttonName?.startsWith('newUiMobile')) { buttonName = buttonName.replaceFirst('newUiMobile', 'newUi') }
  if(buttonName == 'btnNewUiDiscoverSpeakers') { startNewUiDiscoverySession(); return }
  if(buttonName == 'btnExtend60') { extendDiscovery(60); return }
  if(buttonName == 'btnExtend300') { extendDiscovery(300); return }
  if(buttonName == 'createSelectedPlayers') { submitSelectedPlayers(); return }
  if(buttonName == 'removeUnselectedPlayers') { submitPlayerRemoval(); return }
  if(buttonName == 'saveManagedGroup') { saveManagedGroup(); return }
  if(buttonName == 'deleteManagedGroup') { deleteManagedGroup(); return }
  if(buttonName == 'cancelManagedGroup') { state.groupEditorOpen = false; return }
  if(buttonName == 'btnNewUiCreateGroup') {
    state.groupEditorOpen = true
    app.updateSetting('managedGroupSelection', [type: 'enum', value: ''])
    app.updateSetting('managedGroupName', [type: 'text', value: ''])
    return
  }
  if(buttonName?.startsWith('newUiEditGroup|')) {
    Map row = findNewUiGroupByToken(buttonName.substring('newUiEditGroup|'.length))
    if(row) {
      state.groupEditorOpen = true
      app.updateSetting('managedGroupSelection', [type: 'enum', value: row.name?.toString() ?: ''])
      app.updateSetting('managedGroupName', [type: 'text', value: row.name?.toString() ?: ''])
      app.updateSetting('managedGroupCoordinator', [type: 'enum', value: row.coordinatorId?.toString() ?: ''])
      app.updateSetting('managedGroupFollowers', [type: 'enum', value: normalizeGroupIds(row.followerIds)])
    }
    return
  }
  if(buttonName?.startsWith('newUiCreateGroup|')) {
    String coordinator = buttonName.substring('newUiCreateGroup|'.length())
    state.groupEditorOpen = true
    app.updateSetting('managedGroupSelection', [type: 'enum', value: ''])
    app.updateSetting('managedGroupName', [type: 'text', value: ''])
    app.updateSetting('managedGroupCoordinator', [type: 'enum', value: coordinator])
    app.updateSetting('managedGroupFollowers', [type: 'enum', value: []])
    return
  }
  if(buttonName?.startsWith('newUiDeleteGroup|')) {
    Map row = findNewUiGroupByToken(buttonName.substring('newUiDeleteGroup|'.length))
    if(row?.name) { app.updateSetting('managedGroupSelection', [type: 'enum', value: row.name.toString()]); deleteManagedGroup() }
    return
  }
  if(buttonName?.startsWith('newUiCreatePlayer|')) {
    String key = buttonName.substring('newUiCreatePlayer|'.length)
    List<String> selected = settings.playerDevices instanceof Collection ? ((Collection)settings.playerDevices).collect { Object v -> v.toString() } : []
    if(!selected.contains(key)) { selected << key }
    app.updateSetting('playerDevices', [type: 'enum', value: selected])
    submitSelectedPlayers()
    return
  }
  if(buttonName?.startsWith('newUiDeletePlayer|')) {
    state.pendingNewUiDeletePlayer = buttonName.substring('newUiDeletePlayer|'.length())
    return
  }
  if(buttonName == 'btnConfirmNewUiDeletePlayer') {
    String key = state.pendingNewUiDeletePlayer?.toString()
    Map row = buildNewUiSpeakerRows().find { Map item -> item.discoveryKey?.toString() == key || item.id?.toString() == key }
    Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
    List<String> selected = settings.playerDevices instanceof Collection ? ((Collection)settings.playerDevices).collect { Object v -> v.toString() } : new ArrayList<String>(((Map)snapshot.players ?: [:]).keySet())
    String dni = row?.discoveryKey?.toString()
    if(dni) { selected.remove(dni) }
    app.updateSetting('playerDevices', [type: 'enum', value: selected])
    submitPlayerRemoval()
    state.remove('pendingNewUiDeletePlayer')
    return
  }
  if(buttonName == 'btnCancelNewUiDeletePlayer') { state.remove('pendingNewUiDeletePlayer'); return }
  if(buttonName == 'btnNewUiSaveGroup') { saveManagedGroup(); state.groupEditorOpen = false; return }
  if(buttonName == 'btnNewUiCancelGroup') { state.groupEditorOpen = false; return }
}

void saveManagedGroup() {
  String selectedGroup = settings.managedGroupSelection?.toString()?.trim()
  String name = settings.managedGroupName?.toString()?.trim()
  String coordinatorId = settings.managedGroupCoordinator?.toString()?.trim()
  List<String> followers = settings.managedGroupFollowers instanceof Collection
      ? ((Collection)settings.managedGroupFollowers).collect { Object id -> id?.toString() }.findAll { String id -> id }
      : normalizeGroupIds(settings.managedGroupFollowers)
  if(!name || !coordinatorId || followers.isEmpty() || followers.contains(coordinatorId)) {
    state.groupManagementMessage = 'Choose a group name, one coordinator, and at least one different follower.'
    return
  }
  Map result = parent.submitGroupManagementRequest([
    schemaVersion: 1, requestId: UUID.randomUUID().toString(), action: 'upsert',
    oldName: selectedGroup, name: name, groupCoordinatorId: coordinatorId, playerIds: followers
  ]) as Map
  if(result?.accepted != true) {
    state.groupManagementMessage = 'The group change could not be queued.'
    return
  }
  Map<String, Map> groups = copyGroupConfigurations((Map)(state.groupConfigurations ?: [:]))
  if(selectedGroup && selectedGroup != name) { groups.remove(selectedGroup) }
  groups[name] = [groupCoordinatorId: coordinatorId, playerIds: followers.unique()]
  state.groupConfigurations = groups
  app.updateSetting('managedGroupSelection', [type: 'enum', value: name])
  app.updateSetting('managedGroupName', [type: 'text', value: name])
  state.groupManagementMessage = 'Group changes have been queued.'
}

void deleteManagedGroup() {
  String name = settings.managedGroupSelection?.toString()?.trim()
  if(!name) { return }
  Map result = parent.submitGroupManagementRequest([
    schemaVersion: 1, requestId: UUID.randomUUID().toString(), action: 'delete', name: name
  ]) as Map
  if(result?.accepted != true) {
    state.groupManagementMessage = 'The group removal could not be queued.'
    return
  }
  Map<String, Map> groups = copyGroupConfigurations((Map)(state.groupConfigurations ?: [:]))
  groups.remove(name)
  state.groupConfigurations = groups
  app.updateSetting('managedGroupSelection', [type: 'enum', value: ''])
  app.updateSetting('managedGroupName', [type: 'text', value: ''])
  state.groupManagementMessage = 'Group removal has been queued.'
}

void submitSelectedPlayers() {
  Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
  List<String> selected = settings.playerDevices instanceof Collection
      ? ((Collection)settings.playerDevices).collect { Object value -> value.toString() } : new ArrayList<String>(((Map)snapshot.players ?: [:]).keySet())
  Map request = [schemaVersion: 1, requestId: UUID.randomUUID().toString(), selected: selected,
      players: copyCandidateInfoMap(discoveredSonoses), secondaries: copyCandidateInfoMap(discoveredSonosSecondaries)]
  Map result = parent.submitPlayerProvisioningRequest(request) as Map
  state.playerRequestMessage = result?.accepted == true ? 'Speaker creation has been queued.' : 'Speaker creation could not be queued.'
}

Map<String, Map> copyCandidateInfoMap(Map source) {
  Map<String, Map> copy = [:]
  source.each { Object key, Object value ->
    if(key instanceof String && value instanceof Map) {
      Map<String, Object> item = [:]
      ((Map)value).each { Object field, Object fieldValue ->
        if(field instanceof String) {
          Object detached = copyCandidateValue(fieldValue, 0)
          if(detached != null) { item[field.toString()] = detached }
        }
      }
      copy[key.toString()] = item
    }
  }
  return copy
}

Object copyCandidateValue(Object value, Integer depth) {
  if(value == null) { return null }
  if(value instanceof String || value instanceof Number || value instanceof Boolean) {
    String textValue = value.toString()
    return textValue.length() <= 4096 ? value : null
  }
  if(depth >= 3) { return null }
  if(value instanceof Map && ((Map)value).size() <= 64) {
    Map<String, Object> copy = [:]
    ((Map)value).each { Object key, Object nested ->
      if(key instanceof String) {
        Object detached = copyCandidateValue(nested, depth + 1)
        if(detached != null) { copy[key.toString()] = detached }
      }
    }
    return copy
  }
  if(value instanceof Collection && ((Collection)value).size() <= 128) {
    List copy = []
    ((Collection)value).each { Object nested ->
      Object detached = copyCandidateValue(nested, depth + 1)
      if(detached != null) { copy.add(detached) }
    }
    return copy
  }
  return null
}

void submitPlayerRemoval() {
  Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
  List<String> existing = new ArrayList<String>(((Map)snapshot.players ?: [:]).keySet())
  List<String> selected = settings.playerDevices instanceof Collection
      ? ((Collection)settings.playerDevices).collect { Object value -> value.toString() } : existing
  List<String> remove = existing.findAll { String dni -> !selected.contains(dni) }
  Map result = parent.submitPlayerRemovalRequest([schemaVersion: 1, requestId: UUID.randomUUID().toString(), remove: remove]) as Map
  state.playerRequestMessage = result?.accepted == true ? 'Speaker removal has been queued.' : 'Speaker removal could not be queued.'
}

void logError(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info', 'warn', 'error']) { log.error("${app.label ?: app.name}: ${message}") } }
void logWarn(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info', 'warn']) { log.warn("${app.label ?: app.name}: ${message}") } }
void logInfo(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info']) { log.info("${app.label ?: app.name}: ${message}") } }
void logDebug(String message) { if(getConfiguredLogLevel() in ['trace', 'debug']) { log.debug("${app.label ?: app.name}: ${message}") } }
String getConfiguredLogLevel() {
  String level = settings.logLevel?.toString()?.toLowerCase(Locale.ROOT)
  return level in ['trace', 'debug', 'info', 'warn', 'error', 'off'] ? level : 'info'
}
void logPayloadAtTrace(String debugMessage, String traceMessage) {
  if(getConfiguredLogLevel() == 'trace') { log.trace("${app.label ?: app.name}: ${traceMessage}") }
  else { logDebug(debugMessage) }
}

Integer convertHexToInt(String hex) { return Integer.parseInt(hex, 16) }
String convertHexToIP(String hex) { return [convertHexToInt(hex[0..1]), convertHexToInt(hex[2..3]), convertHexToInt(hex[4..5]), convertHexToInt(hex[6..7])].join('.') }
String getLocalApiPrefix(String ipAddress) { return "https://${ipAddress}/api/v1" }
String getDNIFromRincon(String rincon) { return rincon?.replace(':', '') }
List<String> splitTokens(String value, String delimiters) { return value ? StringUtils.split(value, delimiters)?.toList() ?: [] : [] }

String displayNewUiSpeakerTable() {
  String css = """
    <style>
      .mdl-grid:has(.sonos-update-card) { padding-bottom: 0 !important; }
      .mdl-cell:has(.sonos-update-card) { margin-bottom: 0 !important; }
      .sonos-update-card { margin-bottom: 0 !important; }
      .mdl-grid:has(.new-ui-device-card) { padding-top: 0 !important; }
      .mdl-cell:has(.new-ui-device-card) { margin-top: 0 !important; }
      .new-ui-device-card {
        background: var(--surface-card, var(--surface-a, #fff));
        border: 1px solid var(--surface-border, #dedede);
        border-radius: 12px;
        box-shadow: 0 2px 10px rgba(25, 35, 55, .07);
        color: var(--text-color, #30343b);
        margin: 0 0 18px;
        overflow: hidden;
      }
      .new-ui-device-toolbar {
        align-items: center;
        background: var(--surface-b, #f7f8fa);
        border-bottom: 1px solid var(--surface-border, #e5e7eb);
        display: flex;
        flex-wrap: wrap;
        gap: 16px;
        justify-content: space-between;
        padding: 15px 18px;
        position: relative;
      }
      .new-ui-device-toolbar-container { border-bottom: 1px solid var(--surface-border, #e5e7eb); }
      .new-ui-device-toolbar > span[class*='discoveryTimer'] {
        align-items: center;
        display: flex;
        flex: 1 1 auto;
        flex-wrap: wrap;
        gap: 16px;
        justify-content: space-between;
        min-width: 0;
      }
      .new-ui-discovery-status {
        align-items: center;
        display: flex;
        gap: 11px;
        min-width: 220px;
      }
      .new-ui-discovery-indicator {
        background: #9aa2ad;
        border-radius: 50%;
        flex: 0 0 10px;
        height: 10px;
        width: 10px;
      }
      .new-ui-discovery-indicator-running {
        animation: new-ui-discovery-pulse 1.6s ease-in-out infinite;
        background: #16a36a;
        box-shadow: 0 0 0 4px rgba(22, 163, 106, .12);
      }
      @keyframes new-ui-discovery-pulse {
        50% { box-shadow: 0 0 0 7px rgba(22, 163, 106, .04); }
      }
      .new-ui-discovery-copy { line-height: 1.35; }
      .new-ui-discovery-title { font-size: 15px; font-weight: 650; }
      .new-ui-device-section-title {
        color: var(--text-color, #30343b);
        flex: 1 1 auto;
        font-size: 17px;
        font-weight: 650;
        text-align: center;
      }
      .new-ui-discovery-detail {
        color: var(--text-color-secondary, #6b7280);
        font-size: 12px;
        margin-top: 2px;
      }
      .new-ui-device-toolbar .form-group:has(input[name='btnNewUiDiscoverSpeakers']) { display: none !important; }
      .new-ui-discovery-button {
        align-items: center;
        background: #4054b2;
        border: 1px solid #4054b2;
        border-radius: 8px;
        color: #fff;
        cursor: pointer;
        display: inline-flex;
        font-size: 14px;
        font-weight: 600;
        gap: 9px;
        line-height: 1.2;
        padding: 10px 14px;
        transition: background .15s ease, box-shadow .15s ease, transform .15s ease;
      }
      .new-ui-discovery-button:hover { background: #34479e; box-shadow: 0 2px 6px rgba(52, 71, 158, .2); }
      .new-ui-discovery-button:active { transform: translateY(1px); }
      .new-ui-discovery-button:focus-visible { outline: 2px solid #1a77c9; outline-offset: 2px; }
      .new-ui-discovery-button svg { height: 16px; width: 16px; }
      #new-ui-speaker-table-wrapper {
        display: block;
        overflow-x: auto;
        padding: 0 !important;
        width: 100%;
      }

      /* Keep only the group editor's action buttons on one row. */
      .form-group:has(input[name='settings[btnNewUiSaveGroup]']),
      .form-group:has(input[name='settings[btnNewUiCancelGroup]']) {
        display: inline-block !important;
        width: auto !important;
        margin: 8px 8px 0 0 !important;
        vertical-align: top;
      }

      .mdl-cell:has(input[name='settings[btnNewUiSaveGroup]']),
      .mdl-cell:has(input[name='settings[btnNewUiCancelGroup]']),
      .mdl-cell:has(button[name='settings[btnNewUiSaveGroup]']),
      .mdl-cell:has(button[name='settings[btnNewUiCancelGroup]']) {
        display: inline-block !important;
        width: auto !important;
        margin: 8px 8px 0 0 !important;
        vertical-align: top;
      }

      input[name='settings[btnNewUiSaveGroup]'],
      input[name='settings[btnNewUiCancelGroup]'],
      button[name='settings[btnNewUiSaveGroup]'],
      button[name='settings[btnNewUiCancelGroup]'] {
        display: inline-block !important;
        width: auto !important;
      }
      #new-ui-speaker-table {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border: 0;
        border-collapse: separate;
        border-spacing: 0;
        border-radius: 0;
        color: var(--text-color, #424242);
        min-width: 760px;
        width: 100%;
      }
      #new-ui-speaker-table th,
      #new-ui-speaker-table td {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border-bottom: 1px solid var(--surface-border, #EEEEEE);
        border-right: 0;
        color: var(--text-color, #424242);
        font-size: 14px !important;
        padding: 12px 14px !important;
        text-align: center;
      }
      #new-ui-speaker-table thead {
        background-color: var(--surface-b, #F5F5F5);
      }
      #new-ui-speaker-table th {
        background-color: var(--surface-b, #F5F5F5);
        border-bottom: 1px solid var(--surface-border, #E0E0E0);
        color: var(--text-color, #424242);
        font-size: 14px !important;
        font-weight: 500;
        padding: 11px 14px !important;
        text-align: center;
        white-space: nowrap;
      }
      #new-ui-speaker-table th.new-ui-speaker-name,
      #new-ui-speaker-table th.new-ui-secondary-speakers {
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-table-toolbar-cell {
        background: var(--surface-b, #f7f8fa);
        padding: 0 18px !important;
        text-align: left;
      }
      #new-ui-speaker-table tbody tr:hover {
        background-color: var(--surface-hover, #f8f9fc) !important;
      }
      #new-ui-speaker-table tbody tr:last-child td {
        border-bottom: 0;
      }
      #new-ui-speaker-table td:first-child .form-group {
        display: none;
      }
      #new-ui-speaker-table td:first-child .submitOnChange {
        line-height: 20px;
        vertical-align: middle;
      }
      #new-ui-speaker-table .new-ui-speaker-name {
        min-width: 170px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-speaker-name a {
        color: #2196F3;
        text-decoration: none;
      }
      #new-ui-speaker-table .new-ui-speaker-name a:hover {
        text-decoration: underline;
      }
      #new-ui-speaker-table .new-ui-speaker-detail {
        color: var(--text-color-secondary, #666666);
        font-size: 11px;
        margin-top: 3px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-speakers {
        min-width: 190px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-count {
        color: var(--text-color, #424242);
        font-size: 11px;
        font-weight: 600;
        margin-bottom: 3px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-entry {
        color: var(--text-color, #424242);
        font-size: 12px;
        line-height: 1.25;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-entry + .new-ui-secondary-entry {
        border-top: 1px solid var(--surface-border, #EEEEEE);
        margin-top: 4px;
        padding-top: 4px;
      }
      #new-ui-speaker-table .new-ui-secondary-detail {
        color: var(--text-color-secondary, #757575);
        font-size: 10px;
      }
      #new-ui-speaker-table .new-ui-status {
        display: inline-block;
        font-weight: 600;
        white-space: nowrap;
      }
      #new-ui-speaker-table .new-ui-status-active {
        color: #2e7d32;
      }
      #new-ui-speaker-table .new-ui-status-inactive {
        color: #c62828;
      }
      #new-ui-speaker-table .new-ui-status-unknown,
      #new-ui-speaker-table .new-ui-status-na {
        color: var(--text-color-secondary, #757575);
      }
      #new-ui-speaker-table .new-ui-action-cell {
        padding-left: 12px !important;
        padding-right: 12px !important;
        width: 64px;
      }
      #new-ui-speaker-table .new-ui-group-action-cell {
        padding-left: 12px !important;
        padding-right: 12px !important;
        width: 110px;
      }
      .new-ui-mobile-list { display: none; }
      .new-ui-mobile-record { border-bottom: 1px solid var(--surface-border, #e5e7eb); padding: 14px 16px; }
      .new-ui-mobile-record:last-child { border-bottom: 0; }
      .new-ui-mobile-field { align-items: flex-start; display: flex; gap: 12px; padding: 5px 0; }
      .new-ui-mobile-label { color: var(--text-color-secondary, #6b7280); flex: 0 0 34%; font-size: 12px; font-weight: 600; }
      .new-ui-mobile-value { flex: 1 1 0; min-width: 0; overflow-wrap: anywhere; text-align: left; }
      .new-ui-mobile-value .new-ui-speaker-detail { overflow-wrap: anywhere; }
      .new-ui-mobile-actions { align-items: center; display: flex; gap: 14px; }
      .new-ui-mobile-record .form-group { display: none !important; }
      .new-ui-mobile-action-button { align-items: center; border: 1px solid transparent; border-radius: 7px; box-sizing: border-box; display: inline-flex !important; font-size: 13px !important; font-weight: 600; justify-content: center; line-height: 1.2; min-height: 38px; padding: 9px 15px; text-decoration: none; }
      .new-ui-mobile-remove-button { background: var(--surface-b, #fff); border-color: #d84a45; color: #c83d38 !important; }
      .new-ui-mobile-create-group-button { background: #4054b2; border-color: #4054b2; color: #fff !important; }
      .new-ui-mobile-speaker-primary { font-size: 16px; line-height: 1.45; text-align: center; }
      .new-ui-mobile-speaker-primary .new-ui-speaker-detail { color: var(--text-color-secondary, #6b7280); font-size: 13px; overflow-wrap: anywhere; }
      .new-ui-mobile-secondary-box { border: 1px solid var(--surface-border, #cfd3da); border-radius: 9px; margin: 12px auto; max-width: 100%; padding: 10px 12px; text-align: center; }
      .new-ui-mobile-secondary-title { color: var(--text-color-secondary, #6b7280); font-size: 12px; font-weight: 650; margin: 0 auto 7px; }
      .new-ui-mobile-secondary-box .new-ui-secondary-count,
      .new-ui-mobile-secondary-box .new-ui-secondary-entry,
      .new-ui-mobile-secondary-box .new-ui-secondary-detail { text-align: center; }
      .new-ui-mobile-secondary-box .new-ui-secondary-detail { color: var(--text-color-secondary, #6b7280); font-size: 11px !important; overflow-wrap: anywhere; }
      .new-ui-mobile-ip { color: var(--text-color, #30343b); font-size: 14px; text-align: center; }
      .new-ui-mobile-ip-label { color: var(--text-color-secondary, #6b7280); font-size: 12px; font-weight: 650; }
      .new-ui-mobile-create-group { display: flex; justify-content: center; margin-top: 10px; }
      .new-ui-mobile-empty { color: var(--text-color-secondary, #6b7280); padding: 16px; }
      @media (max-width: 640px) {
        .new-ui-speaker-mobile-list .new-ui-mobile-record { padding: 16px 14px; text-align: center; }
        .new-ui-mobile-field { align-items: center; flex-direction: column; gap: 3px; padding: 7px 0; text-align: center; }
        .new-ui-mobile-label { flex: 0 0 auto; }
        .new-ui-mobile-value { flex: 1 1 auto; text-align: center; width: 100%; }
        #new-ui-speaker-table-wrapper,
        #new-ui-speaker-table-wrapper > div { overflow: visible !important; }
        #new-ui-speaker-table-wrapper { display: none !important; }
        .new-ui-speaker-mobile-list { display: block !important; }
        .new-ui-device-toolbar {
          align-items: stretch;
          box-sizing: border-box;
          flex-direction: column;
          gap: 10px;
          padding: 12px 14px !important;
        }
        .new-ui-device-toolbar > div[style='display:inline-block'] { display: block !important; width: 100%; }
        .new-ui-device-toolbar > span[class*='discoveryTimer'] { align-items: stretch; flex-direction: column; gap: 10px; width: 100%; }
        .new-ui-device-section-title { order: 1; }
        .new-ui-device-toolbar > span[class*='discoveryTimer'] > .new-ui-discovery-status { order: 2; }
        .new-ui-device-toolbar > span[class*='discoveryTimer'] > .new-ui-discovery-button { order: 3; }
        .new-ui-device-toolbar > span[class*='discoveryTimer'] > div[style='display:inline-block'] { display: block !important; width: 100%; }
        .new-ui-discovery-button { justify-content: center; width: 100%; }
      }
    </style>
  """
  String iconifyScript = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
  return "${css.trim()}${iconifyScript}<div class='new-ui-device-card'><div class='new-ui-device-toolbar-container'><div class='new-ui-device-toolbar'><span class='ssr-app-state-${app.id}-discoveryTimer'>${renderNewUiDiscoveryTimerMarkup()}</span></div></div><span class='ssr-app-state-${app.id}-newUiSpeakerTable'><div id='new-ui-speaker-table-wrapper'>${renderNewUiSpeakerTableMarkup()}</div>${renderNewUiSpeakerMobileListMarkup()}</span></div>"
}

String renderNewUiSpeakerTableMarkup() {
  List<Map> rows = buildNewUiSpeakerRows()
  StringBuilder html = new StringBuilder()
  html.append("<div><table id='new-ui-speaker-table' class='mdl-data-table'>")
  html.append('<thead><tr>')
  html.append('<th>Action</th>')
  html.append("<th class='new-ui-speaker-name'>Speaker</th>")
  html.append("<th class='new-ui-secondary-speakers'>Secondary Speakers</th>")
  html.append('<th>Network</th>')
  html.append('<th>Create Group From</th>')
  html.append('</tr></thead><tbody>')

  if(rows.isEmpty()) {
    html.append("<tr><td colspan='5'>No primary Sonos speakers have been discovered yet.</td></tr>")
  } else {
    rows.each { Map row ->
      String safeName = escapeNewUiHtml(row.name)
      String safeModel = escapeNewUiHtml(row.model)
      String safeId = escapeNewUiHtml(row.id)
      String safeIp = escapeNewUiHtml(row.ip ?: 'Unknown')
      String childDeviceId = row.player?.id?.toString()
      if(row.created == true && childDeviceId) {
        safeName = deviceEditLink(childDeviceId, safeName)
      }
      String detail = [safeModel, safeId]
          .findAll { String value -> value }
          .join(' &middot; ')

      html.append('<tr>')
      html.append("<td class='new-ui-action-cell' data-label='Action'>${renderNewUiCreatedStatus(row)}</td>")
      html.append("<td class='new-ui-speaker-name' data-label='Speaker'><strong>${safeName}</strong>")
      if(detail) {
        html.append("<div class='new-ui-speaker-detail'>${detail}</div>")
      }
      html.append('</td>')
      html.append("<td class='new-ui-secondary-speakers' data-label='Secondary speakers'>${renderNewUiSecondaryDevices(row)}</td>")
      html.append("<td data-label='Network'>${safeIp}</td>")
      html.append("<td class='new-ui-group-action-cell' data-label='Create group from'>${renderNewUiCreateGroupStatus(row)}</td>")
      html.append('</tr>')
    }
  }

  html.append('</tbody></table></div>')
  return html.toString()
}

String renderNewUiSpeakerMobileListMarkup() {
  List<Map> rows = buildNewUiSpeakerRows()
  StringBuilder html = new StringBuilder("<div id='new-ui-speaker-mobile-list' class='new-ui-mobile-list new-ui-speaker-mobile-list'>")
  if(rows.isEmpty()) {
    html.append("<div class='new-ui-mobile-empty'>No primary Sonos speakers have been discovered yet.</div>")
  } else {
    rows.each { Map row ->
      String safeName = escapeNewUiHtml(row.name)
      String safeModel = escapeNewUiHtml(row.model)
      String safeId = escapeNewUiHtml(row.id)
      String safeIp = escapeNewUiHtml(row.ip ?: 'Unknown')
      String childDeviceId = row.player?.id?.toString()
      if(row.created == true && childDeviceId) {
        safeName = deviceEditLink(childDeviceId, safeName)
      }
      String detail = [safeModel, safeId].findAll { String value -> value }.join(' &middot; ')
      String speakerMarkup = "<strong>${safeName}</strong>" + (detail ? "<div class='new-ui-speaker-detail'>${detail}</div>" : '')
      String actionKey = row.discoveryKey?.toString() ?: row.id?.toString()
      String playerAction
      if(row.created == true) {
        playerAction = newUiButtonLink("newUiMobileDeletePlayer|${actionKey}", 'Remove', '#c83d38', '13px', 'Remove speaker', 'new-ui-mobile-action-button new-ui-mobile-remove-button')
      } else {
        playerAction = newUiButtonLink("newUiMobileCreatePlayer|${actionKey}", 'Add speaker', '#4054b2', '13px', 'Create speaker', 'new-ui-mobile-action-button new-ui-mobile-create-group-button')
      }
      String groupAction = '<span class="new-ui-status new-ui-status-na">—</span>'
      if(row.created == true && row.id) {
        groupAction = newUiButtonLink("newUiMobileCreateGroup|${row.id}", 'Create Group From', '#fff', '13px', 'Create group from speaker', 'new-ui-mobile-action-button new-ui-mobile-create-group-button')
      }
      html.append("<article class='new-ui-mobile-record'><div class='new-ui-mobile-speaker-primary'>${speakerMarkup}</div>")
      html.append("<div class='new-ui-mobile-create-group'>${playerAction}</div>")
      html.append("<section class='new-ui-mobile-secondary-box'><div class='new-ui-mobile-secondary-title'>Secondary Speakers</div>${renderNewUiSecondaryDevices(row)}</section>")
      html.append("<div class='new-ui-mobile-ip'><span class='new-ui-mobile-ip-label'>IP:</span> ${safeIp}</div>")
      html.append("<div class='new-ui-mobile-create-group'>${groupAction}</div></article>")
    }
  }
  html.append('</div>')
  return html.toString()
}

String renderNewUiSecondaryDevices(Map row) {
  List<Map> secondaries = row.secondaryDevices instanceof List
      ? (List<Map>)row.secondaryDevices
      : []
  if(secondaries.isEmpty()) {
    return newUiStatusBadge('—', 'na', 'No discovered secondary speakers')
  }

  StringBuilder html = new StringBuilder()
  html.append("<div class='new-ui-secondary-count'>${secondaries.size()} discovered</div>")
  secondaries.each { Map secondary ->
    String name = (secondary.modelDisplayName ?: secondary.modelName ?: 'Secondary speaker').toString()
    String id = secondary.id?.toString()
    String ip = (secondary.deviceIp ?: secondary.localUpnpHost)?.toString()
    String detail = [ip, id]
        .findAll { Object value -> value }
        .collect { Object value -> escapeNewUiHtml(value) }
        .join(' &middot; ')
    html.append("<div class='new-ui-secondary-entry'><strong>${escapeNewUiHtml(name)}</strong>")
    if(detail) {
      html.append("<div class='new-ui-secondary-detail'>${detail}</div>")
    }
    html.append('</div>')
  }
  return html.toString()
}

String renderNewUiUpnpStatus(Map row, String sid, Boolean groupedOnly = false) {
  if(row.created != true || row.player == null) {
    return newUiStatusBadge('Not created', 'na', 'Create the Hubitat player before checking subscriptions')
  }

  if(groupedOnly) {
    try {
      Object grouped = row.player.currentValue('isGrouped', true)
      if(grouped != null && grouped.toString().toLowerCase(Locale.ROOT) in ['off', 'false']) {
        return newUiStatusBadge('Not required', 'na', 'This subscription is only needed while grouped')
      }
    } catch(Exception ignored) {
      // Fall through to the subscription validity check when group state is unavailable.
    }
  }

  try {
    Boolean valid = row.player.subValid(sid) as Boolean
    return valid
        ? newUiStatusBadge('Active', 'active', "${sid} is within its current renewal window")
        : newUiStatusBadge('Inactive', 'inactive', "${sid} is not currently valid")
  } catch(Exception e) {
    logDebug("Could not read ${sid} status for ${row.id}: ${e.message}")
    return newUiStatusBadge('Unknown', 'unknown', "${sid} status is unavailable")
  }
}

String renderNewUiWebSocketStatus(Map row) {
  if(row.created != true || row.player == null) {
    return newUiStatusBadge('Not created', 'na', 'Create the Hubitat player before checking WebSocket state')
  }

  Object status = null
  try {
    status = row.player.getDataValue('websocketStatus')
    if(!status && row.player.respondsTo('getWebSocketStatus')) {
      status = row.player.getWebSocketStatus()
    }
  } catch(Exception e) {
    logDebug("Could not read WebSocket status for ${row.id}: ${e.message}")
  }

  String normalized = status?.toString()?.trim()?.toLowerCase(Locale.ROOT)
  if(normalized == 'open') {
    return newUiStatusBadge('Connected', 'active', 'WebSocket is open')
  }
  if(normalized in ['closed', 'closing', 'connect timed out']) {
    return newUiStatusBadge('Disconnected', 'inactive', "WebSocket status: ${status}")
  }
  return newUiStatusBadge('Unknown', 'unknown', 'WebSocket status is unavailable')
}

String renderNewUiCreatedStatus(Map row) {
  String actionKey = row.discoveryKey?.toString() ?: row.id?.toString()
  if(row.created == true) {
    String deleteIcon = "<iconify-icon icon='material-symbols:delete-outline' style='font-size:20px;vertical-align:middle'></iconify-icon>"
    return newUiButtonLink("newUiDeletePlayer|${actionKey}", deleteIcon, '#F44336', '20px')
  }
  String addIcon = "<iconify-icon icon='material-symbols:add-circle-outline-rounded' style='font-size:20px;vertical-align:middle'></iconify-icon>"
  return newUiButtonLink("newUiCreatePlayer|${actionKey}", addIcon, '#4CAF50', '20px')
}

String renderNewUiCreateGroupStatus(Map row) {
  if(row.created != true || !row.id) {
    return newUiStatusBadge('—', 'na', 'Create the speaker before creating a group from it')
  }
  String groupIcon = "<iconify-icon icon='material-symbols:group-add' style='font-size:20px;vertical-align:middle'></iconify-icon>"
  return newUiButtonLink("newUiCreateGroup|${row.id}", groupIcon, '#4CAF50', '20px')
}

String newUiButtonLink(String buttonName, String linkText, String color = '#1A77C9', String font = '15px', String ariaLabel = null, String cssClass = '', String clickHandler = null) {
  String safeAriaLabel = ariaLabel ? " aria-label='${escapeNewUiHtml(ariaLabel)}'" : ''
  String safeCssClass = cssClass ? " ${escapeNewUiHtml(cssClass)}" : ''
  String action = clickHandler ?: 'buttonClick(this)'
  return "<div class='form-group'><input type='hidden' name='${buttonName}.type' value='button'></div>" +
      "<div style='display:inline-block'><div class='submitOnChange${safeCssClass}' role='button' tabindex='0'${safeAriaLabel} onclick=\"${action}\" onkeydown=\"if(event.key==='Enter'||event.key===' '){event.preventDefault();${action}}\" style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
      "<input type='hidden' name='settings[${buttonName}]' value=''>"
}

String newUiStatusBadge(String label, String statusClass, String title) {
  String safeLabel = escapeNewUiHtml(label)
  String safeTitle = escapeNewUiHtml(title)
  return "<span class='new-ui-status new-ui-status-${statusClass}' title='${safeTitle}'>${safeLabel}</span>"
}

String escapeNewUiHtml(Object value) {
  return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value?.toString())).replace("'", '&#39;')
}

// The prefs HTML fragment builders below stay on StringBuilder deliberately:
// MarkupBuilder emits double-quoted attributes, which would break the
// single-quoted href/class contract asserted by the specs and rendered by
// Hubitat's prefs. Escaping is centralized in escapeNewUiHtml instead.

String deviceEditLink(String deviceId, String escapedLabel) {
  return "<a href='/device/edit/${escapeNewUiHtml(deviceId)}' target='_blank'>${escapedLabel}</a>"
}

String displayNewUiGroupTable() {
  String css = """
    <style>
      .mdl-grid:has(.new-ui-device-card) { padding-bottom: 0 !important; }
      .mdl-cell:has(.new-ui-device-card) { margin-bottom: 0 !important; }
      .new-ui-device-card { margin-bottom: 0 !important; }
      .mdl-grid:has(.new-ui-groups-card) { padding-bottom: 0 !important; padding-top: 0 !important; }
      .mdl-cell:has(.new-ui-groups-card) { margin-bottom: 0 !important; margin-top: 0 !important; }
      .new-ui-groups-card { margin-bottom: 0 !important; margin-top: 0 !important; }
      .mdl-grid:has(.sonos-settings) { padding-top: 0 !important; }
      .mdl-cell:has(.sonos-settings) { margin-top: 0 !important; }
      .new-ui-groups-card {
        background: var(--surface-card, var(--surface-a, #fff));
        border: 1px solid var(--surface-border, #dedede);
        border-radius: 12px;
        box-shadow: 0 2px 10px rgba(25, 35, 55, .07);
        color: var(--text-color, #30343b);
        margin: 10px 0 18px;
        overflow: hidden;
      }
      #new-ui-group-table-wrapper {
        display: block;
        overflow-x: auto;
        padding: 0 !important;
        width: 100%;
      }
      .new-ui-group-table-content {
        display: block;
        margin: 0 !important;
        padding: 0 !important;
      }
      .new-ui-group-toolbar {
        align-items: center;
        background: var(--surface-b, #f7f8fa);
        border-bottom: 1px solid var(--surface-border, #e5e7eb);
        display: flex;
        gap: 16px;
        justify-content: space-between;
        min-height: 60px;
        padding: 10px 18px;
        position: relative;
      }
      .new-ui-group-toolbar-title {
        color: var(--text-color, #30343b);
        font-size: 17px;
        font-weight: 650;
        left: 50%;
        position: absolute;
        text-align: center;
        transform: translateX(-50%);
      }
      .new-ui-group-toolbar .form-group:has(input[name='btnNewUiCreateGroup']) { display: none !important; }
      .new-ui-group-toolbar-container { border-bottom: 1px solid var(--surface-border, #e5e7eb); }
      .new-ui-group-create-button {
        align-items: center;
        background: #4054b2;
        border: 1px solid #4054b2;
        border-radius: 8px;
        color: #fff;
        cursor: pointer;
        display: inline-flex;
        font-weight: 600;
        gap: 8px;
        line-height: 1.2;
        padding: 9px 13px;
        transition: background .15s ease, box-shadow .15s ease;
      }
      .new-ui-group-create-button:hover { background: #34479e; box-shadow: 0 2px 6px rgba(52, 71, 158, .2); }
      .new-ui-group-create-button:focus-visible { outline: 2px solid #1a77c9; outline-offset: 2px; }
      .new-ui-group-create-button svg { height: 16px; width: 16px; }
      .new-ui-group-toolbar > div[style='display:inline-block'] { display: inline-block !important; margin-left: auto; position: relative; z-index: 1; }
      .new-ui-group-table-toolbar-cell {
        background: var(--surface-b, #f7f8fa) !important;
        padding: 0 !important;
        text-align: left !important;
      }
      #new-ui-group-table {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border: 0;
        border-collapse: separate;
        border-spacing: 0;
        border-radius: 0;
        color: var(--text-color, #424242);
        min-width: 760px;
        table-layout: fixed;
        width: 100%;
      }
      #new-ui-group-table th,
      #new-ui-group-table td {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border-bottom: 1px solid var(--surface-border, #EEEEEE);
        border-right: 0;
        color: var(--text-color, #424242);
        font-size: 14px !important;
        padding: 12px 14px !important;
        text-align: center;
        vertical-align: middle;
      }
      #new-ui-group-table thead {
        background-color: var(--surface-b, #F5F5F5);
      }
      #new-ui-group-table th {
        background-color: var(--surface-b, #F5F5F5);
        border-bottom: 1px solid var(--surface-border, #E0E0E0);
        color: var(--text-color, #424242);
        font-size: 14px !important;
        font-weight: 500;
        white-space: nowrap;
      }
      #new-ui-group-table th.new-ui-group-name,
      #new-ui-group-table th.new-ui-group-coordinator,
      #new-ui-group-table td.new-ui-group-coordinator,
      #new-ui-group-table th.new-ui-group-followers {
        text-align: left;
      }
      #new-ui-group-table .new-ui-group-table-toolbar-cell {
        background: var(--surface-b, #f7f8fa);
        padding: 0 !important;
        text-align: left;
      }
      #new-ui-group-table tbody tr:hover {
        background-color: var(--surface-hover, #f8f9fc) !important;
      }
      #new-ui-group-table tbody tr:last-child td {
        border-bottom: 0;
      }
      #new-ui-group-table td:first-child .form-group {
        display: none;
      }
      #new-ui-group-table td:first-child .submitOnChange {
        line-height: 20px;
        vertical-align: middle;
      }
      #new-ui-group-table .new-ui-group-action-cell {
        padding-left: 10px !important;
        padding-right: 10px !important;
        white-space: nowrap;
        width: 100px;
      }
      #new-ui-group-table .new-ui-group-name,
      #new-ui-group-table .new-ui-group-followers {
        text-align: left;
      }
      #new-ui-group-table .new-ui-group-name {
        min-width: 200px;
        overflow-wrap: anywhere;
      }
      #new-ui-group-table .new-ui-group-name a {
        color: #2196F3;
        text-decoration: none;
      }
      #new-ui-group-table .new-ui-group-name a:hover {
        text-decoration: underline;
      }
      #new-ui-group-table .new-ui-group-followers br {
        line-height: 1.7;
      }
      .new-ui-group-mobile-list { display: none; }
      .new-ui-group-mobile-actions { align-items: center; display: flex; gap: 14px; }
      .new-ui-group-mobile-record { text-align: center; }
      .new-ui-group-mobile-title { font-size: 17px; line-height: 1.4; overflow-wrap: anywhere; }
      .new-ui-group-mobile-title a { color: #2196F3; text-decoration: none; }
      .new-ui-group-mobile-buttons { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; margin: 10px 0 12px; }
      .new-ui-group-mobile-buttons .form-group { display: none !important; }
      .new-ui-group-mobile-buttons .submitOnChange { align-items: center; border: 1px solid transparent; border-radius: 7px; box-sizing: border-box; display: inline-flex !important; font-size: 13px !important; font-weight: 600; justify-content: center; line-height: 1.2; min-height: 38px; padding: 9px 15px; text-decoration: none; }
      .new-ui-group-mobile-edit { background: #4054b2; border-color: #4054b2 !important; color: #fff !important; }
      .new-ui-group-mobile-delete { background: var(--surface-b, #fff); border-color: #d84a45 !important; color: #c83d38 !important; }
      .new-ui-group-mobile-record .new-ui-mobile-field { margin-top: 5px; }
      @media (max-width: 640px) {
        #new-ui-group-table-wrapper,
        #new-ui-group-table-wrapper > div { overflow: visible !important; }
        #new-ui-group-table-wrapper { display: none !important; }
        .new-ui-group-mobile-list { display: block !important; }
        .new-ui-group-toolbar { align-items: stretch; box-sizing: border-box; flex-direction: column; gap: 10px; padding: 12px 14px !important; width: 100%; }
        .new-ui-group-toolbar > div[style='display:inline-block'] { display: block !important; margin: 0; width: 100%; }
        .new-ui-group-toolbar-title { align-self: center; left: auto; position: static; transform: none; }
        .new-ui-group-create-button { box-sizing: border-box; justify-content: center; margin: 0 auto; white-space: nowrap; width: 100%; }
      }
    </style>
  """
  String iconifyScript = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
  String createIcon = "<svg viewBox='0 0 24 24' aria-hidden='true' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round'><path d='M12 5v14M5 12h14'></path></svg>"
  String createButton = newUiButtonLink('btnNewUiCreateGroup', "${createIcon}<span>Create group</span>", '#fff', '14px', 'Create a Sonos group', 'new-ui-group-create-button')
  return "${css.trim()}${iconifyScript}<div class='new-ui-groups-card'><div class='new-ui-group-toolbar-container'><div class='new-ui-group-toolbar'><div class='new-ui-group-toolbar-title'>Sonos groups</div>${createButton}</div></div><span class='ssr-app-state-${app.id}-${NEW_UI_GROUP_TABLE_EVENT}'><div id='new-ui-group-table-wrapper'>${renderNewUiGroupTableMarkup()}</div>${renderNewUiGroupTableMobileListMarkup()}</span></div>"
}

String renderNewUiGroupTableMarkup() {
  List<Map> rows = buildNewUiGroupRows()
  StringBuilder html = new StringBuilder()
  html.append("<div><table id='new-ui-group-table' class='mdl-data-table'>")
  html.append("<colgroup><col style='width:6%'><col style='width:34%'><col style='width:23%'><col style='width:37%'></colgroup>")
  html.append('<thead><tr>')
  html.append('<th>Action</th>')
  html.append("<th class='new-ui-group-name'>Group Name</th>")
  html.append("<th class='new-ui-group-coordinator'>Coordinator</th>")
  html.append("<th class='new-ui-group-followers'>Followers</th>")
  html.append('</tr></thead><tbody>')

  if(rows.isEmpty()) {
    html.append("<tr><td colspan='4'>No Sonos groups have been created yet.</td></tr>")
  } else {
    rows.each { Map row ->
      String groupName = row.name?.toString() ?: 'Unnamed group'
      String token = newUiGroupToken(groupName)
      String editIcon = "<iconify-icon icon='material-symbols:edit' style='font-size:19px;vertical-align:middle;margin-right:6px'></iconify-icon>"
      String deleteIcon = "<iconify-icon icon='material-symbols:delete-outline' style='font-size:20px;vertical-align:middle'></iconify-icon>"
      String actionMarkup = newUiButtonLink("newUiEditGroup|${token}", editIcon, 'var(--text-color, #424242)', '19px') +
          newUiButtonLink("newUiDeleteGroup|${token}", deleteIcon, '#F44336', '20px')
      String safeName = escapeNewUiHtml(groupName)
      String groupDeviceId = row.device?.id?.toString()
      if(groupDeviceId) {
        safeName = deviceEditLink(groupDeviceId, safeName)
      }
      String coordinatorName = row.coordinatorName?.toString() ?: row.coordinatorId?.toString() ?: 'Unknown'
      String safeCoordinator = escapeNewUiHtml(coordinatorName)
      String followerMarkup = renderNewUiGroupFollowers(row.followerIds)

      html.append('<tr>')
      html.append("<td class='new-ui-group-action-cell' data-label='Action'>${actionMarkup}</td>")
      html.append("<td class='new-ui-group-name' data-label='Group name'><strong>${safeName}</strong></td>")
      html.append("<td class='new-ui-group-coordinator' data-label='Coordinator'>${safeCoordinator}</td>")
      html.append("<td class='new-ui-group-followers' data-label='Followers'>${followerMarkup}</td>")
      html.append('</tr>')
    }
  }

  html.append('</tbody></table></div>')
  return html.toString()
}

String renderNewUiGroupTableMobileListMarkup() {
  List<Map> rows = buildNewUiGroupRows()
  StringBuilder html = new StringBuilder("<div id='new-ui-group-mobile-list' class='new-ui-mobile-list new-ui-group-mobile-list'>")
  if(rows.isEmpty()) {
    html.append("<div class='new-ui-mobile-empty'>No Sonos groups have been created yet.</div>")
  } else {
    rows.each { Map row ->
      String groupName = row.name?.toString() ?: 'Unnamed group'
      String token = newUiGroupToken(groupName)
      String actions = newUiButtonLink("newUiMobileEditGroup|${token}", 'Edit', '#fff', '13px', 'Edit group', 'new-ui-group-mobile-edit') +
          newUiButtonLink("newUiMobileDeleteGroup|${token}", 'Delete', '#c83d38', '13px', 'Delete group', 'new-ui-group-mobile-delete')
      String safeName = escapeNewUiHtml(groupName)
      String groupDeviceId = row.device?.id?.toString()
      if(groupDeviceId) {
        safeName = deviceEditLink(groupDeviceId, safeName)
      }
      String coordinator = escapeNewUiHtml(row.coordinatorName?.toString() ?: row.coordinatorId?.toString() ?: 'Unknown')
      String followers = renderNewUiGroupFollowers(row.followerIds)
      html.append("<article class='new-ui-mobile-record new-ui-group-mobile-record'><div class='new-ui-group-mobile-title'><strong>${safeName}</strong></div>")
      html.append("<div class='new-ui-group-mobile-buttons'>${actions}</div>")
      html.append("<div class='new-ui-mobile-field'><span class='new-ui-mobile-label'>Coordinator</span><span class='new-ui-mobile-value'>${coordinator}</span></div>")
      html.append("<div class='new-ui-mobile-field'><span class='new-ui-mobile-label'>Followers</span><span class='new-ui-mobile-value'>${followers}</span></div></article>")
    }
  }
  html.append('</div>')
  return html.toString()
}

Map findNewUiGroupByToken(String token) {
  if(!token) {
    return null
  }
  return buildNewUiGroupRows().find { Map row -> newUiGroupToken(row.name?.toString()) == token }
}

String newUiGroupToken(String groupName) {
  if(groupName == null || groupName.isEmpty()) { return '' }
  try {
    MessageDigest digest = MessageDigest.getInstance('SHA-256')
    byte[] hash = digest.digest(groupName.getBytes('UTF-8'))
    StringBuilder hex = new StringBuilder(hash.length * 2)
    for(byte b : hash) {
      hex.append(String.format(Locale.ROOT, '%02x', b & 0xff))
    }
    return hex.toString()
  } catch(Exception ignored) {
    return UUID.nameUUIDFromBytes(groupName.getBytes('UTF-8')).toString()
  }
}

String newUiGroupNameFromDeviceNetworkId(String deviceNetworkId) {
  String prefix = "${app.id}-SonosGroupDevice-"
  return deviceNetworkId?.startsWith(prefix) ? deviceNetworkId.substring(prefix.length()) : null
}

List<String> normalizeNewUiGroupPlayerIds(Object value) {
  if(value instanceof Collection) {
    return value.collect { Object id -> id?.toString() }.findAll { String id -> id }
  }
  if(value) {
    return value.toString().split(',').collect { String id -> id.trim() }.findAll { String id -> id }
  }
  return []
}

String renderNewUiGroupFollowers(Object followerIds) {
  List<String> ids = normalizeNewUiGroupPlayerIds(followerIds)
  if(ids.isEmpty()) {
    return newUiStatusBadge('—', 'na', 'No follower speakers configured')
  }
  Map<String, String> playerNames = getNewUiGroupPlayerNameMap()
  return StringUtils.join(ids.collect { String id -> escapeNewUiHtml(playerNames[id] ?: id) }, '<br>')
}


List<Map> buildNewUiSpeakerRows() {
  Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
  LinkedHashMap<String, Map> rows = [:]
  List<Map> installed = snapshot.playerRows instanceof Collection ? (List<Map>)snapshot.playerRows : []
  Map<String, String> secondaryOwners = [:]
  installed.each { Map row ->
    String ownerId = row.id?.toString() ?: row.deviceNetworkId?.toString()
    if(ownerId && row.secondaries instanceof Collection) {
      ((Collection<Map>)row.secondaries).each { Map secondary ->
        String secondaryId = secondary.id?.toString()
        if(secondaryId) { secondaryOwners[secondaryId] = ownerId }
      }
    }
  }
  installed.each { Map row ->
    String id = row.id?.toString() ?: row.deviceNetworkId?.toString()
    if(id && !secondaryOwners.containsKey(id)) {
      List<Map> secondaryDevices = row.secondaries instanceof Collection ? new ArrayList<Map>((Collection<Map>)row.secondaries) : []
      getNewUiSecondaryDevices(id).each { Map secondary ->
        if(!secondaryDevices.any { Map existing -> existing.id?.toString() == secondary.id?.toString() }) {
          secondaryDevices.add(secondary)
        }
      }
      rows[id] = [id: id, discoveryKey: row.deviceNetworkId?.toString() ?: id,
        name: row.name?.toString() ?: id, model: row.model?.toString(), ip: row.ip?.toString(),
        secondaryDevices: secondaryDevices,
        created: true, player: row.deviceId ? [id: row.deviceId.toString()] : null]
    }
  }
  discoveredSonoses.each { Object key, Object raw ->
    if(raw instanceof Map) {
      Map info = (Map)raw
      String id = info.id?.toString() ?: key?.toString()
      if(id && !secondaryOwners.containsKey(id) && !rows.containsKey(id)) {
        rows[id] = [id: id, discoveryKey: key?.toString(), name: info.name?.toString() ?: id,
          model: (info.modelDisplayName ?: info.modelName)?.toString(),
          ip: (info.deviceIp ?: info.ipAddress)?.toString(),
          secondaryDevices: getNewUiSecondaryDevices(id), created: false, player: null]
      }
    }
  }
  return new ArrayList<Map>(rows.values()).sort { Map left, Map right ->
    left.name.toString().compareToIgnoreCase(right.name.toString())
  }
}

List<Map> getNewUiSecondaryDevices(String primaryPlayerId) {
  List<Map> values = []
  discoveredSonosSecondaries.each { Object ignored, Object raw ->
    if(raw instanceof Map && ((Map)raw).primaryDeviceId?.toString() == primaryPlayerId) { values << new LinkedHashMap((Map)raw) }
  }
  return values
}

List<Map> buildNewUiGroupRows() {
  Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
  Map<String, Map> byName = copyGroupConfigurations((Map)(state.groupConfigurations ?: [:]))
  List<Map> rows = []
  byName.each { String name, Map config ->
    Map installed = (snapshot.groups instanceof Collection ? (List<Map>)snapshot.groups : []).find { Map item -> item.name?.toString() == name } ?: [:]
    rows << [name: name, coordinatorId: installed.coordinatorId?.toString() ?: config.groupCoordinatorId?.toString(),
      followerIds: normalizeNewUiGroupPlayerIds(installed.followerIds ?: config.playerIds),
      device: installed.deviceId ? [id: installed.deviceId.toString()] : null]
  }
  if(snapshot.groups instanceof Collection) {
    ((List<Map>)snapshot.groups).each { Map installed ->
      String name = installed.name?.toString()
      if(name && !byName.containsKey(name)) {
        rows << [name: name, coordinatorId: installed.coordinatorId?.toString(),
          followerIds: normalizeNewUiGroupPlayerIds(installed.followerIds),
          device: installed.deviceId ? [id: installed.deviceId.toString()] : null]
      }
    }
  }
  Map names = snapshot.playerNames instanceof Map ? (Map)snapshot.playerNames : [:]
  rows.each { Map row -> row.coordinatorName = names[row.coordinatorId?.toString()] ?: row.coordinatorId }
  return rows.sort { Map left, Map right -> left.name.toString().compareToIgnoreCase(right.name.toString()) }
}

Map<String, String> getNewUiGroupPlayerNameMap() {
  Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
  return snapshot.playerNames instanceof Map ? new LinkedHashMap<String, String>((Map<String, String>)snapshot.playerNames) : [:]
}

void startDiscoverySession() {
  if(atomicState[DISCOVERY_RUNNING_KEY] == true) { return }
  discoveredSonoses.clear()
  discoveredSonosSecondaries.clear()
  atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] = [:]
  state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
  state.remove(QUICK_TOPOLOGY_BATCH_STATE_KEY)
  atomicState.remove(QUICK_TOPOLOGY_QUERIED_DURING_DISCOVERY_STATE_KEY)
  String sessionId = UUID.randomUUID().toString()
  atomicState.discoverySessionId = sessionId
  subscribeToSsdpEvents(location)
  atomicState[DISCOVERY_RUNNING_KEY] = true
  ssdpDiscover()
  Long endTime = now() + 60000L
  atomicState[DISCOVERY_END_TIME_KEY] = endTime
  runIn(60, 'stopDiscovery', [data: [sessionId: sessionId]])
  runIn(1, 'updateDiscoveryTimer', [data: [sessionId: sessionId]])
  app.sendEvent(name: 'discoveryTimer', value: 'Discovery started')
}

void persistDiscoveredSonosSecondaries() {
  LinkedHashMap<String, Map> persistedSecondaries = new LinkedHashMap<String, Map>()
  discoveredSonosSecondaries.each { Object discoveryKey, Object rawInfo ->
    if(discoveryKey != null && rawInfo instanceof Map) {
      persistedSecondaries[discoveryKey.toString()] = new LinkedHashMap<String, Object>((Map)rawInfo)
    }
  }
  state[DISCOVERED_SONOS_SECONDARIES_STATE_KEY] = persistedSecondaries
}

void restoreDiscoveredSonosSecondaries() {
  if(discoveredSonosSecondaries == null) {
    discoveredSonosSecondaries = [:]
  }
  if(!discoveredSonosSecondaries.isEmpty()) { return }

  Object persisted = state[DISCOVERED_SONOS_SECONDARIES_STATE_KEY]
  if(!(persisted instanceof Map)) { return }

  ((Map)persisted).each { Object discoveryKey, Object rawInfo ->
    if(discoveryKey != null && rawInfo instanceof Map) {
      discoveredSonosSecondaries[discoveryKey.toString()] = new LinkedHashMap<String, Object>((Map)rawInfo)
    }
  }
}

void stopDiscovery(Map data = [:]) {
  if(data?.sessionId && data.sessionId.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] == true) {
    logInfo('Stopping discovery...')
  }
  // Set flags FIRST so all guard checks see the stop immediately
  atomicState[DISCOVERY_RUNNING_KEY] = false
  atomicState[DISCOVERY_END_TIME_KEY] = null
  atomicState.discoverySessionId = UUID.randomUUID().toString()
  state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
  state.remove(QUICK_TOPOLOGY_BATCH_STATE_KEY)
  app.sendEvent(name: 'discoveryTimer', value: '0')
  // Clean up legacy state keys (migration from state → atomicState)
  state.remove(DISCOVERY_RUNNING_KEY)
  state.remove(DISCOVERY_END_TIME_KEY)

  try {
    unsubscribe(location, 'ssdpTerm.upnp:rootdevice')
    unsubscribe(location, 'ssdpTerm.urn:schemas-upnp-org:device:ZonePlayer:1')
  } catch(Exception e) { logWarn("Error unsubscribing SSDP: ${e.message}") }

  try {
    unschedule('sendFoundSonosEvents')
    unschedule('processMdnsDiscovery')
    unschedule('stopDiscovery')
    unschedule('updateDiscoveryTimer')
  } catch(Exception e) { logWarn("Error unscheduling discovery tasks: ${e.message}") }

}

void updateDiscoveryTimer(Map data = [:]) {
  if(data?.sessionId && data.sessionId.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true || !atomicState[DISCOVERY_END_TIME_KEY]) {
    logDebug("updateDiscoveryTimer: stopping - discoveryRunning=${atomicState[DISCOVERY_RUNNING_KEY]}, discoveryEndTime=${atomicState[DISCOVERY_END_TIME_KEY]}")
    return
  }

  Integer remainingSecs = Math.max(0, (Integer)(((atomicState[DISCOVERY_END_TIME_KEY] as Long) - now()) / 1000))
  logDebug("updateDiscoveryTimer: sending event with ${remainingSecs} seconds remaining")
  app.sendEvent(name: 'discoveryTimer', value: "Discovery time remaining: ${remainingSecs} seconds")

  if(remainingSecs > 0) {
    runIn(1, 'updateDiscoveryTimer', [data: [sessionId: atomicState.discoverySessionId]])
  } else {
    logDebug("updateDiscoveryTimer: timer reached 0, stopping updates")
  }
}

void extendDiscovery(Integer seconds) {
  logInfo("Extending discovery by ${seconds} seconds")
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    // Restart discovery without clearing already-discovered devices
    logDebug('Starting a fresh discovery session while retaining devices already found')
    atomicState[DISCOVERY_RUNNING_KEY] = true
    atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] = [:]
    subscribeToSsdpEvents(location)
    sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
    sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))
    startMdnsDiscovery()
    runIn(1, 'updateDiscoveryTimer', [data: [sessionId: atomicState.discoverySessionId]])
  }
  // Add seconds to current remaining time (or from now if already expired)
  Long currentEnd = atomicState[DISCOVERY_END_TIME_KEY] ? (atomicState[DISCOVERY_END_TIME_KEY] as Long) : now()
  Long newEnd = Math.max(currentEnd, now()) + (seconds * 1000L)
  atomicState[DISCOVERY_END_TIME_KEY] = newEnd
  Long remainingMillis = newEnd - now()
  Integer totalRemaining = (Integer)(remainingMillis / 1000L)
  unschedule('stopDiscovery')
  runIn(totalRemaining, 'stopDiscovery', [data: [sessionId: atomicState.discoverySessionId]])
}

String renderNewUiDiscoveryTimerMarkup() {
  String discoveryIcon = atomicState[DISCOVERY_RUNNING_KEY] == true ?
    "<svg viewBox='0 0 24 24' aria-hidden='true' fill='currentColor'><rect x='6' y='6' width='12' height='12' rx='1'></rect></svg>" :
    "<svg viewBox='0 0 24 24' aria-hidden='true' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M4 12a8 8 0 0 1 13.7-5.7L20 8.5'></path><path d='M20 4v4.5h-4.5'></path><path d='M20 12a8 8 0 0 1-13.7 5.7L4 15.5'></path><path d='M4 20v-4.5h4.5'></path></svg>"
  String actionLabel = atomicState[DISCOVERY_RUNNING_KEY] == true ? 'Cancel discovery' : 'Discover speakers'
  String buttonTitle = atomicState[DISCOVERY_RUNNING_KEY] == true ? 'Cancel speaker discovery' : 'Discover speakers for 60 seconds'
  String discoveryButton = newUiButtonLink('btnNewUiDiscoverSpeakers', "${discoveryIcon}<span>${actionLabel}</span>", '#fff', '14px', buttonTitle, 'new-ui-discovery-button')
  String statusMarkup
  if(atomicState[DISCOVERY_RUNNING_KEY] == true && atomicState[DISCOVERY_END_TIME_KEY]) {
    Long endTime = atomicState[DISCOVERY_END_TIME_KEY] as Long
    Integer remainingSecs = Math.max(0, (Integer)((endTime - now()) / 1000))
    statusMarkup = "<div class='new-ui-discovery-status'><span class='new-ui-discovery-indicator new-ui-discovery-indicator-running' aria-hidden='true'></span><div class='new-ui-discovery-copy'><div class='new-ui-discovery-title'>Searching for Sonos speakers</div><div class='new-ui-discovery-detail'>About ${remainingSecs} seconds remaining</div></div></div>"
  } else {
    statusMarkup = "<div class='new-ui-discovery-status'><span class='new-ui-discovery-indicator' aria-hidden='true'></span><div class='new-ui-discovery-copy'><div class='new-ui-discovery-title'>Discovery is stopped</div><div class='new-ui-discovery-detail'>Start a scan to refresh available speakers</div></div></div>"
  }
  return "${statusMarkup}<div class='new-ui-device-section-title'>Sonos Players</div>${discoveryButton}"
}

void ssdpDiscover() {
  logDebug("Starting SSDP Discovery...")
  discoveredSonoses = [:]
  discoveredSonosSecondaries = [:]
	atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] = [:]
	sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
	sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))

  // Also start mDNS discovery
  startMdnsDiscovery()
}

void startMdnsDiscovery() {
  try {
    // Register mDNS listener for Sonos devices
    // Sonos uses _sonos._tcp for its mDNS service
    registerMDNSListener('_sonos._tcp')
    logDebug("Registered mDNS listener for Sonos devices (_sonos._tcp)")

    // Also listen for general device services that Sonos might broadcast
    registerMDNSListener('_http._tcp')
    logDebug("Registered mDNS listener for HTTP services (_http._tcp)")

    // Schedule processing of mDNS entries after a delay to allow discovery
    runIn(5, 'processMdnsDiscovery', [data: [sessionId: atomicState.discoverySessionId]])
  } catch(Exception e) {
    logWarn("mDNS discovery registration failed: ${e.message}")
  }
}

void processMdnsDiscovery(Map data = [:]) {
  if(data?.sessionId && data.sessionId.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("processMdnsDiscovery: discovery not running, skipping")
    return
  }
  try {
    logDebug("Processing mDNS discovered devices...")

    Integer totalEntries = 0
    ['_sonos._tcp', '_http._tcp'].each { String serviceType ->
      List<Map> mdnsEntries = (getMDNSEntries(serviceType) ?: []) as List<Map>
      totalEntries += mdnsEntries.size()
      logDebug("Found ${mdnsEntries.size()} entries for mDNS service ${serviceType}")
      mdnsEntries.each { Map entry ->
        String serviceName = (entry.server ?: entry.name ?: '').toString()
        String addresses = (entry.ip4Addresses ?: entry.ipAddress ?: '').toString()
        String ipAddress = splitTokens(addresses, ',')?.first()?.trim()
        Integer port = (entry.port ?: 0) as Integer

        logPayloadAtTrace("mDNS entry service=${serviceType}, name=${serviceName}, ip=${ipAddress}, port=${port}; payload omitted",
            "mDNS entry: service=${serviceType}, name=${serviceName}, ip=${ipAddress}, port=${port}, raw=${entry}")

        if(serviceType == '_sonos._tcp' || serviceName.toLowerCase(Locale.ROOT).contains('sonos') || (ipAddress && port == 1400)) {
          logDebug("Found potential Sonos device via mDNS: ${serviceName} at ${ipAddress}:${port}")
          processDiscoveredSonosDevice(ipAddress)
        } else {
          logDebug("Ignoring non-Sonos mDNS entry ${serviceName} (${serviceType}) at ${ipAddress}:${port}")
        }
      }
    }

    logDebug("Processed ${totalEntries} mDNS entries")

    runIn(2, 'sendFoundSonosEvents', [data: [sessionId: atomicState.discoverySessionId]])
  } catch(Exception e) {
    logWarn("Error processing mDNS discovery: ${e.message}")
  }
}

Boolean isIpAlreadyDiscovered(String ipAddress) {
  Boolean alreadyDiscovered = discoveredSonoses.values().any { it.deviceIp == ipAddress }
  Boolean alreadyDiscoveredSecondary = discoveredSonosSecondaries.values().any { it.deviceIp == ipAddress }
  return alreadyDiscovered || alreadyDiscoveredSecondary
}

Boolean reserveDiscoveryProbe(String ipAddress) {
  if(!ipAddress) {
    logDebug('Skipping discovery probe because the candidate IP address is empty')
    return false
  }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Skipping discovery probe for ${ipAddress}; discovery session is stopped")
    return false
  }
  synchronized(this) {
    Map attempts = (atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] ?: [:]) as Map
    // Keep every IP reserved for the full discovery session, including failures and
    // unsupported secondary speakers. A new session resets this map in ssdpDiscover().
    if(attempts.containsKey(ipAddress)) {
      logDebug("Skipping discovery probe for ${ipAddress}; already attempted this session at ${attempts[ipAddress]}")
      return false
    }
    Map updated = new LinkedHashMap(attempts)
    updated[ipAddress] = now()
    atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] = updated
    logDebug("Reserved ${ipAddress} for a discovery probe; ${updated.size()} unique IP(s) attempted this session")
    return true
  }
}

void requestDiscoveryDeviceDescription(String ipAddress, LinkedHashMap playerInfo) {
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Skipping device-description request for ${ipAddress}; discovery stopped before player-info callback")
    return
  }
  requestSonosDeviceDescription(ipAddress, playerInfo, 'discoveryDeviceDescriptionCallback', [ipAddress: ipAddress, sessionId: atomicState.discoverySessionId])
}

Boolean requestSonosDeviceDescription(String ipAddress, LinkedHashMap playerInfo, String callback,
    Map callbackData) {
  Map params = [uri: "http://${ipAddress}:1400/xml/device_description.xml", timeout: 3,
    requestContentType: 'application/xml', contentType: 'application/xml']
  Map data = new LinkedHashMap(callbackData ?: [:])
  data.ipAddress = ipAddress
  data.playerInfo = playerInfo
  try {
    logDebug("Dispatching asynchronous device-description probe for ${ipAddress} (timeout=3s)")
    asynchttpGet(callback, params, data)
    return true
  } catch(Exception e) {
    logDebug("Could not dispatch device-description probe for ${ipAddress}: ${e.message}")
    return false
  }
}

void discoveryPlayerInfoCallback(AsyncResponse response, Map data) {
  String ipAddress = data?.ipAddress?.toString()
  if(!ipAddress) { logDebug('Player-info callback received without an IP address'); return }
  if(data?.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Ignoring player-info callback for ${ipAddress}; discovery session has stopped")
    return
  }
  if(response == null || response.hasError()) {
    logDebug("Player-info probe failed for ${ipAddress}: status=${response?.status}, error=${response?.getErrorMessage()}")
    return
  }
  try {
    Map playerInfo = response.getJson() as Map
    if(!playerInfo) {
      logDebug("Player-info probe for ${ipAddress} returned an empty or non-object JSON response (status=${response.status})")
      return
    }
    logDebug("Player-info probe succeeded for ${ipAddress} (status=${response.status}, playerId=${playerInfo.playerId}, name=${playerInfo.device?.name})")
    requestDiscoveryDeviceDescription(ipAddress, new LinkedHashMap(playerInfo))
  } catch(Exception e) { logDebug("Could not parse player-info response for ${ipAddress} (status=${response.status}): ${e.message}") }
}

void discoveryDeviceDescriptionCallback(AsyncResponse response, Map data) {
  String ipAddress = data?.ipAddress?.toString()
  if(!ipAddress) { logDebug('Device-description callback received without an IP address'); return }
  if(data?.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Ignoring device-description callback for ${ipAddress}; discovery session has stopped")
    return
  }
  if(response == null || response.hasError()) {
    logDebug("Device-description probe failed for ${ipAddress}: status=${response?.status}, error=${response?.getErrorMessage()}")
    return
  }
  try {
    String responseBody = response.getData() as String
    if(!responseBody) {
      logDebug("Device-description probe for ${ipAddress} returned an empty body (status=${response.status})")
      return
    }
    GPathResult description = new XmlSlurper().parseText(responseBody)
    logDebug("Device-description probe succeeded for ${ipAddress} (status=${response.status}, model=${description.device.modelName.text()})")
    processSonosDeviceInfo(data.playerInfo as LinkedHashMap, description, ipAddress)
  } catch(Exception e) { logDebug("Could not parse device-description response for ${ipAddress} (status=${response.status}): ${e.message}") }
}

void requestDiscoveryPlayerInfo(String ipAddress) {
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Skipping player-info request for ${ipAddress}; discovery session has stopped")
    return
  }
  requestSonosPlayerInfo(ipAddress, 'discoveryPlayerInfoCallback', [ipAddress: ipAddress, sessionId: atomicState.discoverySessionId])
}

Boolean requestSonosPlayerInfo(String ipAddress, String callback, Map callbackData) {
  Map params = [uri: "${getLocalApiPrefix("${ipAddress}:1443")}/players/local/info",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json', contentType: 'application/json', ignoreSSLIssues: true, timeout: 3]
  try {
    logDebug("Dispatching asynchronous player-info probe for ${ipAddress}:1443 (timeout=3s)")
    asynchttpGet(callback, params, callbackData ?: [ipAddress: ipAddress])
    return true
  } catch(Exception e) {
    logDebug("Could not dispatch player-info probe for ${ipAddress}: ${e.message}")
    return false
  }
}

void processDiscoveredSonosDevice(String ipAddress) {
  if(!ipAddress) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("processDiscoveredSonosDevice: discovery not running, skipping")
    return
  }

  logDebug("Considering mDNS discovery candidate at ${ipAddress}")
  if(isIpAlreadyDiscovered(ipAddress)) {
    logDebug("Skipping mDNS candidate ${ipAddress}; device is already in discovered results")
    return
  }
  if(!reserveDiscoveryProbe(ipAddress)) { return }
  requestDiscoveryPlayerInfo(ipAddress)
}

@CompileStatic
void subscribeToSsdpEvents(Location location) {
  logDebug("Subscribing to SSDP Discovery events...")
  // SSDP responses carry the responder's concrete ST as the event value, so there is
  // no 'ssdpTerm.ssdp:all' event. The 'lan discovery ssdp:all' search elicits Sonos
  // responses with the ZonePlayer URN as their ST -- subscribe to that to catch
  // players whose upnp:rootdevice response was missed.
	subscribe(location, 'ssdpTerm.upnp:rootdevice', 'ssdpEventHandler')
	subscribe(location, 'ssdpTerm.urn:schemas-upnp-org:device:ZonePlayer:1', 'ssdpEventHandler')
}

void ssdpEventHandler(Event event) {
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) { return }
  LinkedHashMap parsedEvent = parseLanMessage(event?.description)
  processParsedSsdpEvent(parsedEvent)
}

@CompileStatic
void processParsedSsdpEvent(LinkedHashMap event) {
  String ipAddress = convertHexToIP(event?.networkAddress as String)
  String ipPort = convertHexToInt(event?.deviceAddress as String).toString()
  logDebug("Considering SSDP discovery candidate at ${ipAddress}:${ipPort}")

  // Devices send multiple SSDP responses; reserve each IP before issuing async probes.
  if(isIpAlreadyDiscovered(ipAddress)) {
    logDebug("Skipping SSDP candidate ${ipAddress}; device is already in discovered results")
    return
  }
  if(!reserveDiscoveryProbe(ipAddress)) {
    return
  }

  requestDiscoveryPlayerInfo(ipAddress)
}

@CompileStatic
void processSonosDeviceInfo(LinkedHashMap playerInfo, GPathResult deviceDescription, String ipAddress,
    Map topologyInfo = null, Boolean publishDiscoveryUpdates = true) {
  LinkedHashMap playerInfoDevice = playerInfo?.device as LinkedHashMap

  String modelName = deviceDescription['device']['modelName']
	String mac = (deviceDescription['device']['MACAddress']).toString().replace(':','')
  String playerName = playerInfoDevice?.name
  String playerDni = mac
  String swGen = playerInfoDevice?.swGen
  String websocketUrl = playerInfoDevice?.websocketUrl
  String householdId = playerInfo?.householdId
  String playerId = playerInfo?.playerId
  String groupId = playerInfo?.groupId
  List<String> deviceCapabilities = playerInfoDevice?.capabilities as List<String>

  if(mac && playerInfoDevice?.name) {
    logDebug("Processing Sonos device: MAC=${mac}, name=${playerInfoDevice?.name}, IP=${ipAddress}")
  }

  // Use MAC as primary key, but also check for duplicate IPs/playerIds to prevent same device listed multiple times
  // Check against both primary and secondary maps
  // O(1) MAC check first (most common case), then .any{} for early exit on IP/playerId match
  boolean isDuplicate = false
  if(discoveredSonoses.containsKey(mac)) {
    isDuplicate = true
    // Update the existing entry with latest info to keep it fresh
    LinkedHashMap existingDevice = discoveredSonoses[mac] as LinkedHashMap
    existingDevice.deviceIp = ipAddress
    existingDevice.localApiUrl = "https://${ipAddress}:1443/api/v1/"
    existingDevice.localUpnpUrl = "http://${ipAddress}:1400"
    existingDevice.localUpnpHost = "${ipAddress}:1400"
    if(topologyInfo) { existingDevice.topologyInfo = new LinkedHashMap(topologyInfo) }
    logDebug("Device already discovered in primaries (MAC match) - MAC: ${mac}, IP: ${ipAddress}")
  }
  if(!isDuplicate) {
    isDuplicate = discoveredSonoses.any { key, value ->
      LinkedHashMap existingDevice = value as LinkedHashMap
      existingDevice.id == playerId || existingDevice.deviceIp == ipAddress
    }
    if(isDuplicate) { logDebug("Device already discovered in primaries (IP/ID match) - MAC: ${mac}, playerId: ${playerId}, IP: ${ipAddress}") }
  }
  if(!isDuplicate) {
    isDuplicate = discoveredSonosSecondaries.containsKey(mac) || discoveredSonosSecondaries.any { key, value ->
      LinkedHashMap existingDevice = value as LinkedHashMap
      existingDevice.id == playerId || existingDevice.deviceIp == ipAddress
    }
    if(isDuplicate) { logDebug("Device already discovered in secondaries - MAC: ${mac}, playerId: ${playerId}, IP: ${ipAddress}") }
  }

  if(isDuplicate) {
    if(topologyInfo) {
      [discoveredSonoses, discoveredSonosSecondaries].each { Map<String, LinkedHashMap> devices ->
        Object match = devices.values().find { Object rawCandidate ->
          if(!(rawCandidate instanceof Map)) { return false }
          Map candidate = (Map)rawCandidate
          return candidate.id?.toString() == playerId || candidate.deviceIp?.toString() == ipAddress
        }
        if(match instanceof Map) { ((Map)match).topologyInfo = new LinkedHashMap(topologyInfo) }
      }
    }
    return
  }

  LinkedHashMap discoveredSonos = [
    name: playerName,
    id: playerId,
    swGen: swGen,
    capabilities: deviceCapabilities,
    modelName: modelName,
    householdId: householdId,
    websocketUrl: websocketUrl,
    deviceIp: "${ipAddress}",
    localApiUrl: "https://${ipAddress}:1443/api/v1/",
    localUpnpUrl: "http://${ipAddress}:1400",
    localUpnpHost: "${ipAddress}:1400",
    topologyInfo: topologyInfo ? new LinkedHashMap(topologyInfo) : [:]
  ]
  // Handle secondary/satellite devices (subs, surrounds, right channel in stereo pair)
  if(playerInfoDevice?.primaryDeviceId) {
    LinkedHashMap discoveredSonosSecondary = [
      primaryDeviceId: playerInfoDevice?.primaryDeviceId,
      id: playerId,
      swGen: swGen,
      capabilities: deviceCapabilities,
      modelName: modelName,
      householdId: householdId,
      websocketUrl: websocketUrl,
      deviceIp: "${ipAddress}",
      localApiUrl: "https://${ipAddress}:1443/api/v1/",
      localUpnpUrl: "http://${ipAddress}:1400",
      localUpnpHost: "${ipAddress}:1400",
      topologyInfo: topologyInfo ? new LinkedHashMap(topologyInfo) : [:]
    ]
    // Check for duplicate secondaries
    boolean secondaryExists = discoveredSonosSecondaries.values().any { secondaryDevice ->
      LinkedHashMap secondary = secondaryDevice as LinkedHashMap
      return secondary.id == playerId
    }
    if(!secondaryExists) {
      discoveredSonosSecondaries[mac] = discoveredSonosSecondary
      persistDiscoveredSonosSecondaries()
      logInfo("Discovered secondary ${modelName} (${playerId}) for primary ${playerInfoDevice?.primaryDeviceId}")
    }
    
    // Secondary devices should never appear in the main discovered list
    if(publishDiscoveryUpdates) {
      requestQuickTopologyDuringDiscovery(ipAddress)
      scheduleFoundSonosEvents()
    }
    return
  }

  // Only primary (standalone) devices reach here
  if(discoveredSonos?.name != null && discoveredSonos?.name != 'null') {
    discoveredSonoses[mac] = discoveredSonos
    logInfo("Discovered Sonos device: ${playerName} (${modelName}) at ${ipAddress}")
    if(publishDiscoveryUpdates) {
      requestQuickTopologyDuringDiscovery(ipAddress)
      scheduleFoundSonosEvents()
    }
  } else {
    logDebug("Device id:${discoveredSonos?.id} responded to discovery, but did not provide device name. This is expected for right channel speakers on stereo pairs, subwoofers, and other 'non primary' devices.")
  }
}

/** Schedule a discovery UI refresh outside the statically compiled processing path. */
void scheduleFoundSonosEvents() {
  String sessionId = atomicState[DISCOVERY_SESSION_ID_KEY]?.toString()
  runIn(2, 'sendFoundSonosEvents', [data: [sessionId: sessionId]])
}


/** Use one already-known speaker to discover the rest of the Sonos topology. */
void requestQuickTopologyRefresh(String preferredIp = null) {
  if(state[QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY]) { return }
  String speakerIp = preferredIp?.trim()
  if(!speakerIp) {
    Map known = discoveredSonoses.values().find { Object raw -> raw instanceof Map && ((Map)raw).deviceIp } as Map
    speakerIp = known?.deviceIp?.toString()?.trim()
  }
  if(!speakerIp) {
    Map known = discoveredSonosSecondaries.values().find { Object raw -> raw instanceof Map && ((Map)raw).deviceIp } as Map
    speakerIp = known?.deviceIp?.toString()?.trim()
  }
  if(!speakerIp) { return }

  String requestId = UUID.randomUUID().toString()
  String service = 'urn:schemas-upnp-org:service:ZoneGroupTopology:1'
  String body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:GetZoneGroupState xmlns:u=\"${service}\"></u:GetZoneGroupState></s:Body></s:Envelope>"
  Map params = [uri: "http://${speakerIp}:1400/ZoneGroupTopology/Control",
    headers: [SOAPAction: "${service}#GetZoneGroupState"],
    requestContentType: 'text/xml', contentType: 'text/xml', timeout: 5, body: body]
  state[QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY] = requestId
  try {
    logInfo("Querying discovery topology from ${speakerIp}")
    asynchttpPost('quickTopologyCallback', params,
        [requestId: requestId, sourceIp: speakerIp, sessionId: atomicState.discoverySessionId])
  } catch(Exception e) {
    state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
    logWarn("Could not query Sonos topology from ${speakerIp}: ${e.message}")
  }
}

void requestQuickTopologyDuringDiscovery(String speakerIp) {
  if(atomicState[DISCOVERY_RUNNING_KEY] != true || atomicState[QUICK_TOPOLOGY_QUERIED_DURING_DISCOVERY_STATE_KEY] == true) { return }
  atomicState[QUICK_TOPOLOGY_QUERIED_DURING_DISCOVERY_STATE_KEY] = true
  requestQuickTopologyRefresh(speakerIp)
}

void quickTopologyCallback(AsyncResponse response, Map data) {
  String requestId = data?.requestId?.toString()
  if(!requestId || requestId != state[QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY]?.toString() ||
      data?.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(response == null || response.hasError()) {
    state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
    logWarn("Sonos topology query failed from ${data?.sourceIp}: status=${response?.status}, error=${response?.getErrorMessage()}")
    return
  }
  try {
    String body = response.getData()?.toString()
    GPathResult envelope = new XmlSlurper().parseText(body)
    GPathResult responseNode = findXmlDescendantByLocalName(envelope, 'GetZoneGroupStateResponse')
    GPathResult topologyNode = findDirectXmlChild(responseNode, 'ZoneGroupState')
    List<GPathResult> members = []
    collectXmlDescendantsByLocalName(topologyNode, 'ZoneGroupMember', members)
    if(members.isEmpty()) {
      String nestedXml = topologyNode?.text()?.toString()
      if(nestedXml) {
        GPathResult topology = new XmlSlurper().parseText(nestedXml)
        collectXmlDescendantsByLocalName(topology, 'ZoneGroupMember', members)
      }
    }
    LinkedHashMap<String, Map> devicesByIp = new LinkedHashMap<String, Map>()
    members.each { GPathResult member ->
      addQuickTopologyDevice(devicesByIp, member, false, null)
      String primaryId = xmlAttribute(member, 'UUID')
      List<GPathResult> satellites = []
      collectXmlDescendantsByLocalName(member, 'Satellite', satellites)
      satellites.each { GPathResult satellite -> addQuickTopologyDevice(devicesByIp, satellite, true, primaryId) }
    }
    String sourceIp = data?.sourceIp?.toString()
    List<String> pendingIps = new ArrayList<String>(devicesByIp.keySet())
    if(pendingIps.isEmpty()) {
      state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
      return
    }
    state[QUICK_TOPOLOGY_BATCH_STATE_KEY] = [
      requestId: requestId,
      devicesByIp: devicesByIp,
      pendingIps: pendingIps,
      sessionId: data?.sessionId?.toString()
    ]
    pendingIps.each { String ip -> requestQuickTopologySpeakerInfo(ip, requestId) }
    logInfo("Sonos topology query found ${devicesByIp.size()} speaker locations; gathering details before refreshing the page")
  } catch(Exception e) {
    state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
    logWarn("Could not parse Sonos topology response: ${e.message}")
  }
}

void addQuickTopologyDevice(Map<String, Map> devicesByIp, GPathResult deviceNode,
    Boolean secondary, String primaryDeviceId) {
  String ip = null
  try { ip = new URI(xmlAttribute(deviceNode, 'Location')).host } catch(Exception ignored) { }
  if(!ip) { return }
  Map<String, String> topologyInfo = [:]
  QUICK_TOPOLOGY_DEVICE_ATTRIBUTE_NAMES.each { String name ->
    String value = xmlAttribute(deviceNode, name)
    if(value != null) { topologyInfo[name] = value }
  }
  devicesByIp[ip] = [ipAddress: ip, secondary: secondary, primaryDeviceId: primaryDeviceId,
    topologyInfo: topologyInfo]
}

void addQuickTopologyIp(Set<String> ips, String location) {
  try {
    String ip = location ? new URI(location).host : null
    if(ip) { ips.add(ip) }
  } catch(Exception ignored) { }
}

void requestQuickTopologySpeakerInfo(String ipAddress, String requestId) {
  Boolean dispatched = requestSonosPlayerInfo(ipAddress, 'quickTopologySpeakerInfoCallback',
      [ipAddress: ipAddress, requestId: requestId, sessionId: atomicState.discoverySessionId])
  if(!dispatched) { completeQuickTopologySpeaker(ipAddress, requestId) }
}

void quickTopologySpeakerInfoCallback(AsyncResponse response, Map data) {
  String ipAddress = data?.ipAddress?.toString()
  String requestId = data?.requestId?.toString()
  Map batch = state[QUICK_TOPOLOGY_BATCH_STATE_KEY] instanceof Map
      ? (Map)state[QUICK_TOPOLOGY_BATCH_STATE_KEY] : [:]
  if(!ipAddress || !requestId || batch.requestId?.toString() != requestId ||
      data?.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(response == null || response.hasError()) {
    completeQuickTopologySpeaker(ipAddress, requestId)
    return
  }
  try {
    Map playerInfo = response.getJson() as Map
    if(!(playerInfo instanceof Map)) {
      completeQuickTopologySpeaker(ipAddress, requestId)
      return
    }
    Map<String, Map> devicesByIp = batch.devicesByIp instanceof Map ? (Map<String, Map>)batch.devicesByIp : [:]
    Map topologyDevice = devicesByIp[ipAddress] ?: [:]
    if(topologyDevice.secondary == true && topologyDevice.primaryDeviceId && playerInfo.device instanceof Map) {
      Map playerDevice = (Map)playerInfo.device
      if(!playerDevice.primaryDeviceId) {
        playerDevice.primaryDeviceId = topologyDevice.primaryDeviceId
      }
    }
    Boolean dispatched = requestSonosDeviceDescription(ipAddress, new LinkedHashMap(playerInfo),
        'quickTopologyDeviceDescriptionCallback', [ipAddress: ipAddress, requestId: requestId, sessionId: atomicState.discoverySessionId])
    if(!dispatched) { completeQuickTopologySpeaker(ipAddress, requestId) }
  } catch(Exception e) {
    logDebug("Could not process topology player info from ${ipAddress}: ${e.message}")
    completeQuickTopologySpeaker(ipAddress, requestId)
  }
}

void quickTopologyDeviceDescriptionCallback(AsyncResponse response, Map data) {
  String ipAddress = data?.ipAddress?.toString()
  String requestId = data?.requestId?.toString()
  Map batch = state[QUICK_TOPOLOGY_BATCH_STATE_KEY] instanceof Map
      ? (Map)state[QUICK_TOPOLOGY_BATCH_STATE_KEY] : [:]
  if(!ipAddress || !requestId || batch.requestId?.toString() != requestId ||
      data?.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(response == null || response.hasError()) {
    completeQuickTopologySpeaker(ipAddress, requestId)
    return
  }
  try {
    GPathResult description = new XmlSlurper().parseText(response.getData()?.toString())
    Map<String, Map> devicesByIp = batch.devicesByIp instanceof Map ? (Map<String, Map>)batch.devicesByIp : [:]
    Map topologyDevice = devicesByIp[ipAddress] ?: [:]
    processSonosDeviceInfo(data.playerInfo as LinkedHashMap, description, ipAddress,
        topologyDevice.topologyInfo instanceof Map ? (Map)topologyDevice.topologyInfo : [:], false)
  } catch(Exception e) {
    logDebug("Could not process topology device description from ${ipAddress}: ${e.message}")
  }
  completeQuickTopologySpeaker(ipAddress, requestId)
}

void completeQuickTopologySpeaker(String ipAddress, String requestId) {
  Map batch = state[QUICK_TOPOLOGY_BATCH_STATE_KEY] instanceof Map
      ? (Map)state[QUICK_TOPOLOGY_BATCH_STATE_KEY] : [:]
  if(!requestId || batch.requestId?.toString() != requestId ||
      batch.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  List<String> pendingIps = batch.pendingIps instanceof Collection
      ? new ArrayList<String>((Collection<String>)batch.pendingIps) : []
  pendingIps.remove(ipAddress)
  if(!pendingIps.isEmpty()) {
    batch.pendingIps = pendingIps
    state[QUICK_TOPOLOGY_BATCH_STATE_KEY] = batch
    return
  }

  applyQuickTopologyFallbackDevices(batch)
  state.remove(QUICK_TOPOLOGY_BATCH_STATE_KEY)
  state.remove(QUICK_TOPOLOGY_REQUEST_ID_STATE_KEY)
  logInfo("Sonos topology speaker details complete; refreshing the player page once")
  sendFoundSonosEvents([sessionId: atomicState.discoverySessionId])
}

void applyQuickTopologyFallbackDevices(Map batch) {
  Map<String, Map> devicesByIp = batch.devicesByIp instanceof Map ? (Map<String, Map>)batch.devicesByIp : [:]
  devicesByIp.each { String ipAddress, Map topologyDevice ->
    Boolean secondary = topologyDevice.secondary == true
    Map<String, String> topologyInfo = topologyDevice.topologyInfo instanceof Map
        ? (Map<String, String>)topologyDevice.topologyInfo : [:]
    String playerId = topologyInfo.UUID?.toString()
    String primaryDeviceId = topologyDevice.primaryDeviceId?.toString()
    Map target = secondary ? (Map)discoveredSonosSecondaries : (Map)discoveredSonoses
    Boolean alreadyKnown = target.values().any { Object raw ->
      raw instanceof Map && ((Map)raw).deviceIp?.toString() == ipAddress
    }
    if(alreadyKnown) { return }
    String playerName = topologyInfo.ZoneName?.toString() ?: playerId ?: "Sonos speaker at ${ipAddress}"
    Map fallback = [id: playerId ?: ipAddress, name: playerName,
      primaryDeviceId: primaryDeviceId, modelName: topologyInfo.ModelName,
      modelDisplayName: topologyInfo.ModelDisplayName, swGen: topologyInfo.SWGen,
      capabilities: [], deviceIp: ipAddress, localApiUrl: "https://${ipAddress}:1443/api/v1/",
      localUpnpUrl: "http://${ipAddress}:1400", localUpnpHost: "${ipAddress}:1400",
      topologyInfo: topologyInfo]
    target[ipAddress] = new LinkedHashMap(fallback)
  }
}

String xmlLocalName(GPathResult node) {
  String qualifiedName = node?.name()?.toString()
  if(!qualifiedName) { return null }
  Integer namespaceEnd = qualifiedName.lastIndexOf('}')
  if(namespaceEnd >= 0) { qualifiedName = qualifiedName.substring(namespaceEnd + 1) }
  Integer prefixEnd = qualifiedName.lastIndexOf(':')
  if(prefixEnd >= 0) { qualifiedName = qualifiedName.substring(prefixEnd + 1) }
  return qualifiedName
}

String xmlAttribute(GPathResult node, String attributeName) {
  if(node == null || !attributeName) { return null }
  return node.getAt("@${attributeName}")?.toString()
}

GPathResult findXmlDescendantByLocalName(GPathResult node, String localName) {
  if(node == null || !localName) { return null }
  for(Object child : node.children()) {
    if(child instanceof GPathResult) {
      GPathResult current = (GPathResult)child
      if(xmlLocalName(current) == localName) { return current }
      GPathResult hit = findXmlDescendantByLocalName(current, localName)
      if(hit != null) { return hit }
    }
  }
  return null
}

void collectXmlDescendantsByLocalName(GPathResult node, String localName, List<GPathResult> out) {
  if(node == null || !localName || out == null) { return }
  for(Object child : node.children()) {
    if(child instanceof GPathResult) {
      GPathResult current = (GPathResult)child
      if(xmlLocalName(current) == localName) { out.add(current) }
      collectXmlDescendantsByLocalName(current, localName, out)
    }
  }
}

GPathResult findDirectXmlChild(GPathResult node, String localName) {
  if(node == null || !localName) { return null }
  for(Object child : node.children()) {
    if(child instanceof GPathResult && xmlLocalName((GPathResult)child) == localName) { return (GPathResult)child }
  }
  return null
}

@CompileStatic
String getFoundSonoses() {
  String foundDevices = ""
  List<String> names = discoveredSonoses.collect { Object key, Object value -> ((Map)value)?.name?.toString() }.findAll { String value -> value }
  discoveredSonosSecondaries.each { Object key, Object value ->
    Map secondary = (Map)value
    String primaryId = secondary.primaryDeviceId?.toString()
    String primaryDni = getDNIFromRincon(primaryId)
    Map primary = primaryDni ? (Map)discoveredSonoses[primaryDni] : null
    if(primary != null) { names.add("${primary.name} (${secondary.modelDisplayName ?: secondary.modelName ?: "Secondary"})".toString()) }
  }
  names.sort().each { String name -> foundDevices += "\n${name}" }
  return foundDevices
}

void sendFoundSonosEvents(Map data = [:]) {
  if(data?.sessionId && data.sessionId.toString() != atomicState.discoverySessionId?.toString()) { return }
  if(atomicState[DISCOVERY_RUNNING_KEY] == true) {
    app.sendEvent(name: 'sonosDiscoveredCount', value: "Found Devices (${discoveredSonoses.size()} primary, ${discoveredSonosSecondaries.size()} secondary): " )
    app.sendEvent(name: 'sonosDiscovered', value: getFoundSonoses())
    app.sendEvent(name: 'newUiSpeakerTable', value: 'updated')
  }
}

String processServerSideRender(Map event) {
  if(event?.name == 'discoveryTimer') { return renderNewUiDiscoveryTimerMarkup() }
  if(event?.name == 'newUiSpeakerTable') {
    return "<div id='new-ui-speaker-table-wrapper'>${renderNewUiSpeakerTableMarkup()}</div>${renderNewUiSpeakerMobileListMarkup()}"
  }
  if(event?.name == NEW_UI_GROUP_TABLE_EVENT) {
    return "<div id='new-ui-group-table-wrapper'>${renderNewUiGroupTableMarkup()}</div>${renderNewUiGroupTableMobileListMarkup()}"
  }
  return ''
}

Map getDiscoveryStateSnapshot() {
  return [running: atomicState[DISCOVERY_RUNNING_KEY] == true,
    endTime: atomicState[DISCOVERY_END_TIME_KEY], primary: new LinkedHashMap(discoveredSonoses),
    secondaries: new LinkedHashMap(discoveredSonosSecondaries)]
}

void startNewUiDiscoverySession() {
  if(atomicState[DISCOVERY_RUNNING_KEY] == true) { stopDiscovery(); return }
  startDiscoverySession()
}

