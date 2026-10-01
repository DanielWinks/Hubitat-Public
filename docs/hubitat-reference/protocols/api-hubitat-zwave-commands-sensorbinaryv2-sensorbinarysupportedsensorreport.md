# SensorBinarySupportedSensorReport

- **ID:** `api-hubitat-zwave-commands-sensorbinaryv2-sensorbinarysupportedsensorreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensorbinaryv2.SensorBinarySupportedSensorReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: null |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `SensorBinarySupportedSensorReport` | `SensorBinarySupportedSensorReport()` | Creates the command with its declared field defaults. |
| `SensorBinarySupportedSensorReport` | `SensorBinarySupportedSensorReport(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |

## Properties

| Name | Type | Description |
|---|---|---|
| `aux` | `Boolean` | — |
| `co` | `Boolean` | — |
| `co2` | `Boolean` | — |
| `doorwindow` | `Boolean` | — |
| `first` | `Boolean` | — |
| `freeze` | `Boolean` | — |
| `general` | `Boolean` | — |
| `glassBreak` | `Boolean` | — |
| `heat` | `Boolean` | — |
| `motion` | `Boolean` | — |
| `smoke` | `Boolean` | — |
| `tamper` | `Boolean` | — |
| `tilt` | `Boolean` | — |
| `water` | `Boolean` | — |
