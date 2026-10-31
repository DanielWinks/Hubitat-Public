# WindowCoveringStopLevelChange

- **ID:** `api-hubitat-zwave-commands-windowcoveringv1-windowcoveringstoplevelchange`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.windowcoveringv1.WindowCoveringStopLevelChange`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `WindowCoveringStopLevelChange` | `WindowCoveringStopLevelChange()` | Creates the command with its declared field defaults. |
| `WindowCoveringStopLevelChange` | `WindowCoveringStopLevelChange(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `parameterId` | `Short` | — |
