# NodeRemoveStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv1-noderemovestatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv1.NodeRemoveStatus`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `NodeRemoveStatus` | `NodeRemoveStatus()` | Creates the command with its declared field defaults. |
| `NodeRemoveStatus` | `NodeRemoveStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `extendedNodeId` | `Integer` | — |
| `nodeId` | `Short` | — |
| `REMOVE_NODE_STATUS_DONE` | `short` | — |
| `REMOVE_NODE_STATUS_FAILED` | `short` | — |
| `seqNo` | `Short` | — |
| `status` | `Short` | — |
