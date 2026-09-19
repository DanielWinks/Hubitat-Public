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

import groovy.transform.Field


@Field static final Map deviceModelNames =
  ["7000:A002":"ZEN72", "7000:A007":"ZEN77"]
@Field static final Map LOG_LEVELS = [0:"Error", 1:"Warn", 2:"Info", 3:"Debug", 4:"Trace"]
@Field static final Map LOG_TIMES = [0:"Indefinitely", 30:"30 Minutes", 60:"1 Hour", 120:"2 Hours", 180:"3 Hours", 360:"6 Hours", 720:"12 Hours", 1440:"24 Hours"]

metadata {
  definition (
    name: "Zooz ZEN Dimmer Advanced",
    namespace: "dwinks",
    author: "Daniel Winks",
    importUrl: "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/ZWave/ZoozZen72.groovy"
  ) {
    capability "Actuator"
    capability "Switch"
    capability "SwitchLevel"
    capability "ChangeLevel"
    capability "Configuration"
    capability "Refresh"
    capability "PushableButton"
    capability "HoldableButton"
    capability "ReleasableButton"
    capability "DoubleTapableButton"
    capability "Flash"

    command "startLevelChange", [
      [name:"Direction*", description:"Direction for level change request", type: "ENUM", constraints: ["up","down"]],
      [name:"Duration", type:"NUMBER", description:"Transition duration in seconds"] ]

    command "setLED", [
      [name:"Select Color*", description:"Select the color for the LED", type: "ENUM", constraints: ledColorOptions] ]
    command "setLEDMode", [
      [name:"Select Mode*", description:"This Sets Preference (#2)*", type: "ENUM", constraints: ledModeCmdOptions] ]
    command "setParameter",[[name:"parameterNumber*",type:"NUMBER", description:"Parameter Number"],
      [name:"value*",type:"NUMBER", description:"Parameter Value"],
      [name:"size",type:"NUMBER", description:"Parameter Size"]]


    attribute "syncStatus", "string"

    fingerprint mfr:"027A", prod:"7000", deviceId:"A002", inClusters:"0x5E,0x26,0x70,0x5B,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x87,0x73,0x9F,0x6C,0x7A"
    fingerprint mfr:"027A", prod:"7000", deviceId:"A007", inClusters:"0x5E,0x26,0x70,0x5B,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x87,0x73,0x9F,0x6C,0x7A"
  }

  preferences {
    configParams.each { Map param ->
      if (!param.hidden) {
        Integer paramVal = getParamValue(param)
        if (param.options) {
          input "configParam${param.num}", "enum",
            title: fmtTitle("${param.title}"),
            description: fmtDesc("• Parameter #${param.num}, Selected: ${paramVal}" + (param?.description ? "<br>• ${param?.description}" : '')),
            defaultValue: paramVal,
            options: param.options,
            required: false
        }
        else if (param.range) {
          input "configParam${param.num}", "number",
            title: fmtTitle("${param.title}"),
            description: fmtDesc("• Parameter #${param.num}, Range: ${(param.range).toString()}, DEFAULT: ${param.defaultVal}" + (param?.description ? "<br>• ${param?.description}" : '')),
            defaultValue: paramVal,
            range: param.range,
            required: false
        }
      }
    }

    for(int i in 2..maxAssocGroups) {
      input "assocDNI$i", "string",
        title: fmtTitle("Device Associations - Group $i"),
        description: fmtDesc("Supports up to ${maxAssocNodes} Hex Device IDs separated by commas. Check device documentation for more info. Save as blank or 0 to clear."),
        required: false
    }

    input "supervisionGetEncap", "bool",
      title: fmtTitle("Supervision Encapsulation") + "<em> (Experimental)</em>",
      description: fmtDesc("This can increase reliability when the device is paired with security, but may not work correctly on all models."),
      defaultValue: false

    if (hardwareLevelCorrection()) {
      input "levelCorrection", "hidden", 
        title: fmtTitle("Brightness Correction"),
        description: fmtDesc("This feature is implimented within the hardware and cannot be changed"),
        defaultValue: false
    }
    else {
      input "levelCorrection", "bool",
        title: fmtTitle("Brightness Correction"),
        description: fmtDesc("Brightness level set on dimmer is converted to fall within the min/max range but shown with the full range of 1-100%"),
        defaultValue: false
    }

    input "sceneReverse", "bool",
      title: fmtTitle("Scene Up-Down Reversal"),
      description: fmtDesc("If the button numbers and up/down descriptions are backwards in the scene button events change this setting to fix it!"),
      defaultValue: false

    input name: "logLevel", type: "enum", title: fmtTitle("Logging Level"),
      description: fmtDesc("Logs selected level and above"), defaultValue: 3, options: LOG_LEVELS
    input name: "logLevelTime", type: "enum", title: fmtTitle("Logging Level Time"),
      description: fmtDesc("Time to enable Debug/Trace logging"), defaultValue: 30, options: LOG_TIMES
  }
}


void checkLogLevel(Map levelInfo = [level:null, time:null]) {
  unschedule("logsOff")
  if (settings.logLevel == null) {
    device.updateSetting("logLevel", [value:"3", type:"enum"])
  }
  if (settings.logLevelTime == null) {
    device.updateSetting("logLevelTime", [value:"30", type:"enum"])
  }
  if (levelInfo.level == null) {
    levelInfo = getLogLevelInfo()
  }

  String logMsg = "Logging Level is: ${LOG_LEVELS[levelInfo.level]} (${levelInfo.level})"
  if (levelInfo.level >= 3 && levelInfo.time > 0) {
    logMsg += " for ${LOG_TIMES[levelInfo.time]}"
    runIn(60 * levelInfo.time, "logsOff")
  }
  logInfo(logMsg)
}

void setLogLevel(String levelName, String timeName = null) {
  Integer level = LOG_LEVELS.find { entry -> levelName?.equalsIgnoreCase(entry.value) }?.key ?: 2
  Integer time = LOG_TIMES.find { entry -> timeName?.equalsIgnoreCase(entry.value) }?.key ?: 0
  device.updateSetting("logLevel", [value:"${level}", type:"enum"])
  checkLogLevel(level: level, time: time)
}

Map getLogLevelInfo() {
  Integer level = settings.logLevel as Integer ?: 2
  Integer time = settings.logLevelTime as Integer ?: 0
  return [level: level, time: time]
}

void debugLogsOff() {
  logWarn("Debug logging toggle disabled...")
  device.removeSetting("logEnable")
  device.updateSetting("debugEnable", [value:false, type:"bool"])
}

void logsOff() {
  logWarn("Debug and Trace logging disabled...")
  if (getLogLevelInfo().level >= 3) {
    device.updateSetting("logLevel", [value:"2", type:"enum"])
  }
}

void logErr(String msg) {
  log.error("${device.displayName}: ${msg}")
}

void logWarn(String msg) {
  if (getLogLevelInfo().level >= 1) {
    log.warn("${device.displayName}: ${msg}")
  }
}

void logInfo(String msg) {
  if (getLogLevelInfo().level >= 2) {
    log.info("${device.displayName}: ${msg}")
  }
}

void logDebug(String msg) {
  if (getLogLevelInfo().level >= 3) {
    log.debug("${device.displayName}: ${msg}")
  }
}

void logTrace(String msg) {
  if (getLogLevelInfo().level >= 4) {
    log.trace("${device.displayName}: ${msg}")
  }
}


void debugShowVars() {
  logDebug("settings ${settings.hashCode()} ${settings}")
  logDebug("paramsList ${paramsList.hashCode()} ${paramsList}")
  logDebug("paramsMap ${paramsMap.hashCode()} ${paramsMap}")
}


@Field static final int maxAssocGroups = 4
@Field static final int maxAssocNodes = 5

@Field static final Map ledModeCmdOptions = [0:"Default", 1:"Reverse", 2:"Off", 3:"On"]
@Field static final Map ledColorOptions = [0:"White", 1:"Blue", 2:"Green", 3:"Red"]
@Field static Map<String, Long> turningOn = new java.util.concurrent.ConcurrentHashMap()

@Field static Map<String, Map> paramsMap =
[
  paddleControl: [ num: 1,
    title: "Paddle Orientation",
    size: 1, defaultVal: 0,
    options: [0:"Normal", 1:"Reverse", 2:"Toggle Mode"],
  ],
  ledMode: [ num: 2,
    title: "LED Indicator",
    size: 1, defaultVal: 0,
    options: [0:"LED On When Switch Off", 1:"LED On When Switch On", 2:"LED Always Off", 3:"LED Always On"],
  ],
  ledColor: [ num: 23,
    title: "LED Indicator Color",
    size: 1, defaultVal: 1,
    options: [:],
  ],
  ledBrightness: [ num: 24,
    title: "LED Indicator Brightness",
    size: 1, defaultVal: 1,
    options: [0:"Bright (100%)", 1:"Medium (60%)", 2:"Low (30%)"],
  ],
  autoOffInterval: [ num: 4,
    title: "Auto Turn-Off Timer",
    size: 4, defaultVal: 0,
    options: [:],
    changes: ['7X':[num:3]]
  ],
  autoOnInterval: [ num: 6,
    title: "Auto Turn-On Timer",
    size: 4, defaultVal: 0,
    options: [:],
    changes: ['7X':[num:5]]
  ],
  powerFailure: [ num: 8,
    title: "Behavior After Power Failure",
    size: 1, defaultVal: 2,
    options: [2:"Restores Last Status", 0:"Forced to Off", 1:"Forced to On"],
  ],
  rampRate: [ num: 9,
    title: "Ramp Rate to Full ON",
    description: "If no separate OFF setting is shown this controls both",
    size: 1, defaultVal: 1,
    options: [0:"Instant On/Off"],
  ],
  rampRateOff: [ num: 27,
    title: "Ramp Rate to Full OFF",
    size: 1, defaultVal: 2,
    options: [0:"Instant Off"],
    firmVer: 2.0, firmVerM:[10:10],
    changes: [72:[], 77:[firmVer:2.10, firmVerM:[10:20]]
    ],
  ],
  holdRampRate: [ num: 16,
    title: "Dimming Speed when Paddle is Held",
    size: 1, defaultVal: 5,
    options: [:],
  ],
  zwaveRampRate: [ num: 17,
    title: "Z-Wave Ramp Rate (Dimming Speed)",
    size: 1, defaultVal: 1,
    options: [ 1:"Z-Wave Can Set Ramp Rate", 0:"Match Physical Ramp Rate"],
    changes: ['7X':[num:null]]
  ],
  minimumBrightness: [ num: 10,
    title: "Minimum Brightness",
    size: 1, defaultVal: 1,
    options: [:],
  ],
  maximumBrightness: [ num: 11,
    title: "Maximum Brightness",
    size: 1, defaultVal: 99,
    options: [:],
  ],
  doubleTapBrightness: [ num: 12,
    title: "Double Tap Up Brightness",
    size: 1, defaultVal: 0,
    options: [0:"Full Brightness (100%)", 1:"Maximum Brightness Parameter"],
    changes: ['7X':[options:[0:"Full Brightness (100%)", 1:"Custom Brightness Parameter", 2:"Maximum Brightness Parameter", 3:"Disabled"]]],
  ],
  doubleTapFunction: [ num: 14,
    title: "Double Tap Up Function",
    size: 1, defaultVal: 0,
    options: [0:"Full/Maximum Brightness", 1:"Disabled, Single Tap Last Brightness (or Custom)", 2:"Disabled, Single Tap Full/Maximum Brightness"],
    changes: ['7X':[num:null]]
  ],
  singleTapUp: [ num: null,
    title: "Single Tap Up Brightness",
    size: 1, defaultVal: 0,
    options: [0:"Last Brightness Level", 1:"Custom Brightness Parameter", 2:"Maximum Brightness Parameter", 3:"Full Brightness (100%)"],
    changes: ['7X':[num:25]]
  ],
  customBrightness: [ num: 18,
    title: "Custom Brightness when Turned On",
    size: 1, defaultVal: 0,
    options: [0:"Last Brightness Level"],
  ],
  nightLight: [ num: 22,
    title: "Night Light Mode Brightness",
    size: 1, defaultVal: 20,
    options: [0:"Disabled"],
  ],
  sceneControl: [ num: 13,
    title: "Scene Control Events",
    description: "Enable to get push and multi-tap events",
    size: 1, defaultVal: 0,
    options: [0:"Disabled", 1:"Enabled"],
  ],
  sceneControl3w: [ num: null,
    title: "Scene Control Events From 3-Way",
    description: "Enable to get push and multi-tap events from a mechanical switch connected in a direct 3-way",
    size: 1, defaultVal: 0,
    options: [0:"Disabled", 1:"Enabled"],
    changes: [72:[num:31, firmVer:2.20, firmVerM:[10:40]]]
  ],
  loadControl: [ num: 15,
    title: "Smart Bulb Mode - Load Control",
    size: 1, defaultVal: 1,
    options: [1:"Enable Paddle and Z-Wave", 0:"Disable Paddle Control", 2:"Disable Paddle and Z-Wave Control"],
  ],
  smartBulbBehavior: [ num: 21,
    title: "Smart Bulb - On/Off when Paddle Disabled",
    size: 1, defaultVal: 0,
    options: [0:"Reports Status & Changes LED", 1:"Doesn't Report Status or Change LED"],
  ],
  smartBulbDimming: [ num: 20,
    title: "Smart Bulb - Dimming when Paddle Disabled",
    size: 1, defaultVal: 0,
    options: [0:"Report Each Brightness Level", 1:"Report Only Final Brightness Level"],
  ],
  threeWaySwitchType: [num: null,
    title: "3-Way Switch Type",
    size: 1, defaultVal: 0,
    options: [0:"Toggle On/Off Switch", 1:"Toggle On/Off with 2x/3x Shortcuts", 2:"Momentary Switch", 3:"Momentary with 2x/Hold Shortcuts"],
    changes: [72:[num: 19]]
  ],
  paddleProgramming: [ num: null,
    title: "Programming from the Paddle",
    size: 1, defaultVal: 0,
    options: [0:"Enabled", 1:"Disabled"],
    changes: [72:[num: 26, firmVer:2.0], 77:[num: 26, firmVer:2.0]
    ],
  ],
  zwaveRampRateOn: [ num: 28,
    title: "Z-Wave Ramp Rate to Full ON",
    size: 1, defaultVal: 255,
    options: [255:"Match Physical",0:"Instant On"],
    firmVer: 2.0, firmVerM:[10:10],
    changes: [72:[], 77:[firmVer:2.10, firmVerM:[10:20]]
    ],
  ],
  zwaveRampRateOff: [ num: 29,
    title: "Z-Wave Ramp Rate to Full OFF",
    size: 1, defaultVal: 255,
    options: [255:"Match Physical",0:"Instant Off"],
    firmVer: 2.0, firmVerM:[10:10],
    changes: [72:[], 77:[firmVer:2.10, firmVerM:[10:20]]
    ],
  ],
  remoteZWaveDim: [ num: 30,
    title: "Remote Z-Wave Dimming Duration",
    description: "Dimming Speed for devices directly associated in Groups 3/4",
    size: 1, defaultVal: 5,
    options: [:],
    firmVer: 2.0, firmVerM:[10:10],
    changes: [72:[], 77:[firmVer:2.10, firmVerM:[10:20]]
    ],
  ],
  ledFlash: [ num: null,
    title: "LED Flash when Settings Changed",
    size: 1, defaultVal: 0,
    options: [0:"Flash Enabled", 1:"Flash Disabled"],
    changes: [
      72:[num:32, firmVer:2.20, firmVerM:[10:40]],
      77:[num:32, firmVer:3.20, firmVerM:[10:40, 2:30]]
    ]
  ],
  associationReports: [ num: 7,
    title: "Send Status Report to Associations",
    size: 1, defaultVal: 15,
    options: [ 0:"None", 1:"Physical Tap On ZEN Only", 2:"Physical Tap On Connected 3-Way Switch Only", 3:"Physical Tap On ZEN / 3-Way Switch",
      4:"Z-Wave Command From Hub", 5:"Physical Tap On ZEN / Z-Wave Command", 6:"Physical Tap On 3-Way Switch / Z-Wave Command",
      7:"Physical Tap On ZEN / 3-Way Switch / Z-Wave Command", 8:"Timer Only", 9:"Physical Tap On ZEN / Timer",
      10:"Physical Tap On 3-Way Switch / Timer", 11:"Physical Tap On ZEN / 3-Way Switch / Timer", 12:"Z-Wave Command From Hub / Timer",
      13:"Physical Tap On ZEN / Z-Wave Command / Timer", 14:"Physical Tap On ZEN / 3-Way Switch / Z-Wave Command / Timer",
      15:"All Of The Above"
    ],
    changes: [72:[firmVer:2.10, firmVerM:[10:30]]]
  ],
]

@Field static final Map commandClassVersions = [
  0x26: 2,
  0x5B: 3,
  0x6C: 1,
  0x70: 1,
  0x85: 2,
  0x86: 2,
  0x8E: 3,
]


void installed() {
  logInfo("Installed Zooz ZEN Dimmer Advanced")
  initialize()
}

void initialize() {
  logWarn("initialize...")
  refresh()
}

void configure() {
  logWarn("configure...")
  if (!pendingChanges || state.resyncAll == null) {
    logDebug("Enabling Full Re-Sync")
    clearVariables()
    state.resyncAll = true
  }

  updateSyncingStatus(6)
  runIn(1, "executeRefreshCmds")
  runIn(4, "executeConfigureCmds")
}

void updated() {
  logDebug("updated...")
  runIn(1, "executeConfigureCmds")
}

void refresh() {
  logDebug("refresh...")
  executeRefreshCmds()
}


String on() {
  logDebug("on...")
  flashStop()
  if (turningOn[device.id] > (new Date()).time) {
    logWarn("on() blocked, already adjusting")
    return
  }
  return getOnOffCmds(0xFF)
}

String off() {
  logDebug("off...")
  flashStop()
  return getOnOffCmds(0x00)
}

String setLevel(Number level, Number duration=null) {
  logDebug("setLevel($level, $duration)...")
  turningOn[device.id] = (new Date()).time + 1000
  return getSetLevelCmds(level, duration)
}

List<String> startLevelChange(String direction, Number duration = null) {
  Boolean upDown = (direction == "down") ? true : false
  Integer durationVal = validateRange(duration, getParamValue("holdRampRate") as Integer, 0, 127)
  logDebug("startLevelChange($direction) for ${durationVal}s")
  List<String> cmds = [switchMultilevelStartLvChCmd(upDown, durationVal)]


  return delayBetween(cmds, 1000)
}

String stopLevelChange() {
  logDebug("stopLevelChange()")
  return switchMultilevelStopLvChCmd()
}

void push(Integer buttonId) { sendBasicButtonEvent(buttonId, "pushed") }
void hold(Integer buttonId) { sendBasicButtonEvent(buttonId, "held") }
void release(Integer buttonId) { sendBasicButtonEvent(buttonId, "released") }
void doubleTap(Integer buttonId) { sendBasicButtonEvent(buttonId, "doubleTapped") }

void flash(Integer rateToFlash = null) {
  if (!rateToFlash) rateToFlash = state.flashRate
  rateToFlash = validateRange(rateToFlash, 1500, 750, 30000)
  Integer maxRun = 30 * 60
  state.flashNext = (device.currentValue("switch")=="on" ? "off" : "on")
  state.flashRate = rateToFlash

  logInfo("Flashing started with rate of ${rateToFlash}ms")
  runIn(maxRun, "flashStop", [data:true])
  flashHandler(rateToFlash)
}

void flashStop(Boolean turnOn = false) {
  if (state.flashNext != null) {
    logInfo("Flashing stopped...")
    unschedule("flashHandler")
    unschedule("flashStop")
    state.remove("flashNext")
    if (turnOn) { runIn(1, "on") }
  }
}

void flashHandler(Integer rateToFlash) {
  if (state.flashNext == "on") {
    logDebug("Flash On")
    state.flashNext = "off"
    runInMillis(rateToFlash, "flashHandler", [data:rateToFlash])
    sendCommands(getSetLevelCmds(0xFF, 0))
  }
  else if (state.flashNext == "off") {
    logDebug("Flash Off")
    state.flashNext = "on"
    runInMillis(rateToFlash, "flashHandler", [data:rateToFlash])
    sendCommands(getSetLevelCmds(0x00, 0))
  }
}


void setLED(String colorName) {
  Map param = getParam("ledColor")

  if (param?.num && state.deviceModel in ["ZEN72", "ZEN77"]) {
    Short paramVal = ledColorOptions.find{ colorName.equalsIgnoreCase(it.value) }.key
    logDebug("Indicator Color Value [${colorName} : ${paramVal}]")
    device.updateSetting("configParam${param.num}",[value:"${paramVal}", type:"enum"])
    sendCommands(configSetGetCmd(param, paramVal))
  }
  else {
    logWarn("Indicator Color can only be changed on ZEN72/77 models")
  }
}

void setLEDMode(String modeName) {
  Map param = getParam("ledMode")

  if (param?.num) {
    Short paramVal = ledModeCmdOptions.find{ modeName.equalsIgnoreCase(it.value) }.key
    logDebug("Indicator Value [${modeName} : ${paramVal}]")
    device.updateSetting("configParam${param.num}",[value:"${paramVal}", type:"enum"])
    sendCommands(configSetGetCmd(param, paramVal))
  }
  else {
    logWarn("There is No LED Indicator Parameter Found for this model")
  }
}

void refreshParams() {
  List<String> cmds = []
  for (int i = 1; i <= maxAssocGroups; i++) {
    cmds << associationGetCmd(i)
  }

  configParams.each { Map param ->
    cmds << configGetCmd(param)
  }

  if (cmds) sendCommands(cmds)
}

String setParameter(Integer paramNum, Integer value, Integer size = null) {
  Map param = getParam(paramNum)
  if (param && !size) { size = param.size  }

  if (paramNum == null || value == null || size == null) {
    logWarn("Incomplete parameter list supplied...")
    logWarn("Syntax: setParameter(paramNum, value, size)")
    return
  }
  logDebug("setParameter ( number: $paramNum, value: $value, size: $size )" + (param ? " [${param.name}]" : ""))
  return secureCmd(configSetCmd([num: paramNum, size: size], value as Integer))
}


void parse(String description) {
  zwaveParse(description)
  sendEvent(name:"numberOfButtons", value:10)
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  zwaveMultiChannel(cmd)
}
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Integer ep = 0) {
  zwaveSupervision(cmd,ep)
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd, Integer ep = 0) {
  logDebug("Supervision Report - SessionID: ${cmd.sessionID}, Status: ${cmd.status}")
  if (supervisedPackets["${device.id}"] == null) { supervisedPackets["${device.id}"] = [:] }

  switch (cmd.status as Integer) {
    case 0x00:
    case 0x01:
    case 0x02:
      logWarn("Supervision NOT Successful - SessionID: ${cmd.sessionID}, Status: ${cmd.status}")
      break
    case 0xFF:
      supervisedPackets["${device.id}"].remove(cmd.sessionID)
      break
  }
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  logTrace("${cmd}")
  updateSyncingStatus()

  Map param = getParam(cmd.parameterNumber)
  Integer val = cmd.scaledConfigurationValue

  if (param) {
    Long sizeFactor = Math.pow(256,param.size).round()
    if (val < 0) { val += sizeFactor }

    logDebug("${param.name} - ${param.title} (#${param.num}) = ${val.toString()}")
    setParamStoredValue(param.num, val)
  }
  else {
    logDebug("Parameter #${cmd.parameterNumber} = ${val.toString()}")
  }
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logTrace("${cmd}")
  updateSyncingStatus()

  Integer grp = cmd.groupingIdentifier

  if (grp == 1) {
    logDebug("Lifeline Association: ${cmd.nodeId}")
    state.group1Assoc = (cmd.nodeId == [zwaveHubNodeId]) ? true : false
  }
  else if (grp > 1 && grp <= maxAssocGroups) {
    logDebug("Group $grp Association: ${cmd.nodeId}")
    if (cmd.nodeId.size() > 0) {
      state["assocNodes$grp"] = cmd.nodeId
    } else {
      state.remove("assocNodes$grp".toString())
    }

    String dnis = convertIntListToHexList(cmd.nodeId)?.join(", ")
    device.updateSetting("assocDNI$grp", [value:"${dnis}", type:"string"])
  }
  else {
    logDebug("Unhandled Group: $cmd")
  }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  flashStop()
  sendSwitchEvents(cmd.value, "physical", ep)
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  sendSwitchEvents(cmd.value, "digital", ep)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  sendSwitchEvents(cmd.value, "digital", ep)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  if (settings.sceneReverse) {
    if (cmd.sceneNumber == 1) cmd.sceneNumber = 2
    else if (cmd.sceneNumber == 2) cmd.sceneNumber = 1
    logTrace("Scene Reversed: ${cmd}")
  }

  Map sceneEvt = [name: "", value: cmd.sceneNumber, desc: "", type:"physical", isStateChange:true]
  String actionType
  String btnVal

  switch (cmd.sceneNumber) {
    case 1:
      actionType = "up"
      break
    case 2:
      actionType = "down"
      break
    default:
      logDebug("Unknown sceneNumber: ${cmd}")
  }

  if (actionType && cmd.keyAttributes == 3) {
    sceneEvt.name = "doubleTapped"
    sceneEvt.desc = "button ${sceneEvt.value} ${sceneEvt.name} [${actionType}]"
    sendEventLog(sceneEvt, ep)
  }

  switch (cmd.keyAttributes) {
    case 0:
      sceneEvt.name = "pushed"
      btnVal = "${actionType} 1x"
      break
    case 1:
      sceneEvt.name = "released"
      btnVal = "${actionType} released"
      break
    case 2:
      sceneEvt.name = "held"
      btnVal = "${actionType} held"
      break
    case {it >=3 && it <= 6}:
      sceneEvt.name = "pushed"
      sceneEvt.value = cmd.sceneNumber + (2 * (cmd.keyAttributes - 2))
      btnVal = "${actionType} ${cmd.keyAttributes - 1}x"
      break
    default:
      logDebug("Unknown keyAttributes: ${cmd}")
  }

  if (actionType && btnVal) {
    sceneEvt.desc = "button ${sceneEvt.value} ${sceneEvt.name} [${btnVal}]"
    sendEventLog(sceneEvt, ep)
  }
}


void sendEventLog(Map evt, Integer ep=0) {
  evt.descriptionText = evt.desc ?: "${evt.name} set to ${evt.value} ${evt.unit ?: ''}".trim()

  if (device.currentValue(evt.name).toString() != evt.value.toString() || evt.isStateChange) {
    logInfo("${evt.descriptionText}")
  } else {
    logDebug("${evt.descriptionText} [NOT CHANGED]")
  }
  sendEvent(evt)
}

void sendSwitchEvents(Object rawVal, String type, Integer ep = 0) {
  String value = (rawVal ? "on" : "off")
  String desc = "switch is turned ${value}" + (type ? " (${type})" : "")
  sendEventLog(name:"switch", value:value, type:type, desc:desc, ep)
  turningOn[device.id] = 0

  if (rawVal) {
    Integer level = (rawVal == 99 ? 100 : rawVal)
    level = convertLevel(level, false)

    desc = "level is set to ${level}%"
    if (type) desc += " (${type})"
    if (levelCorrection) desc += " [actual: ${rawVal}]"
    sendEventLog(name:"level", value:level, type:type, unit:"%", desc:desc, ep)
  }
}

void sendBasicButtonEvent(Integer buttonId, String name) {
  String desc = "button ${buttonId} ${name} (digital)"
  sendEventLog(name:name, value:buttonId, type:"digital", desc:desc, isStateChange:true)
}


void executeConfigureCmds() {
  logDebug("executeConfigureCmds...")
  checkLogLevel()

  List<String> cmds = []

  if (!firmwareVersion || !state.deviceModel) {
    cmds << versionGetCmd()
  }

  cmds += getConfigureAssocsCmds()

  configParams.each { param ->
    Integer paramVal = getParamValueAdj(param)
    Integer storedVal = getParamStoredValue(param.num)

    if ((paramVal != null) && (state.resyncAll || (storedVal != paramVal))) {
      logDebug("Changing ${param.name} - ${param.title} (#${param.num}) from ${storedVal} to ${paramVal}")
      cmds += configSetGetCmd(param, paramVal)
    }
  }

  state.resyncAll = false

  if (cmds) sendCommands(cmds)
}

void executeRefreshCmds() {
  List<String> cmds = []

  if (state.resyncAll || !firmwareVersion || !state.deviceModel) {
    cmds << versionGetCmd()
    runIn(3, "checkSceneReverse")
  }

  cmds << switchMultilevelGetCmd()

  sendCommands(cmds)
}

List<String> getConfigureAssocsCmds() {
  List<String> cmds = []

  if (!state.group1Assoc || state.resyncAll) {
    if (state.group1Assoc == false) {
      logDebug("Adding missing lifeline association...")
    }
    cmds << associationSetCmd(1, [zwaveHubNodeId])
    cmds << associationGetCmd(1)
  }

  for (int i = 2; i <= maxAssocGroups; i++) {
    List<String> cmdsEach = []
    List settingNodeIds = getAssocDNIsSettingNodeIds(i)

    List oldNodeIds = state."assocNodes$i"?.findAll { !(it in settingNodeIds) }
    if (oldNodeIds) {
      logDebug("Removing Nodes: Group $i - $oldNodeIds")
      cmdsEach << associationRemoveCmd(i, oldNodeIds)
    }

    List newNodeIds = settingNodeIds.findAll { !(it in state."assocNodes$i") }
    if (newNodeIds) {
      logDebug("Adding Nodes: Group $i - $newNodeIds")
      cmdsEach << associationSetCmd(i, newNodeIds)
    }

    if (cmdsEach || state.resyncAll) {
      cmdsEach << associationGetCmd(i)
      cmds += cmdsEach
    }
  }

  return cmds
}

String getOnOffCmds(Object val, Integer endPoint = 0) {
  return getSetLevelCmds(val ? 0xFF : 0x00, null, endPoint)
}

String getSetLevelCmds(Number level, Number duration = null, Integer endPoint = 0) {
  Short levelVal = safeToInt(level, 99)
  if (levelVal != 0xFF && levelVal != 0x00) {
    levelVal = convertLevel(levelVal, true)
    levelVal = validateRange(levelVal, 99, 1, 99)
  }


  if (duration > 120) {
    logDebug("getSetLevelCmds converting ${duration}s to ${Math.round(duration/60)}min")
    duration = (duration / 60) + 127
  }

  Short durationVal = validateRange(duration, -1, -1, 254)
  if (duration == null || durationVal == -1) {
    durationVal = 0xFF
  }

  logDebug("getSetLevelCmds output [level:${levelVal}, duration:${durationVal}, endPoint:${endPoint}]")
  return switchMultilevelSetCmd(levelVal, durationVal, endPoint)
}



void fixParamsMap() {
  paramsMap.ledColor.options << ledColorOptions
  paramsMap.autoOffInterval.options << autoOnOffIntervalOptions
  paramsMap.autoOnInterval.options << autoOnOffIntervalOptions
  paramsMap.rampRate.options << rampRateOptions
  paramsMap.rampRateOff.options << rampRateOptions
  paramsMap.holdRampRate.options << rampRateOptions
  paramsMap.zwaveRampRateOn.options << rampRateOptions
  paramsMap.zwaveRampRateOff.options << rampRateOptions
  paramsMap.remoteZWaveDim.options << rampRateOptions
  paramsMap.minimumBrightness.options << brightnessOptions
  paramsMap.maximumBrightness.options << brightnessOptions
  paramsMap.customBrightness.options << brightnessOptions
  paramsMap.nightLight.options << brightnessOptions
  paramsMap['settings'] = [fixed: true]
}

Integer getParamValueAdj(Map param) {
  return getParamValue(param)
}


Boolean hardwareLevelCorrection() {
  Short modelNum = deviceModelShort
  BigDecimal firmVer = firmwareVersion
  Boolean retVal = false

  if ((modelNum == 77) && ((firmVer >= 2.10 && firmVer < 10.0) || firmVer >= 10.20)) {
    retVal = true
    if (levelCorrection) { device.removeSetting("levelCorrection") }
  }
  return retVal
}

void checkSceneReverse() {
  String devModel = state.deviceModel

  if (devModel in ["ZEN72", "ZEN77"]) {
    logDebug("Scene Reverse switched off, known Model/Firmware match found.")
    device.updateSetting("sceneReverse", [value:"false",type:"bool"])
  } else {
    logWarn("Scene Reverse unchanged, no known Model/Firmware match.")
  }
}




void zwaveParse(String description) {
  hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)

  if (cmd) {
    logTrace("parse: ${description} --PARSED-- ${cmd}")
    zwaveEvent(cmd)
  } else {
    logWarn("Unable to parse: ${description}")
  }

}

void zwaveMultiChannel(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  hubitat.zwave.Command encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
  logTrace("${cmd} --ENCAP-- ${encapsulatedCmd}")
  if (encapsulatedCmd) {
    zwaveEvent(encapsulatedCmd, cmd.sourceEndPoint as Integer)
  } else {
    logWarn("Unable to extract encapsulated cmd from $cmd")
  }
}

void zwaveSupervision(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Integer ep = 0) {
  hubitat.zwave.Command encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
  logTrace("${cmd} --ENCAP-- ${encapsulatedCmd}")
  if (encapsulatedCmd) {
    zwaveEvent(encapsulatedCmd, ep)
  } else {
    logWarn("Unable to extract encapsulated cmd from $cmd")
  }

  sendCommands(secureCmd(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0), ep))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
  logTrace("${cmd}")
  String fullVersion = String.format("%d.%02d",cmd.firmware0Version,cmd.firmware0SubVersion)
  String zwaveVersion = String.format("%d.%02d",cmd.zWaveProtocolVersion,cmd.zWaveProtocolSubVersion)
  device.updateDataValue("firmwareVersion", fullVersion)
  device.updateDataValue("protocolVersion", zwaveVersion)
  device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")

  logDebug("Received Version Report - Firmware: ${fullVersion}")
  setDevModel(new BigDecimal(fullVersion))
}

void zwaveEvent(hubitat.zwave.Command cmd, Integer ep = 0) {
  logDebug("Unhandled zwaveEvent: $cmd (ep ${ep})")}



void sendCommands(List<String> cmds, Long delay=200) {
  Integer packetsCount = supervisedPackets?."${device.id}"?.size()
  if (packetsCount > 0) {
    Integer delayTotal = (cmds.size() * delay) + 2000
    logDebug("Setting supervisionCheck to ${delayTotal}ms | ${packetsCount} | ${cmds.size()} | ${delay}")
    runInMillis(delayTotal, "supervisionCheck", [data:1])
  }

  sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendCommands(String cmd) {
  sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

String associationSetCmd(Integer group, List<Integer> nodes) {
  return supervisionEncap(zwave.associationV2.associationSet(groupingIdentifier: group, nodeId: nodes))
}

String associationRemoveCmd(Integer group, List<Integer> nodes) {
  return supervisionEncap(zwave.associationV2.associationRemove(groupingIdentifier: group, nodeId: nodes))
}

String associationGetCmd(Integer group) {
  return secureCmd(zwave.associationV2.associationGet(groupingIdentifier: group))
}

String mcAssociationGetCmd(Integer group) {
  return secureCmd(zwave.multiChannelAssociationV3.multiChannelAssociationGet(groupingIdentifier: group))
}

String versionGetCmd() {
  return secureCmd(zwave.versionV2.versionGet())
}

String switchBinarySetCmd(Integer value, Integer ep=0) {
  return supervisionEncap(zwave.switchBinaryV1.switchBinarySet(switchValue: value), ep)
}

String switchBinaryGetCmd(Integer ep=0) {
  return secureCmd(zwave.switchBinaryV1.switchBinaryGet(), ep)
}

String switchMultilevelSetCmd(Integer value, Integer duration, Integer ep=0) {
  return supervisionEncap(zwave.switchMultilevelV2.switchMultilevelSet(dimmingDuration: duration, value: value), ep)
}

String switchMultilevelGetCmd(Integer ep=0) {
  return secureCmd(zwave.switchMultilevelV2.switchMultilevelGet(), ep)
}

String switchMultilevelStartLvChCmd(Boolean upDown, Integer duration, Integer ep=0) {
  return supervisionEncap(zwave.switchMultilevelV2.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel:1, dimmingDuration: duration), ep)
}

String switchMultilevelStopLvChCmd(Integer ep=0) {
  return supervisionEncap(zwave.switchMultilevelV2.switchMultilevelStopLevelChange(), ep)
}

String meterGetCmd(Map meter, Integer ep = 0) {
  return secureCmd(zwave.meterV3.meterGet(scale: meter.scale), ep)
}

String meterResetCmd(Integer ep=0) {
  return secureCmd(zwave.meterV3.meterReset(), ep)
}

String wakeUpIntervalGetCmd() {
  return secureCmd(zwave.wakeUpV2.wakeUpIntervalGet())
}

String wakeUpIntervalSetCmd(Integer val) {
  return secureCmd(zwave.wakeUpV2.wakeUpIntervalSet(seconds:val, nodeid:zwaveHubNodeId))
}

String wakeUpNoMoreInfoCmd() {
  return secureCmd(zwave.wakeUpV2.wakeUpNoMoreInformation())
}

String batteryGetCmd() {
  return secureCmd(zwave.batteryV1.batteryGet())
}

String sensorMultilevelGetCmd(Integer sensorType) {
  Integer scale = (temperatureScale == "F" ? 1 : 0)
  return secureCmd(zwave.sensorMultilevelV11.sensorMultilevelGet(scale: scale, sensorType: sensorType))
}

String notificationGetCmd(Integer notificationType, Integer eventType, Integer ep = 0) {
  return secureCmd(zwave.notificationV3.notificationGet(notificationType: notificationType, v1AlarmType:0, event: eventType), ep)
}

String configSetCmd(Map param, Integer value) {
  Long sizeFactor = Math.pow(256,param.size).round()
  if (value >= sizeFactor/2) { value -= sizeFactor }

  return supervisionEncap(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: value))
}

String configGetCmd(Map param) {
  return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

List configSetGetCmd(Map param, Integer value) {
  List<String> cmds = []
  cmds << configSetCmd(param, value)
  cmds << configGetCmd(param)
  return cmds
}



String secureCmd(String cmd) {
  return zwaveSecureEncap(cmd)
}
String secureCmd(hubitat.zwave.Command cmd, Integer ep = 0) {
  return zwaveSecureEncap(multiChannelEncap(cmd, ep))
}

String multiChannelEncap(hubitat.zwave.Command cmd, Integer ep) {
  if (ep > 0) {
    cmd = zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:ep).encapsulate(cmd)
  }
  return cmd.format()
}

@Field static Map<String, Map<Short, String>> supervisedPackets = new java.util.concurrent.ConcurrentHashMap()
@Field static Map<String, Short> sessionIDs = new java.util.concurrent.ConcurrentHashMap()

String supervisionEncap(hubitat.zwave.Command cmd, Integer ep = 0) {
  if (settings.supervisionGetEncap) {
    Short sessId = getSessionId()
    hubitat.zwave.Command supervisedCmd = zwave.supervisionV1.supervisionGet(sessionID: sessId).encapsulate(cmd)

    String cmdEncap = multiChannelEncap(supervisedCmd, ep)

    logDebug("New Supervised Packet for Session: ${sessId}")
    if (supervisedPackets["${device.id}"] == null) { supervisedPackets["${device.id}"] = [:] }
    supervisedPackets["${device.id}"][sessId] = cmdEncap

    Integer packetsCount = supervisedPackets?."${device.id}"?.size()
    Integer delayTotal = (packetsCount * 500) + 2000
    runInMillis(delayTotal, "supervisionCheck", [data:1])

    return secureCmd(cmdEncap)
  }
  else {
    return secureCmd(cmd, ep)
  }
}

Short getSessionId() {
  Short sessId = sessionIDs["${device.id}"] ?: state.lastSupervision ?: 0
  sessId = (sessId + 1) % 64
  state.lastSupervision = sessId
  sessionIDs["${device.id}"] = sessId

  return sessId
}

void supervisionCheck(Integer num) {
  Integer packetsCount = supervisedPackets?."${device.id}"?.size()
  logDebug("Supervision Check #${num} - Packet Count: ${packetsCount}")
  if (packetsCount > 0 ) {
    List<String> cmds = []
    supervisedPackets["${device.id}"].each { sid, cmd ->
      logWarn("Re-Sending Supervised Session: ${sid} (Retry #${num})")
      cmds << secureCmd(cmd)
    }
    sendCommands(cmds)

    if (num >= 3) {
      logWarn("Supervision MAX RETIES (${num}) Reached")
      supervisedPackets["${device.id}"].clear()
    }
    else {
      Integer delayTotal = (packetsCount * 500) + 2000
      runInMillis(delayTotal, "supervisionCheck", [data:num+1])
    }
  }
}

@Field static Map<String, Map> configsList = new java.util.concurrent.ConcurrentHashMap()
Integer getParamStoredValue(Integer paramNum) {
  Map configsMap = getParamStoredMap()
  return safeToInt(configsMap[paramNum], null)
}

void setParamStoredValue(Integer paramNum, Integer value) {
  TreeMap configsMap = getParamStoredMap()
  configsMap[paramNum] = value
  configsList[device.id][paramNum] = value
}

Map getParamStoredMap() {
  TreeMap configsMap = configsList[device.id]
  if (configsMap == null) {
    configsMap = [:]
    if (device.getDataValue("configVals")) {
      try {
        configsMap = evaluate(device.getDataValue("configVals"))
      }
      catch(Exception e) {
        logWarn("Clearing Invalid configVals: ${e}")
        device.removeDataValue("configVals")
      }
    }
    configsList[device.id] = configsMap
  }
  return configsMap
}

@Field static Map<String, Map<String, List>> paramsList = new java.util.concurrent.ConcurrentHashMap()
void updateParamsList() {
  logDebug("Update Params List")
  String devModel = state.deviceModel
  BigDecimal firmware = firmwareVersion

  List<Map> tmpList = []
  paramsMap.each { name, pMap ->
    Map tmpMap = pMap.clone()
    tmpMap.options = tmpMap.options?.clone()

    tmpMap.name = name

    tmpMap.changes.each { m, changes ->
      if (m == devModel || m == "7X") {
        tmpMap.putAll(changes)
        if (changes.options) { tmpMap.options = changes.options.clone() }
      }
    }
    tmpMap.changesFR.each { m, changes ->
      if (firmware >= m.getFrom() && firmware <= m.getTo()) {
        tmpMap.putAll(changes)
        if (changes.options) { tmpMap.options = changes.options.clone() }
      }
    }
    tmpMap.remove("changes")
    tmpMap.remove("changesFR")

    tmpMap.options.each { k, val ->
      if (k == tmpMap.defaultVal) {
        tmpMap.options[(k)] = "${val} [DEFAULT]"
      }
    }

    tmpList << tmpMap
  }

  tmpList.removeAll { it.num == null }
  tmpList.removeAll { firmware < (it.firmVer ?: 0) }
  tmpList.removeAll {
    if (it.firmVerM) {
      (firmware-(int)firmware)*100 < it.firmVerM[(int)firmware]
    }
  }

  if (paramsList[devModel] == null) paramsList[devModel] = [:]
  paramsList[devModel][firmware] = tmpList
}

void verifyParamsList() {
  String devModel = state.deviceModel
  BigDecimal firmware = firmwareVersion
  if (!paramsMap.settings?.fixed) fixParamsMap()
  if (paramsList[devModel] == null) updateParamsList()
  if (paramsList[devModel][firmware] == null) updateParamsList()
}

List<Map> getConfigParams() {
  if (!device) return []
  String devModel = state.deviceModel
  BigDecimal firmware = firmwareVersion

  if (devModel) { verifyParamsList() }
  else          { runInMillis(200, "setDevModel") }
  if (!devModel || devModel == "UNK00") return []

  return paramsList[devModel][firmware]
}

Map getParam(String search) {
  verifyParamsList()
  return configParams.find{ it.name == search }
}
Map getParam(Number search) {
  verifyParamsList()
  return configParams.find{ it.num == search }
}

BigDecimal getParamValue(String paramName) {
  return getParamValue(getParam(paramName))
}
BigDecimal getParamValue(Map param) {
  if (param == null) return
  BigDecimal paramVal = safeToDec(settings."configParam${param.num}", param.defaultVal)

  if (param.hidden && settings."configParam${param.num}" != null) {
    logWarn("Resetting hidden parameter ${param.name} (${param.num}) to default ${param.defaultVal}")
    device.removeSetting("configParam${param.num}")
    paramVal = param.defaultVal
  }

  return paramVal
}

String fmtTitle(String str) {
  return "<strong>${str}</strong>"
}
String fmtDesc(String str) {
  return "<div style='font-size: 85%; font-style: italic; padding: 1px 0px 4px 2px;'>${str}</div>"
}
private getTimeOptionsRange(String name, Integer multiplier, List range) {
  return range.collectEntries{ [(it*multiplier): "${it} ${name}${it == 1 ? '' : 's'}"] }
}

private getBrightnessOptions() {
  Map options = [1:"1%"]
  for(x=5; x<100; x+=5) { options << [(x):"${x}%"] }
  options << [99:"99%"]
  return options
}

private getRampRateOptions() {
  return getTimeOptionsRange("Second", 1, [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,20,25,30,45,60,75,90])
}

private getAutoOnOffIntervalOptions() {
  Map options = [0:"Disabled"]
  options << getTimeOptionsRange("Minute", 1, [1,2,3,4,5,6,7,8,9,10,15,20,25,30,45])
  options << getTimeOptionsRange("Hour", 60, [1,2,3,4,5,6,7,8,9,10,12,18])
  options << getTimeOptionsRange("Day", (60 * 24), [1,2,3,4,5,6])
  options << getTimeOptionsRange("Week", (60 * 24 * 7), [1,2,3,4])
  return options
}

void updateSyncingStatus(Integer delay=2) {
  runIn(delay, "refreshSyncStatus")
  sendEvent(name:"syncStatus", value:"Syncing...")
}

void refreshSyncStatus() {
  Integer changes = pendingChanges
  sendEvent(name:"syncStatus", value:(changes ? "${changes} Pending Changes" : "Synced"))
  device.updateDataValue("configVals", getParamStoredMap()?.inspect())
}

Integer getPendingChanges() {
  Integer configChanges = configParams.count { param ->
    Integer paramVal = getParamValueAdj(param)
    ((paramVal != null) && (paramVal != getParamStoredValue(param.num)))
  }
  Integer pendingAssocs = Math.ceil(getConfigureAssocsCmds()?.size()/2) ?: 0
  return (!state.resyncAll ? (configChanges + pendingAssocs) : configChanges)
}

String getAssocDNIsSetting(Integer grp) {
  String val = settings."assocDNI$grp"
  return ((val && (val.trim() != "0")) ? val : "")
}

List<Integer> getAssocDNIsSettingNodeIds(Integer grp) {
  String dni = getAssocDNIsSetting(grp)
  List<Integer> nodeIds = convertHexListToIntList(dni.split(","))

  if (dni && !nodeIds) {
    logWarn("'${dni}' is not a valid value for the 'Device Associations - Group ${grp}' setting.  All z-wave devices have a 2 character Device Network ID and if you're entering more than 1, use commas to separate them.")
  }
  else if (nodeIds.size() > maxAssocNodes) {
    logWarn("The 'Device Associations - Group ${grp}' setting contains more than ${maxAssocNodes} IDs so some (or all) may not get associated.")
  }

  return nodeIds
}

void clearVariables() {
  logWarn("Clearing state variables and data...")
  String devModel = state.deviceModel
  Object engTime = state.energyTime

  state.clear()

  configsList["${device.id}"] = [:]
  device.removeDataValue("configVals")
  device.removeDataValue("zwaveAssociationG1")
  device.removeDataValue("zwaveAssociationG2")
  device.removeDataValue("zwaveAssociationG3")

  if (devModel) state.deviceModel = devModel
  if (engTime) state.energyTime = engTime
}

String setDevModel(BigDecimal firmware) {
  if (!device) return
  List<String> devTypeId = convertIntListToHexList([
    safeToInt(device.getDataValue("deviceType")),
    safeToInt(device.getDataValue("deviceId"))
  ], 4)
  String devModel = deviceModelNames[devTypeId.join(":")] ?: "UNK00"
  if (!firmware) { firmware = firmwareVersion }

  state.deviceModel = devModel
  device.updateDataValue("deviceModel", devModel)
  logDebug("Set Device Info - Model: ${devModel} | Firmware: ${firmware}")
  if (devModel == "UNK00") {
    logWarn("Unsupported Device USE AT YOUR OWN RISK: ${devTypeId}")
    state.WARNING = "Unsupported Device Model - USE AT YOUR OWN RISK!"
  }
  else state.remove("WARNING")

  verifyParamsList()

  return devModel
}

Integer getDeviceModelShort() {
  return safeToInt(state.deviceModel?.drop(3))
}

BigDecimal getFirmwareVersion() {
  String version = device?.getDataValue("firmwareVersion")
  return ((version != null) && version.isNumber()) ? version.toBigDecimal() : 0.0
}

List<String> convertIntListToHexList(List<Integer> intList, Integer pad = 2) {
  List<String> hexList = []
  intList?.each { Integer value ->
    hexList.add(Integer.toHexString(value).padLeft(pad, "0").toUpperCase())
  }
  return hexList
}

List<Integer> convertHexListToIntList(String[] hexList) {
  List<Integer> intList = []

  hexList?.each { String value ->
    try {
      intList.add(Integer.parseInt(value.trim(), 16))
    }
    catch (Exception ignored) { }
  }
  return intList
}

Integer convertLevel(Number level, Boolean userLevel = false) {
  if (levelCorrection) {
    Integer brightmax = getParamValue("maximumBrightness")
    Integer brightmin = getParamValue("minimumBrightness")
    brightmax = (brightmax == 99) ? 100 : brightmax
    brightmin = (brightmin == 1) ? 0 : brightmin

    if (userLevel) {
      level = ((brightmax-brightmin) * (level/100)) + brightmin
      state.levelActual = level
      level = validateRange(Math.round(level), brightmax, brightmin, brightmax)
    }
    else {
      if (Math.round(state.levelActual ?: 0) == level) level = state.levelActual
      else state.levelActual = level

      level = ((level - brightmin) / (brightmax - brightmin)) * 100
      level = validateRange(Math.round(level), 100, 1, 100)
    }
  }
  else if (state.levelActual) {
    state.remove("levelActual")
  }

  return level
}

Integer validateRange(Object val, Integer defaultVal, Integer lowVal, Integer highVal) {
  Integer intVal = safeToInt(val, defaultVal)
  if (intVal > highVal) {
    return highVal
  } else if (intVal < lowVal) {
    return lowVal
  } else {
    return intVal
  }
}


Integer safeToInt(Object val, Integer defaultVal = 0) {
  if ("${val}"?.isInteger())
    { return "${val}".toInteger() }
  else if ("${val}"?.isNumber())
  { return "${val}".toDouble()?.round() }
  else { return defaultVal }
}

BigDecimal safeToDec(Object val, Number defaultVal = 0, Integer roundTo = -1) {
  BigDecimal decVal = "${val}"?.isNumber() ? "${val}".toBigDecimal() : defaultVal
  if (roundTo == 0)
    { decVal = Math.round(decVal) }
  else if (roundTo > 0)
  { decVal = decVal.setScale(roundTo, BigDecimal.ROUND_HALF_UP).stripTrailingZeros() }
  if (decVal.scale()<0)
  { decVal = decVal.setScale(0) }
  return decVal
}

Boolean isDuplicateCommand(Long lastExecuted, Long allowedMil) {
  !lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}
