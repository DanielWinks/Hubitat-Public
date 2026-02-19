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

// =============================================================================
// Metadata
// =============================================================================

metadata {
  definition(name: 'HTTP Power Monitoring Switch', namespace: 'dwinks', author: 'Daniel Winks', importUrl: '') {
    capability 'Switch'
    capability 'Refresh'
    capability 'PowerMeter'             // power - NUMBER, unit:W
    capability 'VoltageMeasurement'     // voltage - NUMBER, unit:V  frequency - NUMBER, unit:Hz
    capability 'EnergyMeter'            // energy - NUMBER, unit:kWh
    capability 'CurrentMeter'           // amperage - NUMBER, unit:A
    capability 'Sensor'

    command 'restartESP'
    command 'checkConnection'
    command 'initialize'

    attribute 'energy5Min', 'NUMBER'
    attribute 'energy15Min', 'NUMBER'
    attribute 'energy1Hr', 'NUMBER'
    attribute 'energyDaily', 'NUMBER'
    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

  preferences {
    section('Device Settings') {
      input 'ip', 'string', title: 'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title: 'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'powerMonitoringEnable', 'bool', title: 'Enable Power Monitoring', description: 'Toggle power monitoring reporting on the ESPHome device', required: false, defaultValue: true
    }
    section('Reporting Deltas') {
      input 'voltageDelta', 'decimal', title: 'Voltage Delta (V)', description: 'Min change before reporting', required: false, defaultValue: 1.0
      input 'currentDelta', 'decimal', title: 'Current Delta (A)', description: 'Min change before reporting', required: false, defaultValue: 0.1
      input 'powerDelta', 'decimal', title: 'Power Delta (W)', description: 'Min change before reporting', required: false, defaultValue: 1.0
      input 'energyDelta', 'decimal', title: 'Energy Delta (Wh)', description: 'Min change before reporting', required: false, defaultValue: 1.0
    }
    section('Logging') {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: true
      input 'traceLogEnable', 'bool', title: 'Enable trace logging', required: false, defaultValue: false
    }
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

void logsOff() { logWarn('Logging disabled'); device.updateSetting('logEnable', [value: 'false', type: 'bool']) }
void debugLogsOff() { logWarn('Debug logging disabled'); device.updateSetting('debugLogEnable', [value: 'false', type: 'bool']) }
void traceLogsOff() { logWarn('Trace logging disabled'); device.updateSetting('traceLogEnable', [value: 'false', type: 'bool']) }

// =============================================================================
// Lifecycle
// =============================================================================

void installed() {
  logDebug('Installed...')
  device.updateSetting('powerMonitoringEnable', [value: 'true', type: 'bool'])
  device.updateSetting('voltageDelta', [value: '1', type: 'decimal'])
  device.updateSetting('currentDelta', [value: '0.1', type: 'decimal'])
  device.updateSetting('powerDelta', [value: '1', type: 'decimal'])
  device.updateSetting('energyDelta', [value: '1', type: 'decimal'])
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
// Utilities
// =============================================================================

String prettyJson(Map jsonInput) { return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput)) }

// =============================================================================
// ESPHome HTTP
// =============================================================================

@Field static final String UPTIME_STATE = '/sensor/uptime'
@Field static final String RESTART_ESP = '/button/restart/press'
@Field static final String POWER_MONITORING_STATE = '/switch/power_monitoring_enabled'
@Field static final String VOLTAGE_DELTA = '/number/voltage_reporting_delta'
@Field static final String CURRENT_DELTA = '/number/current_reporting_delta'
@Field static final String POWER_DELTA = '/number/power_reporting_delta'
@Field static final String ENERGY_DELTA = '/number/energy_reporting_delta'

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
  String pmPath = settings.powerMonitoringEnable != false ? "${POWER_MONITORING_STATE}/turn_on" : "${POWER_MONITORING_STATE}/turn_off"
  sendCommandAsync(pmPath, null, null)
  syncReportingDeltas()
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
// Reporting Deltas
// =============================================================================

void syncReportingDeltas() {
  BigDecimal vDelta = (settings.voltageDelta ?: 1.0) as BigDecimal
  BigDecimal cDelta = (settings.currentDelta ?: 0.1) as BigDecimal
  BigDecimal pDelta = (settings.powerDelta ?: 1.0) as BigDecimal
  BigDecimal eDelta = (settings.energyDelta ?: 1.0) as BigDecimal
  sendCommandAsync("${VOLTAGE_DELTA}/set?value=${vDelta}", null, null)
  sendCommandAsync("${CURRENT_DELTA}/set?value=${cDelta}", null, null)
  sendCommandAsync("${POWER_DELTA}/set?value=${pDelta}", null, null)
  sendCommandAsync("${ENERGY_DELTA}/set?value=${eDelta}", null, null)
  runIn(2, 'readReportingDeltasFromDevice')
}

void readReportingDeltasFromDevice() {
  sendQueryAsync(VOLTAGE_DELTA, 'reportingDeltaCallback', [setting: 'voltageDelta'])
  sendQueryAsync(CURRENT_DELTA, 'reportingDeltaCallback', [setting: 'currentDelta'])
  sendQueryAsync(POWER_DELTA, 'reportingDeltaCallback', [setting: 'powerDelta'])
  sendQueryAsync(ENERGY_DELTA, 'reportingDeltaCallback', [setting: 'energyDelta'])
}

void reportingDeltaCallback(AsyncResponse response, Map data = null) {
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  Map responseJson = parseJson(response.getData())
  logDebug("Reporting delta sync: ${data.setting} = ${responseJson.value}")
  device.updateSetting(data.setting, [value: "${responseJson.value}", type: 'decimal'])
}

// =============================================================================
// Switch & Power Monitoring
// =============================================================================

@Field static final String RELAY_ID = 'switch-relay'
@Field static final String RELAY_STATE = '/switch/relay'
@Field static final String VOLTAGE_ID = 'sensor-voltage'
@Field static final String VOLTAGE_STATE = '/sensor/voltage'
@Field static final String FREQUENCY_ID = 'sensor-frequency'
@Field static final String CURRENT_ID = 'sensor-current'
@Field static final String CURRENT_STATE = '/sensor/current'
@Field static final String POWER_ID = 'sensor-power'
@Field static final String POWER_STATE = '/sensor/power'
@Field static final String ENERGY_ID = 'sensor-total_daily_energy'
@Field static final String ENERGY_STATE = '/sensor/total_daily_energy'

String turnOnRelayCommandTopic() { return "${RELAY_STATE}/turn_on" }
String turnOffRelayCommandTopic() { return "${RELAY_STATE}/turn_off" }

void on() { sendCommandAsync(turnOnRelayCommandTopic(), null, null) }
void off() { sendCommandAsync(turnOffRelayCommandTopic(), null, null) }

void refresh() {
  sendQueryAsync(RELAY_STATE, 'refreshCallback', null)
  sendQueryAsync(VOLTAGE_STATE, 'refreshCallback', null)
  sendQueryAsync(CURRENT_STATE, 'refreshCallback', null)
  sendQueryAsync(POWER_STATE, 'refreshCallback', null)
  sendQueryAsync(ENERGY_STATE, 'refreshCallback', null)
}

void refreshCallback(AsyncResponse response, Map data = null) {
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  Map responseJson = parseJson(response.getData())
  processJson(responseJson)
}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonData = parseJson(parsedMessage.body)
  processJson(jsonData)
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  if(jsonData?.id == RELAY_ID) {
    String val = jsonData?.value ? 'on' : 'off'
    sendEvent(name: 'switch', value: val, descriptionText: "Relay is now ${val}", isStateChange: true)
  }
  if(jsonData?.id == VOLTAGE_ID) {
    sendEvent(name: 'voltage', value: jsonData?.value, descriptionText: "Voltage is now ${jsonData?.value}", isStateChange: true)
  }
  if(jsonData?.id == FREQUENCY_ID) {
    sendEvent(name: 'frequency', value: jsonData?.value, descriptionText: "Frequency is now ${jsonData?.value}", isStateChange: true)
  }
  if(jsonData?.id == POWER_ID) {
    sendEvent(name: 'power', value: jsonData?.value, descriptionText: "Power is now ${jsonData?.value}", isStateChange: true)
  }
  if(jsonData?.id == CURRENT_ID) {
    sendEvent(name: 'amperage', value: jsonData?.value, descriptionText: "Amperage is now ${jsonData?.value}", isStateChange: true)
  }
  if(jsonData?.id == ENERGY_ID) {
    sendEvent(name: 'energy', value: jsonData?.value, descriptionText: "Energy is now ${jsonData?.value}", isStateChange: true)
  }
}
