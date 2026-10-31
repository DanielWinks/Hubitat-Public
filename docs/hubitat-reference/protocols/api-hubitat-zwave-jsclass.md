# JSClass

- **ID:** `api-hubitat-zwave-jsclass`
- **Section:** Protocols
- **Class:** `hubitat.zwave.JSClass`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `boolToInteger` | `Integer boolToInteger(Boolean value)` | Converts a Boolean to the byte value used by Z-Wave commands. Parameters: value - Boolean value to convert. Returns: 0xFF for true or 0x00 for false. |
| `fillBitmask` | `Short fillBitmask(Boolean bool1 = false, Boolean bool2 = false, Boolean bool3 = false, Boolean bool4 = false, Boolean bool5 = false, Boolean bool6 = false, Boolean bool7 = false, Boolean bool8 = false)` | Packs eight Boolean flags into the corresponding bits of a Short. Parameters: bool1 - flag for bit 0; defaults to false. bool2 - flag for bit 1; defaults to false. bool3 - flag for |
| `getCMD` | `String getCMD()` | Returns this command implementation's hexadecimal frame identifier. Returns: command identifier |
| `getJsonCMD` | `String getJsonCMD(Integer commandClass, String methodName, List<Object> args = [], Integer responseCmd = null, Map<String, Object> supervisionResponse = null)` | Builds JSON for invoking a Z-Wave command-class method. Parameters: commandClass - command-class identifier. methodName - command method name. args - method arguments; defaults to  |
| `getJsonSetCMD` | `String getJsonSetCMD(Integer commandClass, Object value, Object property, Map<String, Object> options = null, Object propertyKey = null, Map<String, Object> supervisionResponse = null)` | Builds JSON for setting a Z-Wave command-class value. Parameters: commandClass - command-class identifier. value - value to assign. property - property name to set. options - optio |
| `getMCEncap` | `String getMCEncap(Short ep)` | Builds the Multi Channel encapsulation header when this command has an endpoint. Parameters: ep - endpoint value written to the returned header. Returns: hexadecimal encapsulation  |
| `parse` | `String parse(Map<String, Object> args)` | Parses command-specific values into the legacy Z-Wave command description. Parameters: args - command-specific value map consumed by the concrete command parser. Returns: formatted |
| `parseResponse` | `String parseResponse(Map<String, Object> response, Map<String, Object> request)` | Parses a command response for this command implementation. The base implementation has no response parser. Parameters: response - response map supplied by the command transport. re |
| `prepareDescription` | `String prepareDescription(List<Short> payload)` | Formats payload bytes as a Z-Wave command description, adding the instance endpoint header when needed. Parameters: payload - command payload bytes; when endpoint is zero, the firs |
| `toJson` | `String toJson(Map<String, Object> payload)` | Serializes a payload map as JSON text. Parameters: payload - payload map to serialize; keys and values are passed to the JSON serializer. Returns: JSON representation of the map. |

## Properties

| Name | Type | Description |
|---|---|---|
| `endpoint` | `Short` | — |
| `version` | `Short` | — |
