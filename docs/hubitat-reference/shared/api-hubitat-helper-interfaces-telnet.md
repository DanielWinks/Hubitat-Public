# Telnet

- **ID:** `api-hubitat-helper-interfaces-telnet`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.Telnet`

> Use Telnet from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `close` | `void close()` | Close this device's Telnet connection. |
| `connect` | `void connect(Map options, String ip, int port, String username, String password)` | Connect to the remote Telnet endpoint. Parameters: options - optional settings map with: byteInterface: Boolean selecting hexadecimal byte payloads. termChars: collection of charac |
| `connect` | `void connect(String ip, int port, String username, String password)` | Connect with default Telnet settings. Parameters: ip - remote host address port - remote TCP port username - login name password - login password |
| `sendMessage` | `void sendMessage(String message)` | Parameters: message - text to send to the remote endpoint |
| `Telnet` | `Telnet(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose Telnet connection is managed |
