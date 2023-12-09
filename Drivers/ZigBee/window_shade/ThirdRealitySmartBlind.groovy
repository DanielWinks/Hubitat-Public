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

import hubitat.zigbee.zcl.DataType

#include dwinks.UtilitiesAndLogging

metadata {
  definition(name: 'ThirdReality Smart Blind', namespace: 'dwinks', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Refresh'
    capability 'Window Shade'
    capability 'Switch Level'
    capability 'Battery'
    attribute 'lastCheckin', 'STRING'
    command 'updateFirmware'
    fingerprint deviceJoinName: 'ThirdReality Window Blind', model: '3RSB015BZ', profileId: '0104', endpointId: 01, inClusters: '0000,0001,0006,0102', outClusters: '0006,0102,0019', manufacturer: 'Third Reality, Inc'
  }
}

preferences {
  section {
    input name: 'updateInterval', title: 'Refresh update interval', type: 'enum', required: true, defaultValue: 6,
      options: [1:'1 hour', 2:'2 hours', 3:'3 hours', 6:'6 hours', 8:'8 hours', 12:'12 hours']
    // input 'updateInterval', 'enum', title: 'Refresh Update Interval', required: true, defaultValue: '3Hours', options: ['15Minutes', '30Minutes', '1Hour', '3Hours']

  }
}

@Field private final Integer CLUSTER_BATTERY_LEVEL = 0x0001
@Field private final Integer CLUSTER_WINDOW_COVERING = 0x0102
@Field private final Integer COMMAND_OPEN = 0x00
@Field private final Integer COMMAND_CLOSE = 0x01
@Field private final Integer COMMAND_PAUSE = 0x02
@Field private final Integer COMMAND_GOTO_LIFT_PERCENTAGE = 0x05
@Field private final Integer ATTRIBUTE_POSITION_LIFT = 0x0008
@Field private final Integer ATTRIBUTE_CURRENT_LEVEL = 0x0000
@Field private final Integer COMMAND_MOVE_LEVEL_ONOFF = 0x04
@Field private final Integer BATTERY_PERCENTAGE_REMAINING = 0x0021
@Field private final Integer OPEN_LEVEL = 100
@Field private final Integer CLOSED_LEVEL = 0

void parse(String description) {
  logDebug("parse(): ${description}")
  sendEvent(name: 'lastCheckin', value: nowFormatted())
  if (description?.startsWith('read attr -')) {
    Map descriptionMap = zigbee.parseDescriptionAsMap(description)
    if (descriptionMap.value && descriptionMap?.clusterInt == CLUSTER_WINDOW_COVERING) {
      if (descriptionMap?.attrInt == ATTRIBUTE_POSITION_LIFT) {
        currentLevel =  zigbee.convertHexToInt(descriptionMap.value)
        setLevelStates(currentLevel, device.currentValue('position') as Integer)
      }
    } else if (descriptionMap.value && descriptionMap?.clusterInt == CLUSTER_BATTERY_LEVEL) {
      sendEvent(name: 'battery', value: ((zigbee.convertHexToInt(descriptionMap.value) - 50) / 1.5) as Integer)
    }
  }
}

void setLevelStates(Integer currentLevel, Integer lastLevel) {
  Integer adjLevel = 100 - currentLevel
  logDebug("Setting current device state to ${adjLevel} for level and ${adjLevel} for position")
  if (adjLevel == CLOSED_LEVEL || adjLevel == OPEN_LEVEL) {
    sendEvent(name: 'windowShade', value: adjLevel == CLOSED_LEVEL ? 'closed' : 'open')
  } else if (adjLevel != CLOSED_LEVEL && adjLevel != OPEN_LEVEL){
    sendEvent([name:'windowShade', value: 'partially open'])
  } else {
    sendEvent(name: 'windowShade', value: lastLevel < adjLevel ? 'opening' : 'closing')
  }
  sendEvent(name: 'level', value: adjLevel)
  sendEvent(name: 'position', value: adjLevel)
}

void updateFirmware() {
  logInfo('Updating device firmware...')
  zigbee.updateFirmware()
}

void open() {
  logInfo('Fully Opening the Blinds.')
  sendEvent(name: 'windowShade', value: 'opening')
  state.desiredPosition = OPEN_LEVEL
  scheduleValidation()
  List<String> cmds =  zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
  sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void close() {
  logInfo('Fully Closing the Blinds.')
  sendEvent(name: 'windowShade', value: 'closing')
  state.desiredPosition = CLOSED_LEVEL
  scheduleValidation()
  List<String> cmds = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
  sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void setPosition(BigDecimal value) {
  logInfo("Setting the Blinds to position: ${value}.")
  setShadePosition(value as Integer)
}

void setShadePosition(Integer value) {
  logDebug("Commanding shade to value: ${value}")
  state.desiredPosition = value
  Integer convertedLevel = 100 - Math.max(Math.min(value, 100), 0)
  logDebug("Converted Level: ${convertedLevel}")
  scheduleValidation()
  List<String> cmds = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(convertedLevel, 2))
  sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void setLevel(BigDecimal value, rate = null) {
  logInfo("Setting the Blinds to level: ${value}")
  setShadePosition(value as Integer)
}

void scheduleValidation() {
  runIn(30, 'refresh')
  runIn(60, 'validatePosition')
}

void validateLevel() {
  logDebug('Validating level...')
  Integer desiredLevel = state.desiredLevel
  Integer currentLevel = device.currentValue('level')
  if (desiredLevel != currentLevel) {
    setLevel(desiredLevel)
  } else {
    logDebug('Desired level matches current level. Success!')
    state.clear()
  }
}

void validatePosition() {
  logDebug 'Validating position...'
  Integer desiredPosition = state.desiredPosition ?: device.currentValue('position')
  Integer currentPosition = device.currentValue('position')
  if (desiredPosition != currentPosition) {
    setPosition(desiredPosition)
  } else {
    logDebug 'Desired position matches current position. Success!'
    state.clear()
  }
}

void stopPositionChange() {
  runIn(10, 'refresh')
  state.clear()
  unschedule('validatePosition')
  unschedule('validateLevel')
  zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_PAUSE)
}

void startPositionChange(String direction) {
  if(direction == 'open') {
    open()
  } else if (direction == 'close') {
    close()
  }
  runIn(10, 'refresh')
}

void refresh() {
  logDebug('Running refresh...')
  positionLift = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT)
  batteryPercentage = zigbee.readAttribute(CLUSTER_BATTERY_LEVEL, BATTERY_PERCENTAGE_REMAINING)
  List<String> cmds =  positionLift + batteryPercentage
  sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
  scheduleRefresh()
}

void initialize() { configure() }
void configure() {
  logDebug('Configuring Device Reporting and Bindings.')
  positionLiftReporting = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 0, 600, 0x01)
  batteryPercentageReporting = zigbee.configureReporting(CLUSTER_BATTERY_LEVEL, 0x0021, DataType.UINT8, 600, 21600, 0x01)
  List<String> cmds = positionLiftReporting + batteryPercentageReporting
  sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
  refresh()
}

void scheduleRefresh() {
  Date scheduledRefresh = new Date(now() + (settings.updateInterval as Integer) * 60 * 60 * 1000)
  logDebug("Scheduling refresh for ${scheduledRefresh}")
  schedule(scheduledRefresh, 'refresh', [overwrite:true])
}
