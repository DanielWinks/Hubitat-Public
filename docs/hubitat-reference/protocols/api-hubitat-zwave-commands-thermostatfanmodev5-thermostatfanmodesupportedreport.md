# ThermostatFanModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev5-thermostatfanmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev5.ThermostatFanModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev4.ThermostatFanModeSupportedReport`
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev4-thermostatfanmodesupportedreport.md)
- [ThermostatFanModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev4-thermostatfanmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getExternalCirculation` | `Boolean getExternalCirculation()` | Reports whether bit 3 of backing value is set for external circulation. Returns: external circulation value |
| `setExternalCirculation` | `void setExternalCirculation(Boolean value)` | Writes the external circulation value to payload position 1. Parameters: value - external circulation value to encode |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
