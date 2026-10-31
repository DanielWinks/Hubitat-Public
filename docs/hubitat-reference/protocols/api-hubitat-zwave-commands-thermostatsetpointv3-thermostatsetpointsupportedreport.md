# ThermostatSetpointSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv3-thermostatsetpointsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointSupportedReport`
- [ThermostatSetpointSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointsupportedreport.md)
- [ThermostatSetpointSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAwayCooling` | `Boolean getAwayCooling()` | Reports whether bit 2 of backing value is set for away cooling. Returns: away cooling value |
| `getFullPower` | `Boolean getFullPower()` | Reports whether bit 3 of backing value is set for full power. Returns: full power value |
| `setAwayCooling` | `void setAwayCooling(Boolean value)` | Writes the away cooling value to payload position 1. Parameters: value - away cooling value to encode |
| `setFullPower` | `void setFullPower(Boolean value)` | Writes the full power value to payload position 1. Parameters: value - full power value to encode |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
