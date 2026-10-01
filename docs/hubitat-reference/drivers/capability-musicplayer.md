# MusicPlayer

- **ID:** `capability-musicplayer`
- **Section:** Drivers
- **Class:** ``
- **Kind:** capability

> Firmware-defined capability contract.

Source reference: capability.musicPlayer

## Methods

| Name | Signature | Description |
|---|---|---|
| `mute` | `mute()` | Command defined by MusicPlayer. |
| `nextTrack` | `nextTrack()` | Command defined by MusicPlayer. |
| `pause` | `pause()` | Command defined by MusicPlayer. |
| `play` | `play()` | Command defined by MusicPlayer. |
| `playText` | `playText(STRING Text)` | Command defined by MusicPlayer. |
| `playTrack` | `playTrack(STRING Track URI)` | Command defined by MusicPlayer. |
| `previousTrack` | `previousTrack()` | Command defined by MusicPlayer. |
| `restoreTrack` | `restoreTrack(STRING Track URI)` | Command defined by MusicPlayer. |
| `resumeTrack` | `resumeTrack(STRING Track URI)` | Command defined by MusicPlayer. |
| `setLevel` | `setLevel(NUMBER Volume level)` | Command defined by MusicPlayer. |
| `setTrack` | `setTrack(STRING Track URI)` | Command defined by MusicPlayer. |
| `stop` | `stop()` | Command defined by MusicPlayer. |
| `unmute` | `unmute()` | Command defined by MusicPlayer. |

## Properties

| Name | Type | Description |
|---|---|---|
| `level` | `NUMBER` | No fixed values are declared. |
| `mute` | `ENUM` | Defined values: unmuted, muted |
| `status` | `STRING` | No fixed values are declared. |
| `trackData` | `JSON_OBJECT` | No fixed values are declared. |
| `trackDescription` | `STRING` | No fixed values are declared. |
