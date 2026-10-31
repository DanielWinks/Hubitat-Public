# EntryControlEventSupportedReport

- **ID:** `api-hubitat-zwave-commands-entrycontrolv1-entrycontroleventsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.entrycontrolv1.EntryControlEventSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EntryControlEventSupportedReport` | `EntryControlEventSupportedReport()` | Creates the command with its declared field defaults. |
| `EntryControlEventSupportedReport` | `EntryControlEventSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `EntryControlEventSupportedReport` | `EntryControlEventSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `dataTypeSupportedBitMask` | `List<Short>` | — |
| `eventTypeSupportedBitMask` | `List<Short>` | — |
| `keyCachedSizeMax` | `Integer` | — |
| `keyCachedTimeoutMax` | `Integer` | — |
