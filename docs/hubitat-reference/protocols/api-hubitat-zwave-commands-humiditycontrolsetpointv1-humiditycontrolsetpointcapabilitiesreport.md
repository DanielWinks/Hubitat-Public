# HumidityControlSetpointCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolsetpointv1-humiditycontrolsetpointcapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolsetpointv1.HumidityControlSetpointCapabilitiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMaximumPrecision` | `Short getMaximumPrecision()` | Returns the maximum precision value stored in bits 5 through 7 of properties3. Returns: maximum precision value |
| `getMaximumScale` | `Short getMaximumScale()` | Returns the maximum scale value stored in bits 3 through 4 of properties3. Returns: maximum scale value |
| `getMaximumScaledValue` | `BigDecimal getMaximumScaledValue()` | Returns the maximum scaled value value stored in parseValue. Returns: maximum scaled value value |
| `getMaximumSize` | `Short getMaximumSize()` | Returns the maximum size value stored in bits 0 through 2 of properties3. Returns: maximum size value |
| `getMinimumPrecision` | `Short getMinimumPrecision()` | Returns the minimum precision value stored in bits 5 through 7 of properties2. Returns: minimum precision value |
| `getMinimumScale` | `Short getMinimumScale()` | Returns the minimum scale value stored in bits 3 through 4 of properties2. Returns: minimum scale value |
| `getMinimumScaledValue` | `BigDecimal getMinimumScaledValue()` | Returns the minimum scaled value value stored in parseValue. Returns: minimum scaled value value |
| `getMinimumSize` | `Short getMinimumSize()` | Returns the minimum size value stored in bits 0 through 2 of properties2. Returns: minimum size value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSetpointType` | `Short getSetpointType()` | Returns the setpoint type value stored in bits 0 through 3 of properties1. Returns: setpoint type value |
| `HumidityControlSetpointCapabilitiesReport` | `HumidityControlSetpointCapabilitiesReport()` | Creates the command with its declared field defaults. |
| `HumidityControlSetpointCapabilitiesReport` | `HumidityControlSetpointCapabilitiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `setMaximumPrecision` | `void setMaximumPrecision(Short value)` | Sets the maximum precision value used by this command. Parameters: value - maximum precision value to encode |
| `setMaximumScale` | `void setMaximumScale(Short value)` | Sets the maximum scale value used by this command. Parameters: value - maximum scale value to encode |
| `setMaximumScaledValue` | `void setMaximumScaledValue(BigDecimal scaledValue)` | Sets the maximum scaled value value used by this command. Parameters: scaledValue - maximum scaled value value to encode |
| `setMaximumSize` | `void setMaximumSize(Short value)` | Encodes maximum size in bits 0 through 2 of properties3 and preserves the bits selected by 0xF8. Parameters: value - maximum size value to encode |
| `setMinimumPrecision` | `void setMinimumPrecision(Short value)` | Sets the minimum precision value used by this command. Parameters: value - minimum precision value to encode |
| `setMinimumScale` | `void setMinimumScale(Short value)` | Sets the minimum scale value used by this command. Parameters: value - minimum scale value to encode |
| `setMinimumScaledValue` | `void setMinimumScaledValue(BigDecimal scaledValue)` | Sets the minimum scaled value value used by this command. Parameters: scaledValue - minimum scaled value value to encode |
| `setMinimumSize` | `void setMinimumSize(Short value)` | Encodes minimum size in bits 0 through 2 of properties2 and preserves the bits selected by 0xF8. Parameters: value - minimum size value to encode |
| `setSetpointType` | `void setSetpointType(Short value)` | Encodes setpoint type in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - setpoint type value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `maximumValue` | `List<Short>` | — |
| `minimumValue` | `List<Short>` | — |
| `SCALE_ABSOLUTE` | `short` | — |
| `SCALE_PERCENTAGE` | `short` | — |
| `SETPOINT_TYPE_DEHUMIDIFIER` | `short` | — |
| `SETPOINT_TYPE_HUMIDIFIER` | `short` | — |
