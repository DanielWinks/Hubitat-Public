# FailedNodeReplace

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv1-failednodereplace`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv1.FailedNodeReplace`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FailedNodeReplace` | `FailedNodeReplace()` | Creates the command with its declared field defaults. |
| `FailedNodeReplace` | `FailedNodeReplace(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getMode` | `Short getMode()` | Returns the mode value stored in mode. Returns: mode value |
| `getNodeId` | `Short getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `getTxOptions` | `Short getTxOptions()` | Returns the tx options value stored in txOptions. Returns: tx options value |
| `setMode` | `void setMode(Short mode)` | Sets the mode value used by this command. Parameters: mode - mode value to encode |
| `setNodeId` | `void setNodeId(Short nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `setTxOptions` | `void setTxOptions(Short txOptions)` | Sets the tx options value used by this command. Parameters: txOptions - tx options value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
