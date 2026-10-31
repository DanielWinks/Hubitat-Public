# ZipNamingNameGet

- **ID:** `api-hubitat-zwave-commands-zipnamingv1-zipnamingnameget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipnamingv1.ZipNamingNameGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `ZipNamingNameGet` | `ZipNamingNameGet()` | Creates the command with its declared field defaults. |
| `ZipNamingNameGet` | `ZipNamingNameGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
