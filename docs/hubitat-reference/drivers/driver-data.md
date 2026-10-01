# Device data

- **ID:** `driver-data`
- **Section:** Drivers
- **Class:** `com.hubitat.hub.executor.DeviceExecutor`

> Use these methods directly in driver code; the hub supplies this execution context.

## Inheritance

- Extends `com.hubitat.hub.executor.BaseExecutor`
- [HTTP requests](../shared/http.md)
- [HTTP requests](../shared/http.md)
- [Date and time](../shared/shared-date-time.md)
- [Hub files](../shared/shared-files.md)
- [Network utilities](../shared/shared-network.md)
- [Scheduling](../shared/shared-scheduling.md)
- [Shared utilities](../shared/shared-utilities.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getDataValue` | `String getDataValue(String name)` | Reads one device data value. Parameters: name - Name of the data value. Returns: Stored text value, or null when the value is absent. |
| `getDeviceDataByName` | `String getDeviceDataByName(String name)` | Reads one device data value by name. Parameters: name - Name of the data value. Returns: Stored text value, or null when the value is absent. |
| `getState` | `Map getState()` | Returns the current state map for this device. Returns: Mutable deep map of device state. Keys are state names; values are decoded JSON scalars, lists, or nested maps and may be ch |
| `removeDataValue` | `void removeDataValue(String name)` | Removes one device data value. Parameters: name - Name of the data value to remove. |
| `toString` | `String toString()` | Returns the device's display name. Returns: Current display name of the device. |
| `updateDataValue` | `void updateDataValue(String name, String value)` | Stores or replaces one device data value. Parameters: name - Name of the data value. value - Text value to store. |
