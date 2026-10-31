# ScheduleEntryLockDailyRepeatingSet

- **ID:** `api-hubitat-zwave-commands-scheduleentrylockv3-scheduleentrylockdailyrepeatingset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.scheduleentrylockv3.ScheduleEntryLockDailyRepeatingSet`

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
| `ScheduleEntryLockDailyRepeatingSet` | `ScheduleEntryLockDailyRepeatingSet()` | Creates the command with its declared field defaults. |
| `ScheduleEntryLockDailyRepeatingSet` | `ScheduleEntryLockDailyRepeatingSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `durationHour` | `Short` | — |
| `durationMinute` | `Short` | — |
| `scheduleSlotId` | `Short` | — |
| `setAction` | `Short` | — |
| `startHour` | `Short` | — |
| `startMinute` | `Short` | — |
| `userIdentifier` | `Short` | — |
| `weekDayBitmask` | `Short` | — |
