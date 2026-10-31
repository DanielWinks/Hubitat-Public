# ThermostatOperatingStateReport

- **ID:** `api-hubitat-zwave-commands-thermostatoperatingstatev1-thermostatoperatingstatereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport`

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
| `ThermostatOperatingStateReport` | `ThermostatOperatingStateReport()` | Creates the command with its declared field defaults. |
| `ThermostatOperatingStateReport` | `ThermostatOperatingStateReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `ThermostatOperatingStateReport` | `ThermostatOperatingStateReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `OPERATING_STATE_COOLING` | `Short` | — |
| `OPERATING_STATE_FAN_ONLY` | `Short` | — |
| `OPERATING_STATE_HEATING` | `Short` | — |
| `OPERATING_STATE_IDLE` | `Short` | — |
| `OPERATING_STATE_PENDING_COOL` | `Short` | — |
| `OPERATING_STATE_PENDING_HEAT` | `Short` | — |
| `OPERATING_STATE_VENT_ECONOMIZER` | `Short` | — |
| `operatingState` | `Short` | — |
