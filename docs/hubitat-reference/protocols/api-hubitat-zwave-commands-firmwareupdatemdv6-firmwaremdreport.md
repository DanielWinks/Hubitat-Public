# FirmwareMdReport

- **ID:** `api-hubitat-zwave-commands-firmwareupdatemdv6-firmwaremdreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.firmwareupdatemdv6.FirmwareMdReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.firmwareupdatemdv5.FirmwareMdReport`
- [FirmwareMdReport](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv5-firmwaremdreport.md)
- [FirmwareMdReport](../protocols/api-hubitat-zwave-commands-firmwareupdatemdv5-firmwaremdreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `FirmwareMdReport` | `FirmwareMdReport()` | Creates the command with its declared field defaults. |
| `FirmwareMdReport` | `FirmwareMdReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `FirmwareMdReport` | `FirmwareMdReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 6 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `cc` | `Boolean` | — |
| `checksum` | `Integer` | — |
| `firmwareId` | `Integer` | — |
| `firmwareIds` | `List<Integer>` | — |
| `firmwareUpgradable` | `Boolean` | — |
| `hardwareVersion` | `Short` | — |
| `manufacturerId` | `Integer` | — |
| `maxFragmentSize` | `Integer` | — |
| `numberOfTargets` | `Short` | — |
