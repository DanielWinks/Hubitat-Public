# App script methods

- **ID:** `app-script-methods`
- **Section:** Apps
- **Class:** `com.hubitat.hub.executor.AppDelegatingScript`

> Call these methods directly in app code; the hub supplies the app script context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPendingHubCommandCount` | `int getPendingHubCommandCount()` | Returns the number of queued hub commands for this app execution. Returns: Number of actions waiting in the app command queue. |
| `sendHubCommand` | `Future sendHubCommand(HubAction hubAction)` | Sends a hub action on the app command worker and returns immediately. Parameters: hubAction - Action to send. Returns: Future for the submitted Runnable; its result is null, and ac |
| `sendHubCommand` | `Future sendHubCommand(HubMultiAction hubMultiAction)` | Sends a sequence of hub actions on the app command worker and returns immediately. Parameters: hubMultiAction - Sequence of actions to send. Returns: Future for the submitted Runna |
| `subscribe` | `void subscribe(Object thing, String attributeName, MetaMethod handlerMethod, Map options = null)` | Subscribes an app handler method to a device, device list, location, installed app, or wildcard device attribute. Parameters: thing - Device or device list, location, installed app |
| `subscribe` | `void subscribe(Object thing, String attributeName, String handlerMethod, Map options = null)` | Subscribes an app handler method to a device, device list, location, installed app, or wildcard device attribute. Parameters: thing - Device or device list, location, installed app |
