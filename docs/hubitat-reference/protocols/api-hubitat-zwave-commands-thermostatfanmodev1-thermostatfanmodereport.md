# ThermostatFanModeReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport`

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
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setFanMode` | `void setFanMode(Short value)` | Encodes fan mode in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - fan mode value to encode |
| `ThermostatFanModeReport` | `ThermostatFanModeReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeReport` | `ThermostatFanModeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeReport` | `ThermostatFanModeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_MODE_AUTO_HIGH` | `Short` | — |
| `FAN_MODE_AUTO_LOW` | `Short` | — |
| `FAN_MODE_HIGH` | `Short` | — |
| `FAN_MODE_LOW` | `Short` | — |
