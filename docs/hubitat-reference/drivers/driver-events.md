# Driver events

- **ID:** `driver-events`
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
| `createEvent` | `Map createEvent(Map options)` | Copies caller-provided event fields into a new map. Parameters: options - Event fields to copy; every supplied key and value is preserved without validation. Returns: Shallow copy  |
| `displayed` | `boolean displayed(String description, boolean value)` | Returns the supplied display flag for compatibility with driver event code. Parameters: description - Event description text; not used by this implementation. value - Display flag  |
| `eventsSince` | `List<Event> eventsSince(Date startDate, Map options = null)` | Returns this device's events recorded since a date. Parameters: startDate - Inclusive lower time bound used by DeviceService. options - Optional history query settings. max: Option |
| `isStateChange` | `boolean isStateChange(DeviceWrapper device, String name, String value)` | Tests whether a device attribute value differs from its current stored value. Parameters: device - Device whose attribute state is checked. name - Attribute name. value - Proposed  |
| `sendEvent` | `void sendEvent(Map properties)` | Creates a device event from its properties and sends it to the event controller. Parameters: properties - Event fields passed to Event.populateValues; see Event.populateValues for  |
