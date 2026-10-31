# UserCredentialAssociationReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-usercredentialassociationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserCredentialAssociationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |
| `UserCredentialAssociationReport` | `UserCredentialAssociationReport()` | Creates report. |
| `UserCredentialAssociationReport` | `UserCredentialAssociationReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `UserCredentialAssociationReport` | `UserCredentialAssociationReport(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialSlot` | `Integer` | — |
| `credentialType` | `Short` | — |
| `destinationUserUniqueIdentifier` | `Integer` | — |
| `userCredentialAssociationStatus` | `Short` | — |
