# Concurrent expiring hash map

- **ID:** `api-com-hubitat-helper-concurrentexpiringhashmap`
- **Section:** Shared APIs
- **Class:** `com.hubitat.helper.ConcurrentExpiringHashMap`

> Use ConcurrentExpiringHashMap from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `clear` | `void clear()` | Remove all entries. |
| `ConcurrentExpiringHashMap` | `ConcurrentExpiringHashMap(int expiresInSeconds, boolean getExtendsExpiration)` | Create an empty map with the configured expiration policy. Parameters: expiresInSeconds - idle time before an entry expires, in seconds getExtendsExpiration - whether reads reset t |
| `containsKey` | `boolean containsKey(Object o)` | Parameters: o - key to look up Returns: true when the key is currently stored |
| `containsValue` | `boolean containsValue(Object o)` | Parameters: o - value to compare with stored values Returns: true when a stored value equals the supplied value |
| `entrySet` | `Set<Entry<K, V>> entrySet()` | Returns: snapshot set of key-value entries |
| `get` | `V get(Object o)` | Read a value and remove it if expired. Parameters: o - key to look up Returns: stored value, or null when absent or expired |
| `isEmpty` | `boolean isEmpty()` | Returns: true when the backing map contains no entries |
| `keySet` | `Set<K> keySet()` | Returns: live concurrent set of stored keys |
| `put` | `V put(K k, V v)` | Store a value and reset its expiration timer. Parameters: k - key to store v - value to store Returns: previous value, or null when the key had no entry |
| `putAll` | `void putAll(Map<? extends K, ? extends V> map)` | Store each supplied entry and reset each expiration timer. Parameters: map - keys and values to copy |
| `remove` | `V remove(Object o)` | Remove a key after expiring stale entries. Parameters: o - key to remove Returns: removed value, or null when the key had no entry |
| `size` | `int size()` | Returns: number of entries currently stored, including any not yet inspected for expiry |
| `values` | `Collection<V> values()` | Returns: snapshot list of stored values |
