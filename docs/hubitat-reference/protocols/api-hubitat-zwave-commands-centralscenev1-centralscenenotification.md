# CentralSceneNotification

- **ID:** `api-hubitat-zwave-commands-centralscenev1-centralscenenotification`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.centralscenev1.CentralSceneNotification`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `CentralSceneNotification` | `CentralSceneNotification()` | Creates the command with its declared field defaults. |
| `CentralSceneNotification` | `CentralSceneNotification(List<Map<String, Object>> payload)` | Initializes scene fields from matching Z-Wave JS property updates. Parameters: payload - list of maps; each map with property equal to the String "scene" must provide propertyKey a |
| `CentralSceneNotification` | `CentralSceneNotification(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 3 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `KEY_HELD_DOWN` | `Short` | — |
| `KEY_PRESSED_1_TIME` | `Short` | — |
| `KEY_RELEASED` | `Short` | — |
| `keyAttributes` | `Short` | — |
| `sceneNumber` | `Short` | — |
| `sequenceNumber` | `Short` | — |
