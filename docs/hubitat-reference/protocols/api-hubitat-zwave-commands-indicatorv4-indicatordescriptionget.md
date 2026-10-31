# IndicatorDescriptionGet

- **ID:** `api-hubitat-zwave-commands-indicatorv4-indicatordescriptionget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.indicatorv4.IndicatorDescriptionGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCMD` | `String getCMD()` | Returns: command-class and command identifier |
| `getJSON` | `String getJSON()` | Returns: Z-Wave JS endpoint API invocation |
| `getPayload` | `List<Short> getPayload()` | Returns: ZIP Gateway wire payload |
| `IndicatorDescriptionGet` | `IndicatorDescriptionGet()` | Creates an empty command. |
| `IndicatorDescriptionGet` | `IndicatorDescriptionGet(String payload)` | Parameters: payload - ZIP Gateway hexadecimal payload |

## Properties

| Name | Type | Description |
|---|---|---|
| `indicatorId` | `Short` | — |
