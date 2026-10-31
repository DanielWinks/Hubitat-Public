# HumidityControlSetpointScaleSupportedReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolsetpointv1-humiditycontrolsetpointscalesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolsetpointv1.HumidityControlSetpointScaleSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAbsolute` | `Boolean getAbsolute()` | Reports whether bit 1 of scaleBitmask is set for absolute. Returns: absolute value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPercentage` | `Boolean getPercentage()` | Reports whether bit 0 of scaleBitmask is set for percentage. Returns: percentage value |
| `getScaleBitmask` | `Short getScaleBitmask()` | Returns the scale bitmask value stored in bits 0 through 3 of properties1. Returns: scale bitmask value |
| `HumidityControlSetpointScaleSupportedReport` | `HumidityControlSetpointScaleSupportedReport()` | Creates the command with its declared field defaults. |
| `HumidityControlSetpointScaleSupportedReport` | `HumidityControlSetpointScaleSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setAbsolute` | `void setAbsolute(Boolean value)` | Sets the absolute value used by this command. Parameters: value - absolute value to encode |
| `setPercentage` | `void setPercentage(Boolean value)` | Sets the percentage value used by this command. Parameters: value - percentage value to encode |
| `setScaleBitmask` | `Short setScaleBitmask(Short value)` | Encodes the scale bitmask in the low four bits of properties1 and preserves its high four bits. Parameters: value - scale bitmask value whose low four bits are stored. Returns: upd |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
