# ZwaveplusInfoReport

- **ID:** `api-hubitat-zwave-commands-zwaveplusinfov2-zwaveplusinforeport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zwaveplusinfov2.ZwaveplusInfoReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.zwaveplusinfov1.ZwaveplusInfoReport`
- [ZwaveplusInfoReport](../protocols/api-hubitat-zwave-commands-zwaveplusinfov1-zwaveplusinforeport.md)
- [ZwaveplusInfoReport](../protocols/api-hubitat-zwave-commands-zwaveplusinfov1-zwaveplusinforeport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `ZwaveplusInfoReport` | `ZwaveplusInfoReport()` | Creates the command with its declared field defaults. |
| `ZwaveplusInfoReport` | `ZwaveplusInfoReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 7 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `installerIconType` | `Integer` | — |
| `userIconType` | `Integer` | — |
