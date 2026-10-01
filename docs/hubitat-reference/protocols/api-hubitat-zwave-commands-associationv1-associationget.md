# AssociationGet

- **ID:** `api-hubitat-zwave-commands-associationv1-associationget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationv1.AssociationGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationGet` | `AssociationGet()` | Creates the command with its declared field defaults. |
| `AssociationGet` | `AssociationGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getGroupingIdentifier` | `Short getGroupingIdentifier()` | Returns the grouping identifier value stored in groupingIdentifier. Returns: grouping identifier value |
| `getJSON` | `String getJSON()` | Builds the Z-Wave JS command invocation for this command. Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setGroupingIdentifier` | `void setGroupingIdentifier(Short groupingIdentifier)` | Sets the grouping identifier value used by this command. Parameters: groupingIdentifier - grouping identifier value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
