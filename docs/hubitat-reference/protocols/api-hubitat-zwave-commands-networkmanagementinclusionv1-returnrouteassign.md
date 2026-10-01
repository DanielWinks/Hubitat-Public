# ReturnRouteAssign

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv1-returnrouteassign`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv1.ReturnRouteAssign`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getDestinationNodeId` | `Short getDestinationNodeId()` | Returns the destination node id value stored in destinationNodeId. Returns: destination node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `getSourceNodeId` | `Short getSourceNodeId()` | Returns the source node id value stored in sourceNodeId. Returns: source node id value |
| `ReturnRouteAssign` | `ReturnRouteAssign()` | Creates the command with its declared field defaults. |
| `ReturnRouteAssign` | `ReturnRouteAssign(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setDestinationNodeId` | `void setDestinationNodeId(Short destinationNodeId)` | Sets the destination node id value used by this command. Parameters: destinationNodeId - destination node id value to encode |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `setSourceNodeId` | `void setSourceNodeId(Short sourceNodeId)` | Sets the source node id value used by this command. Parameters: sourceNodeId - source node id value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
