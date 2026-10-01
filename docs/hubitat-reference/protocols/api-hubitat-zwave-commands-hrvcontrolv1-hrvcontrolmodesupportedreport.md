# HrvControlModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-hrvcontrolv1-hrvcontrolmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.hrvcontrolv1.HrvControlModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `HrvControlModeSupportedReport` | `HrvControlModeSupportedReport()` | Creates the command with its declared field defaults. |
| `HrvControlModeSupportedReport` | `HrvControlModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `demandAutomatic` | `Boolean` | — |
| `energySavingsMode` | `Boolean` | — |
| `manual` | `Boolean` | — |
| `MANUAL_CONTROL_SUPPORTED_BYPASS_AUTO` | `Short` | — |
| `MANUAL_CONTROL_SUPPORTED_BYPASS_OPEN_CLOSE` | `Short` | — |
| `MANUAL_CONTROL_SUPPORTED_MODULATED_BYPASS` | `Short` | — |
| `MANUAL_CONTROL_SUPPORTED_VENTILATION_RATE` | `Short` | — |
| `manualControlSupported` | `Short` | — |
| `off` | `Boolean` | — |
| `schedule` | `Boolean` | — |
