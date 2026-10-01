# VersionReport

- **ID:** `api-hubitat-zwave-commands-versionv2-versionreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.versionv2.VersionReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.versionv1.VersionReport`
- [VersionReport](../protocols/api-hubitat-zwave-commands-versionv1-versionreport.md)
- [VersionReport](../protocols/api-hubitat-zwave-commands-versionv1-versionreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `VersionReport` | `VersionReport()` | Creates the command with its declared field defaults. |
| `VersionReport` | `VersionReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `VersionReport` | `VersionReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 5 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `firmware0SubVersion` | `Short` | — |
| `firmware0Version` | `Short` | — |
| `firmwareTargets` | `Short` | — |
| `hardwareVersion` | `Short` | — |
| `targetVersions` | `List<Map<String, Short>>` | — |
| `zWaveLibraryType` | `Short` | — |
| `zWaveProtocolSubVersion` | `Short` | — |
| `zWaveProtocolVersion` | `Short` | — |
