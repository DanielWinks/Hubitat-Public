# SwitchAllSet

- **ID:** `api-hubitat-zwave-commands-switchallv1-switchallset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchallv1.SwitchAllSet`

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
| `SwitchAllSet` | `SwitchAllSet()` | Creates the command with its declared field defaults. |
| `SwitchAllSet` | `SwitchAllSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `mode` | `Short` | — |
| `MODE_EXCLUDED_FROM_THE_ALL_OFF_FUNCTIONALITY_BUT_NOT_ALL_ON` | `Short` | — |
| `MODE_EXCLUDED_FROM_THE_ALL_ON_ALL_OFF_FUNCTIONALITY` | `Short` | — |
| `MODE_EXCLUDED_FROM_THE_ALL_ON_FUNCTIONALITY_BUT_NOT_ALL_OFF` | `Short` | — |
| `MODE_INCLUDED_IN_THE_ALL_ON_ALL_OFF_FUNCTIONALITY` | `Short` | — |
