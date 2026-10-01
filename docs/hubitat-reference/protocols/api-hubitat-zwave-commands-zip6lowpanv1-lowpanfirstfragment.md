# LowpanFirstFragment

- **ID:** `api-hubitat-zwave-commands-zip6lowpanv1-lowpanfirstfragment`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zip6lowpanv1.LowpanFirstFragment`

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
| `LowpanFirstFragment` | `LowpanFirstFragment()` | Creates the command with its declared field defaults. |
| `LowpanFirstFragment` | `LowpanFirstFragment(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `datagramSize1` | `Short` | — |
| `datagramSize2` | `Short` | — |
| `datagramTag` | `Short` | — |
