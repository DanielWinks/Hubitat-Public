# HumidityControlModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-humiditycontrolmodev1-humiditycontrolmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontrolmodev1.HumidityControlModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getModeDehumidify` | `Boolean getModeDehumidify()` | Reports whether bit 2 of bitmask is set for mode dehumidify. Returns: mode dehumidify value |
| `getModeHumidify` | `Boolean getModeHumidify()` | Reports whether bit 1 of bitmask is set for mode humidify. Returns: mode humidify value |
| `getModeOff` | `Boolean getModeOff()` | Reports whether bit 0 of bitmask is set for mode off. Returns: mode off value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `HumidityControlModeSupportedReport` | `HumidityControlModeSupportedReport()` | Creates the command with its declared field defaults. |
| `HumidityControlModeSupportedReport` | `HumidityControlModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setModeDehumidify` | `void setModeDehumidify(Boolean value)` | Sets the mode dehumidify value used by this command. Parameters: value - mode dehumidify value to encode |
| `setModeHumidify` | `void setModeHumidify(Boolean value)` | Sets the mode humidify value used by this command. Parameters: value - mode humidify value to encode |
| `setModeOff` | `void setModeOff(Boolean value)` | Sets the mode off value used by this command. Parameters: value - mode off value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `bitmask` | `Short` | — |
