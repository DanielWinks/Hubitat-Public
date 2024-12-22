/**
 *  MIT License
 *  Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the 'Software'), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 **/

#include dwinks.UtilitiesAndLoggingLibrary

import hubitat.helper.NetworkUtils
import hubitat.helper.NetworkUtils.PingData

metadata {
  definition (name: 'Moonraker 3D Printer',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability 'Sensor'

    command 'shutdownHost'
    command 'emergencyStop'
    command 'restartFirmware'
    command 'getStatus'
    attribute 'hostName', 'STRING'
    attribute 'stateMessage', 'STRING'
    attribute 'klippyState', 'STRING'
    attribute 'printState', 'ENUM', ['standby','printing','paused','complete','cancelled','error']
    attribute 'hostState', 'ENUM', ['on', 'off']
    attribute 'bedTempActual', 'NUMBER'
    attribute 'bedTempTarget', 'NUMBER'
    attribute 'extruderTempActual', 'NUMBER'
    attribute 'extruderTempTarget', 'NUMBER'
    attribute 'layerTotal', 'NUMBER'
    attribute 'layerCurrent', 'NUMBER'
    attribute 'isPaused', 'BOOLEAN'
    attribute 'print_duration', 'NUMBER'
    attribute 'total_duration', 'NUMBER'
  }

  preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'idleTimer', 'number', title:'Update interval when not printing', description: 'in minutes', required: true, displayDuringSetup: true, defaultValue: '1', range:'1..59'
      input 'printingTimer', 'number', title:'Update interval when printing', description: 'in seconds', required: true, displayDuringSetup: true, defaultValue: '15', range:'5..300'
      input 'offlineTimer', 'number', title:'Update interval when offline', description: 'in minutes', required: true, displayDuringSetup: true, defaultValue: '1', range:'1..59'
    }
  }
}

void initialize() { configure() }
void configure() { getStatus() }

void scheduleIdle() { runIn((idleTimer as Integer) * 60, 'getStatus', [overwrite:true, misfire:'ignore']) }
void scheduleOffline() { runIn((offlineTimer as Integer) * 60, 'getStatus', [overwrite:true, misfire:'ignore']) }
void schedulePrinting() {  runIn((printingTimer as Integer), 'getStatus', [overwrite:true, misfire:'ignore']) }

void shutdownHost() { sendCommandAsync('/machine/shutdown') }
void rebootHost() { sendCommandAsync('/machine/reboot') }
void emergencyStop() { sendCommandAsync('/printer/emergency_stop') }
void restartFirmware() { sendCommandAsync('/printer/firmware_restart') }

void getStatus() {
  queryPrinterAsync('/printer/info', 'printerInfoCallback')
  queryPrinterAsync('/printer/objects/query?toolhead=print_time,estimated_print_time&extruder=target,temperature&heater_bed=target,temperature&print_stats&pause_resume', 'printerQueryCallback')
}


void sendCommandAsync(String path) {
  try{
    Map params = [uri : "http://${ip}:${port}${path}"]
    asynchttpPost('commandResponse', params)
  }
  catch(Exception e){
    if(e.message.toString() != 'OK') {
      logError(e.message)
    }
  }
}

void commandResponse(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(!response.hasError()) {
    logDebug("response.getData() = ${response.getData()}")
  }
}

void queryPrinterAsync(String path, String callback) {
  Map params = [uri : "http://${ip}:${port}${path}"]
  try {
    asynchttpGet(callback, params)
  } catch (Exception e) {
    logDebug('Call failed: ${e.message}')
    return null
  }
}

void printerInfoCallback(AsyncResponse response, Map data = null) {
  if( response.hasError()) {
    logDebug("Response Error: ${response.getErrorMessage()}")
    printerOff()
    return
  }
  Map res = response.getJson().result
  logDebug(prettyJson(res))

  printerOn()
  sendEvent(name:'klippyState', value:res.state, descriptionText:"Klippy is now ${res.state}", isStateChange:true)
  sendEvent(name:'stateMessage', value:res.state_message, descriptionText:"${res.state_message}", isStateChange:true)
}

void printerQueryCallback(AsyncResponse response, Map data = null) {
  if( response.hasError()) {
    logDebug("Response Error: ${response.getErrorMessage()}")
    printerOff()
    return
  }
  printerOn()
  Map res = response.getJson().result.status
  logDebug(prettyJson(res))

  Map heater_bed = res.heater_bed
  Map extruder = res.extruder
  Map print_stats = res.print_stats

  Map bedTemps = ['actual':heater_bed.temperature.toInteger(), 'target':heater_bed.target.toInteger()]
  Map extruderTemps = ['actual':extruder.temperature.toInteger(), 'target':extruder.target.toInteger()]
  sendEvent(name:'bedTempActual', value:bedTemps.actual, descriptionText:"Bed temp actual is now ${bedTemps.actual}", isStateChange:false)
  sendEvent(name:'bedTempTarget', value:bedTemps.target, descriptionText:"Bed temp target is now ${bedTemps.target}", isStateChange:false)
  sendEvent(name:'extruderTempActual', value:extruderTemps.actual, descriptionText:"Extruder temp actual is now ${extruderTemps.actual}", isStateChange:false)
  sendEvent(name:'extruderTempTarget', value:extruderTemps.target, descriptionText:"Extruder temp target is now ${extruderTemps.target}", isStateChange:false)
  sendEvent(name:'print_duration', value:print_stats.print_duration.setScale(0, BigDecimal.ROUND_HALF_UP), isStateChange: false )
  sendEvent(name:'total_duration', value:print_stats.total_duration.setScale(0, BigDecimal.ROUND_HALF_UP), isStateChange: false )
  sendEvent(name:'printState', value:print_stats.state, descriptionText:"Printer is now ${print_stats.state}", isStateChange:false)
  if(print_stats.state == 'printing') { schedulePrinting() }
  else { scheduleIdle() }

  Integer total_layer = print_stats.info.total_layer ? print_stats.info.total_layer.toInteger() : 0
  sendEvent(name:'layerTotal', value:total_layer)

  Integer current_layer = print_stats.info.current_layer ? print_stats.info.current_layer.toInteger() : 0
  sendEvent(name:'layerCurrent', value:current_layer, descriptionText:"Current layer is now ${current_layer}", isStateChange:true)
}

void pingUntilOff() {
  PingData pd = hubitat.helper.NetworkUtils.ping("${ip}")
  if (pd.packetLoss == 3) {
    printerOff()
  } else {
    logDebug('Pinging again in 3 seconds...')
    runIn(3, 'pingUntilOff')
  }
}

void printerOn() {
  sendEvent(name:'hostState', value:'on', isStateChange: true)
}

void printerOff() {
  sendEvent(name:'hostState', value:'off', isStateChange: true)
  scheduleOffline()
}

