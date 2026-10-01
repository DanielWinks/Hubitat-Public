# FirmwareUpdateMdRequestGet

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdrequestget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdRequestGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdRequestGet` | `FirmwareUpdateMdRequestGet()` | Creates an empty command. |
| `FirmwareUpdateMdRequestGet` | `FirmwareUpdateMdRequestGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getJSON` | `String getJSON()` | Returns: Z-Wave JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `activation` | `Boolean` | — |
| `checksum` | `Integer` | — |
| `firmwareId` | `Integer` | — |
| `firmwareTarget` | `Short` | — |
| `fragmentSize` | `Integer` | — |
| `hardwareVersion` | `Short` | — |
| `manufacturerId` | `Integer` | — |
