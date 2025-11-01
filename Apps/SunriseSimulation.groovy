#include dwinks.UtilitiesAndLoggingLibrary
import com.hubitat.hub.domain.Event
import hubitat.helper.RMUtils

definition(
  name: 'Sunrise Simulation',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Slowly raise RGBW bulbs for gentle wakeup.',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: ''
)

preferences {page(name: 'mainPage', title: 'Sunrise Simulation')}
Map mainPage() {
  dynamicPage(
    name: 'mainPage',
    title: '<h1>Sunrise Simulation</h1>',
    install: true,
    uninstall: true,
    refreshInterval: 0
  ) {
    section('<h2>Devices</h2>') {
      input ('birdsongSwitch', 'capability.switch', title: 'Switch to start morning birdsong', required: false, multiple: false)
      input ('annoucementsSwitch', 'capability.switch', title: 'Switch to start morning announcements', required: false, multiple: false)
      input ('startSwitches', 'capability.switch', title: 'Start Sunrise with switches', required: false, multiple: true)

      // input "ctBulbs", "capability.colorTemperature", title: "Color Temp capable bulbs", required: false, multiple: true
      input ('rgbwBulbs', 'capability.colorControl', title: 'RGB capable bulbs', required: false, multiple: true)
      input ('disableSwitches', 'capability.switch', title: 'Disable Sunrise with switches', required: false, multiple: true)
      input ('snoozeSwitches', 'capability.switch', title: 'Snooze Sunrise with switches', required: false, multiple: true)
      input ('requiredPresence', 'capability.presenceSensor', title: 'Required presence for Sunrise', required: false, multiple: true)
    }

    section('<h2>Sunrise Settings</h2>') {
      input ('sunriseDuration', 'number', title: 'Duration of minutes to brighten lights', range: '10..60', required: true, defaultValue: 30)
      input ('snoozeDuration', 'number', title: 'Duration of minutes to snooze', range: '10..60', required: true, defaultValue: 30)
    }

    section("Webhooks") {
      paragraph("<ul><li><strong>Local</strong>: <a href='${getLocalUri()}' target='_blank' rel='noopener noreferrer'>${getLocalUri()}</a></li></ul>")
      paragraph("<ul><li><strong>Cloud</strong>: <a href='${getCloudUri()}' target='_blank' rel='noopener noreferrer'>${getCloudUri()}</a></li></ul>")
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

void configure() {
  unsubscribe()
  initialize()
}

void initialize() {
  unschedule()
  clearAllStates()
  calculateStageDurations()
  subscribe(location, "mode.${settings.sunriseStartMode}", sunriseStartModeEvent)
  subscribe(disableSwitches, 'switch', 'disableEvent')
  subscribe(startSwitches, 'switch', 'sunriseStartSwitchEvent')
  subscribe(snoozeSwitches, 'switch', 'snoozeSwitchEvent')
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
  RMUtils.sendAction([birdsongRule], 'runRuleAct', app.label, '5.0')
}

Map sunriseStartWebhook() {
  sunriseStart()
  return render(contentType: "text/html", data: "<p>Arrived!</p>", status: 200)
}

void sunriseStartSwitchEvent(Event event) {
  logDebug "Received event: ${event.value}"
  if ("${event.value}" == 'on') {sunriseStart()}
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
  clearAllStates()
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

void scheduleBirdsongStart() {
  logDebug "Scheduling birdsong to start in ${state.birdsongStart} seconds..."
  runIn(state.birdsongStart as int, 'birdsongStart')
}

void birdsongStart() { RMUtils.sendAction([birdsongRule], 'runRuleAct', app.label, '5.0') }
void annoucementsStart() { RMUtils.sendAction([annoucementsRule], 'runRuleAct', app.label, '5.0') }
