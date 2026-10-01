# SamsungTV

- **ID:** `capability-samsungtv`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.samsungTV

## Methods

| Name | Signature | Description |
|---|---|---|
| `mute` | `mute()` | Command defined by SamsungTV. |
| `off` | `off()` | Command defined by SamsungTV. |
| `on` | `on()` | Command defined by SamsungTV. |
| `setPictureMode` | `setPictureMode(ENUM argument1)` | Command defined by SamsungTV. |
| `setSoundMode` | `setSoundMode(ENUM argument1)` | Command defined by SamsungTV. |
| `setVolume` | `setVolume(NUMBER argument1)` | Command defined by SamsungTV. |
| `showMessage` | `showMessage(STRING argument1, STRING argument2, STRING argument3, STRING argument4)` | Command defined by SamsungTV. |
| `unmute` | `unmute()` | Command defined by SamsungTV. |
| `volumeDown` | `volumeDown()` | Command defined by SamsungTV. |
| `volumeUp` | `volumeUp()` | Command defined by SamsungTV. |

## Properties

| Name | Type | Description |
|---|---|---|
| `messageButton` | `JSON_OBJECT` | No fixed values are declared. |
| `mute` | `ENUM` | Defined values: muted, unknown, unmuted |
| `pictureMode` | `ENUM` | Defined values: unknown, standard, movie, dynamic |
| `soundMode` | `ENUM` | Defined values: speech, movie, unknown, standard, music |
| `switch` | `ENUM` | Defined values: on, off |
| `volume` | `NUMBER` | No fixed values are declared. |
