# KexGet

- **ID:** `api-hubitat-zwave-commands-security2v1-kexget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.security2v1.KexGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Represents the KexGet Z-Wave command frame. |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `KexGet` | `KexGet()` | Creates the command with its declared field defaults. |
| `KexGet` | `KexGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
