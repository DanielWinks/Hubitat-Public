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

@Field static final String PENDING_BATCH_EVENTS_KEY = 'pendingBatchEvents'
@Field static final String BATCH_EVENTS_SCHEDULED_KEY = 'batchEventsScheduled'
@Field static final String LAST_SENT_BATCH_EVENTS_KEY = 'lastSentBatchEvents'
@Field static final Integer BATCH_EVENTS_PER_DRAIN = 8
@Field static final Integer BATCH_EVENTS_INTERVAL_MS = 200

void logError(String message) {
  if (settings.logEnable != false) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}

void logWarn(String message) {
  if (settings.logEnable != false) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}

void logInfo(String message) {
  if (settings.logEnable != false) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}

void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}

metadata {
  definition(
    name: 'Sonos Advanced Battery Status',
    version: '0.18.6',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvBatteryStats.groovy'
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'PowerSource' //powerSource - ENUM ["battery", "dc", "mains", "unknown"]
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C

  }
}

void initialize() { configure() }
void configure() {
  unschedule()
  state.remove(BATCH_EVENTS_SCHEDULED_KEY)
  schedulePendingBatchEvents()
}

void applyStateBatch(String jsonAttributes) {
  Map attributes
  try { attributes = (Map)parseJson(jsonAttributes) }
  catch(Exception e) { logWarn("Ignoring malformed battery state batch: ${e.message}"); return }
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
      logWarn("Could not emit battery ${name} event; retaining it for retry: ${e.message}")
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
