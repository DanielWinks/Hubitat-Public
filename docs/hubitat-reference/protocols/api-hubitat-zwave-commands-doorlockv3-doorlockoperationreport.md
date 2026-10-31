# DoorLockOperationReport

- **ID:** `api-hubitat-zwave-commands-doorlockv3-doorlockoperationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv3.DoorLockOperationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.doorlockv2.DoorLockOperationReport`
- [DoorLockOperationReport](../protocols/api-hubitat-zwave-commands-doorlockv2-doorlockoperationreport.md)
- [DoorLockOperationReport](../protocols/api-hubitat-zwave-commands-doorlockv2-doorlockoperationreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockOperationReport` | `DoorLockOperationReport()` | Creates the command with its declared field defaults. |
| `DoorLockOperationReport` | `DoorLockOperationReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `DoorLockOperationReport` | `DoorLockOperationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 7 values also keeps the declared field |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Short` | — |
| `targetDoorLockMode` | `Short` | — |
