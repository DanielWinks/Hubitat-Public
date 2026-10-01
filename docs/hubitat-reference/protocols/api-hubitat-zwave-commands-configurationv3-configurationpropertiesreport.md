# ConfigurationPropertiesReport

- **ID:** `api-hubitat-zwave-commands-configurationv3-configurationpropertiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.configurationv3.ConfigurationPropertiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ConfigurationPropertiesReport` | `ConfigurationPropertiesReport()` | Creates the command with its declared field defaults. |
| `ConfigurationPropertiesReport` | `ConfigurationPropertiesReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ConfigurationPropertiesReport` | `ConfigurationPropertiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getFormat` | `Short getFormat()` | Returns the format value stored in bits 3 through 5 of properties1. Returns: format value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 0 through 2 of properties1. Returns: size value |
| `setFormat` | `void setFormat(Short value)` | Sets the format value used by this command. Parameters: value - format value to encode |
| `setSize` | `void setSize(Short value)` | Sets the size value used by this command. Parameters: value - size value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `defaultValue` | `BigInteger` | — |
| `FORMAT_BIT_FIELD` | `short` | — |
| `FORMAT_ENUMERATED` | `short` | — |
| `FORMAT_SIGNED_INTEGER` | `short` | — |
| `FORMAT_UNSIGNED_INTEGER` | `short` | — |
| `maxValue` | `BigInteger` | — |
| `minValue` | `BigInteger` | — |
| `nextParameterNumber` | `Integer` | — |
| `parameterNumber` | `Integer` | — |
