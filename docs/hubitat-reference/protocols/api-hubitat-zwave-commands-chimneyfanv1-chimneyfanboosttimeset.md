# ChimneyFanBoostTimeSet

- **ID:** `api-hubitat-zwave-commands-chimneyfanv1-chimneyfanboosttimeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.chimneyfanv1.ChimneyFanBoostTimeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ChimneyFanBoostTimeSet` | `ChimneyFanBoostTimeSet()` | Creates the command with its declared field defaults. |
| `ChimneyFanBoostTimeSet` | `ChimneyFanBoostTimeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `time` | `Short` | — |
