# ThermostatFanModeReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev3-thermostatfanmodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev2.ThermostatFanModeReport`
- [ThermostatFanModeReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodereport.md)
- [ThermostatFanModeReport](../protocols/api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodereport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ThermostatFanModeReport` | `ThermostatFanModeReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeReport` | `ThermostatFanModeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanModeReport` | `ThermostatFanModeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_MODE_CIRCULATION` | `Short` | — |
| `FAN_MODE_HUMIDITY` | `Short` | — |
