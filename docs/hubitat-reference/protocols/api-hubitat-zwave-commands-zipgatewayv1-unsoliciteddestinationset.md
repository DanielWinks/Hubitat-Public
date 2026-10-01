# UnsolicitedDestinationSet

- **ID:** `api-hubitat-zwave-commands-zipgatewayv1-unsoliciteddestinationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipgatewayv1.UnsolicitedDestinationSet`

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
| `getUnsolicitedDestinationPort` | `Integer getUnsolicitedDestinationPort()` | Returns the unsolicited destination port value stored in unsolicitedDestinationPort. Returns: unsolicited destination port value |
| `getUnsolicitedIPv6Destination` | `List<Short> getUnsolicitedIPv6Destination()` | Returns the unsolicited ipv6 destination value stored in unsolicitedIPv6Destination. Returns: unsolicited ipv6 destination value |
| `setUnsolicitedDestinationPort` | `void setUnsolicitedDestinationPort(Integer unsolicitedDestinationPort)` | Sets the unsolicited destination port value used by this command. Parameters: unsolicitedDestinationPort - unsolicited destination port value to encode |
| `setUnsolicitedIPv6Destination` | `void setUnsolicitedIPv6Destination(List<Short> unsolicitedIPv6Destination)` | Sets the unsolicited ipv6 destination value used by this command. Parameters: unsolicitedIPv6Destination - unsolicited ipv6 destination value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `UnsolicitedDestinationSet` | `UnsolicitedDestinationSet()` | Creates the command with its declared field defaults. |
| `UnsolicitedDestinationSet` | `UnsolicitedDestinationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
