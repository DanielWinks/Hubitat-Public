# Child device wrapper

- **ID:** `api-com-hubitat-app-childdevicewrapper`
- **Section:** Shared APIs
- **Class:** `com.hubitat.app.ChildDeviceWrapper`

> Use ChildDeviceWrapper from app or driver code when you need app or driver APIs.

## Inheritance

- Extends `com.hubitat.app.DeviceWrapper`
- [Device wrapper](../shared/api-com-hubitat-app-devicewrapper.md)
- [Device wrapper](../shared/api-com-hubitat-app-devicewrapper.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ChildDeviceWrapper` | `ChildDeviceWrapper(Device device)` | Wrap an already loaded child device. throws: IllegalArgumentException when device is null Parameters: device - child device to wrap; null is rejected by the base wrapper |
