# Shared utilities

- **ID:** `shared-utilities`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.executor.BaseExecutor`

> Use these methods directly in app or driver code; the hub supplies this execution context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `base64Decode` | `String base64Decode(String str)` | Decodes Base64 text as UTF-8. Parameters: str - Base64 text to decode. Returns: Decoded text, or an empty string when input is blank. |
| `base64Encode` | `String base64Encode(String str)` | Encodes UTF-8 text as Base64. Parameters: str - Text to encode. Returns: Base64 text, or an empty string when input is blank. |
| `compactHtml` | `String compactHtml(String html)` | Removes line breaks and leading or trailing whitespace from each HTML line. Parameters: html - HTML text to compact. Returns: Compacted HTML, or the original null or empty value. |
| `convertHueToGenericColorName` | `String convertHueToGenericColorName(def hue, def sat = 50)` | Maps hue and saturation percentages to the platform's generic color name. Parameters: hue - Hue from 0 through 100. sat - Saturation percentage; defaults to 50, and zero returns Wh |
| `convertTemperatureToGenericColorName` | `String convertTemperatureToGenericColorName(def temp)` | Maps a color temperature in kelvin to the platform's generic color name. Parameters: temp - Color temperature in kelvin. Returns: Generic name from Black, Sodium, Starlight, Sunris |
| `createLocationVariable` | `void createLocationVariable(String name, List values = null, boolean readOnly = false)` | Creates or replaces a location variable. Parameters: name - Variable name. values - Allowed values list, or null when the variable has no enumerated values. readOnly - True to prev |
| `decrypt` | `String decrypt(String value)` | Decrypts text produced by encrypt. Parameters: value - Encrypted text to decrypt. Returns: Decrypted plain text. |
| `encrypt` | `String encrypt(String value)` | Encrypts a string using the platform's static-IV helper. Parameters: value - Plain text to encrypt. Returns: Encrypted text. |
| `escapeHtml` | `String escapeHtml(String str)` | Escapes HTML-sensitive characters in a string. Parameters: str - Text to escape. Returns: HTML-escaped text, or an empty string when str is null or empty. |
| `generateRandomString` | `String generateRandomString(int length)` | Generates a random alphanumeric string. Parameters: length - Number of characters requested. Returns: Random string with the requested length. |
| `getAllRoomNames` | `List<String> getAllRoomNames()` | Returns names of all rooms, including rooms hidden from the UI room list. Returns: Room names. |
| `getExceptionMessageWithLine` | `String getExceptionMessageWithLine(Throwable exception)` | Formats an exception message with the first matching app or driver source line. Parameters: exception - Exception to inspect. Returns: Class, message, and the first matching source |
| `getHubAccountHash` | `String getHubAccountHash()` | Returns an MD5 hash of the email address on the first connected hub account. Returns: Lowercase hexadecimal hash, or an empty string when no email is available. |
| `getHubVersion` | `String getHubVersion()` | Returns the readable hub version. Returns: Human-readable hub version string. |
| `getInstalledDrivers` | `List<Map> getInstalledDrivers()` | Returns name and namespace records for every device type in the database. Returns: List of driver maps. Each map contains: namespace: String driver namespace. name: String driver n |
| `getJWTtoken` | `String getJWTtoken(String claims, String signKey)` | Creates a signed JSON Web Token from claims and a signing key. Parameters: claims - Claims JSON text. signKey - Signing key. Returns: Signed JWT text. |
| `getLatestAvailablePlatformVersion` | `String getLatestAvailablePlatformVersion()` | Returns the latest platform version reported by the cloud, or the running version when no update is available. Returns: Platform version string. |
| `getLocation` | `Location getLocation()` | Returns the first configured hub location. Returns: Hub location, or null when no location is configured. |
| `getLocationVariableNames` | `List getLocationVariableNames()` | Returns names of the configured location variables. Returns: Location variable names. |
| `getLocationVariableValues` | `List getLocationVariableValues(String locationVariable)` | Returns the allowed values for one location variable. Parameters: locationVariable - Name of the location variable. Returns: Allowed values for the variable. |
| `getLog` | `LogWrapper getLog()` | Returns the logger scoped to the running app or device. Returns: Lazily created LogWrapper using this executor's type, id, and name. |
| `getNumericHubVersion` | `int getNumericHubVersion()` | Returns the numeric hub version. Returns: Hub version as an integer. |
| `getObjectClassName` | `String getObjectClassName(Object o)` | Returns the Java class name for a non-null object. Parameters: o - Object to inspect. Returns: Fully qualified class name, or null when o is null. |
| `getPendingEventsCounter` | `int getPendingEventsCounter()` | Returns the number of events pending for the current execution context. Returns: Pending event count. |
| `getPlatformVersion` | `String getPlatformVersion()` | Returns the running platform build version. Returns: Current platform version string. |
| `getRooms` | `List<Map> getRooms()` | Returns the rooms configured on this hub. Returns: List of room maps. Each map contains: id: Long room id. name: String room name. deviceIds: List<Long> hub device ids assigned to  |
| `getStackTrace` | `String getStackTrace(Throwable exception)` | Formats an exception and its app or driver stack frames. Parameters: exception - Exception to format. Returns: Exception text followed by allowed stack frames; developer machines i |
| `getTTSVoice` | `String getTTSVoice()` | Returns the configured text-to-speech voice id. Returns: Current voice id. |
| `getTTSVoices` | `List getTTSVoices()` | Returns the available text-to-speech voices. Returns: List of voice maps. Each map contains name (String voice id), gender (String), and language (String); records are sorted by la |
| `isBeforeSystemStart` | `boolean isBeforeSystemStart()` | Reports whether the hub is still in its pre-startup phase. Returns: Current server startup flag. |
| `isDashboardBasedDevicesAndFavorites` | `boolean isDashboardBasedDevicesAndFavorites()` | Reports whether the dashboard-based devices and favorites setting is enabled. Returns: Setting value; defaults to true. |
| `isHubDeveloper` | `boolean isHubDeveloper()` | Reports whether hub developer mode is enabled. Returns: Current hub developer mode flag. |
| `isIntellij` | `boolean isIntellij()` | Reports whether the hub is running in its developer environment. Returns: True on the developer machine. |
| `isSystemTypeOrHubDeveloper` | `boolean isSystemTypeOrHubDeveloper()` | Reports whether this executor is a system app or hub developer. Returns: True when the executor has system or hub-developer access. |
| `isTTSFriendly` | `boolean isTTSFriendly()` | Reports whether the hub configuration enables text-to-speech friendly output. Returns: Platform setting value. |
| `minimizeWhitespaces` | `String minimizeWhitespaces(String str)` | Replaces line breaks and tabs with spaces and collapses repeated whitespace. Parameters: str - Text to compact. Returns: Trimmed text with single spaces between words and no spaces |
| `parseJson` | `Object parseJson(String stringToParse)` | Parses JSON text into Groovy maps, lists, and scalar values. Parameters: stringToParse - Valid JSON document. Returns: Parsed JSON value; the top-level value may be a Map, List, St |
| `parseJsonFromBase64` | `Object parseJsonFromBase64(String stringToParse)` | Base64-decodes and parses a JSON document. Parameters: stringToParse - Base64-encoded UTF-8 JSON text. Returns: Parsed JSON value; the top-level value may be a Map, List, String, N |
| `parseXML` | `GPathResult parseXML(String stringToParse)` | Parses XML text into a GPathResult tree. Parameters: stringToParse - XML document text. Returns: Parsed XML document. |
| `removeHtmlTags` | `String removeHtmlTags(String str)` | Removes HTML tags from a string. Parameters: str - Text containing HTML. Returns: Text with HTML tags removed. |
| `removeLocationVariable` | `void removeLocationVariable(String name)` | Removes a location variable. Parameters: name - Name of the location variable to remove. |
| `resizeImage` | `byte[] resizeImage(byte[] imageBytes, int maxWidth, int maxHeight)` | Resizes an image to the requested dimensions when either source dimension is larger, then encodes it as JPEG. Parameters: imageBytes - Encoded source image bytes supported by Image |
| `sendLocationEvent` | `void sendLocationEvent(Map properties)` | Creates and sends an event for the hub location. Parameters: properties - Event fields passed to Event.populateValues; see Event.populateValues for the complete field schema and va |
| `sendPush` | `void sendPush(String message, String mobileDeviceId = null)` | Sends a mobile push notification when both message and device id are supplied. Parameters: message - Notification text. mobileDeviceId - Target mobile device id; null skips sending |
| `sendPushMessage` | `void sendPushMessage(String message, String mobileDeviceId = null)` | Sends a mobile push notification when both message and device id are supplied. Parameters: message - Notification text. mobileDeviceId - Target mobile device id; null skips sending |
| `setGlobalVar` | `Boolean setGlobalVar(String name, Object value, Boolean sendConnectorEvent = true)` | Sets a global variable and emits its change event when its value changes. Parameters: name - Variable name; forbidden punctuation is removed before lookup and update. value - New v |
| `textToSpeech` | `Map textToSpeech(String stringToBeSynthesized, String voice = null, def initialPauseStr = null)` | Generates speech audio and returns its audio file metadata. Parameters: stringToBeSynthesized - Text to synthesize. voice - Voice id; null or an unknown id selects the current conf |
| `urlEncode` | `String urlEncode(String str)` | URL-encodes text as UTF-8 and represents spaces as %20. Parameters: str - Text to encode. Returns: Encoded text, or an empty string when str is null or empty. |
