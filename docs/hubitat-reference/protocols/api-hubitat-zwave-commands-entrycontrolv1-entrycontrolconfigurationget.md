# EntryControlConfigurationGet

- **ID:** `api-hubitat-zwave-commands-entrycontrolv1-entrycontrolconfigurationget`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.entrycontrolv1.EntryControlConfigurationGet`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `EntryControlConfigurationGet` | `EntryControlConfigurationGet()` | Creates the command with its declared field defaults. |
| `EntryControlConfigurationGet` | `EntryControlConfigurationGet(String payload)` | Accepts the supplied payload but does not read it or change command fields. Parameters: payload - unused payload value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getJSON` | `String getJSON()` | Returns: command invocation encoded as a JSON string |
| `getPayload` | `List<Short> getPayload()` | Returns no payload values for this command. Returns: an empty list |
