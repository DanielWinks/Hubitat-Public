# FirmwareUpdateMdRequestGet

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv8-firmwareupdatemdrequestget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv8.FirmwareUpdateMdRequestGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.firmwareupdatemdv7.FirmwareUpdateMdRequestGet`
- [FirmwareUpdateMdRequestGet](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdrequestget.md)
- [FirmwareUpdateMdRequestGet](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv7-firmwareupdatemdrequestget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareUpdateMdRequestGet` | `FirmwareUpdateMdRequestGet()` | Creates an empty command. |
| `FirmwareUpdateMdRequestGet` | `FirmwareUpdateMdRequestGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getJSON` | `String getJSON()` | Returns: Z-Wave JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `nonSecure` | `Boolean` | — |
| `resume` | `Boolean` | — |
