# NotificationSet

- **ID:** `api-hubitat-zwave-commands-notificationv4-notificationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv4.NotificationSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv3.NotificationSet`
- [NotificationSet](../protocols/api-hubitat-zwave-commands-notificationv3-notificationset.md)
- [NotificationSet](../protocols/api-hubitat-zwave-commands-notificationv3-notificationset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `NotificationSet` | `NotificationSet()` | Creates the command with its declared field defaults. |
| `NotificationSet` | `NotificationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_STATUS_NO_PENDING_NOTIFICATIONS` | `Short` | — |
| `NOTIFICATION_TYPE_APPLIANCE` | `Short` | — |
| `NOTIFICATION_TYPE_HOME_HEALTH` | `Short` | — |
