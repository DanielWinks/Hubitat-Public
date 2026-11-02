# Hubitat Logs MCP Server

This standalone Hubitat app exposes read-only MCP tools for historical and live
Hubitat logs. Historical entries come from the hub's internal equivalent of
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

The app exposes three tools:

- `hubitat_get_logs` filters by app instance ID, severity, keyword, and timestamp;
  it returns compact structured entries newest first. Pages default to 25 entries
  (maximum 50) and use opaque cursors over a 60-second snapshot.
- `hubitat_get_log_summary` returns counts by severity and source, repeated issues,
  and the latest warnings/errors without returning the full log list.
- `hubitat_get_live_logs` starts/maintains a local log WebSocket connection and
  returns up to 100 compact buffered entries. The first call creates the child
  device using the `Hubitat Log Stream` driver above.

Historical raw logs are parsed and cached for three seconds. Search snapshots are
kept for up to 60 seconds, with at most eight retained. Log message HTML is
removed and text is normalized before returning it. The historical route is fixed
in the source so the app cannot act as a general-purpose HTTP proxy.

The local endpoint uses HTTP. Keep it on a trusted LAN and do not share the OAuth
URL: its access token grants access to this app's MCP endpoint.
