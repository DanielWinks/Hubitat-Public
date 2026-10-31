# FirmwareUpdateMdReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdReport` | `FirmwareUpdateMdReport()` | Creates an empty report. |
| `FirmwareUpdateMdReport` | `FirmwareUpdateMdReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `checksum` | `Integer` | — |
| `data` | `List<Short>` | — |
| `last` | `Boolean` | — |
| `reportNumber` | `Integer` | — |
