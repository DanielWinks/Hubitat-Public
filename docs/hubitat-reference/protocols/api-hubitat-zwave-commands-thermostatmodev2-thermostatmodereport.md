# ThermostatModeReport

- **ID:** `api-hubitat-zwave-commands-thermostatmodev2-thermostatmodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport`
- [ThermostatModeReport](../protocols/api-hubitat-zwave-commands-thermostatmodev1-thermostatmodereport.md)
- [ThermostatModeReport](../protocols/api-hubitat-zwave-commands-thermostatmodev1-thermostatmodereport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ThermostatModeReport` | `ThermostatModeReport()` | Creates the command with its declared field defaults. |
| `ThermostatModeReport` | `ThermostatModeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatModeReport` | `ThermostatModeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `MODE_AWAY` | `short` | — |
| `MODE_ENERGY_SAVE_COOL` | `short` | — |
| `MODE_ENERGY_SAVE_HEAT` | `short` | — |
