# ScreenAttributesReport

- **ID:** `api-hubitat-zwave-commands-screenattributesv1-screenattributesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.screenattributesv1.ScreenAttributesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getNumberOfLines` | `Short getNumberOfLines()` | Returns the number of lines value stored in bits 0 through 4 of properties1. Returns: number of lines value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ScreenAttributesReport` | `ScreenAttributesReport()` | Creates the command with its declared field defaults. |
| `ScreenAttributesReport` | `ScreenAttributesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `setNumberOfLines` | `void setNumberOfLines(Short value)` | Encodes number of lines in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - number of lines value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `numberOfCharactersPerLine` | `Short` | — |
| `numericalPresentationOfACharacter` | `Short` | — |
| `sizeOfLineBuffer` | `Short` | — |
