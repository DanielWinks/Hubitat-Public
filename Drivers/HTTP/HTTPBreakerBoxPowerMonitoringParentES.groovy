
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

import com.hubitat.app.ChildDeviceWrapper
import groovy.json.JsonOutput
import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

metadata {
  definition (name: 'Eventstream Breaker Box Power Monitor', namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability "PowerMeter" //power - NUMBER, unit:W
    capability "VoltageMeasurement" //voltage - NUMBER, unit:V  frequency - NUMBER, unit:Hz
    capability "EnergyMeter" //energy - NUMBER, unit:kWh
    capability "CurrentMeter" //amperage - NUMBER, unit:A

    command 'restartESP'
    command 'createChildDevices'
    command 'murderChildren'
    command 'esConnect'
    command 'esClose'

    attribute 'energy5Min', 'NUMBER'
    attribute 'energy15Min', 'NUMBER'
    attribute 'energy1Hr', 'NUMBER'
    attribute 'energyDaily', 'NUMBER'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
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
  deleteChildDevices()
}

void deleteChildDevices() {
  List<ChildDeviceWrapper> children = getChildDevices()
  children.each { ChildDeviceWrapper child ->
    deleteChildDevice(child.getDeviceNetworkId())
  }
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

void refresh() {}
void refreshCallback(AsyncResponse response, Map data = null){}
void esConnect() {
  interfaces.eventStream.connect('http://192.168.1.70/events', [pingInterval:1])
}
void esClose() {interfaces.eventStream.close()}

String getChildDNI(String name) {
  return "${device.id}-${name.replace(' ','')}"
}

ChildDeviceWrapper getOrCreateChildDevice(String name) {
  ChildDeviceWrapper child = getChildDevice(getChildDNI(name))
  if (child == null) {
    child = addChildDevice(
      'hubitat',
      'Generic Component Power Meter',
      "${getChildDNI(name)}",
      [ name: 'Generic Component Power Monitor', label: "${name} Power Monitor" ]
    )
  }
  return child
}

void murderChildren() {deleteChildDevices()}


void parse(message) {
  logDebug("Message: ${message}")
  // Map parsedMessage = parseLanMessage(message)
  // Map jsonData = parseJson(parsedMessage.body)
  // processJson(jsonData)
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  if(jsonData?.id != null && jsonData?.id != '') {
    ChildDeviceWrapper child = getOrCreateChildDevice(jsonData?.id)
    String attr = jsonData?.measurement.toLowerCase()
    BigDecimal val
    if(jsonData?.measurement == 'Power') {
      val = jsonData?.value as BigDecimal
    } else if(jsonData?.measurement == 'Energy') {
      val = new BigDecimal(jsonData?.value).setScale(2, BigDecimal.ROUND_HALF_UP)
    } else {
      val = new BigDecimal(jsonData?.value).setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    child.parse([[name: attr, value: val, descriptionText: "${child.displayName} ${attr} is ${val}"]])
  }
}

void eventStreamStatus(String message) {
  logDebug("Event Stream Status: ${message}")
}
