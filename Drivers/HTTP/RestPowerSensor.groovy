
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

metadata {
  definition (name: 'Rest Power Sensor',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    command 'restartESP'
    capability 'Refresh'
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

  preferences {
    section {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
      input 'power', 'string', title:'Power Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'energy', 'string', title:'Total Daily Energy Sensor Name', description: '', required: true, displayDuringSetup: true
      input 'autoUpdate', 'bool', title: "Refresh peridocially?", required: true, defaultValue: true
      input 'updateInterval', 'enum', title: 'Sensor Update Interval', required: true, defaultValue: 10, options:
      [
        10:'10 Seconds',
        30:'30 Seconds',
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        240:'4 Minutes',
        300:'5 Minutes',
        600:'10 Minutes',
        900:'15 Minutes',
        1200:'20 Minutes',
        1800:'30 Minutes',
        3600:'60 Minutes'
      ]
    }
  }
}

@Field static final String UPTIME_STATE = '/sensor/uptime'
@Field static final String RESTART_ESP = '/button/restart/press'
String powerState() { return "/sensor/${power.toLowerCase().replace(' ','_')}" }
String energyState() { return "/sensor/${energy.toLowerCase().replace(' ','_')}" }

void refresh() { refreshPowerMonitor() }
void refreshPowerMonitor() {
  sendQueryAsync(powerState(), 'refreshCallback', [sensor:'power'])
  sendQueryAsync(energyState(), 'refreshCallback', [sensor:'energy'])
}

void refreshCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  Map jsonBody = response.getJson()
  logDebug(prettyJson(jsonBody))
  if(data.sensor == 'power') { sendEvent(name:'power', value:jsonBody?.value) }
  if(data.sensor == 'energy') { sendEvent(name:'energy', value:jsonBody?.value) }
}

void initialize() { configure() }
void configure() {
  refresh()
  unschedule()
  if(settings.autoUpdate != null && settings.autoUpdate == true) {
    logDebug("Autoupdate every ${settings?.updateInterval} seconds...")
    Integer interval = settings?.updateInterval as Integer
    if(interval < 60) {
      schedule(runEveryCustomSeconds(interval as Integer), 'refresh')
    }
    if(interval >= 60 && interval < 3600) {
      String cron = runEveryCustomMinutes((interval/60) as Integer)
      schedule(cron, 'refresh')
    }
    if(interval == 3600) {
      schedule(runEveryCustomHours((interval/3600) as Integer), 'refresh')
    }
  }
}

void restartESP() { sendCommandAsync(RESTART_ESP, null, null) }

void sendCommandAsync(String path, String callbackMethod, Map data = null) {
  try{
    Map params = [uri : "http://${ip}:${port}${path}"]
    logDebug("URI: ${params.uri}")
    asynchttpPost(callbackMethod, params, data)
  }
  catch(Exception e){
    if(e.message.toString() != 'OK') {
      logError(e.message)
    }
  }
  runInMillis(500, 'refresh')
}

void sendQueryAsync(String path, String callback, Map data = null) {
  Map params = [uri : "http://${ip}:${port}${path}"]
  logDebug("URI: ${params.uri}")
  try {
    asynchttpGet(callback, params, data)
  } catch (Exception e) {
    logDebug("Call failed: ${e.message}")
    return null
  }
}