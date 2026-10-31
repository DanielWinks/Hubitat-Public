# NodeInfoCachedReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv2-nodeinfocachedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv2.NodeInfoCachedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementproxyv1.NodeInfoCachedReport`
- [NodeInfoCachedReport](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv1-nodeinfocachedreport.md)
- [NodeInfoCachedReport](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv1-nodeinfocachedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getGrantedKeys` | `Short getGrantedKeys()` | Returns the granted keys value stored in grantedKeys. Returns: granted keys value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `NodeInfoCachedReport` | `NodeInfoCachedReport()` | Creates the command with its declared field defaults. |
| `NodeInfoCachedReport` | `NodeInfoCachedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setGrantedKeys` | `void setGrantedKeys(Short grantedKeys)` | Sets the granted keys value used by this command. Parameters: grantedKeys - granted keys value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
