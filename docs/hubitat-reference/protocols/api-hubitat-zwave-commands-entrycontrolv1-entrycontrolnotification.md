# EntryControlNotification

- **ID:** `api-hubitat-zwave-commands-entrycontrolv1-entrycontrolnotification`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.entrycontrolv1.EntryControlNotification`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EntryControlNotification` | `EntryControlNotification()` | Creates the command with its declared field defaults. |
| `EntryControlNotification` | `EntryControlNotification(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `EntryControlNotification` | `EntryControlNotification(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 4 values also keeps the declared field |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |

## Properties

| Name | Type | Description |
|---|---|---|
| `DATA_TYPE_ASCII` | `Short` | — |
| `DATA_TYPE_MD5` | `Short` | — |
| `DATA_TYPE_NA` | `Short` | — |
| `DATA_TYPE_RAW` | `Short` | — |
| `dataType` | `Short` | — |
| `EVENT_TYPE_ALERT_MEDICAL` | `Short` | — |
| `EVENT_TYPE_ALERT_PANIC` | `Short` | — |
| `EVENT_TYPE_ARM_1` | `Short` | — |
| `EVENT_TYPE_ARM_2` | `Short` | — |
| `EVENT_TYPE_ARM_3` | `Short` | — |
| `EVENT_TYPE_ARM_4` | `Short` | — |
| `EVENT_TYPE_ARM_5` | `Short` | — |
| `EVENT_TYPE_ARM_6` | `Short` | — |
| `EVENT_TYPE_ARM_ALL` | `Short` | — |
| `EVENT_TYPE_ARM_AWAY` | `Short` | — |
| `EVENT_TYPE_ARM_HOME` | `Short` | — |
| `EVENT_TYPE_BELL` | `Short` | — |
| `EVENT_TYPE_CACHED_KEYS` | `Short` | — |
| `EVENT_TYPE_CACHING` | `Short` | — |
| `EVENT_TYPE_CANCEL` | `Short` | — |
| `EVENT_TYPE_DISARM_ALL` | `Short` | — |
| `EVENT_TYPE_ENTER` | `Short` | — |
| `EVENT_TYPE_EXIT_DELAY` | `Short` | — |
| `EVENT_TYPE_FIRE` | `Short` | — |
| `EVENT_TYPE_GATE_CLOSE` | `Short` | — |
| `EVENT_TYPE_GATE_OPEN` | `Short` | — |
| `EVENT_TYPE_LOCK` | `Short` | — |
| `EVENT_TYPE_POLICE` | `Short` | — |
| `EVENT_TYPE_RFID` | `Short` | — |
| `EVENT_TYPE_TEST` | `Short` | — |
| `EVENT_TYPE_UNLOCK` | `Short` | — |
| `eventData` | `List<Short>` | — |
| `eventType` | `Short` | — |
| `sequenceNumber` | `Short` | — |
