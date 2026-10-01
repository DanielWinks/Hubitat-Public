# HumidityControlSetpointSupportedReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolsetpointv1-humiditycontrolsetpointsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolsetpointv1.HumidityControlSetpointSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getDehumidifier` | `Boolean getDehumidifier()` | Reports whether bit 1 of bitmask is set for dehumidifier. Returns: dehumidifier value |
| `getHumidifier` | `Boolean getHumidifier()` | Reports whether bit 0 of bitmask is set for humidifier. Returns: humidifier value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `HumidityControlSetpointSupportedReport` | `HumidityControlSetpointSupportedReport()` | Creates the command with its declared field defaults. |
| `HumidityControlSetpointSupportedReport` | `HumidityControlSetpointSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setDehumidifier` | `void setDehumidifier(Boolean value)` | Sets the dehumidifier value used by this command. Parameters: value - dehumidifier value to encode |
| `setHumidifier` | `void setHumidifier(Boolean value)` | Sets the humidifier value used by this command. Parameters: value - humidifier value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `bitmask` | `Short` | — |
