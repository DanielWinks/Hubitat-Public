# SwitchMultilevelSupportedReport

- **ID:** `api-hubitat-zwave-commands-switchmultilevelv3-switchmultilevelsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: null |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPrimarySwitchType` | `Short getPrimarySwitchType()` | Returns the primary switch type value stored in bits 0 through 4 of properties1. Returns: primary switch type value |
| `getSecondarySwitchType` | `Short getSecondarySwitchType()` | Returns the secondary switch type value stored in bits 0 through 4 of properties2. Returns: secondary switch type value |
| `setPrimarySwitchType` | `void setPrimarySwitchType(Short value)` | Sets the primary switch type value used by this command. Parameters: value - primary switch type value to encode |
| `setSecondarySwitchType` | `void setSecondarySwitchType(Short value)` | Sets the secondary switch type value used by this command. Parameters: value - secondary switch type value to encode |
| `SwitchMultilevelSupportedReport` | `SwitchMultilevelSupportedReport()` | Creates the command with its declared field defaults. |
| `SwitchMultilevelSupportedReport` | `SwitchMultilevelSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
