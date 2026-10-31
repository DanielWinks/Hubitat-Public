# TimedSession

- **ID:** `capability-timedsession`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.timedSession

## Methods

| Name | Signature | Description |
|---|---|---|
| `cancel` | `cancel()` | Command defined by TimedSession. |
| `pause` | `pause()` | Command defined by TimedSession. |
| `setTimeRemaining` | `setTimeRemaining(NUMBER argument1)` | Command defined by TimedSession. |
| `start` | `start()` | Command defined by TimedSession. |
| `stop` | `stop()` | Command defined by TimedSession. |

## Properties

| Name | Type | Description |
|---|---|---|
| `sessionStatus` | `ENUM` | Defined values: stopped, canceled, running, paused |
| `timeRemaining` | `NUMBER` | No fixed values are declared. |
