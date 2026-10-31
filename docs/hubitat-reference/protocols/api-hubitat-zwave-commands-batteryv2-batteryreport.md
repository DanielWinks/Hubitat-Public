# BatteryReport

- **ID:** `api-hubitat-zwave-commands-batteryv2-batteryreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.batteryv2.BatteryReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.batteryv1.BatteryReport`
- [BatteryReport](../protocols/api-hubitat-zwave-commands-batteryv1-batteryreport.md)
- [BatteryReport](../protocols/api-hubitat-zwave-commands-batteryv1-batteryreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `BatteryReport` | `BatteryReport()` | Creates the command with its declared field defaults. |
| `BatteryReport` | `BatteryReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `BatteryReport` | `BatteryReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `getBackupBattery` | `Boolean getBackupBattery()` | Reports whether bit 4 of properties1 is set for backup battery. Returns: backup battery value |
| `getChargingStatus` | `Short getChargingStatus()` | Returns the charging status value stored in bits 6 through 7 of properties1. Returns: charging status value |
| `getDisconnected` | `Boolean getDisconnected()` | Reports whether bit 0 of properties2 is set for disconnected. Returns: disconnected value |
| `getLowFluid` | `Boolean getLowFluid()` | Reports whether bit 2 of properties1 is set for low fluid. Returns: low fluid value |
| `getOverheating` | `Boolean getOverheating()` | Reports whether bit 3 of properties1 is set for overheating. Returns: overheating value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getRechargeable` | `Boolean getRechargeable()` | Reports whether bit 5 of properties1 is set for rechargeable. Returns: rechargeable value |
| `getReplaceRecharge` | `Short getReplaceRecharge()` | Returns the replace recharge value stored in bits 0 through 1 of properties1. Returns: replace recharge value |
| `setBackupBattery` | `void setBackupBattery(Boolean backupBattery)` | Sets the backup battery value used by this command. Parameters: backupBattery - backup battery value to encode |
| `setChargingStatus` | `void setChargingStatus(Short chargingStatus)` | Sets the charging status value used by this command. Parameters: chargingStatus - charging status value to encode |
| `setDisconnected` | `void setDisconnected(Boolean disconnected)` | Sets the disconnected value used by this command. Parameters: disconnected - disconnected value to encode |
| `setLowFluid` | `void setLowFluid(Boolean lowFluid)` | Sets the low fluid value used by this command. Parameters: lowFluid - low fluid value to encode |
| `setOverheating` | `void setOverheating(Boolean overheating)` | Sets the overheating value used by this command. Parameters: overheating - overheating value to encode |
| `setRechargeable` | `void setRechargeable(Boolean rechargeable)` | Sets the rechargeable value used by this command. Parameters: rechargeable - rechargeable value to encode |
| `setReplaceRecharge` | `void setReplaceRecharge(Short replaceRecharge)` | Encodes replace recharge in bits 0 through 1 of properties1 and preserves the bits selected by 0xFB. Parameters: replaceRecharge - replace recharge value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `CHARGING_STATUS_CHARGING` | `short` | — |
| `CHARGING_STATUS_DISCHARGING` | `short` | — |
| `CHARGING_STATUS_MAINTAINING` | `short` | — |
