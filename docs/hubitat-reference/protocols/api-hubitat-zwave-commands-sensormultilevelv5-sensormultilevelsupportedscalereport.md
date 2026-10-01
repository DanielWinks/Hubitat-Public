# SensorMultilevelSupportedScaleReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv5-sensormultilevelsupportedscalereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport`

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
| `SensorMultilevelSupportedScaleReport` | `SensorMultilevelSupportedScaleReport()` | Creates the command with its declared field defaults. |
| `SensorMultilevelSupportedScaleReport` | `SensorMultilevelSupportedScaleReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `scaleBitMask` | `Short` | — |
| `SENSOR_TYPE_AIR_FLOW_VERSION_3` | `Short` | — |
| `SENSOR_TYPE_ANGLE_POSITION_VERSION_4` | `Short` | — |
| `SENSOR_TYPE_ATMOSPHERIC_PRESSURE_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_BAROMETRIC_PRESSURE_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_CO2_LEVEL_VERSION_3` | `Short` | — |
| `SENSOR_TYPE_CURRENT_VERSION_3` | `Short` | — |
| `SENSOR_TYPE_DEW_POINT_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_DIRECTION_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_DISTANCE_VERSION_3` | `Short` | — |
| `SENSOR_TYPE_ELECTRICAL_CONDUCTIVITY_V5` | `Short` | — |
| `SENSOR_TYPE_ELECTRICAL_RESISTIVITY_V5` | `Short` | — |
| `SENSOR_TYPE_GENERAL_PURPOSE_VALUE_VERSION_1` | `Short` | — |
| `SENSOR_TYPE_LOUDNESS_V5` | `Short` | — |
| `SENSOR_TYPE_LUMINANCE_VERSION_1` | `Short` | — |
| `SENSOR_TYPE_MOISTURE_V5` | `Short` | — |
| `SENSOR_TYPE_POWER_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_RAIN_RATE_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_RELATIVE_HUMIDITY_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_ROTATION_V5` | `Short` | — |
| `SENSOR_TYPE_SEISMIC_INTENSITY_V5` | `Short` | — |
| `SENSOR_TYPE_SEISMIC_MAGNITUDE_V5` | `Short` | — |
| `SENSOR_TYPE_SOIL_TEMPERATURE_V5` | `Short` | — |
| `SENSOR_TYPE_SOLAR_RADIATION_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_TANK_CAPACITY_VERSION_3` | `Short` | — |
| `SENSOR_TYPE_TEMPERATURE_VERSION_1` | `Short` | — |
| `SENSOR_TYPE_TIDE_LEVEL_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_ULTRAVIOLET_V5` | `Short` | — |
| `SENSOR_TYPE_VELOCITY_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_VOLTAGE_VERSION_3` | `Short` | — |
| `SENSOR_TYPE_WATER_TEMPERATURE_V5` | `Short` | — |
| `SENSOR_TYPE_WEIGHT_VERSION_3` | `Short` | — |
| `sensorType` | `Short` | — |
