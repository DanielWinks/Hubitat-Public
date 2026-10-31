# TonePlaySet

- **ID:** `api-hubitat-zwave-commands-soundswitchv2-toneplayset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.soundswitchv2.TonePlaySet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.soundswitchv1.TonePlaySet`
- [TonePlaySet](../protocols/api-hubitat-zwave-commands-soundswitchv1-toneplayset.md)
- [TonePlaySet](../protocols/api-hubitat-zwave-commands-soundswitchv1-toneplayset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `TonePlaySet` | `TonePlaySet()` | Creates the command with its declared field defaults. |
| `TonePlaySet` | `TonePlaySet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `volume` | `Short` | — |
