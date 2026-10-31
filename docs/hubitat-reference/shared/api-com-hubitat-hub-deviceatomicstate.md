# Device atomic state

- **ID:** `api-com-hubitat-hub-deviceatomicstate`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.DeviceAtomicState`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `clear` | `void clear()` | Remove all device-state entries and persist the empty state immediately. |
| `containsKey` | `boolean containsKey(Object key)` | Parameters: key - state key Returns: true when the latest saved state contains this key |
| `containsValue` | `boolean containsValue(Object value)` | Parameters: value - state value Returns: true when the latest saved state contains this value |
| `entrySet` | `Set<Map.Entry> entrySet()` | Returns: set of key/value entries in the latest saved device state |
| `get` | `Object get(Object key)` | Parameters: key - state key Returns: value stored for the key, or null when absent |
| `isEmpty` | `boolean isEmpty()` | Returns: true when the latest saved device state has no keys |
| `keySet` | `Set keySet()` | Returns: set of keys in the latest saved device state |
| `loadState` | `void loadState()` | Refresh the local state map when the saved device-state version has changed. |
| `put` | `Object put(Object key, Object value)` | Persist one device-state entry immediately. Parameters: key - key to store value - value to store Returns: previous value for the key, or null when there was none |
| `putAll` | `void putAll(Map m)` | Persist every entry from another map immediately. Parameters: m - map of state entries with this schema: Each key is an Object, usually a driver-defined String. Each value is an Ob |
| `remove` | `Object remove(Object key)` | Remove one device-state entry and persist the change immediately. Parameters: key - key to remove Returns: previous value for the key, or null when there was none |
| `size` | `int size()` | Returns: number of keys in the latest saved device state |
| `values` | `Collection values()` | Returns: collection of values in the latest saved device state |
