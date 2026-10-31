# Global variable

- **ID:** `api-com-hubitat-hub-domain-globalvariable`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.GlobalVariable`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getHubName` | `String getHubName()` | Returns: name of the first configured hub, or null when no hub is configured |
| `getValue` | `Object getValue()` | Returns: value converted from its stored text according to type, or null when unavailable |
| `isLinked` | `boolean isLinked()` | Returns: true when a Hub Mesh source hub id is present |
| `setValue` | `void setValue(Object val)` | Set the in-memory variable value after enforcing its text-length limit. throws: IllegalArgumentException when the text representation exceeds 1024 characters Parameters: val - valu |
| `toMap` | `Map toMap()` | Convert this variable to the global-variable map used by hub APIs. Returns: one-entry map whose key is this variable's name (String, possibly null) and whose value is a map with th |
| `toString` | `String toString()` | Returns: text containing the variable name, type, value, device id, and attribute |

## Properties

| Name | Type | Description |
|---|---|---|
| `attribute` | `String` | — |
| `deviceId` | `Long` | — |
| `hubMeshSourceHubId` | `String` | — |
| `hubMeshSourceVarName` | `String` | — |
| `meshEnabled` | `boolean` | — |
| `name` | `String` | — |
| `type` | `String` | — |
| `valueAsString` | `String` | — |
