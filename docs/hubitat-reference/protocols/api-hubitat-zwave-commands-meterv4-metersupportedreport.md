# MeterSupportedReport

- **ID:** `api-hubitat-zwave-commands-meterv4-metersupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv4.MeterSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv3.MeterSupportedReport`
- [MeterSupportedReport](../protocols/api-hubitat-zwave-commands-meterv3-metersupportedreport.md)
- [MeterSupportedReport](../protocols/api-hubitat-zwave-commands-meterv3-metersupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getMoreScaleTypes` | `Boolean getMoreScaleTypes()` | Reports whether bit 7 of properties2 is set for more scale types. Returns: more scale types value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getRateType` | `Short getRateType()` | Returns the rate type value stored in bits 5 through 6 of properties1. Returns: rate type value |
| `getScaleSupported` | `Short getScaleSupported()` | Returns the scale supported value stored in bits 0 through 6 of properties2. Returns: scale supported value |
| `MeterSupportedReport` | `MeterSupportedReport()` | Creates the command with its declared field defaults. |
| `MeterSupportedReport` | `MeterSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterSupportedReport` | `MeterSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `setMoreScaleTypes` | `void setMoreScaleTypes(Boolean value)` | Sets the more scale types value used by this command. Parameters: value - more scale types value to encode |
| `setRateType` | `void setRateType(Short value)` | Sets the rate type value used by this command. Parameters: value - rate type value to encode |
| `setScaleSupported` | `void setScaleSupported(Short value)` | Encodes scale supported in bits 0 through 6 of properties2 and preserves the bits selected by 0x80. Parameters: value - scale supported value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `numberOfScaleSupportedBytes` | `Short` | — |
| `scaleSupportedBytes` | `List<Short>` | — |
