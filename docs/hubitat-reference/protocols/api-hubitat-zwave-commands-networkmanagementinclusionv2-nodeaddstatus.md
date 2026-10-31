# NodeAddStatus

- **ID:** `api-hubitat-zwave-commands-networkmanagementinclusionv2-nodeaddstatus`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementinclusionv2.NodeAddStatus`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.networkmanagementinclusionv1.NodeAddStatus`
- [NodeAddStatus](../protocols/api-hubitat-zwave-commands-networkmanagementinclusionv1-nodeaddstatus.md)
- [NodeAddStatus](../protocols/api-hubitat-zwave-commands-networkmanagementinclusionv1-nodeaddstatus.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `NodeAddStatus` | `NodeAddStatus()` | Creates the command with its declared field defaults. |
| `NodeAddStatus` | `NodeAddStatus(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |

## Properties

| Name | Type | Description |
|---|---|---|
| `grantedKeys` | `Short` | — |
| `KEX_FAIL_AUTH` | `short` | — |
| `KEX_FAIL_CANCEL` | `short` | — |
| `KEX_FAIL_DECRYPT` | `short` | — |
| `KEX_FAIL_KEX_CURVES` | `short` | — |
| `KEX_FAIL_KEX_KEY` | `short` | — |
| `KEX_FAIL_KEX_SCHEME` | `short` | — |
| `KEX_FAIL_KEY_GET` | `short` | — |
| `KEX_FAIL_KEY_REPORT` | `short` | — |
| `KEX_FAIL_KEY_VERIFY` | `short` | — |
| `KEX_SUCCESS` | `short` | — |
| `KEXFailType` | `Short` | — |
