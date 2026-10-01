# Driver communications

- **ID:** `driver-communications`
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
| `delayBetween` | `List<String> delayBetween(List<String> cmds)` | Adds a 100 millisecond delay action between consecutive command strings. Parameters: cmds - Command strings to separate. Returns: New list containing the commands and a delay entry |
| `delayBetween` | `List<String> delayBetween(List<String> cmds, Long delay)` | Adds a delay action between consecutive command strings. Parameters: cmds - Command strings to separate. delay - Delay duration in milliseconds. Returns: New list containing the co |
| `eventStreamClose` | `void eventStreamClose()` | Closes this device's deprecated event stream connection. deprecated: Use InterfaceHelper.EventStream#close(). |
| `eventStreamConnect` | `void eventStreamConnect(String url, String accessToken)` | Opens this device's deprecated event stream connection. deprecated: Use InterfaceHelper.EventStream#connect(). Parameters: url - Event stream endpoint URL. accessToken - Access tok |
| `getChromeCast` | `ChromeCast getChromeCast()` | Returns the Chromecast helper bound to this device. Returns: Lazily created Chromecast helper. |
| `getInterfaces` | `InterfaceHelper getInterfaces()` | Returns the interface helper bound to this device. Returns: Lazily created interface helper. |
| `getMatter` | `Matter getMatter()` | Returns the Matter helper bound to this device. Returns: Lazily created Matter helper. |
| `getZigbee` | `Zigbee getZigbee()` | Returns the Zigbee helper bound to this device. Returns: Lazily created Zigbee helper. |
| `getZwave` | `Zwave getZwave()` | Returns the Z-Wave helper bound to this device. Returns: Lazily created Z-Wave helper. |
| `getZwaveDeviceType` | `String getZwaveDeviceType()` | Returns the Z-Wave generic and specific device class as hexadecimal digits. Returns: Two-byte hexadecimal device class when available, otherwise null. |
| `getZwaveHubNodeId` | `Short getZwaveHubNodeId()` | Returns the hub's Z-Wave node id. Returns: Hub node id as a Short; the newer Z-Wave stack reports node 1. |
| `isZwaveListening` | `Boolean isZwaveListening()` | Reports whether this Z-Wave device listens for commands without being polled. Returns: True when the node is listening or frequently listening, false when it is not, or null when t |
| `response` | `HubAction response(Command cmd)` | Creates a Z-Wave hub action from a command object. Parameters: cmd - Z-Wave command to format and send. Returns: HubAction using the Z-Wave protocol. |
| `response` | `HubMultiAction response(List cmds)` | Creates one multi-action from a list of hub commands. Parameters: cmds - Commands or formatted actions to include. Returns: HubMultiAction containing the supplied entries. |
| `response` | `HubAction response(String cmd)` | Creates a hub action for a supported protocol command string. Parameters: cmd - Formatted Z-Wave, Zigbee, or Matter command. Returns: HubAction for a recognized protocol, or null f |
| `telnetClose` | `void telnetClose()` | Closes this device's Telnet interface. |
| `telnetConnect` | `void telnetConnect(Map options, String ip, int port, String username, String password)` | Opens or reuses this device's Telnet interface. Parameters: options - Optional Telnet settings; null uses the interface defaults. reuseExisting: Boolean, optional; when true, keeps |
| `telnetConnect` | `void telnetConnect(String ip, int port, String username, String password)` | Opens this device's Telnet interface with default connection settings. Parameters: ip - Remote host address. port - Remote Telnet port. username - Login name passed to the Telnet c |
| `zwaveSecureEncap` | `String zwaveSecureEncap(Command cmd)` | Formats and security-encapsulates a Z-Wave command object. Parameters: cmd - Z-Wave command to format and encapsulate. Returns: Formatted command, with security encapsulation when  |
| `zwaveSecureEncap` | `String zwaveSecureEncap(String cmd)` | Adds the security encapsulation required by this device's stored Z-Wave pairing data. Parameters: cmd - Hexadecimal Z-Wave command to encapsulate. Returns: Original command when se |

## Properties

| Name | Type | Description |
|---|---|---|
| `chromeCast` | `ChromeCast` | — |
| `device` | `DeviceWrapper` | — |
| `matter` | `Matter` | — |
| `zigbee` | `Zigbee` | — |
| `zwave` | `Zwave` | — |
