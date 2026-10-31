# SupervisionReport

- **ID:** `api-hubitat-zwave-commands-supervisionv1-supervisionreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.supervisionv1.SupervisionReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: null |
| `getMoreStatusUpdates` | `Boolean getMoreStatusUpdates()` | Reports whether bit 7 of properties1 is set for more status updates. Returns: more status updates value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSessionID` | `Short getSessionID()` | Returns the session id value stored in bits 0 through 5 of properties1. Returns: session id value |
| `setMoreStatusUpdates` | `void setMoreStatusUpdates(Boolean value)` | Sets the more status updates value used by this command. Parameters: value - more status updates value to encode |
| `setSessionID` | `void setSessionID(Short value)` | Encodes session id in bits 0 through 5 of properties1 and preserves the bits selected by 0xC0. Parameters: value - session id value to encode |
| `SupervisionReport` | `SupervisionReport()` | Creates the command with its declared field defaults. |
| `SupervisionReport` | `SupervisionReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Short` | — |
| `FAIL` | `Short` | — |
| `NO_SUPPORT` | `Short` | — |
| `reserved` | `Short` | — |
| `status` | `Short` | — |
| `SUCCESS` | `Short` | — |
| `WORKING` | `Short` | — |
