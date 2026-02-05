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

metadata {
  definition(
    name: 'Sonos Advanced Group',
    version: '0.7.21',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvGroup.groovy'
  ) {
    capability 'Actuator'
    capability 'Switch'
    capability 'SpeechSynthesis'
    capability 'AudioVolume'
    capability 'MusicPlayer'

    command 'groupPlayers'
    command 'joinPlayersToCoordinator'
    command 'removePlayersFromCoordinator'
    command 'ungroupPlayers'
    command 'evictUnlistedPlayers'
    command 'refresh'

    command 'playHighPriorityTTS', [
      [name:'Text*', type:"STRING", description:"Text to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]],
      [name: 'Voice name', type: "ENUM", constraints: getTTSVoices().collect{it.name}.sort(), defaultValue: getCurrentTTSVoice()]
    ]

    command 'playHighPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]]
    ]

    command 'enqueueLowPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]]
    ]

    attribute 'coordinatorActive', 'string'
    attribute 'followers', 'string'

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
    attribute 'currentTrackNumber', 'number'
    attribute 'nextArtistName', 'string'
    attribute 'nextAlbumName', 'string'
    attribute 'nextTrackName', 'string'
    attribute 'queueTrackTotal', 'string'
    attribute 'queueTrackPosition', 'string'
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
  }
}
Boolean getChimeBeforeTTSSetting() { return settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false }
Boolean getControlUngroupedIndividuallySetting() { return settings.controlUngroupedIndividually != null ? settings.controlUngroupedIndividually : false }
Boolean getUseProportionalVolumeSetting() { return settings.useProportionalVolume != null ? settings.useProportionalVolume : true }


String getCurrentTTSVoice() {
  try {
    Map params = [
      uri: "http://127.0.0.1:8080/hub/details/json?reloadAccounts=false",
      contentType: 'application/json',
      requestContentType: 'application/json',
      timeout: 5
    ]
    String voice = 'Matthew'
    httpGet(params) {resp ->
      if(resp.status == 200) {
        def json = resp.data
        voice = json?.ttsCurrent ? json?.ttsCurrent : 'Matthew'
      }
    }
    return voice
  } catch (Exception e) {
    logDebug("Could not retrieve current TTS voice: ${e.message}")
    return 'Matthew'
  }
}


void initialize() {
  if(settings.chimeBeforeTTS == null) { settings.chimeBeforeTTS = false }
  // Initialize volume/mute state from coordinator
  runIn(5, 'refresh')
}
void configure() {}
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
  List<DeviceWrapper> allFollowers = getAllFollowerDevicesInGroupDevice()
  if(!allFollowers) {
    logDebug('No followers to remove from coordinator')
    return
  }
  allFollowers.each{it.playerCreateNewGroup()}
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
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  if(!allDevs) {
    logDebug('No devices found to ungroup')
    return
  }
  allDevs.each{it.playerCreateNewGroup()}
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
void setVolume(BigDecimal level) {
  if(level == null) {
    logWarn('No volume level provided')
    return
  }
  Integer volumeLevel = Math.max(0, Math.min(100, level.intValue()))
  DeviceWrapper coordinator = getCoordinatorDevice()
  Boolean isGrouped = isActuallySonosGrouped()
  logDebug("setVolume(${volumeLevel}) - isGrouped: ${isGrouped}, useProportional: ${getUseProportionalVolumeSetting()}, controlIndividually: ${getControlUngroupedIndividuallySetting()}")

  if(isGrouped) {
    // Speakers ARE grouped in Sonos
    if(getUseProportionalVolumeSetting()) {
      // Use Sonos native group volume API (proportional adjustment)
      if(!coordinator) {
        logWarn('Coordinator device not found - cannot set group volume')
        return
      }
      logDebug("Using Sonos group volume API (proportional)")
      coordinator.setGroupVolume(volumeLevel)
      // State will be updated via event from coordinator through parent app
    } else {
      // Set same volume on each speaker directly
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
    // Speakers are NOT grouped in Sonos
    if(getControlUngroupedIndividuallySetting()) {
      // Control each speaker individually
      List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
      if(!allDevs) {
        logWarn('No player devices found in group - cannot set volume')
        return
      }
      logDebug("Setting volume on each speaker individually (ungrouped)")
      allDevs.each { it.setVolume(volumeLevel) }
      sendEvent(name: 'volume', value: volumeLevel, unit: '%')
    } else {
      // Only control the coordinator
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
    }
    if(muteState != null) {
      sendEvent(name: 'mute', value: muteState)
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
  Integer currentTrackNumber = coordinator.currentValue('currentTrackNumber', true) as Integer
  String nextArtistName = coordinator.currentValue('nextArtistName', true)
  String nextAlbumName = coordinator.currentValue('nextAlbumName', true)
  String nextTrackName = coordinator.currentValue('nextTrackName', true)
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
  if(currentTrackNumber != null) sendEvent(name: 'currentTrackNumber', value: currentTrackNumber)
  if(nextArtistName != null) sendEvent(name: 'nextArtistName', value: nextArtistName)
  if(nextAlbumName != null) sendEvent(name: 'nextAlbumName', value: nextAlbumName)
  if(nextTrackName != null) sendEvent(name: 'nextTrackName', value: nextTrackName)
  if(queueTrackTotal != null) sendEvent(name: 'queueTrackTotal', value: queueTrackTotal)
  if(queueTrackPosition != null) sendEvent(name: 'queueTrackPosition', value: queueTrackPosition)
  if(currentRepeatOneMode != null) sendEvent(name: 'currentRepeatOneMode', value: currentRepeatOneMode)
  if(currentRepeatAllMode != null) sendEvent(name: 'currentRepeatAllMode', value: currentRepeatAllMode)
  if(currentCrossfadeMode != null) sendEvent(name: 'currentCrossfadeMode', value: currentCrossfadeMode)
  if(currentShuffleMode != null) sendEvent(name: 'currentShuffleMode', value: currentShuffleMode)
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
 */
void setLevel(BigDecimal level) {
  setVolume(level)
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