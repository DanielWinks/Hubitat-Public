# ThermostatFanModeReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev2.ThermostatFanModeReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport`
- [ThermostatFanModeReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodereport.md)
- [ThermostatFanModeReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodereport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getOff` | `Boolean getOff()` | Reports whether bit 7 of properties1 is set for off. Returns: off value |
| `setOff` | `void setOff(Boolean value)` | Sets the off value used by this command. Parameters: value - off value to encode |
| `ThermostatFanModeReport` | `ThermostatFanModeReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeReport` | `ThermostatFanModeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeReport` | `ThermostatFanModeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_MODE_AUTO_MEDIUM` | `Short` | — |
| `FAN_MODE_MEDIUM` | `Short` | — |
