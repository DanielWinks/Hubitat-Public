# AlarmReport

- **ID:** `api-hubitat-zwave-commands-alarmv2-alarmreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.alarmv2.AlarmReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.alarmv1.AlarmReport`
- [AlarmReport](../protocols/api-hubitat-zwave-commands-alarmv1-alarmreport.md)
- [AlarmReport](../protocols/api-hubitat-zwave-commands-alarmv1-alarmreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AlarmReport` | `AlarmReport()` | Creates the command with its declared field defaults. |
| `AlarmReport` | `AlarmReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `AlarmReport` | `AlarmReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `alarmLevel` | `Short` | — |
| `alarmType` | `Short` | — |
| `eventParameter` | `List<Short>` | — |
| `numberOfEventParameters` | `Short` | — |
| `zensorNetSourceNodeId` | `Short` | — |
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
| `zwaveAlarmEvent` | `Short` | — |
| `zwaveAlarmStatus` | `Short` | — |
| `zwaveAlarmType` | `Short` | — |
