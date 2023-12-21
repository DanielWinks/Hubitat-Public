
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
    capability "PowerMeter"
    capability "VoltageMeasurement"
    capability "EnergyMeter"

    command 'restartESP'
    command 'initialize'

    attribute '5MinEnergy', 'NUMBER'
    attribute '15MinEnergy', 'NUMBER'
    attribute '1HrEnergy', 'NUMBER'

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
@Field static final String RELAY_STATE = '/switch/relay'

String turnOnRelayCommandTopic() { return "${RELAY_STATE}/turn_on" }
String turnOffRelayCommandTopic() { return "${RELAY_STATE}/turn_off" }

void refresh() {
  sendQueryAsync(RELAY_STATE, 'switchStateCallback', [switch:RELAY])
}

void switchStateCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }

  Map responseJson = parseJson(response.getData())
  String val = responseJson.state.toLowerCase()
  sendEvent(name:'switch', value:val, descriptionText:"Relay is now ${val}", isStateChange:true)
}

void on() {
  sendCommandAsync(turnOnRelayCommandTopic(), null, null)
}

void off() {
  sendCommandAsync(turnOffRelayCommandTopic(), null, null)
}
