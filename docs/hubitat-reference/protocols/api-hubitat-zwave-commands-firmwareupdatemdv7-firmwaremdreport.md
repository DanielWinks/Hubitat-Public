# FirmwareMdReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwaremdreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareMdReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareMdReport` | `FirmwareMdReport()` | Creates an empty report. |
| `FirmwareMdReport` | `FirmwareMdReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `FirmwareMdReport` | `FirmwareMdReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `activation` | `Boolean` | — |
| `cc` | `Boolean` | — |
| `checksum` | `Integer` | — |
| `continuesToFunction` | `Boolean` | — |
| `firmwareId` | `Integer` | — |
| `firmwareIds` | `List<Integer>` | — |
| `firmwareUpgradable` | `Boolean` | — |
| `hardwareVersion` | `Short` | — |
| `manufacturerId` | `Integer` | — |
| `maxFragmentSize` | `Integer` | — |
