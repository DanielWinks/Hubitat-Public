# Hubitat Dynamic Publisher

Publish the active Hubitat app or driver to a selected hub from VS Code. The extension fetches the hub's app or driver list on every publish, then matches the source `name` and `namespace` to find the current ID. It does not keep an ID lookup file in the workspace, so the same source can be published to hubs that assigned different IDs.

## Setup

Add hubs to VS Code User or Workspace Settings:

```json
"hubitatDynamicPublisher.hubs": [
  { "name": "C-7", "host": "192.168.1.3" },
  { "name": "C-8", "host": "http://hubitat-c8.local" }
]
```

Hosts may include `http://` or `https://`. Each publish prompts you to choose a hub.

## Publish

Open a `.groovy` app or driver and press **Ctrl+Alt+U** (Windows/Linux) or **Cmd+Alt+U** (macOS). You can also run **Hubitat: Publish Active App or Driver** from the Command Palette.

An exact name and namespace match is updated using the version fetched from that hub. If only the name matches, the extension asks whether to update it or create a new copy. If no match exists, it creates the app or driver. A hub compile/save rejection is shown as an error.

## Hubitat access

The extension uses Hubitat's local HTTP code editor endpoints (`/hub2/userAppTypes`, `/hub2/userDeviceTypes`, and the app/driver save endpoints), following the dynamic lookup and update flow in `scripts/hubitat_publish.py`. Configure a reachable local hub URL. Hubitat login-protected HTTP endpoints are not supported; use a trusted local network and the hub's supported local access configuration.

## Development

Open this directory in VS Code and press **F5** to launch an Extension Development Host. The extension has no runtime npm dependencies.
