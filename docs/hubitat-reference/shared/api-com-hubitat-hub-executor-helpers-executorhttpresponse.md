# Executor HTTP response

- **ID:** `api-com-hubitat-hub-executor-helpers-executorhttpresponse`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.helpers.ExecutorHttpResponse`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `containsHeader` | `boolean containsHeader(String name)` | Check whether a response header is present. Parameters: name - header name Returns: true when present |
| `getAllHeaders` | `Header getAllHeaders()` | Return all response headers in wire order. Returns: all headers |
| `getCookies` | `Map getCookies()` | Return response cookies parsed from Set-Cookie headers. Returns: map with these keys: Each map key is a cookie name (String). Each map value contains only value (String cookie valu |
| `getFirstHeader` | `Header getFirstHeader(String name)` | Return the first header with the supplied name. Parameters: name - header name Returns: first matching header, or null |
| `getHeaders` | `Header getHeaders(String name)` | Return all headers with the supplied name. Parameters: name - header name Returns: matching headers |
| `getJSON` | `Object getJSON()` | Deprecated: use getJson() Returns: parsed JSON response data |
| `getJson` | `Object getJson()` | Return JSON response data using the legacy response.json/getJson() shape. Returns: parsed JSON response data |
| `getLastHeader` | `Header getLastHeader(String name)` | Return the last header with the supplied name. Parameters: name - header name Returns: last matching header, or null |
| `getResponseData` | `Object getResponseData()` | Return the parsed response body using HTTPBuilder's legacy backing-field name. Returns: the same parsed or raw response body exposed by data |
| `getStatusLine` | `ExecutorHttpResponse.SimpleStatusLine getStatusLine()` | Return a small status-line adapter for legacy code that reads response.statusLine. Returns: status-line adapter |
| `headerIterator` | `Iterator<Header> headerIterator()` | Iterate over all response headers. Returns: header iterator |
| `headerIterator` | `Iterator<Header> headerIterator(String name)` | Iterate over response headers with the supplied name. Parameters: name - header name Returns: header iterator |
| `isSuccess` | `boolean isSuccess()` | Determine whether the response status is in HTTPBuilder's success range. Returns: true when status is between 100 and 399 |
| `toString` | `String toString()` | Match Apache HttpClient 4's HttpResponseException message format. Returns: readable response status line |

## Properties

| Name | Type | Description |
|---|---|---|
| `contentType` | `String` | Effective response content type used for parser selection. |
| `context` | `Object` | Request execution context, when available. |
| `data` | `Object` | Parsed or raw response body data returned to the app/driver callback. |
| `entity` | `HttpEntity` | Buffered HttpClient 5 response entity, when available. |
| `headers` | `Object` | Map-compatible response header view: Each key is the first-seen spelling of a header name (String). Each value is a HeaderEntry with name (String) and value (String). Header names  |
| `locale` | `Locale` | Response locale, when available. |
| `protocolVersion` | `ProtocolVersion` | HTTP protocol version reported by the response, when available. |
| `reasonPhrase` | `String` | HTTP response reason phrase, when supplied by the server. |
| `status` | `int` | Numeric HTTP response status code. |
