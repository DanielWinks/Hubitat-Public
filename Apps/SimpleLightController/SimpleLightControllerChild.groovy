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

import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event

void logInfo(String message) {
  if (settings.logEnable != false) {
    log.info "${app.label ?: app.name}: ${message}"
  }
}

void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    log.debug "${app.label ?: app.name}: ${message}"
  }
}

definition(
  name: 'Simple Light Controller Child',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Turn a light on/off based on a contact sensor state.',
  category: 'Convenience',
  parent: 'dwinks:Simple Light Controller Parent',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {
  page(
    name: 'mainPage', title: 'Simple Light Controller'
  )
}

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Simple Light Controller</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      input('contactSensor', 'capability.contactSensor', title: 'Contact Sensor', required: true, multiple: false)
      input('lightSwitch', 'capability.switch', title: 'Light/Switch to Control', required: true, multiple: false)
    }

    section('<h2>Settings</h2>') {
      input('invertLogic', 'bool', title: 'Invert logic (contact open = light off, contact closed = light on)', required: false, defaultValue: false)
    }

    section('Logging') {
      input('logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true)
      input('debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false)
      input('descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true)
    }

    section() {
      label title: 'Enter a name for this app instance', required: true
    }
  }
}

void installed() {
  logInfo('Installed')
  initialize()
}

void updated() {
  logInfo('Updated')
  unsubscribe()
  initialize()
}

void initialize() {
  subscribe(contactSensor, 'contact.open', 'contactOpenHandler')
  subscribe(contactSensor, 'contact.closed', 'contactClosedHandler')
}

void contactOpenHandler(Event evt) {
  logDebug("Contact opened: ${evt.device}")
  if (settings.invertLogic == true) {
    logInfo("Invert logic: turning ${lightSwitch} off on contact open")
    lightSwitch.off()
  } else {
    logInfo("Turning ${lightSwitch} on for contact open")
    lightSwitch.on()
  }
}

void contactClosedHandler(Event evt) {
  logDebug("Contact closed: ${evt.device}")
  if (settings.invertLogic == true) {
    logInfo("Invert logic: turning ${lightSwitch} on on contact close")
    lightSwitch.on()
  } else {
    logInfo("Turning ${lightSwitch} off for contact close")
    lightSwitch.off()
  }
}
