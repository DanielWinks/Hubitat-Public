# BatteryReport

- **ID:** `api-hubitat-zwave-commands-batteryv3-batteryreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.batteryv3.BatteryReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.batteryv2.BatteryReport`
- [BatteryReport](../protocols/api-hubitat-zwave-commands-batteryv2-batteryreport.md)
- [BatteryReport](../protocols/api-hubitat-zwave-commands-batteryv2-batteryreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `BatteryReport` | `BatteryReport()` | Creates the command with its declared field defaults. |
| `BatteryReport` | `BatteryReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `BatteryReport` | `BatteryReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getLowTemperatureStatus` | `Boolean getLowTemperatureStatus()` | Reports whether bit 1 of properties2 is set for low temperature status. Returns: low temperature status value |
| `setLowTemperatureStatus` | `void setLowTemperatureStatus(Boolean lowTemperatureStatus)` | Sets the low temperature status value used by this command. Parameters: lowTemperatureStatus - low temperature status value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
