# BasicReport

- **ID:** `api-hubitat-zwave-commands-basicv2-basicreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.basicv2.BasicReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.basicv1.BasicReport`
- [BasicReport](../protocols/api-hubitat-zwave-commands-basicv1-basicreport.md)
- [BasicReport](../protocols/api-hubitat-zwave-commands-basicv1-basicreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `BasicReport` | `BasicReport()` | Creates the command with its declared field defaults. |
| `BasicReport` | `BasicReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `BasicReport` | `BasicReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Short` | — |
| `targetValue` | `Short` | — |
| `value` | `Short` | — |
