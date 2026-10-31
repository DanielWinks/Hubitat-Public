# NotificationSet

- **ID:** `api-hubitat-zwave-commands-notificationv8-notificationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv8.NotificationSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv7.NotificationSet`
- [NotificationSet](../protocols/api-hubitat-zwave-commands-notificationv7-notificationset.md)
- [NotificationSet](../protocols/api-hubitat-zwave-commands-notificationv7-notificationset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `NotificationSet` | `NotificationSet()` | Creates the command with its declared field defaults. |
| `NotificationSet` | `NotificationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_TYPE_HOME_MONITORING` | `Short` | — |
| `NOTIFICATION_TYPE_LIGHT_SENSOR` | `Short` | — |
| `NOTIFICATION_TYPE_PEST_CONTROL` | `Short` | — |
| `NOTIFICATION_TYPE_WATER_QUALITY_MONITORING` | `Short` | — |
