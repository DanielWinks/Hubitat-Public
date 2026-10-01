# SwitchColorStartLevelChange

- **ID:** `api-hubitat-zwave-commands-switchcolorv3-switchcolorstartlevelchange`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchcolorv3.SwitchColorStartLevelChange`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchcolorv2.SwitchColorStartLevelChange`
- [SwitchColorStartLevelChange](../protocols/api-hubitat-zwave-commands-switchcolorv2-switchcolorstartlevelchange.md)
- [SwitchColorStartLevelChange](../protocols/api-hubitat-zwave-commands-switchcolorv2-switchcolorstartlevelchange.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchColorStartLevelChange` | `SwitchColorStartLevelChange()` | Creates the command with its declared field defaults. |
| `SwitchColorStartLevelChange` | `SwitchColorStartLevelChange(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `colorComponentId` | `short` | — |
| `dimmingDuration` | `short` | — |
| `ignoreStartLevel` | `boolean` | — |
| `startLevel` | `short` | — |
| `upDown` | `boolean` | — |
