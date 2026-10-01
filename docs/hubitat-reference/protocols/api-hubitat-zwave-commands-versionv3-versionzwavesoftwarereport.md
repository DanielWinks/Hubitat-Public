# VersionZWaveSoftwareReport

- **ID:** `api-hubitat-zwave-commands-versionv3-versionzwavesoftwarereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.versionv3.VersionZWaveSoftwareReport`

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
| `VersionZWaveSoftwareReport` | `VersionZWaveSoftwareReport()` | Creates the command with its declared field defaults. |
| `VersionZWaveSoftwareReport` | `VersionZWaveSoftwareReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `VersionZWaveSoftwareReport` | `VersionZWaveSoftwareReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 23 values also keeps the declared fiel |

## Properties

| Name | Type | Description |
|---|---|---|
| `applicationBuild` | `Integer` | — |
| `applicationVersion` | `Integer` | — |
| `frameworkBuild` | `Integer` | — |
| `frameworkVersion` | `Integer` | — |
| `interfaceBuild` | `Integer` | — |
| `interfaceVersion` | `Integer` | — |
| `protocolBuild` | `Integer` | — |
| `protocolVersion` | `Integer` | — |
| `sdkVersion` | `Integer` | — |
