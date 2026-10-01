# Event stream

- **ID:** `api-hubitat-helper-interfaces-eventstream`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.EventStream`

> Use EventStream from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `void close()` | Close this device's event stream. |
| `connect` | `void connect(String url, Map options = null)` | Connect to an event stream URL. Parameters: url - event stream URL options - optional map with these settings: headers: map of open HTTP header-name Strings to String values. pingI |
| `EventStream` | `EventStream(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose event stream is managed |
| `eventStreamClose` | `void eventStreamClose()` | Deprecated close alias; use close(). |
| `eventStreamConnect` | `void eventStreamConnect(String url, String accessToken)` | Deprecated connection form; use connect(String, Map). Parameters: url - event stream URL accessToken - token sent as the Authorization header value |
