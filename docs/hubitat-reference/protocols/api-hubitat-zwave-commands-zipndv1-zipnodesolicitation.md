# ZipNodeSolicitation

- **ID:** `api-hubitat-zwave-commands-zipndv1-zipnodesolicitation`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipndv1.ZipNodeSolicitation`

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
| `getIpv6Address` | `List<Short> getIpv6Address()` | Returns the ipv6 address value stored in ipv6Address. Returns: ipv6 address value |
| `getNodeId` | `Short getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setIpv6Address` | `void setIpv6Address(List<Short> ipv6Add)` | Sets the ipv6 address value used by this command. Parameters: ipv6Add - ipv6 address value to encode |
| `setNodeId` | `void setNodeId(Short nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `ZipNodeSolicitation` | `ZipNodeSolicitation()` | Creates the command with its declared field defaults. |
| `ZipNodeSolicitation` | `ZipNodeSolicitation(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
