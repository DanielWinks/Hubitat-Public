# ThermostatSetpointSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointSupportedReport`
- [ThermostatSetpointSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatsetpointv1-thermostatsetpointsupportedreport.md)
- [ThermostatSetpointSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatsetpointv1-thermostatsetpointsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAwayHeating` | `Boolean getAwayHeating()` | Reports whether bit 1 of backing value is set for away heating. Returns: away heating value |
| `getEnergySaveCooling` | `Boolean getEnergySaveCooling()` | Reports whether bit 0 of backing value is set for energy save cooling. Returns: energy save cooling value |
| `getEnergySaveHeating` | `Boolean getEnergySaveHeating()` | Reports whether bit 7 of backing value is set for energy save heating. Returns: energy save heating value |
| `setAwayHeating` | `void setAwayHeating(Boolean value)` | Writes the away heating value to payload position 1. Parameters: value - away heating value to encode |
| `setEnergySaveCooling` | `void setEnergySaveCooling(Boolean value)` | Writes the energy save cooling value to payload position 1. Parameters: value - energy save cooling value to encode |
| `setEnergySaveHeating` | `void setEnergySaveHeating(Boolean value)` | Writes the energy save heating value to payload position 0. Parameters: value - energy save heating value to encode |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
