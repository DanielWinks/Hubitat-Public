# HumidityControlSetpointGet

- **ID:** `api-hubitat-zwave-commands-humiditycontrolsetpointv1-humiditycontrolsetpointget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolsetpointv1.HumidityControlSetpointGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSetpointType` | `Short getSetpointType()` | Returns the setpoint type value stored in bits 0 through 3 of properties1. Returns: setpoint type value |
| `HumidityControlSetpointGet` | `HumidityControlSetpointGet()` | Creates the command with its declared field defaults. |
| `HumidityControlSetpointGet` | `HumidityControlSetpointGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setSetpointType` | `void setSetpointType(Short value)` | Encodes setpoint type in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - setpoint type value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `SETPOINT_TYPE_DEHUMIDIFIER` | `short` | — |
| `SETPOINT_TYPE_HUMIDIFIER` | `short` | — |
