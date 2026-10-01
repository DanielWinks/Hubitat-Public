# DeviceSpecificGet

- **ID:** `api-hubitat-zwave-commands-manufacturerspecificv2-devicespecificget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DeviceSpecificGet` | `DeviceSpecificGet()` | Creates the command with its declared field defaults. |
| `DeviceSpecificGet` | `DeviceSpecificGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `DEVICE_ID_TYPE_FACTORY_DEFAULT` | `Short` | — |
| `DEVICE_ID_TYPE_PSEUDO_RANDOM` | `Short` | — |
| `DEVICE_ID_TYPE_SERIAL_NUMBER` | `Short` | — |
| `deviceIdType` | `Short` | — |
