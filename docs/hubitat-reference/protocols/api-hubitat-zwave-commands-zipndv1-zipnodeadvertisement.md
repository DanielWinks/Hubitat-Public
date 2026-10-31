# ZipNodeAdvertisement

- **ID:** `api-hubitat-zwave-commands-zipndv1-zipnodeadvertisement`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipndv1.ZipNodeAdvertisement`

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
| `getHomeId` | `List<Short> getHomeId()` | Returns the home id value stored in homeId. Returns: home id value |
| `getIpv6Address` | `List<Short> getIpv6Address()` | Returns the ipv6 address value stored in ipv6Address. Returns: ipv6 address value |
| `getLocal` | `Boolean getLocal()` | Returns the local value stored in local. Returns: local value |
| `getNodeId` | `Short getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getValidity` | `Short getValidity()` | Returns the validity value stored in validity. Returns: validity value |
| `setHomeId` | `void setHomeId(List<Short> homeId)` | Sets the home id value used by this command. Parameters: homeId - home id value to encode |
| `setIpv6Address` | `void setIpv6Address(List<Short> ipv6Address)` | Sets the ipv6 address value used by this command. Parameters: ipv6Address - ipv6 address value to encode |
| `setLocal` | `void setLocal(Boolean local)` | Sets the local value used by this command. Parameters: local - local value to encode |
| `setNodeId` | `void setNodeId(Short nodeId)` | Sets the node id value used by this command. Parameters: nodeId - node id value to encode |
| `setValidity` | `void setValidity(Short validity)` | Sets the validity value used by this command. Parameters: validity - validity value to encode |
| `ZipNodeAdvertisement` | `ZipNodeAdvertisement()` | Creates the command with its declared field defaults. |
| `ZipNodeAdvertisement` | `ZipNodeAdvertisement(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
