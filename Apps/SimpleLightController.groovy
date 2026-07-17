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
  name: 'Simple Light Controller',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Turn a light on/off based on a contact sensor state.',
  category: '',
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
      input 'contactSensor', 'capability.contactSensor', title: 'Contact Sensor', required: true, multiple: false
      input 'lightSwitch', 'capability.switch', title: 'Light/Switch to Control', required: true, multiple: false
    }

    section('<h2>Settings</h2>') {
      input 'invertLogic', 'bool', title: 'Invert logic (contact open = light off, contact closed = light on)', required: false, defaultValue: false
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
  subscribe(contactSensor, 'contact.open', contactOpenHandler)
  subscribe(contactSensor, 'contact.closed', contactClosedHandler)
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
