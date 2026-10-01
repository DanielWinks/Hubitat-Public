# ExtendedNodeAddStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv4-extendednodeaddstatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv4.ExtendedNodeAddStatus`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ExtendedNodeAddStatus` | `ExtendedNodeAddStatus()` | Creates the command with its declared field defaults. |
| `ExtendedNodeAddStatus` | `ExtendedNodeAddStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `isListening` | `Boolean isListening()` | Reports whether bit 7 of capability is set for listening. Returns: listening flag |
| `isOptionalFunctionality` | `Boolean isOptionalFunctionality()` | Reports whether bit 7 of security is set for optional functionality. Returns: optional functionality flag |

## Properties

| Name | Type | Description |
|---|---|---|
| `ADD_NODE_STATUS_DONE` | `short` | — |
| `ADD_NODE_STATUS_FAILED` | `short` | — |
| `ADD_NODE_STATUS_SECURITY_FAILED` | `short` | — |
| `basicDeviceClass` | `Short` | — |
| `capability` | `Short` | — |
| `genericDeviceClass` | `Short` | — |
| `grantedKeys` | `Short` | — |
| `KEX_FAIL_AUTH` | `short` | — |
| `KEX_FAIL_CANCEL` | `short` | — |
| `KEX_FAIL_DECRYPT` | `short` | — |
| `KEX_FAIL_KEX_CURVES` | `short` | — |
| `KEX_FAIL_KEX_KEY` | `short` | — |
| `KEX_FAIL_KEX_SCHEME` | `short` | — |
| `KEX_FAIL_KEY_GET` | `short` | — |
| `KEX_FAIL_KEY_REPORT` | `short` | — |
| `KEX_FAIL_KEY_VERIFY` | `short` | — |
| `KEX_SUCCESS` | `short` | — |
| `KEXFailType` | `Short` | — |
| `newNodeId` | `Integer` | — |
| `nodeInfoLength` | `Short` | — |
| `nonSecureControlledCommandClass` | `List<Short>` | — |
| `nonSecureSupportedCommandClass` | `List<Short>` | — |
| `security` | `Short` | — |
| `securityScheme0ControlledCommandClass` | `List<Short>` | — |
| `securityScheme0SupportedCommandClass` | `List<Short>` | — |
| `seqNo` | `Short` | — |
| `specificDeviceClass` | `Short` | — |
| `status` | `Short` | — |
