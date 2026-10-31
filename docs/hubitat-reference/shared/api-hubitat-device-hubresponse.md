# Hub response

- **ID:** `api-hubitat-device-hubresponse`
- **Section:** Shared APIs
- **Class:** `hubitat.device.HubResponse`

> Use HubResponse from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getBody` | `String getBody()` | Return the response body, decoding its base64 representation on first access. Returns: decoded body text, or null when no body was supplied |
| `getData` | `Object getData()` | Return the parsed XML or JSON body. Returns: XML GPathResult when XML parsing succeeded, parsed JSON when JSON parsing succeeded, or null when no supported body was parsed |
| `getDescription` | `String getDescription()` | Return a diagnostic string containing the transport addresses and encoded response content. Returns: response summary string |
| `getHeaders` | `Map getHeaders()` | Return the response headers, lazily parsing them when needed. Returns: case-insensitive map with open HTTP header-name keys and String values; this implementation returns an empty  |
| `HubResponse` | `HubResponse(String mac, String ip, String port, String b64Headers, String b64Body, CaseInsensitiveMap headers = null)` | Create a response and parse its body when its content type is XML or JSON. Parameters: mac - device MAC address from the transport ip - device IP address from the transport port -  |

## Properties

| Name | Type | Description |
|---|---|---|
| `error` | `String` | — |
| `hubId` | `String` | — |
| `json` | `Object` | — |
| `status` | `Integer` | — |
| `xml` | `GPathResult` | — |
