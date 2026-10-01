# ThermostatFanModeSet

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev4-thermostatfanmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev4.ThermostatFanModeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeSet`
- [ThermostatFanModeSet](../protocols/api-hubitat-zwave-commands-thermostatfanmodev3-thermostatfanmodeset.md)
- [ThermostatFanModeSet](../protocols/api-hubitat-zwave-commands-thermostatfanmodev3-thermostatfanmodeset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ThermostatFanModeSet` | `ThermostatFanModeSet()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSet` | `ThermostatFanModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_MODE_LEFT_RIGHT` | `short` | — |
| `FAN_MODE_QUIET` | `short` | — |
| `FAN_MODE_UP_DOWN` | `short` | — |
