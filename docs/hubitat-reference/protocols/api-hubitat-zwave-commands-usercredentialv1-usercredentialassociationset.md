# UserCredentialAssociationSet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-usercredentialassociationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserCredentialAssociationSet`

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
| `UserCredentialAssociationSet` | `UserCredentialAssociationSet()` | Creates command. |
| `UserCredentialAssociationSet` | `UserCredentialAssociationSet(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialSlot` | `Integer` | — |
| `credentialType` | `Short` | — |
| `destinationUserUniqueIdentifier` | `Integer` | — |
