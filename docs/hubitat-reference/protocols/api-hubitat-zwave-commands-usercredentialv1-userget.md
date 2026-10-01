# UserGet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-userget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getJSON` | `String getJSON()` | Returns: JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |
| `UserGet` | `UserGet()` | Creates command. |
| `UserGet` | `UserGet(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `userUniqueIdentifier` | `Integer` | — |
