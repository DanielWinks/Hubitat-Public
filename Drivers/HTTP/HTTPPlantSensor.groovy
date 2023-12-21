
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
  definition (name: 'HTTP Plant Sensor',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    command 'restartESP'
    capability 'Refresh'
    capability 'Sensor'
    command 'initialize'


    capability 'IlluminanceMeasurement' // attribute 'illuminance', 'NUMBER'
    attribute 'soilMoisture', 'NUMBER'
    attribute 'soilConductivity', 'NUMBER'
    capability 'TemperatureMeasurement' // attribute 'temperature', 'NUMBER'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'illuminanceName', 'string', title:'Illuminance Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'moistureName', 'string', title:'Moisture Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'conductivityName', 'string', title:'Soil Conductivity Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'temperatureName', 'string', title:'Temperature Sensor Name', description: '', required: true, displayDuringSetup: true
    }
  }
}

String illuminanceState() { return "/sensor/${illuminanceName.toLowerCase().replace(' ','_')}" }
String moistureState() { return "/sensor/${moistureName.toLowerCase().replace(' ','_')}" }
String conductivityState() { return "/sensor/${conductivityName.toLowerCase().replace(' ','_')}" }
String temperatureState() { return "/sensor/${temperatureName.toLowerCase().replace(' ','_')}" }

void refresh() {
  sendQueryAsync(illuminanceState(), 'sensorCallback', [sensor:illuminanceState()])
  sendQueryAsync(moistureState(), 'sensorCallback', [sensor:moistureState()])
  sendQueryAsync(conductivityState(), 'sensorCallback', [sensor:conductivityState()])
  sendQueryAsync(temperatureState(), 'sensorCallback', [sensor:temperatureState()])
}

void sensorCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  if(data.sensor == illuminanceState()) { sendEvent(name:'illuminance', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == moistureState()) { sendEvent(name:'soilMoisture', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == conductivityState()) { sendEvent(name:'soilConductivity', value:parseJson(response.getData()).value.toInteger()) }
  if(data.sensor == temperatureState()) { sendEvent(name:'temperature', value:celsiusToFahrenheit(parseJson(response.getData()).value.toInteger())) }
}
