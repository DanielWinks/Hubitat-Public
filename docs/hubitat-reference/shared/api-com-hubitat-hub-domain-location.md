# Location

- **ID:** `api-com-hubitat-hub-domain-location`
- **Section:** Shared APIs
- **Class:** `com.hubitat.hub.domain.Location`

> Use Location from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `createVariable` | `void createVariable(String name, List values = null)` | Create a location variable. Parameters: name - variable name values - optional List of allowed values; null leaves the values unset |
| `getCurrentMode` | `Mode getCurrentMode()` | Returns: current mode, or null when the location has no current mode |
| `getFormattedLatitude` | `String getFormattedLatitude()` | Returns: absolute latitude followed by N for positive values or S for zero and negative values |
| `getFormattedLongitude` | `String getFormattedLongitude()` | Returns: absolute longitude followed by E for positive values or W for zero and negative values |
| `getHub` | `Hub getHub()` | Returns: detached first configured hub, or null when none is configured |
| `getHubs` | `List<Hub> getHubs()` | Returns: new one-element list containing a detached first hub, which may be null |
| `getMode` | `String getMode()` | Returns: current mode name, or null when the location has no current mode |
| `getModes` | `List<Mode> getModes()` | Returns: the list returned by getTrueModes() |
| `getSunrise` | `Date getSunrise()` | Returns: copy of the sunrise date, or null when no sunrise is set |
| `getSunset` | `Date getSunset()` | Returns: copy of the sunset date, or null when no sunset is set |
| `getTimeFormat` | `String getTimeFormat()` | Returns: configured display format used for time values |
| `getTrueModes` | `List<Mode> getTrueModes()` | Returns: modes configured for this location |
| `getVariableValues` | `List getVariableValues(String locationVariable)` | Parameters: locationVariable - variable name Returns: stored list of variable values, or null when absent |
| `removeVariable` | `void removeVariable(String name)` | Parameters: name - variable name to remove |
| `setMode` | `void setMode(String mode)` | Parameters: mode - name of the mode to set as current |
| `toMap` | `Map toMap(boolean full = true)` | Convert this location to the map returned by location APIs. Parameters: full - true to include mode and hub summaries; defaults to true Returns: map with these keys: id: Long locat |
| `toString` | `String toString()` | Returns: location name |

## Properties

| Name | Type | Description |
|---|---|---|
| `id` | `Long` | — |
| `latitude` | `BigDecimal` | — |
| `longitude` | `BigDecimal` | — |
| `modeId` | `Long` | — |
| `name` | `String` | — |
| `state` | `String` | — |
| `sunrise` | `Date` | — |
| `sunset` | `Date` | — |
| `temperatureScale` | `String` | — |
| `timeZone` | `TimeZone` | — |
| `version` | `Long` | — |
| `zipCode` | `String` | — |
