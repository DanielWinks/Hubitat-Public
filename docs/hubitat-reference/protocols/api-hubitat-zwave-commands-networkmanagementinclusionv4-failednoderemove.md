# FailedNodeRemove

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv4-failednoderemove`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv4.FailedNodeRemove`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementinclusionv3.FailedNodeRemove`
- [FailedNodeRemove](../protocols/api-hubitat-zwave-commands-networkmanagementinclusionv3-failednoderemove.md)
- [FailedNodeRemove](../protocols/api-hubitat-zwave-commands-networkmanagementinclusionv3-failednoderemove.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FailedNodeRemove` | `FailedNodeRemove()` | Creates the command with its declared field defaults. |
| `FailedNodeRemove` | `FailedNodeRemove(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `extendedNodeId` | `Integer` | — |
