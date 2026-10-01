# CredentialLearnReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentiallearnreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialLearnReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialLearnReport` | `CredentialLearnReport()` | Creates report. |
| `CredentialLearnReport` | `CredentialLearnReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `CredentialLearnReport` | `CredentialLearnReport(String payload)` | Parameters: payload - ZIP payload |
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialLearnStatus` | `Short` | — |
| `credentialLearnStepsRemaining` | `Short` | — |
| `credentialSlot` | `Integer` | — |
| `credentialType` | `Short` | — |
| `userUniqueIdentifier` | `Integer` | — |
