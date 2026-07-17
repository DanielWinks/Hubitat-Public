/**
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
 */

void logError(String message) {
  if (settings.logEnable != false) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}

void logWarn(String message) {
  if (settings.logEnable != false) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}

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

void initialize() { configure() }
void configure() {
    log.info 'Installed...'
}

void on() {
    parent?.componentOn(this.device)
}

void off() {
    parent?.componentOff(this.device)
}

void refresh() {
  parent?.componentRefresh(this.device)
}

void parse(String message) {
  if (message == 'on' || message == 'off') {
    sendEvent(
      name:'switch',
      value:message,
      descriptionText:"${device.displayName} switch has been turned ${message}"
    )
  }
}

void parse(Map message) {
  logDebug(message.toString())
  if (message.name == 'energyDuration') {
    logDebug('energyDuration')
    if (!state.energyTime) { state.energyTime = now() }
    BigDecimal duration = ( now() - state.energyTime)/60000
    BigDecimal enDurDays = (duration/(24*60)).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal enDurHours = (duration/60).setScale(1, BigDecimal.ROUND_HALF_UP)

    if (enDurDays > 1.0) {
      state.energyDuration = enDurDays + ' Days'
    } else {
      state.energyDuration = enDurHours + ' Hours'
    }
    sendEvent(name:'energyDuration', value:enDurDays, unit: 'days')
    sendEvent(name:message.name, value:message.value)
  } else {
    sendEvent(name:message.name, value:message.value)
  }
}

metadata {
  definition(name: 'Generic Component Power Monitoring Switch', namespace: 'dwinks', author: 'Daniel Winks', component: true) {
  capability 'Switch'
  capability 'PowerMeter'
  capability 'VoltageMeasurement'
  capability 'EnergyMeter'
  capability 'CurrentMeter'

  command 'resetStats'
  capability 'Refresh'

  attribute 'firmwareVersion', 'string'
  attribute 'syncStatus', 'string'
  attribute 'accessory', 'string'
  attribute 'energyDuration', 'number'
  attribute 'amperageHigh', 'number'
  attribute 'amperageLow', 'number'
  attribute 'powerHigh', 'number'
  attribute 'powerLow', 'number'
  attribute 'voltageHigh', 'number'
  attribute 'voltageLow', 'number'
  attribute 'warnings', 'number'
  attribute 'relayCH1', 'string'
  attribute 'relayCH2', 'string'
  attribute 'relayCH3', 'string'
  attribute 'relayCH4', 'string'
  attribute 'relayCH5', 'string'
  }
}

void resetStats(fullReset = true) {
  logDebug('reset...')

  runIn(2, refresh)

  // if (fullReset) {
  //   state.energyTime = new Date().time
  //   state.remove('energyDuration')
  //   device.deleteCurrentState('energyDuration')
  //   device.deleteCurrentState('warnings')
  // }
  parent?.componentResetStats(this.device)
}
