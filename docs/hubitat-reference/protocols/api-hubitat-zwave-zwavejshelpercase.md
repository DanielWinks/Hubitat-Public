# ZWaveJSHelperCase

- **ID:** `api-hubitat-zwave-zwavejshelpercase`
- **Section:** Protocols
- **Class:** `hubitat.zwave.ZWaveJSHelperCase`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCommandClass` | `JSClass getCommandClass(Integer cc, Integer ver = 1, Integer ep = 0)` | Returns the Z-Wave JS command implementation for a class, version, and endpoint. Parameters: cc - command-class identifier. ver - command-class version; defaults to 1. ep - endpoin |
