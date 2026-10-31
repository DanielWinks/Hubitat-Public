# MultiInstanceCmdEncap

- **ID:** `api-hubitat-zwave-commands-multiinstancev1-multiinstancecmdencap`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.multiinstancev1.MultiInstanceCmdEncap`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `encapsulate` | `MultiInstanceCmdEncap encapsulate(Command cmd)` | Stores the supplied command as the inner command for this encapsulation. Parameters: cmd - command or command list copied into the encapsulation. Returns: this encapsulation with t |
| `encapsulatedCommand` | `Command encapsulatedCommand(Map<Integer, Integer> options)` | Decodes the inner command stored in this encapsulation. Parameters: options - map keyed by command-class identifier (Integer); each value is the version (Integer) passed to command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `MultiInstanceCmdEncap` | `MultiInstanceCmdEncap()` | Creates the command with its declared field defaults. |
| `MultiInstanceCmdEncap` | `MultiInstanceCmdEncap(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `command` | `Short` | — |
| `commandClass` | `Short` | — |
| `instance` | `Short` | — |
| `parameter` | `List<Short>` | — |
