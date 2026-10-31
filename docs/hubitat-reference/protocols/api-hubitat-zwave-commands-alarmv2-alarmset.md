# AlarmSet

- **ID:** `api-hubitat-zwave-commands-alarmv2-alarmset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.alarmv2.AlarmSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AlarmSet` | `AlarmSet()` | Creates the command with its declared field defaults. |
| `AlarmSet` | `AlarmSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
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
| `zwaveAlarmStatus` | `Short` | — |
| `zwaveAlarmType` | `Short` | — |
