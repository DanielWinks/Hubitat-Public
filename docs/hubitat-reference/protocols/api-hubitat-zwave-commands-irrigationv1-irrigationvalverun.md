# IrrigationValveRun

- **ID:** `api-hubitat-zwave-commands-irrigationv1-irrigationvalverun`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.irrigationv1.IrrigationValveRun`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMasterValve` | `Boolean getMasterValve()` | Reports whether bit 0 of properties1 is set for master valve. Returns: master valve value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `IrrigationValveRun` | `IrrigationValveRun()` | Creates the command with its declared field defaults. |
| `IrrigationValveRun` | `IrrigationValveRun(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `setMasterValve` | `void setMasterValve(Boolean value)` | Sets the master valve value used by this command. Parameters: value - master valve value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `duration` | `Integer` | — |
| `valveID` | `Short` | — |
