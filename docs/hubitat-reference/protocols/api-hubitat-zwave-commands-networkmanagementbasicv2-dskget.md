# DSKGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementbasicv2-dskget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementbasicv2.DSKGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DSKGet` | `DSKGet()` | Creates the command with its declared field defaults. |
| `DSKGet` | `DSKGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `addMode` | `Boolean` | — |
| `seqNo` | `Short` | — |
