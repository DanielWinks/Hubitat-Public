# NodeInfoCachedGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv4-nodeinfocachedget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv4.NodeInfoCachedGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementproxyv3.NodeInfoCachedGet`
- [NodeInfoCachedGet](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-nodeinfocachedget.md)
- [NodeInfoCachedGet](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-nodeinfocachedget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getExtendedNodeId` | `Integer getExtendedNodeId()` | Returns the extended node id value stored in extendedNodeId. Returns: extended node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `NodeInfoCachedGet` | `NodeInfoCachedGet()` | Creates the command with its declared field defaults. |
| `NodeInfoCachedGet` | `NodeInfoCachedGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setExtendedNodeId` | `void setExtendedNodeId(Integer extendedNodeId)` | Sets the extended node id value used by this command. Parameters: extendedNodeId - extended node id value to encode |
