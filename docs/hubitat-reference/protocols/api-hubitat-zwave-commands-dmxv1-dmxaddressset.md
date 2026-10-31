# DmxAddressSet

- **ID:** `api-hubitat-zwave-commands-dmxv1-dmxaddressset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.dmxv1.DmxAddressSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DmxAddressSet` | `DmxAddressSet()` | Creates the command with its declared field defaults. |
| `DmxAddressSet` | `DmxAddressSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPageId` | `Short getPageId()` | Returns the page id value stored in bits 0 through 3 of properties1. Returns: page id value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setPageId` | `void setPageId(Short pageId)` | Encodes page id in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: pageId - page id value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `channelId` | `Short` | — |
