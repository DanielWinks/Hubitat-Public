
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
  definition (name: 'Rest Power Sensor',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    command 'restartESP'
    capability "Refresh"
    command 'initialize'


    capability "PowerMeter"
    capability "VoltageMeasurement"
    capability "EnergyMeter"

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'power', 'string', title:'Power Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'voltage', 'string', title:'Voltage Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'frequency', 'string', title:'Frequency Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'energy', 'string', title:'Total Daily Energy Sensor Name', description: '', required: true, displayDuringSetup: true
    }
  }
}

String powerState() { return "/sensor/${power.toLowerCase().replace(' ','_')}" }
String voltageState() { return "/sensor/${voltage.toLowerCase().replace(' ','_')}" }
String frequencyState() { return "/sensor/${frequency.toLowerCase().replace(' ','_')}" }
String energyState() { return "/sensor/${energy.toLowerCase().replace(' ','_')}" }

void refresh() {
  sendQueryAsync(powerState(), 'switchStateCallback', [sensor:powerState()])
  sendQueryAsync(voltageState(), 'switchStateCallback', [sensor:voltageState()])
  sendQueryAsync(frequencyState(), 'switchStateCallback', [sensor:frequencyState()])
  sendQueryAsync(energyState(), 'switchStateCallback', [sensor:energyState()])
}

void switchStateCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  if(data.sensor == powerState()) { sendEvent(name:'power', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == voltageState()) { sendEvent(name:'voltage', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == frequencyState()) { sendEvent(name:'frequency', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == energyState()) { sendEvent(name:'energy', value:celsiusToFahrenheit(parseJson(response.getData()).value.toInteger())) }
}
