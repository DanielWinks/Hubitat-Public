# FailedNodeListReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv4-failednodelistreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv4.FailedNodeListReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementproxyv3.FailedNodeListReport`
- [FailedNodeListReport](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-failednodelistreport.md)
- [FailedNodeListReport](../protocols/api-hubitat-zwave-commands-networkmanagementproxyv3-failednodelistreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FailedNodeListReport` | `FailedNodeListReport()` | Creates the command with its declared field defaults. |
| `FailedNodeListReport` | `FailedNodeListReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getAsList` | `List<Integer> getAsList()` | Returns the as list value stored in retList. Returns: as list value |
| `isExtendedNodeFailed` | `boolean isExtendedNodeFailed(int reqNodeId)` | Tests the failed-node bitmap for the requested node identifier. Parameters: reqNodeId - node identifier whose corresponding bitmap flag is tested. Returns: true when the stored bit |
