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
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
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
  version: '0.19.0',
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
@Field static final String QUICK_TOPOLOGY_REQUESTS_STATE_KEY = 'quickTopologyRequests'
@Field static final String PENDING_DISCOVERY_CANDIDATES_STATE_KEY = 'pendingDiscoveryCandidates'
@Field static final String NEW_UI_GROUP_TABLE_EVENT = 'newUiGroupsTable'
@Field static final String NEW_UI_GROUP_DELETE_KEY = 'pendingNewUiDeleteGroup'
@Field static final String PENDING_GROUP_DEVICE_ACK_KEY = 'pendingGroupDeviceAcknowledgement'
@Field static final String PENDING_PLAYER_TABLE_REFRESH_KEY = 'pendingPlayerTableRefresh'
@Field static final String RINCON_PATTERN = '^RINCON_([0-9A-Fa-f]{12})0([1-9][0-9]{3})$'
// UPnP ports whose players SAC provisions. Multi-zone hardware (Amp Multi zones, the Arc/Ace headphone
// zone) answers on 1500-1700 with its own RINCON but shares one MAC and IP, so those stay unsupported
// until subscription demultiplexing through the 1400 host device exists. Widen this set to enable them.
@Field static final Set<Integer> SUPPORTED_UPNP_PORTS = Collections.unmodifiableSet(new HashSet<Integer>([1400]))
// The host player's port. Only this endpoint serves ZoneGroupTopology, so only a hit here may seed a topology request.
@Field static final Integer HOST_UPNP_PORT = 1400
@Field static final Integer PLAYER_TABLE_REFRESH_MAX_ATTEMPTS = 15
@Field static final List<String> QUICK_TOPOLOGY_DEVICE_ATTRIBUTE_NAMES = [
  'UUID', 'Location', 'ZoneName', 'Icon', 'Configuration', 'SoftwareGeneration',
  'MinCompatibleVersion', 'LegacyCompatibleVersion', 'BootSeq', 'TVConfiguration',
  'WirelessMode', 'HasConfiguredSSID', 'ChannelFreq', 'BehindWifiExtender', 'WifiEnabled',
  'EthLink', 'VoiceConfigState', 'MicEnabled', 'Invisible', 'HdmiCecAvailable',
  'HdmiArcAvailable', 'ModelName', 'ModelDisplayName', 'SWGen', 'SSLPort', 'ChannelMapSet', 'HTSatChanMapSet'
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

void updated() {
  initializeAfterInstall()
  syncManagedSettingsToParent()
}

void uninstalled() {
  stopDiscovery()
  try { parent.unregisterDiscoveryManagementChild(app.id) } catch(Exception ignored) { }
}

void initializeAfterInstall() {
  importLegacyDiscoveryState()
  migrateParentManagedSettings()
}

void migrateParentManagedSettings() {
  try {
    Map legacy = parent.getChildManagedSettingsForMigration() as Map ?: [:]
    List<String> keys = ['maximumTtsRetention', 'groupAnnouncementMode', 'highPriorityAnnouncementMode',
      'queueChimeSeparatelyFromTTS', 'suppressChimeOnSuccessiveTTS', 'enableAlarmManagement',
      'enableAlarmSwitches', 'autoCheckUpdates', 'autoInstallUpdates', 'updateCheckFrequency',
      'autoInstallTime', 'autoInstallNextOccurrence', 'autoInstallDayOfWeek', 'hubSecurityUsername',
      'hubSecurityPassword']
    Map<String, Object> defaults = [maximumTtsRetention: '60', groupAnnouncementMode: 'alwaysNonInterrupting',
      highPriorityAnnouncementMode: 'alwaysNonInterrupting', queueChimeSeparatelyFromTTS: false,
      suppressChimeOnSuccessiveTTS: false, enableAlarmManagement: false, enableAlarmSwitches: false,
      autoCheckUpdates: false, autoInstallUpdates: false, updateCheckFrequency: 'Daily',
      autoInstallNextOccurrence: false, autoInstallDayOfWeek: 'Sunday']
    keys.each { String key ->
      Object initialValue = legacy[key] != null ? legacy[key] : defaults[key]
      if(settings[key] == null && initialValue != null) {
        String type = key in ['enableAlarmManagement', 'enableAlarmSwitches', 'autoCheckUpdates', 'autoInstallUpdates', 'autoInstallNextOccurrence'] ? 'bool' :
            (key in ['maximumTtsRetention', 'groupAnnouncementMode', 'highPriorityAnnouncementMode', 'updateCheckFrequency', 'autoInstallDayOfWeek'] ? 'enum' : 'text')
        app.updateSetting(key, [type: type, value: initialValue])
      }
    }
    Map migrated = [:]
    keys.each { String key -> if(settings[key] != null) { migrated[key] = settings[key] } }
    parent.acknowledgeChildManagedSettings(migrated)
    ['libraryIdSMAPI', 'libraryIdUtilities'].each { String key ->
      try { app.removeSetting(key) } catch(Exception ignored) { }
      try { settings.remove(key) } catch(Exception ignored) { }
      try { app.updatedSettings?.remove(key) } catch(Exception ignored) { }
    }
  } catch(Exception e) {
    logWarn("Could not migrate parent settings to Discovery & Management: ${e.message}")
  }
}

void syncManagedSettingsToParent() {
  Map values = [:]
  ['maximumTtsRetention', 'groupAnnouncementMode', 'highPriorityAnnouncementMode',
    'queueChimeSeparatelyFromTTS', 'suppressChimeOnSuccessiveTTS', 'enableAlarmManagement',
    'enableAlarmSwitches', 'autoCheckUpdates', 'autoInstallUpdates', 'updateCheckFrequency',
    'autoInstallTime', 'autoInstallNextOccurrence', 'autoInstallDayOfWeek', 'hubSecurityUsername',
    'hubSecurityPassword'].each { String key ->
      values[key] = settings[key]
    }
  try { parent.updateChildManagedSettings(values) }
  catch(Exception e) { logWarn("Could not sync Discovery & Management settings to parent: ${e.message}") }
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
  migrateParentManagedSettings()
  dynamicPage(name: 'mainPage', title: 'Sonos Discovery & Management', install: true, uninstall: true) {
    section() {
      href(page: 'discoveryPage', title: 'Discover Sonos Speakers', description: 'Search the local network and select speakers to add or remove')
    }
  }
}

Map discoveryPage() {
  migrateParentManagedSettings()
  importLegacyDiscoveryState()
  if(state.managementOwner != true) {
    return dynamicPage(name: 'discoveryPage', title: 'Discovery App Already Active', install: false, uninstall: false) {
      section('Duplicate child app') {
        paragraph 'Another Sonos Discovery & Management child app instance is active. Remove this duplicate app instance and open the active one.'
      }
    }
  }
  consumeManagedGroupSave()
  restoreDiscoveredSonosSecondaries()
  startDiscoverySession()
  dynamicPage(name: 'discoveryPage', title: '', install: true, uninstall: false) {
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
  section() {
    input(name: 'managedGroupEditorPayload', type: 'text', title: 'Group editor data',
        required: false, submitOnChange: true)
    paragraph renderManagedGroupEditor(getNewUiGroupPlayerNameMap(), state.groupEditorOpen == true)
    state.remove('groupEditorOpen')
  }
  section() {
    paragraph displayAnnouncementSettingsMarkup()
  }
  section() {
    paragraph displayAlarmSettingsMarkup()
  }
  section() {
    paragraph displayAutoUpdateSettingsMarkup()
    input 'autoInstallTime', 'time', title: 'Automatic install time', required: false
    input 'hubSecurityUsername', 'text', title: 'Hub security username', required: false
    input 'hubSecurityPassword', 'password', title: 'Hub security password', required: false
  }
}

String renderManagedGroupEditor(Map players, Boolean visible = true) {
  String coordinator = visible ? (settings.managedGroupCoordinator?.toString() ?: '') : ''
  List<String> selectedFollowers = visible ? normalizeGroupIds(settings.managedGroupFollowers) : []
  String originalName = visible ? (state.groupEditorOriginalName?.toString() ?: '') : ''
  String title = originalName ? 'Edit Sonos group' : 'Create Sonos group'
  String refreshNameHandler = "this.closest('.sonos-group-editor').querySelector('[data-refresh-name]').onclick()"
  StringBuilder options = new StringBuilder("<option value=''>Choose a coordinator</option>")
  StringBuilder followers = new StringBuilder()
  players.each { Object rawId, Object rawLabel ->
    String id = rawId.toString()
    String value = escapeDiscoveryHtml(id)
    String label = escapeDiscoveryHtml(rawLabel)
    String selected = id == coordinator ? ' selected' : ''
    String unavailable = id == coordinator ? ' hidden' : ''
    String checked = id != coordinator && selectedFollowers.contains(id) ? ' checked' : ''
    options.append("<option value='${value}'${selected}>${label}</option>")
    followers.append("<label${unavailable}><input type='checkbox' value='${value}'${checked}> <span>${label}</span></label>")
  }
  String error = visible ? escapeDiscoveryHtml(state.groupManagementMessage) : ''
  // Use the native preference's submit-on-change path to save the draft and reload.
  String save = """
    const editor = this.closest('.sonos-group-editor');
    const name = editor.querySelector('#sonos-group-name').value.trim();
    const coordinator = editor.querySelector('#sonos-group-coordinator').value;
    const followers = Array.from(editor.querySelectorAll('input[type=checkbox]:checked'))
      .map(input => input.value).filter(id => id !== coordinator);
    const error = editor.querySelector('.sonos-group-editor-error');
    if (!name || !coordinator || !followers.length) {
      error.textContent = 'Choose a group name, one coordinator, and at least one different follower.';
      error.hidden = false;
      return;
    }
    const payload = document.querySelector("input[name='settings[managedGroupEditorPayload]']");
    if (!payload) {
      error.textContent = 'The group editor could not submit. Reopen Discovery & Management and try again.';
      error.hidden = false;
      return;
    }
    error.hidden = true;
    this.disabled = true;
    payload.value = JSON.stringify({name, coordinator, followers,
      originalName: editor.dataset.originalName, autoName: editor.dataset.autoName});
    payload.dispatchEvent(new Event('change', {bubbles: true}));
  """
  String refreshName = """
    const editor = this.closest('.sonos-group-editor');
    const coordinator = editor.querySelector('#sonos-group-coordinator');
    const name = editor.querySelector('#sonos-group-name');
    const followerNames = [];
    editor.querySelectorAll('input[type=checkbox]').forEach(input => {
      const isCoordinator = input.value === coordinator.value;
      input.closest('label').hidden = isCoordinator;
      if (isCoordinator) input.checked = false;
      if (input.checked) followerNames.push(input.nextElementSibling.textContent);
    });
    const coordinatorName = coordinator.value ? coordinator.selectedOptions[0].textContent : '';
    const fullName = coordinatorName ? [coordinatorName, ...followerNames].join(' + ') : '';
    const suggestion = followerNames.length > 1 && fullName.length > 32
      ? coordinatorName + ' + ' + followerNames.length + ' others' : fullName;
    if (name.value === editor.dataset.autoName) name.value = suggestion;
    editor.dataset.autoName = suggestion;
  """
  String openEditor = """
    const editor = this.closest('.sonos-group-editor');
    const draft = this.groupDraft;
    const name = editor.querySelector('#sonos-group-name');
    editor.dataset.originalName = draft.name;
    editor.dataset.autoName = '';
    editor.querySelector('.new-ui-group-toolbar-title').textContent = draft.name ? 'Edit Sonos group' : 'Create Sonos group';
    editor.querySelector('#sonos-group-coordinator').value = draft.coordinator;
    editor.querySelectorAll('input[type=checkbox]').forEach(input => {
      input.checked = draft.followers.includes(input.value);
    });
    name.value = '';
    editor.querySelector('[data-refresh-name]').onclick();
    if (draft.name) name.value = draft.name;
    const error = editor.querySelector('.sonos-group-editor-error');
    if (error) error.hidden = true;
    editor.hidden = false;
    name.focus({preventScroll: true});
    editor.scrollIntoView({block: 'nearest'});
  """
  String markup = """
    <style>${managedGroupEditorStyle()}</style>
    <div class='new-ui-groups-card sonos-group-editor' data-original-name='${escapeDiscoveryHtml(originalName)}'
      data-auto-name='${escapeDiscoveryHtml(visible ? state.groupEditorAutoName : '')}'${visible ? '' : ' hidden'}
      onclick='event.stopPropagation()' oninput='event.stopPropagation()' onchange='event.stopPropagation()'>
      <span id='sonos-group-editor-open' hidden onclick='${escapeDiscoveryHtml(openEditor)}'></span>
      <span data-refresh-name hidden onclick='${escapeDiscoveryHtml(refreshName)}'></span>
      <div class='new-ui-group-toolbar-container'>
        <div class='new-ui-group-toolbar'>
          <div class='new-ui-group-toolbar-title' role='heading' aria-level='3'>${title}</div>
        </div>
      </div>
      <div class='sonos-group-editor-body'>
        <p class='sonos-group-editor-error' role='alert'${error ? '' : ' hidden'}>${error}</p>
        <div class='sonos-group-editor-grid'>
          <div class='sonos-group-editor-fields'>
            <label for='sonos-group-name'><span>Group name</span>
              <input id='sonos-group-name' type='text' maxlength='128' autocomplete='off'
                onkeydown="if(event.key === 'Enter'){event.preventDefault();event.stopPropagation()}"
                value='${escapeDiscoveryHtml(visible ? settings.managedGroupName : '')}'>
            </label>
            <label for='sonos-group-coordinator'><span>Coordinator</span>
              <select id='sonos-group-coordinator' onchange="${refreshNameHandler}">${options}</select>
            </label>
          </div>
          <fieldset onchange="${refreshNameHandler}"><legend>Followers</legend>
            <div class='sonos-group-followers'>${followers ?: 'No speakers available.'}</div>
          </fieldset>
        </div>
        <div class='sonos-group-editor-actions'>
          <button type='button' class='sonos-group-save' onclick='${escapeDiscoveryHtml(save)}'>Save group</button>
          <button type='button' onclick="this.closest('.sonos-group-editor').hidden = true">Cancel</button>
        </div>
      </div>
    </div>
  """
  // Keep block elements adjacent in the Hubitat paragraph, just like the group table.
  return markup.readLines().collect { String line -> line.trim() }.join(' ').trim().replaceAll(/> +</, '><')
}

String managedGroupEditorStyle() {
  return """
    .mdl-cell:has(input[name='settings[managedGroupEditorPayload]']):not(:has(.mdl-cell input[name='settings[managedGroupEditorPayload]'])) {
      display: none !important;
    }
    .sonos-group-editor { white-space: normal; }
    .sonos-group-editor [hidden], .sonos-group-editor[hidden] { display: none !important; }
    .mdl-cell:has(.sonos-group-editor[hidden]):not(:has(.mdl-cell .sonos-group-editor[hidden])) {
      display: none !important;
    }
    .sonos-group-editor .new-ui-group-toolbar {
      align-items: center;
      justify-content: center;
    }
    .sonos-group-editor .new-ui-group-toolbar-title {
      position: static; transform: none;
    }
    .sonos-group-editor-body { padding: 16px 18px; }
    .sonos-group-editor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
    .sonos-group-editor-fields { display: grid; align-content: start; gap: 14px; min-width: 0; }
    .sonos-group-editor-fields > label { display: grid; gap: 6px; font-weight: 600; }
    .sonos-group-editor :is(input[type='text'], select) {
      box-sizing: border-box; width: 100%; padding: 9px 12px; min-height: 42px;
      border: 1px solid var(--surface-border, #c8cdd5); border-radius: 8px;
      background: var(--surface-a, #fff); color: inherit; font: inherit; font-weight: 400;
    }
    .sonos-group-editor fieldset {
      min-width: 0; margin: 0; padding: 8px 10px;
      border: 1px solid var(--surface-border, #c8cdd5); border-radius: 8px;
    }
    .sonos-group-editor legend { padding: 0 5px; font-weight: 600; }
    .sonos-group-followers {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(min(165px, 100%), 1fr)); gap: 4px 8px;
    }
    .sonos-group-followers label {
      display: flex; align-items: center; gap: 8px; min-height: 40px;
      padding: 0 8px; border-radius: 6px; cursor: pointer;
    }
    .sonos-group-followers label[hidden] { display: none; }
    .sonos-group-followers label:hover { background: var(--surface-b, #f3f5fb); }
    .sonos-group-followers input { accent-color: #4054b2; }
    .sonos-group-editor-actions {
      display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; margin-top: 14px;
    }
    .sonos-group-editor-actions button {
      cursor: pointer; font: inherit; font-weight: 600; min-height: 40px; padding: 8px 15px;
      color: inherit; background: var(--surface-b, #f7f8fa);
      border: 1px solid var(--surface-border, #c8cdd5); border-radius: 8px;
    }
    .sonos-group-editor-actions .sonos-group-save { color: #fff; background: #4054b2; border-color: #4054b2; }
    .sonos-group-editor-error { color: #a52822; background: #fff1f0; border-radius: 7px; padding: 9px 12px; }
    @media (max-width: 640px) {
      .sonos-group-editor-grid { grid-template-columns: minmax(0, 1fr); }
    }
  """
}

String displayAnnouncementSettingsMarkup() {
  Map<String, String> retentionOptions = ['1':'1 minute','3':'3 minutes','5':'5 minutes','10':'10 minutes','15':'15 minutes','30':'30 minutes','60':'1 hour','120':'2 hours','180':'3 hours','240':'4 hours','360':'6 hours','720':'12 hours','1440':'24 hours','unlimited':'Unlimited']
  Map<String, String> announcementOptions = [automatic:'Automatic',alwaysNonInterrupting:'Always non-interrupting',alwaysCoordinatorStream:'Always standard stream']
  StringBuilder html = new StringBuilder("<style>${managedSettingsStyle()}</style>")
  html.append("<details class='sonos-managed-disclosure sonos-announcement-disclosure' open><summary>Announcements</summary>")
  html.append("<div class='sonos-managed-settings'>")
  html.append(renderManagedSelect('maximumTtsRetention', 'Maximum TTS retention', 'Maximum time a queued TTS message can wait before it is sent.', retentionOptions, '60'))
  html.append(renderManagedSelect('groupAnnouncementMode', 'Normal priority announcement method', 'Choose how regular announcements join Sonos groups.', announcementOptions, 'alwaysNonInterrupting'))
  html.append(renderManagedSelect('highPriorityAnnouncementMode', 'High priority announcement method', 'Choose how high priority announcements join Sonos groups.', announcementOptions, 'alwaysNonInterrupting'))
  html.append(renderManagedToggle('queueChimeSeparatelyFromTTS', 'Queue chime separately from TTS for better sync', 'Coordinate chime release before preparing the spoken audio.'))
  html.append(renderManagedToggle('suppressChimeOnSuccessiveTTS', 'Suppress chime on successive TTS messages', 'Skip a chime when another TTS announcement is active or waiting.'))
  html.append('</div></details>')
  return html.toString()
}

String displayAlarmSettingsMarkup() {
  StringBuilder html = new StringBuilder("<style>${managedSettingsStyle()}</style>")
  html.append("<details class='sonos-managed-disclosure' open><summary>Alarm management</summary>")
  html.append("<div class='sonos-managed-settings'>")
  html.append(renderManagedToggle('enableAlarmManagement', 'Sonos Alarm Manager', 'Create a household-level alarm manager device.'))
  html.append(renderManagedToggle('enableAlarmSwitches', 'Sonos alarm switches', 'Create a switch device for each Sonos alarm.'))
  html.append('</div></details>')
  return html.toString()
}

String displayAutoUpdateSettingsMarkup() {
  Map<String, String> frequencyOptions = [Daily:'Daily',Weekly:'Weekly']
  Map<String, String> dayOptions = [Sunday:'Sunday',Monday:'Monday',Tuesday:'Tuesday',Wednesday:'Wednesday',Thursday:'Thursday',Friday:'Friday',Saturday:'Saturday']
  StringBuilder html = new StringBuilder("<style>${managedSettingsStyle()}${autoUpdateSettingsStyle()}</style>")
  html.append("<details class='sonos-update-settings-disclosure'><summary>Automatic updates</summary>")
  html.append("<div class='sonos-managed-settings'>")
  html.append(renderManagedToggle('autoCheckUpdates', 'Check for updates automatically', 'Schedule checks for new app releases.'))
  html.append(renderManagedToggle('autoInstallUpdates', 'Install updates automatically', 'Install available updates using the schedule below.'))
  html.append(renderManagedSelect('updateCheckFrequency', 'Update check frequency', 'How often to check for app updates.', frequencyOptions, 'Daily'))
  html.append(renderManagedToggle('autoInstallNextOccurrence', 'Install at the next occurrence of this time', 'When off, install on the selected weekday.'))
  html.append(renderManagedSelect('autoInstallDayOfWeek', 'Automatic install day', 'Weekday used when next-occurrence scheduling is off.', dayOptions, 'Sunday'))
  html.append('</div></details>')
  return html.toString()
}

String autoUpdateSettingsStyle() {
  // Scope the layout to the innermost grid containing the disclosure. Hubitat renders
  // the native inputs as siblings of the paragraph, so CSS follows the disclosure state.
  String disclosure = '.sonos-update-settings-disclosure'
  String grid = ".mdl-grid:has(${disclosure}):not(:has(.mdl-grid ${disclosure}))"
  String cells = "${grid} > .mdl-cell"
  String fields = "${cells}:not(:has(${disclosure}))"
  String timeField = "${fields}:has(input[name='settings[autoInstallTime]'])"
  String timeGrid = "${timeField} .mdl-grid:has(input[type='number'])"
  String timeRow = "${timeGrid}:not(:has(.mdl-grid input[type='number']))"
  return """
    ${grid} {
      display: flex !important;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: flex-start;
      gap: 14px;
      padding-inline: 16px !important;
    }
    ${cells}:has(${disclosure}) {
      box-sizing: border-box;
      flex: 0 0 100%;
      min-width: 0;
      max-width: 100% !important;
      width: 100% !important;
      margin: 0 !important;
    }
    ${fields} {
      box-sizing: border-box;
      flex: 0 1 220px;
      min-width: 0;
      width: 220px !important;
      margin: 0 !important;
    }
    ${timeField} {
      flex-basis: 290px;
      width: 290px !important;
    }
    ${timeGrid} {
      margin-bottom: 0 !important;
      padding-bottom: 0 !important;
    }
    ${timeRow} {
      display: grid !important;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      max-width: 156px;
    }
    ${timeRow} > .mdl-cell:has(input[type='number']) {
      min-width: 0;
      width: auto !important;
      margin: 0 !important;
    }
    ${timeField} input[type='number'] {
      display: block;
      min-width: 64px;
    }
    ${grid}:not(:has(${disclosure}[open])) > .mdl-cell:not(:has(${disclosure})) {
      display: none !important;
    }
    ${fields} input:is([type='number'], [type='text'], [type='password']) {
      box-sizing: border-box;
      width: 100%;
      min-height: 42px;
      padding: 8px 10px;
      border: 1px solid var(--surface-border, #c8cdd5);
      border-radius: 8px;
      background: var(--surface-a, #fff);
      color: var(--text-color, #30343b);
      font: inherit;
    }
    @media (width > 800px) {
      /* Hubitat's time control reserves space below its visible input boxes. */
      ${fields}:has(input:is([name='settings[hubSecurityUsername]'], [name='settings[hubSecurityPassword]'])) {
        margin-bottom: 13px !important;
      }
    }
    @media (max-width: 800px) {
      ${fields}, ${timeField} {
        flex: 0 0 100%;
        width: 100% !important;
        max-width: 100% !important;
      }
      ${timeGrid} {
        margin: 0 !important;
        padding: 0 !important;
      }
      ${timeRow} { max-width: none; }
      ${timeField} input[type='number'] { min-width: 0; }
      ${fields} .mdl-textfield { width: 100%; }
    }
  """
}

String managedSettingsStyle() {
  String disclosure = ':is(.sonos-managed-disclosure, .sonos-update-settings-disclosure)'
  return """
    ${disclosure} > summary {
      display: flex;
      align-items: center;
      justify-content: space-between;
      list-style: none;
      cursor: pointer;
      padding: 12px 16px;
      border: 1px solid var(--surface-border, #d8dde5);
      border-radius: 8px;
      background: var(--surface-b, #f7f8fa);
      color: var(--text-color, #30343b);
      font-weight: 650;
    }
    ${disclosure} > summary::-webkit-details-marker { display: none; }
    ${disclosure} > summary::after { content: '+'; font-size: 20px; }
    ${disclosure}[open] > summary::after { content: '−'; }
    ${disclosure} > .sonos-managed-settings { margin: 14px 0 0; }

    .sonos-managed-settings {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(280px, 100%), 1fr));
      grid-auto-rows: 184px;
      gap: 12px;
      margin: 0 0 18px;
    }
    .sonos-managed-card {
      box-sizing: border-box;
      height: 184px;
      border: 1px solid var(--surface-border, #ddd);
      border-radius: 8px;
      padding: 13px 15px;
      background: var(--surface-card, #fff);
      color: var(--text-color, #30343b);
      box-shadow: 0 2px 8px rgba(25, 35, 55, .06);
    }
    .sonos-managed-title { font-weight: 650; }
    .sonos-managed-description { color: var(--text-color-secondary, #666); font-size: .92em; margin-top: 5px; }
    .sonos-managed-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .sonos-managed-switch { position: relative; display: inline-block; flex: 0 0 39px; width: 39px; height: 14px; border-radius: 7px; }
    .sonos-managed-knob { position: absolute; top: -3px; width: 21px; height: 21px; border-radius: 50%; box-shadow: 0 2px 5px rgba(0, 0, 0, .25); }
    .sonos-managed-select { position: relative; margin-top: 9px; }
    .sonos-managed-select summary { list-style: none; cursor: pointer; padding: 9px 34px 9px 10px; border: 1px solid var(--surface-border, #bbb); border-radius: 6px; background: var(--surface-card, #fff); position: relative; }
    .sonos-managed-select summary::-webkit-details-marker { display: none; }
    .sonos-managed-select summary:after { content: '⌄'; position: absolute; right: 12px; top: 7px; font-size: 18px; }
    .sonos-managed-options { width: 100%; border-collapse: collapse; border: 1px solid var(--surface-border, #bbb); border-top: 0; background: var(--surface-card, #fff); display: block; max-height: 240px; overflow: auto; }
    .sonos-managed-options td { padding: 0; }
    .sonos-managed-option { padding: 9px 10px; cursor: pointer; }
    .sonos-managed-option:hover { background: var(--surface-b, #eef2ff); }
    .sonos-managed-selected { font-weight: 650; color: var(--primary-color, #3347a9); }

    .sonos-announcement-disclosure .sonos-managed-card:has(> .sonos-managed-select) {
      display: flex;
      flex-direction: column;
    }
    .sonos-announcement-disclosure .sonos-managed-select { margin-top: auto; }
    .sonos-announcement-disclosure .sonos-managed-select[open] { z-index: 1; }
    .sonos-announcement-disclosure .sonos-managed-options {
      position: absolute;
      top: 100%;
      left: 0;
      box-sizing: border-box;
    }
  """
}

String renderManagedToggle(String key, String title, String description) {
  Boolean enabled = settings[key] == true || settings[key]?.toString() == 'true'
  String value = enabled ? 'false' : 'true'
  String action = "childSetting|${key}|${value}"
  String position = enabled ? '20px' : '-1px'
  String track = enabled ? 'rgba(63,81,181,.5)' : 'rgba(0,0,0,.38)'
  String knob = enabled ? '#3f51b5' : '#b0b0b0'
  return "<div class='sonos-managed-card'><div class='sonos-managed-row'><div><div class='sonos-managed-title'>${escapeDiscoveryHtml(title)}</div><div class='sonos-managed-description'>${escapeDiscoveryHtml(description)}</div></div><div class='form-group'><input type='hidden' name='${action}.type' value='button'></div><div style='display:inline-block'><div class='submitOnChange sonos-managed-switch' role='switch' aria-checked='${enabled}' aria-label='${escapeDiscoveryHtml(title)}' onclick='buttonClick(this)' style='background:${track};cursor:pointer'><span class='sonos-managed-knob' style='left:${position};background:${knob}'></span></div></div><input type='hidden' name='settings[${action}]' value=''></div></div>"
}

String renderManagedSelect(String key, String title, String description, Map<String, String> options, String defaultValue) {
  String selected = settings[key]?.toString() ?: defaultValue
  if(!options.containsKey(selected)) { selected = defaultValue }
  StringBuilder html = new StringBuilder("<div class='sonos-managed-card'><div class='sonos-managed-title'>${escapeDiscoveryHtml(title)}</div><div class='sonos-managed-description'>${escapeDiscoveryHtml(description)}</div><details class='sonos-managed-select'><summary>${escapeDiscoveryHtml(options[selected])}</summary><table class='sonos-managed-options'><tbody>")
  options.each { String value, String label ->
    String action = "childSetting|${key}|${value}"
    String selectedClass = value == selected ? ' sonos-managed-selected' : ''
    String check = value == selected ? ' ✓' : ''
    html.append("<tr><td><div class='form-group'><input type='hidden' name='${action}.type' value='button'></div><div style='display:inline-block;width:100%'><div class='submitOnChange sonos-managed-option${selectedClass}' role='button' tabindex='0' onclick='buttonClick(this)' onkeydown=\"if(event.key==='Enter'||event.key===' '){event.preventDefault();buttonClick(this)}\">${escapeDiscoveryHtml(label)}${check}</div></div><input type='hidden' name='settings[${action}]' value=''></td></tr>")
  }
  html.append('</tbody></table></details></div>')
  return html.toString()
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
  if(buttonName?.startsWith('childSetting|')) {
    List<String> parts = splitTokens(buttonName, '|')
    if(parts.size() != 3) { return }
    String key = parts[1]
    String value = parts[2]
    Map<String, List<String>> allowed = [
      maximumTtsRetention: ['1','3','5','10','15','30','60','120','180','240','360','720','1440','unlimited'],
      groupAnnouncementMode: ['automatic','alwaysNonInterrupting','alwaysCoordinatorStream'],
      highPriorityAnnouncementMode: ['automatic','alwaysNonInterrupting','alwaysCoordinatorStream'],
      queueChimeSeparatelyFromTTS: ['true','false'], suppressChimeOnSuccessiveTTS: ['true','false'],
      enableAlarmManagement: ['true','false'], enableAlarmSwitches: ['true','false'],
      autoCheckUpdates: ['true','false'], autoInstallUpdates: ['true','false'],
      updateCheckFrequency: ['Daily','Weekly'], autoInstallNextOccurrence: ['true','false'],
      autoInstallDayOfWeek: ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday']
    ]
    if(!(key in allowed.keySet()) || !(value in allowed[key])) { return }
    Boolean boolValue = key in ['queueChimeSeparatelyFromTTS','suppressChimeOnSuccessiveTTS','enableAlarmManagement','enableAlarmSwitches','autoCheckUpdates','autoInstallUpdates','autoInstallNextOccurrence']
    Object typedValue = boolValue ? value == 'true' : value
    String type = boolValue ? 'bool' : 'enum'
    app.updateSetting(key, [type: type, value: typedValue])
    settings[key] = typedValue
    syncManagedSettingsToParent()
    return
  }
  if(buttonName?.startsWith('newUiMobile')) { buttonName = buttonName.replaceFirst('newUiMobile', 'newUi') }
  if(buttonName == 'btnNewUiDiscoverSpeakers') { startNewUiDiscoverySession(); return }
  if(buttonName == 'btnExtend60') { extendDiscovery(60); return }
  if(buttonName == 'btnExtend300') { extendDiscovery(300); return }
  if(buttonName == 'deleteManagedGroup') { deleteManagedGroup(); return }
  if(buttonName?.startsWith('newUiDeleteGroup|')) {
    Map row = findNewUiGroupByToken(buttonName.substring('newUiDeleteGroup|'.length()))
    if(row?.name) {
      // Pass the resolved name directly: updateSetting() is not guaranteed to be visible through settings in this execution.
      app.updateSetting('managedGroupSelection', [type: 'enum', value: row.name.toString()])
      deleteManagedGroup(row.name.toString())
    }
    return
  }
  if(buttonName?.startsWith('newUiCreatePlayer|')) {
    // The row's add button creates exactly the clicked speaker.
    queuePlayerCreation(buttonName.substring('newUiCreatePlayer|'.length()))
    return
  }
  if(buttonName?.startsWith('newUiDeletePlayer|')) {
    state.pendingNewUiDeletePlayer = buttonName.substring('newUiDeletePlayer|'.length())
    return
  }
  if(buttonName == 'btnConfirmNewUiDeletePlayer') {
    String key = state.pendingNewUiDeletePlayer?.toString()
    Map row = buildNewUiSpeakerRows().find { Map item -> item.discoveryKey?.toString() == key || item.id?.toString() == key }
    String dni = row?.discoveryKey?.toString()
    // Remove only the confirmed speaker.
    if(dni) { queuePlayerRemoval([dni]) }
    state.remove('pendingNewUiDeletePlayer')
    return
  }
  if(buttonName == 'btnCancelNewUiDeletePlayer') { state.remove('pendingNewUiDeletePlayer'); return }
}

String managedGroupOpenClick(String coordinator = '', String name = '', List<String> followers = []) {
  String draft = escapeDiscoveryHtml(JsonOutput.toJson([name: name, coordinator: coordinator, followers: followers]))
  return "event.preventDefault();event.stopPropagation();var opener=document.getElementById('sonos-group-editor-open');if(opener){opener.groupDraft=${draft};opener.onclick()}"
}

void consumeManagedGroupSave() {
  String payload = settings.managedGroupEditorPayload?.toString()
  if(!payload) { return }
  app.removeSetting('managedGroupEditorPayload')
  Map draft = [:]
  if(payload.length() <= 8192) {
    try {
      Object decoded = new JsonSlurper().parseText(payload)
      if(decoded instanceof Map) { draft = (Map)decoded }
    } catch(IllegalArgumentException ignored) {
      // Treat malformed submissions as missing fields.
    } catch(groovy.json.JsonException ignored) {
      // Treat malformed submissions as missing fields.
    }
  }
  String name = draft.name instanceof String ? draft.name.trim() : ''
  String selectedGroup = draft.originalName instanceof String ? draft.originalName.trim() : ''
  String coordinatorId = draft.coordinator instanceof String ? draft.coordinator.trim() : ''
  List<String> followers = draft.followers instanceof List ? normalizeGroupIds(draft.followers) : []
  app.updateSetting('managedGroupName', [type: 'text', value: name])
  app.updateSetting('managedGroupCoordinator', [type: 'enum', value: coordinatorId])
  app.updateSetting('managedGroupFollowers', [type: 'enum', value: followers])
  state.groupEditorOriginalName = selectedGroup
  state.groupEditorAutoName = draft.autoName instanceof String ? draft.autoName.take(512) : ''
  state.groupEditorOpen = true
  if(!name || name.length() > 128 || selectedGroup.length() > 128 || !coordinatorId ||
      followers.isEmpty() || followers.contains(coordinatorId)) {
    state.groupManagementMessage = 'Choose a group name, one coordinator, and at least one different follower.'
    return
  }
  Map result
  try {
    result = parent.submitGroupManagementRequest([
      schemaVersion: 1, requestId: UUID.randomUUID().toString(), action: 'upsert',
      oldName: selectedGroup, name: name, groupCoordinatorId: coordinatorId, playerIds: followers
    ]) as Map
  } catch(Exception e) {
    logWarn("Could not submit group save: ${e.class.simpleName}")
    state.groupManagementMessage = 'The group change could not be queued.'
    return
  }
  if(result?.accepted != true) {
    state.groupManagementMessage = 'The group change could not be queued.'
    return
  }
  String requestId = result.requestId?.toString()
  Map<String, Map> groups = copyGroupConfigurations((Map)(state.groupConfigurations ?: [:]))
  if(selectedGroup && selectedGroup != name) { groups.remove(selectedGroup) }
  groups[name] = [groupCoordinatorId: coordinatorId, playerIds: followers.unique()]
  state.groupConfigurations = groups
  app.updateSetting('managedGroupSelection', [type: 'enum', value: name])
  state.groupEditorOpen = false
  state.remove('groupEditorOriginalName')
  state.remove('groupEditorAutoName')
  state.groupManagementMessage = 'Group changes have been queued.'
  if(requestId) {
    state[PENDING_GROUP_DEVICE_ACK_KEY] = [requestId: requestId, name: name, attempts: 0]
    runIn(2, 'pollGroupDeviceAcknowledgement')
  }
}

void pollGroupDeviceAcknowledgement() {
  Map pending = state[PENDING_GROUP_DEVICE_ACK_KEY] instanceof Map
      ? (Map)state[PENDING_GROUP_DEVICE_ACK_KEY] : [:]
  String requestId = pending.requestId?.toString()
  if(!requestId) { return }
  Map result
  try {
    result = parent.getGroupManagementResult(requestId) as Map
  } catch(Exception e) {
    logDebug("Waiting for parent group creation acknowledgement (${e.class.simpleName})")
    result = [status: 'pending']
  }
  if(result?.status == 'created' && result.deviceId) {
    state.remove(PENDING_GROUP_DEVICE_ACK_KEY)
    app.sendEvent(name: NEW_UI_GROUP_TABLE_EVENT, value: result.deviceId.toString())
    return
  }
  if(result?.status == 'failed') {
    state.remove(PENDING_GROUP_DEVICE_ACK_KEY)
    state.groupManagementMessage = 'The group settings were saved, but its device could not be created.'
    return
  }
  Integer attempts = (pending.attempts instanceof Number ? (Number)pending.attempts : 0).intValue() + 1
  if(attempts >= 30) {
    state.remove(PENDING_GROUP_DEVICE_ACK_KEY)
    logWarn("No parent acknowledgement received for group request ${requestId}")
    return
  }
  state[PENDING_GROUP_DEVICE_ACK_KEY] = pending + [attempts: attempts]
  runIn(2, 'pollGroupDeviceAcknowledgement')
}

void deleteManagedGroup(String nameOverride = null) {
  String name = (nameOverride ?: settings.managedGroupSelection)?.toString()?.trim()
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

void queuePlayerRemoval(List<String> remove) {
  Map result = parent.submitPlayerRemovalRequest([schemaVersion: 1, requestId: UUID.randomUUID().toString(), remove: remove]) as Map
  state.playerRequestMessage = result?.accepted == true ? 'Speaker removal has been queued.' : 'Speaker removal could not be queued.'
  if(result?.accepted == true) { schedulePlayerTableRefresh([], remove) }
}

void logError(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info', 'warn', 'error']) { log.error("${app.label ?: app.name}: ${message}") } }
void logWarn(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info', 'warn']) { log.warn("${app.label ?: app.name}: ${message}") } }
void logInfo(String message) { if(getConfiguredLogLevel() in ['trace', 'debug', 'info']) { log.info("${app.label ?: app.name}: ${message}") } }
void logDebug(String message) { if(getConfiguredLogLevel() in ['trace', 'debug']) { log.debug("${app.label ?: app.name}: ${message}") } }
void logTrace(String message) { if(getConfiguredLogLevel() == 'trace') { log.trace("${app.label ?: app.name}: ${message}") } }
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
        groupAction = newUiButtonLink('', 'Create Group From', '#fff', '13px', 'Create group from speaker',
            'new-ui-mobile-action-button new-ui-mobile-create-group-button', managedGroupOpenClick(row.id.toString()))
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
    String ip = secondary.deviceIp?.toString()
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
  return newUiButtonLink('', groupIcon, '#4CAF50', '20px', 'Create group from speaker', '', managedGroupOpenClick(row.id.toString()))
}

String newUiButtonLink(String buttonName, String linkText, String color = '#1A77C9', String font = '15px', String ariaLabel = null, String cssClass = '', String clickHandler = null) {
  String safeAriaLabel = ariaLabel ? " aria-label='${escapeNewUiHtml(ariaLabel)}'" : ''
  String safeCssClass = cssClass ? " ${escapeNewUiHtml(cssClass)}" : ''
  String action = clickHandler ?: 'buttonClick(this)'
  String actionClass = clickHandler ? 'sonos-local-action' : 'submitOnChange'
  String control = "<div style='display:inline-block'><div class='${actionClass}${safeCssClass}' role='button' tabindex='0'${safeAriaLabel} onclick=\"${action}\" onkeydown=\"if(event.key==='Enter'||event.key===' '){event.preventDefault();${action}}\" style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>"
  if(clickHandler) { return control }
  return "<div class='form-group'><input type='hidden' name='${buttonName}.type' value='button'></div>" + control +
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
      #new-ui-group-table td:first-child :is(.submitOnChange, .sonos-local-action) {
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
      .new-ui-group-mobile-buttons :is(.submitOnChange, .sonos-local-action) { align-items: center; border: 1px solid transparent; border-radius: 7px; box-sizing: border-box; display: inline-flex !important; font-size: 13px !important; font-weight: 600; justify-content: center; line-height: 1.2; min-height: 38px; padding: 9px 15px; text-decoration: none; }
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
  String createButton = newUiButtonLink('', "${createIcon}<span>Create group</span>", '#fff', '14px',
      'Create a Sonos group', 'new-ui-group-create-button', managedGroupOpenClick())
  return "${css.trim()}${iconifyScript}<div class='new-ui-groups-card'><div class='new-ui-group-toolbar-container'><div class='new-ui-group-toolbar'><div class='new-ui-group-toolbar-title'>Sonos groups</div>${createButton}</div></div><span class='ssr-app-state-${app.id}-${NEW_UI_GROUP_TABLE_EVENT}'>${renderNewUiGroupViews()}</span></div>"
}

String renderNewUiGroupViews() {
  List<Map> rows = buildNewUiGroupRows()
  return "<div id='new-ui-group-table-wrapper'>${renderNewUiGroupTableMarkup(rows)}</div>${renderNewUiGroupTableMobileListMarkup(rows)}"
}

String renderNewUiGroupTableMarkup(List<Map> rows = buildNewUiGroupRows()) {
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
      String actionMarkup = newUiButtonLink('', editIcon, 'var(--text-color, #424242)', '19px', 'Edit group', '',
          managedGroupOpenClick(row.coordinatorId?.toString() ?: '', groupName, normalizeGroupIds(row.followerIds))) +
          newUiButtonLink("newUiDeleteGroup|${token}", deleteIcon, '#F44336', '20px')
      String safeName = escapeNewUiHtml(groupName)
      String groupDeviceId = row.device?.id?.toString()
      if(groupDeviceId) {
        safeName = deviceEditLink(groupDeviceId, safeName)
      }
      String coordinatorName = row.coordinatorName?.toString() ?: row.coordinatorId?.toString() ?: 'Unknown'
      String safeCoordinator = escapeNewUiHtml(coordinatorName)
      String followerMarkup = renderNewUiGroupFollowers(row.followerNames)

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

String renderNewUiGroupTableMobileListMarkup(List<Map> rows = buildNewUiGroupRows()) {
  StringBuilder html = new StringBuilder("<div id='new-ui-group-mobile-list' class='new-ui-mobile-list new-ui-group-mobile-list'>")
  if(rows.isEmpty()) {
    html.append("<div class='new-ui-mobile-empty'>No Sonos groups have been created yet.</div>")
  } else {
    rows.each { Map row ->
      String groupName = row.name?.toString() ?: 'Unnamed group'
      String token = newUiGroupToken(groupName)
      String actions = newUiButtonLink('', 'Edit', '#fff', '13px', 'Edit group', 'new-ui-group-mobile-edit',
          managedGroupOpenClick(row.coordinatorId?.toString() ?: '', groupName, normalizeGroupIds(row.followerIds))) +
          newUiButtonLink("newUiMobileDeleteGroup|${token}", 'Delete', '#c83d38', '13px', 'Delete group', 'new-ui-group-mobile-delete')
      String safeName = escapeNewUiHtml(groupName)
      String groupDeviceId = row.device?.id?.toString()
      if(groupDeviceId) {
        safeName = deviceEditLink(groupDeviceId, safeName)
      }
      String coordinator = escapeNewUiHtml(row.coordinatorName?.toString() ?: row.coordinatorId?.toString() ?: 'Unknown')
      String followers = renderNewUiGroupFollowers(row.followerNames)
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

String renderNewUiGroupFollowers(List<String> names) {
  if(!names) {
    return newUiStatusBadge('—', 'na', 'No follower speakers configured')
  }
  return names.collect { String name -> escapeNewUiHtml(name) }.join('<br>')
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
  rows.each { Map row ->
    row.coordinatorName = names[row.coordinatorId?.toString()] ?: row.coordinatorId
    row.followerNames = ((List<String>)row.followerIds).collect { String id -> (names[id] ?: id).toString() }
  }
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
  state.remove(QUICK_TOPOLOGY_REQUESTS_STATE_KEY)
  state.remove(PENDING_DISCOVERY_CANDIDATES_STATE_KEY)
  // Transient keys written by earlier versions of the per-speaker topology batch.
  ['quickTopologyRequestId', 'quickTopologyRefreshBatch'].each { String legacyKey -> state.remove(legacyKey) }
  atomicState.remove('quickTopologyQueriedDuringDiscovery')
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
  state.remove(QUICK_TOPOLOGY_REQUESTS_STATE_KEY)
  state.remove(PENDING_DISCOVERY_CANDIDATES_STATE_KEY)
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

// =============================================================================
// RINCON identity and discovery candidates
// =============================================================================

/**
 * Parse RINCON_&lt;12 hex MAC&gt;0&lt;4 digit UPnP port&gt;. Returns rincon, mac (upper case) and upnpPort,
 * or null when the value is not that shape. The shape is parsed generally; which ports SAC provisions
 * is a separate policy (SUPPORTED_UPNP_PORTS). Keep the original RINCON as the player identity and never
 * rebuild it from the MAC.
 */
@CompileStatic
Map<String, Object> parseRincon(String rincon) {
  String value = rincon?.trim()
  if(!value || !value.matches(RINCON_PATTERN)) { return null }
  Map<String, Object> parsed = new LinkedHashMap<String, Object>()
  parsed.put('rincon', value)
  parsed.put('mac', value.substring(7, 19).toUpperCase(Locale.ROOT))
  parsed.put('upnpPort', Integer.valueOf(value.substring(20)))
  return parsed
}

@CompileStatic
Boolean isSupportedUpnpPort(Integer upnpPort) {
  return upnpPort != null && SUPPORTED_UPNP_PORTS.contains(upnpPort)
}

/**
 * Discovery and DNI key. The host (1400) player keeps the bare MAC so existing DNIs and saved selections
 * stay valid. Other zones on the same hardware share that MAC, so their key is reserved as MAC-port.
 */
@CompileStatic
String buildPlayerKey(String mac, Integer upnpPort) {
  return upnpPort == HOST_UPNP_PORT ? mac : "${mac}-${upnpPort}".toString()
}

/** RINCON of the host (1400) player on the hardware that owns this MAC. */
@CompileStatic
String buildHostRincon(String mac) {
  return "RINCON_${mac}0${HOST_UPNP_PORT}".toString()
}

Boolean isKnownDiscoveryIp(String ipAddress) {
  Boolean primary = discoveredSonoses.values().any { Object raw -> raw instanceof Map && ((Map)raw).deviceIp?.toString() == ipAddress }
  Boolean secondary = discoveredSonosSecondaries.values().any { Object raw -> raw instanceof Map && ((Map)raw).deviceIp?.toString() == ipAddress }
  return primary || secondary
}

Boolean reserveDiscoveryProbe(String endpoint) {
  if(!endpoint) {
    logDebug('Skipping discovery probe because the candidate endpoint is empty')
    return false
  }
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Skipping discovery probe for ${endpoint}; discovery session is stopped")
    return false
  }
  synchronized(this) {
    Map attempts = (atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] ?: [:]) as Map
    // Keep every endpoint reserved for the full discovery session, including failures.
    // A new session resets this map in startDiscoverySession() and ssdpDiscover().
    if(attempts.containsKey(endpoint)) {
      logDebug("Skipping discovery probe for ${endpoint}; already attempted this session at ${attempts[endpoint]}")
      return false
    }
    Map updated = new LinkedHashMap(attempts)
    updated[endpoint] = now()
    atomicState[DISCOVERY_PROBE_ATTEMPTS_KEY] = updated
    logDebug("Reserved ${endpoint} for a topology request; ${updated.size()} unique endpoint(s) attempted this session")
    return true
  }
}

/**
 * An mDNS or SSDP hit is only a hint that a Sonos speaker lives at this endpoint. One ZoneGroupTopology
 * request to it lists the whole household, so a hit is ignored when its IP is already known, when
 * another topology request is in flight, or when it is not the host (1400) endpoint.
 */
void considerDiscoveryCandidate(String ipAddress, Integer upnpPort) {
  String host = ipAddress?.trim()
  if(!host) { return }
  logTrace("Considering discovery candidate at ${host}:${upnpPort}")
  if(atomicState[DISCOVERY_RUNNING_KEY] != true) {
    logDebug("Skipping discovery candidate ${host}:${upnpPort}; discovery session is stopped")
    return
  }
  if(upnpPort != HOST_UPNP_PORT) {
    logDebug("Ignoring discovery candidate ${host}:${upnpPort}; only the ${HOST_UPNP_PORT} endpoint serves topology")
    return
  }
  if(isKnownDiscoveryIp(host)) {
    logDebug("Skipping discovery candidate ${host}; it is already in the discovered results")
    return
  }
  if(hasQuickTopologyRequestInFlight()) {
    // Keep it: if the request in flight fails or does not list this speaker, it is tried next.
    synchronized(this) {
      List<String> pending = getPendingDiscoveryCandidates()
      if(!pending.contains(host)) { pending << host }
      state[PENDING_DISCOVERY_CANDIDATES_STATE_KEY] = pending
    }
    logTrace("Queued discovery candidate ${host}; a topology request is already in flight")
    return
  }
  if(!reserveDiscoveryProbe("${host}:${upnpPort}".toString())) { return }
  requestQuickTopologyRefresh(host)
}

/** mDNS carries no UPnP port (_sonos._tcp advertises the API port), so a hit stands for the host endpoint. */
void processDiscoveredSonosDevice(String ipAddress) {
  considerDiscoveryCandidate(ipAddress, HOST_UPNP_PORT)
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
  Integer ipPort = convertHexToInt(event?.deviceAddress as String)
  considerDiscoveryCandidate(ipAddress, ipPort)
}

// =============================================================================
// Household topology: one request lists every player
// =============================================================================

Map getQuickTopologyRequests() {
  Object raw = state[QUICK_TOPOLOGY_REQUESTS_STATE_KEY]
  return raw instanceof Map ? new LinkedHashMap((Map)raw) : [:]
}

List<String> getPendingDiscoveryCandidates() {
  Object raw = state[PENDING_DISCOVERY_CANDIDATES_STATE_KEY]
  return raw instanceof Collection ? new ArrayList<String>(((Collection)raw).collect { Object host -> host.toString() }) : new ArrayList<String>()
}

/** After a topology request finishes, try the next queued candidate that is still unknown. */
void startNextPendingDiscoveryCandidate() {
  while(!hasQuickTopologyRequestInFlight() && atomicState[DISCOVERY_RUNNING_KEY] == true) {
    String host = null
    synchronized(this) {
      List<String> pending = getPendingDiscoveryCandidates()
      if(pending.isEmpty()) { return }
      host = pending.remove(0)
      if(pending.isEmpty()) { state.remove(PENDING_DISCOVERY_CANDIDATES_STATE_KEY) } else { state[PENDING_DISCOVERY_CANDIDATES_STATE_KEY] = pending }
    }
    if(isKnownDiscoveryIp(host)) { continue }
    if(!reserveDiscoveryProbe("${host}:${HOST_UPNP_PORT}".toString())) { continue }
    requestQuickTopologyRefresh(host)
  }
}

Boolean hasQuickTopologyRequestInFlight() {
  return !getQuickTopologyRequests().isEmpty()
}

/** Ask one known or supplied speaker for the whole household topology. */
void requestQuickTopologyRefresh(String preferredIp = null) {
  if(hasQuickTopologyRequestInFlight()) { return }
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
  Map params = [uri: "http://${speakerIp}:${HOST_UPNP_PORT}/ZoneGroupTopology/Control",
    headers: [SOAPAction: "${service}#GetZoneGroupState"],
    requestContentType: 'text/xml', contentType: 'text/xml', timeout: 5, body: body]
  Map requests = getQuickTopologyRequests()
  requests[requestId] = speakerIp
  state[QUICK_TOPOLOGY_REQUESTS_STATE_KEY] = requests
  try {
    logInfo("Querying discovery topology from ${speakerIp}")
    asynchttpPost('quickTopologyCallback', params,
        [requestId: requestId, sourceIp: speakerIp, sessionId: atomicState.discoverySessionId])
  } catch(Exception e) {
    finishQuickTopologyRequest(requestId)
    logWarn("Could not query Sonos topology from ${speakerIp}: ${e.message}")
    runInMillis(1, 'startNextPendingDiscoveryCandidate', [overwrite: true])
  }
}

void finishQuickTopologyRequest(String requestId) {
  Map requests = getQuickTopologyRequests()
  requests.remove(requestId)
  if(requests.isEmpty()) { state.remove(QUICK_TOPOLOGY_REQUESTS_STATE_KEY) } else { state[QUICK_TOPOLOGY_REQUESTS_STATE_KEY] = requests }
}

void quickTopologyCallback(AsyncResponse response, Map data) {
  String requestId = data?.requestId?.toString()
  if(!requestId || !getQuickTopologyRequests().containsKey(requestId) ||
      data?.sessionId?.toString() != atomicState.discoverySessionId?.toString()) { return }
  finishQuickTopologyRequest(requestId)
  try {
    if(response == null || response.hasError()) {
      logWarn("Sonos topology query failed from ${data?.sourceIp}: status=${response?.status}, error=${response?.getErrorMessage()}")
      return
    }
    try {
      List<Map<String, Object>> members = parseTopologyResponse(response.getData()?.toString())
      Map<String, Integer> counts = applyTopologyPlayers(members)
      logInfo("Sonos topology listed ${members.size()} member(s); discovered ${counts.primaries} player(s) and ${counts.secondaries} secondary device(s)")
      sendFoundSonosEvents([sessionId: atomicState.discoverySessionId])
    } catch(Exception e) {
      logWarn("Could not parse Sonos topology response: ${e.message}")
    }
  } finally {
    startNextPendingDiscoveryCandidate()
  }
}

/**
 * Parse a GetZoneGroupState response into one map per member, satellite and stereo-pair partner. Every
 * member is kept, including ones SAC does not support, so group and coordinator data stays complete.
 */
List<Map<String, Object>> parseTopologyResponse(String body) {
  GPathResult envelope = new XmlSlurper().parseText(body)
  GPathResult responseNode = findXmlDescendantByLocalName(envelope, 'GetZoneGroupStateResponse')
  GPathResult stateNode = findDirectXmlChild(responseNode, 'ZoneGroupState')
  List<GPathResult> groups = []
  collectXmlDescendantsByLocalName(stateNode, 'ZoneGroup', groups)
  if(groups.isEmpty()) {
    String nestedXml = stateNode?.text()?.toString()
    if(nestedXml) { collectXmlDescendantsByLocalName(new XmlSlurper().parseText(nestedXml), 'ZoneGroup', groups) }
  }
  List<Map<String, Object>> players = []
  groups.each { GPathResult group ->
    String groupId = xmlAttribute(group, 'ID')
    String coordinatorId = xmlAttribute(group, 'Coordinator')
    List<Map<String, Object>> groupPlayers = []
    List<GPathResult> members = []
    collectXmlDescendantsByLocalName(group, 'ZoneGroupMember', members)
    members.each { GPathResult member ->
      Map<String, Object> player = buildTopologyPlayer(member, groupId, coordinatorId)
      if(player == null) { return }
      groupPlayers << player
      List<GPathResult> satellites = []
      collectXmlDescendantsByLocalName(member, 'Satellite', satellites)
      satellites.each { GPathResult satellite ->
        Map<String, Object> satellitePlayer = buildTopologyPlayer(satellite, groupId, coordinatorId)
        if(satellitePlayer == null) { return }
        satellitePlayer.secondary = true
        satellitePlayer.zoneKind = 'satellite'
        satellitePlayer.primaryDeviceId = player.rincon
        groupPlayers << satellitePlayer
      }
    }
    // A stereo-pair partner is an invisible member that shares its channel map with the visible one.
    groupPlayers.findAll { Map<String, Object> candidate -> candidate.secondary != true && candidate.invisible == true }.each { Map<String, Object> partner ->
      String channelMap = partner.channelMapSet?.toString()
      Map<String, Object> primary = channelMap ? groupPlayers.find { Map<String, Object> other ->
        other.secondary != true && other.invisible != true && other.channelMapSet?.toString() == channelMap
      } : null
      partner.secondary = true
      partner.zoneKind = 'stereoPair'
      partner.primaryDeviceId = primary?.rincon
    }
    players.addAll(groupPlayers)
  }
  return players
}

Map<String, Object> buildTopologyPlayer(GPathResult node, String groupId, String coordinatorId) {
  String rincon = xmlAttribute(node, 'UUID')?.trim()
  String ip = null
  Integer locationPort = null
  try {
    URI location = new URI(xmlAttribute(node, 'Location'))
    ip = location.host
    locationPort = location.port > 0 ? location.port : null
  } catch(Exception ignored) { }
  if(!rincon || !ip) { return null }
  Map<String, String> topologyInfo = [:]
  QUICK_TOPOLOGY_DEVICE_ATTRIBUTE_NAMES.each { String name ->
    String value = xmlAttribute(node, name)
    if(value != null && !value.isEmpty()) { topologyInfo[name] = value }
  }
  Map<String, Object> parsed = parseRincon(rincon)
  Map<String, Object> player = [rincon: rincon, mac: parsed?.mac, upnpPort: parsed?.upnpPort, ip: ip,
    locationPort: locationPort, name: xmlAttribute(node, 'ZoneName'), swGen: xmlAttribute(node, 'SWGen'),
    secondary: false, zoneKind: 'primary', primaryDeviceId: null, groupId: groupId, coordinatorId: coordinatorId,
    invisible: xmlAttribute(node, 'Invisible') == '1',
    channelMapSet: xmlAttribute(node, 'ChannelMapSet') ?: xmlAttribute(node, 'HTSatChanMapSet'),
    topologyInfo: topologyInfo] as Map<String, Object>
  return player
}

/** Turn parsed topology members into discovery entries, applying the supported-port policy. */
Map<String, Integer> applyTopologyPlayers(List<Map<String, Object>> members) {
  Integer primaries = 0
  Integer secondaries = 0
  members.each { Map<String, Object> player ->
    String rincon = player.rincon?.toString()
    String name = player.name?.toString() ?: rincon
    if(player.upnpPort == null) {
      logWarn("Ignoring Sonos topology member ${name} with unrecognized RINCON ${rincon}")
      return
    }
    Integer upnpPort = player.upnpPort as Integer
    if(!isSupportedUpnpPort(upnpPort)) {
      logInfo("Skipping Sonos player ${name} (${rincon}): UPnP port ${upnpPort} is not supported yet; only ${SUPPORTED_UPNP_PORTS.sort().join(', ')} players are supported")
      return
    }
    if(player.locationPort != null && player.locationPort != upnpPort) {
      logDebug("Topology Location port ${player.locationPort} differs from the RINCON port ${upnpPort} for ${rincon}; using the RINCON port")
    }
    String mac = player.mac.toString()
    String key = buildPlayerKey(mac, upnpPort)
    Map<String, Object> entry = [id: rincon, name: name, deviceIp: player.ip, swGen: player.swGen,
      hostId: buildHostRincon(mac), upnpPort: upnpPort, zoneKind: player.zoneKind, eventTransport: 'upnp',
      groupId: player.groupId, coordinatorId: player.coordinatorId,
      topologyInfo: new LinkedHashMap((Map)player.topologyInfo)] as Map<String, Object>
    String modelName = ((Map)player.topologyInfo).ModelName
    String modelDisplayName = ((Map)player.topologyInfo).ModelDisplayName
    if(modelName) { entry.modelName = modelName }
    if(modelDisplayName) { entry.modelDisplayName = modelDisplayName }
    if(player.secondary == true) {
      Map<String, Object> primary = parseRincon(player.primaryDeviceId?.toString())
      if(primary == null || !isSupportedUpnpPort(primary.upnpPort as Integer)) {
        logDebug("Skipping secondary ${name} (${rincon}); its primary ${player.primaryDeviceId} is missing or unsupported")
        return
      }
      entry.primaryDeviceId = player.primaryDeviceId
      if(!entry.modelDisplayName) { entry.modelDisplayName = player.zoneKind == 'stereoPair' ? 'Stereo pair' : name }
      discoveredSonosSecondaries[key] = new LinkedHashMap(entry)
      secondaries++
      logInfo("Discovered secondary ${entry.modelDisplayName} (${rincon}) for primary ${player.primaryDeviceId}")
      return
    }
    discoveredSonoses[key] = new LinkedHashMap(entry)
    primaries++
    logInfo("Discovered Sonos device: ${name} at ${player.ip}")
  }
  persistDiscoveredSonosSecondaries()
  return [primaries: primaries, secondaries: secondaries]
}

// =============================================================================
// Player creation
// =============================================================================

/**
 * Household topology already holds everything a player needs to be created, so the request goes to the
 * parent as soon as it is built. One request creates one player; the player reads its own capabilities and
 * household once it is running.
 */
void queuePlayerCreation(String key) {
  Object discovered = key ? discoveredSonoses[key] : null
  if(!(discovered instanceof Map)) {
    logWarn("Cannot create a speaker that is not in the current discovery results: ${key}")
    state.playerRequestMessage = 'Speaker creation could not be queued.'
    return
  }
  Map info = (Map)discovered
  String rincon = info.id?.toString()
  Map<String, Map> secondaries = [:]
  discoveredSonosSecondaries.each { Object secondaryKey, Object raw ->
    if(raw instanceof Map && ((Map)raw).primaryDeviceId?.toString() == rincon) { secondaries[secondaryKey.toString()] = (Map)raw }
  }
  Map request = [schemaVersion: 2, requestId: UUID.randomUUID().toString(), dni: key,
      player: copyCandidateInfoMap([(key): info])[key], secondaries: copyCandidateInfoMap(secondaries)]
  Map result = parent.submitPlayerProvisioningRequest(request) as Map
  if(result?.accepted != true) {
    state.playerRequestMessage = 'Speaker creation could not be queued.'
    return
  }
  state.playerRequestMessage = 'Speaker creation has been queued.'
  schedulePlayerTableRefresh([key], [])
}

/**
 * The parent creates and removes player devices on its own schedule after accepting a request, and nothing
 * else tells this app when it finished. Poll its snapshot until the expected devices appear (or disappear),
 * then refresh the speaker table once.
 */
void schedulePlayerTableRefresh(List<String> expectedPresent, List<String> expectedAbsent) {
  state[PENDING_PLAYER_TABLE_REFRESH_KEY] = [present: expectedPresent, absent: expectedAbsent, attempts: 0]
  runIn(2, 'pollPlayerTableRefresh')
}

void pollPlayerTableRefresh() {
  Object raw = state[PENDING_PLAYER_TABLE_REFRESH_KEY]
  if(!(raw instanceof Map)) { return }
  Map pending = (Map)raw
  Set<String> installed = new HashSet<String>()
  Boolean snapshotRead = false
  try {
    Map snapshot = parent.getProvisioningSnapshot() as Map ?: [:]
    installed.addAll(((Map)(snapshot.players ?: [:])).keySet().collect { Object key -> key.toString() })
    snapshotRead = true
  } catch(Exception e) {
    logDebug("Waiting for the parent player snapshot (${e.class.simpleName})")
  }
  // A failed read leaves `installed` empty, which would look like every removal had finished.
  Boolean settled = snapshotRead && ((Collection)pending.present).every { Object dni -> installed.contains(dni.toString()) } &&
      ((Collection)pending.absent).every { Object dni -> !installed.contains(dni.toString()) }
  Integer attempts = (pending.attempts instanceof Number ? (Number)pending.attempts : 0).intValue() + 1
  if(settled || attempts >= PLAYER_TABLE_REFRESH_MAX_ATTEMPTS) {
    state.remove(PENDING_PLAYER_TABLE_REFRESH_KEY)
    if(!settled) { logWarn('The parent did not report the requested player changes in time; refreshing the speaker table anyway') }
    app.sendEvent(name: 'newUiSpeakerTable', value: 'updated')
    return
  }
  state[PENDING_PLAYER_TABLE_REFRESH_KEY] = pending + [attempts: attempts]
  runIn(2, 'pollPlayerTableRefresh')
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
    Map primary = discoveredSonoses.values().find { Object raw -> raw instanceof Map && ((Map)raw).id?.toString() == primaryId } as Map
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
    return renderNewUiGroupViews()
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

