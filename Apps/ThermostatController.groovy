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

import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event

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

// =============================================================================
// Lifecycle Management
// =============================================================================

void configure() {
  unsubscribe()
  initialize()
}

void initialize() {
  ensureStateDefaults()
  subscribe(thermostat, 'thermostatEventHandler')
  subscribe(contactSensors, 'contact', 'contactSensorEventHandler')
  subscribe(location, 'mode.Away', 'awayModeEventHandler')
  subscribe(location, 'mode.Night', 'nightModeEventHandler')
  logDebug("Active location mode: ${location.getMode()}")
}

private void ensureStateDefaults() {
  if (state.setpointsRestored == null) {
    state.setpointsRestored = true
  }
  if (!state.currentOperatingState) {
    state.currentOperatingState = 'Normal'
  }
}

// =============================================================================
// Contact Sensor Handling
// =============================================================================

void contactSensorEventHandler(Event evt) {
  logDebug("contactSensorEventHandler: ${evt.device.displayName} is ${evt.value}")
  if (evt.value == 'open') {
    runIn(openWindowDuration * 60, 'disableThermostatDueToOpenWindow', [overwrite: true])
    return
  }

  if (evt.value == 'closed') {
    if (allContactSensorsClosed()) {
      unschedule('disableThermostatDueToOpenWindow')
      logInfo('All windows/doors closed; restoring thermostat automation')
      thermostat.auto()
    } else {
      logInfo('Another window/door remains open; keeping thermostat disabled')
    }
  }
}

void disableThermostatDueToOpenWindow() {
  logInfo('Disabling thermostat due to open window/door')
  thermostat.off()
}

private boolean allContactSensorsClosed() {
  return settings.contactSensors?.every { cs -> cs.currentValue('contact') == 'closed' } ?: true
}

boolean anyContactSensorsOpen() {
  return settings.contactSensors?.any { cs -> cs.currentValue('contact') == 'open' } ?: false
}

// =============================================================================
// Mode Coordination
// =============================================================================

void awayModeEventHandler(Event evt) {
  handleModeChange('Away', awayModeVirtualThermostat, evt)
}

void nightModeEventHandler(Event evt) {
  handleModeChange('Night', nightModeVirtualThermostat, evt)
}

private void handleModeChange(String modeName, DeviceWrapper modeThermostat, Event evt) {
  logDebug("${modeName} mode event: ${evt.device.displayName} reported ${evt.value}")
  if (evt.value == modeName && state.setpointsRestored == true) {
    logInfo("Location entered \"${modeName}\" mode")
    if (storeCurrentSetpoints()) {
      applyVirtualThermostatSetpoints(modeThermostat, modeName)
    }
    return
  }

  if (evt.value != modeName && state.setpointsRestored == false) {
    logInfo("Location exited \"${modeName}\" mode")
    restoreStoredSetpoints()
  }
}

private boolean storeCurrentSetpoints() {
  BigDecimal low = thermostat.currentValue('heatingSetpoint') as BigDecimal
  BigDecimal high = thermostat.currentValue('coolingSetpoint') as BigDecimal
  if (low == null || high == null) {
    logWarn('Unable to capture current setpoints; heating or cooling value missing')
    return false
  }
  state.setPointLow = low
  state.setPointHigh = high
  state.setpointsRestored = false
  return true
}

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
  state.currentOperatingState = modeName
}

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
  state.currentOperatingState = 'Normal'
}