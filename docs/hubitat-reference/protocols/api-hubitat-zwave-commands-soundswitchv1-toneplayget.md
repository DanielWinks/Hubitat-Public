# TonePlayGet

- **ID:** `api-hubitat-zwave-commands-soundswitchv1-toneplayget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.soundswitchv1.TonePlayGet`

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
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `TonePlayGet` | `TonePlayGet()` | Creates the command with its declared field defaults. |
| `TonePlayGet` | `TonePlayGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
