# Capability

- **ID:** `api-com-hubitat-hub-domain-capability`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Capability`

> Use Capability from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `Capability` | `Capability()` | Create an empty capability definition. |
| `Capability` | `Capability(Long id, String name)` | Create a capability with its database id and name. Parameters: id - capability database id name - capability name |
| `toMap` | `Map toMap(boolean full = false)` | Convert this capability to the map shape used by capability metadata. Parameters: full - true to include attribute and command definitions Returns: map with these keys: id: Long ca |
| `toString` | `String toString()` | Returns: the capability name |

## Properties

| Name | Type | Description |
|---|---|---|
| `attributes` | `List<Attribute>` | — |
| `commands` | `List<Command>` | — |
| `id` | `Long` | — |
| `name` | `String` | — |
| `reference` | `String` | — |
| `version` | `Long` | — |
