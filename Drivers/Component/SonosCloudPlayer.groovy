#include dwinks.UtilitiesAndLogging
#include dwinks.ChildDeviceLibrary

metadata {
  definition(name: 'Sonos Cloud Player', namespace: 'dwinks', author: 'Daniel Winks') {
  capability 'AudioNotification'
  capability 'MusicPlayer'
  capability 'SpeechSynthesis'
  capability 'PushableButton'
  capability 'Refresh'

  command 'setRepeatMode', [[ name: 'Repeat Mode', type: 'ENUM', constraints: [ 'Off', 'Repeat One', 'Repeat All' ]]]
  command 'setCrossfade', [[ name: 'Crossfade Mode', type: 'ENUM', constraints: ['On', 'Off']]]
  command 'setShuffle', [[ name: 'Shuffle Mode', type: 'ENUM', constraints: ['On', 'Off']]]
  command 'ungroupPlayer'
  command 'subscribe'
  command 'clearStates'
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
  logDebug("Device: ${device}")
  Map playModes = ['playModes': modes ]
  parent?.setPlayModes(device, playModes)
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
  logDebug("Device: ${device}")
  Map playModes = ['playModes': modes ]
  parent?.setPlayModes(device, playModes)
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
  logDebug("Device: ${device}")
  Map playModes = ['playModes': modes ]
  parent?.setPlayModes(device, playModes)
}

void ungroupPlayer() { parent?.ungroupPlayer(device) }

void playText(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndRestore(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndResume(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void playTrack(String uri, BigDecimal volume = null) { devicePlayTrack(uri, volume) }
void playTrackAndRestore(String uri, BigDecimal volume = null) { devicePlayTrack(uri, volume) }
void playTrackAndResume(String uri, BigDecimal volume = null) { devicePlayTrack(uri, volume) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  parent?.componentPlayText(device, text, volume, voice)
}

void devicePlayTrack(String uri, BigDecimal volume = null) {
  parent?.componentPlayTrack(device, uri, volume)
}

void mute(){ parent?.componentMutePlayer(device, true) }
void unmute(){ parent?.componentMutePlayer(device, false) }
void setLevel(BigDecimal level) { parent?.componentSetLevel(device, level) }
void play() { parent?.componentPlay(device) }
void stop() { parent?.componentStop(device) }
void pause() { parent?.componentStop(device) }
void nextTrack() { parent?.componentNextTrack(device) }
void previousTrack() { parent?.componentPreviousTrack(device) }
void refresh() { parent?.componentRefresh(device) }

void getFavorites() {
  Map favorites = parent?.getFavorites(device)
}

void loadFavorite(String favoriteId) {
  parent?.loadFavorite(device, favoriteId)
}
