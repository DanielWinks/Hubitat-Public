
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
  definition (name: 'HTTP Breaker Box Power Monitor', namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability "PowerMeter" //power - NUMBER, unit:W
    capability "VoltageMeasurement" //voltage - NUMBER, unit:V  frequency - NUMBER, unit:Hz
    capability "EnergyMeter" //energy - NUMBER, unit:kWh
    capability "CurrentMeter" //amperage - NUMBER, unit:A

    command 'restartESP'
    command 'createChildDevices'
    command 'checkConnection'

    attribute 'energy5Min', 'NUMBER'
    attribute 'energy15Min', 'NUMBER'
    attribute 'energy1Hr', 'NUMBER'
    attribute 'energyDaily', 'NUMBER'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
    }
  }
}

void refresh() {}
void refreshCallback(AsyncResponse response, Map data = null){}

String getChildDNI(String name) {
  return "${device.id}-${name.replace(' ','')}"
}

ChildDeviceWrapper getOrCreateChildDevice(String name) {
  ChildDeviceWrapper child = getChildDevice(getChildDNI(name))
  if (child == null) {
    child = addChildDevice(
      'dwinks',
      'Generic Component Power Monitor',
      "${getChildDNI(name)}",
      [ name: 'Generic Component Power Monitor', label: "Power Monitor - ${name}" ]
    )
  }
  return child
}

void murderChildren() {deleteChildDevices()}


void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonData = parseJson(parsedMessage.body)
  processJson(jsonData)
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  if(jsonData?.id != null && jsonData?.id != ''){
    ChildDeviceWrapper child = getOrCreateChildDevice(jsonData?.id)
    if(jsonData?.measurement == 'Power') {
      child.sendEvent(name: jsonData?.measurement.toLowerCase(), value: jsonData?.value)
    } else if(jsonData?.measurement == 'Energy'){
      child.sendEvent(name: jsonData?.measurement.toLowerCase(), value: new BigDecimal(jsonData?.value).setScale(2, BigDecimal.ROUND_HALF_UP))
    } else {
      child.sendEvent(name: jsonData?.measurement.toLowerCase(), value: new BigDecimal(jsonData?.value).setScale(1, BigDecimal.ROUND_HALF_UP))
    }

  }
}