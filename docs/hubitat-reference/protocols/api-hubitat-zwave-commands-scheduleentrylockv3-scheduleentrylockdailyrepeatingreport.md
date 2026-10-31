# ScheduleEntryLockDailyRepeatingReport

- **ID:** `api-hubitat-zwave-commands-scheduleentrylockv3-scheduleentrylockdailyrepeatingreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.scheduleentrylockv3.ScheduleEntryLockDailyRepeatingReport`

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
| `ScheduleEntryLockDailyRepeatingReport` | `ScheduleEntryLockDailyRepeatingReport()` | Creates the command with its declared field defaults. |
| `ScheduleEntryLockDailyRepeatingReport` | `ScheduleEntryLockDailyRepeatingReport(String payload)` | Assigns the first seven decoded payload values to this command's fields in wire order. throws: IndexOutOfBoundsException if the decoded payload has fewer than seven values, includi |

## Properties

| Name | Type | Description |
|---|---|---|
| `durationHour` | `Short` | — |
| `durationMinute` | `Short` | — |
| `scheduleSlotId` | `Short` | — |
| `startHour` | `Short` | — |
| `startMinute` | `Short` | — |
| `userIdentifier` | `Short` | — |
| `weekDayBitmask` | `Short` | — |
