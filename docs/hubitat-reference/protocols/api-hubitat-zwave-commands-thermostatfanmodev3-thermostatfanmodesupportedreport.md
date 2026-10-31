# ThermostatFanModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev3-thermostatfanmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev2.ThermostatFanModeSupportedReport`
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodesupportedreport.md)
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCirculation` | `Boolean getCirculation()` | Reports whether bit 6 of backing value is set for circulation. Returns: circulation value |
| `getHumidityCirculation` | `Boolean getHumidityCirculation()` | Reports whether bit 7 of backing value is set for humidity circulation. Returns: humidity circulation value |
| `setCirculation` | `void setCirculation(Boolean value)` | Writes the circulation value to payload position 0. Parameters: value - circulation value to encode |
| `setHumidityCirculation` | `void setHumidityCirculation(Boolean value)` | Writes the humidity circulation value to payload position 0. Parameters: value - humidity circulation value to encode |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
