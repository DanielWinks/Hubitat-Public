# SwitchColorSupportedReport

- **ID:** `api-hubitat-zwave-commands-switchcolorv1-switchcolorsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchcolorv1.SwitchColorSupportedReport`

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
| `SwitchColorSupportedReport` | `SwitchColorSupportedReport()` | Creates the command with its declared field defaults. |
| `SwitchColorSupportedReport` | `SwitchColorSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SwitchColorSupportedReport` | `SwitchColorSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `amber` | `boolean` | — |
| `blue` | `boolean` | — |
| `coldWhite` | `boolean` | — |
| `cyan` | `boolean` | — |
| `green` | `boolean` | — |
| `index` | `boolean` | — |
| `purple` | `boolean` | — |
| `red` | `boolean` | — |
| `warmWhite` | `boolean` | — |
