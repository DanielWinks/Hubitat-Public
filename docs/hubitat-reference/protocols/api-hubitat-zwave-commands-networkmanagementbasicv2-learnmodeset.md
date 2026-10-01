# LearnModeSet

- **ID:** `api-hubitat-zwave-commands-networkmanagementbasicv2-learnmodeset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementbasicv2.LearnModeSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementbasicv1.LearnModeSet`
- [LearnModeSet](../protocols/api-hubitat-zwave-commands-networkmanagementbasicv1-learnmodeset.md)
- [LearnModeSet](../protocols/api-hubitat-zwave-commands-networkmanagementbasicv1-learnmodeset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `LearnModeSet` | `LearnModeSet()` | Creates the command with its declared field defaults. |
| `LearnModeSet` | `LearnModeSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `returnInterviewStatus` | `Boolean` | — |
