# Event

- **ID:** `api-com-hubitat-hub-domain-event`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Event`

> Use Event from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `Event` | `Event()` | Create an event with the default field values. |
| `Event` | `Event(Event event, String subscriptionData = null)` | Copy the event fields used for subscription delivery. Parameters: event - event whose persisted and display fields are copied subscriptionData - optional subscription-specific data |
| `getData` | `String getData()` | Returns: serialized event data, or null when no data was set |
| `getDate` | `Date getDate()` | Returns: event date, initializing it to the current time if it is null |
| `getDateValue` | `Date getDateValue()` | Returns: value parsed using the supported event date formats, or null when parsing fails |
| `getDevice` | `DeviceWrapper getDevice()` | Returns: the wrapper for this event's device, loaded lazily; null when no device id exists |
| `getDeviceId` | `Long getDeviceId()` | Returns: the device database id, using the bound device when available |
| `getDisplayName` | `String getDisplayName()` | Returns: event source display name, resolving it from the location or device when needed |
| `getDoubleValue` | `Double getDoubleValue()` | Returns: value parsed as a Double, or 0.0 when it is not numeric |
| `getFloatValue` | `Float getFloatValue()` | Returns: value parsed as a Float, or 0.0 when it is not numeric |
| `getIntegerValue` | `Integer getIntegerValue()` | Returns: value parsed as an Integer, or 0 when it is not an integer |
| `getJsonData` | `Map getJsonData()` | Parse the serialized event data as a JSON object. Returns: map with these values: Keys are the String property names in the JSON object; no fixed keys are declared. Values are JSON |
| `getLinkText` | `String getLinkText()` | Returns: the display name; use getDisplayName() instead |
| `getLocation` | `Location getLocation()` | Returns: the first configured location |
| `getLongValue` | `Long getLongValue()` | Returns: value parsed as a Long, or 0 when it is not an integer |
| `getNumberValue` | `Number getNumberValue()` | Returns: value parsed using the current locale's number format, or null when parsing fails |
| `getNumericValue` | `Number getNumericValue()` | Returns: the value returned by getNumberValue() |
| `getUnixTime` | `long getUnixTime()` | Returns: event date as milliseconds since the Unix epoch |
| `isDigital` | `Boolean isDigital()` | Returns: true when the event type is digital |
| `isPhysical` | `Boolean isPhysical()` | Returns: true when the event type is physical |
| `populateValues` | `Event populateValues(Map options)` | Apply event fields supplied to createEvent or sendEvent. Accepted options and their effects: name: optional String event name; a falsey value leaves the current name unchanged. val |
| `setData` | `void setData(String data)` | Parameters: data - serialized event data to store |
| `setLinkText` | `void setLinkText(String linkText)` | Parameters: linkText - display name to assign to this event |
| `toJson` | `String toJson(boolean includeData = false)` | Serialize this event to the hub event JSON shape. Parameters: includeData - true to include the serialized data field Returns: JSON text containing source, name, displayName, value |
| `toString` | `String toString()` | Returns: a short reflection-based representation of this event |
| `toUIMap` | `Map toUIMap()` | Create the event map used by the UI. Returns: map with these keys: id: Long event id; non-null. source: String source type; nullable. name: String event name; nullable. displayName |

## Properties

| Name | Type | Description |
|---|---|---|
| `archivable` | `boolean` | — |
| `date` | `Date` | — |
| `description` | `String` | — |
| `descriptionText` | `String` | — |
| `device` | `DeviceWrapper` | — |
| `deviceId` | `Long` | — |
| `displayed` | `boolean` | — |
| `displayName` | `String` | — |
| `forceAttributeUpdate` | `boolean` | — |
| `hubId` | `Long` | — |
| `id` | `Long` | — |
| `installedAppId` | `Long` | — |
| `isStateChange` | `Boolean` | — |
| `location` | `Location` | — |
| `locationId` | `Long` | — |
| `name` | `String` | — |
| `producedBy` | `String` | — |
| `source` | `String` | — |
| `subscriptionData` | `String` | — |
| `translatable` | `boolean` | — |
| `triggeredListeners` | `String` | — |
| `type` | `String` | — |
| `unit` | `String` | — |
| `value` | `String` | — |
