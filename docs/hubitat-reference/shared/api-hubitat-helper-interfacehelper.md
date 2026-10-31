# Interface helper

- **ID:** `api-hubitat-helper-interfacehelper`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.InterfaceHelper`

> Use InterfaceHelper from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAwsMqtt` | `AWSMqtt getAwsMqtt()` | Returns: lazily created AWS MQTT helper for this device |
| `getEventStream` | `EventStream getEventStream()` | Returns: lazily created event-stream helper for this device |
| `getMqtt` | `Mqtt getMqtt()` | Returns: lazily created MQTT helper for this device |
| `getMulticastSocket` | `UDPMulticastSocket getMulticastSocket(String ip, int port)` | Return the cached multicast helper for an address and port, creating it when needed. Parameters: ip - multicast group address port - multicast UDP port Returns: multicast socket he |
| `getRawSocket` | `RawSocket getRawSocket()` | Returns: lazily created raw TCP socket helper for this device |
| `getWebSocket` | `WebSocket getWebSocket()` | Returns: lazily created WebSocket helper for this device |
| `InterfaceHelper` | `InterfaceHelper(DeviceWrapper deviceWrapper)` | Create interface accessors for a device. Parameters: deviceWrapper - device whose network interfaces are managed |
| `isSystemTypeOrHubDeveloper` | `boolean isSystemTypeOrHubDeveloper(Long deviceTypeId)` | Check whether a device type can use system interfaces. Parameters: deviceTypeId - device type id Returns: true for system device types or when hub developer mode is enabled |
