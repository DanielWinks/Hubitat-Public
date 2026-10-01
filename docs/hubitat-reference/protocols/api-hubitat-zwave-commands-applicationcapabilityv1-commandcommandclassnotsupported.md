# CommandCommandClassNotSupported

- **ID:** `api-hubitat-zwave-commands-applicationcapabilityv1-commandcommandclassnotsupported`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.applicationcapabilityv1.CommandCommandClassNotSupported`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CommandCommandClassNotSupported` | `CommandCommandClassNotSupported()` | Creates the command with its declared field defaults. |
| `CommandCommandClassNotSupported` | `CommandCommandClassNotSupported(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `dynamic` | `Boolean` | — |
| `offendingCommand` | `Short` | — |
| `offendingCommandClass` | `Short` | — |
