# NmMultiChannelCapabilityGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv4-nmmultichannelcapabilityget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv4.NmMultiChannelCapabilityGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementproxyv3.NmMultiChannelCapabilityGet`
- [NmMultiChannelCapabilityGet](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-nmmultichannelcapabilityget.md)
- [NmMultiChannelCapabilityGet](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-nmmultichannelcapabilityget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `NmMultiChannelCapabilityGet` | `NmMultiChannelCapabilityGet()` | Creates the command with its declared field defaults. |
| `NmMultiChannelCapabilityGet` | `NmMultiChannelCapabilityGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
