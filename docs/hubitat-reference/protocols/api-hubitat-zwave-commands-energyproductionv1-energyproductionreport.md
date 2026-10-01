# EnergyProductionReport

- **ID:** `api-hubitat-zwave-commands-energyproductionv1-energyproductionreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.energyproductionv1.EnergyProductionReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EnergyProductionReport` | `EnergyProductionReport()` | Creates the command with its declared field defaults. |
| `EnergyProductionReport` | `EnergyProductionReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `PARAMETER_NUMBER_ENERGY_PRODUCTION_TODAY` | `Short` | — |
| `PARAMETER_NUMBER_INSTANT_ENERGY_PRODUCTION` | `Short` | — |
| `PARAMETER_NUMBER_TOTAL_ENERGY_PRODUCTION` | `Short` | — |
| `PARAMETER_NUMBER_TOTAL_PRODUCTION_TIME` | `Short` | — |
| `parameterNumber` | `Short` | — |
| `precision` | `Short` | — |
| `scale` | `Short` | — |
| `scaledValue` | `BigDecimal` | — |
| `size` | `Short` | — |
| `value` | `List<Short>` | — |
