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
    version: '0.7.0',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    singleThreaded: false,
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
  attribute 'nightMode', 'enum', [ 'on', 'off' ]
  attribute 'speechEnhancement', 'enum', [ 'on', 'off' ]
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
      if(deviceHasBattery() == true) {
        input 'createBatteryStatusChildDevice', 'bool', title: 'Create child device for battery status? (portable speakers only)', required: false, defaultValue: false
      }
      input 'createFavoritesChildDevice', 'bool', title: 'Create child device for favorites?', required: false, defaultValue: false
      if(getRightChannelRincon() != null && getRightChannelRincon() != '') {
        input 'createRightChannelChildDevice', 'bool', title: 'Create child device right channel? (stereo pair only)', required: false, defaultValue: false
      }
      if(hasHTPlaybackCapability() == true) {
        input 'createNightModeChildDevice', 'bool', title: 'Create child device for Night Mode control', required: false, defaultValue: false
        input 'createSpeechEnhancementChildDevice', 'bool', title: 'Create child device for Speech Enhancement control', required: false, defaultValue: false
      }
      if(hasAudioClipCapability() == true) {
        input 'chimeBeforeTTS', 'bool', title: 'Play chime before standard priority TTS messages', required: false, defaultValue: false
        input 'alwaysUseLoadAudioClip', 'bool', title: 'Always Use Non-Interrupting Methods', required: false, defaultValue: true
      }
    }
  }
}

// =============================================================================
// Preference Getters And Passthrough Renames For Clarity
// =============================================================================
Boolean getDisableTrackDataEvents() { return settings.disableTrackDataEvents != null ? settings.disableTrackDataEvents : true }
Boolean getIncludeTrackDataMetaData() { return settings.includeTrackDataMetaData != null ? settings.includeTrackDataMetaData : false }
Integer getVolumeAdjustAmountLow() { return settings.volumeAdjustAmountLow != null ? settings.volumeAdjustAmountLow as Integer : 5 }
Integer getVolumeAdjustAmountMid() { return settings.volumeAdjustAmountMid != null ? settings.volumeAdjustAmountMid as Integer : 5 }
Integer getVolumeAdjustAmount() { return settings.volumeAdjustAmount != null ? settings.volumeAdjustAmount as Integer : 5 }
Integer getTTSBoostAmount() { return settings.ttsBoostAmount != null ? settings.ttsBoostAmount as Integer : 10 }
Boolean getDisableArtistAlbumTrackEvents() { return settings.disableArtistAlbumTrackEvents != null ? settings.disableArtistAlbumTrackEvents : false }
Boolean getCreateCrossfadeChildDevice() { return settings.createCrossfadeChildDevice != null ? settings.createCrossfadeChildDevice : false }
Boolean getCreateShuffleChildDevice() { return settings.createShuffleChildDevice != null ? settings.createShuffleChildDevice : false }
Boolean getCreateRepeatOneChildDevice() { return settings.createRepeatOneChildDevice != null ? settings.createRepeatOneChildDevice : false }
Boolean getCreateRepeatAllChildDevice() { return settings.createRepeatAllChildDevice != null ? settings.createRepeatAllChildDevice : false }
Boolean getCreateBatteryStatusChildDevice() { return settings.createBatteryStatusChildDevice != null ? settings.createBatteryStatusChildDevice : false }
Boolean getCreateFavoritesChildDevice() { return settings.createFavoritesChildDevice != null ? settings.createFavoritesChildDevice : false }
Boolean getCreateRightChannelChildDevice() { return settings.createRightChannelChildDevice != null ? settings.createRightChannelChildDevice : false }
Boolean getCreateNightModeChildDevice() { return settings.createNightModeChildDevice != null ? settings.createNightModeChildDevice : false }
Boolean getCreateSpeechEnhancementChildDevice() { return settings.createSpeechEnhancementChildDevice != null ? settings.createSpeechEnhancementChildDevice : false }
Boolean getChimeBeforeTTS() { return settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false }
Boolean getAlwaysUseLoadAudioClip() { return settings.alwaysUseLoadAudioClip != null ? settings.alwaysUseLoadAudioClip : true }

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

@CompileStatic
Boolean hasHTPlaybackCapability() {
  if(device != null) {
    return getDeviceDataValue('capabilities').contains('HT_PLAYBACK')
  } else {return false}
}

@CompileStatic
Boolean hasAudioClipCapability() {
  if(device != null) {
    return getDeviceDataValue('capabilities').contains('AUDIO_CLIP')
  } else {return false}
}
// =============================================================================
// End Preference Getters And Passthrough Renames For Clarity
// =============================================================================



// =============================================================================
// Fields
// =============================================================================
@Field static ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>> audioClipQueue = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static ConcurrentHashMap<String, LinkedHashMap> audioClipQueueTimers = new ConcurrentHashMap<String, LinkedHashMap>()
@Field static java.util.concurrent.Semaphore deviceDataMutex = new java.util.concurrent.Semaphore(1)
@Field static ConcurrentHashMap<String, ArrayList<DeviceWrapper>> groupsRegistry = new ConcurrentHashMap<String, ArrayList<DeviceWrapper>>()
@Field static ConcurrentHashMap<String, LinkedHashMap<String,LinkedHashMap>> statesRegistry = new ConcurrentHashMap<String, LinkedHashMap<String,LinkedHashMap>>()
@Field static ConcurrentHashMap<String, LinkedHashMap> favoritesMap = new ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, Boolean> webSocketStatuses = new ConcurrentHashMap<String, Instant>()
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
@Field final Integer RESUB_INTERVAL = 3600

@Field private final String MRAVT_EVENTS =  '/MediaRenderer/AVTransport/Event'
@Field private final String MRAVT_EVENTS_CALLBACK =  'subscribeResubscribeToMrAvTCallback'
@Field private final String MRAVT_EVENTS_UNSUB_CALLBACK =  'unsubscribeToMrAvTCallback'
@Field private final String MRAVT_EVENTS_DOMAIN =  'MediaRenderer/AVTransport'

@Field private final String MRRC_EVENTS  =  '/MediaRenderer/RenderingControl/Event'
@Field private final String MRRC_EVENTS_CALLBACK  =  'subscribeResubscribeMrRcCallback'
@Field private final String MRRC_EVENTS_UNSUB_CALLBACK =  'unsubscribeFromMrRcEventsCallback'
@Field private final String MRRC_EVENTS_DOMAIN  =  'MediaRenderer/RenderingControl'


@Field private final String ZGT_EVENTS   =  '/ZoneGroupTopology/Event'
@Field private final String ZGT_EVENTS_CALLBACK  =  'subscribeResubscribeToZgtCallback'
@Field private final String ZGT_EVENTS_UNSUB_CALLBACK =  'unsubscribeFromZgtEventsCallback'
@Field private final String ZGT_EVENTS_DOMAIN  =  'ZoneGroupTopology'

@Field private final String MRGRC_EVENTS =  '/MediaRenderer/GroupRenderingControl/Event'
@Field private final String MRGRC_EVENTS_CALLBACK  =  'subscribeResubscribeToMrGrcCallback'
@Field private final String MRGRC_EVENTS_UNSUB_CALLBACK =  'unsubscribeFromMrGrcEventsCallback'
@Field private final String MRGRC_EVENTS_DOMAIN  =  'MediaRenderer/GroupRenderingControl'


// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Initialize and Configure
// =============================================================================
void initialize() {
  configure()
  fullRenewSubscriptions()
  // runEvery3Hours('fullRenewSubscriptions')
}
void configure() {
  atomicState.audioClipPlaying = false
  migrationCleanup()
  runIn(5, 'secondaryConfiguration')
}

void registerRinconId() {
  unschedule('registerRinconId')
  unschedule('checkSubscriptions')
}

void fullRenewSubscriptions() {
  unsubscribeFromAVTransport()
  unsubscribeFromMrRcEvents()
  unsubscribeFromZgtEvents()
  unsubscribeFromMrGrcEvents()
  if(webSocketStatuses == null) {webSocketStatuses = new ConcurrentHashMap<String, Boolean>()}
  runIn(2, 'initializeWebsocketConnection', [overwrite: true])
  runIn(7, 'subscribeToEvents', [overwrite: true])
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
  createRemoveNightModeChildDevice(getCreateNightModeChildDevice())
  createRemoveSpeechEnhancementChildDevice(getCreateSpeechEnhancementChildDevice())
  if(getDisableTrackDataEvents()) { clearTrackDataEvent() }
  if(getDisableArtistAlbumTrackEvents()) { clearCurrentNextArtistAlbumTrackData() }
  audioClipQueueInitialization()
  groupsRegistryInitialization()
  favoritesMapInitialization()
}

void migrationCleanup() {
  unschedule('resubscribeToGMEvents')
  unschedule('registerRinconId')
  unschedule('checkSubscriptions')
  if(settings.disableTrackDataEvents == null) { settings.disableTrackDataEvents = true }
  if(settings.includeTrackDataMetaData == null) { settings.includeTrackDataMetaData = false }
  if(settings.volumeAdjustAmountLow == null) { settings.volumeAdjustAmountLow = 5 }
  if(settings.volumeAdjustAmountMid == null) { settings.volumeAdjustAmountMid = 5 }
  if(settings.volumeAdjustAmount == null) { settings.volumeAdjustAmount = 5 }
  if(settings.ttsBoostAmount == null) { settings.ttsBoostAmount = 10 }
  if(settings.disableArtistAlbumTrackEvents == null) { settings.disableArtistAlbumTrackEvents = false }
  if(settings.createCrossfadeChildDevice == null) { settings.createCrossfadeChildDevice = false }
  if(settings.createShuffleChildDevice == null) { settings.createShuffleChildDevice = false }
  if(settings.createRepeatOneChildDevice == null) { settings.createRepeatOneChildDevice = false }
  if(settings.createRepeatAllChildDevice == null) { settings.createRepeatAllChildDevice = false }
  if(settings.createBatteryStatusChildDevice == null) { settings.createBatteryStatusChildDevice = false }
  if(settings.createFavoritesChildDevice == null) { settings.createFavoritesChildDevice = false }
  if(settings.createRightChannelChildDevice == null) { settings.createRightChannelChildDevice = false }
  if(settings.createNightModeChildDevice == null) { settings.createNightModeChildDevice = false }
  if(settings.createSpeechEnhancementChildDevice == null) { settings.createSpeechEnhancementChildDevice = false }
  if(settings.chimeBeforeTTS == null) { settings.chimeBeforeTTS = false }
  if(settings.alwaysUseLoadAudioClip == null) { settings.alwaysUseLoadAudioClip = true }
}

void audioClipQueueInitialization() {
  if(audioClipQueue == null) { audioClipQueue = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>() }
  if(!audioClipQueue.containsKey(getId())) {
    audioClipQueue[getId()] = new ConcurrentLinkedQueue<Map>()
  }
  if(audioClipQueueTimers == null) { audioClipQueueTimers = new ConcurrentHashMap<String, LinkedHashMap>() }
  if(!audioClipQueueTimers.containsKey(getId())) {
    audioClipQueueTimers[getId()] = new LinkedHashMap()
  }
}


void groupsRegistryInitialization() {
  if(groupsRegistry == null) {groupsRegistry = new ConcurrentHashMap<String, ArrayList<DeviceWrapper>>()}
  if(!groupsRegistry.containsKey(getId())) {groupsRegistry[getId()] = new ArrayList<DeviceWrapper>()}
}

void favoritesMapInitialization() {
  if(favoritesMap == null) {favoritesMap = new ConcurrentHashMap<String, LinkedHashMap>()}
}

@CompileStatic
void checkSubscriptions() {
  // try {if(!lastMrRcEventWithin(900)) {resubscribeToMrRcEvents()}}
  // catch(java.lang.NullPointerException e) {subscribeToMrRcEvents()}

  // try {if(!lastMrGrcEventWithin(900)) {resubscribeToMrGrcEvents()}}
  // catch(java.lang.NullPointerException e) {subscribeToMrGrcEvents()}

  // try {if(!lastZgtEventWithin(900)) {resubscribeToZgtEvents()}}
  // catch(java.lang.NullPointerException e) {subscribeToZgtEvents()}

  // try {if(!lastWebsocketEventWithin(900)) {renewWebsocketConnection()}}
  // catch(java.lang.NullPointerException e) {renewWebsocketConnection()}
}
// =============================================================================
// End Initialize and Configure
// =============================================================================



// =============================================================================
// Device Methods
// =============================================================================
@CompileStatic
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
@CompileStatic
void repeatOne() { setRepeatMode('repeat one') }
@CompileStatic
void repeatAll() { setRepeatMode('repeat all') }
@CompileStatic
void repeatNone() { setRepeatMode('off') }

@CompileStatic
void setCrossfade(String mode) {
  logDebug("Setting crossfade mode to ${mode}")
  Map playModes = mode == 'on' ? [ 'crossfade': true ] : [ 'crossfade': false ]
  playerSetPlayModes(playModes)
}
@CompileStatic
void enableCrossfade() { setCrossfade('on') }
@CompileStatic
void disableCrossfade() { setCrossfade('off') }

@CompileStatic
void setShuffle(String mode) {
  logDebug("Setting shuffle mode to ${mode}")
  Map playModes = mode == 'on' ? [ 'shuffle': true ] : [ 'shuffle': false ]
  playerSetPlayModes(playModes)
}
@CompileStatic
void shuffleOn() { setShuffle('on') }
@CompileStatic
void shuffleOff() { setShuffle('off') }

@CompileStatic
void ungroupPlayer() { playerCreateNewGroup() }

@CompileStatic
void playText(String text, BigDecimal volume = null) {
  if(getAlwaysUseLoadAudioClip()) { devicePlayText(text, volume) }
  else{ devicePlayTextNoRestore(text, volume) }
}
@CompileStatic
void playTextAndRestore(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
@CompileStatic
void playTextAndResume(String text, BigDecimal volume = null) { devicePlayText(text, volume) }
@CompileStatic
void speak(String text, BigDecimal volume = null, String voice = null) { devicePlayText(text, volume, voice) }

void setTrack(String uri) { componentSetStreamUrlLocal(uri, volume) }
void playTrack(String uri, BigDecimal volume = null) {
  if(getAlwaysUseLoadAudioClip()) { playerLoadAudioClip(uri, volume) }
  else{ componentLoadStreamUrlLocal(uri, volume) }
}
@CompileStatic
void playTrackAndRestore(String uri, BigDecimal volume = null) { playerLoadAudioClip(uri, volume) }
@CompileStatic
void playTrackAndResume(String uri, BigDecimal volume = null) { playerLoadAudioClip(uri, volume) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  if(volume) { volume += getTTSBoostAmount() }
  else { volume = getPlayerVolume() + getTTSBoostAmount() }
  LinkedHashMap tts = textToSpeech(text, voice)
  if(hasAudioClipCapability() == true) {
    playerLoadAudioClip(tts.uri, volume, tts.duration)
  } else {
    componentPlayTextNoRestoreLocal(text, volume, voice)
  }
}

void playHighPriorityTTS(String text, BigDecimal volume = null, String voice = null) {
  if(hasAudioClipCapability() == true) {
    playerLoadAudioClipHighPriority(textToSpeech(text, voice).uri, volume )
  } else {
    componentPlayTextNoRestoreLocal(textToSpeech(text, voice).uri, volume, voice)
  }
}

@CompileStatic
void playHighPriorityTrack(String uri, BigDecimal volume = null) {
  if(hasAudioClipCapability() == true) {
    playerLoadAudioClipHighPriority(uri, volume)
  } else {
    componentLoadStreamUrlLocal(uri, volume)
  }
}

void devicePlayTextNoRestore(String text, BigDecimal volume = null, String voice = null) {
  if(volume) { volume += getTTSBoostAmount() }
  else { volume = getPlayerVolume() + getTTSBoostAmount() }
  componentPlayTextNoRestoreLocal(text, volume, voice)
}

void devicePlayTrack(String uri, BigDecimal volume = null) {
  componentLoadStreamUrlLocal(uri, volume)
}

@CompileStatic
void mute(){ playerSetPlayerMute(true) }
@CompileStatic
void unmute(){ playerSetPlayerMute(false) }
void setLevel(BigDecimal level) { playerSetPlayerVolume(level as Integer) }
@CompileStatic
void setVolume(BigDecimal level) { setLevel(level) }
void setTreble(BigDecimal level) { componentSetTrebleLocal(level)}
void setBass(BigDecimal level) { componentSetBassLocal(level)}
void setLoudness(String mode) { componentSetLoudnessLocal(mode == 'on')}
void setBalance(BigDecimal level) { componentSetBalanceLocal(level)}

void muteGroup(){
  if(isGroupedAndCoordinator()) {
    playerSetGroupMute(true)
  } else if(isGroupedAndNotCoordinator()) {
    getDeviceFromRincon(getGroupCoordinatorId()).muteGroup()
  }
  else { playerSetPlayerMute(true) }
}
void unmuteGroup(){
  if(isGroupedAndCoordinator()) {
    playerSetGroupMute(false)
  } else if(isGroupedAndNotCoordinator()) {
    getDeviceFromRincon(getGroupCoordinatorId()).unmuteGroup()
  }
  else { playerSetPlayerMute(false) }
}
void setGroupVolume(BigDecimal level) {
  if(isGroupedAndCoordinator()) {
    playerSetGroupVolume(level)
  } else if(isGroupedAndNotCoordinator()) {
    getDeviceFromRincon(getGroupCoordinatorId()).setGroupVolume(level)
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
  } else if(isGroupedAndNotCoordinator()) {
    getDeviceFromRincon(getGroupCoordinatorId()).groupVolumeUp()
  }
  else { playerSetPlayerRelativeVolume(getPlayerVolumeAdjAmount()) }
}
void groupVolumeDown() {
  if(isGroupedAndCoordinator()) {
    playerSetGroupRelativeVolume(-getGroupVolumeAdjAmount())
  } else if(isGroupedAndNotCoordinator()) {
    getDeviceFromRincon(getGroupCoordinatorId()).groupVolumeDown()
  }
  else { playerSetPlayerRelativeVolume(-getPlayerVolumeAdjAmount()) }
}

@CompileStatic
void volumeUp() { playerSetPlayerRelativeVolume(getPlayerVolumeAdjAmount()) }
@CompileStatic
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

@CompileStatic
void play() { playerPlay() }
@CompileStatic
void stop() { playerStop() }
@CompileStatic
void pause() { playerPause() }
@CompileStatic
void nextTrack() { playerSkipToNextTrack() }
@CompileStatic
void previousTrack() { playerSkipToPreviousTrack() }
@CompileStatic
void subscribeToEvents() {
  subscribeToZgtEvents()
  subscribeToMrGrcEvents()
  subscribeToMrRcEvents()
}

@CompileStatic
void loadFavorite(String favoriteId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode) {
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode) {
  String autoPlay = 'true'
  String crossfadeMode = 'true'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
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
  if(getIsGroupCoordinator() == true) {
    playerLoadFavorite(favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)
  } else if(isGroupedAndNotCoordinator() == true) {
    getDeviceFromRincon(getGroupCoordinatorId()).loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
  }
}
// =============================================================================
// End Device Methods
// =============================================================================



// =============================================================================
// Child device methods
// =============================================================================
String getChildCommandFromDNI(DeviceWrapper dev) {
  if(dev.getDeviceNetworkId() == getCrossfadeControlChildDNI()) {return 'Crossfade'}
  if(dev.getDeviceNetworkId() == getShuffleControlChildDNI()) {return 'Shuffle'}
  if(dev.getDeviceNetworkId() == getRepeatOneControlChildDNI()) {return 'RepeatOne'}
  if(dev.getDeviceNetworkId() == getRepeatAllControlChildDNI()) {return 'RepeatAll'}
  if(dev.getDeviceNetworkId() == getMuteControlChildDNI()) {return 'Mute'}
  if(dev.getDeviceNetworkId() == getBatteryStatusChildDNI()) {return 'BatteryStatus'}
  if(dev.getDeviceNetworkId() == getFavoritesChildDNI()) {return 'Favorites'}
  if(dev.getDeviceNetworkId() == getRightChannelChildDNI()) {return 'RightChannel'}
  if(dev.getDeviceNetworkId() == getNightModeChildDNI()) {return 'NightMode'}
  if(dev.getDeviceNetworkId() == getSpeechEnhancementChildDNI()) {return 'SpeechEnhancement'}
}

@CompileStatic
void componentRefresh(DeviceWrapper child) {
  String command = getChildDeviceDataValue(child, 'command')
  if(command == null) {command = getChildCommandFromDNI(child) }
  logDebug("Child: ${child} command: ${command}")
  switch(command) {
    case 'Crossfade':
      getCrossfadeControlChild().sendEvent(name:'switch', value: getDeviceCurrentStateValue('currentCrossfadeMode') )
    break
    case 'Shuffle':
      getShuffleControlChild().sendEvent(name:'switch', value: getDeviceCurrentStateValue('currentShuffleMode') )
    break
    case 'RepeatOne':
      getRepeatOneControlChild().sendEvent(name:'switch', value: getDeviceCurrentStateValue('currentRepeatOneMode'))
    break
    case 'RepeatAll':
      getRepeatAllControlChild().sendEvent(name:'switch', value: getDeviceCurrentStateValue('currentRepeatAllMode') )
    break
    case 'Mute':
      String muteValue = getDeviceCurrentStateValue('mute') != null ? getDeviceCurrentStateValue('mute') : 'unmuted'
      getMuteControlChild().sendEvent(name:'switch', value: muteValue )
    break
    case 'NightMode':
      getNightModeChild().sendEvent(name:'switch', value: getDeviceCurrentStateValue('nightMode') )
    break
    case 'SpeechEnhancement':
      getSpeechEnhancementChild().sendEvent(name:'switch', value: getDeviceCurrentStateValue('speechEnhancement') )
    break
  }
}

@CompileStatic
void componentOn(DeviceWrapper child) {
  String command = child.getDataValue('command').toString()
  if(command == null) {command = getChildCommandFromDNI(child) }
  logDebug("Child: ${child} command: ${command}")
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
    case 'NightMode':
      setNightMode(true)
    break
    case 'SpeechEnhancement':
      setDialogMode(true)
    break
  }
}

@CompileStatic
void componentOff(DeviceWrapper child) {
  String command = child.getDataValue('command')
  if(command == null) {command = getChildCommandFromDNI(child) }
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
    case 'NightMode':
      setNightMode(false)
    break
    case 'SpeechEnhancement':
      setDialogMode(false)
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
String getNightModeChildDNI() { return "${device.getDeviceNetworkId()}-NightMode" }
String getSpeechEnhancementChildDNI() { return "${device.getDeviceNetworkId()}-SpeechEnhancement" }
ChildDeviceWrapper getCrossfadeControlChild() { return getChildDevice(getCrossfadeControlChildDNI()) }
ChildDeviceWrapper getShuffleControlChild() { return getChildDevice(getShuffleControlChildDNI()) }
ChildDeviceWrapper getRepeatOneControlChild() { return getChildDevice(getRepeatOneControlChildDNI()) }
ChildDeviceWrapper getRepeatAllControlChild() { return getChildDevice(getRepeatAllControlChildDNI()) }
ChildDeviceWrapper getMuteControlChild() { return getChildDevice(getMuteControlChildDNI()) }
ChildDeviceWrapper getBatteryStatusChild() { return getChildDevice(getBatteryStatusChildDNI()) }
ChildDeviceWrapper getFavoritesChild() { return getChildDevice(getFavoritesChildDNI()) }
ChildDeviceWrapper getRightChannelChild() { return getChildDevice(getRightChannelChildDNI()) }
ChildDeviceWrapper getNightModeChild() { return getChildDevice(getNightModeChildDNI()) }
ChildDeviceWrapper getSpeechEnhancementChild() { return getChildDevice(getSpeechEnhancementChildDNI()) }
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
  if(device != null) {
        Map params = [uri: "${getLocalUpnpUrl()}/status/batterystatus"]
    httpGet(params) {resp ->
      if(resp.status == 200) {
        return resp.data.children().find{it.name() == 'LocalBatteryStatus'}.size() > 0
      } else { return false }
    }
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

void createRemoveNightModeChildDevice(Boolean create) {
  String dni = getNightModeChildDNI()
  ChildDeviceWrapper child = getNightModeChild()
  if(!child && create) {
    try {
      logDebug("Creating NightMode device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos NightMode Control',
          label: "Sonos NightMode - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'NightMode')
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveNightModeChildDevice', e)
    }
  }
  else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveSpeechEnhancementChildDevice(Boolean create) {
  String dni = getSpeechEnhancementChildDNI()
  ChildDeviceWrapper child = getSpeechEnhancementChild()
  if(!child && create) {
    try {
      logDebug("Creating SpeechEnhancement device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos SpeechEnhancement Control',
          label: "Sonos SpeechEnhancement - ${this.getDataValue('name')}"]
      )
      child.updateDataValue('command', 'SpeechEnhancement')
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveSpeechEnhancementChildDevice', e)
    }
  }
  else if(!create && child){ deleteChildDevice(dni) }
}
// =============================================================================
// Create Child Devices
// =============================================================================



// =============================================================================
// Parse
// =============================================================================
@CompileStatic
void parse(String raw) {
  if(raw == null || raw == '') {return}
  try {
    if(!raw.startsWith('mac:')){
      setLastWebsocketEvent()
      processWebsocketMessage(raw)
      return
    }
  } catch (Exception e) { logWarn("Ran into an issue parsing websocket: ${e}") }
  try {
    LinkedHashMap message = getMapForRaw(raw)
    LinkedHashMap messageHeaders = (LinkedHashMap)message?.headers
    String xmlBody = (String)message?.body
    if(messageHeaders == null || messageHeaders.size() < 1) {return}
    if(xmlBody == null || xmlBody == '') {return}
    String serviceType = messageHeaders["X-SONOS-SERVICETYPE"]
    if(serviceType == 'AVTransport' || messageHeaders.containsKey('NOTIFY /avt HTTP/1.1')) {
      try {
        processAVTransportMessages(xmlBody, getLocalUpnpUrl())
      } catch (Exception e) { logWarn("Ran into an issue parsing avt: ${e}") }
    }
    else if(serviceType == 'RenderingControl' || messageHeaders.containsKey('NOTIFY /mrc HTTP/1.1')) {
      try {
        setLastInboundMrRcEvent()
        processRenderingControlMessages(xmlBody)
      } catch (Exception e) { logWarn("Ran into an issue parsing mrc: ${e}") }
    }
    else if(serviceType == 'ZoneGroupTopology' || messageHeaders.containsKey('NOTIFY /zgt HTTP/1.1')) {
      try {
        if(xmlBody.contains('ThirdPartyMediaServersX') || xmlBody.contains('AvailableSoftwareUpdate')) { return }
        LinkedHashSet<String> oldGroupedRincons = new LinkedHashSet<String>()
        if(getGroupPlayerIds() != null) {
          oldGroupedRincons = new LinkedHashSet((getGroupPlayerIds()))
        }
        setLastInboundZgtEvent()
        processZoneGroupTopologyMessages(xmlBody, oldGroupedRincons)
      } catch (Exception e) { logWarn("Ran into an issue parsing zgt: ${e}") }
    }
    else if(serviceType == 'GroupRenderingControl' || messageHeaders.containsKey('NOTIFY /mgrc HTTP/1.1')) {
      try {
        setLastInboundMrGrcEvent()
        processGroupRenderingControlMessages(xmlBody)
      } catch (Exception e) { logWarn("Ran into an issue parsing mgrc: ${e}") }
    }
    else {
      logDebug("Could not determine service type for message: ${message}")
    }
  } catch (Exception e) { logWarn("parse() ran into an issue: ${e}") }
}

LinkedHashMap getMapForRaw(String raw) {
  return parseLanMessage(raw)
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
    if(!getDisableTrackDataEvents()) {
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
  if(nextTrackMetaData != null && nextTrackMetaData != '') {
    GPathResult nextTrackMetaDataXML = new XmlSlurper().parseText(nextTrackMetaData)
    if(nextTrackMetaDataXML) {
      String nextArtistName = nextTrackMetaDataXML['item']['creator']
      if(nextArtistName != null && nextArtistName != '') { setNextArtistName(nextArtistName) }
      else { setNextArtistName('Not Available') }

      String nextAlbumName = nextTrackMetaDataXML['item']['album']
      if(nextAlbumName != null && nextAlbumName != '') { setNextAlbumName(nextAlbumName) }
      else { setNextAlbumName('Not Available') }

      String nextTrackName = nextTrackMetaDataXML['item']['title']
      if(nextTrackName != null && nextTrackName != '') { setNextTrackName(nextTrackName) }
      else { setNextTrackName('Not Available') }

    }
  } else {
    setNextArtistName('Not Available')
    setNextAlbumName('Not Available')
    setNextTrackName('Not Available')
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

  String currentGroupCoordinatorName = zoneGroups.children().children().findAll{it['@UUID'] == getId()}['@ZoneName']
  if(currentGroupCoordinatorName) {setGroupCoordinatorName(currentGroupCoordinatorName)}

  String currentGroupCoordinatorRincon = zoneGroups.children().children().findAll{it['@UUID'] == getId()}.parent()['@Coordinator']
  if(currentGroupCoordinatorRincon) {setGroupCoordinatorId(currentGroupCoordinatorRincon)}

  Boolean isGroupCoordinator = currentGroupCoordinatorRincon == getId()

  if(groupedRincons != oldGroupedRincons) {
    List<String> newRincons = new ArrayList<String>()
    newRincons.addAll(groupedRincons - oldGroupedRincons)
    if(newRincons.size() > 0 && isGroupCoordinator == true) {
      logTrace("Sending events to newly joined member(s): ${newRincons}")
      sendEventsToNewGroupMembers(newRincons)
    }
    logTrace('ZGT message parsed, group member changes found.')
  } else {
    logTrace('ZGT message parsed, no group member changes.')
  }

  setGroupPlayerIds(groupedRincons.toList())


  LinkedHashSet currentGroupMemberNames = []
  groupedRincons.each{ gr ->
    currentGroupMemberNames.add(zoneGroups.children().children().findAll{it['@UUID'] == gr}['@ZoneName']) }
  Integer currentGroupMemberCount = groupedRincons.size()


  String groupName = ((GPathResult)propertyset['property']['ZoneGroupName']).text().toString()
  if(groupName && groupedRincons) {
    updateZoneGroupName(groupName)
  }

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

@CompileStatic
void clearCurrentPlayingStates() {
  setCurrentArtistName('Not Available')
  setCurrentAlbumName('Not Available')
  setCurrentTrackName('Not Available')
  setCurrentTrackNumber(0)
  setTrackDataEvents([:])
  setNextArtistName('Not Available')
  setNextAlbumName('Not Available')
  setNextTrackName('Not Available')
  sendDeviceEvent('albumArtURI' ,'Not Available')
  sendDeviceEvent('currentFavorite','Not Available')
  sendDeviceEvent('trackDescription','Not Available')
}

void parentUpdateGroupDevices(String coordinatorId, List<String> playersInGroup) {
  if(coordinatorId == null || coordinatorId == '') {return}
  if(playersInGroup == null || playersInGroup.size() == 0) {return}
  parent?.updateGroupDevices(coordinatorId, playersInGroup)
}

@CompileStatic
void updateZoneGroupName(String groupName) {
  sendDeviceEvent('groupName', groupName)
  sendGroupEvents()
}

@CompileStatic
void processGroupRenderingControlMessages(String xmlString) {
  GPathResult propertyset = new XmlSlurper().parseText(xmlString)
  GPathResult gVol = ((GPathResult)propertyset.children().children()).find{GPathResult it -> it.name() == 'GroupVolume'}
  Integer groupVolume = Integer.parseInt(gVol.text())
  GPathResult gMute = ((GPathResult)propertyset.children().children()).find{GPathResult it -> it.name() == 'GroupMute'}
  String groupMute = Integer.parseInt(gMute.text()) == 1 ? 'muted' : 'unmuted'

  setGroupVolumeState(groupVolume)
  setGroupMuteState(groupMute)
  sendGroupEvents()
}

@CompileStatic
void setGroupVolumeState(Integer groupVolume) {
  sendDeviceEvent('groupVolume', groupVolume)
}
@CompileStatic
Integer getGroupVolumeState() {
  return getDevice().currentValue('groupVolume',true) as Integer
}

@CompileStatic
void setGroupMuteState(String groupMute) {
  sendDeviceEvent('groupMute', groupMute)
}
@CompileStatic
String getGroupMuteState() {
  return getDevice().currentValue('groupMute',true).toString()
}

@CompileStatic
void setNightModeState(String value) {
  sendDeviceEvent('nightMode', value)
  sendChildEvent(getNightModeChild(), 'switch', value)
}

@CompileStatic
void setDialogLevelState(String value) {
  sendDeviceEvent('speechEnhancement', value)
  sendChildEvent(getSpeechEnhancementChild(), 'switch', value)
}


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

  String nightMode = instanceId['NightMode']['@val']
  if(nightMode) { setNightModeState(nightMode == '1' ? 'on' : 'off') }

  String dialogLevel = instanceId['DialogLevel']['@val']
  if(dialogLevel) { setDialogLevelState(dialogLevel == '1' ? 'on' : 'off') }
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
Boolean hasSid(String sid) {
  return device.getDataValue(sid) != null
}

DeviceWrapper getThisDevice() {
  return this.device as DeviceWrapper
}
InstalledAppWrapper getParentApp() {
  return getParent() as InstalledAppWrapper
}
String getSid(String sid) {
  return device.getDataValue(sid)
}
void setSid(String sid, String value) {
  setDeviceDataValue(sid, value)
}
void deleteSid(String sid) {
  device.removeDataValue(sid)
  device.removeDataValue("${sid}-expires")
}
@CompileStatic
Boolean subValid(String sid) {
  Long exp = getDeviceDataValue("${sid}-expires") as Long
  if(exp > Instant.now().getEpochSecond() && hasSid(sid) == true) {
    return true
  } else {
    return false
  }
}
@CompileStatic
void updateSid(String sid, Map headers) {
  setDeviceDataValue("${sid}-expires", (Instant.now().getEpochSecond() + (2*RESUB_INTERVAL)).toString())
  if(headers["SID"]) {setSid(sid, headers["SID"].toString())}
  if(headers["sid"]) {setSid(sid, headers["sid"].toString())}
}

void scheduleResubscriptionToEvents(String eventsToResub) {
  runIn(RESUB_INTERVAL-100, eventsToResub, [overwrite: true])
}

void retrySubscription(String eventsToRetry, Integer retryTime = 60) {
  runIn(retryTime, eventsToRetry, [overwrite: true])
}

void removeResub(String resub) {
  unschedule(resub)
}

// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/AVTransport/Event' //sid1
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToAVTransport() {
  String subId = 'sid1'
  String subPath = '/avt'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_CALLBACK
  if(subValid(subId)) { resubscribeToAVTransport() }
  else { sonosEventSubscribe(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), subPath, callback) }
}

@CompileStatic
void resubscribeToAVTransport() {
  String subId = 'sid1'
  String resub = 'resubscribeToAVTransport'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_CALLBACK
  if(subValid(subId)) {
    sonosEventRenew(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), getSid(subId), callback)
  } else { subscribeToAVTransport() }
  scheduleResubscriptionToEvents(resub)
}

@CompileStatic
void subscribeResubscribeToMrAvTCallback(HubResponse response) {
  String sub = 'subscribeToAVTransport'
  String resub = 'resubscribeToAVTransport'
  String subId = 'sid1'
  String domain = MRAVT_EVENTS_DOMAIN
  if(response?.status == 412){
    if(hasSid(subId)) {
      logTrace("Failed to resubscribe to ${domain}. Will try again in 5 seconds.")
      deleteSid(subId)
      retrySubscription(sub, 5)
    } else {
      logInfo("Failed to subscribe to ${domain}. Will try again in 60 seconds.")
      retrySubscription(sub, 60)
    }
  } else if(response?.status == 200) {
    logTrace("Sucessfully subscribed to ${domain}")
    updateSid(subId, response.headers)
    scheduleResubscriptionToEvents(resub)
  }
}

@CompileStatic
void unsubscribeFromAVTransport() {
  String subId = 'sid1'
  String resub = 'resubscribeToAVTransport'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_UNSUB_CALLBACK
  if(subValid(subId)) {
    sonosEventUnsubscribe(evtSub, getlocalUpnpHost(), getDNI(), getSid(subId), callback)
    removeResub(resub)
  } else if (hasSid(subId)) {deleteSid(subId)}
}

@CompileStatic
void unsubscribeToMrAvTCallback(HubResponse response) {
  String subId = 'sid1'
  String domain = MRAVT_EVENTS_DOMAIN
  if(response?.status == 412){
    logTrace("Failed to unsubscribe to ${domain}. This is likely due to not currently being subscribed and is safely ignored.")
  } else if(response?.status == 200) {
    logTrace("Sucessfully unsubscribed to ${domain}")
  }
  deleteSid(subId)
}


// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/RenderingControl/Event' //sid2
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToMrRcEvents() {
  String subId = 'sid2'
  String subPath = '/mrc'
  String evtSub = MRRC_EVENTS
  String callback = MRRC_EVENTS_CALLBACK
  if(subValid(subId)) { resubscribeToMrRcEvents() }
  else { sonosEventSubscribe(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), subPath, callback) }
}

@CompileStatic
void resubscribeToMrRcEvents() {
  String subId = 'sid2'
  String resub = 'resubscribeToMrRcEvents'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_CALLBACK
  if(subValid(subId)) {
    sonosEventRenew(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), getSid(subId), callback)
  } else { subscribeToMrRcEvents() }
  scheduleResubscriptionToEvents(resub)
}

@CompileStatic
void subscribeResubscribeMrRcCallback(HubResponse response) {
  String sub = 'subscribeToMrRcEvents'
  String resub = 'resubscribeToMrRcEvents'
  String subId = 'sid2'
  String domain = MRRC_EVENTS_DOMAIN
  if(response?.status == 412){
    if(hasSid(subId)) {
      logTrace("Failed to resubscribe to ${domain}. Will try again in 5 seconds.")
      deleteSid(subId)
      retrySubscription(sub, 5)
    } else {
      logInfo("Failed to subscribe to ${domain}. Will try again in 60 seconds.")
      retrySubscription(sub, 60)
    }
  } else if(response?.status == 200) {
    logTrace("Sucessfully subscribed to ${domain}")
    updateSid(subId, response.headers)
    scheduleResubscriptionToEvents(resub)
  }
}

@CompileStatic
void unsubscribeFromMrRcEvents() {
  String subId = 'sid2'
  String resub = 'resubscribeToMrRcEvents'
  String evtSub = MRRC_EVENTS
  String callback = MRRC_EVENTS_UNSUB_CALLBACK
  if(subValid(subId)) {
    sonosEventUnsubscribe(evtSub, getlocalUpnpHost(), getDNI(), getSid(subId), callback)
    removeResub(resub)
  } else if (hasSid(subId)) {deleteSid(subId)}
}

@CompileStatic
void unsubscribeFromMrRcEventsCallback(HubResponse response) {
  String subId = 'sid2'
  String domain = MRRC_EVENTS_DOMAIN
  if(response?.status == 412){
    logTrace("Failed to unsubscribe to ${domain}. This is likely due to not currently being subscribed and is safely ignored.")
  } else if(response?.status == 200) {
    logTrace("Sucessfully unsubscribed to ${domain}")
  }
  deleteSid(subId)
}

// /////////////////////////////////////////////////////////////////////////////
// '/ZoneGroupTopology/Event' //sid3
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToZgtEvents() {
  String subId = 'sid3'
  String subPath = '/zgt'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_CALLBACK
  if(subValid(subId)) { resubscribeToZgtEvents() }
  else { sonosEventSubscribe(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), subPath, callback) }
}

@CompileStatic
void resubscribeToZgtEvents() {
  String subId = 'sid3'
  String resub = 'resubscribeToZgtEvents'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_CALLBACK
  if(subValid(subId)) {
    sonosEventRenew(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), getSid(subId), callback)
  } else { subscribeToZgtEvents() }
  scheduleResubscriptionToEvents(resub)
}

@CompileStatic
void subscribeResubscribeToZgtCallback(HubResponse response) {
  String sub = 'subscribeToZgtEvents'
  String resub = 'resubscribeToZgtEvents'
  String subId = 'sid3'
  String domain = ZGT_EVENTS_DOMAIN
  if(response?.status == 412){
    if(hasSid(subId)) {
      logTrace("Failed to resubscribe to ${domain}. Will try again in 5 seconds.")
      deleteSid(subId)
      retrySubscription(sub, 5)
    } else {
      logInfo("Failed to subscribe to ${domain}. Will try again in 60 seconds.")
      retrySubscription(sub, 60)
    }
  } else if(response?.status == 200) {
    logTrace("Sucessfully subscribed to ${domain}")
    updateSid(subId, response.headers)
    scheduleResubscriptionToEvents(resub)
  }
}

void resubscribeToZgtCallback(HubResponse response) {
  if(response?.status == 412){
    logWarn('Failed to resubscribe to ZoneGroupTopology. Will trying subscribing again in 30 seconds.')
    deleteSid('sid3')
    retrySubscription('subscribeToZgtEvents')
  } else if(response?.status == 200) {
    logTrace('Sucessfully resubscribed to ZoneGroupTopology')
    updateSid('sid3', response.headers)
  }
}

@CompileStatic
void unsubscribeFromZgtEvents() {
  String subId = 'sid3'
  String resub = 'resubscribeToZgtEvents'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_UNSUB_CALLBACK
  if(subValid(subId)) {
    sonosEventUnsubscribe(evtSub, getlocalUpnpHost(), getDNI(), getSid(subId), callback)
    removeResub(resub)
  } else if (hasSid(subId)) {deleteSid(subId)}
}

@CompileStatic
void unsubscribeFromZgtEventsCallback(HubResponse response) {
  String subId = 'sid3'
  String domain = ZGT_EVENTS_DOMAIN
  if(response?.status == 412){
    logTrace("Failed to unsubscribe to ${domain}. This is likely due to not currently being subscribed and is safely ignored.")
  } else if(response?.status == 200) {
    logTrace("Sucessfully unsubscribed to ${domain}")
  }
  deleteSid(subId)
}
// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/GroupRenderingControl/Event' //sid4
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToMrGrcEvents() {
  String subId = 'sid4'
  String subPath = '/mgrc'
  String evtSub = MRGRC_EVENTS
  String callback = MRGRC_EVENTS_CALLBACK
  if(subValid(subId)) { resubscribeToMrGrcEvents() }
  else { sonosEventSubscribe(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), subPath, callback) }
}

@CompileStatic
void resubscribeToMrGrcEvents() {
  String subId = 'sid4'
  String resub = 'resubscribeToMrGrcEvents'
  String evtSub = MRGRC_EVENTS
  String callback = MRGRC_EVENTS_CALLBACK
  if(subValid(subId)) {
    sonosEventRenew(evtSub, getlocalUpnpHost(), RESUB_INTERVAL, getDNI(), getSid(subId), callback)
  } else { subscribeToMrGrcEvents() }
  scheduleResubscriptionToEvents(resub)
}

@CompileStatic
void subscribeResubscribeToMrGrcCallback(HubResponse response) {
  String sub = 'subscribeToMrGrcEvents'
  String resub = 'resubscribeToMrGrcEvents'
  String subId = 'sid4'
  String domain = MRGRC_EVENTS_DOMAIN
  if(response?.status == 412){
    if(hasSid(subId)) {
      logTrace("Failed to resubscribe to ${domain}. Will try again in 5 seconds.")
      deleteSid(subId)
      retrySubscription(sub, 5)
    } else {
      logInfo("Failed to subscribe to ${domain}. Will try again in 60 seconds.")
      retrySubscription(sub, 60)
    }
  } else if(response?.status == 200) {
    logTrace("Sucessfully subscribed to ${domain}")
    updateSid(subId, response.headers)
    scheduleResubscriptionToEvents(resub)
  }
}

@CompileStatic
void unsubscribeFromMrGrcEvents() {
  String subId = 'sid4'
  String resub = 'resubscribeToMrGrcEvents'
  String evtSub = MRGRC_EVENTS
  String callback = MRGRC_EVENTS_UNSUB_CALLBACK
  if(subValid(subId)) {
    sonosEventUnsubscribe(evtSub, getlocalUpnpHost(), getDNI(), getSid(subId), callback)
    removeResub(resub)
  } else if (hasSid(subId)) {deleteSid(subId)}
}

@CompileStatic
void unsubscribeFromMrGrcEventsCallback(HubResponse response) {
  String subId = 'sid4'
  String domain = MRGRC_EVENTS_DOMAIN
  if(response?.status == 412){
    logTrace("Failed to unsubscribe to ${domain}. This is likely due to not currently being subscribed and is safely ignored.")
  } else if(response?.status == 200) {
    logTrace("Sucessfully unsubscribed to ${domain}")
  }
  deleteSid(subId)
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
// End Misc Helpers
// =============================================================================




// =============================================================================
// Getters and Setters
// =============================================================================
String getLocalApiUrl(){
  return "https://${getDeviceDataValue('deviceIp')}:1443/api/v1/"
}
String getLocalUpnpHost(){
  return "${getDeviceDataValue('deviceIp')}:1400"
}
String getLocalUpnpUrl(){
  return "http://${getDeviceDataValue('deviceIp')}:1400"
}
String getLocalWsUrl(){
  return "wss://${getDeviceDataValue('deviceIp')}:1443/websocket/api"
}

List<String> getLocalApiUrlSecondaries(){
  List<String> secondaryDeviceIps = getDeviceDataValue('secondaryDeviceIps').tokenize(',')
  return secondaryDeviceIps.collect{"https://${it}:1443/api/v1/"}
}
List<String> getLocalUpnpHostSecondaries(){
  List<String> secondaryDeviceIps = getDeviceDataValue('secondaryDeviceIps').tokenize(',')
  return secondaryDeviceIps.collect{"${it}:1400"}
}
List<String> getLocalUpnpUrlSecondaries(){
  List<String> secondaryDeviceIps = getDeviceDataValue('secondaryDeviceIps').tokenize(',')
  return secondaryDeviceIps.collect{"http://${it}:1400"}
}

String getRightChannelRincon() {
  return getDeviceDataValue('rightChannelId')
}
void setRightChannelRincon(String rincon) {
  logTrace("Setting right channel RINCON to ${rincon}")
  setDeviceDataValue('rightChannelId', rincon)
  if(getSecondaryIds()) {
    Long secondaryIndex = getSecondaryIds().findIndexValues{it == rincon}[0]
    String rightChannelIpAddress = getSecondaryDeviceIps()[secondaryIndex as Integer]
    setRightChannelDeviceIp(rightChannelIpAddress)
  }
}

String getRightChannelDeviceIp() {
  return getDeviceDataValue('rightChannelDeviceIp')
}
void setRightChannelDeviceIp(String ipAddress) {
  setDeviceDataValue('rightChannelDeviceIp', ipAddress)
}

String getHouseholdId(){
  return getDeviceDataValue('householdId')
}
void setHouseholdId(String householdId) {
  setDeviceDataValue('householdId', householdId)
}

@CompileStatic
Instant getLastInboundMrRcEvent() {
  return Instant.parse(getDeviceDataValue('lastMrRcEvent'))
}
@CompileStatic
Instant setLastInboundMrRcEvent() {
  return setDeviceDataValue('lastMrRcEvent', Instant.now().toString())
}
@CompileStatic
Boolean lastMrRcEventWithin(Long seconds) {
  logTrace("lastMrRcEvent Now: ${Instant.now().getEpochSecond()} - last message: ${getLastInboundMrRcEvent().getEpochSecond()}, difference: ${Instant.now().getEpochSecond() - getLastInboundMrRcEvent().getEpochSecond()}")
  return Instant.now().getEpochSecond() - getLastInboundMrRcEvent().getEpochSecond() < seconds
}

@CompileStatic
Instant getLastInboundZgtEvent() {
  return Instant.parse(getDeviceDataValue('lastZgtEvent'))
}
@CompileStatic
Instant setLastInboundZgtEvent() {
  return setDeviceDataValue('lastZgtEvent', Instant.now().toString())
}
@CompileStatic
Boolean lastZgtEventWithin(Integer seconds) {
  logTrace("lastZgtEvent Now: ${Instant.now().getEpochSecond()} - last message: ${getLastInboundZgtEvent().getEpochSecond()}, difference: ${Instant.now().getEpochSecond() - getLastInboundZgtEvent().getEpochSecond()}")
  return Instant.now().getEpochSecond() - getLastInboundZgtEvent().getEpochSecond() < seconds
}

@CompileStatic
Instant getLastInboundMrGrcEvent() {
  return Instant.parse(getDeviceDataValue('lastMrGrcEvent'))
}
@CompileStatic
Instant setLastInboundMrGrcEvent() {
  return setDeviceDataValue('lastMrGrcEvent', Instant.now().toString())
}
@CompileStatic
Boolean lastMrGrcEventWithin(Integer seconds) {
  logTrace("lastMrGrcEvent Now: ${Instant.now().getEpochSecond()} - last message: ${getLastInboundMrGrcEvent().getEpochSecond()}, difference: ${Instant.now().getEpochSecond() - getLastInboundMrGrcEvent().getEpochSecond()}")
  return Instant.now().getEpochSecond() - getLastInboundMrGrcEvent().getEpochSecond() < seconds
}

@CompileStatic
Instant getLastWebsocketEvent() {
  return Instant.parse(getDeviceDataValue('lastWebsocketEvent'))
}
@CompileStatic
Instant setLastWebsocketEvent() {
  return setDeviceDataValue('lastWebsocketEvent', Instant.now().toString())
}
@CompileStatic
Boolean lastWebsocketEventWithin(Integer seconds) {
  logTrace("lastWebsocketEvent Now: ${Instant.now().getEpochSecond()} - last message: ${getLastWebsocketEvent().getEpochSecond()}, difference: ${Instant.now().getEpochSecond() - getLastWebsocketEvent().getEpochSecond()}")
  return Instant.now().getEpochSecond() - getLastWebsocketEvent().getEpochSecond() < seconds
}

DeviceWrapper getDevice() { return this.device }

LinkedHashMap getDeviceSettings() { return this.settings }

String getDeviceDataValue(String name) {
  if(this.device != null) {
    synchronized(deviceDataMutex) {
      return this.device.getDataValue(name)
    }
  }
}
void setDeviceDataValue(String name, String value) {
  if(this.device != null) {
    synchronized(deviceDataMutex) {
      this.device.updateDataValue(name, value)
    }
  }
}

Object getDeviceCurrentStateValue(String name, Boolean skipCache = false) {
  if(this.device != null) {
    synchronized(deviceDataMutex) {
      return this.device.currentValue(name, skipCache)
    }
  }
}

void sendDeviceEvent(String name, Object value) { this.device.sendEvent(name:name, value:value) }

void sendChildEvent(ChildDeviceWrapper child, String name, Object value) {
  if(child != null) {child.sendEvent(name:name, value:value)}
}

Object getChildCurrentStateValue(DeviceWrapper child, String name, Boolean skipCache = false) {
  if(child != null) {child.currentValue(name, skipCache)}
}

String getChildDeviceDataValue(DeviceWrapper child, String name) {
  if(child != null) { return child.getDataValue(name) }
}

String getDeviceDNI() { return this.device.getDeviceNetworkId() }

@CompileStatic
String getId() { return getDeviceDataValue('id')
}
@CompileStatic
void setId(String id) { setDeviceDataValue('id', id)
}
List<String> getSecondaryIds() {
  return getDeviceDataValue('secondaryIds').tokenize(',')
}
void setSecondaryIds(List<String> ids) {
  setDeviceDataValue('secondaryIds', ids.join(','))
}
Boolean hasSecondaries() {
  return getDeviceDataValue('secondaryIds') && getDeviceDataValue('secondaryIds').size() > 0
}
String getRightChannelId() {
  return getDeviceDataValue('rightChannelId')
}

String getDeviceIp() {
  return getDeviceDataValue('deviceIp')
}
void setDeviceIp(String ipAddress) {
  setDeviceDataValue('deviceIp', ipAddress)
}
List<String> getSecondaryDeviceIps() {
  return getDeviceDataValue('secondaryDeviceIps').tokenize(',')
}
void setSecondaryDeviceIps(List<String> ipAddresses) {
  setDeviceDataValue('secondaryDeviceIps', ipAddresses.join(','))
}

String getGroupId() {
  return getDeviceDataValue('groupId')
}
void setGroupId(String groupId) {
  setDeviceDataValue('groupId', groupId)
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
  return getDeviceDataValue('groupCoordinatorId')
}
@CompileStatic
void setGroupCoordinatorId(String groupCoordinatorId) {
  logTrace("setGroupCoordinatorId ${groupCoordinatorId}")
  String previousGroupCoordinator = getGroupCoordinatorId()
  setDeviceDataValue('groupCoordinatorId', groupCoordinatorId)
  getDevice().sendEvent(name: 'groupCoordinatorId', value: groupCoordinatorId)
  Boolean isGroupCoordinator = getId() == groupCoordinatorId
  Boolean previouslyWasGroupCoordinator = getId() == previousGroupCoordinator
  setIsGroupCoordinator(isGroupCoordinator)

  if(isGroupCoordinator) {
    if(!subValid('sid1')) {
      clearCurrentPlayingStates()
      subscribeToAVTransport()
      subscribeToWsEvents()
      getPlaybackMetadataStatus()
      playerGetGroupsFull()
      logTrace('Just became group coordinator, subscribing to AVT.')
    }
  } else if(!isGroupCoordinator && previouslyWasGroupCoordinator) {
      logTrace("Just added to group!")
      unsubscribeFromAVTransport()
  } else {logTrace("Group coordinator status has not changed.")}
}

String getGroupCoordinatorName() {
  return this.device.currentValue('groupCoordinatorName')
}
void setGroupCoordinatorName(String groupCoordinatorName) {
  if(groupCoordinatorName != getGroupCoordinatorName()) {
    this.device.sendEvent(name: 'groupCoordinatorName', value: groupCoordinatorName)
  }
}

Boolean getIsGroupCoordinator() {
  return getDeviceDataValue('isGroupCoordinator') == 'true'
}
void setIsGroupCoordinator(Boolean isGroupCoordinator) {
  if(isGroupCoordinator != getIsGroupCoordinator()) {
    setDeviceDataValue('isGroupCoordinator', isGroupCoordinator.toString())
    this.device.sendEvent(name: 'isGroupCoordinator', value: isGroupCoordinator ? 'on' : 'off')
  }
}

Boolean isGroupedAndCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator()
}

Boolean isGroupedAndNotCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator() == false
}

List<String> getGroupPlayerIds() {
  return getDeviceDataValue('groupPlayerIds').tokenize(',')
}
void setGroupPlayerIds(List<String> groupPlayerIds) {
  setDeviceDataValue('groupPlayerIds', groupPlayerIds.join(','))
  setDeviceDataValue('groupIds', groupPlayerIds.join(','))
  this.device.sendEvent(name: 'isGrouped', value: groupPlayerIds.size() > 1 ? 'on' : 'off')
  this.device.sendEvent(name: 'groupMemberCount', value: groupPlayerIds.size())
  if(isGroupedAndCoordinator()) {
    logTrace('Updating group device with new group membership information')
    parentUpdateGroupDevices(getId(), groupPlayerIds)
  }
}

List<String> getGroupFollowerRincons() {
  return getGroupPlayerIds() - [getId()]
}

List<String> getGroupFollowerDNIs() {
  return getGroupFollowerRincons().collect{ it.tokenize('_')[1][0..-6] }
}

@CompileStatic
String getDNIFromRincon(String rincon) {
  return rincon.tokenize('_')[1][0..-6]
}

String getGroupMemberNames() {
  return this.device.currentValue('groupMemberNames')
}
void setGroupMemberNames(List<String> groupPlayerNames) {
  this.device.sendEvent(name: 'groupMemberNames' , value: groupPlayerNames.toString())
}

@CompileStatic
void setAlbumArtURI(String albumArtURI, Boolean isPlayingLocalTrack) {
  String uri = '<br>'
  if(albumArtURI.startsWith('/') && !isPlayingLocalTrack) {
    uri += "<img src=\"${getLocalUpnpUrl()}${albumArtURI}\" width=\"200\" height=\"200\" >"
  } else if(!isPlayingLocalTrack) {
    uri += "<img src=\"${albumArtURI}\" width=\"200\" height=\"200\" >"
  }
  sendDeviceEvent('albumArtURI', uri)
  sendGroupEvents()
}

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
}
@CompileStatic
void setCurrentFavorite(String uri) {
  sendDeviceEvent('currentFavorite', uri)
  sendGroupEvents()
}
String getCurrentFavorite() {
  return this.device.currentValue('currentFavorite', true)
}

@CompileStatic
void setStatusTransportStatus(String status) {
  sendDeviceEvent('status', status)
  sendDeviceEvent('transportStatus', status)
  sendGroupEvents()
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
Boolean getIsMuted() {
  return getMuteState() == 'muted'
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

@CompileStatic
void setPlayMode(String playMode){
  switch(playMode) {
    case 'NORMAL':
      sendDeviceEvent('currentRepeatOneMode', 'off')
      sendDeviceEvent('currentRepeatAllMode', 'off')
      sendDeviceEvent('currentShuffleMode', 'off')
      sendChildEvent(getRepeatOneControlChild(), 'switch', 'off')
      sendChildEvent(getRepeatAllControlChild(), 'switch', 'off')
      sendChildEvent(getShuffleControlChild(), 'switch', 'off')
    break
    case 'REPEAT_ALL':
      sendDeviceEvent('currentRepeatOneMode', 'off')
      sendDeviceEvent('currentRepeatAllMode', 'on')
      sendDeviceEvent('currentShuffleMode', 'off')
      sendChildEvent(getRepeatOneControlChild(), 'switch', 'off')
      sendChildEvent(getRepeatAllControlChild(), 'switch', 'on')
      sendChildEvent(getShuffleControlChild(), 'switch', 'off')
    break
    case 'REPEAT_ONE':
      sendDeviceEvent('currentRepeatOneMode', 'on')
      sendDeviceEvent('currentRepeatAllMode', 'off')
      sendDeviceEvent('currentShuffleMode', 'off')
      sendChildEvent(getRepeatOneControlChild(), 'switch', 'on')
      sendChildEvent(getRepeatAllControlChild(), 'switch', 'off')
      sendChildEvent(getShuffleControlChild(), 'switch', 'off')
    break
    case 'SHUFFLE_NOREPEAT':
      sendDeviceEvent('currentRepeatOneMode', 'off')
      sendDeviceEvent('currentRepeatAllMode', 'off')
      sendDeviceEvent('currentShuffleMode', 'on')
      sendChildEvent(getRepeatOneControlChild(), 'switch', 'off')
      sendChildEvent(getRepeatAllControlChild(), 'switch', 'off')
      sendChildEvent(getShuffleControlChild(), 'switch', 'on')
    break
    case 'SHUFFLE':
      sendDeviceEvent('currentRepeatOneMode', 'off')
      sendDeviceEvent('currentRepeatAllMode', 'on')
      sendDeviceEvent('currentShuffleMode', 'on')
      sendChildEvent(getRepeatOneControlChild(), 'switch', 'off')
      sendChildEvent(getRepeatAllControlChild(), 'switch', 'on')
      sendChildEvent(getShuffleControlChild(), 'switch', 'on')
    break
    case 'SHUFFLE_REPEAT_ONE':
      sendDeviceEvent('currentRepeatOneMode', 'on')
      sendDeviceEvent('currentRepeatAllMode', 'off')
      sendDeviceEvent('currentShuffleMode', 'on')
      sendChildEvent(getRepeatOneControlChild(), 'switch', 'on')
      sendChildEvent(getRepeatAllControlChild(), 'switch', 'off')
      sendChildEvent(getShuffleControlChild(), 'switch', 'on')
    break
  }
  sendGroupEvents()
}

String getCurrentRepeatOneMode() { return this.device.currentValue('currentRepeatOneMode') }
String getCurrentRepeatAllMode() { return this.device.currentValue('currentRepeatAllMode') }
String getCurrentShuffleMode() { return this.device.currentValue('currentShuffleMode') }

@CompileStatic
void setCrossfadeMode(String currentCrossfadeMode) {
  sendDeviceEvent('currentCrossfadeMode', currentCrossfadeMode)
  sendGroupEvents()
  sendChildEvent(getCrossfadeControlChild(), 'switch', currentCrossfadeMode)
}
String getCrossfadeMode() { return this.device.currentValue('currentCrossfadeMode') }

@CompileStatic
void setCurrentTrackDuration(String currentTrackDuration){
  sendDeviceEvent('currentTrackDuration', currentTrackDuration)
  sendGroupEvents()
}
String getCurrentTrackDuration() { return this.device.currentValue('currentTrackDuration') }

@CompileStatic
void setCurrentArtistName(String currentArtistName) {
  sendDeviceEvent('currentArtistName', currentArtistName ?: 'Not Available')
  sendGroupEvents()
}
String getCurrentArtistName() { return this.device.currentValue('currentArtistName') }

@CompileStatic
void setCurrentAlbumName(String currentAlbumName) {
  sendDeviceEvent('currentAlbumName', currentAlbumName ?: 'Not Available')
  sendGroupEvents()
}
String getCurrentAlbumName() { return this.device.currentValue('currentAlbumName') }

@CompileStatic
void setCurrentTrackName(String currentTrackName) {
  sendDeviceEvent('currentTrackName', currentTrackName ?: 'Not Available')
  sendGroupEvents()
}
String getCurrentTrackName() { return this.device.currentValue('currentTrackName') }

@CompileStatic
void setCurrentTrackNumber(Integer currentTrackNumber) {
  sendDeviceEvent('currentTrackNumber', currentTrackNumber ?: 0)
  sendGroupEvents()
}
Integer getCurrentTrackNumber() { return this.device.currentValue('currentTrackNumber') }

@CompileStatic
void setTrackDescription(String trackDescription) {
  String prevTrackDescription = getTrackDescription()
  sendDeviceEvent('trackDescription', trackDescription)
  sendGroupEvents()

  if(getIsGroupCoordinator() && prevTrackDescription != trackDescription) {getPlaybackMetadataStatus()}
}
String getTrackDescription() { return this.device.currentValue('trackDescription') }

@CompileStatic
void setTrackDataEvents(Map trackData) {
  sendDeviceEvent('trackData', JsonOutput.toJson(trackData))
  sendGroupEvents()
}
String getTrackDataEvents() {return this.device.currentValue('trackData')}

@CompileStatic
void setNextArtistName(String nextArtistName) {
  sendDeviceEvent('nextArtistName', nextArtistName ?: 'Not Available')
  sendGroupEvents()
}
String getNextArtistName() { return this.device.currentValue('nextArtistName') }

@CompileStatic
void setNextAlbumName(String nextAlbumName) {
  sendDeviceEvent('nextAlbumName', nextAlbumName ?: 'Not Available')
  sendGroupEvents()
}
String getNextAlbumName() { return this.device.currentValue('nextAlbumName') }

@CompileStatic
void setNextTrackName(String nextTrackName) {
  sendDeviceEvent('nextTrackName', nextTrackName ?: 'Not Available')
  sendGroupEvents()
}
String getNextTrackName() { return this.device.currentValue('nextTrackName') }

@CompileStatic
ConcurrentLinkedQueue<Map> getAudioClipQueue() {
  audioClipQueueInitialization()
  return audioClipQueue[getId()]
}

@CompileStatic
LinkedHashMap getAudioClipQueueTimers() {
  audioClipQueueInitialization()
  return audioClipQueueTimers[getId()]
}

@CompileStatic
void addTimerToAudioClipQueueTimers(Long clipDuration, String clipUri) {
  LinkedHashMap timers = getAudioClipQueueTimers()
  timers[clipUri] = Instant.now().getEpochSecond() + clipDuration
  // runIn(clipDuration + 10, 'dequeueAudioClip')
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

@CompileStatic
Boolean isWebsocketConnected() {
  return webSocketStatuses[getId()] == true
}
String getWebSocketStatus() {
  return getDeviceDataValue('websocketStatus')
}
void setWebSocketStatus(String status) {
  setDeviceDataValue('websocketStatus', status)
  if(status == 'open') {
    webSocketStatuses[getId()] = true
    runIn(15, 'subscribeToWsEvents')
  } else {
    webSocketStatuses[getId()] = false
  }
}

// @CompileStatic
// List<String> eventsToSkipForDisabledTrackData() {
//   return [
//     'currentTrackDuration',
//     'currentArtistName',
//     'currentAlbumName',
//     'currentTrackName',
//     'currentTrackNumber',
//     'nextArtistName',
//     'nextAlbumName',
//     'nextTrackName'
//   ]
// }

void sendGroupEvents() {runIn(1, 'sendEventsToGroupMembers', [overwrite: true])}

@CompileStatic
void sendEventsToGroupMembers() {
  if(isGroupedAndCoordinator()) {
    List<String> groupDNIs = getGroupFollowerDNIs()
    List<Map> eventsToSend = getCurrentPlayingStatesForGroup()
    groupDNIs.each{ String dni ->
      eventsToSend.each{ event ->
        parentSendEventToDNI(dni, (String)event.name, event.value)
      }
    }
  }
}

@CompileStatic
void sendEventsToNewGroupMembers(List<String> newRincons) {
  List<String> groupDNIs = newRincons.collect{getDNIFromRincon(it)}
  logTrace("GroupDNIs ${groupDNIs}")
  List<Map> eventsToSend = getCurrentPlayingStatesForGroup()
  groupDNIs.each{ String dni ->
    logTrace("Sending events to: ${dni}")
    eventsToSend.each{ event ->
      parentSendEventToDNI(dni, (String)event.name, event.value)
    }
  }
}

void parentSendEventToDNI(String dni, String name, Object value) {
  parent?.sendEvent(dni, [name:name, value:value])
}


ConcurrentHashMap<String, LinkedHashMap> getFavoritesMap() {
  return favoritesMap
}

void clearFavoritesMap() {
  favoritesMap = new ConcurrentHashMap<String, LinkedHashMap>()
}

@CompileStatic
List<Map> getCurrentPlayingStatesForGroup() {
  List currentStates = []
  currentStates.add([name: 'albumArtURI',           value: getAlbumArtURI()])
  currentStates.add([name: 'status',                value: getTransportStatus()])
  currentStates.add([name: 'transportStatus',       value: getTransportStatus()])
  currentStates.add([name: 'currentRepeatOneMode',  value: getCurrentRepeatOneMode()])
  currentStates.add([name: 'currentRepeatAllMode',  value: getCurrentRepeatAllMode()])
  currentStates.add([name: 'currentShuffleMode',    value: getCurrentShuffleMode()])
  currentStates.add([name: 'currentCrossfadeMode',  value: getCrossfadeMode()])
  currentStates.add([name: 'currentTrackDuration',  value: getCurrentTrackDuration()])
  currentStates.add([name: 'currentArtistName',     value: getCurrentArtistName()])
  currentStates.add([name: 'currentAlbumName',      value: getCurrentAlbumName()])
  currentStates.add([name: 'currentTrackName',      value: getCurrentTrackName()])
  currentStates.add([name: 'trackDescription',      value: getTrackDescription()])
  currentStates.add([name: 'trackData',             value: getTrackDataEvents()])
  currentStates.add([name: 'currentFavorite',       value: getCurrentFavorite()])
  currentStates.add([name: 'nextAlbumName',         value: getNextAlbumName()])
  currentStates.add([name: 'nextArtistName',        value: getNextArtistName()])
  currentStates.add([name: 'nextTrackName',         value: getNextTrackName()])
  currentStates.add([name: 'groupVolume',           value: getGroupVolumeState()])
  currentStates.add([name: 'groupMute',             value: getGroupMuteState()])
  return currentStates
}
// =============================================================================
// End Getters and Setters
// =============================================================================



// =============================================================================
// Websocket Connection and Initialization
// =============================================================================
void webSocketStatus(String message) {
  if(message == 'failure: null') { setWebSocketStatus('closed')}
  else if(message == 'failure: connect timed out') { setWebSocketStatus('connect timed out')}
  else if(message == 'status: open') { setWebSocketStatus('open')}
  else if(message == 'status: closing') { setWebSocketStatus('closing')}
  else {
    setWebSocketStatus('unknown')
    logWarn("Websocket status: ${message}")
  }
}

void wsConnect() {
  Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
  interfaces.webSocket.connect(getDeviceDataValue('websocketUrl'), headers: headers, ignoreSSLIssues: true)
  unschedule('renewWebsocketConnection')
  scheduleResubscriptionToEvents('renewWebsocketConnection')
}

void wsClose() {
  interfaces.webSocket.close()
}

void sendWsMessage(String message) {
  Boolean isConnected = isWebsocketConnected()
  if(!isConnected) { wsConnect() }
  interfaces.webSocket.sendMessage(message)
}

void initializeWebsocketConnection() { wsConnect() }
void renewWebsocketConnection() { initializeWebsocketConnection() }
// =============================================================================
// End Websocket Connection and Initialization
// =============================================================================



// =============================================================================
// Websocket Subscriptions and polling
// =============================================================================
void subscribeToWsEvents() {
  subscribeToPlaylists()
  subscribeToAudioClip()
  subscribeToGroups()
}

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
    'namespace':'favorites',
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
void playerLoadAudioClip(String uri = null, BigDecimal volume = null, Integer duration = 0) {
  logTrace('playerLoadAudioClip')
  // subscribeToAudioClip()
  if(getIsMuted()) {
    logTrace('Skipping loadAudioClip notification because player is muted.')
    return
  }
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
  if(getAudioClipQueueIsEmpty() && getChimeBeforeTTS()) {playerLoadAudioClipChime(volume)}
  if(duration > 0) { audioClip.duration = duration }
  audioClip.uri = uri
  enqueueAudioClip(audioClip)
  logTrace('Enqueued')
}


@CompileStatic
void playerLoadAudioClipChime(BigDecimal volume = null) {
  logTrace('playerLoadAudioClipChime')
  // subscribeToAudioClip()
  if(getIsMuted()) {
    logTrace('Skipping loadAudioClip notification because player is muted.')
    return
  }
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
  audioClip.duration = 8
  enqueueAudioClip(audioClip)
}

void enqueueAudioClip(Map clipMessage) {
  logTrace('enqueueAudioClip')
  getAudioClipQueue().add(clipMessage)
  if(atomicState.audioClipPlaying == false) {dequeueAudioClip()}
}

void dequeueAudioClip() {
  logTrace('dequeueAudioClip')
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  Map clipMessage = getAudioClipQueue().poll()
  if(!clipMessage) {
    return
    logTrace('No more audio clips to dequeue.')
  }
  atomicState.audioClipPlaying = true
  // if(clipMessage?.duration != null && clipMessage?.duration > 0) {
  //   addTimerToAudioClipQueueTimers(clipMessage.duration as Integer, clipMessage.uri)
  // }
  if(clipMessage.rightChannel) {
    sendWsMessage(clipMessage.leftChannel)
    rightChannel.playerLoadAudioClip(clipMessage.rightChannel)
  } else {
    sendWsMessage(clipMessage.leftChannel)
  }
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
// =============================================================================
// End Grouping and Ungrouping
// =============================================================================



// =============================================================================
// SOAP Commands
// =============================================================================
@CompileStatic
void setNightMode(Boolean desiredValue) {
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [EQType: 'NightMode', DesiredValue: desiredValue ? 1 : 0]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetEQ', controlValues)
  httpPostAsync(params)
}

@CompileStatic
void setDialogMode(Boolean desiredValue) {
  logTrace('setDialogMode')
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [EQType: 'DialogLevel', DesiredValue: desiredValue ? 1 : 0]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetEQ', controlValues)
  logDebug(params)
  httpPostAsync(params)
}

@CompileStatic
void componentSetBassLocal(DeviceWrapper device, BigDecimal level) {
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [DesiredBass: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetBass', controlValues)
  httpPostAsync(params)
}

void localControlCallback(AsyncResponse response, Map data) {
  if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
  }
  if(response?.status == 200 && response && response.hasError() == false) {
    logTrace("localControlCallback: ${response.getData()}")
  }
}

void httpPostAsync(Map params, String callback = 'localControlCallback') {
  asynchttpPost(callback, params)
}
// =============================================================================
// End SOAP Commands
// =============================================================================



// =============================================================================
// Local Control Component Methods
// =============================================================================
void componentPlayTextNoRestoreLocal(String text, BigDecimal volume = null, String voice = null) {
  logDebug("${device} play text ${text} (volume ${volume ?: 'not set'})")
  Map data = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  Map tts = textToSpeech(text, voice)
  String streamUrl = tts.uri
  if (volume) data.volume = (int)volume

  String playerId = getId()
  if(volume) {componentSetGroupLevelLocal(device, volume)}
  setAVTransportURIAndPlay(device, streamUrl)
}

void removeAllTracksFromQueue(String callbackMethod = 'localControlCallback') {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map params = getSoapActionParams(ip, AVTransport, 'RemoveAllTracksFromQueue')
  asynchttpPost(callbackMethod, params)
}

void setAVTransportURIAndPlay(String currentURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map controlValues = [CurrentURI: currentURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  Map data = [dni:device.getDeviceNetworkId()]
  asynchttpPost('setAVTransportURIAndPlayCallback', params, data)
}

void setAVTransportURIAndPlayCallback(AsyncResponse response, Map data = null) {
  if(!responseIsValid(response, 'setAVTransportURICallback')) { return }
  ChildDeviceWrapper child = app.getChildDevice(data.dni)
  child.play()
}

void setAVTransportURI(String currentURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map controlValues = [CurrentURI: currentURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  Map data = [dni:device.getDeviceNetworkId()]
  asynchttpPost('localControlCallback', params, data)
}

void addURIToQueue(String enqueuedURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map controlValues = [EnqueuedURI: enqueuedURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'AddURIToQueue', controlValues)
  // removeAllTracksFromQueue(device)
  asynchttpPost('localControlCallback', params)
}

void componentLoadStreamUrlLocal(String streamUrl, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
  logDebug("${device} play track ${streamUrl} (volume ${volume ?: 'not set'})")
  if(volume) {componentSetGroupLevelLocal(device, volume)}
  setAVTransportURIAndPlay(device, streamUrl)
}

void componentSetStreamUrlLocal(String streamUrl, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
  logDebug("${device} play track ${streamUrl} (volume ${volume ?: 'not set'})")
  if(volume) {componentSetGroupLevelLocal(device, volume)}
  setAVTransportURI(device, streamUrl)
}

void getDeviceStateAsync(String callbackMethod = 'localControlCallback', Map service, String action, Map data = null, Map controlValues = null) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, service, action, controlValues)
  asynchttpPost(callbackMethod, params, data)
}

void componentSetPlayerLevelLocal(BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetTrebleLocal(BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredTreble: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetTreble', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetBassLocal(BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredBass: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetBass', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetBalanceLocal(BigDecimal level) {
  if(!hasLeftAndRightChannelsSync(device)) {
    logWarn("Can not set balance on non-stereo pair.")
    return
  }
  String ip = device.getDataValue('localUpnpHost')
  level *= 5
  if(level < 0) {
    level = level < -100 ? -100 : level
    Map controlValues = [DesiredVolume: 100 + level, Channel: "RF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "LF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
  } else if(level > 0) {
    level = level > 100 ? 100 : level
    Map controlValues = [DesiredVolume: 100 - level, Channel: "LF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "RF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
  } else {
    Map controlValues = [DesiredVolume: 100, Channel: "LF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "RF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
  }
}

void componentSetLoudnessLocal(Boolean desiredLoudness) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredLoudness: desiredLoudness]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetLoudness', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentMuteGroupLocal(Boolean desiredMute) {
  DeviceWrapper coordinator = getGroupCoordinatorForPlayerDeviceLocal(device)
  String ip = coordinator.getDataValue('localUpnpHost')
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupMute', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupRelativeLevelLocal(Integer adjustment) {
  DeviceWrapper coordinator = getGroupCoordinatorForPlayerDeviceLocal(device)
  String ip = coordinator.getDataValue('localUpnpHost')
  Map controlValues = [Adjustment: adjustment]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetRelativeGroupVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupLevelLocal(BigDecimal level) {
  DeviceWrapper coordinator = getGroupCoordinatorForPlayerDeviceLocal(device)
  String ip = coordinator.getDataValue('localUpnpHost')
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}
// =============================================================================
// Local Control Component Methods
// =============================================================================



// =============================================================================
// Websocket Incoming Data Processing
// =============================================================================
@CompileStatic
void processWebsocketMessage(String message) {
  if(message == null || message == '') {return}
  ArrayList json = (ArrayList)slurper.parseText(message)
  if(json.size() < 2) {return}
  // logTrace(JsonOutput.prettyPrint(message))

  Map eventType = json[0]
  Map eventData = json[1]

  //Process subscriptions
  if(eventType?.type == 'none' && eventType?.response == 'subscribe') {
    logTrace("Subscription to ${eventType?.namespace} ${eventType?.success ? 'was sucessful' : 'failed'}")
  }

  //Process groups
  if(eventType?.type == 'groups' && eventType?.name == 'groups') {
    // logDebug("Groups: ${prettyJson(eventData)}")
    ArrayList<Map> groups = (ArrayList<Map>)eventData.groups
    if(groups != null && groups.size() > 0) {
      Map group = groups.find{ ((String)it?.playerIds).contains(getId()) }

      setGroupId(group.id.toString())
      setGroupName(group.name.toString())
      setGroupPlayerIds((ArrayList<String>)group.playerIds)
      setGroupCoordinatorId(group.coordinatorId.toString())

      ArrayList<Map> players = (ArrayList<Map>)eventData.players
      String coordinatorName = players.find{it?.id == group.coordinatorId}?.name
      setGroupCoordinatorName(coordinatorName.toString())

      List<String> groupMemberNames = (ArrayList<String>)(group.playerIds.collect{pid -> players.find{player-> player?.id == pid}?.name})
      setGroupMemberNames(groupMemberNames)

      if(hasSecondaries() == true && players != null && players.size() > 0) {
        Map p = (Map)players.find{it?.id == getId()}
        if(p != null && p.size() > 0) {
          Map zInfo = (Map)p?.zoneInfo
          if(zInfo != null && zInfo.size() > 0) {
            ArrayList<Map> members = (ArrayList<Map>)zInfo?.members
            if(members != null && members.size() > 0) {
              String rightChannelId = members.find{((ArrayList<String>)it?.channelMap).contains('RF') }?.id
              setRightChannelRincon(rightChannelId)
            }
          }
        }
      }
    }
  }

  //Process groupCoordinatorChanged
  if(eventType?.type == 'groupCoordinatorChanged' && eventType?.name == 'groupCoordinatorChanged') {
    // logWarn("if(eventType?.type == 'groupCoordinatorChanged' && eventType?.name == 'groupCoordinatorChanged')")
    // ArrayList<LinkedHashMap> groups = (ArrayList<LinkedHashMap>)eventData.groups
    // if(group?.groupStatus == 'GROUP_STATUS_UPDATED') {
    //   logDebug("Group name: ${group.name}")
    //   logDebug("Group coordinatorId: ${group.coordinatorId}")
    //   logDebug("Group playerIds: ${group.playerIds}")
    //   logDebug("Group Id: ${group.id}")
    // }
  }

  if(eventType?.type == 'versionChanged' && eventType?.name == 'favoritesVersionChange') {
    getFavorites()
  }

  if(eventType?.type == 'favoritesList' && eventType?.response == 'getFavorites' && eventType?.success == true) {
    ArrayList<Map> respData = (ArrayList<Map>)eventData?.items
    Map<String, Map> formatted = (Map<String, Map>)respData.collectEntries() { [(String)it.id, [name:it.name, imageUrl:it?.imageUrl]] }
    String html = '<!DOCTYPE html><html><body><ul>'

    formatted.each(){fav ->
      String albumArtURI = fav.value.imageUrl
      String s = "Favorite #${fav.key} ${fav.value.name}"
      if(albumArtURI == null) {
        html += "<li>${s}: No Image Art Available</li>"
      } else if(albumArtURI.startsWith('/')) {
        html += "<li>${s}: <br><img src=\"${getDeviceDataValue('localUpnpUrl')}${albumArtURI}\" width=\"200\" height=\"200\" ></li>"
      } else {
        html += "<li>${s}: <br><img src=\"${albumArtURI}\" width=\"200\" height=\"200\" ></li>"
      }
    }
    html += '</ul></body></html>'
    setChildFavs(html)

    clearFavoritesMap()
    respData.each{
      Map item = (Map)it
      Map resource = (Map)item?.resource
      if(resource != null) {
        Map id = (Map)resource?.id
        if(id != null) {
          String objectId = id?.objectId
          String serviceId = id?.serviceId
          String accountId = id?.accountId
          if(objectId != null) {
            List tok = objectId.tokenize(':')
            if(tok.size() >= 1) { objectId = tok[1] }
          }
          String universalMusicObjectId = "${objectId}${serviceId}${accountId}".toString()
          getFavoritesMap()[universalMusicObjectId] = [id:(String)item?.id, name:(String)item?.name, imageUrl:(String)item?.imageUrl, service:(String)((Map)item?.service)?.name]
        }
      } else if(item?.imageUrl != null) {
        String universalMusicObjectId = ("${item?.imageUrl}".toString()).split('&v=')[0]
        getFavoritesMap()[universalMusicObjectId] = [id:(String)item?.id, name:(String)item?.name, imageUrl:(String)item?.imageUrl, service:(String)((Map)item?.service)?.name]
      }
    }
  }


  if(eventType?.type == 'audioClipStatus' && eventType?.name == 'audioClipStatus') {
    logTrace(JsonOutput.prettyPrint(message))
    ArrayList<Map> audioClips = (ArrayList<Map>)eventData?.audioClips
    if(audioClips != null && audioClips.find{it?.status == 'DONE'}) {
      setAudioClipPlaying(false)
    }
    else if(audioClips.find{it?.status == 'ACTIVE'}) {setAudioClipPlaying(true)}
    else if(audioClips.find{it?.status == 'ERROR'}) {setAudioClipPlaying(false)}
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
    logTrace('Getting currently playing favorite...')
    updateFavsIn(2, eventData)
  }
}

void updateFavsIn(Integer time, Map data) {
  if(getCreateFavoritesChildDevice() == true) {
    if(favoritesMap == null || favoritesMap.size() < 1) {
      logTrace('Favorites map is null, requesting favorites...')
      getFavorites()
      runIn(time + 7, 'isFavoritePlaying', [overwrite: true, data: data ])
    } else {
      runIn(time, 'isFavoritePlaying', [overwrite: true, data: data ])
    }
  } else if(favoritesMap != null) {
    runIn(time, 'isFavoritePlaying', [overwrite: true, data: data ])
  }
}

void setChildFavs(String html) {
  if(getCreateFavoritesChildDevice()) {
    ChildDeviceWrapper favDev = getFavoritesChild()
    favDev.setFavorites(html)
  }
}

void setAudioClipPlaying(Boolean s) {
  atomicState.audioClipPlaying = s
  if(s == false) {dequeueAudioClip()}
}

@CompileStatic
void isFavoritePlaying(Map json) {
  LinkedHashMap container = (LinkedHashMap)json?.container
  LinkedHashMap id = (LinkedHashMap)container?.id
  String objectId = id?.objectId
  if(objectId != null && objectId != '') {
    List tok = objectId.tokenize(':')
    if(tok.size() >= 1) { objectId = tok[1] }
  }
  String serviceId = id?.serviceId
  String accountId = id?.accountId
  String imageUrl = container?.imageUrl

  String universalMusicObjectId = "${objectId}${serviceId}${accountId}".toString()
  String universalMusicObjectIdAlt = "${imageUrl}".toString().split('&v=')[0]
  Boolean isFav = favoritesMap.containsKey(universalMusicObjectId)
  Boolean isFavAlt = favoritesMap.containsKey(universalMusicObjectIdAlt)

  String k = isFav ? universalMusicObjectId : universalMusicObjectIdAlt
  String foundFavId = favoritesMap[k]?.id
  String foundFavImageUrl = favoritesMap[k]?.imageUrl
  String foundFavName = favoritesMap[k]?.name

  setCurrentFavorite(foundFavImageUrl, foundFavId, foundFavName, (isFav||isFavAlt))
}
// =============================================================================
// End Websocket Incoming Data Processing
// =============================================================================