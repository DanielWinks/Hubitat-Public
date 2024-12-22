
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
  definition (name: 'HTTP Power Monitoring Switch', namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability 'Switch'
    capability 'Refresh'
    capability "PowerMeter" //power - NUMBER, unit:W
    capability "VoltageMeasurement" //voltage - NUMBER, unit:V  frequency - NUMBER, unit:Hz
    capability "EnergyMeter" //energy - NUMBER, unit:kWh
    capability "CurrentMeter" //amperage - NUMBER, unit:A
    capability "Consumable" //consumableStatus - ENUM ["missing", "order", "maintenance_required", "good", "replace"]

    command 'restartESP'
    command 'initialize'

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

@Field static final String RELAY = 'relay'
@Field static final String RELAY_STATE = '/switch/relay'
@Field static final String VOLTAGE_STATE = '/sensor/voltage'
@Field static final String CURRENT_STATE = '/sensor/current'
@Field static final String POWER_STATE = '/sensor/power'
@Field static final String ENERGY_STATE = '/sensor/total_daily_energy'

String turnOnRelayCommandTopic() { return "${RELAY_STATE}/turn_on" }
String turnOffRelayCommandTopic() { return "${RELAY_STATE}/turn_off" }

void refresh() {
  sendQueryAsync(RELAY_STATE, 'refreshCallback', null)
  sendQueryAsync(VOLTAGE_STATE, 'refreshCallback', null)
  sendQueryAsync(CURRENT_STATE, 'refreshCallback', null)
  sendQueryAsync(POWER_STATE, 'refreshCallback', null)
  sendQueryAsync(ENERGY_STATE, 'refreshCallback', null)
}

void refreshCallback(AsyncResponse response, Map data = null){
  // logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }

  Map responseJson = parseJson(response.getData())
  processJson(responseJson)
}

void on() {
  sendCommandAsync(turnOnRelayCommandTopic(), null, null)
}

void off() {
  sendCommandAsync(turnOffRelayCommandTopic(), null, null)
}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonData = parseJson(parsedMessage.body)
  processJson(jsonData)
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  if(jsonData?.switch != null) { sendEvent(name:'switch', value:jsonData?.switch, descriptionText:"Relay is now ${jsonData?.switch}", isStateChange:true) }
  if(jsonData?.energy5Min != null) { sendEvent(name:'energy5Min', value:jsonData?.energy5Min, descriptionText:"energy5Min is now ${jsonData?.energy5Min}", isStateChange:true) }
  if(jsonData?.energy15Min != null) { sendEvent(name:'energy15Min', value:jsonData?.energy15Min, descriptionText:"energy15Min is now ${jsonData?.energy15Min}", isStateChange:true) }
  if(jsonData?.energy1Hr != null) { sendEvent(name:'energy1Hr', value:jsonData?.energy1Hr, descriptionText:"energy1Hr is now ${jsonData?.energy1Hr}", isStateChange:true) }
  if(jsonData?.energyDaily != null) { sendEvent(name:'energyDaily', value:jsonData?.energyDaily, descriptionText:"energyDaily is now ${jsonData?.energyDaily}", isStateChange:true) }
  if(jsonData?.voltage != null) { sendEvent(name:'voltage', value:jsonData?.voltage, descriptionText:"voltage is now ${jsonData?.voltage}", isStateChange:true) }
  if(jsonData?.frequency != null) { sendEvent(name:'frequency', value:jsonData?.frequency, descriptionText:"frequency is now ${jsonData?.frequency}", isStateChange:true) }
  if(jsonData?.power != null) { sendEvent(name:'power', value:jsonData?.power, descriptionText:"power is now ${jsonData?.power}", isStateChange:true) }
  if(jsonData?.amperage != null) { sendEvent(name:'amperage', value:jsonData?.amperage, descriptionText:"amperage is now ${jsonData?.amperage}", isStateChange:true) }
  if(jsonData?.energy != null) { sendEvent(name:'energy', value:jsonData?.energy, descriptionText:"energy is now ${jsonData?.energy}", isStateChange:true) }
}