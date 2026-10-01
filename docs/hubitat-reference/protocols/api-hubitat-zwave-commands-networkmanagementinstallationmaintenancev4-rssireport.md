# RssiReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev4-rssireport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev4.RssiReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementinstallationmaintenancev3.RssiReport`
- [RssiReport](../protocols/api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev3-rssireport.md)
- [RssiReport](../protocols/api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev3-rssireport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `RssiReport` | `RssiReport()` | Creates the command with its declared field defaults. |
| `RssiReport` | `RssiReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `lrPrimaryRssi` | `Short` | — |
| `lrSecondaryRssi` | `Short` | — |
