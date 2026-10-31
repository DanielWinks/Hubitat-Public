# SecurityPanelZoneTypeReport

- **ID:** `api-hubitat-zwave-commands-securitypanelzonev1-securitypanelzonetypereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.securitypanelzonev1.SecurityPanelZoneTypeReport`

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
| `SecurityPanelZoneTypeReport` | `SecurityPanelZoneTypeReport()` | Creates the command with its declared field defaults. |
| `SecurityPanelZoneTypeReport` | `SecurityPanelZoneTypeReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 2 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `zoneNumber` | `Short` | — |
| `zoneType` | `Short` | — |
