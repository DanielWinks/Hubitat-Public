# SwitchColorSet

- **ID:** `api-hubitat-zwave-commands-switchcolorv3-switchcolorset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchcolorv3.SwitchColorSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchcolorv2.SwitchColorSet`
- [SwitchColorSet](../protocols/api-hubitat-zwave-commands-switchcolorv2-switchcolorset.md)
- [SwitchColorSet](../protocols/api-hubitat-zwave-commands-switchcolorv2-switchcolorset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchColorSet` | `SwitchColorSet()` | Creates the command with its declared field defaults. |
| `SwitchColorSet` | `SwitchColorSet(Map options)` | Creates a Switch Color Set command from component/value pairs. Parameters: options - supported component inputs: colorComponents (Map) - each key/value pair is appended as a compon |
| `SwitchColorSet` | `SwitchColorSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `colorComponentBytes` | `List<Short>` | — |
| `colorComponentCount` | `short` | — |
| `colorComponents` | `Map` | — |
| `dimmingDuration` | `short` | — |
