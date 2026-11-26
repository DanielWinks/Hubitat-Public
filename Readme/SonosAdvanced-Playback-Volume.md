# Sonos Advanced playback volume arguments

Optional volume arguments on playback, announcement, and queue commands treat
`0` as “not specified.” This preserves integrations such as webCoRE that store
zero in an optional volume field when no value was selected.

Use `-1` when a command should receive an explicit volume of zero. This applies
to player and group commands such as `playTrack`, `playHighPriorityTrack`,
`playText`, `playTextAndRestore`, `playTextAndResume`, `speak`,
`speakNoRestore`, `playHighPriorityTTS`, and `enqueueLowPriorityTrack`.

Non-audioClip TTS announcements temporarily set the Sonos group volume and
restore the prior level when the message finishes, provided the volume has not
been changed independently during playback. AudioClip announcements set the
clip volume without changing the group volume. Regular `playTrack` playback
keeps its requested volume as the listening level.

Direct volume commands such as `setLevel(0)` and `setGroupVolume(0)` continue to
set the volume to zero.

## Reported group volume and mute

Sonos Advanced no longer subscribes to the Sonos GroupRenderingControl service.
`groupVolume` and `groupMute` are calculated by the app from each player's own
RenderingControl `volume` and `mute` reports. Group volume is the rounded average
of the selected players' volume (half up); group mute is `muted` only when every
selected player is muted. Group volume commands such as `setGroupVolume` still
use the Sonos group API.

Player devices report their live Sonos group. A group device chooses its players
from its live state and two settings, which the group driver publishes to the app
and the app caches (`groupPreferencesByDni`):

| Live Sonos state | Use proportional volume | Control ungrouped individually | Players used |
|---|---|---|---|
| Grouped | On | Either | Live Sonos group members |
| Grouped | Off | Either | Players configured on the group device |
| Ungrouped | Either | On | Players configured on the group device |
| Ungrouped | Either | Off | Coordinator only |

No calculated group volume is published while none of the selected players has
reported a valid volume.
