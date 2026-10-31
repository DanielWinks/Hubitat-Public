#!/usr/bin/env python3
"""
MIT License
Copyright 2026 Daniel Winks

Local tests for the client-side Sonos Advanced Controller test harness.
"""

from __future__ import annotations

import unittest
from unittest.mock import patch

from hubitat_sac_test_common import (
    CommandPlan,
    CommandRunner,
    DeviceInfo,
    MessageBus,
    ReceivedMessage,
    command_arguments,
    parse_socket_frame,
)


PLAYER_DOCUMENT = {
    "parentApp": {"id": 283},
    "commands": [
        {"name": "stop", "parameters": None},
        {"name": "setVolume", "parameters": [{"type": "NUMBER"}]},
        {"name": "loadFavoriteFull", "parameters": [
            {"type": "STRING"},
            {"type": "ENUM", "constraints": ["repeat all", "repeat one", "off"]},
            {"type": "ENUM", "constraints": ["replace", "append", "insert", "insert_next"]},
            {"type": "ENUM", "constraints": ["off", "on"]},
            {"type": "ENUM", "constraints": ["true", "false"]},
            {"type": "ENUM", "constraints": ["on", "off"]},
        ]},
    ],
    "device": {
        "id": 530,
        "name": "Sonos Advanced Player",
        "label": "Sonos Advanced - Arc",
        "data": {"groupId": "RINCON_ARC:1"},
        "currentStates": {
            "status": {"value": "stopped"},
            "volume": {"value": "33"},
        },
    },
}


class FakeClient:
    def __init__(self, bus: MessageBus, info: DeviceInfo) -> None:
        self.bus = bus
        self.info = info
        self.calls = []

    def send_command(self, device_id, method, args):
        self.calls.append((device_id, method, args))
        if method == "setVolume":
            self.bus.publish("events", {
                "source": "DEVICE", "deviceId": device_id,
                "name": "volume", "value": args[0]["value"],
            }, "event")
        else:
            self.bus.publish("logs", {
                "id": self.info.device_id,
                "name": self.info.label,
                "msg": f"{method} command accepted",
                "level": "debug",
            }, "log")
        return {"status": 200, "body": "", "request": {"id": device_id, "method": method, "args": args}}


class FailingTopologyClient(FakeClient):
    def send_command(self, device_id, method, args):
        self.calls.append((device_id, method, args))
        self.bus.publish("logs", {
            "id": self.info.app_id,
            "name": "Sonos Advanced Controller",
            "msg": f"Group operation {method} failed: TOPOLOGY_NOT_STABLE",
            "level": "warn",
        }, "log")
        return {"status": 200, "body": "", "request": {"id": device_id, "method": method, "args": args}}


class HarnessTests(unittest.TestCase):
    def setUp(self):
        self.info = DeviceInfo.from_page_json(PLAYER_DOCUMENT)

    def test_message_bus_assigns_one_sequence_per_message(self):
        bus = MessageBus()
        marker = bus.marker()
        bus.publish("events", {"name": "status"}, '{"name":"status"}')
        messages = bus.after(marker)
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0].sequence, marker + 1)

    def test_socket_frame_parser_accepts_objects_and_arrays(self):
        self.assertEqual(parse_socket_frame('{"name":"status"}'), [{"name": "status"}])
        self.assertEqual(parse_socket_frame('[{"name":"status"},{"name":"volume"}]'), [
            {"name": "status"}, {"name": "volume"}
        ])

    def test_command_arguments_follow_device_metadata(self):
        command = self.info.command_map()["loadFavoriteFull"]
        args, error = command_arguments(command, {
            "commands": {"loadFavoriteFull": ["14", "repeat all", "replace", "off", "true", "off"]}
        })
        self.assertIsNone(error)
        self.assertEqual(len(args), 6)
        self.assertEqual(args[0], {"type": "STRING", "value": "14"})

    def test_invalid_enum_profile_value_is_rejected(self):
        command = self.info.command_map()["loadFavoriteFull"]
        args, error = command_arguments(command, {
            "commands": {"loadFavoriteFull": ["14", "not-a-repeat-mode", "replace", "off", "true", "off"]}
        })
        self.assertEqual(args, [])
        self.assertIn("valid ENUM value", error)

    def test_missing_required_profile_value_is_not_guessed(self):
        command = self.info.command_map()["setVolume"]
        args, error = command_arguments(command, {})
        self.assertEqual(args, [])
        self.assertIn("No profile value", error)

    def test_runner_waits_for_response_then_normal_cooldown(self):
        bus = MessageBus()
        client = FakeClient(bus, self.info)
        runner = CommandRunner(client, self.info, bus, normal_cooldown=5, topology_cooldown=10)
        plan = CommandPlan(
            "setVolume", [{"type": "NUMBER", "value": 20}],
            expected_state="volume", expected_value=20, expected_event_names=["volume"],
        )
        with patch("hubitat_sac_test_common.time.sleep") as sleep:
            result = runner.run(plan)
        self.assertEqual(result.status, "PASS")
        sleep.assert_called_once_with(5)

    def test_runner_uses_ten_second_topology_cooldown(self):
        bus = MessageBus()
        client = FakeClient(bus, self.info)
        runner = CommandRunner(client, self.info, bus, normal_cooldown=5, topology_cooldown=10)
        plan = CommandPlan("joinPlayersToCoordinator", [], category="topology", observe_only=True)
        with patch("hubitat_sac_test_common.time.sleep") as sleep:
            result = runner.run(plan)
        self.assertEqual(result.status, "PASS")
        sleep.assert_called_once_with(10)

    def test_failed_topology_log_is_not_reported_as_pass(self):
        bus = MessageBus()
        client = FailingTopologyClient(bus, self.info)
        runner = CommandRunner(client, self.info, bus, normal_cooldown=5, topology_cooldown=10)
        plan = CommandPlan("joinPlayersToCoordinator", [], category="topology", observe_only=True)
        with patch("hubitat_sac_test_common.time.sleep") as sleep:
            result = runner.run(plan)
        self.assertEqual(result.status, "EXPECTED_ERROR")
        self.assertIn("TOPOLOGY_NOT_STABLE", result.reason)
        sleep.assert_called_once_with(10)

    def test_target_error_log_is_classified_even_without_command_name(self):
        bus = MessageBus()
        client = FakeClient(bus, self.info)

        def send_error(device_id, method, args):
            client.calls.append((device_id, method, args))
            bus.publish("logs", {
                "id": self.info.device_id,
                "name": self.info.label,
                "msg": "Request returned HTTP status 500",
                "level": "error",
            }, "log")
            return {"status": 200, "body": "", "request": {"id": device_id, "method": method, "args": args}}

        client.send_command = send_error
        runner = CommandRunner(client, self.info, bus, normal_cooldown=5)
        plan = CommandPlan("setTrack", [], observe_only=True)
        with patch("hubitat_sac_test_common.time.sleep") as sleep:
            result = runner.run(plan)
        self.assertEqual(result.status, "EXPECTED_ERROR")
        self.assertIn("HTTP status 500", result.reason)
        sleep.assert_called_once_with(5)

    def test_destructive_commands_are_skipped_without_confirmation(self):
        bus = MessageBus()
        client = FakeClient(bus, self.info)
        runner = CommandRunner(client, self.info, bus, normal_cooldown=5)
        plan = CommandPlan("stop", [], destructive=True, observe_only=True)
        result = runner.run(plan)
        self.assertEqual(result.status, "SKIPPED")
        self.assertEqual(client.calls, [])

    def test_dry_run_reports_destructive_command_without_sending(self):
        bus = MessageBus()
        client = FakeClient(bus, self.info)
        runner = CommandRunner(client, self.info, bus, dry_run=True)
        plan = CommandPlan("stop", [], destructive=True, observe_only=True)
        result = runner.run(plan)
        self.assertEqual(result.status, "DRY_RUN")
        self.assertEqual(client.calls, [])
        self.assertTrue(result.warnings)


if __name__ == "__main__":
    unittest.main()
