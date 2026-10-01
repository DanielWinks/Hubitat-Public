# HrvControlModeSet

- **ID:** `api-hubitat-zwave-commands-hrvcontrolv1-hrvcontrolmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.hrvcontrolv1.HrvControlModeSet`

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
| `HrvControlModeSet` | `HrvControlModeSet()` | Creates the command with its declared field defaults. |
| `HrvControlModeSet` | `HrvControlModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `mode` | `Short` | — |
| `MODE_DEMAND_AUTOMATIC` | `Short` | — |
| `MODE_ENERGY_SAVINGS_MODE` | `Short` | — |
| `MODE_MANUAL` | `Short` | — |
| `MODE_OFF` | `Short` | — |
| `MODE_SCHEDULE` | `Short` | — |
