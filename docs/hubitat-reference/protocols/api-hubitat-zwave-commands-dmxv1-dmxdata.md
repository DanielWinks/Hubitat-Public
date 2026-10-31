# DmxData

- **ID:** `api-hubitat-zwave-commands-dmxv1-dmxdata`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.dmxv1.DmxData`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DmxData` | `DmxData()` | Creates the command with its declared field defaults. |
| `DmxData` | `DmxData(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPage` | `Short getPage()` | Returns the page value stored in bits 0 through 3 of properties1. Returns: page value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSequenceNo` | `Short getSequenceNo()` | Returns the sequence no value stored in bits 4 through 5 of properties1. Returns: sequence no value |
| `setPage` | `void setPage(Short page)` | Encodes page in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: page - page value to encode |
| `setSequenceNo` | `void setSequenceNo(Short seqNo)` | Sets the sequence no value used by this command. Parameters: seqNo - sequence no value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `dmxChannel` | `List<Short>` | — |
| `source` | `Short` | — |
