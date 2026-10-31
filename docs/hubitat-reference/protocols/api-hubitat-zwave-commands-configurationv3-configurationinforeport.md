# ConfigurationInfoReport

- **ID:** `api-hubitat-zwave-commands-configurationv3-configurationinforeport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.configurationv3.ConfigurationInfoReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ConfigurationInfoReport` | `ConfigurationInfoReport()` | Creates the command with its declared field defaults. |
| `ConfigurationInfoReport` | `ConfigurationInfoReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ConfigurationInfoReport` | `ConfigurationInfoReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getInfo` | `String getInfo()` | Returns the info value stored in retString. Returns: info value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setInfo` | `void setInfo(String value)` | Sets the info value used by this command. Parameters: value - info value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `parameterNumber` | `Integer` | — |
| `reportsToFollow` | `Short` | — |
