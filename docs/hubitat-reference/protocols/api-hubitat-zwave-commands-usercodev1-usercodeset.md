# UserCodeSet

- **ID:** `api-hubitat-zwave-commands-usercodev1-usercodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercodev1.UserCodeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Represents the UserCodeSet Z-Wave command frame. |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `UserCodeSet` | `UserCodeSet()` | Creates the command with its declared field defaults. |
| `UserCodeSet` | `UserCodeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

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
