# SwitchMultilevelReport

- **ID:** `api-hubitat-zwave-commands-switchmultilevelv4-switchmultilevelreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchmultilevelv4.SwitchMultilevelReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport`
- [SwitchMultilevelReport](../protocols/api-hubitat-zwave-commands-switchmultilevelv3-switchmultilevelreport.md)
- [SwitchMultilevelReport](../protocols/api-hubitat-zwave-commands-switchmultilevelv3-switchmultilevelreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchMultilevelReport` | `SwitchMultilevelReport()` | Creates the command with its declared field defaults. |
| `SwitchMultilevelReport` | `SwitchMultilevelReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SwitchMultilevelReport` | `SwitchMultilevelReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Short` | — |
| `targetValue` | `Short` | — |
