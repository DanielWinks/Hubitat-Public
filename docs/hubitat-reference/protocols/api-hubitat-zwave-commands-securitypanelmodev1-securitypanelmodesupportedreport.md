# SecurityPanelModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-securitypanelmodev1-securitypanelmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.securitypanelmodev1.SecurityPanelModeSupportedReport`

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
| `SecurityPanelModeSupportedReport` | `SecurityPanelModeSupportedReport()` | Creates the command with its declared field defaults. |
| `SecurityPanelModeSupportedReport` | `SecurityPanelModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `supportedModeBitMask` | `Integer` | — |
