# NotificationReport

- **ID:** `api-hubitat-zwave-commands-notificationv3-notificationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv3.NotificationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getEventParametersLength` | `Short getEventParametersLength()` | Returns the event parameters length value stored in bits 0 through 4 of properties1. Returns: event parameters length value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSequence` | `Boolean getSequence()` | Reports whether bit 7 of properties1 is set for sequence. Returns: sequence value |
| `NotificationReport` | `NotificationReport()` | Creates the command with its declared field defaults. |
| `NotificationReport` | `NotificationReport(List<Map<String, Object>> payload)` | Decodes Z-Wave JS values, converting derived contact states to Notification event numbers. Parameters: payload - list of value maps; unrelated metadata is ignored. Supported fields |
| `NotificationReport` | `NotificationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `setEventParametersLength` | `void setEventParametersLength(Short value)` | Encodes event parameters length in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - event parameters length value to encode |
| `setSequence` | `void setSequence(Boolean value)` | Sets the sequence value used by this command. Parameters: value - sequence value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `event` | `Short` | — |
| `eventParameter` | `List<Short>` | — |
| `NOTIFICATION_STATUS_OFF` | `Short` | — |
| `NOTIFICATION_STATUS_ON` | `Short` | — |
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
| `notificationStatus` | `Short` | — |
| `notificationType` | `Short` | — |
| `properties1` | `Short` | — |
| `reserved` | `Short` | — |
| `sequenceNumber` | `Short` | — |
| `v1AlarmLevel` | `Short` | — |
| `v1AlarmType` | `Short` | — |
