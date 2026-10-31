# IrrigationValveConfigSet

- **ID:** `api-hubitat-zwave-commands-irrigationv1-irrigationvalveconfigset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.irrigationv1.IrrigationValveConfigSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getFlowHighThresholdPrecision` | `Short getFlowHighThresholdPrecision()` | Returns the flow high threshold precision value stored in bits 5 through 7 of properties3. Returns: flow high threshold precision value |
| `getFlowHighThresholdScale` | `Short getFlowHighThresholdScale()` | Returns the flow high threshold scale value stored in bits 3 through 4 of properties3. Returns: flow high threshold scale value |
| `getFlowHighThresholdSize` | `Short getFlowHighThresholdSize()` | Returns the flow high threshold size value stored in bits 0 through 2 of properties3. Returns: flow high threshold size value |
| `getFlowLowThresholdPrecision` | `Short getFlowLowThresholdPrecision()` | Returns the flow low threshold precision value stored in bits 5 through 7 of properties4. Returns: flow low threshold precision value |
| `getFlowLowThresholdScale` | `Short getFlowLowThresholdScale()` | Returns the flow low threshold scale value stored in bits 3 through 4 of properties4. Returns: flow low threshold scale value |
| `getFlowLowThresholdSize` | `Short getFlowLowThresholdSize()` | Returns the flow low threshold size value stored in bits 0 through 2 of properties4. Returns: flow low threshold size value |
| `getMasterValve` | `Boolean getMasterValve()` | Reports whether bit 0 of properties1 is set for master valve. Returns: master valve value |
| `getMaximumFlowPrecision` | `Short getMaximumFlowPrecision()` | Returns the maximum flow precision value stored in bits 5 through 7 of properties2. Returns: maximum flow precision value |
| `getMaximumFlowScale` | `Short getMaximumFlowScale()` | Returns the maximum flow scale value stored in bits 3 through 4 of properties2. Returns: maximum flow scale value |
| `getMaximumFlowSize` | `Short getMaximumFlowSize()` | Returns the maximum flow size value stored in bits 0 through 2 of properties2. Returns: maximum flow size value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getScaledFlowHighThreshold` | `BigDecimal getScaledFlowHighThreshold()` | Returns the scaled flow high threshold value stored in parseValue. Returns: scaled flow high threshold value |
| `getScaledFlowLowThreshold` | `BigDecimal getScaledFlowLowThreshold()` | Returns the scaled flow low threshold value stored in parseValue. Returns: scaled flow low threshold value |
| `getScaledMaximumFlow` | `BigDecimal getScaledMaximumFlow()` | Returns the scaled maximum flow value stored in parseValue. Returns: scaled maximum flow value |
| `IrrigationValveConfigSet` | `IrrigationValveConfigSet()` | Creates the command with its declared field defaults. |
| `IrrigationValveConfigSet` | `IrrigationValveConfigSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 5 values also keeps the declared field |
| `setFlowHighThresholdPrecision` | `void setFlowHighThresholdPrecision(Short value)` | Sets the flow high threshold precision value used by this command. Parameters: value - flow high threshold precision value to encode |
| `setFlowHighThresholdScale` | `void setFlowHighThresholdScale(Short value)` | Sets the flow high threshold scale value used by this command. Parameters: value - flow high threshold scale value to encode |
| `setFlowHighThresholdSize` | `void setFlowHighThresholdSize(Short value)` | Encodes flow high threshold size in bits 0 through 2 of properties3 and preserves the bits selected by 0x7C. Parameters: value - flow high threshold size value to encode |
| `setFlowLowThresholdPrecision` | `void setFlowLowThresholdPrecision(Short value)` | Sets the flow low threshold precision value used by this command. Parameters: value - flow low threshold precision value to encode |
| `setFlowLowThresholdScale` | `void setFlowLowThresholdScale(Short value)` | Sets the flow low threshold scale value used by this command. Parameters: value - flow low threshold scale value to encode |
| `setFlowLowThresholdSize` | `void setFlowLowThresholdSize(Short value)` | Encodes flow low threshold size in bits 0 through 2 of properties4 and preserves the bits selected by 0x7C. Parameters: value - flow low threshold size value to encode |
| `setMasterValve` | `void setMasterValve(Boolean value)` | Sets the master valve value used by this command. Parameters: value - master valve value to encode |
| `setMaximumFlowPrecision` | `void setMaximumFlowPrecision(Short value)` | Sets the maximum flow precision value used by this command. Parameters: value - maximum flow precision value to encode |
| `setMaximumFlowScale` | `void setMaximumFlowScale(Short value)` | Sets the maximum flow scale value used by this command. Parameters: value - maximum flow scale value to encode |
| `setMaximumFlowSize` | `void setMaximumFlowSize(Short value)` | Encodes maximum flow size in bits 0 through 2 of properties2 and preserves the bits selected by 0x7C. Parameters: value - maximum flow size value to encode |
| `setScaledFlowHighThreshold` | `void setScaledFlowHighThreshold(BigDecimal scaledValue)` | Sets the scaled flow high threshold value used by this command. Parameters: scaledValue - scaled flow high threshold value to encode |
| `setScaledFlowLowThreshold` | `void setScaledFlowLowThreshold(BigDecimal scaledValue)` | Sets the scaled flow low threshold value used by this command. Parameters: scaledValue - scaled flow low threshold value to encode |
| `setScaledMaximumFlow` | `void setScaledMaximumFlow(BigDecimal scaledValue)` | Sets the scaled maximum flow value used by this command. Parameters: scaledValue - scaled maximum flow value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `flowHighThresholdValue` | `List<Short>` | — |
| `flowLowThresholdValue` | `List<Short>` | — |
| `maximumFlowValue` | `List<Short>` | — |
| `nominalCurrentHighThreshold` | `Short` | — |
| `nominalCurrentLowThreshold` | `Short` | — |
| `SENSOR_USAGE_USE_MOISTURE_SENSOR` | `short` | — |
| `SENSOR_USAGE_USE_RAIN_SENSOR` | `short` | — |
| `sensorUsage` | `Short` | — |
| `valveID` | `Short` | — |
