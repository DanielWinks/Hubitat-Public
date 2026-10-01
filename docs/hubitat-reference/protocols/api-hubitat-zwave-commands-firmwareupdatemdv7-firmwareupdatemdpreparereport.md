# FirmwareUpdateMdPrepareReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdpreparereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdPrepareReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdPrepareReport` | `FirmwareUpdateMdPrepareReport()` | Creates an empty report. |
| `FirmwareUpdateMdPrepareReport` | `FirmwareUpdateMdPrepareReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `FirmwareUpdateMdPrepareReport` | `FirmwareUpdateMdPrepareReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `firmwareChecksum` | `Integer` | — |
| `status` | `Short` | — |
