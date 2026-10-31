# ScreenAttributesReport

- **ID:** `api-hubitat-zwave-commands-screenattributesv2-screenattributesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.screenattributesv2.ScreenAttributesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.screenattributesv1.ScreenAttributesReport`
- [ScreenAttributesReport](../protocols/api-hubitat-zwave-commands-screenattributesv1-screenattributesreport.md)
- [ScreenAttributesReport](../protocols/api-hubitat-zwave-commands-screenattributesv1-screenattributesreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getEscapeSequence` | `Boolean getEscapeSequence()` | Reports whether bit 5 of properties1 is set for escape sequence. Returns: escape sequence value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ScreenAttributesReport` | `ScreenAttributesReport()` | Creates the command with its declared field defaults. |
| `ScreenAttributesReport` | `ScreenAttributesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 5 values also keeps the declared field |
| `setEscapeSequence` | `void setEscapeSequence(Boolean value)` | Sets the escape sequence value used by this command. Parameters: value - escape sequence value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `screenTimeout` | `Short` | — |
