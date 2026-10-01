# MeterReport

- **ID:** `api-hubitat-zwave-commands-meterv4-meterreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv4.MeterReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv3.MeterReport`
- [MeterReport](../protocols/api-hubitat-zwave-commands-meterv3-meterreport.md)
- [MeterReport](../protocols/api-hubitat-zwave-commands-meterv3-meterreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `MeterReport` | `MeterReport()` | Creates the command with its declared field defaults. |
| `MeterReport` | `MeterReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MeterReport` | `MeterReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `scale2` | `Short` | — |
