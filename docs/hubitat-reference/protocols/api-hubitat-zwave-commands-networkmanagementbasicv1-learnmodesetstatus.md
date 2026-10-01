# LearnModeSetStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementbasicv1-learnmodesetstatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementbasicv1.LearnModeSetStatus`

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
| `LearnModeSetStatus` | `LearnModeSetStatus()` | Creates the command with its declared field defaults. |
| `LearnModeSetStatus` | `LearnModeSetStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `LEARN_MODE_DONE` | `Short` | — |
| `LEARN_MODE_FAILED` | `Short` | — |
| `LEARN_MODE_SECURITY_FAILED` | `Short` | — |
| `newNodeId` | `Short` | — |
| `seqNo` | `Short` | — |
| `status` | `Short` | — |
