
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
import com.hubitat.app.DeviceWrapper
import groovy.json.JsonOutput
import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

metadata {
  definition (name: 'HTTP Multi Switch',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability 'Switch'
    command 'restartESP'
    command 'checkConnection'
    command 'refresh'
    command 'initialize'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
    attribute 'acRelay1State', 'ENUM', ['on', 'off']
    attribute 'acRelay2State', 'ENUM', ['on', 'off']
    attribute 'acRelay3State', 'ENUM', ['on', 'off']
    attribute 'usbRelayState', 'ENUM', ['on', 'off']
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

@Field static final String RELAY1 = 'switch-ac_relay_1'
@Field static final String RELAY2 = 'switch-ac_relay_2'
@Field static final String RELAY3 = 'switch-ac_relay_3'
@Field static final String USBRELAY = 'switch-usb_relay'

@Field static final String RELAY1_STATE = '/switch/ac_relay_1'
@Field static final String RELAY2_STATE = '/switch/ac_relay_2'
@Field static final String RELAY3_STATE = '/switch/ac_relay_3'
@Field static final String USBRELAY_STATE = '/switch/usb_relay'

String getACRelay1DNI() { return "${device.id}-${RELAY1}" }
String getACRelay2DNI() { return "${device.id}-${RELAY2}" }
String getACRelay3DNI() { return "${device.id}-${RELAY3}" }
String getUSBRelayDNI() { return "${device.id}-${USBRELAY}" }

String turnOnACRelay1CommandTopic() { return "${RELAY1_STATE}/turn_on" }
String turnOnACRelay2CommandTopic() { return "${RELAY2_STATE}/turn_on" }
String turnOnACRelay3CommandTopic() { return "${RELAY3_STATE}/turn_on" }
String turnOnUSBRelayCommandTopic() { return "${USBRELAY_STATE}/turn_on" }

String turnOffACRelay1CommandTopic() { return "${RELAY1_STATE}/turn_off" }
String turnOffACRelay2CommandTopic() { return "${RELAY2_STATE}/turn_off" }
String turnOffACRelay3CommandTopic() { return "${RELAY3_STATE}/turn_off" }
String turnOffUSBRelayCommandTopic() { return "${USBRELAY_STATE}/turn_off" }


void createChildDevices() {
  if (getChildDevice(getACRelay1DNI()) == null) {
    ChildDeviceWrapper relay1 = addChildDevice(
      'hubitat', 'Generic Component Switch', getACRelay1DNI(),
      [ name: 'Generic Component Switch', label: "${device.displayName} AC Relay 1" ]
    )
  }

  if (getChildDevice(getACRelay2DNI()) == null) {
    ChildDeviceWrapper relay2 = addChildDevice(
      'hubitat', 'Generic Component Switch', getACRelay2DNI(),
      [ name: 'Generic Component Switch', label: "${device.displayName} AC Relay 2" ]
    )
  }

  if (getChildDevice(getACRelay3DNI()) == null) {
    ChildDeviceWrapper relay3 = addChildDevice(
      'hubitat', 'Generic Component Switch', getACRelay3DNI(),
      [ name: 'Generic Component Switch', label: "${device.displayName} AC Relay 3" ]
    )
  }

  if (getChildDevice(getUSBRelayDNI()) == null) {
    ChildDeviceWrapper relayUsb = addChildDevice(
      'hubitat', 'Generic Component Switch', getUSBRelayDNI(),
      [ name: 'Generic Component Switch', label: "${device.displayName} USB Relay" ]
    )
  }
}

void refresh() {
  sendQueryAsync(RELAY1_STATE, 'refreshCallback', null)
  sendQueryAsync(RELAY2_STATE, 'refreshCallback', null)
  sendQueryAsync(RELAY3_STATE, 'refreshCallback', null)
  sendQueryAsync(USBRELAY_STATE, 'refreshCallback', null)
}

void refreshCallback(AsyncResponse response, Map data = null) {
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }

  Map jsonData = parseJson(response.getData())
  if(jsonData != null) { processJson(jsonData) }
}

void on() {
  sendCommandAsync(turnOnACRelay1CommandTopic(), null, null)
  sendCommandAsync(turnOnACRelay2CommandTopic(), null, null)
  sendCommandAsync(turnOnACRelay3CommandTopic(), null, null)
}

void off() {
  sendCommandAsync(turnOffACRelay1CommandTopic(), null, null)
  sendCommandAsync(turnOffACRelay2CommandTopic(), null, null)
  sendCommandAsync(turnOffACRelay3CommandTopic(), null, null)
}

void componentOff(DeviceWrapper device) {
  String dni = device.deviceNetworkId
  if (dni == getACRelay1DNI()) { sendCommandAsync(turnOffACRelay1CommandTopic(), null, null) }
  else if (dni == getACRelay2DNI()) { sendCommandAsync(turnOffACRelay2CommandTopic(), null, null) }
  else if (dni == getACRelay3DNI()) { sendCommandAsync(turnOffACRelay3CommandTopic(), null, null) }
  else if (dni == getUSBRelayDNI()) { sendCommandAsync(turnOffUSBRelayCommandTopic(), null, null) }
}

void componentOn(DeviceWrapper device) {
  String dni = device.deviceNetworkId
  if (dni == getACRelay1DNI()) { sendCommandAsync(turnOnACRelay1CommandTopic(), null, null) }
  else if (dni == getACRelay2DNI()) { sendCommandAsync(turnOnACRelay2CommandTopic(), null, null) }
  else if (dni == getACRelay3DNI()) { sendCommandAsync(turnOnACRelay3CommandTopic(), null, null) }
  else if (dni == getUSBRelayDNI()) { sendCommandAsync(turnOnUSBRelayCommandTopic(), null, null) }
}

void componentRefresh(DeviceWrapper device) {
  String dni = device.deviceNetworkId
  if (dni == getACRelay1DNI()) { sendQueryAsync(RELAY1_STATE, 'refreshCallback', null) }
  else if (dni == getACRelay2DNI()) { sendQueryAsync(RELAY2_STATE, 'refreshCallback', null) }
  else if (dni == getACRelay3DNI()) { sendQueryAsync(RELAY3_STATE, 'refreshCallback', null) }
  else if (dni == getUSBRelayDNI()) { sendQueryAsync(USBRELAY_STATE, 'refreshCallback', null) }
}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonData = parseJson(parsedMessage.body)
  if(jsonData != null) { processJson(jsonData) }
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  ChildDeviceWrapper child = null
  if(jsonData?.id == RELAY1)   { child = getChildDevice(getACRelay1DNI()) }
  else if(jsonData?.id == RELAY2)   { child = getChildDevice(getACRelay2DNI()) }
  else if(jsonData?.id == RELAY3)   { child = getChildDevice(getACRelay3DNI()) }
  else if(jsonData?.id == USBRELAY) { child = getChildDevice(getUSBRelayDNI()) }
  if(child != null) {
    String val = jsonData?.value ? 'on' : 'off'
    child.parse([[name: 'switch', value: val, descriptionText: "${child.displayName} is ${val}"]])
    updateMasterSwitch()
  }
}

void updateMasterSwitch() {
  Boolean allOn = [getACRelay1DNI(), getACRelay2DNI(), getACRelay3DNI(), getUSBRelayDNI()].every { String dni ->
    getChildDevice(dni)?.currentValue('switch') == 'on'
  }
  String val = allOn ? 'on' : 'off'
  sendEvent(name: 'switch', value: val, descriptionText: "Master switch is now ${val}")
}
