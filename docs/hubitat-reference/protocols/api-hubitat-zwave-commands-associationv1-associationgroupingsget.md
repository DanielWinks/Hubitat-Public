# AssociationGroupingsGet

- **ID:** `api-hubitat-zwave-commands-associationv1-associationgroupingsget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationv1.AssociationGroupingsGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationGroupingsGet` | `AssociationGroupingsGet()` | Creates the command with its declared field defaults. |
| `AssociationGroupingsGet` | `AssociationGroupingsGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getJSON` | `String getJSON()` | Builds the Z-Wave JS command invocation for this command. Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
