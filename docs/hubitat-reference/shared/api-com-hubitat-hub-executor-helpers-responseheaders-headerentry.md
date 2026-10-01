# Response headers / Header entry

- **ID:** `api-com-hubitat-hub-executor-helpers-responseheaders-headerentry`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.helpers.ResponseHeaders$HeaderEntry`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `contains` | `boolean contains(CharSequence text)` | Parameters: text - text to search for in the header value Returns: true when the value contains the text |
| `getKey` | `String getKey()` | Returns: header name, also used as the Map.Entry key |
| `getValue` | `Object getValue()` | Returns: header value, also used as the Map.Entry value |
| `setValue` | `Object setValue(Object value)` | Response header entries cannot be changed. throws: UnsupportedOperationException always, because response headers are immutable Parameters: value - replacement value, which is not  |
| `toString` | `String toString()` | Returns: header formatted as name: value |

## Properties

| Name | Type | Description |
|---|---|---|
| `name` | `String` | Header name using its original spelling. |
| `value` | `String` | Response header value. |
