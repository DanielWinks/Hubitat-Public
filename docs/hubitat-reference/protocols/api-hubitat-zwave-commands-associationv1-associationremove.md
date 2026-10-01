# AssociationRemove

- **ID:** `api-hubitat-zwave-commands-associationv1-associationremove`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationv1.AssociationRemove`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationRemove` | `AssociationRemove()` | Creates the command with its declared field defaults. |
| `AssociationRemove` | `AssociationRemove(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `groupingIdentifier` | `Short` | — |
| `nodeId` | `Object` | Node identifier value or values used by this command. A single value is cast to Short; a List is serialized by casting each item to Short. The association commands omit falsey valu |
