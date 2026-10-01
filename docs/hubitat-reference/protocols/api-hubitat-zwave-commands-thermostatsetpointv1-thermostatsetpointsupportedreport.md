# ThermostatSetpointSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatsetpointv1-thermostatsetpointsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatsetpointv1.ThermostatSetpointSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAutoChangeover` | `Boolean getAutoChangeover()` | Reports whether bit 6 of backing value is set for auto changeover. Returns: auto changeover value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCooling` | `Boolean getCooling()` | Reports whether bit 2 of backing value is set for cooling. Returns: cooling value |
| `getDryAir` | `Boolean getDryAir()` | Reports whether bit 4 of backing value is set for dry air. Returns: dry air value |
| `getFurnace` | `Boolean getFurnace()` | Reports whether bit 3 of backing value is set for furnace. Returns: furnace value |
| `getHeating` | `Boolean getHeating()` | Reports whether bit 1 of backing value is set for heating. Returns: heating value |
| `getMoistAir` | `Boolean getMoistAir()` | Reports whether bit 5 of backing value is set for moist air. Returns: moist air value |
| `getNone` | `Boolean getNone()` | Reports whether bit 0 of backing value is set for none. Returns: none value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setAutoChangeover` | `void setAutoChangeover(Boolean value)` | Writes the auto changeover value to payload position 0. Parameters: value - auto changeover value to encode |
| `setCooling` | `void setCooling(Boolean value)` | Writes the cooling value to payload position 0. Parameters: value - cooling value to encode |
| `setDryAir` | `void setDryAir(Boolean value)` | Writes the dry air value to payload position 0. Parameters: value - dry air value to encode |
| `setFurnace` | `void setFurnace(Boolean value)` | Writes the furnace value to payload position 0. Parameters: value - furnace value to encode |
| `setHeating` | `void setHeating(Boolean value)` | Writes the heating value to payload position 0. Parameters: value - heating value to encode |
| `setMoistAir` | `void setMoistAir(Boolean value)` | Writes the moist air value to payload position 0. Parameters: value - moist air value to encode |
| `setNone` | `void setNone(Boolean value)` | Writes the none value to payload position 0. Parameters: value - none value to encode |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatSetpointSupportedReport` | `ThermostatSetpointSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `bitmask` | `List<Short>` | — |
