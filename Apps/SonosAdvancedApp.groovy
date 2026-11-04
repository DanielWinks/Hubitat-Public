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
@Field static final List<String> APP_LOG_LEVELS = ['trace', 'debug', 'info', 'warn', 'error', 'off']

void logError(String message) {
  if(loggingEnabled('error')) {
    if(app) { log.error("${app.label ?: app.name}: ${message}") }
  }
}
void logWarn(String message) {
  if(loggingEnabled('warn')) {
    if(app) { log.warn("${app.label ?: app.name}: ${message}") }
  }
}
void logInfo(String message) {
  if(loggingEnabled('info')) {
    if(app) { log.info("${app.label ?: app.name}: ${message}") }
  }
}
void logDebug(String message) {
  if(loggingEnabled('debug')) {
    if(app) { log.debug("${app.label ?: app.name}: ${message}") }
  }
}
void logTrace(String message) {
  if(loggingEnabled('trace')) {
    if(app) { log.trace("${app.label ?: app.name}: ${message}") }
  }
}

String getConfiguredLogLevel() {
  if(settings?.logLevel != null) { return normalizeLogLevel(settings.logLevel.toString()) }
  if(settings?.logEnable == false) { return 'off' }
  if(settings?.traceLogEnable == true) { return 'trace' }
  if(settings?.debugLogEnable == true) { return 'debug' }
  return 'info'
}

String normalizeLogLevel(String level) {
  String normalized = level?.toLowerCase()
  return APP_LOG_LEVELS.contains(normalized) ? normalized : 'info'
}

Boolean loggingEnabled(String messageLevel) {
  Integer configuredRank = APP_LOG_LEVELS.indexOf(getConfiguredLogLevel())
  Integer messageRank = APP_LOG_LEVELS.indexOf(messageLevel)
  return configuredRank >= 0 && messageRank >= configuredRank && messageRank < APP_LOG_LEVELS.indexOf('off')
}

/** Apply a changed preference during the submitOnChange page refresh. */
void applyLogLevelChange() {
  String selectedLevel = getConfiguredLogLevel()
  if(state.logLevel == null) {
    // Use the previous boolean preferences as the migration baseline.
    state.logLevel = selectedLevel
    return
  }

  String previousLevel = normalizeLogLevel(state.logLevel.toString())
  if(previousLevel == selectedLevel) { return }

  state.logLevel = selectedLevel
  String changeMessage = "Logging level changed from ${previousLevel} to ${selectedLevel}"
  switch(selectedLevel) {
    case 'warn': log.warn("${app.label ?: app.name}: ${changeMessage}"); break
    case 'error': log.error("${app.label ?: app.name}: ${changeMessage}"); break
    case 'off': log.warn("${app.label ?: app.name}: ${changeMessage}"); break
    default: log.info("${app.label ?: app.name}: ${changeMessage}")
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
  version: '0.13.3',
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
@Field static final String GROUP_COMMAND_REQUEST_EVENT = 'dwinksSonosAdvancedGroupCommand'
@Field static final String PLAYER_STATE_UPDATE_EVENT = 'dwinksSonosAdvancedPlayerState'
@Field static final String PLAYER_PLAYBACK_OBSERVATIONS_STATE_KEY = 'sonosPlayerPlaybackObservations'
@Field static final String OFFLINE_SONOS_PLAYERS_STATE_KEY = 'offlineSonosPlayers'
@Field static final String PLAYER_COMMAND_REQUEST_ATTRIBUTE = 'playerCommandRequest'
@Field static final String PLAYER_COMMAND_QUEUE_STATE_KEY = 'pendingPlayerCommandRequests'
@Field static final String PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY = 'receivedPlayerCommandRequestIds'
@Field static final String GROUP_FAVORITE_OPERATION_ATTRIBUTE = 'groupFavoriteOperation'
@Field static final String GROUP_OPERATION_STATE_KEY = 'activeGroupFavoriteOperation'
@Field static final String GROUPING_CONTEXTS_STATE_KEY = 'groupingContexts'
@Field static final String GROUP_COMMAND_QUEUE_STATE_KEY = 'pendingGroupCommandRequests'
@Field static final String DEFERRED_GROUP_COMMANDS_STATE_KEY = 'deferredGroupOperationCommands'
@Field static final String GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY = 'groupAnnouncementQueues'
@Field static final String GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY = 'groupAnnouncementTopologyTransactions'
@Field static final String DEFERRED_PLAYER_COMMANDS_STATE_KEY = 'deferredGroupPlayerCommands'
@Field static final String GROUPING_MODE_EXPLICIT = 'EXPLICIT'
@Field static final String GROUPING_MODE_ADDITIVE = 'ADDITIVE'
@Field static final String GROUPING_MODE_CURRENT = 'CURRENT'
@Field static final String GROUP_DEVICE_DNI_MARKER = '-SonosGroupDevice-'
@Field static final Integer GROUP_OPERATION_POLL_SECONDS = 1
@Field static final Integer TOPOLOGY_CHANGE_SUPPRESSION_SECONDS = 15
@Field static final Integer GROUP_OPERATION_DEADLINE_SECONDS = 60
@Field static final Integer GROUPING_CONTEXT_TTL_SECONDS = 15
@Field static final Integer GROUP_OPERATION_STABILITY_OBSERVATIONS = 2
@Field static final Integer GROUP_OPERATION_MAX_TOPOLOGY_ATTEMPTS = 4
@Field static final Integer GROUP_OPERATION_COMMAND_WAIT_SECONDS = 3
@Field static final Integer GROUP_OPERATION_TOPOLOGY_COMMAND_WAIT_SECONDS = 10
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
  applyLogLevelChange()
  if(settings.useOldUi == true) {
    stopDiscovery()
  }
  logTrace("Rendering mainPage; updateAvailable=${state.updateAvailable == true}, asyncCheckInFlight=${state.updateCheckRequestId != null}")
  checkForUpdates()
  logTrace('mainPage returned from asynchronous update-check dispatch')
  dynamicPage(title: 'Sonos Advanced Controller') {
    section {
      paragraph 'This application provides Advanced Sonos Player control, including announcements and grouping.'

      if(state.updateAvailable == true) {
        String currentVer = state.installedAppVersion?.toString() ?: getCurrentVersion()
        logTrace("Rendering cached update notice: installed=${currentVer}, latest=${state.latestVersion}, cachePresent=${state.installedAppVersion != null}")
        section('<b style="color: #ff6b6b;">⚠️ Update Available</b>', hideable: false) {
          paragraph "<b>Version ${state.latestVersion} is available!</b><br/>" +
                    "Current version: ${currentVer}<br/>" +
                    "Released: ${state.latestReleaseDate}<br/><br/>" +
                    "<a href='${state.latestReleaseUrl}' target='_blank'>View Release Notes</a>"
          input 'btnInstallUpdate', 'button', title: 'Install Update', submitOnChange: false
          input 'btnDismissUpdate', 'button', title: 'Dismiss', submitOnChange: false
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

    section('Announcement Settings') {
      input 'groupAnnouncementMode', 'enum', title: 'Normal priority announcement method',
        options: [
          automatic: 'Automatic',
          alwaysNonInterrupting: 'Always non-interrupting',
          alwaysCoordinatorStream: 'Always standard stream'
        ], required: true, defaultValue: 'automatic', submitOnChange: true
      input 'highPriorityAnnouncementMode', 'enum', title: 'High priority announcement method',
        options: [
          automatic: 'Automatic',
          alwaysNonInterrupting: 'Always non-interrupting',
          alwaysCoordinatorStream: 'Always standard stream'
        ], required: true, defaultValue: 'automatic', submitOnChange: true
      input 'automaticTtsTopologyChanges', 'bool', title: 'Automatic TTS topology changes',
        required: true, defaultValue: true, submitOnChange: true
      paragraph 'Choose the routing policy independently for normal and high priority announcements. <b>Automatic:</b> use synchronized audio clips while a player or group is playing another source; otherwise use a standard stream when the target topology can be prepared safely. <b>Always non-interrupting:</b> use synchronized audio clips even when players are idle, preserving current playback. <b>Always standard stream:</b> use a standard stream on the group coordinator; this can replace current playback and may force temporary topology changes.'
      if(settings.automaticTtsTopologyChanges == false || settings.automaticTtsTopologyChanges?.toString() == 'false') {
        if(getAnnouncementMode(false) == 'alwaysCoordinatorStream' || getAnnouncementMode(true) == 'alwaysCoordinatorStream') {
          paragraph '<span style="color:#ff6b6b"><b>Warning:</b> With automatic topology changes off, a standard-stream announcement to a group that is not currently grouped together may play only on the coordinator and any players already grouped with it.</span>'
        }
      }
      if(isAutomaticTtsTopologyChangesEnabled() &&
          (getAnnouncementMode(false) == 'alwaysCoordinatorStream' || getAnnouncementMode(true) == 'alwaysCoordinatorStream')) {
        paragraph '<span style="color:#ff6b6b"><b>Warning:</b> Always standard stream with automatic topology changes can force temporary regrouping, including changes to active outside groups. Playback in those groups may be interrupted. The app attempts to restore their original topology after the announcement.</span>'
      }
    }

    section('Update Settings:', hideable: true, hidden: true) {
      paragraph "<span style='color: #ff6b6b;'><b>⚠ Warning:</b> The built-in auto-update functionality should not be used if you manage this app through Hubitat Package Manager (HPM). Using both may cause conflicts, or version mismatches. If you installed via HPM, use HPM to manage updates.</span>"
      paragraph 'If Hub Login Security is enabled, enter the local hub credentials used by this app.'
      input 'hubSecurityUsername', 'text', title: 'Hub Login Security username', required: false
      input 'hubSecurityPassword', 'password', title: 'Hub Login Security password', required: false
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
      input 'logLevel', 'enum', title: 'Logging level',
        options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'],
        required: false, defaultValue: getConfiguredLogLevel(), submitOnChange: true
    }

    section('Interface Settings:', hideable: true) {
      input 'useOldUi', 'bool', title: 'Use old UI', required: false, defaultValue: false, submitOnChange: true
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
    logTrace('Starting a new discovery session; clearing per-session probe reservations')
    subscribeToSsdpEvents(location)
    atomicState.discoveryRunning = true
    ssdpDiscover()
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
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border: 1px solid var(--surface-border, #E0E0E0);
        border-collapse: collapse;
        border-radius: 4px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        color: var(--text-color, #424242);
        min-width: 720px;
        width: 100%;
      }
      #new-ui-speaker-table th,
      #new-ui-speaker-table td {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border-bottom: 1px solid var(--surface-border, #EEEEEE);
        border-right: 1px solid var(--surface-border, #EEEEEE);
        color: var(--text-color, #424242);
        font-size: 14px !important;
        padding: 6px 4px !important;
        text-align: center;
      }
      #new-ui-speaker-table thead {
        background-color: var(--surface-b, #F5F5F5);
      }
      #new-ui-speaker-table th {
        background-color: var(--surface-b, #F5F5F5);
        border-bottom: 2px solid var(--surface-border, #E0E0E0);
        color: var(--text-color, #424242);
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
        color: var(--text-color, #424242);
        font-size: 18px !important;
        font-weight: 400;
        line-height: 1.2;
        margin: 0 0 12px !important;
        padding: 0 !important;
      }
      #new-ui-group-table {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border: 1px solid var(--surface-border, #E0E0E0);
        border-collapse: collapse;
        border-radius: 4px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        color: var(--text-color, #424242);
        min-width: 760px;
        width: 100%;
      }
      #new-ui-group-table th,
      #new-ui-group-table td {
        background-color: var(--surface-card, var(--surface-a, #ffffff));
        border-bottom: 1px solid var(--surface-border, #EEEEEE);
        border-right: 1px solid var(--surface-border, #EEEEEE);
        color: var(--text-color, #424242);
        font-size: 14px !important;
        padding: 8px 6px !important;
        text-align: center;
        vertical-align: middle;
      }
      #new-ui-group-table thead {
        background-color: var(--surface-b, #F5F5F5);
      }
      #new-ui-group-table th {
        background-color: var(--surface-b, #F5F5F5);
        border-bottom: 2px solid var(--surface-border, #E0E0E0);
        color: var(--text-color, #424242);
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
      String actionMarkup = newUiButtonLink("newUiEditGroup|${token}", editIcon, 'var(--text-color, #424242)', '19px') +
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
  refreshStaticDriverActivePlayerRoster()
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
  refreshStaticDriverActivePlayerRoster()
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

@Field static final String STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY = 'staticDriverActivePlayerRoster'
@Field static final Integer STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_VERSION = 1

/** Return the player roster published during app lifecycle/device changes. */
Map<String, Object> getStaticDriverActivePlayerRoster() {
  Object cachedRoster = state[STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY]
  if(!(cachedRoster instanceof Map)) { return null }
  return new LinkedHashMap<String, Object>((Map<String, Object>)cachedRoster)
}

/** Refresh the driver cleanup snapshot after child-device changes. */
void refreshStaticDriverActivePlayerRoster() {
  try {
    List<Map<String, String>> roster = []
    List<ChildDeviceWrapper> children = app.getChildDevices()
    if(children == null) {
      logWarn('Could not refresh active player roster cache: child device list was unavailable')
      return
    }
    children.each { ChildDeviceWrapper child ->
      String playerId = child.getDataValue('id')
      String dni = child.getDeviceNetworkId()
      if(playerId != null && playerId != '' && dni != null && dni != '') {
        roster.add([playerId: playerId, dni: dni])
      }
    }
    Long refreshedAt = (now() / 1000L) as Long
    state[STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY] = [
      version: STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_VERSION,
      refreshedAt: refreshedAt,
      players: roster
    ]
  } catch(Exception e) {
    logWarn("Could not refresh active player roster cache: ${e.message}")
  }
}

void configure() {
  applyLogLevelChange()
  logInfo("${app.name} updated")
  unsubscribe()
  scheduleUpdateCheck()
  checkForUpdates()

  // Initialize settings with defaults
  if(settings.skipOrphanRemoval == null) { settings.skipOrphanRemoval = false }
  if(settings.descriptionTextEnable == null) { settings.descriptionTextEnable = true }

  try { createPlayerDevices() }
  catch (Exception e) { logError("createPlayerDevices() Failed: ${e}")}
  try { createGroupDevices() }
  catch (Exception e) { logError("createGroupDevices() Failed: ${e}")}
  refreshStaticDriverActivePlayerRoster()
  unschedule('refreshStaticDriverActivePlayerRoster')
  schedule('0 */5 * * * ?', 'refreshStaticDriverActivePlayerRoster')
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
    String requestId = UUID.randomUUID().toString()
    state.ttsVoiceRefreshRequestId = requestId
    asynchttpGet('refreshTTSVoiceCacheCallback', [
      uri: 'http://127.0.0.1:8080/hub/details/json?reloadAccounts=false',
      contentType: 'application/json', requestContentType: 'application/json', timeout: 10
    ], [requestId: requestId, voiceNames: voiceNames])
  } catch(Exception e) {
    logError("Error refreshing TTS voice cache: ${e.message}")
  }
}

void refreshTTSVoiceCacheCallback(AsyncResponse response, Map data) {
  if(!data?.requestId || data.requestId != state.ttsVoiceRefreshRequestId) { return }
  state.remove('ttsVoiceRefreshRequestId')
  String defaultVoice = 'Matthew'
  try {
    if(response != null && !response.hasError() && response.status == 200) {
      Object json = response.getJson()
      if(json instanceof Map && ((Map)json).ttsCurrent) { defaultVoice = ((Map)json).ttsCurrent.toString() }
    } else {
      logWarn('Could not retrieve current TTS voice from hub')
    }
  } catch(Exception e) {
    logWarn("Could not retrieve current TTS voice from hub: ${e.message}")
  }
  List<String> voiceNames = data.voiceNames instanceof List ? (List<String>)data.voiceNames : []
  getCurrentPlayerDevices().each { ChildDeviceWrapper cd ->
    try { cd.updateTTSVoiceCache(voiceNames, defaultVoice) }
    catch (Exception e) { logDebug("Failed to update TTS cache for ${cd.label}: ${e.message}") }
  }
  getCurrentGroupDevices().each { ChildDeviceWrapper cd ->
    try { cd.updateTTSVoiceCache(voiceNames, defaultVoice) }
    catch (Exception e) { logDebug("Failed to update TTS cache for ${cd.label}: ${e.message}") }
  }
  logInfo("TTS voice cache refreshed: ${voiceNames.size()} voices, default: ${defaultVoice}")
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
 * Group devices publish command requests as location events, keeping the
 * group driver from synchronously entering the app while holding its gate.
 */
void subscribeToGroupCommandRequests() {
  unsubscribe(location, GROUP_COMMAND_REQUEST_EVENT, 'groupCommandRequestHandler')
  subscribe(location, GROUP_COMMAND_REQUEST_EVENT, 'groupCommandRequestHandler')
  unsubscribe(location, PLAYER_STATE_UPDATE_EVENT, 'playerStateUpdateHandler')
  subscribe(location, PLAYER_STATE_UPDATE_EVENT, 'playerStateUpdateHandler')
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
  refreshStaticDriverActivePlayerRoster()
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
  refreshStaticDriverActivePlayerRoster()
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
  refreshStaticDriverActivePlayerRoster()
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
  refreshStaticDriverActivePlayerRoster()
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
    logTrace('Starting a fresh discovery session while retaining devices already found')
    atomicState.discoveryRunning = true
    atomicState.discoveryProbeAttempts = [:]
    subscribeToSsdpEvents(location)
    sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
    sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))
    startMdnsDiscovery()
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
	atomicState.discoveryProbeAttempts = [:]
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
    runIn(5, 'processMdnsDiscovery')
  } catch(Exception e) {
    logWarn("mDNS discovery registration failed: ${e.message}")
  }
}

void processMdnsDiscovery() {
  if(atomicState.discoveryRunning != true) {
    logDebug("processMdnsDiscovery: discovery not running, skipping")
    return
  }
  try {
    logDebug("Processing mDNS discovered devices...")

    Integer totalEntries = 0
    ['_sonos._tcp', '_http._tcp'].each { String serviceType ->
      List<Map> mdnsEntries = (getMDNSEntries(serviceType) ?: []) as List<Map>
      totalEntries += mdnsEntries.size()
      logTrace("Found ${mdnsEntries.size()} entries for mDNS service ${serviceType}")
      mdnsEntries.each { Map entry ->
        String serviceName = (entry.server ?: entry.name ?: '').toString()
        String addresses = (entry.ip4Addresses ?: entry.ipAddress ?: '').toString()
        String ipAddress = addresses.tokenize(',')?.first()?.trim()
        Integer port = (entry.port ?: 0) as Integer

        logTrace("mDNS entry: service=${serviceType}, name=${serviceName}, ip=${ipAddress}, port=${port}, raw=${entry}")

        if(serviceType == '_sonos._tcp' || serviceName.toLowerCase().contains('sonos') || (ipAddress && port == 1400)) {
          logDebug("Found potential Sonos device via mDNS: ${serviceName} at ${ipAddress}:${port}")
          processDiscoveredSonosDevice(ipAddress)
        } else {
          logTrace("Ignoring non-Sonos mDNS entry ${serviceName} (${serviceType}) at ${ipAddress}:${port}")
        }
      }
    }

    logDebug("Processed ${totalEntries} mDNS entries")

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

Boolean reserveDiscoveryProbe(String ipAddress) {
  if(!ipAddress) {
    logTrace('Skipping discovery probe because the candidate IP address is empty')
    return false
  }
  if(atomicState.discoveryRunning != true) {
    logTrace("Skipping discovery probe for ${ipAddress}; discovery session is stopped")
    return false
  }
  synchronized(this) {
    Map attempts = (atomicState.discoveryProbeAttempts ?: [:]) as Map
    // Keep every IP reserved for the full discovery session, including failures and
    // unsupported secondary speakers. A new session resets this map in ssdpDiscover().
    if(attempts.containsKey(ipAddress)) {
      logTrace("Skipping discovery probe for ${ipAddress}; already attempted this session at ${attempts[ipAddress]}")
      return false
    }
    Map updated = new LinkedHashMap(attempts)
    updated[ipAddress] = now()
    atomicState.discoveryProbeAttempts = updated
    logTrace("Reserved ${ipAddress} for a discovery probe; ${updated.size()} unique IP(s) attempted this session")
    return true
  }
}

void requestDiscoveryDeviceDescription(String ipAddress, LinkedHashMap playerInfo) {
  if(atomicState.discoveryRunning != true) {
    logTrace("Skipping device-description request for ${ipAddress}; discovery stopped before player-info callback")
    return
  }
  Map params = [uri: "http://${ipAddress}:1400/xml/device_description.xml", timeout: 3,
    requestContentType: 'application/xml', contentType: 'application/xml']
  try {
    logTrace("Dispatching asynchronous device-description probe for ${ipAddress} (timeout=3s)")
    asynchttpGet('discoveryDeviceDescriptionCallback', params, [ipAddress: ipAddress, playerInfo: playerInfo])
  } catch(Exception e) {
    logTrace("Could not dispatch device-description probe for ${ipAddress}: ${e.message}")
  }
}

void discoveryPlayerInfoCallback(AsyncResponse response, Map data) {
  String ipAddress = data?.ipAddress?.toString()
  if(!ipAddress) { logTrace('Player-info callback received without an IP address'); return }
  if(atomicState.discoveryRunning != true) {
    logTrace("Ignoring player-info callback for ${ipAddress}; discovery session has stopped")
    return
  }
  if(response == null || response.hasError()) {
    logTrace("Player-info probe failed for ${ipAddress}: status=${response?.status}, error=${response?.getErrorMessage()}")
    return
  }
  try {
    Map playerInfo = response.getJson() as Map
    if(!playerInfo) {
      logTrace("Player-info probe for ${ipAddress} returned an empty or non-object JSON response (status=${response.status})")
      return
    }
    logTrace("Player-info probe succeeded for ${ipAddress} (status=${response.status}, playerId=${playerInfo.playerId}, name=${playerInfo.device?.name})")
    requestDiscoveryDeviceDescription(ipAddress, new LinkedHashMap(playerInfo))
  } catch(Exception e) { logTrace("Could not parse player-info response for ${ipAddress} (status=${response.status}): ${e.message}") }
}

void discoveryDeviceDescriptionCallback(AsyncResponse response, Map data) {
  String ipAddress = data?.ipAddress?.toString()
  if(!ipAddress) { logTrace('Device-description callback received without an IP address'); return }
  if(atomicState.discoveryRunning != true) {
    logTrace("Ignoring device-description callback for ${ipAddress}; discovery session has stopped")
    return
  }
  if(response == null || response.hasError()) {
    logTrace("Device-description probe failed for ${ipAddress}: status=${response?.status}, error=${response?.getErrorMessage()}")
    return
  }
  try {
    String responseBody = response.getData() as String
    if(!responseBody) {
      logTrace("Device-description probe for ${ipAddress} returned an empty body (status=${response.status})")
      return
    }
    GPathResult description = new XmlSlurper().parseText(responseBody)
    logTrace("Device-description probe succeeded for ${ipAddress} (status=${response.status}, model=${description.device.modelName.text()})")
    processSonosDeviceInfo(data.playerInfo as LinkedHashMap, description, ipAddress)
  } catch(Exception e) { logTrace("Could not parse device-description response for ${ipAddress} (status=${response.status}): ${e.message}") }
}

void requestDiscoveryPlayerInfo(String ipAddress) {
  if(atomicState.discoveryRunning != true) {
    logTrace("Skipping player-info request for ${ipAddress}; discovery session has stopped")
    return
  }
  Map params = [uri: "${getLocalApiPrefix("${ipAddress}:1443")}/players/local/info",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json', contentType: 'application/json', ignoreSSLIssues: true, timeout: 3]
  Map callbackData = [ipAddress: ipAddress]
  try {
    logTrace("Dispatching asynchronous player-info probe for ${ipAddress}:1443 (timeout=3s)")
    asynchttpGet('discoveryPlayerInfoCallback', params, callbackData)
  } catch(Exception e) { logTrace("Could not dispatch player-info probe for ${ipAddress}: ${e.message}") }
}

void processDiscoveredSonosDevice(String ipAddress) {
  if(!ipAddress) { return }
  if(atomicState.discoveryRunning != true) {
    logDebug("processDiscoveredSonosDevice: discovery not running, skipping")
    return
  }

  logTrace("Considering mDNS discovery candidate at ${ipAddress}")
  if(isIpAlreadyDiscovered(ipAddress)) {
    logTrace("Skipping mDNS candidate ${ipAddress}; device is already in discovered results")
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
  if(atomicState.discoveryRunning != true) { return }
  LinkedHashMap parsedEvent = parseLanMessage(event?.description)
  processParsedSsdpEvent(parsedEvent)
}

@CompileStatic
void processParsedSsdpEvent(LinkedHashMap event) {
  String ipAddress = convertHexToIP(event?.networkAddress as String)
  String ipPort = convertHexToInt(event?.deviceAddress as String).toString()
  logTrace("Considering SSDP discovery candidate at ${ipAddress}:${ipPort}")

  // Devices send multiple SSDP responses; reserve each IP before issuing async probes.
  if(isIpAlreadyDiscovered(ipAddress)) {
    logTrace("Skipping SSDP candidate ${ipAddress}; device is already in discovered results")
    return
  }
  if(!reserveDiscoveryProbe(ipAddress)) {
    return
  }

  requestDiscoveryPlayerInfo(ipAddress)
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

/** Resolve and validate a Line-In source in the controller, where sibling lookup is serialized. */
String resolveLineInSourceRincon(ChildDeviceWrapper coordinator, String sourceIdentifier) {
  String identifier = sourceIdentifier?.trim()
  if(!identifier) {
    String capabilities = coordinator?.getDataValue('capabilities') as String
    if(!capabilities?.contains('LINE_IN')) {
      logWarn('Line-In is not supported on the selected player')
      return null
    }
    return coordinator.getDataValue('id') as String
  }

  List<ChildDeviceWrapper> children = app.getChildDevices()
  ChildDeviceWrapper source
  if(identifier.startsWith('RINCON_')) {
    source = children.find { ChildDeviceWrapper child -> child.getDataValue('id') == identifier }
  } else if(identifier.matches('^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$')) {
    source = children.find { ChildDeviceWrapper child -> child.getDataValue('deviceIp') == identifier }
  } else {
    String lowerIdentifier = identifier.toLowerCase()
    source = children.find { ChildDeviceWrapper child ->
      child.getDataValue('name')?.toString()?.toLowerCase()?.contains(lowerIdentifier)
    }
    if(source == null) {
      source = children.find { ChildDeviceWrapper child -> child.label?.toLowerCase()?.contains(lowerIdentifier) }
    }
  }

  if(source == null) {
    logWarn("Cannot resolve Line-In source '${identifier}'")
    return null
  }
  String capabilities = source.getDataValue('capabilities') as String
  if(!capabilities?.contains('LINE_IN')) {
    logWarn("Source device '${source.getDataValue('name')}' does not have Line-In capability")
    return null
  }
  String rincon = source.getDataValue('id') as String
  if(!rincon?.startsWith('RINCON_')) {
    logWarn("Source device '${source.getDataValue('name')}' has no valid RINCON ID")
    return null
  }
  return rincon
}

/** Resolve the public add-player argument against managed primary-player devices. */
ChildDeviceWrapper resolvePlayerIdentifier(String identifier) {
  String value = identifier?.trim()
  if(!value) {
    logWarn('Cannot resolve an empty Sonos player identifier')
    return null
  }

  List<ChildDeviceWrapper> players = getCurrentPlayerDevices()
  String upperValue = value.toUpperCase()
  List<ChildDeviceWrapper> matches = players.findAll { ChildDeviceWrapper player ->
    player.getDataValue('id')?.toString()?.toUpperCase() == upperValue
  }
  if(!matches) {
    String normalizedMac = normalizePlayerMac(value)
    if(normalizedMac) {
      matches = players.findAll { ChildDeviceWrapper player ->
        normalizePlayerMac(player.getDeviceNetworkId()?.toString()) == normalizedMac
      }
    }
  }
  if(!matches) {
    String normalizedIp = normalizePlayerIp(value)
    if(normalizedIp) {
      matches = players.findAll { ChildDeviceWrapper player ->
        normalizePlayerIp(player.getDataValue('deviceIp')?.toString()) == normalizedIp ||
            normalizePlayerIp(player.getDataValue('localUpnpHost')?.toString()) == normalizedIp
      }
    }
  }
  if(!matches) {
    matches = players.findAll { ChildDeviceWrapper player ->
      player.getDataValue('name')?.toString()?.equalsIgnoreCase(value)
    }
  }

  if(matches.size() > 1) {
    logWarn("Player identifier '${value}' is ambiguous; no group membership was changed")
    return null
  }
  if(matches.isEmpty()) {
    logWarn("Could not resolve Sonos player identifier '${value}'")
    return null
  }
  return matches[0]
}

String normalizePlayerMac(String value) {
  if(!value) { return null }
  String normalized = value.replaceAll(/[^A-Fa-f0-9]/, '').toUpperCase()
  return normalized.size() == 12 ? normalized : null
}

String normalizePlayerIp(String value) {
  if(!value) { return null }
  String normalized = value.trim().replaceFirst(/^https?:\/\//, '')
  normalized = normalized.replaceFirst(/:\d+$/, '')
  return normalized
}

/** Add one player to the live group identified by its coordinator. */
void addPlayerToCoordinatorGroup(ChildDeviceWrapper coordinator, String playerIdentifier) {
  if(coordinator == null) {
    logWarn('Cannot add player because the Sonos group coordinator could not be resolved')
    return
  }
  ChildDeviceWrapper target = resolvePlayerIdentifier(playerIdentifier)
  if(target == null) { return }

  String coordinatorId = coordinator.getDataValue('id')?.toString()
  String targetId = target.getDataValue('id')?.toString()
  if(!coordinatorId || !targetId) {
    logWarn("Cannot add '${playerIdentifier}': coordinator or target has no RINCON ID")
    return
  }
  if(coordinatorId == targetId) {
    logDebug("${targetId} is already the requested coordinator; no group change needed")
    return
  }

  String coordinatorHousehold = coordinator.getDataValue('householdId')?.toString()
  String targetHousehold = target.getDataValue('householdId')?.toString()
  if(coordinatorHousehold && targetHousehold && coordinatorHousehold != targetHousehold) {
    logWarn("Cannot add '${playerIdentifier}': players belong to different Sonos households")
    return
  }

  String groupId = coordinator.getDataValue('groupId')?.toString()
  if(!groupId) {
    // Sonos represents a standalone player as a one-member group. If local
    // membership confirms that singleton, create the two-player group with
    // the current coordinator first. Never use this fallback for a coordinator
    // that may already lead a larger group.
    List<String> coordinatorMembers = coordinator.getDataValue('groupPlayerIds')?.tokenize(',') ?: []
    if(coordinatorMembers.size() == 1 && coordinatorMembers[0] == coordinatorId &&
        coordinator.currentValue('isGrouped', true) == 'off') {
      logInfo("Creating a group with ${coordinatorId} as coordinator and adding ${targetId}")
      prepareExpectedTopologyForCreateGroup(coordinator, [coordinatorId, targetId])
      coordinator.playerCreateGroup([coordinatorId, targetId])
      return
    }
    coordinator.playerGetGroupsFull()
    logWarn("Cannot add '${playerIdentifier}' yet: requested a fresh group topology for ${coordinatorId}; retry after it updates")
    return
  }

  String targetGroupId = target.getDataValue('groupId')?.toString()
  List<String> coordinatorMembers = coordinator.getDataValue('groupPlayerIds')?.tokenize(',') ?: []
  if(targetGroupId == groupId || coordinatorMembers.contains(targetId)) {
    logDebug("${targetId} is already in group ${groupId}; no group change needed")
    return
  }

  logInfo("Adding ${targetId} to group ${groupId} through coordinator ${coordinatorId}")
  prepareExpectedTopologyForMutation(coordinator, [targetId], [])
  coordinator.playerModifyGroupMembers([targetId], [])
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

/** Receive a player state batch without holding the player callback open. */
void playerStateUpdateHandler(Event event) {
  Map payload
  try {
    payload = (Map)parseJson(event?.data?.toString())
  } catch(Exception e) {
    logWarn("Ignoring malformed player state update: ${e.message}")
    return
  }
  if(!(payload instanceof Map)) { return }
  String playerDni = payload.playerDni as String
  ChildDeviceWrapper player = playerDni ? getChildDevice(playerDni) : null
  if(player == null || !player.getDataValue('id')) { return }
  if(payload.kind == 'wsRetry') {
    Map<String, Map> offlinePlayers = state[OFFLINE_SONOS_PLAYERS_STATE_KEY] instanceof Map
        ? (Map<String, Map>)state[OFFLINE_SONOS_PLAYERS_STATE_KEY] : [:]
    offlinePlayers[playerDni] = [id: player.getDataValue('id'), name: player.displayName,
      retryDelay: (payload.retryDelay ?: 30) as Integer]
    state[OFFLINE_SONOS_PLAYERS_STATE_KEY] = offlinePlayers
    return
  }
  if(payload.kind == 'wsConnected') {
    Map<String, Map> offlinePlayers = state[OFFLINE_SONOS_PLAYERS_STATE_KEY] instanceof Map
        ? (Map<String, Map>)state[OFFLINE_SONOS_PLAYERS_STATE_KEY] : [:]
    offlinePlayers.remove(playerDni)
    state[OFFLINE_SONOS_PLAYERS_STATE_KEY] = offlinePlayers
    return
  }
  if(payload.kind == 'householdPresence') {
    resetLongBackoffPlayersSeenInHousehold(payload.players)
    return
  }
  if(payload.kind == 'groupAnnouncementProgress') {
    handleGroupAnnouncementProgress(player, payload)
    return
  }
  String coordinatorId = payload.coordinatorId as String
  String kind = payload.kind as String
  if(kind == 'playbackObservation') {
    String playerId = player.getDataValue('id')?.toString()
    if(playerId) {
      Map<String, Map> observations = state[PLAYER_PLAYBACK_OBSERVATIONS_STATE_KEY] instanceof Map
          ? (Map<String, Map>)state[PLAYER_PLAYBACK_OBSERVATIONS_STATE_KEY] : [:]
      Map observation = observations[playerId] instanceof Map ? new LinkedHashMap(observations[playerId]) : [:]
      if(payload.playbackState != null) { observation.playbackState = payload.playbackState.toString() }
      if(payload.playbackObservedAt != null) { observation.playbackObservedAt = payload.playbackObservedAt }
      if(payload.audioSource != null) { observation.audioSource = payload.audioSource.toString() }
      if(payload.signalStatus != null) { observation.signalStatus = payload.signalStatus.toString() }
      if(payload.signalObservedAt != null) { observation.signalObservedAt = payload.signalObservedAt }
      observations[playerId] = observation
      state[PLAYER_PLAYBACK_OBSERVATIONS_STATE_KEY] = observations
      logTrace("Cached playback observation for ${playerId}: state=${payload.playbackState}, signal=${payload.signalStatus}")
    }
    return
  }
  if(kind == 'membership') {
    if(coordinatorId && payload.playersInGroup instanceof List) {
      updateGroupDevices(coordinatorId, (List<String>)payload.playersInGroup)
    }
    return
  }
  if(kind == 'topology') {
    if(handleGroupAnnouncementTopologyEvent(player, payload)) { return }
    Map operation = getActiveGroupOperation()
    String playerId = player.getDataValue('id')?.toString()
    if(operation != null && playerId && normalizeGroupPlayerIds(operation.requiredPlayerIds).contains(playerId)) {
      Long observedAt = (payload.observedAt ?: now()) as Long
      Map<String, Map> observations = operation.topologyObservations instanceof Map
          ? (Map<String, Map>)operation.topologyObservations
          : [:]
      observations[playerId] = [
        groupId: payload.groupId?.toString(),
        coordinatorId: payload.coordinatorId?.toString(),
        playerIds: normalizeGroupPlayerIds(payload.playerIds),
        observedAt: observedAt
      ]
      operation.topologyObservations = observations

      Long favoriteRefreshAt = (operation.favoriteTopologyRefreshAt ?: 0L) as Long
      if(favoriteRefreshAt > 0L && observedAt >= favoriteRefreshAt) {
        Map<String, Map> favoriteObservations = operation.favoriteTopologyObservations instanceof Map
            ? (Map<String, Map>)operation.favoriteTopologyObservations
            : [:]
        favoriteObservations[playerId] = observations[playerId]
        operation.favoriteTopologyObservations = favoriteObservations
      }
      logTrace("Received topology report from ${playerId} for operation ${operation.operationId} at ${observedAt}")
      saveActiveGroupOperation(operation)
      runIn(1, 'advanceGroupFavoriteOperation', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
    }
    return
  }
  if(!(kind in ['state', 'followers'])) { return }
  if(payload.followerDNIs instanceof List && payload.followerAttrs instanceof Map) {
    ((List)payload.followerDNIs).each { Object dni ->
      if(dni != null && getChildDevice(dni.toString()) != null) {
        ((Map)payload.followerAttrs).each { Object name, Object value ->
          if(name != null && value != null) { sendChildEvent(dni.toString(), [name: name.toString(), value: value]) }
        }
      }
    }
  }
  if(kind == 'state' && coordinatorId == player.getDataValue('id')) {
    flushGroupDeviceState(coordinatorId, payload.volumeAttrs instanceof Map ? (Map)payload.volumeAttrs : null,
      payload.playbackAttrs instanceof Map ? (Map)payload.playbackAttrs : null)
  }
}

void resetLongBackoffPlayersSeenInHousehold(Object observedPlayers) {
  if(!(observedPlayers instanceof Collection)) { return }
  Map<String, Map> offlinePlayers = state[OFFLINE_SONOS_PLAYERS_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[OFFLINE_SONOS_PLAYERS_STATE_KEY] : [:]
  if(offlinePlayers.isEmpty()) { return }

  Set<String> observedIds = ((Collection)observedPlayers).findAll { Object item -> item instanceof Map }
      .collect { Object item -> ((Map)item).id?.toString() }.findAll { String id -> id } as Set<String>
  offlinePlayers.each { String dni, Map offlineInfo ->
    Integer retryDelay = (offlineInfo.retryDelay ?: 30) as Integer
    String rinconId = offlineInfo.id?.toString()
    if(retryDelay > 30 && rinconId && observedIds.contains(rinconId)) {
      ChildDeviceWrapper offlinePlayer = getChildDevice(dni)
      if(offlinePlayer != null) {
        if(offlinePlayer.restartWebSocketBackoff() == true) {
          logInfo("Household update shows offline player ${offlineInfo.name} (${rinconId}); restarting its WebSocket retry interval at 30 seconds")
          offlineInfo.retryDelay = 30
        }
      }
    }
  }
  state[OFFLINE_SONOS_PLAYERS_STATE_KEY] = offlinePlayers
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

  enqueueGroupCommandRequest(request)
}

/** Queue group announcements so a coordinator stream and subsequent messages cannot race. */
void enqueueGroupAnnouncement(ChildDeviceWrapper groupDevice, ChildDeviceWrapper coordinator,
    List<ChildDeviceWrapper> players, String command, Map args, String requestId) {
  if(coordinator == null || !players) {
    logWarn("Cannot queue group announcement for ${groupDevice?.deviceNetworkId}: no coordinator or players")
    return
  }
  String groupDni = groupDevice.deviceNetworkId
  String correlationId = args.correlationId?.toString() ?: requestId ?: UUID.randomUUID().toString()
  Map item = [correlationId: correlationId, requestId: requestId, command: command,
    uri: args.uri?.toString(), volume: args.volume?.toString(),
    duration: (toBigDecimal(args.duration)?.intValue() ?: 0),
    forceAudioClip: args.forceAudioClip == true,
    coordinatorId: coordinator.getDataValue('id')?.toString(),
    playerIds: players.collect { ChildDeviceWrapper p -> p.getDataValue('id')?.toString() }.findAll { it }]
  if(!item.uri) {
    logWarn("Ignoring group announcement ${correlationId}: missing stream URI")
    return
  }

  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : [pending: []]
  if(command == 'playHighPriorityTrack' && queue.active instanceof Map) {
    String highPriorityMode = getAnnouncementMode(true)
    if(highPriorityMode != 'alwaysCoordinatorStream') {
      // Native audioClip HIGH priority can overlay an in-flight announcement or
      // an external source without replacing the coordinator's stream.
      dispatchGroupAnnouncementClip(item, players, true)
      return
    }
    // An explicitly selected standard stream takes over the current queue
    // item. Stale callbacks from that item are ignored by the correlation ID.
    List<Map> pendingHighPriority = queue.pending instanceof List ? (List<Map>)queue.pending : []
    pendingHighPriority.add(0, item)
    queue.pending = pendingHighPriority
    String activeCorrelationId = queue.active.correlationId?.toString()
    Map<String, Map> topologyTransactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
        ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
    Map activeTopologyTransaction = topologyTransactions[activeCorrelationId]
    if(activeTopologyTransaction?.phase == 'STREAMING') {
      // Keep the captured pre-TTS topology and replace the active standard
      // stream in place. Restoring first would delay high priority and could
      // interrupt playback twice.
      pendingHighPriority.remove(item)
      queue.pending = pendingHighPriority
      queue.active = item
      item.mode = 'COORDINATOR_STREAM'
      activeTopologyTransaction.correlationId = correlationId
      activeTopologyTransaction.item = item
      topologyTransactions.remove(activeCorrelationId)
      topologyTransactions[correlationId] = activeTopologyTransaction
      state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = topologyTransactions
      queue.ownStreamUri = item.uri
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
      ChildDeviceWrapper activeCoordinator = buildRinconMap()[item.coordinatorId?.toString()]
      logInfo("High priority standard stream ${correlationId} is replacing the active temporary-group TTS stream")
      activeCoordinator?.playerPlayGroupAnnouncementStream(item.uri as String,
          toBigDecimal(item.volume), correlationId)
      scheduleGroupAnnouncementWatchdog(groupDni, item)
      return
    }
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    logInfo("High priority announcement ${correlationId} is taking over the active group stream using the configured standard-stream method")
    if(activeCorrelationId && topologyTransactions[activeCorrelationId] instanceof Map) {
      finishGroupAnnouncement(groupDni, activeCorrelationId, 'SUPERSEDED_BY_HIGH_PRIORITY_ANNOUNCEMENT')
    } else {
      queue.active = null
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
      startNextGroupAnnouncement(groupDni)
    }
    return
  }
  List<Map> pending = queue.pending instanceof List ? (List<Map>)queue.pending : []
  if(command == 'playHighPriorityTrack') { pending.add(0, item) }
  else { pending.add(item) }
  queue.pending = pending
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
  logDebug("Queued group announcement ${correlationId} (${command}) for ${groupDni}; pending=${pending.size()}")
  startNextGroupAnnouncement(groupDni)
}

void startNextGroupAnnouncement(String groupDni) {
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  if(queue == null || queue.active instanceof Map) { return }
  Boolean takeoverCanContinue = queue.externalTakeover == true && canContinueAnnouncementQueueDuringTakeover(groupDni)
  if(getActiveGroupOperation() != null || (hasActiveAnnouncementTopologyTransaction() && !takeoverCanContinue)) {
    logTrace("Deferring queued announcement for ${groupDni} while a Sonos topology transaction is active")
    runIn(2, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: true])
    return
  }
  if(takeoverCanContinue) {
    logTrace("Continuing queued announcements for ${groupDni} in the temporary topology while external playback remains active")
  }
  List<Map> pending = queue.pending instanceof List ? (List<Map>)queue.pending : []
  if(pending.isEmpty()) { queues.remove(groupDni); state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues; return }
  Map item = pending.remove(0)
  queue.active = item
  queue.pending = pending
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues

  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  ChildDeviceWrapper coordinator = rinconMap[item.coordinatorId?.toString()]
  List<ChildDeviceWrapper> players = normalizeGroupPlayerIds(item.playerIds).collect { String id -> rinconMap[id] }.findAll { it != null }
  if(coordinator == null || players.isEmpty()) {
    logWarn("Cannot start group announcement ${item.correlationId}: target devices are unavailable")
    finishGroupAnnouncement(groupDni, item.correlationId?.toString(), 'FAILED')
    return
  }
  if(item.forceAudioClip == true) {
    Boolean allPlayersSupportAudioClip = players.every { ChildDeviceWrapper player ->
      player.hasAudioClipCapability() == true
    }
    if(allPlayersSupportAudioClip) {
      logInfo("Play Text command ${item.correlationId} is forcing synchronized audioClip for all group members")
      item.mode = 'AUDIO_CLIP'
      dispatchGroupAnnouncementClip(item, players, false)
      scheduleGroupAnnouncementWatchdog(groupDni, item)
      return
    }
    logWarn("Play Text command ${item.correlationId} includes a player without audioClip support; falling back to a coordinator stream")
    item.forceCoordinatorStream = true
  }
  Boolean highPriority = item.command == 'playHighPriorityTrack'
  String announcementMode = getAnnouncementMode(highPriority)
  Boolean topologyEnabled = isAutomaticTtsTopologyChangesEnabled()
  Boolean exactCachedTopology = isTargetRosterCurrentlyGrouped(players, item.coordinatorId?.toString())
  if(players.size() > 1 && announcementMode != 'alwaysNonInterrupting' &&
      (announcementMode == 'automatic' || (announcementMode == 'alwaysCoordinatorStream' && topologyEnabled)) &&
      !exactCachedTopology) {
    beginGroupAnnouncementTopologySnapshot(groupDni, queue, item, players)
    return
  }
  dispatchGroupAnnouncementByPolicy(groupDni, queue, item, players, coordinator, null)
}

Boolean canContinueAnnouncementQueueDuringTakeover(String groupDni) {
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  return transactions.values().any { Map transaction ->
    transaction.groupDni?.toString() == groupDni && transaction.phase?.toString() == 'RESTORING' &&
        transaction.restorationReason?.toString() == 'INTERRUPTED_BY_EXTERNAL_PLAYBACK'
  }
}

void dispatchGroupAnnouncementByPolicy(String groupDni, Map queue, Map item,
    List<ChildDeviceWrapper> players, ChildDeviceWrapper coordinator, Map snapshot) {
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Boolean highPriority = item.command == 'playHighPriorityTrack'
  String announcementMode = getAnnouncementMode(highPriority)
  Boolean exactTopology = snapshot instanceof Map
      ? isTargetRosterGroupedInSnapshot(players, item.coordinatorId?.toString(), snapshot)
      : isTargetRosterCurrentlyGrouped(players, item.coordinatorId?.toString())
  if(exactTopology) {
    String liveCoordinatorId
    if(snapshot?.observations instanceof Map) {
      Map firstObservation = ((Map)snapshot.observations)[players[0].getDataValue('id')?.toString()] as Map
      liveCoordinatorId = firstObservation?.coordinatorId?.toString()
    } else {
      liveCoordinatorId = players[0].getDataValue('groupCoordinatorId')?.toString()
    }
    ChildDeviceWrapper liveCoordinator = liveCoordinatorId ? buildRinconMap()[liveCoordinatorId] : null
    if(liveCoordinator != null) {
      coordinator = liveCoordinator
      item.coordinatorId = liveCoordinatorId
    }
  }
  String transportStatus = coordinator.currentValue('transportStatus', true)?.toString()?.toLowerCase()
  String currentAudioSource = coordinator.currentValue('audioSource', true)?.toString()
  Map playbackActivity = getPlayerPlaybackActivity(coordinator, currentAudioSource)
  String playbackState = playbackActivity.playbackState?.toString()
  Boolean playing = playbackActivity.active == true
  Boolean externalTakeover = queue.externalTakeover == true
  logTrace("Announcement route check group=${groupDni}, correlation=${item.correlationId}, priority=${highPriority ? 'high' : 'normal'}, mode=${announcementMode}, playbackState=${playbackState}, playbackObservedAt=${playbackActivity.observedAt}, playbackSignal=${playbackActivity.signal}, transportStatus=${transportStatus}, audioSource=${currentAudioSource}, priorTtsUri=${queue.ownStreamUri}, externalTakeover=${externalTakeover}")
  if(externalTakeover && !playing) {
    queue.remove('externalTakeover')
    externalTakeover = false
    logDebug("External playback ended before the next queued announcement for ${groupDni}; returning to coordinator streams")
  }
  Boolean playingPreviousAnnouncement = playing && queue.ownStreamUri?.toString() &&
      currentAudioSource == queue.ownStreamUri.toString()
  if(playingPreviousAnnouncement && !externalTakeover) {
    logTrace("Coordinator is still playing the previous queued announcement stream ${queue.ownStreamUri}; treating the group as otherwise idle")
    playing = false
  }
  Boolean useAudioClip = announcementMode == 'alwaysNonInterrupting' ||
      (announcementMode == 'automatic' && playing && (!playingPreviousAnnouncement || externalTakeover))
  if(announcementMode == 'automatic' && !exactTopology && !playing) {
    if(!isAutomaticTtsTopologyChangesEnabled()) {
      useAudioClip = true
      logInfo("Automatic topology changes are disabled; routing announcement ${item.correlationId} to every player with audioClip")
    } else if(!canSafelyRegroupForAutomaticAnnouncement(players, item.coordinatorId?.toString(), snapshot)) {
      useAudioClip = true
      logInfo("Active coordinator playback would be disturbed by topology changes; routing announcement ${item.correlationId} to every player with audioClip")
    } else {
      beginGroupAnnouncementTopologyTransaction(groupDni, queue, item, players, snapshot, false)
      return
    }
  }
  if(announcementMode == 'alwaysCoordinatorStream' && !exactTopology && isAutomaticTtsTopologyChangesEnabled()) {
    beginGroupAnnouncementTopologyTransaction(groupDni, queue, item, players, snapshot, true)
    return
  }
  if(item.forceCoordinatorStream == true) { useAudioClip = false }
  if(useAudioClip) {
    item.mode = 'AUDIO_CLIP'
    String routeReason = announcementMode == 'alwaysNonInterrupting' ? 'app policy alwaysNonInterrupting' : "active source state=${playbackState ?: transportStatus}"
    logInfo("Starting group announcement ${item.correlationId} as synchronized per-player audioClip; ${routeReason}")
    dispatchGroupAnnouncementClip(item, players, item.command == 'playHighPriorityTrack')
  } else {
    item.mode = 'COORDINATOR_STREAM'
    queue.ownStreamUri = item.uri
    String routeReason = announcementMode == 'alwaysCoordinatorStream' ? 'app policy alwaysCoordinatorStream' : 'playback is inactive'
    logInfo("Starting group announcement ${item.correlationId} as coordinator stream; ${routeReason}")
    coordinator.playerPlayGroupAnnouncementStream(item.uri as String,
        toBigDecimal(item.volume), item.correlationId as String)
  }
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
  Integer duration = (item.duration ?: 0) as Integer
  Integer timeoutSeconds = duration > 0 ? Math.max(duration + 20, 30) : 180
  runIn(timeoutSeconds, 'groupAnnouncementWatchdog', [data: [groupDni: groupDni,
    correlationId: item.correlationId], overwrite: false])
}

String getAnnouncementMode(Boolean highPriority = false) {
  String configured = highPriority
      ? settings.highPriorityAnnouncementMode?.toString()?.trim()
      : settings.groupAnnouncementMode?.toString()?.trim()
  return configured in ['automatic', 'alwaysNonInterrupting', 'alwaysCoordinatorStream']
      ? configured
      : 'automatic'
}

Boolean isAutomaticTtsTopologyChangesEnabled() {
  return settings.automaticTtsTopologyChanges == null ||
      !(settings.automaticTtsTopologyChanges == false || settings.automaticTtsTopologyChanges.toString() == 'false')
}

Boolean hasActiveAnnouncementTopologyTransaction() {
  Map transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  return !transactions.isEmpty()
}

Boolean isTargetRosterCurrentlyGrouped(List<ChildDeviceWrapper> players, String coordinatorId) {
  List<String> targetIds = players.collect { ChildDeviceWrapper player ->
    player.getDataValue('id')?.toString()
  }.findAll { String id -> id }.unique().sort()
  if(targetIds.size() < 2) { return targetIds.size() <= 1 }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  String commonGroupId
  String commonCoordinatorId
  return targetIds.every { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    List<String> members = normalizeGroupPlayerIds(player?.getDataValue('groupPlayerIds')).sort()
    String groupId = player?.getDataValue('groupId')?.toString()
    String liveCoordinatorId = player?.getDataValue('groupCoordinatorId')?.toString()
    if(!commonGroupId) { commonGroupId = groupId; commonCoordinatorId = liveCoordinatorId }
    groupId && groupId == commonGroupId && liveCoordinatorId == commonCoordinatorId && members == targetIds
  }
}

Boolean isTargetRosterGroupedInSnapshot(List<ChildDeviceWrapper> players, String coordinatorId, Map snapshot) {
  Map<String, Map> observations = snapshot?.observations instanceof Map
      ? (Map<String, Map>)snapshot.observations : [:]
  List<String> targetIds = players.collect { ChildDeviceWrapper player ->
    player.getDataValue('id')?.toString()
  }.findAll { String id -> id }.unique().sort()
  if(targetIds.size() < 2) { return targetIds.size() <= 1 }
  String commonGroupId
  String commonCoordinatorId
  return targetIds.every { String playerId ->
    Map observed = observations[playerId]
    String groupId = observed?.groupId?.toString()
    String observedCoordinatorId = observed?.coordinatorId?.toString()
    if(!commonGroupId) { commonGroupId = groupId; commonCoordinatorId = observedCoordinatorId }
    normalizeGroupPlayerIds(observed?.playerIds).sort() == targetIds &&
        observedCoordinatorId == commonCoordinatorId && groupId && groupId == commonGroupId
  }
}

Boolean canSafelyRegroupForAutomaticAnnouncement(List<ChildDeviceWrapper> players,
    String targetCoordinatorId, Map snapshot) {
  if(!(snapshot instanceof Map) || !(snapshot.observations instanceof Map)) { return false }
  Set<String> targetIds = players.collect { ChildDeviceWrapper player ->
    player.getDataValue('id')?.toString()
  }.findAll { String id -> id } as Set<String>
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  Map<String, Map> observations = (Map<String, Map>)snapshot.observations
  for(String playerId : targetIds) {
    Map observed = observations[playerId]
    if(observed == null || !observed.groupId || !observed.coordinatorId ||
        !(observed.playerIds instanceof Collection) || ((Collection)observed.playerIds).isEmpty()) {
      return false
    }
    String currentCoordinatorId = observed.coordinatorId.toString()
    ChildDeviceWrapper player = rinconMap[playerId]
    ChildDeviceWrapper sourceCoordinator = rinconMap[currentCoordinatorId]
    Boolean playerActive = isPlayerActivelyPlaying(player)
    Boolean coordinatorActive = isPlayerActivelyPlaying(sourceCoordinator)
    Boolean activeStream = playerActive || coordinatorActive
    if(activeStream && targetIds.contains(currentCoordinatorId)) {
      logTrace("Automatic topology preflight blocked: target ${playerId} is attached to active coordinator ${currentCoordinatorId}")
      return false
    }
  }
  return true
}

Boolean isPlayerActivelyPlaying(ChildDeviceWrapper player) {
  if(player == null) { return false }
  String audioSource = player.currentValue('audioSource', true)?.toString()
  return getPlayerPlaybackActivity(player, audioSource).active == true
}

Map getPlayerPlaybackActivity(ChildDeviceWrapper player, String audioSource = null) {
  if(player == null) { return [active: false, signal: 'no-player'] }
  String playerId = player.getDataValue('id')?.toString()
  Map<String, Map> observations = state[PLAYER_PLAYBACK_OBSERVATIONS_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[PLAYER_PLAYBACK_OBSERVATIONS_STATE_KEY] : [:]
  Map observation = playerId ? observations[playerId] : null
  String playbackState = observation?.playbackState?.toString()
  String transportStatus = player.currentValue('transportStatus', true)?.toString()?.toLowerCase()
  String resolvedAudioSource = audioSource ?: observation?.audioSource?.toString()
      ?: player.currentValue('audioSource', true)?.toString()
  Object observedValue = observation?.playbackObservedAt
  Object signalObservedValue = observation?.signalObservedAt
  String signalStatus = observation?.signalStatus?.toString()
  Long observedAt = null
  Long signalObservedAt = null
  try {
    if(observedValue != null) { observedAt = observedValue.toString().toLong() }
  } catch(Exception ignored) { observedAt = null }
  try {
    if(signalObservedValue != null) { signalObservedAt = signalObservedValue.toString().toLong() }
  } catch(Exception ignored) { signalObservedAt = null }
  Long ageMs = observedAt == null ? null : now() - observedAt
  Long signalAgeMs = signalObservedAt == null ? null : now() - signalObservedAt
  Boolean freshPlaybackState = ageMs != null && ageMs >= 0L && ageMs <= 15000L
  Boolean hdmiSource = resolvedAudioSource?.startsWith('x-sonos-htastream:') == true
  Boolean freshSignalStatus = signalAgeMs != null && signalAgeMs >= 0L && signalAgeMs <= 15000L
  if(hdmiSource && freshSignalStatus && signalStatus in ['no-signal', 'signal']) {
    Boolean signalActive = signalStatus == 'signal'
    return [active: signalActive, signal: signalActive ? 'fresh-hdmi-signal' : 'fresh-hdmi-no-signal',
      observedAt: signalObservedAt, ageMs: signalAgeMs, playbackState: playbackState]
  }
  if(hdmiSource && freshPlaybackState && playbackState in [
      'PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING', 'PLAYBACK_STATE_IDLE',
      'PLAYBACK_STATE_PAUSED', 'PLAYBACK_STATE_STOPPED']) {
    Boolean apiActive = playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']
    return [active: apiActive, signal: 'fresh-playback-api-hdmi', observedAt: observedAt, ageMs: ageMs,
      signalStatus: signalStatus]
  }
  Boolean active = playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING'] || transportStatus == 'playing'
  return [active: active, signal: active ? 'playback-or-transport-fallback' : 'inactive-fallback',
    observedAt: observedAt, ageMs: ageMs]
}

void beginGroupAnnouncementTopologySnapshot(String groupDni, Map queue, Map item,
    List<ChildDeviceWrapper> players) {
  if(getActiveGroupOperation() != null || hasActiveAnnouncementTopologyTransaction()) {
    logTrace("Deferring topology snapshot for announcement ${item.correlationId} until the active group operation completes")
    runIn(2, 'retryGroupAnnouncementTopologySnapshot', [overwrite: true,
      data: [groupDni: groupDni, correlationId: item.correlationId]])
    return
  }
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  Map existing = transactions[item.correlationId?.toString()]
  if(existing?.phase == 'SNAPSHOTTING') { return }
  Long refreshAt = now()
  Map transaction = [correlationId: item.correlationId?.toString(), groupDni: groupDni,
    phase: 'SNAPSHOTTING', refreshAt: refreshAt, observations: [:], requestedPlayerIds: [],
    targetPlayerIds: normalizeGroupPlayerIds(item.playerIds), item: item]
  transactions[item.correlationId?.toString()] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  item.mode = 'WAITING_FOR_TOPOLOGY_SNAPSHOT'
  queue.active = item
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  Set<String> requiredIds = new LinkedHashSet<String>(normalizeGroupPlayerIds(item.playerIds))
  requiredIds.toList().each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    normalizeGroupPlayerIds(player?.getDataValue('groupPlayerIds')).each { String memberId -> requiredIds.add(memberId) }
  }
  requestAnnouncementTopologyObservations(transaction, requiredIds)
  Integer timeoutSeconds = 12
  runIn(timeoutSeconds, 'groupAnnouncementTopologySnapshotTimeout', [overwrite: true,
    data: [correlationId: item.correlationId]])
  logTrace("Collecting fresh topology for group announcement ${item.correlationId}; players=${requiredIds.sort()}")
}

void retryGroupAnnouncementTopologySnapshot(Map data = null) {
  String groupDni = data?.groupDni?.toString()
  String correlationId = data?.correlationId?.toString()
  if(!groupDni || !correlationId || getActiveGroupOperation() != null) { return }
  Map queue = ((Map)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY])?.get(groupDni) as Map
  Map item = queue?.active instanceof Map ? (Map)queue.active : null
  if(item?.correlationId?.toString() != correlationId) { return }
  List<ChildDeviceWrapper> players = normalizeGroupPlayerIds(item.playerIds).collect { String id -> buildRinconMap()[id] }
      .findAll { ChildDeviceWrapper player -> player != null }
  beginGroupAnnouncementTopologySnapshot(groupDni, queue, item, players)
}

void requestAnnouncementTopologyObservations(Map transaction, Collection<String> requestedIds) {
  if(transaction == null) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> requested = normalizeGroupPlayerIds(transaction.requestedPlayerIds)
  requestedIds?.each { String playerId ->
    if(!playerId || requested.contains(playerId)) { return }
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player == null) { return }
    requested.add(playerId)
    try {
      player.getPlaybackStatus()
      player.playerGetGroupsFull()
    } catch(Exception e) {
      logDebug("Could not request topology for announcement participant ${playerId}: ${e.message}")
    }
  }
  transaction.requestedPlayerIds = requested.unique().sort()
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[transaction.correlationId.toString()] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
}

Boolean handleGroupAnnouncementTopologyEvent(ChildDeviceWrapper player, Map payload) {
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  String playerId = player?.getDataValue('id')?.toString()
  if(!playerId || transactions.isEmpty()) { return false }
  Long observedAt = (payload?.observedAt ?: 0L) as Long
  Map transaction = transactions.values().find { Map candidate ->
    candidate?.phase == 'SNAPSHOTTING' && normalizeGroupPlayerIds(candidate.requestedPlayerIds).contains(playerId) &&
        observedAt >= ((candidate.refreshAt ?: Long.MAX_VALUE) as Long)
  } as Map
  if(transaction == null) {
    // While a temporary grouping is active, unexpected topology reports signal
    // a user or external-controller change. Never restore over that topology.
    Map activeTransaction = transactions.values().find { Map candidate ->
      candidate?.phase == 'STREAMING' && candidate?.expectedTopology instanceof Map &&
          ((Map)candidate.expectedTopology).containsKey(playerId)
    } as Map
    Map expected = activeTransaction?.expectedTopology instanceof Map
        ? (Map)((Map)activeTransaction.expectedTopology)[playerId] : null
    if(expected != null && !announcementTopologyObservationMatches(expected, payload)) {
      activeTransaction.userTopologyChanged = true
      logWarn("Topology for ${playerId} changed unexpectedly during temporary TTS grouping ${activeTransaction.correlationId}; preserving the observed topology")
      transactions[activeTransaction.correlationId.toString()] = activeTransaction
      state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
    }
    return false
  }
  Map<String, Map> observations = transaction.observations instanceof Map
      ? (Map<String, Map>)transaction.observations : [:]
  List<String> playerIds = normalizeGroupPlayerIds(payload.playerIds)
  observations[playerId] = [groupId: payload.groupId?.toString(),
    coordinatorId: payload.coordinatorId?.toString(), playerIds: playerIds, observedAt: observedAt]
  transaction.observations = observations
  Set<String> additionalIds = new LinkedHashSet<String>()
  normalizeGroupPlayerIds(transaction.targetPlayerIds).each { String targetId ->
    Map targetObservation = observations[targetId]
    if(targetObservation != null) {
      normalizeGroupPlayerIds(targetObservation.playerIds).each { String memberId -> additionalIds.add(memberId) }
    }
  }
  List<String> missingIds = additionalIds.findAll { String id -> !observations.containsKey(id) }.toList()
  if(!missingIds.isEmpty()) { requestAnnouncementTopologyObservations(transaction, missingIds) }
  transactions[transaction.correlationId.toString()] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  List<String> requested = normalizeGroupPlayerIds(transaction.requestedPlayerIds)
  if(requested && requested.every { String id -> observations[id] != null }) {
    completeGroupAnnouncementTopologySnapshot(transaction, false)
  }
  return transaction.phase == 'SNAPSHOTTING'
}

Boolean announcementTopologyObservationMatches(Map expected, Map observed) {
  // Sonos may issue a new groupId while the same coordinator and member roster
  // remain intact. Treat only coordinator or membership changes as a topology edit.
  return expected?.coordinatorId?.toString() == observed?.coordinatorId?.toString() &&
      normalizeGroupPlayerIds(expected?.playerIds).sort() == normalizeGroupPlayerIds(observed?.playerIds).sort()
}

void groupAnnouncementTopologySnapshotTimeout(Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  Map transaction = transactions[correlationId]
  if(transaction?.phase == 'SNAPSHOTTING') {
    logWarn("Fresh topology snapshot timed out for announcement ${correlationId}; using the safe fallback route")
    completeGroupAnnouncementTopologySnapshot(transaction, true)
  }
}

void completeGroupAnnouncementTopologySnapshot(Map transaction, Boolean timedOut) {
  if(transaction == null || transaction.phase != 'SNAPSHOTTING') { return }
  String groupDni = transaction.groupDni?.toString()
  String correlationId = transaction.correlationId?.toString()
  Map queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  Map item = queue?.active instanceof Map ? (Map)queue.active : null
  if(item?.correlationId?.toString() != correlationId) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<ChildDeviceWrapper> players = normalizeGroupPlayerIds(item.playerIds).collect { String id -> rinconMap[id] }
      .findAll { ChildDeviceWrapper player -> player != null }
  ChildDeviceWrapper coordinator = rinconMap[item.coordinatorId?.toString()]
  if(coordinator == null || players.size() != normalizeGroupPlayerIds(item.playerIds).size()) {
    logWarn("Announcement ${correlationId} has missing target players; using audioClip on available members")
    item.mode = 'AUDIO_CLIP'
    dispatchGroupAnnouncementClip(item, players, item.command == 'playHighPriorityTrack')
    scheduleGroupAnnouncementWatchdog(groupDni, item)
    return
  }
  Map snapshot = [observations: transaction.observations ?: [:],
    requestedPlayerIds: transaction.requestedPlayerIds ?: [], timedOut: timedOut]
  transaction.phase = 'ROUTING'
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[correlationId] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  if(timedOut && item.forceCoordinatorStream != true &&
      getAnnouncementMode(item.command == 'playHighPriorityTrack') == 'automatic') {
    logWarn("Routing announcement ${correlationId} to audioClip because complete topology could not be confirmed")
    item.mode = 'AUDIO_CLIP'
    dispatchGroupAnnouncementClip(item, players, item.command == 'playHighPriorityTrack')
  } else {
    dispatchGroupAnnouncementByPolicy(groupDni, queue, item, players, coordinator, snapshot)
  }
  Map<String, Map> latestTransactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  Map latest = latestTransactions[correlationId]
  if(latest?.phase == 'ROUTING') {
    latestTransactions.remove(correlationId)
    state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = latestTransactions
  }
}

void beginGroupAnnouncementTopologyTransaction(String groupDni, Map queue, Map item,
    List<ChildDeviceWrapper> players, Map snapshot, Boolean forced) {
  String correlationId = item.correlationId?.toString()
  Map<String, Map> observations = snapshot?.observations instanceof Map
      ? (Map<String, Map>)snapshot.observations : [:]
  List<String> targetIds = normalizeGroupPlayerIds(item.playerIds)
  if(observations.isEmpty() || !targetIds.every { String id -> observations[id] != null }) {
    if(forced) {
      logWarn("Forced coordinator-stream topology for ${correlationId} lacks a complete snapshot; sending the standard stream to the configured coordinator")
      item.mode = 'COORDINATOR_STREAM'
      queue.ownStreamUri = item.uri
      ChildDeviceWrapper coordinator = buildRinconMap()[item.coordinatorId?.toString()]
      coordinator?.playerPlayGroupAnnouncementStream(item.uri as String, toBigDecimal(item.volume), correlationId)
      scheduleGroupAnnouncementWatchdog(groupDni, item)
    } else {
      logWarn("Cannot safely change topology for ${correlationId}; falling back to audioClip")
      item.mode = 'AUDIO_CLIP'
      dispatchGroupAnnouncementClip(item, players, item.command == 'playHighPriorityTrack')
    }
    return
  }
  List<Map> originalGroups = []
  Set<String> affectedIds = new LinkedHashSet<String>()
  targetIds.each { String playerId ->
    Map observed = observations[playerId]
    List<String> members = normalizeGroupPlayerIds(observed.playerIds)
    affectedIds.addAll(members)
    String fingerprint = "${observed.groupId}:${observed.coordinatorId}:${members.sort().join(',')}"
    if(!originalGroups.any { Map existing -> existing.fingerprint == fingerprint }) {
      originalGroups.add([fingerprint: fingerprint, groupId: observed.groupId,
        coordinatorId: observed.coordinatorId, playerIds: members])
    }
  }
  Map transaction = [correlationId: correlationId, groupDni: groupDni, phase: 'PREPARING',
    forced: forced, originalGroups: originalGroups, affectedPlayerIds: affectedIds.toList().sort(),
    targetPlayerIds: targetIds, coordinatorId: item.coordinatorId?.toString(),
    expectedTopology: [:], userTopologyChanged: false, currentStep: 0, item: item]
  List<Map> steps = []

  // When Always standard stream is forced and the target contains an active
  // outside group's coordinator, first transfer coordination to the outside
  // members. The explicit operation also removes all target members from that
  // outside group while keeping its new coordinator and stream together.
  if(forced) {
    originalGroups.each { Map original ->
      List<String> remaining = normalizeGroupPlayerIds(original.playerIds).findAll { String id -> !targetIds.contains(id) }
      if(targetIds.contains(original.coordinatorId?.toString()) && remaining.size() > 1) {
        String temporaryCoordinator = remaining[0]
        steps.add([phase: 'PREPARE_OUTSIDE_GROUP', playerIds: remaining,
          coordinatorId: temporaryCoordinator, originalGroupFingerprint: original.fingerprint])
      }
    }
  }

  // Sonos createGroup keeps the coordinator of the largest existing group
  // whose members are all in the requested roster. Break up conflicting
  // source groups first so the configured target coordinator wins the tie
  // when createGroup is issued. For a source group coordinated outside the
  // target, retain its non-target members together and pull only target
  // players out, preserving that group's playback coordinator. A forced
  // coordinator handoff above already performs this split while preserving
  // the outside stream, so do not first isolate the original coordinator.
  originalGroups.each { Map original ->
    List<String> sourceMembers = normalizeGroupPlayerIds(original.playerIds)
    String sourceCoordinatorId = original.coordinatorId?.toString()
    List<String> targetMembers = sourceMembers.findAll { String id -> targetIds.contains(id) }
    if(!sourceCoordinatorId || !targetMembers) { return }
    List<String> outsideMembers = sourceMembers.findAll { String id -> !targetIds.contains(id) }
    if(forced && targetIds.contains(sourceCoordinatorId) && outsideMembers.size() > 1) { return }
    List<String> membersToKeep = targetIds.contains(sourceCoordinatorId)
        ? [sourceCoordinatorId]
        : outsideMembers
    membersToKeep = normalizeGroupPlayerIds(membersToKeep)
    if(membersToKeep.size() < sourceMembers.size()) {
      steps.add([phase: 'PREPARE_SOURCE_GROUP', playerIds: membersToKeep,
        coordinatorId: sourceCoordinatorId, sourceGroupFingerprint: original.fingerprint])
    }
  }
  steps.add([phase: 'GROUP_TARGET', playerIds: targetIds, coordinatorId: item.coordinatorId?.toString()])
  transaction.steps = steps
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[correlationId] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  queue.active.mode = 'TOPOLOGY_PREPARING'
  logInfo("Starting ${forced ? 'forced' : 'safe automatic'} topology transaction ${correlationId}; baseline=${originalGroups.collect { Map g -> [g.coordinatorId, g.playerIds] }}")
  startNextAnnouncementTopologyStep(transaction)
}

void startNextAnnouncementTopologyStep(Map transaction) {
  if(transaction == null || getActiveGroupOperation() != null) { return }
  List<Map> steps = transaction.steps instanceof List ? (List<Map>)transaction.steps : []
  Integer index = (transaction.currentStep ?: 0) as Integer
  if(index >= steps.size()) {
    transaction.phase = 'STREAMING'
    Map<String, Map> expected = [:]
    if(transaction.groupingFailed != true) {
      transaction.targetPlayerIds.each { String playerId ->
        expected[playerId] = [groupId: transaction.temporaryGroupId, coordinatorId: transaction.coordinatorId,
          playerIds: transaction.targetPlayerIds]
      }
      ((List<Map>)transaction.originalGroups).each { Map original ->
        List<String> remaining = normalizeGroupPlayerIds(original.playerIds).findAll { String id ->
          !((List<String>)transaction.targetPlayerIds).contains(id)
        }
        if(remaining) {
          String coordId = ((List<String>)transaction.targetPlayerIds).contains(original.coordinatorId?.toString())
              ? (steps.find { Map s -> s.originalGroupFingerprint == original.fingerprint }?.coordinatorId?.toString() ?: remaining[0])
              : original.coordinatorId?.toString()
          remaining.each { String playerId -> expected[playerId] = [groupId: null,
            coordinatorId: coordId, playerIds: remaining] }
        }
      }
    }
    transaction.expectedTopology = expected
    Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
        ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
    transactions[transaction.correlationId.toString()] = transaction
    state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
    Map queue = ((Map)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY])[transaction.groupDni.toString()] as Map
    Map item = transaction.item as Map
    item.mode = 'COORDINATOR_STREAM'
    queue.ownStreamUri = item.uri
    Map activeItem = queue.active instanceof Map ? (Map)queue.active : null
    if(activeItem?.correlationId?.toString() == transaction.correlationId?.toString()) {
      activeItem.mode = 'COORDINATOR_STREAM'
      queue.active = activeItem
    }
    Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
        ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
    queues[transaction.groupDni.toString()] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    ChildDeviceWrapper coordinator = buildRinconMap()[transaction.coordinatorId?.toString()]
    logInfo("Temporary target topology verified for ${transaction.correlationId}; dispatching coordinator stream on ${coordinator?.displayName}")
    coordinator?.playerPlayGroupAnnouncementStream(item.uri as String, toBigDecimal(item.volume), transaction.correlationId as String)
    scheduleGroupAnnouncementWatchdog(transaction.groupDni.toString(), item)
    return
  }
  Map step = steps[index]
  ChildDeviceWrapper groupDevice = getChildDevice(transaction.groupDni?.toString())
  if(groupDevice == null) {
    failAnnouncementTopologyTransaction(transaction, 'GROUP_DEVICE_UNAVAILABLE')
    return
  }
  List<String> ids = normalizeGroupPlayerIds(step.playerIds)
  String coordinatorId = step.coordinatorId?.toString()
  if(!ids || !coordinatorId || !ids.contains(coordinatorId)) {
    failAnnouncementTopologyTransaction(transaction, 'INVALID_TOPOLOGY_STEP')
    return
  }
  Map target = [requiredPlayerIds: [coordinatorId] + ids.findAll { String id -> id != coordinatorId }.sort(),
    configuredCoordinatorId: coordinatorId, desiredCoordinatorId: coordinatorId,
    resolvedCoordinatorId: coordinatorId, missingPlayerIds: []]
  Map operation = buildNewGroupOperation(groupDevice, GROUPING_MODE_EXPLICIT, target)
  operation.announcementTransactionId = transaction.correlationId
  operation.announcementTopologyPhase = step.phase
  operation.announcementTopologyStepIndex = index
  transaction.operationId = operation.operationId
  transaction.phase = step.phase == 'GROUP_TARGET' ? 'GROUPING_TARGET' :
      (step.phase == 'PREPARE_SOURCE_GROUP' ? 'PREPARING_SOURCE_GROUP' : 'PREPARING_OUTSIDE')
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[transaction.correlationId.toString()] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  saveActiveGroupOperation(operation)
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [overwrite: true,
    data: [operationId: operation.operationId]])
}

void handleAnnouncementTopologyOperationFinished(Map operation, String status) {
  String correlationId = operation?.announcementTransactionId?.toString()
  if(!correlationId) { return }
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  Map transaction = transactions[correlationId]
  if(transaction == null || transaction.operationId?.toString() != operation.operationId?.toString()) { return }
  if(transaction.phase == 'RESTORING') {
    if(status != 'SUCCEEDED') { logWarn("Topology restoration step ${operation.announcementTopologyStepIndex} failed for ${correlationId}; continuing recovery") }
    transaction.currentStep = ((transaction.currentStep ?: 0) as Integer) + 1
    transaction.operationId = null
    transactions[correlationId] = transaction
    state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
    startNextAnnouncementRestorationStep(transaction)
    return
  }
  if(status != 'SUCCEEDED' && transaction.phase == 'PREPARING_SOURCE_GROUP' &&
      transaction.forced != true) {
    fallbackAnnouncementTopologyToAudioClip(transaction,
        "Could not safely prepare a source group for the configured coordinator (${status})")
    return
  }
  if(status != 'SUCCEEDED' && transaction.phase == 'PREPARING_SOURCE_GROUP') {
    logWarn("Forced topology preparation step ${operation.announcementTopologyStepIndex} did not complete for ${correlationId}; continuing with forced regrouping")
  }
  if(status != 'SUCCEEDED' && transaction.phase == 'GROUPING_TARGET') {
    if(transaction.forced == true) {
      logWarn("Forced topology grouping was not confirmed for ${correlationId}; standard coordinator stream will still be sent as configured")
      transaction.groupingFailed = true
      transaction.currentStep = ((transaction.steps as List).size()) as Integer
      transactions[correlationId] = transaction
      state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
      startNextAnnouncementTopologyStep(transaction)
      return
    }
    fallbackAnnouncementTopologyToAudioClip(transaction,
        "Target grouping could not be confirmed (${status})")
    return
  }
  if(transaction.phase == 'GROUPING_TARGET' && status == 'SUCCEEDED') {
    transaction.temporaryGroupId = operation.observedGroupId?.toString()
  }
  transaction.currentStep = ((transaction.currentStep ?: 0) as Integer) + 1
  transaction.operationId = null
  transactions[correlationId] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  startNextAnnouncementTopologyStep(transaction)
}

void fallbackAnnouncementTopologyToAudioClip(Map transaction, String reason) {
  if(transaction == null) { return }
  String correlationId = transaction.correlationId?.toString()
  String groupDni = transaction.groupDni?.toString()
  Map item = transaction.item instanceof Map ? (Map)transaction.item : null
  if(!correlationId || !groupDni || item == null) { return }
  List<ChildDeviceWrapper> players = normalizeGroupPlayerIds(item.playerIds)
      .collect { String id -> buildRinconMap()[id] }
      .findAll { ChildDeviceWrapper player -> player != null }
  logWarn("Automatic topology transaction ${correlationId} is falling back to audioClip: ${reason}")
  item.mode = 'AUDIO_CLIP'
  transaction.groupingFailed = true
  transaction.phase = 'STREAMING'
  transaction.currentStep = 0
  transaction.expectedTopology = [:]
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[correlationId] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  Map activeItem = queue?.active instanceof Map ? (Map)queue.active : null
  if(activeItem?.correlationId?.toString() == correlationId) {
    activeItem.mode = 'AUDIO_CLIP'
    queue.active = activeItem
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
  }
  dispatchGroupAnnouncementClip(item, players, item.command == 'playHighPriorityTrack')
  scheduleGroupAnnouncementWatchdog(groupDni, item)
}

void failAnnouncementTopologyTransaction(Map transaction, String reason) {
  if(transaction == null) { return }
  logError("Announcement topology transaction ${transaction.correlationId} failed: ${reason}")
  transaction.phase = 'RESTORING'
  transaction.currentStep = 0
  transaction.operationId = null
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[transaction.correlationId.toString()] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  startNextAnnouncementRestorationStep(transaction)
}

void startNextAnnouncementRestorationStep(Map transaction) {
  if(transaction == null || getActiveGroupOperation() != null) { return }
  if(transaction.userTopologyChanged == true) {
    logWarn("Skipping topology restoration for ${transaction.correlationId}; preserving an unexpected user or external topology change")
    completeAnnouncementTopologyTransaction(transaction)
    return
  }
  String restorationReason = transaction.restorationReason?.toString()
  if(restorationReason && restorationReason != 'DONE') {
    ChildDeviceWrapper targetCoordinator = buildRinconMap()[transaction.coordinatorId?.toString()]
    if(isPlayerActivelyPlaying(targetCoordinator)) {
      logInfo("Deferring topology restoration for ${transaction.correlationId} while coordinator ${transaction.coordinatorId} has active playback")
      runIn(15, 'retryAnnouncementTopologyRestoration', [overwrite: true,
        data: [correlationId: transaction.correlationId]])
      return
    }
  }
  List<Map> originalGroups = transaction.originalGroups instanceof List ? (List<Map>)transaction.originalGroups : []
  Integer index = (transaction.currentStep ?: 0) as Integer
  if(index >= originalGroups.size()) {
    completeAnnouncementTopologyTransaction(transaction)
    return
  }
  Map original = originalGroups[index]
  Map target = [requiredPlayerIds: [original.coordinatorId.toString()] + normalizeGroupPlayerIds(original.playerIds)
      .findAll { String id -> id != original.coordinatorId?.toString() }.sort(),
    configuredCoordinatorId: original.coordinatorId?.toString(),
    desiredCoordinatorId: original.coordinatorId?.toString(),
    resolvedCoordinatorId: original.coordinatorId?.toString(), missingPlayerIds: []]
  ChildDeviceWrapper groupDevice = getChildDevice(transaction.groupDni?.toString())
  if(groupDevice == null) {
    logError("Cannot restore topology for ${transaction.correlationId}: group device is unavailable")
    completeAnnouncementTopologyTransaction(transaction)
    return
  }
  Map operation = buildNewGroupOperation(groupDevice, GROUPING_MODE_EXPLICIT, target)
  operation.announcementTransactionId = transaction.correlationId
  operation.announcementTopologyPhase = 'RESTORING'
  operation.announcementTopologyStepIndex = index
  transaction.operationId = operation.operationId
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions[transaction.correlationId.toString()] = transaction
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  saveActiveGroupOperation(operation)
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [overwrite: true,
    data: [operationId: operation.operationId]])
}

void retryAnnouncementTopologyRestoration(Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  Map transaction = transactions[correlationId]
  if(transaction?.phase == 'RESTORING') { startNextAnnouncementRestorationStep(transaction) }
}

void completeAnnouncementTopologyTransaction(Map transaction) {
  if(transaction == null) { return }
  String correlationId = transaction.correlationId?.toString()
  String groupDni = transaction.groupDni?.toString()
  Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  transactions.remove(correlationId)
  state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = transactions
  Map queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  if(queue?.active?.correlationId?.toString() == correlationId) {
    queue.active = null
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    logInfo("Topology transaction ${correlationId} complete; original topology restored or preserved and announcement queue may advance")
    runIn(1, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: false])
  }
}

void scheduleGroupAnnouncementWatchdog(String groupDni, Map item) {
  Integer duration = (item.duration ?: 0) as Integer
  Integer timeoutSeconds = duration > 0 ? Math.max(duration + 20, 30) : 180
  runIn(timeoutSeconds, 'groupAnnouncementWatchdog', [data: [groupDni: groupDni,
    correlationId: item.correlationId], overwrite: false])
}

/** Resolve the shared announcement policy for an individual player. */
Boolean shouldUseAudioClipForPlayer(String playerDni, Boolean highPriority = false) {
  String mode = getAnnouncementMode(highPriority)
  if(mode == 'alwaysNonInterrupting') { return true }
  if(mode == 'alwaysCoordinatorStream') { return false }

  ChildDeviceWrapper player = playerDni ? getChildDevice(playerDni) : null
  if(player == null) { return false }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  String coordinatorId = player.getDataValue('groupCoordinatorId')?.toString()
      ?: player.getDataValue('id')?.toString()
  ChildDeviceWrapper coordinator = coordinatorId ? rinconMap[coordinatorId] : player
  if(coordinator == null) { coordinator = player }
  String transportStatus = coordinator.currentValue('transportStatus', true)?.toString()?.toLowerCase()
  Map playbackActivity = getPlayerPlaybackActivity(coordinator)
  String playbackState = playbackActivity.playbackState?.toString()
  Boolean active = playbackActivity.active == true
  logTrace("Player announcement route check player=${playerDni}, coordinator=${coordinatorId}, priority=${highPriority ? 'high' : 'normal'}, mode=${mode}, playbackState=${playbackState}, playbackObservedAt=${playbackActivity.observedAt}, playbackSignal=${playbackActivity.signal}, transportStatus=${transportStatus}, useAudioClip=${active}")
  return active
}

void dispatchGroupAnnouncementClip(Map item, List<ChildDeviceWrapper> players, Boolean highPriority) {
  String correlationId = item.correlationId as String
  Integer duration = (item.duration ?: 0) as Integer
  String playerNames = players.collect { ChildDeviceWrapper player ->
    "${player.displayName} (${player.getDataValue('id')})"
  }.join(', ')
  logTrace("Preparing group audioClip ${correlationId} for players: ${playerNames}")
  players.each { ChildDeviceWrapper player ->
    if(highPriority) {
      player.playerLoadAudioClipHighPriority(item.uri as String, toBigDecimal(item.volume), correlationId, duration, true)
    } else {
      player.playerPrepareAudioClipForSynchronizedStart(item.uri as String, toBigDecimal(item.volume), correlationId, duration, true)
    }
  }
  players.each { ChildDeviceWrapper player -> player.prepareAudioClipForDispatch() }
  logTrace("Releasing group audioClip ${correlationId} to players: ${playerNames}")
  players.each { ChildDeviceWrapper player ->
    player.startPreparedAudioClip()
  }
  players.each { ChildDeviceWrapper player -> player.completePreparedAudioClipDispatch() }
}

void handleGroupAnnouncementProgress(ChildDeviceWrapper player, Map payload) {
  String correlationId = payload.correlationId?.toString()
  String phase = payload.phase?.toString()
  if(!correlationId || !phase) { return }
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  String groupDni = payload.groupDni?.toString()
  if(!groupDni) {
    groupDni = queues.find { String dni, Map q ->
      Map activeItem = q?.active instanceof Map ? (Map)q.active : null
      activeItem?.correlationId?.toString() == correlationId &&
          normalizeGroupPlayerIds(activeItem.playerIds).contains(player.getDataValue('id')?.toString())
    }?.key
  }
  if(!groupDni) { return }
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  Map active = queue?.active instanceof Map ? (Map)queue.active : null
  if(active == null || active.correlationId?.toString() != correlationId) {
    logTrace("Ignoring stale group announcement callback ${correlationId} from ${player.getDataValue('id')}")
    return
  }
  if(phase == 'INTERRUPTED' && payload.externalPlayback == true) {
    queue.externalTakeover = true
    queue.externalSourceUri = payload.replacementUri?.toString()
    logTrace("Received coordinator takeover correlation=${correlationId}, player=${player.getDataValue('id')}, playbackState=${payload.playbackState}, sourceUri=${payload.sourceUri}, replacementUri=${payload.replacementUri}; pending=${queue.pending?.size() ?: 0}")
    logInfo("Coordinator stream ${correlationId} was interrupted by external playback; queued Automatic announcements will use audioClip while it remains active")
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    finishGroupAnnouncement(groupDni, correlationId, 'INTERRUPTED_BY_EXTERNAL_PLAYBACK')
    return
  }
  Map<String, String> statuses = active.playerStatuses instanceof Map ? (Map<String, String>)active.playerStatuses : [:]
  String playerId = player.getDataValue('id')?.toString()
  if(playerId) { statuses[playerId] = phase; active.playerStatuses = statuses }
  logTrace("Group announcement ${correlationId} callback from ${playerId}: ${phase}; observed=${statuses}")
  if(phase == 'ACTIVE') {
    Integer duration = (active.duration ?: 0) as Integer
    Integer timeoutSeconds = duration > 0 ? Math.max(duration + 20, 30) : 180
    runIn(timeoutSeconds, 'groupAnnouncementWatchdog', [data: [groupDni: groupDni,
      correlationId: correlationId], overwrite: false])
  }
  String mode = active.mode?.toString()
  String coordinatorId = active.coordinatorId?.toString()
  logTrace("Group announcement ${correlationId} progress routing: mode=${mode ?: 'unset'}, coordinator=${coordinatorId ?: 'unset'}, callbackPlayer=${playerId ?: 'unset'}, phase=${phase}")
  if(mode != 'AUDIO_CLIP') {
    if(playerId == coordinatorId && phase in ['DONE', 'FAILED']) {
      finishGroupAnnouncement(groupDni, correlationId, phase)
    }
  } else {
    List<String> expected = normalizeGroupPlayerIds(active.playerIds)
    if(phase == 'FAILED' || phase == 'DISMISSED') { statuses[playerId] = 'DONE' }
    // INTERRUPTED is not terminal here: HIGH priority clips suspend regular
    // clips and the player driver reports ACTIVE again when it resumes them.
    if(expected && expected.every { String id -> statuses[id] in ['DONE', 'FAILED', 'DISMISSED'] }) {
      finishGroupAnnouncement(groupDni, correlationId, 'DONE')
    }
  }
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
}

void groupAnnouncementWatchdog(Map data = null) {
  String groupDni = data?.groupDni?.toString()
  String correlationId = data?.correlationId?.toString()
  if(!groupDni || !correlationId) { return }
  Map queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map active = queues[groupDni]?.active instanceof Map ? (Map)queues[groupDni].active : null
  if(active?.correlationId?.toString() == correlationId) {
    Map<String, Map> transactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
        ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
    if(transactions[correlationId]?.phase == 'RESTORING') {
      logTrace("Ignoring group announcement watchdog ${correlationId}; topology restoration is already underway")
      return
    }
    logWarn("Group announcement ${correlationId} timed out; advancing queue")
    finishGroupAnnouncement(groupDni, correlationId, 'TIMEOUT')
  }
}

void finishGroupAnnouncement(String groupDni, String correlationId, String reason) {
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  if(queue?.active?.correlationId?.toString() != correlationId) { return }
  logInfo("Group announcement ${correlationId} finished (${reason}) for ${groupDni}")
  Map<String, Map> topologyTransactions = state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] : [:]
  Map topologyTransaction = topologyTransactions[correlationId]
  if(topologyTransaction?.phase == 'STREAMING') {
    topologyTransaction.phase = 'RESTORING'
    topologyTransaction.currentStep = 0
    topologyTransaction.restorationReason = reason
    topologyTransactions[correlationId] = topologyTransaction
    state[GROUP_ANNOUNCEMENT_TOPOLOGY_STATE_KEY] = topologyTransactions
    logInfo("Beginning topology restoration for ${correlationId} after ${reason}")
    startNextAnnouncementRestorationStep(topologyTransaction)
    if(reason == 'INTERRUPTED_BY_EXTERNAL_PLAYBACK' && queue.externalTakeover == true) {
      queue.active = null
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
      logInfo("Releasing announcement queue for ${groupDni} after external playback takeover; the next automatic message can use audioClip while topology restoration waits")
      runIn(1, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: false])
    }
    return
  }
  queue.active = null
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
  runIn(1, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: false])
}

void startNextGroupAnnouncementCallback(Map data = null) {
  String groupDni = data?.groupDni?.toString()
  if(groupDni) { startNextGroupAnnouncement(groupDni) }
}

/**
 * Queue a group command request received through the location event subscription.
 */
void enqueueGroupCommandRequest(Map request) {
  if(!(request instanceof Map)) {
    return
  }

  String groupDni = request?.groupDni as String
  String command = request?.command as String
  if(!groupDni || !command) {
    logWarn("Ignoring group command request with invalid target or command: ${JsonOutput.toJson(request)}")
    return
  }
  // Location events are hub-wide; another app instance may own this group.
  if(getChildDevice(groupDni) == null) { return }

  String requestId = request.requestId as String
  Map<String, List<String>> receivedRequests = state.receivedGroupCommandRequestIds instanceof Map
      ? (Map<String, List<String>>)state.receivedGroupCommandRequestIds
      : [:]
  List<String> receivedForGroup = receivedRequests[groupDni] instanceof List
      ? (List<String>)receivedRequests[groupDni]
      : []
  if(requestId && receivedForGroup.contains(requestId)) {
    logTrace("Ignoring duplicate group command request ${requestId} (${command}) for ${groupDni}; acknowledging without redispatch")
    acknowledgeGroupCommandRequest(groupDni, requestId)
    return
  }

  // Audio clips are time-sensitive. Dispatch them from the event handler so
  // scheduler latency cannot strand an announcement in the delayed command
  // queue; each clip is already prepared on all players before release.
  if(command in ['playAudioClip', 'playHighPriorityTrack', 'enqueueLowPriorityTrack']) {
    logTrace("Dispatching time-sensitive group audio command ${command} immediately for ${groupDni}, request=${requestId ?: 'none'}")
    if(requestId) {
      receivedForGroup.add(requestId)
      if(receivedForGroup.size() > 200) { receivedForGroup = receivedForGroup.takeRight(200) }
      receivedRequests[groupDni] = receivedForGroup
      state.receivedGroupCommandRequestIds = receivedRequests
      acknowledgeGroupCommandRequest(groupDni, requestId)
    }
    processGroupCommandRequest([request: request])
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
  if(requestId) {
    receivedForGroup.add(requestId)
    if(receivedForGroup.size() > 200) { receivedForGroup = receivedForGroup.takeRight(200) }
    receivedRequests[groupDni] = receivedForGroup
    state.receivedGroupCommandRequestIds = receivedRequests
    acknowledgeGroupCommandRequest(groupDni, requestId)
  }
  runIn(1, 'drainGroupCommandRequests', [overwrite: true])
}

void acknowledgeGroupCommandRequest(String groupDni, String requestId) {
  ChildDeviceWrapper groupDevice = getChildDevice(groupDni)
  if(groupDevice == null) { return }
  groupDevice.acknowledgeGroupCommand(requestId)
  logTrace("Acknowledged receipt of group command ${requestId} for ${groupDni}")
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
  if(request?.sequence != null) {
    try {
      return request.sequence.toString().toInteger()
    } catch(Exception ignored) {
      return Integer.MAX_VALUE
    }
  }
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

  Map activeOperation = getActiveGroupOperation()
  if(activeOperation && activeOperation.groupDni as String == groupDni &&
      !activeOperation.favoriteId && activeOperation.phase != 'COMPLETE' &&
      isPlaybackCommandThatWaitsForTopology(command)) {
    // Piston runs commonly send media or transport commands immediately after
    // explicit group activation. Hold commands that would cancel topology until
    // the group operation completes; explicit ungroup/removal commands remain
    // able to supersede it, and Favorite loads retain their attach behavior.
    logTrace("Waiting for topology change to complete before processing group command ${command} for ${groupDni}")
    deferGroupCommandRequest(request)
    return
  }
  if(isGroupOperationCommand(command) && activeOperation && activeOperation.groupDni as String != groupDni) {
    deferGroupCommandRequest(request)
    return
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
      case 'regroupAfterUngroup':
      case 'createGroup':
      case 'groupPlayers':
        startGroupTopologyOperation(groupDevice, GROUPING_MODE_EXPLICIT, args)
        break

      case 'evictUnlistedPlayers':
        // Eviction is never a generic explicit-group rebuild. Force the
        // removal-only safety path even when an older group driver does not
        // include the marker in its request payload.
        startGroupTopologyOperation(groupDevice, GROUPING_MODE_EXPLICIT,
            args + [evictUnlistedOnly: true])
        break

      case 'off':
      case 'removePlayersFromCoordinator':
      case 'ungroupPlayers':
        cancelActiveGroupOperation(groupDni, "${command.toUpperCase()}_REQUESTED")
        if(coordinator && followerIds) {
          prepareExpectedTopologyForMutation(coordinator, [], followerIds)
          coordinator.playerModifyGroupMembers([], followerIds)
        } else if(!coordinator) {
          logWarn("Group command ${command} could not resolve coordinator ${coordinatorId}")
        }
        break

      case 'joinPlayersToCoordinator':
        startGroupTopologyOperation(groupDevice, GROUPING_MODE_ADDITIVE, args)
        break

      case 'addPlayerToGroup':
        // This is intentionally a one-player membership mutation. Do not route
        // through startGroupTopologyOperation(), which operates on the full
        // configured roster when a group device is inactive.
        ChildDeviceWrapper anchor = configuredCoordinator
        ChildDeviceWrapper anchorCoordinator = resolveLiveGroupCoordinator(anchor, rinconMap)
        addPlayerToCoordinatorGroup(anchorCoordinator, args.playerIdentifier as String)
        break

      case 'refresh':
        refreshGroupDeviceState(groupDevice, coordinator, playerDevices, args)
        break

      case 'playAudioClip':
        cancelActiveGroupOperation(groupDni, 'AUDIO_CLIP_REQUESTED')
        enqueueGroupAnnouncement(groupDevice, coordinator, playerDevices, command, args, requestId)
        break

      case 'playHighPriorityTrack':
        cancelActiveGroupOperation(groupDni, 'HIGH_PRIORITY_TRACK_REQUESTED')
        enqueueGroupAnnouncement(groupDevice, coordinator, playerDevices, command, args, requestId)
        break

      case 'enqueueLowPriorityTrack':
        cancelActiveGroupOperation(groupDni, 'LOW_PRIORITY_TRACK_REQUESTED')
        enqueueGroupAnnouncement(groupDevice, coordinator, playerDevices, command, args, requestId)
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
        if(coordinator) { coordinator.devicePlayTrack(args.uri as String, toBigDecimal(args.volume)) }
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

Boolean isPlaybackCommandThatWaitsForTopology(String command) {
  return command in [
    'play', 'pause', 'stop', 'nextTrack', 'previousTrack', 'playTrack', 'setTrack',
    'playAudioClip', 'playHighPriorityTrack', 'enqueueLowPriorityTrack',
    'loadPlaylist', 'loadPlaylistFull', 'setRepeatMode', 'repeatOne', 'repeatAll', 'repeatNone',
    'setShuffle', 'shuffleOn', 'shuffleOff', 'setCrossfade', 'enableCrossfade', 'disableCrossfade'
  ]
}

Boolean isPlayerPlaybackCommandThatWaitsForGroupOperation(String command) {
  return command in [
    'play', 'pause', 'stop', 'nextTrack', 'previousTrack', 'setTrack', 'selectLineIn', 'selectTV', 'playTrack',
    'playHighPriorityTrack', 'loadAudioClip', 'loadHighPriorityAudioClip',
    'speak', 'speakNoRestore', 'playHighPriorityTTS',
    'loadFavoriteFull', 'loadPlaylistFull', 'setRepeatMode', 'repeatOne', 'repeatAll', 'repeatNone',
    'setShuffle', 'shuffleOn', 'shuffleOff', 'setCrossfade', 'enableCrossfade', 'disableCrossfade'
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
  return UUID.randomUUID().toString()
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
  String command = request.command as String
  String groupDni = request.groupDni as String
  if(isCoalescibleTransportStateCommand(command)) {
    Integer replaced = deferred.count { Map pending ->
      pending.groupDni?.toString() == groupDni && isCoalescibleTransportStateCommand(pending.command as String)
    }
    deferred = deferred.findAll { Map pending ->
      pending.groupDni?.toString() != groupDni || !isCoalescibleTransportStateCommand(pending.command as String)
    }
    if(replaced > 0) {
      logDebug("Deferred group transport command ${command} replaced ${replaced} queued state command(s) for ${groupDni}")
    }
  }
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
  Map configuredGroup = getConfiguredGroupDefinition(groupDevice)
  String configuredCoordinatorId = groupDevice.getDataValue('groupCoordinatorId') as String ?:
      configuredGroup.groupCoordinatorId as String
  if(configuredCoordinatorId && !requiredPlayerIds.contains(configuredCoordinatorId)) {
    requiredPlayerIds.add(configuredCoordinatorId)
    requiredPlayerIds = requiredPlayerIds.unique().sort()
  }
  if(configuredGroup.playerIds instanceof Collection) {
    requiredPlayerIds = (requiredPlayerIds + normalizeGroupPlayerIds(configuredGroup.playerIds)).unique().sort()
  }
  ChildDeviceWrapper configuredCoordinator = configuredCoordinatorId ? rinconMap[configuredCoordinatorId] : null
  ChildDeviceWrapper activeCoordinator = resolveGroupCommandCoordinator(configuredCoordinator, requiredPlayerIds, rinconMap)
  ChildDeviceWrapper selectedCoordinator
  if(groupingMode in [GROUPING_MODE_EXPLICIT, GROUPING_MODE_ADDITIVE]) {
    // Both saved-group commands have a configured coordinator. Additive
    // membership may preserve extra players, but it must not select a
    // different coordinator just because another member is currently leader.
    selectedCoordinator = configuredCoordinator ?: activeCoordinator
  } else {
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
    configuredCoordinatorId: target.configuredCoordinatorId,
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
    topologyLastObservedAt: 0L,
    topologyRefreshAt: startTime,
    topologyObservations: [:],
    topologyMutationSent: false,
    topologyRequestId: null,
    topologyCheckRequestId: null,
    lastCommandAt: 0L,
    lastCommand: null,
    registeredPlayerIds: [],
    registrationRequestIds: [:],
    registrationRequestedAt: [:],
    registrationEventPlayerIds: [],
    observedPlayerIds: [],
    observedGroupId: null,
    observedCoordinatorId: null,
    lastTopologyObservations: [],
    loadAcknowledged: false,
    favoriteCommandDelivered: false,
    loadErrorCode: null,
    loadErrorReason: null,
    playbackState: null,
    favoriteCommandAt: 0L,
    favoriteAttemptId: null,
    favoriteRequestId: null,
    playRequestId: null,
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
    exactFavoriteId: null,
    evictUnlistedOnly: false
  ]
  if(favoriteSpec != null) {
    operation.putAll(normalizeGroupFavoriteSpec(favoriteSpec))
  }
  return operation
}

/**
 * Store the expected end topology on each affected player device. Device data
 * values are shared with that device's driver instance and are hidden from the
 * Current States table.
 */
void setExpectedTopologyForPlayers(Map<String, Map> expectedByRincon, String operationId, Long expiresAt) {
  if(expectedByRincon == null || expectedByRincon.isEmpty()) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  Map<String, Map> completeExpectation = [:]
  expectedByRincon.each { String playerId, Map topology ->
    if(playerId && topology != null) {
      completeExpectation[playerId] = new LinkedHashMap(topology)
    }
  }

  // When moving one or more members out of their previous groups, retain an
  // expectation for the members left behind too. Their remaining group should
  // not be held open or briefly published with the moved players still listed.
  Set<String> transitioningPlayers = new LinkedHashSet<String>(completeExpectation.keySet())
  transitioningPlayers.toList().each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player == null) { return }
    List<String> previousMembers = normalizeGroupPlayerIds(player.getDataValue('groupPlayerIds'))
    List<String> remainingMembers = previousMembers.findAll { String memberId ->
      !transitioningPlayers.contains(memberId)
    }
    if(remainingMembers.isEmpty()) { return }
    String previousCoordinatorId = player.getDataValue('groupCoordinatorId')?.toString()
    String remainingCoordinator = remainingMembers.contains(previousCoordinatorId)
        ? previousCoordinatorId
        : remainingMembers[0]
    remainingMembers.each { String remainingPlayerId ->
      if(!completeExpectation.containsKey(remainingPlayerId)) {
        completeExpectation[remainingPlayerId] = [
          coordinatorId: remainingCoordinator,
          memberIds: remainingMembers
        ]
      }
    }
  }

  completeExpectation.each { String playerId, Map topology ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null && topology != null) {
      Map expected = [
        operationId: operationId,
        expiresAt: expiresAt,
        coordinatorId: topology.coordinatorId,
        memberIds: ((Collection<String>)topology.memberIds).toList().sort()
      ]
      try {
        player.updateDataValue('expectedGroupTopology', JsonOutput.toJson(expected))
      } catch(Exception e) {
        logWarn("Could not store expected topology for ${playerId}: ${e.message}")
      }
    }
  }
}

void prepareExpectedTopologyForMutation(ChildDeviceWrapper coordinator, List<String> addIds,
    List<String> removeIds, Map<String, Map> suppliedExpectation = null) {
  if(coordinator == null) { return }
  List<String> currentMembers = normalizeGroupPlayerIds(coordinator.getDataValue('groupPlayerIds'))
  if(!currentMembers) {
    String coordinatorId = coordinator.getDataValue('id') as String
    if(coordinatorId) { currentMembers = [coordinatorId] }
  }
  List<String> resultingMembers = normalizeGroupPlayerIds((currentMembers - (removeIds ?: [])) + (addIds ?: []))
  String coordinatorId = coordinator.getDataValue('groupCoordinatorId') as String ?: coordinator.getDataValue('id') as String
  if(!resultingMembers.contains(coordinatorId)) { coordinatorId = resultingMembers ? resultingMembers[0] : coordinator.getDataValue('id') as String }
  Map<String, Map> expected = suppliedExpectation != null ? suppliedExpectation : [:]
  if(suppliedExpectation == null) {
    resultingMembers.each { String playerId -> expected[playerId] = [coordinatorId: coordinatorId, memberIds: resultingMembers] }
    (removeIds ?: []).each { String playerId -> if(!expected.containsKey(playerId)) { expected[playerId] = [coordinatorId: playerId, memberIds: [playerId]] } }
  }
  setExpectedTopologyForPlayers(expected, UUID.randomUUID().toString(),
      (now() as Long) + TOPOLOGY_CHANGE_SUPPRESSION_SECONDS * 1000L)
}

void prepareExpectedTopologyForCreateGroup(ChildDeviceWrapper coordinator, List<String> playerIds) {
  if(coordinator == null) { return }
  List<String> requested = normalizeGroupPlayerIds(playerIds)
  String coordinatorId = requested ? requested[0] : coordinator.getDataValue('id') as String
  Map<String, Map> expected = [:]
  requested.each { String playerId -> expected[playerId] = [coordinatorId: coordinatorId, memberIds: requested] }
  normalizeGroupPlayerIds(coordinator.getDataValue('groupPlayerIds')).each { String playerId ->
    if(!expected.containsKey(playerId)) { expected[playerId] = [coordinatorId: playerId, memberIds: [playerId]] }
  }
  setExpectedTopologyForPlayers(expected, UUID.randomUUID().toString(),
      (now() as Long) + TOPOLOGY_CHANGE_SUPPRESSION_SECONDS * 1000L)
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
  operation.evictUnlistedOnly = args?.evictUnlistedOnly == true ||
      args?.evictUnlistedOnly?.toString() == 'true'
  saveActiveGroupOperation(operation)
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)
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
  Object storedMode = groupDevice.getDataValue('lastGroupingMode') ?: groupDevice.currentValue('groupingMode', true)
  String deviceMode = normalizeGroupOperationMode(storedMode, GROUPING_MODE_CURRENT)
  String groupingMode = contextMode != GROUPING_MODE_CURRENT ? contextMode : deviceMode
  Map target = buildGroupOperationTarget(groupDevice, groupingMode)
  if(!target.requiredPlayerIds || target.requiredPlayerIds.isEmpty()) {
    logWarn("Group Favorite ${favoriteSpec.favoriteId} has no configured players for ${groupDni}")
    return
  }

  Map operation = buildNewGroupOperation(groupDevice, groupingMode, target, favoriteSpec)
  saveActiveGroupOperation(operation)
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)
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
  operation.favoriteCommandDelivered = false
  operation.playCommandDelivered = false
  operation.loadErrorCode = null
  operation.loadErrorReason = null
  operation.playbackState = null
  operation.favoriteCommandAt = now()
  operation.favoriteAttemptId = null
  operation.favoriteRequestId = null
  operation.playRequestId = null
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
  clearRegisteredGroupOperations(operation)
  operation.registeredPlayerIds = []
  operation.registrationRequestIds = [:]
  operation.registrationRequestedAt = [:]
  operation.registrationEventPlayerIds = []
  saveActiveGroupOperation(operation)
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)
  publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
  refreshGroupOperationTopology([operationId: operation.operationId])
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
    overwrite: true,
    data: [operationId: operation.operationId]
  ])
}

void registerGroupOperationOnPlayers(Map operation, Collection ids) {
  if(operation == null || !operation.operationId) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> registered = normalizeGroupPlayerIds(operation.registeredPlayerIds)
  Map<String, String> requestIds = operation.registrationRequestIds instanceof Map
      ? (Map<String, String>)operation.registrationRequestIds
      : [:]
  Map<String, Long> requestedAt = operation.registrationRequestedAt instanceof Map
      ? (Map<String, Long>)operation.registrationRequestedAt
      : [:]
  normalizeGroupPlayerIds(ids).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player == null || registered.contains(playerId)) { return }
    String requestId = requestIds[playerId]
    if(!requestId) {
      requestId = UUID.randomUUID().toString()
      requestIds[playerId] = requestId
    }
    Long lastRequestedAt = requestedAt[playerId] ?: 0L
    if(lastRequestedAt > 0L && now() < lastRequestedAt + 5000L) { return }
    try {
      if(operation.favoriteId) {
        player.registerGroupFavoriteOperation(
          operation.operationId as String,
          operation.favoriteId as String,
          requestId
        )
      } else {
        player.registerGroupTopologyOperation(operation.operationId as String, requestId)
      }
      registered.add(playerId)
      requestedAt[playerId] = now()
      logTrace("Registered operation ${operation.operationId} on ${playerId} with request ${requestId}")
    } catch(Exception e) {
      requestedAt[playerId] = now()
      logWarn("Could not register group operation ${operation.operationId} on ${playerId}: ${e.message}")
    }
  }
  operation.registeredPlayerIds = registered.unique().sort()
  operation.registrationRequestIds = requestIds
  operation.registrationRequestedAt = requestedAt
  saveActiveGroupOperation(operation)
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
          operation.favoriteExpectedGroupId as String,
          operation.favoriteRequestId as String
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
  Map topology = readGroupTopologyFromObservations(operation, observations, refreshAt)
  if(operation?.groupingMode != GROUPING_MODE_EXPLICIT) {
    return topology
  }

  // A fresh groups response from the configured coordinator describes its
  // complete group roster. Do not require a second response from every member:
  // a member can miss the request while the coordinator already confirms the
  // exact group topology.
  String coordinatorId = operation?.desiredCoordinatorId as String
  Map coordinatorObservation = topology.observations instanceof List
      ? ((List<Map>)topology.observations).find { Map item ->
          item.playerId?.toString() == coordinatorId && item.fresh == true
        }
      : null
  if(coordinatorObservation == null || !coordinatorObservation.groupId ||
      !coordinatorObservation.coordinatorId || !coordinatorObservation.playerIds) {
    return topology
  }

  List<String> playerIds = normalizeGroupPlayerIds(coordinatorObservation.playerIds)
  if(playerIds.isEmpty()) {
    return topology
  }
  String groupId = coordinatorObservation.groupId as String
  String observedCoordinatorId = coordinatorObservation.coordinatorId as String
  topology.fresh = true
  topology.consistent = true
  topology.groupId = groupId
  topology.coordinatorId = observedCoordinatorId
  topology.playerIds = playerIds
  topology.authorityPlayerId = coordinatorId
  topology.fingerprint = "${coordinatorId}:${groupId}:${observedCoordinatorId}:${playerIds.join(',')}"
  return topology
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
    "${item.playerId}: fresh=${item.fresh == true}, group=${item.groupId ?: 'unknown'}, coordinator=${item.coordinatorId ?: 'unknown'}, members=${normalizeGroupPlayerIds(item.playerIds).join(',') ?: 'unknown'}"
  }.join('; ')
}

Map readEvictionCoordinatorTopology(Map operation) {
  String configuredCoordinatorId = operation?.configuredCoordinatorId as String
  if(!configuredCoordinatorId) {
    return null
  }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  ChildDeviceWrapper coordinator = rinconMap[configuredCoordinatorId]
  if(coordinator == null) {
    return null
  }

  String liveCoordinatorId = getGroupOperationChildValue(coordinator, 'groupCoordinatorId')?.toString()
  String groupId = getGroupOperationChildValue(coordinator, 'groupId')?.toString()
  List<String> playerIds = normalizeGroupPlayerIds(
    getGroupOperationChildValue(coordinator, 'groupPlayerIds')
  )
  Boolean isCoordinator = coordinator.getDataValue('isGroupCoordinator') == 'true'
  if(!isCoordinator) {
    try {
      isCoordinator = coordinator.currentValue('isGroupCoordinator', true) == 'on'
    } catch(Exception ignored) {
      isCoordinator = false
    }
  }
  if(liveCoordinatorId != configuredCoordinatorId || !groupId || playerIds.isEmpty() || !isCoordinator) {
    return null
  }

  return [
    fresh: true,
    consistent: true,
    groupId: groupId,
    coordinatorId: liveCoordinatorId,
    playerIds: playerIds,
    observations: [[
      playerId: configuredCoordinatorId,
      groupId: groupId,
      coordinatorId: liveCoordinatorId,
      playerIds: playerIds,
      fresh: true,
      observedAt: now()
    ]],
    fingerprint: "${groupId}:${liveCoordinatorId}:${playerIds.join(',')}"
  ]
}

/**
 * Return membership from the topology view that belongs to the coordinator
 * selected for an additive operation. When the required players are in
 * different groups, combining every observation would incorrectly make those
 * separate groups look like one group. The selected coordinator's cached
 * membership is the safe fallback until a fresh, consistent snapshot arrives.
 */
List<String> getAdditiveObservedPlayerIds(Map operation, Map topology) {
  if(topology?.consistent == true) {
    return normalizeGroupPlayerIds(topology.playerIds)
  }
  String coordinatorId = operation?.resolvedCoordinatorId as String
  List<Map> observations = topology?.observations instanceof List
      ? (List<Map>)topology.observations
      : []
  Map coordinatorObservation = observations.find { Map item ->
    item.playerId?.toString() == coordinatorId
  }
  if(coordinatorObservation == null && operation?.desiredCoordinatorId) {
    coordinatorObservation = observations.find { Map item ->
      item.playerId?.toString() == operation.desiredCoordinatorId.toString()
    }
  }
  return normalizeGroupPlayerIds(coordinatorObservation?.playerIds)
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
  // ADDITIVE accepts extra members, but saved-group additive requests still
  // require the saved coordinator. CURRENT remains a read-only observation.
  if(!observed.containsAll(required)) { return false }
  String desiredCoordinatorId = operation.desiredCoordinatorId as String
  return operation.groupingMode != GROUPING_MODE_ADDITIVE ||
      !desiredCoordinatorId || topology.coordinatorId == desiredCoordinatorId
}

void updateGroupTopologyStability(Map operation, Map topology) {
  Boolean satisfied = isGroupTopologySatisfied(operation, topology)
  if(!satisfied) {
    operation.stableObservations = 0
    operation.topologyFingerprint = null
    operation.topologyLastObservedAt = 0L
    return
  }
  Long latestObservedAt = topology.observations instanceof List
      ? ((List<Map>)topology.observations).findAll { Map item -> item.fresh == true }
          .collect { Map item -> item.observedAt as Long }
          .findAll { Long observedAt -> observedAt != null }
          .max()
      : null
  if(latestObservedAt == null) {
    operation.stableObservations = 0
    operation.topologyFingerprint = null
    operation.topologyLastObservedAt = 0L
    return
  }
  String fingerprint = topology.fingerprint as String
  Long previousObservedAt = operation.topologyLastObservedAt as Long ?: 0L
  if(fingerprint && fingerprint == operation.topologyFingerprint && latestObservedAt > previousObservedAt) {
    operation.stableObservations = ((operation.stableObservations ?: 0) as Integer) + 1
    operation.topologyLastObservedAt = latestObservedAt
  } else if(fingerprint != operation.topologyFingerprint) {
    operation.topologyFingerprint = fingerprint
    operation.stableObservations = 1
    operation.topologyLastObservedAt = latestObservedAt
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

void sendAdditiveGroupTopologyCommand(Map operation, Map topology,
    Map<String, ChildDeviceWrapper> rinconMap, List<String> requiredPlayerIds) {
  String coordinatorId = operation.desiredCoordinatorId as String ?: operation.configuredCoordinatorId as String
  ChildDeviceWrapper coordinator = coordinatorId ? rinconMap[coordinatorId] : null
  if(coordinator == null) {
    failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE',
        "Could not resolve the configured coordinator ${coordinatorId ?: '(none)'} for additive grouping")
    return
  }
  operation.resolvedCoordinatorId = coordinatorId

  List<Map> observations = topology?.observations instanceof List
      ? (List<Map>)topology.observations
      : []

  if(operation.coordinatorTransferStarted == true) {
    if(operation.coordinatorTransferGroupCreated == true) {
      operation.lastCommand = 'refreshGroupTopology'
      coordinator.playerGetGroupsFull()
      ChildDeviceWrapper previousCoordinator = rinconMap[operation.coordinatorTransferPreviousCoordinatorId as String]
      if(previousCoordinator != null && previousCoordinator != coordinator) {
        previousCoordinator.playerGetGroupsFull()
      }
    } else {
      Map coordinatorObservation = observations.find { Map item ->
        item.playerId?.toString() == coordinatorId && item.fresh == true
      }
      List<String> coordinatorMembers = normalizeGroupPlayerIds(coordinatorObservation?.playerIds)
      if(coordinatorObservation?.coordinatorId?.toString() == coordinatorId &&
          coordinatorMembers == [coordinatorId]) {
        List<String> transferPlayerIds = normalizeGroupPlayerIds(operation.coordinatorTransferPlayerIds)
        if(!transferPlayerIds.contains(coordinatorId)) { transferPlayerIds.add(0, coordinatorId) }
        List<String> playerIdsInCoordinatorOrder = [coordinatorId] +
            transferPlayerIds.findAll { String playerId -> playerId != coordinatorId }
        operation.lastCommand = 'createAdditiveGroupAfterTransfer'
        operation.topologyRequestId = UUID.randomUUID().toString()
        logDebug("Dispatching additive coordinator restoration ${operation.topologyRequestId} for group operation ${operation.operationId}")
        prepareExpectedTopologyForCreateGroup(coordinator, playerIdsInCoordinatorOrder)
        coordinator.playerCreateGroup(playerIdsInCoordinatorOrder)
        operation.coordinatorTransferGroupCreated = true
        operation.topologyMutationSent = true
      } else {
        operation.lastCommand = 'refreshGroupTopology'
        coordinator.playerGetGroupsFull()
        ChildDeviceWrapper previousCoordinator = rinconMap[operation.coordinatorTransferPreviousCoordinatorId as String]
        if(previousCoordinator != null && previousCoordinator != coordinator) {
          previousCoordinator.playerGetGroupsFull()
        }
      }
    }
  } else if(topology?.fresh == true && topology?.consistent == true &&
      topology.coordinatorId != coordinatorId &&
      normalizeGroupPlayerIds(topology.playerIds).contains(coordinatorId)) {
    ChildDeviceWrapper previousCoordinator = rinconMap[topology.coordinatorId as String]
    if(previousCoordinator == null) {
      failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE',
          "Could not resolve current Sonos coordinator ${topology.coordinatorId} to transfer coordination to ${coordinatorId}")
      return
    }
    List<String> transferPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
    Map<String, Map> finalTopologyExpectation = [:]
    transferPlayerIds.each { String playerId ->
      finalTopologyExpectation[playerId] = [coordinatorId: coordinatorId, memberIds: transferPlayerIds]
    }
    operation.coordinatorTransferStarted = true
    operation.coordinatorTransferGroupCreated = false
    operation.coordinatorTransferPlayerIds = transferPlayerIds
    operation.coordinatorTransferPreviousCoordinatorId = topology.coordinatorId as String
    operation.lastCommand = 'transferCoordinator'
    operation.topologyRequestId = UUID.randomUUID().toString()
    logInfo("Moving additive group coordination from ${topology.coordinatorId} to configured coordinator ${coordinatorId}")
    logDebug("Dispatching additive coordinator transfer ${operation.topologyRequestId} for group operation ${operation.operationId}")
    prepareExpectedTopologyForMutation(previousCoordinator, [], [coordinatorId], finalTopologyExpectation)
      previousCoordinator.playerModifyGroupMembers([], [coordinatorId], finalTopologyExpectation)
    operation.topologyMutationSent = true
  } else {
    List<String> coordinatorObservedPlayerIds = getAdditiveObservedPlayerIds(operation, topology)
    if(topology?.consistent == true && topology.coordinatorId == coordinatorId) {
      coordinatorObservedPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
    }
    if(topology?.fresh == true) { operation.topologyMutationSent = false }
    List<String> followersToAdd = requiredPlayerIds.findAll { String playerId ->
      playerId != coordinatorId && !coordinatorObservedPlayerIds.contains(playerId)
    }
    operation.lastCommand = 'addMissingPlayers'
    if(followersToAdd) {
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Sending additive group mutation for ${operation.operationId}: configuredCoordinator=${coordinatorId}, add=${followersToAdd}")
      logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
      prepareExpectedTopologyForMutation(coordinator, followersToAdd, [])
      coordinator.playerModifyGroupMembers(followersToAdd, [])
      operation.topologyMutationSent = true
    } else {
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology check ${operation.topologyRequestId} for group operation ${operation.operationId}")
      coordinator.playerGetGroupsFull()
    }
  }

  operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
  operation.lastCommandAt = now()
  saveActiveGroupOperation(operation)
  runIn(1, 'refreshGroupOperationTopology', [overwrite: true, data: [operationId: operation.operationId]])
}

void sendGroupTopologyCommand(Map operation, Map topology) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  if(operation.evictUnlistedOnly == true) {
    Boolean topologyMutationSentNow = false
    String configuredCoordinatorId = operation.configuredCoordinatorId as String
    ChildDeviceWrapper configuredCoordinator = configuredCoordinatorId
        ? rinconMap[configuredCoordinatorId]
        : null
    if(!configuredCoordinatorId || configuredCoordinator == null) {
      Map configuredGroup = getConfiguredGroupDefinitionByDni(operation.groupDni as String)
      failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE',
          "Could not resolve the configured coordinator ${configuredCoordinatorId ?: '(none)'} for unlisted-player eviction (group=${extractGroupNameFromDni(operation.groupDni as String) ?: '(unknown)'}, savedDefinitionFields=${configuredGroup.keySet()})")
      return
    }

    Boolean topologyCanBeChanged = topology?.fresh == true &&
        topology?.consistent == true &&
        topology.coordinatorId == configuredCoordinatorId &&
        topology.groupId && topology.playerIds
    if(!topologyCanBeChanged) {
      // Eviction is deliberately removal-only. Never create/rebuild a group
      // from an incomplete snapshot because that can transfer coordination
      // away from the configured AirPlay/source player.
      operation.lastCommand = 'refreshGroupTopology'
      logDebug("Waiting for a fresh topology with configured coordinator ${configuredCoordinatorId} before evicting unlisted players")
      configuredCoordinator.playerGetGroupsFull()
    } else {
      List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation.requiredPlayerIds)
      List<String> observedPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
      List<String> extras = observedPlayerIds.findAll { String id -> !requiredPlayerIds.contains(id) }
      if(extras) {
        operation.lastCommand = 'removeExtraPlayers'
        operation.topologyRequestId = UUID.randomUUID().toString()
        logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
        logInfo("Evicting unlisted players ${extras} from group ${topology.groupId} using configured coordinator ${configuredCoordinatorId}")
        prepareExpectedTopologyForMutation(configuredCoordinator, [], extras)
        configuredCoordinator.playerModifyGroupMembers([], extras)
        topologyMutationSentNow = true
      } else {
        operation.lastCommand = 'refreshGroupTopology'
        configuredCoordinator.playerGetGroupsFull()
      }
    }
    operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
    operation.lastCommandAt = now()
    saveActiveGroupOperation(operation)
    if(topologyMutationSentNow) {
      // A mutation changes the group ID/membership, so start a new observation
      // window for its result. Read-only getGroups requests must not reset the
      // current window or their responses can be discarded as stale.
      runIn(1, 'refreshGroupOperationTopology', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
    }
    return
  }

  String coordinatorId = operation.resolvedCoordinatorId as String
  ChildDeviceWrapper coordinator = coordinatorId ? rinconMap[coordinatorId] : null
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation.requiredPlayerIds)
  List<String> observedPlayerIds = normalizeGroupPlayerIds(topology?.playerIds)
  if(operation.groupingMode == GROUPING_MODE_EXPLICIT) {
    coordinatorId = operation.desiredCoordinatorId as String ?: coordinatorId
    coordinator = coordinatorId ? rinconMap[coordinatorId] : coordinator
    if(coordinator == null) {
      failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE', "Could not resolve explicit coordinator ${coordinatorId}")
      return
    }
    if(operation.coordinatorTransferStarted == true) {
      if(topology?.fresh == true && topology.coordinatorId == coordinatorId) {
        // The configured coordinator has left the old group. It can now
        // create the requested group without Sonos retaining the old leader.
        operation.coordinatorTransferStarted = false
      } else {
        // Wait for Sonos to publish the split before issuing the second step.
        // Repeating createGroup against the still-joined players preserves the
        // existing coordinator and cannot complete a coordinator transfer.
        operation.lastCommand = 'refreshGroupTopology'
        operation.lastCommandAt = now()
        operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
        saveActiveGroupOperation(operation)
        coordinator.playerGetGroupsFull()
        ChildDeviceWrapper previousCoordinator = topology?.coordinatorId
            ? rinconMap[topology.coordinatorId as String]
            : null
        if(previousCoordinator != null && previousCoordinator != coordinator) {
          previousCoordinator.playerGetGroupsFull()
        }
        return
      }
    }
    // Sonos keeps the coordinator of an already grouped subset when a new
    // group is created around it. Break any conflicting subgroup made only
    // from target players first, so createGroup can elect the configured
    // coordinator instead of cycling between joining the subgroup and pulling
    // the configured coordinator back out of it.
    List<Map> topologyObservations = topology?.observations instanceof List
        ? (List<Map>)topology.observations
        : []
    List<String> requiredPlayerSet = normalizeGroupPlayerIds(requiredPlayerIds)
    Map<String, Map> observedTargetGroups = [:]
    topologyObservations.findAll { Map item ->
      item.fresh == true && item.groupId && item.coordinatorId && item.playerIds
    }.each { Map item ->
      List<String> memberIds = normalizeGroupPlayerIds(item.playerIds)
      if(memberIds.size() > 1 && requiredPlayerSet.containsAll(memberIds)) {
        String groupKey = item.groupId.toString()
        if(!observedTargetGroups.containsKey(groupKey)) {
          observedTargetGroups[groupKey] = [
            groupId: groupKey,
            coordinatorId: item.coordinatorId.toString(),
            memberIds: memberIds
          ]
        }
      }
    }
    Map conflictingTargetGroup = observedTargetGroups.values().find { Map item ->
      item.memberIds != requiredPlayerSet || item.coordinatorId != coordinatorId
    }
    if(conflictingTargetGroup != null) {
      String currentCoordinatorId = conflictingTargetGroup.coordinatorId as String
      List<String> followersToSplit = normalizeGroupPlayerIds(conflictingTargetGroup.memberIds)
          .findAll { String playerId -> playerId != currentCoordinatorId }
      ChildDeviceWrapper currentCoordinator = rinconMap[currentCoordinatorId]
      if(currentCoordinator != null && followersToSplit) {
        operation.lastCommand = 'splitConflictingTargetGroup'
        operation.topologyRequestId = UUID.randomUUID().toString()
        logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
        logInfo("Splitting existing target subgroup ${conflictingTargetGroup.memberIds} from coordinator ${currentCoordinatorId} before creating the exact group with ${coordinatorId}")
        prepareExpectedTopologyForMutation(currentCoordinator, [], followersToSplit)
        currentCoordinator.playerModifyGroupMembers([], followersToSplit)
        operation.topologyMutationSent = true
        operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
        operation.lastCommandAt = now()
        saveActiveGroupOperation(operation)
        runIn(1, 'refreshGroupOperationTopology', [
          overwrite: true,
          data: [operationId: operation.operationId]
        ])
        return
      }
    }
    Boolean coordinatorTransferNeeded = topology?.fresh == true &&
        topology?.consistent == true &&
        topology.coordinatorId != coordinatorId &&
        observedPlayerIds.contains(coordinatorId) &&
        new HashSet<String>(observedPlayerIds).containsAll(new HashSet<String>(requiredPlayerIds))
    if(coordinatorTransferNeeded) {
      ChildDeviceWrapper previousCoordinator = rinconMap[topology.coordinatorId as String]
      if(previousCoordinator == null) {
        failGroupOperation(operation, 'COORDINATOR_UNAVAILABLE',
            "Could not resolve current Sonos coordinator ${topology.coordinatorId} to transfer coordination to ${coordinatorId}")
        return
      }
      operation.lastCommand = 'transferCoordinator'
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
      logInfo("Moving group coordination from ${topology.coordinatorId} to configured coordinator ${coordinatorId}")
      Map<String, Map> finalTopologyExpectation = [:]
      requiredPlayerIds.each { String playerId ->
        finalTopologyExpectation[playerId] = [
          coordinatorId: coordinatorId,
          memberIds: requiredPlayerIds
        ]
      }
      prepareExpectedTopologyForMutation(previousCoordinator, [], [coordinatorId], finalTopologyExpectation)
      previousCoordinator.playerModifyGroupMembers([], [coordinatorId], finalTopologyExpectation)
      operation.coordinatorTransferStarted = true
      operation.topologyMutationSent = true
      operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
      operation.lastCommandAt = now()
      saveActiveGroupOperation(operation)
      runIn(1, 'refreshGroupOperationTopology', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
      return
    }
    Boolean canEvictExtras = topology?.consistent == true &&
        topology.coordinatorId == coordinatorId &&
        new HashSet<String>(observedPlayerIds).containsAll(new HashSet<String>(requiredPlayerIds))
    if(canEvictExtras && observedPlayerIds.size() > requiredPlayerIds.size()) {
      List<String> extras = observedPlayerIds.findAll { String id -> !requiredPlayerIds.contains(id) }
      operation.lastCommand = 'removeExtraPlayers'
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
      prepareExpectedTopologyForMutation(coordinator, [], extras)
      coordinator.playerModifyGroupMembers([], extras)
      operation.topologyMutationSent = true
    } else {
      operation.lastCommand = 'createExactGroup'
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
      // Sonos keeps the coordinator of the largest existing subset group.
      // Announcement topology transactions split conflicting source groups
      // first, so the existing groups are singletons and this ordering makes
      // the configured coordinator win Sonos' equal-size tie-break.
      List<String> playerIdsInCoordinatorOrder = [coordinatorId] +
          requiredPlayerIds.findAll { String playerId -> playerId != coordinatorId }
      prepareExpectedTopologyForCreateGroup(coordinator, playerIdsInCoordinatorOrder)
      coordinator.playerCreateGroup(playerIdsInCoordinatorOrder)
      operation.topologyMutationSent = true
    }
  } else if(operation.groupingMode == GROUPING_MODE_ADDITIVE) {
    sendAdditiveGroupTopologyCommand(operation, topology, rinconMap, requiredPlayerIds)
    return
  } else {
    if(operation.groupingMode == GROUPING_MODE_CURRENT && topology?.fresh != true) {
      // CURRENT is explicitly read-only. It may wait for a fresh snapshot,
      // but it must never mutate membership from cached data.
      operation.lastCommand = 'refreshGroupTopology'
      operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
      operation.lastCommandAt = now()
      saveActiveGroupOperation(operation)
      refreshGroupOperationTopology([operationId: operation.operationId])
      return
    }
    if(topology?.fresh != true && operation.topologyMutationSent == true) {
      // Do not send the same join repeatedly while Sonos is still applying the
      // previous mutation and its topology response is delayed.
      operation.lastCommand = 'refreshGroupTopology'
      operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
      operation.lastCommandAt = now()
      saveActiveGroupOperation(operation)
      refreshGroupOperationTopology([operationId: operation.operationId])
      return
    }
    // A fresh, consistent snapshot is preferred. If a speaker's WebSocket
    // response is delayed or unavailable, use the selected coordinator's
    // cached membership instead of refusing to send the join command. Using
    // only that coordinator's view is important: Arc and Kitchen can each
    // report a valid standalone group, and unioning those views would hide the
    // missing join.
    List<String> coordinatorObservedPlayerIds = getAdditiveObservedPlayerIds(operation, topology)
    if(topology?.consistent == true && topology.coordinatorId) {
      operation.resolvedCoordinatorId = topology.coordinatorId as String
      coordinatorId = operation.resolvedCoordinatorId as String
      coordinator = rinconMap[coordinatorId]
      coordinatorObservedPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
      if(operation.favoriteId) {
        registerGroupOperationOnPlayers(operation, [coordinatorId])
      }
    }
    if(topology?.fresh == true) {
      operation.topologyMutationSent = false
    }
    if(coordinator == null) {
      logWarn("Could not resolve additive coordinator for group operation ${operation.operationId}")
      return
    }
    List<String> followersToAdd = requiredPlayerIds.findAll { String id ->
      id != coordinatorId && !coordinatorObservedPlayerIds.contains(id)
    }
    operation.lastCommand = 'addMissingPlayers'
    logDebug("Sending additive group mutation for ${operation.operationId}: coordinator=${coordinatorId}, group=${coordinator.getDataValue('groupId')}, add=${followersToAdd}")
    if(followersToAdd) {
      operation.topologyMutationSent = true
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
      prepareExpectedTopologyForMutation(coordinator, followersToAdd, [])
      coordinator.playerModifyGroupMembers(followersToAdd, [])
    } else {
      // The topology may be mid-transition or the player cache may be stale.
      // Refresh group data without changing membership; extras are preserved.
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology check ${operation.topologyRequestId} for group operation ${operation.operationId}")
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

void refreshGroupOperationTopology(Map data = [:], Boolean resetStability = true) {
  Map operation = getActiveGroupOperation()
  String callbackOperationId = data?.operationId as String
  if(operation == null || (callbackOperationId && callbackOperationId != operation.operationId) || operation.favoriteId && operation.phase != 'ENSURE_GROUP') {
    return
  }
  operation.topologyRefreshAt = now()
  operation.topologyCheckRequestId = UUID.randomUUID().toString()
  logTrace("Starting topology observation ${operation.topologyCheckRequestId} for operation ${operation.operationId}")
  operation.topologyObservations = [:]
  if(resetStability == true) {
    operation.stableObservations = 0
    operation.topologyFingerprint = null
    operation.topologyLastObservedAt = 0L
  }
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
  operation.favoriteTopologyRequestId = UUID.randomUUID().toString()
  logTrace("Starting post-Favorite topology observation ${operation.favoriteTopologyRequestId} for operation ${operation.operationId}")
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
  registerGroupOperationOnPlayers(operation, [coordinatorId])
  operation.phase = 'WAIT_FOR_FAVORITE'
  operation.status = 'LOADING_FAVORITE'
  operation.favoriteAttempt = ((operation.favoriteAttempt ?: 0) as Integer) + 1
  operation.loadAcknowledged = false
  operation.favoriteCommandDelivered = false
  operation.playCommandDelivered = false
  operation.loadErrorCode = null
  operation.loadErrorReason = null
  operation.playbackState = null
  operation.favoriteCommandAt = now()
  operation.favoriteAttemptId = null
  operation.favoriteRequestId = null
  operation.playRequestId = null
  operation.favoriteExpectedGroupId = null
  operation.favoriteExpectedCoordinatorId = null
  operation.favoriteTopologyRefreshAt = 0L
  operation.favoriteTopologyObservations = [:]
  operation.favoriteTopologyAttempt = 0
  operation.favoriteTopologyStableObservations = 0
  operation.favoriteTopologyLastObservedAt = 0L
  operation.favoriteTopologyFingerprint = null
  operation.favoriteTopologyVerified = false
  operation.favoriteAttemptId = UUID.randomUUID().toString()
  operation.favoriteRequestId = UUID.randomUUID().toString()
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
        operation.favoriteAttemptId as String,
        operation.favoriteRequestId as String
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
        topology.groupId as String,
        operation.favoriteAttemptId as String
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
  operation.favoriteCommandDelivered = false
  operation.playCommandDelivered = false
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
  operation.topologyMutationSent = false
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
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)

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
  if(operation.evictUnlistedOnly == true && !verifyingFavoriteTopology) {
    Map coordinatorTopology = readEvictionCoordinatorTopology(operation)
    if(coordinatorTopology != null) {
      // The configured coordinator's groupPlayerIds data value is updated by
      // its live group event stream and is authoritative for removal-only
      // eviction. A follower event is not required before acting.
      topology = coordinatorTopology
    }
  }
  operation.observedGroupId = topology.groupId
  operation.observedCoordinatorId = topology.coordinatorId
  operation.observedPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
  operation.lastTopologyObservations = topology.observations ?: []
  if(!verifyingFavoriteTopology && operation.groupingMode == GROUPING_MODE_CURRENT && topology.coordinatorId) {
    operation.resolvedCoordinatorId = topology.coordinatorId as String
    if(operation.favoriteId) {
      registerGroupOperationOnPlayers(operation, [topology.coordinatorId as String])
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
    if(topology.fresh == true) {
      operation.stableObservations = 0
      operation.topologyFingerprint = null
      operation.topologyLastObservedAt = 0L
      if(operation.groupingMode == GROUPING_MODE_EXPLICIT) {
        // A fresh coordinator report disproves the requested exact topology,
        // so a corrective createGroup command is now safe to consider.
        operation.topologyMutationSent = false
      }
    }
    if(operation.groupingMode == GROUPING_MODE_CURRENT && topology.fresh == true) {
      failGroupOperation(operation, 'CURRENT_TOPOLOGY_NOT_SATISFIED', 'The current group does not contain all configured players; no topology mutation was requested.')
      return
    }
    if(operation.groupingMode == GROUPING_MODE_EXPLICIT && topology.fresh != true &&
        (operation.topologyMutationSent == true || ((operation.stableObservations ?: 0) as Integer) > 0)) {
      Long commandAt = operation.lastCommandAt as Long ?: 0L
      Boolean waitingForTopologyResponse = commandAt > 0L &&
          now() < commandAt + GROUP_OPERATION_TOPOLOGY_COMMAND_WAIT_SECONDS * 1000L
      if(!waitingForTopologyResponse) {
        if(((operation.topologyAttempt ?: 0) as Integer) >= GROUP_OPERATION_MAX_TOPOLOGY_ATTEMPTS) {
          failGroupOperation(operation, 'TOPOLOGY_NOT_CONFIRMED',
              "The group mutation was sent, but no fresh report from configured coordinator ${operation.desiredCoordinatorId} confirmed the topology: ${summarizeGroupOperationTopology(topology)}")
          return
        }
        operation.lastCommand = 'refreshGroupTopology'
        operation.lastCommandAt = now()
        operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
        saveActiveGroupOperation(operation)
        refreshGroupOperationTopology([operationId: operation.operationId], false)
      }
      saveActiveGroupOperation(operation)
      publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
      runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupFavoriteOperation', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
      return
    }
    operation.phase = 'ENSURE_GROUP'
    operation.status = 'ENSURING_TOPOLOGY'
    Long refreshAt = operation.topologyRefreshAt as Long ?: 0L
    Long commandAt = operation.lastCommandAt as Long ?: 0L
    Long waitFrom = commandAt > 0L ? commandAt : refreshAt
    Boolean topologyMutationRecentlySent = commandAt > 0L &&
      operation.lastCommand in ['removeExtraPlayers', 'createExactGroup', 'addMissingPlayers',
        'transferCoordinator', 'createAdditiveGroupAfterTransfer', 'splitConflictingTargetGroup'] &&
      now() < commandAt + GROUP_OPERATION_TOPOLOGY_COMMAND_WAIT_SECONDS * 1000L
    Boolean waitingForFreshTopology = topology.fresh != true && waitFrom > 0L &&
      now() < waitFrom + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L
    if(!waitingForFreshTopology && !topologyMutationRecentlySent) {
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

  if(operation.groupingMode == GROUPING_MODE_EXPLICIT) {
    operation.topologyMutationSent = false
  }
  updateGroupTopologyStability(operation, topology)
  if(((operation.stableObservations ?: 0) as Integer) < GROUP_OPERATION_STABILITY_OBSERVATIONS) {
    saveActiveGroupOperation(operation)
    refreshGroupOperationTopology([operationId: operation.operationId], false)
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
        operation.playRequestId = UUID.randomUUID().toString()
        saveActiveGroupOperation(operation)
        try { coordinator.playerPlayForGroupOperation(operation.operationId as String,
          operation.favoriteAttemptId as String, operation.playRequestId as String) } catch(Exception e) {
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
        operation.playRequestId = UUID.randomUUID().toString()
        saveActiveGroupOperation(operation)
        try { coordinator.playerPlayForGroupOperation(operation.operationId as String,
          operation.favoriteAttemptId as String, operation.playRequestId as String) } catch(Exception e) {
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
  String rawEvent = stripHiddenBridgeValue(event?.value?.toString())
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
  if(eventName == 'registered' && isRequiredPlayer && eventPlayerId) {
    Map expectedRegistrationIds = operation.registrationRequestIds instanceof Map
        ? (Map)operation.registrationRequestIds
        : [:]
    String expectedRegistrationId = expectedRegistrationIds[eventPlayerId]?.toString()
    if(!expectedRegistrationId || payload.requestId?.toString() == expectedRegistrationId) {
      List<String> eventRegisteredPlayers = normalizeGroupPlayerIds(operation.registrationEventPlayerIds)
      eventRegisteredPlayers.add(eventPlayerId)
      operation.registrationEventPlayerIds = eventRegisteredPlayers.unique().sort()
      logTrace("Player ${eventPlayerId} acknowledged registration request ${payload.requestId ?: '(legacy)'} for operation ${operation.operationId}")
    }
  }
  switch(eventName) {
    case 'loadStarted':
      if(isExpectedAttempt && isCoordinatorEvent && isAfterFavoriteCommand &&
          payload.requestId?.toString() == operation.favoriteRequestId?.toString()) {
        operation.favoriteCommandDelivered = true
        logDebug("Favorite load request ${operation.favoriteRequestId} for operation ${operation.operationId} was handed to player ${eventPlayerId}")
      }
      break
    case 'playStarted':
      if(isExpectedAttempt && isCoordinatorEvent && payload.requestId?.toString() == operation.playRequestId?.toString()) {
        operation.playCommandDelivered = true
        logDebug("Play request ${operation.playRequestId} for operation ${operation.operationId} was handed to player ${eventPlayerId}")
      }
      break
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
    favoriteRequestId: operation.favoriteRequestId,
    favoriteCommandDelivered: operation.favoriteCommandDelivered == true,
    playRequestId: operation.playRequestId,
    playCommandDelivered: operation.playCommandDelivered == true,
    topologyRequestId: operation.topologyRequestId,
    topologyCheckRequestId: operation.topologyCheckRequestId,
    favoriteTopologyRequestId: operation.favoriteTopologyRequestId,
    registrationRequestIds: operation.registrationRequestIds ?: [:],
    registrationEventPlayerIds: operation.registrationEventPlayerIds ?: [],
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

void clearRegisteredGroupOperations(Map operation) {
  if(operation == null) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  normalizeGroupPlayerIds(operation.registeredPlayerIds).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try { player.clearGroupFavoriteOperation(operation.operationId as String) }
      catch(Exception e) { logWarn("Could not clear group operation on ${playerId}: ${e.message}") }
    }
  }
}

void finishGroupOperation(Map operation, String status, String errorCode = null, String detail = null) {
  if(operation == null) { return }
  operation.phase = 'COMPLETE'
  operation.completedAt = now()
  publishGroupOperationStatus(operation, status, errorCode, detail)
  clearRegisteredGroupOperations(operation)
  if(status == 'SUCCEEDED' && !operation.favoriteId && !operation.announcementTransactionId) {
    storeGroupingContext(operation)
  }
  state.remove(GROUP_OPERATION_STATE_KEY)
  if(operation.announcementTransactionId) {
    handleAnnouncementTopologyOperationFinished(operation, status)
  }
  if(state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List) {
    runIn(1, 'drainDeferredGroupOperationCommands', [overwrite: true])
  }
  if(state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] instanceof List) {
    runIn(1, 'drainDeferredGroupPlayerCommands', [overwrite: true])
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

void deferGroupPlayerCommand(Map request) {
  if(!(request instanceof Map)) { return }
  List<Map> deferred = state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_PLAYER_COMMANDS_STATE_KEY]
      : []
  String command = request.command as String
  String playerDni = request.playerDni as String
  if(isCoalescibleTransportStateCommand(command)) {
    Integer replaced = deferred.count { Map pending ->
      pending.playerDni?.toString() == playerDni && isCoalescibleTransportStateCommand(pending.command as String)
    }
    deferred = deferred.findAll { Map pending ->
      pending.playerDni?.toString() != playerDni || !isCoalescibleTransportStateCommand(pending.command as String)
    }
    if(replaced > 0) {
      logDebug("Deferred player transport command ${command} replaced ${replaced} queued state command(s) for ${playerDni}")
    }
  }
  deferred.add(request)
  state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] = deferred
  logInfo("Deferring player command ${request.command} from ${request.playerDni} until the active group operation completes")
}

Boolean isCoalescibleTransportStateCommand(String command) {
  return command in ['play', 'pause', 'stop']
}

void drainDeferredGroupPlayerCommands() {
  if(getActiveGroupOperation() != null) { return }
  List<Map> deferred = state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_PLAYER_COMMANDS_STATE_KEY]
      : []
  state.remove(DEFERRED_PLAYER_COMMANDS_STATE_KEY)
  if(deferred.isEmpty()) { return }

  Map nextRequest = deferred.remove(0)
  if(!deferred.isEmpty()) { state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] = deferred }
  processPlayerCommandRequest([request: nextRequest])
  if(state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] instanceof List && !getActiveGroupOperation()) {
    runIn(1, 'drainDeferredGroupPlayerCommands', [overwrite: true])
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

ChildDeviceWrapper resolveLiveGroupCoordinator(ChildDeviceWrapper anchor,
    Map<String, ChildDeviceWrapper> rinconMap) {
  if(anchor == null) { return null }
  String liveCoordinatorId = anchor.getDataValue('groupCoordinatorId')?.toString()
  ChildDeviceWrapper liveCoordinator = liveCoordinatorId ? rinconMap[liveCoordinatorId] : null
  return liveCoordinator ?: anchor
}

/**
 * Receive a deferred player-to-parent command. Player drivers use this bridge
 * when a command is addressed to a follower; the event is emitted after the
 * follower method returns so the player never waits on the parent app's child
 * lookup semaphore.
 */
void playerCommandRequestHandler(Event event) {
  String rawRequest = stripHiddenBridgeValue(event?.value?.toString())
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

  enqueuePlayerCommandRequest(request)
}

/** Remove the hidden current-state presentation wrapper before parsing a
 * player bridge payload. The wrapper keeps internal event data off the device
 * page while remaining available to this parent app subscription. */
String stripHiddenBridgeValue(String value) {
  if(!value) { return value }
  return value.replaceAll('(?is)<span[^>]*display\\s*:\\s*none[^>]*>', '')
      .replaceAll('(?is)</span\\s*>', '')
}

void enqueuePlayerCommandRequest(Map request) {
  String playerDni = request?.playerDni as String
  String command = request?.command as String
  ChildDeviceWrapper source = playerDni ? getChildDevice(playerDni) : null
  if(!playerDni || !command || source == null) {
    logWarn("Ignoring player command request with invalid target or command: ${JsonOutput.toJson(request)}")
    return
  }
  String requestId = request.requestId as String
  Map<String, List<String>> received = state[PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY] instanceof Map
      ? (Map<String, List<String>>)state[PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY]
      : [:]
  List<String> receivedForPlayer = received[playerDni] instanceof List
      ? (List<String>)received[playerDni]
      : []
  if(requestId && receivedForPlayer.contains(requestId)) {
    acknowledgePlayerCommandRequest(source, requestId)
    return
  }

  Map<String, List<Map>> pending = state[PLAYER_COMMAND_QUEUE_STATE_KEY] instanceof Map
      ? (Map<String, List<Map>>)state[PLAYER_COMMAND_QUEUE_STATE_KEY]
      : [:]
  List<Map> playerRequests = pending[playerDni] instanceof List
      ? (List<Map>)pending[playerDni]
      : []
  playerRequests.add(request)
  pending[playerDni] = playerRequests
  state[PLAYER_COMMAND_QUEUE_STATE_KEY] = pending
  if(requestId) {
    receivedForPlayer.add(requestId)
    if(receivedForPlayer.size() > 200) { receivedForPlayer = receivedForPlayer.takeRight(200) }
    received[playerDni] = receivedForPlayer
    state[PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY] = received
    acknowledgePlayerCommandRequest(source, requestId)
  }
  runIn(1, 'drainPlayerCommandRequests', [overwrite: true])
}

void acknowledgePlayerCommandRequest(ChildDeviceWrapper source, String requestId) {
  source.acknowledgeParentCommand(requestId)
  logTrace("Acknowledged receipt of parent command ${requestId} for ${source.getDeviceNetworkId()}")
}

void drainPlayerCommandRequests() {
  Map<String, List<Map>> pending = state[PLAYER_COMMAND_QUEUE_STATE_KEY] instanceof Map
      ? (Map<String, List<Map>>)state[PLAYER_COMMAND_QUEUE_STATE_KEY]
      : [:]
  state.remove(PLAYER_COMMAND_QUEUE_STATE_KEY)
  pending.each { String playerDni, List<Map> requests ->
    List<Map> ordered = (requests ?: []).sort { Map request -> playerCommandRequestSequence(request) }
    ordered.each { Map request -> processPlayerCommandRequest([request: request]) }
  }
}

Integer playerCommandRequestSequence(Map request) {
  if(request?.sequence != null) {
    try {
      return request.sequence.toString().toInteger()
    } catch(Exception ignored) {
      return Integer.MAX_VALUE
    }
  }
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
  if(command == 'ungroupPlayer') {
    String playerId = source.getDataValue('id') as String
    List<String> oldMembers = normalizeGroupPlayerIds(source.getDataValue('groupPlayerIds'))
    Map<String, Map> expected = [:]
    oldMembers.each { String memberId -> expected[memberId] = [coordinatorId: memberId, memberIds: [memberId]] }
    if(playerId && !expected.containsKey(playerId)) { expected[playerId] = [coordinatorId: playerId, memberIds: [playerId]] }
    setExpectedTopologyForPlayers(expected, UUID.randomUUID().toString(),
        (now() as Long) + TOPOLOGY_CHANGE_SUPPRESSION_SECONDS * 1000L)
    source.playerCreateNewGroup()
    return
  }
  Map activeOperation = getActiveGroupOperation()
  String sourcePlayerId = source.getDataValue('id') as String
  if(activeOperation && !activeOperation.favoriteId && activeOperation.phase != 'COMPLETE' &&
      normalizeGroupPlayerIds(activeOperation.requiredPlayerIds).contains(sourcePlayerId) &&
      isPlayerPlaybackCommandThatWaitsForGroupOperation(command)) {
    logTrace("Waiting for topology change to complete before processing player command ${command} from ${playerDni}")
    deferGroupPlayerCommand(request)
    return
  }

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
      case 'play':
        coordinator.playerPlay()
        break
      case 'pause':
        coordinator.clearFavoriteRetryState()
        coordinator.clearPlaylistRetryState()
        coordinator.cancelPendingAmazonMusicAutoPlay()
        coordinator.playerPause()
        break
      case 'stop':
        coordinator.clearFavoriteRetryState()
        coordinator.clearPlaylistRetryState()
        coordinator.cancelPendingAmazonMusicAutoPlay()
        coordinator.playerStop()
        break
      case 'nextTrack':
        coordinator.playerSkipToNextTrack()
        break
      case 'previousTrack':
        coordinator.playerSkipToPreviousTrack()
        break
      case 'setTrack':
        coordinator.setTrack(args.size() > 0 ? args[0] as String : null)
        break
      case 'selectLineIn':
        String sourceRincon = resolveLineInSourceRincon(coordinator, args.size() > 0 ? args[0] as String : null)
        if(sourceRincon) { coordinator.selectLineInRincon(sourceRincon) }
        break
      case 'selectTV':
        coordinator.selectTV()
        break
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
      case 'addPlayerToGroup':
        addPlayerToCoordinatorGroup(coordinator, args.size() > 0 ? args[0]?.toString() : null)
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
      case 'setRepeatMode':
        coordinator.setRepeatMode(args.size() > 0 ? args[0] as String : null)
        break
      case 'repeatOne':
        coordinator.repeatOne()
        break
      case 'repeatAll':
        coordinator.repeatAll()
        break
      case 'repeatNone':
        coordinator.repeatNone()
        break
      case 'setShuffle':
        coordinator.setShuffle(args.size() > 0 ? args[0] as String : null)
        break
      case 'shuffleOn':
        coordinator.shuffleOn()
        break
      case 'shuffleOff':
        coordinator.shuffleOff()
        break
      case 'setCrossfade':
        coordinator.setCrossfade(args.size() > 0 ? args[0] as String : null)
        break
      case 'enableCrossfade':
        coordinator.enableCrossfade()
        break
      case 'disableCrossfade':
        coordinator.disableCrossfade()
        break
      case 'playTrack':
        coordinator.playTrack(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null)
        )
        break
      case 'playHighPriorityTrack':
        source.playHighPriorityTrack(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null)
        )
        break
      case 'loadAudioClip':
        source.playerLoadAudioClip(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null),
          args.size() > 2 ? (args[2] as Integer) : 0,
          args.size() > 3 ? args[3] as String : null
        )
        break
      case 'loadHighPriorityAudioClip':
        source.playerLoadAudioClipHighPriority(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null),
          args.size() > 2 ? args[2] as String : null,
          args.size() > 3 ? (args[3] as Integer) : 0
        )
        break
      case 'speak':
        source.devicePlayText(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null),
          args.size() > 2 ? args[2] as String : null
        )
        break
      case 'playTextAndRestore':
      case 'playTextAndResume':
        source.devicePlayTextWithAudioClip(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null),
          args.size() > 2 ? args[2] as String : null,
          command
        )
        break
      case 'speakNoRestore':
        source.devicePlayTextNoRestore(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null),
          args.size() > 2 ? args[2] as String : null
        )
        break
      case 'playHighPriorityTTS':
        source.playHighPriorityTTS(
          args.size() > 0 ? args[0] as String : null,
          toBigDecimal(args.size() > 1 ? args[1] : null),
          args.size() > 2 ? args[2] as String : null
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

/** True when every configured target is currently a member of the live group. */
Boolean isGroupDeviceRosterActivelyGrouped(ChildDeviceWrapper coordinator, List<String> playerIds) {
  if(!isSonosGrouped(coordinator) || !playerIds) { return false }
  String groupId = getGroupOperationChildValue(coordinator, 'groupId')?.toString()
  List<String> activePlayerIds = normalizeGroupPlayerIds(
      getGroupOperationChildValue(coordinator, 'groupPlayerIds'))
  return groupId != null && !groupId.trim().isEmpty() &&
      activePlayerIds.containsAll(normalizeGroupPlayerIds(playerIds))
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
    Boolean controlIndividually = groupCommandBoolean(args.controlUngroupedIndividually, false)
    List<ChildDeviceWrapper> feedbackPlayers = !isSonosGrouped(coordinator) && !controlIndividually ?
        [coordinator] : players
    Integer totalVolume = 0
    Integer volumeCount = 0
    Boolean allMuted = !feedbackPlayers.isEmpty()
    feedbackPlayers.each { ChildDeviceWrapper player ->
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
    // Explicit refreshes retain the legacy behavior of hydrating all playback
    // states even when the configured group is currently inactive.
    groupDevice.applyRefreshedPlaybackState(JsonOutput.toJson(attributes))
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
  Map configuredGroup = getConfiguredGroupDefinition(device)
  String coordinatorId = device.getDataValue('groupCoordinatorId') as String ?: configuredGroup.groupCoordinatorId as String
  String playerIdsStr = device.getDataValue('playerIds') as String
  List<String> playerIds = []
  if(coordinatorId) {
    playerIds.add(coordinatorId)
  }
  if(playerIdsStr) {
    playerIds.addAll(playerIdsStr.tokenize(','))
  } else if(configuredGroup.playerIds != null) {
    playerIds.addAll(normalizeGroupPlayerIds(configuredGroup.playerIds))
  }
  return playerIds
}

/**
 * Recover the persisted definition for a group device when an older app or
 * device migration did not copy its coordinator/player data values. This is
 * intentionally limited to the exact child-device DNI; it never infers a
 * coordinator from live topology.
 */
Map getConfiguredGroupDefinition(DeviceWrapper groupDevice) {
  return getConfiguredGroupDefinitionByDni(groupDevice?.getDeviceNetworkId())
}

Map getConfiguredGroupDefinitionByDni(String groupDni) {
  if(!groupDni || !(state.userGroups instanceof Map)) {
    return [:]
  }
  String groupName = extractGroupNameFromDni(groupDni)
  if(groupName && state.userGroups[groupName] instanceof Map) {
    return (Map)state.userGroups[groupName]
  }
  Map definition = null
  state.userGroups.each { Object savedGroupName, Object groupValue ->
    String expectedDni = "${app.id}-SonosGroupDevice-${savedGroupName}"
    if(expectedDni == groupDni && groupValue instanceof Map) {
      definition = (Map)groupValue
    }
  }
  return definition ?: [:]
}

String extractGroupNameFromDni(String groupDni) {
  Integer markerIndex = groupDni?.indexOf(GROUP_DEVICE_DNI_MARKER) ?: -1
  if(markerIndex < 0) {
    return null
  }
  String groupName = groupDni.substring(markerIndex + GROUP_DEVICE_DNI_MARKER.length())
  return groupName?.trim() ?: null
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
    String deviceMode = (gd.getDataValue('lastGroupingMode') ?: gd.currentValue('groupingMode', true))?.toString()
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
  if(state.versionCheckJob instanceof Map) { logInfo('Version check already in progress'); return }
  logInfo('Checking versions of all installed files...')
  state.lastVersionCheck = now()
  state.versionMismatches = []
  state.installedVersions = []
  Map job = [id:UUID.randomUUID().toString(), stage:'login', appVersion:getCurrentVersion(), artifacts:[]]
  state.versionCheckJob = job
  Long cookieAge = state.hubCookieTime != null ? now() - (state.hubCookieTime as Long) : null
  if(state.hubCookie && cookieAge != null && cookieAge < HUB_COOKIE_MAX_AGE_MS) {
    job.cookie = state.hubCookie
    versionCheckRequest(job, 'appList', [uri:'http://127.0.0.1:8080', path:'/hub2/userAppTypes', headers:hubCookieHeaders(job.cookie as String), timeout:15, ignoreSSLIssues:true])
  } else {
    versionCheckRequest(job, 'login', [uri:'http://127.0.0.1:8080', path:'/login', requestContentType:'application/x-www-form-urlencoded',
      body:[username:settings.hubSecurityUsername?.toString() ?: '', password:settings.hubSecurityPassword?.toString() ?: '', submit:'Login'],
      followRedirects:false, textParser:true, timeout:15], 'POST')
  }
}

void versionCheckRequest(Map job, String stage, Map params, String method = 'GET') {
  try {
    job.stage = stage
    String requestId = UUID.randomUUID().toString()
    job.requestId = requestId
    Map data = [jobId:job.id, requestId:requestId, stage:stage]
    if(method == 'POST') { asynchttpPost('versionCheckCallback', params, data) }
    else { asynchttpGet('versionCheckCallback', params, data) }
  } catch(Exception e) {
    logWarn("Version check request ${stage} could not start: ${e.message}")
    versionCheckFinish(job)
  }
}

void versionCheckCallback(AsyncResponse response, Map data) {
  Map job = state.versionCheckJob instanceof Map ? (Map)state.versionCheckJob : null
  if(!job || job.id != data?.jobId || job.requestId != data?.requestId || job.stage != data?.stage) { return }
  String stage = data.stage.toString()
  Boolean ok = response != null && !response.hasError() && (response.status == 200 || (stage == 'login' && response.status == 302))
  try {
    if(stage == 'login') {
      Object header = getResponseHeaderIgnoreCase(response?.headers, 'Set-Cookie')
      if(header instanceof Collection) { header = ((Collection)header).find { it != null } }
      String cookie = null
      try { cookie = header?.value?.toString() } catch(Exception ignored) { cookie = null }
      if(!cookie && header != null) { cookie = header.toString() }
      if(!ok || !cookie) { logWarn('Version check could not authenticate to the hub'); versionCheckFinish(job); return }
      job.cookie = cookie.split(';')[0]
      state.hubCookie = job.cookie
      state.hubCookieTime = now()
      versionCheckRequest(job, 'appList', [uri:'http://127.0.0.1:8080', path:'/hub2/userAppTypes', headers:hubCookieHeaders(job.cookie as String), timeout:15, ignoreSSLIssues:true])
      return
    }
    if(stage == 'appList') {
      Object payload = ok ? response.getJson() : null
      Map target = payload instanceof Collection ? (Map)((Collection)payload).find { it instanceof Map && it.name == 'Sonos Advanced Controller' && it.namespace == 'dwinks' } : null
      if(!target?.id) { logWarn('Could not find app code entry; using running app version'); versionCheckManifest(job); return }
      versionCheckRequest(job, 'appCode', [uri:'http://127.0.0.1:8080', path:'/app/ajax/code', query:[id:target.id], headers:hubCookieHeaders(job.cookie as String), timeout:15, ignoreSSLIssues:true])
      return
    }
    if(stage == 'appCode') {
      String source = ok ? response.getJson()?.source?.toString() : null
      def matcher = source ? (source =~ /version:\s*['"]([^'"]+)['"]/) : null
      if(matcher && matcher.find()) { job.appVersion = matcher.group(1) }
      versionCheckManifest(job)
      return
    }
    if(stage == 'manifest' || stage == 'manifestFallback') {
      Map manifest = ok && response.getJson() instanceof Map ? (Map)response.getJson() : null
      if(!manifest && stage == 'manifest') {
        logDebug('Version-specific manifest unavailable; trying main branch')
        versionCheckRequest(job, 'manifestFallback', [uri:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json', contentType:'application/json', timeout:15])
        return
      }
      if(!manifest) { logWarn('Failed to retrieve package manifest for version checking'); versionCheckFinish(job); return }
      job.manifest = manifest
      job.artifacts = []
      ((Collection)(manifest.drivers ?: [])).each { Object item -> if(item instanceof Map) { Map d = new LinkedHashMap((Map)item); d.type='driver'; d.namespace=d.namespace ?: 'dwinks'; d.expectedVersion=d.version ?: job.appVersion; job.artifacts << d } }
      Boolean canCheckLibraries = settings.libraryIdSMAPI && settings.libraryIdUtilities
      ((Collection)(manifest.files ?: [])).each { Object item ->
        if(!(item instanceof Map)) { return }
        Map f = (Map)item
        String fileName = f.name?.toString() ?: ''
        Integer libraryId = fileName.contains('SMAPILibrary') ? (settings.libraryIdSMAPI as Integer) : (fileName.contains('UtilitiesAndLoggingLibrary') ? (settings.libraryIdUtilities as Integer) : null)
        String expectedName = fileName.contains('SMAPILibrary') ? 'SMAPILibrary' : (fileName.contains('UtilitiesAndLoggingLibrary') ? 'UtilitiesAndLoggingLibrary' : '')
        if(canCheckLibraries && libraryId && expectedName) { job.artifacts << [type:'library', name:expectedName, fileName:fileName, namespace:'dwinks', id:libraryId, expectedVersion:job.appVersion] }
        else { state.installedVersions << [name:fileName, installedVersion:'N/A', expectedVersion:job.appVersion, status:canCheckLibraries ? "⚠ Configure ${expectedName} ID in settings" : '⚠ Configure Library IDs in settings'] }
      }
      versionCheckDriverList(job)
      return
    }
    if(stage == 'driverList') {
      Object payload = ok ? response.getJson() : null
      job.drivers = payload instanceof Map && ((Map)payload).drivers instanceof Collection ? ((Map)payload).drivers : []
      versionCheckNextArtifact(job)
      return
    }
    if(stage == 'driverCode' || stage == 'libraryCode') {
      Map artifact = (Map)job.artifact
      String source = null
      if(ok) {
        Object payload = response.getJson()
        source = payload instanceof Map ? ((Map)payload).source?.toString() : null
      }
      String installedVersion = null
      String error = null
      if(stage == 'driverCode' && source) {
        def matcher = source =~ /version:\s*['"]([^'"]+)['"]/; if(matcher.find()) { installedVersion = matcher.group(1) }
      } else if(stage == 'libraryCode' && source) {
        def nameMatch = source =~ /library\s*\([^)]*name\s*:\s*['"]([^'"]+)['"]/; def namespaceMatch = source =~ /library\s*\([^)]*namespace\s*:\s*['"]([^'"]+)['"]/; def versionMatch = source =~ /version:\s*['"]([^'"]+)['"]/
        String actualName = nameMatch.find() ? nameMatch.group(1) : null
        String actualNamespace = namespaceMatch.find() ? namespaceMatch.group(1) : null
        if(actualName != artifact.name || actualNamespace != artifact.namespace) { error = "Library ID ${artifact.id} contains ${actualName ?: 'unknown'}/${actualNamespace ?: 'unknown'}, expected ${artifact.name}/${artifact.namespace}" }
        else if(versionMatch.find()) { installedVersion = versionMatch.group(1) }
      }
      versionCheckRecordArtifact(artifact, installedVersion, error)
      versionCheckNextArtifact(job)
    }
  } catch(Exception e) { logWarn("Version check stage ${stage} failed: ${e.message}"); if(stage == 'manifest') { versionCheckRequest(job, 'manifestFallback', [uri:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json', contentType:'application/json', timeout:15]) } else { versionCheckNextArtifact(job) } }
}

void versionCheckManifest(Map job) {
  logDebug("Current app version from hub: ${job.appVersion}")
  versionCheckRequest(job, 'manifest', [uri:"https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${job.appVersion}/PackageManifests/SonosAdvancedController/packageManifest.json", contentType:'application/json', timeout:15])
}

void versionCheckDriverList(Map job) {
  versionCheckRequest(job, 'driverList', [uri:'http://127.0.0.1:8080', path:'/device/drivers', headers:hubCookieHeaders(job.cookie as String), timeout:15])
}

void versionCheckNextArtifact(Map job) {
  List artifacts = (List)(job.artifacts ?: [])
  if(artifacts.isEmpty()) { versionCheckFinish(job); return }
  Map artifact = (Map)artifacts.remove(0)
  job.artifact = artifact
  if(artifact.type == 'driver') {
    Map driver = (Map)(((Collection)(job.drivers ?: [])).find { Object entry -> entry instanceof Map && ((Map)entry).type == 'usr' && ((Map)entry).name == artifact.name && ((Map)entry).namespace == artifact.namespace } ?: [:])
    if(!driver.id) { versionCheckRecordArtifact(artifact, null, null); versionCheckNextArtifact(job); return }
    versionCheckRequest(job, 'driverCode', [uri:'http://127.0.0.1:8080', path:'/driver/ajax/code', query:[id:driver.id], headers:hubCookieHeaders(job.cookie as String), timeout:15])
  } else {
    versionCheckRequest(job, 'libraryCode', [uri:'http://127.0.0.1:8080', path:'/library/ajax/code', query:[id:artifact.id.toString()], headers:hubCookieHeaders(job.cookie as String), timeout:15])
  }
}

void versionCheckRecordArtifact(Map artifact, String installedVersion, String error) {
  String expected = artifact.expectedVersion?.toString() ?: getCurrentVersion()
  if(error) {
    state.installedVersions << [name:artifact.fileName ?: artifact.name, installedVersion:'Error', expectedVersion:expected, status:error]
  } else if(installedVersion && installedVersion != expected) {
    state.versionMismatches << [name:artifact.name, fileName:artifact.fileName, namespace:artifact.namespace, installedVersion:installedVersion, expectedVersion:expected, location:artifact.location, libraryId:artifact.id, type:artifact.type]
    state.installedVersions << [name:artifact.fileName ?: artifact.name, installedVersion:installedVersion, expectedVersion:expected, status:'Mismatch']
  } else if(installedVersion) {
    state.installedVersions << [name:artifact.fileName ?: artifact.name, installedVersion:installedVersion, expectedVersion:expected, status:'OK']
  } else {
    state.installedVersions << [name:artifact.fileName ?: artifact.name, installedVersion:'Not Installed', expectedVersion:expected, status:'Not Found']
  }
}

void versionCheckFinish(Map job) {
  if(state.versionCheckJob instanceof Map && state.versionCheckJob.id == job.id) { state.remove('versionCheckJob') }
  if(state.versionMismatches?.size() > 0) { logInfo("Found ${state.versionMismatches.size()} version mismatch(es)") }
  else { logInfo('All installed files are up to date') }
}

String getInstalledDriverVersion(String driverName, String namespace) {
  String foundVersion = null

  try {
    // Get authentication cookie
    String cookie = login()
    if(cookie == null) {
      logWarn('Failed to authenticate with hub')
      return null
    }

    // Get list of installed drivers - HPM uses /device/drivers
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: hubCookieHeaders(cookie)
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
            headers: hubCookieHeaders(cookie),
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
    if(cookie == null) {
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
    if(cookie == null) { return null }

    // Get list of installed drivers
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: hubCookieHeaders(cookie)
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
          headers: hubCookieHeaders(cookie),
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
  if(state.maintenanceJob instanceof Map) { logWarn('A maintenance operation is already in progress'); return }
  if(!state.versionMismatches || state.versionMismatches.size() == 0) {
    logInfo('No version mismatches to fix')
    return
  }
  List libraries = []
  List drivers = []
  state.versionMismatches.each { Map mismatch ->
    if(mismatch.type == 'library') {
      libraries << [id:mismatch.libraryId as Integer, name:mismatch.name, fileName:mismatch.fileName,
        location:"https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${mismatch.expectedVersion}/Libraries/${mismatch.fileName}"]
    } else if(mismatch.type == 'driver') {
      drivers << [name:mismatch.name, namespace:mismatch.namespace ?: 'dwinks', location:mismatch.location]
    }
  }
  Map job = [id:UUID.randomUUID().toString(), op:'repairVersions', libraries:libraries,
    drivers:drivers, errors:[], successCount:0]
  state.maintenanceJob = job
  job.pendingRequest = [stage:'nextLibrary']
  maintenanceAuthenticate(job)
}

void fixDriverMismatches() {
  if(state.maintenanceJob instanceof Map) { logWarn('A maintenance operation is already in progress'); return }
  List driverMismatches = state.pendingDriverMismatches ?: []
  state.remove('pendingDriverMismatches')
  if(!driverMismatches) { checkInstalledVersions(); return }
  Map job = [id:UUID.randomUUID().toString(), op:'repairVersions', drivers:driverMismatches.collect { Map item ->
    [name:item.name, namespace:item.namespace ?: 'dwinks', location:item.location]
  }, libraries:[], errors:[], successCount:0]
  state.maintenanceJob = job
  job.pendingRequest = [stage:'nextDriver']
  maintenanceAuthenticate(job)
}

void cleanupDuplicates() {
  if(state.maintenanceJob instanceof Map) { logWarn('A maintenance operation is already in progress'); return }
  logInfo("Checking for duplicate apps and drivers...")
  state.remove('hubCookie')
  Map job = [id:UUID.randomUUID().toString(), op:'cleanupDuplicates', errors:[], appsRemoved:0, driversRemoved:0,
    appDeletes:[], driverDeletes:[]]
  state.maintenanceJob = job
  state.duplicateCleanupResult = 'Checking for duplicate apps and drivers...'
  job.pendingRequest = [stage:'duplicateApps']
  maintenanceAuthenticate(job)
}

/**
 * Publish libraries to the hub using the configured library IDs.
 * Downloads the latest library files from GitHub and publishes them to the hub.
 */
void publishLibraries() {
  if(state.maintenanceJob instanceof Map) { logWarn('A maintenance operation is already in progress'); return }
  if(!settings.libraryIdSMAPI || !settings.libraryIdUtilities) {
    logWarn("Library IDs not configured. Please enter both library IDs in settings.")
    state.lastLibraryPublish = "Failed: Library IDs not configured"
    return
  }

  String appVersion = getCurrentVersion()
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

  Map job = [id:UUID.randomUUID().toString(), op:'publishLibraries', libraries:libraries,
    totalLibraries:libraries.size(), successCount:0, errors:[]]
  state.maintenanceJob = job
  state.lastLibraryPublish = 'Publishing libraries...'
  job.pendingRequest = [stage:'nextLibrary']
  maintenanceAuthenticate(job)
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
    if(cookie == null) {
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
      headers: hubCookieHeaders(cookie) + [
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
      headers: hubCookieHeaders(cookie),
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
    if(cookie == null) {
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
      headers: hubCookieHeaders(cookie) + [
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
    attemptId: UUID.randomUUID().toString(),
    attemptNumber: isRetryAttempt ? (((state[LOCAL_CONTROL_RETRY_STATE_KEY] ?: 0) as Integer) + 1) : 1,
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
  Map retryData = callbackData instanceof Map && ((Map)callbackData).get(LOCAL_CONTROL_RETRY_DATA_KEY) instanceof Map
      ? (Map)((Map)callbackData).get(LOCAL_CONTROL_RETRY_DATA_KEY) : [:]
  logDebug("Dispatching local HTTP ${retryData.method ?: 'POST'} request ${retryData.requestId ?: 'untracked'} attempt=${retryData.attemptNumber ?: 1} attemptId=${retryData.attemptId ?: 'untracked'} uri=${params.uri}")
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
  Map retryData = callbackData instanceof Map && ((Map)callbackData).get(LOCAL_CONTROL_RETRY_DATA_KEY) instanceof Map
      ? (Map)((Map)callbackData).get(LOCAL_CONTROL_RETRY_DATA_KEY) : [:]
  logDebug("Dispatching local HTTP GET request ${retryData.requestId ?: 'untracked'} attempt=${retryData.attemptNumber ?: 1} attemptId=${retryData.attemptId ?: 'untracked'} uri=${params.uri}")
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
  Map retryData = callbackData instanceof Map && ((Map)callbackData).get(LOCAL_CONTROL_RETRY_DATA_KEY) instanceof Map
      ? (Map)((Map)callbackData).get(LOCAL_CONTROL_RETRY_DATA_KEY) : [:]
  logDebug("Dispatching local HTTP POST request ${retryData.requestId ?: 'untracked'} attempt=${retryData.attemptNumber ?: 1} attemptId=${retryData.attemptId ?: 'untracked'} uri=${params.uri}")
  logTrace("sendLocalJsonAsync: ${params}")
  asynchttpPost(callbackMethod, params, callbackData)
}

void localControlCallback(AsyncResponse response, Map data) {
  Map retryRequest = data?.get(LOCAL_CONTROL_RETRY_DATA_KEY) instanceof Map ? data[LOCAL_CONTROL_RETRY_DATA_KEY] as Map : null
  Map scheduledRetryRequest = state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] instanceof Map ? state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] as Map : null
  String requestId = retryRequest?.requestId as String
  String attemptId = retryRequest?.attemptId as String
  Boolean requestMatchesScheduledRetry = requestId != null && scheduledRetryRequest?.requestId == requestId
  Boolean attemptMatchesScheduledRetry = requestMatchesScheduledRetry &&
      (!scheduledRetryRequest?.activeAttemptId || scheduledRetryRequest.activeAttemptId == attemptId)
  Integer statusCode = response?.status as Integer
  logDebug("Local HTTP callback request=${requestId ?: 'untracked'} attempt=${retryRequest?.attemptNumber ?: 1} attemptId=${attemptId ?: 'untracked'} status=${statusCode ?: 'none'} error=${response?.hasError() == true} receivedAt=${now()}")
  if(retryRequest?.retryable == true && scheduledRetryRequest != null && !attemptMatchesScheduledRetry) {
    logWarn("Ignoring stale local HTTP callback request=${requestId ?: 'unknown'} attemptId=${attemptId ?: 'unknown'}; activeRequest=${scheduledRetryRequest.requestId} activeAttempt=${scheduledRetryRequest.activeAttemptId ?: 'unknown'}")
    return
  }
  if(retryRequest?.retryAttempt == true && scheduledRetryRequest == null) {
    logWarn("Ignoring late local HTTP retry callback request=${requestId ?: 'unknown'} attemptId=${attemptId ?: 'unknown'}; no retry is active")
    return
  }
  if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
    Boolean retryScheduled = false
    if(retryRequest?.retryable == true) {
      if(scheduledRetryRequest == null || attemptMatchesScheduledRetry) {
        retryRequest.activeAttemptId = attemptId
        state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] = retryRequest
        retryScheduled = handleAsyncHttpFailureWithRetry(response, 'executeLocalControlRetry', LOCAL_CONTROL_RETRY_STATE_KEY)
      } else {
        logWarn("Skipping local control auto-retry for ${retryRequest.method} ${retryRequest?.params?.uri} because another retry is already pending")
      }
    }
    if(retryScheduled == false && (scheduledRetryRequest == null || attemptMatchesScheduledRetry)) {
      state.remove(LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY)
      resetHttpRetryCounter(LOCAL_CONTROL_RETRY_STATE_KEY)
    }
    return
  }
  if(response?.status == 200 && response && response.hasError() == false) {
    if(retryRequest?.retryable == true && (scheduledRetryRequest == null || attemptMatchesScheduledRetry)) {
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
  Map retryAttemptData = callbackData.get(LOCAL_CONTROL_RETRY_DATA_KEY) as Map
  retryRequest.activeAttemptId = retryAttemptData.attemptId
  state[LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY] = retryRequest
  logInfo("Retrying local control ${retryRequest.method} request=${retryRequest.requestId} attempt=${retryAttemptData.attemptNumber} attemptId=${retryAttemptData.attemptId} uri=${params.uri}")

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
    if(cookie == null) {
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
      headers: hubCookieHeaders(cookie),
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

  Long startedAt = state.updateCheckStartedAt instanceof Number ? (state.updateCheckStartedAt as Long) : 0L
  if(!manual && state.updateCheckRequestId && now() - startedAt < 60000L) { return }
  String requestId = UUID.randomUUID().toString()
  state.updateCheckRequestId = requestId
  state.updateCheckStartedAt = now()
  try {
    asynchttpGet('updateCheckCallback', [
      uri: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json',
      contentType: 'application/json', timeout: 15
    ], [requestId: requestId, manual: manual])
  } catch(Exception e) {
    state.remove('updateCheckRequestId')
    state.remove('updateCheckStartedAt')
    logError("Error checking for updates: ${e.message}")
  }
}

void updateCheckCallback(AsyncResponse response, Map data) {
  if(!data?.requestId || state.updateCheckRequestId != data.requestId) { return }
  try {
    if(response == null || response.hasError() || response.status != 200) {
      logWarn("Update check failed: ${response?.status ?: response?.errorMessage}")
      finishUpdateCheckRequest()
      return
    }
    Object parsed = response.getJson()
    if(!(parsed instanceof Map) || !((Map)parsed).version) {
      logWarn('Update check returned an invalid manifest')
      finishUpdateCheckRequest()
      return
    }
    Map next = [requestId: data.requestId, manual: data.manual, manifest: parsed]
    Long cookieAge = state.hubCookieTime != null ? now() - (state.hubCookieTime as Long) : null
    if(state.hubCookie != null && cookieAge != null && cookieAge < HUB_COOKIE_MAX_AGE_MS) {
      state.updateCheckCookie = state.hubCookie
      requestUpdateCheckAppList(next)
    } else {
      asynchttpPost('updateCheckLoginCallback', [uri: 'http://127.0.0.1:8080', path: '/login',
        requestContentType: 'application/x-www-form-urlencoded',
        body: [username: settings.hubSecurityUsername?.toString() ?: '', password: settings.hubSecurityPassword?.toString() ?: '', submit: 'Login'],
        followRedirects: false, textParser: true, timeout: 15], next)
    }
  } catch(Exception e) {
    logError("Error processing update check: ${e.message}")
    finishUpdateCheckRequest()
  }
}

void finishUpdateCheckRequest() {
  state.remove('updateCheckRequestId')
  state.remove('updateCheckStartedAt')
  state.remove('updateCheckCookie')
}

void updateCheckLoginCallback(AsyncResponse response, Map data) {
  if(!data?.requestId || state.updateCheckRequestId != data.requestId) { return }
  if(response == null || !(response.status in [200, 302])) {
    completeUpdateCheck(data, getCurrentVersion())
    return
  }
  Object header = getResponseHeaderIgnoreCase(response.headers, 'Set-Cookie')
  if(header instanceof Collection) { header = ((Collection)header).find { it != null } }
  String value = null
  if(header != null) {
    try { value = header.value?.toString() } catch(Exception ignored) { value = null }
    if(!value) { value = header.toString() }
  }
  state.updateCheckCookie = value ? value.split(';')[0] : ''
  requestUpdateCheckAppList(data)
}

void requestUpdateCheckAppList(Map data) {
  try {
    asynchttpGet('updateCheckAppListCallback', [uri: 'http://127.0.0.1:8080', path: '/hub2/userAppTypes',
      headers: hubCookieHeaders(state.updateCheckCookie as String), timeout: 15, ignoreSSLIssues: true], data)
  } catch(Exception e) { completeUpdateCheck(data, getCurrentVersion()) }
}

void updateCheckAppListCallback(AsyncResponse response, Map data) {
  if(!data?.requestId || state.updateCheckRequestId != data.requestId) { return }
  try {
    Object parsed = response != null && !response.hasError() && response.status == 200 ? response.getJson() : null
    if(!(parsed instanceof Collection)) {
      state.remove('hubCookie')
      state.remove('hubCookieTime')
      completeUpdateCheck(data, getCurrentVersion())
      return
    }
    state.hubCookie = state.updateCheckCookie
    state.hubCookieTime = now()
    Map target = parsed instanceof Collection ? (Map)((Collection)parsed).find { it instanceof Map && it.name == 'Sonos Advanced Controller' && it.namespace == 'dwinks' } : null
    if(target?.id == null) { completeUpdateCheck(data, getCurrentVersion()); return }
    asynchttpGet('updateCheckAppCodeCallback', [uri: 'http://127.0.0.1:8080', path: '/app/ajax/code',
      headers: hubCookieHeaders(state.updateCheckCookie as String), query: [id: target.id], timeout: 15, ignoreSSLIssues: true], data)
  } catch(Exception e) { completeUpdateCheck(data, getCurrentVersion()) }
}

void updateCheckAppCodeCallback(AsyncResponse response, Map data) {
  if(!data?.requestId || state.updateCheckRequestId != data.requestId) { return }
  String version = null
  try {
    Object parsed = response != null && !response.hasError() && response.status == 200 ? response.getJson() : null
    if(parsed instanceof Map && ((Map)parsed).source) {
      def matcher = ((Map)parsed).source.toString() =~ /version:\s*['"]([^'"]+)['"]/
      if(matcher) { version = matcher[0][1] }
    }
  } catch(Exception e) { logDebug("Could not read installed app version: ${e.message}") }
  completeUpdateCheck(data, version ?: getCurrentVersion())
}

void completeUpdateCheck(Map data, String currentVersion) {
  if(!data?.requestId || state.updateCheckRequestId != data.requestId) { return }
  finishUpdateCheckRequest()
  state.installedAppVersion = currentVersion
  Map manifest = data.manifest instanceof Map ? (Map)data.manifest : [:]
  if(!manifest.version) { return }
  String latestVersion = manifest.version.toString()
  Boolean manual = data.manual == true
  Integer comparison = compareVersions(currentVersion, latestVersion)
  logTrace("Async update check cached installed=${currentVersion}, latest=${latestVersion}, comparison=${comparison}")
  state.lastUpdateCheck = now()
  state.lastUpdateCheckFormatted = new Date().format('yyyy-MM-dd HH:mm:ss')
  if(comparison < 0) {
    state.updateAvailable = true
    state.latestVersion = latestVersion
    state.latestManifest = manifest
    state.latestReleaseDate = manifest.releaseDate ?: 'Unknown'
    state.latestReleaseUrl = "https://github.com/DanielWinks/Hubitat-Public/releases/tag/v${latestVersion}"
    app.updateLabel('Sonos Advanced Controller [Update Available]')
    logInfo("Update available: ${latestVersion} (current: ${currentVersion})")
    if(manual) { runIn(1, 'refreshPage') }
    if(settings.autoInstallUpdates == true && !manual) { scheduleAutoInstall() }
  } else {
    state.updateAvailable = false
    state.latestVersion = null
    state.latestManifest = null
    state.latestReleaseDate = null
    state.latestReleaseUrl = null
    app.updateLabel('Sonos Advanced Controller')
    if(manual) { logInfo("No updates available. Current version ${currentVersion} is up to date.") }
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
  if(state.maintenanceJob instanceof Map) { logWarn('A maintenance operation is already in progress'); return }
  if(!state.updateAvailable || !state.latestManifest) {
    logError('No update available to install')
    return
  }
  Map job = [id: UUID.randomUUID().toString(), op:'installUpdate', version: state.latestVersion,
    manifest: state.latestManifest, drivers: [], errors: [], updated: false]
  state.maintenanceJob = job
  logInfo("Starting asynchronous update to version ${job.version}...")
  Map apps = job.manifest.apps instanceof Collection ? (Map)job.manifest.apps.find { it instanceof Map } : null
  if(apps?.location) {
    job.pendingRequest = [stage:'downloadApp', params:[uri: apps.location, contentType: 'text/plain', textParser:true, timeout: 30], method:'GET']
    maintenanceAuthenticate(job)
  } else {
    job.pendingRequest = [stage:'nextDriver']
    maintenanceAuthenticate(job)
  }
}

/** Dispatch one maintenance HTTP operation. Each callback advances exactly one stage. */
void maintenanceRequest(Map job, String stage, Map params, String method = 'GET') {
  try {
    job.stage = stage
    String requestId = UUID.randomUUID().toString()
    job.activeRequestId = requestId
    Map requestData = [jobId: job.id, requestId: requestId, stage: stage]
    if(method == 'POST') { asynchttpPost('maintenanceHttpCallback', params, requestData) }
    else { asynchttpGet('maintenanceHttpCallback', params, requestData) }
  } catch(Exception e) {
    logError("Maintenance request ${stage} could not start: ${e.message}")
    maintenanceAdvanceFailure(job, stage)
  }
}

void maintenanceAuthenticate(Map job) {
  Long cookieAge = state.hubCookieTime != null ? now() - (state.hubCookieTime as Long) : null
  if(state.hubCookie != null && cookieAge != null && cookieAge < HUB_COOKIE_MAX_AGE_MS) {
    job.cookie = state.hubCookie
    Map pending = (Map)job.pendingRequest
    if(pending.stage == 'nextLibrary') { maintenanceNextLibrary(job) }
    else if(pending.stage == 'nextDriver') { maintenanceStartDrivers(job) }
    else if(pending.stage == 'duplicateDeletes') { maintenanceNextDuplicateDelete(job) }
    else { maintenanceRequest(job, pending.stage as String, (Map)pending.params, pending.method as String) }
    return
  }
  job.stage = 'login'
  try {
    String requestId = UUID.randomUUID().toString()
    job.activeRequestId = requestId
    asynchttpPost('maintenanceHttpCallback', [uri:'http://127.0.0.1:8080', path:'/login', requestContentType:'application/x-www-form-urlencoded',
      body:[username:settings.hubSecurityUsername?.toString() ?: '', password:settings.hubSecurityPassword?.toString() ?: '', submit:'Login'],
      followRedirects:false, textParser:true, timeout:15], [jobId:job.id, requestId:requestId, stage:'login'])
  } catch(Exception e) { logError("Maintenance login failed to start: ${e.message}"); maintenanceAdvanceFailure(job, 'login') }
}

void maintenanceHttpCallback(AsyncResponse response, Map data) {
  Map job = state.maintenanceJob instanceof Map ? (Map)state.maintenanceJob : null
  if(!job || !data?.jobId || job.id != data.jobId || !data?.requestId ||
      job.activeRequestId != data.requestId || job.stage != data.stage) {
    logDebug("Ignoring stale maintenance callback request=${data?.requestId ?: 'unknown'} stage=${data?.stage ?: 'unknown'}")
    return
  }
  job.remove('activeRequestId')
  String stage = data.stage?.toString()
  Boolean ok = response != null && !response.hasError() && response.status in [200, 302]
  try {
    if(stage == 'downloadApp' || stage == 'downloadDriver' || stage == 'downloadLibrary') {
      String source = ok ? response.data?.toString() : null
      if(!source) { maintenanceAdvanceFailure(job, stage); return }
      job.source = source
      if(stage == 'downloadApp') { maintenanceRequest(job, 'appList', [uri:'http://127.0.0.1:8080', path:'/hub2/userAppTypes', timeout:15, ignoreSSLIssues:true]) }
      else if(stage == 'downloadDriver') { maintenanceRequest(job, 'driverList', [uri:'http://127.0.0.1:8080', path:'/device/drivers', headers:hubCookieHeaders(job.cookie as String), timeout:15]) }
      else { maintenanceRequest(job, 'libraryCode', [uri:'http://127.0.0.1:8080', path:'/library/ajax/code', query:[id:job.artifact.id.toString()], headers:hubCookieHeaders(job.cookie as String), timeout:15]) }
      return
    }
    if(stage == 'login') {
      if(!ok) { maintenanceAdvanceFailure(job, 'login'); return }
      Object header = getResponseHeaderIgnoreCase(response.headers, 'Set-Cookie')
      if(header instanceof Collection) { header = ((Collection)header).find { it != null } }
      String cookieValue = null
      if(header != null) {
        try { cookieValue = header.value?.toString() } catch(Exception ignored) { cookieValue = null }
        if(!cookieValue) { cookieValue = header.toString() }
      }
      job.cookie = cookieValue ? cookieValue.split(';')[0] : ''
      state.hubCookie = job.cookie
      state.hubCookieTime = now()
      Map pending = (Map)job.pendingRequest
      if(pending.stage == 'nextLibrary') { maintenanceNextLibrary(job) }
      else if(pending.stage == 'nextDriver') { maintenanceStartDrivers(job) }
      else if(pending.stage == 'duplicateDeletes') { maintenanceNextDuplicateDelete(job) }
      else { maintenanceRequest(job, pending.stage as String, (Map)pending.params, pending.method as String) }
      return
    }
    if(stage == 'appList') {
      Object payload = ok ? response.getJson() : null
      Map target = payload instanceof Collection ? (Map)((Collection)payload).find { it instanceof Map && it.name == 'Sonos Advanced Controller' && it.namespace == 'dwinks' } : null
      if(!target?.id) { maintenanceAdvanceFailure(job, stage); return }
      job.appId = target.id
      maintenanceRequest(job, 'appCode', [uri:'http://127.0.0.1:8080', path:'/app/ajax/code', query:[id:target.id], headers:hubCookieHeaders(job.cookie as String), timeout:15, ignoreSSLIssues:true])
      return
    }
    if(stage == 'duplicateApps') {
      if(!ok || !(response.getJson() instanceof Collection)) { maintenanceAdvanceFailure(job, stage); return }
      List entries = new ArrayList((Collection)response.getJson())
      job.appDeletes = duplicateCodeDeletes(entries, 'app')
      maintenanceRequest(job, 'duplicateDrivers', [uri:'http://127.0.0.1:8080', path:'/device/drivers', headers:hubCookieHeaders(job.cookie as String), timeout:15])
      return
    }
    if(stage == 'duplicateDrivers') {
      Object payload = ok ? response.getJson() : null
      if(!(payload instanceof Map) || !(((Map)payload).drivers instanceof Collection)) { maintenanceAdvanceFailure(job, stage); return }
      List entries = new ArrayList((Collection)((Map)payload).drivers)
      job.driverDeletes = duplicateCodeDeletes(entries, 'driver')
      job.pendingDeletes = new ArrayList((Collection)job.appDeletes)
      job.pendingDeletes.addAll((Collection)job.driverDeletes)
      maintenanceNextDuplicateDelete(job)
      return
    }
    if(stage == 'duplicateDeleteApp' || stage == 'duplicateDeleteDriver') {
      Map item = (Map)job.activeDelete
      Boolean deleted = ok && response.getJson() instanceof Map && response.getJson().status == true
      if(deleted) {
        if(stage == 'duplicateDeleteApp') { job.appsRemoved = (job.appsRemoved as Integer) + 1 }
        else { job.driversRemoved = (job.driversRemoved as Integer) + 1 }
      } else { job.errors << "Failed to remove ${item.kind} ${item.name} (id: ${item.id})" }
      job.remove('activeDelete')
      maintenanceNextDuplicateDelete(job)
      return
    }
    if(stage == 'appCode') {
      Map payload = ok && response.getJson() instanceof Map ? (Map)response.getJson() : [:]
      if(payload.version == null) { maintenanceAdvanceFailure(job, stage); return }
      maintenanceRequest(job, 'appUpdate', [uri:'http://127.0.0.1:8080', path:'/app/ajax/update', requestContentType:'application/x-www-form-urlencoded', headers:hubCookieHeaders(job.cookie as String), body:[id:job.appId, version:payload.version, source:job.source], timeout:300, ignoreSSLIssues:true], 'POST')
      return
    }
    if(stage == 'appUpdate') {
      Map payload = ok && response.getJson() instanceof Map ? (Map)response.getJson() : [:]
      if(payload.status == 'success') { job.updated = true; runIn(2, 'refreshPage') }
      else { job.errors << 'Failed to update app' }
      job.remove('source')
      maintenanceStartDrivers(job)
      return
    }
    if(stage == 'driverList') {
      Map artifact = (Map)job.artifact
      Object payload = ok ? response.getJson() : null
      Map target = payload instanceof Map && ((Map)payload).drivers instanceof Collection ? (Map)((Map)payload).drivers.find { it instanceof Map && it.type == 'usr' && it.name == artifact.name && it.namespace == artifact.namespace } : null
      if(!target) {
        maintenanceRequest(job, 'driverCreate', [uri:'http://127.0.0.1:8080', path:'/driver/save', requestContentType:'application/x-www-form-urlencoded', headers:hubCookieHeaders(job.cookie as String), body:[id:'', version:'', create:'', source:job.source], timeout:300, ignoreSSLIssues:true], 'POST')
      } else {
        job.driverId = target.id
        maintenanceRequest(job, 'driverCode', [uri:'http://127.0.0.1:8080', path:'/driver/ajax/code', query:[id:target.id], headers:hubCookieHeaders(job.cookie as String), timeout:15, ignoreSSLIssues:true])
      }
      return
    }
    if(stage == 'driverCode') {
      Map payload = ok && response.getJson() instanceof Map ? (Map)response.getJson() : [:]
      if(payload.version == null) { maintenanceAdvanceFailure(job, stage); return }
      maintenanceRequest(job, 'driverUpdate', [uri:'http://127.0.0.1:8080', path:'/driver/ajax/update', requestContentType:'application/x-www-form-urlencoded', headers:hubCookieHeaders(job.cookie as String), body:[id:job.driverId, version:payload.version, source:job.source], timeout:300, ignoreSSLIssues:true], 'POST')
      return
    }
    if(stage == 'driverUpdate' || stage == 'driverCreate') {
      Map payload = ok && response.getJson() instanceof Map ? (Map)response.getJson() : [:]
      Boolean success = stage == 'driverCreate' ? response?.headers?.Location != null : payload.status == 'success'
      if(!success) { job.errors << "Failed to update driver ${job.artifact.name}" } else { job.updated = true }
      maintenanceNextDriver(job)
      return
    }
    if(stage == 'downloadLibrary') {
      job.librarySource = job.source
      maintenanceRequest(job, 'libraryCode', [uri:'http://127.0.0.1:8080', path:'/library/ajax/code', query:[id:job.artifact.id.toString()], headers:hubCookieHeaders(job.cookie as String), timeout:15])
      return
    }
    if(stage == 'libraryCode') {
      Map payload = ok && response.getJson() instanceof Map ? (Map)response.getJson() : [:]
      if(payload.id == null) { maintenanceAdvanceFailure(job, stage); return }
      String body = "id=${job.artifact.id}&version=${payload.version ?: 0}&source=${java.net.URLEncoder.encode(job.librarySource ?: job.source, 'UTF-8')}"
      maintenanceRequest(job, 'libraryUpdate', [uri:'http://127.0.0.1:8080', path:'/library/ajax/update', headers:hubCookieHeaders(job.cookie as String)+['Content-Type':'application/x-www-form-urlencoded'], body:body, timeout:30, ignoreSSLIssues:true], 'POST')
      return
    }
    if(stage == 'libraryUpdate') {
      if(!ok || response.getJson()?.status != 'success') { job.errors << "Failed to publish ${job.artifact.name}" }
      else { job.successCount = (job.successCount ?: 0) + 1 }
      maintenanceNextLibrary(job)
    }
  } catch(Exception e) {
    logError("Maintenance callback ${stage} failed: ${e.message}")
    maintenanceAdvanceFailure(job, stage)
  }
}

void maintenanceAdvanceFailure(Map job, String stage) {
  job.errors << "Maintenance stage failed: ${stage}"
  if(job.op == 'cleanupDuplicates') {
    if(stage == 'duplicateApps') { maintenanceFinish(job) }
    else if(stage == 'duplicateDrivers') { job.pendingDeletes = new ArrayList((Collection)(job.appDeletes ?: [])); maintenanceNextDuplicateDelete(job) }
    else if(stage.startsWith('duplicateDelete')) { job.remove('activeDelete'); maintenanceNextDuplicateDelete(job) }
    else { maintenanceFinish(job) }
  }
  else if(stage.startsWith('driver') || stage == 'downloadDriver') { maintenanceNextDriver(job) }
  else if(stage.startsWith('library') || stage == 'downloadLibrary') { maintenanceNextLibrary(job) }
  else if(job.manifest?.drivers) { maintenanceStartDrivers(job) }
  else { maintenanceFinish(job) }
}

void maintenanceStartDrivers(Map job) {
  if(!(job.drivers instanceof Collection)) {
    job.drivers = job.manifest?.drivers instanceof Collection ? new ArrayList(job.manifest.drivers) : []
  }
  maintenanceNextDriver(job)
}

void maintenanceNextDriver(Map job) {
  if(job.drivers && !job.drivers.isEmpty()) {
    Map artifact = (Map)job.drivers.remove(0)
    artifact.namespace = artifact.namespace ?: 'dwinks'
    job.artifact = artifact
    job.source = null
    maintenanceRequest(job, 'downloadDriver', [uri:artifact.location, contentType:'text/plain', textParser:true, timeout:30])
  } else { maintenanceFinish(job) }
}

void maintenanceNextLibrary(Map job) {
  if(job.libraries && !job.libraries.isEmpty()) {
    Map artifact = (Map)job.libraries.remove(0)
    job.artifact = artifact
    job.source = null
    maintenanceRequest(job, 'downloadLibrary', [uri:artifact.location, contentType:'text/plain', textParser:true, timeout:30])
  } else if(job.op == 'repairVersions') { maintenanceStartDrivers(job) }
  else { maintenanceFinish(job) }
}

List duplicateCodeDeletes(List entries, String kind) {
  Map grouped = [:].withDefault { [] }
  entries.each { Object value ->
    if(value instanceof Map && ((Map)value).name && ((Map)value).namespace && ((Map)value).id != null) {
      Map entry = (Map)value
      grouped["${entry.namespace}:${entry.name}"] << entry
    }
  }
  List deletes = []
  grouped.each { String key, List group ->
    if(group.size() > 1) {
      logWarn("Found ${group.size()} duplicate ${kind}s for ${key}")
      group.sort { Object a, Object b -> (a.id as Integer) <=> (b.id as Integer) }
      group.drop(1).each { Map duplicate -> deletes << [kind:kind, id:duplicate.id.toString(), name:duplicate.name] }
    }
  }
  return deletes
}

void maintenanceNextDuplicateDelete(Map job) {
  List pending = (List)(job.pendingDeletes ?: [])
  if(pending.isEmpty()) { maintenanceFinish(job); return }
  Map item = (Map)pending.remove(0)
  job.activeDelete = item
  logInfo("Removing duplicate ${item.kind}: ${item.name} (id: ${item.id})")
  String stage = item.kind == 'app' ? 'duplicateDeleteApp' : 'duplicateDeleteDriver'
  String path = item.kind == 'app' ? "/app/edit/deleteJsonSafe/${item.id}" : "/driver/editor/deleteJson/${item.id}"
  maintenanceRequest(job, stage, [uri:'http://127.0.0.1:8080', path:path, headers:hubCookieHeaders(job.cookie as String), timeout:30, ignoreSSLIssues:true])
}

void maintenanceFinish(Map job) {
  if(job.op == 'cleanupDuplicates') {
    if(job.appsRemoved > 0 || job.driversRemoved > 0) {
      state.duplicateCleanupResult = "Removed ${job.appsRemoved} duplicate app(s) and ${job.driversRemoved} duplicate driver(s)"
      logInfo(state.duplicateCleanupResult)
    } else if(job.errors) {
      state.duplicateCleanupResult = "Errors during cleanup: ${job.errors.join(', ')}"
      logWarn(state.duplicateCleanupResult)
    } else {
      state.duplicateCleanupResult = 'No duplicates found'
      logInfo('No duplicate apps or drivers found')
    }
  } else if(job.op == 'publishLibraries') {
    Integer successes = job.successCount ?: 0
    String timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
    state.lastLibraryPublish = successes == job.totalLibraries ? "✓ ${timestamp}: Successfully published ${successes} libraries" : (successes ? "⚠ ${timestamp}: Published ${successes}, failed ${job.errors.size()} (${job.errors.join(', ')})" : "✗ ${timestamp}: Failed to publish libraries (${job.errors.join(', ')})")
    if(successes > 0) { runIn(1, 'checkInstalledVersions') }
  } else if(job.op == 'repairVersions') {
    if(job.errors.isEmpty()) { runIn(2, 'checkInstalledVersions') }
    else { logWarn("Version repair completed with errors: ${job.errors.join(', ')}") }
  } else if(job.errors.isEmpty()) {
    state.updateAvailable = false
    state.latestVersion = null
    state.latestManifest = null
    state.latestReleaseDate = null
    state.latestReleaseUrl = null
    state.remove('versionMismatches'); state.remove('installedVersions'); state.remove('lastVersionCheck')
    state.updateJustCompleted = true
    app.updateLabel('Sonos Advanced Controller')
    unschedule('performScheduledInstall')
    runIn(3, 'refreshPage')
  } else {
    logError("Maintenance completed with errors: ${job.errors.join(', ')}")
    if(job.updated) { runIn(3, 'refreshPage') }
  }
  state.remove('maintenanceJob')
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
    if(cookie == null) {
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
      headers: hubCookieHeaders(cookie),
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
      headers: hubCookieHeaders(cookie) + ['Connection': 'keep-alive'],
      body: [
        id: appCodeId,
        version: currentHubVersion,
        source: sourceCode
      ],
      timeout: 300,
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
    if(cookie == null) {
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
        headers: hubCookieHeaders(cookie),
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
      headers: hubCookieHeaders(cookie),
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
    if(cookie == null) { return [] }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: hubCookieHeaders(cookie),
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
    if(cookie == null) { return [] }

    // Use /hub2/userAppTypes endpoint like HPM does (requires 2.3.6+)
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/hub2/userAppTypes',
      headers: hubCookieHeaders(cookie),
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
    if(cookie == null) {
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
      headers: hubCookieHeaders(cookie),
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
      headers: hubCookieHeaders(cookie),
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
  if(state.hubCookie != null && cookieAgeMs != null && cookieAgeMs < HUB_COOKIE_MAX_AGE_MS) {
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
        username: settings.hubSecurityUsername?.toString() ?: '',
        password: settings.hubSecurityPassword?.toString() ?: '',
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
        Object setCookieHeader = getResponseHeaderIgnoreCase(resp?.headers, 'Set-Cookie')
        String cookieValue = null
        if(setCookieHeader instanceof Collection) {
          cookieValue = ((Collection)setCookieHeader).find { it != null }?.toString()
        } else if(setCookieHeader != null) {
          try {
            cookieValue = setCookieHeader.value?.toString()
          } catch(Exception ignored) {
            cookieValue = null
          }
          if(!cookieValue) {
            cookieValue = setCookieHeader.toString()
          }
        }

        if(cookieValue) {
          cookie = cookieValue.split(';')[0]
          state.hubCookie = cookie  // Store for reuse like HPM
          state.hubCookieTime = now()
          logDebug("Got cookie: ${cookie?.take(20)}...")
        } else {
          if(hasUnauthenticatedHubAccess()) {
            cookie = ''
            state.hubCookie = cookie
            state.hubCookieTime = now()
            logDebug("Hub API access does not require a login cookie")
          } else {
            logWarn("No Set-Cookie header in login response")
          }
        }
      } else {
        logWarn("Unexpected login status: ${resp?.status}")
      }
    }

    if(cookie == null) {
      logWarn("Failed to get authentication cookie")
    }
    return cookie
  } catch(Exception e) {
    logError("Login error: ${e.message}")
    return null
  }
}

Boolean hasUnauthenticatedHubAccess() {
  try {
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/hub2/userAppTypes',
      timeout: 15,
      ignoreSSLIssues: true
    ]

    Boolean accessible = false
    httpGet(params) { resp ->
      accessible = resp?.status == 200 && resp.data instanceof Collection
    }
    return accessible
  } catch(Exception e) {
    logDebug("Unable to check unauthenticated hub access: ${e.message}")
    return false
  }
}

Object getResponseHeaderIgnoreCase(Object headers, String headerName) {
  if(!(headers instanceof Map)) {
    return null
  }

  Object headerValue = null
  ((Map)headers).each { key, value ->
    if(key != null && key.toString().equalsIgnoreCase(headerName)) {
      headerValue = value
    }
  }
  return headerValue
}

Map hubCookieHeaders(String cookie) {
  return cookie ? ['Cookie': cookie] : [:]
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
