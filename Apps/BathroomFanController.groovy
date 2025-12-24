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
 **/

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.hub.domain.Event
import groovy.transform.Field

#include dwinks.UtilitiesAndLoggingLibrary

definition (
  name: "Bathroom Fan Controller",
  namespace: "dwinks",
  author: "Daniel Winks",
  description: "Bathroom Fan Controller",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: ""
)

preferences { page(name: "mainPage", title: "Bathroom Fan Controller") }

// =============================================================================
// App Page & Configuration
// =============================================================================

Map mainPage() {
  dynamicPage(name: "mainPage", title: "", install: true, uninstall: true, refreshInterval: 0) {
    section("<b>Device Instructions</b>", hideable: true, hidden: false) {
      paragraph "The humidity sensor(s) you select will control the fan switch you select."
      input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Humidity Sensor(s)", multiple: true, required: true
      input "householdHumiditySensor", "capability.relativeHumidityMeasurement", title: "Optional Household Humidity Sensor, i.e. central thermostat humidity sensor", multiple: false, required: false
      input "fanSwitch", "capability.switch", title: "Fan Switch", required: true
      paragraph "The door sensor you select will turn off the fan when the door has been open for a specified time, 10 minutes by default. Optional."
      input "doorSensor", "capability.contactSensor", title: "Door Sensor", required: false
    }

    section("<b>Change Limit:</b>", hideable: true, hidden: false) {
      paragraph "Maximum change a newly received value can be from historical average without turning on fan."
      input "changeLimit", "number", title: "Change Limit", required: true, defaultValue: 2
    }

    section("<b>Max Run Time:</b>", hideable: true, hidden: false) {
      paragraph "Maximum time fan may run under any circumstances."
      input "maxRuntime", "number", title: "Max run time (minutes, 0 to disable, max 720)", required: true, defaultValue: 60, range: '0..720'
    }

    section("<b>Door Open Time:</b>", hideable: true, hidden: false) {
      paragraph 'Maximum time fan may run after opening door.'
      input "doorOpenTime", "number", title: "Max run time after door opening (minutes, 0 to disable, max 60)", required: true, defaultValue: 10, range: '0..60'
    }

    section("<b>Humidity Statistic To Track:</b>", hideable: true, hidden: false) {
      paragraph 'Humidity statistic type to track for historical value comparison.'
      input 'highHumMode', 'enum', title: 'Humidity Statistic To Track', required: true, offerAll: false, defaultValue: 'Slow Rolling Average', options: [slowRollingAverage:'Slow Rolling Average', fastRollingAverage:'Fast Rolling Average', timeWeightedAverage:'Time Weighted Average']
    }

    section("Logging", hideable: true, hidden: false) {
      input "logEnable", "bool", title: "Enable Logging", required: false, defaultValue: true
      input "debugLogEnable", "bool", title: "Enable debug logging", required: false, defaultValue: false
    }

    section('Application Name:', hideable: true, hidden: false) {
      label title: "Enter a name for this app instance", required: false, defaultValue: 'Bathroom Fan Controller'
    }
  }
}

@Field static final String humidityStaticSensor = 'humidity-stats'
String getHumidityStatSensor() { return "${app.id}-${humidityStaticSensor}" }

// =============================================================================
// Lifecycle
// =============================================================================

void configure() {
  unsubscribe()
  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
  initializeApp(child)
}

void initializeApp(ChildDeviceWrapper child) {
  humiditySensors.each { sensor -> subscribe(sensor, "humidity", humidityEvent) }
  subscribe(fanSwitch, "switch", switchEvent)
  subscribe(doorSensor, "contact", contactEvent)
  subscribe(child, highHumMode, childHumidityEvent)
}

// =============================================================================
// Child Device Management
// =============================================================================

ChildDeviceWrapper getOrCreateChildDevices(String childDNI) {
  DeviceWrapper device = app.getChildDevice(childDNI)
  if (device == null) {
    try {
      logInfo("Creating child device for tracking humidity statistics")
      device = addChildDevice('dwinks', 'Humidity Statistics', childDNI, [name: 'Humidity Statistics', label: "${app.label}: Humidity Statistics"])
    } catch (UnknownDeviceTypeException e) {
      logException 'Humidity Statistics driver not found', e
    }
  }
  return device
}

// =============================================================================
// Humidity Processing
// =============================================================================

void humidityEvent(Event event) {
  logDebug("Received humidity event: ${event.value}")
  ChildDeviceWrapper child = getOrCreateChildDevices(getHumidityStatSensor())
  BigDecimal value = new BigDecimal(event.value)
  if (value > 0 && value < 100) {
    state.currentHumidity = value.toString()
    child.logHumidityEvent(value)
  }
}

void childHumidityEvent(Event event) {
  BigDecimal trackedHumValue = new BigDecimal(event.value)
  BigDecimal currentHumidity = new BigDecimal(state.currentHumidity).setScale(1, BigDecimal.ROUND_HALF_UP)
  BigDecimal lastHumidity = (new BigDecimal(state.lastHumidity)).setScale(1, BigDecimal.ROUND_HALF_UP)
  logDebug("Tracked Humidity Value: ${trackedHumValue}")
  logDebug("Current Humidity Value: ${currentHumidity}")
  String isIncreasing = currentHumidity > lastHumidity ? 'true' : 'false'
  String decreasingSuccessively = state.isIncreasing == 'false' && isIncreasing == 'false' ? 'true' : 'false'
  state.isIncreasing = isIncreasing
  logDebug("Is Increasing: ${isIncreasing}")
  if ((currentHumidity - changeLimit) > trackedHumValue && isIncreasing == 'true') {
    fanSwitch.on()
    logDebug("Last Humidity Reading(${lastHumidity}) was great enough to trigger a fan event.")
    logDebug("Current Humidity(${currentHumidity}) was outside change limit(${changeLimit}) of Last Humidity Reading(${lastHumidity})")
  } else {
    if (decreasingSuccessively == 'true') {
      logDebug("Current Humidity(${currentHumidity}) has been decreasing successively. Turning off fan.")
      fanSwitch.off()
    }
    logDebug("Last Humidity Reading(${lastHumidity}) was not great enough to trigger a fan event.")
    logDebug("Current Humidity(${currentHumidity}) was within change limit(${changeLimit}) of Last Humidity Reading(${lastHumidity})")
  }
  state.lastHumidity = currentHumidity.toString()
}

// =============================================================================
// Fan & Door Event Handling
// =============================================================================

void switchEvent(Event event) {
  logDebug("Received switch event: ${event.value}")
  if (event.value == "off") {
    app.getState().remove('fanOnSince')
  } else if (event.value == "on") {
    state.fanOnSince = now()
    if (maxRuntime > 0) {
      logDebug("Scheduling fan shutoff for ${maxRuntime} minutes")
      runIn(maxRuntime * 60, "runtimeExceeded")
    }
  }
}

void contactEvent(Event event) {
  logDebug("Received contact event: ${event.value}")
  if (event.value == "open") {
    if (doorOpenTime > 0 && fanSwitch.currentValue("switch") == "on") {
      logDebug("Scheduling fan shutoff for ${doorOpenTime} minutes")
      runIn(doorOpenTime * 60, "doorOpenedAutoOff")
    }
  }
  else if (event.value == "closed") { unschedule("doorOpenedAutoOff") }
}

void doorOpenedAutoOff() {
  logInfo("Auto-off: ${fanSwitch.displayName} has been on with door open for ${doorOpenTime} minutes")
  fanSwitch.off()
}

void runtimeExceeded() {
  logInfo("Auto-off: ${fanSwitch.displayName} has been on for ${maxRuntime} minutes")
  fanSwitch.off()
}