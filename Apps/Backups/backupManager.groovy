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
*/

#include dwinks.UtilitiesAndLogging

definition(
  name: 'Backup Manager',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'Utility',
  description: 'Manages Hubitat Backups',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: true,
  iconX3Url: '',
  singleThreaded: true
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
}

mappings {
  path('/latest') { action: [ GET: 'latestUrl' ] }
}

Map mainPage() {
  if(!state.accessToken){ createAccessToken() }

  String localUri = getLocalUri()
  String cloudUri = getCloudUri()

  return dynamicPage(title: 'Backup Manager') {

    section("Webhooks") {
      paragraph("<ul><li><strong>Local</strong>: <a href='${localUri}' target='_blank' rel='noopener noreferrer'>${localUri}</a></li></ul>")
      paragraph("<ul><li><strong>Cloud</strong>: <a href='${cloudUri}' target='_blank' rel='noopener noreferrer'>${cloudUri}</a></li></ul>")
    }

    section {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: false
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: false
    }
  }
}

String getLocalUri() {
  return getFullLocalApiServerUrl() + "/latest?access_token=${state.accessToken}"
}

String getCloudUri() {
  return "${getApiServerUrl()}/${hubUID}/apps/${app.id}/latest?access_token=${state.accessToken}"
}

void initialize() { configure() }

void configure() {
  if (state.accessToken == null) { createAccessToken() }
  latestUrl()
}

Map latestUrl() {
  String fw = "${location.hub.firmwareVersionString}"
  String url = "${getLocalApiServerUrl().replace('apps/api','hub/backupDB?fileName=')}"

  Date d = new Date()
  Integer year = d.year + 1900
  String month = String.format('%02d', d.month + 1)
  String day = String.format('%02d', d.date)

  state.latestUrl = "${url}${year}-${month}-${day}~${fw}.lzf"
  return render(contentType: 'text/html', data: "${url}${year}-${month}-${day}~${fw}.lzf", status: 200)
}
