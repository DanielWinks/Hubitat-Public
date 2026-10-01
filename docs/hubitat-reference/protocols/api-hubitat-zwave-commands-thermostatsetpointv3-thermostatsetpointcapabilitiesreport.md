# ThermostatSetpointCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv3-thermostatsetpointcapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointCapabilitiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMaxPrecision` | `Short getMaxPrecision()` | Returns the max precision value stored in bits 5 through 7 of properties3. Returns: max precision value |
| `getMaxScale` | `Short getMaxScale()` | Returns the max scale value stored in bits 3 through 4 of properties3. Returns: max scale value |
| `getMaxScaledValue` | `BigDecimal getMaxScaledValue()` | Returns the max scaled value value stored in parseValue. Returns: max scaled value value |
| `getMaxSize` | `Short getMaxSize()` | Returns the max size value stored in bits 0 through 2 of properties3. Returns: max size value |
| `getMinPrecision` | `Short getMinPrecision()` | Returns the min precision value stored in bits 5 through 7 of properties2. Returns: min precision value |
| `getMinScale` | `Short getMinScale()` | Returns the min scale value stored in bits 3 through 4 of properties2. Returns: min scale value |
| `getMinScaledValue` | `BigDecimal getMinScaledValue()` | Returns the min scaled value value stored in parseValue. Returns: min scaled value value |
| `getMinSize` | `Short getMinSize()` | Returns the min size value stored in bits 0 through 2 of properties2. Returns: min size value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSetpointType` | `Short getSetpointType()` | Returns the setpoint type value stored in bits 0 through 3 of properties1. Returns: setpoint type value |
| `setMaxPrecision` | `void setMaxPrecision(Short value)` | Sets the max precision value used by this command. Parameters: value - max precision value to encode |
| `setMaxScale` | `void setMaxScale(Short value)` | Sets the max scale value used by this command. Parameters: value - max scale value to encode |
| `setMaxScaledValue` | `void setMaxScaledValue(BigDecimal scaledValue)` | Sets the max scaled value value used by this command. Parameters: scaledValue - max scaled value value to encode |
| `setMaxSize` | `void setMaxSize(Short value)` | Encodes max size in bits 0 through 2 of properties3 and preserves the bits selected by 0xF8. Parameters: value - max size value to encode |
| `setMinPrecision` | `void setMinPrecision(Short value)` | Sets the min precision value used by this command. Parameters: value - min precision value to encode |
| `setMinScale` | `void setMinScale(Short value)` | Sets the min scale value used by this command. Parameters: value - min scale value to encode |
| `setMinScaledValue` | `void setMinScaledValue(BigDecimal scaledValue)` | Sets the min scaled value value used by this command. Parameters: scaledValue - min scaled value value to encode |
| `setMinSize` | `void setMinSize(Short value)` | Encodes min size in bits 0 through 2 of properties2 and preserves the bits selected by 0xF8. Parameters: value - min size value to encode |
| `setSetpointType` | `void setSetpointType(Short value)` | Encodes setpoint type in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - setpoint type value to encode |
| `ThermostatSetpointCapabilitiesReport` | `ThermostatSetpointCapabilitiesReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointCapabilitiesReport` | `ThermostatSetpointCapabilitiesReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatSetpointCapabilitiesReport` | `ThermostatSetpointCapabilitiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `maxValue` | `List<Short>` | — |
| `minValue` | `List<Short>` | — |
| `SETPOINT_TYPE_AUTO_CHANGEOVER` | `short` | — |
| `SETPOINT_TYPE_AWAY_COOLING` | `short` | — |
| `SETPOINT_TYPE_AWAY_HEATING` | `short` | — |
| `SETPOINT_TYPE_COOLING_1` | `short` | — |
| `SETPOINT_TYPE_DRY_AIR` | `short` | — |
| `SETPOINT_TYPE_ENERGY_SAVE_COOLING` | `short` | — |
| `SETPOINT_TYPE_ENERGY_SAVE_HEATING` | `short` | — |
| `SETPOINT_TYPE_FULL_POWER` | `short` | — |
| `SETPOINT_TYPE_FURNACE` | `short` | — |
| `SETPOINT_TYPE_HEATING_1` | `short` | — |
| `SETPOINT_TYPE_MOIST_AIR` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED1` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED2` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED3` | `short` | — |
| `SETPOINT_TYPE_NOT_SUPPORTED4` | `short` | — |
