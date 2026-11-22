# Hubitat Logs MCP Server

This standalone Hubitat app exposes MCP tools for logs and device events,
device diagnostics, hub details and health counters, device commands, and
guarded hub actions (reboot, job deletion, thread dump). Historical entries
come from the hub's internal equivalent of
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

The app exposes eighteen tools:

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
  at least one ID. It returns log entries only; use `hubitat_get_device_events`
  for device and location events. Results use the same 60-second snapshot
  pagination as historical search. When continuing, pass `cursor` and optionally
  `maxEntries`; other filters are ignored. Historical hub retention limits the
  available time range.
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
- `hubitat_reboot_hub` immediately reboots the hub through its fixed local
  `/hub/reboot` endpoint. It requires `allowSensitive: true` and should only be
  called when the user explicitly requests a reboot. The request always sets
  `rebuildDataBase` and `purgeLogs` to `false`. If the hub closes the connection
  before responding, the result is marked unknown and non-retryable; check hub
  connectivity before issuing another reboot request.
- `hubitat_list_device_inventory` returns device IDs, names, types, rooms,
  network IDs, status flags, and other inventory metadata, including nested child
  devices flattened with their `parentId`. Filter by `query`, exact `room`,
  `deviceId`, or `capability` (for example `capability.switch` or
  `device.SomeDriver`; capability names are case-insensitive). When
  `capability` is given, only matching devices are returned from the fixed
  `/device/listJson` route as sparse ID and name records; otherwise all devices
  come from `/hub2/devicesList`. Current attribute values are omitted. Results
  are sorted by name and use 60-second snapshot cursors; on continuation only
  `cursor` and `maxEntries` are used.
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
- `hubitat_get_device_events` reads the fixed local `/logs/eventsJson` route
  and returns device and location events newest first. Filter by `deviceId`,
  `attribute`, `contains`, and hub-local `since`/`before`. Results use the same
  60-second snapshot pagination as historical search; pair this tool with
  `hubitat_get_logs` to correlate device changes with log messages.
- `hubitat_get_hub_info` reads the fixed local `/hub/details/json` route and
  returns scalar hub identity and platform details (hub name, hardware and
  platform version, network and time settings). Large reference lists are
  returned as counts and account data is excluded.
- `hubitat_get_hub_health` reads the fixed local CPU, memory, database, and
  temperature routes. Select `cpu`, `memory`, `memoryHistory`, `memoryLast`,
  `database`, or `temperature`, or omit `section` to return all six (each
  reported as `present` or not, so one missing counter does not fail the call).
  History sections are paginated with short lived snapshot cursors.
- `hubitat_get_event_retention` reads the fixed local retention routes and
  returns the maximum event count, device-state history size, and maximum
  retention ages in days. Read-only; settings are not changed. A section
  reported as `present: false` means the running hub does not expose that
  readback (for example `deviceStateHistorySize` on firmware 2.5.3).
- `hubitat_list_rooms` reads the fixed local `/hub2/roomsList` route and
  returns room IDs and names for room resolution. Filter by `query`; results
  use short lived snapshot cursors.
- `hubitat_list_device_types` reads the fixed local `/device/drivers` and
  `/hub2/userDeviceTypes` routes. Select `drivers` (default) or
  `userDeviceTypes`; filter by `query`. Results use short lived snapshot
  cursors and sensitive fields are removed. Use these to resolve log source
  names to driver code.
- `hubitat_delete_app_jobs` deletes pending jobs for one app or device through
  the fixed local `/hub/advanced/deleteAppJobs/<targetId>` route. It requires
  `allowSensitive: true` and should only be called when the user explicitly
  requests it. Results are `accepted`, `unknown`, or an error; an unknown
  result means the deletion may have been applied, so check hub jobs before
  retrying.
- `hubitat_dump_threads_to_log` writes current Java threads to the engineering
  log through the fixed local `/hub/advanced/dumpThreadsToLog` route. It
  requires `allowSensitive: true` and should only be called when the user
  explicitly requests it. Follow up with `hubitat_get_logs` to read the dumped
  threads. A timeout is reported as `unknown`; check the engineering log
  before retrying.

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
URL: its access token grants access to device and app diagnostics, command
execution, job deletion, thread dumping, and hub reboot.
