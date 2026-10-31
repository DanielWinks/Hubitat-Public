# ConfigurationNameReport

- **ID:** `api-hubitat-zwave-commands-configurationv3-configurationnamereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.configurationv3.ConfigurationNameReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ConfigurationNameReport` | `ConfigurationNameReport()` | Creates the command with its declared field defaults. |
| `ConfigurationNameReport` | `ConfigurationNameReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ConfigurationNameReport` | `ConfigurationNameReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getName` | `String getName()` | Returns the name value stored in retString. Returns: name value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setName` | `void setName(String value)` | Sets the name value used by this command. Parameters: value - name value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `parameterNumber` | `Integer` | — |
| `reportsToFollow` | `Short` | — |
