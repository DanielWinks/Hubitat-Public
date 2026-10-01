# Command

- **ID:** `api-com-hubitat-hub-domain-command`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Command`

> The hub returns or binds this object to app or driver code; use the documented members on the object you receive.

## Methods

| Name | Signature | Description |
|---|---|---|
| `customVersion` | `Command customVersion(Command c1, Command c2)` | Select parameter definitions from the custom or capability command. throws: IllegalArgumentException when command names differ Parameters: c1 - installed command definition c2 - cu |
| `equals` | `boolean equals(Object obj)` | Compare command name, argument types, and parameter metadata without depending on parameter order. Parameters: obj - object to compare Returns: true when the command definitions ma |
| `fromJson` | `Command fromJson(Map parsedJson)` | Create a command by copying matching properties from parsed JSON. Parameters: parsedJson - map of command property names to values Returns: populated command, or null when input is |
| `getArguments` | `List<String> getArguments()` | Return argument types, deriving them from parameters when needed. Returns: argument type names, or null when no arguments or parameters are set |
| `getParameters` | `List<Map> getParameters()` | Return parameter records, deriving type-only records from arguments when needed. Returns: parameter maps with optional name, type, defaultValue, constraints, description, and requi |
| `hashCode` | `int hashCode()` | Returns: hash code based on command name, argument types, and parameters |
| `merge` | `Command merge(Command c1, Command c2)` | Merge command parameters, keeping the first command's parameter for duplicate names. throws: IllegalArgumentException when command names differ Parameters: c1 - first command defin |
| `setArguments` | `void setArguments(List<String> args)` | Parameters: args - argument type names |
| `setParameters` | `void setParameters(List<Map> params)` | Set parameter records. Parameters: params - maps whose standard keys are name (String), type (String), defaultValue (Object), constraints (List), description (String), and required |
| `toMap` | `Map toMap(boolean full = false)` | Convert this command to its JSON-compatible map. id: nullable Long command id. name: nullable String command name. arguments: when full is true, a List of argument type strings; ot |
| `toString` | `String toString()` | Returns: command name |

## Properties

| Name | Type | Description |
|---|---|---|
| `capability` | `boolean` | — |
| `id` | `Long` | — |
| `name` | `String` | — |
| `relatedAttribute` | `String` | — |
| `version` | `Long` | — |
