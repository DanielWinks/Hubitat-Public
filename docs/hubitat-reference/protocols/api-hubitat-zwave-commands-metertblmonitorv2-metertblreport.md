# MeterTblReport

- **ID:** `api-hubitat-zwave-commands-metertblmonitorv2-metertblreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.metertblmonitorv2.MeterTblReport`

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
| `MeterTblReport` | `MeterTblReport()` | Creates the command with its declared field defaults. |
| `MeterTblReport` | `MeterTblReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `dataHistorySupported` | `Integer` | — |
| `datasetHistorySupported` | `Integer` | — |
| `datasetSupported` | `Integer` | — |
| `meterType` | `Short` | — |
| `PAY_METER_CREDITMETER` | `Short` | — |
| `PAY_METER_PREPAYMENT_METER` | `Short` | — |
| `PAY_METER_PREPAYMENT_METER_DEBT` | `Short` | — |
| `PAY_METER_RESERVED0` | `Short` | — |
| `payMeter` | `Short` | — |
| `rateType` | `Short` | — |
