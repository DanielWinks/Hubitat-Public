# DoorLockConfigurationSet

- **ID:** `api-hubitat-zwave-commands-doorlockv1-doorlockconfigurationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv1.DoorLockConfigurationSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockConfigurationSet` | `DoorLockConfigurationSet()` | Creates the command with its declared field defaults. |
| `DoorLockConfigurationSet` | `DoorLockConfigurationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getInsideDoorHandlesState` | `Short getInsideDoorHandlesState()` | Returns the inside door handles state value stored in bits 0 through 3 of properties1. Returns: inside door handles state value |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getOutsideDoorHandlesState` | `Short getOutsideDoorHandlesState()` | Returns the outside door handles state value stored in bits 4 through 7 of properties1. Returns: outside door handles state value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setInsideDoorHandlesState` | `void setInsideDoorHandlesState(Short value)` | Encodes inside door handles state in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - inside door handles state value to encode |
| `setOutsideDoorHandlesState` | `void setOutsideDoorHandlesState(Short value)` | Sets the outside door handles state value used by this command. Parameters: value - outside door handles state value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `lockTimeoutMinutes` | `Short` | — |
| `lockTimeoutSeconds` | `Short` | — |
| `OPERATION_TYPE_CONSTANT_OPERATION` | `Short` | — |
| `OPERATION_TYPE_TIMED_OPERATION` | `Short` | — |
| `operationType` | `Short` | — |
