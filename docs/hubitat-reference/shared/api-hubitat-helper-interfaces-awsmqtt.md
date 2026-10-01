# AWS MQTT

- **ID:** `api-hubitat-helper-interfaces-awsmqtt`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.interfaces.AWSMqtt`

> Use AWSMqtt from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `AWSMqtt` | `AWSMqtt(DeviceWrapper deviceWrapper)` | Parameters: deviceWrapper - device whose AWS MQTT connection is managed |
| `connect` | `void connect(Map options = [:], String endpoint, String clientCertificate, String privateKey)` | Connect using the endpoint and device credentials. Parameters: options - optional connection map: connectionTimeout: Integer timeout in milliseconds. keepAliveInterval: Integer kee |
| `disconnect` | `void disconnect()` | Disconnect this device from its AWS MQTT endpoint. |
| `isConnected` | `boolean isConnected()` | Returns: true when this device has an active AWS MQTT connection |
| `parseMessage` | `Map parseMessage(String stringToParse)` | Parse the comma-separated key/value callback message. Parameters: stringToParse - callback text with comma-separated key:value entries Returns: map with open String keys and String |
| `send` | `void send(String payload)` | Parameters: payload - message text to publish |
