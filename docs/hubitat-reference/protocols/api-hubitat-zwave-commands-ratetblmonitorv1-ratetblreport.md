# RateTblReport

- **ID:** `api-hubitat-zwave-commands-ratetblmonitorv1-ratetblreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.ratetblmonitorv1.RateTblReport`

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
| `consumptionPrecision` | `Short` | — |
| `consumptionScale` | `Short` | — |
| `dcpRateId` | `Short` | — |
| `durationMinute` | `Integer` | — |
| `maxConsumptionValue` | `Integer` | — |
| `maxDemandPrecision` | `Short` | — |
| `maxDemandScale` | `Short` | — |
| `maxDemandValue` | `Integer` | — |
| `minConsumptionValue` | `Integer` | — |
| `numberOfRateChar` | `Short` | — |
| `rateCharacter` | `List<Short>` | — |
| `rateParameterSetId` | `Short` | — |
| `rateType` | `Short` | — |
| `startHourLocalTime` | `Short` | — |
| `startMinuteLocalTime` | `Short` | — |
