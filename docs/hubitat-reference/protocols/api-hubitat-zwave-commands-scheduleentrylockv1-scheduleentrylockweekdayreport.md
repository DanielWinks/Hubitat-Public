# ScheduleEntryLockWeekDayReport

- **ID:** `api-hubitat-zwave-commands-scheduleentrylockv1-scheduleentrylockweekdayreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.scheduleentrylockv1.ScheduleEntryLockWeekDayReport`

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
| `ScheduleEntryLockWeekDayReport` | `ScheduleEntryLockWeekDayReport()` | Creates the command with its declared field defaults. |
| `ScheduleEntryLockWeekDayReport` | `ScheduleEntryLockWeekDayReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 7 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `dayOfWeek` | `Short` | — |
| `scheduleSlotId` | `Short` | — |
| `startHour` | `Short` | — |
| `startMinute` | `Short` | — |
| `stopHour` | `Short` | — |
| `stopMinute` | `Short` | — |
| `userIdentifier` | `Short` | — |
