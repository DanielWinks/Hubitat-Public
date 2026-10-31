# CredentialReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentialreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialReport` | `CredentialReport()` | Creates report. |
| `CredentialReport` | `CredentialReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `CredentialReport` | `CredentialReport(String payload)` | Parameters: payload - ZIP payload |
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialData` | `List<Short>` | — |
| `credentialModifierNodeId` | `Integer` | — |
| `credentialModifierType` | `Short` | — |
| `credentialReadBack` | `Boolean` | — |
| `credentialReportType` | `Short` | — |
| `credentialSlot` | `Integer` | — |
| `credentialType` | `Short` | — |
| `nextCredentialSlot` | `Integer` | — |
| `nextCredentialType` | `Short` | — |
| `userUniqueIdentifier` | `Integer` | — |
