# Driver definition

- **ID:** `driver-definition`
- **Section:** Drivers
- **Class:** `com.hubitat.hub.executor.DeviceExecutor`

> Use these methods directly in driver code; the hub supplies this execution context.

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
| `attribute` | `void attribute(String attributeName, String attributeType)` | Adds an attribute with a declared type. Parameters: attributeName - Attribute name exposed by this driver. attributeType - Attribute value type. |
| `attribute` | `void attribute(String attributeName, String attributeType, List possibleValues)` | Adds an attribute with a declared type and enumerated values. Parameters: attributeName - Attribute name exposed by this driver. attributeType - Attribute value type. possibleValue |
| `attributeState` | `void attributeState(Map options)` | Adds a state map to the current tile attribute. Parameters: options - State fields stored unchanged in the states list; accepted fields are interpreted by the dashboard renderer. |
| `attributeState` | `void attributeState(Map options, String name)` | Adds a named state map to the current tile attribute. Parameters: options - State fields stored in the states list; the name field is set to the supplied name. name - State name. |
| `capability` | `void capability(String capabilityName)` | Adds a capability when the capability exists on the hub. throws: CapabilityNotFoundException when the named capability is unavailable. Parameters: capabilityName - Capability name; |
| `command` | `void command(String commandName)` | Adds a command with no parameters. Parameters: commandName - Command name exposed by this driver. |
| `command` | `void command(String commandName, List parameterTypes)` | Adds a command and its parameter types. Parameters: commandName - Command name exposed by this driver. parameterTypes - Parameter types in command argument order. |
| `controlTile` | `void controlTile(Map options, String tileName, String attributeName, String controlType, Closure closure)` | Defines a control tile and runs its nested definition closure. Parameters: options - Tile fields copied over the default tile fields; arbitrary keys and values are preserved. tileN |
| `definition` | `void definition(Map definitionData, Closure closure)` | Stores the device definition data and runs its nested declaration closure. Parameters: definitionData - Definition map passed to the device metadata parser; entries are caller-defi |
| `details` | `void details(List<String> tileDefinitions)` | Adds tile names to the dashboard details list. Parameters: tileDefinitions - Tile names already declared in metadata. |
| `details` | `void details(String tileDefinition)` | Adds one tile name to the dashboard details list. Parameters: tileDefinition - Tile name already declared in metadata. |
| `details` | `void details(String... tileDefinitions)` | Adds tile names to the dashboard details list. Parameters: tileDefinitions - Tile names already declared in metadata. |
| `fingerprint` | `void fingerprint(Map fingerprintData)` | Adds a device fingerprint definition. Parameters: fingerprintData - Fingerprint fields passed unchanged to the metadata parser. |
| `graphTile` | `void graphTile(Map options)` | Graph tile placeholder; currently performs no operation. Parameters: options - Graph tile fields; currently ignored. |
| `image` | `void image(Map options)` | Accepts an image declaration; this executor does not store or process it. Parameters: options - Image fields, currently ignored. |
| `input` | `def input(Map options)` | Adds an input using all fields supplied in one map. Parameters: options - Input definition fields. Defaults are description "Click to set", multiple false, required false, and titl |
| `input` | `def input(Map options, String name, String type)` | Adds an input using default fields, named arguments, and extra definition fields. Parameters: options - Optional input fields; see input(Map) for defaults and accepted common keys. |
| `input` | `def input(String name, String type)` | Adds an input with a name and type. Parameters: name - Input setting name. type - Input type such as text, number, enum, or capability. Returns: Boolean result of adding the normal |
| `lanFingerprint` | `void lanFingerprint(Map fingerprintData)` | Adds a LAN device fingerprint definition. Parameters: fingerprintData - LAN fingerprint fields passed unchanged to the metadata parser. |
| `library` | `void library(Map parameters)` | Accepts a driver library declaration; the current executor does not process it. Parameters: parameters - Library declaration fields, accepted unchanged and ignored by this implemen |
| `main` | `void main(List<String> tileNames)` | Adds tile names to the dashboard main tile list. Parameters: tileNames - Tile names already declared in metadata. |
| `main` | `void main(String tileName)` | Adds one tile name to the dashboard main tile list. Parameters: tileName - Tile name already declared in metadata. |
| `metadata` | `void metadata(Closure closure)` | Builds this driver's metadata by running the metadata closure. Parameters: closure - Closure that declares metadata, preferences, and tiles. |
| `multiAttributeTile` | `void multiAttributeTile(Map options, Closure closure)` | Defines a 6-by-4 multi-attribute tile and runs its nested closure. Parameters: options - Tile fields. name (String) is required; type (String) is removed and added to the tile type |
| `preferences` | `void preferences(Closure closure)` | Runs the driver preferences declaration closure. Parameters: closure - Closure that declares driver preferences. |
| `section` | `def section(Closure cls)` | Adds an untitled preferences section. Parameters: cls - Closure that declares inputs for the section. Returns: Null after the section is appended to the preferences map. |
| `section` | `def section(String title, Closure cls)` | Adds a titled preferences section. Parameters: title - Section title; null or empty uses the default title. cls - Closure that declares inputs for the section. Returns: Null after  |
| `simulator` | `void simulator(Closure closure)` | Accepts a simulator declaration; this executor ignores the closure. Parameters: closure - Simulator declaration closure, not executed by this implementation. |
| `standardTile` | `void standardTile(Map options, String tileName, String attributeName)` | Defines a standard tile with options and no nested closure. Parameters: options - Tile fields copied over the default tile fields; see standardTile(Map, String, String, Closure). t |
| `standardTile` | `void standardTile(Map options, String tileName, String attributeName, Closure closure)` | Defines a standard dashboard tile and runs its nested definition closure. Parameters: options - Optional tile fields copied over the default tile fields. Defaults are width 1, heig |
| `standardTile` | `void standardTile(String tileName, String attributeName)` | Defines a standard tile with default options and no nested closure. Parameters: tileName - Tile name used by main and details. attributeName - Device attribute displayed by the til |
| `standardTile` | `void standardTile(String tileName, String attributeName, Closure closure)` | Defines a standard tile with default options and a nested closure. Parameters: tileName - Tile name used by main and details. attributeName - Device attribute displayed by the tile |
| `state` | `void state(Map options)` | Adds a tile state using its name field when present. Parameters: options - State fields copied into a new state map; name (String) is removed and used as the state name. |
| `state` | `void state(Map options, String stateName)` | Adds a named state to the current tile. Parameters: options - State fields copied into a new state map; any name entry is replaced by stateName. stateName - State name. |
| `tileAttribute` | `void tileAttribute(Map options, String attributeName)` | Adds a tile attribute with extra fields and no nested closure. Parameters: options - Optional fields copied to the attribute configuration; arbitrary keys and values are preserved. |
| `tileAttribute` | `void tileAttribute(Map options, String attributeName, Closure closure)` | Adds an attribute definition to the current tile. Parameters: options - Optional attribute fields copied to the definition; arbitrary keys and values are preserved. attributeName - |
| `tileAttribute` | `void tileAttribute(String attributeName)` | Adds a tile attribute with no extra fields or nested closure. Parameters: attributeName - Name of the device attribute to display. See Also: tileAttribute(Map, String, Closure) |
| `tileAttribute` | `void tileAttribute(String attributeName, Closure closure)` | Adds a tile attribute with a nested state-definition closure. Parameters: attributeName - Name of the device attribute to display. closure - Closure that adds possible attribute st |
| `tiles` | `void tiles(Closure closure)` | Adds tile definitions using a closure. Parameters: closure - Closure that adds tile definitions. |
| `tiles` | `void tiles(Map vals, Closure closure)` | Adds tile definitions using a tile metadata map and closure. Parameters: vals - Tile metadata entries copied into the tile configuration; each supplied key overrides the existing v |
| `valueTile` | `void valueTile(Map options, String tileName, String attributeName)` | Defines a value tile with options and no nested closure. Parameters: options - Tile fields copied over the default tile fields; see valueTile(Map, String, String, Closure). tileNam |
| `valueTile` | `void valueTile(Map options, String tileName, String attributeName, Closure closure)` | Defines a value dashboard tile and runs its nested definition closure. Parameters: options - Optional tile fields copied over the default tile fields. Defaults are width 1, height  |
| `valueTile` | `void valueTile(String tileName, String attributeName)` | Defines a value tile with default options and no nested closure. Parameters: tileName - Tile name used by main and details. attributeName - Device attribute displayed by the tile.  |
| `valueTile` | `void valueTile(String tileName, String attributeName, Closure closure)` | Defines a value tile with default options and a nested closure. Parameters: tileName - Tile name used by main and details. attributeName - Device attribute displayed by the tile. c |
