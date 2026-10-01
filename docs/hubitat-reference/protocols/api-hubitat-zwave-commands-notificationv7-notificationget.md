# NotificationGet

- **ID:** `api-hubitat-zwave-commands-notificationv7-notificationget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv7.NotificationGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv6.NotificationGet`
- [NotificationGet](../protocols/api-hubitat-zwave-commands-notificationv6-notificationget.md)
- [NotificationGet](../protocols/api-hubitat-zwave-commands-notificationv6-notificationget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `NotificationGet` | `NotificationGet()` | Creates the command with its declared field defaults. |
| `NotificationGet` | `NotificationGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_TYPE_GAS_ALARM` | `Short` | — |
| `NOTIFICATION_TYPE_IRRIGATION` | `Short` | — |
| `NOTIFICATION_TYPE_WATER_VALVE` | `Short` | — |
| `NOTIFICATION_TYPE_WEATHER_ALARM` | `Short` | — |
