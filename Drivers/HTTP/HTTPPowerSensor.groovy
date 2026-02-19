
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
 **/

import groovy.json.JsonOutput
import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

metadata {
  definition (name: 'Rest Power Sensor',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    command 'restartESP'
    capability "Refresh"
    command 'initialize'


    capability "PowerMeter"
    capability "VoltageMeasurement"
    capability "EnergyMeter"

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'power', 'string', title:'Power Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'voltage', 'string', title:'Voltage Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'frequency', 'string', title:'Frequency Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'energy', 'string', title:'Total Daily Energy Sensor Name', description: '', required: true, displayDuringSetup: true
    }
    input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
    input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: true
    input 'traceLogEnable', 'bool', title: 'Enable trace logging', required: false, defaultValue: false
  }
}

// =============================================================================
// Logging
// =============================================================================

void logError(String message) { if(logEnable != false) { log.error("${device.displayName}: ${message}") } }
void logWarn(String message) { if(logEnable != false) { log.warn("${device.displayName}: ${message}") } }
void logInfo(String message) { if(logEnable != false) { log.info("${device.displayName}: ${message}") } }
void logDebug(String message) { if(logEnable != false && debugLogEnable != false) { log.debug("${device.displayName}: ${message}") } }
void logTrace(String message) { if(logEnable != false && traceLogEnable != false) { log.trace("${device.displayName}: ${message}") } }

void logsOff() { logWarn("Logging disabled"); device.updateSetting('logEnable', [value:'false', type:'bool']) }
void debugLogsOff() { logWarn("Debug logging disabled"); device.updateSetting('debugLogEnable', [value:'false', type:'bool']) }
void traceLogsOff() { logWarn("Trace logging disabled"); device.updateSetting('traceLogEnable', [value:'false', type:'bool']) }

// =============================================================================
// Lifecycle
// =============================================================================

void installed() {
  logDebug('Installed...')
  try { initialize() } catch(e) { logWarn("No initialize() method or error: ${e}") }
  if(logEnable != false) { runIn(1800, 'logsOff') }
  if(debugLogEnable != false) { runIn(1800, 'debugLogsOff') }
  if(traceLogEnable != false) { runIn(1800, 'traceLogsOff') }
}

void updated() {
  logDebug('Updated...')
  try { configure() } catch(e) { logWarn("No configure() method or error: ${e}") }
}

void uninstalled() {
  logDebug('Uninstalled...')
  unschedule()
}

// =============================================================================
// JSON Utilities
// =============================================================================

String prettyJson(Map jsonInput) { return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput)) }

// =============================================================================
// ESPHome HTTP
// =============================================================================

@Field static final String UPTIME_STATE = '/sensor/uptime'
@Field static final String RESTART_ESP = '/button/restart/press'

void initialize() { configure() }
void configure() {
  if (!settings.ip) { logWarn('IP address not configured'); return }
  String newDni = getMACFromIP(settings.ip)
  device.setDeviceNetworkId(newDni)
  checkConnection()
  refresh()
  unschedule()
  runEvery3Hours('checkConnection')
  runEvery30Minutes('refresh')
  try { createChildDevices() } catch(e) { logWarn("Error creating child devices: ${e}") }
}

void restartESP() { sendCommandAsync(RESTART_ESP, null, null) }
void checkConnection() { sendQueryAsync(UPTIME_STATE, 'checkConnectionCallback') }
void checkConnectionCallback(AsyncResponse response, Map data = null) {
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    sendEvent(name: 'status', value: 'offline')
  } else {
    Map responseData = parseJson(response.getData())
    logDebug("response.getData() = ${responseData}")
    sendEvent(name: 'status', value: 'online')
    sendEvent(name: 'uptime', value: "${responseData.value.toInteger()}")
  }
}

void sendCommandAsync(String path, String callbackMethod, Map data = null) {
  try {
    Map params = [uri: "http://${settings.ip}:${settings.port}${path}"]
    logDebug("${params.uri}")
    asynchttpPost(callbackMethod, params, data)
  } catch(Exception e) {
    if(e.message.toString() != 'OK') { logError(e.message) }
  }
  runInMillis(500, 'refresh')
}

void sendQueryAsync(String path, String callback, Map data = null) {
  Map params = [uri: "http://${settings.ip}:${settings.port}${path}"]
  logDebug("${params.uri}")
  try {
    asynchttpGet(callback, params, data)
  } catch(Exception e) {
    logDebug("Call failed: ${e.message}")
  }
}

// =============================================================================
// Driver
// =============================================================================

String powerState() { return "/sensor/${power.toLowerCase().replace(' ','_')}" }
String voltageState() { return "/sensor/${voltage.toLowerCase().replace(' ','_')}" }
String frequencyState() { return "/sensor/${frequency.toLowerCase().replace(' ','_')}" }
String energyState() { return "/sensor/${energy.toLowerCase().replace(' ','_')}" }

void refresh() {
  sendQueryAsync(powerState(), 'switchStateCallback', [sensor:powerState()])
  sendQueryAsync(voltageState(), 'switchStateCallback', [sensor:voltageState()])
  sendQueryAsync(frequencyState(), 'switchStateCallback', [sensor:frequencyState()])
  sendQueryAsync(energyState(), 'switchStateCallback', [sensor:energyState()])
}

void switchStateCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  if(data.sensor == powerState()) { sendEvent(name:'power', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == voltageState()) { sendEvent(name:'voltage', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == frequencyState()) { sendEvent(name:'frequency', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == energyState()) { sendEvent(name:'energy', value:celsiusToFahrenheit(parseJson(response.getData()).value.toInteger())) }
}
