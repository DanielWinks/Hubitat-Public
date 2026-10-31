# VideoCamera

- **ID:** `capability-videocamera`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.videoCapture

## Methods

| Name | Signature | Description |
|---|---|---|
| `flip` | `flip()` | Command defined by VideoCamera. |
| `mute` | `mute()` | Command defined by VideoCamera. |
| `off` | `off()` | Command defined by VideoCamera. |
| `on` | `on()` | Command defined by VideoCamera. |
| `unmute` | `unmute()` | Command defined by VideoCamera. |

## Properties

| Name | Type | Description |
|---|---|---|
| `camera` | `ENUM` | Defined values: on, off, restarting, unavailable |
| `mute` | `ENUM` | Defined values: unmuted, muted |
| `settings` | `JSON_OBJECT` | No fixed values are declared. |
| `statusMessage` | `STRING` | No fixed values are declared. |
