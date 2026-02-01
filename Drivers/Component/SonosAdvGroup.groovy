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
    version: '0.7.15',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvGroup.groovy'
  ) {
    capability 'Actuator'
    capability 'Switch'
    capability 'SpeechSynthesis'

    command 'groupPlayers'
    command 'joinPlayersToCoordinator'
    command 'removePlayersFromCoordinator'
    command 'ungroupPlayers'
    command 'evictUnlistedPlayers'

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
  }
  preferences {
    section('Device Settings') {
      input 'chimeBeforeTTS', 'bool', title: 'Play chime before standard priority TTS messages', required: false, defaultValue: false
    }
  }
}
Boolean getChimeBeforeTTSSetting() { return settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false }


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
  logDebug(allDevs)
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