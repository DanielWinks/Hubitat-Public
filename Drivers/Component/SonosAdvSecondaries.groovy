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

metadata {
  definition(
    name: 'Sonos Advanced Secondaries',
    version: '0.3.23',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvSecondaries.groovy'
  ) {
    // capability 'Actuator'
    // capability 'Switch'

  }
}

void initialize() {configure()}
void configure() {
  initializeWebsocketConnection()
}

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
}

void renewWebsocketConnection() {
  initializeWebsocketConnection()
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
// Websocket Commands
// =============================================================================
@CompileStatic
void playerLoadAudioClip(String json) {
  sendWsMessage(json)
}

// =============================================================================
// Parse
// =============================================================================

void parse(String message) {}