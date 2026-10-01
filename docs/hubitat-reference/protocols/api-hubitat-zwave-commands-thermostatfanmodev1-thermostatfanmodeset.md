# ThermostatFanModeSet

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getFanMode` | `Short getFanMode()` | Returns the fan mode value stored in bits 0 through 3 of properties1. Returns: fan mode value |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setFanMode` | `void setFanMode(Short value)` | Encodes fan mode in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - fan mode value to encode |
| `ThermostatFanModeSet` | `ThermostatFanModeSet()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSet` | `ThermostatFanModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_MODE_AUTO_HIGH` | `short` | — |
| `FAN_MODE_AUTO_LOW` | `short` | — |
| `FAN_MODE_HIGH` | `short` | — |
| `FAN_MODE_LOW` | `short` | — |
