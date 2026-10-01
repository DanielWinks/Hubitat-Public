# FirmwareUpdateMdRequestReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv8-firmwareupdatemdrequestreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv8.FirmwareUpdateMdRequestReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdRequestReport`
- [FirmwareUpdateMdRequestReport](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdrequestreport.md)
- [FirmwareUpdateMdRequestReport](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdrequestreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdRequestReport` | `FirmwareUpdateMdRequestReport()` | Creates an empty report. |
| `FirmwareUpdateMdRequestReport` | `FirmwareUpdateMdRequestReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `FirmwareUpdateMdRequestReport` | `FirmwareUpdateMdRequestReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `nonSecure` | `Boolean` | — |
| `resume` | `Boolean` | — |
