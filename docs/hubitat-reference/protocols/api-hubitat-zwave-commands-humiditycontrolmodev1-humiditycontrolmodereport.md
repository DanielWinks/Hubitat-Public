# HumidityControlModeReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolmodev1-humiditycontrolmodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolmodev1.HumidityControlModeReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMode` | `Short getMode()` | Returns the mode value stored in bits 0 through 3 of properties1. Returns: mode value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `HumidityControlModeReport` | `HumidityControlModeReport()` | Creates the command with its declared field defaults. |
| `HumidityControlModeReport` | `HumidityControlModeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setMode` | `Short setMode(Short value)` | Encodes the mode in the low four bits of properties1 and preserves its high four bits. Parameters: value - mode value whose low four bits are stored. Returns: updated properties1 b |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `MODE_DEHUMIDIFY` | `short` | — |
| `MODE_HUMIDIFY` | `short` | — |
| `MODE_OFF` | `short` | — |
