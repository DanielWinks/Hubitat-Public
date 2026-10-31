# SecurityMessageEncapsulation

- **ID:** `api-hubitat-zwave-commands-securityv1-securitymessageencapsulation`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `encapsulate` | `SecurityMessageEncapsulation encapsulate(Command cmd)` | Stores the supplied command as the inner command for this encapsulation. Parameters: cmd - command or command list copied into the encapsulation. Returns: this encapsulation with t |
| `encapsulatedCommand` | `Command encapsulatedCommand(Map<Integer, Integer> options)` | Decodes the inner command stored in this encapsulation. Parameters: options - map keyed by command-class identifier (Integer); each value is the version (Integer) passed to command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getJSON` | `String getJSON()` | Returns: wrapped command format |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SecurityMessageEncapsulation` | `SecurityMessageEncapsulation()` | Creates the command with its declared field defaults. |
| `SecurityMessageEncapsulation` | `SecurityMessageEncapsulation(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `commandByte` | `List<Short>` | — |
| `commandClassIdentifier` | `Short` | — |
| `commandIdentifier` | `Short` | — |
| `secondFrame` | `Boolean` | — |
| `sequenceCounter` | `Short` | — |
| `sequenced` | `Boolean` | — |
