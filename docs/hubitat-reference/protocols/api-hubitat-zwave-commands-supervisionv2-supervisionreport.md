# SupervisionReport

- **ID:** `api-hubitat-zwave-commands-supervisionv2-supervisionreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.supervisionv2.SupervisionReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.supervisionv1.SupervisionReport`
- [SupervisionReport](../protocols/api-hubitat-zwave-commands-supervisionv1-supervisionreport.md)
- [SupervisionReport](../protocols/api-hubitat-zwave-commands-supervisionv1-supervisionreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getWakeUpRequest` | `Boolean getWakeUpRequest()` | Reports whether bit 6 of properties1 is set for wake up request. Returns: wake up request value |
| `setWakeUpRequest` | `void setWakeUpRequest(Boolean value)` | Sets the wake up request value used by this command. Parameters: value - wake up request value to encode |
| `SupervisionReport` | `SupervisionReport()` | Creates the command with its declared field defaults. |
| `SupervisionReport` | `SupervisionReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
