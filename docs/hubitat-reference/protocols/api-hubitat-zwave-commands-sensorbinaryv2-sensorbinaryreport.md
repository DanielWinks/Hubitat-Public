# SensorBinaryReport

- **ID:** `api-hubitat-zwave-commands-sensorbinaryv2-sensorbinaryreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport`
- [SensorBinaryReport](../protocols/api-hubitat-zwave-commands-sensorbinaryv1-sensorbinaryreport.md)
- [SensorBinaryReport](../protocols/api-hubitat-zwave-commands-sensorbinaryv1-sensorbinaryreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SensorBinaryReport` | `SensorBinaryReport()` | Creates the command with its declared field defaults. |
| `SensorBinaryReport` | `SensorBinaryReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SensorBinaryReport` | `SensorBinaryReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

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
| `sensorType` | `Short` | — |
| `sensorValue` | `Short` | — |
