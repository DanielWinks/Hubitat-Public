# Driver script methods

- **ID:** `driver-script-methods`
- **Section:** Drivers
- **Class:** `com.hubitat.hub.executor.DeviceDelegatingScript`

> Call these methods directly in device driver code; the hub supplies the driver script context.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPendingHubCommandCount` | `int getPendingHubCommandCount()` | Returns the number of queued hub commands for this device execution. Returns: Number of commands waiting in the device command queue. |
| `sendHubCommand` | `Future sendHubCommand(HubAction hubAction)` | Sends a hub action on the device command worker and returns immediately. Parameters: hubAction - Action to send. Returns: Future for the submitted Runnable; its result is null, and |
| `sendHubCommand` | `Future sendHubCommand(HubMultiAction hubMultiAction)` | Sends a sequence of hub actions on the device command worker and returns immediately. Parameters: hubMultiAction - Sequence of actions to send. Returns: Future for the submitted Ru |
