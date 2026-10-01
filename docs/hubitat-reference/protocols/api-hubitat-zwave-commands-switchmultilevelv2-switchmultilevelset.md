# SwitchMultilevelSet

- **ID:** `api-hubitat-zwave-commands-switchmultilevelv2-switchmultilevelset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet`
- [SwitchMultilevelSet](../protocols/api-hubitat-zwave-commands-switchmultilevelv1-switchmultilevelset.md)
- [SwitchMultilevelSet](../protocols/api-hubitat-zwave-commands-switchmultilevelv1-switchmultilevelset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `SwitchMultilevelSet` | `SwitchMultilevelSet()` | Creates the command with its declared field defaults. |
| `SwitchMultilevelSet` | `SwitchMultilevelSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `dimmingDuration` | `Short` | — |
