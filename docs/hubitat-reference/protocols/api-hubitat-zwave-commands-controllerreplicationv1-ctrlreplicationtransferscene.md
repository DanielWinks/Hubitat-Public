# CtrlReplicationTransferScene

- **ID:** `api-hubitat-zwave-commands-controllerreplicationv1-ctrlreplicationtransferscene`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.controllerreplicationv1.CtrlReplicationTransferScene`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CtrlReplicationTransferScene` | `CtrlReplicationTransferScene()` | Creates the command with its declared field defaults. |
| `CtrlReplicationTransferScene` | `CtrlReplicationTransferScene(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `level` | `Short` | — |
| `nodeId` | `Short` | — |
| `sceneId` | `Short` | — |
| `sequenceNumber` | `Short` | — |
