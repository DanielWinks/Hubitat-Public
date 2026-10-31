# MeterReport

- **ID:** `api-hubitat-zwave-commands-meterv1-meterreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv1.MeterReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMeterType` | `Short getMeterType()` | Returns the meter type value stored in bits 0 through 4 of properties1. Returns: meter type value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPrecision` | `Short getPrecision()` | Returns the precision value stored in bits 5 through 7 of properties2. Returns: precision value |
| `getScale` | `Short getScale()` | Returns the scale value stored in bits 3 through 4 of properties2. Returns: scale value |
| `getScaledMeterValue` | `BigDecimal getScaledMeterValue()` | Returns the scaled meter value value stored in preScaledValue. Returns: scaled meter value value |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 0 through 2 of properties2. Returns: size value |
| `MeterReport` | `MeterReport()` | Creates the command with its declared field defaults. |
| `MeterReport` | `MeterReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterReport` | `MeterReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `setMeterType` | `void setMeterType(Short value)` | Sets the meter type value used by this command. Parameters: value - meter type value to encode |
| `setPrecision` | `void setPrecision(Short value)` | Sets the precision value used by this command. Parameters: value - precision value to encode |
| `setScale` | `void setScale(Short value)` | Sets the scale value used by this command. Parameters: value - scale value to encode |
| `setScaledValue` | `void setScaledValue(BigDecimal value)` | Sets the scaled value value used by this command. Parameters: value - scaled value value to encode |
| `setSize` | `void setSize(Short value)` | Encodes size in bits 0 through 1 of properties2 and preserves the bits selected by 0xE7. Parameters: value - size value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `METER_TYPE_ELECTRIC_METER` | `short` | — |
| `METER_TYPE_GAS_METER` | `short` | — |
| `METER_TYPE_WATER_METER` | `short` | — |
| `meterValue` | `List<Short>` | — |
