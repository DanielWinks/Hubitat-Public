# HumidityControlOperatingStateReport

- **ID:** `api-hubitat-zwave-commands-humiditycontroloperatingstatev1-humiditycontroloperatingstatereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.humiditycontroloperatingstatev1.HumidityControlOperatingStateReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getOperatingState` | `Short getOperatingState()` | Returns the operating state value stored in bits 0 through 3 of properties1. Returns: operating state value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `HumidityControlOperatingStateReport` | `HumidityControlOperatingStateReport()` | Creates the command with its declared field defaults. |
| `HumidityControlOperatingStateReport` | `HumidityControlOperatingStateReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setOperatingState` | `void setOperatingState(Short value)` | Encodes operating state in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - operating state value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `OPERATING_STATE_DEHUMIDIFYING` | `short` | — |
| `OPERATING_STATE_HUMIDIFYING` | `short` | — |
| `OPERATING_STATE_IDLE` | `short` | — |
