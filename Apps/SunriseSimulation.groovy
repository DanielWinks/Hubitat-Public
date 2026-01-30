// =============================================================================
// SUNRISE SIMULATION APP
// =============================================================================
// Author: Daniel Winks
// Description: Creates a gradual sunrise effect using RGB/RGBW bulbs to help
//              with gentle wake-up. The animation progresses through three
//              stages over a configurable duration:
//              1. Deep red to orange (10 steps)
//              2. Orange to soft white (40 steps, desaturation)
//              3. Warm white to full brightness (80 steps, CT mode)
//
// Features:
//   - Scheduled daily trigger at user-specified time
//   - Snooze functionality to delay sunrise
//   - Disable switches to prevent sunrise from running
//   - Presence sensors to skip when nobody is home
//   - Configurable duration (10-60 minutes)
//   - Test button to manually trigger sunrise
// =============================================================================

// Include the utilities and logging library from the Libraries folder
#include dwinks.UtilitiesAndLoggingLibrary

// Import required Hubitat classes for device interaction and events
import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event

/**
 * definition() - Defines app metadata for Hubitat
 * This block provides information about the app that appears in the
 * Hubitat web interface when users browse available apps.
 */
definition(
  name: 'Sunrise Simulation',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Slowly raise RGBW bulbs for gentle wakeup.',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

/**
 * preferences - Defines the app's configuration page
 * This tells Hubitat to use the mainPage() method to build the settings UI
 */
preferences { page(name: 'mainPage', title: 'Sunrise Simulation') }

/**
 * mainPage() - Builds the configuration UI for the app
 * This method constructs a dynamic settings page with sections for:
 * - Schedule configuration (when to run sunrise)
 * - Device selection (bulbs, control switches, presence sensors)
 * - Animation settings (duration, snooze time)
 * - Logging and testing controls
 *
 * @return Map - A dynamicPage configuration that Hubitat renders as HTML
 */
Map mainPage() {
  return dynamicPage(name: 'mainPage', title: '<h1>Sunrise Simulation</h1>', install: true, uninstall: true, refreshInterval: 0) {
    // =========================================================================
    // SCHEDULE SECTION
    // =========================================================================
    // Allows user to select what time the sunrise should run each day
    section('<h2>Schedule</h2>') {
      input(
        'sunriseTime',           // Setting name stored in settings.sunriseTime
        'time',                  // Input type: time picker
        title: 'Time to start sunrise simulation',
        required: true           // User must configure this
      )
    }

    // =========================================================================
    // DEVICES SECTION
    // =========================================================================
    // Allows user to select bulbs and control switches
    section('<h2>Devices</h2>') {
      // RGB capable bulbs - these will have the sunrise animation applied
      input(
        'rgbwBulbs',                    // Setting name
        'capability.colorControl',       // Device type filter
        title: 'RGB capable bulbs',
        required: false,                 // Optional, but sunrise won't work without bulbs
        multiple: true                   // User can select multiple bulbs
      )

      // Disable switches - turning any of these on will prevent sunrise from running
      input(
        'disableSwitches',
        'capability.switch',
        title: 'Disable Sunrise with switches',
        required: false,
        multiple: true
      )

      // Snooze switches - turning these on stops current sunrise and reschedules
      input(
        'snoozeSwitches',
        'capability.switch',
        title: 'Snooze Sunrise with switches',
        required: false,
        multiple: true
      )

      // Presence sensors - sunrise only runs if at least one shows "present"
      input(
        'requiredPresence',
        'capability.presenceSensor',
        title: 'Required presence for Sunrise',
        required: false,
        multiple: true
      )
    }

    // =========================================================================
    // SUNRISE SETTINGS SECTION
    // =========================================================================
    // Configure timing parameters for the animation
    section('<h2>Sunrise Settings</h2>') {
      // Total duration for the sunrise animation (divided into 3 equal stages)
      input(
        'sunriseDuration',
        'number',
        title: 'Duration of minutes to brighten lights',
        range: '10..60',           // Must be between 10 and 60 minutes
        required: true,
        defaultValue: 30           // Default to 30 minutes
      )

      // How long to wait after snooze before starting a new sunrise
      input(
        'snoozeDuration',
        'number',
        title: 'Duration of minutes to snooze',
        range: '10..60',
        required: true,
        defaultValue: 10           // Default to 10 minute snooze
      )
    }

    // =========================================================================
    // LOGGING AND TESTING SECTION
    // =========================================================================
    // Configure logging verbosity and provide testing buttons
    section('<h2>Logging</h2>') {
      // Enable/disable general informational logging
      input(
        'logEnable',
        'bool',
        title: 'Enable Logging',
        required: false,
        defaultValue: true
      )

      // Enable/disable detailed debug logging (verbose)
      input(
        'debugLogEnable',
        'bool',
        title: 'Enable debug logging',
        required: false,
        defaultValue: false
      )

      // Enable/disable description text in logs
      input(
        'descriptionTextEnable',
        'bool',
        title: 'Enable descriptionText logging',
        required: false,
        defaultValue: true
      )

      // Button to reinitialize the app (reschedule, resubscribe, etc.)
      input(
        name: 'initializeBtn',
        type: 'button',
        title: 'Initialize',
        backgroundColor: 'Crimson',
        textColor: 'white',
        submitOnChange: true
      )

      // Button to manually trigger a test sunrise immediately
      input(
        name: 'testBtn',
        type: 'button',
        title: 'Test Sunrise',
        backgroundColor: 'Crimson',
        textColor: 'white',
        submitOnChange: true
      )
    }

    // =========================================================================
    // APP LABEL SECTION
    // =========================================================================
    // Allows user to give this app instance a custom name
    section() {
      label(
        title: 'Enter a name for this app instance',
        required: false
      )
    }
  }
}

// =============================================================================
// Lifecycle & Configuration
// =============================================================================

/**
 * configure() - Called when the app configuration changes
 * This method clears all existing subscriptions and reinitializes the app
 * to ensure the new settings take effect properly.
 */
void configure() {
  unsubscribe() // Remove all existing event subscriptions
  initialize()  // Reinitialize the app with new settings
}

/**
 * initialize() - Sets up the app from scratch
 * This is the main initialization method that:
 * 1. Clears all scheduled tasks
 * 2. Resets all state variables
 * 3. Calculates animation timing intervals
 * 4. Subscribes to device events
 * 5. Schedules the daily sunrise trigger
 */
void initialize() {
  unschedule()                // Cancel all pending scheduled tasks
  clearAllStates()            // Clear all state variables to start fresh
  calculateStageDurations()   // Calculate how long each animation stage should take
  subscribeEventHandlers()    // Set up event listeners for switches
  scheduleSunrise()           // Schedule the sunrise to run at configured time
}

/**
 * subscribeEventHandlers() - Sets up event listeners for user control switches
 * This subscribes to switch events so the app can respond when users interact with:
 * - Disable switches: Immediately stop the sunrise animation
 * - Snooze switches: Temporarily pause and reschedule the sunrise
 */
private void subscribeEventHandlers() {
  // Listen for disable switch events (on/off changes)
  subscribe(disableSwitches, 'switch', 'disableEvent')

  // Listen for snooze switch events (on/off changes)
  subscribe(snoozeSwitches, 'switch', 'snoozeSwitchEvent')
}

/**
 * calculateStageDurations() - Calculates timing intervals for each animation stage
 * The sunrise animation is divided into 3 equal-duration stages:
 * - Stage 1: Deep red to orange (10 steps)
 * - Stage 2: Orange to soft white (40 steps)
 * - Stage 3: Soft white to full brightness (80 steps)
 * This method calculates how many seconds between each step to achieve the user's
 * desired total duration.
 */
private void calculateStageDurations() {
  // Convert user's desired minutes into total seconds for the entire sunrise
  Integer totalSecs = (60 * (settings.sunriseDuration as Integer))

  // Divide the total time into 3 equal stages
  Integer stageSecs = (totalSecs / 3)
  state.stageSecs = stageSecs

  // Calculate interval for stage 1 (10 steps across 1/3 of total time)
  // Minimum interval is 1 second to avoid overwhelming the hub
  state.stage1interval = (Math.round(stageSecs / 10) > 1) ? Math.round(stageSecs / 10) : 1

  // Calculate interval for stage 2 (40 steps across 1/3 of total time)
  // More steps means shorter intervals for smoother transition
  state.stage2interval = (Math.round(stageSecs / 40) > 1) ? Math.round(stageSecs / 40) : 1

  // Calculate interval for stage 3 (80 steps across 1/3 of total time)
  // Finest granularity for the final brightening phase
  state.stage3interval = (Math.round(stageSecs / 80) > 1) ? Math.round(stageSecs / 80) : 1
}

/**
 * scheduleSunrise() - Schedules the sunrise to run daily at the configured time
 * This uses Hubitat's built-in schedule() function to trigger sunriseStart()
 * automatically every day at the time the user specified in settings.
 */
private void scheduleSunrise() {
  // Check if user has configured a sunrise time
  if (!settings.sunriseTime) {
    logWarn('No sunrise time configured')
    return
  }

  // Schedule sunriseStart() to run daily at the configured time
  schedule(settings.sunriseTime, 'sunriseStart')
  logInfo("Sunrise scheduled for ${settings.sunriseTime}")
}

// =============================================================================
// UI Button Handlers
// =============================================================================

/**
 * appButtonHandler() - Responds to button clicks in the app's settings page
 * This method is automatically called by Hubitat when a user clicks a button
 * defined in the mainPage() preferences section.
 *
 * @param buttonId - The unique ID of the button that was clicked
 */
void appButtonHandler(String buttonId) {
  switch (buttonId) {
    case 'initializeBtn':
      // User clicked "Initialize" - reconfigure and restart the app
      initialize()
      break
    case 'testBtn':
      // User clicked "Test Sunrise" - run the sunrise immediately for testing
      sunriseStart()
      break
  }
}

// =============================================================================
// Event Handlers
// =============================================================================

/**
 * snoozeSwitchEvent() - Handles snooze switch state changes
 * When the snooze switch is turned ON:
 *   - Immediately stops the current sunrise animation
 *   - Schedules the switch to turn off after the snooze duration
 *   - When the switch auto-turns off, it triggers a new sunrise
 * When the snooze switch is turned OFF manually:
 *   - Immediately starts a new sunrise (canceling the snooze)
 *
 * @param event - The Event object containing switch state and device info
 */
void snoozeSwitchEvent(Event event) {
  logDebug("Received snooze switch event: ${event.value}")

  // Check if the switch was turned ON
  if ('on' == "${event.value}") {
    logDebug('Snooze switch turned on')

    // Stop the current sunrise animation and turn off bulbs
    abortSunrise()

    // Convert snooze duration from minutes to seconds
    Integer snoozeDurationSecs = (60 * (settings.snoozeDuration as Integer))

    // Schedule the snooze switch to turn off after the snooze duration
    // This will trigger another sunrise when it turns off
    runIn(snoozeDurationSecs, 'snoozeOffHandler')
  }

  // Check if the switch was turned OFF
  if ('off' == "${event.value}") {
    // User manually ended the snooze - start sunrise immediately
    sunriseStart()
  }
}

/**
 * disableEvent() - Handles disable switch state changes
 * When any disable switch is turned ON:
 *   - Immediately stops the sunrise animation
 *   - Turns off all bulbs
 *   - Prevents future sunrises from starting until switch is turned off
 *
 * @param event - The Event object containing switch state and device info
 */
void disableEvent(Event event) {
  logDebug("Received disable event: ${event.value}")

  // Check if the switch was turned ON
  if ('on' == "${event.value}") {
    // Stop the sunrise animation immediately
    abortSunrise()
  }
}

// =============================================================================
// Snooze & Disable Controls
// =============================================================================

/**
 * snoozeOffHandler() - Turns off all snooze switches after snooze period ends
 * This method is called automatically after the snooze duration expires.
 * Turning off the snooze switches will trigger snoozeSwitchEvent(), which
 * will then start a new sunrise animation.
 */
private void snoozeOffHandler() {
  // Iterate through all configured snooze switches and turn them off
  settings.snoozeSwitches?.each { DeviceWrapper ss ->
    ss.off()
  }
}

/**
 * abortSunrise() - Immediately stops the sunrise animation and turns off bulbs
 * This method performs a complete shutdown of the sunrise sequence:
 * 1. Cancels any scheduled brightenRGBWBulbs() calls
 * 2. Clears all animation state variables
 * 3. Turns off all configured bulbs
 * Used when user hits snooze, disable, or when the animation needs to stop
 */
private void abortSunrise() {
  logInfo('Aborting sunrise and turning off all bulbs')

  // Cancel any pending scheduled calls to brightenRGBWBulbs()
  // This stops the animation loop from continuing
  unschedule('brightenRGBWBulbs')

  // Clear all state variables (animation progress, color values, etc.)
  clearAllStates()

  // Turn off all the bulbs immediately
  turnOffAllBulbs()
}

/**
 * turnOffAllBulbs() - Sends the off() command to all configured RGB bulbs
 * Iterates through the user's selected bulbs and turns each one off.
 */
private void turnOffAllBulbs() {
  // Use safe navigation operator (?.) in case no bulbs are configured
  settings.rgbwBulbs?.each { DeviceWrapper bulb ->
    bulb.off()
  }
}

// =============================================================================
// Sunrise Sequencing
// =============================================================================

/**
 * sunriseStart() - Main entry point for starting the sunrise animation
 * This is the primary method called when the sunrise should begin, whether
 * triggered by:
 * - The scheduled daily time
 * - The "Test Sunrise" button
 * - A snooze switch turning off
 *
 * Before starting the animation, this method checks:
 * 1. Required presence sensors are present (if configured)
 * 2. No disable switches are on
 * 3. No snooze switches are on
 *
 * If all checks pass, it recalculates timing and starts the animation.
 */
void sunriseStart() {
  logInfo('Starting sunrise simulation')

  // Check if presence is required and if so, verify someone is home
  if (!requiredPresencePresent()) {
    logInfo('Skipping sunrise because required presence sensors are not present')
    return
  }

  // Check if sunrise is disabled or snoozed by user switches
  if (sunriseDisabled()) {
    logInfo('Skipping sunrise because disable or snooze switch is on')
    return
  }

  // Recalculate stage durations in case user changed settings
  calculateStageDurations()

  // Initialize the animation state and start the first iteration
  brightenRGBWBulbsStart()
}

/**
 * brightenRGBWBulbsStart() - Initializes state variables and begins animation
 * This method sets up all the initial values for the 3-stage color animation:
 * - Stage 1: Deep red (hue 0) at low brightness
 * - Stage 2: Transition through orange to soft white
 * - Stage 3: Increase color temperature and brightness to full
 *
 * After initialization, it kicks off the animation loop by calling
 * brightenRGBWBulbs() for the first time.
 */
private void brightenRGBWBulbsStart() {
  // === Initialize Stage 1 starting values ===
  // Start with deep red color (hue 0 on HSV color wheel)
  state.rgbwHue = 0

  // Start at full saturation (100%) for vibrant red color
  state.rgbwSaturation = 100

  // Start at very low brightness (1%) - barely visible
  state.rgbwLevel = 1

  // Set initial color temperature for stage 3 (warm white at 2800K)
  state.rgbwCT = 2800

  // Set animation to stage 1 (deep red to orange phase)
  state.rgbwStage = 1

  // Build the initial color map that will be sent to bulbs
  // This map contains HSV (Hue, Saturation, Value/Level) values
  state.rgbwColorMap = [
    hue       : state.rgbwHue,
    saturation: state.rgbwSaturation,
    level     : state.rgbwLevel
  ]

  // Log the starting parameters for debugging
  logDebug("Starting RGBW animation - ColorMap: ${state.rgbwColorMap}, CT: ${state.rgbwCT}")

  // Check if animation is already running (prevents duplicate animations)
  if (!state.brightenRGBWBulbsRunning) {
    // Start the animation loop
    brightenRGBWBulbs()
  }
}

/**
 * brightenRGBWBulbs() - The main animation loop that updates bulb colors over time
 * This method is called repeatedly to create a smooth sunrise effect through 3 stages:
 *
 * STAGE 1 (Deep Red → Orange):
 *   - Duration: 1/3 of total time, 10 steps
 *   - Hue: 0 → 10 (deep red to orange on HSV color wheel)
 *   - Saturation: 100% (stays fully saturated)
 *   - Brightness: 1% → 10% (very dim start)
 *   - Effect: A deep red glow that slowly brightens and shifts to orange
 *
 * STAGE 2 (Orange → Soft White):
 *   - Duration: 1/3 of total time, 40 steps
 *   - Hue: 10 (stays at orange)
 *   - Saturation: 100% → 60% (gradually desaturates)
 *   - Brightness: 10% → 20% (continues brightening slowly)
 *   - Effect: Orange color fades to a warm, soft white glow
 *
 * STAGE 3 (Soft White → Full Brightness):
 *   - Duration: 1/3 of total time, 80 steps
 *   - Color Temp: 2800K → ~5800K (warm to neutral white)
 *   - Brightness: 20% → 100% (ramps up to full brightness)
 *   - Effect: Smooth transition from warm morning light to full daylight
 *
 * The method schedules itself to run again using runIn() until the animation
 * completes (brightness reaches 100%). This creates a self-perpetuating loop.
 */
private void brightenRGBWBulbs() {
  // Mark that the animation is currently running (prevents duplicates)
  state.brightenRGBWBulbsRunning = 'true'

  // Check if user has disabled or snoozed the sunrise
  if (sunriseDisabled()) {
    logInfo('Exiting brightenRGBWBulbs() because sunrise is disabled or snoozed')
    state.remove('brightenRGBWBulbsRunning') // Clear the running flag
    return
  }

  // ========================================================================
  // STAGE 1: Deep Red to Orange Transition
  // ========================================================================
  // This stage creates the initial sunrise glow, transitioning from deep red
  // to orange while slowly increasing brightness. It mimics the very early
  // sunrise when the sun is below the horizon.
  if (state.rgbwStage == 1) {
    // Apply current color to all configured bulbs
    settings.rgbwBulbs?.each { DeviceWrapper bulb ->
      bulb.setColor(state.rgbwColorMap)
    }

    // Increment hue by 1 (shifts color from red toward orange)
    state.rgbwHue += 1

    // Increment brightness by 1% (gradually brightens the bulbs)
    state.rgbwLevel += 1

    // Build updated color map with new values, rounding for clean integer values
    state.rgbwColorMap = [
      hue       : Math.round(state.rgbwHue),
      saturation: Math.round(state.rgbwSaturation),
      level     : Math.round(state.rgbwLevel)
    ]
    logDebug("Stage 1 (Deep Red → Orange) - ColorMap: ${state.rgbwColorMap}")

    // Check if we've reached the end of stage 1 (hue 10 = orange)
    if (state.rgbwColorMap.hue < 10) {
      // Continue stage 1 - schedule next iteration
      runIn((state.stage1interval as Integer), 'brightenRGBWBulbs')
    } else {
      // Stage 1 complete - set exact ending values and move to stage 2
      state.rgbwColorMap = [hue: 10, saturation: 100, level: 10]
      state.rgbwStage = 2
      runIn((state.stage1interval as Integer), 'brightenRGBWBulbs')
    }
  }

  // ========================================================================
  // STAGE 2: Desaturate to Soft White
  // ========================================================================
  // This stage maintains the orange hue but gradually removes saturation,
  // creating a soft, warm white glow. This mimics the sunrise transitioning
  // from colored light to white light as the sun rises higher.
  if (state.rgbwStage == 2) {
    // Apply current color to all configured bulbs
    settings.rgbwBulbs?.each { DeviceWrapper bulb ->
      bulb.setColor(state.rgbwColorMap)
    }

    // Decrease saturation by 1% (removes color, adds white)
    state.rgbwSaturation -= 1

    // Increase brightness by 0.25% (slower increase than stage 1)
    state.rgbwLevel += 0.25

    // Build updated color map with new values
    state.rgbwColorMap = [
      hue       : Math.round(state.rgbwHue),
      saturation: Math.round(state.rgbwSaturation),
      level     : Math.round(state.rgbwLevel)
    ]
    logDebug("Stage 2 (Desaturate to Soft White) - ColorMap: ${state.rgbwColorMap}")

    // Check if we've reached the end of stage 2 (60% saturation)
    if (state.rgbwColorMap.saturation > 60) {
      // Continue stage 2 - schedule next iteration
      runIn((state.stage2interval as Integer), 'brightenRGBWBulbs')
    } else {
      // Stage 2 complete - set exact ending values and move to stage 3
      state.rgbwColorMap = [hue: 10, saturation: 60, level: 20]
      state.rgbwLevel = 20
      state.rgbwStage = 3
      runIn((state.stage2interval as Integer), 'brightenRGBWBulbs')
    }
  }

  // ========================================================================
  // STAGE 3: Increase Color Temperature and Brightness
  // ========================================================================
  // This final stage uses color temperature (CT) mode instead of HSV color mode.
  // It increases both the color temperature (from warm 2800K to cooler ~5800K)
  // and brightness (from 20% to 100%), creating a full daylight effect.
  if (state.rgbwStage == 3) {
    // Apply current color temperature and brightness to all bulbs
    // setColorTemperature(colorTemp, level, transitionTime)
    settings.rgbwBulbs?.each { DeviceWrapper bulb ->
      bulb.setColorTemperature((state.rgbwCT as Integer), (state.rgbwLevel as Integer), 0)
    }

    // Increase color temperature by 40K (makes light cooler/more neutral)
    state.rgbwCT += 40

    // Increase brightness by 1%
    state.rgbwLevel += 1

    logDebug("Stage 3 (Brighten to Full) - CT: ${state.rgbwCT}, Level: ${state.rgbwLevel}")

    // Check if we've reached full brightness (100%)
    if (state.rgbwLevel > 100) {
      // Animation complete!
      logInfo('Sunrise simulation complete')
      state.remove('brightenRGBWBulbsRunning') // Clear the running flag
    } else {
      // Continue stage 3 - schedule next iteration
      runIn((state.stage3interval as Integer), 'brightenRGBWBulbs')
    }
  }
}

// =============================================================================
// Helper Methods
// =============================================================================

/**
 * sunriseDisabled() - Checks if the sunrise should be prevented from running
 * This method checks the state of user-configured control switches to determine
 * if the sunrise animation should be blocked. Returns true if EITHER:
 * 1. Any disable switch is currently ON, OR
 * 2. Any snooze switch is currently ON
 *
 * @return boolean - true if sunrise should be disabled, false if it can run
 */
private boolean sunriseDisabled() {
  // Check if any disable switches are on
  // The any() method returns true if at least one switch is 'on'
  boolean disabled = settings.disableSwitches?.any { DeviceWrapper sw ->
    (sw.currentValue('switch') == 'on')
  }

  // Check if any snooze switches are on
  boolean snoozed = settings.snoozeSwitches?.any { DeviceWrapper sw ->
    (sw.currentValue('switch') == 'on')
  }

  // Return true if either disabled or snoozed (OR logic)
  return (disabled || snoozed)
}

/**
 * requiredPresencePresent() - Checks if required presence conditions are met
 * If the user has configured required presence sensors, this method verifies
 * that at least one of them shows "present". If no presence sensors are
 * configured, this returns true (no presence requirement).
 *
 * This allows users to prevent sunrise from running when nobody is home.
 *
 * @return boolean - true if presence requirement is satisfied, false otherwise
 */
private boolean requiredPresencePresent() {
  // If no presence sensors configured, return true (no requirement)
  // Otherwise, check if at least one sensor shows 'present'
  return settings.requiredPresence?.any { DeviceWrapper presence ->
    (presence.currentValue('presence') == 'present')
  }
}
