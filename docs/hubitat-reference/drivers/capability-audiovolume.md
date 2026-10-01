# AudioVolume

- **ID:** `capability-audiovolume`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.audioVolume

## Methods

| Name | Signature | Description |
|---|---|---|
| `mute` | `mute()` | Command defined by AudioVolume. |
| `setVolume` | `setVolume(NUMBER Volume level)` | Command defined by AudioVolume. |
| `unmute` | `unmute()` | Command defined by AudioVolume. |
| `volumeDown` | `volumeDown()` | Command defined by AudioVolume. |
| `volumeUp` | `volumeUp()` | Command defined by AudioVolume. |

## Properties

| Name | Type | Description |
|---|---|---|
| `mute` | `ENUM` | Defined values: unmuted, muted |
| `volume` | `NUMBER` | No fixed values are declared. |
