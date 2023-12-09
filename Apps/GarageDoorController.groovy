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

#include dwinks.UtilitiesAndLogging

definition (
  name: 'Garage Door Controller',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Garage Door Controller',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {
  page (
    name: 'mainPage', title: 'Garage Door Controller'
  )
}

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Garage Door Controller</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<b>Device Instructions</b>', hideable: true, hidden: true) {

      paragraph 'Tilt/Contact Sensor(s) for determining door state, optional.'

      paragraph 'Relay for controlling door opener.'
    }

    section('<h2>Devices</h2>') {
      input 'doorClosedSensors', 'capability.contactSensor', title: '<b>(Required) Sensor(s) for fully closed state</b>', required: true, multiple: true
      input 'doorOpenedSensors', 'capability.contactSensor', title: '<b>(Optional) Sensor(s) for fully opened state</b>', required: false, multiple: true
      input 'relaySwitch', 'capability.switch', title: '<b>Opener Relay Switch</b>', required: true
      input 'disableModes', 'mode', title: '<b>Disable Remote Access in modes</b>', multiple: true
    }

    section('<h2>Notification Devices</h2>') {
      input 'notificationDevices', 'capability.notification', title: '<b>Notification Devices</b>', required: false, multiple: true
    }

    section('<h2>Auto-Close</h2>') {
      input 'autoCloseTimeOut', 'number', title: '<b>Auto-Close Timeout Minutes</b>', required: true, defaultValue: 30
    }

    section('Logging') {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
    }

    section() {
      label title: 'Enter a name for this app instance', required: false
    }
  }
}

void initialize() { configure() }

void configure() {
  unsubscribe()
  subscribe(relaySwitch, 'switch', switchEvent)
  subscribe(doorClosedSensors, 'contact', closedContactEvent)
  subscribe(doorOpenedSensors, 'contact', openedContactEvent)
  subscribe(getDoorController(), 'door', doorControllerEvent)
  processContactSensors()
}

void switchEvent(Event event) {
  logDebug("Received relay switch event: ${event.value}")
  if (event.value == 'on') { runInMillis(500, 'relaySwitchOff', [overwrite:true]) }
  DeviceWrapper dev = event.getDevice()
}

void relaySwitchOff() { relaySwitch.off() }
void relaySwitchOn() { relaySwitch.on() }

void processContactSensors() {
  ChildDeviceWrapper doorController = getDoorController()

  // Update state variables for all contact sensors:
  doorClosedSensors.each {DeviceWrapper sensor ->
    String name = sensor.getLabel() != null && sensor.getLabel() != '' ? sensor.getLabel() : sensor.getName()
    doorController.setState("${name}", sensor.currentState('contact').value)
  }
  doorOpenedSensors.each {DeviceWrapper sensor ->
    String name = sensor.getLabel() != null && sensor.getLabel() != '' ? sensor.getLabel() : sensor.getName()
    doorController.setState("${name}", sensor.currentState('contact').value)
  }

  String value
  // If any 'door closed' sensors are open, set garage door as being open:
  List<DeviceWrapper> openDoorClosedSensors = doorClosedSensors.findAll {DeviceWrapper doorSensor ->
    doorSensor.currentState('contact').value == 'open'
  }
  List<DeviceWrapper> closedDoorOpenSensors = doorOpenedSensors.findAll {DeviceWrapper doorSensor ->
    doorSensor.currentState('contact').value == 'closed'
  }
  // Open 'closed' sensor and no 'open' sensors:
  if (openDoorClosedSensors.size() > 0 && hasDoorOpenedSensors() == false) {
    value = 'open'
  }

  // Open 'closed' sensor and no closed 'open' sensors (ie, door between openen and closed):
  if (openDoorClosedSensors.size() > 0 && hasDoorOpenedSensors() == true && closedDoorOpenSensors.size() == 0) {
    value = 'partially open'
  }

  // Open 'closed' sensor and closed 'open' sensors:
  if (openDoorClosedSensors.size() > 0 && hasDoorOpenedSensors() == true && closedDoorOpenSensors.size() > 0) {
    value = 'open'
  }

  // Closed 'closed' sensor and no 'open' sensors:
  if (openDoorClosedSensors.size() == 0 && hasDoorOpenedSensors() == false) {
    value = 'closed'
  }

  // Closed 'closed' sensor and closed 'open' sensors:
  // (This shouldn't happen, as it would require the door being both closed and open at the same time)
  if (openDoorClosedSensors.size() == 0 && hasDoorOpenedSensors() == true && closedDoorOpenSensors.size() > 0) {
    value = 'unknown'
  }

  doorController.sendEvent(name: 'door', value: value)
  if(value == 'open' || value == 'closed') { doorController.sendEvent(name: 'contact', value: value) }
  if (value == 'open') {
    logWarn('Closing door due to Auto-Close Timeout being reached...')
    runIn(autoCloseTimeOut * 60, 'autoClose')
  }

  if (value == 'closed') {
    logDebug 'Cancelling autoClose scheduled task...'
    unschedule('autoClose')
  }
}

// Events for when a door moves to/from fully closed position:
void closedContactEvent(Event event) {
  logDebug "Closed contact event: ${event.value}"
  ChildDeviceWrapper doorController = getChildDevice(doorControllerId)
  // Wait 5 seconds and check sensors again when opening:
  if (event.value == 'open' && doorController.currentState('door').value == 'opening') {
    runInMillis(5000, 'processContactSensors', [overwrite:true])
  } else {
    processContactSensors()
  }
}

// Events for when a door moves to/from fully open position:
void openedContactEvent(Event event) {
  logDebug("Open contact event: ${event.value}")
  ChildDeviceWrapper doorController = getChildDevice(doorControllerId)
  // Wait 5 seconds and check sensors again when closing:
  if (event.value == 'open' && doorController.currentState('door').value == 'closing') {
    runInMillis(5000, 'processContactSensors', [overwrite:true])
  } else {
    processContactSensors()
  }
}

void doorControllerEvent(Event event) {
  logDebug("Received door controller event: ${event.value}")
  String message = "The garage door is currently ${event.value}"
  if (notificationDevices) { notificationDevices*.deviceNotification(message) }
}

ChildDeviceWrapper getDoorController() {
  ChildDeviceWrapper doorController = getChildDevice(doorControllerId)
  if (!doorController) {
    doorController = addChildDevice(
      'dwinks', 'Generic Component Smart Garage Door Control', getDoorControllerId(),
      [label: "${app.label}", isComponent: true]
    )
  }
  return doorController
}

String getDoorControllerId() { return "${app.id}-DoorController" }

void componentClose(DeviceWrapper device) {
  if (location.mode in settings.disableModes) { return }
  closeDoor()
}

void componentOpen(DeviceWrapper device) {
  if (location.mode in settings.disableModes) { return }
  openDoor()
}

void sendDoorEvent(String data) {
  ChildDeviceWrapper doorController = getChildDevice(doorControllerId)
  sendEvent(doorController, [name: 'door', value: data])
}

void openDoor() {
  sendDoorEvent('opening')
  relaySwitchOn()
  runIn(10, 'checkDoor')
}

void closeDoor() {
  sendDoorEvent('closing')
  relaySwitchOn()
}

void autoClose() { closeDoor() }

Boolean hasDoorOpenedSensors() {
  return doorOpenedSensors != null && doorOpenedSensors?.size() > 0
}

void checkDoor() {
  List<DeviceWrapper> openDoorClosedSensors = doorClosedSensors.findAll {DeviceWrapper doorSensor ->
    doorSensor.currentState('contact').value == 'open'
  }
  List<DeviceWrapper> closedDoorOpenSensors = doorOpenedSensors.findAll {DeviceWrapper doorSensor ->
    doorSensor.currentState('contact').value == 'closed'
  }
  if (openDoorClosedSensors.size() == 0) {
    logError('Door requested to open, no sensors detected open state!')
  }
}
