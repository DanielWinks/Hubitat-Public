# SensorMultilevelSupportedSensorReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv6-sensormultilevelsupportedsensorreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv6.SensorMultilevelSupportedSensorReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedSensorReport`
- [SensorMultilevelSupportedSensorReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv5-sensormultilevelsupportedsensorreport.md)
- [SensorMultilevelSupportedSensorReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv5-sensormultilevelsupportedsensorreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SensorMultilevelSupportedSensorReport` | `SensorMultilevelSupportedSensorReport()` | Creates the command with its declared field defaults. |
| `SensorMultilevelSupportedSensorReport` | `SensorMultilevelSupportedSensorReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `airFlow` | `Boolean` | — |
| `airTemperature` | `Boolean` | — |
| `anglePosition` | `Boolean` | — |
| `atmosphericPressure` | `Boolean` | — |
| `barometricPressure` | `Boolean` | — |
| `carbonDioxideLevel` | `Boolean` | — |
| `current` | `Boolean` | — |
| `dewPoint` | `Boolean` | — |
| `direction` | `Boolean` | — |
| `distance` | `Boolean` | — |
| `electricalConductivity` | `Boolean` | — |
| `electricalResistivity` | `Boolean` | — |
| `frequency` | `Boolean` | — |
| `generalPurposeValue` | `Boolean` | — |
| `humidity` | `Boolean` | — |
| `loudness` | `Boolean` | — |
| `luminance` | `Boolean` | — |
| `moisture` | `Boolean` | — |
| `power` | `Boolean` | — |
| `rainRate` | `Boolean` | — |
| `rotation` | `Boolean` | — |
| `seismicIntensity` | `Boolean` | — |
| `seismicMagnitude` | `Boolean` | — |
| `soilTemperature` | `Boolean` | — |
| `solarRadiation` | `Boolean` | — |
| `tankCapacity` | `Boolean` | — |
| `targetTemperature` | `Boolean` | — |
| `tideLevel` | `Boolean` | — |
| `time` | `Boolean` | — |
| `ultraviolet` | `Boolean` | — |
| `velocity` | `Boolean` | — |
| `voltage` | `Boolean` | — |
| `waterTemperature` | `Boolean` | — |
| `weight` | `Boolean` | — |
