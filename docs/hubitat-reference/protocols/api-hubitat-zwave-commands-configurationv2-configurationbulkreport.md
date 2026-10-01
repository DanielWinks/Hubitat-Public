# ConfigurationBulkReport

- **ID:** `api-hubitat-zwave-commands-configurationv2-configurationbulkreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.configurationv2.ConfigurationBulkReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ConfigurationBulkReport` | `ConfigurationBulkReport()` | Creates the command with its declared field defaults. |
| `ConfigurationBulkReport` | `ConfigurationBulkReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 5 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getDefaultValue` | `Boolean getDefaultValue()` | Reports whether bit 7 of properties1 is set for default value. Returns: default value value |
| `getHandshake` | `Boolean getHandshake()` | Reports whether bit 6 of properties1 is set for handshake. Returns: handshake value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getScaledConfigurationValues` | `List<BigInteger> getScaledConfigurationValues()` | Returns the scaled configuration values value stored in retList. Returns: scaled configuration values value |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 0 through 2 of properties1. Returns: size value |
| `setDefaultValue` | `void setDefaultValue(Boolean value)` | Sets the default value value used by this command. Parameters: value - default value value to encode |
| `setHandshake` | `void setHandshake(Boolean value)` | Sets the handshake value used by this command. Parameters: value - handshake value to encode |
| `setScaledConfigurationValues` | `void setScaledConfigurationValues(List<BigInteger> values)` | Sets the scaled configuration values value used by this command. Parameters: values - scaled configuration values value to encode |
| `setSize` | `void setSize(Short value)` | Sets the size value used by this command. Parameters: value - size value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `configurationValues` | `List<List<Short>>` | — |
| `numberOfParameters` | `Short` | — |
| `parameterOffset` | `Integer` | — |
| `reportsToFollow` | `Short` | — |
