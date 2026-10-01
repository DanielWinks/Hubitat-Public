# ThermostatModeReport

- **ID:** `api-hubitat-zwave-commands-thermostatmodev1-thermostatmodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMode` | `Short getMode()` | Returns the mode value stored in bits 0 through 4 of properties1. Returns: mode value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setMode` | `void setMode(Short value)` | Encodes mode in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - mode value to encode |
| `ThermostatModeReport` | `ThermostatModeReport()` | Creates the command with its declared field defaults. |
| `ThermostatModeReport` | `ThermostatModeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatModeReport` | `ThermostatModeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `MODE_AUTO` | `short` | — |
| `MODE_AUTO_CHANGEOVER` | `short` | — |
| `MODE_AUXILIARY_HEAT` | `short` | — |
| `MODE_COOL` | `short` | — |
| `MODE_DRY_AIR` | `short` | — |
| `MODE_FAN_ONLY` | `short` | — |
| `MODE_FURNACE` | `short` | — |
| `MODE_HEAT` | `short` | — |
| `MODE_MOIST_AIR` | `short` | — |
| `MODE_OFF` | `short` | — |
| `MODE_RESUME` | `short` | — |
