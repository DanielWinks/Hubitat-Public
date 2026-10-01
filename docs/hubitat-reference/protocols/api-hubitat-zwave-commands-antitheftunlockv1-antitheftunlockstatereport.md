# AntitheftUnlockStateReport

- **ID:** `api-hubitat-zwave-commands-antitheftunlockv1-antitheftunlockstatereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.antitheftunlockv1.AntitheftUnlockStateReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `AntitheftUnlockStateReport` | `AntitheftUnlockStateReport()` | Creates the command with its declared field defaults. |
| `AntitheftUnlockStateReport` | `AntitheftUnlockStateReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getAntitheftHintLength` | `Short getAntitheftHintLength()` | Returns the antitheft hint length value stored in bits 2 through 5 of properties1. Returns: antitheft hint length value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getRestricted` | `Boolean getRestricted()` | Reports whether bit 1 of properties1 is set for restricted. Returns: restricted value |
| `getState` | `Boolean getState()` | Reports whether bit 0 of properties1 is set for state. Returns: state value |
| `setAntitheftHintLength` | `void setAntitheftHintLength(Short value)` | Sets the antitheft hint length value used by this command. Parameters: value - antitheft hint length value to encode |
| `setRestricted` | `void setRestricted(Boolean value)` | Sets the restricted value used by this command. Parameters: value - restricted value to encode |
| `setState` | `void setState(Boolean value)` | Sets the state value used by this command. Parameters: value - state value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `antitheftHint` | `List<Short>` | — |
| `manufacturerID` | `Integer` | — |
| `zwaveAllianceLockingEntityID` | `Integer` | — |
