#include dwinks.UtilitiesAndLogging
#include dwinks.SunPosition
import com.hubitat.hub.domain.Event

definition(
  name: 'Sunrise Simulation',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Slowly rise CT or RGBW bulbs for gentle wakeup.',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {
  page(
    name: 'mainPage', title: 'Sunrise Simulation'
  )
}

Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Sunrise Simulation</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      // input "ctBulbs", "capability.colorTemperature", title: "Color Temp capable bulbs", required: false, multiple: true
      input 'rgbwBulbs', 'capability.colorControl', title: 'RGB capable bulbs', required: false, multiple: true
      input 'windowCovers', 'capability.windowShade', title: "Window Shades to Open at 'Open Window Covers Mode' Start", required: false, multiple: true
      input 'windowCoversLowSun', 'capability.windowShade', title: "Window Shades to Close When 'Sun Is Low'", required: false, multiple: true
      input 'windowCoversSunset', 'capability.windowShade', title: 'Window Shades to Close At Sunset', required: false, multiple: true
      input 'disableSwitches', 'capability.switch', title: 'Disable Sunrise with switches', required: false, multiple: true
      input 'snoozeSwitches', 'capability.switch', title: 'Snooze Sunrise with switches', required: false, multiple: true
      input 'requiredPresence', 'capability.presenceSensor', title: 'Required presence for Sunrise', required: false, multiple: true
      input 'birdsongSwitch', 'capability.switch', title: 'Switch to turn on to start morning birdsong', required: false, multiple: false
      input 'annoucementSwitch', 'capability.switch', title: 'Switch to turn on to start morning annoucements', required: false, multiple: false
    }

    section('<h2>Mode Settings</h2>') {
      input 'sunriseStartMode', 'mode', title: "<b>'Begin Sunrise' Mode (Sunrise Starts with mode change)</b>", multiple: false, required: true
      input 'openShadesMode', 'mode', title: "<b>'Open Window Covers' Mode (Covers open with mode change)</b>", multiple: false, required: true
      input 'sunriseDuration', 'number', title: 'Duration of minutes to brighten lights', range: '10..60', required: true, defaultValue: 30
      input 'snoozeDuration', 'number', title: 'Duration of minutes to snooze', range: '10..60', required: true, defaultValue: 30
      input 'updateInterval', 'enum', title: 'Sun Position Update Interval', required: true, defaultValue: '1 Minute', options: ['1 Minute', '5 Minutes', '10 Minutes', '15 Minutes', '30 Minutes']
      input 'lowSunPosition', 'number', title: "Altitude at which to close 'Sun Is Low' shades", range: '-20..75', required: true, defaultValue: 40
      input 'sunsetPosition', 'number', title: "Altitude at which to close 'Sunset' shades", range: '-20..75', required: true, defaultValue: 6
    }

    section('Logging') {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      input(name: 'initializeBtn', type: 'button', title: 'Initialize', backgroundColor: 'Crimson', textColor: 'white', submitOnChange: true)
      input(name: 'testBtn', type: 'button', title: 'Test', backgroundColor: 'Crimson', textColor: 'white', submitOnChange: true)
    }

    section() {
      label title: 'Enter a name for this app instance', required: false
    }
  }
}

void updated() {
  unsubscribe()
  initialize()
}

void initialize() {
  unschedule()
  clearStates()
  calculateStageDurations()
  sunPositionInitialization()
  refreshSunPosition()
  subscribe(location, "mode.${settings.sunriseStartMode}", sunriseStartModeEvent)
  subscribe(location, "mode.${settings.openShadesMode}", openShadesModeEvent)
  subscribe(disableSwitches, 'switch', disableEvent)
  subscribe(snoozeSwitches, 'switch', snoozeSwitchEvent)
}

boolean sunriseDisabled() {
  boolean disabled = settings.disableSwitches?.any { sw -> sw.currentValue('switch') == 'on' }
  boolean snoozed = settings.snoozeSwitches?.any { sw -> sw.currentValue('switch') == 'on' }
  return  disabled || snoozed
}

boolean requiredPresencePresent() {
  return settings.requiredPresence?.any { p -> p.currentValue('presence') == 'present' }
}

void calculateStageDurations() {
  int totalSecs = 60 * settings.sunriseDuration as int
  int stageSecs = totalSecs / 3
  state.stageSecs = stageSecs
  state.stage1interval = Math.round(stageSecs / 10) > 1 ? Math.round(stageSecs / 10) : 1
  state.stage2interval = Math.round(stageSecs / 40) > 1 ? Math.round(stageSecs / 40) : 1
  state.stage3interval = Math.round(stageSecs / 80) > 1 ? Math.round(stageSecs / 80) : 1

  state.birdsongStart = totalSecs / 3
}

void appButtonHandler(btn) {
  switch (btn) {
    case 'initializeBtn':
      initialize()
      break
    case 'testBtn':
      test()
      break
  }
}

void test() {
  logDebug 'Testing...'
  refreshSunPosition()
}

void sunriseStartModeEvent(Event event) {
  logDebug "Received mode event: ${event.value}"
  sunriseStart()
}

void snoozeSwitchEvent(Event event) {
  logDebug "Received snooze switch event: ${event.value}"

  if ("${event.value}" == 'on') {
    logDebug 'Snooze switch turned on'
    snoozeOnHandler()
    runIn(60 * (settings.snoozeDuration as int), 'snoozeOffHandler')
  }
  if ("${event.value}" == 'off') {
    sunriseStart()
  }
}

void openShadesModeEvent(Event event) {
  logDebug "Received shades mode event: ${event.value}"
  openWindowCovers()
}

void snoozeOnHandler() {
  logInfo 'Snoozing... turning off all bulbs...'
  abortSunrise()
}

void snoozeOffHandler() {
  settings.snoozeSwitches.each { ss -> ss.off() }
}

void disableEvent(Event event) {
  logDebug "Received disable event: ${event.value}"
  abortSunrise()
}

void abortSunrise() {
  unschedule()
  clearStates()
  turnOffAllBulbs()
}

void turnOffAllBulbs() {
  settings.ctBulbs?.each { bulb -> bulb.off() }
  settings.rgbwBulbs?.each { bulb -> bulb.off() }
}

void sunriseStart() {
  logDebug 'Starting sunrise routine...'
  if (!requiredPresencePresent()) {
    logInfo 'Skipping sunrise because none of the required presence sensors were present.'
    return
  }
  calculateStageDurations()
  brightenCTBulbs()
  brightenRGBWBulbsStart()
  scheduleBirdsongStart()
}

void brightenCTBulbs() {
  logDebug 'Brightening CT Bulbs...'
//ToDo implement CT bulb routine...
}

void brightenRGBWBulbsStart() {
  state.rgbwHue = 0
  state.rgbwSaturation = 100
  state.rgbwLevel = 1
  state.rgbwCT = 2800
  state.rgbwStage = 1

  state.rgbwColorMap = [hue:state.rgbwHue, saturation:state.rgbwSaturation, level:state.rgbwLevel]
  logDebug "Starting rgbwColorMap: ${state.rgbwColorMap}"
  logDebug "Starting rgbwCT: ${state.rgbwCT}"
  logDebug "Starting rgbwLevel: ${state.rgbwLevel}"

  if (!state.brightenRGBWBulbsRunning) { brightenRGBWBulbs() }
}

void brightenRGBWBulbs() {
  state.brightenRGBWBulbsRunning = 'true'
  if (sunriseDisabled()) {
    logInfo 'Exiting brightenRGBWBulbs() because disabled switch is on'
    return
  }

  if (state.rgbwStage == 1) {
    settings.rgbwBulbs?.each { bulb -> bulb.setColor(state.rgbwColorMap) }

    state.rgbwHue += 1
    state.rgbwLevel += 1

    state.rgbwColorMap = [
      hue: Math.round(state.rgbwHue),
      saturation: Math.round(state.rgbwSaturation),
      level: Math.round(state.rgbwLevel)
      ]
    logDebug "Stage 1 ColorMap: ${state.rgbwColorMap}"

    if (state.rgbwColorMap.hue < 10) {
      runIn(state.stage1interval, 'brightenRGBWBulbs')
    } else {
      state.rgbwColorMap = [hue:10, saturation:100, level:10]
      state.rgbwStage = 2
      runIn(state.stage1interval, 'brightenRGBWBulbs')
    }
  }

  if (state.rgbwStage == 2) {
    settings.rgbwBulbs?.each { bulb -> bulb.setColor(state.rgbwColorMap) }

    state.rgbwSaturation -= 1
    state.rgbwLevel += 0.25

    state.rgbwColorMap = [
      hue: Math.round(state.rgbwHue),
      saturation: Math.round(state.rgbwSaturation),
      level: Math.round(state.rgbwLevel)
      ]
    logDebug "Stage 2 ColorMap: ${state.rgbwColorMap}"

    if (state.rgbwColorMap.saturation > 60) {
      runIn(state.stage2interval, 'brightenRGBWBulbs')
    } else {
      state.rgbwColorMap = [hue:10, saturation:60, level:20]
      state.rgbwLevel = 20
      state.rgbwStage = 3
      runIn(state.stage2interval, 'brightenRGBWBulbs')
    }
  }

  if (state.rgbwStage == 3) {
    settings.rgbwBulbs?.each { bulb -> bulb.setColorTemperature(state.rgbwCT as int, state.rgbwLevel as int, 0) }
    state.rgbwCT += 40
    state.rgbwLevel += 1

    logDebug "Stage 3 CT Level: ${state.rgbwCT} ${state.rgbwLevel}"
    if (state.rgbwLevel > 100) {
      logDebug 'Finished sunrise sequence'
      state.remove('brightenRGBWBulbsRunning')
      annoucementsStart()
    } else {
      runIn(state.stage3interval, 'brightenRGBWBulbs')
    }
  }
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

void scheduleBirdsongStart() {
  logDebug "Scheduling birdsong to start in ${state.birdsongStart} seconds..."
  runIn(state.birdsongStart as int, 'birdsongStart')
}

void birdsongStart() { settings.birdsongSwitch?.on() }
void annoucementsStart() { settings.annoucementSwitch?.on() }

////////// Sun Calculations //////////
void sunPositionInitialization() {
  String updateIntervalCmd = (settings?.updateInterval ?: '1 Minutes').replace(' ', '')
  "runEvery${updateIntervalCmd}"(refreshSunPosition)
}

void refreshSunPosition() {
  Map<String, BigDecimal> coords = getPosition(location.latitude, location.longitude)
  state.lastSunAltitude = state.sunAltitude ?: coords.altitude
  state.lastSunAzimuth = state.sunAzimuth ?: coords.azimuth
  state.sunAltitude = coords.altitude
  state.sunAzimuth = coords.azimuth
  state.sunAltitudeIncreasing = state.sunAltitude > state.lastSunAltitude
  state.sunAltitudeDecreasing = state.sunAltitude < state.lastSunAltitude
  state.sunPosistionLastUpdated = new Date().format('yyyy-MM-dd h:mm', location.timeZone)
  checkShadesToClose()
}
