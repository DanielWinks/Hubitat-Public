# ZWaveHelper

- **ID:** `api-hubitat-zwave-zwavehelper`
- **Section:** Protocols
- **Class:** `hubitat.zwave.ZWaveHelper`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCommandClass` | `Class<? extends Command> getCommandClass(String cmd, Integer version)` | Looks up the command implementation registered for a Z-Wave command identifier. Parameters: cmd - command class and command identifier in hexadecimal form. version - command-class  |
