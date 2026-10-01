# ThermostatFanStateReport

- **ID:** `api-hubitat-zwave-commands-thermostatfanstatev1-thermostatfanstatereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ThermostatFanStateReport` | `ThermostatFanStateReport()` | Creates the command with its declared field defaults. |
| `ThermostatFanStateReport` | `ThermostatFanStateReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatFanStateReport` | `ThermostatFanStateReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `FAN_OPERATING_STATE_IDLE_OFF` | `short` | — |
| `FAN_OPERATING_STATE_RUNNING` | `short` | — |
| `FAN_OPERATING_STATE_RUNNING_HIGH` | `short` | — |
| `fanOperatingState` | `Short` | — |
