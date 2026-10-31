# AssociationGroupNameGet

- **ID:** `api-hubitat-zwave-commands-associationgrpinfov2-associationgroupnameget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationgrpinfov2.AssociationGroupNameGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.associationgrpinfov1.AssociationGroupNameGet`
- [AssociationGroupNameGet](../protocols/api-hubitat-zwave-commands-associationgrpinfov1-associationgroupnameget.md)
- [AssociationGroupNameGet](../protocols/api-hubitat-zwave-commands-associationgrpinfov1-associationgroupnameget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationGroupNameGet` | `AssociationGroupNameGet()` | Creates the command with its declared field defaults. |
| `AssociationGroupNameGet` | `AssociationGroupNameGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `groupingIdentifier` | `Short` | — |
