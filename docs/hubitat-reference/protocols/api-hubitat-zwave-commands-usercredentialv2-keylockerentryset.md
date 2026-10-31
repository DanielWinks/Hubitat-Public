# KeyLockerEntrySet

- **ID:** `api-hubitat-zwave-commands-usercredentialv2-keylockerentryset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv2.KeyLockerEntrySet`

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
| `KeyLockerEntrySet` | `KeyLockerEntrySet()` | Creates command. |
| `KeyLockerEntrySet` | `KeyLockerEntrySet(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `entryData` | `List<Short>` | — |
| `entrySlot` | `Integer` | — |
| `entryType` | `Short` | — |
| `operationType` | `Short` | — |
