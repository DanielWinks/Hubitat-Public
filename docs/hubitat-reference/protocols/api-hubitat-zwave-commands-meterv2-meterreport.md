# MeterReport

- **ID:** `api-hubitat-zwave-commands-meterv2-meterreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv2.MeterReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv1.MeterReport`
- [MeterReport](../protocols/api-hubitat-zwave-commands-meterv1-meterreport.md)
- [MeterReport](../protocols/api-hubitat-zwave-commands-meterv1-meterreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getRateType` | `Short getRateType()` | Returns the rate type value stored in bits 5 through 6 of properties1. Returns: rate type value |
| `getScaledPreviousMeterValue` | `BigDecimal getScaledPreviousMeterValue()` | Returns the scaled previous meter value value stored in parseValue. Returns: scaled previous meter value value |
| `MeterReport` | `MeterReport()` | Creates the command with its declared field defaults. |
| `MeterReport` | `MeterReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterReport` | `MeterReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `setRateType` | `void setRateType(Short value)` | Sets the rate type value used by this command. Parameters: value - rate type value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `deltaTime` | `Integer` | — |
| `previousMeterValue` | `List<Short>` | — |
