# Sonos Advanced playback volume arguments

Optional volume arguments on playback, announcement, and queue commands treat
`0` as “not specified.” This preserves integrations such as webCoRE that store
zero in an optional volume field when no value was selected.

Use `-1` when a command should receive an explicit volume of zero. This applies
to player and group commands such as `playTrack`, `playHighPriorityTrack`,
`playText`, `playTextAndRestore`, `playTextAndResume`, `speak`,
`speakNoRestore`, `playHighPriorityTTS`, and `enqueueLowPriorityTrack`.

Direct volume commands such as `setLevel(0)` and `setGroupVolume(0)` continue to
set the volume to zero.
