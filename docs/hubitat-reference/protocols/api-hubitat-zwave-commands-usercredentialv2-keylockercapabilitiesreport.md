# KeyLockerCapabilitiesReport

- **ID:** `api-hubitat-zwave-commands-usercredentialv2-keylockercapabilitiesreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercredentialv2.KeyLockerCapabilitiesReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: identifier |
| `getPayload` | `List<Short> getPayload()` | Returns: wire payload |
| `KeyLockerCapabilitiesReport` | `KeyLockerCapabilitiesReport()` | Creates report. |
| `KeyLockerCapabilitiesReport` | `KeyLockerCapabilitiesReport(List<Map<String, Object>> payload)` | Parameters: payload - JS values |
| `KeyLockerCapabilitiesReport` | `KeyLockerCapabilitiesReport(String payload)` | Parameters: payload - ZIP payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `keyLockerCapabilities` | `List<Map<String, Object>>` | — |
