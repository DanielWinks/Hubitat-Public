# FirmwareUpdateMdGet

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdGet` | `FirmwareUpdateMdGet()` | Creates an empty command. |
| `FirmwareUpdateMdGet` | `FirmwareUpdateMdGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `numberOfReports` | `Short` | — |
| `reportNumber` | `Integer` | — |
