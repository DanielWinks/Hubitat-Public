// =============================================================================
// SUNRISE SIMULATION APP
// =============================================================================
// MIT License
// Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
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

// =============================================================================
// IMPORTS
// =============================================================================

import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event
import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static final Integer DEFAULT_SUNRISE_DURATION = 30
@Field static final Integer DEFAULT_SNOOZE_DURATION = 10
@Field static final Integer MIN_DURATION = 10
@Field static final Integer MAX_DURATION = 60
@Field static final Integer CT_STEP_KELVIN = 40
@Field static final Integer START_CT = 2800
@Field static final Integer END_CT = 5800
@Field static final Integer DEFAULT_STAGE1_HUE_START = 0
@Field static final Integer DEFAULT_STAGE1_HUE_END = 10
@Field static final Integer DEFAULT_STAGE1_SATURATION_START = 100
@Field static final Integer DEFAULT_STAGE1_SATURATION_END = 100
@Field static final Integer DEFAULT_STAGE1_LEVEL_START = 1
@Field static final Integer DEFAULT_STAGE1_LEVEL_END = 10
@Field static final Integer DEFAULT_STAGE2_HUE_START = 10
@Field static final Integer DEFAULT_STAGE2_HUE_END = 10
@Field static final Integer DEFAULT_STAGE2_SATURATION_START = 100
@Field static final Integer DEFAULT_STAGE2_SATURATION_END = 60
@Field static final Integer DEFAULT_STAGE2_LEVEL_START = 10
@Field static final Integer DEFAULT_STAGE2_LEVEL_END = 20
@Field static final Integer DEFAULT_STAGE3_LEVEL_START = 20
@Field static final Integer DEFAULT_STAGE3_LEVEL_END = 100

// =============================================================================
// LOGGING
// =============================================================================

void logError(String message) {
  if (loggingEnabled('error')) {
    if (device) { log.error("${device.label ?: device.name}: ${message}") }
    if (app) { log.error("${app.label ?: app.name}: ${message}") }
  }
}

void logWarn(String message) {
  if (loggingEnabled('warn')) {
    if (device) { log.warn("${device.label ?: device.name}: ${message}") }
    if (app) { log.warn("${app.label ?: app.name}: ${message}") }
  }
}

void logInfo(String message) {
  if (loggingEnabled('info')) {
    if (device) { log.info("${device.label ?: device.name}: ${message}") }
    if (app) { log.info("${app.label ?: app.name}: ${message}") }
  }
}

void logDebug(String message) {
  if (loggingEnabled('debug')) {
    if (device) { log.debug("${device.label ?: device.name}: ${message}") }
    if (app) { log.debug("${app.label ?: app.name}: ${message}") }
  }
}

void logTrace(String message) {
  if (loggingEnabled('trace')) {
    if (device) { log.trace("${device.label ?: device.name}: ${message}") }
    if (app) { log.trace("${app.label ?: app.name}: ${message}") }
  }
}

/** Returns whether the configured logging level includes the requested level. */
private boolean loggingEnabled(String messageLevel) {
  String configuredLevel = normalizeLogLevel(settings.logLevel ?: 'info')
  return isLogLevelEnabled(configuredLevel, messageLevel)
}

/** Normalizes an unexpected logging preference to the safe default. */
@CompileStatic
private static String normalizeLogLevel(String level) {
  String normalized = level?.toLowerCase()
  return ['trace', 'debug', 'info', 'warn', 'error', 'off'].contains(normalized) ? normalized : 'info'
}

/**
 * Compares logging levels without accessing Hubitat runtime objects. This is
 * intentionally statically compiled.
 */
@CompileStatic
private static boolean isLogLevelEnabled(String configuredLevel, String messageLevel) {
  Integer configuredRank = logLevelRank(configuredLevel)
  Integer messageRank = logLevelRank(messageLevel)
  return configuredRank > 0 && messageRank > 0 && messageRank <= configuredRank
}

/** Converts a logging level into its filtering rank. */
@CompileStatic
private static Integer logLevelRank(String level) {
  switch (level?.toLowerCase()) {
    case 'trace':
      return 5
    case 'error':
      return 1
    case 'warn':
      return 2
    case 'info':
      return 3
    case 'debug':
      return 4
    default:
      return 0
  }
}

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
        required: true,          // User must configure this
        submitOnChange: true
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
        multiple: true,                  // User can select multiple bulbs
        submitOnChange: true
      )

      // Disable switches - turning any of these on will prevent sunrise from running
      input(
        'disableSwitches',
        'capability.switch',
        title: 'Disable Sunrise with switches',
        required: false,
        multiple: true,
        submitOnChange: true
      )

      // Snooze buttons - pressing one stops current sunrise and reschedules
      input(
        'snoozeButtons',
        'capability.pushableButton',
        title: 'Snooze Sunrise with buttons',
        required: false,
        multiple: true,
        submitOnChange: true
      )

      // Presence sensors - sunrise only runs if at least one shows "present"
      input(
        'requiredPresence',
        'capability.presenceSensor',
        title: 'Required presence for Sunrise',
        required: false,
        multiple: true,
        submitOnChange: true
      )
    }

    // =========================================================================
    // SUNRISE SETTINGS SECTION
    // =========================================================================
    // Configure timing parameters for the animation
    section('<h2>Sunrise Settings</h2>') {
      // Total duration for the sunrise animation (divided across active stages)
      input(
        'sunriseDuration',
        'number',
        title: 'Duration of minutes to brighten lights',
        range: '10..60',           // Must be between 10 and 60 minutes
        required: true,
        defaultValue: 30,          // Default to 30 minutes
        submitOnChange: true
      )

      // How long to wait after snooze before starting a new sunrise
      input(
        'snoozeDuration',
        'number',
        title: 'Duration of minutes to snooze',
        range: '10..60',
        required: true,
        defaultValue: 10,          // Default to 10 minute snooze
        submitOnChange: true
      )

      input(
        'startingStage',
        'enum',
        title: 'Start sunrise at stage',
        options: [
          '1': 'Stage 1 - Full RGB sunrise',
          '2': 'Stage 2 - RGB softening',
          '3': 'Stage 3 - CT brightening'
        ],
        required: true,
        defaultValue: '1',
        submitOnChange: true
      )

      input(
        'customizeStages',
        'bool',
        title: 'Customize animation stages',
        required: false,
        defaultValue: false,
        submitOnChange: true
      )

      String selectedStartingStage = "${settings.startingStage ?: '1'}"
      if (settings.customizeStages == true && selectedStartingStage == '1') {
        section('<h3>Stage 1 - RGB color ramp</h3>') {
          input('stage1HueStart', 'number', title: 'Hue start (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE1_HUE_START, submitOnChange: true)
          input('stage1HueEnd', 'number', title: 'Hue end (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE1_HUE_END, submitOnChange: true)
          input('stage1SaturationStart', 'number', title: 'Saturation start (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE1_SATURATION_START, submitOnChange: true)
          input('stage1SaturationEnd', 'number', title: 'Saturation end (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE1_SATURATION_END, submitOnChange: true)
          input('stage1LevelStart', 'number', title: 'Brightness start (1-100)', range: '1..100', defaultValue: DEFAULT_STAGE1_LEVEL_START, submitOnChange: true)
          input('stage1LevelEnd', 'number', title: 'Brightness end (1-100)', range: '1..100', defaultValue: DEFAULT_STAGE1_LEVEL_END, submitOnChange: true)
        }

      }

      if (settings.customizeStages == true && ['1', '2'].contains(selectedStartingStage)) {
        section('<h3>Stage 2 - RGB softening</h3>') {
          input('stage2HueStart', 'number', title: 'Hue start (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE2_HUE_START, submitOnChange: true)
          input('stage2HueEnd', 'number', title: 'Hue end (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE2_HUE_END, submitOnChange: true)
          input('stage2SaturationStart', 'number', title: 'Saturation start (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE2_SATURATION_START, submitOnChange: true)
          input('stage2SaturationEnd', 'number', title: 'Saturation end (0-100)', range: '0..100', defaultValue: DEFAULT_STAGE2_SATURATION_END, submitOnChange: true)
          input('stage2LevelStart', 'number', title: 'Brightness start (1-100)', range: '1..100', defaultValue: DEFAULT_STAGE2_LEVEL_START, submitOnChange: true)
          input('stage2LevelEnd', 'number', title: 'Brightness end (1-100)', range: '1..100', defaultValue: DEFAULT_STAGE2_LEVEL_END, submitOnChange: true)
        }

      }

      if (settings.customizeStages == true && selectedStartingStage == '3') {
        section('<h3>Stage 3 - CT brightening</h3>') {
          input('stage3CTStart', 'number', title: 'Color temperature start (K)', range: '1000..10000', defaultValue: START_CT, submitOnChange: true)
          input('stage3CTEnd', 'number', title: 'Color temperature end (K)', range: '1000..10000', defaultValue: END_CT, submitOnChange: true)
          input('stage3LevelStart', 'number', title: 'Brightness start (1-100)', range: '1..100', defaultValue: DEFAULT_STAGE3_LEVEL_START, submitOnChange: true)
          input('stage3LevelEnd', 'number', title: 'Brightness end (1-100)', range: '1..100', defaultValue: DEFAULT_STAGE3_LEVEL_END, submitOnChange: true)
        }
      }
    }

    // =========================================================================
    // LOGGING AND TESTING SECTION
    // =========================================================================
    // Configure logging verbosity and provide testing buttons
    section('<h2>Logging</h2>') {
      // Select the most detailed level that should be logged
      input(
        'logLevel',
        'enum',
        title: 'Logging level',
        options: [
          trace: 'Trace',
          debug: 'Debug',
          info: 'Info',
          warn: 'Warn',
          error: 'Error',
          off: 'Off'
        ],
        required: false,
        defaultValue: 'info',
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

      input(
        name: 'previewBtn',
        type: 'button',
        title: 'Preview Sunrise in Logs',
        backgroundColor: 'SteelBlue',
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
// LIFECYCLE & CONFIGURATION
// =============================================================================

/**
 * Clears this app's runtime state.
 *
 * This app is not a device, so the generic library implementation cannot be
 * called unless the library is included. Keeping the small app-specific
 * implementation here also avoids importing unrelated library methods.
 */
void clearAllStates() {
  state.clear()
}

/** Reinitializes the app whenever settings are saved, including Done. */
void updated() {
  configure()
}

/** Cleans up all subscriptions and schedules when the app is removed. */
void uninstalled() {
  unsubscribe()
  unschedule()
  clearAllStates()
}

/** Initializes a newly installed app. */
void installed() {
  configure()
}

/** Clears subscriptions and reinitializes the app configuration. */
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
 * - Snooze buttons: Temporarily pause and reschedule the sunrise
 */
private void subscribeEventHandlers() {
  // Listen for disable switch events (on/off changes)
  subscribe(disableSwitches, 'switch', 'disableEvent')

  // Listen for snooze button presses
  subscribe(snoozeButtons, 'pushed', 'snoozeButtonEvent')
}

/**
 * calculateStageDurations() - Calculates timing intervals for each animation stage
 * The sunrise animation is divided across the configured active stages:
 * - Stage 1: Deep red to orange (10 steps)
 * - Stage 2: Orange to soft white (40 steps)
 * - Stage 3: Soft white to full brightness (80 steps)
 * This method calculates how many seconds between each step to achieve the user's
 * desired total duration. If the animation starts later, the skipped stages
 * do not consume any of the configured duration.
 */
private void calculateStageDurations() {
  Map animationConfig = buildAnimationConfig()
  state.animationConfig = animationConfig
  Map stages = animationConfig.stages as Map
  state.stageSecs = stages.stage1.durationSeconds
  state.stage1interval = calculateInterval(stages.stage1.durationSeconds as Integer, stages.stage1.steps as Integer)
  state.stage2interval = calculateInterval(stages.stage2.durationSeconds as Integer, stages.stage2.steps as Integer)
  state.stage3interval = calculateInterval(stages.stage3.durationSeconds as Integer, stages.stage3.steps as Integer)
}

/** Builds a validated snapshot of all stage settings for one animation run. */
private Map buildAnimationConfig() {
  Integer startingStage = normalizeInteger(settings.startingStage, 1, 1, 3)
  Boolean customize = (settings.customizeStages == true)
  Integer totalSeconds = 60 * normalizeInteger(settings.sunriseDuration, DEFAULT_SUNRISE_DURATION, MIN_DURATION, MAX_DURATION)
  Integer activeStageCount = 4 - startingStage
  Integer baseStageSeconds = (totalSeconds / activeStageCount)
  Integer remainderSeconds = totalSeconds % activeStageCount

  Map stages = [
    stage1: [
      mode: 'rgb',
      durationSeconds: 0,
      hueStart: stageSetting('stage1HueStart', DEFAULT_STAGE1_HUE_START, 0, 100, customize),
      hueEnd: stageSetting('stage1HueEnd', DEFAULT_STAGE1_HUE_END, 0, 100, customize),
      saturationStart: stageSetting('stage1SaturationStart', DEFAULT_STAGE1_SATURATION_START, 0, 100, customize),
      saturationEnd: stageSetting('stage1SaturationEnd', DEFAULT_STAGE1_SATURATION_END, 0, 100, customize),
      levelStart: stageSetting('stage1LevelStart', DEFAULT_STAGE1_LEVEL_START, 1, 100, customize),
      levelEnd: stageSetting('stage1LevelEnd', DEFAULT_STAGE1_LEVEL_END, 1, 100, customize)
    ],
    stage2: [
      mode: 'rgb',
      durationSeconds: 0,
      hueStart: stageSetting('stage2HueStart', DEFAULT_STAGE2_HUE_START, 0, 100, customize),
      hueEnd: stageSetting('stage2HueEnd', DEFAULT_STAGE2_HUE_END, 0, 100, customize),
      saturationStart: stageSetting('stage2SaturationStart', DEFAULT_STAGE2_SATURATION_START, 0, 100, customize),
      saturationEnd: stageSetting('stage2SaturationEnd', DEFAULT_STAGE2_SATURATION_END, 0, 100, customize),
      levelStart: stageSetting('stage2LevelStart', DEFAULT_STAGE2_LEVEL_START, 1, 100, customize),
      levelEnd: stageSetting('stage2LevelEnd', DEFAULT_STAGE2_LEVEL_END, 1, 100, customize)
    ],
    stage3: [
      mode: 'ct',
      durationSeconds: 0,
      ctStart: stageSetting('stage3CTStart', START_CT, 1000, 10000, customize),
      ctEnd: stageSetting('stage3CTEnd', END_CT, 1000, 10000, customize),
      levelStart: stageSetting('stage3LevelStart', DEFAULT_STAGE3_LEVEL_START, 1, 100, customize),
      levelEnd: stageSetting('stage3LevelEnd', DEFAULT_STAGE3_LEVEL_END, 1, 100, customize)
    ]
  ]

  Map stage1 = stages.stage1 as Map
  stage1.steps = calculateRgbStageSteps(
    stage1.hueStart as Integer,
    stage1.hueEnd as Integer,
    stage1.saturationStart as Integer,
    stage1.saturationEnd as Integer,
    stage1.levelStart as Integer,
    stage1.levelEnd as Integer
  )
  Map stage2 = stages.stage2 as Map
  stage2.steps = calculateRgbStageSteps(
    stage2.hueStart as Integer,
    stage2.hueEnd as Integer,
    stage2.saturationStart as Integer,
    stage2.saturationEnd as Integer,
    stage2.levelStart as Integer,
    stage2.levelEnd as Integer
  )
  Map stage3 = stages.stage3 as Map
  stage3.steps = calculateCtStageSteps(
    stage3.ctStart as Integer,
    stage3.ctEnd as Integer,
    stage3.levelStart as Integer,
    stage3.levelEnd as Integer
  )

  Integer activeIndex = 0
  (startingStage..3).each { Integer stageNumber ->
    Map stage = stages["stage${stageNumber}"] as Map
    stage.durationSeconds = baseStageSeconds + ((activeIndex < remainderSeconds) ? 1 : 0)
    activeIndex += 1
  }
  return [startingStage: startingStage, stages: stages]
}

/** Reads one optional advanced stage value or returns its documented default. */
private Integer stageSetting(String settingName, Integer fallback, Integer minimum, Integer maximum, Boolean customize) {
  return customize ? normalizeInteger(settings[settingName], fallback, minimum, maximum) : fallback
}

/** Calculates RGB steps from the largest hue, saturation, or brightness change. */
@CompileStatic
private static Integer calculateRgbStageSteps(Integer hueStart, Integer hueEnd, Integer saturationStart, Integer saturationEnd, Integer levelStart, Integer levelEnd) {
  Integer hueDelta = Math.abs(hueEnd - hueStart)
  Integer saturationDelta = Math.abs(saturationEnd - saturationStart)
  Integer levelDelta = Math.abs(levelEnd - levelStart)
  return Math.max(1, Math.max(hueDelta, Math.max(saturationDelta, levelDelta)))
}

/** Calculates CT steps from brightness change and a maximum CT change per step. */
@CompileStatic
private static Integer calculateCtStageSteps(Integer ctStart, Integer ctEnd, Integer levelStart, Integer levelEnd) {
  Integer levelDelta = Math.abs(levelEnd - levelStart)
  double ctSteps = Math.abs(ctEnd - ctStart).doubleValue() / CT_STEP_KELVIN.doubleValue()
  Integer roundedCtSteps = (Integer) Math.ceil(ctSteps)
  return Math.max(1, Math.max(levelDelta, roundedCtSteps))
}

/** Returns the saved configuration for an active stage. */
private Map stageConfig(Integer stageNumber) {
  Map animationConfig = state.animationConfig as Map
  return animationConfig.stages["stage${stageNumber}"] as Map
}

/** Interpolates one stage frame from its configured start and end values. */
private Map interpolateStageFrame(Map stage, Integer step) {
  Integer totalSteps = stage.steps as Integer
  Integer boundedStep = Math.max(0, Math.min(totalSteps, step))
  if (stage.mode == 'ct') {
    return [
      colorTemperature: interpolateInteger(stage.ctStart as Integer, stage.ctEnd as Integer, boundedStep, totalSteps),
      level: interpolateInteger(stage.levelStart as Integer, stage.levelEnd as Integer, boundedStep, totalSteps)
    ]
  }
  return colorMap(
    interpolateInteger(stage.hueStart as Integer, stage.hueEnd as Integer, boundedStep, totalSteps),
    interpolateInteger(stage.saturationStart as Integer, stage.saturationEnd as Integer, boundedStep, totalSteps),
    interpolateInteger(stage.levelStart as Integer, stage.levelEnd as Integer, boundedStep, totalSteps)
  )
}

/** Interpolates an integer value using floating-point arithmetic and rounding. */
@CompileStatic
private static Integer interpolateInteger(Integer start, Integer end, Integer step, Integer totalSteps) {
  double progress = step.doubleValue() / totalSteps.doubleValue()
  return (Integer) Math.round(start + ((end - start) * progress))
}

/** Formats one calculated frame for concise Hubitat log output. */
private String formatPreviewFrame(Map stage, Map frame) {
  if (stage.mode == 'ct') {
    return "${frame.level}% brightness, ${frame.colorTemperature}K CT"
  }
  return "${frame.level}% brightness, HSV hue ${frame.hue}, saturation ${frame.saturation}%"
}

/** Returns a bounded integer setting value without allowing malformed input to fail initialization. */
@CompileStatic
private static Integer normalizeInteger(Object rawValue, Integer fallback, Integer minimum, Integer maximum) {
  Integer value = fallback
  if (rawValue != null) {
    try {
      value = Integer.valueOf(rawValue.toString())
    } catch (Exception ignored) {
      value = fallback
    }
  }
  return Math.max(minimum, Math.min(maximum, value))
}

/**
 * Calculates a safe animation interval without accessing Hubitat runtime
 * objects. This is intentionally statically compiled.
 */
@CompileStatic
private static Integer calculateInterval(Integer stageSeconds, Integer stepCount) {
  double intervalValue = stageSeconds.doubleValue() / stepCount.doubleValue()
  Integer interval = (Integer) Math.round(intervalValue)
  return (interval > 1) ? interval : 1
}

/** Distributes fractional step intervals so each stage stays on its time budget. */
@CompileStatic
private static Integer calculateStepDelay(Integer stageSeconds, Integer stepCount, Integer completedSteps) {
  double currentTarget = stageSeconds.doubleValue() * completedSteps.doubleValue() / stepCount.doubleValue()
  double nextTarget = stageSeconds.doubleValue() * (completedSteps + 1).doubleValue() / stepCount.doubleValue()
  Integer delay = (Integer) (Math.round(nextTarget) - Math.round(currentTarget))
  return (delay > 0) ? delay : 1
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
// UI BUTTON HANDLERS
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
    case 'testBtn':
      // User clicked "Test Sunrise" - run the sunrise immediately for testing
      sunriseStart()
      break
    case 'previewBtn':
      // User clicked "Preview Sunrise in Logs" - report without controlling bulbs
      previewSunrise()
      break
  }
}

// =============================================================================
// EVENT HANDLERS
// =============================================================================

/**
 * snoozeButtonEvent() - Handles snooze button presses
 * When a snooze button is pressed:
 *   - Immediately stops the current sunrise animation
 *   - Schedules a new sunrise after the snooze duration
 *
 * @param event - The Event object containing button information
 */
void snoozeButtonEvent(Event event) {
  logDebug("Received snooze button press: ${event.value}")

  // Stop the current sunrise animation and turn off bulbs.
  abortSunrise()
  state.snoozeActive = true

  // Restart the sunrise when the snooze duration expires.
  Integer snoozeMinutes = normalizeInteger(settings.snoozeDuration, DEFAULT_SNOOZE_DURATION, MIN_DURATION, MAX_DURATION)
  Integer snoozeDurationSecs = (60 * snoozeMinutes)
  unschedule('snoozeOffHandler')
  runIn(snoozeDurationSecs, 'snoozeOffHandler')
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
// SNOOZE & DISABLE CONTROLS
// =============================================================================

/**
 * snoozeOffHandler() - Ends the snooze period and restarts the sunrise
 * This method is called automatically after the snooze duration expires.
 */
private void snoozeOffHandler() {
  state.snoozeActive = false
  sunriseStart()
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
// SUNRISE SEQUENCING
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

  if (!settings.rgbwBulbs) {
    logWarn('Skipping sunrise because no RGB/RGBW bulbs are configured')
    return
  }

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

/** Logs the calculated sunrise plan without scheduling or commanding bulbs. */
void previewSunrise() {
  Map animationConfig = buildAnimationConfig()
  Integer startingStage = animationConfig.startingStage as Integer
  Map stages = animationConfig.stages as Map

  logInfo('Sunrise preview start')
  (startingStage..3).each { Integer stageNumber ->
    Map stage = stages["stage${stageNumber}"] as Map
    logInfo("Stage ${stageNumber}: ${stage.durationSeconds} seconds duration (${stage.steps} steps)")
  }

  (startingStage..3).each { Integer stageNumber ->
    Map stage = stages["stage${stageNumber}"] as Map
    logInfo("Stage ${stageNumber}, Start: ${formatPreviewFrame(stage, interpolateStageFrame(stage, 0))}")
    Integer totalSteps = stage.steps as Integer
    (1..totalSteps).each { Integer step ->
      logInfo("Stage ${stageNumber}, Step ${step}: ${formatPreviewFrame(stage, interpolateStageFrame(stage, step))}")
    }
  }
  logInfo('Sunrise preview end')
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
  if (state.brightenRGBWBulbsRunning == true) {
    logDebug('Ignoring sunrise start because an animation is already running')
    return
  }

  Map animationConfig = state.animationConfig as Map
  Integer startingStage = animationConfig.startingStage as Integer
  Map stage = stageConfig(startingStage)
  state.brightenRGBWBulbsRunning = true
  state.rgbwStage = startingStage
  state.rgbwStep = 0

  Map initialFrame = interpolateStageFrame(stage, 0)
  applyStageFrame(stage, initialFrame)
  logDebug("Starting sunrise at stage ${startingStage} - Frame: ${initialFrame}")

  // Give the devices a moment to accept the staged command before advancing.
  runIn(1, 'brightenRGBWBulbs')
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
  state.brightenRGBWBulbsRunning = true

  // Check if user has disabled or snoozed the sunrise
  if (sunriseDisabled()) {
    logInfo('Exiting brightenRGBWBulbs() because sunrise is disabled or snoozed')
    state.brightenRGBWBulbsRunning = false
    return
  }

  // Ignore a callback left over from an abort or configuration update.
  if (state.rgbwStage == null || state.rgbwStep == null || !state.animationConfig) {
    logWarn('Exiting brightenRGBWBulbs() because animation state is incomplete')
    state.brightenRGBWBulbsRunning = false
    return
  }

  Integer stageNumber = state.rgbwStage as Integer
  Map stage = stageConfig(stageNumber)
  Integer stageStep = (state.rgbwStep as Integer) + 1
  Integer totalSteps = stage.steps as Integer
  Map frame = interpolateStageFrame(stage, stageStep)

  state.rgbwStep = stageStep
  applyStageFrame(stage, frame)
  logDebug("Stage ${stageNumber} - Frame: ${frame}")

  if (stageStep < totalSteps) {
    Integer delay = calculateStepDelay(stage.durationSeconds as Integer, totalSteps, stageStep)
    runIn(delay, 'brightenRGBWBulbs')
    return
  }

  if (stageNumber < 3) {
    Integer nextStageNumber = stageNumber + 1
    state.rgbwStage = nextStageNumber
    state.rgbwStep = 0
    Map nextStage = stageConfig(nextStageNumber)
    Map initialFrame = interpolateStageFrame(nextStage, 0)
    applyStageFrame(nextStage, initialFrame)
    Integer delay = calculateStepDelay(nextStage.durationSeconds as Integer, nextStage.steps as Integer, 0)
    runIn(delay, 'brightenRGBWBulbs')
    return
  }

  logInfo('Sunrise simulation complete')
  state.brightenRGBWBulbsRunning = false
}

/** Builds an integer HSV color map for a bulb command. */
@CompileStatic
private Map colorMap(Integer hue, Integer saturation, Integer level) {
  return [hue: hue, saturation: saturation, level: level]
}

/** Applies an RGB or CT frame and mirrors its values into the app state. */
private void applyStageFrame(Map stage, Map frame) {
  if (stage.mode == 'ct') {
    state.rgbwCT = frame.colorTemperature
    state.rgbwLevel = frame.level
    setColorTemperatureOnBulbs(frame.colorTemperature as Integer, frame.level as Integer)
    return
  }

  state.rgbwHue = frame.hue
  state.rgbwSaturation = frame.saturation
  state.rgbwLevel = frame.level
  state.rgbwColorMap = frame
  setColorOnBulbs(frame)
}

/** Sends one HSV frame to every selected bulb. */
private void setColorOnBulbs(Map color) {
  settings.rgbwBulbs?.each { DeviceWrapper bulb ->
    bulb.setColor(color)
  }
}

/** Sends one CT frame to every selected bulb. */
private void setColorTemperatureOnBulbs(Integer colorTemperature, Integer level) {
  settings.rgbwBulbs?.each { DeviceWrapper bulb ->
    bulb.setColorTemperature(colorTemperature, level, 0)
  }
}

// =============================================================================
// HELPERS
// =============================================================================

/**
 * sunriseDisabled() - Checks if the sunrise should be prevented from running
 * This method checks the state of user-configured control switches to determine
 * if the sunrise animation should be blocked. Returns true if EITHER:
 * 1. Any disable switch is currently ON, OR
 * 2. A snooze is currently active
 *
 * @return boolean - true if sunrise should be disabled, false if it can run
 */
private boolean sunriseDisabled() {
  // Check if any disable switches are on
  // The any() method returns true if at least one switch is 'on'
  boolean disabled = settings.disableSwitches?.any { DeviceWrapper sw ->
    (sw.currentValue('switch') == 'on')
  }

  // Button devices are momentary, so snooze state is tracked by the app.
  boolean snoozed = (state.snoozeActive == true)

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
  // Hubitat returns an empty collection when the optional input has no
  // selections. Treat both null and empty selections as no requirement.
  List<DeviceWrapper> presenceSensors = settings.requiredPresence as List<DeviceWrapper>
  if (presenceSensors == null || presenceSensors.isEmpty()) {
    return true
  }
  // When sensors are selected, at least one must report "present".
  return presenceSensors.any { DeviceWrapper presence ->
    (presence.currentValue('presence') == 'present')
  }
}
