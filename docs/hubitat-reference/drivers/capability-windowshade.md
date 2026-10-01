# WindowShade

- **ID:** `capability-windowshade`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.windowShade

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `close()` | Command defined by WindowShade. |
| `open` | `open()` | Command defined by WindowShade. |
| `setPosition` | `setPosition(NUMBER Position)` | Command defined by WindowShade. |
| `startPositionChange` | `startPositionChange(ENUM Position)` | Command defined by WindowShade. |
| `stopPositionChange` | `stopPositionChange()` | Command defined by WindowShade. |

## Properties

| Name | Type | Description |
|---|---|---|
| `position` | `NUMBER` | No fixed values are declared. |
| `windowShade` | `ENUM` | Defined values: opening, partially open, closed, open, closing, unknown |
