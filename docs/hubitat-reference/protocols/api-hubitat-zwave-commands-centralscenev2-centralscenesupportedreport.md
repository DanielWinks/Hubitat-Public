# CentralSceneSupportedReport

- **ID:** `api-hubitat-zwave-commands-centralscenev2-centralscenesupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.centralscenev1.CentralSceneSupportedReport`
- [CentralSceneSupportedReport](../protocols/api-hubitat-zwave-commands-centralscenev1-centralscenesupportedreport.md)
- [CentralSceneSupportedReport](../protocols/api-hubitat-zwave-commands-centralscenev1-centralscenesupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CentralSceneSupportedReport` | `CentralSceneSupportedReport()` | Creates the command with its declared field defaults. |
| `CentralSceneSupportedReport` | `CentralSceneSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `CentralSceneSupportedReport` | `CentralSceneSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `identical` | `Boolean` | — |
| `supportedKeyAttributes` | `List<Map>` | — |
| `supportedScenes` | `Short` | — |
