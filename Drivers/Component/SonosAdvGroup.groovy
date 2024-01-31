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
    version: '0.4.0',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvGroup.groovy'
  ) {
    capability 'Actuator'
    capability 'Switch'
    capability 'SpeechSynthesis'

    command 'groupPlayers'
    command 'joinPlayersToCoordinator'
    command 'removePlayersFromCoordinator'
    command 'ungroupPlayers'

    command 'playHighPriorityTTS', [
      [name:'Text*', type:"STRING", description:"Text to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]],
      [name: 'Voice name', type: "ENUM", constraints: getTTSVoices().collect{it.name}.sort(), defaultValue: getCurrentTTSVoice()]
    ]

    command 'playHighPriorityTrack', [
      [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
      [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]]
    ]

    attribute 'coordinatorActive', 'string'
    attribute 'followers', 'string'
  }
}

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


void initialize() {}
void configure() {}
void groupPlayers() { parent?.componentGroupPlayersLocal(this.device) }
void joinPlayersToCoordinator() { parent?.componentJoinPlayersToCoordinatorLocal(this.device) }
void removePlayersFromCoordinator() { parent?.componentRemovePlayersFromCoordinatorLocal(this.device) }
void ungroupPlayers() { parent?.componentUngroupPlayersLocal(this.device) }
void on() { joinPlayersToCoordinator() }
void off() { removePlayersFromCoordinator() }
void setState(String stateName, String stateValue) { state[stateName] = stateValue }
void clearState() { state.clear() }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  parent?.componentPlayTextLocal(this.device, text, volume, voice)
}

List<DeviceWrapper> getGroupMemberDevices() {
  List<String> players = getAllPlayersInGroupDevice()
  return parent?.getDevicesFromRincons(players)
}

void playHighPriorityTTS(String text, BigDecimal volume = null, String voice = null) {
  List<DeviceWrapper> devs = getGroupMemberDevices()
  devs.each{it.playerLoadAudioClipHighPriority(textToSpeech(text, voice).uri, volume)}
}

void playHighPriorityTrack(String uri, BigDecimal volume = null) {
  List<DeviceWrapper> devs = getGroupMemberDevices()
  devs.each{it.playerLoadAudioClipHighPriority(uri, volume)}
}

// =============================================================================
// Getters and Setters
// =============================================================================
List<String> getAllPlayersInGroupDevice() {
  List<String> players = [this.device.getDataValue('groupCoordinatorId')]
  players.add(this.device.getDataValue('playerIds').tokenize(','))
  return players
}