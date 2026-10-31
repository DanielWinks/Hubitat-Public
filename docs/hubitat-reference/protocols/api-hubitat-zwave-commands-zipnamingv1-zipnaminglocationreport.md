# ZipNamingLocationReport

- **ID:** `api-hubitat-zwave-commands-zipnamingv1-zipnaminglocationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipnamingv1.ZipNamingLocationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `ZipNamingLocationReport` | `ZipNamingLocationReport()` | Creates the command with its declared field defaults. |
| `ZipNamingLocationReport` | `ZipNamingLocationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `location` | `String` | — |
