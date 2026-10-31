# ThermostatSetpointGet

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv3-thermostatsetpointget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointGet`
- [ThermostatSetpointGet](../protocols/api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointget.md)
- [ThermostatSetpointGet](../protocols/api-hubitat-zwave-commands-thermostatsetpointv2-thermostatsetpointget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ThermostatSetpointGet` | `ThermostatSetpointGet()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointGet` | `ThermostatSetpointGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `SETPOINT_TYPE_AWAY_COOLING` | `short` | — |
| `SETPOINT_TYPE_FULL_POWER` | `short` | — |
