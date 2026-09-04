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
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

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

@Field static ConcurrentHashMap<String, Map> groupDeviceVolumeFadeState = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, Long> lastGroupDeviceVolumeFadeCallTime = new ConcurrentHashMap<String, Long>()
@Field static ConcurrentHashMap<String, Map<String, Object>> heldPlaybackState = new ConcurrentHashMap<String, Map<String, Object>>()
@Field static final Set<String> MEMBERSHIP_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>(['switch', 'currentlyJoinedPlayers']))
@Field static volatile List<String> cachedTTSVoiceNames = null
@Field static volatile String cachedTTSDefaultVoice = null
@Field static final String GROUP_COMMAND_REQUEST_ATTRIBUTE = 'groupCommandRequest'

metadata {
  definition(
    name: 'Sonos Advanced Group',
    version: '0.11.7',
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
    capability 'AudioVolume'
    capability 'SpeechSynthesis'
    capability 'Actuator'
    capability 'Switch'
    capability 'SwitchLevel'

    command 'groupPlayers'
    command 'joinPlayersToCoordinator'
    command 'removePlayersFromCoordinator'
    command 'ungroupPlayers'
    command 'evictUnlistedPlayers'
    command 'refresh'

    command 'playHighPriorityTTS', [
      [name:'Text*', type:"STRING", description:"Text to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]],
      [name: 'Voice name', type: "ENUM", constraints: getCachedTTSVoiceNames(), defaultValue: getCurrentTTSVoice()]
    ]

    command 'playHighPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]]
    ]

    command 'enqueueLowPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]]
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
      [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off' ]],
      [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
      [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on']],
      [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
      [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
    ]

    attribute 'coordinatorActive', 'string'
    attribute 'followers', 'string'
    attribute 'currentlyJoinedPlayers', 'string'
    // Internal asynchronous command bridge to the parent app. Group commands
    // publish requests instead of synchronously calling parent child lookups.
    attribute 'groupCommandRequest', 'string'

    // Extended playback attributes forwarded from coordinator
    attribute 'currentTrackDuration', 'string'
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
  }
  preferences {
    section('Device Settings') {
      input 'chimeBeforeTTS', 'bool', title: 'Play chime before standard priority TTS messages', required: false, defaultValue: false
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
      input 'onlyUpdateWhenActive', 'bool',
        title: 'Only update state when group is active',
        description: 'When enabled, playback state updates are skipped when the group\'s configured speakers are not all grouped together in Sonos. Reduces hub load when many groups share a coordinator.',
        required: false, defaultValue: true
      input 'resetAttributesWhenInactive', 'bool',
        title: 'Reset attributes when group becomes inactive',
        description: 'When enabled, playback attributes (track name, artist, album art, etc.) are cleared when the group\'s speakers are no longer grouped together. Shows a clean state on dashboards.',
        required: false, defaultValue: true
    }
  }
}
Boolean getChimeBeforeTTSSetting() { return settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false }
Boolean getControlUngroupedIndividuallySetting() { return settings.controlUngroupedIndividually != null ? settings.controlUngroupedIndividually : false }
Boolean getUseProportionalVolumeSetting() { return settings.useProportionalVolume != null ? settings.useProportionalVolume : true }
Boolean getOnlyUpdateWhenActiveSetting() { return settings.onlyUpdateWhenActive != null ? settings.onlyUpdateWhenActive : true }
Boolean getResetAttributesWhenInactiveSetting() { return settings.resetAttributesWhenInactive != null ? settings.resetAttributesWhenInactive : true }

Map getGroupCommandSettings() {
  return [
    useProportionalVolume: getUseProportionalVolumeSetting(),
    controlUngroupedIndividually: getControlUngroupedIndividuallySetting()
  ]
}

/**
 * Publish a group command for the parent app to process after this driver
 * invocation returns. Keeping the bridge event-based prevents a group command
 * from holding the group driver's platform method slot while it waits for the
 * parent app to look up and invoke another child device.
 */
void requestGroupCommand(String command, Map args = [:]) {
  if(!command) {
    return
  }
  Integer sequence = ((state.groupCommandSequence ?: 0) as Integer) + 1
  state.groupCommandSequence = sequence
  Map payload = [
    groupDni: device.getDeviceNetworkId(),
    requestId: "${device.getDeviceNetworkId()}-${sequence}",
    command: command,
    args: args ?: [:]
  ]
  runIn(1, 'emitGroupCommandRequest', [data: [payload: payload]])
}

void emitGroupCommandRequest(Map data) {
  Map payload = data?.payload instanceof Map ? (Map)data.payload : null
  if(payload?.command) {
    sendEvent(name: GROUP_COMMAND_REQUEST_ATTRIBUTE, value: JsonOutput.toJson(payload), isStateChange: true)
  }
}

private void sendVolumeStateEvents(Integer volumeLevel) {
  sendEvent(name: 'volume', value: volumeLevel, unit: '%')
  sendEvent(name: 'groupVolume', value: volumeLevel)
}

private void sendMuteStateEvents(String muteState) {
  sendEvent(name: 'mute', value: muteState)
  sendEvent(name: 'groupMute', value: muteState)
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
  logTrace("TTS voice cache updated: ${voiceNames?.size()} voices, default: ${defaultVoice}")
}


void initialize() {
  if(settings.chimeBeforeTTS == null) { settings.chimeBeforeTTS = false }
  if(settings.onlyUpdateWhenActive == null) { settings.onlyUpdateWhenActive = true }
  if(settings.resetAttributesWhenInactive == null) { settings.resetAttributesWhenInactive = true }
  // Initialize volume/mute state from coordinator
  runIn(5, 'refresh')
}
void configure() {
  Boolean wasGuarded = state.lastOnlyUpdateWhenActive != false
  Boolean isGuarded = getOnlyUpdateWhenActiveSetting()
  state.lastOnlyUpdateWhenActive = isGuarded
  if(!isGuarded && wasGuarded) {
    // Guard just disabled — clear held state and refresh to populate skipped attributes
    clearHeldState()
    runIn(2, 'refresh')
  }
}
void on() { requestGroupCommand('on', getGroupCommandSettings()) }
void off() { requestGroupCommand('off', getGroupCommandSettings()) }
void setState(String stateName, String stateValue) { state[stateName] = stateValue }
void clearState() { state.clear() }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
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
    requestGroupCommand('playAudioClip', [uri: ttsMap.uri as String, volume: volume?.toString()])
  } catch (Exception e) {
    logError("Error playing text: ${e.message}")
  }
}

void playHighPriorityTTS(String text, BigDecimal volume = null, String voice = null) {
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
    requestGroupCommand('playHighPriorityTrack', [uri: ttsMap.uri as String, volume: volume?.toString()])
  } catch (Exception e) {
    logError("Error playing high priority TTS: ${e.message}")
  }
}

void playHighPriorityTrack(String uri, BigDecimal volume = null) {
  if(!uri) {
    logWarn('No URI provided to play')
    return
  }
  requestGroupCommand('playHighPriorityTrack', [uri: uri, volume: volume?.toString()])
}

void enqueueLowPriorityTrack(String uri, BigDecimal volume = null) {
  if(!uri) {
    logWarn('No URI provided to enqueue')
    return
  }
  requestGroupCommand('enqueueLowPriorityTrack', [uri: uri, volume: volume?.toString()])
}

void joinPlayersToCoordinator() {
  List<String> followers = getAllFollowersInGroupDevice()
  if(!followers) {
    logWarn('No followers found to join to coordinator')
    return
  }
  requestGroupCommand('joinPlayersToCoordinator')
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
  String currentSwitch = device.currentValue('switch')
  if(currentSwitch != 'on') {
    // Group is already inactive — no need to ungroup first, just create the group directly
    requestGroupCommand('createGroup')
    return
  }

  // Group is active — ungroup first to ensure the new coordinator is set correctly.
  // The regroup will be triggered by onGroupDeactivated() when the WebSocket event confirms the ungroup.
  state.pendingRegroup = true
  requestGroupCommand('ungroupPlayers')
  // Safety timeout in case the WebSocket event never arrives
  runIn(10, 'regroupSafetyTimeout', [overwrite: true])
}

void createGroupAfterUngroup() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  if(!allPlayers) {
    logWarn('Cannot create group after ungroup - no players found')
    return
  }
  requestGroupCommand('regroupAfterUngroup')
}

void regroupSafetyTimeout() {
  if(!state.pendingRegroup) { return }
  state.remove('pendingRegroup')
  logWarn('Regroup safety timeout reached — WebSocket group event did not arrive within 10 seconds. Attempting regroup anyway.')
  createGroupAfterUngroup()
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
  requestGroupCommand('evictUnlistedPlayers')
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
  if(delta == 0) { return }
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
  Map attributes = (Map)parseJson(jsonAttributes)

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
  membershipAttrs.each { String attrName, Object attrValue ->
    sendEvent(name: attrName, value: attrValue)
  }
  // Determine isActive from the batch value if switch was included, since
  // sendEvent() may not propagate to currentValue() synchronously.
  Boolean isActive = membershipAttrs.containsKey('switch')
      ? membershipAttrs['switch'] == 'on'
      : wasActive

  // If guard is off, always apply playback state
  if(!getOnlyUpdateWhenActiveSetting()) {
    applyPlaybackAttributes(playbackAttrs)
    clearHeldState()
    return
  }

  // If group just became inactive, reset playback attributes and hold new ones
  if(wasActive && !isActive) {
    handleLocalGroupDeactivation()
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
  attrs.each { String attrName, Object attrValue ->
    if(attrValue != null) {
      if(attrName == 'volume') {
        sendVolumeStateEvents(attrValue as Integer)
      } else if(attrName == 'mute') {
        sendMuteStateEvents(attrValue as String)
      } else {
        sendEvent(name: attrName, value: attrValue)
      }
    }
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

private void handleLocalGroupDeactivation() {
  if(state.pendingRegroup) {
    state.remove('pendingRegroup')
    unschedule('regroupSafetyTimeout')
    createGroupAfterUngroup()
  }
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
  handleLocalGroupDeactivation()
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
 * Play track - forwards to coordinator
 */
void playTrack(String uri, BigDecimal volume = null) {
  if(!uri) {
    logWarn('No URI provided to play')
    return
  }
  requestGroupCommand('playTrack', [uri: uri, volume: volume?.toString()])
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
void loadFavorite(String favoriteId) {
  if(!favoriteId) {
    logWarn('No favorite ID provided')
    return
  }
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
void loadPlaylist(String playlistId) {
  if(!playlistId) {
    logWarn('No playlist ID provided')
    return
  }
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
