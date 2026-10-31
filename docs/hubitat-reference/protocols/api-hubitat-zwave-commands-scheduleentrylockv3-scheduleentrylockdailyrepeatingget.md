# ScheduleEntryLockDailyRepeatingGet

- **ID:** `api-hubitat-zwave-commands-scheduleentrylockv3-scheduleentrylockdailyrepeatingget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.scheduleentrylockv3.ScheduleEntryLockDailyRepeatingGet`

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
| `ScheduleEntryLockDailyRepeatingGet` | `ScheduleEntryLockDailyRepeatingGet()` | Creates the command with its declared field defaults. |
| `ScheduleEntryLockDailyRepeatingGet` | `ScheduleEntryLockDailyRepeatingGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `scheduleSlotId` | `Short` | — |
| `userIdentifier` | `Short` | — |
