# WindowBlind

- **ID:** `capability-windowblind`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.windowBlind

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `close()` | Command defined by WindowBlind. |
| `open` | `open()` | Command defined by WindowBlind. |
| `setPosition` | `setPosition(NUMBER Position)` | Command defined by WindowBlind. |
| `setTiltLevel` | `setTiltLevel(NUMBER Tilt)` | Command defined by WindowBlind. |
| `startPositionChange` | `startPositionChange(ENUM Position)` | Command defined by WindowBlind. |
| `stopPositionChange` | `stopPositionChange()` | Command defined by WindowBlind. |

## Properties

| Name | Type | Description |
|---|---|---|
| `position` | `NUMBER` | No fixed values are declared. |
| `tilt` | `NUMBER` | No fixed values are declared. |
| `windowShade` | `ENUM` | Defined values: opening, partially open, closed, open, closing, unknown |
