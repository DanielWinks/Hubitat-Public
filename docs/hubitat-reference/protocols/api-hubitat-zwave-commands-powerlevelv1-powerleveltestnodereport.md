# PowerlevelTestNodeReport

- **ID:** `api-hubitat-zwave-commands-powerlevelv1-powerleveltestnodereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.powerlevelv1.PowerlevelTestNodeReport`

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
| `PowerlevelTestNodeReport` | `PowerlevelTestNodeReport()` | Creates the command with its declared field defaults. |
| `PowerlevelTestNodeReport` | `PowerlevelTestNodeReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `PowerlevelTestNodeReport` | `PowerlevelTestNodeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `STATUS_OF_OPERATION_ZW_TEST_FAILED` | `Short` | — |
| `STATUS_OF_OPERATION_ZW_TEST_INPROGRESS` | `Short` | — |
| `STATUS_OF_OPERATION_ZW_TEST_SUCCES` | `Short` | — |
| `statusOfOperation` | `Short` | — |
| `testFrameCount` | `Integer` | — |
| `testNodeid` | `Short` | — |
