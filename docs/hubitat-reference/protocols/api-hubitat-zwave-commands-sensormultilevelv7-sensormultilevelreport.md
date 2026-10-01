# SensorMultilevelReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv7-sensormultilevelreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv7.SensorMultilevelReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.sensormultilevelv6.SensorMultilevelReport`
- [SensorMultilevelReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv6-sensormultilevelreport.md)
- [SensorMultilevelReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv6-sensormultilevelreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `SensorMultilevelReport` | `SensorMultilevelReport()` | Creates the command with its declared field defaults. |
| `SensorMultilevelReport` | `SensorMultilevelReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SensorMultilevelReport` | `SensorMultilevelReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `SENSOR_TYPE_BASIC_METABOLIC_RATE_BMR_V7` | `Short` | — |
| `SENSOR_TYPE_BLOOD_PRESSURE_V7` | `Short` | — |
| `SENSOR_TYPE_BODY_MASS_INDEX_BMI_V7` | `Short` | — |
| `SENSOR_TYPE_BONE_MASS_V7` | `Short` | — |
| `SENSOR_TYPE_CARBON_MONOXIDE_CO_LEVEL_V7` | `Short` | — |
| `SENSOR_TYPE_FAT_MASS_V7` | `Short` | — |
| `SENSOR_TYPE_FORMALDEHYDE_CH2O_LEVEL_V7` | `Short` | — |
| `SENSOR_TYPE_HEART_RATE_V7` | `Short` | — |
| `SENSOR_TYPE_METHANE_DENSITY_V7` | `Short` | — |
| `SENSOR_TYPE_MUSCLE_MASS_V7` | `Short` | — |
| `SENSOR_TYPE_PARTICULATE_MATTER_V7` | `Short` | — |
| `SENSOR_TYPE_RADON_CONCENTRATION_V7` | `Short` | — |
| `SENSOR_TYPE_SOIL_HUMIDITY_V7` | `Short` | — |
| `SENSOR_TYPE_SOIL_REACTIVITY_V7` | `Short` | — |
| `SENSOR_TYPE_SOIL_SALINITY_V7` | `Short` | — |
| `SENSOR_TYPE_TOTAL_BODY_WATER_TBW_V7` | `Short` | — |
| `SENSOR_TYPE_VOLATILE_ORGANIC_COMPOUND_V7` | `Short` | — |
