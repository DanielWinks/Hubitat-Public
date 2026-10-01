# IrrigationValveInfoReport

- **ID:** `api-hubitat-zwave-commands-irrigationv1-irrigationvalveinforeport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.irrigationv1.IrrigationValveInfoReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getConnected` | `Boolean getConnected()` | Reports whether bit 1 of properties1 is set for connected. Returns: connected value |
| `getMaster` | `Boolean getMaster()` | Returns the master value exposed by this command. Returns: master value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `IrrigationValveInfoReport` | `IrrigationValveInfoReport()` | Creates the command with its declared field defaults. |
| `IrrigationValveInfoReport` | `IrrigationValveInfoReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `setConnected` | `void setConnected(Boolean value)` | Sets the connected value used by this command. Parameters: value - connected value to encode |
| `setMaster` | `void setMaster(Boolean value)` | Sets the master value used by this command. Parameters: value - master value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `nominalCurrent` | `Short` | — |
| `VALVE_ERROR_CURRENT_HIGH_THRESHOLD` | `short` | — |
| `VALVE_ERROR_CURRENT_LOW_THRESHOLD` | `short` | — |
| `VALVE_ERROR_FLOW_HIGH_THRESHOLD` | `short` | — |
| `VALVE_ERROR_FLOW_LOW_THRESHOLD` | `short` | — |
| `VALVE_ERROR_MAXIMUM_FLOW` | `short` | — |
| `VALVE_ERROR_SHORT_CIRCUIT` | `short` | — |
| `valveErrorStatus` | `Short` | — |
| `valveID` | `Short` | — |
