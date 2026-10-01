# EventSupportedGet

- **ID:** `api-hubitat-zwave-commands-notificationv7-eventsupportedget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv7.EventSupportedGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.notificationv6.EventSupportedGet`
- [EventSupportedGet](../protocols/api-hubitat-zwave-commands-notificationv6-eventsupportedget.md)
- [EventSupportedGet](../protocols/api-hubitat-zwave-commands-notificationv6-eventsupportedget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EventSupportedGet` | `EventSupportedGet()` | Creates the command with its declared field defaults. |
| `EventSupportedGet` | `EventSupportedGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_TYPE_GAS_ALARM` | `Short` | — |
| `NOTIFICATION_TYPE_IRRIGATION` | `Short` | — |
| `NOTIFICATION_TYPE_WATER_VALVE` | `Short` | — |
| `NOTIFICATION_TYPE_WEATHER_ALARM` | `Short` | — |
