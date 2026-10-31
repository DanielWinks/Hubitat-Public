# SensorMultilevelReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv2-sensormultilevelreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv2.SensorMultilevelReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport`
- [SensorMultilevelReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv1-sensormultilevelreport.md)
- [SensorMultilevelReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv1-sensormultilevelreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `SensorMultilevelReport` | `SensorMultilevelReport()` | Creates the command with its declared field defaults. |
| `SensorMultilevelReport` | `SensorMultilevelReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SensorMultilevelReport` | `SensorMultilevelReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `SENSOR_TYPE_ATMOSPHERIC_PRESSURE_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_BAROMETRIC_PRESSURE_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_DEW_POINT_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_DIRECTION_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_POWER_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_RAIN_RATE_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_RELATIVE_HUMIDITY_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_SOLAR_RADIATION_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_TIDE_LEVEL_VERSION_2` | `Short` | — |
| `SENSOR_TYPE_VELOCITY_VERSION_2` | `Short` | — |
