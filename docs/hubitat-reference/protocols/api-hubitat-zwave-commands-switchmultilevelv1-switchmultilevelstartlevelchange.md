# SwitchMultilevelStartLevelChange

- **ID:** `api-hubitat-zwave-commands-switchmultilevelv1-switchmultilevelstartlevelchange`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStartLevelChange`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getIgnoreStartLevel` | `Boolean getIgnoreStartLevel()` | Reports whether bit 5 of properties1 is set for ignore start level. Returns: ignore start level value |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getUpDown` | `Boolean getUpDown()` | Reports whether bit 6 of properties1 is set for up down. Returns: up down value |
| `setIgnoreStartLevel` | `void setIgnoreStartLevel(Boolean value)` | Sets the ignore start level value used by this command. Parameters: value - ignore start level value to encode |
| `setUpDown` | `void setUpDown(Boolean value)` | Sets the up down value used by this command. Parameters: value - up down value to encode |
| `SwitchMultilevelStartLevelChange` | `SwitchMultilevelStartLevelChange()` | Creates the command with its declared field defaults. |
| `SwitchMultilevelStartLevelChange` | `SwitchMultilevelStartLevelChange(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `startLevel` | `Short` | — |
