# AssociationGroupNameReport

- **ID:** `api-hubitat-zwave-commands-associationgrpinfov1-associationgroupnamereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationgrpinfov1.AssociationGroupNameReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationGroupNameReport` | `AssociationGroupNameReport()` | Creates the command with its declared field defaults. |
| `AssociationGroupNameReport` | `AssociationGroupNameReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `groupingIdentifier` | `Short` | — |
| `lengthOfName` | `Short` | — |
| `name` | `List<Short>` | — |
