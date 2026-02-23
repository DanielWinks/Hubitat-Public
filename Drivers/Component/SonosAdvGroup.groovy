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

#include dwinks.UtilitiesAndLoggingLibrary

// Delay in milliseconds to wait after ungrouping before regrouping players
// This ensures the Sonos API has time to process the ungroup operation before creating a new group
@Field static final Integer UNGROUP_DELAY_MS = 2000
@Field static ConcurrentHashMap<String, Map> groupDeviceVolumeFadeState = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, Map<String, Object>> heldPlaybackState = new ConcurrentHashMap<String, Map<String, Object>>()
@Field static final Set<String> MEMBERSHIP_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>(['switch', 'currentlyJoinedPlayers']))
@Field static volatile List<String> cachedTTSVoiceNames = null
@Field static volatile String cachedTTSDefaultVoice = null

metadata {
  definition(
    name: 'Sonos Advanced Group',
    version: '0.10.10',
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
void on() { evictUnlistedPlayers() }
void off() { removePlayersFromCoordinator() }
void setState(String stateName, String stateValue) { state[stateName] = stateValue }
void clearState() { state.clear() }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  if(!text) {
    logWarn('No text provided to play')
    return
  }
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  if(!allDevs) {
    logWarn('No player devices found in group')
    return
  }
  logDebug("All Devices In Group: ${allDevs}")
  try {
    def ttsMap = textToSpeech(text, voice)
    if(!ttsMap || !ttsMap.uri) {
      logWarn('Failed to generate TTS URI')
      return
    }
    allDevs.each{it.playerLoadAudioClip(ttsMap.uri, volume)}
  } catch (Exception e) {
    logError("Error playing text: ${e.message}")
  }
}

void playHighPriorityTTS(String text, BigDecimal volume = null, String voice = null) {
  if(!text) {
    logWarn('No text provided to play')
    return
  }
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  if(!allDevs) {
    logWarn('No player devices found in group')
    return
  }
  try {
    def ttsMap = textToSpeech(text, voice)
    if(!ttsMap || !ttsMap.uri) {
      logWarn('Failed to generate TTS URI')
      return
    }
    allDevs.each{it.playerLoadAudioClipHighPriority(ttsMap.uri, volume)}
  } catch (Exception e) {
    logError("Error playing high priority TTS: ${e.message}")
  }
}

void playHighPriorityTrack(String uri, BigDecimal volume = null) {
  if(!uri) {
    logWarn('No URI provided to play')
    return
  }
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  if(!allDevs) {
    logWarn('No player devices found in group')
    return
  }
  allDevs.each{it.playerLoadAudioClipHighPriority(uri, volume)}
}

void enqueueLowPriorityTrack(String uri, BigDecimal volume = null) {
  if(!uri) {
    logWarn('No URI provided to enqueue')
    return
  }
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  if(!allDevs) {
    logWarn('No player devices found in group')
    return
  }
  allDevs.each{it.playerLoadAudioClip(uri, volume)}
}

void joinPlayersToCoordinator() {
  List<String> followers = getAllFollowersInGroupDevice()
  if(!followers) {
    logWarn('No followers found to join to coordinator')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found')
    return
  }
  coordinator.playerModifyGroupMembers(followers)
}

void removePlayersFromCoordinator() {
  List<String> allFollowerIds = getAllFollowersInGroupDevice()
  if(!allFollowerIds) {
    logDebug('No followers to remove from coordinator')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found, cannot remove followers')
    return
  }
  // Single atomic API call to remove all followers at once.
  // This avoids N intermediate group topology changes that cause latency
  // and can trigger unexpected playback on the coordinator.
  coordinator.playerModifyGroupMembers([], allFollowerIds)
}

void groupPlayers() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  if(!allPlayers) {
    logWarn('No players found to group')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found')
    return
  }
  // Ungroup all players first to ensure the new coordinator is set correctly
  // This prevents the Sonos API from keeping an existing coordinator
  ungroupPlayers()
  pauseExecution(UNGROUP_DELAY_MS)
  coordinator.playerCreateGroup(allPlayers)
}

void ungroupPlayers() {
  List<String> allFollowerIds = getAllFollowersInGroupDevice()
  if(!allFollowerIds) {
    logDebug('No followers to ungroup')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logDebug('No coordinator found to ungroup')
    return
  }
  // Single atomic call to remove all followers from the coordinator's group
  coordinator.playerModifyGroupMembers([], allFollowerIds)
}

void evictUnlistedPlayers() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  if(!allPlayers) {
    logWarn('No players found to manage')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found')
    return
  }
  coordinator.playerCreateGroup(allPlayers)
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

List<DeviceWrapper> getAllPlayerDevicesInGroupDevice() {
  List<String> players = getAllPlayersInGroupDevice()
  return parent?.getDevicesFromRincons(players)
}

List<DeviceWrapper> getAllFollowerDevicesInGroupDevice() {
  List<String> players = getAllFollowersInGroupDevice()
  return parent?.getDevicesFromRincons(players)
}

DeviceWrapper getCoordinatorDevice() {
  return parent?.getDeviceFromRincon(getCoordinatorId())
}

// =============================================================================
// AudioVolume Capability Implementation
// =============================================================================

/**
 * Check if the speakers are actually grouped in Sonos with this coordinator.
 * This is different from being members of this "group device" - the speakers
 * must be actively grouped in Sonos for native group volume commands to work.
 */
Boolean isActuallySonosGrouped() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) { return false }
  // Note: isGroupCoordinator attribute is an ENUM with values 'on'/'off', not boolean
  Boolean isGrouped = coordinator.currentValue('isGrouped', true) == 'on'
  Boolean isCoordinator = coordinator.currentValue('isGroupCoordinator', true) == 'on'
  logDebug("isActuallySonosGrouped() - isGrouped: ${isGrouped}, isGroupCoordinator: ${isCoordinator}")
  return isGrouped && isCoordinator
}

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
  Integer targetVolume = Math.max(0, Math.min(100, level.intValue()))

  // If no fade duration, use immediate volume set
  if(duration == null || duration <= 0) {
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
  String deviceId = device.getDeviceNetworkId()
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
  runInMillis(intervalMs, 'groupDeviceVolumeFadeStep')
}

/**
 * Internal: set volume immediately without fade (original setVolume logic)
 */
private void setVolumeImmediate(Integer volumeLevel) {
  DeviceWrapper coordinator = getCoordinatorDevice()
  Boolean isGrouped = isActuallySonosGrouped()
  logDebug("setVolumeImmediate(${volumeLevel}) - isGrouped: ${isGrouped}, useProportional: ${getUseProportionalVolumeSetting()}, controlIndividually: ${getControlUngroupedIndividuallySetting()}")

  if(isGrouped) {
    if(getUseProportionalVolumeSetting()) {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot set group volume')
        return
      }
      logDebug("Using Sonos group volume API (proportional)")
      coordinator.setGroupVolume(volumeLevel)
    } else {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot set volume')
        return
      }
      logDebug("Setting volume on each speaker directly (grouped, non-proportional)")
      allDevs.each { it.setVolume(volumeLevel) }
      sendEvent(name: 'volume', value: volumeLevel, unit: '%')
    }
  } else {
    if(getControlUngroupedIndividuallySetting()) {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot set volume')
        return
      }
      logDebug("Setting volume on each speaker individually (ungrouped)")
      allDevs.each { it.setVolume(volumeLevel) }
      sendEvent(name: 'volume', value: volumeLevel, unit: '%')
    } else {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot set volume')
        return
      }
      logDebug("Setting volume on coordinator only (ungrouped)")
      coordinator.setVolume(volumeLevel)
      logDebug("Sending volume event: ${volumeLevel}")
      sendEvent(name: 'volume', value: volumeLevel, unit: '%')
    }
  }
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
  runInMillis(intervalMs, 'groupDeviceVolumeFadeStep')
}

void cancelGroupDeviceVolumeFade() {
  String deviceId = device.getDeviceNetworkId()
  if(groupDeviceVolumeFadeState.containsKey(deviceId)) {
    groupDeviceVolumeFadeState.remove(deviceId)
    unschedule('groupDeviceVolumeFadeStep')
    logDebug('Cancelled in-progress volume fade')
  }
}

/**
 * Increase volume
 */
void volumeUp() {
  DeviceWrapper coordinator = getCoordinatorDevice()

  if(isActuallySonosGrouped()) {
    if(getUseProportionalVolumeSetting()) {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot increase volume')
        return
      }
      coordinator.groupVolumeUp()
    } else {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot increase volume')
        return
      }
      allDevs.each { it.volumeUp() }
      runIn(1, 'refreshAverageVolume', [overwrite: true])
    }
  } else {
    if(getControlUngroupedIndividuallySetting()) {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot increase volume')
        return
      }
      allDevs.each { it.volumeUp() }
      runIn(1, 'refreshAverageVolume', [overwrite: true])
    } else {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot increase volume')
        return
      }
      coordinator.volumeUp()
      runIn(1, 'refresh', [overwrite: true])
    }
  }
}

/**
 * Decrease volume
 */
void volumeDown() {
  DeviceWrapper coordinator = getCoordinatorDevice()

  if(isActuallySonosGrouped()) {
    if(getUseProportionalVolumeSetting()) {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot decrease volume')
        return
      }
      coordinator.groupVolumeDown()
    } else {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot decrease volume')
        return
      }
      allDevs.each { it.volumeDown() }
      runIn(1, 'refreshAverageVolume', [overwrite: true])
    }
  } else {
    if(getControlUngroupedIndividuallySetting()) {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot decrease volume')
        return
      }
      allDevs.each { it.volumeDown() }
      runIn(1, 'refreshAverageVolume', [overwrite: true])
    } else {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot decrease volume')
        return
      }
      coordinator.volumeDown()
      runIn(1, 'refresh', [overwrite: true])
    }
  }
}

/**
 * Mute all players in the group
 */
void mute() {
  DeviceWrapper coordinator = getCoordinatorDevice()

  if(isActuallySonosGrouped()) {
    if(getUseProportionalVolumeSetting()) {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot mute')
        return
      }
      coordinator.muteGroup()
    } else {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot mute')
        return
      }
      allDevs.each { it.mute() }
      sendEvent(name: 'mute', value: 'muted')
    }
  } else {
    if(getControlUngroupedIndividuallySetting()) {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot mute')
        return
      }
      allDevs.each { it.mute() }
      sendEvent(name: 'mute', value: 'muted')
    } else {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot mute')
        return
      }
      coordinator.mute()
      sendEvent(name: 'mute', value: 'muted')
    }
  }
}

/**
 * Unmute all players in the group
 */
void unmute() {
  DeviceWrapper coordinator = getCoordinatorDevice()

  if(isActuallySonosGrouped()) {
    if(getUseProportionalVolumeSetting()) {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot unmute')
        return
      }
      coordinator.unmuteGroup()
    } else {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot unmute')
        return
      }
      allDevs.each { it.unmute() }
      sendEvent(name: 'mute', value: 'unmuted')
    }
  } else {
    if(getControlUngroupedIndividuallySetting()) {
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot unmute')
        return
      }
      allDevs.each { it.unmute() }
      sendEvent(name: 'mute', value: 'unmuted')
    } else {
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot unmute')
        return
      }
      coordinator.unmute()
      sendEvent(name: 'mute', value: 'unmuted')
    }
  }
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
  Boolean isGrouped = isActuallySonosGrouped()
  Boolean useProportional = getUseProportionalVolumeSetting()
  logDebug("refresh() - isGrouped: ${isGrouped}, useProportional: ${useProportional}")

  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logDebug('Coordinator not found for state refresh')
    return
  }

  if(isGrouped && useProportional) {
    // Use Sonos group volume (which is already an average)
    Integer volume = coordinator.currentValue('groupVolume', true) as Integer
    String muteState = coordinator.currentValue('groupMute', true)
    logDebug("refresh() - Reading from coordinator: groupVolume=${volume}, groupMute=${muteState}")

    if(volume != null) {
      sendEvent(name: 'volume', value: volume, unit: '%')
      sendEvent(name: 'groupVolume', value: volume)
    }
    if(muteState != null) {
      sendEvent(name: 'mute', value: muteState)
      sendEvent(name: 'groupMute', value: muteState)
    }
  } else {
    // Calculate average from individual players
    refreshAverageVolume()
  }

  // Refresh MusicPlayer attributes from coordinator
  String status = coordinator.currentValue('status', true)
  String trackData = coordinator.currentValue('trackData', true)
  String trackDescription = coordinator.currentValue('trackDescription', true)

  if(status != null) {
    sendEvent(name: 'status', value: status)
  }
  String transportStatus = coordinator.currentValue('transportStatus', true)
  if(transportStatus != null) {
    sendEvent(name: 'transportStatus', value: transportStatus)
  }
  if(trackData != null) {
    sendEvent(name: 'trackData', value: trackData)
  }
  if(trackDescription != null) {
    sendEvent(name: 'trackDescription', value: trackDescription)
  }

  // Refresh extended playback attributes from coordinator
  String currentTrackDuration = coordinator.currentValue('currentTrackDuration', true)
  String currentArtistName = coordinator.currentValue('currentArtistName', true)
  String albumArtURI = coordinator.currentValue('albumArtURI', true)
  String albumArtSmall = coordinator.currentValue('albumArtSmall', true)
  String albumArtMedium = coordinator.currentValue('albumArtMedium', true)
  String albumArtLarge = coordinator.currentValue('albumArtLarge', true)
  String audioSource = coordinator.currentValue('audioSource', true)
  String currentAlbumName = coordinator.currentValue('currentAlbumName', true)
  String currentTrackName = coordinator.currentValue('currentTrackName', true)
  String currentFavorite = coordinator.currentValue('currentFavorite', true)
  String currentPlaylist = coordinator.currentValue('currentPlaylist', true)
  Integer currentTrackNumber = coordinator.currentValue('currentTrackNumber', true) as Integer
  String nextArtistName = coordinator.currentValue('nextArtistName', true)
  String nextAlbumName = coordinator.currentValue('nextAlbumName', true)
  String nextTrackName = coordinator.currentValue('nextTrackName', true)
  String nextTrackAlbumArtURI = coordinator.currentValue('nextTrackAlbumArtURI', true)
  String queueTrackTotal = coordinator.currentValue('queueTrackTotal', true)
  String queueTrackPosition = coordinator.currentValue('queueTrackPosition', true)
  String currentRepeatOneMode = coordinator.currentValue('currentRepeatOneMode', true)
  String currentRepeatAllMode = coordinator.currentValue('currentRepeatAllMode', true)
  String currentCrossfadeMode = coordinator.currentValue('currentCrossfadeMode', true)
  String currentShuffleMode = coordinator.currentValue('currentShuffleMode', true)

  if(currentTrackDuration != null) sendEvent(name: 'currentTrackDuration', value: currentTrackDuration)
  if(currentArtistName != null) sendEvent(name: 'currentArtistName', value: currentArtistName)
  if(albumArtURI != null) sendEvent(name: 'albumArtURI', value: albumArtURI)
  if(albumArtSmall != null) sendEvent(name: 'albumArtSmall', value: albumArtSmall)
  if(albumArtMedium != null) sendEvent(name: 'albumArtMedium', value: albumArtMedium)
  if(albumArtLarge != null) sendEvent(name: 'albumArtLarge', value: albumArtLarge)
  if(audioSource != null) sendEvent(name: 'audioSource', value: audioSource)
  if(currentAlbumName != null) sendEvent(name: 'currentAlbumName', value: currentAlbumName)
  if(currentTrackName != null) sendEvent(name: 'currentTrackName', value: currentTrackName)
  if(currentFavorite != null) sendEvent(name: 'currentFavorite', value: currentFavorite)
  if(currentPlaylist != null) sendEvent(name: 'currentPlaylist', value: currentPlaylist)
  if(currentTrackNumber != null) sendEvent(name: 'currentTrackNumber', value: currentTrackNumber)
  if(nextArtistName != null) sendEvent(name: 'nextArtistName', value: nextArtistName)
  if(nextAlbumName != null) sendEvent(name: 'nextAlbumName', value: nextAlbumName)
  if(nextTrackName != null) sendEvent(name: 'nextTrackName', value: nextTrackName)
  if(nextTrackAlbumArtURI != null) sendEvent(name: 'nextTrackAlbumArtURI', value: nextTrackAlbumArtURI)
  if(queueTrackTotal != null) sendEvent(name: 'queueTrackTotal', value: queueTrackTotal)
  if(queueTrackPosition != null) sendEvent(name: 'queueTrackPosition', value: queueTrackPosition)
  if(currentRepeatOneMode != null) sendEvent(name: 'currentRepeatOneMode', value: currentRepeatOneMode)
  if(currentRepeatAllMode != null) sendEvent(name: 'currentRepeatAllMode', value: currentRepeatAllMode)
  if(currentCrossfadeMode != null) sendEvent(name: 'currentCrossfadeMode', value: currentCrossfadeMode)
  if(currentShuffleMode != null) sendEvent(name: 'currentShuffleMode', value: currentShuffleMode)

  // Refresh currently joined players from coordinator's group member names
  String groupMemberNames = coordinator.currentValue('groupMemberNames', true)
  if(groupMemberNames != null) {
    // groupMemberNames is stored as a list toString(), e.g. "[Kitchen Player, Office Player]"
    // Clean it up to a comma-separated string
    String cleaned = groupMemberNames.replaceAll(/^\[|\]$/, '').trim()
    sendEvent(name: 'currentlyJoinedPlayers', value: cleaned)
  }
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
        sendEvent(name: 'volume', value: attrValue, unit: '%')
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
 * Called from the app via notifyGroupDeviceActivated() on off→on transition, and also
 * internally from updateBatchPlaybackState() when a batch includes switch:'on'.
 * Both paths are safe: the first caller consumes held state via atomic remove(),
 * subsequent calls find null and replay only additionalAttrs (or return early).
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

/**
 * Called by the app when this group device transitions from active to inactive.
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
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  if(!allDevs || allDevs.size() == 0) {
    logDebug('No player devices found for average volume calculation')
    return
  }

  // Calculate average volume
  Integer totalVolume = 0
  Integer count = 0
  Boolean anyMuted = false
  Boolean allMuted = true

  allDevs.each { dev ->
    Integer vol = dev.currentValue('volume', true) as Integer
    if(vol != null) {
      totalVolume += vol
      count++
    }
    String muteState = dev.currentValue('mute', true)
    if(muteState == 'muted') {
      anyMuted = true
    } else {
      allMuted = false
    }
  }

  if(count > 0) {
    Integer avgVolume = Math.round(totalVolume / count) as Integer
    sendEvent(name: 'volume', value: avgVolume, unit: '%')
  }

  // Mute state: muted if all players are muted
  sendEvent(name: 'mute', value: allMuted ? 'muted' : 'unmuted')
}

// =============================================================================
// MusicPlayer Capability Implementation
// =============================================================================

/**
 * Play - forwards to coordinator
 */
void play() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot play')
    return
  }
  coordinator.play()
}

/**
 * Pause - forwards to coordinator
 */
void pause() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot pause')
    return
  }
  coordinator.pause()
}

/**
 * Stop - forwards to coordinator
 */
void stop() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot stop')
    return
  }
  coordinator.stop()
}

/**
 * Next track - forwards to coordinator
 */
void nextTrack() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot skip track')
    return
  }
  coordinator.nextTrack()
}

/**
 * Previous track - forwards to coordinator
 */
void previousTrack() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot go to previous track')
    return
  }
  coordinator.previousTrack()
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
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot play track')
    return
  }
  coordinator.playTrack(uri, volume)
}

/**
 * Set track - forwards to coordinator
 */
void setTrack(String uri) {
  if(!uri) {
    logWarn('No URI provided to set')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot set track')
    return
  }
  coordinator.setTrack(uri)
}

// =============================================================================
// Playback Control Commands
// =============================================================================

/**
 * Set repeat mode
 */
void setRepeatMode(String mode) {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot set repeat mode')
    return
  }
  coordinator.setRepeatMode(mode)
}

/**
 * Repeat one track
 */
void repeatOne() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot set repeat one')
    return
  }
  coordinator.repeatOne()
}

/**
 * Repeat all tracks
 */
void repeatAll() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot set repeat all')
    return
  }
  coordinator.repeatAll()
}

/**
 * Disable repeat
 */
void repeatNone() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot disable repeat')
    return
  }
  coordinator.repeatNone()
}

/**
 * Set shuffle mode
 */
void setShuffle(String mode) {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot set shuffle mode')
    return
  }
  coordinator.setShuffle(mode)
}

/**
 * Enable shuffle
 */
void shuffleOn() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot enable shuffle')
    return
  }
  coordinator.shuffleOn()
}

/**
 * Disable shuffle
 */
void shuffleOff() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot disable shuffle')
    return
  }
  coordinator.shuffleOff()
}

/**
 * Set crossfade mode
 */
void setCrossfade(String mode) {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot set crossfade mode')
    return
  }
  coordinator.setCrossfade(mode)
}

/**
 * Enable crossfade
 */
void enableCrossfade() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot enable crossfade')
    return
  }
  coordinator.enableCrossfade()
}

/**
 * Disable crossfade
 */
void disableCrossfade() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot disable crossfade')
    return
  }
  coordinator.disableCrossfade()
}

/**
 * Get list of favorites
 */
void getFavorites() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot get favorites')
    return
  }
  coordinator.getFavorites()
}

/**
 * Load a favorite by ID
 */
void loadFavorite(String favoriteId) {
  if(!favoriteId) {
    logWarn('No favorite ID provided')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot load favorite')
    return
  }
  coordinator.loadFavorite(favoriteId)
}

/**
 * Load a favorite with full options
 */
void loadFavoriteFull(String favoriteId, String playMode = 'NORMAL', BigDecimal startTrack = 1, BigDecimal startTime = 0) {
  if(!favoriteId) {
    logWarn('No favorite ID provided')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot load favorite')
    return
  }
  coordinator.loadFavoriteFull(favoriteId, playMode, startTrack, startTime)
}

/**
 * Get playlists
 */
void getPlaylists() {
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot get playlists')
    return
  }
  coordinator.getPlaylists()
}

/**
 * Load a playlist by ID
 */
void loadPlaylist(String playlistId) {
  if(!playlistId) {
    logWarn('No playlist ID provided')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot load playlist')
    return
  }
  coordinator.loadPlaylist(playlistId)
}

/**
 * Load a playlist with full options
 */
void loadPlaylistFull(String playlistId, String repeatMode = 'repeat all', String queueMode = 'replace', String shuffleMode = 'off', String autoPlay = 'true', String crossfadeMode = 'on') {
  if(!playlistId) {
    logWarn('No playlist ID provided')
    return
  }
  DeviceWrapper coordinator = getCoordinatorDevice()
  if(!coordinator) {
    logWarn('Coordinator device not found - cannot load playlist')
    return
  }
  coordinator.loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}