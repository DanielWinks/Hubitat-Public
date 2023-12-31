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

definition(
  name: 'HTTP Presence Advanced',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'HTTP',
  description: '',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: true,
  iconX3Url: '',
  singleThreaded: true
)


preferences {
  page (
    name: 'mainPage', title: 'Webhook Presence Helper'
  )
}

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Webhook Presence Helper</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      input 'presenceSensors', 'capability.presenceSensor', title: 'Presence Sensor', required: true, multiple: false
      input 'disableModes', 'mode', title: 'Disable "to away" presence changes in Mode(s)', multiple: true
    }

    section('<h2>Leave delay</h2>') {
      input 'leaveDelay', 'number', title: 'Delay "leave" changes by X seconds', required: true, defaultValue: 0
      input 'justArrivedTime', 'enum', title: 'Time to consider "Just Arrived"', required: true, defaultValue: 1, options:
      [
        1:'1 Minute',
        2:'2 Minutes',
        3:'3 Minutes',
        4:'4 Minutes',
        5:'5 Minutes',
        10:'10 Minutes'
      ]
      input 'justDepartedTime', 'enum', title: 'Time to consider "Just Departed"', required: true, defaultValue: 1, options:
      [
        1:'1 Minute',
        2:'2 Minutes',
        3:'3 Minutes',
        4:'4 Minutes',
        5:'5 Minutes',
        10:'10 Minutes'
      ]
      input 'ExtendedPresentTime', 'enum', title: 'Time to consider "Extended Present"', required: true, defaultValue: 30, options:
      [
        15:'15 Minutes',
        20:'20 Minutes',
        30:'30 Minutes',
        45:'45 Minutes',
        60:'60 Minutes',
        90:'90 Minutes',
        120:'120 Minutes',
        240:'240 Minutes'
      ]
      input 'ExtendedAwayTime', 'enum', title: 'Time to consider "Extended Away"', required: true, defaultValue: 30, options:
      [
        15:'15 Minutes',
        20:'20 Minutes',
        30:'30 Minutes',
        45:'45 Minutes',
        60:'60 Minutes',
        90:'90 Minutes',
        120:'120 Minutes',
        240:'240 Minutes'
      ]
    }

    section("Webhooks") {
      paragraph("<ul><li><strong>Local Arrive</strong>: <a href='${getLocalUriArrive()}' target='_blank' rel='noopener noreferrer'>${getLocalUriArrive()}</a></li></ul>")
      paragraph("<ul><li><strong>Cloud Arrive</strong>: <a href='${getCloudUriArrive()}' target='_blank' rel='noopener noreferrer'>${getCloudUriArrive()}</a></li></ul>")
      paragraph("<ul><li><strong>Local Depart</strong>: <a href='${getLocalUriDepart()}' target='_blank' rel='noopener noreferrer'>${getLocalUriDepart()}</a></li></ul>")
      paragraph("<ul><li><strong>Cloud Depart</strong>: <a href='${getCloudUriDepart()}' target='_blank' rel='noopener noreferrer'>${getCloudUriDepart()}</a></li></ul>")
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


mappings {
  path("/arrive") { action: [ GET: "arriveWebhook"] }
  path("/depart") { action: [ GET: "departWebhook"] }
}

Map arriveWebhook() {
  unschedule()
  presenceSensor.each{ p -> p.arrived() }
  return render(contentType: "text/html", data: "<p>Arrived!</p>", status: 200)
}

Map departWebhook() {
  if (disableModes?.contains(location.mode)) {
    logDebug("Skipping depart change due to mode exclusion...")
    return render(contentType: "text/html", data: "<p>Not Departed due to mode exclusion</p>", status: 200)
  }
  runIn(leaveDelay as Integer, 'depart', [overwrite:true])
  return render(contentType: "text/html", data: "<p>Departed</p>", status: 200)
}

void depart() {
  presenceSensor.each{ p -> p.departed() }
}

String getLocalUriArrive() { return getFullLocalApiServerUrl() + "/arrive?access_token=${state.accessToken}" }
String getCloudUriArrive() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/arrive?access_token=${state.accessToken}" }

String getLocalUriDepart() { return getFullLocalApiServerUrl() + "/depart?access_token=${state.accessToken}" }
String getCloudUriDepart() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/depart?access_token=${state.accessToken}" }

void initialize() { configure() }

void configure() {
  if (state.accessToken == null) {
    logDebug('Creating Access Token...')
    createAccessToken()
    logDebug("accessToken: ${state.accessToken}")
  }
}
