# App utilities

- **ID:** `app-utilities`
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
| `apiServerUrl` | `String apiServerUrl(String url)` | Appends a path to the configured cloud API base URL. Parameters: url - path appended to the API base URL Returns: the cloud API base URL followed by / and the supplied path String. |
| `app` | `def app(Map options)` | Adds an app-selection item to the current section body. Parameters: options - values that override the default app-selector fields Returns: the Boolean result of adding the app-sel |
| `app` | `def app(Map options, String name, String namespace, String appName)` | Adds an app-selection item to the current section body. Parameters: options - values that override the default app-selector fields name - preference setting name for the app select |
| `app` | `def app(String name, String namespace, String appName)` | Adds an app-selection item to the current section body. Parameters: name - preference setting name for the app selector namespace - namespace containing the selected app type appNa |
| `appSetting` | `def appSetting(String setting)` | Logs that appSetting is not implemented; it does not return a setting value. Parameters: setting - app setting name; this method currently only logs that it is unimplemented Return |
| `buildDynamicUpdateClassForVariableName` | `String buildDynamicUpdateClassForVariableName(String varName)` | Builds the location-state update class key by hex-encoding the variable name bytes. Parameters: varName - variable name to encode Returns: a String formed from location-state-varia |
| `exitAwayMode` | `void exitAwayMode()` | Asks the location service to exit Away mode. |
| `fullApiServerUrl` | `String fullApiServerUrl(String url)` | Appends a path to this app’s cloud API base URL. Parameters: url - path appended to the API base URL Returns: this app’s cloud API base URL followed by / and the supplied path Stri |
| `fullLocalApiServerUrl` | `String fullLocalApiServerUrl(String url)` | Appends a path to this app’s local API base URL. Parameters: url - path appended to the API base URL Returns: this app’s local API base URL followed by / and the supplied path Stri |
| `getApiServerUrl` | `String getApiServerUrl()` | Returns the configured cloud API base URL, using the Hubitat cloud URL when unset. Returns: the cloud API base URL String, using https://cloud.hubitat.com/api when the property is  |
| `getAppByAppId` | `InstalledApp getAppByAppId(Long installedAppId)` | Loads the installed app record for the supplied id. Parameters: installedAppId - installed app id Returns: the InstalledApp record for the id, or null when it does not exist. |
| `getEasyDashboardDayNightTheme` | `String getEasyDashboardDayNightTheme()` | Returns the configured Easy Dashboard day/night theme. Returns: the DashboardService day/night theme String. |
| `getEasyDashboardReloadInSeconds` | `int getEasyDashboardReloadInSeconds()` | Returns seconds until the next sunrise, sunset, or midnight event, plus a 30-second buffer. Returns: the number of seconds to the next sunrise, sunset, or midnight event plus 30 se |
| `getFullApiServerUrl` | `String getFullApiServerUrl()` | Builds this app’s cloud API base URL from the cloud host, hub identifier, and installed app id. Returns: the cloud API URL String ending in /<hub id>/apps/<app id>. |
| `getFullLocalApiServerUrl` | `String getFullLocalApiServerUrl()` | Builds this app’s local API base URL using its installed app id. Returns: the local apps API URL String ending in /<app id>. |
| `getHubUID` | `String getHubUID()` | Returns the hub identifier configured for cloud API URLs. Returns: the configured cloud hub id String, or null when that property is unset. |
| `getInstalledAppIds` | `List<Long> getInstalledAppIds(String namespace, String name, String appTypeType = null)` | Finds app ids by namespace and name, optionally filtering by app type. Parameters: namespace - namespace containing the app or device type name - app type name appTypeType - option |
| `getInstalledBundlesList` | `List getInstalledBundlesList()` | Returns bundle id, name, and namespace records for installed bundles. Returns: a List of maps; each map contains: id: Long bundle id. name: String bundle name. namespace: String bu |
| `getLocalApiServerUrl` | `String getLocalApiServerUrl()` | Builds the local apps API base URL from the hub URL. Returns: the local hub apps API base URL String ending in /apps/api. |
| `getMdnsServiceTypes` | `List<Map> getMdnsServiceTypes(String type = null)` | Returns discovered mDNS service records, optionally filtered by service type. Parameters: type - service type filter; null includes all discovered types Returns: a List of maps cop |
| `getSettingType` | `String getSettingType(String settingName)` | Returns the type of a setting on the installed app, or null when this executor has no app. Parameters: settingName - app setting name Returns: the setting type String, or null when |
| `getState` | `Map getState()` | Returns the installed app state, or this executor’s local empty-state map while no app is installed. Returns: the state Map from the installed app wrapper, or the executor-local Ma |
| `getSwapWhitelistedParentIntegrationApps` | `List<String> getSwapWhitelistedParentIntegrationApps()` | Returns the app type names whose child devices may be considered during device-id swaps. Returns: a List of the five integration app type constants allowed by the device-swap logic |
| `getThirdPartyHubIPList` | `List getThirdPartyHubIPList()` | Loads the configured third-party hub IP allowlist from the database and caches it on this executor. Returns: a List of configured IP address values parsed from the database propert |
| `getWiFiNetworks` | `List<String> getWiFiNetworks()` | Returns an empty list; Wi-Fi network enumeration is not implemented here. Returns: an empty List; this executor does not enumerate Wi-Fi networks. |
| `gzipAndBase64` | `String gzipAndBase64(String s)` | Compresses UTF-8 text with GZIP and returns the compressed bytes as Base64 text. Parameters: s - UTF-8 text to compress or Base64 GZIP text to expand Returns: a Base64 String conta |
| `isAppInstalled` | `boolean isAppInstalled(String namespace, String name, String type = "ANY")` | Checks whether an app with the namespace and name is installed, optionally restricting its type. Parameters: namespace - namespace containing the app type name - app type name type |
| `isAppInstalledByLabel` | `boolean isAppInstalledByLabel(String namespace, String name, String installedAppLabel, String type = "ANY")` | Checks whether an installed app with the namespace, name, and label exists, optionally restricting its type. Parameters: namespace - namespace containing the app type name - app ty |
| `isHubDeveloper` | `boolean isHubDeveloper()` | Returns whether the hub developer-mode flag is enabled. Returns: the hub developer-mode flag value. |
| `isLibraryPresent` | `boolean isLibraryPresent(String namespace, String name)` | Checks whether a library exists for the namespace and name. Parameters: namespace - namespace containing the library name - library name Returns: true when a library exists for the |
| `isSystemType` | `boolean isSystemType()` | Checks whether the current app type is the system type. Returns: true when this app executor’s type is sys; otherwise false. |
| `isSystemTypeOrHubDeveloper` | `boolean isSystemTypeOrHubDeveloper()` | Checks whether this app is a system app or the hub is in developer mode. Returns: true for a system app executor or when hub developer mode is enabled; otherwise false. |
| `localApiServerUrl` | `String localApiServerUrl(String url)` | Appends a path to the local apps API base URL. Parameters: url - path appended to the API base URL Returns: the local apps API base URL followed by / and the supplied path String. |
| `pause` | `void pause(Long millisecs)` | Pauses app script execution for the requested number of milliseconds and records that runtime as paused. Parameters: millisecs - pause duration in milliseconds |
| `pauseExecution` | `void pauseExecution(Long millisecs)` | Sleeps for the requested duration and subtracts it from this app’s runtime statistic. Parameters: millisecs - pause duration in milliseconds |
| `processBase64EncodedLambdaFormParams` | `Map processBase64EncodedLambdaFormParams(Map sourceParams)` | Expands Base64-encoded URL form data that was parsed as a parameter name and padding value. Existing named parameters take precedence over values found in the decoded form data. Pa |
| `sendSms` | `void sendSms(String phone, String message)` | Calls the deprecated SMS method, which logs that sending SMS messages is unsupported. Parameters: phone - phone number message - message text |
| `sendSmsMessage` | `void sendSmsMessage(String phone, String message)` | Logs that SMS sending is unsupported; this deprecated method does not send a message. Parameters: phone - phone number message - message text |
| `setLocationMode` | `void setLocationMode(String mode)` | Changes the active location mode by its name. Parameters: mode - location mode name to activate |
| `setLocationModeById` | `void setLocationModeById(Long modeId)` | Changes the active location mode when the supplied mode id exists. Parameters: modeId - location mode id to activate or use as the away-mode guard |
| `setModeUnlessAway` | `void setModeUnlessAway(Long modeId)` | Changes location mode unless the requested mode would replace Away. Parameters: modeId - location mode id to activate or use as the away-mode guard |
| `setState` | `void setState(Map value)` | Replaces the installed app state, or the local state map while no app is installed. Parameters: value - state entries to store for this app or executor; keys and values are app-def |
| `setThirdPartyHubIPList` | `void setThirdPartyHubIPList(List thirdPartyHubIPList)` | Stores the third-party hub IP allowlist, then refreshes the LAN controller setting that uses it. Parameters: thirdPartyHubIPList - replacement list of third-party hub IP addresses |
| `unzipBase64` | `String unzipBase64(String s)` | Decodes Base64 text, decompresses the GZIP payload, and returns UTF-8 text. Parameters: s - UTF-8 text to compress or Base64 GZIP text to expand Returns: the UTF-8 text from the Ba |
