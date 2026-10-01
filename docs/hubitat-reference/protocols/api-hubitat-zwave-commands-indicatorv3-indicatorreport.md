# IndicatorReport

- **ID:** `api-hubitat-zwave-commands-indicatorv3-indicatorreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.indicatorv3.IndicatorReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.indicatorv2.IndicatorReport`
- [IndicatorReport](../protocols/api-hubitat-zwave-commands-indicatorv2-indicatorreport.md)
- [IndicatorReport](../protocols/api-hubitat-zwave-commands-indicatorv2-indicatorreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `IndicatorReport` | `IndicatorReport()` | Creates the command with its declared field defaults. |
| `IndicatorReport` | `IndicatorReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `IndicatorReport` | `IndicatorReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
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
