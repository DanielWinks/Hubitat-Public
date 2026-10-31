# BatteryHealthReport

- **ID:** `api-hubitat-zwave-commands-batteryv2-batteryhealthreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.batteryv2.BatteryHealthReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `BatteryHealthReport` | `BatteryHealthReport()` | Creates the command with its declared field defaults. |
| `BatteryHealthReport` | `BatteryHealthReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `BatteryHealthReport` | `BatteryHealthReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: null |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPrecision` | `Short getPrecision()` | Returns the precision value stored in bits 5 through 7 of properties1. Returns: precision value |
| `getScale` | `Short getScale()` | Returns the scale value stored in bits 3 through 4 of properties1. Returns: scale value |
| `getScaledBatteryTemperature` | `BigDecimal getScaledBatteryTemperature()` | Returns the scaled battery temperature value stored in parseValue. Returns: scaled battery temperature value |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 0 through 2 of properties1. Returns: size value |
| `setPrecision` | `void setPrecision(Short precision)` | Encodes precision in bits 0 through 2 of properties1 and preserves the bits selected by 0x1F. Parameters: precision - precision value to encode |
| `setScale` | `void setScale(Short scale)` | Sets the scale value used by this command. Parameters: scale - scale value to encode |
| `setScaledBatteryTemperature` | `void setScaledBatteryTemperature(BigDecimal scaledValue)` | Sets the scaled battery temperature value used by this command. Parameters: scaledValue - scaled battery temperature value to encode |
| `setSize` | `void setSize(Short size)` | Encodes size in bits 0 through 2 of properties1 and preserves the bits selected by 0xF8. Parameters: size - size value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `batteryTemperature` | `List<Short>` | — |
| `maximumCapacity` | `Short` | — |
