# TonePlayReport

- **ID:** `api-hubitat-zwave-commands-soundswitchv2-toneplayreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.soundswitchv2.TonePlayReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.soundswitchv1.TonePlayReport`
- [TonePlayReport](../protocols/api-hubitat-zwave-commands-soundswitchv1-toneplayreport.md)
- [TonePlayReport](../protocols/api-hubitat-zwave-commands-soundswitchv1-toneplayreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `TonePlayReport` | `TonePlayReport()` | Creates the command with its declared field defaults. |
| `TonePlayReport` | `TonePlayReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `TonePlayReport` | `TonePlayReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `volume` | `Short` | — |
