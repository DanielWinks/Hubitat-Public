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

#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.genericComponentLibrary

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

