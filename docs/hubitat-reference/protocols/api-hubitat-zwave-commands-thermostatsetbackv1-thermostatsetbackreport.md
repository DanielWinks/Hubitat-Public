# ThermostatSetbackReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetbackv1-thermostatsetbackreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetbackv1.ThermostatSetbackReport`

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
| `ThermostatSetbackReport` | `ThermostatSetbackReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetbackReport` | `ThermostatSetbackReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `SETBACK_TYPE_NO_OVERRIDE` | `Short` | — |
| `SETBACK_TYPE_PERMANENT_OVERRIDE` | `Short` | — |
| `SETBACK_TYPE_RESERVED3` | `Short` | — |
| `SETBACK_TYPE_TEMPORARY_OVERRIDE` | `Short` | — |
| `setbackState` | `Short` | — |
| `setbackType` | `Short` | — |
