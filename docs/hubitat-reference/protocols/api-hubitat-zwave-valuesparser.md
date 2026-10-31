# ValuesParser

- **ID:** `api-hubitat-zwave-valuesparser`
- **Section:** Protocols
- **Class:** `hubitat.zwave.ValuesParser`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `parse` | `Map<String, Object> parse(Integer nodeId, Map<String, Object> args, Map<Integer, Integer> versions)` | Collects value updates and returns a command when its expected values are available. Parameters: nodeId - node identifier used to group updates args - update map. Required keys are |
| `parseNotification` | `Map<String, Object> parseNotification(Map<String, Object> event)` | Converts a Z-Wave notification event into a command-value map. Parameters: event - event map with ccId, endpointIndex, and args Returns: null for command class 0x73; otherwise a ma |
| `parsePropertyKey` | `void parsePropertyKey(Map<String, Object> event)` | Stores command-class-specific metadata under its node, endpoint, class, and property key. Parameters: event - value update with nodeId and nested args.metadata.ccSpecific; args als |
| `propertyKeyGen` | `String propertyKeyGen(Integer nodeId, Integer endpoint, Integer commandClass, Object propertyKey)` | Builds the cache key used to find command-class-specific property metadata. Parameters: nodeId - node identifier encoded as two hexadecimal bytes. endpoint - endpoint encoded as on |

## Properties

| Name | Type | Description |
|---|---|---|
| `accumulatedValues` | `Map<Integer, Map<Integer, Map<Integer, Map<String, Map<String, Object>>>>>` | Accumulates property updates until they match the command-class version table. Outer key: Integer node identifier. Next key: Integer endpoint, with absent endpoints grouped under 0 |
| `ccSpecificStore` | `Map<String, Map<String, Object>>` | Stores command-class-specific metadata by the key produced by propertyKeyGen(Integer, Integer, Integer, Object). Key: String node, endpoint, command-class, and property-key tuple.  |
| `expectedValues` | `Map<Integer, Map<Integer, List<Map<String, Object>>>>` | Lists the property names required to form commands for each registered command-class version. Outer key: Integer command-class identifier. Next key: Integer command-class version.  |
