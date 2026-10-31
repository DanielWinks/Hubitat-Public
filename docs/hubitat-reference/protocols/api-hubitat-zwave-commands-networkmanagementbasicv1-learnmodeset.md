# LearnModeSet

- **ID:** `api-hubitat-zwave-commands-networkmanagementbasicv1-learnmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementbasicv1.LearnModeSet`

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
| `LearnModeSet` | `LearnModeSet()` | Creates the command with its declared field defaults. |
| `LearnModeSet` | `LearnModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `mode` | `Short` | — |
| `seqNo` | `Short` | — |
| `ZW_SET_LEARN_MODE_CLASSIC` | `Short` | — |
| `ZW_SET_LEARN_MODE_DISABLE` | `Short` | — |
| `ZW_SET_LEARN_MODE_NWI` | `Short` | — |
