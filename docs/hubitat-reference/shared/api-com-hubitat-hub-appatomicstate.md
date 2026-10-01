# App atomic state

- **ID:** `api-com-hubitat-hub-appatomicstate`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.AppAtomicState`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `clear` | `void clear()` | Remove all app-state entries and persist the empty state immediately. |
| `containsKey` | `boolean containsKey(Object key)` | Parameters: key - state key Returns: true when the latest saved state contains this key |
| `containsValue` | `boolean containsValue(Object value)` | Parameters: value - state value Returns: true when the latest saved state contains this value |
| `entrySet` | `Set<Map.Entry> entrySet()` | Returns: set of key/value entries in the latest saved app state |
| `get` | `Object get(Object key)` | Parameters: key - state key Returns: value stored for the key, or null when absent |
| `getLocalStateVersion` | `long getLocalStateVersion()` | Returns: last state version loaded into the local cache, or -1 before the first load |
| `isEmpty` | `boolean isEmpty()` | Returns: true when the latest saved app state has no keys |
| `keySet` | `Set keySet()` | Returns: set of keys in the latest saved app state |
| `put` | `Object put(Object key, Object value)` | Persist one app-state entry immediately. Parameters: key - key to store value - value to store Returns: previous value for the key, or null when there was none |
| `putAll` | `void putAll(Map m)` | Persist every entry from another map immediately. Parameters: m - map of state entries with this schema: Each key is an Object, usually an app-defined String. Each value is an Obje |
| `remove` | `Object remove(Object key)` | Remove one app-state entry and persist the change immediately. Parameters: key - key to remove Returns: previous value for the key, or null when there was none |
| `size` | `int size()` | Returns: number of keys in the latest saved app state |
| `updateMapValue` | `void updateMapValue(Object stateKey, Object key, Object value)` | Update one entry inside a map-valued app-state entry and persist that nested map. Parameters: stateKey - app-state key whose value is the nested map key - key to set inside the nes |
| `values` | `Collection values()` | Returns: collection of values in the latest saved app state |
