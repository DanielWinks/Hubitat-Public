# IndicatorSet

- **ID:** `api-hubitat-zwave-commands-indicatorv2-indicatorset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.indicatorv2.IndicatorSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.indicatorv1.IndicatorSet`
- [IndicatorSet](../protocols/api-hubitat-zwave-commands-indicatorv1-indicatorset.md)
- [IndicatorSet](../protocols/api-hubitat-zwave-commands-indicatorv1-indicatorset.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `IndicatorSet` | `IndicatorSet()` | Creates the command with its declared field defaults. |
| `IndicatorSet` | `IndicatorSet(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `INDICATOR_TYPE_ARMED` | `Short` | — |
| `INDICATOR_TYPE_ARMED_ZONE_1` | `Short` | — |
| `INDICATOR_TYPE_ARMED_ZONE_2` | `Short` | — |
| `INDICATOR_TYPE_ARMED_ZONE_3` | `Short` | — |
| `INDICATOR_TYPE_ARMED_ZONE_4` | `Short` | — |
| `INDICATOR_TYPE_ARMED_ZONE_5` | `Short` | — |
| `INDICATOR_TYPE_ARMED_ZONE_6` | `Short` | — |
| `INDICATOR_TYPE_BACKLIGHT_COMMANDS` | `Short` | — |
| `INDICATOR_TYPE_BACKLIGHT_DIGITS` | `Short` | — |
| `INDICATOR_TYPE_BACKLIGHT_LCD` | `Short` | — |
| `INDICATOR_TYPE_BACKLIGHT_LETTERS` | `Short` | — |
| `INDICATOR_TYPE_BUSY` | `Short` | — |
| `INDICATOR_TYPE_BUZZER` | `Short` | — |
| `INDICATOR_TYPE_CODE_ACCEPTED` | `Short` | — |
| `INDICATOR_TYPE_CODE_REJECTED` | `Short` | — |
| `INDICATOR_TYPE_DISARMED` | `Short` | — |
| `INDICATOR_TYPE_ENTER_ID` | `Short` | — |
| `INDICATOR_TYPE_ENTER_PIN` | `Short` | — |
| `INDICATOR_TYPE_FAULT` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_1` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_10` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_11` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_12` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_2` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_3` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_4` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_5` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_6` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_7` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_8` | `Short` | — |
| `INDICATOR_TYPE_INDICATION_BUTTON_9` | `Short` | — |
| `INDICATOR_TYPE_READY` | `Short` | — |
| `INDICATOR_TYPE_RESERVED` | `Short` | — |
| `indicatorCount` | `Short` | — |
| `indicatorValues` | `List<Map<String, Short>>` | — |
| `value` | `Short` | — |
