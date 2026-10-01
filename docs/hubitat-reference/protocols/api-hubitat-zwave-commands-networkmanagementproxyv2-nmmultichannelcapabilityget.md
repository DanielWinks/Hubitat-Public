# NmMultiChannelCapabilityGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv2-nmmultichannelcapabilityget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv2.NmMultiChannelCapabilityGet`

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
| `getEndPoint` | `short getEndPoint()` | Returns the end point value stored in endPoint. Returns: end point value |
| `getNodeId` | `short getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSeqNo` | `short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `NmMultiChannelCapabilityGet` | `NmMultiChannelCapabilityGet()` | Creates the command with its declared field defaults. |
| `NmMultiChannelCapabilityGet` | `NmMultiChannelCapabilityGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setEndPoint` | `void setEndPoint(short endPoint)` | Sets the end point value used by this command. Parameters: endPoint - end point value to encode |
| `setNodeId` | `void setNodeId(short nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `setSeqNo` | `void setSeqNo(short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
