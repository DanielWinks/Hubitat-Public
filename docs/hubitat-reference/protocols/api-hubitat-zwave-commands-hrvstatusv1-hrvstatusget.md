# HrvStatusGet

- **ID:** `api-hubitat-zwave-commands-hrvstatusv1-hrvstatusget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.hrvstatusv1.HrvStatusGet`

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
| `HrvStatusGet` | `HrvStatusGet()` | Creates the command with its declared field defaults. |
| `HrvStatusGet` | `HrvStatusGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `STATUS_PARAMETER_DISCHARGE_AIR_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_EXHAUST_AIR_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_OUTDOOR_AIR_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_RELATIVE_HUMIDITY_IN_ROOM` | `Short` | — |
| `STATUS_PARAMETER_REMAINING_FILTER_LIFE` | `Short` | — |
| `STATUS_PARAMETER_ROOM_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_SUPPLY_AIR_TEMPERATURE` | `Short` | — |
| `statusParameter` | `Short` | — |
