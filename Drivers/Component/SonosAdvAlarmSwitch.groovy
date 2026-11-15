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

import groovy.transform.Field

@Field static final String PENDING_BATCH_EVENTS_KEY = 'pendingBatchEvents'
@Field static final String BATCH_EVENTS_SCHEDULED_KEY = 'batchEventsScheduled'
@Field static final String LAST_SENT_BATCH_EVENTS_KEY = 'lastSentBatchEvents'
@Field static final Integer BATCH_EVENTS_PER_DRAIN = 8
@Field static final Integer BATCH_EVENTS_INTERVAL_MS = 200

void logWarn(String message) {
  if(settings.logEnable != false) { log.warn "${device.label ?: device.name}: ${message}" }
}

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
void installed() { initialize() }
void updated() { initialize() }
void initialize() { configure() }
void configure() {
  unschedule()
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  schedulePendingBatchEvents()
}
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

void applyStateBatch(String jsonAttributes) {
  Map attributes
  try { attributes = (Map)parseJson(jsonAttributes) }
  catch(Exception e) { logWarn("Ignoring malformed alarm batch: ${e.message}"); return }
  if(!(attributes instanceof Map) || attributes.isEmpty()) { return }
  Map<String, Map> pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? (Map<String, Map>)state[PENDING_BATCH_EVENTS_KEY] : [:]
  Map<String, Map> lastSent = state[LAST_SENT_BATCH_EVENTS_KEY] instanceof Map
      ? (Map<String, Map>)state[LAST_SENT_BATCH_EVENTS_KEY] : [:]
  attributes.each { Object rawName, Object rawValue ->
    String name = rawName?.toString()
    Map event = rawValue instanceof Map && ((Map)rawValue).containsKey('value') ? (Map)rawValue : [value: rawValue]
    Map previous = pending[name] instanceof Map ? pending[name] : lastSent[name]
    if(name && event.value != null && (!previous || previous.value != event.value || previous.unit != event.unit)) {
      pending[name] = event
    }
  }
  if(!pending.isEmpty()) {
    state[PENDING_BATCH_EVENTS_KEY] = pending
    schedulePendingBatchEvents()
  }
}

private void schedulePendingBatchEvents() {
  if(state[PENDING_BATCH_EVENTS_KEY] instanceof Map && !((Map)state[PENDING_BATCH_EVENTS_KEY]).isEmpty()
      && state[BATCH_EVENTS_SCHEDULED_KEY] != true) {
    state[BATCH_EVENTS_SCHEDULED_KEY] = true
    runInMillis(100, 'drainBatchEvents', [overwrite: true])
  }
}

void drainBatchEvents() {
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  Map<String, Map> pending = state[PENDING_BATCH_EVENTS_KEY] instanceof Map
      ? (Map<String, Map>)state[PENDING_BATCH_EVENTS_KEY] : [:]
  if(pending.isEmpty()) { state.remove(PENDING_BATCH_EVENTS_KEY); return }
  Map<String, Map> lastSent = state[LAST_SENT_BATCH_EVENTS_KEY] instanceof Map
      ? (Map<String, Map>)state[LAST_SENT_BATCH_EVENTS_KEY] : [:]
  List<String> names = new ArrayList<String>(pending.keySet()).take(BATCH_EVENTS_PER_DRAIN)
  names.each { String name ->
    Map event = pending.remove(name) as Map
    try {
      if(event.unit) { sendEvent(name: name, value: event.value, unit: event.unit) }
      else { sendEvent(name: name, value: event.value) }
      lastSent[name] = event
    } catch(Exception e) {
      pending.put(name, event)
      logWarn("Could not emit ${name}; retaining it for retry: ${e.message}")
    }
  }
  if(!lastSent.isEmpty()) { state[LAST_SENT_BATCH_EVENTS_KEY] = lastSent }
  if(pending.isEmpty()) {
    state.remove(PENDING_BATCH_EVENTS_KEY)
  } else {
    state[PENDING_BATCH_EVENTS_KEY] = pending
    state[BATCH_EVENTS_SCHEDULED_KEY] = true
    runInMillis(BATCH_EVENTS_INTERVAL_MS, 'drainBatchEvents', [overwrite: true])
  }
}
