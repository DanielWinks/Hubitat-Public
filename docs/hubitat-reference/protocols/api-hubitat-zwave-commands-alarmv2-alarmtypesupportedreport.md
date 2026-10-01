# AlarmTypeSupportedReport

- **ID:** `api-hubitat-zwave-commands-alarmv2-alarmtypesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.alarmv2.AlarmTypeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AlarmTypeSupportedReport` | `AlarmTypeSupportedReport()` | Creates the command with its declared field defaults. |
| `AlarmTypeSupportedReport` | `AlarmTypeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `accessControl` | `Boolean` | — |
| `burglar` | `Boolean` | — |
| `clock` | `Boolean` | — |
| `co` | `Boolean` | — |
| `co2` | `Boolean` | — |
| `emergency` | `Boolean` | — |
| `heat` | `Boolean` | — |
| `numberOfBitMasks` | `Short` | — |
| `powerManagement` | `Boolean` | — |
| `smoke` | `Boolean` | — |
| `system` | `Boolean` | — |
| `v1Alarm` | `Boolean` | — |
| `water` | `Boolean` | — |
