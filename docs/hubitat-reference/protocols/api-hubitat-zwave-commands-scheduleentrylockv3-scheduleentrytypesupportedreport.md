# ScheduleEntryTypeSupportedReport

- **ID:** `api-hubitat-zwave-commands-scheduleentrylockv3-scheduleentrytypesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.scheduleentrylockv3.ScheduleEntryTypeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.scheduleentrylockv2.ScheduleEntryTypeSupportedReport`
- [ScheduleEntryTypeSupportedReport](../protocols/api-hubitat-zwave-commands-scheduleentrylockv2-scheduleentrytypesupportedreport.md)
- [ScheduleEntryTypeSupportedReport](../protocols/api-hubitat-zwave-commands-scheduleentrylockv2-scheduleentrytypesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ScheduleEntryTypeSupportedReport` | `ScheduleEntryTypeSupportedReport()` | Creates the command with its declared field defaults. |
| `ScheduleEntryTypeSupportedReport` | `ScheduleEntryTypeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `numberOfSlotsDailyRepeating` | `Short` | — |
| `numberOfSlotsWeekDay` | `Short` | — |
| `numberOfSlotsYearDay` | `Short` | — |
