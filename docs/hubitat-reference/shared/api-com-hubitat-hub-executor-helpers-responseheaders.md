# Response headers

- **ID:** `api-com-hubitat-hub-executor-helpers-responseheaders`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.helpers.ResponseHeaders`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `containsKey` | `boolean containsKey(Object key)` | Checks whether a response header has the supplied name. Parameters: key - header name; matching ignores case Returns: true when a header with that name exists |
| `each` | `void each(Closure closure)` | Visits every header in original wire order. Parameters: closure - action receiving either a HeaderEntry or its name and HeaderEntry, according to its arity |
| `entrySet` | `Set<Map.Entry<String, Object>> entrySet()` | Returns the unique Map view of response headers. Returns: entries keyed by each header's first-seen spelling; duplicate names use their last HeaderEntry value |
| `find` | `Object find(Closure closure)` | Finds the first matching entry in original wire order. Parameters: closure - predicate receiving either a HeaderEntry or its name and HeaderEntry, according to its arity Returns: f |
| `findAll` | `List findAll(Closure closure)` | Returns every matching entry in original wire order. Parameters: closure - predicate receiving either a HeaderEntry or its name and HeaderEntry, according to its arity Returns: lis |
| `get` | `Object get(Object key)` | Looks up a header by a case-insensitive name. Parameters: key - header name; null never matches Returns: matching HeaderEntry, or null when no header has that name |
| `getAt` | `Object getAt(Object key)` | Parameters: key - header name; matching ignores case Returns: matching HeaderEntry, or null when absent |
| `getAt` | `Object getAt(String key)` | Parameters: key - header name; matching ignores case Returns: matching HeaderEntry, or null when absent |
| `getProperty` | `Object getProperty(String name)` | Supports Groovy property access for response header values. Parameters: name - header name, or class/metaClass for the corresponding Groovy properties Returns: header value String, |
| `isEmpty` | `boolean isEmpty()` | Returns: true when the input contained no response headers |
| `iterator` | `Iterator<HeaderEntry> iterator()` | Returns: iterator over every HeaderEntry in original wire order, including duplicate names |
| `size` | `int size()` | Returns: number of distinct header names, compared without regard to case |
