# Installed app wrapper

- **ID:** `installed-app`
- **Section:** Apps
- **Class:** `com.hubitat.app.InstalledAppWrapper`

> Use app to read or update the current app, for example app.updateSetting(...).

## Methods

| Name | Signature | Description |
|---|---|---|
| `clearSetting` | `void clearSetting(String name)` | Clears and saves a setting value when the setting exists and currently has a value. Parameters: name - Setting name whose value to clear. |
| `getAppTypeId` | `Long getAppTypeId()` | Returns: app type database id |
| `getId` | `Long getId()` | Returns: installed app database id |
| `getInstallationState` | `String getInstallationState()` | Returns: COMPLETE when installed, otherwise INCOMPLETE |
| `getLabel` | `String getLabel()` | Returns: app label |
| `getName` | `String getName()` | Returns: app name |
| `getParentAppId` | `Long getParentAppId()` | Returns: parent app id, or null for a top-level app |
| `getSetting` | `Object getSetting(String name)` | Returns an app setting converted to its script value. Parameters: name - Setting name to query. Returns: Converted setting value, raw stored value when conversion fails, or null wh |
| `getSettingType` | `String getSettingType(String name)` | Returns the declared type of an app setting. Parameters: name - Setting name to query. Returns: Setting type string, or null when the name is null or no setting exists. |
| `getState` | `Map getState()` | Load app state on first access. Returns: mutable app state map with these properties: Keys are app-defined Objects, usually Strings; there is no fixed key set. Values are app-defin |
| `getSubscriptions` | `List<EventSubscriptionWrapper> getSubscriptions()` | Returns: event subscriptions registered by this app |
| `removeSetting` | `void removeSetting(String name)` | Deletes the app setting and removes its current script binding. Parameters: name - Setting name to remove. |
| `updateLabel` | `void updateLabel(String label)` | Change the app label and persist it. Parameters: label - new app label; text longer than 255 characters is truncated to 255 |
| `updateSetting` | `void updateSetting(String name, Boolean value)` | Store a Boolean setting value, using setting type bool. Example: app.updateSetting("notificationsEnabled", true). Parameters: name - name of the app setting to update value - true  |
| `updateSetting` | `void updateSetting(String name, Date value)` | Store a time setting value, using setting type time. The platform normalizes the supplied date using its time-setting rules. Example: app.updateSetting("quietHoursStart", new Date( |
| `updateSetting` | `void updateSetting(String name, Double value)` | Store a decimal setting value, using setting type decimal. Example: app.updateSetting("temperatureOffset", 0.5d). Parameters: name - name of the app setting to update value - decim |
| `updateSetting` | `void updateSetting(String name, List value)` | Store one or more enum selections, using setting type enum. This overload accepts a List for a single selection or multiple selections. The values should match the input's configur |
| `updateSetting` | `void updateSetting(String name, Long value)` | Store a whole-number setting value, using setting type number. Example: app.updateSetting("repeatCount", 3L). Parameters: name - name of the app setting to update value - whole-num |
| `updateSetting` | `void updateSetting(String name, Map options)` | Update an app setting by providing its declared type and value. Use this overload when you need to specify the type explicitly, including password, date, enum, mode, and device cap |
| `updateSetting` | `void updateSetting(String name, String value)` | Store a text setting value, using setting type text. Example: app.updateSetting("displayName", "Kitchen lights"). Parameters: name - name of the app setting to update value - text  |

## Properties

| Name | Type | Description |
|---|---|---|
| `atomicState` | `AppAtomicState` | — |
| `dashboardDeviceCount` | `int` | — |
| `dashboardVersion` | `String` | — |
| `state` | `Map` | App-persisted state map with no fixed key set: Each key is an app-defined Object, usually a String name. Each value is an app-defined Object; nested maps have this same open schema |
