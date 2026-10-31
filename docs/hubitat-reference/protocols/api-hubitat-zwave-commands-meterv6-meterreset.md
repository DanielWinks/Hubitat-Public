# MeterReset

- **ID:** `api-hubitat-zwave-commands-meterv6-meterreset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv6.MeterReset`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv5.MeterReset`
- [MeterReset](../protocols/api-hubitat-zwave-commands-meterv5-meterreset.md)
- [MeterReset](../protocols/api-hubitat-zwave-commands-meterv5-meterreset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getMeterType` | `Short getMeterType()` | Returns the meter type value stored in bits 0 through 4 of properties1. Returns: meter type value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 5 through 7 of properties1. Returns: size value |
| `MeterReset` | `MeterReset()` | Creates the command with its declared field defaults. |
| `MeterReset` | `MeterReset(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setMeterType` | `void setMeterType(Short value)` | Encodes meter type in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - meter type value to encode |
| `setSize` | `void setSize(Short value)` | Sets the size value used by this command. Parameters: value - size value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `meterValue` | `List<Short>` | — |
