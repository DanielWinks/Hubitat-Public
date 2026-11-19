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

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.hub.domain.Event
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.util.slurpersupport.GPathResult
import hubitat.scheduling.AsyncResponse
import java.io.InputStream
import java.time.Instant
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Field static final List<String> LOG_LEVELS = ['trace', 'debug', 'info', 'warn', 'error', 'off']

void logError(String message) {
  if (loggingEnabled('error')) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}
void logWarn(String message) {
  if (loggingEnabled('warn')) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}
void logInfo(String message) {
  if (loggingEnabled('info')) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}
void logDebug(String message) {
  if (loggingEnabled('debug')) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}
void logException(String message) {
  logError(message)
}
void logException(String context, Exception exception) {
  if(exception == null) {
    logError("${context}: unknown exception")
    return
  }
  List<String> stackFrames = exception.stackTrace?.take(20)?.collect { Object frame -> frame.toString() } ?: []
  String details = "${context}: ${exception.class.name}: ${exception.message ?: '(no message)'}"
  if(!stackFrames.isEmpty()) { details += " | stack=${stackFrames.join(' <- ')}" }
  logException(details)
}
void logTrace(String message) {
  if (loggingEnabled('trace')) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

void logPayloadAtTrace(String debugMessage, String traceMessage) {
  if(getConfiguredLogLevel() == 'trace') {
    logTrace(traceMessage)
  } else {
    logDebug(debugMessage)
  }
}

void logZgtXmlAtTrace(String dni, String xmlBody) {
  if(getConfiguredLogLevel() != 'trace') {
    logDebug("Received ZGT NOTIFY for ${dni}; XML body omitted; bodyLength=${xmlBody.length()}")
    return
  }
  Integer chunkSize = 900
  Integer chunkCount = (Integer)Math.ceil(xmlBody.length() / (double)chunkSize)
  String payloadHash = Integer.toHexString(xmlBody.hashCode())
  for(Integer chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
    Integer start = chunkIndex * chunkSize
    Integer end = Math.min(start + chunkSize, xmlBody.length())
    String chunk = xmlBody.substring(start, end)
    logTrace("UPnP ZGT NOTIFY XML; dni=${dni}; chunk=${chunkIndex + 1}/${chunkCount}; totalLength=${xmlBody.length()}; hash=${payloadHash}; data=${chunk}")
  }
}

void logWebSocketCommandPayload(Map command, String json) {
  String namespace = command?.get('namespace')?.toString() ?: 'unknown'
  String commandName = command?.get('command')?.toString() ?: 'unknown'
  logPayloadAtTrace("Sending Sonos WebSocket command ${namespace}.${commandName}; payload omitted", json)
}

// The shared utilities library is intentionally not included in this driver.
// Keep the small timestamp formatter needed by the legacy SID visibility path
// local so the standalone driver has the same behavior after publication.
String formatEpoch(Long epochSeconds) {
  Date timestamp = new Date(epochSeconds * 1000L)
  if(location?.timeZone) { return timestamp.format('yyyy-MMM-dd h:mm:ss a', location.timeZone) }
  return timestamp.format('yyyy-MMM-dd h:mm:ss a')
}

#include dwinks.SMAPILibrary

metadata {
  definition(
    name: 'Sonos Advanced Player',
    version: '0.18.8',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    singleThreaded: false,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvPlayer.groovy'
  ) {
  capability 'MusicPlayer' //attributes: level - NUMBER mute - ENUM ["unmuted", "muted"] status - STRING trackData - JSON_OBJECT trackDescription - STRING

  capability 'AudioNotification'
  capability "AudioVolume" //mute - ENUM ["unmuted", "muted"] volume - NUMBER, unit:%  //commands: volumeDown() volumeUp()
  capability "MediaTransport" //attributes:  transportStatus - ENUM - ["playing", "paused", "stopped"]
  capability 'SpeechSynthesis'
  capability 'Initialize'
  capability 'SwitchLevel' // Explicitly declare for WebCore compatibility - setLevel() command

  command 'setRepeatMode', [[ name: 'Repeat Mode', type: 'ENUM', constraints: [ 'off', 'repeat one', 'repeat all' ]]]
  command 'setCrossfade', [[ name: 'Crossfade Mode', type: 'ENUM', constraints: ['on', 'off']]]
  command 'setShuffle', [[ name: 'Shuffle Mode', type: 'ENUM', constraints: ['on', 'off']]]
  command 'shuffleOn'
  command 'shuffleOff'
  command 'ungroupPlayer'
  command 'addPlayerToGroup', [[name: 'Player', type: 'STRING', description: 'RINCON ID, player name, IP address, MAC address, or Device Network ID']]
  command 'setGroupMute', [[ name: 'state', type: 'ENUM', constraints: ['muted', 'unmuted']]]
  command 'setGroupVolume', [[name: 'Group Volume*', type: 'NUMBER', description: 'Volume level (0-100)'], [name: 'Fade Duration', type: 'NUMBER', description: 'Fade duration in seconds (optional)']]
  command 'setVolumeZero'
  command 'groupVolumeUp'
  command 'groupVolumeDown'
  command 'muteGroup'
  command 'unmuteGroup'

  command 'getFavorites'
  command 'loadFavoriteFull', [
    [ name: 'favoriteId', type: 'STRING'],
    [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off', 'unchanged' ]],
    [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
    [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on', 'unchanged']],
    [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
    [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
  ]
  command 'loadFavorite', [[ name: 'favoriteId', type: 'STRING']]

  command 'getPlaylists'
  command 'loadPlaylistFull', [
    [ name: 'playlistId', type: 'STRING'],
    [ name: 'repeatMode', type: 'ENUM', constraints: [ 'repeat all', 'repeat one', 'off', 'unchanged' ]],
    [ name: 'queueMode', type: 'ENUM', constraints: [ 'replace', 'append', 'insert', 'insert_next' ]],
    [ name: 'shuffleMode', type: 'ENUM', constraints: ['off', 'on', 'unchanged']],
    [ name: 'autoPlay', type: 'ENUM', constraints: [ 'true', 'false' ]],
    [ name: 'crossfadeMode', type: 'ENUM', constraints: ['on', 'off']]
  ]
  command 'loadPlaylist', [[ name: 'playlistId', type: 'STRING']]

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
  attribute 'trackPosition', 'number'
  attribute 'trackPositionUpdated', 'number'
  attribute 'currentArtistName', 'string'
  attribute 'albumArtURI', 'string'
  attribute 'albumArtSmall', 'string'
  attribute 'albumArtMedium', 'string'
  attribute 'albumArtLarge', 'string'
  attribute 'audioSource', 'string'
  attribute 'currentAlbumName', 'string'
  attribute 'currentTrackName', 'string'
  attribute 'currentFavorite', 'string'
  attribute 'currentPlaylist', 'string'
  attribute 'currentTrackNumber', 'number'
  attribute 'nextArtistName', 'string'
  attribute 'nextAlbumName', 'string'
  attribute 'nextTrackName', 'string'
  attribute 'nextTrackAlbumArtURI', 'string'
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
  command 'setNightMode', [[ name: 'Night Mode (Soundbar only)*', type: 'ENUM', description: 'Toggle Night Mode on soundbar speakers (Arc, Beam, Ray, Playbar, Playbase)', constraints: ['on', 'off']]]
  command 'setSpeechEnhancement', [[ name: 'Speech Enhancement (Soundbar only)*', type: 'ENUM', description: 'Toggle Speech Enhancement on soundbar speakers (Arc, Beam, Ray, Playbar, Playbase)', constraints: ['on', 'off']]]

  command 'selectLineIn', [[name: 'Source Player', type: 'STRING', description: 'RINCON ID, IP address, or player name of source device (optional, defaults to this player)']]
  command 'selectTV'

  command 'playHighPriorityTTS', [
    [name:'Text*', type:"STRING", description:"Text to play", constraints:["STRING"]],
    [name:'Volume Level', type:"NUMBER", description:"0 uses the current/default level; -1 explicitly requests zero; 1-100 sets the level", constraints:["NUMBER"]],
    [name: 'Voice name', type: "ENUM", constraints: getCachedTTSVoiceNames(), defaultValue: getCurrentTTSVoice()]
  ]

  command 'playHighPriorityTrack', [
    [name:'Track URI*', type:"STRING", description:"URI/URL of track to play", constraints:["STRING"]],
    [name:'Volume Level', type:"NUMBER", description:"0 uses the current/default level; -1 explicitly requests zero; 1-100 sets the level", constraints:["NUMBER"]]
  ]
  command 'stopAnnouncement'
  command 'stopHighPriorityAnnouncement'

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
      input 'enableTrackPositionUpdates', 'bool', title: 'Enable track position updates', required: false, defaultValue: false
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
      input 'disableAlbumArtEvents', 'bool', title: 'Disable album art URL events (albumArtSmall, albumArtMedium, albumArtLarge)', required: false, defaultValue: true
      input 'createCrossfadeChildDevice', 'bool', title: 'Create child device for crossfade control?', required: false, defaultValue: false
      input 'createShuffleChildDevice', 'bool', title: 'Create child device for shuffle control?', required: false, defaultValue: false
      input 'createRepeatOneChildDevice', 'bool', title: 'Create child device for "repeat one" control?', required: false, defaultValue: false
      input 'createRepeatAllChildDevice', 'bool', title: 'Create child device for "repeat all" control?', required: false, defaultValue: false
      input 'createMuteChildDevice', 'bool', title: 'Create child device for mute control?', required: false, defaultValue: false
      if(deviceHasBattery() == true) {
        input 'createBatteryStatusChildDevice', 'bool', title: 'Create child device for battery status? (portable speakers only)', required: false, defaultValue: false
      }
      input 'createFavoritesChildDevice', 'bool', title: 'Create child device for favorites?', required: false, defaultValue: false
      input 'createPlaylistChildDevice', 'bool', title: 'Create child device for playlists?', required: false, defaultValue: false
      input 'createQueueChildDevice', 'bool', title: 'Create child device for queue management?', required: false, defaultValue: false
      input 'createSleepTimerChildDevice', 'bool', title: 'Add sleep timer child device', required: false, defaultValue: false
      if(getRightChannelRincon() != null && getRightChannelRincon() != '') {
        input 'createRightChannelChildDevice', 'bool', title: 'Create child device right channel? (stereo pair only)', required: false, defaultValue: false
      }
      if(hasHTPlaybackCapability() == true) {
        input 'createNightModeChildDevice', 'bool', title: 'Create child device for Night Mode control', required: false, defaultValue: false
        input 'createSpeechEnhancementChildDevice', 'bool', title: 'Create child device for Speech Enhancement control', required: false, defaultValue: false
      }
      if(hasAudioClipCapability() == true) {
        input 'chimeBeforeStandardPriorityTTS', 'bool', title: 'Play chime before standard priority non-interrupting (audioClip) TTS messages', required: false, defaultValue: false
        input 'chimeBeforeHighPriorityTTS', 'bool', title: 'Play chime before high priority non-interrupting (audioClip) TTS messages', required: false, defaultValue: false
      }
      input 'enableAirPlayUnmuteVolumeFix', 'bool', title: 'Enable volume restore fix for AirPlay unmute (fixes unmute issues with AirPlay streams)', required: false, defaultValue: true
      input 'enableRawWebsocketLogging', 'bool', title: 'Enable raw WebSocket message logging (verbose, requires trace logging enabled)', required: false, defaultValue: false
    }
    section('Logging Settings') {
      input name: 'logLevel', type: 'enum', title: 'Logging level', options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'], defaultValue: 'info', submitOnChange: true
    }
  }
}

String getConfiguredLogLevel() {
  if(settings.logLevel != null) { return normalizeLogLevel(settings.logLevel.toString()) }
  if(settings.logEnable == false) { return 'off' }
  if(settings.traceLogEnable == true) { return 'trace' }
  if(settings.debugLogEnable == true) { return 'debug' }
  return 'info'
}

String normalizeLogLevel(String level) {
  String normalized = level?.toLowerCase()
  return LOG_LEVELS.contains(normalized) ? normalized : 'info'
}

Boolean loggingEnabled(String messageLevel) {
  Integer configuredIndex = LOG_LEVELS.indexOf(getConfiguredLogLevel())
  Integer messageIndex = LOG_LEVELS.indexOf(messageLevel)
  return configuredIndex >= 0 && messageIndex >= configuredIndex && messageIndex < LOG_LEVELS.indexOf('off')
}

// =============================================================================
// Preference Getters And Passthrough Renames For Clarity
// =============================================================================
Boolean getDisableTrackDataEvents() { return settings.disableTrackDataEvents != null ? settings.disableTrackDataEvents : true }
Boolean getEnableTrackPositionUpdates() { return settings.enableTrackPositionUpdates != null ? settings.enableTrackPositionUpdates : false }
Boolean getIncludeTrackDataMetaData() { return settings.includeTrackDataMetaData != null ? settings.includeTrackDataMetaData : false }
Integer getVolumeAdjustAmountLow() { return settings.volumeAdjustAmountLow != null ? settings.volumeAdjustAmountLow as Integer : 5 }
Integer getVolumeAdjustAmountMid() { return settings.volumeAdjustAmountMid != null ? settings.volumeAdjustAmountMid as Integer : 5 }
Integer getVolumeAdjustAmount() { return settings.volumeAdjustAmount != null ? settings.volumeAdjustAmount as Integer : 5 }
Integer getTTSBoostAmount() { return settings.ttsBoostAmount != null ? settings.ttsBoostAmount as Integer : 10 }
Boolean getDisableArtistAlbumTrackEvents() { return settings.disableArtistAlbumTrackEvents != null ? settings.disableArtistAlbumTrackEvents : false }
Boolean getDisableAlbumArtEvents() { return settings.disableAlbumArtEvents != null ? settings.disableAlbumArtEvents : true }
Boolean getCreateCrossfadeChildDevice() { return settings.createCrossfadeChildDevice != null ? settings.createCrossfadeChildDevice : false }
Boolean getCreateShuffleChildDevice() { return settings.createShuffleChildDevice != null ? settings.createShuffleChildDevice : false }
Boolean getCreateRepeatOneChildDevice() { return settings.createRepeatOneChildDevice != null ? settings.createRepeatOneChildDevice : false }
Boolean getCreateRepeatAllChildDevice() { return settings.createRepeatAllChildDevice != null ? settings.createRepeatAllChildDevice : false }
Boolean getCreateMuteChildDevice() { return settings.createMuteChildDevice != null ? settings.createMuteChildDevice : false }
Boolean getCreateBatteryStatusChildDevice() { return settings.createBatteryStatusChildDevice != null ? settings.createBatteryStatusChildDevice : false }
Boolean getCreateFavoritesChildDevice() { return settings.createFavoritesChildDevice != null ? settings.createFavoritesChildDevice : false }
Boolean getCreatePlaylistChildDevice() { return settings.createPlaylistChildDevice != null ? settings.createPlaylistChildDevice : false }
Boolean getCreateQueueChildDevice() { return settings.createQueueChildDevice != null ? settings.createQueueChildDevice : false }
Boolean getCreateSleepTimerChildDevice() { return settings.createSleepTimerChildDevice != null ? settings.createSleepTimerChildDevice : false }
Boolean getCreateRightChannelChildDevice() { return settings.createRightChannelChildDevice != null ? settings.createRightChannelChildDevice : false }
Boolean getCreateNightModeChildDevice() { return settings.createNightModeChildDevice != null ? settings.createNightModeChildDevice : false }
Boolean getCreateSpeechEnhancementChildDevice() { return settings.createSpeechEnhancementChildDevice != null ? settings.createSpeechEnhancementChildDevice : false }
Boolean getChimeBeforeStandardPriorityTTS() {
  if(settings.chimeBeforeStandardPriorityTTS != null) { return settings.chimeBeforeStandardPriorityTTS }
  return settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false
}
Boolean getChimeBeforeHighPriorityTTS() { return settings.chimeBeforeHighPriorityTTS != null ? settings.chimeBeforeHighPriorityTTS : false }
// Retain the legacy setting for existing direct-player commands. Group
// announcements use the parent app's single group policy instead.
Boolean getEnableAirPlayUnmuteVolumeFix() { return settings.enableAirPlayUnmuteVolumeFix != null ? settings.enableAirPlayUnmuteVolumeFix : true }
Boolean getEnableRawWebsocketLogging() { return settings.enableRawWebsocketLogging != null ? settings.enableRawWebsocketLogging : false }

@CompileStatic
Boolean processBatteryStatusChildDeviceMessages() {return getCreateBatteryStatusChildDevice()}
@CompileStatic
Boolean loadAudioClipOnRightChannel() {return getCreateRightChannelChildDevice()}


String getCurrentTTSVoice() {
  return cachedTTSDefaultVoice != null ? cachedTTSDefaultVoice : 'Matthew'
}

List<String> getCachedTTSVoiceNames() {
  if(cachedTTSVoiceNames != null && cachedTTSVoiceNames.size() > 0) { return cachedTTSVoiceNames }
  return ['Matthew']
}

void updateTTSVoiceCache(List<String> voiceNames, String defaultVoice) {
  cachedTTSVoiceNames = voiceNames
  cachedTTSDefaultVoice = defaultVoice
  logDebug("TTS voice cache updated: ${voiceNames?.size()} voices, default: ${defaultVoice}")
}

@CompileStatic
Boolean hasHTPlaybackCapability() {
  if(device != null) {
    String caps = getDeviceDataValue('capabilities')
    return caps != null && caps.contains('HT_PLAYBACK')
  } else {return false}
}

@CompileStatic
Boolean hasAudioClipCapability() {
  if(device != null) {
    String caps = getDeviceDataValue('capabilities')
    return caps != null && caps.contains('AUDIO_CLIP')
  } else {return false}
}

@CompileStatic
Boolean hasLineInCapability() {
  if(device != null) {
    String caps = getDeviceDataValue('capabilities')
    return caps != null && caps.contains('LINE_IN')
  } else { return false }
}
// =============================================================================
// End Preference Getters And Passthrough Renames For Clarity
// =============================================================================


// =============================================================================
// Fields
// =============================================================================
@Field static ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>> audioClipQueue = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> runtimeStateByDni = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>()
// Keep lock identities stable while callbacks may still hold a reference, even
// after a device is removed or its other static runtime state is pruned.
@Field static ConcurrentHashMap<String, Object> websocketCommandLedgerLocks = new ConcurrentHashMap<String, Object>()
// Expected topology is stored as a device data value on each affected player,
// so driver instances can coordinate without exposing it in Current States.
@Field static ConcurrentHashMap<String, String> pendingTopologyXmlByDni = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, Map> pendingTopologySnapshotByDni = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, String> pendingTopologyWebsocketByDni = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, String> pendingTopologyKindByDni = new ConcurrentHashMap<String, String>()
@Field static final Integer TOPOLOGY_CHANGE_SUPPRESSION_SECONDS = 15
@Field static ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>> audioClipQueueHighPriority = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>> audioClipQueueSaved = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static ConcurrentHashMap<String, LinkedHashMap> audioClipQueueTimers = new ConcurrentHashMap<String, LinkedHashMap>()
@Field static final String PLAYER_COMMAND_REQUEST_ATTRIBUTE = 'playerCommandRequest'
@Field static final String GROUP_FAVORITE_OPERATION_ATTRIBUTE = 'groupFavoriteOperation'
@Field static final String WS_OUTBOUND_QUEUE_STATE_KEY = 'websocketOutboundQueue'
@Field static final String WS_OUTBOUND_QUEUE_EXPIRY_AT_STATE_KEY = 'websocketOutboundQueueExpiryAt'
@Field static final String WS_COMMAND_LEDGER_STATE_KEY = 'websocketCommandLedger'
@Field static final String WS_CONNECTION_GENERATION_STATE_KEY = 'websocketConnectionGeneration'
@Field static final String WS_COMMAND_SEQUENCE_STATE_KEY = 'websocketCommandSequence'
@Field static final String WS_LAST_INBOUND_AT_STATE_KEY = 'websocketLastInboundAt'
@Field static final String WS_INBOUND_SEQUENCE_STATE_KEY = 'websocketInboundSequence'
@Field static final String WS_REPLAY_WAIT_SUBSCRIPTIONS_STATE_KEY = 'websocketReplayWaitingForSubscriptions'
@Field static final String WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY = 'websocketReplayWaitingForTopology'
@Field static final String WS_CONNECT_PENDING_STATE_KEY = 'websocketConnectPending'
@Field static final String APP_INITIALIZATION_REQUEST_ID_STATE_KEY = 'appMediatedInitializationRequestId'
@Field static final String APP_INITIALIZATION_STARTED_AT_STATE_KEY = 'appMediatedInitializationStartedAt'
@Field static final String APP_INITIALIZATION_RESUBSCRIPTIONS_STARTED_STATE_KEY = 'appMediatedInitializationResubscriptionsStarted'
@Field static final String APP_INITIALIZATION_UPNP_STARTED_STATE_KEY = 'appMediatedInitializationUpnpStarted'
@Field static final String APP_INITIALIZATION_CONNECTION_GRANTED_STATE_KEY = 'appMediatedInitializationConnectionGranted'
@Field static final String SKIP_PENDING_COMMAND_PURGE_ON_UPDATED_KEY = 'skipPendingCommandPurgeOnUpdated'
@Field static final Integer APP_INITIALIZATION_READY_CHECK_SECONDS = 1
@Field static final Integer APP_INITIALIZATION_CHILD_TIMEOUT_SECONDS = 180
@Field static final Integer WS_OUTBOUND_MAX_ATTEMPTS = 3
@Field static final Integer WS_PING_INTERVAL_SECONDS = 15
@Field static final Integer WS_COMMAND_LEDGER_EXPIRY_SECONDS = 60
@Field static final Integer WS_COMMAND_MAX_AGE_SECONDS = 60
@Field static final Integer WS_OUTBOUND_QUEUE_MAX_SIZE = 128
@Field static final Integer PAIRED_AUDIO_CLIP_SECONDARY_TIMEOUT_SECONDS = 30
@Field static final Integer WS_CONNECT_TIMEOUT_SECONDS = 20
@Field static final Integer WS_HEALTH_CHECK_SECONDS = 300
@Field static final String WS_DISCONNECTED_SINCE_KEY = 'wsDisconnectedSince'
@Field static final String WS_RETRY_SCHEDULED_KEY = 'wsRetryScheduled'
@Field static final String TRANSPORT_QUEUE_KEY = 'transportCommandQueue'
@Field static final String TRANSPORT_ACTIVE_KEY = 'activeTransportCommand'
@Field static final String TRANSPORT_STATUS_SNAPSHOT_KEY = 'transportStatusSnapshot'
@Field static final String TRACK_POSITION_SNAPSHOT_KEY = 'trackPositionSnapshot'
@Field static final Long TRACK_POSITION_SEEK_TOLERANCE_MILLIS = 2500L
@Field static final String ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY = 'announcementPlaybackSnapshot'
@Field static final Integer TRANSPORT_WATCHDOG_SECONDS = 4
@Field static final Integer TRANSPORT_MAX_ATTEMPTS = 3
@Field static final String WS_EXPECTED_EVENTS_KEY = 'websocketExpectedSubscriptionEvents'
@Field static final Integer WS_EXPECTED_EVENT_TIMEOUT_SECONDS = 20
@Field static final String UPNP_EXPECTED_EVENTS_KEY = 'upnpExpectedSubscriptionEvents'
@Field static final Integer UPNP_EXPECTED_EVENT_TIMEOUT_SECONDS = 10
@Field static final List<Long> WS_BACKOFF_STAGE_SECONDS = [600L, 600L, 600L, 7200L, 14400L, 21600L]
@Field static final List<Integer> WS_BACKOFF_INTERVALS_SECONDS = [30, 60, 120, 300, 900, 1800, 3600]
@Field static final Integer WS_STALE_TIMEOUT_SECONDS = 900
// UPnP subscriptions share one per-player socket, so all service requests
// pass through a single in-memory queue for this device.
@Field static ConcurrentHashMap<String, Map> upnpRequestQueues = new ConcurrentHashMap<String, Map>()
@Field static final Integer UPNP_REQUEST_TIMEOUT_SECONDS = 5
@Field static final Integer UPNP_REQUEST_MAX_ATTEMPTS = 3
@Field static final Integer UPNP_MAX_HEADER_BYTES = 16384
@Field static final Integer SUBSCRIBE_RETRY_MAX_ATTEMPTS = 3
@Field static ConcurrentHashMap<String, Integer> subscribeRetryAttempts = new ConcurrentHashMap<String, Integer>()
@Field static ConcurrentHashMap<String, ArrayList<DeviceWrapper>> groupsRegistry = new ConcurrentHashMap<String, ArrayList<DeviceWrapper>>()
@Field static ConcurrentHashMap<String, LinkedHashMap<String,LinkedHashMap>> statesRegistry = new ConcurrentHashMap<String, LinkedHashMap<String,LinkedHashMap>>()
@Field static ConcurrentHashMap<String, LinkedHashMap> favoritesMap = new ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, LinkedHashMap> playlistsMap = new ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, String> favPlaylistDelegate = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, Long> eventTimestamps = new ConcurrentHashMap<String, Long>()
@Field static ConcurrentHashMap<String, String> wsSubscriptionStatus = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, groovy.json.JsonSlurper> jsonSlurpers = new ConcurrentHashMap<String, groovy.json.JsonSlurper>()
@Field static java.util.Random rand = new java.util.Random()
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
@Field private final Integer STATIC_STATE_CLEANUP_INTERVAL_SECONDS = 900
@Field private final Integer STATIC_STATE_ROSTER_MAX_AGE_SECONDS = 1200
@Field private final Integer STATIC_STATE_ROSTER_VERSION = 1
@Field static volatile Long lastStaticStateCleanupEpoch = 0L
@Field static volatile Long lastStaticStateCleanupAttemptEpoch = 0L
@Field static Semaphore staticStateCleanupMutex = new Semaphore(1)

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

@Field static final List<Integer> FAVORITE_RETRY_INTERVALS = [5, 15, 30]
@Field static ConcurrentHashMap<String, Map> favoriteRetryState = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, Map> playlistRetryState = new ConcurrentHashMap<String, Map>()
@Field private final String FAVORITE_RETRY_CALLBACK = 'checkFavoritePlaybackAndRetry'
@Field private final String FAVORITE_RETRY_EVALUATION_CALLBACK = 'evaluateFavoritePlaybackAndRetry'
@Field static final Integer FAVORITE_INITIAL_CHECK_DELAY_SECONDS = 5
@Field static final Integer AMAZON_FAVORITE_INITIAL_CHECK_DELAY_SECONDS = 12

@Field static final List<Integer> PLAYLIST_RETRY_INTERVALS = [5, 15, 30]
@Field static final List<String> PLAYLIST_QUEUE_ACTIONS = ['REPLACE', 'APPEND', 'INSERT', 'INSERT_NEXT']
@Field static final List<String> NON_RETRYABLE_PLAYLIST_ERROR_CODES = [
  'ERROR_INVALID_PARAMETER',
  'ERROR_INVALID_OBJECT_ID',
  'ERROR_MISSING_PARAMETERS',
  'ERROR_UNSUPPORTED_COMMAND',
  'ERROR_UNSUPPORTED_NAMESPACE',
  'ERROR_INVALID_SYNTAX'
]
@Field private final String PLAYLIST_RETRY_CALLBACK = 'checkPlaylistPlaybackAndRetry'
@Field private final String PLAYLIST_RETRY_EVALUATION_CALLBACK = 'evaluatePlaylistPlaybackAndRetry'
@Field static final Integer PLAYLIST_INITIAL_CHECK_DELAY_SECONDS = 5
@Field static final Integer PLAYBACK_STATUS_RESPONSE_GRACE_SECONDS = 2
@Field static final Integer MAX_AMBIGUOUS_PLAYBACK_CONFIRMATION_PASSES = 2
@Field static final Integer MAX_PLAYLIST_PLAY_COMMAND_ATTEMPTS = 1

@Field static ConcurrentHashMap<String, Map> volumeFadeState = new ConcurrentHashMap<String, Map>()
// Sonos recommends avoiding a burst of individual player-volume commands when
// a group is active. Keep the individual-player semantics, but serialize a
// grouped multi-player update by the order Sonos reports the group members.
@Field static ConcurrentHashMap<String, Integer> pendingGroupedPlayerVolumes = new ConcurrentHashMap<String, Integer>()
@Field static final Integer GROUPED_PLAYER_VOLUME_STAGGER_MS = 250
@Field static final String PENDING_BATCH_EVENTS_KEY = 'pendingBatchEvents'
@Field static final String BATCH_EVENTS_SCHEDULED_KEY = 'batchEventsScheduled'
@Field static final String BATCH_EVENTS_DUE_AT_KEY = 'batchEventsDueAt'
@Field static final Integer BATCH_EVENTS_PER_DRAIN = 8
@Field static final Integer BATCH_EVENTS_INTERVAL_MS = 250
@Field static final String LOCAL_EVENT_DRAIN_SCHEDULED_KEY = 'localDeviceEventDrainScheduled'
@Field static final String LOCAL_EVENT_DRAIN_DUE_AT_KEY = 'localDeviceEventDrainDueAt'
@Field static final Integer LOCAL_EVENT_MAX_WAIT_MS = 500
@Field static final Integer LOCAL_EVENTS_PER_DRAIN = 8
@Field static final Integer LOCAL_EVENT_DRAIN_INTERVAL_MS = 200
@Field static ConcurrentHashMap<String, Object> localDeviceEventLocks = new ConcurrentHashMap<String, Object>()
@Field static final String PENDING_COMPONENT_CHILD_EVENTS_KEY = 'pendingComponentChildEvents'
@Field static final String COMPONENT_CHILD_DRAIN_SCHEDULED_KEY = 'componentChildDrainScheduled'
@Field static final String COMPONENT_CHILD_DRAIN_DUE_AT_KEY = 'componentChildDrainDueAt'
@Field static final String COMPONENT_CHILD_RETRY_ATTEMPTS_KEY = 'componentChildRetryAttempts'
@Field static final String LAST_COMPONENT_CHILD_EVENTS_KEY = 'lastComponentChildEvents'
@Field static final Integer COMPONENT_CHILD_MAX_WAIT_MS = 500
@Field static final Integer COMPONENT_CHILD_EVENTS_PER_DRAIN = 8
@Field static final Integer COMPONENT_CHILD_DRAIN_INTERVAL_MS = 200
@Field static final Integer COMPONENT_CHILD_RETRY_MAX_MS = 30000
@Field static final String GROUP_DEVICE_UPDATE_SCHEDULED_KEY = 'groupDeviceUpdateScheduled'
@Field static final Integer GROUP_DEVICE_UPDATE_MAX_WAIT_MS = 500
@Field static final Integer GROUP_DEVICE_UPDATE_RETRY_MS = 1000

@Field static final Integer AUDIO_CLIP_ACTIVE_TIMEOUT_SECONDS = 15
@Field static final Integer AUDIO_CLIP_DONE_GRACE_SECONDS = 15
@Field static ConcurrentHashMap<String, Map> groupVolumeFadeState = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, Long> lastVolumeFadeCallTime = new ConcurrentHashMap<String, Long>()
@Field static ConcurrentHashMap<String, Long> lastGroupVolumeFadeCallTime = new ConcurrentHashMap<String, Long>()
@Field static ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> pendingGroupDeviceUpdates = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>()
@Field static ConcurrentHashMap<String, Integer> groupDeviceUpdateRetryAttempts = new ConcurrentHashMap<String, Integer>()
@Field static ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> pendingLocalDeviceEvents = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>()
@Field static ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> lastLocalDeviceEvents = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>()
@Field static ConcurrentHashMap<String, String> lastMetadataContainerId = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, String> lastPlaybackState = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, String> lastWsEventLog = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, Integer> lastZgtXmlHash = new ConcurrentHashMap<String, Integer>()
@Field static volatile List<String> cachedTTSVoiceNames = null
@Field static volatile String cachedTTSDefaultVoice = null

// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Initialize and Configure
// =============================================================================
void uninstalled() {
  unschedule('flushPendingLocalDeviceEvents')
  state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
  state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
  unschedule('drainComponentChildEvents')
  unschedule('drainTransportCommandQueue')
  unschedule('watchTransportCommand')
  unschedule('retryUnconfirmedTransportCommand')
  unschedule('watchExpectedSubscriptionEvents')
  unschedule('watchExpectedUpnpSubscriptionEvents')
  runtimeState.remove(WS_EXPECTED_EVENTS_KEY)
  runtimeState.remove(UPNP_EXPECTED_EVENTS_KEY)
  clearStaticDriverStateForCurrentDevice()
}
void installed() { initialize() }
void updated() {
  runtimeState[SKIP_PENDING_COMMAND_PURGE_ON_UPDATED_KEY] = true
  try { initialize() }
  finally { runtimeState.remove(SKIP_PENDING_COMMAND_PURGE_ON_UPDATED_KEY) }
}

void initialize() {
  if(runtimeState.remove(SKIP_PENDING_COMMAND_PURGE_ON_UPDATED_KEY) != true) {
    clearPendingCommandsForInitialization()
    publishPlayerStateUpdate([kind: 'initializationRequested', reason: 'driver initialize',
      clearPendingCommands: true])
    return
  }
  requestAppMediatedInitialization('driver initialize')
}

/** Clear work owned by this player only at an explicit device initialization. */
void clearPendingCommandsForInitialization() {
  String playerId = getId()
  String dni = device.getDeviceNetworkId()
  if(playerId) {
    audioClipQueueInitialization()
    getDeviceAudioClipQueue().clear()
    getDeviceAudioClipQueueHighPriority().clear()
    getDeviceAudioClipQueueSaved().clear()
    getDeviceAudioClipQueueTimers().clear()
    pendingGroupedPlayerVolumes.remove(playerId)
  }
  clearFavoriteRetryState()
  clearPlaylistRetryState()
  cancelVolumeFade()

  // Older releases persisted command queues in state/atomicState. Remove them
  // before migrateLegacyRuntimeState() can copy them back into runtimeState.
  ['pendingParentAppTtsRequests', 'pendingParentCoordinatorCommands',
   'activeParentCoordinatorCommand', 'parentCommandSequence',
   TRANSPORT_QUEUE_KEY, TRANSPORT_ACTIVE_KEY,
   WS_OUTBOUND_QUEUE_STATE_KEY,
   'activeAudioClipCorrelationId', 'activeAudioClipSonosId', 'audioClipPlaying',
   'audioClipCommandSent', 'audioClipHadDisconnectAfterSend',
   'audioClipProgressByCorrelationId', 'audioClipQueueStartTime',
   'audioClipQueueTotalDuration', 'groupSynchronizedAudioClipHold',
   'groupAnnouncementStream', 'announcementPlaybackRestore',
   ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY, 'streamTransportIntentGuid',
   'activeAudioClipMessage', 'currentlyPlayingClip'].each { String key ->
    state.remove(key)
    atomicState.remove(key)
    runtimeState.remove(key)
  }
  synchronized(getWebsocketCommandLedgerLock()) {
    runtimeState.remove(WS_COMMAND_LEDGER_STATE_KEY)
  }
  unschedule('drainParentCoordinatorCommandQueue')
  unschedule('retryParentCoordinatorCommand')

  unschedule('drainTransportCommandQueue')
  unschedule('watchTransportCommand')
  unschedule('retryUnconfirmedTransportCommand')

  unschedule('expireWebsocketCommandLedgerEntry')
  unschedule('expireWebsocketOutboundQueueEntry')
  invalidateUpnpRequestsForDevice()

  device.deleteCurrentState('sonosPlayerStateUpdate')
  device.deleteCurrentState('sonosPlayerAppRequest')
  cancelAudioClipWatchdog()
  removeHiddenJsonRuntimeState('activeAudioClipMessage')
  removeHiddenJsonRuntimeState('currentlyPlayingClip')
  removeHiddenJsonRuntimeState('audioClipProgressByCorrelationId')
  logInfo("Cleared pending player commands for initialization (${dni})")
}

/** Remove one parent-owned announcement clip from this player's local queues. */
void clearPendingAudioClipCorrelation(String correlationId) {
  if(!correlationId || !getId()) { return }
  Map<String, Object> pendingTts = state.pendingParentAppTtsRequests instanceof Map
      ? (Map<String, Object>)state.pendingParentAppTtsRequests : [:]
  pendingTts.entrySet().removeAll { Map.Entry<String, Object> entry ->
    ((Map)entry.value)?.correlationId?.toString() == correlationId
  }
  if(pendingTts.isEmpty()) { state.remove('pendingParentAppTtsRequests') }
  else { state.pendingParentAppTtsRequests = pendingTts }
  audioClipQueueInitialization()
  getDeviceAudioClipQueue().removeAll { Map item -> item?.correlationId?.toString() == correlationId }
  getDeviceAudioClipQueueHighPriority().removeAll { Map item -> item?.correlationId?.toString() == correlationId }
  getDeviceAudioClipQueueSaved().removeAll { Map item -> item?.correlationId?.toString() == correlationId }
  List<Map> outbound = getWebsocketOutboundQueue()
  outbound.removeAll { Map item -> item?.correlationId?.toString() == correlationId }
  if(outbound.isEmpty()) { runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY) }
  else { runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = outbound }
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    ledger.removeAll { Map item -> item?.correlationId?.toString() == correlationId }
    if(ledger.isEmpty()) { runtimeState.remove(WS_COMMAND_LEDGER_STATE_KEY) }
    else { runtimeState[WS_COMMAND_LEDGER_STATE_KEY] = ledger }
  }
  Map active = (Map)getAtomicStateValue('activeAudioClipMessage')
  if(active?.correlationId?.toString() == correlationId) {
    cancelAudioClipWatchdog()
    runtimeState.remove('activeAudioClipCorrelationId')
    runtimeState.remove('activeAudioClipSonosId')
    runtimeState.remove('audioClipHadDisconnectAfterSend')
    removeHiddenJsonRuntimeState('activeAudioClipMessage')
    removeHiddenJsonRuntimeState('currentlyPlayingClip')
  }
  Map announcement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  if(announcement?.correlationId?.toString() == correlationId) {
    runtimeState.remove('groupAnnouncementStream')
    runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
  }
  Map restore = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(restore?.correlationId?.toString() == correlationId) {
    runtimeState.remove('announcementPlaybackRestore')
  }
}

/** Perform lifecycle setup only after the parent app grants an initialization turn. */
void prepareAppMediatedInitialization() {
  invalidateUpnpRequestsForDevice()
  Map queueEntry = getUpnpRequestQueue()
  if(queueEntry != null) { synchronized(queueEntry.lock) { queueEntry.retired = false } }
  migrateLegacyRuntimeState()
  resetPendingStatePublicationForInitialization()
  cancelAudioClipWatchdog()
  resetSubscriptionMutexesForDevice()
  maybeCleanupStaticDriverState()
  configure()
  scheduleWebsocketHealthCheck()
}

/** Drop stale publication work at the initialization boundary. A fresh Sonos
 * topology/playback snapshot repopulates these queues after subscriptions start. */
void resetPendingStatePublicationForInitialization() {
  String dni = device.getDeviceNetworkId()
  unschedule('drainBatchEvents')
  unschedule('flushPendingLocalDeviceEvents')
  unschedule('drainComponentChildEvents')
  unschedule('flushPendingGroupDeviceUpdates')
  state.remove(PENDING_BATCH_EVENTS_KEY)
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  state.remove(BATCH_EVENTS_DUE_AT_KEY)
  state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
  state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
  state.remove(PENDING_COMPONENT_CHILD_EVENTS_KEY)
  state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
  state.remove(COMPONENT_CHILD_DRAIN_DUE_AT_KEY)
  state.remove(COMPONENT_CHILD_RETRY_ATTEMPTS_KEY)
  state.remove(LAST_COMPONENT_CHILD_EVENTS_KEY)
  state.remove(GROUP_DEVICE_UPDATE_SCHEDULED_KEY)
  pendingLocalDeviceEvents.remove(dni)
  lastLocalDeviceEvents.remove(dni)
  pendingGroupDeviceUpdates.remove(dni)
  groupDeviceUpdateRetryAttempts.remove(dni)
  logDebug("Cleared stale state-publication queues and drain jobs for ${dni} before fresh initialization snapshot")
}

/** Queue a full subscription recovery. Both lifecycle entry points schedule the
 * same method with overwrite enabled, coalescing close-together requests.
 */
void requestFullSubscriptionRenewal(Integer delaySeconds, String reason) {
  requestAppMediatedInitialization(reason ?: 'full subscription renewal')
}

void requestAppMediatedInitialization(String reason) {
  publishPlayerStateUpdate([kind: 'initializationRequested', reason: reason ?: 'device request'])
}

/** Called only by the parent app after it grants this player the initialization turn. */
void beginAppMediatedInitialization(String requestId, Boolean subscribeZgt = false, String leaderGeneration = null) {
  publishPlayerStateUpdate([kind: 'initializationStarted', requestId: requestId])
  logDebug("Starting app-mediated initialization; request=${requestId}")
  if(!requestId) {
    publishPlayerStateUpdate([kind: 'initializationFailed', requestId: requestId, detail: 'missing parent request ID'])
    return
  }
  if(device.getDataValue('appInitializationRequestId')?.toString() != requestId) {
    logDebug("Ignoring stale app-mediated initialization request=${requestId}")
    return
  }
  runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY] = requestId
  runtimeState['appZgtSubscribe'] = subscribeZgt == true
  runtimeState['appZgtLeaderGeneration'] = leaderGeneration ?: ''
  device.updateDataValue('appZgtSubscribe', (subscribeZgt == true).toString())
  device.updateDataValue('appZgtLeaderGeneration', leaderGeneration ?: '')
  runtimeState[APP_INITIALIZATION_STARTED_AT_STATE_KEY] = now()
  runtimeState[APP_INITIALIZATION_RESUBSCRIPTIONS_STARTED_STATE_KEY] = false
  runtimeState[APP_INITIALIZATION_UPNP_STARTED_STATE_KEY] = false
  runtimeState[APP_INITIALIZATION_CONNECTION_GRANTED_STATE_KEY] = true
  runtimeState.remove('appMediatedInitializationFailure')
  try {
    prepareAppMediatedInitialization()
    performFullSubscriptionRenewal()
  } catch(Exception e) {
    logWarn("App-mediated initialization setup failed before subscription recovery: ${e.message}")
    finishAppMediatedInitialization(false, "driver setup failed: ${e.message}")
  }
}

void cancelAppMediatedInitialization(String requestId) {
  if(runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY]?.toString() != requestId) { return }
  runtimeState.remove(APP_INITIALIZATION_REQUEST_ID_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_STARTED_AT_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_RESUBSCRIPTIONS_STARTED_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_UPNP_STARTED_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_CONNECTION_GRANTED_STATE_KEY)
  runtimeState.remove('appMediatedInitializationFailure')
  unschedule('initializeWebsocketConnection')
  unschedule('websocketConnectWatchdog')
  unschedule('retryWebSocketConnection')
  unschedule('subscribeToEvents')
  unschedule('resubscribeAfterRenewal')
  unschedule('checkAppMediatedInitializationReady')
}
void configure() {
  invalidateUpnpRequestsForDevice()
  migrateLegacyRuntimeState()
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  schedulePendingBatchEvents()
  scheduleLocalDeviceEventDrain(LOCAL_EVENT_DRAIN_INTERVAL_MS)
  resetComponentChildEventRetries()
  state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
  scheduleComponentChildEventDrain(COMPONENT_CHILD_DRAIN_INTERVAL_MS)
  state.remove(GROUP_DEVICE_UPDATE_SCHEDULED_KEY)
  scheduleGroupDeviceUpdateDrain(GROUP_DEVICE_UPDATE_MAX_WAIT_MS)
  cancelAudioClipWatchdog()
  runtimeState.wsRetryCount = 0
  migrationCleanup()
  synchronizeTrackPositionPreference()
  // Remove transient parent-app bridge attributes from existing devices.
  device.deleteCurrentState('sonosPlayerStateUpdate')
  device.deleteCurrentState('sonosPlayerAppRequest')
  clearGroupFavoriteOperationCurrentState()
  clearPlayerCommandRequestCurrentState()
  // Remove the retired audio clip progress state from devices upgraded from
  // versions that exposed it as a current state.
  clearAudioClipPlaybackProgressCurrentState()
  // These diagnostics are stored in State Variables and are not exposed as
  // device Current States. Remove values created by earlier development builds.
  ['playbackState', 'playbackStateObservedAt', 'audioSourceSignalStatus', 'audioSourceSignalObservedAt', 'lastError'].each { String name ->
    device.deleteCurrentState(name)
  }
  // Apply child device preferences synchronously (no-ops when devices already exist)
  createRemoveCrossfadeChildDevice(getCreateCrossfadeChildDevice())
  createRemoveShuffleChildDevice(getCreateShuffleChildDevice())
  createRemoveRepeatOneChildDevice(getCreateRepeatOneChildDevice())
  createRemoveRepeatAllChildDevice(getCreateRepeatAllChildDevice())
  createRemoveMuteChildDevice(getCreateMuteChildDevice())
  createRemoveBatteryStatusChildDevice(getCreateBatteryStatusChildDevice())
  createRemoveFavoritesChildDevice(getCreateFavoritesChildDevice())
  createRemovePlaylistChildDevice(getCreatePlaylistChildDevice())
  createRemoveQueueChildDevice(getCreateQueueChildDevice())
  createRemoveSleepTimerChildDevice(getCreateSleepTimerChildDevice())
  createRemoveRightChannelChildDevice(getCreateRightChannelChildDevice())
  createRemoveNightModeChildDevice(getCreateNightModeChildDevice())
  createRemoveSpeechEnhancementChildDevice(getCreateSpeechEnhancementChildDevice())
  // Clear event attributes per preference
  if(getDisableTrackDataEvents()) { clearTrackDataEvent() }
  if(getDisableArtistAlbumTrackEvents()) { clearCurrentNextArtistAlbumTrackData() }
  if(getDisableAlbumArtEvents()) { clearAlbumArtEvents() }
  // Re-initialize in-memory data structures (static fields cleared on JVM restart)
  // Guard: these methods use getId() as a ConcurrentHashMap key, which throws NPE if null.
  // On fresh install, device data isn't set yet — secondaryConfigurationPhase4() will handle this.
  if(getId()) {
    audioClipQueueInitialization()
    groupsRegistryInitialization()
    favoritesMapInitialization()
    if(getCreateSleepTimerChildDevice()) { getSleepTimer() }
  }
}

void synchronizeTrackPositionPreference() {
  Boolean wasEnabled = device.getDataValue('enableTrackPositionUpdates')?.toString()?.toLowerCase() == 'true'
  Boolean isEnabled = getEnableTrackPositionUpdates()
  device.updateDataValue('enableTrackPositionUpdates', isEnabled.toString())
  if(wasEnabled && !isEnabled) {
    queueLocalDeviceEvent('trackPosition', 0L)
    queueLocalDeviceEvent('trackPositionUpdated', 0L)
  }
}

ConcurrentHashMap<String, Object> getRuntimeState() {
  String dni = device?.getDeviceNetworkId()
  if(!dni) { return new ConcurrentHashMap<String, Object>() }
  ConcurrentHashMap<String, Object> runtime = runtimeStateByDni.get(dni)
  if(runtime != null) { return runtime }
  ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<String, Object>()
  ConcurrentHashMap<String, Object> existing = runtimeStateByDni.putIfAbsent(dni, created)
  return existing ?: created
}

Object getWebsocketCommandLedgerLock() {
  String dni = getDeviceDNI()
  if(!dni) { return this }
  Object lock = websocketCommandLedgerLocks.get(dni)
  if(lock != null) { return lock }
  Object created = new Object()
  Object existing = websocketCommandLedgerLocks.putIfAbsent(dni, created)
  return existing ?: created
}

void migrateLegacyRuntimeState() {
  Map<String, Object> legacy = [:]
  try {
    legacy.putAll(state as Map<String, Object>)
    legacy.putAll(atomicState as Map<String, Object>)
  } catch(Exception e) {
    logWarn("Could not read legacy device state for migration: ${e.message}")
    return
  }
  ConcurrentHashMap<String, Object> runtime = getRuntimeState()
  legacy.each { String key, Object value ->
    if(value != null) {
      Object decodedValue = isHiddenJsonStateKey(key) ? fetchHiddenJsonStateValue(value) : value
      if(decodedValue != null) { runtime.putIfAbsent(key, decodedValue) }
    }
  }
  Object pendingBatchEvents = state[PENDING_BATCH_EVENTS_KEY]
  Object batchEventsScheduled = state[BATCH_EVENTS_SCHEDULED_KEY]
  Object pendingComponentChildEvents = state[PENDING_COMPONENT_CHILD_EVENTS_KEY]
  Object componentChildDrainScheduled = state[COMPONENT_CHILD_DRAIN_SCHEDULED_KEY]
  Object lastComponentChildEvents = state[LAST_COMPONENT_CHILD_EVENTS_KEY]
  state.clear()
  atomicState.clear()
  if(pendingBatchEvents != null) { state[PENDING_BATCH_EVENTS_KEY] = pendingBatchEvents }
  if(batchEventsScheduled != null) { state[BATCH_EVENTS_SCHEDULED_KEY] = batchEventsScheduled }
  if(pendingComponentChildEvents != null) { state[PENDING_COMPONENT_CHILD_EVENTS_KEY] = pendingComponentChildEvents }
  if(componentChildDrainScheduled != null) { state[COMPONENT_CHILD_DRAIN_SCHEDULED_KEY] = componentChildDrainScheduled }
  if(lastComponentChildEvents != null) { state[LAST_COMPONENT_CHILD_EVENTS_KEY] = lastComponentChildEvents }
  ['currentlyPlayingClip', 'activeAudioClipMessage', 'audioClipProgressByCorrelationId'].each { String key ->
    Object value = runtime.get(key)
    if(value != null) { storeHiddenJsonState(key, summarizeHiddenJsonState(key, value), value) }
  }
}

Boolean isHiddenJsonStateKey(String name) {
  return name in ['currentlyPlayingClip', 'activeAudioClipMessage', 'audioClipProgressByCorrelationId']
}

String summarizeHiddenJsonState(String name, Object value) {
  if(name == 'audioClipProgressByCorrelationId') {
    Integer count = value instanceof Map ? ((Map)value).size() : 0
    return "${count} clip progress entr${count == 1 ? 'y' : 'ies'}"
  }
  Map clip = value instanceof Map ? (Map)value : [:]
  String origin = clip.origin?.toString() ?: 'audio clip'
  String correlationId = clip.correlationId?.toString() ?: 'pending'
  return "${origin} (${correlationId})"
}

String escapeHiddenStateHtml(String value) {
  return (value ?: '').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&#39;')
}

void storeHiddenJsonState(String name, String visibleText, Object value) {
  if(value == null) {
    state.remove(name)
    return
  }
  String encoded = JsonOutput.toJson(value).getBytes('UTF-8').encodeBase64().toString()
  state[name] = "<root><span class=\"visible-data\">${escapeHiddenStateHtml(visibleText)}</span><span class=\"hidden-data\" style=\"display:none\">${encoded}</span></root>"
}

Object fetchHiddenJsonStateValue(Object storedValue) {
  if(!(storedValue instanceof CharSequence) || !storedValue.toString().contains('class="hidden-data"')) { return storedValue }
  try {
    GPathResult xml = new XmlSlurper().parseText(storedValue.toString())
    String encoded = xml.span.find { GPathResult span -> span.@class.toString() == 'hidden-data' }?.text()
    if(!encoded) { return null }
    return new JsonSlurper().parseText(new String(encoded.decodeBase64(), 'UTF-8'))
  } catch(Exception e) {
    logWarn("Could not decode hidden JSON state: ${e.message}")
    return null
  }
}

void setHiddenJsonRuntimeState(String name, Object value) {
  if(value == null) {
    runtimeState.remove(name)
    state.remove(name)
    return
  }
  runtimeState[name] = value
  storeHiddenJsonState(name, summarizeHiddenJsonState(name, value), value)
}

void removeHiddenJsonRuntimeState(String name) {
  runtimeState.remove(name)
  state.remove(name)
}

void registerRinconId() {
  unschedule('registerRinconId')
  unschedule('checkSubscriptions')
}

/** Compatibility callback: any persisted legacy schedule is redirected through the app queue. */
void fullRenewSubscriptions() {
  requestAppMediatedInitialization('legacy scheduled full subscription renewal')
}

void performFullSubscriptionRenewal() {
  releaseFavPlaylistDelegate()
  clearWsSubscriptionStatus()
  unsubscribeFromAVTransport()
  unsubscribeFromMrRcEvents()
  unsubscribeFromZgtEvents()
  unsubscribeFromMrGrcEvents()

  // Check if essential device data values exist before attempting subscriptions
  // On fresh install, these won't be set yet - they're configured by the parent app
  String localUpnpHost = device.getDataValue('localUpnpHost')
  String websocketUrl = device.getDataValue('websocketUrl')

  if(!localUpnpHost || !websocketUrl) {
    logInfo('Device data not yet configured (fresh install). Subscriptions will be established after configuration by parent app.')
    finishAppMediatedInitialization(false, 'local Sonos or WebSocket connection data is missing')
    return
  }

  // Driver updates and hub restarts can invalidate the actual WebSocket while
  // leaving the persisted status as "open". Always retire the old transport and
  // clear its subscription markers before reconnecting, regardless of that status.
  logDebug('Closing existing WebSocket before full subscription renewal')
  try { wsClose() }
  catch(Exception e) { logDebug("Could not close existing WebSocket during renewal: ${e.message}") }
  setWebSocketStatus('closed')

  runIn(2, 'initializeWebsocketConnection', [overwrite: true])
  runIn(7, 'subscribeToEvents', [overwrite: true])
  // Re-establish WebSocket and coordinator-specific subscriptions directly.
  // The normal event-driven chain (setGroupCoordinatorId/setGroupId) won't trigger
  // re-subscription because deduplication guards skip processing when the coordinator
  // and group data haven't changed — which is the common case after renewal.
  runIn(9, 'resubscribeAfterRenewal', [overwrite: true])
}

void scheduleWebsocketHealthCheck() {
  runIn(WS_HEALTH_CHECK_SECONDS, 'websocketHealthCheck', [overwrite: true])
}

void websocketHealthCheck() {
  try {
    if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true) {
      logWarn('WebSocket connection attempt exceeded timeout; retrying.')
      runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
      setWebSocketStatus('closed')
      scheduleWebSocketReconnect()
      return
    }

    if(!isWebsocketConnected()) {
      logInfo('WebSocket health check found the connection closed; reconnecting.')
      scheduleWebSocketReconnect()
      return
    }

    Long nowEpoch = (now() / 1000L) as Long
    Long openedEpoch = (runtimeState.websocketOpenedAt ?: nowEpoch) as Long
    Long lastEventEpoch = getLastWebsocketEventEpoch()
    Long lastActivityEpoch = lastEventEpoch > 0L ? lastEventEpoch : openedEpoch
    if((nowEpoch - lastActivityEpoch) > WS_STALE_TIMEOUT_SECONDS) {
      logWarn("WebSocket health check found no inbound activity for ${nowEpoch - lastActivityEpoch}s; reconnecting.")
      reconnectWebsocket('stale inbound activity')
      return
    }

    // Generate a response that lets the next health check verify the socket.
    if(getGroupId()) { getPlaybackStatus() }
  } finally {
    scheduleWebsocketHealthCheck()
  }
}

void reconnectWebsocket(String reason) {
  logInfo("Reconnecting WebSocket: ${reason}")
  runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
  try { wsClose() }
  catch(Exception e) { logDebug("Could not close WebSocket during reconnect: ${e.message}") }
  setWebSocketStatus('closed')
  runIn(1, 'initializeWebsocketConnection', [overwrite: true])
}

/**
 * Re-establishes all WebSocket and coordinator-specific subscriptions after a
 * fullRenewSubscriptions() cycle. Called via runIn to allow WebSocket and base
 * UPnP subscriptions to be established first.
 */
void resubscribeAfterRenewal() {
  // Base WebSocket namespace subscriptions (groups, favorites, playlists, audioClip)
  subscribeToWsEvents()

  // Coordinator-specific subscriptions
  if(getIsGroupCoordinator()) {
    subscribeToAVTransport()
    getPlaybackMetadataStatus()
    // Playback and playbackMetadata WS subscriptions are group-scoped
    String groupId = getGroupId()
    if(groupId) {
      subscribeToPlayback(groupId)
      subscribeToPlaybackMetadata(groupId)
    }
  }
  if(runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY] != null) {
    runtimeState[APP_INITIALIZATION_RESUBSCRIPTIONS_STARTED_STATE_KEY] = true
    ChildDeviceWrapper rightChannel = getRightChannelChild()
    if(rightChannel != null && getRightChannelRincon()) {
      watchRightChannelWebsocket(rightChannel)
      try { rightChannel.beginParentMediatedConnection(runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY].toString()) }
      catch(Exception e) { logWarn("Could not start best-effort right-channel initialization for ${rightChannel.getDeviceNetworkId()}: ${e.message}") }
    }
    runIn(APP_INITIALIZATION_READY_CHECK_SECONDS, 'checkAppMediatedInitializationReady', [overwrite: true])
  }
}

void checkAppMediatedInitializationReady() {
  String requestId = runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY]?.toString()
  if(!requestId) { return }
  String failure = runtimeState.appMediatedInitializationFailure?.toString()
  if(failure) {
    finishAppMediatedInitialization(false, failure)
    return
  }
  Long startedAt = (runtimeState[APP_INITIALIZATION_STARTED_AT_STATE_KEY] ?: now()) as Long
  if(now() - startedAt >= APP_INITIALIZATION_CHILD_TIMEOUT_SECONDS * 1000L) {
    finishAppMediatedInitialization(false, 'WebSocket or subscription setup did not settle within 180 seconds')
    return
  }
  if(runtimeState[APP_INITIALIZATION_UPNP_STARTED_STATE_KEY] != true ||
      runtimeState[APP_INITIALIZATION_RESUBSCRIPTIONS_STARTED_STATE_KEY] != true || !isWebsocketConnected()) {
    runIn(APP_INITIALIZATION_READY_CHECK_SECONDS, 'checkAppMediatedInitializationReady', [overwrite: true])
    return
  }
  Boolean pendingWebsocketSubscription
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    pendingWebsocketSubscription = ledger.any { Map entry -> entry.wireCommand == 'subscribe' }
  }
  List<Map> outbound = getWebsocketOutboundQueue()
  pendingWebsocketSubscription = pendingWebsocketSubscription ||
      outbound.any { Map entry -> entry.wireCommand == 'subscribe' }
  Map upnpQueue = getUpnpRequestQueue()
  Boolean pendingUpnpRequest = false
  if(upnpQueue != null) {
    synchronized(upnpQueue.lock) {
      pendingUpnpRequest = upnpQueue.active != null || !(upnpQueue.queue as List).isEmpty()
    }
  }
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  if(pendingWebsocketSubscription || pendingUpnpRequest) {
    runIn(APP_INITIALIZATION_READY_CHECK_SECONDS, 'checkAppMediatedInitializationReady', [overwrite: true])
    return
  }
  if(rightChannel != null && getRightChannelRincon() && rightChannel.getDataValue('websocketStatus') != 'open') {
    logWarn("Right-channel WebSocket is not ready after left-channel subscriptions settled; continuing initialization best-effort (dni=${rightChannel.getDeviceNetworkId()}, status=${rightChannel.getDataValue('websocketStatus') ?: 'unknown'})")
  }
  finishAppMediatedInitialization(true, 'WebSocket and subscription setup settled')
}

void finishAppMediatedInitialization(Boolean succeeded, String detail) {
  String requestId = runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY]?.toString()
  if(!requestId) { return }
  unschedule('checkAppMediatedInitializationReady')
  runtimeState.remove(APP_INITIALIZATION_REQUEST_ID_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_STARTED_AT_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_RESUBSCRIPTIONS_STARTED_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_UPNP_STARTED_STATE_KEY)
  runtimeState.remove(APP_INITIALIZATION_CONNECTION_GRANTED_STATE_KEY)
  runtimeState.remove('appMediatedInitializationFailure')
  publishPlayerStateUpdate([kind: succeeded ? 'initializationComplete' : 'initializationFailed',
      requestId: requestId, detail: detail])
  if(succeeded) { logInfo("App-mediated initialization complete: ${detail}") }
  else if(detail?.contains('180 seconds') || detail?.contains('hard timeout')) {
    logDebug("App-mediated initialization timed out: ${detail}")
  }
  else { logWarn("App-mediated initialization failed: ${detail}") }
}

void secondaryConfiguration() {
  // Validate critical device data before proceeding
  List<String> criticalFields = ['id', 'deviceIp', 'localUpnpHost', 'websocketUrl']
  List<String> missingFields = []
  criticalFields.each { field ->
    String value = device.getDataValue(field)
    if(!value || value == 'null') {
      missingFields << field
    }
  }

  if(missingFields.size() > 0) {
    logWarn("Cannot complete secondaryConfiguration - missing critical data: ${missingFields.join(', ')}")
    logWarn("This usually indicates the parent app hasn't finished configuring the device yet.")
    // Schedule retry
    runIn(10, 'secondaryConfiguration')
    return
  }

  // Phase 1: First batch of child devices
  createRemoveCrossfadeChildDevice(getCreateCrossfadeChildDevice())
  createRemoveShuffleChildDevice(getCreateShuffleChildDevice())
  createRemoveRepeatOneChildDevice(getCreateRepeatOneChildDevice())
  runIn(1, 'secondaryConfigurationPhase2')
}

void secondaryConfigurationPhase2() {
  // Phase 2: Second batch of child devices
  createRemoveRepeatAllChildDevice(getCreateRepeatAllChildDevice())
  createRemoveMuteChildDevice(getCreateMuteChildDevice())
  createRemoveBatteryStatusChildDevice(getCreateBatteryStatusChildDevice())
  runIn(1, 'secondaryConfigurationPhase3')
}

void secondaryConfigurationPhase3() {
  // Phase 3: Third batch of child devices
  createRemoveFavoritesChildDevice(getCreateFavoritesChildDevice())
  createRemovePlaylistChildDevice(getCreatePlaylistChildDevice())
  createRemoveQueueChildDevice(getCreateQueueChildDevice())
  createRemoveSleepTimerChildDevice(getCreateSleepTimerChildDevice())
  if(getCreateSleepTimerChildDevice()) { getSleepTimer() }
  createRemoveRightChannelChildDevice(getCreateRightChannelChildDevice())
  runIn(1, 'secondaryConfigurationPhase4')
}

void secondaryConfigurationPhase4() {
  // Phase 4: Final child devices, initialization, and subscription setup
  createRemoveNightModeChildDevice(getCreateNightModeChildDevice())
  createRemoveSpeechEnhancementChildDevice(getCreateSpeechEnhancementChildDevice())
  if(getDisableTrackDataEvents()) { clearTrackDataEvent() }
  if(getDisableArtistAlbumTrackEvents()) { clearCurrentNextArtistAlbumTrackData() }
  if(getDisableAlbumArtEvents()) { clearAlbumArtEvents() }
  audioClipQueueInitialization()
  groupsRegistryInitialization()
  favoritesMapInitialization()

  // After device data is configured, force a complete recovery. The persisted
  // WebSocket status and in-memory namespace markers may survive a driver update
  // even though the underlying connection and subscriptions do not.
  String localUpnpHost = device.getDataValue('localUpnpHost')
  String websocketUrl = device.getDataValue('websocketUrl')

  if(localUpnpHost && websocketUrl) {
    requestFullSubscriptionRenewal(1, 'secondary configuration')
  } else {
    logWarn('Device data still incomplete after validation - subscriptions not established')
  }
}

void migrationCleanup() {
  unschedule('resubscribeToGMEvents')
  unschedule('registerRinconId')
  unschedule('checkSubscriptions')
  if(settings.disableTrackDataEvents == null) { settings.disableTrackDataEvents = true }
  if(settings.enableTrackPositionUpdates == null) { settings.enableTrackPositionUpdates = false }
  if(settings.includeTrackDataMetaData == null) { settings.includeTrackDataMetaData = false }
  if(settings.volumeAdjustAmountLow == null) { settings.volumeAdjustAmountLow = 5 }
  if(settings.volumeAdjustAmountMid == null) { settings.volumeAdjustAmountMid = 5 }
  if(settings.volumeAdjustAmount == null) { settings.volumeAdjustAmount = 5 }
  if(settings.ttsBoostAmount == null) { settings.ttsBoostAmount = 10 }
  if(settings.disableArtistAlbumTrackEvents == null) { settings.disableArtistAlbumTrackEvents = false }
  if(settings.disableAlbumArtEvents == null) { settings.disableAlbumArtEvents = true }
  if(settings.createCrossfadeChildDevice == null) { settings.createCrossfadeChildDevice = false }
  if(settings.createShuffleChildDevice == null) { settings.createShuffleChildDevice = false }
  if(settings.createRepeatOneChildDevice == null) { settings.createRepeatOneChildDevice = false }
  if(settings.createRepeatAllChildDevice == null) { settings.createRepeatAllChildDevice = false }
  if(settings.createBatteryStatusChildDevice == null) { settings.createBatteryStatusChildDevice = false }
  if(settings.createFavoritesChildDevice == null) { settings.createFavoritesChildDevice = false }
  if(settings.createPlaylistChildDevice == null) { settings.createPlaylistChildDevice = false }
  if(settings.createQueueChildDevice == null) { settings.createQueueChildDevice = false }
  if(settings.createRightChannelChildDevice == null) { settings.createRightChannelChildDevice = false }
  if(settings.createNightModeChildDevice == null) { settings.createNightModeChildDevice = false }
  if(settings.createSpeechEnhancementChildDevice == null) { settings.createSpeechEnhancementChildDevice = false }
  if(settings.chimeBeforeStandardPriorityTTS == null) {
    settings.chimeBeforeStandardPriorityTTS = settings.chimeBeforeTTS != null ? settings.chimeBeforeTTS : false
  }
  if(settings.chimeBeforeHighPriorityTTS == null) { settings.chimeBeforeHighPriorityTTS = false }
  if(settings.enableAirPlayUnmuteVolumeFix == null) { settings.enableAirPlayUnmuteVolumeFix = true }
  if(settings.enableRawWebsocketLogging == null) { settings.enableRawWebsocketLogging = false }
  if(settings.createMuteChildDevice == null) { settings.createMuteChildDevice = false }
  // Remove debug-only device data keys when debug logging is off
  if(!isDebugLoggingEnabled()) {
    removeWsDeviceDataKeys()
    removeEventTimestampDeviceDataKeys()
  }
  // Remove stale sid-expiresFormatted keys (replaced by human-readable sid-expires)
  ['sid1', 'sid2', 'sid3', 'sid4'].each { String sid ->
    if(device.getDataValue("${sid}-expiresFormatted") != null) { device.removeDataValue("${sid}-expiresFormatted") }
  }
}

void maybeCleanupStaticDriverState() {
  Long nowEpoch = Instant.now().getEpochSecond()
  Long lastCleanup = lastStaticStateCleanupEpoch != null ? lastStaticStateCleanupEpoch : 0L
  Long lastAttempt = lastStaticStateCleanupAttemptEpoch != null ? lastStaticStateCleanupAttemptEpoch : 0L
  Long lastCheck = Math.max(lastCleanup, lastAttempt)
  if((nowEpoch - lastCheck) < STATIC_STATE_CLEANUP_INTERVAL_SECONDS) { return }
  lastStaticStateCleanupAttemptEpoch = nowEpoch

  Map rosterSnapshot
  String serializedRoster = device.getDataValue('staticDriverActivePlayerRoster')
  if(serializedRoster) {
    try { rosterSnapshot = (Map)new JsonSlurper().parseText(serializedRoster) }
    catch(Exception e) { logDebug("Ignoring malformed parent app roster snapshot: ${e.message}") }
  }
  if(rosterSnapshot == null) {
    sendParentAppRequest('refreshRoster')
    return
  }
  List<Map<String, String>> activePlayerRoster = validateStaticDriverActivePlayerRoster(rosterSnapshot, nowEpoch)
  if(activePlayerRoster == null) { return }

  if(!staticStateCleanupMutex.tryAcquire()) { return }

  try {
    lastCleanup = lastStaticStateCleanupEpoch != null ? lastStaticStateCleanupEpoch : 0L
    if((nowEpoch - lastCleanup) < STATIC_STATE_CLEANUP_INTERVAL_SECONDS) { return }
    cleanupStaticDriverState(activePlayerRoster)
    lastStaticStateCleanupEpoch = nowEpoch
  } finally {
    staticStateCleanupMutex.release()
  }
}

List<Map<String, String>> validateStaticDriverActivePlayerRoster(Map rosterSnapshot, Long nowEpoch) {
  if(rosterSnapshot == null || !(rosterSnapshot.version instanceof Number)) { return null }
  if(((Number)rosterSnapshot.version).intValue() != STATIC_STATE_ROSTER_VERSION) { return null }
  if(!(rosterSnapshot.refreshedAt instanceof Number)) { return null }
  Long refreshedAt = ((Number)rosterSnapshot.refreshedAt).longValue()
  if(refreshedAt == null || refreshedAt <= 0L || refreshedAt > (nowEpoch + 60L)) { return null }
  if((nowEpoch - refreshedAt) > STATIC_STATE_ROSTER_MAX_AGE_SECONDS) { return null }

  Object rawPlayers = rosterSnapshot.players
  if(!(rawPlayers instanceof List)) { return null }
  List<Map<String, String>> activePlayerRoster = []
  Set<String> seenPlayerIds = new HashSet<String>()
  Set<String> seenDnis = new HashSet<String>()
  for(Object rawPlayer : (List)rawPlayers) {
    if(!(rawPlayer instanceof Map)) { return null }
    Map player = (Map)rawPlayer
    String playerId = player.playerId?.toString()?.trim()
    String dni = player.dni?.toString()?.trim()
    if(!playerId || !dni || !seenPlayerIds.add(playerId) || !seenDnis.add(dni)) { return null }
    activePlayerRoster.add([playerId: playerId, dni: dni])
  }
  return activePlayerRoster
}

void cleanupStaticDriverState(List<Map<String, String>> activePlayerRoster) {
  Set<String> activePlayerIds = new HashSet<String>()
  Set<String> activePlayerDnis = new HashSet<String>()
  (activePlayerRoster ?: []).each { Map<String, String> player ->
    String playerId = player.playerId
    String dni = player.dni
    if(playerId != null && playerId != '' && dni != null && dni != '') {
      activePlayerIds.add(playerId)
      activePlayerDnis.add(dni)
    }
  }

  if(activePlayerIds.isEmpty() && activePlayerDnis.isEmpty()) {
    Integer clearedEntries = clearStaticMap(audioClipQueue)
    clearedEntries += clearStaticMap(runtimeStateByDni)
    clearedEntries += clearStaticMap(audioClipQueueHighPriority)
    clearedEntries += clearStaticMap(audioClipQueueSaved)
    clearedEntries += clearStaticMap(audioClipQueueTimers)
    clearedEntries += clearStaticMap(groupsRegistry)
    clearedEntries += clearStaticMap(statesRegistry)
    clearedEntries += clearStaticMap(favoritesMap)
    clearedEntries += clearStaticMap(playlistsMap)
    clearedEntries += clearStaticMap(volumeFadeState)
    clearedEntries += clearStaticMap(pendingGroupedPlayerVolumes)
    clearedEntries += clearStaticMap(groupVolumeFadeState)
    clearedEntries += clearStaticMap(lastVolumeFadeCallTime)
    clearedEntries += clearStaticMap(lastGroupVolumeFadeCallTime)
    clearedEntries += clearStaticMap(favoriteRetryState)
    clearedEntries += clearStaticMap(playlistRetryState)
    clearedEntries += clearStaticMap(pendingGroupDeviceUpdates)
    clearedEntries += clearStaticMap(groupDeviceUpdateRetryAttempts)
    clearedEntries += clearStaticMap(pendingLocalDeviceEvents)
    clearedEntries += clearStaticMap(lastLocalDeviceEvents)
    clearedEntries += clearStaticMap(localDeviceEventLocks)
    clearedEntries += clearStaticMap(lastMetadataContainerId)
    clearedEntries += clearStaticMap(lastPlaybackState)
    clearedEntries += clearStaticMap(lastWsEventLog)
    clearedEntries += clearStaticMap(lastZgtXmlHash)
    clearedEntries += clearStaticMap(pendingTopologyXmlByDni)
    clearedEntries += clearStaticMap(pendingTopologySnapshotByDni)
    clearedEntries += clearStaticMap(pendingTopologyWebsocketByDni)
    clearedEntries += clearStaticMap(pendingTopologyKindByDni)
    clearedEntries += clearStaticMap(wsSubscriptionStatus)
    clearedEntries += clearStaticMap(jsonSlurpers)
    clearedEntries += clearStaticMap(eventTimestamps)
    clearedEntries += clearStaticMap(favPlaylistDelegate)
    clearedEntries += cleanupUpnpRequestQueues([] as Set<String>)
    clearedEntries += clearStaticMap(subscribeRetryAttempts)
    if(clearedEntries > 0) {
      logDebug("Cleared ${clearedEntries} orphaned static Sonos Advanced Player cache entries with no active players present")
    }
    return
  }
  if(activePlayerIds.isEmpty() || activePlayerDnis.isEmpty()) { return }

  Integer removedEntries = 0
  removedEntries += pruneStaticMapKeys(audioClipQueue, activePlayerIds)
  removedEntries += pruneStaticMapKeys(runtimeStateByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(audioClipQueueHighPriority, activePlayerIds)
  removedEntries += pruneStaticMapKeys(audioClipQueueSaved, activePlayerIds)
  removedEntries += pruneStaticMapKeys(audioClipQueueTimers, activePlayerIds)
  removedEntries += pruneStaticMapKeys(groupsRegistry, activePlayerIds)
  removedEntries += pruneStaticMapKeys(statesRegistry, activePlayerIds)
  removedEntries += pruneStaticMapKeys(favoritesMap, activePlayerIds)
  removedEntries += pruneStaticMapKeys(playlistsMap, activePlayerIds)
  removedEntries += pruneStaticMapKeys(volumeFadeState, activePlayerIds)
  removedEntries += pruneStaticMapKeys(pendingGroupedPlayerVolumes, activePlayerIds)
  removedEntries += pruneStaticMapKeys(groupVolumeFadeState, activePlayerIds)
  removedEntries += pruneStaticMapKeys(lastVolumeFadeCallTime, activePlayerIds)
  removedEntries += pruneStaticMapKeys(lastGroupVolumeFadeCallTime, activePlayerIds)
  removedEntries += pruneStaticMapKeys(favoriteRetryState, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(playlistRetryState, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingGroupDeviceUpdates, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(groupDeviceUpdateRetryAttempts, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingLocalDeviceEvents, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastLocalDeviceEvents, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(localDeviceEventLocks, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastMetadataContainerId, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastPlaybackState, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastWsEventLog, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastZgtXmlHash, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingTopologyXmlByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingTopologySnapshotByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingTopologyWebsocketByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingTopologyKindByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(wsSubscriptionStatus, activePlayerDnis, '-WS-')
  removedEntries += pruneStaticMapKeys(jsonSlurpers, activePlayerDnis)
  removedEntries += pruneEventTimestampKeys(activePlayerDnis)
  removedEntries += pruneFavPlaylistDelegates(activePlayerDnis)
  removedEntries += cleanupUpnpRequestQueues(activePlayerDnis)
  removedEntries += pruneDniPrefixedMap(subscribeRetryAttempts, activePlayerDnis)

  if(removedEntries > 0) {
    logDebug("Cleaned ${removedEntries} orphaned static Sonos Advanced Player cache entries")
  }
}

@CompileStatic
Integer clearStaticMap(Map map) {
  if(map == null || map.isEmpty()) { return 0 }
  Integer removedEntries = map.size()
  map.clear()
  return removedEntries
}

@CompileStatic
Integer pruneStaticMapKeys(ConcurrentHashMap map, Set<String> activeKeys, String prefixDelimiter = null) {
  if(map == null || map.isEmpty() || activeKeys == null || activeKeys.isEmpty()) { return 0 }

  Integer removedEntries = 0
  List<String> keys = new ArrayList<String>(map.keySet())
  keys.each { String key ->
    String normalizedKey = prefixDelimiter != null ? extractKeyPrefix(key, prefixDelimiter) : key
    if(normalizedKey == null || !activeKeys.contains(normalizedKey)) {
      if(map.remove(key) != null) {
        removedEntries++
      }
    }
  }
  return removedEntries
}

@CompileStatic
Integer pruneEventTimestampKeys(Set<String> activePlayerDnis) {
  if(eventTimestamps == null || eventTimestamps.isEmpty() || activePlayerDnis == null || activePlayerDnis.isEmpty()) { return 0 }

  Integer removedEntries = 0
  List<String> keys = new ArrayList<String>(eventTimestamps.keySet())
  keys.each { String key ->
    String dni = extractEventTimestampDni(key)
    if(dni == null || !activePlayerDnis.contains(dni)) {
      if(eventTimestamps.remove(key) != null) {
        removedEntries++
      }
    }
  }
  return removedEntries
}

@CompileStatic
Integer pruneFavPlaylistDelegates(Set<String> activePlayerDnis) {
  if(favPlaylistDelegate == null || favPlaylistDelegate.isEmpty() || activePlayerDnis == null || activePlayerDnis.isEmpty()) { return 0 }

  Integer removedEntries = 0
  List<String> householdIds = new ArrayList<String>(favPlaylistDelegate.keySet())
  householdIds.each { String householdId ->
    String delegateDni = favPlaylistDelegate.get(householdId)
    if(delegateDni == null || !activePlayerDnis.contains(delegateDni)) {
      if(favPlaylistDelegate.remove(householdId, delegateDni)) {
        removedEntries++
      }
    }
  }
  return removedEntries
}

// Prune keys from a static map whose leading segment (before the first '-')
// isn't an active DNI. Used for the per-device subscription/retry maps whose
// keys are either "${dni}" or "${dni}-${suffix}". Safe assumption: Sonos
// DNIs are RINCON_XXXX style with no '-' characters.
@CompileStatic
Integer pruneDniPrefixedMap(ConcurrentHashMap map, Set<String> activeDnis) {
  if(map == null || map.isEmpty() || activeDnis == null || activeDnis.isEmpty()) { return 0 }
  Integer removed = 0
  List<String> keys = new ArrayList<String>(map.keySet())
  keys.each { String key ->
    Integer idx = key.indexOf('-')
    String dni = idx < 0 ? key : key.substring(0, idx)
    if(!activeDnis.contains(dni)) {
      if(map.remove(key) != null) { removed++ }
    }
  }
  return removed
}

@CompileStatic
String extractKeyPrefix(String key, String delimiter) {
  if(key == null || delimiter == null) { return null }
  Integer delimiterIndex = key.indexOf(delimiter)
  if(delimiterIndex < 0) { return key }
  if(delimiterIndex == 0) { return null }
  return key.substring(0, delimiterIndex)
}

@CompileStatic
String extractEventTimestampDni(String key) {
  String dni = extractKeyPrefix(key, '-last')
  if(dni == null || dni == key) {
    dni = extractKeyPrefix(key, '-sid')
  }
  return dni
}

void clearStaticDriverStateForCurrentDevice() {
  retireUpnpRequestsForDevice()
  Boolean cleanupMutexAcquired = false
  try {
    cleanupMutexAcquired = staticStateCleanupMutex.tryAcquire(100, TimeUnit.MILLISECONDS)
  } catch(InterruptedException e) {
    logWarn("Interrupted while acquiring static state cleanup mutex: ${e.message}")
  }

  String playerId = getId()
  String dni = getDeviceDNI()

  try {
    if(playerId != null && playerId != '') {
      audioClipQueue?.remove(playerId)
      audioClipQueueHighPriority?.remove(playerId)
      audioClipQueueSaved?.remove(playerId)
      audioClipQueueTimers?.remove(playerId)
      groupsRegistry?.remove(playerId)
      statesRegistry?.remove(playerId)
      favoritesMap?.remove(playerId)
      playlistsMap?.remove(playerId)
      volumeFadeState?.remove(playerId)
      pendingGroupedPlayerVolumes?.remove(playerId)
      groupVolumeFadeState?.remove(playerId)
      lastVolumeFadeCallTime?.remove(playerId)
      lastGroupVolumeFadeCallTime?.remove(playerId)
    }

    if(dni != null && dni != '') {
      runtimeStateByDni?.remove(dni)
      favoriteRetryState?.remove(dni)
      playlistRetryState?.remove(dni)
      pendingGroupDeviceUpdates?.remove(dni)
      groupDeviceUpdateRetryAttempts?.remove(dni)
      pendingLocalDeviceEvents?.remove(dni)
      lastLocalDeviceEvents?.remove(dni)
      localDeviceEventLocks?.remove(dni)
      lastMetadataContainerId?.remove(dni)
      lastPlaybackState?.remove(dni)
      lastWsEventLog?.remove(dni)
      lastZgtXmlHash?.remove(dni)
      jsonSlurpers?.remove(dni)
      wsSubscriptionStatus?.keySet()?.removeAll { String key -> key.startsWith("${dni}-WS-") }
      eventTimestamps?.keySet()?.removeAll { String key -> key.startsWith("${dni}-") }
      Map upnpEntry = upnpRequestQueues?.get(dni)
      if(upnpEntry != null) {
        synchronized(upnpEntry.lock) {
          if(upnpEntry.active == null && (upnpEntry.queue as List<Map>).isEmpty()) { upnpRequestQueues.remove(dni, upnpEntry) }
        }
      }
      subscribeRetryAttempts?.keySet()?.removeAll { String key -> key.startsWith("${dni}-") }
    }

    releaseFavPlaylistDelegate()
  } finally {
    if(cleanupMutexAcquired) {
      staticStateCleanupMutex.release()
    }
  }
}

void onUninstalled() {
  clearStaticDriverStateForCurrentDevice()
}


void audioClipQueueInitialization() {
  maybeCleanupStaticDriverState()
  if(!(audioClipQueue instanceof ConcurrentHashMap)) { audioClipQueue = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>() }
  if(!audioClipQueue.containsKey(getId())) {
    audioClipQueue[getId()] = new ConcurrentLinkedQueue<Map>()
  }
  if(!(audioClipQueueHighPriority instanceof ConcurrentHashMap)) { audioClipQueueHighPriority = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>() }
  if(!audioClipQueueHighPriority.containsKey(getId())) {
    audioClipQueueHighPriority[getId()] = new ConcurrentLinkedQueue<Map>()
  }
  if(!(audioClipQueueSaved instanceof ConcurrentHashMap)) { audioClipQueueSaved = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>() }
  if(!audioClipQueueSaved.containsKey(getId())) {
    audioClipQueueSaved[getId()] = new ConcurrentLinkedQueue<Map>()
  }
  if(!(audioClipQueueTimers instanceof ConcurrentHashMap)) { audioClipQueueTimers = new ConcurrentHashMap<String, LinkedHashMap>() }
  if(!audioClipQueueTimers.containsKey(getId())) {
    audioClipQueueTimers[getId()] = new LinkedHashMap()
  }
}


void groupsRegistryInitialization() {
  if(!(groupsRegistry instanceof ConcurrentHashMap)) {groupsRegistry = new ConcurrentHashMap<String, ArrayList<DeviceWrapper>>()}
  if(!groupsRegistry.containsKey(getId())) {groupsRegistry[getId()] = new ArrayList<DeviceWrapper>()}
}

void favoritesMapInitialization() {
  if(!(favoritesMap instanceof ConcurrentHashMap)) { favoritesMap = new ConcurrentHashMap<String, LinkedHashMap>() }
  // getFavorites() is now triggered by subscribeToWsEvents() after WebSocket connects
}

@CompileStatic
void checkSubscriptions() {}
// =============================================================================
// End Initialize and Configure
// =============================================================================



// =============================================================================
// Device Methods
// =============================================================================
@CompileStatic
void setRepeatMode(String mode) {
  logDebug("Setting repeat mode to ${mode}")
  if(routeGroupedPlaybackCommandToCoordinator('setRepeatMode', [mode])) { return }
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
  if(routeGroupedPlaybackCommandToCoordinator('setCrossfade', [mode])) { return }
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
  if(routeGroupedPlaybackCommandToCoordinator('setShuffle', [mode])) { return }
  Map playModes = mode == 'on' ? [ 'shuffle': true ] : [ 'shuffle': false ]
  playerSetPlayModes(playModes)
}
@CompileStatic
void shuffleOn() { setShuffle('on') }
@CompileStatic
void shuffleOff() { setShuffle('off') }

@CompileStatic
void ungroupPlayer() { requestParentCoordinatorCommand('ungroupPlayer') }

/** Add one resolved player to this player's current Sonos group. */
void addPlayerToGroup(String playerIdentifier) {
  String identifier = playerIdentifier?.trim()
  if(!identifier) {
    logWarn('Cannot add player to group without a player identifier')
    return
  }
  requestParentCoordinatorCommand('addPlayerToGroup', [identifier])
}

void playText(String text, Object volume = null) {
  devicePlayText(text, normalizePublicPlaybackVolume(volume))
}
// Public speech entry points. Volume is typed as Object to tolerate callers
// that pass Integer/Long/String (Rule Machine, webCoRE, user apps). Without
// this the @CompileStatic signature throws ClassCastException on any caller
// that doesn't hand us a literal BigDecimal.
void playTextAndRestore(String text, Object volume = null) {
  devicePlayTextWithAudioClip(text, normalizePublicPlaybackVolume(volume), null, 'playTextAndRestore')
}
void playTextAndResume(String text, Object volume = null) {
  devicePlayTextWithAudioClip(text, normalizePublicPlaybackVolume(volume), null, 'playTextAndResume')
}
void speak(String text, Object volume = null, String voice = null) { devicePlayText(text, normalizePublicPlaybackVolume(volume), voice) }

@CompileStatic
BigDecimal coerceVolume(Object volume) {
  if(volume == null) { return null }
  if(volume instanceof BigDecimal) { return (BigDecimal) volume }
  if(volume instanceof Number) { return new BigDecimal(((Number) volume).toString()) }
  if(volume instanceof CharSequence) {
    String asString = volume.toString().trim()
    if(asString == '') { return null }
    try { return new BigDecimal(asString) }
    catch(NumberFormatException e) {
      logWarn("Could not parse volume '${asString}'; ignoring.")
      return null
    }
  }
  logWarn("Unsupported volume value '${volume}'; ignoring.")
  return null
}

/** Optional playback volume compatibility: zero means omitted; -1 is retained as an explicit-zero sentinel. */
@CompileStatic
BigDecimal normalizePublicPlaybackVolume(Object volume) {
  BigDecimal normalized = coerceVolume(volume)
  if(normalized == null) { return null }
  if(normalized.compareTo(BigDecimal.ZERO) == 0) { return null }
  return normalized
}

@CompileStatic
void setTrack(String uri) {
  if(deferCommandToParentDuringGroupOperation('setTrack', [uri])) { return }
  if(routeGroupedPlaybackCommandToCoordinator('setTrack', [uri])) { return }
  componentSetStreamUrlLocal(uri)
}
void playTrack(String uri, Object volume = null) {
  BigDecimal normalizedVolume = normalizePublicPlaybackVolume(volume)
  if(deferCommandToParentDuringGroupOperation('playTrack', [uri, normalizedVolume])) { return }
  if(routeGroupedPlaybackCommandToCoordinator('playTrack', [uri, normalizedVolume])) { return }
  if(shouldUseAnnouncementAudioClip(false)) { playerLoadAudioClip(uri, normalizedVolume) }
  else{ componentLoadStreamUrlLocal(uri, normalizedVolume) }
}
@CompileStatic
void playTrackAndRestore(String uri, Object volume = null) { playerLoadAudioClip(uri, normalizePublicPlaybackVolume(volume)) }
@CompileStatic
void playTrackAndResume(String uri, Object volume = null) { playerLoadAudioClip(uri, normalizePublicPlaybackVolume(volume)) }

void devicePlayText(String text, BigDecimal volume = null, String voice = null) {
  logDebug("Speak request entered; hasText=${text != null && text.toString().trim() != ''}, requestedVolume=${volume}, voiceSpecified=${voice != null && voice != ''}, audioClipCapability=${hasAudioClipCapability()}")
  if(deferCommandToParentDuringGroupOperation('speak', [text, volume, voice])) {
    logDebug('Speak request deferred to the parent app during an active group operation')
    return
  }
  Boolean airPlaySource = isCurrentAirPlayAudioSource()
  Boolean useAudioClip = hasAudioClipCapability() == true && shouldUseAnnouncementAudioClip(false)
  if(airPlaySource && hasAudioClipCapability() == true) {
    try {
      useAudioClip = readParentAppPolicy('useAudioClipForAirPlay', false)
    } catch(Exception e) {
      logWarn("Could not read AirPlay announcement policy; using the standard stream method: ${e.message}")
    }
  }
  // AirPlay cannot be transferred to a temporary holder. Keep the existing
  // audioClip route when policy requests it; only non-AirPlay streams may use
  // Sonos Advanced's coordinator handoff path.
  if(airPlaySource && useAudioClip) {
    LinkedHashMap tts = textToSpeech(text, voice)
    String streamUrl = tts?.uri?.toString()
    Integer ttsDuration = coerceClipDuration(tts?.duration) ?: 30
    logDebug("Speak TTS generated; route=local-airplay-audioClip, hasUri=${streamUrl != null && streamUrl != ''}, duration=${ttsDuration}")
    playerLoadAudioClip(streamUrl, volume, ttsDuration, UUID.randomUUID().toString())
  } else {
    LinkedHashMap tts = textToSpeech(text, voice)
    String streamUrl = tts?.uri?.toString()
    Integer ttsDuration = coerceClipDuration(tts?.duration) ?: 30
    if(!airPlaySource && streamUrl) {
      String requestId = UUID.randomUUID().toString()
      Map<String, Object> pending = state.pendingParentAppTtsRequests instanceof Map
          ? (Map<String, Object>)state.pendingParentAppTtsRequests : [:]
      Long requestCreatedAt = now()
      pending[requestId] = [uri: streamUrl, volume: volume?.toString(), duration: ttsDuration,
        correlationId: UUID.randomUUID().toString(), useAudioClip: useAudioClip, createdAt: requestCreatedAt]
      state.pendingParentAppTtsRequests = pending
      logDebug("Speak TTS generated; route=parent-activeStreamTts, requestId=${requestId}, correlation=${pending[requestId].correlationId}, duration=${ttsDuration}, activeRequests=${pending.size()}")
      Boolean accepted = sendParentAppRequest('activeStreamTts', [requestId: requestId, uri: streamUrl,
        volume: volume?.toString(), duration: ttsDuration, correlationId: pending[requestId].correlationId,
        createdAt: requestCreatedAt])
      completeParentAppActiveStreamTts(requestId, (accepted == true).toString())
      return
    } else {
      logDebug("Speak TTS generated; route=${airPlaySource ? 'local-airplay' : 'local-fallback'}, hasUri=${streamUrl != null && streamUrl != ''}, useAudioClip=${useAudioClip}, duration=${ttsDuration}")
      if(streamUrl) {
        if(useAudioClip) {
          playerLoadAudioClip(streamUrl, volume, ttsDuration, UUID.randomUUID().toString())
        } else {
          volume = getAnnouncementVolume(volume)
          componentPlayStreamWithRestoreLocal(streamUrl, ttsDuration, volume)
        }
      } else {
        volume = getAnnouncementVolume(volume)
        componentPlayTextWithRestoreLocal(text, volume, voice)
      }
    }
  }
}

Boolean isAirPlayAudioSource(String audioSource) {
  String normalized = audioSource?.toLowerCase()
  return normalized?.contains('airplay:') == true || normalized?.contains('linein.airplay') == true
}

Boolean isCurrentAirPlayAudioSource() {
  if(isAirPlayAudioSource(device.currentValue('audioSource')?.toString())) { return true }
  String observedType = state.audioSourceMetadataType?.toString()
  Long observedAt = null
  try { observedAt = state.audioSourceMetadataObservedAt?.toString()?.toLong() }
  catch(Exception ignored) { observedAt = null }
  return observedType?.equalsIgnoreCase('linein.airplay') == true && observedAt != null &&
      now() >= observedAt && now() - observedAt <= 180000L
}

/** Explicit Play Text And Restore/Resume commands always prefer Sonos audioClip,
 * independent of the app's automatic announcement policy. */
void devicePlayTextWithAudioClip(String text, BigDecimal volume = null, String voice = null,
    String deferredCommand = 'playTextAndRestore') {
  if(!text) {
    logWarn('No text provided to play')
    return
  }
  if(deferCommandToParentDuringGroupOperation(deferredCommand, [text, volume, voice])) { return }
  if(hasAudioClipCapability() == true) {
    LinkedHashMap tts = textToSpeech(text, voice)
    Integer ttsDuration = coerceClipDuration(tts?.duration) ?: 0
    playerLoadAudioClip(tts.uri as String, volume, ttsDuration, UUID.randomUUID().toString())
  } else {
    logDebug("${device} does not support audioClip; using the streaming TTS fallback")
    volume = getAnnouncementVolume(volume)
    componentPlayTextWithRestoreLocal(text, volume, voice)
  }
}

/** Ask the controller app for the shared group/player announcement policy. */
Boolean shouldUseAnnouncementAudioClip(Boolean highPriority = false) {
  return readParentAppPolicy(highPriority ? 'useAudioClipForHighPriorityTts' : 'useAudioClipForPlayer', false)
}

Boolean readParentAppPolicy(String dataKey, Boolean fallback) {
  String value = device.getDataValue(dataKey)
  if(value == 'true') { return true }
  if(value == 'false') { return false }
  sendParentAppRequest('refreshPolicy')
  return fallback
}

Boolean sendParentAppRequest(String action, Map args = [:]) {
  Map payload = [version: 1, channel: 'playerAppRequest', action: action,
    sourceDni: device.getDeviceNetworkId(), requestId: UUID.randomUUID().toString(), args: args ?: [:]]
  logDebug("Sending parent app request action=${action}, eventRequestId=${payload.requestId}, ttsRequestId=${args?.requestId ?: 'none'}, sourceDni=${payload.sourceDni}")
  Boolean response = null
  try {
    response = parent != null ? (Boolean)parent.playerAppRequest(payload) : null
  } catch(Exception e) {
    logWarn("Parent app request callback failed action=${action}, requestId=${payload.requestId}, ttsRequestId=${args?.requestId ?: 'none'}, sourceDni=${payload.sourceDni}: ${e.message}")
    throw e
  }
  logDebug("Parent app request callback returned action=${action}, requestId=${payload.requestId}, accepted=${response}, ttsRequestId=${args?.requestId ?: 'none'}, sourceDni=${payload.sourceDni}")
  return response
}

/** Complete the asynchronous parent-app decision for an active-stream TTS request. */
void completeParentAppActiveStreamTts(String requestId, String accepted) {
  Map<String, Object> pending = state.pendingParentAppTtsRequests instanceof Map
      ? (Map<String, Object>)state.pendingParentAppTtsRequests : [:]
  Map request = pending[requestId] instanceof Map ? (Map)pending[requestId] : null
  if(request == null) {
    logDebug("Ignoring parent active-stream TTS response for unknown or expired requestId=${requestId ?: 'none'}, accepted=${accepted}")
    return
  }
  pending.remove(requestId)
  state.pendingParentAppTtsRequests = pending
  Long ageMs = request.createdAt != null ? Math.max(0L, now() - (request.createdAt as Long)) : -1L
  logDebug("Parent active-stream TTS response received; requestId=${requestId}, accepted=${accepted}, ageMs=${ageMs}, remainingRequests=${pending.size()}")
  if(accepted == 'true') { return }

  String uri = request.uri?.toString()
  if(!uri) { return }
  BigDecimal volume = null
  try { volume = request.volume != null ? new BigDecimal(request.volume.toString()) : null }
  catch(Exception e) { logWarn("Could not restore TTS volume for parent response ${requestId}: ${e.message}") }
  Integer duration = (request.duration ?: 30) as Integer
  if(request.useAudioClip == true && hasAudioClipCapability() == true) {
    logDebug("Parent declined active-stream TTS; using local audioClip fallback requestId=${requestId}, correlation=${request.correlationId}")
    playerLoadAudioClip(uri, volume, duration, request.correlationId?.toString())
  } else {
    BigDecimal announcementVolume = getAnnouncementVolume(volume)
    logDebug("Parent declined active-stream TTS; using local stream fallback requestId=${requestId}, duration=${duration}")
    componentPlayStreamWithRestoreLocal(uri, duration, announcementVolume)
  }
}

void playHighPriorityTTS(String text, Object volume = null, String voice = null) {
  BigDecimal normalizedVolume = normalizePublicPlaybackVolume(volume)
  if(deferCommandToParentDuringGroupOperation('playHighPriorityTTS', [text, normalizedVolume, voice])) { return }
  if(hasAudioClipCapability() == true && shouldUseAnnouncementAudioClip(true)) {
    LinkedHashMap tts = textToSpeech(text, voice)
    Integer ttsDuration = coerceClipDuration(tts?.duration) ?: 0
    playerLoadAudioClipHighPriority(tts.uri as String, normalizedVolume, UUID.randomUUID().toString(), ttsDuration,
        false, getChimeBeforeHighPriorityTTS())
  } else {
    BigDecimal announcementVolume = getAnnouncementVolume(normalizedVolume) as BigDecimal
    componentPlayTextWithRestoreLocal(text, announcementVolume, voice)
  }
}

@CompileStatic
void playHighPriorityTrack(String uri, Object volume = null) {
  BigDecimal normalizedVolume = normalizePublicPlaybackVolume(volume)
  if(hasAudioClipCapability() == true && shouldUseAnnouncementAudioClip(true)) {
    playerLoadAudioClipHighPriority(uri, normalizedVolume)
  } else {
    componentLoadStreamUrlLocal(uri, normalizedVolume, true)
  }
}

void devicePlayTextNoRestore(String text, BigDecimal volume = null, String voice = null) {
  volume = normalizePublicPlaybackVolume(volume)
  if(deferCommandToParentDuringGroupOperation('speakNoRestore', [text, volume, voice])) { return }
  volume = getAnnouncementVolume(volume)
  componentPlayTextNoRestoreLocal(text, volume, voice)
}

/** Start an announcement as a coordinator-owned stream when no source is active. */
void playerPlayGroupAnnouncementStream(String uri, BigDecimal volume, String correlationId) {
  if(!uri || !correlationId) {
    logWarn('Cannot start group announcement stream without a URI and correlation ID')
    return
  }
  Integer targetVolume = getAnnouncementVolume(volume)
  Map existingSnapshot = runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] instanceof Map
      ? (Map)runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] : null
  Map previousAnnouncement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  Integer previousGroupVolume = getGroupVolumeState()
  if(previousGroupVolume == null) { previousGroupVolume = getPlayerVolume() }
  Map savedPlayback = existingSnapshot != null && previousAnnouncement != null
      ? new LinkedHashMap(existingSnapshot)
      : (runtimeState.lastTransportPlaybackSnapshot instanceof Map
          ? new LinkedHashMap((Map)runtimeState.lastTransportPlaybackSnapshot) : null)
  if(savedPlayback != null) {
    savedPlayback.correlationId = correlationId
    savedPlayback.captureAt = now()
    savedPlayback.announcementUri = uri
    savedPlayback.previousGroupVolume = previousGroupVolume
    savedPlayback.announcementVolume = targetVolume
    runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] = savedPlayback
    logDebug("Captured pre-announcement transport for ${correlationId}: uri=${savedPlayback.currentUri}, track=${savedPlayback.track}")
  } else {
    logWarn("No AVTransport snapshot is available to restore after announcement ${correlationId}")
  }
  runtimeState.groupAnnouncementStream = [correlationId: correlationId, uri: uri,
      phase: 'LOADING', activeObserved: false, previousGroupVolume: previousGroupVolume,
      announcementVolume: targetVolume]
  logInfo("Starting group announcement stream ${correlationId} on coordinator ${getId()} at volume=${targetVolume}")
  componentSetGroupLevelLocal(new BigDecimal(targetVolume))
  setAVTransportURIAndPlay(uri, null, [groupAnnouncementCorrelationId: correlationId])
}

/** Restore the coordinator's captured transport after its group announcement. */
void restoreGroupAnnouncementPlayback(String correlationId) {
  Map snapshot = runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] instanceof Map
      ? (Map)runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] : null
  Map announcement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  Boolean matchingAnnouncement = announcement?.correlationId?.toString() == correlationId
  Boolean matchingSnapshot = snapshot?.correlationId?.toString() == correlationId
  if(!matchingSnapshot && !matchingAnnouncement) {
    logDebug("No matching playback snapshot to restore for ${correlationId}")
    publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', [restored: false])
    return
  }
  String uri = matchingSnapshot ? snapshot.currentUri?.toString() : null
  String announcementUri = matchingSnapshot ? snapshot.announcementUri?.toString() : announcement?.uri?.toString()
  String currentSource = device.currentValue('audioSource')?.toString()
  if(currentSource && announcementUri && currentSource != announcementUri &&
      state.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']) {
    logInfo("Skipping playback and volume restore for ${correlationId}; another source is active")
    runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
    runtimeState.remove('groupAnnouncementStream')
    publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', [restored: false, skipped: 'external-playback'])
    return
  }
  Integer previousGroupVolume = matchingAnnouncement
      ? (announcement.previousGroupVolume as Integer)
      : (matchingSnapshot ? snapshot.previousGroupVolume as Integer : null)
  Integer announcementVolume = matchingAnnouncement
      ? (announcement.announcementVolume as Integer)
      : (matchingSnapshot ? snapshot.announcementVolume as Integer : null)
  restoreTemporaryAnnouncementVolume(correlationId, previousGroupVolume, announcementVolume)
  if(!uri) {
    logWarn("Captured transport for ${correlationId} has no URI; skipping playback restore")
    runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
    runtimeState.remove('groupAnnouncementStream')
    publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', [restored: false])
    return
  }
  runtimeState.announcementPlaybackRestore = [correlationId: correlationId, snapshot: snapshot]
  logInfo("Restoring pre-announcement transport for ${correlationId}: ${uri}")
  setAVTransportURIForAnnouncementRestore(uri, snapshot.currentUriMetaData?.toString(), correlationId)
}

/** Restore the prior group level only if the announcement's volume is still active. */
void restoreTemporaryAnnouncementVolume(String correlationId, Integer previousVolume, Integer announcementVolume) {
  if(!correlationId || previousVolume == null || announcementVolume == null || previousVolume == announcementVolume) { return }
  Integer currentVolume = getGroupVolumeState()
  if(currentVolume == null) { currentVolume = getPlayerVolume() }
  if(currentVolume != announcementVolume) {
    logDebug("Skipping volume restore for ${correlationId}; volume changed to ${currentVolume} during the announcement")
    return
  }
  logInfo("Restoring group volume after announcement ${correlationId} to ${previousVolume}")
  componentSetGroupLevelLocal(new BigDecimal(previousVolume))
}

void setAVTransportURIForAnnouncementRestore(String uri, String metadata, String correlationId) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(!ip) { finishAnnouncementPlaybackRestore(correlationId, false, 'coordinator-unavailable'); return }
  Map values = [CurrentURI: uri]
  if(metadata) { values.CurrentURIMetaData = metadata }
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', values)
  postSonosSoapAsync('announcementPlaybackUriRestored', params, [correlationId: correlationId])
}

void announcementPlaybackUriRestored(AsyncResponse response, Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  if(response?.status != 200 || response.hasError()) {
    finishAnnouncementPlaybackRestore(correlationId, false, "set-uri-failed-${response?.status}")
    return
  }
  Map snapshot = active.snapshot instanceof Map ? (Map)active.snapshot : [:]
  Integer track = null
  try { track = snapshot.track?.toString()?.toInteger() } catch(Exception ignored) { track = null }
  Long positionMillis = null
  try { positionMillis = snapshot.positionMillis?.toString()?.toLong() } catch(Exception ignored) { positionMillis = null }
  if(track != null && track > 0) {
    sendAnnouncementRestoreSeek(correlationId, 'TRACK_NR', track.toString(), 'track')
  } else if(positionMillis != null && positionMillis > 0L) {
    sendAnnouncementRestoreSeek(correlationId, 'REL_TIME', formatSonosTime(positionMillis), 'position')
  } else {
    restoreAnnouncementPlayMode(correlationId)
  }
}

void sendAnnouncementRestoreSeek(String correlationId, String unit, String target, String stage) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(!ip) { finishAnnouncementPlaybackRestore(correlationId, false, 'coordinator-unavailable'); return }
  Map params = getSoapActionParams(ip, AVTransport, 'Seek', [Unit: unit, Target: target])
  postSonosSoapAsync('announcementPlaybackSeekRestored', params,
      [correlationId: correlationId, stage: stage])
}

void announcementPlaybackSeekRestored(AsyncResponse response, Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  if(response?.status != 200 || response.hasError()) {
    logWarn("Could not restore ${data?.stage} for announcement ${correlationId}: status=${response?.status}")
    restoreAnnouncementPlayMode(correlationId)
    return
  }
  Map snapshot = active.snapshot instanceof Map ? (Map)active.snapshot : [:]
  if(data?.stage == 'track') {
    Long positionMillis = null
    try { positionMillis = snapshot.positionMillis?.toString()?.toLong() } catch(Exception ignored) { positionMillis = null }
    if(positionMillis != null && positionMillis > 0L) {
      sendAnnouncementRestoreSeek(correlationId, 'REL_TIME', formatSonosTime(positionMillis), 'position')
      return
    }
  }
  restoreAnnouncementPlayMode(correlationId)
}

void restoreAnnouncementPlayMode(String correlationId) {
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  Map snapshot = active?.snapshot instanceof Map ? (Map)active.snapshot : [:]
  String playMode = snapshot.playMode?.toString()
  if(!playMode) { finishAnnouncementPlaybackRestore(correlationId, true, null); return }
  String ip = getLocalUpnpHostForGroupOperation()
  if(!ip) { finishAnnouncementPlaybackRestore(correlationId, false, 'coordinator-unavailable'); return }
  Map params = getSoapActionParams(ip, AVTransport, 'SetPlayMode', [NewPlayMode: playMode])
  postSonosSoapAsync('announcementPlaybackPlayModeRestored', params, [correlationId: correlationId])
}

void announcementPlaybackPlayModeRestored(AsyncResponse response, Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  Boolean success = response?.status == 200 && response.hasError() == false
  if(!success) { logWarn("Could not restore play mode for announcement ${correlationId}: status=${response?.status}") }
  finishAnnouncementPlaybackRestore(correlationId, true, null)
}

String formatSonosTime(Long positionMillis) {
  Long seconds = Math.max(0L, positionMillis.intdiv(1000L))
  return String.format('%02d:%02d:%02d', seconds.intdiv(3600L), seconds.intdiv(60L) % 60L, seconds % 60L)
}

void finishAnnouncementPlaybackRestore(String correlationId, Boolean success, String reason) {
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(active?.correlationId?.toString() != correlationId) { return }
  Map snapshot = active.snapshot instanceof Map ? (Map)active.snapshot : [:]
  if(success && snapshot.transportState?.toString() in ['PLAYING', 'TRANSITIONING', 'PAUSED_PLAYBACK']) {
    String ip = getLocalUpnpHostForGroupOperation()
    if(ip) {
      String action = snapshot.transportState?.toString() == 'PAUSED_PLAYBACK' ? 'Pause' : 'Play'
      Map params = getSoapActionParams(ip, AVTransport, action)
      postSonosSoapAsync('announcementPlaybackTransportRestored', params, [correlationId: correlationId])
      return
    }
    success = false
    reason = 'coordinator-unavailable'
  }
  completeAnnouncementPlaybackRestore(correlationId, success, reason)
}

void announcementPlaybackTransportRestored(AsyncResponse response, Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  Boolean success = response?.status == 200 && response.hasError() == false
  completeAnnouncementPlaybackRestore(correlationId, success,
      success ? null : "transport-resume-failed-${response?.status}")
}

void completeAnnouncementPlaybackRestore(String correlationId, Boolean success, String reason) {
  Map active = runtimeState.announcementPlaybackRestore instanceof Map
      ? (Map)runtimeState.announcementPlaybackRestore : null
  if(active?.correlationId?.toString() != correlationId) { return }
  runtimeState.remove('announcementPlaybackRestore')
  runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
  runtimeState.remove('groupAnnouncementStream')
  if(!success) { logWarn("Playback restore for ${correlationId} was incomplete: ${reason}") }
  publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', [restored: success, reason: reason])
}

void completeGroupAnnouncementPlaybackHandoff(String correlationId, Boolean restored = true) {
  Map announcement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  if(announcement?.correlationId?.toString() != correlationId) { return }
  runtimeState.remove('announcementPlaybackRestore')
  runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
  runtimeState.remove('groupAnnouncementStream')
  if(restored != true) { logWarn("Stream handoff for ${correlationId} completed without verifying the original audio source") }
  publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', [restored: restored == true, handoff: true])
}

@CompileStatic
void devicePlayTrack(String uri, BigDecimal volume = null) {
  volume = normalizePublicPlaybackVolume(volume)
  componentLoadStreamUrlLocal(uri, volume)
}

@CompileStatic
void mute(){ playerSetPlayerMute(true) }
@CompileStatic
void unmute(){ playerSetPlayerMute(false) }
void setLevel(BigDecimal level, BigDecimal duration = null) {
  Integer targetVolume = Math.max(0, Math.min(100, level as Integer))
  if(duration == null || duration <= 0) {
    cancelVolumeFade()
    dispatchPlayerVolume(targetVolume)
    return
  }
  // Rapid-call detection: if an external app (e.g. webCoRE) is managing the fade
  // by calling setLevel(level, duration) in a loop, skip our internal fade and set volume directly
  String deviceId = getId()
  if(!deviceId) {
    dispatchPlayerVolume(targetVolume)
    return
  }
  Long now = now()
  Long lastCall = lastVolumeFadeCallTime.put(deviceId, now)
  if(lastCall != null && (now - lastCall) < 2000) {
    logDebug("Rapid setLevel calls detected (${now - lastCall}ms apart) — setting volume directly to ${targetVolume}")
    cancelVolumeFade()
    dispatchPlayerVolume(targetVolume)
    return
  }
  Integer currentVolume = getPlayerVolume()
  Integer delta = Math.abs(targetVolume - currentVolume)
  if(delta == 0) {
    cancelVolumeFade()
    return
  }
  Integer durationSeconds = duration as Integer
  // If the duration is too short for the delta, just send a single command
  if(durationSeconds < 2 || delta <= 1) {
    cancelVolumeFade()
    dispatchPlayerVolume(targetVolume)
    return
  }
  // Calculate step interval: at least 1 second between commands
  Integer steps = Math.min(delta, durationSeconds)
  Integer intervalMs = (Integer)((durationSeconds * 1000) / steps)
  if(intervalMs < 1000) { intervalMs = 1000 }
  // Recalculate steps based on actual interval
  steps = (Integer)(durationSeconds * 1000 / intervalMs)
  if(steps < 1) { steps = 1 }
  BigDecimal volumeStep = (BigDecimal)(targetVolume - currentVolume) / steps
  // Cancel any existing fade and start new one
  cancelVolumeFade()
  Map fadeState = [
    targetVolume: targetVolume,
    startVolume: currentVolume,
    volumeStep: volumeStep,
    currentStep: 0,
    totalSteps: steps,
    intervalMs: intervalMs
  ]
  volumeFadeState.put(deviceId, fadeState)
  logInfo("Starting volume fade from ${currentVolume} to ${targetVolume} over ${durationSeconds}s (${steps} steps, ${intervalMs}ms interval)")
  runInMillis(intervalMs, 'volumeFadeStep', [overwrite: true])
}

void volumeFadeStep() {
  String deviceId = getId()
  if(!deviceId) { return }
  Map fadeState = volumeFadeState.get(deviceId)
  if(fadeState == null) { return }
  Integer currentStep = (fadeState.currentStep as Integer) + 1
  Integer totalSteps = fadeState.totalSteps as Integer
  Integer targetVolume = fadeState.targetVolume as Integer
  if(currentStep >= totalSteps) {
    // Final step: set exact target volume and clean up
    dispatchPlayerVolume(targetVolume)
    volumeFadeState.remove(deviceId)
    lastVolumeFadeCallTime.remove(deviceId)
    logInfo("Volume fade complete: volume set to ${targetVolume}")
    return
  }
  // Calculate intermediate volume
  Integer startVolume = fadeState.startVolume as Integer
  BigDecimal volumeStep = fadeState.volumeStep as BigDecimal
  Integer newVolume = Math.round(startVolume + (volumeStep * currentStep)) as Integer
  newVolume = Math.max(0, Math.min(100, newVolume))
  dispatchPlayerVolume(newVolume)
  // Update step counter and schedule next
  fadeState.currentStep = currentStep
  volumeFadeState.put(deviceId, fadeState)
  Integer intervalMs = fadeState.intervalMs as Integer
  runInMillis(intervalMs, 'volumeFadeStep', [overwrite: true])
}

void cancelVolumeFade() {
  String deviceId = getId()
  if(deviceId) {
    volumeFadeState.remove(deviceId)
    pendingGroupedPlayerVolumes.remove(deviceId)
  }
  unschedule('volumeFadeStep')
  unschedule('flushPendingGroupedPlayerVolume')
}

/**
 * Send a player-volume command without flooding a currently grouped household.
 * A direct player-volume command remains the correct operation for the public
 * player device; only its timing changes when the player is in a multi-player
 * Sonos group. Group devices that use proportional volume still use the
 * dedicated groupVolume API through setGroupVolume().
 */
void dispatchPlayerVolume(Integer volume) {
  String playerId = getId()
  if(playerId == null || playerId == '') {
    playerSetPlayerVolume(volume)
    return
  }

  if(!getIsGrouped()) {
    pendingGroupedPlayerVolumes.remove(playerId)
    unschedule('flushPendingGroupedPlayerVolume')
    playerSetPlayerVolume(volume)
    return
  }

  pendingGroupedPlayerVolumes.put(playerId, volume)
  Integer memberIndex = getGroupPlayerIds().indexOf(playerId)
  Integer delayMs = memberIndex >= 0 ? (memberIndex + 1) * GROUPED_PLAYER_VOLUME_STAGGER_MS : GROUPED_PLAYER_VOLUME_STAGGER_MS
  logDebug("Queueing grouped player volume ${volume} for ${delayMs}ms to avoid a command burst")
  runInMillis(delayMs, 'flushPendingGroupedPlayerVolume', [overwrite: true])
}

void flushPendingGroupedPlayerVolume() {
  String playerId = getId()
  if(playerId == null || playerId == '') {
    return
  }
  Integer volume = pendingGroupedPlayerVolumes.remove(playerId)
  if(volume != null) {
    playerSetPlayerVolume(volume)
  }
}

@CompileStatic
Boolean isVolumeFadeInProgress() {
  String deviceId = getId()
  return deviceId ? volumeFadeState.containsKey(deviceId) : false
}

@CompileStatic
void setVolume(BigDecimal level) { setLevel(level) }
void setVolumeZero() { setLevel(0G) }
@CompileStatic
void setTreble(BigDecimal level) { componentSetTrebleLocal(level)}
@CompileStatic
void setBass(BigDecimal level) { componentSetBassLocal(level)}
@CompileStatic
void setLoudness(String mode) { componentSetLoudnessLocal(mode == 'on')}
@CompileStatic
void setBalance(BigDecimal level) { componentSetBalanceLocal(level)}
void setBalance(Integer level) { setBalance(level as BigDecimal) }
void setNightMode(String mode) {
  if(!hasHTPlaybackCapability()) {
    logWarn("Night Mode is only supported on Sonos soundbar speakers (Arc, Beam, Ray, Playbar, Playbase)")
    return
  }
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [EQType: 'NightMode', DesiredValue: mode == 'on' ? 1 : 0]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetEQ', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}
void setSpeechEnhancement(String mode) {
  if(!hasHTPlaybackCapability()) {
    logWarn("Speech Enhancement is only supported on Sonos soundbar speakers (Arc, Beam, Ray, Playbar, Playbase)")
    return
  }
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [EQType: 'DialogLevel', DesiredValue: mode == 'on' ? 1 : 0]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetEQ', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void selectLineIn(String sourceIdentifier = null) {
  if(deferCommandToParentDuringGroupOperation('selectLineIn', [sourceIdentifier])) { return }
  if(routeGroupedPlaybackCommandToCoordinator('selectLineIn', [sourceIdentifier])) { return }
  requestParentCoordinatorCommand('selectLineIn', [sourceIdentifier])
}

/** Select a Line-In source after the controller has resolved and validated its RINCON. */
void selectLineInRincon(String rinconId) {
  if(rinconId == null || !rinconId.startsWith('RINCON_')) {
    logWarn("Cannot select Line-In without a valid RINCON ID")
    return
  }
  String lineInURI = "x-rincon-stream:${rinconId}"
  logInfo("Switching to Line-In source: ${lineInURI}")
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [CurrentURI: lineInURI]
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  postSonosSoapAsync('localControlCallback', params)
  play()
}

void selectTV() {
  if(deferCommandToParentDuringGroupOperation('selectTV')) { return }
  if(routeGroupedPlaybackCommandToCoordinator('selectTV')) { return }
  if(!hasHTPlaybackCapability()) {
    logWarn("TV input is only supported on Sonos home theater speakers")
    return
  }
  String rinconId = getId()
  String tvURI = "x-sonos-htastream:${rinconId}:spdif"
  logInfo("Switching to TV source: ${tvURI}")
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [CurrentURI: tvURI]
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  postSonosSoapAsync('localControlCallback', params)
  play()
}

/**
 * Defer follower-to-coordinator forwarding through the parent app's event
 * queue. A direct parent call from a player method can deadlock when the app
 * is already waiting for a child method to return.
 */
void requestParentCoordinatorCommand(String command, List args = []) {
  if(!command) {
    return
  }
  Integer sequence = ((runtimeState.parentCommandSequence ?: 0) as Integer) + 1
  runtimeState.parentCommandSequence = sequence
  Map payload = [
    playerDni: device.getDeviceNetworkId(),
    requestId: UUID.randomUUID().toString(),
    sequence: sequence,
    createdAt: now(),
    command: command,
    args: args ?: []
  ]
  List<Map> pending = runtimeState.pendingParentCoordinatorCommands instanceof List
      ? (List<Map>)runtimeState.pendingParentCoordinatorCommands
      : []
  pending.add(payload)
  runtimeState.pendingParentCoordinatorCommands = pending
  logDebug("Queued parent command ${command} (${payload.requestId}); pending=${pending.size()}")
  runIn(1, 'drainParentCoordinatorCommandQueue', [overwrite: true])
}

/** Route group-wide playback changes from a follower to its current coordinator. */
Boolean routeGroupedPlaybackCommandToCoordinator(String command, List args = []) {
  if(!isGroupedAndNotCoordinator()) { return false }
  requestParentCoordinatorCommand(command, args)
  return true
}

/** Route media commands through the app while this player is registered with
 * an active topology-only operation, allowing deferral by the operation roster. */
Boolean deferCommandToParentDuringGroupOperation(String command, List args = []) {
  if(!runtimeState.groupTopologyOperationId &&
      (!runtimeState.groupFavoriteOperationId || runtimeState.groupFavoriteOperationFavoriteId)) { return false }
  requestParentCoordinatorCommand(command, args)
  return true
}

void drainParentCoordinatorCommandQueue() {
  if(runtimeState.activeParentCoordinatorCommand instanceof Map) { return }
  List<Map> pending = runtimeState.pendingParentCoordinatorCommands instanceof List
      ? (List<Map>)runtimeState.pendingParentCoordinatorCommands
      : []
  if(pending.isEmpty()) { return }
  Map payload = pending.remove(0)
  runtimeState.pendingParentCoordinatorCommands = pending
  runtimeState.activeParentCoordinatorCommand = payload
  emitParentCoordinatorCommand(payload)
}

void emitParentCoordinatorCommand(Map data) {
  Map payload = data?.payload instanceof Map ? (Map)data.payload : data
  if(payload?.command) {
    sendDeviceEvent(PLAYER_COMMAND_REQUEST_ATTRIBUTE, hideCurrentStateValue(JsonOutput.toJson(payload)))
    logDebug("Delivered parent command ${payload.command} (${payload.requestId}); waiting for controller acknowledgement")
    runIn(5, 'retryParentCoordinatorCommand', [overwrite: true])
  }
}

void retryParentCoordinatorCommand() {
  Map payload = runtimeState.activeParentCoordinatorCommand instanceof Map ? (Map)runtimeState.activeParentCoordinatorCommand : null
  if(payload) {
    logWarn("Retrying unacknowledged parent command ${payload.command} (${payload.requestId})")
    emitParentCoordinatorCommand(payload)
  }
}

void acknowledgeParentCommand(String requestId) {
  Map active = runtimeState.activeParentCoordinatorCommand instanceof Map ? (Map)runtimeState.activeParentCoordinatorCommand : null
  if(!active || !requestId || requestId != active.requestId) { return }
  unschedule('retryParentCoordinatorCommand')
  runtimeState.remove('activeParentCoordinatorCommand')
  logDebug("Controller acknowledged parent command ${active.command} (${requestId})")
  runIn(1, 'drainParentCoordinatorCommandQueue', [overwrite: true])
}

void muteGroup(){
  if(isGroupedAndCoordinator()) {
    playerSetGroupMute(true)
  } else if(isGroupedAndNotCoordinator()) {
    requestParentCoordinatorCommand('muteGroup')
  }
  else { playerSetPlayerMute(true) }
}
void unmuteGroup(){
  if(isGroupedAndCoordinator()) {
    playerSetGroupMute(false)
  } else if(isGroupedAndNotCoordinator()) {
    requestParentCoordinatorCommand('unmuteGroup')
  }
  else { playerSetPlayerMute(false) }
}
void setGroupVolume(BigDecimal level, BigDecimal duration = null) {
  Boolean isCoord = isGroupedAndCoordinator()
  Boolean isFollower = isGroupedAndNotCoordinator()
  logDebug("setGroupVolume(${level}) - isGroupedAndCoordinator: ${isCoord}, isGroupedAndNotCoordinator: ${isFollower}, isGrouped: ${this.device.currentValue('isGrouped', true)}, isGroupCoordinator: ${getIsGroupCoordinator()}")
  if(isFollower) {
    logDebug("setGroupVolume: Delegating to coordinator")
    requestParentCoordinatorCommand('setGroupVolume', [level, duration])
    return
  }
  String deviceId = getId()
  Integer targetVolume = Math.max(0, Math.min(100, level as Integer))
  Boolean useGroupApi = isCoord
  if(duration == null || duration <= 0) {
    cancelGroupVolumeFade()
    if(useGroupApi) {
      logDebug("setGroupVolume: Using playerSetGroupVolume (Sonos group API)")
      playerSetGroupVolume(targetVolume)
    } else {
      logDebug("setGroupVolume: Falling back to playerSetPlayerVolume (individual)")
      playerSetPlayerVolume(targetVolume)
    }
    return
  }
  if(!deviceId) {
    if(useGroupApi) { playerSetGroupVolume(targetVolume) }
    else { playerSetPlayerVolume(targetVolume) }
    return
  }
  // Rapid-call detection: if an external app is managing the fade by calling setGroupVolume in a loop
  Long now = now()
  Long lastCall = lastGroupVolumeFadeCallTime.put(deviceId, now)
  if(lastCall != null && (now - lastCall) < 2000) {
    logDebug("Rapid setGroupVolume calls detected (${now - lastCall}ms apart) — setting group volume directly to ${targetVolume}")
    cancelGroupVolumeFade()
    if(useGroupApi) { playerSetGroupVolume(targetVolume) }
    else { playerSetPlayerVolume(targetVolume) }
    return
  }
  Integer currentVolume = useGroupApi ? (this.device.currentValue('groupVolume', true) as Integer ?: 0) : getPlayerVolume()
  Integer delta = Math.abs(targetVolume - currentVolume)
  if(delta == 0) {
    cancelGroupVolumeFade()
    return
  }
  Integer durationSeconds = duration as Integer
  if(durationSeconds < 2 || delta <= 1) {
    cancelGroupVolumeFade()
    if(useGroupApi) { playerSetGroupVolume(targetVolume) }
    else { playerSetPlayerVolume(targetVolume) }
    return
  }
  Integer steps = Math.min(delta, durationSeconds)
  Integer intervalMs = (Integer)((durationSeconds * 1000) / steps)
  if(intervalMs < 1000) { intervalMs = 1000 }
  steps = (Integer)(durationSeconds * 1000 / intervalMs)
  if(steps < 1) { steps = 1 }
  BigDecimal volumeStep = (BigDecimal)(targetVolume - currentVolume) / steps
  cancelGroupVolumeFade()
  Map fadeState = [
    targetVolume: targetVolume,
    startVolume: currentVolume,
    volumeStep: volumeStep,
    currentStep: 0,
    totalSteps: steps,
    intervalMs: intervalMs,
    useGroupApi: useGroupApi
  ]
  groupVolumeFadeState.put(deviceId, fadeState)
  logInfo("Starting group volume fade from ${currentVolume} to ${targetVolume} over ${durationSeconds}s (${steps} steps, ${intervalMs}ms interval)")
  runInMillis(intervalMs, 'groupVolumeFadeStep', [overwrite: true])
}

void groupVolumeFadeStep() {
  String deviceId = getId()
  if(!deviceId) { return }
  Map fadeState = groupVolumeFadeState.get(deviceId)
  if(fadeState == null) { return }
  Integer currentStep = (fadeState.currentStep as Integer) + 1
  Integer totalSteps = fadeState.totalSteps as Integer
  Integer targetVolume = fadeState.targetVolume as Integer
  Boolean useGroupApi = fadeState.useGroupApi as Boolean
  if(currentStep >= totalSteps) {
    if(useGroupApi) { playerSetGroupVolume(targetVolume) }
    else { playerSetPlayerVolume(targetVolume) }
    groupVolumeFadeState.remove(deviceId)
    lastGroupVolumeFadeCallTime.remove(deviceId)
    logInfo("Group volume fade complete: volume set to ${targetVolume}")
    return
  }
  Integer startVolume = fadeState.startVolume as Integer
  BigDecimal volumeStep = fadeState.volumeStep as BigDecimal
  Integer newVolume = Math.round(startVolume + (volumeStep * currentStep)) as Integer
  newVolume = Math.max(0, Math.min(100, newVolume))
  if(useGroupApi) { playerSetGroupVolume(newVolume) }
  else { playerSetPlayerVolume(newVolume) }
  fadeState.currentStep = currentStep
  groupVolumeFadeState.put(deviceId, fadeState)
  Integer intervalMs = fadeState.intervalMs as Integer
  runInMillis(intervalMs, 'groupVolumeFadeStep', [overwrite: true])
}

void cancelGroupVolumeFade() {
  String deviceId = getId()
  if(deviceId) { groupVolumeFadeState.remove(deviceId) }
  unschedule('groupVolumeFadeStep')
}

@CompileStatic
Boolean isGroupVolumeFadeInProgress() {
  String deviceId = getId()
  return deviceId ? groupVolumeFadeState.containsKey(deviceId) : false
}

@CompileStatic
void setGroupLevel(BigDecimal level, BigDecimal duration = null) { setGroupVolume(level, duration) }
@CompileStatic
void setGroupMute(String mode) {
  logDebug("Setting group mute to ${mode}")
  if(mode == 'muted') { muteGroup() }
  else { unmuteGroup() }
}
void groupVolumeUp() {
  if(isGroupedAndCoordinator()) {
    playerSetGroupRelativeVolume(getGroupVolumeAdjAmount())
  } else if(isGroupedAndNotCoordinator()) {
    requestParentCoordinatorCommand('groupVolumeUp')
  }
  else { playerSetPlayerRelativeVolume(getPlayerVolumeAdjAmount()) }
}
void groupVolumeDown() {
  if(isGroupedAndCoordinator()) {
    playerSetGroupRelativeVolume(-getGroupVolumeAdjAmount())
  } else if(isGroupedAndNotCoordinator()) {
    requestParentCoordinatorCommand('groupVolumeDown')
  }
  else { playerSetPlayerRelativeVolume(-getPlayerVolumeAdjAmount()) }
}

@CompileStatic
void volumeUp() { playerSetPlayerRelativeVolume(getPlayerVolumeAdjAmount()) }
@CompileStatic
void volumeDown() { playerSetPlayerRelativeVolume(-getPlayerVolumeAdjAmount()) }

@CompileStatic
Integer getPlayerVolumeAdjAmount() {
  Integer currentVolume = (this.device.currentValue('volume', true) as Integer) ?: 0
  if(currentVolume < 11) {
    return getVolumeAdjustAmountLow()
  } else if(currentVolume >= 11 && currentVolume < 21) {
    return getVolumeAdjustAmountMid()
  } else {
    return getVolumeAdjustAmount()
  }
}

@CompileStatic
Integer getGroupVolumeAdjAmount() {
  Integer currentVolume = (this.device.currentValue('groupVolume', true) as Integer) ?: 0
  if(currentVolume < 11) {
    return getVolumeAdjustAmountLow()
  } else if(currentVolume >= 11 && currentVolume < 21) {
    return getVolumeAdjustAmountMid()
  } else {
    return getVolumeAdjustAmount()
  }
}

@CompileStatic
void play() {
  if(deferCommandToParentDuringGroupOperation('play')) { return }
  if(routeGroupedPlaybackCommandToCoordinator('play')) { return }
  playerPlay()
}
@CompileStatic
void stop() {
  if(deferCommandToParentDuringGroupOperation('stop')) { return }
  if(routeGroupedPlaybackCommandToCoordinator('stop')) { return }
  clearFavoriteRetryState()
  clearPlaylistRetryState()
  cancelPendingAmazonMusicAutoPlay()
  playerStop()
}

void stopAnnouncement() {
  stopAudioClipAnnouncement(false)
}

void stopHighPriorityAnnouncement() {
  stopAudioClipAnnouncement(true)
}

void stopAudioClipAnnouncement(Boolean highPriority) {
  Map activeClip = (Map)getAtomicStateValue('activeAudioClipMessage')
  if(runtimeState.audioClipPlaying != true || activeClip == null) {
    logDebug("No active ${highPriority ? 'high-priority ' : ''}announcement to stop")
    return
  }

  Boolean activeClipIsHighPriority = activeClip.priority?.toString() == 'HIGH'
  if(activeClipIsHighPriority != highPriority) {
    logDebug("Active announcement priority does not match requested stop (${highPriority ? 'HIGH' : 'regular'})")
    return
  }

  String sonosClipId = runtimeState.activeAudioClipSonosId?.toString()
  String correlationId = activeClip.correlationId?.toString()
  if(!sonosClipId) {
    logWarn("Cannot stop announcement ${correlationId ?: 'unknown'} because Sonos has not reported its audioClip ID yet")
    return
  }

  Map<String, String> command = [
    'namespace': 'audioClip:1',
    'command': 'cancelAudioClip',
    'playerId': getId()
  ]
  Map<String, String> args = ['id': sonosClipId]
  String message = JsonOutput.toJson([command, args])
  sendWsMessage(message, [kind: 'audioClipCancel', correlationId: correlationId])
  logInfo("Requested cancelAudioClip for ${highPriority ? 'high-priority ' : ''}announcement ${correlationId ?: sonosClipId}")
}

@CompileStatic
void pause() {
  if(deferCommandToParentDuringGroupOperation('pause')) { return }
  if(routeGroupedPlaybackCommandToCoordinator('pause')) { return }
  clearFavoriteRetryState()
  clearPlaylistRetryState()
  cancelPendingAmazonMusicAutoPlay()
  playerPause()
}
@CompileStatic
void nextTrack() {
  if(deferCommandToParentDuringGroupOperation('nextTrack')) { return }
  if(routeGroupedPlaybackCommandToCoordinator('nextTrack')) { return }
  playerSkipToNextTrack()
}
@CompileStatic
void previousTrack() {
  if(deferCommandToParentDuringGroupOperation('previousTrack')) { return }
  if(routeGroupedPlaybackCommandToCoordinator('previousTrack')) { return }
  playerSkipToPreviousTrack()
}
@CompileStatic
void subscribeToEvents() {
  if(isAppZgtSubscriber()) {
    subscribeToZgtEvents()
  } else {
    unsubscribeFromZgtEvents()
  }
  if(getIsGrouped()) { subscribeToMrGrcEvents() } // sid4 only needed when grouped; setGroupPlayerIds() handles transitions
  subscribeToMrRcEvents()
  markAppMediatedUpnpSubscriptionsStarted()
}

Boolean isAppZgtSubscriber() {
  return device.getDataValue('appZgtSubscribe') == 'true' &&
      device.getDataValue('appZgtLeaderGeneration')?.toString()
}

void requestAppZgtResubscription() {
  if(!isAppZgtSubscriber()) { return }
  if(subValid('sid3')) { resubscribeToZgtEvents() }
  else { subscribeToZgtEvents() }
}

void markAppMediatedUpnpSubscriptionsStarted() {
  if(runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY] != null) {
    runtimeState[APP_INITIALIZATION_UPNP_STARTED_STATE_KEY] = true
  }
}

@CompileStatic
void loadFavorite(String favoriteId = null) {
  if(favoriteId == null || favoriteId.trim() == '') {
    String randomFavoriteId = selectRandomAvailableId(getFavoriteIds())
    if(randomFavoriteId == null) {
      queueRandomMediaLoad('favorite')
      getFavorites()
      return
    }
    favoriteId = randomFavoriteId
  }
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode) {
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode) {
  String autoPlay = 'true'
  String crossfadeMode = 'on'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay) {
  String crossfadeMode = 'on'
  loadFavoriteFull(favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadFavoriteFull(String favoriteId, String repeatMode, String queueMode, String shuffleMode, String autoPlay, String crossfadeMode) {
  if(deferCommandToParentDuringGroupOperation('loadFavoriteFull',
      [favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode])) { return }
  String action = queueMode?.toUpperCase() ?: 'REPLACE'
  Boolean playOnCompletion = autoPlay == 'true'
  Boolean repeat = repeatMode == 'unchanged' ? null : repeatMode == 'repeat all'
  Boolean repeatOne = repeatMode == 'unchanged' ? null : repeatMode == 'repeat one'
  Boolean shuffle = shuffleMode == 'unchanged' ? null : shuffleMode == 'on'
  Boolean crossfade = crossfadeMode == 'on'
  if(getIsGroupCoordinator() == true) {
    // A new media request supersedes all pending confirmation/retry work.
    clearFavoriteRetryState()
    clearPlaylistRetryState()
    cancelPendingAmazonMusicAutoPlay()

    if(playOnCompletion) {
      String deviceId = device.getDeviceNetworkId()
      Boolean wasPlayingAtStart = isPlaybackCurrentlyActive(deviceId)
      String operationId = createMediaLoadOperationId('favorite', favoriteId)
      lastPlaybackState.remove(deviceId)
      lastMetadataContainerId.remove(deviceId)
      Map favorite = findFavoriteById(favoriteId)
      Boolean isAmazon = isAmazonMusicService(favorite?.service as String)
      favoriteRetryState.put(deviceId, [
        operationId: operationId,
        favoriteId: favoriteId,
        action: action,
        repeat: repeat,
        repeatOne: repeatOne,
        shuffle: shuffle,
        crossfade: crossfade,
        playOnCompletion: playOnCompletion,
        attemptNumber: 0,
        wasPlayingAtStart: wasPlayingAtStart,
        playbackObserved: false,
        metadataConfirmed: false,
        loadAcknowledged: false,
        ambiguousConfirmationPasses: 0,
        isAmazon: isAmazon
      ])
    }

    playerLoadFavorite(favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)

    if(playOnCompletion) {
      Map retryState = favoriteRetryState.get(device.getDeviceNetworkId())
      Integer initialDelay = retryState?.isAmazon == true ? AMAZON_FAVORITE_INITIAL_CHECK_DELAY_SECONDS : FAVORITE_INITIAL_CHECK_DELAY_SECONDS
      scheduleFavoriteRetryCallback(initialDelay, retryState?.operationId as String)
    }
  } else if(isGroupedAndNotCoordinator() == true) {
    requestParentCoordinatorCommand('loadFavoriteFull', [favoriteId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode])
  }
}

/**
 * Load a Favorite as part of the parent app's serialized group operation.
 * Individual-player Favorite commands retain the existing retry mechanism;
 * this path deliberately leaves retry ownership with the parent so grouping
 * and playback cannot race or retry independently.
 */
void loadFavoriteForGroupOperation(String favoriteId, String repeatMode, String queueMode,
    String shuffleMode, String autoPlay, String crossfadeMode, String operationId,
    String expectedGroupId, String attemptId = null, String requestId = null) {
  if(!operationId || !expectedGroupId) {
    logWarn('Cannot load group Favorite without an operation ID and verified group ID')
    return
  }
  runtimeState.groupFavoriteOperationId = operationId
  runtimeState.groupFavoriteOperationFavoriteId = favoriteId
  runtimeState.groupFavoriteOperationAttemptId = attemptId ?: operationId
  runtimeState.groupFavoriteOperationRequestId = requestId ?: attemptId ?: operationId
  runtimeState.groupFavoriteOperationGroupId = expectedGroupId
  runtimeState.groupFavoriteOperationLoadAt = now()
  runtimeState.remove('groupTopologyOperationId')
  runtimeState.remove('groupTopologyOperationRequestId')
  if(getIsGroupCoordinator() != true) {
    logWarn("Cannot load group Favorite operation ${operationId}: player is not the group coordinator")
    emitGroupFavoriteOperationEvent('loadRejected', [reason: 'PLAYER_NOT_COORDINATOR'])
    return
  }

  clearFavoriteRetryState()
  clearPlaylistRetryState()
  cancelPendingAmazonMusicAutoPlay()

  String action = queueMode.toUpperCase()
  Boolean playOnCompletion = autoPlay == 'true'
  Boolean repeat = repeatMode == 'unchanged' ? null : repeatMode == 'repeat all'
  Boolean repeatOne = repeatMode == 'unchanged' ? null : repeatMode == 'repeat one'
  Boolean shuffle = shuffleMode == 'unchanged' ? null : shuffleMode == 'on'
  Boolean crossfade = crossfadeMode == 'on'
  emitGroupFavoriteOperationEvent('loadStarted', [favoriteId: favoriteId, groupId: expectedGroupId,
    requestId: runtimeState.groupFavoriteOperationRequestId])
  playerLoadFavoriteForGroupOperation(
    favoriteId,
    action,
    repeat,
    repeatOne,
    shuffle,
    crossfade,
    playOnCompletion,
    expectedGroupId
  )
}

/**
 * Register a group Favorite operation on a target player before topology or
 * playback events are observed. The parent app uses this to receive events
 * from every required player, not only the coordinator.
 */
void registerGroupFavoriteOperation(String operationId, String favoriteId, String requestId = null) {
  if(!operationId) { return }
  runtimeState.remove('groupTopologyOperationId')
  runtimeState.remove('groupTopologyOperationRequestId')
  runtimeState.groupFavoriteOperationId = operationId
  if(favoriteId) {
    runtimeState.groupFavoriteOperationFavoriteId = favoriteId
  } else {
    runtimeState.remove('groupFavoriteOperationFavoriteId')
  }
  runtimeState.groupFavoriteOperationRequestId = requestId ?: operationId
  runtimeState.remove('groupFavoriteOperationGroupId')
  runtimeState.remove('groupFavoriteOperationAttemptId')
  runtimeState.remove('groupFavoriteOperationLoadAt')
  emitGroupFavoriteOperationEvent('registered', [favoriteId: favoriteId,
    requestId: runtimeState.groupFavoriteOperationRequestId])
}

/** Register a topology-only group operation without creating Favorite state. */
void registerGroupTopologyOperation(String operationId, String requestId = null) {
  if(!operationId) { return }
  runtimeState.remove('groupFavoriteOperationId')
  runtimeState.remove('groupFavoriteOperationFavoriteId')
  runtimeState.remove('groupFavoriteOperationAttemptId')
  runtimeState.remove('groupFavoriteOperationRequestId')
  runtimeState.remove('groupFavoriteOperationGroupId')
  runtimeState.remove('groupFavoriteOperationLoadAt')
  runtimeState.groupTopologyOperationId = operationId
  runtimeState.groupTopologyOperationRequestId = requestId ?: operationId
  emitGroupFavoriteOperationEvent('registered', [requestId: runtimeState.groupTopologyOperationRequestId])
}

void setGroupFavoriteOperationAttempt(String operationId, String attemptId, String expectedGroupId,
    String requestId = null) {
  String currentOperationId = runtimeState.groupFavoriteOperationId as String
  if(!currentOperationId || currentOperationId != operationId || !attemptId) { return }
  runtimeState.groupFavoriteOperationAttemptId = attemptId
  runtimeState.groupFavoriteOperationRequestId = requestId ?: attemptId
  runtimeState.groupFavoriteOperationGroupId = expectedGroupId
  runtimeState.groupFavoriteOperationLoadAt = now()
}

/** Issue a play command with a local request ID tied to the active group operation. */
void playerPlayForGroupOperation(String operationId, String attemptId, String requestId) {
  if(!operationId || !attemptId || !requestId || runtimeState.groupFavoriteOperationId?.toString() != operationId ||
      runtimeState.groupFavoriteOperationAttemptId?.toString() != attemptId) {
    logWarn('Ignoring group operation play command with stale or incomplete correlation IDs')
    return
  }
  runtimeState.groupFavoriteOperationRequestId = requestId
  emitGroupFavoriteOperationEvent('playStarted', [requestId: requestId])
  playerPlay()
}

void clearGroupOperation(String operationId = null) {
  String currentOperationId = (runtimeState.groupFavoriteOperationId ?:
      runtimeState.groupTopologyOperationId) as String
  if(!currentOperationId || (operationId && currentOperationId != operationId)) { return }
  runtimeState.remove('groupFavoriteOperationId')
  runtimeState.remove('groupFavoriteOperationFavoriteId')
  runtimeState.remove('groupFavoriteOperationGroupId')
  runtimeState.remove('groupFavoriteOperationAttemptId')
  runtimeState.remove('groupFavoriteOperationRequestId')
  runtimeState.remove('groupFavoriteOperationLoadAt')
  runtimeState.remove('groupTopologyOperationId')
  runtimeState.remove('groupTopologyOperationRequestId')
  clearGroupFavoriteOperationCurrentState()
}

/** Backward-compatible alias for existing parent app deployments. */
void clearGroupFavoriteOperation(String operationId = null) {
  clearGroupOperation(operationId)
}

void emitGroupFavoriteOperationEvent(String eventName, Map data = [:]) {
  String topologyOperationId = runtimeState.groupTopologyOperationId as String
  String operationId = topologyOperationId ?: runtimeState.groupFavoriteOperationId as String
  if(!operationId || !eventName) { return }
  Map payload = [
    operationId: operationId,
    requestId: data?.requestId?.toString() ?: (topologyOperationId
        ? runtimeState.groupTopologyOperationRequestId as String
        : runtimeState.groupFavoriteOperationRequestId as String),
    event: eventName,
    playerId: getId(),
    attemptId: topologyOperationId ? null : runtimeState.groupFavoriteOperationAttemptId as String,
    observedAt: now(),
    data: data ?: [:]
  ]
  sendDeviceEvent(GROUP_FAVORITE_OPERATION_ATTRIBUTE, hideCurrentStateValue(JsonOutput.toJson(payload)))
}

String hideCurrentStateValue(String value) {
  return "<span style=\"display:none\">${value ?: ''}</span>"
}

void clearGroupFavoriteOperationCurrentState() {
  try {
    device.deleteCurrentState(GROUP_FAVORITE_OPERATION_ATTRIBUTE)
  } catch(Exception e) {
    logDebug("Could not remove transient group Favorite operation state: ${e.message}")
  }
}

void clearPlayerCommandRequestCurrentState() {
  try {
    device.deleteCurrentState(PLAYER_COMMAND_REQUEST_ATTRIBUTE)
  } catch(Exception e) {
    logDebug("Could not remove transient player command request state: ${e.message}")
  }
}

// Playlist Methods
@CompileStatic
void loadPlaylist(String playlistId = null) {
  if(playlistId == null || playlistId.trim() == '') {
    String randomPlaylistId = selectRandomAvailableId(getPlaylistIds())
    if(randomPlaylistId == null) {
      queueRandomMediaLoad('playlist')
      getPlaylists()
      return
    }
    playlistId = randomPlaylistId
  }
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadPlaylistFull(String playlistId) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String repeatMode = 'repeat all'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadPlaylistFull(String playlistId, String repeatMode) {
  String queueMode = "REPLACE"
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadPlaylistFull(String playlistId, String repeatMode, String queueMode) {
  String autoPlay = 'true'
  String shuffleMode = 'false'
  String crossfadeMode = 'on'
  loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadPlaylistFull(String playlistId, String repeatMode, String queueMode, String shuffleMode) {
  String autoPlay = 'true'
  String crossfadeMode = 'on'
  loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

@CompileStatic
void loadPlaylistFull(String playlistId, String repeatMode, String queueMode, String shuffleMode, String autoPlay) {
  String crossfadeMode = 'on'
  loadPlaylistFull(playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode)
}

void loadPlaylistFull(String playlistId, String repeatMode, String queueMode, String shuffleMode, String autoPlay, String crossfadeMode) {
  if(deferCommandToParentDuringGroupOperation('loadPlaylistFull',
      [playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode])) { return }
  if(playlistId == null || playlistId.trim() == '') {
    reportPlaylistLoadError(playlistId, 'ERROR_MISSING_PARAMETERS', 'A Sonos playlist ID is required.')
    return
  }
  String action = normalizePlaylistQueueAction(queueMode)
  if(action == null) {
    reportPlaylistLoadError(playlistId, 'ERROR_INVALID_PARAMETER', "Unsupported playlist queue mode '${queueMode}'.")
    return
  }
  Boolean playOnCompletion = autoPlay == 'true'
  Boolean repeat = repeatMode == 'unchanged' ? null : repeatMode == 'repeat all'
  Boolean repeatOne = repeatMode == 'unchanged' ? null : repeatMode == 'repeat one'
  Boolean shuffle = shuffleMode == 'unchanged' ? null : shuffleMode == 'on'
  Boolean crossfade = crossfadeMode == 'on'
  if(getIsGroupCoordinator() == true) {
    // A new media request supersedes all pending confirmation/retry work.
    clearFavoriteRetryState()
    clearPlaylistRetryState()
    cancelPendingAmazonMusicAutoPlay()

    if(playOnCompletion) {
      String deviceId = device.getDeviceNetworkId()
      Boolean wasPlayingAtStart = isPlaybackCurrentlyActive(deviceId)
      String operationId = createMediaLoadOperationId('playlist', playlistId)
      lastPlaybackState.remove(deviceId)
      lastMetadataContainerId.remove(deviceId)
      playlistRetryState.put(deviceId, [
        operationId: operationId,
        playlistId: playlistId,
        action: action,
        repeat: repeat,
        repeatOne: repeatOne,
        shuffle: shuffle,
        crossfade: crossfade,
        playOnCompletion: playOnCompletion,
        attemptNumber: 0,
        wasPlayingAtStart: wasPlayingAtStart,
        playbackObserved: false,
        metadataConfirmed: false,
        loadAcknowledged: false,
        ambiguousConfirmationPasses: 0,
        bufferingObserved: false,
        playCommandAttempts: 0,
        loadErrorCode: null,
        loadErrorReason: null
      ])
    }

    playerLoadPlaylist(playlistId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)

    if(playOnCompletion) {
      Map retryState = playlistRetryState.get(device.getDeviceNetworkId())
      schedulePlaylistRetryCallback(PLAYLIST_INITIAL_CHECK_DELAY_SECONDS, retryState?.operationId as String)
    }
  } else if(isGroupedAndNotCoordinator() == true) {
    requestParentCoordinatorCommand('loadPlaylistFull', [playlistId, repeatMode, queueMode, shuffleMode, autoPlay, crossfadeMode])
  }
}

@CompileStatic
String normalizePlaylistQueueAction(String queueMode) {
  if(queueMode == null || queueMode.trim() == '') { return null }
  String action = queueMode.trim().toUpperCase()
  return PLAYLIST_QUEUE_ACTIONS.contains(action) ? action : null
}

// =============================================================================
// End Device Methods
// =============================================================================



// =============================================================================
// Favorite Retry Mechanism
// =============================================================================

void clearFavoriteRetryState() {
  String deviceId = device.getDeviceNetworkId()
  favoriteRetryState.remove(deviceId)
  unschedule(FAVORITE_RETRY_CALLBACK)
  unschedule(FAVORITE_RETRY_EVALUATION_CALLBACK)
}

void scheduleNextFavoriteRetryCheck() {
  String deviceId = device.getDeviceNetworkId()
  Map retryState = favoriteRetryState.get(deviceId)
  if(retryState == null) { return }
  Integer attemptNumber = retryState.attemptNumber as Integer
  Integer intervalIndex = Math.max(attemptNumber - 1, 0)
  Integer delaySeconds = FAVORITE_RETRY_INTERVALS[Math.min(intervalIndex, FAVORITE_RETRY_INTERVALS.size() - 1)]
  logDebug("Scheduling favorite playback confirmation in ${delaySeconds} seconds after retry ${attemptNumber}/${FAVORITE_RETRY_INTERVALS.size()}")
  scheduleFavoriteRetryCallback(delaySeconds, retryState.operationId as String)
}

void scheduleFavoriteRetryCallback(Integer delaySeconds, String operationId = null) {
  runIn(delaySeconds, FAVORITE_RETRY_CALLBACK, [overwrite: true, data: [operationId: operationId]])
}

void checkFavoritePlaybackAndRetry() {
  checkFavoritePlaybackAndRetry([:])
}

void checkFavoritePlaybackAndRetry(Map data) {
  String deviceId = device.getDeviceNetworkId()
  Map retryState = favoriteRetryState.get(deviceId)
  if(retryState == null || !isRetryOperationCurrent(retryState, data)) {
    logDebug("No retry state found, skipping playback check")
    return
  }

  retryState.playbackObserved = false
  getPlaybackStatus()
  getPlaybackMetadataStatus()
  runIn(PLAYBACK_STATUS_RESPONSE_GRACE_SECONDS, FAVORITE_RETRY_EVALUATION_CALLBACK,
    [overwrite: true, data: [operationId: retryState.operationId]])
}

void evaluateFavoritePlaybackAndRetry(Map data) {
  String deviceId = device.getDeviceNetworkId()
  Map retryState = favoriteRetryState.get(deviceId)
  if(retryState == null || !isRetryOperationCurrent(retryState, data)) { return }

  Boolean playbackActive = retryState.playbackObserved == true ||
    (retryState.wasPlayingAtStart != true && getTransportStatus() == 'playing')

  if(playbackActive && retryState.metadataConfirmed == true) {
    logInfo("Favorite '${retryState.favoriteId}' is now playing successfully")
    clearFavoriteRetryState()
    return
  }

  if(playbackActive && (retryState.loadAcknowledged == true || retryState.wasPlayingAtStart != true)) {
    logInfo("Playback is active after loading favorite '${retryState.favoriteId}'; stopping retries while metadata confirmation completes")
    clearFavoriteRetryState()
    return
  }

  if(playbackActive) {
    Integer confirmationPasses = retryState.ambiguousConfirmationPasses as Integer
    if(confirmationPasses < MAX_AMBIGUOUS_PLAYBACK_CONFIRMATION_PASSES) {
      retryState.ambiguousConfirmationPasses = confirmationPasses + 1
      logDebug("Playback is active but favorite '${retryState.favoriteId}' is not identified yet; polling again before retrying")
      scheduleFavoriteRetryCallback(PLAYBACK_STATUS_RESPONSE_GRACE_SECONDS, retryState.operationId as String)
      return
    }
    logWarn("Playback is active but favorite '${retryState.favoriteId}' could not be confirmed; stopping retries to avoid interrupting active playback")
    clearFavoriteRetryState()
    return
  }

  Integer attemptNumber = retryState.attemptNumber as Integer
  if(attemptNumber >= FAVORITE_RETRY_INTERVALS.size()) {
    logWarn("Failed to confirm favorite '${retryState.favoriteId}' after ${FAVORITE_RETRY_INTERVALS.size()} retry attempts. The favorite may not have loaded correctly.")
    clearFavoriteRetryState()
    return
  }

  attemptNumber++
  retryState.attemptNumber = attemptNumber
  logInfo("Favorite '${retryState.favoriteId}' not playing yet, retrying (attempt ${attemptNumber}/${FAVORITE_RETRY_INTERVALS.size()})")
  resetRetryConfirmationSignals(retryState)
  playerLoadFavorite(
    retryState.favoriteId as String,
    retryState.action as String,
    retryState.repeat as Boolean,
    retryState.repeatOne as Boolean,
    retryState.shuffle as Boolean,
    retryState.crossfade as Boolean,
    retryState.playOnCompletion as Boolean
  )
  scheduleNextFavoriteRetryCheck()
}

// =============================================================================
// End Favorite Retry Mechanism
// =============================================================================



// =============================================================================
// Playlist Retry Mechanism
// =============================================================================

void clearPlaylistRetryState() {
  String deviceId = device.getDeviceNetworkId()
  playlistRetryState.remove(deviceId)
  unschedule(PLAYLIST_RETRY_CALLBACK)
  unschedule(PLAYLIST_RETRY_EVALUATION_CALLBACK)
}

void scheduleNextPlaylistRetryCheck() {
  String deviceId = device.getDeviceNetworkId()
  Map retryState = playlistRetryState.get(deviceId)
  if(retryState == null) { return }
  Integer attemptNumber = retryState.attemptNumber as Integer
  Integer intervalIndex = Math.max(attemptNumber - 1, 0)
  Integer delaySeconds = PLAYLIST_RETRY_INTERVALS[Math.min(intervalIndex, PLAYLIST_RETRY_INTERVALS.size() - 1)]
  logDebug("Scheduling playlist playback confirmation in ${delaySeconds} seconds after retry ${attemptNumber}/${PLAYLIST_RETRY_INTERVALS.size()}")
  schedulePlaylistRetryCallback(delaySeconds, retryState.operationId as String)
}

void schedulePlaylistRetryCallback(Integer delaySeconds, String operationId = null) {
  runIn(delaySeconds, PLAYLIST_RETRY_CALLBACK, [overwrite: true, data: [operationId: operationId]])
}

void checkPlaylistPlaybackAndRetry() {
  checkPlaylistPlaybackAndRetry([:])
}

void checkPlaylistPlaybackAndRetry(Map data) {
  String deviceId = device.getDeviceNetworkId()
  Map retryState = playlistRetryState.get(deviceId)
  if(retryState == null || !isRetryOperationCurrent(retryState, data)) {
    logDebug("No retry state found, skipping playback check")
    return
  }

  retryState.playbackObserved = false
  retryState.bufferingObserved = false
  getPlaybackStatus()
  getPlaybackMetadataStatus()
  runIn(PLAYBACK_STATUS_RESPONSE_GRACE_SECONDS, PLAYLIST_RETRY_EVALUATION_CALLBACK,
    [overwrite: true, data: [operationId: retryState.operationId]])
}

void evaluatePlaylistPlaybackAndRetry(Map data) {
  String deviceId = device.getDeviceNetworkId()
  Map retryState = playlistRetryState.get(deviceId)
  if(retryState == null || !isRetryOperationCurrent(retryState, data)) { return }

  Boolean playbackActive = retryState.playbackObserved == true ||
    retryState.bufferingObserved == true ||
    (retryState.wasPlayingAtStart != true && getTransportStatus() == 'playing')

  String loadErrorCode = retryState.loadErrorCode as String
  if(loadErrorCode != null && !isRetryablePlaylistError(loadErrorCode)) {
    reportPlaylistLoadError(
      retryState.playlistId as String,
      loadErrorCode,
      retryState.loadErrorReason as String
    )
    clearPlaylistRetryState()
    return
  }

  if(playbackActive && retryState.metadataConfirmed == true) {
    logInfo("Playlist '${retryState.playlistId}' is now playing successfully")
    clearPlaylistRetryState()
    return
  }

  if(playbackActive && (retryState.loadAcknowledged == true || retryState.wasPlayingAtStart != true)) {
    logInfo("Playback is active after loading playlist '${retryState.playlistId}'; stopping retries while metadata confirmation completes")
    clearPlaylistRetryState()
    return
  }

  if(retryState.loadAcknowledged == true) {
    Integer playCommandAttempts = (retryState.playCommandAttempts ?: 0) as Integer
    if(playCommandAttempts < MAX_PLAYLIST_PLAY_COMMAND_ATTEMPTS) {
      retryState.playCommandAttempts = playCommandAttempts + 1
      logInfo("Playlist '${retryState.playlistId}' was accepted but playback is not active; issuing a play command without reloading it")
      playerPlay()
      schedulePlaylistRetryCallback(PLAYBACK_STATUS_RESPONSE_GRACE_SECONDS, retryState.operationId as String)
      return
    }

    String notPlayingReason = 'Sonos acknowledged the playlist load, but playback did not become active after the follow-up play command.'
    reportPlaylistLoadError(retryState.playlistId as String, 'PLAYBACK_NOT_STARTED', notPlayingReason)
    clearPlaylistRetryState()
    return
  }

  if(playbackActive) {
    Integer confirmationPasses = retryState.ambiguousConfirmationPasses as Integer
    if(confirmationPasses < MAX_AMBIGUOUS_PLAYBACK_CONFIRMATION_PASSES) {
      retryState.ambiguousConfirmationPasses = confirmationPasses + 1
      logDebug("Playback is active but playlist '${retryState.playlistId}' is not identified yet; polling again before retrying")
      schedulePlaylistRetryCallback(PLAYBACK_STATUS_RESPONSE_GRACE_SECONDS, retryState.operationId as String)
      return
    }
    logWarn("Playback is active but playlist '${retryState.playlistId}' could not be confirmed; stopping retries to avoid interrupting active playback")
    clearPlaylistRetryState()
    return
  }

  Integer attemptNumber = retryState.attemptNumber as Integer
  if(attemptNumber >= PLAYLIST_RETRY_INTERVALS.size()) {
    logWarn("Failed to confirm playlist '${retryState.playlistId}' after ${PLAYLIST_RETRY_INTERVALS.size()} retry attempts. The playlist may not have loaded correctly.")
    clearPlaylistRetryState()
    return
  }

  attemptNumber++
  retryState.attemptNumber = attemptNumber
  logInfo("Playlist '${retryState.playlistId}' not playing yet, retrying (attempt ${attemptNumber}/${PLAYLIST_RETRY_INTERVALS.size()})")
  resetRetryConfirmationSignals(retryState)
  playerLoadPlaylist(
    retryState.playlistId as String,
    retryState.action as String,
    retryState.repeat as Boolean,
    retryState.repeatOne as Boolean,
    retryState.shuffle as Boolean,
    retryState.crossfade as Boolean,
    retryState.playOnCompletion as Boolean
  )
  scheduleNextPlaylistRetryCheck()
}

String createMediaLoadOperationId(String mediaType, String mediaId) {
  return "${mediaType}:${device.getDeviceNetworkId()}:${mediaId}:${now()}"
}

Boolean isRetryOperationCurrent(Map retryState, Map callbackData) {
  if(retryState == null) { return false }
  String callbackOperationId = callbackData?.operationId as String
  return callbackOperationId == null || callbackOperationId == retryState.operationId?.toString()
}

Boolean isPlaybackCurrentlyActive(String deviceId) {
  return getTransportStatus() == 'playing' || lastPlaybackState.get(deviceId) == 'PLAYBACK_STATE_PLAYING'
}

void resetRetryConfirmationSignals(Map retryState) {
  retryState.playbackObserved = false
  retryState.bufferingObserved = false
  retryState.metadataConfirmed = false
  retryState.loadAcknowledged = false
  retryState.playCommandAttempts = 0
  retryState.loadErrorCode = null
  retryState.loadErrorReason = null
  retryState.ambiguousConfirmationPasses = 0
  lastPlaybackState.remove(device.getDeviceNetworkId())
  lastMetadataContainerId.remove(device.getDeviceNetworkId())
}

@CompileStatic
Boolean isRetryablePlaylistError(String errorCode) {
  if(errorCode == null || errorCode == '') { return true }
  return !NON_RETRYABLE_PLAYLIST_ERROR_CODES.contains(errorCode)
}

void reportPlaylistLoadError(String playlistId, String errorCode, String reason) {
  String safePlaylistId = playlistId ?: '(unknown)'
  String safeErrorCode = errorCode ?: 'UNKNOWN'
  String safeReason = reason ?: 'Sonos rejected the playlist load request.'
  String message = "Playlist '${safePlaylistId}' failed to load: ${safeErrorCode}"
  logError("${message}. ${safeReason}")
  recordLastError(message, safeReason)
}

void recordLastError(String message, String reason = null) {
  state.lastError = message
  state.lastErrorReason = reason
  state.lastErrorObservedAt = now()
}

// =============================================================================
// End Playlist Retry Mechanism
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
  if(dev.getDeviceNetworkId() == getPlaylistChildDNI()) {return 'Playlist'}
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
      sendChildEvent(getCrossfadeControlChild(), 'switch', getDeviceCurrentStateValue('currentCrossfadeMode'))
    break
    case 'Shuffle':
      sendChildEvent(getShuffleControlChild(), 'switch', getDeviceCurrentStateValue('currentShuffleMode'))
    break
    case 'RepeatOne':
      sendChildEvent(getRepeatOneControlChild(), 'switch', getDeviceCurrentStateValue('currentRepeatOneMode'))
    break
    case 'RepeatAll':
      sendChildEvent(getRepeatAllControlChild(), 'switch', getDeviceCurrentStateValue('currentRepeatAllMode'))
    break
    case 'Mute':
      String muteValue = getDeviceCurrentStateValue('mute') == 'muted' ? 'on' : 'off'
      sendChildEvent(getMuteControlChild(), 'switch', muteValue)
    break
    case 'NightMode':
      sendChildEvent(getNightModeChild(), 'switch', getDeviceCurrentStateValue('nightMode'))
    break
    case 'SpeechEnhancement':
      sendChildEvent(getSpeechEnhancementChild(), 'switch', getDeviceCurrentStateValue('speechEnhancement'))
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
    case 'Mute':
      mute()
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
    case 'Mute':
      unmute()
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
  queueLocalDeviceEvent('nextArtistName', nextArtistName ?: 'Not Available')
  queueLocalDeviceEvent('nextAlbumName', nextAlbumName ?: 'Not Available')
  queueLocalDeviceEvent('nextTrackName', nextTrackName ?: 'Not Available')
}

void updateChildBatteryStatus(Integer battery, String powerSource, BigDecimal temperature) {
  if(getCreateBatteryStatusChildDevice()) {
    ChildDeviceWrapper child = getBatteryStatusChild()
    Map attributes = [
      battery: [value: battery, unit: '%'],
      powerSource: [value: powerSource],
      temperature: [value: getTemperatureScale() == 'F' ? celsiusToFahrenheit(temperature) : temperature,
        unit: getTemperatureScale()]
    ]
    child.applyStateBatch(JsonOutput.toJson(attributes))
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
String getPlaylistChildDNI() { return "${device.getDeviceNetworkId()}-Playlist" }
String getQueueChildDNI() { return "${device.getDeviceNetworkId()}-Queue" }
String getSleepTimerChildDNI() { return "${device.getDeviceNetworkId()}-SleepTimer" }
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
ChildDeviceWrapper getPlaylistChild() { return getChildDevice(getPlaylistChildDNI()) }
ChildDeviceWrapper getQueueChild() { return getChildDevice(getQueueChildDNI()) }
ChildDeviceWrapper getSleepTimerChild() { return getChildDevice(getSleepTimerChildDNI()) }
ChildDeviceWrapper getRightChannelChild() { return getChildDevice(getRightChannelChildDNI()) }
ChildDeviceWrapper getNightModeChild() { return getChildDevice(getNightModeChildDNI()) }
ChildDeviceWrapper getSpeechEnhancementChild() { return getChildDevice(getSpeechEnhancementChildDNI()) }
// =============================================================================
// End Child Device Helpers
// =============================================================================

void invalidateComponentChildState(String dni) {
  if(!dni) { return }
  Map<String, Map<String, Object>> lastSent = state[LAST_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Map<String, Object>>((Map<String, Map<String, Object>>)state[LAST_COMPONENT_CHILD_EVENTS_KEY]) : [:]
  lastSent.remove(dni)
  if(lastSent.isEmpty()) { state.remove(LAST_COMPONENT_CHILD_EVENTS_KEY) }
  else { state[LAST_COMPONENT_CHILD_EVENTS_KEY] = lastSent }
  Map<String, Integer> retries = state[COMPONENT_CHILD_RETRY_ATTEMPTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Integer>((Map<String, Integer>)state[COMPONENT_CHILD_RETRY_ATTEMPTS_KEY]) : [:]
  retries.remove(dni)
  if(retries.isEmpty()) { state.remove(COMPONENT_CHILD_RETRY_ATTEMPTS_KEY) }
  else { state[COMPONENT_CHILD_RETRY_ATTEMPTS_KEY] = retries }
}

/** Drop queued events only when a child is intentionally removed or disabled. */
void discardComponentChildState(String dni) {
  if(!dni) { return }
  invalidateComponentChildState(dni)
  Map<String, Map<String, Object>> pending = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Map<String, Object>>((Map<String, Map<String, Object>>)state[PENDING_COMPONENT_CHILD_EVENTS_KEY]) : [:]
  pending.remove(dni)
  if(pending.isEmpty()) { state.remove(PENDING_COMPONENT_CHILD_EVENTS_KEY) }
  else { state[PENDING_COMPONENT_CHILD_EVENTS_KEY] = pending }
}



// =============================================================================
// Create Child Devices
// =============================================================================
void createRemoveCrossfadeChildDevice(Boolean create) {
  String dni = getCrossfadeControlChildDNI()
  ChildDeviceWrapper child = getCrossfadeControlChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating CrossfadeControl device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos Crossfade Control',
          label: "Sonos Crossfade Control - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'Crossfade')
      scheduleComponentChildEventDrainNow(dni)
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveCrossfadeChildDevice', e)
    }
  } else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
}

void createRemoveShuffleChildDevice(Boolean create) {
  String dni = getShuffleControlChildDNI()
  ChildDeviceWrapper child = getShuffleControlChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating Shuffle Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos Shuffle Control',
          label: "Sonos Shuffle Control - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'Shuffle')
      scheduleComponentChildEventDrainNow(dni)
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveShuffleChildDevice', e)
    }
  } else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
}

void createRemoveRepeatOneChildDevice(Boolean create) {
  String dni = getRepeatOneControlChildDNI()
  ChildDeviceWrapper child = getRepeatOneControlChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating RepeatOne Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos RepeatOne Control',
          label: "Sonos RepeatOne Control - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'RepeatOne')
      scheduleComponentChildEventDrainNow(dni)
      logDebug("ChildDNI = ${child.getDeviceNetworkId()}")
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveRepeatOneChildDevice', e)
    }
  } else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
}

void createRemoveRepeatAllChildDevice(Boolean create) {
  String dni = getRepeatAllControlChildDNI()
  ChildDeviceWrapper child = getRepeatAllControlChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating RepeatAll Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos RepeatAll Control',
          label: "Sonos RepeatAll Control - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'RepeatAll')
      scheduleComponentChildEventDrainNow(dni)
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveRepeatAllChildDevice', e)
    }
  } else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
}

void createRemoveMuteChildDevice(Boolean create) {
  String dni = getMuteControlChildDNI()
  ChildDeviceWrapper child = getMuteControlChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating Mute Control device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos Mute Control',
          label: "Sonos Mute Control - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'Mute')
      scheduleComponentChildEventDrainNow(dni)
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveMuteChildDevice', e)
    } catch (Exception e) {
      logException('createRemoveMuteChildDevice', e)
    }
  } else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
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
          label: "Sonos Battery Status - ${(device.getDataValue('name') ?: device.displayName)}"]
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
  if(device == null) { return false }

  String deviceIp = getDeviceDataValue('deviceIp')
  if(deviceIp == null || deviceIp == '') {
    logDebug('deviceHasBattery: deviceIp not set')
    return false
  }

  Boolean hasBattery = false
  try {
    Map params = [
      uri: "${getLocalUpnpUrl()}/status/batterystatus",
      timeout: 3
    ]
    httpGet(params) {resp ->
      if(resp.status == 200) {
        hasBattery = resp.data.children().find{it.name() == 'LocalBatteryStatus'} != null
      }
    }
  } catch(Exception e) {
    logDebug("deviceHasBattery: Could not check battery status: ${e.message}")
  }
  return hasBattery
}

void createRemoveFavoritesChildDevice(Boolean create) {
  String dni = getFavoritesChildDNI()
  ChildDeviceWrapper child = getFavoritesChild()
  if(!child && create) {
    try {
      logDebug("Creating Favorites device")
      child = addChildDevice('dwinks', 'Sonos Advanced Favorites', dni,
        [ name: 'Sonos Favorites',
          label: "Sonos Favorites - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveFavoritesChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemovePlaylistChildDevice(Boolean create) {
  String dni = getPlaylistChildDNI()
  ChildDeviceWrapper child = getPlaylistChild()
  if(!child && create) {
    try {
      logDebug("Creating Playlist device")
      child = addChildDevice('dwinks', 'Sonos Advanced Playlist', dni,
        [ name: 'Sonos Playlist',
          label: "Sonos Playlist - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
    } catch (UnknownDeviceTypeException e) {
      logException('createRemovePlaylistChildDevice', e)
    }
  } else if(!create && child){ deleteChildDevice(dni) }
}

void createRemoveQueueChildDevice(Boolean create) {
  String dni = getQueueChildDNI()
  ChildDeviceWrapper child = getQueueChild()
  if(!child && create) {
    try {
      logDebug("Creating Queue device")
      child = addChildDevice('dwinks', 'Sonos Advanced Queue', dni,
        [name: 'Sonos Queue', label: "Sonos Queue - ${(device.getDataValue('name') ?: device.displayName)}"])
    } catch (UnknownDeviceTypeException e) {
      logException("createRemoveQueueChildDevice: ${e.message}")
    } catch (Exception e) {
      logException("createRemoveQueueChildDevice: ${e.message}")
    }
  } else if(!create && child) {
    deleteChildDevice(dni)
  }
}

void createRemoveSleepTimerChildDevice(Boolean create) {
  String dni = getSleepTimerChildDNI()
  ChildDeviceWrapper child = getSleepTimerChild()
  if(!child && create) {
    try {
      child = addChildDevice('dwinks', 'Sonos Advanced Sleep Timer', dni,
        [name: 'Sonos Sleep Timer', label: "Sonos Sleep Timer - ${device.getDataValue('name') ?: device.displayName}"])
      child.updateDataValue('playerDni', device.getDeviceNetworkId())
      child.applyStateBatch(JsonOutput.toJson([
        remainingDuration: '00:00:00',
        sleepTimerState: 'unknown'
      ]))
    } catch(Exception e) {
      logException("createRemoveSleepTimerChildDevice: ${e.message}")
    }
  } else if(!create) {
    unschedule('getSleepTimer')
    if(child) { deleteChildDevice(dni) }
  }
}

void setSleepTimer(String duration) {
  if(!requireSleepTimerCoordinator()) { return }
  if(!(duration ==~ /\d{2,}:\d{2}:\d{2}/)) {
    logWarn("setSleepTimer: invalid duration '${duration}'; expected hh:mm:ss")
    return
  }
  List<String> parts = duration.split(':') as List<String>
  if(parts[1].toInteger() > 59 || parts[2].toInteger() > 59) {
    logWarn("setSleepTimer: invalid duration '${duration}'; minutes and seconds must be 00-59")
    return
  }
  String host = getDeviceDataValue('localUpnpHost')
  Map params = getSoapActionParams(host, AVTransport, 'ConfigureSleepTimer',
    [NewSleepTimerDuration: duration])
  postSonosSoapAsync('sleepTimerSetCallback', params,
    [sleepTimerChildDni: getSleepTimerChildDNI()])
}

void cancelSleepTimer() {
  if(!requireSleepTimerCoordinator()) { return }
  String host = getDeviceDataValue('localUpnpHost')
  Map params = getSoapActionParams(host, AVTransport, 'ConfigureSleepTimer',
    [NewSleepTimerDuration: ''])
  postSonosSoapAsync('sleepTimerSetCallback', params,
    [sleepTimerChildDni: getSleepTimerChildDNI()])
}

void getSleepTimer() {
  if(!requireSleepTimerCoordinator()) { return }
  String host = getDeviceDataValue('localUpnpHost')
  Map params = getSoapActionParams(host, AVTransport, 'GetRemainingSleepTimerDuration')
  asynchttpPost('sleepTimerGetCallback', params,
    [sleepTimerChildDni: getSleepTimerChildDNI()])
}

Boolean requireSleepTimerCoordinator() {
  if(getIsGroupCoordinator()) { return true }
  unschedule('getSleepTimer')
  ChildDeviceWrapper child = getSleepTimerChild()
  if(child) { child.applyStateBatch(JsonOutput.toJson([sleepTimerState: 'unknown'])) }
  logWarn('Sleep timer actions are available only on the current Sonos group coordinator')
  return false
}

void sleepTimerSetCallback(AsyncResponse response, Map data = null) {
  if(response?.status != 200 || response.hasError()) {
    logWarn("Sleep timer command failed: ${response?.getErrorMessage() ?: response?.status}")
    return
  }
  getSleepTimer()
}

void sleepTimerGetCallback(AsyncResponse response, Map data = null) {
  if(response?.status != 200 || response.hasError()) {
    ChildDeviceWrapper child = getSleepTimerChild()
    if(child) { child.applyStateBatch(JsonOutput.toJson([sleepTimerState: 'unknown'])) }
    logWarn("Sleep timer query failed: ${response?.getErrorMessage() ?: response?.status}")
    return
  }
  try {
    String body = response.getData()?.toString() ?: ''
    GPathResult xml = new XmlSlurper().parseText(body)
    String remaining = findSleepTimerXmlText(xml, 'RemainingSleepTimerDuration')
    String generation = findSleepTimerXmlText(xml, 'CurrentSleepTimerGeneration')
    ChildDeviceWrapper child = getSleepTimerChild()
    if(child) {
      Boolean active = remaining && remaining != '00:00:00'
      child.applyStateBatch(JsonOutput.toJson([
        remainingDuration: remaining ?: '00:00:00',
        sleepTimerState: active ? 'active' : 'inactive',
        generation: generation
      ]))
      if(active && getCreateSleepTimerChildDevice()) {
        runIn(60, 'getSleepTimer', [overwrite: true])
      } else {
        unschedule('getSleepTimer')
      }
    }
  } catch(Exception e) {
    ChildDeviceWrapper child = getSleepTimerChild()
    if(child) { child.applyStateBatch(JsonOutput.toJson([sleepTimerState: 'unknown'])) }
    logWarn("Could not parse sleep timer response: ${e.message}")
  }
}

String findSleepTimerXmlText(GPathResult node, String elementName) {
  if(node?.name()?.toString() == elementName) { return node.text()?.toString() ?: '' }
  for(Object child : node?.children() ?: []) {
    if(child instanceof GPathResult) {
      String value = findSleepTimerXmlText((GPathResult)child, elementName)
      if(value != null) { return value }
    }
  }
  return null
}

void createRemoveRightChannelChildDevice(Boolean create) {
  String dni = getRightChannelChildDNI()
  ChildDeviceWrapper child = getRightChannelChild()
  if(!create) {
    if(child != null) { deleteChildDevice(dni) }
    return
  }

  // Older discovery stored RF even when this player was itself the RF member.
  // Resolve that value to the companion before creating or repairing the child.
  setRightChannelRincon(getRightChannelRincon())
  String companionId = getRightChannelRincon()
  String companionIp = getRightChannelDeviceIp()
  if(!companionId || companionId == getId() || !companionIp) {
    logWarn('Cannot configure stereo companion child until its ID and IP are discovered.')
    return
  }

  try {
    Boolean created = child == null
    if(created) {
      logDebug('Creating Right Channel device')
      child = addChildDevice('dwinks', 'Sonos Advanced Secondaries', dni,
        [name: 'Sonos Secondaries', label: "Sonos Right Channel - ${getDeviceDataValue('name')}"])
    }
    String websocketUrl = "wss://${companionIp}:1443/websocket/api"
    Boolean changed = child.getDataValue('deviceIp') != companionIp ||
      child.getDataValue('id') != companionId || child.getDataValue('websocketUrl') != websocketUrl
    child.updateDataValue('deviceIp', companionIp)
    child.updateDataValue('id', companionId)
    child.updateDataValue('websocketUrl', websocketUrl)
    if(created || changed) { child.initialize() }
  } catch (UnknownDeviceTypeException e) {
    logException('createRemoveRightChannelChildDevice', e)
  }
}

void createRemoveNightModeChildDevice(Boolean create) {
  String dni = getNightModeChildDNI()
  ChildDeviceWrapper child = getNightModeChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating NightMode device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos NightMode Control',
          label: "Sonos NightMode - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'NightMode')
      scheduleComponentChildEventDrainNow(dni)
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveNightModeChildDevice', e)
    }
  }
  else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
}

void createRemoveSpeechEnhancementChildDevice(Boolean create) {
  String dni = getSpeechEnhancementChildDNI()
  ChildDeviceWrapper child = getSpeechEnhancementChild()
  if(!child && create) {
    invalidateComponentChildState(dni)
    try {
      logDebug("Creating SpeechEnhancement device")
      child = addChildDevice('hubitat', 'Generic Component Switch', dni,
        [ name: 'Sonos SpeechEnhancement Control',
          label: "Sonos SpeechEnhancement - ${(device.getDataValue('name') ?: device.displayName)}"]
      )
      child.updateDataValue('command', 'SpeechEnhancement')
      scheduleComponentChildEventDrainNow(dni)
    } catch (UnknownDeviceTypeException e) {
      logException('createRemoveSpeechEnhancementChildDevice', e)
    }
  }
  else if(!create && child){ discardComponentChildState(dni); deleteChildDevice(dni) }
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
  if(raw.startsWith('UPNP_RESPONSE|')) {
    logDebug("UPnP response parse entered; payloadLength=${raw.length()}")
    handleUpnpRawResponse(raw)
    return
  }
  if(!raw.startsWith('mac:')) {
    logDebug("WebSocket parse entered; dni=${device?.getDeviceNetworkId() ?: 'unknown'}, payloadLength=${raw.length()}")
    try {
      setLastWebsocketEvent()
      logDebug('WebSocket last-event timestamp updated')
    } catch(Exception e) {
      logException('WebSocket last-event timestamp update failed', e)
    }
    try {
      logDebug('WebSocket message dispatch beginning')
      processWebsocketMessage(raw)
      logDebug('WebSocket message dispatch completed')
    } catch(Exception e) {
      logException('WebSocket message processing failed after JSON dispatch', e)
      logWarn("WebSocket payload processing failed; payloadLength=${raw.length()}")
    }
    return
  }
  try {
    logDebug("UPnP NOTIFY parse entered; dni=${device?.getDeviceNetworkId() ?: 'unknown'}, payloadLength=${raw.length()}")
    LinkedHashMap message = getMapForRaw(raw)
    LinkedHashMap messageHeaders = (LinkedHashMap)message?.headers
    String xmlBody = (String)message?.body
    if(messageHeaders == null || messageHeaders.size() < 1) {
      logDebug('Ignoring UPnP NOTIFY without parsed headers')
      return
    }
    if(xmlBody == null || xmlBody == '') {
      logDebug('Ignoring UPnP NOTIFY with an empty body')
      return
    }
    String serviceType = messageHeaders["X-SONOS-SERVICETYPE"]
    logDebug("UPnP NOTIFY parsed; serviceType=${serviceType ?: 'header-unset'}, bodyLength=${xmlBody.length()}, headerNames=${messageHeaders.keySet().join(',')}")
    if(serviceType == 'AVTransport' || messageHeaders.containsKey('NOTIFY /avt HTTP/1.1')) {
      try {
        logDebug('AVTransport event processing beginning')
        setLastInboundAVTransportEvent()
        observeExpectedUpnpSubscriptionEvent('sid1')
        processAVTransportMessages(xmlBody, getLocalUpnpUrl())
        logDebug('AVTransport event processing completed')
      } catch (Exception e) { logWarn("Ran into an issue parsing avt: ${e}") }
    }
    else if(serviceType == 'RenderingControl' || messageHeaders.containsKey('NOTIFY /mrc HTTP/1.1')) {
      try {
        logDebug('RenderingControl event processing beginning')
        observeExpectedUpnpSubscriptionEvent('sid2')
        setLastInboundMrRcEvent()
        processRenderingControlMessages(xmlBody)
        logDebug('RenderingControl event processing completed')
      } catch (Exception e) { logException('RenderingControl event processing failed', e) }
    }
    else if(serviceType == 'ZoneGroupTopology' || messageHeaders.containsKey('NOTIFY /zgt HTTP/1.1')) {
      try {
        observeExpectedUpnpSubscriptionEvent('sid3')
        if(!isPlausibleXmlPayload(xmlBody)) {
          logDebug('Ignoring blank or non-XML ZGT event payload')
          return
        }
        String zgtDni = device.getDeviceNetworkId()?.toString() ?: 'unknown'
        logZgtXmlAtTrace(zgtDni, xmlBody)
        // Compare against the last successfully parsed payload. Invalid XML must
        // not become the dedup baseline or count as a healthy inbound event.
        Integer xmlHash = xmlBody.hashCode()
        deferOrProcessZoneGroupTopologyPayload(zgtDni, xmlBody, xmlHash)
      } catch (Exception e) { logWarn("Ran into an issue parsing zgt: ${e}") }
    }
    else if(serviceType == 'GroupRenderingControl' || messageHeaders.containsKey('NOTIFY /mgrc HTTP/1.1')) {
      try {
        observeExpectedUpnpSubscriptionEvent('sid4')
        setLastInboundMrGrcEvent()
        processGroupRenderingControlMessages(xmlBody)
      } catch (Exception e) { logWarn("Ran into an issue parsing mgrc: ${e}") }
    }
    else {
      logPayloadAtTrace('UPnP NOTIFY message had an unrecognized service type; payload omitted',
          "Could not determine service type for message: ${message}")
    }
  } catch (Exception e) { logException('UPnP NOTIFY parse failed', e) }
}

void deferOrProcessZoneGroupTopologyPayload(String dni, String xmlBody, Integer xmlHash = null) {
  String deviceDni = dni ?: device.getDeviceNetworkId()
  if(!deviceDni || !xmlBody) { return }
  Integer payloadHash = xmlHash != null ? xmlHash : xmlBody.hashCode()
  Integer previousHash = lastZgtXmlHash.get(deviceDni)
  if(previousHash != null && previousHash == payloadHash) { return }

  Map topology = parseZoneGroupTopologySnapshot(xmlBody, getId())
  if(shouldHoldTopologyUpdate(topology)) {
    String previousPendingXml = pendingTopologyXmlByDni.put(deviceDni, xmlBody)
    if(topology != null) { pendingTopologySnapshotByDni.put(deviceDni, topology) }
    else { pendingTopologySnapshotByDni.remove(deviceDni) }
    pendingTopologyKindByDni.put(deviceDni, 'zgt')
    if(previousPendingXml != xmlBody) {
      logDebug('Holding topology update until the affected player reaches its expected topology')
    }
    scheduleTopologyTimeoutFlush()
    return
  }

  applyZoneGroupTopologyPayload(deviceDni, xmlBody, payloadHash, topology)
}

void applyZoneGroupTopologyPayload(String dni, String xmlBody, Integer xmlHash) {
  Map topology = parseZoneGroupTopologySnapshot(xmlBody, getId())
  applyZoneGroupTopologyPayload(dni, xmlBody, xmlHash, topology)
}

void applyZoneGroupTopologyPayload(String dni, String xmlBody, Integer xmlHash, Map topology) {
  LinkedHashSet<String> previousMembers = new LinkedHashSet<String>(getGroupPlayerIds())
  if(processZoneGroupTopologySnapshot(topology, previousMembers)) {
    lastZgtXmlHash.put(dni, xmlHash)
    setLastInboundZgtEvent()
    if(isAppZgtSubscriber() && topology?.playerFound == true && topology?.groups instanceof List) {
      Map householdTopology = [householdId: device.getDataValue('householdId'),
        groups: topology.groups, observedAt: now()]
      publishPlayerStateUpdate([kind: 'zgtTopology', topology: householdTopology,
        leaderGeneration: device.getDataValue('appZgtLeaderGeneration')?.toString(), observedAt: now()])
    }
  }
}

Boolean applyAppHouseholdTopology(String topologyJson, Boolean publishMembership = true) {
  if(!topologyJson) { return false }
  try {
    Map topology = (Map)new JsonSlurper().parseText(topologyJson)
    if(!(topology.groups instanceof List)) { return false }
    String playerId = getId()
    Map ownGroup = ((List<Map>)topology.groups).find { Map group ->
      group.playerIds instanceof List && ((List)group.playerIds).collect { Object id -> id?.toString() }.contains(playerId)
    }
    if(ownGroup == null || !ownGroup.groupId || !ownGroup.coordinatorId) { return false }
    Map playerTopology = [playerFound: true, groupId: ownGroup.groupId?.toString(),
      coordinatorId: ownGroup.coordinatorId?.toString(), coordinatorName: ownGroup.coordinatorName?.toString(),
      visibleMemberIds: ((List)ownGroup.playerIds).collect { Object id -> id.toString() },
      memberNames: ownGroup.playerNames instanceof List ? ownGroup.playerNames : [],
      groupName: ownGroup.groupName?.toString(), batteryData: [:]]
    LinkedHashSet<String> previousMembers = new LinkedHashSet<String>(getGroupPlayerIds())
    setDeviceDataValue('householdTopology', topologyJson)
    return processZoneGroupTopologySnapshot(playerTopology, previousMembers, publishMembership)
  } catch(Exception e) {
    logWarn("Could not apply app household topology snapshot: ${e.message}")
    return false
  }
}

void flushPendingZoneGroupTopology() {
  flushPendingTopologyUpdates()
}

void flushPendingTopologyUpdates() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  String expectedJson = getDeviceDataValue('expectedGroupTopology')
  if(expectedJson) {
    try {
      Map expected = (Map)new JsonSlurper().parseText(expectedJson)
      Long expiresAt = expected.expiresAt as Long
      if(expiresAt != null && expiresAt > (now() as Long)) {
        scheduleTopologyTimeoutFlush()
        return
      }
    } catch(Exception ignored) { }
  }
  setDeviceDataValue('expectedGroupTopology', '')
  String pendingKind = pendingTopologyKindByDni.remove(dni)
  String pendingXml = pendingTopologyXmlByDni.remove(dni)
  Map pendingTopology = pendingTopologySnapshotByDni.remove(dni)
  String pendingWs = pendingTopologyWebsocketByDni.remove(dni)
  if(pendingKind == 'ws' && pendingWs) {
    processWebsocketMessage(pendingWs)
    logDebug('Published the latest groups websocket topology snapshot after its hold window elapsed')
  } else if(pendingKind == 'zgt' && pendingXml) {
    if(pendingTopology != null) {
      applyZoneGroupTopologyPayload(dni, pendingXml, pendingXml.hashCode(), pendingTopology)
    } else {
      applyZoneGroupTopologyPayload(dni, pendingXml, pendingXml.hashCode())
    }
    logDebug('Published the latest ZoneGroupTopology snapshot after its hold window elapsed')
  } else {
    return
  }
}

Boolean shouldHoldTopologyUpdate(Map topology) {
  String expectedJson = getDeviceDataValue('expectedGroupTopology')
  if(!expectedJson) { return false }
  Map expected
  try {
    expected = (Map)new JsonSlurper().parseText(expectedJson)
  } catch(Exception e) {
    setDeviceDataValue('expectedGroupTopology', '')
    clearPendingTopologyForDevice()
    return false
  }
  Long expiresAt = expected.expiresAt as Long
  if(expiresAt == null || expiresAt <= (now() as Long)) {
    setDeviceDataValue('expectedGroupTopology', '')
    clearPendingTopologyForDevice()
    return false
  }
  if(topology != null && topology.coordinatorId == expected.coordinatorId &&
     topology.memberIds == expected.memberIds) {
    setDeviceDataValue('expectedGroupTopology', '')
    clearPendingTopologyForDevice()
    logDebug('Observed expected topology for this player; releasing the update')
    return false
  }
  return true
}

void clearPendingTopologyForDevice() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  pendingTopologyXmlByDni.remove(dni)
  pendingTopologySnapshotByDni.remove(dni)
  pendingTopologyWebsocketByDni.remove(dni)
  pendingTopologyKindByDni.remove(dni)
}

void scheduleTopologyTimeoutFlush() {
  String expectedJson = getDeviceDataValue('expectedGroupTopology')
  if(!expectedJson) { return }
  try {
    Map expected = (Map)new JsonSlurper().parseText(expectedJson)
    Long expiresAt = expected.expiresAt as Long
    Integer remaining = Math.max(1, Math.ceil((expiresAt - (now() as Long)) / 1000.0d) as Integer)
    runIn(remaining, 'flushPendingTopologyUpdates', [overwrite: true])
  } catch(Exception e) {
    runIn(1, 'flushPendingTopologyUpdates', [overwrite: true])
  }
}

@CompileStatic
Map parseZoneGroupTopologySnapshot(String xmlString, String playerId) {
  if(!playerId || !isPlausibleXmlPayload(xmlString)) { return null }
  try {
    XmlSlurper parser = new XmlSlurper()
    GPathResult propertyset = parser.parseText(xmlString)
    String stateXml = ((GPathResult)propertyset['property']['ZoneGroupState']).text()
    String unescaped = unEscapeLastChangeXML(stateXml)
    if(!isPlausibleXmlPayload(unescaped)) { return null }
    GPathResult stateXmlParsed = parser.parseText(unescaped)
    GPathResult wrappedZoneGroups = (GPathResult)stateXmlParsed['ZoneGroups']
    GPathResult zoneGroups = wrappedZoneGroups.size() > 0 ? wrappedZoneGroups : stateXmlParsed
    String groupName = ((GPathResult)propertyset['property']['ZoneGroupName']).text().toString()
    GPathResult playerGroup = null
    GPathResult player = null
    GPathResult groups = zoneGroups.children()
    List<Map> householdGroups = []
    for(GPathResult group : groups) {
      String groupCoordinatorId = group['@Coordinator'].toString()
      String groupId = group['@ID'].toString() ?: groupCoordinatorId
      List<String> groupPlayerIds = []
      List<String> groupPlayerNames = []
      for(GPathResult member : group.children()) {
        String memberId = member['@UUID'].toString()
        String memberName = member['@ZoneName'].toString()
        if(member['@Invisible'].toString() != '1') {
          groupPlayerIds.add(memberId)
          groupPlayerNames.add(memberName)
        }
        if(memberId == playerId) {
          playerGroup = group
          player = member
        }
      }
      householdGroups.add([groupId: groupId, coordinatorId: groupCoordinatorId,
        playerIds: groupPlayerIds, playerNames: groupPlayerNames,
        groupName: group['@ZoneGroupName'].toString() ?: groupName])
    }
    if(playerGroup == null || player == null) {
      List<String> observedMemberIds = []
      for(GPathResult group : groups) {
        for(GPathResult member : group.children()) {
          observedMemberIds.add(member['@UUID'].toString())
        }
      }
      logPayloadAtTrace("Player ${playerId} not found in parsed ZoneGroupTopology; observedMemberCount=${observedMemberIds.size()}; groups=${householdGroups.size()}",
        "ZGT player lookup failed; requestedPlayerId=${playerId}; observedMemberIds=${observedMemberIds}; householdGroups=${householdGroups}")
      return [playerFound: false, groupName: groupName, groups: householdGroups]
    }

    String coordinatorId = playerGroup['@Coordinator'].toString()
    String coordinatorName = null
    LinkedHashSet<String> visibleMemberIds = new LinkedHashSet<String>()
    LinkedHashSet<String> memberNames = new LinkedHashSet<String>()
    for(GPathResult member : playerGroup.children()) {
      String memberId = member['@UUID'].toString()
      if(memberId == coordinatorId) { coordinatorName = member['@ZoneName'].toString() }
      if(member['@Invisible'].toString() == '1') { continue }
      visibleMemberIds.add(memberId)
      memberNames.add(member['@ZoneName'].toString())
    }
    List<String> visibleMemberIdList = new ArrayList<String>(visibleMemberIds)
    List<String> sortedMemberIds = new ArrayList<String>(visibleMemberIds)
    Collections.sort(sortedMemberIds)
    Map<String, String> batteryData = [:]
    String moreInfo = player['@MoreInfo'].toString()
    if(moreInfo) {
      for(String part : moreInfo.tokenize(',')) {
        List<String> entry = part.tokenize(':')
        if(entry.size() > 1 && (entry[0] == 'BattTmp' || entry[0] == 'BattPct' || entry[0] == 'BattChg')) {
          batteryData[entry[0]] = entry[1]
        }
      }
    }
    String playerGroupId = playerGroup['@ID'].toString() ?: coordinatorId
    return [playerFound: true, groupId: playerGroupId, coordinatorId: coordinatorId, coordinatorName: coordinatorName,
      memberIds: sortedMemberIds, visibleMemberIds: visibleMemberIdList,
      memberNames: new ArrayList<String>(memberNames),
      groupName: groupName, batteryData: batteryData, groups: householdGroups]
  } catch(Exception e) {
    logDebug("Could not inspect ZGT topology for ${playerId}: ${e.message}")
    return null
  }
}

Map getZoneGroupTopologyForPlayer(String xmlString, String playerId) {
  Map topology = parseZoneGroupTopologySnapshot(xmlString, playerId)
  return topology?.playerFound == true
      ? [coordinatorId: topology.coordinatorId, memberIds: topology.memberIds]
      : null
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
  return text.replace('&lt;','<').replace('&gt;','>').replace('&quot;','\"').replace('&apos;',"'").replace('&amp;','&')
}

@CompileStatic
void processAVTransportMessages(String xmlString, String localUpnpUrl) {
  if(xmlString.contains('&lt;CurrentTrackURI val=&quot;x-rincon:')) { return } //Bail out if this AVTransport message is just "I'm now playing a stream from a coordinator..."

  XmlSlurper xmlParser = new XmlSlurper()
  GPathResult propertyset = xmlParser.parseText(xmlString)
  String lastChange = ((GPathResult)propertyset['property']['LastChange']).text().toString()
  if(!lastChange) {return}
  GPathResult event = xmlParser.parseText(lastChange)
  GPathResult instanceId = (GPathResult)event['InstanceID']
  String transportState = instanceId['TransportState']['@val']?.toString()
  String status = normalizeTransportStatus(transportState)
  if(transportState?.trim()?.equalsIgnoreCase('TRANSITIONING')) {
    logDebug("Ignoring transient Sonos TransportState=TRANSITIONING from AVTransport event at ${localUpnpUrl}")
    return
  }

  String sleepTimerGeneration = instanceId['SleepTimerGeneration']['@val']?.toString()
  String sleepTimerState = instanceId['SleepTimerState']['@val']?.toString()
  ChildDeviceWrapper sleepTimerChild = getSleepTimerChild()
  if(sleepTimerChild) {
    Map<String, Object> sleepTimerAttributes = [:]
    if(sleepTimerState) {
      String normalizedSleepTimerState = sleepTimerState.toUpperCase()
      Boolean sleepTimerActive = normalizedSleepTimerState in ['ACTIVE', 'RUNNING', 'SLEEP_TIMER_RUNNING']
      sleepTimerAttributes.sleepTimerState = sleepTimerActive ? 'active' : 'inactive'
    }
    if(sleepTimerGeneration) {
      sleepTimerAttributes.generation = sleepTimerGeneration
      getSleepTimer()
    }
    if(!sleepTimerAttributes.isEmpty()) {
      applyChildStateBatch(sleepTimerChild, JsonOutput.toJson(sleepTimerAttributes))
    }
  }

  String trackUri = ((instanceId['CurrentTrackURI']['@val']).toString()).replace('&amp;','&').replace('&amp;','&')
  String enqueuedUri = instanceId['EnqueuedTransportURI']['@val']
  String enqueuedTransportURIMetaDataString = instanceId['EnqueuedTransportURIMetaData']['@val']
  String avTransportURI = instanceId['AVTransportURI']['@val']
  Map<String, Object> playbackSnapshot = new LinkedHashMap<String, Object>()
  playbackSnapshot.put('currentUri', avTransportURI?.replace('&amp;', '&'))
  playbackSnapshot.put('currentUriMetaData', instanceId['AVTransportURIMetaData']['@val']?.toString())
  playbackSnapshot.put('trackUri', trackUri)
  playbackSnapshot.put('trackMetaData', instanceId['CurrentTrackMetaData']['@val']?.toString())
  playbackSnapshot.put('track', instanceId['CurrentTrack']['@val']?.toString())
  playbackSnapshot.put('playMode', instanceId['CurrentPlayMode']['@val']?.toString())
  playbackSnapshot.put('transportState', instanceId['TransportState']['@val']?.toString())
  playbackSnapshot.put('observedAt', new Date().getTime())
  Map<String, Object> positionSnapshot = runtimeState[TRANSPORT_STATUS_SNAPSHOT_KEY] instanceof Map
      ? (Map<String, Object>)runtimeState[TRANSPORT_STATUS_SNAPSHOT_KEY] : [:]
  if(positionSnapshot.get('positionMillis') != null) { playbackSnapshot.put('positionMillis', positionSnapshot.get('positionMillis')) }
  runtimeState.lastTransportPlaybackSnapshot = playbackSnapshot

  Boolean isPlayingLocalTrack = false
  if(trackUri.startsWith('http') && trackUri == enqueuedUri && trackUri == avTransportURI) {
    isPlayingLocalTrack = true
    logDebug("Playing Local Track")
  }

  setStatusTransportStatus(transportState, "AVTransport event from ${localUpnpUrl}")

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
    GPathResult currentTrackMetaData = xmlParser.parseText(unEscapeMetaData(currentTrackMetaDataString))
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
    setAudioSource(trackUri, isPlayingLocalTrack)

  } else {
    setCurrentArtistName('Not Available')
    setCurrentAlbumName('Not Available')
    setCurrentTrackName('Not Available')
    setCurrentTrackNumber(0)
    setTrackDataEvents([:])
  }

  String nextTrackMetaData = instanceId['NextTrackMetaData']['@val']
  if(nextTrackMetaData != null && nextTrackMetaData != '') {
    GPathResult nextTrackMetaDataXML = xmlParser.parseText(nextTrackMetaData)
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

      String nextAlbumArtURI = (((GPathResult)nextTrackMetaDataXML['item']['albumArtURI']).text()).toString()
      while(nextAlbumArtURI.contains('&amp;')) { nextAlbumArtURI = nextAlbumArtURI.replace('&amp;','&') }
      if(nextAlbumArtURI != null && nextAlbumArtURI != '') { setNextTrackAlbumArtURI(nextAlbumArtURI, isPlayingLocalTrack) }
      else { setNextTrackAlbumArtURI('Not Available', isPlayingLocalTrack) }

    }
  } else {
    setNextArtistName('Not Available')
    setNextAlbumName('Not Available')
    setNextTrackName('Not Available')
    setNextTrackAlbumArtURI('Not Available', isPlayingLocalTrack)
  }
}

@CompileStatic
Boolean isPlausibleXmlPayload(String payload) {
  if(payload == null) { return false }
  String normalized = payload.trim()
  return normalized != '' && normalized.startsWith('<')
}

@CompileStatic
Boolean processZoneGroupTopologyMessages(String xmlString, LinkedHashSet oldGroupedRincons) {
  if(!isPlausibleXmlPayload(xmlString)) {
    logDebug('Ignoring blank or non-XML ZGT property set')
    return false
  }
  Map topology = parseZoneGroupTopologySnapshot(xmlString, getId())
  return processZoneGroupTopologySnapshot(topology, oldGroupedRincons)
}

@CompileStatic
Boolean processZoneGroupTopologySnapshot(Map topology, LinkedHashSet<String> oldGroupedRincons,
    Boolean publishMembership = true) {
  if(topology == null) { return false }
  if(topology.playerFound != true) {
    logDebug("Player ${getId()} not found in ZoneGroupTopology!")
    return true
  }

  List<String> memberIds = topology.visibleMemberIds as List<String>
  LinkedHashSet<String> groupedRincons = new LinkedHashSet<String>(memberIds ?: [])
  logDebug("Reconciling topology snapshot; dni=${device.getDeviceNetworkId()}, members=${groupedRincons}, coordinator=${topology.coordinatorId}, groupId=${topology.groupId}, groupName=${topology.groupName}, memberNames=${topology.memberNames}, priorMembers=${oldGroupedRincons}, currentCount=${this.device.currentValue('groupMemberCount', true)}, currentIsGrouped=${this.device.currentValue('isGrouped', true)}, currentNames=${this.device.currentValue('groupMemberNames', true)}")
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
  }

  String currentGroupCoordinatorRincon = topology.coordinatorId as String
  String currentGroupCoordinatorName = topology.coordinatorName as String
  if(currentGroupCoordinatorName) {setGroupCoordinatorName(currentGroupCoordinatorName)}
  if(currentGroupCoordinatorRincon) {setGroupCoordinatorId(currentGroupCoordinatorRincon)}

  Boolean isGroupCoordinator = currentGroupCoordinatorRincon == getId()

  if(groupedRincons != oldGroupedRincons) {
    List<String> newRincons = new ArrayList<String>()
    newRincons.addAll(groupedRincons - oldGroupedRincons)
    if(newRincons.size() > 0 && isGroupCoordinator == true) {
      logDebug("Sending events to newly joined member(s): ${newRincons}")
      sendEventsToNewGroupMembers(newRincons)
    }
    logDebug('ZGT message parsed, group member changes found.')
  } else {
    logDebug('ZGT message parsed, no group member changes.')
  }

  setGroupPlayerIds(groupedRincons.toList(), publishMembership)

  List<String> currentGroupMemberNames = topology.memberNames as List<String>
  Integer currentGroupMemberCount = groupedRincons.size()

  String groupName = topology.groupName as String
  if(groupName && groupedRincons) {
    updateZoneGroupName(groupName)
  }

  setIsGrouped(currentGroupMemberCount > 1)
  setGroupMemberCount(currentGroupMemberCount)
  if(currentGroupMemberNames.size() > 0) {setGroupMemberNames(currentGroupMemberNames.toList())}
  if(groupName) {setGroupName(groupName)}

  logDebug("Topology reconciliation queued; dni=${device.getDeviceNetworkId()}, members=${groupedRincons}, expectedCount=${currentGroupMemberCount}, expectedIsGrouped=${currentGroupMemberCount > 1 ? 'on' : 'off'}, expectedNames=${currentGroupMemberNames}, expectedGroupName=${groupName}, pendingLocalEvents=${pendingLocalDeviceEvents.get(device.getDeviceNetworkId())?.size() ?: 0}")

  if(processBatteryStatusChildDeviceMessages()) {
    Map<String, String> batteryData = topology.batteryData as Map<String, String>
    if(batteryData != null && batteryData.containsKey('BattTmp') && batteryData.containsKey('BattPct') && batteryData.containsKey('BattChg')) {
      Integer battery = batteryData['BattPct'] as Integer
      String powerSource = batteryData['BattChg'] == 'NOT_CHARGING' ? 'battery' : 'mains'
      BigDecimal temperature = new BigDecimal(batteryData['BattTmp'])
      updateChildBatteryStatus(battery, powerSource, temperature)
    }
  }
  return true
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
  setNextTrackAlbumArtURI('Not Available', false)
  // Queue directly (not via setters) to avoid triggering group propagation and metadata refresh on clear
  if(shouldQueueLocalDeviceEvent('albumArtURI', 'Not Available', this.device.currentValue('albumArtURI'))) {
    queueLocalDeviceEvent('albumArtURI', 'Not Available')
  }
  if(!getDisableAlbumArtEvents()) {
    if(shouldQueueLocalDeviceEvent('albumArtSmall', 'Not Available', this.device.currentValue('albumArtSmall'))) {
      queueLocalDeviceEvent('albumArtSmall', 'Not Available')
    }
    if(shouldQueueLocalDeviceEvent('albumArtMedium', 'Not Available', this.device.currentValue('albumArtMedium'))) {
      queueLocalDeviceEvent('albumArtMedium', 'Not Available')
    }
    if(shouldQueueLocalDeviceEvent('albumArtLarge', 'Not Available', this.device.currentValue('albumArtLarge'))) {
      queueLocalDeviceEvent('albumArtLarge', 'Not Available')
    }
  }
  if(shouldQueueLocalDeviceEvent('audioSource', 'Not Available', this.device.currentValue('audioSource'))) {
    queueLocalDeviceEvent('audioSource', 'Not Available')
  }
  if(shouldQueueLocalDeviceEvent('currentFavorite', 'Not Available', this.device.currentValue('currentFavorite'))) {
    queueLocalDeviceEvent('currentFavorite', 'Not Available')
  }
  if(shouldQueueLocalDeviceEvent('trackDescription', 'Not Available', this.device.currentValue('trackDescription'))) {
    queueLocalDeviceEvent('trackDescription', 'Not Available')
  }
}

void parentUpdateGroupDevices(String coordinatorId, List<String> playersInGroup) {
  if(coordinatorId == null || coordinatorId == '') {return}
  if(playersInGroup == null || playersInGroup.size() == 0) {return}
  // Let the topology callback return before entering the parent app. This
  // prevents an app->player state read from waiting on the same player method
  // that is trying to publish the membership change.
  runIn(1, 'deferredParentUpdateGroupDevices', [
    overwrite: true,
    data: [coordinatorId: coordinatorId, playersInGroup: new ArrayList<String>(playersInGroup)]
  ])
}

void deferredParentUpdateGroupDevices(Map data) {
  String coordinatorId = data?.coordinatorId as String
  List<String> playersInGroup = data?.playersInGroup as List<String>
  if(coordinatorId && playersInGroup) {
    publishPlayerStateUpdate([kind: 'membership', coordinatorId: coordinatorId, playersInGroup: playersInGroup])
  }
}

@CompileStatic
void updateZoneGroupName(String groupName) {
  queueLocalDeviceEvent('groupName', groupName)
}

@CompileStatic
void processGroupRenderingControlMessages(String xmlString) {
  GPathResult propertyset = new XmlSlurper().parseText(xmlString)
  GPathResult gVol = ((GPathResult)propertyset.children().children()).find{GPathResult it -> it.name() == 'GroupVolume'}
  GPathResult gMute = ((GPathResult)propertyset.children().children()).find{GPathResult it -> it.name() == 'GroupMute'}

  if(gVol != null && gVol.size() > 0) {
    Integer groupVolume = Integer.parseInt(gVol.text())
    setGroupVolumeState(groupVolume)
    queueGroupDeviceUpdate([_groupVolume: groupVolume])
  }
  if(gMute != null && gMute.size() > 0) {
    String groupMute = Integer.parseInt(gMute.text()) == 1 ? 'muted' : 'unmuted'
    setGroupMuteState(groupMute)
    queueGroupDeviceUpdate([_groupMute: groupMute])
  }
}

@CompileStatic
void setGroupVolumeState(Integer groupVolume) {
  if(!shouldQueueLocalDeviceEvent('groupVolume', groupVolume, getGroupVolumeState())) { return }
  queueLocalDeviceEvent('groupVolume', groupVolume)
}
@CompileStatic
Integer getGroupVolumeState() {
  Object raw = getDevice().currentValue('groupVolume', true)
  return raw != null ? raw as Integer : null
}

@CompileStatic
void setGroupMuteState(String groupMute) {
  if(!shouldQueueLocalDeviceEvent('groupMute', groupMute, getGroupMuteState())) { return }
  queueLocalDeviceEvent('groupMute', groupMute)
}
@CompileStatic
String getGroupMuteState() {
  Object raw = getDevice().currentValue('groupMute', true)
  return raw != null ? raw.toString() : 'unmuted'
}

@CompileStatic
void setNightModeState(String value) {
  if(!shouldQueueLocalDeviceEvent('nightMode', value, this.device.currentValue('nightMode') as String)) { return }
  queueLocalDeviceEvent('nightMode', value)
  sendChildEvent(getNightModeChild(), 'switch', value)
}

@CompileStatic
void setDialogLevelState(String value) {
  if(!shouldQueueLocalDeviceEvent('speechEnhancement', value,
      this.device.currentValue('speechEnhancement') as String)) { return }
  queueLocalDeviceEvent('speechEnhancement', value)
  sendChildEvent(getSpeechEnhancementChild(), 'switch', value)
}


@CompileStatic
void processRenderingControlMessages(String xmlString) {
  logDebug("RenderingControl processor entered; xmlLength=${xmlString?.length() ?: 0}")
  GPathResult propertyset = parseSonosMessageXML(xmlString)
  logDebug('RenderingControl XML parsed')
  GPathResult instanceId = (GPathResult)propertyset['property']['LastChange']['Event']['InstanceID']
  logDebug("RenderingControl InstanceID located; childCount=${instanceId.children().size()}")

  String volume = ''
  String lfString = ''
  String rfString = ''
  String mute = ''
  String loudness = ''
  for(Object childObject : instanceId.children()) {
    GPathResult child = (GPathResult)childObject
    String elementName = child.name()
    if(elementName == 'Volume' || elementName == 'Mute' || elementName == 'Loudness') {
      String channel = child['@channel'].toString()
      String value = child['@val'].toString()
      logDebug("RenderingControl value extracted; element=${elementName}, channel=${channel ?: 'unset'}, value=${value ?: 'unset'}")
      if(elementName == 'Volume') {
        if(channel == 'Master') { volume = value }
        else if(channel == 'LF') { lfString = value }
        else if(channel == 'RF') { rfString = value }
      } else if(channel == 'Master' && elementName == 'Mute') {
        mute = value
      } else if(channel == 'Master' && elementName == 'Loudness') {
        loudness = value
      }
    }
  }
  if(volume) {
    logDebug("RenderingControl Master volume conversion beginning; value=${volume}")
    setPlayerVolume(volume as Integer)
    logDebug('RenderingControl Master volume update completed')
  }

  lfString = lfString ?: '0'
  rfString = rfString ?: '0'
  logDebug("RenderingControl balance conversion beginning; left=${lfString}, right=${rfString}")
  Integer lf = Integer.parseInt(lfString)
  Integer rf = Integer.parseInt(rfString)
  Integer balance = 0
  if(lf < 100) {
    balance = ((100 - lf) / 5) as Integer
  } else if(rf < 100) {
    balance = -((100 - rf) / 5) as Integer
  }
  setBalanceState(balance)
  logDebug("RenderingControl balance update completed; balance=${balance}")

  if(mute) {
    logDebug("RenderingControl mute update beginning; value=${mute}")
    setMuteState(mute == '1' ? 'muted' : 'unmuted')
    logDebug('RenderingControl mute update completed')
  }

  String bass = instanceId['Bass']['@val']
  if(bass) {
    logDebug("RenderingControl bass conversion beginning; value=${bass}")
    setBassState(bass as Integer)
    logDebug('RenderingControl bass update completed')
  }

  String treble = instanceId['Treble']['@val']
  if(treble) {
    logDebug("RenderingControl treble conversion beginning; value=${treble}")
    setTrebleState(treble as Integer)
    logDebug('RenderingControl treble update completed')
  }

  if(loudness) {
    logDebug("RenderingControl loudness update beginning; value=${loudness}")
    setLoudnessState(loudness == '1' ? 'on' : 'off')
    logDebug('RenderingControl loudness update completed')
  }

  String nightMode = instanceId['NightMode']['@val']
  if(nightMode) {
    logDebug("RenderingControl night mode update beginning; value=${nightMode}")
    setNightModeState(nightMode == '1' ? 'on' : 'off')
    logDebug('RenderingControl night mode update completed')
  }

  String dialogLevel = instanceId['DialogLevel']['@val']
  if(dialogLevel) {
    logDebug("RenderingControl dialog level update beginning; value=${dialogLevel}")
    setDialogLevelState(dialogLevel == '1' ? 'on' : 'off')
    logDebug('RenderingControl dialog level update completed')
  }
  logDebug('RenderingControl processor completed')
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
String getSid(String sid) {
  return device.getDataValue(sid)
}
void setSid(String sid, String value) {
  setDeviceDataValue(sid, value)
}
void deleteSid(String sid) {
  logDebug("Removing SID for ${sid}")
  device.removeDataValue(sid)
  device.removeDataValue("${sid}-expires")
  // Clean up in-memory expiry
  String expiryKey = "${getDeviceDNI()}-${sid}-expires"
  eventTimestamps.remove(expiryKey)
}
@CompileStatic
Boolean subValid(String sid) {
  String expiryKey = "${getDeviceDNI()}-${sid}-expires"
  Long exp = eventTimestamps.get(expiryKey)
  if(exp == null) { return false }
  long expiryEpoch = exp.longValue()
  long resubHalfInterval = RESUB_INTERVAL.intdiv(2).longValue()
  long nowEpoch = Instant.now().getEpochSecond()
  if((expiryEpoch - resubHalfInterval) > nowEpoch && hasSid(sid) == true) {
    return true
  } else {
    return false
  }
}
@CompileStatic
void updateSid(String sid, Map headers, Long grantedTimeout = null) {
  long expiresEpochValue = Instant.now().getEpochSecond() + (grantedTimeout != null ? grantedTimeout.longValue() : (2L * RESUB_INTERVAL.longValue()))
  Long expiresEpoch = Long.valueOf(expiresEpochValue)
  // Store expiry in-memory for subValid() checks
  String expiryKey = "${getDeviceDNI()}-${sid}-expires"
  eventTimestamps.put(expiryKey, expiresEpoch)
  // Write human-readable expiry to device data for visibility
  String formatted = formatEpoch(expiresEpoch)
  setDeviceDataValue("${sid}-expires", formatted)
  if(headers["SID"]) {setSid(sid, headers["SID"].toString())}
  if(headers["sid"]) {setSid(sid, headers["sid"].toString())}
}

void scheduleResubscriptionToEvents(String eventsToResub, Long grantedTimeout = null) {
  // Stagger the 1-hour resub window deterministically per device so that
  // multiple players don't all hit the hub at the same second.
  Integer jitterSeconds = getDeviceResubJitterSeconds()
  Integer renewalDelay = grantedTimeout != null ? Math.max(30, Math.min(RESUB_INTERVAL, grantedTimeout.intValue()) - 100 - jitterSeconds) : RESUB_INTERVAL - 100 - jitterSeconds
  runIn(renewalDelay, eventsToResub, [overwrite: true])
  clearSubscriptionRetryAttempts(eventsToResub)
}

Integer getDeviceResubJitterSeconds() {
  String dni = getDNI()
  if(dni == null || dni == '') { return 0 }
  return Math.abs(dni.hashCode() % 180)
}

Integer getRandomLockRetry(Integer low = 8, Integer high = 30) {
  return Math.abs(rand.nextInt() % (high - low)) + low
}

void removeResub(String resub) { unschedule(resub)}

// The registry value is never replaced while present: each player keeps one lock
// guarding the queue, active GUID, socket and lifecycle generation.
Map getUpnpRequestQueue(String dni = getDNI()) {
  if(!dni) { return null }
  Map existing = upnpRequestQueues.get(dni)
  if(existing != null) { return existing }
  Map created = [lock: new Object(), queue: new ArrayList<Map>(), active: null, socket: null, generation: 0L, retired: false]
  Map raced = upnpRequestQueues.putIfAbsent(dni, created)
  return raced ?: created
}

void resetSubscriptionMutexesForDevice() {
  invalidateUpnpRequestsForDevice()
}

Integer incrementSubscriptionRetryAttempts(String eventsToRetry) {
  String key = retryAttemptKey(eventsToRetry)
  Integer next = (subscribeRetryAttempts.get(key) ?: 0) + 1
  subscribeRetryAttempts.put(key, next)
  return next
}

String retryAttemptKey(String eventsToRetry) { return "${getDNI()}-${eventsToRetry}" }
void clearSubscriptionRetryAttempts(String eventsToRetry) { subscribeRetryAttempts.remove(retryAttemptKey(eventsToRetry)) }

void upnpSubscribeGeneric(String sub, String subId, String subPath, String callback, String evtSub) {
  enqueueUpnpRequest('subscribe', sub, null, subId, subPath, callback, evtSub)
}
void upnpResubscribeGeneric(String resub, String subId, String subPath, String callback, String evtSub) {
  enqueueUpnpRequest('renew', null, resub, subId, subPath, callback, evtSub)
}
void upnpUnsubscribeGeneric(String unsub, String subId, String resub, String evtSub, String callback) {
  enqueueUpnpRequest('unsubscribe', unsub, resub, subId, null, callback, evtSub)
}

void enqueueUpnpRequest(String operation, String method, String resub, String sidKey, String eventPath, String callbackPath, String eventUrl) {
  Map entry = getUpnpRequestQueue()
  if(entry == null || !['sid1','sid2','sid3','sid4'].contains(sidKey)) { return }
  String subscribeMethod = defaultSubscribeMethod(sidKey)
  String resubscribeMethod = resub ?: defaultResubscribeMethod(sidKey)
  Map request = [guid: UUID.randomUUID().toString(), operation: operation, sidKey: sidKey,
    eventUrl: eventUrl, eventPath: eventPath, callbackPath: callbackPath, method: method, subscribeMethod: subscribeMethod, resub: resubscribeMethod,
    sid: null, phase: 'queued', attempts: 0, dispatchedAt: null, deadlineAt: null, lifecycleGeneration: entry.generation]
  Boolean added = false
  Integer pendingDepth = 0
  String activeGuid = null
  Long queueGeneration = null
  synchronized(entry.lock) {
    if(entry.retired == true) {
      logDebug("Ignoring UPnP ${operation} for ${sidKey}; request queue is retired generation=${entry.generation}")
      return
    }
    request.lifecycleGeneration = entry.generation
    Map active = entry.active as Map
    // Coalesce identical outcomes only. Opposite operations retain their order.
    List<Map> sameServicePending = (entry.queue as List<Map>).findAll { Map queued -> queued.sidKey == sidKey }
    Map lastSameService = sameServicePending ? sameServicePending.last() : null
    Boolean duplicate = lastSameService != null
      ? lastSameService.operation == operation
      : (active != null && active.operation == operation && active.sidKey == sidKey)
    if(!duplicate) { (entry.queue as List<Map>).add(request); added = true }
    pendingDepth = (entry.queue as List<Map>).size()
    activeGuid = entry.active?.guid as String
    queueGeneration = entry.generation as Long
    if(operation == 'unsubscribe') {
      if(resub) { removeResub(resub) }
      removeResub(subscribeMethod)
      switch(sidKey) {
        case 'sid1': unschedule('retryUpnpSid1'); break
        case 'sid2': unschedule('retryUpnpSid2'); break
        case 'sid3': unschedule('retryUpnpSid3'); break
        case 'sid4': unschedule('retryUpnpSid4'); break
      }
    }
  }
  if(added) {
    logDebug("Queued UPnP ${operation} ${eventUrl} guid=${request.guid} generation=${queueGeneration} depth=${pendingDepth} activeGuid=${activeGuid ?: 'none'}")
    dispatchNextUpnpRequest(entry)
  } else {
    logDebug("Coalesced duplicate UPnP ${operation} ${eventUrl} sidKey=${sidKey} activeGuid=${activeGuid ?: 'none'} depth=${pendingDepth}")
  }
}

String defaultSubscribeMethod(String sidKey) {
  return [sid1: 'subscribeToAVTransport', sid2: 'subscribeToMrRcEvents', sid3: 'subscribeToZgtEvents', sid4: 'subscribeToMrGrcEvents'][sidKey]
}
String defaultResubscribeMethod(String sidKey) {
  return [sid1: 'resubscribeToAVTransport', sid2: 'resubscribeToMrRcEvents', sid3: 'resubscribeToZgtEvents', sid4: 'resubscribeToMrGrcEvents'][sidKey]
}

void dispatchNextUpnpRequest(Map entry) {
  Map request = null
  Integer remaining = 0
  synchronized(entry.lock) {
    if(entry.active == null && !(entry.queue as List<Map>).isEmpty()) {
      request = (entry.queue as List<Map>).remove(0)
      entry.active = request
      request.phase = 'dispatching'
      request.dispatchedAt = now()
      request.deadlineAt = now() + (UPNP_REQUEST_TIMEOUT_SECONDS * 1000L)
    }
    remaining = (entry.queue as List<Map>).size()
  }
  if(request != null) {
    logDebug("Claimed UPnP queue request guid=${request.guid} operation=${request.operation} sidKey=${request.sidKey} generation=${request.lifecycleGeneration} queuedRemaining=${remaining}")
    dispatchUpnpRequest(entry, request)
  }
}

Map getUpnpEndpoint() {
  String configuredHost = getlocalUpnpHost()
  String host = configuredHost ?: ''
  String port = '1400'
  if(configuredHost?.startsWith('[')) {
    Integer closingBracket = configuredHost.indexOf(']')
    if(closingBracket > 0) {
      host = configuredHost.substring(1, closingBracket)
      if(configuredHost.length() > closingBracket + 2 && configuredHost.charAt(closingBracket + 1) == ':') {
        port = configuredHost.substring(closingBracket + 2)
      }
    }
  } else {
    Integer separator = configuredHost != null ? configuredHost.lastIndexOf(':') : -1
    if(separator > 0 && configuredHost.indexOf(':') == separator) {
      String candidatePort = configuredHost.substring(separator + 1)
      if(candidatePort.isNumber()) { host = configuredHost.substring(0, separator); port = candidatePort }
    }
  }
  return [host: host, port: port]
}

void dispatchUpnpRequest(Map entry, Map request) {
  Boolean redundant = false
  Boolean missingUnsubscribeSid = false
  synchronized(entry.lock) {
    if(entry.active?.guid != request.guid || request.phase != 'dispatching') { return }
    String sid = getSid(request.sidKey as String)
    if(!request.containsKey('expectedCurrentSid')) { request.expectedCurrentSid = sid }
    if(request.operation == 'subscribe' && subValid(request.sidKey as String)) { redundant = true }
    if(request.operation == 'renew' && (!subValid(request.sidKey as String) || !sid)) {
      request.operation = 'subscribe'
      sid = null
    }
    request.sid = sid
    if(request.operation == 'unsubscribe') {
      if(request.resub) { removeResub(request.resub as String) }
      missingUnsubscribeSid = !sid
    }
    if(!redundant && !missingUnsubscribeSid) { request.attempts = ((request.attempts ?: 0) as Integer) + 1 }
  }
  if(redundant) { completeUpnpWithoutResponse(entry, request, 'already-valid'); return }
  if(missingUnsubscribeSid) { completeUpnpWithoutResponse(entry, request, 'no-sid'); return }
  Map endpoint = getUpnpEndpoint()
  String host = endpoint.host as String
  String portValue = (device.getDataValue('localUpnpPort') ?: endpoint.port ?: '1400').toString()
  String verb = request.operation == 'unsubscribe' ? 'UNSUBSCRIBE' : 'SUBSCRIBE'
  String target = request.eventUrl as String
  Boolean sidHeaderSent = request.operation == 'unsubscribe' || (request.operation == 'renew' && request.sid != null)
  StringBuilder http = new StringBuilder()
  String callbackUrl = null
  String timeoutHeader = null
  http.append(verb).append(' ').append(target).append(' HTTP/1.1\r\n')
  http.append('HOST: ').append(host).append(':').append(portValue).append('\r\n')
  if(sidHeaderSent) {
    http.append('SID: ').append(request.sid).append('\r\n')
  } else {
    String callback = "${getLocation().getHub().localIP}:${getLocation().getHub().localSrvPortTCP}"
    callbackUrl = "http://${callback}${request.eventPath}"
    http.append("CALLBACK: <${callbackUrl}>\r\nNT: upnp:event\r\n")
  }
  if(request.operation != 'unsubscribe') {
    timeoutHeader = "Second-${RESUB_INTERVAL}"
    http.append("TIMEOUT: ${timeoutHeader}\r\n")
  }
  http.append('Connection: close\r\n\r\n')
  String guid = request.guid as String
  Integer port = Integer.parseInt(portValue)
  Integer requestBytes = http.toString().getBytes('UTF-8').length
  logDebug("UPnP outbound guid=${guid} operation=${request.operation} target=${target} endpoint=${host}:${portValue} callback=${callbackUrl ?: 'none'} sidHeader=${sidHeaderSent ? 'present' : 'none'} capturedSidPresent=${request.sid != null} timeout=${timeoutHeader ?: 'none'} bytes=${requestBytes}")
  StringBuilder responseBuffer = new StringBuilder()
  Boolean emitted = false
  Boolean trailingCallbackLogged = false
  Boolean dispatchOwned = false
  try {
    synchronized(entry.lock) {
      if(entry.active?.guid != guid || request.phase != 'dispatching') { return }
      entry.socket = interfaces.rawSocket
      dispatchOwned = true
    }
    if(!dispatchOwned) { return }
    // Arm recovery before entering the platform socket API, which may block.
    synchronized(entry.lock) {
      if(entry.active?.guid != guid || request.phase != 'dispatching') { return }
      runIn(UPNP_REQUEST_TIMEOUT_SECONDS, 'upnpSubscriptionWatchdog', [data: [guid: guid], overwrite: true])
    }
    interfaces.rawSocket.connect([timeout: UPNP_REQUEST_TIMEOUT_SECONDS * 1000, inputReader: { InputStream input, Closure parseCallback ->
      synchronized(responseBuffer) {
        if(emitted) {
          if(!trailingCallbackLogged) {
            trailingCallbackLogged = true
            logDebug("Ignoring additional raw socket callbacks after response completion guid=${guid}")
          }
          return
        }
        byte[] inputBytes = new byte[UPNP_MAX_HEADER_BYTES + 1]
        Integer bytesRead = input.read(inputBytes)
        if(bytesRead < 0) {
          logDebug("UPnP socket EOF guid=${guid} bufferedBytes=${responseBuffer.length()}")
          if(!emitted) { emitted = true; parseCallback.call("UPNP_RESPONSE|${guid}|ERROR|eof") }
          return
        }
        responseBuffer.append(new String(inputBytes, 0, bytesRead, 'UTF-8'))
        Integer headerEnd = responseBuffer.indexOf('\r\n\r\n')
        logDebug("UPnP socket read guid=${guid} bytesRead=${bytesRead} bufferedBytes=${responseBuffer.length()} headersComplete=${headerEnd >= 0}")
        if(headerEnd >= 0) {
          emitted = true
          if((headerEnd + 4) > UPNP_MAX_HEADER_BYTES) {
            parseCallback.call("UPNP_RESPONSE|${guid}|ERROR|headers-too-large")
          } else {
            parseCallback.call("UPNP_RESPONSE|${guid}|${responseBuffer.toString()}")
          }
        } else if(responseBuffer.length() > UPNP_MAX_HEADER_BYTES) {
          emitted = true
          parseCallback.call("UPNP_RESPONSE|${guid}|ERROR|headers-too-large")
        }
      }
      }], host, port)
    synchronized(entry.lock) {
      if(entry.active?.guid == guid && request.phase == 'dispatching') { request.phase = 'waiting' }
      else { return }
    }
    interfaces.rawSocket.sendMessage(http.toString())
    synchronized(entry.lock) {
      if(entry.active?.guid == guid && request.phase == 'waiting') {
        logDebug("Dispatched ${verb} ${target} guid=${guid} sidKey=${request.sidKey} attempt=${request.attempts} watchdogSeconds=${UPNP_REQUEST_TIMEOUT_SECONDS}")
      }
    }
  } catch(Exception e) {
    logWarn("UPnP socket dispatch failed guid=${guid}: ${e.message}")
    completeUpnpFailure(entry, request, 'connection-error')
  }
}

void handleUpnpRawResponse(String raw) {
  String rest = raw.substring('UPNP_RESPONSE|'.length())
  Integer separator = rest.indexOf('|')
  if(separator < 1) { return }
  String guid = rest.substring(0, separator)
  String payload = rest.substring(separator + 1)
  logDebug("UPnP response callback guid=${guid} payloadBytes=${payload.getBytes('UTF-8').length}")
  Map entry = upnpRequestQueues.get(getDNI())
  if(entry == null) { logDebug("Ignoring UPnP response guid=${guid}; no queue for dni=${getDNI()}"); return }
  Map request = null
  Map response = null
  String error = null
  synchronized(entry.lock) {
    request = entry.active as Map
    if(request == null || request.guid != guid || request.phase == 'complete' || request.phase == 'expired') {
      logDebug("Ignoring stale UPnP response guid=${guid} activeGuid=${request?.guid ?: 'none'} phase=${request?.phase ?: 'none'}")
      return
    }
    if(request.phase == 'complete' || request.phase == 'expired') {
      logDebug("Ignoring UPnP response guid=${guid} because owner phase=${request.phase}")
      return
    }
    if(payload.startsWith('ERROR|')) { error = payload }
    if(error == null) {
      Integer end = payload.indexOf('\r\n\r\n')
      if(end < 0) { return }
      String[] lines = payload.substring(0, end).split('\r\n')
      String[] statusParts = lines[0].split(' ')
      Integer status = null
      if(statusParts.length > 1) { try { status = Integer.valueOf(statusParts[1]) } catch(NumberFormatException ignored) { status = null } }
      Map<String, String> headers = [:]
      for(Integer i = 1; i < lines.length; i++) {
        Integer colon = lines[i].indexOf(':')
        if(colon > 0) { headers.put(lines[i].substring(0, colon).trim().toLowerCase(), lines[i].substring(colon + 1).trim()) }
      }
      response = [status: status, headers: headers]
      logDebug("Parsed UPnP response guid=${guid} statusLine=${lines[0]} headerNames=${headers.keySet().join(',')}")
    }
  }
  completeUpnpCurrent(entry, request, response, error)
}

void completeUpnpCurrent(Map entry, Map request, Map response, String error) {
  Object socket = null
  Boolean completed = false
  synchronized(entry.lock) {
    if(entry.active?.guid != request.guid || request.phase == 'complete' || request.phase == 'expired') { return }
    request.phase = 'complete'
    completed = true
    Integer status = response?.status as Integer
    logDebug("UPnP response guid=${request.guid} operation=${request.operation} status=${status ?: 'none'} error=${error ?: 'none'}")
    if(error) { logDebug("UPnP request failed guid=${request.guid} reason=${error}") }
    Map<String,String> headers = response?.headers as Map<String,String> ?: [:]
    String sidValue = headers['sid']
    if(request.operation == 'unsubscribe') {
      Boolean sidRemoved = false
      if((status == 200 || status == 412) && getSid(request.sidKey as String) == request.sid) {
        deleteSid(request.sidKey as String)
        sidRemoved = true
      }
      logDebug("UPnP unsubscribe outcome guid=${request.guid} sidKey=${request.sidKey} status=${status ?: 'none'} sidRemoved=${sidRemoved}")
    } else if(status == 200 && (sidValue || request.operation == 'renew')) {
      if(getSid(request.sidKey as String) == request.expectedCurrentSid) {
        Long timeout = parseGrantedTimeout(headers['timeout'])
        if(sidValue) { updateSid(request.sidKey as String, [SID: sidValue], timeout) }
        else { updateSid(request.sidKey as String, [SID: request.sid], timeout) }
        logDebug("UPnP SID accepted guid=${request.guid} sidKey=${request.sidKey} sidPresent=${sidValue != null} grantedTimeoutSeconds=${timeout} resubscribe=${request.resub ?: 'none'}")
        if(request.sidKey == 'sid3' && isAppZgtSubscriber()) {
          publishZgtSubscriptionStatus('accepted', "${request.operation} accepted with SID")
        }
        removeResub(request.subscribeMethod as String)
        if(request.resub) { scheduleResubscriptionToEvents(request.resub as String, timeout) }
        clearSubscriptionRetryAttempts(request.subscribeMethod as String)
        clearSubscriptionRetryAttempts(request.resub as String)
      } else {
        logDebug("Ignoring UPnP SID update guid=${request.guid} sidKey=${request.sidKey}; current SID ownership changed before response")
      }
    } else if(status == 412 && request.operation != 'unsubscribe') {
      if(request.expectedCurrentSid && getSid(request.sidKey as String) == request.expectedCurrentSid) {
        deleteSid(request.sidKey as String)
      }
      logDebug("UPnP renewal rejected with 412 guid=${request.guid} sidKey=${request.sidKey} hadSid=${request.sid != null}; scheduling fresh subscribe")
      scheduleUpnpRetry(request, request.expectedCurrentSid ? 5 : 60)
    } else {
      logDebug("UPnP response requires retry guid=${request.guid} operation=${request.operation} status=${status ?: 'none'} error=${error ?: 'none'}")
      scheduleUpnpRetry(request)
      if(request.sidKey == 'sid3' && request.attempts >= UPNP_REQUEST_MAX_ATTEMPTS && isAppZgtSubscriber()) {
        publishZgtSubscriptionStatus('failed', "${request.operation} exhausted retries (status=${status ?: 'none'}, error=${error ?: 'none'})")
      }
    }
    socket = detachOwnedUpnpSocketLocked(entry, request)
  }
  if(completed) {
    closeDetachedUpnpSocket(socket, request)
    Boolean released = false
    synchronized(entry.lock) {
      if(entry.active?.guid == request.guid && request.phase == 'complete') {
        entry.active = null
        released = true
      }
    }
    if(released) { dispatchNextUpnpRequest(entry) }
  }
}

Long parseGrantedTimeout(String value) {
  if(!value) { return 2L * RESUB_INTERVAL }
  java.util.regex.Matcher matcher = java.util.regex.Pattern.compile('(?i)second-(\\d+)').matcher(value)
  if(matcher.find()) { return Math.max(60L, Long.parseLong(matcher.group(1))) }
  return 2L * RESUB_INTERVAL
}

void completeUpnpWithoutResponse(Map entry, Map request, String reason) {
  Boolean completed = false
  synchronized(entry.lock) {
    if(entry.active?.guid != request.guid) { return }
    request.phase = 'complete'
    logDebug("Completed UPnP request guid=${request.guid} operation=${request.operation} sidKey=${request.sidKey} reason=${reason}")
    entry.active = null
    completed = true
  }
  if(completed) { dispatchNextUpnpRequest(entry) }
  if(completed && request.sidKey == 'sid3' && request.operation == 'subscribe' &&
      reason == 'already-valid' && isAppZgtSubscriber()) {
    publishZgtSubscriptionStatus('accepted', 'existing ZGT SID is still valid')
  }
}

void publishZgtSubscriptionStatus(String status, String detail) {
  publishPlayerStateUpdate([kind: 'zgtSubscription', status: status,
    leaderGeneration: device.getDataValue('appZgtLeaderGeneration')?.toString(),
    detail: detail, observedAt: now()])
}
void completeUpnpFailure(Map entry, Map request, String reason) {
  completeUpnpCurrent(entry, request, null, reason)
}
void scheduleUpnpRetry(Map request, Integer delayOverride = null) {
  if((request.attempts as Integer) >= UPNP_REQUEST_MAX_ATTEMPTS) {
    logWarn("UPnP request retry limit reached guid=${request.guid} operation=${request.operation}")
    return
  }
  Map retryRequest = new LinkedHashMap(request)
  retryRequest.remove('guid')
  retryRequest.remove('phase')
  retryRequest.remove('dispatchedAt')
  retryRequest.remove('deadlineAt')
  scheduleUpnpRetryCallback(request, retryRequest, delayOverride)
}
void scheduleUpnpRetryCallback(Map request, Map retryRequest, Integer delayOverride = null) {
  Integer delay = delayOverride != null ? delayOverride : (5 * (request.attempts as Integer))
  Map options = [data: [request: retryRequest], overwrite: true]
  logDebug("Scheduling UPnP retry guid=${request.guid} operation=${request.operation} sidKey=${request.sidKey} attempt=${request.attempts} delaySeconds=${delay} generation=${request.lifecycleGeneration}")
  switch(request.sidKey) {
    case 'sid1': runIn(delay, 'retryUpnpSid1', options); break
    case 'sid2': runIn(delay, 'retryUpnpSid2', options); break
    case 'sid3': runIn(delay, 'retryUpnpSid3', options); break
    case 'sid4': runIn(delay, 'retryUpnpSid4', options); break
  }
}
void retryUpnpSid1(Map data) { retryUpnpRequest(data) }
void retryUpnpSid2(Map data) { retryUpnpRequest(data) }
void retryUpnpSid3(Map data) { retryUpnpRequest(data) }
void retryUpnpSid4(Map data) { retryUpnpRequest(data) }

void retryUpnpRequest(Map data) {
  Map retry = data?.request as Map
  if(retry == null) { logDebug('Ignoring UPnP retry callback without request data'); return }
  Map entry = upnpRequestQueues.get(getDNI())
  if(entry == null) { logDebug("Ignoring UPnP retry guid=${retry.guid ?: 'none'}; no queue for dni=${getDNI()}"); return }
  synchronized(entry.lock) {
    if(entry.retired == true) {
      logDebug("Ignoring UPnP retry guid=${retry.guid ?: 'none'}; queue is retired generation=${entry.generation}")
      return
    }
    if((retry.lifecycleGeneration as Long) != (entry.generation as Long)) {
      logDebug("Ignoring stale UPnP retry after lifecycle change guid=${retry.guid}")
      return
    }
    retry.guid = UUID.randomUUID().toString()
    retry.phase = 'queued'
    retry.dispatchedAt = null
    retry.deadlineAt = null
    (entry.queue as List<Map>).add(retry)
    logDebug("Accepted UPnP retry guid=${retry.guid} operation=${retry.operation} sidKey=${retry.sidKey} attempt=${retry.attempts} generation=${entry.generation} depth=${(entry.queue as List<Map>).size()}")
  }
  dispatchNextUpnpRequest(entry)
}

Object detachOwnedUpnpSocketLocked(Map entry, Map request) {
  if(entry.active?.guid != request.guid) { return null }
  Object socket = entry.socket
  entry.socket = null
  return socket
}
void closeDetachedUpnpSocket(Object socket, Map request) {
  if(socket != null) {
    try { socket.close() } catch(Exception e) { logDebug("Socket close failed guid=${request.guid}: ${e.message}") }
  }
}
void upnpSubscriptionWatchdog(Map data) {
  String guid = data?.guid as String
  Map entry = upnpRequestQueues.get(getDNI())
  if(entry == null) { return }
  Map request = null
  Object socket = null
  synchronized(entry.lock) {
    request = entry.active as Map
    if(request == null || request.guid != guid || request.phase == 'complete' || request.phase == 'expired') {
      logDebug("Ignoring stale UPnP watchdog guid=${guid} activeGuid=${request?.guid ?: 'none'} phase=${request?.phase ?: 'none'}")
      return
    }
    request.phase = 'expired'
    logDebug("UPnP watchdog expired guid=${guid} operation=${request.operation} sidKey=${request.sidKey} attempt=${request.attempts} generation=${request.lifecycleGeneration}")
    socket = detachOwnedUpnpSocketLocked(entry, request)
    if((request.attempts as Integer) < UPNP_REQUEST_MAX_ATTEMPTS) { scheduleUpnpRetry(request) }
  }
  closeDetachedUpnpSocket(socket, request)
  synchronized(entry.lock) {
    if(entry.active?.guid == guid && request.phase == 'expired') { entry.active = null }
    else { return }
  }
  dispatchNextUpnpRequest(entry)
}
void socketStatus(String status) { logDebug("UPnP raw socket status (diagnostic only): ${status}") }

void invalidateUpnpRequestsForDevice() {
  String dni = getDNI()
  Map entry = getUpnpRequestQueue(dni)
  if(entry == null) { return }
  Object socket = null
  Map active = null
  synchronized(entry.lock) {
    Integer queuedCount = (entry.queue as List<Map>).size()
    String activeGuid = entry.active?.guid as String
    entry.generation = ((entry.generation ?: 0L) as Long) + 1L
    logDebug("Invalidated UPnP queue dni=${dni} generation=${entry.generation} previousActiveGuid=${activeGuid ?: 'none'} discardedQueued=${queuedCount}")
    unschedule('retryUpnpSid1')
    unschedule('retryUpnpSid2')
    unschedule('retryUpnpSid3')
    unschedule('retryUpnpSid4')
    active = entry.active as Map
    if(active != null) {
      active.phase = 'expired'
      socket = detachOwnedUpnpSocketLocked(entry, active)
      unschedule('upnpSubscriptionWatchdog')
    }
    (entry.queue as List<Map>).clear()
  }
  closeDetachedUpnpSocket(socket, active)
  synchronized(entry.lock) {
    if(active != null && entry.active?.guid == active.guid) { entry.active = null }
  }
}
void retireUpnpRequestsForDevice() {
  String dni = getDNI()
  Map entry = getUpnpRequestQueue(dni)
  if(entry == null) { return }
  Object socket = null
  Map active = null
  synchronized(entry.lock) {
    Integer queuedCount = (entry.queue as List<Map>).size()
    String activeGuid = entry.active?.guid as String
    entry.retired = true
    entry.generation = ((entry.generation ?: 0L) as Long) + 1L
    logDebug("Retired UPnP queue dni=${dni} generation=${entry.generation} previousActiveGuid=${activeGuid ?: 'none'} discardedQueued=${queuedCount}")
    unschedule('upnpSubscriptionWatchdog')
    unschedule('retryUpnpSid1')
    unschedule('retryUpnpSid2')
    unschedule('retryUpnpSid3')
    unschedule('retryUpnpSid4')
    active = entry.active as Map
    if(active != null) { active.phase = 'expired'; socket = detachOwnedUpnpSocketLocked(entry, active) }
    (entry.queue as List<Map>).clear()
  }
  closeDetachedUpnpSocket(socket, active)
  synchronized(entry.lock) {
    if(active != null && entry.active?.guid == active.guid) { entry.active = null }
  }
}

Integer cleanupUpnpRequestQueues(Set<String> activeDnis) {
  Integer removed = 0
  upnpRequestQueues.each { String dni, Map entry ->
    if(!activeDnis.contains(dni)) {
      synchronized(entry.lock) {
        if(entry.active == null && (entry.queue as List<Map>).isEmpty()) {
          if(upnpRequestQueues.remove(dni, entry)) { removed++ }
        }
      }
    }
  }
  return removed
}

// Retained legacy schedule/callback names. Old HubAction responses carry no GUID
// and therefore cannot be allowed to mutate current subscription ownership.
void unlockSubscribeMutexAfterTimeout(String sid) { logDebug("Ignoring legacy UPnP fallback for ${sid}") }
void unlockSid1SubscribeMutexAfterTimeoutCallback() { logDebug('Ignoring legacy UPnP fallback callback') }
void unlockSid2SubscribeMutexAfterTimeoutCallback() { logDebug('Ignoring legacy UPnP fallback callback') }
void unlockSid3SubscribeMutexAfterTimeoutCallback() { logDebug('Ignoring legacy UPnP fallback callback') }
void unlockSid4SubscribeMutexAfterTimeoutCallback() { logDebug('Ignoring legacy UPnP fallback callback') }
void unlockSubscribeMutexAfterTimeoutCallback(Map data) { logDebug('Ignoring legacy UPnP fallback callback') }
void unlockUnsubscribeMutexAfterTimeout() { logDebug('Ignoring legacy UPnP fallback') }
void unlockUnsubscribeMutexAfterTimeoutCallback() { logDebug('Ignoring legacy UPnP fallback callback') }

@CompileStatic
void subscribeResubscribeGenericCallback(String sub, String resub, String subId, String domain, HubResponse response) {
  logDebug("Ignoring legacy HubAction subscription callback for ${domain}; no request GUID is available")
}

void upnpUnsubscribeCallbackGeneric(String subId, String domain, HubResponse response) {
  logDebug("Ignoring legacy HubAction unsubscribe callback for ${domain}; no request GUID is available")
}
// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/AVTransport/Event' //sid1
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToAVTransport() {
  String sub = 'subscribeToAVTransport'
  String subId = 'sid1'
  String subPath = '/avt'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_CALLBACK
  upnpSubscribeGeneric(sub, subId, subPath, callback, evtSub)
}

@CompileStatic
void resubscribeToAVTransport() {
  String resub = 'resubscribeToAVTransport'
  String subId = 'sid1'
  String subPath = '/avt'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_CALLBACK
  upnpResubscribeGeneric(resub, subId, subPath, callback, evtSub)
}

@CompileStatic
void subscribeResubscribeToMrAvTCallback(HubResponse response) {
  String sub = 'subscribeToAVTransport'
  String resub = 'resubscribeToAVTransport'
  String subId = 'sid1'
  String domain = MRAVT_EVENTS_DOMAIN
  subscribeResubscribeGenericCallback(sub, resub, subId, domain, response)
}

@CompileStatic
void unsubscribeFromAVTransport() {
  String unsub = 'unsubscribeFromAVTransport'
  String subId = 'sid1'
  String resub = 'resubscribeToAVTransport'
  String evtSub = MRAVT_EVENTS
  String callback = MRAVT_EVENTS_UNSUB_CALLBACK
  upnpUnsubscribeGeneric(unsub, subId, resub, evtSub, callback)
}

@CompileStatic
void unsubscribeToMrAvTCallback(HubResponse response) {
  String subId = 'sid1'
  String domain = MRAVT_EVENTS_DOMAIN
  upnpUnsubscribeCallbackGeneric(subId, domain, response)
}
// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/RenderingControl/Event' //sid2
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToMrRcEvents() {
  String sub = 'subscribeToMrRcEvents'
  String subId = 'sid2'
  String subPath = '/mrc'
  String evtSub = MRRC_EVENTS
  String callback = MRRC_EVENTS_CALLBACK
  upnpSubscribeGeneric(sub, subId, subPath, callback, evtSub)
}

@CompileStatic
void resubscribeToMrRcEvents() {
  String resub = 'resubscribeToMrRcEvents'
  String subId = 'sid2'
  String subPath = '/mrc'
  String evtSub = MRRC_EVENTS
  String callback = MRRC_EVENTS_CALLBACK
  upnpResubscribeGeneric(resub, subId, subPath, callback, evtSub)
}

@CompileStatic
void subscribeResubscribeMrRcCallback(HubResponse response) {
  String sub = 'subscribeToMrRcEvents'
  String resub = 'resubscribeToMrRcEvents'
  String subId = 'sid2'
  String domain = MRRC_EVENTS_DOMAIN
  subscribeResubscribeGenericCallback(sub, resub, subId, domain, response)
}

@CompileStatic
void unsubscribeFromMrRcEvents() {
  String unsub = 'unsubscribeFromMrRcEvents'
  String subId = 'sid2'
  String resub = 'resubscribeToMrRcEvents'
  String evtSub = MRRC_EVENTS
  String callback = MRRC_EVENTS_UNSUB_CALLBACK
  upnpUnsubscribeGeneric(unsub, subId, resub, evtSub, callback)
}

@CompileStatic
void unsubscribeFromMrRcEventsCallback(HubResponse response) {
  String subId = 'sid2'
  String domain = MRRC_EVENTS_DOMAIN
  upnpUnsubscribeCallbackGeneric(subId, domain, response)
}

// /////////////////////////////////////////////////////////////////////////////
// '/ZoneGroupTopology/Event' //sid3
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToZgtEvents() {
  if(!isAppZgtSubscriber()) { return }
  String sub = 'subscribeToZgtEvents'
  String subId = 'sid3'
  String subPath = '/zgt'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_CALLBACK
  upnpSubscribeGeneric(sub, subId, subPath, callback, evtSub)
}

// Entry point used by the parent app when a GROUP_STATUS_MOVED 499 arrives.
// Skips the subscribe entirely when the ZGT subscription is still valid --
// GROUP_STATUS_MOVED does not invalidate the UPnP SID, it only means the
// coordinator changed. Otherwise we batch burst-y callers into a single 2s
// delayed subscribe so storms of 499s coalesce into one network request.
void requestZgtResub() {
  if(!isAppZgtSubscriber()) { return }
  if(subValid('sid3')) { return }
  runIn(2, 'subscribeToZgtEvents', [overwrite: true])
}

@CompileStatic
void resubscribeToZgtEvents() {
  if(!isAppZgtSubscriber()) { return }
  String resub = 'resubscribeToZgtEvents'
  String subId = 'sid3'
  String subPath = '/zgt'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_CALLBACK
  upnpResubscribeGeneric(resub, subId, subPath, callback, evtSub)
}

@CompileStatic
void subscribeResubscribeToZgtCallback(HubResponse response) {
  String sub = 'subscribeToZgtEvents'
  String resub = 'resubscribeToZgtEvents'
  String subId = 'sid3'
  String domain = ZGT_EVENTS_DOMAIN
  subscribeResubscribeGenericCallback(sub, resub, subId, domain, response)
}

@CompileStatic
void unsubscribeFromZgtEvents() {
  String unsub = 'unsubscribeFromZgtEvents'
  String subId = 'sid3'
  String resub = 'resubscribeToZgtEvents'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_UNSUB_CALLBACK
  upnpUnsubscribeGeneric(unsub, subId, resub, evtSub, callback)
}

@CompileStatic
void unsubscribeFromZgtEventsCallback(HubResponse response) {
  String subId = 'sid3'
  String domain = ZGT_EVENTS_DOMAIN
  upnpUnsubscribeCallbackGeneric(subId, domain, response)
}
// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/GroupRenderingControl/Event' //sid4
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToMrGrcEvents() {
  String sub = 'subscribeToMrGrcEvents'
  String subId = 'sid4'
  String subPath = '/mgrc'
  String evtSub = MRGRC_EVENTS
  String callback = MRGRC_EVENTS_CALLBACK
  upnpSubscribeGeneric(sub, subId, subPath, callback, evtSub)
}

@CompileStatic
void resubscribeToMrGrcEvents() {
  String resub = 'resubscribeToMrGrcEvents'
  String subId = 'sid4'
  String subPath = '/mgrc'
  String evtSub = MRGRC_EVENTS
  String callback = MRGRC_EVENTS_CALLBACK
  upnpResubscribeGeneric(resub, subId, subPath, callback, evtSub)
}

@CompileStatic
void subscribeResubscribeToMrGrcCallback(HubResponse response) {
  String sub = 'subscribeToMrGrcEvents'
  String resub = 'resubscribeToMrGrcEvents'
  String subId = 'sid4'
  String domain = MRGRC_EVENTS_DOMAIN
  subscribeResubscribeGenericCallback(sub, resub, subId, domain, response)
}

@CompileStatic
void unsubscribeFromMrGrcEvents() {
  String unsub = 'unsubscribeFromMrGrcEvents'
  String subId = 'sid4'
  String resub = 'resubscribeToMrGrcEvents'
  String evtSub = MRGRC_EVENTS
  String callback = MRGRC_EVENTS_UNSUB_CALLBACK
  upnpUnsubscribeGeneric(unsub, subId, resub, evtSub, callback)
}

@CompileStatic
void unsubscribeFromMrGrcEventsCallback(HubResponse response) {
  String subId = 'sid4'
  String domain = MRGRC_EVENTS_DOMAIN
  upnpUnsubscribeCallbackGeneric(subId, domain, response)
}
// =============================================================================
// End UPnP Subscriptions and Resubscriptions
// =============================================================================


// =============================================================================
// Misc helpers
// =============================================================================
void clearCurrentNextArtistAlbumTrackData() {
  setCurrentArtistName(null)
  setCurrentAlbumName(null)
  setCurrentTrackName(null)
  setCurrentTrackNumber(0)
  setNextArtistAlbumTrack(null, null, null)
}

void clearAlbumArtEvents() {
  String disabled = 'Disabled in preferences'
  queueLocalDeviceEvent('albumArtSmall', disabled)
  queueLocalDeviceEvent('albumArtMedium', disabled)
  queueLocalDeviceEvent('albumArtLarge', disabled)
}

void clearTrackDataEvent() {
  queueLocalDeviceEvent('trackData', '{}')
}
// =============================================================================
// End Misc Helpers
// =============================================================================




// =============================================================================
// Getters and Setters
// =============================================================================
String getLocalApiUrl(){
  String deviceIp = getDeviceDataValue('deviceIp')
  if(deviceIp == null || deviceIp == '') { return null }
  return "https://${deviceIp}:1443/api/v1/"
}
String getLocalUpnpHost(){
  String deviceIp = getDeviceDataValue('deviceIp')
  if(deviceIp == null || deviceIp == '') { return null }
  return "${deviceIp}:1400"
}
String getLocalUpnpUrl(){
  String deviceIp = getDeviceDataValue('deviceIp')
  if(deviceIp == null || deviceIp == '') { return null }
  return "http://${deviceIp}:1400"
}
String getLocalWsUrl(){
  String deviceIp = getDeviceDataValue('deviceIp')
  if(deviceIp == null || deviceIp == '') { return null }
  return "wss://${deviceIp}:1443/websocket/api"
}

List<String> getLocalApiUrlSecondaries(){
  List<String> secondaryDeviceIps = getSecondaryDeviceIps()
  return secondaryDeviceIps.collect{"https://${it}:1443/api/v1/"}
}
List<String> getLocalUpnpHostSecondaries(){
  List<String> secondaryDeviceIps = getSecondaryDeviceIps()
  return secondaryDeviceIps.collect{"${it}:1400"}
}
List<String> getLocalUpnpUrlSecondaries(){
  List<String> secondaryDeviceIps = getSecondaryDeviceIps()
  return secondaryDeviceIps.collect{"http://${it}:1400"}
}

String getRightChannelRincon() {
  return getDeviceDataValue('rightChannelId')
}
void setRightChannelRincon(String rincon) {
  List<String> secondaryIds = getSecondaryIds()
  String companionId = rincon
  if(companionId == getId()) {
    companionId = secondaryIds.size() == 1 ? secondaryIds[0] : null
  }
  Integer secondaryIndex = secondaryIds.indexOf(companionId)
  List<String> secondaryIps = getSecondaryDeviceIps()
  if(!companionId || secondaryIndex < 0 || secondaryIndex >= secondaryIps.size() || !secondaryIps[secondaryIndex]) {
    // Do not retain a stale endpoint when pairing information is incomplete.
    device.removeDataValue('rightChannelId')
    device.removeDataValue('rightChannelDeviceIp')
    return
  }
  setDeviceDataValue('rightChannelId', companionId)
  setRightChannelDeviceIp(secondaryIps[secondaryIndex])
}

/** Select the other front channel, regardless of which speaker owns the player. */
@CompileStatic
static String selectStereoCompanionId(List<Map> members, String playerId) {
  if(members == null || !playerId) { return null }
  Map ownMember = members.find { Map member -> member?.get('id') == playerId }
  List ownChannels = ownMember?.get('channelMap') instanceof List ? (List)ownMember.get('channelMap') : []
  String opposite = ownChannels.contains('LF') ? 'RF' : (ownChannels.contains('RF') ? 'LF' : null)
  if(opposite == null) { return null }
  Map companion = members.find { Map member ->
    Object channels = member?.get('channelMap')
    member?.get('id') != playerId && channels instanceof List && ((List)channels).contains(opposite)
  }
  return companion?.get('id')?.toString()
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

String getFormattedLocalTimestamp() {
  if(location.timeZone) { return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone) }
  return new Date().format('yyyy-MMM-dd h:mm:ss a')
}

void updateEventTimestampDeviceData(String key) {
  if(isDebugLoggingEnabled()) {
    setDeviceDataValue(key, getFormattedLocalTimestamp())
  }
}

void removeEventTimestampDeviceDataKeys() {
  EVENT_TIMESTAMP_KEYS.each { String key ->
    if(device.getDataValue(key) != null) { device.removeDataValue(key) }
  }
}

@CompileStatic
Long getLastInboundAVTransportEventEpoch() {
  Long ts = eventTimestamps.get("${getDeviceDNI()}-lastAVTransportEvent" as String)
  return ts != null ? ts : 0L
}
@CompileStatic
void setLastInboundAVTransportEvent() {
  eventTimestamps.put("${getDeviceDNI()}-lastAVTransportEvent" as String, Instant.now().getEpochSecond())
  updateEventTimestampDeviceData('lastAVTransportEvent')
}
@CompileStatic
Long getLastInboundMrRcEventEpoch() {
  Long ts = eventTimestamps.get("${getDeviceDNI()}-lastMrRcEvent" as String)
  return ts != null ? ts : 0L
}
@CompileStatic
void setLastInboundMrRcEvent() {
  eventTimestamps.put("${getDeviceDNI()}-lastMrRcEvent" as String, Instant.now().getEpochSecond())
  updateEventTimestampDeviceData('lastMrRcEvent')
}
@CompileStatic
Boolean lastMrRcEventWithin(Long seconds) {
  Long now = Instant.now().getEpochSecond()
  Long last = getLastInboundMrRcEventEpoch()
  logDebug("lastMrRcEvent Now: ${now} - last message: ${last}, difference: ${now - last}")
  return (now - last) < seconds
}

@CompileStatic
Long getLastInboundZgtEventEpoch() {
  Long ts = eventTimestamps.get("${getDeviceDNI()}-lastZgtEvent" as String)
  return ts != null ? ts : 0L
}
@CompileStatic
void setLastInboundZgtEvent() {
  eventTimestamps.put("${getDeviceDNI()}-lastZgtEvent" as String, Instant.now().getEpochSecond())
  updateEventTimestampDeviceData('lastZgtEvent')
}
@CompileStatic
Boolean lastZgtEventWithin(Integer seconds) {
  Long now = Instant.now().getEpochSecond()
  Long last = getLastInboundZgtEventEpoch()
  logDebug("lastZgtEvent Now: ${now} - last message: ${last}, difference: ${now - last}")
  return (now - last) < seconds
}

@CompileStatic
Long getLastInboundMrGrcEventEpoch() {
  Long ts = eventTimestamps.get("${getDeviceDNI()}-lastMrGrcEvent" as String)
  return ts != null ? ts : 0L
}
@CompileStatic
void setLastInboundMrGrcEvent() {
  eventTimestamps.put("${getDeviceDNI()}-lastMrGrcEvent" as String, Instant.now().getEpochSecond())
  updateEventTimestampDeviceData('lastMrGrcEvent')
}
@CompileStatic
Boolean lastMrGrcEventWithin(Integer seconds) {
  Long now = Instant.now().getEpochSecond()
  Long last = getLastInboundMrGrcEventEpoch()
  logDebug("lastMrGrcEvent Now: ${now} - last message: ${last}, difference: ${now - last}")
  return (now - last) < seconds
}

@CompileStatic
Long getLastWebsocketEventEpoch() {
  Long ts = eventTimestamps.get("${getDeviceDNI()}-lastWebsocketEvent" as String)
  return ts != null ? ts : 0L
}
@CompileStatic
void setLastWebsocketEvent() {
  eventTimestamps.put("${getDeviceDNI()}-lastWebsocketEvent" as String, Instant.now().getEpochSecond())
  updateEventTimestampDeviceData('lastWebsocketEvent')
}
@CompileStatic
Boolean lastWebsocketEventWithin(Integer seconds) {
  Long now = Instant.now().getEpochSecond()
  Long last = getLastWebsocketEventEpoch()
  logDebug("lastWebsocketEvent Now: ${now} - last message: ${last}, difference: ${now - last}")
  return (now - last) < seconds
}

DeviceWrapper getDevice() { return this.device }

LinkedHashMap getDeviceSettings() { return this.settings }

String getDeviceDataValue(String name) {
  if(this.device != null) {
    return this.device.getDataValue(name)
  }
}
void setDeviceDataValue(String name, String value) {
  if(this.device != null) {
    this.device.updateDataValue(name, value)
  }
}

Object getDeviceCurrentStateValue(String name, Boolean skipCache = false) {
  if(this.device != null) {
    return this.device.currentValue(name, skipCache)
  }
}

void sendDeviceEvent(String name, Object value) { this.device.sendEvent(name:name, value:value) }

private Object getLocalDeviceEventLock(String dni) {
  Object lock = localDeviceEventLocks.get(dni)
  if(lock == null) {
    Object created = new Object()
    Object existing = localDeviceEventLocks.putIfAbsent(dni, created)
    lock = existing ?: created
  }
  return lock
}

/** Queue one local attribute until its fixed maximum-wait drain deadline. */
void queueLocalDeviceEvent(String name, Object value) {
  if(!name || value == null) { return }
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Object lock = getLocalDeviceEventLock(dni)
  synchronized(lock) {
    lastLocalDeviceEvents.putIfAbsent(dni, new ConcurrentHashMap<String, Object>())
    ConcurrentHashMap<String, Object> lastSent = lastLocalDeviceEvents.get(dni)
    pendingLocalDeviceEvents.putIfAbsent(dni, new ConcurrentHashMap<String, Object>())
    ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
    Boolean alreadyPending = pending.containsKey(name)
    Object previous = alreadyPending ? pending.get(name) : lastSent?.get(name)
    Object currentValue = this.device.currentValue(name, true)

    if(alreadyPending && previous == value) {
      logDebug("Local event queue coalesced; dni=${dni}, attribute=${name}, value=${value}, pending=${pending.size()}")
      // Re-arm the callback even for a coalesced value. The pending map, not
      // the scheduled flag, is authoritative about whether work remains.
      scheduleLocalDeviceEventDrain(LOCAL_EVENT_MAX_WAIT_MS)
      return
    }
    if(!alreadyPending && previous == value && currentValue == value) {
      logDebug("Local event queue deduplicated against current device state; dni=${dni}, attribute=${name}, value=${value}")
      return
    }

    pending.put(name, value)
    logDebug("Local event queued; dni=${dni}, attribute=${name}, value=${value}, previous=${previous}, current=${currentValue}, pending=${pending.size()}, scheduled=${state[LOCAL_EVENT_DRAIN_SCHEDULED_KEY] == true}")
    scheduleLocalDeviceEventDrain(LOCAL_EVENT_MAX_WAIT_MS)
  }
}

Boolean shouldQueueLocalDeviceEvent(String name, Object value, Object currentValue) {
  if(value != currentValue) { return true }
  String dni = device.getDeviceNetworkId()
  ConcurrentHashMap<String, Object> localPending = pendingLocalDeviceEvents.get(dni)
  if(localPending?.containsKey(name) && localPending.get(name) != value) { return true }
  ConcurrentHashMap<String, Object> groupPending = pendingGroupDeviceUpdates.get(dni)
  String groupAttribute = name == 'groupVolume' ? '_groupVolume' : (name == 'groupMute' ? '_groupMute' : name)
  return groupPending?.containsKey(groupAttribute) && groupPending.get(groupAttribute) != value
}

/** Ensure a callback exists for all pending local events. A stale state flag
 * must never suppress scheduling; repeated calls overwrite the same callback
 * using the earliest pending deadline. */
private void scheduleLocalDeviceEventDrain(Integer delayMs) {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Object lock = getLocalDeviceEventLock(dni)
  synchronized(lock) {
    ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
    if(!pending || pending.isEmpty()) {
      state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
      state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
      return
    }

    Long currentTime = now() as Long
    Long requestedDueAt = currentTime + Math.max(1, delayMs ?: LOCAL_EVENT_DRAIN_INTERVAL_MS) as Long
    Long existingDueAt = state[LOCAL_EVENT_DRAIN_DUE_AT_KEY] as Long
    Long dueAt = existingDueAt == null ? requestedDueAt : Math.min(existingDueAt, requestedDueAt)
    Long remainingMs = Math.max(1L, dueAt - currentTime)
    Integer scheduleDelay = remainingMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : remainingMs as Integer
    state[LOCAL_EVENT_DRAIN_DUE_AT_KEY] = dueAt
    state[LOCAL_EVENT_DRAIN_SCHEDULED_KEY] = true
    runIn(30, 'verifyPendingPublicationDrains', [overwrite: true])
    logDebug("Scheduling or refreshing local event drain; dni=${dni}, delayMs=${scheduleDelay}, dueAt=${dueAt}, pending=${pending.size()}, previousScheduled=${existingDueAt != null}")
    try {
      runInMillis(scheduleDelay, 'flushPendingLocalDeviceEvents', [overwrite: true])
    } catch(Exception e) {
      state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
      state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
      logWarn("Could not schedule local event drain for dni=${dni}; pending=${pending.size()}: ${e.message}")
    }
  }
}

void flushPendingLocalDeviceEvents() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Object lock = getLocalDeviceEventLock(dni)
  List<Map<String, Object>> batch = []
  synchronized(lock) {
    state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
    state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
    ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
    logDebug("Local event drain claimed; dni=${dni}, pending=${pending?.size() ?: 0}")
    if(!pending || pending.isEmpty()) {
      if(pending != null) { pendingLocalDeviceEvents.remove(dni, pending) }
      logDebug("Local event drain found no pending events; dni=${dni}")
      return
    }

    List<String> names = new ArrayList<String>(pending.keySet()).take(LOCAL_EVENTS_PER_DRAIN)
    names.each { String attrName ->
      Object attrValue = pending.remove(attrName)
      if(attrValue != null) { batch.add([name: attrName, value: attrValue]) }
    }
  }

  ConcurrentHashMap<String, Object> lastSent = lastLocalDeviceEvents.get(dni)
  if(lastSent == null) {
    ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<String, Object>()
    ConcurrentHashMap<String, Object> existing = lastLocalDeviceEvents.putIfAbsent(dni, created)
    lastSent = existing ?: created
  }

  batch.each { Map<String, Object> event ->
    String attrName = event.name as String
    Object attrValue = event.value
    try {
      Object currentValue = this.device.currentValue(attrName, true)
      if(lastSent.containsKey(attrName) && lastSent.get(attrName) == attrValue && currentValue == attrValue) {
        lastSent.put(attrName, attrValue)
        logDebug("Local event drain skipped duplicate sent value still present on device; dni=${dni}, attribute=${attrName}, value=${attrValue}")
        return
      }
      logDebug("Local event send starting; dni=${dni}, attribute=${attrName}, value=${attrValue}, current=${currentValue}, lastSent=${lastSent.get(attrName)}")
      this.device.sendEvent(name: attrName, value: attrValue)
      lastSent.put(attrName, attrValue)
      logDebug("Local event sendEvent returned; dni=${dni}, attribute=${attrName}, value=${attrValue}, currentAfter=${this.device.currentValue(attrName, true)}")
    } catch(Exception e) {
      synchronized(lock) {
        ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
        if(pending == null) {
          ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<String, Object>()
          ConcurrentHashMap<String, Object> existing = pendingLocalDeviceEvents.putIfAbsent(dni, created)
          pending = existing ?: created
        }
        pending.putIfAbsent(attrName, attrValue)
        logWarn("Could not emit local ${attrName} event for dni=${dni}; retaining value=${attrValue} for retry, pending=${pending.size()}: ${e.message}")
      }
    }
  }

  synchronized(lock) {
    ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
    if(!pending || pending.isEmpty()) {
      if(pending != null) { pendingLocalDeviceEvents.remove(dni, pending) }
      state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
      state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
      logDebug("Local event drain completed; dni=${dni}, processed=${batch.size()}, pending=0")
    } else {
      logDebug("Local event drain yielded; dni=${dni}, processed=${batch.size()}, pending=${pending.size()}, nextDelayMs=${LOCAL_EVENT_DRAIN_INTERVAL_MS}")
      scheduleLocalDeviceEventDrain(LOCAL_EVENT_DRAIN_INTERVAL_MS)
    }
  }
}

/** Dispatch the child driver's custom batch command across the Hubitat wrapper boundary. */
void applyChildStateBatch(ChildDeviceWrapper child, String jsonAttributes) {
  child.applyStateBatch(jsonAttributes)
}

private void scheduleComponentChildEventDrain(Integer delayMs) {
  Map pending = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map)state[PENDING_COMPONENT_CHILD_EVENTS_KEY] : [:]
  if(pending.isEmpty()) {
    state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
    state.remove(COMPONENT_CHILD_DRAIN_DUE_AT_KEY)
    return
  }
  Long currentTime = now() as Long
  Long requestedDueAt = currentTime + Math.max(1, delayMs ?: COMPONENT_CHILD_DRAIN_INTERVAL_MS) as Long
  Long existingDueAt = state[COMPONENT_CHILD_DRAIN_DUE_AT_KEY] as Long
  Long dueAt = existingDueAt == null ? requestedDueAt : Math.min(existingDueAt, requestedDueAt)
  Integer scheduleDelay = Math.max(1L, dueAt - currentTime) as Integer
  state[COMPONENT_CHILD_DRAIN_DUE_AT_KEY] = dueAt
  state[COMPONENT_CHILD_DRAIN_SCHEDULED_KEY] = true
  runIn(30, 'verifyPendingPublicationDrains', [overwrite: true])
  try {
    runInMillis(scheduleDelay, 'drainComponentChildEvents', [overwrite: true])
  } catch(Exception e) {
    state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
    state.remove(COMPONENT_CHILD_DRAIN_DUE_AT_KEY)
    logWarn("Could not schedule component-child event drain: ${e.message}")
  }
}

/** Retry promptly when a missing component child has been recreated. */
private void scheduleComponentChildEventDrainNow(String dni) {
  Map pending = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map)state[PENDING_COMPONENT_CHILD_EVENTS_KEY] : [:]
  if(dni && pending.containsKey(dni)) {
    unschedule('drainComponentChildEvents')
    state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
    state.remove(COMPONENT_CHILD_DRAIN_DUE_AT_KEY)
    scheduleComponentChildEventDrain(1)
  }
}

private void resetComponentChildEventRetries() {
  state.remove(COMPONENT_CHILD_RETRY_ATTEMPTS_KEY)
}

private Integer componentChildRetryDelay(Integer attempts) {
  Integer delayMs = COMPONENT_CHILD_DRAIN_INTERVAL_MS
  for(Integer attempt = 1; attempt < attempts && delayMs < COMPONENT_CHILD_RETRY_MAX_MS; attempt++) {
    delayMs = Math.min(delayMs * 2, COMPONENT_CHILD_RETRY_MAX_MS)
  }
  return delayMs
}

private synchronized void queueComponentChildEvent(ChildDeviceWrapper child, String name, Object value) {
  if(child == null || !name || value == null) { return }
  String dni = child.getDeviceNetworkId()
  Map<String, Map<String, Object>> lastSent = state[LAST_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[LAST_COMPONENT_CHILD_EVENTS_KEY] : [:]
  Map<String, Object> lastForChild = lastSent[dni] instanceof Map ? lastSent[dni] : [:]
  Map<String, Map<String, Object>> pending = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_COMPONENT_CHILD_EVENTS_KEY] : [:]
  Map<String, Object> eventsForChild = pending[dni] instanceof Map ? pending[dni] : [:]
  Object prior = eventsForChild.containsKey(name) ? eventsForChild[name] : lastForChild[name]
  if((eventsForChild.containsKey(name) || lastForChild.containsKey(name)) && prior == value) { return }
  eventsForChild[name] = value
  pending[dni] = eventsForChild
  state[PENDING_COMPONENT_CHILD_EVENTS_KEY] = pending
  scheduleComponentChildEventDrain(COMPONENT_CHILD_MAX_WAIT_MS)
}

synchronized void drainComponentChildEvents() {
  state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
  state.remove(COMPONENT_CHILD_DRAIN_DUE_AT_KEY)
  Map<String, Map<String, Object>> pending = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[PENDING_COMPONENT_CHILD_EVENTS_KEY] : [:]
  if(pending.isEmpty()) {
    state.remove(PENDING_COMPONENT_CHILD_EVENTS_KEY)
    return
  }
  Map<String, Map<String, Object>> lastSent = state[LAST_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map<String, Map<String, Object>>)state[LAST_COMPONENT_CHILD_EVENTS_KEY] : [:]
  Map<String, Integer> retryAttempts = state[COMPONENT_CHILD_RETRY_ATTEMPTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Integer>((Map<String, Integer>)state[COMPONENT_CHILD_RETRY_ATTEMPTS_KEY]) : [:]
  Set<String> failedDnis = new LinkedHashSet<String>()
  Set<String> successfulDnis = new LinkedHashSet<String>()
  Integer emitted = 0
  List<String> targetDnis = new ArrayList<String>(pending.keySet())
  for(String dni : targetDnis) {
    if(emitted >= COMPONENT_CHILD_EVENTS_PER_DRAIN) { break }
    Map<String, Object> eventsForChild = pending[dni] instanceof Map ? pending[dni] : [:]
    ChildDeviceWrapper child = getChildDevice(dni)
    if(child == null) {
      logWarn("drainComponentChildEvents: no child device found for DNI ${dni}; keeping queued values")
      failedDnis.add(dni)
      emitted++
      continue
    }
    List<String> eventNames = new ArrayList<String>(eventsForChild.keySet())
    for(String eventName : eventNames) {
      if(emitted >= COMPONENT_CHILD_EVENTS_PER_DRAIN) { break }
      Object eventValue = eventsForChild[eventName]
      try {
        child.sendEvent(name: eventName, value: eventValue)
        Map<String, Object> lastForChild = lastSent[dni] instanceof Map ? lastSent[dni] : [:]
        lastForChild[eventName] = eventValue
        lastSent[dni] = lastForChild
        eventsForChild.remove(eventName)
        successfulDnis.add(dni)
        emitted++
      } catch(Exception e) {
        logWarn("Could not emit ${eventName} to child ${dni}; retaining it for retry: ${e.message}")
        failedDnis.add(dni)
        emitted++
      }
    }
    if(eventsForChild.isEmpty()) { pending.remove(dni) }
  }
  successfulDnis.each { String dni ->
    if(!failedDnis.contains(dni)) { retryAttempts.remove(dni) }
  }
  failedDnis.each { String dni ->
    Integer attempts = retryAttempts[dni] ?: 0
    retryAttempts[dni] = Math.min(attempts + 1, 32)
  }
  if(retryAttempts.isEmpty()) { state.remove(COMPONENT_CHILD_RETRY_ATTEMPTS_KEY) }
  else { state[COMPONENT_CHILD_RETRY_ATTEMPTS_KEY] = retryAttempts }
  if(!lastSent.isEmpty()) { state[LAST_COMPONENT_CHILD_EVENTS_KEY] = lastSent }
  if(pending.isEmpty()) {
    state.remove(PENDING_COMPONENT_CHILD_EVENTS_KEY)
  } else {
    state[PENDING_COMPONENT_CHILD_EVENTS_KEY] = pending
    Integer nextDelay = COMPONENT_CHILD_DRAIN_INTERVAL_MS
    for(String dni : pending.keySet()) {
      Integer attempts = retryAttempts[dni]
      if(attempts != null) { nextDelay = Math.max(nextDelay, componentChildRetryDelay(attempts)) }
    }
    scheduleComponentChildEventDrain(nextDelay)
  }
}

// Favorites/playlists WS delegate helpers — only one player per household subscribes
// NOTE: checkIsFavPlaylistDelegate() has a side effect — claims the delegate slot on first call.
// For read-only checks use: favPlaylistDelegate.get(getHouseholdId()) == device.getDeviceNetworkId()
// IMPORTANT: This method must NOT be named isFavPlaylistDelegate() because Groovy's MOP treats
// isFoo() as the Boolean getter for property "foo", conflicting with the @Field static
// ConcurrentHashMap named "favPlaylistDelegate" and causing ClassCastException.
Boolean checkIsFavPlaylistDelegate() {
  String householdId = getHouseholdId()
  if(!householdId) {
    logWarn('checkIsFavPlaylistDelegate: householdId not yet set, acting as delegate (expected on fresh install only)')
    return true
  }
  String myDni = this.device?.getDeviceNetworkId()
  if(!myDni) { return false }
  // Fast path: already registered as delegate
  String current = favPlaylistDelegate.get(householdId)
  if(current == myDni) { return true }
  // Attempt to claim the delegate slot (atomic, only succeeds if unclaimed)
  String claimed = favPlaylistDelegate.putIfAbsent(householdId, myDni)
  return claimed == null
}

void releaseFavPlaylistDelegate() {
  String householdId = getHouseholdId()
  if(!householdId) { return }
  String myDni = this.device?.getDeviceNetworkId()
  if(myDni) { favPlaylistDelegate.remove(householdId, myDni) }
}

// WS subscription status helpers — in-memory by default, also written to device data when debug logging is on
Boolean isDebugLoggingEnabled() {
  return loggingEnabled('debug')
}

@Field static final List<String> WS_NAMESPACES = ['playback', 'playbackMetadata', 'playlists', 'audioClip', 'groups', 'favorites', 'playerVolume', 'groupVolume']
@Field static final List<String> EVENT_TIMESTAMP_KEYS = ['lastAVTransportEvent', 'lastMrRcEvent', 'lastZgtEvent', 'lastMrGrcEvent', 'lastWebsocketEvent']

String getWsSubscriptionValue(String namespace) {
  String key = "${this.device?.getDeviceNetworkId()}-WS-${namespace}".toString()
  return wsSubscriptionStatus.getOrDefault(key, 'Unsubscribed')
}
void setWsSubscriptionValue(String namespace, String value) {
  String key = "${this.device?.getDeviceNetworkId()}-WS-${namespace}".toString()
  wsSubscriptionStatus.put(key, value)
  // Also persist to device data for visibility when debug logging is enabled
  if(isDebugLoggingEnabled()) {
    setDeviceDataValue("WS-${namespace}", value)
  }
}
void clearWsSubscriptionStatus() {
  String prefix = "${this.device?.getDeviceNetworkId()}-WS-"
  wsSubscriptionStatus.keySet().removeAll { String k -> k.startsWith(prefix) }
  // Also clear device data entries
  WS_NAMESPACES.each { String ns ->
    if(device.getDataValue("WS-${ns}") != null) { device.removeDataValue("WS-${ns}") }
  }
}
void removeWsDeviceDataKeys() {
  WS_NAMESPACES.each { String ns ->
    if(device.getDataValue("WS-${ns}") != null) { device.removeDataValue("WS-${ns}") }
  }
}

void sendChildEvent(ChildDeviceWrapper child, String name, Object value) {
  queueComponentChildEvent(child, name, value)
}

/** Accept a parent-delivered attribute batch and emit it in bounded chunks. */
synchronized void applyStateBatch(String jsonAttributes) {
  Map attributes
  try { attributes = (Map)parseJson(jsonAttributes) }
  catch(Exception e) {
    logWarn("Ignoring malformed child state batch: ${e.message}")
    return
  }
  if(!(attributes instanceof Map) || attributes.isEmpty()) { return }
  Map<String, Object> pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? (Map<String, Object>)state[PENDING_BATCH_EVENTS_KEY] : [:]
  attributes.each { Object name, Object value ->
    if(name != null && value != null) { pending[name.toString()] = value }
  }
  state[PENDING_BATCH_EVENTS_KEY] = pending
  schedulePendingBatchEvents()
}

private void schedulePendingBatchEvents() {
  Map pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map ? (Map)state[PENDING_BATCH_EVENTS_KEY] : [:]
  if(pending.isEmpty()) {
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    state.remove(BATCH_EVENTS_DUE_AT_KEY)
    return
  }
  Long currentTime = now() as Long
  Long requestedDueAt = currentTime + BATCH_EVENTS_INTERVAL_MS
  Long existingDueAt = state[BATCH_EVENTS_DUE_AT_KEY] as Long
  Long dueAt = existingDueAt == null ? requestedDueAt : Math.min(existingDueAt, requestedDueAt)
  Integer scheduleDelay = Math.max(1L, dueAt - currentTime) as Integer
  state[BATCH_EVENTS_DUE_AT_KEY] = dueAt
  state[BATCH_EVENTS_SCHEDULED_KEY] = true
  runIn(30, 'verifyPendingPublicationDrains', [overwrite: true])
  try {
    runInMillis(scheduleDelay, 'drainBatchEvents', [overwrite: true])
  } catch(Exception e) {
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    state.remove(BATCH_EVENTS_DUE_AT_KEY)
    logWarn("Could not schedule batched player event drain: ${e.message}")
  }
}

synchronized void drainBatchEvents() {
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  state.remove(BATCH_EVENTS_DUE_AT_KEY)
  Map<String, Object> pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Object>((Map<String, Object>)state[PENDING_BATCH_EVENTS_KEY]) : [:]
  state.remove(PENDING_BATCH_EVENTS_KEY)
  if(pending.isEmpty()) {
    state.remove(PENDING_BATCH_EVENTS_KEY)
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    return
  }
  List<String> names = new ArrayList<String>(pending.keySet()).take(BATCH_EVENTS_PER_DRAIN)
  Map<String, Object> remaining = new LinkedHashMap<String, Object>(pending)
  names.each { String name ->
    try {
      device.sendEvent(name: name, value: pending[name])
      remaining.remove(name)
    } catch(Exception e) {
      logWarn("Could not emit batched ${name} event; retaining it for retry: ${e.message}")
    }
  }
  Map<String, Object> arrivals = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? new LinkedHashMap<String, Object>((Map<String, Object>)state[PENDING_BATCH_EVENTS_KEY]) : [:]
  remaining.putAll(arrivals)
  if(remaining.isEmpty()) {
    state.remove(PENDING_BATCH_EVENTS_KEY)
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  } else {
    state[PENDING_BATCH_EVENTS_KEY] = remaining
    schedulePendingBatchEvents()
  }
}

/** Re-arm publication callbacks if a persisted pending queue outlives its job. */
void verifyPendingPublicationDrains() {
  Map batch = state[PENDING_BATCH_EVENTS_KEY] instanceof Map ? (Map)state[PENDING_BATCH_EVENTS_KEY] : [:]
  String dni = device?.getDeviceNetworkId()
  ConcurrentHashMap<String, Object> local = dni ? pendingLocalDeviceEvents.get(dni) : null
  ConcurrentHashMap<String, Object> group = dni ? pendingGroupDeviceUpdates.get(dni) : null
  Map component = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map)state[PENDING_COMPONENT_CHILD_EVENTS_KEY] : [:]
  if(batch.isEmpty()) {
    state.remove(PENDING_BATCH_EVENTS_KEY)
    state.remove(BATCH_EVENTS_SCHEDULED_KEY)
    state.remove(BATCH_EVENTS_DUE_AT_KEY)
  }
  if(local == null || local.isEmpty()) {
    state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
    state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
  }
  if(!batch.isEmpty()) { schedulePendingBatchEvents() }
  if(local != null && !local.isEmpty()) { scheduleLocalDeviceEventDrain(LOCAL_EVENT_DRAIN_INTERVAL_MS) }
  if(group != null && !group.isEmpty()) { scheduleGroupDeviceUpdateDrain(GROUP_DEVICE_UPDATE_MAX_WAIT_MS) }
  if(!component.isEmpty()) { scheduleComponentChildEventDrain(COMPONENT_CHILD_DRAIN_INTERVAL_MS) }
  if(!batch.isEmpty() || (local != null && !local.isEmpty()) ||
      (group != null && !group.isEmpty()) || !component.isEmpty()) {
    runIn(30, 'verifyPendingPublicationDrains', [overwrite: true])
  }
}

Object getChildCurrentStateValue(DeviceWrapper child, String name, Boolean skipCache = false) {
  if(child != null) {child.currentValue(name, skipCache)}
}

String getChildDeviceDataValue(DeviceWrapper child, String name) {
  if(child != null) { return child.getDataValue(name) }
}

String getDeviceDNI() { return this.device.getDeviceNetworkId() }

Object getAtomicStateValue(String name) {
  return runtimeState[name]
}

void setAtomicStateValue(String name, Object value) {
  if(value == null) { runtimeState.remove(name) }
  else { runtimeState[name] = value }
}

@CompileStatic
String getId() { return getDeviceDataValue('id')
}
@CompileStatic
void setId(String id) { setDeviceDataValue('id', id)
}
List<String> getSecondaryIds() {
  return getDeviceDataValue('secondaryIds')?.tokenize(',') ?: []
}
void setSecondaryIds(List<String> ids) {
  setDeviceDataValue('secondaryIds', ids.join(','))
}
Boolean hasSecondaries() {
  String ids = getDeviceDataValue('secondaryIds')
  return ids != null && ids.size() > 0
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
  return getDeviceDataValue('secondaryDeviceIps')?.tokenize(',') ?: []
}
void setSecondaryDeviceIps(List<String> ipAddresses) {
  setDeviceDataValue('secondaryDeviceIps', ipAddresses.join(','))
}

String getGroupId() {
  return getDeviceDataValue('groupId')
}
void setGroupId(String groupId) {
  String oldGroupId = getGroupId()
  if(oldGroupId == groupId) { return }
  setDeviceDataValue('groupId', groupId)
  queueLocalDeviceEvent('groupId', groupId)
  // Always re-subscribe when groupId changes — playback/playbackMetadata subscriptions
  // are group-scoped, so the old subscription is stale for the new group.
  if(getIsGroupCoordinator() == true) {
    runIn(1, 'subscribeToPlaybackDebounce', [data:[groupId:groupId], overwrite:true])
  }
}

void subscribeToPlaybackDebounce(Map data = null) {
  if(getIsGroupCoordinator() != true) {
    logDebug('Skipping delayed group subscription because this player is no longer the coordinator')
    return
  }
  String groupId = data?.groupId
  if(groupId != null && groupId != '') {
    subscribeToPlayback(groupId)
    subscribeToPlaybackMetadata(groupId)
    subscribeToGroupVolume(groupId)
  }
}
@CompileStatic
Boolean isCurrentlySubcribedToCoodinatorWS() {
  return getWsSubscriptionValue('playback') == 'Subscribed' && getWsSubscriptionValue('playbackMetadata') == 'Subscribed' && getWsSubscriptionValue('groupVolume') == 'Subscribed'
}
@CompileStatic
Boolean isCurrentlySubcribedToPlaylistWS() { return getWsSubscriptionValue('playlists') == 'Subscribed' }
@CompileStatic
Boolean isCurrentlySubcribedToAudioClipWS() { return getWsSubscriptionValue('audioClip') == 'Subscribed' }
@CompileStatic
Boolean isCurrentlySubcribedToGroupsWS() { return getWsSubscriptionValue('groups') == 'Subscribed' }
@CompileStatic
Boolean isCurrentlySubcribedToFavoritesWS() { return getWsSubscriptionValue('favorites') == 'Subscribed' }
@CompileStatic
Boolean isCurrentlySubcribedToPlayerVolumeWS() { return getWsSubscriptionValue('playerVolume') == 'Subscribed' }
@CompileStatic
Boolean isCurrentlySubcribedToGroupVolumeWS() { return getWsSubscriptionValue('groupVolume') == 'Subscribed' }

String getGroupName() {
  return this.device.currentValue('groupName',true)
}
void setGroupName(String groupName) {
  if(!shouldQueueLocalDeviceEvent('groupName', groupName, getGroupName())) { return }
  queueLocalDeviceEvent('groupName', groupName)
}

Boolean getIsGrouped() {
  return this.device.currentValue('isGrouped') == 'on'
}
void setIsGrouped(Boolean isGrouped) {
  String newValue = isGrouped ? 'on' : 'off'
  if(!shouldQueueLocalDeviceEvent('isGrouped', newValue, this.device.currentValue('isGrouped'))) { return }
  queueLocalDeviceEvent('isGrouped', newValue)
}

Integer getGroupMemberCount() {
  return this.device.currentValue('groupMemberCount') as Integer
}
void setGroupMemberCount(Integer groupMemberCount) {
  if(!shouldQueueLocalDeviceEvent('groupMemberCount', groupMemberCount,
      this.device.currentValue('groupMemberCount') as Integer)) { return }
  queueLocalDeviceEvent('groupMemberCount', groupMemberCount)
}

String getGroupCoordinatorId() {
  return getDeviceDataValue('groupCoordinatorId')
}
@CompileStatic
void setGroupCoordinatorId(String groupCoordinatorId) {
  String previousGroupCoordinator = getGroupCoordinatorId()
  Boolean previouslyWasGroupCoordinator = getIsGroupCoordinator()
  if(groupCoordinatorId != previousGroupCoordinator) {
    logDebug("setGroupCoordinatorId ${groupCoordinatorId}")
    setDeviceDataValue('groupCoordinatorId', groupCoordinatorId)
  }
  // Internal device data can be correct while Hubitat's published Current
  // State is stale (for example after a lost local-event drain callback).
  if(groupCoordinatorId != previousGroupCoordinator ||
      this.device.currentValue('groupCoordinatorId', true) != groupCoordinatorId) {
    queueLocalDeviceEvent('groupCoordinatorId', groupCoordinatorId)
  }
  Boolean isGroupCoordinator = getId() == groupCoordinatorId
  // Keep the persisted coordinator role derived from the ID on every topology
  // update, even when the ID itself did not change.
  setIsGroupCoordinator(isGroupCoordinator)

  // Signal setGroupPlayerIds() to re-evaluate group devices even when the
  // player list is unchanged. Avoids calling parentUpdateGroupDevices() here
  // directly, which would use potentially stale player data if both coordinator
  // and membership change in the same event.
  if(isGroupCoordinator != previouslyWasGroupCoordinator) {
    setDeviceDataValue('coordinatorStatusChanged', 'true')
  }

  if(isGroupCoordinator && !previouslyWasGroupCoordinator) {
    if(!subValid('sid1')) {
      clearCurrentPlayingStates()
      subscribeToCoordinatorSubs()
      logDebug('Just became group coordinator, subscribing to AVT.')
    }
  } else if(!isGroupCoordinator && previouslyWasGroupCoordinator) {
      logDebug("Just added to group!")
      unsubscribeFromAVTransport()
  } else {logDebug("Group coordinator status has not changed.")}
}

void subscribeToCoordinatorSubs() {
  runIn(2, 'subscribeToAVTransport', [overwrite: true])
  runIn(2, 'subscribeToWsEvents', [overwrite: true])
  runIn(2, 'getPlaybackMetadataStatus', [overwrite: true])
  runIn(2, 'playerGetGroupsFull', [overwrite: true])
}

String getGroupCoordinatorName() {
  return this.device.currentValue('groupCoordinatorName')
}
void setGroupCoordinatorName(String groupCoordinatorName) {
  if(shouldQueueLocalDeviceEvent('groupCoordinatorName', groupCoordinatorName, getGroupCoordinatorName())) {
    queueLocalDeviceEvent('groupCoordinatorName', groupCoordinatorName)
  }
}

Boolean getIsGroupCoordinator() {
  return getDeviceDataValue('isGroupCoordinator') == 'true'
}
void setIsGroupCoordinator(Boolean isGroupCoordinator) {
  Boolean previouslyWasGroupCoordinator = getIsGroupCoordinator()
  String publicValue = isGroupCoordinator ? 'on' : 'off'
  setDeviceDataValue('isGroupCoordinator', isGroupCoordinator.toString())
  if(isGroupCoordinator != previouslyWasGroupCoordinator ||
      this.device.currentValue('isGroupCoordinator', true) != publicValue) {
    queueLocalDeviceEvent('isGroupCoordinator', publicValue)
  }
}

Boolean isGroupedAndCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator()
}

Boolean isGroupedAndNotCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator() == false
}

List<String> getGroupPlayerIds() {
  return getDeviceDataValue('groupPlayerIds')?.tokenize(',') ?: []
}
void setGroupPlayerIds(List<String> groupPlayerIds, Boolean publishMembership = true) {
  String newValue = groupPlayerIds.join(',')
  String oldValue = getDeviceDataValue('groupPlayerIds')
  Boolean coordinatorChanged = getDeviceDataValue('coordinatorStatusChanged') == 'true'
  if(coordinatorChanged) { setDeviceDataValue('coordinatorStatusChanged', '') }
  if(newValue == oldValue && !coordinatorChanged) { return }
  if(newValue != oldValue) {
    Boolean wasGrouped = oldValue ? oldValue.tokenize(',').size() > 1 : false
    Boolean isNowGrouped = groupPlayerIds.size() > 1
    setDeviceDataValue('groupPlayerIds', newValue)
    queueLocalDeviceEvent('isGrouped', isNowGrouped ? 'on' : 'off')
    queueLocalDeviceEvent('groupMemberCount', groupPlayerIds.size())
    // Subscribe/unsubscribe sid4 (GroupRenderingControl) on group transitions
    if(isNowGrouped && !wasGrouped) {
      subscribeToMrGrcEvents()
    } else if(!isNowGrouped && wasGrouped) {
      unsubscribeFromMrGrcEvents()
    }
  }
  logDebug('Updating group device with new group membership information')
  if(publishMembership) { parentUpdateGroupDevices(getId(), groupPlayerIds) }
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
  String newValue = groupPlayerNames.toString()
  if(!shouldQueueLocalDeviceEvent('groupMemberNames', newValue, getGroupMemberNames())) { return }
  queueLocalDeviceEvent('groupMemberNames', newValue)
}

@CompileStatic
void setAlbumArtURI(String albumArtURI, Boolean isPlayingLocalTrack) {
  Boolean artEventsDisabled = getDisableAlbumArtEvents()
  String uri = '<br>'
  String smallUri = ''
  String mediumUri = ''
  String largeUri = ''

  if(albumArtURI.startsWith('/') && !isPlayingLocalTrack) {
    String baseUrl = "${getLocalUpnpUrl()}${albumArtURI}"
    uri += "<img src=\"${baseUrl}\" width=\"200\" height=\"200\" >"
    if(!artEventsDisabled) {
      // Generate URLs for different sizes based on Sonos conventions
      smallUri = baseUrl.contains('?') ? "${baseUrl}&x=60&y=60" : "${baseUrl}?x=60&y=60"
      mediumUri = baseUrl.contains('?') ? "${baseUrl}&x=200&y=200" : "${baseUrl}?x=200&y=200"
      largeUri = baseUrl.contains('?') ? "${baseUrl}&x=600&y=600" : "${baseUrl}?x=600&y=600"
    }
  } else if(!isPlayingLocalTrack && albumArtURI) {
    // Check if this is J River format with multiple concatenated URLs
    if(albumArtURI.contains('http://') && albumArtURI.indexOf('http://', 7) > 0) {
      // J River style: multiple URLs concatenated together
      // Format: http://IP:PORT/AArl/file.jpghttp://IP:PORT/AArm/file.jpghttp://IP:PORT/AArs/file.jpg
      // May also include /AArt/ (tiny) which we explicitly ignore
      List<String> urls = parseJRiverAlbumArtUrls(albumArtURI)
      // Filter out tiny artwork (/AArt/)
      urls = urls.findAll { !it.contains('/AArt/') }

      if(urls.size() > 0) {
        if(!artEventsDisabled) {
          // Find URLs by size preference: large, medium, small
          largeUri = urls.find { it.contains('/AArl/') } ?: urls[0]
          mediumUri = urls.find { it.contains('/AArm/') } ?: urls.find { it.contains('/AArl/') } ?: urls[0]
          smallUri = urls.find { it.contains('/AArs/') } ?: urls.find { it.contains('/AArm/') } ?: urls[0]
        }
        String imgUrl = urls.find { it.contains('/AArm/') } ?: urls[0]
        uri += "<img src=\"${imgUrl}\" width=\"200\" height=\"200\" >"
      } else {
        // Fallback if all URLs were filtered out
        uri += "<img src=\"${albumArtURI}\" width=\"200\" height=\"200\" >"
        if(!artEventsDisabled) {
          smallUri = albumArtURI
          mediumUri = albumArtURI
          largeUri = albumArtURI
        }
      }
    } else {
      // Single URL format (Spotify, etc.) - use as-is for all sizes
      uri += "<img src=\"${albumArtURI}\" width=\"200\" height=\"200\" >"
      if(!artEventsDisabled) {
        smallUri = albumArtURI
        mediumUri = albumArtURI
        largeUri = albumArtURI
      }
    }
  }

  // Skip albumArtURI if nothing changed
  if(shouldQueueLocalDeviceEvent('albumArtURI', uri, this.device.currentValue('albumArtURI'))) {
    queueLocalDeviceEvent('albumArtURI', uri)
  }
  if(!artEventsDisabled) {
    Boolean artChanged = shouldQueueLocalDeviceEvent('albumArtSmall', smallUri, this.device.currentValue('albumArtSmall')) ||
        shouldQueueLocalDeviceEvent('albumArtMedium', mediumUri, this.device.currentValue('albumArtMedium')) ||
        shouldQueueLocalDeviceEvent('albumArtLarge', largeUri, this.device.currentValue('albumArtLarge'))
    if(artChanged) {
      queueLocalDeviceEvent('albumArtSmall', smallUri)
      queueLocalDeviceEvent('albumArtMedium', mediumUri)
      queueLocalDeviceEvent('albumArtLarge', largeUri)
    }
  }
  // Queue for batched group device update
  Map groupUpdate = [albumArtURI: uri]
  if(!artEventsDisabled) {
    groupUpdate.albumArtSmall = smallUri
    groupUpdate.albumArtMedium = mediumUri
    groupUpdate.albumArtLarge = largeUri
  }
  queueGroupDeviceUpdate(groupUpdate)
}

@CompileStatic
List<String> parseJRiverAlbumArtUrls(String concatenatedUrls) {
  // Parse J River's concatenated album art URLs
  // Format: http://IP:PORT/AArl/file.jpghttp://IP:PORT/AArm/file.jpghttp://IP:PORT/AArs/file.jpg
  List<String> urls = []

  // Split by finding 'http://' or 'https://' after the first character
  Integer startIdx = 0
  while(startIdx < concatenatedUrls.length()) {
    // Check if current URL starts with https:// (8 chars) or http:// (7 chars)
    Integer offset = concatenatedUrls.substring(startIdx).startsWith('https://') ? 8 : 7

    // Find next occurrence of either protocol
    Integer nextHttpIdx = concatenatedUrls.indexOf('http://', startIdx + offset)
    Integer nextHttpsIdx = concatenatedUrls.indexOf('https://', startIdx + offset)

    // Use whichever comes first (and exists)
    Integer nextIdx = -1
    if(nextHttpIdx > 0 && nextHttpsIdx > 0) {
      nextIdx = Math.min(nextHttpIdx, nextHttpsIdx)
    } else if(nextHttpIdx > 0) {
      nextIdx = nextHttpIdx
    } else if(nextHttpsIdx > 0) {
      nextIdx = nextHttpsIdx
    }

    if(nextIdx > 0) {
      urls.add(concatenatedUrls.substring(startIdx, nextIdx))
      startIdx = nextIdx
    } else {
      urls.add(concatenatedUrls.substring(startIdx))
      break
    }
  }

  return urls
}

@CompileStatic
void setAudioSource(String trackUri, Boolean isPlayingLocalTrack) {
  String audioSourceUrl = ''

  if(trackUri && trackUri.startsWith('http') && !isPlayingLocalTrack) {
    // Direct HTTP URL - this is the actual audio source
    audioSourceUrl = trackUri
  } else if(trackUri && trackUri.startsWith('/') && !isPlayingLocalTrack) {
    // Local path on Sonos device
    audioSourceUrl = "${getLocalUpnpUrl()}${trackUri}"
  } else if(trackUri && !isPlayingLocalTrack) {
    // Other protocols (x-rincon-stream, x-sonosapi, etc.) - store as-is for reference
    audioSourceUrl = trackUri
  } else {
    audioSourceUrl = 'Not Available'
  }

  Map announcement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  if(announcement?.activeObserved == true && audioSourceUrl && audioSourceUrl != 'Not Available' &&
      audioSourceUrl != announcement.uri?.toString()) {
    String correlationId = announcement.correlationId?.toString()
    logDebug("Observed external source replacing group announcement ${correlationId} on ${getId()}; expected=${announcement.uri}, observed=${audioSourceUrl}")
    publishGroupAnnouncementProgress(correlationId, 'INTERRUPTED', [
      externalPlayback: true,
      replacementUri: audioSourceUrl,
      playbackState: runtimeState.playbackState?.toString(),
      sourceUri: device.currentValue('audioSource')?.toString()
    ])
    runtimeState.remove('groupAnnouncementStream')
  }
  Boolean audioSourceChanged = shouldQueueLocalDeviceEvent('audioSource', audioSourceUrl,
      this.device.currentValue('audioSource'))
  if(!audioSourceChanged) { return }
  queueLocalDeviceEvent('audioSource', audioSourceUrl)
  // Queue for batched group device update
  queueGroupDeviceUpdate([audioSource: audioSourceUrl])
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
  if(!shouldQueueLocalDeviceEvent('currentFavorite', uri, getCurrentFavorite())) { return }
  queueLocalDeviceEvent('currentFavorite', uri)
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentFavorite: uri])
}
String getCurrentFavorite() {
  return this.device.currentValue('currentFavorite', true)
}

void setCurrentPlaylist(String foundPlaylistId, String foundPlaylistName, Boolean isPlaylist) {
  String value = 'No playlist playing'
  if(isPlaylist && foundPlaylistName) {
    value = "Playlist #${foundPlaylistId} ${foundPlaylistName}"
  }
  setCurrentPlaylist(value)
}

@CompileStatic
void setCurrentPlaylist(String uri) {
  if(!shouldQueueLocalDeviceEvent('currentPlaylist', uri, getCurrentPlaylist())) { return }
  queueLocalDeviceEvent('currentPlaylist', uri)
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentPlaylist: uri])
}

String getCurrentPlaylist() {
  return this.device.currentValue('currentPlaylist', true)
}

@CompileStatic
String normalizeTransportStatus(String transportState) {
  if(transportState == null) { return null }
  String normalizedState = transportState.trim().toUpperCase(Locale.US)
  if(normalizedState == 'PLAYING') { return 'playing' }
  if(normalizedState == 'PAUSED_PLAYBACK') { return 'paused' }
  if(normalizedState == 'STOPPED' || normalizedState == 'NO_MEDIA_PRESENT') { return 'stopped' }
  return null
}

@CompileStatic
void setStatusTransportStatus(String transportState, String source = 'transport status update') {
  if(transportState == null || transportState.trim() == '') {
    logWarn("Ignoring empty Sonos TransportState from ${source}")
    return
  }
  String normalizedState = transportState.trim().toUpperCase(Locale.US)
  if(normalizedState == 'TRANSITIONING') {
    logDebug("Ignoring transient Sonos TransportState=TRANSITIONING from ${source}; preserving ${getTransportStatus() ?: 'unknown'}")
    return
  }
  String status = normalizeTransportStatus(transportState)
  if(status == null) {
    logWarn("Ignoring unsupported Sonos TransportState='${transportState}' from ${source}")
    return
  }
  if(!shouldQueueLocalDeviceEvent('status', status, this.device.currentValue('status')) &&
      !shouldQueueLocalDeviceEvent('transportStatus', status, getTransportStatus())) { return }
  queueLocalDeviceEvent('status', status)
  queueLocalDeviceEvent('transportStatus', status)
  // Queue for batched group device update
  queueGroupDeviceUpdate([status: status, transportStatus: status])
}
String getTransportStatus() {
  return this.device.currentValue('transportStatus')
}

Integer getPlayerVolume() {
  return (this.device.currentValue('volume') as Integer) ?: 0
}
void setPlayerVolume(Integer volume) {
  Integer oldVolume = getPlayerVolume()
  if(!shouldQueueLocalDeviceEvent('volume', volume, oldVolume) &&
      !shouldQueueLocalDeviceEvent('level', volume, this.device.currentValue('level') as Integer)) { return }
  queueLocalDeviceEvent('level', volume)
  queueLocalDeviceEvent('volume', volume)
  if(volume > 0) { runtimeState.restoreLevelAfterUnmute = volume }
}
void setBalanceState(Integer balance) {
  Integer oldBalance = this.device.currentValue('balance') as Integer
  if(!shouldQueueLocalDeviceEvent('balance', balance, oldBalance)) { return }
  queueLocalDeviceEvent('balance', balance)
}
Boolean getIsMuted() {
  return getMuteState() == 'muted'
}
String getMuteState() {
  return this.device.currentValue('mute')
}
void setMuteState(String muted) {
  String observedMutedState = getMuteState()
  String previousMutedState = observedMutedState != null ? observedMutedState : 'unmuted'
  Boolean restoreEnabled = getEnableAirPlayUnmuteVolumeFix()
  Object restoreLevel = runtimeState.restoreLevelAfterUnmute
  logDebug("setMuteState entered; previous=${previousMutedState}, target=${muted}, unmuteRestoreEnabled=${restoreEnabled}, restoreLevel=${restoreLevel ?: 'unset'}")
  sendChildEvent(getMuteControlChild(), 'switch', muted == 'muted' ? 'on' : 'off')
  if(!shouldQueueLocalDeviceEvent('mute', muted, observedMutedState)) {
    logDebug("setMuteState deduplicated parent event; current=${previousMutedState}, target=${muted}")
    return
  }
  if(muted == 'unmuted' && previousMutedState == 'muted' && restoreEnabled) {
    logDebug("Unmute volume restoration beginning; restoreLevel=${restoreLevel ?: 'unset'}")
    Integer restoreVolume = null
    if(restoreLevel instanceof Number) {
      try {
        restoreVolume = new BigDecimal(restoreLevel.toString()).intValueExact()
      } catch(Exception ignored) { }
    }
    if(restoreVolume != null && restoreVolume >= 0 && restoreVolume <= 100) {
      logDebug("Restoring volume after unmute event to level: ${restoreVolume}")
      setLevel(restoreVolume as BigDecimal)
      logDebug('Unmute volume restoration dispatched')
    } else {
      logWarn('Skipping AirPlay unmute volume restore because the saved restore level is unavailable or invalid')
    }
  }
  queueLocalDeviceEvent('mute', muted)
  logDebug("setMuteState queued parent mute event; value=${muted}")
}

void setBassState(Integer bass) {
  if(!shouldQueueLocalDeviceEvent('bass', bass, this.device.currentValue('bass') as Integer)) { return }
  queueLocalDeviceEvent('bass', bass)
}
void setTrebleState(Integer treble) {
  if(!shouldQueueLocalDeviceEvent('treble', treble, this.device.currentValue('treble') as Integer)) { return }
  queueLocalDeviceEvent('treble', treble)
}
void setLoudnessState(String loudness) {
  if(!shouldQueueLocalDeviceEvent('loudness', loudness, this.device.currentValue('loudness'))) { return }
  queueLocalDeviceEvent('loudness', loudness)
}

@CompileStatic
void setPlayMode(String playMode){
  String oldRepeatOne = getCurrentRepeatOneMode()
  String oldRepeatAll = getCurrentRepeatAllMode()
  String oldShuffle = getCurrentShuffleMode()
  String newRepeatOne, newRepeatAll, newShuffle
  switch(playMode) {
    case 'NORMAL':             newRepeatOne = 'off'; newRepeatAll = 'off'; newShuffle = 'off'; break
    case 'REPEAT_ALL':         newRepeatOne = 'off'; newRepeatAll = 'on';  newShuffle = 'off'; break
    case 'REPEAT_ONE':         newRepeatOne = 'on';  newRepeatAll = 'off'; newShuffle = 'off'; break
    case 'SHUFFLE_NOREPEAT':   newRepeatOne = 'off'; newRepeatAll = 'off'; newShuffle = 'on';  break
    case 'SHUFFLE':            newRepeatOne = 'off'; newRepeatAll = 'on';  newShuffle = 'on';  break
    case 'SHUFFLE_REPEAT_ONE': newRepeatOne = 'on';  newRepeatAll = 'off'; newShuffle = 'on';  break
    default: return
  }
  if(newRepeatOne == oldRepeatOne && newRepeatAll == oldRepeatAll && newShuffle == oldShuffle &&
      !shouldQueueLocalDeviceEvent('currentRepeatOneMode', newRepeatOne, oldRepeatOne) &&
      !shouldQueueLocalDeviceEvent('currentRepeatAllMode', newRepeatAll, oldRepeatAll) &&
      !shouldQueueLocalDeviceEvent('currentShuffleMode', newShuffle, oldShuffle)) { return }
  queueLocalDeviceEvent('currentRepeatOneMode', newRepeatOne)
  queueLocalDeviceEvent('currentRepeatAllMode', newRepeatAll)
  queueLocalDeviceEvent('currentShuffleMode', newShuffle)
  sendChildEvent(getRepeatOneControlChild(), 'switch', newRepeatOne)
  sendChildEvent(getRepeatAllControlChild(), 'switch', newRepeatAll)
  sendChildEvent(getShuffleControlChild(), 'switch', newShuffle)
  // Queue for batched group device update
  queueGroupDeviceUpdate([
    currentRepeatOneMode: newRepeatOne,
    currentRepeatAllMode: newRepeatAll,
    currentShuffleMode: newShuffle
  ])
}

String getCurrentRepeatOneMode() { return this.device.currentValue('currentRepeatOneMode') }
String getCurrentRepeatAllMode() { return this.device.currentValue('currentRepeatAllMode') }
String getCurrentShuffleMode() { return this.device.currentValue('currentShuffleMode') }

@CompileStatic
void setCrossfadeMode(String currentCrossfadeMode) {
  if(!shouldQueueLocalDeviceEvent('currentCrossfadeMode', currentCrossfadeMode, getCrossfadeMode())) { return }
  queueLocalDeviceEvent('currentCrossfadeMode', currentCrossfadeMode)
  sendChildEvent(getCrossfadeControlChild(), 'switch', currentCrossfadeMode)
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentCrossfadeMode: currentCrossfadeMode])
}
String getCrossfadeMode() { return this.device.currentValue('currentCrossfadeMode') }

@CompileStatic
void setCurrentTrackDuration(String currentTrackDuration){
  if(shouldQueueLocalDeviceEvent('currentTrackDuration', currentTrackDuration, getCurrentTrackDuration())) {
    queueLocalDeviceEvent('currentTrackDuration', currentTrackDuration)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentTrackDuration: currentTrackDuration])
}
String getCurrentTrackDuration() { return this.device.currentValue('currentTrackDuration') }

/** Publish whole elapsed seconds and the Unix-millisecond sample time for meaningful playback changes. */
void setTrackPositionFromPlaybackStatus(Map eventData, String playbackState, Long observedAt) {
  if(!(eventData instanceof Map)) { return }
  Map previous = runtimeState[TRACK_POSITION_SNAPSHOT_KEY] instanceof Map
      ? (Map)runtimeState[TRACK_POSITION_SNAPSHOT_KEY] : null
  Long positionMillis = toLongValue(eventData.positionMillis)
  String itemId = eventData.itemId?.toString()
  Map snapshot = [playbackState: playbackState, itemId: itemId,
      positionMillis: positionMillis, observedAt: observedAt]
  runtimeState[TRACK_POSITION_SNAPSHOT_KEY] = snapshot

  Boolean trackChanged = previous != null && itemId != null && itemId != previous.itemId?.toString()
  Boolean stateChanged = previous == null || playbackState != previous.playbackState?.toString()
  Boolean positionDiscontinuity = false
  Long previousPositionMillis = toLongValue(previous?.positionMillis)
  Long previousObservedAt = toLongValue(previous?.observedAt)
  if(previous != null && positionMillis != null && previousPositionMillis != null &&
      itemId != null && itemId == previous.itemId?.toString() && !stateChanged) {
    if(playbackState == 'PLAYBACK_STATE_PLAYING' && previousObservedAt != null) {
      Long elapsedMillis = Math.max(0L, observedAt - previousObservedAt)
      Long expectedPositionMillis = previousPositionMillis + elapsedMillis
      positionDiscontinuity = Math.abs(positionMillis - expectedPositionMillis) > TRACK_POSITION_SEEK_TOLERANCE_MILLIS
    } else if(playbackState == 'PLAYBACK_STATE_PAUSED') {
      positionDiscontinuity = Math.abs(positionMillis - previousPositionMillis) > TRACK_POSITION_SEEK_TOLERANCE_MILLIS
    }
  }

  Boolean terminalState = playbackState in ['PLAYBACK_STATE_IDLE', 'PLAYBACK_STATE_STOPPED']
  Boolean shouldPublish = stateChanged || trackChanged || positionDiscontinuity
  if(!shouldPublish) { return }
  if(positionMillis == null && !terminalState) {
    logDebug("Ignoring track position update without positionMillis for state=${playbackState}")
    return
  }
  Long positionSeconds = positionMillis != null ? positionMillis.intdiv(1000L) : 0L
  setTrackPosition(positionSeconds, observedAt)
}

void setTrackPosition(Long positionSeconds, Long updatedAt) {
  if(positionSeconds == null || updatedAt == null) { return }
  if(getEnableTrackPositionUpdates()) {
    queueLocalDeviceEvent('trackPosition', positionSeconds)
    queueLocalDeviceEvent('trackPositionUpdated', updatedAt)
  }
  // Keep group devices independent: their own preference decides whether they
  // apply these coordinator updates, even when this player suppresses its event.
  queueGroupDeviceUpdate([trackPosition: positionSeconds, trackPositionUpdated: updatedAt])
}

Long getTrackPosition() { return this.device.currentValue('trackPosition') as Long }
Long getTrackPositionUpdated() { return this.device.currentValue('trackPositionUpdated') as Long }

@CompileStatic
void setCurrentArtistName(String currentArtistName) {
  String value = currentArtistName ?: 'Not Available'
  if(shouldQueueLocalDeviceEvent('currentArtistName', value, getCurrentArtistName())) {
    queueLocalDeviceEvent('currentArtistName', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentArtistName: value])
}
String getCurrentArtistName() { return this.device.currentValue('currentArtistName') }

@CompileStatic
void setCurrentAlbumName(String currentAlbumName) {
  String value = currentAlbumName ?: 'Not Available'
  if(shouldQueueLocalDeviceEvent('currentAlbumName', value, getCurrentAlbumName())) {
    queueLocalDeviceEvent('currentAlbumName', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentAlbumName: value])
}
String getCurrentAlbumName() { return this.device.currentValue('currentAlbumName') }

@CompileStatic
void setCurrentTrackName(String currentTrackName) {
  String value = currentTrackName ?: 'Not Available'
  if(shouldQueueLocalDeviceEvent('currentTrackName', value, getCurrentTrackName())) {
    queueLocalDeviceEvent('currentTrackName', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentTrackName: value])
}
String getCurrentTrackName() { return this.device.currentValue('currentTrackName') }

@CompileStatic
void setCurrentTrackNumber(Integer currentTrackNumber) {
  Integer value = currentTrackNumber ?: 0
  if(shouldQueueLocalDeviceEvent('currentTrackNumber', value, getCurrentTrackNumber())) {
    queueLocalDeviceEvent('currentTrackNumber', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentTrackNumber: value])
  observeTransportTrackNumber(value)
}
Integer getCurrentTrackNumber() { return this.device.currentValue('currentTrackNumber') }

void observeTransportTrackNumber(Integer currentTrackNumber) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || active.queuedOnSocket == true ||
      !(active.command in ['skipToNextTrack', 'skipToPreviousTrack'])) { return }
  Map baseline = active.baseline instanceof Map ? (Map)active.baseline : [:]
  Integer previous = baseline.trackNumber != null ? (baseline.trackNumber as Integer) : null
  if(previous != null && currentTrackNumber != null && previous != currentTrackNumber) {
    finishTransportCommand(active, true, "confirmed by track number ${previous} -> ${currentTrackNumber}")
  }
}

@CompileStatic
void setTrackDescription(String trackDescription) {
  String prevTrackDescription = getTrackDescription()
  Boolean changed = shouldQueueLocalDeviceEvent('trackDescription', trackDescription, prevTrackDescription)
  if(changed) {
    queueLocalDeviceEvent('trackDescription', trackDescription)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([trackDescription: trackDescription])

  if(changed && getIsGroupCoordinator()) { getPlaybackMetadataStatusIn() }
}
String getTrackDescription() { return this.device.currentValue('trackDescription') }

@CompileStatic
void setTrackDataEvents(Map trackData) {
  String trackDataJson = JsonOutput.toJson(trackData)
  if(shouldQueueLocalDeviceEvent('trackData', trackDataJson, getTrackDataEvents())) {
    queueLocalDeviceEvent('trackData', trackDataJson)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([trackData: trackDataJson])
}
String getTrackDataEvents() {return this.device.currentValue('trackData')}

@CompileStatic
void setNextArtistName(String nextArtistName) {
  String value = nextArtistName ?: 'Not Available'
  if(!shouldQueueLocalDeviceEvent('nextArtistName', value, getNextArtistName())) { return }
  queueLocalDeviceEvent('nextArtistName', value)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextArtistName: value])
}
String getNextArtistName() { return this.device.currentValue('nextArtistName') }

@CompileStatic
void setNextAlbumName(String nextAlbumName) {
  String value = nextAlbumName ?: 'Not Available'
  if(!shouldQueueLocalDeviceEvent('nextAlbumName', value, getNextAlbumName())) { return }
  queueLocalDeviceEvent('nextAlbumName', value)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextAlbumName: value])
}
String getNextAlbumName() { return this.device.currentValue('nextAlbumName') }

@CompileStatic
void setNextTrackName(String nextTrackName) {
  String value = nextTrackName ?: 'Not Available'
  if(!shouldQueueLocalDeviceEvent('nextTrackName', value, getNextTrackName())) { return }
  queueLocalDeviceEvent('nextTrackName', value)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextTrackName: value])
}
String getNextTrackName() { return this.device.currentValue('nextTrackName') }

@CompileStatic
void setNextTrackAlbumArtURI(String nextTrackAlbumArtURI, Boolean isPlayingLocalTrack) {
  String formattedUri = '<br>'

  if(nextTrackAlbumArtURI.startsWith('/') && !isPlayingLocalTrack) {
    // Local Sonos path - prepend base URL and wrap in HTML
    String baseUrl = "${getLocalUpnpUrl()}${nextTrackAlbumArtURI}"
    formattedUri += "<img src=\"${baseUrl}\" width=\"200\" height=\"200\" >"
  } else if(!isPlayingLocalTrack && nextTrackAlbumArtURI && nextTrackAlbumArtURI != 'Not Available') {
    // External URL - wrap in HTML
    formattedUri += "<img src=\"${nextTrackAlbumArtURI}\" width=\"200\" height=\"200\" >"
  } else {
    // No album art available
    formattedUri = 'Not Available'
  }

  if(!shouldQueueLocalDeviceEvent('nextTrackAlbumArtURI', formattedUri, getNextTrackAlbumArtURI())) { return }
  queueLocalDeviceEvent('nextTrackAlbumArtURI', formattedUri)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextTrackAlbumArtURI: formattedUri])
}
String getNextTrackAlbumArtURI() { return this.device.currentValue('nextTrackAlbumArtURI') }

@CompileStatic
ConcurrentLinkedQueue<Map> getDeviceAudioClipQueue() {
  audioClipQueueInitialization()
  return audioClipQueue[getId()]
}

@CompileStatic
ConcurrentLinkedQueue<Map> getDeviceAudioClipQueueHighPriority() {
  audioClipQueueInitialization()
  return audioClipQueueHighPriority[getId()]
}

@CompileStatic
ConcurrentLinkedQueue<Map> getDeviceAudioClipQueueSaved() {
  audioClipQueueInitialization()
  return audioClipQueueSaved[getId()]
}

@CompileStatic
LinkedHashMap getDeviceAudioClipQueueTimers() {
  audioClipQueueInitialization()
  return audioClipQueueTimers[getId()]
}

@CompileStatic
void addTimerToAudioClipQueueTimers(Long clipDuration, String clipUri) {
  LinkedHashMap timers = getDeviceAudioClipQueueTimers()
  timers[clipUri] = Instant.now().getEpochSecond() + clipDuration
  // runIn(clipDuration + 10, 'dequeueAudioClip')
}

@CompileStatic
Integer getAudioClipQueueLength() {
  audioClipQueueInitialization()
  return getDeviceAudioClipQueue().size() as Integer
}

@CompileStatic
Boolean getAudioClipQueueIsEmpty() {
  audioClipQueueInitialization()
  return getDeviceAudioClipQueue().size() == 0
}

// Correlation IDs capture queue ownership without logging clip URLs or speech text.
void traceAudioClipQueues(String stage) {
  if(!loggingEnabled('trace')) { return }
  Map active = runtimeState.activeAudioClipMessage as Map
  logDebug("AudioClipQueue ${stage}: playing=${runtimeState.audioClipPlaying}, active=${runtimeState.activeAudioClipCorrelationId}, activePriority=${active?.priority ?: 'REGULAR'}, currentRegular=${(runtimeState.currentlyPlayingClip as Map)?.correlationId}, regular=${getDeviceAudioClipQueue().collect { Map clip -> clip.correlationId }}, high=${getDeviceAudioClipQueueHighPriority().collect { Map clip -> clip.correlationId }}, saved=${getDeviceAudioClipQueueSaved().collect { Map clip -> clip.correlationId }}")
}

void saveRegularAudioClipQueue() {
  traceAudioClipQueues('save-before-clear')
  logDebug('saveRegularAudioClipQueue')
  ConcurrentLinkedQueue<Map> regularQueue = getDeviceAudioClipQueue()
  ConcurrentLinkedQueue<Map> savedQueue = getDeviceAudioClipQueueSaved()

  // Save all queued regular priority clips
  savedQueue.clear()

  // First, save the currently playing clip if there is one
  Map currentlyPlayingClip = runtimeState.currentlyPlayingClip as Map
  if(currentlyPlayingClip) {
    savedQueue.add(currentlyPlayingClip)
    logDebug('Saved currently playing clip to be restarted')
  }

  // Then save all queued clips
  List<Map> queueList = new ArrayList<Map>(regularQueue)
  queueList.each { clip ->
    savedQueue.add(clip)
  }

  traceAudioClipQueues('save-after-copy')
  logDebug("Saved ${savedQueue.size()} total clips (including currently playing)")
}

void restoreRegularAudioClipQueue() {
  traceAudioClipQueues('restore-before')
  logDebug('restoreRegularAudioClipQueue')
  ConcurrentLinkedQueue<Map> regularQueue = getDeviceAudioClipQueue()
  ConcurrentLinkedQueue<Map> savedQueue = getDeviceAudioClipQueueSaved()

  // Restore saved clips to regular queue
  List<Map> savedList = new ArrayList<Map>(savedQueue)
  savedList.each { clip ->
    regularQueue.add(clip)
  }

  savedQueue.clear()
  traceAudioClipQueues('restore-after')
  logDebug("Restored ${savedList.size()} regular priority clips to queue")
}

void rotateAudioClipCorrelationId(Map clipMessage) {
  if(!(clipMessage instanceof Map)) { return }
  String nextGuid = UUID.randomUUID().toString()
  ['leftChannel', 'rightChannel'].each { String channel ->
    String rawCommand = clipMessage[channel] as String
    if(!rawCommand) { return }
    try {
      List commandEnvelope = (List)new groovy.json.JsonSlurper().parseText(rawCommand)
      if(commandEnvelope.size() >= 2 && commandEnvelope[1] instanceof Map) {
        ((Map)commandEnvelope[1]).name = nextGuid
        clipMessage[channel] = JsonOutput.toJson(commandEnvelope)
      }
    } catch(Exception e) {
      logWarn("Could not assign a fresh clip GUID for replay: ${e.message}")
    }
  }
  clipMessage.correlationId = nextGuid
  logDebug("Assigned new clip GUID ${nextGuid} for resumed ${clipMessage.origin ?: 'audio clip'}")
}

void interruptActiveAudioClipForHighPriority() {
  String activeGuid = runtimeState.activeAudioClipCorrelationId as String
  if(!activeGuid) { return }
  removeQueuedAudioClipCommand(activeGuid)
  Map<String, Map> clipProgress = runtimeState.audioClipProgressByCorrelationId instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)runtimeState.audioClipProgressByCorrelationId)
      : new LinkedHashMap<String, Map>()
  Map progress = clipProgress[activeGuid]
  if(progress instanceof Map) {
    progress.phase = 'INTERRUPTED_BY_HIGH_PRIORITY'
    progress.interruptedAt = now()
    clipProgress[activeGuid] = progress
    setHiddenJsonRuntimeState('audioClipProgressByCorrelationId', clipProgress)
    publishAudioClipProgressEvent(progress, null)
  }
  logInfo("Suspending regular audio clip ${activeGuid} for high-priority playback")
}

void removeQueuedAudioClipCommand(String correlationId) {
  if(!correlationId) { return }
  List<Map> queued = getWebsocketOutboundQueue()
  List<Map> retained = queued.findAll { Map entry ->
    !(entry?.kind == 'audioClip' && entry?.correlationId?.toString() == correlationId)
  }
  if(retained.size() != queued.size()) {
    if(retained.isEmpty()) { runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY) }
    else { runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained }
    logDebug("Removed unsent audio clip ${correlationId} from the WebSocket outbound queue for priority preemption")
  }
}

@CompileStatic
Boolean isWebsocketConnected() {return getDeviceDataValue('websocketStatus') == 'open' }

Boolean isWebsocketSendAllowed() {
  return isWebsocketConnected() && runtimeState[WS_CONNECT_PENDING_STATE_KEY] != true
}

@CompileStatic
String getWebSocketStatus() { return getDeviceDataValue('websocketStatus') }

void setWebSocketStatus(String status) {
  String previousStatus = getWebSocketStatus()
  logDebug("Primary WebSocket status transition: previous=${previousStatus}, next=${status}, outboundQueue=${getWebsocketOutboundQueue().size()}, connectPending=${runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true}")
  setDeviceDataValue('websocketStatus', status)
  if(status == 'open') {
    runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
    Long generation = ((runtimeState[WS_CONNECTION_GENERATION_STATE_KEY] ?: 0L) as Long) + 1L
    runtimeState[WS_CONNECTION_GENERATION_STATE_KEY] = generation
    runtimeState[WS_LAST_INBOUND_AT_STATE_KEY] = now()
    // Keep commands queued while the socket was unavailable behind the
    // subscriptions re-established below. Temporarily remove the pending
    // commands so sendWsMessage() can deliver those subscriptions directly;
    // otherwise its FIFO flush would put a queued operation ahead of them.
    List<Map> pendingCommands = removeExpiredWebsocketQueueEntries(getWebsocketOutboundQueue()).findAll { Map entry ->
      if(entry.wireCommand == 'subscribe') {
        logDebug("Discarding queued ${entry.namespace}.subscribe; recovery will issue a fresh subscription for generation=${generation}")
        return false
      }
      return true
    }
    runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY)
    runtimeState[WS_REPLAY_WAIT_SUBSCRIPTIONS_STATE_KEY] = true
    subscribeToWsEvents()
    List<Map> subscriptionCommands = getWebsocketOutboundQueue()
    if(!pendingCommands.isEmpty()) {
      subscriptionCommands.addAll(pendingCommands)
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = subscriptionCommands
    }
    scheduleNextWebsocketOutboundQueueExpiry()
    finishWebsocketSubscriptionBarrierIfReady()
  }
  else {
    runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
    runtimeState.remove(WS_REPLAY_WAIT_SUBSCRIPTIONS_STATE_KEY)
    runtimeState.remove(WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY)
    releaseFavPlaylistDelegate()
    clearWsSubscriptionStatus()
    runtimeState.audioClipSubscriptionRequestPending = false
    cancelWebsocketCommandLedgerForClosedConnection(status)
    if(runtimeState.audioClipCommandSent == true && runtimeState.activeAudioClipCorrelationId) {
      runtimeState.audioClipHadDisconnectAfterSend = true
      pauseAudioClipWatchdog()
      logWarn("WebSocket disconnected while audio clip ${runtimeState.activeAudioClipCorrelationId} awaited playback status; command will not be resent")
    }
  }
}

/** Queue latest group-facing values behind a fixed maximum-wait drain. */
synchronized void queueGroupDeviceUpdate(Map attributes) {
  String dni = device.getDeviceNetworkId()
  if(!attributes || attributes.isEmpty()) { return }
  groupDeviceUpdateRetryAttempts.remove(dni)
  if(!pendingGroupDeviceUpdates.containsKey(dni)) {
    pendingGroupDeviceUpdates[dni] = new ConcurrentHashMap<String, Object>()
  }
  ConcurrentHashMap<String, Object> pending = pendingGroupDeviceUpdates[dni]
  attributes.each { k, v -> if(v != null) { pending.put(k as String, v) } }
  scheduleGroupDeviceUpdateDrain(GROUP_DEVICE_UPDATE_MAX_WAIT_MS)
}

private void scheduleGroupDeviceUpdateDrain(Integer delayMs) {
  // The persisted marker can outlive its Hubitat job (for example, across a
  // driver update or scheduler recovery). Always replace/create the job when
  // new work arrives so a stale true marker cannot strand playback state.
  state[GROUP_DEVICE_UPDATE_SCHEDULED_KEY] = true
  runIn(30, 'verifyPendingPublicationDrains', [overwrite: true])
  runInMillis(delayMs, 'flushPendingGroupDeviceUpdates', [overwrite: true])
}

Map<String, Object> getPendingGroupDeviceUpdateSnapshot(String dni) {
  ConcurrentHashMap<String, Object> pending = pendingGroupDeviceUpdates[dni]
  if(pending == null || pending.isEmpty()) {
    return [:]
  }
  return new LinkedHashMap<String, Object>(pending)
}

void clearFlushedGroupDeviceUpdates(String dni, Map<String, Object> flushedAttributes) {
  if(flushedAttributes == null || flushedAttributes.isEmpty()) { return }

  ConcurrentHashMap<String, Object> pending = pendingGroupDeviceUpdates[dni]
  if(pending == null || pending.isEmpty()) { return }

  flushedAttributes.each { String attrName, Object attrValue ->
    if(pending[attrName] == attrValue) {
      pending.remove(attrName)
    }
  }
}

/**
 * Flush all pending group device updates in a single consolidated path.
 * Forwards queued attributes to BOTH follower player devices AND group devices,
 * replacing the former dual-path architecture (sendGroupEvents + queueGroupDeviceUpdate).
 * Called at a fixed maximum-wait deadline, then at a bounded cadence if needed.
 */
void flushPendingGroupDeviceUpdates() {
  String dni = device.getDeviceNetworkId()
  state.remove(GROUP_DEVICE_UPDATE_SCHEDULED_KEY)
  Boolean isCoordinator = getIsGroupCoordinator()
  if(!isCoordinator) {
    pendingGroupDeviceUpdates.remove(dni)
    groupDeviceUpdateRetryAttempts.remove(dni)
    return
  }
  Map<String, Object> pendingSnapshot = getPendingGroupDeviceUpdateSnapshot(dni)
  if(!pendingSnapshot || pendingSnapshot.isEmpty()) {
    pendingGroupDeviceUpdates.remove(dni)
    return
  }
  String coordinatorId = getId()
  logDebug("flushPendingGroupDeviceUpdates: Forwarding ${pendingSnapshot.size()} attributes to followers and group devices")

  try {
    // Collect follower updates for one asynchronous delivery to the app.
    List<String> followerDNIs = getGroupFollowerDNIs()
    Map<String, Object> followerAttrs = new LinkedHashMap<String, Object>(pendingSnapshot)
    if(followerAttrs.containsKey('_groupVolume')) { followerAttrs['groupVolume'] = followerAttrs.remove('_groupVolume') }
    if(followerAttrs.containsKey('_groupMute')) { followerAttrs['groupMute'] = followerAttrs.remove('_groupMute') }

    // Extract volume-specific keys and build volume attrs map for group devices
    Map<String, Object> playbackAttrs = new LinkedHashMap<String, Object>(pendingSnapshot)
    Map volumeAttrs = null
    try {
      Object rawVol = playbackAttrs.remove('_groupVolume')
      Object rawMute = playbackAttrs.remove('_groupMute')
      if(rawVol != null || rawMute != null) {
        Integer vol = rawVol != null ? rawVol as Integer : (getGroupVolumeState() ?: 0)
        String mute = rawMute != null ? rawMute as String : getGroupMuteState()
        // Do not include 'switch' here — the parent app's updateGroupDevices() is the
        // sole authority on group device switch state because it checks exact membership.
        // Including switch here caused the player's broad "isGrouped" check to override
        // the parent's more precise determination, leaking playback attributes through
        // the guard when the group device should be inactive.
        volumeAttrs = [volume: vol, mute: mute]
      }
    } catch(Exception e) {
      logWarn("Error preparing volume state for group devices: ${e.message}")
    }

    Map playbackAttrsForParent = playbackAttrs.isEmpty() ? null : playbackAttrs
    publishPlayerStateUpdate([kind: 'state', coordinatorId: coordinatorId, followerDNIs: followerDNIs,
      followerAttrs: followerAttrs, volumeAttrs: volumeAttrs, playbackAttrs: playbackAttrsForParent])
    clearFlushedGroupDeviceUpdates(dni, pendingSnapshot)
    groupDeviceUpdateRetryAttempts.remove(dni)
    ConcurrentHashMap<String, Object> remaining = pendingGroupDeviceUpdates[dni]
    if(remaining != null && !remaining.isEmpty()) {
      scheduleGroupDeviceUpdateDrain(LOCAL_EVENT_DRAIN_INTERVAL_MS)
    }
  } catch(Exception e) {
    logWarn("Error flushing pending group device updates for ${device.displayName}: ${e.message}")
    Integer attempt = groupDeviceUpdateRetryAttempts.get(dni) ?: 0
    groupDeviceUpdateRetryAttempts.put(dni, attempt + 1)
    scheduleGroupDeviceUpdateDrain(GROUP_DEVICE_UPDATE_RETRY_MS)
  }
}

@CompileStatic
void sendEventsToNewGroupMembers(List<String> newRincons) {
  List<String> groupDNIs = newRincons.collect{getDNIFromRincon(it)}
  logDebug("GroupDNIs ${groupDNIs}")
  List<Map> eventsToSend = getCurrentPlayingStatesForGroup()
  Map<String, Object> attributes = [:]
  eventsToSend.each { Map event -> if(event.name != null && event.value != null) { attributes[event.name.toString()] = event.value } }
  publishPlayerStateUpdate([kind: 'followers', followerDNIs: groupDNIs, followerAttrs: attributes])
}

void publishPlayerStateUpdate(Map payload) {
  Map eventData = new LinkedHashMap(payload)
  eventData['playerDni'] = device.getDeviceNetworkId()
  if(parent != null) { parent.playerStateUpdate(eventData) }
}

void publishGroupTopologyUpdate(String groupId, String coordinatorId, List<String> playerIds) {
  publishPlayerStateUpdate([
    kind: 'topology',
    groupId: groupId,
    coordinatorId: coordinatorId,
    playerIds: playerIds ?: [],
    observedAt: now()
  ])
}


ConcurrentHashMap<String, LinkedHashMap> getFavoritesMap() {
  return favoritesMap
}

@CompileStatic
Map findFavoriteById(String favoriteId) {
  if(favoriteId == null || favoriteId == '') { return null }
  for(Map favorite : getFavoritesMap().values()) {
    if(favorite?.get('id')?.toString() == favoriteId) { return favorite }
  }
  return null
}

@CompileStatic
Boolean isAmazonMusicService(String serviceName) {
  return serviceName != null && serviceName.toLowerCase().contains('amazon')
}

void clearFavoritesMap() {
  favoritesMap = new ConcurrentHashMap<String, LinkedHashMap>()
}

ConcurrentHashMap<String, LinkedHashMap> getPlaylistsMap() {
  return playlistsMap
}

@CompileStatic
List<String> getFavoriteIds() {
  List<String> ids = []
  for(Map favorite : getFavoritesMap().values()) {
    String id = favorite?.get('id')?.toString()
    if(id != null && id.trim() != '') { ids.add(id) }
  }
  return ids
}

@CompileStatic
List<String> getPlaylistIds() {
  List<String> ids = []
  for(Map playlist : getPlaylistsMap().values()) {
    String id = playlist?.get('id')?.toString()
    if(id != null && id.trim() != '') { ids.add(id) }
  }
  return ids
}

@CompileStatic
String selectRandomAvailableId(List<String> ids) {
  if(ids == null || ids.isEmpty()) { return null }
  return ids.get(rand.nextInt(ids.size()))
}

void queueRandomMediaLoad(String type) {
  List<String> pending = (List<String>)runtimeState.get('pendingRandomMediaLoads')
  if(pending == null) { pending = [] }
  pending.add(type)
  runtimeState.pendingRandomMediaLoads = pending
}

void completePendingRandomMediaLoads(String type) {
  List<String> pending = (List<String>)runtimeState.get('pendingRandomMediaLoads')
  if(pending == null || !pending.contains(type)) { return }
  List<String> remaining = []
  pending.each { String pendingType ->
    if(pendingType == type) {
      String selectedId = selectRandomAvailableId(type == 'favorite' ? getFavoriteIds() : getPlaylistIds())
      if(selectedId != null) {
        if(type == 'favorite') { loadFavorite(selectedId) }
        else { loadPlaylist(selectedId) }
      } else {
        logWarn("Cannot randomly load ${type}: no available IDs were returned")
      }
    } else { remaining.add(pendingType) }
  }
  runtimeState.pendingRandomMediaLoads = remaining
}

void failPendingRandomMediaLoads(String type) {
  List<String> pending = (List<String>)runtimeState.get('pendingRandomMediaLoads')
  if(pending == null) { return }
  runtimeState.pendingRandomMediaLoads = pending.findAll { String pendingType -> pendingType != type }
  logWarn("Cannot randomly load ${type}: the available ID query failed")
}

void clearPlaylistsMap() {
  playlistsMap = new ConcurrentHashMap<String, LinkedHashMap>()
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
  if(getEnableTrackPositionUpdates()) {
    currentStates.add([name: 'trackPosition', value: getTrackPosition()])
    currentStates.add([name: 'trackPositionUpdated', value: getTrackPositionUpdated()])
  }
  currentStates.add([name: 'currentArtistName',     value: getCurrentArtistName()])
  currentStates.add([name: 'currentAlbumName',      value: getCurrentAlbumName()])
  currentStates.add([name: 'currentTrackName',      value: getCurrentTrackName()])
  currentStates.add([name: 'trackDescription',      value: getTrackDescription()])
  currentStates.add([name: 'trackData',             value: getTrackDataEvents()])
  currentStates.add([name: 'currentFavorite',       value: getCurrentFavorite()])
  currentStates.add([name: 'currentPlaylist',       value: getCurrentPlaylist()])
  currentStates.add([name: 'nextAlbumName',         value: getNextAlbumName()])
  currentStates.add([name: 'nextArtistName',        value: getNextArtistName()])
  currentStates.add([name: 'nextTrackName',         value: getNextTrackName()])
  currentStates.add([name: 'nextTrackAlbumArtURI',  value: this.device.currentValue('nextTrackAlbumArtURI')])
  currentStates.add([name: 'albumArtSmall',         value: this.device.currentValue('albumArtSmall')])
  currentStates.add([name: 'albumArtMedium',        value: this.device.currentValue('albumArtMedium')])
  currentStates.add([name: 'albumArtLarge',         value: this.device.currentValue('albumArtLarge')])
  currentStates.add([name: 'audioSource',           value: this.device.currentValue('audioSource')])
  currentStates.add([name: 'currentTrackNumber',    value: this.device.currentValue('currentTrackNumber')])
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
  logDebug("Primary WebSocket status callback received: message=${message}, currentStatus=${getWebSocketStatus()}, outboundQueue=${getWebsocketOutboundQueue().size()}")
  if(message == 'failure: null') {
    setWebSocketStatus('closed')
    scheduleWebSocketReconnect()
  }
  else if(message == 'failure: connect timed out') {
    setWebSocketStatus('connect timed out')
    scheduleWebSocketReconnect()
  }
  else if(message == 'status: open') {
    if(isWebsocketConnected() && runtimeState[WS_CONNECT_PENDING_STATE_KEY] != true) {
      logTrace('Ignoring duplicate primary WebSocket open callback')
      return
    }
    unschedule('websocketConnectWatchdog')
    runtimeState.websocketOpenedAt = (now() / 1000L) as Long
    runtimeState.remove(WS_DISCONNECTED_SINCE_KEY)
    runtimeState.remove(WS_RETRY_SCHEDULED_KEY)
    setWebSocketStatus('open')
    runtimeState.wsRetryCount = 0 // Reset retry counter on successful connection
    publishPlayerStateUpdate([kind: 'wsConnected'])
  }
  else if(message == 'status: closing') {
    if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true) {
      // connect() may replace the prior transport. A closing callback can belong
      // to that transport while the replacement attempt is already in flight.
      logDebug('Ignoring primary WebSocket closing callback while replacement connection is pending')
      return
    }
    if(getWebSocketStatus() == 'closing') {
      logTrace('Ignoring duplicate primary WebSocket closing callback')
      return
    }
    setWebSocketStatus('closing')
  }
  else {
    setWebSocketStatus('unknown')
    logWarn("Websocket status: ${message}")
    scheduleWebSocketReconnect()
  }
}

void scheduleWebSocketReconnect() {
  if(runtimeState[WS_RETRY_SCHEDULED_KEY] == true) { return }
  if(runtimeState[WS_DISCONNECTED_SINCE_KEY] == null) { runtimeState[WS_DISCONNECTED_SINCE_KEY] = now() }

  Long disconnectedSince = runtimeState[WS_DISCONNECTED_SINCE_KEY] as Long
  Long elapsedSeconds = Math.max(0L, (now() - disconnectedSince).intdiv(1000L))
  Integer delaySeconds = webSocketRetryIntervalSeconds(elapsedSeconds)
  Integer retryCount = (runtimeState.wsRetryCount ?: 0) as Integer
  runtimeState.wsRetryCount = retryCount + 1
  runtimeState[WS_RETRY_SCHEDULED_KEY] = true

  logInfo("WebSocket connection failed. Retry ${retryCount + 1} in ${delaySeconds} seconds...")
  publishPlayerStateUpdate([kind: 'wsRetry', retryDelay: delaySeconds])
  runIn(delaySeconds, 'retryWebSocketConnection', [overwrite: true])
}

Boolean restartWebSocketBackoff() {
  if(isWebsocketConnected() || runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true || runtimeState[WS_DISCONNECTED_SINCE_KEY] == null) { return false }
  unschedule('retryWebSocketConnection')
  runtimeState.remove(WS_RETRY_SCHEDULED_KEY)
  runtimeState[WS_DISCONNECTED_SINCE_KEY] = now()
  runtimeState.wsRetryCount = 0
  scheduleWebSocketReconnect()
  return true
}

Integer webSocketRetryIntervalSeconds(Long elapsedSeconds) {
  Long elapsed = Math.max(0L, elapsedSeconds)
  Long stageEnd = 0L
  for(Integer index = 0; index < WS_BACKOFF_STAGE_SECONDS.size(); index++) {
    stageEnd += WS_BACKOFF_STAGE_SECONDS[index]
    if(elapsed < stageEnd) { return WS_BACKOFF_INTERVALS_SECONDS[index] }
  }
  return WS_BACKOFF_INTERVALS_SECONDS[WS_BACKOFF_INTERVALS_SECONDS.size() - 1]
}

void retryWebSocketConnection() {
  runtimeState.remove(WS_RETRY_SCHEDULED_KEY)
  logDebug("Attempting WebSocket reconnection...")
  requestAppMediatedInitialization('WebSocket reconnect retry')
}

void wsConnect() {
  if(isWebsocketConnected() || runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true) {
    logTrace("Skipping primary WebSocket connect; status=${getWebSocketStatus()}, connectPending=${runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true}")
    return
  }
  if(runtimeState[APP_INITIALIZATION_CONNECTION_GRANTED_STATE_KEY] != true) {
    requestAppMediatedInitialization('WebSocket connection requested outside app initialization turn')
    return
  }
  Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
  runtimeState[WS_CONNECT_PENDING_STATE_KEY] = true
  try {
    logInfo("Requesting primary WebSocket connection; previousStatus=${getWebSocketStatus()}, retryCount=${runtimeState.wsRetryCount ?: 0}")
    // Hubitat's native ping detects a half-open transport independently of
    // command responses. A missing application ACK must not cycle the socket.
    interfaces.webSocket.connect(getDeviceDataValue('websocketUrl'), headers: headers,
        ignoreSSLIssues: true, pingInterval: WS_PING_INTERVAL_SECONDS)
    runIn(WS_CONNECT_TIMEOUT_SECONDS, 'websocketConnectWatchdog', [overwrite: true])
    unschedule('renewWebsocketConnection')
    unschedule('retryWebSocketConnection')
    scheduleResubscriptionToEvents('renewWebsocketConnection')
  } catch(Exception e) {
    runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
    setWebSocketStatus('closed')
    logWarn("WebSocket connect failed: ${e.message}")
    scheduleWebSocketReconnect()
  }
}

void websocketConnectWatchdog() {
  if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true && !isWebsocketConnected()) {
    logWarn('WebSocket connection attempt timed out; retrying.')
    runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
    setWebSocketStatus('closed')
    scheduleWebSocketReconnect()
  }
}

void wsClose() {
  logInfo("Closing primary WebSocket; status=${getWebSocketStatus()}, connectPending=${runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true}")
  interfaces.webSocket.close()
}

List<Map> getWebsocketOutboundQueue() {
  return runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] instanceof List
      ? (List<Map>)runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY]
      : []
}

void queueWebsocketMessage(String message, Integer attempts = 0, Map metadata = [:]) {
  if(!message) { return }
  Map descriptor = describeWebSocketCommand(message)
  if(!descriptor.namespace || !descriptor.wireCommand || !descriptor.expectedResponseType) {
    logWarn('Dropping a queued WebSocket command without a known response fingerprint')
    return
  }
  List<Map> queue = getWebsocketOutboundQueue()
  Map entry = [message: message, attempts: attempts ?: 0, createdAt: metadata?.createdAt ?: now(),
      expiresAt: metadata?.expiresAt ?: now() + WS_COMMAND_MAX_AGE_SECONDS * 1000L]
  if(metadata instanceof Map && !metadata.isEmpty()) { entry.putAll(metadata) }
  entry.queueSequence = entry.queueSequence ?: nextWebsocketCommandSequence()
  entry.putAll(descriptor)
  if(!entry.expectedSubscriptionEvents) { entry.expectedSubscriptionEvents = expectedSubscriptionEventsForWsCommand(message) }
  entry.queueId = entry.queueId ?: UUID.randomUUID().toString()
  List<Map> retained = removeExpiredWebsocketQueueEntries(queue)
  String replacementKey = queuedCommandReplacementKey(entry)
  if(replacementKey) { entry.replacementKey = replacementKey }
  if(replacementKey) {
    Long sequence = entry.queueSequence as Long
    List<Map> matching = retained.findAll { Map queued ->
      queued.replacementKey?.toString() == replacementKey &&
        isWithinWebsocketQueueSegment(retained, queued, sequence)
    }
    Long newestPriorSequence = matching.collect { Map queued -> (queued.queueSequence ?: 0L) as Long }.max() ?: 0L
    if(newestPriorSequence > (entry.queueSequence as Long)) {
      logDebug("Discarding older replay candidate for ${replacementKey}; newer queued intent already exists")
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained
      scheduleNextWebsocketOutboundQueueExpiry()
      return
    }
    Integer replaced = matching.size()
    Set<Long> replacedSequences = matching.collect { Map queued -> (queued.queueSequence ?: 0L) as Long } as Set<Long>
    retained = retained.findAll { Map queued ->
      !(queued.replacementKey?.toString() == replacementKey &&
        replacedSequences.contains((queued.queueSequence ?: 0L) as Long))
    }
    if(replaced > 0) { logDebug("Replaced ${replaced} queued WebSocket command(s) for key=${replacementKey}") }
  }
  Boolean playModesMerged = false
  if(entry.namespace == 'playback' && entry.wireCommand == 'setPlayModes') {
    Map prior = retained.reverse().find { Map queued ->
      queued.namespace == 'playback' && queued.wireCommand == 'setPlayModes' &&
        queued.targetId?.toString() == entry.targetId?.toString() &&
        isWithinWebsocketQueueSegment(retained, queued, entry.queueSequence as Long)
    } as Map
    if(prior != null) {
      mergeQueuedPlayModeFields(prior, entry)
      playModesMerged = true
    }
  }
  if(playModesMerged) {
    runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained
    scheduleNextWebsocketOutboundQueueExpiry()
    return
  }
  if(retained.size() >= WS_OUTBOUND_QUEUE_MAX_SIZE) {
    logWarn("Dropping newest queued WebSocket command ${entry.namespace ?: 'unknown'}.${entry.wireCommand ?: 'unknown'}: queue reached ${WS_OUTBOUND_QUEUE_MAX_SIZE} entries")
    runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained
    scheduleNextWebsocketOutboundQueueExpiry()
    return
  }
  retained.add(entry)
  retained.sort { Map left, Map right -> ((left.queueSequence ?: 0L) as Long) <=> ((right.queueSequence ?: 0L) as Long) }
  runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained
  logDebug("Queued WebSocket command ${entry.namespace ?: 'unknown'}.${entry.wireCommand ?: 'unknown'} target=${entry.targetId ?: 'n/a'} queueId=${entry.queueId} expiresAt=${entry.expiresAt} queued=${retained.size()}")
  scheduleNextWebsocketOutboundQueueExpiry()
}

List<Map> removeExpiredWebsocketQueueEntries(List<Map> queue) {
  Long currentTime = now()
  List<Map> retained = []
  (queue ?: []).each { Map entry ->
    if(entry?.expiresAt != null && (entry.expiresAt as Long) <= currentTime) {
      logDebug("Dropping expired queued WebSocket command ${entry.namespace ?: 'unknown'}.${entry.wireCommand ?: 'unknown'} queueId=${entry.queueId}")
    } else if(entry != null) { retained.add(entry) }
  }
  return retained
}

/** One timer services all queued-command expirations, ordered by expiresAt. */
void scheduleNextWebsocketOutboundQueueExpiry() {
  List<Map> queue = getWebsocketOutboundQueue()
  List<Long> deadlines = queue.collect { Map entry ->
    entry.expiresAt == null ? null : (entry.expiresAt as Long)
  }.findAll { Long deadline -> deadline != null }
  Long nextDeadline = deadlines.isEmpty() ? null : deadlines.min()
  Long scheduledDeadline = (runtimeState[WS_OUTBOUND_QUEUE_EXPIRY_AT_STATE_KEY] ?: 0L) as Long
  if(nextDeadline == null) {
    if(scheduledDeadline > 0L) { unschedule('expireWebsocketOutboundQueueEntry') }
    runtimeState.remove(WS_OUTBOUND_QUEUE_EXPIRY_AT_STATE_KEY)
    return
  }
  if(scheduledDeadline > 0L && scheduledDeadline <= nextDeadline) { return }
  unschedule('expireWebsocketOutboundQueueEntry')
  runtimeState[WS_OUTBOUND_QUEUE_EXPIRY_AT_STATE_KEY] = nextDeadline
  runInMillis(Math.max(1L, nextDeadline - now()), 'expireWebsocketOutboundQueueEntry', [overwrite: true])
}

void expireWebsocketOutboundQueueEntry(Map data = [:]) {
  runtimeState.remove(WS_OUTBOUND_QUEUE_EXPIRY_AT_STATE_KEY)
  Long currentTime = now()
  List<Map> queue = getWebsocketOutboundQueue()
  List<Map> retained = queue.findAll { Map entry ->
    Long deadline = (entry?.expiresAt ?: 0L) as Long
    if(deadline <= currentTime) {
      logDebug("Expired queued WebSocket command ${entry.namespace ?: 'unknown'}.${entry.wireCommand ?: 'unknown'} queueId=${entry.queueId}; it will not be replayed")
      return false
    }
    return true
  }
  if(retained.isEmpty()) { runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY) }
  else { runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained }
  scheduleNextWebsocketOutboundQueueExpiry()
}

Map describeWebSocketCommand(String message) {
  try {
    List payload = (List)new JsonSlurper().parseText(message)
    Map command = payload && payload[0] instanceof Map ? (Map)payload[0] : [:]
    Map args = payload.size() > 1 && payload[1] instanceof Map ? (Map)payload[1] : [:]
    String namespace = command.namespace?.toString()
    String wireCommand = command.command?.toString()
    String targetKind = command.playerId != null ? 'playerId' :
        (command.groupId != null ? 'groupId' : (command.householdId != null ? 'householdId' : null))
    String targetId = targetKind ? command[targetKind]?.toString() : null
    String responseType = expectedWebsocketResponseType(namespace, wireCommand)
    return [namespace: namespace, wireCommand: wireCommand, targetKind: targetKind,
        targetId: targetId, args: args, expectedResponseType: responseType]
  } catch(Exception e) {
    logDebug("Could not describe outbound WebSocket command: ${e.message}")
    return [:]
  }
}

String expectedWebsocketResponseType(String namespace, String wireCommand) {
  if(wireCommand == 'subscribe' && namespace in ['groups', 'favorites', 'playlists', 'audioClip',
      'playback', 'playbackMetadata', 'playerVolume', 'groupVolume']) { return 'none' }
  if(namespace == 'playback' && wireCommand in ['play', 'pause', 'stop', 'skipToNextTrack',
      'skipToPreviousTrack', 'setPlayModes']) { return 'none' }
  if(namespace in ['playerVolume', 'groupVolume'] && wireCommand in ['setVolume', 'setMute', 'setRelativeVolume']) { return 'none' }
  if(namespace == 'favorites' && wireCommand == 'loadFavorite') { return 'none' }
  if(namespace == 'playlists' && wireCommand == 'loadPlaylist') { return 'none' }
  if(namespace == 'favorites' && wireCommand == 'getFavorites') { return 'favoritesList' }
  if(namespace == 'playlists' && wireCommand == 'getPlaylists') { return 'playlistsList' }
  if(namespace == 'groups' && wireCommand == 'getGroups') { return 'groups' }
  if(namespace == 'playback' && wireCommand == 'getPlaybackStatus') { return 'playbackStatus' }
  if(namespace == 'playbackMetadata' && wireCommand == 'getMetadataStatus') { return 'metadataStatus' }
  if(namespace == 'groups' && wireCommand in ['createGroup', 'modifyGroupMembers']) { return 'groupInfo' }
  if(namespace in ['audioClip', 'audioClip:1'] && wireCommand == 'loadAudioClip') { return 'audioClip' }
  if(namespace in ['audioClip', 'audioClip:1'] && wireCommand == 'cancelAudioClip') { return 'none' }
  return null
}

Long nextWebsocketCommandSequence() {
  Long next = ((runtimeState[WS_COMMAND_SEQUENCE_STATE_KEY] ?: 0L) as Long) + 1L
  runtimeState[WS_COMMAND_SEQUENCE_STATE_KEY] = next
  return next
}

String queuedCommandReplacementKey(Map entry) {
  String namespace = entry?.namespace?.toString()
  String command = entry?.wireCommand?.toString()
  String target = entry?.targetId?.toString()
  if(!namespace || !command || !target) { return null }
  if(namespace == 'playback' && command in ['play', 'pause', 'stop']) { return "transport:${target}" }
  if(namespace == 'playerVolume' && command in ['setVolume', 'setMute']) { return "${namespace}:${command}:${target}" }
  if(namespace == 'groupVolume' && command in ['setVolume', 'setMute']) { return "${namespace}:${command}:${target}" }
  if(namespace in ['favorites', 'playlists'] && command in ['loadFavorite', 'loadPlaylist'] &&
      entry.args?.action?.toString()?.toUpperCase() == 'REPLACE') { return "media:${target}" }
  return null
}

Boolean isWithinWebsocketQueueSegment(List<Map> queue, Map candidate, Long sequence) {
  Long candidateSequence = (candidate?.queueSequence ?: 0L) as Long
  Long lowerBound = 0L
  Long upperBound = Long.MAX_VALUE
  (queue ?: []).each { Map queued ->
    if(isWebsocketQueueOrderingBarrier(queued)) {
      Long barrierSequence = (queued.queueSequence ?: 0L) as Long
      if(barrierSequence < sequence && barrierSequence > lowerBound) { lowerBound = barrierSequence }
      if(barrierSequence > sequence && barrierSequence < upperBound) { upperBound = barrierSequence }
    }
  }
  return candidateSequence > lowerBound && candidateSequence < upperBound
}

Boolean isWebsocketQueueOrderingBarrier(Map entry) {
  return queuedCommandReplacementKey(entry) == null &&
      !(entry?.namespace == 'playback' && entry?.wireCommand == 'setPlayModes')
}

void mergeQueuedPlayModeFields(Map prior, Map latest) {
  try {
    List priorPayload = (List)new JsonSlurper().parseText(prior.message.toString())
    List latestPayload = (List)new JsonSlurper().parseText(latest.message.toString())
    Map priorArgs = (Map)priorPayload[1]
    Map latestArgs = (Map)latestPayload[1]
    Boolean latestIsNewer = ((latest.queueSequence ?: 0L) as Long) >= ((prior.queueSequence ?: 0L) as Long)
    Map firstModes = latestIsNewer ?
        (Map)(priorArgs.playModes instanceof Map ? priorArgs.playModes : [:]) :
        (Map)(latestArgs.playModes instanceof Map ? latestArgs.playModes : [:])
    Map secondModes = latestIsNewer ?
        (Map)(latestArgs.playModes instanceof Map ? latestArgs.playModes : [:]) :
        (Map)(priorArgs.playModes instanceof Map ? priorArgs.playModes : [:])
    Map mergedModes = new LinkedHashMap(firstModes)
    mergedModes.putAll(secondModes)
    priorArgs.playModes = mergedModes
    prior.message = JsonOutput.toJson([priorPayload[0], priorArgs])
    prior.createdAt = Math.min((prior.createdAt ?: now()) as Long, (latest.createdAt ?: now()) as Long)
    prior.expiresAt = Math.min((prior.expiresAt ?: now()) as Long, (latest.expiresAt ?: now()) as Long)
    prior.queueSequence = Math.min((prior.queueSequence ?: 0L) as Long, (latest.queueSequence ?: 0L) as Long)
    logDebug("Merged queued playback.setPlayModes fields for group=${prior.targetId}: ${mergedModes.keySet()}")
  } catch(Exception e) { logWarn("Could not merge queued WebSocket play modes: ${e.message}") }
}

void finishWebsocketSubscriptionBarrierIfReady() {
  if(runtimeState[WS_REPLAY_WAIT_SUBSCRIPTIONS_STATE_KEY] != true) { return }
  Long generation = (runtimeState[WS_CONNECTION_GENERATION_STATE_KEY] ?: 0L) as Long
  Boolean pending
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    pending = ledger.any { Map entry -> entry.connectionGeneration == generation && entry.wireCommand == 'subscribe' }
  }
  if(pending) { return }
  runtimeState.remove(WS_REPLAY_WAIT_SUBSCRIPTIONS_STATE_KEY)
  List<Map> queue = getWebsocketOutboundQueue()
  Boolean needsTopology = queue.any { Map entry -> entry.requiresTopologyReconciliation == true }
  if(needsTopology) {
    if(runtimeState[WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY] != true) {
      runtimeState[WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY] = true
      logDebug('Holding WebSocket command replay until a fresh groups snapshot reconciles pending topology mutations')
      runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY)
      playerGetGroupsFull()
      List<Map> queuedDuringQuery = getWebsocketOutboundQueue()
      if(!queuedDuringQuery.isEmpty()) { queue.addAll(queuedDuringQuery) }
      if(!queue.isEmpty()) { runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue }
    }
    return
  }
  logDebug("All WebSocket subscription responses settled for generation=${generation}; releasing queued commands")
  if(isWebsocketConnected()) { flushWebsocketOutboundQueue() }
}

Map registerWebsocketCommandLedgerEntry(String message, Map outbound, Long createdAt = null) {
  Map descriptor = describeWebSocketCommand(message)
  if(descriptor.isEmpty()) { return [:] }
  Long sentAt = now()
  Long inboundSequenceAtSend = (runtimeState[WS_INBOUND_SEQUENCE_STATE_KEY] ?: 0L) as Long
  Long generation = (runtimeState[WS_CONNECTION_GENERATION_STATE_KEY] ?: 0L) as Long
  String entryId = UUID.randomUUID().toString()
  Map entry = new LinkedHashMap(descriptor)
  entry.putAll([entryId: entryId, connectionGeneration: generation, message: message,
      createdAt: createdAt ?: sentAt, sentAt: sentAt,
      expiresAt: (createdAt ?: sentAt) + WS_COMMAND_MAX_AGE_SECONDS * 1000L,
      queueSequence: outbound?.queueSequence ?: nextWebsocketCommandSequence(),
      inboundSequenceAtSend: inboundSequenceAtSend])
  entry.args = descriptor.args instanceof Map ? new LinkedHashMap((Map)descriptor.args) : [:]
  entry.kind = outbound?.kind
  entry.transportCommandId = outbound?.transportCommandId
  entry.correlationId = outbound?.correlationId ?: entry.args?.name
  entry.expectedEventsGuid = outbound?.expectedEventsGuid
  if(entry.namespace == 'groups' && entry.wireCommand == 'modifyGroupMembers') {
    entry.semanticBaseline = getGroupPlayerIds().collect { Object id -> id?.toString() }.findAll { String id -> id }.sort()
  }
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    ledger.add(entry)
    runtimeState[WS_COMMAND_LEDGER_STATE_KEY] = ledger
  }
  outbound.ledgerEntryId = entryId
  runIn(WS_COMMAND_LEDGER_EXPIRY_SECONDS, 'expireWebsocketCommandLedgerEntry',
      [data: [entryId: entryId, connectionGeneration: generation], overwrite: false])
  logDebug("WebSocket command ledger armed id=${entryId} generation=${generation} fingerprint=${entry.namespace}.${entry.wireCommand} target=${entry.targetId ?: 'n/a'} expectedType=${entry.expectedResponseType ?: 'unspecified'}")
  return entry
}

void removeWebsocketCommandLedgerEntryById(String entryId, String reason) {
  if(!entryId) { return }
  Map removed = null
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    removed = ledger.find { Map item -> item.entryId?.toString() == entryId } as Map
    if(removed != null) {
      ledger.remove(removed)
      if(ledger.isEmpty()) { runtimeState.remove(WS_COMMAND_LEDGER_STATE_KEY) }
      else { runtimeState[WS_COMMAND_LEDGER_STATE_KEY] = ledger }
    }
  }
  if(removed != null) { logDebug("Removed WebSocket command ledger entry id=${entryId} fingerprint=${removed.namespace}.${removed.wireCommand}; reason=${reason}") }
}

void observeWebsocketCommandResponse(Map eventType, Map eventData) {
  String response = eventType?.response?.toString()
  String namespace = eventType?.namespace?.toString()
  if(!response || !namespace) { return }
  Long generation = (runtimeState[WS_CONNECTION_GENERATION_STATE_KEY] ?: 0L) as Long
  Map matched = null
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    matched = ledger.find { Map entry ->
      if(entry.connectionGeneration != generation || entry.namespace?.toString() != namespace ||
          entry.wireCommand?.toString() != response) { return false }
      String returnedTarget = eventType.playerId?.toString() ?: eventType.groupId?.toString() ?: eventType.householdId?.toString()
      if(returnedTarget && entry.targetId && returnedTarget != entry.targetId.toString()) { return false }
      if(namespace == 'audioClip' && response == 'loadAudioClip') {
        String returnedCorrelation = eventData?.name?.toString()
        if(eventType.success == true && (!returnedCorrelation || !entry.correlationId || returnedCorrelation != entry.correlationId.toString())) { return false }
        if(returnedCorrelation && entry.correlationId && returnedCorrelation != entry.correlationId.toString()) { return false }
      }
      if(eventType.success == true && entry.expectedResponseType && eventType.type?.toString() != entry.expectedResponseType.toString()) { return false }
      if(eventType.success == true && namespace == 'groups' && response in ['createGroup', 'modifyGroupMembers'] &&
          !doesTopologyAckMatch(entry, eventData)) { return false }
      return true
    } as Map
    if(matched != null) {
      ledger.remove(matched)
      if(ledger.isEmpty()) { runtimeState.remove(WS_COMMAND_LEDGER_STATE_KEY) }
      else { runtimeState[WS_COMMAND_LEDGER_STATE_KEY] = ledger }
    }
  }
  if(matched == null) { return }
  Boolean success = eventType.success == true
  String outcome = success ? 'success' : 'failure'
  String detail = success ? "type=${eventType.type}" : "type=${eventType.type}, error=${eventData?.errorCode ?: eventData?.reason ?: 'unspecified'}"
  if(matched.wireCommand == 'subscribe' && !success && runtimeState[APP_INITIALIZATION_REQUEST_ID_STATE_KEY] != null) {
    runtimeState.appMediatedInitializationFailure = "WebSocket subscription ${namespace} was rejected: ${detail}"
  }
  logDebug("WebSocket command ledger ${outcome}: id=${matched.entryId}, fingerprint=${namespace}.${response}, target=${matched.targetId ?: 'n/a'}, ${detail}")
  if(success && matched.kind == 'transportCommand') {
    transportCommandAcknowledged(matched.transportCommandId?.toString(), response)
  }
  if(namespace == 'groups' && response == 'getGroups' && success == true && runtimeState[WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY] == true) {
    reconcileQueuedTopologyCommands(eventData)
  }
  if(matched.wireCommand == 'subscribe') { finishWebsocketSubscriptionBarrierIfReady() }
}

Boolean doesTopologyAckMatch(Map entry, Map eventData) {
  Map group = eventData?.group instanceof Map ? (Map)eventData.group : eventData
  List<String> returnedMembers = (group?.playerIds instanceof List ? (List)group.playerIds : [])
      .collect { Object id -> id?.toString() }.findAll { String id -> id }.sort()
  Map args = entry.args instanceof Map ? (Map)entry.args : [:]
  if(entry.wireCommand == 'createGroup') {
    List<String> requested = (args.playerIds instanceof List ? (List)args.playerIds : [])
        .collect { Object id -> id?.toString() }.findAll { String id -> id }.sort()
    return requested && returnedMembers == requested
  }
  if(entry.wireCommand == 'modifyGroupMembers') {
    Set<String> requested = new LinkedHashSet<String>((entry.semanticBaseline instanceof List ? (List)entry.semanticBaseline : []).collect { Object id -> id?.toString() })
    (args.playerIdsToAdd instanceof List ? (List)args.playerIdsToAdd : []).each { Object id -> if(id) { requested.add(id.toString()) } }
    (args.playerIdsToRemove instanceof List ? (List)args.playerIdsToRemove : []).each { Object id -> if(id) { requested.remove(id.toString()) } }
    return requested && returnedMembers == requested.toList().sort()
  }
  return true
}

void reconcileQueuedTopologyCommands(Map eventData) {
  List<Map> groups = eventData?.groups instanceof List ? (List<Map>)eventData.groups : []
  List<Map> queue = getWebsocketOutboundQueue()
  List<Map> retained = []
  queue.each { Map entry ->
    if(entry.requiresTopologyReconciliation != true) {
      retained.add(entry)
      return
    }
    Map args = entry.args instanceof Map ? (Map)entry.args : [:]
    List<String> desired = []
    if(entry.wireCommand == 'createGroup') {
      desired = (args.playerIds instanceof List ? (List)args.playerIds : []).collect { Object id -> id?.toString() }.findAll { String id -> id }.sort()
    } else if(entry.wireCommand == 'modifyGroupMembers') {
      Set<String> desiredSet = new LinkedHashSet<String>((entry.semanticBaseline instanceof List ? (List)entry.semanticBaseline : []).collect { Object id -> id?.toString() })
      (args.playerIdsToAdd instanceof List ? (List)args.playerIdsToAdd : []).each { Object id -> if(id) { desiredSet.add(id.toString()) } }
      (args.playerIdsToRemove instanceof List ? (List)args.playerIdsToRemove : []).each { Object id -> if(id) { desiredSet.remove(id.toString()) } }
      desired = desiredSet.toList().sort()
    }
    Boolean alreadyApplied = desired && groups.any { Map group ->
      List<String> members = (group?.playerIds instanceof List ? (List)group.playerIds : []).collect { Object id -> id?.toString() }.findAll { String id -> id }.sort()
      members == desired
    }
    if(alreadyApplied) {
      logDebug("Suppressed replay of ${entry.namespace}.${entry.wireCommand}; fresh groups snapshot already matches members=${desired}")
    } else {
      entry.remove('requiresTopologyReconciliation')
      logDebug("Replaying ${entry.namespace}.${entry.wireCommand}; fresh groups snapshot did not match desired members=${desired}")
      retained.add(entry)
    }
  }
  if(retained.isEmpty()) { runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY) }
  else { runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = retained }
  runtimeState.remove(WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY)
  if(isWebsocketConnected()) { flushWebsocketOutboundQueue() }
}

/** Expire correlation state only. Command response silence is not transport evidence. */
void expireWebsocketCommandLedgerEntry(Map data = [:]) {
  String entryId = data?.entryId?.toString()
  Long generation = data?.connectionGeneration != null ? (data.connectionGeneration as Long) : -1L
  Map entry
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> ledger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    Map pending = ledger.find { Map item -> item.entryId?.toString() == entryId } as Map
    if(pending != null) { entry = new LinkedHashMap(pending) }
  }
  if(entry == null || entry.connectionGeneration != generation) { return }
  removeWebsocketCommandLedgerEntryById(entryId, 'response correlation expired; transport state unchanged')
  if(entry.wireCommand == 'subscribe') {
    logWarn("WebSocket subscription ${entry.namespace}.subscribe produced no response before correlation expiry; leaving the connection open")
    finishWebsocketSubscriptionBarrierIfReady()
  }
}

Boolean shouldReplayUnacknowledgedCommand(Map entry) {
  if(entry?.wireCommand == 'subscribe' || entry?.namespace == 'audioClip' || entry?.kind in ['audioClip', 'transportCommand', 'transportStatusQuery']) { return false }
  if(entry?.namespace in ['favorites', 'playlists'] && entry?.wireCommand in ['loadFavorite', 'loadPlaylist']) {
    return entry.args?.action?.toString()?.toUpperCase() == 'REPLACE'
  }
  return true
}

Boolean requeuePendingWebsocketCommands(String reason) {
  List<Map> ledger = takeWebsocketCommandLedgerBatch()
  if(ledger.isEmpty()) { return false }
  Long currentTime = now()
  ledger.each { Map entry ->
    cancelExpectedSubscriptionEvents(entry)
    if(((entry.expiresAt ?: 0L) as Long) <= currentTime) {
      logDebug("Dropping expired unacknowledged command ${entry.namespace}.${entry.wireCommand} during recovery")
    } else if(!shouldReplayUnacknowledgedCommand(entry)) {
      logWarn("Not automatically replaying unacknowledged ${entry.namespace}.${entry.wireCommand}; its effect may already have occurred (${reason})")
    } else {
      Map metadata = new LinkedHashMap(entry)
      metadata.remove('entryId')
      metadata.remove('ledgerEntryId')
      metadata.remove('connectionGeneration')
      metadata.remove('sentAt')
      metadata.remove('expectedResponseType')
      if(entry.namespace == 'groups' && entry.wireCommand in ['createGroup', 'modifyGroupMembers']) {
        metadata.requiresTopologyReconciliation = true
      }
      logDebug("Preserving unacknowledged WebSocket command for replay: ${entry.namespace}.${entry.wireCommand}, id=${entry.entryId}, expiresAt=${entry.expiresAt}")
      queueWebsocketMessage(entry.message.toString(), 0, metadata)
    }
  }
  return true
}

List<Map> takeWebsocketCommandLedgerBatch() {
  List<Map> ledger = []
  synchronized(getWebsocketCommandLedgerLock()) {
    List<Map> activeLedger = runtimeState[WS_COMMAND_LEDGER_STATE_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_COMMAND_LEDGER_STATE_KEY] : []
    ledger = activeLedger.collect { Map entry -> new LinkedHashMap(entry) }
    runtimeState.remove(WS_COMMAND_LEDGER_STATE_KEY)
  }
  return ledger
}

void cancelWebsocketCommandLedgerForClosedConnection(String status) {
  if(status == 'open') { return }
  requeuePendingWebsocketCommands("socket status ${status}")
}

void flushWebsocketOutboundQueue() {
  if(!isWebsocketSendAllowed()) {
    logDebug("Outbound WebSocket queue drain deferred; primary websocketStatus=${getWebSocketStatus()}, connectPending=${runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true}")
    if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] != true) { wsConnect() }
    return
  }
  if(runtimeState[WS_REPLAY_WAIT_SUBSCRIPTIONS_STATE_KEY] == true) {
    logDebug("Outbound WebSocket queue drain deferred until subscription ACKs arrive; queued=${getWebsocketOutboundQueue().size()}")
    return
  }
  if(runtimeState[WS_REPLAY_WAIT_TOPOLOGY_STATE_KEY] == true) {
    logDebug("Outbound WebSocket queue drain deferred until topology reconciliation completes; queued=${getWebsocketOutboundQueue().size()}")
    return
  }

  List<Map> queue = removeExpiredWebsocketQueueEntries(getWebsocketOutboundQueue())
  if(queue.isEmpty()) { runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY) }
  else { runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue }
  logDebug("Starting outbound WebSocket queue drain: queued=${queue.size()}, primaryStatus=${getWebSocketStatus()}")
  runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY)
  while(!queue.isEmpty()) {
    if(!isWebsocketSendAllowed()) {
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
      scheduleNextWebsocketOutboundQueueExpiry()
      logDebug("Stopping outbound WebSocket drain because transport is no longer sendable; remaining=${queue.size()}")
      if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] != true) { wsConnect() }
      return
    }
    Map entry = (Map)queue.remove(0)
    String message = entry?.message as String
    Integer attempts = (entry?.attempts ?: 0) as Integer
    if(!message) { continue }
    if(entry?.kind == 'audioClip' && !isCurrentlySubcribedToAudioClipWS()) {
      logDebug("Holding audioClip queue entry until primary audioClip subscription is active: correlation=${entry?.correlationId}, queueRemaining=${queue.size() + 1}")
      queue.add(0, entry)
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
      scheduleNextWebsocketOutboundQueueExpiry()
      if(runtimeState.audioClipSubscriptionRequestPending != true) { subscribeToAudioClip() }
      return
    }
    String pairedRightMessage = entry?.pairedRightChannelMessage?.toString()
    ChildDeviceWrapper pairedRightChannel = pairedRightMessage ? getRightChannelChild() : null
    Boolean secondaryExpired = entry.pairedSecondaryExpired == true
    if(pairedRightMessage && !secondaryExpired && (!pairedRightChannel || pairedRightChannel.getDataValue('websocketStatus') != 'open')) {
      logDebug("Holding paired audioClip before either channel send: correlation=${entry.correlationId}, primary=open, secondaryStatus=${pairedRightChannel?.getDataValue('websocketStatus') ?: 'child unavailable'}, timeoutExpired=${secondaryExpired}")
      queue.add(0, entry)
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
      scheduleNextWebsocketOutboundQueueExpiry()
      if(pairedRightChannel) {
        watchRightChannelWebsocket(pairedRightChannel)
        pairedRightChannel.initializeWebsocketConnection()
      } else {
        logInfo('Paired audio clip is waiting for the right-channel socket before dispatch.')
      }
      schedulePairedAudioClipSecondaryTimeout(entry)
      return
    }
    Boolean primarySendReturned = false
    try {
      logDebug("Dispatching audioClip primary channel: correlation=${entry?.correlationId}, paired=${pairedRightMessage != null}, secondaryStatus=${pairedRightChannel?.getDataValue('websocketStatus') ?: 'not paired'}, secondaryExpired=${secondaryExpired}, attempts=${attempts}")
      armExpectedSubscriptionEvents(entry)
      registerWebsocketCommandLedgerEntry(message, entry, entry.createdAt as Long)
      interfaces.webSocket.sendMessage(message)
      primarySendReturned = true
      Boolean rightSent = false
      if(pairedRightMessage && !secondaryExpired) {
        rightSent = pairedRightChannel.playerLoadAudioClipForSynchronizedRelease(
          pairedRightMessage, entry.correlationId?.toString()) == true
        pairedRightChannel.completeSynchronizedAudioClipRelease(entry.correlationId?.toString())
        if(!rightSent) {
          logInfo("Secondary audio clip send failed after primary dispatch for ${entry.correlationId}; not replaying the secondary clip")
        }
      }
      logDebug("AudioClip channel dispatch completed: correlation=${entry?.correlationId}, primary=sent, secondary=${!pairedRightMessage ? 'not paired' : (secondaryExpired ? 'timed out; skipped' : (rightSent ? 'sent' : 'failed'))}")
      if(entry?.kind == 'audioClip') {
        audioClipCommandSent(entry.correlationId as String)
      } else if(entry?.kind == 'transportCommand') {
        transportCommandSocketSent(entry.transportCommandId as String)
      } else if(entry?.kind == 'transportStatusQuery') {
        transportStatusQuerySocketSent(entry.transportCommandId as String)
      }
    } catch(Exception e) {
      removeWebsocketCommandLedgerEntryById(entry?.ledgerEntryId?.toString(), 'send threw')
      logDebug("Outbound WebSocket send threw for correlation=${entry?.correlationId ?: 'n/a'}, kind=${entry?.kind ?: 'unknown'}, attempt=${attempts + 1}: ${e.message}")
      if(primarySendReturned && pairedRightMessage) {
        logInfo("Secondary audio clip dispatch threw after primary dispatch for ${entry.correlationId}; not replaying either channel")
        try {
          pairedRightChannel?.completeSynchronizedAudioClipRelease(entry.correlationId?.toString())
        } catch(Exception completionError) {
          logDebug("Could not complete secondary release logging for ${entry.correlationId}: ${completionError.message}")
        }
        audioClipCommandSent(entry.correlationId as String)
        continue
      }
      cancelExpectedSubscriptionEvents(entry)
      Integer nextAttempt = attempts + 1
      if(nextAttempt >= WS_OUTBOUND_MAX_ATTEMPTS) {
        if(entry?.kind in ['audioClip', 'audioClipSubscribe']) {
          entry.attempts = nextAttempt
          queue.add(0, entry)
          runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
          scheduleNextWebsocketOutboundQueueExpiry()
          logWarn("Keeping unsent audio clip WebSocket message (${entry.kind}, ${entry.correlationId ?: 'subscription'}) queued after ${nextAttempt} send attempts: ${e.message}")
          setWebSocketStatus('closed')
          scheduleWebSocketReconnect()
          return
        }
        logError("Dropping WebSocket message after ${WS_OUTBOUND_MAX_ATTEMPTS} attempts: ${e.message}")
        continue
      }
      entry.attempts = nextAttempt
      queue.add(0, entry)
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
      scheduleNextWebsocketOutboundQueueExpiry()
      setWebSocketStatus('closed')
      scheduleWebSocketReconnect()
      return
    }
  }
  scheduleNextWebsocketOutboundQueueExpiry()
  logDebug("Outbound WebSocket queue drain completed: remaining=${getWebsocketOutboundQueue().size()}, primaryStatus=${getWebSocketStatus()}")
}

void watchRightChannelWebsocket(ChildDeviceWrapper rightChannel) {
  logDebug("Tracking right-channel WebSocket status through child-to-parent callbacks: child=${rightChannel?.displayName}, dni=${rightChannel?.getDeviceNetworkId()}, status=${rightChannel?.getDataValue('websocketStatus')}")
}

void rightChannelWebsocketStatusChanged(String status, String childDeviceNetworkId) {
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  if(rightChannel?.getDeviceNetworkId()?.toString() != childDeviceNetworkId) {
    logDebug("Ignoring right-channel WebSocket status from unexpected child: dni=${childDeviceNetworkId}, expected=${rightChannel?.getDeviceNetworkId()}")
    return
  }
  String statusDetail = "value=${status}, child=${childDeviceNetworkId}, primaryStatus=${getWebSocketStatus()}, queued=${getWebsocketOutboundQueue().size()}"
  if(status in ['error', 'connect timed out', 'unknown']) {
    logWarn("Best-effort right-channel WebSocket failure: ${statusDetail}")
  } else {
    logDebug("Right-channel WebSocket status callback: ${statusDetail}")
  }
  if(status == 'open') {
    if(isWebsocketConnected()) { flushWebsocketOutboundQueue() }
    else { logDebug('Right-channel socket opened first; paired entry remains queued until the primary socket opens') }
  }
}

/** Authorize a right-channel-only connection without restarting the primary player. */
void requestSecondaryWebsocketInitialization(String reason, String childDeviceNetworkId) {
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  if(rightChannel == null || rightChannel.getDeviceNetworkId()?.toString() != childDeviceNetworkId) {
    logWarn("Ignoring secondary WebSocket request from unexpected child: dni=${childDeviceNetworkId ?: 'unknown'}")
    return
  }
  Boolean resetRetries = reason == 'secondary device lifecycle initialize'
  String requestId = UUID.randomUUID().toString()
  logDebug("Granting right-channel-only WebSocket request; reason=${reason ?: 'secondary request'}, child=${childDeviceNetworkId}, resetRetries=${resetRetries}")
  rightChannel.beginParentMediatedConnection(requestId, resetRetries)
}

void rightChannelWebsocketCommandResponse(String result, String correlationId, String errorCode, String childDeviceNetworkId) {
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  if(rightChannel?.getDeviceNetworkId()?.toString() != childDeviceNetworkId) { return }
  logDebug("Paired right-channel WebSocket audioClip response: result=${result}, correlation=${correlationId ?: 'n/a'}, error=${errorCode ?: 'n/a'}, child=${childDeviceNetworkId}")
}

void schedulePairedAudioClipSecondaryTimeout(Map entry) {
  if(!entry?.pairedRightChannelMessage || entry.pairedSecondaryExpired == true || !entry.correlationId) { return }
  if(entry.pairedSecondaryDeadline == null) {
    entry.pairedSecondaryDeadline = now() + (PAIRED_AUDIO_CLIP_SECONDARY_TIMEOUT_SECONDS * 1000L)
  }
  if(entry.pairedSecondaryTimeoutScheduled == true) { return }
  Long remainingMillis = Math.max(0L, (entry.pairedSecondaryDeadline as Long) - now())
  Integer delaySeconds = Math.max(1, Math.ceil(remainingMillis / 1000.0d) as Integer)
  entry.pairedSecondaryTimeoutScheduled = true
  logDebug("Scheduled secondary-open timeout: correlation=${entry.correlationId}, delay=${delaySeconds}s, deadline=${entry.pairedSecondaryDeadline}, secondaryStatus=${getRightChannelChild()?.getDataValue('websocketStatus') ?: 'child unavailable'}")
  runIn(delaySeconds, 'expirePairedAudioClipSecondary',
      [data: [correlationId: entry.correlationId.toString()], overwrite: false])
}

void expirePairedAudioClipSecondary(Map data) {
  String correlationId = data?.correlationId?.toString()
  if(!correlationId) {
    logDebug('Ignored secondary-open timeout without a correlation ID')
    return
  }
  List<Map> queue = getWebsocketOutboundQueue()
  Map entry = queue.find { Map queued -> queued?.correlationId?.toString() == correlationId }
  if(entry == null || entry.pairedSecondaryExpired == true) {
    logDebug("Secondary-open timeout is stale; no pending paired queue entry remains for ${correlationId}")
    return
  }
  ChildDeviceWrapper rightChannel = getRightChannelChild()
  if(rightChannel?.getDataValue('websocketStatus') == 'open') {
    logDebug("Secondary socket reported open before timeout handling for ${correlationId}; preserving paired dispatch")
    if(isWebsocketConnected()) { flushWebsocketOutboundQueue() }
    else { logDebug("Secondary opened before timeout for ${correlationId}, but primary remains ${getWebSocketStatus()}; preserving paired entry") }
    return
  }
  entry.pairedSecondaryExpired = true
  entry.remove('pairedSecondaryTimeoutScheduled')
  runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
  logDebug("Secondary-open timeout expired: correlation=${correlationId}, primaryStatus=${getWebSocketStatus()}, queued=${queue.size()}, secondaryStatus=${rightChannel?.getDataValue('websocketStatus') ?: 'child unavailable'}")
  logInfo("Secondary socket did not report open within ${PAIRED_AUDIO_CLIP_SECONDARY_TIMEOUT_SECONDS} seconds for ${correlationId}; continuing with primary only")
  if(isWebsocketConnected()) { flushWebsocketOutboundQueue() }
  else { logDebug("Primary-only fallback remains queued until primary socket opens: correlation=${correlationId}") }
}

Boolean sendWsMessage(String message, Map metadata = [:]) {
  if(!message) { return false }
  Map descriptor = describeWebSocketCommand(message)
  if(!descriptor.namespace || !descriptor.wireCommand || !descriptor.expectedResponseType) {
    logWarn('Refusing to send a WebSocket command without a known response fingerprint')
    return false
  }
  if(!isWebsocketSendAllowed()) {
    queueWebsocketMessage(message, 0, metadata)
    if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] != true) { wsConnect() }
    return false
  }

  List<Map> queued = getWebsocketOutboundQueue()
  if(!queued.isEmpty()) {
    queueWebsocketMessage(message, 0, metadata)
    flushWebsocketOutboundQueue()
    return false
  }
  if(metadata?.kind == 'audioClip' && !isCurrentlySubcribedToAudioClipWS()) {
    queueWebsocketMessage(message, 0, metadata)
    if(runtimeState.audioClipSubscriptionRequestPending != true) { subscribeToAudioClip() }
    return false
  }
  Map outbound = new LinkedHashMap(metadata ?: [:])
  outbound.message = message
  outbound.createdAt = outbound.createdAt ?: now()
  outbound.expiresAt = outbound.expiresAt ?: ((outbound.createdAt as Long) + WS_COMMAND_MAX_AGE_SECONDS * 1000L)
  outbound.putAll(describeWebSocketCommand(message))
  if(!outbound.expectedSubscriptionEvents) {
    outbound.expectedSubscriptionEvents = expectedSubscriptionEventsForWsCommand(message)
  }
  try {
    armExpectedSubscriptionEvents(outbound)
    registerWebsocketCommandLedgerEntry(message, outbound, outbound.createdAt as Long)
    interfaces.webSocket.sendMessage(message)
    if(metadata?.kind == 'audioClip') { audioClipCommandSent(metadata.correlationId as String) }
    if(metadata?.kind == 'transportCommand') { transportCommandSocketSent(metadata.transportCommandId as String) }
    if(metadata?.kind == 'transportStatusQuery') { transportStatusQuerySocketSent(metadata.transportCommandId as String) }
    return true
  } catch(Exception e) {
    cancelExpectedSubscriptionEvents(outbound)
    removeWebsocketCommandLedgerEntryById(outbound.ledgerEntryId?.toString(), 'send threw')
    queueWebsocketMessage(message, 1, metadata)
    setWebSocketStatus('closed')
    logWarn("WebSocket send failed; queued message for retry: ${e.message}")
    scheduleWebSocketReconnect()
    return false
  }
}

List<Map> expectedSubscriptionEventsForWsCommand(String message) {
  if(!message) { return [] }
  try {
    List payload = (List)new JsonSlurper().parseText(message)
    Map command = payload && payload[0] instanceof Map ? (Map)payload[0] : [:]
    String namespace = command.namespace?.toString()
    String name = command.command?.toString()
    if(namespace in ['favorites', 'playlists'] && name in ['loadFavorite', 'loadPlaylist']) {
      return [[namespace: 'playbackMetadata', type: 'metadataStatus'],
          [namespace: 'playback', type: 'playbackStatus']]
    }
    if(namespace == 'playback' && name in ['play', 'pause', 'stop', 'skipToNextTrack', 'skipToPreviousTrack', 'seek']) {
      return [[namespace: 'playback', type: 'playbackStatus']]
    }
    if(namespace == 'groups' && name in ['createGroup', 'modifyGroupMembers']) {
      return [[namespace: 'groups', type: 'groups']]
    }
  } catch(Exception e) {
    logDebug("Could not inspect WebSocket command for expected subscription events: ${e.message}")
  }
  return []
}

String subscriptionEventKey(Map eventType) {
  return "${eventType?.namespace ?: ''}:${eventType?.type ?: ''}".toString()
}

void armExpectedSubscriptionEvents(Map outbound) {
  List<Map> expected = outbound?.expectedSubscriptionEvents instanceof List
      ? (List<Map>)outbound.expectedSubscriptionEvents : []
  if(expected.isEmpty()) { return }
  String guid = UUID.randomUUID().toString()
  Map expectation = [guid: guid, command: outbound.message?.toString(), expected: expected,
      received: [], attempts: 0, deadlineAt: now() + WS_EXPECTED_EVENT_TIMEOUT_SECONDS * 1000L]
  synchronized(runtimeState) {
    List<Map> pending = runtimeState[WS_EXPECTED_EVENTS_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_EXPECTED_EVENTS_KEY] : []
    pending.add(expectation)
    runtimeState[WS_EXPECTED_EVENTS_KEY] = pending
  }
  outbound.expectedEventsGuid = guid
  logDebug("Armed subscription event watchdog guid=${guid} expected=${expected}")
  scheduleExpectedSubscriptionEventWatchdog()
}

void cancelExpectedSubscriptionEvents(Map outbound) {
  String guid = outbound?.expectedEventsGuid?.toString()
  if(!guid) { return }
  synchronized(runtimeState) {
    List<Map> pending = runtimeState[WS_EXPECTED_EVENTS_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_EXPECTED_EVENTS_KEY] : []
    pending = pending.findAll { Map item -> item.guid?.toString() != guid }
    if(pending.isEmpty()) { runtimeState.remove(WS_EXPECTED_EVENTS_KEY) }
    else { runtimeState[WS_EXPECTED_EVENTS_KEY] = pending }
  }
  scheduleExpectedSubscriptionEventWatchdog()
}

void observeExpectedSubscriptionEvent(Map eventType) {
  if(!(eventType instanceof Map) || eventType.response != null) { return }
  String observed = subscriptionEventKey(eventType)
  synchronized(runtimeState) {
    List<Map> pending = runtimeState[WS_EXPECTED_EVENTS_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_EXPECTED_EVENTS_KEY] : []
    if(pending.isEmpty()) { return }
    List<String> completedGuids = []
    pending.each { Map expectation ->
      List<Map> expected = expectation.expected instanceof List ? (List<Map>)expectation.expected : []
      List<String> received = expectation.received instanceof List ? (List<String>)expectation.received : []
      if(expected.any { Map item -> subscriptionEventKey(item) == observed }) {
        if(!received.contains(observed)) { received.add(observed) }
        expectation.received = received
        logDebug("Subscription event watchdog guid=${expectation.guid} observed=${observed} received=${received}")
        List<Map> missing = expected.findAll { Map item -> !received.contains(subscriptionEventKey(item)) }
        if(missing.isEmpty()) {
          completedGuids.add(expectation.guid?.toString())
          logDebug("Expected subscription events arrived for WebSocket command guid=${expectation.guid}")
        }
      }
    }
    if(!completedGuids.isEmpty()) {
      pending = pending.findAll { Map item -> !completedGuids.contains(item.guid?.toString()) }
      if(pending.isEmpty()) { runtimeState.remove(WS_EXPECTED_EVENTS_KEY) }
      else { runtimeState[WS_EXPECTED_EVENTS_KEY] = pending }
    } else {
      // Reassign the top-level state entry so Hubitat persists nested receipt
      // updates for the scheduled watchdog invocation.
      runtimeState[WS_EXPECTED_EVENTS_KEY] = pending
    }
  }
  scheduleExpectedSubscriptionEventWatchdog()
}

void scheduleExpectedSubscriptionEventWatchdog() {
  List<Map> pending = runtimeState[WS_EXPECTED_EVENTS_KEY] instanceof List
      ? (List<Map>)runtimeState[WS_EXPECTED_EVENTS_KEY] : []
  if(pending.isEmpty()) {
    unschedule('watchExpectedSubscriptionEvents')
    return
  }
  Long earliest = pending.collect { Map item -> item.deadlineAt as Long }.min() as Long
  Integer delay = Math.max(1, (int)Math.ceil((earliest - now()) / 1000.0d))
  runIn(delay, 'watchExpectedSubscriptionEvents', [overwrite: true])
}

void watchExpectedSubscriptionEvents() {
  Long currentTime = now()
  List<Map> toResubscribe = []
  synchronized(runtimeState) {
    List<Map> pending = runtimeState[WS_EXPECTED_EVENTS_KEY] instanceof List
        ? (List<Map>)runtimeState[WS_EXPECTED_EVENTS_KEY] : []
    // Leave one second of slack for a subscription notification arriving on the
    // same scheduler tick as its timeout callback.
    List<Map> due = pending.findAll { Map item -> (item.deadlineAt as Long) <= currentTime - 1000L }
    due.each { Map expectation ->
      List<Map> expected = expectation.expected instanceof List ? (List<Map>)expectation.expected : []
      List<String> received = expectation.received instanceof List ? (List<String>)expectation.received : []
      List<Map> missing = expected.findAll { Map item -> !received.contains(subscriptionEventKey(item)) }
      if(missing.isEmpty()) {
        pending = pending.findAll { Map item -> item.guid?.toString() != expectation.guid?.toString() }
        logDebug("Expected subscription events arrived before watchdog recovery for guid=${expectation.guid}")
      } else if(((expectation.attempts ?: 0) as Integer) < 1) {
        expectation.missingEvents = missing
        expectation.attempts = 1
        expectation.deadlineAt = currentTime + WS_EXPECTED_EVENT_TIMEOUT_SECONDS * 1000L
        toResubscribe.add(expectation)
      } else {
        logWarn("Expected subscription events still missing after resubscription for guid=${expectation.guid}; received=${expectation.received}: ${missing}")
        pending = pending.findAll { Map item -> item.guid?.toString() != expectation.guid?.toString() }
      }
    }
    if(pending.isEmpty()) { runtimeState.remove(WS_EXPECTED_EVENTS_KEY) }
    else { runtimeState[WS_EXPECTED_EVENTS_KEY] = pending }
  }
  toResubscribe.each { Map expectation ->
    List<Map> missing = expectation.missingEvents instanceof List ? (List<Map>)expectation.missingEvents : []
    Set<String> namespaces = missing.collect { Map item -> item.namespace.toString() } as Set<String>
    logWarn("No expected subscription event arrived for WebSocket command guid=${expectation.guid}; resubscribing namespaces=${namespaces}, missing=${missing}")
    namespaces.each { String namespace -> resubscribeWebsocketNamespace(namespace) }
  }
  scheduleExpectedSubscriptionEventWatchdog()
}

void resubscribeWebsocketNamespace(String namespace) {
  if(namespace in ['playback', 'playbackMetadata', 'groupVolume'] && getIsGroupCoordinator() != true) {
    logDebug("Skipping ${namespace} resubscription because this player is not the group coordinator")
    return
  }
  String groupId = getGroupId()
  if(namespace in ['playback', 'playbackMetadata', 'groupVolume'] && !groupId) {
    logWarn("Cannot resubscribe to ${namespace}: no current group ID")
    return
  }
  switch(namespace) {
    case 'playback': subscribeToPlayback(groupId); break
    case 'playbackMetadata': subscribeToPlaybackMetadata(groupId); break
    case 'groups': subscribeToGroups(); break
    case 'playerVolume': subscribeToPlayerVolume(); break
    case 'groupVolume': subscribeToGroupVolume(groupId); break
    case 'favorites': subscribeToFavorites(); break
    case 'playlists': subscribeToPlaylists(); break
    default: logWarn("No resubscribe handler for WebSocket namespace ${namespace}")
  }
}

void initializeWebsocketConnection() { wsConnect() }
void renewWebsocketConnection() {
  if(!isWebsocketConnected()) { requestAppMediatedInitialization('scheduled WebSocket renewal') }
}
// =============================================================================
// End Websocket Connection and Initialization
// =============================================================================



// =============================================================================
// Websocket Subscriptions and polling
// =============================================================================
void subscribeToWsEvents() {
  // Favorites and playlists are household-scoped — only one player per household needs to subscribe
  if(checkIsFavPlaylistDelegate()) {
    if(isCurrentlySubcribedToPlaylistWS() == false) { subscribeToPlaylists() }
    if(isCurrentlySubcribedToFavoritesWS() == false) { subscribeToFavorites() }
  }
  if(hasAudioClipCapability() && isCurrentlySubcribedToAudioClipWS() == false) { subscribeToAudioClip() }
  if(isCurrentlySubcribedToGroupsWS() == false) { subscribeToGroups() }
  if(isCurrentlySubcribedToPlayerVolumeWS() == false) { subscribeToPlayerVolume() }
  // Re-establish coordinator-specific subscriptions if this player is the group
  // coordinator and the subscriptions have been lost (e.g., after WS reconnect).
  // These are group-scoped and require a valid groupId.
  if(getIsGroupCoordinator() == true && isCurrentlySubcribedToCoodinatorWS() == false) {
    String groupId = getGroupId()
    if(groupId) {
      subscribeToPlayback(groupId)
      subscribeToPlaybackMetadata(groupId)
      subscribeToGroupVolume(groupId)
    }
  }
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

void subscribeToAudioClip() {
  if(runtimeState.audioClipSubscriptionRequestPending == true) { return }
  Map command = [
    'namespace':'audioClip',
    'command':'subscribe',
    'playerId':"${getId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  runtimeState.audioClipSubscriptionRequestPending = true
  sendWsMessage(json, [kind: 'audioClipSubscribe'])
}

void retryAudioClipSubscription() {
  if(runtimeState.audioClipPlaying == true && runtimeState.activeAudioClipCorrelationId &&
      isCurrentlySubcribedToAudioClipWS() != true && runtimeState.audioClipSubscriptionRequestPending != true && isWebsocketConnected()) {
    subscribeToAudioClip()
  }
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void subscribeToPlayerVolume() {
  Map command = [
    'namespace':'playerVolume',
    'command':'subscribe',
    'playerId':"${getId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void subscribeToGroupVolume(String groupId) {
  Map command = [
    'namespace':'groupVolume',
    'command':'subscribe',
    'groupId':"${groupId}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void getPlaybackStatus() {
  Map command = [
    'namespace':'playback',
    'command':'getPlaybackStatus',
    'groupId':"${getGroupId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active != null && active.statusQueryPending == true) {
    sendWsMessage(json, [kind: 'transportStatusQuery', transportCommandId: active.id])
  } else {
    sendWsMessage(json)
  }
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
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}
// =============================================================================
// End Websocket Subscriptions and polling
// =============================================================================



// =============================================================================
// Websocket Commands
// =============================================================================
void enqueueTransportCommand(String command) {
  synchronized(getRuntimeState()) {
    if(!(command in ['play', 'pause', 'stop', 'skipToNextTrack', 'skipToPreviousTrack'])) { return }
    String intentGuid = recordStreamTransportIntent(command)
    List<Map> queued = runtimeState[TRANSPORT_QUEUE_KEY] instanceof List
        ? (List<Map>)runtimeState[TRANSPORT_QUEUE_KEY]
        : []
    if(command in ['play', 'pause', 'stop']) {
      List<Map> retained = queued.findAll { Map item -> !(item.command in ['play', 'pause', 'stop']) }
      Integer replaced = queued.size() - retained.size()
      queued = retained
      Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
          ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
      if(active != null && active.queuedOnSocket == true && active.command in ['play', 'pause', 'stop']) {
        cancelUnsentTransportCommand(active)
        replaced++
      }
      if(replaced > 0) {
        logDebug("Transport command ${command} replaced ${replaced} queued play/pause/stop command(s)")
      }
    }
    if(queued.size() >= 32) {
      logWarn("Dropping transport command ${command}: command queue is full")
      return
    }
    Map item = [id: intentGuid, command: command]
    if(command in ['skipToNextTrack', 'skipToPreviousTrack']) {
      Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
          ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
      Map lastQueued = queued ? queued[queued.size() - 1] : null
      String replayId = null
      if(active != null && active.command in ['skipToNextTrack', 'skipToPreviousTrack']) {
        replayId = active.replayId?.toString()
      } else if(lastQueued != null && lastQueued.command in ['skipToNextTrack', 'skipToPreviousTrack']) {
        replayId = lastQueued.replayId?.toString()
      }
      item.replayId = replayId ?: UUID.randomUUID().toString()
    }
    queued.add(item)
    runtimeState[TRANSPORT_QUEUE_KEY] = queued
    logDebug("Queued transport command ${command} (${item.id}); pending=${queued.size()}")
    runIn(1, 'drainTransportCommandQueue', [overwrite: true])
  }
}

void drainTransportCommandQueue() {
  if(runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map) { return }
  List<Map> queued = runtimeState[TRANSPORT_QUEUE_KEY] instanceof List
      ? (List<Map>)runtimeState[TRANSPORT_QUEUE_KEY]
      : []
  if(queued.isEmpty()) { return }
  Map item = queued.remove(0)
  runtimeState[TRANSPORT_QUEUE_KEY] = queued
  Map baseline = runtimeState[TRANSPORT_STATUS_SNAPSHOT_KEY] instanceof Map
      ? new LinkedHashMap((Map)runtimeState[TRANSPORT_STATUS_SNAPSHOT_KEY])
      : [:]
  baseline.trackNumber = getCurrentTrackNumber()
  Map active = [id: item.id, command: item.command, replayId: item.replayId,
      attempts: 0, baseline: baseline,
      queuedOnSocket: false, statusQueryPending: false]
  runtimeState[TRANSPORT_ACTIVE_KEY] = active
  sendTransportCommandAttempt(active)
}

void sendTransportCommandAttempt(Map active) {
  if(active == null || runtimeState[TRANSPORT_ACTIVE_KEY]?.id != active.id) { return }
  String commandName = active.command as String
  String wireCommand = commandName == 'skipToNextTrack' ? 'skipToNextTrack' :
      commandName == 'skipToPreviousTrack' ? 'skipToPreviousTrack' : commandName
  Map command = [namespace: 'playback', command: wireCommand, groupId: getGroupId()]
  String message = JsonOutput.toJson([command, [:]])
  active.attempts = ((active.attempts ?: 0) as Integer) + 1
  active.queuedOnSocket = true
  active.statusQueryPending = false
  logDebug("Sending transport command ${commandName}, attempt ${active.attempts} (${active.id})")
  sendWsMessage(message, [kind: 'transportCommand', transportCommandId: active.id])
}

void transportCommandSocketSent(String commandId) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || active.id?.toString() != commandId) { return }
  active.queuedOnSocket = false
  active.sentAt = now()
  logDebug("Transport command ${active.command} (${commandId}) sent to Sonos WebSocket")
  runIn(TRANSPORT_WATCHDOG_SECONDS, 'watchTransportCommand', [overwrite: true, data: [commandId: commandId]])
}

void transportCommandAcknowledged(String commandId, String response) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || !commandId || active.id?.toString() != commandId) { return }
  active.websocketAcknowledged = true
  active.acknowledgedResponse = response
  active.acknowledgedAt = now()
  unschedule('retryUnconfirmedTransportCommand')
  logDebug("Transport command ${active.command} (${commandId}) received WebSocket ACK response=${response}; suppressing resend attempts")
}

void cancelUnsentTransportCommand(Map active) {
  if(active == null || runtimeState[TRANSPORT_ACTIVE_KEY]?.id != active.id) { return }
  List<Map> socketQueue = getWebsocketOutboundQueue()
  socketQueue.removeAll { Map entry ->
    entry?.kind == 'transportCommand' && entry.transportCommandId?.toString() == active.id?.toString()
  }
  runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = socketQueue
  runtimeState.remove(TRANSPORT_ACTIVE_KEY)
  unschedule('watchTransportCommand')
  unschedule('retryUnconfirmedTransportCommand')
  logDebug("Cancelled unsent transport command ${active.command} (${active.id}) because a newer state command arrived")
}

void watchTransportCommand(Map data = [:]) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  String commandId = data?.commandId?.toString()
  if(active == null || active.id?.toString() != commandId) { return }
  if(active.queuedOnSocket == true) {
    logDebug("Transport command ${active.command} (${commandId}) is still waiting in the WebSocket send queue")
    runIn(30, 'watchTransportCommand', [overwrite: true, data: [commandId: commandId]])
    return
  }
  active.statusQueryPending = true
  logDebug("Watchdog querying playback status for ${active.command} (${commandId})")
  getPlaybackStatus()
}

void transportStatusQuerySocketSent(String commandId) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || active.id?.toString() != commandId || active.statusQueryPending != true) { return }
  active.statusQuerySentAt = now()
  logDebug("Watchdog status query sent for ${active.command} (${commandId})")
  runIn(2, 'retryUnconfirmedTransportCommand', [overwrite: true, data: [commandId: commandId]])
}

void retryUnconfirmedTransportCommand(Map data = [:]) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  String commandId = data?.commandId?.toString()
  if(active == null || active.id?.toString() != commandId) { return }
  if(active.queuedOnSocket == true) { return }
  if(active.websocketAcknowledged == true) {
    String reason = active.lastFailureReason?.toString() ?:
        'no confirming playback status arrived after the successful WebSocket ACK'
    finishTransportCommand(active, false,
        "Sonos acknowledged ${active.acknowledgedResponse ?: active.command}; ${reason}; not retrying")
    return
  }
  Integer attempts = (active.attempts ?: 0) as Integer
  String reason = active.lastFailureReason?.toString() ?:
      "no confirming playback status after ${TRANSPORT_WATCHDOG_SECONDS} seconds"
  if(attempts >= TRANSPORT_MAX_ATTEMPTS) {
    logWarn("Transport command ${active.command} (${commandId}) was not confirmed after ${attempts} attempts; last reason: ${reason}")
    finishTransportCommand(active, false, reason)
    return
  }
  unschedule('watchTransportCommand')
  unschedule('retryUnconfirmedTransportCommand')
  logDebug("Retrying transport command ${active.command} (${commandId}), attempt ${attempts + 1}; reason: ${reason}")
  sendTransportCommandAttempt(active)
}

void observeTransportPlaybackStatus(Map eventData) {
  if(!(eventData instanceof Map)) { return }
  Map snapshot = [playbackState: eventData.playbackState?.toString(),
      itemId: eventData.itemId?.toString(), positionMillis: eventData.positionMillis,
      observedAt: now()]
  runtimeState[TRANSPORT_STATUS_SNAPSHOT_KEY] = snapshot
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || active.queuedOnSocket == true) { return }
  String command = active.command as String
  String playbackState = snapshot.playbackState as String
  if(command == 'play' && playbackState == 'PLAYBACK_STATE_BUFFERING') {
    active.statusQueryPending = false
    logDebug("Transport command play (${active.id}) is buffering; extending the confirmation window")
    runIn(TRANSPORT_WATCHDOG_SECONDS, 'watchTransportCommand',
        [overwrite: true, data: [commandId: active.id]])
    return
  }
  Boolean confirmed = false
  if(command == 'play') { confirmed = playbackState == 'PLAYBACK_STATE_PLAYING' }
  else if(command == 'pause') { confirmed = playbackState in ['PLAYBACK_STATE_PAUSED', 'PLAYBACK_STATE_IDLE'] }
  else if(command == 'stop') { confirmed = playbackState in ['PLAYBACK_STATE_IDLE', 'PLAYBACK_STATE_STOPPED'] }
  else if(command in ['skipToNextTrack', 'skipToPreviousTrack']) {
    Map baseline = active.baseline instanceof Map ? (Map)active.baseline : [:]
    String priorItem = baseline.itemId?.toString()
    String currentItem = snapshot.itemId?.toString()
    Long priorPosition = toLongValue(baseline.positionMillis)
    Long currentPosition = toLongValue(snapshot.positionMillis)
    Integer priorTrackNumber = baseline.trackNumber != null ? (baseline.trackNumber as Integer) : null
    Integer currentTrackNumber = getCurrentTrackNumber()
    confirmed = (priorItem && currentItem && priorItem != currentItem) ||
        (priorTrackNumber != null && currentTrackNumber != null && priorTrackNumber != currentTrackNumber) ||
        (priorPosition != null && currentPosition != null && priorPosition - currentPosition >= 3000L)
  }
  if(confirmed) {
    finishTransportCommand(active, true, "confirmed by playback status ${playbackState}")
  } else if(active.statusQueryPending == true) {
    active.statusQueryPending = false
    active.lastFailureReason = "status query returned ${playbackState ?: 'no playback state'} without confirming ${command}"
    logDebug("Watchdog did not confirm ${command} (${active.id}); state=${playbackState}, itemId=${snapshot.itemId}, position=${snapshot.positionMillis}")
  }
}

Long toLongValue(Object value) {
  if(value == null) { return null }
  try { return value.toString().toLong() }
  catch(Exception ignored) { return null }
}

void finishTransportCommand(Map active, Boolean succeeded, String detail) {
  if(active == null || runtimeState[TRANSPORT_ACTIVE_KEY]?.id != active.id) { return }
  if(!succeeded && active.websocketAcknowledged != true &&
      active.command in ['skipToNextTrack', 'skipToPreviousTrack'] && active.replayId) {
    List<Map> queued = runtimeState[TRANSPORT_QUEUE_KEY] instanceof List
        ? (List<Map>)runtimeState[TRANSPORT_QUEUE_KEY]
        : []
    Integer discarded = queued.count { Map item -> item.replayId?.toString() == active.replayId?.toString() }
    if(discarded > 0) {
      queued = queued.findAll { Map item -> item.replayId?.toString() != active.replayId?.toString() }
      runtimeState[TRANSPORT_QUEUE_KEY] = queued
      logWarn("Discarded ${discarded} queued skip command(s) from replay ${active.replayId} because ${active.command} was not confirmed")
    }
  }
  unschedule('watchTransportCommand')
  unschedule('retryUnconfirmedTransportCommand')
  runtimeState.remove(TRANSPORT_ACTIVE_KEY)
  if(succeeded) { logDebug("Transport command ${active.command} (${active.id}) confirmed: ${detail}") }
  else if(active.websocketAcknowledged == true) {
    logWarn("Transport command ${active.command} (${active.id}) was acknowledged by Sonos but not confirmed by playback status; no retry: ${detail}")
  }
  else { logWarn("Transport command ${active.command} (${active.id}) failed: ${detail}") }
  runIn(1, 'drainTransportCommandQueue', [overwrite: true])
}

void failTransportCommand(String response, String reason) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null) { return }
  String command = active.command as String
  if(response == command || (response == 'stop' && command == 'stop') ||
      (response == 'pause' && command == 'pause')) {
    active.lastFailureReason = reason ?: 'Sonos returned a command error'
    if(reason?.contains('ERROR_UNSUPPORTED_COMMAND') && command == 'stop') {
      finishTransportCommand(active, false, active.lastFailureReason as String)
      logInfo('Sonos does not support playback stop for this target; sending pause as the supported fallback')
      enqueueTransportCommand('pause')
    } else if(reason?.contains('ERROR_PLAYBACK_FAILED') && command in ['skipToNextTrack', 'skipToPreviousTrack']) {
      finishTransportCommand(active, false, active.lastFailureReason as String)
    } else {
      retryUnconfirmedTransportCommand([commandId: active.id])
    }
  }
}

@CompileStatic
void playerPlay() {
  enqueueTransportCommand('play')
}

@CompileStatic
void playerPause() {
  enqueueTransportCommand('pause')
}

@CompileStatic
void playerStop() {
  enqueueTransportCommand('stop')
}

@CompileStatic
void playerSkipToNextTrack() {
  enqueueTransportCommand('skipToNextTrack')
}

@CompileStatic
void playerSkipToPreviousTrack() {
  enqueueTransportCommand('skipToPreviousTrack')
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
  logWebSocketCommandPayload(command, json)
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
    'volume': volume,
  ]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
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
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void getPlaylists() {
  Map command = [
    'namespace':'playlists',
    'command':'getPlaylists',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [:]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void playerLoadFavorite(String favoriteId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  playerLoadFavoriteCommand(favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion, getGroupId(), true)
}

/** Build only the play-mode fields requested by the caller; null means preserve. */
@CompileStatic
private Map<String, Boolean> buildRequestedPlayModes(Boolean repeat, Boolean repeatOne,
    Boolean shuffle, Boolean crossfade) {
  Map<String, Boolean> playModes = new LinkedHashMap<String, Boolean>()
  if(repeat != null) { playModes.put('repeat', repeat) }
  if(repeatOne != null) { playModes.put('repeatOne', repeatOne) }
  if(shuffle != null) { playModes.put('shuffle', shuffle) }
  if(crossfade != null) { playModes.put('crossfade', crossfade) }
  return playModes
}

@CompileStatic
void playerLoadFavoriteForGroupOperation(String favoriteId, String action, Boolean repeat, Boolean repeatOne,
    Boolean shuffle, Boolean crossfade, Boolean playOnCompletion, String groupId) {
  playerLoadFavoriteCommand(favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion, groupId, false)
}

@CompileStatic
private void playerLoadFavoriteCommand(String favoriteId, String action, Boolean repeat, Boolean repeatOne,
    Boolean shuffle, Boolean crossfade, Boolean playOnCompletion, String groupId,
    Boolean scheduleAmazonWorkaround) {
  if(playOnCompletion == true) { invalidateGroupAnnouncementStream('FAVORITE_LOAD_REQUESTED') }
  Map command = [
    'namespace':'favorites',
    'command':'loadFavorite',
    'groupId':"${groupId}"
  ]
  Map<String, Boolean> playModes = buildRequestedPlayModes(repeat, repeatOne, shuffle, crossfade)
  Map args = [
    'favoriteId': favoriteId,
    'action': action,
    'playModes': playModes,
    'playOnCompletion': playOnCompletion
  ]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)

  // Amazon Music doesn't honor playOnCompletion parameter - schedule manual play as workaround
  if(playOnCompletion && scheduleAmazonWorkaround) {
    Map favorite = findFavoriteById(favoriteId)
    String serviceName = favorite?.service
    if(isAmazonMusicService(serviceName)) {
      logDebug("Amazon Music favorite detected - scheduling auto-play in 3 seconds as workaround for service limitation")
      scheduleAmazonMusicAutoPlay()
    }
  }
}

void scheduleAmazonMusicAutoPlay() {
  runIn(3, 'playerPlay', [overwrite: true])
}

void cancelPendingAmazonMusicAutoPlay() {
  unschedule('playerPlay')
}

void playerLoadPlaylist(String playlistId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  if(playOnCompletion == true) { invalidateGroupAnnouncementStream('PLAYLIST_LOAD_REQUESTED') }
  Map command = [
    'namespace':'playlists',
    'command':'loadPlaylist',
    'groupId':"${getGroupId()}"
  ]
  Map<String, Boolean> playModes = buildRequestedPlayModes(repeat, repeatOne, shuffle, crossfade)
  Map args = [
    'playlistId': playlistId,
    'action': action,
    'playModes': playModes,
    'playOnCompletion': playOnCompletion
  ]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)

  // Amazon Music doesn't honor playOnCompletion parameter - schedule manual play as workaround
  if(playOnCompletion) {
    Map playlist = getPlaylistsMap()?.get(playlistId)
    String serviceName = playlist?.service
    if(serviceName?.toLowerCase()?.contains('amazon')) {
      logDebug("Amazon Music playlist detected - scheduling auto-play in 3 seconds as workaround for service limitation")
      scheduleAmazonMusicAutoPlay()
    }
  }
}

/** Explicit zero (including the -1 public-command sentinel) stays silent despite a TTS boost. */
@CompileStatic
static Integer calculateAnnouncementVolume(BigDecimal requestedVolume, Integer currentVolume, Integer boost) {
  if(requestedVolume != null && requestedVolume.compareTo(BigDecimal.valueOf(-1L)) == 0) { return 0 }
  if(requestedVolume != null && requestedVolume.compareTo(BigDecimal.ZERO) == 0) { return 0 }
  BigDecimal baseVolume = requestedVolume != null ? requestedVolume : new BigDecimal(currentVolume)
  return Math.max(0, Math.min((baseVolume + boost) as Integer, 100))
}

@CompileStatic
static BigDecimal resolveExplicitVolumeSentinel(BigDecimal volume) {
  if(volume != null && volume.compareTo(BigDecimal.valueOf(-1L)) == 0) { return BigDecimal.ZERO }
  return volume
}

@CompileStatic
Integer getAnnouncementVolume(BigDecimal requestedVolume) {
  Integer currentVolume = getPlayerVolume()
  Integer boost = getTTSBoostAmount()
  Integer resolvedVolume = calculateAnnouncementVolume(requestedVolume, currentVolume, boost)
  logDebug("Announcement volume: requested=${requestedVolume}, current=${currentVolume}, boost=${boost}, resolved=${resolvedVolume}")
  return resolvedVolume
}

@CompileStatic
void playerLoadAudioClipHighPriority(String uri = null, BigDecimal volume = null, String correlationId = null,
    Integer duration = 0, Boolean deferStart = false, Boolean chimeBeforeTTS = false,
    Boolean holdAfterChime = false) {
  if(deferCommandToParentDuringGroupOperation('loadHighPriorityAudioClip', [uri, volume, correlationId, duration])) { return }
  logDebug('playerLoadAudioClipHighPriority')
  String clipCorrelationId = correlationId ?: UUID.randomUUID().toString()

  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  Map args = ['name': clipCorrelationId, 'appId': 'com.hubitat.sonos', 'priority': 'HIGH']
  if(uri) {args.streamUrl = uri}
  args.volume = getAnnouncementVolume(volume)
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [ leftChannel: json, priority: 'HIGH' ]
  if(duration != null && duration > 0) { audioClip.duration = duration }
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  audioClip.uri = uri
  audioClip.origin = 'high-priority-player-tts'
  audioClip.correlationId = clipCorrelationId
  audioClip.groupAnnouncementCorrelationId = correlationId
  addAudioClipCorrelation(audioClip)

  // A dispatched priority clip has already left the queue. Include the active
  // payload so another priority request cannot overwrite the suspended queue
  // or release the dispatcher while that clip is still playing.
  ConcurrentLinkedQueue<Map> highPriorityQueue = getDeviceAudioClipQueueHighPriority()
  Map activeClip = (Map)getAtomicStateValue('activeAudioClipMessage')
  Boolean audioClipPlaying = getAtomicStateValue('audioClipPlaying') as Boolean
  Boolean highPriorityActive = audioClipPlaying == true && activeClip?.priority == 'HIGH'
  Boolean isFirstHighPriority = highPriorityQueue.isEmpty() && !highPriorityActive

  traceAudioClipQueues("priority-admission incoming=${clipCorrelationId}, first=${isFirstHighPriority}, volume=${args.volume}")
  if(isFirstHighPriority) {
    // Save currently playing + all queued regular priority clips
    saveRegularAudioClipQueue()
    // Clear regular queue
    getDeviceAudioClipQueue().clear()
    interruptActiveAudioClipForHighPriority()
    // Cancel the regular clip timer and release the dispatcher for priority audio.
    cancelAudioClipWatchdog()
  }

  if(chimeBeforeTTS == true) {
    String chimeCorrelationId = UUID.randomUUID().toString()
    Map<String,String> chimeCommand = [
      'namespace':'audioClip',
      'command':'loadAudioClip',
      'playerId':"${getId()}".toString()
    ]
    Map chimeArgs = [
      'name': chimeCorrelationId,
      'appId': 'com.hubitat.sonos',
      'clipType': 'CHIME',
      'priority': 'HIGH',
      'volume': args.volume
    ]
    Map<String,Object> highPriorityChime = new LinkedHashMap<String,Object>()
    highPriorityChime.put('leftChannel', JsonOutput.toJson([chimeCommand, chimeArgs]))
    highPriorityChime.put('priority', 'HIGH')
    highPriorityChime.put('duration', 8)
    highPriorityChime.put('origin', 'high-priority-player-chime')
    highPriorityChime.put('correlationId', chimeCorrelationId)
    highPriorityChime.put('groupAnnouncementCorrelationId', holdAfterChime == true ? correlationId : null)
    highPriorityChime.put('synchronizedGroupChime', holdAfterChime == true)
    if(loadAudioClipOnRightChannel()) {
      chimeCommand.playerId = "${getRightChannelId()}".toString()
      highPriorityChime.put('rightChannel', JsonOutput.toJson([chimeCommand, chimeArgs]))
    }
    addAudioClipCorrelation(highPriorityChime)
    highPriorityQueue.add(highPriorityChime)
    if(holdAfterChime == true && correlationId) {
      Map<String,Object> synchronizedHold = new LinkedHashMap<String,Object>()
      synchronizedHold.put('correlationId', correlationId)
      synchronizedHold.put('allowChimeDispatch', true)
      runtimeState.put('groupSynchronizedAudioClipHold', synchronizedHold)
    }
  }

  // Add to high-priority queue
  highPriorityQueue.add(audioClip)
  traceAudioClipQueues('priority-enqueued')

  // If first high-priority or nothing playing, start immediately
  audioClipPlaying = getAtomicStateValue('audioClipPlaying') as Boolean
  if(!deferStart && (isFirstHighPriority || audioClipPlaying != true)) {
    dequeueAudioClip()
  }
}

@CompileStatic
void playerLoadAudioClip(String uri = null, BigDecimal volume = null, Integer duration = 0, String correlationId = null) {
  if(deferCommandToParentDuringGroupOperation('loadAudioClip', [uri, volume, duration, correlationId])) { return }
  logDebug('playerLoadAudioClip')
  if(getIsMuted()) {
    logDebug('Skipping loadAudioClip notification because player is muted.')
    return
  }
  String clipCorrelationId = correlationId ?: UUID.randomUUID().toString()
  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  Map args = ['name': clipCorrelationId, 'appId': 'com.hubitat.sonos']
  if(uri) {args.streamUrl = uri}
  args.volume = getAnnouncementVolume(volume)
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [ leftChannel: json ]
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  if(getAudioClipQueueIsEmpty() && getChimeBeforeStandardPriorityTTS()) {playerLoadAudioClipChime(volume)}
  if(duration > 0) { audioClip.duration = duration }
  audioClip.uri = uri
  audioClip.origin = 'player-tts'
  audioClip.correlationId = clipCorrelationId
  logDebug("Enqueuing audio clip with URI: ${uri}, volume: ${args.volume}, duration: ${duration}")
  enqueueAudioClip(audioClip)
  logDebug('Enqueued')
}

/** Queue a clip without starting it immediately so a parent can release several
 * ungrouped players on the same Hubitat scheduler tick.
 */
void playerPrepareAudioClipForSynchronizedStart(String uri = null, BigDecimal volume = null, String correlationId = null,
    Integer duration = 0, Boolean deferStart = false, Boolean chimeBeforeTTS = null,
    Boolean holdAfterChime = false) {
  if(getIsMuted()) {
    logDebug('Skipping synchronized audio clip because player is muted.')
    if(correlationId) { publishGroupAnnouncementProgress(correlationId, 'DISMISSED', [reason: 'muted']) }
    return
  }
  String clipCorrelationId = correlationId ?: UUID.randomUUID().toString()
  ConcurrentLinkedQueue<Map> queue = getDeviceAudioClipQueue()
  Boolean shouldChime = chimeBeforeTTS != null ? chimeBeforeTTS : getChimeBeforeStandardPriorityTTS()
  if(queue.isEmpty() && shouldChime) {
    String chimeCorrelationId = UUID.randomUUID().toString()
    Map<String,String> chimeCommand = [
      'namespace':'audioClip',
      'command':'loadAudioClip',
      'playerId':"${getId()}".toString()
    ]
    Map chimeArgs = ['name': chimeCorrelationId, 'appId': 'com.hubitat.sonos', 'clipType': 'CHIME']
    if(volume != null) { chimeArgs.volume = volume }
    Map chimeClip = [leftChannel: JsonOutput.toJson([chimeCommand,chimeArgs]), duration: 8]
    chimeClip.origin = 'synchronized-player-chime'
    chimeClip.correlationId = chimeCorrelationId
    if(holdAfterChime == true) { chimeClip.groupAnnouncementCorrelationId = correlationId }
    chimeClip.synchronizedGroupChime = holdAfterChime == true
    addAudioClipCorrelation(chimeClip)
    if(loadAudioClipOnRightChannel()) {
      chimeCommand.playerId = "${getRightChannelId()}".toString()
      chimeClip.rightChannel = JsonOutput.toJson([chimeCommand,chimeArgs])
    }
    queue.add(chimeClip)
    if(holdAfterChime == true && correlationId) {
      Map<String,Object> synchronizedHold = new LinkedHashMap<String,Object>()
      synchronizedHold.put('correlationId', correlationId)
      synchronizedHold.put('allowChimeDispatch', true)
      runtimeState.put('groupSynchronizedAudioClipHold', synchronizedHold)
    }
  }
  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  Map args = ['name': clipCorrelationId, 'appId': 'com.hubitat.sonos']
  if(uri) { args.streamUrl = uri }
  args.volume = getAnnouncementVolume(volume)
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [leftChannel: json, uri: uri]
  if(duration != null && duration > 0) { audioClip.duration = duration }
  audioClip.origin = 'synchronized-player-tts'
  audioClip.correlationId = clipCorrelationId
  audioClip.groupAnnouncementCorrelationId = correlationId
  addAudioClipCorrelation(audioClip)
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  queue.add(audioClip)
  logDebug("Prepared loadAudioClip for Sonos player ${getId()}, correlation=${clipCorrelationId}, resolvedVolume=${args.volume}, queued=${queue.size()}, deferStart=${deferStart}")
  if(!deferStart && runtimeState.audioClipPlaying != true) {
    runInMillis(100, 'prepareAndStartAudioClip', [overwrite: true])
  }
}

/** Select and fully track the next queued clip before a synchronized release pass. */
void prepareAudioClipForDispatch() {
  if(runtimeState.audioClipPlaying != true) { prepareNextAudioClipForDispatch() }
}

Boolean canPrepareSynchronizedGroupChime(Boolean highPriority) {
  return highPriority ? getDeviceAudioClipQueueHighPriority().isEmpty() : getDeviceAudioClipQueue().isEmpty()
}

void prepareAndStartAudioClip() {
  prepareAudioClipForDispatch()
  startPreparedAudioClip()
  completePreparedAudioClipDispatch()
}

/** Called in the tight group release loop; keep the two socket writes adjacent. */
void startPreparedAudioClip() {
  if(runtimeState.audioClipPlaying != true) {
    logDebug("No active prepared audioClip to release for Sonos player ${getId()}")
    return
  }
  Map clipMessage = (Map)getAtomicStateValue('activeAudioClipMessage')
  String message = clipMessage?.leftChannel?.toString()
  String correlationId = clipMessage?.correlationId?.toString()
  if(!message || !correlationId) {
    logDebug("Prepared audioClip is missing its primary message or correlation ID: player=${getId()}, hasMessage=${message != null}, correlation=${correlationId}")
    runtimeState.preparedAudioClipDispatchResult = 'missing'
    return
  }

  ChildDeviceWrapper rightChannel = clipMessage.rightChannel ? getRightChannelChild() : null
  String pairedRightMessage = clipMessage.rightChannel?.toString()
  if(pairedRightMessage && rightChannel) {
    watchRightChannelWebsocket(rightChannel)
    if(rightChannel.getDataValue('websocketStatus') != 'open') {
      rightChannel.initializeWebsocketConnection()
    }
  } else if(pairedRightMessage) {
    runtimeState.preparedAudioClipRightChannelMissing = true
    logDebug("Paired audioClip has no right-channel child: correlation=${correlationId}; proceeding with primary only")
    pairedRightMessage = null
  }

  Map metadata = [kind: 'audioClip', correlationId: correlationId]
  if(pairedRightMessage) { metadata.pairedRightChannelMessage = pairedRightMessage }
  Boolean primaryReady = isWebsocketConnected() && getWebsocketOutboundQueue().isEmpty() && isCurrentlySubcribedToAudioClipWS()
  Boolean rightReady = !pairedRightMessage || rightChannel?.getDataValue('websocketStatus') == 'open'
  logDebug("AudioClip paired-send readiness: correlation=${correlationId}, primaryStatus=${getWebSocketStatus()}, primarySubscribed=${isCurrentlySubcribedToAudioClipWS()}, primaryQueue=${getWebsocketOutboundQueue().size()}, secondaryStatus=${rightChannel?.getDataValue('websocketStatus') ?: (pairedRightMessage ? 'child unavailable' : 'not paired')}, primaryReady=${primaryReady}, secondaryReady=${rightReady}")
  if(!primaryReady || !rightReady) {
    if(pairedRightMessage && !rightReady) {
      metadata.pairedSecondaryDeadline = now() + (PAIRED_AUDIO_CLIP_SECONDARY_TIMEOUT_SECONDS * 1000L)
    }
    queueWebsocketMessage(message, 0, metadata)
    if(pairedRightMessage && !rightReady) {
      Map queuedPair = getWebsocketOutboundQueue().find { Map entry ->
        entry?.correlationId?.toString() == correlationId
      }
      if(queuedPair) { schedulePairedAudioClipSecondaryTimeout(queuedPair) }
    }
    logDebug("Queued audioClip dispatch as ${pairedRightMessage ? 'one paired entry' : 'primary-only entry'}: correlation=${correlationId}, reason=${!primaryReady ? 'primary socket/queue/subscription not ready' : 'secondary socket not open'}, queueSize=${getWebsocketOutboundQueue().size()}")
    runtimeState.preparedAudioClipDispatchResult = 'queued'
    runtimeState.preparedAudioClipDispatchCorrelationId = correlationId
    if(!isWebsocketConnected()) { wsConnect() }
    else if(!isCurrentlySubcribedToAudioClipWS() && runtimeState.audioClipSubscriptionRequestPending != true) { subscribeToAudioClip() }
    return
  } else {
    Boolean primarySendReturned = false
    Map audioOutbound = new LinkedHashMap(metadata)
    audioOutbound.message = message
    audioOutbound.createdAt = now()
    audioOutbound.expiresAt = (audioOutbound.createdAt as Long) + WS_COMMAND_MAX_AGE_SECONDS * 1000L
    audioOutbound.putAll(describeWebSocketCommand(message))
    try {
      logDebug("Starting tight paired audioClip send pass: correlation=${correlationId}, primaryStatus=open, secondaryStatus=${rightChannel?.getDataValue('websocketStatus') ?: 'not paired'}")
      registerWebsocketCommandLedgerEntry(message, audioOutbound, audioOutbound.createdAt as Long)
      interfaces.webSocket.sendMessage(message)
      primarySendReturned = true
      if(pairedRightMessage && rightChannel) {
        Boolean rightSent = rightChannel.playerLoadAudioClipForSynchronizedRelease(
          pairedRightMessage, correlationId) == true
        rightChannel.completeSynchronizedAudioClipRelease(correlationId)
        logDebug("Tight paired audioClip send pass returned: correlation=${correlationId}, primary=sent, secondary=${rightSent ? 'sent' : 'failed'}")
        if(!rightSent) {
          logInfo("Secondary audio clip send failed after primary dispatch for ${correlationId}; not replaying the secondary clip")
          runtimeState.preparedAudioClipDispatchResult = 'primaryOnly'
        } else {
          runtimeState.preparedAudioClipDispatchResult = 'sent'
        }
      } else {
        logDebug("Primary audioClip send returned successfully without a paired channel: correlation=${correlationId}")
        runtimeState.preparedAudioClipDispatchResult = 'sent'
      }
      runtimeState.preparedAudioClipDispatchCorrelationId = correlationId
    } catch(Exception e) {
      logDebug("Tight audioClip send pass threw: correlation=${correlationId}, primarySendReturned=${primarySendReturned}, secondaryStatus=${rightChannel?.getDataValue('websocketStatus') ?: 'not paired'}, error=${e.message}")
      if(!primarySendReturned) { removeWebsocketCommandLedgerEntryById(audioOutbound.ledgerEntryId?.toString(), 'primary audioClip send threw') }
      if(primarySendReturned && pairedRightMessage) {
        try {
          rightChannel?.completeSynchronizedAudioClipRelease(correlationId)
        } catch(Exception completionError) {
          logDebug("Could not complete secondary release logging for ${correlationId}: ${completionError.message}")
        }
        logInfo("Secondary audio clip dispatch threw after primary dispatch for ${correlationId}; not replaying either channel")
        runtimeState.preparedAudioClipDispatchResult = 'primaryOnly'
      } else {
        queueWebsocketMessage(message, 1, metadata)
        runtimeState.preparedAudioClipDispatchResult = 'failed'
      }
      runtimeState.preparedAudioClipDispatchCorrelationId = correlationId
      if(!primarySendReturned) { runtimeState.preparedAudioClipDispatchError = e.message?.toString() }
    }
  }
}

/** Run acknowledgements, reconnect handling, and diagnostic logs after the group send pass. */
void completePreparedAudioClipDispatch() {
  String result = runtimeState.preparedAudioClipDispatchResult?.toString()
  String correlationId = runtimeState.preparedAudioClipDispatchCorrelationId?.toString()
  String error = runtimeState.preparedAudioClipDispatchError?.toString()
  Boolean rightChannelMissing = runtimeState.preparedAudioClipRightChannelMissing == true
  runtimeState.remove('preparedAudioClipDispatchResult')
  runtimeState.remove('preparedAudioClipDispatchCorrelationId')
  runtimeState.remove('preparedAudioClipDispatchError')
  runtimeState.remove('preparedAudioClipRightChannelMissing')

  if(result == 'sent') {
    logDebug("Sent prepared audioClip to Sonos player ${getId()}, correlation=${correlationId}")
    audioClipCommandSent(correlationId)
  } else if(result == 'queued') {
    logDebug("Queued prepared audioClip for Sonos player ${getId()}, correlation=${correlationId}, primaryStatus=${getWebSocketStatus()}, primarySubscribed=${isCurrentlySubcribedToAudioClipWS()}, queueSize=${getWebsocketOutboundQueue().size()}")
    if(!isWebsocketConnected()) { wsConnect() }
    else if(!isCurrentlySubcribedToAudioClipWS()) {
      if(runtimeState.audioClipSubscriptionRequestPending != true) { subscribeToAudioClip() }
    } else if(getWebsocketOutboundQueue().size() > 0) { flushWebsocketOutboundQueue() }
  } else if(result == 'primaryOnly') {
    logDebug("Completing primary-only audioClip dispatch: player=${getId()}, correlation=${correlationId}; secondary will not be replayed")
    logInfo("Primary audio clip sent for ${getId()}, correlation=${correlationId}; secondary dispatch failed and will not be replayed")
    audioClipCommandSent(correlationId)
  } else if(result == 'failed') {
    logWarn("Prepared audioClip send failed for Sonos player ${getId()}, correlation=${correlationId}: ${error ?: 'unknown error'}; queued for retry")
    setWebSocketStatus('closed')
    scheduleWebSocketReconnect()
  } else if(result == 'missing') {
    logWarn("No prepared audioClip was available to release for Sonos player ${getId()}")
  }

  Map activeClip = (Map)getAtomicStateValue('activeAudioClipMessage')
  if(rightChannelMissing || activeClip?.rightChannel && !getRightChannelChild()) {
    logWarn('Right channel audio clip requested but no right channel child device is available. Playing clip on left channel only.')
  }
}


@CompileStatic
void playerLoadAudioClipChime(BigDecimal volume = null) {
  logDebug('playerLoadAudioClipChime')
  if(getIsMuted()) {
    logDebug('Skipping loadAudioClip notification because player is muted.')
    return
  }
  Map<String,String> command = [
    'namespace':'audioClip',
    'command':'loadAudioClip',
    'playerId':"${getId()}".toString()
  ]
  String correlationId = UUID.randomUUID().toString()
  Map args = ['name': correlationId, 'appId': 'com.hubitat.sonos', "clipType": "CHIME"]
  if(volume != null) {args.volume = volume}
  String json = JsonOutput.toJson([command,args])
  Map audioClip = [ leftChannel: json ]
  if(loadAudioClipOnRightChannel()) {
    command.playerId = "${getRightChannelId()}".toString()
    audioClip.rightChannel = JsonOutput.toJson([command,args])
  }
  audioClip.duration = 8
  audioClip.origin = 'player-chime'
  audioClip.correlationId = correlationId
  enqueueAudioClip(audioClip)
}

void enqueueAudioClip(Map clipMessage) {
  logDebug('enqueueAudioClip')
  addAudioClipCorrelation(clipMessage)
  ConcurrentLinkedQueue<Map> queue = getDeviceAudioClipQueue()
  Boolean alreadyPlaying = runtimeState.audioClipPlaying == true
  logDebug("Audio clip enqueue decision correlation=${clipMessage.correlationId}, origin=${clipMessage.origin}, priority=${clipMessage.priority ?: 'REGULAR'}, alreadyPlaying=${alreadyPlaying}, active=${runtimeState.activeAudioClipCorrelationId ?: 'none'}, synchronizedHold=${runtimeState.groupSynchronizedAudioClipHold instanceof Map ? ((Map)runtimeState.groupSynchronizedAudioClipHold).correlationId : 'none'}, regularBefore=${queue.size()}, highBefore=${getDeviceAudioClipQueueHighPriority().size()}")

  // Regular priority clips go to regular queue
  // High priority clips are now handled separately via playerLoadAudioClipHighPriority
  queue.add(clipMessage)
  logDebug("Queued audio clip ${clipMessage.correlationId} (${clipMessage.origin}) at ${clipMessage.enqueuedAt}; regular=${queue.size()}, high=${getDeviceAudioClipQueueHighPriority().size()}")

  if(!alreadyPlaying) {
    logDebug("Starting audio clip drain after enqueue correlation=${clipMessage.correlationId}")
    dequeueAudioClip()
  } else {
    logDebug("Audio clip remains queued behind active correlation=${runtimeState.activeAudioClipCorrelationId ?: 'unknown'}; newCorrelation=${clipMessage.correlationId}, regular=${queue.size()}, high=${getDeviceAudioClipQueueHighPriority().size()}")
  }
}

void dequeueAudioClip() {
  prepareNextAudioClipForDispatch()
  dispatchPreparedAudioClip()
}

void prepareNextAudioClipForDispatch() {
  traceAudioClipQueues('dequeue-entry')
  logDebug('dequeueAudioClip')

  Map synchronizedHold = runtimeState.groupSynchronizedAudioClipHold instanceof Map
      ? (Map)runtimeState.groupSynchronizedAudioClipHold : null
  if(synchronizedHold != null) {
    if(synchronizedHold.allowChimeDispatch == true) {
      synchronizedHold.allowChimeDispatch = false
      runtimeState.groupSynchronizedAudioClipHold = synchronizedHold
    } else {
      logDebug("Holding queued audioClip for synchronized group TTS ${synchronizedHold.correlationId}")
      return
    }
  }

  // Check high-priority queue first
  ConcurrentLinkedQueue<Map> highPriorityQueue = getDeviceAudioClipQueueHighPriority()
  Map clipMessage = highPriorityQueue.poll()

  // If no high-priority clips, check if we should restore saved queue
  if(!clipMessage) {
    // If high-priority queue is empty and saved queue has items, restore them
    if(!getDeviceAudioClipQueueSaved().isEmpty()) {
      restoreRegularAudioClipQueue()
    }
    // Now get from regular queue
    clipMessage = getDeviceAudioClipQueue().poll()
  }

  if(!clipMessage) {
    logDebug('No more audio clips to dequeue.')
    removeHiddenJsonRuntimeState('currentlyPlayingClip')
    runtimeState.remove('activeAudioClipCorrelationId')
    runtimeState.remove('activeAudioClipSonosId')
    removeHiddenJsonRuntimeState('activeAudioClipMessage')
    runtimeState.remove('audioClipPlaying')
    runtimeState.remove('audioClipCommandSent')
    runtimeState.remove('audioClipHadDisconnectAfterSend')
    runtimeState.remove('audioClipSentAt')
    removeHiddenJsonRuntimeState('audioClipProgressByCorrelationId')
    return
  }

  Integer previousDispatchCount = (clipMessage.dispatchCount ?: 0) as Integer
  if(previousDispatchCount > 0) {
    rotateAudioClipCorrelationId(clipMessage)
  }
  clipMessage.dispatchCount = previousDispatchCount + 1

  // Save currently playing clip (only if it's NOT high priority)
  // High priority clips shouldn't be saved for replay
  if(clipMessage?.priority != 'HIGH') {
    setHiddenJsonRuntimeState('currentlyPlayingClip', clipMessage)
  } else {
    removeHiddenJsonRuntimeState('currentlyPlayingClip')
  }

  runtimeState.activeAudioClipCorrelationId = clipMessage.correlationId
  setHiddenJsonRuntimeState('activeAudioClipMessage', clipMessage)
  runtimeState.remove('activeAudioClipSonosId')
  runtimeState.audioClipCommandSent = false
  runtimeState.audioClipHadDisconnectAfterSend = false
  Map<String, Map> clipProgress = runtimeState.audioClipProgressByCorrelationId instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)runtimeState.audioClipProgressByCorrelationId)
      : new LinkedHashMap<String, Map>()
  clipProgress[clipMessage.correlationId as String] = [
    correlationId: clipMessage.correlationId,
    groupAnnouncementCorrelationId: clipMessage.groupAnnouncementCorrelationId,
    origin: clipMessage.origin ?: 'audio-clip',
    phase: 'QUEUED_FOR_WEBSOCKET',
    duration: coerceClipDuration(clipMessage.duration),
    dispatchCount: clipMessage.dispatchCount,
    enqueuedAt: clipMessage.enqueuedAt,
    queuedForSendAt: now()
  ]
  while(clipProgress.size() > 50) {
    String oldestGuid = clipProgress.find { String guid, Map item ->
      guid != (clipMessage.correlationId as String) && guid != (runtimeState.activeAudioClipCorrelationId as String)
    }?.key as String
    if(!oldestGuid) { break }
    clipProgress.remove(oldestGuid)
  }
  setHiddenJsonRuntimeState('audioClipProgressByCorrelationId', clipProgress)
  publishAudioClipProgressEvent(clipProgress[clipMessage.correlationId as String], null)
  logInfo("Prepared audio clip ${clipMessage.correlationId} (${clipMessage.origin ?: 'unknown'}), queuedAt=${clipMessage.enqueuedAt ?: 'unknown'}, preparedAt=${now()}, regular=${getDeviceAudioClipQueue().size()}, high=${highPriorityQueue.size()}")
  runtimeState.audioClipPlaying = true
  traceAudioClipQueues('dispatch-prepared')
  if(!isCurrentlySubcribedToAudioClipWS()) { subscribeToAudioClip() }
}

/** Release the queued TTS clip after every group member's separate chime has completed. */
void prepareSynchronizedAudioClipAfterChime(String correlationId) {
  Map synchronizedHold = runtimeState.groupSynchronizedAudioClipHold instanceof Map
      ? (Map)runtimeState.groupSynchronizedAudioClipHold : null
  if(!correlationId || synchronizedHold?.correlationId?.toString() != correlationId) { return }
  runtimeState.remove('groupSynchronizedAudioClipHold')
  prepareAudioClipForDispatch()
}

void clearSynchronizedAudioClipHold(String correlationId) {
  Map synchronizedHold = runtimeState.groupSynchronizedAudioClipHold instanceof Map
      ? (Map)runtimeState.groupSynchronizedAudioClipHold : null
  if(correlationId && synchronizedHold?.correlationId?.toString() == correlationId) {
    runtimeState.remove('groupSynchronizedAudioClipHold')
  }
}

void dispatchPreparedAudioClip() {
  startPreparedAudioClip()
  completePreparedAudioClipDispatch()
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
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void playerCreateNewGroup() {
  List<String> playerIds = [getId()]
  sendCreateGroupRequest(playerIds)
}

@CompileStatic
void playerCreateGroup(List<String> playerIds, String musicContextGroupId = null) {
  List<String> requestedPlayers = (playerIds ?: []).findAll { String id -> id }.unique()
  sendCreateGroupRequest(requestedPlayers, musicContextGroupId)
}

@CompileStatic
void sendCreateGroupRequest(List<String> playerIds, String musicContextGroupId = null) {
  Map command = [
    'namespace':'groups',
    'command':'createGroup',
    'householdId':"${getHouseholdId()}"
  ]
  Map args = [
    'musicContextGroupId': musicContextGroupId,
    'playerIds': playerIds
  ]
  String json = JsonOutput.toJson([command,args])
  logWebSocketCommandPayload(command, json)
  sendWsMessage(json)
}

@CompileStatic
void playerModifyGroupMembers(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = [],
    Map<String, Map> expectedTopology = null) {
  List<String> playersToAdd = playerIdsToAdd != null ? playerIdsToAdd : new ArrayList<String>()
  List<String> playersToRemove = playerIdsToRemove != null ? playerIdsToRemove : new ArrayList<String>()
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
  logWebSocketCommandPayload(command, json)
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
  logDebug('setDialogMode')
  String ip = getDeviceDataValue('localUpnpHost')
  Map controlValues = [EQType: 'DialogLevel', DesiredValue: desiredValue ? 1 : 0]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetEQ', controlValues)
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
    logPayloadAtTrace('Successful local HTTP response received; body omitted', "localControlCallback: ${response.getData()}")
  }
}

String expectedUpnpSidForSoapAction(Map params) {
  String soapAction = params?.headers?.SOAPAction?.toString() ?: ''
  Integer separator = soapAction.lastIndexOf('#')
  if(separator < 0) { return null }
  String service = soapAction.substring(0, separator)
  String action = soapAction.substring(separator + 1)
  if(service.contains('AVTransport') && action in ['Play', 'Pause', 'Stop', 'Next', 'Previous', 'Seek',
      'SetAVTransportURI', 'RemoveAllTracksFromQueue', 'AddURIToQueue', 'ConfigureSleepTimer']) { return 'sid1' }
  if(service.contains('GroupRenderingControl') && action in ['SetGroupVolume', 'SetRelativeGroupVolume', 'SetGroupMute']) { return 'sid4' }
  if(service.contains('RenderingControl') && action in ['SetVolume', 'SetMute', 'SetBass', 'SetTreble', 'SetLoudness', 'SetEQ']) { return 'sid2' }
  return null
}

void postSonosSoapAsync(String callback, Map params) {
  String sidKey = expectedUpnpSidForSoapAction(params)
  String guid = sidKey ? armExpectedUpnpSubscriptionEvent(sidKey, params?.headers?.SOAPAction?.toString()) : null
  try {
    asynchttpPost(callback, params)
  } catch(Exception e) {
    if(guid) { cancelExpectedUpnpSubscriptionEvent(guid) }
    throw e
  }
}

void postSonosSoapAsync(String callback, Map params, Map data) {
  String sidKey = expectedUpnpSidForSoapAction(params)
  String guid = sidKey ? armExpectedUpnpSubscriptionEvent(sidKey, params?.headers?.SOAPAction?.toString()) : null
  try {
    asynchttpPost(callback, params, data)
  } catch(Exception e) {
    if(guid) { cancelExpectedUpnpSubscriptionEvent(guid) }
    throw e
  }
}

String armExpectedUpnpSubscriptionEvent(String sidKey, String action) {
  String guid = UUID.randomUUID().toString()
  List<Map> pending = runtimeState[UPNP_EXPECTED_EVENTS_KEY] instanceof List
      ? (List<Map>)runtimeState[UPNP_EXPECTED_EVENTS_KEY] : []
  pending.add([guid: guid, sidKey: sidKey, action: action, attempts: 0,
      deadlineAt: now() + UPNP_EXPECTED_EVENT_TIMEOUT_SECONDS * 1000L])
  runtimeState[UPNP_EXPECTED_EVENTS_KEY] = pending
  logDebug("Armed UPnP subscription event watchdog guid=${guid} action=${action} expected=${sidKey}")
  scheduleExpectedUpnpEventWatchdog()
  return guid
}

void cancelExpectedUpnpSubscriptionEvent(String guid) {
  List<Map> pending = runtimeState[UPNP_EXPECTED_EVENTS_KEY] instanceof List
      ? (List<Map>)runtimeState[UPNP_EXPECTED_EVENTS_KEY] : []
  pending.removeAll { Map item -> item.guid?.toString() == guid }
  if(pending.isEmpty()) { runtimeState.remove(UPNP_EXPECTED_EVENTS_KEY) }
  else { runtimeState[UPNP_EXPECTED_EVENTS_KEY] = pending }
  scheduleExpectedUpnpEventWatchdog()
}

void observeExpectedUpnpSubscriptionEvent(String sidKey) {
  List<Map> pending = runtimeState[UPNP_EXPECTED_EVENTS_KEY] instanceof List
      ? (List<Map>)runtimeState[UPNP_EXPECTED_EVENTS_KEY] : []
  List<Map> observed = pending.findAll { Map item -> item.sidKey?.toString() == sidKey }
  if(observed.isEmpty()) { return }
  pending.removeAll(observed)
  logDebug("Received ${sidKey} UPnP event; satisfied ${observed.size()} command expectation(s)")
  if(pending.isEmpty()) { runtimeState.remove(UPNP_EXPECTED_EVENTS_KEY) }
  else { runtimeState[UPNP_EXPECTED_EVENTS_KEY] = pending }
  scheduleExpectedUpnpEventWatchdog()
}

void scheduleExpectedUpnpEventWatchdog() {
  List<Map> pending = runtimeState[UPNP_EXPECTED_EVENTS_KEY] instanceof List
      ? (List<Map>)runtimeState[UPNP_EXPECTED_EVENTS_KEY] : []
  if(pending.isEmpty()) {
    unschedule('watchExpectedUpnpSubscriptionEvents')
    return
  }
  Long earliest = pending.collect { Map item -> item.deadlineAt as Long }.min() as Long
  Integer delay = Math.max(1, (int)Math.ceil((earliest - now()) / 1000.0d))
  runIn(delay, 'watchExpectedUpnpSubscriptionEvents', [overwrite: true])
}

void watchExpectedUpnpSubscriptionEvents() {
  List<Map> pending = runtimeState[UPNP_EXPECTED_EVENTS_KEY] instanceof List
      ? (List<Map>)runtimeState[UPNP_EXPECTED_EVENTS_KEY] : []
  Long currentTime = now()
  Long currentTimeEpoch = currentTime.longValue().intdiv(1000L)
  List<Map> due = pending.findAll { Map item -> (item.deadlineAt as Long) <= currentTime }
  due.each { Map expectation ->
    String sidKey = expectation.sidKey?.toString()
    Long lastEventEpoch = getLastInboundUpnpEventEpoch(sidKey)
    String lastEventAge = lastEventEpoch > 0L
        ? "${Math.max(0L, currentTimeEpoch - lastEventEpoch)}s ago"
        : 'not observed in this runtime'
    if(((expectation.attempts ?: 0) as Integer) < 1) {
      if(sidKey == 'sid4' && !getIsGrouped()) {
        logDebug("Skipping GroupRenderingControl resubscribe after ${expectation.action}; player is no longer grouped (guid=${expectation.guid})")
        pending.remove(expectation)
        return
      }
      logWarn("No UPnP ${sidKey} subscription event arrived after ${expectation.action}; last event ${lastEventAge}; resubscribing (guid=${expectation.guid})")
      resubscribeUpnpSid(sidKey)
      expectation.attempts = 1
      expectation.deadlineAt = currentTime + UPNP_EXPECTED_EVENT_TIMEOUT_SECONDS * 1000L
    } else {
      logWarn("No UPnP ${sidKey} event arrived after resubscription for ${expectation.action}; last event ${lastEventAge} (guid=${expectation.guid})")
      pending.remove(expectation)
    }
  }
  if(pending.isEmpty()) { runtimeState.remove(UPNP_EXPECTED_EVENTS_KEY) }
  else { runtimeState[UPNP_EXPECTED_EVENTS_KEY] = pending }
  scheduleExpectedUpnpEventWatchdog()
}

@CompileStatic
Long getLastInboundUpnpEventEpoch(String sidKey) {
  switch(sidKey) {
    case 'sid1': return getLastInboundAVTransportEventEpoch()
    case 'sid2': return getLastInboundMrRcEventEpoch()
    case 'sid3': return getLastInboundZgtEventEpoch()
    case 'sid4': return getLastInboundMrGrcEventEpoch()
    default: return 0L
  }
}

void resubscribeUpnpSid(String sidKey) {
  switch(sidKey) {
    case 'sid1': resubscribeToAVTransport(); break
    case 'sid2': resubscribeToMrRcEvents(); break
    case 'sid3': resubscribeToZgtEvents(); break
    case 'sid4': resubscribeToMrGrcEvents(); break
    default: logWarn("No UPnP resubscribe handler for ${sidKey}")
  }
}

void httpPostAsync(Map params, String callback = 'localControlCallback') {
  postSonosSoapAsync(callback, params)
}
// =============================================================================
// End SOAP Commands
// =============================================================================



// =============================================================================
// Local Control Component Methods
// =============================================================================
String getLocalUpnpHostForGroupOperation() {
  String localHost = getlocalUpnpHost()
  if(getIsGrouped() && !getIsGroupCoordinator()) {
    // A follower can still be invoked directly by an external automation. Use
    // its local endpoint rather than synchronously looking up the coordinator
    // through the parent app; the parent-mediated group command path resolves
    // the actual coordinator before invoking this operation.
    logDebug("Using local endpoint for grouped follower ${device.displayName}")
  }
  return localHost
}

void componentPlayTextNoRestoreLocal(String text, BigDecimal volume = null, String voice = null) {
  logDebug("${device} play text ${text} (volume ${volume != null ? volume : 'not set'})")
  Map tts = textToSpeech(text, voice)
  String streamUrl = tts?.uri?.toString()
  if(!streamUrl) { logWarn('TTS did not return a stream URI'); return }
  Integer duration = coerceClipDuration(tts?.duration) ?: 30
  Integer previousGroupVolume = getGroupVolumeState()
  if(previousGroupVolume == null) { previousGroupVolume = getPlayerVolume() }
  volume = resolveExplicitVolumeSentinel(volume)
  if(volume != null) {componentSetGroupLevelLocal(volume)}
  String correlationId = UUID.randomUUID().toString()
  runtimeState.directStreamTtsVolumeRestore = [correlationId: correlationId, streamUri: streamUrl,
      duration: duration, previousGroupVolume: previousGroupVolume, announcementVolume: volume as Integer]
  setAVTransportURIAndPlay(streamUrl)
  runIn(Math.max(duration + 2, 5), 'restoreDirectStreamTtsVolume', [overwrite: true,
      data: [correlationId: correlationId]])
}

void restoreDirectStreamTtsVolume(Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.directStreamTtsVolumeRestore instanceof Map
      ? (Map)runtimeState.directStreamTtsVolumeRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  String currentSource = device.currentValue('audioSource')?.toString()
  if(currentSource && currentSource != active.streamUri?.toString() &&
      state.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']) {
    logInfo("Skipping direct TTS volume restore ${correlationId}; another source is active")
    runtimeState.remove('directStreamTtsVolumeRestore')
    return
  }
  restoreTemporaryAnnouncementVolume(correlationId, active.previousGroupVolume as Integer,
      active.announcementVolume as Integer)
  runtimeState.remove('directStreamTtsVolumeRestore')
}

void scheduleDirectStreamTrackVolumeRestore(Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.directStreamTrackVolumeRestore instanceof Map
      ? (Map)runtimeState.directStreamTrackVolumeRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  String currentSource = device.currentValue('audioSource')?.toString()
  if(currentSource && currentSource != active.streamUri?.toString() &&
      state.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']) {
    logInfo("Skipping high priority track volume restore ${correlationId}; another source is active")
    runtimeState.remove('directStreamTrackVolumeRestore')
    return
  }
  Integer duration = parseSonosDurationSeconds(getCurrentTrackDuration())
  Long startedAt = active.startedAt instanceof Number ? (active.startedAt as Long) : now()
  if(duration == null || duration <= 0) {
    Integer attempts = (active.durationLookupAttempts ?: 0) as Integer
    if(attempts < 10) {
      active.durationLookupAttempts = attempts + 1
      runtimeState.directStreamTrackVolumeRestore = active
      runIn(2, 'scheduleDirectStreamTrackVolumeRestore', [overwrite: true,
          data: [correlationId: correlationId]])
    } else {
      logWarn("Could not determine the duration of high priority track ${correlationId}; volume will remain at its requested level")
      runtimeState.remove('directStreamTrackVolumeRestore')
    }
    return
  }
  Integer remainingSeconds = Math.max(1, duration - ((now() - startedAt).intdiv(1000L) as Integer) + 2)
  runIn(remainingSeconds, 'restoreDirectStreamTrackVolume', [overwrite: true,
      data: [correlationId: correlationId]])
}

void restoreDirectStreamTrackVolume(Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.directStreamTrackVolumeRestore instanceof Map
      ? (Map)runtimeState.directStreamTrackVolumeRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  String currentSource = device.currentValue('audioSource')?.toString()
  if(currentSource && currentSource != active.streamUri?.toString() &&
      state.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']) {
    logInfo("Skipping high priority track volume restore ${correlationId}; another source is active")
    runtimeState.remove('directStreamTrackVolumeRestore')
    return
  }
  restoreTemporaryAnnouncementVolume(correlationId, active.previousGroupVolume as Integer,
      active.announcementVolume as Integer)
  runtimeState.remove('directStreamTrackVolumeRestore')
}

/** Stream TTS, then restore the previous transport if this stream still owns playback. */
void componentPlayTextWithRestoreLocal(String text, BigDecimal volume = null, String voice = null) {
  if(!text) { logWarn('No text provided to play'); return }
  Map tts = textToSpeech(text, voice)
  String streamUrl = tts?.uri?.toString()
  if(!streamUrl) { logWarn('TTS did not return a stream URI'); return }
  Integer duration = coerceClipDuration(tts?.duration) ?: 30
  componentPlayStreamWithRestoreLocal(streamUrl, duration, volume)
}

void componentPlayStreamWithRestoreLocal(String streamUrl, Integer duration, BigDecimal volume = null) {
  if(!streamUrl) { logWarn('TTS did not return a stream URI'); return }
  Map snapshot = runtimeState.lastTransportPlaybackSnapshot instanceof Map
      ? new LinkedHashMap((Map)runtimeState.lastTransportPlaybackSnapshot) : null
  String correlationId = UUID.randomUUID().toString()
  Integer previousGroupVolume = getGroupVolumeState()
  if(previousGroupVolume == null) { previousGroupVolume = getPlayerVolume() }
  volume = resolveExplicitVolumeSentinel(volume)
  Integer announcementVolume = volume as Integer
  if(snapshot != null) {
    snapshot.correlationId = correlationId
    snapshot.announcementUri = streamUrl
    snapshot.previousGroupVolume = previousGroupVolume
    snapshot.announcementVolume = announcementVolume
    runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] = snapshot
    runtimeState.directStreamTtsRestore = [correlationId: correlationId, streamUri: streamUrl,
      duration: duration, previousGroupVolume: previousGroupVolume,
      announcementVolume: announcementVolume]
  } else {
    logWarn("No AVTransport snapshot is available to restore after direct stream TTS ${correlationId}")
    runtimeState.directStreamTtsRestore = [correlationId: correlationId, streamUri: streamUrl,
      duration: duration, previousGroupVolume: previousGroupVolume,
      announcementVolume: announcementVolume]
  }
  if(volume != null) { componentSetGroupLevelLocal(volume) }
  setAVTransportURIAndPlay(streamUrl)
  runIn(Math.max(duration + 2, 5), 'restoreDirectStreamTts', [overwrite: true,
    data: [correlationId: correlationId]])
}

void restoreDirectStreamTts(Map data = null) {
  String correlationId = data?.correlationId?.toString()
  Map active = runtimeState.directStreamTtsRestore instanceof Map
      ? (Map)runtimeState.directStreamTtsRestore : null
  if(!correlationId || active?.correlationId?.toString() != correlationId) { return }
  String currentSource = device.currentValue('audioSource')?.toString()
  if(currentSource && currentSource != active.streamUri?.toString()) {
    if(!(state.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING'])) {
      restoreTemporaryAnnouncementVolume(correlationId, active.previousGroupVolume as Integer,
          active.announcementVolume as Integer)
      runtimeState.remove('directStreamTtsRestore')
      restoreGroupAnnouncementPlayback(correlationId)
      return
    }
    logInfo("Skipping direct TTS playback restore ${correlationId}; another source took over")
    runtimeState.remove('directStreamTtsRestore')
    runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
    return
  }
  restoreTemporaryAnnouncementVolume(correlationId, active.previousGroupVolume as Integer,
      active.announcementVolume as Integer)
  runtimeState.remove('directStreamTtsRestore')
  restoreGroupAnnouncementPlayback(correlationId)
}

void removeAllTracksFromQueue(String callbackMethod = 'localControlCallback') {
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("removeAllTracksFromQueue: could not determine group coordinator UPnP host for ${device.displayName}; skipping")
    return
  }
  Map params = getSoapActionParams(ip, AVTransport, 'RemoveAllTracksFromQueue')
  postSonosSoapAsync(callbackMethod, params)
}

// Keep the latest intent separate from HTTP completion order. A GUID identifies
// one intent; equality, rather than GUID sorting, determines callback ownership.
String recordStreamTransportIntent(String command) {
  synchronized(getRuntimeState()) {
    String guid = UUID.randomUUID().toString()
    runtimeState.streamTransportIntentGuid = guid
    logDebug("Stream transport intent command=${command} guid=${guid}")
    return guid
  }
}

void setAVTransportURIAndPlay(String currentURI, String currentURIMetaData = null, Map callbackData = null) {
  logDebug("setAVTransportURIAndPlay request player=${getId()}, uri=${currentURI}, announcementCorrelation=${callbackData?.groupAnnouncementCorrelationId ?: 'none'}, replacingGroupAnnouncement=${runtimeState.groupAnnouncementStream?.correlationId ?: 'none'}")
  String guid = recordStreamTransportIntent('setAVTransportURIAndPlay')
  if(!(callbackData?.groupAnnouncementCorrelationId) && runtimeState.groupAnnouncementStream instanceof Map) {
    Map announcement = (Map)runtimeState.groupAnnouncementStream
    runtimeState.pendingGroupAnnouncementTakeover = [transportGuid: guid,
      correlationId: announcement.correlationId?.toString(), replacementUri: currentURI]
    logDebug("Staged group announcement takeover player=${getId()}, correlation=${announcement.correlationId}, transportGuid=${guid}, replacementUri=${currentURI}; waiting for Sonos SetAVTransportURI acceptance")
  }
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("setAVTransportURIAndPlay: could not determine group coordinator UPnP host for ${device.displayName}; skipping")
    clearPendingGroupAnnouncementTakeover(guid, 'COORDINATOR_HOST_UNAVAILABLE')
    String announcementGuid = callbackData?.groupAnnouncementCorrelationId?.toString()
    if(announcementGuid) { invalidateGroupAnnouncementStream('COORDINATOR_HOST_UNAVAILABLE') }
    return
  }
  Map controlValues = [CurrentURI: currentURI]
  if(currentURIMetaData) {controlValues.CurrentURIMetaData = currentURIMetaData}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  Map data = [streamTransportIntentGuid: guid]
  if(callbackData instanceof Map) { data.putAll(callbackData) }
  postSonosSoapAsync('setAVTransportURIAndPlayCallback', params, data)
}

void setAVTransportURIAndPlayCallback(AsyncResponse response, Map data = null) {
  localControlCallback(response, data)
  // Hubitat can run a command and an HTTP callback concurrently. Keep ownership
  // validation and the resulting play enqueue atomic with transport commands.
  synchronized(getRuntimeState()) {
    String guid = data?.streamTransportIntentGuid?.toString()
    String latestGuid = runtimeState.streamTransportIntentGuid?.toString()
    Boolean current = guid != null && guid == latestGuid
    logDebug("Stream load callback guid=${guid} latestGuid=${latestGuid} status=${response?.status} current=${current}")
    if(!current) {
      logDebug("Ignoring superseded stream load callback guid=${guid}")
      return
    }
    // Consume the intent even on failure, so duplicate callbacks cannot replay it.
    runtimeState.remove('streamTransportIntentGuid')
    String announcementGuid = data?.groupAnnouncementCorrelationId?.toString()
    Map announcement = runtimeState.groupAnnouncementStream instanceof Map
        ? (Map)runtimeState.groupAnnouncementStream : null
    if(announcementGuid && announcement?.correlationId?.toString() != announcementGuid) {
      logDebug("Ignoring stale group announcement stream callback ${announcementGuid}")
      return
    }
    if(response?.status == 200 && response.hasError() == false) {
      commitPendingGroupAnnouncementTakeover(guid)
      if(announcementGuid) { announcement.phase = 'STARTING' }
      play()
    } else if(announcementGuid) {
      announcement.phase = 'FAILED'
      publishGroupAnnouncementProgress(announcementGuid, 'FAILED')
      runtimeState.remove('groupAnnouncementStream')
    } else {
      clearPendingGroupAnnouncementTakeover(guid, "SET_AV_TRANSPORT_URI_REJECTED status=${response?.status}")
    }
  }
}

void commitPendingGroupAnnouncementTakeover(String transportGuid) {
  Map pending = runtimeState.pendingGroupAnnouncementTakeover instanceof Map
      ? (Map)runtimeState.pendingGroupAnnouncementTakeover : null
  if(pending == null || pending.transportGuid?.toString() != transportGuid) { return }
  runtimeState.remove('pendingGroupAnnouncementTakeover')
  Map announcement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  if(announcement?.correlationId?.toString() != pending.correlationId?.toString()) {
    logDebug("Ignoring transport takeover ${transportGuid}; announcement ownership changed before Sonos accepted the URI")
    return
  }
  String correlationId = pending.correlationId?.toString()
  logDebug("Sonos accepted replacement stream for group announcement ${correlationId}; notifying parent app")
  publishGroupAnnouncementProgress(correlationId, 'INTERRUPTED', [
    externalPlayback: true,
    replacementUri: pending.replacementUri?.toString(),
    playbackState: state.playbackState?.toString(),
    sourceUri: device.currentValue('audioSource')?.toString()
  ])
  runtimeState.remove('groupAnnouncementStream')
  runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
}

void clearPendingGroupAnnouncementTakeover(String transportGuid, String reason) {
  Map pending = runtimeState.pendingGroupAnnouncementTakeover instanceof Map
      ? (Map)runtimeState.pendingGroupAnnouncementTakeover : null
  if(pending?.transportGuid?.toString() == transportGuid) {
    logDebug("Clearing unaccepted group announcement takeover ${transportGuid}: ${reason}")
    runtimeState.remove('pendingGroupAnnouncementTakeover')
  }
}

void setAVTransportURI(String currentURI, String currentURIMetaData = null) {
  invalidateGroupAnnouncementStream('SUPERSEDED_BY_TRANSPORT_REQUEST')
  recordStreamTransportIntent('setAVTransportURI')
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("setAVTransportURI: could not determine group coordinator UPnP host for ${device.displayName}; skipping")
    return
  }
  Map controlValues = [CurrentURI: currentURI]
  if(currentURIMetaData) {controlValues.CurrentURIMetaData = currentURIMetaData}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

// =============================================================================
// Queue management
// =============================================================================
/** Append the current track to an existing Sonos saved playlist. */
void addCurrentlyPlayingToPlaylist(String playlistIdOrName) {
  String requested = playlistIdOrName?.trim()
  if(!requested) { logInfo('Cannot add the currently playing track to a Sonos Playlist: provide a playlist name or ID'); return }
  Map playlist = getPlaylistsMap()?.get(requested)
  if(!playlist) {
    List<Map> matches = (getPlaylistsMap()?.values() ?: []).findAll { Map item -> item.name?.toString()?.equalsIgnoreCase(requested) }
    if(matches.size() == 1) { playlist = matches[0] }
  }
  String objectId = playlist?.id?.toString() ?: requested
  if(objectId && objectId != 'null' && objectId.isNumber() && !objectId.startsWith('SQ:')) {
    objectId = "SQ:${objectId}"
  }
  if(!objectId || !objectId.startsWith('SQ:')) {
    logInfo("Cannot add the currently playing track to '${requested}': only an existing Sonos Playlist (saved queue) is editable; refresh Playlists and pass its name or SQ: ID")
    return
  }
  Map snapshot = runtimeState.lastTransportPlaybackSnapshot instanceof Map
      ? (Map)runtimeState.lastTransportPlaybackSnapshot : [:]
  Map track = getReusableCurrentTrack(snapshot)
  if(!track) { return }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn("addCurrentlyPlayingToPlaylist: coordinator UPnP host unavailable for ${device.displayName}"); return }
  String requestId = UUID.randomUUID().toString()
  state.playlistAddRequestId = requestId
  Map params = getSoapActionParams(host, ContentDirectory, 'Browse', [ObjectID: objectId,
    BrowseFlag: 'BrowseMetadata', Filter: '*', StartingIndex: 0, RequestedCount: 0, SortCriteria: ''])
  postSonosSoapAsync('playlistAddBrowseCallback', params, [requestId: requestId, host: host,
    objectId: objectId, playlistName: playlist?.name?.toString() ?: requested, track: track])
}

void playlistAddBrowseCallback(AsyncResponse response, Map data = null) {
  if(data?.requestId?.toString() != state.playlistAddRequestId?.toString()) { return }
  if(response?.status != 200 || response.hasError()) {
    state.remove('playlistAddRequestId')
    logWarn("Could not read Sonos Playlist '${data?.playlistName}' before adding the track (HTTP ${response?.status})")
    return
  }
  try {
    GPathResult envelope = new XmlSlurper().parseText(response.getData()?.toString() ?: '')
    String updateId = findQueueXmlText(envelope, 'UpdateID') ?: '0'
    Map track = (Map)data.track
    Map params = getSoapActionParams(data.host.toString(), AVTransport, 'AddURIToSavedQueue', [
      ObjectID: data.objectId.toString(), UpdateID: updateId, EnqueuedURI: escapeSoapXmlText(track.uri.toString()),
      EnqueuedURIMetaData: escapeSoapXmlText(track.metadata.toString()), AddAtIndex: '4294967295'])
    postSonosSoapAsync('playlistAddTrackCallback', params, [requestId: data.requestId,
      playlistName: data.playlistName])
  } catch(Exception e) {
    state.remove('playlistAddRequestId')
    logWarn("Could not prepare the track for Sonos Playlist '${data?.playlistName}': ${e.message}")
  }
}

void playlistAddTrackCallback(AsyncResponse response, Map data = null) {
  if(data?.requestId?.toString() != state.playlistAddRequestId?.toString()) { return }
  state.remove('playlistAddRequestId')
  if(response?.status == 200 && !response.hasError()) {
    logInfo("Added currently playing track to Sonos Playlist '${data?.playlistName}'")
    return
  }
  String xml = response?.getData()?.toString() ?: ''
  if(xml.contains('errorCode>800<') || xml.contains('errorCode>701<')) {
    logInfo("Could not add the currently playing track to '${data?.playlistName}': this playlist or audio source does not support adding tracks through Sonos")
  } else {
    logWarn("Adding currently playing track to Sonos Playlist '${data?.playlistName}' failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
  }
}

Map getReusableCurrentTrack(Map snapshot) {
  String currentUri = normalizeQueueTrackUri(snapshot?.trackUri?.toString())
  String metadata = snapshot?.trackMetaData?.toString()
  if(!currentUri || !metadata || currentUri.startsWith('x-rincon-stream:') || currentUri.startsWith('x-sonos-htastream:')) {
    logInfo("Cannot save the currently playing item for ${device.displayName}: the source is a radio station or live input, not a reusable track Sonos exposes to Favorites and Playlists")
    return null
  }
  try {
    GPathResult item = new XmlSlurper().parseText(unEscapeMetaData(metadata))
    String title = item.'**'.find { node -> node.name()?.toString()?.endsWith('title') }?.text()
    String resource = item.'**'.find { node -> node.name()?.toString()?.endsWith('res') }?.text()
    String protocolInfo = item.'**'.find { node -> node.name()?.toString()?.endsWith('res') }?.attributes()?.get('protocolInfo')?.toString()
    String uri = normalizeQueueTrackUri(resource)
    if(!title || !uri || uri.startsWith('x-sonosapi-stream:') || uri.startsWith('x-rincon-mp3radio:') || uri.startsWith('x-sonosapi-radio:')) {
      logInfo("Cannot save the currently playing item for ${device.displayName}: the music service did not provide track metadata that Sonos can add to a Favorite or Playlist")
      return null
    }
    return [uri: uri, metadata: unEscapeMetaData(metadata), title: title, creator: item.'**'.find { node -> node.name()?.toString()?.endsWith('creator') }?.text() ?: '',
      album: item.'**'.find { node -> node.name()?.toString()?.endsWith('album') }?.text() ?: '',
      art: item.'**'.find { node -> node.name()?.toString()?.endsWith('albumArtURI') }?.text() ?: '', protocolInfo: protocolInfo ?: '*:*:*:*']
  } catch(Exception e) {
    logInfo("Cannot save the currently playing item for ${device.displayName}: Sonos did not provide reusable track metadata (${e.message})")
    return null
  }
}

String escapeSoapXmlText(String value) { return value?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')?.replace('"', '&quot;')?.replace("'", '&apos;') ?: '' }
String escapeSoapXmlAttribute(String value) { return escapeSoapXmlText(value) }

void getQueue() {
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) {
    state.remove('queueBrowseRequestId')
    logWarn("getQueue: could not determine coordinator UPnP host for ${device.displayName}")
    setChildQueue('<p>Queue unavailable: player network address is missing.</p>')
    return
  }
  String requestId = UUID.randomUUID().toString()
  state.queueBrowseRequestId = requestId
  requestQueuePage(host, 0, [], requestId)
}

void removeCurrentlyPlayingItemFromQueue() {
  Map snapshot = runtimeState.lastTransportPlaybackSnapshot instanceof Map
      ? (Map)runtimeState.lastTransportPlaybackSnapshot : [:]
  String currentTrackUri = normalizeQueueTrackUri(snapshot.trackUri?.toString())
  String currentTrackPosition = snapshot.track?.toString()
  String transportState = snapshot.transportState?.toString()?.toUpperCase()
  if(!currentTrackUri || (transportState && !(transportState in ['PLAYING', 'PAUSED_PLAYBACK']))) {
    logInfo("Currently playing item is not in the Sonos queue for ${device.displayName}: no active track URI is available")
    return
  }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) {
    logWarn("removeCurrentlyPlayingItemFromQueue: coordinator UPnP host unavailable for ${device.displayName}")
    return
  }
  String requestId = UUID.randomUUID().toString()
  state.queueBrowseRequestId = requestId
  requestQueuePage(host, 0, [], requestId, 'remove-current', currentTrackUri, currentTrackPosition)
}

void requestQueuePage(String host, Integer startingIndex, List<Map> entries, String requestId, String action = 'display', String currentTrackUri = null, String currentTrackPosition = null) {
  Map params = getSoapActionParams(host, ContentDirectory, 'Browse', [
    ObjectID: 'Q:0', BrowseFlag: 'BrowseDirectChildren', Filter: '*',
    StartingIndex: startingIndex, RequestedCount: 100, SortCriteria: ''
  ])
  postSonosSoapAsync('queueBrowseCallback', params, [host: host, startingIndex: startingIndex,
    entries: entries, requestId: requestId, action: action, currentTrackUri: currentTrackUri,
    currentTrackPosition: currentTrackPosition])
}

void queueBrowseCallback(AsyncResponse response, Map data) {
  if(data?.requestId?.toString() != state.queueBrowseRequestId?.toString()) { return }
  if(response?.status != 200 || response.hasError()) {
    state.remove('queueBrowseRequestId')
    logWarn("Queue Browse failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    setChildQueue('<p>Could not retrieve the Sonos queue. Tap Refresh to try again.</p>')
    return
  }
  try {
    String soap = response.getData()?.toString()
    if(!soap) { throw new IllegalArgumentException('Browse response was empty') }
    GPathResult envelope = new XmlSlurper().parseText(soap)
    String resultXml = findQueueXmlText(envelope, 'Result')
    Integer returned = findQueueXmlText(envelope, 'NumberReturned')?.toInteger() ?: 0
    Integer total = findQueueXmlText(envelope, 'TotalMatches')?.toInteger() ?: 0
    String updateId = findQueueXmlText(envelope, 'UpdateID') ?: '0'
    List<Map> entries = (List<Map>)(data.entries ?: [])
    if(resultXml) {
      GPathResult didl = new XmlSlurper().parseText(resultXml)
      didl.children().each { item ->
        String objectId = item.getAt('@id')?.text()
        String title = findQueueXmlText(item, 'title')
        String artist = findQueueXmlText(item, 'creator')
        String album = findQueueXmlText(item, 'album')
        String albumArtURI = findQueueXmlText(item, 'albumArtURI')
        String trackUri = findQueueXmlText(item, 'res')
        if(albumArtURI?.startsWith('/')) { albumArtURI = "${getDeviceDataValue('localUpnpUrl')}${albumArtURI}" }
        entries.add([objectId: objectId, updateId: updateId, title: title ?: 'Unknown track', artist: artist ?: '', album: album ?: '', albumArtURI: albumArtURI ?: '', trackUri: normalizeQueueTrackUri(trackUri)])
      }
    }
    Integer nextIndex = (data.startingIndex as Integer) + returned
    if(returned > 0 && nextIndex < total && entries.size() < 2000) {
      requestQueuePage(data.host.toString(), nextIndex, entries, data.requestId.toString(), data.action?.toString() ?: 'display',
        data.currentTrackUri?.toString(), data.currentTrackPosition?.toString())
    } else {
      state.remove('queueBrowseRequestId')
      if(data.action == 'remove-current') {
        Integer currentTrackPosition = null
        try { currentTrackPosition = data.currentTrackPosition?.toString()?.toInteger() } catch(Exception ignored) { }
        Map currentEntry = null
        if(currentTrackPosition != null && currentTrackPosition > 0 && currentTrackPosition <= entries.size()) {
          Map positionedEntry = entries[currentTrackPosition - 1] as Map
          if(positionedEntry.trackUri && positionedEntry.trackUri == data.currentTrackUri) {
            currentEntry = positionedEntry
          } else {
            logInfo("Cannot remove currently playing queue item for ${device.displayName}: CurrentTrack position ${currentTrackPosition} does not match the current track URI")
            setChildQueue(renderQueueHtml(entries))
            return
          }
        } else if(currentTrackPosition != null && currentTrackPosition > 0) {
          logInfo("Cannot remove currently playing queue item for ${device.displayName}: CurrentTrack position ${currentTrackPosition} is outside the ${entries.size}-item queue")
          setChildQueue(renderQueueHtml(entries))
          return
        } else {
          List<Map> matchingEntries = entries.findAll { Map entry -> entry.trackUri && entry.trackUri == data.currentTrackUri }
          if(matchingEntries.size() == 1) {
            currentEntry = matchingEntries[0]
          } else if(matchingEntries.size() > 1) {
            logWarn("Cannot remove currently playing queue item for ${device.displayName}: URI matches ${matchingEntries.size()} queue entries and CurrentTrack position is unavailable")
            setChildQueue(renderQueueHtml(entries))
            return
          }
        }
        if(currentEntry) {
          logInfo("Removing currently playing queue item '${currentEntry.title}' (${currentEntry.objectId}) from ${device.displayName}")
          removeQueueItem(currentEntry.objectId?.toString(), currentEntry.updateId?.toString())
        } else {
          logInfo("Currently playing item is not in the Sonos queue for ${device.displayName}: ${snapshotTrackDescription(data.currentTrackUri?.toString())}")
          setChildQueue(renderQueueHtml(entries))
        }
      } else {
        setChildQueue(renderQueueHtml(entries))
      }
    }
  } catch(Exception e) {
    state.remove('queueBrowseRequestId')
    logWarn("Could not parse Sonos queue response: ${e.message}")
    setChildQueue('<p>Could not read the Sonos queue response. Tap Refresh to try again.</p>')
  }
}

String normalizeQueueTrackUri(String uri) {
  String normalized = uri?.trim()
  while(normalized?.contains('&amp;')) { normalized = normalized.replace('&amp;', '&') }
  return normalized
}

String snapshotTrackDescription(String trackUri) {
  String title = device.currentValue('currentTrackName')?.toString()
  return title ? "'${title}' (${trackUri ?: 'track URI unavailable'})" : (trackUri ?: 'track URI unavailable')
}

String queueXmlLocalName(Object nodeName) {
  String name = nodeName?.toString() ?: ''
  Integer separator = Math.max(name.lastIndexOf('}'), name.lastIndexOf(':'))
  return separator >= 0 ? name.substring(separator + 1) : name
}

String findQueueXmlText(GPathResult node, String targetName) {
  if(node == null) { return null }
  if(queueXmlLocalName(node.name()) == targetName) { return node.text() }
  for(GPathResult child : node.children()) {
    String value = findQueueXmlText(child, targetName)
    if(value != null) { return value }
  }
  return null
}

String escapeQueueHtml(String value) {
  return (value ?: '').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&#39;')
}

String renderQueueHtml(List<Map> entries) {
  Long queueDeviceId = getQueueChild()?.getIdAsLong() ?: device.getIdAsLong()
  StringBuilder html = new StringBuilder("<div class=\"sonos-queue\" style='max-width:400px;margin:0 auto;display:flex;flex-direction:column;gap:20px;'><div style='display:flex;gap:8px;'><button style='flex:1;' onclick=\"${buildHubitatRunMethodOnclick(queueDeviceId, 'getQueue', [])}\">Refresh</button><button style='flex:1;' onclick=\"${buildHubitatRunMethodOnclick(queueDeviceId, 'removeCurrentlyPlayingItemFromQueue', [])}\">Remove Currently Playing</button></div>")
  if(!entries) { return html.append('<p>The Sonos queue is empty.</p></div>').toString() }
  Integer position = 0
  entries.each { Map entry ->
    position++
    html.append("<div class='queue-row' style='background:white;border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,0.1);padding:15px;text-align:center;'>")
    String artistLabel = entry.artist ? "# ${position} — ${entry.artist}" : "# ${position}"
    html.append("<div class='queue-artist' style='font-size:14px;color:#666;margin-bottom:5px;font-weight:500;'>${escapeQueueHtml(artistLabel)}</div>")
    if(entry.albumArtURI) {
      html.append("<img class='queue-image' src='${escapeQueueHtml(entry.albumArtURI.toString())}' alt='Album art' style='width:100%;aspect-ratio:1;object-fit:cover;border-radius:8px;'/>")
    }
    html.append("<div class='queue-title' style='font-size:18px;color:#333;margin-top:10px;font-weight:600;line-height:1.3;'>${escapeQueueHtml(entry.title?.toString())}</div>")
    if(entry.album) { html.append("<div class='queue-album' style='font-size:13px;color:#666;margin-top:4px;'>${escapeQueueHtml(entry.album.toString())}</div>") }
    html.append("<div class='queue-actions' style='display:flex;justify-content:center;gap:8px;margin-top:12px;'><button style='flex:1;' onclick=\"${buildHubitatRunMethodOnclick(queueDeviceId, 'playQueuePosition', [position.toString()])}\">Play</button>")
    html.append("<button style='flex:1;' onclick=\"${buildHubitatRunMethodOnclick(queueDeviceId, 'removeQueueItem', [entry.objectId?.toString() ?: '', entry.updateId?.toString() ?: '0'])}\">Remove</button></div></div>")
  }
  html.append('</div>')
  return html.toString()
}

String buildHubitatRunMethodOnclick(Long deviceId, String method, List<String> values) {
  List<Map> args = (values ?: []).collect { String value -> [type: 'STRING', value: value ?: ''] }
  String payload = JsonOutput.toJson([id: deviceId, method: method, args: args])
  String handler = "(function(){fetch('http://${device.hub.localIP}/device/runmethod',{method:'POST',headers:{'Content-Type':'application/json'},body:${JsonOutput.toJson(payload)}})})()"
  return handler.replace('&', '&amp;').replace('"', '&quot;').replace('<', '&lt;').replace('>', '&gt;')
}

void setChildQueue(String html) {
  if(getCreateQueueChildDevice()) {
    ChildDeviceWrapper child = getQueueChild()
    if(child) { child.setQueue(html) }
  }
}

void playQueuePosition(String position) {
  Integer trackNumber = null
  try { trackNumber = position?.toInteger() } catch(Exception ignored) { }
  if(trackNumber == null || trackNumber < 1) { logWarn("Invalid queue position: ${position}"); return }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('playQueuePosition: coordinator UPnP host unavailable'); return }
  Map params = getSoapActionParams(host, AVTransport, 'Seek', [Unit: 'TRACK_NR', Target: trackNumber.toString()])
  postSonosSoapAsync('queueMutationCallback', params, [refresh: true])
}

void removeQueueItem(String objectId, String updateId) {
  if(!objectId || !objectId.startsWith('Q:0/')) { logWarn("Invalid queue item id: ${objectId}"); return }
  Integer queueUpdateId = null
  try { queueUpdateId = updateId?.toInteger() } catch(Exception ignored) { }
  if(queueUpdateId == null || queueUpdateId < 0) { logWarn("Invalid queue update ID: ${updateId}"); return }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('removeQueueItem: coordinator UPnP host unavailable'); return }
  Map params = getSoapActionParams(host, AVTransport, 'RemoveTrackFromQueue', [ObjectID: objectId, UpdateID: queueUpdateId])
  postSonosSoapAsync('queueMutationCallback', params, [refresh: true])
}

void queueMutationCallback(AsyncResponse response, Map data) {
  if(response?.status == 200 && !response.hasError()) { getQueue() }
  else { logWarn("Queue command failed (HTTP ${response?.status}): ${response?.getErrorMessage()}"); getQueue() }
}

void invalidateGroupAnnouncementStream(String reason, String replacementUri = null) {
  Map announcement = runtimeState.groupAnnouncementStream instanceof Map
      ? (Map)runtimeState.groupAnnouncementStream : null
  if(announcement == null) { return }
  String correlationId = announcement.correlationId?.toString()
  logDebug("Group announcement interruption candidate player=${getId()}, correlation=${correlationId}, phase=${announcement.phase}, activeObserved=${announcement.activeObserved}, reason=${reason}, replacementUri=${replacementUri}, playbackState=${state.playbackState}, audioSource=${device.currentValue('audioSource')}")
  logInfo("Interrupting group announcement stream ${correlationId} (${reason}); replacement=${replacementUri ?: 'unknown'}")
  if(correlationId && announcement.activeObserved == true) {
    publishGroupAnnouncementProgress(correlationId, 'INTERRUPTED', [
      externalPlayback: true,
      replacementUri: replacementUri,
      playbackState: state.playbackState?.toString(),
      sourceUri: device.currentValue('audioSource')?.toString()
    ])
  } else if(correlationId) {
    publishGroupAnnouncementProgress(correlationId, 'FAILED')
  }
  runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
  runtimeState.remove('groupAnnouncementStream')
}

void addURIToQueue(String enqueuedURI, String enqueuedURIMetaData = null) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("addURIToQueue: could not determine group coordinator UPnP host for ${device.displayName}; skipping")
    return
  }
  Map controlValues = [EnqueuedURI: enqueuedURI]
  if(enqueuedURIMetaData) {controlValues.EnqueuedURIMetaData = enqueuedURIMetaData}
  Map params = getSoapActionParams(ip, AVTransport, 'AddURIToQueue', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentLoadStreamUrlLocal(String streamUrl, BigDecimal volume = null, Boolean restoreVolumeAfterTrack = false) {
  logDebug("${device} play track ${streamUrl} (volume ${volume != null ? volume : 'not set'})")
  volume = resolveExplicitVolumeSentinel(volume)
  if(volume != null && restoreVolumeAfterTrack == true) {
    Integer previousGroupVolume = getGroupVolumeState()
    if(previousGroupVolume == null) { previousGroupVolume = getPlayerVolume() }
    String correlationId = UUID.randomUUID().toString()
    runtimeState.directStreamTrackVolumeRestore = [correlationId: correlationId, streamUri: streamUrl,
        startedAt: now(), previousGroupVolume: previousGroupVolume, announcementVolume: volume as Integer]
    componentSetGroupLevelLocal(volume)
    setAVTransportURIAndPlay(streamUrl)
    runIn(2, 'scheduleDirectStreamTrackVolumeRestore', [overwrite: true,
        data: [correlationId: correlationId]])
    return
  }
  if(volume != null) { componentSetGroupLevelLocal(volume) }
  setAVTransportURIAndPlay(streamUrl)
}

void componentSetStreamUrlLocal(String streamUrl, BigDecimal volume = null) {
  logDebug("${device} play track ${streamUrl} (volume ${volume != null ? volume : 'not set'})")
  volume = resolveExplicitVolumeSentinel(volume)
  if(volume != null) {componentSetGroupLevelLocal(volume)}
  setAVTransportURI(streamUrl)
}

void getDeviceStateAsync(String callbackMethod = 'localControlCallback', Map service, String action, Map data = null, Map controlValues = null) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, service, action, controlValues)
  postSonosSoapAsync(callbackMethod, params, data)
}

void componentSetPlayerLevelLocal(BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentSetTrebleLocal(BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredTreble: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetTreble', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentSetBassLocal(BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredBass: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetBass', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentSetBalanceLocal(BigDecimal level) {
  if(!getRightChannelRincon()) {
    logWarn("Can not set balance on non-stereo pair.")
    return
  }
  String ip = device.getDataValue('localUpnpHost')
  level *= 5
  if(level < 0) {
    level = level < -100 ? -100 : level
    Map controlValues = [DesiredVolume: 100 + level, Channel: "RF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    postSonosSoapAsync('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "LF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    postSonosSoapAsync('localControlCallback', params)
  } else if(level > 0) {
    level = level > 100 ? 100 : level
    Map controlValues = [DesiredVolume: 100 - level, Channel: "LF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    postSonosSoapAsync('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "RF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    postSonosSoapAsync('localControlCallback', params)
  } else {
    Map controlValues = [DesiredVolume: 100, Channel: "LF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    postSonosSoapAsync('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "RF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    postSonosSoapAsync('localControlCallback', params)
  }
}

void componentSetLoudnessLocal(Boolean desiredLoudness) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredLoudness: desiredLoudness]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetLoudness', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentMuteGroupLocal(Boolean desiredMute) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("Could not determine local endpoint for ${device.displayName}; skipping local group mute")
    return
  }
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupMute', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentSetGroupRelativeLevelLocal(Integer adjustment) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("Could not determine local endpoint for ${device.displayName}; skipping local group volume adjustment")
    return
  }
  Map controlValues = [Adjustment: adjustment]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetRelativeGroupVolume', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}

void componentSetGroupLevelLocal(BigDecimal level) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(ip == null || ip.isEmpty()) {
    logWarn("Could not determine local endpoint for ${device.displayName}; skipping local group volume set")
    return
  }
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupVolume', controlValues)
  postSonosSoapAsync('localControlCallback', params)
}
// =============================================================================
// Local Control Component Methods
// =============================================================================



// =============================================================================
// Websocket Incoming Data Processing
// =============================================================================
void processWebsocketMessage(String message) {
  if(message == null || message == '') {return}
  String dni
  ArrayList json
  LinkedHashMap deviceSettings
  Map eventType
  Map eventData
  try {
    dni = device.getDeviceNetworkId()
    logDebug("WebSocket JSON decoding started; dni=${dni ?: 'unknown'}, payloadLength=${message.length()}")
    groovy.json.JsonSlurper deviceSlurper = jsonSlurpers.computeIfAbsent(dni, { String ignored ->
      new groovy.json.JsonSlurper()
    } as java.util.function.Function<String, groovy.json.JsonSlurper>)
    json = (ArrayList)deviceSlurper.parseText(message)
    if(json.size() < 2) {
      logDebug("Ignoring WebSocket JSON envelope with ${json.size()} entries")
      return
    }
    deviceSettings = getDeviceSettings()
    eventType = (json as List)[0] as Map
    eventData = (json as List)[1] as Map
    if(eventType == null || eventData == null) {
      logDebug('Ignoring WebSocket JSON envelope with a null event type or event body')
      return
    }
    logDebug("WebSocket JSON envelope decoded; entryCount=${json.size()}, typeKeys=${eventType.keySet().join(',')}, dataKeys=${eventData.keySet().join(',')}")
  } catch(Exception e) {
    logDebug("WebSocket JSON decoding/envelope extraction failed; type=${e.class.name}, message=${e.message ?: '(no message)'}")
    throw e
  }
  logDebug("WebSocket message dispatched; type=${eventType.type ?: 'unset'}, namespace=${eventType.namespace ?: 'unset'}, name=${eventType.name ?: 'unset'}, response=${eventType.response ?: 'unset'}")
  runtimeState[WS_LAST_INBOUND_AT_STATE_KEY] = now()
  runtimeState[WS_INBOUND_SEQUENCE_STATE_KEY] = ((runtimeState[WS_INBOUND_SEQUENCE_STATE_KEY] ?: 0L) as Long) + 1L
  logDebug('WebSocket expected-subscription observation beginning')
  observeExpectedSubscriptionEvent(eventType)
  logDebug('WebSocket expected-subscription observation completed')

  if(loggingEnabled('trace')) {
    // Suppress consecutive duplicate WS event log messages to reduce logging load
    String eventSig = eventType.toString()
    String lastSig = lastWsEventLog.put(dni, eventSig)
    if(eventSig != lastSig) {
      logTrace("WS event: ${eventType}")
    }
  }
  if(deviceSettings.enableRawWebsocketLogging == true) {
    logPayloadAtTrace('WebSocket raw message received; payload omitted', "WS raw: ${message}")
  }

  //Process subscriptions
  if(eventType?.type == 'none' && eventType?.response == 'subscribe') {
    logDebug("WebSocket subscription response processing beginning; namespace=${eventType?.namespace}, success=${eventType?.success}")
    logDebug("Subscription to ${eventType?.namespace} ${eventType?.success ? 'was successful' : 'failed'}")
    if(eventType?.success == true && eventType?.namespace != null && eventType?.namespace != '') {
      setWsSubscriptionValue("${eventType?.namespace}", 'Subscribed')
    }
    if(eventType?.namespace == 'audioClip') {
      runtimeState.audioClipSubscriptionRequestPending = false
      if(eventType?.success == true) {
        runtimeState.audioClipSubscriptionRetryCount = 0
        flushWebsocketOutboundQueue()
        resumeAudioClipAfterSubscription()
      } else if(runtimeState.activeAudioClipCorrelationId && runtimeState.audioClipPlaying == true) {
        logWarn("Audio clip subscription failed while clip ${runtimeState.activeAudioClipCorrelationId} is pending; retrying subscription")
        runIn(5, 'retryAudioClipSubscription', [overwrite: true])
      }
    }
    logDebug('WebSocket subscription response processing completed')
  }

  logDebug('WebSocket command-response observation beginning')
  observeWebsocketCommandResponse(eventType, eventData)
  logDebug('WebSocket command-response observation completed')

  //Process groups
  if(eventType?.type == 'groups' && (eventType?.name == 'groups' || eventType?.response == 'getGroups')) {
    Integer groupCount = eventData?.groups instanceof Collection ? ((Collection)eventData.groups).size() : 0
    logDebug("WebSocket groups response processing beginning; groupCount=${groupCount}")
    publishHouseholdPlayerPresence(eventData)
    ArrayList<Map> groups = (ArrayList<Map>)eventData?.groups
    if(groups != null && groups.size() > 0) {
      Map group = groups.find{ ((ArrayList<String>)it?.playerIds)?.contains(getId()) }
      if(group == null) {
        if(shouldHoldTopologyUpdate([coordinatorId: null, memberIds: []])) {
          pendingTopologyWebsocketByDni.put(dni, message)
          pendingTopologyKindByDni.put(dni, 'ws')
          publishGroupTopologyUpdate(null, null, [])
          scheduleTopologyTimeoutFlush()
          logDebug('Holding groups websocket topology update until the affected player reaches its expected topology')
          return
        }
        logDebug("Player ${getId()} not found in any group")
        publishGroupTopologyUpdate(null, null, [])
        emitGroupFavoriteOperationEvent('groups', [groupId: null, coordinatorId: null, playerIds: []])
        return
      }

      // Extract only the fields we need from the group
      String groupId = group?.id?.toString()
      String groupName = group?.name?.toString()
      ArrayList<String> playerIds = (ArrayList<String>)group?.playerIds
      String coordinatorId = group?.coordinatorId?.toString()

      Map websocketTopology = [
        coordinatorId: coordinatorId,
        memberIds: (playerIds ?: []).toList().sort()
      ]
      if(shouldHoldTopologyUpdate(websocketTopology)) {
        pendingTopologyWebsocketByDni.put(dni, message)
        pendingTopologyKindByDni.put(dni, 'ws')
        // Publish this topology snapshot for the app's player-state
        // subscription. It does not modify playback attributes or publish
        // membership to group devices while topology is between the requested
        // start and end.
        publishGroupTopologyUpdate(groupId, coordinatorId, playerIds ?: [])
        scheduleTopologyTimeoutFlush()
        logDebug('Holding groups websocket topology update until the affected player reaches its expected topology')
        return
      }

      // Deduplicate: skip processing if nothing has changed
      String oldGroupId = getGroupId()
      String oldCoordinatorId = getGroupCoordinatorId()
      List<String> oldPlayerIds = getGroupPlayerIds()
      Boolean expectedCoordinatorRole = getId() == coordinatorId
      if(oldGroupId == groupId && oldCoordinatorId == coordinatorId && oldPlayerIds == playerIds &&
          getIsGroupCoordinator() == expectedCoordinatorRole) {
        logDebug('Groups websocket event received but group data unchanged, skipping processing')
        publishGroupTopologyUpdate(groupId, coordinatorId, playerIds ?: [])
        emitGroupFavoriteOperationEvent('groups', [
          groupId: groupId,
          coordinatorId: coordinatorId,
          playerIds: playerIds ?: []
        ])
        return
      }

      if(groupId != null && groupId != '') { setGroupId(groupId) }
      if(groupName != null && groupName != '') { setGroupName(groupName) }
      if(coordinatorId != null && coordinatorId != '') { setGroupCoordinatorId(coordinatorId) }
      if(playerIds != null && playerIds.size() > 0) { setGroupPlayerIds(playerIds) }

      // Build a lightweight player name lookup from the players array
      // Only extract id and name — ignore devices, capabilities, versions, etc.
      ArrayList<Map> players = (ArrayList<Map>)eventData?.players
      Map<String, String> playerNameMap = [:]
      if(players != null) {
        players.each { Map p -> playerNameMap[(String)p?.id] = p?.name?.toString() }
      }

      String coordinatorName = playerNameMap[coordinatorId]
      if(coordinatorName != null && coordinatorName != '') { setGroupCoordinatorName(coordinatorName) }

      try {
        List<String> groupMemberNames = playerIds.collect { String pid -> playerNameMap[pid] }.findAll { it != null }
        setGroupMemberNames(groupMemberNames)
      } catch (Exception e) { logDebug('Could not get group member names, continuing on...') }

      if(hasSecondaries() == true && players != null && players.size() > 0) {
        Map p = (Map)players.find{ it?.id == getId() }
        if(p != null && p.size() > 0) {
          Map zInfo = (Map)p?.zoneInfo
          if(zInfo != null && zInfo.size() > 0) {
            ArrayList<Map> members = (ArrayList<Map>)zInfo?.members
            if(members != null && members.size() > 0) {
              String companionId = selectStereoCompanionId(members, getId())
              setRightChannelRincon(companionId)
              if(getCreateRightChannelChildDevice()) { createRemoveRightChannelChildDevice(true) }
            }
          }
        }
      }
      publishGroupTopologyUpdate(groupId, coordinatorId, playerIds ?: [])
      emitGroupFavoriteOperationEvent('groups', [
        groupId: groupId,
        coordinatorId: coordinatorId,
        playerIds: playerIds ?: []
      ])
    } else {
      // A successful groups response can contain no groups while a player is
      // leaving or joining a topology. Publish that negative observation so a
      // parent operation cannot continue using stale cached group data.
      logDebug('Groups websocket response contained no group for player ' + getId())
      publishGroupTopologyUpdate(null, null, [])
      emitGroupFavoriteOperationEvent('groups', [groupId: null, coordinatorId: null, playerIds: []])
    }
    logDebug('WebSocket groups response processing completed')
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
    logDebug('WebSocket favorites-version change processing beginning')
    if(checkIsFavPlaylistDelegate()) {
      getFavorites()
      getPlaylists()
    }
  }

  if(eventType?.type == 'favoritesList' && eventType?.response == 'getFavorites' && eventType?.success == true) {
    Integer favoriteItemCount = eventData?.items instanceof Collection ? ((Collection)eventData.items).size() : 0
    logDebug("WebSocket favorites-list processing beginning; itemCount=${favoriteItemCount}")
    ArrayList<Map> respData = (ArrayList<Map>)eventData?.items
    if(respData == null || respData.isEmpty()) { clearFavoritesMap() }
    if(respData != null && respData.size() > 0) {
      Map<String, Map> formatted = (Map<String, Map>)respData.collectEntries() { [(String)it?.id, [name:it?.name, imageUrl:it?.imageUrl]] }

      // Get device ID for command URLs
      Long deviceId = device.getIdAsLong()
      String hubIp = device.hub.localIP

      // Build HTML with modern styling and click handlers
      String html = """<!DOCTYPE html>
<html>
<head>
  <style>
    body {
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
      background-color: #f5f5f5;
      margin: 0;
      padding: 20px;
    }
    .favorites-container {
      display: flex;
      flex-direction: column;
      gap: 20px;
      max-width: 400px;
      margin: 0 auto;
    }
    .favorite-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      padding: 15px;
      text-align: center;
      transition: transform 0.2s, box-shadow 0.2s;
      cursor: pointer;
    }
    .favorite-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 4px 16px rgba(0,0,0,0.15);
    }
    .favorite-image {
      width: 100%;
      aspect-ratio: 1;
      object-fit: cover;
      border-radius: 8px;
      transition: opacity 0.2s;
    }
    .favorite-image:hover {
      opacity: 0.85;
    }
    .favorite-image:active {
      opacity: 0.7;
    }
    .favorite-number {
      font-size: 12px;
      color: #666;
      margin-bottom: 5px;
      font-weight: 500;
    }
    .favorite-name {
      font-size: 14px;
      color: #333;
      margin-top: 10px;
      font-weight: 600;
      line-height: 1.3;
      min-height: 36px;
    }
    .no-image {
      width: 100%;
      height: 200px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 8px;
      color: white;
      font-size: 16px;
      cursor: pointer;
    }
    .no-image:hover {
      opacity: 0.9;
    }
  </style>
</head>
<body>
  <div class="favorites-container">
"""

      formatted.each(){fav ->
        String albumArtURI = fav?.value?.imageUrl
        String favId = fav?.key
        String favName = fav?.value?.name

        if(albumArtURI == null) {
          html += """    <div class="favorite-card" onclick="${buildHubitatRunMethodOnclick(deviceId, 'loadFavorite', [favId])}">
      <div class="favorite-number">Favorite #${favId}</div>
      <div class="no-image">
        <span>♪ ${favName}</span>
      </div>
"""
        } else if(albumArtURI.startsWith('/')) {
          html += """    <div class="favorite-card" onclick="${buildHubitatRunMethodOnclick(deviceId, 'loadFavorite', [favId])}">
      <div class="favorite-number">Favorite #${favId}</div>
      <img class="favorite-image"
           src="${getDeviceDataValue('localUpnpUrl')}${albumArtURI}"
           alt="${favName}" />
"""
        } else {
          html += """    <div class="favorite-card" onclick="${buildHubitatRunMethodOnclick(deviceId, 'loadFavorite', [favId])}">
      <div class="favorite-number">Favorite #${favId}</div>
      <img class="favorite-image"
           src="${albumArtURI}"
           alt="${favName}" />
"""
        }

        html += """      <div class="favorite-name">${favName}</div>
    </div>
"""
      }

      html += """  </div>
</body>
</html>"""

      setChildFavs(html)


      clearFavoritesMap()
      respData.each{
        Map item = (Map)it
        if(item != null && item.size() > 0) {
          Map resource = (Map)item?.resource
          if(resource != null && resource.size() > 0) {
            Map id = (Map)resource?.id
            if(id != null) {
              String objectId = id?.objectId
              String serviceId = id?.serviceId
              String accountId = id?.accountId
              if(objectId != null) {
                List tok = objectId?.tokenize(':')
                if(tok.size() >= 2) { objectId = tok[1] }
              }
              String universalMusicObjectId = "${objectId}${serviceId}${accountId}".toString()
              getFavoritesMap()[universalMusicObjectId] = [id:(String)item?.id, name:(String)item?.name, imageUrl:(String)item?.imageUrl, service:(String)((Map)item?.service)?.name, details:new LinkedHashMap(item)]
            }
          } else if(item?.imageUrl != null) {
            String universalMusicObjectId = ("${item?.imageUrl}".toString()).split('&v=')[0]
            getFavoritesMap()[universalMusicObjectId] = [id:(String)item?.id, name:(String)item?.name, imageUrl:(String)item?.imageUrl, service:(String)((Map)item?.service)?.name, details:new LinkedHashMap(item)]
          }
        }
      }
    }
    completePendingRandomMediaLoads('favorite')
  }
  if(eventType?.response == 'getFavorites' && eventType?.success != true) {
    failPendingRandomMediaLoads('favorite')
  }

  if(eventType?.type == 'playlistsList' && eventType?.response == 'getPlaylists' && eventType?.success == true) {
    Integer playlistItemCount = eventData?.items instanceof Collection ? ((Collection)eventData.items).size() : 0
    logDebug("WebSocket playlists-list processing beginning; itemCount=${playlistItemCount}")
    ArrayList<Map> respData = (ArrayList<Map>)eventData?.playlists
    if(respData == null || respData.isEmpty()) { clearPlaylistsMap() }
    if(respData != null && respData.size() > 0) {
      Map<String, Map> formatted = (Map<String, Map>)respData.collectEntries() { [(String)it?.id, [name:it?.name, imageUrl:it?.imageUrl]] }

      // Get device ID for command URLs
      Long deviceId = device.getIdAsLong()
      String hubIp = device.hub.localIP

      // Build HTML with modern styling and click handlers
      String html = """<!DOCTYPE html>
<html>
<head>
  <style>
    body {
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
      background-color: #f5f5f5;
      margin: 0;
      padding: 20px;
    }
    .playlists-container {
      display: flex;
      flex-direction: column;
      gap: 20px;
      max-width: 400px;
      margin: 0 auto;
    }
    .playlist-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      padding: 15px;
      text-align: center;
      transition: transform 0.2s, box-shadow 0.2s;
      cursor: pointer;
    }
    .playlist-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 4px 16px rgba(0,0,0,0.15);
    }
    .playlist-image {
      width: 100%;
      aspect-ratio: 1;
      object-fit: cover;
      border-radius: 8px;
      transition: opacity 0.2s;
    }
    .playlist-image:hover {
      opacity: 0.85;
    }
    .playlist-image:active {
      opacity: 0.7;
    }
    .playlist-number {
      font-size: 12px;
      color: #666;
      margin-bottom: 5px;
      font-weight: 500;
    }
    .playlist-name {
      font-size: 14px;
      color: #333;
      margin-top: 10px;
      font-weight: 600;
      line-height: 1.3;
      min-height: 36px;
    }
    .no-image {
      width: 100%;
      height: 200px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
      border-radius: 8px;
      color: white;
      font-size: 16px;
      cursor: pointer;
    }
    .no-image:hover {
      opacity: 0.9;
    }
  </style>
</head>
<body>
  <div class="playlists-container">
"""

      formatted.each(){playlist ->
        String albumArtURI = playlist?.value?.imageUrl
        String playlistId = playlist?.key
        String playlistName = playlist?.value?.name

        if(albumArtURI == null) {
          html += """    <div class="playlist-card" onclick="${buildHubitatRunMethodOnclick(deviceId, 'loadPlaylist', [playlistId])}">
      <div class="playlist-number">Playlist #${playlistId}</div>
      <div class="no-image">
        <span>♪ ${playlistName}</span>
      </div>
"""
        } else if(albumArtURI.startsWith('/')) {
          html += """    <div class="playlist-card" onclick="${buildHubitatRunMethodOnclick(deviceId, 'loadPlaylist', [playlistId])}">
      <div class="playlist-number">Playlist #${playlistId}</div>
      <img class="playlist-image"
           src="${getDeviceDataValue('localUpnpUrl')}${albumArtURI}"
           alt="${playlistName}" />
"""
        } else {
          html += """    <div class="playlist-card" onclick="${buildHubitatRunMethodOnclick(deviceId, 'loadPlaylist', [playlistId])}">
      <div class="playlist-number">Playlist #${playlistId}</div>
      <img class="playlist-image"
           src="${albumArtURI}"
           alt="${playlistName}" />
"""
        }

        html += """      <div class="playlist-name">${playlistName}</div>
    </div>
"""
      }

      html += """  </div>
</body>
</html>"""

      setChildPlaylists(html)


      clearPlaylistsMap()
      respData.each{
        Map item = (Map)it
        if(item != null && item.size() > 0) {
          String playlistId = (String)item?.id
          String playlistName = (String)item?.name
          if(playlistId != null) {
            getPlaylistsMap()[playlistId] = [id: playlistId, name: playlistName, service: 'Sonos', details:new LinkedHashMap(item)]
          }
        }
      }
    }
    completePendingRandomMediaLoads('playlist')
  }
  if(eventType?.response == 'getPlaylists' && eventType?.success != true) {
    failPendingRandomMediaLoads('playlist')
  }


  if(eventType?.type == 'audioClipStatus' && eventType?.name == 'audioClipStatus') {
    logDebug("WebSocket audio-clip status processing beginning; dataKeys=${eventData.keySet().join(',')}")
    String stage = 'read audioClips payload'
    String callbackId = null
    String activeClipId = null
    List<Map> audioClips = []
    Map<String, Map> clipProgress = null
    Boolean matchedCurrentClip = false
    String matchedCurrentStatus = null
    String callbackActiveClipId = null
    try {
      Object rawAudioClips = eventData?.audioClips
      if(rawAudioClips instanceof Collection) {
        ((Collection)rawAudioClips).each { Object item ->
          if(item instanceof Map) {
            audioClips.add(new LinkedHashMap((Map)item))
          } else {
            logDebug("Audio clip callback ${callbackId ?: 'pending'}: skipping non-map audioClips entry")
          }
        }
      } else if(rawAudioClips != null) {
        logDebug('Audio clip callback payload has an unexpected audioClips value; treating as empty')
      }
      callbackId = UUID.randomUUID().toString()
      activeClipId = runtimeState.activeAudioClipCorrelationId as String ?: 'none'
      callbackActiveClipId = runtimeState.activeAudioClipCorrelationId as String
      List<String> statuses = audioClips?.collect { Map clip ->
        "${clip?.status ?: 'unknown'}:sonosId=${clip?.id ?: 'unknown'}:name=${clip?.name ?: 'unknown'}"
      } ?: []
      logDebug("Audio clip status callback ${callbackId} receivedAt=${now()}, activeClip=${activeClipId}, statuses=${statuses}")
      logDebug("Audio clip callback ${callbackId}: payload decoded; count=${audioClips?.size() ?: 0}, activeCorrelation=${callbackActiveClipId ?: 'none'}")

      stage = 'copy correlation progress state'
      clipProgress = new LinkedHashMap<String, Map>()
      Object rawClipProgress = runtimeState.audioClipProgressByCorrelationId
      if(rawClipProgress instanceof Map) {
        ((Map)rawClipProgress).each { Object rawGuid, Object rawProgress ->
          String guid = rawGuid?.toString()
          if(guid && rawProgress instanceof Map) {
            clipProgress[guid] = new LinkedHashMap((Map)rawProgress)
          } else {
            logDebug("Audio clip callback ${callbackId}: discarding malformed saved progress entry; guid=${guid ?: 'missing'}")
          }
        }
      }

      stage = 'correlate and update clip status entries'
      audioClips?.each { Map clip ->
        String clipGuid = clip?.name?.toString()
        if(!clipGuid) {
          logDebug("Audio clip callback ${callbackId}: skipping status without a clip GUID")
          return
        }
        logDebug("Audio clip callback ${callbackId}: processing guid=${clipGuid}, status=${clip?.status ?: 'unknown'}")
        String clipStatus = clip?.status?.toString()
        if(!clipStatus) {
          logDebug("Audio clip callback ${callbackId}: skipping status for guid=${clipGuid} because status is missing")
          return
        }
        Map progress = clipProgress[clipGuid]
        if(progress == null && clipGuid == callbackActiveClipId) {
          progress = [correlationId: clipGuid, origin: 'audio-clip', phase: 'DISPATCHED',
            dispatchedAt: runtimeState.audioClipQueueStartTime ?: now()]
        }
        if(progress == null) {
          logDebug("Audio clip callback ${callbackId}: no tracked progress entry for guid=${clipGuid}; skipping")
          return
        }
        Long observedAt = now()
        progress.lastStatus = clipStatus
        progress.lastCallbackId = callbackId
        progress.lastObservedAt = observedAt
        if(clip?.id != null) { progress.sonosClipId = clip.id.toString() }
        if(clipStatus == 'ACTIVE') {
          if(progress.activeAt == null) { progress.activeAt = observedAt }
          progress.phase = 'ACTIVE'
        } else if(clipStatus == 'DONE') {
          progress.phase = 'DONE'
          progress.completedAt = observedAt
        } else if(clipStatus in ['ERROR', 'DISMISSED', 'INTERRUPTED']) {
          progress.phase = clipStatus
          progress.completedAt = observedAt
        }
        clipProgress[clipGuid] = progress

        stage = "publish progress for clip ${clipGuid}"
        publishAudioClipProgressEvent(progress, callbackId)
        String groupAnnouncementId = progress.groupAnnouncementCorrelationId?.toString() ?: clipGuid
        if(progress.groupAnnouncementCorrelationId || clipGuid == callbackActiveClipId) {
          publishGroupAnnouncementProgress(groupAnnouncementId, clipStatus in ['DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']
              ? (clipStatus == 'ERROR' ? 'FAILED' : clipStatus) : clipStatus)
        }
        logDebug("Audio clip GUID ${clipGuid} received Sonos status ${clipStatus ?: 'unknown'} callback=${callbackId} sonosId=${clip?.id ?: 'unknown'} observedAt=${observedAt}")

        stage = "record active clip match for ${clipGuid}"
        // Only the currently playing GUID may advance or finish the queue. A late
        // DONE for an older clip is recorded, but cannot dequeue over a newer clip.
        if(clipGuid == callbackActiveClipId) {
          matchedCurrentClip = true
          matchedCurrentStatus = clipStatus
          if(clip?.id != null) { runtimeState.activeAudioClipSonosId = clip.id.toString() }
          if(clipStatus == 'ERROR') { logWarn("Audio clip ${clipGuid} returned ERROR status: ${clip}") }
        }
        stage = 'correlate and update clip status entries'
      }

      stage = 'trim old correlation progress entries'
      while(clipProgress.size() > 50) {
        String oldestGuid = clipProgress.find { String guid, Map item ->
          guid != (runtimeState.activeAudioClipCorrelationId as String) && item?.phase != 'DISPATCHED'
        }?.key as String
        if(!oldestGuid) { break }
        clipProgress.remove(oldestGuid)
      }

      stage = 'persist correlation progress state'
      setHiddenJsonRuntimeState('audioClipProgressByCorrelationId', clipProgress)

      stage = 'apply ACTIVE status transition'
      if(matchedCurrentStatus == 'ACTIVE') {
        setAudioClipPlaying(true)
        runtimeState.audioClipHadDisconnectAfterSend = false
        Map activeProgress = clipProgress[callbackActiveClipId]
        Integer activeDuration = coerceClipDuration(activeProgress?.duration)
        if(activeDuration != null && activeDuration > 0) {
          stage = 'schedule ACTIVE clip completion watchdog'
          scheduleAudioClipDoneWatchdog(callbackActiveClipId, (activeProgress?.activeAt ?: now()) as Long, activeDuration)
        } else {
          stage = 'pause watchdog for ACTIVE clip without duration'
          pauseAudioClipWatchdog()
        }
      } else if(matchedCurrentStatus in ['DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']) {
        stage = "advance queue after terminal status ${matchedCurrentStatus}"
        setAudioClipPlaying(false)
      }
      if(!matchedCurrentClip) {
        logDebug("Audio clip callback ${callbackId}: no status matched active clip GUID ${activeClipId}; queue state unchanged")
      }
      stage = 'log audio clip callback completion'
      logDebug("Audio clip callback ${callbackId}: processing completed; matched=${matchedCurrentClip}, matchedStatus=${matchedCurrentStatus ?: 'none'}")
    } catch(Exception e) {
      logException("Audio clip status callback ${callbackId ?: 'unassigned'} failed at stage '${stage}' (active=${callbackActiveClipId ?: activeClipId ?: 'none'})", e)
      return
    }
  }

  if(eventType?.type == 'globalError' && eventType?.success == false) {
    logDebug("WebSocket global-error processing beginning; namespace=${eventType?.namespace}, response=${eventType?.response}, errorCode=${eventData?.errorCode}")
    if(eventType?.namespace == 'audioClip') {
      logWarn("Audio clip command '${eventType?.response}' failed for active clip ${runtimeState.activeAudioClipCorrelationId ?: 'unknown'}: ${eventData}")
      String failedCorrelationId = runtimeState.activeAudioClipCorrelationId as String
      if(failedCorrelationId) {
        retryAudioClipAfterCommandFailure(failedCorrelationId,
          "Sonos rejected audioClip command: ${eventData?.errorCode ?: eventData?.reason ?: 'unknown error'}")
      }
    }
    if(eventType?.namespace == 'playback') {
      String errorReason = "${eventData?.errorCode ?: 'UNKNOWN'}: ${eventData?.reason ?: eventData}"
      failTransportCommand(eventType?.response?.toString(), errorReason)
      if(eventType?.response == 'stop' && eventData?.errorCode == 'ERROR_UNSUPPORTED_COMMAND') {
        logDebug('Stop command unavailable for current target; pause fallback queued and tracked independently')
      }
    }
    if(eventType?.namespace == 'groups' && (eventType?.response == 'createGroup' || eventType?.response == 'modifyGroupMembers')) {
      String reason = eventData?.reason as String
      if(eventData?.errorCode == 'ERROR_PLAYBACK_FAILED' && reason != null && reason.contains('musicContextGroupId')) {
        logWarn("Cannot group/join while playing from this music service (likely Amazon Music). Use 'Join Players to Coordinator' or stop playback first. Reason: ${reason}")
        recordLastError('Cannot group: incompatible music service', reason)
      } else {
        logError("Group operation failed - Error: ${eventData?.errorCode}, Reason: ${reason}")
        recordLastError("Group operation failed: ${eventData?.errorCode}", reason)
      }
    }
    if(eventType?.namespace == 'playlists' && eventType?.response == 'loadPlaylist') {
      String errorCode = eventData?.errorCode?.toString()
      String reason = eventData?.reason?.toString()
      Map retryState = playlistRetryState.get(dni)
      if(retryState != null) {
        retryState.loadAcknowledged = false
        retryState.loadErrorCode = errorCode
        retryState.loadErrorReason = reason
      }
      if(retryState == null || !isRetryablePlaylistError(errorCode)) {
        reportPlaylistLoadError(retryState?.playlistId as String, errorCode, reason)
        if(retryState != null) { clearPlaylistRetryState() }
      } else {
        logWarn("Playlist '${retryState.playlistId}' load returned retryable error ${errorCode}: ${reason}")
      }
    }
  }

  if(eventType?.namespace == 'favorites' && eventType?.response == 'loadFavorite') {
    Map retryState = favoriteRetryState.get(dni)
    if(retryState != null) { retryState.loadAcknowledged = eventType?.success == true }
    emitGroupFavoriteOperationEvent('favoriteLoadAck', [
      success: eventType?.success == true,
      errorCode: eventData?.errorCode?.toString(),
      reason: eventData?.reason?.toString(),
      groupId: getGroupId()
    ])
  }

  if(eventType?.namespace == 'playlists' && eventType?.response == 'loadPlaylist') {
    Map retryState = playlistRetryState.get(dni)
    if(retryState != null) { retryState.loadAcknowledged = eventType?.success == true }
  }

  if(eventType?.type == 'playbackStatus' && eventType?.namespace == 'playback') {
    logDebug("WebSocket playback-status processing beginning; state=${eventData?.playbackState ?: 'unset'}")
    Map playModes = eventData?.playModes instanceof Map ? (Map)eventData.playModes : null
    if(playModes != null) {
      if(playModes.repeat instanceof Boolean && playModes.repeatOne instanceof Boolean && playModes.shuffle instanceof Boolean) {
        Boolean repeat = playModes.repeat as Boolean
        Boolean repeatOne = playModes.repeatOne as Boolean
        Boolean shuffle = playModes.shuffle as Boolean
        String playMode = shuffle ? (repeatOne ? 'SHUFFLE_REPEAT_ONE' : (repeat ? 'SHUFFLE' : 'SHUFFLE_NOREPEAT')) :
            (repeatOne ? 'REPEAT_ONE' : (repeat ? 'REPEAT_ALL' : 'NORMAL'))
        setPlayMode(playMode)
      }
      if(playModes.crossfade instanceof Boolean) {
        setCrossfadeMode((playModes.crossfade as Boolean) ? 'on' : 'off')
      }
    }
    String currentState = eventData?.playbackState?.toString()
    Long playbackStateObservedAt = now()
    state.playbackState = currentState
    state.playbackStateObservedAt = playbackStateObservedAt
    publishPlaybackObservation()
    observeTransportPlaybackStatus(eventData)
    setTrackPositionFromPlaybackStatus(eventData, currentState, playbackStateObservedAt)
    Map announcement = runtimeState.groupAnnouncementStream instanceof Map
        ? (Map)runtimeState.groupAnnouncementStream : null
    if(announcement != null) {
      String announcementGuid = announcement.correlationId?.toString()
      logDebug("Group announcement playback observation player=${getId()}, correlation=${announcementGuid}, playbackState=${currentState}, itemId=${eventData?.itemId}, source=${device.currentValue('audioSource')}, activeObserved=${announcement.activeObserved}")
      if(currentState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']) {
        announcement.activeObserved = true
        announcement.phase = 'ACTIVE'
        publishGroupAnnouncementProgress(announcementGuid, 'ACTIVE')
      } else if(currentState in ['PLAYBACK_STATE_IDLE', 'PLAYBACK_STATE_PAUSED'] && announcement.activeObserved == true) {
        // A user stop/pause does not necessarily transition a Sonos stream to
        // IDLE. Treat either terminal transport state as completion so the app
        // can restore temporary topology without waiting for its watchdog.
        announcement.phase = 'DONE'
        publishGroupAnnouncementProgress(announcementGuid, 'DONE', [playbackState: currentState,
          termination: currentState == 'PLAYBACK_STATE_PAUSED' ? 'paused' : 'idle'])
        runtimeState.remove('groupAnnouncementStream')
      }
    }
    String previousState = lastPlaybackState.put(dni, currentState)
    Map favoriteState = favoriteRetryState.get(dni)
    Map playlistState = playlistRetryState.get(dni)
    if(currentState == 'PLAYBACK_STATE_PLAYING') {
      if(favoriteState != null) { favoriteState.playbackObserved = true }
      if(playlistState != null) { playlistState.playbackObserved = true }
    } else if(currentState == 'PLAYBACK_STATE_BUFFERING') {
      if(playlistState != null) { playlistState.bufferingObserved = true }
    }
    emitGroupFavoriteOperationEvent('playbackStatus', [
      playbackState: currentState,
      groupId: getGroupId(),
      coordinatorId: getGroupCoordinatorId()
    ])
    // Dedup: skip repeated same-state events (common as periodic heartbeats).
    // Retry observation is updated before this return so an explicit playback
    // poll can still confirm an unchanged PLAYING state.
    if(currentState == previousState) { return }
    if(currentState == 'PLAYBACK_STATE_PLAYING') {
      if(getIsGroupCoordinator()) { getPlaybackMetadataStatusIn() }
    } else if(currentState in ['PLAYBACK_STATE_IDLE', 'PLAYBACK_STATE_PAUSED']) {
      lastMetadataContainerId.remove(dni)
    }
  }

  if(eventType?.type == 'metadataStatus' && eventType?.namespace == 'playbackMetadata') {
    logDebug("WebSocket metadata-status processing beginning; dataKeys=${eventData.keySet().join(',')}")
    observeAudioSourceSignalStatus(eventData)
    String containerKey = extractContainerKey(eventData)
    String lastKey = lastMetadataContainerId.get(dni)
    Boolean retryPending = favoriteRetryState.containsKey(dni) || playlistRetryState.containsKey(dni)
    Boolean groupFavoritePending = runtimeState.groupFavoriteOperationId as String ||
        runtimeState.groupTopologyOperationId as String
    if(containerKey == lastKey && !retryPending && !groupFavoritePending) { return }
    lastMetadataContainerId.put(dni, containerKey)
    checkFavAndPlaylist(eventData)
    emitGroupFavoriteOperationEvent('metadataStatus', [
      container: eventData?.container,
      groupId: getGroupId()
    ])
  }

  //Process playerVolume events
  if(eventType?.type == 'playerVolume' && eventType?.namespace == 'playerVolume') {
    logDebug("WebSocket player-volume processing beginning; hasVolume=${eventData?.volume != null}, hasMute=${eventData?.muted != null}")
    if(eventData?.volume != null) {
      Integer vol = eventData.volume as Integer
      setPlayerVolume(vol)
    }
    if(eventData?.muted != null) {
      String muteState = eventData.muted == true ? 'muted' : 'unmuted'
      setMuteState(muteState)
    }
  }

  //Process groupVolume events
  if(eventType?.type == 'groupVolume' && eventType?.namespace == 'groupVolume') {
    logDebug("WebSocket group-volume processing beginning; hasVolume=${eventData?.volume != null}, hasMute=${eventData?.muted != null}")
    if(eventData?.volume != null) {
      Integer groupVol = eventData.volume as Integer
      setGroupVolumeState(groupVol)
      queueGroupDeviceUpdate([_groupVolume: groupVol])
    }
    if(eventData?.muted != null) {
      String groupMuteState = eventData.muted == true ? 'muted' : 'unmuted'
      setGroupMuteState(groupMuteState)
      queueGroupDeviceUpdate([_groupMute: groupMuteState])
    }
  }
  logDebug('WebSocket message dispatch completed all matching handlers')
}

void observeAudioSourceSignalStatus(Map eventData) {
  Map container = eventData?.container instanceof Map ? (Map)eventData.container : [:]
  String containerType = container.type?.toString()
  if(!containerType) {
    logDebug("Ignoring metadata status without a container for ${getId()}; retaining last source signal observation")
    return
  }
  state.audioSourceMetadataType = containerType
  state.audioSourceMetadataObservedAt = now()
  Map inputFormat = container.htInputFormat instanceof Map ? (Map)container.htInputFormat : [:]
  String streamDescription = inputFormat.streamDescription?.toString()?.trim()
  String signalStatus = 'unknown'
  if(containerType?.startsWith('linein.homeTheater')) {
    signalStatus = streamDescription?.equalsIgnoreCase('No Signal') ? 'no-signal' :
        (streamDescription ? 'signal' : 'unknown')
  } else {
    signalStatus = 'not-hdmi'
  }
  Long observedAt = now()
  state.audioSourceSignalStatus = signalStatus
  state.audioSourceSignalObservedAt = observedAt
  publishPlaybackObservation()
  logDebug("Home theater input observation player=${getId()}, containerType=${containerType}, streamDescription=${streamDescription}, signalStatus=${signalStatus}")
}

void publishPlaybackObservation() {
  publishPlayerStateUpdate([kind: 'playbackObservation',
    playbackState: state.playbackState?.toString(),
    playbackObservedAt: state.playbackStateObservedAt,
    audioSource: device.currentValue('audioSource')?.toString(),
    signalStatus: state.audioSourceSignalStatus?.toString(),
    signalObservedAt: state.audioSourceSignalObservedAt])
}

@CompileStatic
void publishHouseholdPlayerPresence(Map eventData) {
  ArrayList<Map> players = eventData?.players instanceof ArrayList ? (ArrayList<Map>)eventData.players : null
  if(players == null || players.isEmpty()) { return }
  List<Map<String, String>> presence = []
  players.each { Map player ->
    String id = player?.id?.toString()
    String name = player?.name?.toString()
    if(id && name) { presence.add([id: id, name: name]) }
  }
  if(!presence.isEmpty()) { publishPlayerStateUpdate([kind: 'householdPresence', players: presence]) }
}

void getPlaybackMetadataStatusIn(Integer time = 2) {
  runIn(time, 'getPlaybackMetadataStatus', [overwrite: true])
}

@CompileStatic
static String extractContainerKey(Map eventData) {
  if(!eventData) { return '' }
  Map container = (Map)eventData.container
  if(!container) { return '' }
  Map id = (Map)container.id
  if(!id) { return '' }
  return "${id.objectId}:${id.serviceId}:${id.accountId}".toString()
}

void checkFavAndPlaylist(Map data) {
  logDebug('Checking currently playing favorite and playlist...')
  Boolean mapsPopulated = true
  if(favoritesMap == null || favoritesMap.size() < 1) {
    logDebug('Favorites map is empty, requesting favorites...')
    getFavorites()
    mapsPopulated = false
  }
  if(playlistsMap == null || playlistsMap.size() < 1) {
    logDebug('Playlists map is empty, requesting playlists...')
    getPlaylists()
    mapsPopulated = false
  }
  if(mapsPopulated) {
    // Maps are already populated — check immediately
    isFavoritePlaying(data)
    isPlaylistPlaying(data)
  } else {
    // Cold start: wait for async map population
    runIn(12, 'isFavoritePlaying', [overwrite: true, data: data])
    runIn(12, 'isPlaylistPlaying', [overwrite: true, data: data])
  }
}

void setChildFavs(String html) {
  if(getCreateFavoritesChildDevice()) {
    ChildDeviceWrapper favDev = getFavoritesChild()
    if(favDev) { favDev.setFavorites(html) }
  }
}

void setChildPlaylists(String html) {
  if(getCreatePlaylistChildDevice()) {
    ChildDeviceWrapper playlistDev = getPlaylistChild()
    if(playlistDev) { playlistDev.setPlaylists(html) }
  }
}

void setAudioClipPlaying(Boolean s) {
  runtimeState.audioClipPlaying = s
  if(s == false) {
    logDebug("Audio clip completion/reset for ${runtimeState.activeAudioClipCorrelationId ?: 'unknown'} at ${now()}")
    pauseAudioClipWatchdog()
    // The regular queue and saved replay queue own any clip that still needs
    // to be played. Drop the runtime payload and completed progress history so
    // large Sonos command JSON does not remain in the hub's State Variables.
    removeHiddenJsonRuntimeState('currentlyPlayingClip')
    runtimeState.remove('activeAudioClipCorrelationId')
    runtimeState.remove('activeAudioClipSonosId')
    removeHiddenJsonRuntimeState('activeAudioClipMessage')
    runtimeState.remove('audioClipSentAt')
    removeHiddenJsonRuntimeState('audioClipProgressByCorrelationId')
    runtimeState.remove('audioClipCommandSent')
    runtimeState.remove('audioClipHadDisconnectAfterSend')
    dequeueAudioClip()
  }
}

@CompileStatic
void addAudioClipCorrelation(Map clipMessage) {
  if(clipMessage == null) { return }
  if(!clipMessage.get('correlationId')) { clipMessage.put('correlationId', UUID.randomUUID().toString()) }
  if(!clipMessage.get('enqueuedAt')) { clipMessage.put('enqueuedAt', new Date().getTime()) }
  if(!clipMessage.get('origin')) { clipMessage.put('origin', 'audio-clip') }
}

void publishAudioClipProgressEvent(Map progress, String callbackId) {
  if(!(progress instanceof Map) || !progress.correlationId) { return }
  logDebug("Audio clip ${progress.correlationId} progress: phase=${progress.phase}, status=${progress.lastStatus}, callback=${callbackId ?: progress.lastCallbackId ?: 'n/a'}")
}

void publishGroupAnnouncementProgress(String correlationId, String phase, Map details = null) {
  if(!correlationId || !phase) { return }
  Map payload = [kind: 'groupAnnouncementProgress', correlationId: correlationId,
    phase: phase, playerId: getId()]
  if(details instanceof Map) { payload.putAll(details) }
  publishPlayerStateUpdate(payload)
}

void clearAudioClipPlaybackProgressCurrentState() {
  try {
    device.deleteCurrentState('audioClipPlaybackProgress')
  } catch(Exception e) {
    logDebug("Could not remove retired audio clip progress state: ${e.message}")
  }
}

void cancelAudioClipWatchdog() {
  pauseAudioClipWatchdog()
  runtimeState.audioClipPlaying = false
  runtimeState.audioClipCommandSent = false
}

void pauseAudioClipWatchdog() {
  unschedule('audioClipWatchdog')
  runtimeState.remove('audioClipWatchdogRequestId')
  runtimeState.remove('audioClipQueueStartTime')
  runtimeState.remove('audioClipQueueTotalDuration')
}

// textToSpeech() returns duration as a String on some TTS providers, so a raw
// `duration > 0` on the Map value invokes String.compareTo(Integer) and throws
// "Integer cannot be cast to String". Always coerce via coerceClipDuration() first.
private Integer coerceClipDuration(Object raw) {
  if(raw == null) { return null }
  if(raw instanceof Number) { return ((Number) raw).intValue() }
  try { return new BigDecimal(raw.toString().trim()).intValue() }
  catch(NumberFormatException ignored) { return null }
  catch(ArithmeticException ignored) { return null }
}

private Integer parseSonosDurationSeconds(String rawDuration) {
  if(!rawDuration || !(rawDuration ==~ /\d{1,}:\d{2}:\d{2}/)) { return null }
  try {
    List<String> parts = rawDuration.split(':') as List<String>
    return (parts[0].toInteger() * 3600) + (parts[1].toInteger() * 60) + parts[2].toInteger()
  } catch(Exception ignored) { return null }
}

void audioClipCommandSent(String correlationId) {
  if(!correlationId || correlationId != (runtimeState.activeAudioClipCorrelationId as String)) { return }
  if(runtimeState.audioClipCommandSent == true) { return }
  Long sentAt = now()
  runtimeState.audioClipCommandSent = true
  runtimeState.audioClipSentAt = sentAt
  runtimeState.audioClipQueueStartTime = sentAt
  Map<String, Map> clipProgress = runtimeState.audioClipProgressByCorrelationId instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)runtimeState.audioClipProgressByCorrelationId)
      : new LinkedHashMap<String, Map>()
  Map progress = clipProgress[correlationId]
  if(progress == null) { progress = [correlationId: correlationId] }
  progress.phase = 'WAITING_FOR_ACTIVE'
  progress.dispatchedAt = sentAt
  progress.sentAt = sentAt
  clipProgress[correlationId] = progress
  setHiddenJsonRuntimeState('audioClipProgressByCorrelationId', clipProgress)
  publishAudioClipProgressEvent(progress, null)
  logInfo("Sent audioClip.loadAudioClip to Sonos player ${getId()} over WebSocket, correlation=${correlationId}, at ${sentAt}; waiting ${AUDIO_CLIP_ACTIVE_TIMEOUT_SECONDS}s for matching ACTIVE status")
  if(runtimeState.audioClipHadDisconnectAfterSend != true) {
    scheduleAudioClipWatchdog(correlationId, 'WAITING_FOR_ACTIVE', sentAt + (AUDIO_CLIP_ACTIVE_TIMEOUT_SECONDS * 1000L))
  }
}

void scheduleAudioClipDoneWatchdog(String correlationId, Long startAt, Integer durationSeconds) {
  if(!correlationId || durationSeconds == null || durationSeconds <= 0) { return }
  Long deadline = (startAt ?: now()) + ((durationSeconds + AUDIO_CLIP_DONE_GRACE_SECONDS) * 1000L)
  scheduleAudioClipWatchdog(correlationId, 'WAITING_FOR_DONE', deadline)
}

void scheduleAudioClipWatchdog(String correlationId, String phase, Long deadline) {
  if(!correlationId || correlationId != (runtimeState.activeAudioClipCorrelationId as String)) { return }
  String watchdogId = UUID.randomUUID().toString()
  Long deadlineAt = deadline ?: now()
  Integer delaySeconds = Math.max(1, (int)Math.ceil((deadlineAt - now()) / 1000.0d))
  runtimeState.audioClipWatchdogRequestId = watchdogId
  Long queueStart = phase == 'WAITING_FOR_ACTIVE'
      ? (runtimeState.audioClipSentAt as Long) : (runtimeState.audioClipQueueStartTime as Long)
  if(queueStart != null) { runtimeState.audioClipQueueStartTime = queueStart }
  runtimeState.audioClipQueueTotalDuration = delaySeconds
  logDebug("Scheduled audio clip watchdog guid=${correlationId} phase=${phase} watchdog=${watchdogId} deadline=${deadlineAt} firesIn=${delaySeconds}s")
  runIn(delaySeconds, 'audioClipWatchdog', [overwrite: true,
    data: [correlationId: correlationId, watchdogId: watchdogId, phase: phase]])
}

void resumeAudioClipAfterSubscription() {
  String correlationId = runtimeState.activeAudioClipCorrelationId as String
  if(!correlationId || runtimeState.audioClipPlaying != true || runtimeState.audioClipCommandSent != true) { return }
  Long connectedAt = now()
  Boolean wasDisconnectedAfterSend = runtimeState.audioClipHadDisconnectAfterSend == true
  runtimeState.audioClipHadDisconnectAfterSend = false
  Map<String, Map> clipProgress = runtimeState.audioClipProgressByCorrelationId instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)runtimeState.audioClipProgressByCorrelationId)
      : new LinkedHashMap<String, Map>()
  Map progress = clipProgress[correlationId] ?: [:]
  Integer duration = coerceClipDuration(progress.duration)
  if(progress.phase == 'ACTIVE') {
    if(duration != null && duration > 0) {
      scheduleAudioClipDoneWatchdog(correlationId, (progress.activeAt ?: connectedAt) as Long, duration)
    } else {
      pauseAudioClipWatchdog()
    }
    return
  }
  // A clip that was queued while offline may have been sent by the outbound
  // queue immediately before this acknowledgement. Its normal ACTIVE timer
  // was already started by audioClipCommandSent(); do not replace it here.
  if(!wasDisconnectedAfterSend) { return }
  if(duration != null && duration > 0) {
    // The command was already sent before disconnection. Reconcile by GUID,
    // then wait through its expected playback window; never resend it.
    scheduleAudioClipDoneWatchdog(correlationId, (runtimeState.audioClipSentAt ?: connectedAt) as Long, duration)
  } else {
    scheduleAudioClipWatchdog(correlationId, 'WAITING_FOR_ACTIVE',
      connectedAt + (AUDIO_CLIP_ACTIVE_TIMEOUT_SECONDS * 1000L))
  }
}

void audioClipWatchdog(Map data = [:]) {
  String correlationId = data?.correlationId as String
  String watchdogId = data?.watchdogId as String
  String phase = data?.phase as String
  if(runtimeState.audioClipPlaying != true || !correlationId ||
      correlationId != (runtimeState.activeAudioClipCorrelationId as String) ||
      !watchdogId || watchdogId != runtimeState.audioClipWatchdogRequestId) {
    logDebug("Ignoring stale audio clip watchdog guid=${correlationId ?: 'unknown'} watchdog=${watchdogId ?: 'unknown'}")
    return
  }
  if(!isWebsocketConnected()) {
    runtimeState.audioClipHadDisconnectAfterSend = runtimeState.audioClipCommandSent == true
    pauseAudioClipWatchdog()
    logDebug("Pausing audio clip watchdog for ${correlationId} while WebSocket is disconnected")
    return
  }
  if(phase == 'WAITING_FOR_ACTIVE') {
    Map<String, Map> progressMap = runtimeState.audioClipProgressByCorrelationId instanceof Map
        ? (Map<String, Map>)runtimeState.audioClipProgressByCorrelationId : [:]
    Map progress = progressMap[correlationId]
    if(progress?.phase in ['ACTIVE', 'DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']) { return }
    markAudioClipWatchdogFailure(correlationId, 'FAILED_ACTIVE_TIMEOUT',
      "No matching ACTIVE status within ${AUDIO_CLIP_ACTIVE_TIMEOUT_SECONDS}s of WebSocket delivery")
    return
  }
  if(phase == 'WAITING_FOR_DONE') {
    Map<String, Map> progressMap = runtimeState.audioClipProgressByCorrelationId instanceof Map
        ? (Map<String, Map>)runtimeState.audioClipProgressByCorrelationId : [:]
    Map progress = progressMap[correlationId]
    if(progress?.phase in ['DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']) { return }
    markAudioClipWatchdogFailure(correlationId, 'FAILED_DONE_TIMEOUT', 'No matching DONE status before the expected-duration deadline')
  }
}

void markAudioClipWatchdogFailure(String correlationId, String phase, String detail) {
  Map<String, Map> clipProgress = runtimeState.audioClipProgressByCorrelationId instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)runtimeState.audioClipProgressByCorrelationId)
      : new LinkedHashMap<String, Map>()
  Map progress = clipProgress[correlationId] ?: [correlationId: correlationId]
  progress.phase = phase
  progress.failureReason = detail
  progress.completedAt = now()
  clipProgress[correlationId] = progress
  setHiddenJsonRuntimeState('audioClipProgressByCorrelationId', clipProgress)
  publishAudioClipProgressEvent(progress, null)
  logWarn("Audio clip ${correlationId} ${phase}: ${detail}; pausing queue to prevent queued clips from playing back-to-back")
  pauseAudioClipWatchdog()
  runtimeState.audioClipPlaying = false
  runtimeState.audioClipCommandSent = false
  runtimeState.remove('audioClipHadDisconnectAfterSend')
  runtimeState.remove('activeAudioClipCorrelationId')
  runtimeState.remove('activeAudioClipSonosId')
  removeHiddenJsonRuntimeState('activeAudioClipMessage')
  removeHiddenJsonRuntimeState('currentlyPlayingClip')
}

void retryAudioClipAfterCommandFailure(String correlationId, String detail) {
  if(!correlationId || correlationId != (runtimeState.activeAudioClipCorrelationId as String)) { return }
  Map<String, Map> clipProgress = runtimeState.audioClipProgressByCorrelationId instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)runtimeState.audioClipProgressByCorrelationId)
      : new LinkedHashMap<String, Map>()
  Map progress = clipProgress[correlationId] ?: [correlationId: correlationId]
  Integer retryCount = (progress.commandRetryCount ?: 0) as Integer
  if(retryCount >= 1) {
    markAudioClipWatchdogFailure(correlationId, 'FAILED_COMMAND',
      "${detail}; one reconnect retry already failed")
    return
  }
  Map clipMessage = (Map)getAtomicStateValue('activeAudioClipMessage')
  String message = clipMessage?.leftChannel?.toString()
  if(!message) {
    markAudioClipWatchdogFailure(correlationId, 'FAILED_COMMAND', "${detail}; active command payload is unavailable")
    return
  }
  progress.commandRetryCount = retryCount + 1
  progress.phase = 'RETRY_PENDING'
  progress.failureReason = detail
  progress.retryQueuedAt = now()
  clipProgress[correlationId] = progress
  setHiddenJsonRuntimeState('audioClipProgressByCorrelationId', clipProgress)
  publishAudioClipProgressEvent(progress, null)
  runtimeState.audioClipHadDisconnectAfterSend = false
  runtimeState.audioClipCommandSent = false
  pauseAudioClipWatchdog()
  queueWebsocketMessage(message, 1, [kind: 'audioClip', correlationId: correlationId])
  setWebSocketStatus('closed')
  logWarn("Audio clip ${correlationId} failed; queued one retry after WebSocket reconnect: ${detail}")
  scheduleWebSocketReconnect()
}

void isFavoritePlaying(Map json) {
  LinkedHashMap container = (LinkedHashMap)json?.container
  LinkedHashMap id = (LinkedHashMap)container?.id
  String objectId = id?.objectId
  if(objectId != null && objectId != '') {
    List tok = objectId.tokenize(':')
    if(tok.size() >= 2) { objectId = tok[1] }
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

  Map retryState = favoriteRetryState.get(device.getDeviceNetworkId())
  if(retryState != null) {
    retryState.metadataConfirmed = (isFav || isFavAlt) && foundFavId == retryState.favoriteId?.toString()
  }

  String groupOperationId = runtimeState.groupFavoriteOperationId as String
  if(groupOperationId && runtimeState.groupFavoriteOperationFavoriteId && getIsGroupCoordinator() == true) {
    emitGroupFavoriteOperationEvent('metadataConfirmed', [
      favoriteId: foundFavId,
      confirmed: (isFav || isFavAlt) && foundFavId == runtimeState.groupFavoriteOperationFavoriteId?.toString(),
      groupId: runtimeState.groupFavoriteOperationGroupId as String
    ])
  }

  setCurrentFavorite(foundFavImageUrl, foundFavId, foundFavName, (isFav||isFavAlt))
}

@CompileStatic
List<String> getPlaylistObjectIdCandidates(String objectId) {
  List<String> candidates = []
  if(objectId == null || objectId == '') { return candidates }
  candidates.add(objectId)
  List<String> tokens = objectId.tokenize(':')
  if(tokens.size() >= 2) { candidates.add(tokens.get(1)) }
  if(tokens.size() >= 3) { candidates.add(tokens.get(tokens.size() - 1)) }
  return candidates
}

void isPlaylistPlaying(Map json) {
  LinkedHashMap container = (LinkedHashMap)json?.container
  LinkedHashMap id = (LinkedHashMap)container?.id
  String objectId = id?.objectId?.toString()
  List<String> objectIdCandidates = getPlaylistObjectIdCandidates(objectId)

  // For playlists, check container type and name
  String containerType = container?.type
  String containerName = container?.name

  // Playlists use simple ID matching since they don't have complex service IDs
  LinkedHashMap matchedById = null
  LinkedHashMap matchedByName = null
  Integer matchingNameCount = 0

  if(containerType == 'playlist' || containerType == 'PLAYLIST') {
    playlistsMap.each { key, value ->
      String mappedId = value?.id?.toString()
      String mappedName = value?.name?.toString()
      if(matchedById == null && mappedId != null && objectIdCandidates.contains(mappedId)) {
        matchedById = value
      }
      if(mappedName != null && mappedName == containerName) {
        matchingNameCount++
        matchedByName = value
      }
    }
  }

  LinkedHashMap matchedPlaylist = matchedById ?: (matchingNameCount == 1 ? matchedByName : null)
  Boolean isPlaylist = matchedPlaylist != null
  String foundPlaylistId = matchedPlaylist?.id?.toString()
  String foundPlaylistName = matchedPlaylist?.name?.toString()

  Map retryState = playlistRetryState.get(device.getDeviceNetworkId())
  if(retryState != null) {
    retryState.metadataConfirmed = isPlaylist && foundPlaylistId == retryState.playlistId?.toString()
  }

  setCurrentPlaylist(foundPlaylistId, foundPlaylistName, isPlaylist)
}

// =============================================================================
// End Websocket Incoming Data Processing
// =============================================================================
