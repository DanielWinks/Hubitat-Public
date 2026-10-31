# SensorBinaryGet

- **ID:** `api-hubitat-zwave-commands-sensorbinaryv2-sensorbinaryget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensorbinaryv2.SensorBinaryGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.sensorbinaryv1.SensorBinaryGet`
- [SensorBinaryGet](../protocols/api-hubitat-zwave-commands-sensorbinaryv1-sensorbinaryget.md)
- [SensorBinaryGet](../protocols/api-hubitat-zwave-commands-sensorbinaryv1-sensorbinaryget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SensorBinaryGet` | `SensorBinaryGet()` | Creates the command with its declared field defaults. |
| `SensorBinaryGet` | `SensorBinaryGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `sensorType` | `Short` | — |
