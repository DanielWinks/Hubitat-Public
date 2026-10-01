# AlarmGet

- **ID:** `api-hubitat-zwave-commands-alarmv2-alarmget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.alarmv2.AlarmGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.alarmv1.AlarmGet`
- [AlarmGet](../protocols/api-hubitat-zwave-commands-alarmv1-alarmget.md)
- [AlarmGet](../protocols/api-hubitat-zwave-commands-alarmv1-alarmget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AlarmGet` | `AlarmGet()` | Creates the command with its declared field defaults. |
| `AlarmGet` | `AlarmGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `alarmType` | `Short` | — |
| `ZWAVE_ALARM_TYPE_ACCESS_CONTROL` | `Short` | — |
| `ZWAVE_ALARM_TYPE_BURGLAR` | `Short` | — |
| `ZWAVE_ALARM_TYPE_CLOCK` | `Short` | — |
| `ZWAVE_ALARM_TYPE_CO` | `Short` | — |
| `ZWAVE_ALARM_TYPE_CO2` | `Short` | — |
| `ZWAVE_ALARM_TYPE_EMERGENCY` | `Short` | — |
| `ZWAVE_ALARM_TYPE_FIRST` | `Short` | — |
| `ZWAVE_ALARM_TYPE_HEAT` | `Short` | — |
| `ZWAVE_ALARM_TYPE_POWER_MANAGEMENT` | `Short` | — |
| `ZWAVE_ALARM_TYPE_RESERVED0` | `Short` | — |
| `ZWAVE_ALARM_TYPE_SMOKE` | `Short` | — |
| `ZWAVE_ALARM_TYPE_SYSTEM` | `Short` | — |
| `ZWAVE_ALARM_TYPE_WATER` | `Short` | — |
| `zwaveAlarmType` | `Short` | — |
