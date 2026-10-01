# ZWaveHelperCase

- **ID:** `api-hubitat-zwave-zwavehelpercase`
- **Section:** Protocols
- **Class:** `hubitat.zwave.ZWaveHelperCase`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCommandClass` | `Class<? extends Command> getCommandClass(String cmd, Integer version)` | Looks up the command implementation registered for a Z-Wave command identifier. Parameters: cmd - command class and command identifier in hexadecimal form. version - command-class  |
| `lookupCommandClass` | `Class<? extends Command> lookupCommandClass(String cmdLookup)` | Looks up the command implementation registered for a Z-Wave command identifier. Parameters: cmdLookup - command class and command identifier in hexadecimal form. Returns: command i |
