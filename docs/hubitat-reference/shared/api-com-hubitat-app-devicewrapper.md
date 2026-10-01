# Device wrapper

- **ID:** `api-com-hubitat-app-devicewrapper`
- **Section:** Shared APIs
- **Class:** `com.hubitat.app.DeviceWrapper`

> Use DeviceWrapper from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `clearSetting` | `void clearSetting(String name)` | Parameters: name - setting name whose stored value should be cleared |
| `currentState` | `State currentState(String attributeName, boolean skipCache = false)` | Read the current state record for one device attribute. Parameters: attributeName - device attribute name skipCache - true to bypass the cached state Returns: current state, or nul |
| `currentValue` | `Object currentValue(String attributeName, boolean skipCache = false)` | Read the current value of one device attribute. Parameters: attributeName - device attribute name skipCache - true to bypass the cached value Returns: current value, or null when t |
| `deleteCurrentState` | `void deleteCurrentState(String attributeName)` | Parameters: attributeName - device attribute whose stored current state should be deleted |
| `DeviceWrapper` | `DeviceWrapper(Device device)` | Wrap an already loaded device. throws: IllegalArgumentException when device is null Parameters: device - device to wrap; null is rejected |
| `DeviceWrapper` | `DeviceWrapper(Long deviceId)` | Create a wrapper that loads a device by id when first needed. throws: IllegalArgumentException when deviceId is null or zero Parameters: deviceId - device database id; null and zer |
| `events` | `List<Event> events(Map options = [max: 10])` | Read recent events for this device. Parameters: options - map with optional max (truthy result limit, default 10) and optional name (truthy event name filter, sanitized to letters  |
| `eventsBetween` | `List<Event> eventsBetween(Date startDate, Date endDate, Map options = null)` | Read recent events for this device within a date range. Parameters: startDate - lower date bound endDate - upper date bound options - optional map with max, a truthy result limit p |
| `eventsSince` | `List<Event> eventsSince(Date startDate, Map options = null)` | Read recent events for this device after a date. Parameters: startDate - inclusive lower date bound options - optional map with max, a truthy result limit passed to the event query |
| `fetchLastEvent` | `Map fetchLastEvent()` | Read the latest event as a UI-compatible map. Returns: null when the device has no event; otherwise a map with these keys: id: Long event id. date: Date of the event. source: Strin |
| `getCapabilities` | `List<Capability> getCapabilities()` | Returns: capabilities declared by this device's type |
| `getControllerType` | `String getControllerType()` | Returns: controller type recorded for the device |
| `getCurrentStates` | `List<State> getCurrentStates()` | Returns: all current state records for this device |
| `getData` | `Map getData()` | Returns: map with driver-defined keys; no fixed key set is declared. Each key is a String data name. Each value is an Object stored by the driver; values may be strings, numbers, B |
| `getDataValue` | `String getDataValue(String name)` | Parameters: name - device data key Returns: stored value as text, or null when absent |
| `getDeviceDataByName` | `String getDeviceDataByName(String name)` | Parameters: name - device data key Returns: stored value as text, or null when absent |
| `getDeviceNetworkId` | `String getDeviceNetworkId()` | Returns: device network id |
| `getDisplayAsChild` | `Boolean getDisplayAsChild()` | Returns: true when the device is displayed as a child, or null when unknown |
| `getDisplayName` | `String getDisplayName()` | Returns: the device's display name |
| `getDriverId` | `Long getDriverId()` | Returns: database id of the device type used as the driver |
| `getDriverType` | `String getDriverType()` | Returns: driver implementation type |
| `getEndpointId` | `String getEndpointId()` | Returns: endpoint id for this device, or null when the device has none |
| `getHub` | `Hub getHub()` | Returns: hub that owns this device |
| `getId` | `String getId()` | Returns: device database id as a String |
| `getIdAsLong` | `Long getIdAsLong()` | Returns: device database id as a Long |
| `getIsComponent` | `Boolean getIsComponent()` | Returns: true when the device is a component device, or null when unknown |
| `getLabel` | `String getLabel()` | Returns: device label |
| `getLanId` | `String getLanId()` | Returns: LAN id |
| `getLastActivity` | `Date getLastActivity()` | Returns: last event/activity time as a UTC-readable date, or null when no activity exists |
| `getName` | `String getName()` | Returns: device name |
| `getNotes` | `String getNotes()` | Returns: device notes, or null when none are set |
| `getParentAppId` | `Long getParentAppId()` | Returns: parent app id, or null when the device has no parent app |
| `getParentDeviceId` | `Long getParentDeviceId()` | Returns: parent device id, or null when the device has no parent device |
| `getRoomId` | `Long getRoomId()` | Returns: room id, or null when the device has no room |
| `getRoomName` | `String getRoomName()` | Returns: room name, or null when the device has no room |
| `getSetting` | `Object getSetting(String name)` | Parameters: name - setting name Returns: converted setting value, raw stored text on conversion failure, or null when absent |
| `getSettingType` | `String getSettingType(String name)` | Parameters: name - setting name Returns: setting type, or null when the name is null or unknown |
| `getStatus` | `String getStatus()` | Returns: current device status text |
| `getSupportedAttributes` | `List<Attribute> getSupportedAttributes()` | Returns: attributes declared by this device's type |
| `getSupportedCommands` | `List<Command> getSupportedCommands()` | Returns: commands declared by this device's type |
| `getTypeName` | `String getTypeName()` | Returns: device type name |
| `getZigbeeId` | `String getZigbeeId()` | Returns: Zigbee identifier for this device, or null when it has none |
| `hasAttribute` | `Boolean hasAttribute(String attribute)` | Parameters: attribute - attribute name Returns: true when this device type declares the attribute |
| `hasCapability` | `Boolean hasCapability(String capability)` | Parameters: capability - capability name Returns: true when this device type declares the capability |
| `hasCommand` | `Boolean hasCommand(String command)` | Parameters: command - command name Returns: true when this device type declares the command |
| `isControllerMatter` | `boolean isControllerMatter()` | Returns: true when the device uses a Matter controller |
| `isControllerZigbee` | `boolean isControllerZigbee()` | Returns: true when the device uses a Zigbee controller |
| `isControllerZWave` | `boolean isControllerZWave()` | Returns: true when the device uses a Z-Wave controller |
| `isDisabled` | `boolean isDisabled()` | Returns: true when the device is disabled |
| `isLinkedDevice` | `boolean isLinkedDevice()` | Returns: true when this is a linked device |
| `isRetryEnabled` | `boolean isRetryEnabled()` | Returns: true when command retry is enabled for this device |
| `isSingleThreaded` | `boolean isSingleThreaded()` | Returns: true when commands for this device type use a single executor thread |
| `latestState` | `State latestState(String attributeName, boolean skipCache = false)` | Read the current state record for one device attribute. Parameters: attributeName - device attribute name skipCache - true to bypass the cached state Returns: current state, or nul |
| `latestValue` | `Object latestValue(String attributeName, boolean skipCache = false)` | Read the current value of one device attribute. Parameters: attributeName - device attribute name skipCache - true to bypass the cached value Returns: current value, or null when t |
| `removeDataValue` | `void removeDataValue(String name)` | Parameters: name - device data key to remove |
| `removeSetting` | `void removeSetting(String name)` | Parameters: name - setting name to remove |
| `sendEvent` | `void sendEvent(Map properties)` | Create and process an event for this device. Parameters: properties - event options using the keys and behavior documented by Event.populateValues |
| `setDeviceNetworkId` | `void setDeviceNetworkId(String dni)` | Change the device network id and save the updated device. Parameters: dni - new device network id |
| `setDisplayName` | `void setDisplayName(String displayName)` | Parameters: displayName - new display name, stored as the device label |
| `setLabel` | `void setLabel(String label)` | Change the device label and save the updated device. Parameters: label - new device label |
| `setLanId` | `void setLanId(String lanId)` | Change the LAN id and save the updated device. Parameters: lanId - new LAN id |
| `setName` | `void setName(String name)` | Change the device name and save the updated device. Parameters: name - new device name |
| `statesSince` | `List<State> statesSince(String attributeName, Date startDate, Map options = null)` | Read recent states for one device attribute after a date. Parameters: attributeName - device attribute name startDate - inclusive lower date bound options - optional map with max,  |
| `toString` | `String toString()` | Returns: device display name |
| `updateDataValue` | `void updateDataValue(String name, String value)` | Save a String value in the device data map. Parameters: name - device data key value - data value to save |
| `updateSetting` | `void updateSetting(String name, Boolean value)` | Store a Boolean setting value. Parameters: name - setting name value - Boolean setting value |
| `updateSetting` | `void updateSetting(String name, Date value)` | Store a time setting value. Parameters: name - setting name value - time setting value |
| `updateSetting` | `void updateSetting(String name, Double value)` | Store a decimal setting value. Parameters: name - setting name value - decimal setting value |
| `updateSetting` | `void updateSetting(String name, List value)` | Store one or more selected enum values. Parameters: name - setting name value - selected enum value or list of enum values |
| `updateSetting` | `void updateSetting(String name, Long value)` | Store a numeric setting value. Parameters: name - setting name value - numeric setting value |
| `updateSetting` | `void updateSetting(String name, Map options)` | Update a device setting using its declared setting type. Parameters: name - setting name options - map with these optional keys: value: any non-null setting value; null or missing  |
| `updateSetting` | `void updateSetting(String name, String value)` | Store a text setting value. Parameters: name - setting name value - text setting value |
