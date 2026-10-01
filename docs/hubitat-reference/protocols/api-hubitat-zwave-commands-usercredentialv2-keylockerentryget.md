# KeyLockerEntryGet

- **ID:** `api-hubitat-zwave-commands-usercredentialv2-keylockerentryget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv2.KeyLockerEntryGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getJSON` | `String getJSON()` | Returns: JS invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |
| `KeyLockerEntryGet` | `KeyLockerEntryGet()` | Creates command. |
| `KeyLockerEntryGet` | `KeyLockerEntryGet(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `entrySlot` | `Integer` | — |
| `entryType` | `Short` | — |
