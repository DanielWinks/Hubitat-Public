# MultiChannelEndPointReport

- **ID:** `api-hubitat-zwave-commands-multichannelv3-multichannelendpointreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.multichannelv3.MultiChannelEndPointReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.multichannelv2.MultiChannelEndPointReport`
- [MultiChannelEndPointReport](../protocols/api-hubitat-zwave-commands-multichannelv2-multichannelendpointreport.md)
- [MultiChannelEndPointReport](../protocols/api-hubitat-zwave-commands-multichannelv2-multichannelendpointreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `MultiChannelEndPointReport` | `MultiChannelEndPointReport()` | Creates the command with its declared field defaults. |
| `MultiChannelEndPointReport` | `MultiChannelEndPointReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `MultiChannelEndPointReport` | `MultiChannelEndPointReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `dynamic` | `Boolean` | — |
| `endPoints` | `Short` | — |
| `identical` | `Boolean` | — |
