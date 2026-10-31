# CredentialChecksumReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentialchecksumreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialChecksumReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialChecksumReport` | `CredentialChecksumReport()` | Creates an empty report. |
| `CredentialChecksumReport` | `CredentialChecksumReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `CredentialChecksumReport` | `CredentialChecksumReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialChecksum` | `Integer` | — |
| `credentialType` | `Short` | — |
