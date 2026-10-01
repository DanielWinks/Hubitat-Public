# EnergyProductionGet

- **ID:** `api-hubitat-zwave-commands-energyproductionv1-energyproductionget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.energyproductionv1.EnergyProductionGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EnergyProductionGet` | `EnergyProductionGet()` | Creates the command with its declared field defaults. |
| `EnergyProductionGet` | `EnergyProductionGet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `PARAMETER_NUMBER_ENERGY_PRODUCTION_TODAY` | `Short` | — |
| `PARAMETER_NUMBER_INSTANT_ENERGY_PRODUCTION` | `Short` | — |
| `PARAMETER_NUMBER_TOTAL_ENERGY_PRODUCTION` | `Short` | — |
| `PARAMETER_NUMBER_TOTAL_PRODUCTION_TIME` | `Short` | — |
| `parameterNumber` | `Short` | — |
