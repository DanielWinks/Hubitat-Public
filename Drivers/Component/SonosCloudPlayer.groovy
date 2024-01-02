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
  definition(name: 'Sonos Cloud Player', namespace: 'dwinks', author: 'Daniel Winks', importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosCloudPlayer.groovy') {
  capability 'AudioNotification'
  capability "AudioVolume" //mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%  //commands: volumeDown() volumeUp()
  capability 'MusicPlayer' //attributes: level - NUMBER mute - ENUM ["unmuted", "muted"] status - STRING trackData - JSON_OBJECT trackDescription - STRING
  capability "MediaTransport" //attributes:  transportStatus - ENUM - ["playing", "paused", "stopped"]
  capability 'SpeechSynthesis'
  capability 'Configuration'
  capability 'Initialize'
  capability 'Refresh'

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
  command 'loadFavorite', [[ name: 'favoriteId', type: 'STRING']]

  command 'enableCrossfade'
  command 'disableCrossfade'

  command 'repeatOne'
  command 'repeatAll'
  command 'repeatNone'
  command 'subscribeToEvents'

  attribute 'currentRepeatOneMode', 'enum', [ 'on', 'off' ]
  attribute 'currentRepeatAllMode', 'enum', [ 'on', 'off' ]
  attribute 'currentCrossfadeMode', 'enum', [ 'on', 'off' ]
  attribute 'currentShuffleMode' , 'enum', [ 'on', 'off' ]
  attribute 'currentTrackDuration', 'string'
  attribute 'currentArtistName', 'string'
  attribute 'currentAlbumName', 'string'
  attribute 'currentTrackName', 'string'
  attribute 'nextArtistName', 'string'
  attribute 'nextAlbumName', 'string'
  attribute 'nextTrackName', 'string'
  attribute 'queueTrackTotal', 'string'
  attribute 'queueTrackPosition', 'string'
  attribute 'treble', 'number'
  attribute 'bass', 'number'
  attribute 'loudness', 'enum', [ 'on', 'off' ]

  attribute 'groupName', 'string'
  attribute 'groupCoordinatorName', 'string'
  attribute 'groupCoordinatorId', 'string'
  attribute 'groupId', 'string'
  attribute 'isGroupCoordinator' , 'enum', [ 'on', 'off' ]
  attribute 'isGrouped', 'enum', [ 'on', 'off' ]
  attribute 'groupMemberCount', 'number'
  attribute 'groupMemberIds', 'JSON_OBJECT'
  attribute 'Fav', 'string'
  }

  preferences {
    section('Device Settings') {
      input 'disableTrackDataEvents', 'bool', title: 'Disable track data events', required: false, defaultValue: true
      input name: 'volumeAdjustAmount', title: 'Volume up/down Adjust default:(+/- 5%)', type: 'enum', required: false, defaultValue: 5,
      options: [1:'+/- 1%', 2:'+/- 2%', 3:'+/- 3%', 4:'+/- 4%', 5:'+/- 5%', 10:'+/- 10%', 20:'+/- 20%']
      input 'disableArtistAlbumTrackEvents', 'bool', title: 'Disable artist, album, and track events', required: false, defaultValue: false
      input 'enableAirPlayUnmuteVolumeFix', 'bool', title: 'Restore prior volume to "AirPlay" group after unmute', required: false, defaultValue: true
      input 'createCrossfadeChildDevice', 'bool', title: 'Create child device for crossfade control?', required: false, defaultValue: false
      input 'createShuffleChildDevice', 'bool', title: 'Create child device for shuffle control?', required: false, defaultValue: false
      input 'createRepeatOneChildDevice', 'bool', title: 'Create child device for "repeat one" control?', required: false, defaultValue: false
      input 'createRepeatAllChildDevice', 'bool', title: 'Create child device for "repeat all" control?', required: false, defaultValue: false
      input 'createMuteChildDevice', 'bool', title: 'Create child device for <b>player</b> mute control?', required: false, defaultValue: false
    }
  }
}

// =============================================================================
// Constants
// =============================================================================
@Field private final Integer RESUB_INTERVAL = 600

// =============================================================================
// Initialize and Configure
// =============================================================================
void initialize() { configure() }
void configure() {
  updateDniIfNeeded()
  subscribeToEvents()
  createRemoveCrossfadeChildDevice(createCrossfadeChildDevice)
  createRemoveShuffleChildDevice(createShuffleChildDevice)
  createRemoveRepeatOneChildDevice(createRepeatOneChildDevice)
  createRemoveRepeatAllChildDevice(createRepeatAllChildDevice)
  createRemoveMuteChildDevice(createMuteChildDevice)
  if(disableTrackDataEvents) { clearTrackDataEvent() }
  if(disableArtistAlbumTrackEvents) { clearCurrentNextArtistAlbumTrackData() }
}

void updateDniIfNeeded() {
  InstalledAppWrapper parentApp = getParent()
  String oldDni = "${parentApp.getId()}-${device.getDataValue('id')}"
  String dni = "${device.getDataValue('id')}".tokenize('_')[1][0..-6]
  logDebug("OldDNI -> newDNI ${oldDni} -> ${dni}")
  if(device.getDeviceNetworkId() == oldDni) {
    device.setDeviceNetworkId(dni)
    logDebug("Set DNI to use new schema...")
  }
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
  parent?.componentSetPlayModes(this.device, ['playModes': playModes ])
}
void repeatOne() { setRepeatMode('repeat one') }
void repeatAll() { setRepeatMode('repeat all') }
void repeatNone() { setRepeatMode('off') }

void setCrossfade(String mode) {
  logDebug("Setting crossfade mode to ${mode}")
  Map playModes = mode == 'on' ? [ 'crossfade': true ] : [ 'crossfade': false ]
  parent?.componentSetPlayModes(this.device, ['playModes': playModes ])
}
void enableCrossfade() { setCrossfade('on') }
void disableCrossfade() { setCrossfade('off') }

void setShuffle(String mode) {
  logDebug("Setting shuffle mode to ${mode}")
  Map playModes = mode = 'on' ? [ 'shuffle': true ] : [ 'shuffle': false ]
  parent?.componentSetPlayModes(this.device, ['playModes': playModes ])
}
void shuffleOn() { setShuffle('on') }
void shuffleOff() { setShuffle('off') }

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
void setLevel(BigDecimal level) { parent?.componentSetPlayerLevel(this.device, level) }
void setVolume(BigDecimal level) { setLevel(level) }

void muteGroup(){
  if(this.device.currentState('isGrouped')?.value == 'on') {parent?.componentMuteGroup(this.device, true) }
  else { parent?.componentMutePlayer(this.device, true) }
}
void unmuteGroup(){
  if(this.device.currentState('isGrouped')?.value == 'on') {parent?.componentMuteGroup(this.device, false) }
  else { parent?.componentMutePlayer(this.device, false) }
}
void setGroupVolume(BigDecimal level) {
  if(this.device.currentState('isGrouped')?.value == 'on') { parent?.componentSetGroupLevel(this.device, level) }
  else { parent?.componentSetPlayerLevel(this.device, level)  }
}
void setGroupLevel(BigDecimal level) { setGroupVolume(level) }
void setGroupMute(String mode) {
  logDebug("Setting group mute to ${mode}")
  if(mode == 'on') { muteGroup() }
  else { unmuteGroup() }
}
void groupVolumeUp() {
  if(this.device.currentState('isGrouped')?.value == 'on') { parent?.componentSetGroupRelativeLevel(this.device, (volumeAdjustAmount as Integer)) }
  else { parent?.componentSetPlayerRelativeLevel(this.device, (volumeAdjustAmount as Integer)) }
}
void groupVolumeDown() {
  if(this.device.currentState('isGrouped')?.value == 'on') { parent?.componentSetGroupRelativeLevel(this.device, -(volumeAdjustAmount as Integer)) }
  else { parent?.componentSetPlayerRelativeLevel(this.device, -(volumeAdjustAmount as Integer)) }
}

void volumeUp() { parent?.componentSetPlayerRelativeLevel(this.device, (volumeAdjustAmount as Integer)) }
void volumeDown() { parent?.componentSetPlayerRelativeLevel(this.device, -(volumeAdjustAmount as Integer)) }

void play() { parent?.componentPlay(this.device) }
void stop() { parent?.componentStop(this.device) }
void pause() { parent?.componentStop(this.device) }
void nextTrack() { parent?.componentNextTrack(this.device) }
void previousTrack() { parent?.componentPreviousTrack(this.device) }
void refresh() {subscribeToEvents()}

void getFavorites() {
  Map favorites = parent?.componentGetFavorites(this.device)
}

void loadFavorite(String favoriteId) {
  parent?.componentLoadFavorite(this.device, favoriteId)
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
          label: "Sonos Crossfade Control - ${this.getDataValue('roomName')}"]
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
          label: "Sonos Shuffle Control - ${this.getDataValue('roomName')}"]
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
          label: "Sonos RepeatOne Control - ${this.getDataValue('roomName')}"]
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
          label: "Sonos RepeatAll Control - ${this.getDataValue('roomName')}"]
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
          label: "Sonos Mute Control - ${this.getDataValue('roomName')}"]
      )
      child.updateDataValue('command', 'Mute')
    } catch (UnknownDeviceTypeException e) {
      logException('createGroupDevices', e)
    }
  } else if (!create && child){ deleteChildDevice(dni) }
}

// =============================================================================
// Parse
// =============================================================================

void parse(raw) {
  Map message = parseLanMessage(raw)
  processHeaders(message.headers)
  state.remove('currentTrackMetaData')

  if(message.body == null) {return}
  String sId = message.headers["SID"]
  String serviceType = message.headers["X-SONOS-SERVICETYPE"]
  if(serviceType == 'AVTransport') {
    device.updateDataValue('sid1', sId)
    processAVTransportMessages(message)
  } else if(serviceType == 'RenderingControl') {
    device.updateDataValue('sid2', sId)
    processRenderingControlMessages(message)
  } else if(serviceType == 'ZoneGroupTopology') {
    device.updateDataValue('sid3', sId)
    processZoneGroupTopologyMessages(message)
  }
}

// =============================================================================
// Parse Helper Methods
// =============================================================================

void processAVTransportMessages(Map message) {
  List<String> groupMemberDNIs = getGroupMemberDNIs()

  GPathResult propertyset = parseSonosMessageXML(message)

  String trackUri = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackURI']['@val']

  Boolean isAirPlay = trackUri.toLowerCase().contains('airplay')
  logDebug("Is AirPlay ${isAirPlay}")

  String status = propertyset['property']['LastChange']['Event']['InstanceID']['TransportState']['@val']
  status = status.toLowerCase().replace('_playback','')
  sendEvent(name:'status', value: status)
  sendEvent(name:'transportStatus', value: status)

  String currentPlayMode = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentPlayMode']['@val']
  logDebug("Current Play Mode Message: ${currentPlayMode}")
  switch(currentPlayMode) {
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


  String currentCrossfadeMode = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentCrossfadeMode']['@val']
  currentCrossfadeMode = currentCrossfadeMode=='1' ? 'on' : 'off'
  sendEvent(name:'currentCrossfadeMode', value: currentCrossfadeMode)
  if(createCrossfadeChildDevice) { getCrossfadeControlChild().sendEvent(name:'switch', value: currentCrossfadeMode) }

  if(!disableArtistAlbumTrackEvents) {
    String currentTrackDuration = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackDuration']['@val']
    sendEvent(name:'currentTrackDuration', value: currentTrackDuration)

    String currentTrackMetaData = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackMetaData']['@val']
    GPathResult currentTrackMetaDataXML
    if(currentTrackMetaData) {currentTrackMetaDataXML = parseXML(currentTrackMetaData)}
    if(currentTrackMetaDataXML) {
      sendEvent(name:'currentArtistName', value: currentTrackMetaDataXML['item']['creator'])
      sendEvent(name:'currentAlbumName',  value: currentTrackMetaDataXML['item']['title'])
      sendEvent(name:'currentTrackName',  value: currentTrackMetaDataXML['item']['album'])

      if(this.device.currentState('isGroupCoordinator')?.value == 'on') {
        List<Map> events = [
          [name:'currentTrackDuration', value: currentTrackDuration],
          [name:'currentArtistName', value: currentTrackMetaDataXML['item']['creator']],
          [name:'currentAlbumName',  value: currentTrackMetaDataXML['item']['title']],
          [name:'currentTrackName',  value: currentTrackMetaDataXML['item']['album']]
        ]

        groupMemberDNIs.each{dni-> parent?.sendEventsToSiblingDevice(dni, events) }
        // logDebug("Group Member Ids: ${groupMemberDNIs}")//TODO ITERATE AND SEND EVENTS
      }
    }

    String nextTrackMetaData = propertyset['property']['LastChange']['Event']['InstanceID']['NextTrackMetaData']['@val']
    GPathResult nextTrackMetaDataXML
    if(nextTrackMetaData) {nextTrackMetaDataXML = parseXML(nextTrackMetaData)}
    if(nextTrackMetaDataXML) {
      sendEvent(name:'nextArtistName', value: nextTrackMetaDataXML['item']['creator'])
      sendEvent(name:'nextAlbumName',  value: nextTrackMetaDataXML['item']['title'])
      sendEvent(name:'nextTrackName',  value: nextTrackMetaDataXML['item']['album'])

      if(this.device.currentState('isGroupCoordinator')?.value == 'on') {
        List<Map> events = [
          [name:'nextArtistName', value: nextTrackMetaDataXML['item']['creator']],
          [name:'nextAlbumName',  value: nextTrackMetaDataXML['item']['title']],
          [name:'nextTrackName',  value: nextTrackMetaDataXML['item']['album']]
        ]

        groupMemberDNIs.each{dni-> parent?.sendEventsToSiblingDevice(dni, events) }
        // logDebug("Group Member Ids: ${groupMemberDNIs}")//TODO ITERATE AND SEND EVENTS
      }
    }

    if(!disableTrackDataEvents && currentTrackMetaDataXML) {
      String name = currentTrackMetaDataXML['item']['title']
      String artist = currentTrackMetaDataXML['item']['creator']
      String album = currentTrackMetaDataXML['item']['title']
      String trackNumber = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrack']['@val']
      // String status = propertyset['property']['LastChange']['Event']['InstanceID']['TransportState']['@val'].toLowerCase()
      String level = this.device.currentState('level').value
      String mute = this.device.currentState('mute').value
      String uri = propertyset['property']['LastChange']['Event']['InstanceID']['AVTransportURI']['@val']

      // String transportUri = uri ?? Seems to be the same on the built-in driver
      String enqueuedUri = propertyset['property']['LastChange']['Event']['InstanceID']['EnqueuedTransportURI']['@val']
      String metaData = propertyset['property']['LastChange']['Event']['InstanceID']['EnqueuedTransportURIMetaData']['@val']
      String trackMetaData = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackMetaData']['@val']

      Map trackData = [
        audioSource: "Sonos Q",
        station: null,
        name: name,
        artist: artist,
        album: album,
        trackNumber: trackNumber,
        status: status,
        level: level,
        mute: mute,
        uri: uri,
        trackUri: trackUri,
        transportUri: uri,
        enqueuedUri: enqueuedUri,
        metaData: metaData,
        trackMetaData: trackMetaData
      ]
      sendEvent(name: 'trackData', value: trackData)
    }
  }
  // event.'**'.findAll{it.name() == 'TransportState'}.each{it -> logDebug("Val= ${it.'@val'}")}
}

void processRenderingControlMessages(Map message) {
  GPathResult propertyset = parseSonosMessageXML(message)

  String volume = propertyset['property']['LastChange']['Event']['InstanceID'].children().findAll{it.name() == 'Volume' && it['@channel'] == 'Master'}['@val']
  if(volume) {
    sendEvent(name:'level', value: volume as Integer)
    sendEvent(name:'volume', value: volume as Integer)

    if(volume && (volume as Integer) > 0) {
      logDebug("Setting volume: ${volume}")
      state.restoreLevelAfterUnmute = volume
    }
  }

  String mute = propertyset['property']['LastChange']['Event']['InstanceID'].children().findAll{it.name() == 'Mute' && it['@channel'] == 'Master'}['@val']
  if(mute) {
    String muted = mute == '1' ? 'muted' : 'unmuted'
    String previousMutedState = this.device.currentState('mute')?.value != null ? this.device.currentState('mute').value : 'unmuted'
    if(muted == 'unmuted' && previousMutedState == 'muted' && enableAirPlayUnmuteVolumeFix) {
      logDebug("Restoring volume after unmute event to level: ${state.restoreLevelAfterUnmute}")
      setLevel(state.restoreLevelAfterUnmute as Integer)
    }
    sendEvent(name:'mute', value: muted)
  }

  String bass = propertyset['property']['LastChange']['Event']['InstanceID']['Bass']['@val']
  if(bass) { sendEvent(name:'bass', value: bass as Integer) }


  String treble = propertyset['property']['LastChange']['Event']['InstanceID']['Treble']['@val']
  if(treble) { sendEvent(name:'treble', value: treble as Integer) }

  String loudness = propertyset['property']['LastChange']['Event']['InstanceID'].children().findAll{it.name() == 'Loudness' && it['@channel'] == 'Master'}['@val']
  if(loudness) { sendEvent(name:'loudness', value: loudness == 1 ? 'on' : 'off') }
}

void processZoneGroupTopologyMessages(Map message) {
  // logDebug("ZoneGroupTopology: ${message}")
  GPathResult propertyset = parseSonosMessageXML(message)

  String rincon = device.getDataValue('id')
  logDebug("RINCON: ${rincon}")
  // def group = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().findAll{it['@Coordinator'] == rincon}.children().each(){logDebug(it.name())}
  // String currentGroupName = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().findAll{it['@Coordinator'] == rincon}.children().findAll{it['@UUID'] == rincon}['@ZoneName']
  String currentGroupCoordinatorName = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}['@ZoneName']
  String currentGroupCoordinatorId = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent()['@Coordinator']
  String currentGroupId = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent()['@ID']
  String currentId = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent()['@ID']
  Integer currentGroupMemberCount = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent().children().size()
  List currentGroupMemberIds = []
  propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent().children().each{ currentGroupMemberIds.add(it['@UUID']) }
  logDebug("Current Group Member IDs: ${currentGroupMemberIds}")
  String groupName = propertyset['property']['ZoneGroupName'].text()
  // String currentGroupName = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().findAll{it['@Coordinator'] == rincon}.children().findAll{it['@UUID'] == rincon}['@ZoneName']
  // String currentGroupName = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().findAll{it['@Coordinator'] == rincon}.children().findAll{it['@UUID'] == rincon}['@ZoneName']
  logDebug("Current group name: ${currentGroupName} Current coordinator: ${currentGroupCoordinator} Current group member count: ${currentGroupMemberCount}")
  sendEvent(name: 'groupCoordinatorName', value: currentGroupCoordinatorName)
  sendEvent(name: 'groupCoordinatorId', value: currentGroupCoordinatorId)
  sendEvent(name: 'groupId', value: currentGroupId)
  sendEvent(name: 'isGrouped', value: currentGroupMemberCount > 1 ? 'on' : 'off')
  sendEvent(name: 'isGroupCoordinator', value: currentGroupCoordinatorId == device.getDataValue('id')  ? 'on' : 'off')
  sendEvent(name: 'groupMemberCount', value: currentGroupMemberCount)
  sendEvent(name: 'groupMemberIds' , value: currentGroupMemberIds)
  sendEvent(name: 'groupName', value: groupName)

  // logDebug(getObjectClassName(group))
}

void processHeaders(Map headers) {
  logDebug(headers['X-SONOS-SERVICETYPE'])
}

GPathResult parseSonosMessageXML(Map message) {
  String body = message.body.replace('&quot;','"').replace('&apos;',"'").replace('&lt;','<').replace('&gt;','>').replace('&amp;','&')
  GPathResult propertyset = parseXML(body)
  return propertyset
}

// =============================================================================
// Subscriptions and Resubscriptions
// =============================================================================

void subscribeToEvents() {
  String host = device.getDataValue('deviceIp')
  String dni = device.getDeviceNetworkId()
  logDebug("DNI ${dni}")

  if(device.getDataValue('sid1')) { sonosEventUnsubscribe('/MediaRenderer/AVTransport/Event', host, dni, device.getDataValue('sid1')) }
  if(device.getDataValue('sid2')) { sonosEventUnsubscribe('/MediaRenderer/RenderingControl/Event', host, dni, device.getDataValue('sid2')) }
  if(device.getDataValue('sid3')) { sonosEventUnsubscribe('/ZoneGroupTopology/Event', host, dni, device.getDataValue('sid3')) }

// sonosEventSubscribe(String eventSubURL, String host, String timeout, String dni)
  sonosEventSubscribe('/MediaRenderer/AVTransport/Event', host, RESUB_INTERVAL, dni)
  sonosEventSubscribe('/MediaRenderer/RenderingControl/Event', host, RESUB_INTERVAL, dni)
  sonosEventSubscribe('/ZoneGroupTopology/Event', host, RESUB_INTERVAL, dni)

  // sonosEventSubscribe('/MediaRenderer/Queue/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/MediaServer/ContentDirectory/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/MusicServices/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/SystemProperties/Event', host, RESUB_INTERVAL, dni)
  // sonosEventSubscribe('/AlarmClock/Event', host, RESUB_INTERVAL, dni)

  unschedule('resubscribeToEvents')
  runIn(RESUB_INTERVAL-100, 'resubscribeToEvents')
}


void resubscribeToEvents() {
  String host = device.getDataValue('deviceIp')
  String dni = device.getDeviceNetworkId()

  sonosEventRenew('/MediaRenderer/AVTransport/Event', host, RESUB_INTERVAL, dni, device.getDataValue('sid1'))
  sonosEventRenew('/MediaRenderer/RenderingControl/Event', host, RESUB_INTERVAL, dni, device.getDataValue('sid2'))
  sonosEventRenew('/ZoneGroupTopology/Event', host, RESUB_INTERVAL, dni, device.getDataValue('sid3'))
  runIn(RESUB_INTERVAL-100, 'resubscribeToEvents')
}

// =============================================================================
// Child Device Helpers
// =============================================================================

String getCrossfadeControlChildDNI() { return "${device.getDeviceNetworkId()}-CrossfadeControl" }
String getShuffleControlChildDNI() { return "${device.getDeviceNetworkId()}-ShuffleControl" }
String getRepeatOneControlChildDNI() { return "${device.getDeviceNetworkId()}-RepeatOneControl" }
String getRepeatAllControlChildDNI() { return "${device.getDeviceNetworkId()}-RepeatAllControl" }
String getMuteControlChildDNI() { return "${device.getDeviceNetworkId()}-MuteControl" }
ChildDeviceWrapper getCrossfadeControlChild() { return getChildDevice(getCrossfadeControlChildDNI()) }
ChildDeviceWrapper getShuffleControlChild() { return getChildDevice(getShuffleControlChildDNI()) }
ChildDeviceWrapper getRepeatOneControlChild() { return getChildDevice(getRepeatOneControlChildDNI()) }
ChildDeviceWrapper getRepeatAllControlChild() { return getChildDevice(getRepeatAllControlChildDNI()) }
ChildDeviceWrapper getMuteControlChild() { return getChildDevice(getMuteControlChildDNI()) }

// =============================================================================
//
// =============================================================================

void clearCurrentNextArtistAlbumTrackData() {
  device.deleteCurrentState('currentTrackDuration')
  device.deleteCurrentState('currentArtistName')
  device.deleteCurrentState('currentAlbumName')
  device.deleteCurrentState('currentTrackName')
  device.deleteCurrentState('nextArtistName')
  device.deleteCurrentState('nextAlbumName')
  device.deleteCurrentState('nextTrackName')
}

void clearTrackDataEvent() {
  device.deleteCurrentState('trackData')
}

List<String> getGroupMemberDNIs() {
  List groupMemberDNIs = []
  String groupMemberIds = this.device.currentState('groupMemberIds')?.value
  groupMemberIds = groupMemberIds != null ?  groupMemberIds.replace('[','').replace(']','') : ""
  logDebug("Getting group member DNIs... ${groupMemberIds}")
  if(groupMemberIds.contains(',')) {
    List groupMemberRincons = groupMemberIds.tokenize(',')
    groupMemberRincons.remove(this.device.getDataValue('id'))
    groupMemberRincons.each{it -> groupMemberDNIs.add("${it}".tokenize('_')[1][0..-6])}
    return groupMemberDNIs
  } else {
    return []
  }
}