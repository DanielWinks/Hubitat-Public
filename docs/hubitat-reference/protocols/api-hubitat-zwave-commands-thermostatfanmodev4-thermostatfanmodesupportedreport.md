# ThermostatFanModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev4-thermostatfanmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev4.ThermostatFanModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeSupportedReport`
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev3-thermostatfanmodesupportedreport.md)
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev3-thermostatfanmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getLeftRight` | `Boolean getLeftRight()` | Reports whether bit 0 of backing value is set for left right. Returns: left right value |
| `getQuiet` | `Boolean getQuiet()` | Reports whether bit 2 of backing value is set for quiet. Returns: quiet value |
| `getUpDown` | `Boolean getUpDown()` | Reports whether bit 1 of backing value is set for up down. Returns: up down value |
| `setLeftRight` | `void setLeftRight(Boolean value)` | Writes the left right value to payload position 1. Parameters: value - left right value to encode |
| `setQuiet` | `void setQuiet(Boolean value)` | Writes the quiet value to payload position 1. Parameters: value - quiet value to encode |
| `setUpDown` | `void setUpDown(Boolean value)` | Writes the up down value to payload position 1. Parameters: value - up down value to encode |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
