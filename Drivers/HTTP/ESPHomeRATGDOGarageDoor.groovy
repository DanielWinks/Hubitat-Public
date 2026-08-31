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
        capability 'Sensor'
        capability 'Switch'

        command 'stop'

        attribute 'obstruction', 'enum', ['clear', 'detected']
        attribute 'position', 'number'
        attribute 'status', 'enum', ['online', 'offline']
    }

    preferences {
        input name: 'logEnable', type: 'bool', title: 'Enable Logging', required: false, defaultValue: true
        input name: 'debugLogEnable', type: 'bool', title: 'Enable Debug Logging', required: false, defaultValue: true
        input name: 'ip', type: 'text', title: 'RATGDO IP Address', required: true
        input name: 'port', type: 'number', title: 'HTTP Port', required: true, defaultValue: 80
        input name: 'coverEntityId', type: 'text', title: 'ESPHome Cover Entity ID',
            required: true, defaultValue: 'door',
            description: 'The REST entity ID, normally derived from the ESPHome cover name. For name "Door", use "door".'
        input name: 'lightEntityId', type: 'text', title: 'ESPHome Light Entity ID',
            required: true, defaultValue: 'light',
            description: 'The REST entity ID for the RATGDO light. The supplied configuration uses "light".'
        input name: 'remoteLockEntityId', type: 'text', title: 'ESPHome Remote Lock Entity ID',
            required: true, defaultValue: 'remotes',
            description: 'The REST entity ID for the RATGDO remote-control lock. The supplied configuration uses "remotes".'
    }
}

@Field static final String STATE_OPEN = 'open'
@Field static final String STATE_CLOSED = 'closed'
@Field static final String STATE_OPENING = 'opening'
@Field static final String STATE_CLOSING = 'closing'
@Field static final String STATE_PARTIALLY_OPEN = 'partially open'
@Field static final String STATE_UNKNOWN = 'unknown'

void installed() {
    initialize()
    if (settings.debugLogEnable != false) {
        runIn(1800, 'debugLogsOff')
    }
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
    final String host = settings.ip?.toString()?.trim()
    if (!host) {
        logWarn('RATGDO IP address is not configured')
        return
    }
    device.setDeviceNetworkId(getMACFromIP(host))
    unschedule('scheduledRefresh')
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
    final String entityId = settings.coverEntityId?.toString()?.trim() ?: 'door'
    return "${baseUrl()}/cover/${entityId}"
}

private String baseUrl() {
    final String host = settings.ip?.toString()?.trim()
    final String portValue = settings.port?.toString()?.trim() ?: '80'
    return "http://${host}:${portValue}"
}

private String lightEntityId() {
    return settings.lightEntityId?.toString()?.trim() ?: 'light'
}

private String remoteLockEntityId() {
    return settings.remoteLockEntityId?.toString()?.trim() ?: 'remotes'
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
    final String eventId = jsonData.id?.toString()
    if (!eventId) {
        logWarn("RATGDO event is missing an id: ${jsonData}")
        return
    }

    emitIfChanged('status', 'online')
    switch (eventId) {
        case 'cover-door':
            processCoverEvent(jsonData.value)
            break
        case 'light-light':
            processBooleanEvent('switch', jsonData.value, 'on', 'off', eventId)
            break
        case 'lock-remotes':
            processBooleanEvent('lock', jsonData.value, 'locked', 'unlocked', eventId)
            break
        case 'binary_sensor-motion':
            processBooleanEvent('motion', jsonData.value, 'active', 'inactive', eventId)
            break
        case 'binary_sensor-obstruction':
            processBooleanEvent('obstruction', jsonData.value, 'detected', 'clear', eventId)
            break
        default:
            logWarn("Ignoring unrecognized RATGDO event id: ${eventId}")
            break
    }
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

private void logError(final String message) {
    if (settings.logEnable != false) {
        log.error("${device.displayName}: ${message}")
    }
}

private void logWarn(final String message) {
    if (settings.logEnable != false) {
        log.warn("${device.displayName}: ${message}")
    }
}

private void logDebug(final String message) {
    if (settings.logEnable != false && settings.debugLogEnable != false) {
        log.debug("${device.displayName}: ${message}")
    }
}

void debugLogsOff() {
    logWarn('Debug logging disabled')
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'])
}

private void emitIfChanged(final String attribute, final Object value) {
    if (device.currentValue(attribute) != value) {
        sendEvent(name: attribute, value: value, descriptionText: "${attribute} is ${value}")
    }
}
