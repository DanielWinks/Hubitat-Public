# MQTT

- **ID:** `api-hubitat-helper-interfaces-mqtt`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.Mqtt`

> Use Mqtt from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `connect` | `void connect(Map options = [:], String broker, String clientId, String username, String password)` | Connect to an MQTT broker. Parameters: options - optional settings map: byteInterface: Boolean selecting hexadecimal payload bytes. ignoreSSLIssues: Boolean controlling certificate |
| `connectToBuiltInBroker` | `boolean connectToBuiltInBroker(String clientId)` | Connects to the running built-in broker without exposing its credentials to the driver. Drivers can check hubitat.helper.MQTTHelper#isBuiltInBrokerRunning() first. The usual MQTT s |
| `disconnect` | `void disconnect()` | Disconnect this device's MQTT client. |
| `isConnected` | `boolean isConnected()` | Returns: true when this device has an active MQTT connection |
| `Mqtt` | `Mqtt(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose MQTT connection is managed |
| `parseMessage` | `Map parseMessage(String stringToParse)` | Parse a callback message containing comma-separated key:value fields. Parameters: stringToParse - callback text Returns: map with open String keys and String or null values; topic  |
| `publish` | `void publish(String topic, String payload, int qos = 1, boolean retained = false)` | Publish a message. Parameters: topic - MQTT topic payload - message text, or hexadecimal bytes when byteInterface is enabled qos - quality of service, from 0 to 2 retained - whethe |
| `publishMqttMessage` | `void publishMqttMessage(String topic, String content, int qos, String broker, String clientId, String userName, String password)` | Publish one MQTT message using a temporary client connection. throws: Exception when connecting or publishing fails Parameters: topic - MQTT topic content - message text qos - qual |
| `subscribe` | `void subscribe(String topicFilter, int qos = 1)` | Subscribe to a topic filter. Parameters: topicFilter - MQTT topic filter qos - quality of service, from 0 to 2 |
| `unsubscribe` | `void unsubscribe(String topicFilter)` | Parameters: topicFilter - MQTT topic filter to remove |
