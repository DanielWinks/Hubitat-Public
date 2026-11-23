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
import com.google.common.util.concurrent.Striped
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import hubitat.scheduling.AsyncResponse
import hubitat.device.HubResponse
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLDecoder
import java.util.Collections
import java.util.TreeSet
import java.util.UUID
import java.time.Clock
import org.apache.commons.codec.binary.Base64
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import java.util.Locale

@Field static final List<Integer> ANNOUNCEMENT_RESTORE_RETRY_DELAYS = [1, 2].asImmutable()
@Field static final List<String> LOG_LEVELS = ['trace', 'debug', 'info', 'warn', 'error', 'off']
@Field static final DateTimeFormatter SID_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern('yyyy-MMM-dd h:mm:ss a', Locale.ENGLISH)

ZoneId hubZoneId() {
  return location?.timeZone ? ZoneId.of(location.timeZone.ID) : ZoneId.systemDefault()
}

Clock hubClock() {
  return Clock.system(hubZoneId())
}

Instant hubNowInstant() {
  return hubClock().instant()
}

JsonSlurper getSharedJsonSlurper() {
  String dni = device?.getDeviceNetworkId()?.toString()
  if(dni) {
    return jsonSlurpers.computeIfAbsent(dni, { String ignored -> new JsonSlurper() } as java.util.function.Function<String, JsonSlurper>)
  }
  return jsonSlurpers.computeIfAbsent('__shared__', { String ignored -> new JsonSlurper() } as java.util.function.Function<String, JsonSlurper>)
}

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
  ZoneId zoneId = location?.timeZone ? ZoneId.of(location.timeZone.ID) : ZoneId.systemDefault()
  return Instant.ofEpochSecond(epochSeconds).atZone(zoneId).format(SID_TIMESTAMP_FORMATTER)
}

// =============================================================================
// UPnP service definitions and SOAP/XML helpers
// =============================================================================
// These local definitions replace the former SMAPILibrary dependency. Service
// action defaults are copied before overrides are applied in each SOAP request.
@Field private final Map AVTransport = [
  service: 'AVTransport',
  serviceType: 'urn:schemas-upnp-org:service:AVTransport:1',
  serviceId: 'urn:upnp-org:serviceId:AVTransport',
  controlURL: '/MediaRenderer/AVTransport/Control',
  eventSubURL: '/MediaRenderer/AVTransport/Event',
  actions: [
    SetAVTransportURI: [
      arguments: [InstanceID: 0, CurrentURI: '', CurrentURIMetaData: '']
    ],
    SetNextAVTransportURI: [
      arguments: [InstanceID: 0, NextURI: '', NextURIMetaData: '']
    ],
    AddURIToQueue: [
      arguments: [InstanceID: 0, EnqueuedURI: '', EnqueuedURIMetaData: '', DesiredFirstTrackNumberEnqueued:0, EnqueueAsNext:true],
      outputs: [NumTracksAdded: '', NewQueueLength: '']
    ],
    AddMultipleURIsToQueue: [
      arguments: [InstanceID: 0, UpdateID: '', NumberOfURIs: '', EnqueuedURIs: '', EnqueuedURIsMetaData: '', ContainerURI: '', ContainerMetaData: '', DesiredFirstTrackNumberEnqueued:0, EnqueueAsNext:false],
      outputs: [NumTracksAdded: '', NewQueueLength: '', NewUpdateID: '']
    ],
    ReorderTracksInQueue: [
      arguments: [InstanceID: 0, StartingIndex: '', NumberOfTracks: '', InsertBefore: '', UpdateID: '']
    ],
    RemoveTrackFromQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: '']
    ],
    RemoveTrackRangeFromQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: ''],
      outputs: [NewUpdateID: '']
    ],
    RemoveAllTracksFromQueue: [
      arguments: [InstanceID: 0]
    ],
    SaveQueue: [
      arguments: [InstanceID: 0, Title: '', ObjectID: ''],
      outputs: [AssignedObjectID: '']
    ],
    BackupQueue: [
      arguments: [InstanceID: 0]
    ],
    CreateSavedQueue: [
      arguments: [InstanceID: 0, Title: '', EnqueuedURI: '', EnqueuedURIMetaData: ''],
      outputs: [NumTracksAdded: '', NewQueueLength: '', AssignedObjectID: '', NewUpdateID: '']
    ],
    AddURIToSavedQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: '', EnqueuedURI: '', EnqueuedURIMetaData: '', AddAtIndex: ''],
      outputs: [NumTracksAdded: '', NewQueueLength: '', NewUpdateID: '']
    ],
    ReorderTracksInSavedQueue: [
      arguments: [InstanceID: 0, ObjectID: '', UpdateID: '', TrackList: '', NewPositionList: ''],
      outputs: [QueueLengthChange: '', NewQueueLength: '', NewUpdateID: '']
    ],
    GetMediaInfo: [
      arguments: [InstanceID: 0],
      outputs: [NrTracks: '', MediaDuration: '', CurrentURI: '', CurrentURIMetaData: '', NextURI: '', NextURIMetaData: '', PlayMedium: '', RecordMedium: '', WriteStatus: '']
    ],
    GetTransportInfo: [
      arguments: [InstanceID: 0],
      outputs: [CurrentTransportState: '', CurrentTransportStatus: '', CurrentSpeed: '']
    ],
    GetPositionInfo: [
      arguments: [InstanceID: 0],
      outputs: [Track: '', TrackDuration: '', TrackMetaData: '', TrackURI: '', RelTime: '', AbsTime: '', RelCount: '', AbsCount: '']
    ],
    GetDeviceCapabilities: [
      arguments: [InstanceID: 0],
      outputs: [PlayMedia: '', RecMedia: '', RecQualityModes: '']
    ],
    GetTransportSettings: [
      arguments: [InstanceID: 0],
      outputs: [PlayMode: '', RecQualityMode: '', CurrentSpeed: '']
    ],
    GetCrossfadeMode: [
      arguments: [InstanceID: 0],
      outputs: [CrossfadeMode: '']
    ],
    Stop: [
      arguments: [InstanceID: 0]
    ],
    Play: [
      arguments: [InstanceID: 0, Speed: 1]
    ],
    Pause: [
      arguments: [InstanceID: 0]
    ],
    Seek: [
      arguments: [InstanceID: 0, Unit: '', Target: '']
    ],
    Next: [
      arguments: [InstanceID: 0]
    ],
    Previous: [
      arguments: [InstanceID: 0]
    ],
    SetPlayMode: [
      arguments: [InstanceID: 0, NewPlayMode: '']
    ],
    SetCrossfadeMode: [
      arguments: [InstanceID: 0, CrossfadeMode: '']
    ],
    GetCurrentTransportActions: [
      arguments: [InstanceID: 0],
      outputs: [Actions: '']
    ],
    BecomeCoordinatorOfStandaloneGroup: [
      arguments: [InstanceID: 0],
      outputs: [DelegatedGroupCoordinatorID: '', NewGroupID: '']
    ],
    DelegateGroupCoordinationTo: [
      arguments: [InstanceID: 0, NewCoordinator: '', RejoinGroup: ''] // RejoinGroup: BOOL
    ],
    BecomeGroupCoordinator: [
      arguments: [
        InstanceID: 0,
        CurrentCoordinator: '',
        CurrentGroupID: '',
        OtherMembers: '',
        TransportSettings: '',
        CurrentURI: '',
        CurrentURIMetaData: '',
        SleepTimerState: '',
        AlarmState: '',
        StreamRestartState: '',
        CurrentQueueTrackList: '',
        CurrentVLIState: ''
      ]
    ],
    BecomeGroupCoordinatorAndSource: [
      arguments: [
        InstanceID: 0,
        CurrentCoordinator: '',
        CurrentGroupID: '',
        OtherMembers: '',
        TransportSettings: '',
        CurrentURI: '',
        CurrentURIMetaData: '',
        SleepTimerState: '',
        AlarmState: '',
        StreamRestartState: '',
        CurrentAVTTrackList: '',
        CurrentQueueTrackList: '',
        CurrentSourceState: '',
        ResumePlayback: ''
      ]
    ],
    ChangeCoordinator: [
      arguments: [InstanceID: 0, CurrentCoordinator: '', NewCoordinator: '', NewTransportSettings: '', CurrentAVTransportURI: '']
    ],
    ChangeTransportSettings: [
      arguments: [InstanceID: 0, NewTransportSettings: '', CurrentAVTransportURI: '']
    ],
    ConfigureSleepTimer: [
      arguments: [InstanceID: 0, NewSleepTimerDuration: '']
    ],
    GetRemainingSleepTimerDuration: [
      arguments: [InstanceID: 0],
      outputs: [RemainingSleepTimerDuration: '', CurrentSleepTimerGeneration: '']
    ],
    RunAlarm: [
      arguments: [InstanceID: 0, AlarmID: '', LoggedStartTime: '', Duration: '', ProgramURI: '', ProgramMetaData: '', PlayMode: '', Volume: '', IncludeLinkedZones: '']
    ],
    StartAutoplay: [
      arguments: [InstanceID: 0, ProgramURI: '', ProgramMetaData: '', Volume: '', IncludeLinkedZones: '', ResetVolumeAfter: '']
    ],
    GetRunningAlarmProperties: [
      arguments: [InstanceID: 0, AlarmID: ''],
      outputs: [GroupID: '', LoggedStartTime: '']
    ],
    SnoozeAlarm: [
      arguments: [InstanceID: 0, Duration: '']
    ],
    EndDirectControlSession: [
      arguments: [InstanceID: 0]
    ]
  ],
  stateVariables: [
    TransportState: ['STOPPED', 'PLAYING', 'PAUSED_PLAYBACK', 'TRANSITIONING'],
    CurrentPlayMode: ['NORMAL', 'REPEAT_ALL', 'REPEAT_ONE', 'SHUFFLE_NOREPEAT', 'SHUFFLE', 'SHUFFLE_REPEAT_ONE'],
    NewPlayMode: ['NORMAL', 'REPEAT_ALL', 'REPEAT_ONE', 'SHUFFLE_NOREPEAT', 'SHUFFLE', 'SHUFFLE_REPEAT_ONE'],
    CurrentCrossfadeMode: [true, false],
    A_ARG_TYPE_SeekMode: [ allowedValueList: [ TRACK_NR: '', REL_TIME: '', TIME_DELTA: '' ]] // <- Seek "Unit": Position in queue (1 indexed), hh:mm:ss for REL_TIME, +/-hh:mm:ss for TIME_DELTA
  ]
]

@Field private final Map ContentDirectory = [
  service: 'ContentDirectory',
  serviceType: 'urn:schemas-upnp-org:service:ContentDirectory:1',
  serviceId: 'urn:upnp-org:serviceId:ContentDirectory',
  controlURL: '/MediaServer/ContentDirectory/Control',
  eventSubURL: '/MediaServer/ContentDirectory/Event',
  actions: [
    Browse: [ arguments: [
        ObjectID: '',
        BrowseFlag: "BrowseDirectChildren",
        Filter: '*',
        StartingIndex: 0,
        RequestedCount: 100,
        SortCriteria: ''
      ]
    ]
  ]
]

@Field private final Map ZoneGroupTopology = [
  service: 'ZoneGroupTopology',
  serviceType: 'urn:schemas-upnp-org:service:ZoneGroupTopology:1',
  serviceId: 'urn:upnp-org:serviceId:ZoneGroupTopology',
  controlURL: '/ZoneGroupTopology/Control',
  eventSubURL: '/ZoneGroupTopology/Event',
  actions: [
    GetZoneGroupState: [arguments: [:], outputs: [ZoneGroupState: '']]
  ]
]

@Field private final Map RenderingControl = [
  service: 'RenderingControl',
  serviceType: 'urn:schemas-upnp-org:service:RenderingControl:1',
  controlURL: '/MediaRenderer/RenderingControl/Control',
  eventSubURL: '/MediaRenderer/RenderingControl/Event',
  actions: [
    GetVolume: [arguments: [InstanceID: 0, Channel: "Master"]],
    GetMute: [arguments: [InstanceID: 0, Channel: "Master"]],
    SetVolume: [arguments: [InstanceID: 0, Channel: "Master", DesiredVolume: 0]],
    SetMute: [arguments: [InstanceID: 0, Channel: "Master", DesiredMute: true]],
    SetRelativeVolume: [arguments: [InstanceID: 0, Channel: "Master", Adjustment: 0]],
    GetBass: [arguments: [InstanceID: 0]],
    GetTreble: [arguments: [InstanceID: 0]],
    SetBass: [arguments: [InstanceID: 0, DesiredBass: 0]],
    SetTreble: [arguments: [InstanceID: 0, DesiredTreble: 0]],
    GetLoudness: [arguments: [InstanceID: 0, Channel: "Master"]],
    SetLoudness: [arguments: [InstanceID: 0, Channel: "Master", DesiredLoudness: true]],
    GetEQ: [arguments: [InstanceID: 0, EQType: "NightMode"]],
    SetEQ: [arguments: [InstanceID: 0, EQType: "DialogLevel", DesiredValue: 0]]
  ]
]

@Field private final Map GroupRenderingControl = [
  service: 'GroupRenderingControl',
  serviceType: 'urn:schemas-upnp-org:service:GroupRenderingControl:1',
  controlURL: '/MediaRenderer/GroupRenderingControl/Control',
  eventSubURL: '/MediaRenderer/GroupRenderingControl/Event',
  actions: [
      GetGroupVolume: [arguments: [InstanceID: 0]],
      GetGroupMute: [arguments: [InstanceID: 0]],
      SetGroupVolume: [arguments: [InstanceID: 0, DesiredVolume: 0]],
      SetGroupMute: [arguments: [InstanceID: 0, DesiredMute: true]],
      SetRelativeGroupVolume: [arguments: [InstanceID: 0, Adjustment: 0]] //For whatever reason this doesn't adjust volume properly...
  ]
]


@CompileStatic
GPathResult parseSonosMessageXML(String xml) {
  return new XmlSlurper().parseText(xml)
}

String getBaseControlXML(Map service, String action, Map controlValues = null) {
  Map actionArgs = service.actions[action] as Map
  Map args = new LinkedHashMap(actionArgs.arguments as Map)
  if(controlValues) { controlValues.each { entry -> args[entry.key] = entry.value } }
  StringBuilder xml = new StringBuilder(256)
  xml.append("<?xml version='1.0'?>")
  xml.append('<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">')
  xml.append('<s:Body><u:').append(action).append(' xmlns:u="')
      .append(StringEscapeUtils.escapeXml10(service.serviceType.toString())).append('">')
  args.each { entry ->
    xml.append('<').append(entry.key).append('>')
        .append(StringEscapeUtils.escapeXml10(entry.value == null ? '' : entry.value.toString()))
        .append('</').append(entry.key).append('>')
  }
  xml.append('</u:').append(action).append('></s:Body></s:Envelope>')
  return xml.toString()
}

@CompileStatic
Map getSoapActionParams(String deviceIp, Map service, String action, Map controlValues = null) {
  if(getConfiguredLogLevel() == 'trace') {
    logTrace("Get SOAP action params: IP=${deviceIp} Service=${service} Action=${action} ControlValues=${controlValues}")
  } else {
    logDebug("Preparing SOAP action ${action} for ${deviceIp}; control payload omitted")
  }
  String body = getBaseControlXML(service, action, controlValues)
  String uri
  try {
    uri = new URI('http', deviceIp, service.controlURL?.toString(), null).toString()
  } catch(Exception ignored) {
    uri = "http://${deviceIp}${service.controlURL}"
  }
  String soapAction = "${service.serviceType}#${action}"
  return [uri: uri, headers: [SOAPAction: soapAction], contentType: 'text/xml', body: body]
}

// Hubitat exposes asynchttpPost and state as script bindings rather than
// statically declared methods/properties. Keep that dynamic boundary narrow so
// callers can retain CompileStatic checking for their data transformations.
void postAsyncHttp(String callback, Map params, Map data = null) {
  if(data == null) {
    asynchttpPost(callback, params)
  } else {
    asynchttpPost(callback, params, data)
  }
}

void setDriverStateValue(String key, Object value) {
  runtimeState[key] = value
}

Object getDriverStateValue(String key) {
  return runtimeState[key]
}

Object removeDriverStateValue(String key) {
  return runtimeState.remove(key)
}

metadata {
  definition(
    name: 'Sonos Advanced Player',
    version: '0.18.9',
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
  // App-to-player topology operations must be declared commands so Hubitat
  // permits calls through a child device wrapper.
  command 'playerCreateNewGroup'
  command 'playerCreateGroupRequest', [[name: 'Request JSON', type: 'STRING']]
  command 'playerModifyGroupMembersRequest', [[name: 'Request JSON', type: 'STRING']]
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
  attribute 'groupPlayerIds', 'string'
  attribute 'groupMemberNames', 'JSON_OBJECT'
  attribute 'status' , 'enum', [ 'playing', 'paused', 'stopped' ]
  attribute 'transportStatus' , 'enum', [ 'playing', 'paused', 'stopped' ]
  attribute 'lastUpdated', 'number'
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
  String normalized = level?.toLowerCase(Locale.ROOT)
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
@Field static ConcurrentHashMap<String, Map<String, Map>> audioClipProgressByCorrelationIdByDni = new ConcurrentHashMap<String, Map<String, Map>>()
// Fixed stripes bound lock storage while preserving per-DNI mutual exclusion.
@Field static final Striped<java.util.concurrent.locks.Lock> websocketCommandLedgerStripes = Striped.lock(64)
// Expected topology is stored as a device data value on each affected player,
// so driver instances can coordinate without exposing it in Current States.
// App owns ZGT/AVT/GRC storage; the driver keeps no snapshot copies (see plan storage rule).
// SEQ/SID gate counters remain below: numbers deciding staleness, never payloads.
@Field static ConcurrentHashMap<String, AtomicInteger> currentZgtSeqByDni = new ConcurrentHashMap<String, AtomicInteger>()
// Transient member host cache for group SOAP targeting (playerId -> host); newest-wins, pruned like SEQ counters.
// Group add/detach must address the target player's UPnP host, which exists only in ZGT member data and
// has no device attribute to store app-side. Not a snapshot: only the host string is kept, never membership.
@Field static ConcurrentHashMap<String, String> zgtMemberHostByPlayerId = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, List<Map>> pendingZgtNotifyByDni = new ConcurrentHashMap<String, List<Map>>()
// Transient presence transition baseline (per originating DNI): visible playerId -> zoneName,
// plus vanished-already-notified dedup set. Used only to diff arrived/departed; pruned like SEQ counters.
@Field static ConcurrentHashMap<String, Map> lastZgtPresenceByDni = new ConcurrentHashMap<String, Map>()
// Transient in-flight hand-off for arrived/departed notifications; claimed on flush like pendingZgtNotifyByDni.
@Field static ConcurrentHashMap<String, List<Map>> pendingZgtPresenceByDni = new ConcurrentHashMap<String, List<Map>>()
@Field static ConcurrentHashMap<String, AtomicInteger> currentAvtSeqByDni = new ConcurrentHashMap<String, AtomicInteger>()
@Field static ConcurrentHashMap<String, Map> pendingCoordinatorAvtUpdatesByDni = new ConcurrentHashMap<String, Map>()
@Field static final Striped<java.util.concurrent.locks.Lock> coordinatorAvtUpdateStripes = Striped.lock(64)
@Field static Set<String> coordinatorAvtUpdateScheduledDnis = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>())
@Field static ConcurrentHashMap<String, AtomicInteger> currentGrcSeqByDni = new ConcurrentHashMap<String, AtomicInteger>()
@Field static ConcurrentHashMap<String, String> currentGrcSidByDni = new ConcurrentHashMap<String, String>()
// Transient in-flight hand-off only (latest delta per key, cleared on flush) — not storage.
// Same claim-and-swap pattern as pendingCoordinatorAvtUpdatesByDni; the app holds the canonical list.
@Field static ConcurrentHashMap<String, Map> pendingGrcByDni = new ConcurrentHashMap<String, Map>()
@Field static final Striped<java.util.concurrent.locks.Lock> grcUpdateStripes = Striped.lock(64)
@Field static Set<String> grcUpdateScheduledDnis = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>())
@Field static ConcurrentHashMap<String, Map> currentRcSnapshotByDni = new ConcurrentHashMap<String, Map>()
@Field static ConcurrentHashMap<String, AtomicInteger> currentRcSeqByDni = new ConcurrentHashMap<String, AtomicInteger>()
@Field static ConcurrentHashMap<String, String> currentRcSidByDni = new ConcurrentHashMap<String, String>()
@Field static final Integer TOPOLOGY_CHANGE_SUPPRESSION_SECONDS = 15
@Field static ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>> audioClipQueueHighPriority = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Map>>()
@Field static ConcurrentHashMap<String, LinkedHashMap> audioClipQueueTimers = new ConcurrentHashMap<String, LinkedHashMap>()
@Field static final String WS_OUTBOUND_QUEUE_STATE_KEY = 'websocketOutboundQueue'
@Field static final String WS_OUTBOUND_QUEUE_EXPIRY_AT_STATE_KEY = 'websocketOutboundQueueExpiryAt'
@Field static final String WS_COMMAND_LEDGER_STATE_KEY = 'websocketCommandLedger'
@Field static final String WS_CONNECTION_GENERATION_STATE_KEY = 'websocketConnectionGeneration'
@Field static final String WS_CONNECT_PENDING_STATE_KEY = 'websocketConnectPending'
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
@Field private final String CONTENT_DIRECTORY_EVENTS = '/MediaServer/ContentDirectory/Event'
@Field private final String CONTENT_DIRECTORY_CALLBACK = 'subscribeResubscribeToContentDirectoryCallback'
@Field private final String CONTENT_DIRECTORY_UNSUB_CALLBACK = 'unsubscribeContentDirectoryCallback'

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

@Field private final String HTCONTROL_EVENTS = '/MediaRenderer/HTControl/Event'
@Field private final String HTCONTROL_EVENTS_CALLBACK = 'subscribeResubscribeToHtControlCallback'
@Field private final String HTCONTROL_EVENTS_UNSUB_CALLBACK = 'unsubscribeFromHtControlEventsCallback'

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
@Field static final String LOCAL_EVENT_DRAIN_SCHEDULED_KEY = 'localDeviceEventDrainScheduled'
@Field static final String LOCAL_EVENT_DRAIN_DUE_AT_KEY = 'localDeviceEventDrainDueAt'
@Field static final Integer LOCAL_EVENT_MAX_WAIT_MS = 500
@Field static final Integer LOCAL_EVENTS_PER_DRAIN = 8
@Field static final Integer LOCAL_EVENT_DRAIN_INTERVAL_MS = 200
@Field static final Striped<java.util.concurrent.locks.Lock> localDeviceEventStripes = Striped.lock(64)
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
@Field static final String ZGT_SUB_DEVICE_KEY = 'zgtSub'
@Field static final String ZGT_SUB_EXPIRES_DEVICE_KEY = 'zgtSubExpires'
@Field static final String ZGT_SUB_EXPIRES_EPOCH_DEVICE_KEY = 'zgtSubExpiresEpoch'
@Field static final String HAS_BATTERY_DEVICE_KEY = 'hasBattery'
@Field static ConcurrentHashMap<String, String> lastMetadataContainerId = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, String> lastPlaybackState = new ConcurrentHashMap<String, String>()
@Field static ConcurrentHashMap<String, String> lastWsEventLog = new ConcurrentHashMap<String, String>()
@Field static volatile List<String> cachedTTSVoiceNames = null
@Field static volatile String cachedTTSDefaultVoice = null

// =============================================================================
// Whitelisted persistence — pruning helper surfaces (standalone copy, no #include)
// =============================================================================
@Field static final Set<String> ACTIVE_PLAYER_STATE_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'localDeviceEventDrainScheduled',
  'localDeviceEventDrainDueAt',
  'pendingComponentChildEvents',
  'componentChildDrainScheduled',
  'componentChildDrainDueAt',
  'componentChildRetryAttempts',
  'lastComponentChildEvents',
  'groupDeviceUpdateScheduled',
  'pendingParentAppTtsRequests',
  'favoriteLoadRequestId',
  'playlistLoadRequestId',
  'playlistAddRequestId',
  'queueBrowseRequestId',
  'playbackState',
  'playbackStateObservedAt',
  'audioSourceMetadataType',
  'audioSourceMetadataObservedAt',
  'audioSourceSignalStatus',
  'audioSourceSignalObservedAt',
  'htControlTosLinkConnected',
  'htControlObservedAt',
  'lastError',
  'lastErrorReason',
  'lastErrorObservedAt',
  'currentlyPlayingClip',
  'activeAudioClipMessage',
]))

@Field static final Set<String> ACTIVE_PLAYER_DEVICE_DATA_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'id',
  'name',
  'localUpnpHost',
  'localUpnpPort',
  'websocketUrl',
  'capabilities',
  'householdId',
  'hasBattery',
  'groupCoordinatorId',
  'groupId',
  'groupPlayerIds',
  'isGroupCoordinator',
  'coordinatorStatusChanged',
  'expectedGroupTopology',
  'deviceIp',
  'secondaryIds',
  'secondaryDeviceIps',
  'rightChannelId',
  'rightChannelDeviceIp',
  'enableTrackPositionUpdates',
  'zgtSub',
  'zgtSubExpires',
  'zgtSubExpiresEpoch',
  'hasBattery',
  'rcSnapshotJson',
  'staticDriverActivePlayerRoster',
  'sid1',
  'sid2',
  'sid3',
  'sid4',
  'sid5',
  'sid6',
  'sid1-expires',
  'sid2-expires',
  'sid3-expires',
  'sid4-expires',
  'sid5-expires',
  'sid6-expires',
  'lastAVTransportEvent',
  'lastMrRcEvent',
  'lastZgtEvent',
  'lastMrGrcEvent',
  'lastHtControlEvent',
  'lastWebsocketEvent',
]))

@Field static final Set<String> ACTIVE_PLAYER_CURRENT_STATE_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>([
  'currentRepeatOneMode',
  'currentRepeatAllMode',
  'currentCrossfadeMode',
  'currentShuffleMode',
  'currentTrackDuration',
  'trackPosition',
  'trackPositionUpdated',
  'currentArtistName',
  'albumArtURI',
  'albumArtSmall',
  'albumArtMedium',
  'albumArtLarge',
  'audioSource',
  'currentAlbumName',
  'currentTrackName',
  'currentFavorite',
  'currentPlaylist',
  'currentTrackNumber',
  'nextArtistName',
  'nextAlbumName',
  'nextTrackName',
  'nextTrackAlbumArtURI',
  'queueTrackTotal',
  'queueTrackPosition',
  'treble',
  'bass',
  'loudness',
  'balance',
  'nightMode',
  'speechEnhancement',
  'groupVolume',
  'groupMute',
  'groupName',
  'groupCoordinatorName',
  'isGroupCoordinator',
  'groupId',
  'groupCoordinatorId',
  'isGrouped',
  'groupMemberCount',
  'groupPlayerIds',
  'groupMemberNames',
  'status',
  'transportStatus',
  'lastUpdated',
]))

@Field static final Set<String> ACTIVE_PLAYER_SETTINGS_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'logLevel',
  'enableTrackPositionUpdates',
  'disableTrackDataEvents',
  'includeTrackDataMetaData',
  'volumeAdjustAmountLow',
  'volumeAdjustAmountMid',
  'volumeAdjustAmount',
  'ttsBoostAmount',
  'disableArtistAlbumTrackEvents',
  'disableAlbumArtEvents',
  'createCrossfadeChildDevice',
  'createShuffleChildDevice',
  'createRepeatOneChildDevice',
  'createRepeatAllChildDevice',
  'createMuteChildDevice',
  'createBatteryStatusChildDevice',
  'createFavoritesChildDevice',
  'createPlaylistChildDevice',
  'createQueueChildDevice',
  'createSleepTimerChildDevice',
  'createRightChannelChildDevice',
  'createNightModeChildDevice',
  'createSpeechEnhancementChildDevice',
  'chimeBeforeStandardPriorityTTS',
  'chimeBeforeHighPriorityTTS',
  'enableAirPlayUnmuteVolumeFix',
  'enableRawWebsocketLogging',
]))

// =============================================================================
// Migration-Prune Helpers — whitelist-driven for-each removal
// =============================================================================
Map<String, Object> pruneOrphanedDriverPersistence() {
  Map<String, Object> result = [
    stateRemoved: pruneOrphanStateKeys(state as Map, ACTIVE_PLAYER_STATE_KEYS),
    atomicStateRemoved: pruneOrphanStateKeys(atomicState as Map, Collections.emptySet()),
    deviceDataRemoved: pruneOrphanDeviceData(device, ACTIVE_PLAYER_DEVICE_DATA_KEYS),
    currentStatesRemoved: pruneOrphanCurrentStates(device, ACTIVE_PLAYER_CURRENT_STATE_ATTRIBUTES),
    settingsRemoved: pruneOrphanSettings(ACTIVE_PLAYER_SETTINGS_KEYS),
  ]
  Integer total = ((List)result.stateRemoved).size() + ((List)result.atomicStateRemoved).size() +
    ((List)result.deviceDataRemoved).size() + ((List)result.currentStatesRemoved).size() +
    ((List)result.settingsRemoved).size()
  if(total > 0) {
    logDebug("Driver persistence prune: removed ${total} orphan value(s) (state=${result.stateRemoved}, deviceData=${result.deviceDataRemoved}, currentStates=${result.currentStatesRemoved}, settings=${result.settingsRemoved})")
  }
  return result
}

List<String> pruneOrphanStateKeys(Map persisted, Set<String> active) {
  if(persisted == null || active == null) { return [] }
  List<String> toRemove = new ArrayList<String>(persisted.keySet())
    .findAll { String k -> !active.contains(k.toString()) }
  toRemove.each { String k -> persisted.remove(k) }
  return toRemove
}

Boolean isActiveSidDataKey(String key) {
  if(key == null) { return false }
  if(key ==~ /^sid[1-6]$/) { return true }
  if(key ==~ /^sid[1-6]-expires$/) { return true }
  return false
}

Boolean isActiveWsDebugDataKey(String key) {
  if(key == null) { return false }
  if(key.startsWith('WS-')) {
    return isDebugLoggingEnabled()
  }
  return false
}

List<String> pruneOrphanDeviceData(Object dev, Set<String> active) {
  if(dev == null || active == null) { return [] }
  try {
    Map data = dev.getData()
    if(data == null) { return [] }
    List<String> toRemove = new ArrayList<String>(data.keySet())
      .findAll { String k -> !active.contains(k) && !isActiveSidDataKey(k) && !isActiveWsDebugDataKey(k) }
    toRemove.each { String k -> dev.removeDataValue(k) }
    return toRemove
  } catch(Exception e) {
    logDebug("Could not enumerate device data for prune: ${e.message}")
    return []
  }
}

List<String> pruneOrphanCurrentStates(Object dev, Set<String> active) {
  if(dev == null || active == null) { return [] }
  try {
    List removed = []
    dev.getCurrentStates().each { Object st ->
      String name = null
      try { name = st?.name?.toString() } catch(Exception ignored) { name = null }
      if(name && !active.contains(name)) {
        try { dev.deleteCurrentState(name); removed << name } catch(Exception ignored) { }
      }
    }
    return removed
  } catch(Exception e) {
    logDebug("Could not enumerate current states for prune: ${e.message}")
    return []
  }
}

List<String> pruneOrphanSettings(Set<String> active) {
  if(active == null) { return [] }
  List<String> toRemove = new ArrayList<String>(settings.keySet())
    .findAll { String k -> !active.contains(k) }
  toRemove.each { String k ->
    try { settings.remove(k) } catch(Exception ignored) { }
    try { device.clearSetting(k) } catch(Exception ignored) { }
    try { device.updatedSettings?.remove(k) } catch(Exception ignored) { }
  }
  return toRemove
}

// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Initialize and Configure
// =============================================================================
void uninstalled() {
  unschedule('refreshUpnpMediaCatalogs')
  unschedule('flushPendingLocalDeviceEvents')
  state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
  state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
  unschedule('drainComponentChildEvents')
  unschedule('drainTransportCommandQueue')
  unschedule('watchTransportCommand')
  unschedule('retryUnconfirmedTransportCommand')
  unschedule('watchExpectedUpnpSubscriptionEvents')
  runtimeState.remove(UPNP_EXPECTED_EVENTS_KEY)
  clearStaticDriverStateForCurrentDevice()
}
void installed() { initialize() }
void updated() { handleSettingsUpdate() }

void initialize() {
  performImmediateInitialization()
}

void handleSettingsUpdate() {
  // Pure settings-save path — no purge, no ZGT clear, no parent turn, no UPnP invalidate
  reconcileChildDevices()
  synchronizeTrackPositionPreference()
  migrationCleanup()
  pruneOrphanedDriverPersistence()
  scheduleLocalDeviceEventDrain(LOCAL_EVENT_DRAIN_INTERVAL_MS)
  resetComponentChildEventRetries()
  state.remove(COMPONENT_CHILD_DRAIN_SCHEDULED_KEY)
  scheduleComponentChildEventDrain(COMPONENT_CHILD_DRAIN_INTERVAL_MS)
  state.remove(GROUP_DEVICE_UPDATE_SCHEDULED_KEY)
  scheduleGroupDeviceUpdateDrain(GROUP_DEVICE_UPDATE_MAX_WAIT_MS)
  cancelAudioClipWatchdog()
  if(getId()) {
    audioClipQueueInitialization()
    groupsRegistryInitialization()
    favoritesMapInitialization()
  }
  scheduleAlbumArtPreferenceRefresh()
}





/** Clear this player’s queued publications before local initialization. */
void resetPendingStatePublicationForInitialization() {
  String dni = device.getDeviceNetworkId()
  unschedule('flushPendingLocalDeviceEvents')
  unschedule('drainComponentChildEvents')
  unschedule('flushPendingGroupDeviceUpdates')
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
  clearLegacyBridgeCurrentStates()
  logDebug("Cleared stale state-publication queues and drain jobs for ${dni} before fresh initialization snapshot")
}

void performImmediateInitialization() {
  if(isWebsocketConnected() || runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true) {
    try { wsClose() }
    catch(Exception e) { logDebug("Could not close WebSocket before player initialization: ${e.message}") }
  }
  runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
  setWebSocketStatus('closed')
  clearPendingCommandsForInitialization()
  clearPlayerSubscriptionsForInitialization()
  migrateLegacyRuntimeState()
  pruneOrphanedDriverPersistence()
  resetPendingStatePublicationForInitialization()
  cancelAudioClipWatchdog()
  maybeCleanupStaticDriverState()
  cacheBatteryCapability()
  reconcileChildDevices()

  // Issue UPnP subscriptions first. These calls enqueue async requests locally;
  // starting the WebSocket does not wait for their callbacks.
  subscribeToMrRcEvents()
  subscribeToMrGrcEvents()
  if(hasHTPlaybackCapability()) { subscribeToHtControlEvents() }
  subscribeToZgtEvents()
  if(getIsGroupCoordinator() == true) { subscribeToAVTransport() }
  subscribeToContentDirectory()
  wsConnect()
  if(getIsGroupCoordinator() == true) { getPlaybackMetadataStatus() }
  refreshUpnpMediaCatalogs()
  scheduleWebsocketHealthCheck()
}







void clearPlayerSubscriptionsForInitialization() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  String sid = device.getDataValue(ZGT_SUB_DEVICE_KEY) ?: getSid('sid3')
  Boolean hadValid = subValid('sid3') || sid
  Boolean hadContentDirectorySubscription = subValid('sid6') || getSid('sid6') != null
  invalidateUpnpRequestsForDevice()
  if(hadContentDirectorySubscription) {
    enqueueUpnpRequest('unsubscribe', 'unsubscribeFromContentDirectory', 'resubscribeToContentDirectory',
        'sid6', null, CONTENT_DIRECTORY_UNSUB_CALLBACK, CONTENT_DIRECTORY_EVENTS)
  }
  // Synchronous local delete after generation bump — stale callbacks are guarded by generation
  deleteSid('sid3')
  device.removeDataValue(ZGT_SUB_DEVICE_KEY)
  device.removeDataValue(ZGT_SUB_EXPIRES_DEVICE_KEY)
  device.removeDataValue(ZGT_SUB_EXPIRES_EPOCH_DEVICE_KEY)
  deleteSid('sid1')
  deleteSid('sid2')
  deleteSid('sid4')
  deleteSid('sid5')
  deleteSid('sid6')
  device.removeDataValue('sid1-expires')
  device.removeDataValue('sid2-expires')
  device.removeDataValue('sid3-expires')
  device.removeDataValue('sid4-expires')
  device.removeDataValue('sid5-expires')
  device.removeDataValue('sid6-expires')
  if(hadValid) {
    enqueueUpnpRequest('unsubscribe', 'unsubscribeFromZgtEvents', 'resubscribeToZgtEvents', 'sid3', null, ZGT_EVENTS_UNSUB_CALLBACK, ZGT_EVENTS)
  }
}

void cacheBatteryCapability() {
  if(device.getDataValue(HAS_BATTERY_DEVICE_KEY) != null) { return }
  try {
    Boolean hasBatt = deviceHasBattery()
    device.updateDataValue(HAS_BATTERY_DEVICE_KEY, hasBatt.toString())
  } catch(Exception e) {
    logDebug("cacheBatteryCapability failed: ${e.message}")
  }
}









/** Clear work owned by this player only at an explicit device initialization. */
void clearPendingCommandsForInitialization() {
  String playerId = getId()
  String dni = device.getDeviceNetworkId()
  if(playerId) {
    audioClipQueueInitialization()
    getDeviceAudioClipQueue().clear()
    getDeviceAudioClipQueueHighPriority().clear()
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
   'groupAnnouncementStream', 'announcementPlaybackRestore', 'directStreamTtsRestore',
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
  Map currentRegular = runtimeState.currentlyPlayingClip instanceof Map
      ? (Map)runtimeState.currentlyPlayingClip : null
  if(currentRegular?.correlationId?.toString() == correlationId) {
    removeHiddenJsonRuntimeState('currentlyPlayingClip')
  }
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


void configure() {
  invalidateUpnpRequestsForDevice()
  migrateLegacyRuntimeState()
  pruneOrphanedDriverPersistence()
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
  reconcileChildDevices()
}

void clearLegacyBridgeCurrentStates() {
  try { device.deleteCurrentState('zgtCurrentTopology') } catch(Exception ignored) { }
  try { device.deleteCurrentState('avtTransport') } catch(Exception ignored) { }
  try { device.deleteCurrentState('grcGroupControl') } catch(Exception ignored) { }
  try { device.deleteCurrentState('rcDeviceControl') } catch(Exception ignored) { }
}

void reconcileChildDevices() {
  // Remove transient parent-app bridge attributes from existing devices.
  device.deleteCurrentState('sonosPlayerStateUpdate')
  device.deleteCurrentState('sonosPlayerAppRequest')
  clearLegacyBridgeCurrentStates()
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
  // Ask the parent app to refresh artwork attributes using this device's preference.
  if(getDisableTrackDataEvents()) { clearTrackDataEvent() }
  if(getDisableArtistAlbumTrackEvents()) { clearCurrentNextArtistAlbumTrackData() }
  scheduleAlbumArtPreferenceRefresh()
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
  Boolean wasEnabled = device.getDataValue('enableTrackPositionUpdates')?.toString()?.toLowerCase(Locale.ROOT) == 'true'
  Boolean isEnabled = getEnableTrackPositionUpdates()
  device.updateDataValue('enableTrackPositionUpdates', isEnabled.toString())
  if(wasEnabled && !isEnabled) {
    queuePlayerAttributeUpdate('trackPosition', 0L)
    queuePlayerAttributeUpdate('trackPositionUpdated', 0L)
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
  return websocketCommandLedgerStripes.get(dni)
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
  Object pendingComponentChildEvents = state[PENDING_COMPONENT_CHILD_EVENTS_KEY]
  Object componentChildDrainScheduled = state[COMPONENT_CHILD_DRAIN_SCHEDULED_KEY]
  Object lastComponentChildEvents = state[LAST_COMPONENT_CHILD_EVENTS_KEY]
  state.clear()
  atomicState.clear()
  if(pendingComponentChildEvents != null) { state[PENDING_COMPONENT_CHILD_EVENTS_KEY] = pendingComponentChildEvents }
  if(componentChildDrainScheduled != null) { state[COMPONENT_CHILD_DRAIN_SCHEDULED_KEY] = componentChildDrainScheduled }
  if(lastComponentChildEvents != null) { state[LAST_COMPONENT_CHILD_EVENTS_KEY] = lastComponentChildEvents }
}

Boolean isHiddenJsonStateKey(String name) {
  return name in ['currentlyPlayingClip', 'activeAudioClipMessage']
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
  return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value)).replace("'", '&#39;')
}

void storeHiddenJsonState(String name, String visibleText, Object value) {
  if(value == null) {
    state.remove(name)
    return
  }
  String encoded = Base64.encodeBase64String(JsonOutput.toJson(value).getBytes('UTF-8'))
  state[name] = "<root><span class=\"visible-data\">${escapeHiddenStateHtml(visibleText)}</span><span class=\"hidden-data\" style=\"display:none\">${encoded}</span></root>"
}

Object fetchHiddenJsonStateValue(Object storedValue) {
  if(!(storedValue instanceof CharSequence) || !storedValue.toString().contains('class="hidden-data"')) { return storedValue }
  try {
    GPathResult xml = new XmlSlurper().parseText(storedValue.toString())
    String encoded = xml.span.find { GPathResult span -> span.@class.toString() == 'hidden-data' }?.text()
    if(!encoded) { return null }
    return getSharedJsonSlurper().parseText(new String(Base64.decodeBase64(encoded), 'UTF-8'))
  } catch(Exception e) {
    logWarn("Could not decode hidden JSON state: ${e.message}")
    return null
  }
}

void setHiddenJsonRuntimeState(String name, Object value) {
  if(name == 'audioClipProgressByCorrelationId') {
    String dni = getDeviceDNI()
    if(dni) {
      if(value instanceof Map && !((Map)value).isEmpty()) {
        audioClipProgressByCorrelationIdByDni.put(dni, new LinkedHashMap<String, Map>((Map<String, Map>)value))
      } else {
        audioClipProgressByCorrelationIdByDni.remove(dni)
      }
    }
    state.remove(name)
    runtimeState.remove(name)
    return
  }
  if(value == null) {
    runtimeState.remove(name)
    state.remove(name)
    return
  }
  runtimeState[name] = value
  state.remove(name)
}

void removeHiddenJsonRuntimeState(String name) {
  if(name == 'audioClipProgressByCorrelationId') {
    String dni = getDeviceDNI()
    if(dni) { audioClipProgressByCorrelationIdByDni.remove(dni) }
  }
  runtimeState.remove(name)
  state.remove(name)
}

Map<String, Map> getAudioClipProgressByCorrelationId() {
  String dni = getDeviceDNI()
  if(!dni) { return [:] }
  Map<String, Map> progress = audioClipProgressByCorrelationIdByDni.get(dni)
  return progress ?: [:]
}

void registerRinconId() {
  unschedule('registerRinconId')
  unschedule('checkSubscriptions')
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

    // The socket is retained for audioClip only, so ordinary inactivity is expected.
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
  scheduleAlbumArtPreferenceRefresh()
  audioClipQueueInitialization()
  groupsRegistryInitialization()
  favoritesMapInitialization()

  // After device data is configured, establish this player’s UPnP subscriptions
  // and WebSocket connection locally.
  String localUpnpHost = device.getDataValue('localUpnpHost')
  String websocketUrl = device.getDataValue('websocketUrl')

  if(localUpnpHost && websocketUrl) {
    performImmediateInitialization()
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
  // Whitelist prune now handles debug-only WS-* keys, event timestamps,
  // and stale sid-expiresFormatted keys (see pruneOrphanDeviceData).
  // Kept here only for explicit log-level transitions that need immediate cleanup.
}

void maybeCleanupStaticDriverState() {
  Long nowEpoch = hubNowInstant().epochSecond
  Long lastCleanup = lastStaticStateCleanupEpoch != null ? lastStaticStateCleanupEpoch : 0L
  Long lastAttempt = lastStaticStateCleanupAttemptEpoch != null ? lastStaticStateCleanupAttemptEpoch : 0L
  Long lastCheck = Math.max(lastCleanup, lastAttempt)
  if((nowEpoch - lastCheck) < STATIC_STATE_CLEANUP_INTERVAL_SECONDS) { return }
  lastStaticStateCleanupAttemptEpoch = nowEpoch

  Map rosterSnapshot
  String serializedRoster = device.getDataValue('staticDriverActivePlayerRoster')
  if(serializedRoster) {
    try { rosterSnapshot = (Map)getSharedJsonSlurper().parseText(serializedRoster) }
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
    clearedEntries += clearStaticMap(audioClipProgressByCorrelationIdByDni)
    clearedEntries += clearStaticMap(audioClipQueueHighPriority)
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
    clearedEntries += clearStaticMap(lastMetadataContainerId)
    clearedEntries += clearStaticMap(lastPlaybackState)
    clearedEntries += clearStaticMap(lastWsEventLog)
    clearedEntries += clearStaticMap(zgtMemberHostByPlayerId)
    clearedEntries += clearStaticMap(pendingZgtNotifyByDni)
    clearedEntries += clearStaticMap(pendingZgtPresenceByDni)
    clearedEntries += clearStaticMap(lastZgtPresenceByDni)
    clearedEntries += clearStaticMap(currentAvtSeqByDni)
    clearedEntries += clearStaticMap(pendingCoordinatorAvtUpdatesByDni)
    coordinatorAvtUpdateScheduledDnis.clear()
    clearedEntries += clearStaticMap(pendingGrcByDni)
    grcUpdateScheduledDnis.clear()
    clearedEntries += clearStaticMap(currentGrcSeqByDni)
    clearedEntries += clearStaticMap(currentGrcSidByDni)
    clearedEntries += clearStaticMap(currentRcSnapshotByDni)
    clearedEntries += clearStaticMap(currentRcSeqByDni)
    clearedEntries += clearStaticMap(currentRcSidByDni)
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
  removedEntries += pruneStaticMapKeys(audioClipProgressByCorrelationIdByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(audioClipQueueHighPriority, activePlayerIds)
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
  removedEntries += pruneStaticMapKeys(lastMetadataContainerId, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastPlaybackState, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastWsEventLog, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(zgtMemberHostByPlayerId, activePlayerIds)
  removedEntries += pruneStaticMapKeys(pendingZgtNotifyByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingZgtPresenceByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(lastZgtPresenceByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(currentAvtSeqByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(pendingCoordinatorAvtUpdatesByDni, activePlayerDnis)
  coordinatorAvtUpdateScheduledDnis.removeAll { String dni -> !activePlayerDnis.contains(dni) }
  removedEntries += pruneStaticMapKeys(pendingGrcByDni, activePlayerDnis)
  grcUpdateScheduledDnis.removeAll { String dni -> !activePlayerDnis.contains(dni) }
  removedEntries += pruneStaticMapKeys(currentGrcSeqByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(currentGrcSidByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(currentRcSnapshotByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(currentRcSeqByDni, activePlayerDnis)
  removedEntries += pruneStaticMapKeys(currentRcSidByDni, activePlayerDnis)
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
      zgtMemberHostByPlayerId?.remove(playerId)
    }

    if(dni != null && dni != '') {
      runtimeStateByDni?.remove(dni)
      audioClipProgressByCorrelationIdByDni?.remove(dni)
      favoriteRetryState?.remove(dni)
      playlistRetryState?.remove(dni)
      pendingGroupDeviceUpdates?.remove(dni)
      groupDeviceUpdateRetryAttempts?.remove(dni)
      pendingLocalDeviceEvents?.remove(dni)
      lastLocalDeviceEvents?.remove(dni)
      lastMetadataContainerId?.remove(dni)
      lastPlaybackState?.remove(dni)
      lastWsEventLog?.remove(dni)
      jsonSlurpers?.remove(dni)
      pendingGrcByDni?.remove(dni)
      grcUpdateScheduledDnis?.remove(dni)
      currentGrcSeqByDni?.remove(dni)
      currentGrcSidByDni?.remove(dni)
      currentRcSnapshotByDni?.remove(dni)
      currentRcSeqByDni?.remove(dni)
      currentRcSidByDni?.remove(dni)
      currentAvtSeqByDni?.remove(dni)
      pendingCoordinatorAvtUpdatesByDni?.remove(dni)
      coordinatorAvtUpdateScheduledDnis?.remove(dni)
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
  // Favorite and playlist catalogs are populated through UPnP ContentDirectory Browse.
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
      sendParentAppRequest('activeStreamTts', [requestId: requestId, uri: streamUrl,
        volume: volume?.toString(), duration: ttsDuration, correlationId: pending[requestId].correlationId,
        createdAt: requestCreatedAt])
      logDebug("Parent active-stream TTS decision deferred to drain; request will complete when flush returns")
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
  String normalized = audioSource?.toLowerCase(Locale.ROOT)
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
  logDebug("Queued parent app request action=${action}, eventRequestId=${payload.requestId}, ttsRequestId=${args?.requestId ?: 'none'}, sourceDni=${payload.sourceDni}")
  sendParentAppRequestDirect(action, args)
  return true
}

void sendParentAppRequestDirect(String action, Map args) {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Map payload = [version: 1, channel: 'playerAppRequest', sourceDni: dni, action: action, args: args ?: [:], requestId: UUID.randomUUID().toString()]
  try { parent.playerAppRequest(payload) } catch(Exception e) { logWarn("parent.playerAppRequest failed for ${action}: ${e.message}") }
}
List<Map> drainPendingParentUpdatesForPoll() { return [] }
List<Map> flushPendingParentUpdates() { return [] }
List<Map> drainPendingParentRequestsForPoll() { return [] }
List<Map> flushPendingParentRequests() { return [] }


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
  synchronized(getRuntimeState()) {
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
      logInfo("Captured pre-announcement transport for ${correlationId}: uri=${savedPlayback.currentUri}, metadataLength=${savedPlayback.currentUriMetaData?.toString()?.length() ?: 0}, trackUri=${savedPlayback.trackUri}, track=${savedPlayback.track}, positionMillis=${savedPlayback.positionMillis}, playMode=${savedPlayback.playMode}, transportState=${savedPlayback.transportState}, observedAt=${savedPlayback.observedAt}")
    } else {
      logWarn("No AVTransport snapshot is available to restore after announcement ${correlationId}")
    }
    runtimeState.remove('announcementPlaybackRestore')
    runtimeState.groupAnnouncementStream = [correlationId: correlationId, uri: uri,
        phase: 'LOADING', activeObserved: false, previousGroupVolume: previousGroupVolume,
        announcementVolume: targetVolume]
    logInfo("Starting group announcement stream ${correlationId} on coordinator ${getId()} at volume=${targetVolume}")
    componentSetGroupLevelLocal(new BigDecimal(targetVolume))
    setAVTransportURIAndPlay(uri, null, [groupAnnouncementCorrelationId: correlationId])
  }
}

/** Restore the coordinator's captured transport after its group announcement. */
void restoreGroupAnnouncementPlayback(String correlationId) {
  synchronized(getRuntimeState()) {
    Map snapshot = runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] instanceof Map
        ? (Map)runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY] : null
    Map announcement = runtimeState.groupAnnouncementStream instanceof Map
        ? (Map)runtimeState.groupAnnouncementStream : null
    Boolean matchingAnnouncement = announcement?.correlationId?.toString() == correlationId
    Boolean matchingSnapshot = snapshot?.correlationId?.toString() == correlationId
    if(!matchingSnapshot && (!matchingAnnouncement || snapshot != null)) {
      logDebug("No matching playback snapshot to restore for ${correlationId}")
      publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', [restored: false])
      return
    }
    if(runtimeState.announcementPlaybackRestore?.correlationId == correlationId) { return }
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
    runtimeState.announcementPlaybackRestore = [correlationId: correlationId, snapshot: snapshot, attempt: 1]
    logInfo("Restoring pre-announcement transport for ${correlationId}: ${uri}")
    setAVTransportURIForAnnouncementRestore(uri, snapshot.currentUriMetaData?.toString(), correlationId)
  }
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

Map getCurrentAnnouncementRestore(String correlationId, String stage = null) {
  Map active = runtimeState.announcementPlaybackRestore as Map
  if(!correlationId || active?.correlationId != correlationId ||
      runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY]?.correlationId != correlationId ||
      (stage && active.stage != stage)) { return null }
  return active
}

void retryAnnouncementPlaybackUri(Map data = null) {
  synchronized(getRuntimeState()) {
    String correlationId = data?.correlationId?.toString()
    Map active = getCurrentAnnouncementRestore(correlationId, 'uri-retry')
    if(active == null || active.attempt != data?.attempt) { return }
    Map snapshot = active.snapshot as Map
    String source = device.currentValue('audioSource')?.toString()
    if(source && snapshot.announcementUri && source != snapshot.announcementUri &&
        state.playbackState in ['PLAYBACK_STATE_PLAYING', 'PLAYBACK_STATE_BUFFERING']) {
      completeAnnouncementPlaybackRestore(correlationId, false, 'external-playback')
      return
    }
    setAVTransportURIForAnnouncementRestore(snapshot.currentUri?.toString(),
        snapshot.currentUriMetaData?.toString(), correlationId)
  }
}

void dispatchAnnouncementRestore(String correlationId, String stage, String callback, Map params) {
  Map active = getCurrentAnnouncementRestore(correlationId)
  if(active == null) { return }
  active.stage = stage
  params.timeout = 5
  try {
    postSonosSoapAsync(callback, params, [correlationId: correlationId, attempt: active.attempt, stage: stage])
  } catch(Exception e) {
    completeAnnouncementPlaybackRestore(correlationId, false, "${stage}-dispatch-failed")
  }
}

void setAVTransportURIForAnnouncementRestore(String uri, String metadata, String correlationId) {
  String ip = getLocalUpnpHostForGroupOperation()
  if(!ip) { finishAnnouncementPlaybackRestore(correlationId, false, 'coordinator-unavailable'); return }
  // The MarkupBuilder SOAP builder XML-escapes values; pass raw values here
  // to avoid double-escaping.
  Map restoreService = AVTransport + [actions: [SetAVTransportURI: [arguments: [InstanceID: 0]]]]
  Map values = [CurrentURI: uri, CurrentURIMetaData: metadata]
  Map params = getSoapActionParams(ip, restoreService, 'SetAVTransportURI', values)
  dispatchAnnouncementRestore(correlationId, 'uri', 'announcementPlaybackUriRestored', params)
}

void announcementPlaybackUriRestored(AsyncResponse response, Map data = null) {
  synchronized(getRuntimeState()) {
    String correlationId = data?.correlationId?.toString()
    Map active = getCurrentAnnouncementRestore(correlationId, 'uri')
    if(active == null || active.attempt != data?.attempt) { return }
    Integer status = response?.status
    if(status != 200 || response.hasError()) {
      active.status = status
      Integer attempt = active.attempt as Integer
      if(status != null && status >= 500 && status < 600 && attempt <= ANNOUNCEMENT_RESTORE_RETRY_DELAYS.size()) {
        active.stage = 'uri-retry'
        active.attempt = attempt + 1
        logDebug("Scheduling playback restore attempt ${active.attempt} for ${correlationId} after HTTP ${status}")
        runIn(ANNOUNCEMENT_RESTORE_RETRY_DELAYS[attempt - 1], 'retryAnnouncementPlaybackUri',
            [overwrite: false, data: [correlationId: correlationId, attempt: active.attempt]])
        return
      }
      finishAnnouncementPlaybackRestore(correlationId, false, "set-uri-failed-${status}")
      return
    }
    active.remove('status')
    advanceAnnouncementPlaybackRestore(correlationId, active.snapshot as Map)
  }
}

void advanceAnnouncementPlaybackRestore(String correlationId, Map snapshot) {
  String uri = snapshot.currentUri?.toString()
  // Live radio can report a track number and position, but Sonos cannot seek it.
  if(uri?.startsWith('x-rincon-mp3radio:') || uri?.startsWith('x-sonosapi-stream:') ||
      uri?.startsWith('x-sonosapi-radio:')) {
    finishAnnouncementPlaybackRestore(correlationId, true, null)
    return
  }
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
  dispatchAnnouncementRestore(correlationId, stage, 'announcementPlaybackSeekRestored', params)
}

void announcementPlaybackSeekRestored(AsyncResponse response, Map data = null) {
  synchronized(getRuntimeState()) {
    String correlationId = data?.correlationId?.toString()
    String stage = data?.stage?.toString()
    if(!(stage in ['track', 'position'])) { return }
    Map active = getCurrentAnnouncementRestore(correlationId, stage)
    if(active == null || active.attempt != data?.attempt) { return }
    if(response?.status != 200 || response.hasError()) {
      active.stepFailure = [stage: data?.stage, status: response?.status, reason: "${data?.stage}-restore-failed-${response?.status}"]
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
}

void restoreAnnouncementPlayMode(String correlationId) {
  Map active = getCurrentAnnouncementRestore(correlationId)
  Map snapshot = active?.snapshot instanceof Map ? (Map)active.snapshot : [:]
  String playMode = snapshot.playMode?.toString()
  if(!playMode) { finishAnnouncementPlaybackRestore(correlationId, true, null); return }
  String ip = getLocalUpnpHostForGroupOperation()
  if(!ip) { finishAnnouncementPlaybackRestore(correlationId, false, 'coordinator-unavailable'); return }
  Map params = getSoapActionParams(ip, AVTransport, 'SetPlayMode', [NewPlayMode: playMode])
  dispatchAnnouncementRestore(correlationId, 'play-mode', 'announcementPlaybackPlayModeRestored', params)
}

void announcementPlaybackPlayModeRestored(AsyncResponse response, Map data = null) {
  synchronized(getRuntimeState()) {
    String correlationId = data?.correlationId?.toString()
    Map active = getCurrentAnnouncementRestore(correlationId, 'play-mode')
    if(active == null || active.attempt != data?.attempt) { return }
    Boolean success = response?.status == 200 && response.hasError() == false
    if(!success) {
      active.stepFailure = active.stepFailure ?: [stage: 'play-mode', status: response?.status, reason: "play-mode-restore-failed-${response?.status}"]
      logWarn("Could not restore play mode for announcement ${correlationId}: status=${response?.status}")
    }
    finishAnnouncementPlaybackRestore(correlationId, true, null)
  }
}

String formatSonosTime(Long positionMillis) {
  Duration duration = Duration.ofMillis(Math.max(0L, positionMillis))
  Long hours = duration.toHours()
  Duration afterHours = duration.minusHours(hours)
  Long minutes = afterHours.toMinutes()
  Long seconds = afterHours.minusMinutes(minutes).seconds
  return String.format(Locale.ROOT, '%02d:%02d:%02d', hours, minutes, seconds)
}

void finishAnnouncementPlaybackRestore(String correlationId, Boolean success, String reason) {
  Map active = getCurrentAnnouncementRestore(correlationId)
  if(active?.correlationId?.toString() != correlationId) { return }
  Map snapshot = active.snapshot instanceof Map ? (Map)active.snapshot : [:]
  if(success && snapshot.transportState?.toString() in ['PLAYING', 'TRANSITIONING', 'PAUSED_PLAYBACK']) {
    String ip = getLocalUpnpHostForGroupOperation()
    if(ip) {
      String action = snapshot.transportState?.toString() == 'PAUSED_PLAYBACK' ? 'Pause' : 'Play'
      Map params = getSoapActionParams(ip, AVTransport, action)
      dispatchAnnouncementRestore(correlationId, 'transport', 'announcementPlaybackTransportRestored', params)
      return
    }
    success = false
    reason = 'coordinator-unavailable'
  }
  completeAnnouncementPlaybackRestore(correlationId, success, reason)
}

void announcementPlaybackTransportRestored(AsyncResponse response, Map data = null) {
  synchronized(getRuntimeState()) {
    String correlationId = data?.correlationId?.toString()
    Map active = getCurrentAnnouncementRestore(correlationId, 'transport')
    if(active == null || active.attempt != data?.attempt) { return }
    Boolean success = response?.status == 200 && response.hasError() == false
    if(!success) { active.status = response?.status }
    completeAnnouncementPlaybackRestore(correlationId, success,
        success ? null : "transport-resume-failed-${response?.status}")
  }
}

void completeAnnouncementPlaybackRestore(String correlationId, Boolean success, String reason) {
  Map active = getCurrentAnnouncementRestore(correlationId)
  if(active?.correlationId?.toString() != correlationId) { return }
  Map failure = active.stepFailure as Map
  if(success && failure != null) {
    success = false
    reason = failure.reason?.toString()
  }
  Map result = [restored: success, reason: reason]
  if(reason == 'external-playback') {
    result.skipped = reason
  } else if(!success) {
    runtimeState.lastAnnouncementPlaybackRestoreFailure = [correlationId: correlationId,
        snapshot: active.snapshot, stage: failure?.reason == reason ? failure?.stage : active.stage,
        status: failure?.reason == reason ? failure?.status : active.status,
        attempt: active.attempt, reason: reason, timestamp: now()]
  }
  runtimeState.remove('announcementPlaybackRestore')
  runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
  if(runtimeState.groupAnnouncementStream?.correlationId == correlationId) {
    runtimeState.remove('groupAnnouncementStream')
  }
  if(!success) { logWarn("Playback restore for ${correlationId} was incomplete: ${reason}") }
  publishGroupAnnouncementProgress(correlationId, 'PLAYBACK_RESTORED', result)
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
  Integer targetVolume = Math.max(0, Math.min(100, roundVolume(level)))
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
    try {
      Boolean accepted = parent.playerCommandRequest(new LinkedHashMap(payload)) as Boolean
      if(accepted) {
        logDebug("Parent accepted command ${payload.command} (${payload.requestId})")
        acknowledgeParentCommand(payload.requestId?.toString())
      } else {
        logWarn("Parent rejected command ${payload.command} (${payload.requestId}); retrying")
        runIn(5, 'retryParentCoordinatorCommand', [overwrite: true])
      }
    } catch(Exception e) {
      logWarn("Parent command ${payload.command} (${payload.requestId}) failed: ${e.message}")
      runIn(5, 'retryParentCoordinatorCommand', [overwrite: true])
    }
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
  Integer targetVolume = Math.max(0, Math.min(100, roundVolume(level)))
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
void loadFavorite(String favoriteId = null) {
  if(StringUtils.isBlank(favoriteId)) {
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
  synchronized(runtimeState) {
    List<Map> pending = runtimeState.pendingGroupFavoriteOperationEvents instanceof List
        ? (List<Map>)runtimeState.pendingGroupFavoriteOperationEvents : []
    pending.add(payload)
    runtimeState.pendingGroupFavoriteOperationEvents = pending
    if(runtimeState.groupFavoriteOperationDispatchScheduled != true) {
      runtimeState.groupFavoriteOperationDispatchScheduled = true
      runInMillis(1, 'flushPendingGroupFavoriteOperationEvents', [overwrite: true])
    }
  }
}

void flushPendingGroupFavoriteOperationEvents() {
  List<Map> pending
  synchronized(runtimeState) {
    pending = runtimeState.pendingGroupFavoriteOperationEvents instanceof List
        ? new ArrayList<Map>((List<Map>)runtimeState.pendingGroupFavoriteOperationEvents) : []
    runtimeState.remove('pendingGroupFavoriteOperationEvents')
    runtimeState.remove('groupFavoriteOperationDispatchScheduled')
  }
  pending.each { Map payload ->
    try { parent.playerGroupFavoriteOperationUpdate(payload) }
    catch(Exception e) {
      logWarn("Deferred group Favorite update failed: ${e.message}")
      synchronized(runtimeState) {
        List<Map> retry = runtimeState.pendingGroupFavoriteOperationEvents instanceof List
            ? (List<Map>)runtimeState.pendingGroupFavoriteOperationEvents : []
        retry.add(payload)
        runtimeState.pendingGroupFavoriteOperationEvents = retry
        if(runtimeState.groupFavoriteOperationDispatchScheduled != true) {
          runtimeState.groupFavoriteOperationDispatchScheduled = true
          runIn(1, 'flushPendingGroupFavoriteOperationEvents', [overwrite: true])
        }
      }
    }
  }
}

// Playlist Methods
@CompileStatic
void loadPlaylist(String playlistId = null) {
  if(StringUtils.isBlank(playlistId)) {
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
  if(StringUtils.isBlank(playlistId)) {
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
  if(StringUtils.isBlank(queueMode)) { return null }
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
  queuePlayerAttributeUpdate('nextArtistName', nextArtistName ?: 'Not Available')
  queuePlayerAttributeUpdate('nextAlbumName', nextAlbumName ?: 'Not Available')
  queuePlayerAttributeUpdate('nextTrackName', nextTrackName ?: 'Not Available')
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

GPathResult findXmlDescendantByLocalName(GPathResult node, String localName) {
  if(node == null || !localName) { return null }
  for(Object child : node.children()) {
    if(child instanceof GPathResult) {
      GPathResult current = (GPathResult)child
      if(xmlLocalName(current) == localName) { return current }
      GPathResult hit = findXmlDescendantByLocalName(current, localName)
      if(hit != null) { return hit }
    }
  }
  return null
}

String findSleepTimerXmlText(GPathResult node, String elementName) {
  if(node == null || !elementName) { return null }
  return findXmlDescendantByLocalName(node, elementName)?.text()
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
Long getHubitatCurrentTime() { return now() }

@CompileStatic
void parse(String raw) {
  if(raw == null || raw == '') {return}
  if(raw.startsWith('UPNP_RESPONSE|')) {
    try { queuePlayerAttributeUpdate('lastUpdated', getHubitatCurrentTime()) } catch(Exception ignored) { }
    logDebug("UPnP response parse entered; payloadLength=${raw.length()}")
    handleUpnpRawResponse(raw)
    return
  }
  if(!raw.startsWith('mac:')) {
    try { queuePlayerAttributeUpdate('lastUpdated', getHubitatCurrentTime()) } catch(Exception ignored) { }
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
    String serviceType = messageHeaders["X-SONOS-SERVICETYPE"]?.toString()
    if(!(serviceType in ['ZoneGroupTopology', 'AVTransport', 'GroupRenderingControl'])) {
      try { queuePlayerAttributeUpdate('lastUpdated', getHubitatCurrentTime()) } catch(Exception ignored) { }
    }
    String seqRaw = messageHeaders["SEQ"] ?: messageHeaders["X-SONOS-SEQ"]
    Integer seq = null
    if(seqRaw != null) {
      try { seq = Integer.parseInt(seqRaw.toString().trim()) } catch(Exception ignored) { seq = null }
    }
    String seqDisplay = seq == null ? 'unset' : seq.toString()
    String sid = messageHeaders["SID"]?.toString()?.trim() ?: null
    if(sid == '') { sid = null }
    logDebug("UPnP NOTIFY parsed; serviceType=${serviceType ?: 'header-unset'}, seq=${seqDisplay}, bodyLength=${xmlBody.length()}, headerNames=${messageHeaders.keySet().join(',')}")
    if(serviceType == 'ZoneGroupTopology' || serviceType == 'AVTransport' || serviceType == 'ContentDirectory') {
      logDebug("UPnP NOTIFY header values; serviceType=${serviceType ?: 'header-unset'}, seq=${seqDisplay}, headerValues=${renderHeaderValues(messageHeaders)}")
    }
    // Header switch: serviceType is the sole dispatch for all UPnP services
    if(serviceType != null) {
      switch(serviceType) {
        case 'ZoneGroupTopology':
          try {
            if(!isPlausibleXmlPayload(xmlBody)) { logDebug('Ignoring blank or non-XML ZGT event payload'); return }
            Map snapshot = parseZgtFullSnapshot(xmlBody)
            if(snapshot == null) { return }
            cacheZgtMemberHosts((List<Map>)snapshot.groups)
            String zgtDni = device.getDeviceNetworkId()?.toString() ?: 'unknown'
            Map<String, List<Map>> eventsByGroupId = projectZgtEventsByGroupId((List<Map>)snapshot.groups)
            queueParsedUpnpRelay([service: 'ZGT', sourceDni: zgtDni, eventsByGroupId: eventsByGroupId])
          } catch (Exception e) { logWarn("Ran into an issue parsing zgt: ${e}") }
          return
        case 'AVTransport':
          try {
            if(!isPlausibleXmlPayload(xmlBody)) { logDebug('Ignoring blank or non-XML AVT event payload'); return }
            Map snapshot = parseAvtFullSnapshot(xmlBody)
            if(snapshot == null) { return }
            String sourceDni = device.getDeviceNetworkId()?.toString() ?: 'unknown'
            String rincon = resolveCurrentCoordinatorRinconForAvt(sourceDni) ?: getId()
            List<Map> events = buildAvtEvents(snapshot, [:])
            queueParsedUpnpRelay([service: 'AVT', sourceDni: sourceDni, rincon: rincon, events: events])
          } catch (Exception e) { logWarn("Ran into an issue parsing avt: ${e}") }
          return
        case 'RenderingControl':
          try {
            logDebug('RenderingControl event processing beginning')
            observeExpectedUpnpSubscriptionEvent('sid2')
            setLastInboundMrRcEvent()
            String rcDni = device.getDeviceNetworkId()?.toString() ?: 'unknown'
            logZgtXmlAtTrace(rcDni, xmlBody)
            if(!isPlausibleXmlPayload(xmlBody)) { logDebug('Ignoring blank or non-XML RC event payload'); return }
            deferOrProcessRenderingControlPayload(rcDni, xmlBody, seq, sid)
            logDebug('RenderingControl event processing completed')
          } catch (Exception e) { logException('RenderingControl event processing failed', e) }
          return
        case 'GroupRenderingControl':
          try {
            if(!isPlausibleXmlPayload(xmlBody)) { logDebug('Ignoring blank or non-XML GRC event payload'); return }
            Map snapshot = parseGrcFullSnapshot(xmlBody)
            if(snapshot == null) { return }
            String sourceDni = device.getDeviceNetworkId()?.toString() ?: 'unknown'
            String rincon = resolveCurrentCoordinatorRinconForAvt(sourceDni) ?: getId()
            List<Map> events = buildGrcEvents(snapshot)
            queueParsedUpnpRelay([service: 'GRC', sourceDni: sourceDni, rincon: rincon, events: events])
          } catch (Exception e) { logWarn("Ran into an issue parsing mgrc: ${e}") }
          return
        case 'HTControl':
          try {
            setLastInboundHtControlEvent()
            if(!isPlausibleXmlPayload(xmlBody)) { logDebug('Ignoring blank or non-XML HTControl event payload'); return }
            handleHtControlPayload(xmlBody)
          } catch(Exception e) { logWarn("Ran into an issue parsing HTControl: ${e}") }
          return
        case 'ContentDirectory':
          try {
            observeExpectedUpnpSubscriptionEvent('sid6')
            if(!isPlausibleXmlPayload(xmlBody)) { logDebug('Ignoring blank or non-XML ContentDirectory event payload'); return }
            handleContentDirectoryPayload(xmlBody)
          } catch(Exception e) { logWarn("Ran into an issue parsing ContentDirectory: ${e}") }
          return
      }
    }
    logPayloadAtTrace('UPnP NOTIFY message had an unrecognized service type; payload omitted',
        "Could not determine service type for message: ${message}")
  } catch (Exception e) { logException('UPnP NOTIFY parse failed', e) }
}

// Parse handlers relay sendEvent-ready payloads after parse() releases the player gate.
void queueParsedUpnpRelay(Map payload) {
  if(payload == null || parent == null) { return }
  runInMillis(1, 'relayParsedUpnpToParent', [overwrite: false, data: new LinkedHashMap(payload)])
}

void relayParsedUpnpToParent(Map data) {
  if(data == null || parent == null) { return }
  try {
    parent.putPlayerUpnpEvents(new LinkedHashMap(data))
  } catch(Exception e) {
    logWarn("Could not relay parsed UPnP ${data.service ?: 'unknown'} data to parent: ${e.message}")
  }
}

// Stale payloads are dropped before enqueue because republished stale state has no value;
// kept driver-side so concurrent parses cannot reorder the coalescing queue.
Boolean tryAcceptSeq(String serviceLabel, ConcurrentHashMap seqMap, String dni, Integer incomingSeq) {
  if(seqMap == null || dni == null || incomingSeq == null) { return true }
  AtomicInteger ref = seqMap.get(dni)
  if(ref == null) {
    AtomicInteger created = new AtomicInteger(incomingSeq)
    seqMap.putIfAbsent(dni, created)
    ref = seqMap.get(dni)
  }
  if(ref.get() > incomingSeq) {
    logDebug("Ignoring stale ${serviceLabel} SEQ=${incomingSeq} <= prev=${ref.get()} for ${dni}")
    return false
  }
  Integer prevVal = ref.get()
  while(incomingSeq > prevVal && !ref.compareAndSet(prevVal, incomingSeq)) {
    prevVal = ref.get()
    if(incomingSeq <= prevVal) {
      logDebug("Ignoring stale ${serviceLabel} SEQ=${incomingSeq} <= prev=${prevVal} for ${dni}")
      return false
    }
  }
  return true
}

void deferOrProcessZoneGroupTopologyPayload(String dni, String xmlBody) {
  deferOrProcessZoneGroupTopologyPayload(dni, xmlBody, null)
}

void deferOrProcessZoneGroupTopologyPayload(String dni, String xmlBody, Integer incomingSeq) {
  String deviceDni = dni ?: device.getDeviceNetworkId()
  if(!deviceDni || !xmlBody) { return }
  if(!isPlausibleXmlPayload(xmlBody)) { return }
  if(!tryAcceptSeq('ZGT', currentZgtSeqByDni, deviceDni, incomingSeq)) { return }
  Map newSnapshot = parseZgtFullSnapshot(xmlBody)
  if(newSnapshot == null) { return }
  if(incomingSeq != null) {
    AtomicInteger ref2 = currentZgtSeqByDni.get(deviceDni)
    if(ref2 != null && incomingSeq < ref2.get()) {
      logDebug("Ignoring ZGT that became stale during parse SEQ=${incomingSeq} < prev=${ref2.get()} for ${deviceDni}")
      return
    }
    if(ref2 != null) { ref2.set(incomingSeq) } else { currentZgtSeqByDni.put(deviceDni, new AtomicInteger(incomingSeq)) }
  }
  List<Map> groups = (List<Map>)newSnapshot.groups
  cacheZgtMemberHosts(groups)
  queueZgtPresenceTransitions(deviceDni, groups, (List<Map>)newSnapshot.vanishedDevices)
  Map<String, List<Map>> eventsByGroupId = projectZgtEventsByGroupId(groups)
  queueZgtTopologyNotification(deviceDni, eventsByGroupId)
}

// Refresh the transient host cache (see field comment); parse is otherwise store-free.
void cacheZgtMemberHosts(List<Map> groups) {
  if(!(groups instanceof List)) { return }
  groups.each { Map g ->
    if(g?.members instanceof List) {
      ((List<?>)g.members).each { Object mem ->
        Map mm = (Map)mem
        String pid = mm?.playerId?.toString()
        String location = mm?.location?.toString()
        if(pid && location) {
          Integer schemeEnd = location.indexOf('://')
          Integer hostStart = schemeEnd >= 0 ? schemeEnd + 3 : 0
          Integer hostEnd = location.indexOf('/', hostStart)
          String host = hostEnd >= 0 ? location.substring(hostStart, hostEnd) : location.substring(hostStart)
          if(host) { zgtMemberHostByPlayerId.put(pid, host) }
        }
      }
    }
  }
}

// Stored as sendEvent-ready attribute maps for parent-app delivery.
Map<String, List<Map>> projectZgtEventsByGroupId(List<Map> groups) {
  Map<String, List<Map>> eventsByGroupId = [:]
  if(!(groups instanceof List)) { return eventsByGroupId }
  groups.each { Map g ->
    String groupId = g?.groupId?.toString()
    if(!groupId) { return }
    List<String> visibleIds = []
    List<String> visibleNames = []
    if(g?.members instanceof List) {
      ((List<?>)g.members).each { Object mem ->
        Map mm = (Map)mem
        String pid = mm?.playerId?.toString()
        Boolean isInvisible = (mm?.invisible == true || mm?.invisible?.toString() == '1')
        if(pid && !isInvisible) {
          visibleIds << pid
          String zoneName = mm?.zoneName?.toString()
          if(zoneName) { visibleNames << zoneName }
        }
      }
    }
    if(visibleIds.isEmpty() && g?.playerIds instanceof List) {
      visibleIds = ((List<?>)g.playerIds).collect { Object id -> id?.toString() }.findAll { String id -> id }
    }
    String coordinatorId = g?.coordinatorId?.toString()
    String coordinatorName = g?.coordinatorName?.toString()
    String groupName = g?.groupName?.toString() ?: coordinatorName
    List<Map> events = []
    if(!visibleIds.isEmpty()) {
      events << [name: 'groupPlayerIds', value: visibleIds.join(',')]
    }
    if(coordinatorId) { events << [name: 'groupCoordinatorId', value: coordinatorId] }
    Integer memberCount = visibleIds.size()
    events << [name: 'isGrouped', value: (memberCount > 1 ? 'on' : 'off')]
    events << [name: 'groupMemberCount', value: memberCount]
    if(groupName) { events << [name: 'groupName', value: groupName] }
    if(coordinatorName) { events << [name: 'groupCoordinatorName', value: coordinatorName] }
    if(!visibleNames.isEmpty()) { events << [name: 'groupMemberNames', value: visibleNames.toString()] }
    if(!events.isEmpty()) { eventsByGroupId[groupId] = events }
  }
  return eventsByGroupId
}

// Transient ordered hand-off only, not storage. Preserve each notification until
// the deferred flush calls the app so concurrent parse executions do not coalesce reports.
void queueZgtTopologyNotification(String deviceDni, Map<String, List<Map>> eventsByGroupId) {
  if(!deviceDni || eventsByGroupId == null) { return }
  Map<String, List<Map>> snapshot = new LinkedHashMap<String, List<Map>>()
  eventsByGroupId.each { String groupId, List<Map> events ->
    snapshot[groupId] = events.collect { Map event -> new LinkedHashMap(event) }
  }
  synchronized(pendingZgtNotifyByDni) {
    List<Map> pending = pendingZgtNotifyByDni.get(deviceDni) ?: []
    pending.add(snapshot)
    pendingZgtNotifyByDni.put(deviceDni, pending)
  }
  try {
    runInMillis(1, 'flushPendingZgtNotify', [overwrite: false])
  } catch(Exception e) {
    logWarn("Could not schedule ZGT parent notification for ${deviceDni}: ${e.message}")
    pendingZgtNotifyByDni.remove(deviceDni)
  }
}

// Presence transitions are reported for each player's own notification stream.
void queueZgtPresenceTransitions(String deviceDni, List<Map> groups, List<Map> vanishedDevices) {
  if(!deviceDni) { return }
  List<Map> groupList = (groups instanceof List) ? groups : []
  List<Map> vanishedList = (vanishedDevices instanceof List) ? vanishedDevices : []
  if(groupList.isEmpty() && vanishedList.isEmpty()) { return }
  Map<String, String> currentVisible = [:]
  groupList.each { Map g ->
    if(g?.members instanceof List) {
      ((List<?>)g.members).each { Object mem ->
        Map mm = (Map)mem
        String pid = mm?.playerId?.toString()
        Boolean isInvisible = (mm?.invisible == true || mm?.invisible?.toString() == '1')
        if(pid && !isInvisible) { currentVisible[pid] = mm?.zoneName?.toString() ?: '' }
      }
    }
  }
  Map<String, Map> vanishedByUuid = [:]
  vanishedList.each { Map vd -> String uuid = vd?.uuid?.toString(); if(uuid) { vanishedByUuid[uuid] = vd } }
  Map prev = lastZgtPresenceByDni.get(deviceDni)
  Boolean initialized = prev != null && prev['initialized'] == true
  Map<String, String> prevVisible = [:]
  Set<String> vanishedNotified = new HashSet<String>()
  if(prev != null) {
    if(prev['visible'] instanceof Map) { prevVisible.putAll((Map<String, String>)prev['visible']) }
    if(prev['vanishedNotified'] instanceof Collection) { (prev['vanishedNotified'] as Collection).each { Object v -> if(v) { vanishedNotified.add(v.toString()) } } }
  }
  List<Map> arrived = []
  List<Map> departed = []
  if(!initialized) {
    vanishedByUuid.keySet().each { String uuid ->
      if(!currentVisible.containsKey(uuid)) {
        Map vd = vanishedByUuid[uuid]
        String name = vd?.zoneName?.toString() ?: ''
        departed << [rincon: uuid, dni: safeDniFromRincon(uuid), name: name]
      }
    }
  } else {
    currentVisible.keySet().each { String pid ->
      if(!prevVisible.containsKey(pid)) {
        arrived << [rincon: pid, dni: safeDniFromRincon(pid), name: currentVisible[pid]]
      }
    }
    prevVisible.keySet().each { String pid ->
      if(!currentVisible.containsKey(pid)) {
        String name = prevVisible[pid]
        Map vd = vanishedByUuid[pid]
        if(vd?.zoneName) { name = vd.zoneName.toString() }
        departed << [rincon: pid, dni: safeDniFromRincon(pid), name: name]
      }
    }
    vanishedByUuid.keySet().each { String uuid ->
      if(!currentVisible.containsKey(uuid) && !prevVisible.containsKey(uuid) && !vanishedNotified.contains(uuid)) {
        Map vd = vanishedByUuid[uuid]
        departed << [rincon: uuid, dni: safeDniFromRincon(uuid), name: vd?.zoneName?.toString() ?: '']
      }
    }
  }
  Set<String> nextVanishedNotified = new HashSet<String>(vanishedNotified)
  nextVanishedNotified.addAll(vanishedByUuid.keySet())
  departed.each { Map d -> if(d?.rincon) { nextVanishedNotified.add(d.rincon.toString()) } }
  arrived.each { Map a -> if(a?.rincon) { nextVanishedNotified.remove(a.rincon.toString()) } }
  lastZgtPresenceByDni.put(deviceDni, [initialized: true, visible: new LinkedHashMap<String, String>(currentVisible), vanishedNotified: nextVanishedNotified])
  if(arrived.isEmpty() && departed.isEmpty()) { return }
  Map pending = [arrived: new ArrayList<Map>(arrived), departed: new ArrayList<Map>(departed)]
  synchronized(pendingZgtPresenceByDni) {
    List<Map> pendingReports = pendingZgtPresenceByDni.get(deviceDni) ?: []
    pendingReports.add(pending)
    pendingZgtPresenceByDni.put(deviceDni, pendingReports)
  }
  try {
    runInMillis(1, 'flushPendingZgtPresence', [overwrite: false])
  } catch(Exception e) {
    logWarn("Could not schedule ZGT presence notification for ${deviceDni}: ${e.message}")
    pendingZgtPresenceByDni.remove(deviceDni)
  }
}

// Deferred so parse() returns before the app gate runs; mirrors flushPendingZgtNotify.
void flushPendingZgtPresence() {
  if(pendingZgtPresenceByDni.isEmpty()) { return }
  Map<String, List<Map>> drained = [:]
  synchronized(pendingZgtPresenceByDni) {
    drained.putAll(pendingZgtPresenceByDni)
    pendingZgtPresenceByDni.clear()
  }
  drained.each { String dni, List<Map> reports ->
    reports.each { Map transitions ->
      if(!(transitions instanceof Map)) { return }
      List<Map> departed = (transitions['departed'] instanceof List) ? (List<Map>)transitions['departed'] : []
      List<Map> arrived = (transitions['arrived'] instanceof List) ? (List<Map>)transitions['arrived'] : []
      departed.each { Map d -> notifyParentPresence('departed', dni, d) }
      arrived.each { Map a -> notifyParentPresence('arrived', dni, a) }
    }
  }
}

void notifyParentPresence(String kind, String sourceDni, Map entry) {
  String rincon = entry?.rincon?.toString()
  if(!rincon) { return }
  String dni = entry?.dni?.toString() ?: safeDniFromRincon(rincon)
  String name = entry?.name?.toString() ?: ''
  try {
    if(parent != null) {
      if(kind == 'arrived') { parent.playerArrived(rincon, dni, name) } else { parent.playerDeparted(rincon, dni, name) }
    } else {
      logDebug("Standalone ZGT presence (no parent) kind=${kind} dni=${dni} rincon=${rincon} name=${name}")
    }
  } catch(Exception e) {
    logWarn("parent ZGT presence notification failed kind=${kind} for ${rincon}: ${e.message}")
    try { logDebug("ZGT presence failure detail: ${e}") } catch(Exception ignored) { }
  }
}

// One contract: the flush calls the pure parent setter directly (no fallback chain).
void flushPendingZgtNotify() {
  if(pendingZgtNotifyByDni.isEmpty()) { return }
  Map<String, List<Map>> drained = [:]
  synchronized(pendingZgtNotifyByDni) {
    drained.putAll(pendingZgtNotifyByDni)
    pendingZgtNotifyByDni.clear()
  }
  drained.each { String dni, List<Map> reports ->
    reports.each { Map topo ->
      if(!(topo instanceof Map)) { return }
      try {
        if(parent != null) {
          parent.putZgtTopology(dni, topo)
        } else {
          logDebug("Standalone ZGT topology (no parent) dni=${dni} groups=${topo.size()}")
        }
      } catch(Exception e) {
        logWarn("parent ZGT notification failed for ${dni}: ${e.message}")
        try { logDebug("ZGT notify failure detail: ${e}") } catch(Exception ignored) { }
      }
    }
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
      Map expected = (Map)getSharedJsonSlurper().parseText(expectedJson)
      Long expiresAt = expected.expiresAt as Long
      if(expiresAt != null && expiresAt > (getHubitatCurrentTime() as Long)) {
        scheduleTopologyTimeoutFlush()
        return
      }
    } catch(Exception ignored) { }
  }
  setDeviceDataValue('expectedGroupTopology', '')
}

Boolean shouldHoldTopologyUpdate(Map topology) {
  return evaluateExpectedTopology(topology, true)
}

void recordExpectedTopologyObservation(Map topology) {
  evaluateExpectedTopology(topology, false)
}

Boolean evaluateExpectedTopology(Map topology, Boolean holdOnMismatch) {
  String expectedJson = getDeviceDataValue('expectedGroupTopology')
  if(!expectedJson) { return false }
  Map expected
  try {
    expected = (Map)getSharedJsonSlurper().parseText(expectedJson)
  } catch(Exception e) {
    setDeviceDataValue('expectedGroupTopology', '')
    return false
  }
  Long expiresAt = expected.expiresAt as Long
  if(expiresAt == null || expiresAt <= (getHubitatCurrentTime() as Long)) {
    setDeviceDataValue('expectedGroupTopology', '')
    return false
  }
  if(topology != null && topology.coordinatorId == expected.coordinatorId &&
     topology.memberIds == expected.memberIds) {
    setDeviceDataValue('expectedGroupTopology', '')
    logDebug('Observed expected topology for this player; releasing the update')
    return false
  }
  return holdOnMismatch == true
}

void scheduleTopologyTimeoutFlush() {
  String expectedJson = getDeviceDataValue('expectedGroupTopology')
  if(!expectedJson) { return }
  try {
    Map expected = (Map)getSharedJsonSlurper().parseText(expectedJson)
    Long expiresAt = expected.expiresAt as Long
    Integer remaining = Math.max(1, Math.ceil((expiresAt - (getHubitatCurrentTime() as Long)) / 1000.0d) as Integer)
    runIn(remaining, 'flushPendingTopologyUpdates', [overwrite: true])
  } catch(Exception e) {
    runIn(1, 'flushPendingTopologyUpdates', [overwrite: true])
  }
}

Map<String, List<String>> parseChannelMap(String raw) {
  Map<String, List<String>> out = [:]
  if(!raw) { return out }
  raw.split(';').each { String pair ->
    if(!pair.contains(':')) { return }
    List<String> ab = pair.split(':', 2)
    String rid = ab[0].trim()
    if(!rid) { return }
    out[rid] = ab[1].split(',').collect { String c -> c.trim() }.findAll { String c -> c }
  }
  return out
}

@CompileStatic
Map parseZgtFullSnapshot(String xmlString) {
  if(!isPlausibleXmlPayload(xmlString)) { return null }
  try {
    XmlSlurper parser = new XmlSlurper()
    GPathResult propertyset = parser.parseText(xmlString)
    String stateXml = ((GPathResult)propertyset['property']['ZoneGroupState']).text()
    if(!isPlausibleXmlPayload(stateXml)) { return null }
    GPathResult stateXmlParsed = parser.parseText(stateXml)
    GPathResult wrapped = (GPathResult)stateXmlParsed['ZoneGroups']
    GPathResult zoneGroups = wrapped.size() > 0 ? wrapped : stateXmlParsed
    String outerName = ((GPathResult)propertyset['property']['ZoneGroupName']).text().toString()
    List<Map> groups = []
    for(GPathResult group : (GPathResult)zoneGroups.children()) {
      String coordId = group['@Coordinator'].toString()
      String gid = group['@ID'].toString() ?: coordId
      List<Map> members = []
      List<Map> secondariesFlat = []
      for(GPathResult member : (GPathResult)group.children()) {
        String memberId = member['@UUID'].toString()
        String memberName = member['@ZoneName'].toString()
        String invisible = member['@Invisible'].toString()
        List<Map> secs = []
        for(GPathResult sat : (GPathResult)member.children()) {
          if(sat.name().toString() != 'Satellite') { continue }
          secs << [ playerId: sat['@UUID'].toString(), zoneName: sat['@ZoneName'].toString(),
            invisible: sat['@Invisible'].toString() == '1',
            location: sat['@Location'].toString(), configuration: sat['@Configuration'].toString(),
            swGen: sat['@SWGen'].toString(), channelMap: parseChannelMap(sat['@ChannelMapSet'].toString()),
            channelMapSet: sat['@ChannelMapSet'].toString(), htSatChannelMapSet: sat['@HTSatChanMapSet'].toString() ]
          secondariesFlat.addAll(secs)
        }
        members << [ playerId: memberId, zoneName: memberName, invisible: invisible == '1',
          location: member['@Location'].toString(), configuration: member['@Configuration'].toString(),
          swGen: member['@SWGen'].toString(), bootSeq: member['@BootSeq'].toString(),
          channelMap: parseChannelMap(member['@ChannelMapSet'].toString()),
          channelMapSet: member['@ChannelMapSet'].toString(),
          htSatChannelMapSet: member['@HTSatChanMapSet'].toString(),
          moreInfo: member['@MoreInfo'].toString(), secondaries: secs ]
      }
      String coordinatorName = members.find { Map it -> it.playerId == coordId }?.zoneName
      String gname = group['@ZoneGroupName'].toString() ?: (outerName && members.any { Map it -> it.playerId } ? outerName : coordinatorName)
      if(!gname) { gname = coordinatorName }
      groups << [ groupId: gid, coordinatorId: coordId, coordinatorName: coordinatorName, groupName: gname, members: members ]
    }
    List<Map> vanished = []
    try {
      GPathResult vanEl = (GPathResult)stateXmlParsed['VanishedDevices']
      if(vanEl.size() > 0) {
        for(Object childObject : vanEl.children()) {
          GPathResult child = (GPathResult)childObject
          Map vanishedDevice = new LinkedHashMap()
          vanishedDevice.put('uuid', child['@UUID'].toString())
          vanishedDevice.put('zoneName', child['@ZoneName'].toString())
          vanishedDevice.put('lastKnownIp', child['@LastKnownIP'].toString())
          vanishedDevice.put('mac', child['@Mac'].toString())
          vanishedDevice.put('modelInfo', child['@ModelInfo'].toString())
          vanishedDevice.put('reason', child['@Reason'].toString())
          vanishedDevice.put('lastSeenUtc', child['@LastSeenUTC'].toString())
          vanished.add(vanishedDevice)
        }
      }
    } catch(Exception ignored) { vanished.clear() }
    return [observedAt: hubNowInstant().toEpochMilli(), groups: groups, vanishedDevices: vanished]
  } catch(Exception e) {
    logDebug("Could not inspect ZGT topology: ${e.message}")
    return null
  }
}

// App owns the ZGT store; reads go through the parent getters, not a driver copy.

Map<String, Object> parseAvtMetaData(String rawDidl) {
  if(!rawDidl) { return [:] }
  String candidate = rawDidl.trim()
  if(!isPlausibleXmlPayload(candidate)) {
    try { candidate = unEscapeMetaData(rawDidl) } catch(Exception ignored) { return [:] }
  }
  if(!isPlausibleXmlPayload(candidate)) { return [:] }
  try {
    GPathResult meta = new XmlSlurper().parseText(candidate)
    // Sonos DIDL-Lite uses a default namespace for <item> and prefixes fields
    // such as dc:title, dc:creator, and upnp:album. Unqualified GPath lookups
    // miss those elements, so inspect only the direct item children by local
    // name rather than recursively walking the DIDL tree.
    GPathResult item = findDirectXmlChild(meta, 'item')
    if(item == null) { return [:] }
    Map<String, String> streamInfo = [:]
    String si = findDirectXmlChild(item, 'streamInfo')?.text()?.toString()
    if(si) { si.split(',').each { String kv -> if(kv.contains(':')) { def ab = kv.split(':', 2); streamInfo[ab[0].trim()] = ab[1].trim() } } }
    String albumArt = findDirectXmlChild(item, 'albumArtURI')?.text()?.toString() ?: ''
    while(albumArt?.contains('&amp;')) { albumArt = StringEscapeUtils.unescapeXml(albumArt) }
    String creator = findDirectXmlChild(item, 'creator')?.text()?.toString()
    if(!creator) { creator = findDirectXmlChild(item, 'artist')?.text()?.toString() }
    return [
      id: xmlAttribute(item, 'id'),
      parentID: xmlAttribute(item, 'parentID'),
      title: findDirectXmlChild(item, 'title')?.text()?.toString(),
      creator: creator,
      album: findDirectXmlChild(item, 'album')?.text()?.toString(),
      albumArtURI: albumArt,
      class: findDirectXmlChild(item, 'class')?.text()?.toString(),
      res: findDirectXmlChild(item, 'res')?.text()?.toString(),
      resMD: findDirectXmlChild(item, 'resMD')?.text()?.toString(),
      favoriteType: findDirectXmlChild(item, 'type')?.text()?.toString(),
      streamInfo: streamInfo,
      streamContent: findDirectXmlChild(item, 'streamContent')?.text()?.toString(),
      description: findDirectXmlChild(item, 'description')?.text()?.toString()
    ]
  } catch(Exception e) {
    logDebug("Could not parse AVT DIDL: ${e.message}")
    return [:]
  }
}

Map parseAvtFullSnapshot(String xmlString) {
  if(!isPlausibleXmlPayload(xmlString)) { return null }
  try {
    XmlSlurper parser = new XmlSlurper()
    GPathResult propertyset = parser.parseText(xmlString)
    String lastChange = ((GPathResult)propertyset['property']['LastChange']).text().toString()
    if(!lastChange) { return null }
    if(!isPlausibleXmlPayload(lastChange)) { return null }
    GPathResult event = parser.parseText(lastChange)
    GPathResult iid = (GPathResult)event['InstanceID']
    if(iid.size() == 0) { return null }
    def normalizeUri = { Object v -> v != null && v.toString() != '' ? StringEscapeUtils.unescapeXml(v.toString()) : null }
    String curUriRaw = iid['CurrentTrackURI']['@val']?.toString()
    return [
      hasTransportState: iid['TransportState'].size() > 0,
      transportState: iid['TransportState']['@val']?.toString(),
      hasPlayMode: iid['CurrentPlayMode'].size() > 0,
      playMode: iid['CurrentPlayMode']['@val']?.toString(),
      hasCrossfadeMode: iid['CurrentCrossfadeMode'].size() > 0,
      crossfadeMode: iid['CurrentCrossfadeMode']['@val']?.toString(),
      numberOfTracks: iid['NumberOfTracks']['@val']?.toString(),
      hasTrack: iid['CurrentTrack'].size() > 0,
      track: iid['CurrentTrack']['@val']?.toString(),
      section: iid['CurrentSection']['@val']?.toString(),
      hasCurrentTrackURI: iid['CurrentTrackURI'].size() > 0,
      currentTrackURI: normalizeUri(curUriRaw),
      hasCurrentTrackDuration: iid['CurrentTrackDuration'].size() > 0,
      currentTrackDuration: iid['CurrentTrackDuration']['@val']?.toString(),
      hasCurrentTrackMetaData: iid['CurrentTrackMetaData'].size() > 0,
      currentTrackMetaDataRaw: iid['CurrentTrackMetaData']['@val']?.toString(),
      currentTrackMetaData: parseAvtMetaData(iid['CurrentTrackMetaData']['@val']?.toString()),
      nextTrackURI: iid['NextTrackURI']['@val']?.toString(),
      nextTrackMetaData: parseAvtMetaData(iid['NextTrackMetaData']['@val']?.toString()),
      enqueuedTransportURI: normalizeUri(iid['EnqueuedTransportURI']['@val']?.toString()),
      enqueuedTransportURIMetaData: parseAvtMetaData(iid['EnqueuedTransportURIMetaData']['@val']?.toString()),
      avTransportURI: iid['AVTransportURI']['@val']?.toString(),
      avTransportURIMetaDataRaw: iid['AVTransportURIMetaData']['@val']?.toString(),
      playbackStorageMedium: iid['PlaybackStorageMedium']['@val']?.toString(),
      currentTransportActions: iid['CurrentTransportActions']['@val']?.toString(),
      currentValidPlayModes: iid['r:CurrentValidPlayModes']['@val']?.toString()?.split(',')?.collect { it.trim() }?.findAll { it } ?: [],
      directControlClientID: iid['r:DirectControlClientID']['@val']?.toString(),
      directControlIsSuspended: iid['r:DirectControlIsSuspended']['@val']?.toString(),
      directControlAccountID: iid['r:DirectControlAccountID']['@val']?.toString(),
      transportStatus: iid['TransportStatus']['@val']?.toString(),
      sleepTimerGeneration: iid['SleepTimerGeneration']['@val']?.toString(),
      sleepTimerState: iid['SleepTimerState']['@val']?.toString(),
      alarmRunning: iid['AlarmRunning']['@val']?.toString(),
      snoozeRunning: iid['SnoozeRunning']['@val']?.toString(),
      restartPending: iid['RestartPending']['@val']?.toString(),
      transportPlaySpeed: iid['TransportPlaySpeed']['@val']?.toString(),
      currentMediaDuration: iid['CurrentMediaDuration']['@val']?.toString(),
      avTransportURIMetaData: parseAvtMetaData(iid['AVTransportURIMetaData']['@val']?.toString()),
      observedAt: getHubitatCurrentTime()
    ]
  } catch(Exception e) {
    logDebug("Could not parse AVT snapshot: ${e.message}")
    return null
  }
}

String unescapeIfNeeded(String s) {
  if(s == null) return s
  if(s.contains('&lt;') || s.contains('&quot;')) {
    return StringEscapeUtils.unescapeXml(s)
  }
  return s
}

// App owns the AVT store; reads go through the parent getters, not a driver copy.
void onAvtUpdated() {
  // Follower refresh now replays from the app store in the later fanout commit.
}

void deferOrProcessAvTransportPayload(String dni, String xmlBody) {
  deferOrProcessAvTransportPayload(dni, xmlBody, null)
}

void deferOrProcessAvTransportPayload(String dni, String xmlBody, Integer incomingSeq) {
  handleAvtFull(dni, xmlBody, incomingSeq)
}

void handleAvtFull(String dni, String xmlBody, Integer incomingSeq) {
  String deviceDni = dni ?: device.getDeviceNetworkId()
  if(!deviceDni || !xmlBody) { return }
  if(!isPlausibleXmlPayload(xmlBody)) { return }
  if(!tryAcceptSeq('AVT', currentAvtSeqByDni, deviceDni, incomingSeq)) { return }
  Map newSnapshot = parseAvtFullSnapshot(xmlBody)
  if(newSnapshot == null) { return }
  if(incomingSeq != null) {
    AtomicInteger ref2 = currentAvtSeqByDni.get(deviceDni)
    if(ref2 != null && incomingSeq < ref2.get()) {
      logDebug("Ignoring AVT that became stale during parse SEQ=${incomingSeq} < prev=${ref2.get()} for ${deviceDni}")
      return
    }
    if(ref2 != null) { ref2.set(incomingSeq) } else { currentAvtSeqByDni.put(deviceDni, new AtomicInteger(incomingSeq)) }
  }
  String myRincon = getId()
  String coordinatorRincon = resolveCurrentCoordinatorRinconForAvt(deviceDni) ?: myRincon
  if(!(myRincon && coordinatorRincon == myRincon)) { return }
  // Capture previous before local apply replaces it, so track-change detection stays exact.
  Map previous = runtimeState.lastTransportPlaybackSnapshot instanceof Map
      ? (Map)runtimeState.lastTransportPlaybackSnapshot : [:]
  applyAvtSnapshotIfDifferent(newSnapshot)
  List<Map> events = buildAvtEvents(newSnapshot, previous)
  if(!events.isEmpty()) {
    queueCoordinatorAvtParentUpdate(deviceDni, [rincon: coordinatorRincon,
      events: events, seq: incomingSeq])
  }
}

// Stored as sendEvent-ready attribute maps for parent-app delivery.
List<Map> buildAvtEvents(Map incoming, Map previous) {
  List<Map> events = []
  String status = normalizeTransportStatus(incoming.transportState?.toString())
  if(incoming.hasTransportState == true && status) {
    events << [name: 'status', value: status]
    events << [name: 'transportStatus', value: status]
  }
  Boolean trackUriChanged = incoming.hasCurrentTrackURI == true &&
      incoming.currentTrackURI?.toString() != (previous?.currentTrackURI?.toString())
  Boolean trackCleared = incoming.hasCurrentTrackURI == true && !incoming.currentTrackURI?.toString()
  Boolean metadataPresent = incoming.hasCurrentTrackMetaData == true
  Boolean metadataEmpty = metadataPresent && !incoming.currentTrackMetaDataRaw?.toString()
  Map metadata = incoming.currentTrackMetaData instanceof Map ? (Map)incoming.currentTrackMetaData : [:]
  Boolean clearMetadata = trackCleared || metadataEmpty ||
      (trackUriChanged && (!metadataPresent || metadata.isEmpty()))
  if(clearMetadata) {
    events << [name: 'currentTrackName', value: 'Not Available']
    events << [name: 'currentArtistName', value: 'Not Available']
    events << [name: 'currentAlbumName', value: 'Not Available']
    events << [name: 'albumArtURI', value: 'Not Available']
    events << [name: 'trackDescription', value: 'Not Available']
  } else if(metadataPresent && !metadata.isEmpty()) {
    String title = metadata.title?.toString() ?: 'Not Available'
    String artist = metadata.creator?.toString() ?: 'Not Available'
    events << [name: 'currentTrackName', value: title]
    events << [name: 'currentArtistName', value: artist]
    events << [name: 'currentAlbumName', value: metadata.album?.toString() ?: 'Not Available']
    events << [name: 'trackDescription', value: metadata.title && metadata.creator
        ? "${metadata.title} by ${metadata.creator}" : (metadata.streamContent?.toString() ?: title)]
  }
  if(incoming.hasTrack == true) {
    String track = incoming.track?.toString()
    if(track?.isInteger()) { events << [name: 'currentTrackNumber', value: Integer.parseInt(track)] }
    else { events << [name: 'currentTrackNumber', value: 0] }
  } else if(trackCleared) {
    events << [name: 'currentTrackNumber', value: 0]
  }
  if(incoming.hasCurrentTrackDuration == true || (clearMetadata && (trackCleared || trackUriChanged))) {
    events << [name: 'currentTrackDuration', value: incoming.currentTrackDuration?.toString() ?: '']
  }
  // Play-mode reaches the store as its three device attributes, matching setPlayMode.
  if(incoming.hasPlayMode == true && incoming.playMode) {
    String repeatOne = null
    String repeatAll = null
    String shuffle = null
    switch(incoming.playMode?.toString()) {
      case 'NORMAL': repeatOne = 'off'; repeatAll = 'off'; shuffle = 'off'; break
      case 'REPEAT_ALL': repeatOne = 'off'; repeatAll = 'on'; shuffle = 'off'; break
      case 'REPEAT_ONE': repeatOne = 'on'; repeatAll = 'off'; shuffle = 'off'; break
      case 'SHUFFLE_NOREPEAT': repeatOne = 'off'; repeatAll = 'off'; shuffle = 'on'; break
      case 'SHUFFLE': repeatOne = 'off'; repeatAll = 'on'; shuffle = 'on'; break
      case 'SHUFFLE_REPEAT_ONE': repeatOne = 'on'; repeatAll = 'off'; shuffle = 'on'; break
    }
    if(repeatOne != null) {
      events << [name: 'currentRepeatOneMode', value: repeatOne]
      events << [name: 'currentRepeatAllMode', value: repeatAll]
      events << [name: 'currentShuffleMode', value: shuffle]
    }
  }
  if(incoming.hasCrossfadeMode == true && incoming.crossfadeMode?.toString()?.trim()) {
    events << [name: 'currentCrossfadeMode', value: (incoming.crossfadeMode.toString().trim() == '1' ? 'on' : 'off')]
  }
  if(!clearMetadata && metadataPresent && !metadata.isEmpty() && metadata.albumArtURI?.toString()) {
    events << [name: 'albumArtURI', value: metadata.albumArtURI.toString()]
  }
  return events
}

Object getCoordinatorAvtUpdateLock(String dni) {
  return coordinatorAvtUpdateStripes.get(dni)
}

// Transient in-flight hand-off only, not storage: deltas merge by name until the flush claims them.
// Deferred so parse() returns before the app gate runs; SEQ prevents an older delta reordering a newer one.
void queueCoordinatorAvtParentUpdate(String dni, Map update) {
  if(!dni || update == null || parent == null) { return }
  Object lock = getCoordinatorAvtUpdateLock(dni)
  synchronized(lock) {
    Map pending = pendingCoordinatorAvtUpdatesByDni.get(dni)
    Integer incomingSeq = update.seq as Integer
    Integer pendingSeq = pending?.seq as Integer
    if(pending != null && incomingSeq != null && pendingSeq != null && incomingSeq < pendingSeq) { return }
    Map<String, Object> merged = [:]
    if(pending != null) { merged.putAll(pending) }
    merged.putAll(update)
    List<Map> mergedEvents = []
    // A coordinator change starts a fresh list so one group's playback never merges into another's.
    if(pending?.events instanceof List && pending?.rincon?.toString() == update.rincon?.toString()) {
      mergedEvents.addAll((List<Map>)pending.events)
    }
    if(update.events instanceof List) {
      ((List<Map>)update.events).each { Map incomingEvent ->
        String incomingName = incomingEvent?.name?.toString()
        if(incomingName) {
          mergedEvents.removeAll { Map existing -> existing?.name?.toString() == incomingName }
        }
        mergedEvents << new LinkedHashMap(incomingEvent)
      }
    }
    merged.events = mergedEvents
    pendingCoordinatorAvtUpdatesByDni.put(dni, merged)
    if(coordinatorAvtUpdateScheduledDnis.add(dni)) {
      try { runInMillis(1, 'flushCoordinatorAvtParentUpdate', [overwrite: true]) }
      catch(Exception e) {
        coordinatorAvtUpdateScheduledDnis.remove(dni)
        runIn(1, 'flushCoordinatorAvtParentUpdate', [overwrite: true])
      }
    }
  }
}

// One contract: the flush calls the pure parent setter directly (no fallback chain).
void flushCoordinatorAvtParentUpdate() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Object lock = getCoordinatorAvtUpdateLock(dni)
  Map update
  synchronized(lock) {
    update = pendingCoordinatorAvtUpdatesByDni.remove(dni)
    coordinatorAvtUpdateScheduledDnis.remove(dni)
  }
  if(update == null) { return }
  String rincon = update.rincon?.toString()
  List<Map> events = update.events instanceof List ? (List<Map>)update.events : []
  if(!rincon || events.isEmpty()) { return }
  try {
    parent.putAvtEvents(rincon, events)
  } catch(Exception e) {
    logWarn("Deferred parent AVT update failed for ${dni}: ${e.message}")
    synchronized(lock) {
      Map newer = pendingCoordinatorAvtUpdatesByDni.get(dni)
      Integer updateSeq = update.seq as Integer
      Integer newerSeq = newer?.seq as Integer
      if(newer == null || updateSeq == null || newerSeq == null || updateSeq > newerSeq) {
        pendingCoordinatorAvtUpdatesByDni.put(dni, update)
      }
      if(coordinatorAvtUpdateScheduledDnis.add(dni)) {
        runIn(1, 'flushCoordinatorAvtParentUpdate', [overwrite: true])
      }
    }
  }
}

// Coordinator key comes from device data, not a stored topology; the app re-keys if it moved.
String resolveCurrentCoordinatorRinconForAvt(String dni) {
  String myRincon = getId()
  String cur = device.getDataValue('groupCoordinatorId')?.toString()
  return cur ?: myRincon
}

Boolean applyAvtSnapshotIfDifferent(Map snap) {
  logDebug("applyAvtSnapshotIfDifferent called: snapshot=${snap ? snap.transportState : 'null'}")
  if(snap == null) { return false }
  observeUpnpPlaybackSource(snap)
  // Diffs are against device.currentValue below (set* dedupes); the app holds the snapshot.
  // Discard transient TRANSITIONING like legacy process (preserve last valid)
  if(snap.transportState?.toString()?.trim()?.equalsIgnoreCase('TRANSITIONING')) {
    return false
  }
  // Apply minimal presentation fields from snapshot (status/track)
  // Sleep timer (still presentation — parse is pure, apply may fan to child)
  try {
    String sleepTimerState = snap.sleepTimerState?.toString()
    String sleepTimerGeneration = snap.sleepTimerGeneration?.toString()
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
  } catch(Exception ignored) {}
  try {
    Map previousPlayback = runtimeState.lastTransportPlaybackSnapshot instanceof Map
        ? (Map)runtimeState.lastTransportPlaybackSnapshot : [:]
    Boolean trackUriPresent = snap.hasCurrentTrackURI == true
    Boolean metadataPresent = snap.hasCurrentTrackMetaData == true
    String trackUri = snap.currentTrackURI?.toString()
    String rawMetadata = snap.currentTrackMetaDataRaw?.toString()
    Boolean trackChanged = trackUriPresent && trackUri != previousPlayback.currentTrackURI?.toString()
    Boolean trackCleared = trackUriPresent && !trackUri
    Boolean metadataCleared = metadataPresent && !rawMetadata
    Map meta = snap.currentTrackMetaData instanceof Map ? (Map)snap.currentTrackMetaData : [:]
    Boolean metadataParsed = !meta.isEmpty()
    Boolean clearTrackMetadata = trackCleared || metadataCleared ||
        (trackChanged && (!metadataPresent || !metadataParsed))

    String ts = snap.transportState?.toString()
    if(ts) { setStatusTransportStatus(ts, 'AVT event') }
    String pm = snap.playMode?.toString()
    if(pm) { setPlayMode(pm) }
    if(snap.hasCurrentTrackDuration == true) {
      setCurrentTrackDuration(snap.currentTrackDuration?.toString() ?: '')
    } else if(trackChanged || trackCleared) {
      setCurrentTrackDuration('')
    }
    String cf = snap.crossfadeMode?.toString()?.trim()
    if(cf) { setCrossfadeMode(cf == '1' ? 'on' : 'off') }
    if(clearTrackMetadata) {
      clearCurrentAvtTrackMetadata()
    } else if(metadataPresent && metadataParsed) {
      String trackName = meta.title?.toString() ?: 'Not Available'
      String artist = meta.creator?.toString() ?: 'Not Available'
      String album = meta.album?.toString() ?: 'Not Available'
      setCurrentTrackName(trackName)
      setCurrentArtistName(artist)
      setCurrentAlbumName(album)
      String desc = meta.title && meta.creator
          ? "${meta.title} by ${meta.creator}"
          : (meta.streamContent?.toString() ?: trackName)
      setTrackDescription(desc)
    }
    updateCurrentFavoriteFromUpnp(snap)
    String num = snap.track?.toString()
    if(num?.isInteger()) { setCurrentTrackNumber(Integer.parseInt(num)) }
    else if(snap.containsKey('track') || trackCleared) { setCurrentTrackNumber(0) }
  } catch(Exception e) { logDebug("applyAvtSnapshotIfDifferent failed: ${e.message}") }
  runtimeState.lastTransportPlaybackSnapshot = new LinkedHashMap(snap)
  return true
}

String normalizeFavoriteUri(String uri) {
  if(!uri) { return null }
  String normalized = StringEscapeUtils.unescapeXml(uri.trim())
  if(normalized.contains('%')) {
    try { normalized = URLDecoder.decode(normalized, 'UTF-8') } catch(Exception ignored) { }
  }
  Integer query = normalized.indexOf('?')
  if(query >= 0) { normalized = normalized.substring(0, query) }
  normalized = normalized.replaceFirst(/(?i)\.mp4$/, '')
  try { new URI(normalized) } catch(Exception ignored) { }
  return normalized
}

void updateCurrentFavoriteFromUpnp(Map snapshot) {
  if(getFavoritesChild() != null && getFavoritesMap().isEmpty() && !getDriverStateValue('favoritesBrowseRequestId')) {
    getFavorites()
  }
  if(getPlaylistChild() != null && getPlaylistsMap().isEmpty() && !getDriverStateValue('playlistsBrowseRequestId')) {
    getPlaylists()
  }
  String currentUri = normalizeFavoriteUri(snapshot?.currentTrackURI?.toString())
  String enqueuedUri = normalizeFavoriteUri(snapshot?.enqueuedTransportURI?.toString())
  Map currentMetadata = snapshot?.currentTrackMetaData instanceof Map ? (Map)snapshot.currentTrackMetaData : [:]
  Map enqueuedMetadata = snapshot?.enqueuedTransportURIMetaData instanceof Map
      ? (Map)snapshot.enqueuedTransportURIMetaData : [:]
  String currentResourceId = normalizeFavoriteResourceId(currentMetadata.id?.toString())
  String enqueuedResourceId = normalizeFavoriteResourceId(enqueuedMetadata.id?.toString())
  Map matched = null
  for(Map favorite : getFavoritesMap().values()) {
    String favoriteUri = normalizeFavoriteUri(favorite?.upnpUri?.toString())
    String resourceId = normalizeFavoriteResourceId(favorite?.resourceObjectId?.toString())
    if((favoriteUri && (favoriteUri == currentUri || favoriteUri == enqueuedUri)) ||
        (resourceId && (resourceId == currentResourceId || resourceId == enqueuedResourceId))) {
      matched = favorite
      break
    }
  }
  String favoriteId = matched?.id?.toString()
  String favoriteName = matched?.name?.toString()
  String imageUrl = matched?.imageUrl?.toString()
  Boolean isFavorite = matched != null
  setCurrentFavorite(imageUrl, favoriteId, favoriteName, isFavorite)

  Map matchedPlaylist = null
  for(Map playlist : getPlaylistsMap().values()) {
    String playlistUri = normalizeFavoriteUri(playlist?.upnpUri?.toString())
    String playlistObjectId = normalizeFavoriteResourceId(playlist?.objectId?.toString())
    if((playlistUri && (playlistUri == currentUri || playlistUri == enqueuedUri)) ||
        (playlistObjectId && (playlistObjectId == currentResourceId || playlistObjectId == enqueuedResourceId))) {
      matchedPlaylist = playlist
      break
    }
  }
  String playlistId = matchedPlaylist?.id?.toString()
  Boolean isPlaylist = matchedPlaylist != null
  setCurrentPlaylist(playlistId, matchedPlaylist?.name?.toString(), isPlaylist)

  Map retryState = favoriteRetryState.get(device.getDeviceNetworkId())
  if(retryState != null && favoriteId) {
    retryState.metadataConfirmed = favoriteId == retryState.favoriteId?.toString()
  }
  String operationId = runtimeState.groupFavoriteOperationId?.toString()
  String expectedFavoriteId = runtimeState.groupFavoriteOperationFavoriteId?.toString()
  if(operationId && expectedFavoriteId && getIsGroupCoordinator() == true) {
    emitGroupFavoriteOperationEvent('metadataConfirmed', [favoriteId: favoriteId,
      confirmed: isFavorite && favoriteId == expectedFavoriteId,
      groupId: runtimeState.groupFavoriteOperationGroupId as String])
  }
  Map playlistRetry = playlistRetryState.get(device.getDeviceNetworkId())
  if(playlistRetry != null && playlistId) {
    playlistRetry.metadataConfirmed = playlistId == playlistRetry.playlistId?.toString()
  }
}

String normalizeFavoriteResourceId(String resourceId) {
  if(resourceId == null) { return null }
  String normalized = StringEscapeUtils.unescapeXml(resourceId.trim())
  if(normalized.contains('%')) {
    try { normalized = URLDecoder.decode(normalized, 'UTF-8') } catch(Exception ignored) { }
  }
  return normalized
}

void clearCurrentAvtTrackMetadata() {
  setCurrentTrackName('Not Available')
  setCurrentArtistName('Not Available')
  setCurrentAlbumName('Not Available')
  setTrackDescription('Not Available')
}

@CompileStatic
Map parseGrcFullSnapshot(String xmlString) {
  if(!isPlausibleXmlPayload(xmlString)) { return null }
  try {
    XmlSlurper parser = new XmlSlurper()
    GPathResult propertyset = parser.parseText(xmlString)
    String volumeText = ((GPathResult)propertyset['property']['GroupVolume']).text()?.toString()?.trim()
    String muteText = ((GPathResult)propertyset['property']['GroupMute']).text()?.toString()?.trim()
    String changeableText = ((GPathResult)propertyset['property']['GroupVolumeChangeable']).text()?.toString()?.trim()
    Integer groupVolume = null
    if(volumeText) {
      try {
        Integer parsed = Integer.parseInt(volumeText)
        if(parsed >= 0 && parsed <= 100) { groupVolume = parsed }
        else { logDebug("Ignoring out-of-range GRC GroupVolume value=${volumeText}") }
      } catch(Exception ignored) { logDebug("Ignoring non-numeric GRC GroupVolume value=${volumeText}") }
    }
    String groupMute = null
    if(muteText) {
      if(muteText == '1') { groupMute = 'muted' }
      else if(muteText == '0') { groupMute = 'unmuted' }
      else { logDebug("Ignoring unexpected GRC GroupMute value=${muteText}") }
    }
    Boolean changeable = null
    if(changeableText) {
      if(changeableText == '1') { changeable = true }
      else if(changeableText == '0') { changeable = false }
      else { logDebug("Ignoring unexpected GRC GroupVolumeChangeable value=${changeableText}") }
    }
    if(groupVolume == null && groupMute == null && changeable == null) { return null }
    return [groupVolume: groupVolume, groupMute: groupMute, groupVolumeChangeable: changeable, observedAt: hubNowInstant().toEpochMilli()]
  } catch(Exception e) {
    logDebug("Could not parse GRC snapshot: ${e.message}")
    return null
  }
}

// App owns the GRC store; reads go through the parent getters, not a driver copy.

void deferOrProcessGroupRenderingControlPayload(String dni, String xmlBody) {
  deferOrProcessGroupRenderingControlPayload(dni, xmlBody, null, null)
}

void deferOrProcessGroupRenderingControlPayload(String dni, String xmlBody, Integer incomingSeq) {
  deferOrProcessGroupRenderingControlPayload(dni, xmlBody, incomingSeq, null)
}

void deferOrProcessGroupRenderingControlPayload(String dni, String xmlBody, Integer incomingSeq, String incomingSid) {
  handleGrcFull(dni, xmlBody, incomingSeq, incomingSid)
}

void handleGrcFull(String dni, String xmlBody, Integer incomingSeq, String incomingSid) {
  String deviceDni = dni ?: device.getDeviceNetworkId()
  if(!deviceDni || !xmlBody) { return }
  if(!isPlausibleXmlPayload(xmlBody)) { return }
  String sidKey = incomingSid?.toString()?.trim() ?: null
  if(sidKey == '') { sidKey = null }
  String storedSid = currentGrcSidByDni.get(deviceDni)
  Boolean sidChanged = sidKey != null && storedSid != null && storedSid != sidKey
  Boolean isReset = (incomingSeq != null && incomingSeq == 0) || sidChanged
  if(incomingSeq != null) {
    if(isReset) {
      AtomicInteger ref = currentGrcSeqByDni.get(deviceDni)
      if(ref == null) {
        AtomicInteger created = new AtomicInteger(incomingSeq)
        currentGrcSeqByDni.putIfAbsent(deviceDni, created)
        ref = currentGrcSeqByDni.get(deviceDni)
      }
      ref.set(incomingSeq)
    } else if(!tryAcceptSeq('GRC', currentGrcSeqByDni, deviceDni, incomingSeq)) { return }
  }
  Map newSnapshot = parseGrcFullSnapshot(xmlBody)
  if(newSnapshot == null) { return }
  if(incomingSeq != null && !isReset) {
    AtomicInteger ref2 = currentGrcSeqByDni.get(deviceDni)
    if(ref2 != null && incomingSeq < ref2.get()) {
      logDebug("Ignoring GRC that became stale during parse SEQ=${incomingSeq} < prev=${ref2.get()} for ${deviceDni}")
      return
    }
  }
  if(incomingSeq != null) {
    AtomicInteger ref2 = currentGrcSeqByDni.get(deviceDni)
    if(ref2 != null) { ref2.set(incomingSeq) } else { currentGrcSeqByDni.put(deviceDni, new AtomicInteger(incomingSeq)) }
  }
  if(sidKey != null) { currentGrcSidByDni[deviceDni] = sidKey }
  String coordRincon = resolveCurrentCoordinatorRinconForAvt(deviceDni)
  applyGrcSnapshotIfDifferent(newSnapshot)
  List<Map> events = buildGrcEvents(newSnapshot)
  if(coordRincon && !events.isEmpty()) {
    queueGrcParentUpdate(deviceDni, [rincon: coordRincon, events: events, seq: incomingSeq, reset: isReset])
  }
}

Boolean applyGrcSnapshotIfDifferent(Map snap) {
  if(snap == null) { return false }
  Integer newVolume = snap.groupVolume as Integer
  Integer priorVolume = getGroupVolumeState()
  String newMute = snap.groupMute?.toString()
  String priorMute = getGroupMuteState()
  Boolean volumeChanged = newVolume != null && newVolume != priorVolume
  Boolean muteChanged = newMute != null && newMute != priorMute
  if(!volumeChanged && !muteChanged) { return false }
  if(volumeChanged) {
    setGroupVolumeState(newVolume)
    queueGroupDeviceUpdate([_groupVolume: newVolume])
  }
  if(muteChanged) {
    setGroupMuteState(newMute)
    queueGroupDeviceUpdate([_groupMute: newMute])
  }
  return true
}

// Stored as sendEvent-ready attribute maps for parent-app delivery.
List<Map> buildGrcEvents(Map snap) {
  List<Map> events = []
  if(snap?.groupVolume != null) { events << [name: 'groupVolume', value: snap.groupVolume as Integer] }
  if(snap?.groupMute != null) { events << [name: 'groupMute', value: snap.groupMute?.toString()] }
  return events
}

Object getGrcUpdateLock(String dni) {
  return grcUpdateStripes.get(dni)
}

// Transient in-flight hand-off only, not storage: deltas merge by name until the flush claims them.
// Deferred so parse() returns before the app gate runs; SEQ prevents an older delta reordering a newer one.
void queueGrcParentUpdate(String dni, Map update) {
  if(!dni || update == null || parent == null) { return }
  Object lock = getGrcUpdateLock(dni)
  synchronized(lock) {
    Map pending = pendingGrcByDni.get(dni)
    Integer incomingSeq = update.seq as Integer
    Integer pendingSeq = pending?.seq as Integer
    // A SEQ-0/SID reset starts a new epoch; never drop it behind the old epoch's SEQ.
    if(pending != null && incomingSeq != null && pendingSeq != null && incomingSeq < pendingSeq && update.reset != true) { return }
    Map<String, Object> merged = [:]
    if(pending != null) { merged.putAll(pending) }
    merged.putAll(update)
    List<Map> mergedEvents = []
    if(pending?.events instanceof List && pending?.rincon?.toString() == update.rincon?.toString()) {
      mergedEvents.addAll((List<Map>)pending.events)
    }
    if(update.events instanceof List) {
      ((List<Map>)update.events).each { Map incomingEvent ->
        String incomingName = incomingEvent?.name?.toString()
        if(incomingName) {
          mergedEvents.removeAll { Map existing -> existing?.name?.toString() == incomingName }
        }
        mergedEvents << new LinkedHashMap(incomingEvent)
      }
    }
    merged.events = mergedEvents
    pendingGrcByDni.put(dni, merged)
    if(grcUpdateScheduledDnis.add(dni)) {
      try { runInMillis(1, 'flushPendingGrc', [overwrite: true]) }
      catch(Exception e) {
        grcUpdateScheduledDnis.remove(dni)
        runIn(1, 'flushPendingGrc', [overwrite: true])
      }
    }
  }
}

// One contract: the flush calls the pure parent setter directly (no fallback chain).
void flushPendingGrc() {
  String dni = device.getDeviceNetworkId()
  if(!dni) { return }
  Object lock = getGrcUpdateLock(dni)
  Map update
  synchronized(lock) {
    update = pendingGrcByDni.remove(dni)
    grcUpdateScheduledDnis.remove(dni)
  }
  if(update == null) { return }
  String rincon = update.rincon?.toString()
  List<Map> events = update.events instanceof List ? (List<Map>)update.events : []
  if(!rincon || events.isEmpty()) { return }
  try {
    parent.putGrcEvents(rincon, events)
  } catch(Exception e) {
    logWarn("Deferred parent GRC update failed for ${dni}: ${e.message}")
    synchronized(lock) {
      Map newer = pendingGrcByDni.get(dni)
      Integer updateSeq = update.seq as Integer
      Integer newerSeq = newer?.seq as Integer
      if(newer == null || updateSeq == null || newerSeq == null || updateSeq > newerSeq) {
        pendingGrcByDni.put(dni, update)
      }
      if(grcUpdateScheduledDnis.add(dni)) {
        runIn(1, 'flushPendingGrc', [overwrite: true])
      }
    }
  }
}

@CompileStatic
Map parseRcFullSnapshot(String xmlString) {
  if(!isPlausibleXmlPayload(xmlString)) { return null }
  try {
    XmlSlurper parser = new XmlSlurper()
    GPathResult propertyset = parser.parseText(xmlString)
    String lastChange = ((GPathResult)propertyset['property']['LastChange']).text()?.toString()
    if(!lastChange) { return null }
    if(!isPlausibleXmlPayload(lastChange)) { return null }
    GPathResult event = parser.parseText(lastChange)
    GPathResult iid = (GPathResult)event['InstanceID']
    if(iid.size() == 0) { return null }
    String volumeMasterText = ''
    String volumeLFText = ''
    String volumeRFText = ''
    String muteMasterText = ''
    String muteLFText = ''
    String muteRFText = ''
    String loudnessText = ''
    for(Object childObject : iid.children()) {
      GPathResult child = (GPathResult)childObject
      String elementName = child.name()?.toString()
      if(elementName == 'Volume' || elementName == 'Mute' || elementName == 'Loudness') {
        String channel = child['@channel']?.toString()
        String value = child['@val']?.toString()
        if(elementName == 'Volume') {
          if(channel == 'Master') { volumeMasterText = value }
          else if(channel == 'LF') { volumeLFText = value }
          else if(channel == 'RF') { volumeRFText = value }
        } else if(elementName == 'Mute') {
          if(channel == 'Master') { muteMasterText = value }
          else if(channel == 'LF') { muteLFText = value }
          else if(channel == 'RF') { muteRFText = value }
        } else if(elementName == 'Loudness' && channel == 'Master') {
          loudnessText = value
        }
      }
    }
    String bassText = iid['Bass']['@val']?.toString()
    String trebleText = iid['Treble']['@val']?.toString()
    String nightModeText = iid['NightMode']['@val']?.toString()
    String dialogLevelText = iid['DialogLevel']['@val']?.toString()
    Integer volumeMaster = null
    if(volumeMasterText) {
      try {
        Integer parsed = Integer.parseInt(volumeMasterText.trim())
        if(parsed >= 0 && parsed <= 100) { volumeMaster = parsed }
        else { logDebug("Ignoring out-of-range RC Volume Master value=${volumeMasterText}") }
      } catch(Exception ignored) { logDebug("Ignoring non-numeric RC Volume Master value=${volumeMasterText}") }
    }
    Integer volumeLF = null
    if(volumeLFText) {
      try {
        Integer parsed = Integer.parseInt(volumeLFText.trim())
        if(parsed >= 0 && parsed <= 100) { volumeLF = parsed }
        else { logDebug("Ignoring out-of-range RC Volume LF value=${volumeLFText}") }
      } catch(Exception ignored) { logDebug("Ignoring non-numeric RC Volume LF value=${volumeLFText}") }
    }
    Integer volumeRF = null
    if(volumeRFText) {
      try {
        Integer parsed = Integer.parseInt(volumeRFText.trim())
        if(parsed >= 0 && parsed <= 100) { volumeRF = parsed }
        else { logDebug("Ignoring out-of-range RC Volume RF value=${volumeRFText}") }
      } catch(Exception ignored) { logDebug("Ignoring non-numeric RC Volume RF value=${volumeRFText}") }
    }
    String muteMaster = null
    if(muteMasterText) {
      if(muteMasterText == '1') { muteMaster = 'muted' }
      else if(muteMasterText == '0') { muteMaster = 'unmuted' }
      else { logDebug("Ignoring unexpected RC Mute Master value=${muteMasterText}") }
    }
    String muteLF = null
    if(muteLFText) {
      if(muteLFText == '1') { muteLF = 'muted' }
      else if(muteLFText == '0') { muteLF = 'unmuted' }
      else { logDebug("Ignoring unexpected RC Mute LF value=${muteLFText}") }
    }
    String muteRF = null
    if(muteRFText) {
      if(muteRFText == '1') { muteRF = 'muted' }
      else if(muteRFText == '0') { muteRF = 'unmuted' }
      else { logDebug("Ignoring unexpected RC Mute RF value=${muteRFText}") }
    }
    Integer bass = null
    if(bassText) {
      try { bass = Integer.parseInt(bassText.trim()) }
      catch(Exception ignored) { logDebug("Ignoring non-numeric RC Bass value=${bassText}") }
    }
    Integer treble = null
    if(trebleText) {
      try { treble = Integer.parseInt(trebleText.trim()) }
      catch(Exception ignored) { logDebug("Ignoring non-numeric RC Treble value=${trebleText}") }
    }
    String loudness = null
    if(loudnessText) {
      if(loudnessText == '1') { loudness = 'on' }
      else if(loudnessText == '0') { loudness = 'off' }
      else { logDebug("Ignoring unexpected RC Loudness value=${loudnessText}") }
    }
    String nightMode = null
    if(nightModeText) {
      if(nightModeText == '1') { nightMode = 'on' }
      else if(nightModeText == '0') { nightMode = 'off' }
      else { logDebug("Ignoring unexpected RC NightMode value=${nightModeText}") }
    }
    String dialogLevel = null
    if(dialogLevelText) {
      if(dialogLevelText == '1') { dialogLevel = 'on' }
      else if(dialogLevelText == '0') { dialogLevel = 'off' }
      else { logDebug("Ignoring unexpected RC DialogLevel value=${dialogLevelText}") }
    }
    String outputFixed = iid['OutputFixed']['@val']?.toString()?.trim() ?: null
    String speakerSize = iid['SpeakerSize']['@val']?.toString()?.trim() ?: null
    String subGain = iid['SubGain']['@val']?.toString()?.trim() ?: null
    String subCrossover = iid['SubCrossover']['@val']?.toString()?.trim() ?: null
    String subPolarity = iid['SubPolarity']['@val']?.toString()?.trim() ?: null
    String subEnabled = iid['SubEnabled']['@val']?.toString()?.trim() ?: null
    String sonarEnabled = iid['SonarEnabled']['@val']?.toString()?.trim() ?: null
    String sonarCalibrationAvailable = iid['SonarCalibrationAvailable']['@val']?.toString()?.trim() ?: null
    String presetNameList = iid['PresetNameList']['@val']?.toString()?.trim() ?: null
    if(volumeMaster == null && volumeLF == null && volumeRF == null && muteMaster == null &&
      muteLF == null && muteRF == null && bass == null && treble == null && loudness == null &&
      nightMode == null && dialogLevel == null && outputFixed == null && speakerSize == null &&
      subGain == null && subCrossover == null && subPolarity == null && subEnabled == null &&
      sonarEnabled == null && sonarCalibrationAvailable == null && presetNameList == null) { return null }
    return [volumeMaster: volumeMaster, volumeLF: volumeLF, volumeRF: volumeRF,
      muteMaster: muteMaster, muteLF: muteLF, muteRF: muteRF, bass: bass, treble: treble,
      loudness: loudness, nightMode: nightMode, dialogLevel: dialogLevel,
      outputFixed: outputFixed, speakerSize: speakerSize, subGain: subGain,
      subCrossover: subCrossover, subPolarity: subPolarity, subEnabled: subEnabled,
      sonarEnabled: sonarEnabled, sonarCalibrationAvailable: sonarCalibrationAvailable,
      presetNameList: presetNameList, observedAt: getHubitatCurrentTime()]
  } catch(Exception e) {
    logDebug("Could not parse RC snapshot: ${e.message}")
    return null
  }
}

Map getCurrentRcSnapshot() {
  String dni = device.getDeviceNetworkId()?.toString()
  return [snapshot: currentRcSnapshotByDni[dni], seq: currentRcSeqByDni[dni]?.get(), sid: currentRcSidByDni[dni]]
}

String getRcDeviceControlJson() {
  try {
    String stored = device.getDataValue('rcSnapshotJson')?.toString()
    if(stored) { return stored }
  } catch(Exception ignored) { }
  String dni = device.getDeviceNetworkId()?.toString()
  Map snap = currentRcSnapshotByDni[dni]
  return JsonOutput.toJson(snap ?: [:])
}

Map getCurrentRc() { return getCurrentRcSnapshot() }

void deferOrProcessRenderingControlPayload(String dni, String xmlBody) {
  deferOrProcessRenderingControlPayload(dni, xmlBody, null, null)
}

void deferOrProcessRenderingControlPayload(String dni, String xmlBody, Integer incomingSeq) {
  deferOrProcessRenderingControlPayload(dni, xmlBody, incomingSeq, null)
}

void deferOrProcessRenderingControlPayload(String dni, String xmlBody, Integer incomingSeq, String incomingSid) {
  handleRcFull(dni, xmlBody, incomingSeq, incomingSid)
}

void handleRcFull(String dni, String xmlBody, Integer incomingSeq, String incomingSid) {
  String deviceDni = dni ?: device.getDeviceNetworkId()
  if(!deviceDni || !xmlBody) { return }
  if(!isPlausibleXmlPayload(xmlBody)) { return }
  String sidKey = incomingSid?.toString()?.trim() ?: null
  if(sidKey == '') { sidKey = null }
  String storedSid = currentRcSidByDni.get(deviceDni)
  Boolean sidChanged = sidKey != null && storedSid != null && storedSid != sidKey
  Boolean isReset = (incomingSeq != null && incomingSeq == 0) || sidChanged
  if(incomingSeq != null) {
    AtomicInteger ref = currentRcSeqByDni.get(deviceDni)
    if(ref == null) {
      AtomicInteger created = new AtomicInteger(incomingSeq)
      currentRcSeqByDni.putIfAbsent(deviceDni, created)
      ref = currentRcSeqByDni.get(deviceDni)
    }
    if(isReset) {
      ref.set(incomingSeq)
    } else {
      if(ref.get() > incomingSeq) {
        logDebug("Ignoring stale RC SEQ=${incomingSeq} <= prev=${ref.get()} for ${deviceDni}")
        return
      }
      Integer prevVal = ref.get()
      while(incomingSeq > prevVal && !ref.compareAndSet(prevVal, incomingSeq)) {
        prevVal = ref.get()
        if(incomingSeq <= prevVal) { logDebug("Ignoring stale RC SEQ=${incomingSeq} <= prev=${prevVal} for ${deviceDni}"); return }
      }
    }
  }
  Map newSnapshot = parseRcFullSnapshot(xmlBody)
  if(newSnapshot == null) { return }
  if(incomingSeq != null && !isReset) {
    AtomicInteger ref2 = currentRcSeqByDni.get(deviceDni)
    if(ref2 != null && incomingSeq < ref2.get()) {
      logDebug("Ignoring RC that became stale during parse SEQ=${incomingSeq} < prev=${ref2.get()} for ${deviceDni}")
      return
    }
  }
  Map prior = currentRcSnapshotByDni.get(deviceDni)
  Map merged = new LinkedHashMap()
  if(prior != null) { merged.putAll(prior) }
  newSnapshot.each { Object key, Object value -> if(value != null) { merged[key] = value } }
  currentRcSnapshotByDni[deviceDni] = new LinkedHashMap(merged)
  if(incomingSeq != null) {
    AtomicInteger ref2 = currentRcSeqByDni.get(deviceDni)
    if(ref2 != null) { ref2.set(incomingSeq) } else { currentRcSeqByDni.put(deviceDni, new AtomicInteger(incomingSeq)) }
  }
  if(sidKey != null) { currentRcSidByDni[deviceDni] = sidKey }
  applyRcSnapshotIfDifferent(merged)
  // RC snapshot canonical in currentRcSnapshotByDni + device data (rcSnapshotJson); no Current State publication
}

Integer deriveBalanceFromStereo(Integer lf, Integer rf) {
  if(lf == null && rf == null) { return null }
  Integer effectiveLF = lf != null ? lf : 100
  Integer effectiveRF = rf != null ? rf : 100
  if(effectiveLF < 100) { return ((100 - effectiveLF) / 5) as Integer }
  else if(effectiveRF < 100) { return -((100 - effectiveRF) / 5) as Integer }
  return 0
}

Boolean applyRcSnapshotIfDifferent(Map snap) {
  if(snap == null) { return false }
  Map prior = null
  try {
    String json = device.getDataValue('rcSnapshotJson')?.toString()
    if(json) { prior = (Map)getSharedJsonSlurper().parseText(json) }
  } catch(Exception ignored) { prior = null }
  Integer newVolume = snap.volumeMaster != null ? (snap.volumeMaster as Integer) : null
  Integer priorVolume = prior?.volumeMaster != null ? (prior.volumeMaster as Integer) : null
  Integer newLF = snap.volumeLF != null ? (snap.volumeLF as Integer) : null
  Integer newRF = snap.volumeRF != null ? (snap.volumeRF as Integer) : null
  Integer priorLF = prior?.volumeLF != null ? (prior.volumeLF as Integer) : null
  Integer priorRF = prior?.volumeRF != null ? (prior.volumeRF as Integer) : null
  Integer newBalance = deriveBalanceFromStereo(newLF, newRF)
  Integer priorBalance = deriveBalanceFromStereo(priorLF, priorRF)
  String newMute = snap.muteMaster?.toString()
  String priorMute = prior?.muteMaster?.toString()
  Integer newBass = snap.bass != null ? (snap.bass as Integer) : null
  Integer priorBass = prior?.bass != null ? (prior.bass as Integer) : null
  Integer newTreble = snap.treble != null ? (snap.treble as Integer) : null
  Integer priorTreble = prior?.treble != null ? (prior.treble as Integer) : null
  String newLoudness = snap.loudness?.toString()
  String priorLoudness = prior?.loudness?.toString()
  String newNightMode = snap.nightMode?.toString()
  String priorNightMode = prior?.nightMode?.toString()
  String newDialogLevel = snap.dialogLevel?.toString()
  String priorDialogLevel = prior?.dialogLevel?.toString()
  Boolean volumeChanged = newVolume != null && newVolume != priorVolume
  Boolean balanceChanged = newBalance != null && newBalance != priorBalance
  Boolean muteChanged = newMute != null && newMute != priorMute
  Boolean bassChanged = newBass != null && newBass != priorBass
  Boolean trebleChanged = newTreble != null && newTreble != priorTreble
  Boolean loudnessChanged = newLoudness != null && newLoudness != priorLoudness
  Boolean nightModeChanged = newNightMode != null && newNightMode != priorNightMode
  Boolean dialogLevelChanged = newDialogLevel != null && newDialogLevel != priorDialogLevel
  Boolean captureChanged = false
  List<String> captureKeys = ['muteLF', 'muteRF', 'outputFixed', 'speakerSize', 'subGain',
    'subCrossover', 'subPolarity', 'subEnabled', 'sonarEnabled', 'sonarCalibrationAvailable', 'presetNameList']
  for(String key : captureKeys) {
    if(!captureChanged && snap[key] != null && snap[key].toString() != (prior?.get(key)?.toString())) { captureChanged = true }
  }
  try { device.updateDataValue('rcSnapshotJson', JsonOutput.toJson(snap)) } catch(Exception ignored) {}
  if(!volumeChanged && !balanceChanged && !muteChanged && !bassChanged && !trebleChanged &&
    !loudnessChanged && !nightModeChanged && !dialogLevelChanged && !captureChanged) { return false }
  if(volumeChanged) { setPlayerVolume(newVolume) }
  if(balanceChanged) { setBalanceState(newBalance) }
  if(muteChanged) { setMuteState(newMute) }
  if(bassChanged) { setBassState(newBass) }
  if(trebleChanged) { setTrebleState(newTreble) }
  if(loudnessChanged) { setLoudnessState(newLoudness) }
  if(nightModeChanged) { setNightModeState(newNightMode) }
  if(dialogLevelChanged) { setDialogLevelState(newDialogLevel) }
  return true
}

@CompileStatic
Map parseZoneGroupTopologySnapshot(String xmlString, String playerId) {
  if(!playerId || !isPlausibleXmlPayload(xmlString)) { return null }
  try {
    XmlSlurper parser = new XmlSlurper()
    GPathResult propertyset = parser.parseText(xmlString)
    String stateXml = ((GPathResult)propertyset['property']['ZoneGroupState']).text()
    if(!isPlausibleXmlPayload(stateXml)) { return null }
    GPathResult stateXmlParsed = parser.parseText(stateXml)
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
      String coordinatorName = null
      for(GPathResult member : group.children()) {
        String memberId = member['@UUID'].toString()
        String memberName = member['@ZoneName'].toString()
        if(memberId == groupCoordinatorId) { coordinatorName = memberName }
        if(member['@Invisible'].toString() != '1') {
          groupPlayerIds.add(memberId)
          groupPlayerNames.add(memberName)
        }
        if(memberId == playerId) {
          playerGroup = group
          player = member
        }
      }
      String resolvedGroupName = group['@ZoneGroupName'].toString()
      if(!resolvedGroupName && groupPlayerIds.contains(playerId)) { resolvedGroupName = groupName }
      if(!resolvedGroupName) { resolvedGroupName = coordinatorName }
      householdGroups.add([groupId: groupId, coordinatorId: groupCoordinatorId,
        playerIds: groupPlayerIds, playerNames: groupPlayerNames, coordinatorName: coordinatorName,
        groupName: resolvedGroupName])
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
      for(String part : splitTokens(moreInfo, ',')) {
        List<String> entry = splitTokens(part, ':')
        if(entry.size() > 1 && (entry[0] == 'BattTmp' || entry[0] == 'BattPct' || entry[0] == 'BattChg')) {
          batteryData[entry[0]] = entry[1]
        }
      }
    }
    String playerGroupId = playerGroup['@ID'].toString() ?: coordinatorId
    Map playerGroupInfo = householdGroups.find { Map item -> item.groupId?.toString() == playerGroupId }
    String playerGroupName = playerGroupInfo?.groupName?.toString()
    return [playerFound: true, groupId: playerGroupId, coordinatorId: coordinatorId, coordinatorName: coordinatorName,
      memberIds: sortedMemberIds, visibleMemberIds: visibleMemberIdList,
      memberNames: new ArrayList<String>(memberNames),
      groupName: playerGroupName, batteryData: batteryData, groups: householdGroups]
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
  return StringEscapeUtils.unescapeXml(text.replace('&amp;lt;', '&lt;').replace('&amp;gt;', '&gt;').replace('&amp;quot;', '&quot;'))
}

@CompileStatic
String xmlLocalName(GPathResult node) {
  String qualifiedName = node?.name()?.toString()
  if(!qualifiedName) { return null }
  Integer namespaceEnd = qualifiedName.lastIndexOf('}')
  if(namespaceEnd >= 0) { qualifiedName = qualifiedName.substring(namespaceEnd + 1) }
  Integer prefixEnd = qualifiedName.lastIndexOf(':')
  if(prefixEnd >= 0) { qualifiedName = qualifiedName.substring(prefixEnd + 1) }
  return qualifiedName
}

String xmlAttribute(GPathResult node, String attributeName) {
  if(node == null || !attributeName) { return null }
  return node.getAt("@${attributeName}")?.toString()
}

GPathResult findDirectXmlChild(GPathResult node, String localName) {
  if(node == null || !localName) { return null }
  for(Object child : node.children()) {
    if(child instanceof GPathResult && xmlLocalName((GPathResult)child) == localName) { return (GPathResult)child }
  }
  return null
}

GPathResult findXmlChildContainer(GPathResult node, String localName) {
  if(node == null || !localName) { return null }
  for(Object child : node.children()) {
    if(child instanceof GPathResult) {
      GPathResult match = findDirectXmlChild((GPathResult)child, localName)
      if(match != null) { return match }
    }
  }
  return null
}

String findSoapBodyField(GPathResult envelope, String localName) {
  GPathResult body = findDirectXmlChild(envelope, 'Body')
  GPathResult container = body ?: envelope
  GPathResult field = findDirectXmlChild(container, localName)
  if(field == null) { field = findXmlChildContainer(container, localName) }
  return field?.text()?.toString()
}

@CompileStatic
String renderHeaderValues(Map headers) {
  if(headers == null || headers.isEmpty()) {
    return 'none'
  }
  List<String> names = new ArrayList<String>()
  for(Object key : headers.keySet()) {
    names.add(key == null ? 'null' : key.toString())
  }
  Collections.sort(names)
  List<String> pairs = new ArrayList<String>()
  for(String name : names) {
    Object rawValue = headers[name]
    String value = rawValue == null ? 'null' : rawValue.toString()
    if(value.length() > 200) {
      value = value.substring(0, 200) + '...'
    }
    pairs.add(name + '=' + value)
  }
  String rendered = pairs.join(',')
  if(rendered.length() > 1000) {
    rendered = rendered.substring(0, 1000) + '...'
  }
  return rendered
}

@CompileStatic
void processAVTransportMessages(String xmlString, String localUpnpUrl) {
  // Thin shim over handleAvtFull (kept for existing callers/tests).
  handleAvtFull(device.getDeviceNetworkId()?.toString() ?: 'unknown', xmlString, null)
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
Boolean processZoneGroupTopologySnapshot(Map topology, LinkedHashSet<String> oldGroupedRincons) {
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
    logDebug('ZGT message parsed, group member changes found.')
  } else {
    logDebug('ZGT message parsed, no group member changes.')
  }

  setGroupPlayerIds(groupedRincons.toList())

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
  String playerDni = device.getDeviceNetworkId()
  lastMetadataContainerId.remove(playerDni)
  lastPlaybackState.remove(playerDni)
  setCurrentArtistName('Not Available')
  setCurrentAlbumName('Not Available')
  setCurrentTrackName('Not Available')
  setCurrentTrackNumber(0)
  setTrackDataEvents([:])
  setNextArtistName('Not Available')
  setNextAlbumName('Not Available')
  setNextTrackName('Not Available')
  setNextTrackAlbumArtURI('Not Available', false)
  // Artwork attributes are owned by the app's UPnP fanout.
  if(shouldQueuePlayerAttributeUpdate('audioSource', 'Not Available', this.device.currentValue('audioSource'))) {
    queuePlayerAttributeUpdate('audioSource', 'Not Available')
  }
  if(shouldQueuePlayerAttributeUpdate('currentFavorite', 'Not Available', this.device.currentValue('currentFavorite'))) {
    queuePlayerAttributeUpdate('currentFavorite', 'Not Available')
  }
  if(shouldQueuePlayerAttributeUpdate('trackDescription', 'Not Available', this.device.currentValue('trackDescription'))) {
    queuePlayerAttributeUpdate('trackDescription', 'Not Available')
  }
}

@CompileStatic
void updateZoneGroupName(String groupName) {
  queuePlayerAttributeUpdate('groupName', groupName)
}

void processGroupRenderingControlMessages(String xmlString) {
  deferOrProcessGroupRenderingControlPayload(device.getDeviceNetworkId()?.toString() ?: 'unknown', xmlString)
}

@CompileStatic
void setGroupVolumeState(Integer groupVolume) {
  if(!shouldQueuePlayerAttributeUpdate('groupVolume', groupVolume, getGroupVolumeState())) { return }
  queuePlayerAttributeUpdate('groupVolume', groupVolume)
}
@CompileStatic
Integer getGroupVolumeState() {
  Object raw = getDevice().currentValue('groupVolume', true)
  return raw != null ? raw as Integer : null
}

@CompileStatic
void setGroupMuteState(String groupMute) {
  if(!shouldQueuePlayerAttributeUpdate('groupMute', groupMute, getGroupMuteState())) { return }
  queuePlayerAttributeUpdate('groupMute', groupMute)
}
@CompileStatic
String getGroupMuteState() {
  Object raw = getDevice().currentValue('groupMute', true)
  return raw != null ? raw.toString() : 'unmuted'
}

@CompileStatic
void setNightModeState(String value) {
  if(!shouldQueuePlayerAttributeUpdate('nightMode', value, this.device.currentValue('nightMode') as String)) { return }
  queuePlayerAttributeUpdate('nightMode', value)
  sendChildEvent(getNightModeChild(), 'switch', value)
}

@CompileStatic
void setDialogLevelState(String value) {
  if(!shouldQueuePlayerAttributeUpdate('speechEnhancement', value,
      this.device.currentValue('speechEnhancement') as String)) { return }
  queuePlayerAttributeUpdate('speechEnhancement', value)
  sendChildEvent(getSpeechEnhancementChild(), 'switch', value)
}


@CompileStatic
void processRenderingControlMessages(String xmlString) {
  deferOrProcessRenderingControlPayload(device.getDeviceNetworkId()?.toString() ?: 'unknown', xmlString)
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
  if(sid == 'sid3') {
    device.removeDataValue(ZGT_SUB_DEVICE_KEY)
    device.removeDataValue(ZGT_SUB_EXPIRES_DEVICE_KEY)
    device.removeDataValue(ZGT_SUB_EXPIRES_EPOCH_DEVICE_KEY)
  }
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
  if(sid == 'sid3') {
    String sidValue = headers["SID"]?.toString() ?: headers["sid"]?.toString() ?: getSid('sid3')
    if(sidValue) { setDeviceDataValue(ZGT_SUB_DEVICE_KEY, sidValue) }
    setDeviceDataValue(ZGT_SUB_EXPIRES_DEVICE_KEY, formatted)
    setDeviceDataValue(ZGT_SUB_EXPIRES_EPOCH_DEVICE_KEY, expiresEpoch.toString())
  }
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
  if(entry == null || !['sid1','sid2','sid3','sid4','sid5','sid6'].contains(sidKey)) { return }
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
        case 'sid5': unschedule('retryUpnpSid5'); break
        case 'sid6': unschedule('retryUpnpSid6'); break
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
  return [sid1: 'subscribeToAVTransport', sid2: 'subscribeToMrRcEvents', sid3: 'subscribeToZgtEvents', sid4: 'subscribeToMrGrcEvents', sid5: 'subscribeToHtControlEvents', sid6: 'subscribeToContentDirectory'][sidKey]
}
String defaultResubscribeMethod(String sidKey) {
  return [sid1: 'resubscribeToAVTransport', sid2: 'resubscribeToMrRcEvents', sid3: 'resubscribeToZgtEvents', sid4: 'resubscribeToMrGrcEvents', sid5: 'resubscribeToHtControlEvents', sid6: 'resubscribeToContentDirectory'][sidKey]
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
        if(colon > 0) { headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim()) }
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
        if(request.sidKey == 'sid4' && request.operation == 'subscribe') {
          String grcDni = device.getDeviceNetworkId()?.toString()
          if(grcDni) {
            currentGrcSeqByDni.remove(grcDni)
            currentGrcSidByDni.remove(grcDni)
          }
        }
        if(request.sidKey == 'sid2' && request.operation == 'subscribe') {
          String rcDni = device.getDeviceNetworkId()?.toString()
          if(rcDni) {
            currentRcSeqByDni.remove(rcDni)
            currentRcSidByDni.remove(rcDni)
          }
        }
        if(request.sidKey == 'sid3') {
          logDebug("ZGT subscription ${request.operation} accepted with SID, device-data zgtSub updated")
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
      if(request.sidKey == 'sid3' && request.attempts >= UPNP_REQUEST_MAX_ATTEMPTS) {
        logWarn("ZGT subscription ${request.operation} exhausted retries (status=${status ?: 'none'}, error=${error ?: 'none'})")
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
    case 'sid5': runIn(delay, 'retryUpnpSid5', options); break
    case 'sid6': runIn(delay, 'retryUpnpSid6', options); break
  }
}
void retryUpnpSid1(Map data) { retryUpnpRequest(data) }
void retryUpnpSid2(Map data) { retryUpnpRequest(data) }
void retryUpnpSid3(Map data) { retryUpnpRequest(data) }
void retryUpnpSid4(Map data) { retryUpnpRequest(data) }
void retryUpnpSid5(Map data) { retryUpnpRequest(data) }
void retryUpnpSid6(Map data) { retryUpnpRequest(data) }

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
    unschedule('retryUpnpSid5')
    unschedule('retryUpnpSid6')
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
    unschedule('retryUpnpSid5')
    unschedule('retryUpnpSid6')
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
  if(subValid('sid3')) { return }
  String sub = 'subscribeToZgtEvents'
  String subId = 'sid3'
  String subPath = '/zgt'
  String evtSub = ZGT_EVENTS
  String callback = ZGT_EVENTS_CALLBACK
  upnpSubscribeGeneric(sub, subId, subPath, callback, evtSub)
}

// Entry point used by the parent app when a GROUP_STATUS_MOVED 499 arrives.

@CompileStatic
void resubscribeToZgtEvents() {
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
// /////////////////////////////////////////////////////////////////////////////
// '/MediaRenderer/HTControl/Event' //sid5
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToHtControlEvents() {
  upnpSubscribeGeneric('subscribeToHtControlEvents', 'sid5', '/htcontrol',
      HTCONTROL_EVENTS_CALLBACK, HTCONTROL_EVENTS)
}

@CompileStatic
void resubscribeToHtControlEvents() {
  upnpResubscribeGeneric('resubscribeToHtControlEvents', 'sid5', '/htcontrol',
      HTCONTROL_EVENTS_CALLBACK, HTCONTROL_EVENTS)
}

@CompileStatic
void subscribeResubscribeToHtControlCallback(HubResponse response) {
  subscribeResubscribeGenericCallback('subscribeToHtControlEvents', 'resubscribeToHtControlEvents',
      'sid5', 'MediaRenderer/HTControl', response)
}

@CompileStatic
void unsubscribeFromHtControlEvents() {
  upnpUnsubscribeGeneric('unsubscribeFromHtControlEvents', 'sid5', 'resubscribeToHtControlEvents',
      HTCONTROL_EVENTS, HTCONTROL_EVENTS_UNSUB_CALLBACK)
}

@CompileStatic
void unsubscribeFromHtControlEventsCallback(HubResponse response) {
  upnpUnsubscribeCallbackGeneric('sid5', 'MediaRenderer/HTControl', response)
}
// /////////////////////////////////////////////////////////////////////////////
// '/MediaServer/ContentDirectory/Event' //sid6
// /////////////////////////////////////////////////////////////////////////////
@CompileStatic
void subscribeToContentDirectory() {
  if(!hasFavoriteOrPlaylistChild()) { return }
  upnpSubscribeGeneric('subscribeToContentDirectory', 'sid6', '/contentdirectory',
      CONTENT_DIRECTORY_CALLBACK, CONTENT_DIRECTORY_EVENTS)
}

@CompileStatic
void resubscribeToContentDirectory() {
  if(!hasFavoriteOrPlaylistChild()) { return }
  upnpResubscribeGeneric('resubscribeToContentDirectory', 'sid6', '/contentdirectory',
      CONTENT_DIRECTORY_CALLBACK, CONTENT_DIRECTORY_EVENTS)
}

@CompileStatic
void unsubscribeFromContentDirectory() {
  upnpUnsubscribeGeneric('unsubscribeFromContentDirectory', 'sid6', 'resubscribeToContentDirectory',
      CONTENT_DIRECTORY_EVENTS, CONTENT_DIRECTORY_UNSUB_CALLBACK)
}

@CompileStatic
void subscribeResubscribeToContentDirectoryCallback(HubResponse response) {
  subscribeResubscribeGenericCallback('subscribeToContentDirectory', 'resubscribeToContentDirectory',
      'sid6', 'MediaServer/ContentDirectory', response)
}

@CompileStatic
void unsubscribeContentDirectoryCallback(HubResponse response) {
  upnpUnsubscribeCallbackGeneric('sid6', 'MediaServer/ContentDirectory', response)
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

void scheduleAlbumArtPreferenceRefresh() {
  runInMillis(1, 'notifyParentAlbumArtPreferenceUpdate', [overwrite: true])
}

void notifyParentAlbumArtPreferenceUpdate() {
  try {
    parent.playerAlbumArtPreferenceUpdate([playerDni: device.getDeviceNetworkId()])
  } catch(Exception e) {
    logWarn("Could not notify parent of album art preference update: ${e.message}")
  }
}

void clearTrackDataEvent() {
  queuePlayerAttributeUpdate('trackData', '{}')
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
  return ZonedDateTime.now(hubClock()).format(SID_TIMESTAMP_FORMATTER)
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
Long getLastInboundHtControlEventEpoch() {
  Long ts = eventTimestamps.get("${getDeviceDNI()}-lastHtControlEvent" as String)
  return ts != null ? ts : 0L
}
@CompileStatic
void setLastInboundHtControlEvent() {
  eventTimestamps.put("${getDeviceDNI()}-lastHtControlEvent" as String, Instant.now().getEpochSecond())
  updateEventTimestampDeviceData('lastHtControlEvent')
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

private Object getLocalDeviceEventLock(String dni) {
  return localDeviceEventStripes.get(dni)
}

/** Queue one local attribute until its fixed maximum-wait drain deadline. */
void queuePlayerAttributeUpdate(String name, Object value) {
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

Boolean shouldQueuePlayerAttributeUpdate(String name, Object value, Object currentValue) {
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
  Map<String, Object> batch = [:]
  synchronized(lock) {
    state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
    state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
    ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
    if(!pending || pending.isEmpty()) {
      if(pending != null) { pendingLocalDeviceEvents.remove(dni, pending) }
      return
    }
    List<String> names = new ArrayList<String>(pending.keySet()).take(LOCAL_EVENTS_PER_DRAIN)
    names.each { String attrName ->
      Object attrValue = pending.remove(attrName)
      if(attrValue != null) { batch[attrName] = attrValue }
    }
  }

  ConcurrentHashMap<String, Object> lastSent = lastLocalDeviceEvents.get(dni)
  if(lastSent == null) {
    ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<String, Object>()
    ConcurrentHashMap<String, Object> existing = lastLocalDeviceEvents.putIfAbsent(dni, created)
    lastSent = existing ?: created
  }
  if(!batch.isEmpty()) {
    try {
      if(!dispatchParentStateUpdateDirect([kind: 'deviceAttributes', playerDni: dni, attributes: batch])) {
        throw new IllegalStateException('Parent app rejected player attribute handoff')
      }
      lastSent.putAll(batch)
    } catch(Exception e) {
      synchronized(lock) {
        ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
        if(pending == null) {
          ConcurrentHashMap<String, Object> created = new ConcurrentHashMap<String, Object>()
          ConcurrentHashMap<String, Object> existing = pendingLocalDeviceEvents.putIfAbsent(dni, created)
          pending = existing ?: created
        }
        batch.each { String attrName, Object attrValue -> pending.put(attrName, attrValue) }
        logWarn("Could not hand off player attributes to the app for dni=${dni}; retaining ${batch.size()} values: ${e.message}")
      }
    }
  }
  synchronized(lock) {
    ConcurrentHashMap<String, Object> pending = pendingLocalDeviceEvents.get(dni)
    if(!pending || pending.isEmpty()) {
      if(pending != null) { pendingLocalDeviceEvents.remove(dni, pending) }
      state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
      state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
    } else {
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

// Retain WS namespace bookkeeping for the commands and event streams still in use.
@Field static final List<String> WS_NAMESPACES = ['audioClip']
@Field static final List<String> EVENT_TIMESTAMP_KEYS = ['lastAVTransportEvent', 'lastMrRcEvent', 'lastZgtEvent', 'lastMrGrcEvent', 'lastHtControlEvent', 'lastWebsocketEvent']

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

/** Re-arm publication callbacks if a persisted pending queue outlives its job. */
void verifyPendingPublicationDrains() {
  String dni = device?.getDeviceNetworkId()
  ConcurrentHashMap<String, Object> local = dni ? pendingLocalDeviceEvents.get(dni) : null
  ConcurrentHashMap<String, Object> group = dni ? pendingGroupDeviceUpdates.get(dni) : null
  Map component = state[PENDING_COMPONENT_CHILD_EVENTS_KEY] instanceof Map
      ? (Map)state[PENDING_COMPONENT_CHILD_EVENTS_KEY] : [:]
  if(local == null || local.isEmpty()) {
    state.remove(LOCAL_EVENT_DRAIN_SCHEDULED_KEY)
    state.remove(LOCAL_EVENT_DRAIN_DUE_AT_KEY)
  }
  if(local != null && !local.isEmpty()) { scheduleLocalDeviceEventDrain(LOCAL_EVENT_DRAIN_INTERVAL_MS) }
  if(group != null && !group.isEmpty()) { scheduleGroupDeviceUpdateDrain(GROUP_DEVICE_UPDATE_MAX_WAIT_MS) }
  if(!component.isEmpty()) { scheduleComponentChildEventDrain(COMPONENT_CHILD_DRAIN_INTERVAL_MS) }
  if((local != null && !local.isEmpty()) ||
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
  return splitTokens(getDeviceDataValue('secondaryIds')?.toString(), ',')
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
  return splitTokens(getDeviceDataValue('secondaryDeviceIps')?.toString(), ',')
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
  queuePlayerAttributeUpdate('groupId', groupId)
  // Refresh coordinator AVTransport state after the topology transition.
  if(getIsGroupCoordinator() == true) {
    runIn(1, 'refreshCoordinatorPlaybackState', [data:[groupId:groupId], overwrite:true])
  }
}

void refreshCoordinatorPlaybackState(Map data = null) {
  if(getIsGroupCoordinator() != true) {
    logDebug('Skipping delayed group subscription because this player is no longer the coordinator')
    return
  }
  String groupId = data?.groupId
  if(groupId != null && groupId != '') {
    subscribeToAVTransport()
    getPlaybackStatus()
    getPlaybackMetadataStatus()
  }
}
@CompileStatic
Boolean isCurrentlySubcribedToAudioClipWS() { return getWsSubscriptionValue('audioClip') == 'Subscribed' }
String getGroupName() {
  return this.device.currentValue('groupName',true)
}
void setGroupName(String groupName) {
  if(!shouldQueuePlayerAttributeUpdate('groupName', groupName, getGroupName())) { return }
  queuePlayerAttributeUpdate('groupName', groupName)
}

Boolean getIsGrouped() {
  return this.device.currentValue('isGrouped') == 'on'
}
void setIsGrouped(Boolean isGrouped) {
  String newValue = isGrouped ? 'on' : 'off'
  if(!shouldQueuePlayerAttributeUpdate('isGrouped', newValue, this.device.currentValue('isGrouped'))) { return }
  queuePlayerAttributeUpdate('isGrouped', newValue)
}

Integer getGroupMemberCount() {
  return this.device.currentValue('groupMemberCount') as Integer
}
void setGroupMemberCount(Integer groupMemberCount) {
  if(!shouldQueuePlayerAttributeUpdate('groupMemberCount', groupMemberCount,
      this.device.currentValue('groupMemberCount') as Integer)) { return }
  queuePlayerAttributeUpdate('groupMemberCount', groupMemberCount)
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
    queuePlayerAttributeUpdate('groupCoordinatorId', groupCoordinatorId)
  }
  Boolean isGroupCoordinator = getId() == groupCoordinatorId
  // Keep the persisted coordinator role derived from the ID on every topology
  // update, even when the ID itself did not change.
  setIsGroupCoordinator(isGroupCoordinator)

  // Signal setGroupPlayerIds() to re-evaluate group devices even when the
  // player list is unchanged, so the parent observes the coordinator change
  // through the topology attribute events in the same update.
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
  runIn(2, 'getPlaybackMetadataStatus', [overwrite: true])
  runIn(2, 'playerGetGroupsFull', [overwrite: true])
}

String getGroupCoordinatorName() {
  return this.device.currentValue('groupCoordinatorName')
}
void setGroupCoordinatorName(String groupCoordinatorName) {
  if(shouldQueuePlayerAttributeUpdate('groupCoordinatorName', groupCoordinatorName, getGroupCoordinatorName())) {
    queuePlayerAttributeUpdate('groupCoordinatorName', groupCoordinatorName)
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
    queuePlayerAttributeUpdate('isGroupCoordinator', publicValue)
  }
}

Boolean isGroupedAndCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator()
}

Boolean isGroupedAndNotCoordinator() {
  return this.device.currentValue('isGrouped', true) == 'on' && getIsGroupCoordinator() == false
}

List<String> getGroupPlayerIds() {
  return splitTokens(getDeviceDataValue('groupPlayerIds')?.toString(), ',')
}
void setGroupPlayerIds(List<String> groupPlayerIds) {
  String newValue = groupPlayerIds.join(',')
  String oldValue = getDeviceDataValue('groupPlayerIds')
  Boolean coordinatorChanged = getDeviceDataValue('coordinatorStatusChanged') == 'true'
  if(coordinatorChanged) { setDeviceDataValue('coordinatorStatusChanged', '') }
  if(newValue == oldValue && !coordinatorChanged) { return }
  if(newValue != oldValue) {
    Boolean isNowGrouped = groupPlayerIds.size() > 1
    setDeviceDataValue('groupPlayerIds', newValue)
    queuePlayerAttributeUpdate('isGrouped', isNowGrouped ? 'on' : 'off')
    queuePlayerAttributeUpdate('groupMemberCount', groupPlayerIds.size())
    queuePlayerAttributeUpdate('groupPlayerIds', newValue)
    // sid4 remains subscribed per device across group membership transitions.
  }
  logDebug('Updating group device with new group membership information')
}

List<String> getGroupFollowerRincons() {
  return getGroupPlayerIds() - [getId()]
}

List<String> getGroupFollowerDNIs() {
  return getGroupFollowerRincons().collect{ splitTokens(it, '_')[1][0..-6] }
}

@CompileStatic
String getDNIFromRincon(String rincon) {
  return splitTokens(rincon, '_')[1][0..-6]
}

String safeDniFromRincon(String rincon) {
  if(!rincon) { return '' }
  try { return getDNIFromRincon(rincon) ?: '' } catch(Exception ignored) { return '' }
}

String getGroupMemberNames() {
  return this.device.currentValue('groupMemberNames')
}
void setGroupMemberNames(List<String> groupPlayerNames) {
  String newValue = groupPlayerNames.toString()
  if(!shouldQueuePlayerAttributeUpdate('groupMemberNames', newValue, getGroupMemberNames())) { return }
  queuePlayerAttributeUpdate('groupMemberNames', newValue)
}

@CompileStatic
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
  Boolean audioSourceChanged = shouldQueuePlayerAttributeUpdate('audioSource', audioSourceUrl,
      this.device.currentValue('audioSource'))
  if(!audioSourceChanged) { return }
  queuePlayerAttributeUpdate('audioSource', audioSourceUrl)
  // Queue for batched group device update
  queueGroupDeviceUpdate([audioSource: audioSourceUrl])
}

String getAlbumArtURI() {
  return this.device.currentValue('albumArtURI',true)
}

void setCurrentFavorite(String foundFavImageUrl, String foundFavId, String foundFavName, Boolean isFav) {
  if(isFav) {
    Map<String, Object> value = [label: "Favorite #${foundFavId} ${foundFavName}", imageUrl: foundFavImageUrl ?: '']
    queuePlayerAttributeUpdate('currentFavorite', value)
    queueGroupDeviceUpdate([currentFavorite: value])
  } else {
    setCurrentFavorite('No favorite playing')
  }
}
@CompileStatic
void setCurrentFavorite(String uri) {
  if(!shouldQueuePlayerAttributeUpdate('currentFavorite', uri, getCurrentFavorite())) { return }
  queuePlayerAttributeUpdate('currentFavorite', uri)
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
  if(!shouldQueuePlayerAttributeUpdate('currentPlaylist', uri, getCurrentPlaylist())) { return }
  queuePlayerAttributeUpdate('currentPlaylist', uri)
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
  if(StringUtils.isBlank(transportState)) {
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
  if(!shouldQueuePlayerAttributeUpdate('status', status, this.device.currentValue('status')) &&
      !shouldQueuePlayerAttributeUpdate('transportStatus', status, getTransportStatus())) { return }
  queuePlayerAttributeUpdate('status', status)
  queuePlayerAttributeUpdate('transportStatus', status)
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
  if(!shouldQueuePlayerAttributeUpdate('volume', volume, oldVolume) &&
      !shouldQueuePlayerAttributeUpdate('level', volume, this.device.currentValue('level') as Integer)) { return }
  queuePlayerAttributeUpdate('level', volume)
  queuePlayerAttributeUpdate('volume', volume)
  if(volume > 0) { runtimeState.restoreLevelAfterUnmute = volume }
}
void setBalanceState(Integer balance) {
  Integer oldBalance = this.device.currentValue('balance') as Integer
  if(!shouldQueuePlayerAttributeUpdate('balance', balance, oldBalance)) { return }
  queuePlayerAttributeUpdate('balance', balance)
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
  if(!shouldQueuePlayerAttributeUpdate('mute', muted, observedMutedState)) {
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
  queuePlayerAttributeUpdate('mute', muted)
  logDebug("setMuteState queued parent mute event; value=${muted}")
}

void setBassState(Integer bass) {
  if(!shouldQueuePlayerAttributeUpdate('bass', bass, this.device.currentValue('bass') as Integer)) { return }
  queuePlayerAttributeUpdate('bass', bass)
}
void setTrebleState(Integer treble) {
  if(!shouldQueuePlayerAttributeUpdate('treble', treble, this.device.currentValue('treble') as Integer)) { return }
  queuePlayerAttributeUpdate('treble', treble)
}
void setLoudnessState(String loudness) {
  if(!shouldQueuePlayerAttributeUpdate('loudness', loudness, this.device.currentValue('loudness'))) { return }
  queuePlayerAttributeUpdate('loudness', loudness)
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
      !shouldQueuePlayerAttributeUpdate('currentRepeatOneMode', newRepeatOne, oldRepeatOne) &&
      !shouldQueuePlayerAttributeUpdate('currentRepeatAllMode', newRepeatAll, oldRepeatAll) &&
      !shouldQueuePlayerAttributeUpdate('currentShuffleMode', newShuffle, oldShuffle)) { return }
  queuePlayerAttributeUpdate('currentRepeatOneMode', newRepeatOne)
  queuePlayerAttributeUpdate('currentRepeatAllMode', newRepeatAll)
  queuePlayerAttributeUpdate('currentShuffleMode', newShuffle)
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
  if(!shouldQueuePlayerAttributeUpdate('currentCrossfadeMode', currentCrossfadeMode, getCrossfadeMode())) { return }
  queuePlayerAttributeUpdate('currentCrossfadeMode', currentCrossfadeMode)
  sendChildEvent(getCrossfadeControlChild(), 'switch', currentCrossfadeMode)
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentCrossfadeMode: currentCrossfadeMode])
}
String getCrossfadeMode() { return this.device.currentValue('currentCrossfadeMode') }

@CompileStatic
void setCurrentTrackDuration(String currentTrackDuration){
  if(shouldQueuePlayerAttributeUpdate('currentTrackDuration', currentTrackDuration, getCurrentTrackDuration())) {
    queuePlayerAttributeUpdate('currentTrackDuration', currentTrackDuration)
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
    queuePlayerAttributeUpdate('trackPosition', positionSeconds)
    queuePlayerAttributeUpdate('trackPositionUpdated', updatedAt)
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
  if(shouldQueuePlayerAttributeUpdate('currentArtistName', value, getCurrentArtistName())) {
    queuePlayerAttributeUpdate('currentArtistName', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentArtistName: value])
}
String getCurrentArtistName() { return this.device.currentValue('currentArtistName') }

@CompileStatic
void setCurrentAlbumName(String currentAlbumName) {
  String value = currentAlbumName ?: 'Not Available'
  if(shouldQueuePlayerAttributeUpdate('currentAlbumName', value, getCurrentAlbumName())) {
    queuePlayerAttributeUpdate('currentAlbumName', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentAlbumName: value])
}
String getCurrentAlbumName() { return this.device.currentValue('currentAlbumName') }

@CompileStatic
void setCurrentTrackName(String currentTrackName) {
  String value = currentTrackName ?: 'Not Available'
  if(shouldQueuePlayerAttributeUpdate('currentTrackName', value, getCurrentTrackName())) {
    queuePlayerAttributeUpdate('currentTrackName', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentTrackName: value])
}
String getCurrentTrackName() { return this.device.currentValue('currentTrackName') }

@CompileStatic
void setCurrentTrackNumber(Integer currentTrackNumber) {
  Integer value = currentTrackNumber ?: 0
  if(shouldQueuePlayerAttributeUpdate('currentTrackNumber', value, getCurrentTrackNumber())) {
    queuePlayerAttributeUpdate('currentTrackNumber', value)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([currentTrackNumber: value])
  observeTransportTrackNumber(value)
}
Integer getCurrentTrackNumber() { return this.device.currentValue('currentTrackNumber') }

void observeTransportTrackNumber(Integer currentTrackNumber) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || !(active.command in ['skipToNextTrack', 'skipToPreviousTrack'])) { return }
  Map baseline = active.baseline instanceof Map ? (Map)active.baseline : [:]
  Integer previous = baseline.trackNumber != null ? (baseline.trackNumber as Integer) : null
  if(previous != null && currentTrackNumber != null && previous != currentTrackNumber) {
    finishTransportCommand(active, true, "confirmed by track number ${previous} -> ${currentTrackNumber}")
  }
}

@CompileStatic
void setTrackDescription(String trackDescription) {
  String prevTrackDescription = getTrackDescription()
  Boolean changed = shouldQueuePlayerAttributeUpdate('trackDescription', trackDescription, prevTrackDescription)
  if(changed) {
    queuePlayerAttributeUpdate('trackDescription', trackDescription)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([trackDescription: trackDescription])

}
String getTrackDescription() { return this.device.currentValue('trackDescription') }

@CompileStatic
void setTrackDataEvents(Map trackData) {
  String trackDataJson = JsonOutput.toJson(trackData)
  if(shouldQueuePlayerAttributeUpdate('trackData', trackDataJson, getTrackDataEvents())) {
    queuePlayerAttributeUpdate('trackData', trackDataJson)
  }
  // Queue for batched group device update
  queueGroupDeviceUpdate([trackData: trackDataJson])
}
String getTrackDataEvents() {return this.device.currentValue('trackData')}

@CompileStatic
void setNextArtistName(String nextArtistName) {
  String value = nextArtistName ?: 'Not Available'
  if(!shouldQueuePlayerAttributeUpdate('nextArtistName', value, getNextArtistName())) { return }
  queuePlayerAttributeUpdate('nextArtistName', value)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextArtistName: value])
}
String getNextArtistName() { return this.device.currentValue('nextArtistName') }

@CompileStatic
void setNextAlbumName(String nextAlbumName) {
  String value = nextAlbumName ?: 'Not Available'
  if(!shouldQueuePlayerAttributeUpdate('nextAlbumName', value, getNextAlbumName())) { return }
  queuePlayerAttributeUpdate('nextAlbumName', value)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextAlbumName: value])
}
String getNextAlbumName() { return this.device.currentValue('nextAlbumName') }

@CompileStatic
void setNextTrackName(String nextTrackName) {
  String value = nextTrackName ?: 'Not Available'
  if(!shouldQueuePlayerAttributeUpdate('nextTrackName', value, getNextTrackName())) { return }
  queuePlayerAttributeUpdate('nextTrackName', value)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextTrackName: value])
}
String getNextTrackName() { return this.device.currentValue('nextTrackName') }

@CompileStatic
void setNextTrackAlbumArtURI(String nextTrackAlbumArtURI, Boolean isPlayingLocalTrack) {
  String rawUri = isPlayingLocalTrack || !nextTrackAlbumArtURI ? 'Not Available' : nextTrackAlbumArtURI
  if(!shouldQueuePlayerAttributeUpdate('nextTrackAlbumArtURI', rawUri, getNextTrackAlbumArtURI())) { return }
  queuePlayerAttributeUpdate('nextTrackAlbumArtURI', rawUri)
  // Queue for batched group device update
  queueGroupDeviceUpdate([nextTrackAlbumArtURI: rawUri])
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

/** Does the regular queue contain work that has not started yet? */
Boolean hasQueuedRegularAudioClip() {
  ConcurrentLinkedQueue<Map> queue = getDeviceAudioClipQueue()
  Map currentRegular = runtimeState.currentlyPlayingClip instanceof Map
      ? (Map)runtimeState.currentlyPlayingClip : null
  Map head = queue.peek()
  Boolean retainedActiveHead = currentRegular?.correlationId != null &&
      currentRegular.correlationId.toString() == head?.correlationId?.toString()
  return retainedActiveHead ? queue.size() > 1 : !queue.isEmpty()
}

// Correlation IDs capture queue ownership without logging clip URLs or speech text.
void traceAudioClipQueues(String stage) {
  if(!loggingEnabled('trace')) { return }
  Map active = runtimeState.activeAudioClipMessage as Map
  logDebug("AudioClipQueue ${stage}: playing=${runtimeState.audioClipPlaying}, active=${runtimeState.activeAudioClipCorrelationId}, activePriority=${active?.priority ?: 'REGULAR'}, currentRegular=${(runtimeState.currentlyPlayingClip as Map)?.correlationId}, regular=${getDeviceAudioClipQueue().collect { Map clip -> clip.correlationId }}, high=${getDeviceAudioClipQueueHighPriority().collect { Map clip -> clip.correlationId }}")
}

Boolean removeActiveRegularAudioClipFromQueue(String correlationId, String outcome) {
  if(!correlationId) { return false }
  ConcurrentLinkedQueue<Map> regularQueue = getDeviceAudioClipQueue()
  Map head = regularQueue.peek()
  if(head?.correlationId?.toString() != correlationId) {
    logWarn("Cannot remove ${outcome} regular audio clip ${correlationId}; queue head is ${head?.correlationId ?: 'empty'}")
    return false
  }
  Boolean removed = regularQueue.remove(head)
  if(removed) {
    logDebug("Removed ${outcome} regular audio clip ${correlationId} from queue; remaining=${regularQueue.collect { Map clip -> clip.correlationId }}")
  }
  return removed
}

void rotateAudioClipCorrelationId(Map clipMessage) {
  if(!(clipMessage instanceof Map)) { return }
  String nextGuid = UUID.randomUUID().toString()
  ['leftChannel', 'rightChannel'].each { String channel ->
    String rawCommand = clipMessage[channel] as String
    if(!rawCommand) { return }
    try {
      List commandEnvelope = (List)getSharedJsonSlurper().parseText(rawCommand)
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
  Map<String, Map> clipProgress = getAudioClipProgressByCorrelationId() instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)getAudioClipProgressByCorrelationId())
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
    // The player WebSocket is reserved for audioClip commands. Re-establish
    // only that subscription after reconnecting.
    List<Map> pendingCommands = removeExpiredWebsocketQueueEntries(getWebsocketOutboundQueue())
    runtimeState.remove(WS_OUTBOUND_QUEUE_STATE_KEY)
    runtimeState.audioClipSubscriptionRequestPending = false
    subscribeToAudioClip()
    List<Map> subscriptionCommands = getWebsocketOutboundQueue()
    if(!pendingCommands.isEmpty()) {
      subscriptionCommands.addAll(pendingCommands)
      runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = subscriptionCommands
    }
    scheduleNextWebsocketOutboundQueueExpiry()
    if(isWebsocketConnected()) { flushWebsocketOutboundQueue() }
  }
  else {
    runtimeState.remove(WS_CONNECT_PENDING_STATE_KEY)
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

    // Group snapshot now sourced by parent via attribute subscriptions (groupVolume/groupMute/status etc.)
    // No outbox publish — keep local flush accounting only
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
Boolean isGroupedFollowerForPlayback() {
  return getGroupPlayerIds().size() > 1 && !getIsGroupCoordinator()
}

void publishPlayerStateUpdate(Map payload) {
  if(payload == null || payload.isEmpty()) { return }
  Map eventData = new LinkedHashMap(payload)
  eventData['playerDni'] = device.getDeviceNetworkId()
  enqueueParentStateUpdate(eventData)
}

void enqueueParentStateUpdate(Map payload) {
  dispatchParentStateUpdateDirect(payload)
}

Boolean dispatchParentStateUpdateDirect(Map payload) {
  if(payload == null || payload.isEmpty()) { return true }
  String kind = payload?.kind?.toString()
  String dni = payload?.playerDni?.toString() ?: device.getDeviceNetworkId()
  try {
    if(kind == 'groupAnnouncementProgress') {
      parent.groupAnnouncementProgress(payload?.correlationId?.toString(), payload?.phase?.toString(), payload, dni)
      return true
    }
    if(kind == 'deviceAttributes') {
      parent.playerAttributeUpdate(payload)
      return true
    }
  } catch(Exception e) {
    logWarn("direct parent dispatch failed for kind=${kind}: ${e.message}")
  }
  return false
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
  return serviceName != null && serviceName.toLowerCase(Locale.ROOT).contains('amazon')
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
    // wsConnected now signaled via lastUpdated heartbeat (parse() sends lastUpdated)
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
  // wsRetry now signaled via lastUpdated heartbeat (absent parse() = offline)
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
  wsConnect()
}

void wsConnect() {
  if(isWebsocketConnected() || runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true) {
    logTrace("Skipping primary WebSocket connect; status=${getWebSocketStatus()}, connectPending=${runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true}")
    return
  }
  String websocketUrl = getDeviceDataValue('websocketUrl')
  if(!websocketUrl) {
    logDebug('WebSocket URL is not configured; waiting for player configuration before connecting')
    return
  }
  Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
  runtimeState[WS_CONNECT_PENDING_STATE_KEY] = true
  try {
    logInfo("Requesting primary WebSocket connection; previousStatus=${getWebSocketStatus()}, retryCount=${runtimeState.wsRetryCount ?: 0}")
    // Hubitat's native ping detects a half-open transport independently of
    // command responses. A missing application ACK must not cycle the socket.
    interfaces.webSocket.connect(websocketUrl, headers: headers,
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
  if(descriptor.namespace != 'audioClip' || !(descriptor.wireCommand in ['subscribe', 'loadAudioClip', 'cancelAudioClip'])) {
    logWarn('Dropping a WebSocket message outside the audioClip namespace')
    return
  }
  List<Map> queue = removeExpiredWebsocketQueueEntries(getWebsocketOutboundQueue())
  Map entry = [message: message, attempts: attempts ?: 0,
      createdAt: metadata?.createdAt ?: now(),
      expiresAt: metadata?.expiresAt ?: now() + WS_COMMAND_MAX_AGE_SECONDS * 1000L]
  if(metadata instanceof Map) { entry.putAll(metadata) }
  entry.putAll(descriptor)
  if(entry.correlationId) {
    queue = queue.findAll { Map queued -> queued.correlationId?.toString() != entry.correlationId.toString() || queued.kind == 'audioClipSubscribe' }
  }
  if(queue.size() >= WS_OUTBOUND_QUEUE_MAX_SIZE) {
    logWarn('Dropping newest audioClip WebSocket message because the queue is full')
    return
  }
  queue.add(entry)
  runtimeState[WS_OUTBOUND_QUEUE_STATE_KEY] = queue
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
    List payload = (List)getSharedJsonSlurper().parseText(message)
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
  if(namespace in ['audioClip', 'audioClip:1'] && wireCommand == 'subscribe') { return 'none' }
  if(namespace in ['audioClip', 'audioClip:1'] && wireCommand == 'loadAudioClip') { return 'audioClip' }
  if(namespace in ['audioClip', 'audioClip:1'] && wireCommand == 'cancelAudioClip') { return 'none' }
  return null
}

Map registerWebsocketCommandLedgerEntry(String message, Map outbound, Long createdAt = null) {
  Map descriptor = describeWebSocketCommand(message)
  if(descriptor.isEmpty()) { return [:] }
  Long sentAt = now()
  Long generation = (runtimeState[WS_CONNECTION_GENERATION_STATE_KEY] ?: 0L) as Long
  String entryId = UUID.randomUUID().toString()
  Map entry = new LinkedHashMap(descriptor)
  entry.putAll([entryId: entryId, connectionGeneration: generation, message: message,
      createdAt: createdAt ?: sentAt, sentAt: sentAt,
      expiresAt: (createdAt ?: sentAt) + WS_COMMAND_MAX_AGE_SECONDS * 1000L,
      ])
  entry.args = descriptor.args instanceof Map ? new LinkedHashMap((Map)descriptor.args) : [:]
  entry.kind = outbound?.kind
  entry.correlationId = outbound?.correlationId ?: entry.args?.name
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
  logDebug("WebSocket command ledger ${outcome}: id=${matched.entryId}, fingerprint=${namespace}.${response}, target=${matched.targetId ?: 'n/a'}, ${detail}")
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
  if(entry.namespace == 'audioClip' && entry.wireCommand == 'subscribe') {
    logWarn('WebSocket audioClip subscription response was not received before correlation expiry')
  }
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
  List<Map> pending = takeWebsocketCommandLedgerBatch()
  if(!pending.isEmpty()) { logWarn("Cleared ${pending.size()} pending audioClip WebSocket acknowledgement(s) after socket status ${status}; clips will not be replayed") }
}

void flushWebsocketOutboundQueue() {
  if(!isWebsocketSendAllowed()) {
    logDebug("Outbound WebSocket queue drain deferred; primary websocketStatus=${getWebSocketStatus()}, connectPending=${runtimeState[WS_CONNECT_PENDING_STATE_KEY] == true}")
    if(runtimeState[WS_CONNECT_PENDING_STATE_KEY] != true) { wsConnect() }
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
      if(entry?.kind == 'audioClip') { audioClipCommandSent(entry.correlationId as String) }
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
  if(!queued.isEmpty() && metadata?.kind != 'audioClipSubscribe') {
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
  try {
    registerWebsocketCommandLedgerEntry(message, outbound, outbound.createdAt as Long)
    interfaces.webSocket.sendMessage(message)
    if(metadata?.kind == 'audioClip') { audioClipCommandSent(metadata.correlationId as String) }
    return true
  } catch(Exception e) {
    removeWebsocketCommandLedgerEntryById(outbound.ledgerEntryId?.toString(), 'send threw')
    queueWebsocketMessage(message, 1, metadata)
    setWebSocketStatus('closed')
    logWarn("WebSocket send failed; queued message for retry: ${e.message}")
    scheduleWebSocketReconnect()
    return false
  }
}

void initializeWebsocketConnection() { wsConnect() }
void renewWebsocketConnection() {
  if(!isWebsocketConnected()) { initializeWebsocketConnection() }
}
// =============================================================================
// End Websocket Connection and Initialization
// =============================================================================



// =============================================================================
// Websocket Subscriptions and polling
// =============================================================================

void refreshUpnpMediaCatalogs() {
  if(getFavoritesChild() != null) { getFavorites() }
  if(getPlaylistChild() != null) { getPlaylists() }
}

Boolean hasFavoriteOrPlaylistChild() { return getFavoritesChild() != null || getPlaylistChild() != null }

void handleContentDirectoryPayload(String xmlBody) {
  GPathResult root = new XmlSlurper().parseText(xmlBody)
  Boolean favoritesChanged = false
  Boolean playlistsChanged = false
  for(Object property : root.children()) {
    if(!(property instanceof GPathResult)) { continue }
    for(Object value : ((GPathResult)property).children()) {
      if(!(value instanceof GPathResult)) { continue }
      String name = xmlLocalName((GPathResult)value)
      if(name == 'FavoritesUpdateID') { favoritesChanged = true }
      if(name == 'SavedQueuesUpdateID') { playlistsChanged = true }
    }
  }
  if(favoritesChanged && getFavoritesChild() != null) { getFavorites() }
  if(playlistsChanged && getPlaylistChild() != null) { getPlaylists() }
}

@CompileStatic
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
void getPlaybackMetadataStatus() {
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('Cannot query playback metadata: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, AVTransport, 'GetPositionInfo')
  postAsyncHttp('playbackMetadataSoapCallback', params)
}

void playbackMetadataSoapCallback(AsyncResponse response, Map data = null) {
  if(response?.status != 200 || response.hasError()) {
    logWarn("UPnP playback-metadata query failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  try {
    GPathResult envelope = new XmlSlurper().parseText(response.getData()?.toString() ?: '')
    String metadata = findSoapBodyField(envelope, 'TrackMetaData')
    Map snapshot = [hasTrack: true, track: findSoapBodyField(envelope, 'Track'),
        hasCurrentTrackURI: true, currentTrackURI: findSoapBodyField(envelope, 'TrackURI'),
        hasCurrentTrackDuration: true, currentTrackDuration: findSoapBodyField(envelope, 'TrackDuration'),
        hasCurrentTrackMetaData: true, currentTrackMetaDataRaw: metadata,
        currentTrackMetaData: parseAvtMetaData(metadata), observedAt: now()]
    Map prior = runtimeState.lastTransportPlaybackSnapshot instanceof Map
        ? (Map)runtimeState.lastTransportPlaybackSnapshot : [:]
    snapshot.transportState = prior.transportState
    snapshot.enqueuedTransportURI = prior.enqueuedTransportURI
    applyAvtSnapshotIfDifferent(snapshot)
  } catch(Exception e) {
    logWarn("Could not parse UPnP playback-metadata response: ${e.message}")
  }
}

@CompileStatic
void getPlaybackStatus() {
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('Cannot query playback status: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, AVTransport, 'GetTransportInfo')
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  postAsyncHttp('transportStatusSoapCallback', params,
      active != null && active.statusQueryPending == true ? [transportCommandId: active.id] : [:])
}

void transportStatusSoapCallback(AsyncResponse response, Map data = null) {
  String commandId = data?.transportCommandId?.toString()
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(commandId && (active == null || active.id?.toString() != commandId)) { return }
  if(response?.status != 200 || response.hasError()) {
    logWarn("UPnP playback-status query failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    if(active != null && (!commandId || active.id?.toString() == commandId)) {
      active.statusQueryPending = false
      active.lastFailureReason = 'UPnP GetTransportInfo failed'
    }
    return
  }
  try {
    GPathResult envelope = new XmlSlurper().parseText(response.getData()?.toString() ?: '')
    String transportState = findSoapBodyField(envelope, 'CurrentTransportState')
    String playbackState = transportState == 'PLAYING' ? 'PLAYBACK_STATE_PLAYING' :
        (transportState == 'PAUSED_PLAYBACK' ? 'PLAYBACK_STATE_PAUSED' :
        (transportState == 'STOPPED' ? 'PLAYBACK_STATE_IDLE' : 'PLAYBACK_STATE_BUFFERING'))
    state.playbackState = transportState
    state.playbackStateObservedAt = now()
    observeTransportPlaybackStatus([playbackState: playbackState])
  } catch(Exception e) {
    logWarn("Could not parse UPnP playback-status response: ${e.message}")
  }
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
      statusQueryPending: false]
  runtimeState[TRANSPORT_ACTIVE_KEY] = active
  sendTransportCommandAttempt(active)
}

void sendTransportCommandAttempt(Map active) {
  if(active == null || runtimeState[TRANSPORT_ACTIVE_KEY]?.id != active.id) { return }
  String commandName = active.command as String
  String action = commandName == 'skipToNextTrack' ? 'Next' :
      (commandName == 'skipToPreviousTrack' ? 'Previous' : commandName.capitalize())
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) {
    active.lastFailureReason = 'local UPnP host is unavailable'
    finishTransportCommand(active, false, active.lastFailureReason as String)
    return
  }
  Map params = getSoapActionParams(host, AVTransport, action)
  active.attempts = ((active.attempts ?: 0) as Integer) + 1
  active.statusQueryPending = false
  active.sentAt = now()
  logDebug("Sending UPnP transport command ${commandName} as ${action}, attempt ${active.attempts} (${active.id})")
  try {
    postSonosSoapAsync('transportCommandSoapCallback', params,
        [transportCommandId: active.id, action: action, command: commandName])
    runIn(TRANSPORT_WATCHDOG_SECONDS, 'watchTransportCommand', [overwrite: true,
        data: [commandId: active.id]])
  } catch(Exception e) {
    active.lastFailureReason = "UPnP ${action} request could not be sent: ${e.message}"
    retryUnconfirmedTransportCommand([commandId: active.id])
  }
}

void transportCommandSoapCallback(AsyncResponse response, Map data = null) {
  String commandId = data?.transportCommandId?.toString()
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || !commandId || active.id?.toString() != commandId) { return }
  if(response?.status == 200 && !response.hasError()) {
    transportCommandSoapAcknowledged(commandId, "UPnP ${data?.action}")
    return
  }
  active.lastFailureReason = "UPnP ${data?.action} failed (HTTP ${response?.status}): ${response?.getErrorMessage()}"
  logWarn("Transport command ${data?.command} (${commandId}) failed: ${active.lastFailureReason}")
  retryUnconfirmedTransportCommand([commandId: commandId])
}

void transportCommandSoapAcknowledged(String commandId, String response) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  if(active == null || !commandId || active.id?.toString() != commandId) { return }
  active.soapAcknowledged = true
  active.acknowledgedResponse = response
  active.acknowledgedAt = now()
  unschedule('retryUnconfirmedTransportCommand')
  logDebug("Transport command ${active.command} (${commandId}) received successful UPnP response=${response}; suppressing resend attempts")
}

void watchTransportCommand(Map data = [:]) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  String commandId = data?.commandId?.toString()
  if(active == null || active.id?.toString() != commandId) { return }
  active.statusQueryPending = true
  logDebug("Watchdog querying playback status for ${active.command} (${commandId})")
  getPlaybackStatus()
}

void retryUnconfirmedTransportCommand(Map data = [:]) {
  Map active = runtimeState[TRANSPORT_ACTIVE_KEY] instanceof Map
      ? (Map)runtimeState[TRANSPORT_ACTIVE_KEY] : null
  String commandId = data?.commandId?.toString()
  if(active == null || active.id?.toString() != commandId) { return }
  if(active.soapAcknowledged == true) {
    String reason = active.lastFailureReason?.toString() ?:
        'no confirming playback status arrived after the successful UPnP response'
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
  if(active == null) { return }
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
  if(!succeeded && active.soapAcknowledged != true &&
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
  else if(active.soapAcknowledged == true) {
    logWarn("Transport command ${active.command} (${active.id}) received a successful UPnP response but was not confirmed by playback status; no retry: ${detail}")
  }
  else { logWarn("Transport command ${active.command} (${active.id}) failed: ${detail}") }
  runIn(1, 'drainTransportCommandQueue', [overwrite: true])
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
  if(!(playModes instanceof Map) || playModes.isEmpty()) { return }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('Cannot set Sonos play modes: local UPnP host is unavailable'); return }
  Boolean repeat = playModes.containsKey('repeat') ? playModes.repeat as Boolean : getCurrentRepeatAllMode() == 'on'
  Boolean repeatOne = playModes.containsKey('repeatOne') ? playModes.repeatOne as Boolean : getCurrentRepeatOneMode() == 'on'
  Boolean shuffle = playModes.containsKey('shuffle') ? playModes.shuffle as Boolean : getCurrentShuffleMode() == 'on'
  String newPlayMode = shuffle ? (repeatOne ? 'SHUFFLE_REPEAT_ONE' : (repeat ? 'SHUFFLE' : 'SHUFFLE_NOREPEAT')) :
      (repeatOne ? 'REPEAT_ONE' : (repeat ? 'REPEAT_ALL' : 'NORMAL'))
  Map params = getSoapActionParams(host, AVTransport, 'SetPlayMode', [NewPlayMode: newPlayMode])
  postSonosSoapAsync('localControlCallback', params)
  if(playModes.containsKey('crossfade')) {
    Map crossfadeParams = getSoapActionParams(host, AVTransport, 'SetCrossfadeMode',
        [CrossfadeMode: (playModes.crossfade as Boolean) ? 1 : 0])
    postSonosSoapAsync('localControlCallback', crossfadeParams)
  }
}

@CompileStatic
void playerSetPlayerVolume(Integer volume) {
  String host = getDeviceDataValue('localUpnpHost')
  if(!host) { logWarn('Cannot set player volume: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, RenderingControl, 'SetVolume', [DesiredVolume: volume])
  postSonosSoapAsync('localControlCallback', params)
}

@CompileStatic
void playerSetPlayerMute(Boolean muted) {
  String host = getDeviceDataValue('localUpnpHost')
  if(!host) { logWarn('Cannot set player mute: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, RenderingControl, 'SetMute', [DesiredMute: muted])
  postSonosSoapAsync('localControlCallback', params)
}

@CompileStatic
void playerSetPlayerRelativeVolume(Integer volumeDelta) {
  String host = getDeviceDataValue('localUpnpHost')
  if(!host) { logWarn('Cannot adjust player volume: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, RenderingControl, 'SetRelativeVolume', [Adjustment: volumeDelta])
  postSonosSoapAsync('localControlCallback', params)
}

@CompileStatic
void playerSetGroupVolume(Integer volume) {
  componentSetGroupLevelLocal(new BigDecimal(volume))
}

@CompileStatic
void playerSetGroupMute(Boolean muted) {
  componentMuteGroupLocal(muted)
}

@CompileStatic
void playerSetGroupRelativeVolume(Integer volumeDelta) {
  componentSetGroupRelativeLevelLocal(volumeDelta)
}

@CompileStatic
void getFavorites() {
  if(getDriverStateValue('favoritesBrowseRequestId')) { return }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) {
    logWarn('Cannot retrieve Sonos favorites: local UPnP host is unavailable')
    return
  }
  Map params = getSoapActionParams(host, ContentDirectory, 'Browse', [ObjectID: 'FV:2',
      BrowseFlag: 'BrowseDirectChildren', Filter: '*', StartingIndex: 0, RequestedCount: 100,
      SortCriteria: ''])
  String requestId = UUID.randomUUID().toString()
  setDriverStateValue('favoritesBrowseRequestId', requestId)
  setDriverStateValue('favoritesBrowseItems', [])
  logDebug("Requesting Sonos favorites through ContentDirectory Browse; requestId=${requestId}")
  try {
    postAsyncHttp('favoritesBrowseCallback', params, [requestId: requestId, host: host, startingIndex: 0])
  } catch(Exception e) {
    removeDriverStateValue('favoritesBrowseRequestId')
    removeDriverStateValue('favoritesBrowseItems')
    logWarn("Could not request Sonos favorites through UPnP: ${e.message}")
  }
}

void favoritesBrowseCallback(AsyncResponse response, Map data = null) {
  if(data?.requestId?.toString() != getDriverStateValue('favoritesBrowseRequestId')?.toString()) { return }
  if(response?.status != 200 || response.hasError()) {
    removeDriverStateValue('favoritesBrowseRequestId')
    removeDriverStateValue('favoritesBrowseItems')
    logWarn("Sonos favorites UPnP Browse failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  try {
    GPathResult envelope = new XmlSlurper().parseText(response.getData()?.toString() ?: '')
    String resultXml = findSoapBodyField(envelope, 'Result')
    String totalText = findSoapBodyField(envelope, 'TotalMatches')
    Integer total = totalText?.isInteger() ? Integer.parseInt(totalText) : 0
    String returnedText = findSoapBodyField(envelope, 'NumberReturned')
    Integer returned = returnedText?.isInteger() ? Integer.parseInt(returnedText) : 0
    GPathResult didl = resultXml ? new XmlSlurper().parseText(resultXml) : null
    List<Map> favorites = []
    if(didl != null) {
      for(GPathResult item : (GPathResult)didl.children()) {
        if(item.name()?.toString() != 'item') { continue }
        String favoriteObjectId = xmlAttribute(item, 'id')
        String favoriteId = favoriteObjectId?.startsWith('FV:2/')
            ? favoriteObjectId.substring('FV:2/'.length()) : null
        String uri = findQueueXmlText(item, 'res')
        String resMetadata = findQueueXmlText(item, 'resMD')
        String title = findQueueXmlText(item, 'title')
        String description = findQueueXmlText(item, 'description')
        String imageUrl = findQueueXmlText(item, 'albumArtURI')
        String resourceObjectId = null
        if(resMetadata) {
          try {
            GPathResult inner = new XmlSlurper().parseText(resMetadata)
            GPathResult innerItem = findDirectXmlChild(inner, 'item')
            resourceObjectId = xmlAttribute(innerItem, 'id')
          } catch(Exception ignored) {
            logDebug("Favorite ${favoriteId ?: '(unknown)'} has unparseable embedded UPnP metadata")
          }
        }
        String serviceId = uri?.contains('?sid=') ? uri.substring(uri.indexOf('?sid=') + 5).split('&')[0] : null
        String accountId = uri?.contains('&sn=') ? "sn_${uri.substring(uri.indexOf('&sn=') + 4).split('&')[0]}" : null
        String matchObjectId = uri?.contains(':') ? uri.substring(uri.indexOf(':') + 1).split('\\?')[0] : resourceObjectId
        matchObjectId = matchObjectId?.replaceFirst(/(?i)\\.mp4$/, '')
        matchObjectId = matchObjectId?.replace('%3a', ':')?.replace('%3A', ':')
        if(matchObjectId?.contains(':')) { matchObjectId = matchObjectId.substring(matchObjectId.indexOf(':') + 1) }
        favorites.add([id: favoriteId, name: title, imageUrl: imageUrl, description: description,
            service: description, upnpFavoriteObjectId: favoriteObjectId, upnpUri: uri,
            upnpMetadata: resMetadata, resourceObjectId: resourceObjectId,
            serviceId: serviceId, accountId: accountId, matchObjectId: matchObjectId,
            type: findQueueXmlText(item, 'type'), details: [id: favoriteId, name: title,
              imageUrl: imageUrl, description: description, resource: [id: [objectId: matchObjectId,
                serviceId: serviceId, accountId: accountId]], upnpFavoriteObjectId: favoriteObjectId,
              upnpUri: uri, upnpMetadata: resMetadata]])
      }
    }
    List<Map> accumulated = getDriverStateValue('favoritesBrowseItems') instanceof List
        ? (List<Map>)getDriverStateValue('favoritesBrowseItems') : []
    accumulated.addAll(favorites)
    setDriverStateValue('favoritesBrowseItems', accumulated)
    Integer startingIndex = data?.startingIndex != null ? (data.startingIndex as Integer) : 0
    if(returned > 0 && total > startingIndex + returned) {
      Integer nextIndex = startingIndex + returned
      Map nextParams = getSoapActionParams(data.host?.toString(), ContentDirectory, 'Browse',
          [ObjectID: 'FV:2', BrowseFlag: 'BrowseDirectChildren', Filter: '*',
           StartingIndex: nextIndex, RequestedCount: 100, SortCriteria: ''])
      asynchttpPost('favoritesBrowseCallback', nextParams,
          [requestId: requestId, host: data.host, startingIndex: nextIndex])
      return
    }
    cacheUpnpFavorites(accumulated)
    removeDriverStateValue('favoritesBrowseRequestId')
    removeDriverStateValue('favoritesBrowseItems')
    logInfo("Loaded ${accumulated.size()} Sonos favorites through UPnP (total=${total}, updateId=${findSoapBodyField(envelope, 'UpdateID') ?: 'unknown'})")
    completePendingRandomMediaLoads('favorite')
  } catch(Exception e) {
    removeDriverStateValue('favoritesBrowseRequestId')
    removeDriverStateValue('favoritesBrowseItems')
    logWarn("Could not parse Sonos favorites UPnP Browse response: ${e.message}")
  }
}

void cacheUpnpFavorites(List<Map> favorites) {
  clearFavoritesMap()
  String html = '<html><body><div class="favorites-container">'
  favorites?.each { Map favorite ->
    String favoriteId = favorite?.id?.toString()
    String matchObjectId = favorite?.matchObjectId?.toString()
    String serviceId = favorite?.serviceId?.toString()
    String accountId = favorite?.accountId?.toString()
    if(favoriteId && matchObjectId) {
      getFavoritesMap()["${matchObjectId}${serviceId ?: ''}${accountId ?: ''}"] = favorite
    }
    if(favoriteId) {
      String onclick = buildHubitatRunMethodOnclick(device.getIdAsLong(), 'loadFavorite', [favoriteId])
      String name = StringEscapeUtils.escapeHtml4(favorite?.name?.toString() ?: "Favorite ${favoriteId}")
      String image = favorite?.imageUrl?.toString()
      html += "<div onclick=\"${onclick}\">${image ? '<img src=\"' + image + '\" width=\"120\" />' : ''}<span>Favorite #${favoriteId} ${name}</span></div>"
    }
  }
  html += '</div></body></html>'
  setChildFavs(html)
  rematchLatestUpnpPlaybackSnapshot()
}

@CompileStatic
void getPlaylists() {
  if(getDriverStateValue('playlistsBrowseRequestId')) { return }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('Cannot retrieve Sonos Playlists: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, ContentDirectory, 'Browse', [ObjectID: 'SQ:',
      BrowseFlag: 'BrowseDirectChildren', Filter: '*', StartingIndex: 0, RequestedCount: 100,
      SortCriteria: ''])
  String requestId = UUID.randomUUID().toString()
  setDriverStateValue('playlistsBrowseRequestId', requestId)
  setDriverStateValue('playlistsBrowseItems', [])
  try {
    postAsyncHttp('playlistsBrowseCallback', params, [requestId: requestId, host: host, startingIndex: 0])
  } catch(Exception e) {
    removeDriverStateValue('playlistsBrowseRequestId')
    removeDriverStateValue('playlistsBrowseItems')
    logWarn("Could not request Sonos Playlists through UPnP: ${e.message}")
  }
}

void playlistsBrowseCallback(AsyncResponse response, Map data = null) {
  if(data?.requestId?.toString() != getDriverStateValue('playlistsBrowseRequestId')?.toString()) { return }
  if(response?.status != 200 || response.hasError()) {
    removeDriverStateValue('playlistsBrowseRequestId')
    removeDriverStateValue('playlistsBrowseItems')
    logWarn("Sonos Playlists UPnP Browse failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  try {
    GPathResult envelope = new XmlSlurper().parseText(response.getData()?.toString() ?: '')
    String resultXml = findSoapBodyField(envelope, 'Result')
    String totalText = findSoapBodyField(envelope, 'TotalMatches')
    Integer total = totalText?.isInteger() ? Integer.parseInt(totalText) : 0
    String returnedText = findSoapBodyField(envelope, 'NumberReturned')
    Integer returned = returnedText?.isInteger() ? Integer.parseInt(returnedText) : 0
    GPathResult didl = resultXml ? new XmlSlurper().parseText(resultXml) : null
    List<Map> page = []
    if(didl != null) {
      for(GPathResult item : (GPathResult)didl.children()) {
        if(item.name()?.toString() != 'container' && item.name()?.toString() != 'item') { continue }
        String objectId = xmlAttribute(item, 'id')
        String playlistId = objectId?.startsWith('SQ:') ? objectId.substring(3) : objectId
        if(!playlistId) { continue }
        String uri = findQueueXmlText(item, 'res')
        String metadata = findQueueXmlText(item, 'resMD')
        String name = findQueueXmlText(item, 'title')
        String imageUrl = findQueueXmlText(item, 'albumArtURI')
        page.add([id: playlistId, objectId: objectId, name: name,
            imageUrl: imageUrl, upnpUri: uri, upnpMetadata: metadata,
            details: [id: playlistId, name: name, imageUrl: imageUrl, objectId: objectId,
              upnpUri: uri, upnpMetadata: metadata]])
      }
    }
    List<Map> accumulated = getDriverStateValue('playlistsBrowseItems') instanceof List
        ? (List<Map>)getDriverStateValue('playlistsBrowseItems') : []
    accumulated.addAll(page)
    setDriverStateValue('playlistsBrowseItems', accumulated)
    Integer startingIndex = data?.startingIndex != null ? (data.startingIndex as Integer) : 0
    if(returned > 0 && total > startingIndex + returned) {
      Integer nextIndex = startingIndex + returned
      Map nextParams = getSoapActionParams(data.host?.toString(), ContentDirectory, 'Browse',
          [ObjectID: 'SQ:', BrowseFlag: 'BrowseDirectChildren', Filter: '*',
           StartingIndex: nextIndex, RequestedCount: 100, SortCriteria: ''])
      asynchttpPost('playlistsBrowseCallback', nextParams,
          [requestId: data.requestId, host: data.host, startingIndex: nextIndex])
      return
    }
    clearPlaylistsMap()
    String html = '<html><body><div class="playlists-container">'
    accumulated.each { Map playlist ->
      String playlistId = playlist.id?.toString()
      getPlaylistsMap()[playlistId] = playlist
      String onclick = buildHubitatRunMethodOnclick(device.getIdAsLong(), 'loadPlaylist', [playlistId])
      html += "<div onclick=\"${onclick}\"><span>${StringEscapeUtils.escapeHtml4(playlist.name?.toString() ?: "Playlist ${playlistId}")}</span></div>"
    }
    html += '</div></body></html>'
    setChildPlaylists(html)
    removeDriverStateValue('playlistsBrowseRequestId')
    removeDriverStateValue('playlistsBrowseItems')
    logInfo("Loaded ${accumulated.size()} Sonos Playlists through UPnP (total=${total}, updateId=${findSoapBodyField(envelope, 'UpdateID') ?: 'unknown'})")
    rematchLatestUpnpPlaybackSnapshot()
    completePendingRandomMediaLoads('playlist')
  } catch(Exception e) {
    removeDriverStateValue('playlistsBrowseRequestId')
    removeDriverStateValue('playlistsBrowseItems')
    logWarn("Could not parse Sonos Playlists UPnP Browse response: ${e.message}")
  }
}

void rematchLatestUpnpPlaybackSnapshot() {
  String dni = device.getDeviceNetworkId()?.toString()
  // App owns playback storage; use only the transient runtime snapshot here.
  Map snapshot = null
  if(runtimeState.lastTransportPlaybackSnapshot instanceof Map) {
    snapshot = (Map)runtimeState.lastTransportPlaybackSnapshot
  }
  if(snapshot != null) {
    logDebug("Rechecking favorite/playlist match from cached UPnP playback snapshot for ${dni}")
    updateCurrentFavoriteFromUpnp(snapshot)
  }
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
  Map<String, Boolean> playModes = buildRequestedPlayModes(repeat, repeatOne, shuffle, crossfade)
  Map request = [favoriteId: favoriteId, action: action, playModes: playModes,
      playOnCompletion: playOnCompletion, groupId: groupId, attempts: 0]
  loadFavoriteOverUpnp(request)

  // UPnP SetAVTransportURI is followed by Play only after the SOAP action succeeds.
}

void loadFavoriteOverUpnp(Map request) {
  if(!(request instanceof Map)) { return }
  String favoriteId = request.favoriteId?.toString()
  Map favorite = findFavoriteById(favoriteId)
  if(favorite == null) {
    Integer attempts = (request.attempts ?: 0) as Integer
    if(attempts < 2) {
      request.attempts = attempts + 1
      logDebug("Favorite ${favoriteId ?: '(unset)'} is not cached; refreshing the UPnP favorites list before retry ${request.attempts}")
      getFavorites()
      runIn(3, 'loadFavoriteOverUpnp', [overwrite: true, data: new LinkedHashMap(request)])
    } else {
      logWarn("Cannot load favorite ${favoriteId ?: '(unset)'}: it was not found in the UPnP favorites list")
    }
    return
  }
  String uri = favorite.upnpUri?.toString()
  String metadata = favorite.upnpMetadata?.toString()
  if(!uri || !metadata) {
    logWarn("Cannot load favorite ${favoriteId}: its UPnP Browse entry has no usable resource URI or metadata")
    return
  }
  String action = request.action?.toString()?.toUpperCase(Locale.ROOT) ?: 'REPLACE'
  if(action == 'INSERT') { action = 'INSERT_NEXT' }
  if(!(action in ['REPLACE', 'APPEND', 'INSERT_NEXT'])) {
    logWarn("UPnP favorite loading does not yet support queue action ${action}; favorite ${favoriteId} was not sent")
    return
  }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn("Cannot load favorite ${favoriteId}: local UPnP host is unavailable"); return }
  Map<String, Boolean> playModes = request.playModes instanceof Map
      ? (Map<String, Boolean>)request.playModes : [:]
  if(!playModes.isEmpty()) { playerSetPlayModes(playModes) }
  Map params
  if(action == 'REPLACE') {
    params = getSoapActionParams(host, AVTransport, 'SetAVTransportURI',
        [CurrentURI: uri, CurrentURIMetaData: metadata])
  } else {
    params = getSoapActionParams(host, AVTransport, 'AddURIToQueue', [EnqueuedURI: uri,
        EnqueuedURIMetaData: metadata, DesiredFirstTrackNumberEnqueued: 0,
        EnqueueAsNext: action == 'INSERT_NEXT'])
  }
  String requestId = UUID.randomUUID().toString()
  state.favoriteLoadRequestId = requestId
  logInfo("Loading Sonos favorite ${favoriteId} (${favorite.name ?: 'unnamed'}) through UPnP; action=${action}")
  try {
    postSonosSoapAsync('favoriteLoadSoapCallback', params, [requestId: requestId,
        favoriteId: favoriteId, action: action, playOnCompletion: request.playOnCompletion == true])
  } catch(Exception e) {
    state.remove('favoriteLoadRequestId')
    logWarn("Could not send UPnP load for favorite ${favoriteId}: ${e.message}")
  }
}

void favoriteLoadSoapCallback(AsyncResponse response, Map data = null) {
  if(data?.requestId?.toString() != state.favoriteLoadRequestId?.toString()) { return }
  state.remove('favoriteLoadRequestId')
  Map retryState = favoriteRetryState.get(device.getDeviceNetworkId())
  Boolean accepted = response?.status == 200 && !response.hasError()
  if(retryState != null && retryState.favoriteId?.toString() == data?.favoriteId?.toString()) {
    retryState.loadAcknowledged = accepted
    if(!accepted) {
      retryState.loadErrorCode = 'UPNP_LOAD_FAILED'
      retryState.loadErrorReason = response?.getErrorMessage()?.toString()
    }
  }
  emitGroupFavoriteOperationEvent('favoriteLoadAck', [success: accepted,
      errorCode: accepted ? null : 'UPNP_LOAD_FAILED', reason: response?.getErrorMessage()?.toString(),
      groupId: getGroupId()])
  if(response?.status != 200 || response.hasError()) {
    logWarn("UPnP load of favorite ${data?.favoriteId} failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  logInfo("Sonos accepted UPnP load of favorite ${data?.favoriteId}")
  if(data?.playOnCompletion == true) { playerPlay() }
}

void scheduleAmazonMusicAutoPlay() {
  runIn(3, 'playerPlay', [overwrite: true])
}

void cancelPendingAmazonMusicAutoPlay() {
  unschedule('playerPlay')
}

void playerLoadPlaylist(String playlistId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  if(playOnCompletion == true) { invalidateGroupAnnouncementStream('PLAYLIST_LOAD_REQUESTED') }
  Map<String, Boolean> playModes = buildRequestedPlayModes(repeat, repeatOne, shuffle, crossfade)
  loadPlaylistOverUpnp([playlistId: playlistId, action: action, playModes: playModes,
      playOnCompletion: playOnCompletion, attempts: 0])

}

void loadPlaylistOverUpnp(Map request) {
  if(!(request instanceof Map)) { return }
  String playlistId = request.playlistId?.toString()
  Map playlist = getPlaylistsMap().get(playlistId)
  if(playlist == null) {
    Integer attempts = (request.attempts ?: 0) as Integer
    if(attempts < 2) {
      request.attempts = attempts + 1
      getPlaylists()
      runIn(3, 'loadPlaylistOverUpnp', [overwrite: true, data: new LinkedHashMap(request)])
    } else {
      reportPlaylistLoadError(playlistId, 'ERROR_NOT_FOUND', 'The playlist was not found in the UPnP saved-playlist list.')
    }
    return
  }
  String uri = playlist.upnpUri?.toString()
  String metadata = playlist.upnpMetadata?.toString()
  if(!uri || !metadata) {
    reportPlaylistLoadError(playlistId, 'ERROR_UNSUPPORTED', 'The UPnP playlist entry did not include a usable resource URI and metadata.')
    return
  }
  String action = request.action?.toString()?.toUpperCase(Locale.ROOT) ?: 'REPLACE'
  if(action == 'INSERT') { action = 'INSERT_NEXT' }
  if(!(action in ['REPLACE', 'APPEND', 'INSERT_NEXT'])) {
    reportPlaylistLoadError(playlistId, 'ERROR_UNSUPPORTED', "UPnP playlist loading does not yet support queue action ${action}.")
    return
  }
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { reportPlaylistLoadError(playlistId, 'ERROR_OFFLINE', 'Local UPnP host is unavailable.'); return }
  Map<String, Boolean> playModes = request.playModes instanceof Map
      ? (Map<String, Boolean>)request.playModes : [:]
  if(!playModes.isEmpty()) { playerSetPlayModes(playModes) }
  Map params = action == 'REPLACE'
      ? getSoapActionParams(host, AVTransport, 'SetAVTransportURI', [CurrentURI: uri, CurrentURIMetaData: metadata])
      : getSoapActionParams(host, AVTransport, 'AddURIToQueue', [EnqueuedURI: uri,
          EnqueuedURIMetaData: metadata, DesiredFirstTrackNumberEnqueued: 0,
          EnqueueAsNext: action == 'INSERT_NEXT'])
  String requestId = UUID.randomUUID().toString()
  state.playlistLoadRequestId = requestId
  postSonosSoapAsync('playlistLoadSoapCallback', params, [requestId: requestId,
      playlistId: playlistId, action: action, playOnCompletion: request.playOnCompletion == true])
}

void playlistLoadSoapCallback(AsyncResponse response, Map data = null) {
  if(data?.requestId?.toString() != state.playlistLoadRequestId?.toString()) { return }
  state.remove('playlistLoadRequestId')
  Map retryState = playlistRetryState.get(device.getDeviceNetworkId())
  if(retryState != null && retryState.playlistId?.toString() == data?.playlistId?.toString()) {
    retryState.loadAcknowledged = response?.status == 200 && !response.hasError()
    if(response?.status != 200 || response.hasError()) {
      retryState.loadErrorCode = 'UPNP_LOAD_FAILED'
      retryState.loadErrorReason = response?.getErrorMessage()?.toString()
    }
  }
  if(response?.status != 200 || response.hasError()) {
    reportPlaylistLoadError(data?.playlistId?.toString(), 'UPNP_LOAD_FAILED',
        "UPnP load failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  if(data?.playOnCompletion == true) { playerPlay() }
}

/** Explicit zero (including the -1 public-command sentinel) stays silent despite a TTS boost. */
@CompileStatic
static Integer calculateAnnouncementVolume(BigDecimal requestedVolume, Integer currentVolume, Integer boost) {
  if(requestedVolume != null && requestedVolume.compareTo(BigDecimal.valueOf(-1L)) == 0) { return 0 }
  if(requestedVolume != null && requestedVolume.compareTo(BigDecimal.ZERO) == 0) { return 0 }
  BigDecimal baseVolume = requestedVolume != null ? requestedVolume : new BigDecimal(currentVolume)
  return Math.max(0, Math.min(roundVolume(baseVolume + boost), 100))
}

@CompileStatic
static Integer roundVolume(BigDecimal volume) {
  return volume.setScale(0, RoundingMode.HALF_UP).intValue()
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
    // Leave the unfinished regular queue intact. Its head may be the active
    // clip, which must resume ahead of regular clips submitted during priority playback.
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
  if(!hasQueuedRegularAudioClip() && getChimeBeforeStandardPriorityTTS()) {playerLoadAudioClipChime(volume)}
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
  if(!hasQueuedRegularAudioClip() && shouldChime) {
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
  return highPriority ? getDeviceAudioClipQueueHighPriority().isEmpty() : !hasQueuedRegularAudioClip()
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
  if(!prepareNextAudioClipForDispatch()) { return }
  dispatchPreparedAudioClip()
}

Boolean prepareNextAudioClipForDispatch() {
  traceAudioClipQueues('dequeue-entry')
  logDebug('dequeueAudioClip')

  Boolean alreadyPlaying = runtimeState.audioClipPlaying == true
  Map activeClip = (Map)getAtomicStateValue('activeAudioClipMessage')
  ConcurrentLinkedQueue<Map> highPriorityQueue = getDeviceAudioClipQueueHighPriority()
  Boolean preemptingRegularClip = alreadyPlaying && activeClip?.priority != 'HIGH' && !highPriorityQueue.isEmpty()
  if(alreadyPlaying && !preemptingRegularClip) {
    logDebug("Ignoring redundant audio clip dequeue while ${activeClip?.priority == 'HIGH' ? 'high-priority' : 'regular'} clip ${runtimeState.activeAudioClipCorrelationId ?: 'unknown'} is active")
    return false
  }

  Map synchronizedHold = runtimeState.groupSynchronizedAudioClipHold instanceof Map
      ? (Map)runtimeState.groupSynchronizedAudioClipHold : null
  if(synchronizedHold != null) {
    if(synchronizedHold.allowChimeDispatch == true) {
      synchronizedHold.allowChimeDispatch = false
      runtimeState.groupSynchronizedAudioClipHold = synchronizedHold
    } else {
      logDebug("Holding queued audioClip for synchronized group TTS ${synchronizedHold.correlationId}")
      return false
    }
  }

  // Check high-priority queue first
  Map clipMessage = highPriorityQueue.poll()
  if(preemptingRegularClip && !clipMessage) {
    logDebug('High-priority audio clip was removed before dispatch; leaving the active regular clip unchanged')
    return false
  }

  // Keep regular clips in the FIFO until their playback reaches a terminal state.
  if(!clipMessage) {
    clipMessage = getDeviceAudioClipQueue().peek()
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
    return false
  }

  Integer previousDispatchCount = (clipMessage.dispatchCount ?: 0) as Integer
  if(previousDispatchCount > 0) {
    rotateAudioClipCorrelationId(clipMessage)
  }
  clipMessage.dispatchCount = previousDispatchCount + 1

  // The regular queue head owns any unfinished regular clip. Keep this marker
  // through high-priority playback so diagnostics and interruption state remain clear.
  if(clipMessage?.priority != 'HIGH') {
    setHiddenJsonRuntimeState('currentlyPlayingClip', clipMessage)
  }

  runtimeState.activeAudioClipCorrelationId = clipMessage.correlationId
  setHiddenJsonRuntimeState('activeAudioClipMessage', clipMessage)
  runtimeState.remove('activeAudioClipSonosId')
  runtimeState.audioClipCommandSent = false
  runtimeState.audioClipHadDisconnectAfterSend = false
  Map<String, Map> clipProgress = getAudioClipProgressByCorrelationId() instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)getAudioClipProgressByCorrelationId())
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
  logDebug("Audio clip dispatch selected correlation=${clipMessage.correlationId}, priority=${clipMessage.priority ?: 'REGULAR'}, dispatchCount=${clipMessage.dispatchCount}, regularQueue=${getDeviceAudioClipQueue().collect { Map clip -> clip.correlationId }}, highQueue=${highPriorityQueue.collect { Map clip -> clip.correlationId }}")
  runtimeState.audioClipPlaying = true
  traceAudioClipQueues('dispatch-prepared')
  if(!isCurrentlySubcribedToAudioClipWS()) { subscribeToAudioClip() }
  return true
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
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('Cannot query Sonos groups: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, ZoneGroupTopology, 'GetZoneGroupState')
  logDebug('Querying Sonos group topology through ZoneGroupTopology.GetZoneGroupState')
  postAsyncHttp('zoneGroupTopologySoapCallback', params)
}

void zoneGroupTopologySoapCallback(AsyncResponse response, Map data = null) {
  if(response?.status != 200 || response.hasError()) {
    logWarn("UPnP group topology query failed (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  try {
    GPathResult envelope = new XmlSlurper().parseText(response.getData()?.toString() ?: '')
    String zoneGroupState = findSoapBodyField(envelope, 'ZoneGroupState')
    if(!isPlausibleXmlPayload(zoneGroupState)) {
      logWarn('UPnP group topology response did not contain ZoneGroupState XML')
      return
    }
    String propertyset = '<propertyset><property><ZoneGroupState>' +
        StringEscapeUtils.escapeXml10(zoneGroupState) + '</ZoneGroupState></property></propertyset>'
    Map topology = parseZgtFullSnapshot(propertyset)
    if(!(topology?.groups instanceof List)) {
      logWarn('Could not parse groups from UPnP ZoneGroupState response')
      return
    }
    deferOrProcessZoneGroupTopologyPayload(device.getDeviceNetworkId()?.toString() ?: 'unknown', propertyset)
    List<Map> groups = (List<Map>)topology.groups
    List<Map> ledgerGroups = groups.collect { Map group ->
      List<Map> members = group.members instanceof List ? (List<Map>)group.members : []
      [id: group.groupId, name: group.groupName, coordinatorId: group.coordinatorId,
       playerIds: members.findAll { Map member -> member.invisible != true }.collect { Map member -> member.playerId?.toString() }]
    }
    Map current = ledgerGroups.find { Map group -> (group.playerIds as List).contains(getId()) }
    if(current != null) {
      emitGroupFavoriteOperationEvent('groups', [groupId: current.id, coordinatorId: current.coordinatorId,
        playerIds: current.playerIds ?: []])
    }
    logInfo("Loaded ${groups.size()} Sonos groups through UPnP ZoneGroupTopology")
  } catch(Exception e) {
    logWarn("Could not parse UPnP group topology response: ${e.message}")
  }
}

@CompileStatic
void playerCreateNewGroup() {
  String host = getLocalUpnpHostForGroupOperation()
  if(!host) { logWarn('Cannot create a standalone Sonos group: local UPnP host is unavailable'); return }
  Map params = getSoapActionParams(host, AVTransport, 'BecomeCoordinatorOfStandaloneGroup')
  logInfo("Creating a standalone Sonos group for ${getId()} through AVTransport")
  postSonosSoapAsync('groupControlSoapCallback', params, [operation: 'createStandalone', playerId: getId()])
}

@CompileStatic
void playerCreateGroup(List<String> playerIds, String musicContextGroupId = null) {
  List<String> requestedPlayers = (playerIds ?: []).findAll { String id -> id }.unique()
  String coordinatorId = requestedPlayers ? requestedPlayers[0] : getId()
  if(coordinatorId != getId()) {
    logWarn("Cannot create group with coordinator ${coordinatorId} from player ${getId()}; invoke the selected coordinator")
    return
  }
  if(requestedPlayers.size() <= 1) {
    playerCreateNewGroup()
    return
  }
  if(musicContextGroupId) {
    logDebug("UPnP group formation uses the selected coordinator playback context; musicContextGroupId=${musicContextGroupId}")
  }
  List<String> currentMembers = getGroupPlayerIds()
  List<String> toRemove = currentMembers.findAll { String id -> !requestedPlayers.contains(id) }
  List<String> toAdd = requestedPlayers.findAll { String id -> id != coordinatorId && !currentMembers.contains(id) }
  playerModifyGroupMembers(toAdd, toRemove)
}

/** Hubitat command wrapper for app-originated topology calls. */
void playerCreateGroupRequest(String requestJson) {
  Map request = parseTopologyCommandRequest(requestJson)
  List args = request.args instanceof List ? (List)request.args : []
  List<String> playerIds = args && args[0] instanceof List
      ? ((List)args[0]).collect { Object id -> id?.toString() }.findAll { String id -> id } : []
  String musicContextGroupId = args.size() > 1 ? args[1]?.toString() : null
  playerCreateGroup(playerIds, musicContextGroupId)
}

/** Hubitat command wrapper for app-originated topology calls. */
void playerModifyGroupMembersRequest(String requestJson) {
  Map request = parseTopologyCommandRequest(requestJson)
  List args = request.args instanceof List ? (List)request.args : []
  List<String> playerIdsToAdd = args && args[0] instanceof List
      ? ((List)args[0]).collect { Object id -> id?.toString() }.findAll { String id -> id } : []
  List<String> playerIdsToRemove = args.size() > 1 && args[1] instanceof List
      ? ((List)args[1]).collect { Object id -> id?.toString() }.findAll { String id -> id } : []
  Map<String, Map> expectedTopology = args.size() > 2 && args[2] instanceof Map
      ? (Map<String, Map>)args[2] : null
  playerModifyGroupMembers(playerIdsToAdd, playerIdsToRemove, expectedTopology)
}

Map parseTopologyCommandRequest(String requestJson) {
  if(!requestJson) { return [:] }
  try {
    Object parsed = new JsonSlurper().parseText(requestJson)
    return parsed instanceof Map ? (Map)parsed : [:]
  } catch(Exception e) {
    logWarn("Ignoring invalid topology command request: ${e.message}")
    return [:]
  }
}

@CompileStatic
void sendCreateGroupRequest(List<String> playerIds, String musicContextGroupId = null) {
  playerCreateGroup(playerIds, musicContextGroupId)
}

@CompileStatic
void playerModifyGroupMembers(List<String> playerIdsToAdd = [], List<String> playerIdsToRemove = [],
    Map<String, Map> expectedTopology = null) {
  modifyGroupMembersWithTopologyRetry(playerIdsToAdd, playerIdsToRemove, expectedTopology, 0)
}

@CompileStatic
private void modifyGroupMembersWithTopologyRetry(List<String> playerIdsToAdd, List<String> playerIdsToRemove,
    Map<String, Map> expectedTopology, Integer retryAttempt) {
  List<String> playersToAdd = playerIdsToAdd != null ? playerIdsToAdd : new ArrayList<String>()
  List<String> playersToRemove = playerIdsToRemove != null ? playerIdsToRemove : new ArrayList<String>()
  String coordinatorId = getGroupCoordinatorId() ?: getId()
  String coordinatorHost = getLocalUpnpHostForGroupOperation()
  if(!coordinatorHost) { logWarn('Cannot modify Sonos group: local UPnP host is unavailable'); return }

  playersToRemove.each { String playerId ->
    if(playerId == coordinatorId) {
      String delegatedCoordinator = expectedTopology?.values()?.find { Map expected ->
        expected?.coordinatorId?.toString() && expected.coordinatorId.toString() != coordinatorId
      }?.coordinatorId?.toString()
      if(!delegatedCoordinator) {
        delegatedCoordinator = playersToAdd.find { String id -> id != coordinatorId }
      }
      if(!delegatedCoordinator) {
        logWarn("Cannot remove current group coordinator ${coordinatorId} without a target coordinator")
        return
      }
      Map expectedGroup = expectedTopology?.get(delegatedCoordinator) instanceof Map
          ? (Map)expectedTopology[delegatedCoordinator]
          : (expectedTopology?.values()?.find { Map expected -> expected?.coordinatorId?.toString() == delegatedCoordinator } as Map ?: [:])
      List<String> expectedMembers = expectedGroup.memberIds instanceof List
          ? ((List)expectedGroup.memberIds).collect { Object id -> id?.toString() } : []
      Boolean rejoin = expectedMembers.contains(coordinatorId)
      Map params = getSoapActionParams(coordinatorHost, AVTransport, 'DelegateGroupCoordinationTo',
          [NewCoordinator: delegatedCoordinator, RejoinGroup: rejoin])
      logInfo("Delegating Sonos group coordination from ${coordinatorId} to ${delegatedCoordinator}; rejoin=${rejoin}")
      postSonosSoapAsync('groupControlSoapCallback', params, [operation: 'delegateCoordinator',
          playerId: coordinatorId, coordinatorId: delegatedCoordinator, rejoinGroup: rejoin])
      return
    }
    Map expectedGroup = expectedTopology?.get(playerId) instanceof Map
        ? (Map)expectedTopology[playerId]
        : (expectedTopology?.values()?.find { Map expected -> expected?.coordinatorId?.toString() == playerId } as Map ?: [:])
    if(expectedGroup.coordinatorId?.toString() == playerId && playerId != coordinatorId) {
      List<String> expectedMembers = expectedGroup.memberIds instanceof List
          ? ((List)expectedGroup.memberIds).collect { Object id -> id?.toString() } : []
      Boolean rejoin = expectedMembers.contains(coordinatorId)
      Map params = getSoapActionParams(coordinatorHost, AVTransport, 'DelegateGroupCoordinationTo',
          [NewCoordinator: playerId, RejoinGroup: rejoin])
      postSonosSoapAsync('groupControlSoapCallback', params, [operation: 'delegateCoordinator',
          playerId: coordinatorId, coordinatorId: playerId, rejoinGroup: rejoin])
      return
    }
    String targetHost = getTopologyMemberHost(playerId)
    if(!targetHost) {
      logWarn("Cannot detach ${playerId} from the Sonos group because its UPnP location is not in the topology cache")
      queueGroupMemberTopologyRetry(playersToAdd, playersToRemove, expectedTopology, retryAttempt)
      return
    }
    Map params = getSoapActionParams(targetHost, AVTransport, 'BecomeCoordinatorOfStandaloneGroup')
    logInfo("Detaching ${playerId} into a standalone Sonos group through AVTransport")
    postSonosSoapAsync('groupControlSoapCallback', params, [operation: 'detachMember', playerId: playerId])
  }

  playersToAdd.findAll { String playerId -> playerId != coordinatorId }.each { String playerId ->
    String targetHost = getTopologyMemberHost(playerId)
    if(!targetHost) {
      logWarn("Cannot add ${playerId} to ${coordinatorId}: its UPnP location is not in the topology cache")
      queueGroupMemberTopologyRetry(playersToAdd, playersToRemove, expectedTopology, retryAttempt)
      return
    }
    Map params = getSoapActionParams(targetHost, AVTransport, 'SetAVTransportURI',
        [CurrentURI: "x-rincon:${coordinatorId}", CurrentURIMetaData: ''])
    logInfo("Adding ${playerId} to Sonos group ${getGroupId()} through UPnP SetAVTransportURI")
    postSonosSoapAsync('groupControlSoapCallback', params, [operation: 'addMember',
        playerId: playerId, coordinatorId: coordinatorId])
  }
}

/** Re-fetch topology, then retry a group mutation whose sibling host was not cached yet. */
private void queueGroupMemberTopologyRetry(List<String> playersToAdd, List<String> playersToRemove,
    Map<String, Map> expectedTopology, Integer retryAttempt) {
  if(retryAttempt >= 3) {
    logError('Could not resolve a Sonos group member host after 3 topology refresh attempts')
    return
  }
  playerGetGroupsFull()
  Map data = [
    playerIdsToAdd: new ArrayList<String>(playersToAdd ?: []),
    playerIdsToRemove: new ArrayList<String>(playersToRemove ?: []),
    expectedTopology: expectedTopology ?: [:],
    retryAttempt: retryAttempt + 1
  ]
  runIn(2, 'retryGroupMemberTopologyMutation', [overwrite: true, data: data])
}

void retryGroupMemberTopologyMutation(Map data) {
  List<String> playersToAdd = data?.playerIdsToAdd instanceof List
      ? ((List)data.playerIdsToAdd).collect { Object id -> id?.toString() } : []
  List<String> playersToRemove = data?.playerIdsToRemove instanceof List
      ? ((List)data.playerIdsToRemove).collect { Object id -> id?.toString() } : []
  Map<String, Map> expectedTopology = data?.expectedTopology instanceof Map
      ? (Map<String, Map>)data.expectedTopology : [:]
  Integer retryAttempt = data?.retryAttempt instanceof Number ? ((Number)data.retryAttempt).intValue() : 1
  modifyGroupMembersWithTopologyRetry(playersToAdd, playersToRemove, expectedTopology, retryAttempt)
}

@CompileStatic
String getTopologyMemberHost(String playerId) {
  // Sibling hosts come from the transient cache (see field comment); self is always local.
  if(!playerId) { return null }
  if(playerId == getId()) { return getLocalUpnpHostForGroupOperation() }
  return zgtMemberHostByPlayerId.get(playerId)
}

@CompileStatic
void groupControlSoapCallback(AsyncResponse response, Map data = null) {
  if(response?.status != 200 || response.hasError()) {
    logWarn("UPnP group operation ${data?.operation ?: 'unknown'} failed for ${data?.playerId ?: 'unknown player'} (HTTP ${response?.status}): ${response?.getErrorMessage()}")
    return
  }
  logDebug("UPnP group operation ${data?.operation ?: 'unknown'} was accepted for ${data?.playerId ?: 'unknown player'}")
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
      'SetAVTransportURI', 'RemoveAllTracksFromQueue', 'AddURIToQueue', 'ConfigureSleepTimer',
      'SetPlayMode', 'SetCrossfadeMode']) { return 'sid1' }
  if(service.contains('GroupRenderingControl') && action in ['SetGroupVolume', 'SetRelativeGroupVolume', 'SetGroupMute']) { return 'sid4' }
  if(service.contains('RenderingControl') && action in ['SetVolume', 'SetMute', 'SetRelativeVolume',
      'SetBass', 'SetTreble', 'SetLoudness', 'SetEQ']) { return 'sid2' }
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
    case 'sid5': return getLastInboundHtControlEventEpoch()
    case 'sid6': return 0L
    default: return 0L
  }
}

void resubscribeUpnpSid(String sidKey) {
  switch(sidKey) {
    case 'sid1': resubscribeToAVTransport(); break
    case 'sid2': resubscribeToMrRcEvents(); break
    case 'sid3': resubscribeToZgtEvents(); break
    case 'sid4': resubscribeToMrGrcEvents(); break
    case 'sid5': resubscribeToHtControlEvents(); break
    case 'sid6': resubscribeToContentDirectory(); break
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
  synchronized(getRuntimeState()) {
    if(!streamUrl) { logWarn('TTS did not return a stream URI'); return }
    Map snapshot = runtimeState.lastTransportPlaybackSnapshot instanceof Map
        ? new LinkedHashMap((Map)runtimeState.lastTransportPlaybackSnapshot) : null
    String correlationId = UUID.randomUUID().toString()
    Integer previousGroupVolume = getGroupVolumeState()
    if(previousGroupVolume == null) { previousGroupVolume = getPlayerVolume() }
    volume = resolveExplicitVolumeSentinel(volume)
    Integer announcementVolume = volume as Integer
    runtimeState.remove('announcementPlaybackRestore')
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
}

void restoreDirectStreamTts(Map data = null) {
  synchronized(getRuntimeState()) {
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
      if(runtimeState[ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY]?.correlationId == correlationId) {
        runtimeState.remove(ANNOUNCEMENT_PLAYBACK_SNAPSHOT_KEY)
      }
      return
    }
    restoreTemporaryAnnouncementVolume(correlationId, active.previousGroupVolume as Integer,
        active.announcementVolume as Integer)
    runtimeState.remove('directStreamTtsRestore')
    restoreGroupAnnouncementPlayback(correlationId)
  }
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
  Map data = [streamTransportIntentGuid: guid, streamUri: currentURI]
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
      Map restore = runtimeState.announcementPlaybackRestore as Map
      if(restore != null && !announcementGuid && data?.streamUri &&
          data.streamUri != restore.snapshot?.announcementUri &&
          getCurrentAnnouncementRestore(restore.correlationId?.toString()) != null) {
        completeAnnouncementPlaybackRestore(restore.correlationId?.toString(), false, 'external-playback')
      }
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
    String updateId = findSoapBodyField(envelope, 'UpdateID') ?: '0'
    Map track = (Map)data.track
    Map params = getSoapActionParams(data.host.toString(), AVTransport, 'AddURIToSavedQueue', [
      ObjectID: data.objectId.toString(), UpdateID: updateId, EnqueuedURI: track.uri.toString(),
      EnqueuedURIMetaData: track.metadata.toString(), AddAtIndex: '4294967295'])
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
    GPathResult parsed = null
    try { parsed = new XmlSlurper().parseText(metadata) }
    catch(Exception ignored) { parsed = new XmlSlurper().parseText(unEscapeMetaData(metadata)) }
    GPathResult item = findDirectXmlChild(parsed, 'item')
    String title = findQueueXmlText(item, 'title')
    String resource = findQueueXmlText(item, 'res')
    String protocolInfo = item != null ? item['res']['@protocolInfo']?.toString() : null
    String uri = normalizeQueueTrackUri(resource)
    if(!title || !uri || uri.startsWith('x-sonosapi-stream:') || uri.startsWith('x-rincon-mp3radio:') || uri.startsWith('x-sonosapi-radio:')) {
      logInfo("Cannot save the currently playing item for ${device.displayName}: the music service did not provide track metadata that Sonos can add to a Favorite or Playlist")
      return null
    }
    return [uri: uri, metadata: metadata, title: title, creator: findQueueXmlText(item, 'creator') ?: findQueueXmlText(item, 'artist') ?: '',
      album: findQueueXmlText(item, 'album') ?: '',
      art: findQueueXmlText(item, 'albumArtURI') ?: '', protocolInfo: protocolInfo ?: '*:*:*:*']
  } catch(Exception e) {
    logInfo("Cannot save the currently playing item for ${device.displayName}: Sonos did not provide reusable track metadata (${e.message})")
    return null
  }
}

String escapeSoapXmlText(String value) { return StringEscapeUtils.escapeXml10(StringUtils.defaultString(value)) }
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
    GPathResult body = findDirectXmlChild(envelope, 'Body')
    GPathResult browseResponse = findDirectXmlChild(body, 'BrowseResponse')
    if(browseResponse == null && body != null && body.children().size() > 0) { browseResponse = (GPathResult)body.children()[0] }
    String resultXml = findQueueXmlText(browseResponse, 'Result')
    Integer returned = findQueueXmlText(browseResponse, 'NumberReturned')?.toInteger() ?: 0
    Integer total = findQueueXmlText(browseResponse, 'TotalMatches')?.toInteger() ?: 0
    String updateId = findQueueXmlText(browseResponse, 'UpdateID') ?: '0'
    List<Map> entries = (List<Map>)(data.entries ?: [])
    if(resultXml) {
      GPathResult didl = new XmlSlurper().parseText(resultXml)
      for(Object itemObject : didl.children()) {
        if(!(itemObject instanceof GPathResult) || xmlLocalName((GPathResult)itemObject) != 'item') { continue }
        GPathResult item = (GPathResult)itemObject
        String objectId = xmlAttribute(item, 'id')
        String title = findQueueXmlText(item, 'title')
        String artist = findQueueXmlText(item, 'creator') ?: findQueueXmlText(item, 'artist')
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
  while(normalized?.contains('&amp;')) { normalized = StringEscapeUtils.unescapeXml(normalized) }
  return normalized
}

String snapshotTrackDescription(String trackUri) {
  String title = device.currentValue('currentTrackName')?.toString()
  return title ? "'${title}' (${trackUri ?: 'track URI unavailable'})" : (trackUri ?: 'track URI unavailable')
}

String findQueueXmlText(GPathResult node, String targetName) {
  if(node == null || !targetName) { return null }
  return findXmlDescendantByLocalName(node, targetName)?.text()
}

String escapeQueueHtml(String value) {
  return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value)).replace("'", '&#39;')
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
  return StringEscapeUtils.escapeHtml4(handler)
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
  try { queuePlayerAttributeUpdate('lastUpdated', now()) } catch(Exception ignored) { }
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
    if(eventType?.namespace == 'audioClip' && eventType?.success == true) {
      setWsSubscriptionValue('audioClip', 'Subscribed')
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
      Object rawClipProgress = getAudioClipProgressByCorrelationId()
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

      stage = 'update in-memory correlation progress'
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
  }

  logDebug('WebSocket message dispatch completed all matching handlers')
}

void observeUpnpPlaybackSource(Map snapshot) {
  if(!(snapshot instanceof Map)) { return }
  String uri = snapshot.avTransportURI?.toString() ?: snapshot.currentTrackURI?.toString()
  Map transportMetadata = snapshot.avTransportURIMetaData instanceof Map
      ? (Map)snapshot.avTransportURIMetaData : [:]
  String itemClass = transportMetadata.class?.toString()
  Boolean isHomeTheater = uri?.startsWith('x-sonos-htastream:') == true ||
      itemClass?.contains('linein.homeTheater') == true
  Boolean isAirPlay = isAirPlayAudioSource(uri) || itemClass?.contains('linein.airplay') == true
  String containerType = isHomeTheater ? 'linein.homeTheater' : (isAirPlay ? 'linein.airplay' : itemClass)
  if(!containerType) { return }
  state.audioSourceMetadataType = containerType
  state.audioSourceMetadataObservedAt = now()
  String signalStatus = 'not-ht'
  if(isHomeTheater) {
    Object connected = state.htControlTosLinkConnected
    signalStatus = connected == null ? 'unknown' : (connected.toString() == '1' ? 'signal' : 'no-signal')
  }
  state.audioSourceSignalStatus = signalStatus
  state.audioSourceSignalObservedAt = now()
  logDebug("UPnP playback-source observation player=${getId()}, uri=${uri}, itemClass=${itemClass}, signalStatus=${signalStatus}")
}

void handleHtControlPayload(String xmlBody) {
  if(!isPlausibleXmlPayload(xmlBody)) { return }
  GPathResult propertyset = new XmlSlurper().parseText(xmlBody)
  GPathResult connectedNode = null
  for(Object child : propertyset.children()) {
    if(child instanceof GPathResult) {
      connectedNode = findDirectXmlChild((GPathResult)child, 'TOSLinkConnected')
      if(connectedNode != null) { break }
    }
  }
  if(connectedNode == null) { return }
  String connected = connectedNode.text()?.toString()?.trim()
  if(!(connected in ['0', '1'])) { return }
  state.htControlTosLinkConnected = connected
  state.htControlObservedAt = now()
  // App owns playback storage; fall back to the transient runtime snapshot.
  Map currentSnapshot = runtimeState.lastTransportPlaybackSnapshot instanceof Map
      ? (Map)runtimeState.lastTransportPlaybackSnapshot : [:]
  observeUpnpPlaybackSource(currentSnapshot)
  logDebug("UPnP HTControl TOSLinkConnected=${connected} player=${getId()}")
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
    String completedCorrelationId = runtimeState.activeAudioClipCorrelationId as String
    Map completedClip = (Map)getAtomicStateValue('activeAudioClipMessage')
    Boolean completedHighPriority = completedClip?.priority == 'HIGH'
    logDebug("Audio clip completion/reset for ${completedCorrelationId ?: 'unknown'} priority=${completedHighPriority ? 'HIGH' : 'REGULAR'} at ${now()}")
    pauseAudioClipWatchdog()
    // A regular clip remains at the queue head while it plays; remove only the
    // matching completed regular clip. A high-priority completion must leave
    // the suspended regular head in place for replay.
    if(completedHighPriority != true && completedCorrelationId) {
      removeActiveRegularAudioClipFromQueue(completedCorrelationId, 'completed')
      removeHiddenJsonRuntimeState('currentlyPlayingClip')
    }
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
  if(!clipMessage.get('enqueuedAt')) { clipMessage.put('enqueuedAt', Instant.now().toEpochMilli()) }
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
    return (NumberUtils.toInt(parts[0]) * 3600) + (NumberUtils.toInt(parts[1]) * 60) + NumberUtils.toInt(parts[2])
  } catch(Exception ignored) { return null }
}

void audioClipCommandSent(String correlationId) {
  if(!correlationId || correlationId != (runtimeState.activeAudioClipCorrelationId as String)) { return }
  if(runtimeState.audioClipCommandSent == true) { return }
  Long sentAt = now()
  runtimeState.audioClipCommandSent = true
  runtimeState.audioClipSentAt = sentAt
  runtimeState.audioClipQueueStartTime = sentAt
  Map<String, Map> clipProgress = getAudioClipProgressByCorrelationId() instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)getAudioClipProgressByCorrelationId())
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
  Map<String, Map> clipProgress = getAudioClipProgressByCorrelationId() instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)getAudioClipProgressByCorrelationId())
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
    Map<String, Map> progressMap = getAudioClipProgressByCorrelationId() instanceof Map
        ? (Map<String, Map>)getAudioClipProgressByCorrelationId() : [:]
    Map progress = progressMap[correlationId]
    if(progress?.phase in ['ACTIVE', 'DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']) { return }
    markAudioClipWatchdogFailure(correlationId, 'FAILED_ACTIVE_TIMEOUT',
      "No matching ACTIVE status within ${AUDIO_CLIP_ACTIVE_TIMEOUT_SECONDS}s of WebSocket delivery")
    return
  }
  if(phase == 'WAITING_FOR_DONE') {
    Map<String, Map> progressMap = getAudioClipProgressByCorrelationId() instanceof Map
        ? (Map<String, Map>)getAudioClipProgressByCorrelationId() : [:]
    Map progress = progressMap[correlationId]
    if(progress?.phase in ['DONE', 'ERROR', 'DISMISSED', 'INTERRUPTED']) { return }
    markAudioClipWatchdogFailure(correlationId, 'FAILED_DONE_TIMEOUT', 'No matching DONE status before the expected-duration deadline')
  }
}

void markAudioClipWatchdogFailure(String correlationId, String phase, String detail) {
  Map failedClip = (Map)getAtomicStateValue('activeAudioClipMessage')
  Map<String, Map> clipProgress = getAudioClipProgressByCorrelationId() instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)getAudioClipProgressByCorrelationId())
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
  if(failedClip?.priority != 'HIGH') {
    removeActiveRegularAudioClipFromQueue(correlationId, 'failed')
    removeHiddenJsonRuntimeState('currentlyPlayingClip')
  }
  runtimeState.remove('activeAudioClipCorrelationId')
  runtimeState.remove('activeAudioClipSonosId')
  removeHiddenJsonRuntimeState('activeAudioClipMessage')
}

void retryAudioClipAfterCommandFailure(String correlationId, String detail) {
  if(!correlationId || correlationId != (runtimeState.activeAudioClipCorrelationId as String)) { return }
  Map<String, Map> clipProgress = getAudioClipProgressByCorrelationId() instanceof Map
      ? new LinkedHashMap<String, Map>((Map<String, Map>)getAudioClipProgressByCorrelationId())
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

// =============================================================================
// End Websocket Incoming Data Processing
// =============================================================================

/** Split using the same character-delimiter and empty-token rules as Groovy tokenize(). */
List<String> splitTokens(String value, String delimiters) {
  if(value == null || value.isEmpty()) { return [] }
  String[] parts = StringUtils.split(value, delimiters)
  return parts == null ? [] : parts.toList()
}

