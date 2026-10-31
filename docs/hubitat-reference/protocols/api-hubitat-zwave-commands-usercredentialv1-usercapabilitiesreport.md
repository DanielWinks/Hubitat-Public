# UserCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv1-usercapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv1.UserCapabilitiesReport`

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
| `UserCapabilitiesReport` | `UserCapabilitiesReport()` | Creates report. |
| `UserCapabilitiesReport` | `UserCapabilitiesReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `UserCapabilitiesReport` | `UserCapabilitiesReport(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `allUsersChecksumSupport` | `Boolean` | — |
| `asciiEncodingSupport` | `Boolean` | — |
| `extendedAsciiEncodingSupport` | `Boolean` | — |
| `maxUserNameLength` | `Short` | — |
| `numberOfSupportedUsers` | `Integer` | — |
| `supportedCredentialRules` | `List<Integer>` | — |
| `supportedUserTypes` | `List<Integer>` | — |
| `userChecksumSupport` | `Boolean` | — |
| `userScheduleSupport` | `Boolean` | — |
| `utf16EncodingSupport` | `Boolean` | — |
