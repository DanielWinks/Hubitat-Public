# CommandConfigurationReport

- **ID:** `api-hubitat-zwave-commands-associationcommandconfigurationv1-commandconfigurationreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.associationcommandconfigurationv1.CommandConfigurationReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CommandConfigurationReport` | `CommandConfigurationReport()` | Creates the command with its declared field defaults. |
| `CommandConfigurationReport` | `CommandConfigurationReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `commandByte` | `List<Short>` | — |
| `commandClassIdentifier` | `Short` | — |
| `commandIdentifier` | `Short` | — |
| `commandLength` | `Short` | — |
| `first` | `Boolean` | — |
| `groupingIdentifier` | `Short` | — |
| `nodeId` | `Short` | — |
| `reportsToFollow` | `Short` | — |
