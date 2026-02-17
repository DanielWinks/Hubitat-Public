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
  page(name: "mainPage", title: "New Bathroom Fan Controller")
}

// =============================================================================
// APP CONFIGURATION PAGES
// =============================================================================



Map mainPage() {
  dynamicPage(name: "mainPage", title: "New Bathroom Fan Controller", install: true, uninstall: true, refreshInterval: 0) {

    // -------------------------------------------------------------------------
    // Device Selection
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
    // Change Limit Configuration
    // -------------------------------------------------------------------------
    section("<b>Change Limit:</b>", hideable: true, hidden: false) {
      paragraph "Maximum change a newly received value can be from historical average without turning on fan."

      input "changeLimit", "number", title: "Change Limit", required: true, defaultValue: 2
    }

    // -------------------------------------------------------------------------
    // Maximum Runtime Configuration
    // -------------------------------------------------------------------------
    section("<b>Max Run Time:</b>", hideable: true, hidden: false) {
      paragraph "Maximum time fan may run under any circumstances."

      input "maxRuntime", "number", title: "Max run time (minutes, 0 to disable, max 720)", required: true, defaultValue: 60, range: '0..720'
    }

    // -------------------------------------------------------------------------
    // Door Open Time Configuration
    // -------------------------------------------------------------------------
    section("<b>Door Open Time:</b>", hideable: true, hidden: false) {
      paragraph 'Maximum time fan may run after opening door.'

      input "doorOpenTime", "number", title: "Max run time after door opening (minutes, 0 to disable, max 60)", required: true, defaultValue: 10, range: '0..60'
    }

    // -------------------------------------------------------------------------
    // Humidity Tracking Mode Selection
    // -------------------------------------------------------------------------
    section("<b>Humidity Statistic To Track:</b>", hideable: true, hidden: false) {
      paragraph 'Humidity statistic type to track for historical value comparison.'

      input 'highHumMode', 'enum', title: 'Humidity Statistic To Track', required: true, offerAll: false, defaultValue: 'Fast Rolling Average', options: [slowRollingAverage:'Slow Rolling Average', fastRollingAverage:'Fast Rolling Average (recommended)', timeWeightedAverage:'Time Weighted Average', householdDifferential:'Household Sensor Differential']
    }

    // -------------------------------------------------------------------------
    // Minimum Runtime
    // -------------------------------------------------------------------------
    section("<b>Min Run Time:</b>", hideable: true, hidden: false) {
      paragraph 'Minimum time fan must run before auto-off (prevents short-cycling). 0 to disable.'

      input "minRuntime", "number", title: "Min run time (minutes, 0 to disable, max 30)", required: false, defaultValue: 5, range: '0..30'
    }

    // -------------------------------------------------------------------------
    // Cooldown Period
    // -------------------------------------------------------------------------
    section("<b>Cooldown Period:</b>", hideable: true, hidden: false) {
      paragraph 'After the fan turns off, wait this many minutes before allowing it to turn on again. 0 to disable.'

      input "cooldownPeriod", "number", title: "Cooldown period (minutes, 0 to disable, max 30)", required: false, defaultValue: 3, range: '0..30'
    }

    // -------------------------------------------------------------------------
    // Absolute Ceiling
    // -------------------------------------------------------------------------
    section("<b>Absolute Humidity Ceiling:</b>", hideable: true, hidden: false) {
      paragraph 'If humidity exceeds this value, the fan turns on unconditionally regardless of trends. 0 to disable.'

      input "absoluteCeiling", "number", title: "Absolute humidity ceiling (%, 0 to disable, max 95)", required: false, defaultValue: 95, range: '0..95'
    }

    // -------------------------------------------------------------------------
    // Fan Dimmer / Speed Control
    // -------------------------------------------------------------------------
    section("<b>Fan Speed Control:</b>", hideable: true, hidden: false) {
      paragraph 'Optional: Use a dimmer-capable fan switch to set low/high speed based on humidity delta.'

      input "fanDimmer", "capability.switchLevel", title: "Fan Dimmer (optional)", required: false

      input "lowSpeedLevel", "number", title: "Low speed level (%)", required: false, defaultValue: 50, range: '1..100'

      input "highSpeedLevel", "number", title: "High speed level (%)", required: false, defaultValue: 100, range: '1..100'

      input "highSpeedThreshold", "number", title: "Humidity delta above baseline to trigger high speed", required: false, defaultValue: 10, range: '1..50'
    }

    // -------------------------------------------------------------------------
    // Notification Device
    // -------------------------------------------------------------------------
    section("<b>Notifications:</b>", hideable: true, hidden: false) {
      paragraph 'Optional: Send push notifications when the fan turns on/off.'

      input "notificationDevice", "capability.notification", title: "Notification Device (optional)", required: false
    }

    // -------------------------------------------------------------------------
    // Active Mode Restriction
    // -------------------------------------------------------------------------
    section("<b>Mode Restriction:</b>", hideable: true, hidden: false) {
      paragraph 'Only allow automatic fan control when the hub is in one of these modes. Leave empty for no restriction.'

      input "activeModes", "mode", title: "Active Modes (leave empty for all modes)", required: false, multiple: true
    }

    // -------------------------------------------------------------------------
    // General
    // -------------------------------------------------------------------------
    section("General") {
      label title: "Enter a name for this fan controller instance (e.g. Master Bathroom Fan)", required: true
    }

    // -------------------------------------------------------------------------
    // Logging Configuration
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
@Field static final String TRIGGERED_BY_APP = 'triggeredByApp'
@Field static final String FAN_ON_SINCE = 'fanOnSince'
@Field static final String FAN_OFF_SINCE = 'fanOffSince'
@Field static final String FAN_START_HUMIDITY = 'fanStartHumidity'
@Field static final String HOUSEHOLD_HUMIDITY = 'householdHumidity'

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

  // Subscribe to household humidity sensor if configured (Bug #3 fix)
  if (householdHumiditySensor) {
    subscribe(householdHumiditySensor, "humidity", householdHumidityEvent)
  }

  // Subscribe to location mode changes (Feature 7)
  subscribe(location, "mode", modeChangeEvent)

  // Only subscribe to child device stat attribute if not in householdDifferential mode
  // (householdDifferential mode evaluates directly in humidityEvent)
  String mode = getSetting('highHumMode') as String
  if (mode != 'householdDifferential') {
    subscribe(child, mode, childHumidityEvent)
  }

  // Initialize baseline freeze to false
  callDeviceMethod(child, 'setBaselineFreeze', 'false')
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
// HELPER METHODS
// =============================================================================

/**
 * Stores household humidity from the household sensor (Bug #3 fix).
 * Also forwards the value to the child device for differential calculation.
 */
void householdHumidityEvent(Event event) {
  logDebug("Received household humidity event: ${event.value}")
  setStateVar(HOUSEHOLD_HUMIDITY, event.value)

  // Forward to child device so it can compute bathroomDifferential
  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
  callDeviceMethod(child, 'setHouseholdHumidity', new BigDecimal(event.value))
}

/**
 * Logs hub mode changes for debugging.
 */
void modeChangeEvent(Event event) {
  logDebug("Hub mode changed to: ${event.value}")
}

/**
 * Checks if the current hub mode is in the activeModes list (Feature 7).
 * Returns true if no restriction is set or if current mode is in the list.
 * Cannot be @CompileStatic due to location.mode access.
 */
Boolean isModeActive() {
  List activeModesList = settings.activeModes
  if (!activeModesList || activeModesList.isEmpty()) {
    return true
  }
  return activeModesList.contains(location.mode)
}

/**
 * Checks if the fan is in cooldown period after turning off (Feature 2).
 */
@CompileStatic
Boolean isInCooldown() {
  Integer cooldown = getSetting('cooldownPeriod') as Integer
  if (cooldown == null || cooldown <= 0) { return false }

  String fanOffSinceStr = getStateVar(FAN_OFF_SINCE) as String
  if (fanOffSinceStr == null) { return false }

  Long fanOffSince = fanOffSinceStr as Long
  Long elapsed = (getCurrentTime() as Long) - fanOffSince
  Long cooldownMs = cooldown * 60000L
  return elapsed < cooldownMs
}

/**
 * Sends a push notification if a notification device is configured (Feature 6).
 */
@CompileStatic
void sendNotification(String message) {
  DeviceWrapper notifDevice = getSetting('notificationDevice') as DeviceWrapper
  if (notifDevice != null) {
    callDeviceMethod(notifDevice, 'deviceNotification', message)
  }
}

/**
 * Sets fan dimmer level based on humidity delta above baseline (Feature 5).
 */
@CompileStatic
void setFanSpeed(BigDecimal currentHumidity, BigDecimal baseline) {
  DeviceWrapper dimmer = getSetting('fanDimmer') as DeviceWrapper
  if (dimmer == null) { return }

  Integer lowSpeed = (getSetting('lowSpeedLevel') ?: 50) as Integer
  Integer highSpeed = (getSetting('highSpeedLevel') ?: 100) as Integer
  Integer threshold = (getSetting('highSpeedThreshold') ?: 10) as Integer

  BigDecimal delta = currentHumidity - baseline
  Integer level = delta >= threshold ? highSpeed : lowSpeed

  logDebug("Setting fan speed to ${level}% (delta: ${delta}, threshold: ${threshold})")
  callDeviceMethod(dimmer, 'setLevel', level)
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

    // In householdDifferential mode, evaluate directly using household humidity
    String mode = getSetting('highHumMode') as String
    if (mode == 'householdDifferential') {
      evaluateHouseholdDifferential(value)
    }
  }
}

/**
 * Evaluates fan decision using the household sensor humidity as baseline (Improvement D).
 */
@CompileStatic
void evaluateHouseholdDifferential(BigDecimal currentHumidity) {
  String householdStr = getStateVar(HOUSEHOLD_HUMIDITY) as String
  if (householdStr == null) {
    logDebug("No household humidity reading available yet, skipping evaluation")
    return
  }
  BigDecimal householdHumidity = new BigDecimal(householdStr)
  evaluateFanDecision(currentHumidity, householdHumidity)
}

/**
 * Called when the child device stat attribute updates (for non-householdDifferential modes).
 */
@CompileStatic
void childHumidityEvent(Event event) {
  BigDecimal trackedHumValue = new BigDecimal(event.value)
  BigDecimal currentHumidity = new BigDecimal(getStateVar('currentHumidity') as String).setScale(1, BigDecimal.ROUND_HALF_UP)
  evaluateFanDecision(currentHumidity, trackedHumValue)
}

/**
 * Core fan decision logic. Called by both childHumidityEvent (stat-based modes)
 * and evaluateHouseholdDifferential (household differential mode).
 *
 * @param currentHumidity The current bathroom humidity reading
 * @param baseline The baseline humidity to compare against (rolling avg or household)
 */
@CompileStatic
void evaluateFanDecision(BigDecimal currentHumidity, BigDecimal baseline) {
  BigDecimal lastHumidity = new BigDecimal((getStateVar('lastHumidity') ?: '0') as String).setScale(1, BigDecimal.ROUND_HALF_UP)

  logDebug("evaluateFanDecision - Current: ${currentHumidity}, Baseline: ${baseline}, Last: ${lastHumidity}")

  String isIncreasing = currentHumidity > lastHumidity ? 'true' : 'false'
  String decreasingSuccessively = getStateVar('isIncreasing') == 'false' && isIncreasing == 'false' ? 'true' : 'false'
  setStateVar('isIncreasing', isIncreasing)

  BigDecimal changeLimitValue = getSetting('changeLimit') as BigDecimal
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  String fanState = fanSwitchDevice.currentValue("switch") as String

  // --- Determine if fan should turn on ---
  Boolean shouldTurnOn = false

  // Feature 4: Absolute ceiling — unconditional trigger
  Integer ceiling = getSetting('absoluteCeiling') as Integer
  if (ceiling != null && ceiling > 0 && currentHumidity >= ceiling) {
    logDebug("Absolute ceiling ${ceiling}% reached, triggering fan")
    shouldTurnOn = true
  }

  // Standard trigger: humidity exceeds baseline + changeLimit and is rising
  if (!shouldTurnOn && (currentHumidity - changeLimitValue) > baseline && isIncreasing == 'true') {
    shouldTurnOn = true
  }

  if (shouldTurnOn && fanState != 'on') {
    // Feature 7: Mode restriction
    if (!isModeActive()) {
      logDebug("Fan trigger skipped: hub mode not in active modes list")
      setStateVar('lastHumidity', currentHumidity.toString())
      return
    }

    // Feature 2: Cooldown check
    if (isInCooldown()) {
      logDebug("Fan trigger skipped: still in cooldown period")
      setStateVar('lastHumidity', currentHumidity.toString())
      return
    }

    // Turn on the fan
    logDebug("Humidity(${currentHumidity}) exceeds baseline(${baseline}) + limit(${changeLimitValue}). Turning on fan.")
    setStateVar(TRIGGERED_BY_APP, 'true')  // Feature 3: track auto-trigger
    setStateVar(FAN_START_HUMIDITY, currentHumidity.toString())  // Feature 8: track start humidity
    callDeviceMethod(fanSwitchDevice, 'on')

    // Feature 5: Set fan speed
    setFanSpeed(currentHumidity, baseline)

    // Feature 8: Record fan start on child device
    ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
    callDeviceMethod(child, 'recordFanStart', currentHumidity)

    // Improvement B: Freeze baseline
    callDeviceMethod(child, 'setBaselineFreeze', 'true')

    // Feature 6: Notification
    String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String
    sendNotification("${displayName}: Fan turned on (humidity ${currentHumidity}%, baseline ${baseline}%)")
  } else if (fanState == 'on' && !shouldTurnOn) {
    // --- Fan is on, evaluate whether to turn it off ---

    // Feature 3: Don't auto-off a manually turned on fan
    if (getStateVar(TRIGGERED_BY_APP) != 'true') {
      logDebug("Fan was not triggered by app, skipping auto-off")
      setStateVar('lastHumidity', currentHumidity.toString())
      return
    }

    // Bug #4 fix: Turn off when humidity returned to normal
    Boolean humidityNormal = (currentHumidity - changeLimitValue) <= baseline

    if (humidityNormal || decreasingSuccessively == 'true') {
      // Feature 1: Check minimum runtime
      Integer minRuntimeValue = getSetting('minRuntime') as Integer
      if (minRuntimeValue != null && minRuntimeValue > 0) {
        String fanOnSinceStr = getStateVar(FAN_ON_SINCE) as String
        if (fanOnSinceStr != null) {
          Long fanOnSince = fanOnSinceStr as Long
          Long elapsed = (getCurrentTime() as Long) - fanOnSince
          Long minMs = minRuntimeValue * 60000L
          if (elapsed < minMs) {
            logDebug("Fan has only run ${elapsed / 60000} min, min runtime is ${minRuntimeValue} min. Skipping off.")
            setStateVar('lastHumidity', currentHumidity.toString())
            return
          }
        }
      }

      String reason = humidityNormal ? "humidity returned to normal" : "decreasing successively"
      logDebug("Turning off fan: ${reason} (humidity ${currentHumidity}%, baseline ${baseline}%)")
      callDeviceMethod(fanSwitchDevice, 'off')

      // Feature 6: Notification
      String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String
      sendNotification("${displayName}: Fan turned off (${reason}, humidity ${currentHumidity}%)")
    }
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
    // Feature 2: Record fan off time for cooldown tracking
    setStateVar(FAN_OFF_SINCE, getCurrentTime().toString())

    // Feature 8: Record fan stop with duration and current humidity
    String fanOnSinceStr = getStateVar(FAN_ON_SINCE) as String
    if (fanOnSinceStr != null) {
      Long fanOnSince = fanOnSinceStr as Long
      Long durationMs = (getCurrentTime() as Long) - fanOnSince
      BigDecimal durationMinutes = new BigDecimal(durationMs) / 60000

      String currentHumStr = getStateVar('currentHumidity') as String
      if (currentHumStr != null) {
        ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
        callDeviceMethod(child, 'recordFanStop', [new BigDecimal(currentHumStr), durationMinutes])
      }
    }

    // Improvement B: Unfreeze baseline
    ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
    callDeviceMethod(child, 'setBaselineFreeze', 'false')

    // Clean up state
    removeStateVar(FAN_ON_SINCE)
    removeStateVar(TRIGGERED_BY_APP)
    removeStateVar(FAN_START_HUMIDITY)

    // Cancel max runtime timer
    unscheduleMethod("runtimeExceeded")
  } else if (event.value == "on") {
    // Bug #6 fix: Only schedule maxRuntime if fanOnSince doesn't already exist
    // (prevents timer reset on redundant on commands)
    if (getStateVar(FAN_ON_SINCE) == null) {
      setStateVar(FAN_ON_SINCE, getCurrentTime().toString())

      Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
      if (maxRuntimeValue > 0) {
        logDebug("Scheduling fan shutoff for ${maxRuntimeValue} minutes")
        scheduleIn(maxRuntimeValue * 60, "runtimeExceeded")
      }
    }

    // Feature 3: If triggeredByApp wasn't set, mark as manual override
    if (getStateVar(TRIGGERED_BY_APP) == null) {
      setStateVar(TRIGGERED_BY_APP, 'false')
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

  // Feature 6: Notification
  sendNotification("${displayName}: Fan auto-off after door open for ${doorOpenTimeValue} minutes")
}

@CompileStatic
void runtimeExceeded() {
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
  String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String

  logInfo("Auto-off: ${displayName} has been on for ${maxRuntimeValue} minutes")

  callDeviceMethod(fanSwitchDevice, 'off')

  // Feature 6: Notification
  sendNotification("${displayName}: Fan auto-off after max runtime of ${maxRuntimeValue} minutes")
}
