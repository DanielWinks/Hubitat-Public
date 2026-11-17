# Hubitat Logs MCP Server

This standalone Hubitat app exposes MCP tools for logs, device diagnostics, and
device commands. Historical entries come from the hub's internal equivalent of
`http://192.168.1.4/logs/past/json`; the app fetches through
`http://127.0.0.1:8080` so the hub does not route back to its LAN address.

## Install

1. In Hubitat, open **Drivers Code** and add
   [`Drivers/Virtual/HubitatLogStream.groovy`](../Drivers/Virtual/HubitatLogStream.groovy).
2. In **Apps Code**, add [`Apps/HubitatLogsMCP.groovy`](../Apps/HubitatLogsMCP.groovy).
3. Open the app code's **OAuth** page, enable OAuth, and save.
4. Choose **Apps > Add User App > Hubitat Logs MCP Server**, then save and reopen it.
5. Copy the displayed local MCP URL, including its `access_token` query parameter,
   into the MCP client's Streamable HTTP server configuration.

The app exposes ten tools:

- `hubitat_get_logs` filters by app instance ID, device ID, source type (`app`
  or `dev`), severity, keyword, and timestamp;
  it returns compact structured entries newest first. Pages default to 25 entries
  (maximum 50) and use opaque cursors over a 60-second snapshot.
- `hubitat_get_log_summary` accepts the same source filters and returns counts by severity and source, repeated issues,
  and the latest warnings/errors without returning the full log list.
- `hubitat_get_live_logs` starts/maintains a local log WebSocket connection and
  returns up to 100 compact buffered entries, with optional app, device, and
  source type filters. The first call creates the child
  device using the `Hubitat Log Stream` driver above.
- `hubitat_get_diagnostic_timeline` merges historical log entries for selected
  `appIds` and `deviceIds` in chronological order. Supply `since` (inclusive)
  and `before` (exclusive) in hub-local `YYYY-MM-DD HH:mm:ss.SSS` format, plus
  at least one ID. It returns log entries only; it does not include device events.
  Results use the same 60-second snapshot pagination as historical search.
  When continuing, pass `cursor` and optionally `maxEntries`; other filters are
  ignored. Historical hub retention limits the available time range.
- `hubitat_get_device_details` reads a device's detailed JSON from the fixed
  local `/device/fullJson/<deviceId>` route. Choose a `section`: `overview`
  (default), `device`, `data`, `attributes`, `state`, `settings`, `inputValues`,
  `commands`, `scheduledJobs`, `appsUsing`, `appsUsingForDialog`, `childDevices`,
  `parentApp`, `dashboards`, `dashboardTypes`, or `raw`. The `raw` section exposes
  all top-level fields, including fields introduced by a future hub version.
  `appsUsing` is the full list; the dialog list may be shorter. Map entries are
  returned as `{key, value}`, array entries as `{index, value}`; `shape` and
  `present` distinguish missing, null, empty, and populated sections. Pass the
  same device ID and section with the returned `cursor` to continue the
  60-second snapshot. Pages default to 25 entries (maximum 50), and may be
  shortened to fit the response size limit. If one entry alone is too large,
  select a more specific section. Device settings, state, and data may contain
  credentials or other sensitive information.
- `hubitat_invoke_device_command` invokes an advertised device command through
  the fixed local `/device/runmethod` route. Supply `deviceId`, the exact
  `command` name, and optional `args` in driver order. Inspect the `commands`
  section of `hubitat_get_device_details` for available commands and their
  parameters. The app checks current command metadata before sending the
  request. This tool can change device state; use it only when requested. A
  timeout or malformed response does not establish whether the command ran, so
  check device state before retrying.
- `hubitat_list_device_inventory` returns device IDs, names, types, rooms,
  network IDs, status flags, and other inventory metadata, including nested child
  devices flattened with their `parentId`. Filter by `query`, exact `room`, or
  `deviceId`. Current attribute values are omitted. Results are sorted by name
  and use 60-second snapshot cursors; on continuation only `cursor` and
  `maxEntries` are used. The source is the fixed `/hub2/devicesList` route.
- `hubitat_list_app_inventory` returns installed app instances and their child
  apps, including instance IDs, app type IDs, status, and `parentId`. Its
  `section` can instead select `systemAppTypes`, `userAppTypes`, or
  `appCollections`. Filter by `query` or an exact installed `appId` (the latter
  applies only to the `apps` section). Names are normalized to remove UI HTML.
  Results use 60-second snapshot cursors; on continuation only `cursor` and
  `maxEntries` are used. The source is the fixed `/hub2/appsList` route.
- `hubitat_get_runtime_diagnostics` reads the fixed local `/logs/json` route.
  Select `summary` (default), `appStats`, `deviceStats`, `jobs`, or `runningJobs`.
  The summary includes scalar hub counters and identifies which data sections
  are present. List and map sections are paginated with the same short lived
  snapshot cursors. Hub performance can affect response time.
- `hubitat_get_app_diagnostics` reads `/installedapp/statusJson/<appId>` for a
  single installed app and returns available app identity, event subscriptions,
  scheduled jobs, and child metadata. It deliberately excludes app settings and
  internal app state because those fields may contain OAuth tokens, passwords,
  or other credentials. Sensitive fields are also removed recursively from
  returned records. Results are paginated with a short lived snapshot cursor.

App and device IDs identify different source types even when their numeric IDs
match. `appId` and `deviceId` cannot be combined; a `sourceType` filter must
agree with the supplied ID.

Historical raw logs are parsed and cached for three seconds. Search snapshots are
kept for up to 60 seconds, with at most eight retained. Log message HTML is
removed and text is normalized before returning it. The historical route is fixed
in the source; device detail requests use only the fixed device route and a
validated numeric ID. Device commands use a second fixed route and are limited
to commands advertised by the selected device.

The local endpoint uses HTTP. Keep it on a trusted LAN and do not share the OAuth
URL: its access token grants access to device and app diagnostics and command
execution.
