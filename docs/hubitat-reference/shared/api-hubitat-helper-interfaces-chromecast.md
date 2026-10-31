# Chromecast

- **ID:** `api-hubitat-helper-interfaces-chromecast`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.Chromecast`

> Use Chromecast from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `Chromecast` | `Chromecast(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose Chromecast connection is managed |
| `connect` | `void connect(String ip, int port)` | Connect to a Chromecast device. Parameters: ip - Chromecast IP address port - Chromecast service port |
| `disconnect` | `void disconnect()` | Disconnect this device from Chromecast. |
| `getMediaStatus` | `void getMediaStatus()` | Request current Chromecast media status. |
| `getStatus` | `void getStatus()` | Request current Chromecast device status. |
| `isConnected` | `Boolean isConnected()` | Returns: true when connected, false when disconnected, or null when no interface exists |
| `isUsePyController` | `boolean isUsePyController()` | Returns: true when the Python Chromecast controller is selected |
| `launchApp` | `Application launchApp(String appId)` | Launch a Chromecast application. Parameters: appId - Chromecast application id Returns: launched application state, or null when launch does not produce an Application value |
| `loadURL` | `void loadURL(String url)` | Load media from a URL. Parameters: url - media URL |
| `mute` | `void mute()` | Mute Chromecast audio. |
| `pause` | `void pause()` | Pause Chromecast playback. |
| `play` | `void play()` | Resume Chromecast playback. |
| `setUsePyController` | `void setUsePyController(boolean value)` | Parameters: value - true to use the Python Chromecast controller |
| `setVolume` | `void setVolume(float level)` | Set Chromecast volume. Parameters: level - volume level accepted by the Chromecast controller |
| `stop` | `void stop()` | Stop the current Chromecast media session. |
| `unmute` | `void unmute()` | Unmute Chromecast audio. |
