# WindowCoveringSupportedReport

- **ID:** `api-hubitat-zwave-commands-windowcoveringv1-windowcoveringsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.windowcoveringv1.WindowCoveringSupportedReport`

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
| `getSupportedParameters` | `List<Short> getSupportedParameters()` | Returns the supported parameters value stored in retVal. Returns: supported parameters value |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `WindowCoveringSupportedReport` | `WindowCoveringSupportedReport()` | Creates the command with its declared field defaults. |
| `WindowCoveringSupportedReport` | `WindowCoveringSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `WindowCoveringSupportedReport` | `WindowCoveringSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `numberOfParameterMaskBytes` | `Short` | — |
| `parameterMask` | `List<Short>` | — |
