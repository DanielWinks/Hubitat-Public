# Chrome cast

- **ID:** `api-com-hubitat-hub-controller-interfaces-chromecast`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.controller.interfaces.ChromeCast`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `connect` | `void connect(String ip, int port)` | Connects this device's Chromecast using the selected controller. Parameters: ip - Chromecast IP address port - Chromecast control port |
| `disconnect` | `void disconnect()` | Disconnects this device's Chromecast and removes its controller interface. |
| `getMediaStatus` | `void getMediaStatus()` | Requests the selected controller's media status; the controller updates device state asynchronously. |
| `getStatus` | `void getStatus()` | Requests the selected controller's device status; the controller updates device state asynchronously. |
| `isConnected` | `Boolean isConnected()` | Returns: true when the selected controller reports a connection; null when no interface is available |
| `isUsePyController` | `boolean isUsePyController()` | Returns: true when the Python-backed Chromecast controller is selected; false on hubs that do not support it |
| `launchApp` | `void launchApp(String appId)` | Parameters: appId - Chromecast application identifier to launch |
| `loadURL` | `void loadURL(String url)` | throws: IllegalArgumentException when the URL is empty or invalid for the selected controller Parameters: url - media URL to load on the Chromecast |
| `mute` | `void mute()` | Mutes Chromecast audio. |
| `pause` | `void pause()` | Pauses Chromecast playback. |
| `play` | `void play()` | Resumes Chromecast playback. |
| `setUsePyController` | `void setUsePyController(boolean value)` | Selects the Chromecast controller and closes every session owned by the controller being replaced. Parameters: value - true to use the Python-backed controller, false to use the le |
| `setVolume` | `void setVolume(float level)` | Parameters: level - volume level passed to the selected Chromecast controller |
| `stop` | `void stop()` | Stops Chromecast playback. |
| `unmute` | `void unmute()` | Unmutes Chromecast audio. |

## Properties

| Name | Type | Description |
|---|---|---|
| `deviceId` | `Long` | Hubitat device id used by delegated Chromecast controller operations. |
