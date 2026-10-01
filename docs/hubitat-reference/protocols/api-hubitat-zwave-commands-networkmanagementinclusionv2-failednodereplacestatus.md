# FailedNodeReplaceStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv2-failednodereplacestatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv2.FailedNodeReplaceStatus`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementinclusionv1.FailedNodeReplaceStatus`
- [FailedNodeReplaceStatus](../protocols/api-hubitat-zwave-commands-networkmanagementinclusionv1-failednodereplacestatus.md)
- [FailedNodeReplaceStatus](../protocols/api-hubitat-zwave-commands-networkmanagementinclusionv1-failednodereplacestatus.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FailedNodeReplaceStatus` | `FailedNodeReplaceStatus()` | Creates the command with its declared field defaults. |
| `FailedNodeReplaceStatus` | `FailedNodeReplaceStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getGrantedKeys` | `Short getGrantedKeys()` | Returns the granted keys value stored in grantedKeys. Returns: granted keys value |
| `getKEXFailType` | `Short getKEXFailType()` | Returns the k exfail type value stored in KEXFailType. Returns: k exfail type value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setGrantedKeys` | `void setGrantedKeys(Short grantedKeys)` | Sets the granted keys value used by this command. Parameters: grantedKeys - granted keys value to encode |
| `setKEXFailType` | `void setKEXFailType(Short KEXFailType)` | Sets the k exfail type value used by this command. Parameters: KEXFailType - k exfail type value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
