# FanControl

- **ID:** `capability-fancontrol`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.fanControl

## Methods

| Name | Signature | Description |
|---|---|---|
| `cycleSpeed` | `cycleSpeed()` | Command defined by FanControl. |
| `setSpeed` | `setSpeed(ENUM Fan speed)` | Command defined by FanControl. |

## Properties

| Name | Type | Description |
|---|---|---|
| `speed` | `ENUM` | Defined values: low, medium-low, medium, medium-high, high, on, off, auto |
| `supportedFanSpeeds` | `JSON_OBJECT` | No fixed values are declared. |
