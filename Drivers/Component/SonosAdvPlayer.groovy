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
    version: '0.3.23',
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

  command 'initializeWebsocketConnection'

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
      input name: 'volumeAdjustAmount', title: 'Volume up/down Adjust default:(+/- 5%)', type: 'enum', required: false, defaultValue: 5,
      options: [1:'+/- 1%', 2:'+/- 2%', 3:'+/- 3%', 4:'+/- 4%', 5:'+/- 5%', 10:'+/- 10%', 20:'+/- 20%']
      input 'disableArtistAlbumTrackEvents', 'bool', title: 'Disable artist, album, and track events', required: false, defaultValue: false
      input 'createCrossfadeChildDevice', 'bool', title: 'Create child device for crossfade control?', required: false, defaultValue: false
      input 'createShuffleChildDevice', 'bool', title: 'Create child device for shuffle control?', required: false, defaultValue: false
      input 'createRepeatOneChildDevice', 'bool', title: 'Create child device for "repeat one" control?', required: false, defaultValue: false
      input 'createRepeatAllChildDevice', 'bool', title: 'Create child device for "repeat all" control?', required: false, defaultValue: false
      input 'createBatteryStatusChildDevice', 'bool', title: 'Create child device for battery status? (portable speakers only)', required: false, defaultValue: false
      input 'createFavoritesChildDevice', 'bool', title: 'Create child device for favorites?', required: false, defaultValue: false
    }
  }
}

// =============================================================================
// Constants
// =============================================================================
@Field private final Integer RESUB_INTERVAL = 7200

// =============================================================================
// Initialize and Configure
// =============================================================================
void initialize() { configure() }
void configure() {
  if(createCrossfadeChildDevice) {createRemoveCrossfadeChildDevice(createCrossfadeChildDevice)}
  if(createShuffleChildDevice) {createRemoveShuffleChildDevice(createShuffleChildDevice)}
  if(createRepeatOneChildDevice) {createRemoveRepeatOneChildDevice(createRepeatOneChildDevice)}
  if(createRepeatAllChildDevice) {createRemoveRepeatAllChildDevice(createRepeatAllChildDevice)}
  if(createMuteChildDevice) {createRemoveMuteChildDevice(createMuteChildDevice)}
  if(createBatteryStatusChildDevice) {createRemoveBatteryStatusChildDevice(createBatteryStatusChildDevice)}
  if(createFavoritesChildDevice) {createRemoveFavoritesChildDevice(createFavoritesChildDevice)}
  if(disableTrackDataEvents) { clearTrackDataEvent() }
  if(disableArtistAlbumTrackEvents) { clearCurrentNextArtistAlbumTrackData() }
  migrationCleanup()
  runIn(5, 'secondaryConfiguration')
}

void secondaryConfiguration() {
  parent?.componentUpdatePlayerInfo(this.device)
  initializeWebsocketConnection()
  runIn(10, 'subscribeToEvents')
}

void migrationCleanup() {
  unschedule('resubscribeToGMEvents')
}

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

void ungroupPlayer() { parent?.componentUngroupPlayerLocal(this.device) }

void playText(String text, BigDecimal volume = null) { devicePlayTextNoRestore(text, volume) }
void playTextAndRestore(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndResume(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void setTrack(String uri) { parent?.componentSetStreamUrlLocal(this.device, uri, volume) }
void playTrack(String uri, BigDecimal volume = null) { parent?.componentLoadStreamUrlLocal(this.device, uri, volume) }
void playTrackAndRestore(String uri, BigDecimal volume = null) { playerLoadAudioClip(uri, volume) }
void playTrackAndResume(String uri, BigDecimal volume = null) { playerLoadAudioClip(uri, volume) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  playerLoadAudioClip(textToSpeech(text, voice).uri, volume)
}

void devicePlayTextNoRestore(String text, BigDecimal volume = null, String voice = null) {
  parent?.componentPlayTextNoRestoreLocal(this.device, text, volume, voice)
}

void devicePlayTrack(String uri, BigDecimal volume = null) {
  parent?.componentLoadStreamUrlLocal(this.device, uri, volume)
}

void mute(){ playerSetPlayerMute(true) }
void unmute(){ playerSetPlayerMute(false) }
void setLevel(BigDecimal level) { playerSetPlayerVolume(level) }
void setVolume(BigDecimal level) { setLevel(level) }
void setTreble(BigDecimal level) { parent?.componentSetTrebleLocal(this.device, level)}
void setBass(BigDecimal level) { parent?.componentSetBassLocal(this.device, level)}
void setLoudness(String mode) { parent?.componentSetLoudnessLocal(this.device, mode == 'on')}
void setBalance(BigDecimal level) { parent?.componentSetBalanceLocal(this.device, level)}

void muteGroup(){
  if(this.device.currentState('isGrouped')?.value == 'on') {playerSetGroupMute(true) }
  else { playerSetPlayerMute(true) }
}
void unmuteGroup(){
  if(this.device.currentState('isGrouped')?.value == 'on') {playerSetGroupMute(false) }
  else { playerSetPlayerMute(false) }
}
void setGroupVolume(BigDecimal level) {
  if(this.device.currentState('isGrouped')?.value == 'on') { playerSetGroupVolume(level) }
  else { playerSetPlayerVolume(level)  }
}
void setGroupLevel(BigDecimal level) { setGroupVolume(level) }
void setGroupMute(String mode) {
  logDebug("Setting group mute to ${mode}")
  if(mode == 'muted') { muteGroup() }
  else { unmuteGroup() }
}
void groupVolumeUp() {
  if(this.device.currentState('isGrouped')?.value == 'on') { playerSetGroupRelativeVolume((volumeAdjustAmount as Integer)) }
  else { playerSetPlayerRelativeVolume((volumeAdjustAmount as Integer)) }
}
void groupVolumeDown() {
  if(this.device.currentState('isGrouped')?.value == 'on') { playerSetGroupRelativeVolume(-(volumeAdjustAmount as Integer)) }
  else { playerSetPlayerRelativeVolume(-(volumeAdjustAmount as Integer)) }
}

void volumeUp() { playerSetPlayerRelativeVolume((volumeAdjustAmount as Integer)) }
void volumeDown() { playerSetPlayerRelativeVolume(-(volumeAdjustAmount as Integer)) }

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
  logDebug("Called")
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
  parent?.componentLoadFavoriteFullLocal(this.device, favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)
}



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

void setPlayMode(String playMode){
  switch(playMode) {
    case 'NORMAL':
      sendEvent(name:'currentRepeatOneMode', value: 'off')
      sendEvent(name:'currentRepeatAllMode', value: 'off')
      sendEvent(name:'currentShuffleMode', value: 'off')
      if(createRepeatOneChildDevice) { getRepeatOneControlChild().sendEvent(name:'switch', value: 'off') }
      if(createRepeatAllChildDevice) { getRepeatAllControlChild().sendEvent(name:'switch', value: 'off') }
      if(createShuffleChildDevice) { getShuffleControlChild().sendEvent(name:'switch', value: 'off') }
    break
    case 'REPEAT_ALL':
      sendEvent(name:'currentRepeatOneMode', value: 'off')
      sendEvent(name:'currentRepeatAllMode', value: 'on')
      sendEvent(name:'currentShuffleMode', value: 'off')
      if(createRepeatOneChildDevice) { getRepeatOneControlChild().sendEvent(name:'switch', value: 'off') }
      if(createRepeatAllChildDevice) { getRepeatAllControlChild().sendEvent(name:'switch', value: 'off') }
      if(createShuffleChildDevice) { getShuffleControlChild().sendEvent(name:'switch', value: 'off') }
    break
    case 'REPEAT_ONE':
      sendEvent(name:'currentRepeatOneMode', value: 'on')
      sendEvent(name:'currentRepeatAllMode', value: 'off')
      sendEvent(name:'currentShuffleMode', value: 'off')
      if(createRepeatOneChildDevice) { getRepeatOneControlChild().sendEvent(name:'switch', value: 'on') }
      if(createRepeatAllChildDevice) { getRepeatAllControlChild().sendEvent(name:'switch', value: 'off') }
      if(createShuffleChildDevice) { getShuffleControlChild().sendEvent(name:'switch', value: 'off') }
    break
    case 'SHUFFLE_NOREPEAT':
      sendEvent(name:'currentRepeatOneMode', value: 'off')
      sendEvent(name:'currentRepeatAllMode', value: 'off')
      sendEvent(name:'currentShuffleMode', value: 'on')
      if(createRepeatOneChildDevice) { getRepeatOneControlChild().sendEvent(name:'switch', value: 'off') }
      if(createRepeatAllChildDevice) { getRepeatAllControlChild().sendEvent(name:'switch', value: 'off') }
      if(createShuffleChildDevice) { getShuffleControlChild().sendEvent(name:'switch', value: 'on') }
    break
    case 'SHUFFLE':
      sendEvent(name:'currentRepeatOneMode', value: 'off')
      sendEvent(name:'currentRepeatAllMode', value: 'on')
      sendEvent(name:'currentShuffleMode', value: 'on')
      if(createRepeatOneChildDevice) { getRepeatOneControlChild().sendEvent(name:'switch', value: 'off') }
      if(createRepeatAllChildDevice) { getRepeatAllControlChild().sendEvent(name:'switch', value: 'on') }
      if(createShuffleChildDevice) { getShuffleControlChild().sendEvent(name:'switch', value: 'on') }
    break
    case 'SHUFFLE_REPEAT_ONE':
      sendEvent(name:'currentRepeatOneMode', value: 'on')
      sendEvent(name:'currentRepeatAllMode', value: 'off')
      sendEvent(name:'currentShuffleMode', value: 'on')
      if(createRepeatOneChildDevice) { getRepeatOneControlChild().sendEvent(name:'switch', value: 'on') }
      if(createRepeatAllChildDevice) { getRepeatAllControlChild().sendEvent(name:'switch', value: 'off') }
      if(createShuffleChildDevice) { getShuffleControlChild().sendEvent(name:'switch', value: 'on') }
    break
  }
}

void setCrossfadeMode(String currentCrossfadeMode) {
  sendEvent(name:'currentCrossfadeMode', value: currentCrossfadeMode)
  if(createCrossfadeChildDevice) { getCrossfadeControlChild().sendEvent(name:'switch', value: currentCrossfadeMode) }
}

void setCurrentTrackDuration(String currentTrackDuration){
  if(disableArtistAlbumTrackEvents) {return}
  sendEvent(name:'currentTrackDuration', value: currentTrackDuration)
}

void setCurrentArtistAlbumTrack(String currentArtistName, String currentAlbumName, String currentTrackName, Integer currentTrackNumber) {
  if(disableArtistAlbumTrackEvents) {return}
  sendEvent(name:'currentArtistName', value: currentArtistName ?: 'Not Available')
  sendEvent(name:'currentAlbumName',  value: currentAlbumName ?: 'Not Available')
  sendEvent(name:'currentTrackName',  value: currentTrackName ?: 'Not Available')
  sendEvent(name:'currentTrackNumber',  value: currentTrackNumber ?: 0)
  String trackDescription = currentTrackName && currentArtistName ? "${currentTrackName} by ${currentArtistName}" : null
  if(trackDescription) { sendEvent(name: 'trackDescription', value: trackDescription) }
}

void setNextArtistAlbumTrack(String nextArtistName, String nextAlbumName, String nextTrackName) {
  if(disableArtistAlbumTrackEvents) {return}
  sendEvent(name:'nextArtistName', value: nextArtistName ?: 'Not Available')
  sendEvent(name:'nextAlbumName',  value: nextAlbumName ?: 'Not Available')
  sendEvent(name:'nextTrackName',  value: nextTrackName ?: 'Not Available')
}

void setTrackDataEvents(Map trackData) {
  if(disableTrackDataEvents) {return}
  trackData['level'] = this.device.currentState('level').value
  trackData['mute'] = this.device.currentState('mute').value
  sendEvent(name: 'trackData', value: JsonOutput.toJson(trackData))
}

void updateChildBatteryStatus(Map event) { if(createBatteryStatusChildDevice) {getBatteryStatusChild().sendEvent(event) }}

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
ChildDeviceWrapper getCrossfadeControlChild() { return getChildDevice(getCrossfadeControlChildDNI()) }
ChildDeviceWrapper getShuffleControlChild() { return getChildDevice(getShuffleControlChildDNI()) }
ChildDeviceWrapper getRepeatOneControlChild() { return getChildDevice(getRepeatOneControlChildDNI()) }
ChildDeviceWrapper getRepeatAllControlChild() { return getChildDevice(getRepeatAllControlChildDNI()) }
ChildDeviceWrapper getMuteControlChild() { return getChildDevice(getMuteControlChildDNI()) }
ChildDeviceWrapper getBatteryStatusChild() { return getChildDevice(getBatteryStatusChildDNI()) }
ChildDeviceWrapper getFavoritesChild() { return getChildDevice(getFavoritesChildDNI()) }

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
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
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
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
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
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
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
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
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
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
}

void createRemoveBatteryStatusChildDevice(Boolean create) {
  String dni = getBatteryStatusChildDNI()
  ChildDeviceWrapper child = getBatteryStatusChild()
  if(!child && create) {
    try {
      logDebug("Creating Battery Status device")
      child = addChildDevice('dwinks', 'Sonos Advanced Battery Status', dni,
        [ name: 'Sonos Battery Status',
          label: "Sonos Battery Status - ${this.getDataValue('name')}"]
      )
    } catch (UnknownDeviceTypeException e) {
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
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
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
}

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
    logDebug(device.currentValue('isGroupCoordinator', true))
    if(device.currentValue('isGroupCoordinator', true) == 'on') {
      processAVTransportMessages(message)
      parent?.isFavoritePlaying(this.device)
    }
  }
  else if(serviceType == 'RenderingControl' || message.headers.containsKey('NOTIFY /mrc HTTP/1.1')) {
    processRenderingControlMessages(message)
  }
  else if(serviceType == 'ZoneGroupTopology' || message.headers.containsKey('NOTIFY /zgt HTTP/1.1')) {
    processZoneGroupTopologyMessages(message)
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
// Parse Helper Methods
// =============================================================================
void processAVTransportMessages(Map message) {
  if(message.body.contains('&lt;CurrentTrackURI val=&quot;x-rincon:')) { return } //Bail out if this AVTransport message is just "I'm now playing a stream from a coordinator..."
  if(message.body.contains('&lt;TransportState val=&quot;TRANSITIONING&quot;/&gt;')) { return } //Bail out if this AVTransport message is TRANSITIONING"

  parent?.processAVTransportMessages(this.device, message)
}

void processZoneGroupTopologyMessages(Map message) {
  String rincon = device.getDataValue('id')
  GPathResult propertyset = parseSonosMessageXML(message)
  GPathResult zoneGroups = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups']

  LinkedHashSet<String> groupedRincons = new LinkedHashSet()
  GPathResult currentGroupMembers = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent().children()

  currentGroupMembers.each{
    if(it['@Invisible'] == '1') {return}
    groupedRincons.add(it['@UUID'].toString())
  }
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }

  if(device.getDataValue('groupIds')) {
    LinkedHashSet<String> oldGroupedRincons = new LinkedHashSet((device.getDataValue('groupIds').tokenize(',')))
    if(groupedRincons != oldGroupedRincons) {
      logTrace('ZGT message parsed, group member changes found.')
    } else {
      logTrace('ZGT message parsed, no group member changes.')
    }
  }

  device.updateDataValue('groupIds', groupedRincons.join(','))
  String currentGroupCoordinatorName = zoneGroups.children().children().findAll{it['@UUID'] == rincon}['@ZoneName']

  LinkedHashSet currentGroupMemberNames = []
  groupedRincons.each{ gr ->
    currentGroupMemberNames.add(zoneGroups.children().children().findAll{it['@UUID'] == gr}['@ZoneName']) }
  Integer currentGroupMemberCount = groupedRincons.size()


  String groupName = (propertyset['property']['ZoneGroupName'].text()).toString()
  if(groupName && groupedRincons) {
    parent?.updateZoneGroupName(groupName, groupedRincons)
  }

  if(currentGroupCoordinatorName) {device.sendEvent(name: 'groupCoordinatorName', value: currentGroupCoordinatorName)}
  device.sendEvent(name: 'isGrouped', value: currentGroupMemberCount > 1 ? 'on' : 'off')
  device.sendEvent(name: 'groupMemberCount', value: currentGroupMemberCount)
  if(currentGroupMemberNames.size() > 0) {device.sendEvent(name: 'groupMemberNames' , value: currentGroupMemberNames)}
  if(groupName) {device.sendEvent(name: 'groupName', value: groupName)}


  if(createBatteryStatusChildDevice) {
    String moreInfoString = zoneGroups.children().children().findAll{it['@UUID'] == rincon}['@MoreInfo']
    if(moreInfoString) {
      Map moreInfo = moreInfoString.tokenize(',').collect{ it.tokenize(':') }.collectEntries{ [it[0],it[1]]}
      if(moreInfo.containsKey('BattTmp') && moreInfo.containsKey('BattPct') && moreInfo.containsKey('BattChg')) {
        BigDecimal battTemp = new BigDecimal(moreInfo['BattTmp'])
        List<Event> stats = [
          [name: 'battery', value: moreInfo['BattPct'] as Integer, unit: '%' ],
          [name: 'powerSource', value: moreInfo['BattChg'] == 'NOT_CHARGING' ? 'battery' : 'mains' ],
          [name: 'temperature', value: getTemperatureScale() == 'F' ? celsiusToFahrenheit(battTemp) : battTemp, unit: getTemperatureScale() ],
        ]
        stats.each{ if(it) {updateChildBatteryStatus(it) }}
      }
    }
  }

  String zonePlayerUUIDsInGroup = (propertyset['property']['ZonePlayerUUIDsInGroup'].text()).toString()
  if(zonePlayerUUIDsInGroup) {
    List<String> playersInGroup = zonePlayerUUIDsInGroup.tokenize(',')
    parent?.updateGroupDevices(playersInGroup[0].toString(), playersInGroup.tail())
  }
}

void processGroupRenderingControlMessages(Map message) { parent?.processGroupRenderingControlMessages(this.device, message) }
void processRenderingControlMessages(Map message) {
  GPathResult propertyset = parseSonosMessageXML(message)
  GPathResult instanceId = propertyset['property']['LastChange']['Event']['InstanceID']

  String volume = instanceId.children().findAll{it.name() == 'Volume' && it['@channel'] == 'Master'}['@val']
  if(volume) {
    sendEvent(name:'level', value: volume as Integer)
    sendEvent(name:'volume', value: volume as Integer)
    if(volume && (volume as Integer) > 0) { state.restoreLevelAfterUnmute = volume }
  }
  String lfString = instanceId.children().findAll{it.name() == 'Volume' && it['@channel'] == 'LF'}['@val'].toString()
  lfString = lfString ?: '0'
  String rfString = instanceId.children().findAll{it.name() == 'Volume' && it['@channel'] == 'RF'}['@val'].toString()
  rfString = rfString ?: '0'
  Integer lf = Integer.parseInt(lfString)
  Integer rf = Integer.parseInt(rfString)
  Integer balance = 0
  if(lf < 100) {
    balance = ((100 - lf) / 5) as Integer
  } else if(rf < 100) {
    balance = -((100 - rf) / 5) as Integer
  }
  sendEvent(name: 'balance', value: balance)

  String mute = instanceId.children().findAll{it.name() == 'Mute' && it['@channel'] == 'Master'}['@val']
  if(mute) {
    String muted = mute == '1' ? 'muted' : 'unmuted'
    String previousMutedState = this.device.currentState('mute')?.value != null ? this.device.currentState('mute').value : 'unmuted'
    if(muted == 'unmuted' && previousMutedState == 'muted' && enableAirPlayUnmuteVolumeFix) {
      logDebug("Restoring volume after unmute event to level: ${state.restoreLevelAfterUnmute}")
      setLevel(state.restoreLevelAfterUnmute as Integer)
    }
    sendEvent(name:'mute', value: muted)
  }

  String bass = instanceId['Bass']['@val']
  if(bass) { sendEvent(name:'bass', value: bass as Integer) }

  String treble = instanceId['Treble']['@val']
  if(treble) { sendEvent(name:'treble', value: treble as Integer) }

  String loudness = instanceId.children().findAll{it.name() == 'Loudness' && it['@channel'] == 'Master'}['@val']
  if(loudness) { sendEvent(name:'loudness', value: loudness == '1' ? 'on' : 'off') }
}



// =============================================================================
// Subscriptions and Resubscriptions
// =============================================================================
  // sonosEventSubscribe('/MediaRenderer/Queue/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/MediaServer/ContentDirectory/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/MusicServices/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/SystemProperties/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/AlarmClock/Event', host, RESUB_INTERVAL, dni)

String getlocalUpnpHost() {return device.getDataValue('localUpnpHost')}
String getDNI() {return device.getDeviceNetworkId()}
@Field private final String MRRC_EVENTS  =  '/MediaRenderer/RenderingControl/Event'
@Field private final String MRGRC_EVENTS =  '/MediaRenderer/GroupRenderingControl/Event'
@Field private final String ZGT_EVENTS   =  '/ZoneGroupTopology/Event'
@Field private final String MRAVT_EVENTS =  '/MediaRenderer/AVTransport/Event'

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
    logWarn('Failed to resubscribe to ZoneGroupTopology. Will trying subscribing again in 60 seconds.')
    device.removeDataValue('sid3')
    runIn(60, 'subscribeToZgtEvents')
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
// Misc helpers
// =============================================================================

void clearCurrentNextArtistAlbumTrackData() {
  setCurrentArtistAlbumTrack(null, null, null, 0)
  setNextArtistAlbumTrack(null, null, null)
}

void clearTrackDataEvent() {
  sendEvent(name: 'trackData', value: '{}')
}

List<String> getGroupMemberDNIs() {
  List groupMemberDNIs = []
  String groupIds = this.device.getDataValue('groupIds')
  logDebug("Getting group member DNIs... ${groupIds}")
  if(groupIds.contains(',')) {
    List<String> groupMemberRincons = groupIds.tokenize(',')
    groupMemberRincons.remove(this.device.getDataValue('id'))
    groupMemberRincons.each{it -> groupMemberDNIs.add("${it}".tokenize('_')[1][0..-6])}
    return groupMemberDNIs
  } else {
    return []
  }
}

String getDeviceData(String name) {
  return this.device.getDataValue(name)
}
void setDeviceData(String name, String value) {
  this.device.updateDataValue(name, value)
}

// =============================================================================
// Getters and Setters
// =============================================================================
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

String getGroupId() {
  return this.device.getDataValue('groupId')
}
void setGroupId(String groupId) {
  this.device.updateDataValue('groupId', groupId)
  this.device.sendEvent(name: 'groupId', value: groupId)
  if(getIsGroupCoordinator()) {
    subscribeToPlayback(groupId)
    subscribeToPlaybackMetadata(groupId)
  }
}

String getGroupName() {
  return this.device.currentValue('groupName',true)
}
void setGroupName(String groupName) {
  this.device.sendEvent(name: 'groupName', value: groupName)
}

String getGroupCoordinatorId() {
  return this.device.getDataValue('groupCoordinatorId')
}
void setGroupCoordinatorId(String groupCoordinatorId) {
  this.device.updateDataValue('groupCoordinatorId', groupCoordinatorId)
  this.device.sendEvent(name: 'groupCoordinatorId', value: groupCoordinatorId)
  Boolean isGroupCoordinator = getId() == groupCoordinatorId
  Boolean previouslyWasGroupCoordinator = getIsGroupCoordinator()

  setIsGroupCoordinator(isGroupCoordinator)

  if(isGroupCoordinator) {
    if(!this.device.getDataValue('sid1')) {subscribeToAVTransport()}
  } else {
    if(previouslyWasGroupCoordinator) {
      logDebug("Just added to group!")
      unsubscribeFromAVTransport()
      parent?.updatePlayerCurrentStates(this.device, coordinatorRincon)
    } else {logDebug("Just removed from group!")}
  }
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

String getGroupPlayerIds() {
  return this.device.getDataValue('isGroupCoordinator')
}
void setGroupPlayerIds(List<String> groupPlayerIds) {
  this.device.updateDataValue('groupPlayerIds', groupPlayerIds.join(','))
  this.device.updateDataValue('groupIds', groupPlayerIds.join(','))
  this.device.sendEvent(name: 'isGrouped', value: groupPlayerIds.size() > 1 ? 'on' : 'off')
  this.device.sendEvent(name: 'groupMemberCount', value: groupPlayerIds.size())
}

String getGroupPlayerNames() {
  return this.device.currentValue('groupMemberNames')
}
void setGroupPlayerNames(List<String> groupPlayerNames) {
  this.device.sendEvent(name: 'groupMemberNames' , value: groupPlayerNames.toString())
}


// =============================================================================
// Websocket Connection and Initialization
// =============================================================================
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()

void webSocketStatus(String message) {
  if(message == 'failure: null') { this.device.updateDataValue('websocketStatus', 'closed')}
  if(message == 'status: open') { this.device.updateDataValue('websocketStatus', 'open')}
  if(message == 'failure: connect timed out') { this.device.updateDataValue('websocketStatus', 'connect timed out')}
  logTrace("Socket Status: ${message}")
}

void wsConnect() {
  Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
  interfaces.webSocket.connect(this.device.getDataValue('websocketUrl'), headers: headers, ignoreSSLIssues: true)
  if(this.device.getDataValue('secondaryWebsocketUrls')) {
    List<String> secondaries = this.device.getDataValue('secondaryWebsocketUrls').tokenize(',')
    secondaries.each{secondary ->
      interfaces.webSocket.connect(secondary, headers: headers, ignoreSSLIssues: true)
    }
  }
}

void wsClose() {
  interfaces.webSocket.close()
}

void sendWsMessage(String message) {
  if(this.device.getDataValue('websocketStatus') != 'open') { wsConnect() }
  interfaces.webSocket.sendMessage(message)
}

void initializeWebsocketConnection() {
  if(this.device.getDataValue('websocketStatus') != 'open') { wsConnect() }
  if(createFavoritesChildDevice) {subscribeToFavorites()}
  subscribeToPlaylists()
  subscribeToAudioClip()
  subscribeToGroups()
}

// =============================================================================
// Websocket Subscriptions
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
    'groupId':"${getId()}"
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
    'groupId':"${getId()}"
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
    'groupId':"${getId()}"
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
void playerLoadAudioClip(String uri = null, BigDecimal volume = null) {
  Map command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}"
  ]
  Map args = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos', "clipType": "CHIME"]
  if(uri) {args.streamUrl = uri}
  if(volume) {args.volume = volume}
  String json = JsonOutput.toJson([command,args])
  logTrace(json)
  sendWsMessage(json)
}



// =============================================================================
// Websocket Incoming Data Processing
// =============================================================================
void processWebsocketMessage(String message) {
  String prettyJson = JsonOutput.prettyPrint(message)
  List<Map> json = slurper.parseText(message)
  logTrace(prettyJson)

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

    List<String> groupPlayerNames = group.playerIds.collect{pid -> players.find{player-> player?.id == pid}?.name}
    setGroupPlayerNames(groupPlayerNames)
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

  if(createFavoritesChildDevice && eventType?.type == 'favoritesList' && eventType?.response == 'getFavorites' && eventType?.success == true) {
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
    ChildDeviceWrapper favDev = getFavoritesChild()
    favDev.setFavorites(html)
    InstalledAppWrapper p = this.getParent()
    if(p.getSetting('favMatching')) {
      Map favs = [:]
      respData.each{
        if(it?.resource?.id?.objectId && it?.resource?.id?.serviceId && it?.resource?.id?.accountId) {
          String objectId = (it?.resource?.id?.objectId).tokenize(':')[1]
          String serviceId = it?.resource?.id?.serviceId
          String accountId = it?.resource?.id?.accountId
          String universalMusicObjectId = "${objectId}${serviceId}${accountId}".toString()
          favs[universalMusicObjectId] = [id:it?.id, name:it?.name, imageUrl:it?.imageUrl, service: it?.service?.name]
        } else if(it?.imageUrl) {
          String universalMusicObjectId = "${it?.imageUrl}".toString()
          favs[universalMusicObjectId] = [id:it?.id, name:it?.name, imageUrl:it?.imageUrl, service: it?.service?.name]
        }
      }
      p.setFavorites(favs)
    }
  }
}


