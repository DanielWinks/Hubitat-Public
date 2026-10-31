# AssociationReport

- **ID:** `api-hubitat-zwave-commands-associationv1-associationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationv1.AssociationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AssociationReport` | `AssociationReport()` | Creates the command with its declared field defaults. |
| `AssociationReport` | `AssociationReport(List<Map<String, Object>> payload)` | Initializes association fields from Z-Wave JS response records. Parameters: payload - non-null list of records. A record is used only when it contains response. For a used record,  |
| `AssociationReport` | `AssociationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getGroupingIdentifier` | `Short getGroupingIdentifier()` | Returns the grouping identifier value stored in groupingIdentifier. Returns: grouping identifier value |
| `getMaxNodesSupported` | `Short getMaxNodesSupported()` | Returns the max nodes supported value stored in maxNodesSupported. Returns: max nodes supported value |
| `getNodeId` | `List<Short> getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getReportsToFollow` | `Short getReportsToFollow()` | Returns the reports to follow value stored in reportsToFollow. Returns: reports to follow value |
| `setGroupingIdentifier` | `void setGroupingIdentifier(Short groupingIdentifier)` | Sets the grouping identifier value used by this command. Parameters: groupingIdentifier - grouping identifier value to encode |
| `setMaxNodesSupported` | `void setMaxNodesSupported(Short maxNodesSupported)` | Sets the max nodes supported value used by this command. Parameters: maxNodesSupported - max nodes supported value to encode |
| `setNodeId` | `void setNodeId(List<Short> nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `setReportsToFollow` | `void setReportsToFollow(Short reportsToFollow)` | Sets the reports to follow value used by this command. Parameters: reportsToFollow - reports to follow value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
