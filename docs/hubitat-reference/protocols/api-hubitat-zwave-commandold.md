# CommandOld

- **ID:** `api-hubitat-zwave-commandold`
- **Section:** Protocols
- **Class:** `hubitat.zwave.CommandOld`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `format` | `String format()` | Formats this command for the configured Z-Wave transport. The new transport returns JSON; the legacy transport joins the command identifier with its payload hex. Returns: transport |
| `getCMD` | `String getCMD()` | Returns the hexadecimal command-class and command identifier implemented by the concrete command. Returns: command frame identifier. |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getPayload` | `List<Short> getPayload()` | Returns payload bytes in wire order. The base implementation returns an empty list. Returns: serialized payload values. |
