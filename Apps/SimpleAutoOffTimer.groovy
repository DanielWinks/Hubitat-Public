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
  name: 'Simple Auto Off Timer',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Automatically turn a light off after it has been on for a configurable delay.',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {
  page(
    name: 'mainPage', title: 'Simple Auto Off Timer'
  )
}

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Simple Auto Off Timer</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      input 'lightSwitch', 'capability.switch', title: 'Light/Switch to Monitor', required: true, multiple: false
    }

    section('<h2>Settings</h2>') {
      input 'delayOffSeconds', 'number', title: 'Delay Off (seconds)', required: true, defaultValue: 60
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
  unschedule()
  initialize()
}

void initialize() {
  subscribe(lightSwitch, 'switch.on', lightOnHandler)
}

void lightOnHandler(Event evt) {
  logDebug("${lightSwitch} turned on")
  unschedule('turnLightOff')
  runIn(settings.delayOffSeconds, 'turnLightOff')
  logInfo("Scheduling ${lightSwitch} to turn off in ${settings.delayOffSeconds} seconds")
}

void turnLightOff() {
  logInfo("Turning ${lightSwitch} off")
  lightSwitch.off()
}
