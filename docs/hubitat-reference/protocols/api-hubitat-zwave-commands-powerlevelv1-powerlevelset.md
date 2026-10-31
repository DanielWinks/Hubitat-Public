# PowerlevelSet

- **ID:** `api-hubitat-zwave-commands-powerlevelv1-powerlevelset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.powerlevelv1.PowerlevelSet`

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
| `PowerlevelSet` | `PowerlevelSet()` | Creates the command with its declared field defaults. |
| `PowerlevelSet` | `PowerlevelSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `POWER_LEVEL_MINUS1DBM` | `Short` | — |
| `POWER_LEVEL_MINUS2DBM` | `Short` | — |
| `POWER_LEVEL_MINUS3DBM` | `Short` | — |
| `POWER_LEVEL_MINUS4DBM` | `Short` | — |
| `POWER_LEVEL_MINUS5DBM` | `Short` | — |
| `POWER_LEVEL_MINUS6DBM` | `Short` | — |
| `POWER_LEVEL_MINUS7DBM` | `Short` | — |
| `POWER_LEVEL_MINUS8DBM` | `Short` | — |
| `POWER_LEVEL_MINUS9DBM` | `Short` | — |
| `POWER_LEVEL_NORMALPOWER` | `Short` | — |
| `powerLevel` | `Short` | — |
| `timeout` | `Short` | — |
