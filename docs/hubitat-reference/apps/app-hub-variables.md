# Hub variables

- **ID:** `app-hub-variables`
- **Section:** Apps
- **Class:** `com.hubitat.hub.executor.AppExecutor`

> Use these methods directly in app code; the hub supplies this execution context.

## Inheritance

- Extends `com.hubitat.hub.executor.BaseExecutor`
- [HTTP requests](../shared/http.md)
- [HTTP requests](../shared/http.md)
- [Date and time](../shared/shared-date-time.md)
- [Hub files](../shared/shared-files.md)
- [Network utilities](../shared/shared-network.md)
- [Scheduling](../shared/shared-scheduling.md)
- [Shared utilities](../shared/shared-utilities.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `addInUseGlobalVar` | `Boolean addInUseGlobalVar(List<String> names)` | Marks one global variable as in use by this app. Parameters: names - global variables to mark as used by this app Returns: the result of the final DAO add operation; an empty input |
| `addInUseGlobalVar` | `Boolean addInUseGlobalVar(String name)` | Marks one global variable as in use by this app. Parameters: name - global variable to mark as used by this app Returns: the DAO result indicating whether this app’s in-use referen |
| `addValueToGlobalVar` | `Boolean addValueToGlobalVar(String name, Object value, Boolean sendConnectorEvent = true)` | Adds a numeric value to an unlinked integer or decimal global variable and optionally emits a connector event. Parameters: name - global variable to update value - numeric amount t |
| `getAllGlobalVars` | `Map getAllGlobalVars()` | Returns all global-variable records keyed by variable name. Returns: a map keyed by variable name (String); each value is a nested record map containing: type: String variable type |
| `getGlobalVar` | `GlobalVariable getGlobalVar(String name)` | Looks up a global variable by name. Parameters: name - global variable name to look up Returns: the GlobalVariable with this name, or null when no variable exists. |
| `getGlobalVarsByType` | `Map getGlobalVarsByType(String type)` | Returns global-variable records keyed by variable name when the requested type is supported. Parameters: type - global variable type filter; supported values are integer, bigdecima |
| `removeAllInUseGlobalVar` | `Boolean removeAllInUseGlobalVar()` | Removes this app’s in-use reference from every global variable. Returns: the result of the last in-use reference removal while visiting all global variables; true when there are no |
| `removeInUseGlobalVar` | `Boolean removeInUseGlobalVar(String name)` | Removes this app’s in-use reference for one global variable. Parameters: name - global variable to unmark for this app Returns: the DAO result indicating whether this app’s in-use  |
