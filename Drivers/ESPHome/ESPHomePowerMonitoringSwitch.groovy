/**
 * MIT License
 * Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
 */

import groovy.json.JsonSlurper
import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

@Field static final String RELAY_STATE = '/switch/relay'
@Field static final String VOLTAGE_STATE = '/sensor/voltage'
@Field static final String FREQUENCY_STATE = '/sensor/frequency'
@Field static final String CURRENT_STATE = '/sensor/current'
@Field static final String POWER_STATE = '/sensor/power'
@Field static final String ENERGY_STATE = '/sensor/total_daily_energy'
@Field static final String HUBITAT_IP_STATE = '/text/hubitat_ip/set'
@Field static final String RELAY_RESTORE_STATE = '/select/relay_restore_mode/select'
@Field static final String VOLTAGE_DELTA_STATE = '/number/voltage_reporting_delta/set'
@Field static final String CURRENT_DELTA_STATE = '/number/current_reporting_delta/set'
@Field static final String POWER_DELTA_STATE = '/number/power_reporting_delta/set'

metadata {
    definition(name: 'ESPHome Power Monitoring Switch', namespace: 'dwinks', author: 'Daniel Winks') {
        capability 'Switch'
        capability 'Refresh'
        capability 'PowerMeter'
        capability 'VoltageMeasurement'
        capability 'CurrentMeter'
        capability 'EnergyMeter'
        capability 'Sensor'
        attribute 'frequency', 'NUMBER'
        attribute 'networkStatus', 'ENUM', ['online', 'offline']
        attribute 'status', 'ENUM', ['online', 'offline']
    }
    preferences {
        section('ESPHome connection') {
            input name: 'relayRestoreMode', type: 'enum', title: 'Relay restore mode', options: [ALWAYS_OFF: 'ALWAYS_OFF', ALWAYS_ON: 'ALWAYS_ON', RESTORE_DEFAULT_OFF: 'RESTORE_DEFAULT_OFF', RESTORE_DEFAULT_ON: 'RESTORE_DEFAULT_ON'], defaultValue: 'ALWAYS_OFF', required: false
        }
        section('Reporting deltas') {
            input name: 'voltageDelta', type: 'decimal', title: 'Voltage delta (V)', defaultValue: 1.0, required: false
            input name: 'currentDelta', type: 'decimal', title: 'Current delta (A)', defaultValue: 0.1, required: false
            input name: 'powerDelta', type: 'decimal', title: 'Power delta (W)', defaultValue: 1.0, required: false
        }
        section('Logging') {
            input name: 'logLevel', type: 'enum', title: 'Logging level', options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'], defaultValue: 'info', submitOnChange: true
        }
    }
}

void installed() { initialize() }
void updated() { initialize() }
void uninstalled() { unschedule() }

void initialize() {
    clearLegacyState()
    if (!connectionIp()) { sendOffline(); return }
    configureEspHome()
    checkConnection()
    refresh()
    unschedule()
    runEvery5Minutes('checkConnection')
}

private void clearLegacyState() {
    // Remove state used by the former native-API implementation. These keys
    // otherwise remain visible after a driver migration or child recreation.
    ['apiVersionMajor', 'apiVersionMinor', 'entities', 'networkStatus', 'services',
     'requireRefresh', 'noiseDetected', 'reconnectDelay', 'configQueue', 'refreshQueue'].each { String key ->
        state.remove(key)
    }
}

void configureEspHome() {
    state.configQueue = [
        [path: HUBITAT_IP_STATE, value: connectionCallbackUrl()],
        [path: RELAY_RESTORE_STATE, value: settings.relayRestoreMode ?: 'ALWAYS_OFF'],
        [path: VOLTAGE_DELTA_STATE, value: settings.voltageDelta ?: 1.0],
        [path: CURRENT_DELTA_STATE, value: settings.currentDelta ?: 0.1],
        [path: POWER_DELTA_STATE, value: settings.powerDelta ?: 1.0]
    ]
    sendNextConfigRequest()
}

void sendNextConfigRequest() {
    List queue = (state.configQueue ?: []) as List
    if (!queue) { state.remove('configQueue'); return }
    Map request = queue.remove(0) as Map
    if (queue) { state.configQueue = queue } else { state.remove('configQueue') }
    postEndpoint(request.path.toString(), request.value)
    if (queue) { runInMillis(750, 'sendNextConfigRequest') }
}

void on() { postEndpoint("${RELAY_STATE}/turn_on", null) }
void off() { postEndpoint("${RELAY_STATE}/turn_off", null) }

void refresh() {
    state.refreshQueue = [RELAY_STATE, VOLTAGE_STATE, FREQUENCY_STATE, CURRENT_STATE, POWER_STATE, ENERGY_STATE]
    sendNextRefreshRequest()
}

void sendNextRefreshRequest() {
    List queue = (state.refreshQueue ?: []) as List
    if (!queue) { state.remove('refreshQueue'); return }
    String path = queue.remove(0).toString()
    if (queue) { state.refreshQueue = queue } else { state.remove('refreshQueue') }
    getEndpoint(path)
    if (queue) { runInMillis(400, 'sendNextRefreshRequest') }
}

void checkConnection() { getEndpoint('/') }

void getEndpoint(String path) {
    if (!connectionIp()) { sendOffline(); return }
    Map params = [uri: "http://${connectionIp()}:${connectionPort()}${path}", contentType: 'application/json']
    try { asynchttpGet('httpGetCallback', params, [path: path]) }
    catch (Exception exception) { logWarn("GET ${path} failed: ${exception.message}"); sendOffline() }
}

void postEndpoint(String path, Object value) {
    if (!connectionIp()) { sendOffline(); return }
    String suffix = ''
    if (value != null) {
        String parameter = path.contains('/select/') ? 'option' : 'value'
        suffix = "?${parameter}=${java.net.URLEncoder.encode(value.toString(), 'UTF-8')}"
    }
    Map params = [uri: "http://${connectionIp()}:${connectionPort()}${path}${suffix}", contentType: 'application/json']
    try { asynchttpPost('httpPostCallback', params, [path: path]) }
    catch (Exception exception) { logWarn("POST ${path} failed: ${exception.message}"); sendOffline() }
}

void httpGetCallback(AsyncResponse response, Map data = null) {
    if (response?.hasError()) { logWarn("GET ${data?.path ?: ''} failed (status ${response?.status}): ${response?.getErrorData()}"); sendOffline(); return }
    sendOnline()
    processJson(parseJsonResponse(response?.getData()))
}

void httpPostCallback(AsyncResponse response, Map data = null) {
    if (response?.hasError()) { logWarn("POST ${data?.path ?: ''} failed (status ${response?.status}): ${response?.getErrorData()}"); sendOffline(); return }
    sendOnline()
}

void parse(message) {
    Map parsed = parseLanMessage(message)
    String body = parsed?.body?.toString() ?: message?.toString()
    processJson(parseJsonResponse(body))
    if (body) { sendOnline() }
}

private Map parseJsonResponse(Object body) {
    try {
        Object value = new JsonSlurper().parseText(body?.toString() ?: '{}')
        return value instanceof Map ? value as Map : [:]
    } catch (Exception ignored) { return [:] }
}

private void processJson(Map jsonData) {
    if (!jsonData) { return }
    String id = jsonData.id?.toString()
    Object value = jsonData.value
    if (id == 'switch-relay') { sendEvent(name: 'switch', value: value ? 'on' : 'off', isStateChange: true) }
    else if (id == 'sensor-voltage') { sendEvent(name: 'voltage', value: value, unit: 'V', isStateChange: true) }
    else if (id == 'sensor-frequency') { sendEvent(name: 'frequency', value: value, unit: 'Hz', isStateChange: true) }
    else if (id == 'sensor-current') { sendEvent(name: 'amperage', value: value, unit: 'A', isStateChange: true) }
    else if (id == 'sensor-power') { sendEvent(name: 'power', value: value, unit: 'W', isStateChange: true) }
    else if (id == 'sensor-total_daily_energy') { sendEvent(name: 'energy', value: value, unit: 'kWh', isStateChange: true) }
}

private void sendOnline() { sendEvent(name: 'networkStatus', value: 'online'); sendEvent(name: 'status', value: 'online') }
private void sendOffline() { sendEvent(name: 'networkStatus', value: 'offline'); sendEvent(name: 'status', value: 'offline') }
private String defaultCallbackUrl() { return "http://${location.hub.localIP}:39501" }
private String connectionIp() { return device.getDataValue('ipAddress') ?: settings?.ipAddress?.toString() }
private Integer connectionPort() {
    try { return Integer.valueOf(device.getDataValue('port') ?: settings?.port ?: '80') }
    catch (Exception ignored) { return 80 }
}
private String connectionCallbackUrl() { return device.getDataValue('hubitatCallbackUrl') ?: settings?.hubitatCallbackUrl ?: defaultCallbackUrl() }

void logError(String message) { writeLog('error', message) }
void logWarn(String message) { writeLog('warn', message) }
void logInfo(String message) { writeLog('info', message) }
void logDebug(String message) { writeLog('debug', message) }
void logTrace(String message) { writeLog('trace', message) }

private void writeLog(String level, String message) {
    List<String> levels = ['trace', 'debug', 'info', 'warn', 'error', 'off']
    String configured = levels.contains(settings?.logLevel?.toString()) ? settings.logLevel.toString() : 'info'
    if (configured == 'off' || levels.indexOf(level) < levels.indexOf(configured)) { return }
    String prefix = device?.displayName ?: 'ESPHome'
    if (level == 'error') { log.error("${prefix}: ${message}") }
    else if (level == 'warn') { log.warn("${prefix}: ${message}") }
    else if (level == 'info') { log.info("${prefix}: ${message}") }
    else if (level == 'debug') { log.debug("${prefix}: ${message}") }
    else { log.trace("${prefix}: ${message}") }
}
