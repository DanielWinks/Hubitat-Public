# HTTP requests

- **ID:** `http`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.BaseExecutor`

> Call these methods directly from an app or driver, for example httpGet(...) or asynchttpGet(...).

## Methods

| Name | Signature | Description |
|---|---|---|
| `asynchttpDelete` | `void asynchttpDelete(String callbackMethod = null, Map params, Map data = null)` | Schedules an asynchronous HTTP DELETE request; request bodies are removed before sending. Parameters: callbackMethod - Name of a two-argument callback method to invoke after the re |
| `asynchttpGet` | `void asynchttpGet(String callbackMethod = null, Map params, Map data = null)` | Schedules an asynchronous HTTP GET request. Parameters: callbackMethod - Name of a two-argument callback method to invoke after the request. The callback receives (AsyncResponse re |
| `asynchttpHead` | `void asynchttpHead(String callbackMethod = null, Map params, Map data = null)` | Schedules an asynchronous HTTP HEAD request. Parameters: callbackMethod - Name of a two-argument callback method to invoke after the request. The callback receives (AsyncResponse r |
| `asynchttpPatch` | `void asynchttpPatch(String callbackMethod = null, Map params, Map data = null)` | Schedules an asynchronous HTTP PATCH request; Map and List bodies are serialized to JSON. Parameters: callbackMethod - Name of a two-argument callback method to invoke after the re |
| `asynchttpPost` | `void asynchttpPost(String callbackMethod = null, Map params, Map data = null)` | Schedules an asynchronous HTTP POST request; Map and List bodies are serialized to JSON. Parameters: callbackMethod - Name of a two-argument callback method to invoke after the req |
| `asynchttpPut` | `void asynchttpPut(String callbackMethod = null, Map params, Map data = null)` | Schedules an asynchronous HTTP PUT request; Map and List bodies are serialized to JSON. Parameters: callbackMethod - Name of a two-argument callback method to invoke after the requ |
| `httpDelete` | `void httpDelete(Map params, Closure closure)` | Sends an HTTP DELETE request and passes its response to a callback. Request bodies are removed before sending. Parameters: params - Request settings with the common HTTP fields des |
| `httpGet` | `def httpGet(Map params, Closure closure)` | Sends an HTTP GET request using request settings and passes its response to a callback. Parameters: params - Request settings. uri: required String or URI request address. path: op |
| `httpGet` | `def httpGet(String uri, Closure closure)` | Sends an HTTP GET request and passes its response to a callback. Parameters: uri - Absolute request URI. closure - One-argument closure called on the calling thread with an Executo |
| `httpPatch` | `void httpPatch(Map params, Closure closure)` | Sends an HTTP PATCH request and passes its response to a callback. Parameters: params - Request settings with the common HTTP fields described by httpGet(Map, Closure). closure - O |
| `httpPatch` | `void httpPatch(String uri, String body, Closure closure)` | Sends a URL-encoded HTTP PATCH request and passes its response to a callback. Parameters: uri - Absolute request URI. body - Request body string. closure - One-argument closure cal |
| `httpPost` | `void httpPost(Map params, Closure closure)` | Sends an HTTP POST request and passes its response to a callback. Parameters: params - Request settings; supports uri, path, query, queryString, params, headers, body, requestConte |
| `httpPost` | `void httpPost(String uri, String body, Closure closure)` | Sends a URL-encoded HTTP POST request and passes its response to a callback. Parameters: uri - Absolute request URI. body - Request body string. closure - One-argument closure call |
| `httpPostJson` | `void httpPostJson(Map params, Closure closure)` | Sends a JSON HTTP POST request using request settings. Parameters: params - Request settings with the common HTTP fields described by httpGet(Map, Closure); the body may be a Strin |
| `httpPostJson` | `void httpPostJson(String uri, Map body, Closure closure)` | Sends a JSON HTTP POST request with a map body. Parameters: uri - Absolute request URI. body - Map serialized to JSON text. closure - One-argument closure called synchronously with |
| `httpPostJson` | `void httpPostJson(String uri, String body, Closure closure)` | Sends a JSON HTTP POST request with a string body. Parameters: uri - Absolute request URI. body - JSON request body text. closure - One-argument closure called synchronously with a |
| `httpPut` | `void httpPut(Map params, Closure closure)` | Sends an HTTP PUT request and passes its response to a callback. Parameters: params - Request settings with the common HTTP fields described by httpGet(Map, Closure). closure - One |
| `httpPut` | `void httpPut(String uri, String body, Closure closure)` | Sends a URL-encoded HTTP PUT request and passes its response to a callback. Parameters: uri - Absolute request URI. body - Request body string. closure - One-argument closure calle |
| `httpPutJson` | `void httpPutJson(Map params, Closure closure)` | Sends a JSON HTTP PUT request using request settings. Parameters: params - Request settings with the common HTTP fields described by httpGet(Map, Closure); Map and List body values |
| `httpPutJson` | `void httpPutJson(String uri, Map body, Closure closure)` | Sends a JSON HTTP PUT request with a map body. Parameters: uri - Absolute request URI. body - Map serialized to JSON text. closure - One-argument closure called synchronously with  |
| `httpPutJson` | `void httpPutJson(String uri, String body, Closure closure)` | Sends a JSON HTTP PUT request with a string body. Parameters: uri - Absolute request URI. body - JSON request body text. closure - One-argument closure called synchronously with an |
