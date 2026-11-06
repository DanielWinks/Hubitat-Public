/**
 *  MIT License
 *  Copyright 2026 Daniel Winks
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

metadata {
  definition(
    name: 'Sonos Advanced Alarm Switch',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvAlarmSwitch.groovy'
  ) {
    capability 'Switch'
    capability 'Refresh'
    capability 'Actuator'

    command 'setAlarmTime', [[name: 'Time (HH:mm:ss)', type: 'STRING']]
    command 'setAlarmVolume', [[name: 'Volume (0-100)', type: 'NUMBER']]
    command 'setRecurrence', [[name: 'Recurrence', type: 'ENUM', constraints: ['ONCE', 'DAILY', 'WEEKDAYS', 'WEEKENDS']]]
    command 'setDuration', [[name: 'Duration (HH:mm:ss)', type: 'STRING']]
    command 'setAlarmRoom', [[name: 'Player ID (RINCON)', type: 'STRING']]
    command 'setAlarmSource', [[name: 'Program URI', type: 'STRING'], [name: 'Program Metadata', type: 'STRING']]
    command 'setPlayMode', [[name: 'Play Mode', type: 'ENUM', constraints: ['NORMAL', 'REPEAT_ALL', 'SHUFFLE_NOREPEAT', 'SHUFFLE']]]
    command 'setIncludeGroupedRooms', [[name: 'Include rooms currently grouped with this room?', description: 'When enabled, the alarm also plays in other rooms grouped with the alarm room when it sounds.', type: 'ENUM', constraints: ['true', 'false']]]

    attribute 'alarmTime', 'string'
    attribute 'alarmVolume', 'number'
    attribute 'recurrence', 'string'
    attribute 'room', 'string'
    attribute 'duration', 'string'
    attribute 'alarmSource', 'string'
    attribute 'playMode', 'string'
    attribute 'includeGroupedRooms', 'string'
  }
}

void on() { parent?.setSonosAlarmEnabled(device.deviceNetworkId, true) }
void off() { parent?.setSonosAlarmEnabled(device.deviceNetworkId, false) }
void refresh() {
  String householdId = device.getDataValue('householdId')
  if(householdId) { parent?.refreshSonosAlarmsForHousehold(householdId) }
}
void setAlarmTime(String time) { parent?.setSonosAlarmTime(device.deviceNetworkId, time) }
void setAlarmVolume(BigDecimal volume) { parent?.setSonosAlarmVolume(device.deviceNetworkId, volume?.intValue()) }
void setRecurrence(String recurrence) { parent?.setSonosAlarmRecurrence(device.deviceNetworkId, recurrence) }
void setDuration(String duration) { parent?.setSonosAlarmDuration(device.deviceNetworkId, duration) }
void setAlarmRoom(String playerId) { parent?.setSonosAlarmRoom(device.deviceNetworkId, playerId) }
void setAlarmSource(String programUri, String programMetadata) { parent?.setSonosAlarmSource(device.deviceNetworkId, programUri, programMetadata) }
void setPlayMode(String playMode) { parent?.setSonosAlarmPlayMode(device.deviceNetworkId, playMode) }
void setIncludeGroupedRooms(String include) { parent?.setSonosAlarmIncludeGroupedRooms(device.deviceNetworkId, include == 'true') }
