# SwitchMultilevelStartLevelChange

- **ID:** `api-hubitat-zwave-commands-switchmultilevelv2-switchmultilevelstartlevelchange`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelStartLevelChange`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStartLevelChange`
- [SwitchMultilevelStartLevelChange](../protocols/api-hubitat-zwave-commands-switchmultilevelv1-switchmultilevelstartlevelchange.md)
- [SwitchMultilevelStartLevelChange](../protocols/api-hubitat-zwave-commands-switchmultilevelv1-switchmultilevelstartlevelchange.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchMultilevelStartLevelChange` | `SwitchMultilevelStartLevelChange()` | Creates the command with its declared field defaults. |
| `SwitchMultilevelStartLevelChange` | `SwitchMultilevelStartLevelChange(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `dimmingDuration` | `Short` | — |
