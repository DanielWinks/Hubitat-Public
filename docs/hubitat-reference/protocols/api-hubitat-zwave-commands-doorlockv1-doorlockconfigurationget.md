# DoorLockConfigurationGet

- **ID:** `api-hubitat-zwave-commands-doorlockv1-doorlockconfigurationget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv1.DoorLockConfigurationGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockConfigurationGet` | `DoorLockConfigurationGet()` | Creates the command with its declared field defaults. |
| `DoorLockConfigurationGet` | `DoorLockConfigurationGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
