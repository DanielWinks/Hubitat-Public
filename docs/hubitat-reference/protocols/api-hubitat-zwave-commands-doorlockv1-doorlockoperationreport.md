# DoorLockOperationReport

- **ID:** `api-hubitat-zwave-commands-doorlockv1-doorlockoperationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv1.DoorLockOperationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockOperationReport` | `DoorLockOperationReport()` | Creates the command with its declared field defaults. |
| `DoorLockOperationReport` | `DoorLockOperationReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `DoorLockOperationReport` | `DoorLockOperationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 5 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getInsideDoorHandlesMode` | `Short getInsideDoorHandlesMode()` | Returns the inside door handles mode value stored in bits 0 through 3 of properties1. Returns: inside door handles mode value |
| `getOutsideDoorHandlesMode` | `Short getOutsideDoorHandlesMode()` | Returns the outside door handles mode value stored in bits 4 through 7 of properties1. Returns: outside door handles mode value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setInsideDoorHandlesMode` | `void setInsideDoorHandlesMode(Short value)` | Encodes inside door handles mode in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - inside door handles mode value to encode |
| `setOutsideDoorHandlesMode` | `void setOutsideDoorHandlesMode(Short value)` | Sets the outside door handles mode value used by this command. Parameters: value - outside door handles mode value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

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
| `doorCondition` | `Short` | — |
| `doorLockMode` | `Short` | — |
| `lockTimeoutMinutes` | `Short` | — |
| `lockTimeoutSeconds` | `Short` | — |
