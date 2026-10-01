# MultiCmdEncap

- **ID:** `api-hubitat-zwave-commands-multicmdv1-multicmdencap`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.multicmdv1.MultiCmdEncap`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `encapsulate` | `MultiCmdEncap encapsulate(List<Command> cmds)` | Stores the supplied command as the inner command for this encapsulation. Parameters: cmds - command or command list copied into the encapsulation. Returns: this encapsulation with  |
| `encapsulatedCommands` | `List<Command> encapsulatedCommands(Map<Integer, Integer> options)` | Decodes the inner commands stored in this encapsulation. Parameters: options - map keyed by command-class identifier (Integer); each value is the version (Integer) passed to comman |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `MultiCmdEncap` | `MultiCmdEncap()` | Creates the command with its declared field defaults. |
| `MultiCmdEncap` | `MultiCmdEncap(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `data` | `List<Short>` | — |
| `numberOfCommands` | `Short` | — |
