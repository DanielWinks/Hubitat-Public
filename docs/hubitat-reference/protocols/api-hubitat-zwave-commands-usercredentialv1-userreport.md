# UserReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-userreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserReport`

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
| `UserReport` | `UserReport()` | Creates report. |
| `UserReport` | `UserReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `UserReport` | `UserReport(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `credentialRule` | `Short` | — |
| `expiringTimeoutMinutes` | `Integer` | — |
| `nextUserUniqueIdentifier` | `Integer` | — |
| `userActiveState` | `Boolean` | — |
| `userModifierNodeId` | `Integer` | — |
| `userModifierType` | `Short` | — |
| `userName` | `String` | — |
| `userNameEncoding` | `Short` | — |
| `userReportType` | `Short` | — |
| `userType` | `Short` | — |
| `userUniqueIdentifier` | `Integer` | — |
