# FirmwareUpdateMdRequestReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdrequestreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdRequestReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdRequestReport` | `FirmwareUpdateMdRequestReport()` | Creates an empty report. |
| `FirmwareUpdateMdRequestReport` | `FirmwareUpdateMdRequestReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `FirmwareUpdateMdRequestReport` | `FirmwareUpdateMdRequestReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `status` | `Short` | — |
