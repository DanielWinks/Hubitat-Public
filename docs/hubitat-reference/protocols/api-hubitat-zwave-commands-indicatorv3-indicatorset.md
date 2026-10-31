# IndicatorSet

- **ID:** `api-hubitat-zwave-commands-indicatorv3-indicatorset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.indicatorv3.IndicatorSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.indicatorv2.IndicatorSet`
- [IndicatorSet](../protocols/api-hubitat-zwave-commands-indicatorv2-indicatorset.md)
- [IndicatorSet](../protocols/api-hubitat-zwave-commands-indicatorv2-indicatorset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `IndicatorSet` | `IndicatorSet()` | Creates the command with its declared field defaults. |
| `IndicatorSet` | `IndicatorSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

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
