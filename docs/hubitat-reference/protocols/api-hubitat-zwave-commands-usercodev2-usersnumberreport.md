# UsersNumberReport

- **ID:** `api-hubitat-zwave-commands-usercodev2-usersnumberreport`
- **Section:** Protocols
- **Class:** `hubitat.zwave.commands.usercodev2.UsersNumberReport`

> Use this protocol type to create, inspect, or parse protocol data in driver code.

## Inheritance

- Extends `hubitat.zwave.commands.usercodev1.UsersNumberReport`
- [UsersNumberReport](../protocols/api-hubitat-zwave-commands-usercodev1-usersnumberreport.md)
- [UsersNumberReport](../protocols/api-hubitat-zwave-commands-usercodev1-usersnumberreport.md)

## Methods

| Name | Signature | Description |
|---|---|---|
| `getPayload` | `List<Short> getPayload()` | Returns this command's payload values in wire order. Returns: serialized payload values |
| `UsersNumberReport` | `UsersNumberReport()` | Creates the command with its declared field defaults. |
| `UsersNumberReport` | `UsersNumberReport(List<Map<String, Object>> payload)` | Initializes command fields from Z-Wave JS property updates. Parameters: payload - property update records used to select and assign command fields: property (String) - command prop |
| `UsersNumberReport` | `UsersNumberReport(String payload)` | Decodes this command's fields from a hexadecimal payload. A null payload keeps the declared field defaults. Payload bytes are separated by spaces; a malformed hexadecimal token pro |
