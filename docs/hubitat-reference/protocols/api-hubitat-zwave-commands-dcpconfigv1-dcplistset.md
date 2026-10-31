# DcpListSet

- **ID:** `api-hubitat-zwave-commands-dcpconfigv1-dcplistset`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.dcpconfigv1.DcpListSet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `DcpListSet` | `DcpListSet()` | Creates the command with its declared field defaults. |
| `DcpListSet` | `DcpListSet(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |

## Properties

| Name | Type | Description |
|---|---|---|
| `day` | `Short` | — |
| `dcpRateId` | `Short` | — |
| `durationHourTime` | `Short` | — |
| `durationMinuteTime` | `Short` | — |
| `durationSecondTime` | `Short` | — |
| `eventPriority` | `Short` | — |
| `hourLocalTime` | `Short` | — |
| `loadShedding` | `Short` | — |
| `minuteLocalTime` | `Short` | — |
| `month` | `Short` | — |
| `numberOfDc` | `Short` | — |
| `randomizationInterval` | `Short` | — |
| `secondLocalTime` | `Short` | — |
| `startAssociationGroup` | `Short` | — |
| `startDay` | `Short` | — |
| `startHourLocalTime` | `Short` | — |
| `startMinuteLocalTime` | `Short` | — |
| `startMonth` | `Short` | — |
| `startSecondLocalTime` | `Short` | — |
| `startYear` | `Integer` | — |
| `stopAssociationGroup` | `Short` | — |
| `year` | `Integer` | — |
