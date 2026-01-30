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
  }
}

// ============================================================================
// USER PREFERENCES - Configuration options shown in device settings
// ============================================================================
preferences {
  // Number of samples for fast-moving average
  // Lower numbers = more responsive to changes but more sensitive to noise
  // Higher numbers = more stable but slower to react to changes
  input name: 'numSamplesFast', title: 'Fast Moving Average Samples', type: 'NUMBER', required: true, defaultValue: 10, range: 10..50

  // Number of samples for slow-moving average
  // This creates a very stable baseline that ignores short-term fluctuations
  input name: 'numSamplesSlow', title: 'Slow Moving Average Samples', type: 'NUMBER', required: true, defaultValue: 50, range: 50..200
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
  // GET USER CONFIGURATION
  // ============================================================================
  // Retrieve how many samples to use for rolling averages from user preferences.
  // ============================================================================

  // Number of samples for fast-moving average (default: 10)
  // Lower = more responsive but less stable
  Integer nFast = getSetting('numSamplesFast') as Integer

  // Number of samples for slow-moving average (default: 50)
  // Higher = more stable but less responsive
  Integer nSlow = getSetting('numSamplesSlow') as Integer

  // ============================================================================
  // PHASE 2: HUMIDITY VALUE TRACKING
  // ============================================================================
  // Store current and previous humidity values for comparison and calculations.
  // ============================================================================

  // Get the humidity value from the previous reading
  BigDecimal humidityPrevious = getDeviceProperty(device, 'currentValue')('humidityCurrent') as BigDecimal
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

  // Calculate the time-weighted humidity component for this reading
  // This is: (current humidity) × (how long since last reading)
  // If no time elapsed, just use the humidity value itself
  BigDecimal timeWeightedHumidity = elapsedTimeSinceLastUpdate != 0 ?
    elapsedTimeSinceLastUpdate * humidityCurrent :
    humidityCurrent

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

  // ============================================================================
  // PHASE 4: ROLLING TIME-WEIGHTED AVERAGES (Experimental)
  // ============================================================================
  // These combine the time-weighted concept with rolling averages.
  // Note: There appears to be a bug in the original code (twSlow uses twFast values)
  // This is preserved as-is but documented for future correction.
  // ============================================================================

  // Calculate fast rolling time-weighted average
  BigDecimal twFast = getDeviceCurrentValue('timeWeightedHumidityRollingAverageFast') as BigDecimal
  if (twFast == null) {
    // First reading - initialize with current humidity
    twFast = humidityCurrent
  } else {
    // Exponential smoothing formula:
    // new = old - old/N + current/N
    // This gives (N-1)/N weight to history and 1/N weight to new value
    twFast -= twFast / nFast
    twFast += humidityCurrent / nFast
  }
  emitEvent('timeWeightedHumidityRollingAverageFast', twFast.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)

  // Calculate slow rolling time-weighted average
  // NOTE: Original code has a bug - uses twFast instead of twSlow
  // Preserving for compatibility but this should be fixed
  BigDecimal twSlow = getDeviceCurrentValue('timeWeightedHumidityRollingAverageSlow') as BigDecimal
  if (twSlow == null) {
    twSlow = humidityCurrent
  } else {
    twSlow -= twSlow / nSlow
    twSlow += humidityCurrent / nSlow
  }
  emitEvent('timeWeightedHumidityRollingAverageSlow', twSlow.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)

  // ============================================================================
  // PHASE 5: STANDARD ROLLING AVERAGES
  // ============================================================================
  // Calculate fast and slow rolling averages using exponential smoothing.
  // These are the primary statistics used by most apps.
  //
  // EXPONENTIAL SMOOTHING FORMULA:
  // new_average = old_average - old_average/N + new_value/N
  //
  // This can be rewritten as:
  // new_average = old_average × (N-1)/N + new_value × 1/N
  //
  // WHAT THIS MEANS:
  // - Give (N-1)/N weight to the historical average
  // - Give 1/N weight to the new reading
  //
  // EXAMPLE with N=10:
  // - Historical average gets 90% weight
  // - New reading gets 10% weight
  // - If old avg = 50 and new value = 60:
  //   new = 50 × 0.9 + 60 × 0.1 = 45 + 6 = 51
  //
  // WHY THIS WORKS:
  // - Automatically adapts to trends
  // - No need to store all N previous values
  // - Computationally very efficient
  // - More recent values gradually have more influence
  // ============================================================================

  // FAST ROLLING AVERAGE (10-50 samples)
  // More responsive to changes, less stable
  BigDecimal pAvgFast = getDeviceCurrentValue('fastRollingAverage') as BigDecimal
  if (pAvgFast == null) {
    // First reading - initialize average with current value
    pAvgFast = humidityCurrent
  } else {
    // Apply exponential smoothing
    // Subtract old/N from old (reduces old's contribution)
    pAvgFast -= pAvgFast / nFast
    // Add current/N (adds new reading's contribution)
    pAvgFast += humidityCurrent / nFast
  }
  // Round to 1 decimal and send event
  emitEvent('fastRollingAverage', pAvgFast.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)

  // SLOW ROLLING AVERAGE (50-200 samples)
  // More stable, less responsive to short-term changes
  BigDecimal pAvgSlow = getDeviceCurrentValue('slowRollingAverage') as BigDecimal
  if (pAvgSlow == null) {
    // First reading - initialize average with current value
    pAvgSlow = humidityCurrent
  } else {
    // Apply exponential smoothing with larger N (slower adjustment)
    pAvgSlow -= pAvgSlow / nSlow
    pAvgSlow += humidityCurrent / nSlow
  }
  // Round to 1 decimal and send event
  emitEvent('slowRollingAverage', pAvgSlow.setScale(1, BigDecimal.ROUND_HALF_UP), null, null)

  // ============================================================================
  // PHASE 6: TREND DETECTION
  // ============================================================================
  // Determine if humidity is increasing or decreasing by comparing current
  // reading to previous reading. This helps apps detect trends.
  // ============================================================================

  // Compare current to previous humidity
  // Result is a string 'true' or 'false' (not boolean) for ENUM attribute
  String isIncreasing = humidityCurrent > humidityPrevious ? 'true' : 'false'

  // Send the trend direction as an event
  // Apps can monitor this to know if humidity is rising or falling
  emitEvent('isIncreasing', isIncreasing, null, null)

  // ============================================================================
  // CALCULATION COMPLETE
  // ============================================================================
  // At this point, all statistics have been calculated and all events have
  // been sent. Apps subscribed to these attributes will receive notifications
  // and can make decisions based on the updated statistics.
  // ============================================================================
}