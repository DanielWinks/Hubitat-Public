# CredentialChecksumGet

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-credentialchecksumget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.CredentialChecksumGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CredentialChecksumGet` | `CredentialChecksumGet()` | Creates an empty command. |
| `CredentialChecksumGet` | `CredentialChecksumGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getJSON` | `String getJSON()` | Returns: Z-Wave JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialType` | `Short` | — |
