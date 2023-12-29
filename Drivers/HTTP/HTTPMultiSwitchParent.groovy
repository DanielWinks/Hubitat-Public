
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

#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.httpLibrary

metadata {
  definition (name: 'HTTP Multi Switch',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability 'Switch'
    command 'restartESP'
    command 'checkConnection'
    command 'refresh'
    command 'initialize'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
    attribute 'acRelay1State', 'ENUM', ['on', 'off']
    attribute 'acRelay2State', 'ENUM', ['on', 'off']
    attribute 'acRelay3State', 'ENUM', ['on', 'off']
    attribute 'usbRelayState', 'ENUM', ['on', 'off']
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
    }
  }
}

@Field static final String RELAY1 = 'ac_relay_1'
@Field static final String RELAY2 = 'ac_relay_2'
@Field static final String RELAY3 = 'ac_relay_3'
@Field static final String USBRELAY = 'usb_relay'

@Field static final String RELAY1_STATE = '/switch/ac_relay_1'
@Field static final String RELAY2_STATE = '/switch/ac_relay_2'
@Field static final String RELAY3_STATE = '/switch/ac_relay_3'
@Field static final String USBRELAY_STATE = '/switch/usb_relay'

String getACRelay1DNI() { return "${device.id}-${RELAY1}" }
String getACRelay2DNI() { return "${device.id}-${RELAY2}" }
String getACRelay3DNI() { return "${device.id}-${RELAY3}" }
String getUSBRelayDNI() { return "${device.id}-${USBRELAY}" }

String turnOnACRelay1CommandTopic() { return "${RELAY1_STATE}/turn_on" }
String turnOnACRelay2CommandTopic() { return "${RELAY2_STATE}/turn_on" }
String turnOnACRelay3CommandTopic() { return "${RELAY3_STATE}/turn_on" }
String turnOnUSBRelayCommandTopic() { return "${USBRELAY_STATE}/turn_on" }

String turnOffACRelay1CommandTopic() { return "${RELAY1_STATE}/turn_off" }
String turnOffACRelay2CommandTopic() { return "${RELAY2_STATE}/turn_off" }
String turnOffACRelay3CommandTopic() { return "${RELAY3_STATE}/turn_off" }
String turnOffUSBRelayCommandTopic() { return "${USBRELAY_STATE}/turn_off" }

void createChildDevices() {
  if (getChildDevice("${getACRelay1DNI()}") == null) {
    ChildDeviceWrapper relay1 = addChildDevice(
      'dwinks', 'Generic Component Switch', "${getACRelay1DNI()}",
      [ name: 'Generic Component Switch', label: "${device.label != 'null' && device.label != '' ? device.label : device.name}" + ' AC Relay 1' ]
    )
  }

  if (getChildDevice("${getACRelay2DNI()}") == null) {
    ChildDeviceWrapper relay2 = addChildDevice(
      'dwinks', 'Generic Component Switch', "${getACRelay2DNI()}",
      [ name: 'Generic Component Switch', label: "${device.label != 'null' && device.label != '' ? device.label : device.name}" + ' AC Relay 2' ]
    )
  }

  if (getChildDevice("${getACRelay3DNI()}") == null) {
    ChildDeviceWrapper relay3 = addChildDevice(
      'dwinks', 'Generic Component Switch', "${getACRelay3DNI()}",
      [ name: 'Generic Component Switch', label: "${device.label != 'null' && device.label != '' ? device.label : device.name}" + ' AC Relay 3' ]
    )
  }

    if (getChildDevice("${getUSBRelayDNI()}") == null) {
    ChildDeviceWrapper relayUsb = addChildDevice(
      'dwinks', 'Generic Component Switch', "${getUSBRelayDNI()}",
      [ name: "Generic Component Switch", label: "${device.label != 'null' && device.label != '' ? device.label : device.name}" + ' USB Relay' ]
    )
  }
}

void refresh() {
  sendQueryAsync(RELAY1_STATE, 'switchStateCallback', [switch:RELAY1])
  sendQueryAsync(RELAY2_STATE, 'switchStateCallback', [switch:RELAY2])
  sendQueryAsync(RELAY3_STATE, 'switchStateCallback', [switch:RELAY3])
  sendQueryAsync(USBRELAY_STATE, 'switchStateCallback', [switch:USBRELAY])
}

void switchStateCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }

  Map responseJson = parseJson(response.getData())
  String childState = responseJson.state.toLowerCase()

  if(data.switch == RELAY1) { getChildDevice("${getACRelay1DNI()}").parse(childState) }
  if(data.switch == RELAY2) { getChildDevice("${getACRelay2DNI()}").parse(childState) }
  if(data.switch == RELAY3) { getChildDevice("${getACRelay3DNI()}").parse(childState) }
  if(data.switch == USBRELAY) { getChildDevice("${getUSBRelayDNI()}").parse(childState) }
  if(
    getChildDevice("${getACRelay1DNI()}")?.currentState("switch")?.value == 'on' &&
    getChildDevice("${getACRelay2DNI()}")?.currentState("switch")?.value == 'on' &&
    getChildDevice("${getACRelay3DNI()}")?.currentState("switch")?.value == 'on' &&
    getChildDevice("${getUSBRelayDNI()}")?.currentState("switch")?.value == 'on'
    ) {
    sendEvent(name:'switch', value:'on', descriptionText:'All relays are now on', isStateChange:true)
  } else {
    sendEvent(name:'switch', value:'off', descriptionText:'All relays are now off', isStateChange:true)
  }
}


void on() {
  sendCommandAsync(turnOnACRelay1CommandTopic(), null, null)
  sendCommandAsync(turnOnACRelay2CommandTopic(), null, null)
  sendCommandAsync(turnOnACRelay3CommandTopic(), null, null)
}

void off() {
  sendCommandAsync(turnOffACRelay1CommandTopic(), null, null)
  sendCommandAsync(turnOffACRelay2CommandTopic(), null, null)
  sendCommandAsync(turnOffACRelay3CommandTopic(), null, null)
}

void componentOff(DeviceWrapper device) {
  String dni = "${device.deviceNetworkId}"
  if (dni == "${getACRelay1DNI()}") { sendCommandAsync(turnOffACRelay1CommandTopic(), null, null) }
  if (dni == "${getACRelay2DNI()}") { sendCommandAsync(turnOffACRelay2CommandTopic(), null, null) }
  if (dni == "${getACRelay3DNI()}") { sendCommandAsync(turnOffACRelay3CommandTopic(), null, null) }
  if (dni == "${getUSBRelayDNI()}") { sendCommandAsync(turnOffUSBRelayCommandTopic(), null, null) }
}

void componentOn(DeviceWrapper device) {
  String dni = "${device.deviceNetworkId}"
  if (dni == "${getACRelay1DNI()}") { sendCommandAsync(turnOnACRelay1CommandTopic(), null, null) }
  if (dni == "${getACRelay2DNI()}") { sendCommandAsync(turnOnACRelay2CommandTopic(), null, null) }
  if (dni == "${getACRelay3DNI()}") { sendCommandAsync(turnOnACRelay3CommandTopic(), null, null) }
  if (dni == "${getUSBRelayDNI()}") { sendCommandAsync(turnOnUSBRelayCommandTopic(), null, null) }
}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonBody = parseJson(parsedMessage.body)
  logDebug(prettyJson(jsonBody))

  String childState = jsonBody.state.toLowerCase()

  if(jsonBody.switch == RELAY1) { getChildDevice("${getACRelay1DNI()}").parse(childState) }
  if(jsonBody.switch == RELAY2) { getChildDevice("${getACRelay2DNI()}").parse(childState) }
  if(jsonBody.switch == RELAY3) { getChildDevice("${getACRelay3DNI()}").parse(childState) }
  if(jsonBody.switch == USBRELAY) { getChildDevice("${getUSBRelayDNI()}").parse(childState) }
  if(
    getChildDevice("${getACRelay1DNI()}")?.currentState("switch")?.value == 'on' &&
    getChildDevice("${getACRelay2DNI()}")?.currentState("switch")?.value == 'on' &&
    getChildDevice("${getACRelay3DNI()}")?.currentState("switch")?.value == 'on' &&
    getChildDevice("${getUSBRelayDNI()}")?.currentState("switch")?.value == 'on'
    ) {
    sendEvent(name:'switch', value:'on', descriptionText:'All relays are now on', isStateChange:true)
  } else {
    sendEvent(name:'switch', value:'off', descriptionText:'All relays are now off', isStateChange:true)
  }
}