#include dwinks.UtilitiesAndLogging

metadata {
  definition(name: 'Humidity Statistics', namespace: 'dwinks', author: 'Daniel Winks', component: true) {
    capability 'Sensor'
    command 'resetAllStatistics'
    command 'resetTimeWeightedStatistics'
    attribute 'timeWeightedAverage', 'NUMBER'
    attribute 'slowRollingAverage', 'NUMBER'
    attribute 'fastRollingAverage', 'NUMBER'
    attribute 'totalElapsedTime', 'NUMBER'
    attribute 'elapsedTimeSinceLastUpdate', 'NUMBER'
    attribute 'totalTimeWeightedHumidity', 'NUMBER'
    attribute 'unixTimeFirst', 'NUMBER'
    attribute 'unixTimePrevious', 'NUMBER'
    attribute 'humidityPrevious', 'NUMBER'
    attribute 'unixTimeCurrent', 'NUMBER'
    attribute 'humidityCurrent', 'NUMBER'
    attribute 'dailyTimeWeightedAverage', 'NUMBER'
    attribute 'weeklyTimeWeightedAverage', 'NUMBER'
    attribute 'dailyTimeWeightedAverageStartTime', 'NUMBER'
    attribute 'weeklyTimeWeightedAverageStartTime', 'NUMBER'
    attribute 'isIncreasing', 'ENUM', ['true', 'false']
  }
}

preferences {
  input name: 'numSamplesFast', title: 'Fast Moving Average Samples', type: 'NUMBER', required: true, defaultValue: 10, range: 10..50
  input name: 'numSamplesSlow', title: 'Slow Moving Average Samples', type: 'NUMBER', required: true, defaultValue: 50, range: 50..200
}

void updated() {
  logInfo 'Updated...'
}

void initialize() {
  logInfo 'Installed...'
  schedule('0 0 0 * * ?', 'resetDailyTWAverages')
  schedule('0 0 0 ? * SUN', 'resetDailyTWAverages')
}

void resetAllStatistics() {
  clearStates()
}

void resetTimeWeightedStatistics() {
  [
    'timeWeightedAverage',
    'totalElapsedTime',
    'elapsedTimeSinceLastUpdate',
    'totalTimeWeightedHumidity',
    'unixTimeFirst',
    'humidityPrevious',
    'unixTimePrevious',
    'unixTimeCurrent',
  ].each { device.deleteCurrentState(it) }
}

void resetDailyTWAverages() {
  sendEvent(name: 'dailyTimeWeightedAverageStartTime', value: now(), isStateChange: true)
}

void resetWeeklyTWAverages() {
  sendEvent(name: 'weeklyTimeWeightedAverageStartTime', value: now(), isStateChange: true)
}

void logHumidityEvent(BigDecimal humidityCurrent) {
  // Get Times
  BigDecimal unixTimeCurrent = now()
  BigDecimal unixTimePrevious = device.currentValue('unixTimeCurrent')
  if(unixTimePrevious == null) { unixTimePrevious = unixTimeCurrent }
  BigDecimal unixTimeFirst = device.currentValue('unixTimeFirst')
  if(unixTimeFirst == null) { unixTimeFirst = unixTimeCurrent }
  BigDecimal totalElapsedTime = unixTimeCurrent - unixTimeFirst
  BigDecimal elapsedTimeSinceLastUpdate = unixTimeCurrent - unixTimePrevious
  Date todayMidnight = timeToday("00:00")
  BigDecimal twDailyStart = device.currentValue('dailyTimeWeightedAverageStartTime')
  if(twDailyStart == null) {
    twDailyStart = todayMidnight.getTime()
  }
  BigDecimal twWeeklyStart = device.currentValue('weeklyTimeWeightedAverageStartTime')
  if(twWeeklyStart == null) {
    todayMidnight.setDate(1-todayMidnight.getDay())
    twWeeklyStart = todayMidnight.getTime()
  }

  // Send Time Events
  sendEvent(name: 'unixTimePrevious', value: unixTimePrevious, isStateChange: true)
  sendEvent(name: 'unixTimeCurrent', value: unixTimeCurrent, isStateChange: true)
  sendEvent(name: 'unixTimeFirst', value: unixTimeFirst, isStateChange: true)
  sendEvent(name: 'totalElapsedTime', value: totalElapsedTime, isStateChange: true)
  sendEvent(name: 'elapsedTimeSinceLastUpdate', value: elapsedTimeSinceLastUpdate, isStateChange: true)

  sendEvent(name: 'dailyTimeWeightedAverageStartTime', value: twDailyStart, isStateChange: true)
  sendEvent(name: 'weeklyTimeWeightedAverageStartTime', value: twWeeklyStart, isStateChange: true)


  Integer nFast = settings.numSamplesFast as Integer
  Integer nSlow = settings.numSamplesSlow as Integer

  // Update humidity values and send events
  BigDecimal humidityPrevious = device.currentValue('humidityCurrent')
  if(humidityPrevious == null) { humidityPrevious = humidityCurrent}
  sendEvent(name: 'humidityPrevious', value: humidityPrevious, isStateChange: true)
  sendEvent(name: 'humidityCurrent', value: humidityCurrent, isStateChange: true)

  // Calculate time weighted average and send events
  BigDecimal timeWeightedHumidity = elapsedTimeSinceLastUpdate != 0 ? elapsedTimeSinceLastUpdate * humidityCurrent : humidityCurrent
  BigDecimal totalTimeWeightedHumidity = device.currentValue('totalTimeWeightedHumidity')
  if(totalTimeWeightedHumidity == null) {
    totalTimeWeightedHumidity = timeWeightedHumidity
  } else {
    totalTimeWeightedHumidity += timeWeightedHumidity
  }
  sendEvent(name: 'totalTimeWeightedHumidity', value: totalTimeWeightedHumidity, isStateChange: true)

  BigDecimal timeWeightedAverage = totalElapsedTime != 0 ? totalTimeWeightedHumidity / totalElapsedTime : humidityCurrent
  sendEvent(name: 'timeWeightedAverage', value: timeWeightedAverage.setScale(1, BigDecimal.ROUND_HALF_UP), isStateChange: true)

  // Calculate rolling time weighted average and send events
  BigDecimal twFast = device.currentValue('timeWeightedHumidityRollingAverageFast')
  if(twFast == null) {
    twFast = humidityCurrent
  } else {
    twFast -= twFast / nFast
    twFast += humidityCurrent / nFast
  }
  sendEvent(name: 'timeWeightedHumidityRollingAverageFast', value: twFast.setScale(1, BigDecimal.ROUND_HALF_UP), isStateChange: true)

  BigDecimal twSlow = device.currentValue('timeWeightedHumidityRollingAverageSlow')
  if(twFast == null) {
    twFast = humidityCurrent
  } else {
    twFast -= twFast / nFast
    twFast += humidityCurrent / nFast
  }
  sendEvent(name: 'timeWeightedHumidityRollingAverageSlow', value: twFast.setScale(1, BigDecimal.ROUND_HALF_UP), isStateChange: true)


  // Calculate rolling averages and send events
  BigDecimal pAvgFast = device.currentValue('fastRollingAverage')
  if(pAvgFast == null) {
    pAvgFast = humidityCurrent
  } else {
    pAvgFast -= pAvgFast / nFast
    pAvgFast += humidityCurrent / nFast
  }
  sendEvent(name: 'fastRollingAverage', value: pAvgFast.setScale(1, BigDecimal.ROUND_HALF_UP), isStateChange: true)

  BigDecimal pAvgSlow = device.currentValue('slowRollingAverage')
  if(pAvgSlow == null) {
    pAvgSlow = humidityCurrent
  } else {
    pAvgSlow -=  pAvgSlow / nSlow
    pAvgSlow += humidityCurrent / nSlow
  }
  sendEvent(name: 'slowRollingAverage', value: pAvgSlow.setScale(1, BigDecimal.ROUND_HALF_UP), isStateChange: true)

  // Determine if humidity is increasing
  String isIncreasing = humidityCurrent > humidityPrevious ? 'true' : 'false'
  sendEvent(name: 'isIncreasing', value: isIncreasing, isStateChange: true)
}