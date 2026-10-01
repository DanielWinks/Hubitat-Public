# ThermostatFanModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAuto` | `Boolean getAuto()` | Reports whether bit 0 of backing value is set for auto. Returns: auto value |
| `getAutoHigh` | `Boolean getAutoHigh()` | Reports whether bit 2 of backing value is set for auto high. Returns: auto high value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getHigh` | `Boolean getHigh()` | Reports whether bit 3 of backing value is set for high. Returns: high value |
| `getLow` | `Boolean getLow()` | Reports whether bit 1 of backing value is set for low. Returns: low value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setAuto` | `void setAuto(Boolean value)` | Writes the auto value to payload position 0. Parameters: value - auto value to encode |
| `setAutoHigh` | `void setAutoHigh(Boolean value)` | Writes the auto high value to payload position 0. Parameters: value - auto high value to encode |
| `setHigh` | `void setHigh(Boolean value)` | Writes the high value to payload position 0. Parameters: value - high value to encode |
| `setLow` | `void setLow(Boolean value)` | Writes the low value to payload position 0. Parameters: value - low value to encode |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeSupportedReport` | `ThermostatFanModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
