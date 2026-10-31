# MultiChannelCmdEncap

- **ID:** `api-hubitat-zwave-commands-multichannelv3-multichannelcmdencap`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `encapsulate` | `MultiChannelCmdEncap encapsulate(Command cmd)` | Stores the supplied command as the inner command for this encapsulation. Parameters: cmd - command or command list copied into the encapsulation. Returns: this encapsulation with t |
| `encapsulatedCommand` | `Command encapsulatedCommand(Map<Integer, Integer> options)` | Decodes the inner command stored in this encapsulation. Parameters: options - map keyed by command-class identifier (Integer); each value is the version (Integer) passed to command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: wrapped command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `MultiChannelCmdEncap` | `MultiChannelCmdEncap()` | Creates the command with its declared field defaults. |
| `MultiChannelCmdEncap` | `MultiChannelCmdEncap(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `bitAddress` | `Boolean` | — |
| `command` | `Short` | — |
| `commandClass` | `Short` | — |
| `destinationEndPoint` | `Short` | — |
| `parameter` | `List<Short>` | — |
| `res01` | `Boolean` | — |
| `sourceEndPoint` | `Short` | — |
