# UDP multicast socket

- **ID:** `api-hubitat-helper-interfaces-udpmulticastsocket`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.UDPMulticastSocket`

> Use UDPMulticastSocket from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `void close()` | Close this multicast socket. |
| `connect` | `void connect(Map options = [:])` | Open the multicast socket. Parameters: options - optional map with: timeout: Integer receive timeout in milliseconds. sendBufferSize: Integer buffer size from 1 to 524288 bytes. re |
| `disconnect` | `void disconnect()` | Close this multicast socket. |
| `isConnected` | `boolean isConnected()` | Returns: true when this device has an active socket for the configured multicast group and port |
| `sendMessage` | `void sendMessage(String message)` | Parameters: message - text to send to the multicast group |
| `UDPMulticastSocket` | `UDPMulticastSocket(DeviceWrapper deviceWrapper, String ip, int port)` | Parameters: deviceWrapper - device whose multicast socket is managed ip - multicast group address port - multicast UDP port |
