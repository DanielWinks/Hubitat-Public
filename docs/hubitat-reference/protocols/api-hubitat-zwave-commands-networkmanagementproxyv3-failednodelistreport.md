# FailedNodeListReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv3-failednodelistreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv3.FailedNodeListReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FailedNodeListReport` | `FailedNodeListReport()` | Creates the command with its declared field defaults. |
| `FailedNodeListReport` | `FailedNodeListReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getFailedNodeListData` | `List<Short> getFailedNodeListData()` | Returns the failed node list data value stored in failedNodeListData. Returns: failed node list data value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `isNodeFailed` | `boolean isNodeFailed(int reqNodeId)` | Tests the failed-node bitmap for the requested node identifier. Parameters: reqNodeId - node identifier whose corresponding bitmap flag is tested. Returns: true when the stored bit |
| `setFailedNodeListData` | `void setFailedNodeListData(List<Short> failedNodeListData)` | Sets the failed node list data value used by this command. Parameters: failedNodeListData - failed node list data value to encode |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
