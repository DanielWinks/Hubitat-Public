# NmMultiChannelAggregatedMembersGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv4-nmmultichannelaggregatedmembersget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv4.NmMultiChannelAggregatedMembersGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementproxyv3.NmMultiChannelAggregatedMembersGet`
- [NmMultiChannelAggregatedMembersGet](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-nmmultichannelaggregatedmembersget.md)
- [NmMultiChannelAggregatedMembersGet](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-nmmultichannelaggregatedmembersget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getExtendedNodeId` | `Integer getExtendedNodeId()` | Returns the extended node id value stored in extendedNodeId. Returns: extended node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `NmMultiChannelAggregatedMembersGet` | `NmMultiChannelAggregatedMembersGet()` | Creates the command with its declared field defaults. |
| `NmMultiChannelAggregatedMembersGet` | `NmMultiChannelAggregatedMembersGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setExtendedNodeId` | `void setExtendedNodeId(Integer extendedNodeId)` | Sets the extended node id value used by this command. Parameters: extendedNodeId - extended node id value to encode |
