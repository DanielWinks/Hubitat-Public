# PowerlevelTestNodeSet

- **ID:** `api-hubitat-zwave-commands-powerlevelv1-powerleveltestnodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.powerlevelv1.PowerlevelTestNodeSet`

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
| `PowerlevelTestNodeSet` | `PowerlevelTestNodeSet()` | Creates the command with its declared field defaults. |
| `PowerlevelTestNodeSet` | `PowerlevelTestNodeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

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
| `testFrameCount` | `Integer` | — |
| `testNodeid` | `Short` | — |
