# ZipKeepAlive

- **ID:** `api-hubitat-zwave-commands-zipv4-zipkeepalive`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipv4.ZipKeepAlive`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAckRequest` | `Boolean getAckRequest()` | Returns the ack request value stored in ackRequest. Returns: ack request value |
| `getAckResponse` | `Boolean getAckResponse()` | Returns the ack response value stored in ackResponse. Returns: ack response value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `setAckRequest` | `void setAckRequest(Boolean ackRequest)` | Sets the ack request value used by this command. Parameters: ackRequest - ack request value to encode |
| `setAckResponse` | `void setAckResponse(Boolean ackResponse)` | Sets the ack response value used by this command. Parameters: ackResponse - ack response value to encode |
