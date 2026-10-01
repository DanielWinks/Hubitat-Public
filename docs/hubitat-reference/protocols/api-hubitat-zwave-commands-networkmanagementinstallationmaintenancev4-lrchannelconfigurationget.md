# LrChannelConfigurationGet

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev4-lrchannelconfigurationget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev4.LrChannelConfigurationGet`

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
| `LrChannelConfigurationGet` | `LrChannelConfigurationGet()` | Creates the command with its declared field defaults. |
| `LrChannelConfigurationGet` | `LrChannelConfigurationGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
