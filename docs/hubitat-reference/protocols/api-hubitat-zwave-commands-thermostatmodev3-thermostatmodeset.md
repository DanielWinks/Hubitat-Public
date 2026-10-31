# ThermostatModeSet

- **ID:** `api-hubitat-zwave-commands-thermostatmodev3-thermostatmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.thermostatmodev2.ThermostatModeSet`
- [ThermostatModeSet](../protocols/api-hubitat-zwave-commands-thermostatmodev2-thermostatmodeset.md)
- [ThermostatModeSet](../protocols/api-hubitat-zwave-commands-thermostatmodev2-thermostatmodeset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getNoOfManufacturerDataFields` | `Short getNoOfManufacturerDataFields()` | Returns the no of manufacturer data fields value stored in bits 5 through 7 of properties1. Returns: no of manufacturer data fields value |
| `setNoOfManufacturerDataFields` | `void setNoOfManufacturerDataFields(Short value)` | Sets the no of manufacturer data fields value used by this command. Parameters: value - no of manufacturer data fields value to encode |
| `ThermostatModeSet` | `ThermostatModeSet()` | Creates the command with its declared field defaults. |
| `ThermostatModeSet` | `ThermostatModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `manufacturerData` | `List<Short>` | — |
| `MODE_FULL_POWER` | `short` | — |
| `MODE_MANUFACTURER_SPECIFIC` | `short` | — |
