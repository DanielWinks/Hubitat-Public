
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
  definition (name: 'HTTP Plant Sensor',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    command 'restartESP'
    capability 'Refresh'
    capability 'Sensor'
    command 'initialize'


    capability 'IlluminanceMeasurement' // attribute 'illuminance', 'NUMBER'
    attribute 'soilMoisture', 'NUMBER'
    attribute 'soilConductivity', 'NUMBER'
    capability 'TemperatureMeasurement' // attribute 'temperature', 'NUMBER'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'illuminanceName', 'string', title:'Illuminance Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'moistureName', 'string', title:'Moisture Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'conductivityName', 'string', title:'Soil Conductivity Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'temperatureName', 'string', title:'Temperature Sensor Name', description: '', required: true, displayDuringSetup: true
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

String illuminanceState() { return "/sensor/${illuminanceName.toLowerCase().replace(' ','_')}" }
String moistureState() { return "/sensor/${moistureName.toLowerCase().replace(' ','_')}" }
String conductivityState() { return "/sensor/${conductivityName.toLowerCase().replace(' ','_')}" }
String temperatureState() { return "/sensor/${temperatureName.toLowerCase().replace(' ','_')}" }

void refresh() {
  sendQueryAsync(illuminanceState(), 'sensorCallback', [sensor:illuminanceState()])
  sendQueryAsync(moistureState(), 'sensorCallback', [sensor:moistureState()])
  sendQueryAsync(conductivityState(), 'sensorCallback', [sensor:conductivityState()])
  sendQueryAsync(temperatureState(), 'sensorCallback', [sensor:temperatureState()])
}

void sensorCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  if(data.sensor == illuminanceState()) { sendEvent(name:'illuminance', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == moistureState()) { sendEvent(name:'soilMoisture', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == conductivityState()) { sendEvent(name:'soilConductivity', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == temperatureState()) { sendEvent(name:'temperature', value:celsiusToFahrenheit(parseJson(response.getData()).value.toInteger())) }
}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonBody = parseJson(parsedMessage.body)
  logDebug(prettyJson(jsonBody))
  if(jsonBody?.illuminance != null) { sendEvent(name:'illuminance', value:jsonBody?.illuminance, descriptionText:"Illuminance is now ${val}", isStateChange:true) }
  if(jsonBody?.soilMoisture != null) { sendEvent(name:'soilMoisture', value:jsonBody?.soilMoisture, descriptionText:"Soil moisture is now ${val}", isStateChange:true) }
  if(jsonBody?.soilConductivity != null) { sendEvent(name:'soilConductivity', value:jsonBody?.soilConductivity, descriptionText:"Soil conductivity is now ${val}", isStateChange:true) }
  if(jsonBody?.temperature != null) { sendEvent(name:'temperature', value:jsonBody?.temperature, descriptionText:"Temperature is now ${val}", isStateChange:true) }
}
