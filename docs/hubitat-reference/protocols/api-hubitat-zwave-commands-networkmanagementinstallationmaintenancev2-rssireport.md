# RssiReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev2-rssireport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev2.RssiReport`

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
| `RssiReport` | `RssiReport()` | Creates the command with its declared field defaults. |
| `RssiReport` | `RssiReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `channel1` | `Short` | — |
| `channel2` | `Short` | — |
| `channel3` | `Short` | — |
| `RSSI_BELOW_SENSITIVITY` | `Short` | — |
| `RSSI_MAX_POWER_SATURATED` | `Short` | — |
| `RSSI_NOT_AVAILABLE` | `Short` | — |
