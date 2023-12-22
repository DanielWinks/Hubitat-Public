#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.ChildDeviceLibrary

metadata {
  definition(name: 'Sonos Cloud Player', namespace: 'dwinks', author: 'Daniel Winks') {
  capability 'AudioNotification'
  capability 'MusicPlayer'
  capability 'SpeechSynthesis'

  command 'setRepeatMode', [[ name: 'Repeat Mode', type: 'ENUM', constraints: [ 'Off', 'Repeat One', 'Repeat All' ]]]
  command 'setCrossfade', [[ name: 'Crossfade Mode', type: 'ENUM', constraints: ['On', 'Off']]]
  command 'setShuffle', [[ name: 'Shuffle Mode', type: 'ENUM', constraints: ['On', 'Off']]]
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
  Map modes = [
    'repeat': false,
    'repeatOne': false
  ]
  switch (mode) {
    case 'Off':
      logDebug modes
      break
    case 'Repeat One':
      modes.repeatOne = true
      logDebug modes
      break
    case 'Repeat All':
      modes.repeat = true
      logDebug modes
      break
  }
  logDebug("Device: ${this.device}")
  Map playModes = ['playModes': modes ]
  parent?.componentSetPlayModes(this.device, playModes)
}

void repeatOne() { setRepeatMode('Repeat One') }
void repeatAll() { setRepeatMode('Repeat All') }
void repeatNone() { setRepeatMode('Off') }

void setCrossfade(String mode) {
  logDebug("Setting crossfade mode to ${mode}")
  Map modes = [
    'crossfade': false
  ]
  switch (mode) {
    case 'Off':
      logDebug modes
      break
    case 'On':
      modes.crossfade = true
      logDebug modes
      break
  }
  logDebug("Device: ${this.device}")
  Map playModes = ['playModes': modes ]
  parent?.componentSetPlayModes(this.device, playModes)
}

void enableCrossfade() { setCrossfade('On') }
void disableCrossfade() { setCrossfade('Off') }

void setShuffle(String mode) {
  logDebug("Setting shuffle mode to ${mode}")
  Map modes = [
    'shuffle': false
  ]
  switch (mode) {
    case 'Off':
      logDebug modes
      break
    case 'On':
      modes.shuffle = true
      logDebug modes
      break
  }
  logDebug("Device: ${this.device}")
  Map playModes = ['playModes': modes ]
  parent?.componentSetPlayModes(this.device, playModes)
}

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
