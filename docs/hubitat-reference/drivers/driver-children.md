# Child devices

- **ID:** `driver-children`
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
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String typeName, String deviceNetworkId, Map properties = [:])` | Creates a child device using the current driver's namespace. Parameters: typeName - Child driver name. deviceNetworkId - Child network id; a UUID is generated when null or empty. p |
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String namespace, String typeName, String deviceNetworkId, Map properties = [:])` | Creates a child device using the requested namespace and driver. Parameters: namespace - Child driver's namespace. typeName - Child driver name. deviceNetworkId - Child network id; |
| `assignDevicesRoomToChildDevices` | `void assignDevicesRoomToChildDevices()` | Placeholder for assigning this device's room to its child devices; currently performs no operation. |
| `deleteAllCurrentStates` | `void deleteAllCurrentStates()` | Deletes all current attribute states for this device. |
| `deleteChildDevice` | `void deleteChildDevice(String deviceNetworkId)` | Deletes a child device by its network id. Parameters: deviceNetworkId - Child device network id. |
| `getChildDevice` | `ChildDeviceWrapper getChildDevice(String deviceNetworkId)` | Looks up a child device by its network id. Parameters: deviceNetworkId - Child device network id. Returns: Matching child wrapper, or null when no child matches. |
| `getChildDevices` | `List<ChildDeviceWrapper> getChildDevices()` | Returns child devices created by this composite driver. Returns: Child device wrappers for this device's direct children. |
