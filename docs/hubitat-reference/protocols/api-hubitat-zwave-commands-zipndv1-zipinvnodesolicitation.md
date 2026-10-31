# ZipInvNodeSolicitation

- **ID:** `api-hubitat-zwave-commands-zipndv1-zipinvnodesolicitation`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipndv1.ZipInvNodeSolicitation`

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
| `getLocal` | `Boolean getLocal()` | Returns the local value stored in local. Returns: local value |
| `getNodeId` | `Short getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setLocal` | `void setLocal(Boolean local)` | Sets the local value used by this command. Parameters: local - local value to encode |
| `setNodeId` | `void setNodeId(Short nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `ZipInvNodeSolicitation` | `ZipInvNodeSolicitation()` | Creates the command with its declared field defaults. |
| `ZipInvNodeSolicitation` | `ZipInvNodeSolicitation(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
