# ZwaveplusInfoReport

- **ID:** `api-hubitat-zwave-commands-zwaveplusinfov1-zwaveplusinforeport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.zwaveplusinfov1.ZwaveplusInfoReport`

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
| `ZwaveplusInfoReport` | `ZwaveplusInfoReport()` | Creates the command with its declared field defaults. |
| `ZwaveplusInfoReport` | `ZwaveplusInfoReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |

## Properties

| Name | Type | Description |
|---|---|---|
| `NODE_TYPE_ZWAVEPLUS_FOR_IP_CLIENT_IP_NODE` | `short` | — |
| `NODE_TYPE_ZWAVEPLUS_FOR_IP_CLIENT_ZWAVE_NODE` | `short` | — |
| `NODE_TYPE_ZWAVEPLUS_FOR_IP_GATEWAY` | `short` | — |
| `NODE_TYPE_ZWAVEPLUS_FOR_IP_ROUTER` | `short` | — |
| `NODE_TYPE_ZWAVEPLUS_NODE` | `short` | — |
| `ROLE_TYPE_CONTROLLER_CENTRAL_STATIC` | `short` | — |
| `ROLE_TYPE_CONTROLLER_PORTABLE` | `short` | — |
| `ROLE_TYPE_CONTROLLER_PORTABLE_REPORTING` | `short` | — |
| `ROLE_TYPE_CONTROLLER_SUB_STATIC` | `short` | — |
| `ROLE_TYPE_SLAVE_ALWAYS_ON` | `short` | — |
| `ROLE_TYPE_SLAVE_PORTABLE` | `short` | — |
| `ROLE_TYPE_SLAVE_SLEEPING_LISTENING` | `short` | — |
| `ROLE_TYPE_SLAVE_SLEEPING_REPORTING` | `short` | — |
| `zWaveplusNodeType` | `Short` | — |
| `zWaveplusRoleType` | `Short` | — |
| `zWaveplusVersion` | `Short` | — |
