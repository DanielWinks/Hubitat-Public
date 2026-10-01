# FirmwareMdReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv8-firmwaremdreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv8.FirmwareMdReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareMdReport`
- [FirmwareMdReport](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv7-firmwaremdreport.md)
- [FirmwareMdReport](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv7-firmwaremdreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareMdReport` | `FirmwareMdReport()` | Creates an empty report. |
| `FirmwareMdReport` | `FirmwareMdReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `FirmwareMdReport` | `FirmwareMdReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `nonSecure` | `Boolean` | — |
| `resume` | `Boolean` | — |
