# NotificationSupportedReport

- **ID:** `api-hubitat-zwave-commands-notificationv8-notificationsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv8.NotificationSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv7.NotificationSupportedReport`
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv7-notificationsupportedreport.md)
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv7-notificationsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getHomeMonitoring` | `Boolean getHomeMonitoring()` | Returns the home monitoring value calculated from this command fields. Returns: home monitoring value |
| `getLightSensor` | `Boolean getLightSensor()` | Returns the light sensor value calculated from this command fields. Returns: light sensor value |
| `getPestControl` | `Boolean getPestControl()` | Returns the pest control value calculated from this command fields. Returns: pest control value |
| `getWaterQualityMonitoring` | `Boolean getWaterQualityMonitoring()` | Returns the water quality monitoring value calculated from this command fields. Returns: water quality monitoring value |
| `NotificationSupportedReport` | `NotificationSupportedReport()` | Creates the command with its declared field defaults. |
| `NotificationSupportedReport` | `NotificationSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `NotificationSupportedReport` | `NotificationSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setHomeMonitoring` | `void setHomeMonitoring(Boolean value)` | Writes the home monitoring value to payload position 2. Parameters: value - home monitoring value to encode |
| `setLightSensor` | `void setLightSensor(Boolean value)` | Writes the light sensor value to payload position 2. Parameters: value - light sensor value to encode |
| `setPestControl` | `void setPestControl(Boolean value)` | Writes the pest control value to payload position 2. Parameters: value - pest control value to encode |
| `setWaterQualityMonitoring` | `void setWaterQualityMonitoring(Boolean value)` | Writes the water quality monitoring value to payload position 2. Parameters: value - water quality monitoring value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
