# LanguageReport

- **ID:** `api-hubitat-zwave-commands-languagev1-languagereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.languagev1.LanguageReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `LanguageReport` | `LanguageReport()` | Creates the command with its declared field defaults. |
| `LanguageReport` | `LanguageReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `country` | `Integer` | — |
| `language` | `Integer` | — |
