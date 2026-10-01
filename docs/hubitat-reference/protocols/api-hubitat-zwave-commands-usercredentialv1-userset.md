# UserSet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-userset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserSet`

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
| `UserSet` | `UserSet()` | Creates command. |
| `UserSet` | `UserSet(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialRule` | `Short` | — |
| `expiringTimeoutMinutes` | `Integer` | — |
| `operationType` | `Short` | — |
| `userActiveState` | `Boolean` | — |
| `userName` | `String` | — |
| `userNameEncoding` | `Short` | — |
| `userType` | `Short` | — |
| `userUniqueIdentifier` | `Integer` | — |
