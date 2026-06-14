#!/usr/bin/env python3
"""
Hubitat Code Publisher

Publishes Groovy source files (apps, drivers, libraries) to a Hubitat hub.
Reads hub connection info and file mappings from .hubitat/metadata.json.

Usage:
    python3 hubitat_publish.py <code_type> <file_path> [workspace_root]

Where code_type is one of: app, driver, library, auto
(Use "auto" to auto-detect from file content)

Workspace root defaults to the ZED_WORKTREE_ROOT env var, falling back to cwd.
"""

import json
import os
import re
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
METADATA_DIR = ".hubitat"
METADATA_FILE = ".hubitat/metadata.json"

# Patterns for auto-detecting code type from file content
# (mirrors the VS Code extension's detection logic)
DRIVER_PATTERN = re.compile(r"^\s*metadata\s*\{", re.MULTILINE)
APP_PATTERN = re.compile(r"^\s*definition\s*\(", re.MULTILINE)
LIBRARY_PATTERN = re.compile(r"^\s*library\s*\(", re.MULTILINE)

# Patterns for extracting name / namespace from Groovy definitions
# Groovy allows both single and double quotes for strings
NAME_RE = re.compile(r"""name\s*:\s*["']([^"']+)["']""")
NAMESPACE_RE = re.compile(r"""namespace\s*:\s*["']([^"']+)["']""")

# Hubitat API endpoints for listing user code
HUB_LIST_ENDPOINTS = {
    "app": "hub2/userAppTypes",
    "driver": "hub2/userDeviceTypes",
    "library": "hub2/userLibraries",
}

VALID_TYPES = {"app", "driver", "library"}

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """
    A redirect handler that refuses to follow any redirects.
    Used when we need to capture a 302 Location header (e.g. for new code creation).
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _create_req(url, data=None, method="GET"):
    """Create a urllib Request with common headers."""
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("Accept", "application/json")
    return req


def _open(req, allow_redirects=True, timeout=15):
    """
    Open a request, optionally following redirects.
    Returns (status, body_str, headers_dict).
    """
    if allow_redirects:
        opener = urllib.request.build_opener()
    else:
        opener = urllib.request.build_opener(NoRedirectHandler)

    try:
        with opener.open(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, body, dict(resp.headers)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        return e.code, body, dict(e.headers)
    except urllib.error.URLError as e:
        raise ConnectionError(f"Cannot connect to hub: {e.reason}") from e
    except socket.timeout:
        raise ConnectionError("Connection to hub timed out") from None


# ---------------------------------------------------------------------------
# Code type detection
# ---------------------------------------------------------------------------


def detect_code_type(filepath):
    """Auto-detect the Hubitat code type from file content. Returns None if unclear."""
    try:
        content = Path(filepath).read_text(encoding="utf-8")
    except (IOError, OSError) as e:
        print(f"Error reading file: {e}")
        return None

    if DRIVER_PATTERN.search(content):
        return "driver"
    if APP_PATTERN.search(content):
        return "app"
    if LIBRARY_PATTERN.search(content):
        return "library"
    return None


def extract_name_and_namespace(source):
    """Extract the definition name and namespace from Groovy source code.
    Returns (name, namespace) — either may be None if not found."""
    name_match = NAME_RE.search(source)
    ns_match = NAMESPACE_RE.search(source)
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
        status, body, _ = _open(_create_req(url), timeout=10)
        if status == 200:
            return json.loads(body)
    except (ConnectionError, json.JSONDecodeError):
        pass
    return []


def find_matching_code(hub_list, name, namespace):
    """Search the hub code list for an entry matching name and namespace.
    Prefers an exact match on both; falls back to name-only match.
    Returns the matching entry dict, or None."""
    if not hub_list or not name:
        return None

    # Exact match on name + namespace
    for entry in hub_list:
        if entry.get("name") == name and entry.get("namespace") == namespace:
            return entry

    # Fallback: name-only match
    for entry in hub_list:
        if entry.get("name") == name:
            return entry

    return None


# ---------------------------------------------------------------------------
# Settings & metadata I/O
# ---------------------------------------------------------------------------


def load_hub_hostname(workspace_root):
    """Load hub hostname from .hubitat/metadata.json."""
    metadata_path = Path(workspace_root) / METADATA_FILE
    if not metadata_path.exists():
        raise FileNotFoundError(
            f"Metadata file not found: {metadata_path}\n"
            f"Please create {METADATA_FILE} with 'hubitat.hub.hostname' set.\n"
            f'Example: {{"hubitat.hub.hostname": "192.168.1.4", "files": []}}'
        )

    with open(metadata_path, "r") as f:
        data = json.load(f)

    host = data.get("hubitat.hub.hostname")
    if not host:
        raise ValueError(
            f"'hubitat.hub.hostname' not set in {METADATA_FILE}.\n"
            f"Add your hub's IP or hostname to the top-level of the JSON.\n"
            f'Example: {{"hubitat.hub.hostname": "192.168.1.4", "files": [...]}}'
        )

    return host


def load_metadata(workspace_root):
    """Load the files list from .hubitat/metadata.json. Returns empty list if missing."""
    metadata_path = Path(workspace_root) / METADATA_FILE
    if not metadata_path.exists():
        return []
    with open(metadata_path, "r") as f:
        data = json.load(f)
    return data.get("files", [])


def save_metadata(workspace_root, files):
    """Save the files list to .hubitat/metadata.json, preserving any extra top-level keys."""
    metadata_path = Path(workspace_root) / METADATA_FILE

    # Preserve any existing top-level keys (e.g. hubitat.hub.hostname, networkTimeout)
    extra = {}
    if metadata_path.exists():
        try:
            with open(metadata_path, "r") as f:
                existing = json.load(f)
            for key, value in existing.items():
                if key != "files":
                    extra[key] = value
        except (json.JSONDecodeError, IOError):
            pass

    metadata_path.parent.mkdir(parents=True, exist_ok=True)
    data = {"files": files, **extra}
    with open(metadata_path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def find_file_in_metadata(files, filepath):
    """Return the first metadata entry matching the given filepath, or None."""
    for entry in files:
        if entry.get("filepath") == filepath:
            return entry
    return None


# ---------------------------------------------------------------------------
# Hubitat API calls
# ---------------------------------------------------------------------------


def get_hub_version(host, code_type, code_id):
    """Fetch the current version of a code file from the hub. Returns int or None."""
    url = f"http://{host}/{code_type}/ajax/code?id={code_id}"
    status, body, _ = _open(_create_req(url))

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
    Create a brand-new code file on the hub.
    Returns the new numeric ID assigned by Hubitat.
    """
    url = f"http://{host}/{code_type}/save"
    form_data = urllib.parse.urlencode(
        {
            "id": "",
            "version": "",
            "create": "",
            "source": source,
        }
    ).encode("utf-8")

    req = _create_req(url, data=form_data, method="POST")
    status, _, headers = _open(req, allow_redirects=False)

    if status == 302:
        location = headers.get("Location", headers.get("location", ""))
        match = re.search(r"/(\d+)$", location)
        if match:
            return int(match.group(1))
        raise RuntimeError(f"Could not parse new ID from redirect location: {location}")

    raise RuntimeError(f"Failed to create new {code_type}. HTTP {status}")


def update_existing_code(host, code_type, code_id, version, source):
    """
    Update an existing code file on the hub.
    Returns (success_bool, new_version_int).
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
    status, body, _ = _open(req)

    if status == 200:
        try:
            data = json.loads(body)
            if data.get("status") == "success":
                return True, data.get("version", version + 1)
            else:
                error_msg = data.get("errorMessage", "Unknown error")
                # Clean up Java SQL prefix that Hubitat sometimes includes
                error_msg = error_msg.replace("java.sql.SQLException:", "").strip()
                raise RuntimeError(f"Hub rejected update: {error_msg}")
        except json.JSONDecodeError:
            raise RuntimeError(
                f"Unexpected response from hub (HTTP 200 but not JSON): {body[:200]}"
            )

    raise RuntimeError(f"Failed to update {code_type}. HTTP {status}: {body[:200]}")


# ---------------------------------------------------------------------------
# Main publish flow
# ---------------------------------------------------------------------------


def publish(host, code_type, filepath, workspace_root):
    """Orchestrate the full publish flow for a single file."""
    # ---- Read source ----
    while True:
        print(f"File: {filepath}")
        try:
            source = Path(filepath).read_text(encoding="utf-8")
        except (IOError, OSError) as e:
            print(f"Error reading source file: {e}")
            sys.exit(1)

        # ---- Quick confirmation (Enter=continue, type a different path to switch) ----
        cfm = input("Press Enter to publish this file, or type a new path: ").strip()
        if cfm == "":
            break
        # Treat as a new file path
        new_path = cfm
        if not os.path.isabs(new_path):
            new_path = os.path.abspath(os.path.join(workspace_root, new_path))
        if not os.path.isfile(new_path):
            print(f"File not found: {new_path}")
            continue
        filepath = new_path
        # Re-detect code type from the new file
        redetected = detect_code_type(filepath)
        if redetected:
            code_type = redetected
            print(f"Auto-detected code type: {code_type}")
        else:
            print(
                "Could not auto-detect code type from new file; using previously detected type."
            )

    # ---- Confirm detected type matches (already resolved by main if using auto) ----
    detected = detect_code_type(filepath)
    if detected and detected != code_type:
        print(
            f"\u26a0\ufe0f  Warning: file content looks like a '{detected}' "
            f"but you're publishing as '{code_type}'."
        )
        print(f"   Proceeding anyway...")
    elif detected is None:
        print(
            f"\u26a0\ufe0f  Warning: could not auto-detect code type from file content."
        )
        print(f"   Publishing as '{code_type}'.")

    # ---- Load metadata ----
    files = load_metadata(workspace_root)
    existing = find_file_in_metadata(files, filepath)

    if existing and existing.get("id") is not None:
        # ============================================================
        # UPDATE EXISTING
        # ============================================================
        code_id = existing["id"]
        local_version = existing.get("version", "?")

        print(
            f"Found existing {code_type} mapping: ID={code_id}, local version={local_version}"
        )
        print(f"Fetching current version from hub...")

        hub_version = get_hub_version(host, code_type, code_id)
        if hub_version is None:
            print(
                f"\u26a0\ufe0f  Could not fetch version from hub (code may have been deleted)."
            )
            print(f"   Forcing update with version 0...")
            hub_version = 0
        else:
            print(f"Hub version: {hub_version}")

        print(f"Publishing to {host} ...")
        success, new_version = update_existing_code(
            host, code_type, code_id, hub_version, source
        )

        if success:
            existing["version"] = new_version
            save_metadata(workspace_root, files)
            print(
                f"\u2705 Successfully published {code_type} (ID {code_id}, version {new_version})"
            )

    else:
        # ============================================================
        # CREATE NEW (or link to existing)
        # ============================================================
        print(f"No existing mapping found for this file in {METADATA_FILE}.")

        # ---- Auto-detect from hub ----
        code_name, code_namespace = extract_name_and_namespace(source)
        if code_name:
            print(
                f'Extracted name="{code_name}", namespace="{code_namespace}" from source.'
            )
        else:
            print("Could not extract name from source code.")

        print(f"Fetching existing {code_type}s from hub...")
        hub_list = fetch_hub_code_list(host, code_type)

        matched = None
        if hub_list:
            print(f"Found {len(hub_list)} {code_type}(s) on hub.")
            matched = find_matching_code(hub_list, code_name, code_namespace)
        else:
            print(
                f"Could not fetch {code_type} list from hub (will fall back to manual entry)."
            )

        if matched:
            # ---- Auto-matched an existing hub entry ----
            match_id = matched["id"]
            match_name = matched["name"]
            print()
            print(f"Found matching {code_type} on hub:")
            print(f"  ID: {match_id}")
            print(f"  Name: {match_name}")
            if matched.get("namespace"):
                print(f"  Namespace: {matched['namespace']}")
            print()

            prompt = (
                input(f"Link to this {code_type}? (Y/n, or enter a different ID): ")
                .strip()
                .lower()
            )

            if prompt == "" or prompt == "y":
                # Use the matched ID
                code_id = match_id
            else:
                # User may have entered a different ID
                try:
                    code_id = int(prompt)
                    print(f"Linking to manually specified {code_type} ID {code_id} ...")
                except ValueError:
                    print(f"Invalid input. Creating new {code_type} instead.")
                    code_id = None

            if code_id is not None:
                hub_version = get_hub_version(host, code_type, code_id)
                if hub_version is None:
                    print(
                        f"\u26a0\ufe0f  No {code_type} with ID {code_id} found on hub."
                    )
                    confirm = (
                        input("Continue anyway and force push? (y/n): ").strip().lower()
                    )
                    if confirm != "y":
                        print("Cancelled.")
                        sys.exit(0)
                    hub_version = 0

                success, new_version = update_existing_code(
                    host, code_type, code_id, hub_version, source
                )

                if success:
                    files.append(
                        {
                            "filepath": filepath,
                            "codeType": code_type,
                            "id": code_id,
                            "version": new_version,
                        }
                    )
                    save_metadata(workspace_root, files)
                    print(
                        f"\u2705 Successfully published {code_type} (ID {code_id}, version {new_version})"
                    )
                return

        # ---- No auto-match — manual entry or create new ----
        print()
        prompt = input("Enter Hubitat ID (leave blank to create new): ").strip()

        if prompt:
            # ---- Link to an existing Hubitat code by ID ----
            try:
                code_id = int(prompt)
            except ValueError:
                print(f"Invalid ID: '{prompt}'. Must be a number.")
                sys.exit(1)

            print(f"Linking to existing {code_type} ID {code_id} ...")
            hub_version = get_hub_version(host, code_type, code_id)
            if hub_version is None:
                print(f"\u26a0\ufe0f  No {code_type} with ID {code_id} found on hub.")
                confirm = (
                    input("Continue anyway and force push? (y/n): ").strip().lower()
                )
                if confirm != "y":
                    print("Cancelled.")
                    sys.exit(0)
                hub_version = 0

            success, new_version = update_existing_code(
                host, code_type, code_id, hub_version, source
            )

            if success:
                files.append(
                    {
                        "filepath": filepath,
                        "codeType": code_type,
                        "id": code_id,
                        "version": new_version,
                    }
                )
                save_metadata(workspace_root, files)
                print(
                    f"\u2705 Successfully published {code_type} (ID {code_id}, version {new_version})"
                )

        else:
            # ---- Create brand new code on Hubitat ----
            print(f"Creating new {code_type} on hub ...")
            code_id = create_new_code(host, code_type, source)

            files.append(
                {
                    "filepath": filepath,
                    "codeType": code_type,
                    "id": code_id,
                    "version": 1,
                }
            )
            save_metadata(workspace_root, files)
            print(f"\u2705 Created new {code_type} on hub with ID {code_id}")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    code_type = sys.argv[1].lower()
    filepath = sys.argv[2]
    workspace_root = (
        sys.argv[3]
        if len(sys.argv) > 3
        else os.environ.get("ZED_WORKTREE_ROOT", os.getcwd())
    )

    # ---- Resolve "auto" code type ----
    if code_type == "auto":
        code_type = detect_code_type(filepath)
        if code_type is None:
            print(
                "Error: could not auto-detect code type from file content.\n"
                "Please specify one of: app, driver, library"
            )
            sys.exit(1)
        print(f"Auto-detected code type: {code_type}")

    if code_type not in VALID_TYPES:
        print(
            f"Invalid code type: '{code_type}'. Must be one of: {', '.join(sorted(VALID_TYPES))}"
        )
        sys.exit(1)

    # Normalize to absolute path so metadata entries are always consistent
    filepath = os.path.abspath(filepath)

    if not os.path.isfile(filepath):
        print(f"File not found: {filepath}")
        sys.exit(1)

    try:
        host = load_hub_hostname(workspace_root)
        publish(host, code_type, filepath, workspace_root)
    except (FileNotFoundError, ValueError) as e:
        print(f"Configuration error: {e}")
        sys.exit(1)
    except ConnectionError as e:
        print(f"Connection error: {e}")
        print(f"Verify your hub is reachable at the address in {METADATA_FILE}")
        sys.exit(1)
    except RuntimeError as e:
        print(f"Publish error: {e}")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\nCancelled.")
        sys.exit(0)


if __name__ == "__main__":
    main()
