# AntitheftReport

- **ID:** `api-hubitat-zwave-commands-antitheftv3-antitheftreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.antitheftv3.AntitheftReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.antitheftv2.AntitheftReport`
- [AntitheftReport](../protocols/api-hubitat-zwave-commands-antitheftv2-antitheftreport.md)
- [AntitheftReport](../protocols/api-hubitat-zwave-commands-antitheftv2-antitheftreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AntitheftReport` | `AntitheftReport()` | Creates the command with its declared field defaults. |
| `AntitheftReport` | `AntitheftReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `zwaveAllianceLockingEntityID` | `Integer` | — |
