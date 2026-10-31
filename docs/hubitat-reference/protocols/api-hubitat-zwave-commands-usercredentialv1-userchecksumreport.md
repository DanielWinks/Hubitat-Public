# UserChecksumReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-userchecksumreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserChecksumReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway payload |
| `UserChecksumReport` | `UserChecksumReport()` | Creates an empty report. |
| `UserChecksumReport` | `UserChecksumReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `UserChecksumReport` | `UserChecksumReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `userChecksum` | `Integer` | — |
| `userUniqueIdentifier` | `Integer` | — |
