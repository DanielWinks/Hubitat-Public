#!/usr/bin/env python3
"""
MIT License
Copyright 2026 Daniel Winks

Client-side command and response regression runner for a Sonos Advanced Player.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from typing import Any, Dict, List, Optional

from hubitat_sac_test_common import (
    CommandPlan,
    CommandRunner,
    DeviceInfo,
    HubitatClient,
    HubitatSocketListener,
    MessageBus,
    add_common_arguments,
    command_arguments,
    configure_logging,
    load_json,
    print_summary,
    profile_value,
    write_json,
)


TOPOLOGY_COMMANDS = {
    "ungroupPlayer",
}

DESTRUCTIVE_COMMANDS = {
    "play", "pause", "stop", "nextTrack", "previousTrack",
    "loadFavorite", "loadFavoriteFull", "loadPlaylist", "loadPlaylistFull",
    "playTrack", "playTrackAndRestore", "playTrackAndResume", "resumeTrack",
    "restoreTrack", "playHighPriorityTrack", "playHighPriorityTTS", "speak", "playText",
    "playTextAndRestore", "playTextAndResume", "setTrack", "setVolume",
    "setLevel", "setVolumeZero", "volumeUp", "volumeDown", "mute",
    "unmute", "muteGroup", "unmuteGroup", "setGroupMute", "setGroupVolume",
    "groupVolumeUp", "groupVolumeDown", "selectLineIn", "selectTV",
    "ungroupPlayer",
}


def expected_for(command: str, info: DeviceInfo, profile: Dict[str, Any]) -> Dict[str, Any]:
    if command in {"play", "resumeTrack", "playTrack", "playTrackAndRestore", "playTrackAndResume", "loadFavorite", "loadFavoriteFull", "loadPlaylist", "loadPlaylistFull"}:
        return {"expected_state": "status", "expected_value": "playing", "expected_event_names": ["status", "transportStatus"]}
    if command == "pause":
        return {"expected_state": "status", "expected_value": "paused", "expected_event_names": ["status", "transportStatus"], "observe_only": True}
    if command == "stop":
        # A stopped stream may legitimately return ERROR_UNSUPPORTED_COMMAND.
        return {"expected_state": "status", "expected_value": "stopped", "expected_event_names": ["status", "transportStatus"], "observe_only": True}
    if command in {"mute", "muteGroup"}:
        return {"expected_state": "mute", "expected_value": "muted", "expected_event_names": ["mute", "groupMute"]}
    if command in {"unmute", "unmuteGroup"}:
        return {"expected_state": "mute", "expected_value": "unmuted", "expected_event_names": ["mute", "groupMute"]}
    if command == "setGroupMute":
        value = profile_value(profile, command, 0, "groupMute") or "unmuted"
        return {"expected_state": "groupMute", "expected_value": value, "expected_event_names": ["groupMute", "mute"]}
    if command in {"setVolume", "setLevel", "setVolumeZero"}:
        value = profile_value(profile, command, 0, "volume")
        return {"expected_state": "volume", "expected_value": value, "expected_event_names": ["volume", "level", "groupVolume"]}
    if command == "setGroupVolume":
        value = profile_value(profile, command, 0, "groupVolume")
        return {"expected_state": "groupVolume", "expected_value": value, "expected_event_names": ["groupVolume", "volume"]}
    if command in {"setBass", "setTreble", "setBalance", "setLoudness", "setNightMode", "setSpeechEnhancement", "setShuffle", "setRepeatMode", "setCrossfade"}:
        state_map = {
            "setBass": "bass", "setTreble": "treble", "setBalance": "balance",
            "setLoudness": "loudness", "setNightMode": "nightMode",
            "setSpeechEnhancement": "speechEnhancement", "setShuffle": "currentShuffleMode",
            "setRepeatMode": "currentRepeatAllMode", "setCrossfade": "currentCrossfadeMode",
        }
        state_name = state_map[command]
        configured = profile_value(profile, command, 0)
        expected_value = configured
        expected_names = [state_name]
        if command == "setRepeatMode":
            if configured == "repeat all":
                expected_value = "on"
                expected_names = ["currentRepeatAllMode"]
            elif configured == "repeat one":
                expected_value = "on"
                expected_names = ["currentRepeatOneMode"]
            else:
                expected_names = ["currentRepeatAllMode", "currentRepeatOneMode"]
                expected_value = "off"
        return {"expected_state": state_name, "expected_value": expected_value, "expected_event_names": expected_names}
    if command in {"groupVolumeUp", "groupVolumeDown", "volumeUp", "volumeDown"}:
        state_name = "groupVolume" if command.startswith("group") else "volume"
        return {"expected_state": state_name, "expected_event_names": [state_name, "volume", "level"], "observe_only": True}
    if command in {"getFavorites", "getPlaylists", "initialize", "restoreTrack", "selectLineIn", "selectTV", "setTrack", "repeatOne", "repeatAll", "repeatNone", "shuffleOn", "shuffleOff", "enableCrossfade", "disableCrossfade"}:
        return {"observe_only": True}
    return {"observe_only": True}


def build_plans(info: DeviceInfo, profile: Dict[str, Any], selected: Optional[List[str]]) -> List[CommandPlan]:
    command_map = info.command_map()
    names = selected or list(command_map.keys())
    plans: List[CommandPlan] = []
    for name in names:
        command = command_map.get(name)
        if command is None:
            plans.append(CommandPlan(name, [], configuration_error="Command is not advertised by this device JSON"))
            continue
        args, configuration_error = command_arguments(
            command,
            profile,
            aliases={
                "loadFavorite": "favoriteId",
                "loadFavoriteFull": "favoriteId",
                "loadPlaylist": "playlistId",
                "loadPlaylistFull": "playlistId",
                "playHighPriorityTTS": "ttsText",
                "speak": "ttsText",
                "playHighPriorityTrack": "trackUri",
                "playTrack": "trackUri",
                "playTrackAndRestore": "trackUri",
                "playTrackAndResume": "trackUri",
                "playText": "ttsText",
                "playTextAndRestore": "ttsText",
                "playTextAndResume": "ttsText",
                "resumeTrack": "trackUri",
                "restoreTrack": "trackUri",
                "setTrack": "trackUri",
                "selectLineIn": "lineInSource",
            },
        )
        expectation = expected_for(name, info, profile)
        plans.append(CommandPlan(
            name=name,
            args=args,
            category="topology" if name in TOPOLOGY_COMMANDS else "normal",
            destructive=name in DESTRUCTIVE_COMMANDS,
            configuration_error=configuration_error,
            **expectation,
        ))
    return plans


def main() -> int:
    parser = argparse.ArgumentParser(description="Exercise every advertised Sonos Advanced Player command")
    add_common_arguments(parser)
    arguments = parser.parse_args()
    configure_logging(arguments.verbose)

    document = load_json(arguments.device_json)
    info = DeviceInfo.from_page_json(document)
    profile = load_json(arguments.profile) if arguments.profile else {}
    bus = MessageBus()
    client = HubitatClient(arguments.hub, timeout=arguments.response_timeout)
    event_listener = HubitatSocketListener(f"ws://{arguments.hub}/eventsocket", "events", bus)
    log_listener = HubitatSocketListener(f"ws://{arguments.hub}/logsocket", "logs", bus)
    runner = CommandRunner(
        client, info, bus,
        response_timeout=arguments.response_timeout,
        topology_timeout=arguments.topology_timeout,
        normal_cooldown=arguments.command_delay,
        topology_cooldown=arguments.topology_delay,
        dry_run=arguments.dry_run,
        confirm_destructive=arguments.confirm_destructive,
    )

    try:
        if not arguments.dry_run:
            event_listener.start()
            log_listener.start()
            time.sleep(1.0)
        plans = build_plans(info, profile, arguments.command)
        for plan in plans:
            result = runner.run(plan)
            if result.status in {"FAIL", "TIMEOUT"} and not arguments.continue_on_error:
                break
    finally:
        event_listener.stop()
        log_listener.stop()
        event_listener.join()
        log_listener.join()

    report = {
        "target": info.snapshot(),
        "socketErrors": {
            "events": event_listener.connection_errors + event_listener.parse_errors,
            "logs": log_listener.connection_errors + log_listener.parse_errors,
        },
        "results": [result.as_dict() for result in runner.results],
    }
    if arguments.output:
        write_json(arguments.output, report)
    print_summary(runner.results)
    return 1 if any(result.status in {"FAIL", "TIMEOUT"} for result in runner.results) else 0


if __name__ == "__main__":
    sys.exit(main())
