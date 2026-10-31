# UserCodeReport

- **ID:** `api-hubitat-zwave-commands-usercodev1-usercodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercodev1.UserCodeReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Represents the UserCodeReport Z-Wave command frame. |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `UserCodeReport` | `UserCodeReport()` | Creates the command with its declared field defaults. |
| `UserCodeReport` | `UserCodeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `UserCodeReport` | `UserCodeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `USER_ID_STATUS_AVAILABLE_NOT_SET` | `Short` | — |
| `USER_ID_STATUS_OCCUPIED` | `Short` | — |
| `USER_ID_STATUS_RESERVED_BY_ADMINISTRATOR` | `Short` | — |
| `USER_ID_STATUS_STATUS_NOT_AVAILABLE` | `Short` | — |
| `userCode` | `String` | — |
| `userIdentifier` | `Short` | — |
| `userIdStatus` | `Short` | — |
