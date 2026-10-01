# ThermostatFanStateReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanstatev2-thermostatfanstatereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanstatev2.ThermostatFanStateReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport`
- [ThermostatFanStateReport](../protocols/api-hubitat-zwave-commands-thermostatfanstatev1-thermostatfanstatereport.md)
- [ThermostatFanStateReport](../protocols/api-hubitat-zwave-commands-thermostatfanstatev1-thermostatfanstatereport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ThermostatFanStateReport` | `ThermostatFanStateReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanStateReport` | `ThermostatFanStateReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanStateReport` | `ThermostatFanStateReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_OPERATING_STATE_CIRCULATION_MODE` | `short` | — |
| `FAN_OPERATING_STATE_HUMIDITY_CIRCULATION_MODE` | `short` | — |
| `FAN_OPERATING_STATE_QUIET_CIRCULATION_MODE` | `short` | — |
| `FAN_OPERATING_STATE_RIGHT_LEFT_CIRCULATION_MODE` | `short` | — |
| `FAN_OPERATING_STATE_RUNNING_MEDIUM` | `short` | — |
| `FAN_OPERATING_STATE_UP_DOWN_CIRCULATION_MODE` | `short` | — |
