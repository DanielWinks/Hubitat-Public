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

import groovy.transform.CompileStatic
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

metadata {
    definition(name: 'Third Reality Smart Plug', namespace: 'dwinks', author: 'Daniel Winks') {
        capability 'Actuator'
        capability 'Configuration'
        capability 'HealthCheck'
        capability 'Outlet'
        capability 'Refresh'
        capability 'Switch'

        command 'toggle'
        command 'updateFirmware'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']

        fingerprint model: '3RSP019BZ', manufacturer: 'Third Reality, Inc'
    }

    preferences {
        input name: 'powerRestore', type: 'enum', title: '<b>Power Restore Mode</b>',
            options: PowerRestoreOpts.options, defaultValue: PowerRestoreOpts.defaultValue,
            description: '<i>Changes what happens when power is restored to the outlet.</i>'

        input name: 'HealthCheckInterval', type: 'enum', title: '<b>Health Check Interval</b>',
            options: HealthCheckIntervalOpts.options, defaultValue: HealthCheckIntervalOpts.defaultValue,
            description: '<i>Changes how often the hub pings the outlet to check health.</i>'

        input name: 'disableOnOff', type: 'bool', title: '<b>Disable On/Off Commands</b>',
            defaultValue: false, description: '<i>Disables driver switch commands.</i>'

        input name: 'txtEnable', type: 'bool', title: '<b>Enable Description Logging</b>',
            defaultValue: true, description: '<i>Enables command logging.</i>'

        input name: 'logEnable', type: 'bool', title: '<b>Enable Debug Logging</b>',
            defaultValue: false, description: '<i>Turns on debug logging for 30 minutes.</i>'
    }
}

@Field static final String VERSION = '1.00 (2026-08-31)'
@Field static final int FIRMWARE_VERSION_ID = 0x4000
@Field static final int PING_ATTR_ID = 0x01
@Field static final int POWER_ON_OFF_ID = 0x0000
@Field static final int POWER_RESTORE_ID = 0x4003
@Field static final int COMMAND_TIMEOUT = 10
@Field static final int DELAY_MS = 200

@Field static final Map PowerRestoreOpts = [
    defaultValue: 0xFF,
    options: [0x00: 'Off', 0x01: 'On', 0xFF: 'Last State']
]

@Field static final Map HealthCheckIntervalOpts = [
    defaultValue: 10,
    options: [10: 'Every 10 Mins', 15: 'Every 15 Mins', 30: 'Every 30 Mins',
              45: 'Every 45 Mins', 59: 'Every Hour', 0: 'Disabled']
]

void installed() {
    logInfo('installed')
    sendEvent(name: 'healthStatus', value: 'unknown')
    sendEvent(name: 'switch', value: 'off')
}

void updated() {
    logInfo('updated...')
    logInfo("${device} driver version ${VERSION}")
    unschedule()

    if (settings.logEnable) {
        runIn(1800, 'logsOff')
    }

    final int interval = (settings.HealthCheckInterval as Integer) ?: 0
    if (interval > 0) {
        logInfo("${device} scheduling health check every ${interval} minutes")
        scheduleDeviceHealthCheck(interval)
    }

    runIn(1, 'configure')
}

List<String> configure() {
    logInfo('configure...')
    final List<String> commands = []

    if (settings.powerRestore != null) {
        commands += zigbee.writeAttribute(zigbee.ON_OFF_CLUSTER, POWER_RESTORE_ID,
            DataType.ENUM8, settings.powerRestore as Integer, [:], DELAY_MS)
    }

    runIn(5, 'refresh')
    return commands
}

List<String> refresh() {
    logInfo('refresh')
    scheduleCommandTimeoutCheck()
    return zigbee.readAttribute(zigbee.BASIC_CLUSTER, [FIRMWARE_VERSION_ID, PING_ATTR_ID], [:], DELAY_MS) +
        zigbee.readAttribute(zigbee.ON_OFF_CLUSTER, [POWER_RESTORE_ID, POWER_ON_OFF_ID], [:], DELAY_MS)
}

List<String> on() {
    if (settings.disableOnOff) {
        return []
    }
    logInfo('turn on')
    state.isDigital = true
    scheduleCommandTimeoutCheck()
    return zigbee.on()
}

List<String> off() {
    if (settings.disableOnOff) {
        return []
    }
    logInfo('turn off')
    state.isDigital = true
    scheduleCommandTimeoutCheck()
    return zigbee.off()
}

List<String> toggle() {
    if (settings.disableOnOff) {
        return []
    }
    logInfo('toggle')
    state.isDigital = true
    scheduleCommandTimeoutCheck()
    return zigbee.command(zigbee.ON_OFF_CLUSTER, 0x02, [:], 0)
}

List<String> ping() {
    logDebug('ping...')
    scheduleCommandTimeoutCheck()
    return zigbee.readAttribute(zigbee.BASIC_CLUSTER, PING_ATTR_ID, [:], 0)
}

List<String> updateFirmware() {
    logInfo('checking for firmware updates')
    return zigbee.updateFirmware()
}

void parse(final String description) {
    final Map descriptionMap = zigbee.parseDescriptionAsMap(description)
    updateAttribute('healthStatus', 'online')
    unschedule('deviceCommandTimeout')

    if (descriptionMap.profileId == '0000') {
        return
    }

    if (descriptionMap.clusterInt == zigbee.BASIC_CLUSTER) {
        parseBasicCluster(descriptionMap)
    } else if (descriptionMap.clusterInt == zigbee.ON_OFF_CLUSTER) {
        parseOnOffCluster(descriptionMap)
    }
}

void parseBasicCluster(final Map descriptionMap) {
    switch (descriptionMap.attrInt as Integer) {
        case PING_ATTR_ID:
            logDebug('pong')
            break
        case FIRMWARE_VERSION_ID:
            updateDataValue('softwareBuild', descriptionMap.value ?: 'unknown')
            break
        default:
            logDebug("unknown Basic cluster attribute: ${descriptionMap}")
            break
    }
}

void parseOnOffCluster(final Map descriptionMap) {
    switch (descriptionMap.attrInt as Integer) {
        case POWER_ON_OFF_ID:
            final String type = state.isDigital == true ? 'digital' : 'physical'
            state.remove('isDigital')
            updateAttribute('switch', switchValue(descriptionMap.value as String), type)
            break
        case POWER_RESTORE_ID:
            final String rawValue = descriptionMap.value as String
            final Integer value = parseHexValue(rawValue)
            final String mode = powerRestoreMode(value, rawValue)
            logInfo("${device} power restore mode is '${mode}'")
            device.updateSetting('powerRestore', [value: value.toString(), type: 'enum'])
            break
        default:
            logDebug("unknown On/Off cluster attribute: ${descriptionMap}")
            break
    }
}

void deviceCommandTimeout() {
    logWarn('no response received (device offline?)')
    updateAttribute('healthStatus', 'offline')
}

void logsOff() {
    logWarn('debug logging disabled...')
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

private void scheduleCommandTimeoutCheck(final int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

private void scheduleDeviceHealthCheck(final int intervalMin) {
    final Random random = new Random()
    schedule(healthCheckSchedule(random.nextInt(59), intervalMin), 'ping')
}

private void updateAttribute(final String attribute, final Object value, final String type = null) {
    if (device.currentValue(attribute) != value && settings.txtEnable) {
        logInfo("${attribute} was set to ${value}")
    }
    sendEvent(name: attribute, value: value, type: type)
}

private void logDebug(final String message) {
    if (settings.logEnable) {
        log.debug "${device}: ${message}"
    }
}

private void logInfo(final String message) {
    if (settings.logEnable != false) {
        log.info "${device}: ${message}"
    }
}

private void logWarn(final String message) {
    log.warn "${device}: ${message}"
}

@CompileStatic
private static Integer parseHexValue(final String value) {
    return Integer.parseInt(value, 16)
}

@CompileStatic
private static String switchValue(final String value) {
    return value == '01' ? 'on' : 'off'
}

@CompileStatic
private static String powerRestoreMode(final Integer value, final String rawValue) {
    switch (value) {
        case 0x00:
            return 'Off'
        case 0x01:
            return 'On'
        case 0xFF:
            return 'Last State'
        default:
            return "0x${rawValue}"
    }
}

@CompileStatic
private static String healthCheckSchedule(final int minute, final int intervalMin) {
    return "${minute} */${intervalMin} * ? * * *"
}
