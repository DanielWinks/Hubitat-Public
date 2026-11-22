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

import groovy.transform.Field
import groovy.transform.CompileStatic

void logError(String message) {
  if (settings.logEnable != false) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}
void logInfo(String message) {
  if (settings.logEnable != false) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}
void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}
void logWarn(String message) {
  if (settings.logEnable != false) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}
void logTrace(String message) {
  if (settings.logEnable != false) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

metadata {
  definition(
    name: 'Sonos Advanced Secondaries',
    version: '0.18.9',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvSecondaries.groovy'
  ) {
      capability 'Initialize'
      attribute 'websocketStatus', 'string'
      attribute 'websocketCommandResponse', 'string'
      attribute 'secondaryInitRequest', 'string'
    }
}
// =============================================================================
// Fields
// =============================================================================
@Field private final Integer RESUB_INTERVAL = 7200
@Field private final Integer WS_MAX_RECONNECT_ATTEMPTS = 3
@Field private final List<Integer> WS_RECONNECT_DELAYS = [5, 15, 60]
@Field private final Integer WS_COMMAND_ACK_TIMEOUT_SECONDS = 3

@Field static final Set<String> ACTIVE_SECONDARY_STATE_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'websocketConnectPending',
  'websocketReconnectAttempts',
  'websocketReconnectScheduled',
  'websocketConnectionGeneration',
  'websocketLastInboundAt',
  'websocketInboundSequence',
  'websocketCommandLedger',
  'synchronizedAudioClipReleases',
  'parentInitializationGranted',
]))

@Field static final Set<String> ACTIVE_SECONDARY_DEVICE_DATA_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'id',
  'deviceIp',
  'householdId',
  'websocketUrl',
  'websocketStatus',
]))

@Field static final Set<String> ACTIVE_SECONDARY_CURRENT_STATE_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>([
  'websocketStatus',
  'websocketCommandResponse',
  'secondaryInitRequest',
]))

@Field static final Set<String> ACTIVE_SECONDARY_SETTINGS_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'logEnable',
  'debugLogEnable',
]))

// =============================================================================
// Migration-Prune Helpers — whitelist-driven for-each removal
// =============================================================================
Map<String, Object> pruneOrphanedSecondaryPersistence() {
  Map<String, Object> result = [
    stateRemoved: pruneOrphanStateKeys(state as Map, ACTIVE_SECONDARY_STATE_KEYS),
    deviceDataRemoved: pruneOrphanDeviceData(device, ACTIVE_SECONDARY_DEVICE_DATA_KEYS),
    currentStatesRemoved: pruneOrphanCurrentStates(device, ACTIVE_SECONDARY_CURRENT_STATE_ATTRIBUTES),
    settingsRemoved: pruneOrphanSettings(ACTIVE_SECONDARY_SETTINGS_KEYS),
  ]
  Integer total = ((List)result.stateRemoved).size() + ((List)result.deviceDataRemoved).size() +
    ((List)result.currentStatesRemoved).size() + ((List)result.settingsRemoved).size()
  if(total > 0) {
    logDebug("Secondary persistence prune: removed ${total} orphan value(s) (state=${result.stateRemoved}, deviceData=${result.deviceDataRemoved}, currentStates=${result.currentStatesRemoved}, settings=${result.settingsRemoved})")
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

List<String> pruneOrphanDeviceData(Object dev, Set<String> active) {
  if(dev == null || active == null) { return [] }
  try {
    Map data = dev.getData()
    if(data == null) { return [] }
    List<String> toRemove = new ArrayList<String>(data.keySet())
      .findAll { String k -> !active.contains(k) }
    toRemove.each { String k -> dev.removeDataValue(k) }
    return toRemove
  } catch(Exception e) {
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
// Initialize And Configure
// =============================================================================
void initialize() { configure() }
void configure() {
  pruneOrphanedSecondaryPersistence()
  sendEvent(name: 'secondaryInitRequest', value: groovy.json.JsonOutput.toJson([reason: 'secondary device lifecycle initialize', dni: this.device.getDeviceNetworkId()]))
}
void installed() { configure() }
void updated() { configure() }
// =============================================================================
// End Initialize And Configure
// =============================================================================



// =============================================================================
// Websocket Connection and Initialization
// =============================================================================
void webSocketStatus(String message) {
  if(message == 'status: open') {
    if(this.device.getDataValue('websocketStatus') == 'open' && state.websocketConnectPending != true) {
      logDebug('Ignoring duplicate secondary WebSocket open callback')
      return
    }
    state.remove('websocketConnectPending')
    state.remove('websocketReconnectAttempts')
    state.websocketConnectionGeneration = ((state.websocketConnectionGeneration ?: 0L) as Long) + 1L
    state.websocketLastInboundAt = now()
    state.websocketInboundSequence = (state.websocketInboundSequence ?: 0L) as Long
    unschedule('retryWebsocketConnection')
    this.device.updateDataValue('websocketStatus', 'open')
    sendEvent(name: 'websocketStatus', value: 'open', isStateChange: true)
    unschedule('renewWebsocketConnection')
    runIn(RESUB_INTERVAL - 100, 'renewWebsocketConnection')
  }
  else if(message == 'status: closing') {
    if(state.websocketConnectPending == true) {
      // Hubitat may report the replaced transport closing after connect() has
      // already started the replacement. Preserve the pending attempt.
      logDebug('Ignoring secondary WebSocket closing callback while replacement connection is pending')
      return
    }
    if(this.device.getDataValue('websocketStatus') == 'closing') {
      logDebug('Ignoring duplicate secondary WebSocket closing callback')
      return
    }
    state.remove('websocketConnectPending')
    this.device.updateDataValue('websocketStatus', 'closing')
    failPendingAudioClipLedger('socket closing; paired command will not be replayed')
    sendEvent(name: 'websocketStatus', value: 'closing', isStateChange: true)
  }
  else if(message?.startsWith('failure:')) {
    state.remove('websocketConnectPending')
    String failureStatus = message == 'failure: null' ? 'closed' :
        (message == 'failure: connect timed out' ? 'connect timed out' : 'error')
    this.device.updateDataValue('websocketStatus', failureStatus)
    failPendingAudioClipLedger("socket ${failureStatus}")
    sendEvent(name: 'websocketStatus', value: failureStatus, isStateChange: true)
    logWarn("Websocket connection failed: ${message}")
    scheduleWebsocketReconnect()
  }
  else {
    state.remove('websocketConnectPending')
    this.device.updateDataValue('websocketStatus', 'unknown')
    sendEvent(name: 'websocketStatus', value: 'unknown', isStateChange: true)
    logWarn("Websocket status: ${message}")
    scheduleWebsocketReconnect()
  }
  notifyParentWebsocketStatus(this.device.getDataValue('websocketStatus')?.toString())
  logDebug("WebSocket status callback: ${message}; storedStatus=${this.device.getDataValue('websocketStatus')}")
}

/** Notify the owning player via device event (event-only parent communication). */
void notifyParentWebsocketStatus(String status) {
  try {
    sendEvent(name: 'websocketStatus', value: status, isStateChange: true)
    logDebug("Notified parent player of secondary WebSocket status via event: status=${status}")
  } catch(Exception e) {
    logDebug("Could not notify parent player of secondary WebSocket status ${status}: ${e.message}")
  }
}

void wsConnect() {
  if(this.device.getDataValue('websocketStatus') == 'open' || state.websocketConnectPending == true) {
    logDebug("Skipping secondary WebSocket connect; status=${this.device.getDataValue('websocketStatus')}, connectPending=${state.websocketConnectPending == true}")
    return
  }
  if(state.parentInitializationGranted != true) {
    try {
      sendEvent(name: 'secondaryInitRequest', value: groovy.json.JsonOutput.toJson([reason: 'secondary WebSocket connection requested', dni: this.device.getDeviceNetworkId()]))
      logDebug("Requested parent-mediated secondary reconnect via event")
    } catch(Exception e) { logDebug("Could not request parent-mediated secondary reconnect: ${e.message}") }
    return
  }
  try {
    String websocketUrl = this.device.getDataValue('websocketUrl') ?: getLocalWsUrl()
    if(!websocketUrl) {
      logWarn('Websocket URL not configured, cannot connect')
      return
    }
    String previousStatus = this.device.getDataValue('websocketStatus')?.toString() ?: 'unknown'
    Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
    state.websocketConnectPending = true
    state.parentInitializationGranted = false
    this.device.updateDataValue('websocketStatus', 'connecting')
    logDebug("Requesting secondary WebSocket connection; previousStatus=${previousStatus}, retryCount=${state.websocketReconnectAttempts ?: 0}")
    interfaces.webSocket.connect(websocketUrl, headers: headers, ignoreSSLIssues: true)
  } catch (Exception e) {
    state.remove('websocketConnectPending')
    state.parentInitializationGranted = false
    logError("Failed to connect websocket: ${e.message}")
    this.device.updateDataValue('websocketStatus', 'error')
    sendEvent(name: 'websocketStatus', value: 'error', isStateChange: true)
    scheduleWebsocketReconnect()
  }
}

void wsClose() {
  logDebug("Closing secondary WebSocket; status=${this.device.getDataValue('websocketStatus')}, connectPending=${state.websocketConnectPending == true}")
  interfaces.webSocket.close()
}

Boolean sendWsMessage(String message) {
  if(!message) { return false }
  if(this.device.getDataValue('websocketStatus') != 'open') {
    logDebug("Not sending secondary WebSocket message; socket status=${this.device.getDataValue('websocketStatus')}, messageLength=${message.length()}")
    initializeWebsocketConnection()
    logWarn('WebSocket message was not sent because the secondary socket is not open; paired dispatch must retry as a group')
    return false
  }
  Map descriptor = describeSecondaryWebsocketCommand(message)
  if(descriptor.namespace != 'audioClip' || descriptor.wireCommand != 'loadAudioClip') {
    logWarn('Refusing to send an unsupported secondary WebSocket command without a response fingerprint')
    return false
  }
  try {
    String ledgerId = registerSecondaryAudioClipLedgerEntry(message, descriptor)
    interfaces.webSocket.sendMessage(message)
    logDebug("Secondary WebSocket sendMessage returned successfully; ledgerId=${ledgerId ?: 'not-ledgered'}")
    return true
  } catch(Exception e) {
    failPendingAudioClipLedger("send failed: ${e.message}")
    this.device.updateDataValue('websocketStatus', 'error')
    sendEvent(name: 'websocketStatus', value: 'error', isStateChange: true)
    logDebug("Secondary WebSocket sendMessage threw; status changed to error: ${e.message}")
    logWarn("WebSocket send failed: ${e.message}")
    scheduleWebsocketReconnect()
    return false
  }
}

void scheduleWebsocketReconnect() {
  if(state.websocketReconnectScheduled == true) { return }
  Integer attempts = (state.websocketReconnectAttempts ?: 0) as Integer
  if(attempts >= WS_MAX_RECONNECT_ATTEMPTS) {
    logWarn('WebSocket reconnect limit reached; another explicit initialize call is required to try again')
    return
  }
  Integer delaySeconds = WS_RECONNECT_DELAYS[attempts]
  state.websocketReconnectAttempts = attempts + 1
  state.websocketReconnectScheduled = true
  logDebug("Scheduling secondary WebSocket reconnect attempt ${attempts + 1}/${WS_MAX_RECONNECT_ATTEMPTS} in ${delaySeconds}s")
  runIn(delaySeconds, 'retryWebsocketConnection', [overwrite: true])
}

void retryWebsocketConnection() {
  state.remove('websocketReconnectScheduled')
  if(state.websocketConnectPending == true || this.device.getDataValue('websocketStatus') == 'open') {
    logDebug("Skipping secondary WebSocket retry; status=${this.device.getDataValue('websocketStatus')}, connectPending=${state.websocketConnectPending == true}")
    return
  }
  logDebug("Attempting secondary WebSocket reconnect ${state.websocketReconnectAttempts ?: 0}/${WS_MAX_RECONNECT_ATTEMPTS}")
  wsConnect()
}

void initializeWebsocketConnection() {
  if(state.websocketReconnectAttempts == null) { state.websocketReconnectAttempts = 0 }
  wsConnect()
}

void beginParentMediatedConnection(String requestId, Boolean resetRetries = false) {
  if(resetRetries == true) {
    unschedule('retryWebsocketConnection')
    state.remove('websocketReconnectScheduled')
    state.websocketReconnectAttempts = 0
  }
  state.parentInitializationGranted = true
  logDebug("Starting parent-mediated secondary WebSocket connection; request=${requestId}")
  wsConnect()
}
void renewWebsocketConnection() {
  state.remove('websocketConnectPending')
  this.device.updateDataValue('websocketStatus', 'closed')
  initializeWebsocketConnection()
}
// =============================================================================
// End Websocket Connection and Initialization
// =============================================================================



// =============================================================================
// Getters and Setters
// =============================================================================
String getLocalApiUrl(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "https://${deviceIp}:1443/api/v1/"
}
String getLocalUpnpHost(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "${deviceIp}:1400"
}
String getLocalUpnpUrl(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "http://${deviceIp}:1400"
}
String getLocalWsUrl(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "wss://${deviceIp}:1443/websocket/api"
}

String getWebSocketStatus() {
  return this.device.getDataValue('websocketStatus')
}
void setWebSocketStatus(String status) {
  this.device.updateDataValue('websocketStatus', status)
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
// =============================================================================
// End Getters and Setters
// =============================================================================



// =============================================================================
// Websocket Commands
// =============================================================================
@CompileStatic
void playerLoadAudioClip(String json) {
  sendWsMessage(json)
}

/** Send only during the parent's paired-channel release pass; never retain a channel-local clip queue. */
Boolean playerLoadAudioClipForSynchronizedRelease(String json, String correlationId) {
  if(!json || !correlationId) { return false }
  Map pending = state.synchronizedAudioClipReleases instanceof Map
      ? (Map)state.synchronizedAudioClipReleases : [:]
  String websocketStatus = this.device.getDataValue('websocketStatus')?.toString()
  Map release = [json: json, result: 'failed', websocketStatusAtRelease: websocketStatus]
  if(websocketStatus == 'open') {
    try {
      release.result = sendWsMessage(json) ? 'sent' : 'failed'
      if(release.result == 'failed') { release.error = 'WebSocket send did not complete' }
    } catch(Exception e) {
      release.result = 'failed'
      release.error = e.message?.toString()
    }
  } else {
    logDebug("Paired audioClip secondary send skipped because socket is not open: correlation=${correlationId}, websocketStatus=${websocketStatus}")
    initializeWebsocketConnection()
    release.result = 'failed'
    release.error = 'Secondary socket was not open during paired release'
  }
  pending[correlationId] = release
  state.synchronizedAudioClipReleases = pending
  logDebug("Paired audioClip secondary release result recorded: correlation=${correlationId}, result=${release.result}, websocketStatusNow=${this.device.getDataValue('websocketStatus')}")
  return release.result == 'sent'
}

/** Preserve synchronized-release result logging after the paired send attempt. */
void completeSynchronizedAudioClipRelease(String correlationId) {
  if(!correlationId) { return }
  Map pending = state.synchronizedAudioClipReleases instanceof Map
      ? (Map)state.synchronizedAudioClipReleases : [:]
  Map release = pending.remove(correlationId) as Map
  state.synchronizedAudioClipReleases = pending
  if(release == null) {
    logDebug("No paired-speaker audioClip release record found during completion for ${correlationId}")
    return
  }
  logDebug("Completing paired-speaker audioClip release for ${correlationId}: result=${release.result}, websocketStatusAtRelease=${release.websocketStatusAtRelease}, websocketStatusNow=${this.device.getDataValue('websocketStatus')}")
  if(release.result == 'failed') {
    logWarn("Paired-speaker audioClip send failed for ${correlationId}: ${release.error ?: 'unknown error'}")
  } else if(release.result == 'sent') {
    logDebug("Paired-speaker audioClip sent during synchronized release pass for ${correlationId}")
  }
}
// =============================================================================
// End Websocket Commands
// =============================================================================



// =============================================================================
// Parse
// =============================================================================
void websocketMessage(String message) { parse(message) }

void parse(String message) {
  if(!message) { return }
  try {
    List payload = (List)new groovy.json.JsonSlurper().parseText(message)
    if(payload.size() < 2 || !(payload[0] instanceof Map) || !(payload[1] instanceof Map)) { return }
    Map eventType = (Map)payload[0]
    Map eventData = (Map)payload[1]
    state.websocketLastInboundAt = now()
    state.websocketInboundSequence = ((state.websocketInboundSequence ?: 0L) as Long) + 1L
    String response = eventType.response?.toString()
    String namespace = eventType.namespace?.toString()
    if(namespace != 'audioClip' || response != 'loadAudioClip') { return }
    List<Map> ledger = state.websocketCommandLedger instanceof List
        ? (List<Map>)state.websocketCommandLedger : []
    String returnedCorrelation = eventData.name?.toString()
    Long currentGeneration = (state.websocketConnectionGeneration ?: 0L) as Long
    Map matched = ledger.find { Map entry ->
      entry.connectionGeneration == currentGeneration &&
        (eventType.success != true || eventType.type?.toString() == 'audioClip') &&
        (eventType.success != true || (returnedCorrelation && entry.correlationId?.toString() == returnedCorrelation)) &&
        (!returnedCorrelation || entry.correlationId?.toString() == returnedCorrelation)
    } as Map
    if(matched == null) {
      logDebug("Secondary WebSocket received unmatched audioClip response: correlation=${returnedCorrelation ?: 'n/a'}, success=${eventType.success}, type=${eventType.type}")
      return
    }
    ledger.remove(matched)
    if(ledger.isEmpty()) { state.remove('websocketCommandLedger') }
    else { state.websocketCommandLedger = ledger }
    String result = eventType.success == true ? 'success' : 'failure'
    logDebug("Secondary WebSocket command ledger ${result}: id=${matched.entryId}, audioClip.loadAudioClip correlation=${matched.correlationId}, type=${eventType.type}, error=${eventData.errorCode ?: eventData.reason ?: 'n/a'}")
    notifyParentWebsocketCommandResponse(result, matched.correlationId?.toString(), eventData.errorCode?.toString())
  } catch(Exception e) {
    logDebug("Could not parse secondary WebSocket message: ${e.message}")
  }
}

Map describeSecondaryWebsocketCommand(String message) {
  try {
    List payload = (List)new groovy.json.JsonSlurper().parseText(message)
    Map command = payload && payload[0] instanceof Map ? (Map)payload[0] : [:]
    Map args = payload.size() > 1 && payload[1] instanceof Map ? (Map)payload[1] : [:]
    return [namespace: command.namespace?.toString(), wireCommand: command.command?.toString(),
      correlationId: args.name?.toString()]
  } catch(Exception e) {
    logDebug("Could not inspect secondary WebSocket command: ${e.message}")
    return [:]
  }
}

String registerSecondaryAudioClipLedgerEntry(String message, Map descriptor) {
  String entryId = UUID.randomUUID().toString()
  Long generation = (state.websocketConnectionGeneration ?: 0L) as Long
  Map entry = [entryId: entryId, connectionGeneration: generation, namespace: descriptor.namespace,
      wireCommand: descriptor.wireCommand, correlationId: descriptor.correlationId, message: message,
      sentAt: now(), inboundSequenceAtSend: (state.websocketInboundSequence ?: 0L) as Long,
      expiresAt: now() + WS_COMMAND_ACK_TIMEOUT_SECONDS * 1000L]
  List<Map> ledger = state.websocketCommandLedger instanceof List
      ? (List<Map>)state.websocketCommandLedger : []
  ledger.add(entry)
  state.websocketCommandLedger = ledger
  runIn(WS_COMMAND_ACK_TIMEOUT_SECONDS, 'secondaryAudioClipLedgerTimeout',
      [data: [entryId: entryId, connectionGeneration: generation], overwrite: false])
  logDebug("Secondary WebSocket command ledger armed id=${entryId}, audioClip.loadAudioClip correlation=${entry.correlationId}, timeout=${WS_COMMAND_ACK_TIMEOUT_SECONDS}s")
  return entryId
}

void secondaryAudioClipLedgerTimeout(Map data = [:]) {
  String entryId = data?.entryId?.toString()
  Long generation = data?.connectionGeneration != null ? (data.connectionGeneration as Long) : -1L
  List<Map> ledger = state.websocketCommandLedger instanceof List
      ? (List<Map>)state.websocketCommandLedger : []
  Map entry = ledger.find { Map item -> item.entryId?.toString() == entryId }
  if(entry == null || entry.connectionGeneration != generation) { return }
  ledger.remove(entry)
  if(ledger.isEmpty()) { state.remove('websocketCommandLedger') }
  else { state.websocketCommandLedger = ledger }
  Boolean socketSilent = ((state.websocketInboundSequence ?: 0L) as Long) <= ((entry.inboundSequenceAtSend ?: 0L) as Long)
  logWarn("Secondary WebSocket audioClip command received no matching ACK in ${WS_COMMAND_ACK_TIMEOUT_SECONDS}s; inboundSinceSend=${!socketSilent}, correlation=${entry.correlationId}. Paired audio will not be replayed.")
  if(!socketSilent) { return }
  logWarn('Secondary WebSocket has no inbound activity during the audioClip ACK window; reconnecting without replaying the paired clip.')
  failPendingAudioClipLedger('secondary WebSocket is stale; paired clips already sent will not be replayed')
  this.device.updateDataValue('websocketStatus', 'closed')
  sendEvent(name: 'websocketStatus', value: 'closed', isStateChange: true)
  notifyParentWebsocketStatus('closed')
  initializeWebsocketConnection()
}

void failPendingAudioClipLedger(String reason) {
  List<Map> ledger = state.websocketCommandLedger instanceof List
      ? (List<Map>)state.websocketCommandLedger : []
  if(!ledger.isEmpty()) {
    ledger.each { Map entry ->
      logWarn("Discarding unacknowledged secondary audioClip command ${entry.correlationId}: ${reason}; it will not be replayed")
    }
    state.remove('websocketCommandLedger')
    unschedule('secondaryAudioClipLedgerTimeout')
  }
}

void notifyParentWebsocketCommandResponse(String result, String correlationId, String errorCode) {
  try {
    sendEvent(name: 'websocketCommandResponse', value: groovy.json.JsonOutput.toJson([result: result, correlationId: correlationId, errorCode: errorCode, dni: this.device.getDeviceNetworkId()]))
  } catch(Exception e) { logDebug("Could not notify parent of secondary command response: ${e.message}") }
}
// =============================================================================
// End Parse
// =============================================================================
