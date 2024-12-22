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
  name: 'HTTP Breaker Box Meter',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'HTTP',
  description: '',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: true,
  iconX3Url: '',
  singleThreaded: false,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/HttpBreakerMeter.groovy'
)


preferences { page(name: 'mainPage', title: 'HTTP Breaker Box Meter ') }

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>HTTP Breaker Box Meter</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    tryCreateAccessToken()
    if(!state.accessToken) {
      section ("<h2 style='color:red;'>OAuth is not enabled for app!! Please enable in Apps Code.</h2>"){      }
    }

    section('<h2>Devices</h2>') {
      input 'textarea', 'textarea', title: 'Disable "to away" presence changes in Mode(s)'
    }

    section("Webhooks") {
      paragraph("<ul><li><strong>Local Arrive</strong>: <a href='${getLocalPostEndpoint()}' target='_blank' rel='noopener noreferrer'>${getLocalPostEndpoint()}</a></li></ul>")
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

mappings { path("/update") { action: [ POST: "updateWebhook"] } }

Map updateWebhook() {
  logDebug(prettyJson(parseJson(request.body)))
  Map jsonData = parseJson(request.body)
  jsonData?.circuits.each{circuit ->
    circuit.each{k,v -> logDebug("Recieved: ${k}:${v}")}
  }
  return render(contentType: "text/html", data: "<p>Received!</p>", status: 200)
}

Map departWebhook() {
  // if (disableModes?.contains(location.mode)) {
  //   logDebug("Skipping depart change due to mode exclusion...")
  //   return render(contentType: "text/html", data: "<p>Not Departed due to mode exclusion</p>", status: 200)
  // }
  // runIn(leaveDelay as Integer, 'depart', [overwrite:true])
  // return render(contentType: "text/html", data: "<p>Departed</p>", status: 200)
}

void depart() {
  // motionSensors.each{ p -> p.inactive() }
}

String getLocalPostEndpoint() { return "${getFullLocalApiServerUrl()}/update?access_token=${state.accessToken}" }
// String getCloudUriArrive() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/arrive?access_token=${state.accessToken}" }

// String getLocalUriDepart() { return getFullLocalApiServerUrl() + "/depart?access_token=${state.accessToken}" }
// String getCloudUriDepart() { return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/depart?access_token=${state.accessToken}" }

void initialize() { configure() }

void configure() {
  if (state.accessToken == null) {
    logDebug('Creating Access Token...')
    tryCreateAccessToken()
    logDebug("accessToken: ${state.accessToken}")
  }
}
