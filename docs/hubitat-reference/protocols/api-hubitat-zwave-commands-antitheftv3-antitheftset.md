# AntitheftSet

- **ID:** `api-hubitat-zwave-commands-antitheftv3-antitheftset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.antitheftv3.AntitheftSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.antitheftv2.AntitheftSet`
- [AntitheftSet](../protocols/api-hubitat-zwave-commands-antitheftv2-antitheftset.md)
- [AntitheftSet](../protocols/api-hubitat-zwave-commands-antitheftv2-antitheftset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AntitheftSet` | `AntitheftSet()` | Creates the command with its declared field defaults. |
| `AntitheftSet` | `AntitheftSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `zwaveAllianceLockingEntityID` | `Integer` | — |
