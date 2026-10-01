# MeterSupportedReport

- **ID:** `api-hubitat-zwave-commands-meterv2-metersupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv2.MeterSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMeterReset` | `Boolean getMeterReset()` | Reports whether bit 7 of properties1 is set for meter reset. Returns: meter reset value |
| `getMeterType` | `Short getMeterType()` | Returns the meter type value stored in bits 0 through 4 of properties1. Returns: meter type value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getScaleSupported` | `Short getScaleSupported()` | Returns the scale supported value stored in bits 0 through 3 of properties2. Returns: scale supported value |
| `MeterSupportedReport` | `MeterSupportedReport()` | Creates the command with its declared field defaults. |
| `MeterSupportedReport` | `MeterSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterSupportedReport` | `MeterSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `setMeterReset` | `void setMeterReset(Boolean value)` | Sets the meter reset value used by this command. Parameters: value - meter reset value to encode |
| `setMeterType` | `void setMeterType(Short value)` | Encodes meter type in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - meter type value to encode |
| `setScaleSupported` | `void setScaleSupported(Short value)` | Encodes scale supported in bits 0 through 3 of properties2 and preserves the bits selected by 0xF0. Parameters: value - scale supported value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
