# ThermostatSetpointReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv3-thermostatsetpointreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport`
- [ThermostatSetpointReport](../protocols/api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointreport.md)
- [ThermostatSetpointReport](../protocols/api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ThermostatSetpointReport` | `ThermostatSetpointReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointReport` | `ThermostatSetpointReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatSetpointReport` | `ThermostatSetpointReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `SETPOINT_TYPE_AWAY_COOLING` | `short` | — |
| `SETPOINT_TYPE_FULL_POWER` | `short` | — |
