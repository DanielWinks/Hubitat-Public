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

void logError(String message) {
  if (settings.logEnable != false) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
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
void logWarn(String message) {
  if (settings.logEnable != false) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}
void logTrace(String message) {
  if (settings.logEnable != false) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

metadata {
  definition(
    name: 'Sonos Advanced Secondaries',
    version: '0.18.9',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    component: true,
    singleThreaded: true,
    importUrl:'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/Component/SonosAdvSecondaries.groovy'
  ) {
      capability 'Initialize'
    }
}
// =============================================================================
// Fields
// =============================================================================
@Field static final Set<String> ACTIVE_SECONDARY_STATE_KEYS = Collections.unmodifiableSet(new HashSet<String>([
]))

@Field static final Set<String> ACTIVE_SECONDARY_DEVICE_DATA_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'id',
  'deviceIp',
  'householdId',
]))

@Field static final Set<String> ACTIVE_SECONDARY_CURRENT_STATE_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>([]))

@Field static final Set<String> ACTIVE_SECONDARY_SETTINGS_KEYS = Collections.unmodifiableSet(new HashSet<String>([
  'logEnable',
  'debugLogEnable',
]))

// =============================================================================
// Migration-Prune Helpers — whitelist-driven for-each removal
// =============================================================================
Map<String, Object> pruneOrphanedSecondaryPersistence() {
  Map<String, Object> result = [
    stateRemoved: pruneOrphanStateKeys(state as Map, ACTIVE_SECONDARY_STATE_KEYS),
    deviceDataRemoved: pruneOrphanDeviceData(device, ACTIVE_SECONDARY_DEVICE_DATA_KEYS),
    currentStatesRemoved: pruneOrphanCurrentStates(device, ACTIVE_SECONDARY_CURRENT_STATE_ATTRIBUTES),
    settingsRemoved: pruneOrphanSettings(ACTIVE_SECONDARY_SETTINGS_KEYS),
  ]
  Integer total = ((List)result.stateRemoved).size() + ((List)result.deviceDataRemoved).size() +
    ((List)result.currentStatesRemoved).size() + ((List)result.settingsRemoved).size()
  if(total > 0) {
    logDebug("Secondary persistence prune: removed ${total} orphan value(s) (state=${result.stateRemoved}, deviceData=${result.deviceDataRemoved}, currentStates=${result.currentStatesRemoved}, settings=${result.settingsRemoved})")
  }
  return result
}

List<String> pruneOrphanStateKeys(Map persisted, Set<String> active) {
  if(persisted == null || active == null) { return [] }
  List<String> toRemove = new ArrayList<String>(persisted.keySet())
    .findAll { String k -> !active.contains(k.toString()) }
  toRemove.each { String k -> persisted.remove(k) }
  return toRemove
}

List<String> pruneOrphanDeviceData(Object dev, Set<String> active) {
  if(dev == null || active == null) { return [] }
  try {
    Map data = dev.getData()
    if(data == null) { return [] }
    List<String> toRemove = new ArrayList<String>(data.keySet())
      .findAll { String k -> !active.contains(k) }
    toRemove.each { String k -> dev.removeDataValue(k) }
    return toRemove
  } catch(Exception e) {
    return []
  }
}

List<String> pruneOrphanCurrentStates(Object dev, Set<String> active) {
  if(dev == null || active == null) { return [] }
  try {
    List removed = []
    dev.getCurrentStates().each { Object st ->
      String name = null
      try { name = st?.name?.toString() } catch(Exception ignored) { name = null }
      if(name && !active.contains(name)) {
        try { dev.deleteCurrentState(name); removed << name } catch(Exception ignored) { }
      }
    }
    return removed
  } catch(Exception e) {
    return []
  }
}

List<String> pruneOrphanSettings(Set<String> active) {
  if(active == null) { return [] }
  List<String> toRemove = new ArrayList<String>(settings.keySet())
    .findAll { String k -> !active.contains(k) }
  toRemove.each { String k ->
    try { settings.remove(k) } catch(Exception ignored) { }
    try { device.clearSetting(k) } catch(Exception ignored) { }
    try { device.updatedSettings?.remove(k) } catch(Exception ignored) { }
  }
  return toRemove
}
// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Initialize And Configure
// =============================================================================
void initialize() { configure() }
void configure() {
  pruneOrphanedSecondaryPersistence()
}
void installed() { configure() }
void updated() { configure() }
// =============================================================================
// End Initialize And Configure
// =============================================================================



// =============================================================================
// Websocket Connection and Initialization
// =============================================================================
// Getters and Setters
// =============================================================================
String getLocalApiUrl(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "https://${deviceIp}:1443/api/v1/"
}
String getLocalUpnpHost(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "${deviceIp}:1400"
}
String getLocalUpnpUrl(){
  String deviceIp = this.device.getDataValue('deviceIp')
  if(!deviceIp) { return null }
  return "http://${deviceIp}:1400"
}
String getHouseholdId(){
  return this.device.getDataValue('householdId')
}
void setHouseholdId(String householdId) {
  this.device.updateDataValue('householdId', householdId)
}

String getId() {
  return this.device.getDataValue('id')
}
void setId(String id) {
  this.device.updateDataValue('id', id)
}
// =============================================================================
// End Getters and Setters
// =============================================================================
// =============================================================================




// =============================================================================
