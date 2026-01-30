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
 *  2. The app watches Hubitat's location modes (Home, Away, Night, etc.)
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
 *  │ 2. App starts a countdown timer (default: 3 minutes)                │
 *  │ 3. If window still open when timer expires:                         │
 *  │    - App turns thermostat completely OFF                            │
 *  │    - Stops heating/cooling to avoid wasting energy                  │
 *  │ 4. When window closes (and all other windows are closed):          │
 *  │    - App cancels timer (if still counting)                          │
 *  │    - App turns thermostat back to "auto" mode                       │
 *  │ 5. Why wait 3 minutes? So briefly opening a door doesn't trigger   │
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
 *  BEHAVIOR SETTINGS:
 *
 *  disableWithOpenWindowsOrDoors (TRUE or FALSE):
 *  - TRUE = Turn off thermostat when any window/door is open
 *  - FALSE = Ignore open windows/doors (not recommended, wastes energy)
 *  - Default: TRUE
 *
 *  openWindowDuration (number of minutes, 1-30):
 *  - How long to wait before turning off thermostat for open window
 *  - Default: 3 minutes
 *  - Why wait? Prevents turning off for brief door openings (getting mail, etc.)
 *  - Longer = less sensitive, Shorter = more aggressive energy saving
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
 *
 *  DATA TYPES:
 *  - BigDecimal for temperatures (precise decimal math)
 *  - Boolean for flags (TRUE/FALSE values)
 *  - String for mode names ("Away", "Night", "Normal")
 *
 *  EVENT HANDLING:
 *  - subscribe() registers event handlers (callbacks)
 *  - Handlers receive Event object with device and value info
 *
 *  ERROR HANDLING:
 *  - Null checks prevent crashes if sensors/settings missing
 *  - Graceful degradation (continues working even if something fails)
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

    // ===== SECTION 2: OPEN WINDOW/DOOR SETTINGS =====
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

      // Number input: how many minutes to wait before turning off
      // 'number' = only accepts numeric input
      // range: '1..30' = must be between 1 and 30 (Hubitat enforces this)
      // Why wait? So briefly opening a door doesn't trigger the automation
      input (
        'openWindowDuration',
        'number',
        title: 'Duration of minutes to wait before disabling thermostat',
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
 *
 * WHAT IT DOES:
 * 1. Cancels all existing event subscriptions (stops listening to devices)
 * 2. Calls initialize() to set everything up fresh from scratch
 *
 * WHY IT EXISTS:
 * - Provides a "clean slate" if settings change or things get stuck
 * - Prevents duplicate event handlers from accumulating
 * - Like rebooting your computer when it's acting weird
 *
 * WHEN YOU MIGHT USE IT:
 * - Changed which thermostat the app controls
 * - Added/removed contact sensors
 * - Things aren't working and you want to reset
 *
 * TECHNICAL NOTE:
 * - unsubscribe() = "stop listening to all device events"
 * - This prevents the app from receiving duplicate notifications
 */
void configure() {
  // Stop listening to all device events
  // This clears out any old subscriptions that might be stale
  unsubscribe()

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

  // STEP 5: Handle the current mode immediately
  // Don't wait for the next mode change - act now!

  // Get the current location mode as a String
  // Example: "Home", "Away", "Night", "Day", etc.
  String mode = location.getMode()

  // Log which mode we're currently in (for troubleshooting)
  // logDebug() only logs if settings.debugLogEnable is true
  // ${...} inserts the mode value into the string
  logDebug("Active location mode: ${location.getMode()}")

  // Check which mode we're in and apply appropriate settings
  // This uses if-else logic to determine what to do

  if (mode == 'Away') {
    // Currently in Away mode - apply Away temperatures now
    // settings.awayModeVirtualThermostat = the virtual thermostat for Away mode
    // handleModeChange() does the actual work of applying temperatures
    handleModeChange('Away', awayModeVirtualThermostat)

  } else if (mode == 'Night') {
    // Currently in Night mode - apply Night temperatures now
    // settings.nightModeVirtualThermostat = the virtual thermostat for Night mode
    handleModeChange('Night', nightModeVirtualThermostat)

  } else {
    // Some other mode (Home, Day, etc.) - treat as Normal mode
    // location.getMode() returns the actual mode name (might be custom)
    // null = no virtual thermostat needed for Normal mode
    handleModeChange(location.getMode())
  }
  // After initialize() completes, the app is fully set up and running
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
 *   - No temperatures are saved in memory yet
 *   - This is the default starting state
 * - FALSE = Currently using Away/Night temperatures
 *   - Normal temperatures ARE saved in memory
 *   - Will need to restore them when returning to Normal mode
 * - Default: true (assume we start in normal mode)
 * - Purpose: THE CRITICAL FLAG (see header documentation for details)
 *
 * state.currentOperatingState (String: "Normal", "Away", or "Night"):
 * - Tracks which mode the app thinks it's currently in
 * - Default: "Normal" (home and awake)
 * - Purpose: Helps with debugging and potential future features
 * - Example: If something goes wrong, you can check logs to see mode
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

  // After this method, both state variables are guaranteed to exist
  // Either with their existing values (if already set) or defaults (if new)
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
 * 3. If sensor OPENED: Starts a countdown timer to disable thermostat
 * 4. If sensor CLOSED: Checks if ALL sensors are closed, re-enables if yes
 *
 * THE TIMER STRATEGY:
 * - Opening a door briefly shouldn't trigger the automation
 * - Example: Getting mail, letting pet out, bringing in groceries
 * - Timer gives a "grace period" (default: 3 minutes from settings)
 * - If door closes before timer expires, thermostat stays on
 * - This prevents annoying on/off cycling for brief openings
 *
 * THE "OVERWRITE" TRICK:
 * - runIn(..., [overwrite: true]) means "cancel previous timer, start new one"
 * - Without this: Opening 3 windows = 3 separate timers = chaos
 * - With this: Opening 3 windows = 1 timer that keeps restarting
 * - Result: thermostat turns off 3 minutes after the LAST window opens
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
    // A window or door just opened

    // Start a countdown timer to disable the thermostat
    // settings.openWindowDuration = number of minutes from configuration
    // * 60 converts minutes to seconds (runIn uses seconds)
    // Example: 3 minutes * 60 = 180 seconds

    // runIn(seconds, 'methodName', options) schedules a method to run later
    // - First parameter: how many seconds to wait (180 = 3 minutes)
    // - Second parameter: name of method to call (as a String)
    // - Third parameter: options Map
    //   - [overwrite: true] = cancel any existing timer with same name
    runIn(openWindowDuration * 60, 'disableThermostatDueToOpenWindow', [overwrite: true])

    // Exit immediately - nothing else to do for "open" events
    return
  }

  // ===== CASE 2: SENSOR CLOSED =====
  if (evt.value == 'closed') {
    // A window or door just closed

    // Check if ALL configured sensors are now closed
    // allContactSensorsClosed() returns true only if EVERY sensor is closed
    if (allContactSensorsClosed()) {
      // All windows/doors are closed - safe to re-enable thermostat

      // Cancel the countdown timer (if it's still running)
      // unschedule('methodName') stops a scheduled method from running
      // If the timer already expired and method ran, this does nothing (harmless)
      unschedule('disableThermostatDueToOpenWindow')

      // Log what we're doing (appears in logs if settings.logEnable is true)
      logInfo('All windows/doors closed; restoring thermostat automation')

      // Set thermostat back to "auto" mode (can heat or cool as needed)
      // settings.thermostat = the thermostat device from configuration
      // .auto() is a method that sets the thermostat to automatic mode
      // Note: This doesn't change temperature setpoints, just enables operation
      thermostat.auto()
    } else {
      // At least one window/door is still open
      // Keep thermostat disabled for now, just log the information
      logInfo('Another window/door remains open; keeping thermostat disabled')
    }
    // No need for return here - it's the last case anyway
  }
  // Method ends here - all possible cases handled
}

/**
 * DISABLE THERMOSTAT DUE TO OPEN WINDOW
 *
 * This method is called by the countdown timer (runIn) if a window or door
 * remains open for the full configured duration. Think of it as the "timeout
 * handler" - the timer expired, so now take action.
 *
 * WHEN IT'S CALLED:
 * - Window/door opens → 3 minute timer starts → timer expires → this runs
 * - Will NOT be called if window closes before timer expires
 * - Will NOT be called if unschedule() cancels the timer
 *
 * WHAT IT DOES:
 * 1. Logs that it's disabling the thermostat (for troubleshooting)
 * 2. Turns the thermostat completely OFF (stops all heating/cooling)
 *
 * WHY TURN OFF (instead of just lowering temperature)?
 * - Completely stops wasting energy on heating/cooling the outdoors
 * - Provides clear indication that something is wrong (open window)
 * - Forces user to address the issue (close the window)
 * - More aggressive energy saving than just adjusting setpoints
 *
 * WHEN USER CLOSES WINDOW:
 * - contactSensorEventHandler() runs (for the "closed" event)
 * - That method calls thermostat.auto() to turn it back on
 * - Thermostat resumes normal operation automatically
 */
void disableThermostatDueToOpenWindow() {
  // Log what we're doing (appears in logs if settings.logEnable is true)
  // This helps troubleshoot: "Why did my thermostat turn off?"
  logInfo('Disabling thermostat due to open window/door')

  // Turn the thermostat completely OFF
  // settings.thermostat = the thermostat device from configuration
  // .off() is a method that turns off the thermostat entirely
  // No heating, no cooling - complete shutdown
  thermostat.off()

  // That's it! The thermostat is now off and will stay off until:
  // - All windows/doors are closed
  // - contactSensorEventHandler() turns it back on with .auto()
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
 * cs.currentValue('contact') = get sensor's current contact value
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

  // cs.currentValue('contact')
  //   = gets the current value of the sensor's 'contact' attribute
  //   = returns 'open' or 'closed' as a String

  // == 'closed'
  //   = checks if the value equals the string 'closed'
  //   = returns true if closed, false if open

  // ?: true
  //   = "Elvis operator" - provides a default value
  //   = if the whole expression before ?: is null, return true
  //   = this handles the case where no sensors are configured

  return settings.contactSensors?.every { cs -> cs.currentValue('contact') == 'closed' } ?: true

  // EQUIVALENT CODE (more verbose but easier to understand):
  // if (settings.contactSensors == null || settings.contactSensors.isEmpty()) {
  //   return true  // No sensors configured - consider "all closed"
  // }
  // for (sensor in settings.contactSensors) {
  //   if (sensor.currentValue('contact') != 'closed') {
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
 * cs.currentValue('contact') = get sensor's current value
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
 * Currently not used by the app, but provided for potential future features.
 * Could be used to check "should I disable something because a window is open?"
 */
boolean anyContactSensorsOpen() {
  // Similar to allContactSensorsClosed() but uses .any{} and checks for 'open'

  // .any{ } returns true if AT LEAST ONE item passes the test
  // Stops checking as soon as it finds a match (efficient)

  return settings.contactSensors?.any { cs -> cs.currentValue('contact') == 'open' } ?: false

  // EQUIVALENT CODE:
  // if (settings.contactSensors == null || settings.contactSensors.isEmpty()) {
  //   return false  // No sensors configured
  // }
  // for (sensor in settings.contactSensors) {
  //   if (sensor.currentValue('contact') == 'open') {
  //     return true  // Found an open sensor
  //   }
  // }
  // return false  // Checked all sensors, none are open
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
 * 3. Determines if this is a "special" mode (Away or Night) or normal mode
 * 4. Delegates to handleModeChange() to do the actual temperature adjustments
 *
 * MODE CATEGORIES:
 * - "Away" = Special mode (apply energy-saving temps from Away virtual thermostat)
 * - "Night" = Special mode (apply sleep temps from Night virtual thermostat)
 * - Everything else = Normal mode (restore user's preferred temps)
 *
 * SPECIAL CASE - Restoring Setpoints:
 * When exiting Away or Night mode to return to normal, we need to restore
 * saved temperatures. But only if we actually saved them! This is where the
 * state.setpointsRestored flag becomes critical.
 *
 * PARAMETERS:
 * @param evt Event object containing:
 *   - evt.value: New mode name as a String (e.g., "Away", "Night", "Home", "Day")
 */
void locationModeChangeHandler(Event evt) {
  // Extract the new mode name from the event
  // evt.value = String like "Away", "Night", "Home", "Day", etc.
  String mode = evt.value

  // Log the mode change for debugging
  // ${mode} inserts the mode name into the string
  // This appears in logs if settings.debugLogEnable is true
  logDebug("locationModeChangeHandler: Location mode changed to ${mode}")

  // ===== CHECK MODE TYPE AND HANDLE APPROPRIATELY =====

  // CASE 1: Entering Away Mode
  if (mode == 'Away') {
    // Location mode is now "Away" - nobody is home
    // Apply energy-saving temperatures from Away virtual thermostat
    // settings.awayModeVirtualThermostat = the virtual device for Away temps
    // handleModeChange() does the actual work of saving and applying temps
    handleModeChange('Away', awayModeVirtualThermostat)

  // CASE 2: Entering Night Mode
  } else if (mode == 'Night') {
    // Location mode is now "Night" - everyone is asleep
    // Apply sleep-friendly temperatures from Night virtual thermostat
    // settings.nightModeVirtualThermostat = the virtual device for Night temps
    handleModeChange('Night', nightModeVirtualThermostat)

  // CASE 3: Entering Some Other Mode (returning to normal)
  } else {
    // Location mode is something else: "Home", "Day", "Evening", etc.
    // This means we're returning to normal operation

    // Check if we have saved temperatures to restore
    // state.setpointsRestored = false means temps ARE saved (need to restore)
    // state.setpointsRestored = true means already using normal temps (do nothing)
    if (state.setpointsRestored == false) {
      // We have saved setpoints from Away or Night mode
      // Log what we're doing and restore them
      // \"${mode}\" = escape quotes so they appear in the string
      logInfo("Location exited \"${mode}\" mode; restoring previous setpoints")

      // Restore the saved normal temperatures
      restoreStoredSetpoints()
    }
    // If setpointsRestored is true, we're already using normal temps
    // No action needed - just stay as-is
  }
  // Method ends - mode change has been handled
}

/**
 * HANDLE MODE CHANGE (The Master Coordinator)
 *
 * This is the "master brain" method that coordinates all mode transitions.
 * It manages the complex logic of saving, restoring, and applying temperature
 * setpoints while respecting manual user changes. This is where the magic
 * of "smart but respectful" automation happens.
 *
 * THE CHALLENGE:
 * We want to:
 * 1. Apply Away/Night temps when entering those modes
 * 2. Restore normal temps when returning to normal mode
 * 3. NOT overwrite saved normal temps when jumping between Away and Night
 * 4. Remember manual user changes made in normal mode
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
 * LOGIC FLOW FOR AWAY MODE:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ 1. Check: Is setpointsRestored == true? (Are we in normal mode?)       │
 * │    YES: We're currently in normal mode                                  │
 * │         → Save current setpoints (storeCurrentSetpoints)                │
 * │         → If save succeeds, apply Away virtual thermostat setpoints     │
 * │         → Mark setpointsRestored = false (saved, not restored yet)      │
 * │    NO: We're already in Away or Night mode                              │
 * │        → Don't save (would overwrite normal temps with Away/Night!)     │
 * │        → Just apply Away virtual thermostat setpoints                   │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * LOGIC FLOW FOR NIGHT MODE:
 * Same as Away mode, but uses Night virtual thermostat
 *
 * LOGIC FLOW FOR NORMAL MODE:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ 1. Check: Is setpointsRestored == false? (Do we have saved temps?)     │
 * │    YES: We have saved setpoints to restore                              │
 * │         → Restore saved setpoints (restoreStoredSetpoints)              │
 * │         → Mark setpointsRestored = true (back to normal)                │
 * │    NO: Already using normal temps (do nothing)                          │
 * └─────────────────────────────────────────────────────────────────────────┘
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
 * ✓ = Correct behavior achieved by the flag logic
 *
 * PARAMETERS:
 * @param modeName Name of mode entering ("Away", "Night", or other)
 * @param modeThermostat Virtual thermostat device with setpoints for this mode
 *                       (null for normal modes that don't use virtual thermostats)
 */
private void handleModeChange(String modeName, DeviceWrapper modeThermostat=null) {
  // The comments below explain the old logic, now replaced with detailed inline comments
  // OLD COMMENT: Only store current setpoints if not already stored;
  // OLD COMMENT: We're only interested in storing the non-away and non-night setpoints
  // OLD COMMENT: We don't want to overwrite the stored setpoints if we're already in away or night mode
  // OLD COMMENT: When jumping straight between away and night modes, it should not store the setpoints again

  // ===== CASE 1: ENTERING AWAY MODE =====
  if (modeName == 'Away') {
    // Log that we're entering Away mode
    // \"${modeName}\" = escape quotes so they appear in the log message
    // This appears in logs if settings.logEnable is true
    logInfo("Location entered \"${modeName}\" mode")

    // Check if we're currently in normal mode (using normal temperatures)
    // state.setpointsRestored == true means "using normal temps, not saved yet"
    if (state.setpointsRestored == true) {
      // We're in normal mode - need to save current temps before changing

      // Try to save current setpoints to memory
      // storeCurrentSetpoints() returns true if successful, false if error
      if (storeCurrentSetpoints()) {
        // Save succeeded - now apply Away mode temperatures
        // modeThermostat = the Away virtual thermostat device
        // modeName = "Away" (for logging purposes)
        applyVirtualThermostatSetpoints(modeThermostat, modeName)
      }
      // If save failed, applyVirtualThermostatSetpoints is NOT called
      // This prevents entering Away mode without saving normal temps
      // The thermostat stays at current temps (safe failure mode)

    } else {
      // state.setpointsRestored == false means we're already in Away or Night mode
      // Normal temps are ALREADY saved in memory from earlier
      // Just apply Away setpoints without re-saving
      // This preserves the original normal temps in memory
      applyVirtualThermostatSetpoints(modeThermostat, modeName)
    }

    // Exit this method immediately - nothing else to do for Away mode
    return
  }

  // ===== CASE 2: ENTERING NIGHT MODE =====
  // Same logic as Away mode, but with Night virtual thermostat
  else if (modeName == 'Night') {
    // Log that we're entering Night mode
    logInfo("Location entered \"${modeName}\" mode")

    // Check if we're currently in normal mode
    if (state.setpointsRestored == true) {
      // In normal mode - save before applying Night setpoints
      if (storeCurrentSetpoints()) {
        // Save succeeded - apply Night mode temperatures
        applyVirtualThermostatSetpoints(modeThermostat, modeName)
      }
    } else {
      // Already in Away or Night - just apply Night setpoints without saving
      applyVirtualThermostatSetpoints(modeThermostat, modeName)
    }

    // Exit immediately
    return
  }

  // ===== CASE 3: ENTERING NORMAL MODE (anything other than Away or Night) =====
  // If we get here, modeName is something like "Home", "Day", "Evening", etc.
  // These are all treated as "Normal" mode (not Away, not Night)

  // Check ALL three conditions (all must be true):
  // 1. modeName != 'Away' = not entering Away mode
  // 2. modeName != 'Night' = not entering Night mode
  // 3. state.setpointsRestored == false = we have saved temps to restore
  //
  // && = "and" operator (all conditions must be true)
  if (modeName != 'Away' && modeName != 'Night' && state.setpointsRestored == false) {
    // We have saved setpoints from Away or Night mode
    // Time to restore them!

    // Log that we're exiting Away/Night mode to some normal mode
    logInfo("Location exited \"${modeName}\" mode")

    // Update state to reflect we're back in normal mode
    state.currentOperatingState = 'Normal'

    // Restore the saved normal temperatures
    restoreStoredSetpoints()
  }
  // If conditions aren't met, do nothing (already using normal temps)

  // Method ends here - mode change has been fully handled
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
  // Check if a virtual thermostat was provided
  // modeThermostat could be null if user hasn't configured one
  if (modeThermostat == null) {
    // No virtual thermostat configured for this mode
    // Log a warning so user knows why nothing happened
    logWarn("No virtual thermostat configured for ${modeName} mode")

    // Exit immediately - can't apply setpoints without a source
    return
  }

  // Read the heating setpoint from the virtual thermostat
  // 1. modeThermostat.currentValue('heatingSetpoint') = get value
  // 2. as BigDecimal = convert to precise decimal
  // 3. ?. = safe operator (if null, stop here and result is null)
  // 4. toDouble() = convert to regular Double for real thermostat
  // Example: Virtual thermostat says 65°F → read as 65.0
  Double heat = (modeThermostat.currentValue('heatingSetpoint') as BigDecimal)?.toDouble()

  // Read the cooling setpoint from the virtual thermostat
  // Same pattern as heating setpoint above
  // Example: Virtual thermostat says 80°F → read as 80.0
  Double cool = (modeThermostat.currentValue('coolingSetpoint') as BigDecimal)?.toDouble()

  // Check if we successfully read a heating setpoint
  // It might be null if virtual thermostat isn't working properly
  if (heat != null) {
    // We have a valid heating setpoint - apply it to REAL thermostat
    // thermostat = the physical thermostat device
    // .setHeatingSetpoint(heat) = tell thermostat to use this heating target
    // This is the moment the physical thermostat actually changes!
    thermostat.setHeatingSetpoint(heat)
  }

  // Check if we successfully read a cooling setpoint
  if (cool != null) {
    // We have a valid cooling setpoint - apply it to REAL thermostat
    // .setCoolingSetpoint(cool) = tell thermostat to use this cooling target
    thermostat.setCoolingSetpoint(cool)
  }

  // Set the physical thermostat to AUTO mode
  // .auto() = enable automatic heating/cooling based on current temperature
  // This ensures the thermostat will heat or cool as needed
  thermostat.auto()

  // Update our internal operating state to match the new mode
  // state.currentOperatingState = persistent memory of current mode
  // modeName = "Away" or "Night" (whatever was passed in)
  // This helps us track what mode we're in for future decisions
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
  // Retrieve the saved heating setpoint from memory
  // state.setPointLow = where we stored it in storeCurrentSetpoints()
  // as BigDecimal = convert back to precise decimal type
  BigDecimal low = state.setPointLow as BigDecimal

  // Retrieve the saved cooling setpoint from memory
  // state.setPointHigh = where we stored the cooling temp
  BigDecimal high = state.setPointHigh as BigDecimal

  // Safety check: verify both values were successfully retrieved
  // || = "or" operator (if EITHER is null, condition is true)
  if (low == null || high == null) {
    // One or both stored values are missing

    // Log a warning so user knows restoration failed
    // This is unusual but could happen after app reinstall or state reset
    logWarn('No stored setpoints available to restore')

    // Exit immediately - can't restore without valid values
    // Thermostat stays at current settings (safe failure mode)
    return
  }

  // If we get here, both values are valid and ready to restore

  // Apply the saved heating setpoint to the REAL thermostat
  // thermostat = the physical thermostat device
  // .setHeatingSetpoint() = command to set heating target temperature
  // low.toDouble() = convert BigDecimal to Double for the command
  thermostat.setHeatingSetpoint(low.toDouble())

  // Apply the saved cooling setpoint to the REAL thermostat
  // .setCoolingSetpoint() = command to set cooling target temperature
  // high.toDouble() = convert BigDecimal to Double for the command
  thermostat.setCoolingSetpoint(high.toDouble())

  // Mark that setpoints have been successfully restored
  // state.setpointsRestored = true means "back to normal, no temps in memory"
  // This is CRITICAL - tells handleModeChange to save again next time
  state.setpointsRestored = true

  // Set the physical thermostat to AUTO mode
  // .auto() = enable automatic heating/cooling
  // Ensures thermostat can heat or cool as needed
  thermostat.auto()

  // Update our internal operating state to "Normal"
  // state.currentOperatingState = persistent memory of current mode
  // "Normal" = not Away, not Night - regular temperatures in use
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
  // Log the event with device name, type, and value
  // ${evt.device.displayName} = thermostat's friendly name
  // ${evt.type} = what kind of event this is
  // ${evt.value} = the new value being reported
  // This only appears in logs if debug logging is enabled
  logDebug("thermostatEventHandler: ${evt.device.displayName}, ${evt.type}: ${evt.value}")
}