# PriorityRouteReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev1-priorityroutereport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev1.PriorityRouteReport`

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
| `PriorityRouteReport` | `PriorityRouteReport()` | Creates the command with its declared field defaults. |
| `PriorityRouteReport` | `PriorityRouteReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |

## Properties

| Name | Type | Description |
|---|---|---|
| `repeater1` | `Short` | — |
| `repeater2` | `Short` | — |
| `repeater3` | `Short` | — |
| `repeater4` | `Short` | — |
| `routeNodeId` | `Short` | — |
| `routeType` | `Short` | — |
| `speed` | `Short` | — |
| `SPEED_100` | `Short` | — |
| `SPEED_40` | `Short` | — |
| `SPEED_9` | `Short` | — |
| `ZW_PRIORITY_ROUTE_APP_PR` | `Short` | — |
| `ZW_PRIORITY_ROUTE_ZW_LWR` | `Short` | — |
| `ZW_PRIORITY_ROUTE_ZW_NLWR` | `Short` | — |
