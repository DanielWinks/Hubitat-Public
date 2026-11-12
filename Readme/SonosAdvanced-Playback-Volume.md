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
