# IrrigationSystemStatusReport

- **ID:** `api-hubitat-zwave-commands-irrigationv1-irrigationsystemstatusreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.irrigationv1.IrrigationSystemStatusReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getFlowPrecision` | `Short getFlowPrecision()` | Returns the flow precision value stored in bits 5 through 7 of properties1. Returns: flow precision value |
| `getFlowScale` | `Short getFlowScale()` | Returns the flow scale value stored in bits 3 through 4 of properties1. Returns: flow scale value |
| `getFlowSize` | `Short getFlowSize()` | Returns the flow size value stored in bits 0 through 2 of properties1. Returns: flow size value |
| `getMasterValve` | `Boolean getMasterValve()` | Reports whether bit 0 of properties3 is set for master valve. Returns: master valve value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPressurePrecision` | `Short getPressurePrecision()` | Returns the pressure precision value stored in bits 5 through 7 of properties2. Returns: pressure precision value |
| `getPressureScale` | `Short getPressureScale()` | Returns the pressure scale value stored in bits 3 through 4 of properties2. Returns: pressure scale value |
| `getPressureSize` | `Short getPressureSize()` | Returns the pressure size value stored in bits 0 through 2 of properties2. Returns: pressure size value |
| `getScaledFlow` | `BigDecimal getScaledFlow()` | Returns the scaled flow value stored in parseValue. Returns: scaled flow value |
| `getScaledPressure` | `BigDecimal getScaledPressure()` | Returns the scaled pressure value stored in parseValue. Returns: scaled pressure value |
| `IrrigationSystemStatusReport` | `IrrigationSystemStatusReport()` | Creates the command with its declared field defaults. |
| `IrrigationSystemStatusReport` | `IrrigationSystemStatusReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `setFlowPrecision` | `void setFlowPrecision(Short value)` | Sets the flow precision value used by this command. Parameters: value - flow precision value to encode |
| `setFlowScale` | `void setFlowScale(Short value)` | Sets the flow scale value used by this command. Parameters: value - flow scale value to encode |
| `setFlowSize` | `void setFlowSize(Short value)` | Encodes flow size in bits 0 through 2 of properties1 and preserves the bits selected by 0x7C. Parameters: value - flow size value to encode |
| `setMasterValve` | `void setMasterValve(Boolean value)` | Sets the master valve value used by this command. Parameters: value - master valve value to encode |
| `setPressurePrecision` | `void setPressurePrecision(Short value)` | Sets the pressure precision value used by this command. Parameters: value - pressure precision value to encode |
| `setPressureScale` | `void setPressureScale(Short value)` | Sets the pressure scale value used by this command. Parameters: value - pressure scale value to encode |
| `setPressureSize` | `void setPressureSize(Short value)` | Encodes pressure size in bits 0 through 2 of properties2 and preserves the bits selected by 0x7C. Parameters: value - pressure size value to encode |
| `setScaledFlow` | `void setScaledFlow(BigDecimal scaledValue)` | Sets the scaled flow value used by this command. Parameters: scaledValue - scaled flow value to encode |
| `setScaledPressure` | `void setScaledPressure(BigDecimal scaledValue)` | Sets the scaled pressure value used by this command. Parameters: scaledValue - scaled pressure value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `ERROR_STATUS_EMERGENCY_SHUTDOWN` | `short` | — |
| `ERROR_STATUS_HIGH_THRESHOLD_TRIGGERED` | `short` | — |
| `ERROR_STATUS_LOW_THRESHOLD_TRIGGERED` | `short` | — |
| `ERROR_STATUS_NOT_PROGRAMMED` | `short` | — |
| `ERROR_STATUS_VALVE_ERRORS` | `short` | — |
| `flowValue` | `List<Short>` | — |
| `pressureValue` | `List<Short>` | — |
| `sensorStatus` | `Short` | — |
| `shutoffDuration` | `Short` | — |
| `STATUS_FLOW_SENSOR_DETECTED` | `short` | — |
| `STATUS_MOISTURE_SENSOR_DETECTED` | `short` | — |
| `STATUS_PRESSURE_SENSOR_DETECTED` | `short` | — |
| `STATUS_RAIN_SENSOR_DETECTED` | `short` | — |
| `systemErrorStatus` | `Short` | — |
| `systemVoltage` | `Short` | — |
| `valveID` | `Short` | — |
