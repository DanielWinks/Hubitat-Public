# ProtectionSupportedReport

- **ID:** `api-hubitat-zwave-commands-protectionv2-protectionsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.protectionv2.ProtectionSupportedReport`

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
| `ProtectionSupportedReport` | `ProtectionSupportedReport()` | Creates the command with its declared field defaults. |
| `ProtectionSupportedReport` | `ProtectionSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 5 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `exclusiveControl` | `Boolean` | — |
| `localProtectionState` | `Integer` | — |
| `rfProtectionState` | `Integer` | — |
| `timeout` | `Boolean` | — |
