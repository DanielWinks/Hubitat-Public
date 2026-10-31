# ThermostatModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatmodev2-thermostatmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatmodev1.ThermostatModeSupportedReport`
- [ThermostatModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatmodev1-thermostatmodesupportedreport.md)
- [ThermostatModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatmodev1-thermostatmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAway` | `Boolean getAway()` | Reports whether bit 5 of backing value is set for away. Returns: away value |
| `getEnergySaveCool` | `Boolean getEnergySaveCool()` | Reports whether bit 4 of backing value is set for energy save cool. Returns: energy save cool value |
| `getEnergySaveHeat` | `Boolean getEnergySaveHeat()` | Reports whether bit 3 of backing value is set for energy save heat. Returns: energy save heat value |
| `setAway` | `void setAway(Boolean value)` | Writes the away value to payload position 1. Parameters: value - away value to encode |
| `setEnergySaveCool` | `void setEnergySaveCool(Boolean value)` | Writes the energy save cool value to payload position 1. Parameters: value - energy save cool value to encode |
| `setEnergySaveHeat` | `void setEnergySaveHeat(Boolean value)` | Writes the energy save heat value to payload position 1. Parameters: value - energy save heat value to encode |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
