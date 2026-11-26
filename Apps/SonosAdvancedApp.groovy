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
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import java.io.StringWriter
import hubitat.scheduling.AsyncResponse
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import java.util.Locale
import java.util.Collections
import java.util.TreeSet
import java.util.UUID
import java.util.Random
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.math.RoundingMode
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import groovy.json.JsonBuilder
import org.quartz.CronExpression

// =============================================================================
// Library: Utilities and Logging (inlined)
// =============================================================================
@Field static final List<String> APP_LOG_LEVELS = ['trace', 'debug', 'info', 'warn', 'error', 'off']
@Field static final String GROUP_MANAGEMENT_RESULTS_KEY = 'groupManagementResults'
@Field static final DateTimeFormatter APP_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
@Field static final JsonSlurper SHARED_JSON_SLURPER = new JsonSlurper()

JsonSlurper getSharedJsonSlurper() {
  return SHARED_JSON_SLURPER
}

ZoneId hubZoneId() {
  return location?.timeZone ? ZoneId.of(location.timeZone.ID) : ZoneId.systemDefault()
}

Clock hubClock() {
  return Clock.system(hubZoneId())
}

Instant hubNowInstant() {
  return hubClock().instant()
}

Long nowPlusSeconds(long seconds) {
  return hubNowInstant().plusSeconds(seconds).toEpochMilli()
}

Long nowPlusMillis(long millis) {
  return hubNowInstant().plusMillis(millis).toEpochMilli()
}

Long millisSince(Long epochMillis) {
  if(epochMillis == null) { return null }
  return Duration.between(Instant.ofEpochMilli(epochMillis), hubNowInstant()).toMillis()
}

DayOfWeek parseDayOfWeek(String value) {
  if(!value) { return DayOfWeek.SUNDAY }
  String normalized = value.trim().toLowerCase(Locale.ROOT)
  switch(normalized) {
    case 'sunday': return DayOfWeek.SUNDAY
    case 'monday': return DayOfWeek.MONDAY
    case 'tuesday': return DayOfWeek.TUESDAY
    case 'wednesday': return DayOfWeek.WEDNESDAY
    case 'thursday': return DayOfWeek.THURSDAY
    case 'friday': return DayOfWeek.FRIDAY
    case 'saturday': return DayOfWeek.SATURDAY
    case 'sun': return DayOfWeek.SUNDAY
    case 'mon': return DayOfWeek.MONDAY
    case 'tue': return DayOfWeek.TUESDAY
    case 'wed': return DayOfWeek.WEDNESDAY
    case 'thu': return DayOfWeek.THURSDAY
    case 'fri': return DayOfWeek.FRIDAY
    case 'sat': return DayOfWeek.SATURDAY
    default: return DayOfWeek.SUNDAY
  }
}

ZonedDateTime nextWeeklyOccurrence(LocalTime time, DayOfWeek dayOfWeek, ZoneId zone) {
  ZonedDateTime nowDateTime = ZonedDateTime.now(hubClock())
  ZonedDateTime candidate = nowDateTime.with(time).with(TemporalAdjusters.nextOrSame(dayOfWeek))
  if(candidate.isBefore(nowDateTime)) {
    candidate = candidate.plusWeeks(1)
  }
  return candidate
}

ZonedDateTime atInstallTime(Date installTime, ZoneId zone) {
  LocalTime time = LocalTime.of(installTime.hours, installTime.minutes)
  return ZonedDateTime.of(ZonedDateTime.now(Clock.system(zone)).toLocalDate(), time, zone)
}

Set<DayOfWeek> sonosAlarmRecurrenceDays(String recurrence) {
  switch(recurrence?.toString()?.toUpperCase(Locale.ROOT)) {
    case 'DAILY': return Collections.unmodifiableSet(new HashSet<DayOfWeek>(DayOfWeek.values()))
    case 'WEEKDAYS': return Collections.unmodifiableSet(new HashSet<DayOfWeek>([DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY]))
    case 'WEEKENDS': return Collections.unmodifiableSet(new HashSet<DayOfWeek>([DayOfWeek.SATURDAY, DayOfWeek.SUNDAY]))
    default: return Collections.unmodifiableSet(new HashSet<DayOfWeek>())
  }
}

Boolean isValidSonosRecurrence(String recurrence) {
  return recurrence in ['ONCE', 'DAILY', 'WEEKDAYS', 'WEEKENDS']
}

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

void logPayloadAtTrace(String debugMessage, String traceMessage) {
  if(getConfiguredLogLevel() == 'trace') {
    logTrace(traceMessage)
  } else {
    logDebug(debugMessage)
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
  String normalized = level?.toLowerCase(Locale.ROOT)
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
  version: '0.18.9',
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
}

// =============================================================================
// Fields
// =============================================================================
@Field static final String LEGACY_DISCOVERY_SECONDARIES_STATE_KEY = 'discoveredSonosSecondaries'
@Field static final String PENDING_PLAYER_PROVISIONING_REQUESTS_KEY = 'pendingPlayerProvisioningRequests'
@Field static final String PLAYER_PROVISIONING_REQUEST_STATUS_KEY = 'playerProvisioningRequestStatus'
@Field static final String PENDING_GROUP_CONFIGURATION_REQUESTS_KEY = 'pendingGroupConfigurationRequests'
@Field static final String DISCOVERY_MANAGEMENT_CHILD_ID_STATE_KEY = 'discoveryManagementChildInstanceId'
@Field static final String CHILD_MANAGED_SETTINGS_STATE_KEY = 'childManagedSettings'
@Field static final List<String> CHILD_MANAGED_SETTING_NAMES = [
  'maximumTtsRetention', 'groupAnnouncementMode', 'highPriorityAnnouncementMode',
  'queueChimeSeparatelyFromTTS', 'suppressChimeOnSuccessiveTTS',
  'enableAlarmManagement', 'enableAlarmSwitches', 'autoCheckUpdates', 'autoInstallUpdates',
  'updateCheckFrequency', 'autoInstallTime', 'autoInstallNextOccurrence', 'autoInstallDayOfWeek',
  'hubSecurityUsername', 'hubSecurityPassword'
]

@Field static final String LOCAL_CONTROL_RETRY_STATE_KEY = 'localControlRetryAttemptCount'
@Field static final String LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY = 'lastLocalControlRetryRequest'
@Field static final String LOCAL_CONTROL_RETRY_DATA_KEY = '_localControlRetryRequest'
// Hub login sessions expire; re-login when the cached cookie is older than this.
@Field static final Long HUB_COOKIE_MAX_AGE_MS = Duration.ofMinutes(10).toMillis()
@Field static final String ZGT_LAST_REPORT_STATE_KEY = 'zgtLastReport'
@Field static final String ZGT_PAYLOAD_STATE_KEY = 'zgtPayload'
@Field static final String PLAYER_PREFERENCES_BY_DNI_STATE_KEY = 'playerPreferencesByDni'
@Field static final String GROUP_PREFERENCES_BY_DNI_STATE_KEY = 'groupPreferencesByDni'
@Field static final String READY_FOR_ZGT_STATE_KEY = 'readyForZgt'
@Field static final String LAST_PROCESSED_TOPOLOGY_STATE_KEY = 'lastProcessedTopologyByDni'
@Field static final String GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY = 'groupAnnouncementProgressQueue'
@Field static final String GROUP_ANNOUNCEMENT_DUE_AT_STATE_KEY = 'groupAnnouncementDueAt'
@Field static final Integer GROUP_ANNOUNCEMENT_PROGRESS_MAX = 64
@Field static final Long GROUP_ANNOUNCEMENT_MAX_WAIT_MS = 2000L
@Field static final Integer GROUP_ANNOUNCEMENT_DEBOUNCE_MS = 1000
@Field static final String ALARM_REQUEST_ATTRIBUTE = 'sonosAlarmRequest'
@Field static final String PENDING_PLAYER_STATE_UPDATES_STATE_KEY = 'pendingPlayerStateUpdates'
@Field static final String PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY = 'playerStateUpdateDrainScheduled'
@Field static final String PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY = 'playerStateUpdateDrainDueAt'
@Field static final String PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY = 'playerStateUpdateDrainActive'
@Field static final String PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY = 'pendingPlayerAttributeUpdates'
@Field static final String PLAYER_ATTRIBUTE_UPDATE_DRAIN_SCHEDULED_STATE_KEY = 'playerAttributeUpdateDrainScheduled'
@Field static final List<String> SHARED_PLAYER_PLAYBACK_ATTRIBUTES = [
  'status', 'transportStatus', 'audioSource', 'audioInputFormat', 'currentTrackName', 'currentArtistName',
  'currentAlbumName', 'albumArtURI', 'currentFavorite', 'currentPlaylist', 'currentTrackNumber',
  'currentTrackDuration', 'trackDescription', 'trackData', 'trackPosition', 'trackPositionUpdated',
  'nextArtistName', 'nextAlbumName', 'nextTrackName', 'nextTrackAlbumArtURI',
  'albumArtSmall', 'albumArtMedium', 'albumArtLarge',
  'currentRepeatOneMode', 'currentRepeatAllMode', 'currentShuffleMode', 'currentCrossfadeMode',
  'groupVolume', 'groupMute'
]
@Field static final Set<String> SOAP_EVENT_OWNED_ATTRIBUTES = [
  'status', 'transportStatus', 'audioSource', 'audioInputFormat', 'currentTrackName', 'currentArtistName',
  'currentAlbumName', 'albumArtURI', 'albumArtSmall', 'albumArtMedium', 'albumArtLarge',
  'currentFavorite', 'currentPlaylist', 'currentTrackNumber', 'currentTrackDuration',
  'currentRepeatOneMode', 'currentRepeatAllMode', 'currentShuffleMode', 'currentCrossfadeMode',
  'trackDescription', 'trackData', 'nextArtistName', 'nextAlbumName', 'nextTrackName',
  'nextTrackAlbumArtURI', 'groupVolume', 'groupMute', 'volume', 'level', 'mute', 'balance',
  'bass', 'treble', 'loudness', 'nightMode', 'speechEnhancement', 'groupId', 'groupName',
  'groupCoordinatorId', 'groupCoordinatorName', 'groupPlayerIds', 'groupMemberCount',
  'groupMemberNames', 'isGrouped', 'isGroupCoordinator'
] as Set<String>
@Field static final Integer PLAYER_STATE_UPDATE_DRAIN_MAX_ITEMS = 12
@Field static final Long PLAYER_STATE_UPDATE_DRAIN_BUDGET_MS = 200L
@Field static final Integer PLAYER_STATE_UPDATE_QUEUE_MAX_ITEMS = 512
@Field static final Integer GROUP_STATE_DRAIN_MAX_ITEMS = 8
@Field static final Integer GROUP_MEMBERSHIP_APPLY_BATCH_SIZE = 8
@Field static final String GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY = 'groupMembershipApplyGenerations'
@Field static final String PLAYER_COMMAND_QUEUE_STATE_KEY = 'pendingPlayerCommandRequests'
@Field static final String PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY = 'receivedPlayerCommandRequestIds'
@Field static final String PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY = 'pendingGroupTopologyEvents'
@Field static final String GROUP_TOPOLOGY_EVENT_DRAIN_SCHEDULED_STATE_KEY = 'groupTopologyEventDrainScheduled'
@Field static final String DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY = 'deviceCommandClearTimes'
@Field static final String AVT_EVENTS_BY_DNI_STATE_KEY = 'avtEventsByDni'
@Field static final String GROUP_DEVICE_COORDINATORS_STATE_KEY = 'groupDeviceCoordinatorsByDni'
@Field static final String ZGT_EVENTS_STATE_KEY = 'zgtEventsByGroupId'
@Field static final Integer UPNP_EVENT_DEBOUNCE_MILLIS = 500
@Field static final String PENDING_UPNP_NOTIFICATION_QUEUE_KEY = 'pendingUpnpNotifications'
@Field static final String PLAYER_READINGS_STATE_KEY = 'playerReadingsByRincon'
@Field static final String PENDING_VOLUME_RECALCULATION_STATE_KEY = 'pendingVolumeRecalculation'
@Field static final String GROUP_OPERATION_STATE_KEY = 'activeGroupTopologyOperation'
@Field static final String GROUPING_CONTEXTS_STATE_KEY = 'groupingContexts'
@Field static final String GROUP_COMMAND_QUEUE_STATE_KEY = 'pendingGroupCommandRequests'
@Field static final String DEFERRED_GROUP_COMMANDS_STATE_KEY = 'deferredGroupOperationCommands'
@Field static final String GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY = 'groupAnnouncementQueues'
@Field static final String AUDIO_CLIP_QUEUES_STATE_KEY = 'audioClipQueuesByPlayer'
@Field static final String AUDIO_CLIP_APP_ID = 'com.dwinks.sonos.advanced'
@Field static final String AUDIO_CLIP_ENDPOINT = '/players/local/audioClip'
@Field static final String DEFERRED_PLAYER_COMMANDS_STATE_KEY = 'deferredGroupPlayerCommands'
@Field static final String GROUPING_MODE_EXPLICIT = 'EXPLICIT'
@Field static final String GROUPING_MODE_ADDITIVE = 'ADDITIVE'
@Field static final String GROUPING_MODE_CURRENT = 'CURRENT'
@Field static final String GROUP_DEVICE_DNI_MARKER = '-SonosGroupDevice-'
@Field static final String ALARM_SWITCH_DNI_MARKER = '-SonosAlarm-'
@Field static final String ALARM_MANAGER_DNI_MARKER = '-SonosAlarmManager-'
@Field static final String SONOS_ALARM_SERVICE_TYPE = 'urn:schemas-upnp-org:service:AlarmClock:1'
@Field static final String SONOS_ALARM_CONTROL_URL = '/AlarmClock/Control'
@Field static final Integer GROUP_OPERATION_POLL_SECONDS = 1
@Field static final Integer TOPOLOGY_CHANGE_SUPPRESSION_SECONDS = 15
@Field static final Integer GROUP_OPERATION_DEADLINE_SECONDS = 240
@Field static final Integer GROUPING_CONTEXT_TTL_SECONDS = 15
@Field static final Integer GROUP_OPERATION_MAX_TOPOLOGY_ATTEMPTS = 4
@Field static final Integer GROUP_OPERATION_COMMAND_WAIT_SECONDS = 3
@Field static final Integer GROUP_OPERATION_TOPOLOGY_COMMAND_WAIT_SECONDS = 10
@Field static final String PENDING_GROUP_STATE_QUEUE_KEY = 'pendingGroupStateUpdates'
@Field static final String GROUP_STATE_DRAIN_SCHEDULED_KEY = 'groupStateDrainScheduled'
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
// Whitelisted persistence — pruning helper surfaces
// Maintaining ACTIVE_* keeps forward migration simple: add the key when a feature
// writes it, delete the entry when the feature is removed, and pruneOrphanedAppPersistence()
// deletes any stored key not in the set on next Save. No hand-maintained blacklist.
// =============================================================================
@Field final Set<String> ACTIVE_APP_STATE_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  ZGT_LAST_REPORT_STATE_KEY,
  ZGT_PAYLOAD_STATE_KEY,
  PLAYER_PREFERENCES_BY_DNI_STATE_KEY,
  GROUP_PREFERENCES_BY_DNI_STATE_KEY,
  READY_FOR_ZGT_STATE_KEY,
  LAST_PROCESSED_TOPOLOGY_STATE_KEY,
  GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY,
  GROUP_ANNOUNCEMENT_DUE_AT_STATE_KEY,
  PENDING_PLAYER_STATE_UPDATES_STATE_KEY,
  PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY,
  PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY,
  PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY,
  GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY,
  PLAYER_COMMAND_QUEUE_STATE_KEY,
  PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY,
  DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY,
  AVT_EVENTS_BY_DNI_STATE_KEY,
  GROUP_DEVICE_COORDINATORS_STATE_KEY,
  ZGT_EVENTS_STATE_KEY,
  PLAYER_READINGS_STATE_KEY,
  PENDING_VOLUME_RECALCULATION_STATE_KEY,
  PENDING_UPNP_NOTIFICATION_QUEUE_KEY,
  GROUP_OPERATION_STATE_KEY,
  GROUPING_CONTEXTS_STATE_KEY,
  GROUP_COMMAND_QUEUE_STATE_KEY,
  DEFERRED_GROUP_COMMANDS_STATE_KEY,
  GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY,
  AUDIO_CLIP_QUEUES_STATE_KEY,
  DEFERRED_PLAYER_COMMANDS_STATE_KEY,
  PENDING_GROUP_STATE_QUEUE_KEY,
  GROUP_STATE_DRAIN_SCHEDULED_KEY,
  LEGACY_DISCOVERY_SECONDARIES_STATE_KEY,
  PENDING_PLAYER_PROVISIONING_REQUESTS_KEY,
  PLAYER_PROVISIONING_REQUEST_STATUS_KEY,
  PENDING_GROUP_CONFIGURATION_REQUESTS_KEY,
  DISCOVERY_MANAGEMENT_CHILD_ID_STATE_KEY,
  LOCAL_CONTROL_RETRY_STATE_KEY,
  LOCAL_CONTROL_RETRY_REQUEST_STATE_KEY,
  STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY,
  ANNOUNCEMENT_POLICY_SETTINGS_STATE_KEY,
  'userGroups',
  'alarmManagementPreferencesApplied',
  CHILD_MANAGED_SETTINGS_STATE_KEY,
  'sonosAlarmRefreshRequestIds',
  'receivedGroupCommandRequestIds',
  'ttsVoiceRefreshRequestId',
  'logLevel',
  'updateAvailable',
  'latestVersion',
  'latestManifest',
  'latestReleaseDate',
  'latestReleaseUrl',
  'dismissedUpdateVersion',
  'installedAppVersion',
  'lastUpdateCheck',
  'lastUpdateCheckFormatted',
  'lastVersionCheck',
  'installedVersions',
  'versionMismatches',
  'versionCheckJob',
  'maintenanceJob',
  'pendingDriverMismatches',
  'duplicateCleanupResult',
  'hubCookie',
  'hubCookieTime',
  'updateCheckRequestId',
  'updateCheckStartedAt',
  'updateCheckCookie',
  'updateJustCompleted',
]))

@Field static final Set<String> ACTIVE_APP_ATOMIC_STATE_KEYS = Collections.unmodifiableSet(new HashSet<String>([
]))

@Field static final Set<String> ACTIVE_APP_SETTINGS_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'playerDevices',
  'logLevel',
  'useOldUi',
  'maximumTtsRetention',
  'groupAnnouncementMode',
  'highPriorityAnnouncementMode',
  'queueChimeSeparatelyFromTTS',
  'suppressChimeOnSuccessiveTTS',
  'enableAlarmManagement',
  'enableAlarmSwitches',
  'autoCheckUpdates',
  'autoInstallUpdates',
  'updateCheckFrequency',
  'autoInstallTime',
  'autoInstallNextOccurrence',
  'autoInstallDayOfWeek',
  'hubSecurityUsername',
  'hubSecurityPassword',
  'groupDevices',
  'skipOrphanRemoval',
  'descriptionTextEnable',
  'newUiGroupName',
  'newUiGroupCoordinator',
  'newUiGroupFollowers',
]))
// =============================================================================
// Migration-Prune Helpers — whitelist-driven for-each removal
// =============================================================================
Map<String, Object> pruneOrphanedAppPersistence() {
  Map<String, Object> result = [
    stateRemoved: [], atomicStateRemoved: [], settingsRemoved: []
  ]
  result.stateRemoved = pruneOrphanKeys(state as Map, ACTIVE_APP_STATE_KEYS, 'state')
  result.atomicStateRemoved = pruneOrphanKeys(atomicState as Map, ACTIVE_APP_ATOMIC_STATE_KEYS, 'atomicState')
  result.settingsRemoved = pruneOrphanSettings(ACTIVE_APP_SETTINGS_KEYS)
  Integer total = ((List)result.stateRemoved).size() + ((List)result.atomicStateRemoved).size() + ((List)result.settingsRemoved).size()
  if(total > 0) {
    logDebug("Persistence prune: removed ${total} orphan value(s) (state=${result.stateRemoved}, atomicState=${result.atomicStateRemoved}, settings=${result.settingsRemoved})")
  }
  return result
}

List<String> pruneOrphanKeys(Map persisted, Set<String> active, String label) {
  if(persisted == null || active == null) { return [] }
  List<String> toRemove = new ArrayList<String>(persisted.keySet())
    .findAll { String k -> !active.contains(k) }
  toRemove.each { String k -> persisted.remove(k) }
  return toRemove
}

List<String> pruneOrphanSettings(Set<String> active) {
  if(active == null) { return [] }
  List<String> toRemove = new ArrayList<String>(settings.keySet())
    .findAll { String k -> !active.contains(k) }
  toRemove.each { String k ->
    try { app.removeSetting(k) } catch(Exception ignored) { }
    try { settings.remove(k) } catch(Exception ignored) { }
    try { app.updatedSettings?.remove(k) } catch(Exception ignored) { }
  }
  return toRemove
}

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

Boolean requestSonosPlayerInfo(String ipAddress, String callback, Map callbackData) {
  Map params = [uri: "${getLocalApiPrefix("${ipAddress}:1443")}/players/local/info",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json', contentType: 'application/json', ignoreSSLIssues: true, timeout: 3]
  try {
    asynchttpGet(callback, params, callbackData ?: [ipAddress: ipAddress])
    return true
  } catch(Exception e) {
    logDebug("Could not dispatch player-info request for ${ipAddress}: ${e.message}")
    return false
  }
}

Boolean requestSonosDeviceDescription(String ipAddress, LinkedHashMap playerInfo, String callback, Map callbackData) {
  Map params = [uri: "http://${ipAddress}:1400/xml/device_description.xml", timeout: 3,
    requestContentType: 'application/xml', contentType: 'application/xml']
  Map data = new LinkedHashMap(callbackData ?: [:])
  data.ipAddress = ipAddress
  data.playerInfo = playerInfo
  try {
    asynchttpGet(callback, params, data)
    return true
  } catch(Exception e) {
    logDebug("Could not dispatch device-description request for ${ipAddress}: ${e.message}")
    return false
  }
}

String getLocalUpnpHostForCoordinatorId(String groupCoordinatorId) {
  ChildDeviceWrapper dev = getDeviceFromRincon(groupCoordinatorId)
  return getSonosLocalUpnpHost(dev)
}

String getSonosDeviceIp(DeviceWrapper dev) {
  return dev?.getDataValue('deviceIp')?.toString()?.trim()
}

String formatSonosDeviceIpForUrl(String deviceIp) {
  String normalized = deviceIp?.trim()
  if(!normalized) { return null }
  if(normalized.startsWith('[') && normalized.endsWith(']')) { return normalized }
  return normalized.count(':') > 1 ? "[${normalized}]" : normalized
}

String getSonosLocalUpnpHost(DeviceWrapper dev) {
  String ip = formatSonosDeviceIpForUrl(getSonosDeviceIp(dev))
  return ip ? "${ip}:1400" : null
}

String getSonosLocalUpnpUrl(DeviceWrapper dev) {
  String ip = formatSonosDeviceIpForUrl(getSonosDeviceIp(dev))
  return ip ? "http://${ip}:1400" : null
}

String getSonosLocalApiUrl(DeviceWrapper dev) {
  String ip = formatSonosDeviceIpForUrl(getSonosDeviceIp(dev))
  return ip ? "https://${ip}:1443/api/v1/" : null
}

String getSonosWebsocketUrl(DeviceWrapper dev) {
  String ip = formatSonosDeviceIpForUrl(getSonosDeviceIp(dev))
  return ip ? "wss://${ip}:1443/websocket/api" : null
}


// =============================================================================
// End Getters and Setters
// =============================================================================

// =============================================================================
// App Pages
// =============================================================================
Map mainPage() {
  InstalledAppWrapper discoveryChild = ensureDiscoveryManagementChild()
  String discoveryChildUrl = discoveryChild
      ? "/installedapp/configure/${discoveryChild.getId()}/discoveryPage" : null
  checkForUpdates()
  dynamicPage(title: 'Sonos Advanced Controller', install: true, uninstall: true) {
    section('Management') {
      if(discoveryChildUrl) {
        href(url: discoveryChildUrl, title: 'Sonos Discovery & Management',
            description: 'Open speaker discovery, player management, and group tables', style: 'internal')
      } else {
        paragraph 'The Discovery & Management child app could not be created. Reopen this page to retry.'
      }
    }
    section('Logging') {
      input 'logLevel', 'enum', title: 'Logging level',
        options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'],
        required: false, defaultValue: 'info', submitOnChange: true
    }
    if(state.updateAvailable == true) {
      section('Update Available') {
        paragraph displayNewUiUpdateNotice()
      }
    }
  }
}

InstalledAppWrapper ensureDiscoveryManagementChild() {
  String childAppName = 'Sonos Advanced Controller - Discovery & Management'
  List<InstalledAppWrapper> childApps = getChildApps() as List<InstalledAppWrapper>
  InstalledAppWrapper child = childApps?.find { candidate ->
    candidate.getName() == childAppName
  }
  if(child) { return child }

  try {
    return app.addChildApp('dwinks', childAppName, 'Sonos Discovery & Management')
  } catch(Exception e) {
    logError("Could not create the Discovery & Management child app: ${e.message}")
    return null
  }
}

String newUiButtonLink(String buttonName, String linkText, String color = '#1A77C9', String font = '15px', String ariaLabel = null, String cssClass = '', String clickHandler = null) {
  String safeAriaLabel = ariaLabel ? " aria-label='${escapeNewUiHtml(ariaLabel)}'" : ''
  String safeCssClass = cssClass ? " ${escapeNewUiHtml(cssClass)}" : ''
  String action = clickHandler ?: 'buttonClick(this)'
  return "<div class='form-group'><input type='hidden' name='${buttonName}.type' value='button'></div>" +
      "<div style='display:inline-block'><div class='submitOnChange${safeCssClass}' role='button' tabindex='0'${safeAriaLabel} onclick=\"${action}\" onkeydown=\"if(event.key==='Enter'||event.key===' '){event.preventDefault();${action}}\" style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
      "<input type='hidden' name='settings[${buttonName}]' value=''>"
}

String escapeNewUiHtml(Object value) {
  return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value?.toString())).replace("'", '&#39;')
}

void appButtonHandler(String buttonName) {
  if(buttonName == 'btnInstallUpdate') { installUpdate(); return }
  if(buttonName == 'btnDismissUpdate') {
    String dismissedVersion = state.latestVersion?.toString() ?: state.latestManifest?.version?.toString()
    if(dismissedVersion) { state.dismissedUpdateVersion = dismissedVersion }
    state.updateAvailable = false
    state.latestVersion = null
    state.latestManifest = null
    state.latestReleaseDate = null
    state.latestReleaseUrl = null
    app.updateLabel('Sonos Advanced Controller')
    app.sendEvent(name: 'newUiUpdateNotice', value: 'dismissed')
  }
}

Map getLegacyDiscoveryState() {
  Object secondaries = state[LEGACY_DISCOVERY_SECONDARIES_STATE_KEY]
  return [secondaries: secondaries instanceof Map ? new LinkedHashMap((Map)secondaries) : [:],
    playerDevices: settings.playerDevices instanceof Collection ? new ArrayList(settings.playerDevices) : settings.playerDevices,
    groupConfigurations: state.userGroups instanceof Map ? new LinkedHashMap((Map)state.userGroups) : [:]]
}

/** Provide pre-migration preferences to the management child before parent cleanup. */
Map getChildManagedSettingsForMigration() {
  Map<String, Object> legacy = [:]
  CHILD_MANAGED_SETTING_NAMES.each { String key ->
    if(settings[key] != null) { legacy[key] = settings[key] }
  }
  return legacy
}

/** Commit child-owned preferences to runtime state and remove their parent preference entries. */
Map acknowledgeChildManagedSettings(Map values) {
  Map<String, Object> managed = state[CHILD_MANAGED_SETTINGS_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Object>((Map<String, Object>)state[CHILD_MANAGED_SETTINGS_STATE_KEY]) : [:]
  CHILD_MANAGED_SETTING_NAMES.each { String key ->
    if(values?.containsKey(key)) { managed[key] = values[key] }
    else if(settings[key] != null) { managed[key] = settings[key] }
  }
  managed.remove('libraryIdSMAPI')
  managed.remove('libraryIdUtilities')
  state[CHILD_MANAGED_SETTINGS_STATE_KEY] = managed
  CHILD_MANAGED_SETTING_NAMES.each { String key -> removeAppSetting(key) }
  removeAppSetting('libraryIdSMAPI')
  removeAppSetting('libraryIdUtilities')
  List<String> staleManagementInputs = new ArrayList<String>(settings.keySet()).collect { Object key -> key?.toString() }
      .findAll { String key -> key && (key.startsWith('newUi') || key.startsWith('btnNewUi') ||
          key.startsWith('btnConfirmNewUi') || key.startsWith('btnCancelNewUi') ||
          key in ['btnExtend60', 'btnExtend300', 'createSelectedPlayers', 'removeUnselectedPlayers',
            'createPlayerDevices', 'saveManagedGroup', 'deleteManagedGroup', 'cancelManagedGroup']) }
  staleManagementInputs.each { String key -> removeAppSetting(key) }
  return [accepted: true]
}

void updateChildManagedSettings(Map values) {
  Map<String, Object> managed = state[CHILD_MANAGED_SETTINGS_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Object>((Map<String, Object>)state[CHILD_MANAGED_SETTINGS_STATE_KEY]) : [:]
  CHILD_MANAGED_SETTING_NAMES.each { String key ->
    if(values?.containsKey(key)) { managed[key] = values[key] }
  }
  state[CHILD_MANAGED_SETTINGS_STATE_KEY] = managed
  runIn(1, 'applyChildManagedSettings', [overwrite: true])
}

void applyChildManagedSettings() {
  try { configureAlarmManagement() }
  catch(Exception e) { logError("Alarm management settings update failed: ${e.message}") }
  refreshPlayerAnnouncementPolicySnapshots(false)
  scheduleUpdateCheck()
  checkForUpdates()
}

/** Receive a complete player preference snapshot after the child releases its device gate. */
void playerPreferencesUpdated(Map payload) {
  String dni = payload?.deviceDni?.toString()
  ChildDeviceWrapper player = dni ? getChildDevice(dni) : null
  if(player == null || !player.getDataValue('id')) { return }
  Map<String, Object> preferences = normalizePlayerPreferenceSnapshot(
      payload?.preferences instanceof Map ? (Map)payload.preferences : [:])
  Map<String, Map<String, Object>> snapshots = state[PLAYER_PREFERENCES_BY_DNI_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map<String, Object>>((Map<String, Map<String, Object>>)state[PLAYER_PREFERENCES_BY_DNI_STATE_KEY]) : [:]
  snapshots[dni] = preferences
  state[PLAYER_PREFERENCES_BY_DNI_STATE_KEY] = snapshots
}

/** Receive a complete group preference snapshot after the child releases its device gate. */
void groupPreferencesUpdated(Map payload) {
  String dni = payload?.deviceDni?.toString()
  ChildDeviceWrapper groupDevice = dni ? getChildDevice(dni) : null
  if(groupDevice == null || groupDevice.getDataValue('id') ||
      (!dni.contains(GROUP_DEVICE_DNI_MARKER) && groupDevice.getDataValue('groupCoordinatorId') == null)) { return }
  Map<String, Object> preferences = normalizeGroupPreferenceSnapshot(
      payload?.preferences instanceof Map ? (Map)payload.preferences : [:])
  Map<String, Map<String, Object>> snapshots = state[GROUP_PREFERENCES_BY_DNI_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map<String, Object>>((Map<String, Map<String, Object>>)state[GROUP_PREFERENCES_BY_DNI_STATE_KEY]) : [:]
  Boolean volumeChanged = snapshots[dni]?.useProportionalVolume != preferences.useProportionalVolume ||
      snapshots[dni]?.controlUngroupedIndividually != preferences.controlUngroupedIndividually
  snapshots[dni] = preferences
  state[GROUP_PREFERENCES_BY_DNI_STATE_KEY] = snapshots
  if(volumeChanged) { queueVolumeRecalculation([], [dni], false) }
}

Map<String, Object> normalizePlayerPreferenceSnapshot(Map values) {
  Integer boost = toBigDecimal(values?.ttsBoostAmount)?.intValue()
  if(!(boost in [-10, -5, 0, 5, 10, 15, 20])) { boost = 10 }
  return [
    disableAlbumArtEvents: toBooleanPreference(values?.disableAlbumArtEvents, true),
    disableArtistAlbumTrackEvents: toBooleanPreference(values?.disableArtistAlbumTrackEvents, false),
    disableTrackDataEvents: toBooleanPreference(values?.disableTrackDataEvents, true),
    enableTrackPositionUpdates: toBooleanPreference(values?.enableTrackPositionUpdates, false),
    includeTrackDataMetaData: toBooleanPreference(values?.includeTrackDataMetaData, false),
    chimeBeforeHighPriorityTTS: toBooleanPreference(values?.chimeBeforeHighPriorityTTS, false),
    chimeBeforeStandardPriorityTTS: toBooleanPreference(values?.chimeBeforeStandardPriorityTTS, false),
    ttsBoostAmount: boost
  ]
}

Map<String, Object> normalizeGroupPreferenceSnapshot(Map values) {
  return [
    chimeBeforeStandardPriorityTTS: toBooleanPreference(values?.chimeBeforeStandardPriorityTTS, false),
    chimeBeforeHighPriorityTTS: toBooleanPreference(values?.chimeBeforeHighPriorityTTS, false),
    disableAlbumArtEvents: toBooleanPreference(values?.disableAlbumArtEvents, true),
    enableTrackPositionUpdates: toBooleanPreference(values?.enableTrackPositionUpdates, false),
    useProportionalVolume: toBooleanPreference(values?.useProportionalVolume, true),
    controlUngroupedIndividually: toBooleanPreference(values?.controlUngroupedIndividually, false)
  ]
}

Boolean toBooleanPreference(Object raw, Boolean defaultValue) {
  if(raw == null) { return defaultValue }
  if(raw instanceof Boolean) { return (Boolean)raw }
  String normalized = raw.toString().trim().toLowerCase(Locale.ROOT)
  if(normalized in ['true', '1', 'on', 'yes']) { return true }
  if(normalized in ['false', '0', 'off', 'no']) { return false }
  return defaultValue
}

Map<String, Object> getPlayerPreferenceSnapshot(ChildDeviceWrapper player) {
  String dni = player?.getDeviceNetworkId()?.toString()
  Map snapshots = state[PLAYER_PREFERENCES_BY_DNI_STATE_KEY] instanceof Map
      ? (Map)state[PLAYER_PREFERENCES_BY_DNI_STATE_KEY] : [:]
  return snapshots[dni] instanceof Map ? (Map<String, Object>)snapshots[dni] : [:]
}

Map<String, Object> getGroupPreferenceSnapshot(ChildDeviceWrapper groupDevice) {
  String dni = groupDevice?.getDeviceNetworkId()?.toString()
  Map snapshots = state[GROUP_PREFERENCES_BY_DNI_STATE_KEY] instanceof Map
      ? (Map)state[GROUP_PREFERENCES_BY_DNI_STATE_KEY] : [:]
  return snapshots[dni] instanceof Map ? (Map<String, Object>)snapshots[dni] : [:]
}

Object getPlayerPreference(ChildDeviceWrapper player, String key, Object defaultValue = null) {
  Map<String, Object> snapshot = getPlayerPreferenceSnapshot(player)
  return snapshot.containsKey(key) ? snapshot[key] : defaultValue
}

Object getGroupPreference(ChildDeviceWrapper groupDevice, String key, Object defaultValue = null) {
  Map<String, Object> snapshot = getGroupPreferenceSnapshot(groupDevice)
  return snapshot.containsKey(key) ? snapshot[key] : defaultValue
}

void removeAppSetting(String key) {
  try { app.removeSetting(key) } catch(Exception ignored) { }
  try { settings.remove(key) } catch(Exception ignored) { }
  try { app.updatedSettings?.remove(key) } catch(Exception ignored) { }
}

Object getAppPreference(String key, Object defaultValue = null) {
  Map managed = state[CHILD_MANAGED_SETTINGS_STATE_KEY] instanceof Map
      ? (Map)state[CHILD_MANAGED_SETTINGS_STATE_KEY] : [:]
  return managed.containsKey(key) ? managed[key] : (settings[key] != null ? settings[key] : defaultValue)
}

void acknowledgeLegacyDiscoveryState() {
  state.remove(LEGACY_DISCOVERY_SECONDARIES_STATE_KEY)
  state.remove('userGroups')
  ['playerDevices', 'groupDevices', 'newGroupName', 'newGroupCoordinator',
    'newGroupPlayers', 'editDeleteGroup'].each { String settingName ->
    try { app.removeSetting(settingName) } catch(Exception ignored) { }
    try { settings.remove(settingName) } catch(Exception ignored) { }
    try { app.updatedSettings?.remove(settingName) } catch(Exception ignored) { }
  }
}

Map registerDiscoveryManagementChild(Object childInstanceId) {
  String childId = childInstanceId?.toString()
  if(!childId || !(childId ==~ /[0-9]{1,12}/)) { return [accepted: false, owner: false] }
  String ownerId = state[DISCOVERY_MANAGEMENT_CHILD_ID_STATE_KEY]?.toString()
  if(!ownerId) {
    state[DISCOVERY_MANAGEMENT_CHILD_ID_STATE_KEY] = childId
    ownerId = childId
  }
  return [accepted: true, owner: ownerId == childId, ownerId: ownerId]
}

void unregisterDiscoveryManagementChild(Object childInstanceId) {
  if(state[DISCOVERY_MANAGEMENT_CHILD_ID_STATE_KEY]?.toString() == childInstanceId?.toString()) {
    state.remove(DISCOVERY_MANAGEMENT_CHILD_ID_STATE_KEY)
  }
}

Map getProvisioningSnapshot() {
  Map<String, String> players = [:]
  Map<String, String> playerNames = [:]
  List<Map<String, Object>> playerRows = []
  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    String dni = player.getDeviceNetworkId()?.toString()
    if(dni) {
      players[dni] = player.getDataValue('name')?.toString() ?: player.label?.toString() ?: dni
      String playerId = player.getDataValue('id')?.toString() ?: dni
      playerNames[playerId] = players[dni]
      List<String> secondaryIds = splitTokens(player.getDataValue('secondaryIds')?.toString(), ',')
      List<String> secondaryIps = splitTokens(player.getDataValue('secondaryDeviceIps')?.toString(), ',')
      List<Map<String, Object>> secondaries = []
      for(Integer index = 0; index < secondaryIds.size(); index++) {
        secondaries.add([id: secondaryIds[index],
          deviceIp: index < secondaryIps.size() ? secondaryIps[index] : null])
      }
      playerRows.add([deviceNetworkId: dni,
        deviceId: player.id?.toString(),
        id: player.getDataValue('id')?.toString() ?: dni,
        name: players[dni],
        model: (player.getDataValue('modelDisplayName') ?: player.getDataValue('modelName'))?.toString(),
        ip: getSonosDeviceIp(player),
        secondaries: secondaries])
    }
  }
  List<Map<String, Object>> groupRows = []
  getCurrentGroupDevices().each { ChildDeviceWrapper group ->
    String dni = group.getDeviceNetworkId()?.toString()
    String name = extractGroupNameFromDni(dni)
    if(name) {
      groupRows.add([name: name,
        deviceId: group.id?.toString(),
        coordinatorId: group.getDataValue('groupCoordinatorId')?.toString(),
        followerIds: splitTokens(group.getDataValue('playerIds')?.toString(), ',')])
    }
  }
  return [players: players, playerNames: playerNames, playerRows: playerRows, groups: groupRows]
}

Map submitGroupConfigurationSync(Map request) {
  if(request?.schemaVersion != 1 || !request.requestId || !(request.groups instanceof Map)) {
    return [accepted: false, error: 'invalid_request']
  }
  Map groups = (Map)request.groups
  if(groups.size() > 64 || request.requestId.toString().length() > 80) {
    return [accepted: false, error: 'request_too_large']
  }
  Map<String, Map> validated = [:]
  groups.each { Object rawName, Object rawDefinition ->
    String name = rawName?.toString()?.trim()
    if(!name || name.length() > 128 || !(rawDefinition instanceof Map)) { return }
    Map definition = (Map)rawDefinition
    String coordinatorId = definition.groupCoordinatorId?.toString()
    List<String> followers = normalizeGroupPlayerIds(definition.playerIds)
    if(coordinatorId && !followers.isEmpty() && coordinatorId.length() <= 128 &&
        followers.size() <= 64 && followers.every { String id -> id.length() <= 128 && id ==~ /[A-Za-z0-9:._-]+/ }) {
      validated[name] = [groupCoordinatorId: coordinatorId, playerIds: followers]
    }
  }
  if(validated.size() != groups.size()) { return [accepted: false, error: 'invalid_group_data'] }
  String requestId = request.requestId.toString()
  Map queue = state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY]) : [:]
  if(queue.containsKey(requestId)) { return [accepted: true, requestId: requestId] }
  if(queue.size() >= 8) { return [accepted: false, error: 'queue_full'] }
  queue[requestId] = [groups: validated]
  state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY] = queue
  runIn(1, 'drainGroupConfigurationRequests', [overwrite: false])
  return [accepted: true, requestId: requestId]
}

Map submitGroupManagementRequest(Map request) {
  if(request?.schemaVersion != 1 || !request.requestId ||
      !(request.action in ['upsert', 'delete'])) {
    return [accepted: false, error: 'invalid_request']
  }
  String requestId = request.requestId.toString()
  if(requestId.length() > 80) { return [accepted: false, error: 'invalid_request'] }
  Map queue = state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY]) : [:]
  if(queue.containsKey(requestId)) { return [accepted: true, requestId: requestId] }
  if(queue.size() >= 8) { return [accepted: false, error: 'queue_full'] }
  String name = request.name?.toString()?.trim()
  String oldName = request.oldName?.toString()?.trim()
  if(!name || name.length() > 128 || (oldName && oldName.length() > 128)) {
    return [accepted: false, error: 'invalid_group_name']
  }
  Map queued = [action: request.action.toString(), name: name, oldName: oldName]
  if(request.action == 'upsert') {
    String coordinatorId = request.groupCoordinatorId?.toString()
    List<String> followers = normalizeGroupPlayerIds(request.playerIds)
    Set<String> installedPlayers = getCurrentPlayerDevices().collect { ChildDeviceWrapper player ->
      player.getDataValue('id')?.toString()
    }.findAll { String id -> id } as Set<String>
    if(!coordinatorId || !installedPlayers.contains(coordinatorId) || followers.isEmpty() ||
        followers.size() > 64 || followers.contains(coordinatorId) ||
        followers.any { String id -> !installedPlayers.contains(id) }) {
      return [accepted: false, error: 'invalid_players']
    }
    queued.groupCoordinatorId = coordinatorId
    queued.playerIds = followers.unique()
  }
  queue[requestId] = queued
  state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY] = queue
  runIn(1, 'drainGroupConfigurationRequests', [overwrite: false])
  return [accepted: true, requestId: requestId]
}

Map getGroupManagementResult(String requestId) {
  if(!requestId || requestId.length() > 80) { return [status: 'invalid_request'] }
  Map results = state[GROUP_MANAGEMENT_RESULTS_KEY] instanceof Map
      ? (Map)state[GROUP_MANAGEMENT_RESULTS_KEY] : [:]
  Map result = results[requestId] instanceof Map ? (Map)results[requestId] : null
  return result ? new LinkedHashMap(result) : [status: 'pending']
}

void drainGroupConfigurationRequests() {
  Map queue = state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY]) : [:]
  if(queue.isEmpty()) { return }
  String requestId = queue.keySet().iterator().next().toString()
  Map request = (Map)queue[requestId]
  queue.remove(requestId)
  state[PENDING_GROUP_CONFIGURATION_REQUESTS_KEY] = queue
  try {
    if(request.action == 'delete') {
      String oldName = request.oldName?.toString()
      String dni = "${app.id}-SonosGroupDevice-${oldName ?: request.name}"
      if(app.getChildDevice(dni)) { app.deleteChildDevice(dni) }
    } else if(request.action == 'upsert') {
      String oldName = request.oldName?.toString()
      String name = request.name?.toString()
      if(oldName && oldName != name) {
        String oldDni = "${app.id}-SonosGroupDevice-${oldName}"
        ChildDeviceWrapper existing = app.getChildDevice(oldDni)
        if(existing) {
          existing.setDeviceNetworkId("${app.id}-SonosGroupDevice-${name}")
          existing.setLabel("Sonos Group: ${name}")
        }
      }
      upsertGroupDevice(name, request)
      String dni = "${app.id}-SonosGroupDevice-${name}"
      DeviceWrapper createdDevice = app.getChildDevice(dni)
      recordGroupManagementResult(requestId, [status: createdDevice ? 'created' : 'failed',
        name: name, deviceId: createdDevice?.getId()?.toString()])
    } else {
      Map groups = request.groups instanceof Map ? (Map)request.groups : [:]
      groups.each { String name, Map definition -> upsertGroupDevice(name, definition) }
    }
    runIn(1, 'subscribeToGroupCommandRequests')
  } catch(Exception e) {
    logError("Group configuration sync ${requestId} failed: ${e.message}")
    if(request.action == 'upsert') {
      recordGroupManagementResult(requestId, [status: 'failed', name: request.name?.toString()])
    }
  }
  if(queue) { runIn(1, 'drainGroupConfigurationRequests', [overwrite: false]) }
}

void recordGroupManagementResult(String requestId, Map result) {
  Map results = state[GROUP_MANAGEMENT_RESULTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[GROUP_MANAGEMENT_RESULTS_KEY]) : [:]
  results[requestId] = new LinkedHashMap(result) + [completedAt: now()]
  while(results.size() > 16) { results.remove(results.keySet().iterator().next()) }
  state[GROUP_MANAGEMENT_RESULTS_KEY] = results
}

Map submitPlayerProvisioningRequest(Map request) {
  if(request?.schemaVersion != 1 || !request.requestId || !(request.selected instanceof Collection) ||
      !(request.players instanceof Map) || !(request.secondaries instanceof Map)) {
    return [accepted: false, error: 'invalid_request']
  }
  List<String> selected = ((Collection)request.selected).collect { Object value -> value?.toString() }
      .findAll { String value -> value } .unique()
  Map players = (Map)request.players
  Map secondaries = (Map)request.secondaries
  if(players.size() > 64 || secondaries.size() > 128 || request.requestId.toString().length() > 80) {
    return [accepted: false, error: 'request_too_large']
  }
  if(selected.isEmpty() || selected.size() > 64 ||
      selected.any { String dni -> dni.length() > 128 || !(dni ==~ /[A-Za-z0-9:._-]+/) }) {
    return [accepted: false, error: 'invalid_selection']
  }
  Set<String> allowedFields = ['id', 'name', 'deviceIp', 'swGen', 'capabilities', 'modelName', 'householdId', 'topologyInfo'] as Set<String>
  Map<String, Map> validatedPlayers = [:]
  selected.each { String dni ->
    Object raw = players[dni]
    if(!(raw instanceof Map)) { return }
    Map player = (Map)raw
    List<String> required = ['id', 'deviceIp']
    Map<String, Object> detachedPlayer = detachProvisioningMap(player, allowedFields)
    if(required.every { String key -> detachedPlayer[key] != null && detachedPlayer[key].toString().trim() }) {
      validatedPlayers[dni] = detachedPlayer
    }
  }
  if(validatedPlayers.size() != selected.size()) { return [accepted: false, error: 'invalid_player_data'] }
  Set<String> allowedSecondaryFields = ['id', 'primaryDeviceId', 'deviceIp', 'swGen', 'capabilities', 'modelName', 'modelDisplayName',
      'householdId', 'topologyInfo'] as Set<String>
  Map<String, Map> validatedSecondaries = [:]
  secondaries.each { Object key, Object raw ->
    if(key instanceof String && raw instanceof Map && ((Map)raw).id && ((Map)raw).primaryDeviceId) {
      validatedSecondaries[key.toString()] = detachProvisioningMap((Map)raw, allowedSecondaryFields)
    }
  }
  if(validatedSecondaries.size() != secondaries.size()) { return [accepted: false, error: 'invalid_secondary_data'] }
  Map<String, Object> queue = state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY]) : [:]
  String requestId = request.requestId.toString()
  if(queue.containsKey(requestId) || ((Map)(state[PLAYER_PROVISIONING_REQUEST_STATUS_KEY] ?: [:])).containsKey(requestId)) {
    return [accepted: true, requestId: requestId]
  }
  if(queue.size() >= 8) { return [accepted: false, error: 'queue_full'] }
  queue[requestId] = [selected: selected, players: validatedPlayers, secondaries: validatedSecondaries]
  state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] = queue
  recordPlayerProvisioningStatus(requestId, 'queued')
  runIn(1, 'drainPlayerProvisioningRequests', [overwrite: false])
  return [accepted: true, requestId: requestId]
}

Map<String, Object> detachProvisioningMap(Map source, Set<String> allowedFields) {
  Map<String, Object> detached = [:]
  source.each { Object field, Object value ->
    if(field instanceof String && allowedFields.contains(field.toString()) && value != null) {
      Object copied = detachProvisioningValue(value, 0)
      if(copied != null) { detached[field.toString()] = copied }
    }
  }
  return detached
}

Object detachProvisioningValue(Object value, Integer depth) {
  if(value == null) { return null }
  if(value instanceof String) { return value.toString().length() <= 4096 ? value.toString() : null }
  if(value instanceof Number || value instanceof Boolean) { return value }
  if(depth >= 3) { return null }
  if(value instanceof Map && ((Map)value).size() <= 64) {
    Map<String, Object> detached = [:]
    ((Map)value).each { Object key, Object nested ->
      if(key instanceof String) {
        Object copied = detachProvisioningValue(nested, depth + 1)
        if(copied != null) { detached[key.toString()] = copied }
      }
    }
    return detached
  }
  if(value instanceof Collection && ((Collection)value).size() <= 128) {
    List detached = []
    ((Collection)value).each { Object nested ->
      Object copied = detachProvisioningValue(nested, depth + 1)
      if(copied != null) { detached.add(copied) }
    }
    return detached
  }
  return null
}

Map submitPlayerRemovalRequest(Map request) {
  if(request?.schemaVersion != 1 || !request.requestId || !(request.remove instanceof Collection)) {
    return [accepted: false, error: 'invalid_request']
  }
  List<String> requested = ((Collection)request.remove).collect { Object value -> value?.toString() }.findAll { String value -> value }.unique()
  if(requested.size() > 64 || request.requestId.toString().length() > 80) { return [accepted: false, error: 'invalid_request'] }
  Set<String> installed = getCurrentPlayerDevices().collect { ChildDeviceWrapper player ->
    player.getDeviceNetworkId()?.toString()
  }.findAll { String dni -> dni } as Set<String>
  List<String> remove = requested.findAll { String dni -> installed.contains(dni) }
  if(remove.size() != requested.size()) { return [accepted: false, error: 'unknown_device'] }
  String requestId = request.requestId.toString()
  Map queue = state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY]) : [:]
  if(queue.containsKey(requestId) || ((Map)(state[PLAYER_PROVISIONING_REQUEST_STATUS_KEY] ?: [:])).containsKey(requestId)) {
    return [accepted: true, requestId: requestId]
  }
  if(queue.size() >= 8) { return [accepted: false, error: 'queue_full'] }
  queue[requestId] = [remove: remove]
  state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] = queue
  recordPlayerProvisioningStatus(requestId, 'queued')
  runIn(1, 'drainPlayerProvisioningRequests', [overwrite: false])
  return [accepted: true, requestId: requestId]
}

void recordPlayerProvisioningStatus(String requestId, String status) {
  Map statuses = state[PLAYER_PROVISIONING_REQUEST_STATUS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PLAYER_PROVISIONING_REQUEST_STATUS_KEY]) : [:]
  statuses[requestId] = [status: status, updatedAt: now()]
  while(statuses.size() > 20) { statuses.remove(statuses.keySet().iterator().next()) }
  state[PLAYER_PROVISIONING_REQUEST_STATUS_KEY] = statuses
}

void drainPlayerProvisioningRequests() {
  Map queue = state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] instanceof Map
      ? new LinkedHashMap((Map)state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY]) : [:]
  if(queue.isEmpty()) { return }
  String requestId = queue.keySet().iterator().next().toString()
  Map request = (Map)queue[requestId]
  queue.remove(requestId)
  state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] = queue
  recordPlayerProvisioningStatus(requestId, 'processing')
  try {
    if(request.remove instanceof Collection) {
      ((Collection)request.remove).each { Object rawDni ->
        String dni = rawDni?.toString()
        ChildDeviceWrapper player = dni ? app.getChildDevice(dni) : null
        if(player && player.getDataValue('id')) { app.deleteChildDevice(dni) }
      }
      refreshStaticDriverActivePlayerRoster()
      runIn(1, 'subscribeToGroupCommandRequests')
    } else {
      Integer created = createPlayerDevicesFromProvisioning((List<String>)request.selected,
          (Map<String, Map>)request.players, (Map<String, Map>)request.secondaries)
      runIn(3, 'finalizeProvisionedPlayerCreation', [data: [requestId: requestId, created: created]])
    }
    recordPlayerProvisioningStatus(requestId, 'complete')
  } catch(Exception e) {
    recordPlayerProvisioningStatus(requestId, 'failed')
    logError("Player management request ${requestId} failed: ${e.message}")
  }
  if(queue) { runIn(1, 'drainPlayerProvisioningRequests', [overwrite: false]) }
}

void installed() { initialize() }
void updated() { configure(false) }
void initialize() { configure(true) }

@Field static final String STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY = 'staticDriverActivePlayerRoster'
@Field static final Integer STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_VERSION = 1
@Field static final String ANNOUNCEMENT_POLICY_SETTINGS_STATE_KEY = 'announcementPolicySettings'

/** Return the player roster published during app lifecycle/device changes. */
Map<String, Object> getStaticDriverActivePlayerRoster() {
  Object cachedRoster = state[STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY]
  if(!(cachedRoster instanceof Map)) { return null }
  return new LinkedHashMap<String, Object>((Map<String, Object>)cachedRoster)
}

/** Record each group device's coordinator RINCON so AVT delivery can address group devices by DNI alone. */
void refreshGroupDeviceCoordinators(List<ChildDeviceWrapper> children = null) {
  Map<String, String> registry = [:]
  (children ?: app.getChildDevices() ?: []).each { ChildDeviceWrapper child ->
    String coordinatorId = child.getDataValue('groupCoordinatorId')
    String dni = child.getDeviceNetworkId()
    if(child.getDataValue('id') == null && coordinatorId && dni) { registry[dni] = coordinatorId }
  }
  state[GROUP_DEVICE_COORDINATORS_STATE_KEY] = registry
}

/** Refresh the driver cleanup snapshot after child-device changes. */
void refreshStaticDriverActivePlayerRoster(Boolean refreshPoliciesWhenRosterChanges = true) {
  try {
    Map<String, Object> previousRoster = getStaticDriverActivePlayerRoster()
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
    refreshGroupDeviceCoordinators(children)
    Long refreshedAt = (now() / 1000L) as Long
    state[STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY] = [
      version: STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_VERSION,
      refreshedAt: refreshedAt,
      players: roster
    ]
    Boolean rosterChanged = previousRoster?.players != roster
    String serializedRoster = new JsonBuilder(state[STATIC_DRIVER_ACTIVE_PLAYER_ROSTER_STATE_KEY]).toString()
    children.each { ChildDeviceWrapper child ->
      if(child.getDeviceNetworkId() && child.getDataValue('id')) {
        child.updateDataValue('staticDriverActivePlayerRoster', serializedRoster)
      }
    }
    if(rosterChanged && refreshPoliciesWhenRosterChanges) {
      Set<String> previousDnis = ((List<Map>)previousRoster?.players ?: []).collect { Map row ->
        row?.dni?.toString()
      }.findAll { String dni -> dni } as Set<String>
      roster.findAll { Map<String, String> row -> !previousDnis.contains(row.dni) }.each { Map<String, String> row ->
        ChildDeviceWrapper addedPlayer = getChildDevice(row.dni)
        refreshPlayerAnnouncementPolicySnapshot(addedPlayer)
      }
    }
  } catch(Exception e) {
    logWarn("Could not refresh active player roster cache: ${e.message}")
  }
}

void configure(Boolean startup = false) {
  applyLogLevelChange()
  synchronizeDiscoveryChildLogLevel()
  logInfo("${app.name} updated")
  // Prune retired parent keys after management state has moved to the child app.
  Map<String, Object> pruneResult = pruneOrphanedAppPersistence()
  // Log summary is already emitted by prune; keep reference for debugging.
  logDebug("App persistence prune completed: ${pruneResult}")
  unsubscribe()
  unschedule('drainAnnouncementPolicyRefreshQueue')
  subscribe(location, 'systemStart', 'systemStartHandler')
  scheduleUpdateCheck()
  checkForUpdates()

  // Initialize settings with defaults
  if(settings.skipOrphanRemoval == null) { settings.skipOrphanRemoval = false }
  if(settings.descriptionTextEnable == null) { settings.descriptionTextEnable = true }

  try { createGroupDevices() }
  catch (Exception e) { logError("createGroupDevices() Failed: ${e}")}
  try { configureAlarmManagement() }
  catch (Exception e) { logError("configureAlarmManagement() Failed: ${e}") }
  refreshStaticDriverActivePlayerRoster(false)
  runIn(2, 'seedGroupPreferenceSnapshots', [overwrite: true])
  refreshPlayerAnnouncementPolicySnapshots(startup)
  unschedule('refreshStaticDriverActivePlayerRoster')
  schedule('0 */5 * * * ?', 'refreshStaticDriverActivePlayerRoster')
  subscribeToGroupCommandRequests()
  Map activeGroupOperation = getActiveGroupOperation()
  if(activeGroupOperation != null) {
    refreshGroupOperationTopology([operationId: activeGroupOperation.operationId])
    runIn(1, 'advanceGroupTopologyOperation', [
      overwrite: true,
      data: [operationId: activeGroupOperation.operationId]
    ])
  }
  if(state[GROUP_COMMAND_QUEUE_STATE_KEY] instanceof Map) {
    runIn(1, 'drainGroupCommandRequests', [overwrite: true])
  }
  if(state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY] instanceof Map && !((Map)state[PENDING_PLAYER_PROVISIONING_REQUESTS_KEY]).isEmpty()) {
    runIn(1, 'drainPlayerProvisioningRequests', [overwrite: false])
  }

  unschedule('appGetFavoritesLocal')

  // Schedule TTS voice cache refresh: 30s delay for hub readiness, then daily at 3 AM
  unschedule('refreshTTSVoiceCache')
  runIn(30, 'refreshTTSVoiceCache')
  schedule('0 0 3 * * ?', 'refreshTTSVoiceCache')

  // Schedule daily favorites/playlists refresh at 3:05 AM
  unschedule('refreshFavoritesAndPlaylists')
  schedule('0 5 3 * * ?', 'refreshFavoritesAndPlaylists')
  rearmPlayerStateUpdateDrainAfterRestart()
}

/** Keep discovery diagnostics under the parent app's logging preference. */
void synchronizeDiscoveryChildLogLevel() {
  String selectedLevel = getConfiguredLogLevel()
  List<InstalledAppWrapper> children = getChildApps() as List<InstalledAppWrapper>
  InstalledAppWrapper discoveryChild = children?.find { InstalledAppWrapper candidate ->
    candidate.getName() == 'Sonos Advanced Controller - Discovery & Management'
  }
  if(discoveryChild && discoveryChild.getSetting('logLevel')?.toString() != selectedLevel) {
    discoveryChild.updateSetting('logLevel', [type: 'enum', value: selectedLevel])
  }
}

void systemStartHandler(Object event = null) {
  // Players re-subscribe after boot and Sonos sends a full AVT on subscribe, so the cache refills itself.
  state.remove(AVT_EVENTS_BY_DNI_STATE_KEY)
  rearmPlayerStateUpdateDrainAfterRestart()
}

/** A reboot can discard the scheduled drain while persisting its state flag. */
void rearmPlayerStateUpdateDrainAfterRestart() {
  state[PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY] = false
  state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = false
  state.remove(PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY)
  scheduleQueuedPlayerStateUpdateDrain(25)
}


void groupAnnouncementProgress(String correlationId, String phase, Map payload, String dni) {
  if(!correlationId || !phase) { return }
  List<Map> q = state[GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY] instanceof List ? new ArrayList<Map>((List<Map>)state[GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY]) : []
  q.add([correlationId: correlationId, phase: phase, payload: new LinkedHashMap(payload ?: [:]), dni: dni, at: now()])
  if(q.size() > GROUP_ANNOUNCEMENT_PROGRESS_MAX) {
    q.remove(0)
    logWarn("Group announcement progress queue overflow; dropped oldest phase (max=${GROUP_ANNOUNCEMENT_PROGRESS_MAX})")
  }
  state[GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY] = q
  Long dueAt = state[GROUP_ANNOUNCEMENT_DUE_AT_STATE_KEY] as Long
  if(dueAt == null || dueAt < now()) {
    dueAt = now() + GROUP_ANNOUNCEMENT_DEBOUNCE_MS as Long
    state[GROUP_ANNOUNCEMENT_DUE_AT_STATE_KEY] = dueAt
  }
  Long remain = dueAt - now()
  if(remain < 1L) { remain = 1L }
  Integer delay = (remain / 1000L).toInteger()
  if(delay < 1) { delay = 1 }
  try { runIn(delay, 'drainGroupAnnouncementProgress', [overwrite: true]) } catch(Exception e) { logWarn("Could not schedule group announcement drain: ${e.message}") }
}
void drainGroupAnnouncementProgress() {
  Long dueAt = state[GROUP_ANNOUNCEMENT_DUE_AT_STATE_KEY] as Long
  if(dueAt != null && dueAt > now()) {
    // debounce not yet at hard deadline
    try { runIn(1, 'drainGroupAnnouncementProgress', [overwrite: true]) } catch(Exception ignored) { }
    return
  }
  List<Map> q = state[GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY] instanceof List ? new ArrayList<Map>((List<Map>)state[GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY]) : []
  state.remove(GROUP_ANNOUNCEMENT_PROGRESS_QUEUE_STATE_KEY)
  state.remove(GROUP_ANNOUNCEMENT_DUE_AT_STATE_KEY)
  if(q.isEmpty()) { return }
  q.each { Map item ->
    ChildDeviceWrapper player = getChildDevice(item.dni?.toString())
    if(!player) { player = getCurrentPlayerDevices().find { ChildDeviceWrapper d -> d.getDeviceNetworkId()?.toString() == item.dni?.toString() } }
    if(player) { handleGroupAnnouncementProgress(player, item.payload ?: item) }
  }
}


/** Clear stale track positions from groups when that feature is disabled, from the app event path. */





List<String> getGroupFollowerDNIsForCoordinator(String coordinatorId) {
  if(!coordinatorId) { return [] }
  List<String> out = []
  getCurrentPlayerDevices().each { ChildDeviceWrapper p ->
    String dni = p.getDeviceNetworkId()?.toString()
    String coord = p.currentValue('groupCoordinatorId', true)?.toString()
    if(coord == coordinatorId && dni) { out.add(dni) }
  }
  return out
}

void handlePolledPlayerRequest(ChildDeviceWrapper player, Map request) {
  String dni = player.getDeviceNetworkId()?.toString()
  String action = request?.action?.toString()
  Map args = request?.args instanceof Map ? (Map)request.args : [:]
  try {
    switch(action) {
      case 'refreshRoster':
        refreshStaticDriverActivePlayerRoster()
        break
      case 'refreshPolicy':
        refreshPlayerAnnouncementPolicySnapshot(player)
          break
      default:
        logWarn("Ignoring unsupported polled player app request action '${action}' from ${dni ?: 'unknown'}")
    }
  } catch(Exception e) {
    logWarn("Polled player app request failed action=${action} from ${dni ?: 'unknown'}: ${e.message}")
  }
}

// =============================================================================
// Alarm management (local Sonos AlarmClock UPnP service)
// =============================================================================
void configureAlarmManagement() {
  Boolean managerEnabled = getAppPreference('enableAlarmManagement', false) == true || getAppPreference('enableAlarmManagement', false)?.toString() == 'true'
  Boolean switchesEnabled = getAppPreference('enableAlarmSwitches', false) == true || getAppPreference('enableAlarmSwitches', false)?.toString() == 'true'
  state.alarmManagementPreferencesApplied = [manager: managerEnabled, switches: switchesEnabled]

  if(!managerEnabled) { removeAlarmManagerDevices() }
  if(!switchesEnabled) {
    unschedule('refreshAllSonosAlarms')
    removeAlarmSwitchDevices()
  } else {
    unschedule('refreshAllSonosAlarms')
    schedule('0 */15 * * * ?', 'refreshAllSonosAlarms')
  }
  if(!managerEnabled && !switchesEnabled) { return }

  List<ChildDeviceWrapper> players = getCurrentPlayerDevices()
  Map<String, ChildDeviceWrapper> householdPlayers = [:]
  players.each { ChildDeviceWrapper player ->
    String householdId = player.getDataValue('householdId')?.toString()
    if(householdId && !householdPlayers.containsKey(householdId)) { householdPlayers[householdId] = player }
  }
  householdPlayers.each { String householdId, ChildDeviceWrapper player ->
    if(managerEnabled) { ensureAlarmManagerDevice(householdId) }
    if(switchesEnabled) { refreshSonosAlarms(householdId, player) }
  }
}

void applyAlarmManagementPreferenceChanges() {
  Boolean managerEnabled = getAppPreference('enableAlarmManagement', false) == true || getAppPreference('enableAlarmManagement', false)?.toString() == 'true'
  Boolean switchesEnabled = getAppPreference('enableAlarmSwitches', false) == true || getAppPreference('enableAlarmSwitches', false)?.toString() == 'true'
  Map applied = state.alarmManagementPreferencesApplied instanceof Map ? (Map)state.alarmManagementPreferencesApplied : null
  if(applied == null || applied.manager != managerEnabled || applied.switches != switchesEnabled) {
    configureAlarmManagement()
  }
}

void refreshAllSonosAlarms() {
  if(!(getAppPreference('enableAlarmSwitches', false) == true || getAppPreference('enableAlarmSwitches', false)?.toString() == 'true')) { return }
  Map<String, ChildDeviceWrapper> householdPlayers = [:]
  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    String householdId = player.getDataValue('householdId')?.toString()
    if(householdId && !householdPlayers.containsKey(householdId)) { householdPlayers[householdId] = player }
  }
  householdPlayers.each { String householdId, ChildDeviceWrapper player -> refreshSonosAlarms(householdId, player) }
}

void ensureAlarmManagerDevice(String householdId) {
  String dni = "${app.id}${ALARM_MANAGER_DNI_MARKER}${householdId}"
  ChildDeviceWrapper manager = getChildDevice(dni)
  if(manager == null) {
    try {
      manager = addChildDevice('dwinks', 'Sonos Advanced Alarm Manager', dni,
        [name: 'Sonos Alarm Manager', label: 'Sonos Alarm Manager', isComponent: true])
      logInfo('Created Sonos Alarm Manager for household ' + householdId)
    } catch(Exception e) {
      logWarn('Could not create alarm manager for ' + householdId + ': ' + e.message)
      return
    }
  }
  manager.updateDataValue('householdId', householdId)
  manager.setLabel('Sonos Alarm Manager')
}

void removeAlarmManagerDevices() {
  getChildDevices().findAll { ChildDeviceWrapper child ->
    child.getDeviceNetworkId()?.contains(ALARM_MANAGER_DNI_MARKER)
  }.each { ChildDeviceWrapper child ->
    try { deleteChildDevice(child.getDeviceNetworkId()) }
    catch(Exception e) { logWarn("Could not remove alarm manager ${child.label}: ${e.message}") }
  }
}

void removeAlarmSwitchDevices() {
  getChildDevices().findAll { ChildDeviceWrapper child ->
    child.getDeviceNetworkId()?.contains(ALARM_SWITCH_DNI_MARKER)
  }.each { ChildDeviceWrapper child ->
    try { deleteChildDevice(child.getDeviceNetworkId()) }
    catch(Exception e) { logWarn("Could not remove alarm child ${child.label}: ${e.message}") }
  }
}

void removeAlarmDevices() {
  removeAlarmSwitchDevices()
  removeAlarmManagerDevices()
}

void refreshSonosAlarms(String householdId, ChildDeviceWrapper player) {
  if(!(getAppPreference('enableAlarmSwitches', false) == true || getAppPreference('enableAlarmSwitches', false)?.toString() == 'true')) { return }
  String host = getSonosLocalUpnpHost(player)
  if(!householdId || !host) { logWarn("Cannot refresh alarms for household ${householdId}: no local Sonos player endpoint"); return }
  String requestId = UUID.randomUUID().toString()
  state.sonosAlarmRefreshRequestIds = (state.sonosAlarmRefreshRequestIds instanceof Map ? state.sonosAlarmRefreshRequestIds : [:]) + [(householdId): requestId]
  String body = sonosAlarmSoapBody('ListAlarms', [:])
  Map params = [uri: "http://${host}${SONOS_ALARM_CONTROL_URL}", contentType: 'text/xml', requestContentType: 'text/xml',
    headers: [SOAPAction: "${SONOS_ALARM_SERVICE_TYPE}#ListAlarms"], body: body, timeout: 10]
  asynchttpPost('sonosAlarmListCallback', params, [householdId: householdId, playerDni: player.deviceNetworkId, requestId: requestId])
}

void refreshSonosAlarmsForHousehold(String householdId) {
  ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
    candidate.getDataValue('householdId')?.toString() == householdId
  }
  if(player) { refreshSonosAlarms(householdId, player) }
  else { logWarn("Cannot refresh alarms for ${householdId}: no Sonos player child is available") }
}

void sonosAlarmListCallback(AsyncResponse response, Map data) {
  String householdId = data?.householdId?.toString()
  Map requests = state.sonosAlarmRefreshRequestIds instanceof Map ? (Map)state.sonosAlarmRefreshRequestIds : [:]
  if(!householdId || requests[householdId] != data?.requestId) { return }
  requests.remove(householdId)
  state.sonosAlarmRefreshRequestIds = requests
  if(!(getAppPreference('enableAlarmSwitches', false) == true || getAppPreference('enableAlarmSwitches', false)?.toString() == 'true')) {
    removeAlarmSwitchDevices()
    return
  }
  if(response == null || response.hasError() || response.status != 200) {
    logWarn("Sonos alarm refresh failed for ${householdId}: HTTP ${response?.status} ${response?.getErrorMessage()}")
    return
  }
  try {
    List<Map> alarms = parseSonosAlarmList(response.getData()?.toString())
    reconcileSonosAlarmChildren(householdId, alarms)
    logInfo("Found ${alarms.size()} Sonos alarm(s) in household ${householdId}")
  } catch(Exception e) {
    logWarn("Could not parse Sonos alarms for household ${householdId}: ${e.message}")
  }
}

String sonosAlarmSoapBody(String action, Map values) {
  StringWriter writer = new StringWriter()
  MarkupBuilder xml = new MarkupBuilder(writer)
  xml.mkp.xmlDeclaration(version: '1.0')
  xml.'s:Envelope'('xmlns:s': 'http://schemas.xmlsoap.org/soap/envelope/', 's:encodingStyle': 'http://schemas.xmlsoap.org/soap/encoding/') {
    's:Body' {
      "u:${action}"('xmlns:u': SONOS_ALARM_SERVICE_TYPE) {
        values.each { Object key, Object value -> "${key}"(value == null ? '' : value.toString()) }
      }
    }
  }
  return writer.toString()
}

@CompileStatic
String escapeSonosXml(String value) {
  return value ? StringEscapeUtils.escapeXml10(value) : ''
}

List<Map> parseSonosAlarmList(String responseXml) {
  if(!responseXml) { return [] }
  GPathResult outer = new XmlSlurper().parseText(responseXml)
  GPathResult alarmHolder = findXmlDescendantByLocalName(outer, 'CurrentAlarmList')
  String alarmXml = (alarmHolder instanceof GPathResult) ? ((GPathResult)alarmHolder).text() : null
  if(!alarmXml) { return [] }
  GPathResult alarmRoot = new XmlSlurper().parseText(alarmXml)
  List<Map> result = []
  List<String> alarmKeys = ['ID','StartTime','StartLocalTime','Duration','Recurrence','Enabled','RoomUUID',
    'ProgramURI','ProgramMetaData','PlayMode','Volume','IncludeLinkedZones']
  List<GPathResult> alarmNodes = []
  collectXmlDescendantsByLocalName(alarmRoot, 'Alarm', alarmNodes)
  alarmNodes.each { GPathResult node ->
    Map attrs = [:]
    alarmKeys.each { String key ->
      String value = xmlAttribute(node, key)
      if(value) { attrs[key] = value }
    }
    if(attrs.StartTime && !attrs.StartLocalTime) { attrs.StartLocalTime = attrs.StartTime }
    if(attrs.ID) { result.add(attrs) }
  }
  return result
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

GPathResult findXmlChildContainer(GPathResult node, String localName) {
  if(node == null || !localName) { return null }
  for(Object child : node.children()) {
    if(child instanceof GPathResult) {
      GPathResult match = findDirectXmlChild((GPathResult)child, localName)
      if(match != null) { return match }
    }
  }
  return null
}

void reconcileSonosAlarmChildren(String householdId, List<Map> alarms) {
  Set<String> keep = [] as Set<String>
  Map<String, ChildDeviceWrapper> playersById = [:]
  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    if(player.getDataValue('householdId')?.toString() == householdId) {
      playersById[player.getDataValue('id')?.toString()] = player
    }
  }
  alarms.each { Map alarm ->
    String alarmId = alarm.ID?.toString()
    String roomUuid = alarm.RoomUUID?.toString()
    if(!alarmId || !roomUuid) { return }
    String dni = "${app.id}${ALARM_SWITCH_DNI_MARKER}${householdId}-${alarmId}"
    keep.add(dni)
    ChildDeviceWrapper child = getChildDevice(dni)
    if(child == null) {
      try { child = addChildDevice('dwinks', 'Sonos Advanced Alarm Switch', dni,
        [name: 'Sonos Alarm', label: "Sonos Alarm: ${roomUuid} ${alarm.StartLocalTime ?: ''}", isComponent: true]) }
      catch(Exception e) { logWarn("Could not create Sonos alarm child ${alarmId}: ${e.message}"); return }
    }
    ChildDeviceWrapper roomPlayer = playersById[roomUuid]
    child.updateDataValue('householdId', householdId)
    child.updateDataValue('alarmId', alarmId)
    child.updateDataValue('roomUuid', roomUuid)
    child.updateDataValue('playerDni', roomPlayer?.deviceNetworkId ?: '')
    child.updateDataValue('deviceIp', getSonosDeviceIp(roomPlayer) ?: '')
    child.updateDataValue('alarmData', JsonOutput.toJson(alarm))
    child.updateDataValue('parentAppId', app.id.toString())
    Map<String, Object> alarmAttributes = [
      'switch': alarm.Enabled?.toString() == '1' ? 'on' : 'off',
      alarmTime: alarm.StartLocalTime?.toString() ?: '',
      alarmVolume: alarm.Volume?.toString()?.toInteger() ?: 0,
      recurrence: alarm.Recurrence?.toString() ?: '',
      room: roomPlayer?.label ?: roomUuid,
      duration: alarm.Duration?.toString() ?: '',
      alarmSource: alarm.ProgramURI?.toString() ?: '',
      playMode: alarm.PlayMode?.toString() ?: ''
    ]
    if(alarm.containsKey('IncludeLinkedZones')) {
      String linkedZones = alarm.IncludeLinkedZones?.toString()?.toLowerCase(Locale.ROOT)
      alarmAttributes.includeGroupedRooms = linkedZones in ['1', 'true'] ? 'true' : 'false'
    }
    sendChildAttributeEvents(child, alarmAttributes)
    child.setLabel("Sonos Alarm: ${roomPlayer?.label ?: roomUuid} ${alarm.StartLocalTime ?: ''}")
  }
  getChildDevices().findAll { ChildDeviceWrapper child -> child.getDeviceNetworkId()?.contains(ALARM_SWITCH_DNI_MARKER) && child.getDeviceNetworkId()?.contains("${ALARM_SWITCH_DNI_MARKER}${householdId}-") && !keep.contains(child.getDeviceNetworkId()) }
    .each { ChildDeviceWrapper child -> deleteChildDevice(child.getDeviceNetworkId()) }
}

void createSonosAlarm(String householdId, Map alarm) {
  ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
    candidate.getDataValue('householdId')?.toString() == householdId
  }
  String host = getSonosLocalUpnpHost(player)
  if(!host) { logWarn("Cannot create Sonos alarm in ${householdId}: no local player endpoint"); return }
  List<String> required = ['StartLocalTime','Duration','Recurrence','Enabled','RoomUUID','ProgramURI','ProgramMetaData','PlayMode','Volume','IncludeLinkedZones']
  if(required.any { String key -> alarm[key] == null }) { logWarn('Cannot create Sonos alarm: one or more required settings are missing'); return }
  if(!isValidSonosRecurrence(alarm.Recurrence?.toString()) || !(alarm.PlayMode in ['NORMAL','REPEAT_ALL','SHUFFLE_NOREPEAT','SHUFFLE'])) {
    logWarn('Cannot create Sonos alarm: recurrence or play mode is invalid'); return
  }
  Integer volume = alarm.Volume.toString().toInteger()
  if(volume < 0 || volume > 100 || !isValidLocalTime(alarm.StartLocalTime?.toString()) || !isValidSonosDuration(alarm.Duration?.toString())) {
    logWarn('Cannot create Sonos alarm: time, duration, or volume is invalid'); return
  }
  Map params = [uri: "http://${host}${SONOS_ALARM_CONTROL_URL}", contentType: 'text/xml', requestContentType: 'text/xml',
    headers: [SOAPAction: "${SONOS_ALARM_SERVICE_TYPE}#CreateAlarm"], body: sonosAlarmSoapBody('CreateAlarm', alarm), timeout: 10]
  asynchttpPost('sonosAlarmCreateCallback', params, [householdId: householdId, playerDni: player.deviceNetworkId])
}

void createSonosAlarmFromCatalogSource(String householdId, String sourceType, String sourceId, Map alarm) {
  ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
    candidate.getDataValue('householdId')?.toString() == householdId
  }
  if(player == null) { logWarn("Cannot create alarm from ${sourceType}: no player is available for household ${householdId}"); return }
  Map<String, LinkedHashMap> catalog = sourceType == 'favorite' ? player.getFavoritesMap() : player.getPlaylistsMap()
  Map entry = catalog?.values()?.find { Map candidate -> candidate?.get('id')?.toString() == sourceId }
  Map details = entry?.details instanceof Map ? (Map)entry.details : [:]
  Map resource = details?.resource instanceof Map ? (Map)details.resource : [:]
  Map resourceId = resource?.id instanceof Map ? (Map)resource.id : [:]
  String programUri = (resource?.uri ?: resource?.resourceUri ?: details?.programURI ?: details?.ProgramURI ?: details?.uri ?: details?.resourceUri)?.toString()
  String metadataValue = (resource?.metadata instanceof String ? resource.metadata : null) ?: (resource?.metaData instanceof String ? resource.metaData : null) ?: (details?.programMetadata ?: details?.ProgramMetaData ?: details?.metadata ?: details?.metaData)?.toString()
  String sourceTitle = (entry?.name ?: details?.name ?: sourceId).toString()
  if(!programUri && sourceType == 'favorite' && resourceId?.objectId && resourceId?.serviceId) {
    String serviceId = resourceId.serviceId.toString()
    String accountId = resourceId.accountId?.toString() ?: 'sn_0'
    String serialNumber = accountId.replaceFirst('^sn_', '')
    String objectId = resourceId.objectId.toString()
    programUri = "x-rincon-cpcontainer:${objectId}?sid=${serviceId}&flags=8300&sn=${serialNumber}"
    String contentId = sonosAlarmXmlEscape(objectId)
    String serviceMetadataId = "SA_RINCON${sonosAlarmXmlEscape(serviceId)}_X_#Svc${sonosAlarmXmlEscape(serviceId)}-0-Token"
    metadataValue = "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"><item id=\"${contentId}\" restricted=\"true\"><dc:title>${sonosAlarmXmlEscape(sourceTitle)}</dc:title><upnp:class>object.container.playlistContainer</upnp:class><desc id=\"cdudn\" nameSpace=\"urn:schemas-rinconnetworks-com:metadata-1-0/\">${serviceMetadataId}</desc></item></DIDL-Lite>"
  } else if(!programUri && sourceType == 'playlist') {
    String contentId = "0006206c${sourceId}"
    programUri = "x-rincon-cpcontainer:${contentId}?sid=0&flags=0&sn=0"
    metadataValue = "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"><item id=\"${contentId}\" restricted=\"true\"><dc:title>${sonosAlarmXmlEscape(sourceTitle)}</dc:title><upnp:class>object.container.playlistContainer</upnp:class><desc id=\"cdudn\" nameSpace=\"urn:schemas-rinconnetworks-com:metadata-1-0/\">RINCON_AssociatedZPUDN</desc></item></DIDL-Lite>"
  }
  String programMetadata = metadataValue ?: ''
  if(!programUri) {
    logWarn("Cannot create alarm from ${sourceType} '${sourceTitle}': Sonos catalog data has no playable resource URI; refresh favorites/playlists and try again")
    return
  }
  Map alarmData = new LinkedHashMap(alarm ?: [:])
  alarmData.ProgramURI = programUri
  alarmData.ProgramMetaData = programMetadata
  logInfo("Creating Sonos alarm from ${sourceType} '${sourceTitle}'")
  createSonosAlarm(householdId, alarmData)
}

@CompileStatic
String sonosAlarmXmlEscape(String value) {
  return value ? StringEscapeUtils.escapeXml10(value) : ''
}

void sonosAlarmCreateCallback(AsyncResponse response, Map data) {
  if(response == null || response.hasError() || response.status != 200) {
    logWarn("Sonos alarm creation failed for ${data?.householdId}: HTTP ${response?.status} ${response?.getErrorMessage()}")
    return
  }
  refreshSonosAlarmsDeferred([householdId: data?.householdId, playerDni: data?.playerDni])
}

void destroySonosAlarm(String householdId, String alarmId, String roomUuid) {
  ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
    candidate.getDataValue('householdId')?.toString() == householdId && candidate.getDataValue('id')?.toString() == roomUuid
  }
  if(player == null) { logWarn("Cannot delete Sonos alarm ${alarmId}: room player ${roomUuid} was not found"); return }
  String host = getSonosLocalUpnpHost(player)
  if(!host) { logWarn("Cannot delete Sonos alarm ${alarmId}: room player ${roomUuid} has no device IP"); return }
  Map params = [uri: "http://${host}${SONOS_ALARM_CONTROL_URL}", contentType: 'text/xml', requestContentType: 'text/xml',
    headers: [SOAPAction: "${SONOS_ALARM_SERVICE_TYPE}#DestroyAlarm"], body: sonosAlarmSoapBody('DestroyAlarm', [ID: alarmId]), timeout: 10]
  asynchttpPost('sonosAlarmDestroyCallback', params, [householdId: householdId, playerDni: player.deviceNetworkId])
}

void sonosAlarmDestroyCallback(AsyncResponse response, Map data) {
  if(response == null || response.hasError() || response.status != 200) {
    logWarn("Sonos alarm deletion failed for ${data?.householdId}: HTTP ${response?.status} ${response?.getErrorMessage()}")
    return
  }
  refreshSonosAlarmsDeferred([householdId: data?.householdId, playerDni: data?.playerDni])
}

void updateSonosAlarm(String dni, Map changes) {
  ChildDeviceWrapper child = getChildDevice(dni)
  Map alarm = [:]
  try { alarm = (Map)getSharedJsonSlurper().parseText(child?.getDataValue('alarmData')?.toString() ?: '{}') }
  catch(Exception e) { logWarn("Cannot update Sonos alarm ${dni}: cached alarm data is invalid"); return }
  String host = getSonosLocalUpnpHost(child)
  if(!host) { logWarn("Cannot update Sonos alarm ${dni}: its room player is unavailable"); return }
  alarm.putAll(changes)
  alarm.ID = child.getDataValue('alarmId')?.toString()
  ChildDeviceWrapper targetPlayer = null
  if(changes.RoomUUID) {
    targetPlayer = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
      candidate.getDataValue('id')?.toString() == changes.RoomUUID.toString() &&
        candidate.getDataValue('householdId')?.toString() == child.getDataValue('householdId')?.toString()
    }
    if(targetPlayer == null) { logWarn("Cannot move alarm ${alarm.ID} to unknown household player ${changes.RoomUUID}"); return }
  }
  Map bodyValues = [:]
  ['ID','StartLocalTime','Duration','Recurrence','RoomUUID','ProgramURI','ProgramMetaData','PlayMode','Volume','IncludeLinkedZones','Enabled'].each { String key ->
    if(alarm.containsKey(key)) { bodyValues[key] = alarm[key] }
  }
  List<String> updateFields = ['ID','StartLocalTime','Duration','Recurrence','RoomUUID','ProgramURI','ProgramMetaData','PlayMode','Volume','IncludeLinkedZones','Enabled']
  if(updateFields.any { String key -> bodyValues[key] == null }) {
    logWarn("Cannot update Sonos alarm ${alarm.ID}: required alarm settings are missing from the cached record")
    return
  }
  String requestHost = getSonosLocalUpnpHost(targetPlayer) ?: host
  Map params = [uri: "http://${requestHost}${SONOS_ALARM_CONTROL_URL}", contentType: 'text/xml', requestContentType: 'text/xml',
    headers: [SOAPAction: "${SONOS_ALARM_SERVICE_TYPE}#UpdateAlarm"], body: sonosAlarmSoapBody('UpdateAlarm', bodyValues), timeout: 10]
  asynchttpPost('sonosAlarmUpdateCallback', params, [householdId: child.getDataValue('householdId')?.toString(), playerDni: targetPlayer?.deviceNetworkId ?: child.getDataValue('playerDni')?.toString(), alarmDni: dni])
}

void sonosAlarmUpdateCallback(AsyncResponse response, Map data) {
  if(response == null || response.hasError() || response.status != 200) {
    logWarn("Sonos alarm update failed for ${data?.alarmDni}: HTTP ${response?.status} ${response?.getErrorMessage()}")
    return
  }
  String householdId = data?.householdId?.toString()
  ChildDeviceWrapper player = data?.playerDni ? getChildDevice(data.playerDni.toString()) : null
  if(householdId && player) { runIn(1, 'refreshSonosAlarmsDeferred', [data: [householdId: householdId, playerDni: player.deviceNetworkId]]) }
}

void refreshSonosAlarmsDeferred(Map data) {
  ChildDeviceWrapper player = data?.playerDni ? getChildDevice(data.playerDni.toString()) : null
  if(player) { refreshSonosAlarms(data.householdId.toString(), player) }
}

void setSonosAlarmEnabled(String dni, Boolean enabled) { updateSonosAlarm(dni, [Enabled: enabled ? '1' : '0']) }
Boolean isValidLocalTime(String value) {
  if(!value) { return false }
  try {
    LocalTime.parse(value, DateTimeFormatter.ofPattern('HH:mm:ss'))
    return true
  } catch(Exception ignored) {
    return false
  }
}
Boolean isValidSonosDuration(String value) {
  if(!value || !(value ==~ /\d{2}:\d{2}:\d{2}/)) { return false }
  List<Integer> parts = value.split(':').collect { String part -> Integer.parseInt(part) }
  if(parts[1] > 59 || parts[2] > 59) { return false }
  try {
    Duration.ofHours(parts[0]).plusMinutes(parts[1]).plusSeconds(parts[2])
    return true
  } catch(Exception ignored) {
    return false
  }
}
void setSonosAlarmTime(String dni, String time) {
  if(!isValidLocalTime(time)) { logWarn("Ignoring invalid alarm time ${time}"); return }
  updateSonosAlarm(dni, [StartLocalTime: time])
}
void setSonosAlarmVolume(String dni, Integer volume) {
  if(volume == null || volume < 0 || volume > 100) { logWarn("Ignoring invalid alarm volume ${volume}"); return }
  updateSonosAlarm(dni, [Volume: volume.toString()])
}
void setSonosAlarmRecurrence(String dni, String recurrence) {
  if(!isValidSonosRecurrence(recurrence)) { logWarn("Ignoring invalid alarm recurrence ${recurrence}"); return }
  updateSonosAlarm(dni, [Recurrence: recurrence])
}
void setSonosAlarmDuration(String dni, String duration) {
  if(!isValidSonosDuration(duration)) { logWarn("Ignoring invalid alarm duration ${duration}"); return }
  updateSonosAlarm(dni, [Duration: duration])
}
void setSonosAlarmRoom(String dni, String playerId) {
  if(!playerId) { return }
  updateSonosAlarm(dni, [RoomUUID: playerId])
}
void setSonosAlarmSource(String dni, String programUri, String programMetadata) {
  if(!programUri) { logWarn('Ignoring alarm source update without a program URI'); return }
  updateSonosAlarm(dni, [ProgramURI: programUri, ProgramMetaData: programMetadata ?: ''])
}
void setSonosAlarmPlayMode(String dni, String playMode) {
  if(!(playMode in ['NORMAL', 'REPEAT_ALL', 'SHUFFLE_NOREPEAT', 'SHUFFLE'])) { logWarn("Ignoring invalid alarm play mode ${playMode}"); return }
  updateSonosAlarm(dni, [PlayMode: playMode])
}
void setSonosAlarmIncludeGroupedRooms(String dni, Boolean include) {
  updateSonosAlarm(dni, [IncludeLinkedZones: include ? '1' : '0'])
}

// =============================================================================
// End Alarm management
// =============================================================================

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
    ChildDeviceWrapper player = getCurrentPlayerDevices().find { it.getDataValue('deviceIp') }
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
  // Group definitions are owned by the discovery child app. Existing group
  // devices persist their coordinator and follower IDs in device data values.
  refreshStaticDriverActivePlayerRoster(false)
}

void upsertGroupDevice(String groupName, Map definition) {
  String name = groupName?.trim()
  String coordinatorId = definition?.groupCoordinatorId?.toString()
  List<String> followerIds = normalizeGroupPlayerIds(definition?.playerIds)
  if(!name || !coordinatorId || followerIds.isEmpty()) { return }
  String dni = "${app.id}-SonosGroupDevice-${name}"
  DeviceWrapper device = app.getChildDevice(dni)
  if(device == null) {
    try {
      device = app.addChildDevice('dwinks', 'Sonos Advanced Group', dni,
          [name: 'Sonos Group', label: "Sonos Group: ${name}"])
    } catch(UnknownDeviceTypeException e) {
      logError("Sonos Advanced Group driver not found: ${e.message}")
      return
    }
  }
  if(device == null) { return }
  device.updateDataValue('groupCoordinatorId', coordinatorId)
  refreshGroupDeviceCoordinators()
  device.updateDataValue('playerIds', followerIds.join(','))
  ChildDeviceWrapper coordinator = getCurrentPlayerDevices().find { ChildDeviceWrapper player ->
    player.getDataValue('id')?.toString() == coordinatorId
  }
  String householdId = coordinator?.getDataValue('householdId')?.toString()
  if(householdId) { device.updateDataValue('householdId', householdId) }
  device.setLabel("Sonos Group: ${name}")
}

/** Set up child event subscriptions used by player and alarm request paths. */
void subscribeToGroupCommandRequests() {
  Integer subscribedPlayers = 0
  app.getChildDevices().each { ChildDeviceWrapper child ->
    String dni = child.getDeviceNetworkId()?.toString()
    if(dni?.contains(ALARM_MANAGER_DNI_MARKER) || dni?.contains(ALARM_SWITCH_DNI_MARKER)) {
      unsubscribe(child, ALARM_REQUEST_ATTRIBUTE, 'alarmRequestHandler')
      subscribe(child, ALARM_REQUEST_ATTRIBUTE, 'alarmRequestHandler')
    }
  }
  getCurrentPlayerDevices().each { ChildDeviceWrapper playerDevice ->
    unsubscribe(playerDevice, 'sonosPlayerStateUpdate', 'playerStateUpdateHandler')
    unsubscribe(playerDevice, 'sonosPlayerAppRequest', 'playerAppRequestHandler')
    unsubscribe(playerDevice, 'audioClipStatus', 'audioClipStatusHandler')
    subscribe(playerDevice, 'audioClipStatus', 'audioClipStatusHandler')
    unsubscribe(playerDevice, 'groupCoordinatorId', 'topologyAttributeHandler')
    unsubscribe(playerDevice, 'groupMemberCount', 'topologyAttributeHandler')
    unsubscribe(playerDevice, 'isGroupCoordinator', 'topologyAttributeHandler')
    unsubscribe(playerDevice, 'groupPlayerIds', 'topologyAttributeHandler')
    subscribe(playerDevice, 'groupCoordinatorId', 'topologyAttributeHandler')
    subscribe(playerDevice, 'groupMemberCount', 'topologyAttributeHandler')
    subscribe(playerDevice, 'isGroupCoordinator', 'topologyAttributeHandler')
    subscribe(playerDevice, 'groupPlayerIds', 'topologyAttributeHandler')
    // ZGT household topology now delivered via parent.putZgtTopology() (in-memory DTO) — no Current State publication or subscription
    subscribedPlayers++
  }
  getCurrentGroupDevices().each { ChildDeviceWrapper groupDevice ->
  }
  logDebug("Child request subscriptions configured; players=${subscribedPlayers}")
}

/** Publish announcement mode preferences; drivers read playback attributes when each command runs. */
void refreshPlayerAnnouncementPolicySnapshots(Boolean force = false) {
  List<ChildDeviceWrapper> players = getCurrentPlayerDevices()
  Map<String, String> policySettings = [
    normal: getAnnouncementMode(false),
    highPriority: getAnnouncementMode(true)
  ]
  Map<String, String> previousSettings = state[ANNOUNCEMENT_POLICY_SETTINGS_STATE_KEY] instanceof Map
      ? (Map<String, String>)state[ANNOUNCEMENT_POLICY_SETTINGS_STATE_KEY] : null
  Boolean missingPolicy = players.any { ChildDeviceWrapper player ->
    !player.getDataValue('normalAnnouncementMode') ||
      !player.getDataValue('highPriorityAnnouncementMode')
  }
  if(force || previousSettings != policySettings || missingPolicy) {
    players.each { ChildDeviceWrapper player -> refreshPlayerAnnouncementPolicySnapshot(player) }
    state[ANNOUNCEMENT_POLICY_SETTINGS_STATE_KEY] = policySettings
  }
}

void refreshPlayerAnnouncementPolicySnapshot(ChildDeviceWrapper player) {
  if(!player?.getDeviceNetworkId() || !player.getDataValue('id')) { return }
  updatePlayerPolicySnapshotValue(player, 'normalAnnouncementMode', getAnnouncementMode(false))
  updatePlayerPolicySnapshotValue(player, 'highPriorityAnnouncementMode', getAnnouncementMode(true))
}







void updatePlayerPolicySnapshotValue(ChildDeviceWrapper player, String key, Object value) {
  String normalized = value.toString()
  if(player.getDataValue(key)?.toString() != normalized) { player.updateDataValue(key, normalized) }
}

void finalizeProvisionedPlayerCreation(Map data) {
  refreshStaticDriverActivePlayerRoster()
  runIn(1, 'subscribeToGroupCommandRequests')
}

Integer createPlayerDevicesFromProvisioning(List<String> selectedPlayerDevices,
    Map<String, Map> playerInfoByDni, Map<String, Map> secondaryInfoByDni) {
  Integer createdCount = 0
  if(selectedPlayerDevices == null || selectedPlayerDevices.isEmpty()) { return createdCount }
  selectedPlayerDevices.each { String dni ->
    Map playerInfo = playerInfoByDni[dni]
    if(!dni || !(playerInfo instanceof Map)) { return }
    ChildDeviceWrapper cd = app.getChildDevice(dni)
    if(cd == null) {
      try {
        cd = addChildDevice('dwinks', 'Sonos Advanced Player', dni,
            [name: 'Sonos Advanced Player', label: "Sonos Advanced - ${playerInfo.name}"])
        if(cd) { createdCount++ }
      } catch(UnknownDeviceTypeException e) {
        logError("Sonos Advanced Player driver not found: ${e.message}")
      } catch(Exception e) {
        logError("Failed to create device for ${dni}: ${e.message}")
        cd = app.getChildDevice(dni)
        if(cd) { createdCount++ }
      }
    }
    if(!cd) { return }
    playerInfo.each { Object key, Object value ->
      if(key != null && value != null) { cd.updateDataValue(key.toString(), value.toString()) }
    }
    String rincon = playerInfo.id?.toString()
    List<Map> secondaries = secondaryInfoByDni.values().findAll { Object raw ->
      raw instanceof Map && ((Map)raw).primaryDeviceId?.toString() == rincon
    } as List<Map>
    List<String> ips = secondaries.collect { Map value -> value.deviceIp?.toString() }.findAll { String value -> value }
    List<String> ids = secondaries.collect { Map value -> value.id?.toString() }.findAll { String value -> value }
    if(ips && ids) {
      cd.updateDataValue('secondaryDeviceIps', ips.join(','))
      cd.updateDataValue('secondaryIds', ids.join(','))
    }
    List<String> critical = ['id', 'deviceIp']
    List<String> missing = critical.findAll { String key -> !cd.getDataValue(key)?.toString() }
    if(missing) {
      logWarn("Device ${cd.label} is missing critical data fields: ${missing.join(', ')}")
      runIn(10, 'retryProvisionedDeviceConfiguration', [data: [dni: dni, playerInfo: playerInfo]])
    } else {
      runIn(1, 'runSecondaryConfiguration', [data: [dni: dni]])
    }
  }
  refreshStaticDriverActivePlayerRoster()
  return createdCount
}

void retryProvisionedDeviceConfiguration(Map data) {
  String dni = data?.dni?.toString()
  ChildDeviceWrapper cd = dni ? app.getChildDevice(dni) : null
  Map playerInfo = data?.playerInfo instanceof Map ? (Map)data.playerInfo : [:]
  if(!cd || playerInfo.isEmpty()) { return }
  playerInfo.each { Object key, Object value ->
    if(key != null && value != null && !cd.getDataValue(key.toString())) {
      cd.updateDataValue(key.toString(), value.toString())
    }
  }
  List<String> critical = ['id', 'deviceIp']
  if(critical.every { String key -> cd.getDataValue(key)?.toString() }) {
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
  // Group removal is explicit from the discovery child app. The coordination
  // parent does not infer deletion from missing table settings.
  refreshStaticDriverActivePlayerRoster()
}
// =============================================================================
// End Create Child Devices
// =============================================================================



/** Split using the same character-delimiter and empty-token rules as Groovy tokenize(). */
List<String> splitTokens(String value, String delimiters) {
  if(value == null || value.isEmpty()) { return [] }
  String[] parts = StringUtils.split(value, delimiters)
  return parts == null ? [] : parts.toList()
}

// =============================================================================
// Helper methods
// =============================================================================

String processServerSideRender(Map event) {
  if(event?.name == 'newUiUpdateNotice') {
    return renderNewUiUpdateNoticeMarkup()
  }
  return ''
}

String displayCustomSettings() {
  return "<span class='ssr-app-state-${app.id}-newUiSettings'>${renderCustomSettingsMarkup()}</span>"
}

String updateSettingsDisclosureInstaller() {
  return "<style>.sonos-update-settings-disclosure{box-sizing:border-box;border:1px solid var(--surface-border,#d8dde5);border-radius:12px;background:var(--surface-card,#fff);box-shadow:0 2px 8px rgba(25,35,55,.06);margin:10px 0 16px;overflow:hidden}.sonos-update-settings-disclosure>summary{align-items:center;background:var(--surface-b,#f7f8fa);color:var(--text-color,#30343b);cursor:pointer;display:flex;font-size:16px;font-weight:650;justify-content:space-between;list-style:none;padding:14px 18px}.sonos-update-settings-disclosure>summary::-webkit-details-marker{display:none}.sonos-update-settings-disclosure>summary:after{content:'+';font-size:20px;line-height:1}.sonos-update-settings-disclosure[open]>summary:after{content:'−'}.sonos-update-settings-disclosure>.mdl-grid{box-shadow:none!important;margin:0!important;padding:12px 18px!important;width:100%!important}.sonos-update-settings-disclosure input.mdl-textfield__input,.sonos-update-settings-disclosure input:not([type='hidden']):not([type='checkbox']):not([type='radio']),.sonos-update-settings-disclosure select{background:var(--surface-a,#fff)!important;border:1px solid var(--surface-border,#c8cdd5)!important;border-radius:8px!important;box-sizing:border-box!important;color:var(--text-color,#30343b)!important;font:inherit!important;min-height:42px;padding:9px 12px!important;width:100%!important;box-shadow:none!important}.sonos-update-settings-disclosure input:focus,.sonos-update-settings-disclosure select:focus{border-color:#4054b2!important;box-shadow:0 0 0 3px rgba(64,84,178,.16)!important;outline:none!important}.sonos-update-settings-disclosure .mdl-button:not(.mdl-button--icon),.sonos-update-settings-disclosure button:not(.mdl-button--icon){background:#4054b2!important;border:1px solid #4054b2!important;border-radius:8px!important;box-shadow:none!important;color:#fff!important;cursor:pointer;font:inherit!important;font-weight:600!important;letter-spacing:normal!important;min-height:40px;padding:9px 16px!important;text-transform:none!important}.sonos-update-settings-disclosure .mdl-button:not(.mdl-button--icon):hover,.sonos-update-settings-disclosure button:not(.mdl-button--icon):hover{background:#34479e!important;border-color:#34479e!important}.sonos-update-settings-disclosure .mdl-button:focus-visible,.sonos-update-settings-disclosure button:focus-visible{outline:2px solid #1a77c9!important;outline-offset:2px}@media(max-width:640px){.sonos-update-settings-disclosure>summary{padding:12px 14px}.sonos-update-settings-disclosure>.mdl-grid{padding:10px 12px!important}}</style><script>(function(){function wrapUpdateSettings(){var input=document.querySelector(\"[name='settings[hubSecurityUsername]']\");var content=input&&input.closest('.mdl-shadow--2dp.mdl-grid');if(!content||content.dataset.nativeDisclosureWrapped==='true'){return}var host=content.parentElement;if(!host){return}var details=document.createElement('details');details.className='sonos-update-settings-disclosure';var summary=document.createElement('summary');summary.textContent='Update settings and maintenance';details.appendChild(summary);host.insertBefore(details,content);details.appendChild(content);content.dataset.nativeDisclosureWrapped='true';content.style.display='none';details.addEventListener('toggle',function(){content.style.display=details.open?'grid':'none'});var oldHeader=host.querySelector('.app-ui-navigation-control');if(oldHeader){oldHeader.remove()}}if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',wrapUpdateSettings,{once:true})}setTimeout(wrapUpdateSettings,0)})();</script>"
}

String updateSettingsDisclosureControlsStyle() {
  return "<style>.sonos-update-settings-disclosure button.app-password-toggle:not(.mdl-button--icon){align-items:center!important;background:transparent!important;border:0!important;box-shadow:none!important;color:#68707c!important;display:inline-flex!important;height:40px!important;justify-content:center!important;min-height:40px!important;padding:0!important;width:40px!important}.sonos-update-settings-disclosure button.app-password-toggle:not(.mdl-button--icon):hover{background:#eef2ff!important;border-radius:8px!important}</style>"
}

String displayNewUiUpdateNotice() {
  return "<span class='ssr-app-state-${app.id}-newUiUpdateNotice'>${renderNewUiUpdateNoticeMarkup()}</span>"
}

String renderNewUiUpdateNoticeMarkup() {
  if(state.updateAvailable != true) { return '' }
  String currentVersion = state.installedAppVersion?.toString() ?: getCurrentVersion()
  String latestVersion = escapeNewUiHtml(state.latestVersion?.toString() ?: 'Unknown')
  String safeCurrentVersion = escapeNewUiHtml(currentVersion)
  String releaseDateValue = state.latestReleaseDate?.toString()
  if(!releaseDateValue || releaseDateValue == 'Unknown') {
    releaseDateValue = state.latestManifest?.dateReleased?.toString() ?: state.latestManifest?.releaseDate?.toString() ?: 'Unknown'
  }
  String releaseDate = escapeNewUiHtml(releaseDateValue)
  String releaseUrl = escapeNewUiHtml(state.latestReleaseUrl?.toString() ?: '#')
  String installButton = buttonLink('btnInstallUpdate', 'Install update', '#FFFFFF', '', 'Install update', '15px')
  String dismissButton = buttonLink('btnDismissUpdate', 'Dismiss', '#30343B', '', 'Dismiss', '15px')
  return "<style>.sonos-update-card{border:1px solid #e3b341;border-radius:10px;padding:16px 18px;margin:10px 0 18px;background:var(--surface-card,#fffdf6);color:var(--text-color,#30343b);box-shadow:0 2px 8px rgba(50,45,20,.06)}.sonos-update-header{display:flex;align-items:center;gap:9px;color:#8a5b00;font-weight:650;font-size:1.12em;margin-bottom:10px}.sonos-update-icon{display:inline-flex;align-items:center;justify-content:center;width:25px;height:25px;border-radius:50%;background:#fff1c2}.sonos-update-version{font-weight:650;font-size:1.05em;margin-bottom:4px}.sonos-update-meta{color:var(--text-color-secondary,#666)}.sonos-update-link{display:inline-block;margin-top:10px}</style>" +
    "<style>.sonos-update-actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:14px}.sonos-update-actions .form-group,.sonos-update-actions input[type='hidden']{display:none!important}.sonos-update-actions .submitOnChange,.sonos-update-actions a{box-sizing:border-box!important;display:inline-block!important;padding:8px 14px!important;border:1px solid var(--surface-border,#c8c8c8)!important;border-radius:7px!important;background:var(--surface-b,#f7f7f7)!important;color:#30343b!important;cursor:pointer!important;font-size:15px!important;font-weight:600!important;line-height:1.4!important;text-decoration:none!important;box-shadow:none!important}.sonos-update-actions .sonos-update-action-primary .submitOnChange,.sonos-update-actions .sonos-update-action-primary a{background:#4054b2!important;border-color:#4054b2!important;color:#fff!important}.sonos-update-actions .submitOnChange:hover,.sonos-update-actions a:hover{filter:brightness(.97)}.sonos-update-actions .submitOnChange:focus-visible,.sonos-update-actions a:focus-visible{outline:2px solid #1a77c9;outline-offset:2px}</style>" +
    "<div class='sonos-update-card'><div class='sonos-update-header'><span class='sonos-update-icon' aria-hidden='true'>⚠</span>Update available</div>" +
    "<div class='sonos-update-version'>Version ${latestVersion} is available</div>" +
    "<div class='sonos-update-meta'>Current version: ${safeCurrentVersion}<br/>Released: ${releaseDate}</div>" +
    "<a class='sonos-update-link' href='${releaseUrl}' target='_blank' rel='noopener noreferrer'>View release notes</a>" +
    "<div class='sonos-update-actions'><div class='sonos-update-action-primary'>${installButton}</div><div class='sonos-update-action-secondary'>${dismissButton}</div></div></div>"
}

String renderCustomSettingsMarkup() {
  StringBuilder html = new StringBuilder()
  html.append("<style>\
    .new-ui-device-card,.new-ui-groups-card,.new-ui-group-editor-card,.sonos-setting-card,.sonos-update-card,.sonos-update-settings-disclosure{border:1px solid var(--surface-border,#d8d8d8)!important;border-radius:7px!important;box-shadow:none!important}\
    .new-ui-device-card,.new-ui-groups-card,.new-ui-group-editor-card,.sonos-setting-card{background:var(--surface-card,#fff)!important}\
    .new-ui-device-toolbar,.new-ui-group-toolbar,#new-ui-speaker-table thead,#new-ui-speaker-table th,#new-ui-group-table thead,#new-ui-group-table th,#new-ui-speaker-table .new-ui-table-toolbar-cell,#new-ui-group-table .new-ui-group-table-toolbar-cell{background:var(--surface-card,#fff)!important}\
    #new-ui-speaker-table .new-ui-table-toolbar-cell{padding:0!important}\
    .new-ui-device-toolbar,.new-ui-group-toolbar{padding:12px 14px!important}\
    .new-ui-group-toolbar-title{font-size:16px!important;font-weight:700!important}\
    .sonos-setting-card{border-radius:7px!important;padding:12px 14px!important}\
    .sonos-setting-title{font-size:15px!important;font-weight:700!important}\
    .sonos-setting-description{font-size:13px!important;line-height:1.45}\
    .sonos-update-card{background:var(--surface-card,#fffdf6)!important;color:var(--text-color,#30343b)!important;padding:14px 16px!important}\
    .sonos-update-settings-disclosure>summary{background:var(--surface-card,#fff)!important}\
    .new-ui-group-editor-heading{font-size:16px!important;font-weight:700!important}\
    @media(max-width:640px){.sonos-setting-card{padding:11px 12px!important}.new-ui-device-toolbar,.new-ui-group-toolbar{padding:10px 12px!important}}\
  </style>")
  html.append("<style>.sonos-settings{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px;margin:0 0 18px}.sonos-setting-card{border:1px solid var(--surface-border,#ddd);border-radius:10px;padding:14px 16px;background:var(--surface-card,#fff);color:var(--text-color,#30343b)}.sonos-setting-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.sonos-setting-title{font-weight:600}.sonos-setting-description{color:var(--text-color-secondary,#666);font-size:.92em;margin-top:6px}.sonos-switch{position:relative;display:inline-block;flex:0 0 39px;width:39px;height:14px;border-radius:7px;transition:background-color .2s}.sonos-switch-knob{position:absolute;top:-3.5px;width:21px;height:21px;border-radius:50%;transition:left .2s,background-color .2s;box-shadow:0 3px 1px -2px rgba(0,0,0,.2),0 2px 2px rgba(0,0,0,.14),0 1px 5px rgba(0,0,0,.12)}.sonos-switch:focus-visible{outline:2px solid #1a77c9;outline-offset:4px}.sonos-custom-select{position:relative;margin-top:10px}.sonos-custom-select summary{list-style:none;cursor:pointer;padding:9px 34px 9px 10px;border:1px solid var(--surface-border,#bbb);border-radius:6px;background:var(--surface-card,#fff);color:var(--text-color,#30343b);position:relative}.sonos-custom-select summary::-webkit-details-marker{display:none}.sonos-custom-select summary:after{content:'⌄';position:absolute;right:12px;top:7px;font-size:18px}.sonos-custom-select[open] summary{border-radius:6px 6px 0 0}.sonos-select-options{width:100%;border-collapse:collapse;border:1px solid var(--surface-border,#bbb);border-top:0;background:var(--surface-card,#fff);color:var(--text-color,#30343b);display:block;max-height:260px;overflow:auto}.sonos-select-options tbody{display:block}.sonos-select-options tr{display:table;width:100%;table-layout:fixed}.sonos-select-options td{padding:0}.sonos-select-option{padding:9px 10px;cursor:pointer}.sonos-select-option:hover,.sonos-select-option:focus{background:var(--surface-b,#eef2ff);outline:none}.sonos-select-option-selected{font-weight:600;color:var(--primary-color,#3347a9)}.sonos-setting-warning{grid-column:1/-1;border-left:4px solid #e0a000;background:#fff8e1;padding:10px 12px;color:#5b4500}</style>")
  html.append("<div class='sonos-settings'>")
  html.append(renderCustomSettingCard('enableAlarmManagement', 'Sonos Alarm Manager', 'Create one household-level alarm manager device.'))
  html.append(renderCustomSettingCard('enableAlarmSwitches', 'Sonos alarm switches', 'Create a switch device for each alarm found on the Sonos household.'))
  html.append(renderCustomSettingCard('autoCheckUpdates', 'Check for updates automatically', 'Schedule checks for new app releases.'))
  html.append(renderCustomSettingCard('autoInstallUpdates', 'Install updates automatically', 'Install available updates using the schedule below.'))
  Map<String, String> retentionOptions = [
    '1': '1 minute', '3': '3 minutes', '5': '5 minutes', '10': '10 minutes',
    '15': '15 minutes', '30': '30 minutes', '60': '1 hour', '120': '2 hours',
    '180': '3 hours', '240': '4 hours', '360': '6 hours', '720': '12 hours',
    '1440': '24 hours', 'unlimited': 'Unlimited'
  ]
  Map<String, String> announcementOptions = [
    'automatic': 'Automatic',
    'alwaysNonInterrupting': 'Always non-interrupting',
    'alwaysCoordinatorStream': 'Always standard stream'
  ]
  html.append(renderCustomSelectCard('maximumTtsRetention', 'Maximum TTS retention', 'Maximum time a queued TTS message can wait before it is sent.', retentionOptions, '60'))
  html.append(renderCustomSelectCard('groupAnnouncementMode', 'Normal priority announcement method', 'Automatic uses synchronized clips when playback is active or the target players are not already grouped. Standard stream uses the current coordinator group without changing membership.', announcementOptions, 'alwaysNonInterrupting'))
  html.append(renderCustomSelectCard('highPriorityAnnouncementMode', 'High priority announcement method', 'Automatic uses synchronized clips when playback is active or the target players are not already grouped. Standard stream uses the current coordinator group without changing membership.', announcementOptions, 'alwaysNonInterrupting'))
  html.append(renderCustomSettingCard('queueChimeSeparatelyFromTTS', 'Queue chime separately from TTS for better sync', 'Coordinate the chime release across group members, then prepare and release the TTS clips together.'))
  if(getAppPreference('queueChimeSeparatelyFromTTS', false) == true || getAppPreference('queueChimeSeparatelyFromTTS', false)?.toString() == 'true') {
    html.append("<div class='sonos-setting-warning'><b>Warning:</b> This may cause a slight delay between chime and TTS start.</div>")
  }
  html.append(renderCustomSettingCard('suppressChimeOnSuccessiveTTS', 'Suppress chime on successive TTS messages', 'Skip a chime when another TTS announcement is active or already waiting in the group queue.'))
  html.append('</div>')
  return html.toString()
}

String renderCustomSettingCard(String settingName, String title, String description) {
  Boolean enabled = settings[settingName] == true || settings[settingName]?.toString() == 'true'
  String value = enabled ? 'false' : 'true'
  String checked = enabled ? 'true' : 'false'
  String action = "newUiSetting|${settingName}|${value}"
  String safeTitle = escapeNewUiHtml(title)
  String safeDescription = escapeNewUiHtml(description)
  String switchColor = enabled ? 'rgba(63, 81, 181, .5)' : 'rgba(0, 0, 0, .38)'
  String knobColor = enabled ? '#3f51b5' : '#b0b0b0'
  String knobPosition = enabled ? '20px' : '-1px'
  return "<div class='sonos-setting-card'><div class='sonos-setting-row'><div><div class='sonos-setting-title'>${safeTitle}</div><div class='sonos-setting-description'>${safeDescription}</div></div><div class='form-group'><input type='hidden' name='${action}.type' value='button'></div><div style='display:inline-block'><div class='submitOnChange sonos-switch' role='switch' aria-checked='${checked}' aria-label='${safeTitle}' title='${enabled ? 'On' : 'Off'}' onclick='buttonClick(this)' style='background:${switchColor};cursor:pointer'><span class='sonos-switch-knob' style='left:${knobPosition};background:${knobColor}'></span></div></div><input type='hidden' name='settings[${action}]' value=''></div></div>"
}

String renderCustomSelectCard(String settingName, String title, String description, Map<String, String> options, String defaultValue) {
  String selectedValue = settings[settingName]?.toString() ?: defaultValue
  if(!options.containsKey(selectedValue)) { selectedValue = defaultValue }
  StringBuilder html = new StringBuilder()
  html.append("<div class='sonos-setting-card'><div class='sonos-setting-title'>${escapeNewUiHtml(title)}</div><div class='sonos-setting-description'>${escapeNewUiHtml(description)}</div>")
  html.append("<details class='sonos-custom-select'><summary aria-label='${escapeNewUiHtml(title)}'>${escapeNewUiHtml(options[selectedValue])}</summary><table class='sonos-select-options' role='listbox' aria-label='${escapeNewUiHtml(title)}'><tbody>")
  options.each { String optionValue, String optionLabel ->
    String action = "newUiSetting|${settingName}|${optionValue}"
    String selectedClass = optionValue == selectedValue ? ' sonos-select-option-selected' : ''
    String selectedMark = optionValue == selectedValue ? ' ✓' : ''
    html.append("<tr><td><div class='form-group'><input type='hidden' name='${action}.type' value='button'></div><div style='display:inline-block;width:100%'><div class='submitOnChange sonos-select-option${selectedClass}' role='button' tabindex='0' onclick='buttonClick(this)' onkeydown=\"if(event.key==='Enter'||event.key===' '){event.preventDefault();buttonClick(this)}\">${escapeNewUiHtml(optionLabel)}${selectedMark}</div></div><input type='hidden' name='settings[${action}]' value=''></td></tr>")
  }
  html.append('</tbody></table></details></div>')
  return html.toString()
}

void handleNewUiSettingChange(String buttonName) {
  List<String> parts = splitTokens(buttonName, '|')
  if(parts.size() != 3) {
    logWarn("Ignored malformed custom setting action '${buttonName}'")
    return
  }
  List<String> allowedSettings = ['enableAlarmManagement', 'enableAlarmSwitches', 'autoCheckUpdates', 'autoInstallUpdates', 'queueChimeSeparatelyFromTTS', 'suppressChimeOnSuccessiveTTS']
  String settingName = parts[1]
  String value = parts[2]
  List<String> enumSettings = ['maximumTtsRetention', 'groupAnnouncementMode', 'highPriorityAnnouncementMode', 'logLevel']
  if(settingName in enumSettings) {
    String requestedEnumValue = value
    Map<String, List<String>> allowedValues = [
      maximumTtsRetention: ['1', '3', '5', '10', '15', '30', '60', '120', '180', '240', '360', '720', '1440', 'unlimited'],
      groupAnnouncementMode: ['automatic', 'alwaysNonInterrupting', 'alwaysCoordinatorStream'],
      highPriorityAnnouncementMode: ['automatic', 'alwaysNonInterrupting', 'alwaysCoordinatorStream'],
      logLevel: ['trace', 'debug', 'info', 'warn', 'error', 'off']
    ]
    if(!(requestedEnumValue in allowedValues[settingName])) {
      logWarn("Ignored unsupported value '${requestedEnumValue}' for custom setting '${settingName}'")
      return
    }
    if(settings[settingName]?.toString() == requestedEnumValue) {
      logDebug("Ignoring duplicate custom setting '${settingName}'=${requestedEnumValue} (already applied)")
      return
    }
    logInfo("Applying custom setting '${settingName}'=${requestedEnumValue}")
    app.updateSetting(settingName, [type: 'enum', value: requestedEnumValue])
    settings[settingName] = requestedEnumValue
    if(settingName == 'logLevel') { applyLogLevelChange() }
    if(settingName in ['groupAnnouncementMode', 'highPriorityAnnouncementMode']) {
      refreshPlayerAnnouncementPolicySnapshots()
    }
    app.sendEvent(name: 'newUiSettings', value: 'updated')
    return
  }
  if(!(settingName in allowedSettings)) {
    logWarn("Ignored unsupported custom setting '${settingName}'")
    return
  }
  if(!(value in ['true', 'false'])) {
    logWarn("Ignored unsupported boolean value '${value}' for custom setting '${settingName}'")
    return
  }
  if(settings[settingName]?.toString() == value) {
    logDebug("Ignoring duplicate custom setting '${settingName}'=${value} (already applied)")
    return
  }
  Boolean booleanValue = value == 'true'
  logInfo("Applying custom setting '${settingName}'=${booleanValue}")
  app.updateSetting(settingName, [type: 'bool', value: booleanValue])
  settings[settingName] = booleanValue
  if(settingName == 'enableAlarmManagement' || settingName == 'enableAlarmSwitches') {
    configureAlarmManagement()
  }
  if(settingName == 'autoCheckUpdates' || settingName == 'autoInstallUpdates') {
    scheduleUpdateCheck()
  }
  app.sendEvent(name: 'newUiSettings', value: 'updated')
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
  return splitTokens(rincon, '_')[1][0..-6]
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
    String lowerIdentifier = identifier.toLowerCase(Locale.ROOT)
    source = children.find { ChildDeviceWrapper child ->
      child.getDataValue('name')?.toString()?.toLowerCase(Locale.ROOT)?.contains(lowerIdentifier)
    }
    if(source == null) {
      source = children.find { ChildDeviceWrapper child -> child.label?.toLowerCase(Locale.ROOT)?.contains(lowerIdentifier) }
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
            normalizePlayerIp(getSonosLocalUpnpHost(player)?.toString()) == normalizedIp
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
    List<String> coordinatorMembers = splitTokens(coordinator.getDataValue('groupPlayerIds')?.toString(), ',')
    if(coordinatorMembers.size() == 1 && coordinatorMembers[0] == coordinatorId &&
        coordinator.currentValue('isGrouped', true) == 'off') {
      logInfo("Creating a group with ${coordinatorId} as coordinator and adding ${targetId}")
      coordinator.playerCreateGroupRequest(JsonOutput.toJson([args: [[coordinatorId, targetId]]]))
      return
    }
    coordinator.playerGetGroupsFull()
    logWarn("Cannot add '${playerIdentifier}' yet: requested a fresh group topology for ${coordinatorId}; retry after it updates")
    return
  }

  String targetGroupId = target.getDataValue('groupId')?.toString()
  List<String> coordinatorMembers = splitTokens(coordinator.getDataValue('groupPlayerIds')?.toString(), ',')
  if(targetGroupId == groupId || coordinatorMembers.contains(targetId)) {
    logDebug("${targetId} is already in group ${groupId}; no group change needed")
    return
  }

  logInfo("Adding ${targetId} to group ${groupId} through coordinator ${coordinatorId}")
  coordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[targetId], []]]))
}

/** Send the supplied child events once; event publication does not have a retry queue. */
void sendChildEvent(String dni, Map event) {
  if(!dni || !event?.name || !event.containsKey('value')) { return }
  ChildDeviceWrapper child = getChildDevice(dni)
  if(child == null) { return }
  sendChildAttributeEvents(child, [(event.name.toString()): event.value])
}

/** Emit attributes once from the app context. Hubitat suppresses unchanged values by default. */
void sendChildAttributeEvents(ChildDeviceWrapper child, Map<String, Object> attributes) {
  if(child == null || !(attributes instanceof Map)) { return }
  attributes.each { String name, Object rawValue ->
    if(!name || rawValue == null) { return }
    try {
      if(rawValue instanceof Map && ((Map)rawValue).containsKey('value')) {
        Map event = [name: name, value: ((Map)rawValue).value]
        if(((Map)rawValue).unit != null) { event.unit = ((Map)rawValue).unit }
        child.sendEvent(event)
      } else {
        child.sendEvent(name: name, value: rawValue)
      }
    } catch(Exception e) {
      logWarn("Could not emit ${name} to child ${child.getDeviceNetworkId()}: ${e.message}")
    }
  }
}

/** Handle driver-reported player state batches. */
void playerStateUpdate(Map payload) {
  if(!(payload instanceof Map) || payload.isEmpty()) { return }
  List<Map> pending = state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] instanceof List
      ? new ArrayList<Map>((List<Map>)state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY]) : []
  Map<String, Object> incoming = new LinkedHashMap<String, Object>(payload)
  String coalesceKey = playerStateUpdateCoalesceKey(incoming)
  Integer existingIndex = coalesceKey ? pending.findIndexOf { Map queued ->
    playerStateUpdateCoalesceKey(queued) == coalesceKey
  } : -1
  if(existingIndex >= 0) {
    pending[existingIndex] = incoming
  } else {
    if(pending.size() >= PLAYER_STATE_UPDATE_QUEUE_MAX_ITEMS) {
      Integer replaceableIndex = pending.findIndexOf { Map queued -> playerStateUpdateCoalesceKey(queued) != null }
      if(replaceableIndex >= 0) {
        pending.remove(replaceableIndex)
        logWarn("Player state queue reached ${PLAYER_STATE_UPDATE_QUEUE_MAX_ITEMS}; evicted oldest replaceable snapshot")
      } else {
        logError("Player state queue is full (${PLAYER_STATE_UPDATE_QUEUE_MAX_ITEMS}); rejected kind=${incoming.kind ?: 'unknown'} player=${incoming.playerDni ?: 'unknown'}")
        return
      }
    }
    pending.add(incoming)
  }
  state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] = pending
  if(state[PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY] != true) {
    scheduleQueuedPlayerStateUpdateDrain(25)
  }
}

/** Receive driver observations and make the app the only owner of player attributes. */
synchronized void playerAttributeUpdate(Map payload) {
  String playerDni = payload?.playerDni?.toString()
  Map incoming = payload?.attributes instanceof Map ? new LinkedHashMap((Map)payload.attributes) : [:]
  incoming.keySet().removeAll { Object name -> name?.toString() in SOAP_EVENT_OWNED_ATTRIBUTES }
  ChildDeviceWrapper player = playerDni ? getChildDevice(playerDni) : null
  if(player != null) {
    incoming.keySet().removeAll { Object name -> isPlayerAttributeDisabled(player, name?.toString()) }
  }
  if(!playerDni || incoming.isEmpty()) { return }
  Map<String, Map<String, Object>> pending = state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY] : [:]
  Map<String, Object> merged = pending[playerDni] instanceof Map
      ? new LinkedHashMap<String, Object>(pending[playerDni]) : [:]
  incoming.each { Object rawName, Object value ->
    String name = rawName?.toString()
    if(name && value != null) { merged[name] = value }
  }
  pending[playerDni] = merged
  state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY] = pending
  if(state[PLAYER_ATTRIBUTE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] != true) {
    state[PLAYER_ATTRIBUTE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = true
    runInMillis(1, 'drainPlayerAttributeUpdates', [overwrite: true])
  }
}

/** App-gated drain only queues child batches; delivery happens on a later tick. */
synchronized void drainPlayerAttributeUpdates() {
  state.remove(PLAYER_ATTRIBUTE_UPDATE_DRAIN_SCHEDULED_STATE_KEY)
  Map<String, Map<String, Object>> pending = state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map<String, Object>>((Map<String, Map<String, Object>>)state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY]) : [:]
  state.remove(PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY)
  pending.each { String playerDni, Map<String, Object> attributes ->
    applyPlayerAttributeUpdate(playerDni, attributes)
  }
  Map<String, Map<String, Object>> arrivals = state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_PLAYER_ATTRIBUTE_UPDATES_STATE_KEY] : [:]
  if(!arrivals.isEmpty() && state[PLAYER_ATTRIBUTE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] != true) {
    state[PLAYER_ATTRIBUTE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = true
    runInMillis(1, 'drainPlayerAttributeUpdates', [overwrite: true])
  }
}

void applyPlayerAttributeUpdate(String playerDni, Map<String, Object> attributes) {
  ChildDeviceWrapper player = playerDni ? getChildDevice(playerDni) : null
  if(player == null || attributes == null || attributes.isEmpty()) { return }
  sendChildAttributeEvents(player, attributes)
}

/** State snapshots are replaceable; lifecycle acknowledgements remain ordered events. */
String playerStateUpdateCoalesceKey(Map payload) {
  return null
}

void scheduleQueuedPlayerStateUpdateDrain(Integer delayMs) {
  List pending = state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] instanceof List
      ? (List)state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] : []
  if(pending.isEmpty()) {
    state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = false
    state.remove(PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY)
    return
  }
  Long currentTime = now() as Long
  Long requestedDueAt = currentTime + Math.max(1, delayMs ?: 25) as Long
  Long existingDueAt = state[PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY] as Long
  Long dueAt = existingDueAt == null ? requestedDueAt : Math.min(existingDueAt, requestedDueAt)
  Integer scheduleDelay = Math.max(1L, dueAt - currentTime) as Integer
  state[PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY] = dueAt
  state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = true
  try {
    runInMillis(scheduleDelay, 'drainQueuedPlayerStateUpdates', [overwrite: true])
  } catch(Exception e) {
    state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = false
    state.remove(PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY)
    logWarn("Could not schedule player state update drain: ${e.message}")
  }
}

void drainQueuedPlayerStateUpdates() {
  if(state[PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY] == true) {
    scheduleQueuedPlayerStateUpdateDrain(100)
    return
  }
  List<Map> pending = state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] instanceof List
      ? new ArrayList<Map>((List<Map>)state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY]) : []
  if(pending.isEmpty()) {
    state.remove(PENDING_PLAYER_STATE_UPDATES_STATE_KEY)
    state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = false
    state.remove(PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY)
    return
  }
  state.remove(PENDING_PLAYER_STATE_UPDATES_STATE_KEY)
  state[PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY] = true
  state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = false
  state.remove(PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY)
  Long startedAt = now() as Long
  Integer processed = 0
  Integer failed = 0
  Integer cursor = 0
  Map<String, Integer> countsByKind = [:]
  Map<String, Long> maxDurationByKind = [:]
  try {
    while(cursor < pending.size() && processed < PLAYER_STATE_UPDATE_DRAIN_MAX_ITEMS &&
        (now() as Long) - startedAt < PLAYER_STATE_UPDATE_DRAIN_BUDGET_MS) {
      Map payload = pending[cursor++]
      String kind = payload?.kind?.toString() ?: 'unknown'
      Long handlerStartedAt = now() as Long
      try {
        playerStateUpdateHandler(payload)
      } catch(Exception e) {
        failed++
        logWarn("Player state update failed for kind=${kind} player=${payload?.playerDni ?: 'unknown'}: ${e.message}")
      } finally {
        Long handlerDuration = Math.max(0L, (now() as Long) - handlerStartedAt)
        countsByKind[kind] = (countsByKind[kind] ?: 0) + 1
        maxDurationByKind[kind] = Math.max(maxDurationByKind[kind] ?: 0L, handlerDuration)
      }
      processed++
    }
  } finally {
    List<Map> deferred = cursor < pending.size() ? pending.subList(cursor, pending.size()) : []
    List<Map> arrivals = state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] instanceof List
        ? new ArrayList<Map>((List<Map>)state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY]) : []
    List<Map> merged = mergePlayerStateUpdateQueues(deferred, arrivals)
    if(merged.isEmpty()) { state.remove(PENDING_PLAYER_STATE_UPDATES_STATE_KEY) }
    else { state[PENDING_PLAYER_STATE_UPDATES_STATE_KEY] = merged }
    state[PLAYER_STATE_UPDATE_DRAIN_ACTIVE_STATE_KEY] = false
    Long elapsed = Math.max(0L, (now() as Long) - startedAt)
    logDebug("Player state drain finished: startedDepth=${pending.size()}, processed=${processed}, failed=${failed}, deferred=${deferred.size()}, arrivals=${arrivals.size()}, remaining=${merged.size()}, elapsedMs=${elapsed}, countsByKind=${countsByKind}, maxHandlerMs=${maxDurationByKind}")
    if(!merged.isEmpty()) { scheduleQueuedPlayerStateUpdateDrain(100) }
    else {
      state[PLAYER_STATE_UPDATE_DRAIN_SCHEDULED_STATE_KEY] = false
      state.remove(PLAYER_STATE_UPDATE_DRAIN_DUE_AT_STATE_KEY)
    }
  }
}

/** Merge old deferred entries before reentrant arrivals so newer snapshots win. */
List<Map> mergePlayerStateUpdateQueues(List<Map> deferred, List<Map> arrivals) {
  List<Map> merged = []
  (deferred + arrivals).each { Map payload ->
    String key = playerStateUpdateCoalesceKey(payload)
    Integer index = key ? merged.findIndexOf { Map item -> playerStateUpdateCoalesceKey(item) == key } : -1
    if(index >= 0) { merged[index] = payload }
    else if(merged.size() < PLAYER_STATE_UPDATE_QUEUE_MAX_ITEMS) {
      merged.add(payload)
    }
    else { logError("Player state continuation queue full; rejected kind=${payload?.kind ?: 'unknown'}") }
  }
  return merged
}

void playerStateUpdateHandler(Object event) {
  Map payload
  if(event instanceof Map && !((Map)event).containsKey('value') && !((Map)event).containsKey('data')) {
    payload = (Map)event
  } else {
    try {
      String rawValue = event?.value?.toString()
      String rawPayload = rawValue?.trim()?.startsWith('{') ? rawValue : event?.data?.toString()
      payload = (Map)parseJson(rawPayload)
    } catch(Exception e) {
      logWarn("Ignoring malformed player state update: ${e.message}")
      return
    }
  }
  if(!(payload instanceof Map)) { return }
  String playerDni = payload.playerDni as String
  ChildDeviceWrapper player = playerDni ? getChildDevice(playerDni) : null
  if(player == null || !player.getDataValue('id')) { return }
  if(payload.kind == 'groupAnnouncementProgress') {
    handleGroupAnnouncementProgress(player, payload)
    return
  }
  if(payload.kind == 'deviceAttributes') {
    applyPlayerAttributeUpdate(playerDni, payload.attributes instanceof Map ? (Map)payload.attributes : [:])
    return
  }
  String coordinatorId = payload.coordinatorId as String
  String kind = payload.kind as String
  if(kind != 'state') { return }
}

/** Rebuild one player's topology from its current attribute values. A single
 * WebSocket update queues up to four attribute events; the last-processed
 * snapshot dedupes the burst so downstream work runs once per change. */
void topologyAttributeHandler(Event event) {
  ChildDeviceWrapper player = resolveTopologyEventPlayer(event)
  if(player == null) { return }
  String playerDni = player.getDeviceNetworkId()?.toString()
  String coordinatorId = player.currentValue('groupCoordinatorId', true)?.toString()
      ?: player.getDataValue('groupCoordinatorId')?.toString()
  String groupPlayerIdsRaw = player.currentValue('groupPlayerIds', true)?.toString()
      ?: player.getDataValue('groupPlayerIds')?.toString()
  List<String> playerIds = normalizeGroupPlayerIds(groupPlayerIdsRaw)
  Map<String, Map> lastProcessed = state[LAST_PROCESSED_TOPOLOGY_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)state[LAST_PROCESSED_TOPOLOGY_STATE_KEY]) : [:]
  Map previous = lastProcessed[playerDni]
  if(previous != null && previous.coordinatorId?.toString() == coordinatorId &&
      normalizeGroupPlayerIds(previous.playerIds) == playerIds) {
    return
  }
  lastProcessed[playerDni] = [coordinatorId: coordinatorId, playerIds: playerIds]
  state[LAST_PROCESSED_TOPOLOGY_STATE_KEY] = lastProcessed
  Map payload = [groupId: player.currentValue('groupId', true)?.toString(),
    coordinatorId: coordinatorId, playerIds: playerIds, observedAt: now(), playerDni: playerDni]
  processTopologyPayload(player, payload)
}

ChildDeviceWrapper resolveTopologyEventPlayer(Event event) {
  String dni = event?.device?.getDeviceNetworkId()?.toString() ?: event?.deviceId?.toString()
  ChildDeviceWrapper player = dni ? getChildDevice(dni) : null
  if(player == null && event?.device instanceof ChildDeviceWrapper) {
    player = (ChildDeviceWrapper)event.device
  }
  if(player == null || !player.getDataValue('id')) { return null }
  return player
}

/** Apply one rebuilt topology payload to policy refresh and in-flight group/announcement operations. */
void processTopologyPayload(ChildDeviceWrapper player, Map payload) {
  String playerDni = player?.getDeviceNetworkId()?.toString()
  if(player == null || !(payload instanceof Map)) { return }
  updateGroupDevices(payload.coordinatorId?.toString(), normalizeGroupPlayerIds(payload.playerIds))
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

    logDebug("Received topology report from ${playerId} for operation ${operation.operationId} at ${observedAt}")
    saveActiveGroupOperation(operation)
    runIn(1, 'advanceGroupTopologyOperation', [
      overwrite: true,
      data: [operationId: operation.operationId]
    ])
  }
}

ChildDeviceWrapper findPlayerBySonosId(String playerId) {
  if(!playerId) { return null }
  List<ChildDeviceWrapper> players = getCurrentPlayerDevices().findAll { ChildDeviceWrapper child ->
    child?.getDataValue('id')?.toString() == playerId
  } ?: []
  return players ? players[0] : null
}

/** Receive asynchronous requests from app-owned alarm child drivers. */
void alarmRequestHandler(Event event) {
  Map request
  try { request = (Map)parseJson(event?.value?.toString()) }
  catch(Exception e) {
    logWarn("Ignoring malformed alarm child request: ${e.message}")
    return
  }
  if(!(request instanceof Map) || !request.requestId) { return }
  String sourceDni = request.sourceDni?.toString()
  ChildDeviceWrapper source = sourceDni ? getChildDevice(sourceDni) : null
  if(source == null) { return }

  Map args = request.args instanceof Map ? (Map)request.args : [:]
  String action = request.action?.toString()
  if(request.channel == 'alarmManager' && sourceDni.contains(ALARM_MANAGER_DNI_MARKER)) {
    String householdId = request.householdId?.toString()
    switch(action) {
      case 'refresh': refreshSonosAlarmsForHousehold(householdId); break
      case 'create': createSonosAlarm(householdId, args); break
      case 'createFromCatalogSource':
        createSonosAlarmFromCatalogSource(householdId, args.sourceType?.toString(), args.sourceId?.toString(), args)
        break
      case 'destroy': destroySonosAlarm(householdId, args.alarmId?.toString(), args.roomUuid?.toString()); break
      default: logWarn("Ignoring unsupported Alarm Manager request action '${action}'")
    }
    return
  }
  if(request.channel != 'alarmSwitch' || !sourceDni.contains(ALARM_SWITCH_DNI_MARKER)) { return }
  Object value = request.value
  switch(action) {
    case 'setEnabled': setSonosAlarmEnabled(sourceDni, value?.toString() == 'true'); break
    case 'refresh': refreshSonosAlarmsForHousehold(value?.toString()); break
    case 'setTime': setSonosAlarmTime(sourceDni, value?.toString()); break
    case 'setVolume': setSonosAlarmVolume(sourceDni, value != null ? value.toString().toInteger() : null); break
    case 'setRecurrence': setSonosAlarmRecurrence(sourceDni, value?.toString()); break
    case 'setDuration': setSonosAlarmDuration(sourceDni, value?.toString()); break
    case 'setRoom': setSonosAlarmRoom(sourceDni, value?.toString()); break
    case 'setSource':
      Map sourceArgs = value instanceof Map ? (Map)value : [:]
      setSonosAlarmSource(sourceDni, sourceArgs.programUri?.toString(), sourceArgs.programMetadata?.toString())
      break
    case 'setPlayMode': setSonosAlarmPlayMode(sourceDni, value?.toString()); break
    case 'setIncludeGroupedRooms': setSonosAlarmIncludeGroupedRooms(sourceDni, value?.toString() == 'true'); break
    default: logWarn("Ignoring unsupported Alarm Switch request action '${action}'")
  }
}

/** Handle player requests sent directly by the child player driver. */
Boolean playerAppRequest(Map request) {
  return playerAppRequestHandler(request)
}

/** Elect the first non-battery player that reports ready as the household ZGT subscriber. */
String readyForZgt(String dni) {
  if(!dni) { return 'wait' }

  List<String> readyPlayers = state[READY_FOR_ZGT_STATE_KEY] instanceof List
      ? new ArrayList<String>((List<String>)state[READY_FOR_ZGT_STATE_KEY]) : []
  if(!readyPlayers.contains(dni)) { readyPlayers.add(dni) }
  state[READY_FOR_ZGT_STATE_KEY] = readyPlayers

  // Keep the original winner stable across repeated initialize calls; only it
  // may retry its subscription after a restart or a failed UPnP request.
  return readyPlayers[0] == dni ? 'proceed' : 'wait'
}

Boolean playerAppRequestHandler(Object event) {
  Map request
  if(event instanceof Map && !((Map)event).containsKey('value') && !((Map)event).containsKey('data')) {
    request = (Map)event
  } else {
    try { request = (Map)parseJson(event?.value?.toString()) }
    catch(Exception e) {
      logWarn("Ignoring malformed player app request: ${e.message}")
      return false
    }
  }
  String playerDni = request?.sourceDni?.toString()
  ChildDeviceWrapper player = playerDni ? getChildDevice(playerDni) : null
  if(player == null || !player.getDataValue('id') || request.channel != 'playerAppRequest') {
    logDebug("Ignoring player app request action=${request?.action ?: 'unknown'}, requestId=${request?.requestId ?: 'unknown'}, sourceDni=${playerDni ?: 'unknown'}, owned=${player != null}, hasPlayerId=${player?.getDataValue('id') != null}, channel=${request?.channel ?: 'unknown'}")
    return false
  }
  Map args = request.args instanceof Map ? (Map)request.args : [:]
  Long eventAgeMs = args.createdAt != null
      ? Math.max(0L, now() - (args.createdAt as Long)) : -1L
  logDebug("Received player app request action=${request.action ?: 'unknown'}, eventRequestId=${request.requestId ?: 'unknown'}, ttsRequestId=${args.requestId ?: 'none'}, correlation=${args.correlationId ?: 'none'}, sourceDni=${playerDni}, ageMs=${eventAgeMs}")
  switch(request.action?.toString()) {
    case 'refreshRoster':
      refreshStaticDriverActivePlayerRoster()
      break
    case 'refreshPolicy':
      refreshPlayerAnnouncementPolicySnapshot(player)
      break
    case 'needCurrentlyPlayingStates':
      runInMillis(1, 'republishCurrentlyPlayingStates', [
        overwrite: true,
        data: [playerDni: playerDni]
      ])
      return true
    case 'enqueueAudioClip':
      enqueuePlayerAudioClip(player, args)
      return true
    case 'playerReading':
      recordPlayerReading(player.getDataValue('id')?.toString(), args)
      return true
    case 'groupRendering':
      applyDirectGroupRendering(player.getDataValue('id')?.toString(), args)
      return true
    default:
      logWarn("Ignoring unsupported player app request action '${request.action}'")
      return false
  }
  return true
}

/** Republish the complete latest leader AVT after a low-frequency settings save. */
void republishCurrentlyPlayingStates(Map data = null) {
  String playerId = getPlayerRinconByDni(data?.playerDni?.toString())
  if(!playerId) { return }
  Map<String, String> coordinators = buildPlayerCoordinatorMap(getStoredZgtEventsMap())
  String coordinatorDni = getPlayerDniByRincon(coordinators[playerId] ?: playerId)
  List<Map> events = coordinatorDni ? getStoredAvtEvents(coordinatorDni) : []
  if(events) { applyAvtEvents(coordinatorDni, events) }
}

/** Accept an individual player's audioClip request into the app-owned scheduler. */
void enqueuePlayerAudioClip(ChildDeviceWrapper player, Map args) {
  String playerId = player?.getDataValue('id')?.toString()
  String uri = args?.uri?.toString()
  String clipType = args?.clipType?.toString()
  if(!playerId || (!uri && clipType != 'CHIME')) {
    logWarn("Ignoring audioClip request from ${player?.deviceNetworkId ?: 'unknown'}: player ID or URI is missing")
    return
  }
  Map item = [correlationId: args.correlationId?.toString() ?: UUID.randomUUID().toString(),
    groupCorrelationId: args.groupCorrelationId?.toString(), playerId: playerId,
    uri: uri, volume: args.volume, duration: toBigDecimal(args.duration)?.intValue() ?: 0,
    priority: args.priority?.toString() == 'HIGH' ? 'HIGH' : 'REGULAR',
    clipType: clipType, origin: args.origin?.toString() ?: 'player-request',
    submittedAt: now()]
  List<Map> items = []
  Boolean chimeBeforeTTS = args.chimeBeforeTTS == true
  if(args.containsKey('chimeBeforeTTS')) {
    String preferenceKey = item.priority == 'HIGH'
        ? 'chimeBeforeHighPriorityTTS' : 'chimeBeforeStandardPriorityTTS'
    chimeBeforeTTS = getPlayerPreference(player, preferenceKey, chimeBeforeTTS) == true
  }
  if(chimeBeforeTTS) {
    items.add([correlationId: UUID.randomUUID().toString(), playerId: playerId,
      volume: item.volume, duration: 8, priority: item.priority, clipType: 'CHIME',
      origin: 'player-chime', submittedAt: now()])
  }
  items.add(item)
  enqueuePlayerAudioClipItems(items)
}

/** All player and group clips pass through this app-owned per-player queue. */
void enqueuePlayerAudioClipItem(Map item) {
  enqueuePlayerAudioClipItems([item])
}

void enqueuePlayerAudioClipItems(List<Map> items) {
  Map<String, Map> queues = state[AUDIO_CLIP_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[AUDIO_CLIP_QUEUES_STATE_KEY] : [:]
  Set<String> playerIds = new LinkedHashSet<String>()
  items.each { Map item ->
    String playerId = item?.playerId?.toString()
    if(!playerId) { return }
    Map queue = queues[playerId] instanceof Map ? (Map)queues[playerId] : [regular: [], high: []]
    List<Map> high = queue.high instanceof List ? (List<Map>)queue.high : []
    List<Map> regular = queue.regular instanceof List ? (List<Map>)queue.regular : []
    if(item.priority == 'HIGH') { high.add(item) } else { regular.add(item) }
    queue.high = high
    queue.regular = regular
    queues[playerId] = queue
    playerIds.add(playerId)
  }
  state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
  dispatchNextPlayerAudioClips(playerIds)
}

void dispatchNextPlayerAudioClip(String playerId) {
  dispatchNextPlayerAudioClips([playerId])
}

void dispatchNextPlayerAudioClips(Collection<String> playerIds) {
  List<Map> preparedPosts = []
  playerIds.each { String playerId ->
    Map prepared = prepareNextPlayerAudioClipPost(playerId)
    if(prepared != null) { preparedPosts.add(prepared) }
  }
  preparedPosts.each { Map post -> asynchttpPost('audioClipPostCallback', post.params, post.data) }
}

Map prepareNextPlayerAudioClipPost(String playerId) {
  Map<String, Map> queues = state[AUDIO_CLIP_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[AUDIO_CLIP_QUEUES_STATE_KEY] : [:]
  Map queue = queues[playerId] instanceof Map ? (Map)queues[playerId] : null
  if(queue == null) { return null }
  List<Map> high = queue.high instanceof List ? (List<Map>)queue.high : []
  List<Map> regular = queue.regular instanceof List ? (List<Map>)queue.regular : []
  Map active = queue.active instanceof Map ? (Map)queue.active : null
  if(active != null) {
    if(active.priority == 'HIGH' || high.isEmpty()) { return null }
    queue.suspended = active
    queue.remove('active')
  }
  Map next = high ? high.remove(0) : (regular ? regular.remove(0) : null)
  queue.high = high
  queue.regular = regular
  if(next == null) {
    queues.remove(playerId)
    state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
    return null
  }
  ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
    candidate.getDataValue('id')?.toString() == playerId
  }
  String ip = player?.getDataValue('deviceIp')?.toString()
  String name = UUID.randomUUID().toString()
  next.name = name
  if(player?.currentValue('mute')?.toString() == 'muted') {
    queue.active = next
    queues[playerId] = queue
    state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
    logInfo("Skipping audioClip for muted player ${playerId}, correlation=${next.correlationId}")
    completePlayerAudioClip(playerId, next, 'DISMISSED')
    return null
  }
  if(!player || !ip) {
    logWarn("Cannot POST audioClip for ${playerId}: player device or IP is unavailable")
    queue.active = next
    queues[playerId] = queue
    state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
    completePlayerAudioClip(playerId, next, 'FAILED')
    return null
  }
  String priority = next.priority == 'HIGH' ? 'HIGH' : null
  BigDecimal requestedVolume = toBigDecimal(next.volume)
  Integer currentVolume = toBigDecimal(player.currentValue('volume')?.toString())?.intValue() ?: 0
  Integer boost = toBigDecimal(getPlayerPreference(player, 'ttsBoostAmount', 10)?.toString())?.intValue() ?: 10
  BigDecimal baseVolume = requestedVolume != null ? requestedVolume : new BigDecimal(currentVolume)
  Integer resolvedVolume = requestedVolume != null && (requestedVolume.compareTo(BigDecimal.ZERO) == 0 ||
      requestedVolume.compareTo(BigDecimal.valueOf(-1L)) == 0)
      ? 0 : Math.max(0, Math.min(baseVolume.add(new BigDecimal(boost)).setScale(0, RoundingMode.HALF_UP).intValue(), 100))
  Map body = [name: name, appId: AUDIO_CLIP_APP_ID,
    volume: resolvedVolume]
  if(priority) { body.priority = priority }
  if(next.uri) { body.streamUrl = next.uri }
  if(next.clipType) { body.clipType = next.clipType }
  Map params = [uri: "${getLocalApiPrefix("${ip}:1443")}${AUDIO_CLIP_ENDPOINT}",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json', contentType: 'application/json',
    ignoreSSLIssues: true, timeout: 5, body: JsonOutput.toJson(body)]
  next.appId = AUDIO_CLIP_APP_ID
  next.dispatchedAt = now()
  next.phase = 'POST_PENDING'
  queue.active = next
  queues[playerId] = queue
  state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
  logInfo("Prepared audioClip POST ${name} for player ${playerId}, priority=${priority}, origin=${next.origin}")
  return [params: params, data: [playerId: playerId, name: name,
    appId: AUDIO_CLIP_APP_ID, correlationId: next.correlationId]]
}

void dispatchNextPlayerAudioClipCallback(Map data = [:]) {
  if(data?.playerId) { dispatchNextPlayerAudioClip(data.playerId.toString()) }
}

void audioClipPostCallback(AsyncResponse response, Map data) {
  String playerId = data?.playerId?.toString()
  String name = data?.name?.toString()
  Map<String, Map> queues = state[AUDIO_CLIP_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[AUDIO_CLIP_QUEUES_STATE_KEY] : [:]
  Map queue = queues[playerId] instanceof Map ? (Map)queues[playerId] : null
  Map active = queue?.active instanceof Map ? (Map)queue.active : null
  if(active?.name?.toString() != name) { logDebug("Ignoring stale audioClip POST callback name=${name}"); return }
  Integer statusCode = response?.status as Integer
  if(response == null || response.hasError() || statusCode == null || statusCode < 200 || statusCode >= 300) {
    logWarn("audioClip POST failed for player ${playerId}, name=${name}, status=${response?.status}, error=${response?.getErrorMessage()}")
    active.phase = 'FAILED'
    completePlayerAudioClip(playerId, active, 'FAILED')
    return
  }
  try {
    Object raw = response.getJson()
    Map result = raw instanceof Map ? (Map)raw : [:]
    if(result.id != null) { active.sonosId = result.id.toString() }
    active.postResponseReceivedAt = now()
    active.phase = active.phase == 'ACTIVE' ? 'ACTIVE' : 'POSTED'
    queues[playerId] = queue
    state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
  } catch(Exception e) {
    logWarn("Could not parse audioClip POST response for player ${playerId}: ${e.message}")
  }
}

void completePlayerAudioClip(String playerId, Map completed, String phase) {
  Map<String, Map> queues = state[AUDIO_CLIP_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[AUDIO_CLIP_QUEUES_STATE_KEY] : [:]
  Map queue = queues[playerId] instanceof Map ? (Map)queues[playerId] : null
  if(queue?.active?.name?.toString() != completed?.name?.toString()) { return }
  queue.remove('active')
  Map suspended = queue.suspended instanceof Map ? (Map)queue.suspended : null
  if(completed.priority == 'HIGH' && suspended != null) {
    queue.remove('suspended')
    // A resumed clip gets a fresh Sonos name so late status messages cannot complete it.
    suspended.remove('name')
    List<Map> regular = queue.regular instanceof List ? (List<Map>)queue.regular : []
    regular.add(0, suspended)
    queue.regular = regular
  }
  queues[playerId] = queue
  state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
  if(completed.groupCorrelationId) {
    ChildDeviceWrapper player = getCurrentPlayerDevices().find { ChildDeviceWrapper candidate ->
      candidate.getDataValue('id')?.toString() == playerId
    }
    handleGroupAnnouncementProgress(player, [correlationId: completed.groupCorrelationId, playerId: playerId,
      phase: phase == 'FAILED' || phase == 'TIMEOUT' ? 'FAILED' : phase])
  }
  runInMillis(1, 'dispatchNextPlayerAudioClipCallback', [data: [playerId: playerId], overwrite: false])
}

void audioClipStatusHandler(Object event) {
  Map payload
  try {
    String raw = event?.value?.toString()
    payload = raw?.trim()?.startsWith('{') ? (Map)new JsonSlurper().parseText(raw) : [:]
  } catch(Exception e) {
    logWarn("Ignoring malformed audioClipStatus event: ${e.message}")
    return
  }
  if(!(payload instanceof Map) || payload.appId?.toString() != AUDIO_CLIP_APP_ID) { return }
  ChildDeviceWrapper player = resolveEventPlayer(event)
  String playerId = player?.getDataValue('id')?.toString()
  String name = payload.name?.toString()
  String phase = payload.status?.toString()
  Map queues = state[AUDIO_CLIP_QUEUES_STATE_KEY] instanceof Map ? (Map)state[AUDIO_CLIP_QUEUES_STATE_KEY] : [:]
  Map queue = queues[playerId] instanceof Map ? (Map)queues[playerId] : null
  Map active = queue?.active instanceof Map ? (Map)queue.active : null
  if(active?.name?.toString() != name || !phase) { return }
  if(payload.id != null) { active.sonosId = payload.id.toString() }
  active.phase = phase
  queues[playerId] = queue
  state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues
  logDebug("audioClip status player=${playerId}, name=${name}, id=${payload.id ?: 'pending'}, status=${phase}")
  if(phase == 'ACTIVE' && active.groupCorrelationId) {
    handleGroupAnnouncementProgress(player, [correlationId: active.groupCorrelationId, phase: 'ACTIVE'])
  } else if(phase in ['DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']) {
    completePlayerAudioClip(playerId, active, phase in ['ERROR', 'INTERRUPTED'] ? 'FAILED' : phase)
  }
}

void audioClipStatusEventHandler(Event event) { audioClipStatusHandler(event) }

ChildDeviceWrapper resolveEventPlayer(Object event) {
  if(event instanceof Event) { return resolveTopologyEventPlayer((Event)event) }
  String dni = event?.device?.getDeviceNetworkId()?.toString() ?: event?.deviceNetworkId?.toString()
  if(!dni && event?.deviceId != null) {
    dni = getCurrentPlayerDevices().find { ChildDeviceWrapper player ->
      player.id?.toString() == event.deviceId.toString()
    }?.deviceNetworkId?.toString()
  }
  if(dni) { return getChildDevice(dni) }
  return null
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
    submittedAt: now(),
    uri: args.uri?.toString(), volume: args.volume?.toString(),
    duration: (toBigDecimal(args.duration)?.intValue() ?: 0),
    isTts: args.isTts == true,
    forceAudioClip: args.forceAudioClip == true,
    coordinatorId: coordinator.getDataValue('id')?.toString(),
    playerIds: players.collect { ChildDeviceWrapper p -> p.getDataValue('id')?.toString() }.findAll { it }]
  if(args.containsKey('chimeBeforeStandardPriorityTTS')) {
    item.chimeBeforeStandardPriorityTTS = getGroupPreference(groupDevice,
        'chimeBeforeStandardPriorityTTS', args.chimeBeforeStandardPriorityTTS) == true
  }
  if(args.containsKey('chimeBeforeHighPriorityTTS')) {
    item.chimeBeforeHighPriorityTTS = getGroupPreference(groupDevice,
        'chimeBeforeHighPriorityTTS', args.chimeBeforeHighPriorityTTS) == true
  }
  if(!item.uri) {
    logWarn("Ignoring group announcement ${correlationId}: missing stream URI")
    return
  }

  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : [pending: []]
  List<Map> pendingAnnouncements = queue.pending instanceof List ? (List<Map>)queue.pending : []
  Boolean hasEarlierTts = queue.active?.isTts == true || queue.priorityActive?.isTts == true ||
      pendingAnnouncements.any { Map queued -> queued.isTts == true }
  Boolean suppressSuccessiveChime = getAppPreference('suppressChimeOnSuccessiveTTS', false) == true ||
      getAppPreference('suppressChimeOnSuccessiveTTS', false)?.toString() == 'true'
  if(item.isTts == true && hasEarlierTts && suppressSuccessiveChime) {
    item.chimeBeforeStandardPriorityTTS = false
    item.chimeBeforeHighPriorityTTS = false
    logDebug("Suppressing chime for successive TTS announcement ${correlationId} in ${groupDni}")
  }
  if(command == 'playHighPriorityTrack' && queue.priorityActive instanceof Map) {
    pendingAnnouncements.add(0, item)
    queue.pending = pendingAnnouncements
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    logDebug("Queued high priority announcement ${correlationId} behind active group high priority clip")
    return
  }
  if(command == 'playHighPriorityTrack' && queue.active instanceof Map) {
    String highPriorityMode = getAnnouncementMode(true)
    if(highPriorityMode != 'alwaysCoordinatorStream') {
      Boolean separateChime = getAppPreference('queueChimeSeparatelyFromTTS', false) == true ||
          getAppPreference('queueChimeSeparatelyFromTTS', false)?.toString() == 'true'
      Boolean highPriorityChime = item.containsKey('chimeBeforeHighPriorityTTS')
          ? item.chimeBeforeHighPriorityTTS == true
          : (item.isTts == true && players.any { ChildDeviceWrapper player ->
              getPlayerPreference(player, 'chimeBeforeHighPriorityTTS', false) == true
            })
      if(separateChime && item.isTts == true && highPriorityChime) {
        item.mode = 'AUDIO_CLIP'
        queue.priorityActive = item
        queues[groupDni] = queue
        state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
        dispatchGroupAnnouncementClip(item, players, true)
        return
      }
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
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    logInfo("High priority announcement ${correlationId} is taking over the active group stream using the configured standard-stream method")
    if(activeCorrelationId) { finishGroupAnnouncement(groupDni, activeCorrelationId, 'SUPERSEDED_BY_HIGH_PRIORITY_ANNOUNCEMENT') } else {
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
  if(queue == null || queue.active instanceof Map || queue.priorityActive instanceof Map) { return }
  if(getActiveGroupOperation() != null) {
    logDebug("Deferring queued announcement for ${groupDni} while a Sonos group operation is active")
    runIn(2, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: true])
    return
  }
  List<Map> pending = queue.pending instanceof List ? (List<Map>)queue.pending : []
  Long maxRetentionMs = getMaximumTtsRetentionMs()
  if(maxRetentionMs != null) {
    Long currentTime = now()
    List<Map> expired = pending.findAll { Map queuedItem ->
      Long submittedAt = queuedItem.submittedAt instanceof Number ? ((Number)queuedItem.submittedAt).longValue() : currentTime
      return currentTime - submittedAt > maxRetentionMs
    }
    if(expired) {
      pending.removeAll(expired)
      expired.each { Map queuedItem ->
        logInfo("Discarding expired queued announcement ${queuedItem.correlationId}; it exceeded the configured TTS retention")
      }
      queue.pending = pending
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    }
  }
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
      return
    }
    logWarn("Play Text command ${item.correlationId} includes a player without audioClip support; falling back to a coordinator stream")
    item.forceCoordinatorStream = true
  }
  dispatchGroupAnnouncementByPolicy(groupDni, queue, item, players, coordinator)
}

/** Return the configured queued-TTS age limit, or null when retention is unlimited. */
Long getMaximumTtsRetentionMs() {
  String configuredValue = getAppPreference('maximumTtsRetention', '60')?.toString() ?: '60'
  if(configuredValue == 'unlimited') { return null }
  try {
    return Duration.ofMinutes(Long.parseLong(configuredValue)).toMillis()
  } catch(NumberFormatException ignored) {
    return Duration.ofHours(1).toMillis()
  }
}


void dispatchGroupAnnouncementByPolicy(String groupDni, Map queue, Map item,
    List<ChildDeviceWrapper> players, ChildDeviceWrapper coordinator, Map snapshot = null) {
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Boolean highPriority = item.command == 'playHighPriorityTrack'
  String announcementMode = getAnnouncementMode(highPriority)
  String transportStatus = coordinator.currentValue('transportStatus', true)?.toString()?.toLowerCase(Locale.ROOT)
  String currentAudioSource = coordinator.currentValue('audioSource', true)?.toString()
  Map playbackActivity = getPlayerPlaybackActivity(coordinator, currentAudioSource)
  String playbackState = playbackActivity.playbackState?.toString()
  Boolean playing = playbackActivity.active == true
  Boolean exactTopology = isTargetRosterCurrentlyGrouped(players, coordinator.getDataValue('id')?.toString())
  Boolean playingPreviousAnnouncement = playing && queue.ownStreamUri?.toString() &&
      currentAudioSource == queue.ownStreamUri.toString()
  if(playingPreviousAnnouncement && queue.externalTakeover != true) { playing = false }
  Boolean useAudioClip = item.forceAudioClip == true || announcementMode == 'alwaysNonInterrupting' ||
      (announcementMode == 'automatic' && (playing || !exactTopology)) ||
      (isAirPlayAudioSource(currentAudioSource) && announcementMode == 'automatic')
  if(item.forceCoordinatorStream == true) { useAudioClip = false }
  if(useAudioClip) {
    item.mode = 'AUDIO_CLIP'
    String routeReason = announcementMode == 'alwaysNonInterrupting' ? 'app policy alwaysNonInterrupting' :
        (exactTopology ? "active source state=${playbackState ?: transportStatus}" : 'target players are not currently grouped together')
    logInfo("Starting group announcement ${item.correlationId} as synchronized per-player audioClip; ${routeReason}")
    dispatchGroupAnnouncementClip(item, players, highPriority)
  } else {
    item.mode = 'COORDINATOR_STREAM'
    queue.ownStreamUri = item.uri
    String routeReason = announcementMode == 'alwaysCoordinatorStream' ? 'app policy alwaysCoordinatorStream' : 'playback is inactive and target topology is already grouped'
    if(!exactTopology) { logWarn("Standard-stream announcement ${item.correlationId} targets only the current coordinator because the target roster is not grouped") }
    logInfo("Starting group announcement ${item.correlationId} as coordinator stream; ${routeReason}")
    coordinator.playerPlayGroupAnnouncementStream(item.uri as String, toBigDecimal(item.volume), item.correlationId as String)
  }
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
}

String getAnnouncementMode(Boolean highPriority = false) {
  String configured = highPriority
      ? getAppPreference('highPriorityAnnouncementMode', 'alwaysNonInterrupting')?.toString()?.trim()
      : getAppPreference('groupAnnouncementMode', 'alwaysNonInterrupting')?.toString()?.trim()
  return configured in ['automatic', 'alwaysNonInterrupting', 'alwaysCoordinatorStream']
      ? configured
      : 'automatic'
}




Boolean isTargetRosterCurrentlyGrouped(List<ChildDeviceWrapper> players, String coordinatorId) {
  List<String> targetIds = new TreeSet<String>(players.collect { ChildDeviceWrapper player ->
    player.getDataValue('id')?.toString()
  }.findAll { String id -> id }).toList()
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




Boolean isAirPlayAudioSource(String audioSource) {
  String normalized = audioSource?.toLowerCase(Locale.ROOT)
  return normalized?.contains('airplay:') == true || normalized?.contains('linein.airplay') == true
}


/** Queue direct player TTS so an active Sonos stream can be handed to a holder and restored. */


Map getPlayerPlaybackActivity(ChildDeviceWrapper player, String audioSource = null) {
  if(player == null) { return [active: false, signal: 'no-player'] }
  String playbackState = player.currentValue('status', true)?.toString()?.toUpperCase(Locale.ROOT)
  String transportStatus = player.currentValue('transportStatus', true)?.toString()?.toLowerCase(Locale.ROOT)
  String resolvedAudioSource = audioSource ?: player.currentValue('audioSource', true)?.toString()
  Boolean hdmiSource = resolvedAudioSource?.startsWith('x-sonos-htastream:') == true
  String signalStatus = hdmiSource ? player.currentValue('audioSourceSignalStatus', true)?.toString() : null
  if(hdmiSource && signalStatus in ['signal', 'no-signal']) {
    Boolean activeSignal = signalStatus == 'signal'
    return [active: activeSignal, signal: activeSignal ? 'hdmi-signal' : 'hdmi-no-signal', playbackState: playbackState]
  }
  Boolean active = playbackState in ['PLAYING', 'BUFFERING'] || transportStatus == 'playing'
  return [active: active, signal: active ? 'device-playing-state' : 'device-idle-state', playbackState: playbackState]
}


















/** Resolve the shared announcement policy for an individual player. */


void dispatchGroupAnnouncementClip(Map item, List<ChildDeviceWrapper> players, Boolean highPriority) {
  String correlationId = item.correlationId as String
  Integer duration = (item.duration ?: 0) as Integer
  String chimeSettingKey = highPriority ? 'chimeBeforeHighPriorityTTS' : 'chimeBeforeStandardPriorityTTS'
  Boolean chimeBeforeTTS = item.isTts == true && item.containsKey(chimeSettingKey) &&
      item[chimeSettingKey] == true
  Boolean separateChimeEnabled = getAppPreference('queueChimeSeparatelyFromTTS', false) == true ||
      getAppPreference('queueChimeSeparatelyFromTTS', false)?.toString() == 'true'
  Boolean separateChime = separateChimeEnabled && item.isTts == true && chimeBeforeTTS
  item.chimePhase = separateChime
  String playerNames = players.collect { ChildDeviceWrapper player ->
    "${player.displayName} (${player.getDataValue('id')})"
  }.join(', ')
  logDebug("Queueing group audioClip ${correlationId} for players: ${playerNames}")
  List<Map> queuedItems = []
  players.each { ChildDeviceWrapper player ->
    String playerId = player.getDataValue('id')?.toString()
    if(chimeBeforeTTS) {
      queuedItems.add([correlationId: UUID.randomUUID().toString(),
        groupCorrelationId: separateChime ? correlationId : null, playerId: playerId,
        uri: null, volume: toBigDecimal(item.volume), duration: 8,
        priority: highPriority ? 'HIGH' : 'REGULAR', clipType: 'CHIME',
        origin: 'group-chime', submittedAt: now()])
    }
    if(!separateChime) {
      queuedItems.add([correlationId: UUID.randomUUID().toString(),
        groupCorrelationId: correlationId, playerId: playerId, uri: item.uri?.toString(),
        volume: toBigDecimal(item.volume), duration: duration,
        priority: highPriority ? 'HIGH' : 'REGULAR', origin: 'group-tts', submittedAt: now()])
    }
  }
  enqueuePlayerAudioClipItems(queuedItems)
  logDebug("Queued group audioClip ${correlationId} on ${players.size()} players")
}

void handleGroupAnnouncementProgress(ChildDeviceWrapper player, Map payload) {
  String correlationId = payload.correlationId?.toString()
  String phase = payload.phase?.toString()
  if(!correlationId || !phase) { return }
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  String playerId = payload.playerId?.toString() ?: player?.getDataValue('id')?.toString()
  String groupDni = payload.groupDni?.toString()
  if(!groupDni) {
    groupDni = queues.find { String dni, Map q ->
      Map activeItem = q?.active instanceof Map ? (Map)q.active : null
      Map priorityItem = q?.priorityActive instanceof Map ? (Map)q.priorityActive : null
      Map matchedItem = activeItem?.correlationId?.toString() == correlationId ? activeItem : priorityItem
      matchedItem?.correlationId?.toString() == correlationId &&
          normalizeGroupPlayerIds(matchedItem.playerIds).contains(playerId)
    }?.key
  }
  if(!groupDni) { return }
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  Map priorityActive = queue?.priorityActive instanceof Map ? (Map)queue.priorityActive : null
  Boolean isPrioritySidecar = priorityActive?.correlationId?.toString() == correlationId
  Map active = isPrioritySidecar ? priorityActive : (queue?.active instanceof Map ? (Map)queue.active : null)
  if(active == null || active.correlationId?.toString() != correlationId) {
    logDebug("Ignoring stale group announcement callback ${correlationId} from ${playerId ?: 'unknown player'}")
    return
  }
  if(phase == 'INTERRUPTED' && payload.externalPlayback == true) {
    if(isPrioritySidecar) {
      logInfo("High priority audioClip announcement ${correlationId} was interrupted by external playback")
      queue.remove('priorityActive')
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
      return
    }
    queue.externalTakeover = true
    queue.externalSourceUri = payload.replacementUri?.toString()
    logDebug("Received coordinator takeover correlation=${correlationId}, player=${player.getDataValue('id')}, playbackState=${payload.playbackState}, sourceUri=${payload.sourceUri}, replacementUri=${payload.replacementUri}; pending=${queue.pending?.size() ?: 0}")
    logInfo("Coordinator stream ${correlationId} was interrupted by external playback; queued Automatic announcements will use audioClip while it remains active")
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    finishGroupAnnouncement(groupDni, correlationId, 'INTERRUPTED_BY_EXTERNAL_PLAYBACK')
    return
  }
  if(phase == 'PLAYBACK_RESTORED') {
    completeGroupAnnouncementPlaybackRestore(groupDni, correlationId, payload)
    return
  }
  Map<String, String> statuses = active.playerStatuses instanceof Map ? (Map<String, String>)active.playerStatuses : [:]
  if(playerId) { statuses[playerId] = phase; active.playerStatuses = statuses }
  logDebug("Group announcement ${correlationId} callback from ${playerId ?: 'unknown player'}: ${phase}; observed=${statuses}")
  if(phase == 'ACTIVE') {
  }
  String mode = active.mode?.toString()
  String coordinatorId = active.coordinatorId?.toString()
  logDebug("Group announcement ${correlationId} progress routing: mode=${mode ?: 'unset'}, coordinator=${coordinatorId ?: 'unset'}, callbackPlayer=${playerId ?: 'unset'}, phase=${phase}")
  if(mode != 'AUDIO_CLIP') {
    if(playerId == coordinatorId && phase in ['DONE', 'FAILED']) {
      finishGroupAnnouncement(groupDni, correlationId, phase)
    }
  } else {
    List<String> expected = normalizeGroupPlayerIds(active.playerIds)
    if(phase == 'FAILED' || phase == 'DISMISSED') { statuses[playerId] = 'DONE' }
    // INTERRUPTED is not terminal here: HIGH priority clips suspend regular
    // clips and the player driver reports ACTIVE again when it resumes them.
    if(phase in ['DONE', 'FAILED', 'DISMISSED'] && active.audioClipCompletionGraceStarted != true) {
      active.audioClipCompletionGraceStarted = true
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
      runInMillis(1000, 'completeGroupAnnouncementAfterAudioClipGrace', [overwrite: false,
        data: [groupDni: groupDni, correlationId: correlationId]])
      logDebug("Started one-second completion grace for group audioClip ${correlationId}")
    }
  }
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
}

/** Give delayed group-member audioClip callbacks one second before advancing the group queue. */
void completeGroupAnnouncementAfterAudioClipGrace(Map data = null) {
  String groupDni = data?.groupDni?.toString()
  String correlationId = data?.correlationId?.toString()
  if(!groupDni || !correlationId) { return }
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  Map priorityActive = queue?.priorityActive instanceof Map ? (Map)queue.priorityActive : null
  Boolean isPrioritySidecar = priorityActive?.correlationId?.toString() == correlationId
  Map active = isPrioritySidecar ? priorityActive : (queue?.active instanceof Map ? (Map)queue.active : null)
  if(active == null || active.correlationId?.toString() != correlationId || active.mode?.toString() != 'AUDIO_CLIP') { return }

  List<String> expected = normalizeGroupPlayerIds(active.playerIds)
  Map<String, String> statuses = active.playerStatuses instanceof Map
      ? (Map<String, String>)active.playerStatuses : [:]
  List<String> missing = expected.findAll { String id -> !(statuses[id] in ['DONE', 'FAILED', 'DISMISSED']) }
  if(missing) {
    logWarn("Group audioClip ${correlationId} completion grace expired; continuing without terminal callbacks from ${missing}")
    missing.each { String id -> statuses[id] = 'FAILED' }
    active.playerStatuses = statuses
  }

  if(active.chimePhase == true) {
    Map<String, String> ttsStatuses = [:]
    statuses.each { String id, String status ->
      if(status == 'DISMISSED') { ttsStatuses[id] = 'DISMISSED' }
    }
    active.chimePhase = false
    active.waitingForSynchronizedTTSRelease = true
    active.playerStatuses = ttsStatuses
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    runInMillis(100, 'releaseGroupAnnouncementTTSAfterChime', [overwrite: true,
      data: [groupDni: groupDni, correlationId: correlationId]])
    return
  }
  if(isPrioritySidecar) {
    queue.remove('priorityActive')
    queues[groupDni] = queue
    state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    if(!(queue.active instanceof Map)) {
      runInMillis(1, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: false])
    }
    return
  }
  finishGroupAnnouncement(groupDni, correlationId, 'DONE')
}

/** After the group chime phase completes, release each queued TTS clip in a second coordinated pass. */
void releaseGroupAnnouncementTTSAfterChime(Map data = null) {
  String groupDni = data?.groupDni?.toString()
  String correlationId = data?.correlationId?.toString()
  if(!groupDni || !correlationId) { return }
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  Map priorityItem = queue?.priorityActive instanceof Map ? (Map)queue.priorityActive : null
  Boolean prioritySidecar = priorityItem?.correlationId?.toString() == correlationId
  Map item = prioritySidecar ? priorityItem : (queue?.active instanceof Map ? (Map)queue.active : null)
  if(item?.correlationId?.toString() != correlationId || item.waitingForSynchronizedTTSRelease != true) { return }
  item.waitingForSynchronizedTTSRelease = false
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<ChildDeviceWrapper> players = normalizeGroupPlayerIds(item.playerIds)
      .collect { String id -> rinconMap[id] }.findAll { it != null }
  if(!correlationId || !players) {
    logWarn("Cannot release synchronized TTS after chime for ${correlationId ?: groupDni}; group players are unavailable")
    if(prioritySidecar) {
      queue.remove('priorityActive')
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
    }
    else { finishGroupAnnouncement(groupDni, correlationId, 'FAILED') }
    return
  }
  logDebug("Releasing synchronized TTS ${correlationId} after all group chimes completed")
  List<Map> queuedItems = players.collect { ChildDeviceWrapper player ->
    [correlationId: UUID.randomUUID().toString(), groupCorrelationId: correlationId,
      playerId: player.getDataValue('id')?.toString(), uri: item.uri?.toString(),
      volume: toBigDecimal(item.volume), duration: item.duration,
      priority: prioritySidecar ? 'HIGH' : 'REGULAR', origin: 'group-tts', submittedAt: now()]
  }
  enqueuePlayerAudioClipItems(queuedItems)
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
}

void finishGroupAnnouncement(String groupDni, String correlationId, String reason) {
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  if(queue?.active?.correlationId?.toString() != correlationId) { return }
  Map active = queue.active instanceof Map ? (Map)queue.active : null
  logInfo("Group announcement ${correlationId} finished (${reason}) for ${groupDni}")
  if(active.mode?.toString() != 'AUDIO_CLIP' && reason != 'INTERRUPTED_BY_EXTERNAL_PLAYBACK' &&
      reason != 'SUPERSEDED_BY_HIGH_PRIORITY_ANNOUNCEMENT') {
    ChildDeviceWrapper coordinator = buildRinconMap()[active.coordinatorId?.toString()]
    if(coordinator != null) {
      active.playbackRestorePending = true
      queues[groupDni] = queue
      state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
      logInfo("Restoring coordinator playback for ${correlationId} after ${reason}")
      coordinator.restoreGroupAnnouncementPlayback(correlationId)
      return
    }
    logWarn("Cannot restore playback for ${correlationId}: coordinator is unavailable")
  }
  completeGroupAnnouncementPlaybackRestore(groupDni, correlationId, [restored: false, skipped: reason])
}

void completeGroupAnnouncementPlaybackRestore(String groupDni, String correlationId, Map payload = [:]) {
  Map<String, Map> queues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] : [:]
  Map queue = queues[groupDni] instanceof Map ? (Map)queues[groupDni] : null
  if(queue?.active?.correlationId?.toString() != correlationId) { return }
  logInfo("Coordinator playback restore completed for ${correlationId}: restored=${payload?.restored}")
  queue.active = null
  queues[groupDni] = queue
  state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = queues
  runInMillis(1, 'startNextGroupAnnouncementCallback', [data: [groupDni: groupDni], overwrite: false])
}

void startNextGroupAnnouncementCallback(Map data = null) {
  String groupDni = data?.groupDni?.toString()
  if(groupDni) { startNextGroupAnnouncement(groupDni) }
}

/** Handle requests sent directly by a child group driver. */
Boolean groupAppRequest(Map request) {
  if(!(request instanceof Map) || request.channel != 'groupAppRequest') {
    logDebug("Ignoring invalid group app request channel=${request?.channel ?: 'unknown'}")
    return false
  }
  String groupDni = request.groupDni?.toString()
  if(!groupDni || getChildDevice(groupDni) == null) {
    logDebug("Ignoring group app request action=${request.action ?: request.command ?: 'unknown'}, requestId=${request.requestId ?: 'unknown'}, sourceDni=${groupDni ?: 'unknown'}, owned=${groupDni ? getChildDevice(groupDni) != null : false}")
    return false
  }
  Long requestAgeMs = request.createdAt != null
      ? Math.max(0L, now() - (request.createdAt as Long)) : -1L
  logDebug("Received group app request action=${request.action ?: request.command ?: 'unknown'}, requestId=${request.requestId ?: 'unknown'}, sourceDni=${groupDni}, sequence=${request.sequence ?: 'unknown'}, ageMs=${requestAgeMs}")
  return enqueueGroupCommandRequest(request)
}

/** Queue a validated group command request for ordered processing. */
Boolean enqueueGroupCommandRequest(Map request) {
  if(!(request instanceof Map)) {
    return false
  }

  String groupDni = request?.groupDni as String
  String command = request?.command as String
  if(request?.action?.toString() == 'initializationPurge' && groupDni && getChildDevice(groupDni) != null) {
    clearPendingCommandsForDevice(groupDni)
    return true
  }
  if(!groupDni || !command) {
    logWarn("Ignoring group command request with invalid target or command: ${JsonOutput.toJson(request)}")
    return false
  }
  if(isRequestOlderThanDeviceCommandClear(groupDni, request.createdAt)) {
    logDebug("Ignoring stale group command request ${request.requestId ?: 'unknown'} from before initialization for ${groupDni}")
    return false
  }
  // Child drivers call the parent directly; still verify ownership before dispatch.
  if(getChildDevice(groupDni) == null) {
    logDebug("Ignoring group command ${command} (${request?.requestId ?: 'no request ID'}) for unowned group ${groupDni}")
    return false
  }

  String requestId = request.requestId as String
  Map<String, List<String>> receivedRequests = state.receivedGroupCommandRequestIds instanceof Map
      ? (Map<String, List<String>>)state.receivedGroupCommandRequestIds
      : [:]
  List<String> receivedForGroup = receivedRequests[groupDni] instanceof List
      ? (List<String>)receivedRequests[groupDni]
      : []
  if(requestId && receivedForGroup.contains(requestId)) {
    logDebug("Ignoring duplicate group command request ${requestId} (${command}) for ${groupDni}; not redispatching")
    return true
  }

  // All group commands — including audio clips — are queued and drained in
  // request-sequence order. Inline dispatch under the app gate held the gate
  // while calling player/group devices, which deadlocks against child
  // executions waiting for the same gate (120 s timeout). The 1 s drain hop
  // preserves queue ordering without waiting on child-device execution.

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
  }
  runIn(1, 'drainGroupCommandRequests', [overwrite: true])
  return true
}

/**
 * Drop commands and announcements owned by a child whose driver is explicitly
 * initializing. The driver invokes this before requesting its initialization
 * turn, so none of the old work can be replayed during recovery.
 */
void clearPendingCommandsForDevice(String deviceDni) {
  if(!deviceDni) { return }

  Map<String, Long> clearTimes = state[DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Long>((Map<String, Long>)state[DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY]) : [:]
  clearTimes[deviceDni] = now()
  state[DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY] = clearTimes

  Map<String, List<Map>> pendingGroups = state[GROUP_COMMAND_QUEUE_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, List<Map>>((Map<String, List<Map>>)state[GROUP_COMMAND_QUEUE_STATE_KEY]) : [:]
  pendingGroups.remove(deviceDni)
  if(pendingGroups.isEmpty()) { state.remove(GROUP_COMMAND_QUEUE_STATE_KEY) }
  else { state[GROUP_COMMAND_QUEUE_STATE_KEY] = pendingGroups }

  Map<String, List<Map>> pendingPlayers = state[PLAYER_COMMAND_QUEUE_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, List<Map>>((Map<String, List<Map>>)state[PLAYER_COMMAND_QUEUE_STATE_KEY]) : [:]
  pendingPlayers.remove(deviceDni)
  if(pendingPlayers.isEmpty()) { state.remove(PLAYER_COMMAND_QUEUE_STATE_KEY) }
  else { state[PLAYER_COMMAND_QUEUE_STATE_KEY] = pendingPlayers }

  List<Map> deferredGroups = state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_GROUP_COMMANDS_STATE_KEY] : []
  deferredGroups = deferredGroups.findAll { Map request -> request.groupDni?.toString() != deviceDni }
  if(deferredGroups.isEmpty()) { state.remove(DEFERRED_GROUP_COMMANDS_STATE_KEY) }
  else { state[DEFERRED_GROUP_COMMANDS_STATE_KEY] = deferredGroups }

  List<Map> deferredPlayers = state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] : []
  deferredPlayers = deferredPlayers.findAll { Map request -> request.playerDni?.toString() != deviceDni }
  if(deferredPlayers.isEmpty()) { state.remove(DEFERRED_PLAYER_COMMANDS_STATE_KEY) }
  else { state[DEFERRED_PLAYER_COMMANDS_STATE_KEY] = deferredPlayers }

  ChildDeviceWrapper child = getChildDevice(deviceDni)
  String playerId = child?.getDataValue('id')?.toString()
  Map<String, Map> announcementQueues = state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY]) : [:]
  Set<String> clearedAnnouncementIds = new LinkedHashSet<String>()
  if(announcementQueues[deviceDni] instanceof Map) {
    Map removedQueue = announcementQueues.remove(deviceDni)
    [removedQueue?.active, removedQueue?.priorityActive].each { Map item ->
      if(item != null) { clearPendingAnnouncementCommands(item) }
      if(item?.correlationId) { clearedAnnouncementIds.add(item.correlationId.toString()) }
    }
    ((removedQueue?.pending instanceof List) ? (List<Map>)removedQueue.pending : []).each { Map item ->
      clearPendingAnnouncementCommands(item)
      if(item?.correlationId) { clearedAnnouncementIds.add(item.correlationId.toString()) }
    }
  }
  if(playerId) {
    new ArrayList<String>(announcementQueues.keySet()).each { String groupDni ->
      Map queue = announcementQueues[groupDni] instanceof Map ? (Map)announcementQueues[groupDni] : null
      if(queue == null) { return }
      ['active', 'priorityActive'].each { String slot ->
        Map item = queue[slot] instanceof Map ? (Map)queue[slot] : null
        if(item != null && normalizeGroupPlayerIds(item.playerIds).contains(playerId)) {
          clearPendingAnnouncementCommands(item)
          if(item.correlationId) { clearedAnnouncementIds.add(item.correlationId.toString()) }
          queue.remove(slot)
        }
      }
      List<Map> queuedAnnouncements = queue.pending instanceof List ? (List<Map>)queue.pending : []
      List<Map> retainedAnnouncements = queuedAnnouncements.findAll { Map item ->
        Boolean targeted = normalizeGroupPlayerIds(item.playerIds).contains(playerId)
        if(targeted) {
          clearPendingAnnouncementCommands(item)
          if(item.correlationId) { clearedAnnouncementIds.add(item.correlationId.toString()) }
        }
        return !targeted
      }
      if(retainedAnnouncements.isEmpty() && !(queue.active instanceof Map) && !(queue.priorityActive instanceof Map)) {
        announcementQueues.remove(groupDni)
      } else {
        queue.pending = retainedAnnouncements
      }
    }
  }
  if(announcementQueues.isEmpty()) { state.remove(GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY) }
  else { state[GROUP_ANNOUNCEMENT_QUEUES_STATE_KEY] = announcementQueues }

  logInfo("Cleared parent-app pending commands for ${deviceDni}; discardedAnnouncements=${clearedAnnouncementIds.size()}")
}

void clearPendingAnnouncementCommands(Map item) {
  if(!(item instanceof Map)) { return }
  String correlationId = item.correlationId?.toString()
  if(!correlationId) { return }
  Map<String, Map> queues = state[AUDIO_CLIP_QUEUES_STATE_KEY] instanceof Map
      ? (Map<String, Map>)state[AUDIO_CLIP_QUEUES_STATE_KEY] : [:]
  normalizeGroupPlayerIds(item.playerIds).each { String playerId ->
    Map queue = queues[playerId] instanceof Map ? (Map)queues[playerId] : null
    if(queue == null) { return }
    ['regular', 'high'].each { String priorityQueue ->
      List<Map> pending = queue[priorityQueue] instanceof List ? (List<Map>)queue[priorityQueue] : []
      queue[priorityQueue] = pending.findAll { Map queued ->
        queued?.correlationId?.toString() != correlationId &&
            queued?.groupCorrelationId?.toString() != correlationId
      }
    }
    ['active', 'suspended'].each { String slot ->
      Map active = queue[slot] instanceof Map ? (Map)queue[slot] : null
      if(active?.correlationId?.toString() == correlationId ||
          active?.groupCorrelationId?.toString() == correlationId) { queue.remove(slot) }
    }
    if(!(queue.active instanceof Map) && !(queue.suspended instanceof Map) &&
        ((List)queue.regular).isEmpty() && ((List)queue.high).isEmpty()) {
      queues.remove(playerId)
    } else { queues[playerId] = queue }
  }
  if(queues.isEmpty()) { state.remove(AUDIO_CLIP_QUEUES_STATE_KEY) }
  else { state[AUDIO_CLIP_QUEUES_STATE_KEY] = queues }
}

Boolean isRequestOlderThanDeviceCommandClear(String deviceDni, Object createdAt) {
  if(!deviceDni || createdAt == null || !(state[DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY] instanceof Map)) { return false }
  try {
    Long clearedAt = ((Map)state[DEVICE_COMMAND_CLEAR_TIMES_STATE_KEY])[deviceDni]?.toString()?.toLong()
    Long requestCreatedAt = createdAt.toString().toLong()
    return clearedAt != null && requestCreatedAt <= clearedAt
  } catch(Exception ignored) {
    return false
  }
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
    return NumberUtils.toInt(request.sequence.toString(), Integer.MAX_VALUE)
  }
  String requestId = request?.requestId as String
  if(!requestId) { return Integer.MAX_VALUE }
  String sequenceText = requestId.contains('-')
      ? requestId.substring(requestId.lastIndexOf('-') + 1)
      : requestId
  return NumberUtils.toInt(sequenceText, Integer.MAX_VALUE)
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
      activeOperation.phase != 'COMPLETE' &&
      isPlaybackCommandThatWaitsForTopology(command)) {
    // Piston runs commonly send media or transport commands immediately after
    // explicit group activation. Hold transport commands until that requested
    // topology operation completes; Favorite and playlist loads go directly to the coordinator.
    logDebug("Waiting for topology change to complete before processing group command ${command} for ${groupDni}")
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
    ChildDeviceWrapper currentMediaCoordinator

    switch(command) {
      case 'on':
      case 'groupPlayers':
        handleOnExactGroupImmediate(groupDevice)
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
          coordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], followerIds]]))
        } else if(!coordinator) {
          logWarn("Group command ${command} could not resolve coordinator ${coordinatorId}")
        }
        break

      case 'joinPlayersToCoordinator':
        handleJoinAdditiveGroupImmediate(groupDevice)
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
        currentMediaCoordinator = resolveCurrentGroupCommandCoordinator(playerIds, rinconMap)
        if(currentMediaCoordinator) {
          currentMediaCoordinator.loadFavorite(args.favoriteId?.toString())
        } else {
          logWarn("Cannot load Favorite for ${groupDni}: current coordinator is unavailable")
        }
        break
      case 'loadFavoriteFull':
        currentMediaCoordinator = resolveCurrentGroupCommandCoordinator(playerIds, rinconMap)
        if(currentMediaCoordinator) {
          currentMediaCoordinator.loadFavoriteFull(args.favoriteId?.toString(),
            args.repeatMode?.toString() ?: 'off', args.queueMode?.toString() ?: 'replace',
            args.shuffleMode?.toString() ?: 'off', args.autoPlay?.toString() ?: 'true',
            args.crossfadeMode?.toString() ?: 'on')
        } else {
          logWarn("Cannot load Favorite for ${groupDni}: current coordinator is unavailable")
        }
        break
      case 'getPlaylists':
        if(coordinator) { coordinator.getPlaylists() }
        break
      case 'loadPlaylist':
        currentMediaCoordinator = resolveCurrentGroupCommandCoordinator(playerIds, rinconMap)
        if(currentMediaCoordinator) {
          currentMediaCoordinator.loadPlaylist(args.playlistId?.toString())
        }
        break
      case 'loadPlaylistFull':
        currentMediaCoordinator = resolveCurrentGroupCommandCoordinator(playerIds, rinconMap)
        if(currentMediaCoordinator) {
          currentMediaCoordinator.loadPlaylistFull(
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
    logWarn("Group command ${command} failed for ${groupDni}, requestId=${requestId ?: 'unknown'}, correlation=${args.correlationId ?: 'none'}: ${e.message}")
  }
}

Boolean isGroupOperationCommand(String command) {
  return command in [
    'on', 'groupPlayers',
    'evictUnlistedPlayers', 'joinPlayersToCoordinator',
    'off', 'removePlayersFromCoordinator', 'ungroupPlayers'
  ]
}

Boolean isPlaybackCommandThatWaitsForTopology(String command) {
  return command in [
    'play', 'pause', 'stop', 'nextTrack', 'previousTrack', 'playTrack', 'setTrack',
    'playAudioClip', 'playHighPriorityTrack', 'enqueueLowPriorityTrack',
    'setRepeatMode', 'repeatOne', 'repeatAll', 'repeatNone',
    'setShuffle', 'shuffleOn', 'shuffleOff', 'setCrossfade', 'enableCrossfade', 'disableCrossfade'
  ]
}

Boolean isPlayerPlaybackCommandThatWaitsForGroupOperation(String command) {
  return command in [
    'play', 'pause', 'stop', 'nextTrack', 'previousTrack', 'setTrack', 'selectLineIn', 'selectTV', 'playTrack',
    'playHighPriorityTrack', 'loadAudioClip', 'loadHighPriorityAudioClip',
    'speak', 'speakNoRestore', 'playHighPriorityTTS',
    'setRepeatMode', 'repeatOne', 'repeatAll', 'repeatNone',
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
  if(operation == null || !operation.groupDni || !operation.groupingMode) {
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
  Map activeOperation = getActiveGroupOperation()
  logInfo("Deferring group command ${request.command} for ${request.groupDni} until group operation ${activeOperation?.operationId ?: '(unknown)'} completes; phase=${activeOperation?.phase ?: 'unknown'}, queueDepth=${deferred.size()}")
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
  logDebug("Draining deferred group command ${nextRequest.command} for ${nextRequest.groupDni}; remaining=${deferred.size()}")
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
  return new TreeSet<String>(ids).toList()
}

Object getGroupOperationChildValue(ChildDeviceWrapper player, String name) {
  if(player == null) { return null }
  Object value = player.getDataValue(name)
  if(value == null || StringUtils.isBlank(value.toString())) {
    try {
      value = player.currentValue(name, true)
    } catch(Exception ignored) {
      value = player.currentValue(name)
    }
  }
  return value
}

Boolean isSonosCoordinator(ChildDeviceWrapper player) {
  if(player == null) { return false }
  String playerId = player.getDataValue('id')?.toString()
  String coordinatorId = player.getDataValue('groupCoordinatorId')?.toString()
  return playerId != null && playerId != '' && playerId == coordinatorId
}

List<String> getDeviceDataFollowerIds(DeviceWrapper groupDevice) {
  String raw = groupDevice.getDataValue('playerIds') as String
  if(!raw) { return [] }
  return raw.split(',').collect { String s -> s?.trim() }.findAll { String s -> s }
}

Map getDeviceDataExactTarget(DeviceWrapper groupDevice) {
  String coordinatorId = groupDevice.getDataValue('groupCoordinatorId') as String
  if(coordinatorId != null) { coordinatorId = coordinatorId.trim() }
  List<String> followers = getDeviceDataFollowerIds(groupDevice)
  followers = new TreeSet<String>(followers.findAll { String id -> id != coordinatorId }).toList()
  List<String> required = coordinatorId ? ([coordinatorId] + followers) : followers
  return [coordinatorId: coordinatorId, requiredPlayerIds: required, followerIds: followers]
}

void handleOnExactGroupImmediate(ChildDeviceWrapper groupDevice) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  Map<String, Object> target = getDeviceDataExactTarget(groupDevice)
  String coordinatorId = target.coordinatorId as String
  List<String> requiredIds = (List<String>)target.requiredPlayerIds
  if(!coordinatorId) {
    logWarn("on() ignored for ${groupDevice.getDeviceNetworkId()}: no groupCoordinatorId in device data")
    return
  }
  ChildDeviceWrapper desiredDevice = rinconMap[coordinatorId]
  if(desiredDevice == null) {
    logWarn("on() ignored for ${groupDevice.getDeviceNetworkId()}: could not resolve coordinator ${coordinatorId}")
    return
  }
  logDebug("on() exact target coordinator=${coordinatorId} required=${requiredIds} for ${groupDevice.getDeviceNetworkId()}")
  String groupId = getGroupOperationChildValue(desiredDevice, 'groupId')?.toString()
  String coordinatorOfDesired = getGroupOperationChildValue(desiredDevice, 'groupCoordinatorId')?.toString()
  List<String> membersOfDesired = normalizeGroupPlayerIds(getGroupOperationChildValue(desiredDevice, 'groupPlayerIds'))
  Boolean desiredIsCoordinator = isSonosCoordinator(desiredDevice)
  if(coordinatorOfDesired != coordinatorId && groupId && membersOfDesired.contains(coordinatorId) && !desiredIsCoordinator) {
    ChildDeviceWrapper prevCoordinator = coordinatorOfDesired ? rinconMap[coordinatorOfDesired] : null
    if(prevCoordinator != null) {
      Map<String, Map> finalExpectation = [:]
      requiredIds.each { String pid ->
        finalExpectation[pid] = [coordinatorId: coordinatorId, memberIds: requiredIds]
      }
      logInfo("on() removing coordinator ${coordinatorId} from ${coordinatorOfDesired} before exact assembly for ${groupDevice.getDeviceNetworkId()}")
      prevCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], [coordinatorId], finalExpectation]]))
      runInMillis(1, 'onAssemblyAfterDetach', [data: [groupDni: groupDevice.getDeviceNetworkId(), retry: 0]])
      return
    }
    logWarn("on() could not resolve previous coordinator ${coordinatorOfDesired} to detach ${coordinatorId}")
    return
  }
  List<String> requiredSet = normalizeGroupPlayerIds(requiredIds)
  List<String> attachedFollowerIds = []
  Map<String, List<String>> followersByOldCoordinator = [:].withDefault { [] as List<String> }
  requiredIds.each { String pid ->
    if(pid == coordinatorId) { return }
    ChildDeviceWrapper f = rinconMap[pid]
    if(f == null) { return }
    List<String> fMembers = normalizeGroupPlayerIds(getGroupOperationChildValue(f, 'groupPlayerIds'))
    if(fMembers.isEmpty()) { return }
    String fGroupId = getGroupOperationChildValue(f, 'groupId')?.toString()
    String fCoord = getGroupOperationChildValue(f, 'groupCoordinatorId')?.toString()
    if(fGroupId == groupId && fCoord == coordinatorId) { return }
    Boolean hasOutside = fMembers.any { String m -> !requiredSet.contains(m) }
    if(hasOutside) {
      if(fCoord) {
        followersByOldCoordinator[fCoord] << pid
        attachedFollowerIds << pid
      }
    }
  }
  if(!followersByOldCoordinator.isEmpty()) {
    String oldCoordId = followersByOldCoordinator.keySet().first()
    List<String> toDetach = followersByOldCoordinator[oldCoordId].unique()
    ChildDeviceWrapper detachDevice = rinconMap[oldCoordId]
    if(detachDevice != null) {
      logInfo("on() detaching requested followers ${toDetach} from old group ${oldCoordId} before exact assembly for ${groupDevice.getDeviceNetworkId()}")
      detachDevice.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], toDetach]]))
      runInMillis(1, 'onAssemblyAfterDetach', [data: [groupDni: groupDevice.getDeviceNetworkId(), retry: retries+1]])
      return
    }
  }
  Boolean isSolo = !groupId || membersOfDesired.isEmpty() || (membersOfDesired.size() == 1 && membersOfDesired[0] == coordinatorId)
  if(isSolo) {
    List<String> ordered = [coordinatorId] + requiredIds.findAll { String pid -> pid != coordinatorId }
    if(ordered.size() > 1) {
      logInfo("on() creating exact group ${ordered} with coordinator ${coordinatorId} for ${groupDevice.getDeviceNetworkId()}")
      desiredDevice.playerCreateGroupRequest(JsonOutput.toJson([args: [ordered, null]]))
    } else {
      logDebug("on() already exact singleton for ${groupDevice.getDeviceNetworkId()}")
    }
    return
  }
  if(!desiredIsCoordinator) {
    // coordinator claims coordinatorId but isGroupCoordinator still off — treat as detached, assemble by create
    List<String> ordered = [coordinatorId] + requiredIds.findAll { String pid -> pid != coordinatorId }
    logInfo("on() creating exact group ${ordered} with coordinator ${coordinatorId} for ${groupDevice.getDeviceNetworkId()}")
    desiredDevice.playerCreateGroupRequest(JsonOutput.toJson([args: [ordered, null]]))
    return
  }
  List<String> extras = membersOfDesired.findAll { String m -> !requiredSet.contains(m) }
  List<String> missing = requiredIds.findAll { String pid -> !membersOfDesired.contains(pid) }
  if(extras.isEmpty() && missing.isEmpty()) {
    return
  }
  if(extras && missing) {
    logInfo("on() evicting ${extras} and adding ${missing} in one step for ${groupDevice.getDeviceNetworkId()}")
    desiredDevice.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [missing, extras]]))
  } else if(extras) {
    logInfo("on() evicting extras ${extras} for ${groupDevice.getDeviceNetworkId()}")
    desiredDevice.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], extras]]))
  } else if(missing) {
    logInfo("on() adding missing ${missing} for ${groupDevice.getDeviceNetworkId()}")
    desiredDevice.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [missing, []]]))
  }
}

void onAssemblyAfterDetach(Map data = [:]) {
  ChildDeviceWrapper groupDevice = getChildDevice(data?.groupDni as String)
  if(groupDevice == null) { return }
  Integer retry = (data?.retry ?: 0) as Integer
  if(retry > 10) {
    logWarn("on() assembly exceeded detach retries for ${groupDevice.getDeviceNetworkId()}, forcing exact create")
    Map<String, Object> target = getDeviceDataExactTarget(groupDevice)
    String coordinatorId = target.coordinatorId as String
    List<String> requiredIds = (List<String>)target.requiredPlayerIds
    Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
    ChildDeviceWrapper desiredDevice = coordinatorId ? rinconMap[coordinatorId] : null
    if(desiredDevice != null && requiredIds.size() > 1) {
      List<String> ordered = [coordinatorId] + requiredIds.findAll { String pid -> pid != coordinatorId }
      desiredDevice.playerCreateGroupRequest(JsonOutput.toJson([args: [ordered, null]]))
    }
    return
  }
  handleOnExactGroupImmediate(groupDevice)
}

void handleJoinAdditiveGroupImmediate(ChildDeviceWrapper groupDevice) {
  Map<String, Object> target = getDeviceDataExactTarget(groupDevice)
  String coordinatorId = target.coordinatorId as String
  List<String> requiredIds = (List<String>)target.requiredPlayerIds
  List<String> followerIds = (List<String>)target.followerIds
  if(!coordinatorId) {
    logWarn("joinPlayersToCoordinator() ignored for ${groupDevice.getDeviceNetworkId()}: no groupCoordinatorId in device data")
    return
  }
  if(followerIds.isEmpty()) {
    logDebug("joinPlayersToCoordinator() no followers for ${groupDevice.getDeviceNetworkId()}")
    return
  }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  ChildDeviceWrapper desiredDevice = rinconMap[coordinatorId]
  if(desiredDevice == null) {
    logWarn("joinPlayersToCoordinator() ignored for ${groupDevice.getDeviceNetworkId()}: could not resolve coordinator ${coordinatorId}")
    return
  }
  logDebug("joinPlayersToCoordinator() adaptive target coordinator=${coordinatorId} required=${requiredIds} for ${groupDevice.getDeviceNetworkId()}")
  String groupId = getGroupOperationChildValue(desiredDevice, 'groupId')?.toString()
  String coordinatorOfDesired = getGroupOperationChildValue(desiredDevice, 'groupCoordinatorId')?.toString()
  List<String> membersOfDesired = normalizeGroupPlayerIds(getGroupOperationChildValue(desiredDevice, 'groupPlayerIds'))
  Boolean desiredIsCoordinator = isSonosCoordinator(desiredDevice)
  if(coordinatorOfDesired != coordinatorId && groupId && membersOfDesired.contains(coordinatorId) && !desiredIsCoordinator) {
    ChildDeviceWrapper prevCoordinator = coordinatorOfDesired ? rinconMap[coordinatorOfDesired] : null
    if(prevCoordinator != null) {
      List<String> preservedMembers = normalizeGroupPlayerIds(membersOfDesired)
      if(preservedMembers.isEmpty()) { preservedMembers = requiredIds }
      preservedMembers = new TreeSet<String>(preservedMembers + requiredIds).toList()
      Map<String, Map> finalExpectation = [:]
      preservedMembers.each { String pid ->
        finalExpectation[pid] = [coordinatorId: coordinatorId, memberIds: preservedMembers]
      }
      logInfo("joinPlayersToCoordinator() removing coordinator ${coordinatorId} from ${coordinatorOfDesired} before join for ${groupDevice.getDeviceNetworkId()}")
      prevCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], [coordinatorId], finalExpectation]]))
      runInMillis(1, 'joinAssemblyAfterDetach', [data: [groupDni: groupDevice.getDeviceNetworkId(), retry: 0]])
      return
    }
    logWarn("joinPlayersToCoordinator() could not resolve previous coordinator ${coordinatorOfDesired}")
    return
  }
  List<String> missing = requiredIds.findAll { String pid -> pid != coordinatorId && !membersOfDesired.contains(pid) }
  if(missing.isEmpty()) {
    return
  }
  Boolean isSolo = !groupId || membersOfDesired.isEmpty() || (membersOfDesired.size() == 1 && membersOfDesired[0] == coordinatorId)
  if(isSolo) {
    List<String> ordered = [coordinatorId] + requiredIds.findAll { String pid -> pid != coordinatorId }
    logInfo("joinPlayersToCoordinator() creating group ${ordered} with coordinator ${coordinatorId} for ${groupDevice.getDeviceNetworkId()}")
    desiredDevice.playerCreateGroupRequest(JsonOutput.toJson([args: [ordered, null]]))
    return
  }
  if(!desiredIsCoordinator) {
    List<String> ordered = [coordinatorId] + requiredIds.findAll { String pid -> pid != coordinatorId }
    logInfo("joinPlayersToCoordinator() creating group ${ordered} with coordinator ${coordinatorId} for ${groupDevice.getDeviceNetworkId()}")
    desiredDevice.playerCreateGroupRequest(JsonOutput.toJson([args: [ordered, null]]))
    return
  }
  logInfo("joinPlayersToCoordinator() adding ${missing} to coordinator ${coordinatorId} for ${groupDevice.getDeviceNetworkId()}")
  desiredDevice.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [missing, []]]))
}

void joinAssemblyAfterDetach(Map data = [:]) {
  ChildDeviceWrapper groupDevice = getChildDevice(data?.groupDni as String)
  if(groupDevice == null) { return }
  Integer retry = (data?.retry ?: 0) as Integer
  if(retry > 10) {
    logWarn("joinPlayersToCoordinator() assembly exceeded detach retries for ${groupDevice.getDeviceNetworkId()}, forcing create")
    Map<String, Object> target = getDeviceDataExactTarget(groupDevice)
    String coordinatorId = target.coordinatorId as String
    List<String> requiredIds = (List<String>)target.requiredPlayerIds
    Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
    ChildDeviceWrapper desiredDevice = coordinatorId ? rinconMap[coordinatorId] : null
    if(desiredDevice != null && requiredIds.size() > 1) {
      List<String> ordered = [coordinatorId] + requiredIds.findAll { String pid -> pid != coordinatorId }
      desiredDevice.playerCreateGroupRequest(JsonOutput.toJson([args: [ordered, null]]))
    }
    return
  }
  handleJoinAdditiveGroupImmediate(groupDevice)
}

Map buildGroupOperationTarget(ChildDeviceWrapper groupDevice, String groupingMode) {
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  List<String> requiredPlayerIds = new TreeSet<String>(getAllPlayersForGroupDevice(groupDevice).collect { String id -> id?.trim() }
      .findAll { String id -> id }).toList()
  Map configuredGroup = getConfiguredGroupDefinition(groupDevice)
  String configuredCoordinatorId = groupDevice.getDataValue('groupCoordinatorId') as String ?:
      configuredGroup.groupCoordinatorId as String
  if(configuredCoordinatorId && !requiredPlayerIds.contains(configuredCoordinatorId)) {
    requiredPlayerIds.add(configuredCoordinatorId)
    requiredPlayerIds = new TreeSet<String>(requiredPlayerIds).toList()
  }
  if(configuredGroup.playerIds instanceof Collection) {
    requiredPlayerIds = new TreeSet<String>(requiredPlayerIds + normalizeGroupPlayerIds(configuredGroup.playerIds)).toList()
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

Map buildNewGroupOperation(ChildDeviceWrapper groupDevice, String groupingMode, Map target) {
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
    topologyDeadlineAt: startTime + GROUP_OPERATION_DEADLINE_SECONDS * 1000L,
    topologyAttempt: 0,
    stableObservations: 0,
    topologyFingerprint: null,
    topologyLastObservedAt: 0L,
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
    evictUnlistedOnly: false
  ]
  return operation
}

/**
 * Store the expected end topology on each affected player device. Device data
 * values are shared with that device's driver instance and are hidden from the
 * Current States table.
 */
void startGroupTopologyOperation(ChildDeviceWrapper groupDevice, String groupingMode, Map args = [:]) {
  String groupDni = groupDevice?.getDeviceNetworkId()
  if(!groupDni) { return }
  String normalizedMode = normalizeGroupOperationMode(groupingMode, GROUPING_MODE_EXPLICIT)
  Map active = getActiveGroupOperation()
  if(active && active.groupDni as String == groupDni) {
    if(active.groupingMode == normalizedMode && active.phase != 'COMPLETE') {
      runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupTopologyOperation', [
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
  runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupTopologyOperation', [
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
      player.registerGroupTopologyOperation(operation.operationId as String, requestId)
      registered.add(playerId)
      requestedAt[playerId] = now()
      logDebug("Registered operation ${operation.operationId} on ${playerId} with request ${requestId}")
    } catch(Exception e) {
      requestedAt[playerId] = now()
      logWarn("Could not register group operation ${operation.operationId} on ${playerId}: ${e.message}")
    }
  }
  operation.registeredPlayerIds = new TreeSet<String>(registered).toList()
  operation.registrationRequestIds = requestIds
  operation.registrationRequestedAt = requestedAt
  saveActiveGroupOperation(operation)
}



Map getCurrentHouseholdTopology() {
  return getCurrentReceivedZgtTopology()
}

Map buildGroupOperationTopologyObservations(Map operation, Map householdTopology) {
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation?.requiredPlayerIds)
  List<Map> groups = householdTopology?.groups instanceof List
      ? (List<Map>)householdTopology.groups
      : []
  if(requiredPlayerIds.isEmpty() || groups.isEmpty()) { return [:] }

  Long observedAt = (householdTopology.observedAt ?: now()) as Long
  Map<String, Map> observations = [:]
  requiredPlayerIds.each { String playerId ->
    Map group = groups.find { Map item ->
      normalizeGroupPlayerIds(item?.playerIds).contains(playerId)
    }
    observations[playerId] = [
      groupId: group?.groupId?.toString(),
      coordinatorId: group?.coordinatorId?.toString(),
      playerIds: normalizeGroupPlayerIds(group?.playerIds),
      observedAt: observedAt
    ]
  }
  return observations
}

Map readGroupTopologyFromObservations(Map operation, Map recordedObservations) {
  List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation?.requiredPlayerIds)
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  Map observationsByPlayer = recordedObservations instanceof Map ? recordedObservations : [:]
  List<Map> observations = []
  requiredPlayerIds.each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    Map recorded = observationsByPlayer[playerId] instanceof Map
        ? (Map)observationsByPlayer[playerId]
        : null
    Long observedAt = recorded?.observedAt as Long
    Boolean fresh = recorded != null
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
  Map householdTopology = getCurrentHouseholdTopology()
  if(!householdTopology || householdTopology.isEmpty()) {
    householdTopology = getCurrentReceivedZgtTopology()
  }
  Map<String, Map> householdObservations =
      buildGroupOperationTopologyObservations(operation, householdTopology)
  if(!householdObservations.isEmpty()) {
    // Every player reports its own subscribed household ZGT snapshot.
    // Its latest source-observed message is authoritative over per-player caches.
    operation.topologyObservations = householdObservations
    return readGroupTopologyFromObservations(operation, householdObservations)
  }

  Map observations = operation?.topologyObservations instanceof Map
      ? (Map)operation.topologyObservations
      : [:]
  Map topology = readGroupTopologyFromObservations(operation, observations)
  List<Map> freshObservations = topology.observations instanceof List
      ? ((List<Map>)topology.observations).findAll { Map item -> item.fresh == true }
      : []
  if(freshObservations.isEmpty()) {
    return topology
  }

  // Before a household ZGT snapshot is available, a single player roster can
  // still confirm the requested members and coordinator.
  String preferredReporterId = operation?.desiredCoordinatorId as String
  if(!preferredReporterId && operation?.groupingMode == GROUPING_MODE_CURRENT) {
    preferredReporterId = operation?.resolvedCoordinatorId as String
  }
  List<Map> orderedObservations = preferredReporterId
      ? freshObservations.findAll { Map item -> item.playerId?.toString() == preferredReporterId } +
          freshObservations.findAll { Map item -> item.playerId?.toString() != preferredReporterId }
      : freshObservations
  Map acceptedObservation = orderedObservations.find { Map item ->
    Map candidate = [fresh: true, consistent: true, groupId: item.groupId,
        coordinatorId: item.coordinatorId, playerIds: item.playerIds]
    return isGroupTopologySatisfied(operation, candidate)
  }
  if(acceptedObservation == null) {
    return topology
  }
  List<String> playerIds = normalizeGroupPlayerIds(acceptedObservation.playerIds)
  Long acceptedObservedAt = acceptedObservation.observedAt as Long ?: 0L
  Boolean newerConflictingReport = freshObservations.any { Map item ->
    Long observedAt = item.observedAt as Long ?: 0L
    observedAt > acceptedObservedAt &&
        (item.groupId?.toString() != acceptedObservation.groupId?.toString() ||
         item.coordinatorId?.toString() != acceptedObservation.coordinatorId?.toString() ||
         normalizeGroupPlayerIds(item.playerIds) != playerIds)
  }
  if(newerConflictingReport) {
    return topology
  }
  topology.fresh = true
  topology.consistent = true
  topology.groupId = acceptedObservation.groupId as String
  topology.coordinatorId = acceptedObservation.coordinatorId as String
  topology.playerIds = playerIds
  topology.observations = [acceptedObservation]
  topology.authorityPlayerId = acceptedObservation.playerId as String
  topology.fingerprint = "${acceptedObservation.playerId}:${acceptedObservation.groupId}:${acceptedObservation.coordinatorId}:${playerIds.join(',')}"
  return topology
}

Boolean explicitFollowersConfirmGroupWasNotApplied(Map operation, Map topology) {
  if(operation?.groupingMode != GROUPING_MODE_EXPLICIT || !(topology?.observations instanceof List)) {
    return false
  }
  String coordinatorId = operation.desiredCoordinatorId as String
  List<String> followers = normalizeGroupPlayerIds(operation.requiredPlayerIds)
      .findAll { String playerId -> playerId != coordinatorId }
  if(!followers) { return false }
  List<Map> observations = (List<Map>)topology.observations
  return followers.every { String playerId ->
    Map observation = observations.find { Map item -> item.playerId?.toString() == playerId }
    observation?.fresh == true && observation.groupId && observation.coordinatorId == playerId &&
        normalizeGroupPlayerIds(observation.playerIds) == [playerId]
  }
}

Boolean isExplicitCoordinatorSolo(Map topology, String coordinatorId) {
  if(topology == null || coordinatorId == null) { return false }
  List<Map> observations = topology?.observations instanceof List ? (List<Map>)topology.observations : []
  if(observations.isEmpty()) { return false }
  Map coordinatorObservation = observations.find { Map item -> item.playerId?.toString() == coordinatorId && item.fresh == true }
  if(coordinatorObservation == null) { return false }
  if(coordinatorObservation.coordinatorId?.toString() != coordinatorId) { return false }
  List<String> members = normalizeGroupPlayerIds(coordinatorObservation.playerIds)
  return members == [coordinatorId]
}

List<Map> getExplicitAttachedFollowerObservations(Map topology, String coordinatorId, List<String> requiredPlayerIds) {
  if(topology == null || requiredPlayerIds == null || requiredPlayerIds.isEmpty()) { return [] }
  List<Map> observations = topology?.observations instanceof List ? (List<Map>)topology.observations : []
  if(observations.isEmpty()) { return [] }
  Set<String> requiredSet = new HashSet<String>(normalizeGroupPlayerIds(requiredPlayerIds))
  List<Map> attached = []
  requiredPlayerIds.each { String playerId ->
    if(playerId == coordinatorId) { return }
    Map observation = observations.find { Map item -> item.playerId?.toString() == playerId && item.fresh == true }
    if(observation == null) { return }
    if(!observation.groupId || !observation.coordinatorId) { return }
    List<String> members = normalizeGroupPlayerIds(observation.playerIds)
    if(members.isEmpty()) { return }
    Boolean hasOutside = members.any { String memberId -> !requiredSet.contains(memberId) }
    if(hasOutside) { attached.add(observation) }
  }
  return attached
}

Boolean isExplicitCoordinatorTransferDetached(Map topology, String coordinatorId, List<String> requiredPlayerIds) {
  if(topology == null || coordinatorId == null || requiredPlayerIds == null || requiredPlayerIds.isEmpty()) { return false }
  List<Map> observations = topology?.observations instanceof List ? (List<Map>)topology.observations : []
  if(observations.isEmpty()) { return false }
  Set<String> requiredSet = new HashSet<String>(normalizeGroupPlayerIds(requiredPlayerIds))
  if(requiredSet.isEmpty() || !requiredSet.contains(coordinatorId)) { return false }
  Boolean allDetached = requiredPlayerIds.every { String playerId ->
    Map observation = observations.find { Map item -> item.playerId?.toString() == playerId && item.fresh == true }
    if(observation == null) { return false }
    if(!observation.groupId || !observation.coordinatorId) { return false }
    List<String> members = normalizeGroupPlayerIds(observation.playerIds)
    if(members.isEmpty()) { return false }
    return requiredSet.containsAll(new HashSet<String>(members))
  }
  if(!allDetached) { return false }
  Map coordinatorObservation = observations.find { Map item -> item.playerId?.toString() == coordinatorId && item.fresh == true }
  if(coordinatorObservation == null) { return false }
  if(coordinatorObservation.coordinatorId?.toString() != coordinatorId) { return false }
  List<String> coordinatorMembers = normalizeGroupPlayerIds(coordinatorObservation.playerIds)
  return coordinatorMembers == [coordinatorId]
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
  Boolean isCoordinator = isSonosCoordinator(coordinator)
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



void requestGroupOperationTopology(Map operation, ChildDeviceWrapper player) {
  if(operation == null || player == null) { return }
  try {
    player.playerGetGroupsFull()
  } catch(Exception e) {
    logDebug("Could not refresh group topology from ${player.getDataValue('id')}: ${e.message}")
  }
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
      requestGroupOperationTopology(operation, coordinator)
      ChildDeviceWrapper previousCoordinator = rinconMap[operation.coordinatorTransferPreviousCoordinatorId as String]
      if(previousCoordinator != null && previousCoordinator != coordinator) {
        requestGroupOperationTopology(operation, previousCoordinator)
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
        coordinator.playerCreateGroupRequest(JsonOutput.toJson([args: [playerIdsInCoordinatorOrder, operation.musicContextGroupId as String]]))
        operation.coordinatorTransferGroupCreated = true
        operation.topologyMutationSent = true
      } else {
        operation.lastCommand = 'refreshGroupTopology'
        requestGroupOperationTopology(operation, coordinator)
        ChildDeviceWrapper previousCoordinator = rinconMap[operation.coordinatorTransferPreviousCoordinatorId as String]
        if(previousCoordinator != null && previousCoordinator != coordinator) {
          requestGroupOperationTopology(operation, previousCoordinator)
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
      previousCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], [coordinatorId], finalTopologyExpectation]]))
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
      coordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [followersToAdd, []]]))
      operation.topologyMutationSent = true
    } else {
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology check ${operation.topologyRequestId} for group operation ${operation.operationId}")
      requestGroupOperationTopology(operation, coordinator)
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
      // away from the configured active source player.
      operation.lastCommand = 'refreshGroupTopology'
      logDebug("Waiting for a fresh topology with configured coordinator ${configuredCoordinatorId} before evicting unlisted players")
      requestGroupOperationTopology(operation, configuredCoordinator)
    } else {
      List<String> requiredPlayerIds = normalizeGroupPlayerIds(operation.requiredPlayerIds)
      List<String> observedPlayerIds = normalizeGroupPlayerIds(topology.playerIds)
      List<String> extras = observedPlayerIds.findAll { String id -> !requiredPlayerIds.contains(id) }
      if(extras) {
        operation.lastCommand = 'removeExtraPlayers'
        operation.topologyRequestId = UUID.randomUUID().toString()
        logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
        logInfo("Evicting unlisted players ${extras} from group ${topology.groupId} using configured coordinator ${configuredCoordinatorId}")
        configuredCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], extras]]))
        topologyMutationSentNow = true
      } else {
        operation.lastCommand = 'refreshGroupTopology'
        requestGroupOperationTopology(operation, configuredCoordinator)
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
      Boolean aggregateDetached = topology?.fresh == true && topology.coordinatorId == coordinatorId
      Boolean perPlayerDetached = isExplicitCoordinatorTransferDetached(topology, coordinatorId, requiredPlayerIds)
      if(aggregateDetached || perPlayerDetached) {
        // The configured coordinator has left the old group. It can now
        // create the requested group without Sonos retaining the old leader.
        // Fresh per-player reports can disagree with each other during the
        // handoff, so an inconsistent aggregate still confirms the split.
        if(perPlayerDetached && !aggregateDetached) {
          logDebug("Explicit coordinator transfer confirmed from fresh per-player observations (coordinator=${coordinatorId})")
        }
        operation.coordinatorTransferStarted = false
      } else if(isExplicitCoordinatorSolo(topology, coordinatorId)) {
        List<Map> attachedFollowers = getExplicitAttachedFollowerObservations(topology, coordinatorId, requiredPlayerIds)
        if(!attachedFollowers.isEmpty()) {
          Map<String, List<String>> followersByCoordinator = [:].withDefault { [] as List<String> }
          attachedFollowers.each { Map observation ->
            String currentCoordinatorId = observation.coordinatorId.toString()
            followersByCoordinator[currentCoordinatorId] << observation.playerId.toString()
          }
          String detachCoordinatorId = followersByCoordinator.keySet().first()
          List<String> followersToDetach = followersByCoordinator[detachCoordinatorId].unique()
          ChildDeviceWrapper detachCoordinator = rinconMap[detachCoordinatorId]
          if(detachCoordinator != null && followersToDetach) {
            operation.lastCommand = 'detachFollowerFromOldGroup'
            operation.topologyRequestId = UUID.randomUUID().toString()
            logInfo("Detaching requested followers ${followersToDetach} from old group ${detachCoordinatorId} before creating exact group with ${coordinatorId} for group operation ${operation.operationId}")
            logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
            detachCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], followersToDetach]]))
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
        operation.lastCommand = 'refreshGroupTopology'
        operation.lastCommandAt = now()
        operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
        saveActiveGroupOperation(operation)
        requestGroupOperationTopology(operation, coordinator)
        ChildDeviceWrapper previousCoordinator = topology?.coordinatorId
            ? rinconMap[topology.coordinatorId as String]
            : null
        if(previousCoordinator != null && previousCoordinator != coordinator) {
          requestGroupOperationTopology(operation, previousCoordinator)
        }
        return
      } else {
        // Wait for Sonos to publish the split before issuing the second step.
        // Repeating createGroup against the still-joined players preserves the
        // existing coordinator and cannot complete a coordinator transfer.
        operation.lastCommand = 'refreshGroupTopology'
        operation.lastCommandAt = now()
        operation.topologyAttempt = ((operation.topologyAttempt ?: 0) as Integer) + 1
        saveActiveGroupOperation(operation)
        requestGroupOperationTopology(operation, coordinator)
        ChildDeviceWrapper previousCoordinator = topology?.coordinatorId
            ? rinconMap[topology.coordinatorId as String]
            : null
        if(previousCoordinator != null && previousCoordinator != coordinator) {
          requestGroupOperationTopology(operation, previousCoordinator)
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
    // When the requested coordinator already leads a valid subset of the
    // exact target roster, preserve that group and attach only players that
    // are not already bound into another multi-player group. Rebuilding here
    // would interrupt active playback even though Sonos already has the
    // desired coordinator in place.
    Map coordinatorLedTargetSubset = observedTargetGroups.values().find { Map item ->
      item.coordinatorId == coordinatorId &&
          item.memberIds.contains(coordinatorId) &&
          item.memberIds.size() < requiredPlayerSet.size() &&
          item.groupId == topology?.groupId &&
          item.memberIds == normalizeGroupPlayerIds(topology?.playerIds)
    }
    if(topology?.fresh == true && topology?.consistent == true &&
        topology.coordinatorId == coordinatorId && coordinatorLedTargetSubset != null) {
      List<String> missingPlayers = requiredPlayerSet.findAll { String playerId ->
        !coordinatorLedTargetSubset.memberIds.contains(playerId)
      }
      Boolean missingPlayersAreUnattached = missingPlayers.every { String playerId ->
        !topologyObservations.any { Map item ->
          List<String> members = normalizeGroupPlayerIds(item.playerIds)
          item.fresh == true && item.groupId && item.groupId.toString() != coordinatorLedTargetSubset.groupId &&
              members.size() > 1 && members.contains(playerId)
        }
      }
      if(missingPlayers && missingPlayersAreUnattached) {
        operation.lastCommand = 'addMissingPlayers'
        operation.topologyRequestId = UUID.randomUUID().toString()
        logInfo("Expanding target subgroup ${coordinatorLedTargetSubset.memberIds} under its configured coordinator ${coordinatorId}; adding ${missingPlayers} for group operation ${operation.operationId}")
        logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
        coordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [missingPlayers, []]]))
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
        currentCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], followersToSplit]]))
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
      previousCoordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], [coordinatorId], finalTopologyExpectation]]))
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
      coordinator.playerModifyGroupMembersRequest(JsonOutput.toJson([args: [[], extras]]))
      operation.topologyMutationSent = true
    } else {
      operation.lastCommand = 'createExactGroup'
      operation.topologyRequestId = UUID.randomUUID().toString()
      logDebug("Dispatching topology step ${operation.topologyRequestId} for group operation ${operation.operationId}")
      // Put the configured coordinator first so it wins Sonos' equal-size tie-break.
      List<String> playerIdsInCoordinatorOrder = [coordinatorId] +
          requiredPlayerIds.findAll { String playerId -> playerId != coordinatorId }
      coordinator.playerCreateGroupRequest(JsonOutput.toJson([args: [playerIdsInCoordinatorOrder, operation.musicContextGroupId as String]]))
      operation.topologyMutationSent = true
    }
  } else if(operation.groupingMode == GROUPING_MODE_ADDITIVE) {
    sendAdditiveGroupTopologyCommand(operation, topology, rinconMap, requiredPlayerIds)
    return
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
  if(operation == null || (callbackOperationId && callbackOperationId != operation.operationId)) {
    return
  }
  operation.topologyCheckRequestId = UUID.randomUUID().toString()
  logDebug("Starting topology observation ${operation.topologyCheckRequestId} for operation ${operation.operationId}")
  Map householdTopology = getCurrentHouseholdTopology()
  Map<String, Map> householdObservations = buildGroupOperationTopologyObservations(operation, householdTopology)
  Boolean hasHouseholdTopology = !householdObservations.isEmpty()
  operation.topologyObservations = hasHouseholdTopology ? householdObservations : [:]
  if(resetStability == true) {
    operation.stableObservations = 0
    operation.topologyFingerprint = null
    operation.topologyLastObservedAt = 0L
  }
  operation.observedGroupId = null
  operation.observedCoordinatorId = null
  operation.observedPlayerIds = []
  saveActiveGroupOperation(operation)
  if(!hasHouseholdTopology) {
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
}















/** Summarize the operation timeline that explains a topology or Favorite timeout. */
String summarizeGroupOperationProgress(Map operation) {
  if(operation == null) { return 'Operation state was unavailable.' }
  Long createdAt = operation.createdAt as Long ?: now()
  Long deadlineAt = operation.deadlineAt as Long ?: 0L
  List<Map> deferred = state[DEFERRED_GROUP_COMMANDS_STATE_KEY] instanceof List
      ? (List<Map>)state[DEFERRED_GROUP_COMMANDS_STATE_KEY]
      : []
  List<String> deferredCommands = deferred.findAll { Map item ->
    item.groupDni?.toString() == operation.groupDni?.toString()
  }.collect { Map item -> item.command?.toString() ?: 'unknown' }
  Long deadlineRemainingMs = deadlineAt > 0L ? deadlineAt - now() : 0L
  return "operation=${operation.operationId}, elapsedMs=${Math.max(0L, now() - createdAt)}, phase=${operation.phase ?: 'unknown'}, lastCommand=${operation.lastCommand ?: 'none'}, topologyAttempt=${operation.topologyAttempt ?: 0}, stableObservations=${operation.stableObservations ?: 0}, deadlineRemainingMs=${deadlineRemainingMs}, deferredCommands=${deferredCommands}"
}



/** Return the expired deadline phase, if any. */
String getExpiredGroupOperationDeadlinePhase(Map operation) {
  if(operation == null) { return null }
  Long currentTime = now()
  Long hardDeadlineAt = operation.deadlineAt as Long ?: 0L
  Long phaseDeadlineAt = operation.topologyDeadlineAt as Long ?: 0L
  if(phaseDeadlineAt <= 0L) { phaseDeadlineAt = hardDeadlineAt }
  if(phaseDeadlineAt > 0L && currentTime >= phaseDeadlineAt) {
    return 'group topology'
  }
  if(hardDeadlineAt > 0L && currentTime >= hardDeadlineAt) { return 'overall operation' }
  return null
}





void advanceGroupTopologyOperation(Map data = [:]) {
  Map operation = getActiveGroupOperation()
  String callbackOperationId = data?.operationId as String
  if(operation == null || (callbackOperationId && callbackOperationId != operation.operationId)) {
    return
  }
  String expiredDeadlinePhase = getExpiredGroupOperationDeadlinePhase(operation)
  if(expiredDeadlinePhase) {
    String detail = "Timed out during ${expiredDeadlinePhase}. ${summarizeGroupOperationProgress(operation)}. "
    detail += "Last topology: ${summarizeGroupOperationTopology([observations: operation.lastTopologyObservations])}"
    failGroupOperation(operation, 'GROUP_OPERATION_TIMEOUT', detail)
    return
  }
  registerGroupOperationOnPlayers(operation, operation.requiredPlayerIds)

  Map topology = readGroupOperationTopology(operation)
  if(operation.evictUnlistedOnly == true) {
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
  if(operation.groupingMode == GROUPING_MODE_CURRENT && topology.coordinatorId) {
    operation.resolvedCoordinatorId = topology.coordinatorId as String
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
        if(operation.topologyMutationSent == true && operation.lastCommand == 'createExactGroup' &&
            explicitFollowersConfirmGroupWasNotApplied(operation, topology)) {
          // The followers have independently reported that they remain
          // standalone. Retry the explicit topology mutation instead of
          // polling indefinitely when the coordinator's report is missing.
          // The retry remains bounded by GROUP_OPERATION_MAX_TOPOLOGY_ATTEMPTS.
          operation.topologyMutationSent = false
          saveActiveGroupOperation(operation)
          try {
            sendGroupTopologyCommand(operation, topology)
          } catch(Exception e) {
            failGroupOperation(operation, 'GROUP_COMMAND_FAILED', e.message as String)
            return
          }
          publishGroupOperationStatus(operation, 'ENSURING_TOPOLOGY')
          runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupTopologyOperation', [
            overwrite: true,
            data: [operationId: operation.operationId]
          ])
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
      runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupTopologyOperation', [
        overwrite: true,
        data: [operationId: operation.operationId]
      ])
      return
    }
    operation.phase = 'ENSURE_GROUP'
    operation.status = 'ENSURING_TOPOLOGY'
    Long commandAt = operation.lastCommandAt as Long ?: 0L
    Boolean topologyMutationRecentlySent = commandAt > 0L &&
      operation.lastCommand in ['removeExtraPlayers', 'createExactGroup', 'addMissingPlayers',
        'transferCoordinator', 'createAdditiveGroupAfterTransfer', 'splitConflictingTargetGroup'] &&
      now() < commandAt + GROUP_OPERATION_TOPOLOGY_COMMAND_WAIT_SECONDS * 1000L
    Boolean waitingForFreshTopology = topology.fresh != true && commandAt > 0L &&
      now() < commandAt + GROUP_OPERATION_COMMAND_WAIT_SECONDS * 1000L
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
    runIn(GROUP_OPERATION_POLL_SECONDS, 'advanceGroupTopologyOperation', [
      overwrite: true,
      data: [operationId: operation.operationId]
    ])
    return
  }

  if(operation.groupingMode == GROUPING_MODE_EXPLICIT) {
    operation.topologyMutationSent = false
  }
  updateGroupTopologyStability(operation, topology)
  completeGroupOperation(operation)
}

/** Store the elected subscriber's parsed ZGT snapshot and defer player event publication. */
void setZgtPayload(Map<String, Object> payload) {
  if(!(payload?.followersByCoordinator instanceof Map) || !(payload?.groupNamesById instanceof Map)) { return }
  Map<String, List<String>> followersByCoordinator = [:]
  ((Map)payload.followersByCoordinator).each { Object rawCoordinator, Object rawFollowers ->
    String coordinator = rawCoordinator?.toString()
    if(coordinator) {
      List<String> followers = rawFollowers instanceof Collection
          ? ((Collection)rawFollowers).collect { Object id -> id?.toString() }.findAll { String id -> id && id != coordinator }.unique()
          : []
      followersByCoordinator[coordinator] = followers
    }
  }
  Map<String, String> groupNamesById = [:]
  ((Map)payload.groupNamesById).each { Object rawId, Object rawName ->
    String groupId = rawId?.toString()
    if(groupId) { groupNamesById[groupId] = rawName?.toString() ?: groupId }
  }
  Map<String, Object> snapshot = [followersByCoordinator: followersByCoordinator, groupNamesById: groupNamesById]
  state[ZGT_PAYLOAD_STATE_KEY] = snapshot
  processUpnpTopologyNotification([sourceDni: 'elected-subscriber',
      eventsByGroupId: buildZgtEventProjection(snapshot)], false)
  runInMillis(1, 'publishZgtPlayerAttributes', [overwrite: true])
}

/** Convert the two-map transport into the existing internal topology event shape. */
Map<String, List<Map>> buildZgtEventProjection(Map<String, Object> payload) {
  Map<String, List<String>> followers = payload?.followersByCoordinator instanceof Map
      ? (Map<String, List<String>>)payload.followersByCoordinator : [:]
  Map<String, String> names = payload?.groupNamesById instanceof Map
      ? (Map<String, String>)payload.groupNamesById : [:]
  Map<String, List<Map>> projection = [:]
  followers.each { String coordinator, List<String> ids ->
    String groupId = names.keySet().find { String candidate -> candidate.startsWith("${coordinator}:") }
    if(!groupId) { return }
    List<String> memberIds = [coordinator]
    memberIds.addAll(ids ?: [])
    memberIds = memberIds.unique()
    List<String> memberNames = memberIds.collect { String id -> getPlayerNameForRincon(id) }
    String coordinatorName = getPlayerNameForRincon(coordinator)
    List<Map> events = [
      [name: 'groupCoordinatorId', value: coordinator],
      [name: 'groupCoordinatorName', value: coordinatorName],
      [name: 'groupId', value: groupId],
      [name: 'groupMemberCount', value: memberIds.size()],
      [name: 'groupMemberNames', value: memberNames.toString()],
      [name: 'groupPlayerIds', value: memberIds.join(',')],
      [name: 'isGroupCoordinator', value: 'on'],
      [name: 'isGrouped', value: memberIds.size() > 1 ? 'on' : 'off'],
      [name: 'groupName', value: names[groupId]]
    ]
    projection[groupId] = events
  }
  return projection
}

String getPlayerNameForRincon(String rincon) {
  ChildDeviceWrapper player = findPlayerBySonosId(rincon)
  return player?.getDataValue('name')?.toString() ?: player?.label?.toString() ?: rincon ?: ''
}

/** Publish all topology attributes to every player child, including singleton/reset states. */
void publishZgtPlayerAttributes() {
  Map payload = state[ZGT_PAYLOAD_STATE_KEY] instanceof Map ? (Map)state[ZGT_PAYLOAD_STATE_KEY] : [:]
  Map<String, List<String>> followers = payload.followersByCoordinator instanceof Map
      ? (Map<String, List<String>>)payload.followersByCoordinator : [:]
  Map<String, String> groupNames = payload.groupNamesById instanceof Map
      ? (Map<String, String>)payload.groupNamesById : [:]
  Map<String, String> coordinatorByPlayer = [:]
  Map<String, String> groupIdByCoordinator = [:]
  followers.each { String coordinator, List<String> ids ->
    String groupId = groupNames.keySet().find { String candidate -> candidate.startsWith("${coordinator}:") }
    if(!groupId) { return }
    groupIdByCoordinator[coordinator] = groupId
    coordinatorByPlayer[coordinator] = coordinator
    (ids ?: []).each { String rincon -> coordinatorByPlayer[rincon] = coordinator }
  }
  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    String rincon = player?.getDataValue('id')?.toString() ?: player?.getDeviceNetworkId()?.toString()
    if(!rincon) { return }
    String coordinator = coordinatorByPlayer[rincon] ?: rincon
    String groupId = groupIdByCoordinator[coordinator] ?: ''
    List<String> members = groupId ? [coordinator] + (followers[coordinator] ?: []) : [rincon]
    members = members.findAll { String id -> id }.unique()
    String coordinatorName = getPlayerNameForRincon(coordinator)
    List<String> memberNames = members.collect { String id -> getPlayerNameForRincon(id) }
    Map<String, Object> attributes = [
      groupCoordinatorId: coordinator,
      groupCoordinatorName: coordinatorName,
      groupId: groupId,
      groupMemberCount: members.size(),
      groupMemberNames: memberNames.toString(),
      groupPlayerIds: members.join(','),
      isGroupCoordinator: rincon == coordinator ? 'on' : 'off',
      isGrouped: members.size() > 1 ? 'on' : 'off'
    ]
    sendChildAttributeEvents(player, attributes)
  }
  publishZgtGroupAttributes(followers)
}

/** Publish live membership and exact configuration status to every group child. */
void publishZgtGroupAttributes(Map<String, List<String>> followersByCoordinator) {
  Map<String, String> coordinatorByPlayer = [:]
  (followersByCoordinator ?: [:]).each { String coordinator, List<String> followers ->
    coordinatorByPlayer[coordinator] = coordinator
    (followers ?: []).each { String follower -> coordinatorByPlayer[follower] = coordinator }
  }
  Map<String, ChildDeviceWrapper> playersById = [:]
  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    String rincon = player?.getDataValue('id')?.toString()
    if(rincon) { playersById[rincon] = player }
  }
  List<ChildDeviceWrapper> groupDevices = getCurrentGroupDevices().findAll { ChildDeviceWrapper child ->
    String dni = child?.getDeviceNetworkId()?.toString() ?: ''
    dni.contains(GROUP_DEVICE_DNI_MARKER) || child?.getDataValue('groupCoordinatorId') != null
  }
  groupDevices.each { ChildDeviceWrapper groupDevice ->
    String configuredCoordinator = groupDevice?.getDataValue('groupCoordinatorId')?.toString()
    String activeCoordinator = configuredCoordinator ? coordinatorByPlayer[configuredCoordinator] : null
    List<String> activeMembers = activeCoordinator
        ? [activeCoordinator] + (followersByCoordinator[activeCoordinator] ?: []) : []
    activeMembers = activeMembers.findAll { String id -> id }.unique()
    List<String> configuredMembers = configuredCoordinator
        ? normalizeGroupPlayerIds(getAllPlayersForGroupDevice(groupDevice)) : []
    if(configuredMembers.isEmpty() && configuredCoordinator) { configuredMembers = [configuredCoordinator] }
    Set<String> configuredSet = new LinkedHashSet<String>(configuredMembers)
    Set<String> activeSet = new LinkedHashSet<String>(activeMembers)
    Boolean exactMatch = configuredCoordinator != null && activeCoordinator == configuredCoordinator &&
        configuredSet == activeSet
    List<String> memberNames = activeMembers.collect { String id ->
      ChildDeviceWrapper player = playersById[id]
      player?.getDataValue('name')?.toString() ?: player?.label?.toString() ?: id
    }
    String joinedPlayers = memberNames.join(', ')
    sendChildAttributeEvents(groupDevice, [
      currentlyJoinedPlayers: joinedPlayers,
      switch: exactMatch ? 'on' : 'off'
    ])
  }
}

/** Pure set+return only: no child calls, so the flush→setter call cannot deadlock the single-threaded gate. */
Map<String, List<Map>> putZgtTopology(String sourceDni, Map<String, List<Map>> eventsByGroupId) {
  Map<String, List<Map>> stored = [:]
  if(eventsByGroupId instanceof Map) {
    eventsByGroupId.each { Object key, Object value ->
      String groupId = key?.toString()
      List<Map> events = value instanceof List ? (List<Map>)value : []
      if(groupId && !events.isEmpty()) {
        stored[groupId] = events.collect { Map event -> new LinkedHashMap(event) }
      }
    }
  }
  state[ZGT_EVENTS_STATE_KEY] = stored
  state[ZGT_LAST_REPORT_STATE_KEY] = [sourcePlayerDni: sourceDni, observedAt: now()]
  return stored
}

void playerArrived(String rincon, String dni, String name) {
  logInfo("Sonos player arrived: ${name} (${rincon}, ${dni})")
}

void playerDeparted(String rincon, String dni, String name) {
  logInfo("Sonos player departed: ${name} (${rincon}, ${dni})")
}

/** Queue complete ZGT notification batches; never coalesce their event lists. AVT uses putAvtEvents. */
synchronized void putPlayerUpnpEvents(Map payload) {
  String service = payload?.service?.toString()
  if(service != 'ZGT') { return }
  Map copy = new LinkedHashMap(payload)
  if(payload.events instanceof List) { copy.events = ((List<Map>)payload.events).collect { Map e -> new LinkedHashMap(e) } }
  if(payload.eventsByGroupId instanceof Map) {
    Map<String, List<Map>> groups = [:]
    ((Map)payload.eventsByGroupId).each { Object id, Object rawEvents ->
      if(rawEvents instanceof List) { groups[id.toString()] = ((List<Map>)rawEvents).collect { Map e -> new LinkedHashMap(e) } }
    }
    copy.eventsByGroupId = groups
  }
  List<Map> pending = state[PENDING_UPNP_NOTIFICATION_QUEUE_KEY] instanceof List
      ? (List<Map>)state[PENDING_UPNP_NOTIFICATION_QUEUE_KEY] : []
  pending.add(copy)
  state[PENDING_UPNP_NOTIFICATION_QUEUE_KEY] = pending
  runInMillis(UPNP_EVENT_DEBOUNCE_MILLIS, 'drainUpnpNotificationQueue', [overwrite: true])
}

/** Apply each notification in arrival order after the 500 ms parent debounce. */
synchronized void drainUpnpNotificationQueue() {
  List<Map> pending = state[PENDING_UPNP_NOTIFICATION_QUEUE_KEY] instanceof List
      ? new ArrayList<Map>((List<Map>)state[PENDING_UPNP_NOTIFICATION_QUEUE_KEY]) : []
  state.remove(PENDING_UPNP_NOTIFICATION_QUEUE_KEY)
  pending.each { Map notification -> processUpnpNotification(notification) }
}

void processUpnpNotification(Map notification) {
  String service = notification?.service?.toString()
  if(service == 'ZGT') { processUpnpTopologyNotification(notification) }
}

void processUpnpTopologyNotification(Map notification, Boolean publishTopologyAttributes = true) {
  String sourceDni = notification?.sourceDni?.toString()
  Map<String, List<Map>> next = notification?.eventsByGroupId instanceof Map
      ? (Map<String, List<Map>>)notification.eventsByGroupId : [:]
  if(!sourceDni) { return }
  Map<String, List<Map>> previous = getStoredZgtEventsMap()
  putZgtTopology(sourceDni, next)
  // Group membership changes move every derived group volume and mute value.
  if(buildLiveGroupRosters(previous) != buildLiveGroupRosters(getStoredZgtEventsMap())) {
    queueVolumeRecalculation([], [], true)
  }
  if(!publishTopologyAttributes) { return }
  next.each { String groupId, List<Map> events ->
    String coordinatorId = events.find { Map e -> e?.name?.toString() == 'groupCoordinatorId' }?.value?.toString()
    List<String> members = normalizeGroupPlayerIds(events.find { Map e -> e?.name?.toString() == 'groupPlayerIds' }?.value?.toString())
    if(coordinatorId && !members.contains(coordinatorId)) { members.add(0, coordinatorId) }
    members.unique().each { String id ->
      List<Map> playerTopologyEvents = new ArrayList<Map>(events.collect { Map event -> new LinkedHashMap(event) })
      playerTopologyEvents << [name: 'groupId', value: groupId]
      playerTopologyEvents << [name: 'isGroupCoordinator', value: id == coordinatorId ? 'on' : 'off']
      deliverUpnpEventsToPlayer(id, playerTopologyEvents)
    }
  }
}

// =============================================================================
// AVT delivery
// Players parse AVT and hand the sendEvent-ready maps to putAvtEvents. Nothing in
// the receiving methods touches a child device; the callback applies with the
// app-level sendEvent(dni, map), which never takes a child device handle.
// =============================================================================

/** Merge a coordinator's parsed AVT events into the per-DNI cache, schedule delivery, and return. */
void putAvtEvents(String dni, List<Map> events) {
  if(!dni || !(events instanceof List) || events.isEmpty()) { return }
  Map<String, List<Map>> store = state[AVT_EVENTS_BY_DNI_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, List<Map>>((Map<String, List<Map>>)state[AVT_EVENTS_BY_DNI_STATE_KEY]) : [:]
  // AVT LastChange payloads are deltas, so the cache keeps the latest value per event name.
  Map<String, Map> latestByName = new LinkedHashMap<String, Map>()
  (store[dni] ?: []).each { Map event -> latestByName[event.name.toString()] = event }
  List<Map> incoming = []
  events.each { Map event ->
    if(event?.name && event.containsKey('value')) {
      Map copy = new LinkedHashMap(event)
      latestByName[event.name.toString()] = copy
      incoming << copy
    }
  }
  store[dni] = new ArrayList<Map>(latestByName.values())
  state[AVT_EVENTS_BY_DNI_STATE_KEY] = store
  runInMillis(1, 'applyAvtCallback', [overwrite: false, data: [dni: dni, events: incoming]])
}

List<Map> getStoredAvtEvents(String dni) {
  Map<String, List<Map>> store = state[AVT_EVENTS_BY_DNI_STATE_KEY] instanceof Map
      ? (Map<String, List<Map>>)state[AVT_EVENTS_BY_DNI_STATE_KEY] : [:]
  List<Map> stored = store[dni?.toString()] as List<Map>
  return stored != null ? new ArrayList<Map>(stored) : []
}

/** A follower just started following this coordinator RINCON; replay the coordinator's cached AVT to it. */
void requestCoordinatorEvents(String coordinatorRincon, String followerDni) {
  String coordinatorDni = getPlayerDniByRincon(coordinatorRincon)
  if(!coordinatorDni || !followerDni) {
    logDebug("Ignoring coordinator AVT request from ${followerDni ?: 'unknown'}: coordinator ${coordinatorRincon ?: 'unknown'} is not a known player")
    return
  }
  if(getStoredAvtEvents(coordinatorDni).isEmpty()) {
    logDebug("No cached AVT for coordinator ${coordinatorRincon}; ${followerDni} will receive it with the coordinator's next AVT")
    return
  }
  runInMillis(1, 'applyAvtCallback', [overwrite: false, data: [dni: coordinatorDni, targetDnis: [followerDni]]])
}

void applyAvtCallback(Map data) {
  String dni = data?.dni?.toString()
  if(!dni) { return }
  List<Map> events = data.events instanceof List ? (List<Map>)data.events : getStoredAvtEvents(dni)
  List<String> targets = data.targetDnis instanceof Collection
      ? ((Collection)data.targetDnis).collect { Object id -> id.toString() } : null
  applyAvtEvents(dni, events, targets)
}

/**
 * Apply AVT events from a coordinator to its player targets (default: the coordinator and every follower
 * in its group, plus group devices). An explicit target list limits delivery to those player DNIs.
 */
void applyAvtEvents(String coordinatorDni, List<Map> events, List<String> targetDnis = null) {
  if(!coordinatorDni || !events) { return }
  List<String> playerTargets = targetDnis ?: getGroupMemberDnis(coordinatorDni)
  playerTargets.each { String dni -> sendAvtEventsToDni(dni, events, false) }
  if(targetDnis == null) {
    getGroupDeviceDnisForCoordinator(getPlayerRinconByDni(coordinatorDni)).each { String dni ->
      sendAvtEventsToDni(dni, events, true)
    }
  }
}

void sendAvtEventsToDni(String dni, List<Map> events, Boolean groupDevice) {
  formatUpnpEventsForDni(events, null, dni, groupDevice).each { Map event ->
    String name = event.name.toString()
    if(!groupDevice && isPlayerAttributeDisabledByDni(dni, name)) { return }
    sendChildEventByDni(dni, name, event.value)
  }
}

/** Sole child-event emission point for AVT delivery: the app's own sendEvent addressed by child DNI. */
void sendChildEventByDni(String dni, String name, Object value) {
  if(!dni || !name || value == null) { return }
  try { sendEvent(dni, [name: name, value: value]) }
  catch(Exception e) { logWarn("Could not send ${name} to ${dni}: ${e.message}") }
}

String getPlayerDniByRincon(String rincon) {
  if(!rincon) { return null }
  return getRosterPlayers().find { Map row -> row.playerId?.toString() == rincon }?.dni?.toString()
}

String getPlayerRinconByDni(String dni) {
  if(!dni) { return null }
  return getRosterPlayers().find { Map row -> row.dni?.toString() == dni }?.playerId?.toString()
}

List<Map> getRosterPlayers() {
  Map<String, Object> roster = getStaticDriverActivePlayerRoster()
  return roster?.players instanceof List ? (List<Map>)roster.players : []
}

List<String> getGroupMemberDnis(String coordinatorDni) {
  String coordinatorId = getPlayerRinconByDni(coordinatorDni)
  if(!coordinatorId) { return [] }
  List<String> memberIds = [coordinatorId]
  getStoredZgtEventsMap().each { String groupId, List<Map> groupEvents ->
    String leader = groupEvents.find { Map e -> e?.name?.toString() == 'groupCoordinatorId' }?.value?.toString()
    if(leader == coordinatorId) {
      memberIds.addAll(normalizeGroupPlayerIds(
          groupEvents.find { Map e -> e?.name?.toString() == 'groupPlayerIds' }?.value?.toString()))
    }
  }
  return memberIds.unique().collect { String id -> getPlayerDniByRincon(id) }.findAll { String dni -> dni }
}

List<String> getGroupDeviceDnisForCoordinator(String coordinatorId) {
  Map<String, String> registry = state[GROUP_DEVICE_COORDINATORS_STATE_KEY] instanceof Map
      ? (Map<String, String>)state[GROUP_DEVICE_COORDINATORS_STATE_KEY] : [:]
  return registry.findAll { String dni, String id -> id == coordinatorId }.keySet().toList()
}

void deliverUpnpEventsToPlayer(String rincon, List<Map> events, String artworkSourceId = null) {
  ChildDeviceWrapper player = findPlayerBySonosId(rincon)
  if(player == null || !events) { return }
  List<Map> targetEvents = formatUpnpEventsForChild(events, artworkSourceId ?: rincon, player)
  targetEvents.each { Map event ->
    String name = event?.name?.toString()
    if(name && event.containsKey('value') && !isPlayerAttributeDisabled(player, name)) {
      sendParentChildEvent(player, name, event.value)
    }
  }
}

// =============================================================================
// Calculated group volume and mute
// =============================================================================
// Group volume is the average of the selected players' own volume, matching the Sonos group
// volume definition. Players send their latest volume and mute to the app; the app combines
// them with ZGT topology and each group device's cached settings, so a volume event never
// reads a child device or its settings.

/** Map each live coordinator RINCON to its member RINCONs from ZGT group events. */
Map<String, List<String>> buildLiveGroupRosters(Map<String, List<Map>> topology) {
  Map<String, List<String>> rosters = [:]
  (topology ?: [:]).each { String groupId, List<Map> events ->
    String coordinatorId = events.find { Map e -> e?.name?.toString() == 'groupCoordinatorId' }?.value?.toString()
    if(!coordinatorId) { return }
    List<String> members = normalizeGroupPlayerIds(
        events.find { Map e -> e?.name?.toString() == 'groupPlayerIds' }?.value?.toString())
    if(!members.contains(coordinatorId)) { members.add(0, coordinatorId) }
    rosters[coordinatorId] = members.unique()
  }
  return rosters
}

/** The player's live Sonos group members; an unknown or ungrouped player is its own group. */
List<String> liveGroupMembers(String playerId, Map<String, String> coordinators, Map<String, List<String>> rosters) {
  String coordinatorId = coordinators[playerId]
  List<String> members = coordinatorId ? rosters[coordinatorId] : null
  return members ? new ArrayList<String>(members) : [playerId]
}

Map<String, Map> getGroupPreferencesMap() {
  return state[GROUP_PREFERENCES_BY_DNI_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)state[GROUP_PREFERENCES_BY_DNI_STATE_KEY]) : [:]
}

/** Keep the volume settings in a refresh request cached for devices that have not published them yet. */
void mergeGroupVolumePreferences(String groupDni, Map raw) {
  if(!groupDni || !(raw instanceof Map) ||
      (raw.useProportionalVolume == null && raw.controlUngroupedIndividually == null)) { return }
  Map<String, Map> all = getGroupPreferencesMap()
  Map<String, Object> merged = new LinkedHashMap<String, Object>(all[groupDni] instanceof Map ? (Map<String, Object>)all[groupDni] : [:])
  Map<String, Object> normalized = normalizeGroupPreferenceSnapshot(merged + raw.subMap(['useProportionalVolume', 'controlUngroupedIndividually']))
  if(all[groupDni] == normalized) { return }
  all[groupDni] = normalized
  state[GROUP_PREFERENCES_BY_DNI_STATE_KEY] = all
}

Map<String, Map> getPlayerReadings() {
  return state[PLAYER_READINGS_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)state[PLAYER_READINGS_STATE_KEY]) : [:]
}

@CompileStatic
Integer parseReadingVolume(Object raw) {
  if(raw == null) { return null }
  try {
    Integer volume = new BigDecimal(raw.toString()).setScale(0, RoundingMode.HALF_UP).intValue()
    return volume >= 0 && volume <= 100 ? volume : null
  } catch(Exception ignored) { return null }
}

@CompileStatic
String parseReadingMute(Object raw) {
  String mute = raw?.toString()
  return mute in ['muted', 'unmuted'] ? mute : null
}

/** Store the player's latest volume/mute and schedule recalculation when either changed. */
void recordPlayerReading(String playerId, Map args) {
  Integer volume = parseReadingVolume(args?.volume)
  String mute = parseReadingMute(args?.mute)
  if(!playerId || (volume == null && mute == null)) { return }
  Map<String, Map> readings = getPlayerReadings()
  Map prior = readings[playerId] instanceof Map ? (Map)readings[playerId] : [:]
  Map next = new LinkedHashMap(prior)
  if(volume != null) { next.volume = volume }
  if(mute != null) { next.mute = mute }
  if(next == prior) { return }
  readings[playerId] = next
  state[PLAYER_READINGS_STATE_KEY] = readings
  queueVolumeRecalculation([playerId], [], false)
}

/** Fill readings for players that have not reported yet; never overwrites a reported value. */
void seedPlayerReadings(List<ChildDeviceWrapper> players) {
  Map<String, Map> readings = getPlayerReadings()
  Boolean changed = false
  (players ?: []).each { ChildDeviceWrapper player ->
    String playerId = player?.getDataValue('id')?.toString()
    if(!playerId || readings[playerId] != null) { return }
    Map reading = [:]
    Integer volume = parseReadingVolume(player.currentValue('volume', true))
    String mute = parseReadingMute(player.currentValue('mute', true))
    if(volume != null) { reading.volume = volume }
    if(mute != null) { reading.mute = mute }
    if(!reading.isEmpty()) { readings[playerId] = reading; changed = true }
  }
  if(changed) { state[PLAYER_READINGS_STATE_KEY] = readings }
}

/** Calculate average volume and aggregate mute for the given players from their latest readings. */
@CompileStatic
Map<String, Object> calculateVolumeState(Collection<String> playerIds, Map<String, Map> readings) {
  Integer total = 0
  Integer volumeCount = 0
  Integer muteReports = 0
  Boolean allMuted = !playerIds.isEmpty()
  for(String playerId : playerIds) {
    Map reading = readings[playerId] instanceof Map ? (Map)readings[playerId] : [:]
    Integer volume = parseReadingVolume(reading.get('volume'))
    if(volume != null) { total += volume; volumeCount++ }
    String mute = parseReadingMute(reading.get('mute'))
    if(mute != null) { muteReports++ }
    if(mute != 'muted') { allMuted = false }
  }
  Map<String, Object> result = [:]
  if(volumeCount > 0) {
    result.put('volume', new BigDecimal(total).divide(new BigDecimal(volumeCount), 0, RoundingMode.HALF_UP).intValue())
  }
  if(muteReports > 0) { result.put('mute', allMuted ? 'muted' : 'unmuted') }
  return result
}

/** True when any selected player has no Hubitat device, so its volume and mute are never reported. */
@CompileStatic
Boolean hasUnmanagedMember(Collection<String> playerIds, Set<String> managedIds) {
  for(String playerId : playerIds) { if(!managedIds.contains(playerId)) { return true } }
  return false
}

/** Calculate once per distinct player selection within a recalculation and reuse the result. */
Map<String, Object> cachedVolumeState(Collection<String> playerIds, Map context) {
  Map<String, Map<String, Object>> cache = (Map<String, Map<String, Object>>)context.cache
  String key = playerIds.join('|')
  Map<String, Object> hit = cache[key]
  if(hit == null) {
    hit = calculateVolumeState(playerIds, (Map<String, Map>)context.readings)
    cache[key] = hit
  }
  return hit
}

/**
 * Choose the players a group device reports on from its live Sonos state and cached settings:
 * grouped + proportional follows the live group, grouped + not proportional uses the configured
 * players, ungrouped uses the configured players only when controlled individually, otherwise the
 * coordinator alone.
 */
List<String> selectGroupDevicePlayerIds(List<String> configuredPlayerIds, Map preferences,
    Map<String, String> coordinators, Map<String, List<String>> rosters) {
  Map<String, Object> settings = normalizeGroupPreferenceSnapshot(preferences)
  String coordinatorId = configuredPlayerIds.find { String id -> coordinators[id] == id } ?: configuredPlayerIds[0]
  List<String> liveMembers = rosters[coordinatorId] ?: [coordinatorId]
  if(liveMembers.size() > 1) {
    return settings.useProportionalVolume ? new ArrayList<String>(liveMembers) : configuredPlayerIds
  }
  return settings.controlUngroupedIndividually ? configuredPlayerIds : [coordinatorId]
}

Map buildVolumeCalculationContext() {
  Map<String, List<Map>> topology = getStoredZgtEventsMap()
  return [coordinators: buildPlayerCoordinatorMap(topology), rosters: buildLiveGroupRosters(topology),
    readings: getPlayerReadings(), preferences: getGroupPreferencesMap(), cache: [:],
    managed: getCurrentPlayerDevices().collect { ChildDeviceWrapper p -> p.getDataValue('id')?.toString() }
        .findAll { it }.toSet()]
}

/** Volume, groupVolume, mute and groupMute attributes for one group device, omitting unknown values. */
Map<String, Object> calculateGroupDeviceVolumeState(ChildDeviceWrapper groupDevice, Map context) {
  List<String> configured = getAllPlayersForGroupDevice(groupDevice).unique()
  if(configured.isEmpty()) { return [:] }
  Map preferences = ((Map)context.preferences)[groupDevice.getDeviceNetworkId()] as Map
  List<String> selected = selectGroupDevicePlayerIds(configured, preferences,
      (Map<String, String>)context.coordinators, (Map<String, List<String>>)context.rosters)
  Map<String, Object> calculated = cachedVolumeState(selected, context)
  Map<String, Object> attributes = [:]
  if(calculated.volume != null) { attributes.volume = calculated.volume; attributes.groupVolume = calculated.volume }
  if(calculated.mute != null) { attributes.mute = calculated.mute; attributes.groupMute = calculated.mute }
  return attributes
}

/** Merge a recalculation request; one debounced drain applies everything queued before it runs. */
void queueVolumeRecalculation(Collection<String> playerIds, Collection<String> groupDnis, Boolean all) {
  Map pending = state[PENDING_VOLUME_RECALCULATION_STATE_KEY] instanceof Map
      ? (Map)state[PENDING_VOLUME_RECALCULATION_STATE_KEY] : [:]
  Set<String> players = new LinkedHashSet<String>((pending.players ?: []) as Collection<String>)
  Set<String> groups = new LinkedHashSet<String>((pending.groups ?: []) as Collection<String>)
  players.addAll(playerIds ?: [])
  groups.addAll(groupDnis ?: [])
  state[PENDING_VOLUME_RECALCULATION_STATE_KEY] = [players: players.toList(), groups: groups.toList(),
    all: all == true || pending.all == true]
  runInMillis(UPNP_EVENT_DEBOUNCE_MILLIS, 'drainVolumeRecalculation', [overwrite: true])
}

void drainVolumeRecalculation() {
  Map pending = state[PENDING_VOLUME_RECALCULATION_STATE_KEY] instanceof Map
      ? (Map)state[PENDING_VOLUME_RECALCULATION_STATE_KEY] : [:]
  state.remove(PENDING_VOLUME_RECALCULATION_STATE_KEY)
  if(pending.isEmpty()) { return }
  recalculateVolumeState((pending.players ?: []) as List<String>, (pending.groups ?: []) as List<String>, pending.all == true)
}

/**
 * Recalculate player-level group state from each player's live Sonos group, and group-device state
 * from its selected players. Only players and group devices touched by the change are published.
 */
void recalculateVolumeState(Collection<String> changedPlayerIds, Collection<String> changedGroupDnis, Boolean all) {
  Map context = buildVolumeCalculationContext()
  Map<String, String> coordinators = (Map<String, String>)context.coordinators
  Map<String, List<String>> rosters = (Map<String, List<String>>)context.rosters
  Set<String> managed = (Set<String>)context.managed
  Set<String> affected = new LinkedHashSet<String>()
  (changedPlayerIds ?: []).each { String id -> affected.addAll(liveGroupMembers(id, coordinators, rosters)) }
  // Groups with a speaker that has no Hubitat device cannot be averaged from readings; ask the coordinator.
  Set<String> directReadCoordinators = new LinkedHashSet<String>()
  getCurrentPlayerDevices().each { ChildDeviceWrapper player ->
    String id = player.getDataValue('id')?.toString()
    if(!id || !(all || affected.contains(id))) { return }
    List<String> members = liveGroupMembers(id, coordinators, rosters)
    if(hasUnmanagedMember(members, managed)) { directReadCoordinators.add(coordinators[id] ?: id); return }
    Map<String, Object> calculated = cachedVolumeState(members, context)
    if(calculated.volume != null) { sendParentChildEvent(player, 'groupVolume', calculated.volume) }
    if(calculated.mute != null) { sendParentChildEvent(player, 'groupMute', calculated.mute) }
  }
  getCurrentGroupDevices().each { ChildDeviceWrapper groupDevice ->
    String groupDni = groupDevice.getDeviceNetworkId()
    List<String> configured = getAllPlayersForGroupDevice(groupDevice).unique()
    Map preferences = ((Map)context.preferences)[groupDni] as Map
    List<String> selected = configured.isEmpty() ? [] : selectGroupDevicePlayerIds(configured, preferences, coordinators, rosters)
    Boolean touched = all || (changedGroupDnis ?: []).contains(groupDni) || configured.any { String id -> affected.contains(id) } ||
        selected.any { String id -> affected.contains(id) }
    if(!touched) { return }
    if(!selected.isEmpty() && hasUnmanagedMember(selected, managed)) {
      String coordinatorId = configured.find { String id -> coordinators[id] == id } ?: configured[0]
      directReadCoordinators.add(coordinatorId)
      return
    }
    Map<String, Object> attributes = calculateGroupDeviceVolumeState(groupDevice, context)
    if(!attributes.isEmpty()) { queueGroupDeviceState(groupDni, attributes) }
  }
  directReadCoordinators.each { String coordinatorId -> findPlayerBySonosId(coordinatorId)?.readGroupRendering() }
}

/**
 * Apply a group volume or mute read directly from the Sonos coordinator to every player in its live
 * group and to the group devices that follow that live group.
 */
void applyDirectGroupRendering(String coordinatorId, Map args) {
  Map<String, Object> attributes = [:]
  Integer volume = parseReadingVolume(args?.groupVolume)
  String mute = parseReadingMute(args?.groupMute)
  if(volume != null) { attributes.volume = volume; attributes.groupVolume = volume }
  if(mute != null) { attributes.mute = mute; attributes.groupMute = mute }
  if(!coordinatorId || attributes.isEmpty()) { return }
  Map context = buildVolumeCalculationContext()
  Map<String, String> coordinators = (Map<String, String>)context.coordinators
  Map<String, List<String>> rosters = (Map<String, List<String>>)context.rosters
  List<String> members = liveGroupMembers(coordinatorId, coordinators, rosters)
  members.each { String id ->
    ChildDeviceWrapper player = findPlayerBySonosId(id)
    if(player == null) { return }
    if(volume != null) { sendParentChildEvent(player, 'groupVolume', volume) }
    if(mute != null) { sendParentChildEvent(player, 'groupMute', mute) }
  }
  getGroupDevicesForCoordinator(coordinatorId).each { ChildDeviceWrapper groupDevice ->
    List<String> configured = getAllPlayersForGroupDevice(groupDevice).unique()
    Map preferences = ((Map)context.preferences)[groupDevice.getDeviceNetworkId()] as Map
    if(!configured.isEmpty() && selectGroupDevicePlayerIds(configured, preferences, coordinators, rosters) as Set == members as Set) {
      queueGroupDeviceState(groupDevice.getDeviceNetworkId(), attributes)
    }
  }
}

/** Ask each group device whose cached settings predate the volume settings to publish them; runs after app Save. */
void seedGroupPreferenceSnapshots() {
  Map<String, Map> cached = getGroupPreferencesMap()
  getCurrentGroupDevices().each { ChildDeviceWrapper groupDevice ->
    if(cached[groupDevice.getDeviceNetworkId()]?.containsKey('useProportionalVolume')) { return }
    try { groupDevice.scheduleGroupPreferenceSnapshot() }
    catch(Exception e) { logDebug("Could not request group preferences from ${groupDevice.displayName}: ${e.message}") }
  }
}

Boolean isPlayerAttributeDisabled(ChildDeviceWrapper player, String attributeName) {
  return isPlayerAttributeDisabledByDni(player?.getDeviceNetworkId()?.toString(), attributeName)
}

Boolean isPlayerAttributeDisabledByDni(String dni, String attributeName) {
  if(!dni || !attributeName) { return false }
  Map<String, Object> snapshot = getPreferenceSnapshotByDni(PLAYER_PREFERENCES_BY_DNI_STATE_KEY, dni)
  if(attributeName == 'trackData') {
    return toBooleanPreference(snapshot.containsKey('disableTrackDataEvents') ? snapshot.disableTrackDataEvents : true, true)
  }
  if(attributeName in ['currentArtistName', 'currentAlbumName', 'currentTrackName', 'currentTrackNumber',
      'nextArtistName', 'nextAlbumName', 'nextTrackName']) {
    return toBooleanPreference(snapshot.disableArtistAlbumTrackEvents, false)
  }
  return false
}

Map<String, Object> getPreferenceSnapshotByDni(String stateKey, String dni) {
  Map snapshots = state[stateKey] instanceof Map ? (Map)state[stateKey] : [:]
  return snapshots[dni] instanceof Map ? (Map<String, Object>)snapshots[dni] : [:]
}

/** Apply the artwork display preference and Hubitat image markup at the parent fanout boundary. */
List<Map> formatUpnpEventsForChild(List<Map> events, String artworkSourceId, ChildDeviceWrapper target) {
  if(target == null) { return formatUpnpEventsForDni(events, artworkSourceId, null, false) }
  return formatUpnpEventsForDni(events, artworkSourceId, target.getDeviceNetworkId()?.toString(),
      target.getDataValue('id') == null)
}

/** Preferences come from the app-held DNI snapshots, so this never needs a child device handle. */
List<Map> formatUpnpEventsForDni(List<Map> events, String artworkSourceId, String targetDni, Boolean groupDevice) {
  List<Map> formatted = []
  Boolean disableArtEvents = isAlbumArtEventsDisabledByDni(targetDni, groupDevice)
  (events ?: []).each { Map event ->
    String name = event?.name?.toString()
    if(!name || !event.containsKey('value')) { return }
    if(name == 'albumArtURI') {
      Map<String, Object> art = albumArtAttributesForFanout(event.value?.toString(), artworkSourceId, disableArtEvents)
      art.each { String attributeName, Object value -> formatted << [name: attributeName, value: value] }
    } else if(name == 'nextTrackAlbumArtURI') {
      String uri = event.value?.toString()
      String resolved = resolveAlbumArtUrl(uri, artworkSourceId)
      formatted << [name: name, value: formatAlbumArtForFanout(resolved)]
    } else {
      formatted << [name: name, value: event.value]
    }
  }
  return formatted
}

Boolean isAlbumArtEventsDisabled(ChildDeviceWrapper target) {
  if(target == null) { return true }
  return isAlbumArtEventsDisabledByDni(target.getDeviceNetworkId()?.toString(), target.getDataValue('id') == null)
}

Boolean isAlbumArtEventsDisabledByDni(String dni, Boolean groupDevice) {
  if(!dni) { return true }
  Map<String, Object> snapshot = getPreferenceSnapshotByDni(
      groupDevice ? GROUP_PREFERENCES_BY_DNI_STATE_KEY : PLAYER_PREFERENCES_BY_DNI_STATE_KEY, dni)
  return toBooleanPreference(snapshot.disableAlbumArtEvents, true)
}

Map<String, Object> albumArtAttributesForFanout(String rawAlbumArtURI, String artworkSourceId,
    Boolean disableArtEvents) {
  String resolvedUri = resolveAlbumArtUrl(rawAlbumArtURI, artworkSourceId)
  Map<String, Object> attributes = [albumArtURI: formatAlbumArtForFanout(resolvedUri)]
  if(disableArtEvents == true) {
    attributes.albumArtSmall = 'Disabled in Preferences'
    attributes.albumArtMedium = 'Disabled in Preferences'
    attributes.albumArtLarge = 'Disabled in Preferences'
    return attributes
  }
  if(!resolvedUri || resolvedUri == 'Not Available') {
    attributes.albumArtSmall = 'Not Available'
    attributes.albumArtMedium = 'Not Available'
    attributes.albumArtLarge = 'Not Available'
    return attributes
  }
  List<String> urls = parseConcatenatedAlbumArtUrls(resolvedUri)
  if(urls.size() > 1) {
    List<String> filtered = urls.findAll { String url -> !url.contains('/AArt/') }
    if(filtered) { urls = filtered }
    String large = urls.find { String url -> url.contains('/AArl/') } ?: urls[0]
    String medium = urls.find { String url -> url.contains('/AArm/') } ?: large
    String small = urls.find { String url -> url.contains('/AArs/') } ?: medium
    attributes.albumArtSmall = small
    attributes.albumArtMedium = medium
    attributes.albumArtLarge = large
    attributes.albumArtURI = formatAlbumArtForFanout(medium)
    return attributes
  }
  if(resolvedUri.contains('/getaa?') || resolvedUri.contains('/getaa/')) {
    attributes.albumArtSmall = appendAlbumArtSize(resolvedUri, 60)
    attributes.albumArtMedium = appendAlbumArtSize(resolvedUri, 200)
    attributes.albumArtLarge = appendAlbumArtSize(resolvedUri, 600)
  } else {
    attributes.albumArtSmall = resolvedUri
    attributes.albumArtMedium = resolvedUri
    attributes.albumArtLarge = resolvedUri
  }
  return attributes
}

String resolveAlbumArtUrl(String rawAlbumArtURI, String artworkSourceId) {
  if(!rawAlbumArtURI || rawAlbumArtURI == 'Not Available') { return 'Not Available' }
  if(!rawAlbumArtURI.startsWith('/') || !artworkSourceId) { return rawAlbumArtURI }
  ChildDeviceWrapper source = findPlayerBySonosId(artworkSourceId)
  String baseUrl = getSonosLocalUpnpUrl(source)
  if(baseUrl?.endsWith('/')) { baseUrl = baseUrl[0..-2] }
  return baseUrl ? "${baseUrl}${rawAlbumArtURI}" : rawAlbumArtURI
}

String formatAlbumArtForFanout(String albumArtURI) {
  if(!albumArtURI || albumArtURI == 'Not Available') { return 'Not Available' }
  return "<br><img src=\"${escapeNewUiHtml(albumArtURI)}\" width=\"200\" height=\"200\" >"
}

String appendAlbumArtSize(String url, Integer size) {
  String separator = url.contains('?') ? '&' : '?'
  return "${url}${separator}x=${size}&y=${size}"
}

List<String> parseConcatenatedAlbumArtUrls(String value) {
  if(!value || !(value.contains('http://') || value.contains('https://'))) { return value ? [value] : [] }
  List<String> urls = []
  Integer start = 0
  while(start < value.length()) {
    Integer offset = value.substring(start).startsWith('https://') ? 8 : 7
    Integer nextHttp = value.indexOf('http://', start + offset)
    Integer nextHttps = value.indexOf('https://', start + offset)
    Integer next = nextHttp > 0 && nextHttps > 0 ? Math.min(nextHttp, nextHttps) :
        (nextHttp > 0 ? nextHttp : nextHttps)
    if(next > 0) {
      urls.add(value.substring(start, next))
      start = next
    } else {
      urls.add(value.substring(start))
      break
    }
  }
  return urls
}

void sendParentChildEvent(ChildDeviceWrapper child, String name, Object value) {
  if(child == null || !name || value == null) { return }
  try { child.sendEvent(name: name, value: value) }
  catch(Exception e) { logWarn("Could not send ${name} to ${child.getDeviceNetworkId()}: ${e.message}") }
}

Map<String, String> buildPlayerCoordinatorMap(Map<String, List<Map>> topology) {
  Map<String, String> coordinators = [:]
  (topology ?: [:]).each { String groupId, List<Map> events ->
    String coordinatorId = events.find { Map event -> event?.name?.toString() == 'groupCoordinatorId' }?.value?.toString()
    if(!coordinatorId) { return }
    List<String> members = normalizeGroupPlayerIds(
        events.find { Map event -> event?.name?.toString() == 'groupPlayerIds' }?.value?.toString())
    if(!members.contains(coordinatorId)) { members.add(0, coordinatorId) }
    members.unique().each { String memberId -> coordinators[memberId] = coordinatorId }
  }
  return coordinators
}

List<Map> getStoredZgtEvents(String groupId) {
  Map<String, List<Map>> snapshots = state[ZGT_EVENTS_STATE_KEY] instanceof Map
      ? (Map<String, List<Map>>)state[ZGT_EVENTS_STATE_KEY] : [:]
  List<Map> stored = snapshots[groupId?.toString()] as List<Map>
  return stored != null ? new ArrayList<Map>(stored) : []
}

Map<String, List<Map>> getStoredZgtEventsMap() {
  return state[ZGT_EVENTS_STATE_KEY] instanceof Map
      ? new LinkedHashMap<String, List<Map>>((Map<String, List<Map>>)state[ZGT_EVENTS_STATE_KEY]) : [:]
}

Map getCurrentReceivedZgtTopology() {
  // Reads the app's own ZGT events store (the driver keeps no topology copy).
  try {
    Map<String, List<Map>> stored = getStoredZgtEventsMap()
    if(stored.isEmpty()) { return [:] }
    Map report = state[ZGT_LAST_REPORT_STATE_KEY] instanceof Map
        ? (Map)state[ZGT_LAST_REPORT_STATE_KEY] : [:]
    List<Map> converted = stored.collect { String groupId, List<Map> events ->
      String coordinatorId = events.find { Map e -> e?.name?.toString() == 'groupCoordinatorId' }?.value?.toString()
      List<String> memberIds = normalizeGroupPlayerIds(
          events.find { Map e -> e?.name?.toString() == 'groupPlayerIds' }?.value?.toString())
      [ groupId: groupId, coordinatorId: coordinatorId,
        playerIds: memberIds,
        memberIds: memberIds,
        coordinatorName: events.find { Map e -> e?.name?.toString() == 'groupCoordinatorName' }?.value?.toString(),
        groupName: events.find { Map e -> e?.name?.toString() == 'groupName' }?.value?.toString(),
        satellites: [], channelMap: [:] ]
    }
    return [groups: converted, sourcePlayerDni: report.sourcePlayerDni?.toString(),
      observedAt: (report.observedAt ?: now()) as Long]
  } catch(Exception e) {
    logDebug("getCurrentReceivedZgtTopology failed: ${e.message}")
    return [:]
  }
}

void playerGroupTopologyOperationUpdate(Map payload) {
  if(!(payload instanceof Map) || payload.isEmpty()) { return }
  List<Map> pending = state[PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY] instanceof List
      ? (List<Map>)state[PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY] : []
  pending.add(new LinkedHashMap(payload))
  state[PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY] = pending
  if(state[GROUP_TOPOLOGY_EVENT_DRAIN_SCHEDULED_STATE_KEY] != true) {
    state[GROUP_TOPOLOGY_EVENT_DRAIN_SCHEDULED_STATE_KEY] = true
    runInMillis(1, 'drainPlayerGroupTopologyEvents', [overwrite: true])
  }
}

void drainPlayerGroupTopologyEvents() {
  state.remove(GROUP_TOPOLOGY_EVENT_DRAIN_SCHEDULED_STATE_KEY)
  List<Map> pending = state[PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY] instanceof List
      ? new ArrayList<Map>((List<Map>)state[PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY]) : []
  state.remove(PENDING_GROUP_TOPOLOGY_EVENTS_STATE_KEY)
  pending.each { Map payload -> groupTopologyOperationEventHandler(payload) }
}

void groupTopologyOperationEventHandler(Object event) {
  Map payload
  if(event instanceof Map && !((Map)event).containsKey('value') && !((Map)event).containsKey('data')) {
    payload = (Map)event
  } else {
    try { payload = (Map)parseJson(event?.value?.toString()) }
    catch(Exception e) {
      logWarn("Ignoring malformed group topology operation event: ${e.message}")
      return
    }
  }
  Map operation = getActiveGroupOperation()
  if(operation == null || payload.operationId?.toString() != operation.operationId?.toString() ||
      payload.event != 'groups') { return }
  String eventPlayerId = payload.playerId as String
  if(!eventPlayerId || !normalizeGroupPlayerIds(operation.requiredPlayerIds).contains(eventPlayerId)) { return }
  Map eventData = payload.data instanceof Map ? (Map)payload.data : [:]
  Long observedAt = (payload.observedAt ?: now()) as Long
  Map<String, Map> observations = operation.topologyObservations instanceof Map
      ? (Map<String, Map>)operation.topologyObservations
      : [:]
  observations[eventPlayerId] = [
    groupId: eventData.groupId?.toString(),
    coordinatorId: eventData.coordinatorId?.toString(),
    playerIds: normalizeGroupPlayerIds(eventData.playerIds),
    observedAt: observedAt
  ]
  operation.topologyObservations = observations
  if(eventData.containsKey('groupId') && eventData.containsKey('coordinatorId')) {
    operation.observedGroupId = eventData.groupId as String
    operation.observedCoordinatorId = eventData.coordinatorId as String
    operation.observedPlayerIds = normalizeGroupPlayerIds(eventData.playerIds)
  }
  saveActiveGroupOperation(operation)
  runIn(1, 'advanceGroupTopologyOperation', [overwrite: true, data: [operationId: operation.operationId]])
}

void publishGroupOperationStatus(Map operation, String status, String errorCode = null, String detail = null) {
  if(operation == null) { return }
  operation.status = status
  if(errorCode != null) { operation.errorCode = errorCode }
  if(detail != null) { operation.detail = detail }
  saveActiveGroupOperation(operation)
}

void clearRegisteredGroupOperations(Map operation) {
  if(operation == null) { return }
  Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
  normalizeGroupPlayerIds(operation.registeredPlayerIds).each { String playerId ->
    ChildDeviceWrapper player = rinconMap[playerId]
    if(player != null) {
      try { player.clearGroupTopologyOperation(operation.operationId as String) }
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
  if(status == 'SUCCEEDED') {
    storeGroupingContext(operation)
  }
  state.remove(GROUP_OPERATION_STATE_KEY)
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
      isSonosCoordinator(player)
    }
  return activeCoordinator ?: configuredCoordinator
}

ChildDeviceWrapper resolveCurrentGroupCommandCoordinator(Collection<String> playerIds,
    Map<String, ChildDeviceWrapper> rinconMap) {
  return playerIds.collect { String id -> rinconMap[id] }.find { ChildDeviceWrapper player ->
    String playerId = player?.getDataValue('id')?.toString()
    String reportedCoordinatorId = player?.currentValue('groupCoordinatorId', true)?.toString() ?:
        player?.getDataValue('groupCoordinatorId')?.toString()
    playerId && playerId == reportedCoordinatorId
  }
}

ChildDeviceWrapper resolveLiveGroupCoordinator(ChildDeviceWrapper anchor,
    Map<String, ChildDeviceWrapper> rinconMap) {
  if(anchor == null) { return null }
  String liveCoordinatorId = anchor.getDataValue('groupCoordinatorId')?.toString()
  ChildDeviceWrapper liveCoordinator = liveCoordinatorId ? rinconMap[liveCoordinatorId] : null
  return liveCoordinator ?: anchor
}

/** Called from the driver's deferred callback after its command gate is released. */
Boolean playerCommandRequest(Map request) {
  return enqueuePlayerCommandRequest(request, false)
}

Boolean enqueuePlayerCommandRequest(Map request, Boolean acknowledgeSource = true) {
  String playerDni = request?.playerDni as String
  String command = request?.command as String
  ChildDeviceWrapper source = playerDni ? getChildDevice(playerDni) : null
  if(!playerDni || !command || source == null) {
    logWarn("Ignoring player command request with invalid target or command: ${JsonOutput.toJson(request)}")
    return false
  }
  if(isRequestOlderThanDeviceCommandClear(playerDni, request.createdAt)) {
    logDebug("Ignoring stale player command request ${request.requestId ?: 'unknown'} from before initialization for ${playerDni}")
    return false
  }
  String requestId = request.requestId as String
  Map<String, List<String>> received = state[PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY] instanceof Map
      ? (Map<String, List<String>>)state[PLAYER_COMMAND_RECEIVED_IDS_STATE_KEY]
      : [:]
  List<String> receivedForPlayer = received[playerDni] instanceof List
      ? (List<String>)received[playerDni]
      : []
  if(requestId && receivedForPlayer.contains(requestId)) {
    if(acknowledgeSource) { acknowledgePlayerCommandRequest(source, requestId) }
    return true
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
    if(acknowledgeSource) { acknowledgePlayerCommandRequest(source, requestId) }
  }
  runIn(1, 'drainPlayerCommandRequests', [overwrite: true])
  return true
}

void acknowledgePlayerCommandRequest(ChildDeviceWrapper source, String requestId) {
  source.acknowledgeParentCommand(requestId)
  logDebug("Acknowledged receipt of parent command ${requestId} for ${source.getDeviceNetworkId()}")
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
    return NumberUtils.toInt(request.sequence.toString(), Integer.MAX_VALUE)
  }
  String requestId = request?.requestId as String
  if(!requestId) { return Integer.MAX_VALUE }
  String sequenceText = requestId.contains('-')
      ? requestId.substring(requestId.lastIndexOf('-') + 1)
      : requestId
  return NumberUtils.toInt(sequenceText, Integer.MAX_VALUE)
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
    source.playerCreateNewGroup()
    return
  }
  Map activeOperation = getActiveGroupOperation()
  String sourcePlayerId = source.getDataValue('id') as String
  if(activeOperation && activeOperation.phase != 'COMPLETE' &&
      normalizeGroupPlayerIds(activeOperation.requiredPlayerIds).contains(sourcePlayerId) &&
      isPlayerPlaybackCommandThatWaitsForGroupOperation(command)) {
    logDebug("Waiting for topology change to complete before processing player command ${command} from ${playerDni}")
    deferGroupPlayerCommand(request)
    return
  }

  try {
    Map<String, ChildDeviceWrapper> rinconMap = buildRinconMap()
    String configuredCoordinatorId = source.getDataValue('groupCoordinatorId')
    List<String> candidateIds = splitTokens(source.getDataValue('groupPlayerIds')?.toString(), ',')
    if(!candidateIds && source.getDataValue('id')) {
      candidateIds.add(source.getDataValue('id'))
    }
    ChildDeviceWrapper coordinator = candidateIds.collect { String id -> rinconMap[id] }
      .find { ChildDeviceWrapper player ->
        isSonosCoordinator(player)
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
        coordinator.cancelPendingAmazonMusicAutoPlay()
        coordinator.playerPause()
        break
      case 'stop':
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
  if(value == null || StringUtils.isBlank(value.toString())) {
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
      isSonosCoordinator(coordinator)
}

/** True when every configured target is currently a member of the live group. */
Boolean isGroupDeviceRosterActivelyGrouped(ChildDeviceWrapper coordinator, List<String> playerIds) {
  if(!isSonosGrouped(coordinator) || !playerIds) { return false }
  String groupId = getGroupOperationChildValue(coordinator, 'groupId')?.toString()
  List<String> activePlayerIds = normalizeGroupPlayerIds(
      getGroupOperationChildValue(coordinator, 'groupPlayerIds'))
  return groupId != null && !StringUtils.isBlank(groupId) &&
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

  Map attributes = [:]
  // The refresh request carries the current settings; keep the cache correct for devices that
  // have not published a snapshot yet, and seed readings from players that have not reported.
  mergeGroupVolumePreferences(groupDevice.getDeviceNetworkId(), args)
  seedPlayerReadings(players)
  attributes.putAll(calculateGroupDeviceVolumeState(groupDevice, buildVolumeCalculationContext()))

  ['status', 'transportStatus', 'trackData', 'trackDescription', 'currentTrackDuration',
   'trackPosition', 'trackPositionUpdated',
   'currentArtistName', 'albumArtURI', 'albumArtSmall', 'albumArtMedium', 'albumArtLarge',
   'audioSource', 'currentAlbumName', 'currentTrackName', 'currentFavorite', 'currentPlaylist',
   'currentTrackNumber', 'nextArtistName', 'nextAlbumName', 'nextTrackName', 'nextTrackAlbumArtURI',
   'queueTrackTotal', 'queueTrackPosition', 'currentRepeatOneMode', 'currentRepeatAllMode',
   'currentCrossfadeMode', 'currentShuffleMode'].each { String name ->
    if(name in ['trackPosition', 'trackPositionUpdated'] &&
        (!isPlayerTrackPositionUpdatesEnabled(coordinator) || !isGroupTrackPositionUpdatesEnabled(groupDevice))) { return }
    if(isPlayerAttributeDisabled(coordinator, name)) { return }
    Object value = coordinator.currentValue(name, true)
    if(value != null) { attributes[name] = value }
  }

  String coordinatorId = coordinator.getDataValue('id')?.toString()
  List<Map> latestAvt = getStoredAvtEvents(coordinator.getDeviceNetworkId()?.toString())
  Map artEvent = latestAvt.find { Map event -> event?.name?.toString() == 'albumArtURI' && event.containsKey('value') }
  if(artEvent) {
    attributes.putAll(albumArtAttributesForFanout(artEvent.value?.toString(), coordinatorId,
        isAlbumArtEventsDisabled(groupDevice)))
  } else if(isAlbumArtEventsDisabled(groupDevice)) {
    attributes.albumArtSmall = 'Disabled in Preferences'
    attributes.albumArtMedium = 'Disabled in Preferences'
    attributes.albumArtLarge = 'Disabled in Preferences'
  }

  Object memberNames = coordinator.currentValue('groupMemberNames', true)
  if(memberNames != null) {
    attributes.currentlyJoinedPlayers = memberNames.toString().replaceAll(/^\[|\]$/, '').trim()
  }
  if(!attributes.isEmpty()) {
    queueGroupDeviceState(groupDevice.getDeviceNetworkId(), attributes)
  }
}

Boolean isPlayerTrackPositionUpdatesEnabled(ChildDeviceWrapper player) {
  return toBooleanPreference(getPlayerPreference(player, 'enableTrackPositionUpdates', false), false)
}

Boolean isGroupTrackPositionUpdatesEnabled(ChildDeviceWrapper groupDevice) {
  return toBooleanPreference(getGroupPreference(groupDevice, 'enableTrackPositionUpdates', false), false)
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
  return getCurrentGroupDevices().collect { ChildDeviceWrapper group ->
    group.getDeviceNetworkId()?.toString()
  }.findAll { String dni -> dni }
}

List<ChildDeviceWrapper> getCurrentPlayerDevices() {
  List<ChildDeviceWrapper> currentPlayers = []
  app.getChildDevices().each{child ->
    String dni = child.getDeviceNetworkId()?.toString() ?: ''
    if(child.getDataValue('id') && !dni.contains(GROUP_DEVICE_DNI_MARKER) &&
        !dni.contains(ALARM_MANAGER_DNI_MARKER)) {
      currentPlayers.add(child)
    }
  }
  return currentPlayers
}

// @CompileStatic
// void registerAllPlayersInRinconMap(DeviceWrapper cd) {
//   cd.addAllPlayersToRinconMap(getCurrentPlayerDevices())
// }

List<ChildDeviceWrapper> getCurrentGroupDevices() {
  List<ChildDeviceWrapper> currentGroupDevs = []
  app.getChildDevices().each{child ->
    String dni = child.getDeviceNetworkId()?.toString() ?: ''
    if(child.getDataValue('id') == null && !dni.contains(ALARM_MANAGER_DNI_MARKER)) {
      currentGroupDevs.add(child)
    }
  }
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
    playerIds.addAll(splitTokens(playerIdsStr, ','))
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
  if(!groupDni) { return [:] }
  ChildDeviceWrapper group = app.getChildDevice(groupDni)
  if(group == null) { return [:] }
  String coordinatorId = group.getDataValue('groupCoordinatorId')?.toString()
  List<String> playerIds = normalizeGroupPlayerIds(group.getDataValue('playerIds'))
  return coordinatorId && !playerIds.isEmpty()
      ? [groupCoordinatorId: coordinatorId, playerIds: playerIds] : [:]
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
  String playerDni = (splitTokens(respJson?.device?.serialNumber?.replace('-', ''), ':'))[0]
  String swGen = respJson?.device?.swGen
    String householdId = respJson?.householdId
  String playerId = respJson?.playerId
  String groupId = respJson?.groupId
}

void getZoneGroupAttributesAsync(DeviceWrapper device, String callbackMethod = 'getZoneGroupAttributesAsyncCallback', Map data = null) {
  String ip = getSonosLocalUpnpHost(device)
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  asynchttpPost(callbackMethod, params, data)
}

void getZoneGroupAttributesAsyncCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'getZoneGroupAttributesAsyncCallback')) { return }
}

List<DeviceWrapper> getCurrentGroupedDevices(DeviceWrapper device) {
  String ip = getSonosLocalUpnpHost(device)
  List<String> groupedRincons
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupedRincons = splitTokens(xml['Body']['GetZoneGroupAttributesResponse']['CurrentZonePlayerUUIDsInGroup'].text()?.toString(), ',')
    }
    else { logError("getCurrentGroupedDevices: unexpected response: ${resp?.data}") }
  }
  List<DeviceWrapper> groupedDevices = getDevicesFromRincons(groupedRincons)
  return groupedDevices
}

List<DeviceWrapper> getGroupedPlayerDevicesFromGetZoneGroupAttributes(GPathResult xml, String rincon) {
  List<DeviceWrapper> groupedDevices = []
  List<String> groupIds = []
  List<String> groupedRincons = splitTokens(xml['Body']['GetZoneGroupAttributesResponse']['CurrentZonePlayerUUIDsInGroup'].text()?.toString(), ',')
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  groupedRincons.each{groupIds.add(splitTokens("${it}", '_')[1][0..-6])}
  groupIds.each{groupedDevices.add(getChildDevice(it))}
  return groupedDevices
}

String getGroupForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = getSonosLocalUpnpHost(device)
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
  String ip = getSonosLocalUpnpHost(device)
  if(ip == null) {
    logWarn("No deviceIp found for ${device?.displayName}; cannot determine group coordinator")
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

  List<String> groupIdParts = splitTokens(groupId, ':')
  if(groupIdParts.size() == 0) {
    logWarn("Unexpected group ID '${groupId}' returned for ${device?.displayName}")
    return null
  }

  return getDeviceFromRincon(groupIdParts[0])
}

String getHouseholdForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = getSonosLocalUpnpHost(device)
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
  String localApiUrl = getSonosLocalApiUrl(device)
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
  return StringEscapeUtils.unescapeXml(text)
}

String unEscapeMetaData(String text) {
  return StringEscapeUtils.unescapeXml(text.replace('&amp;lt;', '&lt;').replace('&amp;gt;', '&gt;').replace('&amp;quot;', '&quot;'))
}
// =============================================================================
// Helper methods
// =============================================================================



// =============================================================================
// Component Methods for Child Event Processing
// =============================================================================
void updateGroupDevices(String coordinatorId, List<String> playersInGroup) {
  logDebug('updateGroupDevices')
  if(!coordinatorId || !playersInGroup) {
    return
  }
  // Single pass over all children: partition into group devices (for this coordinator)
  // and a RINCON lookup map (for player name resolution). Avoids two separate getChildDevices() calls.
  Map partitioned = getGroupDevicesAndRinconMap(coordinatorId)
  List<ChildDeviceWrapper> groupsForCoord = (List<ChildDeviceWrapper>)partitioned['groupDevices']
  Map<String, ChildDeviceWrapper> rinconMap = (Map<String, ChildDeviceWrapper>)partitioned['rinconMap']
  List<String> groupDnis = groupsForCoord.collect { ChildDeviceWrapper gd -> gd.getDeviceNetworkId() }
  logDebug("updateGroupDevices: coordinator=${coordinatorId}, players=${playersInGroup}, matchingGroups=${groupDnis}")
  Map<String, String> playerNames = [:]
  rinconMap.each { String id, ChildDeviceWrapper player ->
    playerNames[id] = player.getDataValue('name')?.toString() ?: id
  }
  ChildDeviceWrapper callerDevice = rinconMap[coordinatorId]
  Boolean isCallerActualCoordinator = isSonosCoordinator(callerDevice)
  Map<String, Long> generations = state[GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY] instanceof Map
      ? (Map<String, Long>)state[GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY] : [:]
  Long generation = ((generations[coordinatorId] ?: 0L) as Long) + 1L
  generations[coordinatorId] = generation
  state[GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY] = generations
  applyGroupDeviceMembershipBatch(coordinatorId, new ArrayList<String>(playersInGroup), groupDnis,
      playerNames, isCallerActualCoordinator, generation, 0)
}

/** Apply membership to a bounded number of group devices per invocation. */
void applyGroupDeviceMembershipBatch(String coordinatorId, List<String> playersInGroup,
    List<String> groupDnis, Map<String, String> playerNames, Boolean isCallerActualCoordinator,
    Long generation, Integer cursor) {
  Map<String, Long> generations = state[GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY] instanceof Map
      ? (Map<String, Long>)state[GROUP_MEMBERSHIP_GENERATIONS_STATE_KEY] : [:]
  if(((generations[coordinatorId] ?: 0L) as Long) != generation) { return }
  List<String> joinedPlayerNames = playersInGroup.collect { String rincon -> playerNames[rincon] ?: rincon }
  String joinedPlayersValue = joinedPlayerNames.join(', ')
  ChildDeviceWrapper callerDevice = getCurrentPlayerDevices().find { ChildDeviceWrapper player ->
    player.getDataValue('id') == coordinatorId
  }
  Integer end = Math.min(groupDnis.size(), cursor + GROUP_MEMBERSHIP_APPLY_BATCH_SIZE)
  while(cursor < end) {
    String groupDni = groupDnis[cursor++]
    ChildDeviceWrapper gd = getChildDevice(groupDni)
    if(gd == null) { continue }
    Boolean wasActive = gd.currentValue('switch', true) == 'on'
    HashSet<String> configuredPlayers = new HashSet<String>(getAllPlayersForGroupDevice(gd))
    HashSet<String> observedPlayers = new HashSet<String>(normalizeGroupPlayerIds(playersInGroup))
    String rememberedMode = getFreshGroupingContext(gd.getDeviceNetworkId())?.mode as String
    String groupingMode = normalizeGroupOperationMode(rememberedMode, GROUPING_MODE_EXPLICIT)
    Boolean membershipSatisfied = groupingMode == GROUPING_MODE_EXPLICIT
        ? configuredPlayers.equals(observedPlayers)
        : observedPlayers.containsAll(configuredPlayers)
    Boolean shouldBeActive = membershipSatisfied && isCallerActualCoordinator
    Map<String, Object> attributes = new LinkedHashMap<String, Object>()
    attributes['switch'] = shouldBeActive ? 'on' : 'off'
    attributes['currentlyJoinedPlayers'] = joinedPlayersValue
    queueGroupDeviceState(gd.getDeviceNetworkId(), attributes)
  }
  if(cursor < groupDnis.size()) {
    runIn(1, 'continueGroupDeviceMembershipUpdate', [overwrite: false, data: [
      coordinatorId: coordinatorId, playersInGroup: playersInGroup, groupDnis: groupDnis,
      playerNames: playerNames, isCallerActualCoordinator: isCallerActualCoordinator,
      generation: generation, cursor: cursor]])
  }
}

void continueGroupDeviceMembershipUpdate(Map data = [:]) {
  applyGroupDeviceMembershipBatch(data.coordinatorId?.toString(),
      data.playersInGroup instanceof List ? (List<String>)data.playersInGroup : [],
      data.groupDnis instanceof List ? (List<String>)data.groupDnis : [],
      data.playerNames instanceof Map ? (Map<String, String>)data.playerNames : [:],
      data.isCallerActualCoordinator == true, (data.generation ?: 0L) as Long,
      (data.cursor ?: 0) as Integer)
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
    if(value != null) { groupState[name] = value }
  }
  if(groupState.isEmpty()) { return }
  pending[groupDni] = groupState
  state[PENDING_GROUP_STATE_QUEUE_KEY] = pending

  // Re-arm on every enqueue. The scheduled-job table can lose a job while the
  // persisted marker remains true (for example after an app update), which
  // would otherwise strand all subsequent group state updates.
  state[GROUP_STATE_DRAIN_SCHEDULED_KEY] = true
  runInMillis(1000, 'drainGroupStateUpdates', [overwrite: true])
}

/**
 * Deliver coalesced group state after the originating player callback returns.
 * Each event is attempted once; event publication has no retry queue.
 */
void drainGroupStateUpdates() {
  Map<String, Map<String, Object>> pending = state[PENDING_GROUP_STATE_QUEUE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_GROUP_STATE_QUEUE_KEY]
      : [:]
  state.remove(PENDING_GROUP_STATE_QUEUE_KEY)
  state.remove(GROUP_STATE_DRAIN_SCHEDULED_KEY)
  if(pending.isEmpty()) {
    return
  }
  Map<String, Map<String, Object>> deferred = [:]
  List<String> groupDnis = new ArrayList<String>(pending.keySet())
  Set<String> selected = new LinkedHashSet<String>(groupDnis.take(GROUP_STATE_DRAIN_MAX_ITEMS))
  groupDnis.each { String groupDni ->
    if(!selected.contains(groupDni)) { deferred[groupDni] = pending[groupDni] }
  }
  selected.each { String groupDni ->
    Map<String, Object> attributes = pending[groupDni]
    ChildDeviceWrapper groupDevice = getChildDevice(groupDni)
    if(groupDevice == null) {
      logWarn("drainGroupStateUpdates: no group device found for DNI ${groupDni}")
      return
    }
    logDebug("Delivering group state to ${groupDni}: attributes=${attributes.keySet()}")
    sendChildAttributeEvents(groupDevice, new LinkedHashMap<String, Object>(attributes))
    logDebug("Attempted ${attributes.size()} group state event(s) for ${groupDni}")
  }
  Map<String, Map<String, Object>> retryAndNew = new LinkedHashMap<String, Map<String, Object>>(deferred)
  Map<String, Map<String, Object>> arrivals = state[PENDING_GROUP_STATE_QUEUE_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_GROUP_STATE_QUEUE_KEY] : [:]
  arrivals.each { String dni, Map<String, Object> attrs ->
    Map<String, Object> combined = retryAndNew[dni] instanceof Map ? retryAndNew[dni] : [:]
    combined.putAll(attrs)
    retryAndNew[dni] = combined
  }
  if(retryAndNew.isEmpty()) {
    return
  }
  state[PENDING_GROUP_STATE_QUEUE_KEY] = retryAndNew
  state[GROUP_STATE_DRAIN_SCHEDULED_KEY] = true
  runInMillis(1000, 'drainGroupStateUpdates', [overwrite: true])
}

/** Apply the group driver's inactive and track-position guards before app-owned events are emitted. */

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
  Long cookieAge = millisSince(state.hubCookieTime as Long)
  if(state.hubCookie && cookieAge != null && cookieAge < HUB_COOKIE_MAX_AGE_MS) {
    job.cookie = state.hubCookie
    versionCheckRequest(job, 'appList', [uri:'http://127.0.0.1:8080', path:'/hub2/userAppTypes', headers:hubCookieHeaders(job.cookie as String), timeout:15, ignoreSSLIssues:true])
  } else {
    versionCheckRequest(job, 'login', [uri:'http://127.0.0.1:8080', path:'/login', requestContentType:'application/x-www-form-urlencoded',
      body:[username:getAppPreference('hubSecurityUsername', '')?.toString() ?: '', password:getAppPreference('hubSecurityPassword', '')?.toString() ?: '', submit:'Login'],
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
      versionCheckDriverList(job)
      return
    }
    if(stage == 'driverList') {
      Object payload = ok ? response.getJson() : null
      job.drivers = payload instanceof Map && ((Map)payload).drivers instanceof Collection ? ((Map)payload).drivers : []
      versionCheckNextArtifact(job)
      return
    }
    if(stage == 'driverCode') {
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
  }
}

void versionCheckRecordArtifact(Map artifact, String installedVersion, String error) {
  String expected = artifact.expectedVersion?.toString() ?: getCurrentVersion()
  if(error) {
    state.installedVersions << [name:artifact.fileName ?: artifact.name, installedVersion:'Error', expectedVersion:expected, status:error]
  } else if(installedVersion && installedVersion != expected) {
    state.versionMismatches << [name:artifact.name, fileName:artifact.fileName, namespace:artifact.namespace, installedVersion:installedVersion, expectedVersion:expected, location:artifact.location, type:artifact.type]
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
  List<Map> driverMismatches = ((List<Map>)(state.versionMismatches ?: [])).findAll { Map item -> item.type == 'driver' }
  if(driverMismatches.isEmpty()) { logInfo('No driver version mismatches to fix'); return }
  Map job = [id:UUID.randomUUID().toString(), op:'repairVersions', drivers:driverMismatches.collect { Map item ->
    [name:item.name, namespace:item.namespace ?: 'dwinks', location:item.location]
  }, errors:[], successCount:0]
  state.maintenanceJob = job
  job.pendingRequest = [stage:'nextDriver']
  maintenanceAuthenticate(job)
}

void fixDriverMismatches() {
  if(state.maintenanceJob instanceof Map) { logWarn('A maintenance operation is already in progress'); return }
  List driverMismatches = state.pendingDriverMismatches ?: []
  state.remove('pendingDriverMismatches')
  if(!driverMismatches) { checkInstalledVersions(); return }
  Map job = [id:UUID.randomUUID().toString(), op:'repairVersions', drivers:driverMismatches.collect { Map item ->
    [name:item.name, namespace:item.namespace ?: 'dwinks', location:item.location]
  }, errors:[], successCount:0]
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
  String requestSummary = "Dispatching local HTTP ${retryData.method ?: 'POST'} request ${retryData.requestId ?: 'untracked'} attempt=${retryData.attemptNumber ?: 1} attemptId=${retryData.attemptId ?: 'untracked'} uri=${params.uri}; payload omitted"
  logPayloadAtTrace(requestSummary, "sendLocalCommandAsync: ${params}")
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
  logPayloadAtTrace("Sending local HTTP GET request uri=${params.uri}; payload omitted", "sendLocalQuerySync: ${params}")
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
  String requestSummary = "Dispatching local HTTP GET request ${retryData.requestId ?: 'untracked'} attempt=${retryData.attemptNumber ?: 1} attemptId=${retryData.attemptId ?: 'untracked'} uri=${params.uri}; payload omitted"
  logPayloadAtTrace(requestSummary, "sendLocalQueryAsync: ${params}")
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
  String requestSummary = "Dispatching local HTTP POST request ${retryData.requestId ?: 'untracked'} attempt=${retryData.attemptNumber ?: 1} attemptId=${retryData.attemptId ?: 'untracked'} uri=${params.uri}; payload omitted"
  logPayloadAtTrace(requestSummary, "sendLocalJsonAsync: ${params}")
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
    logPayloadAtTrace('Successful local HTTP response received; body omitted', "localControlCallback: ${response.getData()}")
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


Boolean responseIsValid(AsyncResponse response, String requestName = null) {
  if(response == null) {
    logError("${requestName ?: 'Request'} received no response")
    return false
  }
  if(response?.status != 200 || response.hasError()) {
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
  if(!manual && getAppPreference('autoCheckUpdates', false) != true) { return }

  // Check frequency limits for automatic checks
  if(!manual && state.lastUpdateCheck) {
    Long lastCheck = state.lastUpdateCheck as Long
    Instant nowInstant = hubNowInstant()
    if(getAppPreference('updateCheckFrequency', 'Daily') == 'Daily' && Duration.between(Instant.ofEpochMilli(lastCheck), nowInstant).compareTo(Duration.ofDays(1)) < 0) { return }
    if(getAppPreference('updateCheckFrequency', 'Daily') == 'Weekly' && Duration.between(Instant.ofEpochMilli(lastCheck), nowInstant).compareTo(Duration.ofDays(7)) < 0) { return }
  }

  Long startedAt = state.updateCheckStartedAt instanceof Number ? (state.updateCheckStartedAt as Long) : 0L
  if(!manual && state.updateCheckRequestId && millisSince(startedAt as Long) < Duration.ofMinutes(1).toMillis()) { return }
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
    Long cookieAge = millisSince(state.hubCookieTime as Long)
    if(state.hubCookie != null && cookieAge != null && cookieAge < HUB_COOKIE_MAX_AGE_MS) {
      state.updateCheckCookie = state.hubCookie
      requestUpdateCheckAppList(next)
    } else {
      asynchttpPost('updateCheckLoginCallback', [uri: 'http://127.0.0.1:8080', path: '/login',
        requestContentType: 'application/x-www-form-urlencoded',
        body: [username: getAppPreference('hubSecurityUsername', '')?.toString() ?: '', password: getAppPreference('hubSecurityPassword', '')?.toString() ?: '', submit: 'Login'],
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
  logDebug("Async update check cached installed=${currentVersion}, latest=${latestVersion}, comparison=${comparison}")
  state.lastUpdateCheck = now()
  state.lastUpdateCheckFormatted = ZonedDateTime.now(location?.timeZone ? ZoneId.of(location.timeZone.ID) : ZoneId.systemDefault()).format(APP_TIMESTAMP_FORMATTER)
  Boolean updateDismissed = state.dismissedUpdateVersion?.toString() == latestVersion
  logDebug("Completing update check; currentVersion=${currentVersion}, latestVersion=${latestVersion}, comparison=${comparison}, dismissedVersion=${state.dismissedUpdateVersion}, updateDismissed=${updateDismissed}")
  if(comparison < 0 && !updateDismissed) {
    state.updateAvailable = true
    state.latestVersion = latestVersion
    state.latestManifest = manifest
    state.latestReleaseDate = manifest.dateReleased ?: manifest.releaseDate ?: 'Unknown'
    state.latestReleaseUrl = "https://github.com/DanielWinks/Hubitat-Public/releases/tag/v${latestVersion}"
    app.updateLabel('Sonos Advanced Controller [Update Available]')
    logInfo("Update available: ${latestVersion} (current: ${currentVersion})")
    if(manual) { runIn(1, 'refreshPage') }
    if(getAppPreference('autoInstallUpdates', false) == true && !manual) { scheduleAutoInstall() }
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

  List parts1 = splitTokens(version1, '.')
  List parts2 = splitTokens(version2, '.')

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
  Long cookieAge = millisSince(state.hubCookieTime as Long)
  if(state.hubCookie != null && cookieAge != null && cookieAge < HUB_COOKIE_MAX_AGE_MS) {
    job.cookie = state.hubCookie
    Map pending = (Map)job.pendingRequest
    if(pending.stage == 'nextDriver') { maintenanceStartDrivers(job) }
    else if(pending.stage == 'duplicateDeletes') { maintenanceNextDuplicateDelete(job) }
    else { maintenanceRequest(job, pending.stage as String, (Map)pending.params, pending.method as String) }
    return
  }
  job.stage = 'login'
  try {
    String requestId = UUID.randomUUID().toString()
    job.activeRequestId = requestId
    asynchttpPost('maintenanceHttpCallback', [uri:'http://127.0.0.1:8080', path:'/login', requestContentType:'application/x-www-form-urlencoded',
      body:[username:getAppPreference('hubSecurityUsername', '')?.toString() ?: '', password:getAppPreference('hubSecurityPassword', '')?.toString() ?: '', submit:'Login'],
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
    if(stage == 'downloadApp' || stage == 'downloadDriver') {
      String source = ok ? response.data?.toString() : null
      if(!source) { maintenanceAdvanceFailure(job, stage); return }
      job.source = source
      if(stage == 'downloadApp') { maintenanceRequest(job, 'appList', [uri:'http://127.0.0.1:8080', path:'/hub2/userAppTypes', timeout:15, ignoreSSLIssues:true]) }
      else { maintenanceRequest(job, 'driverList', [uri:'http://127.0.0.1:8080', path:'/device/drivers', headers:hubCookieHeaders(job.cookie as String), timeout:15]) }
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
      if(pending.stage == 'nextDriver') { maintenanceStartDrivers(job) }
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
  // Hub sessions expire, so discard stale credentials before app and driver checks.
  Long cookieAgeMs = millisSince(state.hubCookieTime as Long)
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
        username: getAppPreference('hubSecurityUsername', '')?.toString() ?: '',
        password: getAppPreference('hubSecurityPassword', '')?.toString() ?: '',
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

  if(getAppPreference('autoCheckUpdates', false) == true) {
    if(getAppPreference('updateCheckFrequency', 'Daily') == 'Daily') {
      schedule('0 0 2 * * ?', 'checkForUpdates')  // 2 AM daily
    } else if(getAppPreference('updateCheckFrequency', 'Daily') == 'Weekly') {
      schedule('0 0 2 ? * MON', 'checkForUpdates')  // 2 AM Monday
    }
  }
}

void scheduleAutoInstall() {
  if(!(getAppPreference('autoInstallUpdates', false) == true) || !getAppPreference('autoInstallTime') || !state.updateAvailable) {
    unschedule('performScheduledInstall')
    return
  }

  try {
    Date installTime = timeToday(getAppPreference('autoInstallTime'), location.timeZone)
    String cronExpression
    ZoneId zone = hubZoneId()
    Clock clock = hubClock()

    if(getAppPreference('autoInstallNextOccurrence', false) == true) {
      cronExpression = "0 ${installTime.minutes} ${installTime.hours} * * ?"
      ZonedDateTime nowDateTime = ZonedDateTime.now(clock)
      ZonedDateTime installDateTime = atInstallTime(installTime, zone)
      if(installDateTime.isBefore(nowDateTime)) {
        logDebug("Auto-install time has passed today, will install tomorrow at ${getAppPreference('autoInstallTime')}")
      } else {
        logDebug("Auto-install scheduled for today at ${getAppPreference('autoInstallTime')}")
      }
    } else {
      DayOfWeek weekday = parseDayOfWeek(getAppPreference('autoInstallDayOfWeek', 'Sunday') ?: 'Sunday')
      String cronDay = weekday.name().substring(0, 3)
      cronExpression = "0 ${installTime.minutes} ${installTime.hours} ? * ${cronDay}"
      LocalTime time = LocalTime.of(installTime.hours, installTime.minutes)
      ZonedDateTime nextFire = nextWeeklyOccurrence(time, weekday, zone)
      logDebug("Auto-install scheduled for ${weekday}s at ${getAppPreference('autoInstallTime')}; next fire ${nextFire.format(APP_TIMESTAMP_FORMATTER)}")
    }

    CronExpression cron = new CronExpression(cronExpression)
    Date nextFireDate = cron.getNextValidTimeAfter(Date.from(clock.instant()))
    logDebug("Auto-install cron ${cronExpression} validated; next fire ${nextFireDate}")

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
