# IndicatorDescriptionReport

- **ID:** `api-hubitat-zwave-commands-indicatorv4-indicatordescriptionreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.indicatorv4.IndicatorDescriptionReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: command-class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |
| `IndicatorDescriptionReport` | `IndicatorDescriptionReport()` | Creates an empty command. |
| `IndicatorDescriptionReport` | `IndicatorDescriptionReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `IndicatorDescriptionReport` | `IndicatorDescriptionReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `description` | `String` | — |
| `indicatorId` | `Short` | — |
