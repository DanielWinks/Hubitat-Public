# Initiate

- **ID:** `api-hubitat-zwave-commands-inclusioncontrollerv1-initiate`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.inclusioncontrollerv1.Initiate`

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
| `getNodeID` | `short getNodeID()` | Returns the node id value stored in nodeID. Returns: node id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getStepID` | `short getStepID()` | Returns the step id value stored in stepID. Returns: step id value |
| `Initiate` | `Initiate()` | Creates the command with its declared field defaults. |
| `Initiate` | `Initiate(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setNodeID` | `void setNodeID(short nodeID)` | Sets the node id value used by this command. Parameters: nodeID - node id value to encode |
| `setStepID` | `void setStepID(short stepID)` | Sets the step id value used by this command. Parameters: stepID - step id value to encode |
