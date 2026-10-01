# ProtectionSet

- **ID:** `api-hubitat-zwave-commands-protectionv2-protectionset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.protectionv2.ProtectionSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.protectionv1.ProtectionSet`
- [ProtectionSet](../protocols/api-hubitat-zwave-commands-protectionv1-protectionset.md)
- [ProtectionSet](../protocols/api-hubitat-zwave-commands-protectionv1-protectionset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Serializes the V2 local and RF protection states for Z-Wave JS. Returns: JSON command accepted by the Z-Wave JS Protection API |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ProtectionSet` | `ProtectionSet()` | Creates the command with its declared field defaults. |
| `ProtectionSet` | `ProtectionSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `localProtectionState` | `Short` | — |
| `rfProtectionState` | `Short` | — |
