# AllUsersChecksumReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-alluserschecksumreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.AllUsersChecksumReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AllUsersChecksumReport` | `AllUsersChecksumReport()` | Creates an empty report. |
| `AllUsersChecksumReport` | `AllUsersChecksumReport(List<Map<String, Object>> payload)` | Parameters: payload - Z-Wave JS values |
| `AllUsersChecksumReport` | `AllUsersChecksumReport(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |
| `getCMD` | `String getCMD()` | Returns: command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `allUsersChecksum` | `Integer` | — |
