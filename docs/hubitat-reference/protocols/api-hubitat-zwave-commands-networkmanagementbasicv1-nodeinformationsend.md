# NodeInformationSend

- **ID:** `api-hubitat-zwave-commands-networkmanagementbasicv1-nodeinformationsend`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementbasicv1.NodeInformationSend`

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
| `NodeInformationSend` | `NodeInformationSend()` | Creates the command with its declared field defaults. |
| `NodeInformationSend` | `NodeInformationSend(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `destinationNodeId` | `Short` | — |
| `seqNo` | `Short` | — |
| `TRANSMIT_OPTION_ACK` | `Short` | — |
| `TRANSMIT_OPTION_EXPLORE` | `Short` | — |
| `TRANSMIT_OPTION_LOW_POWER` | `Short` | — |
| `TRANSMIT_OPTION_NO_ROUTE` | `Short` | — |
| `TRANSMIT_OPTION_NULL` | `Short` | — |
| `txOptions` | `Short` | — |
