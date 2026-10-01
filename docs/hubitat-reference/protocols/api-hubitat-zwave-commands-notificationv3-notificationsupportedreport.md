# NotificationSupportedReport

- **ID:** `api-hubitat-zwave-commands-notificationv3-notificationsupportedreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.notificationv3.NotificationSupportedReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.Command`
- [Command](../protocols/api-hubitat-zwave-command.md)
- [Command](../protocols/api-hubitat-zwave-command.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getAccessControl` | `Boolean getAccessControl()` | Returns the access control value calculated from this command fields. Returns: access control value |
| `getBurglar` | `Boolean getBurglar()` | Returns the burglar value calculated from this command fields. Returns: burglar value |
| `getClock` | `Boolean getClock()` | Returns the clock value calculated from this command fields. Returns: clock value |
| `getCMD` | `String getCMD()` | Returns this command frame identifier in hexadecimal form. Returns: hexadecimal command class and command identifier |
| `getCo` | `Boolean getCo()` | Returns the co value calculated from this command fields. Returns: co value |
| `getCo2` | `Boolean getCo2()` | Returns the co2 value calculated from this command fields. Returns: co2 value |
| `getEmergency` | `Boolean getEmergency()` | Returns the emergency value calculated from this command fields. Returns: emergency value |
| `getHeat` | `Boolean getHeat()` | Returns the heat value calculated from this command fields. Returns: heat value |
| `getNumberOfBitMasks` | `Short getNumberOfBitMasks()` | Returns the number of bit masks value stored in bitMasks. Returns: number of bit masks value |
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `getPowerManagement` | `Boolean getPowerManagement()` | Returns the power management value calculated from this command fields. Returns: power management value |
| `getSmoke` | `Boolean getSmoke()` | Returns the smoke value calculated from this command fields. Returns: smoke value |
| `getSystem` | `Boolean getSystem()` | Returns the system value calculated from this command fields. Returns: system value |
| `getV1Alarm` | `boolean getV1Alarm()` | Reports whether bit 3 of properties1 is set for v1 alarm. Returns: v1 alarm value |
| `getWater` | `Boolean getWater()` | Returns the water value calculated from this command fields. Returns: water value |
| `NotificationSupportedReport` | `NotificationSupportedReport()` | Creates the command with its declared field defaults. |
| `NotificationSupportedReport` | `NotificationSupportedReport(List<Map<String, Object>> payload)` | Initializes supported notification fields from Z-Wave JS response records. Parameters: payload - list of maps; a map is processed when it contains response, regardless of that valu |
| `NotificationSupportedReport` | `NotificationSupportedReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. A decoded payload with fewer than 1 values also keeps the declared field |
| `setAccessControl` | `void setAccessControl(Boolean value)` | Writes the access control value to payload position 0. Parameters: value - access control value to encode |
| `setBurglar` | `void setBurglar(Boolean value)` | Writes the burglar value to payload position 0. Parameters: value - burglar value to encode |
| `setClock` | `void setClock(Boolean value)` | Writes the clock value to payload position 1. Parameters: value - clock value to encode |
| `setCo` | `void setCo(Boolean value)` | Writes the co value to payload position 0. Parameters: value - co value to encode |
| `setCo2` | `void setCo2(Boolean value)` | Writes the co2 value to payload position 0. Parameters: value - co2 value to encode |
| `setEmergency` | `void setEmergency(Boolean value)` | Writes the emergency value to payload position 1. Parameters: value - emergency value to encode |
| `setHeat` | `void setHeat(Boolean value)` | Writes the heat value to payload position 0. Parameters: value - heat value to encode |
| `setNumberOfBitMasks` | `void setNumberOfBitMasks(Short value)` | Encodes number of bit masks in bits 0 through 4 of properties1 and preserves the bits selected by 0xE0. Parameters: value - number of bit masks value to encode |
| `setPowerManagement` | `void setPowerManagement(Boolean value)` | Writes the power management value to payload position 1. Parameters: value - power management value to encode |
| `setSmoke` | `void setSmoke(Boolean value)` | Writes the smoke value to payload position 0. Parameters: value - smoke value to encode |
| `setSystem` | `void setSystem(Boolean value)` | Writes the system value to payload position 1. Parameters: value - system value to encode |
| `setV1Alarm` | `void setV1Alarm(Boolean value)` | Sets the v1 alarm value used by this command. Parameters: value - v1 alarm value to encode |
| `setWater` | `void setWater(Boolean value)` | Writes the water value to payload position 0. Parameters: value - water value to encode |
| `toString` | `String toString()` | Returns a readable description containing the fields selected by this command's formatter. Returns: command field description |
