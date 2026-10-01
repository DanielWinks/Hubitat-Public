# Security2NonceReport

- **ID:** `api-hubitat-zwave-commands-security2v1-security2noncereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.security2v1.Security2NonceReport`

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
| `Security2NonceReport` | `Security2NonceReport()` | Creates the command with its declared field defaults. |
| `Security2NonceReport` | `Security2NonceReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `MOS` | `Boolean` | — |
| `REI` | `List<Short>` | — |
| `sequenceNumber` | `Short` | — |
| `SOS` | `Boolean` | — |
