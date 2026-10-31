# ThermostatSetpointReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv1-thermostatsetpointreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPrecision` | `Short getPrecision()` | Returns the precision value stored in bits 5 through 7 of properties2. Returns: precision value |
| `getScale` | `Short getScale()` | Returns the scale value stored in bits 3 through 4 of properties2. Returns: scale value |
| `getScaledValue` | `BigDecimal getScaledValue()` | Returns the scaled value value stored in parseValue. Returns: scaled value value |
| `getSetpointType` | `Short getSetpointType()` | Returns the setpoint type value stored in bits 0 through 3 of properties1. Returns: setpoint type value |
| `getSize` | `Short getSize()` | Returns the size value stored in bits 0 through 2 of properties2. Returns: size value |
| `setPrecision` | `void setPrecision(Short value)` | Sets the precision value used by this command. Parameters: value - precision value to encode |
| `setScale` | `void setScale(Short value)` | Sets the scale value used by this command. Parameters: value - scale value to encode |
| `setScaledValue` | `void setScaledValue(BigDecimal scaledValue)` | Sets the scaled value value used by this command. Parameters: scaledValue - scaled value value to encode |
| `setSetpointType` | `void setSetpointType(Short value)` | Encodes setpoint type in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - setpoint type value to encode |
| `setSize` | `void setSize(Short value)` | Encodes size in bits 0 through 2 of properties2 and preserves the bits selected by 0xF8. Parameters: value - size value to encode |
| `ThermostatSetpointReport` | `ThermostatSetpointReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointReport` | `ThermostatSetpointReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatSetpointReport` | `ThermostatSetpointReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `SETPOINT_TYPE_AUTO_CHANGEOVER` | `short` | — |
| `SETPOINT_TYPE_COOLING_1` | `short` | — |
| `SETPOINT_TYPE_DRY_AIR` | `short` | — |
| `SETPOINT_TYPE_FURNACE` | `short` | — |
| `SETPOINT_TYPE_HEATING_1` | `short` | — |
| `SETPOINT_TYPE_MOIST_AIR` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED1` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED2` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED3` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED4` | `short` | — |
| `value` | `List<Short>` | — |
