# Network utils

- **ID:** `api-hubitat-helper-networkutils`
- **Section:** Shared APIs
- **Class:** `hubitat.helper.NetworkUtils`

> Use NetworkUtils from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `base64DecodeAndGzipDecompress` | `byte[] base64DecodeAndGzipDecompress(String data)` | Base64-decode and gzip-decompress text. Parameters: data - Base64-encoded compressed bytes Returns: decompressed bytes |
| `calculateMD5Hash` | `String calculateMD5Hash(String data)` | Calculate an MD5 digest for text. Parameters: data - text to hash Returns: lowercase digest text, or an empty string for blank input |
| `getLocalHubsByIP` | `Map<String, String> getLocalHubsByIP()` | Returns: map keyed by hub IP address (String), with hub names as String values |
| `getMDNSServiceResults` | `List<Map> getMDNSServiceResults(String mdnsServiceType, Integer timeoutMs = 5000)` | Browse for mDNS services and return their advertised addresses and records. Parameters: mdnsServiceType - mDNS service type timeoutMs - requested browse time in milliseconds, clamp |
| `getONVIFScanResults` | `List<Map> getONVIFScanResults()` | Returns the latest ONVIF network video transmitter discovery results. Returns: list of maps containing the responding camera host |
| `getRawMDNSEndpointsByMACForServiceType` | `Map<String, BaseEndpoint> getRawMDNSEndpointsByMACForServiceType(String serviceType)` | Return raw discovered endpoints for an mDNS service type. Parameters: serviceType - registered mDNS service type Returns: map keyed by MAC address (String), with BaseEndpoint value |
| `getRegisteredMDNSServiceTypes` | `List<String> getRegisteredMDNSServiceTypes()` | Returns: mDNS service type names registered on the hub |
| `getReolinkDiscoveryStatus` | `Map getReolinkDiscoveryStatus(String scanId)` | Reads a detached snapshot of a Reolink scan. Parameters: scanId - opaque identifier returned by startReolinkDiscovery Returns: map with: status: String status: running, complete, e |
| `getRTSPScanResults` | `List<Map> getRTSPScanResults()` | Return the latest RTSP port scan. Returns: maps with: host: String host name or IPv4 address. port: Integer TCP port. open: Boolean TCP connection result. rtspConfirmed: Boolean RT |
| `gzipCompress` | `byte[] gzipCompress(String data)` | Compress text with gzip using UTF-8 encoding. Parameters: data - text to compress Returns: compressed bytes |
| `gzipCompressAndBase64Encode` | `String gzipCompressAndBase64Encode(String data)` | Gzip-compress text and encode the bytes as Base64. Parameters: data - text to compress Returns: Base64 text |
| `gzipDecompress` | `byte[] gzipDecompress(byte[] data)` | Decompress gzip bytes. Parameters: data - compressed bytes Returns: decompressed bytes |
| `isONVIFDiscoveryRunning` | `boolean isONVIFDiscoveryRunning()` | Returns whether ONVIF discovery is currently running. Returns: true while an ONVIF discovery scan is running |
| `isRTSPDiscoveryRunning` | `boolean isRTSPDiscoveryRunning()` | Returns whether RTSP discovery is currently running. Returns: true while an RTSP discovery scan is running |
| `isValidHttpURL` | `boolean isValidHttpURL(String urlStr)` | Parameters: urlStr - URL text to check Returns: true when it parses as an HTTP or HTTPS URL |
| `ping` | `NetworkUtils.PingData ping(String IPAddress)` | Ping an IPv4 address three times with the default timeout. Parameters: IPAddress - IPv4 address Returns: packet and round-trip statistics, or null for invalid address text |
| `ping` | `NetworkUtils.PingData ping(String IPAddress, Integer count, Integer timeout = 3)` | Ping an IPv4 address with a bounded packet count and timeout. Parameters: IPAddress - IPv4 address count - requested packet count; capped at 5 timeout - requested timeout in second |
| `restartONVIFDiscovery` | `void restartONVIFDiscovery()` | Restarts ONVIF discovery unless an ONVIF discovery scan is already running. |
| `restartRTSPDiscovery` | `void restartRTSPDiscovery()` | Restarts RTSP discovery unless an RTSP discovery scan is already running. |
| `startReolinkDiscovery` | `String startReolinkDiscovery()` | Starts or joins a bounded local Reolink UID scan. Returns: opaque scan ID for getReolinkDiscoveryStatus |
