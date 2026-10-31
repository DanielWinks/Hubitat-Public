# UnsolicitedDestinationGet

- **ID:** `api-hubitat-zwave-commands-zipgatewayv1-unsoliciteddestinationget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipgatewayv1.UnsolicitedDestinationGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `UnsolicitedDestinationGet` | `UnsolicitedDestinationGet()` | Creates the command with its declared field defaults. |
| `UnsolicitedDestinationGet` | `UnsolicitedDestinationGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
