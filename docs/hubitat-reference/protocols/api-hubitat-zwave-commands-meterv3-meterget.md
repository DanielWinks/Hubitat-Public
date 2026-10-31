# MeterGet

- **ID:** `api-hubitat-zwave-commands-meterv3-meterget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.meterv3.MeterGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.meterv2.MeterGet`
- [MeterGet](../protocols/api-hubitat-zwave-commands-meterv2-meterget.md)
- [MeterGet](../protocols/api-hubitat-zwave-commands-meterv2-meterget.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getScale` | `Short getScale()` | Returns the scale value stored in bits 3 through 5 of properties1. Returns: scale value |
| `MeterGet` | `MeterGet()` | Creates the command with its declared field defaults. |
| `MeterGet` | `MeterGet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setScale` | `void setScale(Short value)` | Sets the scale value used by this command. Parameters: value - scale value to encode |
