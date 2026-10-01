# NodeAddStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv1-nodeaddstatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv1.NodeAddStatus`

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
| `isListening` | `Boolean isListening()` | Reports whether bit 7 of capability is set for listening. Returns: listening flag |
| `isOptionalFunctionality` | `Boolean isOptionalFunctionality()` | Reports whether bit 7 of security is set for optional functionality. Returns: optional functionality flag |
| `NodeAddStatus` | `NodeAddStatus()` | Creates the command with its declared field defaults. |
| `NodeAddStatus` | `NodeAddStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `ADD_NODE_STATUS_DONE` | `short` | — |
| `ADD_NODE_STATUS_FAILED` | `short` | — |
| `ADD_NODE_STATUS_SECURITY_FAILED` | `short` | — |
| `basicDeviceClass` | `Short` | — |
| `capability` | `Short` | Capability byte consists of: bit 7: Listening flag bit 6: Routing flag bits 5-3: Max Speed bits 2-0 Protocol version |
| `genericDeviceClass` | `Short` | — |
| `newNodeId` | `Short` | — |
| `nodeInfoLength` | `Short` | — |
| `nonSecureControlledCommandClass` | `List<Short>` | — |
| `nonSecureSupportedCommandClass` | `List<Short>` | — |
| `security` | `Short` | Security byte consists of: bit 7: Optional Functionality flag bit 6: Frequently listening Mode flag 1000ms bit 5: Frequently listening Mode flag 250ms bit 4: Beaming flag bit 3: Ro |
| `securityScheme0ControlledCommandClass` | `List<Short>` | — |
| `securityScheme0SupportedCommandClass` | `List<Short>` | — |
| `seqNo` | `Short` | — |
| `specificDeviceClass` | `Short` | — |
| `status` | `Short` | — |
