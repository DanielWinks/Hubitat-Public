# NotificationSupportedReport

- **ID:** `api-hubitat-zwave-commands-notificationv7-notificationsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv7.NotificationSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv6.NotificationSupportedReport`
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv6-notificationsupportedreport.md)
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv6-notificationsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getGasAlarm` | `Boolean getGasAlarm()` | Returns the gas alarm value calculated from this command fields. Returns: gas alarm value |
| `getIrrigation` | `Boolean getIrrigation()` | Returns the irrigation value calculated from this command fields. Returns: irrigation value |
| `getWaterValve` | `Boolean getWaterValve()` | Returns the water valve value calculated from this command fields. Returns: water valve value |
| `getWeatherAlarm` | `Boolean getWeatherAlarm()` | Returns the weather alarm value calculated from this command fields. Returns: weather alarm value |
| `NotificationSupportedReport` | `NotificationSupportedReport()` | Creates the command with its declared field defaults. |
| `NotificationSupportedReport` | `NotificationSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `NotificationSupportedReport` | `NotificationSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setGasAlarm` | `void setGasAlarm(Boolean value)` | Writes the gas alarm value to payload position 2. Parameters: value - gas alarm value to encode |
| `setIrrigation` | `void setIrrigation(Boolean value)` | Writes the irrigation value to payload position 2. Parameters: value - irrigation value to encode |
| `setWaterValve` | `void setWaterValve(Boolean value)` | Writes the water valve value to payload position 1. Parameters: value - water valve value to encode |
| `setWeatherAlarm` | `void setWeatherAlarm(Boolean value)` | Writes the weather alarm value to payload position 2. Parameters: value - weather alarm value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
