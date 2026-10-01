# S2ResynchronizationEvent

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev2-s2resynchronizationevent`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev2.S2ResynchronizationEvent`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `S2ResynchronizationEvent` | `S2ResynchronizationEvent()` | Creates the command with its declared field defaults. |
| `S2ResynchronizationEvent` | `S2ResynchronizationEvent(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `nodeId` | `Short` | — |
| `reason` | `Short` | — |
| `SOS_EVENT_REASON_UNANSWERED` | `short` | — |
