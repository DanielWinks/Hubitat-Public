# AssociationGroupingsReport

- **ID:** `api-hubitat-zwave-commands-associationv1-associationgroupingsreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationv1.AssociationGroupingsReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationGroupingsReport` | `AssociationGroupingsReport()` | Creates the command with its declared field defaults. |
| `AssociationGroupingsReport` | `AssociationGroupingsReport(List<Map<String, Object>> payload)` | Initializes the supported-groupings field from Z-Wave JS response records. Parameters: payload - non-null list of records. A record is used only when it contains response; that val |
| `AssociationGroupingsReport` | `AssociationGroupingsReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getJSON` | `String getJSON()` | Reports that this command has no Z-Wave JS JSON representation. Returns: null |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSupportedGroupings` | `Short getSupportedGroupings()` | Returns the supported groupings value stored in supportedGroupings. Returns: supported groupings value |
| `setSupportedGroupings` | `void setSupportedGroupings(Short supportedGroupings)` | Sets the supported groupings value used by this command. Parameters: supportedGroupings - supported groupings value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
