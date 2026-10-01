# KeyLockerEntryReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv2-keylockerentryreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv2.KeyLockerEntryReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |
| `KeyLockerEntryReport` | `KeyLockerEntryReport()` | Creates report. |
| `KeyLockerEntryReport` | `KeyLockerEntryReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `KeyLockerEntryReport` | `KeyLockerEntryReport(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `entrySlot` | `Integer` | — |
| `entryType` | `Short` | — |
| `occupied` | `Boolean` | — |
