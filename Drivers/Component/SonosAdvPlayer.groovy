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
    namespace: 'dwinks',
    author: 'Daniel Winks',
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
  // command 'subscribeToEvents'
  // command 'resubscribeToEvents'

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
  attribute 'isGrouped', 'enum', [ 'on', 'off' ]
  attribute 'groupMemberCount', 'number'
  attribute 'groupMemberNames', 'JSON_OBJECT'
  attribute 'Fav', 'string'
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
  createRemoveCrossfadeChildDevice(createCrossfadeChildDevice)
  createRemoveShuffleChildDevice(createShuffleChildDevice)
  createRemoveRepeatOneChildDevice(createRepeatOneChildDevice)
  createRemoveRepeatAllChildDevice(createRepeatAllChildDevice)
  createRemoveMuteChildDevice(createMuteChildDevice)
  createRemoveBatteryStatusChildDevice(createBatteryStatusChildDevice)
  if(disableTrackDataEvents) { clearTrackDataEvent() }
  if(disableArtistAlbumTrackEvents) { clearCurrentNextArtistAlbumTrackData() }
  secondaryConfiguration()
}

void secondaryConfiguration() {
  parent?.componentUpdatePlayerInfo(this.device)
  runIn(10, 'subscribeToEvents')
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
  parent?.componentSetPlayModesLocal(this.device, ['playModes': playModes ])
}
void repeatOne() { setRepeatMode('repeat one') }
void repeatAll() { setRepeatMode('repeat all') }
void repeatNone() { setRepeatMode('off') }

void setCrossfade(String mode) {
  logDebug("Setting crossfade mode to ${mode}")
  Map playModes = mode == 'on' ? [ 'crossfade': true ] : [ 'crossfade': false ]
  parent?.componentSetPlayModesLocal(this.device, ['playModes': playModes ])
}
void enableCrossfade() { setCrossfade('on') }
void disableCrossfade() { setCrossfade('off') }

void setShuffle(String mode) {
  logDebug("Setting shuffle mode to ${mode}")
  Map playModes = mode = 'on' ? [ 'shuffle': true ] : [ 'shuffle': false ]
  parent?.componentSetPlayModesLocal(this.device, ['playModes': playModes ])
}
void shuffleOn() { setShuffle('on') }
void shuffleOff() { setShuffle('off') }

void ungroupPlayer() { parent?.componentUngroupPlayerLocal(this.device) }

void playText(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndRestore(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void playTextAndResume(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void setTrack(String uri) { playTrack(uri) }
void playTrack(String uri, BigDecimal volume = null) { parent?.componentLoadStreamUrlLocal(this.device, uri, volume) }
void playTrackAndRestore(String uri, BigDecimal volume = null) { parent?.componentPlayAudioClipLocal(this.device, uri, volume) }
void playTrackAndResume(String uri, BigDecimal volume = null) { parent?.componentPlayAudioClipLocal(this.device, uri, volume) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  parent?.componentPlayTextLocal(this.device, text, volume, voice)
}

void devicePlayTrack(String uri, BigDecimal volume = null) {
  parent?.componentLoadStreamUrlLocal(this.device, uri, volume)
}

void mute(){ parent?.componentMutePlayerLocal(this.device, true) }
void unmute(){ parent?.componentMutePlayerLocal(this.device, false) }
void setLevel(BigDecimal level) { parent?.componentSetPlayerLevelLocal(this.device, level) }
void setVolume(BigDecimal level) { setLevel(level) }
void setTreble(BigDecimal level) { parent?.componentSetTrebleLocal(this.device, level)}
void setBass(BigDecimal level) { parent?.componentSetBassLocal(this.device, level)}
void setLoudness(String mode) { parent?.componentSetLoudnessLocal(this.device, mode == 'on')}
void setBalance(BigDecimal level) { parent?.componentSetBalanceLocal(this.device, level)}

void muteGroup(){
  if(this.device.currentState('isGrouped')?.value == 'on') {parent?.componentMuteGroupLocal(this.device, true) }
  else { parent?.componentMutePlayerLocal(this.device, true) }
}
void unmuteGroup(){
  if(this.device.currentState('isGrouped')?.value == 'on') {parent?.componentMuteGroupLocal(this.device, false) }
  else { parent?.componentMutePlayerLocal(this.device, false) }
}
void setGroupVolume(BigDecimal level) {
  if(this.device.currentState('isGrouped')?.value == 'on') { parent?.componentSetGroupLevelLocal(this.device, level) }
  else { parent?.componentSetPlayerLevelLocal(this.device, level)  }
}
void setGroupLevel(BigDecimal level) { setGroupVolume(level) }
void setGroupMute(String mode) {
  logDebug("Setting group mute to ${mode}")
  if(mode == 'muted') { muteGroup() }
  else { unmuteGroup() }
}
void groupVolumeUp() {
  if(this.device.currentState('isGrouped')?.value == 'on') { parent?.componentSetGroupRelativeLevelLocal(this.device, (volumeAdjustAmount as Integer)) }
  else { parent?.componentSetPlayerRelativeLevelLocal(this.device, (volumeAdjustAmount as Integer)) }
}
void groupVolumeDown() {
  if(this.device.currentState('isGrouped')?.value == 'on') { parent?.componentSetGroupRelativeLevelLocal(this.device, -(volumeAdjustAmount as Integer)) }
  else { parent?.componentSetPlayerRelativeLevelLocal(this.device, -(volumeAdjustAmount as Integer)) }
}

void volumeUp() { parent?.componentSetPlayerRelativeLevelLocal(this.device, (volumeAdjustAmount as Integer)) }
void volumeDown() { parent?.componentSetPlayerRelativeLevelLocal(this.device, -(volumeAdjustAmount as Integer)) }

void play() { parent?.componentPlayLocal(this.device) }
void stop() { parent?.componentStopLocal(this.device) }
void pause() { parent?.componentPauseLocal(this.device) }
void nextTrack() { parent?.componentNextTrackLocal(this.device) }
void previousTrack() { parent?.componentPreviousTrackLocal(this.device) }
void refresh() {
  subscribeToZgtEvents()
  subscribeToMrGrcEvents()
  subscribeToMrRcEvents()
}

void getFavorites() {
  Map favorites = parent?.componentGetFavoritesLocal(this.device)
}

void loadFavorite(String favoriteId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}
// void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay, String crossfadeMode)

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
  switch(command) {
    case 'CrossFade':
      getCrossfadeControlChild().sendEvent(name:'switch', value: this.device.currentState('currentCrossfadeMode')?.value )
    break
    case 'Shuffle':
      getShuffleControlChild().sendEvent(name:'switch', value: this.device.currentState('currentShuffleMode')?.value )
    break
    case 'RepeatOne':
      getRepeatOneControlChild().sendEvent(name:'switch', value: this.device.currentState('currentRepeatOneMode')?.value)
    break
    case 'RepeatAll':
      getRepeatAllControlChild().sendEvent(name:'switch', value: this.device.currentState('currentRepeatAllMode')?.value )
    break
    case 'Mute':
      String muteValue = this.device.currentState('mute')?.value != null ? this.device.currentState('mute').value : 'unmuted'
      getMuteControlChild().sendEvent(name:'switch', value: muteValue )
    break
  }
}

void componentOn(DeviceWrapper child) {
  String command = child.getDataValue('command')
  switch(command) {
    case 'CrossFade':
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
    case 'CrossFade':
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
  sendEvent(name: 'trackData', value: trackData)
}

void componentUpdateBatteryStatus() { parent?.componentUpdateBatteryStatus(this.device) }
void updateChildBatteryStatus(Map event) { getBatteryStatusChild().sendEvent(event) }

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

// =============================================================================
// Parse
// =============================================================================

void parse(String raw) {
  LinkedHashMap message = parseLanMessage(raw)
  if(message.body == null) {return}
  String serviceType = message.headers["X-SONOS-SERVICETYPE"]
  if(serviceType == 'AVTransport' || message.headers.containsKey('NOTIFY /avt HTTP/1.1')) {
    if(this.device.getDataValue('isGroupCoordinator') == 'true') {
      processAVTransportMessages(message)
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
  else {
    logDebug("Could not determine service type for message: ${message}")
  }
}

// =============================================================================
// Parse Helper Methods
// =============================================================================

void processAVTransportMessages(Map message) { parent?.processAVTransportMessages(this.device, message) }
void processZoneGroupTopologyMessages(Map message) { parent?.processZoneGroupTopologyMessages(this.device, message)}
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

void processGroupRenderingControlMessages(Map message) {
  GPathResult propertyset = parseSonosMessageXML(message)
  Integer groupVolume = Integer.parseInt(propertyset.'**'.find{it.name() == 'GroupVolume'}.text())
  String groupMute = Integer.parseInt(propertyset.'**'.find{it.name() == 'GroupMute'}.text()) == 1 ? 'muted' : 'unmuted'
  if(groupVolume) { sendEvent(name:'groupVolume', value: groupVolume) }
  if(groupMute) { sendEvent(name:'groupMute', value: groupMute ) }
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

void subscribeToEvents() { refresh() }
// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/RenderingControl/Event' //sid2
// /////////////////////////////////////////////////////////////////////////////
void subscribeToMrRcEvents() {
  sonosEventSubscribe(MRRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/mrc', 'subscribeToMrRcCallback')
  unschedule('resubscribeToMrRcEvents')
  runIn(RESUB_INTERVAL-100, 'resubscribeToMrRcEvents')
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
    logDebug('Sucessfully subscribed to MediaRenderer/RenderingControl')
    if(response.headers["SID"]) {device.updateDataValue('sid2', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid2', response.headers["sid"])}
  }
}


// /////////////////////////////////////////////////////////////////////////////
// '/ZoneGroupTopology/Event' //sid3
// /////////////////////////////////////////////////////////////////////////////
void subscribeToZgtEvents() {
  sonosEventSubscribe(ZGT_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/zgt', 'subscribeToZgtCallback')
  unschedule('resubscribeToZgtEvents')
  runIn(RESUB_INTERVAL-100, 'resubscribeToZgtEvents')
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
    logDebug('Sucessfully subscribed to ZoneGroupTopology')
    if(response.headers["SID"]) {device.updateDataValue('sid3', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid3', response.headers["sid"])}
  }
}


// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/AVTransport/Event' //sid1
// /////////////////////////////////////////////////////////////////////////////
void subscribeToAVTransport() {
  sonosEventSubscribe(MRAVT_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/avt', 'subscribeToMrAvTCallback')
  unschedule('resubscribeToAVTransport')
  runIn(RESUB_INTERVAL-100, 'resubscribeToAVTransport')
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
    logDebug('Sucessfully subscribed to MediaRenderer/AVTransport')
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
// '/MediaRenderer/GroupRenderingControl/Event' //sid4
// /////////////////////////////////////////////////////////////////////////////
void subscribeToMrGrcEvents() {
  sonosEventSubscribe(MRGRC_EVENTS, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), '/mgrc', 'subscribeToMrGrcCallback')
  unschedule('resubscribeToMrGrcEvents')
  runIn(RESUB_INTERVAL-100, 'resubscribeToMrGrcEvents')
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
    logDebug('Sucessfully subscribed to MediaRenderer/GroupRenderingControl')
    if(response.headers["SID"]) {device.updateDataValue('sid4', response.headers["SID"])}
    if(response.headers["sid"]) {device.updateDataValue('sid4', response.headers["sid"])}
  }
}


// =============================================================================
// Child Device Helpers
// =============================================================================

String getCrossfadeControlChildDNI() { return "${device.getDeviceNetworkId()}-CrossfadeControl" }
String getShuffleControlChildDNI() { return "${device.getDeviceNetworkId()}-ShuffleControl" }
String getRepeatOneControlChildDNI() { return "${device.getDeviceNetworkId()}-RepeatOneControl" }
String getRepeatAllControlChildDNI() { return "${device.getDeviceNetworkId()}-RepeatAllControl" }
String getMuteControlChildDNI() { return "${device.getDeviceNetworkId()}-MuteControl" }
String getBatteryStatusChildDNI() { return "${device.getDeviceNetworkId()}-BatteryStatus" }
ChildDeviceWrapper getCrossfadeControlChild() { return getChildDevice(getCrossfadeControlChildDNI()) }
ChildDeviceWrapper getShuffleControlChild() { return getChildDevice(getShuffleControlChildDNI()) }
ChildDeviceWrapper getRepeatOneControlChild() { return getChildDevice(getRepeatOneControlChildDNI()) }
ChildDeviceWrapper getRepeatAllControlChild() { return getChildDevice(getRepeatAllControlChildDNI()) }
ChildDeviceWrapper getMuteControlChild() { return getChildDevice(getMuteControlChildDNI()) }
ChildDeviceWrapper getBatteryStatusChild() { return getChildDevice(getBatteryStatusChildDNI()) }

// =============================================================================
// Misc helpers
// =============================================================================

void clearCurrentNextArtistAlbumTrackData() {
  setCurrentArtistAlbumTrack(null, null, null, 0)
  setNextArtistAlbumTrack(null, null, null)
}

void clearTrackDataEvent() {
  sendEvent(name: 'trackData', value: [:])
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