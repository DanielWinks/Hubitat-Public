# SwitchToggleMultilevelGet

- **ID:** `api-hubitat-zwave-commands-switchtogglemultilevelv1-switchtogglemultilevelget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchtogglemultilevelv1.SwitchToggleMultilevelGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `SwitchToggleMultilevelGet` | `SwitchToggleMultilevelGet()` | Creates the command with its declared field defaults. |
| `SwitchToggleMultilevelGet` | `SwitchToggleMultilevelGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
