# SubsequentSegment

- **ID:** `api-hubitat-zwave-commands-transportservicev2-subsequentsegment`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.transportservicev2.SubsequentSegment`

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
| `getDatagramOffset` | `Integer getDatagramOffset()` | Returns the datagram offset value stored in datagramOffset. Returns: datagram offset value |
| `getDatagramSize` | `Integer getDatagramSize()` | Returns the datagram size value stored in datagramSize. Returns: datagram size value |
| `getExt` | `Boolean getExt()` | Returns the ext value stored in ext. Returns: ext value |
| `getFCS` | `Integer getFCS()` | Returns the f cs value stored in FCS. Returns: f cs value |
| `getHeaderExtension` | `List<Short> getHeaderExtension()` | Returns the header extension value stored in headerExtension. Returns: header extension value |
| `getHeaderExtensionLength` | `Short getHeaderExtensionLength()` | Returns the header extension length value stored in headerExtensionLength. Returns: header extension length value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSessionID` | `Short getSessionID()` | Returns the session id value stored in sessionID. Returns: session id value |
| `setDatagramOffset` | `void setDatagramOffset(Integer datagramOffset)` | Sets the datagram offset value used by this command. Parameters: datagramOffset - datagram offset value to encode |
| `setDatagramSize` | `void setDatagramSize(Integer datagramSize)` | Sets the datagram size value used by this command. Parameters: datagramSize - datagram size value to encode |
| `setExt` | `void setExt(Boolean ext)` | Sets the ext value used by this command. Parameters: ext - ext value to encode |
| `setFCS` | `void setFCS(Integer FCS)` | Sets the f cs value used by this command. Parameters: FCS - f cs value to encode |
| `setHeaderExtension` | `void setHeaderExtension(List<Short> headerExtension)` | Sets the header extension value used by this command. Parameters: headerExtension - header extension value to encode |
| `setHeaderExtensionLength` | `void setHeaderExtensionLength(Short headerExtensionLength)` | Sets the header extension length value used by this command. Parameters: headerExtensionLength - header extension length value to encode |
| `setPayload` | `void setPayload(List<Short> payload)` | Sets the payload value used by this command. Parameters: payload - payload value to encode |
| `setSessionID` | `void setSessionID(Short sessionID)` | Sets the session id value used by this command. Parameters: sessionID - session id value to encode |
| `SubsequentSegment` | `SubsequentSegment()` | Creates the command with its declared field defaults. |
| `SubsequentSegment` | `SubsequentSegment(String payloadStr)` | Decodes this command's fields from a hexadecimal payload. Parameters: payloadStr - hexadecimal payload bytes for this command |
