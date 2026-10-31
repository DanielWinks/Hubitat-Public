# SwitchBinaryReport

- **ID:** `api-hubitat-zwave-commands-switchbinaryv2-switchbinaryreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport`
- [SwitchBinaryReport](../protocols/api-hubitat-zwave-commands-switchbinaryv1-switchbinaryreport.md)
- [SwitchBinaryReport](../protocols/api-hubitat-zwave-commands-switchbinaryv1-switchbinaryreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchBinaryReport` | `SwitchBinaryReport()` | Creates the command with its declared field defaults. |
| `SwitchBinaryReport` | `SwitchBinaryReport(List<Map<String, Object>> payload)` | Initializes switch report fields from Z-Wave JS property updates. Parameters: payload - non-null list of maps. property selects the update: "currentValue" and "targetValue" pass ne |
| `SwitchBinaryReport` | `SwitchBinaryReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload or a decoded payload with fewer than 1 values keeps the declared field defaults. Payload bytes are separate |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Short` | — |
| `targetValue` | `Short` | — |
| `value` | `Short` | — |
