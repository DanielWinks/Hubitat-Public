# ThermostatFanModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev2.ThermostatFanModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeSupportedReport`
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodesupportedreport.md)
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAutoMedium` | `Boolean getAutoMedium()` | Reports whether bit 4 of backing value is set for auto medium. Returns: auto medium value |
| `getMedium` | `Boolean getMedium()` | Reports whether bit 5 of backing value is set for medium. Returns: medium value |
| `setAutoMedium` | `void setAutoMedium(Boolean value)` | Writes the auto medium value to payload position 0. Parameters: value - auto medium value to encode |
| `setMedium` | `void setMedium(Boolean value)` | Writes the medium value to payload position 0. Parameters: value - medium value to encode |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
