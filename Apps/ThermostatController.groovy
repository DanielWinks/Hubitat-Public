/**
 *  ============================================================================
 *  THERMOSTAT CONTROLLER - SMART TEMPERATURE AUTOMATION
 *  ============================================================================
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
 *
 *  ============================================================================
 *  PURPOSE & OVERVIEW (THE BIG PICTURE)
 *  ============================================================================
 *
 *  WHAT THIS APP DOES:
 *  This app acts as an "intelligent thermostat manager" for your home. Think
 *  of it as a smart assistant that automatically adjusts your heating and
 *  cooling based on whether you're home, away, or asleep - while always
 *  respecting any manual temperature changes you make.
 *
 *  THE PROBLEM IT SOLVES:
 *  Traditional thermostats keep the same temperature settings all day and night,
 *  even when nobody is home. This wastes energy and money. Many "smart"
 *  thermostats try to fix this but they often "fight" with you - constantly
 *  resetting temperatures back to a schedule even when you've intentionally
 *  changed them. This app is different: it's smart but respectful of your choices.
 *
 *  HOW IT WORKS (SIMPLE EXPLANATION):
 *  1. You configure three temperature "profiles" using virtual thermostats:
 *     - Normal Mode: Your preferred temps when home and awake
 *     - Away Mode: Energy-saving temps when nobody is home
 *     - Night Mode: Sleep-friendly temps when everyone is asleep
 *
 *  2. You select which Hubitat location modes map to "Away" and "Night"
 *     (e.g., "Vacation" → Away, "Sleep" → Night). Any mode not selected
 *     is treated as Normal mode.
 *
 *  3. When modes change, the app:
 *     - Saves your current temperature preferences (if in Normal mode)
 *     - Applies the appropriate profile temperatures
 *     - Restores your saved preferences when returning to Normal mode
 *
 *  4. BONUS FEATURE: If you open a window or door, the app can automatically
 *     turn off your heating/cooling to avoid wasting energy on the outdoors.
 *
 *  ============================================================================
 *  KEY CONCEPTS EXPLAINED (TERMS YOU NEED TO KNOW)
 *  ============================================================================
 *
 *  THERMOSTAT:
 *  - The device on your wall that controls heating and cooling
 *  - Has two temperature settings (called "setpoints"):
 *    • Heating Setpoint: Turn on heat if temperature drops below this
 *    • Cooling Setpoint: Turn on A/C if temperature rises above this
 *  - Example: Heat = 70°F, Cool = 75°F means maintain 70-75°F range
 *
 *  VIRTUAL THERMOSTAT:
 *  - A "pretend" thermostat that exists only in Hubitat's software
 *  - Used as a "memory slot" to store temperature settings for each mode
 *  - You set these up once in Hubitat, then configure this app to use them
 *  - Think of them as "temperature presets" like radio station presets
 *  - Example: Your "Away" virtual thermostat might be set to 65°F/80°F
 *
 *  LOCATION MODES (Hubitat concept):
 *  - Hubitat lets you define "modes" that represent your home's state
 *  - Common modes: Home, Away, Night, Day, Evening, Vacation
 *  - Modes can change automatically (based on time or presence) or manually
 *  - This app watches for mode changes and adjusts temperatures accordingly
 *
 *  SETPOINT:
 *  - Technical term for "target temperature"
 *  - "Heating setpoint of 70°F" = "keep temperature at least 70 degrees"
 *  - "Cooling setpoint of 75°F" = "keep temperature no higher than 75 degrees"
 *
 *  CONTACT SENSOR:
 *  - A device that detects if a door or window is open or closed
 *  - Typically has two parts: one on the frame, one on the moving part
 *  - When separated, reports "open"; when together, reports "closed"
 *  - Used by this app to detect open windows and save energy
 *
 *  STATE (programming concept):
 *  - The app's "memory" that persists between runs
 *  - Like writing notes on a notepad that stays there even after you leave
 *  - This app uses state to remember your temperature preferences
 *  - Example: state.setPointLow stores your normal heating temperature
 *
 *  SETTINGS (programming concept):
 *  - Configuration values you provide when setting up the app
 *  - Examples: which thermostat to control, which sensors to monitor
 *  - These are what you see in the app's configuration page
 *
 *  ============================================================================
 *  HOW THE APP WORKS (DETAILED STEP-BY-STEP)
 *  ============================================================================
 *
 *  SCENARIO 1: LEAVING HOME (Normal → Away Mode)
 *  ┌───────────────────────────────────────────────────────────────────────┐
 *  │ 1. You leave home, Hubitat changes mode to "Away"                    │
 *  │ 2. App receives notification: "Mode changed to Away"                 │
 *  │ 3. App checks: "Am I currently using normal temperatures?" (Yes)     │
 *  │ 4. App reads current thermostat: 70°F heat, 75°F cool               │
 *  │ 5. App saves these to memory: state.setPointLow = 70                │
 *  │                                 state.setPointHigh = 75               │
 *  │ 6. App marks: "Temperatures saved, not restored yet"                 │
 *  │ 7. App reads Away virtual thermostat: 65°F heat, 80°F cool         │
 *  │ 8. App applies these to real thermostat                             │
 *  │ 9. App sets thermostat to "auto" mode (can heat or cool)            │
 *  │ 10. Result: House now maintains 65-80°F to save energy             │
 *  └───────────────────────────────────────────────────────────────────────┘
 *
 *  SCENARIO 2: ARRIVING HOME (Away → Normal Mode)
 *  ┌───────────────────────────────────────────────────────────────────────┐
 *  │ 1. You arrive home, Hubitat changes mode to "Home" or "Day"         │
 *  │ 2. App receives notification: "Mode changed to Home"                │
 *  │ 3. App checks: "Do I have saved temperatures to restore?" (Yes)     │
 *  │ 4. App retrieves from memory: 70°F heat, 75°F cool                 │
 *  │ 5. App applies these to real thermostat                            │
 *  │ 6. App marks: "Temperatures restored, back to normal"               │
 *  │ 7. App sets thermostat to "auto" mode                               │
 *  │ 8. Result: House returns to your preferred 70-75°F                 │
 *  └───────────────────────────────────────────────────────────────────────┘
 *
 *  SCENARIO 3: GOING TO SLEEP (Normal → Night Mode)
 *  ┌───────────────────────────────────────────────────────────────────────┐
 *  │ Same as "Leaving Home" but uses Night virtual thermostat settings   │
 *  │ Example: Night mode might be 68°F heat, 72°F cool (cooler for sleep)│
 *  └───────────────────────────────────────────────────────────────────────┘
 *
 *  SCENARIO 4: WAKING UP (Night → Normal Mode)
 *  ┌───────────────────────────────────────────────────────────────────────┐
 *  │ Same as "Arriving Home" - restores your preferred daytime temps     │
 *  └───────────────────────────────────────────────────────────────────────┘
 *
 *  SCENARIO 5: SWITCHING BETWEEN AWAY AND NIGHT (Special Case)
 *  ┌───────────────────────────────────────────────────────────────────────┐
 *  │ Example: You're away, then it becomes nighttime (Away → Night)      │
 *  │ 1. App checks: "Are temperatures already saved?" (Yes, from Away)   │
 *  │ 2. App DOES NOT save again (would overwrite normal temps!)          │
 *  │ 3. App applies Night mode temperatures                              │
 *  │ 4. When you return home, app restores ORIGINAL normal temps         │
 *  │ 5. This prevents "forgetting" your true preferences                 │
 *  └───────────────────────────────────────────────────────────────────────┘
 *
 *  SCENARIO 6: OPEN WINDOW DETECTED
 *  ┌───────────────────────────────────────────────────────────────────────┐
 *  │ 1. Contact sensor reports: "Window opened"                          │
 *  │ 2. App immediately turns thermostat OFF (no grace period)           │
 *  │ 3. App also schedules a belt-and-suspenders retry after             │
 *  │    openWindowDuration minutes in case the first off() was lost      │
 *  │ 4. thermostatEventHandler watches thermostat events; if the         │
 *  │    thermostat drifts back to cooling/heating/non-off mode while a   │
 *  │    window is still open, it is forced off again                     │
 *  │ 5. When all windows/doors close:                                    │
 *  │    - App cancels the scheduled retry                                │
 *  │    - App turns thermostat back to "auto" mode                       │
 *  └───────────────────────────────────────────────────────────────────────┘
 *
 *  ============================================================================
 *  THE CRITICAL FLAG: state.setpointsRestored
 *  ============================================================================
 *
 *  This is the "smart" part that prevents the app from overwriting your
 *  preferences. It's a TRUE/FALSE flag that tracks the app's current state:
 *
 *  state.setpointsRestored = TRUE means:
 *  - Currently using your normal/preferred temperatures
 *  - No temperatures are saved in memory yet
 *  - If mode changes to Away/Night, SHOULD save current temps first
 *
 *  state.setpointsRestored = FALSE means:
 *  - Currently using Away or Night mode temperatures
 *  - Your normal temperatures ARE saved in memory
 *  - If mode changes to Away/Night again, should NOT save (already saved)
 *  - If mode returns to Normal, SHOULD restore saved temps
 *
 *  EXAMPLE WALKTHROUGH:
 *  ═══════════════════════════════════════════════════════════════════════════
 *  Current Mode          | setpointsRestored | What's in Memory | Current Temps
 *  ═══════════════════════════════════════════════════════════════════════════
 *  Normal (at home)      | TRUE              | (nothing)        | 70°F / 75°F
 *  Away (left home)      | FALSE             | 70°F / 75°F ✓   | 65°F / 80°F
 *  Night (still away)    | FALSE             | 70°F / 75°F ✓   | 68°F / 72°F
 *  Home (returned)       | TRUE              | (cleared)        | 70°F / 75°F ✓
 *  ═══════════════════════════════════════════════════════════════════════════
 *  ✓ = Correct behavior achieved by the flag
 *
 *  WITHOUT THIS FLAG (what would go wrong):
 *  ═══════════════════════════════════════════════════════════════════════════
 *  Current Mode          | What's in Memory      | Problem
 *  ═══════════════════════════════════════════════════════════════════════════
 *  Normal (at home)      | (nothing)             | OK
 *  Away (left home)      | 70°F / 75°F          | OK
 *  Night (still away)    | 68°F / 72°F ✗        | Overwrote normal temps!
 *  Home (returned)       | (nothing)             | Wrong temps restored!
 *  ═══════════════════════════════════════════════════════════════════════════
 *
 *  ============================================================================
 *  MEMORY STORAGE (State Variables Explained)
 *  ============================================================================
 *
 *  state.setPointLow (BigDecimal number):
 *  - Your normal heating temperature before entering Away/Night mode
 *  - Example: 70.0 means you normally want heating at 70°F
 *  - BigDecimal = precise decimal number (not approximate like regular decimals)
 *
 *  state.setPointHigh (BigDecimal number):
 *  - Your normal cooling temperature before entering Away/Night mode
 *  - Example: 75.0 means you normally want cooling at 75°F
 *
 *  state.setpointsRestored (TRUE or FALSE):
 *  - TRUE = using normal temps, nothing saved yet
 *  - FALSE = using Away/Night temps, normal temps are saved
 *  - This is the "smart flag" explained in detail above
 *
 *  state.currentOperatingState (text: "Normal", "Away", or "Night"):
 *  - Tracks which mode the app thinks it's in
 *  - Mainly for debugging and future features
 *  - Example: "Away" means app is currently applying Away mode temps
 *
 *  ============================================================================
 *  CONFIGURATION OPTIONS (What You Set Up)
 *  ============================================================================
 *
 *  REQUIRED DEVICES (must provide):
 *
 *  thermostat:
 *  - The actual physical thermostat device to control
 *  - This is the real device on your wall
 *
 *  awayModeVirtualThermostat:
 *  - Virtual device storing your Away mode temperature preferences
 *  - Set this to energy-saving temperatures (e.g., 65°F heat, 80°F cool)
 *
 *  nightModeVirtualThermostat:
 *  - Virtual device storing your Night mode temperature preferences
 *  - Set this to sleep-friendly temperatures (e.g., 68°F heat, 72°F cool)
 *
 *  OPTIONAL DEVICES (nice to have):
 *
 *  contactSensors:
 *  - One or more door/window sensors to detect when they're open
 *  - If any sensor reports "open", app can turn off heating/cooling
 *  - Saves energy by not heating/cooling the outdoors
 *
 *  MODE MAPPING (required):
 *
 *  awayModes:
 *  - One or more Hubitat location modes that should use Away temperatures
 *  - Example: "Away", "Vacation", "Gone"
 *  - Multiple modes can map to the same Away behavior
 *
 *  nightModes:
 *  - One or more Hubitat location modes that should use Night temperatures
 *  - Example: "Night", "Sleep", "Bedtime"
 *  - Multiple modes can map to the same Night behavior
 *
 *  NOTE: Any mode NOT selected as Away or Night is treated as Normal mode.
 *
 *  BEHAVIOR SETTINGS:
 *
 *  disableWithOpenWindowsOrDoors (TRUE or FALSE):
 *  - TRUE = Turn off thermostat when any window/door is open
 *  - FALSE = Ignore open windows/doors (not recommended, wastes energy)
 *  - Default: TRUE
 *
 *  openWindowDuration (number of minutes, 1-30):
 *  - Retry interval (in minutes) for the belt-and-suspenders safety retry
 *    that re-asserts thermostat.off() after a sensor opens. The thermostat
 *    is turned off immediately on every sensor-open event; this setting
 *    only controls the scheduled follow-up call in case the first off()
 *    command was lost to a transient mesh issue.
 *  - Default: 3 minutes
 *
 *  LOGGING SETTINGS (for troubleshooting):
 *
 *  logEnable (TRUE or FALSE):
 *  - TRUE = Log general informational messages
 *  - Recommended: TRUE (helps troubleshoot issues)
 *
 *  debugLogEnable (TRUE or FALSE):
 *  - TRUE = Log detailed technical information
 *  - Only enable when troubleshooting problems
 *  - Creates a lot of log entries
 *
 *  descriptionTextEnable (TRUE or FALSE):
 *  - TRUE = Log human-readable event descriptions
 *  - Recommended: TRUE (explains what the app is doing)
 *
 *  ============================================================================
 *  TIPS & BEST PRACTICES
 *  ============================================================================
 *
 *  SETTING UP VIRTUAL THERMOSTATS:
 *  1. Create two Generic Virtual Thermostat devices in Hubitat
 *  2. Name them clearly (e.g., "Away Temps", "Night Temps")
 *  3. Set their temperatures to your desired Away/Night settings
 *  4. You can adjust these anytime without reconfiguring the app
 *
 *  CHOOSING AWAY MODE TEMPERATURES:
 *  - Heating: 5-8°F lower than normal (saves energy, won't freeze pipes)
 *  - Cooling: 5-8°F higher than normal (saves energy, won't damage home)
 *  - Example: Normal = 70/75, Away = 65/80
 *
 *  CHOOSING NIGHT MODE TEMPERATURES:
 *  - Most people sleep better in cooler temperatures
 *  - Typical: 2-3°F cooler than daytime for heating
 *  - Example: Normal = 70/75, Night = 68/72
 *
 *  SETTING THE WINDOW TIMER:
 *  - 3 minutes (default) = Good balance for most homes
 *  - 1-2 minutes = Very aggressive, use if you frequently leave windows open
 *  - 5-10 minutes = More forgiving, use if doors open frequently for pets/kids
 *
 *  MANUAL TEMPERATURE ADJUSTMENTS:
 *  - You can manually change the thermostat anytime
 *  - The app only saves/restores temps during mode changes
 *  - Between mode changes, your manual adjustments are respected
 *  - Example: You can turn up the heat in Normal mode, and the app won't fight you
 *
 *  ============================================================================
 *  TROUBLESHOOTING COMMON ISSUES
 *  ============================================================================
 *
 *  PROBLEM: "Temperature doesn't restore when I come home"
 *  SOLUTION: Check Hubitat logs to see if mode actually changed to Home/Day
 *           The app only restores temps when exiting Away/Night modes
 *
 *  PROBLEM: "Thermostat turns off even when all windows are closed"
 *  SOLUTION: Check that all contact sensors are reporting "closed" correctly
 *           One faulty sensor reporting "open" will trigger the automation
 *
 *  PROBLEM: "App saved the wrong temperatures"
 *  SOLUTION: This usually happens if you left while in Night mode, or vice versa
 *           The app only saves temps when setpointsRestored = TRUE
 *           You may need to manually set desired temps and wait for next cycle
 *
 *  PROBLEM: "Away and Night temperatures are the same"
 *  SOLUTION: Check your virtual thermostats - they might be set to same values
 *           Update the virtual thermostat settings to your desired temps
 *
 *  ============================================================================
 *  TECHNICAL NOTES (For Programmers)
 *  ============================================================================
 *
 *  THREAD SAFETY:
 *  - runIn() with [overwrite: true] prevents duplicate timers
 *  - Multiple windows opening = single timer (keeps restarting)
 *  - disableThermostatDueToOpenWindow() re-checks window state before acting
 *    to guard against timer/close event race conditions
 *
 *  DATA TYPES:
 *  - BigDecimal for temperatures (precise decimal math)
 *  - Boolean for flags (TRUE/FALSE values)
 *  - String for mode names ("Away", "Night", "Normal")
 *  - List<String> for configurable mode lists (settings.awayModes, settings.nightModes)
 *
 *  STATE VARIABLES:
 *  - state.setpointsRestored: Boolean flag tracking save/restore lifecycle
 *  - state.currentOperatingState: String tracking current mode ("Normal", "Away", "Night")
 *  - state.disabledDueToOpenWindow: Boolean tracking open-window thermostat disable
 *  - state.initializedBefore: Boolean preventing wrong saves on first install
 *  - state.setPointLow / state.setPointHigh: Saved normal heating/cooling setpoints
 *
 *  EVENT HANDLING:
 *  - subscribe() registers event handlers (callbacks)
 *  - Handlers receive Event object with device and value info
 *  - All mode handling flows through handleModeChange() (single code path)
 *
 *  ERROR HANDLING:
 *  - Null checks prevent crashes if sensors/settings missing
 *  - Graceful degradation (continues working even if something fails)
 *  - First-install guard prevents saving wrong temps as "normal"
 *  - configure() calls both unsubscribe() and unschedule() for clean resets
 */

// ============================================================================
// IMPORTS (External Code Libraries)
// ============================================================================
// These lines "import" pre-written code that we'll use in this app
// Think of imports like bringing tools from a toolbox

// DeviceWrapper: Represents a device in Hubitat (thermostat, sensor, etc.)
// This is how the code "talks to" and controls physical devices
import com.hubitat.app.DeviceWrapper

// Event: Represents something that happened (sensor opened, temperature changed, etc.)
// Events are how Hubitat tells our app "something just happened!"
import com.hubitat.hub.domain.Event

// Include our custom utility library for logging and helper functions
// This provides useful tools like logDebug(), logInfo(), logWarn()
// The "#include" loads code from a shared library file
#include dwinks.UtilitiesAndLoggingLibrary

// ============================================================================
// APP DEFINITION (Tells Hubitat About This App)
// ============================================================================
// This section registers the app with Hubitat so it shows up in your apps list

definition(
  // The name that appears in Hubitat's app list
  name: 'Thermostat Controller',

  // Namespace prevents name conflicts with other developers' apps
  // Like a last name: there might be other "Thermostat Controllers" but only one by "dwinks"
  namespace: 'dwinks',

  // Who created this app
  author: 'Daniel Winks',

  // Short description shown when browsing apps
  description: 'Control thermostats based on occupancy and time of day. Remembering manual changes, so it does not fight the user.',

  // Category for organizing apps (empty = no specific category)
  category: '',

  // Icon URLs (empty = use default icon)
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

// ============================================================================
// USER INTERFACE (Configuration Page)
// ============================================================================
// This section defines what users see when setting up the app

// Tell Hubitat: "Show a page called 'mainPage' with title 'Thermostat Controller'"
// preferences is a special Hubitat block that defines configuration pages
preferences {page(name: 'mainPage', title: 'Thermostat Controller')}

/**
 * MAIN CONFIGURATION PAGE
 *
 * This function creates the user interface for configuring the app.
 * It's called automatically by Hubitat when the user clicks to configure the app.
 *
 * RETURN VALUE:
 * Returns a Map (collection of key-value pairs) that defines the page layout.
 * Hubitat reads this Map and generates the HTML page that users interact with.
 *
 * WHAT THE USER SEES:
 * - Device selection dropdowns (pick thermostats and sensors)
 * - Settings for open window behavior (how long to wait, whether to turn off, etc.)
 * - Logging options (what to log for troubleshooting)
 * - Optional custom name field (to label this app instance)
 */
Map mainPage() {
  // dynamicPage creates an interactive configuration page
  // "dynamic" means it can update/refresh as the user makes changes
  dynamicPage(
    // Internal name for this page (matches the preferences declaration above)
    name: 'mainPage',

    // HTML title shown at the top of the page
    // <h1> makes it a big heading
    title: '<h1>Thermostat Controller</h1>',

    // install: true = Show "Done" button to save and install the app
    install: true,

    // uninstall: true = Show "Remove" button to uninstall the app
    uninstall: true,

    // refreshInterval: 0 = Don't auto-refresh (would be annoying while configuring)
    refreshInterval: 0
  ) {
    // ===== SECTION 1: DEVICE SELECTION =====
    // section() creates a collapsible box on the page
    // <h2> makes a medium-sized heading
    section('<h2>Devices</h2>') {
      // input() creates a user input field
      // Each input has: internal name, type, user-visible title, and options

      // Select the main thermostat to control
      // 'thermostat' = internal name (used in code as settings.thermostat)
      // 'capability.thermostat' = only show devices that can control temperature
      // required: true = user MUST select something (can't leave blank)
      // multiple: false = can only select ONE device (not multiple)
      input (
        'thermostat',
        'capability.thermostat',
        title: 'Thermostat to control',
        required: true,
        multiple: false
      )

      // Select virtual thermostat for Away mode temperatures
      // This device stores the temperature settings to use when nobody is home
      // Think of it as a "memory slot" for Away mode preferences
      input (
        'awayModeVirtualThermostat',
        'capability.thermostat',
        title: 'Virtual Thermostat for "away" mode settings',
        required: true,
        multiple: false
      )

      // Select virtual thermostat for Night mode temperatures
      // This device stores the temperature settings to use when everyone is asleep
      input (
        'nightModeVirtualThermostat',
        'capability.thermostat',
        title: 'Virtual Thermostat for "night" mode settings',
        required: true,
        multiple: false
      )

      // Select contact sensors (door/window sensors)
      // 'capability.contactSensor' = only show devices that detect open/closed
      // required: false = this is OPTIONAL (app works without sensors)
      // multiple: true = can select MULTIPLE sensors (all your doors/windows)
      input (
        'contactSensors',
        'capability.contactSensor',
        title: 'Contact sensors to detect open windows/doors',
        required: false,
        multiple: true
      )
    }

    // ===== SECTION 2: MODE MAPPING =====
    // Let users select which Hubitat location modes map to Away and Night behavior.
    // Any mode NOT selected here is treated as "Normal" (preferred temperatures).
    // 'mode' input type shows a dropdown of all configured Hubitat location modes.
    section('<br><br><hr><h2>Mode Settings</h2>') {
      input (
        'awayModes',
        'mode',
        title: 'Which location modes should use "Away" temperatures?',
        description: 'Select one or more modes (e.g., Away, Vacation)',
        required: true,
        multiple: true
      )

      input (
        'nightModes',
        'mode',
        title: 'Which location modes should use "Night" temperatures?',
        description: 'Select one or more modes (e.g., Night, Sleep)',
        required: true,
        multiple: true
      )
    }

    // ===== SECTION 3: OPEN WINDOW/DOOR SETTINGS =====
    // <br> creates blank lines (spacing)
    // <hr> creates a horizontal line (visual separator)
    section('<br><br><hr><h2>Open Window/Door Settings</h2>') {
      // Toggle switch: should we turn off thermostat for open windows?
      // 'bool' = boolean (true/false) input type (shows as checkbox/toggle)
      // defaultValue: true = checkbox is CHECKED by default
      input (
        'disableWithOpenWindowsOrDoors',
        'bool',
        title: 'Disable thermostat when any window is open.',
        required: true,
        defaultValue: true
      )

      // Number input: retry interval for the belt-and-suspenders disable call.
      // The thermostat is turned off immediately whenever any sensor opens;
      // this setting only controls the follow-up runIn() that re-asserts the
      // off() command in case the initial call was lost to a mesh/retry issue.
      // 'number' = only accepts numeric input
      // range: '1..30' = must be between 1 and 30 (Hubitat enforces this)
      input (
        'openWindowDuration',
        'number',
        title: 'Retry interval (minutes) for the open-window thermostat-off safety retry',
        range: '1..30',
        required: true,
        defaultValue: 3
      )
    }

    // ===== SECTION 3: LOGGING OPTIONS =====
    // These control what messages appear in Hubitat's logs
    // Useful for troubleshooting when things don't work as expected
    section('<br><br><hr><h2>Logging</h2>') {
      // Enable general informational messages
      // Example: "Location entered Away mode", "Restoring previous setpoints"
      input (
        'logEnable',
        'bool',
        title: 'Enable Logging',
        required: false,
        defaultValue: true
      )

      // Enable detailed technical debug messages
      // Example: "contactSensorEventHandler: Kitchen Window is open"
      // Only enable this when troubleshooting problems (creates many log entries)
      input (
        'debugLogEnable',
        'bool',
        title: 'Enable debug logging',
        required: false,
        defaultValue: false
      )

      // Enable human-readable event descriptions
      // Example: "All windows/doors closed; restoring thermostat automation"
      input (
        'descriptionTextEnable',
        'bool',
        title: 'Enable descriptionText logging',
        required: false,
        defaultValue: true
      )
    }

    // ===== SECTION 4: CUSTOM NAME =====
    // Allows user to give this app instance a custom name
    // Useful if you have multiple instances controlling different thermostats
    // Example: "Main Floor Thermostat", "Upstairs Thermostat"
    section() {
      // label input creates a text field for naming the app instance
      // required: false = optional (Hubitat will use default name if left blank)
      label title: 'Enter a name for this app instance', required: false
    }
  }
  // The dynamicPage() call above returns the Map that Hubitat uses to build the page
}

// =============================================================================
// LIFECYCLE MANAGEMENT (App Startup & Initialization)
// =============================================================================
// These methods are called automatically by Hubitat at specific times in the
// app's "lifecycle" (birth, updates, death). Think of them as the app's
// "boot sequence" and "setup instructions".

/**
 * CONFIGURE METHOD
 *
 * This method is called when:
 * - User clicks a "Configure" button (if you add one)
 * - App needs to reset its event subscriptions
 * - Something went wrong and needs a fresh start
 * - The library's updated() lifecycle hook calls this automatically
 *
 * WHAT IT DOES:
 * 1. Cancels all existing event subscriptions (stops listening to devices)
 * 2. Cancels all pending scheduled tasks (stops stale timers)
 * 3. Calls initialize() to set everything up fresh from scratch
 *
 * WHY IT EXISTS:
 * - Provides a "clean slate" if settings change or things get stuck
 * - Prevents duplicate event handlers from accumulating
 * - Prevents stale timers from firing after reconfiguration
 *
 * TECHNICAL NOTE:
 * - unsubscribe() = "stop listening to all device events"
 * - unschedule() = "cancel all pending timers (runIn, schedule)"
 */
void configure() {
  // Stop listening to all device events
  // This clears out any old subscriptions that might be stale
  unsubscribe()

  // Cancel all pending scheduled tasks (e.g., open-window disable timers)
  // Without this, old timers could fire after reconfiguration and cause
  // unexpected behavior (e.g., turning off the thermostat from a stale timer)
  unschedule()

  // Now set everything up fresh
  // initialize() will create new subscriptions with current settings
  initialize()
}

/**
 * INITIALIZE METHOD
 *
 * This is the "main startup sequence" called when the app is:
 * - Installed for the first time
 * - Updated with new settings
 * - Manually configured via configure() above
 *
 * Think of this as the app's "boot sequence" - it prepares everything the
 * app needs to run, sets up event listeners, and handles the current state.
 *
 * WHAT IT DOES (step by step):
 * 1. Initializes state variables with default values (ensureStateDefaults)
 * 2. Subscribes to thermostat events (to monitor what it's doing)
 * 3. Subscribes to contact sensor events (to detect open windows/doors)
 * 4. Subscribes to location mode changes (to detect Home/Away/Night transitions)
 * 5. Checks current location mode and applies appropriate temperatures immediately
 *
 * WHY STEP 5 IS IMPORTANT:
 * - If you install the app while in Away mode, it should apply Away temps NOW
 * - Without this, the app would wait until the NEXT mode change to act
 * - This ensures immediate action rather than waiting for an event
 *
 * ABOUT SUBSCRIBE():
 * - subscribe() tells Hubitat: "When X happens, call function Y"
 * - Example: subscribe(contactSensors, 'contact', 'contactSensorEventHandler')
 *   Means: "When any contact sensor's 'contact' attribute changes,
 *          call the contactSensorEventHandler function"
 * - This is how the app responds to real-time events in your home
 */
void initialize() {
  // STEP 1: Set up default values for state variables
  // This ensures state.setpointsRestored and state.currentOperatingState exist
  // If they already exist, this does nothing (safe to call multiple times)
  ensureStateDefaults()

  // Log a fresh snapshot of every configured contact sensor using
  // skipCache=true. Produces one logInfo per sensor on every install/update
  // so we always have a known-good baseline in the logs.
  logFreshSensorStates()

  // STEP 2: Subscribe to thermostat events
  // Listen to ALL events from the thermostat device
  // Currently just for logging, but allows future features
  // When thermostat changes, call thermostatEventHandler()
  // settings.thermostat = the thermostat device selected in configuration
  subscribe(thermostat, 'thermostatEventHandler')

  // STEP 3: Subscribe to contact sensor events
  // Listen for open/closed events from door/window sensors
  // 'contact' = the attribute name (can be 'open' or 'closed')
  // When any sensor opens or closes, call contactSensorEventHandler()
  // settings.contactSensors = the list of sensors selected in configuration
  subscribe(contactSensors, 'contact', 'contactSensorEventHandler')

  // STEP 4: Subscribe to location mode changes
  // Listen for changes to Hubitat's location mode (Home, Away, Night, etc.)
  // location = special Hubitat object representing your home
  // 'mode' = the attribute that holds the current mode
  // When mode changes, call locationModeChangeHandler()
  subscribe(location, 'mode', 'locationModeChangeHandler')

  String mode = location.getMode()
  logDebug("Active location mode: ${mode}")

  // If a window/door is already open at startup, defer mode handling entirely.
  // Turn the thermostat off and stash the current Hubitat mode so it is applied
  // when contactSensorEventHandler() sees all windows close. Also start the
  // disable timer as a belt-and-suspenders guard (noop if already off).
  if (settings.disableWithOpenWindowsOrDoors && anyContactSensorsOpen()) {
    logInfo("Window/door open at initialization; deferring mode application for \"${mode}\" and keeping thermostat off")
    state.pendingLocationMode = mode
    thermostat.off()
    state.disabledDueToOpenWindow = true
    runIn((settings.openWindowDuration ?: 3) * 60, 'disableThermostatDueToOpenWindow', [overwrite: true])
    return
  }

  state.pendingLocationMode = null
  applyModeFromHubitat(mode)
}

/**
 * ENSURE STATE DEFAULTS METHOD
 *
 * This method initializes state variables with sensible default values if they
 * don't already exist. Think of "state" as the app's "memory notebook" that
 * persists between runs (survives hub reboots, app updates, etc.).
 *
 * STATE VARIABLES INITIALIZED:
 *
 * state.setpointsRestored (Boolean: true or false):
 * - Tracks whether we're using normal temps or Away/Night temps
 * - TRUE = Currently using normal/preferred temperatures
 * - FALSE = Currently using Away/Night temperatures (normal temps are saved)
 * - Default: true (assume we start in normal mode)
 *
 * state.currentOperatingState (String: "Normal", "Away", or "Night"):
 * - Tracks which mode the app thinks it's currently in
 * - Default: "Normal" (home and awake)
 *
 * state.disabledDueToOpenWindow (Boolean: true or false):
 * - Tracks whether the thermostat was turned off because a window/door was open
 * - Prevents mode changes from accidentally re-enabling the thermostat
 * - Default: false
 *
 * state.initializedBefore (Boolean: true or false):
 * - Guards against saving wrong temps on first install
 * - Set to true the first time the app enters a Normal mode
 * - While false, Away/Night mode transitions apply setpoints without saving
 * - Default: false
 *
 * WHY CHECK "== null"?
 * - State variables persist between app runs (saved to hub's disk)
 * - We only want to set defaults on FIRST run, not every time
 * - "== null" means "this variable doesn't exist yet"
 * - If it already has a value, we DON'T overwrite it
 * - This preserves the app's "memory" across restarts
 *
 * EXAMPLE SCENARIO:
 * - First install: state.setpointsRestored = null (doesn't exist yet)
 *   → This method sets it to true
 * - Hub reboots: state.setpointsRestored = false (from last Away mode)
 *   → This method does NOT change it (preserves the false value)
 */
private void ensureStateDefaults() {
  // Check if setpointsRestored has ever been set
  // state.setpointsRestored accesses the stored value
  // == null means "doesn't exist" or "never been set"
  if (state.setpointsRestored == null) {
    // First run - this variable doesn't exist yet
    // Set it to true (assume we're starting in normal mode)
    // This means: "Using normal temps, nothing saved yet"
    state.setpointsRestored = true
  }
  // If it's not null, do nothing (preserves existing value)

  // Check if currentOperatingState has ever been set
  // !state.currentOperatingState means "is null, empty, or false"
  if (!state.currentOperatingState) {
    // First run - set to "Normal" mode
    // This tracks that we're in normal operating mode
    state.currentOperatingState = 'Normal'
  }
  // If it already has a value, do nothing (preserves existing state)

  // Check if disabledDueToOpenWindow has ever been set
  // Tracks whether thermostat was turned off because a window/door was open
  if (state.disabledDueToOpenWindow == null) {
    state.disabledDueToOpenWindow = false
  }

  // Track whether the app has completed at least one full initialization cycle.
  // On the very first install, if the hub is in Away/Night mode, we should NOT
  // save the thermostat's current setpoints as "normal" temps because they may
  // already be Away/Night temps from manual adjustment before the app was installed.
  // After the first full cycle (entering normal mode at least once), this flag
  // is set to true and normal save/restore behavior takes effect.
  if (state.initializedBefore == null) {
    state.initializedBefore = false
  }

  // After this method, all state variables are guaranteed to exist
  // Either with their existing values (if already set) or defaults (if new)
}

/**
 * CHECK IF A MODE IS AN "AWAY" MODE
 *
 * Returns true if the given mode name is one that the user configured as an
 * Away mode in settings.awayModes. This replaces hardcoded "Away" comparisons
 * so users can map any Hubitat mode (e.g., "Vacation", "Gone") to Away behavior.
 *
 * @param modeName The Hubitat location mode name to check
 * @return Boolean true if this mode should use Away temperatures
 */
private boolean isAwayMode(String modeName) {
  return (settings.awayModes as List<String>)?.contains(modeName) ?: false
}

/**
 * CHECK IF A MODE IS A "NIGHT" MODE
 *
 * Returns true if the given mode name is one that the user configured as a
 * Night mode in settings.nightModes. This replaces hardcoded "Night" comparisons
 * so users can map any Hubitat mode (e.g., "Sleep", "Bedtime") to Night behavior.
 *
 * @param modeName The Hubitat location mode name to check
 * @return Boolean true if this mode should use Night temperatures
 */
private boolean isNightMode(String modeName) {
  return (settings.nightModes as List<String>)?.contains(modeName) ?: false
}

// =============================================================================
// CONTACT SENSOR HANDLING (Open Window/Door Detection & Energy Saving)
// =============================================================================
// These methods detect when windows or doors are open and can automatically
// turn off the thermostat to avoid wasting energy heating/cooling the outdoors

/**
 * CONTACT SENSOR EVENT HANDLER
 *
 * This method is called automatically whenever ANY configured contact sensor
 * changes state (opens or closes). Think of this as a "doorbell ringer" that
 * alerts the app "something just opened!" or "something just closed!"
 *
 * WHEN IT'S CALLED:
 * - User opens a window → sensor reports "open" → this method runs
 * - User closes a door → sensor reports "closed" → this method runs
 * - Happens for EVERY sensor configured in settings.contactSensors
 *
 * WHAT IT DOES (the logic flow):
 * 1. Logs which sensor changed and its new state (for debugging)
 * 2. Checks if open window detection is enabled (can be turned off in settings)
 * 3. If sensor OPENED: Immediately turns the thermostat off and schedules
 *    a retry after openWindowDuration minutes
 * 4. If sensor CLOSED: Checks if ALL sensors are closed, re-enables if yes
 *
 * THE RETRY STRATEGY:
 * - The thermostat is turned off immediately on any sensor-open event so
 *   the HVAC cannot keep running while a window/door is open, even briefly.
 * - openWindowDuration drives a scheduled retry call to
 *   disableThermostatDueToOpenWindow() as belt-and-suspenders in case the
 *   first off() command is lost to a transient Z-Wave / mesh issue.
 *
 * THE "OVERWRITE" TRICK:
 * - runIn(..., [overwrite: true]) means "cancel previous timer, start new one"
 * - Without this: Opening 3 windows = 3 separate retries stacked up
 * - With this: Opening 3 windows = 1 retry timer that keeps restarting
 *
 * PARAMETERS:
 * @param evt Event object containing information about what happened:
 *   - evt.device: Which sensor triggered this (e.g., "Kitchen Window")
 *   - evt.device.displayName: User-friendly name of the sensor
 *   - evt.value: New state as a String ("open" or "closed")
 */
void contactSensorEventHandler(Event evt) {
  // Log which sensor changed and its new value
  // evt.device.displayName = User-friendly name like "Kitchen Window"
  // evt.value = "open" or "closed"
  // ${...} inserts values into the string
  // This log appears if settings.debugLogEnable is true
  logDebug("contactSensorEventHandler: ${evt.device.displayName} is ${evt.value}")

  // Check if this feature is enabled in settings
  // settings.disableWithOpenWindowsOrDoors is the checkbox value
  // ! means "not" (reverses true/false)
  // So this reads: "if NOT enabled..."
  if (!disableWithOpenWindowsOrDoors) {
    // Feature is disabled - do nothing and exit immediately
    // return = stop executing this method
    return
  }
  // If we get here, the feature IS enabled (checkbox was checked)

  // ===== CASE 1: SENSOR OPENED =====
  if (evt.value == 'open') {
    // Turn the thermostat off immediately — no grace period. If the HVAC
    // is already running when a window opens, we do not want it to keep
    // running for the openWindowDuration window. initialize() already
    // uses this exact pattern when it detects an open sensor at startup.
    logInfo("${evt.device.displayName} opened; turning thermostat off")
    thermostat.off()
    state.disabledDueToOpenWindow = true

    // Retain the scheduled retry as belt-and-suspenders: re-asserts off()
    // after openWindowDuration minutes. Noop if the thermostat is already
    // off by then, but protects against the first off() call being lost
    // to a transient Z-Wave / mesh issue.
    runIn(openWindowDuration * 60, 'disableThermostatDueToOpenWindow', [overwrite: true])

    return
  }

  // ===== CASE 2: SENSOR CLOSED =====
  if (evt.value == 'closed') {
    // Check if ALL configured sensors are now closed. allContactSensorsClosed()
    // now uses currentValue('contact', true) (skipCache), so this reads fresh
    // values directly from Hubitat's attribute database rather than the
    // per-execution in-memory cache.
    if (allContactSensorsClosed()) {
      unschedule('disableThermostatDueToOpenWindow')
      logInfo('All windows/doors closed; restoring thermostat automation')

      // Apply any deferred Hubitat location-mode change first. handleModeChange
      // may or may not touch the thermostat depending on the destination mode
      // (Normal→Normal is a noop), so setThermostatAutoUnlessWindowOpen() below
      // handles the actual re-enable.
      String pending = state.pendingLocationMode
      if (pending) {
        state.pendingLocationMode = null
        logInfo("Applying deferred mode change to \"${pending}\"")
        applyModeFromHubitat(pending)
      }

      // Authoritative single path for enabling the thermostat: re-checks
      // anyContactSensorsOpen() (also skipCache) immediately before setting
      // auto, and owns state.disabledDueToOpenWindow end-to-end.
      setThermostatAutoUnlessWindowOpen()
    } else {
      logInfo('Another window/door remains open; keeping thermostat disabled')
    }
  }
}

/**
 * DISABLE THERMOSTAT DUE TO OPEN WINDOW
 *
 * Belt-and-suspenders retry invoked by runIn() from contactSensorEventHandler()
 * and initialize() after a sensor opens. The primary off() command is issued
 * immediately by those handlers — this method only re-asserts it after
 * openWindowDuration minutes in case that first command was lost to a
 * transient Z-Wave / mesh issue. A noop if the thermostat is already off.
 *
 * WHEN IT'S CALLED:
 * - Window/door opens → handler calls thermostat.off() AND schedules this
 *   method via runIn(openWindowDuration * 60, ...) → this runs N minutes later
 * - Will NOT be called if the window/door closes first and the close branch
 *   calls unschedule('disableThermostatDueToOpenWindow')
 *
 * WHAT IT DOES:
 * 1. Logs that it's re-asserting the disable (for troubleshooting)
 * 2. Calls thermostat.off() (stops all heating/cooling)
 *
 * WHEN USER CLOSES WINDOW:
 * - contactSensorEventHandler() runs (for the "closed" event)
 * - That method unschedules this retry and calls
 *   setThermostatAutoUnlessWindowOpen() to re-enable the thermostat
 *
 * SAFETY RE-CHECK:
 * Before turning off, this method verifies that:
 * 1. The open window feature is still enabled (user might have toggled it off)
 * 2. At least one window/door is still actually open (race condition guard)
 * If either check fails, the method exits without turning off the thermostat.
 */
void disableThermostatDueToOpenWindow() {
  // Safety re-check: verify the feature is still enabled and windows are still open.
  // A race condition could occur if a window closes just as the timer fires
  // but before the unschedule() in contactSensorEventHandler executes.
  if (!settings.disableWithOpenWindowsOrDoors || allContactSensorsClosed()) {
    logDebug('Open window timer fired but feature disabled or all windows now closed; skipping')
    return
  }

  // Log what we're doing (appears in logs if settings.logEnable is true)
  // This helps troubleshoot: "Why did my thermostat turn off?"
  logInfo('Disabling thermostat due to open window/door')

  // Turn the thermostat completely OFF
  // settings.thermostat = the thermostat device from configuration
  // .off() is a method that turns off the thermostat entirely
  // No heating, no cooling - complete shutdown
  thermostat.off()

  // Track that we disabled due to open window so mode changes won't
  // accidentally re-enable the thermostat while a window is still open
  state.disabledDueToOpenWindow = true
}

/**
 * LOG FRESH SENSOR STATES
 *
 * Walks every configured contact sensor and logs its current value using
 * currentValue(name, skipCache=true), which forces a fresh read from
 * Hubitat's attribute database. Called from initialize() so every install,
 * update, or app-save produces an explicit, up-to-date snapshot in the
 * logs. This gives us a reliable baseline for troubleshooting stale-cache
 * issues.
 */
private void logFreshSensorStates() {
  settings.contactSensors?.each { cs ->
    String val = cs.currentValue('contact', true)
    logInfo("Sensor state: ${cs.displayName} = ${val}")
  }
}

/**
 * ALL CONTACT SENSORS CLOSED
 *
 * Helper method that checks if ALL configured contact sensors are closed.
 * Returns true only if every single sensor reports "closed".
 * Think of this as answering: "Is it safe to turn on the thermostat?"
 *
 * HOW IT WORKS:
 * - Gets the list of contact sensors from settings
 * - Uses .every{} to check if ALL sensors meet a condition
 * - Condition: sensor's current 'contact' value equals 'closed'
 * - If ANY sensor is open, returns false immediately
 *
 * THE GROOVY MAGIC EXPLAINED:
 *
 * settings.contactSensors = list of sensor devices (might be null/empty)
 * ?. = "safe navigation operator" = only do this if not null
 * .every{ } = "check if ALL items pass this test"
 * cs -> = "for each sensor (call it 'cs'), do..."
 * cs.currentValue('contact', true) = get sensor's current contact value;
 *   the second argument is skipCache=true, which forces a fresh read from
 *   Hubitat's attribute database rather than the per-execution in-memory
 *   cache. Without skipCache the cache can lag when events fire in rapid
 *   succession, producing a false "all closed" result.
 * == 'closed' = check if it equals the string 'closed'
 * ?: true = "if the whole thing is null, return true instead"
 *
 * EDGE CASE:
 * - If no sensors are configured (null or empty list), returns true
 * - This makes sense: "Are all zero sensors closed?" = "Yes!"
 * - Allows app to work even if user doesn't configure any sensors
 * - The ?: operator (called "Elvis operator") provides this default
 *
 * RETURN VALUE:
 * @return Boolean (true or false)
 *   - true = All sensors are closed (or no sensors configured)
 *   - false = At least one sensor is open
 *
 * EXAMPLE:
 * - 3 sensors: Kitchen Window (closed), Front Door (closed), Back Door (closed)
 *   → Returns: true (all closed)
 * - 3 sensors: Kitchen Window (OPEN), Front Door (closed), Back Door (closed)
 *   → Returns: false (one is open)
 * - 0 sensors: (none configured)
 *   → Returns: true (no sensors to check)
 */
private boolean allContactSensorsClosed() {
  // This is a one-line method with complex Groovy syntax
  // Let's break it down step by step:

  // settings.contactSensors
  //   = list of contact sensor devices from configuration
  //   = might be null if user didn't select any sensors
  //   = might be empty list [] if user cleared selection
  //   = might be list of sensors [sensor1, sensor2, sensor3]

  // ?.every{ }
  //   = "safe navigation" - only runs if contactSensors is not null
  //   = .every{ } checks if ALL items pass a test
  //   = { cs -> ... } is a closure (anonymous function)
  //     - cs = current sensor being tested

  // cs.currentValue('contact', true)
  //   = gets the current value of the sensor's 'contact' attribute
  //   = returns 'open' or 'closed' as a String
  //   = the second argument (skipCache=true) forces a fresh read from
  //     Hubitat's attribute database instead of the per-execution cache;
  //     the cached value can lag behind reality when multiple events fire
  //     in quick succession, producing a false "all closed" result.

  // == 'closed'
  //   = checks if the value equals the string 'closed'
  //   = returns true if closed, false if open

  // ?: true
  //   = "Elvis operator" - provides a default value
  //   = if the whole expression before ?: is null, return true
  //   = this handles the case where no sensors are configured

  return settings.contactSensors?.every { cs -> cs.currentValue('contact', true) == 'closed' } ?: true

  // EQUIVALENT CODE (more verbose but easier to understand):
  // if (settings.contactSensors == null || settings.contactSensors.isEmpty()) {
  //   return true  // No sensors configured - consider "all closed"
  // }
  // for (sensor in settings.contactSensors) {
  //   if (sensor.currentValue('contact', true) != 'closed') {
  //     return false  // Found an open sensor - not all closed
  //   }
  // }
  // return true  // Checked all sensors, all are closed
}

/**
 * ANY CONTACT SENSORS OPEN
 *
 * Helper method that checks if ANY configured contact sensors are open.
 * Returns true if at least one sensor reports "open".
 * This is the opposite of allContactSensorsClosed() - useful for different logic.
 *
 * HOW IT WORKS:
 * - Gets the list of contact sensors from settings
 * - Uses .any{} to check if ANY sensor meets a condition
 * - Condition: sensor's current 'contact' value equals 'open'
 * - If ALL sensors are closed, returns false
 *
 * THE GROOVY MAGIC (similar to allContactSensorsClosed):
 *
 * settings.contactSensors = list of sensor devices
 * ?. = "safe navigation operator"
 * .any{ } = "check if ANY item passes this test" (opposite of .every)
 * cs -> = "for each sensor"
 * cs.currentValue('contact', true) = get sensor's current value, with
 *   skipCache=true so the read comes from Hubitat's attribute database
 *   rather than the per-execution cache (see allContactSensorsClosed()
 *   for the full rationale).
 * == 'open' = check if it equals 'open'
 * ?: false = "if null, return false instead"
 *
 * EDGE CASE:
 * - If no sensors configured, returns false
 * - This makes sense: "Are any of zero sensors open?" = "No!"
 *
 * RETURN VALUE:
 * @return Boolean (true or false)
 *   - true = At least one sensor is open
 *   - false = All sensors are closed (or no sensors configured)
 *
 * USAGE:
 * - initialize() checks this at startup to decide whether to defer mode
 *   application and turn the thermostat off.
 * - locationModeChangeHandler() uses it to defer mode changes while a
 *   window/door is open.
 * - setThermostatAutoUnlessWindowOpen() uses it as the guard that
 *   prevents the thermostat from being enabled with a sensor open.
 * - disableThermostatDueToOpenWindow() and thermostatEventHandler()
 *   both use it as their re-check gate.
 */
boolean anyContactSensorsOpen() {
  // Similar to allContactSensorsClosed() but uses .any{} and checks for 'open'

  // .any{ } returns true if AT LEAST ONE item passes the test
  // Stops checking as soon as it finds a match (efficient)

  // skipCache=true — see allContactSensorsClosed() for the rationale.
  return settings.contactSensors?.any { cs -> cs.currentValue('contact', true) == 'open' } ?: false

  // EQUIVALENT CODE:
  // if (settings.contactSensors == null || settings.contactSensors.isEmpty()) {
  //   return false  // No sensors configured
  // }
  // for (sensor in settings.contactSensors) {
  //   if (sensor.currentValue('contact', true) == 'open') {
  //     return true  // Found an open sensor
  //   }
  // }
  // return false  // Checked all sensors, none are open
}

/**
 * SET THERMOSTAT TO AUTO UNLESS WINDOW IS OPEN
 *
 * Helper method that sets the thermostat to auto mode, but first checks
 * whether any contact sensors report a window/door as open. If the open
 * window feature is enabled and any sensor is open, the thermostat is
 * turned OFF instead of auto to avoid heating/cooling the outdoors.
 *
 * This method should be used in place of raw thermostat.auto() calls
 * anywhere the thermostat mode is being set as part of setpoint changes
 * (mode transitions, setpoint restoration, etc.).
 *
 * WHEN WINDOW IS OPEN:
 * - Keeps thermostat off (or turns it off)
 * - Sets state.disabledDueToOpenWindow = true
 * - Starts the open window disable timer so that if the timer hasn't
 *   fired yet (e.g., window just opened), it will still fire
 *
 * WHEN ALL WINDOWS ARE CLOSED:
 * - Sets thermostat to auto mode as normal
 * - Clears state.disabledDueToOpenWindow
 */
private void setThermostatAutoUnlessWindowOpen() {
  if (settings.disableWithOpenWindowsOrDoors && anyContactSensorsOpen()) {
    logInfo('Window/door is open; keeping thermostat disabled instead of setting to auto')
    thermostat.off()
    state.disabledDueToOpenWindow = true
    return
  }
  thermostat.auto()
  state.disabledDueToOpenWindow = false
}

// =============================================================================
// MODE COORDINATION (The Brain of the App - Away/Night/Normal Logic)
// =============================================================================
// This is the most complex and important part of the app. These methods handle
// transitions between Home, Away, and Night modes while carefully preserving
// your temperature preferences. This is where the "smart but respectful" logic lives.

/**
 * LOCATION MODE CHANGE HANDLER
 *
 * This method is called automatically whenever Hubitat's location mode changes.
 * Think of this as the app's "ears" listening for announcements like:
 * "Everyone just left!" (Home → Away)
 * "Someone arrived home!" (Away → Home)
 * "It's bedtime!" (Home → Night)
 *
 * WHEN IT'S CALLED:
 * - User manually changes Hubitat mode
 * - Mode changes automatically (time of day, presence sensors, etc.)
 * - Hubitat Rule Machine or other apps change the mode
 *
 * WHAT IT DOES:
 * 1. Receives notification that mode changed (new mode in evt.value)
 * 2. Logs the change (for troubleshooting)
 * 3. Uses isAwayMode()/isNightMode() helpers to classify the new mode
 * 4. Delegates ALL mode handling to handleModeChange() (single code path)
 *
 * MODE CATEGORIES (user-configurable via settings):
 * - Away modes (settings.awayModes): Apply energy-saving temps
 * - Night modes (settings.nightModes): Apply sleep temps
 * - Everything else: Normal mode (restore user's preferred temps)
 *
 * PARAMETERS:
 * @param evt Event object containing:
 *   - evt.value: New mode name as a String (e.g., "Away", "Night", "Home", "Day")
 */
void locationModeChangeHandler(Event evt) {
  String mode = evt.value
  logDebug("locationModeChangeHandler: Location mode changed to ${mode}")

  // If a window/door is open, defer the entire mode change until it closes.
  // There's no point in saving/restoring setpoints or re-classifying the mode
  // while the HVAC is supposed to stay off anyway, and doing so risks writing
  // setpoints whose side effects wake the HVAC up. contactSensorEventHandler()
  // applies the stored pendingLocationMode when all windows close.
  if (settings.disableWithOpenWindowsOrDoors && anyContactSensorsOpen()) {
    logInfo("Window/door open; deferring mode change to \"${mode}\" until all windows close")
    state.pendingLocationMode = mode
    return
  }

  state.pendingLocationMode = null
  applyModeFromHubitat(mode)
}

// Classify the raw Hubitat location mode and dispatch to handleModeChange().
// Shared by locationModeChangeHandler(), initialize(), and the open-window
// close handler so the classification logic lives in exactly one place.
private void applyModeFromHubitat(String mode) {
  if (isAwayMode(mode)) {
    handleModeChange('Away', awayModeVirtualThermostat)
  } else if (isNightMode(mode)) {
    handleModeChange('Night', nightModeVirtualThermostat)
  } else {
    handleModeChange(mode)
  }
}

/**
 * HANDLE MODE CHANGE (The Master Coordinator)
 *
 * This is the "master brain" method that coordinates all mode transitions.
 * It manages the complex logic of saving, restoring, and applying temperature
 * setpoints while respecting manual user changes. Both locationModeChangeHandler()
 * and initialize() delegate to this single method, keeping all mode-transition
 * logic in one place.
 *
 * CALLERS:
 * - locationModeChangeHandler() classifies the mode using isAwayMode()/isNightMode()
 *   and passes "Away" or "Night" as modeName (with the virtual thermostat), or the
 *   raw mode name for normal modes (with no virtual thermostat).
 * - initialize() does the same classification at startup for the current mode.
 *
 * THE SOLUTION - The state.setpointsRestored FLAG:
 * This boolean flag is the KEY to the entire system:
 *
 * state.setpointsRestored = TRUE means:
 * - Currently using normal/preferred temperatures
 * - No temperatures are saved in memory yet
 * - If mode changes to Away/Night, SHOULD save current temps first
 *
 * state.setpointsRestored = FALSE means:
 * - Currently using Away or Night mode temperatures
 * - Normal temperatures ARE saved in memory
 * - If mode changes to Away/Night again, should NOT save (already saved!)
 * - If mode returns to Normal, SHOULD restore saved temps
 *
 * FIRST-INSTALL GUARD (state.initializedBefore):
 * On the very first install, if the hub is already in an Away/Night mode,
 * the thermostat may be showing Away/Night temps from manual setup. Saving
 * those as "normal" temps would be incorrect. When initializedBefore is false,
 * the method applies the virtual thermostat setpoints but skips saving.
 * The flag is set to true the first time a Normal mode is entered.
 *
 * WALKTHROUGH EXAMPLE:
 * ═══════════════════════════════════════════════════════════════════════════
 * Action                 | setpointsRestored | Memory      | Current Temps
 * ═══════════════════════════════════════════════════════════════════════════
 * Start in Normal mode   | TRUE              | (empty)     | 70°F / 75°F
 * Leave home (→ Away)    | FALSE             | 70°F / 75°F | 65°F / 80°F ✓
 * Bedtime (Away → Night) | FALSE             | 70°F / 75°F | 68°F / 72°F ✓
 * Return home (→ Home)   | TRUE              | (cleared)   | 70°F / 75°F ✓
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * @param modeName Logical mode category: "Away", "Night", or the raw Hubitat
 *                 mode name for normal modes (e.g., "Home", "Day")
 * @param modeThermostat Virtual thermostat device with setpoints for this mode
 *                       (null for normal modes that don't use virtual thermostats)
 */
private void handleModeChange(String modeName, DeviceWrapper modeThermostat=null) {
  // ===== CASE 1: ENTERING AWAY MODE =====
  if (modeName == 'Away') {
    logInfo("Location entered Away mode (\"${location.getMode()}\")")

    if (state.setpointsRestored == true) {
      // We're in normal mode - need to save current temps before changing.
      // Guard: on first install in Away/Night, the thermostat may already show
      // Away/Night temps from manual setup. Saving those as "normal" would be
      // wrong. Skip the save and just apply the virtual thermostat setpoints.
      if (state.initializedBefore == false) {
        logWarn('First initialization in Away mode; skipping setpoint save (thermostat may not have normal temps)')
        applyVirtualThermostatSetpoints(modeThermostat, modeName)
      } else if (storeCurrentSetpoints()) {
        applyVirtualThermostatSetpoints(modeThermostat, modeName)
      }
      // If storeCurrentSetpoints() failed, thermostat stays at current temps (safe failure)
    } else {
      // Already in Away or Night — normal temps already saved, just apply new setpoints
      applyVirtualThermostatSetpoints(modeThermostat, modeName)
    }

    return
  }

  // ===== CASE 2: ENTERING NIGHT MODE =====
  if (modeName == 'Night') {
    logInfo("Location entered Night mode (\"${location.getMode()}\")")

    if (state.setpointsRestored == true) {
      // Same first-install guard as Away mode above
      if (state.initializedBefore == false) {
        logWarn('First initialization in Night mode; skipping setpoint save (thermostat may not have normal temps)')
        applyVirtualThermostatSetpoints(modeThermostat, modeName)
      } else if (storeCurrentSetpoints()) {
        applyVirtualThermostatSetpoints(modeThermostat, modeName)
      }
    } else {
      applyVirtualThermostatSetpoints(modeThermostat, modeName)
    }

    return
  }

  // ===== CASE 3: ENTERING NORMAL MODE (anything other than Away or Night) =====
  // If we get here, modeName is something like "Home", "Day", "Evening", etc.

  // Mark that we've now been through a normal mode at least once.
  // After this, future Away/Night transitions will correctly save setpoints.
  state.initializedBefore = true

  if (state.setpointsRestored == false) {
    // We have saved setpoints from Away or Night mode — time to restore
    logInfo("Location entered \"${modeName}\" mode (was ${state.currentOperatingState}); restoring previous setpoints")

    state.currentOperatingState = 'Normal'
    restoreStoredSetpoints()
  }
}

/**
 * STORE CURRENT SETPOINTS (Save Normal Temperatures)
 *
 * This method captures the current heating and cooling setpoints from the
 * real thermostat and saves them to memory. This happens just before entering
 * Away or Night mode, so we can restore these "normal" temperatures later.
 *
 * WHAT IT DOES:
 * 1. Reads current heating setpoint (e.g., 70°F)
 * 2. Reads current cooling setpoint (e.g., 75°F)
 * 3. Saves both values to memory (state object)
 * 4. Marks that setpoints are NOT restored (flag = false)
 * 5. Updates operating state to Away or Night
 *
 * BIGDECIMAL EXPLAINED:
 * BigDecimal is a special number type for precise decimal values.
 * We use it because:
 * - Temperatures need precision: 72.5°F, not just 72°F or 73°F
 * - Regular numbers can have rounding errors
 * - BigDecimal guarantees accuracy for money and measurements
 *
 * NULL CHECK:
 * "== null" checks if the thermostat failed to report a value
 * This prevents errors if the thermostat is offline or not responding
 *
 * Example:
 *   heatingSetpoint = 70.5  → saves 70.5 (success)
 *   heatingSetpoint = null  → returns false, logs warning (failure)
 *
 * MEMORY STORAGE:
 * state.setPointLow = heating setpoint stored in memory (persists)
 * state.setPointHigh = cooling setpoint stored in memory (persists)
 * These values survive hub reboots and app updates
 *
 * THE FLAG:
 * state.setpointsRestored = false means "saved but not restored yet"
 * This prevents handleModeChange from re-saving if we jump between
 * Away and Night modes
 *
 * RETURN VALUE:
 * Returns true if save succeeded (both values exist)
 * Returns false if save failed (either value is null)
 *
 * @return Boolean true if setpoints saved successfully, false if error
 */
private boolean storeCurrentSetpoints() {
  // Read current heating setpoint from the real thermostat
  // thermostat = the physical thermostat device (shortcut to settings.theThermostat)
  // .currentValue('heatingSetpoint') = get current heating target temperature
  // as BigDecimal = convert to precise decimal number type
  BigDecimal low = thermostat.currentValue('heatingSetpoint') as BigDecimal

  // Read current cooling setpoint from the real thermostat
  // Same pattern as heating - get the cooling target temperature
  BigDecimal high = thermostat.currentValue('coolingSetpoint') as BigDecimal

  // Check if EITHER value is null (missing/invalid)
  // || = "or" operator (if either is true, the whole condition is true)
  // If this is true, we have a problem and can't proceed
  if (low == null || high == null) {
    // One or both values are missing - cannot save

    // Log a warning so user knows something went wrong
    // This might indicate thermostat communication problems
    logWarn('Unable to capture current setpoints; heating or cooling value missing')

    // Return false = failure
    // handleModeChange will see this false and not proceed with mode change
    // This is a safety feature - don't enter Away mode if we can't save normal temps
    return false
  }

  // If we get here, both values are valid numbers

  // Save the heating setpoint to memory
  // state.setPointLow = persistent storage that survives reboots
  // "Low" because heating is the lower temperature limit
  state.setPointLow = low

  // Save the cooling setpoint to memory
  // "High" because cooling is the upper temperature limit
  state.setPointHigh = high

  // Mark that we have saved setpoints that haven't been restored yet
  // false = "setpoints are saved in memory but NOT restored to thermostat"
  // This flag is KEY to the entire system's logic
  state.setpointsRestored = false

  // Return true = success!
  // handleModeChange will see this true and proceed with applying Away/Night temps
  return true
}

/**
 * APPLY VIRTUAL THERMOSTAT SETPOINTS (Use Away/Night Temperatures)
 *
 * This method takes the temperature setpoints from a "virtual thermostat"
 * and applies them to the real physical thermostat. This is how we actually
 * change the thermostat to Away or Night temperatures.
 *
 * VIRTUAL THERMOSTAT CONCEPT:
 * A virtual thermostat is not a real device - it's a "fake" thermostat device
 * created in Hubitat that only stores temperature preferences. Think of it as
 * a notepad where you write down what temperatures you want for Away or Night mode.
 *
 * Why use a virtual thermostat?
 * - Easy to configure through Hubitat's device interface
 * - Stores Away/Night preferences independently
 * - Can be controlled by other automations/apps
 * - Provides a consistent interface (looks like a real thermostat)
 *
 * WHAT IT DOES:
 * 1. Check if virtual thermostat exists (user might not have configured one)
 * 2. Read heating setpoint from virtual thermostat (e.g., 65°F for Away)
 * 3. Read cooling setpoint from virtual thermostat (e.g., 80°F for Away)
 * 4. Apply heating setpoint to REAL thermostat
 * 5. Apply cooling setpoint to REAL thermostat
 * 6. Set real thermostat to AUTO mode
 * 7. Update our operating state to match (Away or Night)
 *
 * TYPE CONVERSION:
 * BigDecimal → Double conversion is required because:
 * - Virtual thermostat reports BigDecimal (precise decimal)
 * - Real thermostat expects Double (standard decimal)
 * - toDouble() converts between the two
 * ?. = safe navigation operator (if value is null, result is null)
 *
 * AUTO MODE:
 * thermostat.auto() sets the thermostat to automatic mode
 * This means it can switch between heating and cooling as needed
 * Alternative modes: heat only, cool only, off
 *
 * PARAMETERS:
 * @param modeThermostat The virtual thermostat device to copy setpoints from
 * @param modeName The mode name ("Away" or "Night") for logging and state tracking
 */
private void applyVirtualThermostatSetpoints(DeviceWrapper modeThermostat, String modeName) {
  if (modeThermostat == null) {
    logWarn("No virtual thermostat configured for ${modeName} mode")
    return
  }

  Double heat = (modeThermostat.currentValue('heatingSetpoint') as BigDecimal)?.toDouble()
  Double cool = (modeThermostat.currentValue('coolingSetpoint') as BigDecimal)?.toDouble()

  if (heat != null) {
    thermostat.setHeatingSetpoint(heat)
  }
  if (cool != null) {
    thermostat.setCoolingSetpoint(cool)
  }

  setThermostatAutoUnlessWindowOpen()
  state.currentOperatingState = modeName
}


/**
 * RESTORE STORED SETPOINTS (Return to Normal Temperatures)
 *
 * This method retrieves the saved "normal" temperature setpoints from memory
 * and applies them back to the real thermostat. This happens when exiting
 * Away or Night mode and returning to a normal mode like "Home" or "Day".
 *
 * WHAT IT DOES:
 * 1. Retrieve saved heating setpoint from memory (e.g., 70°F)
 * 2. Retrieve saved cooling setpoint from memory (e.g., 75°F)
 * 3. Verify both values exist (safety check)
 * 4. Apply heating setpoint to real thermostat
 * 5. Apply cooling setpoint to real thermostat
 * 6. Mark setpoints as restored (flag = true)
 * 7. Set thermostat to AUTO mode
 * 8. Update operating state to Normal
 *
 * THE RESTORATION PROCESS:
 * When you left home (Normal → Away):
 *   storeCurrentSetpoints() saved: heat=70°F, cool=75°F
 *   Then applied Away temps: heat=65°F, cool=80°F
 *
 * When you return home (Away → Normal):
 *   restoreStoredSetpoints() retrieves: heat=70°F, cool=75°F
 *   Then applies those original temps back to thermostat
 *
 * MEMORY RETRIEVAL:
 * state.setPointLow = the saved heating setpoint
 * state.setPointHigh = the saved cooling setpoint
 * as BigDecimal = convert from storage to precise decimal type
 *
 * WHY CHECK FOR NULL:
 * If setpoints were never saved (unusual), both would be null
 * This could happen if:
 * - App just installed and user is in Away mode
 * - State was cleared/reset
 * - Storage failed for some reason
 *
 * THE FLAG:
 * state.setpointsRestored = true means "back to normal, no saved temps"
 * This tells handleModeChange that next time we enter Away/Night,
 * it should save the current temps (they're the new "normal")
 *
 * TYPE CONVERSION:
 * BigDecimal.toDouble() converts from storage format to thermostat format
 * - BigDecimal = precise, used for storage
 * - Double = standard, used by thermostat commands
 */
private void restoreStoredSetpoints() {
  BigDecimal low = state.setPointLow as BigDecimal
  BigDecimal high = state.setPointHigh as BigDecimal

  if (low == null || high == null) {
    logWarn('No stored setpoints available to restore')
    return
  }

  thermostat.setHeatingSetpoint(low.toDouble())
  thermostat.setCoolingSetpoint(high.toDouble())
  state.setpointsRestored = true
  setThermostatAutoUnlessWindowOpen()
  state.currentOperatingState = 'Normal'
}

// =============================================================================
// Telemetry & Future Hooks
// =============================================================================

/**
 * THERMOSTAT EVENT HANDLER (Future Expansion)
 *
 * This method is called whenever the thermostat device reports an event.
 * Currently it only logs the event for debugging purposes.
 *
 * WHAT EVENTS ARE CAPTURED:
 * Any change reported by the thermostat device, such as:
 * - Temperature changes
 * - Setpoint changes
 * - Mode changes (heat/cool/auto)
 * - System state changes (heating/cooling/idle)
 *
 * CURRENT PURPOSE:
 * Debugging and monitoring - helps troubleshoot issues
 * You can see in logs when thermostat reports changes
 *
 * FUTURE USE:
 * This is a "hook" for future enhancements, such as:
 * - Detecting manual user changes to setpoints
 * - Responding to temperature changes
 * - Tracking heating/cooling patterns
 * - Advanced automation based on thermostat state
 *
 * THE EVENT OBJECT:
 * evt.device.displayName = name of the thermostat (e.g., "Living Room Thermostat")
 * evt.type = type of event (e.g., "attribute change")
 * evt.value = new value that was reported (e.g., "72" for temperature)
 *
 * LOG LEVEL:
 * logDebug = only shows if settings.debugLogEnable is turned on
 * This prevents cluttering logs with constant updates
 *
 * @param evt Event object containing device, attribute, and value information
 */
void thermostatEventHandler(Event evt) {
  // evt.name is the attribute that changed (e.g. "thermostatOperatingState");
  // evt.type is the event source/type and is frequently null on device
  // attribute events — that's why the original bug report showed
  // "null: cooling". Log evt.name for clarity.
  logDebug("thermostatEventHandler: ${evt.device.displayName}, ${evt.name}: ${evt.value}")

  // Reactive safety net. If the open-window protection is disabled, there
  // is nothing to react to.
  if (!settings.disableWithOpenWindowsOrDoors) { return }

  // Gate on evt.name before doing any sensor reads. The thermostat fires
  // many events we do not care about here (temperature, humidity, setpoint
  // reports) and each one would otherwise trigger a skipCache=true DB read
  // across every configured contact sensor. Only operating-state and mode
  // transitions can cause HVAC to run.
  if (evt.name != 'thermostatOperatingState' && evt.name != 'thermostatMode') { return }

  // Now that we know this event is one we might act on, check whether any
  // sensor is currently open.
  if (!anyContactSensorsOpen()) { return }

  // Active operating states: Hubitat reports 'pending cool' / 'pending heat'
  // before the compressor engages. Catching them lets us force the thermostat
  // off before the HVAC actually starts.
  boolean operatingActive = (evt.name == 'thermostatOperatingState' &&
                              evt.value in ['cooling', 'heating', 'pending cool', 'pending heat'])

  // Any thermostatMode other than 'off' could let the thermostat's own
  // temperature logic activate HVAC. Reverse it immediately.
  boolean modeWouldRun = (evt.name == 'thermostatMode' && evt.value != 'off')

  if (operatingActive || modeWouldRun) {
    logWarn("Window/door open but thermostat reported ${evt.name}=${evt.value}; forcing off")
    thermostat.off()
    state.disabledDueToOpenWindow = true
  }
}