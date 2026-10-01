# MeterSupportedReport

- **ID:** `api-hubitat-zwave-commands-meterv3-metersupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv3.MeterSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv2.MeterSupportedReport`
- [MeterSupportedReport](../protocols/api-hubitat-zwave-commands-meterv2-metersupportedreport.md)
- [MeterSupportedReport](../protocols/api-hubitat-zwave-commands-meterv2-metersupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getScaleSupported` | `Short getScaleSupported()` | Returns the scale supported value stored in properties2. Returns: scale supported value |
| `MeterSupportedReport` | `MeterSupportedReport()` | Creates the command with its declared field defaults. |
| `MeterSupportedReport` | `MeterSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterSupportedReport` | `MeterSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setScaleSupported` | `void setScaleSupported(Short value)` | Sets the scale supported value used by this command. Parameters: value - scale supported value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
