# CredentialGet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentialget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialGet` | `CredentialGet()` | Creates command. |
| `CredentialGet` | `CredentialGet(String payload)` | Parameters: payload - ZIP payload |
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getJSON` | `String getJSON()` | Returns: JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialSlot` | `Integer` | — |
| `credentialType` | `Short` | — |
| `userUniqueIdentifier` | `Integer` | — |
