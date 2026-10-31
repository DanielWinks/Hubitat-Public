# LearnModeSetStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementbasicv2-learnmodesetstatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementbasicv2.LearnModeSetStatus`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementbasicv1.LearnModeSetStatus`
- [LearnModeSetStatus](../protocols/api-hubitat-zwave-commands-networkmanagementbasicv1-learnmodesetstatus.md)
- [LearnModeSetStatus](../protocols/api-hubitat-zwave-commands-networkmanagementbasicv1-learnmodesetstatus.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `LearnModeSetStatus` | `LearnModeSetStatus()` | Creates the command with its declared field defaults. |
| `LearnModeSetStatus` | `LearnModeSetStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `DSK` | `List<Short>` | — |
| `grantedKeys` | `Short` | — |
| `KEXFailType` | `Short` | — |
| `LEARN_MODE_INTERVIEW_COMPLETED` | `Short` | — |
| `newNodeId` | `Short` | — |
| `seqNo` | `Short` | — |
| `status` | `Short` | — |
