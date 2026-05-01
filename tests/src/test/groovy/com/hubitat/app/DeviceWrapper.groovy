package com.hubitat.app

/** Test stub for the Hubitat platform's DeviceWrapper class. */
class DeviceWrapper {
  String deviceNetworkId
  String displayName
  Map<String, Object> currentValues = [:]
  Map<String, Object> updatedSettings = [:]
  Object currentValue(String n)                   { currentValues[n] }
  Object currentValue(String n, boolean fresh)    { currentValues[n] }
  void   updateSetting(String n, Object v)        { updatedSettings[n] = v }
  void   updateSetting(String n, Map opts)        { updatedSettings[n] = opts.value }
  void   setDeviceNetworkId(String dni)           { this.deviceNetworkId = dni }
  String getDeviceNetworkId() { deviceNetworkId }
  String getDisplayName()     { displayName }
  List<Map> getCurrentStates() { currentValues.collect { String n, Object v -> [name: n, value: v] } }
  void deleteCurrentState(String n) { currentValues.remove(n) }
}
