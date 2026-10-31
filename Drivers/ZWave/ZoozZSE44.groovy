/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
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

@Field static final Map DEVICE_MODEL_NAMES = ['7000:E004': 'ZSE44']
@Field static final Map LOG_LEVELS = [0: 'Trace', 1: 'Debug', 2: 'Info', 3: 'Warn', 4: 'Error', 5: 'Off']

@Field static final Integer SENSOR_TYPE_TEMPERATURE = 0x01
@Field static final Integer SENSOR_TYPE_LUMINANCE = 0x03
@Field static final Integer SENSOR_TYPE_HUMIDITY = 0x05
@Field static final Integer NOTIFICATION_TYPE_SECURITY = 0x07
@Field static final Integer NOTIFICATION_TYPE_HEAT = 0x04
@Field static final Integer NOTIFICATION_TYPE_WEATHER = 0x10

@Field static final Map COMMAND_CLASS_VERSIONS = [
  0x31: 11,
  0x70: 1,
  0x71: 8,
  0x72: 2,
  0x80: 1,
  0x84: 2,
  0x85: 2,
  0x86: 2
]

@Field static Map<String, Map> CONFIG_VALUES = new java.util.concurrent.ConcurrentHashMap()
@Field static Map<String, Map<String, List>> PARAMETER_LISTS = new java.util.concurrent.ConcurrentHashMap()

metadata {
  definition(
    name: 'Zooz ZSE44 Temperature Humidity XS Sensor',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    singleThreaded: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/ZWave/ZoozZSE44.groovy'
  ) {
    capability('Sensor')
    capability('RelativeHumidityMeasurement')
    capability('TemperatureMeasurement')
    capability('IlluminanceMeasurement')
    capability('Battery')
    capability('TamperAlert')
    capability('Configuration')
    capability('Refresh')

    command('fullConfigure')
    command('forceRefresh')
    command('setParameter', [
      [name: 'parameterNumber*', type: 'NUMBER', description: 'Parameter number'],
      [name: 'value*', type: 'NUMBER', description: 'Parameter value'],
      [name: 'size', type: 'NUMBER', description: 'Parameter size']
    ])

    attribute('syncStatus', 'string')

    fingerprint(
      mfr: '027A',
      prod: '7000',
      deviceId: 'E004',
      inClusters: '0x00,0x00',
      controllerType: 'ZWV'
    )
  }

  preferences {
    configParams.each { Map param ->
      if (!param.hidden) {
        if (param.options) {
          input("configParam${param.num}", 'enum',
            title: fmtTitle(param.title),
            description: fmtDesc("• Parameter #${param.num}, Selected: ${getParamValue(param)}" + (param.description ? "<br>• ${param.description}" : '')),
            defaultValue: param.defaultVal,
            options: param.options,
            required: false
          )
        }
        else if (param.range) {
          input("configParam${param.num}", 'number',
            title: fmtTitle(param.title),
            description: fmtDesc("• Parameter #${param.num}, Range: ${param.range}, DEFAULT: ${param.defaultVal}" + (param.description ? "<br>• ${param.description}" : '')),
            defaultValue: param.defaultVal,
            range: param.range,
            required: false
          )
        }
      }
    }

    input('tempOffset', 'decimal',
      title: fmtTitle('Temperature offset (driver)'),
      description: fmtDesc('Range: -25.0..25.0, DEFAULT: 0'),
      defaultValue: 0,
      range: '-25..25',
      required: false
    )
    input('humidityOffset', 'decimal',
      title: fmtTitle('Humidity offset (driver)'),
      description: fmtDesc('Range: -25.0..25.0, DEFAULT: 0'),
      defaultValue: 0,
      range: '-25..25',
      required: false
    )
    input('wakeUpInt', 'number',
      title: fmtTitle('Wake-up interval (hours)'),
      description: fmtDesc('How often the device wakes up to receive commands from the hub.'),
      defaultValue: 12,
      range: '1..24',
      required: false
    )
    input('logLevel', 'enum',
      title: fmtTitle('Logging level'),
      description: fmtDesc('Logs selected level and above.'),
      defaultValue: 2,
      options: LOG_LEVELS,
      required: false
    )
  }
}

@Field static final Map PARAMS = [
  batteryLow: [
    num: 2,
    title: 'Low battery report (%)',
    size: 1,
    defaultVal: 10,
    range: '10..50'
  ],
  tempTrigger: [
    num: 3,
    title: 'Temperature change report trigger (1 = 0.1° / 10 = 1°)',
    size: 1,
    defaultVal: 20,
    range: '10..100'
  ],
  humidityTrigger: [
    num: 4,
    title: 'Humidity change report trigger (%)',
    size: 1,
    defaultVal: 5,
    range: '1..50'
  ],
  tempOffsetHw: [
    num: 14,
    title: 'Temperature offset (hardware)',
    size: 1,
    defaultVal: 0,
    range: '-10.0..10.0'
  ],
  humidOffsetHw: [
    num: 15,
    title: 'Humidity offset (hardware)',
    size: 1,
    defaultVal: 0,
    range: '-10.0..10.0'
  ],
  tempInterval: [
    num: 16,
    title: 'Temperature reporting interval (minutes)',
    description: 'Set to 0 to disable.',
    size: 2,
    defaultVal: 240,
    range: '0..480'
  ],
  humidInterval: [
    num: 17,
    title: 'Humidity reporting interval (minutes)',
    description: 'Set to 0 to disable.',
    size: 2,
    defaultVal: 240,
    range: '0..480'
  ],
  tempUnits: [
    num: 13,
    title: 'Temperature units',
    size: 1,
    defaultVal: 1,
    options: [0: 'Celsius (°C)', 1: 'Fahrenheit (°F)']
  ]
]

// ============================================================================
// Logging
// ============================================================================

Integer getLogLevel() {
  Integer level = safeToInt(settings.logLevel, 2)
  return validateRange(level, 2, 0, 5)
}

void logError(String message) {
  if (getLogLevel() <= 4) {
    log.error("${device.displayName}: ${message}")
  }
}

void logWarn(String message) {
  if (getLogLevel() <= 3) {
    log.warn("${device.displayName}: ${message}")
  }
}

void logInfo(String message) {
  if (getLogLevel() <= 2) {
    log.info("${device.displayName}: ${message}")
  }
}

void logDebug(String message) {
  if (getLogLevel() <= 1) {
    log.debug("${device.displayName}: ${message}")
  }
}

void logTrace(String message) {
  if (getLogLevel() == 0) {
    log.trace("${device.displayName}: ${message}")
  }
}

// ============================================================================
// Lifecycle and custom commands
// ============================================================================

void installed() {
  logInfo('Installed Zooz ZSE44 driver')
  state.resyncAll = true
  state.pendingRefresh = true
  runIn(2, 'runWakeupCommands')
  sendCommands(getRefreshCommands(), 400L)
}

void initialize() {
  configure()
}

void updated() {
  logDebug('Preferences updated')
  if (!firmwareVersion || !state.deviceModel) {
    state.resyncAll = true
    state.pendingRefresh = true
    logForceWakeupMessage('Full reconfigure and refresh')
  }
  if (pendingChanges) {
    logForceWakeupMessage('Pending configuration changes')
  }
  updateSyncingStatus(1)
}

void configure() {
  logDebug('Configuring sleepy device')
  if (!pendingChanges || state.resyncAll == null) {
    clearVariables()
    state.resyncAll = true
  }
  updateSyncingStatus(1)
  logForceWakeupMessage('Configuration')
}

void fullConfigure() {
  logInfo('Full configuration requested')
  state.resyncAll = true
  state.pendingRefresh = true
  updateSyncingStatus(1)
  logForceWakeupMessage('Full reconfigure')
}

void forceRefresh() {
  logInfo('Refresh requested')
  state.pendingRefresh = true
  updateSyncingStatus(1)
  logForceWakeupMessage('Sensor refresh')
}

void refresh() {
  forceRefresh()
}

void setParameter(Integer parameterNumber, Integer value, Integer size = null) {
  Map param = getParam(parameterNumber)
  Integer parameterSize = size ?: (param?.size as Integer)
  if (parameterNumber == null || value == null || parameterSize == null) {
    logWarn('Parameter number, value, and size are required')
    return
  }
  logDebug("Setting parameter ${parameterNumber} to ${value} with size ${parameterSize}")
  sendCommands(configSetCmd([num: parameterNumber, size: parameterSize], value))
}

// ============================================================================
// Z-Wave parsing and reports
// ============================================================================

void parse(String description) {
  zwaveParse(description)
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  zwaveMultiChannel(cmd)
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Integer ep = 0) {
  zwaveSupervision(cmd, ep)
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  logTrace("${cmd}")
  updateSyncingStatus()

  Map param = getParam(cmd.parameterNumber)
  Integer value = cmd.scaledConfigurationValue as Integer
  if (value < 0) {
    Long sizeFactor = Math.pow(256, param?.size ?: 1).round()
    value += sizeFactor
  }

  if (param) {
    logDebug("${param.name} - ${param.title} (#${param.num}) = ${value}")
    setParamStoredValue(param.num as Integer, value)
  }
  else {
    logDebug("Parameter #${cmd.parameterNumber} = ${value}")
  }
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logTrace("${cmd}")
  updateSyncingStatus()
  Integer group = cmd.groupingIdentifier as Integer
  if (group == 1) {
    List<Integer> nodes = (cmd.nodeId ?: []).collect { Object node -> node as Integer }
    state.group1Assoc = (nodes == [zwaveHubNodeId])
    logDebug("Lifeline association: ${nodes}")
  }
  else {
    logDebug("Unhandled association group: ${group}")
  }
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  Integer batteryLevel = cmd.batteryLevel as Integer
  if (batteryLevel == 0xFF) {
    batteryLevel = 1
    logWarn('Low battery warning')
  }
  batteryLevel = validateRange(batteryLevel, 100, 1, 100)
  sendEventLog([
    name: 'battery',
    value: batteryLevel,
    unit: '%',
    desc: "Battery level is ${batteryLevel}%",
    isStateChange: true
  ])
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
  logTrace("${cmd}")
  BigDecimal wakeHours = safeToDec((cmd.seconds as BigDecimal) / 3600, 0, 2)
  logDebug("Wake-up interval is ${cmd.seconds} seconds (${wakeHours} hours)")
  device.updateDataValue('zwWakeupInterval', "${cmd.seconds}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  logDebug('Wake-up notification received')
  runWakeupCommands()
}

void runWakeupCommands() {
  List<String> commands = ['delay 0', batteryGetCmd()]
  if (state.pendingRefresh) {
    commands += getRefreshCommands()
  }
  commands += getConfigureCommands()
  commands += ['delay 1400', wakeUpNoMoreInfoCmd()]

  state.resyncAll = false
  state.pendingRefresh = false
  state.remove('INFO')
  sendCommands(commands, 300L)
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  switch (cmd.sensorType as Integer) {
    case SENSOR_TYPE_TEMPERATURE:
      String temperature = convertTemperatureIfNeeded(cmd.scaledSensorValue, (cmd.scale ? 'F' : 'C'), cmd.precision)
      BigDecimal temperatureOffset = safeToDec(settings.tempOffset, 0)
      BigDecimal adjustedTemperature = safeToDec(temperature, 0) + temperatureOffset
      Integer precision = Math.min(cmd.precision as Integer, 1)
      logDebug("Temperature offset by ${temperatureOffset} from ${temperature} to ${adjustedTemperature}")
      sendEventLog([
        name: 'temperature',
        value: safeToDec(adjustedTemperature, 0, precision),
        unit: "°${temperatureScale}"
      ], ep)
      break
    case SENSOR_TYPE_HUMIDITY:
      BigDecimal humidityOffset = safeToDec(settings.humidityOffset, 0)
      BigDecimal adjustedHumidity = safeToDec(cmd.scaledSensorValue, 0) + humidityOffset
      Integer humidityPrecision = Math.min(cmd.precision as Integer, 1)
      logDebug("Humidity offset by ${humidityOffset} from ${cmd.scaledSensorValue} to ${adjustedHumidity}")
      sendEventLog([
        name: 'humidity',
        value: safeToDec(adjustedHumidity, 0, humidityPrecision),
        unit: '%'
      ], ep)
      break
    case SENSOR_TYPE_LUMINANCE:
      BigDecimal illuminance = safeToDec(cmd.scaledSensorValue, 0, 0)
      logDebug("Illuminance is ${illuminance} lux")
      sendEventLog([
        name: 'illuminance',
        value: illuminance,
        unit: 'lx'
      ], ep)
      break
    default:
      logDebug("Unhandled sensor type: ${cmd.sensorType}")
  }
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, Integer ep = 0) {
  logTrace("${cmd} (ep ${ep})")
  switch (cmd.notificationType as Integer) {
    case NOTIFICATION_TYPE_SECURITY:
    case NOTIFICATION_TYPE_HEAT:
    case NOTIFICATION_TYPE_WEATHER:
      logDebug("Notification type ${cmd.notificationType}, event ${cmd.event}")
      break
    default:
      logDebug("Unhandled notification: ${cmd}")
  }
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
  logTrace("${cmd}")
  String firmware = String.format('%d.%02d', cmd.firmware0Version, cmd.firmware0SubVersion)
  String protocol = String.format('%d.%02d', cmd.zWaveProtocolVersion, cmd.zWaveProtocolSubVersion)
  device.updateDataValue('firmwareVersion', firmware)
  device.updateDataValue('protocolVersion', protocol)
  device.updateDataValue('hardwareVersion', "${cmd.hardwareVersion}")
  logDebug("Received version report - firmware: ${firmware}")
  setDevModel(firmware.toBigDecimal())
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  logTrace("${cmd}")
  device.updateDataValue('manufacturer', "${cmd.manufacturerId}")
  device.updateDataValue('deviceType', "${cmd.productTypeId}")
  device.updateDataValue('deviceId', "${cmd.productId}")
  logDebug("Manufacturer report identified ${cmd.manufacturerId}:${cmd.productTypeId}:${cmd.productId}")
  setDevModel(firmwareVersion)
}

void zwaveEvent(hubitat.zwave.Command cmd, Integer ep = 0) {
  logDebug("Unhandled Z-Wave event: ${cmd} (ep ${ep})")
}

// ============================================================================
// Event handling
// ============================================================================

void sendEventLog(Map eventData, Integer ep = 0) {
  eventData.descriptionText = eventData.desc ?: "${eventData.name} set to ${eventData.value}${eventData.unit ?: ''}"
  Object currentValue = device.currentValue(eventData.name)
  if (eventData.isStateChange || currentValue?.toString() != eventData.value?.toString()) {
    logInfo("${eventData.descriptionText}")
  }
  else {
    logDebug("${eventData.descriptionText} [NOT CHANGED]")
  }
  sendEvent(eventData)
}

// ============================================================================
// Command building and sleepy-device configuration
// ============================================================================

List<String> getConfigureCommands() {
  List<String> commands = []
  Integer wakeSeconds = safeToInt(settings.wakeUpInt, 12) * 3600

  if (state.resyncAll || wakeSeconds != (device.getDataValue('zwWakeupInterval') as Integer)) {
    logDebug("Setting wake-up interval to ${wakeSeconds} seconds")
    commands << wakeUpIntervalSetCmd(wakeSeconds)
    commands << wakeUpIntervalGetCmd()
  }
  if (state.resyncAll || !firmwareVersion || !state.deviceModel) {
    commands << mfgSpecificGetCmd()
    commands << versionGetCmd()
  }
  commands += getConfigureAssociationCommands(true)

  configParams.each { Map param ->
    Integer desiredValue = getParamValueAdjusted(param)
    Integer storedValue = getParamStoredValue(param.num as Integer)
    if (desiredValue != null && (state.resyncAll || storedValue != desiredValue)) {
      logDebug("Changing ${param.name} - ${param.title} (#${param.num}) from ${storedValue} to ${desiredValue}")
      commands += configSetGetCmd(param, desiredValue)
    }
  }

  if (state.resyncAll) {
    clearVariables()
  }
  state.resyncAll = false
  if (commands) {
    updateSyncingStatus(6)
  }
  return commands
}

List<String> getRefreshCommands() {
  return [
    wakeUpIntervalGetCmd(),
    versionGetCmd(),
    sensorMultilevelGetCmd(SENSOR_TYPE_TEMPERATURE),
    sensorMultilevelGetCmd(SENSOR_TYPE_LUMINANCE),
    sensorMultilevelGetCmd(SENSOR_TYPE_HUMIDITY)
  ]
}

List<String> getConfigureAssociationCommands(Boolean queryAssociations = true) {
  List<String> commands = []
  if (!state.group1Assoc || state.resyncAll) {
    logDebug('Setting lifeline association')
    commands << associationSetCmd(1, [zwaveHubNodeId])
    if (queryAssociations) {
      commands << associationGetCmd(1)
    }
  }
  return commands
}

void logForceWakeupMessage(String message) {
  String helpText = 'Quickly press the internal button four times to wake the device.'
  logWarn("${message} will execute the next time the device wakes up. ${helpText}")
  state.INFO = "*** ${message} *** Waiting for device to wake up. ${helpText}"
}

// ============================================================================
// Z-Wave command helpers
// ============================================================================

void sendCommands(List<String> commands, Long delay = 200L) {
  if (commands) {
    sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(commands, delay), hubitat.device.Protocol.ZWAVE))
  }
}

void sendCommands(String command) {
  sendHubCommand(new hubitat.device.HubAction(command, hubitat.device.Protocol.ZWAVE))
}

String associationSetCmd(Integer group, List<Integer> nodes) {
  return secureCmd(zwave.associationV2.associationSet(groupingIdentifier: group, nodeId: nodes))
}

String associationGetCmd(Integer group) {
  return secureCmd(zwave.associationV2.associationGet(groupingIdentifier: group))
}

String versionGetCmd() {
  return secureCmd(zwave.versionV2.versionGet())
}

String mfgSpecificGetCmd() {
  return secureCmd(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
}

String wakeUpIntervalGetCmd() {
  return secureCmd(zwave.wakeUpV2.wakeUpIntervalGet())
}

String wakeUpIntervalSetCmd(Integer seconds) {
  return secureCmd(zwave.wakeUpV2.wakeUpIntervalSet(seconds: seconds, nodeid: zwaveHubNodeId))
}

String wakeUpNoMoreInfoCmd() {
  return secureCmd(zwave.wakeUpV2.wakeUpNoMoreInformation())
}

String batteryGetCmd() {
  return secureCmd(zwave.batteryV1.batteryGet())
}

String sensorMultilevelGetCmd(Integer sensorType) {
  Integer scale = sensorType == SENSOR_TYPE_TEMPERATURE && temperatureScale == 'F' ? 1 : 0
  return secureCmd(zwave.sensorMultilevelV11.sensorMultilevelGet(scale: scale, sensorType: sensorType))
}

String configSetCmd(Map param, Integer value) {
  Long sizeFactor = Math.pow(256, param.size as Integer).round()
  Integer signedValue = value
  if (signedValue >= sizeFactor / 2) {
    signedValue -= sizeFactor
  }
  return secureCmd(zwave.configurationV1.configurationSet(
    parameterNumber: param.num,
    size: param.size,
    scaledConfigurationValue: signedValue
  ))
}

String configGetCmd(Map param) {
  return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

List<String> configSetGetCmd(Map param, Integer value) {
  return [configSetCmd(param, value), configGetCmd(param)]
}

void zwaveParse(String description) {
  hubitat.zwave.Command command = zwave.parse(description, COMMAND_CLASS_VERSIONS)
  if (command) {
    logTrace("parse: ${description} --PARSED-- ${command}")
    zwaveEvent(command)
  }
  else {
    logWarn("Unable to parse: ${description}")
  }
}

void zwaveMultiChannel(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap command) {
  hubitat.zwave.Command encapsulated = command.encapsulatedCommand(COMMAND_CLASS_VERSIONS)
  logTrace("${command} --ENCAP-- ${encapsulated}")
  if (encapsulated) {
    zwaveEvent(encapsulated, command.sourceEndPoint as Integer)
  }
  else {
    logWarn("Unable to extract encapsulated command from ${command}")
  }
}

void zwaveSupervision(hubitat.zwave.commands.supervisionv1.SupervisionGet command, Integer ep = 0) {
  hubitat.zwave.Command encapsulated = command.encapsulatedCommand(COMMAND_CLASS_VERSIONS)
  logTrace("${command} --ENCAP-- ${encapsulated}")
  if (encapsulated) {
    zwaveEvent(encapsulated, ep)
  }
  else {
    logWarn("Unable to extract supervised command from ${command}")
  }
  sendCommands(secureCmd(zwave.supervisionV1.supervisionReport(
    sessionID: command.sessionID,
    reserved: 0,
    moreStatusUpdates: false,
    status: 0xFF,
    duration: 0
  ), ep))
}

String secureCmd(String command) {
  return zwaveSecureEncap(command)
}

String secureCmd(hubitat.zwave.Command command, Integer ep = 0) {
  return zwaveSecureEncap(multiChannelEncap(command, ep))
}

String multiChannelEncap(hubitat.zwave.Command command, Integer ep = 0) {
  if (ep > 0) {
    command = zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(command)
  }
  return command.format()
}

// ============================================================================
// Configuration and parameter helpers
// ============================================================================

Integer getParamStoredValue(Integer parameterNumber) {
  return safeToInt(getParamStoredMap()[parameterNumber], null)
}

void setParamStoredValue(Integer parameterNumber, Integer value) {
  Map values = getParamStoredMap()
  values[parameterNumber] = value
  CONFIG_VALUES[device.id][parameterNumber] = value
}

Map getParamStoredMap() {
  Map values = CONFIG_VALUES[device.id]
  if (values == null) {
    values = [:]
    String stored = device.getDataValue('configVals')
    if (stored) {
      try {
        values = evaluate(stored) as Map
      }
      catch (Exception exception) {
        logWarn("Clearing invalid stored configuration: ${exception}")
        device.removeDataValue('configVals')
      }
    }
    CONFIG_VALUES[device.id] = values
  }
  return values
}

void updateParameterList() {
  String model = state.deviceModel
  BigDecimal firmware = firmwareVersion
  if (!model) {
    return
  }

  List<Map> parameters = []
  PARAMS.each { String name, Map source ->
    Map parameter = source.clone()
    parameter.name = name
    parameter.options = parameter.options?.clone()
    parameter.options?.each { Object key, Object label ->
      if (key == parameter.defaultVal) {
        parameter.options[key] = "${label} [DEFAULT]"
      }
    }
    parameters << parameter
  }
  parameters.removeAll { Map parameter -> firmware < (parameter.firmVer ?: 0) }

  if (PARAMETER_LISTS[model] == null) {
    PARAMETER_LISTS[model] = [:]
  }
  PARAMETER_LISTS[model][firmware.toString()] = parameters
}

void verifyParameterList() {
  String model = state.deviceModel
  if (!model) {
    return
  }
  String firmware = firmwareVersion.toString()
  if (PARAMETER_LISTS[model] == null || PARAMETER_LISTS[model][firmware] == null) {
    updateParameterList()
  }
}

List<Map> getConfigParams() {
  if (!device || !state.deviceModel) {
    return []
  }
  verifyParameterList()
  return PARAMETER_LISTS[state.deviceModel][firmwareVersion.toString()] ?: []
}

Map getParam(String name) {
  return configParams.find { Map parameter -> parameter.name == name }
}

Map getParam(Number number) {
  return configParams.find { Map parameter -> parameter.num == number }
}

BigDecimal getParamValue(String name) {
  return getParamValue(getParam(name))
}

BigDecimal getParamValue(Map param) {
  if (!param) {
    return null
  }
  return safeToDec(settings["configParam${param.num}"], param.defaultVal)
}

Integer getParamValueAdjusted(Map param) {
  if (!param) {
    return null
  }
  BigDecimal value = getParamValue(param)
  if (param.name in ['tempOffsetHw', 'humidOffsetHw']) {
    value = (value * 10) + 100
    return validateRange(value, 100, 0, 200)
  }
  return safeToInt(value, null)
}

Integer getPendingChanges() {
  Integer configurationChanges = configParams.count { Map param ->
    Integer desired = getParamValueAdjusted(param)
    desired != null && desired != getParamStoredValue(param.num as Integer)
  }
  Integer associationChanges = getConfigureAssociationCommands(false).size()
  return state.resyncAll ? configurationChanges : configurationChanges + associationChanges
}

void updateSyncingStatus(Integer delay = 2) {
  runIn(delay, 'refreshSyncStatus')
  sendEvent(name: 'syncStatus', value: 'Syncing...')
}

void refreshSyncStatus() {
  Integer changes = pendingChanges
  sendEvent(name: 'syncStatus', value: changes ? "${changes} Pending Changes" : 'Synced')
  device.updateDataValue('configVals', getParamStoredMap().inspect())
}

void clearVariables() {
  logWarn('Clearing state variables and stored configuration')
  String model = state.deviceModel
  state.clear()
  CONFIG_VALUES[device.id] = [:]
  device.removeDataValue('configVals')
  if (model) {
    state.deviceModel = model
  }
}

String setDevModel(BigDecimal firmware = null) {
  if (!device) {
    return null
  }
  List<String> typeAndId = convertIntListToHexList([
    safeToInt(device.getDataValue('deviceType'), 0),
    safeToInt(device.getDataValue('deviceId'), 0)
  ], 4)
  String model = DEVICE_MODEL_NAMES[typeAndId.join(':')] ?: 'UNK00'
  state.deviceModel = model
  device.updateDataValue('deviceModel', model)
  logDebug("Set device model to ${model} for firmware ${firmware ?: firmwareVersion}")
  if (model == 'UNK00') {
    state.WARNING = "Unsupported device model: ${typeAndId.join(':')}"
    logWarn("Unsupported device model ${typeAndId.join(':')}")
  }
  else {
    state.remove('WARNING')
  }
  verifyParameterList()
  return model
}

Integer getDeviceModelShort() {
  return safeToInt(state.deviceModel?.drop(3), 0)
}

BigDecimal getFirmwareVersion() {
  String version = device?.getDataValue('firmwareVersion')
  return version?.isNumber() ? version.toBigDecimal() : 0.0G
}

List<String> convertIntListToHexList(List<Integer> values, Integer pad = 2) {
  return values.collect { Integer value -> Integer.toHexString(value).padLeft(pad, '0').toUpperCase() }
}

Integer validateRange(Object value, Integer defaultValue, Integer lowValue, Integer highValue) {
  Integer intValue = safeToInt(value, defaultValue)
  return Math.max(lowValue, Math.min(highValue, intValue))
}

Integer safeToInt(Object value, Integer defaultValue = 0) {
  String text = "${value}"
  if (text.isInteger()) {
    return text.toInteger()
  }
  if (text.isNumber()) {
    return text.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP).intValue()
  }
  return defaultValue
}

BigDecimal safeToDec(Object value, Number defaultValue = 0, Integer roundTo = -1) {
  String text = "${value}"
  BigDecimal decimal = text.isNumber() ? text.toBigDecimal() : defaultValue.toBigDecimal()
  if (roundTo == 0) {
    decimal = new BigDecimal(Math.round(decimal))
  }
  else if (roundTo > 0) {
    decimal = decimal.setScale(roundTo, BigDecimal.ROUND_HALF_UP).stripTrailingZeros()
  }
  return decimal.scale() < 0 ? decimal.setScale(0) : decimal
}

String fmtTitle(String text) {
  return "<strong>${text}</strong>"
}

String fmtDesc(String text) {
  return "<div style='font-size: 85%; font-style: italic; padding: 1px 0px 4px 2px;'>${text}</div>"
}
