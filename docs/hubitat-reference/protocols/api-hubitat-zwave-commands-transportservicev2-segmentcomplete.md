# SegmentComplete

- **ID:** `api-hubitat-zwave-commands-transportservicev2-segmentcomplete`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.transportservicev2.SegmentComplete`

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
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSessionID` | `Short getSessionID()` | Returns the session id value stored in sessionID. Returns: session id value |
| `SegmentComplete` | `SegmentComplete()` | Creates the command with its declared field defaults. |
| `SegmentComplete` | `SegmentComplete(String payloadStr)` | Decodes this command's fields from a hexadecimal payload. Parameters: payloadStr - hexadecimal payload bytes for this command |
| `setSessionID` | `void setSessionID(Short sessionID)` | Sets the session id value used by this command. Parameters: sessionID - session id value to encode |
