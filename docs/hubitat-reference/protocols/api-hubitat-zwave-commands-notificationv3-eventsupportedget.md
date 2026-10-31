# EventSupportedGet

- **ID:** `api-hubitat-zwave-commands-notificationv3-eventsupportedget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv3.EventSupportedGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EventSupportedGet` | `EventSupportedGet()` | Creates the command with its declared field defaults. |
| `EventSupportedGet` | `EventSupportedGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `NOTIFICATION_TYPE_ACCESS_CONTROL` | `Short` | — |
| `NOTIFICATION_TYPE_BURGLAR` | `Short` | — |
| `NOTIFICATION_TYPE_CLOCK` | `Short` | — |
| `NOTIFICATION_TYPE_CO` | `Short` | — |
| `NOTIFICATION_TYPE_CO2` | `Short` | — |
| `NOTIFICATION_TYPE_EMERGENCY` | `Short` | — |
| `NOTIFICATION_TYPE_FIRST` | `Short` | — |
| `NOTIFICATION_TYPE_HEAT` | `Short` | — |
| `NOTIFICATION_TYPE_POWER_MANAGEMENT` | `Short` | — |
| `NOTIFICATION_TYPE_RESERVED0` | `Short` | — |
| `NOTIFICATION_TYPE_SMOKE` | `Short` | — |
| `NOTIFICATION_TYPE_SYSTEM` | `Short` | — |
| `NOTIFICATION_TYPE_WATER` | `Short` | — |
| `notificationType` | `Short` | — |
