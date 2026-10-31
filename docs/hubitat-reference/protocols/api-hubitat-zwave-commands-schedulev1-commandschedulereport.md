# CommandScheduleReport

- **ID:** `api-hubitat-zwave-commands-schedulev1-commandschedulereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.schedulev1.CommandScheduleReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `activeId` | `Short` | — |
| `durationByte` | `Integer` | — |
| `durationType` | `Short` | — |
| `numberOfCmdToFollow` | `Short` | — |
| `reportsToFollow` | `Short` | — |
| `res51` | `Boolean` | — |
| `scheduleId` | `Short` | — |
| `startDayOfMonth` | `Short` | — |
| `startHour` | `Short` | — |
| `startMinute` | `Short` | — |
| `startMonth` | `Short` | — |
| `startWeekday` | `Short` | — |
| `startYear` | `Short` | — |
| `userIdentifier` | `Short` | — |
