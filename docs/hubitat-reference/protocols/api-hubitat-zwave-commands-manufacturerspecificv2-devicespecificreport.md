# DeviceSpecificReport

- **ID:** `api-hubitat-zwave-commands-manufacturerspecificv2-devicespecificreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DeviceSpecificReport` | `DeviceSpecificReport()` | Creates the command with its declared field defaults. |
| `DeviceSpecificReport` | `DeviceSpecificReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `DeviceSpecificReport` | `DeviceSpecificReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `DEVICE_ID_DATA_FORMAT_BINARY` | `Short` | — |
| `DEVICE_ID_DATA_FORMAT_UTF8` | `Short` | — |
| `DEVICE_ID_TYPE_FACTORY_DEFAULT` | `Short` | — |
| `DEVICE_ID_TYPE_PSEUDO_RANDOM` | `Short` | — |
| `DEVICE_ID_TYPE_SERIAL_NUMBER` | `Short` | — |
| `deviceIdData` | `List<Short>` | — |
| `deviceIdDataFormat` | `Short` | — |
| `deviceIdDataLengthIndicator` | `Short` | — |
| `deviceIdType` | `Short` | — |
| `deviceIdTypeLookup` | `Map<String, Integer>` | — |
