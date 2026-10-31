# WakeUpIntervalCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-wakeupv3-wakeupintervalcapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.wakeupv3.WakeUpIntervalCapabilitiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport`
- [WakeUpIntervalCapabilitiesReport](../protocols/api-hubitat-zwave-commands-wakeupv2-wakeupintervalcapabilitiesreport.md)
- [WakeUpIntervalCapabilitiesReport](../protocols/api-hubitat-zwave-commands-wakeupv2-wakeupintervalcapabilitiesreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getWakeUpOnDemandSupport` | `Boolean getWakeUpOnDemandSupport()` | Reports whether bit 0 of properties1 is set for wake up on demand support. Returns: wake up on demand support value |
| `setWakeUpOnDemandSupport` | `void setWakeUpOnDemandSupport(Boolean value)` | Sets the wake up on demand support value used by this command. Parameters: value - wake up on demand support value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
| `WakeUpIntervalCapabilitiesReport` | `WakeUpIntervalCapabilitiesReport()` | Creates the command with its declared field defaults. |
| `WakeUpIntervalCapabilitiesReport` | `WakeUpIntervalCapabilitiesReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `WakeUpIntervalCapabilitiesReport` | `WakeUpIntervalCapabilitiesReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 13 values also keeps the declared fiel |
