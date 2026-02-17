/**
 *  ============================================================================
 *  HUMIDITY STATISTICS DRIVER - ADVANCED HUMIDITY TRACKING & ANALYSIS
 *  ============================================================================
 *
 *  PURPOSE:
 *  This is a virtual device driver that acts as a "calculator" for humidity data.
 *  It receives humidity readings from sensors and calculates various statistical
 *  measures to understand humidity patterns over time. Think of it as a smart
 *  notebook that not only records humidity but also analyzes trends.
 *
 *  HOW IT WORKS (THE BIG PICTURE):
 *  1. Parent apps (like BathroomFanController) send humidity readings to this driver
 *  2. The driver maintains running averages and time-weighted calculations
 *  3. It provides multiple types of averages (fast, slow, time-weighted)
 *  4. Apps can use these statistics to make smart decisions (like when to turn on fans)
 *
 *  KEY STATISTICS CALCULATED:
 *
 *  A. ROLLING AVERAGES (Simple Moving Averages):
 *     - Fast Rolling Average: Average of last 10-50 readings (responsive to changes)
 *     - Slow Rolling Average: Average of last 50-200 readings (stable baseline)
 *     These smooth out sensor noise and provide a "normal" humidity baseline
 *
 *  B. TIME-WEIGHTED AVERAGES:
 *     - Overall Time-Weighted Average: Accounts for how long each humidity level lasted
 *     - Daily Time-Weighted Average: Resets at midnight each day
 *     - Weekly Time-Weighted Average: Resets every Sunday at midnight
 *     These are more accurate because they consider duration, not just values
 *
 *  EXAMPLE TO UNDERSTAND TIME-WEIGHTED AVERAGES:
 *  Regular average: If humidity is 50% for 1 minute, then 80% for 1 minute,
 *                   average = (50+80)/2 = 65%
 *
 *  Time-weighted: If humidity is 50% for 10 minutes, then 80% for 2 minutes,
 *                 time-weighted = (50×10 + 80×2)/(10+2) = 54.2%
 *                 This is more accurate because 50% lasted much longer!
 *
 *  KEY CONCEPTS FOR NON-PROGRAMMERS:
 *  - "Attribute": A piece of information this device reports (like a sensor reading)
 *  - "Command": An action you can tell this device to do (like reset statistics)
 *  - "Event": Sending updated information to Hubitat (like reporting a new average)
 *  - "State": Information stored between function calls (like accumulated totals)
 *  - "BigDecimal": A precise number type for accurate decimal calculations
 *
 *  WHY THIS DRIVER EXISTS:
 *  Apps need to know "what's normal" to detect "what's unusual". This driver
 *  continuously tracks what's normal so apps can detect spikes or drops in humidity.
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
// IMPORTS - External code libraries this driver needs to function
// ============================================================================

// BigDecimal: Precise decimal number type for accurate calculations
import java.math.BigDecimal

// CompileStatic: Annotation for compile-time type checking
import groovy.transform.CompileStatic

// Include the utilities and logging library - provides helper functions
#include dwinks.UtilitiesAndLoggingLibrary

// ============================================================================
// DRIVER METADATA - Tells Hubitat about this driver's capabilities
// ============================================================================
metadata {
  // Define the driver's basic information and capabilities
  definition(name: 'Humidity Statistics', namespace: 'dwinks', author: 'Daniel Winks', component: true) {
    // Declare this is a sensor device (reports data, doesn't control anything)
    capability 'Sensor'

    // COMMANDS - Actions users or apps can trigger on this device
    // These appear as buttons in the device page
    command 'resetAllStatistics'              // Clears ALL statistics and starts fresh
    command 'resetTimeWeightedStatistics'     // Clears only time-weighted statistics

    // ATTRIBUTES - Data points this device reports
    // These are the "readings" other apps can monitor and use

    // Primary Statistics (Most commonly used by apps)
    attribute 'timeWeightedAverage', 'NUMBER'        // Overall time-weighted average humidity
    attribute 'slowRollingAverage', 'NUMBER'         // Slow-moving average (stable baseline)
    attribute 'fastRollingAverage', 'NUMBER'         // Fast-moving average (responsive to changes)

    // Time Tracking (Internal data for calculations)
    attribute 'totalElapsedTime', 'NUMBER'           // Total milliseconds since first reading
    attribute 'elapsedTimeSinceLastUpdate', 'NUMBER' // Milliseconds since previous reading
    attribute 'totalTimeWeightedHumidity', 'NUMBER'  // Accumulated (time × humidity) for average calculation

    // Unix Timestamps (milliseconds since January 1, 1970)
    attribute 'unixTimeFirst', 'NUMBER'              // When first humidity reading was received
    attribute 'unixTimePrevious', 'NUMBER'           // When previous reading was received
    attribute 'unixTimeCurrent', 'NUMBER'            // When current reading was received

    // Humidity Values (The actual humidity percentages)
    attribute 'humidityPrevious', 'NUMBER'           // Previous humidity reading
    attribute 'humidityCurrent', 'NUMBER'            // Current humidity reading

    // Periodic Averages (Reset on schedule)
    attribute 'dailyTimeWeightedAverage', 'NUMBER'               // Today's time-weighted average
    attribute 'weeklyTimeWeightedAverage', 'NUMBER'              // This week's time-weighted average
    attribute 'dailyTimeWeightedAverageStartTime', 'NUMBER'      // Midnight timestamp when daily tracking started
    attribute 'weeklyTimeWeightedAverageStartTime', 'NUMBER'     // Sunday midnight when weekly tracking started

    // Trend Detection
    attribute 'isIncreasing', 'ENUM', ['true', 'false']          // Is humidity rising or falling?

    // Rate of Change
    attribute 'rateOfChange', 'NUMBER'                           // Humidity change rate (%/min)

    // Baseline Freeze (prevents rolling averages from updating during fan runs)
    attribute 'baselineFrozen', 'ENUM', ['true', 'false']

    // Fan Run Tracking (recorded by parent app via commands)
    attribute 'lastFanRunDurationMinutes', 'NUMBER'
    attribute 'lastFanHumidityDrop', 'NUMBER'
    attribute 'lastFanStartHumidity', 'NUMBER'
    attribute 'lastFanEndHumidity', 'NUMBER'

    // Advanced Statistics
    attribute 'smoothedRateOfChange', 'NUMBER'          // EMA-filtered rate of change (%/min)
    attribute 'humidityStdDev', 'NUMBER'                // Running standard deviation of humidity
    attribute 'emaVariance', 'NUMBER'                   // EMA-tracked variance (internal, used for stddev)
    attribute 'dailyMinHumidity', 'NUMBER'              // Today's minimum humidity reading
    attribute 'dailyMaxHumidity', 'NUMBER'              // Today's maximum humidity reading
    attribute 'rateOfChangeAcceleration', 'NUMBER'      // Second derivative — is rise speeding up? (%/min²)
    attribute 'currentSpikePeak', 'NUMBER'              // Peak humidity during current fan run
    attribute 'dailyMinutesAboveThreshold', 'NUMBER'    // Minutes today above configured threshold
    attribute 'householdHumidity', 'NUMBER'             // Last received household sensor reading
    attribute 'bathroomDifferential', 'NUMBER'          // Bathroom humidity minus household humidity

    // Commands for baseline freeze control and fan run tracking
    command 'setBaselineFreeze', [[name: 'frozen', type: 'ENUM', constraints: ['true', 'false']]]
    command 'recordFanStart', [[name: 'humidity', type: 'NUMBER']]
    command 'recordFanStop', [[name: 'humidity', type: 'NUMBER'], [name: 'durationMinutes', type: 'NUMBER']]
    command 'setHouseholdHumidity', [[name: 'humidity', type: 'NUMBER']]
  }
}

// ============================================================================
// USER PREFERENCES - Configuration options shown in device settings
// ============================================================================
preferences {
  // Legacy sample-count preferences (kept for backward compatibility, no longer used)
  input name: 'numSamplesFast', title: 'Fast Moving Average Samples (legacy, not used)', type: 'NUMBER', required: true, defaultValue: 10, range: 10..50

  input name: 'numSamplesSlow', title: 'Slow Moving Average Samples (legacy, not used)', type: 'NUMBER', required: true, defaultValue: 50, range: 50..200

  // Time constants for time-weighted EMA (replaces sample-count averages)
  // tauFast: Time constant in minutes for the fast-moving EMA
  // Lower = more responsive, higher = more stable
  input name: 'tauFastMinutes', title: 'Fast EMA Time Constant (minutes)', type: 'NUMBER', required: true, defaultValue: 30, range: 5..120

  // tauSlow: Time constant in minutes for the slow-moving EMA
  input name: 'tauSlowMinutes', title: 'Slow EMA Time Constant (minutes)', type: 'NUMBER', required: true, defaultValue: 120, range: 30..480

  // tauRoc: Time constant in minutes for the smoothed rate-of-change EMA
  // Shorter = more responsive to rapid changes, longer = more stable trend signal
  input name: 'tauRocMinutes', title: 'Rate of Change EMA Time Constant (minutes)', type: 'NUMBER', required: true, defaultValue: 5, range: 1..30

  // humidityThreshold: Humidity level above which "time above threshold" accumulates (mold risk metric)
  // 0 to disable tracking
  input name: 'humidityThreshold', title: 'High Humidity Threshold for Daily Accumulator (%)', type: 'NUMBER', required: true, defaultValue: 60, range: 0..90
}

// =============================================================================
// SCHEDULED TASKS & RESET COMMANDS
// =============================================================================
// These functions handle automatic resets and manual resets of statistics.
// Periodic resets help track daily/weekly patterns without old data skewing results.
// =============================================================================

/**
 * Sets up scheduled tasks to automatically reset statistics at specific times.
 *
 * WHEN THIS RUNS:
 * - Called when the driver is first installed or updated
 *
 * WHAT THIS DOES:
 * Schedules two automatic resets:
 * 1. Daily reset at midnight (00:00) every day
 * 2. Weekly reset at midnight on Sundays
 *
 * WHY THIS MATTERS:
 * By resetting statistics periodically, we can track patterns like:
 * - "What's the average humidity today?" (daily reset)
 * - "What's the average humidity this week?" (weekly reset)
 *
 * CRON EXPRESSION EXPLANATION:
 * '0 0 0 * * ?' means: second=0, minute=0, hour=0 (midnight), any day
 * '0 0 0 ? * SUN' means: second=0, minute=0, hour=0, Sunday
 *
 * These schedules persist even if the hub restarts.
 */
void configure() {
  // Schedule daily reset: runs at midnight (00:00:00) every day
  // This allows tracking of daily humidity patterns
  schedule('0 0 0 * * ?', 'resetDailyTWAverages')

  // Schedule weekly reset: runs at midnight on Sundays
  // This allows tracking of weekly humidity patterns
  schedule('0 0 0 ? * SUN', 'resetWeeklyTWAverages')
}

/**
 * Resets ALL statistics - complete fresh start.
 *
 * WHEN THIS RUNS:
 * - When user clicks "Reset All Statistics" button in device page
 * - Can be called programmatically by apps
 *
 * WHAT THIS DOES:
 * Deletes every state variable and attribute value, as if the device
 * was just installed. The next humidity reading will start fresh calculations.
 *
 * USE CASES:
 * - Sensor was replaced
 * - Want to start tracking from a specific point
 * - Statistics seem incorrect and need a clean slate
 *
 * WARNING: This is irreversible! All historical data is lost.
 */
void resetAllStatistics() {
  // clearStates() is from the UtilitiesAndLoggingLibrary
  // It deletes all state variables and current device attribute values
  clearAllStates()
}

/**
 * Resets only time-weighted statistics while keeping rolling averages.
 *
 * WHEN THIS RUNS:
 * - When user clicks "Reset Time Weighted Statistics" button
 * - Can be called programmatically by apps
 *
 * WHAT THIS DOES:
 * Deletes attributes related to time-weighted calculations but preserves
 * the rolling averages (fast and slow). This is useful if you want to
 * recalibrate time-based tracking without losing your moving averages.
 *
 * ATTRIBUTES DELETED:
 * - timeWeightedAverage (overall time-weighted average)
 * - totalElapsedTime (total time tracked)
 * - elapsedTimeSinceLastUpdate (time between readings)
 * - totalTimeWeightedHumidity (accumulated time×humidity)
 * - unixTimeFirst (first reading timestamp)
 * - humidityPrevious (previous humidity value)
 * - unixTimePrevious (previous reading timestamp)
 * - unixTimeCurrent (current reading timestamp)
 *
 * ATTRIBUTES PRESERVED:
 * - fastRollingAverage (continues tracking)
 * - slowRollingAverage (continues tracking)
 */
void resetTimeWeightedStatistics() {
  // List of attribute names to delete
  // The .each {} loop processes each item in the list
  [
    'timeWeightedAverage',
    'totalElapsedTime',
    'elapsedTimeSinceLastUpdate',
    'totalTimeWeightedHumidity',
    'unixTimeFirst',
    'humidityPrevious',
    'unixTimePrevious',
    'unixTimeCurrent',
    'rateOfChange',
    'smoothedRateOfChange',
    'rateOfChangeAcceleration',
    'humidityStdDev',
    'emaVariance',
    'dailyMinHumidity',
    'dailyMaxHumidity',
    'currentSpikePeak',
    'dailyMinutesAboveThreshold',
    'bathroomDifferential',
    'baselineFrozen',
    'lastFanRunDurationMinutes',
    'lastFanHumidityDrop',
    'lastFanStartHumidity',
    'lastFanEndHumidity',
  ].each {
    // Delete each attribute's current value from the device
    // device.deleteCurrentState() removes the stored value for that attribute
    deleteDeviceCurrentState(it)
  }
}

/**
 * Resets the daily time-weighted average tracking.
 *
 * WHEN THIS RUNS:
 * - Automatically at midnight every day (scheduled by configure())
 * - Can be manually triggered by apps
 *
 * WHAT THIS DOES:
 * Records the current timestamp as the start of a new daily tracking period.
 * Future daily average calculations will only include readings after this time.
 *
 * WHY SEPARATE FROM resetTimeWeightedStatistics():
 * This is for periodic automated resets. The daily average needs to reset
 * automatically each day, but we don't want to reset the overall time-weighted
 * average (which tracks since device creation).
 *
 * TECHNICAL DETAILS:
 * - sendEvent() reports the new value to Hubitat
 * - isStateChange: true forces the event even if the value hasn't changed
 * - now() returns current time in milliseconds since Unix epoch
 */
void resetDailyTWAverages() {
  // Send an event setting the daily start time to right now
  // This marks the beginning of a new day for tracking purposes
  emitEvent('dailyTimeWeightedAverageStartTime', getCurrentTime(), null, null)

  // Reset daily min/max and time above threshold
  deleteDeviceCurrentState('dailyMinHumidity')
  deleteDeviceCurrentState('dailyMaxHumidity')
  deleteDeviceCurrentState('dailyMinutesAboveThreshold')
}

/**
 * Resets the weekly time-weighted average tracking.
 *
 * WHEN THIS RUNS:
 * - Automatically at midnight every Sunday (scheduled by configure())
 * - Can be manually triggered by apps
 *
 * WHAT THIS DOES:
 * Records the current timestamp as the start of a new weekly tracking period.
 * Future weekly average calculations will only include readings after this time.
 *
 * WHY SUNDAY:
 * Sunday is traditionally the first day of the week in many calendars,
 * making it a natural reset point for weekly statistics.
 *
 * RELATIONSHIP TO DAILY:
 * Both daily and weekly tracking run simultaneously. An app can choose
 * to use daily averages (more responsive) or weekly averages (more stable).
 */
void resetWeeklyTWAverages() {
  // Send an event setting the weekly start time to right now
  // This marks the beginning of a new week for tracking purposes
  emitEvent('weeklyTimeWeightedAverageStartTime', getCurrentTime(), null, null)
}

// =============================================================================
// BASELINE FREEZE AND FAN RUN TRACKING COMMANDS
// =============================================================================

/**
 * Freezes or unfreezes the rolling average baseline.
 * When frozen, rolling averages (fast and slow) stop updating so that
 * a humidity spike during a fan run does not contaminate the baseline.
 *
 * @param frozen 'true' to freeze, 'false' to unfreeze
 */
void setBaselineFreeze(String frozen) {
  emitEvent('baselineFrozen', frozen, null, null)
}

/**
 * Records the start of a fan run for tracking purposes.
 * Called by the parent app when the fan is turned on.
 * Also resets the spike peak tracker to the starting humidity.
 *
 * @param humidity The humidity reading at the time the fan was turned on
 */
void recordFanStart(BigDecimal humidity) {
  emitEvent('lastFanStartHumidity', humidity, null, null)
  emitEvent('currentSpikePeak', humidity, null, null)
}

/**
 * Records the end of a fan run for tracking purposes.
 * Called by the parent app when the fan is turned off.
 *
 * @param humidity The humidity reading at the time the fan was turned off
 * @param durationMinutes How long the fan ran in minutes
 */
void recordFanStop(BigDecimal humidity, BigDecimal durationMinutes) {
  BigDecimal startHumidity = getDeviceCurrentValue('lastFanStartHumidity') as BigDecimal
  emitEvent('lastFanEndHumidity', humidity, null, null)
  emitEvent('lastFanRunDurationMinutes', durationMinutes, null, null)
  if (startHumidity != null) {
    emitEvent('lastFanHumidityDrop', startHumidity - humidity, null, null)
  }
}

/**
 * Stores the household humidity reading for differential calculation.
 * Called by the parent app when the household sensor reports.
 *
 * @param humidity The household sensor humidity reading
 */
void setHouseholdHumidity(BigDecimal humidity) {
  emitEvent('householdHumidity', humidity, null, null)
  // Recompute differential if we have a current bathroom reading
  BigDecimal current = getDeviceCurrentValue('humidityCurrent') as BigDecimal
  if (current != null) {
    emitEvent('bathroomDifferential', (current - humidity).setScale(1, BigDecimal.ROUND_HALF_UP), null, null)
  }
}

// =============================================================================
// HUMIDITY EVENT PROCESSING - THE CORE CALCULATION ENGINE
// =============================================================================
// This is the heart of the driver. When a new humidity reading arrives,
// this function calculates all the statistics and updates all attributes.
//
// CALLED BY: Parent apps (like BathroomFanController) when they receive
//            humidity sensor readings
//
// ALGORITHM OVERVIEW:
// 1. Get timing information (when was this reading, when was the last one)
// 2. Calculate time-weighted values (humidity × time duration)
// 3. Update rolling averages using exponential smoothing
// 4. Determine trend direction (is humidity rising or falling)
// 5. Send all updated values as events
// =============================================================================

/**
 * Processes a new humidity reading and updates all statistical calculations.
 *
 * THIS IS THE MAIN FUNCTION - Called every time a new humidity reading arrives.
 *
 * WHAT THIS DOES (Step-by-Step):
 *
 * PHASE 1: TIME TRACKING
 * - Records when this reading occurred
 * - Calculates how much time passed since the last reading
 * - Tracks total elapsed time since first reading
 *
 * PHASE 2: TIME-WEIGHTED CALCULATIONS
 * - Multiplies humidity by duration (how long that humidity lasted)
 * - Accumulates these products to get accurate average over time
 * - Calculates overall time-weighted average
 *
 * PHASE 3: ROLLING AVERAGES
 * - Updates fast-moving average (10-50 samples)
 * - Updates slow-moving average (50-200 samples)
 * - Uses exponential smoothing for efficiency
 *
 * PHASE 4: TREND DETECTION
 * - Compares current humidity to previous
 * - Determines if humidity is increasing or decreasing
 *
 * @param humidityCurrent The new humidity reading as a percentage (0-100)
 *
 * EXAMPLE FLOW:
 * Time 0:00 - Humidity 50%, first reading
 *             → Fast avg = 50%, Slow avg = 50%, Time-weighted = 50%
 *
 * Time 0:05 - Humidity 60%, 5 minutes later
 *             → Time-weighted component = 60% × 5min = 300 (%·min)
 *             → Fast avg updates more (60 has bigger impact)
 *             → Slow avg updates less (60 has smaller impact)
 *             → isIncreasing = true (60 > 50)
 *
 * Time 0:10 - Humidity 55%, 5 minutes later
 *             → Time-weighted component = 55% × 5min = 275 (%·min)
 *             → Both averages adjust toward 55%
 *             → isIncreasing = false (55 < 60)
 *
 * TECHNICAL NOTES:
 * - All timestamps are Unix time (milliseconds since Jan 1, 1970)
 * - BigDecimal used for precision (avoids floating point errors)
 * - null checks ensure first reading initializes correctly
 * - Rolling averages use formula: new = old - old/N + current/N
 *   (This is exponential smoothing, very efficient!)
 */
void logHumidityEvent(BigDecimal humidityCurrent) {
  // ============================================================================
  // PHASE 1: TIME TRACKING
  // ============================================================================
  // We need to know WHEN readings occur to calculate time-weighted averages
  // and to ensure we're comparing apples-to-apples with timing.
  // ============================================================================

  // Get the current time in milliseconds (Unix timestamp)
  // This is our reference point for "now"
  BigDecimal unixTimeCurrent = getCurrentTime() as BigDecimal

  // Get the time of the previous reading from device attributes
  // currentValue() retrieves the stored value of an attribute
  // If this is the first reading, this will be null
  BigDecimal unixTimePrevious = getDeviceCurrentValue('unixTimeCurrent') as BigDecimal
  if (unixTimePrevious == null) {
    // First reading ever - no previous time exists
    // Set previous time = current time (elapsed = 0)
    unixTimePrevious = unixTimeCurrent
  }

  // Get the time of the very first reading ever received
  // This lets us track total time since tracking started
  BigDecimal unixTimeFirst = getDeviceCurrentValue('unixTimeFirst') as BigDecimal
  if (unixTimeFirst == null) {
    // This IS the first reading - initialize it
    unixTimeFirst = unixTimeCurrent
  }

  // Calculate how much total time has passed since we started tracking
  // Result is in milliseconds (e.g., 3600000 = 1 hour)
  BigDecimal totalElapsedTime = unixTimeCurrent - unixTimeFirst

  // Calculate how much time passed since the previous reading
  // This tells us how long the previous humidity level lasted
  // Example: If previous was 5 minutes ago, this = 300000 milliseconds
  BigDecimal elapsedTimeSinceLastUpdate = unixTimeCurrent - unixTimePrevious

  // Get midnight of today for daily tracking
  // timeToday() is a Hubitat function that returns a Date object for today at specified time
  Date todayMidnight = getTodayAtTime('00:00')

  // Get the start time for daily time-weighted average tracking
  // This was set by resetDailyTWAverages() (runs at midnight)
  BigDecimal twDailyStart = getDeviceCurrentValue('dailyTimeWeightedAverageStartTime') as BigDecimal
  if (twDailyStart == null) {
    // No daily start time set yet - use today's midnight
    twDailyStart = todayMidnight.getTime() as BigDecimal
  }

  // Get the start time for weekly time-weighted average tracking
  // This was set by resetWeeklyTWAverages() (runs Sunday midnight)
  BigDecimal twWeeklyStart = getDeviceCurrentValue('weeklyTimeWeightedAverageStartTime') as BigDecimal
  if (twWeeklyStart == null) {
    // No weekly start time set yet - calculate last Sunday's midnight
    // getDay() returns day of week (0=Sunday, 1=Monday, etc.)
    // Subtract days to get to previous Sunday
    todayMidnight.setDate(1 - todayMidnight.getDay())
    twWeeklyStart = todayMidnight.getTime() as BigDecimal
  }

  // ============================================================================
  // SEND TIME TRACKING EVENTS
  // ============================================================================
  // Report all the timing information we just calculated as device attributes.
  // Other apps can monitor these if they need timing details.
  // ============================================================================

  emitEvent('unixTimePrevious', unixTimePrevious, null, null)
  emitEvent('unixTimeCurrent', unixTimeCurrent, null, null)
  emitEvent('unixTimeFirst', unixTimeFirst, null, null)
  emitEvent('totalElapsedTime', totalElapsedTime, null, null)
  emitEvent('elapsedTimeSinceLastUpdate', elapsedTimeSinceLastUpdate, null, null)
  emitEvent('dailyTimeWeightedAverageStartTime', twDailyStart, null, null)
  emitEvent('weeklyTimeWeightedAverageStartTime', twWeeklyStart, null, null)

  // ============================================================================
  // PHASE 2: HUMIDITY VALUE TRACKING
  // ============================================================================
  // Store current and previous humidity values for comparison and calculations.
  // ============================================================================

  // Get the humidity value from the previous reading
  BigDecimal humidityPrevious = getDeviceCurrentValue('humidityCurrent') as BigDecimal
  if (humidityPrevious == null) {
    // First reading - no previous value exists
    // Initialize previous = current (no change)
    humidityPrevious = humidityCurrent
  }

  // Send events to record both humidity values
  emitEvent('humidityPrevious', humidityPrevious, null, null)
  emitEvent('humidityCurrent', humidityCurrent, null, null)

  // ============================================================================
  // PHASE 3: TIME-WEIGHTED AVERAGE CALCULATIONS
  // ============================================================================
  // Calculate the time-weighted average, which accounts for how long each
  // humidity level lasted. This is more accurate than simple averaging.
  //
  // FORMULA: Time-Weighted Average = Σ(humidity × duration) / Σ(duration)
  //
  // EXAMPLE:
  // If humidity was 50% for 10 minutes, then 80% for 2 minutes:
  // - Component 1: 50 × 10 = 500 (%·min)
  // - Component 2: 80 × 2 = 160 (%·min)
  // - Total: 500 + 160 = 660 (%·min)
  // - Total time: 10 + 2 = 12 minutes
  // - Average: 660 / 12 = 55%
  //
  // This is more accurate than (50+80)/2 = 65% because 50% lasted longer!
  // ============================================================================

  // Calculate the time-weighted humidity component for this interval
  // The elapsed interval had the PREVIOUS humidity (not current), because
  // the previous reading was in effect during the time between readings.
  // If no time elapsed (first reading), contribute nothing to the TWA numerator.
  BigDecimal timeWeightedHumidity = elapsedTimeSinceLastUpdate != 0 ?
    elapsedTimeSinceLastUpdate * humidityPrevious :
    0

  // Get the accumulated total of all time-weighted humidity components
  // This is the sum of (humidity × duration) for all readings
  BigDecimal totalTimeWeightedHumidity = getDeviceCurrentValue('totalTimeWeightedHumidity') as BigDecimal
  if (totalTimeWeightedHumidity == null) {
    // First reading - initialize with current component
    totalTimeWeightedHumidity = timeWeightedHumidity
  } else {
    // Add this reading's component to the running total
    totalTimeWeightedHumidity += timeWeightedHumidity
  }

  // Send event for the accumulated total (used in next calculation)
  emitEvent('totalTimeWeightedHumidity', totalTimeWeightedHumidity, null, null)

  // Calculate the overall time-weighted average
  // Divide accumulated (humidity × time) by total time
  // If no time elapsed yet, just use current humidity
  BigDecimal timeWeightedAverage = totalElapsedTime != 0 ?
    totalTimeWeightedHumidity / totalElapsedTime :
    humidityCurrent

  // Round to 1 decimal place and send event
  // ROUND_HALF_UP means 45.65 → 45.7 and 45.64 → 45.6
  emitEvent('timeWeightedAverage', timeWeightedAverage.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)

  // Capture pre-update slow average for variance/stddev calculation (needed before EMA update)
  BigDecimal preUpdateSlowAvg = getDeviceCurrentValue('slowRollingAverage') as BigDecimal

  // ============================================================================
  // PHASE 4+5: TIME-WEIGHTED EXPONENTIAL MOVING AVERAGES (EMA)
  // ============================================================================
  // Replaces old sample-count based rolling averages with time-weighted EMAs.
  // This fixes bias from irregular sensor reporting intervals.
  //
  // FORMULA:  α = 1 - exp(-elapsedMs / tauMs)
  //           newAvg = oldAvg * (1 - α) + currentHumidity * α
  //
  // The time constant (tau) controls how fast the EMA responds:
  // - After one tau of elapsed time, the average is ~63% of the way to the new value
  // - Irregularly spaced readings are handled correctly because α adapts to elapsed time
  //
  // When baseline is frozen (fan running), averages do NOT update so that
  // humidity spikes don't contaminate the baseline.
  // ============================================================================

  String baselineFrozen = getDeviceCurrentValue('baselineFrozen') as String

  if (baselineFrozen != 'true' && elapsedTimeSinceLastUpdate > 0) {
    // Get tau values from preferences (in minutes), convert to milliseconds
    BigDecimal tauFastMs = ((getSetting('tauFastMinutes') ?: 30) as BigDecimal) * 60000
    BigDecimal tauSlowMs = ((getSetting('tauSlowMinutes') ?: 120) as BigDecimal) * 60000

    // Calculate alpha for fast EMA
    double alphaFast = 1.0d - Math.exp(-elapsedTimeSinceLastUpdate.doubleValue() / tauFastMs.doubleValue())
    // Calculate alpha for slow EMA
    double alphaSlow = 1.0d - Math.exp(-elapsedTimeSinceLastUpdate.doubleValue() / tauSlowMs.doubleValue())

    // FAST ROLLING AVERAGE (time-weighted EMA)
    BigDecimal pAvgFast = getDeviceCurrentValue('fastRollingAverage') as BigDecimal
    if (pAvgFast == null) {
      pAvgFast = humidityCurrent
    } else {
      pAvgFast = pAvgFast * (1.0 - alphaFast) + humidityCurrent * alphaFast
    }
    emitEvent('fastRollingAverage', pAvgFast.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)

    // SLOW ROLLING AVERAGE (time-weighted EMA)
    BigDecimal pAvgSlow = getDeviceCurrentValue('slowRollingAverage') as BigDecimal
    if (pAvgSlow == null) {
      pAvgSlow = humidityCurrent
    } else {
      pAvgSlow = pAvgSlow * (1.0 - alphaSlow) + humidityCurrent * alphaSlow
    }
    emitEvent('slowRollingAverage', pAvgSlow.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)
  } else if (elapsedTimeSinceLastUpdate == 0) {
    // First reading - initialize both averages
    emitEvent('fastRollingAverage', humidityCurrent.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)
    emitEvent('slowRollingAverage', humidityCurrent.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)
  }

  // ============================================================================
  // PHASE 6: RATE OF CHANGE AND TREND DETECTION
  // ============================================================================
  // Calculate how fast humidity is changing (%/min) and whether it's rising.
  // ============================================================================

  // Capture previous rate of change BEFORE emitting the new one (for acceleration calc)
  BigDecimal previousRoc = getDeviceCurrentValue('rateOfChange') as BigDecimal

  // Rate of change in %/min (positive = rising, negative = falling)
  BigDecimal currentRoc = null
  if (elapsedTimeSinceLastUpdate > 0) {
    BigDecimal elapsedMinutes = elapsedTimeSinceLastUpdate / 60000
    currentRoc = (humidityCurrent - humidityPrevious) / elapsedMinutes
    emitEvent('rateOfChange', currentRoc.setScale(2, BigDecimal.ROUND_HALF_UP), null, null)
  }

  // Compare current to previous humidity
  String isIncreasing = humidityCurrent > humidityPrevious ? 'true' : 'false'
  emitEvent('isIncreasing', isIncreasing, null, null)

  // ============================================================================
  // PHASE 7: ADVANCED STATISTICS
  // ============================================================================

  // --- 7a: Smoothed Rate of Change (EMA-filtered derivative) ---
  // Filters out noise from the raw single-sample RoC, giving a reliable
  // "humidity is rapidly rising/falling" signal.
  if (elapsedTimeSinceLastUpdate > 0 && currentRoc != null) {
    BigDecimal tauRocMs = ((getSetting('tauRocMinutes') ?: 5) as BigDecimal) * 60000
    double alphaRoc = 1.0d - Math.exp(-elapsedTimeSinceLastUpdate.doubleValue() / tauRocMs.doubleValue())

    BigDecimal smoothedRoc = getDeviceCurrentValue('smoothedRateOfChange') as BigDecimal
    if (smoothedRoc == null) {
      smoothedRoc = currentRoc
    } else {
      smoothedRoc = smoothedRoc * (1.0 - alphaRoc) + currentRoc * alphaRoc
    }
    emitEvent('smoothedRateOfChange', smoothedRoc.setScale(2, BigDecimal.ROUND_HALF_UP), null, null)

    // --- 7b: Rate of Change Acceleration (second derivative) ---
    // Detects the very beginning of a shower spike — humidity isn't just
    // rising, it's rising *faster*. Units: %/min²
    if (previousRoc != null) {
      BigDecimal elapsedMin = elapsedTimeSinceLastUpdate / 60000
      BigDecimal acceleration = (currentRoc - previousRoc) / elapsedMin
      emitEvent('rateOfChangeAcceleration', acceleration.setScale(3, BigDecimal.ROUND_HALF_UP), null, null)
    }
  }

  // --- 7c: Running Standard Deviation (EMA-based variance) ---
  // Tracks how much humidity typically varies from the slow average.
  // Enables adaptive thresholds (e.g., "trigger at 2 standard deviations").
  // Uses the same tau as the slow EMA for stability.
  // Only updates when baseline is NOT frozen (same as rolling averages).
  if (baselineFrozen != 'true' && elapsedTimeSinceLastUpdate > 0 && preUpdateSlowAvg != null) {
    BigDecimal tauSlowMs = ((getSetting('tauSlowMinutes') ?: 120) as BigDecimal) * 60000
    double alphaVar = 1.0d - Math.exp(-elapsedTimeSinceLastUpdate.doubleValue() / tauSlowMs.doubleValue())

    BigDecimal diff = humidityCurrent - preUpdateSlowAvg
    BigDecimal emaVar = getDeviceCurrentValue('emaVariance') as BigDecimal
    if (emaVar == null) {
      emaVar = diff * diff
    } else {
      emaVar = emaVar * (1.0 - alphaVar) + (diff * diff) * alphaVar
    }
    emitEvent('emaVariance', emaVar.setScale(2, BigDecimal.ROUND_HALF_UP), null, null)

    BigDecimal stdDev = new BigDecimal(Math.sqrt(emaVar.doubleValue()))
    emitEvent('humidityStdDev', stdDev.setScale(2, BigDecimal.ROUND_HALF_UP), null, null)
  }

  // --- 7d: Daily Min / Max ---
  // Simple tracking of today's humidity range. Useful for detecting baseline
  // drift over time (e.g., steadily climbing = possible ventilation problem).
  // Reset at midnight by resetDailyTWAverages().
  BigDecimal dailyMin = getDeviceCurrentValue('dailyMinHumidity') as BigDecimal
  BigDecimal dailyMax = getDeviceCurrentValue('dailyMaxHumidity') as BigDecimal
  if (dailyMin == null || humidityCurrent < dailyMin) {
    emitEvent('dailyMinHumidity', humidityCurrent, null, null)
  }
  if (dailyMax == null || humidityCurrent > dailyMax) {
    emitEvent('dailyMaxHumidity', humidityCurrent, null, null)
  }

  // --- 7e: Current Spike Peak ---
  // Tracks the highest humidity reached during a fan run (when baseline is frozen).
  // Reset when recordFanStart() is called.
  if (baselineFrozen == 'true') {
    BigDecimal currentPeak = getDeviceCurrentValue('currentSpikePeak') as BigDecimal
    if (currentPeak == null || humidityCurrent > currentPeak) {
      emitEvent('currentSpikePeak', humidityCurrent, null, null)
    }
  }

  // --- 7f: Time Above Threshold (daily mold-risk accumulator) ---
  // Accumulates minutes per day that humidity exceeds the configured threshold.
  // Uses the PREVIOUS humidity for the interval (same principle as TWA).
  Integer threshold = getSetting('humidityThreshold') as Integer
  if (threshold != null && threshold > 0 && elapsedTimeSinceLastUpdate > 0) {
    if (humidityPrevious >= threshold) {
      BigDecimal minutesAbove = getDeviceCurrentValue('dailyMinutesAboveThreshold') as BigDecimal
      BigDecimal elapsedMin = elapsedTimeSinceLastUpdate / 60000
      if (minutesAbove == null) { minutesAbove = 0 }
      minutesAbove += elapsedMin
      emitEvent('dailyMinutesAboveThreshold', minutesAbove.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)
    }
  }

  // --- 7g: Bathroom Differential ---
  // How much higher the bathroom humidity is vs. the household sensor.
  // Positive = bathroom is more humid than rest of house.
  BigDecimal householdHum = getDeviceCurrentValue('householdHumidity') as BigDecimal
  if (householdHum != null) {
    BigDecimal differential = humidityCurrent - householdHum
    emitEvent('bathroomDifferential', differential.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)
  }

  // ============================================================================
  // CALCULATION COMPLETE
  // ============================================================================
}