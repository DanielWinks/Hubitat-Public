# MeterTblCurrentDataReport

- **ID:** `api-hubitat-zwave-commands-metertblmonitorv1-metertblcurrentdatareport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.metertblmonitorv1.MeterTblCurrentDataReport`

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
| `MeterTblCurrentDataReport` | `MeterTblCurrentDataReport()` | Creates the command with its declared field defaults. |
| `MeterTblCurrentDataReport` | `MeterTblCurrentDataReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `dataset` | `Integer` | — |
| `day` | `Short` | — |
| `hourLocalTime` | `Short` | — |
| `minuteLocalTime` | `Short` | — |
| `month` | `Short` | — |
| `rateType` | `Short` | — |
| `reportsToFollow` | `Short` | — |
| `secondLocalTime` | `Short` | — |
| `year` | `Integer` | — |
