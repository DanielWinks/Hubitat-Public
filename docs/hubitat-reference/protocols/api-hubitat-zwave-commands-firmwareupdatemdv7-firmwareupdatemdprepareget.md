# FirmwareUpdateMdPrepareGet

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdprepareget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdPrepareGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdPrepareGet` | `FirmwareUpdateMdPrepareGet()` | Creates an empty command. |
| `FirmwareUpdateMdPrepareGet` | `FirmwareUpdateMdPrepareGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `firmwareId` | `Integer` | — |
| `firmwareTarget` | `Short` | — |
| `fragmentSize` | `Integer` | — |
| `hardwareVersion` | `Short` | — |
| `manufacturerId` | `Integer` | — |
