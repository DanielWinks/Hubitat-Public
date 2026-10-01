# HumidityControlModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolmodev2-humiditycontrolmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolmodev2.HumidityControlModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.humiditycontrolmodev1.HumidityControlModeSupportedReport`
- [HumidityControlModeSupportedReport](../protocols/api-hubitat-zwave-commands-humiditycontrolmodev1-humiditycontrolmodesupportedreport.md)
- [HumidityControlModeSupportedReport](../protocols/api-hubitat-zwave-commands-humiditycontrolmodev1-humiditycontrolmodesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getModeAuto` | `Boolean getModeAuto()` | Reports whether bit 3 of bitmask is set for mode auto. Returns: mode auto value |
| `HumidityControlModeSupportedReport` | `HumidityControlModeSupportedReport()` | Creates the command with its declared field defaults. |
| `HumidityControlModeSupportedReport` | `HumidityControlModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setModeAuto` | `void setModeAuto(Boolean value)` | Sets the mode auto value used by this command. Parameters: value - mode auto value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
