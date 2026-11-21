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
import groovy.json.JsonOutput
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Field static final List<String> LOG_LEVELS = ['trace', 'debug', 'info', 'warn', 'error', 'off']

void logError(String message) {
  if (loggingEnabled('error')) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}

void logWarn(String message) {
  if (loggingEnabled('warn')) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}

void logInfo(String message) {
  if (loggingEnabled('info')) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}

void logDebug(String message) {
  if (loggingEnabled('debug')) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}

void logTrace(String message) {
  if (loggingEnabled('trace')) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

@Field static ConcurrentHashMap<String, Map> groupDeviceVolumeFadeState = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> runtimeStateByDni = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>()
@Field static ConcurrentHashMap<String, Long> lastGroupDeviceVolumeFadeCallTime = new ConcurrentHashMap<String, Long>()
@Field static ConcurrentHashMap<String, Map<String, Object>> heldPlaybackState = new ConcurrentHashMap<String, Map<String, Object>>()
@Field static final Set<String> MEMBERSHIP_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>(['switch', 'currentlyJoinedPlayers']))
@Field static volatile List<String> cachedTTSVoiceNames = null
@Field static volatile String cachedTTSDefaultVoice = null
@Field static final String GROUPING_MODE_EXPLICIT = 'EXPLICIT'
@Field static final String GROUPING_MODE_ADDITIVE = 'ADDITIVE'
@Field static final String PENDING_BATCH_EVENTS_KEY = 'pendingBatchEvents'
@Field static final String BATCH_EVENTS_SCHEDULED_KEY = 'batchEventsScheduled'
@Field static final String BATCH_EVENTS_DUE_AT_KEY = 'batchEventsDueAt'
@Field static final Integer BATCH_EVENTS_PER_DRAIN = 8
@Field static final Integer BATCH_EVENTS_INTERVAL_MS = 250
@Field static final List<String> DEPRECATED_GROUP_STATE_ATTRIBUTES = [
  'groupCommandRequest',
  'sonosGroupCommandRequest',
  'groupingMode',
  'groupOperationStatus'
]
@Field static final String PARENT_REQUEST_DRAIN_SCHEDULED_KEY = 'parentRequestDrainScheduled'
@Field static final Integer PARENT_REQUEST_DRAIN_MS = 1
@Field static final Integer PARENT_REQUEST_MAX_ITEMS = 32
@Field static final Integer PARENT_REQUEST_RETRY_MS = 1000
@Field static ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>> pendingGroupRequests = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static ConcurrentHashMap<String, Object> parentRequestLocks = new ConcurrentHashMap<String, Object>()

metadata {
  definition(
    name: 'Sonos Advanced Group',
    version: '0.18.9',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvGroup.groovy',
    dashboardTypes: [
      "MusicPlayer"
    ]
  ) {
    capability 'MusicPlayer'
    capability 'AudioNotification'
    capability 'AudioVolume'
    capability 'SpeechSynthesis'
    capability 'Actuator'
    capability 'Switch'
    capability 'SwitchLevel'

    command 'groupPlayers'
    command 'joinPlayersToCoordinator'
    command 'addPlayerToGroup', [[name: 'Player', type: 'STRING', description: 'RINCON ID, player name, IP address, MAC address, or Device Network ID']]
    command 'removePlayersFromCoordinator'
    command 'ungroupPlayers'
    command 'evictUnlistedPlayers'
    command 'refresh'
    command 'setVolumeZero'
    command 'playHighPriorityTTS', [
      [name:'Text*', type:"STRING", description:"Text to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"0 uses the current/default level; -1 explicitly requests zero; 1-100 sets the level", constraints:["NUMBER"]],
      [name: 'Voice name', type: "ENUM", constraints: getCachedTTSVoiceNames(), defaultValue: getCurrentTTSVoice()]
    ]

    command 'playHighPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"0 uses the current/default level; -1 explicitly requests zero; 1-100 sets the level", constraints:["NUMBER"]]
    ]

    command 'enqueueLowPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"0 uses the current/default level; -1 explicitly requests zero; 1-100 sets the level", constraints:["NUMBER"]]
    ]

    // Playback control commands
    command 'setRepeatMode', [[ name: 'Repeat Mode', type: 'ENUM', constraints: [ 'off', 'repeat one', 'repeat all' ]]]
    command 'repeatOne'
    command 'repeatAll'
    command 'repeatNone'
    command 'setShuffle', [[ name: 'Shuffle Mode', type: 'ENUM', constraints: ['on', 'off']]]
    command 'shuffleOn'
    command 'shuffleOff'
    command 'setCrossfade', [[ name: 'Crossfade Mode', type: 'ENUM', constraints: ['on', 'off']]]
    command 'enableCrossfade'
    command 'disableCrossfade'
    command 'getFavorites'
    command 'loadFavorite', [[ name: 'favoriteId', type: 'STRING']]
    command 'loadFavoriteFull', [
      [name: 'favoriteId', type: 'STRING'],
      [name: 'playMode', type: 'ENUM', constraints: ['NORMAL', 'REPEAT_ALL', 'REPEAT_ONE', 'SHUFFLE_NOREPEAT', 'SHUFFLE', 'SHUFFLE_REPEAT_ONE']],
      [name: 'startTrack', type: 'NUMBER'],
      [name: 'startTime', type: 'NUMBER']
    ]

    command 'getPlaylists'
    command 'loadPlaylist', [[ name: 'playlistId', type: 'STRING']]
    command 'loadPlaylistFull', [
      [ name: 'playlistId', type: 'STRING'],
      [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off', 'unchanged' ]],
      [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
      [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on', 'unchanged']],
      [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
      [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
    ]

    attribute 'coordinatorActive', 'string'
    attribute 'followers', 'string'
    attribute 'currentlyJoinedPlayers', 'string'
    attribute 'zgtInProgress', 'enum', ['on', 'off']
    attribute 'parentAppRequest', 'string'

    // Extended playback attributes forwarded from coordinator
    attribute 'currentTrackDuration', 'string'
    attribute 'trackPosition', 'number'
    attribute 'trackPositionUpdated', 'number'
    attribute 'currentArtistName', 'string'
    attribute 'albumArtURI', 'string'
    attribute 'albumArtSmall', 'string'
    attribute 'albumArtMedium', 'string'
    attribute 'albumArtLarge', 'string'
    attribute 'audioSource', 'string'
    attribute 'currentAlbumName', 'string'
    attribute 'currentTrackName', 'string'
    attribute 'currentFavorite', 'string'
    attribute 'currentPlaylist', 'string'
    attribute 'currentTrackNumber', 'number'
    attribute 'nextArtistName', 'string'
    attribute 'nextAlbumName', 'string'
    attribute 'nextTrackName', 'string'
    attribute 'nextTrackAlbumArtURI', 'string'
    attribute 'queueTrackTotal', 'string'
    attribute 'queueTrackPosition', 'string'
    attribute 'transportStatus', 'enum', ['playing', 'paused', 'stopped']
    attribute 'groupVolume', 'number'
    attribute 'groupMute', 'string'
    attribute 'currentRepeatOneMode', 'enum', ['on', 'off']
    attribute 'currentRepeatAllMode', 'enum', ['on', 'off']
    attribute 'currentCrossfadeMode', 'enum', ['on', 'off']
    attribute 'currentShuffleMode', 'enum', ['on', 'off']
    attribute 'lastUpdated', 'number'
  }
  preferences {
    section('Device Settings') {
      input 'chimeBeforeStandardPriorityTTS', 'bool', title: 'Play chime before standard priority non-interrupting (audioClip) TTS messages', required: false, defaultValue: false
      input 'chimeBeforeHighPriorityTTS', 'bool', title: 'Play chime before high priority non-interrupting (audioClip) TTS messages', required: false, defaultValue: false
    }
    section('Volume Control Settings') {
      input 'controlUngroupedIndividually', 'bool',
        title: 'Control ungrouped speakers individually',
        description: 'When speakers are not grouped in Sonos, control each speaker\'s volume instead of just the coordinator',
        required: false, defaultValue: false
      input 'useProportionalVolume', 'bool',
        title: 'Use proportional volume control (Sonos group volume API)',
        description: 'When speakers are grouped, use Sonos native group volume API which maintains relative volume ratios between speakers. When disabled, sets the same volume on each speaker directly.',
        required: false, defaultValue: true
    }
    section('State Update Settings') {
      input 'enableTrackPositionUpdates', 'bool',
        title: 'Enable track position updates',
        required: false, defaultValue: false
      input 'onlyUpdateWhenActive', 'bool',
        title: 'Only update state when group is active',
        description: 'When enabled, playback state updates are skipped when the group\'s configured speakers are not all grouped together in Sonos. Reduces hub load when many groups share a coordinator.',
        required: false, defaultValue: true
      input 'resetAttributesWhenInactive', 'bool',
        title: 'Reset attributes when group becomes inactive',
        description: 'When enabled, playback attributes (track name, artist, album art, etc.) are cleared when the group\'s speakers are no longer grouped together. Shows a clean state on dashboards.',
        required: false, defaultValue: true
    }
    section('Logging Settings') {
      input 'logLevel', 'enum',
        title: 'Logging level',
        options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'],
        required: false,
        defaultValue: 'info',
        submitOnChange: true
    }
  }
}

String getConfiguredLogLevel() {
  if(settings.logLevel != null) { return normalizeLogLevel(settings.logLevel.toString()) }
  if(settings.logEnable == false) { return 'off' }
  if(settings.traceLogEnable == true) { return 'trace' }
  if(settings.debugLogEnable == true) { return 'debug' }
  return 'info'
}

String normalizeLogLevel(String level) {
  String normalized = level?.toLowerCase()
  return LOG_LEVELS.contains(normalized) ? normalized : 'info'
}

Boolean loggingEnabled(String messageLevel) {
  Integer configuredIndex = LOG_LEVELS.indexOf(getConfiguredLogLevel())
  Integer messageIndex = LOG_LEVELS.indexOf(messageLevel)
  return configuredIndex >= 0 && messageIndex >= configuredIndex && messageIndex < LOG_LEVELS.indexOf('off')
}
Boolean getChimeBeforeStandardPriorityTTSSetting() {
  if(settings.chimeBeforeStandardPriorityTTS != null) { return settings.chimeBeforeStandardPriorityTTS }
  return settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false
}
Boolean getChimeBeforeHighPriorityTTSSetting() { return settings.chimeBeforeHighPriorityTTS != null ? settings.chimeBeforeHighPriorityTTS : false }
Boolean getControlUngroupedIndividuallySetting() { return settings.controlUngroupedIndividually != null ? settings.controlUngroupedIndividually : false }
Boolean getUseProportionalVolumeSetting() { return settings.useProportionalVolume != null ? settings.useProportionalVolume : true }
Boolean getOnlyUpdateWhenActiveSetting() { return settings.onlyUpdateWhenActive != null ? settings.onlyUpdateWhenActive : true }
Boolean getResetAttributesWhenInactiveSetting() { return settings.resetAttributesWhenInactive != null ? settings.resetAttributesWhenInactive : true }
Boolean getEnableTrackPositionUpdates() { return settings.enableTrackPositionUpdates != null ? settings.enableTrackPositionUpdates : false }

void setLastGroupingMode(String groupingMode) {
  if(groupingMode != GROUPING_MODE_EXPLICIT && groupingMode != GROUPING_MODE_ADDITIVE) {
    return
  }
  runtimeState.lastGroupingMode = groupingMode
  // Keep the mode available to the parent app without creating a device
  // current-state attribute. Data values are not rendered as current states.
  device.updateDataValue('lastGroupingMode', groupingMode)
}

void removeStaleGroupStates() {
  DEPRECATED_GROUP_STATE_ATTRIBUTES.each { String attributeName ->
    try {
      device.deleteCurrentState(attributeName)
    } catch(Exception e) {
      logDebug("Could not remove stale group state ${attributeName}: ${e.message}")
    }
  }
}

Map getGroupCommandSettings() {
  return [
    useProportionalVolume: getUseProportionalVolumeSetting(),
    controlUngroupedIndividually: getControlUngroupedIndividuallySetting()
  ]
}

/**
 * Queue a group command for deferred delivery to the parent app.
 * The caller returns immediately; the drain calls parent.groupAppRequest
 * after the device gate is released so playHighPriorityTTS never blocks
 * on the app gate from inside parse/command execution.
 */
void setZgtInProgress(Object inProgress) {
  String normalized = (inProgress == true || inProgress?.toString()?.trim()?.toLowerCase() in ['true', 'on', 'yes']) ? 'on' : 'off'
  sendEvent(name: 'zgtInProgress', value: normalized)
}

void requestGroupCommand(String command, Map args = [:]) {
  if(!command) {
    return
  }
  if(command in ['on', 'groupPlayers', 'joinPlayersToCoordinator', 'evictUnlistedPlayers', 'addPlayerToGroup', 'removePlayersFromCoordinator', 'ungroupPlayers', 'off']) {
    setZgtInProgress(true)
  }
  Integer sequence = ((runtimeState.groupCommandSequence ?: 0) as Integer) + 1
  runtimeState.groupCommandSequence = sequence
  Map payload = [
    version: 1,
    channel: 'groupAppRequest',
    groupDni: device.getDeviceNetworkId(),
    requestId: UUID.randomUUID().toString(),
    sequence: sequence,
    createdAt: now(),
    command: command,
    args: args ?: [:]
  ]
  logDebug("Queued parent app request action=${command}, requestId=${payload.requestId}, sourceDni=${payload.groupDni}")
  enqueueGroupAppRequest(payload)
}

private Object getParentRequestLock(String dni) {
  Object lock = parentRequestLocks.get(dni)
  if(lock == null) {
    Object created = new Object()
    Object existing = parentRequestLocks.putIfAbsent(dni, created)
    lock = existing ?: created
  }
  return lock
}

void enqueueGroupAppRequest(Map payload) {
  String dni = device.getDeviceNetworkId()
  if(!dni || payload == null) { return }
  Object lock = getParentRequestLock(dni)
  synchronized(lock) {
    ConcurrentLinkedQueue<Map> queue = pendingGroupRequests.get(dni)
    if(queue == null) {
      ConcurrentLinkedQueue<Map> created = new ConcurrentLinkedQueue<Map>()
      ConcurrentLinkedQueue<Map> existing = pendingGroupRequests.putIfAbsent(dni, created)
      queue = existing ?: created
    }
    queue.add(new LinkedHashMap(payload))
    if(queue.size() > PARENT_REQUEST_MAX_ITEMS) {
      queue.poll()
      logWarn("Parent group request queue overflow for dni=${dni}; dropped oldest request")
    }
  }
}

private void scheduleParentRequestDrain(Integer delayMs) {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Object lock = getParentRequestLock(dni)
  synchronized(lock) {
    ConcurrentLinkedQueue<Map> queue = pendingGroupRequests.get(dni)
    if(queue == null || queue.isEmpty()) {
      state.remove(PARENT_REQUEST_DRAIN_SCHEDULED_KEY)
      return
    }
    state[PARENT_REQUEST_DRAIN_SCHEDULED_KEY] = true
    try {
      runInMillis(Math.max(1, delayMs ?: PARENT_REQUEST_DRAIN_MS), 'flushPendingGroupRequests', [overwrite: true])
    } catch(Exception e) {
      state.remove(PARENT_REQUEST_DRAIN_SCHEDULED_KEY)
      logWarn("Could not schedule parent group request drain for dni=${dni}: ${e.message}")
    }
  }
}

List<Map> drainPendingGroupRequestsForPoll() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return [] }
  Object lock = getParentRequestLock(dni)
  List<Map> batch = []
  synchronized(lock) {
    ConcurrentLinkedQueue<Map> queue = pendingGroupRequests.get(dni)
    if(queue == null || queue.isEmpty()) { return [] }
    Integer count = 0
    while(queue.peek() != null && count < PARENT_REQUEST_MAX_ITEMS) {
      Map item = queue.poll()
      if(item != null) { batch.add(new LinkedHashMap(item)) }
      count++
    }
    if(queue.isEmpty()) { pendingGroupRequests.remove(dni) }
  }
  return batch
}

/** Back-compat poll alias for tests and the app poller. */
List<Map> flushPendingGroupRequests() {
  return drainPendingGroupRequestsForPoll()
}

private Map getFavoriteLoadOptionsFromPlayMode(String playMode) {
  String normalizedPlayMode = playMode != null ? playMode.toUpperCase() : 'NORMAL'
  String repeatMode = 'off'
  String shuffleMode = 'off'

  switch(normalizedPlayMode) {
    case 'REPEAT_ALL':
      repeatMode = 'repeat all'
      break
    case 'REPEAT_ONE':
      repeatMode = 'repeat one'
      break
    case 'SHUFFLE_NOREPEAT':
      shuffleMode = 'on'
      break
    case 'SHUFFLE':
      repeatMode = 'repeat all'
      shuffleMode = 'on'
      break
    case 'SHUFFLE_REPEAT_ONE':
      repeatMode = 'repeat one'
      shuffleMode = 'on'
      break
    case 'NORMAL':
      break
    default:
      logWarn("Unknown favorite play mode '${playMode}', defaulting to NORMAL")
  }

  return [
    repeatMode: repeatMode,
    queueMode: 'replace',
    shuffleMode: shuffleMode,
    autoPlay: 'true',
    crossfadeMode: 'on'
  ]
}


String getCurrentTTSVoice() {
  return cachedTTSDefaultVoice != null ? cachedTTSDefaultVoice : 'Matthew'
}

List<String> getCachedTTSVoiceNames() {
  if(cachedTTSVoiceNames != null && cachedTTSVoiceNames.size() > 0) { return cachedTTSVoiceNames }
  return ['Matthew']
}

void updateTTSVoiceCache(List<String> voiceNames, String defaultVoice) {
  cachedTTSVoiceNames = voiceNames
  cachedTTSDefaultVoice = defaultVoice
  logDebug("TTS voice cache updated: ${voiceNames?.size()} voices, default: ${defaultVoice}")
}


void initialize() {
  clearPendingCommandsForInitialization()
  migrateLegacyRuntimeState()
  resetPendingStatePublicationForInitialization()
  removeStaleGroupStates()
  applyInitializationDefaults()
  // Initialize volume/mute state from coordinator
  runIn(5, 'refresh')
}

/** Clear stale group-device publication work; refresh repopulates authoritative state. */
void resetPendingStatePublicationForInitialization() {
  unschedule('drainBatchEvents')
  state.remove(PENDING_BATCH_EVENTS_KEY)
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  state.remove(BATCH_EVENTS_DUE_AT_KEY)
  logDebug("Cleared stale group state-publication queue and drain job for ${device.getDeviceNetworkId()}")
}

void applyInitializationDefaults() {
  if(settings.chimeBeforeStandardPriorityTTS == null) {
    settings.chimeBeforeStandardPriorityTTS = settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false
  }
  if(settings.chimeBeforeHighPriorityTTS == null) { settings.chimeBeforeHighPriorityTTS = false }
  if(settings.onlyUpdateWhenActive == null) { settings.onlyUpdateWhenActive = true }
  if(settings.resetAttributesWhenInactive == null) { settings.resetAttributesWhenInactive = true }
  if(settings.enableTrackPositionUpdates == null) { settings.enableTrackPositionUpdates = false }
}

/** Clear work owned by this group only at an explicit device initialization. */
void clearPendingCommandsForInitialization() {
  // The command queue used to live in state. Clear that persisted form before
  // migrateLegacyRuntimeState() copies old keys into runtimeState.
  ['pendingGroupCommands', 'activeGroupCommandRequest', 'groupCommandSequence'].each { String key ->
    state.remove(key)
    runtimeState.remove(key)
  }
  unschedule('drainGroupCommandQueue')
  unschedule('retryActiveGroupCommandRequest')
  unschedule('groupDeviceVolumeFadeStep')
  unschedule('flushPendingGroupRequests')
  state.remove(PARENT_REQUEST_DRAIN_SCHEDULED_KEY)
  String dni = device.getDeviceNetworkId()
  groupDeviceVolumeFadeState.remove(dni)
  lastGroupDeviceVolumeFadeCallTime.remove(dni)
  pendingGroupRequests.remove(dni)
  runInMillis(1, 'purgeParentGroupCommandsAfterInitialization', [
    overwrite: true,
    data: [deviceDni: dni]
  ])
  logInfo("Cleared pending group commands for initialization (${dni}); parent purge scheduled")
}

/** Notify the parent after the driver lifecycle callback releases its device gate. */
void purgeParentGroupCommandsAfterInitialization(Map data = [:]) {
  String dni = data?.deviceDni?.toString()
  if(!dni || dni != device.getDeviceNetworkId()?.toString()) {
    logDebug("Ignoring stale parent command purge for ${dni ?: '(unknown device)'}")
    return
  }
  try {
    Map payload = [version: 1, channel: 'groupAppRequest', action: 'initializationPurge',
      groupDni: dni, createdAt: now(), requestId: UUID.randomUUID().toString()]
    // Event-only parent communication (child never calls parent.methodName())
    sendEvent(name: 'parentAppRequest', value: JsonOutput.toJson(payload))
    logDebug("Parent app initialization purge sent via event, sourceDni=${dni}, requestId=${payload.requestId}")
  } catch(Exception e) {
    logWarn("Parent app initialization purge event failed for ${dni}: ${e.message}")
  }
}

void installed() { initialize() }

void updated() {
  initialize()
  configure()
}

void uninstalled() {
  unschedule()
  String dni = device.getDeviceNetworkId()
  runtimeStateByDni.remove(dni)
  groupDeviceVolumeFadeState.remove(dni)
  lastGroupDeviceVolumeFadeCallTime.remove(dni)
  heldPlaybackState.remove(dni)
  pendingGroupRequests.remove(dni)
}

void configure() {
  migrateLegacyRuntimeState()
  schedulePendingBatchEvents()
  removeStaleGroupStates()
  synchronizeTrackPositionPreference()
  Boolean wasGuarded = runtimeState.lastOnlyUpdateWhenActive != false
  Boolean isGuarded = getOnlyUpdateWhenActiveSetting()
  runtimeState.lastOnlyUpdateWhenActive = isGuarded
  if(!isGuarded && wasGuarded) {
    // Guard just disabled — clear held state and refresh to populate skipped attributes
    clearHeldState()
    runIn(2, 'refresh')
  }
}

void synchronizeTrackPositionPreference() {
  Boolean wasEnabled = device.getDataValue('enableTrackPositionUpdates')?.toString()?.toLowerCase() == 'true'
  Boolean isEnabled = getEnableTrackPositionUpdates()
  device.updateDataValue('enableTrackPositionUpdates', isEnabled.toString())
  if(wasEnabled && !isEnabled) {
    queueBatchEvents([trackPosition: 0L, trackPositionUpdated: 0L])
  }
}

ConcurrentHashMap<String, Object> getRuntimeState() {
  String dni = device?.getDeviceNetworkId()
  if(!dni) { return new ConcurrentHashMap<String, Object>() }
  ConcurrentHashMap<String, Object> runtime = runtimeStateByDni.get(dni)
  if(runtime != null) { return runtime }
  ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<String, Object>()
  ConcurrentHashMap<String, Object> existing = runtimeStateByDni.putIfAbsent(dni, created)
  return existing ?: created
}

void migrateLegacyRuntimeState() {
  Map<String, Object> legacy = [:]
  try {
    legacy.putAll(state as Map<String, Object>)
  } catch(Exception e) {
    logWarn("Could not read legacy group device state for migration: ${e.message}")
    return
  }
  ConcurrentHashMap<String, Object> runtime = getRuntimeState()
  legacy.each { String key, Object value ->
    if(value != null) { runtime.putIfAbsent(key, value) }
  }
  Object pendingBatchEvents = state[PENDING_BATCH_EVENTS_KEY]
  Object batchEventsScheduled = state[BATCH_EVENTS_SCHEDULED_KEY]
  state.clear()
  if(pendingBatchEvents != null) { state[PENDING_BATCH_EVENTS_KEY] = pendingBatchEvents }
  if(batchEventsScheduled != null) { state[BATCH_EVENTS_SCHEDULED_KEY] = batchEventsScheduled }
}
void on() {
  setLastGroupingMode(GROUPING_MODE_EXPLICIT)
  Map args = getGroupCommandSettings()
  args.groupingMode = GROUPING_MODE_EXPLICIT
  requestGroupCommand('on', args)
}
void off() { requestGroupCommand('off', getGroupCommandSettings()) }
void setState(String stateName, String stateValue) {
  if(stateValue == null) { runtimeState.remove(stateName) }
  else { runtimeState[stateName] = stateValue }
}
void clearState() { runtimeState.clear() }
void playText(String text, Object volume = null) {
  devicePlayText(text, normalizeLegacyOptionalPlaybackVolume(volume), null, false)
}

void playTextAndRestore(String text, Object volume = null) {
  devicePlayText(text, normalizeLegacyOptionalPlaybackVolume(volume), null, true)
}

void playTextAndResume(String text, Object volume = null) {
  devicePlayText(text, normalizeLegacyOptionalPlaybackVolume(volume), null, true)
}

void speak(String text, Object volume = null, String voice = null) {
  devicePlayText(text, normalizeLegacyOptionalPlaybackVolume(volume), voice)
}

void speakNoRestore(String text, Object volume = null, String voice = null) {
  devicePlayTextNoRestore(text, normalizeLegacyOptionalPlaybackVolume(volume), voice)
}

@CompileStatic
BigDecimal coerceVolume(Object volume) {
  if(volume == null) { return null }
  if(volume instanceof BigDecimal) { return (BigDecimal) volume }
  if(volume instanceof Number) { return new BigDecimal(((Number) volume).toString()) }
  if(volume instanceof CharSequence) {
    String asString = volume.toString().trim()
    if(asString == '') { return null }
    try { return new BigDecimal(asString) }
    catch(NumberFormatException e) {
      logWarn("Could not parse volume '${asString}'; ignoring.")
      return null
    }
  }
  logWarn("Unsupported volume value '${volume}'; ignoring.")
  return null
}

/** Optional group playback volume compatibility: zero means omitted; -1 is passed through for explicit zero. */
@CompileStatic
BigDecimal normalizeLegacyOptionalPlaybackVolume(Object volume) {
  BigDecimal normalized = coerceVolume(volume)
  if(normalized != null && normalized.compareTo(BigDecimal.ZERO) == 0) { return null }
  return normalized
}

void devicePlayText(String text, BigDecimal volume = null, String voice = null, Boolean forceAudioClip = false) {
  if(!text) {
    logWarn('No text provided to play')
    return
  }
  try {
    Map ttsMap = (Map)textToSpeech(text, voice)
    if(!ttsMap || !ttsMap.uri) {
      logWarn('Failed to generate TTS URI')
      return
    }
    String correlationId = UUID.randomUUID().toString()
    requestGroupCommand('playAudioClip', [uri: ttsMap.uri as String, volume: volume?.toString(),
      correlationId: correlationId, duration: ttsMap.duration, forceAudioClip: forceAudioClip == true,
      isTts: true,
      chimeBeforeStandardPriorityTTS: getChimeBeforeStandardPriorityTTSSetting()])
  } catch (Exception e) {
    logError("Error playing text: ${e.message}")
  }
}

// Group announcements use the same synchronized clip path regardless of the
// AudioNotification command variant, so every member starts together.
void devicePlayTextNoRestore(String text, BigDecimal volume = null, String voice = null) {
  devicePlayText(text, volume, voice)
}

void playHighPriorityTTS(String text, Object volume = null, String voice = null) {
  if(!text) {
    logWarn('No text provided to play')
    return
  }
  try {
    Map ttsMap = (Map)textToSpeech(text, voice)
    if(!ttsMap || !ttsMap.uri) {
      logWarn('Failed to generate TTS URI')
      return
    }
    String correlationId = UUID.randomUUID().toString()
    BigDecimal normalizedVolume = normalizeLegacyOptionalPlaybackVolume(volume)
    requestGroupCommand('playHighPriorityTrack', [uri: ttsMap.uri as String, volume: normalizedVolume?.toString(),
      correlationId: correlationId, duration: ttsMap.duration,
      isTts: true,
      chimeBeforeHighPriorityTTS: getChimeBeforeHighPriorityTTSSetting()])
  } catch (Exception e) {
    logError("Error playing high priority TTS: ${e.message}")
  }
}

void playHighPriorityTrack(String uri, Object volume = null) {
  if(!uri) {
    logWarn('No URI provided to play')
    return
  }
  requestGroupCommand('playHighPriorityTrack', [uri: uri,
    volume: normalizeLegacyOptionalPlaybackVolume(volume)?.toString()])
}

void playTrackAndRestore(String uri, Object volume = null) {
  playTrackWithSynchronizedClip(uri, volume)
}

void playTrackAndResume(String uri, Object volume = null) {
  playTrackWithSynchronizedClip(uri, volume)
}

private void playTrackWithSynchronizedClip(String uri, Object volume) {
  if(!uri) {
    logWarn('No URI provided to play')
    return
  }
  requestGroupCommand('playAudioClip', [uri: uri, volume: normalizeLegacyOptionalPlaybackVolume(volume)?.toString(),
    correlationId: UUID.randomUUID().toString()])
}

void enqueueLowPriorityTrack(String uri, Object volume = null) {
  if(!uri) {
    logWarn('No URI provided to enqueue')
    return
  }
  requestGroupCommand('enqueueLowPriorityTrack', [uri: uri,
    volume: normalizeLegacyOptionalPlaybackVolume(volume)?.toString()])
}

void joinPlayersToCoordinator() {
  List<String> followers = getAllFollowersInGroupDevice()
  if(!followers) {
    logWarn('No followers found to join to coordinator')
    return
  }
  setLastGroupingMode(GROUPING_MODE_ADDITIVE)
  requestGroupCommand('joinPlayersToCoordinator', [groupingMode: GROUPING_MODE_ADDITIVE])
}

/** Add one player to this group's coordinator without regrouping its roster. */
void addPlayerToGroup(String playerIdentifier) {
  String identifier = playerIdentifier?.trim()
  if(!identifier) {
    logWarn('Cannot add player to group without a player identifier')
    return
  }
  requestGroupCommand('addPlayerToGroup', [playerIdentifier: identifier])
}

void removePlayersFromCoordinator() {
  List<String> allFollowerIds = getAllFollowersInGroupDevice()
  if(!allFollowerIds) {
    logDebug('No followers to remove from coordinator')
    return
  }
  requestGroupCommand('removePlayersFromCoordinator')
}

void groupPlayers() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  if(!allPlayers) {
    logWarn('No players found to group')
    return
  }
  // Exact grouping is owned by the parent app's operation coordinator. Keeping
  // the mode in the request means this command cannot accidentally take the
  // additive join path when the current group contains extra players.
  setLastGroupingMode(GROUPING_MODE_EXPLICIT)
  requestGroupCommand('groupPlayers', [groupingMode: GROUPING_MODE_EXPLICIT])
}

void ungroupPlayers() {
  List<String> allFollowerIds = getAllFollowersInGroupDevice()
  if(!allFollowerIds) {
    logDebug('No followers to ungroup')
    return
  }
  requestGroupCommand('ungroupPlayers')
}

void evictUnlistedPlayers() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  if(!allPlayers) {
    logWarn('No players found to manage')
    return
  }
  setLastGroupingMode(GROUPING_MODE_EXPLICIT)
  requestGroupCommand('evictUnlistedPlayers', [
    groupingMode: GROUPING_MODE_EXPLICIT,
    evictUnlistedOnly: true
  ])
}

/**
 * Receive operation status from the parent app without making the group
 * driver responsible for orchestration. The JSON value is intentionally kept
 * opaque so status additions remain backward compatible.
 */
void updateGroupOperationStatus(String statusJson, String groupingMode = null) {
  if(groupingMode == GROUPING_MODE_EXPLICIT || groupingMode == GROUPING_MODE_ADDITIVE) {
    setLastGroupingMode(groupingMode)
  }
}

// =============================================================================
// Getters and Setters
// =============================================================================
String getCoordinatorId() {
  return this.device.getDataValue('groupCoordinatorId')
}

List<String> getAllPlayersInGroupDevice() {
  String coordinatorId = this.device.getDataValue('groupCoordinatorId')
  String playerIdsStr = this.device.getDataValue('playerIds')

  List<String> players = []
  if(coordinatorId) {
    players.add(coordinatorId)
  }
  if(playerIdsStr) {
    players.addAll(playerIdsStr.tokenize(','))
  }
  return players
}

List<String> getAllFollowersInGroupDevice() {
  String playerIdsStr = this.device.getDataValue('playerIds')
  if(!playerIdsStr) {
    return []
  }
  return playerIdsStr.tokenize(',')
}

// Player lookup and command dispatch are owned by the parent app. The group
// driver only publishes asynchronous requests and applies local state.

// =============================================================================
// AudioVolume Capability Implementation
// =============================================================================

/**
 * Set volume level (0-100)
 * Behavior depends on settings and whether speakers are grouped in Sonos:
 *
 * When GROUPED in Sonos:
 *   - useProportionalVolume ON: Uses Sonos group volume API (maintains relative ratios)
 *   - useProportionalVolume OFF: Sets same volume on each speaker directly
 *
 * When NOT GROUPED in Sonos:
 *   - controlUngroupedIndividually ON: Sets volume on each speaker
 *   - controlUngroupedIndividually OFF: Only controls the coordinator
 */
void setVolume(BigDecimal level, BigDecimal duration = null) {
  if(level == null) {
    logWarn('No volume level provided')
    return
  }
  String deviceId = device.getDeviceNetworkId()
  Integer targetVolume = Math.max(0, Math.min(100, level.intValue()))

  // If no fade duration, use immediate volume set
  if(duration == null || duration <= 0) {
    cancelGroupDeviceVolumeFade()
    setVolumeImmediate(targetVolume)
    return
  }

  // Rapid-call detection: if an external app is managing the fade by calling setVolume in a loop
  Long now = now()
  Long lastCall = lastGroupDeviceVolumeFadeCallTime.put(deviceId, now)
  if(lastCall != null && (now - lastCall) < 2000) {
    logDebug("Rapid setVolume calls detected (${now - lastCall}ms apart) — setting volume directly to ${targetVolume}")
    cancelGroupDeviceVolumeFade()
    setVolumeImmediate(targetVolume)
    return
  }

  Integer currentVolume = (this.device.currentValue('volume', true) as Integer) ?: 0
  Integer delta = Math.abs(targetVolume - currentVolume)
  if(delta == 0) {
    cancelGroupDeviceVolumeFade()
    return
  }
  Integer durationSeconds = duration as Integer

  // If duration too short or delta too small, just send a single command
  if(durationSeconds < 2 || delta <= 1) {
    cancelGroupDeviceVolumeFade()
    setVolumeImmediate(targetVolume)
    return
  }

  // Calculate step interval: at least 1 second between commands
  Integer steps = Math.min(delta, durationSeconds)
  Integer intervalMs = (Integer)((durationSeconds * 1000) / steps)
  if(intervalMs < 1000) { intervalMs = 1000 }
  steps = (Integer)(durationSeconds * 1000 / intervalMs)
  if(steps < 1) { steps = 1 }
  BigDecimal volumeStep = (BigDecimal)(targetVolume - currentVolume) / steps

  // Cancel any existing fade and start new one
  cancelGroupDeviceVolumeFade()
  Map fadeState = [
    targetVolume: targetVolume,
    startVolume: currentVolume,
    volumeStep: volumeStep,
    currentStep: 0,
    totalSteps: steps,
    intervalMs: intervalMs
  ]
  groupDeviceVolumeFadeState.put(deviceId, fadeState)
  logInfo("Starting volume fade from ${currentVolume} to ${targetVolume} over ${durationSeconds}s (${steps} steps, ${intervalMs}ms interval)")
  runInMillis(intervalMs, 'groupDeviceVolumeFadeStep', [overwrite: true])
}

/**
 * Internal: set volume immediately without fade (original setVolume logic)
 */
private void setVolumeImmediate(Integer volumeLevel) {
  logDebug("setVolumeImmediate(${volumeLevel}) - useProportional: ${getUseProportionalVolumeSetting()}, controlIndividually: ${getControlUngroupedIndividuallySetting()}")
  requestGroupCommand('setVolume', [
    level: volumeLevel,
    useProportionalVolume: getUseProportionalVolumeSetting(),
    controlUngroupedIndividually: getControlUngroupedIndividuallySetting()
  ])
}

void groupDeviceVolumeFadeStep() {
  String deviceId = device.getDeviceNetworkId()
  Map fadeState = groupDeviceVolumeFadeState.get(deviceId)
  if(fadeState == null) { return }
  Integer currentStep = (fadeState.currentStep as Integer) + 1
  Integer totalSteps = fadeState.totalSteps as Integer
  Integer targetVolume = fadeState.targetVolume as Integer
  if(currentStep >= totalSteps) {
    setVolumeImmediate(targetVolume)
    groupDeviceVolumeFadeState.remove(deviceId)
    lastGroupDeviceVolumeFadeCallTime.remove(deviceId)
    logInfo("Volume fade complete: volume set to ${targetVolume}")
    return
  }
  Integer startVolume = fadeState.startVolume as Integer
  BigDecimal volumeStep = fadeState.volumeStep as BigDecimal
  Integer newVolume = Math.round(startVolume + (volumeStep * currentStep)) as Integer
  newVolume = Math.max(0, Math.min(100, newVolume))
  setVolumeImmediate(newVolume)
  fadeState.currentStep = currentStep
  groupDeviceVolumeFadeState.put(deviceId, fadeState)
  Integer intervalMs = fadeState.intervalMs as Integer
  runInMillis(intervalMs, 'groupDeviceVolumeFadeStep', [overwrite: true])
}

void cancelGroupDeviceVolumeFade() {
  String deviceId = device.getDeviceNetworkId()
  groupDeviceVolumeFadeState.remove(deviceId)
  unschedule('groupDeviceVolumeFadeStep')
}

/**
 * Increase volume
 */
void volumeUp() {
  requestGroupCommand('volumeUp', getGroupCommandSettings())
}

/**
 * Decrease volume
 */
void volumeDown() {
  requestGroupCommand('volumeDown', getGroupCommandSettings())
}

/**
 * Mute all players in the group
 */
void mute() {
  requestGroupCommand('mute', getGroupCommandSettings())
}

/**
 * Unmute all players in the group
 */
void unmute() {
  requestGroupCommand('unmute', getGroupCommandSettings())
}

// =============================================================================
// State Management
// =============================================================================

/**
 * Refresh volume/mute state
 * Uses Sonos group volume when speakers are grouped AND proportional volume is enabled,
 * otherwise calculates average from individual player volumes
 */
void refresh() {
  migrateLegacyRuntimeState()
  requestGroupCommand('refresh', getGroupCommandSettings())
}

/**
 * Receive a batch of attribute updates as a JSON string from the parent app.
 * Separates membership attributes (always processed) from playback attributes (guarded).
 * When the group is inactive and guarding is enabled, playback attributes are held in memory
 * and replayed when the group transitions to active.
 * @param jsonAttributes JSON string of attribute name-value pairs
 */
void updateBatchPlaybackState(String jsonAttributes) {
  processBatchPlaybackState(jsonAttributes, false)
}

/** Accept one coalesced parent batch through the normal group-state guard. */
void applyStateBatch(String jsonAttributes) {
  try { sendEvent(name: 'lastUpdated', value: now()) } catch(Exception ignored) { }
  processBatchPlaybackState(jsonAttributes, false)
}

/** Receive the app's canonical household topology and reconcile this group's membership. */
void applyAppHouseholdTopology(String topologyJson) {
  if(!topologyJson) { return }
  try {
    Map topology = (Map)parseJson(topologyJson)
    if(!(topology.groups instanceof List)) { return }
    device.updateDataValue('householdTopology', topologyJson)
    String configuredCoordinator = device.getDataValue('groupCoordinatorId')?.toString()?.trim()
    List<String> configuredPlayers = configuredCoordinator ? [configuredCoordinator] : []
    String configuredFollowerIds = device.getDataValue('playerIds')?.toString()
    configuredPlayers.addAll(normalizeGroupTopologyPlayerIds(configuredFollowerIds))
    Set<String> configuredPlayerIds = new LinkedHashSet<String>(configuredPlayers)
    String groupingMode = getGroupTopologyGroupingMode()
    Map coordinatorGroup = ((List<Map>)topology.groups).find { Map item ->
      normalizeGroupTopologyPlayerIds(item.playerIds).contains(configuredCoordinator)
    }
    List<String> actualPlayerIds = normalizeGroupTopologyPlayerIds(coordinatorGroup?.playerIds)
    Boolean coordinatorMatches = coordinatorGroup?.coordinatorId?.toString()?.trim() == configuredCoordinator
    Boolean rosterMatches = groupingMode == GROUPING_MODE_ADDITIVE
        ? actualPlayerIds.containsAll(configuredPlayerIds)
        : new HashSet<String>(actualPlayerIds) == new HashSet<String>(configuredPlayerIds)
    Boolean shouldBeActive = coordinatorGroup != null && configuredPlayerIds && coordinatorMatches && rosterMatches
    List<String> joinedNames = coordinatorGroup?.playerNames instanceof List
        ? ((List)coordinatorGroup.playerNames).collect { Object name -> name?.toString() } : []
    updateBatchPlaybackState(JsonOutput.toJson([
      switch: shouldBeActive ? 'on' : 'off',
      currentlyJoinedPlayers: joinedNames.join(', ')
    ]))
  } catch(Exception e) {
    logWarn("Could not apply app household topology snapshot: ${e.message}")
  }
}

/** Normalize player identifiers from either a topology list or a stored CSV value. */
@CompileStatic
private List<String> normalizeGroupTopologyPlayerIds(Object rawIds) {
  List<String> playerIds = new ArrayList<String>()
  if(rawIds instanceof Collection) {
    for(Object rawId : (Collection)rawIds) {
      String playerId = rawId?.toString()?.trim()
      if(playerId) { playerIds.add(playerId) }
    }
  } else if(rawIds != null) {
    String[] rawValues = rawIds.toString().split(',')
    for(String rawId : rawValues) {
      String playerId = rawId?.trim()
      if(playerId) { playerIds.add(playerId) }
    }
  }
  return new ArrayList<String>(new LinkedHashSet<String>(playerIds))
}

/** Resolve the stored grouping intent, defaulting legacy or unknown values to exact grouping. */
private String getGroupTopologyGroupingMode() {
  Object rawMode = device.getDataValue('lastGroupingMode')
  if(rawMode == null || rawMode.toString().trim().isEmpty()) {
    rawMode = device.currentValue('groupingMode', true)
  }
  return normalizeGroupTopologyGroupingMode(rawMode)
}

@CompileStatic
private String normalizeGroupTopologyGroupingMode(Object rawMode) {
  String groupingMode = rawMode?.toString()?.trim()?.toUpperCase()
  return groupingMode == GROUPING_MODE_ADDITIVE ? GROUPING_MODE_ADDITIVE : GROUPING_MODE_EXPLICIT
}

/**
 * Apply an explicit refresh from the parent app. Refresh playback is subject
 * to the same inactive-group guard as other playback updates.
 */
void applyRefreshedPlaybackState(String jsonAttributes) {
  processBatchPlaybackState(jsonAttributes, true)
}

private void processBatchPlaybackState(String jsonAttributes, Boolean forcePlaybackUpdate) {
  Map attributes = (Map)parseJson(jsonAttributes)
  if(!getEnableTrackPositionUpdates()) {
    attributes.remove('trackPosition')
    attributes.remove('trackPositionUpdated')
  }

  // Separate membership keys (always processed) from playback keys (guarded)
  Map membershipAttrs = [:]
  Map playbackAttrs = [:]
  attributes.each { String attrName, Object attrValue ->
    if(attrValue == null) { return }
    if(MEMBERSHIP_ATTRIBUTES.contains(attrName)) {
      membershipAttrs[attrName] = attrValue
    } else {
      playbackAttrs[attrName] = attrValue
    }
  }

  // Always apply membership attributes first
  Boolean wasActive = this.device.currentValue('switch') == 'on'
  queueBatchEvents(membershipAttrs)
  // Determine isActive from the batch value if switch was included, since
  // sendEvent() may not propagate to currentValue() synchronously.
  Boolean isActive = membershipAttrs.containsKey('switch')
      ? membershipAttrs['switch'] == 'on'
      : wasActive

  if(forcePlaybackUpdate) {
    if(getOnlyUpdateWhenActiveSetting() && !isActive) {
      if(getResetAttributesWhenInactiveSetting()) {
        resetPlaybackAttributes()
      }
      logDebug('Ignoring refreshed playback state because the group is inactive')
      return
    }
    applyPlaybackAttributes(playbackAttrs)
    clearHeldState()
    return
  }

  // If guard is off, always apply playback state
  if(!getOnlyUpdateWhenActiveSetting()) {
    applyPlaybackAttributes(playbackAttrs)
    clearHeldState()
    return
  }

  // If group just became inactive, reset playback attributes and hold new ones
  if(wasActive && !isActive) {
    if(getResetAttributesWhenInactiveSetting()) {
      resetPlaybackAttributes()
    }
    holdPlaybackState(playbackAttrs)
    return
  }

  // If group just became active, replay held state merged with new attributes
  if(isActive && !wasActive) {
    replayHeldState(playbackAttrs)
    return
  }

  // If group is active, apply playback attributes directly
  if(isActive) {
    applyPlaybackAttributes(playbackAttrs)
    return
  }

  // Group is not active — hold the playback attributes for later
  holdPlaybackState(playbackAttrs)
}

private void applyPlaybackAttributes(Map attrs) {
  if(!attrs) { return }
  Map<String, Object> eventBatch = [:]
  attrs.each { String attrName, Object attrValue ->
    if(!getEnableTrackPositionUpdates() && attrName in ['trackPosition', 'trackPositionUpdated']) { return }
    if(attrValue != null) {
      if(attrName == 'volume') {
        eventBatch.volume = [value: attrValue, unit: '%']
        eventBatch.groupVolume = attrValue
      } else if(attrName == 'mute') {
        eventBatch.mute = attrValue
        eventBatch.groupMute = attrValue
      } else {
        eventBatch[attrName] = attrValue
      }
    }
  }
  queueBatchEvents(eventBatch)
}

private void queueBatchEvents(Map attributes) {
  if(!attributes) { return }
  Map<String, Object> pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? (Map<String, Object>)state[PENDING_BATCH_EVENTS_KEY] : [:]
  attributes.each { Object rawName, Object rawValue ->
    String name = rawName?.toString()
    if(name && rawValue != null) {
      pending[name] = rawValue instanceof Map && ((Map)rawValue).containsKey('value')
          ? rawValue : [value: rawValue, unit: null]
    }
  }
  if(pending.isEmpty()) { return }
  state[PENDING_BATCH_EVENTS_KEY] = pending
  schedulePendingBatchEvents()
}

private void schedulePendingBatchEvents() {
  Map pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map ? (Map)state[PENDING_BATCH_EVENTS_KEY] : [:]
  if(pending.isEmpty()) {
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    state.remove(BATCH_EVENTS_DUE_AT_KEY)
    return
  }
  Long currentTime = now() as Long
  Long requestedDueAt = currentTime + BATCH_EVENTS_INTERVAL_MS
  Long existingDueAt = state[BATCH_EVENTS_DUE_AT_KEY] as Long
  Long dueAt = existingDueAt == null ? requestedDueAt : Math.min(existingDueAt, requestedDueAt)
  Integer scheduleDelay = Math.max(1L, dueAt - currentTime) as Integer
  state[BATCH_EVENTS_DUE_AT_KEY] = dueAt
  state[BATCH_EVENTS_SCHEDULED_KEY] = true
  try {
    runInMillis(scheduleDelay, 'drainBatchEvents', [overwrite: true])
  } catch(Exception e) {
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    state.remove(BATCH_EVENTS_DUE_AT_KEY)
    logWarn("Could not schedule group state event drain: ${e.message}")
  }
}

void drainBatchEvents() {
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  state.remove(BATCH_EVENTS_DUE_AT_KEY)
  Map<String, Object> pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Object>((Map<String, Object>)state[PENDING_BATCH_EVENTS_KEY]) : [:]
  if(pending.isEmpty()) {
    state.remove(PENDING_BATCH_EVENTS_KEY)
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    return
  }
  List<String> names = new ArrayList<String>(pending.keySet()).take(BATCH_EVENTS_PER_DRAIN)
  Map<String, Object> remaining = new LinkedHashMap<String, Object>((Map<String, Object>)state[PENDING_BATCH_EVENTS_KEY])
  names.each { String name ->
    Map event = pending[name] instanceof Map ? (Map)pending[name] : [value: pending[name]]
    try {
      if(event.unit) { sendEvent(name: name, value: event.value, unit: event.unit) }
      else { sendEvent(name: name, value: event.value) }
      remaining.remove(name)
    } catch(Exception e) {
      logWarn("Could not emit batched ${name} event; retaining it for retry: ${e.message}")
    }
  }
  if(remaining.isEmpty()) {
    state.remove(PENDING_BATCH_EVENTS_KEY)
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  } else {
    state[PENDING_BATCH_EVENTS_KEY] = remaining
    schedulePendingBatchEvents()
  }
}

private void holdPlaybackState(Map newAttrs) {
  if(!newAttrs || newAttrs.isEmpty()) { return }
  String dni = device.getDeviceNetworkId()
  Map<String, Object> existing = heldPlaybackState.get(dni)
  if(existing == null) {
    heldPlaybackState[dni] = new LinkedHashMap<String, Object>(newAttrs)
  } else {
    existing.putAll(newAttrs)
  }
}

/**
 * Replay any held playback attributes that were accumulated while the group was inactive.
 * Merges held state with optional additional attributes (current batch wins on collision).
 * Called internally from updateBatchPlaybackState() when a batch includes
 * switch:'on'. The held state is consumed atomically, so repeated membership
 * deliveries do not replay the same held data more than once.
 * @param additionalAttrs Optional map of new attributes to merge on top of held state
 */
void replayHeldState(Map additionalAttrs = null) {
  String dni = device.getDeviceNetworkId()
  Map<String, Object> held = heldPlaybackState.remove(dni)
  if(held == null && (additionalAttrs == null || additionalAttrs.isEmpty())) { return }

  // Merge: held state first, then current batch on top (current wins on collision)
  Map merged = [:]
  if(held) { merged.putAll(held) }
  if(additionalAttrs) { merged.putAll(additionalAttrs) }

  logDebug("Replaying ${merged.size()} held attributes after group became active")
  applyPlaybackAttributes(merged)
}

private void clearHeldState() {
  String dni = device.getDeviceNetworkId()
  heldPlaybackState.remove(dni)
}

private void resetPlaybackAttributes() {
  logDebug('Resetting playback attributes for inactive group')
  Map resetAttrs = [
    status: 'inactive',
    transportStatus: 'stopped',
    trackData: '{}',
    trackDescription: 'n/a',
    currentTrackDuration: 'n/a',
    trackPosition: 0,
    trackPositionUpdated: 0,
    currentArtistName: 'n/a',
    currentAlbumName: 'n/a',
    currentTrackName: 'n/a',
    albumArtURI: 'n/a',
    albumArtSmall: 'n/a',
    albumArtMedium: 'n/a',
    albumArtLarge: 'n/a',
    audioSource: 'n/a',
    currentFavorite: 'n/a',
    currentPlaylist: 'n/a',
    currentTrackNumber: 0,
    nextArtistName: 'n/a',
    nextAlbumName: 'n/a',
    nextTrackName: 'n/a',
    nextTrackAlbumArtURI: 'n/a',
    queueTrackTotal: '0',
    queueTrackPosition: '0',
    groupVolume: 0,
    groupMute: 'unmuted',
    currentRepeatOneMode: 'off',
    currentRepeatAllMode: 'off',
    currentCrossfadeMode: 'off',
    currentShuffleMode: 'off'
  ]
  applyPlaybackAttributes(resetAttrs)
}

/**
 * Retained as a local compatibility hook for callers that explicitly invoke
 * it when this group device transitions from active to inactive.
 * Resets playback attributes to a clean "inactive" state if configured to do so.
 * Only resets when both resetAttributesWhenInactive and onlyUpdateWhenActive are enabled,
 * because when the guard is off, playback attributes flow through unconditionally and
 * resetting would cause a brief flash of empty data immediately overwritten.
 */
void onGroupDeactivated() {
  if(getResetAttributesWhenInactiveSetting() && getOnlyUpdateWhenActiveSetting()) {
    resetPlaybackAttributes()
  }
}

/**
 * Calculate and update volume as average of all player volumes
 */
void refreshAverageVolume() {
  requestGroupCommand('refresh', getGroupCommandSettings())
}

// =============================================================================
// MusicPlayer Capability Implementation
// =============================================================================

/**
 * Play - forwards to coordinator
 */
void play() {
  requestGroupCommand('play')
}

/**
 * Pause - forwards to coordinator
 */
void pause() {
  requestGroupCommand('pause')
}

/**
 * Stop - forwards to coordinator
 */
void stop() {
  requestGroupCommand('stop')
}

/**
 * Next track - forwards to coordinator
 */
void nextTrack() {
  requestGroupCommand('nextTrack')
}

/**
 * Previous track - forwards to coordinator
 */
void previousTrack() {
  requestGroupCommand('previousTrack')
}

/**
 * Set level (same as setVolume for MusicPlayer compatibility)
 * Supports optional duration parameter for volume fade (seconds)
 */
void setLevel(BigDecimal level, BigDecimal duration = null) {
  setVolume(level, duration)
}

/**
 * Set the group volume to zero without requiring a numeric command argument.
 * Hubitat's built-in AudioVolume control may reject zero in its UI even though
 * the capability range is 0-100.
 */
void setVolumeZero() {
  setVolume(0G)
}

/**
 * Play track - forwards to coordinator
 */
void playTrack(String uri, Object volume = null) {
  if(!uri) {
    logWarn('No URI provided to play')
    return
  }
  requestGroupCommand('playTrack', [uri: uri,
    volume: normalizeLegacyOptionalPlaybackVolume(volume)?.toString()])
}

/**
 * Set track - forwards to coordinator
 */
void setTrack(String uri) {
  if(!uri) {
    logWarn('No URI provided to set')
    return
  }
  requestGroupCommand('setTrack', [uri: uri])
}

// =============================================================================
// Playback Control Commands
// =============================================================================

/**
 * Set repeat mode
 */
void setRepeatMode(String mode) {
  requestGroupCommand('setRepeatMode', [mode: mode])
}

/**
 * Repeat one track
 */
void repeatOne() {
  requestGroupCommand('repeatOne')
}

/**
 * Repeat all tracks
 */
void repeatAll() {
  requestGroupCommand('repeatAll')
}

/**
 * Disable repeat
 */
void repeatNone() {
  requestGroupCommand('repeatNone')
}

/**
 * Set shuffle mode
 */
void setShuffle(String mode) {
  requestGroupCommand('setShuffle', [mode: mode])
}

/**
 * Enable shuffle
 */
void shuffleOn() {
  requestGroupCommand('shuffleOn')
}

/**
 * Disable shuffle
 */
void shuffleOff() {
  requestGroupCommand('shuffleOff')
}

/**
 * Set crossfade mode
 */
void setCrossfade(String mode) {
  requestGroupCommand('setCrossfade', [mode: mode])
}

/**
 * Enable crossfade
 */
void enableCrossfade() {
  requestGroupCommand('enableCrossfade')
}

/**
 * Disable crossfade
 */
void disableCrossfade() {
  requestGroupCommand('disableCrossfade')
}

/**
 * Get list of favorites
 */
void getFavorites() {
  requestGroupCommand('getFavorites')
}

/**
 * Load a favorite by ID
 */
void loadFavorite(String favoriteId = null) {
  requestGroupCommand('loadFavorite', [favoriteId: favoriteId])
}

/**
 * Load a favorite with full options
 */
void loadFavoriteFull(String favoriteId, String playMode = 'NORMAL', BigDecimal startTrack = 1, BigDecimal startTime = 0) {
  if(!favoriteId) {
    logWarn('No favorite ID provided')
    return
  }
  if((startTrack != null && startTrack.compareTo(BigDecimal.ONE) != 0) || (startTime != null && startTime.compareTo(BigDecimal.ZERO) != 0)) {
    logWarn("Group favorite load ignores startTrack/startTime; using play mode '${playMode}' only")
  }
  Map options = getFavoriteLoadOptionsFromPlayMode(playMode)
  requestGroupCommand('loadFavoriteFull', [
    favoriteId: favoriteId,
    repeatMode: options.repeatMode as String,
    queueMode: options.queueMode as String,
    shuffleMode: options.shuffleMode as String,
    autoPlay: options.autoPlay as String,
    crossfadeMode: options.crossfadeMode as String
  ])
}

/**
 * Get playlists
 */
void getPlaylists() {
  requestGroupCommand('getPlaylists')
}

/**
 * Load a playlist by ID
 */
void loadPlaylist(String playlistId = null) {
  requestGroupCommand('loadPlaylist', [playlistId: playlistId])
}

/**
 * Load a playlist with full options
 */
void loadPlaylistFull(String playlistId, String repeatMode = 'repeat all', String queueMode = 'replace', String shuffleMode = 'off', String autoPlay = 'true', String crossfadeMode = 'on') {
  if(!playlistId) {
    logWarn('No playlist ID provided')
    return
  }
  requestGroupCommand('loadPlaylistFull', [
    playlistId: playlistId,
    repeatMode: repeatMode,
    queueMode: queueMode,
    shuffleMode: shuffleMode,
    autoPlay: autoPlay,
    crossfadeMode: crossfadeMode
  ])
}
