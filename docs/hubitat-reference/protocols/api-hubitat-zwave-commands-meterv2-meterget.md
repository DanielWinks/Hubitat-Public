# MeterGet

- **ID:** `api-hubitat-zwave-commands-meterv2-meterget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv2.MeterGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv1.MeterGet`
- [MeterGet](../protocols/api-hubitat-zwave-commands-meterv1-meterget.md)
- [MeterGet](../protocols/api-hubitat-zwave-commands-meterv1-meterget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getScale` | `Short getScale()` | Returns the scale value stored in bits 3 through 4 of properties1. Returns: scale value |
| `MeterGet` | `MeterGet()` | Creates the command with its declared field defaults. |
| `MeterGet` | `MeterGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setScale` | `void setScale(Short value)` | Sets the scale value used by this command. Parameters: value - scale value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
