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

metadata {
  definition(
    name: 'Sonos Advanced Group',
    version: '0.5.1',
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
  Map params = [uri: "http://127.0.0.1:8080/hub/details/json?reloadAccounts=false"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  String voice
  httpGet(params) {resp ->
    if(resp.status == 200) {
      def json = resp.data
      voice = json?.ttsCurrent ? json?.ttsCurrent : 'Matthew'
    }
  }
  return voice
}


void initialize() {
  if(settings.chimeBeforeTTS == null) { settings.chimeBeforeTTS = false }
}
void configure() {}
void on() { joinPlayersToCoordinator() }
void off() { removePlayersFromCoordinator() }
void setState(String stateName, String stateValue) { state[stateName] = stateValue }
void clearState() { state.clear() }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  logDebug(allDevs)
  allDevs.each{it.playerLoadAudioClip(textToSpeech(text, voice).uri, volume, getChimeBeforeTTSSetting())}
}

void playHighPriorityTTS(String text, BigDecimal volume = null, String voice = null) {
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  allDevs.each{it.playerLoadAudioClipHighPriority(textToSpeech(text, voice).uri, volume)}
}

void playHighPriorityTrack(String uri, BigDecimal volume = null) {
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  allDevs.each{it.playerLoadAudioClipHighPriority(uri, volume)}
}

void enqueueLowPriorityTrack(String uri, BigDecimal volume = null) {
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  allDevs.each{it.playerLoadAudioClip(uri, volume)}
}

void joinPlayersToCoordinator() {
  List<String> followers = getAllFollowersInGroupDevice()
  DeviceWrapper coordinator = getCoordinatorDevice()
  coordinator.playerModifyGroupMembers(followers)
}

void removePlayersFromCoordinator() {
  List<DeviceWrapper> allFollowers = getAllFollowerDevicesInGroupDevice()
  allFollowers.each{it.playerCreateNewGroup()}
}

void groupPlayers() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  DeviceWrapper coordinator = getCoordinatorDevice()
  coordinator.playerCreateGroup(allPlayers)
}

void ungroupPlayers() {
  List<DeviceWrapper> allDevs = getAllPlayerDevicesInGroupDevice()
  allDevs.each{it.playerCreateNewGroup()}
}

void evictUnlistedPlayers() {
  List<String> allPlayers = getAllPlayersInGroupDevice()
  DeviceWrapper coordinator = getCoordinatorDevice()
  coordinator.playerCreateGroup(allPlayers)
}

// =============================================================================
// Getters and Setters
// =============================================================================
String getCoordinatorId() {
  return this.device.getDataValue('groupCoordinatorId')
}

List<String> getAllPlayersInGroupDevice() {
  List<String> players = [this.device.getDataValue('groupCoordinatorId')]
  players.addAll(this.device.getDataValue('playerIds').tokenize(','))
  return players
}

List<String> getAllFollowersInGroupDevice() {
  List<String> players = this.device.getDataValue('playerIds').tokenize(',')
  return players
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