# SensorMultilevelReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv1-sensormultilevelreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport`

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
| `getScaledSensorValue` | `BigDecimal getScaledSensorValue()` | Returns the scaled sensor value value stored in preScaledSensorValue. Returns: scaled sensor value value |
| `SensorMultilevelReport` | `SensorMultilevelReport()` | Creates the command with its declared field defaults. |
| `SensorMultilevelReport` | `SensorMultilevelReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SensorMultilevelReport` | `SensorMultilevelReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `precision` | `Short` | — |
| `preScaledSensorValue` | `BigDecimal` | — |
| `scale` | `Short` | — |
| `SENSOR_TYPE_GENERAL_PURPOSE_VALUE_VERSION_1` | `Short` | — |
| `SENSOR_TYPE_LUMINANCE_VERSION_1` | `Short` | — |
| `SENSOR_TYPE_TEMPERATURE_VERSION_1` | `Short` | — |
| `sensorType` | `Short` | — |
| `sensorValue` | `List<Short>` | — |
| `size` | `Short` | — |
