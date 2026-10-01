# Hub

- **ID:** `api-com-hubitat-hub-domain-hub`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Hub`

> Use Hub from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `addData` | `void addData(Map newData)` | Add entries to the stored hub data map. Parameters: newData - String-keyed entries using the stored data schema above; falsey input is ignored |
| `getData` | `Map getData()` | Returns: map with stored entries using the data field schema above, plus: zigbeeEui: String Zigbee identifier; nullable. zigbeeChannel: String Zigbee channel; nullable when network |
| `getDataSorted` | `Map getDataSorted()` | Returns: new map containing stored data entries only, sorted by key. Keys and values follow the stored data schema above; nested values are not copied here. |
| `getDataValue` | `String getDataValue(String name)` | Return one supported hub data value by name. Parameters: name - data key; supported dynamic keys are localIP, localSrvPortTCP, and zigbeeEui Returns: value as String, or null for a |
| `getFirmwareVersionString` | `String getFirmwareVersionString()` | Returns: current hub firmware build version |
| `getHardwareID` | `String getHardwareID()` | Returns: stored hardware id, normally 000D |
| `getLocalIP` | `String getLocalIP()` | Returns: hub IP address on the local network |
| `getLocalSrvPortTCP` | `String getLocalSrvPortTCP()` | Returns: hub TCP listening port formatted as a String |
| `getType` | `String getType()` | Returns: the fixed hub type PHYSICAL |
| `getUptime` | `BigInteger getUptime()` | Returns: hub uptime in whole seconds |
| `getZigbeeEui` | `String getZigbeeEui()` | Returns: the value returned by getZigbeeId() |
| `getZigbeeId` | `String getZigbeeId()` | Returns: Zigbee EUI identifier, or null when network information is unavailable |
| `isBatteryInUse` | `Boolean isBatteryInUse()` | Returns: false because the hub uses mains power |
| `toMap` | `Map toMap()` | Convert the hub to its API map. Returns: map with these keys: id: Long hub id; nullable. name: String hub name; initialized to an empty String and may be null. version: Long hub ve |
| `toString` | `String toString()` | Returns: hub name |
| `updateSystemTime` | `boolean updateSystemTime(Date date)` | Ask the hub runtime to update its system time. Parameters: date - new system date and time Returns: true when the system time update succeeds |

## Properties

| Name | Type | Description |
|---|---|---|
| `createTime` | `Date` | — |
| `data` | `Map<String, Object>` | Stored hub data map: Keys are String data names. Values are normally String data values; null and serializable nested values are also supported. Nested maps have application-define |
| `id` | `Long` | — |
| `lastActivityTime` | `Date` | — |
| `locationId` | `Long` | — |
| `name` | `String` | — |
| `updateTime` | `Date` | — |
| `version` | `Long` | — |
