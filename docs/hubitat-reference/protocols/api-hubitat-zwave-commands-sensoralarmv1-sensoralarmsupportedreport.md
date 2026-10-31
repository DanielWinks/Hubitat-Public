# SensorAlarmSupportedReport

- **ID:** `api-hubitat-zwave-commands-sensoralarmv1-sensoralarmsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensoralarmv1.SensorAlarmSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SensorAlarmSupportedReport` | `SensorAlarmSupportedReport()` | Creates the command with its declared field defaults. |
| `SensorAlarmSupportedReport` | `SensorAlarmSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `bitMask` | `List<Short>` | — |
| `numberOfBitMasks` | `Short` | — |
