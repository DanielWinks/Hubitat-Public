# Chime

- **ID:** `capability-chime`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.chime

## Methods

| Name | Signature | Description |
|---|---|---|
| `playSound` | `playSound(NUMBER Sound number)` | Command defined by Chime. |
| `stop` | `stop()` | Command defined by Chime. |

## Properties

| Name | Type | Description |
|---|---|---|
| `soundEffects` | `JSON_OBJECT` | No fixed values are declared. |
| `soundName` | `STRING` | No fixed values are declared. |
| `status` | `ENUM` | Defined values: playing, stopped |
