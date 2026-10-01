# SensorBinaryV2

- **ID:** `api-hubitat-zwave-commandclasses-sensorbinaryv2`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commandclasses.SensorBinaryV2`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `sensorBinaryGet` | `SensorBinaryGet sensorBinaryGet()` | Creates a SensorBinaryGet command with its declared field defaults. Returns: the SensorBinaryGet command |
| `sensorBinaryGet` | `SensorBinaryGet sensorBinaryGet(Map args)` | Creates a SensorBinaryGet command initialized from writable bean properties. Parameters: args - values for writable protocol properties; omitted keys retain their constructed defau |
| `sensorBinaryReport` | `SensorBinaryReport sensorBinaryReport()` | Creates a SensorBinaryReport command with its declared field defaults. Returns: the SensorBinaryReport command |
| `sensorBinaryReport` | `SensorBinaryReport sensorBinaryReport(Map args)` | Creates a SensorBinaryReport command initialized from writable bean properties. Parameters: args - values for writable protocol properties; omitted keys retain their constructed de |
| `sensorBinarySupportedGetSensor` | `SensorBinarySupportedGetSensor sensorBinarySupportedGetSensor()` | Creates a SensorBinarySupportedGetSensor command with its declared field defaults. Returns: the SensorBinarySupportedGetSensor command |
| `sensorBinarySupportedGetSensor` | `SensorBinarySupportedGetSensor sensorBinarySupportedGetSensor(Map args)` | Creates a SensorBinarySupportedGetSensor command initialized from writable bean properties. Parameters: args - values for writable protocol properties; omitted keys retain their co |
| `sensorBinarySupportedSensorReport` | `SensorBinarySupportedSensorReport sensorBinarySupportedSensorReport()` | Creates a SensorBinarySupportedSensorReport command with its declared field defaults. Returns: the SensorBinarySupportedSensorReport command |
| `sensorBinarySupportedSensorReport` | `SensorBinarySupportedSensorReport sensorBinarySupportedSensorReport(Map args)` | Creates a SensorBinarySupportedSensorReport command initialized from writable bean properties. Parameters: args - values for writable protocol properties; omitted keys retain their |

## Properties

| Name | Type | Description |
|---|---|---|
| `SENSOR_TYPE_AUX` | `Short` | — |
| `SENSOR_TYPE_CO` | `Short` | — |
| `SENSOR_TYPE_CO2` | `Short` | — |
| `SENSOR_TYPE_DOOR_WINDOW` | `Short` | — |
| `SENSOR_TYPE_FIRST` | `Short` | — |
| `SENSOR_TYPE_FREEZE` | `Short` | — |
| `SENSOR_TYPE_GENERAL_PURPOSE` | `Short` | — |
| `SENSOR_TYPE_GLASS_BREAK` | `Short` | — |
| `SENSOR_TYPE_HEAT` | `Short` | — |
| `SENSOR_TYPE_MOTION` | `Short` | — |
| `SENSOR_TYPE_SMOKE` | `Short` | — |
| `SENSOR_TYPE_TAMPER` | `Short` | — |
| `SENSOR_TYPE_TILT` | `Short` | — |
| `SENSOR_TYPE_WATER` | `Short` | — |
| `SENSOR_VALUE_DETECTED_AN_EVENT` | `Short` | — |
| `SENSOR_VALUE_IDLE` | `Short` | — |
