# IpConfigurationSet

- **ID:** `api-hubitat-zwave-commands-ipconfigurationv1-ipconfigurationset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.ipconfigurationv1.IpConfigurationSet`

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
| `IpConfigurationSet` | `IpConfigurationSet()` | Creates the command with its declared field defaults. |
| `IpConfigurationSet` | `IpConfigurationSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `autoDns` | `Boolean` | — |
| `autoIp` | `Boolean` | — |
| `dns1` | `Integer` | — |
| `dns2` | `Integer` | — |
| `gateway` | `Integer` | — |
| `ipAddress` | `Integer` | — |
| `subnetMask` | `Integer` | — |
