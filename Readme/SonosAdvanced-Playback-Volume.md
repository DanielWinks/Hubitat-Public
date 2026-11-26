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

Every player subscribes to the Sonos GroupRenderingControl service, including
followers, so a player is ready if it becomes a coordinator. Only a coordinator
receives group notifications. Its driver relays the `groupVolume` and `groupMute`
values to the app, which applies them to the coordinator and every follower in the
live group, and caches the latest values so a player that joins later receives them
immediately. A notification from a player that is not the group's coordinator is
ignored, so the coordinator's report always wins. This keeps the values correct for
groups that mix Hubitat players with speakers that have no Hubitat device.

Group devices that follow the live group use the same coordinator values. Group
devices that use only their configured players calculate group volume as the rounded
average (half up) of those players' own `volume` reports, and group mute is `muted`
only when every one of them is muted. Group volume commands such as `setGroupVolume`
still use the Sonos group API.

A group device chooses its players from its live state and two settings. The group
driver publishes them to the app, and the app also caches the copy carried by every
volume, mute and refresh command (`groupPreferencesByDni`), so the app never calls a
group device to ask for them:

| Live Sonos state | Use proportional volume | Control ungrouped individually | Players used |
|---|---|---|---|
| Grouped | On | Either | Live Sonos group (coordinator's GroupRenderingControl) |
| Grouped | Off | Either | Players configured on the group device |
| Ungrouped | Either | On | Players configured on the group device |
| Ungrouped | Either | Off | Coordinator only |

No calculated group volume is published while none of the selected players has
reported a valid volume, and no live-group value is published until the coordinator
has reported its group volume.
