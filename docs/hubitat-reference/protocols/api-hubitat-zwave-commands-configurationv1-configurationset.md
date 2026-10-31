# ConfigurationSet

- **ID:** `api-hubitat-zwave-commands-configurationv1-configurationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.configurationv1.ConfigurationSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ConfigurationSet` | `ConfigurationSet()` | Creates the command with its declared field defaults. |
| `ConfigurationSet` | `ConfigurationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getDefaultValue` | `Boolean getDefaultValue()` | Reports whether bit 7 of properties1 is set for default value. Returns: default value value |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getScaledConfigurationValue` | `BigInteger getScaledConfigurationValue()` | Returns the scaled configuration value value stored in BigInteger. Returns: scaled configuration value value |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 0 through 2 of properties1. Returns: size value |
| `setDefaultValue` | `void setDefaultValue(Boolean value)` | Sets the default value value used by this command. Parameters: value - default value value to encode |
| `setScaledConfigurationValue` | `void setScaledConfigurationValue(BigInteger value)` | Sets the scaled configuration value value used by this command. Parameters: value - scaled configuration value value to encode |
| `setSize` | `void setSize(Short value)` | Sets the size value used by this command. Parameters: value - size value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `configurationValue` | `List<Short>` | — |
| `parameterNumber` | `Short` | — |
