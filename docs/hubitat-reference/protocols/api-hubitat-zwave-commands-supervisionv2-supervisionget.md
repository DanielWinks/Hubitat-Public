# SupervisionGet

- **ID:** `api-hubitat-zwave-commands-supervisionv2-supervisionget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.supervisionv2.SupervisionGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.supervisionv1.SupervisionGet`
- [SupervisionGet](../protocols/api-hubitat-zwave-commands-supervisionv1-supervisionget.md)
- [SupervisionGet](../protocols/api-hubitat-zwave-commands-supervisionv1-supervisionget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `encapsulate` | `SupervisionGet encapsulate(Command cmd)` | Stores the supplied command as the inner command for this encapsulation. Parameters: cmd - command or command list copied into the encapsulation. Returns: this encapsulation with t |
| `encapsulatedCommand` | `Command encapsulatedCommand(Map<Integer, Integer> options)` | Decodes the inner command stored in this encapsulation. Parameters: options - map keyed by command-class identifier (Integer); each value is the version (Integer) passed to command |
| `SupervisionGet` | `SupervisionGet()` | Creates the command with its declared field defaults. |
| `SupervisionGet` | `SupervisionGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
