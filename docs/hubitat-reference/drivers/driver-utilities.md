# Driver utilities

- **ID:** `driver-utilities`
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
| `getLinkText` | `String getLinkText(DeviceWrapper device)` | Returns the display name used as an event link label. Parameters: device - Device whose display name supplies the label. Returns: Device display name. |
| `getParent` | `Object getParent()` | Returns the parent device or app wrapper, when this device has a parent. Returns: ParentDeviceWrapper for a parent device, InstalledAppWrapper for a parent app, or null when there  |
| `hexStrToSignedInt` | `Long hexStrToSignedInt(String hexStr)` | Parses a signed hexadecimal string as an integer. Parameters: hexStr - Hexadecimal digits to parse. Returns: Parsed signed value, or null when the input is null or empty. |
| `hexStrToUnsignedInt` | `Long hexStrToUnsignedInt(String hexStr)` | Parses an unsigned hexadecimal string as an integer. Parameters: hexStr - Hexadecimal digits to parse. Returns: Parsed unsigned value, or null when the input is null or empty. |
| `intToHexStr` | `String intToHexStr(Long value, Integer minBytes = 1)` | Formats an integer as a hexadecimal string with a minimum byte width. Parameters: value - Integer to format. minBytes - Minimum output width in bytes; defaults to 1. Returns: Upper |
| `isSystemTypeOrHubDeveloper` | `boolean isSystemTypeOrHubDeveloper()` | Reports whether this device executor has system-driver or hub-developer privileges. Returns: True for system drivers or when the hub developer flag is enabled. |
| `limitDoubleRange` | `Double limitDoubleRange(Object value, double min, double max)` | Converts a value to a double and clamps it to an inclusive range. Parameters: value - Value parsed as a number; invalid text becomes zero before clamping. min - Inclusive lower bou |
| `limitIntegerRange` | `Integer limitIntegerRange(Object value, int min, int max)` | Converts a value to an integer and clamps it to an inclusive range. Parameters: value - Value parsed as a number; invalid text becomes zero before clamping. min - Inclusive lower b |
| `pauseExecution` | `void pauseExecution(Long millisecs)` | Pauses driver execution and subtracts the pause duration from its runtime total. Parameters: millisecs - Pause duration in milliseconds. |
| `stringToMap` | `Map stringToMap(String str)` | Parses a relaxed JSON-style object string into a map. Parameters: str - String containing quoted or unquoted map keys and values; square brackets are converted to braces before par |
