# SupervisionGet

- **ID:** `api-hubitat-zwave-commands-supervisionv1-supervisionget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.supervisionv1.SupervisionGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `encapsulate` | `SupervisionGet encapsulate(Command cmd)` | Stores the supplied command as the inner command for this encapsulation. Parameters: cmd - command or command list copied into the encapsulation. Returns: this encapsulation with t |
| `encapsulatedCommand` | `Command encapsulatedCommand(Map<Integer, Integer> options)` | Decodes the inner command stored in this encapsulation. Parameters: options - map keyed by command-class identifier (Integer); each value is the version (Integer) passed to command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSessionID` | `Short getSessionID()` | Returns the session id value stored in bits 0 through 5 of properties1. Returns: session id value |
| `getStatusUpdates` | `Boolean getStatusUpdates()` | Reports whether bit 7 of properties1 is set for status updates. Returns: status updates value |
| `setSessionID` | `void setSessionID(Short value)` | Encodes session id in bits 0 through 5 of properties1 and preserves the bits selected by 0xC0. Parameters: value - session id value to encode |
| `setStatusUpdates` | `void setStatusUpdates(Boolean value)` | Sets the status updates value used by this command. Parameters: value - status updates value to encode |
| `SupervisionGet` | `SupervisionGet()` | Creates the command with its declared field defaults. |
| `SupervisionGet` | `SupervisionGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `commandByte` | `List<Short>` | — |
| `commandClassIdentifier` | `Short` | — |
| `commandIdentifier` | `Short` | — |
| `commandLength` | `Short` | — |
