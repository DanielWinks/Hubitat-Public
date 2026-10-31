# HumidityControlSetpointSupportedReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolsetpointv2-humiditycontrolsetpointsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolsetpointv2.HumidityControlSetpointSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.humiditycontrolsetpointv1.HumidityControlSetpointSupportedReport`
- [HumidityControlSetpointSupportedReport](../protocols/api-hubitat-zwave-commands-humiditycontrolsetpointv1-humiditycontrolsetpointsupportedreport.md)
- [HumidityControlSetpointSupportedReport](../protocols/api-hubitat-zwave-commands-humiditycontrolsetpointv1-humiditycontrolsetpointsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAuto` | `Boolean getAuto()` | Reports whether bit 2 of bitmask is set for auto. Returns: auto value |
| `HumidityControlSetpointSupportedReport` | `HumidityControlSetpointSupportedReport()` | Creates the command with its declared field defaults. |
| `HumidityControlSetpointSupportedReport` | `HumidityControlSetpointSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setAuto` | `void setAuto(Boolean value)` | Sets the auto value used by this command. Parameters: value - auto value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
