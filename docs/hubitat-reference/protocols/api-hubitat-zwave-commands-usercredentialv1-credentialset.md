# CredentialSet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentialset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialSet` | `CredentialSet()` | Creates command. |
| `CredentialSet` | `CredentialSet(String payload)` | Parameters: payload - ZIP payload |
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getJSON` | `String getJSON()` | Returns: JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialData` | `List<Short>` | — |
| `credentialSlot` | `Integer` | — |
| `credentialType` | `Short` | — |
| `operationType` | `Short` | — |
| `userUniqueIdentifier` | `Integer` | — |
