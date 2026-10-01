# NmMultiChannelAggregatedMembersGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv2-nmmultichannelaggregatedmembersget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv2.NmMultiChannelAggregatedMembersGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAggregatedEndPoint` | `short getAggregatedEndPoint()` | Returns the aggregated end point value stored in aggregatedEndPoint. Returns: aggregated end point value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getNodeId` | `short getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSeqNo` | `short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `NmMultiChannelAggregatedMembersGet` | `NmMultiChannelAggregatedMembersGet()` | Creates the command with its declared field defaults. |
| `NmMultiChannelAggregatedMembersGet` | `NmMultiChannelAggregatedMembersGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setAggregatedEndPoint` | `void setAggregatedEndPoint(short aggregatedEndPoint)` | Sets the aggregated end point value used by this command. Parameters: aggregatedEndPoint - aggregated end point value to encode |
| `setNodeId` | `void setNodeId(short nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `setSeqNo` | `void setSeqNo(short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
