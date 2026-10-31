# ProtectionReport

- **ID:** `api-hubitat-zwave-commands-protectionv2-protectionreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.protectionv2.ProtectionReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.protectionv1.ProtectionReport`
- [ProtectionReport](../protocols/api-hubitat-zwave-commands-protectionv1-protectionreport.md)
- [ProtectionReport](../protocols/api-hubitat-zwave-commands-protectionv1-protectionreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ProtectionReport` | `ProtectionReport()` | Creates the command with its declared field defaults. |
| `ProtectionReport` | `ProtectionReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ProtectionReport` | `ProtectionReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `localProtectionState` | `Short` | — |
| `rfProtectionState` | `Short` | — |
