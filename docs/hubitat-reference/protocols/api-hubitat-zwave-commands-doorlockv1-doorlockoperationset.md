# DoorLockOperationSet

- **ID:** `api-hubitat-zwave-commands-doorlockv1-doorlockoperationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv1.DoorLockOperationSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockOperationSet` | `DoorLockOperationSet()` | Creates the command with its declared field defaults. |
| `DoorLockOperationSet` | `DoorLockOperationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `DOOR_LOCK_MODE_DOOR_SECURED` | `Short` | — |
| `DOOR_LOCK_MODE_DOOR_UNSECURED` | `Short` | — |
| `DOOR_LOCK_MODE_DOOR_UNSECURED_FOR_INSIDE_DOOR_HANDLES` | `Short` | — |
| `DOOR_LOCK_MODE_DOOR_UNSECURED_FOR_INSIDE_DOOR_HANDLES_WITH_TIMEOUT` | `Short` | — |
| `DOOR_LOCK_MODE_DOOR_UNSECURED_FOR_OUTSIDE_DOOR_HANDLES` | `Short` | — |
| `DOOR_LOCK_MODE_DOOR_UNSECURED_FOR_OUTSIDE_DOOR_HANDLES_WITH_TIMEOUT` | `Short` | — |
| `DOOR_LOCK_MODE_DOOR_UNSECURED_WITH_TIMEOUT` | `Short` | — |
| `doorLockMode` | `Short` | — |
