# IndicatorSupportedReport

- **ID:** `api-hubitat-zwave-commands-indicatorv3-indicatorsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.indicatorv3.IndicatorSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.indicatorv2.IndicatorSupportedReport`
- [IndicatorSupportedReport](../protocols/api-hubitat-zwave-commands-indicatorv2-indicatorsupportedreport.md)
- [IndicatorSupportedReport](../protocols/api-hubitat-zwave-commands-indicatorv2-indicatorsupportedreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `IndicatorSupportedReport` | `IndicatorSupportedReport()` | Creates the command with its declared field defaults. |
| `IndicatorSupportedReport` | `IndicatorSupportedReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `IndicatorSupportedReport` | `IndicatorSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `binary` | `Boolean` | — |
| `bitMaskLength` | `Short` | — |
| `INDICATOR_TYPE_ALARM` | `Short` | — |
| `INDICATOR_TYPE_ALARM_BURGLAR` | `Short` | — |
| `INDICATOR_TYPE_ALARM_CO` | `Short` | — |
| `INDICATOR_TYPE_ALARM_SMOKE` | `Short` | — |
| `INDICATOR_TYPE_ARMED_AWAY` | `Short` | — |
| `INDICATOR_TYPE_ARMED_STAY` | `Short` | — |
| `INDICATOR_TYPE_BYPASS_CHALLENGE` | `Short` | — |
| `INDICATOR_TYPE_ENTRY_DELAY` | `Short` | — |
| `INDICATOR_TYPE_EXIT_DELAY` | `Short` | — |
| `INDICATOR_TYPE_IDENTIFY` | `Short` | — |
| `indicatorId` | `Short` | — |
| `lowPower` | `Boolean` | — |
| `multiLevel` | `Boolean` | — |
| `multilevelSound` | `Boolean` | — |
| `nextIndicatorId` | `Short` | — |
| `timeoutCentiSeconds` | `Boolean` | — |
| `timeoutMinutes` | `Boolean` | — |
| `timeoutSeconds` | `Boolean` | — |
| `togglingCycles` | `Boolean` | — |
| `togglingOnTime` | `Boolean` | — |
| `togglingPeriods` | `Boolean` | — |
