#include dwinks.UtilitiesAndLoggingLibrary

/**
 * MIT License
 * Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

// =============================================================================
// App Definition
// =============================================================================

/**
 * Defines the metadata for this Hubitat child app.
 * This app creates a virtual contact sensor that aggregates the states of multiple physical contact sensors.
 */
definition(
  name: "Contact Sensor Group Child",
  namespace: "dwinks",
  author: "Daniel Winks",
  description: "Groups multiple contact sensors into a single virtual device for simplified monitoring.",
  category: "My Apps",
  parent: "dwinks:Device Groups",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: "",
  documentationLink: ""
)

// =============================================================================
// Preferences and Pages
// =============================================================================

/**
 * Defines the preference pages for the app setup.
 */
preferences {
  page(name: "contactSwitchPage")
  page(name: "settingsPage")
}

/**
 * First setup page: Prompts for the app name.
 * @return Map The dynamic page configuration.
 */
Map contactSwitchPage() {
  return dynamicPage(name: "contactSwitchPage", title: "New Contact Group", nextPage: "settingsPage", uninstall: false, install: false) {
    section {
      label title: "Enter a name for the child app. This creates a virtual contact sensor that combines the states of multiple physical contact sensors.", required: true
    }
  }
}

/**
 * Second setup page: Configures the sensors and logging.
 * @return Map The dynamic page configuration.
 */
Map settingsPage() {
  createOrUpdateChildDevice()
  return dynamicPage(name: "settingsPage", title: "", install: true, uninstall: true) {
    section {
      paragraph "Select the contact sensors to include in this group. The virtual device will be 'closed' only when all selected sensors are closed. If any sensor is open, the virtual device will be open."
      input "contactSensors", "capability.contactSensor", title: "Contact sensors to monitor", multiple: true, required: true
    }
    section('Logging') {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
    }
  }
}

// =============================================================================
// Lifecycle Methods
// =============================================================================

/**
 * Configures subscriptions for the contact sensors.
 * Called during app setup and updates.
 * Configure method called from UtilitiesAndLoggingLibrary.
 */
void configure() {
  subscribe(contactSensors, "contact.open", contactOpenHandler)
  subscribe(contactSensors, "contact.closed", contactClosedHandler)
}

// =============================================================================
// Event Handlers
// =============================================================================

/**
 * Handles the event when a contact sensor opens.
 * Sets the virtual device to open.
 * @param evt The event object (not used in this handler).
 */
void contactOpenHandler(com.hubitat.hub.domain.Event evt) {
  logDebug ("A contact sensor opened; setting virtual device to open.")
  ChildDeviceWrapper device = getChildDevice(state.contactSwitchDevice)
  device.open()
  device.on()
}

/**
 * Handles the event when a contact sensor closes.
 * Checks if all sensors are closed; if so, sets virtual device to closed; otherwise, keeps it open.
 * @param evt The event object (not used in this handler).
 */
void contactClosedHandler(com.hubitat.hub.domain.Event evt) {
  ChildDeviceWrapper device = getChildDevice(state.contactSwitchDevice)
  Integer totalClosed = 0
  contactSensors.each { com.hubitat.app.DeviceWrapper sensor ->
    if (sensor.currentValue("contact") == "closed") {
      totalClosed++
    }
  }

  if (totalClosed < contactSensors.size()) {
    logDebug("Not all contact sensors are closed.")
    device.open()
    device.on()
  } else {
    logDebug("All contact sensors are closed.")
    device.close()
    device.off()
  }
}

// =============================================================================
// Utility Methods
// =============================================================================

/**
 * Creates or updates the virtual child device for this app.
 * Ensures a virtual contact sensor exists with the correct name.
 */
void createOrUpdateChildDevice() {
  def childDevice = getChildDevice("contactSwitch:" + app.getId())
  if (!childDevice || state.contactSwitchDevice == null) {
    logDebug "Creating Generic Component Contact Switch."
    state.contactSwitchDevice = "contactSwitch:" + app.getId()
    addChildDevice("dwinks", "Generic Component Contact Switch", "contactSwitch:" + app.getId(), 1234, [name: app.label, isComponent: false])
  } else if (childDevice && childDevice.name != app.label) {
    childDevice.name = app.label
  }
}
