/**
 * MIT License
 * Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

metadata {
    definition(name: 'ESPHome RATGDO Garage Door', namespace: 'dwinks', author: 'Daniel Winks') {
        capability 'Actuator'
        capability 'GarageDoorControl'
        capability 'Lock'
        capability 'MotionSensor'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Switch'

        command 'stop'

        attribute 'obstruction', 'enum', ['clear', 'detected']
        attribute 'position', 'number'
        attribute 'status', 'enum', ['online', 'offline']
    }

    preferences {
        input name: 'logLevel', type: 'enum', title: 'Logging level', options: [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off'], defaultValue: 'info', submitOnChange: true
    }
}

@Field static final String STATE_OPEN = 'open'
@Field static final String STATE_CLOSED = 'closed'
@Field static final String STATE_OPENING = 'opening'
@Field static final String STATE_CLOSING = 'closing'
@Field static final String STATE_PARTIALLY_OPEN = 'partially open'
@Field static final String STATE_UNKNOWN = 'unknown'
@Field static final String DEVICE_TRACKER_PREFIX = 'device-tracker-'
@Field static final String PRESENCE_CHILD_DRIVER = 'Generic Component Presence Sensor'
@Field static final String HUBITAT_IP_SET = '/text/hubitat_ip/set'

void installed() {
    initialize()
}

void updated() {
    configure()
}

void uninstalled() {
    unschedule()
}

void initialize() {
    configure()
}

void configure() {
    final String host = connectionIp()
    if (!host) {
        logWarn('RATGDO IP address is not configured')
        return
    }
    device.setDeviceNetworkId(getMACFromIP(host))
    configureHubitatCallback()
    unschedule('scheduledRefresh')
}

private void configureHubitatCallback() {
    String hubIp = location?.hub?.localIP?.toString()
    if (!hubIp) { return }
    try {
        String callback = java.net.URLEncoder.encode("http://${hubIp}:39501", 'UTF-8')
        asynchttpPost('commandCallback', [uri: "${baseUrl()}${HUBITAT_IP_SET}?value=${callback}"], [action: 'configure callback'])
    } catch (Exception exception) {
        logWarn("Unable to configure Hubitat callback URL: ${exception.message}")
    }
}

void open() {
    emitIfChanged('door', STATE_OPENING)
    sendCommandAsync('open')
}

void close() {
    emitIfChanged('door', STATE_CLOSING)
    sendCommandAsync('close')
}

void stop() {
    sendCommandAsync('stop')
}

void refresh() {
    ["${coverPath()}", "${baseUrl()}/light/${lightEntityId()}", "${baseUrl()}/lock/${remoteLockEntityId()}",
     "${baseUrl()}/binary_sensor/motion", "${baseUrl()}/binary_sensor/obstruction"].each { String uri ->
        try { asynchttpGet('refreshCallback', [uri: uri], [uri: uri]) }
        catch (Exception exception) { logWarn("RATGDO refresh failed for ${uri}: ${exception.message}") }
    }
}

void refreshCallback(AsyncResponse response, Map data = null) {
    if (response?.hasError()) { logWarn("RATGDO refresh failed for ${data?.uri ?: ''}: ${response?.getErrorData()}"); return }
    String body = response?.getData()?.toString()
    if (body) { processInboundMessage(parseInboundJson(body)) }
}

void on() {
    sendEntityCommandAsync('light', lightEntityId(), 'turn_on')
}

void off() {
    sendEntityCommandAsync('light', lightEntityId(), 'turn_off')
}

void lock() {
    sendEntityCommandAsync('remote-control lock', remoteLockEntityId(), 'lock')
}

void unlock() {
    sendEntityCommandAsync('remote-control lock', remoteLockEntityId(), 'unlock')
}

void commandCallback(AsyncResponse response, Map data = null) {
    if (response.hasError()) {
        logError("RATGDO ${data?.action ?: 'door'} command failed: ${response.getErrorData()}")
        return
    }
    logDebug("RATGDO ${data?.action ?: 'door'} command accepted")
}

private void sendCommandAsync(final String action) {
    try {
        asynchttpPost('commandCallback', [uri: "${coverPath()}/${action}"], [action: action])
    } catch (Exception exception) {
        logError("RATGDO ${action} command could not be sent: ${exception.message}")
    }
}

private void sendEntityCommandAsync(final String entityType, final String entityId, final String action) {
    try {
        asynchttpPost('commandCallback', [uri: "${baseUrl()}/${entityType}/${entityId}/${action}"], [action: action])
    } catch (Exception exception) {
        logError("RATGDO ${entityType} ${action} command could not be sent: ${exception.message}")
    }
}

private String coverPath() {
    return "${baseUrl()}/cover/door"
}

private String baseUrl() {
    final String host = connectionIp()
    final String portValue = connectionPort().toString()
    return "http://${host}:${portValue}"
}

private String lightEntityId() {
    return 'light'
}

private String remoteLockEntityId() {
    return 'remotes'
}

private String connectionIp() { return device.getDataValue('ipAddress') ?: settings?.ip?.toString()?.trim() }

private Integer connectionPort() {
    try { return Integer.valueOf(device.getDataValue('port') ?: settings?.port ?: '80') }
    catch (Exception ignored) { return 80 }
}

void parse(final String message) {
    try {
        final String trimmedMessage = message?.trim()
        if (!trimmedMessage) {
            logWarn('Received an empty RATGDO LAN message')
            return
        }

        final String body = trimmedMessage.startsWith('{') ? trimmedMessage : parseLanMessage(message)?.body?.toString()
        final Map jsonData = parseInboundJson(body)
        if (jsonData != null) {
            processInboundMessage(jsonData)
        }
    } catch (Exception exception) {
        logWarn("Unable to parse RATGDO LAN message: ${exception.message}")
    }
}

private Map parseInboundJson(final String body) {
    if (!body) {
        logWarn('Received RATGDO LAN message without a JSON body')
        return null
    }
    final Object parsed = new JsonSlurper().parseText(body)
    if (parsed instanceof Map) {
        return parsed as Map
    }
    logWarn("Unexpected RATGDO event payload: ${body}")
    return null
}

private void processInboundMessage(final Map jsonData) {
    if (!jsonData) { return }
    final String eventId = jsonData.id?.toString()
    if (!eventId) {
        logWarn("RATGDO event is missing an id: ${jsonData}")
        return
    }

    emitIfChanged('status', 'online')
    if (eventId.startsWith(DEVICE_TRACKER_PREFIX)) {
        processDeviceTrackerEvent(eventId.substring(DEVICE_TRACKER_PREFIX.length()), jsonData.value)
        return
    }

    switch (eventId) {
        case 'cover-door':
        case 'cover/door':
            processCoverEvent(jsonData.value)
            break
        case 'light-light':
        case 'light/light':
            processBooleanEvent('switch', jsonData.value, 'on', 'off', eventId)
            break
        case 'lock-remotes':
        case 'lock/remotes':
            processBooleanEvent('lock', jsonData.value, 'locked', 'unlocked', eventId)
            break
        case 'binary_sensor-motion':
        case 'binary_sensor/motion':
            processBooleanEvent('motion', jsonData.value, 'active', 'inactive', eventId)
            break
        case 'binary_sensor-obstruction':
        case 'binary_sensor/obstruction':
            processBooleanEvent('obstruction', jsonData.value, 'detected', 'clear', eventId)
            break
        default:
            logWarn("Ignoring unrecognized RATGDO event id: ${eventId}")
            break
    }
}

private void processDeviceTrackerEvent(final String trackerName, final Object value) {
    final Boolean isHome = parseBoolean(value)
    if (!trackerName || isHome == null) {
        logWarn("RATGDO device-tracker event is missing a name or boolean value: ${trackerName}")
        return
    }

    final String childDni = presenceChildDni(trackerName)
    Object child = getChildDevice(childDni)
    if (child == null) {
        try {
            child = addChildDevice(
                'hubitat',
                PRESENCE_CHILD_DRIVER,
                childDni,
                [name: PRESENCE_CHILD_DRIVER, label: "${device.displayName} - ${trackerName}", isComponent: true]
            )
            logInfo("Created presence child device for ${trackerName}")
        } catch (Exception exception) {
            logError("Unable to create presence child device for ${trackerName}: ${exception.message}")
            return
        }
    }
    sendEvent(child, [name: 'presence', value: isHome ? 'present' : 'not present',
                      descriptionText: "${trackerName} is ${isHome ? 'home' : 'away'}"])
}

private String presenceChildDni(final String trackerName) {
    final String safeTrackerName = trackerName.replaceAll(/[^A-Za-z0-9_-]/, '_')
    return "${device.deviceNetworkId}-presence-${safeTrackerName}"
}

private void processCoverEvent(final Object value) {
    final String direction = value?.toString()?.trim()?.toLowerCase()
    if (direction == STATE_OPENING || direction == STATE_CLOSING) {
        emitIfChanged('door', direction)
        return
    }

    final Boolean endpointValue = parseBoolean(value)
    if (endpointValue == null) {
        logWarn("RATGDO cover-door event has an unsupported value: ${value}")
        return
    }
    emitIfChanged('position', endpointValue ? BigDecimal.ONE : BigDecimal.ZERO)
    emitIfChanged('door', endpointValue ? STATE_OPEN : STATE_CLOSED)
}

private void processBooleanEvent(final String attribute, final Object value, final String trueValue,
                                 final String falseValue, final String eventId) {
    final Boolean eventValue = parseBoolean(value)
    if (eventValue == null) {
        logWarn("RATGDO ${eventId} event has an unsupported value: ${value}")
        return
    }
    emitIfChanged(attribute, eventValue ? trueValue : falseValue)
}

@CompileStatic
private static Boolean parseBoolean(final Object value) {
    if (value instanceof Boolean) {
        return value as Boolean
    }
    if (value instanceof Number) {
        return (value as Number).intValue() != 0
    }
    final String text = value?.toString()?.trim()?.toLowerCase()
    if (text == 'true' || text == '1') {
        return true
    }
    if (text == 'false' || text == '0') {
        return false
    }
    return null
}

private void logError(final String message) { writeLog('error', message) }
private void logWarn(final String message) { writeLog('warn', message) }
private void logDebug(final String message) { writeLog('debug', message) }
private void logInfo(final String message) { writeLog('info', message) }
private void logTrace(final String message) { writeLog('trace', message) }

private void writeLog(final String level, final String message) {
    List<String> levels = ['trace', 'debug', 'info', 'warn', 'error', 'off']
    String configured = levels.contains(settings?.logLevel?.toString()) ? settings.logLevel.toString() : 'info'
    if (configured == 'off' || levels.indexOf(level) < levels.indexOf(configured)) { return }
    String prefix = device?.displayName ?: 'ESPHome RATGDO'
    if (level == 'error') { log.error("${prefix}: ${message}") }
    else if (level == 'warn') { log.warn("${prefix}: ${message}") }
    else if (level == 'info') { log.info("${prefix}: ${message}") }
    else if (level == 'debug') { log.debug("${prefix}: ${message}") }
    else { log.trace("${prefix}: ${message}") }
}

private void emitIfChanged(final String attribute, final Object value) {
    if (device.currentValue(attribute) != value) {
        sendEvent(name: attribute, value: value, descriptionText: "${attribute} is ${value}")
    }
}
