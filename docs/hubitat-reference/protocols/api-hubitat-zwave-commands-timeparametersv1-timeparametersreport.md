# TimeParametersReport

- **ID:** `api-hubitat-zwave-commands-timeparametersv1-timeparametersreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.timeparametersv1.TimeParametersReport`

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
| `TimeParametersReport` | `TimeParametersReport()` | Creates the command with its declared field defaults. |
| `TimeParametersReport` | `TimeParametersReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `day` | `Short` | — |
| `hourUtc` | `Short` | — |
| `minuteUtc` | `Short` | — |
| `month` | `Short` | — |
| `secondUtc` | `Short` | — |
| `year` | `Integer` | — |
