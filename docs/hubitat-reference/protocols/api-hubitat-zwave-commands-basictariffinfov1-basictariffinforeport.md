# BasicTariffInfoReport

- **ID:** `api-hubitat-zwave-commands-basictariffinfov1-basictariffinforeport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.basictariffinfov1.BasicTariffInfoReport`

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

## Properties

| Name | Type | Description |
|---|---|---|
| `dual` | `Boolean` | — |
| `e1CurrentRateInUse` | `Short` | — |
| `e1RateConsumptionRegister` | `Integer` | — |
| `e1TimeForNextRateHours` | `Short` | — |
| `e1TimeForNextRateMinutes` | `Short` | — |
| `e1TimeForNextRateSeconds` | `Short` | — |
| `e2CurrentRateInUse` | `Short` | — |
| `e2RateConsumptionRegister` | `Integer` | — |
| `totalNoImportRates` | `Short` | — |
