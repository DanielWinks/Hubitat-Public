# DoorLockCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-doorlockv4-doorlockcapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.doorlockv4.DoorLockCapabilitiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DoorLockCapabilitiesReport` | `DoorLockCapabilitiesReport()` | Creates the command with its declared field defaults. |
| `DoorLockCapabilitiesReport` | `DoorLockCapabilitiesReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `DoorLockCapabilitiesReport` | `DoorLockCapabilitiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getARS` | `Boolean getARS()` | Reports whether bit 3 of properties3 is set for a rs. Returns: a rs value |
| `getBTBS` | `Boolean getBTBS()` | Reports whether bit 0 of properties3 is set for b tbs. Returns: b tbs value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getHRS` | `Boolean getHRS()` | Reports whether bit 2 of properties3 is set for h rs. Returns: h rs value |
| `getOperationTypeConstantOperation` | `Boolean getOperationTypeConstantOperation()` | Reports whether bit 1 of backing value is set for operation type constant operation. Returns: operation type constant operation value |
| `getOperationTypeTimedOperation` | `Boolean getOperationTypeTimedOperation()` | Reports whether bit 2 of backing value is set for operation type timed operation. Returns: operation type timed operation value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSupportedInsideHandleModes` | `Short getSupportedInsideHandleModes()` | Returns the supported inside handle modes value stored in bits 0 through 3 of properties2. Returns: supported inside handle modes value |
| `getSupportedOperationTypeBitMaskLength` | `Short getSupportedOperationTypeBitMaskLength()` | Returns the supported operation type bit mask length value stored in bits 0 through 4 of properties1. Returns: supported operation type bit mask length value |
| `getSupportedOutsideHandleModes` | `Short getSupportedOutsideHandleModes()` | Returns the supported outside handle modes value stored in bits 4 through 7 of properties2. Returns: supported outside handle modes value |
| `getTAS` | `Boolean getTAS()` | Reports whether bit 1 of properties3 is set for t as. Returns: t as value |
| `setARS` | `void setARS(Boolean value)` | Sets the a rs value used by this command. Parameters: value - a rs value to encode |
| `setBTBS` | `void setBTBS(Boolean value)` | Sets the b tbs value used by this command. Parameters: value - b tbs value to encode |
| `setHRS` | `void setHRS(Boolean value)` | Sets the h rs value used by this command. Parameters: value - h rs value to encode |
| `setOperationTypeConstantOperation` | `void setOperationTypeConstantOperation(Boolean value)` | Writes the operation type constant operation value to payload position 0. Parameters: value - operation type constant operation value to encode |
| `setOperationTypeTimedOperation` | `void setOperationTypeTimedOperation(Boolean value)` | Writes the operation type timed operation value to payload position 0. Parameters: value - operation type timed operation value to encode |
| `setSupportedInsideHandleModes` | `void setSupportedInsideHandleModes(Short value)` | Encodes supported inside handle modes in bits 0 through 3 of properties2 and preserves the bits selected by 0xF0. Parameters: value - supported inside handle modes value to encode |
| `setSupportedOperationTypeBitMaskLength` | `void setSupportedOperationTypeBitMaskLength(Short value)` | Encodes supported operation type bit mask length in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - supported operation type bit mask l |
| `setSupportedOutsideHandleModes` | `void setSupportedOutsideHandleModes(Short value)` | Sets the supported outside handle modes value used by this command. Parameters: value - supported outside handle modes value to encode |
| `setTAS` | `void setTAS(Boolean value)` | Sets the t as value used by this command. Parameters: value - t as value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |

## Properties

| Name | Type | Description |
|---|---|---|
| `supportedDoorComponents` | `Short` | — |
| `supportedDoorLockModeListLength` | `Short` | — |
| `supportedDoorLockModes` | `List<Short>` | — |
