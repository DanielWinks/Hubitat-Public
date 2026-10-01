# NotificationSet

- **ID:** `api-hubitat-zwave-commands-notificationv7-notificationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv7.NotificationSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv6.NotificationSet`
- [NotificationSet](../protocols/api-hubitat-zwave-commands-notificationv6-notificationset.md)
- [NotificationSet](../protocols/api-hubitat-zwave-commands-notificationv6-notificationset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `NotificationSet` | `NotificationSet()` | Creates the command with its declared field defaults. |
| `NotificationSet` | `NotificationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_TYPE_GAS_ALARM` | `Short` | — |
| `NOTIFICATION_TYPE_IRRIGATION` | `Short` | — |
| `NOTIFICATION_TYPE_WATER_VALVE` | `Short` | — |
| `NOTIFICATION_TYPE_WEATHER_ALARM` | `Short` | — |
