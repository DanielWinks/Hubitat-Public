# ChimneyFanStatusReport

- **ID:** `api-hubitat-zwave-commands-chimneyfanv1-chimneyfanstatusreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.chimneyfanv1.ChimneyFanStatusReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ChimneyFanStatusReport` | `ChimneyFanStatusReport()` | Creates the command with its declared field defaults. |
| `ChimneyFanStatusReport` | `ChimneyFanStatusReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `alarmTemperatureExceeded` | `Boolean` | — |
| `externalAlarm` | `Boolean` | — |
| `notUsed` | `Short` | — |
| `precision` | `Short` | — |
| `scale` | `Short` | — |
| `scaledValue` | `BigDecimal` | — |
| `sensorError` | `Boolean` | — |
| `service` | `Boolean` | — |
| `size` | `Short` | — |
| `speed` | `Short` | — |
| `speedChangeEnable` | `Boolean` | — |
| `startTemperatureExceeded` | `Boolean` | — |
| `state` | `Short` | — |
| `STATE_BOOST` | `Short` | — |
| `STATE_CHIMNEY_FIRE` | `Short` | — |
| `STATE_EXHAUST` | `Short` | — |
| `STATE_EXTERNAL_ALARM` | `Short` | — |
| `STATE_OFF` | `Short` | — |
| `STATE_RELOAD` | `Short` | — |
| `STATE_SENSOR_FAILURE` | `Short` | — |
| `STATE_SERVICE` | `Short` | — |
| `STATE_STOP` | `Short` | — |
| `STATE_VENTING` | `Short` | — |
| `STATE_VENTING_EX` | `Short` | — |
| `value` | `List<Short>` | — |
