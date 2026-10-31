# Network utilities

- **ID:** `shared-network`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.BaseExecutor`

> Use these methods directly in app or driver code; the hub supplies this execution context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getMACFromIP` | `String getMACFromIP(String ipAddr)` | Looks up the MAC address associated with an IP address. Parameters: ipAddr - IP address to look up. Returns: MAC address, or null when no matching address is found. |
| `getMDNSEntries` | `List<Map> getMDNSEntries(String serviceType)` | Returns resolved mDNS service endpoints for a local service type. Parameters: serviceType - Service type without the .local. suffix. Returns: List of endpoint maps. Each map contai |
| `getZigbeeDeviceJoinName` | `String getZigbeeDeviceJoinName(String manufacturer, String model)` | Looks up a Zigbee device join name from manufacturer and model strings. Parameters: manufacturer - Manufacturer string reported by the device. model - Model string reported by the  |
| `getZWaveDeviceJoinName` | `String getZWaveDeviceJoinName(Integer mfr, Integer typeId, Integer productId)` | Looks up a Z-Wave device join name from integer identifiers. Parameters: mfr - Manufacturer id. typeId - Device type id. productId - Product id. Returns: Matching device type name, |
| `getZWaveDeviceJoinName` | `String getZWaveDeviceJoinName(String mfr, String typeId, String productId)` | Looks up a Z-Wave device join name from hexadecimal identifiers. Parameters: mfr - Manufacturer id text. typeId - Device type id text. productId - Product id text. Returns: Matchin |
| `isValidIP` | `boolean isValidIP(String ip)` | Tests whether a string is a valid IP address. Parameters: ip - Address text to validate. Returns: True when LanService accepts the address. |
| `isValidMAC` | `boolean isValidMAC(String mac)` | Tests whether a string is a valid MAC address. Parameters: mac - Address text to validate. Returns: True when LanService accepts the address. |
| `parseLanMessage` | `Map parseLanMessage(String stringToParse)` | Decodes a LAN message and parses its optional HTTP headers and body. throws: ArrayIndexOutOfBoundsException when a non-empty comma-separated field has no colon. throws: NullPointer |
| `ping` | `boolean ping(String url, int timeoutMillis)` | Sends a GET request to test whether a URL responds. Parameters: url - URL to request. timeoutMillis - Connection and response timeout in milliseconds. Returns: True when the reques |
| `registerMDNSListener` | `void registerMDNSListener(String serviceType)` | Registers the shared mDNS listener for a local service type if one is not already registered. Parameters: serviceType - Service type without the .local. suffix. |
| `startMdnsListener` | `void startMdnsListener(String serviceType, int durationSeconds = 30)` | Listens for mDNS service changes on a worker thread for a bounded duration. Parameters: serviceType - mDNS service type passed to JmDNSService. durationSeconds - Listen duration in |
