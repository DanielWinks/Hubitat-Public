# NotificationSupportedReport

- **ID:** `api-hubitat-zwave-commands-notificationv4-notificationsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv4.NotificationSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv3.NotificationSupportedReport`
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv3-notificationsupportedreport.md)
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv3-notificationsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAppliance` | `Boolean getAppliance()` | Returns the appliance value calculated from this command fields. Returns: appliance value |
| `getHomeHealth` | `Boolean getHomeHealth()` | Returns the home health value calculated from this command fields. Returns: home health value |
| `NotificationSupportedReport` | `NotificationSupportedReport()` | Creates the command with its declared field defaults. |
| `NotificationSupportedReport` | `NotificationSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `NotificationSupportedReport` | `NotificationSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setAppliance` | `void setAppliance(Boolean value)` | Writes the appliance value to payload position 1. Parameters: value - appliance value to encode |
| `setHomeHealth` | `void setHomeHealth(Boolean value)` | Writes the home health value to payload position 1. Parameters: value - home health value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
