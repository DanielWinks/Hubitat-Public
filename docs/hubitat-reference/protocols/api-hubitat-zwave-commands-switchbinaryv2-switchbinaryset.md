# SwitchBinarySet

- **ID:** `api-hubitat-zwave-commands-switchbinaryv2-switchbinaryset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchbinaryv2.SwitchBinarySet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchbinaryv1.SwitchBinarySet`
- [SwitchBinarySet](../protocols/api-hubitat-zwave-commands-switchbinaryv1-switchbinaryset.md)
- [SwitchBinarySet](../protocols/api-hubitat-zwave-commands-switchbinaryv1-switchbinaryset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchBinarySet` | `SwitchBinarySet()` | Creates the command with its declared field defaults. |
| `SwitchBinarySet` | `SwitchBinarySet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Short` | — |
| `switchValue` | `Short` | — |
