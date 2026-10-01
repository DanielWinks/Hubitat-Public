# NodeProvisioningDelete

- **ID:** `api-hubitat-zwave-commands-nodeprovisioningv1-nodeprovisioningdelete`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.nodeprovisioningv1.NodeProvisioningDelete`

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
| `getDSK` | `List<Short> getDSK()` | Returns the d sk value stored in DSK. Returns: d sk value |
| `getDSKLength` | `Short getDSKLength()` | Returns the d sklength value stored in DSKLength. Returns: d sklength value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `NodeProvisioningDelete` | `NodeProvisioningDelete()` | Creates the command with its declared field defaults. |
| `NodeProvisioningDelete` | `NodeProvisioningDelete(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `parseDSK` | `NodeProvisioningDelete parseDSK(String DSKParam)` | Parses a provisioning DSK string into command fields. Parameters: DSKParam - DSK text to decode. Returns: this command populated from the parsed provisioning data. |
| `setDSK` | `void setDSK(List<Short> DSK)` | Sets the d sk value used by this command. Parameters: DSK - d sk value to encode |
| `setDSKLength` | `void setDSKLength(Short DSKLength)` | Sets the d sklength value used by this command. Parameters: DSKLength - d sklength value to encode |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
