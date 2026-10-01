# TariffTblSupplierReport

- **ID:** `api-hubitat-zwave-commands-tarifftblmonitorv1-tarifftblsupplierreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.tarifftblmonitorv1.TariffTblSupplierReport`

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
| `TariffTblSupplierReport` | `TariffTblSupplierReport()` | Creates the command with its declared field defaults. |
| `TariffTblSupplierReport` | `TariffTblSupplierReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `currency` | `Integer` | — |
| `day` | `Short` | — |
| `hourLocalTime` | `Short` | — |
| `minuteLocalTime` | `Short` | — |
| `month` | `Short` | — |
| `numberOfSupplierCharacters` | `Short` | — |
| `secondLocalTime` | `Short` | — |
| `standingChargePeriod` | `Short` | — |
| `standingChargePrecision` | `Short` | — |
| `standingChargeValue` | `Integer` | — |
| `supplierCharacter` | `List<Short>` | — |
| `year` | `Integer` | — |
