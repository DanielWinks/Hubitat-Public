# SensorMultilevelSupportedSensorReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv5-sensormultilevelsupportedsensorreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedSensorReport`

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
| `tideLevel` | `Boolean` | — |
| `ultraviolet` | `Boolean` | — |
| `velocity` | `Boolean` | — |
| `voltage` | `Boolean` | — |
| `waterTemperature` | `Boolean` | — |
| `weight` | `Boolean` | — |
