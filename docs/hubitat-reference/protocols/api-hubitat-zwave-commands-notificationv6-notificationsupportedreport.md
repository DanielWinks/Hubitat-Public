# NotificationSupportedReport

- **ID:** `api-hubitat-zwave-commands-notificationv6-notificationsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv6.NotificationSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv5.NotificationSupportedReport`
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv5-notificationsupportedreport.md)
- [NotificationSupportedReport](../protocols/api-hubitat-zwave-commands-notificationv5-notificationsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getSiren` | `Boolean getSiren()` | Returns the siren value calculated from this command fields. Returns: siren value |
| `NotificationSupportedReport` | `NotificationSupportedReport()` | Creates the command with its declared field defaults. |
| `NotificationSupportedReport` | `NotificationSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `NotificationSupportedReport` | `NotificationSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setSiren` | `void setSiren(Boolean value)` | Writes the siren value to payload position 1. Parameters: value - siren value to encode |
