package com.hubitat.app

/** Test stub for the Hubitat platform's DeviceWrapper class. */
class DeviceWrapper {
  String deviceNetworkId
  String displayName
  String label
  String name
  Map<String, Object> currentValues = [:]
  Map<String, String> dataValues = [:]
  Map<String, Object> updatedSettings = [:]
  List<Map> events = []
  Object currentValue(String n)                   { currentValues[n] }
  Object currentValue(String n, boolean fresh)    { currentValues[n] }
  void   updateSetting(String n, Object v)        { updatedSettings[n] = v }
  void   updateSetting(String n, Map opts)        { updatedSettings[n] = opts.value }
  void   setDeviceNetworkId(String dni)           { this.deviceNetworkId = dni }
  String getDeviceNetworkId() { deviceNetworkId }
  String getDisplayName()     { displayName }
  String getLabel()           { label ?: displayName }
  String getName()            { name ?: displayName }
  String getDataValue(String key) { dataValues[key] }
  void updateDataValue(String key, String value) { dataValues[key] = value }
  void removeDataValue(String key) { dataValues.remove(key) }
  void sendEvent(Map event) {
    events << event
    if(event?.name) { currentValues[event.name as String] = event.value }
  }
  List<Map> getCurrentStates() { currentValues.collect { String n, Object v -> [name: n, value: v] } }
  void deleteCurrentState(String n) { currentValues.remove(n) }
}
