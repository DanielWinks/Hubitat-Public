#!/usr/bin/env python3
"""
Sonos Advanced (SAC) Publisher for the C7 hub.

Publishes the Sonos Advanced app and all of its component drivers to the
C7 hub (192.168.1.3) with no arguments and no prompting. Each file is
matched on the hub by its definition name + namespace and updated in
place; if no match exists, it is created.

Usage:
    python3 scripts/hubitat_publish.py

Per-file results are printed as each file completes, followed by a
summary. Every result is also appended to hubitat_publish.log next to
this script (git-ignored), one JSON line per file per run.

On startup the log is read back to decide what to push:

- If the last recorded result for every SAC file is a success (or
  there is no usable log yet), all 12 files are pushed.
- If any file's last result is a failure (or is missing from the log),
  only those files are attempted, so a failed run can be retried
  quickly. Once that retry makes every file successful, the following
  run goes back to a full push.

Exit code is 0 only when every file attempted in this run published
successfully. Failures distinguish timeouts, connection errors, and
hub-rejected saves (compile errors), with the fullest error detail
available from the hub.
"""

import json
import re
import socket
from datetime import datetime, timezone
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# ---------------------------------------------------------------------------
# Hard-coded SAC / C7 targets
# ---------------------------------------------------------------------------
HUB_HOST = "192.168.1.3"

# SAC = the controller app plus all component drivers. The app is
# published last. Paths are relative to the repository root.
SAC_FILES = [
    ("Drivers/Component/SonosAdvAlarmManager.groovy", "driver"),
    ("Drivers/Component/SonosAdvAlarmSwitch.groovy", "driver"),
    ("Drivers/Component/SonosAdvBatteryStats.groovy", "driver"),
    ("Drivers/Component/SonosAdvFavorites.groovy", "driver"),
    ("Drivers/Component/SonosAdvGroup.groovy", "driver"),
    ("Drivers/Component/SonosAdvPlayer.groovy", "driver"),
    ("Drivers/Component/SonosAdvPlaylist.groovy", "driver"),
    ("Drivers/Component/SonosAdvQueue.groovy", "driver"),
    ("Drivers/Component/SonosAdvSecondaries.groovy", "driver"),
    ("Drivers/Component/SonosAdvSleepTimer.groovy", "driver"),
    ("Drivers/Component/SonosAdvSnapshot.groovy", "driver"),
    ("Apps/SonosAdvancedApp.groovy", "app"),
]

# Compile of the large app/driver files takes a while on the hub.
SAVE_TIMEOUT_SECONDS = 180
LIST_TIMEOUT_SECONDS = 15

# Append-only result log (JSON lines), next to this script, git-ignored.
# The next run reads it back to decide full push vs retry-only-failures.
LOG_FILENAME = "hubitat_publish.log"

# Hubitat API endpoints for listing user code
HUB_LIST_ENDPOINTS = {
    "app": "hub2/userAppTypes",
    "driver": "hub2/userDeviceTypes",
}

# Groovy allows both single and double quotes for strings
NAME_RE = re.compile(r"""name\s*:\s*["']([^"']+)["']""")
NAMESPACE_RE = re.compile(r"""namespace\s*:\s*["']([^"']+)["']""")

# Start of the definition block. Extraction is anchored here so that
# earlier occurrences of name:/namespace: (license headers, page(name:...),
# child-app references like app(name:..., appName:..., namespace:...)) can
# never be mistaken for the code's own identity.
DEFINITION_START_RE = re.compile(r"^\s*definition\s*\(", re.MULTILINE)


# ---------------------------------------------------------------------------
# Failure types (classified for the per-file report)
# ---------------------------------------------------------------------------
class HubTimeoutError(Exception):
    """The hub did not answer within the request timeout."""


class HubConnectionError(Exception):
    """The hub could not be reached at all."""


class HubSaveError(Exception):
    """The hub rejected the save (e.g. the source failed to compile)."""


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------
def _create_req(url, data=None, method="GET", headers=None):
    """Create a urllib Request with common headers and optional extras."""
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("Accept", "application/json")
    if headers:
        for key, value in headers.items():
            req.add_header(key, value)
    return req


def _open(req, timeout=15, allow_redirects=True):
    """
    Open a request.
    Returns (status, body_str, final_url, headers_dict).

    Raises HubTimeoutError on timeout and HubConnectionError when the
    hub cannot be reached, so the report can tell the two apart.
    """
    if allow_redirects:
        opener = urllib.request.build_opener()
    else:
        # Build opener without redirect handling to capture 302 responses
        opener = urllib.request.OpenerDirector()
        opener.add_handler(urllib.request.HTTPHandler())
        opener.add_handler(urllib.request.HTTPSHandler())
        opener.add_handler(urllib.request.HTTPDefaultErrorHandler())
        opener.add_handler(urllib.request.HTTPErrorProcessor())
        # No HTTPRedirectHandler — redirects will raise HTTPError

    try:
        with opener.open(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, body, resp.url, dict(resp.headers)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        return e.code, body, e.url, dict(e.headers)
    except urllib.error.URLError as e:
        reason = getattr(e, "reason", None)
        if isinstance(reason, (socket.timeout, TimeoutError)):
            raise HubTimeoutError(
                f"timed out after {timeout}s waiting for the hub"
            ) from e
        raise HubConnectionError(
            f"cannot connect to hub at {HUB_HOST} ({reason})"
        ) from e
    except (socket.timeout, TimeoutError) as e:
        raise HubTimeoutError(
            f"timed out after {timeout}s waiting for the hub"
        ) from e


# ---------------------------------------------------------------------------
# Code identification
# ---------------------------------------------------------------------------
def extract_name_and_namespace(source):
    """Extract the definition name and namespace from Groovy source code.

    Search is anchored at the definition( block so that name:/namespace:
    occurrences elsewhere in the file (pages, child-app references, comments)
    are ignored. Returns (name, namespace) — either may be None if not found."""
    block = DEFINITION_START_RE.search(source)
    scope = source[block.end() :] if block else source
    name_match = NAME_RE.search(scope)
    ns_match = NAMESPACE_RE.search(scope)
    return (
        name_match.group(1) if name_match else None,
        ns_match.group(1) if ns_match else None,
    )


def fetch_hub_code_list(host, code_type):
    """Fetch the full list of user code of a given type from the hub.
    Returns a list of dicts (the parsed JSON array), or an empty list on failure."""
    endpoint = HUB_LIST_ENDPOINTS.get(code_type)
    if not endpoint:
        return []
    url = f"http://{host}/{endpoint}"
    try:
        status, body, _, _ = _open(_create_req(url), timeout=LIST_TIMEOUT_SECONDS)
        if status == 200:
            return json.loads(body)
    except (HubTimeoutError, HubConnectionError, json.JSONDecodeError):
        pass
    return []


def find_matching_code(hub_list, name, namespace):
    """Search the hub code list for an entry matching name and namespace.
    Returns (entry, match_kind) where match_kind is 'exact' (name + namespace)
    or 'name-only' (namespace differs), or (None, None) if no match."""
    if not hub_list or not name:
        return None, None

    # Exact match on name + namespace
    for entry in hub_list:
        if entry.get("name") == name and entry.get("namespace") == namespace:
            return entry, "exact"

    # Fallback: name-only match (namespace differs or is missing)
    for entry in hub_list:
        if entry.get("name") == name:
            return entry, "name-only"

    return None, None


# ---------------------------------------------------------------------------
# Hubitat save calls
# ---------------------------------------------------------------------------
def _extract_compile_detail(body):
    """Pull every scrap of error detail out of a failed save response.

    Returns a multi-line string with the Hubitat #errors block, any
    editor-shell markers, and a raw-body excerpt. The editor-shell
    note covers newer firmware that re-renders the SPA with HTTP 200
    and no server-side error text — that almost always means a
    compile failure (bad import, sandbox, or syntax)."""
    parts = []
    if body:
        errors_match = re.search(
            r'id=["\']errors["\'][^>]*>(.*?)</div>',
            body,
            re.IGNORECASE | re.DOTALL,
        )
        if errors_match:
            detail = re.sub(r"<[^>]+>", " ", errors_match.group(1)).strip()
            detail = re.sub(r"\s+", " ", detail)
            if detail:
                parts.append(f"Hubitat #errors: {detail}")
        low = body[:5000].lower()
        if "password" in low and "login" in low:
            parts.append(
                "Hub returned a login page (hub security may be enabled)"
            )
        elif "/ui2/" in body or "<title>" in low:
            parts.append(
                "Hub returned its editor shell page with no error detail. "
                "This usually means the source failed to COMPILE on the hub "
                "(bad import, sandbox-disallowed import, or syntax error). "
                "Open the file in the hub editor to see the exact compiler message."
            )
        parts.append(f"Raw response ({len(body)} bytes, first 1500):")
        parts.append(body[:1500])
    return "\n".join(parts) if parts else "empty response from hub"


def get_hub_version(host, code_type, code_id):
    """Fetch the current version of a code file from the hub. Returns int or None."""
    url = f"http://{host}/{code_type}/ajax/code?id={code_id}"
    status, body, _, _ = _open(_create_req(url), timeout=LIST_TIMEOUT_SECONDS)

    if status == 200:
        try:
            data = json.loads(body)
            if data.get("id") == code_id:
                return data.get("version")
        except json.JSONDecodeError:
            pass
    return None


def create_new_code(host, code_type, source):
    """
    Create a brand-new code file on the hub via the /save endpoint.
    Returns the new numeric ID assigned by Hubitat.

    On success Hubitat responds 302 with Location: /<type>/editor/<id>, where the
    new ID is the final path segment. On failure it returns 200 with the editor
    HTML containing a <div id="errors"> block. The 302 is the success signal —
    do NOT follow the redirect, as its Location header is the only reliable id.
    """
    url = f"http://{host}/{code_type}/save"
    form_data = urllib.parse.urlencode(
        {"id": "", "version": "", "create": "", "source": source}
    ).encode("utf-8")

    req = _create_req(url, data=form_data, method="POST")
    status, body, _, headers = _open(
        req, timeout=SAVE_TIMEOUT_SECONDS, allow_redirects=False
    )

    if status == 302:
        location = headers.get("Location") or headers.get("location") or ""
        new_id = location.rstrip("/").rsplit("/", 1)[-1].split("?")[0].split("#")[0]
        if new_id.isdigit():
            return int(new_id)
        raise HubSaveError(
            f"Hub created the {code_type} but returned an unexpected "
            f"redirect Location: '{location}'"
        )

    # Anything other than a 302 means the save was rejected.
    raise HubSaveError(
        f"Failed to create new {code_type}. HTTP {status}:\n"
        f"{_extract_compile_detail(body)}"
    )


def update_existing_code(host, code_type, code_id, version, source):
    """
    Update an existing code file on the hub.
    Returns the new version int assigned by Hubitat.
    """
    url = f"http://{host}/{code_type}/ajax/update"
    form_data = urllib.parse.urlencode(
        {
            "id": str(code_id),
            "version": str(version),
            "source": source,
        }
    ).encode("utf-8")

    req = _create_req(url, data=form_data, method="POST")
    status, body, _, _ = _open(req, timeout=SAVE_TIMEOUT_SECONDS)

    if status == 200:
        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            raise HubSaveError(
                f"Hub returned a non-JSON response on update "
                f"(HTTP 200, {len(body)} bytes, first 1500):\n{body[:1500]}"
            )
        if data.get("status") == "success":
            return data.get("version", version + 1)
        error_msg = data.get("errorMessage") or "(no errorMessage in response)"
        # Clean up Java SQL prefix that Hubitat sometimes includes
        error_msg = error_msg.replace("java.sql.SQLException:", "").strip()
        raise HubSaveError(
            f"Hub rejected update (status={data.get('status')!r}):\n"
            f"errorMessage: {error_msg}\n"
            f"Raw response ({len(body)} bytes):\n{body[:1500]}"
        )

    raise HubSaveError(
        f"Failed to update {code_type}. HTTP {status}:\n"
        f"{_extract_compile_detail(body)}"
    )


# ---------------------------------------------------------------------------
# Per-file publish (unconditional, non-interactive)
# ---------------------------------------------------------------------------
def publish_one(host, code_type, filepath, hub_lists):
    """Publish one SAC file. Returns a result dict:
    {"ok": bool, "kind": str, "message": str} where kind is one of
    "success", "timeout", "connection", "save", "read", "unexpected",
    and message is the human-readable report line (with full error
    detail on failures)."""
    try:
        source = Path(filepath).read_text(encoding="utf-8")
    except (IOError, OSError) as e:
        return {
            "ok": False,
            "kind": "read",
            "message": f"FAILED: {filepath} — cannot read file: {e}",
        }

    code_name, code_namespace = extract_name_and_namespace(source)
    label = f"{Path(filepath).name} ({code_type}: {code_name or '?'})"

    hub_list = hub_lists.get(code_type) or []
    matched, match_kind = find_matching_code(hub_list, code_name, code_namespace)

    try:
        if matched:
            code_id = matched["id"]
            hub_version = get_hub_version(host, code_type, code_id)
            if hub_version is None:
                hub_version = 0
            new_version = update_existing_code(
                host, code_type, code_id, hub_version, source
            )
            note = "" if match_kind == "exact" else " (name-only match)"
            return {
                "ok": True,
                "kind": "success",
                "message": f"OK: {label} → updated ID {code_id}, version {new_version}{note}",
            }

        code_id = create_new_code(host, code_type, source)
        return {
            "ok": True,
            "kind": "success",
            "message": f"OK: {label} → created ID {code_id}",
        }

    except HubTimeoutError as e:
        return {
            "ok": False,
            "kind": "timeout",
            "message": f"FAILED: {label} — TIMED OUT: {e}",
        }
    except HubConnectionError as e:
        return {
            "ok": False,
            "kind": "connection",
            "message": f"FAILED: {label} — CONNECTION ERROR: {e}",
        }
    except HubSaveError as e:
        return {
            "ok": False,
            "kind": "save",
            "message": f"FAILED: {label} — COMPILE/SAVE ERROR:\n{e}",
        }
    except Exception as e:  # keep publishing the rest no matter what
        return {
            "ok": False,
            "kind": "unexpected",
            "message": f"FAILED: {label} — UNEXPECTED ERROR: {type(e).__name__}: {e}",
        }


# ---------------------------------------------------------------------------
# Result log (JSON lines, append-only)
# ---------------------------------------------------------------------------
def log_path():
    """Path to the result log: next to this script."""
    return Path(__file__).resolve().parent / LOG_FILENAME


def read_last_statuses(path):
    """Return {relpath: status} from the most recent log entry per file.

    "status" is "success" or "failed". Non-result lines (run/summary
    markers, blank lines, corrupt JSON) are skipped. Returns an empty
    dict when the log is missing or unreadable — the caller then does
    a full push."""
    statuses = {}
    try:
        lines = Path(path).read_text(encoding="utf-8").splitlines()
    except (IOError, OSError):
        return {}

    for line in lines:
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue
        relpath = entry.get("file")
        status = entry.get("status")
        if relpath and status in ("success", "failed"):
            statuses[relpath] = status  # later lines win
    return statuses


def append_log(path, entry):
    """Append one JSON line to the log. Logging must never break a publish."""
    entry = {"ts": datetime.now(timezone.utc).isoformat(), **entry}
    try:
        with open(path, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry) + "\n")
    except (IOError, OSError) as e:
        print(f"    WARNING: could not write to log file {path}: {e}")


def select_targets(last_statuses):
    """Decide what to publish given the last recorded statuses.

    Returns (targets, mode_line) where targets is a list of
    (relpath, code_type) to attempt this run and mode_line describes
    the decision for the terminal:

    - No failures/missing in the log (or no log): full push.
    - Otherwise: retry only the files whose last result was a failure
      or that never appear in the log.
    """
    retry = [(r, t) for (r, t) in SAC_FILES if last_statuses.get(r) != "success"]

    if not last_statuses:
        return list(SAC_FILES), "Full publish (no prior log)"
    if not retry:
        return list(SAC_FILES), f"Full publish (last log shows {len(SAC_FILES)}/{len(SAC_FILES)} success)"
    return retry, f"Retry {len(retry)} file(s) from last run (skipping {len(SAC_FILES) - len(retry)} already successful)"


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
def repo_root():
    """Repository root: the parent of this script's directory."""
    return Path(__file__).resolve().parent.parent


def main():
    root = repo_root()
    log = log_path()

    last_statuses = read_last_statuses(log)
    targets, mode_line = select_targets(last_statuses)
    print(f"C7 ({HUB_HOST}) — {mode_line}.")
    print(f"Log: {log}")
    print()

    # One code list per type, fetched once up front.
    hub_lists = {}
    for code_type in ("app", "driver"):
        hub_lists[code_type] = fetch_hub_code_list(HUB_HOST, code_type)
        n = len(hub_lists[code_type])
        print(f"Hub list for {code_type}s: {n} code file(s)" + ("" if n else " — NOT FETCHED (will try create; may duplicate)"))
    print()

    append_log(
        log,
        {"event": "run-start", "mode": mode_line, "files": [r for r, _ in targets]},
    )

    successes, failures = [], []
    run_statuses = dict(last_statuses)
    total = len(targets)
    for i, (relpath, code_type) in enumerate(targets, 1):
        filepath = root / relpath
        print(f"[{i}/{total}] Publishing {relpath} ...", flush=True)
        result = publish_one(HUB_HOST, code_type, filepath, hub_lists)
        # Print the per-file verdict; on failure the message already
        # carries the full compile-error detail.
        for line in result["message"].split("\n"):
            print(f"    {line}" if line else "")
        append_log(
            log,
            {
                "file": relpath,
                "codeType": code_type,
                "status": "success" if result["ok"] else "failed",
                "kind": result["kind"],
                "message": result["message"],
            },
        )
        run_statuses[relpath] = "success" if result["ok"] else "failed"
        (successes if result["ok"] else failures).append(relpath)
        print(flush=True)

    ok_total = sum(1 for r, _ in SAC_FILES if run_statuses.get(r) == "success")
    append_log(
        log,
        {
            "event": "summary",
            "attempted": len(successes) + len(failures),
            "succeeded": len(successes),
            "failed": len(failures),
            "totalSuccess": f"{ok_total}/{len(SAC_FILES)}",
        },
    )

    print(f"Summary: {len(successes)}/{total} published this run, {len(failures)} failed")
    for relpath in failures:
        print(f"  FAILED: {relpath}")
    print(f"Overall log state: {ok_total}/{len(SAC_FILES)} successful"
          + (" — next run will be a full publish" if ok_total == len(SAC_FILES)
             else " — next run will retry only the failed files"))
    sys.exit(0 if not failures else 1)


if __name__ == "__main__":
    main()
