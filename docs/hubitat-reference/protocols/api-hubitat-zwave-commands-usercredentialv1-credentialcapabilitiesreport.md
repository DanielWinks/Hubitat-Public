# CredentialCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentialcapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialCapabilitiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialCapabilitiesReport` | `CredentialCapabilitiesReport()` | Creates report. |
| `CredentialCapabilitiesReport` | `CredentialCapabilitiesReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `CredentialCapabilitiesReport` | `CredentialCapabilitiesReport(String payload)` | Parameters: payload - ZIP payload |
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `adminCodeDeactivationSupport` | `Boolean` | — |
| `adminCodeSupport` | `Boolean` | — |
| `credentialCapabilities` | `List<Map<String, Object>>` | — |
| `credentialChecksumSupport` | `Boolean` | — |
