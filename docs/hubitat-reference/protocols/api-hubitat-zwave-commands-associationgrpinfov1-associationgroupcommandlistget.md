# AssociationGroupCommandListGet

- **ID:** `api-hubitat-zwave-commands-associationgrpinfov1-associationgroupcommandlistget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationgrpinfov1.AssociationGroupCommandListGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationGroupCommandListGet` | `AssociationGroupCommandListGet()` | Creates the command with its declared field defaults. |
| `AssociationGroupCommandListGet` | `AssociationGroupCommandListGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Builds the Z-Wave JS command invocation for this command. Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `allowCache` | `Boolean` | — |
| `groupingIdentifier` | `Short` | — |
