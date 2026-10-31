# MeterReport

- **ID:** `api-hubitat-zwave-commands-meterv3-meterreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv3.MeterReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv2.MeterReport`
- [MeterReport](../protocols/api-hubitat-zwave-commands-meterv2-meterreport.md)
- [MeterReport](../protocols/api-hubitat-zwave-commands-meterv2-meterreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getScale` | `Short getScale()` | Returns the scale value stored in bits 3 through 4 of properties2. Returns: scale value |
| `MeterReport` | `MeterReport()` | Creates the command with its declared field defaults. |
| `MeterReport` | `MeterReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterReport` | `MeterReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setScale` | `void setScale(Short value)` | Sets the scale value used by this command. Parameters: value - scale value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
