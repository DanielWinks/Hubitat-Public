# ChimneyFanStateReport

- **ID:** `api-hubitat-zwave-commands-chimneyfanv1-chimneyfanstatereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.chimneyfanv1.ChimneyFanStateReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ChimneyFanStateReport` | `ChimneyFanStateReport()` | Creates the command with its declared field defaults. |
| `ChimneyFanStateReport` | `ChimneyFanStateReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
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
