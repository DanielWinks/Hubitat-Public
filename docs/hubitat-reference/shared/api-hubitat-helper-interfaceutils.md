# Interface utils

- **ID:** `api-hubitat-helper-interfaceutils`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.InterfaceUtils`

> Use InterfaceUtils from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `alphaV1mqttConnect` | `void alphaV1mqttConnect(Map options = [:], DeviceWrapper device, String broker, String clientId, String username, String password)` | Connect a device to an MQTT broker. Parameters: options - MQTT settings documented by Mqtt.connect device - device whose connection is opened broker - broker URI or host clientId - |
| `alphaV1mqttDisconnect` | `void alphaV1mqttDisconnect(DeviceWrapper device)` | Disconnect a device's MQTT connection. Parameters: device - device whose connection is closed |
| `alphaV1mqttPublish` | `void alphaV1mqttPublish(DeviceWrapper device, String topic, String payload, int qos = 1, boolean retained = false)` | Publish an MQTT message. Parameters: device - device with the MQTT connection topic - MQTT topic payload - message text qos - quality of service from 0 through 2 retained - whether |
| `alphaV1mqttSubscribe` | `void alphaV1mqttSubscribe(DeviceWrapper device, String topicFilter, int qos = 1)` | Subscribe a device to an MQTT topic filter. Parameters: device - device with the MQTT connection topicFilter - MQTT topic filter qos - quality of service from 0 through 2 |
| `alphaV1mqttUnsubscribe` | `void alphaV1mqttUnsubscribe(DeviceWrapper device, String topicFilter)` | Remove a device's MQTT topic subscription. Parameters: device - device with the MQTT connection topicFilter - MQTT topic filter |
| `alphaV1parseMqttMessage` | `Map alphaV1parseMqttMessage(String stringToParse)` | Parse the legacy MQTT callback message. Parameters: stringToParse - callback text Returns: map with the schema documented by Mqtt.parseMessage |
| `alphaV1PublishMqttMessage` | `void alphaV1PublishMqttMessage(String topic, String content, int qos, String broker, String clientId, String userName, String password)` | Publish one MQTT message using a temporary client. throws: Exception when connecting or publishing fails Parameters: topic - MQTT topic content - message text qos - quality of serv |
| `eventStreamClose` | `void eventStreamClose(DeviceWrapper deviceWrapper)` | Close a device's HTTP event stream. Parameters: deviceWrapper - device whose connection is closed |
| `eventStreamConnect` | `void eventStreamConnect(DeviceWrapper deviceWrapper, String url, String accessToken)` | Open an HTTP event stream using the legacy access-token form. Parameters: deviceWrapper - device whose connection is opened url - event stream URL accessToken - token sent in the A |
| `isSystemTypeOrHubDeveloper` | `boolean isSystemTypeOrHubDeveloper(Long deviceTypeId)` | Parameters: deviceTypeId - device type id Returns: true for a system device type or when hub developer mode is enabled |
| `sendSocketMessage` | `void sendSocketMessage(DeviceWrapper device, String message)` | Send a message over a device's TCP socket. Parameters: device - device with the open connection message - message text or bytes formatted according to socket settings |
| `sendWebSocketMessage` | `void sendWebSocketMessage(DeviceWrapper device, String message)` | Send a message over a device's WebSocket. Parameters: device - device with the open connection message - text or hexadecimal bytes according to the connection settings |
| `socketClose` | `void socketClose(DeviceWrapper device)` | Close a device's TCP socket. Parameters: device - device whose connection is closed |
| `socketConnect` | `void socketConnect(Map options = [:], DeviceWrapper device, String ip, int port)` | Open a TCP socket for a device. Parameters: options - socket settings as documented by RawSocket.connect device - device whose connection is opened ip - remote host address port -  |
| `webSocketClose` | `void webSocketClose(DeviceWrapper device)` | Close a device's WebSocket. Parameters: device - device whose connection is closed |
| `webSocketConnect` | `void webSocketConnect(Map options = [:], DeviceWrapper device, String url)` | Open a WebSocket for a device. Parameters: options - WebSocket settings as documented by WebSocket.connect device - device whose connection is opened url - WebSocket URL |
