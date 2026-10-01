# Parent device wrapper

- **ID:** `api-com-hubitat-app-parentdevicewrapper`
- **Section:** Shared APIs
- **Class:** `com.hubitat.app.ParentDeviceWrapper`

> Use ParentDeviceWrapper from app or driver code when you need app or driver APIs.

## Inheritance

- Extends `com.hubitat.app.DeviceWrapper`
- [Device wrapper](../shared/api-com-hubitat-app-devicewrapper.md)
- [Device wrapper](../shared/api-com-hubitat-app-devicewrapper.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ParentDeviceWrapper` | `ParentDeviceWrapper(Device device)` | Wrap an already loaded parent device. throws: IllegalArgumentException when device is null Parameters: device - parent device to wrap; null is rejected by the base wrapper |
