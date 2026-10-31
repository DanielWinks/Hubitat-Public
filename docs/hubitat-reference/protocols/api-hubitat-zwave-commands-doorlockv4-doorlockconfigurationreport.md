# DoorLockConfigurationReport

- **ID:** `api-hubitat-zwave-commands-doorlockv4-doorlockconfigurationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv4.DoorLockConfigurationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.doorlockv3.DoorLockConfigurationReport`
- [DoorLockConfigurationReport](../protocols/api-hubitat-zwave-commands-doorlockv3-doorlockconfigurationreport.md)
- [DoorLockConfigurationReport](../protocols/api-hubitat-zwave-commands-doorlockv3-doorlockconfigurationreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockConfigurationReport` | `DoorLockConfigurationReport()` | Creates the command with its declared field defaults. |
| `DoorLockConfigurationReport` | `DoorLockConfigurationReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `DoorLockConfigurationReport` | `DoorLockConfigurationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 9 values also keeps the declared field |
| `getBTB` | `Boolean getBTB()` | Reports whether bit 1 of properties2 is set for b tb. Returns: b tb value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getTA` | `Boolean getTA()` | Reports whether bit 0 of properties2 is set for t a. Returns: t a value |
| `setBTB` | `void setBTB(Boolean value)` | Sets the b tb value used by this command. Parameters: value - b tb value to encode |
| `setTA` | `void setTA(Boolean value)` | Sets the t a value used by this command. Parameters: value - t a value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `autoRelockTime` | `Integer` | — |
| `holdAndReleaseTime` | `Integer` | — |
