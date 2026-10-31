# RecordReport

- **ID:** `api-hubitat-zwave-commands-doorlockloggingv1-recordreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockloggingv1.RecordReport`

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
| `RecordReport` | `RecordReport()` | Creates the command with its declared field defaults. |
| `RecordReport` | `RecordReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 11 values also keeps the declared fiel |

## Properties

| Name | Type | Description |
|---|---|---|
| `day` | `Short` | — |
| `eventType` | `Short` | — |
| `hourLocalTime` | `Short` | — |
| `minuteLocalTime` | `Short` | — |
| `month` | `Short` | — |
| `recordNumber` | `Short` | — |
| `recordStatus` | `Short` | — |
| `secondLocalTime` | `Short` | — |
| `userCode` | `List<Short>` | — |
| `userCodeLength` | `Short` | — |
| `userIdentifier` | `Short` | — |
| `year` | `Integer` | — |
