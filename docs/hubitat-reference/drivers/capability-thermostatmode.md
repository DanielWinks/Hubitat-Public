# ThermostatMode

- **ID:** `capability-thermostatmode`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.thermostatMode

## Methods

| Name | Signature | Description |
|---|---|---|
| `auto` | `auto()` | Command defined by ThermostatMode. |
| `cool` | `cool()` | Command defined by ThermostatMode. |
| `emergencyHeat` | `emergencyHeat()` | Command defined by ThermostatMode. |
| `heat` | `heat()` | Command defined by ThermostatMode. |
| `off` | `off()` | Command defined by ThermostatMode. |
| `setThermostatMode` | `setThermostatMode(ENUM Thermostat mode)` | Command defined by ThermostatMode. |

## Properties

| Name | Type | Description |
|---|---|---|
| `thermostatMode` | `ENUM` | Defined values: heat, cool, emergency heat, auto, off |
