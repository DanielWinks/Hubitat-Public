# Event subscription wrapper

- **ID:** `api-com-hubitat-app-eventsubscriptionwrapper`
- **Section:** Shared APIs
- **Class:** `com.hubitat.app.EventSubscriptionWrapper`

> Use EventSubscriptionWrapper from app or driver code when you need app or driver APIs.

## Methods

| Name | Signature | Description |
|---|---|---|
| `EventSubscriptionWrapper` | `EventSubscriptionWrapper(EventSubscription eventSubscription)` | Wrap an event subscription. throws: IllegalArgumentException when eventSubscription is null Parameters: eventSubscription - subscription record to wrap; null is rejected |
| `getData` | `String getData()` | Returns: subscription event name stored in the subscription record |
| `getDevice` | `DeviceWrapper getDevice()` | Returns: wrapper for the subscribed device, loaded lazily; null when it is unavailable |
| `getDeviceId` | `Long getDeviceId()` | Returns: subscribed device id, or null for a non-device subscription |
| `getHandler` | `String getHandler()` | Returns: handler name for the subscribed event |
| `getId` | `Long getId()` | Returns: subscription database id |
| `getLocationId` | `Long getLocationId()` | Returns: subscribed location id, or null for a device-only subscription |
