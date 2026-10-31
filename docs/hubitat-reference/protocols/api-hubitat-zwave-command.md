# Command

- **ID:** `api-hubitat-zwave-command`
- **Section:** Protocols
- **Class:** `hubitat.zwave.Command`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Methods

| Name | Signature | Description |
|---|---|---|
| `boolToInteger` | `Integer boolToInteger(Boolean value)` | Converts a Boolean to the byte value used by Z-Wave commands. Parameters: value - Boolean value to convert. Returns: 0xFF for true or 0x00 for false. |
| `format` | `String format()` | Formats this command for the configured Z-Wave transport. The new transport returns JSON; the legacy transport joins the command identifier with its payload hex. Returns: transport |
| `getCMD` | `String getCMD()` | Returns the hexadecimal command-class and command identifier implemented by the concrete command. Returns: command frame identifier. |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getJSON` | `String getJSON()` | Builds the command invocation JSON used by this transport. Returns: command JSON string |
| `getJsonCMD` | `String getJsonCMD(Integer commandClass, String methodName)` | Builds JSON for invoking a Z-Wave command-class method. Parameters: commandClass - command-class identifier. methodName - command method name. Returns: JSON object text with comman |
| `getJsonCMD` | `String getJsonCMD(Integer commandClass, String methodName, List<Object> args)` | Builds JSON for invoking a Z-Wave command-class method. Parameters: commandClass - command-class identifier. methodName - command method name. args - method arguments; the shorter  |
| `getJsonCMD` | `String getJsonCMD(Integer commandClass, String methodName, List<Object> args, Integer responseCmd)` | Builds JSON for invoking a Z-Wave command-class method. Parameters: commandClass - command-class identifier. methodName - command method name. args - method arguments; the shorter  |
| `getJsonCMD` | `String getJsonCMD(Integer commandClass, String methodName, List<Object> args, Integer responseCmd, Map<String, Object> supervisionResponse)` | Builds JSON for invoking a Z-Wave command-class method. Parameters: commandClass - command-class identifier. methodName - command method name. args - method arguments; the shorter  |
| `getJsonSetCMD` | `String getJsonSetCMD(Integer commandClass, Object value, Object property)` | Builds JSON for setting a Z-Wave command-class value. Parameters: commandClass - command-class identifier. value - value to assign. property - property name to set. Returns: JSON o |
| `getJsonSetCMD` | `String getJsonSetCMD(Integer commandClass, Object value, Object property, Map<String, Object> options)` | Builds JSON for setting a Z-Wave command-class value. Parameters: commandClass - command-class identifier. value - value to assign. property - property name to set. options - optio |
| `getJsonSetCMD` | `String getJsonSetCMD(Integer commandClass, Object value, Object property, Map<String, Object> options, Object propertyKey)` | Builds JSON for setting a Z-Wave command-class value. Parameters: commandClass - command-class identifier. value - value to assign. property - property name to set. options - optio |
| `getJsonSetCMD` | `String getJsonSetCMD(Integer commandClass, Object value, Object property, Map<String, Object> options, Object propertyKey, Map<String, Object> supervisionResponse)` | Builds JSON for setting a Z-Wave command-class value. Parameters: commandClass - command-class identifier. value - value to assign. property - property name to set. options - optio |
| `getPayload` | `List<Short> getPayload()` | Returns payload bytes in wire order. The base implementation returns an empty list. Returns: serialized payload values. |
