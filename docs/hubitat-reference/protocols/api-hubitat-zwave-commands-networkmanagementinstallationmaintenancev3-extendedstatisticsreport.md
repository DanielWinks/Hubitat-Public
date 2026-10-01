# ExtendedStatisticsReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev3-extendedstatisticsreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev3.ExtendedStatisticsReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `ExtendedStatisticsReport` | `ExtendedStatisticsReport()` | Creates the command with its declared field defaults. |
| `ExtendedStatisticsReport` | `ExtendedStatisticsReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |

## Properties

| Name | Type | Description |
|---|---|---|
| `extendedNodeId` | `Integer` | — |
| `neighbors` | `Map<Short, Map<String, Short>>` | — |
| `packetErrorCount` | `Short` | — |
| `routeChanges` | `Short` | — |
| `SPEED_100` | `Short` | — |
| `SPEED_40` | `Short` | — |
| `SPEED_9` | `Short` | — |
| `STATISTIC_TYPE_NB` | `Short` | — |
| `STATISTIC_TYPE_PEC` | `Short` | — |
| `STATISTIC_TYPE_RC` | `Short` | — |
| `STATISTIC_TYPE_TC` | `Short` | — |
| `STATISTIC_TYPE_TS` | `Short` | — |
| `STATISTIC_TYPE_TS2` | `Short` | — |
| `sumOfTransmissionTimes` | `Long` | — |
| `sumOfTransmissionTimesSquared` | `Long` | — |
| `transmissionCount` | `Short` | — |
