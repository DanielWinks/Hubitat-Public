# NodeAdd

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv1-nodeadd`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv1.NodeAdd`

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
| `NodeAdd` | `NodeAdd()` | Creates the command with its declared field defaults. |
| `NodeAdd` | `NodeAdd(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `ADD_NODE_ANY` | `short` | — |
| `ADD_NODE_STOP` | `short` | — |
| `mode` | `Short` | — |
| `seqNo` | `Short` | — |
| `TRANSMIT_OPTION_EXPLORE` | `short` | — |
| `TRANSMIT_OPTION_LOW_POWER` | `short` | — |
| `TRANSMIT_OPTION_NULL` | `short` | — |
| `txOptions` | `Short` | — |
