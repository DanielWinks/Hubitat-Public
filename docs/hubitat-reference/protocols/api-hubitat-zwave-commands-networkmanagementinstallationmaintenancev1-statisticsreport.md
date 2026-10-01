# StatisticsReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementinstallationmaintenancev1-statisticsreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinstallationmaintenancev1.StatisticsReport`

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
| `StatisticsReport` | `StatisticsReport()` | Creates the command with its declared field defaults. |
| `StatisticsReport` | `StatisticsReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `neighbors` | `Map<Short, Map<String, Short>>` | — |
| `packetErrorCount` | `Short` | — |
| `routeChanges` | `Short` | — |
| `routeNodeId` | `Short` | — |
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
