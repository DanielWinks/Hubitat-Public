# AntitheftUnlockSet

- **ID:** `api-hubitat-zwave-commands-antitheftunlockv1-antitheftunlockset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.antitheftunlockv1.AntitheftUnlockSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AntitheftUnlockSet` | `AntitheftUnlockSet()` | Creates the command with its declared field defaults. |
| `AntitheftUnlockSet` | `AntitheftUnlockSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getMagicCodeLength` | `Short getMagicCodeLength()` | Returns the magic code length value stored in bits 0 through 3 of properties1. Returns: magic code length value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setMagicCodeLength` | `void setMagicCodeLength(Short value)` | Encodes magic code length in bits 0 through 3 of properties1 and preserves the bits selected by 0xF0. Parameters: value - magic code length value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `magicCode` | `List<Short>` | — |
