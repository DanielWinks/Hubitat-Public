# Sonos Advanced Controller client tests

These scripts exercise the commands advertised by a Hubitat Sonos Advanced
Player or Group device through the local Hubitat HTTP and WebSocket endpoints.

Install the only external dependency:

```bash
python3 -m pip install -r scripts/requirements-sonos-test.txt
```

Save the JSON shown by the Hubitat device page to a file, for example
`/private/tmp/sonos-player-530.json`. Use the matching example profile as a
starting point and adjust media IDs/URIs for the household.

The scripts are deliberately serialized:

- They wait for a matching device event, relevant command log, expected error,
  or timeout before advancing.
- Normal commands wait five seconds after the terminal response.
- Group-topology commands wait ten seconds after the terminal response before
  the next topology-changing request.
- A timeout also receives the cooldown before the next command.
- Topology-changing and playback commands require explicit confirmation.

Start with a dry run:

```bash
python3 scripts/test_sonos_player.py \
  --hub 192.168.1.4 \
  --device-json /private/tmp/sonos-player-530.json \
  --profile scripts/player-test-profile.example.json \
  --dry-run
```

Run one low-risk command first:

```bash
python3 scripts/test_sonos_player.py \
  --hub 192.168.1.4 \
  --device-json /private/tmp/sonos-player-530.json \
  --profile scripts/player-test-profile.example.json \
  --command initialize \
  --output /private/tmp/sonos-player-initialize.json
```

Run the full player suite only after reviewing the plan:

```bash
python3 scripts/test_sonos_player.py \
  --hub 192.168.1.4 \
  --device-json /private/tmp/sonos-player-530.json \
  --profile scripts/player-test-profile.example.json \
  --confirm-destructive \
  --continue-on-error \
  --output /private/tmp/sonos-player-results.json
```

Run the group suite with group-device JSON and the group profile:

```bash
python3 scripts/test_sonos_group.py \
  --hub 192.168.1.4 \
  --device-json /private/tmp/sonos-group.json \
  --profile scripts/group-test-profile.example.json \
  --confirm-destructive \
  --continue-on-error \
  --output /private/tmp/sonos-group-results.json
```

The result JSON includes every request, HTTP response, captured event, log,
timeout, expected error, socket parse error, and cooldown used. Commands whose
arguments are not present in the profile are reported as
`NEEDS_CONFIGURATION` instead of being guessed.

The scripts do not authenticate to the Hubitat local endpoints because the
provided local endpoints do not require authentication. Keep them on the
trusted LAN and do not commit captured device JSON or result files.
