# PrepaymentBalanceReport

- **ID:** `api-hubitat-zwave-commands-prepaymentv1-prepaymentbalancereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.prepaymentv1.PrepaymentBalanceReport`

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
| `PrepaymentBalanceReport` | `PrepaymentBalanceReport()` | Creates the command with its declared field defaults. |
| `PrepaymentBalanceReport` | `PrepaymentBalanceReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `balancePrecision` | `Short` | — |
| `balanceType` | `Short` | — |
| `balanceValue` | `Integer` | — |
| `currency` | `Integer` | — |
| `debt` | `Integer` | — |
| `debtPrecision` | `Short` | — |
| `debtRecoveryPercentage` | `Short` | — |
| `emerCredit` | `Integer` | — |
| `emerCreditPrecision` | `Short` | — |
| `meterType` | `Short` | — |
| `scale` | `Short` | — |
