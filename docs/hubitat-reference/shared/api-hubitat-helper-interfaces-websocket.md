# Web socket

- **ID:** `api-hubitat-helper-interfaces-websocket`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.WebSocket`

> Use WebSocket from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `void close()` | Close this device's WebSocket connection. |
| `connect` | `void connect(Map options = [:], String url)` | Connect to the specified WebSocket URL. Parameters: options - optional map with: byteInterface: Boolean selecting hexadecimal payload bytes. pingInterval: Integer seconds, defaults |
| `sendMessage` | `void sendMessage(String message)` | Parameters: message - text or hexadecimal byte payload, depending on byteInterface |
| `WebSocket` | `WebSocket(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose WebSocket connection is managed |
