# App device access

- **ID:** `app-device-access`
- **Section:** Apps
- **Class:** `com.hubitat.hub.executor.AppExecutor`

> Use these methods directly in app code; the hub supplies this execution context.

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
| `getDeviceListByAttribute` | `List getDeviceListByAttribute()` | Returns the UI device list filtered by this executor’s attributeName context. Returns: the List returned by DeviceDao2 for this executor’s attributeName context. |
| `getDeviceListByAttribute2` | `List getDeviceListByAttribute2()` | Returns the device list filtered by this executor’s attributeName context. Returns: the List returned by DeviceDao2 for this executor’s attributeName context. |
| `getDevicesByIds` | `List<DeviceWrapper> getDevicesByIds(List<Long> deviceIds)` | Loads and wraps the devices with the supplied ids, omitting ids that do not resolve to a device. Parameters: deviceIds - device ids Returns: a List for ids that resolve to devices; |
| `getInstalledCapabilities` | `List getInstalledCapabilities()` | Returns the capabilities installed on the hub. Returns: the List of capability records returned by DeviceDao2. |
