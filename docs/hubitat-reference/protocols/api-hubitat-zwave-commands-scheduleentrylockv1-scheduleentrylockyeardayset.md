# ScheduleEntryLockYearDaySet

- **ID:** `api-hubitat-zwave-commands-scheduleentrylockv1-scheduleentrylockyeardayset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.scheduleentrylockv1.ScheduleEntryLockYearDaySet`

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
| `ScheduleEntryLockYearDaySet` | `ScheduleEntryLockYearDaySet()` | Creates the command with its declared field defaults. |
| `ScheduleEntryLockYearDaySet` | `ScheduleEntryLockYearDaySet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 13 values also keeps the declared fiel |

## Properties

| Name | Type | Description |
|---|---|---|
| `scheduleSlotId` | `Short` | — |
| `setAction` | `Short` | — |
| `startDay` | `Short` | — |
| `startHour` | `Short` | — |
| `startMinute` | `Short` | — |
| `startMonth` | `Short` | — |
| `startYear` | `Short` | — |
| `stopDay` | `Short` | — |
| `stopHour` | `Short` | — |
| `stopMinute` | `Short` | — |
| `stopMonth` | `Short` | — |
| `stopYear` | `Short` | — |
| `userIdentifier` | `Short` | — |
