# Raw socket

- **ID:** `api-hubitat-helper-interfaces-rawsocket`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.RawSocket`

> Use RawSocket from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `void close()` | Close and remove this socket connection. |
| `connect` | `void connect(Map options = [:], String ip, int port)` | Opens a TCP connection using the supplied driver options. The options map supports: byteInterface: Boolean selecting hexadecimal byte payloads. readDelay: positive millisecond dela |
| `disconnect` | `void disconnect()` | Remove this device's socket interface. |
| `isConnected` | `boolean isConnected()` | Returns: true when this device has an active socket interface |
| `RawSocket` | `RawSocket(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose TCP connection is managed |
| `sendMessage` | `void sendMessage(String message)` | Parameters: message - text or hexadecimal byte payload, depending on byteInterface |
