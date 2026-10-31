# NodeProvisioningListIterationReport

- **ID:** `api-hubitat-zwave-commands-nodeprovisioningv1-nodeprovisioninglistiterationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.nodeprovisioningv1.NodeProvisioningListIterationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getBootMode` | `Short getBootMode()` | Returns the boot mode value stored in bootMode. Returns: boot mode value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getDSK` | `List<Short> getDSK()` | Returns the d sk value stored in DSK. Returns: d sk value |
| `getDSKAsString` | `String getDSKAsString()` | Returns the d skas string value stored in format. Returns: d skas string value |
| `getDSKLength` | `Short getDSKLength()` | Returns the d sklength value stored in DSKLength. Returns: d sklength value |
| `getInclusionSetting` | `Short getInclusionSetting()` | Returns the inclusion setting value stored in inclusionSetting. Returns: inclusion setting value |
| `getLocation` | `List<Short> getLocation()` | Returns the location value stored in location. Returns: location value |
| `getMetaDataExtension` | `List<Short> getMetaDataExtension()` | Returns the meta data extension value stored in metaDataExtension. Returns: meta data extension value |
| `getName` | `List<Short> getName()` | Returns the name value stored in name. Returns: name value |
| `getNetworkStatus` | `Short getNetworkStatus()` | Returns the network status value stored in networkStatus. Returns: network status value |
| `getNodeId` | `Integer getNodeId()` | Returns the node id value stored in nodeId. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getProductType` | `List<Short> getProductType()` | Returns the product type value stored in productType. Returns: product type value |
| `getRemainingCount` | `Short getRemainingCount()` | Returns the remaining count value stored in remainingCount. Returns: remaining count value |
| `getRequestedKeys` | `Short getRequestedKeys()` | Returns the requested keys value stored in requestedKeys. Returns: requested keys value |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `NodeProvisioningListIterationReport` | `NodeProvisioningListIterationReport()` | Creates the command with its declared field defaults. |
| `NodeProvisioningListIterationReport` | `NodeProvisioningListIterationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setDSK` | `void setDSK(List<Short> DSK)` | Sets the d sk value used by this command. Parameters: DSK - d sk value to encode |
| `setDSKLength` | `void setDSKLength(Short DSKLength)` | Sets the d sklength value used by this command. Parameters: DSKLength - d sklength value to encode |
| `setMetaDataExtension` | `void setMetaDataExtension(List<Short> metaDataExtension)` | Sets the meta data extension value used by this command. Parameters: metaDataExtension - meta data extension value to encode |
| `setRemainingCount` | `void setRemainingCount(Short remainingCount)` | Sets the remaining count value used by this command. Parameters: remainingCount - remaining count value to encode |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
