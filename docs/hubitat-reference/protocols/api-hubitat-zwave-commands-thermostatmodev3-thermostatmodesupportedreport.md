# ThermostatModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatmodev3-thermostatmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatmodev3.ThermostatModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport`
- [ThermostatModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatmodev2-thermostatmodesupportedreport.md)
- [ThermostatModeSupportedReport](../protocols/api-hubitat-zwave-commands-thermostatmodev2-thermostatmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getFullPower` | `Boolean getFullPower()` | Reports whether bit 7 of backing value is set for full power. Returns: full power value |
| `getManufacturerSpecific` | `Boolean getManufacturerSpecific()` | Reports whether bit 0 of backing value is set for manufacturer specific. Returns: manufacturer specific value |
| `setFullPower` | `void setFullPower(Boolean value)` | Writes the full power value to payload position 1. Parameters: value - full power value to encode |
| `setManufacturerSpecific` | `void setManufacturerSpecific(Boolean value)` | Writes the manufacturer specific value to payload position 2. Parameters: value - manufacturer specific value to encode |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
