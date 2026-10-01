# Complete

- **ID:** `api-hubitat-zwave-commands-inclusioncontrollerv1-complete`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.inclusioncontrollerv1.Complete`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `Complete` | `Complete()` | Creates the command with its declared field defaults. |
| `Complete` | `Complete(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getNodeID` | `short getNodeID()` | Returns the node id value stored in nodeID. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getStatus` | `short getStatus()` | Returns the status value stored in status. Returns: status value |
| `setNodeID` | `void setNodeID(short nodeID)` | Sets the node id value used by this command. Parameters: nodeID - node id value to encode |
| `setStatus` | `void setStatus(short status)` | Sets the status value used by this command. Parameters: status - status value to encode |
