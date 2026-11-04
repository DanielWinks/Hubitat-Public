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
    version: '0.13.3',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvSecondaries.groovy'
  ) {
      capability 'Initialize'
    }
}
// =============================================================================
// Fields
// =============================================================================
@Field private final Integer RESUB_INTERVAL = 7200
// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Initialize And Configure
// =============================================================================
void initialize() { configure() }
void configure() { initializeWebsocketConnection() }
// =============================================================================
// End Initialize And Configure
// =============================================================================



// =============================================================================
// Websocket Connection and Initialization
// =============================================================================
void webSocketStatus(String message) {
  if(message == 'failure: null') { this.device.updateDataValue('websocketStatus', 'closed')}
  else if(message == 'failure: connect timed out') { this.device.updateDataValue('websocketStatus', 'connect timed out')}
  else if(message == 'status: open') { this.device.updateDataValue('websocketStatus', 'open')}
  else {
    this.device.updateDataValue('websocketStatus', 'unknown')
    logWarn("Websocket status: ${message}")
  }
  logTrace("WebSocket status callback: ${message}; storedStatus=${this.device.getDataValue('websocketStatus')}")
}

void wsConnect() {
  try {
    String websocketUrl = this.device.getDataValue('websocketUrl') ?: getLocalWsUrl()
    if(!websocketUrl) {
      logWarn('Websocket URL not configured, cannot connect')
      return
    }
    Map headers = ['X-Sonos-Api-Key':'123e4567-e89b-12d3-a456-426655440000']
    interfaces.webSocket.connect(websocketUrl, headers: headers, ignoreSSLIssues: true)
    unschedule('renewWebsocketConnection')
    runIn(RESUB_INTERVAL-100, 'renewWebsocketConnection')
  } catch (Exception e) {
    logError("Failed to connect websocket: ${e.message}")
    this.device.updateDataValue('websocketStatus', 'error')
  }
}

void wsClose() { interfaces.webSocket.close() }

void sendWsMessage(String message) {
  if(this.device.getDataValue('websocketStatus') != 'open') { initializeWebsocketConnection() }
  interfaces.webSocket.sendMessage(message)
}

void initializeWebsocketConnection() { wsConnect() }
void renewWebsocketConnection() { initializeWebsocketConnection() }
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

/** Queue the paired-channel send result without logging during a synchronized group release pass. */
void playerLoadAudioClipForSynchronizedRelease(String json, String correlationId) {
  if(!json || !correlationId) { return }
  Map pending = state.synchronizedAudioClipReleases instanceof Map
      ? (Map)state.synchronizedAudioClipReleases : [:]
  String websocketStatus = this.device.getDataValue('websocketStatus')?.toString()
  Map release = [json: json, result: 'deferred', websocketStatusAtRelease: websocketStatus]
  if(websocketStatus == 'open') {
    try {
      interfaces.webSocket.sendMessage(json)
      release.result = 'sent'
    } catch(Exception e) {
      release.result = 'failed'
      release.error = e.message?.toString()
    }
  }
  pending[correlationId] = release
  state.synchronizedAudioClipReleases = pending
}

/** Complete deferred connection/error handling after the synchronized send pass. */
void completeSynchronizedAudioClipRelease(String correlationId) {
  if(!correlationId) { return }
  Map pending = state.synchronizedAudioClipReleases instanceof Map
      ? (Map)state.synchronizedAudioClipReleases : [:]
  Map release = pending.remove(correlationId) as Map
  state.synchronizedAudioClipReleases = pending
  if(release == null) {
    logTrace("No paired-speaker audioClip release record found during completion for ${correlationId}")
    return
  }
  logTrace("Completing paired-speaker audioClip release for ${correlationId}: result=${release.result}, websocketStatusAtRelease=${release.websocketStatusAtRelease}, websocketStatusNow=${this.device.getDataValue('websocketStatus')}")
  if(release.result == 'deferred') {
    try {
      sendWsMessage(release.json?.toString())
      logTrace("Deferred paired-speaker audioClip dispatch returned successfully for ${correlationId}; websocketStatusNow=${this.device.getDataValue('websocketStatus')}")
    } catch(Exception e) {
      logWarn("Deferred paired-speaker audioClip dispatch failed for ${correlationId}: ${e.message}")
    }
  } else if(release.result == 'failed') {
    logWarn("Paired-speaker audioClip send failed for ${correlationId}: ${release.error ?: 'unknown error'}")
  } else if(release.result == 'sent') {
    logTrace("Paired-speaker audioClip sent during synchronized release pass for ${correlationId}")
  }
}
// =============================================================================
// End Websocket Commands
// =============================================================================



// =============================================================================
// Parse
// =============================================================================
void parse(String message) {}
// =============================================================================
// End Parse
// =============================================================================
