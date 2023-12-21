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
  name: 'ESPHome Device Helper',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'HTTP',
  description: 'Provides HTTP Endpoint For ESPHome Devices To Publish Updates',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: true,
  iconX3Url: '',
  singleThreaded: false
)

preferences { page name:"pageMain" }

mappings {
  path("/webhook") { action: [ GET: "webhook"] }
}

void initialize() { configure() }

Map webhook() {
  runIn(0, 'processWebhookData', [data: [params:params]])
}

void processWebhookData(Map data) {
  Map params = data.params
  (switches+sensors).findAll{ sw -> sw.getDeviceNetworkId() == params['dni'] }.each{ sw ->
    sw.sendEvent([name: params['component'], value: params['action']])
  }
}

String getLocalUri() {
  return getFullLocalApiServerUrl() + "/webhook?access_token=${state.accessToken}"
}

String getLocalPostUri() {
  return getFullLocalApiServerUrl() + "/posthook?access_token=${state.accessToken}"
}

String getCloudUri() {
  return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/webhook?access_token=${state.accessToken}"
}

void appButtonHandler(String buttonName) {
  if(buttonName == 'updateESPHomeDev') { updateAllESPDevices() }
}

void updateAllESPDevices() {
  (switches+sensors).each{ s -> s.updateDataValue('hub_webhook_url', getLocalUri()) }
  (switches+sensors).each{ s -> s.updateDataValue('hub_posthook_url', getLocalPostUri()) }
}

Map pageMain(){
  if(!state.accessToken){
    createAccessToken()
  }

  String localUri = getLocalUri()
  String cloudUri = getCloudUri()

  return dynamicPage(name: "pageMain", install: true,  uninstall: true, refreshInterval:0) {
    section("<h2>Devices</h2>") {
      paragraph "Select devices below to allow app to recieve and publish status updates for them."
      input "switches", "capability.switch", title: "<b>ESPHome Switches/Plugs</b>", required: false, multiple: true
      input "sensors", "capability.sensor", title: "<b>ESPHome Sensors</b>", required: false, multiple: true
    }

    section("Webhooks") {
      paragraph("<ul><li><strong>Local</strong>: <a href='${localUri}' target='_blank' rel='noopener noreferrer'>${localUri}</a></li></ul>")
      paragraph("<ul><li><strong>Cloud</strong>: <a href='${cloudUri}' target='_blank' rel='noopener noreferrer'>${cloudUri}</a></li></ul>")
    }

    section {
      input "logEnable", "bool", title: "Enable Logging", required: false, defaultValue: true
      input "debugLogEnable", "bool", title: "Enable debug logging", required: false, defaultValue: false
      input "descriptionTextEnable", "bool", title: "Enable descriptionText logging", required: false, defaultValue: true
      input "updateESPHomeDev", "button", title: "Update ESPHome Devices With Webhook Data"
    }
  }
}

void configure() {
  logInfo "${app.name} configuration updated"
  if (state.accessToken == null) {
    logDebug 'Creating Access Token...'
    createAccessToken()
    logDebug "accessToken: ${state.accessToken}"
  }
  updateAllESPDevices()
}


