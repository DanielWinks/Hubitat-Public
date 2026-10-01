# NotificationReport

- **ID:** `api-hubitat-zwave-commands-notificationv6-notificationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv6.NotificationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv5.NotificationReport`
- [NotificationReport](../protocols/api-hubitat-zwave-commands-notificationv5-notificationreport.md)
- [NotificationReport](../protocols/api-hubitat-zwave-commands-notificationv5-notificationreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `NotificationReport` | `NotificationReport()` | Creates the command with its declared field defaults. |
| `NotificationReport` | `NotificationReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `NotificationReport` | `NotificationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_TYPE_SIREN` | `Short` | — |
