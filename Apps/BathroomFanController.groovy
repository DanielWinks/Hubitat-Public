/**
 *  ============================================================================
 *  BATHROOM FAN CONTROLLER - SMART HUMIDITY-BASED FAN AUTOMATION
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
definition (
  name: "Bathroom Fan Controller",              // Display name shown in Hubitat interface
  namespace: "dwinks",                          // Author's unique identifier to prevent name conflicts
  author: "Daniel Winks",                       // Who created this app
  description: "Bathroom Fan Controller",       // Brief description of what this app does
  category: "Convenience",                      // Category for organizing apps in Hubitat
  iconUrl: "",                                  // Optional: URL to small icon image
  iconX2Url: "",                                // Optional: URL to medium icon image
  iconX3Url: ""                                 // Optional: URL to large icon image
)

// ============================================================================
// USER PREFERENCES - Define the configuration page users see
// ============================================================================

// Tell Hubitat this app has one configuration page called "mainPage"
preferences { page(name: "mainPage", title: "Bathroom Fan Controller") }

// =============================================================================
// APP CONFIGURATION PAGE
// =============================================================================
// This function creates the user interface where users configure the app.
// It returns a Map (a collection of key-value pairs) that defines the page layout.
// The page is "dynamic" meaning it can change based on user selections.
// =============================================================================

Map mainPage() {
  // Create a dynamic configuration page with these properties:
  // - name: Internal identifier for this page
  // - title: What users see at the top (empty here, sections have their own titles)
  // - install: true = show "Done" button to save and activate the app
  // - uninstall: true = show option to remove the app
  // - refreshInterval: 0 = don't auto-refresh the page (would disrupt user input)
  dynamicPage(name: "mainPage", title: "", install: true, uninstall: true, refreshInterval: 0) {

    // -------------------------------------------------------------------------
    // SECTION 1: Device Selection
    // -------------------------------------------------------------------------
    // This section lets users choose which devices the app will monitor/control
    section("<b>Device Instructions</b>", hideable: true, hidden: false) {
      // Instructions explaining what devices do
      paragraph "The humidity sensor(s) you select will control the fan switch you select."

      // Input field for selecting humidity sensors
      // - "humiditySensors": The variable name that will store the selected devices
      // - "capability.relativeHumidityMeasurement": Only show devices that can measure humidity
      // - multiple: true = users can select more than one sensor
      // - required: true = app won't save without selecting at least one
      input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Humidity Sensor(s)", multiple: true, required: true

      // Optional household humidity sensor (like a thermostat's humidity reading)
      // This can be used to compare bathroom humidity to the rest of the house
      input "householdHumiditySensor", "capability.relativeHumidityMeasurement", title: "Optional Household Humidity Sensor, i.e. central thermostat humidity sensor", multiple: false, required: false

      // Input field for selecting the fan switch to control
      // - capability.switch: Any device that can turn on/off
      // - multiple: false (default) = only one switch can be selected
      input "fanSwitch", "capability.switch", title: "Fan Switch", required: true

      // Instructions for optional door sensor
      paragraph "The door sensor you select will turn off the fan when the door has been open for a specified time, 10 minutes by default. Optional."

      // Input field for optional door contact sensor
      // - capability.contactSensor: Device that detects open/closed state
      // - required: false = user doesn't have to select one
      input "doorSensor", "capability.contactSensor", title: "Door Sensor", required: false
    }

    // -------------------------------------------------------------------------
    // SECTION 2: Change Limit Configuration
    // -------------------------------------------------------------------------
    // Determines how much humidity must rise above "normal" to trigger the fan
    section("<b>Change Limit:</b>", hideable: true, hidden: false) {
      // Explain what this setting controls
      paragraph "Maximum change a newly received value can be from historical average without turning on fan."

      // Number input for the humidity change threshold
      // - "number": Accept only numeric input
      // - defaultValue: 2 = if humidity rises 2% above average, turn on fan
      // Example: If average is 45%, and current is 47%, that's within the limit.
      //          If current is 48%, that's above the limit and triggers the fan.
      input "changeLimit", "number", title: "Change Limit", required: true, defaultValue: 2
    }

    // -------------------------------------------------------------------------
    // SECTION 3: Maximum Runtime Configuration
    // -------------------------------------------------------------------------
    // Safety feature to prevent fan from running indefinitely
    section("<b>Max Run Time:</b>", hideable: true, hidden: false) {
      // Explain what this setting controls
      paragraph "Maximum time fan may run under any circumstances."

      // Number input with range validation
      // - range: '0..720' = only accept values between 0 and 720
      // - 0 = disabled (fan can run indefinitely)
      // - defaultValue: 60 = fan auto-shuts off after 60 minutes
      // This prevents the fan from running all day if humidity never drops
      input "maxRuntime", "number", title: "Max run time (minutes, 0 to disable, max 720)", required: true, defaultValue: 60, range: '0..720'
    }

    // -------------------------------------------------------------------------
    // SECTION 4: Door Open Time Configuration
    // -------------------------------------------------------------------------
    // Turns off fan if bathroom door is left open (no need for fan if door is open)
    section("<b>Door Open Time:</b>", hideable: true, hidden: false) {
      // Explain what this setting controls
      paragraph 'Maximum time fan may run after opening door.'

      // Number input for door-open timeout
      // - range: '0..60' = only accept values between 0 and 60 minutes
      // - defaultValue: 10 = turn off fan if door open for 10 minutes
      // Logic: If door is open, air exchanges naturally so fan isn't needed
      input "doorOpenTime", "number", title: "Max run time after door opening (minutes, 0 to disable, max 60)", required: true, defaultValue: 10, range: '0..60'
    }

    // -------------------------------------------------------------------------
    // SECTION 5: Humidity Tracking Mode Selection
    // -------------------------------------------------------------------------
    // Choose which statistical method to use for tracking "normal" humidity
    section("<b>Humidity Statistic To Track:</b>", hideable: true, hidden: false) {
      // Explain what this setting controls
      paragraph 'Humidity statistic type to track for historical value comparison.'

      // Enum (enumeration) input = dropdown with predefined choices
      // Three different ways to calculate the "normal" humidity baseline:
      // 1. Slow Rolling Average: Average of many readings over longer time (very stable)
      // 2. Fast Rolling Average: Average of fewer readings over shorter time (more responsive)
      // 3. Time Weighted Average: Recent readings count more than older readings (balanced)
      // The selected mode determines what "normal" means when checking if humidity spiked
      input 'highHumMode', 'enum', title: 'Humidity Statistic To Track', required: true, offerAll: false, defaultValue: 'Slow Rolling Average', options: [slowRollingAverage:'Slow Rolling Average', fastRollingAverage:'Fast Rolling Average', timeWeightedAverage:'Time Weighted Average']
    }

    // -------------------------------------------------------------------------
    // SECTION 6: Logging Configuration
    // -------------------------------------------------------------------------
    // Control how much diagnostic information the app writes to logs
    section("Logging", hideable: true, hidden: false) {
      // Enable/disable basic logging (important events like "fan turned on")
      input "logEnable", "bool", title: "Enable Logging", required: false, defaultValue: true

      // Enable/disable debug logging (detailed technical info for troubleshooting)
      // Debug logs show every calculation and decision the app makes
      input "debugLogEnable", "bool", title: "Enable debug logging", required: false, defaultValue: false
    }

    // -------------------------------------------------------------------------
    // SECTION 7: Application Name
    // -------------------------------------------------------------------------
    // Let user give this app instance a custom name (useful if running multiple instances)
    section('Application Name:', hideable: true, hidden: false) {
      // Label input lets users rename this app instance
      // Example: "Master Bathroom Fan" vs "Guest Bathroom Fan"
      label title: "Enter a name for this app instance", required: false, defaultValue: 'Bathroom Fan Controller'
    }
  }
}

// =============================================================================
// STATIC CONSTANTS AND HELPER FUNCTIONS
// =============================================================================
// @Field creates a static constant that's shared across the entire app
// This is more memory-efficient than creating the same string multiple times
// =============================================================================

// This is the name template for the child device that tracks humidity statistics
// The child device is a virtual device created by this app to calculate and store
// rolling averages of humidity over time
@Field static final String humidityStaticSensor = 'humidity-stats'

/**
 * Generates the unique Device Network ID (DNI) for the humidity statistics child device.
 *
 * WHAT THIS DOES:
 * Creates a unique identifier by combining the app's ID with a constant name.
 *
 * WHY THIS MATTERS:
 * Every device in Hubitat needs a unique identifier. By using the app's ID,
 * we ensure this device name won't conflict with other apps or devices.
 *
 * EXAMPLE OUTPUT:
 * If app.id is "42", this returns "42-humidity-stats"
 *
 * @return String - The unique identifier for this app's humidity statistics device
 */
String getHumidityStatSensor() {
  return "${app.id}-${humidityStaticSensor}"
}

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================
// These methods are automatically called by Hubitat at specific times:
// - installed(): Called once when the app is first installed
// - updated(): Called whenever the user changes settings
// - uninstalled(): Called when the app is removed
//
// Note: This app uses configure() which is called by both installed() and updated()
// =============================================================================

/**
 * Main configuration method that sets up all subscriptions and child devices.
 *
 * WHEN THIS RUNS:
 * - When the app is first installed (via installed() method)
 * - Whenever settings are changed and saved (via updated() method)
 *
 * WHAT THIS DOES:
 * 1. Unsubscribes from all existing device events (clean slate)
 * 2. Creates or retrieves the humidity statistics tracking device
 * 3. Sets up new subscriptions to monitor all selected devices
 *
 * WHY UNSUBSCRIBE FIRST:
 * If a user changes settings (like selecting different sensors), we need to
 * remove old subscriptions before creating new ones. Otherwise, we'd get
 * duplicate events or events from devices the user removed.
 *
 * EXECUTION FLOW:
 * configure() → unsubscribe() → getOrCreateChildDevices() → initializeApp()
 */
void configure() {
  // Remove all existing event subscriptions to start fresh
  // This prevents duplicate subscriptions if settings changed
  unsubscribe()

  // Get the existing child device or create a new one if it doesn't exist
  // This device tracks humidity statistics over time
  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())

  // Set up subscriptions to monitor all selected devices
  initializeApp(child)
}

/**
 * Initialize all device subscriptions and monitoring.
 *
 * WHAT "SUBSCRIBING" MEANS:
 * When you subscribe to a device event, you're telling Hubitat:
 * "Whenever this device does X, call my Y function"
 *
 * WHAT THIS DOES:
 * Sets up four types of monitoring:
 * 1. Humidity sensors → calls humidityEvent() when humidity changes
 * 2. Fan switch → calls switchEvent() when fan turns on/off
 * 3. Door sensor → calls contactEvent() when door opens/closes
 * 4. Statistics device → calls childHumidityEvent() when statistics update
 *
 * @param child - The humidity statistics child device to monitor
 *
 * IMPORTANT NOTES:
 * - Each sensor can trigger multiple times per minute
 * - The highHumMode variable determines which statistic to monitor
 *   (slowRollingAverage, fastRollingAverage, or timeWeightedAverage)
 * - Door and switch monitoring is for safety/automation features
 */
void initializeApp(ChildDeviceWrapper child) {
  // Subscribe to each selected humidity sensor
  // The .each method loops through all sensors in the humiditySensors list
  // For each sensor, subscribe to its "humidity" attribute
  // When humidity changes, call our humidityEvent() function
  humiditySensors.each { sensor ->
    subscribe(sensor, "humidity", humidityEvent)
  }

  // Monitor the fan switch state (on/off)
  // This lets us track how long the fan has been running
  subscribe(fanSwitch, "switch", switchEvent)

  // Monitor the door sensor (if user configured one)
  // This lets us turn off fan when door is left open
  subscribe(doorSensor, "contact", contactEvent)

  // Monitor the humidity statistics device
  // The highHumMode setting determines which attribute we watch:
  // - "slowRollingAverage" or "fastRollingAverage" or "timeWeightedAverage"
  // When that statistic updates, call our childHumidityEvent() function
  subscribe(child, highHumMode, childHumidityEvent)
}

// =============================================================================
// CHILD DEVICE MANAGEMENT
// =============================================================================
// This app creates a "child device" - a virtual device that exists only to
// track humidity statistics. Think of it like a calculator that keeps running
// averages of humidity readings.
// =============================================================================

/**
 * Gets an existing humidity statistics device or creates a new one.
 *
 * WHAT IS A CHILD DEVICE:
 * A child device is a virtual device created and owned by this app. It's not
 * a physical device, but acts like one in Hubitat. This particular child device
 * calculates and stores three types of humidity averages:
 * - Slow Rolling Average: Average of many readings over long time
 * - Fast Rolling Average: Average of fewer readings over short time
 * - Time Weighted Average: Recent readings weighted more than older ones
 *
 * WHY WE NEED THIS:
 * To detect a humidity "spike", we need to know what's "normal". This device
 * maintains that baseline by tracking historical humidity levels.
 *
 * WHAT THIS DOES:
 * 1. Checks if the child device already exists (from previous app run)
 * 2. If it exists, returns the existing device
 * 3. If it doesn't exist, creates a new one
 * 4. If creation fails (driver not installed), logs an error
 *
 * @param childDNI - The unique Device Network ID for the child device
 * @return ChildDeviceWrapper - The humidity statistics device (or null if creation failed)
 *
 * TECHNICAL DETAILS:
 * - Uses the 'Humidity Statistics' driver (must be installed separately)
 * - The driver namespace is 'dwinks' (matches this app's namespace)
 * - Device is automatically labeled with the app's name for easy identification
 *
 * ERROR HANDLING:
 * If the Humidity Statistics driver isn't installed, this catches the error
 * and logs it instead of crashing the app.
 */
@CompileStatic
ChildDeviceWrapper getOrCreateChildDevices(String childDNI) {
  // Try to get the child device using its unique identifier
  // This returns null if the device doesn't exist yet
  DeviceWrapper device = getAppChildDevice(childDNI)

  // If device doesn't exist (this is first run, or device was deleted)
  if (device == null) {
    try {
      // Log that we're creating the device (helps with troubleshooting)
      logInfo("Creating child device for tracking humidity statistics")

      // Create the new child device with these parameters:
      // - 'dwinks': The namespace (must match the driver's namespace)
      // - 'Humidity Statistics': The driver name (must match exactly)
      // - childDNI: The unique identifier for this device
      // - [name: ..., label: ...]: Additional properties for the device
      //   - name: Internal name (what code uses)
      //   - label: Display name (what users see, includes app name for context)
      device = createAppChildDevice(
        'dwinks',                                        // Namespace
        'Humidity Statistics',                           // Driver type
        childDNI,                                        // Unique device ID
        [
          name: 'Humidity Statistics',                   // Internal name
          label: "${getAppLabel()}: Humidity Statistics"     // Display name
        ]
      )
    } catch (UnknownDeviceTypeException e) {
      // If the driver doesn't exist, log a helpful error message
      // This tells the user they need to install the Humidity Statistics driver
      logExceptionWithDetails('Humidity Statistics driver not found', e)
    }
  }

  // Return the device (either existing or newly created)
  // Could be null if creation failed
  return device as ChildDeviceWrapper
}

// =============================================================================
// HUMIDITY PROCESSING - THE CORE LOGIC
// =============================================================================
// This is where the "magic" happens. These functions monitor humidity changes
// and decide when to turn the fan on or off.
//
// THE TWO-STEP PROCESS:
// 1. humidityEvent(): Receives raw humidity readings from sensors
// 2. childHumidityEvent(): Receives calculated statistics and makes decisions
//
// WHY TWO STEPS:
// Step 1 just collects data. Step 2 analyzes the data against historical trends.
// This separation keeps the logic clean and maintainable.
// =============================================================================

/**
 * Handles humidity readings from bathroom sensors.
 *
 * WHEN THIS RUNS:
 * Every time a humidity sensor reports a new reading (typically every 1-5 minutes,
 * depending on the sensor). This could be dozens of times per hour.
 *
 * WHAT THIS DOES:
 * 1. Receives the humidity value from the sensor
 * 2. Validates it's a sensible number (between 0-100%)
 * 3. Stores it in app state for later reference
 * 4. Sends it to the statistics device for averaging
 *
 * WHY VALIDATE:
 * Sometimes sensors report error values like -1 or 999. We filter these out
 * to prevent them from affecting our averages.
 *
 * @param event - The Event object containing the humidity reading
 *                event.value contains the humidity percentage as a string
 *
 * TECHNICAL DETAILS:
 * - BigDecimal is used for precise decimal math (better than float/double)
 * - state.currentHumidity stores the most recent valid reading
 * - The child device receives the value via logHumidityEvent() method
 *
 * DATA FLOW:
 * Sensor → humidityEvent() → Validation → state.currentHumidity
 *                          ↓
 *              Child Device (calculates averages)
 *                          ↓
 *              childHumidityEvent() → Fan decision
 */
@CompileStatic
void humidityEvent(Event event) {
  // Log the received value for debugging purposes
  // This helps troubleshoot if sensors are reporting bad values
  logDebug("Received humidity event: ${event.value}")

  // Get or create the child device that tracks statistics
  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())

  // Convert the humidity value from String to BigDecimal for precise math
  // event.value is always a String, even though it contains a number
  // BigDecimal is better than Float or Double for this kind of calculation
  BigDecimal value = new BigDecimal(event.value)

  // Validate the humidity reading is sensible
  // Humidity must be between 0% and 100% (exclusive)
  // We reject 0 and 100 because they're often sensor errors
  if (value > 0 && value < 100) {
    // Store this as the current humidity reading
    // .toString() converts it back to String for storage in state
    // state is a Map that persists data between function calls
    setStateVar('currentHumidity', value.toString())

    // Send this reading to the child device for statistical tracking
    // The child device will update its rolling averages
    // This eventually triggers childHumidityEvent() when statistics update
    callDeviceMethod(child, 'logHumidityEvent', value)
  }
  // If value is invalid (<=0 or >=100), we silently ignore it
  // The previous valid value remains in state.currentHumidity
}

/**
 * Handles updated humidity statistics and decides whether to control the fan.
 *
 * THIS IS THE BRAIN OF THE APP - Where fan control decisions are made.
 *
 * WHEN THIS RUNS:
 * After the child device calculates new statistics from the raw humidity readings.
 * This happens shortly after humidityEvent() processes a new sensor reading.
 *
 * WHAT THIS DOES:
 * Implements a sophisticated algorithm to detect humidity "spikes":
 *
 * 1. Get three values:
 *    - trackedHumValue: Historical average (what's "normal")
 *    - currentHumidity: Right now (what we're experiencing)
 *    - lastHumidity: Previous reading (for trend analysis)
 *
 * 2. Determine if humidity is rising or falling:
 *    - Rising: Someone probably just started a shower
 *    - Falling: Shower is over, moisture is dissipating
 *
 * 3. Apply decision logic:
 *    FAN ON if:
 *      - Current humidity is RISING (vs last reading)
 *      - Current humidity is MORE than changeLimit above the average
 *      Example: Average is 45%, changeLimit is 2, current is 48%
 *               48 - 2 = 46, which is > 45, so turn ON
 *
 *    FAN OFF if:
 *      - Humidity has been FALLING for two readings in a row
 *      - This indicates the moisture source is gone
 *
 * @param event - Event from child device containing updated statistics
 *                event.value contains the calculated average (slow/fast/weighted)
 *
 * THE ALGORITHM EXPLAINED FOR NON-PROGRAMMERS:
 *
 * Imagine you're watching a thermometer:
 * - You know the room is normally 70°F (this is trackedHumValue)
 * - You see it's now 73°F (this is currentHumidity)
 * - You remember it was 72°F a minute ago (this is lastHumidity)
 *
 * Is it getting hotter? Yes (73 > 72), so isIncreasing = true
 * Is it MORE than 2° above normal? 73 - 70 = 3, which is > 2, so YES
 * Therefore: Turn on the fan!
 *
 * Later, you see:
 * - Normal is still 70°F
 * - Now it's 71°F (went down from 73)
 * - A minute ago it was 72°F
 *
 * Is it getting hotter? No (71 < 72), so isIncreasing = false
 * Was it decreasing before too? Yes (72 < 73), so decreasingSuccessively = true
 * Therefore: Turn off the fan!
 *
 * IMPORTANT STATE VARIABLES:
 * - state.currentHumidity: Most recent sensor reading
 * - state.lastHumidity: Previous reading (for trend detection)
 * - state.isIncreasing: "true" or "false" string indicating trend direction
 *
 * WHY USE STRINGS FOR BOOLEANS:
 * Hubitat's state storage converts booleans to strings, so we work with
 * strings directly to avoid conversion issues.
 */
@CompileStatic
void childHumidityEvent(Event event) {
  // Extract the three humidity values we need for decision making

  // 1. trackedHumValue: The historical average ("what's normal")
  //    This comes from the child device's statistical calculations
  BigDecimal trackedHumValue = new BigDecimal(event.value)

  // 2. currentHumidity: The most recent sensor reading ("what is it now")
  //    Retrieved from state where humidityEvent() stored it
  //    setScale(1, ROUND_HALF_UP) rounds to 1 decimal place (e.g., 45.7%)
  BigDecimal currentHumidity = new BigDecimal(getStateVar('currentHumidity') as String).setScale(1, BigDecimal.ROUND_HALF_UP)

  // 3. lastHumidity: The previous reading ("what was it before")
  //    Also from state, rounded to 1 decimal place for consistent comparison
  //    Default to 0 if this is the first reading (using ?: operator)
  BigDecimal lastHumidity = new BigDecimal((getStateVar('lastHumidity') ?: '0') as String).setScale(1, BigDecimal.ROUND_HALF_UP)

  // Log these values for debugging and troubleshooting
  logDebug("Tracked Humidity Value: ${trackedHumValue}")
  logDebug("Current Humidity Value: ${currentHumidity}")

  // Determine if humidity is rising or falling
  // Compare current reading to previous reading
  // Result is stored as "true" or "false" STRING (not boolean)
  String isIncreasing = currentHumidity > lastHumidity ? 'true' : 'false'

  // Check if humidity has been decreasing for multiple readings in a row
  // This happens when: previous reading was decreasing AND current reading is also decreasing
  // Why this matters: One decreasing reading might be a fluke, two in a row is a real trend
  String decreasingSuccessively = getStateVar('isIncreasing') == 'false' && isIncreasing == 'false' ? 'true' : 'false'

  // Store the current trend direction for next time
  // This becomes the "previous trend" in the next reading
  setStateVar('isIncreasing', isIncreasing)

  // Log the trend for debugging
  logDebug("Is Increasing: ${isIncreasing}")

  // ==========================================================================
  // MAIN DECISION LOGIC - Should we turn the fan ON or OFF?
  // ==========================================================================

  // CONDITION FOR TURNING FAN ON:
  // Both of these must be true:
  // 1. Humidity is RISING (isIncreasing == 'true')
  // 2. Current humidity is more than changeLimit above the historical average
  //
  // Example with changeLimit = 2:
  //   If trackedHumValue = 45% and currentHumidity = 48%
  //   Then: 48 - 2 = 46, and 46 > 45, so turn ON
  //
  // Why subtract changeLimit from current?
  //   We're asking: "Is current humidity high enough that even after
  //   accounting for normal variation (changeLimit), it's still above average?"
  BigDecimal changeLimitValue = getSetting('changeLimit') as BigDecimal
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper

  if ((currentHumidity - changeLimitValue) > trackedHumValue && isIncreasing == 'true') {
    // Turn on the fan
    callDeviceMethod(fanSwitchDevice, 'on')

    // Log why we turned it on (helpful for understanding behavior)
    logDebug("Last Humidity Reading(${lastHumidity}) was great enough to trigger a fan event.")
    logDebug("Current Humidity(${currentHumidity}) was outside change limit(${changeLimitValue}) of Last Humidity Reading(${lastHumidity})")
  } else {
    // Fan stays off (or is turned off) because conditions aren't met

    // However, check if we should explicitly turn OFF due to decreasing trend
    // This handles the case where fan is already on but humidity is dropping
    if (decreasingSuccessively == 'true') {
      // Humidity has been decreasing for 2+ readings, time to turn off
      logDebug("Current Humidity(${currentHumidity}) has been decreasing successively. Turning off fan.")
      callDeviceMethod(fanSwitchDevice, 'off')
    }

    // Log why we didn't turn on (or turned off)
    logDebug("Last Humidity Reading(${lastHumidity}) was not great enough to trigger a fan event.")
    logDebug("Current Humidity(${currentHumidity}) was within change limit(${changeLimitValue}) of Last Humidity Reading(${lastHumidity})")
  }

  // Store current reading as "last reading" for next comparison
  // This becomes lastHumidity in the next call to this function
  setStateVar('lastHumidity', currentHumidity.toString())
}

// =============================================================================
// FAN & DOOR EVENT HANDLING - SAFETY FEATURES
// =============================================================================
// These functions implement safety features to prevent the fan from running
// excessively or unnecessarily:
// 1. Maximum runtime limit (prevents fan from running all day)
// 2. Door open detection (no need for fan if door is open)
//
// These work independently of the humidity-based control to provide multiple
// layers of protection against fan over-use.
// =============================================================================

/**
 * Handles fan switch state changes (on/off events).
 *
 * WHEN THIS RUNS:
 * Every time the fan switch changes state, whether:
 * - This app turned it on/off
 * - User manually turned it on/off
 * - Another app/automation turned it on/off
 *
 * WHAT THIS DOES:
 * Implements a "maximum runtime" safety feature:
 *
 * When fan turns ON:
 * - Records the current time
 * - Schedules an auto-shutoff after maxRuntime minutes
 * - This prevents the fan from running indefinitely if humidity never drops
 *
 * When fan turns OFF:
 * - Clears the recorded "on" time
 * - This cleanup prevents stale timing data
 *
 * @param event - Event object containing switch state ("on" or "off")
 *
 * WHY THIS MATTERS:
 * Without this safety feature, if humidity stays elevated (maybe due to weather,
 * a stuck sensor, or persistent moisture), the fan would run continuously,
 * wasting energy and potentially damaging the fan motor.
 *
 * TECHNICAL DETAILS:
 * - state.fanOnSince: Timestamp (in milliseconds) when fan turned on
 * - runIn(): Hubitat function that schedules a method to run after X seconds
 * - now(): Returns current time in milliseconds since epoch
 * - maxRuntime: User configuration value in minutes
 * - app.getState().remove(): Deletes a state variable (cleanup)
 *
 * EXAMPLE:
 * User sets maxRuntime = 60 minutes
 * Fan turns on at 2:00 PM
 * At 3:00 PM, runtimeExceeded() is called and fan turns off
 * Even if humidity is still high, fan won't run longer than 60 minutes
 */
@CompileStatic
void switchEvent(Event event) {
  // Log the switch state change for debugging
  logDebug("Received switch event: ${event.value}")

  // Check what happened to the switch
  if (event.value == "off") {
    // FAN TURNED OFF
    // Remove the "fan on since" timestamp from state
    // This cleanup prevents stale data and prepares for next "on" event
    removeStateVar('fanOnSince')
  } else if (event.value == "on") {
    // FAN TURNED ON

    // Record the current time (in milliseconds) when fan turned on
    // now() returns milliseconds since January 1, 1970 (Unix epoch)
    // We store this so we can calculate how long the fan has been running
    setStateVar('fanOnSince', getCurrentTime())

    // Schedule automatic shutoff if user configured a maximum runtime
    // Check if maxRuntime > 0 (user can set 0 to disable this feature)
    Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
    if (maxRuntimeValue > 0) {
      // Log the scheduled shutoff for debugging
      logDebug("Scheduling fan shutoff for ${maxRuntimeValue} minutes")

      // Schedule the runtimeExceeded() function to run after maxRuntime
      // runIn() takes seconds, so multiply minutes by 60
      // "runtimeExceeded" is the name of the function to call (as a String)
      scheduleIn(maxRuntimeValue * 60, "runtimeExceeded")
    }
    // If maxRuntime is 0, we skip scheduling and fan can run indefinitely
    // until humidity-based logic or door sensor turns it off
  }
}

/**
 * Handles door sensor open/close events.
 *
 * WHEN THIS RUNS:
 * Every time the door sensor changes state (door opens or closes).
 *
 * WHY THIS FEATURE EXISTS:
 * When the bathroom door is open, air exchanges naturally with the rest of
 * the house, so running the fan is unnecessary and wastes energy. This
 * feature automatically turns off the fan if the door stays open too long.
 *
 * WHAT THIS DOES:
 *
 * When door OPENS:
 * - Checks if fan is currently running
 * - If yes, schedules auto-shutoff after doorOpenTime minutes
 * - This gives time for brief door openings (like someone checking on kids)
 *   but turns off fan if door stays open (bathroom not in use)
 *
 * When door CLOSES:
 * - Cancels any pending auto-shutoff
 * - Allows normal humidity-based control to resume
 *
 * @param event - Event object containing contact state ("open" or "closed")
 *
 * THE LOGIC EXPLAINED:
 * Scenario 1: Door opens briefly (30 seconds)
 *   - Door opens → auto-shutoff scheduled for 10 minutes
 *   - Door closes after 30 seconds → scheduled shutoff cancelled
 *   - Fan keeps running, controlled by humidity
 *
 * Scenario 2: Door stays open (bathroom not in use)
 *   - Door opens → auto-shutoff scheduled for 10 minutes
 *   - 10 minutes pass → doorOpenedAutoOff() is called
 *   - Fan turns off (no point running fan with door open)
 *
 * CONFIGURATION:
 * - doorOpenTime: User setting (default 10 minutes)
 * - Set to 0 to disable this feature
 *
 * TECHNICAL DETAILS:
 * - fanSwitch.currentValue("switch"): Gets current state of fan ("on" or "off")
 * - runIn(): Schedules a function to run after specified seconds
 * - unschedule("doorOpenedAutoOff"): Cancels any pending scheduled call to that function
 */
@CompileStatic
void contactEvent(Event event) {
  // Log the door state change for debugging
  logDebug("Received contact event: ${event.value}")

  // Check what happened to the door
  if (event.value == "open") {
    // DOOR OPENED

    // Only take action if:
    // 1. User configured a door open timeout (doorOpenTime > 0)
    // 2. Fan is currently running
    Integer doorOpenTimeValue = getSetting('doorOpenTime') as Integer
    DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
    String fanState = fanSwitchDevice.currentValue("switch") as String

    if (doorOpenTimeValue > 0 && fanState == "on") {
      // Schedule auto-shutoff after doorOpenTime minutes
      // This gives a grace period for brief door openings
      logDebug("Scheduling fan shutoff for ${doorOpenTimeValue} minutes")

      // runIn() takes seconds, so multiply minutes by 60
      scheduleIn(doorOpenTimeValue * 60, "doorOpenedAutoOff")
    }
    // If doorOpenTime is 0, this feature is disabled
    // If fan is already off, no need to schedule shutoff
  }
  else if (event.value == "closed") {
    // DOOR CLOSED

    // Cancel any pending auto-shutoff that was scheduled when door opened
    // unschedule() removes the scheduled function call if it hasn't run yet
    // If it already ran, this does nothing (safe to call)
    unscheduleMethod("doorOpenedAutoOff")

    // Now that door is closed, humidity-based control resumes normally
  }
}

/**
 * Auto-shutoff function called when door has been open too long.
 *
 * WHEN THIS RUNS:
 * After the door has been open for doorOpenTime minutes (if fan is running).
 * Scheduled by contactEvent() when door opens.
 *
 * WHAT THIS DOES:
 * Simply turns off the fan and logs why.
 *
 * WHY SEPARATE FUNCTION:
 * runIn() requires a function name to call. This small function provides
 * a clear, named action that appears in logs.
 *
 * LOG MESSAGE:
 * The log message includes the fan's display name and the timeout duration,
 * making it clear in logs why the fan turned off.
 */
@CompileStatic
void doorOpenedAutoOff() {
  // Log the reason for shutoff (helpful for troubleshooting)
  // fanSwitch.displayName is the human-readable name of the fan device
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer doorOpenTimeValue = getSetting('doorOpenTime') as Integer
  String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String

  logInfo("Auto-off: ${displayName} has been on with door open for ${doorOpenTimeValue} minutes")

  // Turn off the fan
  callDeviceMethod(fanSwitchDevice, 'off')
}

/**
 * Auto-shutoff function called when maximum runtime is exceeded.
 *
 * WHEN THIS RUNS:
 * After the fan has been running for maxRuntime minutes.
 * Scheduled by switchEvent() when fan turns on.
 *
 * WHAT THIS DOES:
 * Turns off the fan regardless of humidity levels.
 *
 * WHY THIS EXISTS:
 * Safety feature to prevent fan from running indefinitely if:
 * - Humidity sensor fails
 * - Humidity stays elevated due to weather
 * - Algorithm has a bug
 * - Any other unforeseen situation
 *
 * This ensures fan can't run for hours/days unintentionally.
 *
 * LOG MESSAGE:
 * Clearly states why the fan turned off (exceeded max runtime).
 */
@CompileStatic
void runtimeExceeded() {
  // Log the reason for shutoff with specific timing information
  // This helps users understand what happened if they see the fan turned off
  DeviceWrapper fanSwitchDevice = getSetting('fanSwitch') as DeviceWrapper
  Integer maxRuntimeValue = getSetting('maxRuntime') as Integer
  String displayName = getDeviceProperty(fanSwitchDevice, 'displayName') as String

  logInfo("Auto-off: ${displayName} has been on for ${maxRuntimeValue} minutes")

  // Turn off the fan
  // This overrides any other control logic (humidity-based, manual, etc.)
  callDeviceMethod(fanSwitchDevice, 'off')

  // Note: The switchEvent() function will automatically clean up state.fanOnSince
  // when it receives the "off" event, so we don't need to do that here
}