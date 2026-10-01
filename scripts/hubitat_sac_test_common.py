#!/usr/bin/env python3
"""
MIT License
Copyright 2026 Daniel Winks

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

Shared client-side test harness for Hubitat Sonos Advanced Controller devices.
"""

from __future__ import annotations

import argparse
import json
import logging
import threading
import time
import urllib.error
import urllib.request
from collections import deque
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Callable, Deque, Dict, Iterable, List, Optional, Sequence, Tuple


NORMAL_COOLDOWN_SECONDS = 5.0
TOPOLOGY_COOLDOWN_SECONDS = 10.0
DEFAULT_RESPONSE_TIMEOUT_SECONDS = 15.0
DEFAULT_TOPOLOGY_TIMEOUT_SECONDS = 60.0

try:
    import websocket  # type: ignore
except ImportError:  # pragma: no cover - exercised by the CLI, not unit tests
    websocket = None


LOGGER = logging.getLogger("hubitat_sac_test")


def json_value(value: Any) -> Any:
    """Return the most useful value from a Hubitat current-state record."""
    if isinstance(value, dict):
        if "value" in value:
            return value["value"]
        if "stringValue" in value:
            return value["stringValue"]
    return value


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return value


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, sort_keys=True, default=str)
        handle.write("\n")


def as_text(value: Any) -> str:
    return "" if value is None else str(value)


def normalized(value: Any) -> str:
    return as_text(value).strip().lower()


def numeric(value: Any) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


@dataclass(frozen=True)
class DeviceInfo:
    device_id: int
    label: str
    name: str
    app_id: Optional[int]
    data: Dict[str, Any]
    states: Dict[str, Any]
    commands: List[Dict[str, Any]]

    @classmethod
    def from_page_json(cls, document: Dict[str, Any]) -> "DeviceInfo":
        device = document.get("device") if isinstance(document.get("device"), dict) else {}
        raw_device_id = device.get("id", document.get("deviceId"))
        if raw_device_id is None:
            raise ValueError("Device JSON does not contain device.id")

        raw_states = device.get("currentStates", {})
        states = {
            str(name): json_value(value)
            for name, value in raw_states.items()
        } if isinstance(raw_states, dict) else {}

        parent = document.get("parentApp") if isinstance(document.get("parentApp"), dict) else {}
        app_id = parent.get("id")
        if app_id is not None:
            app_id = int(app_id)

        return cls(
            device_id=int(raw_device_id),
            label=as_text(device.get("label") or document.get("extraBreadcrumb") or device.get("name")),
            name=as_text(device.get("name") or document.get("extraBreadcrumb")),
            app_id=app_id,
            data=dict(device.get("data") or {}),
            states=states,
            commands=list(document.get("commands") or []),
        )

    def state(self, name: str) -> Any:
        return self.states.get(name)

    def command_map(self) -> Dict[str, Dict[str, Any]]:
        return {
            as_text(command.get("name")): command
            for command in self.commands
            if command.get("name")
        }

    def snapshot(self) -> Dict[str, Any]:
        return {
            "deviceId": self.device_id,
            "label": self.label,
            "states": dict(self.states),
            "data": dict(self.data),
        }


@dataclass(frozen=True)
class ReceivedMessage:
    sequence: int
    channel: str
    received_at: float
    payload: Any
    raw: str


class MessageBus:
    """Thread-safe message history shared by both socket listeners."""

    def __init__(self, max_messages: int = 10000) -> None:
        self._messages: Deque[ReceivedMessage] = deque(maxlen=max_messages)
        self._condition = threading.Condition()
        self._sequence = 0

    def publish(self, channel: str, payload: Any, raw: str) -> ReceivedMessage:
        with self._condition:
            self._sequence += 1
            message = ReceivedMessage(
                sequence=self._sequence,
                channel=channel,
                received_at=time.time(),
                payload=payload,
                raw=raw,
            )
            self._messages.append(message)
            self._condition.notify_all()
            return message

    def marker(self) -> int:
        with self._condition:
            return self._sequence

    def after(self, marker: int) -> List[ReceivedMessage]:
        with self._condition:
            return [message for message in self._messages if message.sequence > marker]

    def wait_for(
        self,
        predicate: Callable[[ReceivedMessage], bool],
        marker: int,
        timeout: float,
    ) -> Tuple[Optional[ReceivedMessage], List[ReceivedMessage]]:
        deadline = time.monotonic() + timeout
        inspected: List[ReceivedMessage] = []
        seen = marker
        with self._condition:
            while True:
                new_messages = [message for message in self._messages if message.sequence > seen]
                for message in new_messages:
                    seen = max(seen, message.sequence)
                    inspected.append(message)
                    if predicate(message):
                        return message, inspected
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return None, inspected
                self._condition.wait(timeout=min(remaining, 0.25))


def parse_socket_frame(raw: str) -> List[Any]:
    """Parse one Hubitat socket frame, flattening JSON arrays of messages."""
    value = json.loads(raw)
    if isinstance(value, list):
        return value
    return [value]


class HubitatSocketListener:
    """Reconnectable background listener for /eventsocket or /logsocket."""

    def __init__(self, url: str, channel: str, bus: MessageBus) -> None:
        self.url = url
        self.channel = channel
        self.bus = bus
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._socket_lock = threading.Lock()
        self._socket: Optional[Any] = None
        self.parse_errors: List[Dict[str, Any]] = []
        self.connection_errors: List[str] = []

    def start(self) -> None:
        if websocket is None:
            raise RuntimeError(
                "websocket-client is required; install it with "
                "python3 -m pip install -r scripts/requirements-sonos-test.txt"
            )
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run, name=f"hubitat-{self.channel}", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        with self._socket_lock:
            socket = self._socket
        if socket is not None:
            try:
                socket.close()
            except Exception:  # pragma: no cover - network-specific
                LOGGER.debug("Could not close %s socket cleanly", self.channel, exc_info=True)

    def join(self, timeout: float = 2.0) -> None:
        if self._thread:
            self._thread.join(timeout=timeout)

    def _run(self) -> None:
        retry_delay = 1.0
        while not self._stop.is_set():
            def on_open(_socket: Any) -> None:
                with self._socket_lock:
                    self._socket = _socket
                LOGGER.info("Connected to %s", self.url)

            def on_message(_socket: Any, message: str) -> None:
                try:
                    for payload in parse_socket_frame(message):
                        self.bus.publish(self.channel, payload, message)
                except (TypeError, ValueError, json.JSONDecodeError) as error:
                    detail = {"channel": self.channel, "error": str(error), "raw": message}
                    self.parse_errors.append(detail)
                    LOGGER.warning("Could not parse %s frame: %s", self.channel, error)

            def on_error(_socket: Any, error: Any) -> None:
                detail = f"{self.channel}: {error}"
                self.connection_errors.append(detail)
                LOGGER.warning("%s", detail)

            def on_close(_socket: Any, status: Any, message: Any) -> None:
                with self._socket_lock:
                    self._socket = None
                LOGGER.info("Disconnected from %s (%s: %s)", self.url, status, message)

            app = websocket.WebSocketApp(
                self.url,
                on_open=on_open,
                on_message=on_message,
                on_error=on_error,
                on_close=on_close,
            )
            try:
                app.run_forever(ping_interval=20, ping_timeout=10)
            except Exception as error:  # pragma: no cover - network-specific
                self.connection_errors.append(f"{self.channel}: {error}")
                LOGGER.warning("Socket loop failed for %s: %s", self.url, error)
            if self._stop.is_set():
                break
            self._stop.wait(retry_delay)
            retry_delay = min(retry_delay * 2.0, 30.0)


class HubitatClient:
    def __init__(self, host: str, timeout: float = 15.0) -> None:
        self.host = host.replace("http://", "").replace("https://", "").rstrip("/")
        self.timeout = timeout

    def send_command(self, device_id: int, method: str, args: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
        url = f"http://{self.host}/device/runmethod"
        body = json.dumps({"id": device_id, "method": method, "args": list(args)}).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=body,
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8", errors="replace")
                return {
                    "status": response.status,
                    "body": raw,
                    "url": url,
                    "request": json.loads(body.decode("utf-8")),
                }
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", errors="replace")
            return {
                "status": error.code,
                "body": raw,
                "url": url,
                "request": json.loads(body.decode("utf-8")),
            }
        except urllib.error.URLError as error:
            raise ConnectionError(f"Could not connect to Hubitat at {url}: {error.reason}") from error


@dataclass
class CommandPlan:
    name: str
    args: List[Dict[str, Any]]
    category: str = "normal"
    destructive: bool = False
    expected_state: Optional[str] = None
    expected_value: Optional[Any] = None
    expected_event_names: List[str] = field(default_factory=list)
    observe_only: bool = False
    configuration_error: Optional[str] = None

    @property
    def topology(self) -> bool:
        return self.category == "topology"


@dataclass
class CommandResult:
    command: str
    arguments: List[Dict[str, Any]]
    category: str
    status: str
    reason: str
    started_at: str
    finished_at: str
    duration_ms: int
    cooldown_seconds: float
    http: Optional[Dict[str, Any]]
    events: List[Dict[str, Any]]
    logs: List[Dict[str, Any]]
    warnings: List[str] = field(default_factory=list)
    errors: List[str] = field(default_factory=list)

    def as_dict(self) -> Dict[str, Any]:
        return asdict(self)


def iso_timestamp(epoch: Optional[float] = None) -> str:
    from datetime import datetime, timezone

    return datetime.fromtimestamp(epoch or time.time(), tz=timezone.utc).isoformat()


def message_payloads(messages: Iterable[ReceivedMessage], channel: str) -> List[Dict[str, Any]]:
    return [
        {
            "channel": message.channel,
            "receivedAt": iso_timestamp(message.received_at),
            "payload": message.payload,
            "raw": message.raw,
        }
        for message in messages
        if message.channel == channel and isinstance(message.payload, dict)
    ]


def event_matches_device(payload: Any, info: DeviceInfo) -> bool:
    if not isinstance(payload, dict):
        return False
    raw_id = payload.get("deviceId", payload.get("id"))
    if raw_id is not None:
        try:
            return int(raw_id) == info.device_id
        except (TypeError, ValueError):
            pass
    display_name = normalized(payload.get("displayName"))
    return bool(display_name and display_name in {normalized(info.label), normalized(info.name)})


def log_matches_target(payload: Any, info: DeviceInfo) -> bool:
    if not isinstance(payload, dict):
        return False
    raw_id = payload.get("id")
    if raw_id is not None:
        try:
            if int(raw_id) in {info.device_id, info.app_id}:
                return True
        except (TypeError, ValueError):
            pass
    name = normalized(payload.get("name"))
    return bool(name and name in {normalized(info.label), normalized(info.name)})


def extract_error(payload: Any) -> Optional[str]:
    if not isinstance(payload, dict):
        return None
    event_type = normalized(payload.get("type"))
    success = payload.get("success")
    if event_type in {"globalerror", "playbackerror", "error"} or success is False:
        details = payload.get("reason") or payload.get("errorCode") or payload.get("msg") or payload
        return as_text(details)
    return None


def command_log_relevance(command: str, message: str) -> bool:
    lowered = normalized(message)
    aliases = {
        "setvolume": ["setvolume", "volume"],
        "setlevel": ["setlevel", "volume"],
        "stop": ["stop", "pause"],
        "play": ["play"],
        "pause": ["pause"],
        "joinplayerstocoordinator": ["join", "addmissingplayers", "modifygroupmembers", "group operation"],
        "groupplayers": ["group", "creategroup", "group operation"],
        "ungroupplayers": ["ungroup", "group operation"],
    }
    needles = aliases.get(normalized(command), [normalized(command)])
    return any(needle in lowered for needle in needles)


def log_indicates_failure(payload: Any) -> bool:
    if not isinstance(payload, dict):
        return False
    level = normalized(payload.get("level"))
    message = normalized(payload.get("msg"))
    if level == "error":
        return True
    failure_markers = (
        "failed", "error", "exception", "unavailable", "unsupported",
        "no signature", "could not", "returned http status 4", "returned http status 5",
    )
    return any(marker in message for marker in failure_markers)


def state_changed(before: Any, after: Any, expected: Any = None) -> bool:
    if expected is not None:
        return normalized(after) == normalized(expected)
    return before != after


class CommandRunner:
    """Serial command executor with response parsing and post-response pacing."""

    def __init__(
        self,
        client: HubitatClient,
        info: DeviceInfo,
        bus: MessageBus,
        response_timeout: float = DEFAULT_RESPONSE_TIMEOUT_SECONDS,
        topology_timeout: float = DEFAULT_TOPOLOGY_TIMEOUT_SECONDS,
        normal_cooldown: float = NORMAL_COOLDOWN_SECONDS,
        topology_cooldown: float = TOPOLOGY_COOLDOWN_SECONDS,
        dry_run: bool = False,
        confirm_destructive: bool = False,
    ) -> None:
        self.client = client
        self.info = info
        self.bus = bus
        self.response_timeout = response_timeout
        self.topology_timeout = topology_timeout
        self.normal_cooldown = normal_cooldown
        self.topology_cooldown = topology_cooldown
        self.dry_run = dry_run
        self.confirm_destructive = confirm_destructive
        self.results: List[CommandResult] = []

    def run(self, plan: CommandPlan) -> CommandResult:
        started_epoch = time.time()
        started_monotonic = time.monotonic()
        started_at = iso_timestamp(started_epoch)
        cooldown = self.topology_cooldown if plan.topology else self.normal_cooldown
        http_response: Optional[Dict[str, Any]] = None
        inspected: List[ReceivedMessage] = []
        warnings: List[str] = []
        errors: List[str] = []

        if plan.configuration_error:
            result = self._result(
                plan, "NEEDS_CONFIGURATION", plan.configuration_error, started_at,
                started_monotonic, cooldown, http_response, inspected, warnings, errors,
            )
            self.results.append(result)
            return result

        if plan.destructive and not self.confirm_destructive and not self.dry_run:
            result = self._result(
                plan, "SKIPPED", "Requires --confirm-destructive", started_at,
                started_monotonic, cooldown, http_response, inspected, warnings, errors,
            )
            self.results.append(result)
            return result

        before = self.info.snapshot()
        marker = self.bus.marker()
        request_preview = json.dumps(
            {"id": self.info.device_id, "method": plan.name, "args": plan.args},
            sort_keys=True,
        )
        LOGGER.info("Sending %s%s args=%s", plan.name, " [DRY RUN]" if self.dry_run else "", request_preview)

        if self.dry_run:
            status = "DRY_RUN"
            reason = "Command request not sent"
            if plan.destructive and not self.confirm_destructive:
                warnings.append("Would require --confirm-destructive when not running in dry-run mode")
        else:
            try:
                http_response = self.client.send_command(self.info.device_id, plan.name, plan.args)
                if int(http_response.get("status", 0)) < 200 or int(http_response.get("status", 0)) >= 300:
                    status = "FAIL"
                    reason = f"Hubitat HTTP status {http_response.get('status')}"
                else:
                    status, reason, inspected = self._wait_for_response(plan, before, marker)
            except (ConnectionError, OSError) as error:
                status = "FAIL"
                reason = str(error)
                errors.append(str(error))

        if status not in {"SKIPPED", "NEEDS_CONFIGURATION", "DRY_RUN"}:
            events = message_payloads(inspected, "events")
            logs = message_payloads(inspected, "logs")
        else:
            events = []
            logs = []

        result = self._result(
            plan, status, reason, started_at, started_monotonic, cooldown,
            http_response, inspected, warnings, errors,
        )
        self.results.append(result)

        if not self.dry_run and status not in {"SKIPPED", "NEEDS_CONFIGURATION"}:
            LOGGER.info("%s: %s — cooling down for %.1f seconds", plan.name, reason, cooldown)
            time.sleep(max(0.0, cooldown))
        return result

    def _wait_for_response(
        self,
        plan: CommandPlan,
        before: Dict[str, Any],
        marker: int,
    ) -> Tuple[str, str, List[ReceivedMessage]]:
        timeout = self.topology_timeout if plan.topology else self.response_timeout
        deadline = time.monotonic() + timeout
        inspected: List[ReceivedMessage] = []
        seen = marker
        saw_activity = False
        last_error: Optional[str] = None

        while time.monotonic() < deadline:
            new_messages = self.bus.after(seen)
            for message in new_messages:
                seen = max(seen, message.sequence)
                inspected.append(message)
                if message.channel == "events" and event_matches_device(message.payload, self.info):
                    saw_activity = True
                    error = extract_error(message.payload)
                    if error:
                        last_error = error
                    if self._event_satisfies(plan, message.payload, before):
                        return "PASS", "Expected device event observed", inspected
                    if error and plan.observe_only:
                        return "EXPECTED_ERROR", error, inspected
                elif message.channel == "logs" and log_matches_target(message.payload, self.info):
                    message_text = as_text(message.payload.get("msg"))
                    if log_indicates_failure(message.payload):
                        last_error = message_text
                        if plan.observe_only:
                            return "EXPECTED_ERROR", message_text, inspected
                        continue
                    if command_log_relevance(plan.name, message_text):
                        saw_activity = True
                        if plan.observe_only:
                            return "PASS", "Relevant command log observed", inspected

            if plan.expected_state and self.info.state(plan.expected_state) != before["states"].get(plan.expected_state):
                return "PASS", f"{plan.expected_state} changed", inspected
            if plan.observe_only and saw_activity and not last_error:
                return "PASS", "Relevant asynchronous activity observed", inspected
            time.sleep(0.1)

        if last_error:
            return "EXPECTED_ERROR" if plan.observe_only else "FAIL", last_error, inspected
        return "TIMEOUT", "No matching device event or command log before timeout", inspected

    def _event_satisfies(self, plan: CommandPlan, payload: Any, before: Dict[str, Any]) -> bool:
        if not isinstance(payload, dict):
            return False
        event_name = as_text(payload.get("name"))
        if plan.expected_event_names and event_name in plan.expected_event_names:
            if plan.expected_value is None:
                return True
            return normalized(payload.get("value")) == normalized(plan.expected_value)
        if plan.expected_state and event_name == plan.expected_state:
            return state_changed(before["states"].get(plan.expected_state), payload.get("value"), plan.expected_value)
        return False

    def _result(
        self,
        plan: CommandPlan,
        status: str,
        reason: str,
        started_at: str,
        started_monotonic: float,
        cooldown: float,
        http_response: Optional[Dict[str, Any]],
        inspected: List[ReceivedMessage],
        warnings: List[str],
        errors: List[str],
    ) -> CommandResult:
        events = message_payloads(inspected, "events")
        logs = message_payloads(inspected, "logs")
        return CommandResult(
            command=plan.name,
            arguments=plan.args,
            category=plan.category,
            status=status,
            reason=reason,
            started_at=started_at,
            finished_at=iso_timestamp(),
            duration_ms=int((time.monotonic() - started_monotonic) * 1000),
            cooldown_seconds=cooldown,
            http=http_response,
            events=events,
            logs=logs,
            warnings=warnings,
            errors=errors,
        )


def add_common_arguments(parser: argparse.ArgumentParser, default_profile: Optional[str] = None) -> None:
    parser.add_argument("--hub", required=True, help="Hubitat IP address or hostname")
    parser.add_argument("--device-json", required=True, type=Path, help="JSON copied from the Hubitat device page")
    parser.add_argument("--profile", type=Path, help="JSON test profile containing command arguments")
    parser.add_argument("--command", action="append", help="Run only this command; repeat the option as needed")
    parser.add_argument("--response-timeout", type=float, default=DEFAULT_RESPONSE_TIMEOUT_SECONDS)
    parser.add_argument("--topology-timeout", type=float, default=DEFAULT_TOPOLOGY_TIMEOUT_SECONDS)
    parser.add_argument("--command-delay", type=float, default=NORMAL_COOLDOWN_SECONDS)
    parser.add_argument("--topology-delay", type=float, default=TOPOLOGY_COOLDOWN_SECONDS)
    parser.add_argument("--dry-run", action="store_true", help="Show the command plan without sending requests")
    parser.add_argument("--confirm-destructive", action="store_true", help="Allow playback and topology-changing commands")
    parser.add_argument("--continue-on-error", action="store_true")
    parser.add_argument("--output", type=Path, help="Write detailed JSON results to this path")
    parser.add_argument("--verbose", action="store_true")
    if default_profile:
        parser.set_defaults(default_profile=default_profile)


def configure_logging(verbose: bool) -> None:
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )


def profile_value(profile: Dict[str, Any], command: str, index: int, fallback_key: Optional[str] = None) -> Any:
    commands = profile.get("commands") if isinstance(profile.get("commands"), dict) else {}
    configured = commands.get(command)
    if isinstance(configured, list) and index < len(configured):
        return configured[index]
    if isinstance(configured, dict):
        values = configured.get("args")
        if isinstance(values, list) and index < len(values):
            return values[index]
    if fallback_key:
        return profile.get(fallback_key)
    return None


def argument(
    value: Any,
    type_name: str,
    constraints: Optional[Sequence[Any]] = None,
) -> Dict[str, Any]:
    normalized_type = normalized(type_name)
    if normalized_type == "number":
        number = numeric(value)
        if number is None:
            raise ValueError(f"{value!r} is not a NUMBER")
        if number.is_integer():
            value = int(number)
    if normalized_type == "enum" and constraints:
        allowed = {normalized(item) for item in constraints}
        if normalized(value) not in allowed:
            choices = ", ".join(as_text(item) for item in constraints)
            raise ValueError(f"{value!r} is not a valid ENUM value; choose one of: {choices}")
    return {"type": type_name or "STRING", "value": value}


def command_arguments(
    command: Dict[str, Any],
    profile: Dict[str, Any],
    aliases: Optional[Dict[str, str]] = None,
) -> Tuple[List[Dict[str, Any]], Optional[str]]:
    name = as_text(command.get("name"))
    parameters = command.get("parameters") if isinstance(command.get("parameters"), list) else []
    args: List[Dict[str, Any]] = []
    aliases = aliases or {}
    for index, parameter in enumerate(parameters):
        parameter_type = as_text(parameter.get("type") or "STRING") if isinstance(parameter, dict) else "STRING"
        constraints = parameter.get("constraints") if isinstance(parameter, dict) else None
        configured = profile_value(profile, name, index, aliases.get(name) if index == 0 else None)
        if configured is None:
            return [], f"No profile value for argument {index + 1} of {name}"
        try:
            args.append(argument(configured, parameter_type, constraints))
        except ValueError as error:
            return [], str(error)
    return args, None


def print_summary(results: Sequence[CommandResult]) -> None:
    counts: Dict[str, int] = {}
    for result in results:
        counts[result.status] = counts.get(result.status, 0) + 1
    print("\nCommand summary")
    for status in sorted(counts):
        print(f"  {status:20} {counts[status]}")
    for result in results:
        print(f"  {result.status:20} {result.command}: {result.reason}")
