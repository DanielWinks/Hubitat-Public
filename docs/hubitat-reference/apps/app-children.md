# Child apps and devices

- **ID:** `app-children`
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
| `addChildApp` | `InstalledAppWrapper addChildApp(String namespace, String name, String label, Map properties = null)` | Creates a child app under the current installed app. Parameters: namespace - namespace containing the child app type name - child app type name label - display label properties - o |
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String namespace, String typeName, Map properties)` | Creates a child device owned by this app, using a generated or supplied network id and optional properties. Parameters: namespace - namespace containing the app or device type type |
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String namespace, String typeName, String deviceNetworkId)` | Creates a child device owned by this app, using a generated or supplied network id and optional properties. Parameters: namespace - namespace containing the app or device type type |
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String namespace, String typeName, String deviceNetworkId, Long hubId)` | Creates a child device owned by this app, using a generated or supplied network id and optional properties. Parameters: namespace - namespace containing the app or device type type |
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String namespace, String typeName, String deviceNetworkId, Long hubId, Map properties)` | Creates a child device owned by this app, using a generated or supplied network id and optional properties. Parameters: namespace - namespace containing the app or device type type |
| `addChildDevice` | `ChildDeviceWrapper addChildDevice(String namespace, String typeName, String deviceNetworkId, Map properties)` | Creates a child device owned by this app, using a generated or supplied network id and optional properties. Parameters: namespace - namespace containing the app or device type type |
| `deleteChildApp` | `void deleteChildApp(Long childAppId)` | Deletes the specified child app of the current installed app. Parameters: childAppId - child app id |
| `deleteChildDevice` | `void deleteChildDevice(String deviceNetworkId)` | Deletes the child device with the supplied network id when it is owned by this app. Parameters: deviceNetworkId - device network id |
| `getAllChildApps` | `def getAllChildApps()` | Returns the child app wrappers for this installed app. Returns: the result of getChildApps(): cached child-app wrappers, or null when the current app has no child-app list loaded. |
| `getAllChildDevices` | `List<ChildDeviceWrapper> getAllChildDevices()` | Returns the child device wrappers from getChildDevices(). Returns: the List returned by getChildDevices(). |
| `getChildAppById` | `def getChildAppById(Long childAppId)` | Loads a child app owned by this app and returns null when its id is not found. Parameters: childAppId - child app id Returns: the child app wrapper for this app and id, or null whe |
| `getChildAppByLabel` | `def getChildAppByLabel(String childAppLabel)` | Loads a child app owned by this app and returns null when its label is not found. Parameters: childAppLabel - child app label to match Returns: the child app wrapper for this app a |
| `getChildApps` | `def getChildApps()` | Loads and caches wrappers for this app’s child apps. Returns: the cached List for this app’s child apps, or null when this executor has no app. |
| `getChildAppsByParentId` | `List<InstalledAppWrapper> getChildAppsByParentId(Long parentAppId)` | Returns wrappers for child apps of the supplied installed parent app. Parameters: parentAppId - installed parent app id Returns: a List for the supplied parent id; no children prod |
| `getChildDevice` | `ChildDeviceWrapper getChildDevice(String deviceNetworkId)` | Finds a child device owned by this app using its device network id. Parameters: deviceNetworkId - device network id Returns: the child device wrapper for this app and the supplied  |
| `getChildDevices` | `List<ChildDeviceWrapper> getChildDevices()` | Returns wrappers for child devices owned by this app. Returns: a List for this app’s child devices; no children produce an empty list. |
| `getParent` | `InstalledAppWrapper getParent()` | Loads and caches the installed parent app wrapper; returns null when there is no parent or it cannot be loaded. Returns: the parent installed app wrapper, or null when no parent ap |
| `hasParent` | `boolean hasParent()` | Checks whether this executor already has a cached parent app wrapper. Returns: true when a parent app wrapper has been cached, otherwise false. |
