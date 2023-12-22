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
  definition(name: 'Sonos Cloud Player', namespace: 'dwinks', author: 'Daniel Winks', importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosCloudPlayer.groovy') {
  capability 'AudioNotification'
  capability 'MusicPlayer'
  capability 'SpeechSynthesis'

  command 'setRepeatMode', [[ name: 'Repeat Mode', type: 'ENUM', constraints: [ 'Off', 'Repeat One', 'Repeat All' ]]]
  command 'setCrossfade', [[ name: 'Crossfade Mode', type: 'ENUM', constraints: ['On', 'Off']]]
  command 'setShuffle', [[ name: 'Shuffle Mode', type: 'ENUM', constraints: ['On', 'Off']]]
  command 'shuffleOn'
  command 'shuffleOff'
  command 'ungroupPlayer'

  command 'getFavorites'
  command 'loadFavorite', [[ name: 'favoriteId', type: 'STRING']]

  command 'enableCrossfade'
  command 'disableCrossfade'

  command 'repeatOne'
  command 'repeatAll'
  command 'repeatNone'

  attribute 'playlistRepeat', 'enum', [ 'Off', 'Repeat One', 'Repeat All' ]
  attribute 'Crossfade', 'enum', [ 'On', 'Off' ]
  attribute 'Fav', 'string'
  }
}

void setRepeatMode(String mode) {
  logDebug("Setting repeat mode to ${mode}")
  Map playModes = [
    'repeat': false,
    'repeatOne': false
  ]
  if(mode = 'Repeat One') { playModes.repeatOne = true }
  if(mode = 'Repeat All') { playModes.repeat = true }
  parent?.componentSetPlayModes(this.device, ['playModes': playModes ])
}
void repeatOne() { setRepeatMode('Repeat One') }
void repeatAll() { setRepeatMode('Repeat All') }
void repeatNone() { setRepeatMode('Off') }

void setCrossfade(String mode) {
  logDebug("Setting crossfade mode to ${mode}")
  Map playModes = mode == 'On' ? [ 'crossfade': true ] : [ 'crossfade': false ]
  parent?.componentSetPlayModes(this.device, ['playModes': playModes ])
}
void enableCrossfade() { setCrossfade('On') }
void disableCrossfade() { setCrossfade('Off') }

void setShuffle(String mode) {
  logDebug("Setting shuffle mode to ${mode}")
  Map playModes = mode = 'On' ? [ 'shuffle': true ] : [ 'shuffle': false ]
  parent?.componentSetPlayModes(this.device, ['playModes': playModes ])
}
void shuffleOn() { setShuffle('On') }
void shuffleOff() { setShuffle('Off') }

void ungroupPlayer() { parent?.componentUngroupPlayer(this.device) }

void playText(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndRestore(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndResume(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void playTrack(String uri, BigDecimal volume = null) { devicePlayTrack(uri, volume) }
void playTrackAndRestore(String uri, BigDecimal volume = null) { devicePlayTrack(uri, volume) }
void playTrackAndResume(String uri, BigDecimal volume = null) { devicePlayTrack(uri, volume) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  parent?.componentPlayText(this.device, text, volume, voice)
}

void devicePlayTrack(String uri, BigDecimal volume = null) {
  parent?.componentPlayTrack(this.device, uri, volume)
}

void mute(){ parent?.componentMutePlayer(this.device, true) }
void unmute(){ parent?.componentMutePlayer(this.device, false) }
void setLevel(BigDecimal level) { parent?.componentSetLevel(this.device, level) }
void play() { parent?.componentPlay(this.device) }
void stop() { parent?.componentStop(this.device) }
void pause() { parent?.componentStop(this.device) }
void nextTrack() { parent?.componentNextTrack(this.device) }
void previousTrack() { parent?.componentPreviousTrack(this.device) }
void refresh() { parent?.componentRefresh(this.device) }

void getFavorites() {
  Map favorites = parent?.componentGetFavorites(this.device)
}

void loadFavorite(String favoriteId) {
  parent?.componentLoadFavorite(this.device, favoriteId)
}
