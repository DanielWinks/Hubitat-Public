# Security2MessageEncapsulation

- **ID:** `api-hubitat-zwave-commands-security2v1-security2messageencapsulation`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.security2v1.Security2MessageEncapsulation`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `Security2MessageEncapsulation` | `Security2MessageEncapsulation()` | Creates the command with its declared field defaults. |
| `Security2MessageEncapsulation` | `Security2MessageEncapsulation(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `encryptedExtension` | `Boolean` | — |
| `encryptedExtensions` | `List<Map>` | — |
| `extension` | `Boolean` | — |
| `extensions` | `List<Map>` | — |
| `sequenceNumber` | `Short` | — |
