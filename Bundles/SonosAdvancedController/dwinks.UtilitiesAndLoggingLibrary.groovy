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

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.util.slurpersupport.GPathResult
import groovy.xml.XmlUtil
import hubitat.device.HubResponse
import hubitat.scheduling.AsyncResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

library(
  name: 'UtilitiesAndLoggingLibrary',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Utility and Logging Library',
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Libraries/UtilitiesAndLoggingLibrary.groovy'
)

// Preferences for device logging settings
if (device != null) {
  preferences {
    input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
    input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: true
    input 'traceLogEnable', 'bool', title: 'Enable trace logging', required: false, defaultValue: false
    input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: false
  }
}

// =============================================================================
// Device/App ID Utilities
// =============================================================================

/**
 * Returns the device network ID for a given device or the current device/app ID.
 * @param dev Optional device wrapper. If null, uses current device or app.
 * @return String The device network ID or app ID.
 */
String dniOrAppId(DeviceWrapper dev = null)
{
  if(dev) {return dev.getDeviceNetworkId()}
  return device?.getDeviceNetworkId() ?: app.getId()
}

// =============================================================================
// State and Device Management
// =============================================================================

/**
 * Clears all state variables and deletes current device states if running on a device.
 */
void clearAllStates() {
  state.clear()
  if (device) device.getCurrentStates().each { device.deleteCurrentState(it.name) }
}

/**
 * Deletes all child devices associated with the current device.
 */
void deleteChildDevices() {
  List<ChildDeviceWrapper> children = getChildDevices()
  children.each { ChildDeviceWrapper child ->
    deleteChildDevice(child.getDeviceNetworkId())
  }
}

// =============================================================================
// Lifecycle Methods
// =============================================================================

/**
 * Called when the app or driver is installed.
 * Initializes the component and schedules logging to turn off after 30 minutes.
 */
void installed() {
  logDebug('Installed...')
  try {
    initialize()
  } catch(e) {
    logWarn("No initialize() method defined or initialize() resulted in error: ${e}")
  }

  if (settings.logEnable) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable) { runIn(1800, 'debugLogsOff') }
  if (settings.traceLogEnable) { runIn(1800, 'traceLogsOff') }
}

/**
 * Called when the app or driver is uninstalled.
 * Unschedules all tasks and deletes child devices.
 */
void uninstalled() {
  logDebug('Uninstalled...')
  unschedule()
  deleteChildDevices()
}

/**
 * Called when the app or driver is updated.
 * Attempts to run the configure method.
 */
void updated() {
  logDebug('Updated...')
  try { configure() }
  catch(e) {
    logWarn("No configure() method defined or configure() resulted in error: ${e}")
  }
}

// =============================================================================
// Basic Logging Methods
// =============================================================================

/**
 * Logs an exception message if logging is enabled.
 * @param message The exception message to log.
 */
void logException(String message) {
  if (settings.logEnable) {
    if(device) log.exception "${device.label ?: device.name }: ${message}"
    if(app) log.exception "${app.label ?: app.name }: ${message}"
  }
}

/**
 * Logs an error message if logging is enabled.
 * @param message The error message to log.
 */
void logError(String message) {
  if (settings.logEnable) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}

/**
 * Logs a warning message if logging is enabled.
 * @param message The warning message to log.
 */
void logWarn(String message) {
  if (settings.logEnable) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}

/**
 * Logs an info message if logging is enabled.
 * @param message The info message to log.
 */
void logInfo(String message) {
  if (settings.logEnable) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}

/**
 * Logs a debug message if logging and debug logging are enabled.
 * @param message The debug message to log.
 */
void logDebug(String message) {
  if (settings.logEnable && settings.debugLogEnable) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}

/**
 * Logs a trace message if logging and trace logging are enabled.
 * @param message The trace message to log.
 */
void logTrace(String message) {
  if (settings.logEnable && settings.traceLogEnable) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

// =============================================================================
// Specialized Logging Methods
// =============================================================================

/**
 * Logs the class name of the given object for debugging.
 * @param obj The object whose class name to log.
 */
void logClass(Object obj) {
  logDebug("Object Class Name: ${getObjectClassName(obj)}")
}

/**
 * Logs an XML structure as a debug message, escaping HTML entities.
 * @param xml The GPathResult XML to log.
 */
void logXml(GPathResult xml) {
  String serialized = XmlUtil.serialize(xml)
  logDebug(serialized.replace('"', '&quot;').replace("'", '&apos;').replace('<', '&lt;').replace('>','&gt;').replace('&','&amp;'))
}

/**
 * Logs a map as pretty-printed JSON for debugging.
 * @param message The map to log as JSON.
 */
void logJson(Map message) {
  logDebug(prettyJson(message))
}

/**
 * Logs an XML structure as an error message, escaping HTML entities.
 * @param xml The GPathResult XML to log.
 */
void logErrorXml(GPathResult xml) {
  String serialized = XmlUtil.serialize(xml)
  logError(serialized.replace('"', '&quot;').replace("'", '&apos;').replace('<', '&lt;').replace('>','&gt;').replace('&','&amp;'))
}

/**
 * Logs a map as pretty-printed JSON for error logging.
 * @param message The map to log as JSON.
 */
void logErrorJson(Map message) {
  logError(prettyJson(message))
}

// =============================================================================
// Logging Control Methods
// =============================================================================

/**
 * Disables general logging after a timeout period.
 */
void logsOff() {
  if (device) {
    logWarn("Logging disabled for ${device}")
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Logging disabled for ${app}")
    app.updateSetting('logEnable', [value: 'false', type: 'bool'] )
  }
}

/**
 * Disables debug logging after a timeout period.
 */
void debugLogsOff() {
  if (device) {
    logWarn("Debug logging disabled for ${device}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Debug logging disabled for ${app}")
    app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
}

/**
 * Disables trace logging after a timeout period.
 * Note: The code incorrectly updates 'debugLogEnable' instead of 'traceLogEnable'.
 */
void traceLogsOff() {
  if (device) {
    logWarn("Trace logging disabled for ${device}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Trace logging disabled for ${app}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
}

// =============================================================================
// JSON Utilities
// =============================================================================

/**
 * Converts a map to a pretty-printed JSON string.
 * @param jsonInput The map to convert.
 * @return String The pretty-printed JSON string.
 */
@CompileStatic
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

// =============================================================================
// Time Utilities
// =============================================================================

/**
 * Returns the current date and time formatted as a string.
 * @return String The formatted current date and time.
 */
String nowFormatted() {
  if(location.timeZone) return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone)
  else                  return new Date().format('yyyy-MMM-dd h:mm:ss a')
}

/**
 * Returns the current time in days since epoch.
 * @return double The current time in days.
 */
double nowDays() {
  return (now() / 86400000)
}

// =============================================================================
// Scheduling Utilities
// =============================================================================

/**
 * Generates a cron expression to run every specified number of seconds.
 * @param seconds The interval in seconds.
 * @return String The cron expression.
 */
@CompileStatic
String runEveryCustomSeconds(Integer seconds) {
  String currentSecond = new Date().format('ss')
  return "${currentSecond} /${seconds} * * * ?"
}

/**
 * Generates a cron expression to run every specified number of minutes.
 * @param minutes The interval in minutes.
 * @return String The cron expression.
 */
@CompileStatic
String runEveryCustomMinutes(Integer minutes) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  return "${currentSecond} ${currentMinute}/${minutes} * * * ?"
}

/**
 * Generates a cron expression to run every specified number of hours.
 * @param hours The interval in hours.
 * @return String The cron expression.
 */
@CompileStatic
String runEveryCustomHours(Integer hours) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  String currentHour = new Date().format('H')
  return "${currentSecond} ${currentMinute} ${currentHour}/${hours} * * ?"
}

// =============================================================================
// Conversion Utilities
// =============================================================================

/**
 * Converts a hexadecimal string to an integer.
 * @param hex The hexadecimal string to convert.
 * @return Integer The integer value.
 */
Integer convertHexToInt(String hex) { Integer.parseInt(hex,16) }

/**
 * Converts a hexadecimal string representing an IP address to dotted decimal format.
 * @param hex The hexadecimal string (8 characters).
 * @return String The IP address in dotted decimal format.
 */
String convertHexToIP(String hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

/**
 * Converts an IP address in dotted decimal format to a hexadecimal string.
 * @param ipAddress The IP address string.
 * @return String The hexadecimal representation.
 */
String convertIPToHex(String ipAddress) {
  List<String> parts = ipAddress.tokenize('.')
  return String.format("%X%X%X%X", parts[0] as Integer, parts[1] as Integer, parts[2] as Integer, parts[3] as Integer)
}

// =============================================================================
// OAuth Utilities
// =============================================================================

/**
 * Attempts to create an access token for OAuth if not already present.
 * Logs success or failure.
 */
void tryCreateAccessToken() {
  if (state.accessToken == null) {
    try {
      logDebug('Creating Access Token...')
      createAccessToken()
      logDebug("accessToken: ${state.accessToken}")
    } catch(e) {
      logError('OAuth is not enabled for app. Please enable.')
    }
  }
}