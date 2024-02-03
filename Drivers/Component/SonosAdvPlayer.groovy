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
#include dwinks.SMAPILibrary

metadata {
  definition(
    name: 'Sonos Advanced Player',
    version: '0.4.0',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    singleThreaded: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvPlayer.groovy'
  ) {

  capability 'AudioNotification'
  capability "AudioVolume" //mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%  //commands: volumeDown() volumeUp()
  capability 'MusicPlayer' //attributes: level - NUMBER mute - ENUM ["unmuted", "muted"] status - STRING trackData - JSON_OBJECT trackDescription - STRING
  capability "MediaTransport" //attributes:  transportStatus - ENUM - ["playing", "paused", "stopped"]
  capability 'SpeechSynthesis'
  capability 'Initialize'

  command 'setRepeatMode', [[ name: 'Repeat Mode', type: 'ENUM', constraints: [ 'off', 'repeat one', 'repeat all' ]]]
  command 'setCrossfade', [[ name: 'Crossfade Mode', type: 'ENUM', constraints: ['on', 'off']]]
  command 'setShuffle', [[ name: 'Shuffle Mode', type: 'ENUM', constraints: ['on', 'off']]]
  command 'shuffleOn'
  command 'shuffleOff'
  command 'ungroupPlayer'

  command 'setGroupMute', [[ name: 'state', type: 'ENUM', constraints: ['muted', 'unmuted']]]
  command 'setGroupVolume', [[name: 'Group Volume', type: 'NUMBER']]
  command 'groupVolumeUp'
  command 'groupVolumeDown'
  command 'muteGroup'
  command 'unmuteGroup'

  command 'getFavorites'
  command 'loadFavoriteFull', [
    [ name: 'favoriteId', type: 'STRING'],
    [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off' ]],
    [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
    [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on']],
    [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
    [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
  ]
  command 'loadFavorite', [[ name: 'favoriteId', type: 'STRING']]

  command 'enableCrossfade'
  command 'disableCrossfade'

  command 'repeatOne'
  command 'repeatAll'
  command 'repeatNone'

  attribute 'currentRepeatOneMode', 'enum', [ 'on', 'off' ]
  attribute 'currentRepeatAllMode', 'enum', [ 'on', 'off' ]
  attribute 'currentCrossfadeMode', 'enum', [ 'on', 'off' ]
  attribute 'currentShuffleMode' , 'enum', [ 'on', 'off' ]
  attribute 'currentTrackDuration', 'string'
  attribute 'currentArtistName', 'string'
  attribute 'albumArtURI', 'string'
  attribute 'currentAlbumName', 'string'
  attribute 'currentTrackName', 'string'
  attribute 'currentFavorite', 'string'
  attribute 'currentTrackNumber', 'number'
  attribute 'nextArtistName', 'string'
  attribute 'nextAlbumName', 'string'
  attribute 'nextTrackName', 'string'
  attribute 'queueTrackTotal', 'string'
  attribute 'queueTrackPosition', 'string'

  attribute 'treble', 'number'
  attribute 'bass', 'number'
  attribute 'loudness', 'enum', [ 'on', 'off' ]
  attribute 'balance', 'number'
  command 'setTreble', [[name:'Treble Level*', type:"NUMBER", description:"Treble level (-10..10)", constraints:["NUMBER"]]]
  command 'setBass', [[name:'Bass Level*', type:"NUMBER", description:"Bass level (-10..10)", constraints:["NUMBER"]]]
  command 'setLoudness', [[ name: 'Loudness Mode', type: 'ENUM', constraints: ['on', 'off']]]
  command 'setBalance', [[name:'Left/Right Balance*', type:"NUMBER", description:"Left/Right Balance (-20..20)", constraints:["NUMBER"]]]

  command 'playHighPriorityTTS', [
    [name:'Text*', type:"STRING", description:"Text to play", constraints:["STRING"]],
    [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]],
    [name: 'Voice name', type: "ENUM", constraints: getTTSVoices().collect{it.name}.sort(), defaultValue: getCurrentTTSVoice()]
  ]

  command 'playHighPriorityTrack', [
    [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
    [name:'Volume Level', type:"NUMBER", description:"Volume level (0 to 100)", constraints:["NUMBER"]]
  ]

  attribute 'groupVolume', 'number'
  attribute 'groupMute', 'string'
  attribute 'groupName', 'string'
  attribute 'groupCoordinatorName', 'string'
  attribute 'isGroupCoordinator' , 'enum', [ 'on', 'off' ]
  attribute 'groupId', 'string'
  attribute 'groupCoordinatorId', 'string'
  attribute 'isGrouped', 'enum', [ 'on', 'off' ]
  attribute 'groupMemberCount', 'number'
  attribute 'groupMemberNames', 'JSON_OBJECT'

  attribute 'status' , 'enum', [ 'playing', 'paused', 'stopped' ]
  attribute 'transportStatus' , 'enum', [ 'playing', 'paused', 'stopped' ]
  }

  preferences {
    section('Device Settings') {
      input 'disableTrackDataEvents', 'bool', title: 'Disable track data events', required: false, defaultValue: true
      input 'includeTrackDataMetaData', 'bool', title: 'Include metaData and trackMetaData in trackData JSON', required: false, defaultValue: false
      input name: 'volumeAdjustAmountLow', title: 'Volume up/down Adjust (Speaker Levels 0-10)', type: 'enum', required: false, defaultValue: 5,
      options: [1:'+/- 1%', 2:'+/- 2%', 3:'+/- 3%', 4:'+/- 4%', 5:'+/- 5%', 10:'+/- 10%', 20:'+/- 20%']
      input name: 'volumeAdjustAmountMid', title: 'Volume up/down Adjust (Speaker Levels 11-20)', type: 'enum', required: false, defaultValue: 5,
      options: [1:'+/- 1%', 2:'+/- 2%', 3:'+/- 3%', 4:'+/- 4%', 5:'+/- 5%', 10:'+/- 10%', 20:'+/- 20%']
      input name: 'volumeAdjustAmount', title: 'Volume up/down Adjust (Speaker Levels 21+)', type: 'enum', required: false, defaultValue: 5,
      options: [1:'+/- 1%', 2:'+/- 2%', 3:'+/- 3%', 4:'+/- 4%', 5:'+/- 5%', 10:'+/- 10%', 20:'+/- 20%']
      input name: 'ttsBoostAmount', title: 'TTS Volume boost/cut default:(+10%)', type: 'enum', required: false, defaultValue: 10,
      options: [(-10):'-10%', (-5):'-5%', 0:'No Change', 5:'+5%', 10:'+10%', 15:'+15%', 20:'+20%']
      input 'disableArtistAlbumTrackEvents', 'bool', title: 'Disable artist, album, and track events', required: false, defaultValue: false
      input 'createCrossfadeChildDevice', 'bool', title: 'Create child device for crossfade control?', required: false, defaultValue: false
      input 'createShuffleChildDevice', 'bool', title: 'Create child device for shuffle control?', required: false, defaultValue: false
      input 'createRepeatOneChildDevice', 'bool', title: 'Create child device for "repeat one" control?', required: false, defaultValue: false
      input 'createRepeatAllChildDevice', 'bool', title: 'Create child device for "repeat all" control?', required: false, defaultValue: false
      input 'createBatteryStatusChildDevice', 'bool', title: 'Create child device for battery status? (portable speakers only)', required: false, defaultValue: false
      input 'createFavoritesChildDevice', 'bool', title: 'Create child device for favorites?', required: false, defaultValue: false
      input 'createRightChannelChildDevice', 'bool', title: 'Create child device right channel? (stereo pair only)', required: false, defaultValue: false
      input 'chimeBeforeTTS', 'bool', title: 'Play chime before standard priority TTS messages', required: false, defaultValue: false
      input 'alwaysUseLoadAudioClip', 'bool', title: 'Always Use Non-Interrupting Methods', required: false, defaultValue: true
    }
  }
}

// =============================================================================
// Preference Getters And Passthrough Renames For Clarity
// =============================================================================
Boolean getDisableTrackDataEvents() { return disableTrackDataEvents != null ? disableTrackDataEvents : true }
Boolean getIncludeTrackDataMetaData() { return includeTrackDataMetaData != null ? includeTrackDataMetaData : false }
Integer getVolumeAdjustAmountLow() { return volumeAdjustAmountLow != null ? volumeAdjustAmountLow as Integer : 5 }
Integer getVolumeAdjustAmountMid() { return volumeAdjustAmountMid != null ? volumeAdjustAmountMid as Integer : 5 }
Integer getVolumeAdjustAmount() { return volumeAdjustAmount != null ? volumeAdjustAmount as Integer : 5 }
Integer getTTSBoostAmount() { return ttsBoostAmount != null ? ttsBoostAmount as Integer : 10 }
Boolean getDisableArtistAlbumTrackEvents() { return disableArtistAlbumTrackEvents != null ? disableArtistAlbumTrackEvents : false }
Boolean getCreateCrossfadeChildDevice() { return createCrossfadeChildDevice != null ? createCrossfadeChildDevice : false }
Boolean getCreateShuffleChildDevice() { return createShuffleChildDevice != null ? createShuffleChildDevice : false }
Boolean getCreateRepeatOneChildDevice() { return createRepeatOneChildDevice != null ? createRepeatOneChildDevice : false }
Boolean getCreateRepeatAllChildDevice() { return createRepeatAllChildDevice != null ? createRepeatAllChildDevice : false }
Boolean getCreateBatteryStatusChildDevice() { return createBatteryStatusChildDevice != null ? createBatteryStatusChildDevice : false }
Boolean getCreateFavoritesChildDevice() { return createFavoritesChildDevice != null ? createFavoritesChildDevice : false }
Boolean getCreateRightChannelChildDevice() { return createRightChannelChildDevice != null ? createRightChannelChildDevice : false }
Boolean getChimeBeforeTTS() { return chimeBeforeTTS != null ? chimeBeforeTTS : false }
Boolean getAlwaysUseLoadAudioClip() { return alwaysUseLoadAudioClip != null ? alwaysUseLoadAudioClip : true }

Boolean processBatteryStatusChildDeviceMessages() {return getCreateBatteryStatusChildDevice()}
Boolean loadAudioClipOnRightChannel() {return getCreateRightChannelChildDevice()}


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
// =============================================================================
// End Preference Getters And Passthrough Renames For Clarity
// =============================================================================



// =============================================================================
// Fields
// =============================================================================
@Field final Integer RESUB_INTERVAL = 7200
@Field static LinkedHashMap<String, ConcurrentLinkedQueue<Map>> audioClipQueue = new LinkedHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()
@Field static Map SOURCES = [
  "\$": "None",
  "x-file-cifs:": "Library",
  "x-rincon-mp3radio:": "Radio",
  "x-sonosapi-stream:": "Radio",
  "x-sonosapi-radio:": "Radio",
  "x-sonosapi-hls:": "Radio",
  "x-sonos-http:sonos": "Radio",
  "aac:": "Radio",
  "hls-radio:": "Radio",
  "https?:": "File",
  "x-rincon-stream:": "LineIn",
  "x-sonos-htastream:": "TV",
  "x-sonos-vli:.*,airplay:": "Airplay",
  "x-sonos-vli:.*,spotify:": "Spotify",
  "x-rincon-queue": "Sonos Q"
]
@Field private final String MRRC_EVENTS  =  '/MediaRenderer/RenderingControl/Event'
@Field private final String MRGRC_EVENTS =  '/MediaRenderer/GroupRenderingControl/Event'
@Field private final String ZGT_EVENTS   =  '/ZoneGroupTopology/Event'
@Field private final String MRAVT_EVENTS =  '/MediaRenderer/AVTransport/Event'
// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Initialize and Configure
// =============================================================================
void initialize() { configure() }
void configure() {
  atomicState.audioClipPlaying = false
  migrationCleanup()
  runIn(5, 'secondaryConfiguration')
}

void secondaryConfiguration() {
  createRemoveCrossfadeChildDevice(getCreateCrossfadeChildDevice())
  createRemoveShuffleChildDevice(getCreateShuffleChildDevice())
  createRemoveRepeatOneChildDevice(getCreateRepeatOneChildDevice())
  createRemoveRepeatAllChildDevice(getCreateRepeatAllChildDevice())
  createRemoveMuteChildDevice(createMuteChildDevice)
  createRemoveBatteryStatusChildDevice(getCreateBatteryStatusChildDevice())
  createRemoveFavoritesChildDevice(getCreateFavoritesChildDevice())
  createRemoveRightChannelChildDevice(getCreateRightChannelChildDevice())
  if(getDisableTrackDataEvents()) { clearTrackDataEvent() }
  if(getDisableArtistAlbumTrackEvents()) { clearCurrentNextArtistAlbumTrackData() }

  initializeWebsocketConnection()
  audioClipQueueInitialization()
  runIn(10, 'subscribeToEvents')
}

void migrationCleanup() {
  unschedule('resubscribeToGMEvents')
}

void audioClipQueueInitialization() {
  if(audioClipQueue == null) { audioClipQueue = new LinkedHashMap<String, ConcurrentLinkedQueue<Map>>() }
  if(!audioClipQueue.containsKey(getId())) {
    audioClipQueue[getId()] = new ConcurrentLinkedQueue<Map>()
  }
}
// =============================================================================
// End Initialize and Configure
// =============================================================================



// =============================================================================
// Device Methods
// =============================================================================
void setRepeatMode(String mode) {
  logDebug("Setting repeat mode to ${mode}")
  Map playModes = [
    'repeat': false,
    'repeatOne': false
  ]
  if(mode == 'repeat one') { playModes.repeatOne = true }
  if(mode == 'repeat all') { playModes.repeat = true }
  playerSetPlayModes(playModes)
}
void repeatOne() { setRepeatMode('repeat one') }
void repeatAll() { setRepeatMode('repeat all') }
void repeatNone() { setRepeatMode('off') }

void setCrossfade(String mode) {
  logDebug("Setting crossfade mode to ${mode}")
  Map playModes = mode == 'on' ? [ 'crossfade': true ] : [ 'crossfade': false ]
  playerSetPlayModes(playModes)
}
void enableCrossfade() { setCrossfade('on') }
void disableCrossfade() { setCrossfade('off') }

void setShuffle(String mode) {
  logDebug("Setting shuffle mode to ${mode}")
  Map playModes = mode == 'on' ? [ 'shuffle': true ] : [ 'shuffle': false ]
  playerSetPlayModes(playModes)
}
void shuffleOn() { setShuffle('on') }
void shuffleOff() { setShuffle('off') }

void ungroupPlayer() { playerCreateNewGroup() }

void playText(String text, BigDecimal volume = null) {
  if(getAlwaysUseLoadAudioClip()) { devicePlayText(text, volume) }
  else{ devicePlayTextNoRestore(text, volume) }
}
void playTextAndRestore(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndResume(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void setTrack(String uri) { parent?.componentSetStreamUrlLocal(this.device, uri, volume) }
void playTrack(String uri, BigDecimal volume = null) {
  if(getAlwaysUseLoadAudioClip()) { playerLoadAudioClip(uri, volume) }
  else{ parent?.componentLoadStreamUrlLocal(this.device, uri, volume) }
}
void playTrackAndRestore(String uri, BigDecimal volume = null) { playerLoadAudioClip(uri, volume) }
void playTrackAndResume(String uri, BigDecimal volume = null) { playerLoadAudioClip(uri, volume) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  playerLoadAudioClip(textToSpeech(text, voice).uri, volume)
}

void playHighPriorityTTS(String text, BigDecimal volume = null, String voice = null) {
  playerLoadAudioClipHighPriority(textToSpeech(text, voice).uri, volume )
}

void playHighPriorityTrack(String uri, BigDecimal volume = null) {
  playerLoadAudioClipHighPriority(uri, volume)
}

void devicePlayTextNoRestore(String text, BigDecimal volume = null, String voice = null) {
  if(volume) { volume += getTTSBoostAmount() }
  else { volume = getPlayerVolume() + getTTSBoostAmount() }
  parent?.componentPlayTextNoRestoreLocal(this.device, text, volume, voice)
}

void devicePlayTrack(String uri, BigDecimal volume = null) {
  parent?.componentLoadStreamUrlLocal(this.device, uri, volume)
}

void mute(){ playerSetPlayerMute(true) }
void unmute(){ playerSetPlayerMute(false) }
void setLevel(BigDecimal level) { playerSetPlayerVolume(level as Integer) }
void setVolume(BigDecimal level) { setLevel(level) }
void setTreble(BigDecimal level) { parent?.componentSetTrebleLocal(this.device, level)}
void setBass(BigDecimal level) { parent?.componentSetBassLocal(this.device, level)}
void setLoudness(String mode) { parent?.componentSetLoudnessLocal(this.device, mode == 'on')}
void setBalance(BigDecimal level) { parent?.componentSetBalanceLocal(this.device, level)}

void muteGroup(){
  if(isGroupedAndCoordinator()) {
    playerSetGroupMute(true)
  } else if(isGroupedAndNotCoordinator) {
    parent?.getDeviceFromRincon(getGroupCoordinatorId()).playerSetGroupMute(true)
  }
  else { playerSetPlayerMute(true) }
}
void unmuteGroup(){
  if(isGroupedAndCoordinator()) {
    playerSetGroupMute(false)
  } else if(isGroupedAndNotCoordinator) {
    parent?.getDeviceFromRincon(getGroupCoordinatorId()).playerSetGroupMute(false)
  }
  else { playerSetPlayerMute(false) }
}
void setGroupVolume(BigDecimal level) {
  if(isGroupedAndCoordinator()) {
    playerSetGroupVolume(level)
  } else if(isGroupedAndNotCoordinator) {
    parent?.getDeviceFromRincon(getGroupCoordinatorId()).playerSetGroupVolume(level as Integer)
  }
  else { playerSetPlayerVolume(level as Integer) }
}
void setGroupLevel(BigDecimal level) { setGroupVolume(level as Integer) }
void setGroupMute(String mode) {
  logDebug("Setting group mute to ${mode}")
  if(mode == 'muted') { muteGroup() }
  else { unmuteGroup() }
}
void groupVolumeUp() {
  if(isGroupedAndCoordinator()) {
    playerSetGroupRelativeVolume(getGroupVolumeAdjAmount())
  } else if(isGroupedAndNotCoordinator) {
    parent?.getDeviceFromRincon(getGroupCoordinatorId()).playerSetGroupRelativeVolume(getGroupVolumeAdjAmount())
  }
  else { playerSetPlayerRelativeVolume(getPlayerVolumeAdjAmount()) }
}
void groupVolumeDown() {
  if(isGroupedAndCoordinator()) {
    playerSetGroupRelativeVolume(-getGroupVolumeAdjAmount())
  } else if(isGroupedAndNotCoordinator) {
    parent?.getDeviceFromRincon(getGroupCoordinatorId()).playerSetGroupRelativeVolume(-getGroupVolumeAdjAmount())
  }
  else { playerSetPlayerRelativeVolume(-getPlayerVolumeAdjAmount()) }
}

void volumeUp() { playerSetPlayerRelativeVolume(getPlayerVolumeAdjAmount()) }
void volumeDown() { playerSetPlayerRelativeVolume(-getPlayerVolumeAdjAmount()) }

Integer getPlayerVolumeAdjAmount() {
  Integer currentVolume = (this.device.currentValue('volume', true) as Integer)
  if(currentVolume < 11) {
    return getVolumeAdjustAmountLow()
  } else if(currentVolume >= 11 && currentVolume < 21) {
    return getVolumeAdjustAmountMid()
  } else {
    return getVolumeAdjustAmount()
  }
}

Integer getGroupVolumeAdjAmount() {
  Integer currentVolume = (this.device.currentValue('groupVolume', true) as Integer)
  if(currentVolume < 11) {
    return getVolumeAdjustAmountLow()
  } else if(currentVolume >= 11 && currentVolume < 21) {
    return getVolumeAdjustAmountMid()
  } else {
    return getVolumeAdjustAmount()
  }
}

void play() { playerPlay() }
void stop() { playerStop() }
void pause() { playerPause() }
void nextTrack() { playerSkipToNextTrack() }
void previousTrack() { playerSkipToPreviousTrack() }
void subscribeToEvents() {
  subscribeToZgtEvents()
  subscribeToMrGrcEvents()
  subscribeToMrRcEvents()
}

void loadFavorite(String favoriteId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode) {
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode) {
  String autoPlay = 'true'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay) {
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay, String crossfadeMode) {
  String action = queueMode.toUpperCase()
  Boolean playOnCompletion = autoPlay == 'true'
  Boolean repeat = repeatMode == 'repeat all'
  Boolean repeatOne = repeatMode == 'repeat one'
  Boolean shuffle = shuffleMode == 'on'
  Boolean crossfade = crossfadeMode == 'on'
  if(isGroupedAndCoordinator()) {
    playerLoadFavorite(favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)
  } else if(isGroupedAndNotCoordinator) {
    parent?.getDeviceFromRincon(getGroupCoordinatorId()).playerLoadFavorite(favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)
  }
}
// =============================================================================
// End Device Methods
// =============================================================================



// =============================================================================
// Child device methods
// =============================================================================
void componentRefresh(DeviceWrapper child) {
  String command = child.getDataValue('command')
  logDebug("Child: ${child} command: ${command}")
  switch(command) {
    case 'Crossfade':
      getCrossfadeControlChild().sendEvent(name:'switch', value: device.currentState('currentCrossfadeMode')?.value )
    break
    case 'Shuffle':
      getShuffleControlChild().sendEvent(name:'switch', value: device.currentState('currentShuffleMode')?.value )
    break
    case 'RepeatOne':
      getRepeatOneControlChild().sendEvent(name:'switch', value: device.currentState('currentRepeatOneMode')?.value)
    break
    case 'RepeatAll':
      getRepeatAllControlChild().sendEvent(name:'switch', value: device.currentState('currentRepeatAllMode')?.value )
    break
    case 'Mute':
      String muteValue = device.currentState('mute')?.value != null ? device.currentState('mute').value : 'unmuted'
      getMuteControlChild().sendEvent(name:'switch', value: muteValue )
    break
  }
}

void componentOn(DeviceWrapper child) {
  String command = child.getDataValue('command').toString()
  switch(command) {
    case 'Crossfade':
      enableCrossfade()
    break
    case 'Shuffle':
      shuffleOn()
    break
    case 'RepeatOne':
      repeatOne()
    break
    case 'RepeatAll':
      repeatAll()
    break
  }

}

void componentOff(DeviceWrapper child) {
    String command = child.getDataValue('command')
  switch(command) {
    case 'Crossfade':
      disableCrossfade()
    break
    case 'Shuffle':
      shuffleOff()
    break
    case 'RepeatOne':
      repeatNone()
    break
    case 'RepeatAll':
      repeatNone()
    break
  }
}

void setNextArtistAlbumTrack(String nextArtistName, String nextAlbumName, String nextTrackName) {
  if(getDisableArtistAlbumTrackEvents()) {return}
  sendEvent(name:'nextArtistName', value: nextArtistName ?: 'Not Available')
  sendEvent(name:'nextAlbumName',  value: nextAlbumName ?: 'Not Available')
  sendEvent(name:'nextTrackName',  value: nextTrackName ?: 'Not Available')
}

void updateChildBatteryStatus(Integer battery, String powerSource, BigDecimal temperature) {
  if(getCreateBatteryStatusChildDevice()) {
    ChildDeviceWrapper child = getBatteryStatusChild()
    List<Event> stats = [
      [name: 'battery', value: battery, unit: '%' ],
      [name: 'powerSource', value: powerSource ],
      [name: 'temperature', value: getTemperatureScale() == 'F' ? celsiusToFahrenheit(temperature) : temperature, unit: getTemperatureScale() ],
    ]
    stats.each{ child.sendEvent(it) }
  }
}
// =============================================================================
// End Child device methods
// =============================================================================


// =============================================================================
// Child Device Helpers
// =============================================================================
String getCrossfadeControlChildDNI() { return "${device.getDeviceNetworkId()}-CrossfadeControl" }
String getShuffleControlChildDNI() { return "${device.getDeviceNetworkId()}-ShuffleControl" }
String getRepeatOneControlChildDNI() { return "${device.getDeviceNetworkId()}-RepeatOneControl" }
String getRepeatAllControlChildDNI() { return "${device.getDeviceNetworkId()}-RepeatAllControl" }
String getMuteControlChildDNI() { return "${device.getDeviceNetworkId()}-MuteControl" }
String getBatteryStatusChildDNI() { return "${device.getDeviceNetworkId()}-BatteryStatus" }
String getFavoritesChildDNI() { return "${device.getDeviceNetworkId()}-Favorites" }
String getRightChannelChildDNI() { return "${device.getDeviceNetworkId()}-RightChannel" }
ChildDeviceWrapper getCrossfadeControlChild() { return getChildDevice(getCrossfadeControlChildDNI()) }
ChildDeviceWrapper getShuffleControlChild() { return getChildDevice(getShuffleControlChildDNI()) }
ChildDeviceWrapper getRepeatOneControlChild() { return getChildDevice(getRepeatOneControlChildDNI()) }
ChildDeviceWrapper getRepeatAllControlChild() { return getChildDevice(getRepeatAllControlChildDNI()) }
ChildDeviceWrapper getMuteControlChild() { return getChildDevice(getMuteControlChildDNI()) }
ChildDeviceWrapper getBatteryStatusChild() { return getChildDevice(getBatteryStatusChildDNI()) }
ChildDeviceWrapper getFavoritesChild() { return getChildDevice(getFavoritesChildDNI()) }
ChildDeviceWrapper getRightChannelChild() { return getChildDevice(getRightChannelChildDNI()) }
// =============================================================================
// End Child Device Helpers
// =============================================================================



// =============================================================================
// Create Child Devices
// =============================================================================
void createRemoveCrossfadeChildDevice(Boolean create) {
  String dni = getCrossfadeControlChildDNI()
  ChildDeviceWrapper child = getCrossfadeControlChild()
  if(!child && create) {
    try {
      logDebug("Creating CrossfadeControl device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos Crossfade Control',
          label: "Sonos Crossfade Control - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'Crossfade')
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveCrossfadeChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveShuffleChildDevice(Boolean create) {
  String dni = getShuffleControlChildDNI()
  ChildDeviceWrapper child = getShuffleControlChild()
  if(!child && create) {
    try {
      logDebug("Creating Shuffle Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos Shuffle Control',
          label: "Sonos Shuffle Control - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'Shuffle')
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveShuffleChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveRepeatOneChildDevice(Boolean create) {
  String dni = getRepeatOneControlChildDNI()
  ChildDeviceWrapper child = getRepeatOneControlChild()
  if(!child && create) {
    try {
      logDebug("Creating RepeatOne Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos RepeatOne Control',
          label: "Sonos RepeatOne Control - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'RepeatOne')
      logDebug("ChildDNI = ${child.getDeviceNetworkId()}")
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveRepeatOneChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveRepeatAllChildDevice(Boolean create) {
  String dni = getRepeatAllControlChildDNI()
  ChildDeviceWrapper child = getRepeatAllControlChild()
  if(!child && create) {
    try {
      logDebug("Creating RepeatAll Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos RepeatAll Control',
          label: "Sonos RepeatAll Control - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'RepeatAll')
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveRepeatAllChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveMuteChildDevice(Boolean create) {
  String dni = getMuteControlChildDNI()
  ChildDeviceWrapper child = getMuteControlChild()
  if(!child && create) {
    try {
      logDebug("Creating Mute Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos Mute Control',
          label: "Sonos Mute Control - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'Mute')
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveMuteChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveBatteryStatusChildDevice(Boolean create) {
  String dni = getBatteryStatusChildDNI()
  ChildDeviceWrapper child = getBatteryStatusChild()
  Boolean hasBattery = deviceHasBattery()
  if(!child && create && hasBattery) {
    try {
      logDebug("Creating Battery Status device")
      child = addChildDevice('dwinks', 'Sonos Advanced Battery Status', dni,
        [ name: 'Sonos Battery Status',
          label: "Sonos Battery Status - ${this.getDataValue('name')}"]
      )
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveBatteryStatusChildDevice', e)
    }
  }
  else if(!create && child){ deleteChildDevice(dni) }
  else if(!child && create && !hasBattery) {
    logWarn('Not creating child battery device. No battery detected.')
    this.device.updateSetting('createBatteryStatusChildDevice', false)
  }
}

Boolean deviceHasBattery() {
  Map params = [uri: "${getLocalUpnpUrl()}/status/batterystatus"]
  httpGet(params) {resp ->
    if(resp.status == 200) {
      return resp.data.children().find{it.name() == 'LocalBatteryStatus'}.size() > 0
    } else { return false }
  }
}

void createRemoveFavoritesChildDevice(Boolean create) {
  String dni = getFavoritesChildDNI()
  ChildDeviceWrapper child = getFavoritesChild()
  if(!child && create) {
    try {
      logDebug("Creating Favorites device")
      child = addChildDevice('dwinks', 'Sonos Advanced Favorites', dni,
        [ name: 'Sonos Favorites',
          label: "Sonos Favorites - ${this.getDataValue('name')}"]
      )
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveFavoritesChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveRightChannelChildDevice(Boolean create) {
  String dni = getRightChannelChildDNI()
  ChildDeviceWrapper child = getRightChannelChild()
  if(!child && create && getRightChannelRincon()) {
    try {
      logDebug("Creating Right Channel device")
      child = addChildDevice('dwinks', 'Sonos Advanced Secondaries', dni,
        [ name: 'Sonos Secondaries',
          label: "Sonos Right Channel - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('deviceIp', getRightChannelDeviceIp())
      child.updateDataValue('id', getRightChannelRincon())
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveRightChannelChildDevice', e)
    }
  }
  else if(!create && child){ deleteChildDevice(dni) }
  else if(!child && create && !getRightChannelRincon()) {
    logWarn('Not creating right channel device. No right channel device detected.')
    this.device.updateSetting('createRightChannelChildDevice', false)
  }
}
// =============================================================================
// Create Child Devices
// =============================================================================



// =============================================================================
// Parse
// =============================================================================
void parse(String raw) {
  if(!raw.startsWith('mac:')){
    processWebsocketMessage(raw)
    return
  }
  LinkedHashMap message = parseLanMessage(raw)
  if(message.body == null) {return}
  String serviceType = message.headers["X-SONOS-SERVICETYPE"]
  if(serviceType == 'AVTransport' || message.headers.containsKey('NOTIFY /avt HTTP/1.1')) {
    processAVTransportMessages(message.body, getLocalUpnpUrl())
  }
  else if(serviceType == 'RenderingControl' || message.headers.containsKey('NOTIFY /mrc HTTP/1.1')) {
    processRenderingControlMessages(message?.body)
  }
  else if(serviceType == 'ZoneGroupTopology' || message.headers.containsKey('NOTIFY /zgt HTTP/1.1')) {
    LinkedHashSet<String> oldGroupedRincons = new LinkedHashSet((device.getDataValue('groupIds').tokenize(',')))
    processZoneGroupTopologyMessages(message?.body, oldGroupedRincons)
  }
  else if(serviceType == 'GroupRenderingControl' || message.headers.containsKey('NOTIFY /mgrc HTTP/1.1')) {
    processGroupRenderingControlMessages(message)
  }
  else if(serviceType == 'GroupManager' || message.headers.containsKey('NOTIFY /gm HTTP/1.1')) {
    processGroupManagementMessages(message)
  }
  else {
    logDebug("Could not determine service type for message: ${message}")
  }
}
// =============================================================================
// End Parse
// =============================================================================



// =============================================================================
// Parse Helper Methods
// =============================================================================
@CompileStatic
String unEscapeMetaData(String text) {
  return text.replace('&amp;lt;','<').replace('&amp;gt;','>').replace('&amp;quot;','"')
}

@CompileStatic
String unEscapeLastChangeXML(String text) {
  return text.replace('&lt;','<').replace('&gt;','>')
}

@CompileStatic
void processAVTransportMessages(String xmlString, String localUpnpUrl) {
  if(xmlString.contains('&lt;CurrentTrackURI val=&quot;x-rincon:')) { return } //Bail out if this AVTransport message is just "I'm now playing a stream from a coordinator..."
  if(xmlString.contains('&lt;TransportState val=&quot;TRANSITIONING&quot;/&gt;')) { return } //Bail out if this AVTransport message is TRANSITIONING"

  GPathResult propertyset = new XmlSlurper().parseText(xmlString)
  String lastChange = ((GPathResult)propertyset['property']['LastChange']).text().toString()

  if(!lastChange) {return}
  GPathResult event = new XmlSlurper().parseText(lastChange)
  GPathResult instanceId = (GPathResult)event['InstanceID']

  String trackUri = ((instanceId['CurrentTrackURI']['@val']).toString()).replace('&amp;','&').replace('&amp;','&')
  String enqueuedUri = instanceId['EnqueuedTransportURI']['@val']
  String enqueuedTransportURIMetaDataString = instanceId['EnqueuedTransportURIMetaData']['@val']
  String avTransportURI = instanceId['AVTransportURI']['@val']

  Boolean isPlayingLocalTrack = false
  if(trackUri.startsWith('http') && trackUri == enqueuedUri && trackUri == avTransportURI) {
    isPlayingLocalTrack = true
    logTrace("Playing Local Track")
  }

  String status = (instanceId['TransportState']['@val'].toString()).toLowerCase().replace('_playback','')
  setStatusTransportStatus(status)

  String currentPlayMode = instanceId['CurrentPlayMode']['@val']
  setPlayMode(currentPlayMode)

  String numberOfTracks = instanceId['NumberOfTracks']['@val']
  String trackNumber = instanceId['CurrentTrack']['@val']

  Boolean isAirPlay = trackUri.toLowerCase().contains('airplay')
  String currentTrackDuration = instanceId['CurrentTrackDuration']['@val']
  setCurrentTrackDuration(currentTrackDuration)

  String currentCrossfadeMode = instanceId['CurrentCrossfadeMode']['@val']
  setCrossfadeMode(currentCrossfadeMode == '1' ? 'on' : 'off')

  String currentTrackMetaDataString = (instanceId['CurrentTrackMetaData']['@val']).toString()
  if(currentTrackMetaDataString) {
    GPathResult currentTrackMetaData = new XmlSlurper().parseText(unEscapeMetaData(currentTrackMetaDataString))
    String albumArtURI = (((GPathResult)currentTrackMetaData['item']['albumArtURI']).text()).toString()
    while(albumArtURI.contains('&amp;')) { albumArtURI = albumArtURI.replace('&amp;','&') }
    String currentArtistName = currentTrackMetaData['item']['creator']
    String currentAlbumName = currentTrackMetaData['item']['album']
    String currentTrackName = currentTrackMetaData['item']['title']
    String streamContent = currentTrackMetaData['item']['streamContent'].toString()

    String trackDescription = currentTrackName && currentArtistName ? "${currentTrackName} by ${currentArtistName}" : null
    streamContent = streamContent == 'ZPSTR_BUFFERING' ? 'Starting...' : streamContent
    streamContent = streamContent == 'ZPSTR_CONNECTING' ? 'Connecting...' : streamContent
    streamContent = !streamContent ? 'Not Available' : streamContent
    if(streamContent && (!currentArtistName && !currentTrackName)) { trackDescription = streamContent }
    else if(isPlayingLocalTrack) { trackDescription = 'Not Available' }

    String trackDataAlbumArtUri
    if(albumArtURI.startsWith('/') && !isPlayingLocalTrack) {
      trackDataAlbumArtUri = "${localUpnpUrl}${albumArtURI}"
    } else if (!isPlayingLocalTrack) { trackDataAlbumArtUri = "${albumArtURI}" }

    String uri = instanceId['AVTransportURI']['@val']
      // String transportUri = uri ?? Seems to be the same on the built-in driver
    String audioSource = SOURCES["${(uri.tokenize(':')[0])}"]
    if(!getDisableTrackDataEvents()) {
      Map trackData = [:]
      trackData['audioSource'] = audioSource ?: trackData['audioSource']
      trackData['station'] = null
      trackData['name'] = currentTrackName
      trackData['artist'] = currentArtistName
      trackData['album'] = currentAlbumName
      trackData['albumArtUrl'] = trackDataAlbumArtUri ?: trackData['albumArtUrl']
      trackData['trackNumber'] = trackNumber ?: trackData['trackNumber']
      trackData['status'] = status ?: trackData['status']
      trackData['uri'] = uri ?: trackData['uri']
      trackData['trackUri'] = trackUri ?: trackData['trackUri']
      trackData['transportUri'] = uri ?: trackData['transportUri']
      trackData['enqueuedUri'] = enqueuedUri ?: trackData['enqueuedUri']
      if(getIncludeTrackDataMetaData()) {
        trackData['metaData'] = enqueuedTransportURIMetaDataString
        trackData['trackMetaData'] =  currentTrackMetaDataString ?: trackData['trackMetaData']
      }
      setTrackDataEvents(trackData)
    }
    setCurrentArtistName(currentArtistName)
    setCurrentAlbumName(currentAlbumName)
    setCurrentTrackName(currentTrackName)
    setCurrentTrackNumber(trackNumber as Integer)
    setTrackDescription(trackDescription)
  setAlbumArtURI(albumArtURI, isPlayingLocalTrack)

  } else {
    setCurrentArtistName('Not Available')
    setCurrentAlbumName('Not Available')
    setCurrentTrackName('Not Available')
    setCurrentTrackNumber(0)
    setTrackDataEvents([:])
  }

  String nextTrackMetaData = instanceId['NextTrackMetaData']['@val']
  if(nextTrackMetaData) {
    GPathResult nextTrackMetaDataXML = new XmlSlurper().parseText(nextTrackMetaData)
    if(nextTrackMetaDataXML) {
      String nextArtistName = nextTrackMetaDataXML['item']['creator']
      String nextAlbumName = nextTrackMetaDataXML['item']['album']
      String nextTrackName = nextTrackMetaDataXML['item']['title']
      setNextArtistName(nextArtistName)
      setNextAlbumName(nextAlbumName)
      setNextTrackName(nextTrackName)
    } else {
      setNextArtistName('Not Available')
      setNextAlbumName('Not Available')
      setNextTrackName('Not Available')
    }
  }
}

@CompileStatic
void processZoneGroupTopologyMessages(String xmlString, LinkedHashSet oldGroupedRincons) {
  GPathResult propertyset = new XmlSlurper().parseText(xmlString)
  String zoneGroupStateString = ((GPathResult)propertyset['property']['ZoneGroupState']).text() //['ZoneGroupState']['ZoneGroups']
  GPathResult zoneGroupState = new XmlSlurper().parseText(unEscapeLastChangeXML(zoneGroupStateString))
  GPathResult zoneGroups = (GPathResult)zoneGroupState['ZoneGroups']

  LinkedHashSet<String> groupedRincons = new LinkedHashSet()
  GPathResult currentGroupMembers = zoneGroups.children().children().findAll{it['@UUID'] == getId()}.parent().children()

  currentGroupMembers.each{
    if(it['@Invisible'] == '1') {return}
    groupedRincons.add(it['@UUID'].toString())
  }
  if(groupedRincons.size() == 0) {
    logTrace("No grouped rincons found!")
  }

  if(groupedRincons != oldGroupedRincons) {
    logTrace('ZGT message parsed, group member changes found.')
  } else {
    logTrace('ZGT message parsed, no group member changes.')
  }

  setGroupPlayerIds(groupedRincons.toList())

  String currentGroupCoordinatorName = zoneGroups.children().children().findAll{it['@UUID'] == getId()}['@ZoneName']
  LinkedHashSet currentGroupMemberNames = []
  groupedRincons.each{ gr ->
    currentGroupMemberNames.add(zoneGroups.children().children().findAll{it['@UUID'] == gr}['@ZoneName']) }
  Integer currentGroupMemberCount = groupedRincons.size()


  String groupName = ((GPathResult)propertyset['property']['ZoneGroupName']).text().toString()
  if(groupName && groupedRincons) {
    updateZoneGroupName(groupName, groupedRincons)
  }

  if(currentGroupCoordinatorName) {setGroupCoordinatorName(currentGroupCoordinatorName)}
  setIsGrouped(currentGroupMemberCount > 1)
  setGroupMemberCount(currentGroupMemberCount)
  if(currentGroupMemberNames.size() > 0) {setGroupMemberNames(currentGroupMemberNames.toList())}
  if(groupName) {setGroupName(groupName)}

  if(processBatteryStatusChildDeviceMessages()) {
    String moreInfoString = zoneGroups.children().children().findAll{it['@UUID'] == getId()}['@MoreInfo']
    if(moreInfoString) {
      Map<String,String> moreInfo = moreInfoString.tokenize(',').collect{ it.tokenize(':') }.collectEntries{ [it[0].toString(),it[1].toString()]}
      if(moreInfo.containsKey('BattTmp') && moreInfo.containsKey('BattPct') && moreInfo.containsKey('BattChg')) {
        Integer battery =  moreInfo['BattPct'] as Integer
        String powerSource = moreInfo['BattChg'] == 'NOT_CHARGING' ? 'battery' : 'mains'
        BigDecimal temperature = new BigDecimal(moreInfo['BattTmp'])
        updateChildBatteryStatus(battery, powerSource, temperature)
      }
    }
  }
}

void parentUpdateGroupDevices(String coordinatorId, List<String> playersInGroup) {
  parent?.updateGroupDevices(coordinatorId, playersInGroup)
}

void updateZoneGroupName(String groupName, LinkedHashSet<String> groupedRincons) {
  parent?.updateZoneGroupName(groupName, groupedRincons)
}

void processGroupRenderingControlMessages(Map message) { parent?.processGroupRenderingControlMessages(this.device, message) }

@CompileStatic
void processRenderingControlMessages(String xmlString) {
  GPathResult propertyset = parseSonosMessageXML(xmlString)
  GPathResult instanceId = (GPathResult)propertyset['property']['LastChange']['Event']['InstanceID']

  String volume = instanceId.children().findAll{GPathResult it -> it.name() == 'Volume' && it['@channel'] == 'Master'}['@val']
  if(volume) { setPlayerVolume(volume as Integer) }

  String lfString = instanceId.children().findAll{((GPathResult)it).name() == 'Volume' && it['@channel'] == 'LF'}['@val'].toString()
  lfString = lfString ?: '0'
  String rfString = instanceId.children().findAll{((GPathResult)it).name() == 'Volume' && it['@channel'] == 'RF'}['@val'].toString()
  rfString = rfString ?: '0'
  Integer lf = Integer.parseInt(lfString)
  Integer rf = Integer.parseInt(rfString)
  Integer balance = 0
  if(lf < 100) {
    balance = ((100 - lf) / 5) as Integer
  } else if(rf < 100) {
    balance = -((100 - rf) / 5) as Integer
  }
  setBalance(balance)

  String mute = instanceId.children().findAll{((GPathResult)it).name() == 'Mute' && it['@channel'] == 'Master'}['@val']
  if(mute) {
    setMuteState(mute == '1' ? 'muted' : 'unmuted')
  }

  String bass = instanceId['Bass']['@val']
  if(bass) { setBassState(bass as Integer) }

  String treble = instanceId['Treble']['@val']
  if(treble) { setTrebleState(treble as Integer) }

  String loudness = instanceId.children().findAll{((GPathResult)it).name() == 'Loudness' && it['@channel'] == 'Master'}['@val']
  if(loudness) { setLoudnessState(loudness == '1' ? 'on' : 'off') }
}
// =============================================================================
// Parse Helper Methods
// =============================================================================



// =============================================================================
// UPnP Subscriptions and Resubscriptions
// =============================================================================
  // sonosEventSubscribe('/MediaRenderer/Queue/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/MediaServer/ContentDirectory/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/MusicServices/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/SystemProperties/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/AlarmClock/Event', host, RESUB_INTERVAL, dni)

String getlocalUpnpHost() {return device.getDataValue('localUpnpHost')}
String getDNI() {return device.getDeviceNetworkId()}


// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/AVTransport/Event' //sid1
// /////////////////////////////////////////////////////////////////////////////
void subscribeToAVTransport() {
  if(device.getDataValue('sid1')) { resubscribeToAVTransport() }
  else {
    sonosEventSubscribe(MRAVT_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/avt', 'subscribeToMrAvTCallback')
    unschedule('resubscribeToAVTransport')
    runIn(RESUB_INTERVAL-100, 'resubscribeToAVTransport')
  }
}

void subscribeToMrAvTCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to subscribe to MediaRenderer/AVTransport. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid1')
    runIn(60, 'subscribeToAVTransport')
  } else if(response.status == 200) {
    logDebug('Sucessfully subscribed to MediaRenderer/AVTransport')
    if(response.headers["SID"]) {device.updateDataValue('sid1', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid1', response.headers["sid"])}
  }
}

void resubscribeToAVTransport() {
  if(device.getDataValue('sid1')) {
    sonosEventRenew(MRAVT_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), device.getDataValue('sid1'), 'resubscribeToMrAvTCallback')
  } else { subscribeToAVTransport() }
  runIn(RESUB_INTERVAL-100, 'resubscribeToAVTransport')
}

void resubscribeToMrAvTCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to resubscribe to MediaRenderer/AVTransport. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid1')
    runIn(60, 'subscribeToAVTransport')
  } else if(response.status == 200) {
    logTrace('Sucessfully resubscribed to MediaRenderer/AVTransport')
    if(response.headers["SID"]) {device.updateDataValue('sid1', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid1', response.headers["sid"])}
  }
}

void unsubscribeFromAVTransport() {
  if(device.getDataValue('sid1')) {
    sonosEventUnsubscribe(MRAVT_EVENTS, getlocalUpnpHost(), getDNI(), device.getDataValue('sid1'), 'unsubscribeToMrAvTCallback')
    unschedule('resubscribeToAVTransport')
  }
}

void unsubscribeToMrAvTCallback(HubResponse response) {
  if(response.status == 412){
    logDebug('Failed to unsubscribe to MediaRenderer/AVTransport. This is likely due to not currently being subscribed and is safely ignored.')
  } else if(response.status == 200) {
    logDebug('Sucessfully unsubscribed to MediaRenderer/AVTransport')
  }
  device.removeDataValue('sid1')
}


// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/RenderingControl/Event' //sid2
// /////////////////////////////////////////////////////////////////////////////
void subscribeToMrRcEvents() {
  if(device.getDataValue('sid2')) { resubscribeToMrRcEvents() }
  else {
    sonosEventSubscribe(MRRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/mrc', 'subscribeToMrRcCallback')
    unschedule('resubscribeToMrRcEvents')
    runIn(RESUB_INTERVAL-100, 'resubscribeToMrRcEvents')
  }
}

void subscribeToMrRcCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to subscribe to MediaRenderer/RenderingControl. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid2')
    runIn(60, 'subscribeToMrRcEvents')
  } else if(response.status == 200) {
    logDebug('Sucessfully subscribed to MediaRenderer/RenderingControl')
    if(response.headers["SID"]) {device.updateDataValue('sid2', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid2', response.headers["sid"])}
  }
}

void resubscribeToMrRcEvents() {
  if(device.getDataValue('sid2')) {
    sonosEventRenew(MRRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), device.getDataValue('sid2'), 'resubscribeToMrRcCallback')
  } else { subscribeToMrRcEvents() }
  runIn(RESUB_INTERVAL-100, 'resubscribeToMrRcEvents')
}

void resubscribeToMrRcCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to resubscribe to MediaRenderer/RenderingControl. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid2')
    runIn(60, 'subscribeToMrRcEvents')
  } else if(response.status == 200) {
    logDebug('Sucessfully resubscribed to MediaRenderer/RenderingControl')
    if(response.headers["SID"]) {device.updateDataValue('sid2', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid2', response.headers["sid"])}
  }
}


// /////////////////////////////////////////////////////////////////////////////
// '/ZoneGroupTopology/Event' //sid3
// /////////////////////////////////////////////////////////////////////////////
void subscribeToZgtEvents() {
  if(device.getDataValue('sid3')) { resubscribeToZgtEvents() }
  else {
    sonosEventSubscribe(ZGT_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/zgt', 'subscribeToZgtCallback')
    unschedule('resubscribeToZgtEvents')
    runIn(RESUB_INTERVAL-100, 'resubscribeToZgtEvents')
  }
}

void subscribeToZgtCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to subscribe to ZoneGroupTopology. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid3')
    runIn(60, 'subscribeToZgtEvents')
  } else if(response.status == 200) {
    logDebug('Sucessfully subscribed to ZoneGroupTopology')
    if(response.headers["SID"]) {device.updateDataValue('sid3', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid3', response.headers["sid"])}
  }
}

void resubscribeToZgtEvents() {
  if(device.getDataValue('sid3')) {
    sonosEventRenew(MRRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), device.getDataValue('sid3'), 'resubscribeToZgtCallback')
  } else { subscribeToZgtEvents() }
  runIn(RESUB_INTERVAL-100, 'resubscribeToZgtEvents')
}

void resubscribeToZgtCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to resubscribe to ZoneGroupTopology. Will trying subscribing again in 30 seconds.')
    device.removeDataValue('sid3')
    runIn(30, 'subscribeToZgtEvents')
  } else if(response.status == 200) {
    logDebug('Sucessfully resubscribed to ZoneGroupTopology')
    if(response.headers["SID"]) {device.updateDataValue('sid3', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid3', response.headers["sid"])}
  }
}


// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/GroupRenderingControl/Event' //sid4
// /////////////////////////////////////////////////////////////////////////////
void subscribeToMrGrcEvents() {
  if(device.getDataValue('sid4')) { resubscribeToMrGrcEvents() }
  else {
    sonosEventSubscribe(MRGRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/mgrc', 'subscribeToMrGrcCallback')
    unschedule('resubscribeToMrGrcEvents')
    runIn(RESUB_INTERVAL-100, 'resubscribeToMrGrcEvents')
  }
}

void subscribeToMrGrcCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to resubscribe to MediaRenderer/GroupRenderingControl. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid4')
    runIn(60, 'subscribeToMrGrcEvents')
  } else if(response.status == 200) {
    logDebug('Sucessfully subscribed to MediaRenderer/GroupRenderingControl')
    if(response.headers["SID"]) {device.updateDataValue('sid4', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid4', response.headers["sid"])}
  }
}

void resubscribeToMrGrcEvents() {
  if(device.getDataValue('sid4')) { sonosEventRenew(MRGRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), device.getDataValue('sid4'), 'resubscribeToMrGrcCallback')}
  runIn(RESUB_INTERVAL-100, 'resubscribeToMrGrcEvents')
}

void resubscribeToMrGrcCallback(HubResponse response) {
  if(response.status == 412){
    logWarn('Failed to resubscribe to MediaRenderer/GroupRenderingControl. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid4')
    runIn(60, 'subscribeToMrGrcEvents')
  } else if(response.status == 200) {
    logDebug('Sucessfully resubscribed to MediaRenderer/GroupRenderingControl')
    if(response.headers["SID"]) {device.updateDataValue('sid4', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid4', response.headers["sid"])}
  }
}
// =============================================================================
// End UPnP Subscriptions and Resubscriptions
// =============================================================================


// =============================================================================
// Misc helpers
// =============================================================================
void clearCurrentNextArtistAlbumTrackData() {
  setCurrentArtistAlbumTrack(null, null, null, 0)
  setNextArtistAlbumTrack(null, null, null)
}

void clearTrackDataEvent() {
  sendEvent(name: 'trackData', value: '{}')
}





// =============================================================================
// Getters and Setters
// =============================================================================
String getLocalApiUrl(){
  return "https://${this.device.getDataValue('deviceIp')}:1443/api/v1/"
}
String getLocalUpnpHost(){
  return "${this.device.getDataValue('deviceIp')}:1400"
}
String getLocalUpnpUrl(){
  return "http://${this.device.getDataValue('deviceIp')}:1400"
}
String getLocalWsUrl(){
  return "wss://${this.device.getDataValue('deviceIp')}:1443/websocket/api"
}

List<String> getLocalApiUrlSecondaries(){
  List<String> secondaryDeviceIps = this.device.getDataValue('secondaryDeviceIps').tokenize(',')
  return secondaryDeviceIps.collect{"https://${it}:1443/api/v1/"}
}
List<String> getLocalUpnpHostSecondaries(){
  List<String> secondaryDeviceIps = this.device.getDataValue('secondaryDeviceIps').tokenize(',')
  return secondaryDeviceIps.collect{"${it}:1400"}
}
List<String> getLocalUpnpUrlSecondaries(){
  List<String> secondaryDeviceIps = this.device.getDataValue('secondaryDeviceIps').tokenize(',')
  return secondaryDeviceIps.collect{"http://${it}:1400"}
}

String getRightChannelRincon() {
  return this.device.getDataValue('rightChannelId')
}
void setRightChannelRincon(String rincon) {
  logTrace("Setting right channel RINCON to ${rincon}")
  this.device.updateDataValue('rightChannelId', rincon)
  if(getSecondaryIds()) {
    Long secondaryIndex = getSecondaryIds().findIndexValues{it == rincon}[0]
    String rightChannelIpAddress = getSecondaryDeviceIps()[secondaryIndex as Integer]
    setRightChannelDeviceIp(rightChannelIpAddress)
  }
}

String getRightChannelDeviceIp() {
  return this.device.getDataValue('rightChannelDeviceIp')
}
void setRightChannelDeviceIp(String ipAddress) {
  this.device.updateDataValue('rightChannelDeviceIp', ipAddress)
}

String getHouseholdId(){
  return this.device.getDataValue('householdId')
}
void setHouseholdId(String householdId) {
  this.device.updateDataValue('householdId', householdId)
}

String getId() {
  return this.device.getDataValue('id')
}
void setId(String id) {
  this.device.updateDataValue('id', id)
}
List<String> getSecondaryIds() {
  return this.device.getDataValue('secondaryIds').tokenize(',')
}
void setSecondaryIds(List<String> ids) {
  this.device.updateDataValue('secondaryIds', ids.join(','))
}
Boolean hasSecondaries() {
  return this.device.getDataValue('secondaryIds') && this.device.getDataValue('secondaryIds').size() > 0
}
String getRightChannelId() {
  return this.device.getDataValue('rightChannelId')
}

String getDeviceIp() {
  return this.device.getDataValue('deviceIp')
}
void setDeviceIp(String ipAddress) {
  this.device.updateDataValue('deviceIp', ipAddress)
}
List<String> getSecondaryDeviceIps() {
  return this.device.getDataValue('secondaryDeviceIps').tokenize(',')
}
void setSecondaryDeviceIps(List<String> ipAddresses) {
  this.device.updateDataValue('secondaryDeviceIps', ipAddresses.join(','))
}

String getGroupId() {
  return this.device.getDataValue('groupId')
}
void setGroupId(String groupId) {
  this.device.updateDataValue('groupId', groupId)
  this.device.sendEvent(name: 'groupId', value: groupId)
  if(getIsGroupCoordinator()) {
    subscribeToPlayback(groupId)
    // subscribeToPlaybackMetadata(groupId)
  }
}

String getGroupName() {
  return this.device.currentValue('groupName',true)
}
void setGroupName(String groupName) {
  this.device.sendEvent(name: 'groupName', value: groupName)
}

Boolean getIsGrouped() {
  return this.device.currentValue('isGrouped') == 'on'
}
void setIsGrouped(Boolean isGrouped) {
  this.device.sendEvent(name: 'isGrouped', value: isGrouped ? 'on' : 'off')
}

Integer getGroupMemberCount() {
  return this.device.currentValue('groupMemberCount') as Integer
}
void setGroupMemberCount(Integer groupMemberCount) {
  this.device.sendEvent(name: 'groupMemberCount', value: groupMemberCount)
}

String getGroupCoordinatorId() {
  return this.device.getDataValue('groupCoordinatorId')
}
void setGroupCoordinatorId(String groupCoordinatorId) {
  logTrace("setGroupCoordinatorId ${groupCoordinatorId}")
  this.device.updateDataValue('groupCoordinatorId', groupCoordinatorId)
  this.device.sendEvent(name: 'groupCoordinatorId', value: groupCoordinatorId)
  Boolean isGroupCoordinator = getId() == groupCoordinatorId
  Boolean previouslyWasGroupCoordinator = getIsGroupCoordinator()
  setIsGroupCoordinator(isGroupCoordinator)

  if(isGroupCoordinator && !previouslyWasGroupCoordinator) {
    if(!this.device.getDataValue('sid1')) {
      subscribeToAVTransport()
      initializeWebsocketConnection()
      getPlaybackMetadataStatus()
      playerGetGroupsFull()
      logTrace('Just became group coordinator, subscribing to AVT.')
    }
  } else if(previouslyWasGroupCoordinator && !isGroupCoordinator) {
      logTrace("Just added to group!")
      unsubscribeFromAVTransport()
      parent?.updatePlayerCurrentStates(this.device, groupCoordinatorId)
  } else {logTrace("Group coordinator status has not changed.")}
}

String getGroupCoordinatorName() {
  return this.device.currentValue('groupCoordinatorName', true)
}
void setGroupCoordinatorName(String groupCoordinatorName) {
  this.device.sendEvent(name: 'groupCoordinatorName ', value: groupCoordinatorName)
}

Boolean getIsGroupCoordinator() {
  return this.device.getDataValue('isGroupCoordinator') == 'true'
}
void setIsGroupCoordinator(Boolean isGroupCoordinator) {
  this.device.updateDataValue('isGroupCoordinator', isGroupCoordinator.toString())
  this.device.sendEvent(name: 'isGroupCoordinator', value: isGroupCoordinator ? 'on' : 'off')
}

Boolean isGroupedAndCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator()
}

Boolean isGroupedAndNotCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator() == false
}

List<String> getGroupPlayerIds() {
  return this.device.getDataValue('groupPlayerIds').tokenize(',')
}
void setGroupPlayerIds(List<String> groupPlayerIds) {
  this.device.updateDataValue('groupPlayerIds', groupPlayerIds.join(','))
  this.device.updateDataValue('groupIds', groupPlayerIds.join(','))
  this.device.sendEvent(name: 'isGrouped', value: groupPlayerIds.size() > 1 ? 'on' : 'off')
  this.device.sendEvent(name: 'groupMemberCount', value: groupPlayerIds.size())
  if(isGroupedAndCoordinator()) {
    logTrace('Updating group device with new group membership information')
    parentUpdateGroupDevices(getId(), groupPlayerIds)
  }
}

List<String> getGroupFollowerDNIs() {
  return getGroupPlayerIds() - [getId()]
}

String getGroupMemberNames() {
  return this.device.currentValue('groupMemberNames')
}
void setGroupMemberNames(List<String> groupPlayerNames) {
  this.device.sendEvent(name: 'groupMemberNames' , value: groupPlayerNames.toString())
}

void setAlbumArtURI(String albumArtURI, Boolean isPlayingLocalTrack) {
  String uri = '<br>'
  if(albumArtURI.startsWith('/') && !isPlayingLocalTrack) {
    uri += "<img src=\"${getLocalUpnpUrl()}${albumArtURI}\" width=\"200\" height=\"200\" >"
  } else if(!isPlayingLocalTrack) {
    uri += "<img src=\"${albumArtURI}\" width=\"200\" height=\"200\" >"
  }
  setAlbumArtURI(uri)
  if(isGroupedAndCoordinator()) {parent?.setAlbumArtURI(getGroupFollowerDNIs(), uri)}
}
void setAlbumArtURI(String uri) { sendEvent([name:'albumArtURI', value: uri]) }
String getAlbumArtURI() {
  return this.device.currentValue('albumArtURI',true)
}

void setCurrentFavorite(String foundFavImageUrl, String foundFavId, String foundFavName, Boolean isFav) {
  String value = 'No favorite playing'
  if((isFav) && foundFavImageUrl?.startsWith('/')) {
    value = "Favorite #${foundFavId} ${foundFavName} <br><img src=\"${getLocalUpnpUrl()}${foundFavImageUrl}\" width=\"200\" height=\"200\" >"
  } else if((isFav) && !foundFavImageUrl) {
    value = "Favorite #${foundFavId} ${foundFavName}"
  } else if(isFav) {
    value = "Favorite #${foundFavId} ${foundFavName} <br><img src=\"${foundFavImageUrl}\" width=\"200\" height=\"200\" >"
  }
  setCurrentFavorite(value)
  if(isGroupedAndCoordinator()) {parent?.setCurrentFavorite(getGroupFollowerDNIs(), value)}
}
void setCurrentFavorite(String uri) { sendEvent([name:'currentFavorite', value: uri]) }
String getCurrentFavorite() {
  return this.device.currentValue('currentFavorite', true)
}

void setStatusTransportStatus(String status) {
  sendEvent(name: 'status', value: status)
  sendEvent(name: 'transportStatus', value: status)
  if(isGroupedAndCoordinator()) {parent?.setStatusTransportStatus(getGroupFollowerDNIs(), status)}
}
String getTransportStatus() {
  return this.device.currentValue('transportStatus')
}

Integer getPlayerVolume() {
  return this.device.currentValue('volume') as Integer
}
void setPlayerVolume(Integer volume) {
  sendEvent(name:'level', value: volume)
  sendEvent(name:'volume', value: volume)
  if(volume > 0) { state.restoreLevelAfterUnmute = volume }
}
void setBalance(Integer balance) {
  sendEvent(name: 'balance', value: balance)
}
String getMuteState() {
  return this.device.currentValue('mute')
}
void setMuteState(String muted) {
  String previousMutedState = getMuteState() != null ? getMuteState() : 'unmuted'
  if(muted == 'unmuted' && previousMutedState == 'muted' && enableAirPlayUnmuteVolumeFix) {
    logDebug("Restoring volume after unmute event to level: ${state.restoreLevelAfterUnmute}")
    setLevel(state.restoreLevelAfterUnmute as Integer)
  }
  sendEvent(name:'mute', value: muted)
}

void setBassState(Integer bass) {
  sendEvent(name:'bass', value: bass)
}
void setTrebleState(Integer treble) {
  sendEvent(name:'treble', value: treble)
}
void setLoudnessState(String loudness) {
  sendEvent(name:'loudness', value: loudness == '1' ? 'on' : 'off')
}

void setPlayMode(String playMode){
  switch(playMode) {
    case 'NORMAL':
      setCurrentRepeatOneMode('off')
      setCurrentRepeatAllMode('off')
      setCurrentShuffleMode('off')
    break
    case 'REPEAT_ALL':
      setCurrentRepeatOneMode('off')
      setCurrentRepeatAllMode('on')
      setCurrentShuffleMode('off')
    break
    case 'REPEAT_ONE':
      setCurrentRepeatOneMode('on')
      setCurrentRepeatAllMode('off')
      setCurrentShuffleMode('off')
    break
    case 'SHUFFLE_NOREPEAT':
      setCurrentRepeatOneMode('off')
      setCurrentRepeatAllMode('off')
      setCurrentShuffleMode('on')
    break
    case 'SHUFFLE':
      setCurrentRepeatOneMode('off')
      setCurrentRepeatAllMode('on')
      setCurrentShuffleMode('on')
    break
    case 'SHUFFLE_REPEAT_ONE':
      setCurrentRepeatOneMode('on')
      setCurrentRepeatAllMode('off')
      setCurrentShuffleMode('on')
    break
  }
  if(isGroupedAndCoordinator()) {parent?.setPlayMode(getGroupFollowerDNIs(), playMode)}
}

void setCurrentRepeatOneMode(String value) {
  sendEvent(name:'currentRepeatOneMode', value: value)
  if(getCreateRepeatOneChildDevice()) { getRepeatOneControlChild().sendEvent(name:'switch', value: value) }
}
String getCurrentRepeatOneMode() { return this.device.currentValue('currentRepeatOneMode') }

void setCurrentRepeatAllMode(String value) {
  sendEvent(name:'currentRepeatAllMode', value: value)
  if(getCreateRepeatAllChildDevice()) { getRepeatAllControlChild().sendEvent(name:'switch', value: value) }
}
String getCurrentRepeatAllMode() { return this.device.currentValue('currentRepeatAllMode') }

void setCurrentShuffleMode(String value) {
  sendEvent(name:'currentShuffleMode', value: value)
  if(getCreateShuffleChildDevice()) { getShuffleControlChild().sendEvent(name:'switch', value: value) }
}
String getCurrentShuffleMode() { return this.device.currentValue('currentShuffleMode') }

void setCrossfadeMode(String currentCrossfadeMode) {
  sendEvent(name:'currentCrossfadeMode', value: currentCrossfadeMode)
  if(getCreateCrossfadeChildDevice()) { getCrossfadeControlChild().sendEvent(name:'switch', value: currentCrossfadeMode) }
  if(isGroupedAndCoordinator()) {parent?.setCrossfadeMode(getGroupFollowerDNIs(), currentCrossfadeMode)}
}
String getCrossfadeMode() { return this.device.currentValue('currentCrossfadeMode') }

void setCurrentTrackDuration(String currentTrackDuration){
  if(!getDisableArtistAlbumTrackEvents()) {sendEvent(name:'currentTrackDuration', value: currentTrackDuration)}
  if(isGroupedAndCoordinator()) {parent?.setCurrentTrackDuration(getGroupFollowerDNIs(), currentTrackDuration)}
}
String getCurrentTrackDuration() { return this.device.currentValue('currentTrackDuration') }

void setCurrentArtistName(String currentArtistName) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'currentArtistName', value: currentArtistName ?: 'Not Available') }
  if(isGroupedAndCoordinator()) { parent?.setCurrentArtistName(getGroupFollowerDNIs(), currentArtistName) }
}
String getCurrentArtistName() { return this.device.currentValue('currentArtistName') }

void setCurrentAlbumName(String currentAlbumName) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'currentAlbumName', value: currentAlbumName ?: 'Not Available') }
  if(isGroupedAndCoordinator()) { parent?.setCurrentAlbumName(getGroupFollowerDNIs(), currentAlbumName) }
}
String getCurrentAlbumName() { return this.device.currentValue('currentAlbumName') }

void setCurrentTrackName(String currentTrackName) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'currentTrackName', value: currentTrackName ?: 'Not Available') }
  if(isGroupedAndCoordinator()) { parent?.setCurrentTrackName(getGroupFollowerDNIs(), currentTrackName) }
}
String getCurrentTrackName() { return this.device.currentValue('currentTrackName') }

void setCurrentTrackNumber(Integer currentTrackNumber) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'currentTrackNumber', value: currentTrackNumber ?: 0) }
  if(isGroupedAndCoordinator()) { parent?.setCurrentTrackNumber(getGroupFollowerDNIs(), currentTrackNumber) }
}
Integer getCurrentTrackNumber() { return this.device.currentValue('currentTrackNumber') }

void setTrackDescription(String trackDescription) {
  String prevTrackDescription = getTrackDescription()
  sendEvent(name: 'trackDescription', value: trackDescription)

  if(getIsGroupCoordinator() && prevTrackDescription != trackDescription) {getPlaybackMetadataStatus()}
  if(isGroupedAndCoordinator()) {
    parent?.setTrackDescription(getGroupFollowerDNIs(), trackDescription)
  }
}
String getTrackDescription() { return this.device.currentValue('trackDescription') }

void setTrackDataEvents(Map trackData) {
  if(!getDisableTrackDataEvents()) {
    trackData['level'] = this.device.currentValue('level')
    trackData['mute'] = this.device.currentValue('mute')
    sendEvent(name: 'trackData', value: JsonOutput.toJson(trackData))
  }
  if(isGroupedAndCoordinator()) { parent?.setTrackDataEvents(getGroupFollowerDNIs(), trackData) }
}
String getTrackDataEvents() {return this.device.currentValue('trackData')}

void setNextArtistName(String nextArtistName) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'nextArtistName', value: nextArtistName ?: 'Not Available') }
  if(isGroupedAndCoordinator()) { parent?.setNextArtistName(getGroupFollowerDNIs(), nextArtistName) }
}
String getNextArtistName() { return this.device.currentValue('nextArtistName') }

void setNextAlbumName(String nextAlbumName) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'nextAlbumName', value: nextAlbumName ?: 'Not Available') }
  if(isGroupedAndCoordinator()) { parent?.setNextAlbumName(getGroupFollowerDNIs(), nextAlbumName) }
}
String getNextAlbumName() { return this.device.currentValue('nextAlbumName') }

void setNextTrackName(String nextTrackName) {
  if(!getDisableArtistAlbumTrackEvents()) { sendEvent(name:'nextTrackName', value: nextTrackName ?: 'Not Available') }
  if(isGroupedAndCoordinator()) { parent?.setNextTrackName(getGroupFollowerDNIs(), nextTrackName) }
}
String getNextTrackName() { return this.device.currentValue('nextTrackName') }

@CompileStatic
List<Map> getCurrentPlayingStatesForGroup() {
  List currentStates = []
  currentStates.add([name: 'albumArtURI', value: getAlbumArtURI()])
  currentStates.add([name: 'status', value: getTransportStatus()])
  currentStates.add([name: 'transportStatus', value: getTransportStatus()])
  currentStates.add([name: 'currentRepeatOneMode', value: getCurrentRepeatOneMode()])
  currentStates.add([name: 'currentRepeatAllMode', value: getCurrentRepeatAllMode()])
  currentStates.add([name: 'currentShuffleMode', value: getCurrentShuffleMode()])
  currentStates.add([name: 'currentCrossfadeMode', value: getCrossfadeMode()])
  currentStates.add([name: 'currentTrackDuration', value: getCurrentTrackDuration()])
  currentStates.add([name: 'currentArtistName', value: getCurrentArtistName()])
  currentStates.add([name: 'currentAlbumName', value: getCurrentAlbumName()])
  currentStates.add([name: 'currentTrackName', value: getCurrentTrackName()])
  currentStates.add([name: 'trackDescription', value: getTrackDescription()])
  currentStates.add([name: 'trackData', value: getTrackDataEvents()])
  currentStates.add([name: 'currentFavorite', value: getCurrentFavorite()])
  return currentStates
}

@CompileStatic
ConcurrentLinkedQueue<Map> getAudioClipQueue() {
  audioClipQueueInitialization()
  return audioClipQueue[getId()]
}

@CompileStatic
Integer getAudioClipQueueLength() {
  audioClipQueueInitialization()
  return getAudioClipQueue().size() as Integer
}

@CompileStatic
Boolean getAudioClipQueueIsEmpty() {
  audioClipQueueInitialization()
  return getAudioClipQueue().size() == 0
}
// =============================================================================
// End Getters and Setters
// =============================================================================

// =============================================================================
// Websocket Connection and Initialization
// =============================================================================
void webSocketStatus(String message) {
  if(message == 'failure: null') { this.device.updateDataValue('websocketStatus', 'closed')}
  if(message == 'status: open') { this.device.updateDataValue('websocketStatus', 'open')}
  if(message == 'failure: connect timed out') { this.device.updateDataValue('websocketStatus', 'connect timed out')}
  logTrace("Socket Status: ${message}")
}

void wsConnect() {
  Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
  interfaces.webSocket.connect(this.device.getDataValue('websocketUrl'), headers: headers, ignoreSSLIssues: true)
  unschedule('renewWebsocketConnection')
  runIn(RESUB_INTERVAL-100, 'renewWebsocketConnection')
}

void wsClose() {
  interfaces.webSocket.close()
}

void sendWsMessage(String message) {
  if(this.device.getDataValue('websocketStatus') != 'open') { wsConnect() }
  interfaces.webSocket.sendMessage(message)
}

void initializeWebsocketConnection() {
  wsConnect()
  if(getRightChannelChild()) {subscribeToFavorites()}
  subscribeToPlaylists()
  subscribeToAudioClip()
  subscribeToGroups()
}

void renewWebsocketConnection() {
  initializeWebsocketConnection()
}
// =============================================================================
// End Websocket Connection and Initialization
// =============================================================================



// =============================================================================
// Websocket Subscriptions and polling
// =============================================================================
@CompileStatic
void subscribeToGroups() {
  Map command = [
    'namespace':'groups',
    'command':'subscribe',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToGroups')
  sendWsMessage(json)
}

@CompileStatic
void subscribeToFavorites() {
  Map command = [
    'namespace':'favorites',
    'command':'subscribe',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToFavorites')
  sendWsMessage(json)
}

@CompileStatic
void subscribeToPlaylists() {
  Map command = [
    'namespace':'playlists',
    'command':'subscribe',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToPlaylists')
  sendWsMessage(json)
}

@CompileStatic
void subscribeToAudioClip() {
  Map command = [
    'namespace':'audioClip',
    'command':'subscribe',
    'playerId':"${getId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToAudioClip')
  sendWsMessage(json)
}

@CompileStatic
void subscribeToPlayback(String groupId) {
  Map command = [
    'namespace':'playback',
    'command':'subscribe',
    'groupId':"${groupId}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToPlayback')
  sendWsMessage(json)
}

@CompileStatic
void subscribeToPlaybackMetadata(String groupId) {
  Map command = [
    'namespace':'playbackMetadata',
    'command':'subscribe',
    'groupId':"${groupId}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToPlaybackMetadata')
  sendWsMessage(json)
}

@CompileStatic
void getPlaybackMetadataStatus() {
  Map command = [
    'namespace':'playbackMetadata',
    'command':'getMetadataStatus',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void subscribeToFavorites(String groupId) {
  Map command = [
    'namespace':'playbackMetadata',
    'command':'subscribe',
    'groupId':"${groupId}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  logDebug('subscribeToFavorites')
  sendWsMessage(json)
}
// =============================================================================
// End Websocket Subscriptions and polling
// =============================================================================



// =============================================================================
// Websocket Commands
// =============================================================================
@CompileStatic
void getHouseholdsWS() {
  Map command = [
    'command':'getHouseholds'
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerPlay() {
  Map command = [
    'namespace':'playback',
    'command':'play',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerPause() {
  Map command = [
    'namespace':'playback',
    'command':'pause',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerStop() {
  Map command = [
    'namespace':'playback',
    'command':'stop',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSkipToNextTrack() {
  Map command = [
    'namespace':'playback',
    'command':'skipToNextTrack',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSkipToPreviousTrack() {
  Map command = [
    'namespace':'playback',
    'command':'skipToPreviousTrack',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetPlayModes(Map playModes) {
  Map command = [
    'namespace':'playback',
    'command':'setPlayModes',
    'groupId':"${getGroupId()}"
  ]
  Map args = [ 'playModes': playModes ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetPlayerVolume(Integer volume) {
  Map command = [
    'namespace':'playerVolume',
    'command':'setVolume',
    'playerId':"${getId()}"
  ]
  Map args = [
    'volume': volume
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetPlayerMute(Boolean muted) {
  Map command = [
    'namespace':'playerVolume',
    'command':'setMute',
    'playerId':"${getId()}"
  ]
  Map args = [
    'muted': muted
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetPlayerRelativeVolume(Integer volumeDelta) {
  Map command = [
    'namespace':'playerVolume',
    'command':'setRelativeVolume',
    'playerId':"${getId()}"
  ]
  Map args = [
    'volumeDelta': volumeDelta
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetGroupVolume(Integer volume) {
  Map command = [
    'namespace':'groupVolume',
    'command':'setVolume',
    'groupId':"${getGroupId()}"
  ]
  Map args = [
    'volume': volume
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetGroupMute(Boolean muted) {
  Map command = [
    'namespace':'groupVolume',
    'command':'setMute',
    'groupId':"${getGroupId()}"
  ]
  Map args = [
    'muted': muted
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerSetGroupRelativeVolume(Integer volumeDelta) {
  Map command = [
    'namespace':'groupVolume',
    'command':'setRelativeVolume',
    'groupId':"${getGroupId()}"
  ]
  Map args = [
    'volumeDelta': volumeDelta
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void getFavorites() {
  Map command = [
    'namespace':'favorites',
    'command':'getFavorites',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerLoadFavorite(String favoriteId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  Map command = [
    'namespace':'favorites',
    'command':'loadFavorite',
    'groupId':"${getGroupId()}"
  ]
  Map args = [
    'favoriteId': favoriteId,
    'action': action,
    'playModes': [
      'repeat': repeat,
      'repeatOne': repeatOne,
      'shuffle': shuffle,
      'crossfade': crossfade
    ],
    'playOnCompletion': playOnCompletion
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerLoadAudioClipHighPriority(String uri = null, BigDecimal volume = null) {
  logTrace('playerLoadAudioClipHighPriority')
  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  Map args = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos', 'priority': 'HIGH']
  if(uri) {args.streamUrl = uri}
  if(volume) {args.volume = volume + getTTSBoostAmount()}
  else {args.volume = getPlayerVolume() + getTTSBoostAmount()}
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [ leftChannel: json ]
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  sendAudioClipHighPriority(audioClip)
}

void sendAudioClipHighPriority(Map clipMessage) {
  if(!clipMessage) {return}
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  if(clipMessage.rightChannel) {
    sendWsMessage(clipMessage.leftChannel)
    rightChannel.playerLoadAudioClip(clipMessage.rightChannel)
  } else {
    sendWsMessage(clipMessage.leftChannel)
  }
  atomicState.audioClipPlaying = true
}

@CompileStatic
void playerLoadAudioClip(String uri = null, BigDecimal volume = null, Boolean chimeBeforeTTS = getChimeBeforeTTS()) {
  logTrace('playerLoadAudioClip')
  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  Map args = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  if(uri) {args.streamUrl = uri}
  if(volume) {args.volume = volume + getTTSBoostAmount()}
  else {args.volume = getPlayerVolume() + getTTSBoostAmount()}
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [ leftChannel: json ]
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  if(getAudioClipQueueIsEmpty() && chimeBeforeTTS) {playerLoadAudioClipChime(volume)}
  enqueueAudioClip(audioClip)
  logTrace('Enqueued')
}


@CompileStatic
void playerLoadAudioClipChime(BigDecimal volume = null) {
  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  Map args = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos', "clipType": "CHIME"]
  if(volume) {args.volume = volume}
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [ leftChannel: json ]
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  enqueueAudioClip(audioClip)
}

void enqueueAudioClip(Map clipMessage) {
  logTrace('enqueueAudioClip')
  Boolean queueWasEmpty = getAudioClipQueueIsEmpty()
  getAudioClipQueue().add(clipMessage)
  if(queueWasEmpty && atomicState.audioClipPlaying == false) {dequeueAudioClip()}
  else { subscribeToAudioClip() }
}
//The graphic and typographic operators know this well, in reality all the professions dealing with the universe of communication have a stable relationship with these words, but what is it? Lorem ipsum is a dummy text without any sense.  It is a sequence of Latin words that, as they are positioned, do not form sentences with a complete sense, but give life to a test text useful to fill spaces that will subsequently be occupied from ad hoc texts composed by communication professionals.  It is certainly the most famous placeholder text even if there are different versions distinguishable from the order in which the Latin words are repeated.  Lorem ipsum contains the typefaces more in use, an aspect that allows you to have an overview of the rendering of the text in terms of font choice and font size .
void dequeueAudioClip() {
  logTrace('dequeueAudioClip')
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  Map clipMessage = getAudioClipQueue().poll()
  if(!clipMessage) {return}
  if(clipMessage.rightChannel) {
    sendWsMessage(clipMessage.leftChannel)
    rightChannel.playerLoadAudioClip(clipMessage.rightChannel)
  } else {
    sendWsMessage(clipMessage.leftChannel)
  }
  atomicState.audioClipPlaying = true
}
// =============================================================================
// End Websocket Commands
// =============================================================================

// =============================================================================
// Grouping and Ungrouping
// =============================================================================
@CompileStatic
void playerGetGroupsFull() {
  Map command = [
    'namespace':'groups',
    'command':'getGroups',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = ['includeDeviceInfo': true]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerCreateNewGroup() {
  List playerIds = ["${getId()}".toString()]
  playerCreateGroup(playerIds)
}

@CompileStatic
void playerCreateGroup(List<String> playerIds) {
  Map command = [
    'namespace':'groups',
    'command':'createGroup',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [
    'musicContextGroupId': null,
    'playerIds': playerIds
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerModifyGroupMembers(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = []) {
  Map command = [
    'namespace':'groups',
    'command':'modifyGroupMembers',
    'groupId':"${getGroupId()}"
  ]
  Map args = [
    'playerIdsToAdd': playerIdsToAdd,
    'playerIdsToRemove': playerIdsToRemove
  ]
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}

@CompileStatic
void playerEvictUnlistedPlayers(List<String> playerIdsToKeep) {
  List<String> playerIdsToRemove = getGroupPlayerIds() - playerIdsToKeep
  playerModifyGroupMembers(playerIdsToKeep, playerIdsToRemove)
}

// @CompileStatic
// void playerBecomeCoordinatorOfGroup(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = []) {
//   Map command = [
//     'namespace':'groups',
//     'command':'modifyGroupMembers',
//     'groupId':"${getGroupId()}"
//   ]
//   Map args = [
//     'playerIdsToAdd': playerIdsToAdd,
//     'playerIdsToRemove': playerIdsToRemove
//   ]
//   String json = JsonOutput.toJson([command,args])
//   logTrace(json)
//   sendWsMessage(json)
// }



// =============================================================================
// Websocket Incoming Data Processing
// =============================================================================
void processWebsocketMessage(String message) {
  String prettyJson = JsonOutput.prettyPrint(message)
  List<Map> json = slurper.parseText(message)
  // logTrace(prettyJson)

  Map eventType = json[0]
  Map eventData = json[1]

  //Process subscriptions
  if(eventType?.type == 'none' && eventType?.response == 'subscribe') {
    logDebug("Subscription to ${eventType?.namespace} ${eventType?.success ? 'was sucessful' : 'failed'}")
  }

  //Process groups
  if(eventType?.type == 'groups' && eventType?.name == 'groups') {
    List<Map> groups = eventData.groups
    Map group = groups.find{ it?.playerIds.contains(getId()) }

    setGroupId(group.id)
    setGroupName(group.name)
    setGroupPlayerIds(group.playerIds)
    setGroupCoordinatorId(group.coordinatorId)

    List<Map> players = eventData.players
    String coordinatorName = players.find{it?.id == group.coordinatorId}?.name
    setGroupCoordinatorName(coordinatorName)

    List<String> groupMemberNames = group.playerIds.collect{pid -> players.find{player-> player?.id == pid}?.name}
    setGroupMemberNames(groupMemberNames)

    if(hasSecondaries()) {
      String rightChannelId = players.find{it?.id == getId()}?.zoneInfo?.members.find{it?.channelMap.contains('RF') }?.id
      setRightChannelRincon(rightChannelId)
    }

    // logError("Right channel: ${rightChannelId} should be: RINCON_542A1B5D6A7001400")
  }

  //Process groupCoordinatorChanged
  if(eventType?.type == 'groupCoordinatorChanged' && eventType?.name == 'groupCoordinatorChanged') {
    if(group?.groupStatus == 'GROUP_STATUS_UPDATED') {
      logDebug("Group name: ${group.name}")
      logDebug("Group coordinatorId: ${group.coordinatorId}")
      logDebug("Group playerIds: ${group.playerIds}")
      logDebug("Group Id: ${group.id}")
    }
  }

  if(eventType?.type == 'versionChanged' && eventType?.name == 'favoritesVersionChange') {
    getFavorites()
  }

  if(eventType?.type == 'favoritesList' && eventType?.response == 'getFavorites' && eventType?.success == true) {
    List respData = eventData?.items
    Map formatted = respData.collectEntries() { [it.id, [name:it.name, imageUrl:it?.imageUrl]] }
    String html = '<!DOCTYPE html><html><body><ul>'
    state.remove('favorites')
    formatted.each(){fav ->
      String albumArtURI = fav.value.imageUrl
      String s = "Favorite #${fav.key} ${fav.value.name}"

      if(albumArtURI == null) {
        html += "<li>${s}: No Image Art Available</li>"
      } else if(albumArtURI.startsWith('/')) {
        html += "<li>${s}: <br><img src=\"${this.device.getDataValue('localUpnpUrl')}${albumArtURI}\" width=\"200\" height=\"200\" ></li>"
      } else {
        html += "<li>${s}: <br><img src=\"${albumArtURI}\" width=\"200\" height=\"200\" ></li>"
      }
    }
    html += '</ul></body></html>'
    if(getCreateFavoritesChildDevice()) {
      ChildDeviceWrapper favDev = getFavoritesChild()
      favDev.setFavorites(html)
    }
    InstalledAppWrapper p = this.getParent()
    if(p.getSetting('favMatching')) {
      Map favs = [:]
      respData.each{
        if(it?.resource?.id?.objectId && it?.resource?.id?.serviceId && it?.resource?.id?.accountId) {
          String objectId = it?.resource?.id?.objectId
          if(objectId) {
            List tok = objectId.tokenize(':')
            if(tok.size >= 1) { objectId = tok[1] }
          }
          String serviceId = it?.resource?.id?.serviceId
          String accountId = it?.resource?.id?.accountId
          String universalMusicObjectId = "${objectId}${serviceId}${accountId}".toString()
          favs[universalMusicObjectId] = [id:it?.id, name:it?.name, imageUrl:it?.imageUrl, service: it?.service?.name]
        } else if(it?.imageUrl) {
          String universalMusicObjectId = ("${it?.imageUrl}".toString()).split('&v=')[0]
          favs[universalMusicObjectId] = [id:it?.id, name:it?.name, imageUrl:it?.imageUrl, service: it?.service?.name]
        }
      }
      p.setFavorites(favs)
    }
  }

  if(eventType?.type == 'audioClipStatus' && eventType?.name == 'audioClipStatus') {
    logTrace(prettyJson)
    if(eventData?.audioClips.find{it?.status == 'DONE'}) {
      atomicState.audioClipPlaying = false
      dequeueAudioClip()
    }
    else if(eventData?.audioClips.find{it?.status == 'ACTIVE'}) {atomicState.audioClipPlaying = true}
    else if(eventData?.audioClips.find{it?.status == 'ERROR'}) {atomicState.audioClipPlaying = false}
  }

  if(eventType?.type == 'globalError' && eventType?.success == false) {
    if(eventType?.namespace == 'playback' && eventType?.response == 'stop') {
      if(eventData?.errorCode == 'ERROR_UNSUPPORTED_COMMAND') {
        logTrace("Stop command unavailable for current stream, issuing pause command...")
        playerPause()
      }
    }
  }

  if(eventType?.type == 'playbackStatus' && eventType?.namespace == 'playback') {
    if(eventData?.playbackState == 'PLAYBACK_STATE_PLAYING') {
      if(getIsGroupCoordinator()) {getPlaybackMetadataStatus()}
    }
  }

  if(eventType?.type == 'metadataStatus' && eventType?.namespace == 'playbackMetadata') {
    runIn(1, 'isFavoritePlaying', [overwrite: true, data: eventData ])
  }
}

void isFavoritePlaying(Map data) {
  parent?.isFavoritePlaying(this.device, data)
}