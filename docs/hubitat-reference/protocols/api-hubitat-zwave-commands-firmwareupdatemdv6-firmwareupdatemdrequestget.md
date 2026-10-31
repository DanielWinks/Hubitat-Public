# FirmwareUpdateMdRequestGet

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv6-firmwareupdatemdrequestget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv6.FirmwareUpdateMdRequestGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.firmwareupdatemdv5.FirmwareUpdateMdRequestGet`
- [FirmwareUpdateMdRequestGet](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv5-firmwareupdatemdrequestget.md)
- [FirmwareUpdateMdRequestGet](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv5-firmwareupdatemdrequestget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdRequestGet` | `FirmwareUpdateMdRequestGet()` | Creates the command with its declared field defaults. |
| `FirmwareUpdateMdRequestGet` | `FirmwareUpdateMdRequestGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 9 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

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
