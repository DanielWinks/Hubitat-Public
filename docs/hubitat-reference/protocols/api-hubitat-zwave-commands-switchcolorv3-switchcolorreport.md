# SwitchColorReport

- **ID:** `api-hubitat-zwave-commands-switchcolorv3-switchcolorreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchcolorv3.SwitchColorReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchcolorv2.SwitchColorReport`
- [SwitchColorReport](../protocols/api-hubitat-zwave-commands-switchcolorv2-switchcolorreport.md)
- [SwitchColorReport](../protocols/api-hubitat-zwave-commands-switchcolorv2-switchcolorreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getColorComponent` | `String getColorComponent()` | Returns the color component value stored in cc. Returns: color component value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchColorReport` | `SwitchColorReport()` | Creates the command with its declared field defaults. |
| `SwitchColorReport` | `SwitchColorReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SwitchColorReport` | `SwitchColorReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `colorComponentId` | `short` | — |
| `dimmingDuration` | `short` | — |
| `targetValue` | `short` | — |
| `value` | `short` | — |
