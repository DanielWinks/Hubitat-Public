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
 *  3. When humidity suddenly spikes above the stable baseline with a rapid rise,
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

// ============================================================================
// STANDALONE HELPER FUNCTIONS
// ============================================================================

// Logging helpers
void logInfo(String message) {
  if (settings.logEnable != false) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}
void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}
void logWarn(String message) {
  if (settings.logEnable != false) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}
void logError(String message) {
  if (settings.logEnable != false) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}

// Exception logging helpers
void logException(String message) {
  if (settings.logEnable != false) {
    if(device) log.exception "${device.label ?: device.name }: ${message}"
    if(app) log.exception "${app.label ?: app.name }: ${message}"
  }
}
void logExceptionWithDetails(String message, Exception exception) {
  logException("${message}: ${exception.message}")
}

// State and settings helpers
Object getStateVar(String key) { return state[key] }
void setStateVar(String key, Object value) { state[key] = value }
void removeStateVar(String key) {
  if (app) app.getState().remove(key)
  if (device) device.getState().remove(key)
}
Object getSetting(String key) { return settings[key] }
Long getCurrentTime() { return now() }

// Scheduling helper
void scheduleIn(Integer seconds, String methodName) { runIn(seconds, methodName) }
void unscheduleMethod(String methodName) { unschedule(methodName) }

// App label helper
String getAppLabel() { return app.label }

DeviceWrapper getAppChildDevice(String dni) { return getChildDevice(dni) }
DeviceWrapper createAppChildDevice(String namespace, String typeName, String dni, Map properties) {
  return addChildDevice(namespace, typeName, dni, properties)
}

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

      input "householdHumiditySensor", "capability.relativeHumidityMeasurement", title: "Optional Household Humidity Sensor for diagnostics", multiple: false, required: false

      input "fanSwitch", "capability.switch", title: "Fan Switch", required: true

    }

    // -------------------------------------------------------------------------
    // Maximum Runtime Configuration
    // -------------------------------------------------------------------------
    section("<b>Fan Run Limits:</b>", hideable: true, hidden: false) {
      paragraph "Maximum time fan may run under any circumstances."

      input "maxRuntime", "number", title: "Max run time (minutes, 0 to disable, max 720)", required: true, defaultValue: 60, range: '0..720'
      input "minRuntime", "number", title: "Minimum fan run time (minutes, 0 to disable, max 30)", required: false, defaultValue: 5, range: '0..30'
      input "cooldownPeriod", "number", title: "Re-trigger delay after fan turns off (minutes, 0 to disable, max 30)", required: false, defaultValue: 3, range: '0..30'

      input "doorSensor", "capability.contactSensor", title: "Door Sensor (optional)", required: false, submitOnChange: true
      if (settings.doorSensor) {
        input "doorOpenTime", "number", title: "Max run time after door opening (minutes, 0 to disable, max 60)", required: true, defaultValue: 10, range: '0..60'
      }
    }

    // -------------------------------------------------------------------------
    // Absolute Ceiling
    // -------------------------------------------------------------------------
    section("<b>Safety Override:</b>", hideable: true, hidden: false) {
      paragraph 'If humidity reaches this emergency ceiling, the fan may start even without a valid rise-rate signal. Mode restrictions still prevent automatic starts.'

      input "absoluteCeiling", "number", title: "Absolute humidity ceiling (%, 0 to disable, max 95)", required: false, defaultValue: 95, range: '0..95'
    }

    // -------------------------------------------------------------------------
    // Fan Dimmer / Speed Control
    // -------------------------------------------------------------------------
    section("<b>Fan Speed Control:</b>", hideable: true, hidden: false) {
      paragraph 'Optional: Use a dimmer-capable fan switch to set low/high speed based on humidity delta.'

      input "fanDimmer", "capability.switchLevel", title: "Fan Dimmer (optional)", required: false, submitOnChange: true

      if (settings.fanDimmer) {
        input "lowSpeedLevel", "number", title: "Low speed level (%)", required: false, defaultValue: 50, range: '1..100'
        input "highSpeedLevel", "number", title: "High speed level (%)", required: false, defaultValue: 100, range: '1..100'
        input "highSpeedThreshold", "number", title: "Humidity delta above baseline to trigger high speed", required: false, defaultValue: 10, range: '1..50'
      }
    }

    // -------------------------------------------------------------------------
    // Notification Device
    // -------------------------------------------------------------------------
    section("<b>Notifications:</b>", hideable: true, hidden: false) {
      paragraph 'Optional: Send push notifications when the fan turns on/off.'

      input "notificationDevice", "capability.notification", title: "Notification Device (optional)", required: false
    }

    // -------------------------------------------------------------------------
    // Mode Policies
    // -------------------------------------------------------------------------
    section("<b>Mode Restriction:</b>", hideable: true, hidden: false) {
      paragraph 'Automatic fan starts are blocked in the modes selected below. Leave empty to allow automatic starts in every mode.'
      input "disallowedModes", "mode", title: "Modes that disallow automatic fan starts", required: false, multiple: true
    }

    section("<b>High-Certainty Modes:</b>", hideable: true, hidden: false) {
      paragraph 'Automatic starts require a stronger humidity rise and rise rate in the modes selected below. This is useful for preventing false starts overnight without disabling shower detection.'
      input "highCertaintyModes", "mode", title: "Modes requiring high-certainty starts", required: false, multiple: true
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
@Field static final String HOUSEHOLD_HUMIDITY_AT = 'householdHumidityAt'
@Field static final String LAST_HUMIDITY_AT = 'lastHumidityAt'
@Field static final String SENSOR_READINGS = 'sensorReadings'
@Field static final String RISE_CANDIDATE_COUNT = 'riseCandidateCount'
@Field static final String DECLINE_SAMPLE_COUNT = 'declineSampleCount'
@Field static final String REARM_REQUIRED = 'rearmRequired'
@Field static final String PEAK_HUMIDITY = 'peakHumidity'
@Field static final String LAST_SHORT_TERM_RATE = 'lastShortTermRate'
@Field static final String LAST_RATE_ACCELERATION = 'lastRateAcceleration'
@Field static final String LAST_MEASUREMENT_GAP = 'lastMeasurementGap'
@Field static final String CONTROL_BASELINE_ATTRIBUTE = 'slowRollingAverage'
@Field static final String CONTROL_BASELINE = 'controlBaseline'
@Field static final String CONTROL_BASELINE_AT = 'controlBaselineAt'

// Fixed control tuning. These values intentionally are not user preferences:
// the slow EMA provides the weather-resistant baseline, the rate gate filters
// gradual environmental drift, and the confirmation/hysteresis prevents
// short-lived spikes from causing fan chatter.
@Field static final BigDecimal STOP_DELTA = new BigDecimal('1.0')
@Field static final BigDecimal MIN_RISE_RATE = new BigDecimal('0.20')
@Field static final BigDecimal STRONG_RISE_RATE = new BigDecimal('0.60')
@Field static final BigDecimal MIN_RISE_ACCELERATION = new BigDecimal('0.05')
@Field static final BigDecimal MIN_FALL_RATE = new BigDecimal('0.05')
@Field static final BigDecimal HIGH_CERTAINTY_MULTIPLIER = new BigDecimal('1.5')
@Field static final BigDecimal REARM_DELTA = new BigDecimal('0.5')
@Field static final BigDecimal PEAK_DROP_TO_STOP = new BigDecimal('2.0')
@Field static final Integer RISE_CONFIRMATION_SAMPLES = 2
@Field static final Integer DECLINE_CONFIRMATION_SAMPLES = 2
@Field static final Long HOUSEHOLD_FRESHNESS_MILLISECONDS = 6L * 60L * 60L * 1000L
@Field static final Long CONTROL_BASELINE_TAU_MILLISECONDS = 360L * 60L * 1000L
@Field static final double CONTROL_BASELINE_MAX_ALPHA = 0.25d

String getHumidityStatSensor() {
  return "${app.id}-${humidityStaticSensor}"
}

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

void installed() {
  configure()
}

void updated() {
  configure()
}

void uninstalled() {
  unsubscribe()
  unschedule()

  try {
    deleteChildDevice(getHumidityStatSensor())
  } catch (Exception e) {
    logWarn("Unable to remove humidity statistics child device: ${e.message}")
  }
}

void configure() {
  unsubscribe()

  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())

  if (child == null) {
    logError('Humidity Statistics driver is not installed; automation is disabled')
    return
  }

  initializeApp(child)
}

void initializeApp(ChildDeviceWrapper child) {
  humiditySensors.each { sensor ->
    subscribe(sensor, "humidity", humidityEvent)
  }

  subscribe(fanSwitch, "switch", switchEvent)

  if (doorSensor) {
    subscribe(doorSensor, "contact", contactEvent)
  }

  // Subscribe to household humidity sensor if configured (Bug #3 fix)
  if (householdHumiditySensor) {
    subscribe(householdHumiditySensor, "humidity", householdHumidityEvent)
  }

  // Subscribe to location mode changes (Feature 7)
  subscribe(location, "mode", modeChangeEvent)

  String fanState = fanSwitch.currentValue('switch') as String
  child.setBaselineFreeze(fanState == 'on' ? 'true' : 'false')

  // Seed the app-owned control baseline from existing statistics and the most
  // recent household reading. This avoids waiting for a new bathroom event
  // after an app update before the external humidity reference is used.
  updateControlBaseline(child.currentValue(CONTROL_BASELINE_ATTRIBUTE) as BigDecimal, getCurrentTime())
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
 * Stores household humidity from the optional household sensor and uses it as
 * the external reference for the fan-control baseline. The raw reading remains
 * available on the statistics child for diagnostics.
 */
void householdHumidityEvent(Event event) {
  logDebug("Received household humidity event: ${event.value}")
  BigDecimal humidity
  try {
    humidity = new BigDecimal(event.value as String)
  } catch (Exception e) {
    logWarn("Ignoring invalid household humidity value: ${event.value}")
    return
  }

  Long currentTime = getCurrentTime()
  setStateVar(HOUSEHOLD_HUMIDITY, humidity.toString())
  setStateVar(HOUSEHOLD_HUMIDITY_AT, currentTime.toString())

  // Forward to the child device so it can expose bathroomDifferential.
  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
  if (child != null) {
    child.setHouseholdHumidity(humidity)
    updateControlBaseline(child.currentValue(CONTROL_BASELINE_ATTRIBUTE) as BigDecimal, currentTime)
  } else {
    updateControlBaseline(null, currentTime)
  }
}

/**
 * Logs hub mode changes for debugging.
 */
void modeChangeEvent(Event event) {
  logDebug("Hub mode changed to: ${event.value}")
}

/**
 * Normalizes a Hubitat single- or multi-select mode preference to a list.
 */
List<String> getConfiguredModes(Object configuredModes) {
  if (configuredModes == null) {
    return []
  }
  if (configuredModes instanceof Collection) {
    return configuredModes.collect { Object mode -> mode.toString() }
  }
  return [configuredModes.toString()]
}

/**
 * Returns true when automatic fan starts are allowed in the current mode.
 */
Boolean isModeAllowed() {
  String currentMode = location.mode as String
  List<String> disallowedModes = getConfiguredModes(settings.disallowedModes)
  return !disallowedModes.any { String mode -> mode.equalsIgnoreCase(currentMode) }
}

/**
 * Returns true when the current mode requires stronger evidence before an
 * automatic fan start.
 */
Boolean isHighCertaintyMode() {
  String currentMode = location.mode as String
  List<String> highCertaintyModes = getConfiguredModes(settings.highCertaintyModes)
  return highCertaintyModes.any { String mode -> mode.equalsIgnoreCase(currentMode) }
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
void sendNotification(String message) {
  DeviceWrapper notifDevice = getSetting('notificationDevice') as DeviceWrapper
  if (notifDevice != null) {
    notifDevice.deviceNotification(message)
  }
}

/**
 * Sets fan dimmer level based on humidity delta above baseline (Feature 5).
 */
void setFanSpeed(BigDecimal currentHumidity, BigDecimal baseline) {
  DeviceWrapper dimmer = getSetting('fanDimmer') as DeviceWrapper
  if (dimmer == null) { return }

  Integer lowSpeed = (getSetting('lowSpeedLevel') ?: 50) as Integer
  Integer highSpeed = (getSetting('highSpeedLevel') ?: 100) as Integer
  Integer threshold = (getSetting('highSpeedThreshold') ?: 10) as Integer

  BigDecimal delta = currentHumidity - baseline
  Integer level = delta >= threshold ? highSpeed : lowSpeed

  logDebug("Setting fan speed to ${level}% (delta: ${delta}, threshold: ${threshold})")
  dimmer.setLevel(level)
}

// =============================================================================
// HUMIDITY PROCESSING - THE CORE LOGIC
// =============================================================================

String getHumiditySensorKey(Event event) {
  Object sensorId = event.deviceId
  if (sensorId == null) {
    sensorId = event.deviceNetworkId
  }
  if (sensorId == null && event.device != null) {
    sensorId = event.device.deviceNetworkId ?: event.device.id
  }
  return sensorId == null ? 'default' : sensorId.toString()
}

/**
 * Aggregates the latest value from each selected bathroom sensor.
 *
 * The maximum is intentional: a sensor closest to the shower should be able
 * to start the fan immediately, even if another selected sensor is asleep.
 * The baseline is built from the same aggregate, so a persistent difference
 * between sensors is learned rather than treated as a new trigger each time.
 */
BigDecimal aggregateHumidity(Map readings) {
  List<BigDecimal> values = []
  readings.each { Object key, Object readingObject ->
    if (readingObject instanceof Map) {
      Object humidityObject = (readingObject as Map).humidity
      if (humidityObject != null) {
        try {
          values << new BigDecimal(humidityObject.toString())
        } catch (Exception ignored) {
          logDebug("Ignoring invalid stored humidity for sensor ${key}")
        }
      }
    }
  }
  return values ? values.max() : null
}

/**
 * Selects the candidate baseline for the current environment. Household
 * humidity is a floor rather than a replacement for the bathroom history:
 * bathrooms can normally run drier than the rest of the house, but a reading
 * below the household level must not look like a shower spike.
 */
@CompileStatic
BigDecimal selectControlBaselineCandidate(BigDecimal bathroomBaseline, BigDecimal householdHumidity) {
  if (bathroomBaseline == null) {
    return householdHumidity
  }
  if (householdHumidity == null) {
    return bathroomBaseline
  }
  return bathroomBaseline.max(householdHumidity)
}

/**
 * Moves a stored control baseline toward a new candidate without allowing a
 * single noisy household report or a long reporting gap to move it abruptly.
 */
@CompileStatic
BigDecimal calculateStableControlBaseline(
  BigDecimal previousBaseline,
  BigDecimal candidate,
  Long elapsedMilliseconds
) {
  if (candidate == null) {
    return previousBaseline
  }
  if (previousBaseline == null || elapsedMilliseconds == null || elapsedMilliseconds <= 0L) {
    return previousBaseline == null ? candidate : previousBaseline
  }

  double alpha = 1.0d - Math.exp(-elapsedMilliseconds.doubleValue() / CONTROL_BASELINE_TAU_MILLISECONDS.doubleValue())
  if (alpha > CONTROL_BASELINE_MAX_ALPHA) {
    alpha = CONTROL_BASELINE_MAX_ALPHA
  }
  BigDecimal smoothing = BigDecimal.valueOf(alpha)
  return previousBaseline + ((candidate - previousBaseline) * smoothing)
}

/**
 * Returns the latest household reading only while it is recent enough to be a
 * useful outside-the-bathroom reference.
 */
BigDecimal getFreshHouseholdHumidity(Long currentTime) {
  String humidityString = getStateVar(HOUSEHOLD_HUMIDITY) as String
  String timestampString = getStateVar(HOUSEHOLD_HUMIDITY_AT) as String
  if (humidityString == null || timestampString == null) {
    return null
  }

  Long timestamp
  try {
    timestamp = timestampString as Long
  } catch (Exception ignored) {
    return null
  }

  if (timestamp > currentTime || currentTime - timestamp > HOUSEHOLD_FRESHNESS_MILLISECONDS) {
    return null
  }

  try {
    return new BigDecimal(humidityString)
  } catch (Exception ignored) {
    return null
  }
}

/**
 * Updates and returns the app-owned, slowly adapting control baseline.
 */
BigDecimal updateControlBaseline(BigDecimal bathroomBaseline, Long currentTime) {
  BigDecimal householdHumidity = getFreshHouseholdHumidity(currentTime)
  BigDecimal candidate = selectControlBaselineCandidate(bathroomBaseline, householdHumidity)
  BigDecimal previousBaseline
  try {
    previousBaseline = new BigDecimal(getStateVar(CONTROL_BASELINE) as String)
  } catch (Exception ignored) {
    previousBaseline = null
  }

  // Do not adapt the control baseline while the fan is running. The child
  // statistics are frozen too, and both protections keep a shower from
  // becoming the next run's definition of normal.
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  if (fanSwitchDevice?.currentValue('switch') == 'on') {
    return previousBaseline ?: candidate
  }

  String previousAtString = getStateVar(CONTROL_BASELINE_AT) as String
  Long previousAt = null
  try {
    previousAt = previousAtString as Long
  } catch (Exception ignored) {
    previousAt = null
  }

  Long elapsedMilliseconds = previousAt == null ? 0L : Math.max(0L, currentTime - previousAt)
  BigDecimal stableBaseline = calculateStableControlBaseline(previousBaseline, candidate, elapsedMilliseconds)
  if (stableBaseline != null) {
    setStateVar(CONTROL_BASELINE, stableBaseline.toString())
    setStateVar(CONTROL_BASELINE_AT, currentTime.toString())
    logDebug("Control baseline updated: bathroom=${bathroomBaseline}, household=${householdHumidity}, baseline=${stableBaseline.setScale(1, BigDecimal.ROUND_HALF_UP)}")
  }
  return stableBaseline
}

/**
 * A fresh household reading must not be higher than the bathroom before the
 * local slope can qualify as a shower rise. This specifically rejects the
 * common overnight case where the bathroom slowly rises but remains drier
 * than the rest of the house.
 */
@CompileStatic
Boolean isAboveHouseholdBaseline(BigDecimal currentHumidity, BigDecimal householdHumidity, BigDecimal requiredDelta) {
  return householdHumidity == null || currentHumidity >= householdHumidity + requiredDelta
}

void humidityEvent(Event event) {
  logDebug("Received humidity event: ${event.value}")

  BigDecimal value
  try {
    value = new BigDecimal(event.value as String)
  } catch (Exception e) {
    logWarn("Ignoring invalid humidity value: ${event.value}")
    return
  }

  if (value > 0 && value < 100) {
    Long currentTime = getCurrentTime()
    Map readings = (getStateVar(SENSOR_READINGS) as Map) ?: [:]
    readings[getHumiditySensorKey(event)] = [humidity: value.toString(), at: currentTime.toString()]
    setStateVar(SENSOR_READINGS, readings)

    BigDecimal aggregate = aggregateHumidity(readings)
    if (aggregate == null) {
      return
    }

    ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
    if (child == null) {
      logError('Humidity Statistics driver is unavailable; cannot evaluate humidity')
      setStateVar('currentHumidity', aggregate.toString())
      setStateVar('lastHumidity', aggregate.toString())
      setStateVar(LAST_HUMIDITY_AT, currentTime.toString())
      return
    }

    // Capture the stable baseline before recording this reading. The driver
    // protects it from a single long-gap reading and freezes it during a run.
    BigDecimal baseline = child.currentValue(CONTROL_BASELINE_ATTRIBUTE) as BigDecimal
    child.logHumidityEvent(aggregate)

    if (baseline == null) {
      // The first reading establishes the baseline; it cannot also be a
      // reliable shower trigger.
      baseline = child.currentValue(CONTROL_BASELINE_ATTRIBUTE) as BigDecimal
    }

    BigDecimal shortTermRate = child.currentValue('shortTermRateOfChange') as BigDecimal
    BigDecimal acceleration = child.currentValue('rateOfChangeAcceleration') as BigDecimal
    Boolean measurementGap = (child.currentValue('measurementGap') as String) == 'true'
    setStateVar('currentHumidity', aggregate.toString())
    setStateVar(LAST_HUMIDITY_AT, currentTime.toString())
    setStateVar(LAST_SHORT_TERM_RATE, shortTermRate?.toString())
    setStateVar(LAST_RATE_ACCELERATION, acceleration?.toString())
    setStateVar(LAST_MEASUREMENT_GAP, measurementGap ? 'true' : 'false')

    BigDecimal controlBaseline = updateControlBaseline(baseline, currentTime)
    if (controlBaseline != null) {
      evaluateFanDecision(aggregate, controlBaseline, shortTermRate, acceleration, measurementGap)
    } else {
      setStateVar('lastHumidity', aggregate.toString())
    }
  }
}

/**
 * Core fan decision logic.
 *
 * @param currentHumidity The current bathroom humidity reading
 * @param baseline The fixed slow rolling average baseline
 * @param shortTermRate The time-weighted local derivative in percentage points per minute
 * @param acceleration The local derivative acceleration, when available
 * @param measurementGap True when the previous reporting interval was stale
 */
void evaluateFanDecision(
  BigDecimal currentHumidity,
  BigDecimal baseline,
  BigDecimal shortTermRate,
  BigDecimal acceleration,
  Boolean measurementGap
) {
  String lastHumidityString = getStateVar('lastHumidity') as String
  BigDecimal lastHumidity = lastHumidityString == null ? currentHumidity : new BigDecimal(lastHumidityString)

  Boolean humidityDecreasing = currentHumidity < lastHumidity
  Integer declineSampleCount = (getStateVar(DECLINE_SAMPLE_COUNT) ?: 0) as Integer
  if (humidityDecreasing) {
    declineSampleCount += 1
  } else {
    declineSampleCount = 0
  }
  setStateVar(DECLINE_SAMPLE_COUNT, declineSampleCount)

  Boolean highCertainty = isHighCertaintyMode()
  BigDecimal certaintyMultiplier = highCertainty ? HIGH_CERTAINTY_MULTIPLIER : BigDecimal.ONE
  BigDecimal requiredRiseRate = MIN_RISE_RATE * certaintyMultiplier
  BigDecimal strongRiseRate = STRONG_RISE_RATE * certaintyMultiplier

  BigDecimal householdHumidity = getFreshHouseholdHumidity(getCurrentTime())
  Boolean householdQualified = isAboveHouseholdBaseline(currentHumidity, householdHumidity, BigDecimal.ZERO)

  Boolean ceilingReached = false
  Integer ceiling = getSetting('absoluteCeiling') as Integer
  if (ceiling != null && ceiling > 0) {
    ceilingReached = currentHumidity >= ceiling
  }

  // The slope is the primary shower signal. The baseline and household checks
  // only establish that the rising signal is occurring at an elevated level;
  // they do not require a fixed percentage jump before the fan can start.
  Boolean aboveControlBaseline = currentHumidity > baseline
  Boolean usableRate = shortTermRate != null && !measurementGap
  Boolean rateQualified = usableRate && shortTermRate >= requiredRiseRate
  Boolean strongRiseQualified = usableRate && shortTermRate >= strongRiseRate
  Boolean accelerationQualified = usableRate && acceleration != null && acceleration >= MIN_RISE_ACCELERATION
  Boolean riseCandidate = aboveControlBaseline && householdQualified && rateQualified
  Integer riseCandidateCount = (getStateVar(RISE_CANDIDATE_COUNT) ?: 0) as Integer
  if (riseCandidate) {
    riseCandidateCount += 1
  } else {
    riseCandidateCount = 0
  }
  setStateVar(RISE_CANDIDATE_COUNT, riseCandidateCount)

  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  String fanState = fanSwitchDevice.currentValue("switch") as String

  // A reporting gap invalidates the local slope, so there is deliberately no
  // post-gap magnitude shortcut. The next frequent samples must establish a
  // real rate of rise before an automatic start is allowed.
  if (aboveControlBaseline && householdHumidity != null && !householdQualified) {
    logDebug("Fan trigger withheld: bathroom humidity ${currentHumidity}% is below fresh household humidity ${householdHumidity}%")
  }

  // Start immediately on a strong local rise. Marginal local rises require two
  // consecutive qualifying readings, including after a reporting gap.
  Boolean shouldTurnOn = ceilingReached ||
    (riseCandidate && (strongRiseQualified ||
      (highCertainty && accelerationQualified) ||
      riseCandidateCount >= RISE_CONFIRMATION_SAMPLES))

  // Once a run ends, require the humidity to come back close to baseline
  // before accepting another automatic start. This is separate from the
  // time-based cooldown and prevents on/off/on chatter while humidity is
  // still elevated.
  Boolean rearmRequired = getStateVar(REARM_REQUIRED) == 'true'
  if (fanState != 'on' && rearmRequired && !ceilingReached) {
    if (currentHumidity <= baseline + REARM_DELTA) {
      removeStateVar(REARM_REQUIRED)
      rearmRequired = false
      logDebug('Humidity returned close to baseline; automatic fan starts are re-armed')
    } else {
      shouldTurnOn = false
      setStateVar(RISE_CANDIDATE_COUNT, 0)
    }
  }

  if (shouldTurnOn && fanState != 'on') {
    if (!isModeAllowed()) {
      logDebug("Fan trigger skipped: automatic starts are disallowed in mode ${location.mode}")
      setStateVar(RISE_CANDIDATE_COUNT, 0)
      setStateVar('lastHumidity', currentHumidity.toString())
      return
    }

    // Feature 2: Cooldown check
    if (isInCooldown()) {
      logDebug("Fan trigger skipped: still in cooldown period")
      setStateVar(RISE_CANDIDATE_COUNT, 0)
      setStateVar('lastHumidity', currentHumidity.toString())
      return
    }

    // Turn on the fan
    String triggerReason = ceilingReached ?
      "absolute ceiling ${ceiling}% reached" :
      "humidity rose ${currentHumidity - baseline}% above baseline at ${shortTermRate?.setScale(2, BigDecimal.ROUND_HALF_UP)}%/min"
    logDebug("${triggerReason}; turning on fan")
    setStateVar(TRIGGERED_BY_APP, 'true')  // Feature 3: track auto-trigger
    setStateVar(FAN_START_HUMIDITY, currentHumidity.toString())  // Feature 8: track start humidity
    setStateVar(PEAK_HUMIDITY, currentHumidity.toString())
    setStateVar(RISE_CANDIDATE_COUNT, 0)
    fanSwitchDevice.on()

    // Feature 5: Set fan speed
    setFanSpeed(currentHumidity, baseline)

    // Feature 8: Record fan start on child device
    ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
    if (child != null) {
      child.recordFanStart(currentHumidity)

      // Improvement B: Freeze baseline
      child.setBaselineFreeze('true')
    }

    // Feature 6: Notification
    String displayName = fanSwitchDevice.displayName as String
    sendNotification("${displayName}: Fan turned on (humidity ${currentHumidity}%, baseline ${baseline}%)")
  } else if (fanState == 'on') {
    // --- Fan is on, evaluate whether to turn it off ---

    // Feature 3: Don't auto-off a manually turned on fan
    if (getStateVar(TRIGGERED_BY_APP) != 'true') {
      logDebug("Fan was not triggered by app, skipping auto-off")
      setStateVar('lastHumidity', currentHumidity.toString())
      return
    }

    BigDecimal peakHumidity = (getStateVar(PEAK_HUMIDITY) ?: currentHumidity.toString()) as BigDecimal
    if (currentHumidity > peakHumidity) {
      peakHumidity = currentHumidity
      setStateVar(PEAK_HUMIDITY, peakHumidity.toString())
    }

    // Use hysteresis so the fan can turn off earlier than the start point
    // without immediately bouncing back on at the same humidity. A sharp
    // drop from the run peak is allowed on its first reading; a small,
    // gradual decline still needs consecutive samples.
    Boolean humidityNormal = currentHumidity <= baseline + STOP_DELTA
    Boolean sharpDecline = !measurementGap &&
      peakHumidity - currentHumidity >= PEAK_DROP_TO_STOP &&
      shortTermRate != null && shortTermRate <= -MIN_FALL_RATE
    Boolean sustainedDecline = humidityDecreasing &&
      declineSampleCount >= DECLINE_CONFIRMATION_SAMPLES &&
      !measurementGap && shortTermRate != null && shortTermRate <= -MIN_FALL_RATE

    if (humidityNormal || sharpDecline || sustainedDecline) {
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

      String reason = humidityNormal ? "humidity returned to normal" :
        (sharpDecline ? "humidity dropped sharply after the peak" : "humidity is declining steadily")
      logDebug("Turning off fan: ${reason} (humidity ${currentHumidity}%, baseline ${baseline}%)")
      fanSwitchDevice.off()

      // Feature 6: Notification
      String displayName = fanSwitchDevice.displayName as String
      sendNotification("${displayName}: Fan turned off (${reason}, humidity ${currentHumidity}%)")
    }
  }

  setStateVar('lastHumidity', currentHumidity.toString())
}

// =============================================================================
// FAN & DOOR EVENT HANDLING - SAFETY FEATURES
// =============================================================================

void switchEvent(Event event) {
  logDebug("Received switch event: ${event.value}")

  if (event.value == "off") {
    Boolean wasAutoTriggered = getStateVar(TRIGGERED_BY_APP) == 'true'

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
        if (child != null) {
          child.recordFanStop(new BigDecimal(currentHumStr), durationMinutes)
        }
      }
    }

    // Improvement B: Unfreeze baseline
    ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
    if (child != null) {
      child.setBaselineFreeze('false')
    }

    // Clean up state
    removeStateVar(FAN_ON_SINCE)
    removeStateVar(TRIGGERED_BY_APP)
    removeStateVar(FAN_START_HUMIDITY)
    if (wasAutoTriggered) {
      setStateVar(REARM_REQUIRED, 'true')
    } else {
      removeStateVar(REARM_REQUIRED)
      removeStateVar(PEAK_HUMIDITY)
    }
    setStateVar(RISE_CANDIDATE_COUNT, 0)
    setStateVar(DECLINE_SAMPLE_COUNT, 0)

    // Cancel pending auto-off timers so they don't fire later against a
    // subsequent manual re-on of the fan within the original timer window.
    unscheduleMethod("runtimeExceeded")
    unscheduleMethod("doorOpenedAutoOff")
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

    // If humidity has already normalized, turn off immediately rather than
    // waiting for the next humidity event or the door-open timer.
    if (fanState == "on") {
      evaluateOffFromCurrentState()
    }
  }
  else if (event.value == "closed") {
    unscheduleMethod("doorOpenedAutoOff")
  }
}

/**
 * Reads the latest humidity from state and baseline from the child device,
 * then invokes evaluateFanDecision. Used when an external trigger (e.g. the
 * door opening) should cause an immediate re-evaluation rather than waiting
 * for the next periodic humidity event.
 */
@CompileStatic
void evaluateOffFromCurrentState() {
  String currentHumStr = getStateVar('currentHumidity') as String
  if (currentHumStr == null) { return }
  BigDecimal currentHumidity = new BigDecimal(currentHumStr)

  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
  if (child == null) {
    return
  }

  BigDecimal baseline = child.currentValue(CONTROL_BASELINE_ATTRIBUTE) as BigDecimal
  baseline = updateControlBaseline(baseline, getCurrentTime())
  if (baseline != null) {
    BigDecimal shortTermRate = getStateVar(LAST_SHORT_TERM_RATE) as BigDecimal
    BigDecimal acceleration = getStateVar(LAST_RATE_ACCELERATION) as BigDecimal
    Boolean measurementGap = getStateVar(LAST_MEASUREMENT_GAP) == 'true'
    evaluateFanDecision(currentHumidity, baseline, shortTermRate, acceleration, measurementGap)
  }
}

void doorOpenedAutoOff() {
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer doorOpenTimeValue = getSetting('doorOpenTime') as Integer
  String displayName = fanSwitchDevice.displayName as String

  logInfo("Auto-off: ${displayName} has been on with door open for ${doorOpenTimeValue} minutes")

  fanSwitchDevice.off()

  // Feature 6: Notification
  sendNotification("${displayName}: Fan auto-off after door open for ${doorOpenTimeValue} minutes")
}

void runtimeExceeded() {
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
  String displayName = fanSwitchDevice.displayName as String

  logInfo("Auto-off: ${displayName} has been on for ${maxRuntimeValue} minutes")

  fanSwitchDevice.off()

  // Feature 6: Notification
  sendNotification("${displayName}: Fan auto-off after max runtime of ${maxRuntimeValue} minutes")
}
