# ChimneyFanAlarmLogReport

- **ID:** `api-hubitat-zwave-commands-chimneyfanv1-chimneyfanalarmlogreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.chimneyfanv1.ChimneyFanAlarmLogReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ChimneyFanAlarmLogReport` | `ChimneyFanAlarmLogReport()` | Creates the command with its declared field defaults. |
| `ChimneyFanAlarmLogReport` | `ChimneyFanAlarmLogReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `alarmStillActive1` | `Boolean` | — |
| `alarmStillActive2` | `Boolean` | — |
| `alarmStillActive3` | `Boolean` | — |
| `alarmStillActive4` | `Boolean` | — |
| `alarmStillActive5` | `Boolean` | — |
| `alarmTemperatureExceeded1` | `Boolean` | — |
| `alarmTemperatureExceeded2` | `Boolean` | — |
| `alarmTemperatureExceeded3` | `Boolean` | — |
| `alarmTemperatureExceeded4` | `Boolean` | — |
| `alarmTemperatureExceeded5` | `Boolean` | — |
| `externalAlarm1` | `Boolean` | — |
| `externalAlarm2` | `Boolean` | — |
| `externalAlarm3` | `Boolean` | — |
| `externalAlarm4` | `Boolean` | — |
| `externalAlarm5` | `Boolean` | — |
| `sensorError1` | `Boolean` | — |
| `sensorError2` | `Boolean` | — |
| `sensorError3` | `Boolean` | — |
| `sensorError4` | `Boolean` | — |
| `sensorError5` | `Boolean` | — |
