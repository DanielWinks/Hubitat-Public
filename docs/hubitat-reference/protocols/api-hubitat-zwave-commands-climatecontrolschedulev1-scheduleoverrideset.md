# ScheduleOverrideSet

- **ID:** `api-hubitat-zwave-commands-climatecontrolschedulev1-scheduleoverrideset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.climatecontrolschedulev1.ScheduleOverrideSet`

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
| `ScheduleOverrideSet` | `ScheduleOverrideSet()` | Creates the command with its declared field defaults. |
| `ScheduleOverrideSet` | `ScheduleOverrideSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `OVERRIDE_STATE_NO_OVERRIDE` | `Short` | — |
| `OVERRIDE_STATE_PERMANENT_OVERRIDE` | `Short` | — |
| `OVERRIDE_STATE_RESERVED3` | `Short` | — |
| `OVERRIDE_STATE_TEMPORARY_OVERRIDE` | `Short` | — |
| `overrideState` | `Short` | — |
| `overrideType` | `Short` | — |
