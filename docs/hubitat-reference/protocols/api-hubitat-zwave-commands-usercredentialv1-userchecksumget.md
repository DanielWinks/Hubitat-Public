# UserChecksumGet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-userchecksumget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserChecksumGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getJSON` | `String getJSON()` | Returns: Z-Wave JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway payload |
| `UserChecksumGet` | `UserChecksumGet()` | Creates an empty command. |
| `UserChecksumGet` | `UserChecksumGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `userUniqueIdentifier` | `Integer` | — |
