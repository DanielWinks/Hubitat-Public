# WakeUpIntervalSet

- **ID:** `api-hubitat-zwave-commands-wakeupv1-wakeupintervalset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.wakeupv1.WakeUpIntervalSet`

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
| `WakeUpIntervalSet` | `WakeUpIntervalSet()` | Creates the command with its declared field defaults. |
| `WakeUpIntervalSet` | `WakeUpIntervalSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `nodeid` | `Short` | — |
| `seconds` | `Integer` | — |
