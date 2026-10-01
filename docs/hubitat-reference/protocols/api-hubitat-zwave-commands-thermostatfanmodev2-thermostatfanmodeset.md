# ThermostatFanModeSet

- **ID:** `api-hubitat-zwave-commands-thermostatfanmodev2-thermostatfanmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanmodev2.ThermostatFanModeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeSet`
- [ThermostatFanModeSet](../protocols/api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodeset.md)
- [ThermostatFanModeSet](../protocols/api-hubitat-zwave-commands-thermostatfanmodev1-thermostatfanmodeset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getOff` | `Boolean getOff()` | Reports whether bit 7 of properties1 is set for off. Returns: off value |
| `setOff` | `void setOff(Boolean value)` | Sets the off value used by this command. Parameters: value - off value to encode |
| `ThermostatFanModeSet` | `ThermostatFanModeSet()` | Creates the command with its declared field defaults. |
| `ThermostatFanModeSet` | `ThermostatFanModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_MODE_AUTO_MEDIUM` | `short` | — |
| `FAN_MODE_MEDIUM` | `short` | — |
