# FirmwareUpdateActivationStatusReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdateactivationstatusreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateActivationStatusReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateActivationStatusReport` | `FirmwareUpdateActivationStatusReport()` | Creates an empty report. |
| `FirmwareUpdateActivationStatusReport` | `FirmwareUpdateActivationStatusReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `FirmwareUpdateActivationStatusReport` | `FirmwareUpdateActivationStatusReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `checksum` | `Integer` | — |
| `firmwareId` | `Integer` | — |
| `firmwareTarget` | `Short` | — |
| `firmwareUpdateStatus` | `Short` | — |
| `hardwareVersion` | `Short` | — |
| `manufacturerId` | `Integer` | — |
