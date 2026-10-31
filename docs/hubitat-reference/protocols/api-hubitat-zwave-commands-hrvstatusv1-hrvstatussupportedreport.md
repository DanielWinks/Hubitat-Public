# HrvStatusSupportedReport

- **ID:** `api-hubitat-zwave-commands-hrvstatusv1-hrvstatussupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.hrvstatusv1.HrvStatusSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `HrvStatusSupportedReport` | `HrvStatusSupportedReport()` | Creates the command with its declared field defaults. |
| `HrvStatusSupportedReport` | `HrvStatusSupportedReport(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |

## Properties

| Name | Type | Description |
|---|---|---|
| `dischargeAirTemperature` | `Boolean` | — |
| `exhaustAirTemperature` | `Boolean` | — |
| `outdoorAirTemperature` | `Boolean` | — |
| `relativeHumidityInRoom` | `Boolean` | — |
| `remainingFilterLife` | `Boolean` | — |
| `roomTemperature` | `Boolean` | — |
| `supplyAirTemperature` | `Boolean` | — |
