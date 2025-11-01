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

// This app controls a thermostat based on occupancy and time of day.
// It uses virtual thermostats to store 'away' and 'night' mode settings.
// It remembers manual changes, so it does not fight the user.
// If a window or door is open, it can disable the thermostat to save energy.
// It uses presence sensors to determine if anyone is home.
// If everyone is away, it sets the thermostat to auto mode.
// If someone arrives home, it sets the thermostat to auto mode, restoring previous temperature settings.
// If everyone leaves, it saves the current temperature settings and turns the thermostat to 'away' temperature settings.
// At night, it can set the thermostat to a lower temperature for energy savings.
// If the user manually changes the temperature during night mode, it remembers the new setting and uses it for future night mode adjustments.
// Logging options are provided for debugging and monitoring.

#include dwinks.UtilitiesAndLoggingLibrary

definition(
  name: 'Thermostat Controller',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Control thermostats based on occupancy and time of day. Remembering manual changes, so it does not fight the user.',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {page(name: 'mainPage', title: 'Thermostat Controller')}
Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Thermostat Controller</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      input ('thermostat', 'capability.thermostat', title: 'Thermostat to control', required: true, multiple: false)
      input ('awayModeVirtualThermostat', 'capability.thermostat', title: 'Virtual Thermostat for "away" mode settings', required: true, multiple: false)
      input ('nightModeVirtualThermostat', 'capability.thermostat', title: 'Virtual Thermostat for "night" mode settings', required: true, multiple: false)
      input ('contactSensors', 'capability.contactSensor', title: 'Contact sensors to detect open windows/doors', required: false, multiple: true)
    }
    section('<br><br><hr><h2>Open Window/Door Settings</h2>') {
      input ('disableWithOpenWindowsOrDoors', 'bool', title: 'Disable thermostat when any window is open.', required: true, defaultValue: true)
      input ('openWindowDuration', 'number', title: 'Duration of minutes to wait before disabling thermostat', range: '1..30', required: true, defaultValue: 3)
    }

    section('<br><br><hr><h2>Logging</h2>') {
      input ('logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true)
      input ('debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false)
      input ('descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true)
    }

    section() {
      label title: 'Enter a name for this app instance', required: false
    }
  }
}

void configure() {
  unsubscribe()
  initialize()
}

void initialize() {
  // unschedule()
  // clearAllStates()
  subscribe(thermostat, 'thermostatEventHandler')
  subscribe(contactSensors, 'contact', 'contactSensorEventHandler')
  subscribe(location, "mode.Away", 'awayModeEventHandler')
  subscribe(location, "mode.Night", 'nightModeEventHandler')
  logDebug(location.getMode())
}

boolean everyoneIsAway() {
  return settings.awaySensors?.all { p -> p.currentValue('presence') == 'not present' }
}

void contactSensorEventHandler(Event evt) {
  logDebug("contactSensorEventHandler: ${evt.device.displayName} is ${evt.value}")
  if (evt.value == 'open') {
    runIn(openWindowDuration * 60, 'disableThermostatDueToOpenWindow', [overwrite: true])
  } else if (evt.value == 'closed') {
    if (allContactSensorsClosed()) {
      unschedule('disableThermostatDueToOpenWindow')
      logInfo('Enabling thermostat as everyone is away and all windows/doors are closed')
      thermostat.auto()
    } else {
      logInfo('Not enabling thermostat because a window/door is still open')
    }
  }
}

boolean allContactSensorsClosed() {
  return settings.contactSensors?.every { cs -> cs.currentValue('contact') == 'closed' }
}

void disableThermostatDueToOpenWindow() {
  logInfo('Disabling thermostat due to open window/door')
  thermostat.off()
}

void awayModeEventHandler(Event evt) {
  logDebug("awayModeEventHandler: ${evt.device.displayName} is ${evt.value}")
  if (evt.value == 'Away' && state.setpointsRestored == true) {
    logInfo('Location is in "Away" mode')
    // Save current high/low setpoints
    state.setPointLow = thermostat.currentValue('heatingSetpoint')
    state.setPointHigh = thermostat.currentValue('coolingSetpoint')
    // Flag to specify that we are storing setpoints that have not been restored yet
    state.setpointsRestored = false
    // Set thermostat to away mode settings
    thermostat.setHeatingSetpoint(awayModeVirtualThermostat.currentValue('heatingSetpoint'))
    thermostat.setCoolingSetpoint(awayModeVirtualThermostat.currentValue('coolingSetpoint'))
    state.currentOperatingState = 'Away'
  } else {
    logInfo('Location is not in "Away" mode')
    // Restore previous high/low setpoints
    if (state.setPointLow != null && state.setPointHigh != null) {
      thermostat.setHeatingSetpoint(state.setPointLow as Double)
      thermostat.setCoolingSetpoint(state.setPointHigh as Double)
      state.setpointsRestored = true
      state.currentOperatingState = 'Normal'
    }
  }
}

void nightModeEventHandler(Event evt) {
  logDebug("nightModeEventHandler: ${evt.device.displayName} is ${evt.value}")
  if (evt.value == 'Night' && state.setpointsRestored == true) {
    logInfo('Location is in "Night" mode')
    // Save current high/low setpoints
    state.setPointLow = thermostat.currentValue('heatingSetpoint')
    state.setPointHigh = thermostat.currentValue('coolingSetpoint')
    // Flag to specify that we are storing setpoints that have not been restored yet
    state.setpointsRestored = false
    // Set thermostat to night mode settings
    thermostat.setHeatingSetpoint(nightModeVirtualThermostat.currentValue('heatingSetpoint'))
    thermostat.setCoolingSetpoint(nightModeVirtualThermostat.currentValue('coolingSetpoint'))
    state.currentOperatingState = 'Night'
  } else {
    logInfo('Location is not in "Night" mode')
    // Restore previous high/low setpoints
    if (state.setPointLow != null && state.setPointHigh != null) {
      thermostat.setHeatingSetpoint(state.setPointLow as Double)
      thermostat.setCoolingSetpoint(state.setPointHigh as Double)
      state.setpointsRestored = true
      state.currentOperatingState = 'Normal'
    }
  }
}

boolean anyContactSensorsOpen() {
  return settings.contactSensors?.any { cs -> cs.currentValue('contact') == 'open' }
}

void thermostatEventHandler(evt) {
  logDebug("thermostatEventHandler: ${evt.device.displayName}, ${evt.type}: ${evt.value}")
}