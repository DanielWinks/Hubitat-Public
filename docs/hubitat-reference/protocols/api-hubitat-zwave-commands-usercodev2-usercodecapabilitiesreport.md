# UserCodeCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-usercodev2-usercodecapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercodev2.UserCodeCapabilitiesReport`

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
| `UserCodeCapabilitiesReport` | `UserCodeCapabilitiesReport()` | Creates the command with its declared field defaults. |
| `UserCodeCapabilitiesReport` | `UserCodeCapabilitiesReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `UserCodeCapabilitiesReport` | `UserCodeCapabilitiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `supportedASCIIChars` | `String` | — |
| `supportedKeypadModes` | `List<Short>` | — |
| `supportedUserIDStatuses` | `List<Short>` | — |
| `supportsAdminCode` | `Boolean` | — |
| `supportsAdminCodeDeactivation` | `Boolean` | — |
| `supportsMultipleUserCodeReport` | `Boolean` | — |
| `supportsMultipleUserCodeSet` | `Boolean` | — |
| `supportsUserCodeChecksum` | `Boolean` | — |
