# IrrigationSystemInfoReport

- **ID:** `api-hubitat-zwave-commands-irrigationv1-irrigationsysteminforeport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.irrigationv1.IrrigationSystemInfoReport`

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
| `getValveTableMaxSize` | `Short getValveTableMaxSize()` | Returns the valve table max size value stored in bits 0 through 3 of properties2. Returns: valve table max size value |
| `IrrigationSystemInfoReport` | `IrrigationSystemInfoReport()` | Creates the command with its declared field defaults. |
| `IrrigationSystemInfoReport` | `IrrigationSystemInfoReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `setMasterValve` | `void setMasterValve(Boolean value)` | Sets the master valve value used by this command. Parameters: value - master valve value to encode |
| `setValveTableMaxSize` | `void setValveTableMaxSize(Short value)` | Encodes valve table max size in bits 0 through 3 of properties2 and preserves the bits selected by 0xF0. Parameters: value - valve table max size value to encode |

## Properties

| Name | Type | Description |
|---|---|---|
| `totalNumberOfValves` | `Short` | — |
| `totalNumberOfValveTables` | `Short` | — |
