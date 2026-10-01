# Zwave2

- **ID:** `api-hubitat-zwave-zwave2`
- **Section:** Protocols
- **Class:** `hubitat.zwave.Zwave2`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `getCommand` | `Command getCommand(Short commandClass, Short command, List<Short> payload)` | Creates a command object from its command-class identifier, command identifier, and payload. Parameters: commandClass - command-class identifier. command - command identifier. payl |
| `getCommand` | `Command getCommand(Short commandClass, Short command, List<Short> payload, Integer version)` | Creates a command object from its command-class identifier, command identifier, and payload. Parameters: commandClass - command-class identifier. command - command identifier. payl |
| `getCommand` | `Command getCommand(String cmd, String payload, Integer version)` | Creates a command object from its command-class identifier, command identifier, and payload. Parameters: cmd - combined hexadecimal command-class and command identifier. payload -  |
| `getCommand` | `Command getCommand(byte commandClass, byte command, byte[] payload)` | Creates a command object from byte identifiers and a byte-array payload using default version selection. Parameters: commandClass - command-class identifier byte used to select the |
| `getCommand` | `Command getCommand(byte commandClass, byte command, byte[] payload, Integer version)` | Creates a command object from byte identifiers and a byte-array payload. Parameters: commandClass - command-class identifier byte used to select the command class command - command |
| `parse` | `Command parse(String description)` | Parses a Z-Wave command description using default version selection. Parameters: description - command description or JSON request text. Returns: parsed command, or null when no co |
| `parse` | `Command parse(String description, Map options)` | Parses a Z-Wave command description or JSON command into its command object. Parameters: description - legacy command description or JSON request text. options - optional Map<Integ |
| `parse` | `Command parse(String description, Map options, boolean dropMulticast)` | Parses a Z-Wave command description or JSON command into its command object. Parameters: description - legacy command description or JSON request text. options - optional Map<Integ |
| `parse` | `Command parse(String description, boolean dropMulticast)` | Parses a legacy Z-Wave command description with optional multicast filtering. Parameters: description - comma-separated command description. dropMulticast - when true, filters a de |
