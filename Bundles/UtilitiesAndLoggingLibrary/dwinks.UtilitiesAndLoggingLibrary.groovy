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
  description: 'Utilities and Logging Library',
  version: '0.7.18',
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
 * Logs an exception with a message and exception details.
 * @param message The message to log
 * @param exception The exception object
 */
void logExceptionWithDetails(String message, Exception exception) {
  logException("${message}: ${exception.message}")
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
// Event Utilities
// =============================================================================

/**
 * Emits a device/app event using a map of event properties.
 * This wrapper allows @CompileStatic callers to safely send events.
 *
 * @param eventData Map containing event fields (name, value, unit, descriptionText, etc.)
 */
void emitEvent(Map eventData) {
  if (eventData != null) {
    sendEvent(eventData)
  }
}

/**
 * Emits a device/app event using common fields.
 * This wrapper allows @CompileStatic callers to safely send events.
 *
 * @param name            Attribute name
 * @param value           Attribute value
 * @param unit            Optional unit
 * @param descriptionText Optional description text
 */
void emitEvent(String name, Object value, String unit = null, String descriptionText = null) {
  Map eventData = [name: name, value: value]
  if (unit != null) {
    eventData.unit = unit
  }
  if (descriptionText != null) {
    eventData.descriptionText = descriptionText
  }
  sendEvent(eventData)
}

// =============================================================================
// Date/Time Utilities
// =============================================================================

/**
 * Parses an ISO-8601 date-time string into a Date.
 * Falls back to Hubitat's toDateTime() when available.
 *
 * @param isoString The ISO date-time string
 * @return Date or null if parsing fails
 */
Date parseIsoDateTime(String isoString) {
  if (isoString == null) {
    return null
  }
  try {
    return Date.parse("yyyy-MM-dd'T'HH:mm:ssXXX", isoString)
  } catch (Exception e) {
    try {
      return toDateTime(isoString)
    } catch (Exception ignored) {
      return null
    }
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

// =============================================================================
// HTTP Retry Utilities
// =============================================================================
// These utilities provide automatic retry logic for failed HTTP requests.
// Use these when making async HTTP calls that should retry on failure.

/**
 * Default retry delays in seconds: 1 minute, 3 minutes, 5 minutes.
 * Each index corresponds to the retry attempt number (0-indexed).
 * Customize by passing different values to the retry handler.
 */
@Field static final List<Integer> DEFAULT_HTTP_RETRY_DELAYS_SECONDS = [60, 180, 300]

/**
 * Default maximum number of retry attempts before giving up.
 */
@Field static final Integer DEFAULT_MAX_HTTP_RETRY_ATTEMPTS = 3

/**
 * Attempts to normalize the HTTP status code from an AsyncResponse.
 * Handles cases where status is a String or other non-Integer type.
 *
 * @param response The AsyncResponse object
 * @return Integer HTTP status code, or null if not available/parseable
 */
Integer getHttpStatusCode(AsyncResponse response) {
  if (response == null) return null
  def statusObj = response.status
  if (statusObj == null) return null

  if (statusObj instanceof Number) {
    return (statusObj as Number).intValue()
  }

  try {
    String statusText = statusObj.toString()
    // Try direct integer conversion first
    try {
      return statusText.toInteger()
    } catch (Exception ignored) {
      // Fallback: extract first 3-digit HTTP status code from text like "200 OK"
      def matcher = (statusText =~ /(\d{3})/)
      if (matcher.find()) {
        return matcher.group(1).toInteger()
      }
      return null
    }
  } catch (Exception e) {
    return null
  }
}

/**
 * Checks if an async HTTP response indicates a failure (error or non-200 status).
 *
 * @param response The AsyncResponse object from an asynchttpGet/Post call
 * @return Boolean true if the response indicates a failure, false if successful
 */
Boolean isHttpResponseFailure(AsyncResponse response) {
  Integer statusCode = getHttpStatusCode(response)
  return response?.hasError() || statusCode == null || statusCode != 200
}

/**
 * Resets the HTTP retry counter in state.
 * Call this at the start of a fresh HTTP request (not a retry).
 *
 * @param stateKey The state key to use for tracking retries (default: 'httpRetryAttemptCount')
 */
void resetHttpRetryCounter(String stateKey = 'httpRetryAttemptCount') {
  state[stateKey] = 0
  logDebug "HTTP retry counter reset (${stateKey})"
}

/**
 * Gets the current HTTP retry attempt count from state.
 *
 * @param stateKey The state key used for tracking retries (default: 'httpRetryAttemptCount')
 * @return Integer The current retry count (0 if not set)
 */
Integer getHttpRetryCount(String stateKey = 'httpRetryAttemptCount') {
  return state[stateKey] ?: 0
}

/**
 * Handles an HTTP request failure by logging the error and scheduling a retry.
 * This is a generic handler that can be used by any driver or app making HTTP requests.
 *
 * Usage example:
 *   void myHttpCallback(AsyncResponse response, Map data) {
 *     if (isHttpResponseFailure(response)) {
 *       handleAsyncHttpFailureWithRetry(response, 'executeMyRetry')
 *       return
 *     }
 *     // Process successful response...
 *   }
 *
 *   void executeMyRetry() {
 *     executeHttpRetry('myHttpCallback', myHttpParams, 'executeMyRetry')
 *   }
 *
 * @param response           The failed AsyncResponse object
 * @param retryMethodName    The name of the method to call for retry (must be a void method in your driver/app)
 * @param stateKey           State key for tracking retry count (default: 'httpRetryAttemptCount')
 * @param retryDelays        List of delays in seconds for each retry attempt (default: [60, 180, 300])
 * @param maxRetries         Maximum number of retry attempts (default: 3)
 * @return Boolean           true if a retry was scheduled, false if retries exhausted
 */
Boolean handleAsyncHttpFailureWithRetry(
  AsyncResponse response,
  String retryMethodName,
  String stateKey = 'httpRetryAttemptCount',
  List<Integer> retryDelays = DEFAULT_HTTP_RETRY_DELAYS_SECONDS,
  Integer maxRetries = DEFAULT_MAX_HTTP_RETRY_ATTEMPTS,
  String customErrorMessage = null
) {
  // Get the current retry attempt count
  Integer currentRetryCount = state[stateKey] ?: 0

  // Log the specific error with attempt information
  Integer statusCode = getHttpStatusCode(response)
  String errorDetails = customErrorMessage ?: (response?.hasError() ?
    "HTTP request error: ${response.getErrorMessage()}" :
    (statusCode != null ? "HTTP request returned status ${statusCode} (expected 200 OK)" :
      "HTTP request returned no status code"))
  logError "${errorDetails} (attempt ${currentRetryCount + 1} of ${maxRetries + 1})"

  // Check if we have retries remaining
  if (currentRetryCount < maxRetries) {
    // Get the delay for this retry attempt (0-indexed)
    Integer retryDelaySeconds = retryDelays[currentRetryCount]

    // Increment the retry counter for the next attempt
    state[stateKey] = currentRetryCount + 1

    // Calculate human-readable delay for logging
    String delayDescription = retryDelaySeconds >= 60 ?
        "${retryDelaySeconds / 60} minute(s)" :
        "${retryDelaySeconds} second(s)"

    logWarn "Scheduling retry attempt ${currentRetryCount + 1} of ${maxRetries} in ${delayDescription}"

    // Schedule the retry attempt
    runIn(retryDelaySeconds, retryMethodName)
    return true
  } else {
    // All retries exhausted
    logError "All ${maxRetries} retry attempts failed. Will retry at next scheduled refresh."
    state[stateKey] = 0  // Reset for next scheduled refresh
    return false
  }
}

/**
 * Executes an HTTP retry by making an async GET request.
 * Call this from your retry method to re-attempt the HTTP request.
 *
 * @param callbackMethodName The name of the callback method to handle the response
 * @param httpParams         The HTTP request parameters (uri, contentType, etc.)
 * @param retryMethodName    The name of this retry method (for logging purposes)
 * @param stateKey           State key for tracking retry count (default: 'httpRetryAttemptCount')
 * @param maxRetries         Maximum retries for logging (default: 3)
 */
void executeHttpRetryGet(
    String callbackMethodName,
    Map httpParams,
    String retryMethodName,
    String stateKey = 'httpRetryAttemptCount',
    Integer maxRetries = DEFAULT_MAX_HTTP_RETRY_ATTEMPTS
) {
  Integer currentRetryCount = state[stateKey] ?: 0
  logInfo "Executing HTTP GET retry attempt ${currentRetryCount} of ${maxRetries}"

  asynchttpGet(callbackMethodName, httpParams)
}
// =============================================================================
// @CompileStatic Helper Methods
// =============================================================================
// These helper methods allow @CompileStatic methods to access dynamic properties
// that the static compiler can't see (app, state, settings, device, etc.)
// =============================================================================

/**
 * Gets the InstalledAppWrapper instance.
 * @return The app instance
 */
Object getAppInstance() { return app }

/**
 * Gets a child device by its DNI.
 * @param dni The device network ID
 * @return The child device or null
 */
DeviceWrapper getAppChildDevice(String dni) { return app.getChildDevice(dni) }

/**
 * Adds a child device.
 * @param namespace The namespace
 * @param typeName The device type name
 * @param dni The device network ID
 * @param properties Additional properties map
 * @return The created device
 */
DeviceWrapper createAppChildDevice(String namespace, String typeName, String dni, Map properties) {
  return addChildDevice(namespace, typeName, dni, properties)
}

/**
 * Gets the app label.
 * @return The app label
 */
String getAppLabel() { return app.label }

/**
 * Gets a state variable.
 * @param key The state key
 * @return The state value
 */
Object getStateVar(String key) { return state[key] }

/**
 * Sets a state variable.
 * @param key The state key
 * @param value The value to set
 */
void setStateVar(String key, Object value) { state[key] = value }

/**
 * Removes a state variable.
 * @param key The state key
 */
void removeStateVar(String key) {
  if (app) app.getState().remove(key)
  if (device) device.getState().remove(key)
}

/**
 * Gets a setting value.
 * @param key The setting key
 * @return The setting value
 */
Object getSetting(String key) { return settings[key] }

/**
 * Gets the current timestamp in milliseconds.
 * @return Current time in milliseconds
 */
Long getCurrentTime() { return now() }

/**
 * Schedules a method to run after a delay.
 * @param seconds Number of seconds to delay
 * @param methodName Name of the method to call
 */
void scheduleIn(Integer seconds, String methodName) { runIn(seconds, methodName) }

/**
 * Unschedules a method.
 * @param methodName Name of the method to unschedule
 */
void unscheduleMethod(String methodName) { unschedule(methodName) }

/**
 * Calls a method on a device.
 * @param device The device
 * @param methodName The method name
 * @param args Optional arguments
 */
void callDeviceMethod(DeviceWrapper device, String methodName, Object... args) {
  device."${methodName}"(*args)
}

/**
 * Gets a device property.
 * @param device The device
 * @param propertyName The property name
 * @return The property value
 */
Object getDeviceProperty(DeviceWrapper device, String propertyName) {
  return device."${propertyName}"
}

/**
 * Gets the device instance (for drivers).
 * @return The device instance
 */
Object getDeviceInstance() { return device }

/**
 * Gets a device attribute's current value.
 * @param attributeName The attribute name
 * @return The current value
 */
Object getDeviceCurrentValue(String attributeName) {
  return device.currentValue(attributeName)
}

/**
 * Gets today's Date at a specific time.
 * @param timeString Time in format "HH:mm"
 * @return Date object for today at specified time
 */
Date getTodayAtTime(String timeString) {
  return timeToday(timeString)
}

/**
 * Deletes a device's current state for an attribute.
 * @param attributeName The attribute name to delete
 */
void deleteDeviceCurrentState(String attributeName) {
  device.deleteCurrentState(attributeName)
}

/**
 * Executes an HTTP retry by making an async POST request.
 * Call this from your retry method to re-attempt the HTTP request.
 *
 * @param callbackMethodName The name of the callback method to handle the response
 * @param httpParams         The HTTP request parameters (uri, contentType, body, etc.)
 * @param retryMethodName    The name of this retry method (for logging purposes)
 * @param stateKey           State key for tracking retry count (default: 'httpRetryAttemptCount')
 * @param maxRetries         Maximum retries for logging (default: 3)
 */
void executeHttpRetryPost(
    String callbackMethodName,
    Map httpParams,
    String retryMethodName,
    String stateKey = 'httpRetryAttemptCount',
    Integer maxRetries = DEFAULT_MAX_HTTP_RETRY_ATTEMPTS
) {
  Integer currentRetryCount = state[stateKey] ?: 0
  logInfo "Executing HTTP POST retry attempt ${currentRetryCount} of ${maxRetries}"

  asynchttpPost(callbackMethodName, httpParams)
}