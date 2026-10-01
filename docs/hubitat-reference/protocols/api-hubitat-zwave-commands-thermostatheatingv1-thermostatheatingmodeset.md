# ThermostatHeatingModeSet

- **ID:** `api-hubitat-zwave-commands-thermostatheatingv1-thermostatheatingmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatheatingv1.ThermostatHeatingModeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ThermostatHeatingModeSet` | `ThermostatHeatingModeSet()` | Creates the command with its declared field defaults. |
| `ThermostatHeatingModeSet` | `ThermostatHeatingModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `mode` | `Short` | — |
| `MODE_ANTI_FREEZE` | `Short` | — |
| `MODE_AUTOMATIC` | `Short` | — |
| `MODE_MANUAL` | `Short` | — |
| `MODE_MANUAL_TIMED` | `Short` | — |
| `MODE_OFF` | `Short` | — |
| `MODE_OFF_3_HOURS` | `Short` | — |
| `MODE_OFF_TIMED` | `Short` | — |
| `MODE_TEMPORARY_MANUAL` | `Short` | — |
