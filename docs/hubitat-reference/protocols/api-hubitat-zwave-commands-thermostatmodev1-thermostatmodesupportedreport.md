# ThermostatModeSupportedReport

- **ID:** `api-hubitat-zwave-commands-thermostatmodev1-thermostatmodesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatmodev1.ThermostatModeSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAuto` | `Boolean getAuto()` | Reports whether bit 3 of backing value is set for auto. Returns: auto value |
| `getAutoChangeover` | `Boolean getAutoChangeover()` | Reports whether bit 2 of backing value is set for auto changeover. Returns: auto changeover value |
| `getAuxiliaryemergencyHeat` | `Boolean getAuxiliaryemergencyHeat()` | Reports whether bit 4 of backing value is set for auxiliaryemergency heat. Returns: auxiliaryemergency heat value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCool` | `Boolean getCool()` | Reports whether bit 2 of backing value is set for cool. Returns: cool value |
| `getDryAir` | `Boolean getDryAir()` | Reports whether bit 0 of backing value is set for dry air. Returns: dry air value |
| `getFanOnly` | `Boolean getFanOnly()` | Reports whether bit 6 of backing value is set for fan only. Returns: fan only value |
| `getFurnace` | `Boolean getFurnace()` | Reports whether bit 7 of backing value is set for furnace. Returns: furnace value |
| `getHeat` | `Boolean getHeat()` | Reports whether bit 1 of backing value is set for heat. Returns: heat value |
| `getMoistAir` | `Boolean getMoistAir()` | Reports whether bit 1 of backing value is set for moist air. Returns: moist air value |
| `getOff` | `Boolean getOff()` | Reports whether bit 0 of backing value is set for off. Returns: off value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getResume` | `Boolean getResume()` | Reports whether bit 5 of backing value is set for resume. Returns: resume value |
| `setAuto` | `void setAuto(Boolean value)` | Writes the auto value to payload position 0. Parameters: value - auto value to encode |
| `setAutoChangeover` | `void setAutoChangeover(Boolean value)` | Writes the auto changeover value to payload position 1. Parameters: value - auto changeover value to encode |
| `setAuxiliaryemergencyHeat` | `void setAuxiliaryemergencyHeat(Boolean value)` | Writes the auxiliaryemergency heat value to payload position 0. Parameters: value - auxiliaryemergency heat value to encode |
| `setCool` | `void setCool(Boolean value)` | Writes the cool value to payload position 0. Parameters: value - cool value to encode |
| `setDryAir` | `void setDryAir(Boolean value)` | Writes the dry air value to payload position 1. Parameters: value - dry air value to encode |
| `setFanOnly` | `void setFanOnly(Boolean value)` | Writes the fan only value to payload position 0. Parameters: value - fan only value to encode |
| `setFurnace` | `void setFurnace(Boolean value)` | Writes the furnace value to payload position 0. Parameters: value - furnace value to encode |
| `setHeat` | `void setHeat(Boolean value)` | Writes the heat value to payload position 0. Parameters: value - heat value to encode |
| `setMoistAir` | `void setMoistAir(Boolean value)` | Writes the moist air value to payload position 1. Parameters: value - moist air value to encode |
| `setOff` | `void setOff(Boolean value)` | Writes the off value to payload position 0. Parameters: value - off value to encode |
| `setResume` | `void setResume(Boolean value)` | Writes the resume value to payload position 0. Parameters: value - resume value to encode |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport()` | Creates the command with its declared field defaults. |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatModeSupportedReport` | `ThermostatModeSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
