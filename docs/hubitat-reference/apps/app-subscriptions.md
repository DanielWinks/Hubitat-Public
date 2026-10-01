# Subscriptions

- **ID:** `app-subscriptions`
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
| `getAppsUsingDevice` | `List<InstalledApp> getAppsUsingDevice(Long deviceId)` | Returns installed apps reported as using the supplied device. Parameters: deviceId - device id Returns: the List returned by DeviceService for this device id. |
| `getLocationEventsSince` | `List<Event> getLocationEventsSince(String attributeName, Date startDate, Map options = null)` | Returns location events with the requested attribute at or after the supplied start date. Parameters: attributeName - event attribute name startDate - earliest event date to includ |
| `getSubscribedDeviceById` | `DeviceWrapper getSubscribedDeviceById(Long deviceId)` | Loads a device only when the current app is subscribed to it. Parameters: deviceId - device id Returns: the subscribed device wrapper, or null when the app is not subscribed to a m |
| `sendEvent` | `void sendEvent(DeviceWrapper device, Map properties)` | Builds an Event from the supplied event properties and submits it for the app, device wrapper, or device network id. Parameters: device - device wrapper that receives the event pro |
| `sendEvent` | `void sendEvent(Map properties)` | Builds an Event from the supplied event properties and submits it for the app, device wrapper, or device network id. Parameters: properties - event fields consumed by Event.populat |
| `sendEvent` | `void sendEvent(String dni, Map properties)` | Builds an Event from the supplied event properties and submits it for the app, device wrapper, or device network id. Parameters: dni - device network id of the event target propert |
| `subscribe` | `void subscribe(Object thing, MetaMethod handlerMethod)` | Registers a handler for every event from a device, location, installed app, or device list. The handler method receives one Event argument. It runs asynchronously after the event i |
| `subscribe` | `void subscribe(Object thing, MetaMethod handlerMethod, Map options)` | Registers a handler for every event from a device, location, installed app, or device list, and attaches optional data. The handler method runs asynchronously and receives one Even |
| `subscribe` | `void subscribe(Object thing, String methodName)` | Registers a named handler for every event from a device, location, installed app, or device list. The handler method receives one Event argument. It runs asynchronously after the e |
| `subscribe` | `void subscribe(Object thing, String methodName, Map options)` | Registers a named handler for every event from a device, location, installed app, or device list, and attaches optional data. The handler method runs asynchronously and receives on |
| `subscribe` | `void subscribe(Object thing, String attributeName, MetaMethod handlerMethod, Map options = null)` | Registers an event handler for a device attribute, location event, or all devices that expose the named attribute. The handler method receives one Event argument. Its common fields |
| `subscribe` | `void subscribe(Object thing, String attributeName, String methodName, Map options = null)` | Registers a named handler for a device attribute, location event, or all devices that expose the named attribute. The handler method receives one Event argument, including the even |
| `unsubscribe` | `void unsubscribe()` | Removes every event subscription owned by this app. Call this before replacing a dynamic set of subscriptions, then add the current subscriptions again. unsubscribe() subscribe(the |
| `unsubscribe` | `void unsubscribe(DeviceWrapper device)` | Removes this app's subscriptions for the supplied device, across all attributes and handler methods. Parameters: device - device whose subscriptions should be removed unsubscribe(t |
| `unsubscribe` | `void unsubscribe(DeviceWrapper device, String attributeName)` | Removes this app's subscriptions for the supplied device and event attribute, across all handlers. Parameters: device - device wrapper receiving this event attributeName - event at |
| `unsubscribe` | `void unsubscribe(DeviceWrapper device, String attributeName, String handler)` | Removes this app's subscription for the supplied device, attribute, and handler method. Parameters: device - device wrapper receiving this event attributeName - event attribute nam |
| `unsubscribe` | `void unsubscribe(InstalledAppWrapper installedApp)` | Removes this app's subscriptions for the supplied installed app, across all event names and handlers. Parameters: installedApp - installed app subscription target unsubscribe(child |
| `unsubscribe` | `void unsubscribe(List<DeviceWrapper> deviceList)` | Removes this app's subscriptions for each supplied device, across all attributes and handler methods. Parameters: deviceList - device wrappers whose subscriptions should be removed |
| `unsubscribe` | `void unsubscribe(List<DeviceWrapper> deviceList, String attributeName)` | Removes this app's subscriptions for the supplied attribute on each device, across all handlers. Parameters: deviceList - device wrappers whose subscriptions should be removed attr |
| `unsubscribe` | `void unsubscribe(List<DeviceWrapper> deviceList, String attributeName, String handler)` | Removes the matching attribute and handler subscription for each supplied device. Parameters: deviceList - device wrappers whose subscriptions should be removed attributeName - eve |
| `unsubscribe` | `void unsubscribe(Location location)` | Removes this app's subscriptions for the supplied location, across all location event names. Parameters: location - location subscription target unsubscribe(location) |
| `unsubscribe` | `void unsubscribe(Location location, String attributeName)` | Removes this app's subscriptions for the supplied location event. Parameters: location - location subscription target attributeName - event attribute name unsubscribe(location, "mo |
| `unsubscribe` | `void unsubscribe(Location location, String attributeName, String handler)` | Removes this app's location subscription for the event name and handler method. Parameters: location - location subscription target attributeName - event attribute name handler - s |
| `unsubscribe` | `void unsubscribe(String handler)` | Unsubscribe from any subscriptions that use the handler method. Parameters: handler - handler method name whose app subscriptions should be removed unsubscribe("temperatureChanged" |
