# RateTblHistoricalDataGet

- **ID:** `api-hubitat-zwave-commands-ratetblmonitorv1-ratetblhistoricaldataget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.ratetblmonitorv1.RateTblHistoricalDataGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `datasetRequested` | `Integer` | — |
| `maximumReports` | `Short` | — |
| `rateParameterSetId` | `Short` | — |
| `startDay` | `Short` | — |
| `startHourLocalTime` | `Short` | — |
| `startMinuteLocalTime` | `Short` | — |
| `startMonth` | `Short` | — |
| `startSecondLocalTime` | `Short` | — |
| `startYear` | `Integer` | — |
| `stopDay` | `Short` | — |
| `stopHourLocalTime` | `Short` | — |
| `stopMinuteLocalTime` | `Short` | — |
| `stopMonth` | `Short` | — |
| `stopSecondLocalTime` | `Short` | — |
| `stopYear` | `Integer` | — |
