# ConfigurationPropertiesReport

- **ID:** `api-hubitat-zwave-commands-configurationv4-configurationpropertiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.configurationv4.ConfigurationPropertiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.configurationv3.ConfigurationPropertiesReport`
- [ConfigurationPropertiesReport](../protocols/api-hubitat-zwave-commands-configurationv3-configurationpropertiesreport.md)
- [ConfigurationPropertiesReport](../protocols/api-hubitat-zwave-commands-configurationv3-configurationpropertiesreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ConfigurationPropertiesReport` | `ConfigurationPropertiesReport()` | Creates the command with its declared field defaults. |
| `ConfigurationPropertiesReport` | `ConfigurationPropertiesReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ConfigurationPropertiesReport` | `ConfigurationPropertiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getAdvanced` | `Boolean getAdvanced()` | Reports whether bit 0 of properties2 is set for advanced. Returns: advanced value |
| `getNoBulkSupport` | `Boolean getNoBulkSupport()` | Reports whether bit 1 of properties2 is set for no bulk support. Returns: no bulk support value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setAdvanced` | `void setAdvanced(Boolean value)` | Sets the advanced value used by this command. Parameters: value - advanced value to encode |
| `setNoBulkSupport` | `void setNoBulkSupport(Boolean value)` | Sets the no bulk support value used by this command. Parameters: value - no bulk support value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `isReadonly` | `boolean` | — |
