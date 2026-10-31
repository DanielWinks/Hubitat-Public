# Thermostat

- **ID:** `capability-thermostat`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.thermostat

## Methods

| Name | Signature | Description |
|---|---|---|
| `auto` | `auto()` | Command defined by Thermostat. |
| `cool` | `cool()` | Command defined by Thermostat. |
| `emergencyHeat` | `emergencyHeat()` | Command defined by Thermostat. |
| `fanAuto` | `fanAuto()` | Command defined by Thermostat. |
| `fanCirculate` | `fanCirculate()` | Command defined by Thermostat. |
| `fanOn` | `fanOn()` | Command defined by Thermostat. |
| `heat` | `heat()` | Command defined by Thermostat. |
| `off` | `off()` | Command defined by Thermostat. |
| `setCoolingSetpoint` | `setCoolingSetpoint(NUMBER Temperature)` | Command defined by Thermostat. |
| `setHeatingSetpoint` | `setHeatingSetpoint(NUMBER Temperature)` | Command defined by Thermostat. |
| `setThermostatFanMode` | `setThermostatFanMode(ENUM Fan mode)` | Command defined by Thermostat. |
| `setThermostatMode` | `setThermostatMode(ENUM Thermostat mode)` | Command defined by Thermostat. |

## Properties

| Name | Type | Description |
|---|---|---|
| `coolingSetpoint` | `NUMBER` | No fixed values are declared. |
| `heatingSetpoint` | `NUMBER` | No fixed values are declared. |
| `supportedThermostatFanModes` | `JSON_OBJECT` | No fixed values are declared. |
| `supportedThermostatModes` | `JSON_OBJECT` | No fixed values are declared. |
| `temperature` | `NUMBER` | No fixed values are declared. |
| `thermostatFanMode` | `ENUM` | Defined values: on, circulate, auto |
| `thermostatMode` | `ENUM` | Defined values: auto, off, heat, emergency heat, cool |
| `thermostatOperatingState` | `ENUM` | Defined values: heating, pending cool, pending heat, vent economizer, idle, cooling, fan only |
| `thermostatSetpoint` | `NUMBER` | No fixed values are declared. |
