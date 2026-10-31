# IrrigationSystemConfigSet

- **ID:** `api-hubitat-zwave-commands-irrigationv1-irrigationsystemconfigset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.irrigationv1.IrrigationSystemConfigSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getHighPressureThresholdPrecision` | `Short getHighPressureThresholdPrecision()` | Returns the high pressure threshold precision value stored in bits 5 through 7 of properties1. Returns: high pressure threshold precision value |
| `getHighPressureThresholdScale` | `Short getHighPressureThresholdScale()` | Returns the high pressure threshold scale value stored in bits 3 through 4 of properties1. Returns: high pressure threshold scale value |
| `getHighPressureThresholdSize` | `Short getHighPressureThresholdSize()` | Returns the high pressure threshold size value stored in bits 0 through 2 of properties1. Returns: high pressure threshold size value |
| `getLowPressureThresholdPrecision` | `Short getLowPressureThresholdPrecision()` | Returns the low pressure threshold precision value stored in bits 5 through 7 of properties2. Returns: low pressure threshold precision value |
| `getLowPressureThresholdScale` | `Short getLowPressureThresholdScale()` | Returns the low pressure threshold scale value stored in bits 3 through 4 of properties2. Returns: low pressure threshold scale value |
| `getLowPressureThresholdSize` | `Short getLowPressureThresholdSize()` | Returns the low pressure threshold size value stored in bits 0 through 2 of properties2. Returns: low pressure threshold size value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getScaledHighPressureThreshold` | `BigDecimal getScaledHighPressureThreshold()` | Returns the scaled high pressure threshold value stored in parseValue. Returns: scaled high pressure threshold value |
| `getScaledLowPressureThresholdPressure` | `BigDecimal getScaledLowPressureThresholdPressure()` | Returns the scaled low pressure threshold pressure value stored in parseValue. Returns: scaled low pressure threshold pressure value |
| `IrrigationSystemConfigSet` | `IrrigationSystemConfigSet()` | Creates the command with its declared field defaults. |
| `IrrigationSystemConfigSet` | `IrrigationSystemConfigSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `setHighPressureThresholdPrecision` | `void setHighPressureThresholdPrecision(Short value)` | Sets the high pressure threshold precision value used by this command. Parameters: value - high pressure threshold precision value to encode |
| `setHighPressureThresholdScale` | `void setHighPressureThresholdScale(Short value)` | Sets the high pressure threshold scale value used by this command. Parameters: value - high pressure threshold scale value to encode |
| `setHighPressureThresholdSize` | `void setHighPressureThresholdSize(Short value)` | Encodes high pressure threshold size in bits 0 through 2 of properties1 and preserves the bits selected by 0x7C. Parameters: value - high pressure threshold size value to encode |
| `setLowPressureThresholdPrecision` | `void setLowPressureThresholdPrecision(Short value)` | Sets the low pressure threshold precision value used by this command. Parameters: value - low pressure threshold precision value to encode |
| `setLowPressureThresholdScale` | `void setLowPressureThresholdScale(Short value)` | Sets the low pressure threshold scale value used by this command. Parameters: value - low pressure threshold scale value to encode |
| `setLowPressureThresholdSize` | `void setLowPressureThresholdSize(Short value)` | Encodes low pressure threshold size in bits 0 through 2 of properties2 and preserves the bits selected by 0x7C. Parameters: value - low pressure threshold size value to encode |
| `setScaledHighPressureThreshold` | `void setScaledHighPressureThreshold(BigDecimal scaledValue)` | Sets the scaled high pressure threshold value used by this command. Parameters: scaledValue - scaled high pressure threshold value to encode |
| `setScaledLowPressureThresholdPressure` | `void setScaledLowPressureThresholdPressure(BigDecimal scaledValue)` | Sets the scaled low pressure threshold pressure value used by this command. Parameters: scaledValue - scaled low pressure threshold pressure value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `highPressureThresholdValue` | `List<Short>` | — |
| `lowPressureThresholdValue` | `List<Short>` | — |
| `masterValveDelay` | `Short` | — |
| `SENSOR_POLARITY_MOISTURE_SENSOR_POLARITY` | `short` | — |
| `SENSOR_POLARITY_RAIN_SENSOR_POLARITY` | `short` | — |
| `SENSOR_POLARITY_VALID` | `short` | — |
| `sensorPolarity` | `Short` | — |
