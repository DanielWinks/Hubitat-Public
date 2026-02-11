/**
 *  ============================================================================
 *  BATHROOM FAN CONTROLLER CHILD - SMART HUMIDITY-BASED FAN AUTOMATION
 *  ============================================================================
 *
 *  PURPOSE:
 *  This app automatically controls a bathroom exhaust fan based on humidity levels.
 *  It monitors humidity sensors and turns the fan on when humidity rises rapidly
 *  (like when someone takes a shower), then turns it off when humidity returns to
 *  normal levels. This helps prevent mold/mildew while saving energy.
 *
 *  HOW IT WORKS (THE BIG PICTURE):
 *  1. The app constantly monitors humidity sensor(s) in your bathroom
 *  2. It tracks the "normal" humidity levels over time using statistical averages
 *  3. When humidity suddenly spikes above normal (by your configured amount),
 *     it turns on the fan
 *  4. The fan stays on until humidity drops back to normal levels
 *  5. Safety features prevent the fan from running too long or when door is open
 *
 *  KEY CONCEPTS FOR NON-PROGRAMMERS:
 *  - "Event": A notification from a device that something changed (like humidity went up)
 *  - "Subscribe": Telling Hubitat "let me know when this device does something"
 *  - "State": Information the app remembers between events (like current humidity)
 *  - "Child Device": A virtual device this app creates to track humidity statistics
 *
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

// ============================================================================
// IMPORTS - External code libraries this app needs to function
// ============================================================================

// ChildDeviceWrapper: Represents virtual/child devices created by this app
import com.hubitat.app.ChildDeviceWrapper

// DeviceWrapper: Represents any Hubitat device (sensor, switch, etc.)
import com.hubitat.app.DeviceWrapper

// UnknownDeviceTypeException: Error that occurs when trying to create a device type that doesn't exist
import com.hubitat.app.exception.UnknownDeviceTypeException

// Event: Represents something that happened with a device (humidity changed, switch turned on, etc.)
import com.hubitat.hub.domain.Event

// Field: Annotation that creates a static constant shared across all instances
import groovy.transform.Field

// CompileStatic: Annotation that enables compile-time type checking for safer code
import groovy.transform.CompileStatic

// Include the utilities and logging library - provides helper functions for logging and common tasks
#include dwinks.UtilitiesAndLoggingLibrary

// ============================================================================
// APP DEFINITION - Metadata that tells Hubitat about this app
// ============================================================================
definition(
  name: "Bathroom Fan Controller Child",
  namespace: "dwinks",
  author: "Daniel Winks",
  description: "Bathroom Fan Controller",
  category: "Convenience",
  parent: "dwinks:Bathroom Fan Controllers",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: ""
)

// ============================================================================
// USER PREFERENCES - Define the configuration page users see
// ============================================================================

preferences {
  page(name: "namePage")
  page(name: "mainPage", title: "Bathroom Fan Controller")
}

// =============================================================================
// APP CONFIGURATION PAGES
// =============================================================================

Map namePage() {
  return dynamicPage(name: "namePage", title: "New Bathroom Fan Controller", nextPage: "mainPage", uninstall: false, install: false) {
    section {
      label title: "Enter a name for this fan controller instance (e.g. Master Bathroom Fan)", required: true
    }
  }
}

Map mainPage() {
  dynamicPage(name: "mainPage", title: "", install: true, uninstall: true, refreshInterval: 0) {

    // -------------------------------------------------------------------------
    // SECTION 1: Device Selection
    // -------------------------------------------------------------------------
    section("<b>Device Instructions</b>", hideable: true, hidden: false) {
      paragraph "The humidity sensor(s) you select will control the fan switch you select."

      input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Humidity Sensor(s)", multiple: true, required: true

      input "householdHumiditySensor", "capability.relativeHumidityMeasurement", title: "Optional Household Humidity Sensor, i.e. central thermostat humidity sensor", multiple: false, required: false

      input "fanSwitch", "capability.switch", title: "Fan Switch", required: true

      paragraph "The door sensor you select will turn off the fan when the door has been open for a specified time, 10 minutes by default. Optional."

      input "doorSensor", "capability.contactSensor", title: "Door Sensor", required: false
    }

    // -------------------------------------------------------------------------
    // SECTION 2: Change Limit Configuration
    // -------------------------------------------------------------------------
    section("<b>Change Limit:</b>", hideable: true, hidden: false) {
      paragraph "Maximum change a newly received value can be from historical average without turning on fan."

      input "changeLimit", "number", title: "Change Limit", required: true, defaultValue: 2
    }

    // -------------------------------------------------------------------------
    // SECTION 3: Maximum Runtime Configuration
    // -------------------------------------------------------------------------
    section("<b>Max Run Time:</b>", hideable: true, hidden: false) {
      paragraph "Maximum time fan may run under any circumstances."

      input "maxRuntime", "number", title: "Max run time (minutes, 0 to disable, max 720)", required: true, defaultValue: 60, range: '0..720'
    }

    // -------------------------------------------------------------------------
    // SECTION 4: Door Open Time Configuration
    // -------------------------------------------------------------------------
    section("<b>Door Open Time:</b>", hideable: true, hidden: false) {
      paragraph 'Maximum time fan may run after opening door.'

      input "doorOpenTime", "number", title: "Max run time after door opening (minutes, 0 to disable, max 60)", required: true, defaultValue: 10, range: '0..60'
    }

    // -------------------------------------------------------------------------
    // SECTION 5: Humidity Tracking Mode Selection
    // -------------------------------------------------------------------------
    section("<b>Humidity Statistic To Track:</b>", hideable: true, hidden: false) {
      paragraph 'Humidity statistic type to track for historical value comparison.'

      input 'highHumMode', 'enum', title: 'Humidity Statistic To Track', required: true, offerAll: false, defaultValue: 'Slow Rolling Average', options: [slowRollingAverage:'Slow Rolling Average', fastRollingAverage:'Fast Rolling Average', timeWeightedAverage:'Time Weighted Average']
    }

    // -------------------------------------------------------------------------
    // SECTION 6: Logging Configuration
    // -------------------------------------------------------------------------
    section("Logging", hideable: true, hidden: false) {
      input "logEnable", "bool", title: "Enable Logging", required: false, defaultValue: true

      input "debugLogEnable", "bool", title: "Enable debug logging", required: false, defaultValue: false
    }
  }
}

// =============================================================================
// STATIC CONSTANTS AND HELPER FUNCTIONS
// =============================================================================

@Field static final String humidityStaticSensor = 'humidity-stats'

String getHumidityStatSensor() {
  return "${app.id}-${humidityStaticSensor}"
}

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

void configure() {
  unsubscribe()

  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())

  initializeApp(child)
}

void initializeApp(ChildDeviceWrapper child) {
  humiditySensors.each { sensor ->
    subscribe(sensor, "humidity", humidityEvent)
  }

  subscribe(fanSwitch, "switch", switchEvent)

  subscribe(doorSensor, "contact", contactEvent)

  subscribe(child, highHumMode, childHumidityEvent)
}

// =============================================================================
// CHILD DEVICE MANAGEMENT
// =============================================================================

@CompileStatic
ChildDeviceWrapper getOrCreateChildDevices(String childDNI) {
  DeviceWrapper device = getAppChildDevice(childDNI)

  if (device == null) {
    try {
      logInfo("Creating child device for tracking humidity statistics")

      device = createAppChildDevice(
        'dwinks',
        'Humidity Statistics',
        childDNI,
        [
          name: 'Humidity Statistics',
          label: "${getAppLabel()}: Humidity Statistics"
        ]
      )
    } catch (UnknownDeviceTypeException e) {
      logExceptionWithDetails('Humidity Statistics driver not found', e)
    }
  }

  return device as ChildDeviceWrapper
}

// =============================================================================
// HUMIDITY PROCESSING - THE CORE LOGIC
// =============================================================================

@CompileStatic
void humidityEvent(Event event) {
  logDebug("Received humidity event: ${event.value}")

  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())

  BigDecimal value = new BigDecimal(event.value)

  if (value > 0 && value < 100) {
    setStateVar('currentHumidity', value.toString())

    callDeviceMethod(child, 'logHumidityEvent', value)
  }
}

@CompileStatic
void childHumidityEvent(Event event) {
  BigDecimal trackedHumValue = new BigDecimal(event.value)

  BigDecimal currentHumidity = new BigDecimal(getStateVar('currentHumidity') as String).setScale(1, BigDecimal.ROUND_HALF_UP)

  BigDecimal lastHumidity = new BigDecimal((getStateVar('lastHumidity') ?: '0') as String).setScale(1, BigDecimal.ROUND_HALF_UP)

  logDebug("Tracked Humidity Value: ${trackedHumValue}")
  logDebug("Current Humidity Value: ${currentHumidity}")

  String isIncreasing = currentHumidity > lastHumidity ? 'true' : 'false'

  String decreasingSuccessively = getStateVar('isIncreasing') == 'false' && isIncreasing == 'false' ? 'true' : 'false'

  setStateVar('isIncreasing', isIncreasing)

  logDebug("Is Increasing: ${isIncreasing}")

  BigDecimal changeLimitValue = getSetting('changeLimit') as BigDecimal
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper

  if ((currentHumidity - changeLimitValue) > trackedHumValue && isIncreasing == 'true') {
    callDeviceMethod(fanSwitchDevice, 'on')

    logDebug("Last Humidity Reading(${lastHumidity}) was great enough to trigger a fan event.")
    logDebug("Current Humidity(${currentHumidity}) was outside change limit(${changeLimitValue}) of Last Humidity Reading(${lastHumidity})")
  } else {
    if (decreasingSuccessively == 'true') {
      logDebug("Current Humidity(${currentHumidity}) has been decreasing successively. Turning off fan.")
      callDeviceMethod(fanSwitchDevice, 'off')
    }

    logDebug("Last Humidity Reading(${lastHumidity}) was not great enough to trigger a fan event.")
    logDebug("Current Humidity(${currentHumidity}) was within change limit(${changeLimitValue}) of Last Humidity Reading(${lastHumidity})")
  }

  setStateVar('lastHumidity', currentHumidity.toString())
}

// =============================================================================
// FAN & DOOR EVENT HANDLING - SAFETY FEATURES
// =============================================================================

@CompileStatic
void switchEvent(Event event) {
  logDebug("Received switch event: ${event.value}")

  if (event.value == "off") {
    removeStateVar('fanOnSince')
  } else if (event.value == "on") {
    setStateVar('fanOnSince', getCurrentTime())

    Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
    if (maxRuntimeValue > 0) {
      logDebug("Scheduling fan shutoff for ${maxRuntimeValue} minutes")

      scheduleIn(maxRuntimeValue * 60, "runtimeExceeded")
    }
  }
}

@CompileStatic
void contactEvent(Event event) {
  logDebug("Received contact event: ${event.value}")

  if (event.value == "open") {
    Integer doorOpenTimeValue = getSetting('doorOpenTime') as Integer
    DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
    String fanState = fanSwitchDevice.currentValue("switch") as String

    if (doorOpenTimeValue > 0 && fanState == "on") {
      logDebug("Scheduling fan shutoff for ${doorOpenTimeValue} minutes")

      scheduleIn(doorOpenTimeValue * 60, "doorOpenedAutoOff")
    }
  }
  else if (event.value == "closed") {
    unscheduleMethod("doorOpenedAutoOff")
  }
}

@CompileStatic
void doorOpenedAutoOff() {
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer doorOpenTimeValue = getSetting('doorOpenTime') as Integer
  String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String

  logInfo("Auto-off: ${displayName} has been on with door open for ${doorOpenTimeValue} minutes")

  callDeviceMethod(fanSwitchDevice, 'off')
}

@CompileStatic
void runtimeExceeded() {
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
  String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String

  logInfo("Auto-off: ${displayName} has been on for ${maxRuntimeValue} minutes")

  callDeviceMethod(fanSwitchDevice, 'off')
}
