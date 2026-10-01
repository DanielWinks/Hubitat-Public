# MeterGet

- **ID:** `api-hubitat-zwave-commands-meterv4-meterget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv4.MeterGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv3.MeterGet`
- [MeterGet](../protocols/api-hubitat-zwave-commands-meterv3-meterget.md)
- [MeterGet](../protocols/api-hubitat-zwave-commands-meterv3-meterget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getRateType` | `Short getRateType()` | Returns the rate type value stored in bits 6 through 7 of properties1. Returns: rate type value |
| `MeterGet` | `MeterGet()` | Creates the command with its declared field defaults. |
| `MeterGet` | `MeterGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `setRateType` | `void setRateType(Short value)` | Sets the rate type value used by this command. Parameters: value - rate type value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `RATE_TYPE_EXPORT` | `short` | — |
| `RATE_TYPE_IMPORT` | `short` | — |
| `scale2` | `Short` | — |
