
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
import groovy.util.slurpersupport.GPathResult

metadata {
  definition (name: 'Rest WLED Light',namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability 'Switch'
    capability "Refresh"
    command 'initialize'

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

@Field static final String CHECK = '/win'
@Field static final String ON = '/win&T=1'
@Field static final String OFF = '/win&T=0'
@Field static final String PS = '/win&PL='
@Field static final String REBOOT = '/win&RB'

void initialize() {
  checkConnection()
  // runEvery3Hours('checkConnection')
  // runEvery30Minutes('refresh')
}

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
  sendCommandAsync(ON, null, null)
}

void off() {
  sendCommandAsync(OFF, null, null)
}

void restart() {
  sendCommandAsync(REBOOT, null, null)
}

void setPreset1(Integer preset) {
  sendCommandAsync("${PS}${preset}", null, null)
}

void checkConnection() {
  sendQueryAsync(CHECK, 'checkConnectionCallback')
}

void checkConnectionCallback(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    sendEvent(name:'status', value:'offline', isStateChange: true)
  } else {
    GPathResult responseData = parseXML(response.getData())
    logDebug("response.getXml() = ${response.getXml()}")
    logDebug("response.getXml() = ${response.getXml().ps}")
    logDebug("responseData = ${responseData}")
    // sendEvent(name:'status', value:'online', isStateChange: true)
    // sendEvent(name:'uptime', value: "${responseData.value.toInteger()}")
  }
}

void sendCommandAsync(String path, String callbackMethod, Map data = null) {
  try{
    Map params = [uri : "http://${ip}:${port}${path}"]
    logDebug("${params.uri}")
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
  params.put("requestContentType", "application/xml")
  params.put("contentType", "application/xml")
  logDebug("${params.uri}")
  try {
    asynchttpGet(callback, params, data)
  } catch (Exception e) {
    logDebug("Call failed: ${e.message}")
    return null
  }
}

void commandResponse(AsyncResponse response, Map data = null){
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("response.getData() = ${response.getData()}")
    sendEvent(name:'status', value:'offline', isStateChange: true)
  } else {
    sendEvent(name:'status', value:'online', isStateChange: true)
  }
}