# SensorMultilevelReport

- **ID:** `api-hubitat-zwave-commands-sensormultilevelv9-sensormultilevelreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.sensormultilevelv9.SensorMultilevelReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.sensormultilevelv8.SensorMultilevelReport`
- [SensorMultilevelReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv8-sensormultilevelreport.md)
- [SensorMultilevelReport](../protocols/api-hubitat-zwave-commands-sensormultilevelv8-sensormultilevelreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `SensorMultilevelReport` | `SensorMultilevelReport()` | Creates the command with its declared field defaults. |
| `SensorMultilevelReport` | `SensorMultilevelReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `SensorMultilevelReport` | `SensorMultilevelReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `SENSOR_TYPE_RF_SIGNAL_STRENGTH_V9` | `Short` | — |
| `SENSOR_TYPE_WATER_FLOW_V9` | `Short` | — |
| `SENSOR_TYPE_WATER_PRESSURE_V9` | `Short` | — |
