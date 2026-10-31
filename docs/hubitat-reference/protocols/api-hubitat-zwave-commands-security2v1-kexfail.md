# KexFail

- **ID:** `api-hubitat-zwave-commands-security2v1-kexfail`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.security2v1.KexFail`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `KexFail` | `KexFail()` | Creates the command with its declared field defaults. |
| `KexFail` | `KexFail(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `failType` | `Short` | — |
| `KEX_FAIL_AUTH` | `Short` | — |
| `KEX_FAIL_CANCEL` | `Short` | — |
| `KEX_FAIL_DECRYPT` | `Short` | — |
| `KEX_FAIL_KEX_CURVES` | `Short` | — |
| `KEX_FAIL_KEX_KEY` | `Short` | — |
| `KEX_FAIL_KEX_SCHEME` | `Short` | — |
| `KEX_FAIL_KEY_GET` | `Short` | — |
| `KEX_FAIL_KEY_REPORT` | `Short` | — |
| `KEX_FAIL_KEY_VERIFY` | `Short` | — |
