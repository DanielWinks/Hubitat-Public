# ZipPacket

- **ID:** `api-hubitat-zwave-commands-zipv1-zippacket`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zipv1.ZipPacket`

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
| `getBitAddress` | `Boolean getBitAddress()` | Returns the bit address value stored in bitAddress. Returns: bit address value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getDestinationEndPoint` | `Short getDestinationEndPoint()` | Returns the destination end point value stored in destinationEndPoint. Returns: destination end point value |
| `getHeaderExtension` | `List<Short> getHeaderExtension()` | Returns the header extension value stored in headerExtension. Returns: header extension value |
| `getHeaderExtIncluded` | `Boolean getHeaderExtIncluded()` | Returns the header ext included value stored in headerExtIncluded. Returns: header ext included value |
| `getMoreInformation` | `Boolean getMoreInformation()` | Returns the more information value stored in moreInformation. Returns: more information value |
| `getNackOptionError` | `Boolean getNackOptionError()` | Returns the nack option error value stored in nackOptionError. Returns: nack option error value |
| `getNackQueueFull` | `Boolean getNackQueueFull()` | Returns the nack queue full value stored in nackQueueFull. Returns: nack queue full value |
| `getNackResponse` | `Boolean getNackResponse()` | Returns the nack response value stored in nackResponse. Returns: nack response value |
| `getNackWaiting` | `Boolean getNackWaiting()` | Returns the nack waiting value stored in nackWaiting. Returns: nack waiting value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getRes31` | `Boolean getRes31()` | Returns the res31 value stored in res31. Returns: res31 value |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `getSourceEndPoint` | `Short getSourceEndPoint()` | Returns the source end point value stored in sourceEndPoint. Returns: source end point value |
| `getzWaveCmdIncluded` | `Boolean getzWaveCmdIncluded()` | Returns the z wave cmd included value stored in zWaveCmdIncluded. Returns: z wave cmd included value |
| `getzWaveCommand` | `List<Short> getzWaveCommand()` | Returns the z wave command value stored in zWaveCommand. Returns: z wave command value |
| `setAckRequest` | `void setAckRequest(Boolean ackRequest)` | Sets the ack request value used by this command. Parameters: ackRequest - ack request value to encode |
| `setAckResponse` | `void setAckResponse(Boolean ackResponse)` | Sets the ack response value used by this command. Parameters: ackResponse - ack response value to encode |
| `setBitAddress` | `void setBitAddress(Boolean bitAddress)` | Sets the bit address value used by this command. Parameters: bitAddress - bit address value to encode |
| `setDestinationEndPoint` | `void setDestinationEndPoint(Short destinationEndPoint)` | Sets the destination end point value used by this command. Parameters: destinationEndPoint - destination end point value to encode |
| `setHeaderExtension` | `void setHeaderExtension(List<Short> headerExtension)` | Sets the header extension value used by this command. Parameters: headerExtension - header extension value to encode |
| `setHeaderExtIncluded` | `void setHeaderExtIncluded(Boolean headerExtIncluded)` | Sets the header ext included value used by this command. Parameters: headerExtIncluded - header ext included value to encode |
| `setMoreInformation` | `void setMoreInformation(Boolean moreInformation)` | Sets the more information value used by this command. Parameters: moreInformation - more information value to encode |
| `setNackOptionError` | `void setNackOptionError(Boolean nackOptionError)` | Sets the nack option error value used by this command. Parameters: nackOptionError - nack option error value to encode |
| `setNackQueueFull` | `void setNackQueueFull(Boolean nackQueueFull)` | Sets the nack queue full value used by this command. Parameters: nackQueueFull - nack queue full value to encode |
| `setNackResponse` | `void setNackResponse(Boolean nackResponse)` | Sets the nack response value used by this command. Parameters: nackResponse - nack response value to encode |
| `setNackWaiting` | `void setNackWaiting(Boolean nackWaiting)` | Sets the nack waiting value used by this command. Parameters: nackWaiting - nack waiting value to encode |
| `setRes31` | `void setRes31(Boolean res31)` | Sets the res31 value used by this command. Parameters: res31 - res31 value to encode |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `setSourceEndPoint` | `void setSourceEndPoint(Short sourceEndPoint)` | Sets the source end point value used by this command. Parameters: sourceEndPoint - source end point value to encode |
| `setzWaveCmdIncluded` | `void setzWaveCmdIncluded(Boolean zWaveCmdIncluded)` | Sets the z wave cmd included value used by this command. Parameters: zWaveCmdIncluded - z wave cmd included value to encode |
| `setzWaveCommand` | `void setzWaveCommand(List<Short> zWaveCommand)` | Sets the z wave command value used by this command. Parameters: zWaveCommand - z wave command value to encode |
