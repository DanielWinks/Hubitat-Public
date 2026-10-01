# Async response

- **ID:** `api-hubitat-scheduling-asyncresponse`
- **Section:** Shared APIs
- **Class:** `hubitat.scheduling.AsyncResponse`

> Use AsyncResponse from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `AsyncResponse` | `AsyncResponse(ExecutorHttpResponse httpResponse)` | Build an async response from the HttpClient 5 executor response wrapper. Parameters: httpResponse - response wrapper produced by ExecutorHttpClient5 |
| `AsyncResponse` | `AsyncResponse(int status, String message)` | Build an async response from an explicit status and message. Parameters: status - HTTP-like status code message - error message used for non-success statuses |
| `getData` | `String getData()` | Return successful response data. throws: Exception when this response has an error Returns: response body text, or null when absent |
| `getErrorData` | `String getErrorData()` | Return response data for an error response. throws: Exception when this response has no error Returns: response body text, or null when absent |
| `getErrorJSON` | `Object getErrorJSON()` | Deprecated: use getErrorJson() |
| `getErrorJson` | `Object getErrorJson()` | Return the JSON serialization of error response data. throws: IllegalArgumentException when this response has no error Returns: JSON text generated from the error data |
| `getErrorMessage` | `String getErrorMessage()` | Return the HTTP reason phrase for an error response. throws: Exception when this response has no error Returns: error reason phrase |
| `getErrorXML` | `GPathResult getErrorXML()` | Deprecated use getErrorXml() |
| `getErrorXml` | `GPathResult getErrorXml()` | Parse and return error response data as XML. throws: IllegalArgumentException when this response has no error Returns: parsed error XML document |
| `getHeaders` | `Object getHeaders()` | Return response headers supplied by the executor. When constructed from ExecutorHttpResponse, the value is a map keyed by each header's first-seen spelling (String). Each value is  |
| `getJson` | `Object getJson()` | Parse and return successful response data as JSON. throws: IllegalArgumentException when this response has an error Returns: parsed JSON value, which may be a scalar, list, or an o |
| `getStatus` | `int getStatus()` | Returns: numeric HTTP status code |
| `getWarningMessages` | `List<String> getWarningMessages()` | Returns: empty list; warning messages are not currently populated |
| `getXML` | `GPathResult getXML()` | Deprecated: use getXml() |
| `getXml` | `GPathResult getXml()` | Parse and return successful response data as XML. throws: IllegalArgumentException when this response has an error Returns: parsed XML document |
| `hasError` | `boolean hasError()` | Returns: true when the response status indicates failure |
