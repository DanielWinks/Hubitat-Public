# HrvStatusReport

- **ID:** `api-hubitat-zwave-commands-hrvstatusv1-hrvstatusreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.hrvstatusv1.HrvStatusReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `HrvStatusReport` | `HrvStatusReport()` | Creates the command with its declared field defaults. |
| `HrvStatusReport` | `HrvStatusReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `precision` | `Short` | — |
| `scale` | `Short` | — |
| `scaledValue` | `BigDecimal` | — |
| `size` | `Short` | — |
| `STATUS_PARAMETER_DISCHARGE_AIR_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_EXHAUST_AIR_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_OUTDOOR_AIR_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_RELATIVE_HUMIDITY_IN_ROOM` | `Short` | — |
| `STATUS_PARAMETER_REMAINING_FILTER_LIFE` | `Short` | — |
| `STATUS_PARAMETER_ROOM_TEMPERATURE` | `Short` | — |
| `STATUS_PARAMETER_SUPPLY_AIR_TEMPERATURE` | `Short` | — |
| `statusParameter` | `Short` | — |
| `value` | `List<Short>` | — |
