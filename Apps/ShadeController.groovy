#include dwinks.UtilitiesAndLoggingLibrary

definition(
  name: 'Shade Controller',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Control window shades based on time and/or sun position.',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {
  page(
    name: 'mainPage', title: 'Window Shade Controller'
  )
}

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Window Shade Controller</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      input 'windowCovers', 'capability.windowShade', title: "Window Shades to Open at 'Open Window Covers Mode' Start", required: false, multiple: true
      input 'windowCoversLowSun', 'capability.windowShade', title: "Window Shades to Close When 'Sun Is Low'", required: false, multiple: true
      input 'windowCoversSunset', 'capability.windowShade', title: 'Window Shades to Close At Sunset', required: false, multiple: true
      input 'disableSwitches', 'capability.switch', title: 'Disable Shade Control with switches', required: false, multiple: true
      input 'requiredPresence', 'capability.presenceSensor', title: 'Required presence for Shade Control', required: false, multiple: true
      input 'sunPosition', 'capability.sensor', title: 'Sun Position Sensor', required: true, multiple: false
    }

    section('<h2>Mode Settings</h2>') {
      input 'openShadesMode', 'mode', title: "<b>'Open Window Covers' Mode (Covers open with mode change)</b>", multiple: true, required: false
      input 'closeShadesMode', 'mode', title: "<b>'Close Window Covers' Mode (Covers close with mode change)</b>", multiple: true, required: false
      input 'lowSunPosition', 'number', title: "Altitude at which to close 'Sun Is Low' shades", range: '-20..75', required: true, defaultValue: 25
      input 'sunsetPosition', 'number', title: "Altitude at which to close 'Sunset' shades", range: '-20..75', required: true, defaultValue: 0
    }

    section('Logging') {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      input(name: 'initializeBtn', type: 'button', title: 'Initialize', backgroundColor: 'Crimson', textColor: 'white', submitOnChange: true)
    }

    section() {
      label title: 'Enter a name for this app instance', required: false
    }
  }
}

void initialize() {
  configure()
  unsubscribe()
}

void configure() {
  unschedule()
  // clearStates()
  subscribe(location, "mode.${settings.openShadesMode}", openShadesModeEvent)
  subscribe(location, "mode.${settings.closeShadesMode}", closeShadesModeEvent)
  subscribe(disableSwitches, 'switch', disableEvent)
  subscribe(snoozeSwitches, 'switch', snoozeSwitchEvent)
  subscribe(sunPosition, 'altitude', sunAltitudeEvent)
  subscribe(sunPosition, 'azimuth', sunAzimuthEvent)
}

void sunAltitudeEvent(Event event) {
  logDebug(event)
}

boolean sunriseDisabled() {
  boolean disabled = settings.disableSwitches?.any { sw -> sw.currentValue('switch') == 'on' }
  boolean snoozed = settings.snoozeSwitches?.any { sw -> sw.currentValue('switch') == 'on' }
  return  disabled || snoozed
}

boolean requiredPresencePresent() {
  return settings.requiredPresence?.any { p -> p.currentValue('presence') == 'present' }
}


void appButtonHandler(btn) {
  switch (btn) {
    case 'initializeBtn':
      initialize()
      break
  }
}

void test() {
  logDebug 'Testing...'
  refreshSunPosition()
}



void openShadesModeEvent(Event event) {
  logDebug "Received shades mode event: ${event.value}"
  openWindowCovers()
}

void openWindowCovers() {
  // Skip opening shades if disabled switches are on
  if (sunriseDisabled()) {
    logInfo 'Skipping opening shades because disabled switch is on'
    return
  }

  logDebug 'Opening all window coverings...'
  settings.windowCovers?.each { wc -> wc.open() }
  state.lowSunBlindsClosedToday = false
  state.sunsetBlindsClosedToday = false
}

void checkShadesToClose() {
  if (state.sunAltitudeDecreasing) {
    if (state.lowSunBlindsClosedToday == false && state.sunAltitude < (settings.lowSunPosition as int)) {
      logDebug "Closing 'Low Sun' Window Shades"
      settings.windowCoversLowSun?.each { wc -> wc.close() }
      state.lowSunBlindsClosedToday = true
    }
    if (state.sunsetBlindsClosedToday == false && state.sunAltitude < (settings.sunsetPosition as int)) {
      logDebug "Closing 'Sunset' Window Shades"
      settings.windowCoversSunset?.each { wc -> wc.close() }
      state.sunsetBlindsClosedToday = true
    }
  }
}
