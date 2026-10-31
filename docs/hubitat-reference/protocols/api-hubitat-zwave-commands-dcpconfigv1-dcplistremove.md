# DcpListRemove

- **ID:** `api-hubitat-zwave-commands-dcpconfigv1-dcplistremove`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.dcpconfigv1.DcpListRemove`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DcpListRemove` | `DcpListRemove()` | Creates the command with its declared field defaults. |
| `DcpListRemove` | `DcpListRemove(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `day` | `Short` | — |
| `hourLocalTime` | `Short` | — |
| `minuteLocalTime` | `Short` | — |
| `month` | `Short` | — |
| `secondLocalTime` | `Short` | — |
| `year` | `Integer` | — |
