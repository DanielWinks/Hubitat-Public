# GroupingNameReport

- **ID:** `api-hubitat-zwave-commands-groupingnamev1-groupingnamereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.groupingnamev1.GroupingNameReport`

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
| `GroupingNameReport` | `GroupingNameReport()` | Creates the command with its declared field defaults. |
| `GroupingNameReport` | `GroupingNameReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `charPresentation` | `Short` | — |
| `grouping` | `List<Short>` | — |
| `groupingIdentifier` | `Short` | — |
