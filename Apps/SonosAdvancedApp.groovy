/**
 *  MIT License
 *  Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
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
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.json.JsonOutput
import groovy.util.slurpersupport.GPathResult
import hubitat.scheduling.AsyncResponse

// =============================================================================
// Library: Utilities and Logging (inlined)
// =============================================================================
void logError(String message) {
  if (settings.logEnable != false) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}
void logWarn(String message) {
  if (settings.logEnable != false) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}
void logInfo(String message) {
  if (settings.logEnable != false) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}
void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}
void logTrace(String message) {
  if (settings.logEnable != false && settings.traceLogEnable != false) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

// Error logging for already-stringified JSON/XML payloads. Call sites in this app
// pass strings (e.g. "Request ErrorJson: ${response.getErrorJson()}"), so only the
// String forms are needed here.
void logErrorJson(String message) { logError(message) }
void logErrorXml(String message) { logError(message) }

Integer convertHexToInt(String hex) { Integer.parseInt(hex,16) }

String convertHexToIP(String hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

@Field static final List<Integer> DEFAULT_HTTP_RETRY_DELAYS_SECONDS = [60, 180, 300]
@Field static final Integer DEFAULT_MAX_HTTP_RETRY_ATTEMPTS = 3

Integer getHttpStatusCode(AsyncResponse response) {
  if (response == null) return null
  def statusObj = response.status
  if (statusObj == null) return null

  if (statusObj instanceof Number) {
    return (statusObj as Number).intValue()
  }

  try {
    String statusText = statusObj.toString()
    // Try direct integer conversion first
    try {
      return statusText.toInteger()
    } catch (Exception ignored) {
      // Fallback: extract first 3-digit HTTP status code from text like "200 OK"
      def matcher = (statusText =~ /(\d{3})/)
      if (matcher.find()) {
        return matcher.group(1).toInteger()
      }
      return null
    }
  } catch (Exception e) {
    return null
  }
}

void resetHttpRetryCounter(String stateKey = 'httpRetryAttemptCount') {
  state[stateKey] = 0
  logDebug "HTTP retry counter reset (${stateKey})"
}

Boolean handleAsyncHttpFailureWithRetry(
  AsyncResponse response,
  String retryMethodName,
  String stateKey = 'httpRetryAttemptCount',
  List<Integer> retryDelays = DEFAULT_HTTP_RETRY_DELAYS_SECONDS,
  Integer maxRetries = DEFAULT_MAX_HTTP_RETRY_ATTEMPTS,
  String customErrorMessage = null
) {
  // Get the current retry attempt count
  Integer currentRetryCount = state[stateKey] ?: 0

  // Log the specific error with attempt information
  Integer statusCode = getHttpStatusCode(response)
  String errorDetails = customErrorMessage ?: (response?.hasError() ?
    "HTTP request error: ${response.getErrorMessage()}" :
    (statusCode != null ? "HTTP request returned status ${statusCode} (expected 200 OK)" :
      "HTTP request returned no status code"))
  logError "${errorDetails} (attempt ${currentRetryCount + 1} of ${maxRetries + 1})"

  // Check if we have retries remaining
  if (currentRetryCount < maxRetries) {
    // Get the delay for this retry attempt (0-indexed)
    Integer retryDelaySeconds = retryDelays[currentRetryCount]

    // Increment the retry counter for the next attempt
    state[stateKey] = currentRetryCount + 1

    // Calculate human-readable delay for logging
    String delayDescription = retryDelaySeconds >= 60 ?
        "${retryDelaySeconds / 60} minute(s)" :
        "${retryDelaySeconds} second(s)"

    logWarn "Scheduling retry attempt ${currentRetryCount + 1} of ${maxRetries} in ${delayDescription}"

    // Schedule the retry attempt
    runIn(retryDelaySeconds, retryMethodName)
    return true
  } else {
    // All retries exhausted
    logError "All ${maxRetries} retry attempts failed. Will retry at next scheduled refresh."
    state[stateKey] = 0  // Reset for next scheduled refresh
    return false
  }
}


definition(
  name: 'Sonos Advanced Controller',
  version: '0.11.7',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'Audio',
  description: 'Sonos speaker integration with advanced functionality, such as non-interrupting announcements and grouping control',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: false,
  iconX3Url: '',
  singleThreaded: true,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/SonosAdvancedApp.groovy'
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
  page(name: 'newUiPage')
  page(name: 'localPlayerPage')
  page(name: 'localPlayerSelectionPage')
  page(name: 'groupPage')
}

// =============================================================================
// Fields
// =============================================================================
@Field static Map discoveredSonoses = new java.util.concurrent.ConcurrentHashMap()
@Field static Map discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap()
@Field static final String DISCOVERED_SONOS_SECONDARIES_STATE_KEY = 'discoveredSonosSecondaries'
@Field static final String LOCAL_CONTROL_RETRY_STATE_KEY = 'localControlRetryAttemptCount'
@Field static final String LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY = 'lastLocalControlRetryRequest'
@Field static final String LOCAL_CONTROL_RETRY_DATA_KEY = '_localControlRetryRequest'
// Hub login sessions expire; re-login when the cached cookie is older than this.
@Field static final Long HUB_COOKIE_MAX_AGE_MS = 10L * 60L * 1000L
// Per-child debounce for GROUP_STATUS_MOVED driven ZGT resubscribes. Burst 499
// responses during group transitions would otherwise fire dozens of subscribes
// on the same device back to back.
@Field static java.util.concurrent.ConcurrentHashMap<String, Long> zgtResubRequestAt = new java.util.concurrent.ConcurrentHashMap<String, Long>()
@Field static final Long ZGT_RESUB_REQUEST_MIN_INTERVAL_MS = 60000L
@Field static final String GROUP_COMMAND_REQUEST_ATTRIBUTE = 'groupCommandRequest'
@Field static final String PLAYER_COMMAND_REQUEST_ATTRIBUTE = 'playerCommandRequest'
@Field static final String GROUP_FAVORITE_OPERATION_ATTRIBUTE = 'groupFavoriteOperation'
@Field static final String GROUP_OPERATION_STATE_KEY = 'activeGroupFavoriteOperation'
@Field static final String GROUPING_CONTEXTS_STATE_KEY = 'groupingContexts'
@Field static final String GROUP_COMMAND_QUEUE_STATE_KEY = 'pendingGroupCommandRequests'
@Field static final String DEFERRED_GROUP_COMMANDS_STATE_KEY = 'deferredGroupOperationCommands'
@Field static final String GROUPING_MODE_EXPLICIT = 'EXPLICIT'
@Field static final String GROUPING_MODE_ADDITIVE = 'ADDITIVE'
@Field static final String GROUPING_MODE_CURRENT = 'CURRENT'
@Field static final Integer GROUP_OPERATION_POLL_SECONDS = 1
@Field static final Integer GROUP_OPERATION_DEADLINE_SECONDS = 60
@Field static final Integer GROUPING_CONTEXT_TTL_SECONDS = 15
@Field static final Integer GROUP_OPERATION_STABILITY_OBSERVATIONS = 2
@Field static final Integer GROUP_OPERATION_MAX_TOPOLOGY_ATTEMPTS = 4
@Field static final Integer GROUP_OPERATION_COMMAND_WAIT_SECONDS = 3
@Field static final Integer GROUP_OPERATION_MAX_FAVORITE_ATTEMPTS = 2
@Field static final Integer GROUP_OPERATION_MAX_PLAY_ATTEMPTS = 1
@Field static final Integer GROUP_OPERATION_MAX_FAVORITE_TOPOLOGY_ATTEMPTS = 3
@Field static final String PENDING_GROUP_STATE_QUEUE_KEY = 'pendingGroupStateUpdates'
@Field static final String PENDING_CHILD_EVENT_QUEUE_KEY = 'pendingChildEvents'
@Field static final String GROUP_STATE_DRAIN_ATTEMPTS_KEY = 'groupStateDrainAttempts'
@Field static final Integer GROUP_STATE_DRAIN_MAX_ATTEMPTS = 3
@Field static final String NEW_UI_GROUP_EDITOR_MODE_KEY = 'newUiGroupEditorMode'
@Field static final String NEW_UI_GROUP_ORIGINAL_NAME_KEY = 'newUiGroupOriginalName'
@Field static final String NEW_UI_GROUP_DRAFT_KEY = 'newUiGroupDraft'
@Field static final String NEW_UI_GROUP_AUTO_NAME_KEY = 'newUiGroupAutoName'
@Field static final String NEW_UI_GROUP_ERROR_KEY = 'newUiGroupError'
@Field static final String NEW_UI_GROUP_DELETE_KEY = 'pendingNewUiDeleteGroup'
@Field static final String NEW_UI_GROUP_TABLE_EVENT = 'newUiGroupsTable'
@Field static final String NEW_UI_GROUP_MODE_CREATE = 'create'
@Field static final String NEW_UI_GROUP_MODE_EDIT = 'edit'
@Field static Map SOURCES = [
  "\$": "None",
  "x-file-cifs:": "Library",
  "x-rincon-mp3radio:": "Radio",
  "x-sonosapi-stream:": "Radio",
  "x-sonosapi-radio:": "Radio",
  "x-sonosapi-hls:": "Radio",
  "x-sonos-http:sonos": "Radio",
  "aac:": "Radio",
  "hls-radio:": "Radio",
  "https?:": "File",
  "x-rincon-stream:": "LineIn",
  "x-sonos-htastream:": "TV",
  "x-sonos-vli:.*,airplay:": "Airplay",
  "x-sonos-vli:.*,spotify:": "Spotify",
  "x-rincon-queue": "Sonos Q"
]
// =============================================================================
// End Fields
// =============================================================================


// =============================================================================
// Getters and Setters
// =============================================================================
@CompileStatic
String getLocalApiPrefix(String ipAddress) {
  return "https://${ipAddress}/api/v1"
}

String getLocalUpnpHostForCoordinatorId(String groupCoordinatorId) {
  ChildDeviceWrapper dev = getDeviceFromRincon(groupCoordinatorId)
  return dev?.getDataValue('localUpnpHost')
}


// =============================================================================
// End Getters and Setters
// =============================================================================

// =============================================================================
// App Pages
// =============================================================================
Map mainPage() {
  if(settings.useOldUi == true) {
    stopDiscovery()
  }
  checkForUpdates()
  dynamicPage(title: 'Sonos Advanced Controller') {
    section {
      paragraph 'This application provides Advanced Sonos Player control, including announcements and grouping.'

      if(state.updateAvailable == true) {
        // Validate that the "available" version is actually newer than what's installed
        // This handles the case where the user updated via HPM or manual paste, bypassing installUpdate()
        String currentVer = getActualInstalledAppVersion()
        if(state.latestVersion && compareVersions(currentVer, state.latestVersion as String) < 0) {
          section('<b style="color: #ff6b6b;">⚠️ Update Available</b>', hideable: false) {
            paragraph "<b>Version ${state.latestVersion} is available!</b><br/>" +
                      "Current version: ${currentVer}<br/>" +
                      "Released: ${state.latestReleaseDate}<br/><br/>" +
                      "<a href='${state.latestReleaseUrl}' target='_blank'>View Release Notes</a>"
            input 'btnInstallUpdate', 'button', title: 'Install Update', submitOnChange: false
            input 'btnDismissUpdate', 'button', title: 'Dismiss', submitOnChange: false
          }
        } else {
          // Stale update notification — installed version is already current or newer
          state.updateAvailable = false
          state.latestVersion = null
          state.latestManifest = null
          state.latestReleaseDate = null
          state.latestReleaseUrl = null
          app.updateLabel('Sonos Advanced Controller')
        }
      }
    }

    if(settings.useOldUi == true) {
      section() {
        href (
          page: 'localPlayerPage',
          title: 'Sonos Virtual Player Devices',
          description: 'Select to create Sonos player devices using local discovery'
        )
        href (
          page: 'groupPage',
          title: 'Sonos Virtual Group Devices',
          description: 'Select to create/delete Sonos group devices'
        )
      }
    } else {
      renderNewUiPageContent()
    }

    section('Update Settings:', hideable: true, hidden: true) {
      paragraph "<span style='color: #ff6b6b;'><b>⚠ Warning:</b> The built-in auto-update functionality should not be used if you manage this app through Hubitat Package Manager (HPM). Using both may cause conflicts, or version mismatches. If you installed via HPM, use HPM to manage updates.</span>"
      input 'autoCheckUpdates', 'bool', title: 'Automatically check for updates', required: false, defaultValue: true, submitOnChange: true
      if(autoCheckUpdates) {
        input 'updateCheckFrequency', 'enum', title: 'Check frequency', required: false, defaultValue: 'Daily',
              options: ['Daily', 'Weekly', 'Manual']
      }
      input 'autoInstallUpdates', 'bool', title: 'Automatically install updates', required: false, defaultValue: false, submitOnChange: true
      if(autoInstallUpdates) {
        input 'autoInstallTime', 'time', title: 'Install updates at this time', required: true, defaultValue: '02:00'
        input 'autoInstallNextOccurrence', 'bool', title: 'Install at next occurrence of selected time (regardless of day)', required: false, defaultValue: false, submitOnChange: true
        if(!autoInstallNextOccurrence) {
          input 'autoInstallDayOfWeek', 'enum', title: 'Install on this day of week', required: false, defaultValue: 'Sunday',
                options: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
        }
      }
      input 'btnCheckForUpdates', 'button', title: 'Check for Updates Now', submitOnChange: false
      if(state.lastUpdateCheckFormatted) {
        paragraph "Last checked: ${state.lastUpdateCheckFormatted}"
      }

      input 'btnCheckInstalledVersions', 'button', title: 'Check Versions of Installed Files', submitOnChange: false
      if(state.lastVersionCheck) {
        paragraph "Last version check: ${new Date(state.lastVersionCheck).format('yyyy-MM-dd HH:mm:ss')}"
      }

      // Show message if update was just completed
      if(state.updateJustCompleted) {
        paragraph "<span style='color:green'><b>✓ Update completed successfully!</b></span><br><i>Click 'Check Versions of Installed Files' to verify all files are at the correct version.</i>"
        state.remove('updateJustCompleted')
      }

      // Display installed file versions
      if(state.installedVersions && state.installedVersions.size() > 0) {
        paragraph "<b>Installed File Versions:</b>"
        StringBuilder versionList = new StringBuilder('<ul>')
        state.installedVersions.each { file ->
          String statusColor = file.status == 'OK' ? 'green' : (file.status == 'Mismatch' ? 'red' : 'gray')
          String statusIcon = file.status == 'OK' ? '✓' : (file.status == 'Mismatch' ? '✗' : '?')
          versionList.append("<li><span style='color:${statusColor}'><b>${statusIcon}</b></span> <b>${file.name}</b>: ${file.installedVersion}")
          if(file.status == 'Mismatch') {
            versionList.append(" <span style='color:red'>(expected ${file.expectedVersion})</span>")
          }
          versionList.append('</li>')
        }
        versionList.append('</ul>')
        paragraph versionList.toString()
      }

      if(state.versionMismatches && state.versionMismatches.size() > 0) {
        paragraph "<span style='color:red'><b>⚠ ${state.versionMismatches.size()} Version Mismatch(es) Found</b></span>"
        input 'btnFixVersionMismatches', 'button', title: 'Update All Mismatched Versions', submitOnChange: false
      } else if(state.lastVersionCheck && state.versionMismatches != null) {
        paragraph "<span style='color:green'><b>✓ All installed files are at the correct version</b></span>"
      }

      // Duplicate cleanup section
      paragraph "<hr><b>Maintenance</b>"
      input 'btnCleanupDuplicates', 'button', title: 'Check and Remove Duplicate Apps/Drivers', submitOnChange: false
      if(state.duplicateCleanupResult) {
        paragraph "<i>Last cleanup result: ${state.duplicateCleanupResult}</i>"
      }

      // Library management section
      paragraph "<hr><b>Library Management</b>"
      paragraph "<i>Enter the library IDs from your hub's Library Code section to enable library publishing. Find these by going to Apps Code → Libraries in the Hubitat interface.</i>"
      input 'libraryIdSMAPI', 'number', title: 'SMAPILibrary ID', required: false, submitOnChange: true
      input 'libraryIdUtilities', 'number', title: 'UtilitiesAndLoggingLibrary ID', required: false, submitOnChange: true

      if(settings.libraryIdSMAPI && settings.libraryIdUtilities) {
        input 'btnPublishLibraries', 'button', title: 'Publish Libraries to Hub', submitOnChange: false
        if(state.lastLibraryPublish) {
          paragraph "<i>Last publish: ${state.lastLibraryPublish}</i>"
        }
      } else {
        paragraph "<i>⚠ Enter both library IDs above to enable library publishing.</i>"
      }
    }

    section('Logging Settings:', hideable: true) {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'traceLogEnable', 'bool', title: 'Enable trace logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      input 'useOldUi', 'bool', title: 'Use old UI', required: false, defaultValue: false, submitOnChange: true
      // input 'applySettingsButton', 'button', title: 'Apply Settings'
    }
  }
}

Map newUiPage() {
  dynamicPage(
    name: 'newUiPage',
    title: 'Player Devices',
    nextPage: 'mainPage',
    install: false,
    uninstall: false,
    refreshInterval: 0
  ) {
    renderNewUiPageContent()
  }
}

void renderNewUiPageContent() {
  section() {
    paragraph "<span class='ssr-app-state-${app.id}-discoveryTimer'>${renderNewUiDiscoveryTimerMarkup()}</span>"
    input 'btnNewUiDiscoverSpeakers', 'button', title: 'Discover Speakers (60 seconds)', submitOnChange: true
    paragraph displayNewUiSpeakerTable()
  }

  if(state.pendingNewUiDeletePlayer) {
    String pendingPlayerKey = state.pendingNewUiDeletePlayer as String
    Map pendingPlayer = buildNewUiSpeakerRows().find { Map row ->
      String rowKey = row.discoveryKey?.toString() ?: row.id?.toString()
      rowKey == pendingPlayerKey
    }
    String pendingPlayerName = pendingPlayer?.name?.toString() ?: pendingPlayerKey
    String safePendingPlayerName = escapeNewUiHtml(pendingPlayerName)
    String safePendingPlayerKey = escapeNewUiHtml(pendingPlayerKey)
    section() {
      paragraph "<b style='color:#F44336'>Are you sure you want to remove '${safePendingPlayerName}' (${safePendingPlayerKey})?</b>"
      input 'btnConfirmNewUiDeletePlayer', 'button', title: 'Yes, Remove Speaker', submitOnChange: true
      input 'btnCancelNewUiDeletePlayer', 'button', title: 'Cancel', submitOnChange: true
    }
  }

  if(state[NEW_UI_GROUP_DELETE_KEY]) {
    Map pendingGroup = findNewUiGroupByToken(state[NEW_UI_GROUP_DELETE_KEY] as String)
    String pendingGroupName = pendingGroup?.name?.toString() ?: state[NEW_UI_GROUP_DELETE_KEY].toString()
    String safePendingGroupName = escapeNewUiHtml(pendingGroupName)
    section() {
      paragraph "<b style='color:#F44336'>Are you sure you want to remove the Sonos group '${safePendingGroupName}'?</b>"
      input 'btnConfirmNewUiDeleteGroup', 'button', title: 'Yes, Remove Group', submitOnChange: true
      input 'btnCancelNewUiDeleteGroup', 'button', title: 'Cancel', submitOnChange: true
    }
  }

  section() {
    paragraph "<div class='new-ui-group-section-title'>Sonos Groups</div>"
    if(state[NEW_UI_GROUP_EDITOR_MODE_KEY]) {
      Map groupDraft = getNewUiGroupDraft()
      List<ChildDeviceWrapper> players = getCurrentPlayerDevices()
      Map<String, String> playerOptions = players.collectEntries { ChildDeviceWrapper player ->
        String id = player.getDataValue('id')?.toString()
        id ? [(id): (player.getDataValue('name')?.toString() ?: id)] : [:]
      }
      String selectedCoordinator = getNewUiGroupCoordinator(groupDraft)
      List<String> selectedFollowers = getNewUiGroupFollowers(groupDraft)
      Map<String, String> playerSwGenMap = players.collectEntries { ChildDeviceWrapper player ->
        String id = player.getDataValue('id')?.toString()
        id ? [(id): player.getDataValue('swGen')?.toString()] : [:]
      }
      String coordinatorSwGen = playerSwGenMap[selectedCoordinator]
      Map<String, String> followerOptions = playerOptions.findAll { String id, String ignored ->
        id != selectedCoordinator && (!coordinatorSwGen || !playerSwGenMap[id] || playerSwGenMap[id] == coordinatorSwGen || selectedFollowers.contains(id))
      }
      String editorMode = state[NEW_UI_GROUP_EDITOR_MODE_KEY] as String
      String editorTitle = editorMode == NEW_UI_GROUP_MODE_EDIT ? 'Edit Sonos Group' : 'Create Sonos Group'
      String editorName = synchronizeNewUiGroupName(groupDraft, selectedCoordinator, selectedFollowers, playerOptions)

      paragraph "<h3>${editorTitle}</h3>"
      if(state[NEW_UI_GROUP_ERROR_KEY]) {
        paragraph "<b style='color:#F44336'>${escapeNewUiHtml(state[NEW_UI_GROUP_ERROR_KEY])}</b>"
      }
      if(selectedCoordinator) {
        paragraph 'Followers cannot include the selected coordinator. Only compatible speakers are listed.'
      } else {
        paragraph 'Select one coordinator, then select one or more follower speakers.'
      }
      input name: 'newUiGroupName', type: 'text', title: 'Group Name:', required: false,
          defaultValue: editorName, submitOnChange: true
      input name: 'newUiGroupCoordinator', type: 'enum', title: 'Coordinator:', multiple: false,
          options: playerOptions, required: false, defaultValue: selectedCoordinator, submitOnChange: true, offerAll: false
      input name: 'newUiGroupFollowers', type: 'enum', title: 'Followers:', multiple: true,
          options: followerOptions, required: false, defaultValue: selectedFollowers, submitOnChange: true, offerAll: false
      input name: 'btnNewUiSaveGroup', type: 'button', title: 'Save Group', submitOnChange: true
      input name: 'btnNewUiCancelGroup', type: 'button', title: 'Cancel', submitOnChange: true
    }

    paragraph displayNewUiGroupTable()
    input 'btnNewUiCreateGroup', 'button', title: 'Create Group', submitOnChange: true
  }
}

void startDiscoverySession() {
  if(atomicState.discoveryRunning != true) {
    subscribeToSsdpEvents(location)
    ssdpDiscover()
    atomicState.discoveryRunning = true
    atomicState.discoveryEndTime = now() + 60000
    app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
    runIn(60, 'stopDiscovery')
    runIn(1, 'updateDiscoveryTimer')
  }
}

String displayNewUiSpeakerTable() {
  String css = """
    <style>
      #new-ui-speaker-table-wrapper {
        display: block;
        overflow-x: auto;
        margin: -64px 0 0 0 !important;
        padding: 0 !important;
        width: 100%;
      }

      /* Keep only the group editor's action buttons on one row. */
      .form-group:has(input[name='btnNewUiSaveGroup']),
      .form-group:has(input[name='btnNewUiCancelGroup']) {
        display: inline-block !important;
        width: auto !important;
        margin: 8px 8px 0 0 !important;
        vertical-align: top;
      }

      .mdl-cell:has(input[name='btnNewUiSaveGroup']),
      .mdl-cell:has(input[name='btnNewUiCancelGroup']),
      .mdl-cell:has(button[name='btnNewUiSaveGroup']),
      .mdl-cell:has(button[name='btnNewUiCancelGroup']) {
        display: inline-block !important;
        width: auto !important;
        margin: 8px 8px 0 0 !important;
        vertical-align: top;
      }

      input[name='btnNewUiSaveGroup'],
      input[name='btnNewUiCancelGroup'],
      button[name='btnNewUiSaveGroup'],
      button[name='btnNewUiCancelGroup'] {
        display: inline-block !important;
        width: auto !important;
      }
      #new-ui-speaker-table {
        border: 1px solid #E0E0E0;
        border-collapse: collapse;
        border-radius: 4px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        min-width: 720px;
        width: 100%;
      }
      #new-ui-speaker-table th,
      #new-ui-speaker-table td {
        border-bottom: 1px solid #EEEEEE;
        border-right: 1px solid #EEEEEE;
        font-size: 14px !important;
        padding: 6px 4px !important;
        text-align: center;
      }
      #new-ui-speaker-table thead {
        background-color: #F5F5F5;
      }
      #new-ui-speaker-table th {
        border-bottom: 2px solid #E0E0E0;
        color: #424242;
        font-size: 14px !important;
        font-weight: 500;
        padding: 8px !important;
        text-align: center;
        white-space: nowrap;
      }
      #new-ui-speaker-table tbody tr:hover {
        background-color: inherit !important;
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
        color: #666666;
        font-size: 11px;
        margin-top: 3px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-speakers {
        min-width: 190px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-count {
        color: #424242;
        font-size: 11px;
        font-weight: 600;
        margin-bottom: 3px;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-entry {
        color: #424242;
        font-size: 12px;
        line-height: 1.25;
        text-align: left;
      }
      #new-ui-speaker-table .new-ui-secondary-entry + .new-ui-secondary-entry {
        border-top: 1px solid #EEEEEE;
        margin-top: 4px;
        padding-top: 4px;
      }
      #new-ui-speaker-table .new-ui-secondary-detail {
        color: #757575;
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
        color: #757575;
      }
      #new-ui-speaker-table .new-ui-action-cell {
        padding-left: 4px !important;
        padding-right: 4px !important;
        width: 56px;
      }
      #new-ui-speaker-table .new-ui-group-action-cell {
        padding-left: 4px !important;
        padding-right: 4px !important;
        width: 92px;
      }
    </style>
  """
  String iconifyScript = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
  return "${css}${iconifyScript}<span class='ssr-app-state-${app.id}-newUiSpeakerTable'><div id='new-ui-speaker-table-wrapper'>${renderNewUiSpeakerTableMarkup()}</div></span>"
}

String renderNewUiSpeakerTableMarkup() {
  List<Map> rows = buildNewUiSpeakerRows()
  StringBuilder html = new StringBuilder()
  html.append("<div style='overflow-x:auto'><table id='new-ui-speaker-table' class='mdl-data-table'>")
  html.append('<thead><tr>')
  html.append('<th>Action</th>')
  html.append('<th>Speaker</th>')
  html.append('<th>Secondary Speakers</th>')
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
        safeName = "<a href='/device/edit/${escapeNewUiHtml(childDeviceId)}' target='_blank'>${safeName}</a>"
      }
      String detail = [safeModel, safeId]
          .findAll { String value -> value }
          .join(' &middot; ')

      html.append('<tr>')
      html.append("<td class='new-ui-action-cell'>${renderNewUiCreatedStatus(row)}</td>")
      html.append("<td class='new-ui-speaker-name'><strong>${safeName}</strong>")
      if(detail) {
        html.append("<div class='new-ui-speaker-detail'>${detail}</div>")
      }
      html.append('</td>')
      html.append("<td class='new-ui-secondary-speakers'>${renderNewUiSecondaryDevices(row)}</td>")
      html.append("<td>${safeIp}</td>")
      html.append("<td class='new-ui-group-action-cell'>${renderNewUiCreateGroupStatus(row)}</td>")
      html.append('</tr>')
    }
  }

  html.append('</tbody></table></div>')
  return html.toString()
}

List<Map> buildNewUiSpeakerRows() {
  restoreDiscoveredSonosSecondaries()
  LinkedHashMap<String, Map> rowsById = new LinkedHashMap<String, Map>()

  discoveredSonoses.each { Object discoveryKey, Object rawInfo ->
    Map info = rawInfo instanceof Map ? (Map)rawInfo : [:]
    String discoveryId = discoveryKey?.toString()
    String playerId = info.id?.toString() ?: discoveryId
    if(playerId) {
      rowsById[playerId] = [
        id: playerId,
        discoveryKey: discoveryId,
        name: info.name?.toString() ?: playerId,
        model: (info.modelDisplayName ?: info.modelName)?.toString(),
        ip: (info.deviceIp ?: info.ipAddress)?.toString(),
        secondaryDevices: getNewUiSecondaryDevices(playerId),
        created: false,
        player: null
      ]
    }
  }

  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    String playerId = player.getDataValue('id')?.toString() ?: player.getDeviceNetworkId()?.toString()
    if(playerId) {
      Map row = rowsById[playerId] ?: [
        id: playerId,
        discoveryKey: player.getDeviceNetworkId()?.toString(),
        name: playerId,
        model: null,
        ip: null,
        secondaryDevices: getNewUiSecondaryDevices(playerId),
        created: false,
        player: null
      ]
      row.created = true
      row.player = player
      row.name = player.getDataValue('name')?.toString() ?: player.label?.toString() ?: row.name
      row.model = (player.getDataValue('modelDisplayName') ?: player.getDataValue('modelName'))?.toString() ?: row.model
      row.ip = (player.getDataValue('deviceIp') ?: player.getDataValue('localUpnpHost'))?.toString() ?: row.ip
      row.discoveryKey = row.discoveryKey ?: player.getDeviceNetworkId()?.toString()
      row.secondaryDevices = getNewUiSecondaryDevices(playerId)
      rowsById[playerId] = row
    }
  }

  List<Map> rows = new ArrayList<Map>(rowsById.values())
  rows.sort { Map left, Map right ->
    left.name.toString().compareToIgnoreCase(right.name.toString())
  }
  return rows
}

List<Map> getNewUiSecondaryDevices(String primaryPlayerId) {
  List<Map> secondaries = []
  discoveredSonosSecondaries.each { Object ignored, Object rawInfo ->
    Map info = rawInfo instanceof Map ? (Map)rawInfo : [:]
    if(info.primaryDeviceId?.toString() == primaryPlayerId) {
      secondaries.add(info)
    }
  }
  secondaries.sort { Map left, Map right ->
    String leftName = (left.modelDisplayName ?: left.modelName ?: left.id ?: '').toString()
    String rightName = (right.modelDisplayName ?: right.modelName ?: right.id ?: '').toString()
    leftName.compareToIgnoreCase(rightName)
  }
  return secondaries
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
      if(grouped != null && grouped.toString().toLowerCase() in ['off', 'false']) {
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
    logTrace("Could not read ${sid} status for ${row.id}: ${e.message}")
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
    logTrace("Could not read WebSocket status for ${row.id}: ${e.message}")
  }

  String normalized = status?.toString()?.trim()?.toLowerCase()
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

String newUiButtonLink(String buttonName, String linkText, String color = '#1A77C9', String font = '15px') {
  return "<div class='form-group'><input type='hidden' name='${buttonName}.type' value='button'></div>" +
      "<div style='display:inline-block'><div class='submitOnChange' onclick='buttonClick(this)' style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
      "<input type='hidden' name='settings[${buttonName}]' value=''>"
}

String newUiStatusBadge(String label, String statusClass, String title) {
  String safeLabel = escapeNewUiHtml(label)
  String safeTitle = escapeNewUiHtml(title)
  return "<span class='new-ui-status new-ui-status-${statusClass}' title='${safeTitle}'>${safeLabel}</span>"
}

String escapeNewUiHtml(Object value) {
  String text = value?.toString() ?: ''
  return text.replace('&', '&amp;')
      .replace('<', '&lt;')
      .replace('>', '&gt;')
      .replace('"', '&quot;')
      .replace("'", '&#39;')
}

String displayNewUiGroupTable() {
  String css = """
    <style>
      #new-ui-group-table-wrapper {
        display: block;
        overflow-x: auto;
        margin: 0 !important;
        padding: 0 !important;
        width: 100%;
      }
      .new-ui-group-table-content {
        display: block;
        margin: -56px 0 0 0 !important;
        padding: 0 !important;
      }
      .new-ui-group-section-title {
        color: #424242;
        font-size: 18px !important;
        font-weight: 400;
        line-height: 1.2;
        margin: 0 0 12px !important;
        padding: 0 !important;
      }
      #new-ui-group-table {
        border: 1px solid #E0E0E0;
        border-collapse: collapse;
        border-radius: 4px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        min-width: 760px;
        width: 100%;
      }
      #new-ui-group-table th,
      #new-ui-group-table td {
        border-bottom: 1px solid #EEEEEE;
        border-right: 1px solid #EEEEEE;
        font-size: 14px !important;
        padding: 8px 6px !important;
        text-align: center;
        vertical-align: middle;
      }
      #new-ui-group-table thead {
        background-color: #F5F5F5;
      }
      #new-ui-group-table th {
        border-bottom: 2px solid #E0E0E0;
        color: #424242;
        font-size: 14px !important;
        font-weight: 500;
        white-space: nowrap;
      }
      #new-ui-group-table tbody tr:hover {
        background-color: inherit !important;
      }
      #new-ui-group-table td:first-child .form-group {
        display: none;
      }
      #new-ui-group-table td:first-child .submitOnChange {
        line-height: 20px;
        vertical-align: middle;
      }
      #new-ui-group-table .new-ui-group-action-cell {
        padding-left: 4px !important;
        padding-right: 4px !important;
        white-space: nowrap;
        width: 92px;
      }
      #new-ui-group-table .new-ui-group-name,
      #new-ui-group-table .new-ui-group-followers {
        text-align: left;
      }
      #new-ui-group-table .new-ui-group-name {
        min-width: 180px;
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
    </style>
  """
  String iconifyScript = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
  return "${css}${iconifyScript}<div class='new-ui-group-table-content'><span class='ssr-app-state-${app.id}-${NEW_UI_GROUP_TABLE_EVENT}'><div id='new-ui-group-table-wrapper'>${renderNewUiGroupTableMarkup()}</div></span></div>"
}

String renderNewUiGroupTableMarkup() {
  List<Map> rows = buildNewUiGroupRows()
  StringBuilder html = new StringBuilder()
  html.append("<div style='overflow-x:auto'><table id='new-ui-group-table' class='mdl-data-table'>")
  html.append('<thead><tr>')
  html.append('<th>Action</th>')
  html.append('<th>Group Name</th>')
  html.append('<th>Coordinator</th>')
  html.append('<th>Followers</th>')
  html.append('</tr></thead><tbody>')

  if(rows.isEmpty()) {
    html.append("<tr><td colspan='4'>No Sonos groups have been created yet.</td></tr>")
  } else {
    rows.each { Map row ->
      String groupName = row.name?.toString() ?: 'Unnamed group'
      String token = newUiGroupToken(groupName)
      String editIcon = "<iconify-icon icon='material-symbols:edit' style='font-size:19px;vertical-align:middle;margin-right:6px'></iconify-icon>"
      String deleteIcon = "<iconify-icon icon='material-symbols:delete-outline' style='font-size:20px;vertical-align:middle'></iconify-icon>"
      String actionMarkup = newUiButtonLink("newUiEditGroup|${token}", editIcon, '#424242', '19px') +
          newUiButtonLink("newUiDeleteGroup|${token}", deleteIcon, '#F44336', '20px')
      String safeName = escapeNewUiHtml(groupName)
      String groupDeviceId = row.device?.id?.toString()
      if(groupDeviceId) {
        safeName = "<a href='/device/edit/${escapeNewUiHtml(groupDeviceId)}' target='_blank'>${safeName}</a>"
      }
      String coordinatorName = row.coordinatorName?.toString() ?: row.coordinatorId?.toString() ?: 'Unknown'
      String safeCoordinator = escapeNewUiHtml(coordinatorName)
      String followerMarkup = renderNewUiGroupFollowers(row.followerIds)

      html.append('<tr>')
      html.append("<td class='new-ui-group-action-cell'>${actionMarkup}</td>")
      html.append("<td class='new-ui-group-name'><strong>${safeName}</strong></td>")
      html.append("<td>${safeCoordinator}</td>")
      html.append("<td class='new-ui-group-followers'>${followerMarkup}</td>")
      html.append('</tr>')
    }
  }

  html.append('</tbody></table></div>')
  return html.toString()
}

List<Map> buildNewUiGroupRows() {
  LinkedHashMap<String, Map> rowsByName = new LinkedHashMap<String, Map>()
  Map configuredGroups = state.userGroups instanceof Map ? (Map)state.userGroups : [:]

  configuredGroups.each { Object rawName, Object rawDefinition ->
    String groupName = rawName?.toString()
    Map definition = rawDefinition instanceof Map ? (Map)rawDefinition : [:]
    if(groupName) {
      rowsByName[groupName] = [
        name: groupName,
        coordinatorId: definition.groupCoordinatorId?.toString(),
        followerIds: normalizeNewUiGroupPlayerIds(definition.playerIds),
        device: null
      ]
    }
  }

  getCurrentGroupDevices().each { ChildDeviceWrapper groupDevice ->
    String groupName = newUiGroupNameFromDeviceNetworkId(groupDevice.getDeviceNetworkId()?.toString())
    if(!groupName) {
      return
    }
    Map row = rowsByName[groupName] ?: [
      name: groupName,
      coordinatorId: null,
      followerIds: [],
      device: null
    ]
    row.device = groupDevice
    if(!row.coordinatorId) {
      row.coordinatorId = groupDevice.getDataValue('groupCoordinatorId')?.toString()
    }
    if(!row.followerIds) {
      row.followerIds = normalizeNewUiGroupPlayerIds(groupDevice.getDataValue('playerIds'))
    }
    rowsByName[groupName] = row
  }

  Map<String, String> playerNames = getNewUiGroupPlayerNameMap()
  rowsByName.values().each { Map row ->
    String coordinatorId = row.coordinatorId?.toString()
    row.coordinatorName = coordinatorId ? (playerNames[coordinatorId] ?: coordinatorId) : null
  }

  List<Map> rows = new ArrayList<Map>(rowsByName.values())
  rows.sort { Map left, Map right ->
    left.name.toString().compareToIgnoreCase(right.name.toString())
  }
  return rows
}

Map findNewUiGroupByToken(String token) {
  if(!token) {
    return null
  }
  return buildNewUiGroupRows().find { Map row -> newUiGroupToken(row.name?.toString()) == token }
}

String newUiGroupToken(String groupName) {
  String encoded = groupName?.bytes?.encodeBase64()?.toString() ?: ''
  return encoded.replace('+', '-').replace('/', '_').replace('=', '')
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

Map<String, String> getNewUiGroupPlayerNameMap() {
  return getCurrentPlayerDevices().collectEntries { ChildDeviceWrapper player ->
    String id = player.getDataValue('id')?.toString()
    id ? [(id): (player.getDataValue('name')?.toString() ?: id)] : [:]
  }
}

String renderNewUiGroupFollowers(Object followerIds) {
  List<String> ids = normalizeNewUiGroupPlayerIds(followerIds)
  if(ids.isEmpty()) {
    return newUiStatusBadge('—', 'na', 'No follower speakers configured')
  }
  Map<String, String> playerNames = getNewUiGroupPlayerNameMap()
  return ids.collect { String id -> escapeNewUiHtml(playerNames[id] ?: id) }.join('<br>')
}

Map getNewUiGroupDraft() {
  return state[NEW_UI_GROUP_DRAFT_KEY] instanceof Map ? (Map)state[NEW_UI_GROUP_DRAFT_KEY] : [:]
}

String getNewUiGroupCoordinator(Map draft) {
  Object configured = settings.containsKey('newUiGroupCoordinator') ? settings.newUiGroupCoordinator : null
  return configured != null ? configured.toString() : (draft.coordinatorId?.toString() ?: '')
}

List<String> getNewUiGroupFollowers(Map draft) {
  Object configured = settings.containsKey('newUiGroupFollowers') ? settings.newUiGroupFollowers : null
  return normalizeNewUiGroupPlayerIds(configured != null ? configured : draft.followerIds)
}

Map localPlayerPage() {
  startDiscoverySession()

  Long endTime = atomicState.discoveryEndTime ? (atomicState.discoveryEndTime as Long) : now()
  Integer remainingSecs = Math.max(0, (Integer)((endTime - now()) / 1000))

  dynamicPage(
		name: "localPlayerPage",
		title: "Discovery Started!",
		nextPage: 'localPlayerSelectionPage',
		install: false,
		uninstall: false
  ) {
    section("Please wait while we discover your Sonos devices. Using both SSDP and mDNS discovery methods. Click Next when all your devices have been discovered.") {
      if(atomicState.discoveryRunning == true) {
        paragraph "<b><span class='app-state-${app.id}-discoveryTimer'>Discovery time remaining: ${remainingSecs} seconds</span></b>"
      } else {
        paragraph "<b>Discovery has stopped.</b> Click Next to proceed or extend to continue."
      }
      input 'btnExtend60', 'button', title: 'Extend 60 Seconds', submitOnChange: true
      input 'btnExtend300', 'button', title: 'Extend 5 Minutes', submitOnChange: true
      paragraph (
        "<div id='discoveryStatus'>" +
        "<span class='app-state-${app.id}-sonosDiscoveredCount'>Found Devices (${discoveredSonoses.size()} primary, ${discoveredSonosSecondaries.size()} secondary): </span>" +
        "<span class='app-state-${app.id}-sonosDiscovered'>${getFoundSonoses()}</span>" +
        "</div>"
      )
    }
  }
}

Map localPlayerSelectionPage() {
  stopDiscovery()

  // Clear feedback when entering page fresh (not from button click)
  if(state.playerCreationFeedback && state.playerCreationTimestamp) {
    Long timeSinceCreation = now() - (state.playerCreationTimestamp as Long)
    if(timeSinceCreation > 2000) { state.remove('playerCreationFeedback') }
  }
  if(state.playerRemovalFeedback && state.playerRemovalTimestamp) {
    Long timeSinceRemoval = now() - (state.playerRemovalTimestamp as Long)
    if(timeSinceRemoval > 2000) { state.remove('playerRemovalFeedback') }
  }

  LinkedHashMap newlyDiscovered = discoveredSonoses.collectEntries{id, player -> [(id.toString()): player.name]}
  LinkedHashMap previouslyCreated = getCurrentPlayerDevices().collectEntries{[(it.getDeviceNetworkId().toString()): it.getDataValue('name')]}

  // Additional duplicate check: ensure no device appears twice with different MACs
  // This can happen if a device changes IP or MAC spoofing occurs
  Set<String> seenPlayerIds = [] as Set
  LinkedHashMap deduplicatedDiscovered = [:]
  newlyDiscovered.each { mac, name ->
    Map deviceInfo = discoveredSonoses[mac]
    String playerId = deviceInfo?.id
    if(playerId && !seenPlayerIds.contains(playerId)) {
      deduplicatedDiscovered[mac] = name
      seenPlayerIds.add(playerId)
    } else if(playerId) {
      logWarn("Duplicate device detected: ${name} (${mac}) has same playerId as another device. Skipping.")
    } else {
      deduplicatedDiscovered[mac] = name
    }
  }

  LinkedHashMap selectionOptions = previouslyCreated
  Integer newlyFoundCount = 0
  deduplicatedDiscovered.each{ k,v ->
    if(!selectionOptions.containsKey(k)) {
      selectionOptions[k] = v
      newlyFoundCount++
    }
  }

  dynamicPage(
		name: "localPlayerSelectionPage",
		title: "",
		nextPage: 'mainPage',
		install: false,
		uninstall: false
  ) {
    section("Select your device(s) below.") {
      input (
        name: 'playerDevices',
        title: "<b>Available Devices (${selectionOptions.size()}):</b> <i>${newlyFoundCount > 0 ? "${newlyFoundCount} newly discovered" : 'No new devices found'}</i>",
        type: 'enum',
        options: selectionOptions,
        multiple: true,
        required: false,
        submitOnChange: true
      )

      // Show feedback messages
      if(state.playerCreationFeedback) {
        paragraph "<div style='background-color:#90EE90;padding:10px;border-radius:5px;margin:10px 0;'><b>✓ ${state.playerCreationFeedback}</b></div>"
      }
      if(state.playerRemovalFeedback) {
        paragraph "<div style='background-color:#FFB6C1;padding:10px;border-radius:5px;margin:10px 0;'><b>✓ ${state.playerRemovalFeedback}</b></div>"
      }

      // Action buttons
      List<String> willBeCreated = settings.playerDevices ? (settings.playerDevices - getCreatedPlayerDevices()) : []
      List<ChildDeviceWrapper> willBeRemoved = getCurrentPlayerDevices().findAll { p -> (!settings.playerDevices?.contains(p.getDeviceNetworkId())) }

      if(willBeCreated.size() > 0) {
        input 'createPlayerDevices', 'button', title: "Create ${willBeCreated.size()} Player(s)", submitOnChange: true, width: 6
        String createList = willBeCreated.collect{ selectionOptions[it] }.join('\n')
        paragraph "<b>Will create:</b>\n${createList}"
      }

      if(willBeRemoved.size() > 0) {
        input 'removePlayerDevices', 'button', title: "Remove ${willBeRemoved.size()} Player(s)", submitOnChange: true, width: 6
        String removeList = willBeRemoved.collect{ it.label }.join('\n')
        paragraph "<b>Will remove:</b>\n${removeList}"
      }

      // Only show "all in sync" message if:
      // 1. Current selection has no pending creates or removes AND
      // 2. There are no newly discovered devices available to select
      if(willBeCreated.size() == 0 && willBeRemoved.size() == 0 && newlyFoundCount == 0) {
        paragraph "<i>All discovered devices are already created. Select or deselect devices above to create or remove players.</i>"
      } else if(willBeCreated.size() == 0 && willBeRemoved.size() == 0 && newlyFoundCount > 0) {
        paragraph "<i>Select newly discovered devices above to create them, or deselect existing devices to remove them.</i>"
      }

      href (
        name: 'localPlayerPageRefresh',
        title: 'Re-discover Devices',
        description: 'Run discovery again to find new devices',
        page: 'localPlayerPage'
      )
    }
  }
}

Map groupPage() {
  if(!state.userGroups) { state.userGroups = [:] }
  // Build player data in a single pass — collect ID, name, and swGen together
  // Avoids N separate getDeviceFromRincon() calls for S1/S2 filtering
  List<ChildDeviceWrapper> players = getCurrentPlayerDevices()
  Map coordinatorSelectionOptions = players.collectEntries { player -> [(player.getDataValue('id')): player.getDataValue('name')] }
  Map<String, String> playerSwGenMap = players.collectEntries { player -> [(player.getDataValue('id')): player.getDataValue('swGen')] }
  Boolean coordIsS1 = (newGroupCoordinator && playerSwGenMap[newGroupCoordinator] == '1')
  logDebug("Selected group coordinator is S1: ${coordIsS1}")
  Map playerSelectionOptionsS1 = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator && playerSwGenMap[it.key] == '1' }
  Map playerSelectionOptionsS2 = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator && playerSwGenMap[it.key] == '2' }
  Map playerSelectionOptions = [:]
  if(!newGroupCoordinator) {playerSelectionOptions = playerSelectionOptionsS1 + playerSelectionOptionsS2}
  else if(newGroupCoordinator && coordIsS1) {playerSelectionOptions = playerSelectionOptionsS1}
  else {playerSelectionOptions = playerSelectionOptionsS2}

  String edg = app.getSetting('editDeleteGroup')
  if(edg) {
    if(!app.getSetting('newGroupName')) {
      app.updateSetting('newGroupName', edg)
    }
    if(!app.getSetting('newGroupPlayers')) {
      app.updateSetting('newGroupPlayers', [type: 'enum', value: state.userGroups[edg]?.playerIds])
    }
    if(!app.getSetting('newGroupCoordinator')) {
      app.updateSetting('newGroupCoordinator', [type: 'enum', value: state.userGroups[edg]?.groupCoordinatorId])
    }
    playerSelectionOptions = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator }
  }
  if(app.getSetting('newGroupName') == null || app.getSetting('newGroupPlayers') == null || app.getSetting('newGroupCoordinator') == null) {
    state.groupPageError = 'You must select a coordinator, at least 1 follower player, and provide a name for the group'
  } else {
    state.remove('groupPageError')
  }

  dynamicPage(title: 'Sonos Player Virtual Groups', nextPage: 'mainPage') {
    section {
      paragraph ('This page allows you to create and delete Sonos group devices.')
    }
    section ('Select players to add to new group:', hideable: true){
      if(state.groupPageError) {
        paragraph (state.groupPageError)
      }
      input(name: 'newGroupName', type: 'text', title: 'Group Name:', required: false, submitOnChange: true)
      input(name: 'newGroupCoordinator', type: 'enum', title: 'Select Coordinator (leader):', multiple: false, options: coordinatorSelectionOptions, required: false, submitOnChange: true, offerAll: false)
      input(name: 'newGroupPlayers', type: 'enum', title: 'Select Players (followers):', multiple: true, options: playerSelectionOptions, required: false, submitOnChange: true, offerAll: false)
      input(name: 'saveGroup', type: 'button', title: 'Save Group?', submitOnChange: true, width: 2, disabled: state.groupPageError != null)
      input(name: 'deleteGroup', type: 'button', title: 'Delete Group?', submitOnChange: true, width: 2)
      input(name: 'cancelGroupEdit', type: 'button', title: 'Cancel Edit?', submitOnChange: true, width: 2)
    }
    section ("Select virtual groups to edit/delete (${state.userGroups.size()} active groups found):", hideable: true) {
      input (name: 'editDeleteGroup', title: '', type: 'enum', multiple: false, options: state.userGroups.keySet(), submitOnChange: true)
    }
    if(state.refreshGroupPage) {
      section() {
        state.remove('refreshGroupPage')
        app.removeSetting('newGroupName')
        app.removeSetting('newGroupPlayers')
        app.removeSetting('newGroupCoordinator')
        paragraph "<script>{changeSubmit(this)}</script>"
      }
    }
  }
}
// =============================================================================
// End App Pages
// =============================================================================



// =============================================================================
// Button Handlers
// =============================================================================
void appButtonHandler(String buttonName) {
  if(buttonName == 'applySettingsButton') { applySettingsButton() }
  if(buttonName == 'saveGroup') { saveGroup() }
  if(buttonName == 'deleteGroup') { deleteGroup() }
  if(buttonName == 'cancelGroupEdit') { cancelGroupEdit() }
  if(buttonName == 'createPlayerDevices') { createPlayerDevicesWithFeedback() }
  if(buttonName == 'removePlayerDevices') { removePlayerDevicesWithFeedback() }

  // Update management buttons
  if(buttonName == 'btnCheckForUpdates') { checkForUpdates(true) }
  if(buttonName == 'btnInstallUpdate') { installUpdate() }
  if(buttonName == 'btnCheckInstalledVersions') { checkInstalledVersions() }
  if(buttonName == 'btnFixVersionMismatches') { fixVersionMismatches() }
  if(buttonName == 'btnCleanupDuplicates') { cleanupDuplicates() }
  if(buttonName == 'btnPublishLibraries') { publishLibraries() }
  if(buttonName == 'btnDismissUpdate') {
    state.updateAvailable = false
    state.latestVersion = null
    state.latestReleaseDate = null
    state.latestReleaseUrl = null
  }
  if(buttonName == 'btnExtend60') { extendDiscovery(60) }
  if(buttonName == 'btnExtend300') { extendDiscovery(300) }
  if(buttonName == 'btnNewUiDiscoverSpeakers') {
    startNewUiDiscoverySession()
    return
  }

  if(buttonName?.startsWith('newUiCreateGroup|')) {
    beginNewUiGroupEditor(buttonName.minus('newUiCreateGroup|'))
    return
  }
  if(buttonName?.startsWith('newUiEditGroup|')) {
    beginNewUiGroupEdit(buttonName.minus('newUiEditGroup|'))
    return
  }
  if(buttonName?.startsWith('newUiDeleteGroup|')) {
    requestNewUiGroupDeletion(buttonName.minus('newUiDeleteGroup|'))
    return
  }
  if(buttonName == 'btnNewUiCreateGroup') {
    beginNewUiGroupEditor(null)
    return
  }
  if(buttonName == 'btnNewUiSaveGroup') {
    saveNewUiGroup()
    return
  }
  if(buttonName == 'btnNewUiCancelGroup') {
    cancelNewUiGroupEditor()
    return
  }
  if(buttonName == 'btnConfirmNewUiDeleteGroup') {
    confirmNewUiGroupDeletion()
    return
  }
  if(buttonName == 'btnCancelNewUiDeleteGroup') {
    cancelNewUiGroupDeletion()
    return
  }
  if(buttonName.startsWith('newUiCreatePlayer|')) {
    createNewUiPlayer(buttonName.minus('newUiCreatePlayer|'))
    return
  }
  if(buttonName.startsWith('newUiDeletePlayer|')) {
    requestNewUiPlayerDeletion(buttonName.minus('newUiDeletePlayer|'))
    return
  }
  if(buttonName == 'btnConfirmNewUiDeletePlayer') {
    confirmNewUiPlayerDeletion()
    return
  }
  if(buttonName == 'btnCancelNewUiDeletePlayer') {
    cancelNewUiPlayerDeletion()
    return
  }
}

void createNewUiPlayer(String playerKey) {
  if(!playerKey) {
    logWarn('New UI create request did not include a Sonos player identifier')
    return
  }

  Map playerRow = buildNewUiSpeakerRows().find { Map row ->
    String rowKey = row.discoveryKey?.toString() ?: row.id?.toString()
    rowKey == playerKey
  }
  if(playerRow == null) {
    logWarn("New UI create request could not find discovered Sonos player '${playerKey}'")
    return
  }
  if(playerRow.created == true) {
    logInfo("New UI create request ignored because Sonos player '${playerKey}' already exists")
    return
  }
  if(!discoveredSonoses[playerKey]) {
    logWarn("New UI create request could not find discovery data for Sonos player '${playerKey}'")
    return
  }

  List<String> configuredPlayers = getConfiguredPlayerDeviceKeys()
  if(!configuredPlayers.contains(playerKey)) {
    configuredPlayers.add(playerKey)
  }

  try {
    Integer createdCount = createPlayerDevices([playerKey])
    if(createdCount < 1) {
      logWarn("New UI could not create Sonos player '${playerKey}'")
      return
    }
    app.updateSetting('playerDevices', [type: 'enum', value: configuredPlayers])
    String playerName = playerRow.name?.toString() ?: playerKey
    logInfo("New UI created Sonos Advanced Player '${playerName}' (${playerKey})")
    runIn(3, 'finalizeNewUiPlayerCreation')
  } catch(Exception e) {
    logError("New UI failed to create Sonos player '${playerKey}': ${e.message}")
  }
}

void finalizeNewUiPlayerCreation() {
  app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
  runIn(1, 'subscribeToGroupCommandRequests')
  app.sendEvent(name: 'newUiSpeakerTable', value: 'updated')
}

void beginNewUiGroupEditor(String coordinatorId) {
  if(coordinatorId) {
    ChildDeviceWrapper coordinator = getCurrentPlayerDevices().find { ChildDeviceWrapper player ->
      player.getDataValue('id')?.toString() == coordinatorId
    }
    if(coordinator == null) {
      logWarn("New UI group editor could not find coordinator '${coordinatorId}'")
      return
    }
  }

  clearNewUiGroupSettings()
  state[NEW_UI_GROUP_EDITOR_MODE_KEY] = NEW_UI_GROUP_MODE_CREATE
  state.remove(NEW_UI_GROUP_ORIGINAL_NAME_KEY)
  state[NEW_UI_GROUP_AUTO_NAME_KEY] = true
  state.remove(NEW_UI_GROUP_ERROR_KEY)
  state.remove(NEW_UI_GROUP_DELETE_KEY)
  Map<String, String> playerNames = getNewUiGroupPlayerNameMap()
  String initialName = buildNewUiAutomaticGroupName(coordinatorId, [], playerNames)
  state[NEW_UI_GROUP_DRAFT_KEY] = [name: initialName, coordinatorId: coordinatorId, followerIds: []]
  if(coordinatorId) {
    app.updateSetting('newUiGroupCoordinator', [type: 'enum', value: coordinatorId])
    app.updateSetting('newUiGroupName', [type: 'text', value: initialName])
  }
  logInfo(coordinatorId
      ? "New UI group creation started from coordinator '${coordinatorId}'"
      : 'New UI group creation started')
}

void beginNewUiGroupEdit(String token) {
  Map group = findNewUiGroupByToken(token)
  if(group == null) {
    logWarn("New UI group edit request could not resolve group token '${token}'")
    return
  }

  clearNewUiGroupSettings()
  state[NEW_UI_GROUP_EDITOR_MODE_KEY] = NEW_UI_GROUP_MODE_EDIT
  state[NEW_UI_GROUP_ORIGINAL_NAME_KEY] = group.name.toString()
  Map<String, String> playerNames = getNewUiGroupPlayerNameMap()
  List<String> existingFollowerIds = normalizeNewUiGroupPlayerIds(group.followerIds)
  state[NEW_UI_GROUP_AUTO_NAME_KEY] = isNewUiGroupAutomaticName(
      group.name.toString(), buildNewUiAutomaticGroupName(group.coordinatorId?.toString(), existingFollowerIds, playerNames))
  state.remove(NEW_UI_GROUP_ERROR_KEY)
  state[NEW_UI_GROUP_DRAFT_KEY] = [
    name: group.name.toString(),
    coordinatorId: group.coordinatorId?.toString(),
    followerIds: existingFollowerIds
  ]
  app.updateSetting('newUiGroupName', group.name.toString())
  if(group.coordinatorId) {
    app.updateSetting('newUiGroupCoordinator', [type: 'enum', value: group.coordinatorId.toString()])
  }
  app.updateSetting('newUiGroupFollowers', [type: 'enum', value: normalizeNewUiGroupPlayerIds(group.followerIds)])
  logInfo("New UI group edit started for '${group.name}'")
}

void saveNewUiGroup() {
  if(!state[NEW_UI_GROUP_EDITOR_MODE_KEY]) {
    logWarn('New UI group save requested without an active editor')
    return
  }

  Map draft = getNewUiGroupDraft()
  String originalName = state[NEW_UI_GROUP_ORIGINAL_NAME_KEY]?.toString()
  Object configuredName = settings.containsKey('newUiGroupName') ? settings.newUiGroupName : null
  String groupName = configuredName != null ? configuredName.toString().trim() : (draft.name?.toString()?.trim() ?: '')
  String coordinatorId = getNewUiGroupCoordinator(draft)
  List<String> followerIds = getNewUiGroupFollowers(draft).unique()

  String draftName = draft.name?.toString()?.trim() ?: ''
  if(configuredName != null && groupName && groupName != draftName) {
    state[NEW_UI_GROUP_AUTO_NAME_KEY] = false
  }

  Map<String, String> playerNames = getNewUiGroupPlayerNameMap()
  if(!coordinatorId || !playerNames.containsKey(coordinatorId)) {
    state[NEW_UI_GROUP_ERROR_KEY] = 'Select a coordinator before saving the group.'
    return
  }
  if(followerIds.isEmpty()) {
    state[NEW_UI_GROUP_ERROR_KEY] = 'Select at least one follower before saving the group.'
    return
  }
  if(followerIds.contains(coordinatorId)) {
    state[NEW_UI_GROUP_ERROR_KEY] = 'The coordinator cannot also be a follower.'
    return
  }
  if(state[NEW_UI_GROUP_AUTO_NAME_KEY] == true) {
    groupName = buildNewUiAutomaticGroupName(coordinatorId, followerIds, playerNames)
  }
  List<String> unknownFollowers = followerIds.findAll { String id -> !playerNames.containsKey(id) }
  if(!unknownFollowers.isEmpty()) {
    state[NEW_UI_GROUP_ERROR_KEY] = "These follower speakers are no longer available: ${unknownFollowers.join(', ')}"
    return
  }

  if(!groupName) {
    groupName = "${playerNames[coordinatorId]} + ${followerIds.size()} others"
  }
  Map configuredGroups = state.userGroups instanceof Map ? (Map)state.userGroups : [:]
  if(configuredGroups.containsKey(groupName) && groupName != originalName) {
    state[NEW_UI_GROUP_ERROR_KEY] = "A group named '${groupName}' already exists. Choose a different name."
    return
  }
  if(originalName && originalName != groupName && !renameNewUiGroupDevice(originalName, groupName)) {
    state[NEW_UI_GROUP_ERROR_KEY] = "The existing group device could not be renamed to '${groupName}'."
    return
  }

  if(originalName && originalName != groupName) {
    configuredGroups.remove(originalName)
  }
  configuredGroups[groupName] = [groupCoordinatorId: coordinatorId, playerIds: followerIds]
  state.userGroups = configuredGroups
  String mode = state[NEW_UI_GROUP_EDITOR_MODE_KEY] as String
  clearNewUiGroupSettings()
  createGroupDevices()
  state.remove(NEW_UI_GROUP_EDITOR_MODE_KEY)
  state.remove(NEW_UI_GROUP_ORIGINAL_NAME_KEY)
  state.remove(NEW_UI_GROUP_DRAFT_KEY)
  state.remove(NEW_UI_GROUP_AUTO_NAME_KEY)
  state.remove(NEW_UI_GROUP_ERROR_KEY)
  runIn(1, 'subscribeToGroupCommandRequests')
  app.sendEvent(name: NEW_UI_GROUP_TABLE_EVENT, value: 'updated')
  logInfo("New UI ${mode == NEW_UI_GROUP_MODE_EDIT ? 'updated' : 'created'} Sonos group '${groupName}'")
}

Boolean renameNewUiGroupDevice(String oldName, String newName) {
  String oldDni = "${app.id}-SonosGroupDevice-${oldName}"
  String newDni = "${app.id}-SonosGroupDevice-${newName}"
  DeviceWrapper existingDevice = getChildDevice(oldDni)
  if(existingDevice == null) {
    return true
  }
  try {
    existingDevice.setDeviceNetworkId(newDni)
    existingDevice.setLabel("Sonos Group: ${newName}")
    logInfo("Renamed group device from '${oldName}' to '${newName}'")
    return true
  } catch(Exception e) {
    logError("Failed to rename group device from '${oldName}' to '${newName}': ${e.message}")
    return false
  }
}

void clearNewUiGroupSettings() {
  app.removeSetting('newUiGroupName')
  app.removeSetting('newUiGroupCoordinator')
  app.removeSetting('newUiGroupFollowers')
}

String synchronizeNewUiGroupName(Map draft, String coordinatorId, List<String> followerIds,
    Map<String, String> playerNames) {
  String configuredName = settings.containsKey('newUiGroupName') && settings.newUiGroupName != null
      ? settings.newUiGroupName.toString().trim()
      : null
  String previousName = draft.name?.toString()?.trim() ?: ''
  if(configuredName != null && configuredName != previousName && configuredName != '') {
    state[NEW_UI_GROUP_AUTO_NAME_KEY] = false
  }

  String automaticName = buildNewUiAutomaticGroupName(coordinatorId, followerIds, playerNames)
  if(state[NEW_UI_GROUP_AUTO_NAME_KEY] != false && automaticName) {
    if(configuredName != automaticName) {
      app.updateSetting('newUiGroupName', [type: 'text', value: automaticName])
    }
    draft.name = automaticName
    draft.coordinatorId = coordinatorId
    draft.followerIds = followerIds
    return automaticName
  }

  String editorName = configuredName != null ? configuredName : previousName
  draft.name = editorName
  draft.coordinatorId = coordinatorId
  draft.followerIds = followerIds
  return editorName
}

String buildNewUiAutomaticGroupName(String coordinatorId, List<String> followerIds,
    Map<String, String> playerNames) {
  String coordinatorName = playerNames[coordinatorId]
  if(!coordinatorName) { return '' }
  if(followerIds.size() == 1 && playerNames[followerIds[0]]) {
    return "${coordinatorName} + ${playerNames[followerIds[0]]}"
  }
  return "${coordinatorName} + ${followerIds.size()} others"
}

Boolean isNewUiGroupAutomaticName(String name, String automaticName) {
  return name && automaticName && name == automaticName
}

void cancelNewUiGroupEditor() {
  clearNewUiGroupSettings()
  state.remove(NEW_UI_GROUP_EDITOR_MODE_KEY)
  state.remove(NEW_UI_GROUP_ORIGINAL_NAME_KEY)
  state.remove(NEW_UI_GROUP_DRAFT_KEY)
  state.remove(NEW_UI_GROUP_AUTO_NAME_KEY)
  state.remove(NEW_UI_GROUP_ERROR_KEY)
  logInfo('Cancelled New UI group edit')
}

void requestNewUiGroupDeletion(String token) {
  Map group = findNewUiGroupByToken(token)
  if(group == null) {
    logWarn("New UI group delete request could not resolve group token '${token}'")
    return
  }
  state[NEW_UI_GROUP_DELETE_KEY] = token
  logInfo("New UI delete requested for Sonos group '${group.name}'; awaiting confirmation")
}

void confirmNewUiGroupDeletion() {
  String token = state[NEW_UI_GROUP_DELETE_KEY]?.toString()
  state.remove(NEW_UI_GROUP_DELETE_KEY)
  Map group = findNewUiGroupByToken(token)
  if(group == null) {
    logWarn('New UI group delete confirmation could not resolve the selected group')
    return
  }

  String groupName = group.name.toString()
  try {
    if(state.userGroups instanceof Map) {
      state.userGroups.remove(groupName)
    }
    String groupDni = "${app.id}-SonosGroupDevice-${groupName}"
    if(getChildDevice(groupDni) != null) {
      app.deleteChildDevice(groupDni)
    }
    app.updateSetting('groupDevices', [type: 'enum', value: getUserGroupsDNIsFromUserGroups()])
    if(state[NEW_UI_GROUP_ORIGINAL_NAME_KEY]?.toString() == groupName) {
      cancelNewUiGroupEditor()
    }
    runIn(1, 'subscribeToGroupCommandRequests')
    app.sendEvent(name: NEW_UI_GROUP_TABLE_EVENT, value: 'updated')
    logInfo("Removed Sonos group '${groupName}'")
  } catch(Exception e) {
    logError("Failed to remove Sonos group '${groupName}': ${e.message}")
  }
}

void cancelNewUiGroupDeletion() {
  String token = state[NEW_UI_GROUP_DELETE_KEY]?.toString()
  state.remove(NEW_UI_GROUP_DELETE_KEY)
  if(token) {
    Map group = findNewUiGroupByToken(token)
    logInfo("Cancelled New UI group delete request for '${group?.name ?: token}'")
  }
}

List<String> getConfiguredPlayerDeviceKeys() {
  if(settings.playerDevices instanceof Collection) {
    return settings.playerDevices.collect { Object value -> value?.toString() }.findAll { String value -> value }
  }
  if(settings.playerDevices) {
    return [settings.playerDevices.toString()]
  }
  return []
}

void requestNewUiPlayerDeletion(String playerKey) {
  if(!playerKey) {
    logWarn('New UI delete request did not include a Sonos player identifier')
    return
  }
  state.pendingNewUiDeletePlayer = playerKey
  Map playerRow = buildNewUiSpeakerRows().find { Map row ->
    String rowKey = row.discoveryKey?.toString() ?: row.id?.toString()
    rowKey == playerKey
  }
  String playerName = playerRow?.name?.toString() ?: playerKey
  logInfo("New UI delete requested for Sonos player '${playerName}' (${playerKey}); awaiting confirmation")
}

void confirmNewUiPlayerDeletion() {
  String playerKey = state.pendingNewUiDeletePlayer as String
  state.remove('pendingNewUiDeletePlayer')
  if(!playerKey) {
    logWarn('New UI delete confirmation received without a pending Sonos player')
    return
  }

  ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
    String deviceNetworkId = candidate.getDeviceNetworkId()?.toString()
    String playerId = candidate.getDataValue('id')?.toString()
    deviceNetworkId == playerKey || playerId == playerKey
  }
  if(player == null) {
    logWarn("New UI delete confirmation could not find Sonos player '${playerKey}'")
    return
  }

  String deviceNetworkId = player.getDeviceNetworkId()?.toString()
  String playerName = player.getDataValue('name')?.toString() ?: player.label?.toString() ?: playerKey
  try {
    logInfo("Removing Sonos Advanced Player '${playerName}' (${deviceNetworkId}) from Hubitat")
    app.deleteChildDevice(deviceNetworkId)
    List<String> configuredPlayers = getConfiguredPlayerDeviceKeys()
    configuredPlayers = configuredPlayers.findAll { String value -> value != deviceNetworkId && value != playerKey }
    app.updateSetting('playerDevices', [type: 'enum', value: configuredPlayers])
    runIn(3, 'finalizeNewUiPlayerDeletion')
  } catch(Exception e) {
    logError("Failed to remove Sonos Advanced Player '${playerName}': ${e.message}")
  }
}

void finalizeNewUiPlayerDeletion() {
  app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
  app.sendEvent(name: 'newUiSpeakerTable', value: 'updated')
}

void cancelNewUiPlayerDeletion() {
  String playerKey = state.pendingNewUiDeletePlayer as String
  state.remove('pendingNewUiDeletePlayer')
  if(playerKey) {
    logInfo("Cancelled New UI delete request for Sonos player '${playerKey}'")
  }
}

void startNewUiDiscoverySession() {
  if(atomicState.discoveryRunning == true) {
    stopDiscovery()
  }
  logInfo('Starting New UI Sonos speaker discovery for 60 seconds')
  startDiscoverySession()
}

void applySettingsButton() { configure() }

void saveGroup() {
  if(!state.userGroups) { state.userGroups = [:] }
  String editingGroup = app.getSetting('editDeleteGroup')
  String newName = app.getSetting('newGroupName')

  // If editing and name changed, rename the existing device in place
  // This preserves dashboard tiles, rules, and automations that reference the device
  if(editingGroup && editingGroup != newName) {
    if(state.userGroups.containsKey(newName)) {
      logWarn("Cannot rename group '${editingGroup}' to '${newName}': a group with that name already exists")
      return
    }
    String oldDni = "${app.id}-SonosGroupDevice-${editingGroup}"
    String newDni = "${app.id}-SonosGroupDevice-${newName}"
    DeviceWrapper existingDevice = getChildDevice(oldDni)
    if(existingDevice) {
      try {
        existingDevice.setDeviceNetworkId(newDni)
      } catch (Exception e) {
        logError("Failed to rename group device from '${editingGroup}' to '${newName}': ${e.message}")
        return
      }
      try {
        existingDevice.setLabel("Sonos Group: ${newName}")
      } catch (Exception e) {
        logWarn("Renamed group device DNI for '${editingGroup}', but could not update the label: ${e.message}")
      }
      logInfo("Renamed group device from '${editingGroup}' to '${newName}'")
    }
    state.userGroups.remove(editingGroup)
  }

  state.userGroups[newName] = [groupCoordinatorId:app.getSetting('newGroupCoordinator'), playerIds:app.getSetting('newGroupPlayers')]
  app.removeSetting('newGroupName')
  app.removeSetting('newGroupPlayers')
  app.removeSetting('newGroupCoordinator')
  app.removeSetting('editDeleteGroup')

  createGroupDevices()
  runIn(1, 'subscribeToGroupCommandRequests')
}

void deleteGroup() {
  String groupName = app.getSetting('editDeleteGroup') ?: app.getSetting('newGroupName')
  if(!groupName) {
    logWarn('No group selected to delete')
    return
  }
  if(!state.userGroups?.containsKey(groupName)) {
    logWarn("Group '${groupName}' not found, nothing to delete")
  }
  state.userGroups.remove(groupName)
  app.removeSetting('newGroupName')
  app.removeSetting('newGroupPlayers')
  app.removeSetting('newGroupCoordinator')
  app.removeSetting('editDeleteGroup')
  state.refreshGroupPage = true
  // Update groupDevices setting to match remaining groups so removeOrphans() can detect the orphaned device
  app.updateSetting('groupDevices', [type: 'enum', value: getUserGroupsDNIsFromUserGroups()])
  removeOrphans()
  runIn(1, 'subscribeToGroupCommandRequests')
}

void cancelGroupEdit() {
  app.removeSetting('newGroupName')
  app.removeSetting('newGroupPlayers')
  app.removeSetting('newGroupCoordinator')
  app.removeSetting('editDeleteGroup')
}
// =============================================================================
// End Button Handlers
// =============================================================================



// =============================================================================
// Initialize() and Configure()
// =============================================================================
void initialize() { configure() }
void configure() {
  logInfo("${app.name} updated")
  unsubscribe()
  scheduleUpdateCheck()
  checkForUpdates()

  // Initialize settings with defaults
  if(settings.skipOrphanRemoval == null) { settings.skipOrphanRemoval = false }
  if(settings.logEnable == null) { settings.logEnable = true }
  if(settings.debugLogEnable == null) { settings.debugLogEnable = false }
  if(settings.traceLogEnable == null) { settings.traceLogEnable = false }
  if(settings.descriptionTextEnable == null) { settings.descriptionTextEnable = true }

  try { createPlayerDevices() }
  catch (Exception e) { logError("createPlayerDevices() Failed: ${e}")}
  try { createGroupDevices() }
  catch (Exception e) { logError("createGroupDevices() Failed: ${e}")}
  subscribeToGroupCommandRequests()
  Map activeGroupOperation = getActiveGroupOperation()
  if(activeGroupOperation != null) {
    if(!activeGroupOperation.favoriteId || activeGroupOperation.phase == 'ENSURE_GROUP') {
      refreshGroupOperationTopology([operationId: activeGroupOperation.operationId])
    }
    runIn(1, 'advanceGroupFavoriteOperation', [
      overwrite: true,
      data: [operationId: activeGroupOperation.operationId]
    ])
  }
  if(state[GROUP_COMMAND_QUEUE_STATE_KEY] instanceof Map) {
    runIn(1, 'drainGroupCommandRequests', [overwrite: true])
  }

  state.remove('favs')
  unschedule('appGetFavoritesLocal')
  stopDiscovery()

  // Schedule TTS voice cache refresh: 30s delay for hub readiness, then daily at 3 AM
  unschedule('refreshTTSVoiceCache')
  runIn(30, 'refreshTTSVoiceCache')
  schedule('0 0 3 * * ?', 'refreshTTSVoiceCache')

  // Schedule daily favorites/playlists refresh at 3:05 AM
  unschedule('refreshFavoritesAndPlaylists')
  schedule('0 5 3 * * ?', 'refreshFavoritesAndPlaylists')
}

void refreshTTSVoiceCache() {
  logDebug('Refreshing TTS voice cache for all devices...')
  try {
    List<String> voiceNames = getTTSVoices().collect { it.name }.sort()
    if(!voiceNames || voiceNames.size() == 0) {
      logWarn('getTTSVoices() returned empty list, skipping cache refresh')
      return
    }
    String defaultVoice = 'Matthew'
    try {
      Map params = [
        uri: "http://127.0.0.1:8080/hub/details/json?reloadAccounts=false",
        contentType: 'application/json',
        requestContentType: 'application/json',
        timeout: 10
      ]
      httpGet(params) { resp ->
        if(resp.status == 200) {
          Map json = resp.data
          defaultVoice = json?.ttsCurrent ? json.ttsCurrent : 'Matthew'
        }
      }
    } catch (Exception e) {
      logWarn("Could not retrieve current TTS voice from hub: ${e.message}")
    }
    getCurrentPlayerDevices().each { ChildDeviceWrapper cd ->
      try { cd.updateTTSVoiceCache(voiceNames, defaultVoice) }
      catch (Exception e) { logDebug("Failed to update TTS cache for ${cd.label}: ${e.message}") }
    }
    getCurrentGroupDevices().each { ChildDeviceWrapper cd ->
      try { cd.updateTTSVoiceCache(voiceNames, defaultVoice) }
      catch (Exception e) { logDebug("Failed to update TTS cache for ${cd.label}: ${e.message}") }
    }
    logInfo("TTS voice cache refreshed: ${voiceNames.size()} voices, default: ${defaultVoice}")
  } catch (Exception e) {
    logError("Error refreshing TTS voice cache: ${e.message}")
  }
}
void refreshFavoritesAndPlaylists() {
  logDebug('Refreshing favorites and playlists...')
  try {
    ChildDeviceWrapper player = getCurrentPlayerDevices().find { it.getDataValue('websocketUrl') }
    if(player) {
      player.getFavorites()
      player.getPlaylists()
      logInfo("Favorites/playlists refresh triggered via ${player.label}")
    } else {
      logWarn('No player device available for favorites/playlists refresh')
    }
  } catch (Exception e) {
    logError("Error refreshing favorites/playlists: ${e.message}")
  }
}

// =============================================================================
// End Initialize() and Configure()
// =============================================================================



// =============================================================================
// Create Child Devices
// =============================================================================
void createGroupDevices() {
  if(!state.userGroups) {return}
  logDebug('Creating group devices...')
  state.userGroups.each{ it ->
    if(!it.value?.groupCoordinatorId || !it.value?.playerIds) {
      logWarn("Skipping group '${it.key}': saved group state is missing coordinator or player list")
      return
    }
    String dni = "${app.id}-SonosGroupDevice-${it.key}"
    DeviceWrapper device = getChildDevice(dni)
    if (device == null) {
      try {
        logDebug("Creating group device for ${it.key}")
        device = addChildDevice('dwinks', 'Sonos Advanced Group', dni, [name: 'Sonos Group', label: "Sonos Group: ${it.key}"])


      } catch (UnknownDeviceTypeException e) {logError("Sonos Advanced Group driver not found: ${e.message}")}
    }
    if(device == null) {
      logWarn("Skipping group '${it.key}' because the child device could not be created")
    } else {
      String groupCoordinatorId = it.value.groupCoordinatorId as String
      String playerIds =  it.value.playerIds.join(',')
      ChildDeviceWrapper coordDev = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId}
      device.updateDataValue('groupCoordinatorId', groupCoordinatorId)
      device.updateDataValue('playerIds', playerIds)
      if(coordDev == null) {
        logWarn("Skipping household ID update for group '${it.key}': coordinator '${groupCoordinatorId}' was not found")
      } else {
        String householdId = coordDev.getDataValue('householdId')
        if(householdId != null && householdId != '') {
          device.updateDataValue('householdId', householdId)
        } else {
          logWarn("Skipping household ID update for group '${it.key}': coordinator '${groupCoordinatorId}' has no householdId")
        }
      }
    }
  }
  app.removeSetting('groupDevices')
  app.updateSetting('groupDevices', [type: 'enum', value: getUserGroupsDNIsFromUserGroups()])
  removeOrphans()
}

/**
 * Group devices publish command requests as an attribute event. Subscribing
 * the parent app to that event gives commands a one-way boundary: the group
 * driver never has to synchronously call parent.getDeviceFromRincon() while a
 * parent callback may be updating group state.
 */
void subscribeToGroupCommandRequests() {
  getCurrentGroupDevices().each { ChildDeviceWrapper groupDevice ->
    subscribe(groupDevice, GROUP_COMMAND_REQUEST_ATTRIBUTE, 'groupCommandRequestHandler')
  }
  getCurrentPlayerDevices().each { ChildDeviceWrapper playerDevice ->
    subscribe(playerDevice, PLAYER_COMMAND_REQUEST_ATTRIBUTE, 'playerCommandRequestHandler')
    subscribe(playerDevice, GROUP_FAVORITE_OPERATION_ATTRIBUTE, 'groupFavoriteOperationEventHandler')
  }
}

void createPlayerDevicesWithFeedback() {
  List<String> willBeCreated = settings.playerDevices ? (settings.playerDevices - getCreatedPlayerDevices()) : []
  if(willBeCreated.size() == 0) {
    state.playerCreationFeedback = "No new players to create"
    state.playerCreationTimestamp = now()
    return
  }

  Integer createdCount = createPlayerDevices()

  // Defer settings update to allow Hubitat to register new child devices
  runIn(3, 'finalizePlayerCreation', [data: [createdCount: createdCount, expectedCount: willBeCreated.size()]])
}

void finalizePlayerCreation(Map data) {
  app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
  runIn(1, 'subscribeToGroupCommandRequests')
  Integer createdCount = data.createdCount as Integer
  Integer expectedCount = data.expectedCount as Integer
  if(createdCount == expectedCount) {
    state.playerCreationFeedback = "Successfully created ${createdCount} player(s)"
  } else {
    state.playerCreationFeedback = "Created ${createdCount} of ${expectedCount} player(s) - check logs for errors"
  }
  state.playerCreationTimestamp = now()
}

void removePlayerDevicesWithFeedback() {
  List<ChildDeviceWrapper> willBeRemoved = getCurrentPlayerDevices().findAll { p -> (!settings.playerDevices?.contains(p.getDeviceNetworkId())) }
  if(willBeRemoved.size() == 0) {
    state.playerRemovalFeedback = "No players to remove"
    state.playerRemovalTimestamp = now()
    return
  }

  Integer removedCount = 0
  willBeRemoved.each { child ->
    try {
      String dni = child.getDeviceNetworkId()
      logInfo("Removing player device: ${child.label}")
      app.deleteChildDevice(dni)
      removedCount++
    } catch(Exception e) {
      logError("Failed to remove device ${child.label}: ${e.message}")
    }
  }

  // Defer settings update to allow Hubitat to unregister deleted child devices
  runIn(3, 'finalizePlayerRemoval', [data: [removedCount: removedCount]])
}

void finalizePlayerRemoval(Map data) {
  app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
  Integer removedCount = data.removedCount as Integer
  state.playerRemovalFeedback = "Successfully removed ${removedCount} player(s)"
  state.playerRemovalTimestamp = now()
}

Integer createPlayerDevices() {
  return createPlayerDevices(null)
}

Integer createPlayerDevices(List<String> requestedPlayerDevices) {
  Integer createdCount = 0
  List<String> selectedPlayerDevices = requestedPlayerDevices != null
      ? requestedPlayerDevices
      : getConfiguredPlayerDeviceKeys()

  // Safety check: ensure playerDevices selection exists
  if(selectedPlayerDevices.isEmpty()) {
    logWarn("No player devices selected, skipping device creation")
    return createdCount
  }

  // Safety check: ensure discovery maps are initialized
  if(discoveredSonoses == null) {
    logError("discoveredSonoses is null, cannot create devices")
    return createdCount
  }
  if(discoveredSonosSecondaries == null) {
    discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
    logDebug("Initialized empty discoveredSonosSecondaries map")
  }

  selectedPlayerDevices.each{ dni ->
    if(!dni) {
      logWarn("Encountered null or empty DNI in playerDevices, skipping")
      return
    }

    ChildDeviceWrapper cd = app.getChildDevice(dni)
    Map playerInfo = discoveredSonoses[dni]

    if(cd) {
      String deviceName = cd.getDataValue('name')
      logDebug("Not creating ${deviceName ?: dni}, child already exists.")
    } else {
      if(playerInfo) {
        logInfo("Creating Sonos Advanced Player device for ${playerInfo?.name}")
        try {
          cd = addChildDevice('dwinks', 'Sonos Advanced Player', dni, [name: 'Sonos Advanced Player', label: "Sonos Advanced - ${playerInfo?.name}"])
          if(cd) { createdCount++ }
        } catch (UnknownDeviceTypeException e) {
          logError("Sonos Advanced Player driver not found: ${e.message}")
        } catch (Exception e) {
          logError("Failed to create device for ${dni}: ${e.message}")
          // Device may have been persisted before the lifecycle exception — re-check
          cd = app.getChildDevice(dni)
          if(cd) {
            logWarn("Device '${playerInfo?.name}' (${dni}) was created despite lifecycle error - will attempt configuration")
            createdCount++
          }
        }
      } else {
        logWarn("Attempted to create child device for ${dni} but did not find playerInfo")
      }
    }

    // Only update device info if we have both a valid device and player info
    if(cd && playerInfo) {
      try {
        logInfo("Updating player info with latest info from discovery...")

        // First, update all basic player info
        playerInfo.each { key, value ->
          if(key != null && value != null) {
            cd.updateDataValue(key as String, value as String)
          }
        }

        // Then handle secondaries
        LinkedHashMap<String,String> macToRincon = discoveredSonoses.collectEntries{ k,v ->
          if(k != null && v != null && v.id != null) {
            return [(k as String): (v.id as String)]
          }
          return [:]
        }
        String rincon = macToRincon[dni]

        if(rincon) {
          LinkedHashMap<String,Map> secondaries = discoveredSonosSecondaries.findAll{k,v ->
            v != null && v.primaryDeviceId != null && v.primaryDeviceId == rincon
          }
          if(secondaries){
            List<String> secondaryDeviceIps = secondaries.collect{it.value?.deviceIp}.findAll{it != null}
            List<String> secondaryIds = secondaries.collect{it.value?.id}.findAll{it != null}
            if(secondaryDeviceIps && secondaryIds) {
              cd.updateDataValue('secondaryDeviceIps', secondaryDeviceIps.join(','))
              cd.updateDataValue('secondaryIds', secondaryIds.join(','))
            }
          }
        }

        // Validate critical device data fields are populated
        List<String> criticalFields = ['id', 'deviceIp', 'localUpnpHost', 'localUpnpUrl', 'websocketUrl']
        List<String> missingFields = []
        criticalFields.each { field ->
          String value = cd.getDataValue(field)
          if(!value || value == 'null') {
            missingFields << field
          }
        }

        if(missingFields.size() > 0) {
          logWarn("Device ${cd.label} is missing critical data fields: ${missingFields.join(', ')}. This may cause issues.")
          logWarn("Player info available: ${playerInfo.keySet().join(', ')}")
          // Schedule a retry of secondaryConfiguration in case data gets populated later
          runIn(10, 'retryDeviceConfiguration', [data: [dni: dni]])
        } else {
          // Defer secondaryConfiguration to ensure all data values are committed
          runIn(1, 'runSecondaryConfiguration', [data: [dni: dni]])
        }
      } catch (Exception e) {
        logError("Failed to configure device ${dni}: ${e.message}")
      }
    } else if(!cd) {
      logWarn("Skipping device configuration for ${dni} - device not created")
    } else if(!playerInfo) {
      logWarn("Skipping device configuration for ${dni} - no player info available")
    }
  }
  // Note: removeOrphans() is now handled by the explicit Remove Players button
  return createdCount
}

void retryDeviceConfiguration(Map data) {
  String dni = data?.dni
  if(!dni) {
    logWarn('retryDeviceConfiguration called without DNI')
    return
  }

  ChildDeviceWrapper cd = app.getChildDevice(dni)
  if(!cd) {
    logWarn("Cannot retry configuration for ${dni} - device not found")
    return
  }

  Map playerInfo = discoveredSonoses[dni]
  if(!playerInfo) {
    logWarn("Cannot retry configuration for ${dni} - no player info available")
    return
  }

  logInfo("Retrying device configuration for ${cd.label}...")

  // Re-apply all device data
  playerInfo.each { key, value ->
    if(key != null && value != null) {
      String currentValue = cd.getDataValue(key as String)
      if(!currentValue || currentValue == 'null') {
        logDebug("Setting missing field ${key} = ${value}")
        cd.updateDataValue(key as String, value as String)
      }
    }
  }

  // Validate again
  List<String> criticalFields = ['id', 'deviceIp', 'localUpnpHost', 'localUpnpUrl', 'websocketUrl']
  List<String> stillMissing = []
  criticalFields.each { field ->
    String value = cd.getDataValue(field)
    if(!value || value == 'null') {
      stillMissing << field
    }
  }

  if(stillMissing.size() > 0) {
    logError("Device ${cd.label} still missing critical fields after retry: ${stillMissing.join(', ')}")
    logError("This device may not function correctly. Try deleting and re-discovering it.")
  } else {
    logInfo("Device ${cd.label} configuration completed successfully on retry")
    runIn(1, 'runSecondaryConfiguration', [data: [dni: dni]])
  }
}

void runSecondaryConfiguration(Map data) {
  String dni = data?.dni
  if(!dni) { return }
  ChildDeviceWrapper cd = app.getChildDevice(dni)
  if(cd) { cd.secondaryConfiguration() }
}

void removeOrphans() {
  // Single getChildDevices() call partitioned into groups and players
  // Copy the platform collection before deleting any child; Hubitat may return
  // a live list, and mutating it during iteration can skip devices or throw.
  List<ChildDeviceWrapper> allChildren = new ArrayList<ChildDeviceWrapper>(app.getChildDevices())
  List<String> configuredGroupDevices = state.userGroups instanceof Map
      ? getUserGroupsDNIsFromUserGroups()
      : normalizeNewUiGroupPlayerIds(settings.groupDevices)
  allChildren.each { child ->
    String id = child.getDataValue('id')
    String dni = child.getDeviceNetworkId()
    if(id == null) {
      // Group device
      if(!(dni in configuredGroupDevices)) {
        logInfo("Removing group device not found in selected devices list: ${child} (DNI: ${dni}; configured: ${configuredGroupDevices})")
        app.deleteChildDevice(dni)
      }
    } else if(!settings.skipOrphanRemoval) {
      // Player device
      if(!(dni in settings.playerDevices)) {
        logInfo("Removing player device not found in selected devices list: ${child}")
        app.deleteChildDevice(dni)
      }
    }
  }
}
// =============================================================================
// End Create Child Devices
// =============================================================================



// =============================================================================
// Local Discovery
//
// This section implements dual discovery methods for Sonos devices:
// 1. SSDP (Simple Service Discovery Protocol) - Legacy method, works on most networks
// 2. mDNS (Multicast DNS) - Modern method added in Hubitat 2.4.1+, more reliable
//
// Duplicate Prevention:
// - Devices are keyed by MAC address (primary identifier)
// - Additional checks prevent duplicates by playerId and IP address
// - Selection page deduplicates by playerId to handle edge cases
// =============================================================================
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
    discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
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

void stopDiscovery() {
  if(atomicState.discoveryRunning == true) {
    logInfo('Stopping discovery...')
  }
  // Set flags FIRST so all guard checks see the stop immediately
  atomicState.discoveryRunning = false
  atomicState.discoveryEndTime = null
  app.sendEvent(name: 'discoveryTimer', value: '0')
  // Clean up legacy state keys (migration from state → atomicState)
  state.remove('discoveryRunning')
  state.remove('discoveryEndTime')

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

  try {
    if(this.respondsTo('unregisterMDNSListener')) {
      unregisterMDNSListener('_sonos._tcp')
      unregisterMDNSListener('_http._tcp')
    }
  } catch(Exception e) { logTrace("Could not unregister mDNS listeners: ${e.message}") }
}

void updateDiscoveryTimer() {
  if(atomicState.discoveryRunning != true || !atomicState.discoveryEndTime) {
    logDebug("updateDiscoveryTimer: stopping - discoveryRunning=${atomicState.discoveryRunning}, discoveryEndTime=${atomicState.discoveryEndTime}")
    return
  }

  Integer remainingSecs = Math.max(0, (Integer)(((atomicState.discoveryEndTime as Long) - now()) / 1000))
  logTrace("updateDiscoveryTimer: sending event with ${remainingSecs} seconds remaining")
  app.sendEvent(name: 'discoveryTimer', value: "Discovery time remaining: ${remainingSecs} seconds")

  if(remainingSecs > 0) {
    runIn(1, 'updateDiscoveryTimer')
  } else {
    logDebug("updateDiscoveryTimer: timer reached 0, stopping updates")
  }
}

void extendDiscovery(Integer seconds) {
  logInfo("Extending discovery by ${seconds} seconds")
  if(atomicState.discoveryRunning != true) {
    // Restart discovery without clearing already-discovered devices
    subscribeToSsdpEvents(location)
    sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
    sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))
    startMdnsDiscovery()
    atomicState.discoveryRunning = true
    runIn(1, 'updateDiscoveryTimer')
  }
  // Add seconds to current remaining time (or from now if already expired)
  Long currentEnd = atomicState.discoveryEndTime ? (atomicState.discoveryEndTime as Long) : now()
  Long newEnd = Math.max(currentEnd, now()) + (seconds * 1000L)
  atomicState.discoveryEndTime = newEnd
  Long remainingMillis = newEnd - now()
  Integer totalRemaining = (Integer)(remainingMillis / 1000L)
  unschedule('stopDiscovery')
  runIn(totalRemaining, 'stopDiscovery')
}

String renderNewUiDiscoveryTimerMarkup() {
  if(atomicState.discoveryRunning == true && atomicState.discoveryEndTime) {
    Long endTime = atomicState.discoveryEndTime as Long
    Integer remainingSecs = Math.max(0, (Integer)((endTime - now()) / 1000))
    return "<b>Discovery is running: ${remainingSecs} seconds remaining.</b>"
  }
  return '<b>Discovery has stopped.</b>'
}

void ssdpDiscover() {
  logDebug("Starting SSDP Discovery...")
  discoveredSonoses = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
  discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
	sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
	sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))

  // Also start mDNS discovery
  startMdnsDiscovery()
}

void startMdnsDiscovery() {
  try {
    // Check if mDNS methods exist (requires Hubitat 2.4.1+)
    if(!this.respondsTo('registerMDNSListener')) {
      logDebug("mDNS not available on this hub firmware version")
      return
    }

    // Register mDNS listener for Sonos devices
    // Sonos uses _sonos._tcp for its mDNS service
    registerMDNSListener('_sonos._tcp')
    logDebug("Registered mDNS listener for Sonos devices (_sonos._tcp)")

    // Also listen for general device services that Sonos might broadcast
    registerMDNSListener('_http._tcp')
    logDebug("Registered mDNS listener for HTTP services (_http._tcp)")

    // Schedule processing of mDNS entries after a delay to allow discovery
    runIn(5, 'processMdnsDiscovery')
  } catch(Exception e) {
    logWarn("mDNS discovery not available or failed: ${e.message}")
  }
}

void processMdnsDiscovery() {
  if(atomicState.discoveryRunning != true) {
    logDebug("processMdnsDiscovery: discovery not running, skipping")
    return
  }
  try {
    logDebug("Processing mDNS discovered devices...")

    // Get all mDNS entries
    def mdnsEntries = getMDNSEntries()
    if(!mdnsEntries) {
      logDebug("No mDNS entries found")
      return
    }

    logDebug("Found ${mdnsEntries.size()} mDNS entries")

    mdnsEntries.each { entry ->
      // Filter for Sonos devices - they typically have 'Sonos' in the name or specific service types
      String serviceName = entry.name ?: ''
      String serviceType = entry.type ?: ''
      String ipAddress = entry.ipAddress ?: ''
      Integer port = entry.port ?: 0

      logTrace("mDNS entry: name=${serviceName}, type=${serviceType}, ip=${ipAddress}, port=${port}")

      // Check if this looks like a Sonos device
      if(serviceName.toLowerCase().contains('sonos') || serviceType == '_sonos._tcp') {
        logDebug("Found potential Sonos device via mDNS: ${serviceName} at ${ipAddress}")
        processDiscoveredSonosDevice(ipAddress)
      } else if(ipAddress && port == 1400) {
        // Port 1400 is Sonos UPnP port - worth checking
        logTrace("Found device on Sonos UPnP port 1400: ${ipAddress}")
        processDiscoveredSonosDevice(ipAddress)
      }
    }

    runIn(2, 'sendFoundSonosEvents')
  } catch(Exception e) {
    logWarn("Error processing mDNS discovery: ${e.message}")
  }
}

Boolean isIpAlreadyDiscovered(String ipAddress) {
  Boolean alreadyDiscovered = discoveredSonoses.values().any { it.deviceIp == ipAddress }
  Boolean alreadyDiscoveredSecondary = discoveredSonosSecondaries.values().any { it.deviceIp == ipAddress }
  return alreadyDiscovered || alreadyDiscoveredSecondary
}

void processDiscoveredSonosDevice(String ipAddress) {
  if(!ipAddress) { return }
  if(atomicState.discoveryRunning != true) {
    logDebug("processDiscoveredSonosDevice: discovery not running, skipping")
    return
  }

  if(isIpAlreadyDiscovered(ipAddress)) {
    logTrace("Device at ${ipAddress} already discovered, skipping")
    return
  }

  // Try to get player info
  LinkedHashMap playerInfo = getPlayerInfoLocalSync("${ipAddress}:1443")
  if(!playerInfo) {
    logTrace("Could not get player info for ${ipAddress}")
    return
  }

  GPathResult deviceDescription = getDeviceDescriptionLocalSync("${ipAddress}")
  if(!deviceDescription) {
    logTrace("Could not get device description for ${ipAddress}")
    return
  }

  // Process using the same logic as SSDP
  processSonosDeviceInfo(playerInfo, deviceDescription, ipAddress)
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
  if(atomicState.discoveryRunning != true) { return }
  LinkedHashMap parsedEvent = parseLanMessage(event?.description)
  processParsedSsdpEvent(parsedEvent)
}

@CompileStatic
void processParsedSsdpEvent(LinkedHashMap event) {
  String ipAddress = convertHexToIP(event?.networkAddress as String)
  String ipPort = convertHexToInt(event?.deviceAddress as String).toString()

  // Devices send multiple SSDP responses (and we subscribe to two STs) -- skip the
  // synchronous player info/description fetches for already-seen IPs.
  if(isIpAlreadyDiscovered(ipAddress)) {
    logTrace("Device at ${ipAddress} already discovered, skipping")
    return
  }

  LinkedHashMap playerInfo = getPlayerInfoLocalSync("${ipAddress}:1443")
  if(playerInfo) {
    logTrace("Discovered playerInfo for ${ipAddress}")
  } else {
    logTrace("Did not receive playerInfo for ${ipAddress}")
    return
  }

  GPathResult deviceDescription = getDeviceDescriptionLocalSync("${ipAddress}")
  if(deviceDescription) {
    logTrace("Discovered device description for ${ipAddress}")
  } else {
    logTrace("Did not receive device description for ${ipAddress}")
    return
  }

  processSonosDeviceInfo(playerInfo, deviceDescription, ipAddress)
}

@CompileStatic
void processSonosDeviceInfo(LinkedHashMap playerInfo, GPathResult deviceDescription, String ipAddress) {
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
    logTrace("Processing Sonos device: MAC=${mac}, name=${playerInfoDevice?.name}, IP=${ipAddress}")
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
    logTrace("Device already discovered in primaries (MAC match) - MAC: ${mac}, IP: ${ipAddress}")
  }
  if(!isDuplicate) {
    isDuplicate = discoveredSonoses.any { key, value ->
      LinkedHashMap existingDevice = value as LinkedHashMap
      existingDevice.id == playerId || existingDevice.deviceIp == ipAddress
    }
    if(isDuplicate) { logTrace("Device already discovered in primaries (IP/ID match) - MAC: ${mac}, playerId: ${playerId}, IP: ${ipAddress}") }
  }
  if(!isDuplicate) {
    isDuplicate = discoveredSonosSecondaries.containsKey(mac) || discoveredSonosSecondaries.any { key, value ->
      LinkedHashMap existingDevice = value as LinkedHashMap
      existingDevice.id == playerId || existingDevice.deviceIp == ipAddress
    }
    if(isDuplicate) { logTrace("Device already discovered in secondaries - MAC: ${mac}, playerId: ${playerId}, IP: ${ipAddress}") }
  }

  if(isDuplicate) { return }

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
    localUpnpHost: "${ipAddress}:1400"
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
      localUpnpHost: "${ipAddress}:1400"
    ]
    // Check for duplicate secondaries
    boolean secondaryExists = discoveredSonosSecondaries.values().any { secondaryDevice ->
      LinkedHashMap secondary = secondaryDevice as LinkedHashMap
      return secondary.id == playerId
    }
    if(!secondaryExists) {
      discoveredSonosSecondaries[mac] = discoveredSonosSecondary
      persistDiscoveredSonosSecondaries()
      logTrace("Found secondary ${modelName} (${playerId}) for primary ${playerInfoDevice?.primaryDeviceId}")
    }
    // Secondary devices should never appear in the main discovered list
    scheduleMethod(2, 'sendFoundSonosEvents')
    return
  }

  // Only primary (standalone) devices reach here
  if(discoveredSonos?.name != null && discoveredSonos?.name != 'null') {
    discoveredSonoses[mac] = discoveredSonos
    logInfo("Discovered Sonos device: ${playerName} (${modelName}) at ${ipAddress}")
    scheduleMethod(2, 'sendFoundSonosEvents')
  } else {
    logTrace("Device id:${discoveredSonos?.id} responded to discovery, but did not provide device name. This is expected for right channel speakers on stereo pairs, subwoofers, and other 'non primary' devices.")
  }
}

@CompileStatic
String getFoundSonoses() {
  String foundDevices = ''
  List<String> discoveredSonosesNames = discoveredSonoses.collect{Object k, Object v -> ((LinkedHashMap)v)?.name as String }
  discoveredSonosSecondaries.each{ Object k, Object v ->
    LinkedHashMap secondary = (LinkedHashMap)v
    String primaryDeviceId = secondary?.primaryDeviceId as String
    String modelName = secondary?.modelDisplayName ?: secondary?.modelName ?: 'Secondary'
    if(primaryDeviceId) {
      String primaryDNI = getDNIFromRincon(primaryDeviceId)
      if(discoveredSonoses.containsKey(primaryDNI)) {
        LinkedHashMap primary = (LinkedHashMap)discoveredSonoses[primaryDNI]
        discoveredSonosesNames.add("${primary?.name as String} (${modelName})".toString())
      }
    }
  }
  discoveredSonosesNames.sort()
  discoveredSonosesNames.each{ discoveredSonos -> foundDevices += "\n${discoveredSonos}" }
  return foundDevices
}

void sendFoundSonosEvents() {
  if(atomicState.discoveryRunning != true) {
    logDebug("sendFoundSonosEvents: discovery not running, skipping")
    app.sendEvent(name: 'newUiSpeakerTable', value: 'updated')
    return
  }
  app.sendEvent(name: 'sonosDiscoveredCount', value: "Found Devices (${discoveredSonoses.size()} primary, ${discoveredSonosSecondaries.size()} secondary): ")
  app.sendEvent(name: 'sonosDiscovered', value: getFoundSonoses())
  app.sendEvent(name: 'newUiSpeakerTable', value: 'updated')
}
// =============================================================================
// End Local Discovery
// =============================================================================



// =============================================================================
// Helper methods
// =============================================================================

String processServerSideRender(Map event) {
  if(event?.name == 'discoveryTimer') {
    return renderNewUiDiscoveryTimerMarkup()
  }
  if(event?.name == 'newUiSpeakerTable') {
    return "<div id='new-ui-speaker-table-wrapper'>${renderNewUiSpeakerTableMarkup()}</div>"
  }
  if(event?.name == NEW_UI_GROUP_TABLE_EVENT) {
    return "<div id='new-ui-group-table-wrapper'>${renderNewUiGroupTableMarkup()}</div>"
  }
  return ''
}

/**
 * Wrapper for runIn() to allow calls from @CompileStatic methods.
 * The dynamic method lookup for runIn() is incompatible with static compilation.
 */
void scheduleMethod(Integer seconds, String methodName) {
  runIn(seconds, methodName)
}

@CompileStatic
String getDNIFromRincon(String rincon) {
  return rincon.tokenize('_')[1][0..-6]
}

/**
 * Build a Map of RINCON ID to ChildDeviceWrapper for O(1) lookups.
 * Single traversal of child devices instead of per-lookup traversal.
 */
Map<String, ChildDeviceWrapper> buildRinconMap() {
  Map<String, ChildDeviceWrapper> rinconMap = [:]
  app.getChildDevices().each { child ->
    String id = child.getDataValue('id')
    if(id) { rinconMap[id] = child }
  }
  return rinconMap
}

/**
 * Single-call RINCON lookup. Uses find() with early exit instead of
 * building a full player list then searching it.
 */
ChildDeviceWrapper getDeviceFromRincon(String rincon) {
  return app.getChildDevices().find { it.getDataValue('id') == rincon }
}

/**
 * Queue a child event so player callbacks never synchronously fan out into
 * another child device. The queue is keyed by target DNI and attribute, so a
 * burst of state changes keeps only the newest value for each attribute.
 */
void sendChildEvent(String dni, Map event) {
  if(!dni || !event?.name) {
    return
  }
  Map<String, Map<String, Object>> pending = state[PENDING_CHILD_EVENT_QUEUE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_CHILD_EVENT_QUEUE_KEY]
      : [:]
  Map<String, Object> childEvents = pending[dni] instanceof Map
      ? pending[dni]
      : [:]
  childEvents[event.name as String] = event.value
  pending[dni] = childEvents
  state[PENDING_CHILD_EVENT_QUEUE_KEY] = pending
  runIn(1, 'drainQueuedChildEvents', [overwrite: true])
}

/**
 * Deliver queued child events after the originating player callback has
 * returned. This method intentionally performs only the final child event
 * delivery and never calls back into a player or group command.
 */
void drainQueuedChildEvents() {
  Map<String, Map<String, Object>> pending = state[PENDING_CHILD_EVENT_QUEUE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_CHILD_EVENT_QUEUE_KEY]
      : [:]
  state.remove(PENDING_CHILD_EVENT_QUEUE_KEY)
  if(pending.isEmpty()) {
    return
  }

  pending.each { String dni, Map<String, Object> eventsForChild ->
    ChildDeviceWrapper child = getChildDevice(dni)
    if(child == null) {
      logWarn("drainQueuedChildEvents: no child device found for DNI ${dni}")
      return
    }
    eventsForChild.each { String name, Object value ->
      if(value != null) {
        child.sendEvent(name: name, value: value)
      }
    }
  }
}

/**
 * Receive a command request emitted by a Sonos Advanced Group device. Group
 * drivers deliberately publish requests instead of synchronously traversing
 * back through the parent app. The app owns child-device lookup and forwards
 * only to player devices, so this handler cannot form an app -> group -> app
 * call cycle.
 */
void groupCommandRequestHandler(Event event) {
  String rawRequest = event?.value?.toString()
  if(!rawRequest) {
    logWarn('Ignoring empty group command request')
    return
  }

  Map request
  try {
    request = (Map)parseJson(rawRequest)
  } catch(Exception e) {
    logWarn("Ignoring malformed group command request: ${e.message}")
    return
  }

  String groupDni = request?.groupDni as String
  String command = request?.command as String
  if(!groupDni || !command || getChildDevice(groupDni) == null) {
    logWarn("Ignoring group command request with invalid target or command: ${rawRequest}")
    return
  }

  // Requests from a group device can arrive in the same scheduler tick as a
  // Favorite request. Drain them in request-sequence order so an explicit or
  // additive grouping request is attached to the following Favorite load
  // instead of racing it.
  Map<String, List<Map>> pending = state[GROUP_COMMAND_QUEUE_STATE_KEY] instanceof Map
      ? (Map<String, List<Map>>)state[GROUP_COMMAND_QUEUE_STATE_KEY]
      : [:]
  List<Map> groupRequests = pending[groupDni] instanceof List
      ? (List<Map>)pending[groupDni]
      : []
  groupRequests.add(request)
  pending[groupDni] = groupRequests
  state[GROUP_COMMAND_QUEUE_STATE_KEY] = pending
  runIn(1, 'drainGroupCommandRequests', [overwrite: true])
}

void drainGroupCommandRequests() {
  Map<String, List<Map>> pending = state[GROUP_COMMAND_QUEUE_STATE_KEY] instanceof Map
      ? (Map<String, List<Map>>)state[GROUP_COMMAND_QUEUE_STATE_KEY]
      : [:]
  state.remove(GROUP_COMMAND_QUEUE_STATE_KEY)
  pending.each { String groupDni, List<Map> requests ->
    List<Map> ordered = (requests ?: []).sort { Map request -> groupCommandRequestSequence(request) }
    ordered.each { Map request -> processGroupCommandRequest([request: request]) }
  }
}

Integer groupCommandRequestSequence(Map request) {
  String requestId = request?.requestId as String
  if(!requestId) { return Integer.MAX_VALUE }
  String sequenceText = requestId.contains('-')
      ? requestId.substring(requestId.lastIndexOf('-') + 1)
      : requestId
  try {
    return sequenceText.toInteger()
  } catch(Exception ignored) {
    return Integer.MAX_VALUE
  }
}

void processGroupCommandRequest(Map data) {
  Map request = data?.request instanceof Map ? (Map)data.request : null
  String groupDni = request?.groupDni as String
  String command = request?.command as String
  if(!groupDni || !command) {
    return
  }

  ChildDeviceWrapper groupDevice = getChildDevice(groupDni)
  if(groupDevice == null) {
    logWarn("Group command ${command} ignored because ${groupDni} no longer exists")
    return
  }

  Map args = request.args instanceof Map ? (Map)request.args : [:]
  String requestId = request.requestId as String
  logDebug("Processing group command ${command} for ${groupDni}${requestId ? " (${requestId})" : ''}")

  if(isGroupOperationCommand(command)) {
    Map activeOperation = getActiveGroupOperation()
    if(activeOperation && activeOperation.groupDni as String != groupDni) {
      deferGroupCommandRequest(request)
      return
    }
  }

  try {
    Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
    String coordinatorId = groupDevice.getDataValue('groupCoordinatorId')
    List<String> playerIds = getAllPlayersForGroupDevice(groupDevice).unique()
    ChildDeviceWrapper configuredCoordinator = coordinatorId ? rinconMap[coordinatorId] : null
    ChildDeviceWrapper coordinator = resolveGroupCommandCoordinator(configuredCoordinator, playerIds, rinconMap)
    String resolvedCoordinatorId = coordinator?.getDataValue('id') ?: coordinatorId
    List<String> followerIds = playerIds.findAll { String id -> id != resolvedCoordinatorId }
    List<ChildDeviceWrapper> playerDevices = playerIds.collect { String id -> rinconMap[id] }.findAll { it != null }

    switch(command) {
      case 'on':
      case 'evictUnlistedPlayers':
      case 'regroupAfterUngroup':
      case 'createGroup':
      case 'groupPlayers':
        startGroupTopologyOperation(groupDevice, GROUPING_MODE_EXPLICIT, args)
        break

      case 'off':
      case 'removePlayersFromCoordinator':
      case 'ungroupPlayers':
        cancelActiveGroupOperation(groupDni, "${command.toUpperCase()}_REQUESTED")
        if(coordinator && followerIds) {
          coordinator.playerModifyGroupMembers([], followerIds)
        } else if(!coordinator) {
          logWarn("Group command ${command} could not resolve coordinator ${coordinatorId}")
        }
        break

      case 'joinPlayersToCoordinator':
        startGroupTopologyOperation(groupDevice, GROUPING_MODE_ADDITIVE, args)
        break

      case 'refresh':
        refreshGroupDeviceState(groupDevice, coordinator, playerDevices, args)
        break

      case 'playAudioClip':
        cancelActiveGroupOperation(groupDni, 'AUDIO_CLIP_REQUESTED')
        playerDevices.each { ChildDeviceWrapper player ->
          player.playerLoadAudioClip(args.uri as String, toBigDecimal(args.volume))
        }
        break

      case 'playHighPriorityTrack':
        cancelActiveGroupOperation(groupDni, 'HIGH_PRIORITY_TRACK_REQUESTED')
        playerDevices.each { ChildDeviceWrapper player ->
          player.playerLoadAudioClipHighPriority(args.uri as String, toBigDecimal(args.volume))
        }
        break

      case 'enqueueLowPriorityTrack':
        cancelActiveGroupOperation(groupDni, 'LOW_PRIORITY_TRACK_REQUESTED')
        playerDevices.each { ChildDeviceWrapper player ->
          player.playerLoadAudioClip(args.uri as String, toBigDecimal(args.volume))
        }
        break

      case 'play':
        cancelActiveGroupOperation(groupDni, 'PLAY_REQUESTED')
        if(coordinator) { coordinator.play() }
        break
      case 'pause':
        cancelActiveGroupOperation(groupDni, 'PAUSE_REQUESTED')
        if(coordinator) { coordinator.pause() }
        break
      case 'stop':
        cancelActiveGroupOperation(groupDni, 'STOP_REQUESTED')
        if(coordinator) { coordinator.stop() }
        break
      case 'nextTrack':
        cancelActiveGroupOperation(groupDni, 'NEXT_TRACK_REQUESTED')
        if(coordinator) { coordinator.nextTrack() }
        break
      case 'previousTrack':
        cancelActiveGroupOperation(groupDni, 'PREVIOUS_TRACK_REQUESTED')
        if(coordinator) { coordinator.previousTrack() }
        break
      case 'playTrack':
        cancelActiveGroupOperation(groupDni, 'PLAY_TRACK_REQUESTED')
        if(coordinator) { coordinator.playTrack(args.uri as String, toBigDecimal(args.volume)) }
        break
      case 'setTrack':
        cancelActiveGroupOperation(groupDni, 'SET_TRACK_REQUESTED')
        if(coordinator) { coordinator.setTrack(args.uri as String) }
        break

      case 'setVolume':
        executeGroupVolumeCommand(coordinator, playerDevices, args)
        break
      case 'volumeUp':
        executeGroupRelativeVolumeCommand(coordinator, playerDevices, args, 1)
        break
      case 'volumeDown':
        executeGroupRelativeVolumeCommand(coordinator, playerDevices, args, -1)
        break
      case 'mute':
        executeGroupMuteCommand(coordinator, playerDevices, args, true)
        break
      case 'unmute':
        executeGroupMuteCommand(coordinator, playerDevices, args, false)
        break

      case 'setRepeatMode':
        if(coordinator) { coordinator.setRepeatMode(args.mode as String) }
        break
      case 'repeatOne':
        if(coordinator) { coordinator.repeatOne() }
        break
      case 'repeatAll':
        if(coordinator) { coordinator.repeatAll() }
        break
      case 'repeatNone':
        if(coordinator) { coordinator.repeatNone() }
        break
      case 'setShuffle':
        if(coordinator) { coordinator.setShuffle(args.mode as String) }
        break
      case 'shuffleOn':
        if(coordinator) { coordinator.shuffleOn() }
        break
      case 'shuffleOff':
        if(coordinator) { coordinator.shuffleOff() }
        break
      case 'setCrossfade':
        if(coordinator) { coordinator.setCrossfade(args.mode as String) }
        break
      case 'enableCrossfade':
        if(coordinator) { coordinator.enableCrossfade() }
        break
      case 'disableCrossfade':
        if(coordinator) { coordinator.disableCrossfade() }
        break
      case 'getFavorites':
        if(coordinator) { coordinator.getFavorites() }
        break
      case 'loadFavorite':
        startGroupFavoriteOperation(groupDevice, [
          favoriteId: args.favoriteId as String,
          repeatMode: 'off',
          queueMode: 'replace',
          shuffleMode: 'off',
          autoPlay: 'true',
          crossfadeMode: 'on'
        ])
        break
      case 'loadFavoriteFull':
        startGroupFavoriteOperation(groupDevice, [
          favoriteId: args.favoriteId as String,
          repeatMode: args.repeatMode as String,
          queueMode: args.queueMode as String,
          shuffleMode: args.shuffleMode as String,
          autoPlay: args.autoPlay as String,
          crossfadeMode: args.crossfadeMode as String
        ])
        break
      case 'getPlaylists':
        if(coordinator) { coordinator.getPlaylists() }
        break
      case 'loadPlaylist':
        cancelActiveGroupOperation(groupDni, 'PLAYLIST_REQUESTED')
        if(coordinator) { coordinator.loadPlaylist(args.playlistId as String) }
        break
      case 'loadPlaylistFull':
        cancelActiveGroupOperation(groupDni, 'PLAYLIST_REQUESTED')
        if(coordinator) {
          coordinator.loadPlaylistFull(
            args.playlistId as String,
            args.repeatMode as String,
            args.queueMode as String,
            args.shuffleMode as String,
            args.autoPlay as String,
            args.crossfadeMode as String
          )
        }
        break
      default:
        logWarn("Unsupported group command '${command}'")
    }
  } catch(Exception e) {
    logWarn("Group command ${command} failed for ${groupDni}: ${e.message}")
  }
}

Boolean isGroupOperationCommand(String command) {
  return command in [
    'on', 'groupPlayers', 'createGroup', 'regroupAfterUngroup',
    'evictUnlistedPlayers', 'joinPlayersToCoordinator',
    'loadFavorite', 'loadFavoriteFull',
    'off', 'removePlayersFromCoordinator', 'ungroupPlayers'
  ]
}

Map getActiveGroupOperation() {
  return state[GROUP_OPERATION_STATE_KEY] instanceof Map
      ? (Map)state[GROUP_OPERATION_STATE_KEY]
      : null
}

void saveActiveGroupOperation(Map operation) {
  if(operation != null) {
    state[GROUP_OPERATION_STATE_KEY] = operation
  }
}

String newGroupOperationId() {
  Integer sequence = ((state.groupOperationSequence ?: 0) as Integer) + 1
  state.groupOperationSequence = sequence
  return "${app.id ?: 'sonos'}-${now()}-${sequence}"
}

String normalizeGroupOperationMode(Object value, String defaultMode = GROUPING_MODE_CURRENT) {
  String normalized = value?.toString()?.trim()?.toUpperCase()
  if(normalized in [GROUPING_MODE_EXPLICIT, GROUPING_MODE_ADDITIVE, GROUPING_MODE_CURRENT]) {
    return normalized
  }
  return defaultMode
}

String normalizeGroupOperationValue(Object value, String defaultValue) {
  String normalized = value?.toString()?.trim()
  return normalized ? normalized : defaultValue
}

Map normalizeGroupFavoriteSpec(Map rawSpec) {
  Map spec = rawSpec ?: [:]
  return [
    favoriteId: spec.favoriteId?.toString(),
    repeatMode: normalizeGroupOperationValue(spec.repeatMode, 'off').toLowerCase(),
    queueMode: normalizeGroupOperationValue(spec.queueMode, 'replace').toLowerCase(),
    shuffleMode: normalizeGroupOperationValue(spec.shuffleMode, 'off').toLowerCase(),
    autoPlay: normalizeGroupOperationValue(spec.autoPlay, 'true').toLowerCase(),
    crossfadeMode: normalizeGroupOperationValue(spec.crossfadeMode, 'on').toLowerCase()
  ]
}

Map getFreshGroupingContext(String groupDni) {
  Map contexts = state[GROUPING_CONTEXTS_STATE_KEY] instanceof Map
      ? (Map)state[GROUPING_CONTEXTS_STATE_KEY]
      : [:]
  Map context = contexts[groupDni] instanceof Map ? (Map)contexts[groupDni] : null
  if(context == null) { return null }
  Long expiresAt = context.expiresAt as Long
  if(expiresAt != null && expiresAt < now()) {
    contexts.remove(groupDni)
    state[GROUPING_CONTEXTS_STATE_KEY] = contexts
    return null
  }
  return context
}

void storeGroupingContext(Map operation) {
  if(operation == null || !operation.groupDni || !operation.groupingMode || operation.favoriteId) {
    return
  }
  Map contexts = state[GROUPING_CONTEXTS_STATE_KEY] instanceof Map
      ? (Map)state[GROUPING_CONTEXTS_STATE_KEY]
      : [:]
  contexts[operation.groupDni as String] = [
    mode: operation.groupingMode as String,
    requiredPlayerIds: normalizeGroupPlayerIds(operation.requiredPlayerIds),
    coordinatorId: operation.resolvedCoordinatorId as String,
    expiresAt: now() + GROUPING_CONTEXT_TTL_SECONDS * 1000L
  ]
  state[GROUPING_CONTEXTS_STATE_KEY] = contexts
}

void deferGroupCommandRequest(Map request) {
  if(!(request instanceof Map)) { return }
  List<Map> deferred = state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_GROUP_COMMANDS_STATE_KEY]
      : []
  deferred.add(request)
  state[DEFERRED_GROUP_COMMANDS_STATE_KEY] = deferred
  logInfo("Deferring group command ${request.command} for ${request.groupDni} until the current group operation completes")
}

void drainDeferredGroupOperationCommands() {
  if(getActiveGroupOperation() != null) {
    return
  }
  List<Map> deferred = state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_GROUP_COMMANDS_STATE_KEY]
      : []
  state.remove(DEFERRED_GROUP_COMMANDS_STATE_KEY)
  if(deferred.isEmpty()) { return }

  Map nextRequest = deferred.remove(0)
  if(!deferred.isEmpty()) {
    state[DEFERRED_GROUP_COMMANDS_STATE_KEY] = deferred
  }
  processGroupCommandRequest([request: nextRequest])
  if(state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List && !getActiveGroupOperation()) {
    runIn(1, 'drainDeferredGroupOperationCommands', [overwrite: true])
  }
}

List<String> normalizeGroupPlayerIds(Object raw) {
  List<String> ids = []
  if(raw instanceof Collection) {
    ((Collection)raw).each { Object value ->
      String id = value?.toString()?.trim()
      if(id) { ids.add(id) }
    }
  } else if(raw != null) {
    raw.toString().split(',').each { String value ->
      String id = value?.trim()
      if(id) { ids.add(id) }
    }
  }
  return ids.unique().sort()
}

Object getGroupOperationChildValue(ChildDeviceWrapper player, String name) {
  if(player == null) { return null }
  Object value = player.getDataValue(name)
  if(value == null || value.toString().trim() == '') {
    try {
      value = player.currentValue(name, true)
    } catch(Exception ignored) {
      value = player.currentValue(name)
    }
  }
  return value
}

Map buildGroupOperationTarget(ChildDeviceWrapper groupDevice, String groupingMode) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> requiredPlayerIds = getAllPlayersForGroupDevice(groupDevice).collect { String id -> id?.trim() }
      .findAll { String id -> id }
      .unique()
      .sort()
  String configuredCoordinatorId = groupDevice.getDataValue('groupCoordinatorId') as String
  ChildDeviceWrapper configuredCoordinator = configuredCoordinatorId ? rinconMap[configuredCoordinatorId] : null
  ChildDeviceWrapper activeCoordinator = resolveGroupCommandCoordinator(configuredCoordinator, requiredPlayerIds, rinconMap)
  ChildDeviceWrapper selectedCoordinator
  if(groupingMode == GROUPING_MODE_EXPLICIT) {
    selectedCoordinator = configuredCoordinator ?: activeCoordinator
  } else {
    // Additive/current operations continue using the live coordinator when
    // one of the configured players identifies it. They never change the
    // coordinator merely to satisfy the configured group definition.
    selectedCoordinator = activeCoordinator ?: configuredCoordinator
  }
  String selectedId = selectedCoordinator?.getDataValue('id') as String
  return [
    requiredPlayerIds: requiredPlayerIds,
    configuredCoordinatorId: configuredCoordinatorId,
    desiredCoordinatorId: configuredCoordinatorId ?: selectedId,
    resolvedCoordinatorId: selectedId,
    missingPlayerIds: requiredPlayerIds.findAll { String id -> rinconMap[id] == null },
    playerIdsWithDevices: requiredPlayerIds.findAll { String id -> rinconMap[id] != null }
  ]
}

Map buildNewGroupOperation(ChildDeviceWrapper groupDevice, String groupingMode, Map target,
    Map favoriteSpec = null) {
  Long startTime = now()
  Map operation = [
    operationId: newGroupOperationId(),
    groupDni: groupDevice.getDeviceNetworkId(),
    groupingMode: groupingMode,
    requiredPlayerIds: target.requiredPlayerIds ?: [],
    desiredCoordinatorId: target.desiredCoordinatorId,
    resolvedCoordinatorId: target.resolvedCoordinatorId,
    allowExtraPlayers: groupingMode != GROUPING_MODE_EXPLICIT,
    mutateTopology: groupingMode != GROUPING_MODE_CURRENT,
    phase: 'ENSURE_GROUP',
    status: 'ENSURING_TOPOLOGY',
    createdAt: startTime,
    deadlineAt: startTime + GROUP_OPERATION_DEADLINE_SECONDS * 1000L,
    topologyAttempt: 0,
    favoriteAttempt: 0,
    playAttempt: 0,
    stableObservations: 0,
    topologyFingerprint: null,
    topologyRefreshAt: startTime,
    topologyObservations: [:],
    lastCommandAt: 0L,
    lastCommand: null,
    registeredPlayerIds: [],
    observedPlayerIds: [],
    observedGroupId: null,
    observedCoordinatorId: null,
    lastTopologyObservations: [],
    loadAcknowledged: false,
    loadErrorCode: null,
    loadErrorReason: null,
    playbackState: null,
    favoriteCommandAt: 0L,
    favoriteAttemptId: null,
    favoriteExpectedGroupId: null,
    favoriteExpectedCoordinatorId: null,
    favoriteTopologyRefreshAt: 0L,
    favoriteTopologyObservations: [:],
    favoriteTopologyAttempt: 0,
    favoriteTopologyStableObservations: 0,
    favoriteTopologyLastObservedAt: 0L,
    favoriteTopologyFingerprint: null,
    favoriteTopologyVerified: false,
    playerPlaybackStates: [:],
    metadataConfirmed: false,
    exactFavoriteId: null
  ]
  if(favoriteSpec != null) {
    operation.putAll(normalizeGroupFavoriteSpec(favoriteSpec))
  }
  return operation
}

void startGroupTopologyOperation(ChildDeviceWrapper groupDevice, String groupingMode, Map args = [:]) {
  String groupDni = groupDevice?.getDeviceNetworkId()
  if(!groupDni) { return }
  String normalizedMode = normalizeGroupOperationMode(groupingMode, GROUPING_MODE_EXPLICIT)
  Map active = getActiveGroupOperation()
  if(active && active.groupDni as String == groupDni) {
    if(!active.favoriteId && active.groupingMode == normalizedMode && active.phase != 'COMPLETE') {
      runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
        overwrite: true,
        data: [operationId: active.operationId]
      ])
      return
    }
    cancelActiveGroupOperation(groupDni, 'SUPERSEDED_BY_GROUPING_REQUEST')
  }

  Map target = buildGroupOperationTarget(groupDevice, normalizedMode)
  if(!target.requiredPlayerIds || target.requiredPlayerIds.isEmpty()) {
    logWarn("Group operation for ${groupDni} has no configured players")
    return
  }
  if(target.missingPlayerIds && !target.missingPlayerIds.isEmpty()) {
    logWarn("Group operation for ${groupDni} is missing child devices for ${target.missingPlayerIds}")
  }

  Map operation = buildNewGroupOperation(groupDevice, normalizedMode, target)
  saveActiveGroupOperation(operation)
  registerGroupFavoriteOperationOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void startGroupFavoriteOperation(ChildDeviceWrapper groupDevice, Map rawSpec) {
  String groupDni = groupDevice?.getDeviceNetworkId()
  Map favoriteSpec = normalizeGroupFavoriteSpec(rawSpec)
  if(!groupDni || !favoriteSpec.favoriteId) {
    logWarn("Ignoring group Favorite request for ${groupDni ?: 'unknown group'} without a Favorite ID")
    return
  }

  Map active = getActiveGroupOperation()
  if(active && active.groupDni as String == groupDni) {
    if(!active.favoriteId && active.groupingMode in [GROUPING_MODE_EXPLICIT, GROUPING_MODE_ADDITIVE]) {
      attachFavoriteToGroupOperation(active, favoriteSpec)
      return
    }
    if(active.favoriteId?.toString() == favoriteSpec.favoriteId?.toString() && active.phase != 'COMPLETE') {
      return
    }
    cancelActiveGroupOperation(groupDni, 'SUPERSEDED_BY_FAVORITE_REQUEST')
  }

  Map context = getFreshGroupingContext(groupDni)
  String contextMode = normalizeGroupOperationMode(context?.mode, GROUPING_MODE_CURRENT)
  String deviceMode = normalizeGroupOperationMode(groupDevice.currentValue('groupingMode', true), GROUPING_MODE_CURRENT)
  String groupingMode = contextMode != GROUPING_MODE_CURRENT ? contextMode : deviceMode
  Map target = buildGroupOperationTarget(groupDevice, groupingMode)
  if(!target.requiredPlayerIds || target.requiredPlayerIds.isEmpty()) {
    logWarn("Group Favorite ${favoriteSpec.favoriteId} has no configured players for ${groupDni}")
    return
  }

  Map operation = buildNewGroupOperation(groupDevice, groupingMode, target, favoriteSpec)
  saveActiveGroupOperation(operation)
  registerGroupFavoriteOperationOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void attachFavoriteToGroupOperation(Map operation, Map rawSpec) {
  if(operation == null || operation.favoriteId) { return }
  Map favoriteSpec = normalizeGroupFavoriteSpec(rawSpec)
  if(!favoriteSpec.favoriteId) { return }
  operation.putAll(favoriteSpec)
  operation.favoriteAttempt = 0
  operation.playAttempt = 0
  operation.loadAcknowledged = false
  operation.loadErrorCode = null
  operation.loadErrorReason = null
  operation.playbackState = null
  operation.favoriteCommandAt = now()
  operation.favoriteAttemptId = null
  operation.favoriteExpectedGroupId = null
  operation.favoriteExpectedCoordinatorId = null
  operation.favoriteTopologyRefreshAt = 0L
  operation.favoriteTopologyObservations = [:]
  operation.favoriteTopologyAttempt = 0
  operation.favoriteTopologyStableObservations = 0
  operation.favoriteTopologyLastObservedAt = 0L
  operation.favoriteTopologyFingerprint = null
  operation.favoriteTopologyVerified = false
  operation.playerPlaybackStates = [:]
  operation.metadataConfirmed = false
  operation.exactFavoriteId = null
  operation.deadlineAt = now() + GROUP_OPERATION_DEADLINE_SECONDS * 1000L
  saveActiveGroupOperation(operation)
  registerGroupFavoriteOperationOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void registerGroupFavoriteOperationOnPlayers(Map operation, Collection ids) {
  if(operation == null || !operation.operationId) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> registered = normalizeGroupPlayerIds(operation.registeredPlayerIds)
  normalizeGroupPlayerIds(ids).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player == null || registered.contains(playerId)) { return }
    registered.add(playerId)
    operation.registeredPlayerIds = registered
    saveActiveGroupOperation(operation)
    try {
      player.registerGroupFavoriteOperation(operation.operationId as String, operation.favoriteId as String)
    } catch(Exception e) {
      logWarn("Could not register group Favorite operation ${operation.operationId} on ${playerId}: ${e.message}")
    }
  }
}

void setGroupFavoriteOperationAttemptOnPlayers(Map operation, Collection ids) {
  if(operation == null || !operation.operationId || !operation.favoriteAttemptId) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  normalizeGroupPlayerIds(ids).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try {
        player.setGroupFavoriteOperationAttempt(
          operation.operationId as String,
          operation.favoriteAttemptId as String,
          operation.favoriteExpectedGroupId as String
        )
      } catch(Exception e) {
        logDebug("Could not update Favorite attempt ${operation.favoriteAttemptId} on ${playerId}: ${e.message}")
      }
    }
  }
}

Map readGroupTopologyFromObservations(Map operation, Map recordedObservations, Long refreshAt) {
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation?.requiredPlayerIds)
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  Map observationsByPlayer = recordedObservations instanceof Map ? recordedObservations : [:]
  Long observationRefreshAt = refreshAt ?: 0L
  List<Map> observations = []
  requiredPlayerIds.each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    Map recorded = observationsByPlayer[playerId] instanceof Map
        ? (Map)observationsByPlayer[playerId]
        : null
    Long observedAt = recorded?.observedAt as Long
    Boolean fresh = recorded != null && observedAt != null && observedAt >= observationRefreshAt
    String groupId = fresh
        ? recorded.groupId?.toString()
        : getGroupOperationChildValue(player, 'groupId')?.toString()
    String coordinatorId = fresh
        ? recorded.coordinatorId?.toString()
        : getGroupOperationChildValue(player, 'groupCoordinatorId')?.toString()
    List<String> playerIds = fresh
        ? normalizeGroupPlayerIds(recorded.playerIds)
        : normalizeGroupPlayerIds(getGroupOperationChildValue(player, 'groupPlayerIds'))
    if(player == null) {
      groupId = null
      coordinatorId = null
      playerIds = []
    }
    observations.add([
      playerId: playerId,
      groupId: groupId,
      coordinatorId: coordinatorId,
      playerIds: playerIds,
      fresh: fresh,
      observedAt: observedAt
    ])
  }

  Boolean fresh = !observations.isEmpty() && observations.every { Map item -> item.fresh == true }
  Boolean complete = fresh && observations.every { Map item ->
    item.groupId && item.coordinatorId && item.playerIds && !item.playerIds.isEmpty()
  }
  Boolean consistent = complete && observations.every { Map item ->
    item.groupId == observations[0].groupId &&
      item.coordinatorId == observations[0].coordinatorId &&
      normalizeGroupPlayerIds(item.playerIds) == normalizeGroupPlayerIds(observations[0].playerIds)
  }
  Map first = observations ? observations[0] : [:]
  String fingerprint = observations.collect { Map item ->
    "${item.playerId}:${item.groupId ?: ''}:${item.coordinatorId ?: ''}:${normalizeGroupPlayerIds(item.playerIds).join(',')}"
  }.join('|')
  return [
    fresh: fresh,
    consistent: consistent,
    groupId: consistent ? first.groupId as String : null,
    coordinatorId: consistent ? first.coordinatorId as String : null,
    playerIds: consistent ? normalizeGroupPlayerIds(first.playerIds) : [],
    observations: observations,
    fingerprint: fingerprint
  ]
}

Map readGroupOperationTopology(Map operation) {
  Map observations = operation?.topologyObservations instanceof Map
      ? (Map)operation.topologyObservations
      : [:]
  Long refreshAt = (operation?.topologyRefreshAt ?: 0L) as Long
  return readGroupTopologyFromObservations(operation, observations, refreshAt)
}

Map readFavoriteGroupTopology(Map operation) {
  Map observations = operation?.favoriteTopologyObservations instanceof Map
      ? (Map)operation.favoriteTopologyObservations
      : [:]
  Long refreshAt = (operation?.favoriteTopologyRefreshAt ?: 0L) as Long
  return readGroupTopologyFromObservations(operation, observations, refreshAt)
}

String summarizeGroupOperationTopology(Map topology) {
  List<Map> observations = topology?.observations instanceof List
      ? (List<Map>)topology.observations
      : []
  if(observations.isEmpty()) { return 'No configured player topology was available.' }
  return observations.collect { Map item ->
    "${item.playerId}: group=${item.groupId ?: 'unknown'}, coordinator=${item.coordinatorId ?: 'unknown'}, members=${normalizeGroupPlayerIds(item.playerIds).join(',') ?: 'unknown'}"
  }.join('; ')
}

Boolean isGroupTopologySatisfied(Map operation, Map topology) {
  if(operation == null || topology?.consistent != true) { return false }
  Set<String> required = new HashSet<String>(normalizeGroupPlayerIds(operation.requiredPlayerIds))
  Set<String> observed = new HashSet<String>(normalizeGroupPlayerIds(topology.playerIds))
  if(required.isEmpty() || !topology.coordinatorId || !topology.groupId) { return false }
  if(operation.groupingMode == GROUPING_MODE_EXPLICIT) {
    String desiredCoordinatorId = operation.desiredCoordinatorId as String
    return required == observed && topology.coordinatorId == desiredCoordinatorId
  }
  // ADDITIVE and CURRENT deliberately accept extra members. The actual
  // coordinator is observed rather than changed by these modes.
  return observed.containsAll(required)
}

void updateGroupTopologyStability(Map operation, Map topology) {
  Boolean satisfied = isGroupTopologySatisfied(operation, topology)
  if(!satisfied) {
    operation.stableObservations = 0
    operation.topologyFingerprint = null
    return
  }
  String fingerprint = topology.fingerprint as String
  if(fingerprint && fingerprint == operation.topologyFingerprint) {
    operation.stableObservations = ((operation.stableObservations ?: 0) as Integer) + 1
  } else {
    operation.topologyFingerprint = fingerprint
    operation.stableObservations = 1
  }
}

void updateFavoriteGroupTopologyStability(Map operation, Map topology) {
  Boolean satisfied = isFavoriteGroupTopologySatisfied(operation, topology)
  if(!satisfied) {
    operation.favoriteTopologyStableObservations = 0
    operation.favoriteTopologyLastObservedAt = 0L
    operation.favoriteTopologyFingerprint = null
    return
  }

  String fingerprint = topology.fingerprint as String
  Long latestObservedAt = topology.observations instanceof List
      ? ((List<Map>)topology.observations).collect { Map item -> item.observedAt as Long }
          .findAll { Long observedAt -> observedAt != null }
          .max()
      : null
  Long previousObservedAt = operation.favoriteTopologyLastObservedAt as Long ?: 0L
  if(fingerprint && fingerprint == operation.favoriteTopologyFingerprint &&
      latestObservedAt != null && latestObservedAt > previousObservedAt) {
    operation.favoriteTopologyStableObservations =
        ((operation.favoriteTopologyStableObservations ?: 0) as Integer) + 1
  } else if(fingerprint != operation.favoriteTopologyFingerprint) {
    operation.favoriteTopologyFingerprint = fingerprint
    operation.favoriteTopologyStableObservations = 1
  }
  if(latestObservedAt != null && latestObservedAt > previousObservedAt) {
    operation.favoriteTopologyLastObservedAt = latestObservedAt
  }
}

Boolean isFavoriteGroupTopologySatisfied(Map operation, Map topology) {
  if(!isGroupTopologySatisfied(operation, topology)) { return false }
  String expectedGroupId = operation?.favoriteExpectedGroupId as String
  String expectedCoordinatorId = operation?.favoriteExpectedCoordinatorId as String
  if(expectedGroupId && topology?.groupId != expectedGroupId) { return false }
  if(expectedCoordinatorId && topology?.coordinatorId != expectedCoordinatorId) { return false }
  return true
}

void sendGroupTopologyCommand(Map operation, Map topology) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  String coordinatorId = operation.resolvedCoordinatorId as String
  ChildDeviceWrapper coordinator = coordinatorId ? rinconMap[coordinatorId] : null
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation.requiredPlayerIds)
  List<String> observedPlayerIds = normalizeGroupPlayerIds(topology?.playerIds)
  if(operation.groupingMode == GROUPING_MODE_EXPLICIT) {
    coordinatorId = operation.desiredCoordinatorId as String ?: coordinatorId
    coordinator = coordinatorId ? rinconMap[coordinatorId] : coordinator
    Boolean canEvictExtras = topology?.consistent == true &&
        topology.coordinatorId == coordinatorId &&
        new HashSet<String>(observedPlayerIds).containsAll(new HashSet<String>(requiredPlayerIds))
    if(coordinator == null) {
      failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE', "Could not resolve explicit coordinator ${coordinatorId}")
      return
    }
    if(canEvictExtras && observedPlayerIds.size() > requiredPlayerIds.size()) {
      List<String> extras = observedPlayerIds.findAll { String id -> !requiredPlayerIds.contains(id) }
      operation.lastCommand = 'removeExtraPlayers'
      coordinator.playerModifyGroupMembers([], extras)
    } else {
      operation.lastCommand = 'createExactGroup'
      coordinator.playerCreateGroup(requiredPlayerIds)
    }
  } else {
    if(topology?.fresh != true) {
      // Additive and CURRENT operations must not use cached membership or a
      // cached coordinator to mutate topology. Request a fresh groups
      // snapshot and let the next operation pass decide whether a follower
      // is actually missing.
      operation.lastCommand = 'refreshGroupTopology'
      operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
      operation.lastCommandAt = now()
      saveActiveGroupOperation(operation)
      refreshGroupOperationTopology([operationId: operation.operationId])
      return
    }
    if(topology?.consistent == true && topology.coordinatorId) {
      operation.resolvedCoordinatorId = topology.coordinatorId as String
      coordinatorId = operation.resolvedCoordinatorId as String
      coordinator = rinconMap[coordinatorId]
      if(operation.favoriteId) {
        registerGroupFavoriteOperationOnPlayers(operation, [coordinatorId])
      }
    }
    if(coordinator == null) {
      logWarn("Could not resolve additive coordinator for group operation ${operation.operationId}")
      return
    }
    List<String> followersToAdd = requiredPlayerIds.findAll { String id -> id != coordinatorId && !observedPlayerIds.contains(id) }
    operation.lastCommand = 'addMissingPlayers'
    if(followersToAdd) {
      coordinator.playerModifyGroupMembers(followersToAdd, [])
    } else {
      // The topology may be mid-transition or the player cache may be stale.
      // Refresh group data without changing membership; extras are preserved.
      coordinator.playerGetGroupsFull()
    }
  }
  operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
  operation.lastCommandAt = now()
  saveActiveGroupOperation(operation)
  runIn(1, 'refreshGroupOperationTopology', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void refreshGroupOperationTopology(Map data = [:]) {
  Map operation = getActiveGroupOperation()
  String callbackOperationId = data?.operationId as String
  if(operation == null || (callbackOperationId && callbackOperationId != operation.operationId) || operation.favoriteId && operation.phase != 'ENSURE_GROUP') {
    return
  }
  operation.topologyRefreshAt = now()
  operation.topologyObservations = [:]
  operation.stableObservations = 0
  operation.topologyFingerprint = null
  operation.observedGroupId = null
  operation.observedCoordinatorId = null
  operation.observedPlayerIds = []
  saveActiveGroupOperation(operation)
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> refreshIds = normalizeGroupPlayerIds(
    normalizeGroupPlayerIds(operation.requiredPlayerIds) +
    [operation.desiredCoordinatorId as String, operation.resolvedCoordinatorId as String]
  )
  refreshIds.each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try { player.playerGetGroupsFull() }
      catch(Exception e) { logDebug("Could not refresh group topology from ${playerId}: ${e.message}") }
    }
  }
}

void refreshGroupFavoriteTopology(Map operation, Boolean resetStability = true) {
  if(operation == null || !operation.favoriteId) { return }
  operation.favoriteTopologyRefreshAt = now()
  operation.favoriteTopologyAttempt = ((operation.favoriteTopologyAttempt ?: 0) as Integer) + 1
  operation.favoriteTopologyObservations = [:]
  if(resetStability == true) {
    operation.favoriteTopologyStableObservations = 0
    operation.favoriteTopologyLastObservedAt = 0L
    operation.favoriteTopologyFingerprint = null
  }
  operation.favoriteTopologyVerified = false
  saveActiveGroupOperation(operation)

  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> refreshIds = normalizeGroupPlayerIds(
    normalizeGroupPlayerIds(operation.requiredPlayerIds) +
    [
      operation.favoriteExpectedCoordinatorId as String,
      operation.resolvedCoordinatorId as String
    ]
  )
  refreshIds.each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try { player.playerGetGroupsFull() }
      catch(Exception e) { logDebug("Could not refresh post-Favorite group topology from ${playerId}: ${e.message}") }
    }
  }
}

void sendGroupFavoriteLoad(Map operation, Map topology) {
  if(!operation.favoriteId || topology?.consistent != true || !topology.groupId || !topology.coordinatorId) {
    return
  }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  String coordinatorId = topology.coordinatorId as String
  ChildDeviceWrapper coordinator = rinconMap[coordinatorId]
  if(coordinator == null) {
    failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE', "Could not resolve live coordinator ${coordinatorId}")
    return
  }
  operation.resolvedCoordinatorId = coordinatorId
  registerGroupFavoriteOperationOnPlayers(operation, [coordinatorId])
  operation.phase = 'WAIT_FOR_FAVORITE'
  operation.status = 'LOADING_FAVORITE'
  operation.favoriteAttempt = ((operation.favoriteAttempt ?: 0) as Integer) + 1
  operation.loadAcknowledged = false
  operation.loadErrorCode = null
  operation.loadErrorReason = null
  operation.playbackState = null
  operation.favoriteCommandAt = now()
  operation.favoriteAttemptId = null
  operation.favoriteExpectedGroupId = null
  operation.favoriteExpectedCoordinatorId = null
  operation.favoriteTopologyRefreshAt = 0L
  operation.favoriteTopologyObservations = [:]
  operation.favoriteTopologyAttempt = 0
  operation.favoriteTopologyStableObservations = 0
  operation.favoriteTopologyLastObservedAt = 0L
  operation.favoriteTopologyFingerprint = null
  operation.favoriteTopologyVerified = false
  operation.favoriteAttemptId = "${operation.operationId}:${operation.favoriteAttempt}".toString()
  operation.favoriteExpectedGroupId = topology.groupId as String
  operation.favoriteExpectedCoordinatorId = coordinatorId
  operation.playerPlaybackStates = [:]
  operation.metadataConfirmed = false
  operation.exactFavoriteId = null
  operation.lastCommandAt = operation.favoriteCommandAt
  saveActiveGroupOperation(operation)
  setGroupFavoriteOperationAttemptOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'LOADING_FAVORITE')
  try {
    try {
      coordinator.loadFavoriteForGroupOperation(
        operation.favoriteId as String,
        operation.repeatMode as String,
        operation.queueMode as String,
        operation.shuffleMode as String,
        operation.autoPlay as String,
        operation.crossfadeMode as String,
        operation.operationId as String,
        topology.groupId as String,
        operation.favoriteAttemptId as String
      )
    } catch(MissingMethodException ignored) {
      // Permit an app update to coexist briefly with an older child driver.
      // The legacy call has no attempt token, so the event handler falls back
      // to its operation/time/group identity checks for that mixed version.
      coordinator.loadFavoriteForGroupOperation(
        operation.favoriteId as String,
        operation.repeatMode as String,
        operation.queueMode as String,
        operation.shuffleMode as String,
        operation.autoPlay as String,
        operation.crossfadeMode as String,
        operation.operationId as String,
        topology.groupId as String
      )
    }
  } catch(Exception e) {
    failGroupOperation(operation, 'GROUP_FAVORITE_COMMAND_FAILED', e.message as String)
    return
  }
  refreshGroupFavoriteTopology(operation)
  runIn(2, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void pollGroupFavoritePlayback(Map operation) {
  if(!operation?.favoriteId) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  normalizeGroupPlayerIds(operation.requiredPlayerIds).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try { player.getPlaybackStatus() } catch(Exception ignored) { }
    }
  }
  ChildDeviceWrapper coordinator = rinconMap[operation.resolvedCoordinatorId as String]
  if(coordinator != null) {
    try { coordinator.getPlaybackMetadataStatus() } catch(Exception ignored) { }
  }
}

void updateGroupFavoriteSignalsFromState(Map operation) {
  if(!operation?.favoriteId) { return }
  Map<String, Map> playbackStates = operation.playerPlaybackStates instanceof Map
      ? (Map<String, Map>)operation.playerPlaybackStates
      : [:]
  Map coordinatorState = playbackStates[operation.resolvedCoordinatorId as String]
  Long favoriteCommandAt = operation.favoriteCommandAt as Long ?: 0L
  Long observedAt = coordinatorState?.observedAt as Long ?: 0L
  if(coordinatorState != null && observedAt >= favoriteCommandAt) {
    operation.playbackState = coordinatorState.playbackState as String
  }
}

Boolean areRequiredPlayersPlaying(Map operation) {
  if(!operation?.favoriteId) { return false }
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation.requiredPlayerIds)
  Map<String, Map> playbackStates = operation.playerPlaybackStates instanceof Map
      ? (Map<String, Map>)operation.playerPlaybackStates
      : [:]
  Long favoriteCommandAt = operation.favoriteCommandAt as Long ?: 0L
  return !requiredPlayerIds.isEmpty() && requiredPlayerIds.every { String playerId ->
    Map playerState = playbackStates[playerId]
    Long observedAt = playerState?.observedAt as Long ?: 0L
    observedAt >= favoriteCommandAt && playerState?.playbackState == 'PLAYBACK_STATE_PLAYING'
  }
}

Boolean haveRequiredPlaybackObservations(Map operation) {
  if(!operation?.favoriteId) { return false }
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation.requiredPlayerIds)
  Map<String, Map> playbackStates = operation.playerPlaybackStates instanceof Map
      ? (Map<String, Map>)operation.playerPlaybackStates
      : [:]
  Long favoriteCommandAt = operation.favoriteCommandAt as Long ?: 0L
  return !requiredPlayerIds.isEmpty() && requiredPlayerIds.every { String playerId ->
    Map playerState = playbackStates[playerId]
    Long observedAt = playerState?.observedAt as Long ?: 0L
    observedAt >= favoriteCommandAt && playerState?.containsKey('playbackState')
  }
}

String summarizeGroupFavoritePlayback(Map operation) {
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation?.requiredPlayerIds)
  Map<String, Map> playbackStates = operation?.playerPlaybackStates instanceof Map
      ? (Map<String, Map>)operation.playerPlaybackStates
      : [:]
  return requiredPlayerIds.collect { String playerId ->
    Map playerState = playbackStates[playerId]
    playerId + ': ' + (playerState?.playbackState ?: 'not observed')
  }.join('; ')
}

void recoverGroupFavoriteAfterTopologyChange(Map operation, Map topology) {
  String detail = "The group topology changed after the Favorite load: ${summarizeGroupOperationTopology(topology)}"
  if(operation.groupingMode == GROUPING_MODE_CURRENT) {
    failGroupOperation(operation, 'GROUP_CHANGED_DURING_FAVORITE', detail)
    return
  }
  if(((operation.favoriteAttempt ?: 0) as Integer) >= GROUP_OPERATION_MAX_FAVORITE_ATTEMPTS) {
    failGroupOperation(operation, 'FAVORITE_TOPOLOGY_CHANGED', detail)
    return
  }

  operation.phase = 'ENSURE_GROUP'
  operation.status = 'ENSURING_TOPOLOGY'
  operation.loadAcknowledged = false
  operation.loadErrorCode = null
  operation.loadErrorReason = null
  operation.playbackState = null
  operation.favoriteCommandAt = 0L
  operation.favoriteAttemptId = null
  operation.favoriteExpectedGroupId = null
  operation.favoriteExpectedCoordinatorId = null
  operation.favoriteTopologyRefreshAt = 0L
  operation.favoriteTopologyObservations = [:]
  operation.favoriteTopologyAttempt = 0
  operation.favoriteTopologyStableObservations = 0
  operation.favoriteTopologyLastObservedAt = 0L
  operation.favoriteTopologyFingerprint = null
  operation.favoriteTopologyVerified = false
  operation.playerPlaybackStates = [:]
  operation.metadataConfirmed = false
  operation.exactFavoriteId = null
  operation.topologyAttempt = 0
  operation.stableObservations = 0
  operation.topologyFingerprint = null
  operation.topologyObservations = [:]
  operation.observedGroupId = null
  operation.observedCoordinatorId = null
  operation.observedPlayerIds = []
  operation.lastTopologyObservations = topology?.observations ?: []
  operation.lastCommand = 'refreshGroupTopology'
  operation.lastCommandAt = now()
  operation.errorCode = null
  operation.detail = null
  saveActiveGroupOperation(operation)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

Boolean isRetryableGroupFavoriteError(String errorCode) {
  if(!errorCode) { return true }
  return !(errorCode in ['ERROR_INVALID_OBJECT_ID', 'ERROR_NOT_FOUND', 'ERROR_UNSUPPORTED_COMMAND'])
}

void advanceGroupFavoriteOperation(Map data = [:]) {
  Map operation = getActiveGroupOperation()
  String callbackOperationId = data?.operationId as String
  if(operation == null || (callbackOperationId && callbackOperationId != operation.operationId)) {
    return
  }
  if((operation.deadlineAt as Long ?: 0L) <= now()) {
    failGroupOperation(operation, 'GROUP_OPERATION_TIMEOUT', "The live Sonos topology or playback confirmation did not stabilize before the deadline. Last topology: ${summarizeGroupOperationTopology([observations: operation.lastTopologyObservations])}")
    return
  }

  Boolean favoriteLoadInProgress = operation.favoriteId &&
      ((operation.favoriteAttempt ?: 0) as Integer) > 0 &&
      operation.phase != 'ENSURE_GROUP'
  if(favoriteLoadInProgress && ((operation.favoriteTopologyRefreshAt ?: 0L) as Long) <= 0L) {
    Map knownTopology = readGroupOperationTopology(operation)
    if(knownTopology?.consistent == true) {
      operation.favoriteExpectedGroupId = operation.favoriteExpectedGroupId as String ?: knownTopology.groupId as String
      operation.favoriteExpectedCoordinatorId = operation.favoriteExpectedCoordinatorId as String ?: knownTopology.coordinatorId as String
    }
    refreshGroupFavoriteTopology(operation)
    publishGroupOperationStatus(operation, 'VERIFYING_TOPOLOGY')
    runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
      overwrite: true,
      data: [operationId: operation.operationId]
    ])
    return
  }

  Boolean verifyingFavoriteTopology = favoriteLoadInProgress &&
      operation.favoriteTopologyVerified != true
  Map topology = verifyingFavoriteTopology
      ? readFavoriteGroupTopology(operation)
      : readGroupOperationTopology(operation)
  operation.observedGroupId = topology.groupId
  operation.observedCoordinatorId = topology.coordinatorId
  operation.observedPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
  operation.lastTopologyObservations = topology.observations ?: []
  if(!verifyingFavoriteTopology && operation.groupingMode != GROUPING_MODE_EXPLICIT && topology.coordinatorId) {
    operation.resolvedCoordinatorId = topology.coordinatorId as String
    if(operation.favoriteId) {
      registerGroupFavoriteOperationOnPlayers(operation, [topology.coordinatorId as String])
    }
  }

  if(verifyingFavoriteTopology) {
    Boolean finalTopologySatisfied = isFavoriteGroupTopologySatisfied(operation, topology)
    if(topology.fresh != true) {
      Long refreshAt = operation.favoriteTopologyRefreshAt as Long ?: 0L
      Boolean waitingForFreshTopology = refreshAt > 0L &&
          now() < refreshAt + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L
      if(!waitingForFreshTopology) {
        if(((operation.favoriteTopologyAttempt ?: 0) as Integer) >= GROUP_OPERATION_MAX_FAVORITE_TOPOLOGY_ATTEMPTS) {
          failGroupOperation(operation, 'FAVORITE_TOPOLOGY_NOT_CONFIRMED',
              "Post-Favorite group topology was not observed from every required player: ${summarizeGroupOperationTopology(topology)}")
          return
        }
        refreshGroupFavoriteTopology(operation)
      }
      publishGroupOperationStatus(operation, 'VERIFYING_TOPOLOGY')
      runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
      return
    }
    if(!finalTopologySatisfied) {
      recoverGroupFavoriteAfterTopologyChange(operation, topology)
      return
    }

    updateFavoriteGroupTopologyStability(operation, topology)
    if(((operation.favoriteTopologyStableObservations ?: 0) as Integer) < GROUP_OPERATION_STABILITY_OBSERVATIONS) {
      if(((operation.favoriteTopologyAttempt ?: 0) as Integer) >= GROUP_OPERATION_MAX_FAVORITE_TOPOLOGY_ATTEMPTS) {
        failGroupOperation(operation, 'FAVORITE_TOPOLOGY_NOT_STABLE',
            "Post-Favorite group topology did not remain stable: ${summarizeGroupOperationTopology(topology)}")
        return
      }
      refreshGroupFavoriteTopology(operation, false)
      publishGroupOperationStatus(operation, 'VERIFYING_TOPOLOGY')
      runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
      return
    }

    operation.favoriteTopologyVerified = true
    operation.topologyRefreshAt = operation.favoriteTopologyRefreshAt
    operation.topologyObservations = operation.favoriteTopologyObservations ?: [:]
    operation.stableObservations = GROUP_OPERATION_STABILITY_OBSERVATIONS
    operation.topologyFingerprint = topology.fingerprint
    operation.resolvedCoordinatorId = topology.coordinatorId as String
    operation.favoriteExpectedGroupId = topology.groupId as String
    operation.favoriteExpectedCoordinatorId = topology.coordinatorId as String
    saveActiveGroupOperation(operation)
  }

  Boolean topologySatisfied = isGroupTopologySatisfied(operation, topology)
  if(!topologySatisfied) {
    operation.stableObservations = 0
    operation.topologyFingerprint = null
    if(operation.groupingMode == GROUPING_MODE_CURRENT && topology.fresh == true) {
      failGroupOperation(operation, 'CURRENT_TOPOLOGY_NOT_SATISFIED', 'The current group does not contain all configured players; no topology mutation was requested.')
      return
    }
    operation.phase = 'ENSURE_GROUP'
    operation.status = 'ENSURING_TOPOLOGY'
    Long refreshAt = operation.topologyRefreshAt as Long ?: 0L
    Long commandAt = operation.lastCommandAt as Long ?: 0L
    Long waitFrom = commandAt > 0L ? commandAt : refreshAt
    Boolean waitingForFreshTopology = topology.fresh != true && waitFrom > 0L &&
      now() < waitFrom + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L
    if(!waitingForFreshTopology) {
      if(((operation.topologyAttempt ?: 0) as Integer) >= GROUP_OPERATION_MAX_TOPOLOGY_ATTEMPTS) {
        failGroupOperation(operation, 'TOPOLOGY_NOT_STABLE', "The requested group topology could not be confirmed: ${summarizeGroupOperationTopology(topology)}")
        return
      }
      try {
        sendGroupTopologyCommand(operation, topology)
      } catch(Exception e) {
        failGroupOperation(operation, 'GROUP_COMMAND_FAILED', e.message as String)
        return
      }
      if(getActiveGroupOperation()?.operationId?.toString() != operation.operationId?.toString()) {
        return
      }
    }
    saveActiveGroupOperation(operation)
    publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
    runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
      overwrite: true,
      data: [operationId: operation.operationId]
    ])
    return
  }

  updateGroupTopologyStability(operation, topology)
  if(((operation.stableObservations ?: 0) as Integer) < GROUP_OPERATION_STABILITY_OBSERVATIONS) {
    saveActiveGroupOperation(operation)
    runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
      overwrite: true,
      data: [operationId: operation.operationId]
    ])
    return
  }

  if(!operation.favoriteId) {
    completeGroupOperation(operation)
    return
  }

  if(operation.phase == 'ENSURE_GROUP') {
    sendGroupFavoriteLoad(operation, topology)
    return
  }

  updateGroupFavoriteSignalsFromState(operation)
  if(operation.loadErrorCode && !isRetryableGroupFavoriteError(operation.loadErrorCode as String)) {
    failGroupOperation(operation, operation.loadErrorCode as String, operation.loadErrorReason as String)
    return
  }

  Long favoriteCommandAt = operation.lastCommandAt as Long ?: 0L
  Boolean favoriteRetryDue = operation.loadErrorCode ||
      (operation.loadAcknowledged != true && now() >= favoriteCommandAt + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L)
  if(favoriteRetryDue) {
    if(((operation.favoriteAttempt ?: 0) as Integer) < GROUP_OPERATION_MAX_FAVORITE_ATTEMPTS) {
      // Retry only the Favorite load; topology has already been verified and
      // no grouping command is repeated for a media acknowledgement failure.
      operation.phase = 'ENSURE_GROUP'
      operation.stableObservations = GROUP_OPERATION_STABILITY_OBSERVATIONS
      operation.lastCommandAt = 0L
      saveActiveGroupOperation(operation)
      sendGroupFavoriteLoad(operation, topology)
      return
    }
    failGroupOperation(
      operation,
      operation.loadErrorCode as String ?: 'FAVORITE_LOAD_NOT_ACKNOWLEDGED',
      operation.loadErrorReason as String ?: 'Sonos did not acknowledge the Favorite load.'
    )
    return
  }

  Boolean autoPlay = operation.autoPlay?.toString() == 'true'
  Boolean playbackInProgress = operation.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']
  if(operation.loadAcknowledged == true && !autoPlay) {
    completeGroupOperation(operation)
    return
  }
  if(operation.loadAcknowledged == true && operation.playbackState == 'PLAYBACK_STATE_PLAYING' &&
      areRequiredPlayersPlaying(operation) &&
      operation.metadataConfirmed == true && operation.exactFavoriteId?.toString() == operation.favoriteId?.toString()) {
    completeGroupOperation(operation)
    return
  }

  Long lastCommandAt = operation.lastCommandAt as Long ?: 0L
  Boolean allPlaybackObserved = haveRequiredPlaybackObservations(operation)
  Boolean partialPlaybackObserved = allPlaybackObserved &&
      operation.playbackState == 'PLAYBACK_STATE_PLAYING' &&
      !areRequiredPlayersPlaying(operation)
  if(operation.loadAcknowledged == true && partialPlaybackObserved &&
      now() >= lastCommandAt + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L) {
    if(((operation.playAttempt ?: 0) as Integer) < GROUP_OPERATION_MAX_PLAY_ATTEMPTS) {
      Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
      ChildDeviceWrapper coordinator = rinconMap[operation.resolvedCoordinatorId as String]
      if(coordinator != null) {
        operation.playAttempt = ((operation.playAttempt ?: 0) as Integer) + 1
        operation.phase = 'WAIT_FOR_PLAYBACK'
        operation.lastCommand = 'play'
        operation.lastCommandAt = now()
        saveActiveGroupOperation(operation)
        try { coordinator.playerPlay() } catch(Exception e) {
          failGroupOperation(operation, 'GROUP_PLAY_COMMAND_FAILED', e.message as String)
          return
        }
      }
    } else {
      failGroupOperation(
        operation,
        'PARTIAL_PLAYBACK',
        'The coordinator is playing but one or more requested players are not: ' + summarizeGroupFavoritePlayback(operation)
      )
      return
    }
  }
  if(operation.loadAcknowledged == true && !playbackInProgress &&
      now() >= lastCommandAt + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L) {
    if(((operation.playAttempt ?: 0) as Integer) < GROUP_OPERATION_MAX_PLAY_ATTEMPTS) {
      Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
      ChildDeviceWrapper coordinator = rinconMap[operation.resolvedCoordinatorId as String]
      if(coordinator != null) {
        operation.playAttempt = ((operation.playAttempt ?: 0) as Integer) + 1
        operation.phase = 'WAIT_FOR_PLAYBACK'
        operation.lastCommand = 'play'
        operation.lastCommandAt = now()
        saveActiveGroupOperation(operation)
        try { coordinator.playerPlay() } catch(Exception e) {
          failGroupOperation(operation, 'GROUP_PLAY_COMMAND_FAILED', e.message as String)
          return
        }
      }
    } else if(((operation.favoriteAttempt ?: 0) as Integer) < GROUP_OPERATION_MAX_FAVORITE_ATTEMPTS &&
        !playbackInProgress) {
      // A second load is bounded and only occurs after the acknowledged load
      // failed to start playback. Once playback is active, never interrupt it
      // with another Favorite request merely because metadata is late.
      operation.phase = 'ENSURE_GROUP'
      operation.stableObservations = GROUP_OPERATION_STABILITY_OBSERVATIONS
      operation.lastCommandAt = 0L
      saveActiveGroupOperation(operation)
      sendGroupFavoriteLoad(operation, topology)
      return
    } else if(!playbackInProgress) {
      failGroupOperation(operation, 'PLAYBACK_NOT_STARTED', 'Sonos acknowledged the Favorite but playback did not become active.')
      return
    }
  }

  pollGroupFavoritePlayback(operation)
  saveActiveGroupOperation(operation)
  runIn(2, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void groupFavoriteOperationEventHandler(Event event) {
  String rawEvent = event?.value?.toString()
  if(!rawEvent) { return }
  Map payload
  try {
    payload = (Map)parseJson(rawEvent)
  } catch(Exception e) {
    logWarn("Ignoring malformed group Favorite operation event: ${e.message}")
    return
  }
  Map operation = getActiveGroupOperation()
  if(operation == null || payload.operationId?.toString() != operation.operationId?.toString()) {
    return
  }
  String eventName = payload.event as String
  Map eventData = payload.data instanceof Map ? (Map)payload.data : [:]
  String eventPlayerId = payload.playerId as String
  Long eventObservedAt = (payload.observedAt ?: now()) as Long
  String eventAttemptId = payload.attemptId as String
  String expectedAttemptId = operation.favoriteAttemptId as String
  Boolean isExpectedAttempt = expectedAttemptId && (!eventAttemptId || eventAttemptId == expectedAttemptId)
  String expectedCoordinatorId = operation.favoriteExpectedCoordinatorId as String ?: operation.observedCoordinatorId as String ?: operation.resolvedCoordinatorId as String
  Boolean isCoordinatorEvent = !eventPlayerId || !expectedCoordinatorId || eventPlayerId == expectedCoordinatorId
  Boolean isRequiredPlayer = !eventPlayerId || normalizeGroupPlayerIds(operation.requiredPlayerIds).contains(eventPlayerId)
  Long favoriteCommandAt = operation.favoriteCommandAt as Long ?: 0L
  Boolean isAfterFavoriteCommand = favoriteCommandAt <= 0L || eventObservedAt >= favoriteCommandAt
  String eventGroupId = eventData.groupId as String
  String expectedGroupId = operation.favoriteExpectedGroupId as String ?: operation.observedGroupId as String
  Boolean isExpectedGroup = !eventGroupId || !expectedGroupId || eventGroupId == expectedGroupId
  switch(eventName) {
    case 'favoriteLoadAck':
      if(isExpectedAttempt && isCoordinatorEvent && isAfterFavoriteCommand && isExpectedGroup) {
        operation.loadAcknowledged = eventData.success == true
        operation.loadErrorCode = eventData.errorCode as String
        operation.loadErrorReason = eventData.reason as String
      }
      break
    case 'loadRejected':
      if(isExpectedAttempt && isCoordinatorEvent && isAfterFavoriteCommand && isExpectedGroup) {
        operation.loadAcknowledged = false
        operation.loadErrorCode = eventData.reason as String ?: 'PLAYER_REJECTED_LOAD'
        operation.loadErrorReason = eventData.reason as String
      }
      break
    case 'playbackStatus':
      if(isExpectedAttempt && isRequiredPlayer && isAfterFavoriteCommand && isExpectedGroup && eventPlayerId) {
        Map<String, Map> playbackStates = operation.playerPlaybackStates instanceof Map
            ? (Map<String, Map>)operation.playerPlaybackStates
            : [:]
        playbackStates[eventPlayerId] = [
          playbackState: eventData.playbackState as String,
          observedAt: eventObservedAt
        ]
        operation.playerPlaybackStates = playbackStates
      }
      if(isExpectedAttempt && isCoordinatorEvent && isAfterFavoriteCommand && isExpectedGroup) {
        operation.playbackState = eventData.playbackState as String
      }
      break
    case 'metadataConfirmed':
      if(isExpectedAttempt && isCoordinatorEvent && isAfterFavoriteCommand && isExpectedGroup) {
        if(eventData.confirmed == true && eventData.favoriteId?.toString() == operation.favoriteId?.toString()) {
          operation.metadataConfirmed = true
          operation.exactFavoriteId = eventData.favoriteId as String
        } else {
          operation.metadataConfirmed = false
          operation.exactFavoriteId = eventData.favoriteId as String
        }
      }
      break
    case 'groups':
      if(eventPlayerId && isRequiredPlayer) {
        Map<String, Map> topologyObservations = operation.topologyObservations instanceof Map
            ? (Map<String, Map>)operation.topologyObservations
            : [:]
        topologyObservations[eventPlayerId] = [
          groupId: eventData.groupId?.toString(),
          coordinatorId: eventData.coordinatorId?.toString(),
          playerIds: normalizeGroupPlayerIds(eventData.playerIds),
          observedAt: eventObservedAt
        ]
        operation.topologyObservations = topologyObservations

        Long favoriteTopologyRefreshAt = (operation.favoriteTopologyRefreshAt ?: 0L) as Long
        if(favoriteTopologyRefreshAt > 0L && eventObservedAt >= favoriteTopologyRefreshAt) {
          Map<String, Map> favoriteTopologyObservations = operation.favoriteTopologyObservations instanceof Map
              ? (Map<String, Map>)operation.favoriteTopologyObservations
              : [:]
          favoriteTopologyObservations[eventPlayerId] = topologyObservations[eventPlayerId]
          operation.favoriteTopologyObservations = favoriteTopologyObservations

          if(operation.favoriteTopologyVerified == true) {
            Map eventTopology = [
              fresh: true,
              consistent: true,
              groupId: eventData.groupId as String,
              coordinatorId: eventData.coordinatorId as String,
              playerIds: normalizeGroupPlayerIds(eventData.playerIds),
              observations: [favoriteTopologyObservations[eventPlayerId]],
              fingerprint: ''
            ]
            if(!isFavoriteGroupTopologySatisfied(operation, eventTopology)) {
              operation.favoriteTopologyVerified = false
              operation.favoriteTopologyStableObservations = 0
              operation.favoriteTopologyLastObservedAt = 0L
              operation.favoriteTopologyFingerprint = null
              operation.favoriteTopologyObservations = [:]
              refreshGroupFavoriteTopology(operation)
            }
          }
        }
      }
      if(eventData.containsKey('groupId') && eventData.containsKey('coordinatorId')) {
        operation.observedGroupId = eventData.groupId as String
        operation.observedCoordinatorId = eventData.coordinatorId as String
        operation.observedPlayerIds = normalizeGroupPlayerIds(eventData.playerIds)
      }
      break
    default:
      break
  }
  saveActiveGroupOperation(operation)
  runIn(1, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void publishGroupOperationStatus(Map operation, String status, String errorCode = null, String detail = null) {
  if(operation == null) { return }
  operation.status = status
  if(errorCode != null) { operation.errorCode = errorCode }
  if(detail != null) { operation.detail = detail }
  saveActiveGroupOperation(operation)
  Map statusData = [
    operationId: operation.operationId,
    status: status,
    groupingMode: operation.groupingMode,
    phase: operation.phase,
    favoriteId: operation.favoriteId,
    groupId: operation.observedGroupId,
    coordinatorId: operation.observedCoordinatorId ?: operation.resolvedCoordinatorId,
    playerIds: normalizeGroupPlayerIds(operation.observedPlayerIds),
    observations: operation.lastTopologyObservations ?: [],
    playbackStates: operation.playerPlaybackStates ?: [:],
    favoriteAcknowledged: operation.loadAcknowledged == true,
    metadataConfirmed: operation.metadataConfirmed == true,
    topologyAttempt: operation.topologyAttempt ?: 0,
    favoriteAttempt: operation.favoriteAttempt ?: 0,
    favoriteAttemptId: operation.favoriteAttemptId,
    favoriteTopologyAttempt: operation.favoriteTopologyAttempt ?: 0,
    favoriteTopologyVerified: operation.favoriteTopologyVerified == true,
    playAttempt: operation.playAttempt ?: 0,
    lastCommand: operation.lastCommand,
    errorCode: operation.errorCode,
    detail: operation.detail,
    updatedAt: now()
  ]
  ChildDeviceWrapper groupDevice = getChildDevice(operation.groupDni as String)
  if(groupDevice == null) { return }
  try {
    String mode = operation.groupingMode in [GROUPING_MODE_EXPLICIT, GROUPING_MODE_ADDITIVE]
        ? operation.groupingMode as String
        : null
    groupDevice.updateGroupOperationStatus(JsonOutput.toJson(statusData), mode)
  } catch(Exception e) {
    logWarn("Could not publish group operation status for ${operation.groupDni}: ${e.message}")
  }
}

void clearRegisteredGroupFavoriteOperations(Map operation) {
  if(operation == null) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  normalizeGroupPlayerIds(operation.registeredPlayerIds).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try { player.clearGroupFavoriteOperation(operation.operationId as String) }
      catch(Exception e) { logWarn("Could not clear group Favorite operation on ${playerId}: ${e.message}") }
    }
  }
}

void finishGroupOperation(Map operation, String status, String errorCode = null, String detail = null) {
  if(operation == null) { return }
  operation.phase = 'COMPLETE'
  operation.completedAt = now()
  publishGroupOperationStatus(operation, status, errorCode, detail)
  clearRegisteredGroupFavoriteOperations(operation)
  if(status == 'SUCCEEDED' && !operation.favoriteId) {
    storeGroupingContext(operation)
  }
  state.remove(GROUP_OPERATION_STATE_KEY)
  if(state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List) {
    runIn(1, 'drainDeferredGroupOperationCommands', [overwrite: true])
  }
}

void completeGroupOperation(Map operation) {
  logInfo("Group operation ${operation.operationId} completed successfully")
  finishGroupOperation(operation, 'SUCCEEDED')
}

void failGroupOperation(Map operation, String errorCode, String detail = null) {
  if(operation == null) { return }
  logWarn("Group operation ${operation.operationId} failed: ${errorCode}${detail ? " (${detail})" : ''}")
  finishGroupOperation(operation, 'FAILED', errorCode, detail)
}

void cancelActiveGroupOperation(String groupDni, String reason = 'CANCELLED') {
  Map operation = getActiveGroupOperation()
  if(operation != null && operation.groupDni as String == groupDni) {
    finishGroupOperation(operation, 'CANCELLED', reason, 'A newer user command superseded the operation.')
  }
}

ChildDeviceWrapper resolveGroupCommandCoordinator(ChildDeviceWrapper configuredCoordinator,
    List<String> playerIds, Map<String, ChildDeviceWrapper> rinconMap) {
  ChildDeviceWrapper activeCoordinator = playerIds.collect { String id -> rinconMap[id] }
    .find { ChildDeviceWrapper player ->
      player != null && (player.getDataValue('isGroupCoordinator') == 'true' ||
        player.currentValue('isGroupCoordinator', true) == 'on')
    }
  return activeCoordinator ?: configuredCoordinator
}

/**
 * Receive a deferred player-to-parent command. Player drivers use this bridge
 * when a command is addressed to a follower; the event is emitted after the
 * follower method returns so the player never waits on the parent app's child
 * lookup semaphore.
 */
void playerCommandRequestHandler(Event event) {
  String rawRequest = event?.value?.toString()
  if(!rawRequest) {
    logWarn('Ignoring empty player command request')
    return
  }

  Map request
  try {
    request = (Map)parseJson(rawRequest)
  } catch(Exception e) {
    logWarn("Ignoring malformed player command request: ${e.message}")
    return
  }

  String playerDni = request?.playerDni as String
  String command = request?.command as String
  if(!playerDni || !command || getChildDevice(playerDni) == null) {
    logWarn("Ignoring player command request with invalid target or command: ${rawRequest}")
    return
  }
  runIn(1, 'processPlayerCommandRequest', [data: [request: request]])
}

void processPlayerCommandRequest(Map data) {
  Map request = data?.request instanceof Map ? (Map)data.request : null
  String playerDni = request?.playerDni as String
  String command = request?.command as String
  if(!playerDni || !command) {
    return
  }

  ChildDeviceWrapper source = getChildDevice(playerDni)
  if(source == null) {
    logWarn("Player command ${command} ignored because ${playerDni} no longer exists")
    return
  }
  List args = request.args instanceof List ? (List)request.args : []

  try {
    Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
    String configuredCoordinatorId = source.getDataValue('groupCoordinatorId')
    List<String> candidateIds = source.getDataValue('groupPlayerIds')?.tokenize(',') ?: []
    if(!candidateIds && source.getDataValue('id')) {
      candidateIds.add(source.getDataValue('id'))
    }
    ChildDeviceWrapper coordinator = candidateIds.collect { String id -> rinconMap[id] }
      .find { ChildDeviceWrapper player ->
        player != null && (player.getDataValue('isGroupCoordinator') == 'true' ||
          player.currentValue('isGroupCoordinator', true) == 'on')
      }
    if(coordinator == null && configuredCoordinatorId) {
      coordinator = rinconMap[configuredCoordinatorId]
    }
    if(coordinator == null) {
      logWarn("Player command ${command} could not resolve a coordinator for ${playerDni}")
      return
    }

    switch(command) {
      case 'muteGroup':
        coordinator.muteGroup()
        break
      case 'unmuteGroup':
        coordinator.unmuteGroup()
        break
      case 'setGroupVolume':
        coordinator.setGroupVolume(toBigDecimal(args.size() > 0 ? args[0] : null), toBigDecimal(args.size() > 1 ? args[1] : null))
        break
      case 'groupVolumeUp':
        coordinator.groupVolumeUp()
        break
      case 'groupVolumeDown':
        coordinator.groupVolumeDown()
        break
      case 'loadFavoriteFull':
        coordinator.loadFavoriteFull(
          args.size() > 0 ? args[0] as String : null,
          args.size() > 1 ? args[1] as String : null,
          args.size() > 2 ? args[2] as String : null,
          args.size() > 3 ? args[3] as String : null,
          args.size() > 4 ? args[4] as String : null,
          args.size() > 5 ? args[5] as String : null
        )
        break
      case 'loadPlaylistFull':
        coordinator.loadPlaylistFull(
          args.size() > 0 ? args[0] as String : null,
          args.size() > 1 ? args[1] as String : null,
          args.size() > 2 ? args[2] as String : null,
          args.size() > 3 ? args[3] as String : null,
          args.size() > 4 ? args[4] as String : null,
          args.size() > 5 ? args[5] as String : null
        )
        break
      default:
        logWarn("Unsupported deferred player command '${command}'")
    }
  } catch(Exception e) {
    logWarn("Deferred player command ${command} failed for ${playerDni}: ${e.message}")
  }
}

BigDecimal toBigDecimal(Object value) {
  if(value == null || value.toString().trim() == '') {
    return null
  }
  try {
    return value instanceof BigDecimal ? (BigDecimal)value : new BigDecimal(value.toString())
  } catch(Exception e) {
    logWarn("Ignoring invalid numeric group command value '${value}'")
    return null
  }
}

Boolean groupCommandBoolean(Object value, Boolean defaultValue) {
  if(value == null) { return defaultValue }
  if(value instanceof Boolean) { return (Boolean)value }
  return value.toString().toBoolean()
}

Boolean isSonosGrouped(ChildDeviceWrapper coordinator) {
  return coordinator?.currentValue('isGrouped', true) == 'on' &&
      coordinator?.currentValue('isGroupCoordinator', true) == 'on'
}

void executeGroupVolumeCommand(ChildDeviceWrapper coordinator, List<ChildDeviceWrapper> players, Map args) {
  BigDecimal level = toBigDecimal(args.level)
  if(level == null) {
    logWarn('Ignoring group volume command without a valid level')
    return
  }
  BigDecimal duration = toBigDecimal(args.duration)
  Boolean grouped = isSonosGrouped(coordinator)
  Boolean useProportional = groupCommandBoolean(args.useProportionalVolume, true)
  Boolean controlIndividually = groupCommandBoolean(args.controlUngroupedIndividually, false)

  if(grouped && useProportional) {
    coordinator.setGroupVolume(level, duration)
  } else if((grouped && !useProportional) || (!grouped && controlIndividually)) {
    players.each { ChildDeviceWrapper player -> player.setLevel(level) }
  } else if(coordinator) {
    coordinator.setLevel(level)
  } else {
    logWarn('Cannot execute group volume command without a coordinator')
  }
}

void executeGroupRelativeVolumeCommand(ChildDeviceWrapper coordinator, List<ChildDeviceWrapper> players, Map args, Integer direction) {
  Boolean grouped = isSonosGrouped(coordinator)
  Boolean useProportional = groupCommandBoolean(args.useProportionalVolume, true)
  Boolean controlIndividually = groupCommandBoolean(args.controlUngroupedIndividually, false)
  if(grouped && useProportional) {
    if(direction > 0) { coordinator.groupVolumeUp() }
    else { coordinator.groupVolumeDown() }
  } else if((grouped && !useProportional) || (!grouped && controlIndividually)) {
    players.each { ChildDeviceWrapper player ->
      if(direction > 0) { player.volumeUp() }
      else { player.volumeDown() }
    }
  } else if(coordinator) {
    if(direction > 0) { coordinator.volumeUp() }
    else { coordinator.volumeDown() }
  }
}

void executeGroupMuteCommand(ChildDeviceWrapper coordinator, List<ChildDeviceWrapper> players, Map args, Boolean muted) {
  Boolean grouped = isSonosGrouped(coordinator)
  Boolean useProportional = groupCommandBoolean(args.useProportionalVolume, true)
  Boolean controlIndividually = groupCommandBoolean(args.controlUngroupedIndividually, false)
  if(grouped && useProportional) {
    if(muted) { coordinator.muteGroup() }
    else { coordinator.unmuteGroup() }
  } else if((grouped && !useProportional) || (!grouped && controlIndividually)) {
    players.each { ChildDeviceWrapper player ->
      if(muted) { player.mute() }
      else { player.unmute() }
    }
  } else if(coordinator) {
    if(muted) { coordinator.mute() }
    else { coordinator.unmute() }
  }
}

void refreshGroupDeviceState(ChildDeviceWrapper groupDevice, ChildDeviceWrapper coordinator, List<ChildDeviceWrapper> players, Map args) {
  if(!coordinator) {
    logWarn("Cannot refresh ${groupDevice.displayName}: coordinator not found")
    return
  }

  Boolean useProportional = groupCommandBoolean(args.useProportionalVolume, true)
  Map attributes = [:]
  if(isSonosGrouped(coordinator) && useProportional) {
    Object volume = coordinator.currentValue('groupVolume', true)
    Object mute = coordinator.currentValue('groupMute', true)
    if(volume != null) { attributes.volume = volume; attributes.groupVolume = volume }
    if(mute != null) { attributes.mute = mute; attributes.groupMute = mute }
  } else {
    Integer totalVolume = 0
    Integer volumeCount = 0
    Boolean allMuted = !players.isEmpty()
    players.each { ChildDeviceWrapper player ->
      Integer volume = player.currentValue('volume', true) as Integer
      if(volume != null) { totalVolume += volume; volumeCount++ }
      if(player.currentValue('mute', true) != 'muted') { allMuted = false }
    }
    if(volumeCount > 0) {
      Integer averageVolume = Math.round(totalVolume / volumeCount) as Integer
      attributes.volume = averageVolume
      attributes.groupVolume = averageVolume
    }
    String mute = allMuted ? 'muted' : 'unmuted'
    attributes.mute = mute
    attributes.groupMute = mute
  }

  ['status', 'transportStatus', 'trackData', 'trackDescription', 'currentTrackDuration',
   'currentArtistName', 'albumArtURI', 'albumArtSmall', 'albumArtMedium', 'albumArtLarge',
   'audioSource', 'currentAlbumName', 'currentTrackName', 'currentFavorite', 'currentPlaylist',
   'currentTrackNumber', 'nextArtistName', 'nextAlbumName', 'nextTrackName', 'nextTrackAlbumArtURI',
   'queueTrackTotal', 'queueTrackPosition', 'currentRepeatOneMode', 'currentRepeatAllMode',
   'currentCrossfadeMode', 'currentShuffleMode'].each { String name ->
    Object value = coordinator.currentValue(name, true)
    if(value != null) { attributes[name] = value }
  }

  Object memberNames = coordinator.currentValue('groupMemberNames', true)
  if(memberNames != null) {
    attributes.currentlyJoinedPlayers = memberNames.toString().replaceAll(/^\[|\]$/, '').trim()
  }
  if(!attributes.isEmpty()) {
    // updateBatchPlaybackState() is a local-only group-driver operation.
    groupDevice.updateBatchPlaybackState(JsonOutput.toJson(attributes))
  }
}

/**
 * Batch RINCON lookup — single pass over child devices, O(1) per rincon.
 */
List<ChildDeviceWrapper> getDevicesFromRincons(LinkedHashSet<String> rincons) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<ChildDeviceWrapper> children = []
  rincons.each { String player ->
    ChildDeviceWrapper dev = rinconMap[player]
    if(dev != null) { children.add(dev) }
  }
  return children
}

List<ChildDeviceWrapper> getDevicesFromRincons(List<String> rincons) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<ChildDeviceWrapper> children = []
  rincons.each { String player ->
    ChildDeviceWrapper dev = rinconMap[player]
    if(dev != null) { children.add(dev) }
  }
  return children
}

List<String> getCreatedPlayerDevices() {
  List<ChildDeviceWrapper> childDevices = getCurrentPlayerDevices()
  List<String> pds = []
  childDevices.each() {cd -> pds.add("${cd.getDeviceNetworkId()}")}
  return pds
}

List<String> getCreatedGroupDevices() {
  List<ChildDeviceWrapper> childDevices = getCurrentGroupDevices()
  List<String> pds = []
  childDevices.each() {cd -> pds.add("${cd.getDeviceNetworkId()}")}
  return pds
}

List<String> getUserGroupsDNIsFromUserGroups() {
  List<String> dnis = state.userGroups.collect { Object groupName, Object ignored ->
    "${app.id}-SonosGroupDevice-${groupName}".toString()
  }
  return dnis
}

List<ChildDeviceWrapper> getCurrentPlayerDevices() {
  List<ChildDeviceWrapper> currentPlayers = []
  app.getChildDevices().each{child -> if(child.getDataValue('id')) { currentPlayers.add(child)}}
  return currentPlayers
}

// @CompileStatic
// void registerAllPlayersInRinconMap(DeviceWrapper cd) {
//   cd.addAllPlayersToRinconMap(getCurrentPlayerDevices())
// }

List<ChildDeviceWrapper> getCurrentGroupDevices() {
  List<ChildDeviceWrapper> currentGroupDevs = []
  app.getChildDevices().each{child -> if(child.getDataValue('id') == null) { currentGroupDevs.add(child)}}
  return currentGroupDevs
}

/**
 * Get all group devices whose coordinator matches the given RINCON ID.
 * Shared helper used by all forwarding methods to avoid repeated traversals.
 * NOT @CompileStatic — requires dynamic access to app.getChildDevices().
 */
List<ChildDeviceWrapper> getGroupDevicesForCoordinator(String coordinatorId) {
  List<ChildDeviceWrapper> result = []
  app.getChildDevices().each { child ->
    if(child.getDataValue('id') == null && child.getDataValue('groupCoordinatorId') == coordinatorId) {
      result.add(child)
    }
  }
  return result
}

/**
 * Single-pass partition of all child devices into group devices (for a coordinator)
 * and a RINCON lookup map (for player name resolution).
 * Returns [groupDevices: List<ChildDeviceWrapper>, rinconMap: Map<String, ChildDeviceWrapper>]
 * NOT @CompileStatic — requires dynamic access to app.getChildDevices().
 */
Map getGroupDevicesAndRinconMap(String coordinatorId) {
  List<ChildDeviceWrapper> groupsForCoord = []
  Map<String, ChildDeviceWrapper> rinconMap = [:]
  app.getChildDevices().each { child ->
    String id = child.getDataValue('id')
    if(id) {
      rinconMap[id] = child
    } else if(child.getDataValue('groupCoordinatorId') == coordinatorId) {
      groupsForCoord.add(child)
    }
  }
  return [groupDevices: groupsForCoord, rinconMap: rinconMap]
}

List<String> getAllPlayersForGroupDevice(DeviceWrapper device) {
  String coordinatorId = device.getDataValue('groupCoordinatorId')
  String playerIdsStr = device.getDataValue('playerIds')
  List<String> playerIds = []
  if(coordinatorId) {
    playerIds.add(coordinatorId)
  }
  if(playerIdsStr) {
    playerIds.addAll(playerIdsStr.tokenize(','))
  }
  return playerIds
}

LinkedHashMap getPlayerInfoLocalSync(String ipAddress) {
  ipAddress = ipAddress.contains(':') ? ipAddress : "${ipAddress}:1443"
  LinkedHashMap params = [
    uri:  "${getLocalApiPrefix(ipAddress)}/players/local/info",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json',
    contentType: 'application/json',
    ignoreSSLIssues: true
  ]
  try {
    httpGet(params) { resp ->
      if (resp && resp.data && resp.success) { return resp.data }
    }
  } catch(Exception e){
    logInfo("Could not connect to: ${ipAddress}. If this is a Sonos player, please report an issue. Note that RIGHT channel speakers on a stereo pair, subwoofers, or rear channel speakers this is expected. Only LEFT channel in stereo pairs (or Arc/Beam in a center + rear setup) will respond.")
  }
}

GPathResult getDeviceDescriptionLocalSync(String ipAddress) {
  if(!ipAddress.contains(':')) { ipAddress = "${ipAddress}:1400"}
  Map params = [
    uri:  "http://${ipAddress}/xml/device_description.xml",
    requestContentType: 'application/xml',
    contentType: 'application/xml'
  ]
  try {
    httpGet(params) { resp ->
      if (resp && resp.data && resp.success) { return resp.data }
      else { logError("getDeviceDescriptionLocalSync: unexpected response: ${resp?.data}") }
    }
  } catch(Exception e){
    logInfo("Could not connect to: ${ipAddress}. If this is a Sonos player, please report an issue. Note that RIGHT channel speakers on a stereo pair, subwoofers, or rear channel speakers this is expected. Only LEFT channel in stereo pairs (or Arc/Beam in a center + rear setup) will respond.")
  }
}

void getPlayerInfoLocalCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'getPlayerInfoLocalCallback')) { return }
  Map respJson = response.getJson()
  String playerName = respJson?.device?.name
  String playerDni = (respJson?.device?.serialNumber.replace('-','').tokenize(':'))[0]
  String swGen = respJson?.device?.swGen
  String websocketUrl = respJson?.device?.websocketUrl
  String householdId = respJson?.householdId
  String playerId = respJson?.playerId
  String groupId = respJson?.groupId
}

void getZoneGroupAttributesAsync(DeviceWrapper device, String callbackMethod = 'getZoneGroupAttributesAsyncCallback', Map data = null) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  asynchttpPost(callbackMethod, params, data)
}

void getZoneGroupAttributesAsyncCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'getZoneGroupAttributesAsyncCallback')) { return }
}

List<DeviceWrapper> getCurrentGroupedDevices(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  List<String> groupedRincons
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupedRincons = (xml['Body']['GetZoneGroupAttributesResponse']['CurrentZonePlayerUUIDsInGroup'].text()).toString().tokenize(',')
    }
    else { logError("getCurrentGroupedDevices: unexpected response: ${resp?.data}") }
  }
  List<DeviceWrapper> groupedDevices = getDevicesFromRincons(groupedRincons)
  return groupedDevices
}

List<DeviceWrapper> getGroupedPlayerDevicesFromGetZoneGroupAttributes(GPathResult xml, String rincon) {
  List<DeviceWrapper> groupedDevices = []
  List<String> groupIds = []
  List<String> groupedRincons = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZonePlayerUUIDsInGroup'].text().tokenize(',')
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  groupedRincons.each{groupIds.add("${it}".tokenize('_')[1][0..-6])}
  groupIds.each{groupedDevices.add(getChildDevice(it))}
  return groupedDevices
}

String getGroupForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZoneGroupID'].text().toString()
    }
    else { logError("getGroupForPlayerDeviceLocal: unexpected response: ${resp?.data}") }
  }
  return groupId
}

DeviceWrapper getGroupCoordinatorForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  if(ip == null) {
    logWarn("No localUpnpHost found for ${device?.displayName}; cannot determine group coordinator")
    return null
  }
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZoneGroupID'].text().toString()
    }
    else { logError("getGroupCoordinatorForPlayerDeviceLocal: unexpected response: ${resp?.data}") }
  }
  if(groupId == null || groupId == '') {
    logWarn("No group ID returned for ${device?.displayName}; cannot determine group coordinator")
    return null
  }

  List<String> groupIdParts = groupId.tokenize(':')
  if(groupIdParts.size() == 0) {
    logWarn("Unexpected group ID '${groupId}' returned for ${device?.displayName}")
    return null
  }

  return getDeviceFromRincon(groupIdParts[0])
}

String getHouseholdForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentMuseHouseholdId'].text().toString()
    }
    else { logError("getHouseholdForPlayerDeviceLocal: unexpected response: ${resp?.data}") }
  }
  return groupId
}

Boolean hasLeftAndRightChannelsSync(DeviceWrapper device) {
  String householdId = device.getDataValue('householdId')
  String localApiUrl = device.getDataValue('localApiUrl')
  String endpoint = "households/${householdId}/groups"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  Map json = sendLocalJsonQuerySync([params:params])
  Map playerInfo = json?.players.find{it?.id == device.getDataValue('id')}
  Boolean hasLeftChannel = playerInfo?.zoneInfo?.members.find{it?.channelMap.contains('LF')}
  Boolean hasRightChannel = playerInfo?.zoneInfo?.members.find{it?.channelMap.contains('RF')}
  return hasLeftChannel && hasRightChannel
}

String unEscapeOnce(String text) {
  return text.replace('&lt;','<').replace('&gt;','>').replace('&quot;','"')
}

String unEscapeMetaData(String text) {
  return text.replace('&amp;lt;','<').replace('&amp;gt;','>').replace('&amp;quot;','"')
}
// =============================================================================
// Helper methods
// =============================================================================



// =============================================================================
// Component Methods for Child Event Processing
// =============================================================================
void updateGroupDevices(String coordinatorId, List<String> playersInGroup) {
  logTrace('updateGroupDevices')
  if(!coordinatorId || !playersInGroup) {
    return
  }
  // Single pass over all children: partition into group devices (for this coordinator)
  // and a RINCON lookup map (for player name resolution). Avoids two separate getChildDevices() calls.
  Map partitioned = getGroupDevicesAndRinconMap(coordinatorId)
  List<ChildDeviceWrapper> groupsForCoord = (List<ChildDeviceWrapper>)partitioned['groupDevices']
  Map<String, ChildDeviceWrapper> rinconMap = (Map<String, ChildDeviceWrapper>)partitioned['rinconMap']

  // Resolve RINCON IDs to friendly player names for the currentlyJoinedPlayers attribute
  List<String> joinedPlayerNames = []
  playersInGroup.each { String rincon ->
    ChildDeviceWrapper playerDev = rinconMap[rincon]
    if(playerDev != null) {
      String name = playerDev.getDataValue('name')
      joinedPlayerNames.add(name != null && name != '' ? name : rincon)
    } else {
      // Preserve the complete live membership even when a player is not
      // currently represented by a SAC child device.
      joinedPlayerNames.add(rincon)
    }
  }
  String joinedPlayersValue = joinedPlayerNames.join(', ')

  // Resolve caller once — constant for all group devices in this call.
  // Verify the calling player is the actual Sonos group coordinator.
  // Without this, every player in the group whose configured coordinator matches
  // a group device would incorrectly activate that device when all members are
  // the same across multiple group devices with different coordinators.
  ChildDeviceWrapper callerDevice = rinconMap[coordinatorId]
  Boolean isCallerActualCoordinator = callerDevice?.getDataValue('isGroupCoordinator') == 'true'

  groupsForCoord.each { ChildDeviceWrapper gd ->
    HashSet<String> configuredPlayers = new HashSet<String>(getAllPlayersForGroupDevice(gd))
    HashSet<String> observedPlayers = new HashSet<String>(normalizeGroupPlayerIds(playersInGroup))
    observedPlayers.add(coordinatorId)
    String rememberedMode = getFreshGroupingContext(gd.getDeviceNetworkId())?.mode as String
    String deviceMode = gd.currentValue('groupingMode', true)?.toString()
    String groupingMode = normalizeGroupOperationMode(rememberedMode ?: deviceMode, GROUPING_MODE_EXPLICIT)
    Boolean membershipSatisfied = groupingMode == GROUPING_MODE_EXPLICIT
        ? configuredPlayers.equals(observedPlayers)
        : observedPlayers.containsAll(configuredPlayers)
    Boolean shouldBeActive = membershipSatisfied && isCallerActualCoordinator
    Map<String, Object> attributes = new LinkedHashMap<String, Object>()
    attributes['switch'] = shouldBeActive ? 'on' : 'off'
    attributes['currentlyJoinedPlayers'] = joinedPlayersValue
    // Include the coordinator's current state with an activation update. The
    // group driver applies this locally after the player callback has returned.
    if(shouldBeActive && callerDevice != null) {
      ['albumArtURI', 'status', 'transportStatus', 'currentRepeatOneMode',
       'currentRepeatAllMode', 'currentShuffleMode', 'currentCrossfadeMode',
       'currentTrackDuration', 'currentArtistName', 'currentAlbumName',
       'currentTrackName', 'trackDescription', 'trackData', 'currentFavorite',
       'currentPlaylist', 'nextAlbumName', 'nextArtistName', 'nextTrackName',
       'nextTrackAlbumArtURI', 'albumArtSmall', 'albumArtMedium', 'albumArtLarge',
       'audioSource', 'currentTrackNumber', 'groupVolume', 'groupMute'].each { String name ->
        Object value = callerDevice.currentValue(name, true)
        if(value != null) {
          attributes[name] = value
        }
      }
    }
    queueGroupDeviceState(gd.getDeviceNetworkId(), attributes)
  }
}

/**
 * Queue a coalesced group-device state update. This is deliberately a one-way
 * app-to-group boundary: it never calls a group driver while processing a
 * player callback.
 */
void queueGroupDeviceState(String groupDni, Map attributes) {
  if(!groupDni || !attributes || attributes.isEmpty()) {
    return
  }
  Map<String, Map<String, Object>> pending = state[PENDING_GROUP_STATE_QUEUE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_GROUP_STATE_QUEUE_KEY]
      : [:]
  Map<String, Object> groupState = pending[groupDni] instanceof Map
      ? pending[groupDni]
      : [:]
  attributes.each { String name, Object value ->
    if(value != null) {
      groupState[name] = value
    }
  }
  pending[groupDni] = groupState
  state[PENDING_GROUP_STATE_QUEUE_KEY] = pending

  Map<String, Integer> attempts = state[GROUP_STATE_DRAIN_ATTEMPTS_KEY] instanceof Map
      ? (Map<String, Integer>)state[GROUP_STATE_DRAIN_ATTEMPTS_KEY]
      : [:]
  attempts.remove(groupDni)
  state[GROUP_STATE_DRAIN_ATTEMPTS_KEY] = attempts
  runIn(1, 'drainGroupStateUpdates', [overwrite: true])
}

/**
 * Deliver queued group state after the originating player callback has
 * returned. A bounded retry protects the app from a transient child-device
 * invocation failure without recreating the original synchronous call chain.
 */
void drainGroupStateUpdates() {
  Map<String, Map<String, Object>> pending = state[PENDING_GROUP_STATE_QUEUE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_GROUP_STATE_QUEUE_KEY]
      : [:]
  state.remove(PENDING_GROUP_STATE_QUEUE_KEY)
  if(pending.isEmpty()) {
    state.remove(GROUP_STATE_DRAIN_ATTEMPTS_KEY)
    return
  }

  Map<String, Integer> attempts = state[GROUP_STATE_DRAIN_ATTEMPTS_KEY] instanceof Map
      ? (Map<String, Integer>)state[GROUP_STATE_DRAIN_ATTEMPTS_KEY]
      : [:]
  Map<String, Map<String, Object>> retryQueue = [:]
  pending.each { String groupDni, Map<String, Object> attributes ->
    ChildDeviceWrapper groupDevice = getChildDevice(groupDni)
    if(groupDevice == null) {
      logWarn("drainGroupStateUpdates: no group device found for DNI ${groupDni}")
      attempts.remove(groupDni)
      return
    }
    try {
      groupDevice.updateBatchPlaybackState(JsonOutput.toJson(attributes))
      attempts.remove(groupDni)
    } catch(Exception e) {
      Integer attempt = attempts[groupDni] ?: 0
      if(attempt < GROUP_STATE_DRAIN_MAX_ATTEMPTS) {
        attempts[groupDni] = attempt + 1
        retryQueue[groupDni] = attributes
        logWarn("Group state delivery to ${groupDni} failed (attempt ${attempt + 1} of ${GROUP_STATE_DRAIN_MAX_ATTEMPTS}): ${e.message}")
      } else {
        attempts.remove(groupDni)
        logError("Giving up group state delivery to ${groupDni} after ${GROUP_STATE_DRAIN_MAX_ATTEMPTS} retries: ${e.message}")
      }
    }
  }
  if(retryQueue.isEmpty()) {
    state.remove(GROUP_STATE_DRAIN_ATTEMPTS_KEY)
    return
  }
  state[PENDING_GROUP_STATE_QUEUE_KEY] = retryQueue
  state[GROUP_STATE_DRAIN_ATTEMPTS_KEY] = attempts
  runIn(1, 'drainGroupStateUpdates', [overwrite: true])
}

/**
 * Combined flush method: resolves group devices ONCE for both volume and playback attributes.
 * Called by the player driver's flushPendingGroupDeviceUpdates() to avoid two separate
 * child device traversals.
 * @param coordinatorId The RINCON ID of the coordinator
 * @param volumeAttrs Map of volume/mute attributes (may be null)
 * @param playbackAttrs Map of extended playback attributes (may be null/empty)
 */
void flushGroupDeviceState(String coordinatorId, Map volumeAttrs, Map playbackAttrs) {
  if(!coordinatorId) { return }

  // Invariant: volumeAttrs keys (volume, mute) must not overlap with playbackAttrs keys.
  // The caller (flushPendingGroupDeviceUpdates) guarantees this by stripping _groupVolume/_groupMute
  // from the pending map before passing the remainder as playbackAttrs.
  // Note: switch is NOT included in volumeAttrs — only updateGroupDevices() manages switch state.
  Map combined = [:]
  if(volumeAttrs) { combined.putAll(volumeAttrs) }
  if(playbackAttrs) { combined.putAll(playbackAttrs) }
  if(combined.isEmpty()) { return }

  List<ChildDeviceWrapper> groupsForCoord = getGroupDevicesForCoordinator(coordinatorId)
  groupsForCoord.each { ChildDeviceWrapper groupDevice ->
    queueGroupDeviceState(groupDevice.getDeviceNetworkId(), combined)
  }
}

// =============================================================================
// Version Checking for Installed Files
// =============================================================================
void checkInstalledVersions() {
  logInfo('Checking versions of all installed files...')
  state.lastVersionCheck = now()
  state.versionMismatches = []
  state.installedVersions = []

  // Get the ACTUAL version from the hub's app code, not the running instance
  // This is critical after updates when the instance may have the old version
  String appVersion = getActualInstalledAppVersion()
  logDebug("Current app version from hub: ${appVersion}")

  // Get package manifest for the CURRENT installed version (not main branch)
  // This ensures we're comparing against the correct expected versions
  String manifestUrl = "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/PackageManifests/SonosAdvancedController/packageManifest.json"
  Map manifest = null

  try {
    Map params = [
      uri: manifestUrl,
      contentType: 'application/json',
      timeout: 15
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        manifest = resp.data
      }
    }
  } catch(Exception e) {
    logWarn("Failed to retrieve package manifest for v${appVersion}: ${e.message}")
    logDebug("Trying main branch manifest as fallback...")

    // Fallback to main branch if version-specific manifest not found
    try {
      manifestUrl = 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json'
      Map fallbackParams = [
        uri: manifestUrl,
        contentType: 'application/json',
        timeout: 15
      ]
      httpGet(fallbackParams) { resp ->
        if(resp?.status == 200 && resp.data) {
          manifest = resp.data
        }
      }
    } catch(Exception e2) {
      logWarn("Failed to retrieve fallback manifest: ${e2.message}")
    }
  }

  if(!manifest) {
    logWarn('Failed to retrieve package manifest for version checking')
    return
  }

  logDebug("Using manifest version: ${manifest.version}")

  // Check each driver in the manifest
  manifest.drivers?.each { driver ->
    String driverName = driver.name
    String namespace = driver.namespace ?: 'dwinks'
    String expectedVersion = driver.version ?: appVersion

    logDebug("Checking ${driverName} (${namespace})...")
    String installedVersion = getInstalledDriverVersion(driverName, namespace)

    if(installedVersion && installedVersion != expectedVersion) {
      logWarn("Version mismatch: ${driverName} - installed: ${installedVersion}, expected: ${expectedVersion}")
      state.versionMismatches << [
        name: driverName,
        namespace: namespace,
        installedVersion: installedVersion,
        expectedVersion: expectedVersion,
        location: driver.location,
        type: 'driver'
      ]
      state.installedVersions << [
        name: driverName,
        installedVersion: installedVersion,
        expectedVersion: expectedVersion,
        status: 'Mismatch'
      ]
    } else if(installedVersion) {
      logDebug("${driverName} version OK: ${installedVersion}")
      state.installedVersions << [
        name: driverName,
        installedVersion: installedVersion,
        expectedVersion: expectedVersion,
        status: 'OK'
      ]
    } else {
      logDebug("${driverName} not found or could not determine version")
      state.installedVersions << [
        name: driverName,
        installedVersion: 'Not Installed',
        expectedVersion: expectedVersion,
        status: 'Not Found'
      ]
    }
  }

  // Check library files
  // Libraries can be checked if library IDs are configured
  logInfo("Library checking: libraryIdSMAPI=${libraryIdSMAPI}, libraryIdUtilities=${libraryIdUtilities}, manifest.files count=${manifest.files?.size() ?: 0}")
  logInfo("Settings check: libraryIdSMAPI=${settings.libraryIdSMAPI}, libraryIdUtilities=${settings.libraryIdUtilities}")

  if(settings.libraryIdSMAPI && settings.libraryIdUtilities) {
    logInfo("Checking library versions using configured IDs (SMAPI: ${settings.libraryIdSMAPI}, Utilities: ${settings.libraryIdUtilities})...")

    if(!manifest.files || manifest.files.size() == 0) {
      logWarn("No library files found in manifest!")
    } else {
      logInfo("Found ${manifest.files.size()} file(s) in manifest to check")
    }

    manifest.files?.each { file ->
      String fileName = file.name
      String expectedVersion = appVersion

      logInfo("Processing file from manifest: ${fileName}")

      // Determine which library ID to use based on filename
      Integer libraryId = null
      if(fileName.contains('SMAPILibrary')) {
        libraryId = settings.libraryIdSMAPI as Integer
      } else if(fileName.contains('UtilitiesAndLoggingLibrary')) {
        libraryId = settings.libraryIdUtilities as Integer
      }

      if(libraryId) {
        logInfo("Checking library ${fileName} (ID: ${libraryId})...")

        // Determine expected library name and namespace
        String expectedName = ''
        String expectedNamespace = 'dwinks'
        if(fileName.contains('SMAPILibrary')) {
          expectedName = 'SMAPILibrary'
        } else if(fileName.contains('UtilitiesAndLoggingLibrary')) {
          expectedName = 'UtilitiesAndLoggingLibrary'
        }

        Map libraryResult = getInstalledLibraryVersionWithValidation(libraryId, expectedName, expectedNamespace)
        String installedVersion = libraryResult.version
        String error = libraryResult.error

        logInfo("Library ${fileName} installed version: ${installedVersion ?: 'Not found'}, expected: ${expectedVersion}")

        if(error) {
          // Library ID is wrong or other error occurred
          logWarn("Library check failed: ${error}")
          state.installedVersions << [
            name: fileName,
            installedVersion: 'Error',
            expectedVersion: expectedVersion,
            status: error
          ]
        } else if(installedVersion && installedVersion != expectedVersion) {
          logWarn("Version mismatch: ${fileName} - installed: ${installedVersion}, expected: ${expectedVersion}")
          state.versionMismatches << [
            name: expectedName,
            fileName: fileName,
            namespace: expectedNamespace,
            installedVersion: installedVersion,
            expectedVersion: expectedVersion,
            libraryId: libraryId,
            type: 'library'
          ]
          state.installedVersions << [
            name: fileName,
            installedVersion: installedVersion,
            expectedVersion: expectedVersion,
            status: 'Mismatch'
          ]
        } else if(installedVersion) {
          logInfo("${fileName} version OK: ${installedVersion}")
          state.installedVersions << [
            name: fileName,
            installedVersion: installedVersion,
            expectedVersion: expectedVersion,
            status: 'OK'
          ]
        } else {
          logInfo("${fileName} not found or could not determine version")
          state.installedVersions << [
            name: fileName,
            installedVersion: 'Not Installed',
            expectedVersion: expectedVersion,
            status: 'Not Found'
          ]
        }
      } else {
        // Determine which specific library ID is missing
        String missingLibrary = ''
        if(fileName.contains('SMAPILibrary')) {
          missingLibrary = 'SMAPILibrary ID'
        } else if(fileName.contains('UtilitiesAndLoggingLibrary')) {
          missingLibrary = 'UtilitiesAndLoggingLibrary ID'
        }
        logDebug("Library file ${fileName} - no library ID configured for checking")
        state.installedVersions << [
          name: fileName,
          installedVersion: 'N/A',
          expectedVersion: expectedVersion,
          status: "⚠ Configure ${missingLibrary} in settings"
        ]
      }
    }
  } else {
    // No library IDs configured, skip library version checking
    logInfo("Library IDs not configured - skipping library version checks")
    manifest.files?.each { file ->
      String fileName = file.name
      String expectedVersion = appVersion

      logDebug("Library file ${fileName} - version checking not available (configure library IDs in settings)")
      state.installedVersions << [
        name: fileName,
        installedVersion: 'N/A',
        expectedVersion: expectedVersion,
        status: '⚠ Configure Library IDs in settings'
      ]
    }
  }

  if(state.versionMismatches.size() > 0) {
    logInfo("Found ${state.versionMismatches.size()} version mismatch(es)")
  } else {
    logInfo('All installed files are up to date')
  }
}

String getInstalledDriverVersion(String driverName, String namespace) {
  String foundVersion = null

  try {
    // Get authentication cookie
    String cookie = login()
    if(!cookie) {
      logWarn('Failed to authenticate with hub')
      return null
    }

    // Get list of installed drivers - HPM uses /device/drivers
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [Cookie: cookie]
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200) {
        logDebug("Driver list response received, checking for ${driverName} in namespace ${namespace}")

        // Log all user drivers for debugging
        def userDrivers = resp.data?.drivers?.findAll { it.type == 'usr' }
        logDebug("Found ${userDrivers?.size()} user drivers")
        userDrivers?.each { d ->
          logDebug("  - ${d.name} (${d.namespace})")
        }

        // Find driver by name and namespace
        def driver = resp.data?.drivers?.find {
          it.type == 'usr' && it?.name == driverName && it?.namespace == namespace
        }

        if(driver && driver.id) {
          Integer driverId = driver.id
          logDebug("Found driver ${driverName}, getting source code...")

          // Get driver source code using /driver/ajax/code with query parameter
          Map codeParams = [
            uri: "http://127.0.0.1:8080",
            path: '/driver/ajax/code',
            headers: [Cookie: cookie],
            query: [id: driverId]
          ]

          httpGet(codeParams) { codeResp ->
            if(codeResp?.status == 200 && codeResp.data?.source) {
              String source = codeResp.data.source
              // Extract semantic version from source code for comparison
              def matcher = (source =~ /version:\s*['"]([^'"]+)['"]/)
              if(matcher.find()) {
                foundVersion = matcher.group(1)
                logDebug("Extracted version ${foundVersion} from ${driverName}")
              } else {
                logWarn("Could not find version pattern in source for ${driverName}")
              }
            } else {
              logWarn("Failed to get source code for ${driverName}, status: ${codeResp?.status}")
            }
          }
        } else if(!driver) {
          logDebug("Driver not found in hub: ${driverName} (${namespace})")
        }
      } else {
        logWarn("Failed to get driver list, status: ${resp?.status}")
      }
    }
  } catch(Exception e) {
    logWarn("Error getting version for ${driverName}: ${e.message}")
  }

  return foundVersion
}

/**
 * Get the installed version of a library from the hub and validate its identity.
 * @param libraryId The library ID on the hub
 * @param expectedName The expected library name (e.g., 'SMAPILibrary')
 * @param expectedNamespace The expected namespace (e.g., 'dwinks')
 * @return Map with [version: String, error: String] - version is null if error occurred
 */
Map getInstalledLibraryVersionWithValidation(Integer libraryId, String expectedName, String expectedNamespace) {
  Map result = [version: null, error: null]

  try {
    logInfo("Getting library version for ID ${libraryId}...")
    // Get authentication cookie
    String cookie = login()
    if(!cookie) {
      result.error = 'Failed to authenticate with hub'
      logWarn(result.error)
      return result
    }

    // Get library code using /library/ajax/code
    logInfo("Fetching library code for ID ${libraryId}...")
    Map libraryData = getLibraryCode(libraryId, cookie)
    logInfo("Library data received: ${libraryData ? 'Yes' : 'No'}, has source: ${libraryData?.source ? 'Yes' : 'No'}")

    if(!libraryData?.source) {
      result.error = "Library ID ${libraryId} not found on hub"
      logWarn(result.error)
      return result
    }

    String source = libraryData.source

    // Extract library name and namespace from source
    def nameMatch = (source =~ /library\s*\([^)]*name\s*:\s*['"]([^'"]+)['"]/)
    def namespaceMatch = (source =~ /library\s*\([^)]*namespace\s*:\s*['"]([^'"]+)['"]/)

    String actualName = nameMatch ? nameMatch[0][1] : null
    String actualNamespace = namespaceMatch ? namespaceMatch[0][1] : null

    logInfo("Library ID ${libraryId} - Expected: ${expectedName}/${expectedNamespace}, Found: ${actualName}/${actualNamespace}")

    // Validate library identity
    if(actualName != expectedName || actualNamespace != expectedNamespace) {
      result.error = "⚠ Library ID ${libraryId} is '${actualName}' (${actualNamespace}), not '${expectedName}' (${expectedNamespace})"
      logWarn(result.error)
      return result
    }

    // Extract version from library() definition
    result.version = extractLibraryVersion(source)
    logInfo("Extracted version ${result.version} from library ${actualName}")

  } catch(Exception e) {
    result.error = "Error: ${e.message}"
    logWarn("Error getting library version for ID ${libraryId}: ${e.message}")
    logError("Stack trace: ${e}")
  }

  return result
}

String getDriverVersionForUpdate(String driverName, String namespace) {
  String hubVersion = null

  try {
    String cookie = login()
    if(!cookie) { return null }

    // Get list of installed drivers
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [Cookie: cookie]
    ]

    httpGet(params) { resp ->
      def driver = resp.data?.drivers?.find {
        it.type == 'usr' && it?.name == driverName && it?.namespace == namespace
      }

      if(driver?.id) {
        // Get hub's internal version using /driver/ajax/code
        Map codeParams = [
          uri: "http://127.0.0.1:8080",
          path: '/driver/ajax/code',
          headers: [Cookie: cookie],
          query: [id: driver.id]
        ]

        httpGet(codeParams) { codeResp ->
          if(codeResp?.status == 200 && codeResp.data?.version) {
            hubVersion = codeResp.data.version.toString()
            logDebug("Got hub version ${hubVersion} for ${driverName}")
          }
        }
      }
    }
  } catch(Exception e) {
    logWarn("Error getting hub version for ${driverName}: ${e.message}")
  }

  return hubVersion
}

void fixVersionMismatches() {
  if(!state.versionMismatches || state.versionMismatches.size() == 0) {
    logInfo('No version mismatches to fix')
    return
  }

  logInfo("Fixing ${state.versionMismatches.size()} version mismatch(es)...")

  // Process libraries FIRST, then drivers (since drivers depend on libraries)
  // Single-pass partition instead of two findAll traversals
  Map grouped = state.versionMismatches.groupBy { it.type }
  List libraryMismatches = grouped['library'] ?: []
  List driverMismatches = grouped['driver'] ?: []

  // Step 1: Update libraries if any are out of date
  if(libraryMismatches.size() > 0) {
    logInfo("Updating ${libraryMismatches.size()} library/libraries first...")

    libraryMismatches.each { mismatch ->
      logInfo("Updating library ${mismatch.name} from ${mismatch.installedVersion} to ${mismatch.expectedVersion}...")

      // Get current app version to download correct library files
      String appVersion = getActualInstalledAppVersion()
      String libraryUrl = "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/Libraries/${mismatch.fileName}"

      String sourceCode = downloadFile(libraryUrl)

      if(sourceCode) {
        String libraryVersion = extractLibraryVersion(sourceCode)
        Boolean success = publishLibraryToHub(mismatch.libraryId, sourceCode, libraryVersion)
        if(success) {
          logInfo("Successfully updated library ${mismatch.name}")
        } else {
          logWarn("Failed to update library ${mismatch.name}")
        }
      } else {
        logWarn("Failed to download source code for library ${mismatch.name} from ${libraryUrl}")
      }
    }

  }

  // Store driver mismatches for deferred processing
  if(driverMismatches.size() > 0) {
    state.pendingDriverMismatches = driverMismatches
  }

  // Defer driver phase to allow hub to process library updates
  if(libraryMismatches.size() > 0) {
    runIn(3, 'fixDriverMismatches')
  } else {
    fixDriverMismatches()
  }
}

void fixDriverMismatches() {
  List driverMismatches = state.pendingDriverMismatches ?: []
  state.remove('pendingDriverMismatches')

  if(driverMismatches.size() > 0) {
    logInfo("Updating ${driverMismatches.size()} driver(s)...")
    driverMismatches.each { mismatch ->
      logInfo("Updating driver ${mismatch.name} from ${mismatch.installedVersion} to ${mismatch.expectedVersion}...")
      String sourceCode = downloadFile(mismatch.location)
      if(sourceCode) {
        Boolean success = updateDriver(mismatch.name, mismatch.namespace, sourceCode, mismatch.expectedVersion)
        if(success) { logInfo("Successfully updated ${mismatch.name}") }
        else { logWarn("Failed to update ${mismatch.name}") }
      } else {
        logWarn("Failed to download source code for ${mismatch.name}")
      }
    }
    runIn(2, 'checkInstalledVersions')
  } else {
    checkInstalledVersions()
  }
}

void cleanupDuplicates() {
  logInfo("Checking for duplicate apps and drivers...")

  // Clear cached cookie to ensure fresh auth
  state.remove('hubCookie')

  Map results = findAndRemoveDuplicates()

  if(results.appsRemoved > 0 || results.driversRemoved > 0) {
    state.duplicateCleanupResult = "Removed ${results.appsRemoved} duplicate app(s) and ${results.driversRemoved} duplicate driver(s)"
    logInfo(state.duplicateCleanupResult)
  } else if(results.errors.size() > 0) {
    state.duplicateCleanupResult = "Errors during cleanup: ${results.errors.join(', ')}"
    logWarn(state.duplicateCleanupResult)
  } else {
    state.duplicateCleanupResult = "No duplicates found"
    logInfo("No duplicate apps or drivers found")
  }
}

/**
 * Publish libraries to the hub using the configured library IDs.
 * Downloads the latest library files from GitHub and publishes them to the hub.
 */
void publishLibraries() {
  logInfo("Publishing libraries to hub...")

  // Validate library IDs are configured
  if(!settings.libraryIdSMAPI || !settings.libraryIdUtilities) {
    logWarn("Library IDs not configured. Please enter both library IDs in settings.")
    state.lastLibraryPublish = "Failed: Library IDs not configured"
    return
  }

  // Get current app version to determine which files to publish
  String appVersion = getActualInstalledAppVersion()
  if(!appVersion) {
    logWarn("Failed to determine app version")
    state.lastLibraryPublish = "Failed: Could not determine app version"
    return
  }

  logInfo("Publishing libraries for version ${appVersion}")

  // Define libraries to publish
  List libraries = [
    [
      id: settings.libraryIdSMAPI as Integer,
      name: 'SMAPILibrary',
      namespace: 'dwinks',
      location: "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/Libraries/SMAPILibrary.groovy"
    ],
    [
      id: settings.libraryIdUtilities as Integer,
      name: 'UtilitiesAndLoggingLibrary',
      namespace: 'dwinks',
      location: "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/Libraries/UtilitiesAndLoggingLibrary.groovy"
    ]
  ]

  Integer successCount = 0
  Integer failCount = 0
  List errors = []

  libraries.each { library ->
    logInfo("Publishing ${library.name}...")

    try {
      // Download library source code
      String sourceCode = downloadFile(library.location)

      if(!sourceCode) {
        String error = "Failed to download ${library.name}"
        logWarn(error)
        errors << error
        failCount++
        return
      }

      // Extract version from library source
      String libraryVersion = extractLibraryVersion(sourceCode)
      logInfo("Library ${library.name} version: ${libraryVersion}")

      // Publish library to hub
      Boolean success = publishLibraryToHub(library.id, sourceCode, libraryVersion)

      if(success) {
        logInfo("Successfully published ${library.name} version ${libraryVersion}")
        successCount++
      } else {
        String error = "Failed to publish ${library.name}"
        logWarn(error)
        errors << error
        failCount++
      }
    } catch(Exception e) {
      String error = "Error publishing ${library.name}: ${e.message}"
      logError(error)
      errors << error
      failCount++
    }
  }

  // Update status message
  String timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
  if(successCount == libraries.size()) {
    state.lastLibraryPublish = "✓ ${timestamp}: Successfully published ${successCount} libraries"
    logInfo("Library publish completed successfully: ${successCount} published")
  } else if(successCount > 0) {
    state.lastLibraryPublish = "⚠ ${timestamp}: Published ${successCount}, failed ${failCount} (${errors.join(', ')})"
    logWarn("Library publish partially completed: ${successCount} published, ${failCount} failed")
  } else {
    state.lastLibraryPublish = "✗ ${timestamp}: Failed to publish any libraries (${errors.join(', ')})"
    logWarn("Library publish failed: ${errors.join(', ')}")
  }

  // Re-check installed versions to reflect the published libraries
  if(successCount > 0) {
    runIn(1, 'checkInstalledVersions')
  }
}

/**
 * Extract version from library source code by parsing the library() definition.
 * @param sourceCode The library source code
 * @return The version string, or '1.0.0' if not found
 */
String extractLibraryVersion(String sourceCode) {
  logWarn("Extracting library version from source code...")
  try {
    // Look for version: 'x.x.x' or version: "x.x.x" in library() block
    def versionMatch = (sourceCode =~ /library\s*\([^)]*version\s*:\s*['"]([\d.]+)['"]/)
    if(versionMatch) {
      return versionMatch[0][1]
    }
  } catch(Exception e) {
    logWarn("Failed to extract library version: ${e.message}")
  }
  return '1.0.0' // Default version if not found
}

/**
 * Publish a library to the hub using the library ID.
 * Uses the same endpoint pattern as apps/drivers: /library/ajax/update
 * @param libraryId The library ID on the hub
 * @param sourceCode The library source code
 * @param version The library version (for reference)
 * @return true if successful, false otherwise
 */
Boolean publishLibraryToHub(Integer libraryId, String sourceCode, String version) {
  try {
    // Get authentication cookie
    String cookie = login()
    if(!cookie) {
      logWarn("Failed to authenticate with hub for library publish")
      return false
    }

    // First, check if library exists and get current version
    Map currentLibrary = getLibraryCode(libraryId, cookie)

    if(currentLibrary == null) {
      logWarn("Library with ID ${libraryId} not found on hub. Please verify the library ID is correct.")
      return false
    }

    logInfo("Current library version on hub: ${currentLibrary.version}")

    // Prepare form body for update (same format as apps/drivers)
    String body = "id=${libraryId}&version=${currentLibrary.version ?: 0}&source=${java.net.URLEncoder.encode(sourceCode, 'UTF-8')}"

    // Post update to hub
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/library/ajax/update',
      headers: [
        'Cookie': cookie,
        'Content-Type': 'application/x-www-form-urlencoded'
      ],
      body: body,
      timeout: 30,
      ignoreSSLIssues: true
    ]

    Map result = null
    httpPost(params) { resp ->
      if(resp?.status == 200 && resp?.data) {
        result = resp.data
        logDebug("Library update response: ${result}")
      } else {
        logWarn("Unexpected response status: ${resp?.status}")
        return false
      }
    }

    // Check if update was successful
    if(result?.status == 'success') {
      logInfo("Library update successful")
      return true
    } else {
      logWarn("Library update failed: ${result?.errorMessage ?: 'Unknown error'}")
      return false
    }

  } catch(Exception e) {
    logError("Error publishing library to hub: ${e.message}")
    return false
  }
}

/**
 * Get library code from hub by ID.
 * @param libraryId The library ID
 * @param cookie Authentication cookie
 * @return Map with id, version, source, or null if not found
 */
Map getLibraryCode(Integer libraryId, String cookie) {
  try {
    logInfo("Requesting library code for ID ${libraryId} from /library/ajax/code...")
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/library/ajax/code',
      query: [id: libraryId.toString()],
      headers: ['Cookie': cookie],
      timeout: 15,
      ignoreSSLIssues: true
    ]

    Map libraryData = null
    httpGet(params) { resp ->
      logInfo("Library code response status: ${resp?.status}")
      if(resp?.status == 200 && resp?.data) {
        libraryData = resp.data
        logInfo("Library data ID: ${libraryData?.id}, has source: ${libraryData?.source ? 'Yes' : 'No'}")
      } else {
        logWarn("Unexpected response: status ${resp?.status}, data: ${resp?.data}")
      }
    }

    // Verify the library ID matches (apps return 200 with empty payload when not found)
    if(libraryData?.id == libraryId) {
      logInfo("Library ID ${libraryId} found and verified")
      return libraryData
    } else {
      logWarn("Library ID mismatch or not found. Requested: ${libraryId}, Received: ${libraryData?.id}")
      return null
    }

  } catch(Exception e) {
    logWarn("Error getting library code: ${e.message}")
    logError("Stack trace: ${e}")
    return null
  }
}

void uploadBundle(Map bundle) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return
    }

    logInfo("Uploading bundle: ${bundle.name}")

    // Download the bundle ZIP file
    byte[] zipData = null
    Map params = [
      uri: bundle.location,
      contentType: 'application/zip',
      timeout: 300,
      ignoreSSLIssues: true
    ]

    httpGet(params) { resp ->
      zipData = resp.data.bytes
    }

    if(!zipData) {
      logWarn("Failed to download bundle ${bundle.name}")
      return
    }

    // Upload using HPM's installFile pattern
    String boundary = "----WebKitFormBoundaryDtoO2QfPwfhTjOuS"
    String zipFilename = "${bundle.id}-${bundle.name.replaceAll(/[^a-zA-Z0-9]/, '')}.zip"

    Map uploadParams = [
      uri: "http://127.0.0.1:8080",
      path: "/hub/fileManager/upload",
      query: ['folder': '/'],
      headers: [
        'Cookie': cookie,
        'Content-Type': "multipart/form-data; boundary=${boundary}"
      ],
      body: """--${boundary}\nContent-Disposition: form-data; name="uploadFile"; filename="${zipFilename}"\nContent-Type: application/zip\n\n${new String(zipData, 'ISO-8859-1')}\n\n--${boundary}\nContent-Disposition: form-data; name="folder"\n\n\n--${boundary}--""",
      timeout: 300,
      ignoreSSLIssues: true
    ]

    httpPost(uploadParams) { resp ->
      if(resp?.status == 200) {
        logInfo("Successfully uploaded bundle ${bundle.name}")
      }
    }
  } catch(Exception e) {
    logError("Failed to upload bundle ${bundle.name}: ${e.message}")
  }
}

Map downloadManifest(String url) {
  try {
    Map params = [
      uri: url,
      contentType: 'application/json',
      timeout: 15
    ]
    Map manifest = null
    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        manifest = resp.data
      }
    }
    return manifest
  } catch(Exception e) {
    logWarn("Failed to download manifest: ${e.message}")
    return null
  }
}

// =============================================================================
// Component Methods for Child Event Processing
// =============================================================================



// =============================================================================
// HTTP Helpers
// =============================================================================
Map buildLocalControlCallbackData(Object existingData, String requestMethod, String callbackMethod, Map params, Boolean retryable, Boolean isRetryAttempt = false, String requestId = null) {
  Map callbackData = existingData instanceof Map ? new LinkedHashMap(existingData as Map) : [:]
  if(existingData != null && existingData instanceof Map == false) {
    callbackData.originalData = existingData
  }
  callbackData[LOCAL_CONTROL_RETRY_DATA_KEY] = [
    requestId: requestId ?: UUID.randomUUID().toString(),
    method: requestMethod,
    callbackMethod: callbackMethod,
    params: new LinkedHashMap(params),
    retryable: retryable,
    retryAttempt: isRetryAttempt
  ]
  return callbackData
}

void sendLocalCommandAsync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  String callbackMethod = args.callbackMethod ?: 'localControlCallback'
  Map params = args.params ?: [:]
  params.uri = args?.params?.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args?.params?.contentType ?: 'application/json'
  params.requestContentType = args?.params?.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args?.params?.ignoreSSLIssues ?: true
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  Object callbackData = args.data
  if(callbackMethod == 'localControlCallback') {
    Boolean retryable = args.retryable == true
    if(retryable && state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] == null) {
      resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    }
    callbackData = buildLocalControlCallbackData(args.data, 'POST', callbackMethod, params, retryable)
  }
  logTrace("sendLocalCommandAsync: ${params}")
  asynchttpPost(callbackMethod, params, callbackData)
}

Map sendLocalJsonQuerySync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  Map params = args.params ?: [:]
  params.uri = args.params.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args.params.contentType ?: 'application/json'
  params.requestContentType = args.params.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args.params.ignoreSSLIssues ?: true
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  logTrace("sendLocalQuerySync: ${params}")
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data }
    else { logError("sendLocalJsonQuerySync: unexpected response: ${resp?.data}") }
  }
}

void sendLocalQueryAsync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  String callbackMethod = args.callbackMethod ?: 'localControlCallback'
  Map params = args.params ?: [:]
  params.uri = args?.params?.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args?.params?.contentType ?: 'application/json'
  params.requestContentType = args?.params?.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args?.params?.ignoreSSLIssues ?: true
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  Object callbackData = args.data
  if(callbackMethod == 'localControlCallback') {
    if(state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] == null) {
      resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    }
    callbackData = buildLocalControlCallbackData(args.data, 'GET', callbackMethod, params, true)
  }
  logTrace("sendLocalQueryAsync: ${params}")
  asynchttpGet(callbackMethod, params, callbackData)
}

void sendLocalJsonAsync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  String callbackMethod = args.callbackMethod ?: 'localControlCallback'
  Map params = args.params ?: [:]
  params.uri = args?.params?.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args?.params?.contentType ?: 'application/json'
  params.requestContentType = args?.params?.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args?.params?.ignoreSSLIssues ?: true
  params.body = JsonOutput.toJson(args.data)
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  Object callbackData = args.data
  if(callbackMethod == 'localControlCallback') {
    Boolean retryable = args.retryable == true
    if(retryable && state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] == null) {
      resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    }
    callbackData = buildLocalControlCallbackData(args.data, 'POST', callbackMethod, params, retryable)
  }
  logTrace("sendLocalJsonAsync: ${params}")
  asynchttpPost(callbackMethod, params, callbackData)
}

void localControlCallback(AsyncResponse response, Map data) {
  Map retryRequest = data?.get(LOCAL_CONTROL_RETRY_DATA_KEY) instanceof Map ? data[LOCAL_CONTROL_RETRY_DATA_KEY] as Map : null
  Map scheduledRetryRequest = state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] instanceof Map ? state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] as Map : null
  Boolean requestMatchesScheduledRetry = retryRequest?.requestId != null && scheduledRetryRequest?.requestId == retryRequest?.requestId
  if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
    Boolean retryScheduled = false
    if(retryRequest?.retryable == true) {
      if(scheduledRetryRequest == null || requestMatchesScheduledRetry) {
        state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] = retryRequest
        retryScheduled = handleAsyncHttpFailureWithRetry(response, 'executeLocalControlRetry', LOCAL_CONTROL_RETRY_STATE_KEY)
      } else {
        logWarn("Skipping local control auto-retry for ${retryRequest.method} ${retryRequest?.params?.uri} because another retry is already pending")
      }
    }
    if(retryScheduled == false && (scheduledRetryRequest == null || requestMatchesScheduledRetry)) {
      state.remove(LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY)
      resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    }
    return
  }
  if(response?.status == 200 && response && response.hasError() == false) {
    if(retryRequest?.retryable == true && (scheduledRetryRequest == null || scheduledRetryRequest?.requestId == retryRequest?.requestId)) {
      state.remove(LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY)
      resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    }
    logTrace("localControlCallback: ${response.getData()}")
  }
}

void executeLocalControlRetry() {
  Map retryRequest = state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] instanceof Map ? state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] as Map : null
  if(retryRequest?.params instanceof Map == false || retryRequest?.callbackMethod == null || retryRequest?.method == null) {
    logWarn('Unable to execute local control retry because the prior request details were unavailable')
    state.remove(LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY)
    resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    return
  }

  Map params = new LinkedHashMap(retryRequest.params as Map)
  Map callbackData = buildLocalControlCallbackData(null, retryRequest.method as String, retryRequest.callbackMethod as String, params, retryRequest.retryable == true, true, retryRequest.requestId as String)
  logInfo("Retrying local control ${retryRequest.method} request to ${params.uri}")

  if(retryRequest.method == 'GET') {
    asynchttpGet(retryRequest.callbackMethod as String, params, callbackData)
  } else if(retryRequest.method == 'POST') {
    asynchttpPost(retryRequest.callbackMethod as String, params, callbackData)
  } else {
    logWarn("Unsupported local control retry method: ${retryRequest.method}")
    state.remove(LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY)
    resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
  }
}

Boolean shouldRequestZgtResub(String dni) {
  if(dni == null || dni == '') { return false }
  Long nowMs = now()
  Long previous = zgtResubRequestAt.get(dni)
  if(previous != null && (nowMs - previous) < ZGT_RESUB_REQUEST_MIN_INTERVAL_MS) {
    return false
  }
  zgtResubRequestAt.put(dni, nowMs)
  return true
}

Boolean responseIsValid(AsyncResponse response, String requestName = null) {
  if(response == null) {
    logError("${requestName ?: 'Request'} received no response")
    return false
  }
  if(response?.status == 499) {
    try{
      Map errData = response.getErrorData()
      if(errData?.groupStatus == 'GROUP_STATUS_MOVED') {
        ChildDeviceWrapper child = getDeviceFromRincon(errData?.playerId)
        if(child != null && shouldRequestZgtResub(child.getDeviceNetworkId())) {
          child.requestZgtResub()
        }
      }
    } catch(Exception e){}
  } else if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
  }
  if (response.hasError()) { return false } else { return true }
}

// =============================================================================
// Update Management
// =============================================================================

String getCurrentVersion() {
  return app.version ?: '0.7.10'
}

// Get the ACTUAL current version from the hub's app code (not the running instance)
// This is important after updates, as the running instance may have the old version
String getActualInstalledAppVersion() {
  try {
    String cookie = login()
    if(!cookie) {
      logWarn('Failed to authenticate to get app version')
      return getCurrentVersion() // Fallback to instance version
    }

    // Find this app's code ID
    List allApps = getAppCodeList()
    Map targetApp = allApps.find { it.name == 'Sonos Advanced Controller' && it.namespace == 'dwinks' }

    if(!targetApp) {
      logWarn('Could not find app code entry')
      return getCurrentVersion() // Fallback to instance version
    }

    // Query the app code to get its version
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/code',
      headers: ['Cookie': cookie],
      query: [id: targetApp.id],
      timeout: 15,
      ignoreSSLIssues: true
    ]

    String version = null
    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data?.source) {
        // Parse the version from the source code definition
        def matcher = resp.data.source =~ /version:\s*['"]([^'"]+)['"]/
        if(matcher) {
          version = matcher[0][1]
        }
      }
    }

    if(version) {
      logDebug("Got actual installed app version from hub: ${version}")
      return version
    } else {
      logWarn('Could not parse version from app code')
      return getCurrentVersion() // Fallback to instance version
    }
  } catch(Exception e) {
    logError("Error getting actual app version: ${e.message}")
    return getCurrentVersion() // Fallback to instance version
  }
}

void checkForUpdates(Boolean manual = false) {
  // Check if auto-check is enabled or if this is a manual check
  if(!manual && autoCheckUpdates != true) { return }

  // Check frequency limits for automatic checks
  if(!manual && state.lastUpdateCheck) {
    Long lastCheck = state.lastUpdateCheck as Long
    Long now = now()
    Long dayInMs = 86400000L
    Long weekInMs = dayInMs * 7

    if(updateCheckFrequency == 'Daily' && (now - lastCheck) < dayInMs) { return }
    if(updateCheckFrequency == 'Weekly' && (now - lastCheck) < weekInMs) { return }
  }

  try {
    String manifestUrl = 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json'

    Map params = [
      uri: manifestUrl,
      contentType: 'application/json',
      timeout: 15
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        Map manifest = resp.data
        String latestVersion = manifest.version ?: null

        if(latestVersion) {
          String currentVersion = getActualInstalledAppVersion()
          Integer comparison = compareVersions(currentVersion, latestVersion)

          state.lastUpdateCheck = now()
          state.lastUpdateCheckFormatted = new Date().format('yyyy-MM-dd HH:mm:ss')

          if(comparison < 0) {
            // Update available
            state.updateAvailable = true
            state.latestVersion = latestVersion
            state.latestManifest = manifest
            state.latestReleaseDate = manifest.releaseDate ?: 'Unknown'
            state.latestReleaseUrl = "https://github.com/DanielWinks/Hubitat-Public/releases/tag/v${latestVersion}"

            // Update app label - keep simple since updateLabel doesn't render HTML
            app.updateLabel("Sonos Advanced Controller [Update Available]")

            logInfo("Update available: ${latestVersion} (current: ${currentVersion})")

            if(manual) {
              // Force page refresh to show update notification
              runIn(1, 'refreshPage')
            }

            // Auto-install if enabled
            if(autoInstallUpdates == true && !manual) {
              scheduleAutoInstall()
            }
          } else {
            state.updateAvailable = false
            state.latestVersion = null
            state.latestManifest = null
            state.latestReleaseDate = null
            state.latestReleaseUrl = null

            // Clear update label
            app.updateLabel("Sonos Advanced Controller")

            if(manual) {
              logInfo("No updates available. Current version ${currentVersion} is up to date.")
            }
          }
        }
      }
    }
  } catch(Exception e) {
    logError("Error checking for updates: ${e.message}")
    state.lastUpdateCheck = now()
    state.lastUpdateCheckFormatted = new Date().format('yyyy-MM-dd HH:mm:ss')
  }
}

void refreshPage() {
  // Dummy method to trigger page refresh
}

Integer compareVersions(String version1, String version2) {
  // Remove 'v' prefix if present
  version1 = version1?.replaceAll(/^v/, '') ?: '0.0.0'
  version2 = version2?.replaceAll(/^v/, '') ?: '0.0.0'

  List parts1 = version1.tokenize('.')
  List parts2 = version2.tokenize('.')

  Integer maxLength = Math.max(parts1.size(), parts2.size())

  for(Integer i = 0; i < maxLength; i++) {
    Integer num1 = i < parts1.size() ? (parts1[i] as Integer) : 0
    Integer num2 = i < parts2.size() ? (parts2[i] as Integer) : 0

    if(num1 < num2) { return -1 }
    if(num1 > num2) { return 1 }
  }

  return 0
}

void installUpdate() {
  if(!state.updateAvailable || !state.latestManifest) {
    logError('No update available to install')
    return
  }

  try {
    // First, check for and remove any duplicate apps/drivers that may have been created
    logDebug("Checking for duplicate app/driver entries...")
    Map duplicateResults = findAndRemoveDuplicates()
    if(duplicateResults.appsRemoved > 0 || duplicateResults.driversRemoved > 0) {
      logInfo("Cleaned up ${duplicateResults.appsRemoved} duplicate apps and ${duplicateResults.driversRemoved} duplicate drivers")
    }
    if(duplicateResults.errors.size() > 0) {
      logWarn("Some errors occurred during duplicate cleanup: ${duplicateResults.errors}")
    }

    // Clear cached cookie to ensure fresh auth for updates
    state.remove('hubCookie')

    Map manifest = state.latestManifest
    String version = state.latestVersion

    logInfo("Starting update to version ${version}...")

    // Track what needs updating
    Map updateStatus = [
      app: false,
      drivers: [:],
      errors: []
    ]

    // Update the main app
    if(manifest.apps && manifest.apps.size() > 0) {
      Map appInfo = manifest.apps[0]
      String appLocation = appInfo.location

      logDebug("Downloading app from: ${appLocation}")
      String appCode = downloadFile(appLocation)

      if(appCode) {
        logDebug("Downloaded app code (${appCode.length()} bytes)")
        Boolean success = updateThisApp(appCode, version)
        updateStatus.app = success

        if(success) {
          logInfo("App updated successfully to version ${version}")
          // Trigger page refresh after successful app update
          runIn(2, 'refreshPage')
        } else {
          updateStatus.errors << "Failed to update app"
          logError("Failed to update app - check logs for details")
          // Don't stop - continue with driver updates even if app fails
        }
      } else {
        updateStatus.errors << "Failed to download app code"
        logError("Failed to download app code from ${appLocation}")
      }
    }

    // Update drivers
    if(manifest.drivers) {
      manifest.drivers.each { driverInfo ->
        String driverName = driverInfo.name
        String driverLocation = driverInfo.location
        String driverNamespace = driverInfo.namespace ?: 'dwinks'

        logDebug("Downloading driver ${driverName} from: ${driverLocation}")
        String driverCode = downloadFile(driverLocation)

        if(driverCode) {
          logDebug("Downloaded driver ${driverName} code (${driverCode.length()} bytes)")
          Boolean success = updateDriver(driverName, driverNamespace, driverCode, version)
          updateStatus.drivers[driverName] = success

          if(success) {
            logInfo("Driver ${driverName} updated successfully")
          } else {
            updateStatus.errors << "Failed to update driver: ${driverName}"
            logError("Failed to update driver: ${driverName} - check logs for details")
          }
        } else {
          updateStatus.errors << "Failed to download driver: ${driverName}"
          logError("Failed to download driver: ${driverName} from ${driverLocation}")
        }
      }
    }

    // Clear update notification if successful
    if(updateStatus.errors.size() == 0) {
      state.updateAvailable = false
      state.latestVersion = null
      state.latestManifest = null
      state.latestReleaseDate = null
      state.latestReleaseUrl = null

      // Clear stale version check data - the page will refresh and new code will run
      // User can re-run version check after refresh to see current state
      state.remove('versionMismatches')
      state.remove('installedVersions')
      state.remove('lastVersionCheck')

      // Set flag to show success message after page refresh
      state.updateJustCompleted = true

      // Clear update label
      app.updateLabel("Sonos Advanced Controller")

      // Clear any scheduled auto-install
      unschedule('performScheduledInstall')

      logInfo("Update completed successfully! The app will refresh in 3 seconds...")
      // Force page refresh to load new code
      runIn(3, 'refreshPage')
    } else {
      logError("Update completed with errors: ${updateStatus.errors.join(', ')}")
      // Even with errors, if drivers updated, recommend checking versions
      if(updateStatus.drivers.any { it.value == true }) {
        logInfo("Some drivers were updated successfully. The page will refresh in 3 seconds...")
        runIn(3, 'refreshPage')
      }
    }

  } catch(Exception e) {
    logError("Error installing update: ${e.message}")
  }
}

String downloadFile(String uri) {
  try {
    Map params = [
      uri: uri,
      contentType: 'text/plain',
      timeout: 30
    ]

    String fileContent = null

    httpGet(params) { resp ->
      if(resp?.status == 200) {
        fileContent = resp.data.text
      }
    }

    return fileContent
  } catch(Exception e) {
    logError("Error downloading file from ${uri}: ${e.message}")
    return null
  }
}

Boolean updateThisApp(String sourceCode, String newVersionForLogging) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    // Find the app code ID by matching name and namespace
    // Note: app.id is the INSTANCE id, not the CODE id. We need the code ID from /hub2/userAppTypes
    List allApps = getAppCodeList()
    String thisAppName = 'Sonos Advanced Controller'
    String thisAppNamespace = 'dwinks'

    Map targetApp = allApps.find { appEntry ->
      appEntry.name == thisAppName && appEntry.namespace == thisAppNamespace
    }

    if(!targetApp) {
      logError("Could not find app code for ${thisAppName} in namespace ${thisAppNamespace}")
      logDebug("Available apps: ${allApps}")
      return false
    }

    String appCodeId = targetApp.id
    logDebug("Found app code ID: ${appCodeId} for ${thisAppName}")

    // Get the app's current internal version from the hub (HPM uses this for updates)
    Integer currentHubVersion = null
    Map getAppParams = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/code',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: ['Cookie': cookie],
      query: [id: appCodeId],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    try {
      httpGet(getAppParams) { getResp ->
        logDebug("App code response status: ${getResp?.status}")
        logDebug("App code response data: ${getResp.data}")
        if(getResp?.status == 200 && getResp.data?.version != null) {
          currentHubVersion = getResp.data.version as Integer
          logDebug("Got current app version from hub: ${currentHubVersion}")
        } else {
          logWarn("Could not get app version from hub - response: ${getResp.data}")
        }
      }
    } catch(Exception e) {
      logError("Error getting app version: ${e.message}")
    }

    // If we couldn't get the version, we cannot update
    if(currentHubVersion == null) {
      logError("Cannot update app: unable to retrieve current version from hub")
      return false
    }

    logDebug("Updating app to version ${newVersionForLogging}, hub internal version: ${currentHubVersion}")

    // HPM uses /app/ajax/update for existing apps with version parameter
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/update',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: [
        'Connection': 'keep-alive',
        'Cookie': cookie
      ],
      body: [
        id: appCodeId,
        version: currentHubVersion,
        source: sourceCode
      ],
      timeout: 420,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpPost(params) { resp ->
      logDebug("App update response: ${resp.data}")
      // HPM checks for resp.data.status == "success"
      if(resp.data?.status == 'success') {
        logInfo("App updated successfully to version ${newVersionForLogging}")
        result = true
      } else {
        logError("App update failed - response: ${resp.data}")
        result = false
      }
    }
    return result
  } catch(Exception e) {
    logError("Error updating app: ${e.message}")
    return false
  }
}

Boolean updateDriver(String driverName, String namespace, String sourceCode, String newVersionForLogging) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    // Find the driver by name and namespace
    List allDrivers = getDriverList()
    Map targetDriver = allDrivers.find { driver ->
      driver.name == driverName && driver.namespace == namespace
    }

    if(!targetDriver) {
      // Driver doesn't exist - create it using /driver/save (HPM pattern)
      logInfo("Driver not found, creating new driver: ${namespace}.${driverName}")

      Map createParams = [
        uri: "http://127.0.0.1:8080",
        path: '/driver/save',
        requestContentType: 'application/x-www-form-urlencoded',
        headers: [
          'Cookie': cookie
        ],
        body: [
          id: '',
          version: '',
          create: '',
          source: sourceCode
        ],
        timeout: 300,
        ignoreSSLIssues: true
      ]

      Boolean result = false
      httpPost(createParams) { resp ->
        // HPM checks for Location header on successful create
        if(resp.headers?.Location != null) {
          String newId = resp.headers.Location.replaceAll("https?://127.0.0.1:(?:8080|8443)/driver/editor/", "")
          logInfo("Successfully created driver ${driverName} with id ${newId}")
          result = true
        } else {
          logError("Driver ${driverName} creation failed - no Location header")
          result = false
        }
      }
      return result
    }

    // Driver exists - update it using /driver/ajax/update (HPM pattern)
    logInfo("Updating existing driver ${driverName} (id: ${targetDriver.id})")

    // Get driver's current version
    String currentHubVersion = getDriverVersionForUpdate(driverName, namespace) ?: ''
    logDebug("Updating driver ${driverName}: hub version=${currentHubVersion}, new version=${newVersionForLogging}")

    Map updateParams = [
      uri: "http://127.0.0.1:8080",
      path: '/driver/ajax/update',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: [
        'Cookie': cookie
      ],
      body: [
        id: targetDriver.id,
        version: currentHubVersion,
        source: sourceCode
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpPost(updateParams) { resp ->
      logDebug("Driver update response: ${resp.data}")
      // HPM checks for resp.data.status == "success"
      if(resp.data?.status == 'success') {
        logInfo("Successfully updated driver ${driverName}")
        result = true
      } else {
        logError("Driver ${driverName} update failed - response: ${resp.data}")
        result = false
      }
    }
    return result
  } catch(Exception e) {
    logError("Error updating/creating driver ${driverName}: ${e.message}")
    return false
  }
}

List getDriverList() {
  try {
    String cookie = login()
    if(!cookie) { return [] }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [
        'Cookie': cookie
      ],
      timeout: 15
    ]

    List drivers = []

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data?.drivers) {
        // Filter to only user drivers
        drivers = resp.data.drivers.findAll { it.type == 'usr' }
      }
    }

    return drivers
  } catch(Exception e) {
    logError("Error getting driver list: ${e.message}")
    return []
  }
}

// Get list of app code entries (not installed app instances)
List getAppCodeList() {
  try {
    String cookie = login()
    if(!cookie) { return [] }

    // Use /hub2/userAppTypes endpoint like HPM does (requires 2.3.6+)
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/hub2/userAppTypes',
      headers: [
        'Cookie': cookie
      ],
      timeout: 15,
      ignoreSSLIssues: true
    ]

    List apps = []

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        resp.data.each { appEntry ->
          apps << [id: appEntry.id.toString(), name: appEntry.name, namespace: appEntry.namespace]
        }
      }
    }

    return apps
  } catch(Exception e) {
    logError("Error getting app code list: ${e.message}")
    return []
  }
}

// Find and remove duplicate app or driver entries (keeps the one with lowest ID, removes others)
Map findAndRemoveDuplicates() {
  Map results = [appsRemoved: 0, driversRemoved: 0, errors: []]

  try {
    String cookie = login()
    if(!cookie) {
      results.errors << "Failed to authenticate"
      return results
    }

    // Check for duplicate apps
    List allApps = getAppCodeList()
    Map appsByNamespace = [:].withDefault { [] }
    allApps.each { appEntry ->
      String key = "${appEntry.namespace}:${appEntry.name}"
      appsByNamespace[key] << appEntry
    }

    appsByNamespace.each { key, entries ->
      if(entries.size() > 1) {
        logWarn("Found ${entries.size()} duplicate apps for ${key}")
        // Sort by ID (keep lowest, which is usually the original)
        entries.sort { Integer.parseInt(it.id) }
        // Remove all but the first (lowest ID)
        entries.drop(1).each { duplicate ->
          logInfo("Removing duplicate app: ${duplicate.name} (id: ${duplicate.id})")
          if(uninstallAppCode(duplicate.id, cookie)) {
            results.appsRemoved++
          } else {
            results.errors << "Failed to remove app ${duplicate.name} (id: ${duplicate.id})"
          }
        }
      }
    }

    // Check for duplicate drivers
    List allDrivers = getDriverList()
    Map driversByNamespace = [:].withDefault { [] }
    allDrivers.each { driverEntry ->
      String key = "${driverEntry.namespace}:${driverEntry.name}"
      driversByNamespace[key] << driverEntry
    }

    driversByNamespace.each { key, entries ->
      if(entries.size() > 1) {
        logWarn("Found ${entries.size()} duplicate drivers for ${key}")
        // Sort by ID (keep lowest, which is usually the original)
        entries.sort { it.id as Integer }
        // Remove all but the first (lowest ID)
        entries.drop(1).each { duplicate ->
          logInfo("Removing duplicate driver: ${duplicate.name} (id: ${duplicate.id})")
          if(uninstallDriverCode(duplicate.id.toString(), cookie)) {
            results.driversRemoved++
          } else {
            results.errors << "Failed to remove driver ${duplicate.name} (id: ${duplicate.id})"
          }
        }
      }
    }

  } catch(Exception e) {
    logError("Error finding/removing duplicates: ${e.message}")
    results.errors << e.message
  }

  if(results.appsRemoved > 0 || results.driversRemoved > 0) {
    logInfo("Removed ${results.appsRemoved} duplicate apps and ${results.driversRemoved} duplicate drivers")
  }

  return results
}

// Uninstall app code (not app instance) - uses HPM-style endpoint
Boolean uninstallAppCode(String appCodeId, String cookie) {
  try {
    // Use newer endpoint if available (2.3.8+), fallback to older method
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: "/app/edit/deleteJsonSafe/${appCodeId}",
      headers: [
        'Cookie': cookie
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpGet(params) { resp ->
      if(resp.data?.status == true) {
        result = true
      }
    }
    return result
  } catch(Exception e) {
    logError("Error uninstalling app code ${appCodeId}: ${e.message}")
    return false
  }
}

// Uninstall driver code - uses HPM-style endpoint
Boolean uninstallDriverCode(String driverCodeId, String cookie) {
  try {
    // Use newer endpoint if available (2.3.7+)
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: "/driver/editor/deleteJson/${driverCodeId}",
      headers: [
        'Cookie': cookie
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpGet(params) { resp ->
      if(resp.data?.status == true) {
        result = true
      }
    }
    return result
  } catch(Exception e) {
    logError("Error uninstalling driver code ${driverCodeId}: ${e.message}")
    return false
  }
}

String login() {
  // Reuse a recent cookie, but treat it as stale after HUB_COOKIE_MAX_AGE_MS --
  // hub sessions expire, and a cookie trusted forever breaks version checks and
  // library publishing until it is manually cleared.
  Long cookieAgeMs = state.hubCookieTime != null ? now() - (state.hubCookieTime as Long) : null
  if(state.hubCookie && cookieAgeMs != null && cookieAgeMs < HUB_COOKIE_MAX_AGE_MS) {
    logDebug("Reusing existing cookie")
    return state.hubCookie
  }
  state.remove('hubCookie')
  state.remove('hubCookieTime')

  try {
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/login',
      requestContentType: 'application/x-www-form-urlencoded',
      body: [
        username: '',
        password: '',
        submit: 'Login'
      ],
      followRedirects: false,
      textParser: true,
      timeout: 15
    ]

    String cookie = null

    httpPost(params) { resp ->
      logDebug("Login response status: ${resp?.status}")
      if(resp?.status == 200 || resp?.status == 302) {
        def setCookieHeader = resp.headers['Set-Cookie']
        if(setCookieHeader) {
          String cookieValue = setCookieHeader.value ?: setCookieHeader.toString()
          cookie = cookieValue.split(';')[0]
          state.hubCookie = cookie  // Store for reuse like HPM
          state.hubCookieTime = now()
          logDebug("Got cookie: ${cookie?.take(20)}...")
        } else {
          logWarn("No Set-Cookie header in login response")
        }
      } else {
        logWarn("Unexpected login status: ${resp?.status}")
      }
    }

    if(!cookie) {
      logWarn("Failed to get authentication cookie")
    }
    return cookie
  } catch(Exception e) {
    logError("Login error: ${e.message}")
    return null
  }
}

void scheduleUpdateCheck() {
  unschedule('checkForUpdates')

  if(autoCheckUpdates == true) {
    if(updateCheckFrequency == 'Daily') {
      schedule('0 0 2 * * ?', 'checkForUpdates')  // 2 AM daily
    } else if(updateCheckFrequency == 'Weekly') {
      schedule('0 0 2 ? * MON', 'checkForUpdates')  // 2 AM Monday
    }
  }
}

void scheduleAutoInstall() {
  if(!autoInstallUpdates || !autoInstallTime || !state.updateAvailable) {
    unschedule('performScheduledInstall')
    return
  }

  try {
    Date installTime = timeToday(autoInstallTime, location.timeZone)
    String cronExpression

    if(autoInstallNextOccurrence == true) {
      // Install at next occurrence of time, regardless of day
      cronExpression = "0 ${installTime.minutes} ${installTime.hours} * * ?"

      Date now = new Date()
      if(installTime.before(now)) {
        logDebug("Auto-install time has passed today, will install tomorrow at ${autoInstallTime}")
      } else {
        logDebug("Auto-install scheduled for today at ${autoInstallTime}")
      }
    } else {
      // Install on specific day of week
      String dayOfWeek = autoInstallDayOfWeek ?: 'Sunday'
      Map dayMap = [
        'Sunday': 'SUN',
        'Monday': 'MON',
        'Tuesday': 'TUE',
        'Wednesday': 'WED',
        'Thursday': 'THU',
        'Friday': 'FRI',
        'Saturday': 'SAT'
      ]
      String cronDay = dayMap[dayOfWeek]
      cronExpression = "0 ${installTime.minutes} ${installTime.hours} ? * ${cronDay}"

      logDebug("Auto-install scheduled for ${dayOfWeek}s at ${autoInstallTime}")
    }

    schedule(cronExpression, 'performScheduledInstall')
  } catch(Exception e) {
    logError("Error scheduling auto-install: ${e.message}")
  }
}

void performScheduledInstall() {
  if(!state.updateAvailable) {
    logInfo("Scheduled install called but no update available")
    return
  }

  logInfo("Performing scheduled automatic update installation...")
  installUpdate()
}
