# NodeInfoCachedReport

- **ID:** `api-hubitat-zwave-commands-networkmanagementproxyv1-nodeinfocachedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.networkmanagementproxyv1.NodeInfoCachedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAge` | `Short getAge()` | Returns the age value stored in age. Returns: age value |
| `getBasicDeviceClass` | `Short getBasicDeviceClass()` | Returns the basic device class value stored in basicDeviceClass. Returns: basic device class value |
| `getCapability` | `Short getCapability()` | Returns the capability value stored in capability. Returns: capability value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCommandClassId` | `Short getCommandClassId()` | Returns the command class byte from this command frame identifier. Returns: command class byte |
| `getCommandId` | `Short getCommandId()` | Returns the command byte from this command frame identifier. Returns: command byte |
| `getGenericDeviceClass` | `Short getGenericDeviceClass()` | Returns the generic device class value stored in genericDeviceClass. Returns: generic device class value |
| `getNonSecureControlledCommandClass` | `List<Short> getNonSecureControlledCommandClass()` | Returns the non secure controlled command class value stored in nonSecureControlledCommandClass. Returns: non secure controlled command class value |
| `getNonSecureSupportedCommandClass` | `List<Short> getNonSecureSupportedCommandClass()` | Returns the non secure supported command class value stored in nonSecureSupportedCommandClass. Returns: non secure supported command class value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getSecurity` | `Short getSecurity()` | Returns the security value stored in security. Returns: security value |
| `getSecurityScheme0ControlledCommandClass` | `List<Short> getSecurityScheme0ControlledCommandClass()` | Returns the security scheme0 controlled command class value stored in securityScheme0ControlledCommandClass. Returns: security scheme0 controlled command class value |
| `getSecurityScheme0SupportedCommandClass` | `List<Short> getSecurityScheme0SupportedCommandClass()` | Returns the security scheme0 supported command class value stored in securityScheme0SupportedCommandClass. Returns: security scheme0 supported command class value |
| `getSeqNo` | `Short getSeqNo()` | Returns the seq no value stored in seqNo. Returns: seq no value |
| `getSpecificDeviceClass` | `Short getSpecificDeviceClass()` | Returns the specific device class value stored in specificDeviceClass. Returns: specific device class value |
| `getStatus` | `Short getStatus()` | Returns the status value stored in status. Returns: status value |
| `isBeaming` | `boolean isBeaming()` | Reports whether bit 4 of security is set for beaming. Returns: beaming flag |
| `isFlirs` | `boolean isFlirs()` | Reports the flirs value stored in bits 5 through 6 of security. Returns: flirs flag |
| `isListening` | `boolean isListening()` | Reports whether bit 7 of capability is set for listening. Returns: listening flag |
| `isOptionalFunctionality` | `boolean isOptionalFunctionality()` | Reports whether bit 7 of security is set for optional functionality. Returns: optional functionality flag |
| `NodeInfoCachedReport` | `NodeInfoCachedReport()` | Creates the command with its declared field defaults. |
| `NodeInfoCachedReport` | `NodeInfoCachedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. Parameters: payload - hexadecimal payload bytes for this command |
| `setAge` | `void setAge(Short age)` | Sets the age value used by this command. Parameters: age - age value to encode |
| `setBasicDeviceClass` | `void setBasicDeviceClass(Short basicDeviceClass)` | Sets the basic device class value used by this command. Parameters: basicDeviceClass - basic device class value to encode |
| `setCapability` | `void setCapability(Short capability)` | Sets the capability value used by this command. Parameters: capability - capability value to encode |
| `setGenericDeviceClass` | `void setGenericDeviceClass(Short genericDeviceClass)` | Sets the generic device class value used by this command. Parameters: genericDeviceClass - generic device class value to encode |
| `setNonSecureControlledCommandClass` | `void setNonSecureControlledCommandClass(List<Short> nonSecureControlledCommandClass)` | Sets the non secure controlled command class value used by this command. Parameters: nonSecureControlledCommandClass - non secure controlled command class value to encode |
| `setNonSecureSupportedCommandClass` | `void setNonSecureSupportedCommandClass(List<Short> nonSecureSupportedCommandClass)` | Sets the non secure supported command class value used by this command. Parameters: nonSecureSupportedCommandClass - non secure supported command class value to encode |
| `setSecurity` | `void setSecurity(Short security)` | Sets the security value used by this command. Parameters: security - security value to encode |
| `setSecurityScheme0ControlledCommandClass` | `void setSecurityScheme0ControlledCommandClass(List<Short> securityScheme0ControlledCommandClass)` | Sets the security scheme0 controlled command class value used by this command. Parameters: securityScheme0ControlledCommandClass - security scheme0 controlled command class value t |
| `setSecurityScheme0SupportedCommandClass` | `void setSecurityScheme0SupportedCommandClass(List<Short> securityScheme0SupportedCommandClass)` | Sets the security scheme0 supported command class value used by this command. Parameters: securityScheme0SupportedCommandClass - security scheme0 supported command class value to e |
| `setSeqNo` | `void setSeqNo(Short seqNo)` | Sets the seq no value used by this command. Parameters: seqNo - seq no value to encode |
| `setSpecificDeviceClass` | `void setSpecificDeviceClass(Short specificDeviceClass)` | Sets the specific device class value used by this command. Parameters: specificDeviceClass - specific device class value to encode |
| `setStatus` | `void setStatus(Short status)` | Sets the status value used by this command. Parameters: status - status value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
